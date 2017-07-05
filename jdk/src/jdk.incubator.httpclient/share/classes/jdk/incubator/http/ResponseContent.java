/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import jdk.incubator.http.internal.common.Utils;

/**
 * Implements chunked/fixed transfer encodings of HTTP/1.1 responses.
 *
 * Call pushBody() to read the body (blocking). Data and errors are provided
 * to given Consumers. After final buffer delivered, empty optional delivered
 */
class ResponseContent {

    final HttpResponse.BodyProcessor<?> pusher;
    final HttpConnection connection;
    final int contentLength;
    ByteBuffer buffer;
    //ByteBuffer lastBufferUsed;
    final ResponseHeaders headers;
    private final Consumer<Optional<ByteBuffer>> dataConsumer;
    private final Consumer<IOException> errorConsumer;
    private final HttpClientImpl client;
    // this needs to run before we complete the body
    // so that connection can be returned to pool
    private final Runnable onFinished;

    ResponseContent(HttpConnection connection,
                    int contentLength,
                    ResponseHeaders h,
                    HttpResponse.BodyProcessor<?> userProcessor,
                    Consumer<Optional<ByteBuffer>> dataConsumer,
                    Consumer<IOException> errorConsumer,
                    Runnable onFinished)
    {
        this.pusher = (HttpResponse.BodyProcessor)userProcessor;
        this.connection = connection;
        this.contentLength = contentLength;
        this.headers = h;
        this.dataConsumer = dataConsumer;
        this.errorConsumer = errorConsumer;
        this.client = connection.client;
        this.onFinished = onFinished;
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
    void pushBody(ByteBuffer b) {
        try {
            // TODO: check status
            if (contentChunked()) {
                pushBodyChunked(b);
            } else {
                pushBodyFixed(b);
            }
        } catch (IOException t) {
            errorConsumer.accept(t);
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
        if (chunkbuf == null || !chunkbuf.hasRemaining()) {
            chunkbuf = connection.read();
        }
    }

    private void consumeBytes(int n) throws IOException {
        getHunk();
        while (n > 0) {
            int e = Math.min(chunkbuf.remaining(), n);
            chunkbuf.position(chunkbuf.position() + e);
            n -= e;
            if (n > 0) {
                getHunk();
            }
        }
    }

    /**
     * Returns a ByteBuffer containing a chunk of data or a "hunk" of data
     * (a chunk of a chunk if the chunk size is larger than our ByteBuffers).
     * ByteBuffer returned is obtained from response processor.
     */
    ByteBuffer readChunkedBuffer() throws IOException {
        if (chunklen == -1) {
            // new chunk
            chunklen = readChunkLen() - 2;
            bytesremaining =  chunklen;
            if (chunklen == 0) {
                consumeBytes(2);
                return null;
            }
        }

        getHunk();
        bytesread = chunkbuf.remaining();
        ByteBuffer returnBuffer = Utils.getBuffer();
        int space = returnBuffer.remaining();

        int bytes2Copy = Math.min(bytesread, Math.min(bytesremaining, space));
        Utils.copy(chunkbuf, returnBuffer, bytes2Copy);
        returnBuffer.flip();
        bytesremaining -= bytes2Copy;
        if (bytesremaining == 0) {
            consumeBytes(2);
            chunklen = -1;
        }
        return returnBuffer;
    }

    ByteBuffer initialBuffer;
    int fixedBytesReturned;

    //ByteBuffer getResidue() {
        //return lastBufferUsed;
    //}

    private void compactBuffer(ByteBuffer buf) {
        buf.compact()
           .flip();
    }

    /**
     * Copies inbuf (numBytes from its position) to new buffer. The returned
     * buffer's position is zero and limit is at end (numBytes)
     */
    private ByteBuffer copyBuffer(ByteBuffer inbuf, int numBytes) {
        ByteBuffer b1 = Utils.getBuffer();
        assert b1.remaining() >= numBytes;
        byte[] b = b1.array();
        inbuf.get(b, 0, numBytes);
        b1.limit(numBytes);
        return b1;
    }

    private void pushBodyChunked(ByteBuffer b) throws IOException {
        chunkbuf = b;
        while (true) {
            ByteBuffer b1 = readChunkedBuffer();
            if (b1 != null) {
                if (b1.hasRemaining()) {
                    dataConsumer.accept(Optional.of(b1));
                }
            } else {
                onFinished.run();
                dataConsumer.accept(Optional.empty());
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

    private void pushBodyFixed(ByteBuffer b) throws IOException {
        int remaining = contentLength;
        while (b.hasRemaining() && remaining > 0) {
            ByteBuffer buffer = Utils.getBuffer();
            int amount = Math.min(b.remaining(), remaining);
            Utils.copy(b, buffer, amount);
            remaining -= amount;
            buffer.flip();
            dataConsumer.accept(Optional.of(buffer));
        }
        while (remaining > 0) {
            ByteBuffer buffer = connection.read();
            if (buffer == null)
                throw new IOException("connection closed");

            int bytesread = buffer.remaining();
            // assume for now that pipelining not implemented
            if (bytesread > remaining) {
                throw new IOException("too many bytes read");
            }
            remaining -= bytesread;
            dataConsumer.accept(Optional.of(buffer));
        }
        onFinished.run();
        dataConsumer.accept(Optional.empty());
    }
}
