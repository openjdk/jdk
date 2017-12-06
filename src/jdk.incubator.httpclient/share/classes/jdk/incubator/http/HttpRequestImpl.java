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

import jdk.incubator.http.internal.common.HttpHeadersImpl;
import jdk.incubator.http.internal.websocket.WebSocketRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static jdk.incubator.http.internal.common.Utils.ALLOWED_HEADERS;

class HttpRequestImpl extends HttpRequest implements WebSocketRequest {

    private final HttpHeaders userHeaders;
    private final HttpHeadersImpl systemHeaders;
    private final URI uri;
    private Proxy proxy;
    private InetSocketAddress authority; // only used when URI not specified
    private final String method;
    final BodyPublisher requestPublisher;
    final boolean secure;
    final boolean expectContinue;
    private boolean isWebSocket;
    private AccessControlContext acc;
    private final Duration timeout;  // may be null
    private final Optional<HttpClient.Version> version;

    private static String userAgent() {
        PrivilegedAction<String> pa = () -> System.getProperty("java.version");
        String version = AccessController.doPrivileged(pa);
        return "Java-http-client/" + version;
    }

    /** The value of the User-Agent header for all requests sent by the client. */
    public static final String USER_AGENT = userAgent();

    /**
     * Creates an HttpRequestImpl from the given builder.
     */
    public HttpRequestImpl(HttpRequestBuilderImpl builder) {
        String method = builder.method();
        this.method = method == null ? "GET" : method;
        this.userHeaders = ImmutableHeaders.of(builder.headers().map(), ALLOWED_HEADERS);
        this.systemHeaders = new HttpHeadersImpl();
        this.uri = builder.uri();
        assert uri != null;
        this.proxy = null;
        this.expectContinue = builder.expectContinue();
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.requestPublisher = builder.bodyPublisher();  // may be null
        this.timeout = builder.timeout();
        this.version = builder.version();
    }

    /**
     * Creates an HttpRequestImpl from the given request.
     */
    public HttpRequestImpl(HttpRequest request, ProxySelector ps, AccessControlContext acc) {
        String method = request.method();
        this.method = method == null ? "GET" : method;
        this.userHeaders = request.headers();
        if (request instanceof HttpRequestImpl) {
            this.systemHeaders = ((HttpRequestImpl) request).systemHeaders;
            this.isWebSocket = ((HttpRequestImpl) request).isWebSocket;
        } else {
            this.systemHeaders = new HttpHeadersImpl();
        }
        this.systemHeaders.setHeader("User-Agent", USER_AGENT);
        this.uri = request.uri();
        if (isWebSocket) {
            // WebSocket determines and sets the proxy itself
            this.proxy = ((HttpRequestImpl) request).proxy;
        } else {
            if (ps != null)
                this.proxy = retrieveProxy(ps, uri);
            else
                this.proxy = null;
        }
        this.expectContinue = request.expectContinue();
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.requestPublisher = request.bodyPublisher().orElse(null);
        if (acc != null && requestPublisher instanceof RequestPublishers.FilePublisher) {
            // Restricts the file publisher with the senders ACC, if any
            ((RequestPublishers.FilePublisher)requestPublisher).setAccessControlContext(acc);
        }
        this.timeout = request.timeout().orElse(null);
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
        this.proxy = other.proxy;
        this.expectContinue = other.expectContinue;
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.requestPublisher = other.requestPublisher;  // may be null
        this.acc = other.acc;
        this.timeout = other.timeout;
        this.version = other.version();
    }

    /* used for creating CONNECT requests  */
    HttpRequestImpl(String method, InetSocketAddress authority) {
        // TODO: isWebSocket flag is not specified, but the assumption is that
        // such a request will never be made on a connection that will be returned
        // to the connection pool (we might need to revisit this constructor later)
        this.method = method;
        this.systemHeaders = new HttpHeadersImpl();
        this.userHeaders = ImmutableHeaders.empty();
        this.uri = URI.create("socket://" + authority.getHostString() + ":"
                              + Integer.toString(authority.getPort()) + "/");
        this.proxy = null;
        this.requestPublisher = null;
        this.authority = authority;
        this.secure = false;
        this.expectContinue = false;
        this.timeout = null;
        // The CONNECT request sent for tunneling is only used in two cases:
        //   1. websocket, which only supports HTTP/1.1
        //   2. SSL tunneling through a HTTP/1.1 proxy
        // In either case we do not want to upgrade the connection to the proxy.
        // What we want to possibly upgrade is the tunneled connection to the
        // target server (so not the CONNECT request itself)
        this.version = Optional.of(HttpClient.Version.HTTP_1_1);
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
        this.proxy = null;
        this.userHeaders = ImmutableHeaders.of(headers.map(), ALLOWED_HEADERS);
        this.systemHeaders = parent.systemHeaders;
        this.expectContinue = parent.expectContinue;
        this.secure = parent.secure;
        this.requestPublisher = parent.requestPublisher;
        this.acc = parent.acc;
        this.timeout = parent.timeout;
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

    /** Retrieves the proxy, from the given ProxySelector, if there is one. */
    private static Proxy retrieveProxy(ProxySelector ps, URI uri) {
        Proxy proxy = null;
        List<Proxy> pl = ps.select(uri);
        if (!pl.isEmpty()) {
            Proxy p = pl.get(0);
            if (p.type() == Proxy.Type.HTTP)
                proxy = p;
        }
        return proxy;
    }

    InetSocketAddress proxy() {
        if (proxy == null || proxy.type() != Proxy.Type.HTTP
                || method.equalsIgnoreCase("CONNECT")) {
            return null;
        }
        return (InetSocketAddress)proxy.address();
    }

    boolean secure() { return secure; }

    @Override
    public void setProxy(Proxy proxy) {
        assert isWebSocket;
        this.proxy = proxy;
    }

    @Override
    public void isWebSocket(boolean is) {
        isWebSocket = is;
    }

    boolean isWebSocket() {
        return isWebSocket;
    }

    @Override
    public Optional<BodyPublisher> bodyPublisher() {
        return requestPublisher == null ? Optional.empty()
                                        : Optional.of(requestPublisher);
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
    public Optional<Duration> timeout() {
        return timeout == null ? Optional.empty() : Optional.of(timeout);
    }

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

    InetSocketAddress getAddress() {
        URI uri = uri();
        if (uri == null) {
            return authority();
        }
        int p = uri.getPort();
        if (p == -1) {
            if (uri.getScheme().equalsIgnoreCase("https")) {
                p = 443;
            } else {
                p = 80;
            }
        }
        final String host = uri.getHost();
        final int port = p;
        if (proxy() == null) {
            PrivilegedAction<InetSocketAddress> pa = () -> new InetSocketAddress(host, port);
            return AccessController.doPrivileged(pa);
        } else {
            return InetSocketAddress.createUnresolved(host, port);
        }
    }
}
