/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.nio.ByteBuffer;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.PropertyDescriptor;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Implementation of {@link ArrayData} that wraps a nio ByteBuffer
 */
final class ByteBufferArrayData extends ArrayData {
    private final ByteBuffer buf;

    ByteBufferArrayData(final int length) {
        super(length);
        this.buf = ByteBuffer.allocateDirect(length);
    }

    /**
     * Constructor
     *
     * @param buf ByteBuffer to create array data with.
     */
    ByteBufferArrayData(final ByteBuffer buf) {
        super(buf.capacity());
        this.buf = buf;
    }

    /**
     * Returns property descriptor for element at a given index
     *
     * @param global the global object
     * @param index  the index
     *
     * @return property descriptor for element
     */
    @Override
    public PropertyDescriptor getDescriptor(final Global global, final int index) {
        // make the index properties not configurable
        return global.newDataDescriptor(getObject(index), false, true, true);
    }

    @Override
    public ArrayData copy() {
        throw unsupported("copy");
    }

    @Override
    public Object[] asObjectArray() {
        throw unsupported("asObjectArray");
    }

    @Override
    public void setLength(final long length) {
        throw new UnsupportedOperationException("setLength");
    }

    @Override
    public void shiftLeft(int by) {
        throw unsupported("shiftLeft");
    }

    @Override
    public ArrayData shiftRight(int by) {
        throw unsupported("shiftRight");
    }

    @Override
    public ArrayData ensure(long safeIndex) {
        if (safeIndex < buf.capacity()) {
            return this;
        }

        throw unsupported("ensure");
    }

    @Override
    public ArrayData shrink(long newLength) {
        throw unsupported("shrink");
    }

    @Override
    public ArrayData set(int index, Object value, boolean strict) {
        if (value instanceof Number) {
            buf.put(index, ((Number)value).byteValue());
            return this;
        }

        throw typeError("not.a.number", ScriptRuntime.safeToString(value));
    }

    @Override
    public ArrayData set(int index, int value, boolean strict) {
        buf.put(index, (byte)value);
        return this;
    }

    @Override
    public ArrayData set(int index, long value, boolean strict) {
        buf.put(index, (byte)value);
        return this;
    }

    @Override
    public ArrayData set(int index, double value, boolean strict) {
        buf.put(index, (byte)value);
        return this;
    }

    @Override
    public int getInt(int index) {
        return 0x0ff & buf.get(index);
    }

    @Override
    public long getLong(int index) {
        return 0x0ff & buf.get(index);
    }

    @Override
    public double getDouble(int index) {
        return 0x0ff & buf.get(index);
    }

    @Override
    public Object getObject(int index) {
        return (int)(0x0ff & buf.get(index));
    }

    @Override
    public boolean has(int index) {
        return index > -1 && index < buf.capacity();
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
    public ArrayData delete(int index) {
        throw unsupported("delete");
    }

    @Override
    public ArrayData delete(long fromIndex, long toIndex) {
        throw unsupported("delete");
    }

    @Override
    public ArrayData push(final boolean strict, final Object... items) {
        throw unsupported("push");
    }

    @Override
    public Object pop() {
        throw unsupported("pop");
    }

    @Override
    public ArrayData slice(long from, long to) {
        throw unsupported("slice");
    }

    @Override
    public ArrayData convert(final Class<?> type) {
        throw unsupported("convert");
    }

    private UnsupportedOperationException unsupported(final String method) {
        return new UnsupportedOperationException(method);
    }
}
