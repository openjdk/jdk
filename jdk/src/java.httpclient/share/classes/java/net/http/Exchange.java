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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketPermission;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLPermission;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * One request/response exchange (handles 100/101 intermediate response also).
 * depth field used to track number of times a new request is being sent
 * for a given API request. If limit exceeded exception is thrown.
 *
 * Security check is performed here:
 * - uses AccessControlContext captured at API level
 * - checks for appropriate URLPermission for request
 * - if permission allowed, grants equivalent SocketPermission to call
 * - in case of direct HTTP proxy, checks additionally for access to proxy
 *    (CONNECT proxying uses its own Exchange, so check done there)
 *
 */
class Exchange {

    final HttpRequestImpl request;
    final HttpClientImpl client;
    ExchangeImpl exchImpl;
    HttpResponseImpl response;
    final List<SocketPermission> permissions = new LinkedList<>();
    AccessControlContext acc;
    boolean upgrading; // to HTTP/2

    Exchange(HttpRequestImpl request) {
        this.request = request;
        this.upgrading = false;
        this.client = request.client();
    }

    /* If different AccessControlContext to be used  */
    Exchange(HttpRequestImpl request, AccessControlContext acc) {
        this.request = request;
        this.acc = acc;
        this.upgrading = false;
        this.client = request.client();
    }

    public HttpRequestImpl request() {
        return request;
    }

    public HttpResponseImpl response() throws IOException, InterruptedException {
        response = responseImpl(null);
        return response;
    }

    public void cancel() {
        if (exchImpl != null)
            exchImpl.cancel();
    }

    public void h2Upgrade() {
        upgrading = true;
        request.setH2Upgrade();
    }

    static final SocketPermission[] SOCKET_ARRAY = new SocketPermission[0];

    HttpResponseImpl responseImpl(HttpConnection connection)
        throws IOException, InterruptedException
    {
        if (acc == null) {
            acc = request.getAccessControlContext();
        }
        SecurityException e = securityCheck(acc);
        if (e != null)
            throw e;

        if (permissions.size() > 0) {
            try {
                return AccessController.doPrivileged(
                        (PrivilegedExceptionAction<HttpResponseImpl>)() ->
                             responseImpl0(connection),
                        null,
                        permissions.toArray(SOCKET_ARRAY));
            } catch (Throwable ee) {
                if (ee instanceof PrivilegedActionException) {
                    ee = ee.getCause();
                }
                if (ee instanceof IOException)
                    throw (IOException)ee;
                else
                    throw new RuntimeException(ee); // TODO: fix
            }
        } else {
            return responseImpl0(connection);
        }
    }

    HttpResponseImpl responseImpl0(HttpConnection connection)
        throws IOException, InterruptedException
    {
        exchImpl = ExchangeImpl.get(this, connection);
        if (request.expectContinue()) {
            request.addSystemHeader("Expect", "100-Continue");
            exchImpl.sendHeadersOnly();
            HttpResponseImpl resp = exchImpl.getResponse();
            logResponse(resp);
            if (resp.statusCode() != 100) {
                return resp;
            }
            exchImpl.sendBody();
            return exchImpl.getResponse();
        } else {
            exchImpl.sendRequest();
            HttpResponseImpl resp = exchImpl.getResponse();
            logResponse(resp);
            return checkForUpgrade(resp, exchImpl);
        }
    }

    // Completed HttpResponse will be null if response succeeded
    // will be a non null responseAsync if expect continue returns an error

    public CompletableFuture<HttpResponseImpl> responseAsync(Void v) {
        return responseAsyncImpl(null);
    }

    CompletableFuture<HttpResponseImpl> responseAsyncImpl(HttpConnection connection) {
        if (acc == null) {
            acc = request.getAccessControlContext();
        }
        SecurityException e = securityCheck(acc);
        if (e != null) {
            CompletableFuture<HttpResponseImpl> cf = new CompletableFuture<>();
            cf.completeExceptionally(e);
            return cf;
        }
        if (permissions.size() > 0) {
            return AccessController.doPrivileged(
                    (PrivilegedAction<CompletableFuture<HttpResponseImpl>>)() ->
                        responseAsyncImpl0(connection),
                    null,
                    permissions.toArray(SOCKET_ARRAY));
        } else {
            return responseAsyncImpl0(connection);
        }
    }

    CompletableFuture<HttpResponseImpl> responseAsyncImpl0(HttpConnection connection) {
        try {
            exchImpl = ExchangeImpl.get(this, connection);
        } catch (IOException | InterruptedException e) {
            CompletableFuture<HttpResponseImpl> cf = new CompletableFuture<>();
            cf.completeExceptionally(e);
            return cf;
        }
        if (request.expectContinue()) {
            request.addSystemHeader("Expect", "100-Continue");
            return exchImpl.sendHeadersAsync()
                    .thenCompose(exchImpl::getResponseAsync)
                    .thenCompose((HttpResponseImpl r1) -> {
                        int rcode = r1.statusCode();
                        CompletableFuture<HttpResponseImpl> cf =
                                checkForUpgradeAsync(r1, exchImpl);
                        if (cf != null)
                            return cf;
                        if (rcode == 100) {
                            return exchImpl.sendBodyAsync()
                                .thenCompose(exchImpl::getResponseAsync)
                                .thenApply((r) -> {
                                    logResponse(r);
                                    return r;
                                });
                        } else {
                            Exchange.this.response = r1;
                            logResponse(r1);
                            return CompletableFuture.completedFuture(r1);
                        }
                    });
        } else {
            return exchImpl
                .sendHeadersAsync()
                .thenCompose((Void v) -> {
                    // send body and get response at same time
                    return exchImpl.sendBodyAsync()
                                   .thenCompose(exchImpl::getResponseAsync);
                })
                .thenCompose((HttpResponseImpl r1) -> {
                    int rcode = r1.statusCode();
                    CompletableFuture<HttpResponseImpl> cf =
                            checkForUpgradeAsync(r1, exchImpl);
                    if (cf != null) {
                        return cf;
                    } else {
                        Exchange.this.response = r1;
                        logResponse(r1);
                        return CompletableFuture.completedFuture(r1);
                    }
                })
                .thenApply((HttpResponseImpl response) -> {
                    this.response = response;
                    logResponse(response);
                    return response;
                });
        }
    }

    // if this response was received in reply to an upgrade
    // then create the Http2Connection from the HttpConnection
    // initialize it and wait for the real response on a newly created Stream

    private CompletableFuture<HttpResponseImpl>
    checkForUpgradeAsync(HttpResponseImpl resp,
                         ExchangeImpl ex) {
        int rcode = resp.statusCode();
        if (upgrading && (rcode == 101)) {
            Http1Exchange e = (Http1Exchange)ex;
            // check for 101 switching protocols
            return e.responseBodyAsync(HttpResponse.ignoreBody())
                .thenCompose((Void v) ->
                     Http2Connection.createAsync(e.connection(),
                                                 client.client2(),
                                                 this)
                        .thenCompose((Http2Connection c) -> {
                            Stream s = c.getStream(1);
                            exchImpl = s;
                            c.putConnection();
                            return s.getResponseAsync(null);
                        })
                );
        }
        return CompletableFuture.completedFuture(resp);
    }

    private HttpResponseImpl checkForUpgrade(HttpResponseImpl resp,
                                             ExchangeImpl ex)
        throws IOException, InterruptedException
    {
        int rcode = resp.statusCode();
        if (upgrading && (rcode == 101)) {
            Http1Exchange e = (Http1Exchange) ex;
            // must get connection from Http1Exchange
            e.responseBody(HttpResponse.ignoreBody(), false);
            Http2Connection h2con = new Http2Connection(e.connection(),
                                                        client.client2(),
                                                        this);
            h2con.putConnection();
            Stream s = h2con.getStream(1);
            exchImpl = s;
            return s.getResponse();
        }
        return resp;
    }


    <T> T responseBody(HttpResponse.BodyProcessor<T> processor) {
        try {
            return exchImpl.responseBody(processor);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private void logResponse(HttpResponseImpl r) {
        if (!Log.requests())
            return;
        StringBuilder sb = new StringBuilder();
        String method = r.request().method();
        URI uri = r.uri();
        String uristring = uri == null ? "" : uri.toString();
        sb.append('(')
          .append(method)
          .append(" ")
          .append(uristring)
          .append(") ")
          .append(Integer.toString(r.statusCode()));
        Log.logResponse(sb.toString());
    }

    <T> CompletableFuture<T> responseBodyAsync(HttpResponse.BodyProcessor<T> processor) {
        return exchImpl.responseBodyAsync(processor);
    }

    private URI getURIForSecurityCheck() {
        URI u;
        String method = request.method();
        InetSocketAddress authority = request.authority();
        URI uri = request.uri();

        // CONNECT should be restricted at API level
        if (method.equalsIgnoreCase("CONNECT")) {
            try {
                u = new URI("socket",
                             null,
                             authority.getHostString(),
                             authority.getPort(),
                             null,
                             null,
                             null);
            } catch (URISyntaxException e) {
                throw new InternalError(e); // shouldn't happen
            }
        } else {
            u = uri;
        }
        return u;
    }

    /**
     * Do the security check and return any exception.
     * Return null if no check needed or passes.
     *
     * Also adds any generated permissions to the "permissions" list.
     */
    private SecurityException securityCheck(AccessControlContext acc) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return null;
        }

        String method = request.method();
        HttpHeadersImpl userHeaders = request.getUserHeaders();
        URI u = getURIForSecurityCheck();
        URLPermission p = Utils.getPermission(u, method, userHeaders.directMap());

        try {
            assert acc != null;
            sm.checkPermission(p, acc);
            permissions.add(getSocketPermissionFor(u));
        } catch (SecurityException e) {
            return e;
        }
        InetSocketAddress proxy = request.proxy();
        if (proxy != null) {
            // may need additional check
            if (!method.equals("CONNECT")) {
                // a direct http proxy. Need to check access to proxy
                try {
                    u = new URI("socket", null, proxy.getHostString(),
                        proxy.getPort(), null, null, null);
                } catch (URISyntaxException e) {
                    throw new InternalError(e); // shouldn't happen
                }
                p = new URLPermission(u.toString(), "CONNECT");
                try {
                    sm.checkPermission(p, acc);
                } catch (SecurityException e) {
                    permissions.clear();
                    return e;
                }
                String sockperm = proxy.getHostString() +
                        ":" + Integer.toString(proxy.getPort());

                permissions.add(new SocketPermission(sockperm, "connect,resolve"));
            }
        }
        return null;
    }

    private static SocketPermission getSocketPermissionFor(URI url) {
        if (System.getSecurityManager() == null)
            return null;

        StringBuilder sb = new StringBuilder();
        String host = url.getHost();
        sb.append(host);
        int port = url.getPort();
        if (port == -1) {
            String scheme = url.getScheme();
            if ("http".equals(scheme)) {
                sb.append(":80");
            } else { // scheme must be https
                sb.append(":443");
            }
        } else {
            sb.append(':')
              .append(Integer.toString(port));
        }
        String target = sb.toString();
        return new SocketPermission(target, "connect");
    }

    AccessControlContext getAccessControlContext() {
        return acc;
    }
}
