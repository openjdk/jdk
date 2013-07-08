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

import java.util.Arrays;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.Getter;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;

@ScriptClass("ArrayBuffer")
final class NativeArrayBuffer extends ScriptObject {
    private final byte[] buffer;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    static PropertyMap getInitialMap() {
        return $nasgenmap$;
    }

    @Constructor(arity = 1)
    public static Object constructor(final boolean newObj, final Object self, final Object... args) {
        if (args.length == 0) {
            throw new RuntimeException("missing length argument");
        }

        return new NativeArrayBuffer(JSType.toInt32(args[0]));
    }

    protected NativeArrayBuffer(final byte[] byteArray, final Global global) {
        super(global.getArrayBufferPrototype(), global.getArrayBufferMap());
        this.buffer = byteArray;
    }

    protected NativeArrayBuffer(final byte[] byteArray) {
        this(byteArray, Global.instance());
    }

    protected NativeArrayBuffer(final int byteLength) {
        this(new byte[byteLength]);
    }

    protected NativeArrayBuffer(final NativeArrayBuffer other, final int begin, final int end) {
        this(Arrays.copyOfRange(other.buffer, begin, end));
    }

    @Getter(attributes = Attribute.NOT_ENUMERABLE | Attribute.NOT_WRITABLE | Attribute.NOT_CONFIGURABLE)
    public static Object byteLength(final Object self) {
        return ((NativeArrayBuffer)self).buffer.length;
    }

    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object slice(final Object self, final Object begin0, final Object end0) {
        final NativeArrayBuffer arrayBuffer = (NativeArrayBuffer)self;
        int begin = JSType.toInt32(begin0);
        int end = end0 != ScriptRuntime.UNDEFINED ? JSType.toInt32(end0) : arrayBuffer.getByteLength();
        begin = adjustIndex(begin, arrayBuffer.getByteLength());
        end = adjustIndex(end, arrayBuffer.getByteLength());
        return new NativeArrayBuffer((NativeArrayBuffer) self, begin, Math.max(end, begin));
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
        if (index < 0) {
            return clamp(index + length, length);
        }
        return clamp(index, length);
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

    public byte[] getByteArray() {
        return buffer;
    }

    public int getByteLength() {
        return buffer.length;
    }
}
