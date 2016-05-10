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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Asynchronous version of SSLConnection.
 */
class AsyncSSLConnection extends HttpConnection implements AsyncConnection {
    final AsyncSSLDelegate sslDelegate;
    final PlainHttpConnection delegate;

    AsyncSSLConnection(InetSocketAddress addr, HttpClientImpl client, String[] ap) {
        super(addr, client);
        delegate = new PlainHttpConnection(addr, client);
        sslDelegate = new AsyncSSLDelegate(delegate, client, ap);
    }

    @Override
    public void connect() throws IOException, InterruptedException {
        delegate.connect();
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        return delegate.connectAsync();
    }

    @Override
    boolean connected() {
        return delegate.connected();
    }

    @Override
    boolean isSecure() {
        return true;
    }

    @Override
    boolean isProxied() {
        return false;
    }

    @Override
    SocketChannel channel() {
        return delegate.channel();
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return ConnectionPool.cacheKey(address, null);
    }

    @Override
    synchronized long write(ByteBuffer[] buffers, int start, int number) throws IOException {
        ByteBuffer[] bufs = Utils.reduce(buffers, start, number);
        long n = Utils.remaining(bufs);
        sslDelegate.write(bufs);
        return n;
    }

    @Override
    long write(ByteBuffer buffer) throws IOException {
        long n = buffer.remaining();
        sslDelegate.write(buffer);
        return n;
    }

    @Override
    public void close() {
        Utils.close(sslDelegate, delegate.channel());
    }

    @Override
    public void setAsyncCallbacks(Consumer<ByteBuffer> asyncReceiver, Consumer<Throwable> errorReceiver) {
        sslDelegate.setAsyncCallbacks(asyncReceiver, errorReceiver);
        delegate.setAsyncCallbacks(sslDelegate::lowerRead, errorReceiver);
    }

    // Blocking read functions not used here

    @Override
    protected ByteBuffer readImpl(int length) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    protected int readImpl(ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    CompletableFuture<Void> whenReceivingResponse() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void startReading() {
        delegate.startReading();
        sslDelegate.startReading();
    }
}
