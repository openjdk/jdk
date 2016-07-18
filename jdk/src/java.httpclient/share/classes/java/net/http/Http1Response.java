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

package java.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;
import static java.net.http.HttpClient.Version.HTTP_1_1;

/**
 * Handles a HTTP/1.1 response in two blocking calls. readHeaders() and
 * readBody(). There can be more than one of these per Http exchange.
 */
class Http1Response {

    private ResponseContent content;
    private final HttpRequestImpl request;
    HttpResponseImpl response;
    private final HttpConnection connection;
    private ResponseHeaders headers;
    private int responseCode;
    private ByteBuffer buffer; // same buffer used for reading status line and headers
    private final Http1Exchange exchange;
    private final boolean redirecting; // redirecting
    private boolean return2Cache; // return connection to cache when finished

    Http1Response(HttpConnection conn, Http1Exchange exchange) {
        this.request = exchange.request();
        this.exchange = exchange;
        this.connection = conn;
        this.redirecting = false;
        buffer = connection.getRemaining();
    }

    // called when the initial read should come from a buffer left
    // over from a previous response.
    void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @SuppressWarnings("unchecked")
    public void readHeaders() throws IOException {
        String statusline = readStatusLine();
        if (statusline == null) {
            if (Log.errors()) {
                Log.logError("Connection closed. Retry");
            }
            connection.close();
            // connection was closed
            throw new IOException("Connection closed");
        }
        if (!statusline.startsWith("HTTP/1.")) {
            throw new IOException("Invalid status line: " + statusline);
        }
        char c = statusline.charAt(7);
        responseCode = Integer.parseInt(statusline.substring(9, 12));

        headers = new ResponseHeaders(connection, buffer);
        headers.initHeaders();
        if (Log.headers()) {
            logHeaders(headers);
        }
        response = new HttpResponseImpl(responseCode,
                                        exchange.exchange,
                                        headers,
                                        null,
                                        connection.sslParameters(),
                                        HTTP_1_1,
                                        connection);
    }

    private boolean finished;

    synchronized void completed() {
        finished = true;
    }

    synchronized boolean finished() {
        return finished;
    }

    // Blocking flow controller implementation. Only works when a
    // thread is dedicated to reading response body

    static class FlowController implements LongConsumer {
        long window ;

        @Override
        public synchronized void accept(long value) {
            window += value;
            notifyAll();
        }

        public synchronized void request(long value) throws InterruptedException {
            while (window < value) {
                wait();
            }
            window -= value;
        }
    }

    FlowController flowController;

    int fixupContentLen(int clen) {
        if (request.method().equalsIgnoreCase("HEAD")) {
            return 0;
        }
        if (clen == -1) {
            if (headers.firstValue("Transfer-encoding").orElse("")
                       .equalsIgnoreCase("chunked")) {
                return -1;
            }
            return 0;
        }
        return clen;
    }

    private void returnBuffer(ByteBuffer buf) {
        // not currently used, but will be when we change SSL to use fixed
        // sized buffers and a single buffer pool for HttpClientImpl
    }

    @SuppressWarnings("unchecked")
    public <T> T readBody(java.net.http.HttpResponse.BodyProcessor<T> p,
                          boolean return2Cache)
        throws IOException
    {
        T body = null; // TODO: check null case below
        this.return2Cache = return2Cache;
        final java.net.http.HttpResponse.BodyProcessor<T> pusher = p;

        int clen0 = headers.getContentLength();
        final int clen = fixupContentLen(clen0);

        flowController = new FlowController();

        body = pusher.onResponseBodyStart(clen, headers, flowController);

        ExecutorWrapper executor;
        if (body == null) {
            executor = ExecutorWrapper.callingThread();
        } else {
            executor = request.client().executorWrapper();
        }

        final ResponseHeaders h = headers;
        if (body == null) {
            content = new ResponseContent(connection,
                                          clen,
                                          h,
                                          pusher,
                                          flowController);
            content.pushBody(headers.getResidue());
            body = pusher.onResponseComplete();
            completed();
            onFinished();
            return body;
        } else {
            executor.execute(() -> {
                    try {
                        content = new ResponseContent(connection,
                                                      clen,
                                                      h,
                                                      pusher,
                                                      flowController);
                        content.pushBody(headers.getResidue());
                        pusher.onResponseComplete();
                        completed();
                        onFinished();
                    } catch (Throwable e) {
                        pusher.onResponseError(e);
                    }
                },
                () -> response.getAccessControlContext());
        }
        return body;
    }

    private void onFinished() {
        connection.buffer = content.getResidue();
        if (return2Cache) {
            connection.returnToCache(headers);
        }
    }

    private void logHeaders(ResponseHeaders headers) {
        Map<String, List<String>> h = headers.mapInternal();
        Set<String> keys = h.keySet();
        Set<Map.Entry<String, List<String>>> entries = h.entrySet();
        for (Map.Entry<String, List<String>> entry : entries) {
            String key = entry.getKey();
            StringBuilder sb = new StringBuilder();
            sb.append(key).append(": ");
            List<String> values = entry.getValue();
            if (values != null) {
                for (String value : values) {
                    sb.append(value).append(' ');
                }
            }
            Log.logHeaders(sb.toString());
        }
    }

    HttpResponseImpl response() {
        return response;
    }

    boolean redirecting() {
        return redirecting;
    }

    HttpHeaders responseHeaders() {
        return headers;
    }

    int responseCode() {
        return responseCode;
    }

    static final char CR = '\r';
    static final char LF = '\n';

    private ByteBuffer getBuffer() throws IOException {
        if (buffer == null || !buffer.hasRemaining()) {
            buffer = connection.read();
        }
        return buffer;
    }

    ByteBuffer buffer() {
        return buffer;
    }

    String readStatusLine() throws IOException {
        boolean cr = false;
        StringBuilder statusLine = new StringBuilder(128);
        ByteBuffer b;
        while ((b = getBuffer()) != null) {
            byte[] buf = b.array();
            int offset = b.position();
            int len = b.limit() - offset;

            for (int i = 0; i < len; i++) {
                char c = (char) buf[i+offset];

                if (cr) {
                    if (c == LF) {
                        b.position(i + 1 + offset);
                        return statusLine.toString();
                    } else {
                        throw new IOException("invalid status line");
                    }
                }
                if (c == CR) {
                    cr = true;
                } else {
                    statusLine.append(c);
                }
            }
            // unlikely, but possible, that multiple reads required
            b.position(b.limit());
        }
        return null;
    }
}
