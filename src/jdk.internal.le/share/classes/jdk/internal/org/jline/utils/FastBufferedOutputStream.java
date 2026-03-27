/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A simple, non-synchronized buffered output stream for improved performance.
 *
 * <p>
 * The FastBufferedOutputStream class provides a buffered output stream implementation
 * that improves performance by reducing the number of calls to the underlying output
 * stream. Unlike the standard BufferedOutputStream, this implementation does not
 * include synchronization, making it faster but not thread-safe.
 * </p>
 *
 * <p>
 * This class is particularly useful in single-threaded contexts where the overhead
 * of synchronization is unnecessary. It uses a fixed-size buffer (8192 bytes) to
 * collect written data before flushing it to the underlying output stream.
 * </p>
 *
 * <p>
 * Key features include:
 * </p>
 * <ul>
 *   <li>No synchronization overhead for improved performance</li>
 *   <li>Fixed-size buffer to reduce system calls</li>
 *   <li>Compatible with the standard OutputStream API</li>
 * </ul>
 *
 * <p>
 * Note that this class is not thread-safe and should not be used in multi-threaded
 * contexts without external synchronization.
 * </p>
 */
public class FastBufferedOutputStream extends FilterOutputStream {

    protected final byte[] buf = new byte[8192];
    protected int count;

    public FastBufferedOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        if (count >= buf.length) {
            flushBuffer();
        }
        buf[count++] = (byte) b;
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if (len >= buf.length) {
            flushBuffer();
            out.write(b, off, len);
            return;
        }
        if (len > buf.length - count) {
            flushBuffer();
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    private void flushBuffer() throws IOException {
        if (count > 0) {
            out.write(buf, 0, count);
            count = 0;
        }
    }

    @Override
    public void flush() throws IOException {
        flushBuffer();
        out.flush();
    }
}
