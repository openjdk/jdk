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
 * Implementation of {@link ArrayData} as soon as an Object has been
 * written to the array
 */
final class ObjectArrayData extends ContinuousArrayData implements AnyElements {

    /**
     * The wrapped array
     */
    private Object[] array;

    /**
     * Constructor
     * @param array an int array
     * @param length a length, not necessarily array.length
     */
    ObjectArrayData(final Object[] array, final int length) {
        super(length);
        assert array.length >= length;
        this.array  = array;
    }

    @Override
    public final Class<?> getElementType() {
        return Object.class;
    }

    @Override
    public final Class<?> getBoxedElementType() {
        return getElementType();
    }

    @Override
    public final int getElementWeight() {
        return 4;
    }

    @Override
    public final ContinuousArrayData widest(final ContinuousArrayData otherData) {
        return otherData instanceof NumericElements ? this : otherData;
    }

    @Override
    public ObjectArrayData copy() {
        return new ObjectArrayData(array.clone(), (int)length());
    }

    @Override
    public Object[] asObjectArray() {
        return array.length == length() ? array.clone() : asObjectArrayCopy();
    }

    private Object[] asObjectArrayCopy() {
        final long len = length();
        assert len <= Integer.MAX_VALUE;
        final Object[] copy = new Object[(int)len];
        System.arraycopy(array, 0, copy, 0, (int)len);
        return copy;
    }

    @Override
    public ObjectArrayData convert(final Class<?> type) {
        return this;
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
            array = Arrays.copyOf(array, newLength); //fill with undefined or OK? TODO
        }
        if (safeIndex >= length()) {
            setLength(safeIndex + 1);
        }
        return this;
    }

    @Override
    public ArrayData shrink(final long newLength) {
        Arrays.fill(array, (int) newLength, array.length, ScriptRuntime.UNDEFINED);
        return this;
    }

    @Override
    public ArrayData set(final int index, final Object value, final boolean strict) {
        array[index] = value;
        setLength(Math.max(index + 1, length()));
        return this;
    }

    @Override
    public ArrayData set(final int index, final int value, final boolean strict) {
        array[index] = value;
        setLength(Math.max(index + 1, length()));
        return this;
    }

    @Override
    public ArrayData set(final int index, final double value, final boolean strict) {
        array[index] = value;
        setLength(Math.max(index + 1, length()));
        return this;
    }

    @Override
    public ArrayData setEmpty(final int index) {
        array[index] = ScriptRuntime.EMPTY;
        return this;
    }

    @Override
    public ArrayData setEmpty(final long lo, final long hi) {
        // hi parameter is inclusive, but Arrays.fill toIndex parameter is exclusive
        Arrays.fill(array, (int)Math.max(lo, 0L), (int)Math.min(hi + 1, Integer.MAX_VALUE), ScriptRuntime.EMPTY);
        return this;
    }

    private static final MethodHandle HAS_GET_ELEM = specialCall(MethodHandles.lookup(), ObjectArrayData.class, "getElem", Object.class, int.class).methodHandle();
    private static final MethodHandle SET_ELEM     = specialCall(MethodHandles.lookup(), ObjectArrayData.class, "setElem", void.class, int.class, Object.class).methodHandle();

    @SuppressWarnings("unused")
    private Object getElem(final int index) {
        if (has(index)) {
            return array[index];
        }
        throw new ClassCastException();
    }

    @SuppressWarnings("unused")
    private void setElem(final int index, final Object elem) {
        if (hasRoomFor(index)) {
            array[index] = elem;
            return;
        }
        throw new ClassCastException();
    }

    @Override
    public MethodHandle getElementGetter(final Class<?> returnType, final int programPoint) {
        if (returnType.isPrimitive()) {
            return null;
        }
        return getContinuousElementGetter(HAS_GET_ELEM, returnType, programPoint);
    }

    @Override
    public MethodHandle getElementSetter(final Class<?> elementType) {
        return getContinuousElementSetter(SET_ELEM, Object.class);
    }


    @Override
    public int getInt(final int index) {
        return JSType.toInt32(array[index]);
    }

    @Override
    public double getDouble(final int index) {
        return JSType.toNumber(array[index]);
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
        setEmpty(index);
        return new DeletedRangeArrayFilter(this, index, index);
    }

    @Override
    public ArrayData delete(final long fromIndex, final long toIndex) {
        setEmpty(fromIndex, toIndex);
        return new DeletedRangeArrayFilter(this, fromIndex, toIndex);
    }

    @Override
    public double fastPush(final int arg) {
        return fastPush((Object)arg);
    }

    @Override
    public double fastPush(final long arg) {
        return fastPush((Object)arg);
    }

    @Override
    public double fastPush(final double arg) {
        return fastPush((Object)arg);
    }

    @Override
    public double fastPush(final Object arg) {
        final int len = (int)length();
        if (len == array.length) {
            array = Arrays.copyOf(array, nextSize(len));
        }
        array[len] = arg;
        return increaseLength();
    }

    @Override
    public Object fastPopObject() {
        if (length() == 0) {
            return ScriptRuntime.UNDEFINED;
        }
        final int newLength = (int)decreaseLength();
        final Object elem = array[newLength];
        array[newLength] = ScriptRuntime.EMPTY;
        return elem;
    }

    @Override
    public Object pop() {
        if (length() == 0) {
            return ScriptRuntime.UNDEFINED;
        }

        final int newLength = (int)length() - 1;
        final Object elem = array[newLength];
        setEmpty(newLength);
        setLength(newLength);
        return elem;
    }

    @Override
    public ArrayData slice(final long from, final long to) {
        final long start     = from < 0 ? from + length() : from;
        final long newLength = to - start;
        return new ObjectArrayData(Arrays.copyOfRange(array, (int)from, (int)to), (int)newLength);
    }

    @Override
    public ArrayData push(final boolean strict, final Object item) {
        final long      len     = length();
        final ArrayData newData = ensure(len);
        if (newData == this) {
            array[(int)len] = item;
            return this;
        }
        return newData.set((int)len, item, strict);
    }

    @Override
    public ArrayData fastSplice(final int start, final int removed, final int added) throws UnsupportedOperationException {
        final long oldLength = length();
        final long newLength = oldLength - removed + added;
        if (newLength > SparseArrayData.MAX_DENSE_LENGTH && newLength > array.length) {
            throw new UnsupportedOperationException();
        }
        final ArrayData returnValue = removed == 0 ?
                EMPTY_ARRAY : new ObjectArrayData(Arrays.copyOfRange(array, start, start + removed), removed);

        if (newLength != oldLength) {
            final Object[] newArray;

            if (newLength > array.length) {
                newArray = new Object[ArrayData.nextSize((int)newLength)];
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
    public ContinuousArrayData fastConcat(final ContinuousArrayData otherData) {
        final int   otherLength = (int)otherData.length();
        final int   thisLength  = (int)length();
        assert otherLength > 0 && thisLength > 0;

        final Object[] otherArray = ((ObjectArrayData)otherData).array;
        final int      newLength  = otherLength + thisLength;
        final Object[] newArray   = new Object[ArrayData.alignUp(newLength)];

        System.arraycopy(array, 0, newArray, 0, thisLength);
        System.arraycopy(otherArray, 0, newArray, thisLength, otherLength);

        return new ObjectArrayData(newArray, newLength);
    }

    @Override
    public String toString() {
        assert length() <= array.length : length() + " > " + array.length;
        return getClass().getSimpleName() + ':' + Arrays.toString(Arrays.copyOf(array, (int)length()));
    }
}
