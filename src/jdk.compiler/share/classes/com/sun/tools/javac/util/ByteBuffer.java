/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.io.*;

/** A byte buffer is a flexible array which grows when elements are
 *  appended. There are also methods to append names to byte buffers
 *  and to convert byte buffers to names.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ByteBuffer {

    /** An array holding the bytes in this buffer; can be grown.
     */
    public byte[] elems;

    /** The current number of defined bytes in this buffer.
     */
    public int length;

    /** Create a new byte buffer.
     */
    public ByteBuffer() {
        this(64);
    }

    /** Create a new byte buffer with an initial elements array
     *  of given size.
     */
    public ByteBuffer(int initialSize) {
        elems = new byte[initialSize];
        length = 0;
    }

    /** Append byte to this buffer.
     */
    public void appendByte(int b) {
        elems = ArrayUtils.ensureCapacity(elems, length);
        elems[length++] = (byte)b;
    }

    /** Append `len' bytes from byte array,
     *  starting at given `start' offset.
     */
    public void appendBytes(byte[] bs, int start, int len) {
        elems = ArrayUtils.ensureCapacity(elems, length + len);
        System.arraycopy(bs, start, elems, length, len);
        length += len;
    }

    /** Append all bytes from given byte array.
     */
    public void appendBytes(byte[] bs) {
        appendBytes(bs, 0, bs.length);
    }

    /** Append a character as a two byte number.
     */
    public void appendChar(int x) {
        elems = ArrayUtils.ensureCapacity(elems, length + 1);
        elems[length  ] = (byte)((x >>  8) & 0xFF);
        elems[length+1] = (byte)((x      ) & 0xFF);
        length = length + 2;
    }

    /** Append an integer as a four byte number.
     */
    public void appendInt(int x) {
        elems = ArrayUtils.ensureCapacity(elems, length + 3);
        elems[length  ] = (byte)((x >> 24) & 0xFF);
        elems[length+1] = (byte)((x >> 16) & 0xFF);
        elems[length+2] = (byte)((x >>  8) & 0xFF);
        elems[length+3] = (byte)((x      ) & 0xFF);
        length = length + 4;
    }

    /** Append a long as an eight byte number.
     */
    public void appendLong(long x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeLong(x);
            appendBytes(buffer.toByteArray(), 0, 8);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    /** Append a float as a four byte number.
     */
    public void appendFloat(float x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(4);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeFloat(x);
            appendBytes(buffer.toByteArray(), 0, 4);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    /** Append a double as a eight byte number.
     */
    public void appendDouble(double x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeDouble(x);
            appendBytes(buffer.toByteArray(), 0, 8);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    /** Append a name.
     */
    public void appendName(Name name) {
        appendBytes(name.getByteArray(), name.getByteOffset(), name.getByteLength());
    }

     /** Append the content of a given input stream, and then close the stream.
     */
    public void appendStream(InputStream is) throws IOException {
        try (InputStream input = is) {
            while (true) {

                // Read another chunk of data, using size hint from available().
                // If available() is accurate, the array size should be just right.
                int amountToRead = Math.max(input.available(), 64);
                elems = ArrayUtils.ensureCapacity(elems, length + amountToRead);
                int amountRead = input.read(elems, length, amountToRead);
                if (amountRead == -1)
                    break;
                length += amountRead;

                // Check for the common case where input.available() returned the
                // entire remaining input; in that case, avoid an extra array extension.
                // Note we are guaranteed that elems.length >= length + 1 at this point.
                if (amountRead == amountToRead) {
                    int byt = input.read();
                    if (byt == -1)
                        break;
                    elems[length++] = (byte)byt;
                }
            }
        }
    }

    /** Extract an integer at position bp from elems.
     *
     * @param bp starting offset
     * @throws IllegalArgumentException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public int getInt(int bp) {
        verifyRange(bp, 4);
        return
            ((elems[bp] & 0xFF) << 24) +
            ((elems[bp+1] & 0xFF) << 16) +
            ((elems[bp+2] & 0xFF) << 8) +
            (elems[bp+3] & 0xFF);
    }


    /** Extract a long integer at position bp from elems.
     *
     * @param bp starting offset
     * @throws IllegalArgumentException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public long getLong(int bp) {
        verifyRange(bp, 8);
        DataInputStream elemsin =
            new DataInputStream(new ByteArrayInputStream(elems, bp, 8));
        try {
            return elemsin.readLong();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Extract a float at position bp from elems.
     *
     * @param bp starting offset
     * @throws IllegalArgumentException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public float getFloat(int bp) {
        verifyRange(bp, 4);
        DataInputStream elemsin =
            new DataInputStream(new ByteArrayInputStream(elems, bp, 4));
        try {
            return elemsin.readFloat();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Extract a double at position bp from elems.
     *
     * @param bp starting offset
     * @throws IllegalArgumentException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public double getDouble(int bp) {
        verifyRange(bp, 8);
        DataInputStream elemsin =
            new DataInputStream(new ByteArrayInputStream(elems, bp, 8));
        try {
            return elemsin.readDouble();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Extract a character at position bp from elems.
     *
     * @param bp starting offset
     * @throws IllegalArgumentException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public char getChar(int bp) {
        verifyRange(bp, 2);
        return
            (char)(((elems[bp] & 0xFF) << 8) + (elems[bp+1] & 0xFF));
    }

    /** Extract a byte at position bp from elems.
     *
     * @param bp starting offset
     * @throws IllegalArgumentException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public byte getByte(int bp) {
        verifyRange(bp, 1);
        return elems[bp];
    }

    /** Reset to zero length.
     */
    public void reset() {
        length = 0;
    }

    /** Convert contents to name.
     */
    public Name toName(Names names) {
        return names.fromUtf(elems, 0, length);
    }

    /** Verify there are at least the specified number of bytes in this buffer at the specified offset.
     *
     * @param off starting offset
     * @param len required length
     * @throws IllegalArgumentException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code off} or {@code len} is negative
     */
    public void verifyRange(int off, int len) {
        if (off < 0 || len < 0 || off + len < 0 || off + len > length)
            throw new IllegalArgumentException("off=" + off + ", len=" + len + ", length=" + length);
    }
}
