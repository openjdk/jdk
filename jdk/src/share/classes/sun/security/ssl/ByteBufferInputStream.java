/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.ssl;

import java.io.*;
import java.nio.*;

/**
 * A simple InputStream which uses ByteBuffers as it's backing store.
 * <P>
 * The only IOException should come if the InputStream has been closed.
 * All other IOException should not occur because all the data is local.
 * Data reads on an exhausted ByteBuffer returns a -1.
 *
 * @author  Brad Wetmore
 */
class ByteBufferInputStream extends InputStream {

    ByteBuffer bb;

    ByteBufferInputStream(ByteBuffer bb) {
        this.bb = bb;
    }

    /**
     * Returns a byte from the ByteBuffer.
     *
     * Increments position().
     */
    public int read() throws IOException {

        if (bb == null) {
            throw new IOException("read on a closed InputStream");
        }

        if (bb.remaining() == 0) {
            return -1;
        }
        return bb.get();
    }

    /**
     * Returns a byte array from the ByteBuffer.
     *
     * Increments position().
     */
    public int read(byte b[]) throws IOException {

        if (bb == null) {
            throw new IOException("read on a closed InputStream");
        }

        return read(b, 0, b.length);
    }

    /**
     * Returns a byte array from the ByteBuffer.
     *
     * Increments position().
     */
    public int read(byte b[], int off, int len) throws IOException {

        if (bb == null) {
            throw new IOException("read on a closed InputStream");
        }

        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                   ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int length = Math.min(bb.remaining(), len);
        if (length == 0) {
            return -1;
        }

        bb.get(b, off, length);
        return length;
    }

    /**
     * Skips over and discards <code>n</code> bytes of data from this input
     * stream.
     */
    public long skip(long n) throws IOException {

        if (bb == null) {
            throw new IOException("skip on a closed InputStream");
        }

        if (n <= 0) {
            return 0;
        }

        /*
         * ByteBuffers have at most an int, so lose the upper bits.
         * The contract allows this.
         */
        int nInt = (int) n;
        int skip = Math.min(bb.remaining(), nInt);

        bb.position(bb.position() + skip);

        return nInt;
    }

    /**
     * Returns the number of bytes that can be read (or skipped over)
     * from this input stream without blocking by the next caller of a
     * method for this input stream.
     */
    public int available() throws IOException {

        if (bb == null) {
            throw new IOException("available on a closed InputStream");
        }

        return bb.remaining();
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    public void close() throws IOException {
        bb = null;
    }

    /**
     * Marks the current position in this input stream.
     */
    public synchronized void mark(int readlimit) {}

    /**
     * Repositions this stream to the position at the time the
     * <code>mark</code> method was last called on this input stream.
     */
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    /**
     * Tests if this input stream supports the <code>mark</code> and
     * <code>reset</code> methods.
     */
    public boolean markSupported() {
        return false;
    }
}
