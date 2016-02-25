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
import java.net.SocketPermission;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.Set;
import static java.net.http.HttpRedirectImpl.getRedirects;
import java.util.Locale;

class HttpRequestImpl extends HttpRequest {

    private final HttpHeadersImpl userHeaders;
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

    public HttpRequestImpl(HttpClientImpl client,
                           String method,
                           HttpRequestBuilderImpl builder) {
        this.client = client;
        this.method = method == null? "GET" : method;
        this.userHeaders = builder.headers() == null ?
                new HttpHeadersImpl() : builder.headers();
        dropDisallowedHeaders();
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
        this.userHeaders = other.userHeaders == null ?
                new HttpHeadersImpl() : other.userHeaders;
        dropDisallowedHeaders();
        this.followRedirects = getRedirects(other.followRedirects() == null ?
                client.followRedirects() : other.followRedirects());
        this.systemHeaders = other.systemHeaders;
        this.uri = uri;
        this.expectContinue = other.expectContinue;
        this.secure = other.secure;
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
        this.userHeaders = new HttpHeadersImpl();
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


    @Override
    public String toString() {
        return (uri == null ? "" : uri.toString()) + "/" + method + "("
                + hashCode() + ")";
    }

    @Override
    public HttpHeaders headers() {
        userHeaders.makeUnmodifiable();
        return userHeaders;
    }

    InetSocketAddress authority() { return authority; }

    void setH2Upgrade() {
        Http2ClientImpl h2client = client.client2();
        systemHeaders.setHeader("Connection", "Upgrade, HTTP2-Settings");
        systemHeaders.setHeader("Upgrade", "h2c");
        systemHeaders.setHeader("HTTP2-Settings", h2client.getSettingsString());
    }

    private static final Set<String>  DISALLOWED_HEADERS_SET = Set.of(
        "authorization", "connection", "cookie", "content-length",
        "date", "expect", "from", "host", "origin", "proxy-authorization",
        "referer", "user-agent", "upgrade", "via", "warning");


    // we silently drop headers that are disallowed
    private void dropDisallowedHeaders() {
        Set<String> hdrnames = userHeaders.directMap().keySet();

        hdrnames.removeIf((s) ->
              DISALLOWED_HEADERS_SET.contains(s.toLowerCase())
        );
    }

    private synchronized void receiving() {
        if (receiving) {
            throw new IllegalStateException("already receiving response");
        }
        receiving = true;
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

    public <U> CompletableFuture<U>
    sendAsyncMulti(HttpResponse.MultiProcessor<U> rspproc) {
        // To change body of generated methods, choose Tools | Templates.
        throw new UnsupportedOperationException("Not supported yet.");
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

    HttpHeadersImpl getUserHeaders() { return userHeaders; }

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

    @Override
    public <U> CompletableFuture<U>
    multiResponseAsync(MultiProcessor<U> rspproc) {
        //To change body of generated methods, choose Tools | Templates.
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
