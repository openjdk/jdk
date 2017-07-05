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
import java.util.concurrent.CompletableFuture;
import static java.net.http.HttpClient.Version.HTTP_1_1;

/**
 * Splits request so that headers and body can be sent separately with optional
 * (multiple) responses in between (e.g. 100 Continue). Also request and
 * response always sent/received in different calls.
 *
 * Synchronous and asynchronous versions of each method are provided.
 *
 * Separate implementations of this class exist for HTTP/1.1 and HTTP/2
 *      Http1Exchange   (HTTP/1.1)
 *      Stream          (HTTP/2)
 *
 * These implementation classes are where work is allocated to threads.
 */
abstract class ExchangeImpl {

    final Exchange exchange;

    ExchangeImpl(Exchange e) {
        this.exchange = e;
    }

    /**
     * Initiates a new exchange and assigns it to a connection if one exists
     * already. connection usually null.
     */
    static ExchangeImpl get(Exchange exchange, HttpConnection connection)
        throws IOException, InterruptedException
    {
        HttpRequestImpl req = exchange.request();
        if (req.version() == HTTP_1_1) {
            return new Http1Exchange(exchange, connection);
        } else {
            Http2ClientImpl c2 = exchange.request().client().client2(); // TODO: improve
            HttpRequestImpl request = exchange.request();
            Http2Connection c = c2.getConnectionFor(request);
            if (c == null) {
                // no existing connection. Send request with HTTP 1 and then
                // upgrade if successful
                ExchangeImpl ex = new Http1Exchange(exchange, connection);
                exchange.h2Upgrade();
                return ex;
            }
            return c.createStream(exchange);
        }
    }

    /* The following methods have separate HTTP/1.1 and HTTP/2 implementations */

    /**
     * Sends the request headers only. May block until all sent.
     */
    abstract void sendHeadersOnly() throws IOException, InterruptedException;

    /**
     * Gets response headers by blocking if necessary. This may be an
     * intermediate response (like 101) or a final response 200 etc.
     */
    abstract HttpResponseImpl getResponse() throws IOException;

    /**
     * Sends a request body after request headers.
     */
    abstract void sendBody() throws IOException, InterruptedException;

    /**
     * Sends the entire request (headers and body) blocking.
     */
    abstract void sendRequest() throws IOException, InterruptedException;

    /**
     * Asynchronous version of sendHeaders().
     */
    abstract CompletableFuture<Void> sendHeadersAsync();

    /**
     * Asynchronous version of getResponse().  Requires void parameter for
     * CompletableFuture chaining.
     */
    abstract CompletableFuture<HttpResponseImpl> getResponseAsync(Void v);

    /**
     * Asynchronous version of sendBody().
     */
    abstract CompletableFuture<Void> sendBodyAsync();

    /**
     * Cancels a request.  Not currently exposed through API.
     */
    abstract void cancel();

    /**
     * Asynchronous version of sendRequest().
     */
    abstract CompletableFuture<Void> sendRequestAsync();

    abstract <T> T responseBody(HttpResponse.BodyProcessor<T> processor)
        throws IOException;

    /**
     * Asynchronous version of responseBody().
     */
    abstract <T> CompletableFuture<T>
    responseBodyAsync(HttpResponse.BodyProcessor<T> processor);
}
