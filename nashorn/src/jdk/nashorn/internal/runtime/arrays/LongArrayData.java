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

package jdk.nashorn.internal.runtime.arrays;

import java.util.Arrays;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Implementation of {@link ArrayData} as soon as a long has been
 * written to the array
 */
final class LongArrayData extends ArrayData {
    /**
     * The wrapped array
     */
    private long[] array;

    /**
     * Constructor
     * @param array an int array
     * @param length a length, not necessarily array.length
     */
    LongArrayData(final long array[], final int length) {
        super(length);
        assert array.length >= length;
        this.array  = array;
    }

    @Override
    public ArrayData copy() {
        return new LongArrayData(array.clone(), (int) length());
    }

    @Override
    public Object[] asObjectArray() {
        return toObjectArray(array, (int) length());
    }

    private static Object[] toObjectArray(final long[] array, final int length) {
        assert length <= array.length : "length exceeds internal array size";
        final Object[] oarray = new Object[array.length];

        for (int index = 0; index < length; index++) {
            oarray[index] = Long.valueOf(array[index]);
        }

        return oarray;
    }

    @Override
    public Object asArrayOfType(final Class<?> componentType) {
        if(componentType == long.class) {
            return array.length == length() ? array.clone() : Arrays.copyOf(array, (int) length());
        }
        return super.asArrayOfType(componentType);
    }

    private static double[] toDoubleArray(final long[] array, final int length) {
        assert length <= array.length : "length exceeds internal array size";
        final double[] darray = new double[array.length];

        for (int index = 0; index < length; index++) {
            darray[index] = array[index];
        }

        return darray;
    }

    @Override
    public ArrayData convert(final Class<?> type) {
        if (type == Long.class) {
            return this;
        }
        final int length = (int) length();
        if (type == Double.class) {
            return new NumberArrayData(LongArrayData.toDoubleArray(array, length), length);
        } else {
            return new ObjectArrayData(LongArrayData.toObjectArray(array, length), length);
        }
    }

    @Override
    public void shiftLeft(final int by) {
        System.arraycopy(array, by, array, 0, array.length - by);
    }

    @Override
    public ArrayData shiftRight(final int by) {
        final ArrayData newData = ensure(by + length() - 1);
        if (newData != this) {
            newData.shiftRight(by);
            return newData;
        }
        System.arraycopy(array, 0, array, by, array.length - by);

        return this;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        if (safeIndex >= SparseArrayData.MAX_DENSE_LENGTH && safeIndex >= array.length) {
            return new SparseArrayData(this, safeIndex + 1);
        }

        int newLength = array.length;

        while (newLength <= safeIndex) {
            newLength = ArrayData.nextSize(newLength);
        }

        if (array.length <= safeIndex) {
            array = Arrays.copyOf(array, newLength);
        }

        setLength(safeIndex + 1);

        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        Arrays.fill(array, (int) newLength, array.length, 0);

        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        if (value instanceof Long || value instanceof Integer) {
            return set(index, ((Number)value).longValue(), strict);
        } else if (value == ScriptRuntime.UNDEFINED) {
            return new UndefinedArrayFilter(this).set(index, value, strict);
        }

        final ArrayData newData = convert(value == null ? Object.class : value.getClass());
        return newData.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        array[index] = value;
        setLength(Math.max(index + 1, length()));
        return this;
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        array[index] = value;
        setLength(Math.max(index + 1, length()));
        return this;
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        if (JSType.isRepresentableAsLong(value)) {
            array[index] = (long)value;
            setLength(Math.max(index + 1, length()));
            return this;
        }
        return convert(Double.class).set(index, value, strict);
    }

    @Override
    public int getInt(final int index) {
        return (int)array[index];
    }

    @Override
    public long getLong(final int index) {
        return array[index];
    }

    @Override
    public double getDouble(final int index) {
        return array[index];
    }

    @Override
    public Object getObject(final int index) {
        return array[index];
    }

    @Override
    public boolean has(final int index) {
        return 0 <= index && index < length();
    }

    @Override
    public ArrayData delete(final int index) {
        return new DeletedRangeArrayFilter(this, index, index);
    }

    @Override
    public ArrayData delete(final long fromIndex, final long toIndex) {
        return new DeletedRangeArrayFilter(this, fromIndex, toIndex);
    }

    @Override
    public Object pop() {
        if (length() == 0) {
            return ScriptRuntime.UNDEFINED;
        }

        final int newLength = (int) (length() - 1);
        final long elem = array[newLength];
        array[newLength] = 0;
        setLength(newLength);

        return elem;
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        final long start     = from < 0 ? (from + length()) : from;
        final long newLength = to - start;
        return new LongArrayData(Arrays.copyOfRange(array, (int)from, (int)to), (int)newLength);
    }
}
