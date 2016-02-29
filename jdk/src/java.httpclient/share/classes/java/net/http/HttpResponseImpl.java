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
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;
import javax.net.ssl.SSLParameters;

/**
 * The implementation class for HttpResponse
 */
class HttpResponseImpl extends HttpResponse {

    int responseCode;
    Exchange exchange;
    HttpRequestImpl request;
    HttpHeaders1 headers;
    HttpHeaders1 trailers;
    SSLParameters sslParameters;
    URI uri;
    HttpClient.Version version;
    AccessControlContext acc;
    RawChannel rawchan;
    HttpConnection connection;

    public HttpResponseImpl(int responseCode, Exchange exch, HttpHeaders1 headers,
            HttpHeaders1 trailers, SSLParameters sslParameters,
            HttpClient.Version version, HttpConnection connection) {
        this.responseCode = responseCode;
        this.exchange = exch;
        this.request = exchange.request();
        this.headers = headers;
        this.trailers = trailers;
        this.sslParameters = sslParameters;
        this.uri = request.uri();
        this.version = version;
        this.connection = connection;
    }

    @Override
    public int statusCode() {
        return responseCode;
    }

    @Override
    public HttpRequestImpl request() {
        return request;
    }

    @Override
    public HttpHeaders headers() {
        headers.makeUnmodifiable();
        return headers;
    }

    @Override
    public HttpHeaders trailers() {
        trailers.makeUnmodifiable();
        return trailers;
    }


    @Override
    public <T> T body(java.net.http.HttpResponse.BodyProcessor<T> processor) {
        return exchange.responseBody(processor);
    }

    @Override
    public <T> CompletableFuture<T> bodyAsync(java.net.http.HttpResponse.BodyProcessor<T> processor) {
        acc = AccessController.getContext();
        return exchange.responseBodyAsync(processor);
    }

    @Override
    public SSLParameters sslParameters() {
        return sslParameters;
    }

    public AccessControlContext getAccessControlContext() {
        return acc;
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

    public static java.net.http.HttpResponse.BodyProcessor<Void> ignoreBody(boolean keepalive) {
        return new java.net.http.HttpResponse.BodyProcessor<Void>() {

            @Override
            public Void onResponseBodyStart(long clen, HttpHeaders h,
                    LongConsumer flowController) throws IOException {
                return null;
            }

            @Override
            public void onResponseBodyChunk(ByteBuffer b) throws IOException {
            }

            @Override
            public Void onResponseComplete() throws IOException {
                return null;
            }

            @Override
            public void onResponseError(Throwable t) {
            }
        };
    }

    /**
     *
     * @return
     */
    RawChannel rawChannel() {
        if (rawchan == null) {
            rawchan = new RawChannel(request.client(), connection);
        }
        return rawchan;
    }
}
