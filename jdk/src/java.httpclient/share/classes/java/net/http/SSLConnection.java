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
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLParameters;
import java.net.http.SSLDelegate.BufType;
import java.net.http.SSLDelegate.WrapperResult;

/**
 * An SSL connection built on a Plain TCP connection.
 */
class SSLConnection extends HttpConnection {

    PlainHttpConnection delegate;
    SSLDelegate sslDelegate;
    final String[] alpn;

    @Override
    public CompletableFuture<Void> connectAsync() {
        return delegate.connectAsync()
                .thenCompose((Void v) -> {
                    CompletableFuture<Void> cf = new CompletableFuture<>();
                    try {
                        this.sslDelegate = new SSLDelegate(delegate.channel(),
                                                           client,
                                                           alpn);
                        cf.complete(null);
                    } catch (IOException e) {
                        cf.completeExceptionally(e);
                    }
                    return cf;
                });
    }

    @Override
    public void connect() throws IOException {
        delegate.connect();
        this.sslDelegate = new SSLDelegate(delegate.channel(), client, alpn);
    }

    SSLConnection(InetSocketAddress addr, HttpClientImpl client, String[] ap) {
        super(addr, client);
        this.alpn = ap;
        delegate = new PlainHttpConnection(addr, client);
    }

    @Override
    SSLParameters sslParameters() {
        return sslDelegate.getSSLParameters();
    }

    @Override
    public String toString() {
        return "SSLConnection: " + super.toString();
    }

    private static long countBytes(ByteBuffer[] buffers, int start, int length) {
        long c = 0;
        for (int i=0; i<length; i++) {
            c+= buffers[start+i].remaining();
        }
        return c;
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return ConnectionPool.cacheKey(address, null);
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
    void close() {
        try {
            //System.err.println ("Closing: " + this);
            delegate.channel().close(); // TODO: proper close
        } catch (IOException ex) {
            Log.logError(ex.toString());
        }
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
        // TODO: need to ensure that buf is big enough for application data
        WrapperResult r = sslDelegate.recvData(buf);
        // TODO: check for closure
        String s = "Receive) ";
        //debugPrint(s, r.buf);
        return r.result.bytesProduced();
    }

    @Override
    boolean connected() {
        return delegate.connected();
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
        return false;
    }

}
