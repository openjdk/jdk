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

import javax.net.ssl.SSLParameters;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

import jdk.incubator.http.internal.common.ByteBufferReference;
import jdk.incubator.http.internal.common.Utils;

/**
 * Wraps socket channel layer and takes care of SSL also.
 *
 * Subtypes are:
 *      PlainHttpConnection: regular direct TCP connection to server
 *      PlainProxyConnection: plain text proxy connection
 *      PlainTunnelingConnection: opens plain text (CONNECT) tunnel to server
 *      SSLConnection: TLS channel direct to server
 *      SSLTunnelConnection: TLS channel via (CONNECT) proxy tunnel
 */
abstract class HttpConnection implements Closeable {

    enum Mode {
        BLOCKING,
        NON_BLOCKING,
        ASYNC
    }

    protected Mode mode;

    // address we are connected to. Could be a server or a proxy
    final InetSocketAddress address;
    final HttpClientImpl client;

    HttpConnection(InetSocketAddress address, HttpClientImpl client) {
        this.address = address;
        this.client = client;
    }

    /**
     * Public API to this class. addr is the ultimate destination. Any proxies
     * etc are figured out from the request. Returns an instance of one of the
     * following
     *      PlainHttpConnection
     *      PlainTunnelingConnection
     *      SSLConnection
     *      SSLTunnelConnection
     *
     * When object returned, connect() or connectAsync() must be called, which
     * when it returns/completes, the connection is usable for requests.
     */
    public static HttpConnection getConnection(
            InetSocketAddress addr, HttpClientImpl client, HttpRequestImpl request)
    {
        return getConnectionImpl(addr, client, request, false);
    }

    /**
     * Called specifically to get an async connection for HTTP/2 over SSL.
     */
    public static HttpConnection getConnection(InetSocketAddress addr,
        HttpClientImpl client, HttpRequestImpl request, boolean isHttp2) {

        return getConnectionImpl(addr, client, request, isHttp2);
    }

    public abstract void connect() throws IOException, InterruptedException;

    public abstract CompletableFuture<Void> connectAsync();

    /**
     * Returns whether this connection is connected to its destination
     */
    abstract boolean connected();

    abstract boolean isSecure();

    abstract boolean isProxied();

    /**
     * Completes when the first byte of the response is available to be read.
     */
    abstract CompletableFuture<Void> whenReceivingResponse();

    final boolean isOpen() {
        return channel().isOpen();
    }

    /* Returns either a plain HTTP connection or a plain tunnelling connection
     * for proxied WebSocket */
    private static HttpConnection getPlainConnection(InetSocketAddress addr,
                                                     InetSocketAddress proxy,
                                                     HttpRequestImpl request,
                                                     HttpClientImpl client) {
        if (request.isWebSocket() && proxy != null) {
            return new PlainTunnelingConnection(addr, proxy, client);
        } else {
            if (proxy == null) {
                return new PlainHttpConnection(addr, client);
            } else {
                return new PlainProxyConnection(proxy, client);
            }
        }
    }

    private static HttpConnection getSSLConnection(InetSocketAddress addr,
            InetSocketAddress proxy, HttpRequestImpl request,
            String[] alpn, boolean isHttp2, HttpClientImpl client)
    {
        if (proxy != null) {
            return new SSLTunnelConnection(addr, client, proxy);
        } else if (!isHttp2) {
            return new SSLConnection(addr, client, alpn);
        } else {
            return new AsyncSSLConnection(addr, client, alpn);
        }
    }

    /**
     * Main factory method.   Gets a HttpConnection, either cached or new if
     * none available.
     */
    private static HttpConnection getConnectionImpl(InetSocketAddress addr,
            HttpClientImpl client,
            HttpRequestImpl request, boolean isHttp2)
    {
        HttpConnection c = null;
        InetSocketAddress proxy = request.proxy(client);
        boolean secure = request.secure();
        ConnectionPool pool = client.connectionPool();
        String[] alpn =  null;

        if (secure && isHttp2) {
            alpn = new String[2];
            alpn[0] = "h2";
            alpn[1] = "http/1.1";
        }

        if (!secure) {
            c = pool.getConnection(false, addr, proxy);
            if (c != null) {
                return c;
            } else {
                return getPlainConnection(addr, proxy, request, client);
            }
        } else {
            if (!isHttp2) { // if http2 we don't cache connections
                c = pool.getConnection(true, addr, proxy);
            }
            if (c != null) {
                return c;
            } else {
                return getSSLConnection(addr, proxy, request, alpn, isHttp2, client);
            }
        }
    }

    void returnToCache(HttpHeaders hdrs) {
        if (hdrs == null) {
            // the connection was closed by server
            close();
            return;
        }
        if (!isOpen()) {
            return;
        }
        ConnectionPool pool = client.connectionPool();
        boolean keepAlive = hdrs.firstValue("Connection")
                .map((s) -> !s.equalsIgnoreCase("close"))
                .orElse(true);

        if (keepAlive) {
            pool.returnToPool(this);
        } else {
            close();
        }
    }

    /**
     * Also check that the number of bytes written is what was expected. This
     * could be different if the buffer is user-supplied and its internal
     * pointers were manipulated in a race condition.
     */
    final void checkWrite(long expected, ByteBuffer buffer) throws IOException {
        long written = write(buffer);
        if (written != expected) {
            throw new IOException("incorrect number of bytes written");
        }
    }

    final void checkWrite(long expected,
                          ByteBuffer[] buffers,
                          int start,
                          int length)
        throws IOException
    {
        long written = write(buffers, start, length);
        if (written != expected) {
            throw new IOException("incorrect number of bytes written");
        }
    }

    abstract SocketChannel channel();

    final InetSocketAddress address() {
        return address;
    }

    synchronized void configureMode(Mode mode) throws IOException {
        this.mode = mode;
        if (mode == Mode.BLOCKING) {
            channel().configureBlocking(true);
        } else {
            channel().configureBlocking(false);
        }
    }

    synchronized Mode getMode() {
        return mode;
    }

    abstract ConnectionPool.CacheKey cacheKey();

    // overridden in SSL only
    SSLParameters sslParameters() {
        return null;
    }

    // Methods to be implemented for Plain TCP and SSL

    abstract long write(ByteBuffer[] buffers, int start, int number)
        throws IOException;

    abstract long write(ByteBuffer buffer) throws IOException;

    // Methods to be implemented for Plain TCP (async mode) and AsyncSSL

    /**
     * In {@linkplain Mode#ASYNC async mode}, this method puts buffers at the
     * end of the send queue; Otherwise, it is equivalent to {@link
     * #write(ByteBuffer[], int, int) write(buffers, 0, buffers.length)}.
     * When in async mode, calling this method should later be followed by
     * subsequent flushAsync invocation.
     * That allows multiple threads to put buffers into the queue while some other
     * thread is writing.
     */
    abstract void writeAsync(ByteBufferReference[] buffers) throws IOException;

    /**
     * In {@linkplain Mode#ASYNC async mode}, this method may put
     * buffers at the beginning of send queue, breaking frames sequence and
     * allowing to write these buffers before other buffers in the queue;
     * Otherwise, it is equivalent to {@link
     * #write(ByteBuffer[], int, int) write(buffers, 0, buffers.length)}.
     * When in async mode, calling this method should later be followed by
     * subsequent flushAsync invocation.
     * That allows multiple threads to put buffers into the queue while some other
     * thread is writing.
     */
    abstract void writeAsyncUnordered(ByteBufferReference[] buffers) throws IOException;

    /**
     * This method should be called after  any writeAsync/writeAsyncUnordered
     * invocation.
     * If there is a race to flushAsync from several threads one thread
     * (race winner) capture flush operation and write the whole queue content.
     * Other threads (race losers) exits from the method (not blocking)
     * and continue execution.
     */
    abstract void flushAsync() throws IOException;

    /**
     * Closes this connection, by returning the socket to its connection pool.
     */
    @Override
    public abstract void close();

    abstract void shutdownInput() throws IOException;

    abstract void shutdownOutput() throws IOException;

    /**
     * Puts position to limit and limit to capacity so we can resume reading
     * into this buffer, but if required > 0 then limit may be reduced so that
     * no more than required bytes are read next time.
     */
    static void resumeChannelRead(ByteBuffer buf, int required) {
        int limit = buf.limit();
        buf.position(limit);
        int capacity = buf.capacity() - limit;
        if (required > 0 && required < capacity) {
            buf.limit(limit + required);
        } else {
            buf.limit(buf.capacity());
        }
    }

    final ByteBuffer read() throws IOException {
        ByteBuffer b = readImpl();
        return b;
    }

    /*
     * Returns a ByteBuffer with the data available at the moment, or null if
     * reached EOF.
     */
    protected abstract ByteBuffer readImpl() throws IOException;

    @Override
    public String toString() {
        return "HttpConnection: " + channel().toString();
    }
}
