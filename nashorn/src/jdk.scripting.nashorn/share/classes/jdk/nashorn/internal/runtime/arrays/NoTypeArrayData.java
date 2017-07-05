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

import java.lang.reflect.Array;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Place holding array data for non-array objects.  Activates a true array when
 * accessed.  Should only exist as a singleton defined in ArrayData.
 */
final class NoTypeArrayData extends ArrayData {
    NoTypeArrayData() {
        super(0);
    }

    NoTypeArrayData(final long length) {
         super(length);
    }

    @Override
    public Object[] asObjectArray() {
        return ScriptRuntime.EMPTY_ARRAY;
    }

    @Override
    public ArrayData copy() {
        return new NoTypeArrayData();
    }

    @Override
    public Object asArrayOfType(final Class<?> componentType) {
        return Array.newInstance(componentType, 0);
    }

    @Override
    public ArrayData convert(final Class<?> type) {
        final long length = length();
        final ArrayData arrayData;
        if (type == Long.class) {
            arrayData = new LongArrayData(new long[ArrayData.nextSize((int)length)], (int)length);
        } else if (type == Double.class) {
            arrayData = new NumberArrayData(new double[ArrayData.nextSize((int)length)], (int)length);
        } else if (type == Integer.class) {
            arrayData = new IntArrayData(new int[ArrayData.nextSize((int)length)], (int)length);
        } else {
            assert !type.isPrimitive();
            arrayData = new ObjectArrayData(new Object[ArrayData.nextSize((int)length)], (int)length);
        }
        return length == 0 ? arrayData : new DeletedRangeArrayFilter(arrayData, 0, length - 1);
    }

    @Override
    public void shiftLeft(final int by) {
        //empty
    }

    @Override
    public ArrayData shiftRight(final int by) {
        return this;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        if (safeIndex >= SparseArrayData.MAX_DENSE_LENGTH) {
            return new SparseArrayData(this, safeIndex + 1);
        }

        // Don't trample the shared EMPTY_ARRAY.
        if (length() == 0) {
            return new NoTypeArrayData(Math.max(safeIndex + 1, length()));
        }

        setLength(Math.max(safeIndex + 1, length()));
        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        ArrayData newData;

        if (value instanceof Double) {
            newData = convert(Double.class);
        } else if (value instanceof Long) {
            newData = convert(Long.class);
        } else if (value instanceof Integer) {
            newData = convert(Integer.class);
        } else {
            assert !(value instanceof Number);
            newData = convert(value == null ? Object.class : value.getClass());
        }

        return newData.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        final ArrayData newData = convert(Integer.class);
        return newData.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        final ArrayData newData = convert(Long.class);
        return newData.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        final ArrayData newData = convert(Double.class);
        return newData.set(index, value, strict);
    }

    @Override
    public int getInt(final int index) {
        throw new ArrayIndexOutOfBoundsException(index);
    }

    @Override
    public long getLong(final int index) {
        throw new ArrayIndexOutOfBoundsException(index);
    }

    @Override
    public double getDouble(final int index) {
        throw new ArrayIndexOutOfBoundsException(index);
    }

    @Override
    public Object getObject(final int index) {
        throw new ArrayIndexOutOfBoundsException(index);
    }

    @Override
    public boolean has(final int index) {
        return false;
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
        return ScriptRuntime.UNDEFINED;
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        return this;
    }
}
