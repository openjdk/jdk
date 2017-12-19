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
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.CompletableFuture;
import jdk.incubator.http.internal.common.FlowTube;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;

/**
 * Plain raw TCP connection direct to destination.
 * The connection operates in asynchronous non-blocking mode.
 * All reads and writes are done non-blocking.
 */
class PlainHttpConnection extends HttpConnection {

    private final Object reading = new Object();
    protected final SocketChannel chan;
    private final FlowTube tube;
    private final PlainHttpPublisher writePublisher = new PlainHttpPublisher(reading);
    private volatile boolean connected;
    private boolean closed;

    // should be volatile to provide proper synchronization(visibility) action

    final class ConnectEvent extends AsyncEvent {
        private final CompletableFuture<Void> cf;

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
                assert !connected : "Already connected";
                assert !chan.isBlocking() : "Unexpected blocking channel";
                debug.log(Level.DEBUG, "ConnectEvent: finishing connect");
                boolean finished = chan.finishConnect();
                assert finished : "Expected channel to be connected";
                debug.log(Level.DEBUG,
                          "ConnectEvent: connect finished: %s Local addr: %s", finished, chan.getLocalAddress());
                connected = true;
                // complete async since the event runs on the SelectorManager thread
                cf.completeAsync(() -> null, client().theExecutor());
            } catch (Throwable e) {
                client().theExecutor().execute( () -> cf.completeExceptionally(e));
            }
        }

        @Override
        public void abort(IOException ioe) {
            close();
            client().theExecutor().execute( () -> cf.completeExceptionally(ioe));
        }
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        CompletableFuture<Void> cf = new MinimalFuture<>();
        try {
            assert !connected : "Already connected";
            assert !chan.isBlocking() : "Unexpected blocking channel";
            boolean finished = false;
            PrivilegedExceptionAction<Boolean> pa = () -> chan.connect(address);
            try {
                 finished = AccessController.doPrivileged(pa);
            } catch (PrivilegedActionException e) {
                cf.completeExceptionally(e.getCause());
            }
            if (finished) {
                debug.log(Level.DEBUG, "connect finished without blocking");
                connected = true;
                cf.complete(null);
            } else {
                debug.log(Level.DEBUG, "registering connect event");
                client().registerEvent(new ConnectEvent(cf));
            }
        } catch (Throwable throwable) {
            cf.completeExceptionally(throwable);
        }
        return cf;
    }

    @Override
    SocketChannel channel() {
        return chan;
    }

    @Override
    final FlowTube getConnectionFlow() {
        return tube;
    }

    PlainHttpConnection(InetSocketAddress addr, HttpClientImpl client) {
        super(addr, client);
        try {
            this.chan = SocketChannel.open();
            chan.configureBlocking(false);
            int bufsize = client.getReceiveBufferSize();
            if (!trySetReceiveBufferSize(bufsize)) {
                trySetReceiveBufferSize(256*1024);
            }
            chan.setOption(StandardSocketOptions.TCP_NODELAY, true);
            // wrap the connected channel in a Tube for async reading and writing
            tube = new SocketTube(client(), chan, Utils::getBuffer);
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    private boolean trySetReceiveBufferSize(int bufsize) {
        try {
            chan.setOption(StandardSocketOptions.SO_RCVBUF, bufsize);
            return true;
        } catch(IOException x) {
            debug.log(Level.DEBUG,
                    "Failed to set receive buffer size to %d on %s",
                    bufsize, chan);
        }
        return false;
    }

    @Override
    HttpPublisher publisher() { return writePublisher; }


    @Override
    public String toString() {
        return "PlainHttpConnection: " + super.toString();
    }

    /**
     * Closes this connection
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            Log.logTrace("Closing: " + toString());
            chan.close();
        } catch (IOException e) {}
    }

    @Override
    void shutdownInput() throws IOException {
        debug.log(Level.DEBUG, "Shutting down input");
        chan.shutdownInput();
    }

    @Override
    void shutdownOutput() throws IOException {
        debug.log(Level.DEBUG, "Shutting down output");
        chan.shutdownOutput();
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return new ConnectionPool.CacheKey(address, null);
    }

    @Override
    synchronized boolean connected() {
        return connected;
    }


    @Override
    boolean isSecure() {
        return false;
    }

    @Override
    boolean isProxied() {
        return false;
    }

    // Support for WebSocket/RawChannelImpl which unfortunately
    // still depends on synchronous read/writes.
    // It should be removed when RawChannelImpl moves to using asynchronous APIs.
    private static final class PlainDetachedChannel
            extends DetachedConnectionChannel {
        final PlainHttpConnection plainConnection;
        boolean closed;
        PlainDetachedChannel(PlainHttpConnection conn) {
            // We're handing the connection channel over to a web socket.
            // We need the selector manager's thread to stay alive until
            // the WebSocket is closed.
            conn.client().webSocketOpen();
            this.plainConnection = conn;
        }

        @Override
        SocketChannel channel() {
            return plainConnection.channel();
        }

        @Override
        ByteBuffer read() throws IOException {
            ByteBuffer dst = ByteBuffer.allocate(8192);
            int n = readImpl(dst);
            if (n > 0) {
                return dst;
            } else if (n == 0) {
                return Utils.EMPTY_BYTEBUFFER;
            } else {
                return null;
            }
        }

        @Override
        public void close() {
            HttpClientImpl client = plainConnection.client();
            try {
                plainConnection.close();
            } finally {
                // notify the HttpClientImpl that the websocket is no
                // no longer operating.
                synchronized(this) {
                    if (closed == true) return;
                    closed = true;
                }
                client.webSocketClose();
            }
        }

        @Override
        public long write(ByteBuffer[] buffers, int start, int number)
                throws IOException
        {
            return channel().write(buffers, start, number);
        }

        @Override
        public void shutdownInput() throws IOException {
            plainConnection.shutdownInput();
        }

        @Override
        public void shutdownOutput() throws IOException {
            plainConnection.shutdownOutput();
        }

        private int readImpl(ByteBuffer buf) throws IOException {
            int mark = buf.position();
            int n;
            n = channel().read(buf);
            if (n == -1) {
                return -1;
            }
            Utils.flipToMark(buf, mark);
            return n;
        }
    }

    // Support for WebSocket/RawChannelImpl which unfortunately
    // still depends on synchronous read/writes.
    // It should be removed when RawChannelImpl moves to using asynchronous APIs.
    @Override
    DetachedConnectionChannel detachChannel() {
        client().cancelRegistration(channel());
        return new PlainDetachedChannel(this);
    }

}
