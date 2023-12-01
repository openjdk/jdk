/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * This class extends ByteArrayOutputStream by optimizing internal buffering.
 * It skips bounds checking, as the buffers are known and input previously
 * checked.  toByteArray() returns the internal buffer to avoid an extra copy.
 *
 * This uses `count` to determine the state of `buf`.  `buf` can still
 * point to an array while `count` equals zero.
 */
final class AEADBufferedStream extends ByteArrayOutputStream {

    /**
     * Create an instance with the specified buffer
     */

    public AEADBufferedStream(int len) {
        super(len);
    }

    /**
     * This method saves memory by returning the internal buffer. If
     * internal buffer is larger than the data stored {@code count}, a copy is
     * returned.
     *
     * @return internal or new byte array of non-blocksize data.
     */
    @Override
    public byte[] toByteArray() {
        if (buf.length > count) {
            return Arrays.copyOfRange(buf, 0, count);
        }
        return buf;
    }

    /**
     * Takes a ByteBuffer writing non-blocksize data directly to the internal
     * buffer.
     * @param src remaining non-blocksize ByteBuffer
     */
    public void write(ByteBuffer src) {
        int pos = src.position();
        int len = src.remaining();

        if (src.hasArray()) {
            write(src.array(), pos + src.arrayOffset(), len);
            src.position(pos + len);
            return;
        }
        if (buf == null) {
            buf = new byte[len];
            src.get(buf);
            count = buf.length;
        } else {
            if (buf.length < (count + len)) {
                buf = Arrays.copyOf(buf, count + len);
            }
            src.get(buf, count, len);
            count += len;
        }
    }

    @Override
    public void write(byte[] in, int offset, int len) {
        if (buf == null) {
            buf = new byte[len];
            System.arraycopy(in, offset, buf, 0, len);
            count = buf.length;
        } else {
            // Create a new larger buffer and append the new data
            if (buf.length < count + len) {
                buf = Arrays.copyOf(buf, count + len);
            }

            System.arraycopy(in, offset, buf, count, len);
            count += len;
        }
    }

    @Override
    public String toString() {
        return (count == 0 ? "null" : HexFormat.of().formatHex(buf));
    }
}
