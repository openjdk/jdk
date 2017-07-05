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
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import jdk.incubator.http.internal.common.Log;
import static jdk.incubator.http.HttpClient.Version.HTTP_1_1;

/**
 * Handles a HTTP/1.1 response in two blocking calls. readHeaders() and
 * readBody(). There can be more than one of these per Http exchange.
 */
class Http1Response<T> {

    private volatile ResponseContent content;
    private final HttpRequestImpl request;
    private Response response;
    private final HttpConnection connection;
    private ResponseHeaders headers;
    private int responseCode;
    private final ByteBuffer buffer; // same buffer used for reading status line and headers
    private final Http1Exchange<T> exchange;
    private final boolean redirecting; // redirecting
    private boolean return2Cache; // return connection to cache when finished

    Http1Response(HttpConnection conn, Http1Exchange<T> exchange) {
        this.request = exchange.request();
        this.exchange = exchange;
        this.connection = conn;
        this.redirecting = false;
        buffer = exchange.getBuffer();
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
        if (Log.trace()) {
            Log.logTrace("Statusline: {0}", statusline);
        }
        char c = statusline.charAt(7);
        responseCode = Integer.parseInt(statusline.substring(9, 12));

        headers = new ResponseHeaders(connection, buffer);
        if (Log.headers()) {
            logHeaders(headers);
        }
        response = new Response(
                request, exchange.getExchange(),
                headers, responseCode, HTTP_1_1);
    }

    private boolean finished;

    synchronized void completed() {
        finished = true;
    }

    synchronized boolean finished() {
        return finished;
    }

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

    public CompletableFuture<T> readBody(
            HttpResponse.BodyProcessor<T> p,
            boolean return2Cache,
            Executor executor) {
        final BlockingPushPublisher<ByteBuffer> publisher = new BlockingPushPublisher<>();
        return readBody(p, return2Cache, publisher, executor);
    }

    private CompletableFuture<T> readBody(
            HttpResponse.BodyProcessor<T> p,
            boolean return2Cache,
            AbstractPushPublisher<ByteBuffer> publisher,
            Executor executor) {
        this.return2Cache = return2Cache;
        final jdk.incubator.http.HttpResponse.BodyProcessor<T> pusher = p;
        final CompletableFuture<T> cf = p.getBody().toCompletableFuture();

        int clen0;
        try {
            clen0 = headers.getContentLength();
        } catch (IOException ex) {
            cf.completeExceptionally(ex);
            return cf;
        }
        final int clen = fixupContentLen(clen0);

        executor.execute(() -> {
            try {
                content = new ResponseContent(
                        connection, clen, headers, pusher,
                        publisher.asDataConsumer(),
                        (t -> {
                            publisher.acceptError(t);
                            connection.close();
                            cf.completeExceptionally(t);
                        }),
                        () -> onFinished()
                );
                publisher.subscribe(p);
                if (cf.isCompletedExceptionally()) {
                    // if an error occurs during subscription
                    connection.close();
                    return;
                }
                content.pushBody(buffer);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        return cf;
    }

    private void onFinished() {
        if (return2Cache) {
            connection.returnToCache(headers);
        }
    }

    private void logHeaders(ResponseHeaders headers) {
        StringBuilder sb = new StringBuilder("RESPONSE HEADERS:\n");
        Log.dumpHeaders(sb, "    ", headers);
        Log.logHeaders(sb.toString());
    }

    Response response() {
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

    private int getBuffer() throws IOException {
        int n = buffer.remaining();

        if (n == 0) {
            buffer.clear();
            return connection.read(buffer);
        }
        return n;
    }

    String readStatusLine() throws IOException {
        boolean cr = false;
        StringBuilder statusLine = new StringBuilder(128);
        ByteBuffer b = buffer;
        while (getBuffer() != -1) {
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
