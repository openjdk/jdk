/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.testng.annotations.Test;
import jdk.incubator.http.internal.common.ByteBufferReference;

@Test
public class ResponseHeadersTest {

    static final String BODY =
          "This is the body dude,\r\n"
        + "not a header!\r\n";

    static final String MESSAGE_OK =
          "HTTP/1.1 200 OK\r\n"
        + "Content-Length: " + BODY.length() + "\r\n"
        + "MY-Folding-Header: YES\r\n"
        + " OR\r\n"
        + " NO\r\n"
        + "\r\n"
        + BODY;

    static final String MESSAGE_NOK =
          "HTTP/1.1 101 Switching Protocols\r\n"
        + "\r\n";

    //public static void main(String[] args) throws IOException {
    //    new  ResponseHeadersTest().test();
    //}

    @Test
    public void test() throws IOException {
        testResponseHeaders(MESSAGE_OK);
        testResponseHeaders(MESSAGE_NOK);
    }

    /**
     * Verifies that ResponseHeaders behave as we expect.
     * @param msg The response string.
     * @throws IOException should not happen.
     */
    static void testResponseHeaders(String msg) throws IOException {
        byte[] bytes = msg.getBytes("US-ASCII");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Read status line
        String statusLine = readStatusLine(buffer);
        System.out.println("StatusLine: " + statusLine);
        if (!statusLine.startsWith("HTTP/1.1")) {
            throw new AssertionError("bad status line: " + statusLine);
        }

        // We have two cases:
        //    - MESSAGE_OK: there will be some headers to read,
        //    - MESSAGE_NOK: there will be no headers to read.
        HttpHeaders headers = createResponseHeaders(buffer);

        // Now get the expected length of the body
        Optional<String> contentLengthValue = headers.firstValue("Content-length");
        int contentLength = contentLengthValue.map(Integer::parseInt).orElse(0);

        // We again have two cases:
        //    - MESSAGE_OK:  there should be a Content-length: header
        //    - MESSAGE_NOK: there should be no Content-length: header
        if (contentLengthValue.isPresent()) {
            // MESSAGE_NOK has no headers and no body and therefore
            // no Content-length: header.
            if (MESSAGE_NOK.equals(msg)) {
                throw new AssertionError("Content-length: header found in"
                          + " error 101 message");
            }
        } else {
            if (!MESSAGE_NOK.equals(msg)) {
                throw new AssertionError("Content-length: header not found");
            }
        }

        // Now read the remaining bytes. It should either be
        // the empty string (MESSAGE_NOK) or BODY (MESSAGE_OK),
        // and it should not contains any leading CR or LF"
        String remaining = readRemainingBytes(buffer);
        System.out.println("Body: <<<" + remaining + ">>>");
        if (remaining.length() != contentLength) {
            throw new AssertionError("Unexpected body length: " + remaining.length()
                     + " expected " + contentLengthValue);
        }
        if (contentLengthValue.isPresent()) {
            if (!BODY.equals(remaining)) {
                throw new AssertionError("Body does not match!");
            }
        }
    }

    static String readRemainingBytes(ByteBuffer buffer) throws UnsupportedEncodingException {
        byte[] res = new byte[buffer.limit() - buffer.position()];
        System.arraycopy(buffer.array(), buffer.position(), res, 0, res.length);
        buffer.position(buffer.limit());
        return new String(res, "US-ASCII");
    }

    static String readStatusLine(ByteBuffer buffer) throws IOException {
        buffer.mark();
        int p = buffer.position();
        while(buffer.hasRemaining()) {
            char c = (char)buffer.get();
            if (c == '\r') {
                c = (char)buffer.get();
                if (c == '\n') {
                    byte[] res = new byte[buffer.position() - p -2];
                    System.arraycopy(buffer.array(), p, res, 0, res.length);
                    return new String(res, "US-ASCII");
                }
            }
        }
        throw new IOException("Status line not found");
    }

    private static final class HttpConnectionStub extends HttpConnection {
        public HttpConnectionStub() {
            super(null, null);
        }
        @Override
        public void connect() throws IOException, InterruptedException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        public CompletableFuture<Void> connectAsync() {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        boolean connected() {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        boolean isSecure() {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        boolean isProxied() {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        CompletableFuture<Void> whenReceivingResponse() {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        SocketChannel channel() {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        ConnectionPool.CacheKey cacheKey() {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        long write(ByteBuffer[] buffers, int start, int number) throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        long write(ByteBuffer buffer) throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        void writeAsync(ByteBufferReference[] buffers) throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        void writeAsyncUnordered(ByteBufferReference[] buffers) throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        void flushAsync() throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        public void close() {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        void shutdownInput() throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        void shutdownOutput() throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        protected ByteBuffer readImpl() throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
        @Override
        protected int readImpl(ByteBuffer buffer) throws IOException {
            throw new AssertionError("Bad test assumption: should not have reached here!");
        }
    }

    public static HttpHeaders createResponseHeaders(ByteBuffer buffer)
        throws IOException{
        return new ResponseHeaders(new HttpConnectionStub(), buffer);
    }
}
