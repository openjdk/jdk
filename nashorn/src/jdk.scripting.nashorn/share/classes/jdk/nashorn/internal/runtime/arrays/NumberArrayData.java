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

import static jdk.nashorn.internal.codegen.CompilerConstants.specialCall;
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

/**
 * Implementation of {@link ArrayData} as soon as a double has been
 * written to the array
 */
final class NumberArrayData extends ContinuousArrayData implements NumericElements {
    /**
     * The wrapped array
     */
    private double[] array;

    /**
     * Constructor
     * @param array an int array
     * @param length a length, not necessarily array.length
     */
    NumberArrayData(final double array[], final int length) {
        super(length);
        assert array.length >= length;
        this.array  = array;
    }

    @Override
    public Class<?> getElementType() {
        return double.class;
    }

    @Override
    public ArrayData copy() {
        return new NumberArrayData(array.clone(), (int)length);
    }

    @Override
    public Object[] asObjectArray() {
        return toObjectArray(true);
    }

    private Object[] toObjectArray(final boolean trim) {
        assert length <= array.length : "length exceeds internal array size";
        final Object[] oarray = new Object[trim ? (int)length : array.length];

        for (int index = 0; index < length; index++) {
            oarray[index] = Double.valueOf(array[index]);
        }
        return oarray;
    }

    @Override
    public Object asArrayOfType(final Class<?> componentType) {
        if(componentType == double.class) {
            return array.length == length ? array.clone() : Arrays.copyOf(array, (int)length);
        }
        return super.asArrayOfType(componentType);
    }

    @Override
    public ArrayData convert(final Class<?> type) {
        if (type != Double.class && type != Integer.class && type != Long.class) {
            final int len = (int)length;
            return new ObjectArrayData(toObjectArray(false), len);
        }
        return this;
    }

    @Override
    public void shiftLeft(final int by) {
        System.arraycopy(array, by, array, 0, array.length - by);
    }

    @Override
    public ArrayData shiftRight(final int by) {
        final ArrayData newData = ensure(by + length - 1);
        if (newData != this) {
            newData.shiftRight(by);
            return newData;
        }
        System.arraycopy(array, 0, array, by, array.length - by);
        return this;
    }

    @Override
    public ArrayData ensure(final long safeIndex) {
        if (safeIndex >= SparseArrayData.MAX_DENSE_LENGTH) {
            return new SparseArrayData(this, safeIndex + 1);
        }
        final int alen = array.length;
        if (safeIndex >= alen) {
            final int newLength = ArrayData.nextSize((int)safeIndex);
            array = Arrays.copyOf(array, newLength); //todo fill with nan or never accessed?
        }
        setLength(safeIndex + 1);
        return this;

    }

    @Override
    public ArrayData shrink(final long newLength) {
        Arrays.fill(array, (int) newLength, array.length, 0.0);
        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        if (value instanceof Double || value instanceof Integer || value instanceof Long) {
            return set(index, ((Number)value).doubleValue(), strict);
        } else if (value == UNDEFINED) {
            return new UndefinedArrayFilter(this).set(index, value, strict);
        }

        final ArrayData newData = convert(value == null ? Object.class : value.getClass());
        return newData.set(index, value, strict);
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        array[index] = value;
        setLength(Math.max(index + 1, length));
        return this;
    }

    @Override
    public ArrayData set(final int index, final long value, final boolean strict) {
        array[index] = value;
        setLength(Math.max(index + 1, length));
        return this;
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        array[index] = value;
        setLength(Math.max(index + 1, length));
        return this;
    }

    private static final MethodHandle HAS_GET_ELEM = specialCall(MethodHandles.lookup(), NumberArrayData.class, "getElem", double.class, int.class).methodHandle();
    private static final MethodHandle SET_ELEM     = specialCall(MethodHandles.lookup(), NumberArrayData.class, "setElem", void.class, int.class, double.class).methodHandle();

    @SuppressWarnings("unused")
    private double getElem(final int index) {
        if (has(index)) {
            return array[index];
        }
        throw new ClassCastException();
    }

    @SuppressWarnings("unused")
    private void setElem(final int index, final double elem) {
        if (hasRoomFor(index)) {
            array[index] = elem;
            return;
        }
        throw new ClassCastException();
    }

    @Override
    public MethodHandle getElementGetter(final Class<?> returnType, final int programPoint) {
        if (returnType == int.class || returnType == long.class) {
            return null;
        }
        return getContinuousElementGetter(HAS_GET_ELEM, returnType, programPoint);
    }

    @Override
    public MethodHandle getElementSetter(final Class<?> elementType) {
        return elementType.isPrimitive() ? getContinuousElementSetter(MH.asType(SET_ELEM, SET_ELEM.type().changeParameterType(2, elementType)), elementType) : null;
    }

    @Override
    public int getInt(final int index) {
        return (int)array[index];
    }

    @Override
    public long getLong(final int index) {
        return (long)array[index];
    }

    @Override
    public double getDouble(final int index) {
        return array[index];
    }

    @Override
    public double getDoubleOptimistic(final int index, final int programPoint) {
        return array[index];
    }

    @Override
    public Object getObject(final int index) {
        return array[index];
    }

    @Override
    public boolean has(final int index) {
        return 0 <= index && index < length;
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
        if (length == 0) {
            return UNDEFINED;
        }

        final int newLength = (int)length - 1;
        final double elem = array[newLength];
        array[newLength] = 0;
        setLength(newLength);
        return elem;
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        final long start     = from < 0 ? from + length : from;
        final long newLength = to - start;
        return new NumberArrayData(Arrays.copyOfRange(array, (int)from, (int)to), (int)newLength);
    }

    @Override
    public final ArrayData push(final boolean strict, final double item) {
        final long      len     = length;
        final ArrayData newData = ensure(len);
        if (newData == this) {
            array[(int)len] = item;
            return this;
        }
        return newData.set((int)len, item, strict);
    }

    @Override
    public ArrayData fastSplice(final int start, final int removed, final int added) throws UnsupportedOperationException {
        final long oldLength = length;
        final long newLength = oldLength - removed + added;
        if (newLength > SparseArrayData.MAX_DENSE_LENGTH && newLength > array.length) {
            throw new UnsupportedOperationException();
        }
        final ArrayData returnValue = removed == 0 ?
                EMPTY_ARRAY : new NumberArrayData(Arrays.copyOfRange(array, start, start + removed), removed);

        if (newLength != oldLength) {
            final double[] newArray;

            if (newLength > array.length) {
                newArray = new double[ArrayData.nextSize((int)newLength)];
                System.arraycopy(array, 0, newArray, 0, start);
            } else {
                newArray = array;
            }

            System.arraycopy(array, start + removed, newArray, start + added, (int)(oldLength - start - removed));
            array = newArray;
            setLength(newLength);
        }

        return returnValue;
    }

    @Override
    public long fastPush(final int arg) {
        return fastPush((double)arg);
    }

    @Override
    public long fastPush(final long arg) {
        return fastPush((double)arg);
    }

    @Override
    public long fastPush(final double arg) {
        final int len = (int)length;
        if (len == array.length) {
           //note that fastpush never creates spares arrays, there is nothing to gain by that - it will just use even more memory
           array = Arrays.copyOf(array, nextSize(len));
        }
        array[len] = arg;
        return ++length;
    }

    @Override
    public double fastPopDouble() {
        if (length == 0) {
            throw new ClassCastException();
        }
        final int newLength = (int)--length;
        final double elem = array[newLength];
        array[newLength] = 0;
        return elem;
    }

    @Override
    public Object fastPopObject() {
        return fastPopDouble();
    }
}
