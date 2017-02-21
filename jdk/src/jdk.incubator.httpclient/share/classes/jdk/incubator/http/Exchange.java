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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketPermission;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLPermission;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;
import jdk.incubator.http.internal.common.Log;

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
final class Exchange<T> {

    final HttpRequestImpl request;
    final HttpClientImpl client;
    volatile ExchangeImpl<T> exchImpl;
    final List<SocketPermission> permissions = new LinkedList<>();
    final AccessControlContext acc;
    final MultiExchange<?,T> multi;
    final Executor parentExecutor;
    final HttpRequest.BodyProcessor requestProcessor;
    boolean upgrading; // to HTTP/2
    final PushGroup<?,T> pushGroup;

    // buffer for receiving response headers
    private volatile ByteBuffer rxBuffer;

    Exchange(HttpRequestImpl request, MultiExchange<?,T> multi) {
        this.request = request;
        this.upgrading = false;
        this.client = multi.client();
        this.multi = multi;
        this.acc = multi.acc;
        this.parentExecutor = multi.executor;
        this.requestProcessor = request.requestProcessor;
        this.pushGroup = multi.pushGroup;
    }

    /* If different AccessControlContext to be used  */
    Exchange(HttpRequestImpl request,
             MultiExchange<?,T> multi,
             AccessControlContext acc)
    {
        this.request = request;
        this.acc = acc;
        this.upgrading = false;
        this.client = multi.client();
        this.multi = multi;
        this.parentExecutor = multi.executor;
        this.requestProcessor = request.requestProcessor;
        this.pushGroup = multi.pushGroup;
    }

    PushGroup<?,T> getPushGroup() {
        return pushGroup;
    }

    Executor executor() {
        return parentExecutor;
    }

    public HttpRequestImpl request() {
        return request;
    }

    HttpClientImpl client() {
        return client;
    }

    ByteBuffer getBuffer() {
        if(rxBuffer == null) {
            synchronized (this) {
                if(rxBuffer == null) {
                    rxBuffer = Utils.getExchangeBuffer();
                }
            }
        }
        return rxBuffer;
    }

    public Response response() throws IOException, InterruptedException {
        return responseImpl(null);
    }

    public T readBody(HttpResponse.BodyHandler<T> responseHandler) throws IOException {
        return exchImpl.readBody(responseHandler, true);
    }

    public CompletableFuture<T> readBodyAsync(HttpResponse.BodyHandler<T> handler) {
        return exchImpl.readBodyAsync(handler, true, parentExecutor);
    }

    public void cancel() {
        if (exchImpl != null) {
            exchImpl.cancel();
        }
    }

    public void cancel(IOException cause) {
        if (exchImpl != null) {
            exchImpl.cancel(cause);
        }
    }

    public void h2Upgrade() {
        upgrading = true;
        request.setH2Upgrade(client.client2());
    }

    static final SocketPermission[] SOCKET_ARRAY = new SocketPermission[0];

    Response responseImpl(HttpConnection connection)
        throws IOException, InterruptedException
    {
        SecurityException e = securityCheck(acc);
        if (e != null) {
            throw e;
        }

        if (permissions.size() > 0) {
            try {
                return AccessController.doPrivileged(
                        (PrivilegedExceptionAction<Response>)() ->
                             responseImpl0(connection),
                        null,
                        permissions.toArray(SOCKET_ARRAY));
            } catch (Throwable ee) {
                if (ee instanceof PrivilegedActionException) {
                    ee = ee.getCause();
                }
                if (ee instanceof IOException) {
                    throw (IOException) ee;
                } else {
                    throw new RuntimeException(ee); // TODO: fix
                }
            }
        } else {
            return responseImpl0(connection);
        }
    }

    private Response responseImpl0(HttpConnection connection)
        throws IOException, InterruptedException
    {
        exchImpl = ExchangeImpl.get(this, connection);
        exchImpl.setClientForRequest(requestProcessor);
        if (request.expectContinue()) {
            Log.logTrace("Sending Expect: 100-Continue");
            request.addSystemHeader("Expect", "100-Continue");
            exchImpl.sendHeadersOnly();

            Log.logTrace("Waiting for 407-Expectation-Failed or 100-Continue");
            Response resp = exchImpl.getResponse();
            HttpResponseImpl.logResponse(resp);
            int rcode = resp.statusCode();
            if (rcode != 100) {
                Log.logTrace("Expectation failed: Received {0}",
                             rcode);
                if (upgrading && rcode == 101) {
                    throw new IOException(
                        "Unable to handle 101 while waiting for 100-Continue");
                }
                return resp;
            }

            Log.logTrace("Received 100-Continue: sending body");
            exchImpl.sendBody();

            Log.logTrace("Body sent: waiting for response");
            resp = exchImpl.getResponse();
            HttpResponseImpl.logResponse(resp);

            return checkForUpgrade(resp, exchImpl);
        } else {
            exchImpl.sendHeadersOnly();
            exchImpl.sendBody();
            Response resp = exchImpl.getResponse();
            HttpResponseImpl.logResponse(resp);
            return checkForUpgrade(resp, exchImpl);
        }
    }

    // Completed HttpResponse will be null if response succeeded
    // will be a non null responseAsync if expect continue returns an error

    public CompletableFuture<Response> responseAsync() {
        return responseAsyncImpl(null);
    }

    CompletableFuture<Response> responseAsyncImpl(HttpConnection connection) {
        SecurityException e = securityCheck(acc);
        if (e != null) {
            return MinimalFuture.failedFuture(e);
        }
        if (permissions.size() > 0) {
            return AccessController.doPrivileged(
                    (PrivilegedAction<CompletableFuture<Response>>)() ->
                        responseAsyncImpl0(connection),
                    null,
                    permissions.toArray(SOCKET_ARRAY));
        } else {
            return responseAsyncImpl0(connection);
        }
    }

    CompletableFuture<Response> responseAsyncImpl0(HttpConnection connection) {
        try {
            exchImpl = ExchangeImpl.get(this, connection);
        } catch (IOException | InterruptedException e) {
            return MinimalFuture.failedFuture(e);
        }
        if (request.expectContinue()) {
            request.addSystemHeader("Expect", "100-Continue");
            Log.logTrace("Sending Expect: 100-Continue");
            return exchImpl
                    .sendHeadersAsync()
                    .thenCompose(v -> exchImpl.getResponseAsync(parentExecutor))
                    .thenCompose((Response r1) -> {
                        HttpResponseImpl.logResponse(r1);
                        int rcode = r1.statusCode();
                        if (rcode == 100) {
                            Log.logTrace("Received 100-Continue: sending body");
                            CompletableFuture<Response> cf =
                                    exchImpl.sendBodyAsync()
                                            .thenCompose(exIm -> exIm.getResponseAsync(parentExecutor));
                            cf = wrapForUpgrade(cf);
                            cf = wrapForLog(cf);
                            return cf;
                        } else {
                            Log.logTrace("Expectation failed: Received {0}",
                                         rcode);
                            if (upgrading && rcode == 101) {
                                IOException failed = new IOException(
                                        "Unable to handle 101 while waiting for 100");
                                return MinimalFuture.failedFuture(failed);
                            }
                            return exchImpl.readBodyAsync(this::ignoreBody, false, parentExecutor)
                                  .thenApply(v ->  r1);
                        }
                    });
        } else {
            CompletableFuture<Response> cf = exchImpl
                    .sendHeadersAsync()
                    .thenCompose(ExchangeImpl::sendBodyAsync)
                    .thenCompose(exIm -> exIm.getResponseAsync(parentExecutor));
            cf = wrapForUpgrade(cf);
            cf = wrapForLog(cf);
            return cf;
        }
    }

    private CompletableFuture<Response> wrapForUpgrade(CompletableFuture<Response> cf) {
        if (upgrading) {
            return cf.thenCompose(r -> checkForUpgradeAsync(r, exchImpl));
        }
        return cf;
    }

    private CompletableFuture<Response> wrapForLog(CompletableFuture<Response> cf) {
        if (Log.requests()) {
            return cf.thenApply(response -> {
                HttpResponseImpl.logResponse(response);
                return response;
            });
        }
        return cf;
    }

    HttpResponse.BodyProcessor<T> ignoreBody(int status, HttpHeaders hdrs) {
        return HttpResponse.BodyProcessor.discard((T)null);
    }

    // if this response was received in reply to an upgrade
    // then create the Http2Connection from the HttpConnection
    // initialize it and wait for the real response on a newly created Stream

    private CompletableFuture<Response>
    checkForUpgradeAsync(Response resp,
                         ExchangeImpl<T> ex) {

        int rcode = resp.statusCode();
        if (upgrading && (rcode == 101)) {
            Http1Exchange<T> e = (Http1Exchange<T>)ex;
            // check for 101 switching protocols
            // 101 responses are not supposed to contain a body.
            //    => should we fail if there is one?
            return e.readBodyAsync(this::ignoreBody, false, parentExecutor)
                .thenCompose((T v) -> // v is null
                     Http2Connection.createAsync(e.connection(),
                                                 client.client2(),
                                                 this, e.getBuffer())
                        .thenCompose((Http2Connection c) -> {
                            c.putConnection();
                            Stream<T> s = c.getStream(1);
                            exchImpl = s;
                            return s.getResponseAsync(null);
                        })
                );
        }
        return MinimalFuture.completedFuture(resp);
    }

    private Response checkForUpgrade(Response resp,
                                             ExchangeImpl<T> ex)
        throws IOException, InterruptedException
    {
        int rcode = resp.statusCode();
        if (upgrading && (rcode == 101)) {
            Http1Exchange<T> e = (Http1Exchange<T>) ex;

            // 101 responses are not supposed to contain a body.
            //    => should we fail if there is one?
            //    => readBody called here by analogy with
            //       checkForUpgradeAsync above
            e.readBody(this::ignoreBody, false);

            // must get connection from Http1Exchange
            Http2Connection h2con = new Http2Connection(e.connection(),
                                                        client.client2(),
                                                        this, e.getBuffer());
            h2con.putConnection();
            Stream<T> s = h2con.getStream(1);
            exchImpl = s;
            Response xx = s.getResponse();
            HttpResponseImpl.logResponse(xx);
            return xx;
        }
        return resp;
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
        HttpHeaders userHeaders = request.getUserHeaders();
        URI u = getURIForSecurityCheck();
        URLPermission p = Utils.getPermission(u, method, userHeaders.map());

        try {
            assert acc != null;
            sm.checkPermission(p, acc);
            permissions.add(getSocketPermissionFor(u));
        } catch (SecurityException e) {
            return e;
        }
        ProxySelector ps = client.proxy().orElse(null);
        if (ps != null) {
            InetSocketAddress proxy = (InetSocketAddress)
                    ps.select(u).get(0).address(); // TODO: check this
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

    HttpClient.Redirect followRedirects() {
        return client.followRedirects();
    }

    HttpClient.Version version() {
        return client.version();
    }

    private static SocketPermission getSocketPermissionFor(URI url) {
        if (System.getSecurityManager() == null) {
            return null;
        }

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
