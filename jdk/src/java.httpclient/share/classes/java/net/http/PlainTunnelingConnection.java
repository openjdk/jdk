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
package java.net.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.AccessControlContext;
import java.util.concurrent.CompletableFuture;

/**
 * A plain text socket tunnel through a proxy. Uses "CONNECT" but does not
 * encrypt. Used by WebSockets. Subclassed in SSLTunnelConnection for encryption.
 */
class PlainTunnelingConnection extends HttpConnection {

    final PlainHttpConnection delegate;
    protected final InetSocketAddress proxyAddr;
    private volatile boolean connected;
    private final AccessControlContext acc;

    @Override
    public CompletableFuture<Void> connectAsync() {
        return delegate.connectAsync()
            .thenCompose((Void v) -> {
                HttpRequestImpl req = new HttpRequestImpl(client, "CONNECT", address);
                Exchange connectExchange = new Exchange(req, acc);
                return connectExchange
                    .responseAsyncImpl(delegate)
                    .thenCompose((HttpResponse r) -> {
                        CompletableFuture<Void> cf = new CompletableFuture<>();
                        if (r.statusCode() != 200) {
                            cf.completeExceptionally(new IOException("Tunnel failed"));
                        } else {
                            connected = true;
                            cf.complete(null);
                        }
                        return cf;
                    });
            });
    }

    @Override
    public void connect() throws IOException, InterruptedException {
        delegate.connect();
        HttpRequestImpl req = new HttpRequestImpl(client, "CONNECT", address);
        Exchange connectExchange = new Exchange(req, acc);
        HttpResponse r = connectExchange.responseImpl(delegate);
        if (r.statusCode() != 200) {
            throw new IOException("Tunnel failed");
        }
        connected = true;
    }

    @Override
    boolean connected() {
        return connected;
    }

    protected PlainTunnelingConnection(InetSocketAddress addr,
                                       InetSocketAddress proxy,
                                       HttpClientImpl client,
                                       AccessControlContext acc) {
        super(addr, client);
        this.proxyAddr = proxy;
        this.acc = acc;
        delegate = new PlainHttpConnection(proxy, client);
    }

    @Override
    SocketChannel channel() {
        return delegate.channel();
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return new ConnectionPool.CacheKey(null, proxyAddr);
    }

    @Override
    long write(ByteBuffer[] buffers, int start, int number) throws IOException {
        return delegate.write(buffers, start, number);
    }

    @Override
    long write(ByteBuffer buffer) throws IOException {
        return delegate.write(buffer);
    }

    @Override
    void close() {
        delegate.close();
        connected = false;
    }

    @Override
    protected ByteBuffer readImpl(int length) throws IOException {
        return delegate.readImpl(length);
    }

    @Override
    CompletableFuture<Void> whenReceivingResponse() {
        return delegate.whenReceivingResponse();
    }

    @Override
    protected int readImpl(ByteBuffer buffer) throws IOException {
        return delegate.readImpl(buffer);
    }

    @Override
    boolean isSecure() {
        return false;
    }

    @Override
    boolean isProxied() {
        return true;
    }
}
