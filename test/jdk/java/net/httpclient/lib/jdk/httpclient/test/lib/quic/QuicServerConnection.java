/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.net.ssl.SSLParameters;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.QuicEndpoint;
import jdk.internal.net.http.quic.QuicTransportParameters.VersionInformation;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.QuicTransportParameters;
import jdk.internal.net.http.quic.QuicTransportParameters.ParameterId;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.frames.HandshakeDoneFrame;
import jdk.internal.net.http.quic.frames.NewTokenFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.packets.InitialPacket;
import jdk.internal.net.http.quic.packets.OneRttPacket;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketNumberSpace;
import jdk.internal.net.http.quic.packets.QuicPacket.PacketType;
import jdk.internal.net.http.quic.packets.QuicPacketDecoder;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTLSEngine.HandshakeState;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicVersion;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_data;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_local;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_remote;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_uni;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_streams_bidi;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_streams_uni;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_source_connection_id;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.max_idle_timeout;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.original_destination_connection_id;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.retry_source_connection_id;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.stateless_reset_token;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.version_information;
import static jdk.internal.net.quic.QuicTransportErrors.PROTOCOL_VIOLATION;

public final class QuicServerConnection extends QuicConnectionImpl {
    private static final AtomicLong CONNECTIONS = new AtomicLong();
    private final QuicVersion preferredQuicVersion;
    private volatile boolean connectionIdAcknowledged;
    private final QuicServer server;
    private final byte[] clientInitialToken;
    private final QuicConnectionId clientSentDestConnId;
    private final QuicConnectionId originalServerConnId;
    private final QuicServer.RetryData retryData;
    private final AtomicBoolean firstHandshakePktProcessed = new AtomicBoolean();

    public static final boolean FILTER_SENDER_ADDRESS = Utils.getBooleanProperty(
            "test.quic.server.filterSenderAddress", true);

    QuicServerConnection(QuicServer server,
                         QuicVersion quicVersion,
                         QuicVersion preferredQuicVersion,
                         InetSocketAddress peerAddress,
                         QuicConnectionId clientSentDestConnId,
                         SSLParameters sslParameters,
                         byte[] initialToken) {
        this(server, quicVersion, preferredQuicVersion, peerAddress, clientSentDestConnId,
                sslParameters, initialToken, null);

    }

    QuicServerConnection(QuicServer server,
                         QuicVersion quicVersion,
                         QuicVersion preferredQuicVersion,
                         InetSocketAddress peerAddress,
                         QuicConnectionId clientSentDestConnId,
                         SSLParameters sslParameters,
                         byte[] initialToken,
                         QuicServer.RetryData retryData) {
        super(quicVersion, server, peerAddress, null, -1, sslParameters,
                "QuicServerConnection(%s)", CONNECTIONS.incrementAndGet());
        this.preferredQuicVersion = preferredQuicVersion;
        // this should have been first statement in this constructor but compiler doesn't allow it
        Objects.requireNonNull(quicVersion, "quic version");
        this.clientInitialToken = initialToken;
        this.server = server;
        this.clientSentDestConnId = clientSentDestConnId;
        this.retryData = retryData;
        this.originalServerConnId = retryData == null ? clientSentDestConnId : retryData.originalServerConnId();
        handshakeFlow().handshakeCF().thenAccept((hs) -> {
            try {
                onHandshakeCompletion(hs);
            } catch (Exception e) {
                // TODO: consider if this needs to be propagated somehow. for now just log
                System.err.println("onHandshakeCompletion() failed: " + e);
                e.printStackTrace();
            }
        });
        assert quicVersion == quicVersion() : "unexpected quic version on" +
                " server connection, expected " + quicVersion + " but found " + quicVersion();
        getTLSEngine().deriveInitialKeys(quicVersion, clientSentDestConnId.asReadOnlyBuffer());
    }

    @Override
    protected QuicConnectionId originalServerConnId() {
        return this.originalServerConnId;
    }

    @Override
    protected boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
        return Arrays.equals(clientInitialToken, token);
    }

    @Override
    public List<QuicConnectionId> connectionIds() {
        var connectionIds = super.connectionIds();
        // we can stop using the original connection id if we have
        // received the ClientHello fully.
        // TODO: find when/where to switch connectionIdAcknowledged to true
        // TODO: what if the ClientHello is in 3 initial packets and the packet number 2
        //       gets lost? How do we know? I guess we can assume that the client hello
        //       was fully receive when we send (or receive) the first handshake packet.
        if (!connectionIdAcknowledged) {
            // Add client's initial connection ID (original or retry)
            QuicConnectionId initial = this.clientSentDestConnId;
            connectionIds = Stream.concat(connectionIds.stream(), Stream.of(initial)).toList();
        }
        return connectionIds;
    }

    @Override
    public Optional<QuicConnectionId> initialConnectionId() {
        return Optional.ofNullable(clientSentDestConnId);
    }

    @Override
    public void processIncoming(final SocketAddress source, final ByteBuffer destConnId,
                                final QuicPacket.HeadersType headersType, final ByteBuffer buffer) {
        // consult the delivery policy if this packet should be dropped
        if (this.server.incomingDeliveryPolicy().shouldDrop(source, buffer, this, headersType)) {
            silentIgnorePacket(source, buffer, headersType, false, "incoming delivery policy");
            return;
        }
        if (!connectionIdAcknowledged && localConnectionId().matches(destConnId)) {
            debug.log("connection acknowledged");
            connectionIdAcknowledged = true;
            server.connectionAcknowledged(this, clientSentDestConnId, localConnectionId());
            endpoint.removeConnectionId(clientSentDestConnId, this);
        }
        super.processIncoming(source, destConnId, headersType, buffer);
    }

    @Override
    public boolean accepts(SocketAddress source) {
        if (FILTER_SENDER_ADDRESS && !source.equals(peerAddress())) {
            // We do not support path migration yet, so we only accept
            // packets from the endpoint to which we send them.
            if (debug.on()) {
                debug.log("unexpected sender %s, skipping packet", source);
            }
            return false;
        }
        return true;
    }

    @Override
    protected void processRetryPacket(final QuicPacket quicPacket) {
        // server is not supposed to receive retry packet:
        // ignore it?
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.RETRY) {
            throw new IllegalArgumentException("Not a RETRY packet: " + quicPacket.packetType());
        }
        if (Log.errors()) {
            Log.logError("Server received RETRY packet - discarding it");
        }
    }

    @Override
    protected void incoming1RTTFrame(HandshakeDoneFrame frame) throws QuicTransportException {
        // RFC-9000, section 19.20: A HANDSHAKE_DONE frame can only be sent by
        // the server. ... A server MUST treat receipt of a HANDSHAKE_DONE frame
        // as a connection error of type PROTOCOL_VIOLATION
        throw new QuicTransportException("HANDSHAKE_DONE frame isn't allowed from clients",
                null,
                frame.getTypeField(), PROTOCOL_VIOLATION);
    }

    @Override
    protected void incoming1RTTFrame(NewTokenFrame frame) throws QuicTransportException {
        // This is a server connection and as per RFC-9000, section 19.7, clients
        // aren't supposed to send NEW_TOKEN frames and if a server receives such
        // a frame then it is considered a connection error
        // of type PROTOCOL_VIOLATION.
        throw new QuicTransportException("NEW_TOKEN frame isn't allowed from clients",
                null,
                frame.getTypeField(), PROTOCOL_VIOLATION);
    }

    @Override
    protected void pushDatagram(final SocketAddress destination, final ByteBuffer datagram) {
        final QuicPacket.HeadersType headersType = QuicPacketDecoder.peekHeaderType(datagram,
                datagram.position());
        // consult the delivery policy if this packet should be dropped
        if (this.server.outgoingDeliveryPolicy().shouldDrop(destination, datagram,
                this, headersType)) {
            silentIgnorePacket(destination, datagram, headersType, true, "outgoing delivery policy");
            return;
        }
        super.pushDatagram(destination, datagram);
    }

    @Override
    protected void processInitialPacket(final QuicPacket quicPacket) {
        try {
            if (!(quicPacket instanceof InitialPacket initialPacket)) {
                throw new AssertionError("Bad packet type: " + quicPacket);
            }
            updatePeerConnectionId(initialPacket);
            var initialPayloadLength = initialPacket.payloadSize();
            assert initialPayloadLength < Integer.MAX_VALUE;
            if (debug.on()) {
                debug.log("Initial payload (count=%d, remaining=%d)",
                        initialPacket.frames().size(), initialPayloadLength);
            }
            long total = processInitialPacketPayload(initialPacket);
            assert total == initialPayloadLength;
            if (initialPacket.frames().stream().anyMatch(f -> f instanceof CryptoFrame)) {
                debug.log("ClientHello received");
            }
            var hsState = getTLSEngine().getHandshakeState();
            debug.log("hsState: " + hsState);
            if (hsState == QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO) {
                debug.log("Continuing handshake");
                continueHandshake();
            } else if (quicPacket.isAckEliciting() &&
                    getTLSEngine().getCurrentSendKeySpace() == QuicTLSEngine.KeySpace.HANDSHAKE) {
                packetNumberSpaces().initial().fastRetransmit();
            }
        } catch (Throwable t) {
            debug.log("Unexpected exception handling initial packet", t);
            connectionTerminator().terminate(TerminationCause.forException(t));
        }
    }

    @Override
    protected void processHandshakePacket(final QuicPacket quicPacket) {
        super.processHandshakePacket(quicPacket);
        if (this.firstHandshakePktProcessed.compareAndSet(false, true)) {
            // close INITIAL packet space and discard INITIAL keys as expected by
            // RFC-9001, section 4.9.1: ... a server MUST discard Initial keys when
            // it first successfully processes a Handshake packet. Endpoints MUST NOT send
            // Initial packets after this point.
            if (debug.on()) {
                debug.log("server processed first handshake packet, initiating close of" +
                        " INITIAL packet space");
            }
            packetNumberSpaces().initial().close();
        }
        QuicTLSEngine engine = getTLSEngine();
        switch (engine.getHandshakeState()) {
            case NEED_SEND_HANDSHAKE_DONE -> {
                // should ack handshake and possibly send HandshakeDoneFrame
                // the HANDSHAKE space will be closed after sending the
                // HANDSHAKE_DONE frame (see sendStreamData)
                packetSpace(PacketNumberSpace.HANDSHAKE).runTransmitter();
                engine.tryMarkHandshakeDone();
                enqueue1RTTFrame(new HandshakeDoneFrame());
                debug.log("Adding HandshakeDoneFrame");
                completeHandshakeCF();
                packetSpace(PacketNumberSpace.APPLICATION).runTransmitter();
            }
        }
    }

    @Override
    protected void completeHandshakeCF() {
        completeHandshakeCF(null);
    }

    @Override
    protected void send1RTTPacket(final OneRttPacket packet)
            throws QuicKeyUnavailableException, QuicTransportException {
        boolean closeHandshake = false;
        var handshakeSpace = packetNumberSpaces().handshake();
        if (!handshakeSpace.isClosed()) {
            closeHandshake = packet.frames()
                    .stream()
                    .anyMatch(HandshakeDoneFrame.class::isInstance);
        }
        super.send1RTTPacket(packet);
        if (closeHandshake) {
            // close handshake space after sending
            // HANDSHAKE_DONE
            handshakeSpace.close();
        }
    }

    /**
     * This method can be invoked if a certain {@code action} needs to be performed,
     * by this server connection, on a successful completion of Quic connection handshake
     * (initiated by a client).
     *
     * @param action The action to be performed on successful completion of the handshake
     */
    public void onSuccessfulHandshake(final Runnable action) {
        this.handshakeFlow().handshakeCF().thenRun(action);
    }

    /**
     * This method can be invoked if a certain {@code action} needs to be performed,
     * by this server connection, when a Quic connection handshake (initiated by a client), completes.
     * The handshake could either have succeeded or failed. If the handshake succeeded, then the
     * {@code Throwable} passed to the {@code action} will be {@code null}, else it will represent
     * the handshake failure.
     *
     * @param action The action to be performed on completion of the handshake
     */
    public void onHandshakeCompletion(final Consumer<Throwable> action) {
        this.handshakeFlow().handshakeCF().handle((unused, failure) -> {
            action.accept(failure);
            return null;
        });
    }


    @Override
    public QuicServer quicInstance() {
        return server;
    }

    @Override
    protected long getMaxIdleTimeoutTransportParam() {
        return this.transportParams.getIntParameter(
                ParameterId.max_idle_timeout, TimeUnit.SECONDS.toMillis(
                        Utils.getLongProperty("jdk.test.server.quic.idleTimeout", 30)));
    }

    @Override
    protected ByteBuffer buildInitialParameters() {
        final QuicTransportParameters params = new QuicTransportParameters(this.transportParams);
        if (!params.isPresent(original_destination_connection_id)) {
            params.setParameter(original_destination_connection_id, originalServerConnId.getBytes());
        }
        if (!params.isPresent(initial_source_connection_id)) {
            params.setParameter(initial_source_connection_id, localConnectionId().getBytes());
        }
        if (!params.isPresent(stateless_reset_token)) {
            params.setParameter(stateless_reset_token,
                    endpoint.idFactory().statelessTokenFor(localConnectionId()));
        }
        if (retryData != null && !params.isPresent(retry_source_connection_id)) {
            // include the connection id that was directed by this server's RETRY packet
            // for usage in INITIAL packets sent by client
            params.setParameter(retry_source_connection_id,
                    retryData.serverChosenConnId().getBytes());
        }
        setIntParamIfNotSet(params, max_idle_timeout, this::getMaxIdleTimeoutTransportParam);
        setIntParamIfNotSet(params, initial_max_data, () -> DEFAULT_INITIAL_MAX_DATA);
        setIntParamIfNotSet(params, initial_max_stream_data_bidi_local, () -> DEFAULT_INITIAL_STREAM_MAX_DATA);
        setIntParamIfNotSet(params, initial_max_stream_data_bidi_remote, () -> DEFAULT_INITIAL_STREAM_MAX_DATA);
        setIntParamIfNotSet(params, initial_max_stream_data_uni, () -> DEFAULT_INITIAL_STREAM_MAX_DATA);
        setIntParamIfNotSet(params, initial_max_streams_bidi, () -> (long) DEFAULT_MAX_BIDI_STREAMS);
        setIntParamIfNotSet(params, initial_max_streams_uni, () -> (long) DEFAULT_MAX_UNI_STREAMS);
        // params.setParameter(QuicTransportParameters.ParameterId.stateless_reset_token, ...); // no token
        // params.setIntParameter(QuicTransportParameters.ParameterId.ack_delay_exponent, 3); // unit 2^3 microseconds
        // params.setIntParameter(QuicTransportParameters.ParameterId.max_ack_delay, 25); //25 millis
        // params.setBooleanParameter(QuicTransportParameters.ParameterId.disable_active_migration, false);
        // params.setPreferedAddressParameter(QuicTransportParameters.ParameterId.preferred_address, ...);
        // params.setIntParameter(QuicTransportParameters.ParameterId.active_connection_id_limit, 2);
        if (!params.isPresent(version_information)) {
            final VersionInformation vi = QuicTransportParameters.buildVersionInformation(
                    quicVersion(), quicInstance().getAvailableVersions());
            params.setVersionInformationParameter(version_information, vi);
        }
        final byte[] unsupportedTransportParam = encodeRandomUnsupportedTransportParameter();
        final int capacity = params.size() + unsupportedTransportParam.length;
        final ByteBuffer buf = ByteBuffer.allocate(capacity);
        params.encode(buf);
        // add an unsupported transport param id so that we can exercise the case where endpoints
        // are expected to ignore unsupported transport parameters (RFC-9000, section 7.4.2)
        buf.put(unsupportedTransportParam);
        buf.flip();
        newLocalTransportParameters(params);
        return buf;
    }

    // returns the encoded representation of a random unsupported transport parameter
    private static byte[] encodeRandomUnsupportedTransportParameter() {
        final int n = new Random().nextInt(1, 100);
        final long unsupportedParamId = 31 * n + 27; // RFC-9000, section 18.1
        final int value = 42;
        //        Transport Parameter {
        //            Transport Parameter ID (i),
        //            Transport Parameter Length (i),
        //            Transport Parameter Value (..),
        //        }
        int size = 0;
        size += VariableLengthEncoder.getEncodedSize(unsupportedParamId);
        final int paramLength = VariableLengthEncoder.getEncodedSize(value);
        size += VariableLengthEncoder.getEncodedSize(paramLength);
        size += paramLength;
        final byte[] encoded = new byte[size];
        final ByteBuffer buf = ByteBuffer.wrap(encoded);

        VariableLengthEncoder.encode(buf, unsupportedParamId); // write out the id, as a variable length integer
        VariableLengthEncoder.encode(buf, paramLength); // write out the len, as a variable length integer
        VariableLengthEncoder.encode(buf, value); // write out the actual value
        return encoded;
    }

    @Override
    protected void consumeQuicParameters(ByteBuffer byteBuffer)
            throws QuicTransportException {
        final QuicTransportParameters params = QuicTransportParameters.decode(byteBuffer);
        if (debug.on()) {
            debug.log("Received (from client) Quic transport params: " + params.toStringWithValues());
        }
        if (params.isPresent(retry_source_connection_id)) {
            throw new QuicTransportException("Retry connection ID not expected here",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (params.isPresent(original_destination_connection_id)) {
            throw new QuicTransportException("Original connection ID not expected here",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (params.isPresent(stateless_reset_token)) {
            throw new QuicTransportException("Reset token not expected here",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (params.isPresent(ParameterId.preferred_address)) {
            throw new QuicTransportException("Preferred address not expected here",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (!params.matches(initial_source_connection_id,
                getIncomingInitialPacketSourceId())) {
            throw new QuicTransportException("Peer connection ID does not match",
                    null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        VersionInformation vi =
                params.getVersionInformationParameter(version_information);
        if (vi != null) {
            boolean found = false;
            for (int v: vi.availableVersions()) {
                if (v == vi.chosenVersion()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new QuicTransportException(
                        "[version_information] Chosen Version not in Available Versions",
                        null, 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
            if (vi.chosenVersion() != quicVersion().versionNumber()) {
                throw new QuicTransportException(
                        "[version_information] Chosen Version %s does not match version in use %s"
                                .formatted(vi.chosenVersion(), quicVersion().versionNumber()),
                        null, 0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
            }
            assert Arrays.stream(vi.availableVersions()).anyMatch(v -> v == preferredQuicVersion.versionNumber());
            if (preferredQuicVersion != quicVersion()) {
                if (!switchVersion(preferredQuicVersion)) {
                    throw new QuicTransportException("Switching version failed",
                            null, 0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
                }
            }
        } else {
            assert preferredQuicVersion == quicVersion();
        }
        markVersionNegotiated(preferredQuicVersion.versionNumber());
        handleIncomingPeerTransportParams(params);

        // build our parameters
        final ByteBuffer quicInitialParameters = buildInitialParameters();
        getTLSEngine().setLocalQuicTransportParameters(quicInitialParameters);
        // params.setIntParameter(QuicTransportParameters.ParameterId.initial_max_data, DEFAULT_INITIAL_MAX_DATA);
        // params.setIntParameter(QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_local, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.setIntParameter(QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_remote, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.setIntParameter(QuicTransportParameters.ParameterId.initial_max_stream_data_uni, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.setIntParameter(QuicTransportParameters.ParameterId.initial_max_streams_bidi, DEFAULT_MAX_STREAMS);
        // params.setIntParameter(QuicTransportParameters.ParameterId.initial_max_streams_uni, DEFAULT_MAX_STREAMS);
        // params.setIntParameter(QuicTransportParameters.ParameterId.ack_delay_exponent, 3); // unit 2^3 microseconds
        // params.setIntParameter(QuicTransportParameters.ParameterId.max_ack_delay, 25); //25 millis
        // params.setBooleanParameter(QuicTransportParameters.ParameterId.disable_active_migration, false);
        // params.setIntParameter(QuicTransportParameters.ParameterId.active_connection_id_limit, 2);
    }

    @Override
    protected void processVersionNegotiationPacket(final QuicPacket quicPacket) {
        // ignore the packet: the server doesn't reply to version negotiation.
        debug.log("Server ignores version negotiation packet: " + quicPacket);
    }

    @Override
    public boolean isClientConnection() {
        return false;
    }

    @Override
    protected QuicEndpoint onHandshakeCompletion(HandshakeState result) {
        super.onHandshakeCompletion(result);
        // send a new token frame to the client, for use in new connection attempts.
        sendNewToken();
        return this.endpoint;
    }

    private void sendNewToken() {
        final byte[] token = server.buildNewToken();
        final QuicFrame newTokenFrame = new NewTokenFrame(ByteBuffer.wrap(token));
        enqueue1RTTFrame(newTokenFrame);
    }

    @Override
    public long nextMaxStreamsLimit(final boolean bidi) {
        final Function<Boolean, Long> limitComputer = this.server.getMaxStreamLimitComputer();
        if (limitComputer != null) {
            return limitComputer.apply(bidi);
        }
        return super.nextMaxStreamsLimit(bidi);
    }

    private void silentIgnorePacket(final SocketAddress source, final ByteBuffer payload,
                                    final QuicPacket.HeadersType headersType,
                                    final boolean outgoing,
                                    final String reason) {
        if (debug.on()) {
            debug.log("silently dropping %s packet %s %s, reason: %s", headersType,
                    (outgoing ? "to dest" : "from source"), source, reason);
        }
    }
}
