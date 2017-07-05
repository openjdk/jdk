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
import jdk.nashorn.internal.runtime.UnwarrantedOptimismException;

/**
 * This filter handles the presence of undefined array elements.
 */
final class UndefinedArrayFilter extends ArrayFilter {
    /** Bit vector tracking undefines. */
    private final BitVector undefined;

    UndefinedArrayFilter(final ArrayData underlying) {
        super(underlying);
        this.undefined = new BitVector(underlying.length());
    }

    @Override
    public ArrayData copy() {
        final UndefinedArrayFilter copy = new UndefinedArrayFilter(underlying.copy());
        copy.getUndefined().copy(undefined);
        return copy;
    }

    @Override
    public Object[] asObjectArray() {
        final Object[] value = super.asObjectArray();

        for (int i = 0; i < value.length; i++) {
            if (undefined.isSet(i)) {
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
            if (undefined.isSet(i)) {
                Array.set(value, i,undefValue);
            }
        }

        return value;
    }

    @Override
    public void shiftLeft(final int by) {
        super.shiftLeft(by);
        undefined.shiftLeft(by, length());
    }

    @Override
    public ArrayData shiftRight(final int by) {
        super.shiftRight(by);
        undefined.shiftRight(by, length());
        return this;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        if (safeIndex >= SparseArrayData.MAX_DENSE_LENGTH && safeIndex >= length()) {
            return new SparseArrayData(this, safeIndex + 1);
        }

        super.ensure(safeIndex);
        undefined.resize(length());

        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        super.shrink(newLength);
        undefined.resize(length());
        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        undefined.clear(index);

        if (value == UNDEFINED) {
            undefined.set(index);
            return this;
        }

        return super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        undefined.clear(index);

        return super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        undefined.clear(index);

        return super.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        undefined.clear(index);

        return super.set(index, value, strict);
    }

    @Override
    public int getInt(final int index) {
        if (undefined.isSet(index)) {
            return 0;
        }

        return super.getInt(index);
    }

    @Override
    public int getIntOptimistic(final int index, final int programPoint) {
        if (undefined.isSet(index)) {
            throw new UnwarrantedOptimismException(UNDEFINED, programPoint);
        }

        return super.getIntOptimistic(index, programPoint);
    }

    @Override
    public long getLong(final int index) {
        if (undefined.isSet(index)) {
            return 0L;
        }

        return super.getLong(index);
    }

    @Override
    public long getLongOptimistic(final int index, final int programPoint) {
        if (undefined.isSet(index)) {
            throw new UnwarrantedOptimismException(UNDEFINED, programPoint);
        }

        return super.getLongOptimistic(index, programPoint);
    }

    @Override
    public double getDouble(final int index) {
        if (undefined.isSet(index)) {
            return Double.NaN;
        }

        return super.getDouble(index);
    }

    @Override
    public double getDoubleOptimistic(final int index, final int programPoint) {
        if (undefined.isSet(index)) {
            throw new UnwarrantedOptimismException(UNDEFINED, programPoint);
        }

        return super.getDoubleOptimistic(index, programPoint);
    }

    @Override
    public Object getObject(final int index) {
        if (undefined.isSet(index)) {
            return UNDEFINED;
        }

        return super.getObject(index);
    }

    @Override
    public ArrayData delete(final int index) {
        undefined.clear(index);

        return super.delete(index);
    }

    @Override
    public Object pop() {
        final long index = length() - 1;

        if (super.has((int)index)) {
            final boolean isUndefined = undefined.isSet(index);
            final Object value = super.pop();

            return isUndefined ? UNDEFINED : value;
        }

        return super.pop();
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        final ArrayData newArray = underlying.slice(from, to);
        final UndefinedArrayFilter newFilter = new UndefinedArrayFilter(newArray);
        newFilter.getUndefined().copy(undefined);
        newFilter.getUndefined().shiftLeft(from, newFilter.length());

        return newFilter;
    }

    private BitVector getUndefined() {
        return undefined;
    }
}
