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
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.net.ssl.SSLParameters;
import jdk.incubator.http.internal.websocket.RawChannel;

/**
 * The implementation class for HttpResponse
 */
class HttpResponseImpl<T> extends HttpResponse<T> implements RawChannel.Provider {

    final int responseCode;
    final Exchange<T> exchange;
    final HttpRequest initialRequest;
    final Optional<HttpResponse<T>> previousResponse;
    final HttpHeaders headers;
    final SSLParameters sslParameters;
    final URI uri;
    final HttpClient.Version version;
    RawChannel rawchan;
    final HttpConnection connection;
    final Stream<T> stream;
    final T body;

    public HttpResponseImpl(HttpRequest initialRequest,
                            Response response,
                            HttpResponse<T> previousResponse,
                            T body,
                            Exchange<T> exch) {
        this.responseCode = response.statusCode();
        this.exchange = exch;
        this.initialRequest = initialRequest;
        this.previousResponse = Optional.ofNullable(previousResponse);
        this.headers = response.headers();
        //this.trailers = trailers;
        this.sslParameters = exch.client().sslParameters();
        this.uri = response.request().uri();
        this.version = response.version();
        this.connection = exch.exchImpl.connection();
        this.stream = null;
        this.body = body;
    }

//    // A response to a PUSH_PROMISE
//    public HttpResponseImpl(Response response,
//                            HttpRequestImpl pushRequest,
//                            ImmutableHeaders headers,
//                            Stream<T> stream,
//                            SSLParameters sslParameters,
//                            T body) {
//        this.responseCode = response.statusCode();
//        this.exchange = null;
//        this.initialRequest = null; // ## fix this
//        this.finalRequest = pushRequest;
//        this.headers = headers;
//        //this.trailers = null;
//        this.sslParameters = sslParameters;
//        this.uri = finalRequest.uri(); // TODO: take from headers
//        this.version = HttpClient.Version.HTTP_2;
//        this.connection = stream.connection();
//        this.stream = stream;
//        this.body = body;
//    }

    private ExchangeImpl<?> exchangeImpl() {
        return exchange != null ? exchange.exchImpl : stream;
    }

    @Override
    public int statusCode() {
        return responseCode;
    }

    @Override
    public HttpRequest request() {
        return initialRequest;
    }

    @Override
    public Optional<HttpResponse<T>> previousResponse() {
        return previousResponse;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public T body() {
        return body;
    }

    @Override
    public SSLParameters sslParameters() {
        return sslParameters;
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public HttpClient.Version version() {
        return version;
    }
    // keepalive flag determines whether connection is closed or kept alive
    // by reading/skipping data

    /**
     * Returns a RawChannel that may be used for WebSocket protocol.
     * @implNote This implementation does not support RawChannel over
     *           HTTP/2 connections.
     * @return a RawChannel that may be used for WebSocket protocol.
     * @throws UnsupportedOperationException if getting a RawChannel over
     *         this connection is not supported.
     * @throws IOException if an I/O exception occurs while retrieving
     *         the channel.
     */
    @Override
    public synchronized RawChannel rawChannel() throws IOException {
        if (rawchan == null) {
            ExchangeImpl<?> exchImpl = exchangeImpl();
            if (!(exchImpl instanceof Http1Exchange)) {
                // RawChannel is only used for WebSocket - and WebSocket
                // is not supported over HTTP/2 yet, so we should not come
                // here. Getting a RawChannel over HTTP/2 might be supported
                // in the future, but it would entail retrieving any left over
                // bytes that might have been read but not consumed by the
                // HTTP/2 connection.
                throw new UnsupportedOperationException("RawChannel is not supported over HTTP/2");
            }
            // Http1Exchange may have some remaining bytes in its
            // internal buffer.
            Supplier<ByteBuffer> initial = ((Http1Exchange<?>)exchImpl)::drainLeftOverBytes;
            rawchan = new RawChannelImpl(exchange.client(), connection, initial);
        }
        return rawchan;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String method = request().method();
        URI uri = request().uri();
        String uristring = uri == null ? "" : uri.toString();
        sb.append('(')
          .append(method)
          .append(" ")
          .append(uristring)
          .append(") ")
          .append(statusCode());
        return sb.toString();
    }
}
