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

import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.reflect.Array;
import jdk.nashorn.internal.runtime.BitVector;

/**
 * This filter handles the deletion of array elements.
 */
final class DeletedArrayFilter extends ArrayFilter {
    /** Bit vector tracking deletions. */
    private final BitVector deleted;

    DeletedArrayFilter(final ArrayData underlying) {
        super(underlying);

        this.deleted = new BitVector(underlying.length());
    }

    @Override
    public ArrayData copy() {
        DeletedArrayFilter copy = new DeletedArrayFilter(underlying.copy());
        copy.getDeleted().copy(deleted);
        return copy;
    }

    @Override
    public Object[] asObjectArray() {
        final Object[] value = super.asObjectArray();

        for (int i = 0; i < value.length; i++) {
            if (deleted.isSet(i)) {
                value[i] = UNDEFINED;
            }
        }

        return value;
    }

    @Override
    public Object asArrayOfType(final Class<?> componentType) {
        final Object value = super.asArrayOfType(componentType);
        final Object undefValue = convertUndefinedValue(componentType);
        final int l = Array.getLength(value);
        for (int i = 0; i < l; i++) {
            if (deleted.isSet(i)) {
                Array.set(value, i, undefValue);
            }
        }

        return value;
    }

    @Override
    public void shiftLeft(final int by) {
        super.shiftLeft(by);
        deleted.shiftLeft(by, length());
    }

    @Override
    public ArrayData shiftRight(final int by) {
        super.shiftRight(by);
        deleted.shiftRight(by, length());

        return this;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        if (safeIndex >= SparseArrayData.MAX_DENSE_LENGTH && safeIndex >= length()) {
            return new SparseArrayData(this, safeIndex + 1);
        }

        super.ensure(safeIndex);
        deleted.resize(length());

        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        super.shrink(newLength);
        deleted.resize(length());

        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        deleted.clear(ArrayIndex.toLongIndex(index));

        return super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        deleted.clear(ArrayIndex.toLongIndex(index));

        return super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        deleted.clear(ArrayIndex.toLongIndex(index));

        return super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        deleted.clear(ArrayIndex.toLongIndex(index));

        return super.set(index, value, strict);
    }

    @Override
    public boolean has(final int index) {
        return super.has(index) && deleted.isClear(ArrayIndex.toLongIndex(index));
    }

    @Override
    public ArrayData delete(final int index) {
        final long longIndex = ArrayIndex.toLongIndex(index);
        assert longIndex >= 0 && longIndex < length();
        deleted.set(longIndex);
        underlying.setEmpty(index);
        return this;
    }

    @Override
    public ArrayData delete(final long fromIndex, final long toIndex) {
        assert fromIndex >= 0 && fromIndex <= toIndex && toIndex < length();
        deleted.setRange(fromIndex, toIndex + 1);
        underlying.setEmpty(fromIndex, toIndex);
        return this;
    }

    @Override
    public Object pop() {
        final long index = length() - 1;

        if (super.has((int)index)) {
            final boolean isDeleted = deleted.isSet(index);
            final Object value = super.pop();

            return isDeleted ? UNDEFINED : value;
        }

        return super.pop();
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        final ArrayData newArray = underlying.slice(from, to);
        final DeletedArrayFilter newFilter = new DeletedArrayFilter(newArray);
        newFilter.getDeleted().copy(deleted);
        newFilter.getDeleted().shiftLeft(from, newFilter.length());

        return newFilter;
    }

    private BitVector getDeleted() {
        return deleted;
    }
}
