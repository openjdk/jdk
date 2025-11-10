/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.httpclient.test.lib.quic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongFunction;

import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import jdk.httpclient.test.lib.common.ServerNameMatcher;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.PeerConnectionId;
import jdk.internal.net.http.quic.QuicConnection;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.QuicEndpoint;
import jdk.internal.net.http.quic.QuicEndpoint.QuicEndpointFactory;
import jdk.internal.net.http.quic.QuicInstance;
import jdk.internal.net.http.quic.QuicSelector;
import jdk.internal.net.http.quic.QuicTransportParameters;
import jdk.internal.net.http.quic.packets.LongHeader;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacketDecoder;
import jdk.internal.net.http.quic.packets.QuicPacketEncoder;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;
import static jdk.internal.net.http.quic.TerminationCause.forTransportError;
import static jdk.internal.net.quic.QuicTransportErrors.CONNECTION_REFUSED;

/**
 * This class represents a QuicServer.
 */
public sealed class QuicServer implements QuicInstance, AutoCloseable permits QuicStandaloneServer {

    public interface ConnectionAcceptor {
        boolean acceptIncoming(SocketAddress source, QuicServerConnection quicConnection);
    }

    final Logger debug = Utils.getDebugLogger(this::name);

    private final String serverId;
    private final String name;
    private final ExecutorService executor;
    private final boolean ownExecutor;
    private volatile ConnectionAcceptor newConnectionAcceptor;
    private final String alpn;
    private final InetSocketAddress bindAddress;
    private final SNIMatcher sniMatcher;
    private volatile InetSocketAddress listenAddress;
    private final QuicTLSContext quicTLSContext;
    private volatile boolean started;
    private volatile boolean sendRetry;
    protected final List<QuicVersion> availableQuicVersions;
    private final QuicVersion preferredVersion;
    private volatile QuicEndpoint endpoint;
    private volatile QuicSelector<?> selector;
    private volatile boolean closed;
    private volatile QuicTransportParameters transportParameters;
    private final byte[] retryTokenPrefixBytes = new byte[4];
    private final byte[] newTokenPrefixBytes = new byte[4];
    private final DatagramDeliveryPolicy incomingDeliveryPolicy;
    private final DatagramDeliveryPolicy outgoingDeliveryPolicy;
    private boolean wantClientAuth;
    private boolean needClientAuth;
    // set of KeyAgreement algorithms to reject; used to force a HelloRetryRequest
    private Set<String> rejectedKAAlgos;
    // used to compute MAX_STREAMS limit by connections created on this server instance.
    // if null, then an internal algorithm is used to compute the limit.
    // The Function takes a boolean argument whose value is true if the computation is for bidi
    // streams and false for uni streams. The returned value from this function is expected
    // to be the MAX_STREAMS limit to be imposed on the peer for that particular stream type
    private volatile Function<Boolean, Long> maxStreamLimitComputer;

    private final ReentrantLock quickServerLock = new ReentrantLock();
    private final LongFunction<String> appErrorCodeToString;

    record RetryData(QuicConnectionId originalServerConnId,
                     QuicConnectionId serverChosenConnId) {
    }

    public static abstract class Builder<T extends QuicServer> {
        protected SSLContext sslContext;
        protected InetSocketAddress bindAddress;
        protected DatagramDeliveryPolicy incomingDeliveryPolicy;
        protected DatagramDeliveryPolicy outgoingDeliveryPolicy;
        protected String serverId;
        protected SNIMatcher sniMatcher;
        protected ExecutorService executor;
        protected QuicVersion[] availableQuicVersions;
        protected boolean compatible;
        protected LongFunction<String> appErrorCodeToString;

        protected ConnectionAcceptor connAcceptor =
                (source, conn) -> {
                    System.err.println("Rejecting connection " + conn + " attempt from source "
                            + source);
                    return false;
                };

        protected String alpn;

        protected Builder() {
            try {
                incomingDeliveryPolicy = DatagramDeliveryPolicy.defaultIncomingPolicy();
                outgoingDeliveryPolicy = DatagramDeliveryPolicy.defaultOutgoingPolicy();
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        public Builder<T> availableVersions(final QuicVersion[] available) {
            Objects.requireNonNull(available);
            if (available.length == 0) {
                throw new IllegalArgumentException("Empty available versions");
            }
            this.availableQuicVersions = available;
            return this;
        }

        public Builder<T> appErrorCodeToString(LongFunction<String> appErrorCodeToString) {
            this.appErrorCodeToString = appErrorCodeToString;
            return this;
        }

        public Builder<T> compatibleNegotiation(boolean compatible) {
            this.compatible = compatible;
            return this;
        }

        public Builder<T> sslContext(final SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder<T> sniMatcher(final SNIMatcher sniMatcher) {
            this.sniMatcher = sniMatcher;
            return this;
        }

        public Builder<T> bindAddress(final InetSocketAddress addr) {
            Objects.requireNonNull(addr);
            this.bindAddress = addr;
            return this;
        }

        public Builder<T> serverId(final String serverId) {
            Objects.requireNonNull(serverId);
            this.serverId = serverId;
            return this;
        }

        public Builder<T> incomingDeliveryPolicy(final DatagramDeliveryPolicy policy) {
            Objects.requireNonNull(policy);
            this.incomingDeliveryPolicy = policy;
            return this;
        }

        public Builder<T> outgoingDeliveryPolicy(final DatagramDeliveryPolicy policy) {
            Objects.requireNonNull(policy);
            this.outgoingDeliveryPolicy = policy;
            return this;
        }

        public Builder<T> executor(final ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder<T> alpn(String alpn) {
            this.alpn = alpn;
            return this;
        }

        public abstract T build() throws IOException;
    }

    private final QuicEndpointFactory endpointFactory;
    public QuicServer(final String serverId, final InetSocketAddress bindAddress,
                      final ExecutorService executor, final QuicVersion[] availableQuicVersions,
                      boolean compatible, final QuicTLSContext quicTLSContext, final SNIMatcher sniMatcher,
                      final DatagramDeliveryPolicy incomingDeliveryPolicy,
                      final DatagramDeliveryPolicy outgoingDeliveryPolicy, String alpn,
                      final LongFunction<String> appErrorCodeToString) {
        this.bindAddress = bindAddress;
        this.sniMatcher = sniMatcher == null
                ? new ServerNameMatcher(this.bindAddress.getHostName())
                : sniMatcher;
        this.alpn = Objects.requireNonNull(alpn);
        this.appErrorCodeToString = appErrorCodeToString == null
                ? QuicInstance.super::appErrorToString
                : appErrorCodeToString;
        if (executor != null) {
            this.executor = executor;
            this.ownExecutor = false;
        } else {
            this.executor = Utils.safeExecutor(
                    createExecutor(serverId),
                    (_, t) -> debug.log("rejected task - using ASYNC_POOL", t));
            this.ownExecutor = true;
        }
        this.serverId = serverId;
        this.quicTLSContext = quicTLSContext;
        this.name = "QuicServer(%s)".formatted(serverId);
        if (compatible) {
            this.availableQuicVersions = Arrays.asList(QuicVersion.values());
        } else {
            this.availableQuicVersions = Arrays.asList(availableQuicVersions);
        }
        this.preferredVersion = availableQuicVersions[0];
        this.incomingDeliveryPolicy = incomingDeliveryPolicy;
        this.outgoingDeliveryPolicy = outgoingDeliveryPolicy;
        final Random random = new Random();
        random.nextBytes(retryTokenPrefixBytes);
        random.nextBytes(newTokenPrefixBytes);
        this.endpointFactory = newQuicEndpointFactory();
        if (debug.on()) {
            debug.log("server created, incoming delivery policy %s, outgoing delivery policy %s",
                    this.incomingDeliveryPolicy, this.outgoingDeliveryPolicy);
        }
    }

    private static ExecutorService createExecutor(String name) {
        String threadNamePrefix = "%s-quic-pool".formatted(name);
        ThreadFactory threadFactory = Thread.ofPlatform().name(threadNamePrefix, 0).factory();
        return Executors.newCachedThreadPool(threadFactory);
    }

    @Override
    public String appErrorToString(long errorCode) {
        return appErrorCodeToString.apply(errorCode);
    }

    static QuicEndpointFactory newQuicEndpointFactory() {
        return new QuicEndpointFactory();
    }

    public void setConnectionAcceptor(final ConnectionAcceptor acceptor) {
        Objects.requireNonNull(acceptor);
        quickServerLock.lock();
        try {
            var current = this.newConnectionAcceptor;
            if (current != null) {
                throw new IllegalStateException("An connection acceptor already exists for" +
                        " this quic server " + this);
            }
            this.newConnectionAcceptor = acceptor;
        } finally {
            quickServerLock.unlock();
        }
    }

    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }

    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    public void setRejectKeyAgreement(Set<String> rejectedKAAlgos) {
        this.rejectedKAAlgos = rejectedKAAlgos;
    }

    Function<Boolean, Long> getMaxStreamLimitComputer() {
        return this.maxStreamLimitComputer;
    }

    /**
     * Sets a new MAX_STREAMS limit computer for this server.
     * @param computer the limit computer. can be null, in which case an internal computation
     *                 algorithm with decide the MAX_STREAMS limit for connections on this server
     *                 instance
     */
    public void setMaxStreamLimitComputer(final Function<Boolean, Long> computer) {
        this.maxStreamLimitComputer = computer;
    }

    @Override
    public String instanceId() {
        return serverId;
    }

    @Override
    public QuicTLSContext getQuicTLSContext() {
        return quicTLSContext;
    }

    @Override
    public boolean isVersionAvailable(QuicVersion quicVersion) {
        return availableQuicVersions.contains(quicVersion);
    }

    @Override
    public List<QuicVersion> getAvailableVersions() {
        return availableQuicVersions;
    }

    public void sendRetry(final boolean enable) {
        this.sendRetry = enable;
    }

    public void start() {
        this.started = true;
        try {
            final QuicEndpoint endpoint = getEndpoint();
            final InetSocketAddress addr = this.listenAddress = (InetSocketAddress) endpoint.getLocalAddress();
            if (debug.on()) {
                debug.log("Quic server listening at: " + addr
                        + " supported versions " + this.availableQuicVersions);
            }
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * {@return the address on which the server is listening on}
     *
     * @throws IllegalStateException If server hasn't yet started
     */
    public InetSocketAddress getAddress() {
        final var addr = this.listenAddress;
        if (addr == null) {
            throw new IllegalArgumentException("Server hasn't started");
        }
        return addr;
    }

    /**
     * The name identifying this QuicServer, used in debug traces.
     *
     * @return the name identifying this QuicServer.
     * @implNote This is {@code "QuicServer(<serverId>)"}.
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * The executor used by this QuicServer when a task needs to
     * be offloaded to a separate thread.
     *
     * @return the executor used by this QuicServer.
     * @implNote This is the server internal executor.
     */
    @Override
    public Executor executor() {
        return executor;
    }

    public ExecutorService executorService() {
        return this.executor;
    }

    /**
     * Get the QuicEndpoint for the given transport.
     *
     * @return the QuicEndpoint for the given transport.
     * @throws IOException           if an error occurs when setting up the endpoint
     *                               or linking the transport with the endpoint.
     * @throws IllegalStateException if the server is closed.
     */
    @Override
    public QuicEndpoint getEndpoint() throws IOException {
        var endpoint = this.endpoint;
        if (endpoint != null) return endpoint;
        var selector = getSelector();
        quickServerLock.lock();
        try {
            if (closed) throw new IllegalStateException("QuicServer is closed");
            endpoint = this.endpoint;
            if (endpoint != null) return endpoint;
            final String endpointName = "QuicEndpoint(" + serverId + ")";
            endpoint = this.endpoint = switch (QuicEndpoint.CONFIGURED_CHANNEL_TYPE) {
                case NON_BLOCKING_WITH_SELECTOR ->
                        endpointFactory.createSelectableEndpoint(this, endpointName,
                                bindAddress, selector.timer());
                case BLOCKING_WITH_VIRTUAL_THREADS ->
                        endpointFactory.createVirtualThreadedEndpoint(this, endpointName,
                                bindAddress, selector.timer());
            };
        } finally {
            quickServerLock.unlock();
        }
        // register the newly created endpoint with the selector
        QuicEndpoint.registerWithSelector(endpoint, selector, debug);
        return endpoint;
    }

    /**
     * Gets the QuicSelector for the transport.
     *
     * @return the QuicSelector for the given transport.
     * @throws IOException           if an error occurs when setting up the selector
     *                               or linking the transport with the selector.
     * @throws IllegalStateException if the server is closed.
     */
    private QuicSelector<?> getSelector() throws IOException {
        var selector = this.selector;
        if (selector != null) return selector;
        quickServerLock.lock();
        try {
            if (closed) throw new IllegalStateException("QuicServer is closed");
            selector = this.selector;
            if (selector != null) return selector;
            final String selectorName = "QuicSelector(" + serverId + ")";
            selector = this.selector = switch (QuicEndpoint.CONFIGURED_CHANNEL_TYPE) {
                case NON_BLOCKING_WITH_SELECTOR ->
                        QuicSelector.createQuicNioSelector(this, selectorName);
                case BLOCKING_WITH_VIRTUAL_THREADS ->
                        QuicSelector.createQuicVirtualThreadPoller(this, selectorName);
            };
        } finally {
            quickServerLock.unlock();
        }
        // we may be closed when we reach here. It doesn't matter though.
        // if the selector is closed before it's started the thread will
        // immediately exit (or exit after the first wakeup)
        debug.log("starting selector");
        selector.start();
        return selector;
    }

    @Override
    public void unmatchedQuicPacket(SocketAddress source, QuicPacket.HeadersType type, ByteBuffer buffer) {
        if (debug.on()) {
            debug.log("Received datagram %s(src=%s, payload(%d))", type, source, buffer.remaining());
        }
        // consult the delivery policy to see if we should silently drop this packet
        if (this.incomingDeliveryPolicy.shouldDrop(source, buffer, type)) {
            silentIgnorePacket(source, buffer, type, false);
            return;
        }
        // check packet type. If Initial, it may be a connection attempt
        int pos = buffer.position();
        if (type != QuicPacket.HeadersType.LONG) {
            if (debug.on()) {
                debug.log("Dropping unmatched datagram %s(src=%s, payload(%d))",
                        type, source, buffer.remaining());
            }
            return;
        }
        // INITIAL packet
        // decode packet here
        // TODO: FIXME
        //    Transport: is this needed?
        //    ALPN, etc...
        //    Move this to a dedicated method
        //    Double check how the serverId provided by the client should
        //        be replaced
        //    Should the new connection have 2 connections id for a time?
        //       => the initial one that the client sent, and that will
        //          be used until the client receives our response,
        //       => the new connection id that we are sending back to the
        //          client?
        LongHeader header = QuicPacketDecoder.peekLongHeader(buffer);
        if (header == null) {
            if (debug.on()) {
                debug.log("Dropping invalid datagram %s(src=%s, payload(%d))",
                        type, source, buffer.remaining());
            }
            return;
        }
        // need to assert that dest.remaining() >= 8 and drop the packet
        // if this is not the case.
        if (header.destinationId().length() < 8) {
            debug.log("destination connection id has not enough bytes: %d",
                    header.destinationId().length());
            return;
        }

        final QuicVersion version = QuicVersion.of(header.version()).orElse(null);
        try {
            // check that the server supports the version, send a version
            if (header.version() == 0) {
                if (debug.on()) {
                    debug.log("Stray version negotiation packet");
                }
                return;
            }
            if (version == null || !availableQuicVersions.contains(version)) {
                if (debug.on()) {
                    debug.log("Unsupported version number 0x%x in incoming packet (len=%d)", header.version(), buffer.remaining());
                }
                if (buffer.remaining() >= QuicConnectionImpl.SMALLEST_MAXIMUM_DATAGRAM_SIZE) {
                    // A server might not send a Version Negotiation packet if the datagram it receives is smaller than the minimum size specified in a different version
                    int[] supported = availableQuicVersions.stream().mapToInt(QuicVersion::versionNumber).toArray();
                    var negotiate = QuicPacketEncoder
                            .newVersionNegotiationPacket(header.destinationId(),
                            header.sourceId(), supported);
                    ByteBuffer datagram = ByteBuffer.allocateDirect(negotiate.size());
                    QuicPacketEncoder.of(QuicVersion.QUIC_V1).encode(negotiate, datagram, null);
                    datagram.flip();
                    sendDatagram(source, datagram);
                }
                return;
            }
        } catch (Throwable t) {
            debug.log("Failed to decode packet", t);
            return;
        }
        assert availableQuicVersions.contains(version);
        final InetSocketAddress peerAddress = (InetSocketAddress) source;
        final ByteBuffer token = QuicPacketDecoder.peekInitialPacketToken(buffer);
        if (token == null) {
            // packet is malformed: token will be an empty ByteBuffer if
            //      the packet doesn't contain a token.
            debug.log("failed to read connection token");
            return;
        }
        var localAddress = this.getAddress();
        var conflict = Utils.addressConflict(localAddress, peerAddress);
        if (conflict != null) {
            String msg = "%s: %s (local:%s == peer:%s)!";
            System.out.println(msg.formatted(this, conflict, localAddress, peerAddress));
            debug.log(msg, "WARNING", conflict, localAddress, peerAddress);
            Log.logError(msg.formatted(this, conflict, localAddress, peerAddress));
        }
        final QuicServerConnection connection;
        if (token.hasRemaining()) {
            // the INITIAL packet contains a token. This token is then expected to either match
            // the RETRY packet token that this server might have sent (if any) or a NEW_TOKEN frame
            // token that this server might have sent (if any). If the token doesn't match either
            // of these expectations then drop the packet (or send CLOSE_CONNECTION frame)
            final RetryData retryData = isRetryToken(token.asReadOnlyBuffer());
            if (retryData != null) {
                // the token matches one that this server could have sent as a RETRY token. verify
                // that this server was indeed configured to send a RETRY token.
                if (!sendRetry) {
                    // although the token looks like a RETRY token, this server wasn't configured
                    // to send a RETRY token, so consider this an invalid token and drop the packet
                    // (or send CLOSE_CONNECTION frame)
                    debug.log("Server dropping INITIAL packet due to token " +
                            "(which looks like an unexpected retry token) from " + peerAddress);
                    return;
                }
                // verify the dest connection id in the INITIAL packet is the one that we had asked
                // the client to use through our RETRY packet
                if (!retryData.serverChosenConnId.equals(header.destinationId())) {
                    // drop the packet
                    debug.log("Invalid dest connection id in INITIAL packet," +
                            " expected the one sent in RETRY packet " + retryData.serverChosenConnId
                            + " but found a different one " + header.destinationId());
                    return;
                }
                // at this point we have verified that the token is a valid retry token that this server
                // sent. We can now create a connection
                final SSLParameters sslParameters = createSSLParameters(peerAddress);
                final byte[] clientInitialToken = new byte[token.remaining()];
                token.get(clientInitialToken);
                connection = new QuicServerConnection(this, version, preferredVersion,
                        peerAddress, header.destinationId(),
                        sslParameters, clientInitialToken, retryData);
                debug.log("Created new server connection " + connection + " (with a retry token) " +
                        "to client " + peerAddress);
            } else {
                // token doesn't match a RETRY token. check if it is a NEW_TOKEN that this server
                // sent
                final boolean isNewToken = isNewToken(token.asReadOnlyBuffer());
                if (!isNewToken) {
                    // invalid token in the INITIAL packet. drop packet (or send CLOSE_CONNECTION
                    // frame)
                    debug.log("Server dropping INITIAL packet due to unexpected token from "
                            + peerAddress);
                    return;
                }
                // matches a NEW_TOKEN token. create the connection
                final SSLParameters sslParameters = createSSLParameters(peerAddress);
                final byte[] clientInitialToken = new byte[token.remaining()];
                token.get(clientInitialToken);
                connection = new QuicServerConnection(this, version, preferredVersion,
                        peerAddress, header.destinationId(),
                        sslParameters, clientInitialToken);
                debug.log("Created new server connection " + connection
                        + " (with NEW_TOKEN initial token) to client " + peerAddress);
            }
        } else {
            // token is empty in INITIAL packet. send a RETRY packet if the server is configured
            // to do so. The spec allows us to send the RETRY packet more than once to the same
            // client, so we don't have to maintain any state to check if we already have sent one.
            if (sendRetry) {
                // send RETRY packet
                final QuicConnectionId serverConnId = this.endpoint.idFactory().newConnectionId();
                final byte[] retryToken = buildRetryToken(header.destinationId(), serverConnId);
                QuicPacketEncoder encoder = QuicPacketEncoder.of(version);
                final var retry = encoder
                        .newRetryPacket(serverConnId, header.sourceId(), retryToken);
                final ByteBuffer datagram = ByteBuffer.allocateDirect(retry.size());
                try {
                    encoder.encode(retry, datagram, new RetryCodingContext(header.destinationId(), quicTLSContext));
                } catch (Throwable t) {
                    // TODO: should we throw exception?
                    debug.log("Failed to encode packet", t);
                    return;
                }
                datagram.flip();
                debug.log("Sending RETRY packet to client " + peerAddress);
                sendDatagram(source, datagram);
                return;
            }
            // no token in INITIAL frame and the server isn't configured to send a RETRY packet.
            // we are now ready to create the connection
            final SSLParameters sslParameters = createSSLParameters(peerAddress);
            connection = new QuicServerConnection(this, version, preferredVersion,
                    peerAddress, header.destinationId(),
                    sslParameters, null);
            debug.log("Created new server connection " + connection
                    + " (without any initial token) to client " + peerAddress);
        }
        assert connection.quicVersion() == version;

        // TODO: maybe we should coalesce some dummy packet in the datagram
        //       to make sure the client will ignore it
        //       => this might require slightly altering the algorithm for
        //          encoding packets:
        //          we may need to build a packet, and then only encode it
        //          instead of having a toByteBuffer() method.
        try {
            endpoint.registerNewConnection(connection);
        } catch (IOException io) {
            if (closed) {
                debug.log("Can't register new connection: server closed");
            } else if (debug.on()) {
                debug.log("Can't register new connection", io);
            }
            // drop all bytes in the payload
            buffer.position(buffer.limit());
            connection.connectionTerminator().terminate(
                    forTransportError(CONNECTION_REFUSED).loggedAs(io.getMessage()));
            return;
        }
        connection.processIncoming(source, header.destinationId().asReadOnlyBuffer(), type, buffer);

        final ConnectionAcceptor connAcceptor = this.newConnectionAcceptor;
        if (connAcceptor == null || !connAcceptor.acceptIncoming(source, connection)) {
            buffer.position(buffer.limit());
            final String msg = "Quic server " + this.serverId + " refused connection";
            connection.connectionTerminator().terminate(
                    forTransportError(CONNECTION_REFUSED).loggedAs(msg));
            return;
        }
    }

    void sendDatagram(final SocketAddress dest, final ByteBuffer datagram) {
        final QuicPacket.HeadersType headersType = QuicPacketDecoder.peekHeaderType(datagram,
                datagram.position());
        if (this.outgoingDeliveryPolicy.shouldDrop(dest, datagram, headersType)) {
            silentIgnorePacket(dest, datagram, headersType, true);
            return;
        }
        endpoint.pushDatagram(null, dest, datagram);
        return;
    }

    private SSLParameters createSSLParameters(final InetSocketAddress peerAddress) {
        final SSLParameters sslParameters = Utils.copySSLParameters(this.getSSLParameters());
        sslParameters.setApplicationProtocols(new String[]{this.alpn});
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        if (this.sniMatcher != null) {
            sslParameters.setSNIMatchers(List.of(this.sniMatcher));
        }
        if (wantClientAuth) {
            sslParameters.setWantClientAuth(true);
        } else if (needClientAuth) {
            sslParameters.setNeedClientAuth(true);
        }
        if (rejectedKAAlgos != null) {
            sslParameters.setAlgorithmConstraints(new TestAlgorithmConstraints(rejectedKAAlgos));
        }
        return sslParameters;
    }

    private byte[] buildRetryToken(final QuicConnectionId originalServerConnId,
                                   final QuicConnectionId serverChosenNewConnId) {
        // TODO this token is too simple to provide authenticity guarantee; use for testing only
        final int NUM_BYTES_FOR_CONN_ID_LENGTH = 1;
        final byte[] result = new byte[retryTokenPrefixBytes.length
                + NUM_BYTES_FOR_CONN_ID_LENGTH + originalServerConnId.length()
                + NUM_BYTES_FOR_CONN_ID_LENGTH + serverChosenNewConnId.length()];
        // copy the retry token prefix
        System.arraycopy(retryTokenPrefixBytes, 0, result, 0, retryTokenPrefixBytes.length);
        int currentIndex = retryTokenPrefixBytes.length;
        // copy over the length of the original dest conn id
        result[currentIndex++] = (byte) originalServerConnId.length();
        // copy over the original dest connection id sent by the client
        originalServerConnId.asReadOnlyBuffer().get(0, result, currentIndex, originalServerConnId.length());
        currentIndex += originalServerConnId.length();
        // copy over the length of the server chosen dest conn id
        result[currentIndex++] = (byte) serverChosenNewConnId.length();
        // copy over the connection id that the server has chosen and expects clients to use as new dest conn id
        serverChosenNewConnId.asReadOnlyBuffer().get(0, result, currentIndex, serverChosenNewConnId.length());
        return result;
    }

    private RetryData isRetryToken(final ByteBuffer token) {
        Objects.requireNonNull(token);
        if (!token.hasRemaining()) {
            return null;
        }
        final int NUM_BYTES_FOR_CONN_ID_LENGTH = 1;
        // we expect the retry token prefix and 2 connection ids. so expected length = retry token prefix
        // length plus the length of each of the connection ids
        final int expectedLength = retryTokenPrefixBytes.length + NUM_BYTES_FOR_CONN_ID_LENGTH
                + NUM_BYTES_FOR_CONN_ID_LENGTH;
        if (token.remaining() <= expectedLength) {
            return null;
        }
        final byte[] tokenPrefixBytes = new byte[retryTokenPrefixBytes.length];
        token.get(tokenPrefixBytes);
        int mismatchIndex = Arrays.mismatch(retryTokenPrefixBytes, 0, retryTokenPrefixBytes.length,
                tokenPrefixBytes, 0, retryTokenPrefixBytes.length);
        if (mismatchIndex != -1) {
            // token doesn't start with the expected retry token prefix. Not a valid retry token
            return null;
        }
        // now find the length of the original connection id
        final int originalServerConnIdLen = token.get();
        final byte[] originalServerConnId = new byte[originalServerConnIdLen];
        // read the original dest conn id
        token.get(originalServerConnId);

        // now find the length of the server generated dest connection id
        final int serverChosenDestConnIdLen = token.get();
        final byte[] serverChosenDestConnId = new byte[serverChosenDestConnIdLen];
        // read the server chosen dest conn id
        token.get(serverChosenDestConnId);

        // TODO: the use of PeerConnectionId is only for convenience
        return new RetryData(new PeerConnectionId(originalServerConnId), new PeerConnectionId(serverChosenDestConnId));
    }

    DatagramDeliveryPolicy incomingDeliveryPolicy() {
        return this.incomingDeliveryPolicy;
    }

    DatagramDeliveryPolicy outgoingDeliveryPolicy() {
        return this.outgoingDeliveryPolicy;
    }

    byte[] buildNewToken() {
        // TODO this token is too simple to provide authenticity guarantee; use for testing only
        final byte[] token = new byte[newTokenPrefixBytes.length];
        // copy the new_token prefix
        System.arraycopy(newTokenPrefixBytes, 0, token, 0, newTokenPrefixBytes.length);
        return token;
    }

    private boolean isNewToken(final ByteBuffer token) {
        Objects.requireNonNull(token);
        if (!token.hasRemaining()) {
            return false;
        }
        if (token.remaining() != newTokenPrefixBytes.length) {
            return false;
        }
        final byte[] tokenBytes = new byte[newTokenPrefixBytes.length];
        token.get(tokenBytes);
        int mismatchIndex = Arrays.mismatch(newTokenPrefixBytes, 0, newTokenPrefixBytes.length,
                tokenBytes, 0, newTokenPrefixBytes.length);
        if (mismatchIndex != -1) {
            // token doesn't start with the expected new_token prefix. Not a valid token
            return false;
        }
        return true;
    }

    private void silentIgnorePacket(final SocketAddress source, final ByteBuffer payload,
                                    final QuicPacket.HeadersType headersType, final boolean outgoing) {
        if (debug.on()) debug.log("silently dropping %s packet %s %s", headersType,
                (outgoing ? "to dest" : "from source"), source);
    }

    @Override
    public void close() throws IOException {
        // TODO: ignore exceptions while closing?
        quickServerLock.lock();
        try {
            if (closed) return;
            closed = true;
        } finally {
            quickServerLock.unlock();
        }
        //http3Server.stop();
        var endpoint = this.endpoint;
        if (endpoint != null) endpoint.close();
        debug.log("endpoint closed");
        var selector = this.selector;
        if (selector != null) selector.close();
        debug.log("selector closed");
        if (ownExecutor && executor != null) {
            debug.log("shutting down executor");
            this.executor.shutdown();
            try {
                debug.log("awaiting termination");
                this.executor.awaitTermination(QuicSelector.IDLE_PERIOD_MS,
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                this.executor.shutdownNow();
            }
        }
    }

    /**
     * The transport parameters that will be sent to the peer by any new subsequent server connections
     * that are created by this server
     *
     * @param params transport parameters. Can be null, in which case the new server connection
     *               (whenever it is created) will use internal defaults
     */
    public void setTransportParameters(QuicTransportParameters params) {
        this.transportParameters = params;
    }

    /**
     * {@return the current configured transport parameters for new server connections. null if none
     * configured}
     */
    @Override
    public QuicTransportParameters getTransportParameters() {
        final QuicTransportParameters qtp = this.transportParameters;
        if (qtp == null) {
            return null;
        }
        // return a copy
        return new QuicTransportParameters(qtp);
    }

    /**
     * Called
     *
     * @param connection
     * @param originalConnectionId
     * @param localConnectionId
     */
    void connectionAcknowledged(QuicConnection connection,
                                QuicConnectionId originalConnectionId,
                                QuicConnectionId localConnectionId) {
        // endpoint.removeConnectionId(originalConnectionId);
    }

    private static class TestAlgorithmConstraints implements AlgorithmConstraints {
        private final Set<String> rejectedKAAlgos;

        public TestAlgorithmConstraints(Set<String> rejectedKAAlgos) {
            this.rejectedKAAlgos = rejectedKAAlgos;
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, AlgorithmParameters parameters) {
            if (primitives.contains(CryptoPrimitive.KEY_AGREEMENT) &&
                    rejectedKAAlgos.contains(algorithm)) {
                return false;
            }
            return true;
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
            return true;
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, Key key, AlgorithmParameters parameters) {
            return true;
        }
    }
}
