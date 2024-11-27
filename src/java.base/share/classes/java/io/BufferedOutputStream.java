/*
 * Copyright (c) 1994, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import jdk.internal.misc.VM;

/**
 * The class implements a buffered output stream. By setting up such
 * an output stream, an application can write bytes to the underlying
 * output stream without necessarily causing a call to the underlying
 * system for each byte written.
 *
 * @apiNote
 * Once wrapped in a {@code BufferedOutputStream}, the underlying
 * {@code OutputStream} should not be used directly nor wrapped with
 * another stream.
 *
 * @author  Arthur van Hoff
 * @since   1.0
 */
public class BufferedOutputStream extends FilterOutputStream {
    private static final int DEFAULT_INITIAL_BUFFER_SIZE = 512;
    private static final int DEFAULT_MAX_BUFFER_SIZE = 8192;

    /**
     * The internal buffer where data is stored.
     */
    protected byte[] buf;

    /**
     * The number of valid bytes in the buffer. This value is always
     * in the range {@code 0} through {@code buf.length}; elements
     * {@code buf[0]} through {@code buf[count-1]} contain valid
     * byte data.
     */
    protected int count;

    /**
     * Max size of the internal buffer.
     */
    private final int maxBufSize;

    /**
     * Returns the buffer size to use when no output buffer size specified.
     */
    private static int initialBufferSize() {
        if (VM.isBooted() && Thread.currentThread().isVirtual()) {
            return DEFAULT_INITIAL_BUFFER_SIZE;
        } else {
            return DEFAULT_MAX_BUFFER_SIZE;
        }
    }

    /**
     * Creates a new buffered output stream.
     */
    private BufferedOutputStream(OutputStream out, int initialSize, int maxSize) {
        super(out);

        if (initialSize <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }

        if (getClass() == BufferedOutputStream.class) {
            // resizable when not sub-classed
            this.buf = new byte[initialSize];
        } else {
            this.buf = new byte[maxSize];
        }
        this.maxBufSize = maxSize;
    }

    /**
     * Creates a new buffered output stream to write data to the
     * specified underlying output stream.
     *
     * @param   out   the underlying output stream.
     */
    public BufferedOutputStream(OutputStream out) {
        this(out, initialBufferSize(), DEFAULT_MAX_BUFFER_SIZE);
    }

    /**
     * Creates a new buffered output stream to write data to the
     * specified underlying output stream with the specified buffer
     * size.
     *
     * @param   out    the underlying output stream.
     * @param   size   the buffer size.
     * @throws  IllegalArgumentException if size &lt;= 0.
     */
    public BufferedOutputStream(OutputStream out, int size) {
        this(out, size, size);
    }

    /** Flush the internal buffer */
    private void flushBuffer() throws IOException {
        if (count > 0) {
            out.write(buf, 0, count);
            count = 0;
        }
    }

    /**
     * Grow buf to fit an additional len bytes if needed.
     * If possible, it grows by len+1 to avoid flushing when len bytes
     * are added. A no-op if the buffer is not resizable.
     */
    private void growIfNeeded(int len) {
        int neededSize = count + len + 1;
        if (neededSize < 0)
            neededSize = Integer.MAX_VALUE;
        int bufSize = buf.length;
        if (neededSize > bufSize && bufSize < maxBufSize) {
            int newSize = Math.min(neededSize, maxBufSize);
            buf = Arrays.copyOf(buf, newSize);
        }
    }

    /**
     * Writes the specified byte to this buffered output stream.
     *
     * @param      b   the byte to be written.
     * @throws     IOException  if an I/O error occurs.
     */
    @Override
    public synchronized void write(int b) throws IOException {
        growIfNeeded(1);
        if (count >= buf.length) {
            flushBuffer();
        }
        buf[count++] = (byte)b;
    }

    /**
     * Writes {@code len} bytes from the specified byte array
     * starting at offset {@code off} to this buffered output stream.
     *
     * <p> Ordinarily this method stores bytes from the given array into this
     * stream's buffer, flushing the buffer to the underlying output stream as
     * needed.  If the requested length is at least as large as this stream's
     * buffer, however, then this method will flush the buffer and write the
     * bytes directly to the underlying output stream.  Thus redundant
     * {@code BufferedOutputStream}s will not copy data unnecessarily.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @throws     IOException  if an I/O error occurs.
     * @throws     IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        if (len >= maxBufSize) {
            /* If the request length exceeds the max size of the output buffer,
               flush the output buffer and then write the data directly.
               In this way buffered streams will cascade harmlessly. */
            flushBuffer();
            out.write(b, off, len);
            return;
        }
        growIfNeeded(len);
        if (len > buf.length - count) {
            flushBuffer();
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    /**
     * Flushes this buffered output stream. This forces any buffered
     * output bytes to be written out to the underlying output stream.
     *
     * @throws     IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    @Override
    public synchronized void flush() throws IOException {
        flushBuffer();
        out.flush();
    }
}
