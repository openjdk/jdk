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

import java.io.Closeable;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Flow;
import jdk.incubator.http.HttpClient.Version;
import jdk.incubator.http.internal.common.Demand;
import jdk.incubator.http.internal.common.FlowTube;
import jdk.incubator.http.internal.common.SequentialScheduler;
import jdk.incubator.http.internal.common.SequentialScheduler.DeferredCompleter;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.Utils;
import static jdk.incubator.http.HttpClient.Version.HTTP_2;

/**
 * Wraps socket channel layer and takes care of SSL also.
 *
 * Subtypes are:
 *      PlainHttpConnection: regular direct TCP connection to server
 *      PlainProxyConnection: plain text proxy connection
 *      PlainTunnelingConnection: opens plain text (CONNECT) tunnel to server
 *      AsyncSSLConnection: TLS channel direct to server
 *      AsyncSSLTunnelConnection: TLS channel via (CONNECT) proxy tunnel
 */
abstract class HttpConnection implements Closeable {

    static final boolean DEBUG = Utils.DEBUG; // Revisit: temporary dev flag.
    final System.Logger  debug = Utils.getDebugLogger(this::dbgString, DEBUG);
    final static System.Logger DEBUG_LOGGER = Utils.getDebugLogger(
            () -> "HttpConnection(SocketTube(?))", DEBUG);

    /** The address this connection is connected to. Could be a server or a proxy. */
    final InetSocketAddress address;
    private final HttpClientImpl client;
    private final TrailingOperations trailingOperations;

    HttpConnection(InetSocketAddress address, HttpClientImpl client) {
        this.address = address;
        this.client = client;
        trailingOperations = new TrailingOperations();
    }

    private static final class TrailingOperations {
        private final Map<CompletionStage<?>, Boolean> operations =
                new IdentityHashMap<>();
        void add(CompletionStage<?> cf) {
            synchronized(operations) {
                cf.whenComplete((r,t)-> remove(cf));
                operations.put(cf, Boolean.TRUE);
            }
        }
        boolean remove(CompletionStage<?> cf) {
            synchronized(operations) {
                return operations.remove(cf);
            }
        }
    }

    final void addTrailingOperation(CompletionStage<?> cf) {
        trailingOperations.add(cf);
    }

//    final void removeTrailingOperation(CompletableFuture<?> cf) {
//        trailingOperations.remove(cf);
//    }

    final HttpClientImpl client() {
        return client;
    }

    //public abstract void connect() throws IOException, InterruptedException;

    public abstract CompletableFuture<Void> connectAsync();

    /** Tells whether, or not, this connection is connected to its destination. */
    abstract boolean connected();

    /** Tells whether, or not, this connection is secure ( over SSL ) */
    abstract boolean isSecure();

    /** Tells whether, or not, this connection is proxied. */
    abstract boolean isProxied();

    /** Tells whether, or not, this connection is open. */
    final boolean isOpen() {
        return channel().isOpen() &&
                (connected() ? !getConnectionFlow().isFinished() : true);
    }

    interface HttpPublisher extends FlowTube.TubePublisher {
        void enqueue(List<ByteBuffer> buffers) throws IOException;
        void enqueueUnordered(List<ByteBuffer> buffers) throws IOException;
        void signalEnqueued() throws IOException;
    }

    /**
     * Returns the HTTP publisher associated with this connection.  May be null
     * if invoked before connecting.
     */
    abstract HttpPublisher publisher();

    /**
     * Factory for retrieving HttpConnections. A connection can be retrieved
     * from the connection pool, or a new one created if none available.
     *
     * The given {@code addr} is the ultimate destination. Any proxies,
     * etc, are determined from the request. Returns a concrete instance which
     * is one of the following:
     *      {@link PlainHttpConnection}
     *      {@link PlainTunnelingConnection}
     *
     * The returned connection, if not from the connection pool, must have its,
     * connect() or connectAsync() method invoked, which ( when it completes
     * successfully ) renders the connection usable for requests.
     */
    public static HttpConnection getConnection(InetSocketAddress addr,
                                               HttpClientImpl client,
                                               HttpRequestImpl request,
                                               Version version) {
        HttpConnection c = null;
        InetSocketAddress proxy = request.proxy();
        if (proxy != null && proxy.isUnresolved()) {
            // The default proxy selector may select a proxy whose  address is
            // unresolved. We must resolve the address before connecting to it.
            proxy = new InetSocketAddress(proxy.getHostString(), proxy.getPort());
        }
        boolean secure = request.secure();
        ConnectionPool pool = client.connectionPool();

        if (!secure) {
            c = pool.getConnection(false, addr, proxy);
            if (c != null && c.isOpen() /* may have been eof/closed when in the pool */) {
                final HttpConnection conn = c;
                DEBUG_LOGGER.log(Level.DEBUG, () -> conn.getConnectionFlow()
                            + ": plain connection retrieved from HTTP/1.1 pool");
                return c;
            } else {
                return getPlainConnection(addr, proxy, request, client);
            }
        } else {  // secure
            if (version != HTTP_2) { // only HTTP/1.1 connections are in the pool
                c = pool.getConnection(true, addr, proxy);
            }
            if (c != null && c.isOpen()) {
                final HttpConnection conn = c;
                DEBUG_LOGGER.log(Level.DEBUG, () -> conn.getConnectionFlow()
                            + ": SSL connection retrieved from HTTP/1.1 pool");
                return c;
            } else {
                String[] alpn = null;
                if (version == HTTP_2) {
                    alpn = new String[] { "h2", "http/1.1" };
                }
                return getSSLConnection(addr, proxy, alpn, client);
            }
        }
    }

    private static HttpConnection getSSLConnection(InetSocketAddress addr,
                                                   InetSocketAddress proxy,
                                                   String[] alpn,
                                                   HttpClientImpl client) {
        if (proxy != null)
            return new AsyncSSLTunnelConnection(addr, client, alpn, proxy);
        else
            return new AsyncSSLConnection(addr, client, alpn);
    }

    /* Returns either a plain HTTP connection or a plain tunnelling connection
     * for proxied WebSocket */
    private static HttpConnection getPlainConnection(InetSocketAddress addr,
                                                     InetSocketAddress proxy,
                                                     HttpRequestImpl request,
                                                     HttpClientImpl client) {
        if (request.isWebSocket() && proxy != null)
            return new PlainTunnelingConnection(addr, proxy, client);

        if (proxy == null)
            return new PlainHttpConnection(addr, client);
        else
            return new PlainProxyConnection(proxy, client);
    }

    void closeOrReturnToCache(HttpHeaders hdrs) {
        if (hdrs == null) {
            // the connection was closed by server, eof
            close();
            return;
        }
        if (!isOpen()) {
            return;
        }
        HttpClientImpl client = client();
        if (client == null) {
            close();
            return;
        }
        ConnectionPool pool = client.connectionPool();
        boolean keepAlive = hdrs.firstValue("Connection")
                .map((s) -> !s.equalsIgnoreCase("close"))
                .orElse(true);

        if (keepAlive) {
            Log.logTrace("Returning connection to the pool: {0}", this);
            pool.returnToPool(this);
        } else {
            close();
        }
    }

    abstract SocketChannel channel();

    final InetSocketAddress address() {
        return address;
    }

    abstract ConnectionPool.CacheKey cacheKey();

//    // overridden in SSL only
//    SSLParameters sslParameters() {
//        return null;
//    }

    /**
     * Closes this connection, by returning the socket to its connection pool.
     */
    @Override
    public abstract void close();

    abstract void shutdownInput() throws IOException;

    abstract void shutdownOutput() throws IOException;

    // Support for WebSocket/RawChannelImpl which unfortunately
    // still depends on synchronous read/writes.
    // It should be removed when RawChannelImpl moves to using asynchronous APIs.
    abstract static class DetachedConnectionChannel implements Closeable {
        DetachedConnectionChannel() {}
        abstract SocketChannel channel();
        abstract long write(ByteBuffer[] buffers, int start, int number)
                throws IOException;
        abstract void shutdownInput() throws IOException;
        abstract void shutdownOutput() throws IOException;
        abstract ByteBuffer read() throws IOException;
        @Override
        public abstract void close();
        @Override
        public String toString() {
            return this.getClass().getSimpleName() + ": " + channel().toString();
        }
    }

    // Support for WebSocket/RawChannelImpl which unfortunately
    // still depends on synchronous read/writes.
    // It should be removed when RawChannelImpl moves to using asynchronous APIs.
    abstract DetachedConnectionChannel detachChannel();

    abstract FlowTube getConnectionFlow();

    /**
     * A publisher that makes it possible to publish (write)
     * ordered (normal priority) and unordered (high priority)
     * buffers downstream.
     */
    final class PlainHttpPublisher implements HttpPublisher {
        final Object reading;
        PlainHttpPublisher() {
            this(new Object());
        }
        PlainHttpPublisher(Object readingLock) {
            this.reading = readingLock;
        }
        final ConcurrentLinkedDeque<List<ByteBuffer>> queue = new ConcurrentLinkedDeque<>();
        volatile Flow.Subscriber<? super List<ByteBuffer>> subscriber;
        volatile HttpWriteSubscription subscription;
        final SequentialScheduler writeScheduler =
                    new SequentialScheduler(this::flushTask);
        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            synchronized (reading) {
                //assert this.subscription == null;
                //assert this.subscriber == null;
                if (subscription == null) {
                    subscription = new HttpWriteSubscription();
                }
                this.subscriber = subscriber;
            }
            // TODO: should we do this in the flow?
            subscriber.onSubscribe(subscription);
            signal();
        }

        void flushTask(DeferredCompleter completer) {
            try {
                HttpWriteSubscription sub = subscription;
                if (sub != null) sub.flush();
            } finally {
                completer.complete();
            }
        }

        void signal() {
            writeScheduler.runOrSchedule();
        }

        final class HttpWriteSubscription implements Flow.Subscription {
            final Demand demand = new Demand();

            @Override
            public void request(long n) {
                if (n <= 0) throw new IllegalArgumentException("non-positive request");
                demand.increase(n);
                debug.log(Level.DEBUG, () -> "HttpPublisher: got request of "
                            + n + " from "
                            + getConnectionFlow());
                writeScheduler.runOrSchedule();
            }

            @Override
            public void cancel() {
                debug.log(Level.DEBUG, () -> "HttpPublisher: cancelled by "
                          + getConnectionFlow());
            }

            void flush() {
                while (!queue.isEmpty() && demand.tryDecrement()) {
                    List<ByteBuffer> elem = queue.poll();
                    debug.log(Level.DEBUG, () -> "HttpPublisher: sending "
                                + Utils.remaining(elem) + " bytes ("
                                + elem.size() + " buffers) to "
                                + getConnectionFlow());
                    subscriber.onNext(elem);
                }
            }
        }

        @Override
        public void enqueue(List<ByteBuffer> buffers) throws IOException {
            queue.add(buffers);
            int bytes = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
            debug.log(Level.DEBUG, "added %d bytes to the write queue", bytes);
        }

        @Override
        public void enqueueUnordered(List<ByteBuffer> buffers) throws IOException {
            // Unordered frames are sent before existing frames.
            int bytes = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
            queue.addFirst(buffers);
            debug.log(Level.DEBUG, "inserted %d bytes in the write queue", bytes);
        }

        @Override
        public void signalEnqueued() throws IOException {
            debug.log(Level.DEBUG, "signalling the publisher of the write queue");
            signal();
        }
    }

    String dbgTag = null;
    final String dbgString() {
        FlowTube flow = getConnectionFlow();
        String tag = dbgTag;
        if (tag == null && flow != null) {
            dbgTag = tag = this.getClass().getSimpleName() + "(" + flow + ")";
        } else if (tag == null) {
            tag = this.getClass().getSimpleName() + "(?)";
        }
        return tag;
    }

    @Override
    public String toString() {
        return "HttpConnection: " + channel().toString();
    }
}
