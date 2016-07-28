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
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Encapsulates one HTTP/1.1 request/responseAsync exchange.
 */
class Http1Exchange extends ExchangeImpl {

    final HttpRequestImpl request;        // main request
    final List<CompletableFuture<?>> operations; // used for cancel
    final Http1Request requestAction;
    volatile Http1Response response;
    final HttpConnection connection;
    final HttpClientImpl client;
    final ExecutorWrapper executor;

    @Override
    public String toString() {
        return request.toString();
    }

    HttpRequestImpl request() {
        return request;
    }

    Http1Exchange(Exchange exchange, HttpConnection connection)
        throws IOException
    {
        super(exchange);
        this.request = exchange.request();
        this.client = request.client();
        this.executor = client.executorWrapper();
        this.operations = Collections.synchronizedList(new LinkedList<>());
        if (connection != null) {
            this.connection = connection;
        } else {
            InetSocketAddress addr = Utils.getAddress(request);
            this.connection = HttpConnection.getConnection(addr, request);
        }
        this.requestAction = new Http1Request(request, this.connection);
    }


    HttpConnection connection() {
        return connection;
    }

    @Override
    <T> T responseBody(HttpResponse.BodyProcessor<T> processor)
        throws IOException
    {
        return responseBody(processor, true);
    }

    <T> T responseBody(HttpResponse.BodyProcessor<T> processor,
                       boolean return2Cache)
        throws IOException
    {
        try {
            T body = response.readBody(processor, return2Cache);
            return body;
        } catch (Throwable t) {
            connection.close();
            throw t;
        }
    }

    @Override
    <T> CompletableFuture<T> responseBodyAsync(HttpResponse.BodyProcessor<T> processor) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        request.client()
               .executorWrapper()
               .execute(() -> {
                            try {
                                T body = responseBody(processor);
                                cf.complete(body);
                            } catch (Throwable e) {
                                cf.completeExceptionally(e);
                            }
                        },
                        () -> response.response.getAccessControlContext()); // TODO: fix
        return cf;
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
    HttpResponseImpl getResponse() throws IOException {
        try {
            response = new Http1Response(connection, this);
            response.readHeaders();
            return response.response();
        } catch (Throwable t) {
            connection.close();
            throw t;
        }
    }

    @Override
    void sendRequest() throws IOException, InterruptedException {
        try {
            if (!connection.connected()) {
                connection.connect();
            }
            requestAction.sendRequest();
        } catch (Throwable t) {
            connection.close();
            throw t;
        }
    }

    private void closeConnection() {
        connection.close();
    }

    @Override
    CompletableFuture<Void> sendHeadersAsync() {
        if (!connection.connected()) {
            CompletableFuture<Void> op = connection.connectAsync()
                    .thenCompose(this::sendHdrsAsyncImpl)
                    .whenComplete((Void b, Throwable t) -> {
                        if (t != null)
                            closeConnection();
                    });
            operations.add(op);
            return op;
        } else {
            return sendHdrsAsyncImpl(null);
        }
    }

    private CompletableFuture<Void> sendHdrsAsyncImpl(Void v) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
                            try {
                                requestAction.sendHeadersOnly();
                                cf.complete(null);
                            } catch (Throwable e) {
                                cf.completeExceptionally(e);
                                connection.close();
                            }
                         },
                request::getAccessControlContext);
        operations.add(cf);
        return cf;
    }

    /**
     * Cancel checks to see if request and responseAsync finished already.
     * If not it closes the connection and completes all pending operations
     */
    @Override
    synchronized void cancel() {
        if (requestAction != null && requestAction.finished()
                && response != null && response.finished()) {
            return;
        }
        connection.close();
        IOException e = new IOException("Request cancelled");
        int count = 0;
        for (CompletableFuture<?> cf : operations) {
            cf.completeExceptionally(e);
            count++;
        }
        Log.logError("Http1Exchange.cancel: count=" + count);
    }

    CompletableFuture<HttpResponseImpl> getResponseAsyncImpl(Void v) {
        CompletableFuture<HttpResponseImpl> cf = new CompletableFuture<>();
        try {
            response = new Http1Response(connection, Http1Exchange.this);
            response.readHeaders();
            cf.complete(response.response());
        } catch (IOException e) {
            cf.completeExceptionally(e);
        }
        return cf;
    }

    @Override
    CompletableFuture<HttpResponseImpl> getResponseAsync(Void v) {
        CompletableFuture<HttpResponseImpl> cf =
            connection.whenReceivingResponse()
                      .thenCompose(this::getResponseAsyncImpl);

        operations.add(cf);
        return cf;
    }

    @Override
    CompletableFuture<Void> sendBodyAsync() {
        final CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                requestAction.continueRequest();
                cf.complete(null);
            } catch (Throwable e) {
                cf.completeExceptionally(e);
                connection.close();
            }
        }, request::getAccessControlContext);
        operations.add(cf);
        return cf;
    }

    @Override
    CompletableFuture<Void> sendRequestAsync() {
        CompletableFuture<Void> op;
        if (!connection.connected()) {
            op = connection.connectAsync()
                .thenCompose(this::sendRequestAsyncImpl)
                .whenComplete((Void v, Throwable t) -> {
                    if (t != null) {
                        closeConnection();
                    }
                });
        } else {
            op = sendRequestAsyncImpl(null);
        }
        operations.add(op);
        return op;
    }

    CompletableFuture<Void> sendRequestAsyncImpl(Void v) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                requestAction.sendRequest();
                cf.complete(null);
            } catch (Throwable e) {
                cf.completeExceptionally(e);
                connection.close();
            }
        }, request::getAccessControlContext);
        operations.add(cf);
        return cf;
    }
}
