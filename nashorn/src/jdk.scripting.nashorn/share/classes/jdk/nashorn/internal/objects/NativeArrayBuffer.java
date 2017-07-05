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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.nio.ByteBuffer;

import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * NativeArrayBuffer - ArrayBuffer as described in the JS typed
 * array spec
 */
@ScriptClass("ArrayBuffer")
public final class NativeArrayBuffer extends ScriptObject {
    private final ByteBuffer nb;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    /**
     * Constructor
     * @param nb native byte buffer to wrap
     * @param global global instance
     */
    protected NativeArrayBuffer(final ByteBuffer nb, final Global global) {
        super(global.getArrayBufferPrototype(), $nasgenmap$);
        this.nb = nb;
    }

    /**
     * Constructor
     * @param nb native byte buffer to wrap
     */
    protected NativeArrayBuffer(final ByteBuffer nb) {
        this(nb, Global.instance());
    }

    /**
     * Constructor
     * @param byteLength byteLength for buffer
     */
    protected NativeArrayBuffer(final int byteLength) {
        this(ByteBuffer.allocateDirect(byteLength));
    }

    /**
     * Clone constructor
     * Used only for slice
     * @param other original buffer
     * @param begin begin byte index
     * @param end   end byte index
     */
    protected NativeArrayBuffer(final NativeArrayBuffer other, final int begin, final int end) {
        this(cloneBuffer(other.getNioBuffer(), begin, end));
    }

    /**
     * Constructor
     * @param newObj is this invoked with new
     * @param self   self reference
     * @param args   arguments to constructor
     * @return new NativeArrayBuffer
     */
    @Constructor(arity = 1)
    public static NativeArrayBuffer constructor(final boolean newObj, final Object self, final Object... args) {
        if (!newObj) {
            throw typeError("constructor.requires.new", "ArrayBuffer");
        }

        if (args.length == 0) {
            return new NativeArrayBuffer(0);
        }

        final Object arg0 = args[0];
        if (arg0 instanceof ByteBuffer) {
            return new NativeArrayBuffer((ByteBuffer)arg0);
        } else {
            return new NativeArrayBuffer(JSType.toInt32(arg0));
        }
    }

    private static ByteBuffer cloneBuffer(final ByteBuffer original, final int begin, final int end) {
        final ByteBuffer clone = ByteBuffer.allocateDirect(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        clone.position(begin);
        clone.limit(end);
        return clone.slice();
    }

    ByteBuffer getNioBuffer() {
        return nb;
    }

    @Override
    public String getClassName() {
        return "ArrayBuffer";
    }

    /**
     * Byte length for native array buffer
     * @param self native array buffer
     * @return byte length
     */
    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static int byteLength(final Object self) {
        return ((NativeArrayBuffer)self).getByteLength();
    }

    /**
     * Returns true if an object is an ArrayBufferView
     *
     * @param self self
     * @param obj  object to check
     *
     * @return true if obj is an ArrayBufferView
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static boolean isView(final Object self, final Object obj) {
        return obj instanceof ArrayBufferView;
    }

    /**
     * Slice function
     * @param self   native array buffer
     * @param begin0 start byte index
     * @param end0   end byte index
     * @return new array buffer, sliced
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static NativeArrayBuffer slice(final Object self, final Object begin0, final Object end0) {
        final NativeArrayBuffer arrayBuffer = (NativeArrayBuffer)self;
        final int               byteLength  = arrayBuffer.getByteLength();
        final int               begin       = adjustIndex(JSType.toInt32(begin0), byteLength);
        final int               end         = adjustIndex(end0 != ScriptRuntime.UNDEFINED ? JSType.toInt32(end0) : byteLength, byteLength);
        return new NativeArrayBuffer(arrayBuffer, begin, Math.max(end, begin));
    }

    /**
     * Specialized slice function
     * @param self   native array buffer
     * @param begin  start byte index
     * @param end    end byte index
     * @return new array buffer, sliced
     */
    @SpecializedFunction
    public static Object slice(final Object self, final int begin, final int end) {
        final NativeArrayBuffer arrayBuffer = (NativeArrayBuffer)self;
        final int byteLength  = arrayBuffer.getByteLength();
        return new NativeArrayBuffer(arrayBuffer, adjustIndex(begin, byteLength), Math.max(adjustIndex(end, byteLength), begin));
    }

    /**
     * Specialized slice function
     * @param self   native array buffer
     * @param begin  start byte index
     * @return new array buffer, sliced
     */
    @SpecializedFunction
    public static Object slice(final Object self, final int begin) {
        return slice(self, begin, ((NativeArrayBuffer)self).getByteLength());
    }

    /**
     * If index is negative, it refers to an index from the end of the array, as
     * opposed to from the beginning. The index is clamped to the valid index
     * range for the array.
     *
     * @param index  The index.
     * @param length The length of the array.
     * @return valid index index in the range [0, length).
     */
    static int adjustIndex(final int index, final int length) {
        return index < 0 ? clamp(index + length, length) : clamp(index, length);
    }

    /**
     * Clamp index into the range [0, length).
     */
    private static int clamp(final int index, final int length) {
        if (index < 0) {
            return 0;
        } else if (index > length) {
            return length;
        }
        return index;
    }

    int getByteLength() {
        return nb.limit();
    }

    ByteBuffer getBuffer() {
       return nb;
    }

    ByteBuffer getBuffer(final int offset) {
        return nb.duplicate().position(offset);
    }

    ByteBuffer getBuffer(final int offset, final int length) {
        return getBuffer(offset).limit(length);
    }
}
