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

import static jdk.nashorn.internal.codegen.ObjectClassGenerator.ACCESSOR_TYPES;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.DEBUG_FIELDS;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.LOG;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.OBJECT_FIELDS_ONLY;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.PRIMITIVE_TYPE;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.createGetter;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.createGuardBoxedPrimitiveSetter;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.createSetter;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.getAccessorType;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.getAccessorTypeIndex;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.getNumberOfAccessorTypes;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.lookup.MethodHandleFactory.stripName;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.lookup.MethodHandleFactory;

/**
 * An AccessorProperty is the most generic property type. An AccessorProperty is
 * represented as fields in a ScriptObject class.
 */
public class AccessorProperty extends Property {
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
    private static final MethodHandle REPLACE_MAP = findOwnMH("replaceMap", Object.class, Object.class, PropertyMap.class, String.class, Class.class, Class.class);

    private static final int NOOF_TYPES = getNumberOfAccessorTypes();

    /**
     * Properties in different maps for the same structure class will share their field getters and setters. This could
     * be further extended to other method handles that are looked up in the AccessorProperty constructor, but right now
     * these are the most frequently retrieved ones, and lookup of method handle natives only registers in the profiler
     * for them.
     */
    private static ClassValue<GettersSetters> GETTERS_SETTERS = new ClassValue<GettersSetters>() {
        @Override
        protected GettersSetters computeValue(Class<?> structure) {
            return new GettersSetters(structure);
        }
    };

    /** Property getter cache */
    private MethodHandle[] getters = new MethodHandle[NOOF_TYPES];

    private static final MethodType[] ACCESSOR_GETTER_TYPES = new MethodType[NOOF_TYPES];
    private static final MethodType[] ACCESSOR_SETTER_TYPES = new MethodType[NOOF_TYPES];
    private static final MethodType ACCESSOR_GETTER_PRIMITIVE_TYPE;
    private static final MethodType ACCESSOR_SETTER_PRIMITIVE_TYPE;
    private static final MethodHandle SPILL_ELEMENT_GETTER;
    private static final MethodHandle SPILL_ELEMENT_SETTER;

    private static final int SPILL_CACHE_SIZE = 8;
    private static final MethodHandle[] SPILL_ACCESSORS = new MethodHandle[SPILL_CACHE_SIZE * 2];

    static {
        MethodType getterPrimitiveType = null;
        MethodType setterPrimitiveType = null;

        for (int i = 0; i < NOOF_TYPES; i++) {
            final Type type = ACCESSOR_TYPES.get(i);
            ACCESSOR_GETTER_TYPES[i] = MH.type(type.getTypeClass(), Object.class);
            ACCESSOR_SETTER_TYPES[i] = MH.type(void.class, Object.class, type.getTypeClass());

            if (type == PRIMITIVE_TYPE) {
                getterPrimitiveType = ACCESSOR_GETTER_TYPES[i];
                setterPrimitiveType = ACCESSOR_SETTER_TYPES[i];
            }
        }

        ACCESSOR_GETTER_PRIMITIVE_TYPE = getterPrimitiveType;
        ACCESSOR_SETTER_PRIMITIVE_TYPE = setterPrimitiveType;

        final MethodType spillGetterType = MethodType.methodType(Object[].class, Object.class);
        final MethodHandle spillGetter = MH.asType(MH.getter(MethodHandles.lookup(), ScriptObject.class, "spill", Object[].class), spillGetterType);
        SPILL_ELEMENT_GETTER = MH.filterArguments(MH.arrayElementGetter(Object[].class), 0, spillGetter);
        SPILL_ELEMENT_SETTER = MH.filterArguments(MH.arrayElementSetter(Object[].class), 0, spillGetter);
    }

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
    private MethodHandle primitiveGetter;

    /** Seed setter for the primitive version of this field (in -Dnashorn.fields.dual=true mode) */
    private MethodHandle primitiveSetter;

    /** Seed getter for the Object version of this field */
    private MethodHandle objectGetter;

    /** Seed setter for the Object version of this field */
    private MethodHandle objectSetter;

    /**
     * Current type of this object, in object only mode, this is an Object.class. In dual-fields mode
     * null means undefined, and primitive types are allowed. The reason a special type is used for
     * undefined, is that are no bits left to represent it in primitive types
     */
    private Class<?> currentType;

    /**
     * Delegate constructor. This is used when adding properties to the Global scope, which
     * is necessary for outermost levels in a script (the ScriptObject is represented by
     * a JO-prefixed ScriptObject class, but the properties need to be in the Global scope
     * and are thus rebound with that as receiver
     *
     * @param property  accessor property to rebind
     * @param delegate  delegate object to rebind receiver to
     */
    public AccessorProperty(final AccessorProperty property, final Object delegate) {
        super(property);

        this.primitiveGetter = bindTo(property.primitiveGetter, delegate);
        this.primitiveSetter = bindTo(property.primitiveSetter, delegate);
        this.objectGetter    = bindTo(property.ensureObjectGetter(), delegate);
        this.objectSetter    = bindTo(property.ensureObjectSetter(), delegate);

        setCurrentType(property.getCurrentType());
    }

    /**
     * Constructor for spill properties. Array getters and setters will be created on demand.
     *
     * @param key    the property key
     * @param flags  the property flags
     * @param slot   spill slot
     */
    public AccessorProperty(final String key, final int flags, final int slot) {
        super(key, flags, slot);
        assert (flags & IS_SPILL) == IS_SPILL;

        setCurrentType(Object.class);
    }

    /**
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
    public AccessorProperty(final String key, final int flags, final int slot, final MethodHandle getter, final MethodHandle setter) {
        super(key, flags, slot);

        // we don't need to prep the setters these will never be invalidated as this is a nasgen
        // or known type getter/setter. No invalidations will take place

        final Class<?> getterType = getter.type().returnType();
        final Class<?> setterType = setter == null ? null : setter.type().parameterType(1);

        assert setterType == null || setterType == getterType;

        if (getterType.isPrimitive()) {
            for (int i = 0; i < NOOF_TYPES; i++) {
                getters[i] = MH.asType(
                    Lookup.filterReturnType(
                        getter,
                        getAccessorType(i).getTypeClass()),
                    ACCESSOR_GETTER_TYPES[i]);
            }
        } else {
            objectGetter = getter.type() != Lookup.GET_OBJECT_TYPE ? MH.asType(getter, Lookup.GET_OBJECT_TYPE) : getter;
            objectSetter = setter != null && setter.type() != Lookup.SET_OBJECT_TYPE ? MH.asType(setter, Lookup.SET_OBJECT_TYPE) : setter;
        }

        setCurrentType(getterType);
    }

    private static class GettersSetters {
        final MethodHandle[] getters;
        final MethodHandle[] setters;

        public GettersSetters(Class<?> structure) {
            final int fieldCount = ObjectClassGenerator.getFieldCount(structure);
            getters = new MethodHandle[fieldCount];
            setters = new MethodHandle[fieldCount];
            for(int i = 0; i < fieldCount; ++i) {
                final String fieldName = ObjectClassGenerator.getFieldName(i, Type.OBJECT);
                getters[i] = MH.asType(MH.getter(lookup, structure, fieldName, Type.OBJECT.getTypeClass()), Lookup.GET_OBJECT_TYPE);
                setters[i] = MH.asType(MH.setter(lookup, structure, fieldName, Type.OBJECT.getTypeClass()), Lookup.SET_OBJECT_TYPE);
            }
        }
    }

    /**
     * Constructor for dual field AccessorPropertys.
     *
     * @param key              property key
     * @param flags            property flags
     * @param structure        structure for objects associated with this property
     * @param slot             property field number or spill slot
     */
    public AccessorProperty(final String key, final int flags, final Class<?> structure, final int slot) {
        super(key, flags, slot);

        /*
         * primitiveGetter and primitiveSetter are only used in dual fields mode. Setting them to null also
         * works in dual field mode, it only means that the property never has a primitive
         * representation.
         */
        primitiveGetter = null;
        primitiveSetter = null;

        if (isParameter() && hasArguments()) {
            final MethodHandle arguments   = MH.getter(lookup, structure, "arguments", ScriptObject.class);

            objectGetter = MH.asType(MH.insertArguments(MH.filterArguments(ScriptObject.GET_ARGUMENT.methodHandle(), 0, arguments), 1, slot), Lookup.GET_OBJECT_TYPE);
            objectSetter = MH.asType(MH.insertArguments(MH.filterArguments(ScriptObject.SET_ARGUMENT.methodHandle(), 0, arguments), 1, slot), Lookup.SET_OBJECT_TYPE);
        } else {
            final GettersSetters gs = GETTERS_SETTERS.get(structure);
            objectGetter = gs.getters[slot];
            objectSetter = gs.setters[slot];

            if (!OBJECT_FIELDS_ONLY) {
                final String fieldNamePrimitive = ObjectClassGenerator.getFieldName(slot, PRIMITIVE_TYPE);
                final Class<?> typeClass = PRIMITIVE_TYPE.getTypeClass();
                primitiveGetter = MH.asType(MH.getter(lookup, structure, fieldNamePrimitive, typeClass), ACCESSOR_GETTER_PRIMITIVE_TYPE);
                primitiveSetter = MH.asType(MH.setter(lookup, structure, fieldNamePrimitive, typeClass), ACCESSOR_SETTER_PRIMITIVE_TYPE);
            }
        }

        Class<?> initialType = null;

        if (OBJECT_FIELDS_ONLY || isAlwaysObject()) {
            initialType = Object.class;
        } else if (!canBePrimitive()) {
            info(key + " cannot be primitive");
            initialType = Object.class;
        } else {
            info(key + " CAN be primitive");
            if (!canBeUndefined()) {
                info(key + " is always defined");
                initialType = int.class; //double works too for less type invalidation, but this requires experimentation, e.g. var x = 17; x += 2 will turn it into double now because of lack of range analysis
            }
        }

        // is always object means "is never initialized to undefined, and always of object type
        setCurrentType(initialType);
    }

    /**
     * Copy constructor
     *
     * @param property  source property
     */
    protected AccessorProperty(final AccessorProperty property) {
        super(property);

        this.getters         = property.getters;
        this.primitiveGetter = property.primitiveGetter;
        this.primitiveSetter = property.primitiveSetter;
        this.objectGetter    = property.objectGetter;
        this.objectSetter    = property.objectSetter;

        setCurrentType(property.getCurrentType());
    }

    private static MethodHandle bindTo(final MethodHandle mh, final Object receiver) {
        if (mh == null) {
            return null;
        }

        return MH.dropArguments(MH.bindTo(mh, receiver), 0, Object.class);
    }

    @Override
    protected Property copy() {
        return new AccessorProperty(this);
    }

    @Override
    public void setObjectValue(final ScriptObject self, final ScriptObject owner, final Object value, final boolean strict)  {
        if (isSpill()) {
            self.spill[getSlot()] = value;
        } else {
            try {
                getSetter(Object.class, self.getMap()).invokeExact((Object)self, value);
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Object getObjectValue(final ScriptObject self, final ScriptObject owner) {
        if (isSpill()) {
            return self.spill[getSlot()];
        }

        try {
            return getGetter(Object.class).invokeExact((Object)self);
        } catch (final Error|RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Spill getters and setters are lazily initialized, see JDK-8011630
    private MethodHandle ensureObjectGetter() {
        if (isSpill() && objectGetter == null) {
            objectGetter = getSpillGetter();
        }
        return objectGetter;
    }

    private MethodHandle ensureObjectSetter() {
        if (isSpill() && objectSetter == null) {
            objectSetter = getSpillSetter();
        }
        return objectSetter;
    }

    @Override
    public MethodHandle getGetter(final Class<?> type) {
        final int i = getAccessorTypeIndex(type);
        ensureObjectGetter();

        if (getters[i] == null) {
            getters[i] = debug(
                createGetter(currentType, type, primitiveGetter, objectGetter),
                currentType, type, "get");
        }

        return getters[i];
    }

    private Property getWiderProperty(final Class<?> type) {
        final AccessorProperty newProperty = new AccessorProperty(this);
        newProperty.invalidate(type);
        return newProperty;
    }

    private PropertyMap getWiderMap(final PropertyMap oldMap, final Property newProperty) {
        final PropertyMap newMap = oldMap.replaceProperty(this, newProperty);
        assert oldMap.size() > 0;
        assert newMap.size() == oldMap.size();
        return newMap;
    }

    // the final three arguments are for debug printout purposes only
    @SuppressWarnings("unused")
    private static Object replaceMap(final Object sobj, final PropertyMap newMap, final String key, final Class<?> oldType, final Class<?> newType) {
        if (DEBUG_FIELDS) {
            final PropertyMap oldMap = ((ScriptObject)sobj).getMap();
            info("Type change for '" + key + "' " + oldType + "=>" + newType);
            finest("setting map " + sobj + " from " + Debug.id(oldMap) + " to " + Debug.id(newMap) + " " + oldMap + " => " + newMap);
        }
        ((ScriptObject)sobj).setMap(newMap);
        return sobj;
    }

    private MethodHandle generateSetter(final Class<?> forType, final Class<?> type) {
        ensureObjectSetter();
        MethodHandle mh = createSetter(forType, type, primitiveSetter, objectSetter);
        mh = debug(mh, currentType, type, "set");
        return mh;
    }

    @Override
    public MethodHandle getSetter(final Class<?> type, final PropertyMap currentMap) {
        final int i            = getAccessorTypeIndex(type);
        final int ci           = currentType == null ? -1 : getAccessorTypeIndex(currentType);
        final Class<?> forType = currentType == null ? type : currentType;

        //if we are asking for an object setter, but are still a primitive type, we might try to box it
        MethodHandle mh;

        if (needsInvalidator(i, ci)) {
            final Property     newProperty = getWiderProperty(type);
            final PropertyMap  newMap      = getWiderMap(currentMap, newProperty);
            final MethodHandle widerSetter = newProperty.getSetter(type, newMap);
            final MethodHandle explodeTypeSetter = MH.filterArguments(widerSetter, 0, MH.insertArguments(REPLACE_MAP, 1, newMap, getKey(), currentType, type));
            if (currentType != null && currentType.isPrimitive() && type == Object.class) {
                //might try a box check on this to avoid widening field to object storage
                mh = createGuardBoxedPrimitiveSetter(currentType, generateSetter(currentType, currentType), explodeTypeSetter);
            } else {
                mh = explodeTypeSetter;
            }
        } else {
            mh = generateSetter(forType, type);
        }

        return mh;
    }

    @Override
    public boolean canChangeType() {
        if (OBJECT_FIELDS_ONLY) {
            return false;
        }
        return currentType != Object.class && (isConfigurable() || isWritable());
    }

    private boolean needsInvalidator(final int ti, final int fti) {
        return canChangeType() && ti > fti;
    }

    private void invalidate(final Class<?> newType) {
        getters = new MethodHandle[NOOF_TYPES];
        setCurrentType(newType);
    }

    private MethodHandle getSpillGetter() {
        final int slot = getSlot();
        MethodHandle getter = slot < SPILL_CACHE_SIZE ? SPILL_ACCESSORS[slot * 2] : null;
        if (getter == null) {
            getter = MH.insertArguments(SPILL_ELEMENT_GETTER, 1, slot);
            if (slot < SPILL_CACHE_SIZE) {
                SPILL_ACCESSORS[slot * 2 + 0] = getter;
            }
        }
        return getter;
    }

    private MethodHandle getSpillSetter() {
        final int slot = getSlot();
        MethodHandle setter = slot < SPILL_CACHE_SIZE ? SPILL_ACCESSORS[slot * 2 + 1] : null;
        if (setter == null) {
            setter = MH.insertArguments(SPILL_ELEMENT_SETTER, 1, slot);
            if (slot < SPILL_CACHE_SIZE) {
                SPILL_ACCESSORS[slot * 2 + 1] = setter;
            }
        }
        return setter;
    }

    private static void finest(final String str) {
        if (DEBUG_FIELDS) {
            LOG.finest(str);
        }
    }

    private static void info(final String str) {
        if (DEBUG_FIELDS) {
            LOG.info(str);
        }
    }

    private MethodHandle debug(final MethodHandle mh, final Class<?> forType, final Class<?> type, final String tag) {
        if (DEBUG_FIELDS) {
           return MethodHandleFactory.addDebugPrintout(
               LOG,
               mh,
               tag + " '" + getKey() + "' (property="+ Debug.id(this) + ", forType=" + stripName(forType) + ", type=" + stripName(type) + ')');
        }
        return mh;
    }

    private void setCurrentType(final Class<?> currentType) {
        this.currentType = currentType;
    }

    @Override
    public Class<?> getCurrentType() {
        return currentType;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(lookup, AccessorProperty.class, name, MH.type(rtype, types));
    }

}
