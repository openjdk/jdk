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
        this(new byte[initialSize]);
    }

    /** Create a new byte buffer using the given array for storage.
     */
    public ByteBuffer(byte[] elems) {
        this.elems = elems;
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

    /** Append a name encoded in Modified UTF-8.
     */
    public void appendName(Name name) {
        int utf8len = name.getUtf8Length();
        elems = ArrayUtils.ensureCapacity(elems, length + utf8len);
        name.getUtf8Bytes(elems, length);
        length += utf8len;
    }

     /** Append the content of the given input stream.
     */
    public void appendStream(InputStream input) throws IOException {
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

    /** Extract an integer at position bp from elems.
     *
     * @param bp starting offset
     * @throws UnderflowException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public int getInt(int bp) throws UnderflowException {
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
     * @throws UnderflowException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public long getLong(int bp) throws UnderflowException {
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
     * @throws UnderflowException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public float getFloat(int bp) throws UnderflowException {
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
     * @throws UnderflowException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public double getDouble(int bp) throws UnderflowException {
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
     * @throws UnderflowException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public char getChar(int bp) throws UnderflowException {
        verifyRange(bp, 2);
        return
            (char)(((elems[bp] & 0xFF) << 8) + (elems[bp+1] & 0xFF));
    }

    /** Extract a byte at position bp from elems.
     *
     * @param bp starting offset
     * @throws UnderflowException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code bp} is negative
     */
    public byte getByte(int bp) throws UnderflowException {
        verifyRange(bp, 1);
        return elems[bp];
    }

    /** Reset to zero length.
     */
    public void reset() {
        length = 0;
    }

    /** Convert contents to name.
     *  @throws InvalidUtfException if invalid Modified UTF-8 is encountered
     */
    public Name toName(Names names) throws InvalidUtfException {
        return names.fromUtf(elems, 0, length, Convert.Validation.STRICT);
    }

    /** Verify there are at least the specified number of bytes in this buffer at the specified offset.
     *
     * @param off starting offset
     * @param len required length
     * @throws UnderflowException if there is not enough data in this buffer
     * @throws IllegalArgumentException if {@code off} or {@code len} is negative
     */
    public void verifyRange(int off, int len) throws UnderflowException {
        if (off < 0 || len < 0)
            throw new IllegalArgumentException("off=" + off + ", len=" + len);
        if (off + len < 0 || off + len > length)
            throw new UnderflowException(length);
    }

    /** Create a {@link java.nio.ByteBuffer} view of this instance.
     *
     *  <p>
     *  If this instance is modified, the returned buffer may no longer reflect it.
     */
    public java.nio.ByteBuffer asByteBuffer() {
        return java.nio.ByteBuffer.wrap(elems, 0, length);
    }

// UnderflowException

    /** Thrown when trying to read past the end of the buffer.
     */
    public static class UnderflowException extends Exception {

        private static final long serialVersionUID = 0;

        private final int length;

        public UnderflowException(int length) {
            this.length = length;
        }

        /** Get the length of the buffer, which apparently is not long enough.
         */
        public int getLength() {
            return length;
        }
    }
}
