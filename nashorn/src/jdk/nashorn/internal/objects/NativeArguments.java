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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.BitSet;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.ArrayIndex;
import jdk.nashorn.internal.lookup.Lookup;

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

    private static final PropertyMap nasgenmap$;

    static {
        PropertyMap map = PropertyMap.newMap(NativeArguments.class);
        map = Lookup.newProperty(map, "length", Property.NOT_ENUMERABLE, G$LENGTH, S$LENGTH);
        map = Lookup.newProperty(map, "callee", Property.NOT_ENUMERABLE, G$CALLEE, S$CALLEE);
        nasgenmap$ = map;
    }

    private Object length;
    private Object callee;
    private ArrayData namedArgs;
    // This is lazily initialized - only when delete is invoked at all
    private BitSet deleted;

    NativeArguments(final Object[] arguments, final Object callee, final int numParams) {
        super(nasgenmap$);
        setIsArguments();

        setArray(ArrayData.allocate(arguments));
        this.length = arguments.length;
        this.callee = callee;

        /**
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
        if (numParams > arguments.length) {
            Arrays.fill(newValues, UNDEFINED);
        }
        System.arraycopy(arguments, 0, newValues, 0, Math.min(newValues.length, arguments.length));
        this.namedArgs = ArrayData.allocate(newValues);

        // set Object.prototype as __proto__
        this.setProto(Global.objectPrototype());
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
        return namedArgs.has(key) ? namedArgs.getObject(key) : UNDEFINED;
    }

    /**
     * setArgument is used for named argument set.
     */
    @Override
    public void setArgument(final int key, final Object value) {
        if (namedArgs.has(key)) {
            namedArgs.set(key, value, false);
        }
    }

    @Override
    public int getInt(final Object key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getInt(index) : super.getInt(key);
    }

    @Override
    public int getInt(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getInt(index) : super.getInt(key);
    }

    @Override
    public int getInt(final long key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getInt(index) : super.getInt(key);
    }

    @Override
    public int getInt(final int key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getInt(index) : super.getInt(key);
    }

    @Override
    public long getLong(final Object key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getLong(index) : super.getLong(key);
    }

    @Override
    public long getLong(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getLong(index) : super.getLong(key);
    }

    @Override
    public long getLong(final long key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getLong(index) : super.getLong(key);
    }

    @Override
    public long getLong(final int key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getLong(index) : super.getLong(key);
    }

    @Override
    public double getDouble(final Object key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getDouble(index) : super.getDouble(key);
    }

    @Override
    public double getDouble(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getDouble(index) : super.getDouble(key);
    }

    @Override
    public double getDouble(final long key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getDouble(index) : super.getDouble(key);
    }

    @Override
    public double getDouble(final int key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getDouble(index) : super.getDouble(key);
    }

    @Override
    public Object get(final Object key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getObject(index) : super.get(key);
    }

    @Override
    public Object get(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getObject(index) : super.get(key);
    }

    @Override
    public Object get(final long key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getObject(index) : super.get(key);
    }

    @Override
    public Object get(final int key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) ? namedArgs.getObject(index) : super.get(key);
    }

    @Override
    public void set(final Object key, final int value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final Object key, final long value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final Object key, final double value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final Object key, final Object value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final double key, final int value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final double key, final long value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final double key, final double value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final double key, final Object value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final long key, final int value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final long key, final long value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final long key, final double value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final long key, final Object value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final int key, final int value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final int key, final long value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final int key, final double value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public void set(final int key, final Object value, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (isMapped(index)) {
            namedArgs = namedArgs.set(index, value, strict);
        } else {
            super.set(key, value, strict);
        }
    }

    @Override
    public boolean has(final Object key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) || super.has(key);
    }

    @Override
    public boolean has(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) || super.has(key);
    }

    @Override
    public boolean has(final long key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) || super.has(key);
    }

    @Override
    public boolean has(final int key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) || super.has(key);
    }

    @Override
    public boolean hasOwnProperty(final Object key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final int key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final long key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(final double key) {
        final int index = ArrayIndex.getArrayIndex(key);
        return isMapped(index) || super.hasOwnProperty(key);
    }

    @Override
    public boolean delete(final int key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        final boolean success = super.delete(key, strict);
        if (success && namedArgs.has(index)) {
            setDeleted(index);
        }
        return success;
    }

    @Override
    public boolean delete(final long key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        final boolean success = super.delete(key, strict);
        if (success && namedArgs.has(index)) {
            setDeleted(index);
        }
        return success;
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        final boolean success = super.delete(key, strict);
        if (success && namedArgs.has(index)) {
            setDeleted(index);
        }
        return success;
    }

    @Override
    public boolean delete(final Object key, final boolean strict) {
        final int index = ArrayIndex.getArrayIndex(key);
        final boolean success = super.delete(key, strict);
        if (success && namedArgs.has(index)) {
            setDeleted(index);
        }
        return success;
    }

    /**
     * ECMA 15.4.5.1 [[DefineOwnProperty]] ( P, Desc, Throw ) as specialized in
     * ECMA 10.6 for Arguments object.
     */
    @Override
    public boolean defineOwnProperty(final String key, final Object propertyDesc, final boolean reject) {
        final int index = ArrayIndex.getArrayIndex(key);
        if (index >= 0) {
            final boolean allowed = super.defineOwnProperty(key, propertyDesc, false);
            if (!allowed) {
                if (reject) {
                    throw typeError("cant.redefine.property",  key, ScriptRuntime.safeToString(this));
                }
                return false;
            }

            if (isMapped(index)) {
                // When mapped argument is redefined, if new descriptor is accessor property
                // or data-non-writable property, we have to "unmap" (unlink).
                final PropertyDescriptor desc = toPropertyDescriptor(Global.instance(), propertyDesc);
                if (desc.type() == PropertyDescriptor.ACCESSOR) {
                    setDeleted(index);
                } else {
                    // set "value" from new descriptor to named args
                    if (desc.has(PropertyDescriptor.VALUE)) {
                        namedArgs = namedArgs.set(index, desc.getValue(), false);
                    }

                    if (desc.has(PropertyDescriptor.WRITABLE) && !desc.isWritable()) {
                        setDeleted(index);
                    }
                }
            }

            return true;
        }

        return super.defineOwnProperty(key, propertyDesc, reject);
    }

    // Internals below this point

    // We track deletions using a bit set (delete arguments[index])
    private boolean isDeleted(final int index) {
        return (deleted != null) ? deleted.get(index) : false;
    }

    private void setDeleted(final int index) {
        if (deleted == null) {
            deleted = new BitSet((int)namedArgs.length());
        }
        deleted.set(index, true);
    }

    /**
     * Are arguments[index] and corresponding named parameter linked?
     *
     * In non-strict mode, arguments[index] and corresponding named param
     * are "linked" or "mapped". Modifications are tacked b/w each other - till
     * (delete arguments[index]) is used. Once deleted, the corresponding arg
     * is no longer 'mapped'. Please note that delete can happen only through
     * the arguments array - named param can not be deleted. (delete is one-way).
     */
    private boolean isMapped(final int index) {
        // in named args and not marked as "deleted"
        return namedArgs.has(index) && !isDeleted(index);
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
        return isStrict ? new NativeStrictArguments(arguments, numParams) : new NativeArguments(arguments, callee, numParams);
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
        return MH.findStatic(MethodHandles.publicLookup(), NativeArguments.class, name, MH.type(rtype, types));
    }

}
