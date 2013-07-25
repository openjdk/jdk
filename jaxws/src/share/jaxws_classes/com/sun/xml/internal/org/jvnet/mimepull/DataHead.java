/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Represents an attachment part in a MIME message. MIME message parsing is done
 * lazily using a pull parser, so the part may not have all the data. {@link #read}
 * and {@link #readOnce} may trigger the actual parsing the message. In fact,
 * parsing of an attachment part may be triggered by calling {@link #read} methods
 * on some other attachment parts. All this happens behind the scenes so the
 * application developer need not worry about these details.
 *
 * @author Jitendra Kotamraju
 */
final class DataHead {

    /**
     * Linked list to keep the part's content
     */
    volatile Chunk head, tail;

    /**
     * If the part is stored in a file, non-null.
     */
    DataFile dataFile;

    private final MIMEPart part;

    boolean readOnce;
    volatile long inMemory;

    /**
     * Used only for debugging. This records where readOnce() is called.
     */
    private Throwable consumedAt;

    DataHead(MIMEPart part) {
        this.part = part;
    }

    void addBody(ByteBuffer buf) {
        synchronized(this) {
            inMemory += buf.limit();
        }
        if (tail != null) {
            tail = tail.createNext(this, buf);
        } else {
            head = tail = new Chunk(new MemoryData(buf, part.msg.config));
        }
    }

    void doneParsing() {
    }

    void moveTo(File f) {
        if (dataFile != null) {
            dataFile.renameTo(f);
        } else {
            try {
                OutputStream os = new FileOutputStream(f);
                try {
                    InputStream in = readOnce();
                    byte[] buf = new byte[8192];
                    int len;
                    while((len=in.read(buf)) != -1) {
                        os.write(buf, 0, len);
                    }
                } finally {
                    if (os != null) {
                        os.close();
                    }
                }
            } catch(IOException ioe) {
                throw new MIMEParsingException(ioe);
            }
        }
    }

    void close() {
        head = tail = null;
        if (dataFile != null) {
            dataFile.close();
        }
    }


    /**
     * Can get the attachment part's content multiple times. That means
     * the full content needs to be there in memory or on the file system.
     * Calling this method would trigger parsing for the part's data. So
     * do not call this unless it is required(otherwise, just wrap MIMEPart
     * into a object that returns InputStream for e.g DataHandler)
     *
     * @return data for the part's content
     */
    public InputStream read() {
        if (readOnce) {
            throw new IllegalStateException("readOnce() is called before, read() cannot be called later.");
        }

        // Trigger parsing for the part
        while(tail == null) {
            if (!part.msg.makeProgress()) {
                throw new IllegalStateException("No such MIME Part: "+part);
            }
        }

        if (head == null) {
            throw new IllegalStateException("Already read. Probably readOnce() is called before.");
        }
        return new ReadMultiStream();
    }

    /**
     * Used for an assertion. Returns true when readOnce() is not already called.
     * or otherwise throw an exception.
     *
     * <p>
     * Calling this method also marks the stream as 'consumed'
     *
     * @return true if readOnce() is not called before
     */
    @SuppressWarnings("ThrowableInitCause")
    private boolean unconsumed() {
        if (consumedAt != null) {
            AssertionError error = new AssertionError("readOnce() is already called before. See the nested exception from where it's called.");
            error.initCause(consumedAt);
            throw error;
        }
        consumedAt = new Exception().fillInStackTrace();
        return true;
    }

    /**
     * Can get the attachment part's content only once. The content
     * will be lost after the method. Content data is not be stored
     * on the file system or is not kept in the memory for the
     * following case:
     *   - Attachement parts contents are accessed sequentially
     *
     * In general, take advantage of this when the data is used only
     * once.
     *
     * @return data for the part's content
     */
    public InputStream readOnce() {
        assert unconsumed();
        if (readOnce) {
            throw new IllegalStateException("readOnce() is called before. It can only be called once.");
        }
        readOnce = true;
        // Trigger parsing for the part
        while(tail == null) {
            if (!part.msg.makeProgress() && tail == null) {
                throw new IllegalStateException("No such Part: "+part);
            }
        }
        InputStream in = new ReadOnceStream();
        head = null;
        return in;
    }

    class ReadMultiStream extends InputStream {
        Chunk current;
        int offset;
        int len;
        byte[] buf;
        boolean closed;

        public ReadMultiStream() {
            this.current = head;
            len = current.data.size();
            buf = current.data.read();
        }

        @Override
        public int read(byte b[], int off, int sz) throws IOException {
            if (!fetch()) {
                return -1;
            }

            sz = Math.min(sz, len-offset);
            System.arraycopy(buf,offset,b,off,sz);
            offset += sz;
            return sz;
        }

        @Override
        public int read() throws IOException {
            if (!fetch()) {
                return -1;
            }
            return (buf[offset++] & 0xff);
        }

        void adjustInMemoryUsage() {
            // Nothing to do in this case.
        }

        /**
         * Gets to the next chunk if we are done with the current one.
         * @return true if any data available
         * @throws IOException when i/o error
         */
        private boolean fetch() throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            if (current == null) {
                return false;
            }

            while(offset==len) {
                while(!part.parsed && current.next == null) {
                    part.msg.makeProgress();
                }
                current = current.next;

                if (current == null) {
                    return false;
                }
                adjustInMemoryUsage();
                this.offset = 0;
                this.buf = current.data.read();
                this.len = current.data.size();
            }
            return true;
        }

        @Override
        public void close() throws IOException {
            super.close();
            current = null;
            closed = true;
        }
    }

    final class ReadOnceStream extends ReadMultiStream {

        @Override
        void adjustInMemoryUsage() {
            synchronized(DataHead.this) {
                inMemory -= current.data.size();    // adjust current memory usage
            }
        }

    }


}
