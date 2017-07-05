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
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse.MultiProcessor;
import java.util.concurrent.CompletableFuture;
import java.security.AccessControlContext;
import java.security.AccessController;
import static java.net.http.HttpRedirectImpl.getRedirects;
import java.util.Locale;

class HttpRequestImpl extends HttpRequest {

    private final ImmutableHeaders userHeaders;
    private final HttpHeadersImpl systemHeaders;
    private final URI uri;
    private InetSocketAddress authority; // only used when URI not specified
    private final String method;
    private final HttpClientImpl client;
    private final HttpRedirectImpl followRedirects;
    private final ProxySelector proxy;
    final BodyProcessor requestProcessor;
    final boolean secure;
    final boolean expectContinue;
    private final java.net.http.HttpClient.Version version;
    private boolean isWebSocket;
    final MultiExchange exchange;
    private boolean receiving;
    private AccessControlContext acc;
    private final long timeval;
    private Stream.PushGroup<?> pushGroup;

    public HttpRequestImpl(HttpClientImpl client,
                           String method,
                           HttpRequestBuilderImpl builder) {
        this.client = client;
        this.method = method == null? "GET" : method;
        this.userHeaders = builder.headers() == null ?
                new ImmutableHeaders() :
                new ImmutableHeaders(builder.headers(), Utils.ALLOWED_HEADERS);
        this.followRedirects = getRedirects(builder.followRedirects() == null ?
                client.followRedirects() : builder.followRedirects());
        this.systemHeaders = new HttpHeadersImpl();
        this.uri = builder.uri();
        this.proxy = builder.proxy();
        this.expectContinue = builder.expectContinue();
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.version = builder.version();
        if (builder.body() == null) {
            this.requestProcessor = HttpRequest.noBody();
        } else {
            this.requestProcessor = builder.body();
        }
        this.exchange = new MultiExchange(this);
        this.timeval = builder.timeval();
    }

    /** Creates a HttpRequestImpl using fields of an existing request impl. */
    public HttpRequestImpl(URI uri,
                           HttpRequest request,
                           HttpClientImpl client,
                           String method,
                           HttpRequestImpl other) {
        this.client = client;
        this.method = method == null? "GET" : method;
        this.userHeaders = other.userHeaders;
        this.followRedirects = getRedirects(other.followRedirects() == null ?
                client.followRedirects() : other.followRedirects());
        this.systemHeaders = other.systemHeaders;
        this.uri = uri;
        this.expectContinue = other.expectContinue;
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.requestProcessor = other.requestProcessor;
        this.proxy = other.proxy;
        this.version = other.version;
        this.acc = other.acc;
        this.exchange = new MultiExchange(this);
        this.timeval = other.timeval;
    }

    /* used for creating CONNECT requests  */
    HttpRequestImpl(HttpClientImpl client,
                    String method,
                    InetSocketAddress authority) {
        this.client = client;
        this.method = method;
        this.followRedirects = getRedirects(client.followRedirects());
        this.systemHeaders = new HttpHeadersImpl();
        this.userHeaders = new ImmutableHeaders();
        this.uri = null;
        this.proxy = null;
        this.requestProcessor = HttpRequest.noBody();
        this.version = java.net.http.HttpClient.Version.HTTP_1_1;
        this.authority = authority;
        this.secure = false;
        this.expectContinue = false;
        this.exchange = new MultiExchange(this);
        this.timeval = 0; // block TODO: fix
    }

    @Override
    public HttpClientImpl client() {
        return client;
    }

    /**
     * Creates a HttpRequestImpl from the given set of Headers and the associated
     * "parent" request. Fields not taken from the headers are taken from the
     * parent.
     */
    static HttpRequestImpl createPushRequest(HttpRequestImpl parent,
            HttpHeadersImpl headers) throws IOException {

        return new HttpRequestImpl(parent, headers);
    }

    // only used for push requests
    private HttpRequestImpl(HttpRequestImpl parent, HttpHeadersImpl headers) throws IOException {
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

        this.client = parent.client;
        this.userHeaders = new ImmutableHeaders(headers, Utils.ALLOWED_HEADERS);
        this.followRedirects = parent.followRedirects;
        this.systemHeaders = parent.systemHeaders;
        this.expectContinue = parent.expectContinue;
        this.secure = parent.secure;
        this.requestProcessor = parent.requestProcessor;
        this.proxy = parent.proxy;
        this.version = parent.version;
        this.acc = parent.acc;
        this.exchange = parent.exchange;
        this.timeval = parent.timeval;
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

    void setH2Upgrade() {
        Http2ClientImpl h2client = client.client2();
        systemHeaders.setHeader("Connection", "Upgrade, HTTP2-Settings");
        systemHeaders.setHeader("Upgrade", "h2c");
        systemHeaders.setHeader("HTTP2-Settings", h2client.getSettingsString());
    }

    private synchronized void receiving() {
        if (receiving) {
            throw new IllegalStateException("already receiving response");
        }
        receiving = true;
    }

    synchronized Stream.PushGroup<?> pushGroup() {
        return pushGroup;
    }

    /*
     * Response filters may result in a new HttpRequestImpl being created
     * (but still associated with the same API HttpRequest) and the process
     * is repeated.
     */
    @Override
    public HttpResponse response() throws IOException, InterruptedException {
        receiving(); // TODO: update docs
        if (System.getSecurityManager() != null) {
            acc = AccessController.getContext();
        }
        return exchange.response();
    }

    @Override
    public synchronized CompletableFuture<HttpResponse> responseAsync() {
        receiving(); // TODO: update docs
        if (System.getSecurityManager() != null) {
            acc = AccessController.getContext();
        }
        return exchange.responseAsync(null)
            .thenApply((r) -> (HttpResponse)r);
    }


    @SuppressWarnings("unchecked")
    @Override
    public synchronized <U> CompletableFuture<U>
    multiResponseAsync(MultiProcessor<U> rspproc) {
        if (System.getSecurityManager() != null) {
            acc = AccessController.getContext();
        }
        this.pushGroup = new Stream.PushGroup<>(rspproc, this);
        CompletableFuture<HttpResponse> cf = pushGroup.mainResponse();
        responseAsync()
            .whenComplete((HttpResponse r, Throwable t) -> {
                if (r != null)
                    cf.complete(r);
                else
                    cf.completeExceptionally(t);
                pushGroup.pushError(t);
            });
        return (CompletableFuture<U>)pushGroup.groupResult();
    }

    @Override
    public boolean expectContinue() { return expectContinue; }

    public boolean requestHttp2() {
        return version.equals(HttpClient.Version.HTTP_2);
        //return client.getHttp2Allowed();
    }

    AccessControlContext getAccessControlContext() { return acc; }

    InetSocketAddress proxy() {
        ProxySelector ps = this.proxy;
        if (ps == null) {
            ps = client.proxy().orElse(null);
        }
        if (ps == null || method.equalsIgnoreCase("CONNECT")) {
            return null;
        }
        return (InetSocketAddress)ps.select(uri).get(0).address();
    }

    boolean secure() { return secure; }

    void isWebSocket(boolean is) {
        isWebSocket = is;
    }

    boolean isWebSocket() {
        return isWebSocket;
    }

    /** Returns the follow-redirects setting for this request. */
    @Override
    public java.net.http.HttpClient.Redirect followRedirects() {
        return getRedirects(followRedirects);
    }

    HttpRedirectImpl followRedirectsImpl() { return followRedirects; }

    /**
     * Returns the request method for this request. If not set explicitly,
     * the default method for any request is "GET".
     */
    @Override
    public String method() { return method; }

    @Override
    public URI uri() { return uri; }

    HttpHeaders getUserHeaders() { return userHeaders; }

    HttpHeadersImpl getSystemHeaders() { return systemHeaders; }

    HttpClientImpl getClient() { return client; }

    BodyProcessor requestProcessor() { return requestProcessor; }

    @Override
    public Version version() { return version; }

    void addSystemHeader(String name, String value) {
        systemHeaders.addHeader(name, value);
    }

    void setSystemHeader(String name, String value) {
        systemHeaders.setHeader(name, value);
    }

    long timeval() { return timeval; }
}
