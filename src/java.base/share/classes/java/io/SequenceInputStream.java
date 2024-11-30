/*
 * Copyright (c) 1994, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;

/**
 * A {@code SequenceInputStream} represents
 * the logical concatenation of other input
 * streams. It starts out with an ordered
 * collection of input streams and reads from
 * the first one until end of file is reached,
 * whereupon it reads from the second one,
 * and so on, until end of file is reached
 * on the last of the contained input streams.
 *
 * @author  Arthur van Hoff
 * @since   1.0
 */
public class SequenceInputStream extends InputStream {
    private final Enumeration<? extends InputStream> e;
    private InputStream in;

    /**
     * Initializes a newly created {@code SequenceInputStream}
     * by remembering the argument, which must
     * be an {@code Enumeration}  that produces
     * objects whose run-time type is {@code InputStream}.
     * The input streams that are  produced by
     * the enumeration will be read, in order,
     * to provide the bytes to be read  from this
     * {@code SequenceInputStream}. After
     * each input stream from the enumeration
     * is exhausted, it is closed by calling its
     * {@code close} method.
     *
     * @param   e   an enumeration of input streams.
     * @see     java.util.Enumeration
     */
    public SequenceInputStream(Enumeration<? extends InputStream> e) {
        this.e = e;
        peekNextStream();
    }

    /**
     * Initializes a newly
     * created {@code SequenceInputStream}
     * by remembering the two arguments, which
     * will be read in order, first {@code s1}
     * and then {@code s2}, to provide the
     * bytes to be read from this {@code SequenceInputStream}.
     *
     * @param   s1   the first input stream to read.
     * @param   s2   the second input stream to read.
     */
    public SequenceInputStream(InputStream s1, InputStream s2) {
        this(Collections.enumeration(Arrays.asList(s1, s2)));
    }

    /**
     * Continues reading in the next stream if an EOF is reached.
     */
    final void nextStream() throws IOException {
        if (in != null) {
            in.close();
        }
        peekNextStream();
    }

    private void peekNextStream() {
        if (e.hasMoreElements()) {
            in = e.nextElement();
            if (in == null)
                throw new NullPointerException();
        } else {
            in = null;
        }
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from the current underlying input stream without
     * blocking by the next invocation of a method for the current
     * underlying input stream. The next invocation might be
     * the same thread or another thread.  A single read or skip of this
     * many bytes will not block, but may read or skip fewer bytes.
     * <p>
     * This method simply calls {@code available} of the current underlying
     * input stream and returns the result.
     *
     * @return   an estimate of the number of bytes that can be read (or
     *           skipped over) from the current underlying input stream
     *           without blocking or {@code 0} if this input stream
     *           has been closed by invoking its {@link #close()} method
     * @throws   IOException {@inheritDoc}
     *
     * @since    1.1
     */
    @Override
    public int available() throws IOException {
        if (in == null) {
            return 0; // no way to signal EOF from available()
        }
        return in.available();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method
     * tries to read one byte from the current substream. If it
     * reaches the end of the stream, it calls the {@code close}
     * method of the current substream and begins reading from the next
     * substream.
     *
     * @return     {@inheritDoc}
     * @throws     IOException  if an I/O error occurs.
     */
    @Override
    public int read() throws IOException {
        while (in != null) {
            int c = in.read();
            if (c != -1) {
                return c;
            }
            nextStream();
        }
        return -1;
    }

    /**
     * Reads up to {@code len} bytes of data from this input stream into an
     * array of bytes.  If the end of the last contained stream has been reached
     * then {@code -1} is returned.  Otherwise, if {@code len} is not zero, the
     * method blocks until at least 1 byte of input is available; if {@code len}
     * is zero, no bytes are read and {@code 0} is returned.
     * <p>
     * The {@code read} method of {@code SequenceInputStream}
     * tries to read the data from the current substream. If it fails to
     * read any bytes because the substream has reached the end of
     * the stream, it calls the {@code close} method of the current
     * substream and begins reading from the next substream.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in array {@code b}
     *                   at which the data is written.
     * @param      len   the maximum number of bytes read.
     * @return     the total number of bytes read into the buffer, or
     *             {@code -1} if there is no more data because the end of
     *             the last contained stream has been reached.
     * @throws     NullPointerException if the end of the last contained
     *             stream has not been reached and {@code b} is {@code null}.
     * @throws     IndexOutOfBoundsException if the end of the last contained
     *             stream has not been reached and {@code off} is negative,
     *             {@code len} is negative, or {@code len} is
     *             greater than {@code b.length - off}
     * @throws     IOException  if an I/O error occurs.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (in == null) {
            return -1;
        } else if (b == null) {
            throw new NullPointerException();
        }
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }
        do {
            int n = in.read(b, off, len);
            if (n > 0) {
                return n;
            }
            nextStream();
        } while (in != null);
        return -1;
    }

    /**
     * {@inheritDoc}
     * A closed {@code SequenceInputStream}
     * cannot  perform input operations and cannot
     * be reopened.
     * <p>
     * If this stream was created
     * from an enumeration, all remaining elements
     * are requested from the enumeration and closed
     * before the {@code close} method returns.
     *
     * @throws     IOException {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        IOException ioe = null;
        while (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                if (ioe == null) {
                    ioe = e;
                } else {
                    ioe.addSuppressed(e);
                }
            }
            peekNextStream();
        }
        if (ioe != null) {
            throw ioe;
        }
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out");
        if (getClass() == SequenceInputStream.class) {
            long transferred = 0;
            while (in != null) {
                long numTransferred = in.transferTo(out);
                // increment the total transferred byte count
                // only if we haven't already reached the Long.MAX_VALUE
                if (transferred < Long.MAX_VALUE) {
                    try {
                        transferred = Math.addExact(transferred, numTransferred);
                    } catch (ArithmeticException ignore) {
                        transferred = Long.MAX_VALUE;
                    }
                }
                nextStream();
            }
            return transferred;
        } else {
            return super.transferTo(out);
        }
    }
}
