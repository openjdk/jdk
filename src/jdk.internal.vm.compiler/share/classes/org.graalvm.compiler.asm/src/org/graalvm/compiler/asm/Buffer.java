/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.asm;

import org.graalvm.compiler.core.common.NumUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Code buffer management for the assembler.
 */
final class Buffer {

    protected ByteBuffer data;

    Buffer(ByteOrder order) {
        data = ByteBuffer.allocate(AsmOptions.InitialCodeBufferSize);
        data.order(order);
    }

    public int position() {
        return data.position();
    }

    public void setPosition(int position) {
        assert position >= 0 && position <= data.limit();
        data.position(position);
    }

    /**
     * Closes this buffer. Any further operations on a closed buffer will result in a
     * {@link NullPointerException}.
     *
     * @param trimmedCopy if {@code true}, then a copy of the underlying byte array up to (but not
     *            including) {@code position()} is returned
     * @return the data in this buffer or a trimmed copy if {@code trimmedCopy} is {@code true}
     */
    public byte[] close(boolean trimmedCopy) {
        byte[] result = data.array();
        if (trimmedCopy) {
            // Make a copy even if result.length == data.position() since
            // the API for trimmedCopy states a copy is always made
            result = Arrays.copyOf(result, data.position());
        }
        data = null;
        return result;
    }

    public byte[] copyData(int start, int end) {
        if (data == null) {
            return null;
        }
        return Arrays.copyOfRange(data.array(), start, end);
    }

    /**
     * Copies the data from this buffer into a given array.
     *
     * @param dst the destination array
     * @param off starting position in {@code dst}
     * @param len number of bytes to copy
     */
    public void copyInto(byte[] dst, int off, int len) {
        System.arraycopy(data.array(), 0, dst, off, len);
    }

    protected void ensureSize(int length) {
        if (length >= data.limit()) {
            byte[] newBuf = Arrays.copyOf(data.array(), length * 4);
            ByteBuffer newData = ByteBuffer.wrap(newBuf);
            newData.order(data.order());
            newData.position(data.position());
            data = newData;
        }
    }

    public void emitBytes(byte[] arr, int off, int len) {
        ensureSize(data.position() + len);
        data.put(arr, off, len);
    }

    public void emitByte(int b) {
        assert NumUtil.isUByte(b) || NumUtil.isByte(b);
        ensureSize(data.position() + 1);
        data.put((byte) (b & 0xFF));
    }

    public void emitShort(int b) {
        assert NumUtil.isUShort(b) || NumUtil.isShort(b);
        ensureSize(data.position() + 2);
        data.putShort((short) b);
    }

    public void emitInt(int b) {
        ensureSize(data.position() + 4);
        data.putInt(b);
    }

    public void emitLong(long b) {
        ensureSize(data.position() + 8);
        data.putLong(b);
    }

    public void emitBytes(byte[] arr, int pos) {
        final int len = arr.length;
        ensureSize(pos + len);
        // Write directly into the underlying array so as to not
        // change the ByteBuffer's position
        System.arraycopy(arr, 0, data.array(), pos, len);
    }

    public void emitByte(int b, int pos) {
        assert NumUtil.isUByte(b) || NumUtil.isByte(b);
        ensureSize(pos + 1);
        data.put(pos, (byte) (b & 0xFF));
    }

    public void emitShort(int b, int pos) {
        assert NumUtil.isUShort(b) || NumUtil.isShort(b);
        ensureSize(pos + 2);
        data.putShort(pos, (short) b).position();
    }

    public void emitInt(int b, int pos) {
        ensureSize(pos + 4);
        data.putInt(pos, b).position();
    }

    public void emitLong(long b, int pos) {
        ensureSize(pos + 8);
        data.putLong(pos, b).position();
    }

    public int getByte(int pos) {
        int b = data.get(pos);
        return b & 0xff;
    }

    public int getShort(int pos) {
        short s = data.getShort(pos);
        return s & 0xffff;
    }

    public int getInt(int pos) {
        return data.getInt(pos);
    }

    public void reset() {
        data.clear();
    }
}
