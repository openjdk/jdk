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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.AccessControlContext;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLParameters;
import java.net.http.SSLDelegate.BufType;
import java.net.http.SSLDelegate.WrapperResult;

/**
 * An SSL tunnel built on a Plain (CONNECT) TCP tunnel.
 */
class SSLTunnelConnection extends HttpConnection {

    final PlainTunnelingConnection delegate;
    protected SSLDelegate sslDelegate;
    private volatile boolean connected;

    @Override
    public void connect() throws IOException, InterruptedException {
        delegate.connect();
        this.sslDelegate = new SSLDelegate(delegate.channel(), client, null);
        connected = true;
    }

    @Override
    boolean connected() {
        return connected && delegate.connected();
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        return delegate.connectAsync()
            .thenAccept((Void v) -> {
                try {
                    // can this block?
                    this.sslDelegate = new SSLDelegate(delegate.channel(),
                                                       client,
                                                       null);
                    connected = true;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
    }

    SSLTunnelConnection(InetSocketAddress addr,
                        HttpClientImpl client,
                        InetSocketAddress proxy,
                        AccessControlContext acc) {
        super(addr, client);
        delegate = new PlainTunnelingConnection(addr, proxy, client, acc);
    }

    @Override
    SSLParameters sslParameters() {
        return sslDelegate.getSSLParameters();
    }

    @Override
    public String toString() {
        return "SSLTunnelConnection: " + super.toString();
    }

    private static long countBytes(ByteBuffer[] buffers, int start, int number) {
        long c = 0;
        for (int i=0; i<number; i++) {
            c+= buffers[start+i].remaining();
        }
        return c;
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return ConnectionPool.cacheKey(address, delegate.proxyAddr);
    }

    @Override
    long write(ByteBuffer[] buffers, int start, int number) throws IOException {
        //debugPrint("Send", buffers, start, number);
        long l = countBytes(buffers, start, number);
        WrapperResult r = sslDelegate.sendData(buffers, start, number);
        if (r.result.getStatus() == Status.CLOSED) {
            if (l > 0) {
                throw new IOException("SSLHttpConnection closed");
            }
        }
        return l;
    }

    @Override
    long write(ByteBuffer buffer) throws IOException {
        //debugPrint("Send", buffer);
        long l = buffer.remaining();
        WrapperResult r = sslDelegate.sendData(buffer);
        if (r.result.getStatus() == Status.CLOSED) {
            if (l > 0) {
                throw new IOException("SSLHttpConnection closed");
            }
        }
        return l;
    }

    @Override
    public void close() {
        Utils.close(delegate.channel());
    }

    @Override
    protected ByteBuffer readImpl(int length) throws IOException {
        ByteBuffer buf = sslDelegate.allocate(BufType.PACKET, length);
        WrapperResult r = sslDelegate.recvData(buf);
        // TODO: check for closure
        String s = "Receive) ";
        //debugPrint(s, r.buf);
        return r.buf;
    }

    @Override
    protected int readImpl(ByteBuffer buf) throws IOException {
        WrapperResult r = sslDelegate.recvData(buf);
        // TODO: check for closure
        String s = "Receive) ";
        //debugPrint(s, r.buf);
        if (r.result.bytesProduced() > 0) {
            assert buf == r.buf;
        }

        return r.result.bytesProduced();
    }

    @Override
    SocketChannel channel() {
        return delegate.channel();
    }

    @Override
    CompletableFuture<Void> whenReceivingResponse() {
        return delegate.whenReceivingResponse();
    }

    @Override
    boolean isSecure() {
        return true;
    }

    @Override
    boolean isProxied() {
        return true;
    }
}
