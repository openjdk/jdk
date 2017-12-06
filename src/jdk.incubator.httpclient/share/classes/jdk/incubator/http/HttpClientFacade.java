/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.ref.Reference;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * An HttpClientFacade is a simple class that wraps an HttpClient implementation
 * and delegates everything to its implementation delegate.
 */
final class HttpClientFacade extends HttpClient {

    final HttpClientImpl impl;

    /**
     * Creates an HttpClientFacade.
     */
    HttpClientFacade(HttpClientImpl impl) {
        this.impl = impl;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return impl.cookieHandler();
    }

    @Override
    public Redirect followRedirects() {
        return impl.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return impl.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return impl.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return impl.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return impl.authenticator();
    }

    @Override
    public HttpClient.Version version() {
        return impl.version();
    }

    @Override
    public Optional<Executor> executor() {
        return impl.executor();
    }

    @Override
    public <T> HttpResponse<T>
    send(HttpRequest req, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException
    {
        try {
            return impl.send(req, responseBodyHandler);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>>
    sendAsync(HttpRequest req, HttpResponse.BodyHandler<T> responseBodyHandler) {
        try {
            return impl.sendAsync(req, responseBodyHandler);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public <U, T> CompletableFuture<U>
    sendAsync(HttpRequest req, HttpResponse.MultiSubscriber<U, T> multiSubscriber) {
        try {
            return impl.sendAsync(req, multiSubscriber);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        try {
            return impl.newWebSocketBuilder();
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public String toString() {
        // Used by tests to get the client's id.
        return impl.toString();
    }
}
