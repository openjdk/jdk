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
 * This filter handles the deletion of array elements.
 */
final class DeletedRangeArrayFilter extends ArrayFilter {
    /** Range (inclusive) tracking deletions */
    private long lo, hi;

    DeletedRangeArrayFilter(final ArrayData underlying, final long lo, final long hi) {
        super(maybeSparse(underlying, hi));
        this.lo = lo;
        this.hi = hi;
    }

    private static ArrayData maybeSparse(final ArrayData underlying, final long hi) {
        if(hi < SparseArrayData.MAX_DENSE_LENGTH || underlying instanceof SparseArrayData) {
            return underlying;
        }
        return new SparseArrayData(underlying, underlying.length());
    }

    private boolean isEmpty() {
        return lo > hi;
    }

    private boolean isDeleted(final int index) {
        final long longIndex = ArrayIndex.toLongIndex(index);
        return lo <= longIndex && longIndex <= hi;
    }

    @Override
    public ArrayData copy() {
        return new DeletedRangeArrayFilter(underlying.copy(), lo, hi);
    }

    @Override
    public Object[] asObjectArray() {
        final Object[] value = super.asObjectArray();

        if (lo <= Integer.MAX_VALUE) {
            final int intHi = (int)Math.min(hi, Integer.MAX_VALUE);
            for (int i = (int)lo; i <= intHi; i++) {
                value[i] = ScriptRuntime.UNDEFINED;
            }
        }

        return value;
    }

    @Override
    public Object asArrayOfType(final Class<?> componentType) {
        final Object value = super.asArrayOfType(componentType);
        final Object undefValue = convertUndefinedValue(componentType);

        if (lo <= Integer.MAX_VALUE) {
            final int intHi = (int)Math.min(hi, Integer.MAX_VALUE);
            for (int i = (int)lo; i <= intHi; i++) {
                Array.set(value, i, undefValue);
            }
        }

        return value;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        if (safeIndex >= SparseArrayData.MAX_DENSE_LENGTH && safeIndex >= length()) {
            return new SparseArrayData(this, safeIndex + 1);
        }

        return super.ensure(safeIndex);
    }

    @Override
    public void shiftLeft(final int by) {
        super.shiftLeft(by);
        lo = Math.max(0, lo - by);
        hi = Math.max(-1, hi - by);
    }

    @Override
    public ArrayData shiftRight(final int by) {
        super.shiftRight(by);
        lo = Math.min(length(), lo + by);
        hi = Math.min(length() - 1, hi + by);

        return isEmpty() ? getUnderlying() : this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        super.shrink(newLength);
        lo = Math.min(newLength, lo);
        hi = Math.min(newLength - 1, hi);

        return isEmpty() ? getUnderlying() : this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (longIndex < lo || longIndex > hi) {
            return super.set(index, value, strict);
        } else if (longIndex > lo && longIndex < hi) {
            return getDeletedArrayFilter().set(index, value, strict);
        }
        if (longIndex == lo) {
            lo++;
        } else {
            assert longIndex == hi;
            hi--;
        }

        return isEmpty() ? getUnderlying().set(index, value, strict) : super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (longIndex < lo || longIndex > hi) {
            return super.set(index, value, strict);
        } else if (longIndex > lo && longIndex < hi) {
            return getDeletedArrayFilter().set(index, value, strict);
        }
        if (longIndex == lo) {
            lo++;
        } else {
            assert longIndex == hi;
            hi--;
        }

        return isEmpty() ? getUnderlying().set(index, value, strict) : super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (longIndex < lo || longIndex > hi) {
            return super.set(index, value, strict);
        } else if (longIndex > lo && longIndex < hi) {
            return getDeletedArrayFilter().set(index, value, strict);
        }
        if (longIndex == lo) {
            lo++;
        } else {
            assert longIndex == hi;
            hi--;
        }

        return isEmpty() ? getUnderlying().set(index, value, strict) : super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        final long longIndex = ArrayIndex.toLongIndex(index);
        if (longIndex < lo || longIndex > hi) {
            return super.set(index, value, strict);
        } else if (longIndex > lo && longIndex < hi) {
            return getDeletedArrayFilter().set(index, value, strict);
        }
        if (longIndex == lo) {
            lo++;
        } else {
            assert longIndex == hi;
            hi--;
        }

        return isEmpty() ? getUnderlying().set(index, value, strict) : super.set(index, value, strict);
    }

    @Override
    public boolean has(final int index) {
        return super.has(index) && !isDeleted(index);
    }

    private ArrayData getDeletedArrayFilter() {
        final ArrayData deleteFilter = new DeletedArrayFilter(getUnderlying());
        deleteFilter.delete(lo, hi);
        return deleteFilter;
    }

    @Override
    public ArrayData delete(final int index) {
        final long longIndex = ArrayIndex.toLongIndex(index);
        underlying.setEmpty(index);

        if (longIndex + 1 == lo) {
            lo = longIndex;
        } else if (longIndex - 1 == hi) {
            hi = longIndex;
        } else if (longIndex < lo || hi < longIndex) {
           return getDeletedArrayFilter().delete(index);
        }

        return this;
    }

    @Override
    public ArrayData delete(final long fromIndex, final long toIndex) {
        if (fromIndex > hi + 1  || toIndex < lo - 1) {
            return getDeletedArrayFilter().delete(fromIndex, toIndex);
        }
        lo = Math.min(fromIndex, lo);
        hi = Math.max(toIndex, hi);
        underlying.setEmpty(lo, hi);
        return this;
    }

    @Override
    public Object pop() {
        final int index = (int)(length() - 1);
        if (super.has(index)) {
            final boolean isDeleted = isDeleted(index);
            final Object value      = super.pop();

            lo = Math.min(index + 1, lo);
            hi = Math.min(index, hi);
            return isDeleted ? ScriptRuntime.UNDEFINED : value;
        }

        return super.pop();
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        return new DeletedRangeArrayFilter(underlying.slice(from, to), Math.max(0, lo - from), Math.max(0, hi - from));
    }
}
