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
    private final List<CompletableFuture<?>> operations; // used for cancel
    final Http1Request requestAction;
    private volatile Http1Response<T> response;
    // use to record possible cancellation raised before any operation
    // has been initiated.
    private IOException failed;
    final HttpConnection connection;
    final HttpClientImpl client;
    final Executor executor;
    volatile ByteBuffer buffer; // used for receiving

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
        this.operations = new LinkedList<>();
        this.buffer = Utils.EMPTY_BYTEBUFFER;
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
    T readBody(BodyHandler<T> handler, boolean returnConnectionToPool)
        throws IOException
    {
        BodyProcessor<T> processor = handler.apply(response.responseCode(),
                                                   response.responseHeaders());
        setClientForResponse(processor);
        CompletableFuture<T> bodyCF = response.readBody(processor,
                                                        returnConnectionToPool,
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
                                       boolean returnConnectionToPool,
                                       Executor executor)
    {
        BodyProcessor<T> processor = handler.apply(response.responseCode(),
                                                   response.responseHeaders());
        setClientForResponse(processor);
        CompletableFuture<T> bodyCF = response.readBody(processor,
                                                        returnConnectionToPool,
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
            Response r = response.response();
            buffer = response.getBuffer();
            return r;
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
        if (operations.isEmpty()) {
            failed = cause;
            Log.logTrace("Http1Exchange: request [{0}/timeout={1}ms] no pending operation."
                         + "\n\tCan''t cancel yet with {2}",
                         request.uri(),
                         request.duration() == null ? -1 :
                         // calling duration.toMillis() can throw an exception.
                         // this is just debugging, we don't care if it overflows.
                         (request.duration().getSeconds() * 1000
                          + request.duration().getNano() / 1000000),
                         cause);
        } else {
            for (CompletableFuture<?> cf : operations) {
                cf.completeExceptionally(cause);
                count++;
            }
        }
        Log.logError("Http1Exchange.cancel: count=" + count);
    }

    CompletableFuture<Response> getResponseAsyncImpl(Executor executor) {
        return MinimalFuture.supply( () -> {
            response = new Http1Response<>(connection, Http1Exchange.this);
            response.readHeaders();
            Response r = response.response();
            buffer = response.getBuffer();
            return r;
        }, executor);
    }

    @Override
    CompletableFuture<Response> getResponseAsync(Executor executor) {
        CompletableFuture<Response> cf =
            connection.whenReceivingResponse()
                      .thenCompose((v) -> getResponseAsyncImpl(executor));
        IOException cause;
        synchronized(this) {
            operations.add(cf);
            cause = failed;
            failed = null;
        }
        if (cause != null) {
            Log.logTrace("Http1Exchange: request [{0}/timeout={1}ms]"
                         + "\n\tCompleting exceptionally with {2}\n",
                         request.uri(),
                         request.duration() == null ? -1 :
                         // calling duration.toMillis() can throw an exception.
                         // this is just debugging, we don't care if it overflows.
                         (request.duration().getSeconds() * 1000
                          + request.duration().getNano() / 1000000),
                         cause);
            cf.completeExceptionally(cause);
        }
        return cf;
    }
}
