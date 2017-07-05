/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Piped character-input streams.
 *
 * @author      Mark Reinhold
 * @since       1.1
 */

public class PipedReader extends Reader {
    boolean closedByWriter = false;
    boolean closedByReader = false;
    boolean connected = false;

    /* REMIND: identification of the read and write sides needs to be
       more sophisticated.  Either using thread groups (but what about
       pipes within a thread?) or using finalization (but it may be a
       long time until the next GC). */
    Thread readSide;
    Thread writeSide;

   /**
    * The size of the pipe's circular input buffer.
    */
    private static final int DEFAULT_PIPE_SIZE = 1024;

    /**
     * The circular buffer into which incoming data is placed.
     */
    char buffer[];

    /**
     * The index of the position in the circular buffer at which the
     * next character of data will be stored when received from the connected
     * piped writer. <code>in&lt;0</code> implies the buffer is empty,
     * <code>in==out</code> implies the buffer is full
     */
    int in = -1;

    /**
     * The index of the position in the circular buffer at which the next
     * character of data will be read by this piped reader.
     */
    int out = 0;

    /**
     * Creates a <code>PipedReader</code> so
     * that it is connected to the piped writer
     * <code>src</code>. Data written to <code>src</code>
     * will then be available as input from this stream.
     *
     * @param      src   the stream to connect to.
     * @exception  IOException  if an I/O error occurs.
     */
    public PipedReader(PipedWriter src) throws IOException {
        this(src, DEFAULT_PIPE_SIZE);
    }

    /**
     * Creates a <code>PipedReader</code> so that it is connected
     * to the piped writer <code>src</code> and uses the specified
     * pipe size for the pipe's buffer. Data written to <code>src</code>
     * will then be  available as input from this stream.

     * @param      src       the stream to connect to.
     * @param      pipeSize  the size of the pipe's buffer.
     * @exception  IOException  if an I/O error occurs.
     * @exception  IllegalArgumentException if {@code pipeSize <= 0}.
     * @since      1.6
     */
    public PipedReader(PipedWriter src, int pipeSize) throws IOException {
        initPipe(pipeSize);
        connect(src);
    }


    /**
     * Creates a <code>PipedReader</code> so
     * that it is not yet {@linkplain #connect(java.io.PipedWriter)
     * connected}. It must be {@linkplain java.io.PipedWriter#connect(
     * java.io.PipedReader) connected} to a <code>PipedWriter</code>
     * before being used.
     */
    public PipedReader() {
        initPipe(DEFAULT_PIPE_SIZE);
    }

    /**
     * Creates a <code>PipedReader</code> so that it is not yet
     * {@link #connect(java.io.PipedWriter) connected} and uses
     * the specified pipe size for the pipe's buffer.
     * It must be  {@linkplain java.io.PipedWriter#connect(
     * java.io.PipedReader) connected} to a <code>PipedWriter</code>
     * before being used.
     *
     * @param   pipeSize the size of the pipe's buffer.
     * @exception  IllegalArgumentException if {@code pipeSize <= 0}.
     * @since      1.6
     */
    public PipedReader(int pipeSize) {
        initPipe(pipeSize);
    }

    private void initPipe(int pipeSize) {
        if (pipeSize <= 0) {
            throw new IllegalArgumentException("Pipe size <= 0");
        }
        buffer = new char[pipeSize];
    }

    /**
     * Causes this piped reader to be connected
     * to the piped  writer <code>src</code>.
     * If this object is already connected to some
     * other piped writer, an <code>IOException</code>
     * is thrown.
     * <p>
     * If <code>src</code> is an
     * unconnected piped writer and <code>snk</code>
     * is an unconnected piped reader, they
     * may be connected by either the call:
     *
     * <pre><code>snk.connect(src)</code> </pre>
     * <p>
     * or the call:
     *
     * <pre><code>src.connect(snk)</code> </pre>
     * <p>
     * The two calls have the same effect.
     *
     * @param      src   The piped writer to connect to.
     * @exception  IOException  if an I/O error occurs.
     */
    public void connect(PipedWriter src) throws IOException {
        src.connect(this);
    }

    /**
     * Receives a char of data. This method will block if no input is
     * available.
     */
    synchronized void receive(int c) throws IOException {
        if (!connected) {
            throw new IOException("Pipe not connected");
        } else if (closedByWriter || closedByReader) {
            throw new IOException("Pipe closed");
        } else if (readSide != null && !readSide.isAlive()) {
            throw new IOException("Read end dead");
        }

        writeSide = Thread.currentThread();
        while (in == out) {
            if ((readSide != null) && !readSide.isAlive()) {
                throw new IOException("Pipe broken");
            }
            /* full: kick any waiting readers */
            notifyAll();
            try {
                wait(1000);
            } catch (InterruptedException ex) {
                throw new java.io.InterruptedIOException();
            }
        }
        if (in < 0) {
            in = 0;
            out = 0;
        }
        buffer[in++] = (char) c;
        if (in >= buffer.length) {
            in = 0;
        }
    }

    /**
     * Receives data into an array of characters.  This method will
     * block until some input is available.
     */
    synchronized void receive(char c[], int off, int len)  throws IOException {
        while (--len >= 0) {
            receive(c[off++]);
        }
    }

    /**
     * Notifies all waiting threads that the last character of data has been
     * received.
     */
    synchronized void receivedLast() {
        closedByWriter = true;
        notifyAll();
    }

    /**
     * Reads the next character of data from this piped stream.
     * If no character is available because the end of the stream
     * has been reached, the value <code>-1</code> is returned.
     * This method blocks until input data is available, the end of
     * the stream is detected, or an exception is thrown.
     *
     * @return     the next character of data, or <code>-1</code> if the end of the
     *             stream is reached.
     * @exception  IOException  if the pipe is
     *          <a href=PipedInputStream.html#BROKEN> <code>broken</code></a>,
     *          {@link #connect(java.io.PipedWriter) unconnected}, closed,
     *          or an I/O error occurs.
     */
    public synchronized int read()  throws IOException {
        if (!connected) {
            throw new IOException("Pipe not connected");
        } else if (closedByReader) {
            throw new IOException("Pipe closed");
        } else if (writeSide != null && !writeSide.isAlive()
                   && !closedByWriter && (in < 0)) {
            throw new IOException("Write end dead");
        }

        readSide = Thread.currentThread();
        int trials = 2;
        while (in < 0) {
            if (closedByWriter) {
                /* closed by writer, return EOF */
                return -1;
            }
            if ((writeSide != null) && (!writeSide.isAlive()) && (--trials < 0)) {
                throw new IOException("Pipe broken");
            }
            /* might be a writer waiting */
            notifyAll();
            try {
                wait(1000);
            } catch (InterruptedException ex) {
                throw new java.io.InterruptedIOException();
            }
        }
        int ret = buffer[out++];
        if (out >= buffer.length) {
            out = 0;
        }
        if (in == out) {
            /* now empty */
            in = -1;
        }
        return ret;
    }

    /**
     * Reads up to <code>len</code> characters of data from this piped
     * stream into an array of characters. Less than <code>len</code> characters
     * will be read if the end of the data stream is reached or if
     * <code>len</code> exceeds the pipe's buffer size. This method
     * blocks until at least one character of input is available.
     *
     * @param      cbuf     the buffer into which the data is read.
     * @param      off   the start offset of the data.
     * @param      len   the maximum number of characters read.
     * @return     the total number of characters read into the buffer, or
     *             <code>-1</code> if there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException  if the pipe is
     *                  <a href=PipedInputStream.html#BROKEN> <code>broken</code></a>,
     *                  {@link #connect(java.io.PipedWriter) unconnected}, closed,
     *                  or an I/O error occurs.
     */
    public synchronized int read(char cbuf[], int off, int len)  throws IOException {
        if (!connected) {
            throw new IOException("Pipe not connected");
        } else if (closedByReader) {
            throw new IOException("Pipe closed");
        } else if (writeSide != null && !writeSide.isAlive()
                   && !closedByWriter && (in < 0)) {
            throw new IOException("Write end dead");
        }

        if ((off < 0) || (off > cbuf.length) || (len < 0) ||
            ((off + len) > cbuf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        /* possibly wait on the first character */
        int c = read();
        if (c < 0) {
            return -1;
        }
        cbuf[off] =  (char)c;
        int rlen = 1;
        while ((in >= 0) && (--len > 0)) {
            cbuf[off + rlen] = buffer[out++];
            rlen++;
            if (out >= buffer.length) {
                out = 0;
            }
            if (in == out) {
                /* now empty */
                in = -1;
            }
        }
        return rlen;
    }

    /**
     * Tell whether this stream is ready to be read.  A piped character
     * stream is ready if the circular buffer is not empty.
     *
     * @exception  IOException  if the pipe is
     *                  <a href=PipedInputStream.html#BROKEN> <code>broken</code></a>,
     *                  {@link #connect(java.io.PipedWriter) unconnected}, or closed.
     */
    public synchronized boolean ready() throws IOException {
        if (!connected) {
            throw new IOException("Pipe not connected");
        } else if (closedByReader) {
            throw new IOException("Pipe closed");
        } else if (writeSide != null && !writeSide.isAlive()
                   && !closedByWriter && (in < 0)) {
            throw new IOException("Write end dead");
        }
        if (in < 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Closes this piped stream and releases any system resources
     * associated with the stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    public void close()  throws IOException {
        in = -1;
        closedByReader = true;
    }
}
