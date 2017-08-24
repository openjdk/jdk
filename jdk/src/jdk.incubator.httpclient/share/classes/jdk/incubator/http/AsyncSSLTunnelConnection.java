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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import jdk.incubator.http.internal.common.ByteBufferReference;
import jdk.incubator.http.internal.common.Utils;

/**
 * An SSL tunnel built on a Plain (CONNECT) TCP tunnel.
 */
class AsyncSSLTunnelConnection extends AbstractAsyncSSLConnection {

    final PlainTunnelingConnection plainConnection;
    final AsyncSSLDelegate sslDelegate;
    final String serverName;

    @Override
    public void connect() throws IOException, InterruptedException {
        plainConnection.connect();
        configureMode(Mode.ASYNC);
        startReading();
        sslDelegate.connect();
    }

    @Override
    boolean connected() {
        return plainConnection.connected() && sslDelegate.connected();
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        throw new InternalError();
    }

    AsyncSSLTunnelConnection(InetSocketAddress addr,
                        HttpClientImpl client,
                        String[] alpn,
                        InetSocketAddress proxy)
    {
        super(addr, client);
        this.serverName = Utils.getServerName(addr);
        this.plainConnection = new PlainTunnelingConnection(addr, proxy, client);
        this.sslDelegate = new AsyncSSLDelegate(plainConnection, client, alpn, serverName);
    }

    @Override
    synchronized void configureMode(Mode mode) throws IOException {
        super.configureMode(mode);
        plainConnection.configureMode(mode);
    }

    @Override
    SSLParameters sslParameters() {
        return sslDelegate.getSSLParameters();
    }

    @Override
    public String toString() {
        return "AsyncSSLTunnelConnection: " + super.toString();
    }

    @Override
    PlainTunnelingConnection plainConnection() {
        return plainConnection;
    }

    @Override
    AsyncSSLDelegate sslDelegate() {
        return sslDelegate;
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return ConnectionPool.cacheKey(address, plainConnection.proxyAddr);
    }

    @Override
    long write(ByteBuffer[] buffers, int start, int number) throws IOException {
        //debugPrint("Send", buffers, start, number);
        ByteBuffer[] bufs = Utils.reduce(buffers, start, number);
        long n = Utils.remaining(bufs);
        sslDelegate.writeAsync(ByteBufferReference.toReferences(bufs));
        sslDelegate.flushAsync();
        return n;
    }

    @Override
    long write(ByteBuffer buffer) throws IOException {
        //debugPrint("Send", buffer);
        long n = buffer.remaining();
        sslDelegate.writeAsync(ByteBufferReference.toReferences(buffer));
        sslDelegate.flushAsync();
        return n;
    }

    @Override
    public void writeAsync(ByteBufferReference[] buffers) throws IOException {
        sslDelegate.writeAsync(buffers);
    }

    @Override
    public void writeAsyncUnordered(ByteBufferReference[] buffers) throws IOException {
        sslDelegate.writeAsyncUnordered(buffers);
    }

    @Override
    public void flushAsync() throws IOException {
        sslDelegate.flushAsync();
    }

    @Override
    public void close() {
        Utils.close(sslDelegate, plainConnection.channel());
    }

    @Override
    void shutdownInput() throws IOException {
        plainConnection.channel().shutdownInput();
    }

    @Override
    void shutdownOutput() throws IOException {
        plainConnection.channel().shutdownOutput();
    }

    @Override
    SocketChannel channel() {
        return plainConnection.channel();
    }

    @Override
    boolean isProxied() {
        return true;
    }

    @Override
    public void setAsyncCallbacks(Consumer<ByteBufferReference> asyncReceiver,
                                  Consumer<Throwable> errorReceiver,
                                  Supplier<ByteBufferReference> readBufferSupplier) {
        sslDelegate.setAsyncCallbacks(asyncReceiver, errorReceiver, readBufferSupplier);
        plainConnection.setAsyncCallbacks(sslDelegate::asyncReceive, errorReceiver, sslDelegate::getNetBuffer);
    }

    @Override
    public void startReading() {
        plainConnection.startReading();
        sslDelegate.startReading();
    }

    @Override
    public void stopAsyncReading() {
        plainConnection.stopAsyncReading();
    }

    @Override
    public void enableCallback() {
        sslDelegate.enableCallback();
    }

    @Override
    public void closeExceptionally(Throwable cause) throws IOException {
        Utils.close(cause, sslDelegate, plainConnection.channel());
    }

    @Override
    SSLEngine getEngine() {
        return sslDelegate.getEngine();
    }

    @Override
    SSLTunnelConnection downgrade() {
        return new SSLTunnelConnection(this);
    }
}
