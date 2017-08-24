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
 * questions.
 */

package jdk.incubator.http;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.InetSocketAddress;
import jdk.incubator.http.HttpConnection.Mode;
import java.nio.charset.StandardCharsets;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import jdk.incubator.http.internal.common.HttpHeadersImpl;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;

/**
 *  A HTTP/1.1 request.
 *
 * send() -> Writes the request + body to the given channel, in one blocking
 * operation.
 */
class Http1Request {
    final HttpClientImpl client;
    final HttpRequestImpl request;
    final HttpConnection chan;
    // Multiple buffers are used to hold different parts of request
    // See line 206 and below for description
    final ByteBuffer[] buffers;
    final HttpRequest.BodyProcessor requestProc;
    final HttpHeaders userHeaders;
    final HttpHeadersImpl systemHeaders;
    boolean streaming;
    long contentLength;
    final CompletableFuture<Void> cf;

    Http1Request(HttpRequestImpl request, HttpClientImpl client, HttpConnection connection)
        throws IOException
    {
        this.client = client;
        this.request = request;
        this.chan = connection;
        buffers = new ByteBuffer[5]; // TODO: check
        this.requestProc = request.requestProcessor;
        this.userHeaders = request.getUserHeaders();
        this.systemHeaders = request.getSystemHeaders();
        this.cf = new MinimalFuture<>();
    }

    private void logHeaders() throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("REQUEST HEADERS:\n");
        Log.dumpHeaders(sb, "    ", systemHeaders);
        Log.dumpHeaders(sb, "    ", userHeaders);
        Log.logHeaders(sb.toString());
    }

    private void dummy(long x) {
        // not used in this class
    }

    private void collectHeaders0() throws IOException {
        if (Log.headers()) {
            logHeaders();
        }
        StringBuilder sb = new StringBuilder(256);
        collectHeaders1(sb, request, systemHeaders);
        collectHeaders1(sb, request, userHeaders);
        sb.append("\r\n");
        String headers = sb.toString();
        buffers[1] = ByteBuffer.wrap(headers.getBytes(StandardCharsets.US_ASCII));
    }

    private void collectHeaders1(StringBuilder sb,
                                 HttpRequestImpl request,
                                 HttpHeaders headers)
        throws IOException
    {
        Map<String,List<String>> h = headers.map();
        Set<Map.Entry<String,List<String>>> entries = h.entrySet();

        for (Map.Entry<String,List<String>> entry : entries) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            for (String value : values) {
                sb.append(key)
                  .append(": ")
                  .append(value)
                  .append("\r\n");
            }
        }
    }

    private String getPathAndQuery(URI uri) {
        String path = uri.getPath();
        String query = uri.getQuery();
        if (path == null || path.equals("")) {
            path = "/";
        }
        if (query == null) {
            query = "";
        }
        if (query.equals("")) {
            return path;
        } else {
            return path + "?" + query;
        }
    }

    private String authorityString(InetSocketAddress addr) {
        return addr.getHostString() + ":" + addr.getPort();
    }

    private String hostString() {
        URI uri = request.uri();
        int port = uri.getPort();
        String host = uri.getHost();

        boolean defaultPort;
        if (port == -1) {
            defaultPort = true;
        } else if (request.secure()) {
            defaultPort = port == 443;
        } else {
            defaultPort = port == 80;
        }

        if (defaultPort) {
            return host;
        } else {
            return host + ":" + Integer.toString(port);
        }
    }

    private String requestURI() {
        URI uri = request.uri();
        String method = request.method();

        if ((request.proxy(client) == null && !method.equals("CONNECT"))
                || request.isWebSocket()) {
            return getPathAndQuery(uri);
        }
        if (request.secure()) {
            if (request.method().equals("CONNECT")) {
                // use authority for connect itself
                return authorityString(request.authority());
            } else {
                // requests over tunnel do not require full URL
                return getPathAndQuery(uri);
            }
        }
        return uri == null? authorityString(request.authority()) : uri.toString();
    }

    void sendHeadersOnly() throws IOException {
        collectHeaders();
        chan.write(buffers, 0, 2);
    }

    void sendRequest() throws IOException {
        collectHeaders();
        chan.configureMode(Mode.BLOCKING);
        if (contentLength == 0) {
            chan.write(buffers, 0, 2);
        } else if (contentLength > 0) {
            writeFixedContent(true);
        } else {
            writeStreamedContent(true);
        }
        setFinished();
    }

    private boolean finished;

    synchronized boolean finished() {
        return  finished;
    }

    synchronized void setFinished() {
        finished = true;
    }

    private void collectHeaders() throws IOException {
        if (Log.requests() && request != null) {
            Log.logRequest(request.toString());
        }
        String uriString = requestURI();
        StringBuilder sb = new StringBuilder(64);
        sb.append(request.method())
          .append(' ')
          .append(uriString)
          .append(" HTTP/1.1\r\n");
        String cmd = sb.toString();

        buffers[0] = ByteBuffer.wrap(cmd.getBytes(StandardCharsets.US_ASCII));
        URI uri = request.uri();
        if (uri != null) {
            systemHeaders.setHeader("Host", hostString());
        }
        if (request == null) {
            // this is not a user request. No content
            contentLength = 0;
        } else {
            contentLength = requestProc.contentLength();
        }

        if (contentLength == 0) {
            systemHeaders.setHeader("Content-Length", "0");
            collectHeaders0();
        } else if (contentLength > 0) {
            /* [0] request line [1] headers [2] body  */
            systemHeaders.setHeader("Content-Length",
                                    Integer.toString((int) contentLength));
            streaming = false;
            collectHeaders0();
            buffers[2] = getBuffer();
        } else {
            /* Chunked:
             *
             * [0] request line [1] headers [2] chunk header [3] chunk data [4]
             * final chunk header and trailing CRLF of previous chunks
             *
             * 2,3,4 used repeatedly */
            streaming = true;
            systemHeaders.setHeader("Transfer-encoding", "chunked");
            collectHeaders0();
            buffers[3] = getBuffer();
        }
    }

    private ByteBuffer getBuffer() {
        return ByteBuffer.allocate(Utils.BUFSIZE);
    }

    // The following two methods used by Http1Exchange to handle expect continue

    void continueRequest() throws IOException {
        if (streaming) {
            writeStreamedContent(false);
        } else {
            writeFixedContent(false);
        }
        setFinished();
    }

    class StreamSubscriber implements Flow.Subscriber<ByteBuffer> {
        volatile Flow.Subscription subscription;
        volatile boolean includeHeaders;

        StreamSubscriber(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                throw new IllegalStateException("already subscribed");
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            int startbuf, nbufs;

            if (cf.isDone()) {
                throw new IllegalStateException("subscription already completed");
            }

            if (includeHeaders) {
                startbuf = 0;
                nbufs = 5;
            } else {
                startbuf = 2;
                nbufs = 3;
            }
            int chunklen = item.remaining();
            buffers[3] = item;
            buffers[2] = getHeader(chunklen);
            buffers[4] = CRLF_BUFFER();
            try {
                chan.write(buffers, startbuf, nbufs);
            } catch (IOException e) {
                subscription.cancel();
                cf.completeExceptionally(e);
            }
            includeHeaders = false;
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            if (cf.isDone()) {
                return;
            }
            subscription.cancel();
            cf.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (cf.isDone()) {
                throw new IllegalStateException("subscription already completed");
            }
            buffers[3] = EMPTY_CHUNK_HEADER();
            buffers[4] = CRLF_BUFFER();
            try {
                chan.write(buffers, 3, 2);
            } catch (IOException ex) {
                cf.completeExceptionally(ex);
                return;
            }
            cf.complete(null);
        }
    }

    private void waitForCompletion() throws IOException {
        try {
            cf.join();
        } catch (CompletionException e) {
            throw Utils.getIOException(e);
        }
    }

    /* Entire request is sent, or just body only  */
    private void writeStreamedContent(boolean includeHeaders)
        throws IOException
    {
        StreamSubscriber subscriber = new StreamSubscriber(includeHeaders);
        requestProc.subscribe(subscriber);
        waitForCompletion();
    }

    class FixedContentSubscriber implements Flow.Subscriber<ByteBuffer>
    {
        volatile Flow.Subscription subscription;
        volatile boolean includeHeaders;
        volatile long contentWritten = 0;

        FixedContentSubscriber(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                throw new IllegalStateException("already subscribed");
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            int startbuf, nbufs;
            long headersLength;

            if (includeHeaders) {
                startbuf = 0;
                nbufs = 3;
                headersLength = buffers[0].remaining() + buffers[1].remaining();
            } else {
                startbuf = 2;
                nbufs = 1;
                headersLength = 0;
            }
            buffers[2] = item;
            try {
                long writing = buffers[2].remaining() + headersLength;
                contentWritten += buffers[2].remaining();
                chan.checkWrite(writing, buffers, startbuf, nbufs);

                if (contentWritten > contentLength) {
                    String msg = "Too many bytes in request body. Expected: " +
                        Long.toString(contentLength) + " Sent: " +
                        Long.toString(contentWritten);
                    throw new IOException(msg);
                }
                subscription.request(1);
            } catch (IOException e) {
                subscription.cancel();
                cf.completeExceptionally(e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (cf.isDone()) {
                return;
            }
            subscription.cancel();
            cf.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (cf.isDone()) {
                throw new IllegalStateException("subscription already completed");
            }

            if (contentLength > contentWritten) {
                subscription.cancel();
                Exception e = new IOException("Too few bytes returned by the processor");
                cf.completeExceptionally(e);
            } else {
                cf.complete(null);
            }
        }
    }

    /* Entire request is sent, or just body only */
    private void writeFixedContent(boolean includeHeaders)
            throws IOException {
        if (contentLength == 0) {
            return;
        }
        FixedContentSubscriber subscriber = new FixedContentSubscriber(includeHeaders);
        requestProc.subscribe(subscriber);
        waitForCompletion();
    }

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] EMPTY_CHUNK_BYTES = {'0', '\r', '\n'};

    private ByteBuffer CRLF_BUFFER() {
        return ByteBuffer.wrap(CRLF);
    }

    private ByteBuffer EMPTY_CHUNK_HEADER() {
        return ByteBuffer.wrap(EMPTY_CHUNK_BYTES);
    }

    /* Returns a header for a particular chunk size */
    private static ByteBuffer getHeader(int size){
        String hexStr =  Integer.toHexString(size);
        byte[] hexBytes = hexStr.getBytes(US_ASCII);
        byte[] header = new byte[hexStr.length()+2];
        System.arraycopy(hexBytes, 0, header, 0, hexBytes.length);
        header[hexBytes.length] = CRLF[0];
        header[hexBytes.length+1] = CRLF[1];
        return ByteBuffer.wrap(header);
    }
}
