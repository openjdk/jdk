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

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCall;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.OBJECT_FIELDS_ONLY;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.referenceError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.JSType.UNDEFINED_DOUBLE;
import static jdk.nashorn.internal.runtime.JSType.UNDEFINED_INT;
import static jdk.nashorn.internal.runtime.JSType.UNDEFINED_LONG;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.CONFIGURABLE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.ENUMERABLE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.GET;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.SET;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.VALUE;
import static jdk.nashorn.internal.runtime.PropertyDescriptor.WRITABLE;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.isValid;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.getArrayIndex;
import static jdk.nashorn.internal.runtime.arrays.ArrayIndex.isValidArrayIndex;
import static jdk.nashorn.internal.runtime.linker.NashornGuards.explicitInstanceOfCheck;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.support.CallSiteDescriptorFactory;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.objects.AccessorPropertyDescriptor;
import jdk.nashorn.internal.objects.DataPropertyDescriptor;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.objects.NativeArray;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.LinkerCallSite;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.linker.NashornGuards;

/**
 * Base class for generic JavaScript objects.
 * <p>
 * Notes:
 * <ul>
 * <li>The map is used to identify properties in the object.</li>
 * <li>If the map is modified then it must be cloned and replaced.  This notifies
 *     any code that made assumptions about the object that things have changed.
 *     Ex. CallSites that have been validated must check to see if the map has
 *     changed (or a map from a different object type) and hence relink the method
 *     to call.</li>
 * <li>Modifications of the map include adding/deleting attributes or changing a
 *     function field value.</li>
 * </ul>
 */

public abstract class ScriptObject implements PropertyAccess {
    /** __proto__ special property name inside object literals. ES6 draft. */
    public static final String PROTO_PROPERTY_NAME   = "__proto__";

    /** Search fall back routine name for "no such method" */
    public static final String NO_SUCH_METHOD_NAME   = "__noSuchMethod__";

    /** Search fall back routine name for "no such property" */
    public static final String NO_SUCH_PROPERTY_NAME = "__noSuchProperty__";

    /** Per ScriptObject flag - is this a scope object? */
    public static final int IS_SCOPE       = 1 << 0;

    /** Per ScriptObject flag - is this an array object? */
    public static final int IS_ARRAY       = 1 << 1;

    /** Per ScriptObject flag - is this an arguments object? */
    public static final int IS_ARGUMENTS   = 1 << 2;

    /** Is length property not-writable? */
    public static final int IS_LENGTH_NOT_WRITABLE = 1 << 3;

    /** Is this a builtin object? */
    public static final int IS_BUILTIN = 1 << 4;

    /**
     * Spill growth rate - by how many elements does {@link ScriptObject#primitiveSpill} and
     * {@link ScriptObject#objectSpill} when full
     */
    public static final int SPILL_RATE = 8;

    /** Map to property information and accessor functions. Ordered by insertion. */
    private PropertyMap map;

    /** objects proto. */
    private ScriptObject proto;

    /** Object flags. */
    private int flags;

    /** Area for primitive properties added to object after instantiation, see {@link AccessorProperty} */
    protected long[]   primitiveSpill;

    /** Area for reference properties added to object after instantiation, see {@link AccessorProperty} */
    protected Object[] objectSpill;

    /**
     * Number of elements in the spill. This may be less than the spill array lengths, if not all of
     * the allocated memory is in use
     */
    private int spillLength;

    /** Indexed array data. */
    private ArrayData arrayData;

    /** Method handle to retrieve prototype of this object */
    public static final MethodHandle GETPROTO      = findOwnMH_V("getProto", ScriptObject.class);

    static final MethodHandle MEGAMORPHIC_GET    = findOwnMH_V("megamorphicGet", Object.class, String.class, boolean.class);
    static final MethodHandle GLOBALFILTER       = findOwnMH_S("globalFilter", Object.class, Object.class);

    private static final MethodHandle TRUNCATINGFILTER   = findOwnMH_S("truncatingFilter", Object[].class, int.class, Object[].class);
    private static final MethodHandle KNOWNFUNCPROPGUARDSELF = findOwnMH_S("knownFunctionPropertyGuardSelf", boolean.class, Object.class, PropertyMap.class, MethodHandle.class, ScriptFunction.class);
    private static final MethodHandle KNOWNFUNCPROPGUARDPROTO = findOwnMH_S("knownFunctionPropertyGuardProto", boolean.class, Object.class, PropertyMap.class, MethodHandle.class, int.class, ScriptFunction.class);

    private static final ArrayList<MethodHandle> PROTO_FILTERS = new ArrayList<>();

    /** Method handle for getting the array data */
    public static final Call GET_ARRAY          = virtualCall(MethodHandles.lookup(), ScriptObject.class, "getArray", ArrayData.class);

    /** Method handle for getting the property map - debugging purposes */
    public static final Call GET_MAP            = virtualCall(MethodHandles.lookup(), ScriptObject.class, "getMap", PropertyMap.class);

    /** Method handle for setting the array data */
    public static final Call SET_ARRAY          = virtualCall(MethodHandles.lookup(), ScriptObject.class, "setArray", void.class, ArrayData.class);

    /** Method handle for getting a function argument at a given index. Used from MapCreator */
    public static final Call GET_ARGUMENT       = virtualCall(MethodHandles.lookup(), ScriptObject.class, "getArgument", Object.class, int.class);

    /** Method handle for setting a function argument at a given index. Used from MapCreator */
    public static final Call SET_ARGUMENT       = virtualCall(MethodHandles.lookup(), ScriptObject.class, "setArgument", void.class, int.class, Object.class);

    /** Method handle for getting the proto of a ScriptObject */
    public static final Call GET_PROTO          = virtualCallNoLookup(ScriptObject.class, "getProto", ScriptObject.class);

    /** Method handle for getting the proto of a ScriptObject */
    public static final Call GET_PROTO_DEPTH    = virtualCallNoLookup(ScriptObject.class, "getProto", ScriptObject.class, int.class);

    /** Method handle for setting the proto of a ScriptObject */
    public static final Call SET_GLOBAL_OBJECT_PROTO = staticCallNoLookup(ScriptObject.class, "setGlobalObjectProto", void.class, ScriptObject.class);

    /** Method handle for setting the proto of a ScriptObject after checking argument */
    public static final Call SET_PROTO_FROM_LITERAL    = virtualCallNoLookup(ScriptObject.class, "setProtoFromLiteral", void.class, Object.class);

    /** Method handle for setting the user accessors of a ScriptObject */
    //TODO fastpath this
    public static final Call SET_USER_ACCESSORS = virtualCall(MethodHandles.lookup(), ScriptObject.class, "setUserAccessors", void.class, String.class, ScriptFunction.class, ScriptFunction.class);

    static final MethodHandle[] SET_SLOW = new MethodHandle[] {
        findOwnMH_V("set", void.class, Object.class, int.class, boolean.class),
        findOwnMH_V("set", void.class, Object.class, long.class, boolean.class),
        findOwnMH_V("set", void.class, Object.class, double.class, boolean.class),
        findOwnMH_V("set", void.class, Object.class, Object.class, boolean.class)
    };

    /** Method handle to reset the map of this ScriptObject */
    public static final Call SET_MAP = virtualCallNoLookup(ScriptObject.class, "setMap", void.class, PropertyMap.class);

    static final MethodHandle CAS_MAP           = findOwnMH_V("compareAndSetMap", boolean.class, PropertyMap.class, PropertyMap.class);
    static final MethodHandle EXTENSION_CHECK   = findOwnMH_V("extensionCheck", boolean.class, boolean.class, String.class);
    static final MethodHandle ENSURE_SPILL_SIZE = findOwnMH_V("ensureSpillSize", Object.class, int.class);

    /**
     * Constructor
     */
    public ScriptObject() {
        this(null);
    }

    /**
    * Constructor
    *
    * @param map {@link PropertyMap} used to create the initial object
    */
    public ScriptObject(final PropertyMap map) {
        if (Context.DEBUG) {
            ScriptObject.count++;
        }
        this.arrayData = ArrayData.EMPTY_ARRAY;
        this.setMap(map == null ? PropertyMap.newMap() : map);
    }

    /**
     * Constructor that directly sets the prototype to {@code proto} and property map to
     * {@code map} without invalidating the map as calling {@link #setProto(ScriptObject)}
     * would do. This should only be used for objects that are always constructed with the
     * same combination of prototype and property map.
     *
     * @param proto the prototype object
     * @param map intial {@link PropertyMap}
     */
    protected ScriptObject(final ScriptObject proto, final PropertyMap map) {
        this(map);
        this.proto = proto;
    }

    /**
     * Constructor used to instantiate spill properties directly. Used from
     * SpillObjectCreator.
     *
     * @param map            property maps
     * @param primitiveSpill primitive spills
     * @param objectSpill    reference spills
     */
    public ScriptObject(final PropertyMap map, final long[] primitiveSpill, final Object[] objectSpill) {
        this(map);
        this.primitiveSpill = primitiveSpill;
        this.objectSpill    = objectSpill;
        assert primitiveSpill.length == objectSpill.length : " primitive spill pool size is not the same length as object spill pool size";
        this.spillLength = spillAllocationLength(primitiveSpill.length);
    }

    /**
     * Check whether this is a global object
     * @return true if global
     */
    protected boolean isGlobal() {
        return false;
    }

    private static int alignUp(final int size, final int alignment) {
        return size + alignment - 1 & ~(alignment - 1);
    }

    /**
     * Given a number of properties, return the aligned to SPILL_RATE
     * buffer size required for the smallest spill pool needed to
     * house them
     * @param nProperties number of properties
     * @return property buffer length, a multiple of SPILL_RATE
     */
    public static int spillAllocationLength(final int nProperties) {
        return alignUp(nProperties, SPILL_RATE);
    }

    /**
     * Copy all properties from the source object with their receiver bound to the source.
     * This function was known as mergeMap
     *
     * @param source The source object to copy from.
     */
    public void addBoundProperties(final ScriptObject source) {
        addBoundProperties(source, source.getMap().getProperties());
    }

    /**
     * Copy all properties from the array with their receiver bound to the source.
     *
     * @param source The source object to copy from.
     * @param properties The array of properties to copy.
     */
    public void addBoundProperties(final ScriptObject source, final Property[] properties) {
        PropertyMap newMap = this.getMap();

        for (final Property property : properties) {
            final String key = property.getKey();
            final Property oldProp = newMap.findProperty(key);
            if (oldProp == null) {
                if (property instanceof UserAccessorProperty) {
                    // Note: we copy accessor functions to this object which is semantically different from binding.
                    final UserAccessorProperty prop = this.newUserAccessors(key, property.getFlags(), property.getGetterFunction(source), property.getSetterFunction(source));
                    newMap = newMap.addPropertyNoHistory(prop);
                } else {
                    newMap = newMap.addPropertyBind((AccessorProperty)property, source);
                }
            } else {
                // See ECMA section 10.5 Declaration Binding Instantiation
                // step 5 processing each function declaration.
                if (property.isFunctionDeclaration() && !oldProp.isConfigurable()) {
                     if (oldProp instanceof UserAccessorProperty ||
                         !(oldProp.isWritable() && oldProp.isEnumerable())) {
                         throw typeError("cant.redefine.property", key, ScriptRuntime.safeToString(this));
                     }
                }
            }
        }

        this.setMap(newMap);
    }

    /**
     * Copy all properties from the array with their receiver bound to the source.
     *
     * @param source The source object to copy from.
     * @param properties The collection of accessor properties to copy.
     */
    public void addBoundProperties(final Object source, final AccessorProperty[] properties) {
        PropertyMap newMap = this.getMap();

        for (final AccessorProperty property : properties) {
            final String key = property.getKey();

            if (newMap.findProperty(key) == null) {
                newMap = newMap.addPropertyBind(property, source);
            }
        }

        this.setMap(newMap);
    }

    /**
     * Bind the method handle to the specified receiver, while preserving its original type (it will just ignore the
     * first argument in lieu of the bound argument).
     * @param methodHandle Method handle to bind to.
     * @param receiver     Object to bind.
     * @return Bound method handle.
     */
    static MethodHandle bindTo(final MethodHandle methodHandle, final Object receiver) {
        return MH.dropArguments(MH.bindTo(methodHandle, receiver), 0, methodHandle.type().parameterType(0));
    }

    /**
     * Return a property iterator.
     * @return Property iterator.
     */
    public Iterator<String> propertyIterator() {
        return new KeyIterator(this);
    }

    /**
     * Return a property value iterator.
     * @return Property value iterator.
     */
    public Iterator<Object> valueIterator() {
        return new ValueIterator(this);
    }

    /**
     * ECMA 8.10.1 IsAccessorDescriptor ( Desc )
     * @return true if this has a {@link AccessorPropertyDescriptor} with a getter or a setter
     */
    public final boolean isAccessorDescriptor() {
        return has(GET) || has(SET);
    }

    /**
     * ECMA 8.10.2 IsDataDescriptor ( Desc )
     * @return true if this has a {@link DataPropertyDescriptor}, i.e. the object has a property value and is writable
     */
    public final boolean isDataDescriptor() {
        return has(VALUE) || has(WRITABLE);
    }

    /**
     * ECMA 8.10.3 IsGenericDescriptor ( Desc )
     * @return true if this has a descriptor describing an {@link AccessorPropertyDescriptor} or {@link DataPropertyDescriptor}
     */
    public final boolean isGenericDescriptor() {
        return isAccessorDescriptor() || isDataDescriptor();
    }

    /**
      * ECMA 8.10.5 ToPropertyDescriptor ( Obj )
      *
      * @return property descriptor
      */
    public final PropertyDescriptor toPropertyDescriptor() {
        final Global global = Context.getGlobal();

        final PropertyDescriptor desc;
        if (isDataDescriptor()) {
            if (has(SET) || has(GET)) {
                throw typeError(global, "inconsistent.property.descriptor");
            }

            desc = global.newDataDescriptor(UNDEFINED, false, false, false);
        } else if (isAccessorDescriptor()) {
            if (has(VALUE) || has(WRITABLE)) {
                throw typeError(global, "inconsistent.property.descriptor");
            }

            desc = global.newAccessorDescriptor(UNDEFINED, UNDEFINED, false, false);
        } else {
            desc = global.newGenericDescriptor(false, false);
        }

        return desc.fillFrom(this);
    }

    /**
     * ECMA 8.10.5 ToPropertyDescriptor ( Obj )
     *
     * @param global  global scope object
     * @param obj object to create property descriptor from
     *
     * @return property descriptor
     */
    public static PropertyDescriptor toPropertyDescriptor(final Global global, final Object obj) {
        if (obj instanceof ScriptObject) {
            return ((ScriptObject)obj).toPropertyDescriptor();
        }

        throw typeError(global, "not.an.object", ScriptRuntime.safeToString(obj));
    }

    /**
     * ECMA 8.12.1 [[GetOwnProperty]] (P)
     *
     * @param key property key
     *
     * @return Returns the Property Descriptor of the named own property of this
     * object, or undefined if absent.
     */
    public Object getOwnPropertyDescriptor(final String key) {
        final Property property = getMap().findProperty(key);

        final Global global = Context.getGlobal();

        if (property != null) {
            final ScriptFunction get   = property.getGetterFunction(this);
            final ScriptFunction set   = property.getSetterFunction(this);

            final boolean configurable = property.isConfigurable();
            final boolean enumerable   = property.isEnumerable();
            final boolean writable     = property.isWritable();

            if (property instanceof UserAccessorProperty) {
                return global.newAccessorDescriptor(
                    get != null ?
                        get :
                        UNDEFINED,
                    set != null ?
                        set :
                        UNDEFINED,
                    configurable,
                    enumerable);
            }

            return global.newDataDescriptor(getWithProperty(property), configurable, enumerable, writable);
        }

        final int index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return array.getDescriptor(global, index);
        }

        return UNDEFINED;
    }

    /**
     * ECMA 8.12.2 [[GetProperty]] (P)
     *
     * @param key property key
     *
     * @return Returns the fully populated Property Descriptor of the named property
     * of this object, or undefined if absent.
     */
    public Object getPropertyDescriptor(final String key) {
        final Object res = getOwnPropertyDescriptor(key);

        if (res != UNDEFINED) {
            return res;
        } else if (getProto() != null) {
            return getProto().getOwnPropertyDescriptor(key);
        } else {
            return UNDEFINED;
        }
    }

    /**
     * ECMA 8.12.9 [[DefineOwnProperty]] (P, Desc, Throw)
     *
     * @param key the property key
     * @param propertyDesc the property descriptor
     * @param reject is the property extensible - true means new definitions are rejected
     *
     * @return true if property was successfully defined
     */
    public boolean defineOwnProperty(final String key, final Object propertyDesc, final boolean reject) {
        final Global             global  = Context.getGlobal();
        final PropertyDescriptor desc    = toPropertyDescriptor(global, propertyDesc);
        final Object             current = getOwnPropertyDescriptor(key);
        final String             name    = JSType.toString(key);

        if (current == UNDEFINED) {
            if (isExtensible()) {
                // add a new own property
                addOwnProperty(key, desc);
                return true;
            }
            // new property added to non-extensible object
            if (reject) {
                throw typeError(global, "object.non.extensible", name, ScriptRuntime.safeToString(this));
            }
            return false;
        }

        // modifying an existing property
        final PropertyDescriptor currentDesc = (PropertyDescriptor)current;
        final PropertyDescriptor newDesc     = desc;

        if (newDesc.type() == PropertyDescriptor.GENERIC && !newDesc.has(CONFIGURABLE) && !newDesc.has(ENUMERABLE)) {
            // every descriptor field is absent
            return true;
        }

        if (newDesc.hasAndEquals(currentDesc)) {
            // every descriptor field of the new is same as the current
            return true;
        }

        if (!currentDesc.isConfigurable()) {
            if (newDesc.has(CONFIGURABLE) && newDesc.isConfigurable()) {
                // not configurable can not be made configurable
                if (reject) {
                    throw typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                }
                return false;
            }

            if (newDesc.has(ENUMERABLE) &&
                currentDesc.isEnumerable() != newDesc.isEnumerable()) {
                // cannot make non-enumerable as enumerable or vice-versa
                if (reject) {
                    throw typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                }
                return false;
            }
        }

        int propFlags = Property.mergeFlags(currentDesc, newDesc);
        Property property = getMap().findProperty(key);

        if (currentDesc.type() == PropertyDescriptor.DATA &&
                (newDesc.type() == PropertyDescriptor.DATA ||
                 newDesc.type() == PropertyDescriptor.GENERIC)) {
            if (!currentDesc.isConfigurable() && !currentDesc.isWritable()) {
                if (newDesc.has(WRITABLE) && newDesc.isWritable() ||
                    newDesc.has(VALUE) && !ScriptRuntime.sameValue(currentDesc.getValue(), newDesc.getValue())) {
                    if (reject) {
                        throw typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                    }
                    return false;
                }
            }

            final boolean newValue = newDesc.has(VALUE);
            final Object value     = newValue ? newDesc.getValue() : currentDesc.getValue();

            if (newValue && property != null) {
                // Temporarily clear flags.
                property = modifyOwnProperty(property, 0);
                set(key, value, false);
                //this might change the map if we change types of the property
                //hence we need to read it again. note that we should probably
                //have the setter return the new property throughout and in
                //general respect Property return values from modify and add
                //functions - which we don't seem to do at all here :-(
                //There is already a bug filed to generify PropertyAccess so we
                //can have the setter return e.g. a Property
                property = getMap().findProperty(key);
            }

            if (property == null) {
                // promoting an arrayData value to actual property
                addOwnProperty(key, propFlags, value);
                checkIntegerKey(key);
            } else {
                // Now set the new flags
                modifyOwnProperty(property, propFlags);
            }
        } else if (currentDesc.type() == PropertyDescriptor.ACCESSOR &&
                   (newDesc.type() == PropertyDescriptor.ACCESSOR ||
                    newDesc.type() == PropertyDescriptor.GENERIC)) {
            if (!currentDesc.isConfigurable()) {
                if (newDesc.has(PropertyDescriptor.GET) && !ScriptRuntime.sameValue(currentDesc.getGetter(), newDesc.getGetter()) ||
                    newDesc.has(PropertyDescriptor.SET) && !ScriptRuntime.sameValue(currentDesc.getSetter(), newDesc.getSetter())) {
                    if (reject) {
                        throw typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                    }
                    return false;
                }
            }
            // New set the new features.
            modifyOwnProperty(property, propFlags,
                                      newDesc.has(GET) ? newDesc.getGetter() : currentDesc.getGetter(),
                                      newDesc.has(SET) ? newDesc.getSetter() : currentDesc.getSetter());
        } else {
            // changing descriptor type
            if (!currentDesc.isConfigurable()) {
                // not configurable can not be made configurable
                if (reject) {
                    throw typeError(global, "cant.redefine.property", name, ScriptRuntime.safeToString(this));
                }
                return false;
            }

            propFlags = 0;

            // Preserve only configurable and enumerable from current desc
            // if those are not overridden in the new property descriptor.
            boolean value = newDesc.has(CONFIGURABLE) ? newDesc.isConfigurable() : currentDesc.isConfigurable();
            if (!value) {
                propFlags |= Property.NOT_CONFIGURABLE;
            }
            value = newDesc.has(ENUMERABLE)? newDesc.isEnumerable() : currentDesc.isEnumerable();
            if (!value) {
                propFlags |= Property.NOT_ENUMERABLE;
            }

            final int type = newDesc.type();
            if (type == PropertyDescriptor.DATA) {
                // get writable from the new descriptor
                value = newDesc.has(WRITABLE) && newDesc.isWritable();
                if (!value) {
                    propFlags |= Property.NOT_WRITABLE;
                }

                // delete the old property
                deleteOwnProperty(property);
                // add new data property
                addOwnProperty(key, propFlags, newDesc.getValue());
            } else if (type == PropertyDescriptor.ACCESSOR) {
                if (property == null) {
                    addOwnProperty(key, propFlags,
                                     newDesc.has(GET) ? newDesc.getGetter() : null,
                                     newDesc.has(SET) ? newDesc.getSetter() : null);
                } else {
                    // Modify old property with the new features.
                    modifyOwnProperty(property, propFlags,
                                        newDesc.has(GET) ? newDesc.getGetter() : null,
                                        newDesc.has(SET) ? newDesc.getSetter() : null);
                }
            }
        }

        checkIntegerKey(key);

        return true;
    }

    /**
     * Almost like defineOwnProperty(int,Object) for arrays this one does
     * not add 'gap' elements (like the array one does).
     *
     * @param index key for property
     * @param value value to define
     */
    public void defineOwnProperty(final int index, final Object value) {
        assert isValidArrayIndex(index) : "invalid array index";
        final long longIndex = ArrayIndex.toLongIndex(index);
        doesNotHaveEnsureDelete(longIndex, getArray().length(), false);
        setArray(getArray().ensure(longIndex));
        setArray(getArray().set(index, value, false));
    }

    private void checkIntegerKey(final String key) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            final ArrayData data = getArray();

            if (data.has(index)) {
                setArray(data.delete(index));
            }
        }
    }

    /**
      * Add a new property to the object.
      *
      * @param key          property key
      * @param propertyDesc property descriptor for property
      */
    public final void addOwnProperty(final String key, final PropertyDescriptor propertyDesc) {
        // Already checked that there is no own property with that key.
        PropertyDescriptor pdesc = propertyDesc;

        final int propFlags = Property.toFlags(pdesc);

        if (pdesc.type() == PropertyDescriptor.GENERIC) {
            final Global global = Context.getGlobal();
            final PropertyDescriptor dDesc = global.newDataDescriptor(UNDEFINED, false, false, false);

            dDesc.fillFrom((ScriptObject)pdesc);
            pdesc = dDesc;
        }

        final int type = pdesc.type();
        if (type == PropertyDescriptor.DATA) {
            addOwnProperty(key, propFlags, pdesc.getValue());
        } else if (type == PropertyDescriptor.ACCESSOR) {
            addOwnProperty(key, propFlags,
                    pdesc.has(GET) ? pdesc.getGetter() : null,
                    pdesc.has(SET) ? pdesc.getSetter() : null);
        }

        checkIntegerKey(key);
    }

    /**
     * Low level property API (not using property descriptors)
     * <p>
     * Find a property in the prototype hierarchy. Note: this is final and not
     * a good idea to override. If you have to, use
     * {jdk.nashorn.internal.objects.NativeArray{@link #getProperty(String)} or
     * {jdk.nashorn.internal.objects.NativeArray{@link #getPropertyDescriptor(String)} as the
     * overriding way to find array properties
     *
     * @see jdk.nashorn.internal.objects.NativeArray
     *
     * @param key  Property key.
     * @param deep Whether the search should look up proto chain.
     *
     * @return FindPropertyData or null if not found.
     */
    public final FindProperty findProperty(final String key, final boolean deep) {
        return findProperty(key, deep, false, this);
    }

    /**
     * Low level property API (not using property descriptors)
     * <p>
     * Find a property in the prototype hierarchy. Note: this is not a good idea
     * to override except as it was done in {@link WithObject}.
     * If you have to, use
     * {jdk.nashorn.internal.objects.NativeArray{@link #getProperty(String)} or
     * {jdk.nashorn.internal.objects.NativeArray{@link #getPropertyDescriptor(String)} as the
     * overriding way to find array properties
     *
     * @see jdk.nashorn.internal.objects.NativeArray
     *
     * @param key  Property key.
     * @param deep Whether the search should look up proto chain.
     * @param stopOnNonScope should a deep search stop on the first non-scope object?
     * @param start the object on which the lookup was originally initiated
     *
     * @return FindPropertyData or null if not found.
     */
    FindProperty findProperty(final String key, final boolean deep, final boolean stopOnNonScope, final ScriptObject start) {
        // if doing deep search, stop search on the first non-scope object if asked to do so
        if (stopOnNonScope && start != this && !isScope()) {
            return null;
        }

        final PropertyMap selfMap  = getMap();
        final Property    property = selfMap.findProperty(key);

        if (property != null) {
            return new FindProperty(start, this, property);
        }

        if (deep) {
            final ScriptObject myProto = getProto();
            if (myProto != null) {
                return myProto.findProperty(key, deep, stopOnNonScope, start);
            }
        }

        return null;
    }

    /**
     * Low level property API. This is similar to {@link #findProperty(String, boolean)} but returns a
     * {@code boolean} value instead of a {@link FindProperty} object.
     * @param key  Property key.
     * @param deep Whether the search should look up proto chain.
     * @return true if the property was found.
     */
    boolean hasProperty(final String key, final boolean deep) {
        if (getMap().findProperty(key) != null) {
            return true;
        }

        if (deep) {
            final ScriptObject myProto = getProto();
            if (myProto != null) {
                return myProto.hasProperty(key, deep);
            }
        }

        return false;
    }

    /**
     * Add a new property to the object.
     * <p>
     * This a more "low level" way that doesn't involve {@link PropertyDescriptor}s
     *
     * @param key             Property key.
     * @param propertyFlags   Property flags.
     * @param getter          Property getter, or null if not defined
     * @param setter          Property setter, or null if not defined
     *
     * @return New property.
     */
    public final Property addOwnProperty(final String key, final int propertyFlags, final ScriptFunction getter, final ScriptFunction setter) {
        return addOwnProperty(newUserAccessors(key, propertyFlags, getter, setter));
    }

    /**
     * Add a new property to the object.
     * <p>
     * This a more "low level" way that doesn't involve {@link PropertyDescriptor}s
     *
     * @param key             Property key.
     * @param propertyFlags   Property flags.
     * @param value           Value of property
     *
     * @return New property.
     */
    public final Property addOwnProperty(final String key, final int propertyFlags, final Object value) {
        return addSpillProperty(key, propertyFlags, value, true);
    }

    /**
     * Add a new property to the object.
     * <p>
     * This a more "low level" way that doesn't involve {@link PropertyDescriptor}s
     *
     * @param newProperty property to add
     *
     * @return New property.
     */
    public final Property addOwnProperty(final Property newProperty) {
        PropertyMap oldMap = getMap();
        while (true) {
            final PropertyMap newMap = oldMap.addProperty(newProperty);
            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
                final Property oldProperty = oldMap.findProperty(newProperty.getKey());

                if (oldProperty != null) {
                    return oldProperty;
                }
            } else {
                return newProperty;
            }
        }
    }

    private void erasePropertyValue(final Property property) {
        // Erase the property field value with undefined. If the property is defined
        // by user-defined accessors, we don't want to call the setter!!
        if (!(property instanceof UserAccessorProperty)) {
            assert property != null;
            property.setValue(this, this, UNDEFINED, false);
        }
    }

    /**
     * Delete a property from the object.
     *
     * @param property Property to delete.
     *
     * @return true if deleted.
     */
    public final boolean deleteOwnProperty(final Property property) {
        erasePropertyValue(property);
        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = oldMap.deleteProperty(property);

            if (newMap == null) {
                return false;
            }

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
            } else {
                // delete getter and setter function references so that we don't leak
                if (property instanceof UserAccessorProperty) {
                    ((UserAccessorProperty)property).setAccessors(this, getMap(), null);
                }
                Global.getConstants().delete(property.getKey());
                return true;
            }
        }

    }

    /**
     * Fast initialization functions for ScriptFunctions that are strict, to avoid
     * creating setters that probably aren't used. Inject directly into the spill pool
     * the defaults for "arguments" and "caller"
     *
     * @param key
     * @param propertyFlags
     * @param getter
     * @param setter
     */
    protected final void initUserAccessors(final String key, final int propertyFlags, final ScriptFunction getter, final ScriptFunction setter) {
        final int slot = spillLength;
        ensureSpillSize(spillLength); //arguments=slot0, caller=slot0
        objectSpill[slot] = new UserAccessorProperty.Accessors(getter, setter);
        final PropertyMap oldMap = getMap();
        Property    newProperty;
        PropertyMap newMap;
        do {
            newProperty = new UserAccessorProperty(key, propertyFlags, slot);
            newMap = oldMap.addProperty(newProperty);
        } while (!compareAndSetMap(oldMap, newMap));
    }

    /**
     * Modify a property in the object
     *
     * @param oldProperty    property to modify
     * @param propertyFlags  new property flags
     * @param getter         getter for {@link UserAccessorProperty}, null if not present or N/A
     * @param setter         setter for {@link UserAccessorProperty}, null if not present or N/A
     *
     * @return new property
     */
    public final Property modifyOwnProperty(final Property oldProperty, final int propertyFlags, final ScriptFunction getter, final ScriptFunction setter) {
        Property newProperty;

        if (oldProperty instanceof UserAccessorProperty) {
            final UserAccessorProperty uc = (UserAccessorProperty)oldProperty;
            final int slot = uc.getSlot();

            assert uc.getCurrentType() == Object.class;
            if (slot >= spillLength) {
                uc.setAccessors(this, getMap(), new UserAccessorProperty.Accessors(getter, setter));
            } else {
                final UserAccessorProperty.Accessors gs = uc.getAccessors(this); //this crashes
                if (gs == null) {
                    uc.setAccessors(this, getMap(), new UserAccessorProperty.Accessors(getter, setter));
                } else {
                    //reuse existing getter setter for speed
                    gs.set(getter, setter);
                    if (uc.getFlags() == propertyFlags) {
                        return oldProperty;
                    }
                }
            }
            newProperty = new UserAccessorProperty(uc.getKey(), propertyFlags, slot);
        } else {
            // erase old property value and create new user accessor property
            erasePropertyValue(oldProperty);
            newProperty = newUserAccessors(oldProperty.getKey(), propertyFlags, getter, setter);
        }

        return modifyOwnProperty(oldProperty, newProperty);
    }

    /**
      * Modify a property in the object
      *
      * @param oldProperty    property to modify
      * @param propertyFlags  new property flags
      *
      * @return new property
      */
    public final Property modifyOwnProperty(final Property oldProperty, final int propertyFlags) {
        return modifyOwnProperty(oldProperty, oldProperty.setFlags(propertyFlags));
    }

    /**
     * Modify a property in the object, replacing a property with a new one
     *
     * @param oldProperty   property to replace
     * @param newProperty   property to replace it with
     *
     * @return new property
     */
    private Property modifyOwnProperty(final Property oldProperty, final Property newProperty) {
        if (oldProperty == newProperty) {
            return newProperty; //nop
        }

        assert newProperty.getKey().equals(oldProperty.getKey()) : "replacing property with different key";

        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = oldMap.replaceProperty(oldProperty, newProperty);

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
                final Property oldPropertyLookup = oldMap.findProperty(oldProperty.getKey());

                if (oldPropertyLookup != null && oldPropertyLookup.equals(newProperty)) {
                    return oldPropertyLookup;
                }
            } else {
                return newProperty;
            }
        }
    }

    /**
     * Update getter and setter in an object literal.
     *
     * @param key    Property key.
     * @param getter {@link UserAccessorProperty} defined getter, or null if none
     * @param setter {@link UserAccessorProperty} defined setter, or null if none
     */
    public final void setUserAccessors(final String key, final ScriptFunction getter, final ScriptFunction setter) {
        final Property oldProperty = getMap().findProperty(key);
        if (oldProperty instanceof UserAccessorProperty) {
            modifyOwnProperty(oldProperty, oldProperty.getFlags(), getter, setter);
        } else {
            addOwnProperty(newUserAccessors(key, oldProperty != null ? oldProperty.getFlags() : 0, getter, setter));
        }
    }

    private static int getIntValue(final FindProperty find, final int programPoint) {
        final MethodHandle getter = find.getGetter(int.class, programPoint);
        if (getter != null) {
            try {
                return (int)getter.invokeExact((Object)find.getGetterReceiver());
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return UNDEFINED_INT;
    }

    private static long getLongValue(final FindProperty find, final int programPoint) {
        final MethodHandle getter = find.getGetter(long.class, programPoint);
        if (getter != null) {
            try {
                return (long)getter.invokeExact((Object)find.getGetterReceiver());
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return UNDEFINED_LONG;
    }

    private static double getDoubleValue(final FindProperty find, final int programPoint) {
        final MethodHandle getter = find.getGetter(double.class, programPoint);
        if (getter != null) {
            try {
                return (double)getter.invokeExact((Object)find.getGetterReceiver());
            } catch (final Error|RuntimeException e) {
                throw e;
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return UNDEFINED_DOUBLE;
    }

    /**
     * Return methodHandle of value function for call.
     *
     * @param find      data from find property.
     * @param type      method type of function.
     * @param bindName  null or name to bind to second argument (property not found method.)
     *
     * @return value of property as a MethodHandle or null.
     */
    protected MethodHandle getCallMethodHandle(final FindProperty find, final MethodType type, final String bindName) {
        return getCallMethodHandle(find.getObjectValue(), type, bindName);
    }

    /**
     * Return methodHandle of value function for call.
     *
     * @param value     value of receiver, it not a {@link ScriptFunction} this will return null.
     * @param type      method type of function.
     * @param bindName  null or name to bind to second argument (property not found method.)
     *
     * @return value of property as a MethodHandle or null.
     */
    protected static MethodHandle getCallMethodHandle(final Object value, final MethodType type, final String bindName) {
        return value instanceof ScriptFunction ? ((ScriptFunction)value).getCallMethodHandle(type, bindName) : null;
    }

    /**
     * Get value using found property.
     *
     * @param property Found property.
     *
     * @return Value of property.
     */
    public final Object getWithProperty(final Property property) {
        return new FindProperty(this, this, property).getObjectValue();
    }

    /**
     * Get a property given a key
     *
     * @param key property key
     *
     * @return property for key
     */
    public final Property getProperty(final String key) {
        return getMap().findProperty(key);
    }

    /**
     * Overridden by {@link jdk.nashorn.internal.objects.NativeArguments} class (internal use.)
     * Used for argument access in a vararg function using parameter name.
     * Returns the argument at a given key (index)
     *
     * @param key argument index
     *
     * @return the argument at the given position, or undefined if not present
     */
    public Object getArgument(final int key) {
        return get(key);
    }

    /**
     * Overridden by {@link jdk.nashorn.internal.objects.NativeArguments} class (internal use.)
     * Used for argument access in a vararg function using parameter name.
     * Returns the argument at a given key (index)
     *
     * @param key   argument index
     * @param value the value to write at the given index
     */
    public void setArgument(final int key, final Object value) {
        set(key, value, false);
    }

    /**
     * Return the current context from the object's map.
     * @return Current context.
     */
    protected Context getContext() {
        return Context.fromClass(getClass());
    }

    /**
     * Return the map of an object.
     * @return PropertyMap object.
     */
    public final PropertyMap getMap() {
        return map;
    }

    /**
     * Set the initial map.
     * @param map Initial map.
     */
    public final void setMap(final PropertyMap map) {
        this.map = map;
    }

    /**
     * Conditionally set the new map if the old map is the same.
     * @param oldMap Map prior to manipulation.
     * @param newMap Replacement map.
     * @return true if the operation succeeded.
     */
    protected final boolean compareAndSetMap(final PropertyMap oldMap, final PropertyMap newMap) {
        if (oldMap == this.map) {
            this.map = newMap;
            return true;
        }
        return false;
     }

    /**
     * Return the __proto__ of an object.
     * @return __proto__ object.
     */
    public final ScriptObject getProto() {
        return proto;
    }

    /**
     * Get the proto of a specific depth
     * @param n depth
     * @return proto at given depth
     */
    public final ScriptObject getProto(final int n) {
        assert n > 0;
        ScriptObject p = getProto();
        for (int i = n; i-- > 0;) {
            p = p.getProto();
        }
        return p;
    }

    /**
     * Set the __proto__ of an object.
     * @param newProto new __proto__ to set.
     */
    public final void setProto(final ScriptObject newProto) {
        final ScriptObject oldProto = proto;

        if (oldProto != newProto) {
            proto = newProto;

            // Let current listeners know that the protototype has changed and set our map
            final PropertyListeners listeners = getMap().getListeners();
            if (listeners != null) {
                listeners.protoChanged();
            }
            // Replace our current allocator map with one that is associated with the new prototype.
            setMap(getMap().changeProto(newProto));
        }
    }

    /**
     * Set the initial __proto__ of this object. This should be used instead of
     * {@link #setProto} if it is known that the current property map will not be
     * used on a new object with any other parent property map, so we can pass over
     * property map invalidation/evolution.
     *
     * @param initialProto the initial __proto__ to set.
     */
    public void setInitialProto(final ScriptObject initialProto) {
        this.proto = initialProto;
    }

    /**
     * Invoked from generated bytecode to initialize the prototype of object literals to the global Object prototype.
     * @param obj the object literal that needs to have its prototype initialized to the global Object prototype.
     */
    public static void setGlobalObjectProto(final ScriptObject obj) {
        obj.setInitialProto(Global.objectPrototype());
    }

    /**
     * Set the __proto__ of an object with checks.
     * This is the built-in operation [[SetPrototypeOf]]
     * See ES6 draft spec: 9.1.2 [[SetPrototypeOf]] (V)
     *
     * @param newProto Prototype to set.
     */
    public final void setPrototypeOf(final Object newProto) {
        if (newProto == null || newProto instanceof ScriptObject) {
            if (! isExtensible()) {
                // okay to set same proto again - even if non-extensible

                if (newProto == getProto()) {
                    return;
                }
                throw typeError("__proto__.set.non.extensible", ScriptRuntime.safeToString(this));
            }

            // check for circularity
            ScriptObject p = (ScriptObject)newProto;
            while (p != null) {
                if (p == this) {
                    throw typeError("circular.__proto__.set", ScriptRuntime.safeToString(this));
                }
                p = p.getProto();
            }
            setProto((ScriptObject)newProto);
        } else {
            throw typeError("cant.set.proto.to.non.object", ScriptRuntime.safeToString(this), ScriptRuntime.safeToString(newProto));
        }
    }

    /**
     * Set the __proto__ of an object from an object literal.
     * See ES6 draft spec: B.3.1 __proto__ Property Names in
     * Object Initializers. Step 6 handling of "__proto__".
     *
     * @param newProto Prototype to set.
     */
    public final void setProtoFromLiteral(final Object newProto) {
        if (newProto == null || newProto instanceof ScriptObject) {
            setPrototypeOf(newProto);
        } else {
            // Some non-object, non-null. Then, we need to set
            // Object.prototype as the new __proto__
            //
            // var obj = { __proto__ : 34 };
            // print(obj.__proto__ === Object.prototype); // => true
            setPrototypeOf(Global.objectPrototype());
        }
    }

    /**
     * return an array of own property keys associated with the object.
     *
     * @param all True if to include non-enumerable keys.
     * @return Array of keys.
     */
    public final String[] getOwnKeys(final boolean all) {
        return getOwnKeys(all, null);
    }

    /**
     * return an array of own property keys associated with the object.
     *
     * @param all True if to include non-enumerable keys.
     * @param nonEnumerable set of non-enumerable properties seen already.Used
       to filter out shadowed, but enumerable properties from proto children.
     * @return Array of keys.
     */
    protected String[] getOwnKeys(final boolean all, final Set<String> nonEnumerable) {
        final List<Object> keys    = new ArrayList<>();
        final PropertyMap  selfMap = this.getMap();

        final ArrayData array  = getArray();
        final long length      = array.length();

        for (long i = 0; i < length; i = array.nextIndex(i)) {
            if (array.has((int)i)) {
                keys.add(JSType.toString(i));
            }
        }

        for (final Property property : selfMap.getProperties()) {
            final boolean enumerable = property.isEnumerable();
            final String key = property.getKey();
            if (all) {
                keys.add(key);
            } else if (enumerable) {
                // either we don't have non-enumerable filter set or filter set
                // does not contain the current property.
                if (nonEnumerable == null || !nonEnumerable.contains(key)) {
                    keys.add(key);
                }
            } else {
                // store this non-enumerable property for later proto walk
                if (nonEnumerable != null) {
                    nonEnumerable.add(key);
                }
            }
        }

        return keys.toArray(new String[keys.size()]);
    }

    /**
     * Check if this ScriptObject has array entries. This means that someone has
     * set values with numeric keys in the object.
     *
     * @return true if array entries exists.
     */
    public boolean hasArrayEntries() {
        return getArray().length() > 0 || getMap().containsArrayKeys();
    }

    /**
     * Return the valid JavaScript type name descriptor
     *
     * @return "Object"
     */
    public String getClassName() {
        return "Object";
    }

    /**
     * {@code length} is a well known property. This is its getter.
     * Note that this *may* be optimized by other classes
     *
     * @return length property value for this ScriptObject
     */
    public Object getLength() {
        return get("length");
    }

    /**
     * Stateless toString for ScriptObjects.
     *
     * @return string description of this object, e.g. {@code [object Object]}
     */
    public String safeToString() {
        return "[object " + getClassName() + "]";
    }

    /**
     * Return the default value of the object with a given preferred type hint.
     * The preferred type hints are String.class for type String, Number.class
     * for type Number. <p>
     *
     * A <code>hint</code> of null means "no hint".
     *
     * ECMA 8.12.8 [[DefaultValue]](hint)
     *
     * @param typeHint the preferred type hint
     * @return the default value
     */
    public Object getDefaultValue(final Class<?> typeHint) {
        // We delegate to Global, as the implementation uses dynamic call sites to invoke object's "toString" and
        // "valueOf" methods, and in order to avoid those call sites from becoming megamorphic when multiple contexts
        // are being executed in a long-running program, we move the code and their associated dynamic call sites
        // (Global.TO_STRING and Global.VALUE_OF) into per-context code.
        return Context.getGlobal().getDefaultValue(this, typeHint);
    }

    /**
     * Checking whether a script object is an instance of another. Used
     * in {@link ScriptFunction} for hasInstance implementation, walks
     * the proto chain
     *
     * @param instance instace to check
     * @return true if 'instance' is an instance of this object
     */
    public boolean isInstance(final ScriptObject instance) {
        return false;
    }

    /**
     * Flag this ScriptObject as non extensible
     *
     * @return the object after being made non extensible
     */
    public ScriptObject preventExtensions() {
        PropertyMap oldMap = getMap();
        while (!compareAndSetMap(oldMap,  getMap().preventExtensions())) {
            oldMap = getMap();
        }

        //invalidate any fast array setters
        final ArrayData array = getArray();
        if (array != null) {
            array.invalidateSetters();
        }
        return this;
    }

    /**
     * Check whether if an Object (not just a ScriptObject) represents JavaScript array
     *
     * @param obj object to check
     *
     * @return true if array
     */
    public static boolean isArray(final Object obj) {
        return obj instanceof ScriptObject && ((ScriptObject)obj).isArray();
    }

    /**
     * Check if this ScriptObject is an array
     * @return true if array
     */
    public final boolean isArray() {
        return (flags & IS_ARRAY) != 0;
    }

    /**
     * Flag this ScriptObject as being an array
     */
    public final void setIsArray() {
        flags |= IS_ARRAY;
    }

    /**
     * Check if this ScriptObject is an {@code arguments} vector
     * @return true if arguments vector
     */
    public final boolean isArguments() {
        return (flags & IS_ARGUMENTS) != 0;
    }

    /**
     * Flag this ScriptObject as being an {@code arguments} vector
     */
    public final void setIsArguments() {
        flags |= IS_ARGUMENTS;
    }

    /**
     * Check if this object has non-writable length property
     *
     * @return {@code true} if 'length' property is non-writable
     */
    public final boolean isLengthNotWritable() {
        return (flags & IS_LENGTH_NOT_WRITABLE) != 0;
    }

    /**
     * Flag this object as having non-writable length property
     */
    public void setIsLengthNotWritable() {
        flags |= IS_LENGTH_NOT_WRITABLE;
    }

    /**
     * Get the {@link ArrayData} for this ScriptObject if it is an array
     * @return array data
     */
    public final ArrayData getArray() {
        return arrayData;
    }

    /**
     * Set the {@link ArrayData} for this ScriptObject if it is to be an array
     * @param arrayData the array data
     */
    public final void setArray(final ArrayData arrayData) {
        this.arrayData = arrayData;
    }

    /**
     * Check if this ScriptObject is extensible
     * @return true if extensible
     */
    public boolean isExtensible() {
        return getMap().isExtensible();
    }

    /**
     * ECMAScript 15.2.3.8 - seal implementation
     * @return the sealed ScriptObject
     */
    public ScriptObject seal() {
        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = getMap().seal();

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
            } else {
                setArray(ArrayData.seal(getArray()));
                return this;
            }
        }
    }

    /**
     * Check whether this ScriptObject is sealed
     * @return true if sealed
     */
    public boolean isSealed() {
        return getMap().isSealed();
    }

    /**
     * ECMA 15.2.39 - freeze implementation. Freeze this ScriptObject
     * @return the frozen ScriptObject
     */
    public ScriptObject freeze() {
        PropertyMap oldMap = getMap();

        while (true) {
            final PropertyMap newMap = getMap().freeze();

            if (!compareAndSetMap(oldMap, newMap)) {
                oldMap = getMap();
            } else {
                setArray(ArrayData.freeze(getArray()));
                return this;
            }
        }
    }

    /**
     * Check whether this ScriptObject is frozen
     * @return true if frozen
     */
    public boolean isFrozen() {
        return getMap().isFrozen();
    }


    /**
     * Flag this ScriptObject as scope
     */
    public final void setIsScope() {
        if (Context.DEBUG) {
            scopeCount++;
        }
        flags |= IS_SCOPE;
    }

    /**
     * Check whether this ScriptObject is scope
     * @return true if scope
     */
    public final boolean isScope() {
        return (flags & IS_SCOPE) != 0;
    }

    /**
     * Tag this script object as built in
     */
    public final void setIsBuiltin() {
        flags |= IS_BUILTIN;
    }

    /**
     * Check if this script object is built in
     * @return true if build in
     */
    public final boolean isBuiltin() {
        return (flags & IS_BUILTIN) != 0;
    }

    /**
     * Clears the properties from a ScriptObject
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @param strict strict mode or not
     */
    public void clear(final boolean strict) {
        final Iterator<String> iter = propertyIterator();
        while (iter.hasNext()) {
            delete(iter.next(), strict);
        }
    }

    /**
     * Checks if a property with a given key is present in a ScriptObject
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @param key the key to check for
     * @return true if a property with the given key exists, false otherwise
     */
    public boolean containsKey(final Object key) {
        return has(key);
    }

    /**
     * Checks if a property with a given value is present in a ScriptObject
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @param value value to check for
     * @return true if a property with the given value exists, false otherwise
     */
    public boolean containsValue(final Object value) {
        final Iterator<Object> iter = valueIterator();
        while (iter.hasNext()) {
            if (iter.next().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the set of {@literal <property, value>} entries that make up this
     * ScriptObject's properties
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @return an entry set of all the properties in this object
     */
    public Set<Map.Entry<Object, Object>> entrySet() {
        final Iterator<String> iter = propertyIterator();
        final Set<Map.Entry<Object, Object>> entries = new HashSet<>();
        while (iter.hasNext()) {
            final Object key = iter.next();
            entries.add(new AbstractMap.SimpleImmutableEntry<>(key, get(key)));
        }
        return Collections.unmodifiableSet(entries);
    }

    /**
     * Check whether a ScriptObject contains no properties
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @return true if object has no properties
     */
    public boolean isEmpty() {
        return !propertyIterator().hasNext();
    }

    /**
     * Return the set of keys (property names) for all properties
     * in this ScriptObject
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @return keySet of this ScriptObject
     */
    public Set<Object> keySet() {
        final Iterator<String> iter = propertyIterator();
        final Set<Object> keySet = new HashSet<>();
        while (iter.hasNext()) {
            keySet.add(iter.next());
        }
        return Collections.unmodifiableSet(keySet);
    }

    /**
     * Put a property in the ScriptObject
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @param key property key
     * @param value property value
     * @param strict strict mode or not
     * @return oldValue if property with same key existed already
     */
    public Object put(final Object key, final Object value, final boolean strict) {
        final Object oldValue = get(key);
        set(key, value, strict);
        return oldValue;
    }

    /**
     * Put several properties in the ScriptObject given a mapping
     * of their keys to their values
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @param otherMap a {@literal <key,value>} map of properties to add
     * @param strict strict mode or not
     */
    public void putAll(final Map<?, ?> otherMap, final boolean strict) {
        for (final Map.Entry<?, ?> entry : otherMap.entrySet()) {
            set(entry.getKey(), entry.getValue(), strict);
        }
    }

    /**
     * Remove a property from the ScriptObject.
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @param key the key of the property
     * @param strict strict mode or not
     * @return the oldValue of the removed property
     */
    public Object remove(final Object key, final boolean strict) {
        final Object oldValue = get(key);
        delete(key, strict);
        return oldValue;
    }

    /**
     * Return the size of the ScriptObject - i.e. the number of properties
     * it contains
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @return number of properties in ScriptObject
     */
    public int size() {
        int n = 0;
        for (final Iterator<String> iter = propertyIterator(); iter.hasNext(); iter.next()) {
            n++;
        }
        return n;
    }

    /**
     * Return the values of the properties in the ScriptObject
     * (java.util.Map-like method to help ScriptObjectMirror implementation)
     *
     * @return collection of values for the properties in this ScriptObject
     */
    public Collection<Object> values() {
        final List<Object>     values = new ArrayList<>(size());
        final Iterator<Object> iter   = valueIterator();
        while (iter.hasNext()) {
            values.add(iter.next());
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Lookup method that, given a CallSiteDescriptor, looks up the target
     * MethodHandle and creates a GuardedInvocation
     * with the appropriate guard(s).
     *
     * @param desc call site descriptor
     * @param request the link request
     *
     * @return GuardedInvocation for the callsite
     */
    public GuardedInvocation lookup(final CallSiteDescriptor desc, final LinkRequest request) {
        final int c = desc.getNameTokenCount();
        // JavaScript is "immune" to all currently defined Dynalink composite operation - getProp is the same as getElem
        // is the same as getMethod as JavaScript objects have a single namespace for all three. Therefore, we don't
        // care about them, and just link to whatever is the first operation.
        final String operator = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);
        // NOTE: we support getElem and setItem as JavaScript doesn't distinguish items from properties. Nashorn itself
        // emits "dyn:getProp:identifier" for "<expr>.<identifier>" and "dyn:getElem" for "<expr>[<expr>]", but we are
        // more flexible here and dispatch not on operation name (getProp vs. getElem), but rather on whether the
        // operation has an associated name or not.
        switch (operator) {
        case "getProp":
        case "getElem":
        case "getMethod":
            return c > 2 ? findGetMethod(desc, request, operator) : findGetIndexMethod(desc, request);
        case "setProp":
        case "setElem":
            return c > 2 ? findSetMethod(desc, request) : findSetIndexMethod(desc, request);
        case "call":
            return findCallMethod(desc, request);
        case "new":
            return findNewMethod(desc, request);
        case "callMethod":
            return findCallMethodMethod(desc, request);
        default:
            return null;
        }
    }

    /**
     * Find the appropriate New method for an invoke dynamic call.
     *
     * @param desc The invoke dynamic call site descriptor.
     * @param request The link request
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        return notAFunction();
    }

    /**
     * Find the appropriate CALL method for an invoke dynamic call.
     * This generates "not a function" always
     *
     * @param desc    the call site descriptor.
     * @param request the link request
     *
     * @return GuardedInvocation to be invoed at call site.
     */
    protected GuardedInvocation findCallMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        return notAFunction();
    }

    private GuardedInvocation notAFunction() {
        throw typeError("not.a.function", ScriptRuntime.safeToString(this));
    }

    /**
     * Find an implementation for a "dyn:callMethod" operation. Note that Nashorn internally never uses
     * "dyn:callMethod", but instead always emits two call sites in bytecode, one for "dyn:getMethod", and then another
     * one for "dyn:call". Explicit support for "dyn:callMethod" is provided for the benefit of potential external
     * callers. The implementation itself actually folds a "dyn:getMethod" method handle into a "dyn:call" method handle.
     *
     * @param desc    the call site descriptor.
     * @param request the link request
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findCallMethodMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        // R(P0, P1, ...)
        final MethodType callType = desc.getMethodType();
        // use type Object(P0) for the getter
        final CallSiteDescriptor getterType = desc.changeMethodType(MethodType.methodType(Object.class, callType.parameterType(0)));
        final GuardedInvocation getter = findGetMethod(getterType, request, "getMethod");

        // Object(P0) => Object(P0, P1, ...)
        final MethodHandle argDroppingGetter = MH.dropArguments(getter.getInvocation(), 1, callType.parameterList().subList(1, callType.parameterCount()));
        // R(Object, P0, P1, ...)
        final MethodHandle invoker = Bootstrap.createDynamicInvoker("dyn:call", callType.insertParameterTypes(0, argDroppingGetter.type().returnType()));
        // Fold Object(P0, P1, ...) into R(Object, P0, P1, ...) => R(P0, P1, ...)
        return getter.replaceMethods(MH.foldArguments(invoker, argDroppingGetter), getter.getGuard());
    }

    /**
     * Test whether this object contains in its prototype chain or is itself a with-object.
     * @return true if a with-object was found
     */
    final boolean hasWithScope() {
        if (isScope()) {
            for (ScriptObject obj = this; obj != null; obj = obj.getProto()) {
                if (obj instanceof WithObject) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Add a filter to the first argument of {@code methodHandle} that calls its {@link #getProto()} method
     * {@code depth} times.
     * @param methodHandle a method handle
     * @param depth        distance to target prototype
     * @return the filtered method handle
     */
    static MethodHandle addProtoFilter(final MethodHandle methodHandle, final int depth) {
        if (depth == 0) {
            return methodHandle;
        }
        final int listIndex = depth - 1; // We don't need 0-deep walker
        MethodHandle filter = listIndex < PROTO_FILTERS.size() ? PROTO_FILTERS.get(listIndex) : null;

        if (filter == null) {
            filter = addProtoFilter(GETPROTO, depth - 1);
            PROTO_FILTERS.add(null);
            PROTO_FILTERS.set(listIndex, filter);
        }

        return MH.filterArguments(methodHandle, 0, filter.asType(filter.type().changeReturnType(methodHandle.type().parameterType(0))));
    }

    //this will only return true if apply is still builtin
    private static SwitchPoint checkReservedName(final CallSiteDescriptor desc, final LinkRequest request) {
        final boolean isApplyToCall = NashornCallSiteDescriptor.isApplyToCall(desc);
        final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        if ("apply".equals(name) && isApplyToCall && Global.instance().isSpecialNameValid(name)) {
            assert Global.instance().getChangeCallback("apply") == Global.instance().getChangeCallback("call");
            return Global.instance().getChangeCallback("apply");
        }
        return null;
    }

    /**
     * Find the appropriate GET method for an invoke dynamic call.
     *
     * @param desc     the call site descriptor
     * @param request  the link request
     * @param operator operator for get: getProp, getMethod, getElem etc
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final LinkRequest request, final String operator) {
        final boolean explicitInstanceOfCheck = explicitInstanceOfCheck(desc, request);
        final String name;
        final SwitchPoint reservedNameSwitchPoint;

        reservedNameSwitchPoint = checkReservedName(desc, request);
        if (reservedNameSwitchPoint != null) {
            name = "call"; //turn apply into call, it is the builtin apply and has been modified to explode args
        } else {
            name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        }

        if (request.isCallSiteUnstable() || hasWithScope()) {
            return findMegaMorphicGetMethod(desc, name, "getMethod".equals(operator));
        }

        final FindProperty find = findProperty(name, true);
        MethodHandle mh;

        if (find == null) {
            switch (operator) {
            case "getProp":
                return noSuchProperty(desc, request);
            case "getMethod":
                return noSuchMethod(desc, request);
            case "getElem":
                return createEmptyGetter(desc, explicitInstanceOfCheck, name);
            default:
                throw new AssertionError(operator); // never invoked with any other operation
            }
        }

        final GuardedInvocation cinv = Global.getConstants().findGetMethod(find, this, desc, request, operator);
        if (cinv != null) {
            return cinv;
        }

        final Class<?> returnType = desc.getMethodType().returnType();
        final Property property   = find.getProperty();

        final int programPoint = NashornCallSiteDescriptor.isOptimistic(desc) ?
                NashornCallSiteDescriptor.getProgramPoint(desc) :
                UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

        mh = find.getGetter(returnType, programPoint);
        // Get the appropriate guard for this callsite and property.
        final MethodHandle guard = NashornGuards.getGuard(this, property, desc, explicitInstanceOfCheck);
        final ScriptObject owner = find.getOwner();
        final Class<ClassCastException> exception = explicitInstanceOfCheck ? null : ClassCastException.class;

        final SwitchPoint protoSwitchPoint;

        if (mh == null) {
            mh = Lookup.emptyGetter(returnType);
            protoSwitchPoint = getProtoSwitchPoint(name, owner);
        } else if (!find.isSelf()) {
            assert mh.type().returnType().equals(returnType) : "returntype mismatch for getter " + mh.type().returnType() + " != " + returnType;
            if (!property.hasGetterFunction(owner)) {
                // Add a filter that replaces the self object with the prototype owning the property.
                mh = addProtoFilter(mh, find.getProtoChainLength());
            }
            protoSwitchPoint = getProtoSwitchPoint(name, owner);
        } else {
            protoSwitchPoint = null;
        }

        assert OBJECT_FIELDS_ONLY || guard != null : "we always need a map guard here";

        final GuardedInvocation inv = new GuardedInvocation(mh, guard, protoSwitchPoint, exception);
        return inv.addSwitchPoint(reservedNameSwitchPoint);
    }

    private static GuardedInvocation findMegaMorphicGetMethod(final CallSiteDescriptor desc, final String name, final boolean isMethod) {
        Context.getContextTrusted().getLogger(ObjectClassGenerator.class).warning("Megamorphic getter: " + desc + " " + name + " " +isMethod);
        final MethodHandle invoker = MH.insertArguments(MEGAMORPHIC_GET, 1, name, isMethod);
        final MethodHandle guard   = getScriptObjectGuard(desc.getMethodType(), true);
        return new GuardedInvocation(invoker, guard);
    }

    @SuppressWarnings("unused")
    private Object megamorphicGet(final String key, final boolean isMethod) {
        final FindProperty find = findProperty(key, true);
        if (find != null) {
            return find.getObjectValue();
        }

        return isMethod ? getNoSuchMethod(key, INVALID_PROGRAM_POINT) : invokeNoSuchProperty(key, INVALID_PROGRAM_POINT);
    }

    /**
     * Find the appropriate GETINDEX method for an invoke dynamic call.
     *
     * @param desc    the call site descriptor
     * @param request the link request
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final MethodType callType                = desc.getMethodType();
        final Class<?>   returnType              = callType.returnType();
        final Class<?>   returnClass             = returnType.isPrimitive() ? returnType : Object.class;
        final Class<?>   keyClass                = callType.parameterType(1);
        final boolean    explicitInstanceOfCheck = explicitInstanceOfCheck(desc, request);

        final String name;
        if (returnClass.isPrimitive()) {
            //turn e.g. get with a double into getDouble
            final String returnTypeName = returnClass.getName();
            name = "get" + Character.toUpperCase(returnTypeName.charAt(0)) + returnTypeName.substring(1, returnTypeName.length());
        } else {
            name = "get";
        }

        final MethodHandle mh = findGetIndexMethodHandle(returnClass, name, keyClass, desc);
        return new GuardedInvocation(mh, getScriptObjectGuard(callType, explicitInstanceOfCheck), (SwitchPoint)null, explicitInstanceOfCheck ? null : ClassCastException.class);
    }

    private static MethodHandle getScriptObjectGuard(final MethodType type, final boolean explicitInstanceOfCheck) {
        return ScriptObject.class.isAssignableFrom(type.parameterType(0)) ? null : NashornGuards.getScriptObjectGuard(explicitInstanceOfCheck);
    }

    /**
     * Find a handle for a getIndex method
     * @param returnType     return type for getter
     * @param name           name
     * @param elementType    index type for getter
     * @param desc           call site descriptor
     * @return method handle for getter
     */
    protected MethodHandle findGetIndexMethodHandle(final Class<?> returnType, final String name, final Class<?> elementType, final CallSiteDescriptor desc) {
        if (!returnType.isPrimitive()) {
            return findOwnMH_V(getClass(), name, returnType, elementType);
        }

        return MH.insertArguments(
                findOwnMH_V(getClass(), name, returnType, elementType, int.class),
                2,
                NashornCallSiteDescriptor.isOptimistic(desc) ?
                        NashornCallSiteDescriptor.getProgramPoint(desc) :
                        INVALID_PROGRAM_POINT);
    }

    /**
     * Get a switch point for a property with the given {@code name} that will be invalidated when
     * the property definition is changed in this object's prototype chain. Returns {@code null} if
     * the property is defined in this object itself.
     *
     * @param name the property name
     * @param owner the property owner, null if property is not defined
     * @return a SwitchPoint or null
     */
    public final SwitchPoint getProtoSwitchPoint(final String name, final ScriptObject owner) {
        if (owner == this || getProto() == null) {
            return null;
        }

        for (ScriptObject obj = this; obj != owner && obj.getProto() != null; obj = obj.getProto()) {
            final ScriptObject parent = obj.getProto();
            parent.getMap().addListener(name, obj.getMap());
        }

        return getMap().getSwitchPoint(name);
    }

    /**
     * Find the appropriate SET method for an invoke dynamic call.
     *
     * @param desc    the call site descriptor
     * @param request the link request
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);

        if (request.isCallSiteUnstable() || hasWithScope()) {
            return findMegaMorphicSetMethod(desc, name);
        }

        final boolean scope                   = isScope();
        final boolean explicitInstanceOfCheck = explicitInstanceOfCheck(desc, request);

        /*
         * If doing property set on a scope object, we should stop proto search on the first
         * non-scope object. Without this, for example, when assigning "toString" on global scope,
         * we'll end up assigning it on it's proto - which is Object.prototype.toString !!
         *
         * toString = function() { print("global toString"); } // don't affect Object.prototype.toString
         */
        FindProperty find = findProperty(name, true, scope, this);

        // If it's not a scope search, then we don't want any inherited properties except those with user defined accessors.
        if (!scope && find != null && find.isInherited() && !(find.getProperty() instanceof UserAccessorProperty)) {
            // We should still check if inherited data property is not writable
            if (isExtensible() && !find.getProperty().isWritable()) {
                return createEmptySetMethod(desc, explicitInstanceOfCheck, "property.not.writable", false);
            }
            // Otherwise, forget the found property
            find = null;
        }

        if (find != null) {
            if (!find.getProperty().isWritable()) {
                // Existing, non-writable property
                return createEmptySetMethod(desc, explicitInstanceOfCheck, "property.not.writable", true);
            }
        } else {
            if (!isExtensible()) {
                return createEmptySetMethod(desc, explicitInstanceOfCheck, "object.non.extensible", false);
            }
        }

        final GuardedInvocation inv = new SetMethodCreator(this, find, desc, explicitInstanceOfCheck).createGuardedInvocation();

        final GuardedInvocation cinv = Global.getConstants().findSetMethod(find, this, inv, desc, request);
        if (cinv != null) {
            return cinv;
        }

        return inv;
    }

    private GuardedInvocation createEmptySetMethod(final CallSiteDescriptor desc, final boolean explicitInstanceOfCheck, final String strictErrorMessage, final boolean canBeFastScope) {
        final String  name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
         if (NashornCallSiteDescriptor.isStrict(desc)) {
           throw typeError(strictErrorMessage, name, ScriptRuntime.safeToString(this));
        }
        assert canBeFastScope || !NashornCallSiteDescriptor.isFastScope(desc);
        return new GuardedInvocation(
                Lookup.EMPTY_SETTER,
                NashornGuards.getMapGuard(getMap(), explicitInstanceOfCheck),
                getProtoSwitchPoint(name, null),
                explicitInstanceOfCheck ? null : ClassCastException.class);
    }

    @SuppressWarnings("unused")
    private boolean extensionCheck(final boolean isStrict, final String name) {
        if (isExtensible()) {
            return true; //go on and do the set. this is our guard
        } else if (isStrict) {
            //throw an error for attempting to do the set in strict mode
            throw typeError("object.non.extensible", name, ScriptRuntime.safeToString(this));
        } else {
            //not extensible, non strict - this is a nop
            return false;
        }
    }

    private GuardedInvocation findMegaMorphicSetMethod(final CallSiteDescriptor desc, final String name) {
        final MethodType        type = desc.getMethodType().insertParameterTypes(1, Object.class);
        //never bother with ClassCastExceptionGuard for megamorphic callsites
        final GuardedInvocation inv = findSetIndexMethod(getClass(), false, type, NashornCallSiteDescriptor.isStrict(desc));
        return inv.replaceMethods(MH.insertArguments(inv.getInvocation(), 1, name), inv.getGuard());
    }

    @SuppressWarnings("unused")
    private static Object globalFilter(final Object object) {
        ScriptObject sobj = (ScriptObject) object;
        while (sobj != null && !(sobj instanceof Global)) {
            sobj = sobj.getProto();
        }
        return sobj;
    }

    /**
     * Lookup function for the set index method, available for subclasses as well, e.g. {@link NativeArray}
     * provides special quick accessor linkage for continuous arrays that are represented as Java arrays
     *
     * @param desc    call site descriptor
     * @param request link request
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    protected GuardedInvocation findSetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) { // array, index, value
        return findSetIndexMethod(getClass(), explicitInstanceOfCheck(desc, request), desc.getMethodType(), NashornCallSiteDescriptor.isStrict(desc));
    }

    /**
     * Find the appropriate SETINDEX method for an invoke dynamic call.
     *
     * @param callType the method type at the call site
     * @param isStrict are we in strict mode?
     *
     * @return GuardedInvocation to be invoked at call site.
     */
    private static GuardedInvocation findSetIndexMethod(final Class<? extends ScriptObject> clazz, final boolean explicitInstanceOfCheck, final MethodType callType, final boolean isStrict) {
        assert callType.parameterCount() == 3;
        final Class<?> keyClass   = callType.parameterType(1);
        final Class<?> valueClass = callType.parameterType(2);

        MethodHandle methodHandle = findOwnMH_V(clazz, "set", void.class, keyClass, valueClass, boolean.class);
        methodHandle = MH.insertArguments(methodHandle, 3, isStrict);

        return new GuardedInvocation(methodHandle, getScriptObjectGuard(callType, explicitInstanceOfCheck), (SwitchPoint)null, explicitInstanceOfCheck ? null : ClassCastException.class);
    }

    /**
     * Fall back if a function property is not found.
     * @param desc The call site descriptor
     * @param request the link request
     * @return GuardedInvocation to be invoked at call site.
     */
    public GuardedInvocation noSuchMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final String       name      = desc.getNameToken(2);
        final FindProperty find      = findProperty(NO_SUCH_METHOD_NAME, true);
        final boolean      scopeCall = isScope() && NashornCallSiteDescriptor.isScope(desc);

        if (find == null) {
            return noSuchProperty(desc, request);
        }

        final boolean explicitInstanceOfCheck = explicitInstanceOfCheck(desc, request);

        final Object value = find.getObjectValue();
        if (!(value instanceof ScriptFunction)) {
            return createEmptyGetter(desc, explicitInstanceOfCheck, name);
        }

        final ScriptFunction func = (ScriptFunction)value;
        final Object         thiz = scopeCall && func.isStrict() ? ScriptRuntime.UNDEFINED : this;
        // TODO: It'd be awesome if we could bind "name" without binding "this".
        return new GuardedInvocation(
                MH.dropArguments(
                        MH.constant(
                                ScriptFunction.class,
                                func.makeBoundFunction(thiz, new Object[] { name })),
                        0,
                        Object.class),
                NashornGuards.getMapGuard(getMap(), explicitInstanceOfCheck),
                (SwitchPoint)null,
                explicitInstanceOfCheck ? null : ClassCastException.class);
    }

    /**
     * Fall back if a property is not found.
     * @param desc the call site descriptor.
     * @param request the link request
     * @return GuardedInvocation to be invoked at call site.
     */
    public GuardedInvocation noSuchProperty(final CallSiteDescriptor desc, final LinkRequest request) {
        final String       name        = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        final FindProperty find        = findProperty(NO_SUCH_PROPERTY_NAME, true);
        final boolean      scopeAccess = isScope() && NashornCallSiteDescriptor.isScope(desc);

        if (find != null) {
            final Object   value = find.getObjectValue();
            ScriptFunction func  = null;
            MethodHandle   mh    = null;

            if (value instanceof ScriptFunction) {
                func = (ScriptFunction)value;
                mh   = getCallMethodHandle(func, desc.getMethodType(), name);
            }

            if (mh != null) {
                assert func != null;
                if (scopeAccess && func.isStrict()) {
                    mh = bindTo(mh, UNDEFINED);
                }

                return new GuardedInvocation(
                        mh,
                        find.isSelf()?
                            getKnownFunctionPropertyGuardSelf(
                                getMap(),
                                find.getGetter(Object.class, INVALID_PROGRAM_POINT),
                                func)
                            :
                            //TODO this always does a scriptobject check
                            getKnownFunctionPropertyGuardProto(
                                getMap(),
                                find.getGetter(Object.class, INVALID_PROGRAM_POINT),
                                find.getProtoChainLength(),
                                func),
                        getProtoSwitchPoint(NO_SUCH_PROPERTY_NAME, find.getOwner()),
                        //TODO this doesn't need a ClassCastException as guard always checks script object
                        null);
            }
        }

        if (scopeAccess) {
            throw referenceError("not.defined", name);
        }

        return createEmptyGetter(desc, explicitInstanceOfCheck(desc, request), name);
    }

    /**
     * Invoke fall back if a property is not found.
     * @param name Name of property.
     * @param programPoint program point
     * @return Result from call.
     */
    protected Object invokeNoSuchProperty(final String name, final int programPoint) {
        final FindProperty find = findProperty(NO_SUCH_PROPERTY_NAME, true);

        Object ret = UNDEFINED;

        if (find != null) {
            final Object func = find.getObjectValue();

            if (func instanceof ScriptFunction) {
                ret = ScriptRuntime.apply((ScriptFunction)func, this, name);
            }
        }

        if (isValid(programPoint)) {
            throw new UnwarrantedOptimismException(ret, programPoint);
        }

        return ret;
    }


    /**
     * Get __noSuchMethod__ as a function bound to this object and {@code name} if it is defined.
     * @param name the method name
     * @return the bound function, or undefined
     */
    private Object getNoSuchMethod(final String name, final int programPoint) {
        final FindProperty find = findProperty(NO_SUCH_METHOD_NAME, true);

        if (find == null) {
            return invokeNoSuchProperty(name, programPoint);
        }

        final Object value = find.getObjectValue();
        if (!(value instanceof ScriptFunction)) {
            return UNDEFINED;
        }

        return ((ScriptFunction)value).makeBoundFunction(this, new Object[] {name});
    }

    private GuardedInvocation createEmptyGetter(final CallSiteDescriptor desc, final boolean explicitInstanceOfCheck, final String name) {
        if (NashornCallSiteDescriptor.isOptimistic(desc)) {
            throw new UnwarrantedOptimismException(UNDEFINED, NashornCallSiteDescriptor.getProgramPoint(desc), Type.OBJECT);
        }

        return new GuardedInvocation(Lookup.emptyGetter(desc.getMethodType().returnType()),
                NashornGuards.getMapGuard(getMap(), explicitInstanceOfCheck), getProtoSwitchPoint(name, null),
                explicitInstanceOfCheck ? null : ClassCastException.class);
    }

    private abstract static class ScriptObjectIterator <T extends Object> implements Iterator<T> {
        protected T[] values;
        protected final ScriptObject object;
        private int index;

        ScriptObjectIterator(final ScriptObject object) {
            this.object = object;
        }

        protected abstract void init();

        @Override
        public boolean hasNext() {
            if (values == null) {
                init();
            }
            return index < values.length;
        }

        @Override
        public T next() {
            if (values == null) {
                init();
            }
            return values[index++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class KeyIterator extends ScriptObjectIterator<String> {
        KeyIterator(final ScriptObject object) {
            super(object);
        }

        @Override
        protected void init() {
            final Set<String> keys = new LinkedHashSet<>();
            final Set<String> nonEnumerable = new HashSet<>();
            for (ScriptObject self = object; self != null; self = self.getProto()) {
                keys.addAll(Arrays.asList(self.getOwnKeys(false, nonEnumerable)));
            }
            this.values = keys.toArray(new String[keys.size()]);
        }
    }

    private static class ValueIterator extends ScriptObjectIterator<Object> {
        ValueIterator(final ScriptObject object) {
            super(object);
        }

        @Override
        protected void init() {
            final ArrayList<Object> valueList = new ArrayList<>();
            final Set<String> nonEnumerable = new HashSet<>();
            for (ScriptObject self = object; self != null; self = self.getProto()) {
                for (final String key : self.getOwnKeys(false, nonEnumerable)) {
                    valueList.add(self.get(key));
                }
            }
            this.values = valueList.toArray(new Object[valueList.size()]);
        }
    }

    /**
     * Add a spill property for the given key.
     * @param key           Property key.
     * @param propertyFlags Property flags.
     * @return Added property.
     */
    private Property addSpillProperty(final String key, final int propertyFlags, final Object value, final boolean hasInitialValue) {
        final PropertyMap propertyMap = getMap();
        final int fieldSlot  = propertyMap.getFreeFieldSlot();

        Property property;
        if (fieldSlot > -1) {
            property = hasInitialValue ?
                new AccessorProperty(key, propertyFlags, fieldSlot, this, value) :
                new AccessorProperty(key, propertyFlags, getClass(), fieldSlot);
            property = addOwnProperty(property);
        } else {
            final int spillSlot = propertyMap.getFreeSpillSlot();
            property = hasInitialValue ?
                new SpillProperty(key, propertyFlags, spillSlot, this, value) :
                new SpillProperty(key, propertyFlags, spillSlot);
            property = addOwnProperty(property);
            ensureSpillSize(property.getSlot());
        }
        return property;
    }

    /**
     * Add a spill entry for the given key.
     * @param key Property key.
     * @return Setter method handle.
     */
    MethodHandle addSpill(final Class<?> type, final String key) {
        return addSpillProperty(key, 0, null, false).getSetter(OBJECT_FIELDS_ONLY ? Object.class : type, getMap());
    }

    /**
     * Make sure arguments are paired correctly, with respect to more parameters than declared,
     * fewer parameters than declared and other things that JavaScript allows. This might involve
     * creating collectors.
     *
     * @param methodHandle method handle for invoke
     * @param callType     type of the call
     *
     * @return method handle with adjusted arguments
     */
    protected static MethodHandle pairArguments(final MethodHandle methodHandle, final MethodType callType) {
        return pairArguments(methodHandle, callType, null);
    }

    /**
     * Make sure arguments are paired correctly, with respect to more parameters than declared,
     * fewer parameters than declared and other things that JavaScript allows. This might involve
     * creating collectors.
     *
     * Make sure arguments are paired correctly.
     * @param methodHandle MethodHandle to adjust.
     * @param callType     MethodType of the call site.
     * @param callerVarArg true if the caller is vararg, false otherwise, null if it should be inferred from the
     * {@code callType}; basically, if the last parameter type of the call site is an array, it'll be considered a
     * variable arity call site. These are ordinarily rare; Nashorn code generator creates variable arity call sites
     * when the call has more than {@link LinkerCallSite#ARGLIMIT} parameters.
     *
     * @return method handle with adjusted arguments
     */
    public static MethodHandle pairArguments(final MethodHandle methodHandle, final MethodType callType, final Boolean callerVarArg) {
        final MethodType methodType = methodHandle.type();
        if (methodType.equals(callType.changeReturnType(methodType.returnType()))) {
            return methodHandle;
        }

        final int parameterCount = methodType.parameterCount();
        final int callCount      = callType.parameterCount();

        final boolean isCalleeVarArg = parameterCount > 0 && methodType.parameterType(parameterCount - 1).isArray();
        final boolean isCallerVarArg = callerVarArg != null ? callerVarArg.booleanValue() : callCount > 0 &&
                callType.parameterType(callCount - 1).isArray();

        if (isCalleeVarArg) {
            return isCallerVarArg ?
                methodHandle :
                MH.asCollector(methodHandle, Object[].class, callCount - parameterCount + 1);
        }

        if (isCallerVarArg) {
            return adaptHandleToVarArgCallSite(methodHandle, callCount);
        }

        if (callCount < parameterCount) {
            final int      missingArgs = parameterCount - callCount;
            final Object[] fillers     = new Object[missingArgs];

            Arrays.fill(fillers, UNDEFINED);

            if (isCalleeVarArg) {
                fillers[missingArgs - 1] = ScriptRuntime.EMPTY_ARRAY;
            }

            return MH.insertArguments(
                methodHandle,
                parameterCount - missingArgs,
                fillers);
        }

        if (callCount > parameterCount) {
            final int discardedArgs = callCount - parameterCount;

            final Class<?>[] discards = new Class<?>[discardedArgs];
            Arrays.fill(discards, Object.class);

            return MH.dropArguments(methodHandle, callCount - discardedArgs, discards);
        }

        return methodHandle;
    }

    static MethodHandle adaptHandleToVarArgCallSite(final MethodHandle mh, final int callSiteParamCount) {
        final int spreadArgs = mh.type().parameterCount() - callSiteParamCount + 1;
        return MH.filterArguments(
            MH.asSpreader(
            mh,
            Object[].class,
            spreadArgs),
            callSiteParamCount - 1,
            MH.insertArguments(
                TRUNCATINGFILTER,
                0,
                spreadArgs)
            );
    }

    @SuppressWarnings("unused")
    private static Object[] truncatingFilter(final int n, final Object[] array) {
        final int length = array == null ? 0 : array.length;
        if (n == length) {
            return array == null ? ScriptRuntime.EMPTY_ARRAY : array;
        }

        final Object[] newArray = new Object[n];

        if (array != null) {
            System.arraycopy(array, 0, newArray, 0, Math.min(n, length));
        }

        if (length < n) {
            final Object fill = UNDEFINED;

            for (int i = length; i < n; i++) {
                newArray[i] = fill;
            }
        }

        return newArray;
    }

    /**
      * Numeric length setter for length property
      *
      * @param newLength new length to set
      */
    public final void setLength(final long newLength) {
       final long arrayLength = getArray().length();
       if (newLength == arrayLength) {
           return;
       }

       if (newLength > arrayLength) {
           setArray(getArray().ensure(newLength - 1));
            if (getArray().canDelete(arrayLength, newLength - 1, false)) {
               setArray(getArray().delete(arrayLength, newLength - 1));
           }
           return;
       }

       if (newLength < arrayLength) {
           long actualLength = newLength;

           // Check for numeric keys in property map and delete them or adjust length, depending on whether
           // they're defined as configurable. See ES5 #15.4.5.2
           if (getMap().containsArrayKeys()) {

               for (long l = arrayLength - 1; l >= newLength; l--) {
                   final FindProperty find = findProperty(JSType.toString(l), false);

                   if (find != null) {

                       if (find.getProperty().isConfigurable()) {
                           deleteOwnProperty(find.getProperty());
                       } else {
                           actualLength = l + 1;
                           break;
                       }
                   }
               }
           }

           setArray(getArray().shrink(actualLength));
           getArray().setLength(actualLength);
       }
    }

    private int getInt(final int index, final String key, final int programPoint) {
        if (isValidArrayIndex(index)) {
            for (ScriptObject object = this; ; ) {
                if (object.getMap().containsArrayKeys()) {
                    final FindProperty find = object.findProperty(key, false, false, this);

                    if (find != null) {
                        return getIntValue(find, programPoint);
                    }
                }

                if ((object = object.getProto()) == null) {
                    break;
                }

                final ArrayData array = object.getArray();

                if (array.has(index)) {
                    return isValid(programPoint) ?
                        array.getIntOptimistic(index, programPoint) :
                        array.getInt(index);
                }
            }
        } else {
            final FindProperty find = findProperty(key, true);

            if (find != null) {
                return getIntValue(find, programPoint);
            }
        }

        return JSType.toInt32(invokeNoSuchProperty(key, programPoint));
    }

    @Override
    public int getInt(final Object key, final int programPoint) {
        final Object    primitiveKey = JSType.toPrimitive(key, String.class);
        final int       index        = getArrayIndex(primitiveKey);
        final ArrayData array        = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getIntOptimistic(index, programPoint) : array.getInt(index);
        }

        return getInt(index, JSType.toString(primitiveKey), programPoint);
    }

    @Override
    public int getInt(final double key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getIntOptimistic(index, programPoint) : array.getInt(index);
        }

        return getInt(index, JSType.toString(key), programPoint);
    }

    @Override
    public int getInt(final long key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getIntOptimistic(index, programPoint) : array.getInt(index);
        }

        return getInt(index, JSType.toString(key), programPoint);
    }

    @Override
    public int getInt(final int key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getIntOptimistic(key, programPoint) : array.getInt(key);
        }

        return getInt(index, JSType.toString(key), programPoint);
    }

    private long getLong(final int index, final String key, final int programPoint) {
        if (isValidArrayIndex(index)) {
            for (ScriptObject object = this; ; ) {
                if (object.getMap().containsArrayKeys()) {
                    final FindProperty find = object.findProperty(key, false, false, this);
                    if (find != null) {
                        return getLongValue(find, programPoint);
                    }
                }

                if ((object = object.getProto()) == null) {
                    break;
                }

                final ArrayData array = object.getArray();

                if (array.has(index)) {
                    return isValid(programPoint) ?
                        array.getLongOptimistic(index, programPoint) :
                        array.getLong(index);
                }
            }
        } else {
            final FindProperty find = findProperty(key, true);

            if (find != null) {
                return getLongValue(find, programPoint);
            }
        }

        return JSType.toLong(invokeNoSuchProperty(key, programPoint));
    }

    @Override
    public long getLong(final Object key, final int programPoint) {
        final Object    primitiveKey = JSType.toPrimitive(key, String.class);
        final int       index        = getArrayIndex(primitiveKey);
        final ArrayData array        = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getLongOptimistic(index, programPoint) : array.getLong(index);
        }

        return getLong(index, JSType.toString(primitiveKey), programPoint);
    }

    @Override
    public long getLong(final double key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getLongOptimistic(index, programPoint) : array.getLong(index);
        }

        return getLong(index, JSType.toString(key), programPoint);
    }

    @Override
    public long getLong(final long key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getLongOptimistic(index, programPoint) : array.getLong(index);
        }

        return getLong(index, JSType.toString(key), programPoint);
    }

    @Override
    public long getLong(final int key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getLongOptimistic(key, programPoint) : array.getLong(key);
        }

        return getLong(index, JSType.toString(key), programPoint);
    }

    private double getDouble(final int index, final String key, final int programPoint) {
        if (isValidArrayIndex(index)) {
            for (ScriptObject object = this; ; ) {
                if (object.getMap().containsArrayKeys()) {
                    final FindProperty find = object.findProperty(key, false, false, this);
                    if (find != null) {
                        return getDoubleValue(find, programPoint);
                    }
                }

                if ((object = object.getProto()) == null) {
                    break;
                }

                final ArrayData array = object.getArray();

                if (array.has(index)) {
                    return isValid(programPoint) ?
                        array.getDoubleOptimistic(index, programPoint) :
                        array.getDouble(index);
                }
            }
        } else {
            final FindProperty find = findProperty(key, true);

            if (find != null) {
                return getDoubleValue(find, programPoint);
            }
        }

        return JSType.toNumber(invokeNoSuchProperty(key, INVALID_PROGRAM_POINT));
    }

    @Override
    public double getDouble(final Object key, final int programPoint) {
        final Object    primitiveKey = JSType.toPrimitive(key, String.class);
        final int       index        = getArrayIndex(primitiveKey);
        final ArrayData array        = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getDoubleOptimistic(index, programPoint) : array.getDouble(index);
        }

        return getDouble(index, JSType.toString(primitiveKey), programPoint);
    }

    @Override
    public double getDouble(final double key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getDoubleOptimistic(index, programPoint) : array.getDouble(index);
        }

        return getDouble(index, JSType.toString(key), programPoint);
    }

    @Override
    public double getDouble(final long key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getDoubleOptimistic(index, programPoint) : array.getDouble(index);
        }

        return getDouble(index, JSType.toString(key), programPoint);
    }

    @Override
    public double getDouble(final int key, final int programPoint) {
        final int       index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return isValid(programPoint) ? array.getDoubleOptimistic(key, programPoint) : array.getDouble(key);
        }

        return getDouble(index, JSType.toString(key), programPoint);
    }

    private Object get(final int index, final String key) {
        if (isValidArrayIndex(index)) {
            for (ScriptObject object = this; ; ) {
                if (object.getMap().containsArrayKeys()) {
                    final FindProperty find = object.findProperty(key, false, false, this);

                    if (find != null) {
                        return find.getObjectValue();
                    }
                }

                if ((object = object.getProto()) == null) {
                    break;
                }

                final ArrayData array = object.getArray();

                if (array.has(index)) {
                    return array.getObject(index);
                }
            }
        } else {
            final FindProperty find = findProperty(key, true);

            if (find != null) {
                return find.getObjectValue();
            }
        }

        return invokeNoSuchProperty(key, INVALID_PROGRAM_POINT);
    }

    @Override
    public Object get(final Object key) {
        final Object    primitiveKey = JSType.toPrimitive(key, String.class);
        final int       index        = getArrayIndex(primitiveKey);
        final ArrayData array        = getArray();

        if (array.has(index)) {
            return array.getObject(index);
        }

        return get(index, JSType.toString(primitiveKey));
    }

    @Override
    public Object get(final double key) {
        final int index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return array.getObject(index);
        }

        return get(index, JSType.toString(key));
    }

    @Override
    public Object get(final long key) {
        final int index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return array.getObject(index);
        }

        return get(index, JSType.toString(key));
    }

    @Override
    public Object get(final int key) {
        final int index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            return array.getObject(index);
        }

        return get(index, JSType.toString(key));
    }

    private boolean doesNotHaveCheckArrayKeys(final long longIndex, final int value, final boolean strict) {
        if (getMap().containsArrayKeys()) {
            final String       key  = JSType.toString(longIndex);
            final FindProperty find = findProperty(key, true);
            if (find != null) {
                setObject(find, strict, key, value);
                return true;
            }
        }
        return false;
    }

    private boolean doesNotHaveCheckArrayKeys(final long longIndex, final long value, final boolean strict) {
        if (getMap().containsArrayKeys()) {
            final String       key  = JSType.toString(longIndex);
            final FindProperty find = findProperty(key, true);
            if (find != null) {
                setObject(find, strict, key, value);
                return true;
            }
        }
        return false;
    }

    private boolean doesNotHaveCheckArrayKeys(final long longIndex, final double value, final boolean strict) {
         if (getMap().containsArrayKeys()) {
            final String       key  = JSType.toString(longIndex);
            final FindProperty find = findProperty(key, true);
            if (find != null) {
                setObject(find, strict, key, value);
                return true;
            }
        }
        return false;
    }

    private boolean doesNotHaveCheckArrayKeys(final long longIndex, final Object value, final boolean strict) {
        if (getMap().containsArrayKeys()) {
            final String       key  = JSType.toString(longIndex);
            final FindProperty find = findProperty(key, true);
            if (find != null) {
                setObject(find, strict, key, value);
                return true;
            }
        }
        return false;
    }

    //value agnostic
    private boolean doesNotHaveEnsureLength(final long longIndex, final long oldLength, final boolean strict) {
        if (longIndex >= oldLength) {
            if (!isExtensible()) {
                if (strict) {
                    throw typeError("object.non.extensible", JSType.toString(longIndex), ScriptRuntime.safeToString(this));
                }
                return true;
            }
            setArray(getArray().ensure(longIndex));
        }
        return false;
    }

    private void doesNotHaveEnsureDelete(final long longIndex, final long oldLength, final boolean strict) {
        if (longIndex > oldLength) {
            ArrayData array = getArray();
            if (array.canDelete(oldLength, longIndex - 1, strict)) {
                array = array.delete(oldLength, longIndex - 1);
            }
            setArray(array);
        }
    }

    private void doesNotHave(final int index, final int value, final boolean strict) {
        final long oldLength = getArray().length();
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (!doesNotHaveCheckArrayKeys(longIndex, value, strict) && !doesNotHaveEnsureLength(longIndex, oldLength, strict)) {
            setArray(getArray().set(index, value, strict));
            doesNotHaveEnsureDelete(longIndex, oldLength, strict);
        }
    }

    private void doesNotHave(final int index, final long value, final boolean strict) {
        final long oldLength = getArray().length();
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (!doesNotHaveCheckArrayKeys(longIndex, value, strict) && !doesNotHaveEnsureLength(longIndex, oldLength, strict)) {
            setArray(getArray().set(index, value, strict));
            doesNotHaveEnsureDelete(longIndex, oldLength, strict);
        }
    }

    private void doesNotHave(final int index, final double value, final boolean strict) {
        final long oldLength = getArray().length();
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (!doesNotHaveCheckArrayKeys(longIndex, value, strict) && !doesNotHaveEnsureLength(longIndex, oldLength, strict)) {
            setArray(getArray().set(index, value, strict));
            doesNotHaveEnsureDelete(longIndex, oldLength, strict);
        }
    }

    private void doesNotHave(final int index, final Object value, final boolean strict) {
        final long oldLength = getArray().length();
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (!doesNotHaveCheckArrayKeys(longIndex, value, strict) && !doesNotHaveEnsureLength(longIndex, oldLength, strict)) {
            setArray(getArray().set(index, value, strict));
            doesNotHaveEnsureDelete(longIndex, oldLength, strict);
        }
    }

    /**
     * This is the most generic of all Object setters. Most of the others use this in some form.
     * TODO: should be further specialized
     *
     * @param find    found property
     * @param strict  are we in strict mode
     * @param key     property key
     * @param value   property value
     */
    public final void setObject(final FindProperty find, final boolean strict, final String key, final Object value) {
        FindProperty f = find;

        if (f != null && f.isInherited() && !(f.getProperty() instanceof UserAccessorProperty) && !isScope()) {
            // Setting a property should not modify the property in prototype unless this is a scope object.
            f = null;
        }

        if (f != null) {
            if (!f.getProperty().isWritable()) {
                if (strict) {
                    throw typeError("property.not.writable", key, ScriptRuntime.safeToString(this));
                }

                return;
            }

            f.setValue(value, strict);

        } else if (!isExtensible()) {
            if (strict) {
                throw typeError("object.non.extensible", key, ScriptRuntime.safeToString(this));
            }
        } else {
            ScriptObject sobj = this;
            // undefined scope properties are set in the global object.
            if (isScope()) {
                while (sobj != null && !(sobj instanceof Global)) {
                    sobj = sobj.getProto();
                }
                assert sobj != null : "no parent global object in scope";
            }
            //this will unbox any Number object to its primitive type in case the
            //property supports primitive types, so it doesn't matter that it comes
            //in as an Object.
            sobj.addSpillProperty(key, 0, value, true);
        }
    }

    @Override
    public void set(final Object key, final int value, final boolean strict) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int    index        = getArrayIndex(primitiveKey);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(primitiveKey);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final Object key, final long value, final boolean strict) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int    index        = getArrayIndex(primitiveKey);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(primitiveKey);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final Object key, final double value, final boolean strict) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int    index        = getArrayIndex(primitiveKey);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(primitiveKey);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final Object key, final Object value, final boolean strict) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int    index        = getArrayIndex(primitiveKey);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(primitiveKey);
        setObject(findProperty(propName, true), strict, propName, value);
    }

    @Override
    public void set(final double key, final int value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final double key, final long value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final double key, final double value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final double key, final Object value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, value);
    }

    @Override
    public void set(final long key, final int value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final long key, final long value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final long key, final double value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final long key, final Object value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, value);
    }

    @Override
    public void set(final int key, final int value, final boolean strict) {
        final int index = getArrayIndex(key);
        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }
            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final int key, final long value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final int key, final double value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, JSType.toObject(value));
    }

    @Override
    public void set(final int key, final Object value, final boolean strict) {
        final int index = getArrayIndex(key);

        if (isValidArrayIndex(index)) {
            if (getArray().has(index)) {
                setArray(getArray().set(index, value, strict));
            } else {
                doesNotHave(index, value, strict);
            }

            return;
        }

        final String propName = JSType.toString(key);
        setObject(findProperty(propName, true), strict, propName, value);
    }

    @Override
    public boolean has(final Object key) {
        final Object primitiveKey = JSType.toPrimitive(key);
        final int    index        = getArrayIndex(primitiveKey);
        return isValidArrayIndex(index) ? hasArrayProperty(index) : hasProperty(JSType.toString(primitiveKey), true);
    }

    @Override
    public boolean has(final double key) {
        final int index = getArrayIndex(key);
        return isValidArrayIndex(index) ? hasArrayProperty(index) : hasProperty(JSType.toString(key), true);
    }

    @Override
    public boolean has(final long key) {
        final int index = getArrayIndex(key);
        return isValidArrayIndex(index) ? hasArrayProperty(index) : hasProperty(JSType.toString(key), true);
    }

    @Override
    public boolean has(final int key) {
        final int index = getArrayIndex(key);
        return isValidArrayIndex(index) ? hasArrayProperty(index) : hasProperty(JSType.toString(key), true);
    }

    private boolean hasArrayProperty(final int index) {
        boolean hasArrayKeys = false;

        for (ScriptObject self = this; self != null; self = self.getProto()) {
            if (self.getArray().has(index)) {
                return true;
            }
            hasArrayKeys = hasArrayKeys || self.getMap().containsArrayKeys();
        }

        return hasArrayKeys && hasProperty(ArrayIndex.toKey(index), true);
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int    index        = getArrayIndex(primitiveKey);
        return isValidArrayIndex(index) ? hasOwnArrayProperty(index) : hasProperty(JSType.toString(primitiveKey), false);
    }

    @Override
    public boolean hasOwnProperty(final int key) {
        final int index = getArrayIndex(key);
        return isValidArrayIndex(index) ? hasOwnArrayProperty(index) : hasProperty(JSType.toString(key), false);
    }

    @Override
    public boolean hasOwnProperty(final long key) {
        final int index = getArrayIndex(key);
        return isValidArrayIndex(index) ? hasOwnArrayProperty(index) : hasProperty(JSType.toString(key), false);
    }

    @Override
    public boolean hasOwnProperty(final double key) {
        final int index = getArrayIndex(key);
        return isValidArrayIndex(index) ? hasOwnArrayProperty(index) : hasProperty(JSType.toString(key), false);
    }

    private boolean hasOwnArrayProperty(final int index) {
        return getArray().has(index) || getMap().containsArrayKeys() && hasProperty(ArrayIndex.toKey(index), false);
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        final int index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            if (array.canDelete(index, strict)) {
                setArray(array.delete(index));
                return true;
            }
            return false;
        }

        return deleteObject(JSType.toObject(key), strict);
    }

    @Override
    public boolean delete(final long key, final boolean strict) {
        final int index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            if (array.canDelete(index, strict)) {
                setArray(array.delete(index));
                return true;
            }
            return false;
        }

        return deleteObject(JSType.toObject(key), strict);
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        final int index = getArrayIndex(key);
        final ArrayData array = getArray();

        if (array.has(index)) {
            if (array.canDelete(index, strict)) {
                setArray(array.delete(index));
                return true;
            }
            return false;
        }

        return deleteObject(JSType.toObject(key), strict);
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final Object    primitiveKey = JSType.toPrimitive(key, String.class);
        final int       index        = getArrayIndex(primitiveKey);
        final ArrayData array        = getArray();

        if (array.has(index)) {
            if (array.canDelete(index, strict)) {
                setArray(array.delete(index));
                return true;
            }
            return false;
        }

        return deleteObject(primitiveKey, strict);
    }

    private boolean deleteObject(final Object key, final boolean strict) {
        final String propName = JSType.toString(key);
        final FindProperty find = findProperty(propName, false);

        if (find == null) {
            return true;
        }

        if (!find.getProperty().isConfigurable()) {
            if (strict) {
                throw typeError("cant.delete.property", propName, ScriptRuntime.safeToString(this));
            }
            return false;
        }

        final Property prop = find.getProperty();
        deleteOwnProperty(prop);

        return true;
    }

    /**
     * Make a new UserAccessorProperty property. getter and setter functions are stored in
     * this ScriptObject and slot values are used in property object.
     *
     * @param key the property name
     * @param propertyFlags attribute flags of the property
     * @param getter getter function for the property
     * @param setter setter function for the property
     * @return the newly created UserAccessorProperty
     */
    protected final UserAccessorProperty newUserAccessors(final String key, final int propertyFlags, final ScriptFunction getter, final ScriptFunction setter) {
        final UserAccessorProperty uc = getMap().newUserAccessors(key, propertyFlags);
        //property.getSetter(Object.class, getMap());
        uc.setAccessors(this, getMap(), new UserAccessorProperty.Accessors(getter, setter));
        return uc;
    }

    Object ensureSpillSize(final int slot) {
        if (slot < spillLength) {
            return this;
        }
        final int newLength = alignUp(slot + 1, SPILL_RATE);
        final Object[] newObjectSpill    = new Object[newLength];
        final long[]   newPrimitiveSpill = OBJECT_FIELDS_ONLY ? null : new long[newLength];

        if (objectSpill != null) {
            System.arraycopy(objectSpill, 0, newObjectSpill, 0, spillLength);
            if (!OBJECT_FIELDS_ONLY) {
                System.arraycopy(primitiveSpill, 0, newPrimitiveSpill, 0, spillLength);
            }
        }

        this.primitiveSpill = newPrimitiveSpill;
        this.objectSpill    = newObjectSpill;
        this.spillLength = newLength;

        return this;
    }

    private static MethodHandle findOwnMH_V(final Class<? extends ScriptObject> clazz, final String name, final Class<?> rtype, final Class<?>... types) {
        // TODO: figure out how can it work for NativeArray$Prototype etc.
        return MH.findVirtual(MethodHandles.lookup(), ScriptObject.class, name, MH.type(rtype, types));
    }

    private static MethodHandle findOwnMH_V(final String name, final Class<?> rtype, final Class<?>... types) {
        return findOwnMH_V(ScriptObject.class, name, rtype, types);
    }

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptObject.class, name, MH.type(rtype, types));
    }

    private static MethodHandle getKnownFunctionPropertyGuardSelf(final PropertyMap map, final MethodHandle getter, final ScriptFunction func) {
        return MH.insertArguments(KNOWNFUNCPROPGUARDSELF, 1, map, getter, func);
    }

    @SuppressWarnings("unused")
    private static boolean knownFunctionPropertyGuardSelf(final Object self, final PropertyMap map, final MethodHandle getter, final ScriptFunction func) {
        if (self instanceof ScriptObject && ((ScriptObject)self).getMap() == map) {
            try {
                return getter.invokeExact(self) == func;
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return false;
    }

    private static MethodHandle getKnownFunctionPropertyGuardProto(final PropertyMap map, final MethodHandle getter, final int depth, final ScriptFunction func) {
        return MH.insertArguments(KNOWNFUNCPROPGUARDPROTO, 1, map, getter, depth, func);
    }

    private static ScriptObject getProto(final ScriptObject self, final int depth) {
        ScriptObject proto = self;
        for (int d = 0; d < depth; d++) {
            proto = proto.getProto();
            if (proto == null) {
                return null;
            }
        }

        return proto;
    }

    @SuppressWarnings("unused")
    private static boolean knownFunctionPropertyGuardProto(final Object self, final PropertyMap map, final MethodHandle getter, final int depth, final ScriptFunction func) {
        if (self instanceof ScriptObject && ((ScriptObject)self).getMap() == map) {
            final ScriptObject proto = getProto((ScriptObject)self, depth);
            if (proto == null) {
                return false;
            }
            try {
                return getter.invokeExact((Object)proto) == func;
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        return false;
    }

    /** This is updated only in debug mode - counts number of {@code ScriptObject} instances created */
    private static int count;

    /** This is updated only in debug mode - counts number of {@code ScriptObject} instances created that are scope */
    private static int scopeCount;

    /**
     * Get number of {@code ScriptObject} instances created. If not running in debug
     * mode this is always 0
     *
     * @return number of ScriptObjects created
     */
    public static int getCount() {
        return count;
    }

    /**
     * Get number of scope {@code ScriptObject} instances created. If not running in debug
     * mode this is always 0
     *
     * @return number of scope ScriptObjects created
     */
    public static int getScopeCount() {
        return scopeCount;
    }

}
