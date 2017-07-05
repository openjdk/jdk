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
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

/**
 * Plain raw TCP connection direct to destination
 */
class PlainHttpConnection extends HttpConnection {

    protected SocketChannel chan;
    private volatile boolean connected;
    private boolean closed;

    class ConnectEvent extends AsyncEvent implements AsyncEvent.Blocking {
        CompletableFuture<Void> cf;

        ConnectEvent(CompletableFuture<Void> cf) {
            this.cf = cf;
        }

        @Override
        public SelectableChannel channel() {
            return chan;
        }

        @Override
        public int interestOps() {
            return SelectionKey.OP_CONNECT;
        }

        @Override
        public void handle() {
            try {
                chan.finishConnect();
            } catch (IOException e) {
                cf.completeExceptionally(e);
            }
            connected = true;
            cf.complete(null);
        }

        @Override
        public void abort() {
            close();
        }
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        CompletableFuture<Void> plainFuture = new CompletableFuture<>();
        try {
            chan.configureBlocking(false);
            chan.connect(address);
            client.registerEvent(new ConnectEvent(plainFuture));
        } catch (IOException e) {
            plainFuture.completeExceptionally(e);
        }
        return plainFuture;
    }

    @Override
    public void connect() throws IOException {
        chan.connect(address);
        connected = true;
    }

    @Override
    SocketChannel channel() {
        return chan;
    }

    PlainHttpConnection(InetSocketAddress addr, HttpClientImpl client) {
        super(addr, client);
        try {
            this.chan = SocketChannel.open();
            int bufsize = client.getReceiveBufferSize();
            chan.setOption(StandardSocketOptions.SO_RCVBUF, bufsize);
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    @Override
    long write(ByteBuffer[] buffers, int start, int number) throws IOException {
        //debugPrint("Send", buffers, start, number);
        return chan.write(buffers, start, number);
    }

    @Override
    long write(ByteBuffer buffer) throws IOException {
        //debugPrint("Send", buffer);
        return chan.write(buffer);
    }

    @Override
    public String toString() {
        return "PlainHttpConnection: " + super.toString();
    }

    /**
     * Close this connection
     */
    @Override
    synchronized void close() {
        if (closed)
            return;
        closed = true;
        try {
            Log.logError("Closing: " + toString());
            //System.out.println("Closing: " + this);
            chan.close();
        } catch (IOException e) {}
    }

    @Override
    protected ByteBuffer readImpl(int length) throws IOException {
        ByteBuffer buf = getBuffer(); // TODO not using length
        int n = chan.read(buf);
        if (n == -1) {
            return null;
        }
        buf.flip();
        String s = "Receive (" + n + " bytes) ";
        //debugPrint(s, buf);
        return buf;
    }

    @Override
    protected int readImpl(ByteBuffer buf) throws IOException {
        int mark = buf.position();
        int n = chan.read(buf);
        if (n == -1) {
            return -1;
        }
        Utils.flipToMark(buffer, mark);
        String s = "Receive (" + n + " bytes) ";
        //debugPrint(s, buf);
        return n;
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return new ConnectionPool.CacheKey(address, null);
    }

    @Override
    synchronized boolean connected() {
        return connected;
    }

    class ReceiveResponseEvent extends AsyncEvent implements AsyncEvent.Blocking {
        CompletableFuture<Void> cf;

        ReceiveResponseEvent(CompletableFuture<Void> cf) {
            this.cf = cf;
        }
        @Override
        public SelectableChannel channel() {
            return chan;
        }

        @Override
        public void handle() {
            cf.complete(null);
        }

        @Override
        public int interestOps() {
            return SelectionKey.OP_READ;
        }

        @Override
        public void abort() {
            close();
        }
    }

    @Override
    boolean isSecure() {
        return false;
    }

    @Override
    boolean isProxied() {
        return false;
    }

    @Override
    CompletableFuture<Void> whenReceivingResponse() {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        try {
            client.registerEvent(new ReceiveResponseEvent(cf));
        } catch (IOException e) {
            cf.completeExceptionally(e);
        }
        return cf;
    }
}
