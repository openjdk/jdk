/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.httpclient.test.lib.http3;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.RequestPathMatcherUtil;
import jdk.httpclient.test.lib.common.ThrowingConsumer;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.quic.QuicServer;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class Http3TestServer implements QuicServer.ConnectionAcceptor, AutoCloseable {
    private static final AtomicLong IDS = new AtomicLong();

    static final long DECODER_MAX_CAPACITY =
            Utils.getLongProperty("http3.test.server.decoderMaxTableCapacity", 4096);
    static final long ENCODER_CAPACITY_LIMIT =
            Utils.getLongProperty("http3.test.server.encoderTableCapacityLimit", 4096);

    private static final String ALLOWED_HEADERS_PROP_NAME = "http3.test.server.encoderAllowedHeaders";
    static final String ALL_ALLOWED = "*";
    static final List<String> ENCODER_ALLOWED_HEADERS = readEncoderAllowedHeadersProp();

    private static List<String> readEncoderAllowedHeadersProp() {
        String properties = Utils.getProperty(ALLOWED_HEADERS_PROP_NAME);
        if (properties == null) {
            // If the system property is not set all headers are allowed to be encoded
            return List.of(ALL_ALLOWED);
        } else if(properties.isBlank()) {
            // If system property value is a blank string - no headers are
            // allowed to be encoded
            return List.of();
        }
        var headers = Arrays.stream(properties.split(","))
                .filter(Predicate.not(String::isBlank))
                .toList();
        if (headers.contains(ALL_ALLOWED)) {
            return List.of(ALL_ALLOWED);
        }
        return headers;
    }

    private final QuicServer quicServer;
    private volatile boolean stopping;
    private final Map<String, ThrowingConsumer<Http2TestExchange, IOException>> handlers = new ConcurrentHashMap<>();
    private final Function<String, ThrowingConsumer<Http2TestExchange, IOException>> handlerProvider;
    private final Logger debug;
    private final InetSocketAddress serverAddr;
    private volatile ConnectionSettings ourSettings;
    // request approver which takes the server connection key as the input
    private volatile Predicate<String> newRequestApprover;

    private static String nextName() {
        return "h3-server-" + IDS.incrementAndGet();
    }

    /**
     * Same as calling {@code Http3TestServer(sslContext, null)}
     *
     * @param sslContext SSLContext
     * @throws IOException if the server could not be created
     */
    public Http3TestServer(final SSLContext sslContext) throws IOException {
        this(sslContext, null);
    }

    public Http3TestServer(final SSLContext sslContext, final ExecutorService executor) throws IOException {
        this(quicServerBuilder().sslContext(sslContext).executor(executor).build(), null);
    }

    public Http3TestServer(final QuicServer quicServer) throws IOException {
        this(quicServer, null);
    }

    public Http3TestServer(final QuicServer quicServer,
                           final Function<String, ThrowingConsumer<Http2TestExchange, IOException>> handlerProvider)
            throws IOException {
        Objects.requireNonNull(quicServer);
        this.debug = Utils.getDebugLogger(quicServer::name);
        this.quicServer = quicServer;
        this.handlerProvider = handlerProvider;
        this.quicServer.setConnectionAcceptor(this);
        this.serverAddr = bindServer(this.quicServer);
        debug.log(Level.INFO, "H3 server is listening at " + this.serverAddr);
    }

    public void start() {
        quicServer.start();
    }

    public void stop() {
        try {
            close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public QuicServer getQuicServer() {
        return this.quicServer;
    }

    public InetSocketAddress getAddress() {
        return this.serverAddr;
    }

    public String serverAuthority() {
        final InetSocketAddress inetSockAddr = getAddress();
        final String hostIP = inetSockAddr.getAddress().getHostAddress();
        // escape for ipv6
        final String h = hostIP.contains(":") ? "[" + hostIP + "]" : hostIP;
        return h + ":" + inetSockAddr.getPort();
    }

    public void addHandler(final String path, final ThrowingConsumer<Http2TestExchange, IOException> handler) {
        if (this.handlerProvider != null) {
            throw new IllegalStateException("Cannot add handler to H3 server which uses a handler provider");
        }
        Objects.requireNonNull(path);
        Objects.requireNonNull(handler);
        this.handlers.put(path, handler);
    }

    public void setRequestApprover(final Predicate<String> approver) {
        this.newRequestApprover = approver;
    }

    /**
     * Sets the connection settings that will be used by this server to generate a SETTINGS frame
     * to send to client peers
     *
     * @param connectionSettings The connection settings
     * @return The instance of this server
     */
    public Http3TestServer setConnectionSettings(final ConnectionSettings connectionSettings) {
        Objects.requireNonNull(connectionSettings);
        this.ourSettings = connectionSettings;
        return this;
    }

    /**
     * {@return the connection settings of this server, which will be sent to
     * client peers in a SETTINGS frame. If none have been configured then this method returns
     * {@link Optional#empty() empty}}
     */
    public ConnectionSettings getConfiguredConnectionSettings() {
        if (this.ourSettings == null) {
            SettingsFrame settings = SettingsFrame.defaultRFCSettings();
            settings.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, DECODER_MAX_CAPACITY);
            return ConnectionSettings.createFrom(settings);
        }
        return this.ourSettings;
    }

    private static InetSocketAddress bindServer(final QuicServer server) throws IOException {
        // bind the quic server to the socket
        final SocketAddress addr = server.getEndpoint().getLocalAddress();
        if (addr instanceof InetSocketAddress inetsaddr) {
            return inetsaddr;
        }
        throw new IOException(new IOException("Unexpected socket address type "
                + addr.getClass().getName()));
    }

    void submitExchange(final Http2TestExchange exchange) {
        debug.log("H3 server handling exchange for: %s%n\t\t" +
                "(Memory: max=%s, free=%s, total=%s)%n",
                exchange.getRequestURI(), Runtime.getRuntime().maxMemory(),
                Runtime.getRuntime().freeMemory(), Runtime.getRuntime().totalMemory());
        final String reqPath = exchange.getRequestURI().getPath();
        final ThrowingConsumer<Http2TestExchange, IOException> handler;
        if (this.handlerProvider != null) {
            handler = this.handlerProvider.apply(reqPath);
        } else {
            RequestPathMatcherUtil.Resolved<ThrowingConsumer<Http2TestExchange, IOException>> match = null;
            try {
                match = RequestPathMatcherUtil.findHandler(reqPath, this.handlers);
            } catch (IllegalArgumentException iae) {
                // no handler available for the request path
            }
            handler = match == null ? null : match.handler();
        }
        // The server Http3ServerExchange uses a BlockingQueue<ByteBuffer> to
        // read data so handling the exchange in the current thread would
        // wedge it. The executor must have at least one thread and must not
        // execute inline - otherwise, we'd be wedged.
        Thread currentThread = Thread.currentThread();
        this.quicServer.executorService().execute(() -> {
            try {
                // if no handler was located, we respond with a 404
                if (handler == null) {
                    respondForMissingHandler(reqPath, exchange);
                    return;
                }
                // This assertion is too strong: there are cases
                //     where the calling task might terminate before
                //     the submitted task is executed. In which case
                //     it can safely run on the same thread.
                // assert Thread.currentThread() != currentThread
                //        : "HTTP/3 server executor must have at least one thread";
                handler.accept(exchange);
            } catch (Throwable failure) {
                System.err.println("Failed to handle exchange: " + failure);
                failure.printStackTrace();
                final var ioException = (failure instanceof IOException)
                        ? (IOException) failure
                        : new IOException(failure);
                try {
                    exchange.close(ioException);
                } catch (IOException x) {
                    System.err.println("Failed to close exchange: " + x);
                }
            }
        });
    }

    private void respondForMissingHandler(final String reqPath, final Http2TestExchange exchange)
            throws IOException {
        final byte[] responseBody = ("No handler available to handle request " + reqPath)
                .getBytes(US_ASCII);
        try (final OutputStream os = exchange.getResponseBody()) {
            exchange.sendResponseHeaders(404, responseBody.length);
            os.write(responseBody);
        }
    }

    /**
     * Called by the {@link QuicServer} when a new connection has been added to the endpoint's
     * connection map.
     *
     * @param source   The client address
     * @param quicConn the new connection
     * @return true if the new connection should be accepted, false
     * if it should be closed
     */
    @Override
    public boolean acceptIncoming(final SocketAddress source, final QuicServerConnection quicConn) {
        if (stopping) {
            return false;
        }
        debug.log("New connection %s accepted from %s", quicConn.dbgTag(), source);
        quicConn.onSuccessfulHandshake(() -> {
            var http3Connection = new Http3ServerConnection(this, quicConn, source);
            http3Connection.start();
        });
        return true;
    }

    boolean shouldProcessNewHTTPRequest(final Http3ServerConnection serverConn) {
        final Predicate<String> approver = this.newRequestApprover;
        if (approver == null) {
            // by the default the server will process new requests
            return true;
        }
        final String connKey = serverConn.connectionKey();
        return approver.test(connKey);
    }

    @Override
    public void close() throws IOException {
        stopping = true;
        if (debug.on()) {
            debug.log("Stopping H3 server " + this.serverAddr);
        }
        // TODO: should we close the quic server only if we "own" it
        if (this.quicServer != null) {
            this.quicServer.close();
        }
    }

    public static QuicServer.Builder<QuicServer> quicServerBuilder() {
        return new H3QuicBuilder();
    }

    private static final class H3QuicBuilder extends QuicServer.Builder<QuicServer> {

        public H3QuicBuilder() {
            alpn = "h3";
        }

        @Override
        public QuicServer build() throws IOException {
            QuicVersion[] versions = availableQuicVersions;
            if (versions == null) {
                // default to v1 and v2
                versions = new QuicVersion[]{QuicVersion.QUIC_V1, QuicVersion.QUIC_V2};
            }
            if (versions.length == 0) {
                throw new IllegalStateException("Empty available QUIC versions");
            }
            InetSocketAddress addr = bindAddress;
            if (addr == null) {
                // default to loopback address and ephemeral port
                addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            }
            SSLContext ctx = sslContext;
            if (ctx == null) {
                try {
                    ctx = SSLContext.getDefault();
                } catch (NoSuchAlgorithmException e) {
                    throw new IOException(e);
                }
            }
            final QuicTLSContext quicTLSContext = new QuicTLSContext(ctx);
            final String name = serverId == null ? nextName() : serverId;
            return new QuicServer(name, addr, executor, versions, compatible, quicTLSContext, sniMatcher,
                    incomingDeliveryPolicy, outgoingDeliveryPolicy, alpn, Http3Error::stringForCode);
        }
    }
}
