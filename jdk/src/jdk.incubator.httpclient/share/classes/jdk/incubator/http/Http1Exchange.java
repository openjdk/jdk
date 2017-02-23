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
import java.net.InetSocketAddress;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodyProcessor;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;

/**
 * Encapsulates one HTTP/1.1 request/responseAsync exchange.
 */
class Http1Exchange<T> extends ExchangeImpl<T> {

    final HttpRequestImpl request;        // main request
    final List<CompletableFuture<?>> operations; // used for cancel
    final Http1Request requestAction;
    volatile Http1Response<T> response;
    final HttpConnection connection;
    final HttpClientImpl client;
    final Executor executor;
    final ByteBuffer buffer; // used for receiving

    @Override
    public String toString() {
        return request.toString();
    }

    HttpRequestImpl request() {
        return request;
    }

    Http1Exchange(Exchange<T> exchange, HttpConnection connection)
        throws IOException
    {
        super(exchange);
        this.request = exchange.request();
        this.client = exchange.client();
        this.executor = exchange.executor();
        this.operations = Collections.synchronizedList(new LinkedList<>());
        this.buffer = exchange.getBuffer();
        if (connection != null) {
            this.connection = connection;
        } else {
            InetSocketAddress addr = request.getAddress(client);
            this.connection = HttpConnection.getConnection(addr, client, request);
        }
        this.requestAction = new Http1Request(request, client, this.connection);
    }


    HttpConnection connection() {
        return connection;
    }


    @Override
    T readBody(BodyHandler<T> handler, boolean returnToCache)
        throws IOException
    {
        BodyProcessor<T> processor = handler.apply(response.responseCode(),
                                                   response.responseHeaders());
        setClientForResponse(processor);
        CompletableFuture<T> bodyCF = response.readBody(processor,
                                                        returnToCache,
                                                        this::executeInline);
        try {
            return bodyCF.join();
        } catch (CompletionException e) {
            throw Utils.getIOException(e);
        }
    }

    private void executeInline(Runnable r) {
        r.run();
    }

    synchronized ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    CompletableFuture<T> readBodyAsync(BodyHandler<T> handler,
                                       boolean returnToCache,
                                       Executor executor)
    {
        BodyProcessor<T> processor = handler.apply(response.responseCode(),
                                                   response.responseHeaders());
        setClientForResponse(processor);
        CompletableFuture<T> bodyCF = response.readBody(processor,
                                                        returnToCache,
                                                        executor);
        return bodyCF;
    }

    @Override
    void sendHeadersOnly() throws IOException, InterruptedException {
        try {
            if (!connection.connected()) {
                connection.connect();
            }
            requestAction.sendHeadersOnly();
        } catch (Throwable e) {
            connection.close();
            throw e;
        }
    }

    @Override
    void sendBody() throws IOException {
        try {
            requestAction.continueRequest();
        } catch (Throwable e) {
            connection.close();
            throw e;
        }
    }

    @Override
    Response getResponse() throws IOException {
        try {
            response = new Http1Response<>(connection, this);
            response.readHeaders();
            return response.response();
        } catch (Throwable t) {
            connection.close();
            throw t;
        }
    }

    private void closeConnection() {
        connection.close();
    }

    /**
     * Cancel checks to see if request and responseAsync finished already.
     * If not it closes the connection and completes all pending operations
     */
    @Override
    void cancel() {
        cancel(new IOException("Request cancelled"));
    }

    /**
     * Cancel checks to see if request and responseAsync finished already.
     * If not it closes the connection and completes all pending operations
     */
    @Override
    synchronized void cancel(IOException cause) {
        if (requestAction != null && requestAction.finished()
                && response != null && response.finished()) {
            return;
        }
        connection.close();
        int count = 0;
        for (CompletableFuture<?> cf : operations) {
            cf.completeExceptionally(cause);
            count++;
        }
        Log.logError("Http1Exchange.cancel: count=" + count);
    }

    CompletableFuture<Response> getResponseAsyncImpl(Executor executor) {
        return MinimalFuture.supply( () -> {
            response = new Http1Response<>(connection, Http1Exchange.this);
            response.readHeaders();
            return response.response();
        }, executor);
    }

    @Override
    CompletableFuture<Response> getResponseAsync(Executor executor) {
        CompletableFuture<Response> cf =
            connection.whenReceivingResponse()
                      .thenCompose((v) -> getResponseAsyncImpl(executor));

        operations.add(cf);
        return cf;
    }
}
