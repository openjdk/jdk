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
package jdk.internal.net.quic;

import javax.crypto.AEADBadTagException;
import javax.crypto.ShortBufferException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * One instance of these per QUIC connection. Configuration methods not shown
 * but would be similar to SSLEngine.
 */
public interface QuicTLSEngine {

    /**
     * Represents the encryption level associated with a packet encryption or
     * decryption. A QUIC connection has a current keyspace for sending and
     * receiving which can be queried.
     */
    enum KeySpace {
        INITIAL,
        HANDSHAKE,
        RETRY, // Special algorithm used for this packet
        ZERO_RTT,
        ONE_RTT
    }

    enum HandshakeState {
        /**
         * Need to receive a CRYPTO frame
         */
        NEED_RECV_CRYPTO,
        /**
         * Need to receive a HANDSHAKE_DONE frame from server to complete the
         * handshake, but application data can be sent in this state (client
         * only state).
         */
        NEED_RECV_HANDSHAKE_DONE,
        /**
         * Need to send a CRYPTO frame
         */
        NEED_SEND_CRYPTO,
        /**
         * Need to send a HANDSHAKE_DONE frame to complete the handshake, but
         * application data can be sent in this state (server only state)
         */
        NEED_SEND_HANDSHAKE_DONE,
        /**
         * Need to execute a task
         */
        NEED_TASK,
        /**
         * Handshake is confirmed, as specified in section 4.1.2 of RFC-9001
         */
        // On client side this happens when client receives HANDSHAKE_DONE
        // frame. On server side this happens when the TLS stack has both
        // sent a Finished message and verified the peer's Finished message.
        HANDSHAKE_CONFIRMED,
    }

    /**
     * {@return the QUIC versions supported by this engine}
     */
    Set<QuicVersion> getSupportedQuicVersions();

    /**
     * If {@code mode} is {@code true} then configures this QuicTLSEngine to
     * operate in client mode. If {@code false}, then this QuicTLSEngine
     * operates in server mode.
     *
     * @param mode true to make this QuicTLSEngine operate in client
     *         mode, false otherwise
     */
    void setUseClientMode(boolean mode);

    /**
     * {@return true if this QuicTLSEngine is operating in client mode, false
     * otherwise}
     */
    boolean getUseClientMode();

    /**
     * {@return the SSLParameters in effect for this engine.}
     */
    SSLParameters getSSLParameters();

    /**
     * Sets the {@code SSLParameters} to be used by this engine
     *
     * @param sslParameters the SSLParameters
     * @throws IllegalArgumentException if
     *         {@linkplain SSLParameters#getProtocols() TLS protocol versions} on the
     *         {@code sslParameters} is either empty or contains anything other
     *         than {@code TLSv1.3}
     * @throws NullPointerException if {@code sslParameters} is null
     */
    void setSSLParameters(SSLParameters sslParameters);

    /**
     * {@return the most recent application protocol value negotiated by the
     * engine. Returns null if no application protocol has yet been negotiated
     * by the engine}
     */
    String getApplicationProtocol();

    /**
     * {@return the SSLSession}
     *
     * @see SSLEngine#getSession()
     */
    SSLSession getSession();

    /**
     * Returns the SSLSession being constructed during a QUIC handshake.
     *
     * @return null if this instance is not currently handshaking, or if the
     *              current handshake has not progressed far enough to create
     *              a basic SSLSession. Otherwise, this method returns the
     *              {@code SSLSession} currently being negotiated.
     *
     * @see SSLEngine#getHandshakeSession()
     */
    SSLSession getHandshakeSession();

    /**
     * Returns the current handshake state of the connection. Sometimes packets
     * that could be decrypted can be received before the handshake has
     * completed, but should not be decrypted until it is complete
     *
     * @return the HandshakeState
     */
    HandshakeState getHandshakeState();

    /**
     * Returns true if the TLS handshake is considered complete.
     * <p>
     * The TLS handshake is considered complete when the TLS stack
     * has reported that the handshake is complete. This happens when
     * the TLS stack has both sent a {@code Finished} message and verified
     * the peer's {@code Finished} message.
     *
     * @return true if TLS handshake is complete, false otherwise.
     */
    boolean isTLSHandshakeComplete();

    /**
     * {@return the current sending key space (encryption level)}
     */
    KeySpace getCurrentSendKeySpace();

    /**
     * Checks whether the keys for the given key space are available.
     * <p>
     * Keys are available when they are already computed and not discarded yet.
     *
     * @param keySpace key space to check
     * @return true if the given keys are available
     */
    boolean keysAvailable(KeySpace keySpace);

    /**
     * Discard the keys used by the {@code keySpace}.
     * <p>
     * Once the keys for a particular {@code keySpace} have been discarded, the
     * keySpace will no longer be able to
     * {@linkplain #encryptPacket(KeySpace, long, IntFunction,
     * ByteBuffer, ByteBuffer) encrypt} or
     * {@linkplain #decryptPacket(KeySpace, long, int, ByteBuffer, int, ByteBuffer)
     * decrypt} packets.
     *
     * @param keySpace The keyspace whose current keys should be discarded
     */
    void discardKeys(KeySpace keySpace);

    /**
     * Provide quic_transport_parameters for inclusion in handshake message.
     *
     * @param params encoded quic_transport_parameters
     */
    void setLocalQuicTransportParameters(ByteBuffer params);

    /**
     * Reset the handshake state and produce a new ClientHello message.
     *
     * When a Quic client receives a Version Negotiation packet,
     * it restarts the handshake by calling this method after updating the
     * {@linkplain #setLocalQuicTransportParameters(ByteBuffer) transport parameters}
     * with the new version information.
     */
    void restartHandshake() throws IOException;

    /**
     * Set consumer for quic_transport_parameters sent by the remote side.
     * Consumer will receive a byte buffer containing the value of
     * quic_transport_parameters extension sent by the remote endpoint.
     *
     * @param consumer consumer for remote quic transport parameters
     */
    void setRemoteQuicTransportParametersConsumer(
            QuicTransportParametersConsumer consumer);

    /**
     * Derive initial keys for the given QUIC version and connection ID
     * @param quicVersion QUIC protocol version
     * @param connectionId initial destination connection ID
     * @throws IllegalArgumentException if the {@code quicVersion} isn't
     *         {@linkplain #getSupportedQuicVersions() supported} on this
     *         {@code QuicTLSEngine}
     */
    void deriveInitialKeys(QuicVersion quicVersion, ByteBuffer connectionId) throws IOException;

    /**
     * Get the sample size for header protection algorithm
     *
     * @param keySpace Packet key space
     * @return required sample size for header protection
     * @throws IllegalArgumentException when keySpace does not require
     *         header protection
     */
    int getHeaderProtectionSampleSize(KeySpace keySpace);

    /**
     * Compute the header protection mask for the given sample,
     * packet key space and direction (incoming/outgoing).
     *
     * @param keySpace Packet key space
     * @param incoming true for incoming packets, false for outgoing
     * @param sample sampled data
     * @return mask bytes, at least 5.
     * @throws IllegalArgumentException when keySpace does not require
     *         header protection or sample length is different from required
     * @see #getHeaderProtectionSampleSize(KeySpace)
     * @spec https://www.rfc-editor.org/rfc/rfc9001.html#name-header-protection-applicati
     *     RFC 9001, Section 5.4.1 Header Protection Application
     */
    ByteBuffer computeHeaderProtectionMask(KeySpace keySpace,
                                           boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Get the authentication tag size. Encryption adds this number of bytes.
     *
     * @return authentication tag size
     */
    int getAuthTagSize();

    /**
     * Encrypt into {@code output}, the given {@code packetPayload} bytes using the
     * keys for the given {@code keySpace}.
     * <p>
     * Before encrypting the {@code packetPayload}, this method invokes the {@code headerGenerator}
     * passing it the key phase corresponding to the encryption key that's in use.
     * For {@code KeySpace}s where key phase isn't applicable, the {@code headerGenerator} will
     * be invoked with a value of {@code 0} for the key phase.
     * <p>
     * The {@code headerGenerator} is expected to return a {@code ByteBuffer} representing the
     * packet header and where applicable, the returned header must contain the key phase
     * that was passed to the {@code headerGenerator}. The packet header will be used as
     * the Additional Authentication Data (AAD) for encrypting the {@code packetPayload}.
     * <p>
     * Upon return, the {@code output} will contain the encrypted packet payload bytes
     * and the authentication tag. The {@code packetPayload} and the packet header, returned
     * by the {@code headerGenerator}, will have their {@code position} equal to their
     * {@code limit}. The limit of either of those buffers will not have changed.
     * <p>
     * It is recommended to do the encryption in place by using slices of a bigger
     * buffer as the input and output buffer:
     * <pre>
     *          +--------+-------------------+
     * input:   | header | plaintext payload |
     *          +--------+-------------------+----------+
     * output:           | encrypted payload | AEAD tag |
     *                   +-------------------+----------+
     * </pre>
     *
     * @param keySpace Packet key space
     * @param packetNumber full packet number
     * @param headerGenerator an {@link IntFunction} which takes a key phase and returns
     *                        the packet header
     * @param packetPayload buffer containing unencrypted packet payload
     * @param output buffer into which the encrypted packet payload will be written
     * @throws QuicKeyUnavailableException if keys are not available
     * @throws QuicTransportException if encrypting the packet would result
     *          in exceeding the AEAD cipher confidentiality limit
     */
    void encryptPacket(KeySpace keySpace, long packetNumber,
                       IntFunction<ByteBuffer> headerGenerator,
                       ByteBuffer packetPayload,
                       ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException, ShortBufferException;

    /**
     * Decrypt the given packet bytes using keys for the given packet key space.
     * Header protection must be removed before calling this method.
     * <p>
     * The input buffer contains the packet header and the encrypted packet payload.
     * The packet header (first {@code headerLength} bytes of the input buffer)
     * is consumed by this method, but is not decrypted.
     * The packet payload (bytes following the packet header) is decrypted
     * by this method. This method consumes the entire input buffer.
     * <p>
     * The decrypted payload bytes are written
     * to the output buffer.
     * <p>
     * It is recommended to do the decryption in place by using slices of a bigger
     * buffer as the input and output buffer:
     * <pre>
     *          +--------+-------------------+----------+
     * input:   | header | encrypted payload | AEAD tag |
     *          +--------+-------------------+----------+
     * output:           | decrypted payload |
     *                   +-------------------+
     * </pre>
     *
     * @param keySpace Packet key space
     * @param packetNumber full packet number
     * @param keyPhase key phase bit (0 or 1) found on the packet, or -1
     *                 if the packet does not have a key phase bit
     * @param packet buffer containing encrypted packet bytes
     * @param headerLength length of the packet header
     * @param output buffer where decrypted packet bytes will be stored
     * @throws IllegalArgumentException if keyPhase bit is invalid
     * @throws QuicKeyUnavailableException if keys are not available
     * @throws AEADBadTagException if the provided packet's authentication tag
     *          is incorrect
     * @throws QuicTransportException if decrypting the invalid packet resulted
     *          in exceeding the AEAD cipher integrity limit
     */
    void decryptPacket(KeySpace keySpace, long packetNumber, int keyPhase,
            ByteBuffer packet, int headerLength, ByteBuffer output)
            throws IllegalArgumentException, QuicKeyUnavailableException,
            AEADBadTagException, QuicTransportException, ShortBufferException;

    /**
     * Sign the provided retry packet. Input buffer contains the retry packet
     * payload. Integrity tag is stored in the output buffer.
     *
     * @param version Quic version
     * @param originalConnectionId original destination connection ID,
     *         without length
     * @param packet retry packet bytes without tag
     * @param output buffer where integrity tag will be stored
     * @throws ShortBufferException if output buffer is too short to
     *         hold the tag
     * @throws IllegalArgumentException if originalConnectionId is
     *         longer than 255 bytes
     * @throws IllegalArgumentException if {@code version} isn't
     *         {@linkplain #getSupportedQuicVersions() supported}
     */
    void signRetryPacket(QuicVersion version, ByteBuffer originalConnectionId,
            ByteBuffer packet, ByteBuffer output) throws ShortBufferException, QuicTransportException;

    /**
     * Verify the provided retry packet.
     *
     * @param version Quic version
     * @param originalConnectionId original destination connection ID,
     *         without length
     * @param packet retry packet bytes with tag
     * @throws AEADBadTagException if integrity tag is invalid
     * @throws IllegalArgumentException if originalConnectionId is
     *         longer than 255 bytes
     * @throws IllegalArgumentException if {@code version} isn't
     *         {@linkplain #getSupportedQuicVersions() supported}
     */
    void verifyRetryPacket(QuicVersion version, ByteBuffer originalConnectionId,
            ByteBuffer packet) throws AEADBadTagException, QuicTransportException;

    /**
     * If the current handshake state is {@link HandshakeState#NEED_SEND_CRYPTO}
     * meaning that a CRYPTO frame needs to be sent then this method is called
     * to obtain the contents of the frame. Current handshake state
     * can be obtained from {@link #getHandshakeState()}, and the current
     * key space can be obtained with {@link #getCurrentSendKeySpace()}
     * The bytes returned by this call are used to build a CRYPTO frame.
     *
     * @param keySpace the key space of the packet in which the
     *         requested data will be placed
     * @return buffer containing data that will be put by caller in a CRYPTO
     *         frame, or null if there are no more handshake bytes to send in
     *         this key space at this time.
     */
    ByteBuffer getHandshakeBytes(KeySpace keySpace) throws IOException;

    /**
     * This method consumes crypto stream.
     *
     * @param keySpace the key space of the packet in which the provided
     *         crypto data was encountered.
     * @param payload contents of the next CRYPTO frame
     * @throws IllegalArgumentException if keySpace is ZERORTT or
     *         payload is empty
     * @throws QuicTransportException if the handshake failed
     */
    void consumeHandshakeBytes(KeySpace keySpace, ByteBuffer payload)
            throws QuicTransportException;

    /**
     * Returns a delegated {@code Runnable} task for
     * this {@code QuicTLSEngine}.
     * <P>
     * {@code QuicTLSEngine} operations may require the results of
     * operations that block, or may take an extended period of time to
     * complete.  This method is used to obtain an outstanding {@link
     * java.lang.Runnable} operation (task).  Each task must be assigned
     * a thread (possibly the current) to perform the {@link
     * java.lang.Runnable#run() run} operation.  Once the
     * {@code run} method returns, the {@code Runnable} object
     * is no longer needed and may be discarded.
     * <P>
     * A call to this method will return each outstanding task
     * exactly once.
     * <P>
     * Multiple delegated tasks can be run in parallel.
     *
     * @return  a delegated {@code Runnable} task, or null
     *          if none are available.
     */
    Runnable getDelegatedTask();

    /**
     * Called to check if a {@code HANDSHAKE_DONE} frame needs to be sent by the
     * server. This method will only be called for a {@code QuicTLSEngine} which
     * is in {@linkplain #getUseClientMode() server mode}. If the current TLS handshake
     * state is
     * {@link  HandshakeState#NEED_SEND_HANDSHAKE_DONE
     * NEED_SEND_HANDSHAKE_DONE} then this method returns {@code true} and
     * advances the TLS handshake state to
     * {@link  HandshakeState#HANDSHAKE_CONFIRMED HANDSHAKE_CONFIRMED}. Else
     * returns {@code false}.
     *
     * @return true if handshake state was {@code NEED_SEND_HANDSHAKE_DONE},
     *         false otherwise
     * @throws IllegalStateException If this {@code QuicTLSEngine} is
     *         not in server mode
     */
    boolean tryMarkHandshakeDone() throws IllegalStateException;

    /**
     * Called when HANDSHAKE_DONE message is received from the server. This
     * method will only be called for a {@code QuicTLSEngine} which is in
     * {@linkplain #getUseClientMode() client mode}. If the current TLS handshake state
     * is
     * {@link  HandshakeState#NEED_RECV_HANDSHAKE_DONE
     * NEED_RECV_HANDSHAKE_DONE} then this method returns {@code true} and
     * advances the TLS handshake state to
     * {@link  HandshakeState#HANDSHAKE_CONFIRMED HANDSHAKE_CONFIRMED}. Else
     * returns {@code false}.
     *
     * @return true if handshake state was {@code NEED_RECV_HANDSHAKE_DONE},
     *         false otherwise
     * @throws IllegalStateException if this {@code QuicTLSEngine} is
     *         not in client mode
     */
    boolean tryReceiveHandshakeDone() throws IllegalStateException;

    /**
     * Called when the client and the server, during the connection creation
     * handshake, have settled on a Quic version to use for the connection. This
     * can happen either due to an explicit version negotiation (as outlined in
     * Quic RFC) or the server accepting the Quic version that the client chose
     * in its first INITIAL packet. In either of those cases, this method will
     * be called.
     *
     * @param quicVersion the negotiated {@code QuicVersion}
     * @throws IllegalArgumentException if the {@code quicVersion} isn't
     *         {@linkplain #getSupportedQuicVersions() supported} on this engine
     */
    void versionNegotiated(QuicVersion quicVersion);

    /**
     * Sets the {@link QuicOneRttContext} on the {@code QuicTLSEngine}.
     * <p> The {@code ctx} will be used by the {@code QuicTLSEngine} to access contextual 1-RTT
     * data that might be required for the TLS operations.
     *
     * @param ctx the 1-RTT context to set
     * @throws NullPointerException if {@code ctx} is null
     */
    void setOneRttContext(QuicOneRttContext ctx);
}
