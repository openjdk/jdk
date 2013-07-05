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

import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Property;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.arrays.ArrayData;

/**
 * Float64 array for the TypedArray extension
 */
@ScriptClass("Float64Array")
public final class NativeFloat64Array extends ArrayBufferView {
    /**
     * The size in bytes of each element in the array.
     */
    @Property(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE, where = Where.CONSTRUCTOR)
    public static final int BYTES_PER_ELEMENT = 8;

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    private static final Factory FACTORY = new Factory(BYTES_PER_ELEMENT) {
        @Override
        public ArrayBufferView construct(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
            return new NativeFloat64Array(buffer, byteOffset, length);
        }
        @Override
        public ArrayData createArrayData(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
            return new Float64ArrayData(buffer, byteOffset, length);
        }
    };

    private static final class Float64ArrayData extends ArrayDataImpl {
        private Float64ArrayData(final NativeArrayBuffer buffer,
                final int byteOffset, final int elementLength) {
            super(buffer, byteOffset, elementLength);
        }

        @Override
        protected int byteIndex(final int index) {
            return index * BYTES_PER_ELEMENT + byteOffset;
        }

        @Override
        protected double getDoubleImpl(final int index) {
            final int byteIndex = byteIndex(index);
            final byte[] byteArray = buffer.getByteArray();
            final long bits;
            bits =       byteArray[byteIndex  ]       & 0x0000_0000_0000_00ffL |
                   (long)byteArray[byteIndex+1] <<  8 & 0x0000_0000_0000_ff00L |
                   (long)byteArray[byteIndex+2] << 16 & 0x0000_0000_00ff_0000L |
                   (long)byteArray[byteIndex+3] << 24 & 0x0000_0000_ff00_0000L |
                   (long)byteArray[byteIndex+4] << 32 & 0x0000_00ff_0000_0000L |
                   (long)byteArray[byteIndex+5] << 40 & 0x0000_ff00_0000_0000L |
                   (long)byteArray[byteIndex+6] << 48 & 0x00ff_0000_0000_0000L |
                   (long)byteArray[byteIndex+7] << 56 & 0xff00_0000_0000_0000L ;
            return Double.longBitsToDouble(bits);
        }

        @Override
        protected int getIntImpl(final int index) {
            return (int)getDoubleImpl(index);
        }

        @Override
        protected long getLongImpl(final int key) {
            return (long)getDoubleImpl(key);
        }

        @Override
        protected Object getObjectImpl(final int key) {
            return getDoubleImpl(key);
        }

        @Override
        protected void setImpl(final int index, final double value) {
            final long bits = Double.doubleToRawLongBits(value);
            final int byteIndex = byteIndex(index);
            @SuppressWarnings("MismatchedReadAndWriteOfArray")
            final byte[] byteArray = buffer.getByteArray();
            byteArray[byteIndex  ] = (byte)(bits        & 0xff);
            byteArray[byteIndex+1] = (byte)(bits >>>  8 & 0xff);
            byteArray[byteIndex+2] = (byte)(bits >>> 16 & 0xff);
            byteArray[byteIndex+3] = (byte)(bits >>> 24 & 0xff);
            byteArray[byteIndex+4] = (byte)(bits >>> 32 & 0xff);
            byteArray[byteIndex+5] = (byte)(bits >>> 40 & 0xff);
            byteArray[byteIndex+6] = (byte)(bits >>> 48 & 0xff);
            byteArray[byteIndex+7] = (byte)(bits >>> 56 & 0xff);
        }

        @Override
        protected void setImpl(final int key, final int value) {
            setImpl(key, (double)value);
        }

        @Override
        protected void setImpl(final int key, final long value) {
            setImpl(key, (double)value);
        }

        @Override
        protected void setImpl(final int key, final Object value) {
            setImpl(key, JSType.toNumber(value));
        }
    }

    /**
     * Constructor
     *
     * @param newObj is this typed array instantiated with the new operator
     * @param self   self reference
     * @param args   args
     *
     * @return new typed array
     */
    @Constructor(arity = 1)
    public static Object constructor(final boolean newObj, final Object self, final Object... args) {
        return constructorImpl(args, FACTORY);
    }

    NativeFloat64Array(final NativeArrayBuffer buffer, final int byteOffset, final int length) {
        super(buffer, byteOffset, length);
    }

    @Override
    protected Factory factory() {
        return FACTORY;
    }

    @Override
    protected boolean isFloatArray() {
        return true;
    }

    /**
     * Set values
     * @param self   self reference
     * @param array  multiple values of array's type to set
     * @param offset optional start index, interpreted  0 if undefined
     * @return undefined
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    protected static Object set(final Object self, final Object array, final Object offset) {
        return ArrayBufferView.setImpl(self, array, offset);
    }

    /**
     * Returns a new TypedArray view of the ArrayBuffer store for this TypedArray,
     * referencing the elements at begin, inclusive, up to end, exclusive. If either
     * begin or end is negative, it refers to an index from the end of the array,
     * as opposed to from the beginning.
     * <p>
     * If end is unspecified, the subarray contains all elements from begin to the end
     * of the TypedArray. The range specified by the begin and end values is clamped to
     * the valid index range for the current array. If the computed length of the new
     * TypedArray would be negative, it is clamped to zero.
     * <p>
     * The returned TypedArray will be of the same type as the array on which this
     * method is invoked.
     *
     * @param self self reference
     * @param begin begin position
     * @param end end position
     *
     * @return sub array
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    protected static Object subarray(final Object self, final Object begin, final Object end) {
        return ArrayBufferView.subarrayImpl(self, begin, end);
    }

    @Override
    protected ScriptObject getPrototype(final Global global) {
        return global.getFloat64ArrayPrototype();
    }
}
