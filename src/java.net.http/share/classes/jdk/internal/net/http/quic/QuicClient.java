/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongFunction;

import javax.net.ssl.SSLParameters;

import jdk.internal.net.http.AltServicesRegistry;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.QuicEndpoint.QuicEndpointFactory;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;

/**
 * This class represents a QuicClient.
 * The QuicClient is responsible for creating/returning instances
 * of QuicConnection for a given AltService, and for linking them
 * with an instance of QuicEndpoint and QuicSelector for reading
 * and writing Datagrams off the network.
 * A QuicClient is also a factory for QuicConnectionIds.
 * There is a 1-1 relationship between a QuicClient and an Http3Client.
 * A QuicClient can be closed: closing a QuicClient will close all
 * quic connections opened on that client.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9369
 *      RFC 9369: QUIC Version 2
 */
public final class QuicClient implements QuicInstance, AutoCloseable {
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong CONNECTIONS = new AtomicLong();

    private final Logger debug = Utils.getDebugLogger(this::name);

    // See quic transport draft 34 section 14
    static final int SMALLEST_MAXIMUM_DATAGRAM_SIZE = 1200;
    static final int INITIAL_SERVER_CONNECTION_ID_LENGTH = 17;
    static final int MAX_ENDPOINTS_LIMIT = 16;
    static final int DEFAULT_MAX_ENDPOINTS = Utils.getIntegerNetProperty(
            "jdk.httpclient.quic.maxEndpoints", 1);

    private final String clientId;
    private final String name;
    private final Executor executor;
    private final QuicTLSContext quicTLSContext;
    private final SSLParameters sslParameters;
    // QUIC versions in their descending order of preference
    private final List<QuicVersion> availableVersions;
    private final InetSocketAddress bindAddress;
    private final QuicTransportParameters transportParams;
    private final ReentrantLock lock = new ReentrantLock();
    private final QuicEndpoint[] endpoints = new QuicEndpoint[computeMaxEndpoints()];
    private int insertionPoint;
    private volatile QuicSelector<?> selector;
    private volatile boolean closed;
    // keep track of any initial tokens that a server has advertised for use. The key in this
    // map is the server's host and port representation and the value is the token to use.
    private final Map<InitialTokenRecipient, byte[]> initialTokens = new ConcurrentHashMap<>();
    private final QuicEndpointFactory endpointFactory = new QuicEndpointFactory();
    private final LongFunction<String> appErrorCodeToString;

    private QuicClient(final QuicClient.Builder builder) {
        Objects.requireNonNull(builder, "Quic client builder");
        if (builder.availableVersions == null) {
            throw new IllegalStateException("Need at least one available Quic version");
        }
        if (builder.tlsContext == null) {
            throw new IllegalStateException("No QuicTLSContext set");
        }
        this.clientId = builder.clientId == null ? nextName() : builder.clientId;
        this.name = "QuicClient(%s)".formatted(clientId);
        this.appErrorCodeToString = builder.appErrorCodeToString == null
                ? QuicInstance.super::appErrorToString
                : builder.appErrorCodeToString;
        // verify that QUIC TLS supports all requested QUIC versions
        var test = new ArrayList<>(builder.availableVersions);
        test.removeAll(builder.tlsContext.createEngine().getSupportedQuicVersions());
        if (!test.isEmpty()) {
            throw new IllegalArgumentException(
                    "Requested QUIC versions not supported by TLS: " + test);
        }
        this.availableVersions = builder.availableVersions;
        this.quicTLSContext = builder.tlsContext;
        this.bindAddress = builder.bindAddr == null ? new InetSocketAddress(0) : builder.bindAddr;
        this.executor = builder.executor;
        this.sslParameters = builder.sslParams == null
                ? new SSLParameters()
                : requireTLS13(builder.sslParams);
        this.transportParams = builder.transportParams;
        if (debug.on()) debug.log("created");
    }


    private static int computeMaxEndpoints() {
        // available processors may change according to the API doc,
        // so recompute this for each new client...
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int max = DEFAULT_MAX_ENDPOINTS <= 0 ? availableProcessors >> 1 : DEFAULT_MAX_ENDPOINTS;
        return Math.clamp(max, 1, MAX_ENDPOINTS_LIMIT);
    }

    // verifies that the TLS protocol(s) configured in SSLParameters, if any,
    // allows TLSv1.3
    private static SSLParameters requireTLS13(final SSLParameters parameters) {
        final String[] protos = parameters.getProtocols();
        if (protos == null || protos.length == 0) {
            // no specific protocols specified, so it's OK
            return parameters;
        }
        for (final String proto : protos) {
            if ("TLSv1.3".equals(proto)) {
                // TLSv1.3 is allowed, that's good
                return parameters;
            }
        }
        // explicit TLS protocols have been configured in SSLParameters and it doesn't
        // include TLSv1.3. QUIC mandates TLSv1.3, so we can't use this SSLParameters
        throw new IllegalArgumentException("TLSv1.3 is required for QUIC," +
                " but SSLParameters is configured with " + Arrays.toString(protos));
    }

    @Override
    public String appErrorToString(long code) {
        return appErrorCodeToString.apply(code);
    }

    @Override
    public QuicTransportParameters getTransportParameters() {
        if (this.transportParams == null) {
            return null;
        }
        // return a copy
        return new QuicTransportParameters(this.transportParams);
    }

    private static String nextName() {
        return "quic-client-" + IDS.incrementAndGet();
    }

    /**
     * The address that the QuicEndpoint will bind to.
     * @implNote By default, this is wildcard:0
     * @return the address that the QuicEndpoint will bind to.
     */
    public InetSocketAddress bindAddress() {
        return bindAddress;
    }

    @Override
    public boolean isVersionAvailable(final QuicVersion quicVersion) {
        return this.availableVersions.contains(quicVersion);
    }

    /**
     *
     * {@return the versions that are available for use on this instance, in the descending order
     * of their preference}
     */
    @Override
    public List<QuicVersion> getAvailableVersions() {
        return this.availableVersions;
    }

    /**
     * Creates a new unconnected {@code QuicConnection} to the given
     * {@code service}.
     *
     * @param service the alternate service for which to create the connection for
     * @return a new unconnected {@code QuicConnection}
     * @throws IllegalArgumentException if the ALPN of this transport isn't the same as that of the
     *                                  passed alternate service
     * @apiNote The caller is expected to call {@link QuicConnectionImpl#startHandshake()} to
     * initiate the handshaking. The connection is considered "connected" when
     * the handshake is successfully completed.
     */
    public QuicConnectionImpl createConnectionFor(final AltServicesRegistry.AltService service) {
        final InetSocketAddress peerAddress = new InetSocketAddress(service.identity().host(),
                service.identity().port());
        final String alpn = service.alpn();
        if (alpn == null) {
            throw new IllegalArgumentException("missing ALPN on alt service");
        }
        final SSLParameters sslParameters = createSSLParameters(new String[]{alpn});
        return new QuicConnectionImpl(null, this, peerAddress,
                service.origin().host(), service.origin().port(), sslParameters, "QuicClientConnection(%s)",
                CONNECTIONS.incrementAndGet());
    }

    /**
     * Creates a new unconnected {@code QuicConnection} to the given
     * {@code peerAddress}.
     *
     * @param peerAddress the address of the peer
     * @return a new unconnected {@code QuicConnection}
     * @apiNote The caller is expected to call {@link QuicConnectionImpl#startHandshake()} to
     * initiate the handshaking. The connection is considered "connected" when
     * the handshake is successfully completed.
     */
    public QuicConnectionImpl createConnectionFor(final InetSocketAddress peerAddress,
                                                  final String[] alpns) {
        Objects.requireNonNull(peerAddress);
        Objects.requireNonNull(alpns);
        if (alpns.length == 0) {
            throw new IllegalArgumentException("at least one ALPN is needed");
        }
        final SSLParameters sslParameters = createSSLParameters(alpns);
        return new QuicConnectionImpl(null, this, peerAddress, peerAddress.getHostString(),
                peerAddress.getPort(), sslParameters, "QuicClientConnection(%s)", CONNECTIONS.incrementAndGet());
    }

    private SSLParameters createSSLParameters(final String[] alpns) {
        final SSLParameters sslParameters = Utils.copySSLParameters(this.getSSLParameters());
        sslParameters.setApplicationProtocols(alpns);
        // section 4.2, RFC-9001 (QUIC) Clients MUST NOT offer TLS versions older than 1.3
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        return sslParameters;
    }

    @Override
    public String instanceId() {
        return clientId;
    }

    @Override
    public QuicTLSContext getQuicTLSContext() {
        return quicTLSContext;
    }

    @Override
    public SSLParameters getSSLParameters() {
        return Utils.copySSLParameters(sslParameters);
    }

    /**
     * The name identifying this QuicClient, used in debug traces.
     * @implNote This is {@code "QuicClient(<clientId>)"}.
     * @return the name identifying this QuicClient.
     */
    public String name() {
        return name;
    }

    /**
     * The HttpClientImpl Id. used to identify the client in
     * debug traces.
     * @return A string identifying the HttpClientImpl instance.
     */
    public String clientId() {
        return clientId;
    }

    /**
     * The executor used by this QuicClient when a task needs to
     * be offloaded to a separate thread.
     * @implNote This is the HttpClientImpl internal executor.
     * @return the executor used by this QuicClient.
     */
    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public QuicEndpoint getEndpoint() throws IOException {
        return chooseEndpoint();
    }

    private QuicEndpoint chooseEndpoint() throws IOException {
        QuicEndpoint endpoint;
        lock.lock();
        try {
            if (closed) throw new IllegalStateException("QuicClient is closed");
            int index = insertionPoint;
            if (index >= endpoints.length) index = 0;
            endpoint = endpoints[index];
            if (endpoint != null) {
                if (endpoints.length == 1) return endpoint;
                if (endpoint.connectionCount() < 2) return endpoint;
                for (int i = 1; i < endpoints.length - 1; i++) {
                    var nexti = (index + i) % endpoints.length;
                    var next = endpoints[nexti];
                    if (next == null) continue;
                    if (next.connectionCount() < endpoint.connectionCount()) {
                        endpoint = next;
                        index = nexti;
                    }
                }
                if (++index >= endpoints.length) index = 0;
                insertionPoint = index;

                if (Log.quicControl()) {
                    Log.logQuic("Selecting endpoint: " + endpoint.name());
                } else if (debug.on()) {
                    debug.log("Selecting endpoint: " + endpoint.name());
                }

                return endpoint;
            }

            final var endpointName = "QuicEndpoint(" + clientId + "-" + index + ")";
            if (Log.quicControl()) {
                Log.logQuic("Adding new endpoint: " + endpointName);
            } else if (debug.on()) {
                debug.log("Adding new endpoint: " + endpointName);
            }
            endpoint = createEndpoint(endpointName);
            assert endpoints[index] == null;
            endpoints[index] = endpoint;
            insertionPoint = index + 1;
        } finally {
            lock.unlock();
        }
        // register the newly created endpoint with the selector
        QuicEndpoint.registerWithSelector(endpoint, selector, debug);
        return endpoint;
    }

    /**
     * Creates an endpoint with the given name, and register it with a selector.
     * @return the new QuicEndpoint
     * @throws IOException if an error occurs when setting up the selector
     *      or linking the transport with the selector.
     * @throws IllegalStateException if the client is closed.
     */
    private QuicEndpoint createEndpoint(final String endpointName) throws IOException {
        var selector = this.selector;
        boolean newSelector = false;
        final QuicEndpoint.ChannelType configuredChannelType = QuicEndpoint.CONFIGURED_CHANNEL_TYPE;
        if (selector == null) {
            // create a selector first
            lock.lock();
            try {
                if (closed) {
                    throw new IllegalStateException("QuicClient is closed");
                }
                selector = this.selector;
                if (selector == null) {
                    final String selectorName = "QuicSelector(" + clientId + ")";
                    selector = this.selector = switch (configuredChannelType) {
                        case NON_BLOCKING_WITH_SELECTOR ->
                                QuicSelector.createQuicNioSelector(this, selectorName);
                        case BLOCKING_WITH_VIRTUAL_THREADS ->
                                QuicSelector.createQuicVirtualThreadPoller(this, selectorName);
                    };
                    newSelector = true;
                }
            } finally {
                lock.unlock();
            }
        }
        if (newSelector) {
            // we may be closed when we reach here. It doesn't matter though.
            // if the selector is closed before it's started the thread will
            // immediately exit (or exit after the first wakeup)
            selector.start();
        }
        final QuicEndpoint endpoint = switch (configuredChannelType) {
            case NON_BLOCKING_WITH_SELECTOR ->
                    endpointFactory.createSelectableEndpoint(this, endpointName,
                            bindAddress(), selector.timer());
            case BLOCKING_WITH_VIRTUAL_THREADS ->
                    endpointFactory.createVirtualThreadedEndpoint(this, endpointName,
                            bindAddress(), selector.timer());
        };
        assert endpoint.channelType() == configuredChannelType
                : "bad endpoint for " + configuredChannelType + ": " + endpoint.getClass();
        return endpoint;
    }

    @Override
    public void unmatchedQuicPacket(SocketAddress source, QuicPacket.HeadersType type, ByteBuffer buffer) {
        if (debug.on()) {
            debug.log("dropping unmatched packet in buffer [%s, %d bytes, %s]",
                    type, buffer.remaining(), source);
        }
    }

    /**
     * @param peerAddress The address of the server
     * @return the initial token to use in INITIAL packets during connection establishment
     * against a server represented by the {@code peerAddress}. Returns null if no token exists for
     * the server.
     */
    byte[] initialTokenFor(final InetSocketAddress peerAddress) {
        if (peerAddress == null) {
            return null;
        }
        final InitialTokenRecipient recipient = new InitialTokenRecipient(peerAddress.getHostString(),
                peerAddress.getPort());
        // an initial token (obtained through NEW_TOKEN frame) can be used only once against the
        // peer which advertised it. Hence, we remove it.
        return this.initialTokens.remove(recipient);
    }

    /**
     * Registers a token to use in INITIAL packets during connection establishment against a server
     * represented by the {@code peerAddress}.
     *
     * @param peerAddress The address of the server
     * @param token       The token to use
     * @throws NullPointerException     If either of {@code peerAddress} or {@code token} is null
     * @throws IllegalArgumentException If the token is of zero length
     */
    void registerInitialToken(final InetSocketAddress peerAddress, final byte[] token) {
        Objects.requireNonNull(peerAddress);
        Objects.requireNonNull(token);
        if (token.length == 0) {
            throw new IllegalArgumentException("Empty token");
        }
        final InitialTokenRecipient recipient = new InitialTokenRecipient(peerAddress.getHostString(),
                peerAddress.getPort());
        // multiple initial tokens (through NEW_TOKEN frame) can be sent by the same peer, but as
        // per RFC-9000, section 8.1.3, it's OK for clients to just use the last received token,
        // since the rest are less likely to be useful
        this.initialTokens.put(recipient, token);
    }

    @Override
    public void close() {
        // TODO: ignore exceptions while closing?
        lock.lock();
        try {
            if (closed) return;
            closed = true;
        } finally {
            lock.unlock();
        }
        for (int i = 0 ; i < endpoints.length ; i++) {
            var endpoint = endpoints[i];
            if (endpoint != null) closeEndpoint(endpoint);
        }
        var selector = this.selector;
        if (selector != null) selector.close();
    }

    private void closeEndpoint(QuicEndpoint endpoint) {
        try { endpoint.close(); } catch (Throwable t) {
            if (debug.on()) {
                debug.log("Failed to close endpoint: %s: %s", endpoint.name(), t);
            }
        }
    }

    // Called in case of RejectedExecutionException, or shutdownNow;
    public void abort(Throwable t) {
        lock.lock();
        try {
            if (closed) return;
            closed = true;
        } finally {
            lock.unlock();
        }
        for (int i = 0 ; i < endpoints.length ; i++) {
            var endpoint = endpoints[i];
            if (endpoint != null) abortEndpoint(endpoint, t);
        }
        var selector = this.selector;
        if (selector != null) selector.abort(t);
    }

    private void abortEndpoint(QuicEndpoint endpoint, Throwable cause) {
        try { endpoint.abort(cause); } catch (Throwable t) {
            if (debug.on()) {
                debug.log("Failed to abort endpoint: %s: %s", endpoint.name(), t);
            }
        }
    }

    private record InitialTokenRecipient (String host, int port) {
    }

    public static final class Builder {
        private String clientId;
        private List<QuicVersion> availableVersions;
        private Executor executor;
        private SSLParameters sslParams;
        private QuicTLSContext tlsContext;
        private QuicTransportParameters transportParams;
        private InetSocketAddress bindAddr;
        private LongFunction<String> appErrorCodeToString;

        public Builder availableVersions(final List<QuicVersion> versions) {
            Objects.requireNonNull(versions, "Quic versions");
            if (versions.isEmpty()) {
                throw new IllegalArgumentException("Need at least one available Quic version");
            }
            this.availableVersions = List.copyOf(versions);
            return this;
        }

        public Builder applicationErrors(LongFunction<String> errorCodeToString) {
            this.appErrorCodeToString = errorCodeToString;
            return this;
        }

        public Builder availableVersions(final QuicVersion version, final QuicVersion... more) {
            Objects.requireNonNull(version, "Quic version");
            if (more == null) {
                this.availableVersions = List.of(version);
                return this;
            }
            final List<QuicVersion> versions = new ArrayList<>();
            versions.add(version);
            for (final QuicVersion v : more) {
                Objects.requireNonNull(v, "Quic version");
                versions.add(v);
            }
            this.availableVersions = List.copyOf(versions);
            return this;
        }

        public Builder clientId(final String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder tlsContext(final QuicTLSContext tlsContext) {
            this.tlsContext = tlsContext;
            return this;
        }

        public Builder sslParameters(final SSLParameters sslParameters) {
            this.sslParams = sslParameters;
            return this;
        }

        public Builder bindAddress(final InetSocketAddress bindAddr) {
            this.bindAddr = bindAddr;
            return this;
        }

        public Builder executor(final Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder transportParameters(final QuicTransportParameters transportParams) {
            this.transportParams = transportParams;
            return this;
        }

        public QuicClient build() {
            return new QuicClient(this);
        }
    }
}
