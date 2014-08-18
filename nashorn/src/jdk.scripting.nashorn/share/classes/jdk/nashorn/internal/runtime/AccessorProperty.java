/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.ObjectClassGenerator.OBJECT_FIELDS_ONLY;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.PRIMITIVE_FIELD_TYPE;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.createGetter;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.createSetter;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.getFieldCount;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.getFieldName;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.lookup.MethodHandleFactory.stripName;
import static jdk.nashorn.internal.runtime.JSType.getAccessorTypeIndex;
import static jdk.nashorn.internal.runtime.JSType.getNumberOfAccessorTypes;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.SwitchPoint;
import java.util.function.Supplier;
import java.util.logging.Level;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.objects.Global;

/**
 * An AccessorProperty is the most generic property type. An AccessorProperty is
 * represented as fields in a ScriptObject class.
 */
public class AccessorProperty extends Property {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle REPLACE_MAP   = findOwnMH_S("replaceMap", Object.class, Object.class, PropertyMap.class);
    private static final MethodHandle INVALIDATE_SP = findOwnMH_S("invalidateSwitchPoint", Object.class, Object.class, SwitchPoint.class);

    private static final SwitchPoint NO_CHANGE_CALLBACK = new SwitchPoint();

    private static final int NOOF_TYPES = getNumberOfAccessorTypes();
    private static final long serialVersionUID = 3371720170182154920L;

    /**
     * Properties in different maps for the same structure class will share their field getters and setters. This could
     * be further extended to other method handles that are looked up in the AccessorProperty constructor, but right now
     * these are the most frequently retrieved ones, and lookup of method handle natives only registers in the profiler
     * for them.
     */
    private static ClassValue<Accessors> GETTERS_SETTERS = new ClassValue<Accessors>() {
        @Override
        protected Accessors computeValue(final Class<?> structure) {
            return new Accessors(structure);
        }
    };

    private static class Accessors {
        final MethodHandle[] objectGetters;
        final MethodHandle[] objectSetters;
        final MethodHandle[] primitiveGetters;
        final MethodHandle[] primitiveSetters;

        /**
         * Normal
         * @param structure
         */
        Accessors(final Class<?> structure) {
            final int fieldCount = getFieldCount(structure);
            objectGetters    = new MethodHandle[fieldCount];
            objectSetters    = new MethodHandle[fieldCount];
            primitiveGetters = new MethodHandle[fieldCount];
            primitiveSetters = new MethodHandle[fieldCount];

            for (int i = 0; i < fieldCount; i++) {
                final String fieldName = getFieldName(i, Type.OBJECT);
                final Class<?> typeClass = Type.OBJECT.getTypeClass();
                objectGetters[i] = MH.asType(MH.getter(LOOKUP, structure, fieldName, typeClass), Lookup.GET_OBJECT_TYPE);
                objectSetters[i] = MH.asType(MH.setter(LOOKUP, structure, fieldName, typeClass), Lookup.SET_OBJECT_TYPE);
            }

            if (!OBJECT_FIELDS_ONLY) {
                for (int i = 0; i < fieldCount; i++) {
                    final String fieldNamePrimitive = getFieldName(i, PRIMITIVE_FIELD_TYPE);
                    final Class<?> typeClass = PRIMITIVE_FIELD_TYPE.getTypeClass();
                    primitiveGetters[i] = MH.asType(MH.getter(LOOKUP, structure, fieldNamePrimitive, typeClass), Lookup.GET_PRIMITIVE_TYPE);
                    primitiveSetters[i] = MH.asType(MH.setter(LOOKUP, structure, fieldNamePrimitive, typeClass), Lookup.SET_PRIMITIVE_TYPE);
                }
            }
        }
    }

    /**
     * Property getter cache
     *   Note that we can't do the same simple caching for optimistic getters,
     *   due to the fact that they are bound to a program point, which will
     *   produce different boun method handles wrapping the same access mechanism
     *   depending on callsite
     */
    private MethodHandle[] GETTER_CACHE = new MethodHandle[NOOF_TYPES];

    /**
     * Create a new accessor property. Factory method used by nasgen generated code.
     *
     * @param key           {@link Property} key.
     * @param propertyFlags {@link Property} flags.
     * @param getter        {@link Property} get accessor method.
     * @param setter        {@link Property} set accessor method.
     *
     * @return  New {@link AccessorProperty} created.
     */
    public static AccessorProperty create(final String key, final int propertyFlags, final MethodHandle getter, final MethodHandle setter) {
        return new AccessorProperty(key, propertyFlags, -1, getter, setter);
    }

    /** Seed getter for the primitive version of this field (in -Dnashorn.fields.dual=true mode) */
    transient MethodHandle primitiveGetter;

    /** Seed setter for the primitive version of this field (in -Dnashorn.fields.dual=true mode) */
    transient MethodHandle primitiveSetter;

    /** Seed getter for the Object version of this field */
    transient MethodHandle objectGetter;

    /** Seed setter for the Object version of this field */
    transient MethodHandle objectSetter;

    /**
     * Current type of this object, in object only mode, this is an Object.class. In dual-fields mode
     * null means undefined, and primitive types are allowed. The reason a special type is used for
     * undefined, is that are no bits left to represent it in primitive types
     */
    private Class<?> currentType;

    /**
     * Delegate constructor for bound properties. This is used for properties created by
     * {@link ScriptRuntime#mergeScope} and the Nashorn {@code Object.bindProperties} method.
     * The former is used to add a script's defined globals to the current global scope while
     * still storing them in a JO-prefixed ScriptObject class.
     *
     * <p>All properties created by this constructor have the {@link #IS_BOUND} flag set.</p>
     *
     * @param property  accessor property to rebind
     * @param delegate  delegate object to rebind receiver to
     */
    AccessorProperty(final AccessorProperty property, final Object delegate) {
        super(property, property.getFlags() | IS_BOUND);

        this.primitiveGetter = bindTo(property.primitiveGetter, delegate);
        this.primitiveSetter = bindTo(property.primitiveSetter, delegate);
        this.objectGetter    = bindTo(property.objectGetter, delegate);
        this.objectSetter    = bindTo(property.objectSetter, delegate);
        property.GETTER_CACHE = new MethodHandle[NOOF_TYPES];
        // Properties created this way are bound to a delegate
        setCurrentType(property.getCurrentType());
    }

    /**
     * SPILL PROPERTY or USER ACCESSOR PROPERTY abstract constructor
     *
     * Constructor for spill properties. Array getters and setters will be created on demand.
     *
     * @param key    the property key
     * @param flags  the property flags
     * @param slot   spill slot
     * @param primitiveGetter primitive getter
     * @param primitiveSetter primitive setter
     * @param objectGetter    object getter
     * @param objectSetter    object setter
     */
    protected AccessorProperty(
            final String key,
            final int flags,
            final int slot,
            final MethodHandle primitiveGetter,
            final MethodHandle primitiveSetter,
            final MethodHandle objectGetter,
            final MethodHandle objectSetter) {
        super(key, flags, slot);
        assert getClass() != AccessorProperty.class;
        this.primitiveGetter = primitiveGetter;
        this.primitiveSetter = primitiveSetter;
        this.objectGetter    = objectGetter;
        this.objectSetter    = objectSetter;
        initializeType();
    }

    /**
     * NASGEN constructor
     *
     * Constructor. Similar to the constructor with both primitive getters and setters, the difference
     * here being that only one getter and setter (setter is optional for non writable fields) is given
     * to the constructor, and the rest are created from those. Used e.g. by Nasgen classes
     *
     * @param key    the property key
     * @param flags  the property flags
     * @param slot   the property field number or spill slot
     * @param getter the property getter
     * @param setter the property setter or null if non writable, non configurable
     */
    private AccessorProperty(final String key, final int flags, final int slot, final MethodHandle getter, final MethodHandle setter) {
        super(key, flags | (getter.type().returnType().isPrimitive() ? IS_NASGEN_PRIMITIVE : 0), slot);
        assert !isSpill();

        // we don't need to prep the setters these will never be invalidated as this is a nasgen
        // or known type getter/setter. No invalidations will take place

        final Class<?> getterType = getter.type().returnType();
        final Class<?> setterType = setter == null ? null : setter.type().parameterType(1);

        assert setterType == null || setterType == getterType;
        if (OBJECT_FIELDS_ONLY) {
            primitiveGetter = primitiveSetter = null;
        } else {
            if (getterType == int.class || getterType == long.class) {
                primitiveGetter = MH.asType(getter, Lookup.GET_PRIMITIVE_TYPE);
                primitiveSetter = setter == null ? null : MH.asType(setter, Lookup.SET_PRIMITIVE_TYPE);
            } else if (getterType == double.class) {
                primitiveGetter = MH.asType(MH.filterReturnValue(getter, ObjectClassGenerator.PACK_DOUBLE), Lookup.GET_PRIMITIVE_TYPE);
                primitiveSetter = setter == null ? null : MH.asType(MH.filterArguments(setter, 1, ObjectClassGenerator.UNPACK_DOUBLE), Lookup.SET_PRIMITIVE_TYPE);
            } else {
                primitiveGetter = primitiveSetter = null;
            }
        }

        assert primitiveGetter == null || primitiveGetter.type() == Lookup.GET_PRIMITIVE_TYPE : primitiveGetter + "!=" + Lookup.GET_PRIMITIVE_TYPE;
        assert primitiveSetter == null || primitiveSetter.type() == Lookup.SET_PRIMITIVE_TYPE : primitiveSetter;

        objectGetter  = getter.type() != Lookup.GET_OBJECT_TYPE ? MH.asType(getter, Lookup.GET_OBJECT_TYPE) : getter;
        objectSetter  = setter != null && setter.type() != Lookup.SET_OBJECT_TYPE ? MH.asType(setter, Lookup.SET_OBJECT_TYPE) : setter;

        setCurrentType(OBJECT_FIELDS_ONLY ? Object.class : getterType);
    }

    /**
     * Normal ACCESS PROPERTY constructor given a structure class.
     * Constructor for dual field AccessorPropertys.
     *
     * @param key              property key
     * @param flags            property flags
     * @param structure        structure for objects associated with this property
     * @param slot             property field number or spill slot
     */
    public AccessorProperty(final String key, final int flags, final Class<?> structure, final int slot) {
        super(key, flags, slot);

        initGetterSetter(structure);
        initializeType();
    }

    private void initGetterSetter(final Class<?> structure) {
        final int slot = getSlot();
        /*
         * primitiveGetter and primitiveSetter are only used in dual fields mode. Setting them to null also
         * works in dual field mode, it only means that the property never has a primitive
         * representation.
         */

        if (isParameter() && hasArguments()) {
            //parameters are always stored in an object array, which may or may not be a good idea
            final MethodHandle arguments = MH.getter(LOOKUP, structure, "arguments", ScriptObject.class);
            objectGetter = MH.asType(MH.insertArguments(MH.filterArguments(ScriptObject.GET_ARGUMENT.methodHandle(), 0, arguments), 1, slot), Lookup.GET_OBJECT_TYPE);
            objectSetter = MH.asType(MH.insertArguments(MH.filterArguments(ScriptObject.SET_ARGUMENT.methodHandle(), 0, arguments), 1, slot), Lookup.SET_OBJECT_TYPE);
            primitiveGetter = null;
            primitiveSetter = null;
        } else {
            final Accessors gs = GETTERS_SETTERS.get(structure);
            objectGetter    = gs.objectGetters[slot];
            primitiveGetter = gs.primitiveGetters[slot];
            objectSetter    = gs.objectSetters[slot];
            primitiveSetter = gs.primitiveSetters[slot];
        }
    }

    /**
     * Constructor
     *
     * @param key          key
     * @param flags        flags
     * @param slot         field slot index
     * @param owner        owner of property
     * @param initialValue initial value to which the property can be set
     */
    protected AccessorProperty(final String key, final int flags, final int slot, final ScriptObject owner, final Object initialValue) {
        this(key, flags, owner.getClass(), slot);
        setInitialValue(owner, initialValue);
    }

    /**
     * Normal access property constructor that overrides the type
     * Override the initial type. Used for Object Literals
     *
     * @param key          key
     * @param flags        flags
     * @param structure    structure to JO subclass
     * @param slot         field slot index
     * @param initialType  initial type of the property
     */
    public AccessorProperty(final String key, final int flags, final Class<?> structure, final int slot, final Class<?> initialType) {
        this(key, flags, structure, slot);
        setCurrentType(OBJECT_FIELDS_ONLY ? Object.class : initialType);
    }

    /**
     * Copy constructor that may change type and in that case clear the cache. Important to do that before
     * type change or getters will be created already stale.
     *
     * @param property property
     * @param newType  new type
     */
    protected AccessorProperty(final AccessorProperty property, final Class<?> newType) {
        super(property, property.getFlags());

        this.GETTER_CACHE    = newType != property.getCurrentType() ? new MethodHandle[NOOF_TYPES] : property.GETTER_CACHE;
        this.primitiveGetter = property.primitiveGetter;
        this.primitiveSetter = property.primitiveSetter;
        this.objectGetter    = property.objectGetter;
        this.objectSetter    = property.objectSetter;

        setCurrentType(newType);
    }

    /**
     * COPY constructor
     *
     * @param property  source property
     */
    protected AccessorProperty(final AccessorProperty property) {
        this(property, property.getCurrentType());
    }

    /**
     * Set initial value of a script object's property
     * @param owner        owner
     * @param initialValue initial value
     */
    protected final void setInitialValue(final ScriptObject owner, final Object initialValue) {
        setCurrentType(JSType.unboxedFieldType(initialValue));
        if (initialValue instanceof Integer) {
            invokeSetter(owner, ((Integer)initialValue).intValue());
        } else if (initialValue instanceof Long) {
            invokeSetter(owner, ((Long)initialValue).longValue());
        } else if (initialValue instanceof Double) {
            invokeSetter(owner, ((Double)initialValue).doubleValue());
        } else {
            invokeSetter(owner, initialValue);
        }
    }

    /**
     * Initialize the type of a property
     */
    protected final void initializeType() {
        setCurrentType(OBJECT_FIELDS_ONLY ? Object.class : null);
    }

    private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        // Restore getters array
        GETTER_CACHE = new MethodHandle[NOOF_TYPES];
    }

    private static MethodHandle bindTo(final MethodHandle mh, final Object receiver) {
        if (mh == null) {
            return null;
        }

        return MH.dropArguments(MH.bindTo(mh, receiver), 0, Object.class);
    }

    @Override
    public Property copy() {
        return new AccessorProperty(this);
    }

    @Override
    public Property copy(final Class<?> newType) {
        return new AccessorProperty(this, newType);
    }

    @Override
    public int getIntValue(final ScriptObject self, final ScriptObject owner) {
        try {
            return (int)getGetter(int.class).invokeExact((Object)self);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
     }

    @Override
    public long getLongValue(final ScriptObject self, final ScriptObject owner) {
        try {
            return (long)getGetter(long.class).invokeExact((Object)self);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

     @Override
     public double getDoubleValue(final ScriptObject self, final ScriptObject owner) {
        try {
            return (double)getGetter(double.class).invokeExact((Object)self);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

     @Override
     public Object getObjectValue(final ScriptObject self, final ScriptObject owner) {
        try {
            return getGetter(Object.class).invokeExact((Object)self);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

     /**
      * Invoke setter for this property with a value
      * @param self  owner
      * @param value value
      */
    protected final void invokeSetter(final ScriptObject self, final int value) {
        try {
            getSetter(int.class, self.getMap()).invokeExact((Object)self, value);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invoke setter for this property with a value
     * @param self  owner
     * @param value value
     */
    protected final void invokeSetter(final ScriptObject self, final long value) {
        try {
            getSetter(long.class, self.getMap()).invokeExact((Object)self, value);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invoke setter for this property with a value
     * @param self  owner
     * @param value value
     */
    protected final void invokeSetter(final ScriptObject self, final double value) {
        try {
            getSetter(double.class, self.getMap()).invokeExact((Object)self, value);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invoke setter for this property with a value
     * @param self  owner
     * @param value value
     */
    protected final void invokeSetter(final ScriptObject self, final Object value) {
        try {
            getSetter(Object.class, self.getMap()).invokeExact((Object)self, value);
        } catch (final Error | RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setValue(final ScriptObject self, final ScriptObject owner, final int value, final boolean strict)  {
        assert isConfigurable() || isWritable() : getKey() + " is not writable or configurable";
        invokeSetter(self, value);
    }

    @Override
    public void setValue(final ScriptObject self, final ScriptObject owner, final long value, final boolean strict)  {
        assert isConfigurable() || isWritable() : getKey() + " is not writable or configurable";
        invokeSetter(self, value);
    }

    @Override
    public void setValue(final ScriptObject self, final ScriptObject owner, final double value, final boolean strict)  {
        assert isConfigurable() || isWritable() : getKey() + " is not writable or configurable";
        invokeSetter(self, value);
    }

    @Override
    public void setValue(final ScriptObject self, final ScriptObject owner, final Object value, final boolean strict)  {
        //this is sometimes used for bootstrapping, hence no assert. ugly.
        invokeSetter(self, value);
    }

    @Override
    void initMethodHandles(final Class<?> structure) {
        // sanity check for structure class
        if (!ScriptObject.class.isAssignableFrom(structure) || !StructureLoader.isStructureClass(structure.getName())) {
            throw new IllegalArgumentException();
        }
        // this method is overridden in SpillProperty
        assert !isSpill();
        initGetterSetter(structure);
    }

    @Override
    public MethodHandle getGetter(final Class<?> type) {
        final int i = getAccessorTypeIndex(type);

        assert type == int.class ||
                type == long.class ||
                type == double.class ||
                type == Object.class :
                "invalid getter type " + type + " for " + getKey();

        //all this does is add a return value filter for object fields only
        final MethodHandle[] getterCache = GETTER_CACHE;
        final MethodHandle cachedGetter = getterCache[i];
        final MethodHandle getter;
        if (cachedGetter != null) {
            getter = cachedGetter;
        } else {
            getter = debug(
                createGetter(
                    getCurrentType(),
                    type,
                    primitiveGetter,
                    objectGetter,
                    INVALID_PROGRAM_POINT),
                getCurrentType(),
                type,
                "get");
            getterCache[i] = getter;
       }
       assert getter.type().returnType() == type && getter.type().parameterType(0) == Object.class;
       return getter;
    }

    @Override
    public MethodHandle getOptimisticGetter(final Class<?> type, final int programPoint) {
        // nasgen generated primitive fields like Math.PI have only one known unchangeable primitive type
        if (objectGetter == null) {
            return getOptimisticPrimitiveGetter(type, programPoint);
        }

        return debug(
            createGetter(
                getCurrentType(),
                type,
                primitiveGetter,
                objectGetter,
                programPoint),
            getCurrentType(),
            type,
            "get");
    }

    private MethodHandle getOptimisticPrimitiveGetter(final Class<?> type, final int programPoint) {
        final MethodHandle g = getGetter(getCurrentType());
        return MH.asType(OptimisticReturnFilters.filterOptimisticReturnValue(g, type, programPoint), g.type().changeReturnType(type));
    }

    private Property getWiderProperty(final Class<?> type) {
        return copy(type); //invalidate cache of new property

    }

    private PropertyMap getWiderMap(final PropertyMap oldMap, final Property newProperty) {
        final PropertyMap newMap = oldMap.replaceProperty(this, newProperty);
        assert oldMap.size() > 0;
        assert newMap.size() == oldMap.size();
        return newMap;
    }

    // the final three arguments are for debug printout purposes only
    @SuppressWarnings("unused")
    private static Object replaceMap(final Object sobj, final PropertyMap newMap) {
        ((ScriptObject)sobj).setMap(newMap);
        return sobj;
    }

    @SuppressWarnings("unused")
    private static Object invalidateSwitchPoint(final Object obj, final SwitchPoint sp) {
        SwitchPoint.invalidateAll(new SwitchPoint[] { sp });
        return obj;
    }

    private MethodHandle generateSetter(final Class<?> forType, final Class<?> type) {
        return debug(createSetter(forType, type, primitiveSetter, objectSetter), getCurrentType(), type, "set");
    }

    /**
     * Is this property of the undefined type?
     * @return true if undefined
     */
    protected final boolean isUndefined() {
        return getCurrentType() == null;
    }

    @Override
    public MethodHandle getSetter(final Class<?> type, final PropertyMap currentMap) {
        final int      i       = getAccessorTypeIndex(type);
        final int      ci      = isUndefined() ? -1 : getAccessorTypeIndex(getCurrentType());
        final Class<?> forType = isUndefined() ? type : getCurrentType();

        //if we are asking for an object setter, but are still a primitive type, we might try to box it
        MethodHandle mh;
        if (needsInvalidator(i, ci)) {
            final Property     newProperty = getWiderProperty(type);
            final PropertyMap  newMap      = getWiderMap(currentMap, newProperty);

            final MethodHandle widerSetter = newProperty.getSetter(type, newMap);
            final Class<?>     ct = getCurrentType();
            mh = MH.filterArguments(widerSetter, 0, MH.insertArguments(debugReplace(ct, type, currentMap, newMap) , 1, newMap));
            if (ct != null && ct.isPrimitive() && !type.isPrimitive()) {
                 mh = ObjectClassGenerator.createGuardBoxedPrimitiveSetter(ct, generateSetter(ct, ct), mh);
            }
        } else {
            mh = generateSetter(!forType.isPrimitive() ? Object.class : forType, type);
        }

        /**
         * Check if this is a special global name that requires switchpoint invalidation
         */
        final SwitchPoint ccb = getChangeCallback();
        if (ccb != null && ccb != NO_CHANGE_CALLBACK) {
            mh = MH.filterArguments(mh, 0, MH.insertArguments(debugInvalidate(getKey(), ccb), 1, changeCallback));
        }

        assert mh.type().returnType() == void.class : mh.type();

        return mh;
    }

    /**
     * Get the change callback for this property
     * @return switchpoint that is invalidated when property changes
     */
    protected SwitchPoint getChangeCallback() {
        if (changeCallback == null) {
            try {
                changeCallback = Global.instance().getChangeCallback(getKey());
            } catch (final NullPointerException e) {
                assert !"apply".equals(getKey()) && !"call".equals(getKey());
                //empty
            }
            if (changeCallback == null) {
                changeCallback = NO_CHANGE_CALLBACK;
            }
        }
        return changeCallback;
    }

    @Override
    public final boolean canChangeType() {
        if (OBJECT_FIELDS_ONLY) {
            return false;
        }
        return getCurrentType() != Object.class && (isConfigurable() || isWritable());
    }

    private boolean needsInvalidator(final int ti, final int fti) {
        return canChangeType() && ti > fti;
    }

    @Override
    public final void setCurrentType(final Class<?> currentType) {
        assert currentType != boolean.class : "no boolean storage support yet - fix this";
        this.currentType = currentType == null ? null : currentType.isPrimitive() ? currentType : Object.class;
    }

    @Override
    public Class<?> getCurrentType() {
        return currentType;
    }


    private MethodHandle debug(final MethodHandle mh, final Class<?> forType, final Class<?> type, final String tag) {
        if (!Context.DEBUG || !Global.hasInstance()) {
            return mh;
        }

        final Context context = Context.getContextTrusted();
        assert context != null;

        return context.addLoggingToHandle(
                ObjectClassGenerator.class,
                Level.INFO,
                mh,
                0,
                true,
                new Supplier<String>() {
                    @Override
                    public String get() {
                        return tag + " '" + getKey() + "' (property="+ Debug.id(this) + ", slot=" + getSlot() + " " + getClass().getSimpleName() + " forType=" + stripName(forType) + ", type=" + stripName(type) + ')';
                    }
                });
    }

    private MethodHandle debugReplace(final Class<?> oldType, final Class<?> newType, final PropertyMap oldMap, final PropertyMap newMap) {
        if (!Context.DEBUG || !Global.hasInstance()) {
            return REPLACE_MAP;
        }

        final Context context = Context.getContextTrusted();
        assert context != null;

        MethodHandle mh = context.addLoggingToHandle(
                ObjectClassGenerator.class,
                REPLACE_MAP,
                new Supplier<String>() {
                    @Override
                    public String get() {
                        return "Type change for '" + getKey() + "' " + oldType + "=>" + newType;
                    }
                });

        mh = context.addLoggingToHandle(
                ObjectClassGenerator.class,
                Level.FINEST,
                mh,
                Integer.MAX_VALUE,
                false,
                new Supplier<String>() {
                    @Override
                    public String get() {
                        return "Setting map " + Debug.id(oldMap) + " => " + Debug.id(newMap) + " " + oldMap + " => " + newMap;
                    }
                });
        return mh;
    }

    private static MethodHandle debugInvalidate(final String key, final SwitchPoint sp) {
        if (!Context.DEBUG || !Global.hasInstance()) {
            return INVALIDATE_SP;
        }

        final Context context = Context.getContextTrusted();
        assert context != null;

        return context.addLoggingToHandle(
                ObjectClassGenerator.class,
                INVALIDATE_SP,
                new Supplier<String>() {
                    @Override
                    public String get() {
                        return "Field change callback for " + key + " triggered: " + sp;
                    }
                });
    }

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(LOOKUP, AccessorProperty.class, name, MH.type(rtype, types));
    }
}
