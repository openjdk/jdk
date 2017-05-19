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

import jdk.incubator.http.internal.common.HttpHeadersImpl;
import jdk.incubator.http.internal.websocket.WebSocketRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.security.AccessControlContext;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static jdk.incubator.http.internal.common.Utils.ALLOWED_HEADERS;

class HttpRequestImpl extends HttpRequest implements WebSocketRequest {

    private final HttpHeaders userHeaders;
    private final HttpHeadersImpl systemHeaders;
    private final URI uri;
    private InetSocketAddress authority; // only used when URI not specified
    private final String method;
    final BodyProcessor requestProcessor;
    final boolean secure;
    final boolean expectContinue;
    private boolean isWebSocket;
    private AccessControlContext acc;
    private final Duration duration;
    private final Optional<HttpClient.Version> version;

    /**
     * Creates an HttpRequestImpl from the given builder.
     */
    public HttpRequestImpl(HttpRequestBuilderImpl builder) {
        this.method = builder.method();
        this.userHeaders = ImmutableHeaders.of(builder.headers().map(), ALLOWED_HEADERS);
        this.systemHeaders = new HttpHeadersImpl();
        this.uri = builder.uri();
        this.expectContinue = builder.expectContinue();
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        if (builder.body() == null) {
            this.requestProcessor = HttpRequest.noBody();
        } else {
            this.requestProcessor = builder.body();
        }
        this.duration = builder.duration();
        this.version = builder.version();
    }

    /**
     * Creates an HttpRequestImpl from the given request.
     */
    public HttpRequestImpl(HttpRequest request) {
        this.method = request.method();
        this.userHeaders = request.headers();
        if (request instanceof HttpRequestImpl) {
            this.systemHeaders = ((HttpRequestImpl) request).systemHeaders;
            this.isWebSocket = ((HttpRequestImpl) request).isWebSocket;
        } else {
            this.systemHeaders = new HttpHeadersImpl();
        }
        this.uri = request.uri();
        this.expectContinue = request.expectContinue();
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        if (!request.bodyProcessor().isPresent()) {
            this.requestProcessor = HttpRequest.noBody();
        } else {
            this.requestProcessor = request.bodyProcessor().get();
        }
        this.duration = request.duration();
        this.version = request.version();
    }

    /** Creates a HttpRequestImpl using fields of an existing request impl. */
    public HttpRequestImpl(URI uri,
                           String method,
                           HttpRequestImpl other) {
        this.method = method == null? "GET" : method;
        this.userHeaders = other.userHeaders;
        this.isWebSocket = other.isWebSocket;
        this.systemHeaders = other.systemHeaders;
        this.uri = uri;
        this.expectContinue = other.expectContinue;
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.requestProcessor = other.requestProcessor;
        this.acc = other.acc;
        this.duration = other.duration;
        this.version = other.version();
    }

    /* used for creating CONNECT requests  */
    HttpRequestImpl(String method, HttpClientImpl client,
                    InetSocketAddress authority) {
        // TODO: isWebSocket flag is not specified, but the assumption is that
        // such a request will never be made on a connection that will be returned
        // to the connection pool (we might need to revisit this constructor later)
        this.method = method;
        this.systemHeaders = new HttpHeadersImpl();
        this.userHeaders = ImmutableHeaders.empty();
        this.uri = null;
        this.requestProcessor = HttpRequest.noBody();
        this.authority = authority;
        this.secure = false;
        this.expectContinue = false;
        this.duration = null;
        this.version = Optional.of(client.version());
    }

    /**
     * Creates a HttpRequestImpl from the given set of Headers and the associated
     * "parent" request. Fields not taken from the headers are taken from the
     * parent.
     */
    static HttpRequestImpl createPushRequest(HttpRequestImpl parent,
                                             HttpHeadersImpl headers)
        throws IOException
    {
        return new HttpRequestImpl(parent, headers);
    }

    // only used for push requests
    private HttpRequestImpl(HttpRequestImpl parent, HttpHeadersImpl headers)
        throws IOException
    {
        this.method = headers.firstValue(":method")
                .orElseThrow(() -> new IOException("No method in Push Promise"));
        String path = headers.firstValue(":path")
                .orElseThrow(() -> new IOException("No path in Push Promise"));
        String scheme = headers.firstValue(":scheme")
                .orElseThrow(() -> new IOException("No scheme in Push Promise"));
        String authority = headers.firstValue(":authority")
                .orElseThrow(() -> new IOException("No authority in Push Promise"));
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(authority).append(path);
        this.uri = URI.create(sb.toString());

        this.userHeaders = ImmutableHeaders.of(headers.map(), ALLOWED_HEADERS);
        this.systemHeaders = parent.systemHeaders;
        this.expectContinue = parent.expectContinue;
        this.secure = parent.secure;
        this.requestProcessor = parent.requestProcessor;
        this.acc = parent.acc;
        this.duration = parent.duration;
        this.version = parent.version;
    }

    @Override
    public String toString() {
        return (uri == null ? "" : uri.toString()) + " " + method;
    }

    @Override
    public HttpHeaders headers() {
        return userHeaders;
    }

    InetSocketAddress authority() { return authority; }

    void setH2Upgrade(Http2ClientImpl h2client) {
        systemHeaders.setHeader("Connection", "Upgrade, HTTP2-Settings");
        systemHeaders.setHeader("Upgrade", "h2c");
        systemHeaders.setHeader("HTTP2-Settings", h2client.getSettingsString());
    }

    @Override
    public boolean expectContinue() { return expectContinue; }

    InetSocketAddress proxy(HttpClientImpl client) {
        ProxySelector ps = client.proxy().orElse(null);
        if (ps == null) {
            ps = client.proxy().orElse(null);
        }
        if (ps == null || method.equalsIgnoreCase("CONNECT")) {
            return null;
        }
        return (InetSocketAddress)ps.select(uri).get(0).address();
    }

    boolean secure() { return secure; }

    @Override
    public void isWebSocket(boolean is) {
        isWebSocket = is;
    }

    boolean isWebSocket() {
        return isWebSocket;
    }

//    /** Returns the follow-redirects setting for this request. */
//    @Override
//    public jdk.incubator.http.HttpClient.Redirect followRedirects() {
//        return followRedirects;
//    }

    @Override
    public Optional<BodyProcessor> bodyProcessor() {
        return Optional.of(requestProcessor);
    }

    /**
     * Returns the request method for this request. If not set explicitly,
     * the default method for any request is "GET".
     */
    @Override
    public String method() { return method; }

    @Override
    public URI uri() { return uri; }

    @Override
    public Duration duration() {
        return duration;
    }

//    HttpClientImpl client() {
//        return client;
//    }

    HttpHeaders getUserHeaders() { return userHeaders; }

    HttpHeadersImpl getSystemHeaders() { return systemHeaders; }

    @Override
    public Optional<HttpClient.Version> version() { return version; }

    void addSystemHeader(String name, String value) {
        systemHeaders.addHeader(name, value);
    }

    @Override
    public void setSystemHeader(String name, String value) {
        systemHeaders.setHeader(name, value);
    }

//    @Override
//    public <T> HttpResponse<T>
//    response(HttpResponse.BodyHandler<T> responseHandler)
//        throws IOException, InterruptedException
//    {
//        if (!sent.compareAndSet(false, true)) {
//            throw new IllegalStateException("request already sent");
//        }
//        MultiExchange<Void,T> mex = new MultiExchange<>(this, responseHandler);
//        return mex.response();
//    }
//
//    @Override
//    public <T> CompletableFuture<HttpResponse<T>>
//    responseAsync(HttpResponse.BodyHandler<T> responseHandler)
//    {
//        if (!sent.compareAndSet(false, true)) {
//            throw new IllegalStateException("request already sent");
//        }
//        MultiExchange<Void,T> mex = new MultiExchange<>(this, responseHandler);
//        return mex.responseAsync(null)
//                  .thenApply((HttpResponseImpl<T> b) -> (HttpResponse<T>) b);
//    }
//
//    @Override
//    public <U, T> CompletableFuture<U>
//    multiResponseAsync(HttpResponse.MultiProcessor<U, T> responseHandler)
//    {
//        if (!sent.compareAndSet(false, true)) {
//            throw new IllegalStateException("request already sent");
//        }
//        MultiExchange<U,T> mex = new MultiExchange<>(this, responseHandler);
//        return mex.multiResponseAsync();
//    }

    public InetSocketAddress getAddress(HttpClientImpl client) {
        URI uri = uri();
        if (uri == null) {
            return authority();
        }
        int port = uri.getPort();
        if (port == -1) {
            if (uri.getScheme().equalsIgnoreCase("https")) {
                port = 443;
            } else {
                port = 80;
            }
        }
        String host = uri.getHost();
        if (proxy(client) == null) {
            return new InetSocketAddress(host, port);
        } else {
            return InetSocketAddress.createUnresolved(host, port);
        }
    }
}
