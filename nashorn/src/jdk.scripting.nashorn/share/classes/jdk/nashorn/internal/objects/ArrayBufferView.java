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

import static jdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.arrays.ArrayData;
import jdk.nashorn.internal.runtime.arrays.TypedArrayData;

/**
 * ArrayBufferView, es6 class or TypedArray implementation
 */
@ScriptClass("ArrayBufferView")
public abstract class ArrayBufferView extends ScriptObject {
    private final NativeArrayBuffer buffer;
    private final int byteOffset;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private ArrayBufferView(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength, final Global global) {
        super($nasgenmap$);

        final int bytesPerElement = bytesPerElement();

        checkConstructorArgs(buffer.getByteLength(), bytesPerElement, byteOffset, elementLength);
        setProto(getPrototype(global));

        this.buffer     = buffer;
        this.byteOffset = byteOffset;

        assert byteOffset % bytesPerElement == 0;
        final int start = byteOffset / bytesPerElement;
        final ByteBuffer newNioBuffer = buffer.getNioBuffer().duplicate().order(ByteOrder.nativeOrder());
        final ArrayData  data         = factory().createArrayData(newNioBuffer, start, start + elementLength);

        setArray(data);
    }

    /**
     * Constructor
     *
     * @param buffer         underlying NativeArrayBuffer
     * @param byteOffset     byte offset for buffer
     * @param elementLength  element length in bytes
     */
    protected ArrayBufferView(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength) {
        this(buffer, byteOffset, elementLength, Global.instance());
    }

    private static void checkConstructorArgs(final int byteLength, final int bytesPerElement, final int byteOffset, final int elementLength) {
        if (byteOffset < 0 || elementLength < 0) {
            throw new RuntimeException("byteOffset or length must not be negative, byteOffset=" + byteOffset + ", elementLength=" + elementLength + ", bytesPerElement=" + bytesPerElement);
        } else if (byteOffset + elementLength * bytesPerElement > byteLength) {
            throw new RuntimeException("byteOffset + byteLength out of range, byteOffset=" + byteOffset + ", elementLength=" + elementLength + ", bytesPerElement=" + bytesPerElement);
        } else if (byteOffset % bytesPerElement != 0) {
            throw new RuntimeException("byteOffset must be a multiple of the element size, byteOffset=" + byteOffset + " bytesPerElement=" + bytesPerElement);
        }
    }

    private int bytesPerElement() {
        return factory().bytesPerElement;
    }

    /**
     * Buffer getter as per spec
     * @param self ArrayBufferView instance
     * @return buffer
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static Object buffer(final Object self) {
        return ((ArrayBufferView)self).buffer;
    }

    /**
     * Buffer offset getter as per spec
     * @param self ArrayBufferView instance
     * @return buffer offset
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static int byteOffset(final Object self) {
        return ((ArrayBufferView)self).byteOffset;
    }

    /**
     * Byte length getter as per spec
     * @param self ArrayBufferView instance
     * @return array buffer view length in bytes
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static int byteLength(final Object self) {
        final ArrayBufferView view = (ArrayBufferView)self;
        return ((TypedArrayData<?>)view.getArray()).getElementLength() * view.bytesPerElement();
    }

    /**
     * Length getter as per spec
     * @param self ArrayBufferView instance
     * @return length in elements
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static int length(final Object self) {
        return ((ArrayBufferView)self).elementLength();
    }

    @Override
    public final Object getLength() {
        return elementLength();
    }

    private int elementLength() {
        return ((TypedArrayData<?>)getArray()).getElementLength();
    }

    /**
     * Factory class for byte ArrayBufferViews
     */
    protected static abstract class Factory {
        final int bytesPerElement;
        final int maxElementLength;

        /**
         * Constructor
         *
         * @param bytesPerElement number of bytes per element for this buffer
         */
        public Factory(final int bytesPerElement) {
            this.bytesPerElement  = bytesPerElement;
            this.maxElementLength = Integer.MAX_VALUE / bytesPerElement;
        }

        /**
         * Factory method
         *
         * @param elementLength number of elements
         * @return new ArrayBufferView
         */
        public final ArrayBufferView construct(final int elementLength) {
            if (elementLength > maxElementLength) {
                throw rangeError("inappropriate.array.buffer.length", JSType.toString(elementLength));
            }
            return construct(new NativeArrayBuffer(elementLength * bytesPerElement), 0, elementLength);
        }

        /**
         * Factory method
         *
         * @param buffer         underlying buffer
         * @param byteOffset     byte offset
         * @param elementLength  number of elements
         *
         * @return new ArrayBufferView
         */
        public abstract ArrayBufferView construct(final NativeArrayBuffer buffer, final int byteOffset, final int elementLength);

        /**
         * Factory method for array data
         *
         * @param nb    underlying native buffer
         * @param start start element
         * @param end   end element
         *
         * @return      new array data
         */
        public abstract TypedArrayData<?> createArrayData(final ByteBuffer nb, final int start, final int end);

        /**
         * Get the class name for this type of buffer
         *
         * @return class name
         */
        public abstract String getClassName();
    }

    /**
     * Get the factor for this kind of buffer
     * @return Factory
     */
    protected abstract Factory factory();

    /**
     * Get the prototype for this ArrayBufferView
     * @param global global instance
     * @return prototype
     */
    protected abstract ScriptObject getPrototype(final Global global);

    @Override
    public final String getClassName() {
        return factory().getClassName();
    }

    /**
     * Check if this array contains floats
     * @return true if float array (or double)
     */
    protected boolean isFloatArray() {
        return false;
    }

    /**
     * Inheritable constructor implementation
     *
     * @param newObj   is this a new constructor
     * @param args     arguments
     * @param factory  factory
     *
     * @return new ArrayBufferView
     */
    protected static ArrayBufferView constructorImpl(final boolean newObj, final Object[] args, final Factory factory) {
        final Object          arg0 = args.length != 0 ? args[0] : 0;
        final ArrayBufferView dest;
        final int             length;

        if (!newObj) {
            throw typeError("constructor.requires.new", factory.getClassName());
        }


        if (arg0 instanceof NativeArrayBuffer) {
            // Constructor(ArrayBuffer buffer, optional unsigned long byteOffset, optional unsigned long length)
            final NativeArrayBuffer buffer     = (NativeArrayBuffer)arg0;
            final int               byteOffset = args.length > 1 ? JSType.toInt32(args[1]) : 0;

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
            dest   = factory.construct(length);
        } else if (arg0 instanceof NativeArray) {
            // Constructor(type[] array)
            length = lengthToInt(((NativeArray) arg0).getArray().length());
            dest   = factory.construct(length);
        } else {
            // Constructor(unsigned long length). Treating infinity as 0 is a special case for ArrayBufferView.
            final double dlen = JSType.toNumber(arg0);
            length = lengthToInt(Double.isInfinite(dlen) ? 0L : JSType.toLong(dlen));
            return factory.construct(length);
        }

        copyElements(dest, length, (ScriptObject)arg0, 0);

        return dest;
    }

    /**
     * Inheritable implementation of set, if no efficient implementation is available
     *
     * @param self     ArrayBufferView instance
     * @param array    array
     * @param offset0  array offset
     *
     * @return result of setter
     */
    protected static Object setImpl(final Object self, final Object array, final Object offset0) {
        final ArrayBufferView dest = (ArrayBufferView)self;
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

        final ScriptObject source = (ScriptObject)array;
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
                dest.set(j, source.getInt(i, INVALID_PROGRAM_POINT), 0);
            }
        } else {
            for (int i = 0, j = offset; i < length; i++, j++) {
                dest.set(j, source.getDouble(i, INVALID_PROGRAM_POINT), 0);
            }
        }
    }

    private static int lengthToInt(final long length) {
        if (length > Integer.MAX_VALUE || length < 0) {
            throw rangeError("inappropriate.array.buffer.length", JSType.toString(length));
        }
        return (int)(length & Integer.MAX_VALUE);
    }

    /**
     * Implementation of subarray if no efficient override exists
     *
     * @param self    ArrayBufferView instance
     * @param begin0  begin index
     * @param end0    end index
     *
     * @return sub array
     */
    protected static ScriptObject subarrayImpl(final Object self, final Object begin0, final Object end0) {
        final ArrayBufferView arrayView       = (ArrayBufferView)self;
        final int             byteOffset      = arrayView.byteOffset;
        final int             bytesPerElement = arrayView.bytesPerElement();
        final int             elementLength   = arrayView.elementLength();
        final int             begin           = NativeArrayBuffer.adjustIndex(JSType.toInt32(begin0), elementLength);
        final int             end             = NativeArrayBuffer.adjustIndex(end0 != ScriptRuntime.UNDEFINED ? JSType.toInt32(end0) : elementLength, elementLength);
        final int             length          = Math.max(end - begin, 0);

        assert byteOffset % bytesPerElement == 0;

        //second is byteoffset
        return arrayView.factory().construct(arrayView.buffer, begin * bytesPerElement + byteOffset, length);
    }

    @Override
    protected GuardedInvocation findGetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = getArray().findFastGetIndexMethod(getArray().getClass(), desc, request);
        if (inv != null) {
            return inv;
        }
        return super.findGetIndexMethod(desc, request);
    }

    @Override
    protected GuardedInvocation findSetIndexMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final GuardedInvocation inv = getArray().findFastSetIndexMethod(getArray().getClass(), desc, request);
        if (inv != null) {
            return inv;
        }
        return super.findSetIndexMethod(desc, request);
    }
}
