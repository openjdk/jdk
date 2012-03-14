/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.jobjc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A wrapper around a direct ByteBuffer and its native pointer. For documentation, @see java.nio.ByteBuffer
 */
public class NativeBuffer {
    static native long getPtrOfBuffer(final ByteBuffer byteBuffer);

    public final ByteBuffer buffer;
    public final long bufferPtr;

    public NativeBuffer(final int capacity){
        this(ByteBuffer.allocateDirect(capacity));
    }

    /**
     * Wrap a ByteBuffer and set the ByteOrder to nativeOrder.
     */
    public NativeBuffer(ByteBuffer buffer){
        this.buffer = buffer;
        this.bufferPtr = getPtrOfBuffer(buffer);
        assert buffer != null;
        assert bufferPtr != 0;
        this.buffer.order(ByteOrder.nativeOrder());
    }

    public byte   get() { return buffer.get(); }
    public char   getChar() { return buffer.getChar(); }
    public double getDouble() { return buffer.getDouble(); }
    public float  getFloat() { return buffer.getFloat(); }
    public int    getInt() { return buffer.getInt(); }
    public long   getLong() { return buffer.getLong(); }
    public short  getShort() { return buffer.getShort(); }
    public NativeBuffer put(byte b) { buffer.put(b); return this; }
    public NativeBuffer put(NativeBuffer src) { buffer.put(src.buffer); return this; }
    public NativeBuffer putChar(char c) { buffer.putChar(c); return this; }
    public NativeBuffer putDouble(double d) { buffer.putDouble(d); return this; }
    public NativeBuffer putFloat(float f) { buffer.putFloat(f); return this; }
    public NativeBuffer putInt(int i) { buffer.putInt(i); return this; }
    public NativeBuffer putLong(long l) { buffer.putLong(l); return this; }
    public NativeBuffer putShort(short s) { buffer.putShort(s); return this; }

    public int capacity() { return buffer.capacity(); }
    public int position() { return buffer.position(); }
    public NativeBuffer position(int newPosition) { buffer.position(newPosition); return this; }
    public NativeBuffer rewind(){ buffer.rewind(); return this; }

    public int limit() { return buffer.limit(); }
    public NativeBuffer limit(final int sizeof) { buffer.limit(sizeof); return this; }

    public int remaining() { return buffer.remaining(); }

    public NativeBuffer slice(){ return new NativeBuffer(buffer.slice()); }

    @Override public String toString() {
        final StringBuilder builder = new StringBuilder();

        for(int i = 0; i < limit(); i += JObjCRuntime.PTR_LEN){
            if(position() == i)
                builder.append("*");
            if(JObjCRuntime.IS32)
                builder.append(buffer.getInt(i));
            else
                builder.append(buffer.getLong(i));
            builder.append(" ");
        }

        return builder.toString();
    }

    public long positionPtr() {
        return bufferPtr + position();
    }

    /**
     * bufferPtr <= ptr && ptr < bufferPtr + capacity();
     */
    public boolean ptrInBounds(final long ptr){
        return bufferPtr <= ptr && ptr < bufferPtr + capacity();
    }
}
