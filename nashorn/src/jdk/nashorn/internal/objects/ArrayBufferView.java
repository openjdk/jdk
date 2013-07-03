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
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayData;

import static jdk.nashorn.internal.runtime.ECMAErrors.rangeError;

@ScriptClass("ArrayBufferView")
abstract class ArrayBufferView extends ScriptObject {

    // initialized by nasgen
    @SuppressWarnings("unused")
    private static PropertyMap $nasgenmap$;

    ArrayBufferView(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength) {
        checkConstructorArgs(buffer, byteOffset, elementLength);
        this.setProto(getPrototype());
        this.setArray(factory().createArrayData(buffer, byteOffset, elementLength));
    }

    private void checkConstructorArgs(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength) {
        if (byteOffset < 0 || elementLength < 0) {
            throw new RuntimeException("byteOffset or length must not be negative");
        }
        if (byteOffset + elementLength * bytesPerElement() > buffer.getByteLength()) {
            throw new RuntimeException("byteOffset + byteLength out of range");
        }
        if (byteOffset % bytesPerElement() != 0) {
            throw new RuntimeException("byteOffset must be a multiple of the element size");
        }
    }

    private int bytesPerElement() {
        return factory().bytesPerElement;
    }

    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static Object buffer(final Object self) {
        return ((ArrayDataImpl)((ArrayBufferView)self).getArray()).buffer;
    }

    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static Object byteOffset(final Object self) {
        return ((ArrayDataImpl)((ArrayBufferView)self).getArray()).byteOffset;
    }

    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static Object byteLength(final Object self) {
        final ArrayBufferView view = (ArrayBufferView)self;
        return ((ArrayDataImpl)view.getArray()).elementLength * view.bytesPerElement();
    }

    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static Object length(final Object self) {
        return ((ArrayBufferView)self).elementLength();
    }

    @Override
    public final Object getLength() {
        return elementLength();
    }

    private int elementLength() {
        return ((ArrayDataImpl)getArray()).elementLength;
    }

    protected static abstract class ArrayDataImpl extends ArrayData {
        protected final NativeArrayBuffer buffer;
        protected final int byteOffset;
        private final int elementLength;

        protected ArrayDataImpl(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength) {
            super(elementLength);
            this.buffer = buffer;
            this.byteOffset = byteOffset;
            this.elementLength = elementLength;
        }

        @Override
        public Object[] asObjectArray() {
            final Object[] array = new Object[elementLength];
            for (int i = 0; i < elementLength; i++) {
                array[i] = getObjectImpl(i);
            }
            return array;
        }

        @Override
        public ArrayData ensure(final long safeIndex) {
            return this;
        }

        @Override
        public void setLength(final long length) {
            //empty?
            //TODO is this right?
        }

        @Override
        public ArrayData shrink(final long newLength) {
            return this;
        }

        @Override
        public ArrayData set(final int index, final Object value, final boolean strict) {
            if (has(index)) {
                setImpl(index, value);
            }
            return this;
        }

        @Override
        public ArrayData set(final int index, final int value, final boolean strict) {
            if (has(index)) {
                setImpl(index, value);
            }
            return this;
        }

        @Override
        public ArrayData set(final int index, final long value, final boolean strict) {
            if (has(index)) {
                setImpl(index, value);
            }
            return this;
        }

        @Override
        public ArrayData set(final int index, final double value, final boolean strict) {
            if (has(index)) {
                setImpl(index, value);
            }
            return this;
        }

        @Override
        public int getInt(final int index) {
            return getIntImpl(index);
        }

        @Override
        public long getLong(final int index) {
            return getLongImpl(index);
        }

        @Override
        public double getDouble(final int index) {
            return getDoubleImpl(index);
        }

        @Override
        public Object getObject(final int index) {
            return getObjectImpl(index);
        }

        @Override
        public boolean has(final int index) {
            return index >= 0 && index < elementLength;
        }

        @Override
        public boolean canDelete(final int index, final boolean strict) {
            return false;
        }

        @Override
        public boolean canDelete(final long fromIndex, final long toIndex, final boolean strict) {
            return false;
        }

        @Override
        public ArrayData delete(final int index) {
            return this;
        }

        @Override
        public ArrayData delete(final long fromIndex, final long toIndex) {
            return this;
        }

        @Override
        protected ArrayData convert(final Class<?> type) {
            return this;
        }

        @Override
        public void shiftLeft(final int by) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArrayData shiftRight(final int by) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object pop() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArrayData slice(final long from, final long to) {
            throw new UnsupportedOperationException();
        }

        protected abstract int getIntImpl(int key);

        protected long getLongImpl(final int key) {
            return getIntImpl(key);
        }

        protected double getDoubleImpl(final int key) {
            return getIntImpl(key);
        }

        protected Object getObjectImpl(final int key) {
            return getIntImpl(key);
        }

        protected abstract void setImpl(int key, int value);

        protected void setImpl(final int key, final long value) {
            setImpl(key, (int)value);
        }

        protected void setImpl(final int key, final double value) {
            setImpl(key, JSType.toInt32(value));
        }

        protected void setImpl(final int key, final Object value) {
            setImpl(key, JSType.toInt32(value));
        }

        protected abstract int byteIndex(int index);
    }

    protected static abstract class Factory {
        final int bytesPerElement;

        public Factory(final int bytesPerElement) {
            this.bytesPerElement = bytesPerElement;
        }

        public final ArrayBufferView construct(final int elementLength) {
            return construct(new NativeArrayBuffer(elementLength * bytesPerElement), 0, elementLength);
        }

        public abstract ArrayBufferView construct(NativeArrayBuffer buffer, int byteOffset, int elementLength);

        public abstract ArrayData createArrayData(NativeArrayBuffer buffer, int byteOffset, int elementLength);
    }

    protected abstract Factory factory();

    protected abstract ScriptObject getPrototype();

    protected boolean isFloatArray() {
        return false;
    }

    protected static ArrayBufferView constructorImpl(final Object[] args, final Factory factory) {
        final Object arg0 = args.length != 0 ? args[0] : 0;
        final ArrayBufferView dst;
        final int length;
        if (arg0 instanceof NativeArrayBuffer) {
            // Constructor(ArrayBuffer buffer, optional unsigned long byteOffset, optional unsigned long length)
            final NativeArrayBuffer buffer = (NativeArrayBuffer) arg0;
            final int byteOffset = args.length > 1 ? JSType.toInt32(args[1]) : 0;
            if (args.length > 2) {
                length = JSType.toInt32(args[2]);
            } else {
                if ((buffer.getByteLength() - byteOffset) % factory.bytesPerElement != 0) {
                    throw new RuntimeException("buffer.byteLength - byteOffset must be a multiple of the element size");
                }
                length = (buffer.getByteLength() - byteOffset) / factory.bytesPerElement;
            }
            return factory.construct(buffer, byteOffset, length);
        } else if (arg0 instanceof ArrayBufferView) {
            // Constructor(TypedArray array)
            length = ((ArrayBufferView)arg0).elementLength();
            dst = factory.construct(length);
        } else if (arg0 instanceof NativeArray) {
            // Constructor(type[] array)
            length = lengthToInt(((NativeArray) arg0).getArray().length());
            dst = factory.construct(length);
        } else {
            // Constructor(unsigned long length)
            length = lengthToInt(JSType.toInt64(arg0));
            return factory.construct(length);
        }

        copyElements(dst, length, (ScriptObject)arg0, 0);
        return dst;
    }

    protected static Object setImpl(final Object self, final Object array, final Object offset0) {
        final ArrayBufferView dest = ((ArrayBufferView)self);
        final int length;
        if (array instanceof ArrayBufferView) {
            // void set(TypedArray array, optional unsigned long offset)
            length = ((ArrayBufferView)array).elementLength();
        } else if (array instanceof NativeArray) {
            // void set(type[] array, optional unsigned long offset)
            length = (int) (((NativeArray) array).getArray().length() & 0x7fff_ffff);
        } else {
            throw new RuntimeException("argument is not of array type");
        }

        final ScriptObject source = (ScriptObject) array;
        final int offset = JSType.toInt32(offset0); // default=0

        if (dest.elementLength() < length + offset || offset < 0) {
            throw new RuntimeException("offset or array length out of bounds");
        }

        copyElements(dest, length, source, offset);

        return ScriptRuntime.UNDEFINED;
    }

    private static void copyElements(final ArrayBufferView dest, final int length, final ScriptObject source, final int offset) {
        if (!dest.isFloatArray()) {
            for (int i = 0, j = offset; i < length; i++, j++) {
                dest.set(j, source.getInt(i), false);
            }
        } else {
            for (int i = 0, j = offset; i < length; i++, j++) {
                dest.set(j, source.getDouble(i), false);
            }
        }
    }

    private static int lengthToInt(final long length) {
        if (length > Integer.MAX_VALUE || length < 0) {
            throw rangeError("inappropriate.array.buffer.length", JSType.toString(length));
        }
        return (int) (length & Integer.MAX_VALUE);
    }

    protected static Object subarrayImpl(final Object self, final Object begin0, final Object end0) {
        final ArrayBufferView arrayView = ((ArrayBufferView)self);
        final int elementLength = arrayView.elementLength();
        final int begin = NativeArrayBuffer.adjustIndex(JSType.toInt32(begin0), elementLength);
        final int end = NativeArrayBuffer.adjustIndex(end0 != ScriptRuntime.UNDEFINED ? JSType.toInt32(end0) : elementLength, elementLength);
        final ArrayDataImpl arrayData = (ArrayDataImpl)arrayView.getArray();
        return arrayView.factory().construct(arrayData.buffer, arrayData.byteIndex(begin), Math.max(end - begin, 0));
    }
}
