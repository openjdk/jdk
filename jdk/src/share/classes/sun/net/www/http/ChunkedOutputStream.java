/*
 * Copyright 2004-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.net.www.http;

import java.io.*;

/**
 * OutputStream that sends the output to the underlying stream using chunked
 * encoding as specified in RFC 2068.
 *
 * @author  Alan Bateman
 */
public class ChunkedOutputStream extends PrintStream {

    /* Default chunk size (including chunk header) if not specified */
    static final int DEFAULT_CHUNK_SIZE = 4096;

    /* internal buffer */
    private byte buf[];
    private int count;

    /* underlying stream */
    private PrintStream out;

    /* the chunk size we use */
    private int preferredChunkSize;

    /* if the users write buffer is bigger than this size, we
     * write direct from the users buffer instead of copying
     */
    static final int MAX_BUF_SIZE = 10 * 1024;

    /* return the size of the header for a particular chunk size */
    private int headerSize(int size) {
        return 2 + (Integer.toHexString(size)).length();
    }

    public ChunkedOutputStream(PrintStream o) {
        this(o, DEFAULT_CHUNK_SIZE);
    }

    public ChunkedOutputStream(PrintStream o, int size) {
        super(o);

        out = o;

        if (size <= 0) {
            size = DEFAULT_CHUNK_SIZE;
        }
        /* Adjust the size to cater for the chunk header - eg: if the
         * preferred chunk size is 1k this means the chunk size should
         * be 1019 bytes (differs by 5 from preferred size because of
         * 3 bytes for chunk size in hex and CRLF).
         */
        if (size > 0) {
            int adjusted_size = size - headerSize(size);
            if (adjusted_size + headerSize(adjusted_size) < size) {
                adjusted_size++;
            }
            size = adjusted_size;
        }

        if (size > 0) {
            preferredChunkSize = size;
        } else {
            preferredChunkSize = DEFAULT_CHUNK_SIZE - headerSize(DEFAULT_CHUNK_SIZE);
        }

        /* start with an initial buffer */
        buf = new byte[preferredChunkSize + 32];
    }

    /*
     * If flushAll is true, then all data is flushed in one chunk.
     *
     * If false and the size of the buffer data exceeds the preferred
     * chunk size then chunks are flushed to the output stream.
     * If there isn't enough data to make up a complete chunk,
     * then the method returns.
     */
    private void flush(byte[] buf, boolean flushAll) {
        flush (buf, flushAll, 0);
    }

    private void flush(byte[] buf, boolean flushAll, int offset) {
        int chunkSize;

        do {
            if (count < preferredChunkSize) {
                if (!flushAll) {
                    break;
                }
                chunkSize = count;
            } else {
                chunkSize = preferredChunkSize;
            }

            byte[] bytes = null;
            try {
                bytes = (Integer.toHexString(chunkSize)).getBytes("US-ASCII");
            } catch (java.io.UnsupportedEncodingException e) {
                //This should never happen.
                throw new InternalError(e.getMessage());
            }

            out.write(bytes, 0, bytes.length);
            out.write((byte)'\r');
            out.write((byte)'\n');
            if (chunkSize > 0) {
                out.write(buf, offset, chunkSize);
                out.write((byte)'\r');
                out.write((byte)'\n');
            }
            out.flush();
            if (checkError()) {
                break;
            }
            if (chunkSize > 0) {
                count -= chunkSize;
                offset += chunkSize;
            }
        } while (count > 0);

        if (!checkError() && count > 0) {
            System.arraycopy(buf, offset, this.buf, 0, count);
        }
    }

    public boolean checkError() {
        return out.checkError();
    }

    /*
     * Check if we have enough data for a chunk and if so flush to the
     * underlying output stream.
     */
    private void checkFlush() {
        if (count >= preferredChunkSize) {
            flush(buf, false);
        }
    }

    /* Check that the output stream is still open */
    private void ensureOpen() {
        if (out == null)
            setError();
    }

    public synchronized void write(byte b[], int off, int len) {
        ensureOpen();
        if ((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        if (len > MAX_BUF_SIZE) {
            /* first finish the current chunk */
            int l = preferredChunkSize - count;
            if (l > 0) {
                System.arraycopy(b, off, buf, count, l);
                count = preferredChunkSize;
                flush(buf, false);
            }
            count = len - l;
            /* Now write the rest of the data */
            flush (b, false, l+off);
        } else {
            int newcount = count + len;

            if (newcount > buf.length) {
                byte newbuf[] = new byte[Math.max(buf.length << 1, newcount)];
                System.arraycopy(buf, 0, newbuf, 0, count);
                buf = newbuf;
            }
            System.arraycopy(b, off, buf, count, len);
            count = newcount;
            checkFlush();
        }
    }

    public synchronized void write(int b) {
        ensureOpen();
        int newcount = count + 1;
        if (newcount > buf.length) {
            byte newbuf[] = new byte[Math.max(buf.length << 1, newcount)];
            System.arraycopy(buf, 0, newbuf, 0, count);
            buf = newbuf;
        }
        buf[count] = (byte)b;
        count = newcount;
        checkFlush();
    }

    public synchronized void reset() {
        count = 0;
    }

    public int size() {
        return count;
    }

    public synchronized void close() {
        ensureOpen();

        /* if we have buffer a chunked send it */
        if (count > 0) {
            flush(buf, true);
        }

        /* send a zero length chunk */
        flush(buf, true);

        /* don't close the underlying stream */
        out = null;
    }

    public synchronized void flush() {
        ensureOpen();
        if (count > 0) {
            flush(buf, true);
        }
    }

}
