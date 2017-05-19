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

import jdk.incubator.http.internal.common.ByteBufferReference;
import jdk.incubator.http.internal.common.MinimalFuture;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

/**
 * A plain text socket tunnel through a proxy. Uses "CONNECT" but does not
 * encrypt. Used by WebSocket. Subclassed in SSLTunnelConnection for encryption.
 */
class PlainTunnelingConnection extends HttpConnection {

    final PlainHttpConnection delegate;
    protected final InetSocketAddress proxyAddr;
    private volatile boolean connected;

    @Override
    public CompletableFuture<Void> connectAsync() {
        return delegate.connectAsync()
            .thenCompose((Void v) -> {
                HttpRequestImpl req = new HttpRequestImpl("CONNECT", client, address);
                MultiExchange<Void,Void> mconnectExchange = new MultiExchange<>(req, client, this::ignore);
                return mconnectExchange.responseAsync()
                    .thenCompose((HttpResponseImpl<Void> resp) -> {
                        CompletableFuture<Void> cf = new MinimalFuture<>();
                        if (resp.statusCode() != 200) {
                            cf.completeExceptionally(new IOException("Tunnel failed"));
                        } else {
                            connected = true;
                            cf.complete(null);
                        }
                        return cf;
                    });
            });
    }

    private HttpResponse.BodyProcessor<Void> ignore(int status, HttpHeaders hdrs) {
        return HttpResponse.BodyProcessor.discard((Void)null);
    }

    @Override
    public void connect() throws IOException, InterruptedException {
        delegate.connect();
        HttpRequestImpl req = new HttpRequestImpl("CONNECT", client, address);
        Exchange<?> connectExchange = new Exchange<>(req, null);
        Response r = connectExchange.responseImpl(delegate);
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
                                       HttpClientImpl client) {
        super(addr, client);
        this.proxyAddr = proxy;
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
    void writeAsync(ByteBufferReference[] buffers) throws IOException {
        delegate.writeAsync(buffers);
    }

    @Override
    void writeAsyncUnordered(ByteBufferReference[] buffers) throws IOException {
        delegate.writeAsyncUnordered(buffers);
    }

    @Override
    void flushAsync() throws IOException {
        delegate.flushAsync();
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
    CompletableFuture<Void> whenReceivingResponse() {
        return delegate.whenReceivingResponse();
    }

    @Override
    protected ByteBuffer readImpl() throws IOException {
        return delegate.readImpl();
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
