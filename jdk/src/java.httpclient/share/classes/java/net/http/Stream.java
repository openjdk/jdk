/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongConsumer;

/**
 * Http/2 Stream
 */
class Stream extends ExchangeImpl {

    void debugPrint() {
    }

    @Override
    @SuppressWarnings("unchecked")
    <T> CompletableFuture<T> responseBodyAsync(HttpResponse.BodyProcessor<T> processor) {
            return null;
    }

    Stream(HttpClientImpl client, Http2Connection connection, Exchange e) {
        super(e);
    }

    @Override
    HttpResponseImpl getResponse() throws IOException {
        return null;
    }

    @Override
    void sendRequest() throws IOException, InterruptedException {
    }

    @Override
    void sendHeadersOnly() throws IOException, InterruptedException {
    }

    @Override
    void sendBody() throws IOException, InterruptedException {
    }

    @Override
    CompletableFuture<Void> sendHeadersAsync() {
        return null;
    }

    @Override
    CompletableFuture<HttpResponseImpl> getResponseAsync(Void v) {
        return null;
    }

    @Override
    CompletableFuture<Void> sendBodyAsync() {
        return null;
    }

    @Override
    void cancel() {
    }


    @Override
    CompletableFuture<Void> sendRequestAsync() {
        return null;
    }

    @Override
    <T> T responseBody(HttpResponse.BodyProcessor<T> processor) throws IOException {
        return null;
    }
}
