/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import jdk.internal.misc.VM;

/**
 * Writes text to a character-output stream, buffering characters so as to
 * provide for the efficient writing of single characters, arrays, and strings.
 *
 * <p> The buffer size may be specified, or the default size may be accepted.
 * The default is large enough for most purposes.
 *
 * <p> A {@code newLine()} method is provided, which uses the platform's own
 * notion of line separator as defined by the system property
 * {@linkplain System#lineSeparator() line.separator}. Not all platforms use the newline character ('\n')
 * to terminate lines. Calling this method to terminate each output line is
 * therefore preferred to writing a newline character directly.
 *
 * <p> In general, a {@code Writer} sends its output immediately to the
 * underlying character or byte stream.  Unless prompt output is required, it
 * is advisable to wrap a {@code BufferedWriter} around any {@code Writer} whose
 * {@code write()} operations may be costly, such as {@code FileWriter}s and
 * {@code OutputStreamWriter}s.  For example,
 *
 * {@snippet lang=java :
 *     PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("foo.out")));
 * }
 *
 * will buffer the {@code PrintWriter}'s output to the file.  Without buffering,
 * each invocation of a {@code print()} method would cause characters to be
 * converted into bytes that would then be written immediately to the file,
 * which can be very inefficient.
 *
 * @apiNote
 * Once wrapped in a {@code BufferedWriter}, the underlying
 * {@code Writer} should not be used directly nor wrapped with
 * another writer.
 *
 * @see PrintWriter
 * @see FileWriter
 * @see OutputStreamWriter
 * @see java.nio.file.Files#newBufferedWriter
 *
 * @author      Mark Reinhold
 * @since       1.1
 */

public class BufferedWriter extends Writer {
    private static final int DEFAULT_INITIAL_BUFFER_SIZE = 512;
    private static final int DEFAULT_MAX_BUFFER_SIZE = 8192;

    private Writer out;

    private char[] cb;
    private int nChars;
    private int nextChar;
    private final int maxChars;  // maximum number of buffers chars

    /**
     * Returns the buffer size to use when no output buffer size specified
     */
    private static int initialBufferSize() {
        if (VM.isBooted() && Thread.currentThread().isVirtual()) {
            return DEFAULT_INITIAL_BUFFER_SIZE;
        } else {
            return DEFAULT_MAX_BUFFER_SIZE;
        }
    }

    /**
     * Creates a buffered character-output stream.
     */
    private BufferedWriter(Writer out, int initialSize, int maxSize) {
        super(out);
        if (initialSize <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }

        this.out = out;
        this.cb = new char[initialSize];
        this.nChars = initialSize;
        this.maxChars = maxSize;
    }

    /**
     * Creates a buffered character-output stream that uses a default-sized
     * output buffer.
     *
     * @param  out  A Writer
     */
    public BufferedWriter(Writer out) {
        this(out, initialBufferSize(), DEFAULT_MAX_BUFFER_SIZE);
    }

    /**
     * Creates a new buffered character-output stream that uses an output
     * buffer of the given size.
     *
     * @param  out  A Writer
     * @param  sz   Output-buffer size, a positive integer
     *
     * @throws     IllegalArgumentException  If {@code sz <= 0}
     */
    public BufferedWriter(Writer out, int sz) {
        this(out, sz, sz);
    }

    /** Checks to make sure that the stream has not been closed */
    private void ensureOpen() throws IOException {
        if (out == null)
            throw new IOException("Stream closed");
    }

    /**
     * Grow char array to fit an additional len characters if needed.
     * If possible, it grows by len+1 to avoid flushing when len chars
     * are added.
     *
     * This method should only be called while holding the lock.
     */
    private void growIfNeeded(int len) {
        int neededSize = nextChar + len + 1;
        if (neededSize < 0)
            neededSize = Integer.MAX_VALUE;
        if (neededSize > nChars && nChars < maxChars) {
            int newSize = min(neededSize, maxChars);
            cb = Arrays.copyOf(cb, newSize);
            nChars = newSize;
        }
    }

    /**
     * Flushes the output buffer to the underlying character stream, without
     * flushing the stream itself.  This method is non-private only so that it
     * may be invoked by PrintStream.
     */
    void flushBuffer() throws IOException {
        synchronized (lock) {
            ensureOpen();
            if (nextChar == 0)
                return;
            out.write(cb, 0, nextChar);
            nextChar = 0;
        }
    }

    /**
     * Writes a single character.
     *
     * @throws     IOException  If an I/O error occurs
     */
    public void write(int c) throws IOException {
        synchronized (lock) {
            ensureOpen();
            growIfNeeded(1);
            if (nextChar >= nChars)
                flushBuffer();
            cb[nextChar++] = (char) c;
        }
    }

    /**
     * Our own little min method, to avoid loading java.lang.Math if we've run
     * out of file descriptors and we're trying to print a stack trace.
     */
    private int min(int a, int b) {
        if (a < b) return a;
        return b;
    }

    /**
     * Writes a portion of an array of characters.
     *
     * <p> Ordinarily this method stores characters from the given array into
     * this stream's buffer, flushing the buffer to the underlying stream as
     * needed.  If the requested length is at least as large as the buffer,
     * however, then this method will flush the buffer and write the characters
     * directly to the underlying stream.  Thus redundant
     * {@code BufferedWriter}s will not copy data unnecessarily.
     *
     * @param  cbuf  A character array
     * @param  off   Offset from which to start reading characters
     * @param  len   Number of characters to write
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code off} is negative, or {@code len} is negative,
     *          or {@code off + len} is negative or greater than the length
     *          of the given array
     *
     * @throws  IOException  If an I/O error occurs
     */
    public void write(char[] cbuf, int off, int len) throws IOException {
        synchronized (lock) {
            ensureOpen();
            Objects.checkFromIndexSize(off, len, cbuf.length);
            if (len == 0) {
                return;
            }

            if (len >= maxChars) {
                /* If the request length exceeds the max size of the output buffer,
                   flush the buffer and then write the data directly.  In this
                   way buffered streams will cascade harmlessly. */
                flushBuffer();
                out.write(cbuf, off, len);
                return;
            }

            growIfNeeded(len);
            int b = off, t = off + len;
            while (b < t) {
                int d = min(nChars - nextChar, t - b);
                System.arraycopy(cbuf, b, cb, nextChar, d);
                b += d;
                nextChar += d;
                if (nextChar >= nChars) {
                    flushBuffer();
                }
            }
        }
    }

    /**
     * Writes a portion of a String.
     *
     * @implSpec
     * While the specification of this method in the
     * {@linkplain java.io.Writer#write(java.lang.String,int,int) superclass}
     * recommends that an {@link IndexOutOfBoundsException} be thrown
     * if {@code len} is negative or {@code off + len} is negative,
     * the implementation in this class does not throw such an exception in
     * these cases but instead simply writes no characters.
     *
     * @param  s     String to be written
     * @param  off   Offset from which to start reading characters
     * @param  len   Number of characters to be written
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code off} is negative,
     *          or {@code off + len} is greater than the length
     *          of the given string
     *
     * @throws  IOException  If an I/O error occurs
     */
    public void write(String s, int off, int len) throws IOException {
        synchronized (lock) {
            ensureOpen();
            growIfNeeded(len);
            int b = off, t = off + len;
            while (b < t) {
                int d = min(nChars - nextChar, t - b);
                s.getChars(b, b + d, cb, nextChar);
                b += d;
                nextChar += d;
                if (nextChar >= nChars)
                    flushBuffer();
            }
        }
    }

    /**
     * Writes a line separator.  The line separator string is defined by the
     * system property {@code line.separator}, and is not necessarily a single
     * newline ('\n') character.
     *
     * @throws     IOException  If an I/O error occurs
     */
    public void newLine() throws IOException {
        write(System.lineSeparator());
    }

    /**
     * Flushes the stream.
     *
     * @throws     IOException  If an I/O error occurs
     */
    public void flush() throws IOException {
        synchronized (lock) {
            flushBuffer();
            out.flush();
        }
    }

    @SuppressWarnings("try")
    public void close() throws IOException {
        synchronized (lock) {
            if (out == null) {
                return;
            }
            try (Writer w = out) {
                flushBuffer();
            } finally {
                out = null;
                cb = null;
            }
        }
    }
}
