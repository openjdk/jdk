/*
 * Copyright (c) 1994, 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.io;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

import jdk.internal.util.ArraysSupport;

/**
 * This class implements an output stream in which the data is
 * written into a byte array. The buffer automatically grows as data
 * is written to it.
 * The data can be retrieved using {@code toByteArray()} and
 * {@code toString()}.
 * <p>
 * Closing a {@code ByteArrayOutputStream} has no effect. The methods in
 * this class can be called after the stream has been closed without
 * generating an {@code IOException}.
 *
 * @author  Arthur van Hoff
 * @since   1.0
 */

public class ByteArrayOutputStream extends OutputStream {
    /**
     * Factory method for a memory-optimized, unsynchronized instance of ByteArrayOutputStream based
     * on multiple smaller bytes arrays ("segments") which enables far higher
     * capacity and cheaper capacity growth.  See {@code java.io.MemoryOutputStream} for more details.
     *
     * @return java.io.ByteArrayOutputStream
     * @see java.io.MemoryOutputStream
     * @since 25
     */
    public static ByteArrayOutputStream memoryOptimizedInstance() {
        return new MemoryOutputStream();
    }

    /**
     * Identical to {@code ByteArrayOutputStream.memoryOptimizedInstance()} except that it
     * passes a hint for the size of the initial segment.
     *
     * @param   initialSize A capacity hint for the new object
     * @return  java.io.ByteArrayOutputStream
     * @see     java.io.ByteArrayOutputStream#memoryOptimizedInstance()
     * @since   25
     */
    public static ByteArrayOutputStream memoryOptimizedInstance(int initialSize) {
        return new MemoryOutputStream(initialSize);
    }

    /**
     * Factory method for a standard ByteArrayOutputStream.  Equivalent to {@code new java.io.ByteArrayOutputStream()}.
     *
     * @return  java.io.ByteArrayOutputStream
     * @since   25
     */
    public static ByteArrayOutputStream synchronizedInstance() {
        return new ByteArrayOutputStream();
    }

    /**
     * Factory method for a standard ByteArrayOutputStream.  Equivalent to {@code new java.io.ByteArrayOutputStream(int)}.
     *
     * @param   initialSize A capacity hint for the new object
     * @return  java.io.ByteArrayOutputStream
     * @see     java.io.UnsynchronizedByteArrayOutputStream
     * @since   25
     */
    public static ByteArrayOutputStream synchronizedInstance(int initialSize) {
        return new UnsynchronizedByteArrayOutputStream(initialSize);
    }

    /**
     * Factory method for a standard ByteArrayOutputStream, minus synchronization.
     *
     * @return  java.io.ByteArrayOutputStream
     * @see     java.io.UnsynchronizedByteArrayOutputStream
     * @since   25
     */
    public static ByteArrayOutputStream unsynchronizedInstance() {
        return new UnsynchronizedByteArrayOutputStream();
    }

    /**
     * Factory method for a standard ByteArrayOutputStream, minus synchronization.
     *
     * @param initialSize A capacity hint for the new object
     * @return java.io.ByteArrayOutputStream
     * @see java.io.UnsynchronizedByteArrayOutputStream
     * @since 25
     */
    public static ByteArrayOutputStream unsynchronizedInstance(int initialSize) {
        return new UnsynchronizedByteArrayOutputStream(initialSize);
    }

    /**
     * The buffer where data is stored.
     */
    protected byte[] buf;

    /**
     * The number of valid bytes in the buffer.
     */
    protected int count;

    /**
     * Creates a new {@code ByteArrayOutputStream}. The buffer capacity is
     * initially 32 bytes, though its size increases if necessary.
     */
    public ByteArrayOutputStream() {
        this(32);
    }

    /**
     * Creates a new {@code ByteArrayOutputStream}, with a buffer capacity of
     * the specified size, in bytes.
     *
     * @param  size   the initial size.
     * @throws IllegalArgumentException if size is negative.
     */
    public ByteArrayOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: "
                                               + size);
        }
        buf = new byte[size];
    }

    /**
     * Closing a {@code ByteArrayOutputStream} has no effect. The methods in
     * this class can be called after the stream has been closed without
     * generating an {@code IOException}.
     */
    @Override
    public void close() throws IOException {
    }

    /**
     * Increases the capacity if necessary to ensure that it can hold
     * at least the number of elements specified by the minimum
     * capacity argument.
     *
     * @param  minCapacity the desired minimum capacity.
     * @throws OutOfMemoryError if {@code minCapacity < 0} and
     * {@code minCapacity - buf.length > 0}.  This is interpreted as a
     * request for the unsatisfiably large capacity.
     * {@code (long) Integer.MAX_VALUE + (minCapacity - Integer.MAX_VALUE)}.
     */
    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = buf.length;
        int minGrowth = minCapacity - oldCapacity;
        if (minGrowth > 0) {
            buf = Arrays.copyOf(buf, ArraysSupport.newLength(oldCapacity,
                    minGrowth, oldCapacity /* preferred growth */));
        }
    }

    /**
     * Internal, unsynchronized implementation of {@code reset()}.
     * 
     * @see     java.io.ByteArrayOutputStream#reset
     * @since   25
     */
    void internalReset() {
        count = 0;
    }

    /**
     * Internal, unsynchronized implementation of {@code size()}.
     *
     * @return  the value of the {@code count} field, which is the number
     *          of valid bytes in this output stream.
     * @see     java.io.ByteArrayOutputStream#size
     * @since   25
     */
    int internalSize() {
        return count;
    }
    /**
     * Internal, unsynchronized implementation of {@code toByteArray)_}.
     *
     * @return  the current contents of this output stream, as a byte array.
     * @see     java.io.ByteArrayOutputStream#toByteArray()
     * @since   25
     */
    byte[] internalToByteArray() {
        return Arrays.copyOf(buf, count);
    }

    /**
     * Internal, unsynchronized version of {@code toString()}.
     * 
     * @return  String decoded from the buffer's contents.
     * @see     java.io.ByteArrayOutputStream#toString
     * @since   25
     */
    String internalToString() {
        return new String(buf, 0, count);
    }

    /**
     * Internal, unsynchronized version of {@code toString}.
     * 
     * @return  String decoded from the buffer's contents.
     * @see     java.io.ByteArrayOutputStream#toString
     * @since   25
     */
    String internalToString(Charset charset) {
        return new String(buf, 0, count, charset);
    }

    /**
     * Internal, unsynchronized implementation of {@code toString}.
     *
     * @param   charsetName  the name of a supported
     *          {@link Charset charset}
     * @return  String decoded from the buffer's contents.
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     * @see     java.io.ByteArrayOutputStream#toString
     * @since   25
     */
    String internalToString(String charsetName)
            throws UnsupportedEncodingException
        {
            return new String(buf, 0, count, charsetName);
        }

    /**
     * Internal, unsynchronized implementation of {@code write(byte[], int, int)}.
     *
     * @param   b     {@inheritDoc}
     * @param   off   {@inheritDoc}
     * @param   len   {@inheritDoc}
     * @throws  NullPointerException if {@code b} is {@code null}.
     * @throws  IndexOutOfBoundsException if {@code off} is negative,
     * {@code len} is negative, or {@code len} is greater than
     * {@code b.length - off}
     * @see     java.io.ByteArrayOutputStream#write
     * @since   25
     */
    void internalWrite(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    /**
     * Internal, unsynchronized implementation of {@code write(int}.
     *
     * @param   b   the byte to be written.
     * @see     java.io.ByteArrayOutputStream#write
     * @since 25
     */
    void internalWrite(int b) {
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    /**
     * Internal, unsynchronized implementation of {@code writeTo}.
     *
     * @param   out   the output stream to which to write the data.
     * @throws  NullPointerException if {@code out} is {@code null}.
     * @throws  IOException if an I/O error occurs.
     * @see     java.io.ByteArrayOutputStream#writeTo
     * @since   25
     */
    void internalWriteTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    /**
     * Resets the {@code count} field of this {@code ByteArrayOutputStream}
     * to zero, so that all currently accumulated output in the
     * output stream is discarded. The output stream can be used again,
     * reusing the already allocated buffer space.
     *
     * @see     java.io.ByteArrayInputStream#count
     */
    public synchronized void reset() {
        internalReset();
    }

    /**
     * Returns the current size of the buffer.
     *
     * @return  the value of the {@code count} field, which is the number
     *          of valid bytes in this output stream.
     * @see     java.io.ByteArrayOutputStream#count
     */
    public synchronized int size() {
        return internalSize();
    }

    /**
     * Implementations may now contain payloads larger than {@code Integer.MAX_VALUE}, and {@code size} cannot
     * be reported as an int. Returning a long ensures a valid response up to {@code Long.MAX_VALUE}.
     *
     * @return the size of the payload stored as a long
     * @see    java.io.ByteArrayOutputStream#memoryOptimizedInstance()
     * @since  25
     */
    public synchronized long sizeAsLong() {
        return internalSize();
    }

    /**
     * Creates a newly allocated byte array. Its size is the current
     * size of this output stream and the valid contents of the buffer
     * have been copied into it.
     *
     * @return  the current contents of this output stream, as a byte array.
     * @see     java.io.ByteArrayOutputStream#size()
     */
    public synchronized byte[] toByteArray() {
        return internalToByteArray();
    }

    /**
     * Converts the buffer's contents into a string decoding bytes using the
     * default charset. The length of the new {@code String}
     * is a function of the charset, and hence may not be equal to the
     * size of the buffer.
     *
     * <p> This method always replaces malformed-input and unmappable-character
     * sequences with the default replacement string for the
     * default charset. The {@linkplain java.nio.charset.CharsetDecoder}
     * class should be used when more control over the decoding process is
     * required.
     *
     * @see Charset#defaultCharset()
     * @return String decoded from the buffer's contents.
     * @since  1.1
     */
    @Override
    public synchronized String toString() {
        return internalToString();
    }

    /**
     * Converts the buffer's contents into a string by decoding the bytes using
     * the specified {@link Charset charset}. The length of the new
     * {@code String} is a function of the charset, and hence may not be equal
     * to the length of the byte array.
     *
     * <p> This method always replaces malformed-input and unmappable-character
     * sequences with the charset's default replacement string. The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     *
     * @param      charset  the {@linkplain Charset charset}
     *             to be used to decode the {@code bytes}
     * @return     String decoded from the buffer's contents.
     * @since      10
     */
    public synchronized String toString(Charset charset) {
        return internalToString(charset);
    }

    /**
     * Creates a newly allocated string. Its size is the current size of
     * the output stream and the valid contents of the buffer have been
     * copied into it. Each character <i>c</i> in the resulting string is
     * constructed from the corresponding element <i>b</i> in the byte
     * array such that:
     * {@snippet lang=java :
     *     c == (char)(((hibyte & 0xff) << 8) | (b & 0xff))
     * }
     *
     * @deprecated This method does not properly convert bytes into characters.
     * As of JDK&nbsp;1.1, the preferred way to do this is via the
     * {@link #toString(String charsetName)} or {@link #toString(Charset charset)}
     * method, which takes an encoding-name or charset argument,
     * or the {@code toString()} method, which uses the default charset.
     *
     * @param      hibyte    the high byte of each resulting Unicode character.
     * @return     the current contents of the output stream, as a string.
     * @see        java.io.ByteArrayOutputStream#size()
     * @see        java.io.ByteArrayOutputStream#toString(String)
     * @see        java.io.ByteArrayOutputStream#toString()
     * @see        Charset#defaultCharset()
     */
    @Deprecated
    public synchronized String toString(int hibyte) {
        return new String(buf, hibyte, 0, count);
    }

    /**
     * Converts the buffer's contents into a string by decoding the bytes using
     * the named {@link Charset charset}.
     *
     * <p> This method is equivalent to {@code #toString(charset)} that takes a
     * {@link Charset charset}.
     *
     * <p> An invocation of this method of the form
     *
     * {@snippet lang=java :
     *     ByteArrayOutputStream b;
     *     b.toString("UTF-8")
     * }
     *
     * behaves in exactly the same way as the expression
     *
     * {@snippet lang=java :
     *     ByteArrayOutputStream b;
     *     b.toString(StandardCharsets.UTF_8)
     * }
     *
     *
     * @param  charsetName  the name of a supported
     *         {@link Charset charset}
     * @return String decoded from the buffer's contents.
     * @throws UnsupportedEncodingException
     *         If the named charset is not supported
     * @since  1.1
     */
    public synchronized String toString(String charsetName)
            throws UnsupportedEncodingException
        {
            return internalToString(charsetName);
        }

    /**
     * Writes {@code len} bytes from the specified byte array
     * starting at offset {@code off} to this {@code ByteArrayOutputStream}.
     *
     * @param   b     {@inheritDoc}
     * @param   off   {@inheritDoc}
     * @param   len   {@inheritDoc}
     * @throws  NullPointerException if {@code b} is {@code null}.
     * @throws  IndexOutOfBoundsException if {@code off} is negative,
     * {@code len} is negative, or {@code len} is greater than
     * {@code b.length - off}
     */
    @Override
    public synchronized void write(byte[] b, int off, int len) {
        internalWrite(b, off, len);
    }

    /**
     * Writes the specified byte to this {@code ByteArrayOutputStream}.
     *
     * @param   b   the byte to be written.
     */
    @Override
    public synchronized void write(int b) {
        internalWrite(b);
    }

    /**
     * Writes the complete contents of the specified byte array
     * to this {@code ByteArrayOutputStream}.
     *
     * @apiNote
     * This method is equivalent to {@link #write(byte[],int,int)
     * write(b, 0, b.length)}.
     *
     * @param   b     the data.
     * @throws  NullPointerException if {@code b} is {@code null}.
     * @since   11
     */
    public void writeBytes(byte[] b) {
        write(b, 0, b.length);
    }

    /**
     * Writes the complete contents of this {@code ByteArrayOutputStream} to
     * the specified output stream argument, as if by calling the output
     * stream's write method using {@code out.write(buf, 0, count)}.
     *
     * @param   out   the output stream to which to write the data.
     * @throws  NullPointerException if {@code out} is {@code null}.
     * @throws  IOException if an I/O error occurs.
     */
    public synchronized void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

}
