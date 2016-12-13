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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.incubator.http.internal.common.AsyncWriteQueue;
import jdk.incubator.http.internal.common.ByteBufferReference;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;

/**
 * Plain raw TCP connection direct to destination. 2 modes
 * 1) Blocking used by http/1. In this case the connect is actually non
 *    blocking but the request is sent blocking. The first byte of a response
 *    is received non-blocking and the remainder of the response is received
 *    blocking
 * 2) Non-blocking. In this case (for http/2) the connection is actually opened
 *    blocking but all reads and writes are done non-blocking under the
 *    control of a Http2Connection object.
 */
class PlainHttpConnection extends HttpConnection implements AsyncConnection {

    protected SocketChannel chan;
    private volatile boolean connected;
    private boolean closed;

    // should be volatile to provide proper synchronization(visibility) action
    private volatile Consumer<ByteBufferReference> asyncReceiver;
    private volatile Consumer<Throwable> errorReceiver;
    private volatile Supplier<ByteBufferReference> readBufferSupplier;

    private final AsyncWriteQueue asyncOutputQ = new AsyncWriteQueue(this::asyncOutput);

    private final Object reading = new Object();

    @Override
    public void startReading() {
        try {
            client.registerEvent(new ReadEvent());
        } catch (IOException e) {
            shutdown();
        }
    }

    class ConnectEvent extends AsyncEvent {
        CompletableFuture<Void> cf;

        ConnectEvent(CompletableFuture<Void> cf) {
            super(AsyncEvent.BLOCKING);
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
        CompletableFuture<Void> plainFuture = new MinimalFuture<>();
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
            chan.setOption(StandardSocketOptions.TCP_NODELAY, true);
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    @Override
    long write(ByteBuffer[] buffers, int start, int number) throws IOException {
        if (getMode() != Mode.ASYNC) {
            return chan.write(buffers, start, number);
        }
        // async
        buffers = Utils.reduce(buffers, start, number);
        long n = Utils.remaining(buffers);
        asyncOutputQ.put(ByteBufferReference.toReferences(buffers));
        flushAsync();
        return n;
    }

    @Override
    long write(ByteBuffer buffer) throws IOException {
        if (getMode() != Mode.ASYNC) {
            return chan.write(buffer);
        }
        // async
        long n = buffer.remaining();
        asyncOutputQ.put(ByteBufferReference.toReferences(buffer));
        flushAsync();
        return n;
    }

    // handle registered WriteEvent; invoked from SelectorManager thread
    void flushRegistered() {
        if (getMode() == Mode.ASYNC) {
            try {
                asyncOutputQ.flushDelayed();
            } catch (IOException e) {
                // Only IOException caused by closed Queue is expected here
                shutdown();
            }
        }
    }

    @Override
    public void writeAsync(ByteBufferReference[] buffers) throws IOException {
        if (getMode() != Mode.ASYNC) {
            chan.write(ByteBufferReference.toBuffers(buffers));
            ByteBufferReference.clear(buffers);
        } else {
            asyncOutputQ.put(buffers);
        }
    }

    @Override
    public void writeAsyncUnordered(ByteBufferReference[] buffers) throws IOException {
        if (getMode() != Mode.ASYNC) {
            chan.write(ByteBufferReference.toBuffers(buffers));
            ByteBufferReference.clear(buffers);
        } else {
            // Unordered frames are sent before existing frames.
            asyncOutputQ.putFirst(buffers);
        }
    }

    @Override
    public void flushAsync() throws IOException {
        if (getMode() == Mode.ASYNC) {
            asyncOutputQ.flush();
        }
    }

    void asyncOutput(ByteBufferReference[] refs, AsyncWriteQueue delayCallback) {
        try {
            ByteBuffer[] bufs = ByteBufferReference.toBuffers(refs);
            while (Utils.remaining(bufs) > 0) {
                long n = chan.write(bufs);
                if (n == 0) {
                    delayCallback.setDelayed(refs);
                    client.registerEvent(new WriteEvent());
                    return;
                }
            }
            ByteBufferReference.clear(refs);
        } catch (IOException e) {
            shutdown();
        }
    }

    @Override
    public String toString() {
        return "PlainHttpConnection: " + super.toString();
    }

    /**
     * Close this connection
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            Log.logError("Closing: " + toString());
            //System.out.println("Closing: " + this);
            chan.close();
        } catch (IOException e) {}
    }

    @Override
    void shutdownInput() throws IOException {
        chan.shutdownInput();
    }

    @Override
    void shutdownOutput() throws IOException {
        chan.shutdownOutput();
    }

    void shutdown() {
        close();
        errorReceiver.accept(new IOException("Connection aborted"));
    }

    void asyncRead() {
        synchronized (reading) {
            try {
                while (true) {
                    ByteBufferReference buf = readBufferSupplier.get();
                    int n = chan.read(buf.get());
                    //System.err.printf("Read %d bytes from chan\n", n);
                    if (n == -1) {
                        throw new IOException();
                    }
                    if (n == 0) {
                        buf.clear();
                        return;
                    }
                    buf.get().flip();
                    asyncReceiver.accept(buf);
                }
            } catch (IOException e) {
                shutdown();
            }
        }
    }

    @Override
    protected ByteBuffer readImpl() throws IOException {
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
    protected int readImpl(ByteBuffer buf) throws IOException {
        int mark = buf.position();
        int n;
        // FIXME: this hack works in conjunction with the corresponding change
        // in jdk.incubator.http.RawChannel.registerEvent
        //if ((n = buffer.remaining()) != 0) {
            //buf.put(buffer);
        //} else {
            n = chan.read(buf);
        //}
        if (n == -1) {
            return -1;
        }
        Utils.flipToMark(buf, mark);
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

    // used for all output in HTTP/2
    class WriteEvent extends AsyncEvent {
        WriteEvent() {
            super(0);
        }

        @Override
        public SelectableChannel channel() {
            return chan;
        }

        @Override
        public int interestOps() {
            return SelectionKey.OP_WRITE;
        }

        @Override
        public void handle() {
            flushRegistered();
        }

        @Override
        public void abort() {
            shutdown();
        }
    }

    // used for all input in HTTP/2
    class ReadEvent extends AsyncEvent {
        ReadEvent() {
            super(AsyncEvent.REPEATING); // && !BLOCKING
        }

        @Override
        public SelectableChannel channel() {
            return chan;
        }

        @Override
        public int interestOps() {
            return SelectionKey.OP_READ;
        }

        @Override
        public void handle() {
            asyncRead();
        }

        @Override
        public void abort() {
            shutdown();
        }

    }

    // used in blocking channels only
    class ReceiveResponseEvent extends AsyncEvent {
        CompletableFuture<Void> cf;

        ReceiveResponseEvent(CompletableFuture<Void> cf) {
            super(AsyncEvent.BLOCKING);
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
    public void setAsyncCallbacks(Consumer<ByteBufferReference> asyncReceiver,
                                  Consumer<Throwable> errorReceiver,
                                  Supplier<ByteBufferReference> readBufferSupplier) {
        this.asyncReceiver = asyncReceiver;
        this.errorReceiver = errorReceiver;
        this.readBufferSupplier = readBufferSupplier;
    }

    @Override
    CompletableFuture<Void> whenReceivingResponse() {
        CompletableFuture<Void> cf = new MinimalFuture<>();
        try {
            client.registerEvent(new ReceiveResponseEvent(cf));
        } catch (IOException e) {
            cf.completeExceptionally(e);
        }
        return cf;
    }
}
