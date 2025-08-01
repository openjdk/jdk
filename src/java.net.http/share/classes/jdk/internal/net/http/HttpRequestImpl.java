/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.util.function.BiPredicate;

import jdk.internal.net.http.common.Alpns;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.websocket.WebSocketRequest;

import static java.net.Authenticator.RequestorType.SERVER;
import static jdk.internal.net.http.common.Utils.ALLOWED_HEADERS;
import static jdk.internal.net.http.common.Utils.ProxyHeaders;
import static jdk.internal.net.http.common.Utils.copyProxy;

public class HttpRequestImpl extends HttpRequest implements WebSocketRequest {

    private final HttpHeaders userHeaders;
    private final HttpHeadersBuilder systemHeadersBuilder;
    private final URI uri;
    private volatile Proxy proxy; // ensure safe publishing
    private final InetSocketAddress authority; // only used when URI not specified
    private final String method;
    final BodyPublisher requestPublisher;
    final boolean secure;
    final boolean expectContinue;
    private volatile boolean isWebSocket;
    private final Duration timeout;  // may be null
    private final Optional<HttpClient.Version> version;
    private volatile boolean userSetAuthorization;
    private volatile boolean userSetProxyAuthorization;

    private static String userAgent() {
        String version = System.getProperty("java.version");
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
        this.userHeaders = HttpHeaders.of(builder.headersBuilder().map(), ALLOWED_HEADERS);
        this.systemHeadersBuilder = new HttpHeadersBuilder();
        this.uri = builder.uri();
        assert uri != null;
        this.proxy = null;
        this.expectContinue = builder.expectContinue();
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.requestPublisher = builder.bodyPublisher();  // may be null
        this.timeout = builder.timeout();
        this.version = builder.version();
        this.authority = null;
    }

    /**
     * Creates an HttpRequestImpl from the given request.
     */
    public HttpRequestImpl(HttpRequest request, ProxySelector ps) {
        String method = request.method();
        if (method != null && !Utils.isValidName(method))
            throw new IllegalArgumentException("illegal method \""
                    + method.replace("\n","\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    + "\"");
        URI requestURI = Objects.requireNonNull(request.uri(),
                "uri must be non null");
        Duration timeout = request.timeout().orElse(null);
        this.method = method == null ? "GET" : method;
        this.userHeaders = HttpHeaders.of(request.headers().map(), Utils.VALIDATE_USER_HEADER);
        if (request instanceof HttpRequestImpl) {
            // all cases exception WebSocket should have a new system headers
            this.isWebSocket = ((HttpRequestImpl) request).isWebSocket;
            if (isWebSocket) {
                this.systemHeadersBuilder = ((HttpRequestImpl)request).systemHeadersBuilder;
            } else {
                this.systemHeadersBuilder = new HttpHeadersBuilder();
            }
        } else {
            HttpRequestBuilderImpl.checkURI(requestURI);
            checkTimeout(timeout);
            this.systemHeadersBuilder = new HttpHeadersBuilder();
        }
        if (userHeaders.firstValue("User-Agent").isEmpty()) {
            this.systemHeadersBuilder.setHeader("User-Agent", USER_AGENT);
        }
        this.uri = requestURI;
        this.proxy = retrieveProxy(request, ps, uri);
        this.expectContinue = request.expectContinue();
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.requestPublisher = request.bodyPublisher().orElse(null);
        this.timeout = timeout;
        this.version = request.version();
        this.authority = null;
    }

    private static void checkTimeout(Duration duration) {
        if (duration != null) {
            if (duration.isNegative() || Duration.ZERO.equals(duration))
                throw new IllegalArgumentException("Invalid duration: " + duration);
        }
    }

    /** Returns a new instance suitable for redirection. */
    public static HttpRequestImpl newInstanceForRedirection(URI uri,
                                                            String method,
                                                            HttpRequestImpl other,
                                                            boolean mayHaveBody) {
        if (uri.getScheme().equalsIgnoreCase(other.uri.getScheme()) &&
                uri.getRawAuthority().equals(other.uri.getRawAuthority())) {
            return new HttpRequestImpl(uri, method, other, mayHaveBody, Optional.empty());
        }
        return new HttpRequestImpl(uri, method, other, mayHaveBody, Optional.of(Utils.ALLOWED_REDIRECT_HEADERS));
    }

    /** Returns a new instance suitable for authentication. */
    public static HttpRequestImpl newInstanceForAuthentication(HttpRequestImpl other) {
        HttpRequestImpl request = new HttpRequestImpl(other.uri(), other.method(), other, true);
        if (request.isWebSocket()) {
            Utils.setWebSocketUpgradeHeaders(request);
        }
        return request;
    }

    /**
     * Creates a HttpRequestImpl using fields of an existing request impl.
     * The newly created HttpRequestImpl does not copy the system headers.
     */
    private HttpRequestImpl(URI uri,
                            String method,
                            HttpRequestImpl other,
                            boolean mayHaveBody) {
        this(uri, method, other, mayHaveBody, Optional.empty());
    }

    private HttpRequestImpl(URI uri,
                            String method,
                            HttpRequestImpl other,
                            boolean mayHaveBody,
                            Optional<BiPredicate<String, String>> redirectHeadersFilter) {
        assert method == null || Utils.isValidName(method);
        this.method = method == null ? "GET" : method;
        HttpHeaders userHeaders = redirectHeadersFilter.isPresent() ?
                HttpHeaders.of(other.userHeaders.map(), redirectHeadersFilter.get()) : other.userHeaders;
        this.userHeaders = userHeaders;
        this.isWebSocket = other.isWebSocket;
        this.systemHeadersBuilder = new HttpHeadersBuilder();
        if (userHeaders.firstValue("User-Agent").isEmpty()) {
            this.systemHeadersBuilder.setHeader("User-Agent", USER_AGENT);
        }
        this.uri = uri;
        this.proxy = other.proxy;
        this.expectContinue = other.expectContinue;
        this.secure = uri.getScheme().toLowerCase(Locale.US).equals("https");
        this.requestPublisher = mayHaveBody ? publisher(other) : null; // may be null
        this.timeout = other.timeout;
        this.version = other.version();
        this.authority = null;
    }

    private BodyPublisher publisher(HttpRequestImpl other) {
        BodyPublisher res = other.requestPublisher;
        if (!Objects.equals(method, other.method)) {
            res = null;
        }
        return res;
    }

    /* used for creating CONNECT requests  */
    HttpRequestImpl(String method, InetSocketAddress authority, ProxyHeaders headers) {
        // TODO: isWebSocket flag is not specified, but the assumption is that
        // such a request will never be made on a connection that will be returned
        // to the connection pool (we might need to revisit this constructor later)
        assert "CONNECT".equalsIgnoreCase(method);
        this.method = method;
        this.systemHeadersBuilder = new HttpHeadersBuilder();
        this.systemHeadersBuilder.map().putAll(headers.systemHeaders().map());
        this.userHeaders = headers.userHeaders();
        this.uri = URI.create("socket://" + authority.getHostString() + ":"
                              + authority.getPort() + "/");
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

    final boolean isConnect() {
        return "CONNECT".equalsIgnoreCase(method);
    }

    /**
     * Creates a HttpRequestImpl from the given set of Headers and the associated
     * "parent" request. Fields not taken from the headers are taken from the
     * parent.
     */
    static HttpRequestImpl createPushRequest(HttpRequestImpl parent,
                                             HttpHeaders headers)
        throws IOException
    {
        return new HttpRequestImpl(parent, headers);
    }

    // only used for push requests
    private HttpRequestImpl(HttpRequestImpl parent, HttpHeaders headers)
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
        this.userHeaders = HttpHeaders.of(headers.map(), ALLOWED_HEADERS);
        this.systemHeadersBuilder = parent.systemHeadersBuilder;
        this.expectContinue = parent.expectContinue;
        this.secure = parent.secure;
        this.requestPublisher = parent.requestPublisher;
        this.timeout = parent.timeout;
        this.version = parent.version;
        this.authority = null;
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

    void setH2Upgrade(Exchange<?> exchange) {
        systemHeadersBuilder.setHeader("Connection", "Upgrade, HTTP2-Settings");
        systemHeadersBuilder.setHeader("Upgrade", Alpns.H2C);
        systemHeadersBuilder.setHeader("HTTP2-Settings", exchange.h2cSettingsStrings());
    }

    @Override
    public boolean expectContinue() { return expectContinue; }

    /** Retrieves a copy of the proxy either from the given {@link HttpRequest} or {@link ProxySelector}, if there is one. */
    private static Proxy retrieveProxy(HttpRequest request, ProxySelector ps, URI uri) {

        // WebSocket determines and sets the proxy itself
        if (request instanceof HttpRequestImpl requestImpl && requestImpl.isWebSocket) {
            return requestImpl.proxy;
        }

        // Try to find a matching one from the `ProxySelector`
        if (ps != null) {
            List<Proxy> pl = ps.select(uri);
            if (!pl.isEmpty()) {
                Proxy p = pl.getFirst();
                if (p.type() == Proxy.Type.HTTP) {
                    return copyProxy(p);
                }
            }
        }

        return null;

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
        this.proxy = copyProxy(proxy);
    }

    @Override
    public void isWebSocket(boolean is) {
        isWebSocket = is;
    }

    boolean isWebSocket() {
        return isWebSocket;
    }

    /**
     * These flags are set if the user set an Authorization or Proxy-Authorization header
     * overriding headers produced by an Authenticator that was also set
     *
     * The values are checked in the AuthenticationFilter which tells the library
     * to return whatever response received to the user instead of causing request
     * to be resent, in case of error.
     */
    public void setUserSetAuthFlag(Authenticator.RequestorType type, boolean value) {
        if (type == SERVER) {
            userSetAuthorization = value;
        } else {
            userSetProxyAuthorization = value;
        }
    }

    public boolean getUserSetAuthFlag(Authenticator.RequestorType type) {
        if (type == SERVER) {
            return userSetAuthorization;
        } else {
            return userSetProxyAuthorization;
        }
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

    HttpHeadersBuilder getSystemHeadersBuilder() { return systemHeadersBuilder; }

    @Override
    public Optional<HttpClient.Version> version() { return version; }

    @Override
    public void setSystemHeader(String name, String value) {
        systemHeadersBuilder.setHeader(name, value);
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
            return new InetSocketAddress(host, port);
        } else {
            return InetSocketAddress.createUnresolved(host, port);
        }
    }
}
