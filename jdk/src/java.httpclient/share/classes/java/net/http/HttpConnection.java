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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLParameters;

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
abstract class HttpConnection implements BufferHandler, Closeable {

    protected final static ByteBuffer emptyBuf = Utils.EMPTY_BYTEBUFFER;

    enum Mode {
        BLOCKING,
        NON_BLOCKING,
        ASYNC
    }

    protected Mode mode;

    // address we are connected to. Could be a server or a proxy
    final InetSocketAddress address;
    final HttpClientImpl client;
    protected volatile ByteBuffer buffer;

    HttpConnection(InetSocketAddress address, HttpClientImpl client) {
        this.address = address;
        this.client = client;
        this.buffer = emptyBuf;
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
    public static HttpConnection getConnection(InetSocketAddress addr,
                                               HttpRequestImpl request) {
        return getConnectionImpl(addr, request, null);
    }

    /**
     * Called specifically to get an async connection for HTTP/2 over SSL.
     *
     * @param addr
     * @param request
     * @param http2
     * @return
     */
    public static HttpConnection getConnection(InetSocketAddress addr,
        HttpRequestImpl request, Http2Connection http2) {

        return getConnectionImpl(addr, request, http2);
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

    // must be called before reading any data off connection
    // at beginning of response.
    ByteBuffer getRemaining() {
        ByteBuffer b = buffer;
        buffer = emptyBuf;
        return b;
    }

    final boolean isOpen() {
        return channel().isOpen();
    }

    /* Returns either a plain HTTP connection or a plain tunnelling connection
     * for proxied websockets */
    private static HttpConnection getPlainConnection(InetSocketAddress addr,
                                                     InetSocketAddress proxy,
                                                     HttpRequestImpl request) {
        HttpClientImpl client = request.client();

        if (request.isWebSocket() && proxy != null) {
            return new PlainTunnelingConnection(addr,
                                                proxy,
                                                client,
                                                request.getAccessControlContext());
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
            String[] alpn, Http2Connection http2) {
        HttpClientImpl client = request.client();
        if (proxy != null) {
            return new SSLTunnelConnection(addr,
                                           client,
                                           proxy,
                                           request.getAccessControlContext());
        } else if (http2 == null) {
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
            HttpRequestImpl request, Http2Connection http2) {

        HttpConnection c;
        HttpClientImpl client = request.client();
        InetSocketAddress proxy = request.proxy();
        boolean secure = request.secure();
        ConnectionPool pool = client.connectionPool();
        String[] alpn =  null;

        if (secure && request.requestHttp2()) {
            alpn = new String[1];
            alpn[0] = "h2";
        }

        if (!secure) {
            c = pool.getConnection(false, addr, proxy);
            if (c != null) {
                return c;
            } else {
                return getPlainConnection(addr, proxy, request);
            }
        } else {
            c = pool.getConnection(true, addr, proxy);
            if (c != null) {
                return c;
            } else {
                return getSSLConnection(addr, proxy, request, alpn, http2);
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
        if (mode == Mode.BLOCKING)
            channel().configureBlocking(true);
        else
            channel().configureBlocking(false);
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

    /**
     * Closes this connection, by returning the socket to its connection pool.
     */
    @Override
    public abstract void close();

    /**
     * Returns a ByteBuffer with data, or null if EOF.
     */
    final ByteBuffer read() throws IOException {
        return read(-1);
    }

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

    /**
     * Blocks ands return requested amount.
     */
    final ByteBuffer read(int length) throws IOException {
        if (length <= 0) {
            buffer = readImpl(length);
            return buffer;
        }
        buffer = readImpl(length);
        int required = length - buffer.remaining();
        while (buffer.remaining() < length) {
            resumeChannelRead(buffer, required);
            int n = readImpl(buffer);
            required -= n;
        }
        return buffer;
    }

    final int read(ByteBuffer buffer) throws IOException {
        int n = readImpl(buffer);
        return n;
    }

    /** Reads up to length bytes. */
    protected abstract ByteBuffer readImpl(int length) throws IOException;

    /** Reads as much as possible into given buffer and returns amount read. */
    protected abstract int readImpl(ByteBuffer buffer) throws IOException;

    @Override
    public String toString() {
        return "HttpConnection: " + channel().toString();
    }

    @Override
    public final ByteBuffer getBuffer(int n) {
        return client.getBuffer(n);
    }

    @Override
    public final void returnBuffer(ByteBuffer buffer) {
        client.returnBuffer(buffer);
    }

    @Override
    public final void setMinBufferSize(int n) {
        client.setMinBufferSize(n);
    }
}
