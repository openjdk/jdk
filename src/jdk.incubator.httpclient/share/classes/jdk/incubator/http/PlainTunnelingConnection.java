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

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import jdk.incubator.http.internal.common.FlowTube;
import jdk.incubator.http.internal.common.MinimalFuture;
import static jdk.incubator.http.HttpResponse.BodyHandler.discard;

/**
 * A plain text socket tunnel through a proxy. Uses "CONNECT" but does not
 * encrypt. Used by WebSocket, as well as HTTP over SSL + Proxy.
 * Wrapped in SSLTunnelConnection or AsyncSSLTunnelConnection for encryption.
 */
final class PlainTunnelingConnection extends HttpConnection {

    final PlainHttpConnection delegate;
    protected final InetSocketAddress proxyAddr;
    private volatile boolean connected;

    protected PlainTunnelingConnection(InetSocketAddress addr,
                                       InetSocketAddress proxy,
                                       HttpClientImpl client) {
        super(addr, client);
        this.proxyAddr = proxy;
        delegate = new PlainHttpConnection(proxy, client);
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        debug.log(Level.DEBUG, "Connecting plain connection");
        return delegate.connectAsync()
            .thenCompose((Void v) -> {
                debug.log(Level.DEBUG, "sending HTTP/1.1 CONNECT");
                HttpClientImpl client = client();
                assert client != null;
                HttpRequestImpl req = new HttpRequestImpl("CONNECT", address);
                MultiExchange<Void,Void> mulEx = new MultiExchange<>(null, req, client, discard(null), null);
                Exchange<Void> connectExchange = new Exchange<>(req, mulEx);

                return connectExchange
                        .responseAsyncImpl(delegate)
                        .thenCompose((Response resp) -> {
                            CompletableFuture<Void> cf = new MinimalFuture<>();
                            debug.log(Level.DEBUG, "got response: %d", resp.statusCode());
                            if (resp.statusCode() != 200) {
                                cf.completeExceptionally(new IOException(
                                        "Tunnel failed, got: "+ resp.statusCode()));
                            } else {
                                // get the initial/remaining bytes
                                ByteBuffer b = ((Http1Exchange<?>)connectExchange.exchImpl).drainLeftOverBytes();
                                int remaining = b.remaining();
                                assert remaining == 0: "Unexpected remaining: " + remaining;
                                connected = true;
                                cf.complete(null);
                            }
                            return cf;
                        });
            });
    }

    @Override
    HttpPublisher publisher() { return delegate.publisher(); }

    @Override
    boolean connected() {
        return connected;
    }

    @Override
    SocketChannel channel() {
        return delegate.channel();
    }

    @Override
    FlowTube getConnectionFlow() {
        return delegate.getConnectionFlow();
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return new ConnectionPool.CacheKey(null, proxyAddr);
    }

    @Override
    public void close() {
        delegate.close();
        connected = false;
    }

    @Override
    void shutdownInput() throws IOException {
        delegate.shutdownInput();
    }

    @Override
    void shutdownOutput() throws IOException {
        delegate.shutdownOutput();
    }

    @Override
    boolean isSecure() {
        return false;
    }

    @Override
    boolean isProxied() {
        return true;
    }

    // Support for WebSocket/RawChannelImpl which unfortunately
    // still depends on synchronous read/writes.
    // It should be removed when RawChannelImpl moves to using asynchronous APIs.
    @Override
    DetachedConnectionChannel detachChannel() {
        return delegate.detachChannel();
    }
}
