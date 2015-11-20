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

package jdk.nashorn.internal.objects;

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import jdk.nashorn.internal.runtime.AccessorProperty;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;

/**
 * ECMA 10.6 Arguments Object.
 *
 * Arguments object used for non-strict mode functions. For strict mode, we use
 * a different implementation (@see NativeStrictArguments). In non-strict mode,
 * named argument access and index argument access (arguments[i]) are linked.
 * Modifications reflect on each other access -- till arguments indexed element
 * is deleted. After delete, there is no link between named access and indexed
 * access for that deleted index alone.
 */
public final class NativeArguments extends ScriptObject {

    private static final MethodHandle G$LENGTH = findOwnMH("G$length", Object.class, Object.class);
    private static final MethodHandle S$LENGTH = findOwnMH("S$length", void.class, Object.class, Object.class);
    private static final MethodHandle G$CALLEE = findOwnMH("G$callee", Object.class, Object.class);
    private static final MethodHandle S$CALLEE = findOwnMH("S$callee", void.class, Object.class, Object.class);

    private static final PropertyMap map$;

    static {
        final ArrayList<Property> properties = new ArrayList<>(2);
        properties.add(AccessorProperty.create("length", Property.NOT_ENUMERABLE, G$LENGTH, S$LENGTH));
        properties.add(AccessorProperty.create("callee", Property.NOT_ENUMERABLE, G$CALLEE, S$CALLEE));
        map$ = PropertyMap.newMap(properties);
    }

    static PropertyMap getInitialMap() {
        return map$;
    }

    private Object length;
    private Object callee;
    private final int numMapped;
    private final int numParams;

    // These are lazily initialized when delete is invoked on a mapped arg or an unmapped argument is set.
    private ArrayData unmappedArgs;
    private BitSet deleted;

    NativeArguments(final Object[] arguments, final Object callee, final int numParams, final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        setIsArguments();
        setArray(ArrayData.allocate(arguments));
        this.length = arguments.length;
        this.callee = callee;
        this.numMapped = Math.min(numParams, arguments.length);
        this.numParams = numParams;
    }

    @Override
    public String getClassName() {
        return "Arguments";
    }

    /**
     * getArgument is used for named argument access.
     */
    @Override
    public Object getArgument(final int key) {
        assert key >= 0 && key < numParams : "invalid argument index";
        return isMapped(key) ? getArray().getObject(key) : getUnmappedArg(key);
    }

    /**
     * setArgument is used for named argument set.
     */
    @Override
    public void setArgument(final int key, final Object value) {
        assert key >= 0 && key < numParams : "invalid argument index";
        if (isMapped(key)) {
            setArray(getArray().set(key, value, false));
        } else {
            setUnmappedArg(key, value);
        }
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? deleteMapped(index, strict) : super.delete(key, strict);
    }

    @Override
    public boolean delete(final long key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? deleteMapped(index, strict) : super.delete(key, strict);
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? deleteMapped(index, strict) : super.delete(key, strict);
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final Object primitiveKey = JSType.toPrimitive(key, String.class);
        final int index = ArrayIndex.getArrayIndex(primitiveKey);
        return isMapped(index) ? deleteMapped(index, strict) : super.delete(primitiveKey, strict);
    }

    /**
     * ECMA 15.4.5.1 [[DefineOwnProperty]] ( P, Desc, Throw ) as specialized in
     * ECMA 10.6 for Arguments object.
     */
    @Override
    public boolean defineOwnProperty(final Object key, final Object propertyDesc, final boolean reject) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (index >= 0) {
            final boolean isMapped = isMapped(index);
            final Object oldValue = isMapped ? getArray().getObject(index) : null;

            if (!super.defineOwnProperty(key, propertyDesc, false)) {
                if (reject) {
                    throw typeError("cant.redefine.property",  key.toString(), ScriptRuntime.safeToString(this));
                }
                return false;
            }

            if (isMapped) {
                // When mapped argument is redefined, if new descriptor is accessor property
                // or data-non-writable property, we have to "unmap" (unlink).
                final PropertyDescriptor desc = toPropertyDescriptor(Global.instance(), propertyDesc);
                if (desc.type() == PropertyDescriptor.ACCESSOR) {
                    setDeleted(index, oldValue);
                } else if (desc.has(PropertyDescriptor.WRITABLE) && !desc.isWritable()) {
                    // delete and set value from new descriptor if it has one, otherwise use old value
                    setDeleted(index, desc.has(PropertyDescriptor.VALUE) ? desc.getValue() : oldValue);
                } else if (desc.has(PropertyDescriptor.VALUE)) {
                    setArray(getArray().set(index, desc.getValue(), false));
                }
            }

            return true;
        }

        return super.defineOwnProperty(key, propertyDesc, reject);
    }

    // Internals below this point

    // We track deletions using a bit set (delete arguments[index])
    private boolean isDeleted(final int index) {
        return deleted != null && deleted.get(index);
    }

    private void setDeleted(final int index, final Object unmappedValue) {
        if (deleted == null) {
            deleted = new BitSet(numMapped);
        }
        deleted.set(index, true);
        setUnmappedArg(index, unmappedValue);
    }

    private boolean deleteMapped(final int index, final boolean strict) {
        final Object value = getArray().getObject(index);
        final boolean success = super.delete(index, strict);
        if (success) {
            setDeleted(index, value);
        }
        return success;
    }

    private Object getUnmappedArg(final int key) {
        assert key >= 0 && key < numParams;
        return unmappedArgs == null ? UNDEFINED : unmappedArgs.getObject(key);
    }

    private void setUnmappedArg(final int key, final Object value) {
        assert key >= 0 && key < numParams;
        if (unmappedArgs == null) {
            /*
             * Declared number of parameters may be more or less than the actual passed
             * runtime arguments count. We need to truncate or extend with undefined values.
             *
             * Example:
             *
             * // less declared params
             * (function (x) { print(arguments); })(20, 44);
             *
             * // more declared params
             * (function (x, y) { print(arguments); })(3);
             */
            final Object[] newValues = new Object[numParams];
            System.arraycopy(getArray().asObjectArray(), 0, newValues, 0, numMapped);
            if (numMapped < numParams) {
                Arrays.fill(newValues, numMapped, numParams, UNDEFINED);
            }
            this.unmappedArgs = ArrayData.allocate(newValues);
        }
        // Set value of argument
        unmappedArgs = unmappedArgs.set(key, value, false);
    }

    /**
     * Are arguments[index] and corresponding named parameter linked?
     *
     * In non-strict mode, arguments[index] and corresponding named param are "linked" or "mapped"
     * if the argument is provided by the caller. Modifications are tacked b/w each other - until
     * (delete arguments[index]) is used. Once deleted, the corresponding arg is no longer 'mapped'.
     * Please note that delete can happen only through the arguments array - named param can not
     * be deleted. (delete is one-way).
     */
    private boolean isMapped(final int index) {
        // in mapped named args and not marked as "deleted"
        return index >= 0 && index < numMapped && !isDeleted(index);
    }

    /**
     * Factory to create correct Arguments object based on strict mode.
     *
     * @param arguments the actual arguments array passed
     * @param callee the callee function that uses arguments object
     * @param numParams the number of declared (named) function parameters
     * @return Arguments Object
     */
    public static ScriptObject allocate(final Object[] arguments, final ScriptFunction callee, final int numParams) {
        // Strict functions won't always have a callee for arguments, and will pass null instead.
        final boolean isStrict = callee == null || callee.isStrict();
        final Global global = Global.instance();
        final ScriptObject proto = global.getObjectPrototype();
        if (isStrict) {
            return new NativeStrictArguments(arguments, numParams, proto, NativeStrictArguments.getInitialMap());
        }
        return new NativeArguments(arguments, callee, numParams, proto, NativeArguments.getInitialMap());
    }

    /**
     * Length getter
     * @param self self reference
     * @return length property value
     */
    public static Object G$length(final Object self) {
        if (self instanceof NativeArguments) {
            return ((NativeArguments)self).getArgumentsLength();
        }

        return 0;
    }

    /**
     * Length setter
     * @param self self reference
     * @param value value for length property
     */
    public static void S$length(final Object self, final Object value) {
        if (self instanceof NativeArguments) {
            ((NativeArguments)self).setArgumentsLength(value);
        }
    }

    /**
     * Callee getter
     * @param self self reference
     * @return value for callee property
     */
    public static Object G$callee(final Object self) {
        if (self instanceof NativeArguments) {
            return ((NativeArguments)self).getCallee();
        }
        return UNDEFINED;
    }

    /**
     * Callee setter
     * @param self self reference
     * @param value value for callee property
     */
    public static void S$callee(final Object self, final Object value) {
        if (self instanceof NativeArguments) {
            ((NativeArguments)self).setCallee(value);
        }
    }

    @Override
    public Object getLength() {
        return length;
    }

    private Object getArgumentsLength() {
        return length;
    }

    private void setArgumentsLength(final Object length) {
        this.length = length;
    }

    private Object getCallee() {
        return callee;
    }

    private void setCallee(final Object callee) {
        this.callee = callee;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), NativeArguments.class, name, MH.type(rtype, types));
    }
}
