/*
 * Copyright (c) 1996, 2011, Oracle and/or its affiliates. All rights reserved.
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


/**
 * A character-stream reader that allows characters to be pushed back into the
 * stream.
 *
 * @author      Mark Reinhold
 * @since       JDK1.1
 */

public class PushbackReader extends FilterReader {

    /** Pushback buffer */
    private char[] buf;

    /** Current position in buffer */
    private int pos;

    /**
     * Creates a new pushback reader with a pushback buffer of the given size.
     *
     * @param   in   The reader from which characters will be read
     * @param   size The size of the pushback buffer
     * @exception IllegalArgumentException if {@code size <= 0}
     */
    public PushbackReader(Reader in, int size) {
        super(in);
        if (size <= 0) {
            throw new IllegalArgumentException("size <= 0");
        }
        this.buf = new char[size];
        this.pos = size;
    }

    /**
     * Creates a new pushback reader with a one-character pushback buffer.
     *
     * @param   in  The reader from which characters will be read
     */
    public PushbackReader(Reader in) {
        this(in, 1);
    }

    /** Checks to make sure that the stream has not been closed. */
    private void ensureOpen() throws IOException {
        if (buf == null)
            throw new IOException("Stream closed");
    }

    /**
     * Reads a single character.
     *
     * @return     The character read, or -1 if the end of the stream has been
     *             reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    public int read() throws IOException {
        synchronized (lock) {
            ensureOpen();
            if (pos < buf.length)
                return buf[pos++];
            else
                return super.read();
        }
    }

    /**
     * Reads characters into a portion of an array.
     *
     * @param      cbuf  Destination buffer
     * @param      off   Offset at which to start writing characters
     * @param      len   Maximum number of characters to read
     *
     * @return     The number of characters read, or -1 if the end of the
     *             stream has been reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    public int read(char cbuf[], int off, int len) throws IOException {
        synchronized (lock) {
            ensureOpen();
            try {
                if (len <= 0) {
                    if (len < 0) {
                        throw new IndexOutOfBoundsException();
                    } else if ((off < 0) || (off > cbuf.length)) {
                        throw new IndexOutOfBoundsException();
                    }
                    return 0;
                }
                int avail = buf.length - pos;
                if (avail > 0) {
                    if (len < avail)
                        avail = len;
                    System.arraycopy(buf, pos, cbuf, off, avail);
                    pos += avail;
                    off += avail;
                    len -= avail;
                }
                if (len > 0) {
                    len = super.read(cbuf, off, len);
                    if (len == -1) {
                        return (avail == 0) ? -1 : avail;
                    }
                    return avail + len;
                }
                return avail;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IndexOutOfBoundsException();
            }
        }
    }

    /**
     * Pushes back a single character by copying it to the front of the
     * pushback buffer. After this method returns, the next character to be read
     * will have the value <code>(char)c</code>.
     *
     * @param  c  The int value representing a character to be pushed back
     *
     * @exception  IOException  If the pushback buffer is full,
     *                          or if some other I/O error occurs
     */
    public void unread(int c) throws IOException {
        synchronized (lock) {
            ensureOpen();
            if (pos == 0)
                throw new IOException("Pushback buffer overflow");
            buf[--pos] = (char) c;
        }
    }

    /**
     * Pushes back a portion of an array of characters by copying it to the
     * front of the pushback buffer.  After this method returns, the next
     * character to be read will have the value <code>cbuf[off]</code>, the
     * character after that will have the value <code>cbuf[off+1]</code>, and
     * so forth.
     *
     * @param  cbuf  Character array
     * @param  off   Offset of first character to push back
     * @param  len   Number of characters to push back
     *
     * @exception  IOException  If there is insufficient room in the pushback
     *                          buffer, or if some other I/O error occurs
     */
    public void unread(char cbuf[], int off, int len) throws IOException {
        synchronized (lock) {
            ensureOpen();
            if (len > pos)
                throw new IOException("Pushback buffer overflow");
            pos -= len;
            System.arraycopy(cbuf, off, buf, pos, len);
        }
    }

    /**
     * Pushes back an array of characters by copying it to the front of the
     * pushback buffer.  After this method returns, the next character to be
     * read will have the value <code>cbuf[0]</code>, the character after that
     * will have the value <code>cbuf[1]</code>, and so forth.
     *
     * @param  cbuf  Character array to push back
     *
     * @exception  IOException  If there is insufficient room in the pushback
     *                          buffer, or if some other I/O error occurs
     */
    public void unread(char cbuf[]) throws IOException {
        unread(cbuf, 0, cbuf.length);
    }

    /**
     * Tells whether this stream is ready to be read.
     *
     * @exception  IOException  If an I/O error occurs
     */
    public boolean ready() throws IOException {
        synchronized (lock) {
            ensureOpen();
            return (pos < buf.length) || super.ready();
        }
    }

    /**
     * Marks the present position in the stream. The <code>mark</code>
     * for class <code>PushbackReader</code> always throws an exception.
     *
     * @exception  IOException  Always, since mark is not supported
     */
    public void mark(int readAheadLimit) throws IOException {
        throw new IOException("mark/reset not supported");
    }

    /**
     * Resets the stream. The <code>reset</code> method of
     * <code>PushbackReader</code> always throws an exception.
     *
     * @exception  IOException  Always, since reset is not supported
     */
    public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    /**
     * Tells whether this stream supports the mark() operation, which it does
     * not.
     */
    public boolean markSupported() {
        return false;
    }

    /**
     * Closes the stream and releases any system resources associated with
     * it. Once the stream has been closed, further read(),
     * unread(), ready(), or skip() invocations will throw an IOException.
     * Closing a previously closed stream has no effect.
     *
     * @exception  IOException  If an I/O error occurs
     */
    public void close() throws IOException {
        super.close();
        buf = null;
    }

    /**
     * Skips characters.  This method will block until some characters are
     * available, an I/O error occurs, or the end of the stream is reached.
     *
     * @param  n  The number of characters to skip
     *
     * @return    The number of characters actually skipped
     *
     * @exception  IllegalArgumentException  If <code>n</code> is negative.
     * @exception  IOException  If an I/O error occurs
     */
    public long skip(long n) throws IOException {
        if (n < 0L)
            throw new IllegalArgumentException("skip value is negative");
        synchronized (lock) {
            ensureOpen();
            int avail = buf.length - pos;
            if (avail > 0) {
                if (n <= avail) {
                    pos += n;
                    return n;
                } else {
                    pos = buf.length;
                    n -= avail;
                }
            }
            return avail + super.skip(n);
        }
    }
}
