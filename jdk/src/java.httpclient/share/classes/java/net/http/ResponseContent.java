/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */
package java.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Implements chunked/fixed transfer encodings of HTTP/1.1 responses.
 */
class ResponseContent {

    final HttpResponse.BodyProcessor<?> userProcessor;
    final HttpResponse.BodyProcessor<?> pusher;
    final HttpConnection connection;
    final int contentLength;
    ByteBuffer buffer;
    ByteBuffer lastBufferUsed;
    final ResponseHeaders headers;
    final Http1Response.FlowController flowController;

    ResponseContent(HttpConnection connection,
                    int contentLength,
                    ResponseHeaders h,
                    HttpResponse.BodyProcessor<?> userProcessor,
                    Http1Response.FlowController flowController) {
        this.userProcessor = userProcessor;
        this.pusher = (HttpResponse.BodyProcessor)userProcessor;
        this.connection = connection;
        this.contentLength = contentLength;
        this.headers = h;
        this.flowController = flowController;
    }

    static final int LF = 10;
    static final int CR = 13;
    static final int SP = 0x20;
    static final int BUF_SIZE = 1024;

    boolean chunkedContent, chunkedContentInitialized;

    private boolean contentChunked() throws IOException {
        if (chunkedContentInitialized) {
            return chunkedContent;
        }
        if (contentLength == -1) {
            String tc = headers.firstValue("Transfer-Encoding")
                               .orElse("");
            if (!tc.equals("")) {
                if (tc.equalsIgnoreCase("chunked")) {
                    chunkedContent = true;
                } else {
                    throw new IOException("invalid content");
                }
            } else {
                chunkedContent = false;
            }
        }
        chunkedContentInitialized = true;
        return chunkedContent;
    }

    /**
     * Entry point for pusher. b is an initial ByteBuffer that may
     * have some data in it. When this method returns, the body
     * has been fully processed.
     */
    void pushBody(ByteBuffer b) throws IOException {
        // TODO: check status
        if (contentChunked()) {
            pushBodyChunked(b);
        } else {
            pushBodyFixed(b);
        }
    }

    // reads and returns chunklen. Position of chunkbuf is first byte
    // of chunk on return. chunklen includes the CR LF at end of chunk
    int readChunkLen() throws IOException {
        chunklen = 0;
        boolean cr = false;
        while (true) {
            getHunk();
            int c = chunkbuf.get();
            if (cr) {
                if (c == LF) {
                    return chunklen + 2;
                } else {
                    throw new IOException("invalid chunk header");
                }
            }
            if (c == CR) {
                cr = true;
            } else {
                int digit = toDigit(c);
                chunklen = chunklen * 16 + digit;
            }
        }
    }

    int chunklen = -1;      // number of bytes in chunk (fixed)
    int bytesremaining;     // number of bytes in chunk left to be read incl CRLF
    int bytesread;
    ByteBuffer chunkbuf;    // initialise

    // make sure we have at least 1 byte to look at
    private void getHunk() throws IOException {
        while (chunkbuf == null || !chunkbuf.hasRemaining()) {

            if (chunkbuf != null) {
                connection.returnBuffer(chunkbuf);
            }
            chunkbuf = connection.read();
        }
    }

    private void consumeBytes(int n) throws IOException {
        getHunk();
        while (n > 0) {
            int e = Math.min(chunkbuf.remaining(), n);
            chunkbuf.position(chunkbuf.position() + e);
            n -= e;
            if (n > 0)
                getHunk();
        }
    }

    /**
     * Returns a ByteBuffer containing a chunk of data or a "hunk" of data
     * (a chunk of a chunk if the chunk size is larger than our ByteBuffers).
     */
    ByteBuffer readChunkedBuffer() throws IOException {
        if (chunklen == -1) {
            // new chunk
            bytesremaining = readChunkLen();
            chunklen = bytesremaining - 2;
            if (chunklen == 0) {
                consumeBytes(2);
                return null;
            }
        }

        getHunk();
        bytesread = chunkbuf.remaining();
        ByteBuffer returnBuffer;

        /**
         * Cases. Always at least one byte is read by getHunk()
         *
         * 1) one read contains exactly 1 chunk. Strip off CRLF and pass buffer on
         * 2) one read contains a hunk. If at end of chunk, consume CRLF.Pass buffer up.
         * 3) read contains rest of chunk and more data. Copy buffer.
         */
        if (bytesread == bytesremaining) {
            // common case: 1 read = 1 chunk (or final hunk of chunk)
            chunkbuf.limit(chunkbuf.limit() - 2); // remove trailing CRLF
            bytesremaining = 0;
            returnBuffer = chunkbuf;
            chunkbuf = null;
            chunklen = -1;
        } else if (bytesread < bytesremaining) {
            // read a hunk, maybe including CR or LF or both
            bytesremaining -= bytesread;
            if (bytesremaining <= 2) {
                // remove any trailing CR LF already read, and then read the rest
                chunkbuf.limit(chunkbuf.limit() - (2 - bytesremaining));
                consumeBytes(bytesremaining);
                chunklen = -1;
            }
            returnBuffer = chunkbuf;
            chunkbuf = null;
        } else {
            // bytesread > bytesremaining
            returnBuffer = splitChunkedBuffer(bytesremaining-2);
            bytesremaining = 0;
            chunklen = -1;
            consumeBytes(2);
        }
        return returnBuffer;
    }

    ByteBuffer initialBuffer;
    int fixedBytesReturned;

    ByteBuffer getResidue() {
        return lastBufferUsed;
    }

    private void compactBuffer(ByteBuffer buf) {
        buf.compact()
           .flip();
    }

    /**
     * Copies inbuf (numBytes from its position) to new buffer. The returned
     * buffer's position is zero and limit is at end (numBytes)
     */
    private ByteBuffer copyBuffer(ByteBuffer inbuf, int numBytes) {
        ByteBuffer b1 = connection.getBuffer();
        assert b1.remaining() >= numBytes;
        byte[] b = b1.array();
        inbuf.get(b, 0, numBytes);
        b1.limit(numBytes);
        return b1;
    }

    /**
     * Split numBytes of data out of chunkbuf from the remainder,
     * copying whichever part is smaller. chunkbuf points to second part
     * of buffer on return. The returned buffer is the data from position
     * to position + numBytes. Both buffers positions are reset so same
     * data can be re-read.
     */
    private ByteBuffer splitChunkedBuffer(int numBytes) {
        ByteBuffer newbuf = connection.getBuffer();
        byte[] b = newbuf.array();
        int midpoint = chunkbuf.position() + numBytes;
        int remainder = chunkbuf.limit() - midpoint;

        if (numBytes < remainder) {
            // copy first part of chunkbuf to new buf
            chunkbuf.get(b, 0, numBytes);
            newbuf.limit(numBytes);
            return newbuf;
        } else {
            // copy remainder of chunkbuf to newbuf and make newbuf chunkbuf
            chunkbuf.mark();
            chunkbuf.position(midpoint);
            chunkbuf.get(b, 0, remainder);
            chunkbuf.reset();
            chunkbuf.limit(midpoint);
            newbuf.limit(remainder);
            newbuf.position(0);
            ByteBuffer tmp = chunkbuf;
            chunkbuf = newbuf;
            return tmp;
        }
    }

    private void pushBodyChunked(ByteBuffer b) throws IOException {
        chunkbuf = b;
        while (true) {
            ByteBuffer b1 = readChunkedBuffer();
            if (b1 != null) {
                if (b1.hasRemaining()) {
                    request(1); // wait till we can send
                    pusher.onResponseBodyChunk(b1);
                    lastBufferUsed = b1;
                }
            } else {
                return;
            }
        }
    }

    private int toDigit(int b) throws IOException {
        if (b >= 0x30 && b <= 0x39) {
            return b - 0x30;
        }
        if (b >= 0x41 && b <= 0x46) {
            return b - 0x41 + 10;
        }
        if (b >= 0x61 && b <= 0x66) {
            return b - 0x61 + 10;
        }
        throw new IOException("Invalid chunk header byte " + b);
    }

    private void request(long value) throws IOException {
        try {
            flowController.request(value);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private void pushBodyFixed(ByteBuffer b) throws IOException {
        lastBufferUsed = b;
        for (int remaining = contentLength; remaining > 0;) {
            int bufsize = b.remaining();
            if (bufsize > remaining) {
                // more data available than required, must copy
                lastBufferUsed = b;
                b = copyBuffer(b, remaining);
                remaining = 0;
            } else {
                // pass entire buffer up to user
                remaining -= bufsize;
                compactBuffer(b);
            }
            request(1); // wait till we can send
            pusher.onResponseBodyChunk(b);
            if (remaining > 0) {
                b = connection.read();
                if (b == null) {
                    throw new IOException("Error reading response");
                }
                lastBufferUsed = b;
            }
        }
    }
}
