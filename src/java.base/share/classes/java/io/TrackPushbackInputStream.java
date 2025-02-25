/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PushbackInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * A PushbackInputStream that also tracks the real position in the stream.
 * <p>
 * This class extends {@link PushbackInputStream} to keep track of the
 * actual byte position in the stream, adjusting for pushback operations.
 * </p>
 *
 * @since 25
 */
public class TrackPushbackInputStream extends PushbackInputStream {
    private long realPos = 0;

    /**
     * Constructs a new TrackPushbackInputStream with the specified buffer size.
     *
     * @param  in    the input stream from which bytes will be read.
     * @param  size  the size of the pushback buffer.
     *
     * @since  25
     */
    public TrackPushbackInputStream(InputStream in, int size) {
        super(in, size);
    }

    /**
     * Check to make sure that this stream has not been closed
     */
    private void ensureOpen() throws IOException {
        if (in == null)
            throw new IOException("Stream closed");
    }

    /**
     * {@inheritDoc}
     * <p> Updates the real position when a byte is read. </p>
     *
     * @since 25
     */
    @Override
    public int read() throws IOException {
        ensureOpen();
        if (pos < buf.length) {
            realPos++;
            return buf[pos++] & 0xff;
        }
        int readByte = in.read();
        if (readByte != -1) {
            realPos++;
        }
        return readByte;
    }

    /**
     * {@inheritDoc}
     * <p> Updates the real position when multiple bytes are read. </p>
     *
     * @since 25
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if (b == null) {
            throw new NullPointerException();
        }
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }

        int avail = buf.length - pos;
        if (avail > 0) {
            if (len < avail) {
                avail = len;
            }
            System.arraycopy(buf, pos, b, off, avail);
            pos += avail;
            off += avail;
            len -= avail;

            // Update realPos for bytes consumed from the buffer
            realPos += avail;
        }

        if (len > 0) {
            int bytesRead = in.read(b, off, len);
            if (bytesRead == -1) {
                return avail == 0 ? -1 : avail;
            }

            // Update realPos for bytes read from the stream
            realPos += bytesRead;
            return avail + bytesRead;
        }

        return avail;
    }

    /**
     * {@inheritDoc}
     * <p> Adjusts the real position when an array of bytes are pushed back. </p>
     *
     * @since 25
     */
    @Override
    public void unread(int b) throws IOException {
        super.unread(b);
        realPos--;
    }

    /**
     * {@inheritDoc}
     * <p> Adjusts the real position when a portion of an array of bytes
     * are pushed back. </p>
     *
     * @since 25
     */
    @Override
    public void unread(byte[] b, int off, int len) throws IOException {
        super.unread(b, off, len);
        realPos -= len;
    }

    /**
     * {@inheritDoc}
     * <p> Adjusts the real position when {@code n} bytes of data from the
     * input stream are skipped. </p>
     *
     * @since 25
     */
    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        realPos += skipped;
        return skipped;
    }

    /**
     * {@inheritDoc}
     * <p> Additionally, this method tracks the real position in the stream. </p>
     *
     * @since 25
     */
    @Override
    public long transferTo(OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out");
        ensureOpen();
        if (getClass() == TrackPushbackInputStream.class) {
            int avail = buf.length - pos;
            if (avail > 0) {
                byte[] buffer = Arrays.copyOfRange(buf, pos, buf.length);
                out.write(buffer);
                pos = buffer.length;
                realPos += avail; // Correctly track realPos for buffered data
            }
            long transferred = in.transferTo(out);
            if (transferred > 0) {
                realPos += transferred; // Ensure realPos is updated for streamed data
            }
            try {
                return Math.addExact(avail, transferred);
            } catch (ArithmeticException ignore) {
                return Long.MAX_VALUE;
            }
        } else {
            long transferred = super.transferTo(out);
            if (transferred > 0) {
                realPos += transferred; // Ensure realPos is updated
            }
            return transferred;
        }
    }

    /**
     * Returns the real position in the stream.
     * @return the real position
     *
     * @since 25
     */
    public long getRealPos() {
        return realPos;
    }

}
