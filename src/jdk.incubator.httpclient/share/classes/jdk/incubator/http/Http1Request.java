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
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.Flow;

import jdk.incubator.http.Http1Exchange.Http1BodySubscriber;
import jdk.incubator.http.internal.common.HttpHeadersImpl;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.Utils;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 *  An HTTP/1.1 request.
 */
class Http1Request {
    private final HttpRequestImpl request;
    private final Http1Exchange<?> http1Exchange;
    private final HttpConnection connection;
    private final HttpRequest.BodyPublisher requestPublisher;
    private final HttpHeaders userHeaders;
    private final HttpHeadersImpl systemHeaders;
    private volatile boolean streaming;
    private volatile long contentLength;

    Http1Request(HttpRequestImpl request,
                 Http1Exchange<?> http1Exchange)
        throws IOException
    {
        this.request = request;
        this.http1Exchange = http1Exchange;
        this.connection = http1Exchange.connection();
        this.requestPublisher = request.requestPublisher;  // may be null
        this.userHeaders = request.getUserHeaders();
        this.systemHeaders = request.getSystemHeaders();
    }

    private void logHeaders(String completeHeaders) {
        if (Log.headers()) {
            //StringBuilder sb = new StringBuilder(256);
            //sb.append("REQUEST HEADERS:\n");
            //Log.dumpHeaders(sb, "    ", systemHeaders);
            //Log.dumpHeaders(sb, "    ", userHeaders);
            //Log.logHeaders(sb.toString());

            String s = completeHeaders.replaceAll("\r\n", "\n");
            Log.logHeaders("REQUEST HEADERS:\n" + s);
        }
    }

    private void collectHeaders0(StringBuilder sb) {
        collectHeaders1(sb, systemHeaders);
        collectHeaders1(sb, userHeaders);
        sb.append("\r\n");
    }

    private void collectHeaders1(StringBuilder sb, HttpHeaders headers) {
        for (Map.Entry<String,List<String>> entry : headers.map().entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            for (String value : values) {
                sb.append(key).append(": ").append(value).append("\r\n");
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

        if ((request.proxy() == null && !method.equals("CONNECT"))
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
        if (request.method().equals("CONNECT")) {
            // use authority for connect itself
            return authorityString(request.authority());
        }

        return uri == null? authorityString(request.authority()) : uri.toString();
    }

    private boolean finished;

    synchronized boolean finished() {
        return  finished;
    }

    synchronized void setFinished() {
        finished = true;
    }

    List<ByteBuffer> headers() {
        if (Log.requests() && request != null) {
            Log.logRequest(request.toString());
        }
        String uriString = requestURI();
        StringBuilder sb = new StringBuilder(64);
        sb.append(request.method())
          .append(' ')
          .append(uriString)
          .append(" HTTP/1.1\r\n");

        URI uri = request.uri();
        if (uri != null) {
            systemHeaders.setHeader("Host", hostString());
        }
        if (request == null || requestPublisher == null) {
            // Not a user request, or maybe a method, e.g. GET, with no body.
            contentLength = 0;
        } else {
            contentLength = requestPublisher.contentLength();
        }

        if (contentLength == 0) {
            systemHeaders.setHeader("Content-Length", "0");
        } else if (contentLength > 0) {
            systemHeaders.setHeader("Content-Length", Long.toString(contentLength));
            streaming = false;
        } else {
            streaming = true;
            systemHeaders.setHeader("Transfer-encoding", "chunked");
        }
        collectHeaders0(sb);
        String hs = sb.toString();
        logHeaders(hs);
        ByteBuffer b = ByteBuffer.wrap(hs.getBytes(US_ASCII));
        return List.of(b);
    }

    Http1BodySubscriber continueRequest()  {
        Http1BodySubscriber subscriber;
        if (streaming) {
            subscriber = new StreamSubscriber();
            requestPublisher.subscribe(subscriber);
        } else {
            if (contentLength == 0)
                return null;

            subscriber = new FixedContentSubscriber();
            requestPublisher.subscribe(subscriber);
        }
        return subscriber;
    }

    class StreamSubscriber extends Http1BodySubscriber {

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                Throwable t = new IllegalStateException("already subscribed");
                http1Exchange.appendToOutgoing(t);
            } else {
                this.subscription = subscription;
            }
        }

        @Override
        public void onNext(ByteBuffer item) {
            Objects.requireNonNull(item);
            if (complete) {
                Throwable t = new IllegalStateException("subscription already completed");
                http1Exchange.appendToOutgoing(t);
            } else {
                int chunklen = item.remaining();
                ArrayList<ByteBuffer> l = new ArrayList<>(3);
                l.add(getHeader(chunklen));
                l.add(item);
                l.add(ByteBuffer.wrap(CRLF));
                http1Exchange.appendToOutgoing(l);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (complete)
                return;

            subscription.cancel();
            http1Exchange.appendToOutgoing(throwable);
        }

        @Override
        public void onComplete() {
            if (complete) {
                Throwable t = new IllegalStateException("subscription already completed");
                http1Exchange.appendToOutgoing(t);
            } else {
                ArrayList<ByteBuffer> l = new ArrayList<>(2);
                l.add(ByteBuffer.wrap(EMPTY_CHUNK_BYTES));
                l.add(ByteBuffer.wrap(CRLF));
                complete = true;
                //setFinished();
                http1Exchange.appendToOutgoing(l);
                http1Exchange.appendToOutgoing(COMPLETED);
                setFinished();  // TODO: before or after,? does it matter?

            }
        }
    }

    class FixedContentSubscriber extends Http1BodySubscriber {

        private volatile long contentWritten;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                Throwable t = new IllegalStateException("already subscribed");
                http1Exchange.appendToOutgoing(t);
            } else {
                this.subscription = subscription;
            }
        }

        @Override
        public void onNext(ByteBuffer item) {
            debug.log(Level.DEBUG, "onNext");
            Objects.requireNonNull(item);
            if (complete) {
                Throwable t = new IllegalStateException("subscription already completed");
                http1Exchange.appendToOutgoing(t);
            } else {
                long writing = item.remaining();
                long written = (contentWritten += writing);

                if (written > contentLength) {
                    subscription.cancel();
                    String msg = connection.getConnectionFlow()
                                  + " [" + Thread.currentThread().getName() +"] "
                                  + "Too many bytes in request body. Expected: "
                                  + contentLength + ", got: " + written;
                    http1Exchange.appendToOutgoing(new IOException(msg));
                } else {
                    http1Exchange.appendToOutgoing(List.of(item));
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            debug.log(Level.DEBUG, "onError");
            if (complete)  // TODO: error?
                return;

            subscription.cancel();
            http1Exchange.appendToOutgoing(throwable);
        }

        @Override
        public void onComplete() {
            debug.log(Level.DEBUG, "onComplete");
            if (complete) {
                Throwable t = new IllegalStateException("subscription already completed");
                http1Exchange.appendToOutgoing(t);
            } else {
                complete = true;
                long written = contentWritten;
                if (contentLength > written) {
                    subscription.cancel();
                    Throwable t = new IOException(connection.getConnectionFlow()
                                         + " [" + Thread.currentThread().getName() +"] "
                                         + "Too few bytes returned by the publisher ("
                                                  + written + "/"
                                                  + contentLength + ")");
                    http1Exchange.appendToOutgoing(t);
                } else {
                    http1Exchange.appendToOutgoing(COMPLETED);
                }
            }
        }
    }

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] EMPTY_CHUNK_BYTES = {'0', '\r', '\n'};

    /** Returns a header for a particular chunk size */
    private static ByteBuffer getHeader(int size) {
        String hexStr = Integer.toHexString(size);
        byte[] hexBytes = hexStr.getBytes(US_ASCII);
        byte[] header = new byte[hexStr.length()+2];
        System.arraycopy(hexBytes, 0, header, 0, hexBytes.length);
        header[hexBytes.length] = CRLF[0];
        header[hexBytes.length+1] = CRLF[1];
        return ByteBuffer.wrap(header);
    }

    static final boolean DEBUG = Utils.DEBUG; // Revisit: temporary dev flag.
    final System.Logger  debug = Utils.getDebugLogger(this::toString, DEBUG);

}
