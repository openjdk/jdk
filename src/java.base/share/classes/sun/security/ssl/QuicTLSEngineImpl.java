/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.ssl;

import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicOneRttContext;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicTransportParametersConsumer;
import jdk.internal.net.quic.QuicVersion;
import sun.security.ssl.QuicKeyManager.HandshakeKeyManager;
import sun.security.ssl.QuicKeyManager.InitialKeyManager;
import sun.security.ssl.QuicKeyManager.OneRttKeyManager;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import static jdk.internal.net.quic.QuicTLSEngine.HandshakeState.*;
import static jdk.internal.net.quic.QuicTLSEngine.KeySpace.*;

/**
 * One instance per QUIC connection. Configuration methods similar to
 * SSLEngine.
 * <p>
 * The implementation of this class uses the {@link QuicKeyManager} to maintain
 * all state relating to keys for each encryption levels.
 */
public final class QuicTLSEngineImpl implements QuicTLSEngine, SSLTransport {

    private static final Map<Byte, KeySpace> messageTypeMap =
            Map.of(SSLHandshake.CLIENT_HELLO.id, INITIAL,
                    SSLHandshake.SERVER_HELLO.id, INITIAL,
                    SSLHandshake.ENCRYPTED_EXTENSIONS.id, HANDSHAKE,
                    SSLHandshake.CERTIFICATE_REQUEST.id, HANDSHAKE,
                    SSLHandshake.CERTIFICATE.id, HANDSHAKE,
                    SSLHandshake.CERTIFICATE_VERIFY.id, HANDSHAKE,
                    SSLHandshake.FINISHED.id, HANDSHAKE,
                    SSLHandshake.NEW_SESSION_TICKET.id, ONE_RTT);
    static final long BASE_CRYPTO_ERROR = 256;

    private static final Set<QuicVersion> SUPPORTED_QUIC_VERSIONS =
            Set.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2);

    // VarHandles are used to access compareAndSet semantics.
    private static final VarHandle HANDSHAKE_STATE_HANDLE;
    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            final Class<?> quicTlsEngineImpl = QuicTLSEngineImpl.class;
            HANDSHAKE_STATE_HANDLE = lookup.findVarHandle(
                    quicTlsEngineImpl,
                    "handshakeState",
                    HandshakeState.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final TransportContext conContext;
    private final String peerHost;
    private final int peerPort;
    private volatile HandshakeState handshakeState;
    private volatile KeySpace sendKeySpace;
    // next message to send or receive
    private volatile ByteBuffer localQuicTransportParameters;
    private volatile QuicTransportParametersConsumer
            remoteQuicTransportParametersConsumer;

    // keymanagers for individual keyspaces
    private final InitialKeyManager initialKeyManager = new InitialKeyManager();
    private final HandshakeKeyManager handshakeKeyManager =
            new HandshakeKeyManager();
    private final OneRttKeyManager oneRttKeyManager = new OneRttKeyManager();

    // buffer for crypto data that was received but not yet processed (i.e.
    // incomplete messages)
    private volatile ByteBuffer incomingCryptoBuffer;
    // key space for incomingCryptoBuffer
    private volatile KeySpace incomingCryptoSpace;

    private volatile QuicVersion negotiatedVersion;

    public QuicTLSEngineImpl(SSLContextImpl sslContextImpl) {
        this(sslContextImpl, null, -1);
    }

    public QuicTLSEngineImpl(SSLContextImpl sslContextImpl, final String peerHost, final int peerPort) {
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.sendKeySpace = INITIAL;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContextImpl, this,
                new SSLEngineInputRecord(handshakeHash),
                new QuicEngineOutputRecord(handshakeHash));
        conContext.sslConfig.enabledProtocols = List.of(ProtocolVersion.TLS13);
        if (peerHost != null) {
            conContext.sslConfig.serverNames =
                    Utilities.addToSNIServerNameList(
                            conContext.sslConfig.serverNames, peerHost);
        }
        conContext.setQuic(true);
    }

    @Override
    public void setUseClientMode(boolean mode) {
        conContext.setUseClientMode(mode);
        this.handshakeState = mode
                ? HandshakeState.NEED_SEND_CRYPTO
                : HandshakeState.NEED_RECV_CRYPTO;
    }

    @Override
    public boolean getUseClientMode() {
        return conContext.sslConfig.isClientMode;
    }

    @Override
    public void setSSLParameters(final SSLParameters params) {
        Objects.requireNonNull(params);
        // section, 4.2 of RFC-9001
        // Clients MUST NOT offer TLS versions older than 1.3
        final String[] protos = params.getProtocols();
        if (protos == null || protos.length == 0) {
            throw new IllegalArgumentException("No TLS protocols set");
        }
        boolean tlsv13Present = false;
        Set<String> unsupported = new HashSet<>();
        for (String p : protos) {
            if ("TLSv1.3".equals(p)) {
                tlsv13Present = true;
            } else {
                unsupported.add(p);
            }
        }
        if (!tlsv13Present) {
            throw new IllegalArgumentException(
                    "required TLSv1.3 protocol version hasn't been set");
        }
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsupported TLS protocol versions " + unsupported);
        }
        conContext.sslConfig.setSSLParameters(params);
    }

    @Override
    public SSLSession getSession() {
        return conContext.conSession;
    }

    @Override
    public SSLSession getHandshakeSession() {
        final HandshakeContext handshakeContext = conContext.handshakeContext;
        return handshakeContext == null
                ? null
                : handshakeContext.handshakeSession;
    }

    /**
     * {@return the {@link AlgorithmConstraints} that are applicable for this engine,
     * or null if none are applicable}
     */
    AlgorithmConstraints getAlgorithmConstraints() {
        final HandshakeContext handshakeContext = conContext.handshakeContext;
        // if we are handshaking then use the handshake context
        // to determine the constraints, else use the configured
        // SSLParameters
        return handshakeContext == null
                ? getSSLParameters().getAlgorithmConstraints()
                : handshakeContext.sslConfig.userSpecifiedAlgorithmConstraints;
    }

    @Override
    public SSLParameters getSSLParameters() {
        return conContext.sslConfig.getSSLParameters();
    }

    @Override
    public String getApplicationProtocol() {
        // TODO: review thread safety when dealing with conContext
        return conContext.applicationProtocol;
    }

    @Override
    public Set<QuicVersion> getSupportedQuicVersions() {
        return SUPPORTED_QUIC_VERSIONS;
    }

    @Override
    public void setOneRttContext(final QuicOneRttContext ctx) {
        this.oneRttKeyManager.setOneRttContext(ctx);
    }

    private QuicVersion getNegotiatedVersion() {
        final QuicVersion negotiated = this.negotiatedVersion;
        if (negotiated == null) {
            throw new IllegalStateException(
                    "Quic version hasn't been negotiated yet");
        }
        return negotiated;
    }

    private boolean isEnabled(final QuicVersion quicVersion) {
        final Set<QuicVersion> enabled = getSupportedQuicVersions();
        if (enabled == null) {
            return false;
        }
        return enabled.contains(quicVersion);
    }

    /**
     * Returns the current handshake state of the connection. Sometimes packets
     * that could be decrypted can be received before the handshake has
     * completed, but should not be decrypted until it is complete
     *
     * @return the HandshakeState
     */
    @Override
    public HandshakeState getHandshakeState() {
        return handshakeState;
    }

    /**
     * Returns the current sending key space (encryption level)
     *
     * @return the current sending key space
     */
    @Override
    public KeySpace getCurrentSendKeySpace() {
        return sendKeySpace;
    }

    @Override
    public boolean keysAvailable(KeySpace keySpace) {
        return switch (keySpace) {
            case INITIAL -> this.initialKeyManager.keysAvailable();
            case HANDSHAKE -> this.handshakeKeyManager.keysAvailable();
            case ONE_RTT -> this.oneRttKeyManager.keysAvailable();
            case ZERO_RTT -> false;
            case RETRY -> true;
            default -> throw new IllegalArgumentException(
                    keySpace + " not expected here");
        };
    }

    @Override
    public void discardKeys(KeySpace keySpace) {
        switch (keySpace) {
            case INITIAL -> this.initialKeyManager.discardKeys();
            case HANDSHAKE -> this.handshakeKeyManager.discardKeys();
            case ONE_RTT -> this.oneRttKeyManager.discardKeys();
            default -> throw new IllegalArgumentException(
                    "key discarding not implemented for " + keySpace);
        }
    }

    @Override
    public int getHeaderProtectionSampleSize(KeySpace keySpace) {
        return switch (keySpace) {
            case INITIAL, HANDSHAKE, ZERO_RTT, ONE_RTT -> 16;
            default -> throw new IllegalArgumentException(
                    "Type '" + keySpace + "' not expected here");
        };
    }

    @Override
    public ByteBuffer computeHeaderProtectionMask(KeySpace keySpace,
                                                  boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        final QuicKeyManager keyManager = keyManager(keySpace);
        if (incoming) {
            final QuicCipher.QuicReadCipher quicCipher =
                    keyManager.getReadCipher();
            return quicCipher.computeHeaderProtectionMask(sample);
        } else {
            final QuicCipher.QuicWriteCipher quicCipher =
                    keyManager.getWriteCipher();
            return quicCipher.computeHeaderProtectionMask(sample);
        }
    }

    @Override
    public int getAuthTagSize() {
        // RFC-9001, section 5.3
        // QUIC can use any of the cipher suites defined in [TLS13] with the
        // exception of TLS_AES_128_CCM_8_SHA256. ...
        // These cipher suites have a 16-byte authentication tag and produce
        // an output 16 bytes larger than their input.
        return 16;
    }

    @Override
    public void encryptPacket(final KeySpace keySpace, final long packetNumber,
                              final IntFunction<ByteBuffer> headerGenerator,
                              final ByteBuffer packetPayload, final ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException, ShortBufferException {
        final QuicKeyManager keyManager = keyManager(keySpace);
        keyManager.encryptPacket(packetNumber, headerGenerator, packetPayload, output);
    }

    @Override
    public void decryptPacket(final KeySpace keySpace,
            final long packetNumber, final int keyPhase,
            final ByteBuffer packet, final int headerLength,
            final ByteBuffer output)
            throws QuicKeyUnavailableException, AEADBadTagException,
            QuicTransportException, ShortBufferException {
        if (keySpace == ONE_RTT && !isTLSHandshakeComplete()) {
            // RFC-9001, section 5.7 specifies that the server or the client MUST NOT
            // decrypt 1-RTT packets, even if 1-RTT keys are available, before the
            // TLS handshake is complete.
            throw new QuicKeyUnavailableException("QUIC TLS handshake not yet complete", ONE_RTT);
        }
        final QuicKeyManager keyManager = keyManager(keySpace);
        keyManager.decryptPacket(packetNumber, keyPhase, packet, headerLength,
                output);
    }

    @Override
    public void signRetryPacket(final QuicVersion quicVersion,
            final ByteBuffer originalConnectionId, final ByteBuffer packet,
            final ByteBuffer output)
            throws ShortBufferException, QuicTransportException {
        if (!isEnabled(quicVersion)) {
            throw new IllegalArgumentException(
                    "Quic version " + quicVersion + " isn't enabled");
        }
        int connIdLength = originalConnectionId.remaining();
        if (connIdLength >= 256 || connIdLength < 0) {
            throw new IllegalArgumentException("connection ID length");
        }
        final Cipher cipher = InitialKeyManager.getRetryCipher(
                quicVersion, false);
        cipher.updateAAD(new byte[]{(byte) connIdLength});
        cipher.updateAAD(originalConnectionId);
        cipher.updateAAD(packet);
        try {
            // No data to encrypt, just outputting the tag which will be
            // verified later.
            cipher.doFinal(ByteBuffer.allocate(0), output);
        } catch (ShortBufferException e) {
            throw e;
        } catch (Exception e) {
            throw new QuicTransportException("Failed to sign packet",
                    null, 0, BASE_CRYPTO_ERROR + Alert.INTERNAL_ERROR.id, e);
        }
    }

    @Override
    public void verifyRetryPacket(final QuicVersion quicVersion,
            final ByteBuffer originalConnectionId,
            final ByteBuffer packet)
            throws AEADBadTagException, QuicTransportException {
        if (!isEnabled(quicVersion)) {
            throw new IllegalArgumentException(
                    "Quic version " + quicVersion + " isn't enabled");
        }
        int connIdLength = originalConnectionId.remaining();
        if (connIdLength >= 256 || connIdLength < 0) {
            throw new IllegalArgumentException("connection ID length");
        }
        int originalLimit = packet.limit();
        packet.limit(originalLimit - 16);
        final Cipher cipher =
                InitialKeyManager.getRetryCipher(quicVersion, true);
        cipher.updateAAD(new byte[]{(byte) connIdLength});
        cipher.updateAAD(originalConnectionId);
        cipher.updateAAD(packet);
        packet.limit(originalLimit);
        try {
            assert packet.remaining() == 16;
            int outBufLength = cipher.getOutputSize(packet.remaining());
            // No data to decrypt, just checking the tag.
            ByteBuffer outBuffer = ByteBuffer.allocate(outBufLength);
            cipher.doFinal(packet, outBuffer);
            assert outBuffer.position() == 0;
        } catch (AEADBadTagException e) {
            throw e;
        } catch (Exception e) {
            throw new QuicTransportException("Failed to verify packet",
                    null, 0, BASE_CRYPTO_ERROR + Alert.INTERNAL_ERROR.id, e);
        }
    }

    private QuicKeyManager keyManager(final KeySpace keySpace) {
        return switch (keySpace) {
            case INITIAL -> this.initialKeyManager;
            case HANDSHAKE -> this.handshakeKeyManager;
            case ONE_RTT -> this.oneRttKeyManager;
            default -> throw new IllegalArgumentException(
                    "No key manager available for key space: " + keySpace);
        };
    }

    @Override
    public ByteBuffer getHandshakeBytes(KeySpace keySpace) throws IOException {
        if (keySpace != sendKeySpace) {
            throw new IllegalStateException("Unexpected key space: "
                    + keySpace + " (expected " + sendKeySpace + ")");
        }
        if (handshakeState == HandshakeState.NEED_SEND_CRYPTO ||
                !conContext.outputRecord.isEmpty()) { // session ticket
            byte[] bytes = produceNextHandshakeMessage();
            return ByteBuffer.wrap(bytes);
        } else {
            return null;
        }
    }

    private byte[] produceNextHandshakeMessage() throws IOException {
        if (!conContext.isNegotiated && !conContext.isBroken &&
                !conContext.isInboundClosed() &&
                !conContext.isOutboundClosed()) {
            conContext.kickstart();
        }
        byte[] message = conContext.outputRecord.getHandshakeMessage();
        if (handshakeState == NEED_SEND_CRYPTO) {
            if (conContext.outputRecord.isEmpty()) {
                if (conContext.isNegotiated) {
                    // client, done
                    handshakeState = NEED_RECV_HANDSHAKE_DONE;
                    sendKeySpace = ONE_RTT;
                } else {
                    handshakeState = NEED_RECV_CRYPTO;
                }
            } else if (sendKeySpace == INITIAL && !getUseClientMode()) {
                // Server sends handshake messages immediately after
                // the initial server hello. Need to check the next key space.
                sendKeySpace = conContext.outputRecord.getHandshakeMessageKeySpace();
            }
        } else {
            assert conContext.isNegotiated;
        }
        return message;
    }

    @Override
    public void consumeHandshakeBytes(KeySpace keySpace, ByteBuffer payload)
            throws QuicTransportException {
        if (!payload.hasRemaining()) {
            throw new IllegalArgumentException("Empty crypto buffer");
        }
        if (keySpace == KeySpace.ZERO_RTT) {
            throw new IllegalArgumentException("Crypto in zero-rtt");
        }
        if (incomingCryptoSpace != null && incomingCryptoSpace != keySpace) {
            throw new QuicTransportException("Unexpected message", null, 0,
                    BASE_CRYPTO_ERROR + Alert.UNEXPECTED_MESSAGE.id,
                    new SSLHandshakeException(
                            "Unfinished message in " + incomingCryptoSpace));
        }
        try {
            if (!conContext.isNegotiated && !conContext.isBroken &&
                    !conContext.isInboundClosed() &&
                    !conContext.isOutboundClosed()) {
                conContext.kickstart();
            }
        } catch (IOException e) {
            throw new QuicTransportException(e.toString(), null, 0,
                    BASE_CRYPTO_ERROR + Alert.INTERNAL_ERROR.id, e);
        }
        // previously unconsumed bytes in incomingCryptoBuffer, new bytes in
        // payload. if incomingCryptoBuffer is not null, it's either 4 bytes
        // or large enough to hold the entire message.
        while (payload.hasRemaining()) {
            if (keySpace != KeySpace.ONE_RTT &&
                    handshakeState != HandshakeState.NEED_RECV_CRYPTO) {
                // in one-rtt we may receive session tickets at any time;
                // during handshake we're either sending or receiving
                throw new QuicTransportException("Unexpected message", null, 0,
                        BASE_CRYPTO_ERROR + Alert.UNEXPECTED_MESSAGE.id,
                        new SSLHandshakeException(
                                "Not expecting a handshake message, state: " +
                                        handshakeState));
            }
            if (incomingCryptoBuffer != null) {
                // message type validated already; pump more bytes
                if (payload.remaining() <= incomingCryptoBuffer.remaining()) {
                    incomingCryptoBuffer.put(payload);
                } else {
                    // more than one message in buffer, or we don't have a
                    // header yet
                    int remaining = incomingCryptoBuffer.remaining();
                    incomingCryptoBuffer.put(incomingCryptoBuffer.position(),
                            payload, payload.position(), remaining);
                    incomingCryptoBuffer.position(incomingCryptoBuffer.limit());
                    payload.position(payload.position() + remaining);
                    if (incomingCryptoBuffer.capacity() == 4) {
                        // small buffer for header only; retrieve size and
                        // expand if necessary
                        int messageSize =
                                ((incomingCryptoBuffer.get(1) & 0xFF) << 16) |
                                ((incomingCryptoBuffer.get(2) & 0xFF) << 8) |
                                (incomingCryptoBuffer.get(3) & 0xFF);
                        if (messageSize != 0) {
                            if (messageSize > SSLConfiguration.maxHandshakeMessageSize) {
                                throw new QuicTransportException(
                                        "The size of the handshake message ("
                                                + messageSize
                                                + ") exceeds the maximum allowed size ("
                                                + SSLConfiguration.maxHandshakeMessageSize
                                                + ")",
                                        null, 0,
                                        QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED);
                            }
                            ByteBuffer newBuffer =
                                    ByteBuffer.allocate(messageSize + 4);
                            incomingCryptoBuffer.flip();
                            newBuffer.put(incomingCryptoBuffer);
                            incomingCryptoBuffer = newBuffer;
                            assert incomingCryptoBuffer.position() == 4 :
                                    incomingCryptoBuffer.position();
                            // start over with larger buffer
                            continue;
                        }
                        // message size was zero... can it really happen?
                    }
                }
            } else {
                // incoming crypto buffer is null. Validate message type,
                // check if size is available
                byte messageType = payload.get(payload.position());
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.fine("Received message of type 0x" +
                            Integer.toHexString(messageType & 0xFF));
                }
                KeySpace expected = messageTypeMap.get(messageType);
                if (expected != keySpace) {
                    throw new QuicTransportException("Unexpected message",
                            null, 0,
                            BASE_CRYPTO_ERROR + Alert.UNEXPECTED_MESSAGE.id,
                            new SSLHandshakeException("Message " + messageType +
                                    " received in " + keySpace +
                                    " but should be " + expected));
                }
                if (payload.remaining() < 4) {
                    // partial message, length missing. Store in
                    // incomingCryptoBuffer
                    incomingCryptoBuffer = ByteBuffer.allocate(4);
                    incomingCryptoBuffer.put(payload);
                    incomingCryptoSpace = keySpace;
                    return;
                }
                int payloadPos = payload.position();
                int messageSize = ((payload.get(payloadPos + 1) & 0xFF) << 16)
                        | ((payload.get(payloadPos + 2) & 0xFF) << 8)
                        | (payload.get(payloadPos + 3) & 0xFF);
                if (payload.remaining() < messageSize + 4) {
                    // partial message, length known. Store in
                    // incomingCryptoBuffer
                    if (messageSize > SSLConfiguration.maxHandshakeMessageSize) {
                        throw new QuicTransportException(
                                "The size of the handshake message ("
                                        + messageSize
                                        + ") exceeds the maximum allowed size ("
                                        + SSLConfiguration.maxHandshakeMessageSize
                                        + ")",
                                null, 0,
                                QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED);
                    }
                    incomingCryptoBuffer = ByteBuffer.allocate(messageSize + 4);
                    incomingCryptoBuffer.put(payload);
                    incomingCryptoSpace = keySpace;
                    return;
                }
                incomingCryptoSpace = keySpace;
                incomingCryptoBuffer = payload.slice(payloadPos,
                        messageSize + 4);
                // set position at end to indicate that the buffer is ready
                // for processing
                incomingCryptoBuffer.position(messageSize + 4);
                assert !incomingCryptoBuffer.hasRemaining() :
                        incomingCryptoBuffer.remaining();
                payload.position(payloadPos + messageSize + 4);
            }
            if (!incomingCryptoBuffer.hasRemaining()) {
                incomingCryptoBuffer.flip();
                handleHandshakeMessage(keySpace, incomingCryptoBuffer);
                incomingCryptoBuffer = null;
                incomingCryptoSpace = null;
            } else {
                assert !payload.hasRemaining() : payload.remaining();
                return;
            }
        }
    }

    private void handleHandshakeMessage(KeySpace keySpace, ByteBuffer message)
            throws QuicTransportException {
        // message param contains one whole TLS message
        boolean useClientMode = getUseClientMode();
        byte messageType = message.get();
        int messageSize = ((message.get() & 0xFF) << 16)
                | ((message.get() & 0xFF) << 8)
                | (message.get() & 0xFF);

        assert message.remaining() == messageSize :
                message.remaining() - messageSize;
        try {
            if (conContext.inputRecord.handshakeHash.isHashable(messageType)) {
                ByteBuffer temp = message.duplicate();
                temp.position(0);
                conContext.inputRecord.handshakeHash.receive(temp);
            }
            if (conContext.handshakeContext == null) {
                if (!conContext.isNegotiated) {
                    throw new QuicTransportException(
                            "Cannot process crypto message, broken: "
                                    + conContext.isBroken,
                            null, 0, QuicTransportErrors.INTERNAL_ERROR);
                }
                conContext.handshakeContext =
                        new PostHandshakeContext(conContext);
            }
            conContext.handshakeContext.dispatch(messageType, message.slice());
        } catch (SSLHandshakeException e) {
            if (e.getCause() instanceof QuicTransportException qte) {
                // rethrow quic transport parameters validation exception
                throw qte;
            }
            Alert alert = ((QuicEngineOutputRecord)
                    conContext.outputRecord).getAlert();
            throw new QuicTransportException(alert.description, keySpace, 0,
                    BASE_CRYPTO_ERROR + alert.id, e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (handshakeState == NEED_RECV_CRYPTO) {
            if (conContext.outputRecord.isEmpty()) {
                if (conContext.isNegotiated) {
                    // dead code? done, server side, no session ticket
                    handshakeState = NEED_SEND_HANDSHAKE_DONE;
                    sendKeySpace = ONE_RTT;
                } else {
                    // expect more messages
                    // client side: if we're still in INITIAL, switch
                    // to HANDSHAKE
                    if (sendKeySpace == INITIAL) {
                        sendKeySpace = HANDSHAKE;
                    }
                }
            } else {
                // our turn to send
                if (conContext.isNegotiated && !useClientMode) {
                    // done, server side, wants to send session ticket
                    handshakeState = NEED_SEND_HANDSHAKE_DONE;
                    sendKeySpace = ONE_RTT;
                } else {
                    // more messages needed to finish handshake
                    handshakeState = HandshakeState.NEED_SEND_CRYPTO;
                }
            }
        } else {
            assert conContext.isNegotiated;
        }
    }

    @Override
    public void deriveInitialKeys(final QuicVersion quicVersion,
            final ByteBuffer connectionId) throws IOException {
        if (!isEnabled(quicVersion)) {
            throw new IllegalArgumentException("Quic version " + quicVersion +
                    " isn't enabled");
        }
        final byte[] connectionIdBytes = new byte[connectionId.remaining()];
        connectionId.get(connectionIdBytes);
        this.initialKeyManager.deriveKeys(quicVersion, connectionIdBytes,
                getUseClientMode());
    }

    @Override
    public void versionNegotiated(final QuicVersion quicVersion) {
        Objects.requireNonNull(quicVersion);
        if (!isEnabled(quicVersion)) {
            throw new IllegalArgumentException("Quic version " + quicVersion +
                    " is not enabled");
        }
        synchronized (this) {
            final QuicVersion prevNegotiated = this.negotiatedVersion;
            if (prevNegotiated != null) {
                throw new IllegalStateException("A Quic version has already " +
                        "been negotiated previously");
            }
            this.negotiatedVersion = quicVersion;
        }
    }

    public void deriveHandshakeKeys() throws IOException {
        final QuicVersion quicVersion = getNegotiatedVersion();
        this.handshakeKeyManager.deriveKeys(quicVersion,
                this.conContext.handshakeContext,
                getUseClientMode());
    }

    public void deriveOneRTTKeys() throws IOException {
        final QuicVersion quicVersion = getNegotiatedVersion();
        this.oneRttKeyManager.deriveKeys(quicVersion,
                this.conContext.handshakeContext,
                getUseClientMode());
    }

    // for testing (PacketEncryptionTest)
    void deriveOneRTTKeys(final QuicVersion version,
                          final SecretKey client_application_traffic_secret_0,
                          final SecretKey server_application_traffic_secret_0,
                          final CipherSuite negotiatedCipherSuite,
                          final boolean clientMode) throws IOException,
            GeneralSecurityException {
        this.oneRttKeyManager.deriveOneRttKeys(version,
                client_application_traffic_secret_0,
                server_application_traffic_secret_0,
                negotiatedCipherSuite, clientMode);
    }

    @Override
    public Runnable getDelegatedTask() {
        // TODO: actually delegate tasks
        return null;
    }

    @Override
    public String getPeerHost() {
        return peerHost;
    }

    @Override
    public int getPeerPort() {
        return peerPort;
    }

    @Override
    public boolean useDelegatedTask() {
        return true;
    }

    public byte[] getLocalQuicTransportParameters() {
        ByteBuffer ltp = localQuicTransportParameters;
        if (ltp == null) {
            return null;
        }
        byte[] result = new byte[ltp.remaining()];
        ltp.get(0, result);
        return result;
    }

    @Override
    public void setLocalQuicTransportParameters(ByteBuffer params) {
        this.localQuicTransportParameters = params;
    }

    @Override
    public void restartHandshake() throws IOException {
        if (negotiatedVersion != null) {
            throw new IllegalStateException("Version already negotiated");
        }
        if (sendKeySpace != INITIAL || handshakeState != NEED_RECV_CRYPTO) {
            throw new IllegalStateException("Unexpected handshake state");
        }
        HandshakeContext context = conContext.handshakeContext;
        ClientHandshakeContext chc = (ClientHandshakeContext)context;

        // Refresh handshake hash
        chc.handshakeHash.finish();     // reset the handshake hash

        // Update the initial ClientHello handshake message.
        chc.initialClientHelloMsg.extensions.reproduce(chc,
                new SSLExtension[] {
                        SSLExtension.CH_QUIC_TRANSPORT_PARAMETERS,
                        SSLExtension.CH_PRE_SHARED_KEY
                });

        // produce handshake message
        chc.initialClientHelloMsg.write(chc.handshakeOutput);
        handshakeState = NEED_SEND_CRYPTO;
    }

    @Override
    public void setRemoteQuicTransportParametersConsumer(
            QuicTransportParametersConsumer consumer) {
        this.remoteQuicTransportParametersConsumer = consumer;
    }

    void processRemoteQuicTransportParameters(ByteBuffer buffer)
            throws QuicTransportException{
        remoteQuicTransportParametersConsumer.accept(buffer);
    }

    @Override
    public boolean tryMarkHandshakeDone() {
        if (getUseClientMode()) {
            // not expected to be called on client
            throw new IllegalStateException(
                    "Not expected to be called in client mode");
        }
        final boolean confirmed = HANDSHAKE_STATE_HANDLE.compareAndSet(this,
                NEED_SEND_HANDSHAKE_DONE, HANDSHAKE_CONFIRMED);
        if (confirmed) {
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.fine("QuicTLSEngine (server) marked handshake " +
                        "state as HANDSHAKE_CONFIRMED");
            }
        }
        return confirmed;
    }

    @Override
    public boolean tryReceiveHandshakeDone() {
        final boolean isClient = getUseClientMode();
        if (!isClient) {
            throw new IllegalStateException(
                    "Not expected to receive HANDSHAKE_DONE in server mode");
        }
        final boolean confirmed = HANDSHAKE_STATE_HANDLE.compareAndSet(this,
                NEED_RECV_HANDSHAKE_DONE, HANDSHAKE_CONFIRMED);
        if (confirmed) {
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.fine(
                        "QuicTLSEngine (client) received HANDSHAKE_DONE," +
                        " marking state as HANDSHAKE_DONE");
            }
        }
        return confirmed;
    }

    @Override
    public boolean isTLSHandshakeComplete() {
        final boolean isClient = getUseClientMode();
        final HandshakeState hsState = this.handshakeState;
        if (isClient) {
            // the client has received TLS Finished message from server and
            // has sent its own TLS Finished message and is waiting for the server
            // to send QUIC HANDSHAKE_DONE frame.
            // OR
            // the client has received TLS Finished message from server and
            // has sent its own TLS Finished message and has even received the
            // QUIC HANDSHAKE_DONE frame.
            // Either of these implies the TLS handshake is complete for the client
            return hsState == NEED_RECV_HANDSHAKE_DONE || hsState == HANDSHAKE_CONFIRMED;
        }
        // on the server side the TLS handshake is complete only when the server has
        // sent a TLS Finished message and received the client's Finished message.
        return hsState == HANDSHAKE_CONFIRMED;
    }

    /**
     * {@return the key phase being used when decrypting incoming 1-RTT
     * packets}
     */
    // this is only used in tests
    public int getOneRttKeyPhase() throws QuicKeyUnavailableException {
        return this.oneRttKeyManager.getReadCipher().getKeyPhase();
    }
}
