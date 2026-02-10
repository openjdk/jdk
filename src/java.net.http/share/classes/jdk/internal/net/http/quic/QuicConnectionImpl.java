/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NetworkChannel;
import java.nio.channels.UnresolvedAddressException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;

import jdk.internal.net.http.common.Deadline;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.TimeSource;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.OrderedFlow.CryptoDataFlow;
import jdk.internal.net.http.quic.QuicEndpoint.QuicDatagram;
import jdk.internal.net.http.quic.QuicTransportParameters.VersionInformation;
import jdk.internal.net.http.quic.frames.AckFrame;
import jdk.internal.net.http.quic.frames.ConnectionCloseFrame;
import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.DataBlockedFrame;
import jdk.internal.net.http.quic.frames.HandshakeDoneFrame;
import jdk.internal.net.http.quic.frames.MaxDataFrame;
import jdk.internal.net.http.quic.frames.MaxStreamDataFrame;
import jdk.internal.net.http.quic.frames.MaxStreamsFrame;
import jdk.internal.net.http.quic.frames.NewConnectionIDFrame;
import jdk.internal.net.http.quic.frames.NewTokenFrame;
import jdk.internal.net.http.quic.frames.PaddingFrame;
import jdk.internal.net.http.quic.frames.PathChallengeFrame;
import jdk.internal.net.http.quic.frames.PathResponseFrame;
import jdk.internal.net.http.quic.frames.PingFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.ResetStreamFrame;
import jdk.internal.net.http.quic.frames.RetireConnectionIDFrame;
import jdk.internal.net.http.quic.frames.StopSendingFrame;
import jdk.internal.net.http.quic.frames.StreamDataBlockedFrame;
import jdk.internal.net.http.quic.frames.StreamFrame;
import jdk.internal.net.http.quic.frames.StreamsBlockedFrame;
import jdk.internal.net.http.quic.packets.HandshakePacket;
import jdk.internal.net.http.quic.packets.InitialPacket;
import jdk.internal.net.http.quic.packets.LongHeader;
import jdk.internal.net.http.quic.packets.OneRttPacket;
import jdk.internal.net.http.quic.packets.PacketSpace;
import jdk.internal.net.http.quic.packets.QuicPacketDecoder;
import jdk.internal.net.http.quic.packets.QuicPacketEncoder;
import jdk.internal.net.http.quic.packets.QuicPacketEncoder.OutgoingQuicPacket;
import jdk.internal.net.http.quic.packets.RetryPacket;
import jdk.internal.net.http.quic.packets.VersionNegotiationPacket;
import jdk.internal.net.http.quic.streams.CryptoWriterQueue;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicBidiStreamImpl;
import jdk.internal.net.http.quic.streams.QuicConnectionStreams;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.http.quic.streams.QuicStream.StreamState;
import jdk.internal.net.http.quic.streams.QuicStreams;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicOneRttContext;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTLSEngine.HandshakeState;
import jdk.internal.net.quic.QuicTLSEngine.KeySpace;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.http.quic.QuicTransportParameters.ParameterId;
import jdk.internal.net.quic.QuicVersion;

import static jdk.internal.net.http.quic.QuicClient.INITIAL_SERVER_CONNECTION_ID_LENGTH;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.active_connection_id_limit;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_data;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_local;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_remote;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_uni;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_streams_bidi;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_streams_uni;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_source_connection_id;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.max_idle_timeout;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.max_udp_payload_size;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.version_information;
import static jdk.internal.net.http.quic.TerminationCause.forException;
import static jdk.internal.net.http.quic.TerminationCause.forTransportError;
import static jdk.internal.net.http.quic.QuicConnectionId.MAX_CONNECTION_ID_LENGTH;
import static jdk.internal.net.http.quic.QuicRttEstimator.MAX_PTO_BACKOFF_TIMEOUT;
import static jdk.internal.net.http.quic.QuicRttEstimator.MIN_PTO_BACKOFF_TIMEOUT;
import static jdk.internal.net.http.quic.frames.QuicFrame.MAX_VL_INTEGER;
import static jdk.internal.net.http.quic.packets.QuicPacketNumbers.computePacketNumberLength;
import static jdk.internal.net.http.quic.streams.QuicStreams.isUnidirectional;
import static jdk.internal.net.http.quic.streams.QuicStreams.streamType;
import static jdk.internal.net.quic.QuicTransportErrors.PROTOCOL_VIOLATION;

/**
 * This class implements a QUIC connection.
 * A QUIC connection is established between a client and a server over a
 * QuicEndpoint endpoint.
 * A QUIC connection can then multiplex multiple QUIC streams to the same server.
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/info/rfc9001
 *      RFC 9001: Using TLS to Secure QUIC
 * @spec https://www.rfc-editor.org/info/rfc9002
 *      RFC 9002: QUIC Loss Detection and Congestion Control
 */
public class QuicConnectionImpl extends QuicConnection implements QuicPacketReceiver {

    private static final int MAX_IPV6_MTU = 65527;
    private static final int MAX_IPV4_MTU = 65507;

    // Quic assumes a minimum packet size of 1200
    // See https://www.rfc-editor.org/rfc/rfc9000#name-datagram-size
    public static final int SMALLEST_MAXIMUM_DATAGRAM_SIZE =
            QuicClient.SMALLEST_MAXIMUM_DATAGRAM_SIZE;

    // The default value for the Quic maxInitialTimeout, in seconds. Will be clamped to [1, Integer.MAX_vALUE]
    public static final int DEFAULT_MAX_INITIAL_TIMEOUT = Math.clamp(
            Utils.getIntegerProperty("jdk.httpclient.quic.maxInitialTimeout", 30),
            1, Integer.MAX_VALUE);
    // The default value for the initial_max_data transport parameter that a QuicConnectionImpl
    // will send to its peer, if no value is provided by the higher level protocol.
    public static final long DEFAULT_INITIAL_MAX_DATA = Math.clamp(
            Utils.getLongProperty("jdk.httpclient.quic.maxInitialData", 15 << 20),
            0, 1L << 60);
    // The default value for the initial_max_stream_data_bidi_local, initial_max_stream_data_bidi_remote,
    // and initial_max_stream_data_uni transport parameters that a QuicConnectionImpl
    // will send to its peer, if no value is provided by the higher level protocol.
    public static final long DEFAULT_INITIAL_STREAM_MAX_DATA = Math.clamp(
            Utils.getIntegerProperty("jdk.httpclient.quic.maxStreamInitialData", 6 << 20),
            0, 1L << 60);
    // The default value for the initial_max_streams_bidi transport parameter that a QuicConnectionImpl
    // will send to its peer, if no value is provided by the higher level protocol.
    // The Http3ClientImpl typically provides a value of 0, so this property has no effect
    // on QuicConnectionImpl instances created on behalf of the HTTP/3 client
    public static final int DEFAULT_MAX_BIDI_STREAMS =
            Utils.getIntegerProperty("jdk.internal.httpclient.quic.maxBidiStreams", 100);
    // The default value for the initial_max_streams_uni transport parameter that a QuicConnectionImpl
    // will send to its peer, if no value is provided by the higher level protocol.
    public static final int DEFAULT_MAX_UNI_STREAMS =
            Utils.getIntegerProperty("jdk.httpclient.quic.maxUniStreams", 100);
    public static final boolean USE_DIRECT_BUFFER_POOL = Utils.getBooleanProperty(
            "jdk.internal.httpclient.quic.poolDirectByteBuffers", !QuicEndpoint.DGRAM_SEND_ASYNC);

    public static final int RESET_TOKEN_LENGTH = 16; // RFC states 16 bytes for stateless token
    public static final long MAX_STREAMS_VALUE_LIMIT = 1L << 60; // cannot exceed 2^60 as per RFC

    // VarHandle provide the same atomic compareAndSet functionality
    // than AtomicXXXXX classes, but without the additional cost in
    // footprint.
    private static final VarHandle VERSION_NEGOTIATED;
    private static final VarHandle STATE;
    private static final VarHandle MAX_SND_DATA;
    private static final VarHandle MAX_RCV_DATA;
    public static final int DEFAULT_DATAGRAM_SIZE;
    private static final int MAX_INCOMING_CRYPTO_CAPACITY = 64 << 10;

    static {
        try {
            Lookup lookup = MethodHandles.lookup();
            VERSION_NEGOTIATED = lookup
                    .findVarHandle(QuicConnectionImpl.class, "versionNegotiated", boolean.class);
            STATE = lookup.findVarHandle(QuicConnectionImpl.class, "state", int.class);
            MAX_SND_DATA = lookup.findVarHandle(OneRttFlowControlledSendingQueue.class, "maxData", long.class);
            MAX_RCV_DATA = lookup.findVarHandle(OneRttFlowControlledReceivingQueue.class, "maxData", long.class);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
        int size = Utils.getIntegerProperty("jdk.httpclient.quic.defaultMTU",
                SMALLEST_MAXIMUM_DATAGRAM_SIZE);
        // don't allow the value to be below 1200 and above 65527, to conform with RFC-9000,
        // section 18.2:
        // The default for this parameter is the maximum permitted UDP payload of 65527.
        // Values below 1200 are invalid.
        if (size < SMALLEST_MAXIMUM_DATAGRAM_SIZE || size > MAX_IPV6_MTU) {
            // fallback to SMALLEST_MAXIMUM_DATAGRAM_SIZE
            size = SMALLEST_MAXIMUM_DATAGRAM_SIZE;
        }
        DEFAULT_DATAGRAM_SIZE = size;
    }

    protected final Logger debug = Utils.getDebugLogger(this::dbgTag);

    final QuicRttEstimator rttEstimator = new QuicRttEstimator();
    final QuicCongestionController congestionController;
    /**
     * The state of the quic connection.
     * The handshake is confirmed when HANDSHAKE_DONE has been received,
     * or when the first 1-RTT packet has been successfully decrypted.
     * See RFC 9001 section 4.1.2
     * https://www.rfc-editor.org/rfc/rfc9001#name-handshake-confirmed
     */
    private final StateHandle stateHandle = new StateHandle();
    private final AtomicBoolean startHandshakeCalled = new AtomicBoolean();
    private final InetSocketAddress peerAddress;
    private final QuicInstance quicInstance;
    private final String dbgTag;
    private final QuicTLSEngine quicTLSEngine;
    private final CodingContext codingContext;
    private final PacketSpaces packetSpaces;
    private final OneRttFlowControlledSendingQueue oneRttSndQueue =
            new OneRttFlowControlledSendingQueue();
    private final OneRttFlowControlledReceivingQueue oneRttRcvQueue =
            new OneRttFlowControlledReceivingQueue(this::logTag);
    protected final QuicConnectionStreams streams;
    protected final Queue<QuicFrame> outgoing1RTTFrames = new ConcurrentLinkedQueue<>();
    // for one-rtt crypto data (session tickets)
    private final CryptoDataFlow peerCryptoFlow = new CryptoDataFlow();
    private final CryptoWriterQueue localCryptoFlow = new CryptoWriterQueue();
    private final HandshakeFlow handshakeFlow = new HandshakeFlow();
    final ConnectionTerminatorImpl terminator;
    protected final IdleTimeoutManager idleTimeoutManager;
    protected final QuicTransportParameters transportParams;
    // the initial (local) connection ID
    private final QuicConnectionId connectionId;
    private final PeerConnIdManager peerConnIdManager;
    private final LocalConnIdManager localConnIdManager;
    private volatile QuicConnectionId incomingInitialPacketSourceId;
    protected final QuicEndpoint endpoint;
    private volatile QuicTransportParameters localTransportParameters;
    private volatile QuicTransportParameters peerTransportParameters;
    private volatile byte[] initialToken;
    // the number of (active) connection ids the peer is willing to accept for a given connection
    private volatile long peerActiveConnIdsLimit = 2; // default is 2 as per RFC

    private volatile int state;
    // the quic version currently in use
    private volatile QuicVersion quicVersion;
    // the quic version from the first packet
    private final QuicVersion originalVersion;
    private volatile QuicPacketDecoder decoder;
    private volatile QuicPacketEncoder encoder;
    // (client-only) if true, we no longer accept VERSIONS packets
    private volatile boolean versionCompatible;
    // if true, we no longer accept version changes
    private volatile boolean versionNegotiated;
    // true if we changed version in response to VERSIONS packet
    private volatile boolean processedVersionsPacket;
    // start off with 1200 or whatever is configured through
    // jdk.net.httpclient.quic.defaultPDU system property
    private int maxPeerAdvertisedPayloadSize = DEFAULT_DATAGRAM_SIZE;
    // max MTU size on the connection: either MAX_IPV4_MTU or MAX_IPV6_MTU,
    // depending on whether the peer address is IPv6 or IPv4
    private final int maxConnectionMTU;
    // we start with a pathMTU that is 1200 or whatever is configured through
    // jdk.net.httpclient.quic.defaultPDU system property
    private int pathMTU = DEFAULT_DATAGRAM_SIZE;
    private final SequentialScheduler handshakeScheduler =
            SequentialScheduler.lockingScheduler(this::continueHandshake0);
    private final ReentrantLock handshakeLock = new ReentrantLock();
    private final String cachedToString;
    private final String logTag;
    private final long labelId;
    // incoming PATH_CHALLENGE frames waiting for PATH_RESPONSE
    private final Queue<PathChallengeFrame> pathChallengeFrameQueue = new ConcurrentLinkedQueue<>();

    private volatile MaxInitialTimer maxInitialTimer;

    static String dbgTag(QuicInstance quicInstance, String logTag) {
        return String.format("QuicConnection(%s, %s)",
                quicInstance.instanceId(), logTag);
    }

    protected QuicConnectionImpl(final QuicVersion firstFlightVersion,
                                 final QuicInstance quicInstance,
                                 final InetSocketAddress peerAddress,
                                 final String peerName,
                                 final int peerPort,
                                 final SSLParameters sslParameters,
                                 final String logTagFormat,
                                 final long labelId) {
        this.labelId = labelId;
        this.quicInstance = Objects.requireNonNull(quicInstance, "quicInstance");
        try {
            this.endpoint = quicInstance.getEndpoint();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.peerAddress = peerAddress;
        this.maxConnectionMTU = peerAddress.getAddress() instanceof Inet6Address
                ? MAX_IPV6_MTU
                : MAX_IPV4_MTU;
        this.pathMTU = Math.clamp(DEFAULT_DATAGRAM_SIZE, SMALLEST_MAXIMUM_DATAGRAM_SIZE, maxConnectionMTU);
        this.cachedToString = String.format(logTagFormat.formatted("quic:%s:%s:%s"), labelId,
                Arrays.toString(sslParameters.getApplicationProtocols()), peerAddress);
        this.connectionId = this.endpoint.idFactory().newConnectionId();
        this.logTag = logTagFormat.formatted(labelId);
        this.dbgTag = dbgTag(quicInstance, logTag);
        this.congestionController = createCongestionController(dbgTag, rttEstimator);
        this.originalVersion = this.quicVersion = firstFlightVersion == null
                ? QuicVersion.firstFlightVersion(quicInstance.getAvailableVersions())
                : firstFlightVersion;
        final boolean isClientConn = isClientConnection();
        this.peerConnIdManager = new PeerConnIdManager(this, dbgTag);
        this.localConnIdManager = new LocalConnIdManager(this, dbgTag, connectionId);
        this.decoder = QuicPacketDecoder.of(this.quicVersion);
        this.encoder = QuicPacketEncoder.of(this.quicVersion);
        this.codingContext = new QuicCodingContext();
        final QuicTLSEngine engine = this.quicInstance.getQuicTLSContext()
                .createEngine(peerName, peerPort);
        engine.setUseClientMode(isClientConn);
        engine.setSSLParameters(sslParameters);
        this.quicTLSEngine = engine;
        quicTLSEngine.setRemoteQuicTransportParametersConsumer(this::consumeQuicParameters);
        packetSpaces = PacketSpaces.forConnection(this);
        quicTLSEngine.setOneRttContext(packetSpaces.getOneRttContext());
        streams = new QuicConnectionStreams(this, debug);
        if (quicInstance instanceof QuicClient quicClient) {
            // use the (INITIAL) token that a server might have sent to this client (through
            // NEW_TOKEN frame) on a previous connection against that server
            this.initialToken = quicClient.initialTokenFor(this.peerAddress);
        }
        terminator = new ConnectionTerminatorImpl(this);
        idleTimeoutManager = new IdleTimeoutManager(this);
        transportParams = quicInstance.getTransportParameters() == null
                ? new QuicTransportParameters()
                : quicInstance.getTransportParameters();
        if (debug.on()) debug.log("Quic Connection Created");
    }

    private static QuicCongestionController createCongestionController
            (String dbgTag, QuicRttEstimator rttEstimator) {
        String algo = System.getProperty("jdk.internal.httpclient.quic.congestionController", "cubic");
        if (algo.equalsIgnoreCase("reno")) {
            return new QuicRenoCongestionController(dbgTag, rttEstimator);
        } else {
            return new QuicCubicCongestionController(dbgTag, rttEstimator);
        }
    }

    @Override
    public final long uniqueId() {
        return labelId;
    }

    /**
     * An abstraction to represent the connection state as a bit mask.
     * This is not an enum as some stages can overlap.
     */
    public abstract static class QuicConnectionState {
        public static final int
                NEW = 0,           // the connection is new
                HISENT = 1,        // first initial hello packet sent
                HSCOMPLETE = 16,   // handshake completed
                CLOSING = 128,     // connection has entered "Closing" state as defined in RFC-9000
                DRAINING = 256,    // connection has entered "Draining" state as defined in RFC-9000
                CLOSED = 512;      // CONNECTION_CLOSE ACK sent or received
        public abstract int state();
        public boolean helloSent() {return isMarked(HISENT);}
        public boolean handshakeComplete() { return isMarked(HSCOMPLETE);}
        public boolean closing() { return isMarked(CLOSING);}
        public boolean draining() { return isMarked(DRAINING);}
        public boolean opened() { return (state() & (CLOSED | DRAINING | CLOSING)) == 0; }
        public boolean isMarked(int mask) { return isMarked(state(), mask); }
        public String toString() { return toString(state()); }
        public static boolean isMarked(int state, int mask) {
            return mask == 0 ? state == 0 : (state & mask) == mask;
        }
        public static String toString(int state) {
            if (state == NEW) return "new";
            if (isMarked(state, CLOSED)) return "closed";
            if (isMarked(state, DRAINING)) return "draining";
            if (isMarked(state, CLOSING)) return "closing";
            if (isMarked(state, HSCOMPLETE)) return "handshakeComplete";
            if (isMarked(state, HISENT)) return "helloSent";
            return "Unknown(" + state + ")";
        }
    }

    /**
     * A {link QuicTimedEvent} used to interrupt the handshake
     * if no response to the first initial packet is received within
     * a reasonable delay (default is ~ 30s).
     * This avoids waiting more than 30s for ConnectionException
     * to be raised if no server is available at the peer address.
     * This class is only used on the client side.
     */
    final class MaxInitialTimer implements QuicTimedEvent {
        private final Deadline maxInitialDeadline;
        private final QuicTimerQueue timerQueue;
        private final long eventId;
        private volatile Deadline deadline;
        private volatile boolean initialPacketReceived;
        private volatile boolean connectionClosed;

        // optimization: if done is true it avoids volatile read
        // of initialPacketReceived and/or connectionClosed
        // from initialPacketReceived()
        private boolean done;
        private MaxInitialTimer(QuicTimerQueue timerQueue, Deadline maxDeadline) {
            this.eventId = QuicTimerQueue.newEventId();
            this.timerQueue = timerQueue;
            maxInitialDeadline = deadline = maxDeadline;
            assert isClientConnection() : "MaxInitialTimer should only be used on QuicClients";
        }

        /**
         * Called when an initial packet is received from the
         * peer. At this point the MaxInitialTimer is disarmed,
         * and further calls to this method are no-op.
         */
        void initialPacketReceived() {
            if (done) return; // races are OK - avoids volatile read
            boolean firsPacketReceived = initialPacketReceived;
            boolean closed = connectionClosed;
            if (done = (firsPacketReceived || closed)) return;
            initialPacketReceived = true;
            if (debug.on()) {
                debug.log("Quic initial timer disarmed after %s seconds",
                        DEFAULT_MAX_INITIAL_TIMEOUT -
                                Deadline.between(now(), maxInitialDeadline).toSeconds());
            }
            if (!closed) {
                // rescheduling with Deadline.MAX will take the
                // MaxInitialTimer out of the timer queue.
                timerQueue.reschedule(this, Deadline.MAX);
            }
        }

        @Override
        public Deadline deadline() {
            return deadline;
        }

        /**
         * This method is called if the timer expires.
         * If no initial packet has been received (
         * {@link #initialPacketReceived()} was never called),
         * the connection's handshakeCF is completed with a
         * {@link ConnectException}.
         * Calling this method a second time is a no-op.
         * @return {@link Deadline#MAX}, always.
         */
        @Override
        public Deadline handle() {
            if (done) return Deadline.MAX;
            boolean firsPacketReceived = initialPacketReceived;
            boolean closed = connectionClosed;
            if (!firsPacketReceived && !closed) {
                assert !now().isBefore(maxInitialDeadline);
                var connectException = new ConnectException("No response from peer for %s seconds"
                        .formatted(DEFAULT_MAX_INITIAL_TIMEOUT));
                if (QuicConnectionImpl.this.handshakeFlow.handshakeCF()
                        .completeExceptionally(connectException)) {
                    // abandon the connection, but sends ConnectionCloseFrame
                    TerminationCause cause = TerminationCause.forException(
                            new QuicTransportException(connectException.getMessage(),
                                    KeySpace.INITIAL, 0, QuicTransportErrors.APPLICATION_ERROR));
                    terminator.terminate(cause);
                }
                connectionClosed = done = closed = true;
            }
            assert firsPacketReceived || closed;
            return Deadline.MAX;
        }

        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public Deadline refreshDeadline() {
            boolean firstPacketReceived = initialPacketReceived;
            boolean closed = connectionClosed;
            Deadline newDeadlne = deadline;
            if (closed || firstPacketReceived) newDeadlne = deadline = Deadline.MAX;
            return newDeadlne;
        }

        private Deadline now() {
            return QuicConnectionImpl.this.endpoint().timeSource().instant();
        }
    }

    /**
     * A state handle is a mutable implementation of {@link QuicConnectionState}
     * that allows to view the volatile connection int variable {@code state} as
     * a {@code QuicConnectionState}, and provides methods to mutate it in
     * a thread safe atomic way.
     */
    protected final class StateHandle extends QuicConnectionState {
        public int state() { return state;}

        /**
         * Updates the state to a new state value with the passed bit {@code mask} set.
         *
         * @param mask The state mask
         * @return true if previously the state value didn't have the {@code mask} set and this
         * method successfully updated the state value to set the {@code mask}
         */
        final boolean mark(final int mask) {
            int state, desired;
            do {
                state = desired = state();
                if ((state & mask) == mask) return false; // already set
                desired = state | mask;
            } while (!STATE.compareAndSet(QuicConnectionImpl.this, state, desired));
            return true; // compareAndSet switched the old state to the desired state
        }
        public boolean markHelloSent() { return mark(HISENT); }
        public boolean markHandshakeComplete() { return mark(HSCOMPLETE); }
    }

    /**
     * Keeps track of:
     * - handshakeCF   the handshake completable future
     * - localInitial  the local initial crypto writer queue
     * - peerInitial   the peer initial crypto flow
     * - localHandshake the local handshake crypto queue
     * - peerHandshake the peer handshake crypto flow
     */
    protected final class HandshakeFlow {

        private final CompletableFuture<HandshakeState> handshakeCF;
        // a CompletableFuture which will get completed when the handshake initiated locally,
        // has "reached" the peer i.e. when the peer acknowledges or replies to the first
        // INITIAL packet sent by an endpoint
        final CompletableFuture<Void> handshakeReachedPeerCF;
        private final CryptoWriterQueue localInitial = new CryptoWriterQueue();
        private final CryptoDataFlow peerInitial = new CryptoDataFlow();
        private final CryptoWriterQueue localHandshake = new CryptoWriterQueue();
        private final CryptoDataFlow peerHandshake = new CryptoDataFlow();
        private final AtomicBoolean handshakeStarted = new AtomicBoolean();

        private HandshakeFlow() {
            this.handshakeCF = new MinimalFuture<>();
            this.handshakeReachedPeerCF = new MinimalFuture<>();
            // ensure that the handshakeReachedPeerCF gets completed exceptionally
            // if an exception is raised before the first INITIAL packet is
            // acked by the peer.
            handshakeCF.whenComplete((r, t) -> {
                if (Log.quicHandshake()) {
                    Log.logQuic("{0} handshake completed {1}",
                            logTag(),
                            t == null ? "successfully" : ("exceptionally: " + t));
                }
                if (t != null) {
                    handshakeReachedPeerCF.completeExceptionally(t);
                }
            });
        }

        /**
         * {@return the CompletableFuture representing a handshake}
         */
        public CompletableFuture<HandshakeState> handshakeCF() {
            return this.handshakeCF;
        }

        public void failHandshakeCFs(final Throwable cause) {
            assert cause != null : "missing cause when failing handshake CFs";
            SSLHandshakeException sslHandshakeException = null;
            if (!handshakeCF.isDone()) {
                sslHandshakeException = sslHandshakeException(cause);
                if (Log.errors()) {
                    Log.logError("%s QUIC handshake failed: %s"
                            .formatted(logTag(), cause));
                    Log.logError(cause);
                }
                handshakeCF.completeExceptionally(sslHandshakeException);
            }
            if (!handshakeReachedPeerCF.isDone()) {
                if (sslHandshakeException == null) {
                    sslHandshakeException = sslHandshakeException(cause);
                }
                handshakeReachedPeerCF.completeExceptionally(sslHandshakeException);
            }
        }

        private SSLHandshakeException sslHandshakeException(final Throwable cause) {
            if (cause instanceof SSLHandshakeException ssl) {
                return ssl;
            }
            return new SSLHandshakeException("QUIC connection establishment failed", cause);
        }

        /**
         * Marks the start of a handshake.
         * @throws IllegalStateException If handshake has already started
         */
        private void markHandshakeStart() {
            if (!handshakeStarted.compareAndSet(false, true)) {
                throw new IllegalStateException("Handshake has already started on "
                        + QuicConnectionImpl.this);
            }
        }
    }

    public record PacketSpaces(PacketSpace initial, PacketSpace handshake, PacketSpace app) {
        public PacketSpace get(PacketNumberSpace pnspace) {
            return switch (pnspace) {
                case INITIAL -> initial();
                case HANDSHAKE -> handshake();
                case APPLICATION -> app();
                default -> throw new IllegalArgumentException(String.valueOf(pnspace));
            };
        }

        private QuicOneRttContext getOneRttContext() {
            final var appPacketSpaceMgr = app();
            assert appPacketSpaceMgr instanceof QuicOneRttContext
                    : "unexpected 1-RTT packet space manager";
            return (QuicOneRttContext) appPacketSpaceMgr;
        }

        public static PacketSpaces forConnection(final QuicConnectionImpl connection) {
            final var initialPktSpaceMgr = new PacketSpaceManager(connection, PacketNumberSpace.INITIAL);
            return new PacketSpaces(initialPktSpaceMgr,
                    new PacketSpaceManager.HandshakePacketSpaceManager(connection, initialPktSpaceMgr),
                    new PacketSpaceManager.OneRttPacketSpaceManager(connection));
        }

        public void close() {
            initial.close();
            handshake.close();
            app.close();
        }
    }

    private final ConcurrentLinkedQueue<IncomingDatagram> incoming = new ConcurrentLinkedQueue<>();
    private final SequentialScheduler incomingLoopScheduler =
            SequentialScheduler.lockingScheduler(this::incoming);


    /*
     * delegate handling of the datagrams to the executor to free up
     * the endpoint readLoop. Helps with processing ACKs in a more
     * timely fashion, which avoids too many retransmission.
     * The endpoint readLoop runs on a single thread, while this loop
     * will have one thread per connection which helps with a better
     * utilization of the system resources.
     */
    private void scheduleForDecryption(IncomingDatagram datagram) {
        // Processes an incoming encrypted packet that has just been
        // read off the network.
        var received = datagram.buffer.remaining();
        if (incomingLoopScheduler.isStopped()) {
            if (debug.on()) {
                debug.log("scheduleForDecryption closed: dropping datagram (%d bytes)",
                        received);
            }
            return;
        }
        if (debug.on()) {
            debug.log("scheduleForDecryption: %d bytes", received);
        }
        endpoint.buffer(received);
        incoming.add(datagram);

        incomingLoopScheduler.runOrSchedule(quicInstance().executor());
    }

    private void incoming() {
        try {
            IncomingDatagram datagram;
            while ((datagram = incoming.poll()) != null) {
                ByteBuffer buffer = datagram.buffer;
                int remaining = buffer.remaining();
                try {
                    if (incomingLoopScheduler.isStopped()) {
                        // we still need to unbuffer, continue here will
                        // ensure we skip directly to the finally-block
                        // below.
                        continue;
                    }

                    internalProcessIncoming(datagram.source(),
                            datagram.destConnId(),
                            datagram.headersType(),
                            datagram.buffer());
                } catch (Throwable t) {
                    if (Log.errors() || debug.on()) {
                        String msg = "Failed to process datagram: " + t;
                        Log.logError(logTag() + " " + msg);
                        debug.log(msg, t);
                    }
                } finally {
                    endpoint.unbuffer(remaining);
                }
            }
        } catch (Throwable t) {
            terminator.terminate(TerminationCause.forException(t));
        }
    }

    /**
     * Schedule an incoming quic packet for decryption.
     * The ByteBuffer should contain a single packet, and its
     * limit should be set at the end of the packet.
     *
     * @param buffer     a byte buffer containing the incoming packet
     */
    private void decrypt(ByteBuffer buffer) {
        // Processes an incoming encrypted packet that has just been
        // read off the network.
        PacketType packetType = decoder.peekPacketType(buffer);
        var received = buffer.remaining();
        var pos = buffer.position();
        if (debug.on()) {
            debug.log("decrypt %s(pos=%d, remaining=%d)",
                    packetType, pos, received);
        }
        try {
            assert packetType != PacketType.VERSIONS;
            var packet = codingContext.parsePacket(buffer);
            if (packet != null) {
                processDecrypted(packet);
            } else {
                if (packetType == PacketType.HANDSHAKE) {
                    packetSpaces.initial.fastRetransmit();
                }
            }
        } catch (QuicTransportException qte) {
            // close the connection on this fatal error
            if (Log.errors() || debug.on()) {
                final String msg = "closing connection due to error while decoding" +
                        " packet (type=" + packetType + "): " + qte;
                Log.logError(logTag() + " " + msg);
                debug.log(msg, qte);
            }
            terminator.terminate(TerminationCause.forException(qte));
        } catch (Throwable t) {
            if (Log.errors() || debug.on()) {
                String msg = "Failed to decode packet (type=" + packetType + "): " + t;
                Log.logError(logTag() + " " + msg);
                debug.log(msg, t);
            }
        }
    }

    public void closeIncoming() {
        incomingLoopScheduler.stop();
        IncomingDatagram icd;
        // we still need to unbuffer all datagrams in the queue
        while ((icd = incoming.poll()) != null ) {
            endpoint.unbuffer(icd.buffer().remaining());
        }
    }

    /**
     * A protection record contains a packet to encrypt, and a datagram that may already
     * contain encrypted packets. The firstPacketOffset indicates the position of the
     * first encrypted packet in the datagram. The packetOffset indicates the position
     * at which this packet will be - or has been - written in the datagram.
     * Before the packet is encrypted and written to the datagram, the packetOffset
     * should be the same as the datagram buffer position.
     * After the packet has been written, the packetOffset should indicate
     * at which position the packet has been written. The datagram position
     * indicates where to write the next packet.
     * <p>
     * Additionally, a {@code ProtectionRecord} may carry some flags indicating the
     * intended usage of the datagram. The following flags are supported:
     * <ul>
     *     <li>{@link #SINGLE_PACKET}: the default - it is not expected that the
     *         datagram will contain more packets</li>
     *     <li>{@link #COALESCED}: should be used if it is expected that the
     *         datagram will contain more than one packet</li>
     *     <li>{@link #LAST_PACKET}: should be used in conjunction with {@link #COALESCED}
     *         to indicate that the packet being protected is the last that will be
     *         added to the datagram</li>
     * </ul>
     *
     * @apiNote
     * Flag values can be combined, but some combinations
     * may not make sense. A single packet can also be identified as any
     * packet that doesn't have the {@code COALESCED} bit on.
     * The flag is used to convey information that may be used to figure
     * out whether to send the datagram right away, or whether to wait for
     * more packet to be coalesced inside it.
     *
     * @param packet the packet to encrypt
     * @param datagram the datagram in which the encrypted packet should be written
     * @param firstPacketOffset the position of the first encrypted packet in the datagram
     * @param packetOffset the offset at which the packet should be / has been written in the datagram
     * @param flags a bit mask containing some details about the datagram being sent out
     */
    public record ProtectionRecord(QuicPacket packet, ByteBuffer datagram,
                                   int firstPacketOffset, int packetOffset,
                                   long retransmittedPacketNumber, int flags) {
        /**
         * This is the default.
         * This protection record is adding a single packet to be sent into
         * the datagram and the datagram can be sent as soon as the packet
         * has been encrypted.
         */
        public static final int SINGLE_PACKET = 0;
        /**
         * This can be used when it is expected that more than one packet
         * will be added to this datagram. We should wait until the last packet
         * has been added before sending the datagram out.
         */
        public static final int COALESCED = 1;
        /**
         * This protection record is adding the last packet to be sent into
         * the datagram and the datagram can be sent as soon as the packet
         * has been encrypted.
         */
        public static final int LAST_PACKET = 2;

        // indicate that the packet is not retransmitted
        private static final long NOT_RETRANSMITTED = -1L;

        ProtectionRecord withOffset(int packetOffset) {
            if (this.packetOffset == packetOffset) {
                return this;
            }
            return new ProtectionRecord(packet, datagram, firstPacketOffset,
                    packetOffset, retransmittedPacketNumber, flags);
        }

        public ProtectionRecord encrypt(final CodingContext codingContext)
                throws QuicKeyUnavailableException, QuicTransportException {
            final PacketType packetType = packet.packetType();
            assert packetType != PacketType.VERSIONS;
            // keep track of position before encryption
            final int preEncryptPos = datagram.position();
            codingContext.writePacket(packet, datagram);
            final ProtectionRecord encrypted = withOffset(preEncryptPos);
            return encrypted;
        }

        /**
         * Records the intent of protecting a packet that will be sent as soon
         * as it has been encrypted, without waiting for more packets to be
         * coalesced into the datagram.
         *
         * @param packet     the packet to protect
         * @param allocator  an allocator to allocate the datagram
         * @return a protection record to submit for packet protection
         */
        public static ProtectionRecord single(QuicPacket packet,
                                          Function<QuicPacket, ByteBuffer> allocator) {
            ByteBuffer datagram = allocator.apply(packet);
            int offset = datagram.position();
            return new ProtectionRecord(packet, datagram,
                    offset, offset, NOT_RETRANSMITTED, 0);
        }

        /**
         * Records the intent of protecting a packet that retransmits
         * a previously transmitted packet. The packet will be sent as soon
         * as it has been encrypted, without waiting for more packets to be
         * coalesced into the datagram.
         *
         * @param packet     the packet to protect
         * @param retransmittedPacketNumber the packet number of the original
         *                                  packet that was considered lost
         * @param allocator  an allocator to allocate the datagram
         * @return a protection record to submit for packet protection
         */
        public static ProtectionRecord retransmitting(QuicPacket packet,
                                          long retransmittedPacketNumber,
                                          Function<QuicPacket, ByteBuffer> allocator) {
            ByteBuffer datagram = allocator.apply(packet);
            int offset = datagram.position();
            return new ProtectionRecord(packet, datagram, offset, offset,
                    retransmittedPacketNumber, 0);
        }

        /**
         * Records the intent of protecting a packet that will be followed by
         * more packets to be coalesced in the same datagram. The datagram
         * should not be sent until the last packet has been coalesced.
         *
         * @param packet    the packet to protect
         * @param datagram  the datagram in which packet will be coalesced
         * @param firstPacketOffset the offset of the first packet in the datagram
         * @return a protection record to submit for packet protection
         */
        public static ProtectionRecord more(QuicPacket packet, ByteBuffer datagram, int firstPacketOffset) {
            return new ProtectionRecord(packet, datagram, firstPacketOffset,
                    datagram.position(), NOT_RETRANSMITTED, COALESCED);
        }

        /**
         * Records the intent of protecting the last packet that will be
         * coalesced in the given datagram. The datagram can be sent as soon
         * as the packet has been encrypted and coalesced into the given
         * datagram.
         *
         * @param packet    the packet to protect
         * @param datagram  the datagram in which packet will be coalesced
         * @param firstPacketOffset the offset of the first packet in the datagram
         * @return a protection record to submit for packet protection
         */
        public static ProtectionRecord last(QuicPacket packet, ByteBuffer datagram, int firstPacketOffset) {
            return new ProtectionRecord(packet, datagram, firstPacketOffset,
                    datagram.position(), NOT_RETRANSMITTED, LAST_PACKET | COALESCED);
        }
    }

    final QuicPacket newQuicPacket(final KeySpace keySpace, final List<QuicFrame> frames) {
        final PacketSpace packetSpace = packetSpaces.get(PacketNumberSpace.of(keySpace));
        return encoder.newOutgoingPacket(keySpace, packetSpace,
                localConnectionId(), peerConnectionId(), initialToken(),
                frames,
                codingContext);
    }

    /**
     * Encrypt an outgoing quic packet.
     * The ProtectionRecord indicates the position at which the encrypted packet
     * should be written in the datagram, as well as the position of the
     * first packet in the datagram. After encrypting the packet, this method calls
     * {@link #pushEncryptedDatagram(ProtectionRecord)}
     *
     * @param protectionRecord a record containing a quic packet to encrypt,
     *                         a destination byte buffer, and various offset information.
     */
    final void pushDatagram(final ProtectionRecord protectionRecord)
            throws QuicKeyUnavailableException, QuicTransportException {
        final QuicPacket packet = protectionRecord.packet();
        if (debug.on()) {
            debug.log("encrypting packet into datagram %s(pn:%s, %s)", packet.packetType(),
                    packet.packetNumber(), packet.frames());
        }
        // Processes an outgoing unencrypted packet that needs to be
        // encrypted before being packaged in a datagram.
        final ProtectionRecord encrypted;
        try {
            encrypted = protectionRecord.encrypt(codingContext);
        } catch (Throwable e) {
            // release the datagram ByteBuffer on failure to encrypt
            datagramDiscarded(new QuicDatagram(this, peerAddress, protectionRecord.datagram()));
            if (Log.errors()) {
                Log.logError("Failed to encrypt packet: " + e);
                // certain failures like key not being available are OK
                // in some situations. log the stacktrace only if this
                // was an unexpected failure.
                boolean skipStackTrace = false;
                if (e instanceof QuicKeyUnavailableException) {
                    final PacketSpace packetSpace = packetSpace(protectionRecord.packet().numberSpace());
                    skipStackTrace = packetSpace.isClosed();
                }
                if (!skipStackTrace) {
                    Log.logError(e);
                }
            }
            throw e;
        }
        // we currently don't support a ProtectionRecord with more than one QuicPacket
        assert (encrypted.flags & ProtectionRecord.COALESCED) == 0 : "coalesced packets not supported";
        // encryption of the datagram is complete, now push the encrypted
        // datagram through the endpoint
        if (Log.quicPacketOutLoggable(packet)) {
            Log.logQuicPacketOut(logTag(), packet);
        }
        pushEncryptedDatagram(encrypted);
    }

    protected void completeHandshakeCF() {
        // This can be called from the decrypt loop, and can trigger
        // sending of 1-RTT application data from within the same
        // thread: we use an executor here to avoid running the application
        // sending loop from within the Quic decrypt loop.
        completeHandshakeCF(quicInstance().executor());
    }

    protected final void completeHandshakeCF(Executor executor) {
        final var handshakeCF = handshakeFlow.handshakeCF();
        if (handshakeCF.isDone()) {
            return;
        }
        var handshakeState = quicTLSEngine.getHandshakeState();
        if (executor != null) {
            handshakeCF.completeAsync(() -> handshakeState, executor);
        } else {
            handshakeCF.complete(handshakeState);
        }
    }

    /**
     * A class used to check that 1-RTT received data doesn't exceed
     * the MAX_DATA of the connection
     */
    class OneRttFlowControlledReceivingQueue {
        private static final long MIN_BUFFER_SIZE = 16L << 10; // 16k
        private volatile long receivedData;
        private volatile long maxData;
        private volatile long processedData;
        // Desired buffer size; used when updating maxStreamData
        private final long desiredBufferSize = Math.clamp(DEFAULT_INITIAL_MAX_DATA, MIN_BUFFER_SIZE, MAX_VL_INTEGER);
        private final Supplier<String> logTag;

        OneRttFlowControlledReceivingQueue(Supplier<String> logTag) {
            this.logTag = Objects.requireNonNull(logTag);
        }

        /**
         * Called when new local parameters are available
         * @param localParameters the new local paramaters
         */
        void newLocalParameters(QuicTransportParameters localParameters) {
            if (localParameters.isPresent(ParameterId.initial_max_data)) {
                long maxData = this.maxData;
                long newMaxData = localParameters.getIntParameter(ParameterId.initial_max_data);
                while (maxData < newMaxData) {
                    if (MAX_RCV_DATA.compareAndSet(this, maxData, newMaxData)) break;
                    maxData = this.maxData;
                }
            }
        }

        /**
         * Checks whether the give frame would cause the connection max data
         * to be exceeded. If no, increase the amount of data processed by
         * this connection by the length of the frame. If yes, sends a
         * ConnectionCloseFrame with FLOW_CONTROL_ERROR.
         *
         * @param diff number of bytes newly received
         * @param frameType type of frame received
         * @throws QuicTransportException if processing this frame would cause the connection
         * max data to be exceeded
         */
        void checkAndIncreaseReceivedData(long diff, long frameType) throws QuicTransportException {
            assert diff > 0;
            long max, processed;
            boolean exceeded;
            synchronized (this) {
                max = maxData;
                processed = receivedData;
                if (max - processed < diff) {
                    exceeded = true;
                } else {
                    try {
                        receivedData = processed = Math.addExact(processed, diff);
                        exceeded = false;
                    } catch (ArithmeticException x ) {
                        // should not happen - flow control should have
                        // caught that
                        receivedData = processed = Long.MAX_VALUE;
                        exceeded = true;
                    }
                }
            }
            if (exceeded) {
                String reason = "Connection max data exceeded: max data processed=%s, max connection data=%s"
                        .formatted(processed, max);
                throw new QuicTransportException(reason,
                        QuicTLSEngine.KeySpace.ONE_RTT, frameType, QuicTransportErrors.FLOW_CONTROL_ERROR);
            }
        }

        public void increaseProcessedData(long diff) {
            long processed, received, max;
            synchronized (this) {
                processed = processedData += diff;
                received = receivedData;
                max = maxData;
            }
            if (Log.quicProcessed()) {
                Log.logQuic(logTag()+ " Processed: " + processed +
                        ", received: " + received +
                        ", max:" + max);
            }
            if (needSendMaxData()) {
                runAppPacketSpaceTransmitter();
            }
        }

        private long bumpMaxData() {
            long newMaxData = processedData + desiredBufferSize;
            long maxData = this.maxData;
            if (newMaxData - maxData < (desiredBufferSize / 5)) {
                return 0;
            }
            while (maxData < newMaxData) {
                if (MAX_RCV_DATA.compareAndSet(this, maxData, newMaxData))
                    return newMaxData;
                maxData = this.maxData;
            }
            return 0;
        }

        public boolean needSendMaxData() {
             return maxData - processedData < desiredBufferSize/2;
        }

        String logTag() { return logTag.get(); }
    }

    /**
     * An event loop triggered when stream data is available for sending.
     * We use a sequential scheduler here to make sure we don't send
     * more data than allowed by the connection's flow control.
     * This guarantee that only one thread composes flow controlled
     * OneRTT packets at a given time, which in turn guarantees that the
     * credit computed at the beginning of the loop will still be
     * available after the packet has been composed.
     */
    class OneRttFlowControlledSendingQueue {
        private volatile long dataProcessed;
        private volatile long maxData;

        /**
         * Called when a MAX_DATA frame is received.
         * This method is a no-op if the given value is less than the
         * current max stream data for the connection.
         *
         * @param maxData the maximum data offset that the peer is prepared
         *                to accept for the whole connection
         * @param isInitial true when processing transport parameters,
         *                  false when processing MaxDataFrame
         * @return the actual max data after taking the given value into account
         */
        public long setMaxData(long maxData, boolean isInitial) {
            long max;
            long processed;
            boolean wasblocked, unblocked = false;
            do {
                synchronized (this) {
                    max = this.maxData;
                    processed = dataProcessed;
                }
                wasblocked = max <= processed;
                if (max < maxData) {
                    if (MAX_SND_DATA.compareAndSet(this, max, maxData)) {
                        max = maxData;
                        unblocked = (wasblocked && max > processed);
                    }
                }
            } while (max < maxData);
            if (unblocked && !isInitial) {
                packetSpaces.app.runTransmitter();
            }
            return max;
        }

        /**
         * {@return the remaining credit for this connection}
         */
        public long credit() {
            synchronized (this) {
                return maxData - dataProcessed;
            }
        }

        // We can continue sending if we have credit and data is available to send
        private boolean canSend() {
            return credit() > 0 && streams.hasAvailableData()
                    || streams.hasControlFrames()
                    || hasQueuedFrames()
                    || oneRttRcvQueue.needSendMaxData();
        }

        // implementation of the sending loop.
        private boolean send1RTTData() {
            Throwable failure;
            try {
                return doSend1RTTData();
            } catch (Throwable t) {
                failure = t;
            }
            if (failure instanceof QuicKeyUnavailableException qkue) {
                if (!QuicConnectionImpl.this.stateHandle().opened()) {
                    // connection is already being closed and that explains the
                    // key unavailability (they might have been discarded). just log
                    // and return
                    if (debug.on()) {
                        debug.log("failed to send stream data, reason: " + qkue.getMessage());
                    }
                    return false;
                }
                // connection is still open but a key unavailability exception was raised.
                // close the connection and use an IOException instead of the internal
                // QuicKeyUnavailableException as the cause for the connection close.
                failure = new IOException(qkue.getMessage());
            }
            if (debug.on()) {
                debug.log("failed to send stream data", failure);
            }
            // close the connection to make sure it's not just ignored
            terminator.terminate(TerminationCause.forException(failure));
            return false;
        }

        private boolean doSend1RTTData() throws QuicKeyUnavailableException, QuicTransportException {
            // Loop over all sending streams to see if data is available - include
            // as much data as possible in the quic packet before sending it.
            // The QuicConnectionStreams make sure that streams are polled in a fair
            // manner (using round-robin?)
            // This loop is called through a sequential scheduler to make
            // sure we only have one thread emitting flow control data for
            // this connection
            final PacketSpace space = packetSpace(PacketNumberSpace.APPLICATION);
            final int maxDatagramSize = getMaxDatagramSize();
            final QuicConnectionId peerConnectionId = peerConnectionId();
            final int dstIdLength = peerConnectionId.length();
            if (!canSend()) {
                return false;
            }
            final long packetNumber = space.allocateNextPN();
            final long largestPeerAckedPN = space.getLargestPeerAckedPN();
            int remaining = QuicPacketEncoder.computeMaxOneRTTPayloadSize(
                    codingContext, packetNumber, dstIdLength, maxDatagramSize, largestPeerAckedPN);
            if (remaining == 0) {
                // not enough space to send available data
                return false;
            }
            final List<QuicFrame> frames = new ArrayList<>();
            remaining -= addConnectionControlFrames(remaining, frames);
            assert remaining >= 0 : remaining;
            long produced = streams.produceFramesToSend(encoder, remaining, credit(), frames);
            if (frames.isEmpty()) {
                // produced cannot be > 0 unless there are some frames to send
                assert produced == 0;
                return false;
            }
            // non-atomic operation should be OK since sendStreamData0 is called
            // only from the sending loop, and this is the only place where we
            // mutate dataProcessed.
            dataProcessed += produced;
            final OneRttPacket packet = encoder.newOneRttPacket(peerConnectionId,
                    packetNumber, largestPeerAckedPN, frames, codingContext);
            QuicConnectionImpl.this.send1RTTPacket(packet);
            return true;
        }

        /**
         * Produces connection-level control frames for sending in the next one-rtt
         * packet. The frames are added to the provided list.
         *
         * @param maxAllowedBytes maximum number of bytes the method is allowed to add
         * @param frames    list where the frames are added
         * @return number of bytes added
         */
        private int addConnectionControlFrames(final int maxAllowedBytes,
                                               final List<QuicFrame> frames) {
            assert maxAllowedBytes > 0 : "unexpected max allowed bytes: " + maxAllowedBytes;
            int added = 0;
            int remaining = maxAllowedBytes;
            QuicFrame f;
            while ((f = outgoing1RTTFrames.peek()) != null) {
                final int frameSize = f.size();
                if (frameSize <= remaining) {
                    outgoing1RTTFrames.remove();
                    frames.add(f);
                    added += frameSize;
                    remaining -= frameSize;
                } else {
                    break;
                }
            }
            PathChallengeFrame pcf;
            while (remaining >= 9 && (pcf = pathChallengeFrameQueue.poll()) != null) {
                f = new PathResponseFrame(pcf.data());
                final int frameSize = f.size();
                assert frameSize <= remaining : "Frame too large";
                frames.add(f);
                added += frameSize;
                remaining -= frameSize;
            }

            // NEW_CONNECTION_ID
            while ((f = localConnIdManager.nextFrame(remaining)) != null) {
                final int frameSize = f.size();
                assert frameSize <= remaining : "Frame too large";
                frames.add(f);
                added += frameSize;
                remaining -= frameSize;
            }
            // RETIRE_CONNECTION_ID
            while ((f = peerConnIdManager.nextFrame(remaining)) != null) {
                final int frameSize = f.size();
                assert frameSize <= remaining : "Frame too large";
                frames.add(f);
                added += frameSize;
                remaining -= frameSize;
            }

            if (remaining == 0) {
                return added;
            }
            final PacketSpace space = packetSpace(PacketNumberSpace.APPLICATION);
            final AckFrame ack = space.getNextAckFrame(false, remaining);
            if (ack != null) {
                final int ackFrameSize = ack.size();
                assert ackFrameSize <= remaining;
                if (debug.on()) {
                    debug.log("Adding AckFrame");
                }
                frames.add(ack);
                added += ackFrameSize;
                remaining -= ackFrameSize;
            }
            final long credit = credit();
            if (credit < remaining && remaining > 10) {
                if (debug.on()) {
                    debug.log("Adding DataBlockedFrame");
                }
                DataBlockedFrame dbf = new DataBlockedFrame(maxData);
                frames.add(dbf);
                added += dbf.size();
                remaining -= dbf.size();
            }
            // max data
            if (remaining > 10) {
                long maxData = oneRttRcvQueue.bumpMaxData();
                if (maxData != 0) {
                    if (debug.on()) {
                        debug.log("Adding MaxDataFrame (processed: %s)",
                                oneRttRcvQueue.processedData);
                    }
                    MaxDataFrame mdf = new MaxDataFrame(maxData);
                    frames.add(mdf);
                    added += mdf.size();
                    remaining -= mdf.size();
                }
            }
            // session ticket
            if (quicTLSEngine.getCurrentSendKeySpace() == KeySpace.ONE_RTT) {
                try {
                    ByteBuffer payloadBuffer = quicTLSEngine.getHandshakeBytes(KeySpace.ONE_RTT);
                    if (payloadBuffer != null) {
                        localCryptoFlow.enqueue(payloadBuffer);
                    }
                } catch (IOException e) {
                    throw new AssertionError("Should not happen!", e);
                }
                if (localCryptoFlow.remaining() > 0 && remaining > 3) {
                    CryptoFrame frame = localCryptoFlow.produceFrame(remaining);
                    if (frame != null) {
                        if (debug.on()) {
                            debug.log("Adding CryptoFrame");
                        }
                        frames.add(frame);
                        added += frame.size();
                        remaining -= frame.size();
                        assert remaining >= 0;
                    }
                }
            }
            return added;
        }
    }

    /**
     * Invoked to send a ONERTT packet containing stream data or
     * control frames.
     *
     * @apiNote
     * This method can be overridden if some action needs to be
     * performed after sending a packet containing certain type
     * of frames. Typically, a server side connection may want
     * to close the HANDSHAKE space only after sending the
     * HANDSHAKE_DONE frame.
     *
     * @param packet The ONERTT packet to send.
     */
    protected void send1RTTPacket(final OneRttPacket packet)
            throws QuicKeyUnavailableException, QuicTransportException {
        pushDatagram(ProtectionRecord.single(packet,
                QuicConnectionImpl.this::allocateDatagramForEncryption));
    }

    /**
     * Schedule a frame for sending in a 1-RTT packet.
     * <p>
     * For use with frames that do not change with time
     * (like MAX_* / *_BLOCKED / ACK),
     * or with remaining datagram capacity (like STREAM or CRYPTO),
     * and do not require certain path (PATH_CHALLENGE / RESPONSE).
     * <p>
     * Use with frames like HANDSHAKE_DONE, NEW_TOKEN,
     * NEW_CONNECTION_ID, RETIRE_CONNECTION_ID.
     * <p>
     * Maximum accepted frame size is 1000 bytes to ensure that the frame
     * will fit in a 1-RTT datagram in the foreseeable future.
     * @param frame frame to send
     * @throws IllegalArgumentException if frame is larger than 1000 bytes
     */
    protected void enqueue1RTTFrame(final QuicFrame frame) {
        if (frame.size() > 1000) {
            throw new IllegalArgumentException("Frame too big");
        }
        assert frame.isValidIn(PacketType.ONERTT) : "frame " + frame + " is not" +
                " eligible in 1-RTT space";
        outgoing1RTTFrames.add(frame);
    }

    /**
     * {@return true if queued frames are available for sending}
     */
    private boolean hasQueuedFrames() {
        return !outgoing1RTTFrames.isEmpty();
    }

    protected QuicPacketEncoder encoder() { return encoder;}
    protected QuicPacketDecoder decoder() { return decoder; }
    public QuicEndpoint endpoint() { return endpoint; }
    protected final StateHandle stateHandle() { return stateHandle; }
    protected CodingContext codingContext() {
        return codingContext;
    }

    public long largestAckedPN(PacketNumberSpace packetSpace) {
        var space = packetSpaces.get(packetSpace);
        return space.getLargestPeerAckedPN();
    }

    public long largestProcessedPN(PacketNumberSpace packetSpace) {
        var space = packetSpaces.get(packetSpace);
        return space.getLargestProcessedPN();
    }

    public int connectionIdLength() {
        return localConnectionId().length();
    }

    public QuicInstance quicInstance() {
        return this.quicInstance;
    }

    public QuicVersion quicVersion() {
        return this.quicVersion;
    }

    protected class QuicCodingContext implements CodingContext {
        @Override public long largestProcessedPN(PacketNumberSpace packetSpace) {
            return QuicConnectionImpl.this.largestProcessedPN(packetSpace);
        }
        @Override public long largestAckedPN(PacketNumberSpace packetSpace) {
            return QuicConnectionImpl.this.largestAckedPN(packetSpace);
        }
        @Override public int connectionIdLength() {
            return QuicConnectionImpl.this.connectionIdLength();
        }
        @Override public int writePacket(QuicPacket packet, ByteBuffer buffer)
                throws QuicKeyUnavailableException, QuicTransportException {
            int start = buffer.position();
            encoder.encode(packet, buffer, this);
            return buffer.position() - start;
        }
        @Override public QuicPacket parsePacket(ByteBuffer src)
                throws IOException, QuicKeyUnavailableException, QuicTransportException {
            return decoder.decode(src, this);
        }
        @Override
        public QuicConnectionId originalServerConnId() {
            return QuicConnectionImpl.this.originalServerConnId();
        }

        @Override
        public QuicTLSEngine getTLSEngine() {
            return quicTLSEngine;
        }

        @Override
        public boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
            return QuicConnectionImpl.this.verifyToken(destinationID, token);
        }
    }

    protected boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
        // server must send zero-length token
        return token == null;
    }

    protected PacketEmitter emitter() {
        return new PacketEmitter() {
            @Override
            public QuicTimerQueue timer() {
                return QuicConnectionImpl.this.endpoint().timer();
            }

            @Override
            public void retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts)
                    throws QuicKeyUnavailableException, QuicTransportException {
                QuicConnectionImpl.this.retransmit(packetSpaceManager, packet, attempts);
            }

            @Override
            public long emitAckPacket(PacketSpace packetSpaceManager,
                                      AckFrame frame,
                                      boolean sendPing)
                    throws QuicKeyUnavailableException, QuicTransportException {
                return QuicConnectionImpl.this.emitAckPacket(packetSpaceManager, frame, sendPing);
            }

            @Override
            public void acknowledged(QuicPacket packet) {
                QuicConnectionImpl.this.packetAcknowledged(packet);
            }

            @Override
            public boolean sendData(PacketNumberSpace packetNumberSpace)
                        throws QuicKeyUnavailableException, QuicTransportException {
                return QuicConnectionImpl.this.sendData(packetNumberSpace);
            }

            @Override
            public Executor executor() {
                return quicInstance().executor();
            }

            @Override
            public void reschedule(QuicTimedEvent task) {
                var endpoint = QuicConnectionImpl.this.endpoint();
                if (endpoint == null) return;
                endpoint.timer().reschedule(task);
            }

            @Override
            public void reschedule(QuicTimedEvent task, Deadline deadline) {
                var endpoint = QuicConnectionImpl.this.endpoint();
                if (endpoint == null) return;
                endpoint.timer().reschedule(task, deadline);
            }

            @Override
            public void checkAbort(PacketNumberSpace packetNumberSpace) {
                QuicConnectionImpl.this.checkAbort(packetNumberSpace);
            }

            @Override
            public void ptoBackoffIncreased(PacketSpaceManager space, long backoff) {
                if (Log.quicRetransmit()) {
                    Log.logQuic("%s OUT: [%s] increase backoff to %s, duration %s ms: %s"
                            .formatted(QuicConnectionImpl.this.logTag(),
                                    space.packetNumberSpace(), backoff,
                                    space.getPtoDuration().toMillis(),
                                    rttEstimator.state()));
                }
            }

            @Override
            public String logTag() {
                return QuicConnectionImpl.this.logTag();
            }

            @Override
            public boolean isOpen() {
                return QuicConnectionImpl.this.stateHandle.opened();
            }
        };
    }

    private void checkAbort(PacketNumberSpace packetNumberSpace) {
        // if pto backoff > 32 (i.e. PTO expired 5 times in a row), abort,
        // unless we haven't reached MIN_PTO_BACKOFF_TIMEOUT
        var backoff = rttEstimator.getPtoBackoff();
        if (backoff > QuicRttEstimator.MAX_PTO_BACKOFF) {
            // If the maximum backoff is exceeded, we close the connection
            // only if the associated backoff timeout exceeds the
            // MIN_PTO_BACKOFF_TIMEOUT. Otherwise, we allow the backoff
            // factor to grow again past the MAX_PTO_BACKOFF
            if (rttEstimator.isMinBackoffTimeoutExceeded()) {
                if (debug.on()) {
                    debug.log("%s Too many probe time outs: %s", packetNumberSpace, backoff);
                    debug.log(String.valueOf(rttEstimator.state()));
                    debug.log("State: %s", stateHandle().toString());
                }
                if (Log.quicRetransmit() || Log.quicCC()) {
                    Log.logQuic("%s OUT: %s: Too many probe timeouts %s"
                            .formatted(logTag(), packetNumberSpace,
                                    rttEstimator.state()));
                    StringBuilder sb = new StringBuilder(logTag());
                    sb.append(" State: ").append(stateHandle().toString());
                    for (PacketNumberSpace sp : PacketNumberSpace.values()) {
                        if (sp == PacketNumberSpace.NONE) continue;
                        if (packetSpaces.get(sp) instanceof PacketSpaceManager m) {
                            sb.append("\nPacketSpace: ").append(sp).append('\n');
                            m.debugState("  ", sb);
                        }
                    }
                    Log.logQuic(sb.toString());
                } else if (debug.on()) {
                    for (PacketNumberSpace sp : PacketNumberSpace.values()) {
                        if (sp == PacketNumberSpace.NONE) continue;
                        if (packetSpaces.get(sp) instanceof PacketSpaceManager m) {
                            m.debugState();
                        }
                    }
                }
                var pto = rttEstimator.getBasePtoDuration();
                var to = pto.multipliedBy(backoff);
                if (to.compareTo(MAX_PTO_BACKOFF_TIMEOUT) > 0) to = MAX_PTO_BACKOFF_TIMEOUT;
                String msg = "%s: Too many probe time outs (%s: backoff %s, duration %s, %s)"
                        .formatted(logTag(), packetNumberSpace, backoff,
                                to, rttEstimator.state());
                final TerminationCause terminationCause;
                if (packetNumberSpace == PacketNumberSpace.HANDSHAKE) {
                    terminationCause = TerminationCause.forException(new SSLHandshakeException(msg));
                } else if (packetNumberSpace == PacketNumberSpace.INITIAL) {
                    terminationCause = TerminationCause.forException(new ConnectException(msg));
                } else {
                    terminationCause = TerminationCause.forException(new IOException(msg));
                }
                terminator.terminate(terminationCause);
            } else {
                if (debug.on()) {
                    debug.log("%s: Max PTO backoff reached (%s) before min probe timeout exceeded (%s)," +
                            " allow more backoff %s",
                            packetNumberSpace, backoff, MIN_PTO_BACKOFF_TIMEOUT, rttEstimator.state());
                }
                if (Log.quicRetransmit() || Log.quicCC()) {
                    Log.logQuic("%s OUT: %s: Max PTO backoff reached (%s) before min probe timeout exceeded (%s) - %s"
                                .formatted(QuicConnectionImpl.this.logTag(), packetNumberSpace, backoff,
                                        MIN_PTO_BACKOFF_TIMEOUT, rttEstimator.state()));
                }
            }
        }
    }

    // this method is called when a packet has been acknowledged
    private void packetAcknowledged(QuicPacket packet) {
        // process packet frames to track acknowledgement
        // of RESET_STREAM frames etc...
        if (debug.on()) {
            debug.log("Packet %s(pn:%s) is acknowledged by peer",
                    packet.packetType(),
                    packet.packetNumber());
        }
        packet.frames().forEach(this::frameAcknowledged);
    }

    // this method is called when a frame has been acknowledged
    private void frameAcknowledged(QuicFrame frame) {
        if (frame instanceof ResetStreamFrame reset) {
            long streamId = reset.streamId();
            if (streams.isSendingStream(streamId)) {
                streams.streamResetAcknowledged(reset);
            }
        } else if (frame instanceof StreamFrame streamFrame) {
            if (streamFrame.isLast()) {
                streams.streamDataSentAcknowledged(streamFrame);
            }
        }
    }

    protected PacketSpaces packetNumberSpaces() {
        return packetSpaces;
    }
    protected PacketSpace packetSpace(PacketNumberSpace packetNumberSpace) {
        return packetSpaces.get(packetNumberSpace);
    }

    public String dbgTag() { return dbgTag; }

    public String streamDbgTag(long streamId, String direction) {
        String dir = direction == null || direction.isEmpty()
                ? "" : ("("  + direction + ")");
        return dbgTag + "[streamId" + dir +  "=" + streamId + "]";
    }


    @Override
    public CompletableFuture<QuicBidiStream> openNewLocalBidiStream(final Duration limitIncreaseDuration) {
        if (!stateHandle.opened()) {
            return MinimalFuture.failedFuture(new ClosedChannelException());
        }
        final CompletableFuture<CompletableFuture<QuicBidiStream>> streamCF =
            this.handshakeFlow.handshakeCF().thenApply((ignored) ->
                    streams.createNewLocalBidiStream(limitIncreaseDuration));
        return streamCF.thenCompose(Function.identity());
    }

    @Override
    public CompletableFuture<QuicSenderStream> openNewLocalUniStream(final Duration limitIncreaseDuration) {
        if (!stateHandle.opened()) {
            return MinimalFuture.failedFuture(new ClosedChannelException());
        }
        final CompletableFuture<CompletableFuture<QuicSenderStream>> streamCF =
        this.handshakeFlow.handshakeCF().thenApply((ignored)
                -> streams.createNewLocalUniStream(limitIncreaseDuration));
        return streamCF.thenCompose(Function.identity());
    }

    @Override
    public void addRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
        streams.addRemoteStreamListener(streamConsumer);
    }

    @Override
    public boolean removeRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
        return streams.removeRemoteStreamListener(streamConsumer);
    }

    @Override
    public Stream<? extends QuicStream> quicStreams() {
        return streams.quicStreams();
    }

    @Override
    public List<QuicConnectionId> connectionIds() {
        return localConnIdManager.connectionIds();
    }

    @Override
    public List<byte[]> activeResetTokens() {
        return peerConnIdManager.activeResetTokens();
    }

    LocalConnIdManager localConnectionIdManager() {
        return localConnIdManager;
    }

    /**
     * {@return the local connection id}
     */
    public QuicConnectionId localConnectionId() {
        return connectionId;
    }

    /**
     * {@return the peer connection id}
     */
    public QuicConnectionId peerConnectionId() {
        return this.peerConnIdManager.getPeerConnId();
    }

    /**
     * Returns the original connection id.
     * This is the original destination connection id that
     * the client generated when connecting to the server for
     * the first time.
     * @return the original connection id
     */
    protected QuicConnectionId originalServerConnId() {
        return this.peerConnIdManager.originalServerConnId();
    }

    private record IncomingDatagram(SocketAddress source, ByteBuffer destConnId,
                                    QuicPacket.HeadersType headersType, ByteBuffer buffer) {}

    @Override
    public boolean accepts(SocketAddress source) {
        // The client ever accepts packets from two sources:
        //   => the original peer address
        //   => the preferred peer address (not implemented)
        if (!source.equals(peerAddress)) {
            // We only accept packets from the endpoint to
            // which we send them.
            if (debug.on()) {
                debug.log("unexpected sender %s, skipping packet", source);
            }
            return false;
        }
        return true;
    }

    public void processIncoming(SocketAddress source, ByteBuffer destConnId,
                                QuicPacket.HeadersType headersType, ByteBuffer buffer) {
        // Processes an incoming datagram that has just been
        // read off the network.
        if (debug.on()) {
            debug.log("processIncoming %s(pos=%d, remaining=%d)",
                    headersType, buffer.position(), buffer.remaining());
        }
        if (!stateHandle.opened()) {
            if (debug.on()) {
                debug.log("connection closed, skipping packet");
            }
            return;
        }

        assert accepts(source);

        scheduleForDecryption(new IncomingDatagram(source, destConnId, headersType, buffer));
    }

    public void internalProcessIncoming(SocketAddress source, ByteBuffer destConnId,
            QuicPacket.HeadersType headersType, ByteBuffer buffer) {
        try {
            int packetIndex = 0;
            while(buffer.hasRemaining()) {
                int startPos = buffer.position();
                packetIndex++;
                boolean isLongHeader = QuicPacketDecoder.peekHeaderType(buffer, startPos) == QuicPacket.HeadersType.LONG;
                // It's only safe to check version here if versionNegotiated is true.
                // We might be receiving an INITIAL packet before the version negotiation
                // has been handled.
                if (isLongHeader) {
                    LongHeader header = QuicPacketDecoder.peekLongHeader(buffer);
                    if (header == null) {
                        if (debug.on()) {
                            debug.log("Dropping long header packet (%s in datagram): too short", packetIndex);
                        }
                        return;
                    }
                    if (!header.destinationId().matches(destConnId)) {
                        if (debug.on()) {
                            debug.log("Dropping long header packet (%s in datagram):" +
                                            " wrong connection id (%s vs %s)",
                                    packetIndex,
                                    header.destinationId().toHexString(),
                                    Utils.asHexString(destConnId));
                        }
                        return;
                    }
                    var peekedVersion = header.version();
                    final var version = this.quicVersion.versionNumber();
                    if (version != peekedVersion) {
                        if (peekedVersion == 0) {
                            if (!versionCompatible) {
                                VersionNegotiationPacket packet = (VersionNegotiationPacket) codingContext.parsePacket(buffer);
                                processDecrypted(packet);
                            } else {
                                if (debug.on()) {
                                    debug.log("Versions packet (%s in datagram) ignored", packetIndex);
                                }
                            }
                            return;
                        }
                        QuicVersion packetVersion = QuicVersion.of(peekedVersion).orElse(null);
                        if (packetVersion == null) {
                            if (debug.on()) {
                                debug.log("Unknown Quic version in long header packet" +
                                                " (%s in datagram) %s: 0x%x",
                                        packetIndex, headersType, peekedVersion);
                            }
                            return;
                        } else if (versionNegotiated) {
                            if (debug.on()) {
                                debug.log("Dropping long header packet (%s in datagram)" +
                                                " with version %s, already negotiated %s",
                                        packetIndex, packetVersion, quicVersion);
                            }
                            return;
                        } else if (!quicInstance().isVersionAvailable(packetVersion)) {
                            if (debug.on()) {
                                debug.log("Dropping long header packet (%s in datagram)" +
                                                " with disabled version %s",
                                        packetIndex, packetVersion);
                            }
                            return;
                        } else {
                            // do we need to be less trusting here?
                            if (debug.on()) {
                                debug.log("Switching version to %s, previous: %s",
                                        packetVersion, quicVersion);
                            }
                            switchVersion(packetVersion);
                        }
                    }
                    if (decoder.peekPacketType(buffer) == PacketType.INITIAL &&
                            !quicTLSEngine.keysAvailable(KeySpace.INITIAL)) {
                        if (debug.on()) {
                            debug.log("Dropping INITIAL packet (%s in datagram): %s",
                                    packetIndex, "keys discarded");
                        }
                        decoder.skipPacket(buffer, startPos);
                        continue;
                    }
                } else {
                    var cid = QuicPacketDecoder.peekShortConnectionId(buffer, destConnId.remaining());
                    if (cid == null) {
                        if (debug.on()) {
                            debug.log("Dropping short header packet (%s in datagram):" +
                                    " too short", packetIndex);
                        }
                        return;
                    }
                    if (cid.mismatch(destConnId) != -1) {
                        if (debug.on()) {
                            debug.log("Dropping short header packet (%s in datagram):" +
                                            " wrong connection id (%s vs %s)",
                                    packetIndex, Utils.asHexString(cid), Utils.asHexString(destConnId));
                        }
                        return;
                    }

                }
                ByteBuffer packet = decoder.nextPacketSlice(buffer, buffer.position());
                PacketType packetType = decoder.peekPacketType(packet);
                if (debug.on()) {
                    debug.log("unprotecting packet (%s in datagram) %s(%s bytes)",
                            packetIndex, packetType, packet.remaining());
                }
                decrypt(packet);
            }
        } catch (Throwable t) {
            if (debug.on()) {
                debug.log("Failed to process incoming packet", t);
            }
        }
    }

    /**
     * Called when an incoming packet has been decrypted.
     * <p>
     * @param quicPacket the decrypted quic packet
     */
    public void processDecrypted(QuicPacket quicPacket) {
        PacketType packetType = quicPacket.packetType();
        long packetNumber = quicPacket.packetNumber();
        if (debug.on()) {
            debug.log("processDecrypted %s(%d)", packetType, packetNumber);
        }
        if (Log.quicPacketInLoggable(quicPacket)) {
            Log.logQuicPacketIn(logTag(), quicPacket);
        }
        if (packetType != PacketType.VERSIONS) {
            versionCompatible = true;
            // versions will also set versionCompatible later
        }
        if (isClientConnection()
                && quicPacket instanceof InitialPacket longPacket
                && quicPacket.frames().stream().anyMatch(CryptoFrame.class::isInstance)) {
            markVersionNegotiated(longPacket.version());
        }
        PacketSpace packetSpace = null;
        if (packetNumber >= 0) {
            packetSpace = packetSpace(quicPacket.numberSpace());

            // From RFC 9000, Section 13.2.3:
            // A receiver MUST retain an ACK Range unless it can ensure that
            // it will not subsequently accept packets with numbers in
            // that range. Maintaining a minimum packet number that increases
            // as ranges are discarded is one way to achieve this with minimal
            // state.
            long threshold = packetSpace.getMinPNThreshold();
            if (packetNumber <= threshold) {
                // discard the packet, as we are no longer acknowledging
                // packets in this range.
                if (debug.on())
                    debug.log("discarding packet %s(%d) - threshold: %d",
                            packetType, packetNumber, threshold);
                return;
            }
            if (packetSpace.isAcknowledged(packetNumber)) {
                if (debug.on())
                    debug.log("discarding packet %s(%d) - duplicated",
                            packetType, packetNumber, threshold);
            }

            if (debug.on()) {
                debug.log("receiving packet %s(pn:%s, %s)", packetType,
                        packetNumber, quicPacket.frames());
            }
        }
        switch (packetType) {
            case VERSIONS  -> processVersionNegotiationPacket(quicPacket);
            case INITIAL   -> processInitialPacket(quicPacket);
            case ONERTT    -> processOneRTTPacket(quicPacket);
            case HANDSHAKE -> processHandshakePacket(quicPacket);
            case RETRY     -> processRetryPacket(quicPacket);
            case ZERORTT -> {
                if (debug.on()) {
                    debug.log("Dropping unhandled quic packet %s", packetType);
                }
            }
            case NONE -> throw new InternalError("Unrecognized packet type");
        }
        // packet has been processed successfully - connection isn't idle (RFC-9000, section 10.1)
        this.terminator.markActive();
        if (packetSpace != null) {
            packetSpace.packetReceived(
                    packetType,
                    packetNumber,
                    quicPacket.isAckEliciting());
        }
    }

    /**
     * {@return true if this is a stream initiated locally, and false if
     *  this is a stream initiated by the peer}.
     * @param streamId a stream ID.
     */
    protected final boolean isLocalStream(long streamId) {
        return isClientConnection() == QuicStreams.isClientInitiated(streamId);
    }

    /**
     * If a stream with this streamId was already created, returns it.
     * @param streamId the stream ID
     * @return the stream identified by the given {@code streamId}, or {@code null}.
     */
    protected QuicStream findStream(long streamId) {
        return streams.findStream(streamId);
    }

    /**
     * @return true if this stream ID identifies a stream that was
     *         already opened
     * @param streamId the stream id
     */
    protected boolean isExistingStreamId(long streamId) {
        long next = streams.peekNextStreamId(streamType(streamId));
        return streamId < next;
    }

    /**
     * Get or open a peer initiated stream with the given stream ID
     * @param streamId the id of the remote stream
     * @param frameType type of the frame received, used in exceptions
     * @return the remote initiated stream identified by the given
     * stream ID, or null
     * @throws QuicTransportException if the streamID is higher than allowed
     */
    protected QuicStream openOrGetRemoteStream(long streamId, long frameType) throws QuicTransportException {
        assert !isLocalStream(streamId);
        return streams.getOrCreateRemoteStream(streamId, frameType);
    }

    /**
     * Called to process a {@link OneRttPacket} after it has been successfully decrypted
     * @param quicPacket the Quic packet
     * @throws IllegalArgumentException if the {@code quicPacket} isn't a 1-RTT packet
     * @throws NullPointerException if {@code quicPacket} is null
     */
    protected void processOneRTTPacket(final QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.ONERTT) {
            throw new IllegalArgumentException("Not a ONERTT packet: " + quicPacket.packetType());
        }
        assert quicPacket instanceof OneRttPacket : "Unexpected ONERTT packet class type: "
                + quicPacket.getClass();
        final OneRttPacket oneRTT = (OneRttPacket) quicPacket;
        try {
            if (debug.on()) {
                debug.log("processing packet ONERTT(%s)", quicPacket.packetNumber());
            }
            final var frames = oneRTT.frames();
            if (debug.on()) {
                debug.log("processing frames: " + frames.stream()
                        .map(Object::getClass).map(Class::getSimpleName)
                        .collect(Collectors.joining(", ", "[", "]")));
            }
            for (var frame : oneRTT.frames()) {
                if (!frame.isValidIn(PacketType.ONERTT)) {
                    throw new QuicTransportException("Invalid frame in ONERTT packet",
                            KeySpace.ONE_RTT, frame.getTypeField(),
                            PROTOCOL_VIOLATION);
                }
                if (debug.on()) {
                    debug.log("received 1-RTT frame %s", frame);
                }
                switch (frame) {
                    case AckFrame ackFrame -> {
                        incoming1RTTFrame(ackFrame);
                    }
                    case StreamFrame streamFrame -> {
                        incoming1RTTFrame(streamFrame);
                    }
                    case CryptoFrame crypto -> {
                        incoming1RTTFrame(crypto);
                    }
                    case ResetStreamFrame resetStreamFrame -> {
                        incoming1RTTFrame(resetStreamFrame);
                    }
                    case DataBlockedFrame dataBlockedFrame -> {
                        incoming1RTTFrame(dataBlockedFrame);
                    }
                    case StreamDataBlockedFrame streamDataBlockedFrame -> {
                        incoming1RTTFrame(streamDataBlockedFrame);
                    }
                    case StreamsBlockedFrame streamsBlockedFrame -> {
                        incoming1RTTFrame(streamsBlockedFrame);
                    }
                    case PaddingFrame paddingFrame -> {
                        incoming1RTTFrame(paddingFrame);
                    }
                    case MaxDataFrame maxData -> {
                        incoming1RTTFrame(maxData);
                    }
                    case MaxStreamDataFrame maxStreamData -> {
                        incoming1RTTFrame(maxStreamData);
                    }
                    case MaxStreamsFrame maxStreamsFrame -> {
                        incoming1RTTFrame(maxStreamsFrame);
                    }
                    case StopSendingFrame stopSendingFrame -> {
                        incoming1RTTFrame(stopSendingFrame);
                    }
                    case PingFrame ping -> {
                        incoming1RTTFrame(ping);
                    }
                    case ConnectionCloseFrame close -> {
                        incoming1RTTFrame(close);
                    }
                    case HandshakeDoneFrame handshakeDoneFrame -> {
                        incoming1RTTFrame(handshakeDoneFrame);
                    }
                    case NewConnectionIDFrame newCid -> {
                        incoming1RTTFrame(newCid);
                    }
                    case RetireConnectionIDFrame retireCid -> {
                        incoming1RTTFrame(oneRTT, retireCid);
                    }
                    case NewTokenFrame newTokenFrame -> {
                        incoming1RTTFrame(newTokenFrame);
                    }
                    case PathResponseFrame pathResponseFrame -> {
                        incoming1RTTFrame(pathResponseFrame);
                    }
                    case PathChallengeFrame pathChallengeFrame -> {
                        incoming1RTTFrame(pathChallengeFrame);
                    }
                    default -> {
                        if (debug.on()) {
                            debug.log("Frame type: %s not supported yet", frame.getClass());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            onProcessingError(quicPacket, t);
        }
    }

    /**
     * Gets a receiving stream instance for the given ID, used for processing
     * incoming STREAM, RESET_STREAM and STREAM_DATA_BLOCKED frames.
     * Returns null if the instance is gone already. Throws an exception if the stream ID is incorrect.
     * @param streamId stream ID
     * @param frameType received frame type. Used in QuicTransportException
     * @return receiver stream, or null if stream is already gone
     * @throws QuicTransportException if the stream ID is not a valid receiving stream
     */
    private QuicReceiverStream getReceivingStream(long streamId, long frameType) throws QuicTransportException {
        var stream = findStream(streamId);
        boolean isLocalStream = isLocalStream(streamId);
        boolean isUnidirectional = isUnidirectional(streamId);
        if (isLocalStream && isUnidirectional) {
            // stream is write-only
            throw new QuicTransportException("Stream %s (type %s) is unidirectional"
                    .formatted(streamId, streamType(streamId)),
                    KeySpace.ONE_RTT, frameType, QuicTransportErrors.STREAM_STATE_ERROR);
        }
        if (stream == null && isLocalStream) {
            // the stream is either closed or bad stream
            if (!isExistingStreamId(streamId)) {
                throw new QuicTransportException("No such stream %s (type %s)"
                        .formatted(streamId, streamType(streamId)),
                        KeySpace.ONE_RTT, frameType,
                        QuicTransportErrors.STREAM_STATE_ERROR);
            }
            return null;
        }

        if (stream == null) {
            assert !isLocalStream;
            // Note: The quic protocol allows any peer to open
            //       a bidirectional remote stream.
            //       The HTTP/3 protocol does not allow a server to open a
            //       bidirectional stream on the client. If this is a client
            //       connection and the stream type is bidirectional and
            //       remote, the connection will be closed by the HTTP/3
            //       higher level protocol but not here, since this is
            //       not a Quic protocol error.
            stream = openOrGetRemoteStream(streamId, frameType);
            if (stream == null) {
                return null;
            }
        }
        return (QuicReceiverStream)stream;
    }

    /**
     * Gets a sending stream instance for the given ID, used for processing
     * incoming MAX_STREAM_DATA and STOP_SENDING frames.
     * Returns null if the instance is gone already. Throws an exception if the stream ID is incorrect.
     * @param streamId stream ID
     * @param frameType received frame type. Used in QuicTransportException
     * @return sender stream, or null if stream is already gone
     * @throws QuicTransportException if the stream ID is not a valid sending stream
     */
    private QuicSenderStream getSendingStream(long streamId, long frameType) throws QuicTransportException {
        var stream = findStream(streamId);
        boolean isLocalStream = isLocalStream(streamId);
        boolean isUnidirectional = isUnidirectional(streamId);
        if (!isLocalStream && isUnidirectional) {
            // stream is read-only
            throw new QuicTransportException("Stream %s (type %s) is unidirectional"
                    .formatted(streamId, streamType(streamId)),
                    QuicTLSEngine.KeySpace.ONE_RTT, frameType, QuicTransportErrors.STREAM_STATE_ERROR);
        }
        if (stream == null && isLocalStream) {
            // the stream is either closed or bad stream
            if (!isExistingStreamId(streamId)) {
                throw new QuicTransportException("No such stream %s (type %s)"
                        .formatted(streamId, streamType(streamId)),
                        QuicTLSEngine.KeySpace.ONE_RTT, frameType,
                        QuicTransportErrors.STREAM_STATE_ERROR);
            }
            return null;
        }

        if (stream == null) {
            assert !isLocalStream;
            stream = openOrGetRemoteStream(streamId, frameType);
            if (stream == null) {
                return null;
            }
        }
        return (QuicSenderStream)stream;
    }

    /**
     * Called to process an {@link InitialPacket} after it has been decrypted.
     * @param quicPacket the Quic packet
     * @throws IllegalArgumentException if {@code quicPacket} isn't a INITIAL packet
     * @throws NullPointerException if {@code quicPacket} is null
     */
    protected void processInitialPacket(final QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.INITIAL) {
            throw new IllegalArgumentException("Not a INITIAL packet: " + quicPacket.packetType());
        }
        try {
            if (quicPacket instanceof InitialPacket initial) {
                MaxInitialTimer initialTimer = this.maxInitialTimer;
                if (initialTimer != null) {
                    // will be a no-op after the first call;
                    initialTimer.initialPacketReceived();
                    // we no longer need the timer
                    this.maxInitialTimer = null;
                }
                int total;
                updatePeerConnectionId(initial);
                total = processInitialPacketPayload(initial);
                assert total == initial.payloadSize();
                // received initial packet from server - we won't need to replay anything now
                handshakeFlow.localInitial.discardReplayData();
                continueHandshake();
                if (quicTLSEngine.getHandshakeState() == HandshakeState.NEED_RECV_CRYPTO &&
                        quicTLSEngine.keysAvailable(KeySpace.HANDSHAKE)) {
                    // arm the anti-deadlock PTO timer
                    packetSpaces.handshake.runTransmitter();
                }
            } else {
                throw new InternalError("Bad packet type: " + quicPacket);
            }
        } catch (Throwable t) {
            terminator.terminate(TerminationCause.forException(t));
        }
    }

    protected void updatePeerConnectionId(InitialPacket initial) throws QuicTransportException {
        this.incomingInitialPacketSourceId = initial.sourceId();
        this.peerConnIdManager.finalizeHandshakePeerConnId(initial);
    }

    public QuicConnectionId getIncomingInitialPacketSourceId() {
        return incomingInitialPacketSourceId;
    }

    @Override
    public CompletableFuture<Void> handshakeReachedPeer() {
        return this.handshakeFlow.handshakeReachedPeerCF;
    }

    /**
     * Process the payload of an incoming initial packet
     * @param packet   the incoming packet
     * @return the total number of bytes consumed
     * @throws SSLHandshakeException if the handshake failed
     * @throws IOException if a frame couldn't be decoded, or the payload
     *                     wasn't entirely consumed.
     */
    protected int processInitialPacketPayload(final InitialPacket packet)
            throws IOException, QuicTransportException {
        int provided=0, total=0;
        int initialPayloadSize = packet.payloadSize();
        if (debug.on()) {
            debug.log("Processing initial packet pn:%s payload:%s",
                    packet.packetNumber(), initialPayloadSize);
        }
        for (final var frame: packet.frames()) {
            if (debug.on()) {
                debug.log("received INITIAL frame %s", frame);
            }
            int size = frame.size();
            total += size;
            switch (frame) {
                case AckFrame ack -> {
                    incomingInitialFrame(ack);
                }
                case CryptoFrame crypto -> {
                    provided = incomingInitialFrame(crypto);
                }
                case PaddingFrame paddingFrame -> {
                    incomingInitialFrame(paddingFrame);
                }
                case PingFrame ping -> {
                    incomingInitialFrame(ping);
                }
                case ConnectionCloseFrame close -> {
                    incomingInitialFrame(close);
                }
                default -> {
                    if (debug.on()) {
                        debug.log("Received invalid frame: " + frame);
                    }
                    assert !frame.isValidIn(packet.packetType()) : frame.getClass();
                    throw new QuicTransportException("Invalid frame in this packet type",
                            packet.packetType().keySpace().orElse(null), frame.getTypeField(),
                            PROTOCOL_VIOLATION);
                }
            }
        }
        if (total != initialPayloadSize) {
            throw new IOException("Initial payload wasn't fully consumed: %s read, of which %s crypto, from %s size"
                    .formatted(total, provided, initialPayloadSize));
        }
        return total;
    }
    /**
     * Process the payload of an incoming handshake packet
     * @param packet   the incoming packet
     * @return the total number of bytes consumed
     * @throws SSLHandshakeException if the handshake failed
     * @throws IOException if a frame couldn't be decoded, or the payload
     *                     wasn't entirely consumed.
     */
    protected int processHandshakePacketPayload(final HandshakePacket packet)
            throws IOException, QuicTransportException {
        int provided=0, total=0;
        int payloadSize = packet.payloadSize();
        for (final var frame: packet.frames()) {
            if (debug.on()) {
                debug.log("received HANDSHAKE frame %s", frame);
            }
            int size = frame.size();
            total += size;
            switch (frame) {
                case AckFrame ack -> {
                    incomingHandshakeFrame(ack);
                }
                case CryptoFrame crypto -> {
                    provided = incomingHandshakeFrame(crypto);
                }
                case PaddingFrame paddingFrame -> {
                    incomingHandshakeFrame(paddingFrame);
                }
                case PingFrame ping -> {
                    incomingHandshakeFrame(ping);
                }
                case ConnectionCloseFrame close -> {
                    incomingHandshakeFrame(close);
                }
                default -> {
                    assert !frame.isValidIn(packet.packetType()) : frame.getClass();
                    throw new QuicTransportException("Invalid frame in this packet type",
                            packet.packetType().keySpace().orElse(null), frame.getTypeField(),
                            PROTOCOL_VIOLATION);
                }
            }
        }
        if (total != payloadSize) {
            throw new IOException("Handshake payload wasn't fully consumed: %s read, of which %s crypto, from %s size"
                    .formatted(total, provided, payloadSize));
        }
        return total;
    }

    /**
     * Called to process an {@link HandshakePacket} after it has been decrypted.
     * @param quicPacket the handshake quic packet
     * @throws IllegalArgumentException if {@code quicPacket} is not a HANDSHAKE packet
     * @throws NullPointerException if {@code quicPacket} is null
     */
    protected void processHandshakePacket(final QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.HANDSHAKE) {
            throw new IllegalArgumentException("Not a HANDSHAKE packet: " + quicPacket.packetType());
        }
        final var handshake = this.handshakeFlow.handshakeCF();
        if (handshake.isDone() && debug.on()) {
            debug.log("Receiving HandshakePacket(%s) after handshake is done: %s",
                    quicPacket.packetNumber(), quicPacket.frames());
        }
        try {
            if (quicPacket instanceof HandshakePacket hs) {
                int total;
                total = processHandshakePacketPayload(hs);
                assert total == hs.payloadSize();
                continueHandshake();
            } else {
                throw new InternalError("Bad packet type: " + quicPacket);
            }
        } catch (Throwable t) {
            terminator.terminate(TerminationCause.forException(t));
        }
    }

    /**
     * Called to process a {@link RetryPacket} after it has been decrypted.
     * @param quicPacket the retry quic packet
     * @throws IllegalArgumentException if {@code quicPacket} is not a RETRY packet
     * @throws NullPointerException if {@code quicPacket} is null
     */
    protected void processRetryPacket(final QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.RETRY) {
            throw new IllegalArgumentException("Not a RETRY packet: " + quicPacket.packetType());
        }
        try {
            if (!(quicPacket instanceof RetryPacket rt)) {
                throw new InternalError("Bad packet type: " + quicPacket);
            }
            assert stateHandle.helloSent() : "unexpected message";
            if (rt.retryToken().length == 0) {
                if (debug.on()) {
                    debug.log("Invalid retry, empty token");
                }
                return;
            }
            final QuicConnectionId currentPeerConnId = peerConnectionId();
            if (rt.sourceId().equals(currentPeerConnId)) {
                if (debug.on()) {
                    debug.log("Invalid retry, same connection ID");
                }
                return;
            }
            if (this.peerConnIdManager.retryConnId() != null) {
                if (debug.on()) {
                    debug.log("Ignoring retry, already got one");
                }
                return;
            }
            // ignore retry if we already received initial packets
            if (incomingInitialPacketSourceId != null) {
                if (debug.on()) {
                    debug.log("Already received initial, ignoring retry");
                }
                return;
            }
            final int version = rt.version();
            final QuicVersion retryVersion = QuicVersion.of(version).orElse(null);
            if (retryVersion == null) {
                if (debug.on()) {
                    debug.log("Ignoring retry packet with unknown version 0x"
                            + Integer.toHexString(version));
                }
                // ignore the packet
                return;
            }
            final QuicVersion originalVersion = this.quicVersion; // the original version used to establish the connection
            if (originalVersion != retryVersion) {
                if (debug.on()) {
                    debug.log("Ignoring retry packet with version 0x"
                            + Integer.toHexString(version)
                            + " since it doesn't match the original version 0x"
                            + Integer.toHexString(originalVersion.versionNumber()));
                }
                // ignore the packet
                return;
            }
            ReentrantLock tl = packetSpaces.initial.getTransmitLock();
            tl.lock();
            try {
                initialToken = rt.retryToken();
                final QuicConnectionId retryConnId = rt.sourceId();
                this.peerConnIdManager.retryConnId(retryConnId);
                quicTLSEngine.deriveInitialKeys(originalVersion, retryConnId.asReadOnlyBuffer());
                this.packetSpace(PacketNumberSpace.INITIAL).retry();
                handshakeFlow.localInitial.replayData();
            } finally {
                tl.unlock();
            }
            packetSpaces.initial.runTransmitter();
        } catch (Throwable t) {
            terminator.terminate(TerminationCause.forException(t));
        }
    }

    /**
     * {@return the next (higher) max streams limit that should be advertised to the remote peer.
     * Returns {@code 0} if the limit should not be increased}
     *
     * @param bidi true if bidirectional stream, false otherwise
     */
    public long nextMaxStreamsLimit(final boolean bidi) {
        if (isClientConnection() && bidi) return 0; // server does not open bidi streams
        return streams.nextMaxStreamsLimit(bidi);
    }

    /**
     * Called when a stateless reset token is received.
     */
    @Override
    public void processStatelessReset() {
        terminator.incomingStatelessReset();
    }

    /**
     * Called to process a received {@link VersionNegotiationPacket}
     * @param quicPacket the {@link VersionNegotiationPacket}
     * @throws IllegalArgumentException if {@code quicPacket} is not a {@link PacketType#VERSIONS}
     *                                  packet
     * @throws NullPointerException if {@code quicPacket} is null
     */
    protected void processVersionNegotiationPacket(final QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.VERSIONS) {
            throw new IllegalArgumentException("Not a VERSIONS packet type: " + quicPacket.packetType());
        }
        // servers aren't expected to receive version negotiation packet
        if (!this.isClientConnection()) {
            if (debug.on()) {
                debug.log("(server) ignoring version negotiation packet");
            }
            return;
        }
        try {
            final var handshakeCF = this.handshakeFlow.handshakeCF();
            // we must ignore version negotiation if we already had a successful exchange
            var versionCompatible = this.versionCompatible;
            if (versionCompatible || handshakeCF.isDone()) {
                if (debug.on()) {
                    debug.log("ignoring version negotiation packet (neg: %s, state: %s, hs: %s)",
                            versionCompatible, stateHandle, handshakeCF);
                }
                return;
            }
            // we shouldn't receive unsolicited version negotiation packets
            assert stateHandle.helloSent();
            if (!(quicPacket instanceof VersionNegotiationPacket negotiate)) {
                if (debug.on()) {
                    debug.log("Bad packet type %s for %s",
                            quicPacket.getClass().getName(), quicPacket);
                }
                return;
            }
            if (!negotiate.sourceId().equals(originalServerConnId())) {
                if (debug.on()) {
                    debug.log("Received version negotiation packet with wrong connection id");
                    debug.log("expected source id: %s, received source id: %s",
                            originalServerConnId(), negotiate.sourceId());
                    debug.log("ignoring version negotiation packet (wrong id)");
                }
                return;
            }
            final int[] serverSupportedVersions = negotiate.supportedVersions();
            if (debug.on()) {
                debug.log("Received version negotiation packet with supported=%s",
                        Arrays.toString(serverSupportedVersions));
            }
            assert this.quicInstance() instanceof QuicClient : "Not a quic client";
            final QuicClient client = (QuicClient) this.quicInstance();
            QuicVersion negotiatedVersion = null;
            for (final int v : serverSupportedVersions) {
                final QuicVersion serverVersion = QuicVersion.of(v).orElse(null);
                if (serverVersion == null) {
                    if (debug.on()) {
                        debug.log("Ignoring unrecognized server supported version %d", v);
                    }
                    continue;
                }
                if (serverVersion == this.quicVersion) {
                    // RFC-9000, section 6.2:
                    // A client MUST discard a Version Negotiation packet that lists
                    // the QUIC version selected by the client.
                    if (debug.on()) {
                        debug.log("ignoring version negotiation packet since the version" +
                                " %d matches the current quic version selected by the client", v);
                    }
                    return;
                }
                // check if the current quic client is enabled for this version
                if (!client.isVersionAvailable(serverVersion)) {
                    if (debug.on()) {
                        debug.log("Ignoring server supported version %d because the " +
                                "client isn't enabled for it", v);
                    }
                    continue;
                }
                if (debug.on()) {
                    if (negotiatedVersion == null) {
                        debug.log("Accepting server supported version %d",
                                serverVersion.versionNumber());
                        negotiatedVersion = serverVersion;
                    } else {
                        // currently all versions are equal
                        debug.log("Skipping server supported version %d",
                                serverVersion.versionNumber());
                    }
                }
            }
            // at this point if negotiatedVersion is null, then it implies that none of the server
            // supported versions are supported by the client. The spec expects us to abandon the
            // current connection attempt in such cases (RFC-9000, section 6.2)
            if (negotiatedVersion == null) {
                final String msg = "No support for any of the QUIC versions being negotiated: "
                        + Arrays.toString(serverSupportedVersions);
                if (debug.on()) {
                    debug.log("No version could be negotiated: %s", msg);
                }
                terminator.terminate(forException(new IOException(msg)));
                return;
            }
            // a different version than the current client chosen version has been negotiated,
            // switch the client connection to use this negotiated version
            ReentrantLock tl = packetSpaces.initial.getTransmitLock();
            tl.lock();
            try {
                if (switchVersion(negotiatedVersion)) {
                    final ByteBuffer quicInitialParameters = buildInitialParameters();
                    quicTLSEngine.setLocalQuicTransportParameters(quicInitialParameters);
                    quicTLSEngine.restartHandshake();
                    handshakeFlow.localInitial.reset();
                    continueHandshake();
                    packetSpaces.initial.runTransmitter();
                    this.versionCompatible = true;
                    processedVersionsPacket = true;
                }
            } finally {
                tl.unlock();
            }
        } catch (Throwable t) {
            if (debug.on()) {
                debug.log("Failed to handle packet", t);
            }
        }

    }

    /**
     * Switch to a new version after receiving a version negotiation
     * packet. This method checks that no version was previously
     * negotiated, in which case it switches the connection to the
     * new version and returns true.
     * Otherwise, it returns false.
     *
     * @param negotiated the new version that was negotiated
     * @return true if switching to the new version was successful
     */
    protected boolean switchVersion(QuicVersion negotiated) {
        try {
            assert !versionNegotiated;
            if (debug.on())
                debug.log("switch to negotiated version %s", negotiated);
            this.quicVersion = negotiated;
            this.decoder = QuicPacketDecoder.of(negotiated);
            this.encoder = QuicPacketEncoder.of(negotiated);
            this.packetSpace(PacketNumberSpace.INITIAL).versionChanged();
            // regenerate the INITIAL keys using the new negotiated Quic version
            this.quicTLSEngine.deriveInitialKeys(negotiated, originalServerConnId().asReadOnlyBuffer());
            return true;
        } catch (Throwable t) {
            terminator.terminate(forException(t));
            throw new RuntimeException("failed to switch to version", t);
        }
    }

    /**
     * Mark the version as negotiated. No further version changes are possible.
     *
     * @param packetVersion  the packet version
     */
    protected void markVersionNegotiated(int packetVersion) {
        int version = this.quicVersion.versionNumber();
        assert packetVersion == version;
        if (!versionNegotiated) {
            if (VERSION_NEGOTIATED.compareAndSet(this, false, true)) {
                // negotiated version finalized
                quicTLSEngine.versionNegotiated(QuicVersion.of(version).get());
            }
        }
    }

    /**
     * {@return a boolean value telling whether the datagram in the
     * protection record is complete}
     * The datagram is complete when no other packet need to be coalesced
     * in the datagram.
     * If a datagram is complete, it is ready to be sent.
     *
     * @param protectionRecord the protection record
     */
    protected boolean isDatagramComplete(ProtectionRecord protectionRecord) {
        return protectionRecord.datagram.remaining() == 0
                || protectionRecord.flags == ProtectionRecord.SINGLE_PACKET
                || (protectionRecord.flags & ProtectionRecord.LAST_PACKET) != 0
                || (protectionRecord.flags & ProtectionRecord.COALESCED) == 0;
    }

    /**
     * {@return the peer address that should be used when sending datagram
     * to the peer}
     */
    public InetSocketAddress peerAddress() {
        return peerAddress;
    }

    /**
     * {@return the local address of the quic endpoint}
     * @throws UncheckedIOException if the address is not available
     */
    public SocketAddress localAddress() {
        try {
            var endpoint = this.endpoint;
            if (endpoint == null) {
                throw new IOException("no endpoint defined");
            }
            return endpoint.getLocalAddress();
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Pushes the {@linkplain ProtectionRecord#datagram() datagram} contained in
     * the {@code protectionRecord}, through the {@linkplain QuicEndpoint endpoint}.
     *
     * @param protectionRecord the ProtectionRecord containing the datagram
     */
    private void pushEncryptedDatagram(final ProtectionRecord protectionRecord) {
        final long packetNumber = protectionRecord.packet().packetNumber();
        assert packetNumber >= 0 : "unexpected packet number: " + packetNumber;
        final long retransmittedPacketNumber = protectionRecord.retransmittedPacketNumber();
        assert packetNumber > retransmittedPacketNumber : "packet number: " + packetNumber
                + " was expected to be greater than packet the packet being retransmitted: "
                + retransmittedPacketNumber;
        final boolean pktContainsConnClose = containsConnectionClose(protectionRecord.packet());
        // if the connection isn't open then except for the packet containing a CONNECTION_CLOSE
        // frame, we don't push any other packets.
        if (!isOpen() && !pktContainsConnClose) {
            if (debug.on()) {
                debug.log("connection isn't open - ignoring %s(pn:%s): frames:%s",
                        protectionRecord.packet.packetType(),
                        protectionRecord.packet.packetNumber(),
                        protectionRecord.packet.frames());
            }
            datagramDropped(new QuicDatagram(this, peerAddress, protectionRecord.datagram));
            return;
        }
        // TODO: revisit this: we need to figure out how best to emit coalesced packet,
        //       and having one protection record per packet may not be the the best.
        //       Maybe a protection record should have a list of coalesced packets
        //       instead of a single packet?
        final ByteBuffer datagram = protectionRecord.datagram();
        final int firstPacketOffset = protectionRecord.firstPacketOffset();
        // flip the datagram
        datagram.limit(datagram.position());
        datagram.position(firstPacketOffset);
        if (debug.on()) {
            final PacketType packetType = protectionRecord.packet().packetType();
            final int packetOffset = protectionRecord.packetOffset();
            if (packetOffset == firstPacketOffset) {
                debug.log("Pushing datagram([%s(%d)], %d)", packetType, packetNumber,
                        datagram.remaining());
            } else {
                debug.log("Pushing coalesced datagram([%s(%d)], %d)",
                        packetType, packetNumber, datagram.remaining());
            }
        }

        // upon successful sending of the datagram, notify that the packet was sent
        // we call packetSent just before sending the packet here, to make sure
        // that the PendingAcknowledgement will be present in the queue before
        // we receive the ACK frame from the server. Not doing this would create
        // a race where the peer might be able to send the ack, and we might process
        // it, before the PendingAcknowledgement is added.
        final QuicPacket packet = protectionRecord.packet();
        final PacketSpace packetSpace = packetSpace(packet.numberSpace());
        packetSpace.packetSent(packet, retransmittedPacketNumber, packetNumber);

        // if we are sending a packet containing a CONNECTION_CLOSE frame, then we
        // also switch/remove the current connection instance in the endpoint.
        if (pktContainsConnClose) {
            if (stateHandle.isMarked(QuicConnectionState.DRAINING)) {
                // a CONNECTION_CLOSE frame is being sent to the peer when the local
                // connection state is in DRAINING. This implies that the local endpoint
                // is responding to an incoming CONNECTION_CLOSE frame from the peer.
                // we switch this connection to one that does not respond to incoming packets.
                endpoint.pushClosedDatagram(this, peerAddress(), datagram);
            } else if (stateHandle.isMarked(QuicConnectionState.CLOSING)) {
                // a CONNECTION_CLOSE frame is being sent to the peer when the local
                // connection state is in CLOSING. For such cases, we switch this
                // connection in the endpoint to one which responds with
                // CONNECTION_CLOSE frame for any subsequent incoming packets
                // from the peer.
                endpoint.pushClosingDatagram(this, peerAddress(), datagram);
            } else {
                // should not happen
                throw new IllegalStateException("connection is neither draining nor closing," +
                        " cannot send a connection close frame");
            }
        } else {
            pushDatagram(peerAddress(), datagram);
        }
        // RFC-9000, section 10.1: An endpoint also restarts its idle timer when sending
        // an ack-eliciting packet ...
        if (packet.isAckEliciting()) {
            this.terminator.markActive();
        }
    }

    /**
     * Calls the {@link QuicEndpoint#pushDatagram(QuicPacketReceiver, SocketAddress, ByteBuffer)}
     *
     * @param destination The destination of this datagram
     * @param datagram The datagram
     */
    protected void pushDatagram(final SocketAddress destination, final ByteBuffer datagram) {
        endpoint.pushDatagram(this, destination, datagram);
    }

    /**
     * Called when a datagram scheduled for writing by this connection
     * could not be written to the network.
     * @param t the error that occurred
     */
    @Override
    public void onWriteError(Throwable t) {
        // log exception if still opened
        if (stateHandle.opened()) {
            if (Log.errors()) {
                Log.logError("%s: Failed to write datagram: %s", dbgTag(), t );
                Log.logError(t);
            } else if (debug.on()) {
                debug.log("Failed to write datagram", t);
            }
        }
    }

    /**
     * Called when a packet couldn't be processed
     * @param t the error that occurred
     */
    public void onProcessingError(QuicPacket packet, Throwable t) {
        terminator.terminate(TerminationCause.forException(t));
    }

    /**
     * Starts the Quic Handshake.
     * @return A completable future which will be completed when the
     *         handshake is completed.
     * @throws UnsupportedOperationException If this connection isn't a client connection
     */
    public final CompletableFuture<QuicEndpoint> startHandshake() {
        if (!isClientConnection()) {
            throw new UnsupportedOperationException("Not a client connection, cannot start handshake");
        }
        if (!this.startHandshakeCalled.compareAndSet(false, true)) {
            throw new IllegalStateException("handshake has already been started on connection");
        }
        if (this.peerAddress.isUnresolved()) {
            // fail if address is unresolved
            return MinimalFuture.failedFuture(
                    Utils.toConnectException(new UnresolvedAddressException()));
        }
        CompletableFuture<Void> cf;
        try {
            // register the connection with an endpoint
            assert this.quicInstance instanceof QuicClient : "Not a QuicClient";
            endpoint.registerNewConnection(this);
            cf = MinimalFuture.completedFuture(null);
        } catch (Throwable t) {
            cf = MinimalFuture.failedFuture(t);
        }
        return cf.thenApply(this::sendFirstInitialPacket)
                .exceptionally((t) -> {
                    // complete the handshake CFs with the failure
                    handshakeFlow.failHandshakeCFs(t);
                    return handshakeFlow;
                })
                .thenCompose(HandshakeFlow::handshakeCF)
                .thenApply(this::onHandshakeCompletion);
    }

    /**
     * This method is called when the handshake is successfully completed.
     * @param result the result of the handshake
     */
    protected QuicEndpoint onHandshakeCompletion(final HandshakeState result) {
        if (debug.on()) {
            debug.log("Quic handshake successfully completed with %s(%s)",
                    quicTLSEngine.getApplicationProtocol(), peerAddress());
        }
        // now that the handshake has successfully completed, start the
        // idle timeout management for this connection
        this.idleTimeoutManager.start();
        return this.endpoint;
    }

    protected HandshakeFlow handshakeFlow() {
        return handshakeFlow;
    }

    protected void startInitialTimer() {
        if (!isClientConnection()) return;
        MaxInitialTimer initialTimer = maxInitialTimer;
        if (initialTimer == null && DEFAULT_MAX_INITIAL_TIMEOUT < Integer.MAX_VALUE) {
            Deadline maxInitialDeadline = null;
            synchronized (this) {
                initialTimer = maxInitialTimer;
                if (initialTimer == null) {
                    Deadline now = this.endpoint().timeSource().instant();
                    maxInitialDeadline = now.plusSeconds(DEFAULT_MAX_INITIAL_TIMEOUT);
                    initialTimer = maxInitialTimer = new MaxInitialTimer(this.endpoint().timer(), maxInitialDeadline);
                }
            }
            if (maxInitialDeadline != null) {
                if (Log.quic()) {
                    Log.logQuic("{0}: Arming quic initial timer for {1}", logTag(),
                            Deadline.between(this.endpoint().timeSource().instant(), maxInitialDeadline));
                }
                if (debug.on()) {
                    debug.log("Arming quic initial timer for %s seconds",
                            Deadline.between(this.endpoint().timeSource().instant(), maxInitialDeadline).toSeconds());
                }
                initialTimer.timerQueue.reschedule(initialTimer, maxInitialDeadline);
            }
        }
    }

    // adaptation to Function<? super Void, HandshakeFlow>
    private HandshakeFlow sendFirstInitialPacket(Void unused) {
        // may happen if connection cancelled before endpoint is
        // created
        final TerminationCause tc = terminationCause();
        if (tc != null) {
            throw new CompletionException(tc.getCloseCause());
        }
        try {
            startInitialTimer();
            if (Log.quic()) {
                Log.logQuic(logTag() + ": connectionId: "
                    + connectionId.toHexString()
                    + ", " + endpoint + ": " + endpoint.name()
                    + " - " + endpoint.getLocalAddressString());
            } else if (debug.on()) {
                debug.log(logTag() + ": connectionId: "
                        + connectionId.toHexString()
                        + ", " + endpoint + ": " + endpoint.name()
                        + " - " + endpoint.getLocalAddressString());
            }
            var localAddress = endpoint.getLocalAddress();
            var conflict = Utils.addressConflict(localAddress, peerAddress);
            if (conflict != null) {
                String msg = conflict;
                if (debug.on()) {
                    debug.log("%s (local: %s, remote: %s)", msg, localAddress, peerAddress);
                }
                Log.logError("{0} {1} (local: {2}, remote: {3})", logTag(),
                        msg, localAddress, peerAddress);
                throw new SSLHandshakeException(msg);
            }
            final QuicConnectionId clientSelectedPeerId = initialServerConnectionId();
            this.peerConnIdManager.originalServerConnId(clientSelectedPeerId);
            handshakeFlow.markHandshakeStart();
            stateHandle.markHelloSent();
            // the "original version" used to establish the connection
            final QuicVersion originalVersion = this.quicVersion;
            quicTLSEngine.deriveInitialKeys(originalVersion, clientSelectedPeerId.asReadOnlyBuffer());
            final ByteBuffer quicInitialParameters = buildInitialParameters();
            quicTLSEngine.setLocalQuicTransportParameters(quicInitialParameters);
            handshakeFlow.localInitial.keepReplayData();
            continueHandshake();
            packetSpaces.initial.runTransmitter();
        } catch (Throwable t) {
            terminator.terminate(forException(t));
            throw new CompletionException(terminationCause().getCloseCause());
        }
        return handshakeFlow;
    }

    private static final Random RANDOM = new SecureRandom();

    private QuicConnectionId initialServerConnectionId() {
        byte[] bytes = new byte[INITIAL_SERVER_CONNECTION_ID_LENGTH];
        RANDOM.nextBytes(bytes);
        return new PeerConnectionId(bytes);
    }

    /**
     * Compose a list of Quic frames containing a crypto frame and an ack frame,
     * omitting null frames.
     * @param crypto        the crypto frame
     * @param ack           the ack frame
     * @return A list of {@link QuicFrame}.
     */
    private List<QuicFrame> makeList(CryptoFrame crypto, AckFrame ack) {
        List<QuicFrame> frames = new ArrayList<>(2);
        if (crypto != null) {
            frames.add(crypto);
        }
        if (ack != null) {
            frames.add(ack);
        }
        return frames;
    }

    /**
     * Allocate a {@link ByteBuffer} that can be used to encrypt the
     * given packet.
     * @param packet the packet to encrypt
     * @return a new {@link ByteBuffer} with sufficient space to encrypt
     * the given packet.
     */
    protected ByteBuffer allocateDatagramForEncryption(QuicPacket packet) {
        int size = packet.size();
        if (packet.hasLength()) { // packet can be coalesced
            size = Math.max(size, getMaxDatagramSize());
        }
        if (size > getMaxDatagramSize()) {

            if (Log.errors()) {
                var error = new AssertionError("%s: Size too big: %s > %s".formatted(
                        logTag(),
                        size, getMaxDatagramSize()));
                Log.logError(logTag() + ": Packet too big: " + packet.prettyPrint());
                Log.logError(error);
            } else if (debug.on()) {
                var error = new AssertionError("%s: Size too big: %s > %s".formatted(
                        logTag(),
                        size, getMaxDatagramSize()));
                debug.log("Packet too big: " + packet.prettyPrint());
                debug.log(error);
            }
            // Revisit: if we implement Path MTU detection, then the max datagram size
            //       may evolve, increasing or decreasing as the path change.
            //       In which case - we may want to tune this, down and only
            //       log an error or warning?
            final String errMsg = "Failed to encode packet, too big: " + size;
            terminator.terminate(forTransportError(PROTOCOL_VIOLATION).loggedAs(errMsg));
            throw new UncheckedIOException(terminator.getTerminationCause().getCloseCause());
        }
        return getOutgoingByteBuffer(size);
    }

    /**
     * {@return the maximum datagram size that can be used on the
     * connection path}
     * @implSpec
     * Initially this is {@link #DEFAULT_DATAGRAM_SIZE}, but the
     * value will then be decided if the peer sends a specific size
     * in the transport parameters and the value can further evolve based
     * on path MTU.
     */
    // this is public for use in tests
    public int getMaxDatagramSize() {
        // TODO: we should implement path MTU detection, or maybe let
        //       this be configurable. Sizes of 32256 or 64512 seem to
        //       be giving much better throughput when downloading.
        //       large files
        return Math.min(maxPeerAdvertisedPayloadSize, pathMTU);
    }

    /**
     * Retrieves cryptographic messages from TLS engine, enqueues them for sending
     * and starts the transmitter.
     */
    protected void continueHandshake() {
        handshakeScheduler.runOrSchedule();
    }

    protected void continueHandshake0() {
        try {
            continueHandshake1();
        } catch (Throwable t) {
            var flow = handshakeFlow;
            flow.handshakeReachedPeerCF.completeExceptionally(t);
            flow.handshakeCF.completeExceptionally(t);
        }
    }

    private void continueHandshake1() throws IOException {
        HandshakeFlow flow = handshakeFlow;
        // make sure the localInitialQueue is not modified concurrently
        // while we are in this loop
        boolean handshakeDataAvailable = false;
        boolean initialDataAvailable = false;
        for (;;) {
            var handshakeState = quicTLSEngine.getHandshakeState();
            if (debug.on()) {
                debug.log("continueHandshake: state: %s", handshakeState);
            }
            if (handshakeState == QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO) {
                // buffer next TLS message
                KeySpace keySpace = quicTLSEngine.getCurrentSendKeySpace();
                ByteBuffer payloadBuffer;
                handshakeLock.lock();
                try {
                    payloadBuffer = quicTLSEngine.getHandshakeBytes(keySpace);
                    assert payloadBuffer != null;
                    assert payloadBuffer.hasRemaining();
                    if (keySpace == KeySpace.INITIAL) {
                        flow.localInitial.enqueue(payloadBuffer);
                        initialDataAvailable = true;
                    } else if (keySpace == KeySpace.HANDSHAKE) {
                        flow.localHandshake.enqueue(payloadBuffer);
                        handshakeDataAvailable = true;
                    }
                } finally {
                    handshakeLock.unlock();
                }

                assert payloadBuffer != null;
                if (debug.on()) {
                    debug.log("continueHandshake: buffered %s bytes in %s keyspace",
                            payloadBuffer.remaining(), keySpace);
                }
            } else if (handshakeState == QuicTLSEngine.HandshakeState.NEED_TASK) {
                quicTLSEngine.getDelegatedTask().run();
            } else {
                if (debug.on()) {
                    debug.log("continueHandshake: nothing to do (state: %s)", handshakeState);
                }
                if (initialDataAvailable) {
                    packetSpaces.initial.runTransmitter();
                }
                if (handshakeDataAvailable && flow.localInitial.remaining() == 0) {
                    packetSpaces.handshake.runTransmitter();
                }
                return;
            }
        }
    }

    private boolean sendData(PacketNumberSpace packetNumberSpace)
                throws QuicKeyUnavailableException, QuicTransportException {
        if (packetNumberSpace != PacketNumberSpace.APPLICATION) {
            // This method can be called by two packet spaces: INITIAL and HANDSHAKE.
            // We need to lock to make sure that the method is not run concurrently.
            handshakeLock.lock();
            try {
                return sendInitialOrHandshakeData(packetNumberSpace);
            } finally {
                handshakeLock.unlock();
            }
        } else {
            return oneRttSndQueue.send1RTTData();
        }
    }

    private boolean sendInitialOrHandshakeData(final PacketNumberSpace packetNumberSpace)
            throws QuicKeyUnavailableException, QuicTransportException {
        if (Log.quicCrypto()) {
            Log.logQuic(String.format("%s: Send %s data", logTag(), packetNumberSpace));
        }
        final HandshakeFlow flow = handshakeFlow;
        final QuicConnectionId peerConnId = peerConnectionId();
        if (packetNumberSpace == PacketNumberSpace.INITIAL && flow.localInitial.remaining() > 0) {
            // process buffered initial data
            byte[] token = initialToken();
            int tksize = token == null ? 0 : token.length;
            PacketSpace packetSpace = packetSpaces.get(PacketNumberSpace.INITIAL);
            int maxDstIdLength = isClientConnection() ?
                    MAX_CONNECTION_ID_LENGTH :  // reserve space for the id to grow
                    peerConnId.length();
            int maxSrcIdLength = connectionId.length();
            // compute maxPayloadSize given maxSizeBeforeEncryption
            var largestAckedPN = packetSpace.getLargestPeerAckedPN();
            var packetNumber = packetSpace.allocateNextPN();
            int maxPayloadSize = QuicPacketEncoder.computeMaxInitialPayloadSize(codingContext, 4, tksize,
                    maxSrcIdLength, maxDstIdLength, SMALLEST_MAXIMUM_DATAGRAM_SIZE);
            // compute how many bytes were reserved to allow smooth retransmission
            // of packets
            int reserved = QuicPacketEncoder.computeMaxInitialPayloadSize(codingContext,
                    computePacketNumberLength(packetNumber,
                            codingContext.largestAckedPN(PacketNumberSpace.INITIAL)),
                    tksize, connectionId.length(), peerConnId.length(),
                    SMALLEST_MAXIMUM_DATAGRAM_SIZE) - maxPayloadSize;
            assert reserved >= 0 : "reserved is negative: " + reserved;
            if (debug.on()) {
                debug.log("reserved %s byte in initial packet", reserved);
            }
            if (maxPayloadSize < 5) {
                // token too long, can't fit a crypto frame in this packet. Abort.
                final String msg = "Initial token too large, maxPayload: " + maxPayloadSize;
                terminator.terminate(TerminationCause.forException(new IOException(msg)));
                return false;
            }
            AckFrame ackFrame = packetSpace.getNextAckFrame(false, maxPayloadSize);
            int ackSize = ackFrame == null ? 0 : ackFrame.size();
            if (debug.on()) {
                debug.log("ack frame size: %d", ackSize);
            }

            CryptoFrame crypto = flow.localInitial.produceFrame(maxPayloadSize - ackSize);
            int cryptoSize = crypto == null ? 0 : crypto.size();
            assert cryptoSize <= maxPayloadSize : cryptoSize - maxPayloadSize;
            List<QuicFrame> frames = makeList(crypto, ackFrame);

            if (debug.on()) {
                debug.log("building initial packet: source=%s, dest=%s",
                        connectionId, peerConnId);
            }
            OutgoingQuicPacket packet = encoder.newInitialPacket(
                    connectionId, peerConnId, token,
                    packetNumber, largestAckedPN, frames, codingContext);
            int size = packet.size();
            if (debug.on()) {
                debug.log("initial packet size is %d, max is %d",
                        size, SMALLEST_MAXIMUM_DATAGRAM_SIZE);
            }
            assert size == SMALLEST_MAXIMUM_DATAGRAM_SIZE : size - SMALLEST_MAXIMUM_DATAGRAM_SIZE;

            stateHandle.markHelloSent();
            if (debug.on()) {
                debug.log("protecting initial quic hello packet for %s(%s) - %d bytes",
                        Arrays.toString(quicTLSEngine.getSSLParameters().getApplicationProtocols()),
                        peerAddress(), packet.size());
            }
            pushDatagram(ProtectionRecord.single(packet, this::allocateDatagramForEncryption));
            if (flow.localHandshake.remaining() > 0) {
                if (Log.quicCrypto()) {
                    Log.logQuic(String.format("%s: local handshake has remaining, starting HANDSHAKE transmitter", logTag()));
                }
                packetSpaces.handshake.runTransmitter();
            }
        } else if (packetNumberSpace == PacketNumberSpace.HANDSHAKE && flow.localHandshake.remaining() > 0) {
            // process buffered handshake data
            PacketSpace packetSpace = packetSpaces.get(PacketNumberSpace.HANDSHAKE);
            AckFrame ackFrame = packetSpace.getNextAckFrame(false);
            int ackSize = ackFrame == null ? 0 : ackFrame.size();
            if (debug.on()) {
                debug.log("ack frame size: %d", ackSize);
            }
            // compute maxPayloadSize given maxSizeBeforeEncryption
            var largestAckedPN = packetSpace.getLargestPeerAckedPN();
            var packetNumber = packetSpace.allocateNextPN();
            int maxPayloadSize = QuicPacketEncoder.computeMaxHandshakePayloadSize(codingContext,
                    packetNumber, connectionId.length(), peerConnId.length(),
                    SMALLEST_MAXIMUM_DATAGRAM_SIZE);
            maxPayloadSize = maxPayloadSize - ackSize;

            final CryptoFrame crypto = flow.localHandshake.produceFrame(maxPayloadSize);
            assert crypto != null : "Handshake data was available ("
                    + flow.localHandshake.remaining() + " bytes) for sending, but no CRYPTO" +
                    " frame was produced, for max frame size: " + maxPayloadSize;
            int cryptoSize = crypto.size();
            assert cryptoSize <= maxPayloadSize : cryptoSize - maxPayloadSize;
            List<QuicFrame> frames = makeList(crypto, ackFrame);

            if (debug.on()) {
                debug.log("building handshake packet: source=%s, dest=%s",
                        connectionId, peerConnId);
            }
            OutgoingQuicPacket packet = encoder.newHandshakePacket(
                    connectionId, peerConnId,
                    packetNumber, largestAckedPN, frames, codingContext);
            int size = packet.size();
            if (debug.on()) {
                debug.log("handshake packet size is %d, max is %d",
                        size, SMALLEST_MAXIMUM_DATAGRAM_SIZE);
            }
            assert size <= SMALLEST_MAXIMUM_DATAGRAM_SIZE : size - SMALLEST_MAXIMUM_DATAGRAM_SIZE;

            if (debug.on()) {
                debug.log("protecting handshake quic hello packet for %s(%s) - %d bytes",
                        Arrays.toString(quicTLSEngine.getSSLParameters().getApplicationProtocols()),
                        peerAddress(), packet.size());
            }
            pushDatagram(ProtectionRecord.single(packet, this::allocateDatagramForEncryption));
            var handshakeState = quicTLSEngine.getHandshakeState();
            if (debug.on()) {
                debug.log("Handshake state is now: %s", handshakeState);
            }
            if (flow.localHandshake.remaining() == 0
                    && quicTLSEngine.isTLSHandshakeComplete()
                    && !flow.handshakeCF.isDone()) {
                if (stateHandle.markHandshakeComplete()) {
                    if (debug.on()) {
                        debug.log("Handshake completed");
                    }
                    completeHandshakeCF();
                }
            }
            if (!packetSpaces.initial().isClosed() && flow.localInitial.remaining() > 0) {
                if (Log.quicCrypto()) {
                    Log.logQuic(String.format("%s: local initial has remaining, starting INITIAL transmitter", logTag()));
                }
                packetSpaces.initial.runTransmitter();
            }
        } else {
            return false;
        }
        return true;
    }

    public QuicTransportParameters peerTransportParameters() {
        return peerTransportParameters;
    }

    public QuicTransportParameters localTransportParameters() {
        return localTransportParameters;
    }

    protected void consumeQuicParameters(final ByteBuffer byteBuffer) throws QuicTransportException {
        final QuicTransportParameters params = QuicTransportParameters.decode(byteBuffer);
        if (debug.on()) {
            debug.log("Received peer Quic transport params: %s", params.toStringWithValues());
        }
        final QuicConnectionId retryConnId = this.peerConnIdManager.retryConnId();
        if (params.isPresent(ParameterId.retry_source_connection_id)) {
            if (retryConnId == null) {
                throw new QuicTransportException("Retry connection ID was set even though no retry was performed",
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            } else if (!params.matches(ParameterId.retry_source_connection_id, retryConnId)) {
                throw new QuicTransportException("Retry connection ID does not match",
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
        } else {
            if (retryConnId != null) {
                throw new QuicTransportException("Retry connection ID was expected but absent",
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
        }
        if (!params.isPresent(ParameterId.original_destination_connection_id)) {
            throw new QuicTransportException(
                    "Original connection ID transport parameter missing",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);

        }
        if (!params.isPresent(initial_source_connection_id)) {
            throw new QuicTransportException(
                    "Initial source connection ID transport parameter missing",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);

        }
        final QuicConnectionId clientSelectedPeerConnId = this.peerConnIdManager.originalServerConnId();
        if (!params.matches(ParameterId.original_destination_connection_id, clientSelectedPeerConnId)) {
            throw new QuicTransportException(
                    "Original connection ID does not match",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (!params.matches(initial_source_connection_id, incomingInitialPacketSourceId)) {
            throw new QuicTransportException(
                    "Initial source connection ID does not match",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        // RFC-9000, section 18.2: A server that chooses a zero-length connection ID MUST NOT
        // provide a preferred address.
        if (peerConnectionId().length() == 0 &&
                params.isPresent(ParameterId.preferred_address)) {
            throw new QuicTransportException(
                    "Preferred address present but connection ID has zero length",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (params.isPresent(active_connection_id_limit)) {
            final long limit = params.getIntParameter(active_connection_id_limit);
            if (limit < 2) {
                throw new QuicTransportException(
                        "Invalid active_connection_id_limit " + limit,
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
        }
        if (params.isPresent(ParameterId.stateless_reset_token)) {
            final byte[] statelessResetToken = params.getParameter(ParameterId.stateless_reset_token);
            if (statelessResetToken.length != RESET_TOKEN_LENGTH) {
                // RFC states 16 bytes for stateless token
                throw new QuicTransportException(
                        "Invalid stateless reset token length " + statelessResetToken.length,
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
        }
        VersionInformation vi =
                params.getVersionInformationParameter(version_information);
        if (vi != null) {
            if (vi.chosenVersion() != quicVersion().versionNumber()) {
                throw new QuicTransportException(
                        "[version_information] Chosen Version does not match version in use",
                        null, 0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
            }
            if (processedVersionsPacket) {
                if (vi.availableVersions().length == 0) {
                    throw new QuicTransportException(
                            "[version_information] available versions empty",
                            null, 0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
                }
                if (Arrays.stream(vi.availableVersions())
                        .anyMatch(i -> i == originalVersion.versionNumber())) {
                    throw new QuicTransportException(
                            "[version_information] original version was available",
                            null, 0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
                }
            }
        } else {
            if (processedVersionsPacket && quicVersion != QuicVersion.QUIC_V1) {
                throw new QuicTransportException(
                        "version_information parameter absent",
                        null, 0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
            }
        }
        handleIncomingPeerTransportParams(params);

        // params.setIntParameter(ParameterId.max_idle_timeout, TimeUnit.SECONDS.toMillis(30));
        // params.setParameter(ParameterId.stateless_reset_token, ...); // no token
        // params.setIntParameter(ParameterId.initial_max_data, DEFAULT_INITIAL_MAX_DATA);
        // params.setIntParameter(ParameterId.initial_max_stream_data_bidi_local, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.setIntParameter(ParameterId.initial_max_stream_data_bidi_remote, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.setIntParameter(ParameterId.initial_max_stream_data_uni, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.setIntParameter(ParameterId.initial_max_streams_bidi, DEFAULT_MAX_STREAMS);
        // params.setIntParameter(ParameterId.initial_max_streams_uni, DEFAULT_MAX_STREAMS);
        // params.setIntParameter(ParameterId.ack_delay_exponent, 3); // unit 2^3 microseconds
        // params.setIntParameter(ParameterId.max_ack_delay, 25); //25 millis
        // params.setBooleanParameter(ParameterId.disable_active_migration, false);
        // params.setPreferredAddressParameter(ParameterId.preferred_address, ...);
        // params.setIntParameter(ParameterId.active_connection_id_limit, 2);
    }

    /**
     * {@return the number of (active) connection ids that this endpoint is willing
     * to accept from the peer for a given connection}
     */
    protected long getLocalActiveConnIDLimit() {
        // currently we don't accept anything more than 2 (the RFC defined default minimum)
        return 2;
    }

    /**
     * {@return the number of (active) connection ids that the peer is willing to accept
     * for a given connection}
     */
    protected long getPeerActiveConnIDLimit() {
        return this.peerActiveConnIdsLimit;
    }

    protected ByteBuffer buildInitialParameters() {
        final QuicTransportParameters params = new QuicTransportParameters(this.transportParams);
        setIntParamIfNotSet(params, active_connection_id_limit, this::getLocalActiveConnIDLimit);
        final long idleTimeoutMillis = TimeUnit.SECONDS.toMillis(
                Utils.getLongProperty("jdk.httpclient.quic.idleTimeout", 30));
        setIntParamIfNotSet(params, max_idle_timeout, () -> idleTimeoutMillis);
        setIntParamIfNotSet(params, max_udp_payload_size, () -> {
            assert this.endpoint != null : "Endpoint hasn't been set";
            return (long) this.endpoint.getMaxUdpPayloadSize();
        });
        setIntParamIfNotSet(params, initial_max_data, () -> DEFAULT_INITIAL_MAX_DATA);
        setIntParamIfNotSet(params, initial_max_stream_data_bidi_local,
                () -> DEFAULT_INITIAL_STREAM_MAX_DATA);
        setIntParamIfNotSet(params, initial_max_stream_data_uni, () -> DEFAULT_INITIAL_STREAM_MAX_DATA);
        setIntParamIfNotSet(params, initial_max_stream_data_bidi_remote, () -> DEFAULT_INITIAL_STREAM_MAX_DATA);
        setIntParamIfNotSet(params, initial_max_streams_uni, () -> (long) DEFAULT_MAX_UNI_STREAMS);
        setIntParamIfNotSet(params, initial_max_streams_bidi, () -> (long) DEFAULT_MAX_BIDI_STREAMS);
        if (!params.isPresent(initial_source_connection_id)) {
            params.setParameter(initial_source_connection_id, connectionId.getBytes());
        }
        if (!params.isPresent(version_information)) {
            final VersionInformation vi =
                    QuicTransportParameters.buildVersionInformation(quicVersion,
                            quicInstance().getAvailableVersions());
            params.setVersionInformationParameter(version_information, vi);
        }
        // params.setIntParameter(ParameterId.ack_delay_exponent, 3); // unit 2^3 microseconds
        // params.setIntParameter(ParameterId.max_ack_delay, 25); //25 millis
        // params.setBooleanParameter(ParameterId.disable_active_migration, false);
        final ByteBuffer buf = ByteBuffer.allocate(params.size());
        params.encode(buf);
        buf.flip();
        if (debug.on()) {
            debug.log("local transport params: %s", params.toStringWithValues());
        }
        newLocalTransportParameters(params);
        return buf;
    }

    protected static void setIntParamIfNotSet(final QuicTransportParameters params,
                                              final ParameterId paramId,
                                              final Supplier<Long> valueSupplier) {
        if (params.isPresent(paramId)) {
            return;
        }
        params.setIntParameter(paramId, valueSupplier.get());
    }

    // the token to be included in initial packets, if any.
    private byte[] initialToken() {
        return initialToken;
    }

    protected void newLocalTransportParameters(QuicTransportParameters params) {
        localTransportParameters = params;
        oneRttRcvQueue.newLocalParameters(params);
        streams.newLocalTransportParameters(params);
        final long idleTimeout = params.getIntParameter(max_idle_timeout, 0);
        this.idleTimeoutManager.localIdleTimeout(idleTimeout);
    }

    private List<QuicFrame> ackOrPing(AckFrame ack, boolean sendPing) {
        if (sendPing) {
            return ack == null ? List.of(new PingFrame()) : List.of(new PingFrame(), ack);
        }
        assert ack != null;
        return List.of(ack);
    }

    /**
     * Emit a possibly non ACK-eliciting packet containing the given ACK frame.
     * @param packetSpaceManager the packet space manager on behalf
     *                           of which the acknowledgement should
     *                           be sent.
     * @param ackFrame the ACK frame to be sent.
     * @param sendPing whether a PING frame should be sent.
     * @return the emitted packet number, or -1L if not applicable or not emitted
     */
    private long emitAckPacket(final PacketSpace packetSpaceManager, final AckFrame ackFrame,
                               final boolean sendPing)
            throws QuicKeyUnavailableException, QuicTransportException {
        if (ackFrame == null && !sendPing) {
            return -1L;
        }
        if (debug.on()) {
            if (sendPing) {
                debug.log("Sending PING packet %s ack",
                        ackFrame == null ? "without" : "with");
            } else {
                debug.log("sending ACK packet");
            }
        }
        final List<QuicFrame> frames = ackOrPing(ackFrame, sendPing);
        final PacketNumberSpace packetNumberSpace = packetSpaceManager.packetNumberSpace();
        if (debug.on()) {
            debug.log("Sending packet for %s, frame=%s", packetNumberSpace, frames);
        }
        final KeySpace keySpace = switch (packetNumberSpace) {
            case APPLICATION -> KeySpace.ONE_RTT;
            case HANDSHAKE -> KeySpace.HANDSHAKE;
            case INITIAL -> KeySpace.INITIAL;
            default -> throw new UnsupportedOperationException(
                    "Invalid packet number space: " + packetNumberSpace);
        };
        if (sendPing && Log.quicRetransmit()) {
            Log.logQuic("{0} {1}: sending PingFrame", logTag(), keySpace);
        }
        final QuicPacket ackpacket = encoder.newOutgoingPacket(keySpace,
                packetSpaceManager, localConnectionId(),
                peerConnectionId(), initialToken(), frames, codingContext);
        pushDatagram(ProtectionRecord.single(ackpacket, this::allocateDatagramForEncryption));
        return ackpacket.packetNumber();
    }

    private LinkedList<QuicFrame> removeOutdatedFrames(List<QuicFrame> frames) {
        // Remove frames that should not be retransmitted
        LinkedList<QuicFrame> result = new LinkedList<>();
        for (QuicFrame f : frames) {
            if (!(f instanceof PaddingFrame) &&
                    !(f instanceof AckFrame) &&
                    !(f instanceof PathChallengeFrame) &&
                    !(f instanceof PathResponseFrame)) {
               result.add(f);
            }
        }
        return result;
    }

    /**
     * Retransmit the given packet on behalf of the given packet space
     * manager.
     * @param packetSpaceManager the packet space manager on behalf of
     *                           which the packet is being retransmitted
     * @param packet the unacknowledged packet which should be retransmitted
     */
    private void retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts)
            throws QuicKeyUnavailableException, QuicTransportException {
        if (debug.on()) {
            debug.log("Retransmitting packet [type=%s, pn=%d, attempts:%d]: %s",
                    packet.packetType(), packet.packetNumber(), attempts, packet);
        }

        assert packetSpaceManager.packetNumberSpace() == packet.numberSpace();
        long oldPacketNumber = packet.packetNumber();
        assert oldPacketNumber >= 0;

        long largestAckedPN = packetSpaceManager.getLargestPeerAckedPN();
        long newPacketNumber = packetSpaceManager.allocateNextPN();
        final int maxDatagramSize = getMaxDatagramSize();
        final QuicConnectionId peerConnectionId = peerConnectionId();
        final int dstIdLength = peerConnectionId.length();
        final PacketNumberSpace packetNumberSpace = packetSpaceManager.packetNumberSpace();
        final int initialDstIdLength = MAX_CONNECTION_ID_LENGTH; // reserve space for the ID to grow

        int maxPayloadSize = switch (packetNumberSpace) {
            case APPLICATION -> QuicPacketEncoder.computeMaxOneRTTPayloadSize(
                        codingContext, newPacketNumber, dstIdLength, maxDatagramSize, largestAckedPN);
            case INITIAL -> QuicPacketEncoder.computeMaxInitialPayloadSize(
                        codingContext, computePacketNumberLength(newPacketNumber,
                                codingContext.largestAckedPN(PacketNumberSpace.INITIAL)),
                        ((InitialPacket) packet).tokenLength(),
                        localConnectionId().length(), initialDstIdLength, maxDatagramSize);
            case HANDSHAKE -> QuicPacketEncoder.computeMaxHandshakePayloadSize(
                    codingContext, newPacketNumber, localConnectionId().length(),
                    dstIdLength, maxDatagramSize);
            default -> throw new IllegalArgumentException(
                    "Invalid packet number space: " + packetNumberSpace);
        };

        // The new packet may have larger size(), which might no longer fit inside
        // the maximum datagram size supported on the path. To avoid that, we
        // strip the padding and old ack frame from the original packet, and
        // include the new ack frame only if it fits in the available size.
        LinkedList<QuicFrame> frames = removeOutdatedFrames(packet.frames());
        int size = frames.stream().mapToInt(QuicFrame::size).sum();
        int remaining = maxPayloadSize - size;
        AckFrame ack = packetSpaceManager.getNextAckFrame(false, remaining);
        if (ack != null) {
            assert ack.size() <= remaining : "AckFrame size %s is bigger than %s"
                    .formatted(ack.size(), remaining);
            frames.addFirst(ack);
        }
        QuicPacket retransmitted =
                switch (packet.packetType()) {
                    case INITIAL -> encoder.newInitialPacket(localConnectionId(),
                            peerConnectionId, ((InitialPacket) packet).token(),
                            newPacketNumber, largestAckedPN, frames,
                            codingContext);
                    case HANDSHAKE -> encoder.newHandshakePacket(localConnectionId(),
                            peerConnectionId, newPacketNumber, largestAckedPN,
                            frames, codingContext);
                    case ONERTT, ZERORTT -> encoder.newOneRttPacket(
                            peerConnectionId, newPacketNumber, largestAckedPN,
                            frames, codingContext);
                    default -> throw new IllegalArgumentException("packetType: %s, packet: %s"
                            .formatted(packet.packetType(), packet.packetNumber()));
                };

        if (Log.quicRetransmit()) {
            Log.logQuic("%s OUT: retransmitting packet [%s] pn:%s as pn:%s".formatted(
                    logTag(), packet.packetType(), oldPacketNumber, newPacketNumber));
        }
        pushDatagram(ProtectionRecord.retransmitting(retransmitted,
                oldPacketNumber,
                this::allocateDatagramForEncryption));
    }

    @Override
    public CompletableFuture<Long> requestSendPing() {
        final KeySpace space = quicTLSEngine.getCurrentSendKeySpace();
        final PacketSpace spaceManager = packetSpaces.get(PacketNumberSpace.of(space));
        return spaceManager.requestSendPing();
    }

    /**
     * {@return the underlying {@code NetworkChannel} used by this connection,
     *  which may be {@code null} if the endpoint has not been configured yet}
     */
    public NetworkChannel channel() {
        QuicEndpoint endpoint = this.endpoint;
        return endpoint == null ? null : endpoint.channel();
    }

    @Override
    public String toString() {
        return cachedToString;
    }

    @Override
    public boolean isOpen() {
        return stateHandle.opened();
    }

    @Override
    public TerminationCause terminationCause() {
        return terminator.getTerminationCause();
    }

    public final CompletableFuture<TerminationCause> futureTerminationCause() {
        return terminator.futureTerminationCause();
    }

    @Override
    public ConnectionTerminator connectionTerminator() {
        return this.terminator;
    }

    /**
     * {@return true if this connection is a client connection}
     * Server side connections will return false.
     */
    public boolean isClientConnection() {
        return true;
    }

    /**
     * Called when new quic transport parameters are available from the peer.
     * @param params the peer's new quic transport parameter
     */
    protected void handleIncomingPeerTransportParams(final QuicTransportParameters params) {
        peerTransportParameters = params;
        this.idleTimeoutManager.peerIdleTimeout(params.getIntParameter(max_idle_timeout));
        // when we reach here, the value for max_udp_payload_size has already been
        // asserted that it isn't outside the allowed range of 1200 to 65527. That has
        // happened in QuicTransportParameters.checkParameter().
        // intentional cast to int since the value will be within int range
        maxPeerAdvertisedPayloadSize = (int) params.getIntParameter(max_udp_payload_size);
        congestionController.updateMaxDatagramSize(getMaxDatagramSize());
        if (params.isPresent(ParameterId.initial_max_data)) {
            oneRttSndQueue.setMaxData(params.getIntParameter(ParameterId.initial_max_data), true);
        }
        streams.newPeerTransportParameters(params);
        packetSpaces.app().updatePeerTransportParameters(
                params.getIntParameter(ParameterId.max_ack_delay),
                params.getIntParameter(ParameterId.ack_delay_exponent));
        // param value for this param is already validated outside of this method, so we just
        // set the value without any validations
        this.peerActiveConnIdsLimit = params.getIntParameter(active_connection_id_limit);
        if (params.isPresent(ParameterId.stateless_reset_token)) {
            // the stateless reset token for the handshake connection id
            final byte[] statelessResetToken = params.getParameter(ParameterId.stateless_reset_token);
            // register with peer connid manager
            this.peerConnIdManager.handshakeStatelessResetToken(statelessResetToken);
        }
        if (params.isPresent(ParameterId.preferred_address)) {
            final byte[] val = params.getParameter(ParameterId.preferred_address);
            final ByteBuffer preferredConnId = QuicTransportParameters.getPreferredConnectionId(val);
            final byte[] preferredStatelessResetToken = QuicTransportParameters
                    .getPreferredStatelessResetToken(val);
            this.peerConnIdManager.handlePreferredAddress(preferredConnId, preferredStatelessResetToken);
        }
        if (debug.on()) {
            debug.log("incoming peer parameters handled");
        }
    }

    protected void incomingInitialFrame(final AckFrame frame) throws QuicTransportException {
        packetSpaces.initial.processAckFrame(frame);
        if (!handshakeFlow.handshakeReachedPeerCF.isDone()) {
            if (debug.on()) debug.log("completing handshakeStartedCF normally");
            handshakeFlow.handshakeReachedPeerCF.complete(null);
        }
    }

    protected int incomingInitialFrame(final CryptoFrame frame) throws QuicTransportException {
        // make sure to provide the frames in order, and
        // buffer them if at the wrong offset
        if (!handshakeFlow.handshakeReachedPeerCF.isDone()) {
            if (debug.on()) debug.log("completing handshakeStartedCF normally");
            handshakeFlow.handshakeReachedPeerCF.complete(null);
        }
        final CryptoDataFlow peerInitial = handshakeFlow.peerInitial;
        final long buffer = frame.offset() + frame.length() - peerInitial.offset();
        if (buffer > MAX_INCOMING_CRYPTO_CAPACITY) {
            throw new QuicTransportException(
                    "Crypto buffer exceeded, required: " + buffer,
                    KeySpace.INITIAL, frame.frameType(),
                    QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED);
        }
        int provided = 0;
        CryptoFrame nextFrame = peerInitial.receive(frame);
        while (nextFrame != null) {
            if (debug.on()) {
                debug.log("Provide crypto frame to engine: %s", nextFrame);
            }
            quicTLSEngine.consumeHandshakeBytes(KeySpace.INITIAL, nextFrame.payload());
            provided += nextFrame.length();
            nextFrame = peerInitial.poll();
            if (debug.on()) {
                debug.log("Provided: " + provided);
            }
        }
        return provided;
    }

    protected void incomingInitialFrame(final PaddingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    protected void incomingInitialFrame(final PingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    protected void incomingInitialFrame(final ConnectionCloseFrame frame)
            throws QuicTransportException {
        terminator.incomingConnectionCloseFrame(frame);
    }

    protected void incomingHandshakeFrame(final AckFrame frame) throws QuicTransportException {
        packetSpaces.handshake.processAckFrame(frame);
    }

    protected int incomingHandshakeFrame(final CryptoFrame frame) throws QuicTransportException {
        final CryptoDataFlow peerHandshake = handshakeFlow.peerHandshake;
        // make sure to provide the frames in order, and
        // buffer them if at the wrong offset
        final long buffer = frame.offset() + frame.length() - peerHandshake.offset();
        if (buffer > MAX_INCOMING_CRYPTO_CAPACITY) {
            throw new QuicTransportException(
                    "Crypto buffer exceeded, required: " + buffer,
                    KeySpace.HANDSHAKE, frame.frameType(),
                    QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED);
        }
        int provided = 0;
        CryptoFrame nextFrame = peerHandshake.receive(frame);
        while (nextFrame != null) {
            quicTLSEngine.consumeHandshakeBytes(KeySpace.HANDSHAKE, nextFrame.payload());
            provided += nextFrame.length();
            nextFrame = peerHandshake.poll();
        }
        return provided;
    }

    protected void incomingHandshakeFrame(final PaddingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    protected void incomingHandshakeFrame(final PingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    protected void incomingHandshakeFrame(final ConnectionCloseFrame frame)
            throws QuicTransportException {
        terminator.incomingConnectionCloseFrame(frame);
    }

    protected void incoming1RTTFrame(final AckFrame ackFrame) throws QuicTransportException {
        packetSpaces.app.processAckFrame(ackFrame);
    }

    protected void incoming1RTTFrame(final StreamFrame frame) throws QuicTransportException {
        final long streamId = frame.streamId();
        final QuicReceiverStream stream = getReceivingStream(streamId, frame.getTypeField());
        if (stream != null) {
            assert frame.streamId() == stream.streamId();
            streams.processIncomingFrame(stream, frame);
        }
    }

    protected void incoming1RTTFrame(final CryptoFrame frame) throws QuicTransportException {
        final long buffer = frame.offset() + frame.length() - peerCryptoFlow.offset();
        if (buffer > MAX_INCOMING_CRYPTO_CAPACITY) {
            throw new QuicTransportException(
                    "Crypto buffer exceeded, required: " + buffer,
                    KeySpace.ONE_RTT, frame.frameType(),
                    QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED);
        }
        CryptoFrame nextFrame = peerCryptoFlow.receive(frame);
        while (nextFrame != null) {
            quicTLSEngine.consumeHandshakeBytes(KeySpace.ONE_RTT, nextFrame.payload());
            nextFrame = peerCryptoFlow.poll();
        }
    }

    protected void incoming1RTTFrame(final ResetStreamFrame frame) throws QuicTransportException {
        final long streamId = frame.streamId();
        final QuicReceiverStream stream = getReceivingStream(streamId, frame.getTypeField());
        if (stream != null) {
            assert frame.streamId() == stream.streamId();
            streams.processIncomingFrame(stream, frame);
        }
    }

    protected void incoming1RTTFrame(final StreamDataBlockedFrame frame)
            throws QuicTransportException {
        final QuicReceiverStream stream = getReceivingStream(frame.streamId(), frame.getTypeField());
        if (stream != null) {
            assert frame.streamId() == stream.streamId();
            streams.processIncomingFrame(stream, frame);
        }
    }

    protected void incoming1RTTFrame(final DataBlockedFrame frame) throws QuicTransportException {
        // TODO implement similar logic as STREAM_DATA_BLOCKED frame receipt
        // and increment gradually if consumption is more than 1/4th the window size of the
        // connection
    }

    protected void incoming1RTTFrame(final StreamsBlockedFrame frame)
            throws QuicTransportException {
        if (frame.maxStreams() > MAX_STREAMS_VALUE_LIMIT) {
            throw new QuicTransportException("Invalid maxStreams value %s"
                    .formatted(frame.maxStreams()),
                    KeySpace.ONE_RTT,
                    frame.getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        streams.peerStreamsBlocked(frame);
    }

    protected void incoming1RTTFrame(final PaddingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    protected void incoming1RTTFrame(final MaxDataFrame frame) throws QuicTransportException {
        oneRttSndQueue.setMaxData(frame.maxData(), false);
    }

    protected void incoming1RTTFrame(final MaxStreamDataFrame frame)
            throws QuicTransportException {
        final long streamId = frame.streamID();
        final QuicSenderStream stream = getSendingStream(streamId, frame.getTypeField());
        if (stream != null) {
            streams.setMaxStreamData(stream, frame.maxStreamData());
        }
    }

    protected void incoming1RTTFrame(final MaxStreamsFrame frame) throws QuicTransportException {
        if (frame.maxStreams() >> 60 != 0) {
            throw new QuicTransportException("Invalid maxStreams value %s"
                    .formatted(frame.maxStreams()),
                    KeySpace.ONE_RTT,
                    frame.getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        final boolean increased = streams.tryIncreaseStreamLimit(frame);
        if (debug.on()) {
            debug.log((increased ? "increased" : "did not increase")
                    + " " + (frame.isBidi() ? "bidi" : "uni")
                    + " stream limit to " + frame.maxStreams());
        }
    }

    protected void incoming1RTTFrame(final StopSendingFrame frame) throws QuicTransportException {
        final long streamId = frame.streamID();
        final QuicSenderStream stream = getSendingStream(streamId, frame.getTypeField());
        if (stream != null) {
            streams.stopSendingReceived(stream,
                    frame.errorCode());
        }
    }

    protected void incoming1RTTFrame(final PingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    protected void incoming1RTTFrame(final ConnectionCloseFrame frame)
            throws QuicTransportException {
        terminator.incomingConnectionCloseFrame(frame);
    }

    protected void incoming1RTTFrame(final HandshakeDoneFrame frame)
            throws QuicTransportException {
        if (quicTLSEngine.tryReceiveHandshakeDone()) {
            // now that HANDSHAKE_DONE is received (and thus handshake confirmed),
            // close the HANDSHAKE packet space (and thus discard the keys)
            if (debug.on()) {
                debug.log("received HANDSHAKE_DONE from server, initiating close of" +
                        " HANDSHAKE packet space");
            }
            packetSpaces.handshake.close();
        }
        packetSpaces.app.confirmHandshake();
    }

    protected void incoming1RTTFrame(final NewConnectionIDFrame frame)
            throws QuicTransportException {
        if (peerConnectionId().length() == 0) {
            throw new QuicTransportException(
                    "NEW_CONNECTION_ID not allowed here",
                    null, frame.getTypeField(), PROTOCOL_VIOLATION);
        }
        this.peerConnIdManager.handleNewConnectionIdFrame(frame);
    }

    protected void incoming1RTTFrame(final OneRttPacket oneRttPacket,
                                     final RetireConnectionIDFrame frame)
            throws QuicTransportException {
        this.localConnIdManager.handleRetireConnectionIdFrame(oneRttPacket.destinationId(),
                PacketType.ONERTT, frame);
    }

    protected void incoming1RTTFrame(final NewTokenFrame frame) throws QuicTransportException {
        // as per RFC 9000, section 19.7, token cannot be empty and if it is, then
        // a connection error of type FRAME_ENCODING_ERROR needs to be raised
        final byte[] newToken = frame.token();
        if (newToken.length == 0) {
            throw new QuicTransportException("Empty token in NEW_TOKEN frame",
                    KeySpace.ONE_RTT,
                    frame.getTypeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        assert this.quicInstance instanceof QuicClient : "Not a QuicClient";
        final QuicClient quicClient = (QuicClient) this.quicInstance;
        // set this as the initial token to be used in INITIAL packets when attempting
        // any new subsequent connections against this same target server
        quicClient.registerInitialToken(this.peerAddress, newToken);
        if (debug.on()) {
            debug.log("Registered a new (initial) token for peer " + this.peerAddress);
        }
    }

    protected void incoming1RTTFrame(final PathResponseFrame frame)
            throws QuicTransportException {
        throw new QuicTransportException("Unmatched PATH_RESPONSE frame",
                KeySpace.ONE_RTT,
                frame.getTypeField(), PROTOCOL_VIOLATION);
    }

    protected void incoming1RTTFrame(final PathChallengeFrame frame)
            throws QuicTransportException {
        pathChallengeFrameQueue.offer(frame);
        if (pathChallengeFrameQueue.size() > 3) {
            // we don't expect to hold more than 1 PathChallenge per path.
            // If there's more than 3 outstanding challenges, drop the oldest one.
            pathChallengeFrameQueue.poll();
        }
    }

    /**
     * Signal the connection that some stream data is available for sending on one or more streams.
     * @param streamIds the stream ids
     */
    public void streamDataAvailableForSending(final Set<Long> streamIds) {
        for (final long id : streamIds) {
            streams.enqueueForSending(id);
        }
        packetSpaces.app.runTransmitter();
    }

    /**
     * Called when the receiving part or the sending part of a stream
     * reaches a terminal state.
     * @param streamId the id of the stream
     * @param state the terminal state
     */
    public void notifyTerminalState(long streamId, StreamState state) {
        assert state.isTerminal() : state;
        streams.notifyTerminalState(streamId, state);
    }

    /**
     * Called to request sending of a RESET_STREAM frame.
     *
     * @apiNote
     * Should only be called for sending streams. For stopping a
     * receiving stream then {@link #scheduleStopSendingFrame(long, long)} should be called.
     * This method should only be called from {@code QuicSenderStreamImpl}, after
     * switching the state of the stream to RESET_SENT.
     *
     * @param streamId  the id of the stream that should be reset
     * @param errorCode the application error code
     */
    public void requestResetStream(long streamId, long errorCode) {
        assert streams.isSendingStream(streamId);
        streams.requestResetStream(streamId, errorCode);
        packetSpaces.app.runTransmitter();
    }

    /**
     * Called to request sending of a STOP_SENDING frame.
     * @apiNote
     * Should only be called for receiving streams. For stopping a
     * sending stream then {@link #requestResetStream(long, long)}
     * should be called.
     * This method should only be called from {@code QuicReceiverStreamImpl}
     * @param streamId the stream id to be cancelled
     * @param errorCode the application error code
     */
    public void scheduleStopSendingFrame(long streamId, long errorCode) {
        assert streams.isReceivingStream(streamId);
        streams.scheduleStopSendingFrame(new StopSendingFrame(streamId, errorCode));
        packetSpaces.app.runTransmitter();
    }

    /**
     * Called to request sending of a MAX_STREAM_DATA frame.
     * @apiNote
     * Should only be called for receiving streams.
     * This method should only be called from {@code QuicReceiverStreamImpl}
     * @param streamId the stream id to be cancelled
     * @param maxStreamData the new max data we are prepared to receive on
     *                      this stream
     */
    public void requestSendMaxStreamData(long streamId, long maxStreamData) {
        assert streams.isReceivingStream(streamId);
        streams.requestSendMaxStreamData(new MaxStreamDataFrame(streamId, maxStreamData));
        packetSpaces.app.runTransmitter();
    }

    /**
     * Called when frame data can be safely added to the amount of
     * data received by the connection for MAX_DATA flow control
     * purpose.
     * @throws QuicTransportException if flow control was exceeded
     * @param frameType type of frame received
     */
    public void increaseReceivedData(long diff, long frameType) throws QuicTransportException {
        oneRttRcvQueue.checkAndIncreaseReceivedData(diff, frameType);
    }

    /**
     * Called when frame data is removed from the connection
     * and the amount of data can be added to MAX_DATA window.
     * @param diff amount of data processed
     */
    public void increaseProcessedData(long diff) {
        oneRttRcvQueue.increaseProcessedData(diff);
    }

    public QuicTLSEngine getTLSEngine() {
        return quicTLSEngine;
    }

    /**
     * {@return the computed PTO for the current packet number space,
     * adjusted by our max ack delay}
     */
    public long peerPtoMs() {
        return rttEstimator.getBasePtoDuration().toMillis() +
                (quicTLSEngine.getCurrentSendKeySpace() == KeySpace.ONE_RTT ?
                        PacketSpaceManager.ADVERTISED_MAX_ACK_DELAY : 0);
    }

    public void runAppPacketSpaceTransmitter() {
        this.packetSpaces.app.runTransmitter();
    }

    public void shutdown() {
        packetSpaces.close();
    }

    public final String logTag() {
        return logTag;
    }

    /* ========================================================
     *    Direct Byte Buffer Pool
     * ======================================================== */

    // Maximum size of the connection's Direct ByteBuffer Pool.
    // For a connection configured to attempt sending datagrams in thread
    // (QuicEndpoint.SEND_DGRAM_ASYNC == false), 2 should be enough, as we
    // shouldn't have more than 2 packet number spaces active at the same time.
    private static final int MAX_DBB_POOL_SIZE = 3;
    // The ByteBuffer pool, which contains available byte buffers
    private final ConcurrentLinkedQueue<ByteBuffer> bbPool = new ConcurrentLinkedQueue<>();
    // The number of Direct Byte Buffers allocated for sending and managed by the pool.
    // This is the number of Direct Byte Buffers currently in flight, plus the number
    // of available byte buffers present in the pool. It will never exceed
    // MAX_DBB_POOL_SIZE.
    private final AtomicInteger bbAllocated = new AtomicInteger();

    // Some counters used for printing debug statistics when Log quic:dbb is enabled
    // Byte Buffers in flight: the number of byte buffers that were returned by
    // getOutgoingByteBuffer() minus the number of byte buffers that were released
    // through datagramReleased()
    private final AtomicInteger bbInFlight = new AtomicInteger();
    // Peak number of byte buffers in flight. Never decreases.
    private final AtomicInteger bbPeak = new AtomicInteger();
    // Number of unreleased byte buffers. This should eventually reach 0.
    final AtomicInteger bbUnreleased = new AtomicInteger();

    /**
     * {@return a new {@code ByteBuffer} to encode and encrypt packets in a datagram}
     * This method may either allocate a new heap BteBuffer or return a (possibly
     * new) Direct ByteBuffer from the connection's Direct Byte Buffer Pool.
     * @param size the maximum size of the datagram
     */
    protected ByteBuffer getOutgoingByteBuffer(int size) {
        bbUnreleased.incrementAndGet();
        if (USE_DIRECT_BUFFER_POOL) {
            if (size <= getMaxDatagramSize()) {
                ByteBuffer buffer = bbPool.poll();
                if (buffer != null) {
                    if (buffer.limit() >= getMaxDatagramSize()) {
                        if (Log.quicDBB()) {
                            Log.logQuic("[" + Thread.currentThread().getName() + "] "
                                    + logTag() + ": DIRECTBB: got direct buffer from pool"
                                    + ", inFlight: " + bbInFlight.get() + ", peak: " + bbPeak.get()
                                    + ", unreleased:" + bbUnreleased.get());
                        }
                        int inFlight = bbInFlight.incrementAndGet();
                        if (inFlight > bbPeak.get()) {
                            synchronized (this) {
                                if (inFlight > bbPeak.get()) bbPeak.set(inFlight);
                            }
                        }
                        return buffer;
                    }
                    bbAllocated.decrementAndGet();
                    if (Log.quicDBB()) {
                        Log.logQuic("[" + Thread.currentThread().getName() + "] "
                                + logTag() + ": DIRECTBB: releasing direct buffer");
                    }
                    buffer = null;
                }

                assert buffer == null;
                int allocated;
                while ((allocated = bbAllocated.get()) < MAX_DBB_POOL_SIZE) {
                    if (bbAllocated.compareAndSet(allocated, allocated + 1)) {
                        if (Log.quicDBB()) {
                            Log.logQuic("[" + Thread.currentThread().getName() + "] "
                                    + logTag() + ": DIRECTBB: allocating direct buffer #" + (allocated + 1)
                                    + ", inFlight: " + bbInFlight.get() + ", peak: "
                                    + bbPeak.get() + ", unreleased:" + bbUnreleased.get());
                        }
                        int inFlight = bbInFlight.incrementAndGet();
                        if (inFlight > bbPeak.get()) {
                            synchronized (this) {
                                if (inFlight > bbPeak.get()) bbPeak.set(inFlight);
                            }
                        }
                        return ByteBuffer.allocateDirect(getMaxDatagramSize());
                    }
                }
                if (Log.quicDBB()) {
                    Log.logQuic("[" + Thread.currentThread().getName() + "] "
                            + logTag() + ": DIRECTBB: too many buffers allocated: " + allocated
                            + ", inFlight: " + bbInFlight.get() + ", peak: "
                            + bbPeak.get() + ", unreleased:" + bbUnreleased.get());
                }

            } else {
                if (Log.quicDBB()) {
                    Log.logQuic("[" + Thread.currentThread().getName() + "] "
                            + logTag() + ": DIRECTBB: wrong size " + size);
                }
            }
        }
        int inFlight = bbInFlight.incrementAndGet();
        if (inFlight > bbPeak.get()) {
            synchronized (this) {
                if (inFlight > bbPeak.get()) bbPeak.set(inFlight);
            }
        }
        return ByteBuffer.allocate(size);
    }
    @Override
    public void datagramSent(QuicDatagram datagram) {
        datagramReleased(datagram);
    }

    @Override
    public void datagramDiscarded(QuicDatagram datagram) {
        if (Log.quicDBB()) {
            Log.logQuic("[" + Thread.currentThread().getName() + "] "
                    + logTag() + ": DIRECTBB: datagram discarded " + datagram.payload().isDirect()
                    + ", inFlight: " + bbInFlight.get() + ", peak: " + bbPeak.get()
                    + ", unreleased:" + bbUnreleased.get());
        }
        datagramReleased(datagram);
    }

    public void datagramDropped(QuicDatagram datagram) {
        if (Log.quicDBB()) {
            Log.logQuic("[" + Thread.currentThread().getName() + "] "
                    + logTag() + ": DIRECTBB: datagram dropped " + datagram.payload().isDirect()
                    + ", inFlight: " + bbInFlight.get() + ", peak: " + bbPeak.get()
                    + ", unreleased:" + bbUnreleased.get());
        }
        datagramReleased(datagram);
    }

    /**
     * Returns a {@link jdk.internal.net.http.quic.QuicEndpoint.Datagram} which contains
     * an encrypted QUIC packet containing
     * a {@linkplain ConnectionCloseFrame CONNECTION_CLOSE frame}. The CONNECTION_CLOSE
     * frame will have a frame type of {@code 0x1c} and error code of {@code NO_ERROR}.
     * <p>
     * This method should only be invoked when the {@link QuicEndpoint} is being closed
     * and the endpoint wants to send out a {@code CONNECTION_CLOSE} frame on a best-effort
     * basis (in a fire and forget manner).
     *
     * @return  the datagram containing the QUIC packet with a CONNECTION_CLOSE frame or
     *          an {@linkplain Optional#empty() empty Optional} if the datagram couldn't
     *          be constructed.
     */
    final Optional<QuicEndpoint.Datagram> connectionCloseDatagram() {
        try {
            final ByteBuffer quicPktPayload = this.terminator.makeConnectionCloseDatagram();
            return Optional.of(new QuicDatagram(this, peerAddress, quicPktPayload));
        } catch (Exception e) {
            // ignore any exception because providing the connection close datagram
            // when the endpoint is being closed, is on best-effort basis
            return Optional.empty();
        }
    }

    /**
     * Called when a datagram is being released, either from
     * {@link #datagramSent(QuicDatagram)}, {@link #datagramDiscarded(QuicDatagram)},
     * or {@link #datagramDropped(QuicDatagram)}.
     * This method may either release the datagram and let it get garbage collected,
     * or return it to the pool.
     * @param datagram the released datagram
     */
    protected void datagramReleased(QuicDatagram datagram) {
        bbUnreleased.decrementAndGet();
        if (Log.quicDBB()) {
            Log.logQuic("[" + Thread.currentThread().getName() + "] "
                    + logTag() + ": DIRECTBB: datagram released " + datagram.payload().isDirect()
                    + ", inFlight: " + bbInFlight.get() + ", peak: " + bbPeak.get()
                    + ", unreleased:" + bbUnreleased.get());
        }
        bbInFlight.decrementAndGet();
        if (USE_DIRECT_BUFFER_POOL) {
            ByteBuffer buffer = datagram.payload();
            buffer.clear();
            if (buffer.isDirect()) {
                if (buffer.limit() >= getMaxDatagramSize()) {
                    if (Log.quicDBB()) {
                        Log.logQuic("[" + Thread.currentThread().getName() + "] "
                                + logTag() + ": DIRECTBB: offering buffer to pool");
                    }
                    bbPool.offer(buffer);
                } else {
                    if (Log.quicDBB()) {
                        Log.logQuic("[" + Thread.currentThread().getName() + "] "
                                + logTag() + ": DIRECTBB: releasing direct buffer (too small)");
                    }
                    bbAllocated.decrementAndGet();
                }
            }
        }
    }

    public String loggableState() {
        // for HTTP3 debugging
        // If the connection was active (open bidi streams), log connection state
        if (streams.quicStreams().noneMatch(QuicStream::isBidirectional)) {
            // no active requests
            return "No active requests";
        }
        Deadline now = TimeSource.now();
        StringBuilder result = new StringBuilder("sending: {canSend:" + oneRttSndQueue.canSend() +
                ", credit: " + oneRttSndQueue.credit() +
                ", sendersReady: " + streams.hasAvailableData() +
                ", hasControlFrames: " + streams.hasControlFrames() +
                "}, cc: { backoff: " + rttEstimator.getPtoBackoff() +
                ", duration: " + ((PacketSpaceManager) packetSpaces.app).getPtoDuration() +
                ", current deadline: " + Utils.debugDeadline(now,
                ((PacketSpaceManager) packetSpaces.app).deadline()) +
                ", prospective deadline: " + Utils.debugDeadline(now,
                ((PacketSpaceManager) packetSpaces.app).prospectiveDeadline()) +
                "}, streams: [");
        streams.quicStreams().filter(QuicStream::isBidirectional).forEach(
                s -> {
                    QuicBidiStreamImpl qb = (QuicBidiStreamImpl) s;
                    result.append("{id:" + s.streamId() +
                            ", available: " + qb.senderPart().available() +
                            ", blocked: " + qb.senderPart().isBlocked() + "},"
                    );
                }
        );
        result.append("]");
        return result.toString();
    }

    /**
     * {@return true if the packet contains a CONNECTION_CLOSE frame, false otherwise}
     * @param packet the QUIC packet
     */
    private static boolean containsConnectionClose(final QuicPacket packet) {
        for (final QuicFrame frame : packet.frames()) {
            if (frame instanceof ConnectionCloseFrame) {
                return true;
            }
        }
        return false;
    }
}
