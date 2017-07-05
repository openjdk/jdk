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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Implementation of {@link ArrayData} as soon as an int has been
 * written to the array. This is the default data for new arrays
 */
final class IntArrayData extends ContinuousArrayData implements IntElements {
    /**
     * The wrapped array
     */
    private int[] array;

    IntArrayData() {
        this(new int[ArrayData.CHUNK_SIZE], 0);
    }

    IntArrayData(final int length) {
        super(length);
        this.array  = new int[ArrayData.nextSize(length)];
    }

    /**
     * Constructor
     * @param array an int array
     * @param length a length, not necessarily array.length
     */
    IntArrayData(final int[] array, final int length) {
        super(length);
        assert array == null || array.length >= length;
        this.array = array;
    }

    @Override
    public final Class<?> getElementType() {
        return int.class;
    }

    @Override
    public final Class<?> getBoxedElementType() {
        return Integer.class;
    }

    @Override
    public final int getElementWeight() {
        return 1;
    }

    @Override
    public final ContinuousArrayData widest(final ContinuousArrayData otherData) {
        return otherData;
    }

    private static final MethodHandle HAS_GET_ELEM = specialCall(MethodHandles.lookup(), IntArrayData.class, "getElem", int.class, int.class).methodHandle();
    private static final MethodHandle SET_ELEM     = specialCall(MethodHandles.lookup(), IntArrayData.class, "setElem", void.class, int.class, int.class).methodHandle();

    @Override
    public Object[] asObjectArray() {
        return toObjectArray(true);
    }

    @SuppressWarnings("unused")
    private int getElem(final int index) {
        if (has(index)) {
            return array[index];
        }
        throw new ClassCastException();
    }

    @SuppressWarnings("unused")
    private void setElem(final int index, final int elem) {
        if (hasRoomFor(index)) {
            array[index] = elem;
            return;
        }
        throw new ClassCastException();
    }

    @Override
    public MethodHandle getElementGetter(final Class<?> returnType, final int programPoint) {
        return getContinuousElementGetter(HAS_GET_ELEM, returnType, programPoint);
    }

    @Override
    public MethodHandle getElementSetter(final Class<?> elementType) {
        return elementType == int.class ? getContinuousElementSetter(SET_ELEM, elementType) : null;
    }

    @Override
    public IntArrayData copy() {
        return new IntArrayData(array.clone(), (int)length());
    }

    @Override
    public Object asArrayOfType(final Class<?> componentType) {
        if (componentType == int.class) {
            final int len = (int)length();
            return array.length == len ? array.clone() : Arrays.copyOf(array, len);
        }
        return super.asArrayOfType(componentType);
    }

    private Object[] toObjectArray(final boolean trim) {
        assert length() <= array.length : "length exceeds internal array size";
        final int len = (int)length();
        final Object[] oarray = new Object[trim ? len : array.length];

        for (int index = 0; index < len; index++) {
            oarray[index] = array[index];
        }

        return oarray;
    }

    private double[] toDoubleArray() {
        assert length() <= array.length : "length exceeds internal array size";
        final int len = (int)length();
        final double[] darray = new double[array.length];

        for (int index = 0; index < len; index++) {
            darray[index] = array[index];
        }

        return darray;
    }

    private NumberArrayData convertToDouble() {
        return new NumberArrayData(toDoubleArray(), (int)length());
    }

    private ObjectArrayData convertToObject() {
        return new ObjectArrayData(toObjectArray(false), (int)length());
    }

    @Override
    public ArrayData convert(final Class<?> type) {
        if (type == Integer.class || type == Byte.class || type == Short.class) {
            return this;
        } else if (type == Double.class || type == Float.class) {
            return convertToDouble();
        } else {
            return convertToObject();
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
        if (safeIndex >= SparseArrayData.MAX_DENSE_LENGTH) {
            return new SparseArrayData(this, safeIndex + 1);
        }
        final int alen = array.length;
        if (safeIndex >= alen) {
            final int newLength = ArrayData.nextSize((int)safeIndex);
            array = Arrays.copyOf(array, newLength);
        }
        if (safeIndex >= length()) {
            setLength(safeIndex + 1);
        }
        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        Arrays.fill(array, (int)newLength, array.length, 0);
        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        if (JSType.isRepresentableAsInt(value)) {
            return set(index, JSType.toInt32(value), strict);
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
    public ArrayData set(final int index, final double value, final boolean strict) {
        if (JSType.isRepresentableAsInt(value)) {
            array[index] = (int)(long)value;
            setLength(Math.max(index + 1, length()));
            return this;
        }

        return convert(Double.class).set(index, value, strict);
    }

    @Override
    public int getInt(final int index) {
        return array[index];
    }

    @Override
    public int getIntOptimistic(final int index, final int programPoint) {
        return array[index];
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
        final int len = (int)length();
        if (len == 0) {
            return ScriptRuntime.UNDEFINED;
        }

        final int newLength = len - 1;
        final int elem = array[newLength];
        array[newLength] = 0;
        setLength(newLength);

        return elem;
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        return new IntArrayData(Arrays.copyOfRange(array, (int)from, (int)to), (int)(to - (from < 0 ? from + length() : from)));
    }

    @Override
    public ArrayData fastSplice(final int start, final int removed, final int added) throws UnsupportedOperationException {
        final long oldLength = length();
        final long newLength = oldLength - removed + added;
        if (newLength > SparseArrayData.MAX_DENSE_LENGTH && newLength > array.length) {
            throw new UnsupportedOperationException();
        }
        final ArrayData returnValue = removed == 0 ?
                EMPTY_ARRAY :
                new IntArrayData(
                        Arrays.copyOfRange(
                                array,
                                start,
                                start + removed),
                        removed);

        if (newLength != oldLength) {
            final int[] newArray;

            if (newLength > array.length) {
                newArray = new int[ArrayData.nextSize((int)newLength)];
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
        final int len = (int)length();
        if (len == array.length) {
            array = Arrays.copyOf(array, nextSize(len));
        }
        array[len] = arg;
        return increaseLength();
    }

    //length must not be zero
    @Override
    public int fastPopInt() {
        if (length() == 0) {
            throw new ClassCastException(); //relink
        }
        final int newLength = (int)decreaseLength();
        final int elem = array[newLength];
        array[newLength] = 0;
        return elem;
    }

    @Override
    public long fastPopLong() {
        return fastPopInt();
    }

    @Override
    public double fastPopDouble() {
        return fastPopInt();
    }

    @Override
    public Object fastPopObject() {
        return fastPopInt();
    }

    @Override
    public ContinuousArrayData fastConcat(final ContinuousArrayData otherData) {
        final int   otherLength = (int)otherData.length();
        final int   thisLength  = (int)length();
        assert otherLength > 0 && thisLength > 0;

        final int[] otherArray  = ((IntArrayData)otherData).array;
        final int   newLength   = otherLength + thisLength;
        final int[] newArray    = new int[ArrayData.alignUp(newLength)];

        System.arraycopy(array, 0, newArray, 0, thisLength);
        System.arraycopy(otherArray, 0, newArray, thisLength, otherLength);

        return new IntArrayData(newArray, newLength);
    }

    @Override
    public String toString() {
        assert length() <= array.length : length() + " > " + array.length;
        return getClass().getSimpleName() + ':' + Arrays.toString(Arrays.copyOf(array, (int)length()));
    }
}
