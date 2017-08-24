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

import jdk.incubator.http.internal.common.ByteBufferReference;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.HttpResponse.BodyHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A plain text socket tunnel through a proxy. Uses "CONNECT" but does not
 * encrypt. Used by WebSocket, as well as HTTP over SSL + Proxy.
 * Wrapped in SSLTunnelConnection or AsyncSSLTunnelConnection for encryption.
 */
class PlainTunnelingConnection extends HttpConnection implements AsyncConnection {

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
        MultiExchange<Void,Void> mul = new MultiExchange<>(req, client, BodyHandler.<Void>discard(null));
        Exchange<Void> connectExchange = new Exchange<>(req, mul);
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
    public void writeAsync(ByteBufferReference[] buffers) throws IOException {
        delegate.writeAsync(buffers);
    }

    @Override
    public void writeAsyncUnordered(ByteBufferReference[] buffers) throws IOException {
        delegate.writeAsyncUnordered(buffers);
    }

    @Override
    public void flushAsync() throws IOException {
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
    boolean isSecure() {
        return false;
    }

    @Override
    boolean isProxied() {
        return true;
    }

    @Override
    public void setAsyncCallbacks(Consumer<ByteBufferReference> asyncReceiver,
            Consumer<Throwable> errorReceiver,
            Supplier<ByteBufferReference> readBufferSupplier) {
        delegate.setAsyncCallbacks(asyncReceiver, errorReceiver, readBufferSupplier);
    }

    @Override
    public void startReading() {
        delegate.startReading();
    }

    @Override
    public void stopAsyncReading() {
        delegate.stopAsyncReading();
    }

    @Override
    public void enableCallback() {
        delegate.enableCallback();
    }

    @Override
    synchronized void configureMode(Mode mode) throws IOException {
        super.configureMode(mode);
        delegate.configureMode(mode);
    }
}
