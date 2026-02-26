/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicOneRttContext;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicVersion;
import jdk.internal.vm.annotation.Stable;
import sun.security.ssl.QuicCipher.QuicReadCipher;
import sun.security.ssl.QuicCipher.QuicWriteCipher;
import sun.security.util.KeyUtil;

import static jdk.internal.net.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static jdk.internal.net.quic.QuicTLSEngine.KeySpace.INITIAL;
import static jdk.internal.net.quic.QuicTLSEngine.KeySpace.ONE_RTT;
import static jdk.internal.net.quic.QuicTransportErrors.AEAD_LIMIT_REACHED;
import static jdk.internal.net.quic.QuicTransportErrors.KEY_UPDATE_ERROR;
import static sun.security.ssl.QuicTLSEngineImpl.BASE_CRYPTO_ERROR;

sealed abstract class QuicKeyManager
        permits QuicKeyManager.HandshakeKeyManager,
        QuicKeyManager.InitialKeyManager, QuicKeyManager.OneRttKeyManager {

    private record QuicKeys(SecretKey key, byte[] iv, SecretKey hp) {
    }

    private record CipherPair(QuicReadCipher readCipher,
                              QuicWriteCipher writeCipher) {
        void discard(boolean destroyHP) {
            writeCipher.discard(destroyHP);
            readCipher.discard(destroyHP);
        }

        /**
         * {@return true if the keys represented by this {@code CipherPair}
         * were used by both this endpoint and the peer, thus implying these
         * keys are available to both of them}
         */
        boolean usedByBothEndpoints() {
            return this.readCipher.hasDecryptedAny() &&
                    this.writeCipher.hasEncryptedAny();
        }
    }

    final QuicTLSEngine.KeySpace keySpace;
    // counter towards the integrity limit
    final AtomicLong invalidPackets = new AtomicLong();
    volatile boolean keysDiscarded;

    private QuicKeyManager(final QuicTLSEngine.KeySpace keySpace) {
        this.keySpace = keySpace;
    }

    protected abstract boolean keysAvailable();

    protected abstract QuicReadCipher getReadCipher()
            throws QuicKeyUnavailableException;

    protected abstract QuicWriteCipher getWriteCipher()
            throws QuicKeyUnavailableException;

    abstract void discardKeys();

    void decryptPacket(final long packetNumber, final int keyPhase,
            final ByteBuffer packet,final int headerLength,
            final ByteBuffer output) throws QuicKeyUnavailableException,
            IllegalArgumentException, AEADBadTagException,
            QuicTransportException, ShortBufferException {
        // keyPhase is only applicable for 1-RTT packets; the decryptPacket
        // method is overridden by OneRttKeyManager, so this check is for
        // other packet types
        if (keyPhase != -1) {
            throw new IllegalArgumentException(
                    "Unexpected key phase value: " + keyPhase);
        }
        // use current keys to decrypt
        QuicReadCipher readCipher = getReadCipher();
        try {
            readCipher.decryptPacket(packetNumber, packet, headerLength, output);
        } catch (AEADBadTagException e) {
            if (invalidPackets.incrementAndGet() >=
                    readCipher.integrityLimit()) {
                throw new QuicTransportException("Integrity limit reached",
                        keySpace, 0, AEAD_LIMIT_REACHED);
            }
            throw e;
        }
    }

    void encryptPacket(final long packetNumber,
                       final IntFunction<ByteBuffer> headerGenerator,
                       final ByteBuffer packetPayload,
                       final ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException, ShortBufferException {
        // generate the packet header passing the generator the key phase
        final ByteBuffer header = headerGenerator.apply(0); // key phase is always 0 for non-ONE_RTT
        getWriteCipher().encryptPacket(packetNumber, header, packetPayload, output);
    }

    private static QuicKeys deriveQuicKeys(final QuicVersion quicVersion,
            final CipherSuite cs, final SecretKey traffic_secret)
            throws IOException {
        final SSLKeyDerivation kd = new QuicTLSKeyDerivation(cs,
                traffic_secret);
        final QuicTLSData tlsData = getQuicData(quicVersion);
        final SecretKey quic_key = kd.deriveKey(tlsData.getTlsKeyLabel());
        final byte[] quic_iv = kd.deriveData(tlsData.getTlsIvLabel());
        final SecretKey quic_hp = kd.deriveKey(tlsData.getTlsHpLabel());
        return new QuicKeys(quic_key, quic_iv, quic_hp);
    }

    // Used in 1RTT when advancing the keyphase.  quic_hp is not advanced.
    private static QuicKeys deriveQuicKeys(final QuicVersion quicVersion,
            final CipherSuite cs, final SecretKey traffic_secret,
            final SecretKey quic_hp) throws IOException {
        final SSLKeyDerivation kd = new QuicTLSKeyDerivation(cs,
                traffic_secret);
        final QuicTLSData tlsData = getQuicData(quicVersion);
        final SecretKey quic_key = kd.deriveKey(tlsData.getTlsKeyLabel());
        final byte[] quic_iv = kd.deriveData(tlsData.getTlsIvLabel());
        return new QuicKeys(quic_key, quic_iv, quic_hp);
    }

    private static QuicTLSData getQuicData(final QuicVersion quicVersion) {
        return switch (quicVersion) {
            case QUIC_V1 -> QuicTLSData.V1;
            case QUIC_V2 -> QuicTLSData.V2;
        };
    }

    private static byte[] createHkdfInfo(final String label, final int length) {
        final byte[] tls13Label =
                ("tls13 " + label).getBytes(StandardCharsets.UTF_8);
        return createHkdfInfo(tls13Label, length);
    }

    private static byte[] createHkdfInfo(final byte[] tls13Label,
            final int length) {
        final byte[] info = new byte[4 + tls13Label.length];
        final ByteBuffer m = ByteBuffer.wrap(info);
        try {
            Record.putInt16(m, length);
            Record.putBytes8(m, tls13Label);
            Record.putInt8(m, 0x00); // zero-length context
        } catch (IOException ioe) {
            // unlikely
            throw new UncheckedIOException("Unexpected exception", ioe);
        }
        return info;
    }

    static final class InitialKeyManager extends QuicKeyManager {

        private volatile CipherPair cipherPair;

        InitialKeyManager() {
            super(INITIAL);
        }

        @Override
        protected boolean keysAvailable() {
            return this.cipherPair != null && !this.keysDiscarded;
        }

        @Override
        protected QuicReadCipher getReadCipher()
                throws QuicKeyUnavailableException {
            final CipherPair pair = this.cipherPair;
            if (pair == null) {
                final String msg = this.keysDiscarded
                        ? "Keys have been discarded"
                        : "Keys not available";
                throw new QuicKeyUnavailableException(msg, this.keySpace);
            }
            return pair.readCipher;
        }

        @Override
        protected QuicWriteCipher getWriteCipher()
                throws QuicKeyUnavailableException {
            final CipherPair pair = this.cipherPair;
            if (pair == null) {
                final String msg = this.keysDiscarded
                        ? "Keys have been discarded"
                        : "Keys not available";
                throw new QuicKeyUnavailableException(msg, this.keySpace);
            }
            return pair.writeCipher;
        }

        @Override
        void discardKeys() {
            final CipherPair toDiscard = this.cipherPair;
            this.keysDiscarded = true;
            this.cipherPair = null; // no longer needed
            if (toDiscard == null) {
                return;
            }
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.finest("discarding keys (keyphase="
                        + toDiscard.writeCipher.getKeyPhase()
                        + ") of " + this.keySpace + " key space");
            }
            toDiscard.discard(true);
        }

        void deriveKeys(final QuicVersion quicVersion,
                final byte[] connectionId,
                final boolean clientMode) throws IOException{
            Objects.requireNonNull(quicVersion);
            final CipherSuite cs = CipherSuite.TLS_AES_128_GCM_SHA256;
            final CipherSuite.HashAlg hashAlg = cs.hashAlg;

            KDF hkdf;
            try {
                hkdf = KDF.getInstance(hashAlg.hkdfAlgorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new SSLHandshakeException("Could not generate secret", e);
            }
            final QuicTLSData tlsData = QuicKeyManager.getQuicData(quicVersion);
            SecretKey initial_secret = null;
            SecretKey server_initial_secret = null;
            SecretKey client_initial_secret = null;
            try {
                initial_secret = hkdf.deriveKey("TlsInitialSecret",
                        HKDFParameterSpec.ofExtract()
                                .addSalt(tlsData.getInitialSalt())
                                .addIKM(connectionId).extractOnly());

                byte[] clientInfo = createHkdfInfo("client in",
                        hashAlg.hashLength);
                client_initial_secret =
                        hkdf.deriveKey("TlsClientInitialTrafficSecret",
                                HKDFParameterSpec.expandOnly(
                                        initial_secret,
                                        clientInfo,
                                        hashAlg.hashLength));
                QuicKeys clientKeys = deriveQuicKeys(quicVersion, cs,
                        client_initial_secret);

                byte[] serverInfo = createHkdfInfo("server in",
                        hashAlg.hashLength);
                server_initial_secret =
                        hkdf.deriveKey("TlsServerInitialTrafficSecret",
                                HKDFParameterSpec.expandOnly(
                                        initial_secret,
                                        serverInfo,
                                        hashAlg.hashLength));
                QuicKeys serverKeys = deriveQuicKeys(quicVersion, cs,
                        server_initial_secret);

                final QuicReadCipher readCipher;
                final QuicWriteCipher writeCipher;
                final int keyPhase = 0;
                if (clientMode) {
                    readCipher = QuicCipher.createReadCipher(cs,
                            server_initial_secret,
                            serverKeys.key, serverKeys.iv, serverKeys.hp,
                            keyPhase);
                    writeCipher = QuicCipher.createWriteCipher(cs,
                            client_initial_secret,
                            clientKeys.key, clientKeys.iv, clientKeys.hp,
                            keyPhase);
                } else {
                    readCipher = QuicCipher.createReadCipher(cs,
                            client_initial_secret,
                            clientKeys.key, clientKeys.iv, clientKeys.hp,
                            keyPhase);
                    writeCipher = QuicCipher.createWriteCipher(cs,
                            server_initial_secret,
                            serverKeys.key, serverKeys.iv, serverKeys.hp,
                            keyPhase);
                }
                final CipherPair old = this.cipherPair;
                // we don't check if keys are already available, since it's a
                // valid case where the INITIAL keys are regenerated due to a
                // RETRY packet from the peer or even for the case where a
                // different quic version was negotiated by the server
                this.cipherPair = new CipherPair(readCipher, writeCipher);
                if (old != null) {
                    old.discard(true);
                }
            } catch (GeneralSecurityException e) {
                throw new SSLException("Missing cipher algorithm", e);
            } finally {
                KeyUtil.destroySecretKeys(initial_secret, client_initial_secret,
                        server_initial_secret);
            }
        }

        static Cipher getRetryCipher(final QuicVersion quicVersion,
                final boolean incoming) throws QuicTransportException {
            final QuicTLSData tlsData = QuicKeyManager.getQuicData(quicVersion);
            return tlsData.getRetryCipher(incoming);
        }
    }

    static final class HandshakeKeyManager extends QuicKeyManager {
        private volatile CipherPair cipherPair;

        HandshakeKeyManager() {
            super(HANDSHAKE);
        }

        @Override
        protected boolean keysAvailable() {
            return this.cipherPair != null && !this.keysDiscarded;
        }

        @Override
        protected QuicReadCipher getReadCipher()
                throws QuicKeyUnavailableException {
            final CipherPair pair = this.cipherPair;
            if (pair == null) {
                final String msg = this.keysDiscarded
                        ? "Keys have been discarded"
                        : "Keys not available";
                throw new QuicKeyUnavailableException(msg, this.keySpace);
            }
            return pair.readCipher;
        }

        @Override
        protected QuicWriteCipher getWriteCipher()
                throws QuicKeyUnavailableException {
            final CipherPair pair = this.cipherPair;
            if (pair == null) {
                final String msg = this.keysDiscarded
                        ? "Keys have been discarded"
                        : "Keys not available";
                throw new QuicKeyUnavailableException(msg, this.keySpace);
            }
            return pair.writeCipher;
        }

        @Override
        void discardKeys() {
            final CipherPair toDiscard = this.cipherPair;
            this.cipherPair = null; // no longer needed
            this.keysDiscarded = true;
            if (toDiscard == null) {
                return;
            }
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.finest("discarding keys (keyphase="
                        + toDiscard.writeCipher.getKeyPhase()
                        + ") of " + this.keySpace + " key space");
            }
            toDiscard.discard(true);
        }

        void deriveKeys(final QuicVersion quicVersion,
                final HandshakeContext handshakeContext,
                final boolean clientMode) throws IOException {
            Objects.requireNonNull(quicVersion);
            if (keysAvailable()) {
                throw new IllegalStateException(
                        "Keys already derived for " + this.keySpace +
                                " key space");
            }
            SecretKey client_handshake_traffic_secret = null;
            SecretKey server_handshake_traffic_secret = null;
            try {
                final SSLKeyDerivation kd =
                        handshakeContext.handshakeKeyDerivation;
                client_handshake_traffic_secret = kd.deriveKey(
                        "TlsClientHandshakeTrafficSecret");
                final QuicKeys clientKeys = deriveQuicKeys(quicVersion,
                        handshakeContext.negotiatedCipherSuite,
                        client_handshake_traffic_secret);
                server_handshake_traffic_secret = kd.deriveKey(
                        "TlsServerHandshakeTrafficSecret");
                final QuicKeys serverKeys = deriveQuicKeys(quicVersion,
                        handshakeContext.negotiatedCipherSuite,
                        server_handshake_traffic_secret);

                final CipherSuite negotiatedCipherSuite =
                        handshakeContext.negotiatedCipherSuite;
                final QuicReadCipher readCipher;
                final QuicWriteCipher writeCipher;
                final int keyPhase = 0;
                if (clientMode) {
                    readCipher =
                            QuicCipher.createReadCipher(negotiatedCipherSuite,
                                    server_handshake_traffic_secret,
                                    serverKeys.key, serverKeys.iv,
                                    serverKeys.hp, keyPhase);
                    writeCipher =
                            QuicCipher.createWriteCipher(negotiatedCipherSuite,
                                    client_handshake_traffic_secret,
                                    clientKeys.key, clientKeys.iv,
                                    clientKeys.hp, keyPhase);
                } else {
                    readCipher =
                            QuicCipher.createReadCipher(negotiatedCipherSuite,
                                    client_handshake_traffic_secret,
                                    clientKeys.key, clientKeys.iv,
                                    clientKeys.hp, keyPhase);
                    writeCipher =
                            QuicCipher.createWriteCipher(negotiatedCipherSuite,
                                    server_handshake_traffic_secret,
                                    serverKeys.key, serverKeys.iv,
                                    serverKeys.hp, keyPhase);
                }
                synchronized (this) {
                    if (this.cipherPair != null) {
                        // don't allow setting more than once
                        throw new IllegalStateException("Keys already " +
                                "available for keyspace: "
                                + this.keySpace);
                    }
                    this.cipherPair = new CipherPair(readCipher, writeCipher);
                }
            } catch (GeneralSecurityException e) {
                throw new SSLException("Missing cipher algorithm", e);
            } finally {
                KeyUtil.destroySecretKeys(client_handshake_traffic_secret,
                        server_handshake_traffic_secret);
            }
        }
    }

    static final class OneRttKeyManager extends QuicKeyManager {
        // a series of keys that the 1-RTT key manager uses
        private record KeySeries(QuicReadCipher old, CipherPair current,
                                 CipherPair next) {
            private KeySeries {
                Objects.requireNonNull(current);
                if (old != null) {
                    if (old.getKeyPhase() ==
                            current.writeCipher.getKeyPhase()) {
                        throw new IllegalArgumentException("Both old keys and" +
                                " current keys have the same key phase: " +
                                current.writeCipher.getKeyPhase());
                    }
                }
                if (next != null) {
                    if (next.writeCipher.getKeyPhase() ==
                            current.writeCipher.getKeyPhase()) {
                        throw new IllegalArgumentException("Both next keys " +
                                "and current keys have the same key phase: " +
                                current.writeCipher.getKeyPhase());
                    }
                }
            }

            /**
             * {@return true if this {@code KeySeries} has an old decryption key
             * and the {@code pktNum} is lower than the least packet number the
             * current decryption key has decrypted so far}
             *
             * @param pktNum the packet number for which the old key
             *         might be needed
             */
            boolean canUseOldDecryptKey(final long pktNum) {
                assert pktNum >= 0 : "unexpected packet number: " + pktNum;
                if (this.old == null) {
                    return false;
                }
                final QuicReadCipher currentKey = this.current.readCipher;
                final long lowestDecrypted = currentKey.lowestDecryptedPktNum();
                // if the incoming packet number is lesser than the lowest
                // decrypted packet number by the current key, then it
                // implies that this might be a delayed packet and thus is
                // allowed to use the old key (if available) from
                // the previous key phase.
                // see RFC-9001, section 6.5
                if (lowestDecrypted == -1) {
                    return true;
                }
                return pktNum < lowestDecrypted;
            }
        }

        // will be set when the keys are derived
        private volatile QuicVersion negotiatedVersion;

        private final Lock keySeriesLock = new ReentrantLock();
        // will be set when keys are derived and will
        // be updated whenever keys are updated.
        // Must be updated/written only
        // when holding the keySeriesLock lock
        private volatile KeySeries keySeries;

        @Stable
        private volatile QuicOneRttContext oneRttContext;

        OneRttKeyManager() {
            super(ONE_RTT);
        }

        @Override
        protected boolean keysAvailable() {
            return this.keySeries != null && !this.keysDiscarded;
        }

        @Override
        protected QuicReadCipher getReadCipher()
                throws QuicKeyUnavailableException {
            final KeySeries series = requireKeySeries();
            return series.current.readCipher;
        }

        @Override
        protected QuicWriteCipher getWriteCipher()
                throws QuicKeyUnavailableException {
            final KeySeries series = requireKeySeries();
            return series.current.writeCipher;
        }

        @Override
        void discardKeys() {
            this.keysDiscarded = true;
            final KeySeries series;
            this.keySeriesLock.lock();
            try {
                series = this.keySeries;
                this.keySeries = null; // no longer available
            } finally {
                this.keySeriesLock.unlock();
            }
            if (series == null) {
                return;
            }
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.finest("discarding key (series) of " +
                        this.keySpace + " key space");
            }
            if (series.old != null) {
                series.old.discard(false);
            }
            discardKeys(series.current);
            discardKeys(series.next);
        }

        @Override
        void decryptPacket(final long packetNumber, final int keyPhase,
                final ByteBuffer packet, final int headerLength,
                final ByteBuffer output) throws QuicKeyUnavailableException,
                QuicTransportException, AEADBadTagException, ShortBufferException {
            if (keyPhase != 0 && keyPhase != 1) {
                throw new IllegalArgumentException("Unexpected key phase " +
                        "value: " + keyPhase);
            }
            final KeySeries series = requireKeySeries();
            final CipherPair current = series.current;
            // Use the write cipher's key phase to detect a key update as noted
            // in RFC-9001, section 6.2:
            // An endpoint detects a key update when processing a packet with
            // a key phase that differs from the value used to protect the
            // last packet it sent.
            final int currentKeyPhase = current.writeCipher.getKeyPhase();
            if (keyPhase == currentKeyPhase) {
                current.readCipher.decryptPacket(packetNumber, packet,
                        headerLength, output);
                return;
            }
            // incoming packet is using a key phase which doesn't match the
            // current key phase. this implies that either a key update
            // is being initiated or a key update initiated by the current
            // endpoint is in progress and some older packet with the
            // previous key phase has arrived.
            if (series.canUseOldDecryptKey(packetNumber)) {
                final QuicReadCipher oldReadCipher = series.old;
                assert oldReadCipher != null : "old key is unexpectedly null";
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.finest("using old read key to decrypt packet: " +
                            packetNumber + ", with incoming key phase: " +
                            keyPhase + ", current key phase: " +
                            currentKeyPhase);
                }
                oldReadCipher.decryptPacket(
                        packetNumber, packet, headerLength, output);
                // we were able to decrypt using an old key. now verify
                // that it was OK to use this old key for this packet.
                if (!series.current.usedByBothEndpoints()
                        && series.current.writeCipher.hasEncryptedAny()
                        && oneRttContext.getLargestPeerAckedPN()
                            >= series.current.writeCipher.lowestEncryptedPktNum()) {
                    // RFC-9001, section 6.2:
                    // An endpoint that receives an acknowledgment that is
                    // carried in a packet protected with old keys where any
                    // acknowledged packet was protected with newer keys MAY
                    // treat that as a connection error of type
                    // KEY_UPDATE_ERROR. This indicates that a peer has
                    // received and acknowledged a packet that initiates a key
                    // update, but has not updated keys in response.
                    if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                        SSLLogger.finest("peer used incorrect key, was" +
                                " expected to use updated key of" +
                                " key phase: " + currentKeyPhase +
                                ", incoming key phase: " + keyPhase +
                                ", packet number: " + packetNumber);
                    }
                    throw new QuicTransportException("peer used incorrect" +
                            " key, was expected to use updated key",
                            this.keySpace, 0, KEY_UPDATE_ERROR);
                }
                return;
            }
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.finest("detected ONE_RTT key update, current key " +
                        "phase: " + currentKeyPhase
                        + ", incoming key phase: " + keyPhase
                        + ", packet number: " + packetNumber);
            }
            decryptUsingNextKeys(
                    series, packetNumber, packet, headerLength, output);
        }

        @Override
        void encryptPacket(final long packetNumber,
                           final IntFunction<ByteBuffer> headerGenerator,
                           final ByteBuffer packetPayload,
                           final ByteBuffer output)
                throws QuicKeyUnavailableException, QuicTransportException, ShortBufferException {
            KeySeries currentSeries = requireKeySeries();
            if (currentSeries.next == null) {
                // next keys haven't yet been generated,
                // generate them now
                try {
                    currentSeries = generateNextKeys(
                            this.negotiatedVersion, currentSeries);
                } catch (GeneralSecurityException | IOException e) {
                    throw new QuicTransportException("Failed to update keys",
                            ONE_RTT, 0, BASE_CRYPTO_ERROR + Alert.INTERNAL_ERROR.id, e);
                }
            }
            maybeInitiateKeyUpdate(currentSeries, packetNumber);
            // call getWriteCipher() afresh so that it can use
            // the new keyseries if at all the key update was
            // initiated
            final QuicWriteCipher writeCipher = getWriteCipher();
            final int keyPhase = writeCipher.getKeyPhase();
            // generate the packet header passing the generator the key phase
            final ByteBuffer header = headerGenerator.apply(keyPhase);
            writeCipher.encryptPacket(packetNumber, header, packetPayload, output);
        }

        void setOneRttContext(final QuicOneRttContext ctx) {
            Objects.requireNonNull(ctx);
            this.oneRttContext = ctx;
        }

        private KeySeries requireKeySeries()
                throws QuicKeyUnavailableException {
            final KeySeries series = this.keySeries;
            if (series != null) {
                return series;
            }
            final String msg = this.keysDiscarded
                    ? "Keys have been discarded"
                    : "Keys not available";
            throw new QuicKeyUnavailableException(msg, this.keySpace);
        }

        // based on certain internal criteria, this method may trigger a key
        // update.
        // returns true if it does trigger the key update. false otherwise.
        private boolean maybeInitiateKeyUpdate(final KeySeries currentSeries,
                final long packetNumber) {
            final QuicWriteCipher cipher = currentSeries.current.writeCipher;
            // when we notice that we have reached 80% (which is arbitrary)
            // of the confidentiality limit, we trigger a key update instead
            // of waiting to hit the limit
            final long confidentialityLimit = cipher.confidentialityLimit();
            if (confidentialityLimit < 0) {
                return false;
            }
            final long numEncrypted = cipher.getNumEncrypted();
            if (numEncrypted >= 0.8 * confidentialityLimit) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.finest("about to reach confidentiality limit, " +
                            "attempting to initiate a 1-RTT key update," +
                            " packet number: " +
                            packetNumber + ", current key phase: " +
                            cipher.getKeyPhase());
                }
                final boolean initiated = initiateKeyUpdate(currentSeries);
                if (initiated) {
                    final int newKeyPhase =
                            this.keySeries.current.writeCipher.getKeyPhase();
                    assert cipher.getKeyPhase() != newKeyPhase
                            : "key phase of updated key unexpectedly matches " +
                            "the key phase "
                            + cipher.getKeyPhase() + " of current keys";
                    if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                        SSLLogger.finest(
                                "1-RTT key update initiated, new key phase: "
                                        + newKeyPhase);
                    }
                }
                return initiated;
            }
            return false;
        }

        private boolean initiateKeyUpdate(final KeySeries series) {
            // we only initiate a key update if this current endpoint and the
            // peer have both been using this current key
            if (!series.current.usedByBothEndpoints()) {
                // RFC-9001, section 6.1:
                // An endpoint MUST NOT initiate a subsequent key update
                // unless it has received
                // an acknowledgment for a packet that was sent protected
                // with keys from the
                // current key phase. This ensures that keys are
                // available to both peers before
                // another key update can be initiated.
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.finest(
                            "skipping key update initiation because peer " +
                            "hasn't yet sent us a packet encrypted with " +
                            "current key of key phase: " +
                            series.current.readCipher.getKeyPhase());
                }
                return false;
            }
            // OK to initiate a key update.
            // An endpoint initiates a key update by updating its packet
            // protection write secret
            // and using that to protect new packets.
            rolloverKeys(this.negotiatedVersion, series);
            return true;
        }

        private static void discardKeys(final CipherPair cipherPair) {
            if (cipherPair == null) {
                return;
            }
            cipherPair.discard(true);
        }

        /**
         * uses "next" keys to try and decrypt the incoming packet. if that
         * succeeded then it implies that the key update was indeed initiated by
         * the peer and this method then rolls over the keys to start using
         * these "next" keys. this method then returns true in such cases. if
         * the packet decryption using the "next" key fails, then this method
         * just returns back false (and doesn't roll over the keys)
         */
        private void decryptUsingNextKeys(
                final KeySeries currentKeySeries,
                final long packetNumber,
                final ByteBuffer packet,
                final int headerLength,
                final ByteBuffer output)
                throws QuicKeyUnavailableException, AEADBadTagException,
                ShortBufferException, QuicTransportException {
            if (currentKeySeries.next == null) {
                // this can happen if the peer initiated another
                // key update before we could generate the next
                // keys during our encryption flow. in such
                // cases we reject the key update for the packet
                // (we avoid timing attacks by not generating
                // keys during decryption, our key generation
                // only happens during encryption)
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.finest("next keys unavailable," +
                            " won't decrypt a packet which appears to be" +
                            " a key update");
                }
                throw new QuicKeyUnavailableException(
                        "next keys unavailable to handle key update",
                        this.keySpace);
            }
            // use the next keys to attempt decrypting
            currentKeySeries.next.readCipher.decryptPacket(packetNumber, packet,
                    headerLength, output);
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.finest(
                        "decrypted using next keys for peer-initiated" +
                        " key update; will now switch to new key phase: " +
                        currentKeySeries.next.readCipher.getKeyPhase());
            }
            // we have successfully decrypted the packet using the new/next
            // read key. So we now update even the write key as noted in
            // RFC-9001, section 6.2:
            // If a packet is successfully processed using the next key and
            // IV, then the peer has initiated a key update. The endpoint
            // MUST update its send keys to the corresponding
            // key phase in response, as described in Section 6.1. Sending
            // keys MUST be updated before sending an acknowledgment for the
            // packet that was received with updated keys. rollover the
            // keys == old gets discarded and is replaced by
            // current, current is replaced by next and next is set to null
            // (a new set of next keys will be generated separately on
            // a schedule)
            rolloverKeys(this.negotiatedVersion, currentKeySeries);
        }

        void deriveKeys(final QuicVersion negotiatedVersion,
                final HandshakeContext handshakeContext,
                final boolean clientMode) throws IOException {
            Objects.requireNonNull(negotiatedVersion);
            if (keysAvailable()) {
                throw new IllegalStateException("Keys already derived for " +
                        this.keySpace + " key space");
            }
            this.negotiatedVersion = negotiatedVersion;

            try {
                SSLKeyDerivation kd = handshakeContext.handshakeKeyDerivation;
                SecretKey client_application_traffic_secret_0 = kd.deriveKey(
                        "TlsClientAppTrafficSecret");
                SecretKey server_application_traffic_secret_0 = kd.deriveKey(
                        "TlsServerAppTrafficSecret");

                deriveOneRttKeys(this.negotiatedVersion,
                        client_application_traffic_secret_0,
                        server_application_traffic_secret_0,
                        handshakeContext.negotiatedCipherSuite,
                        clientMode);
            } catch (GeneralSecurityException e) {
                throw new SSLException("Missing cipher algorithm", e);
            }
        }

        void deriveOneRttKeys(final QuicVersion version,
                final SecretKey client_application_traffic_secret_0,
                final SecretKey server_application_traffic_secret_0,
                final CipherSuite negotiatedCipherSuite,
                final boolean clientMode) throws IOException,
                GeneralSecurityException {
            final QuicKeys clientKeys = deriveQuicKeys(version,
                    negotiatedCipherSuite,
                    client_application_traffic_secret_0);
            final QuicKeys serverKeys = deriveQuicKeys(version,
                    negotiatedCipherSuite,
                    server_application_traffic_secret_0);
            final QuicReadCipher readCipher;
            final QuicWriteCipher writeCipher;
            // this method always derives the first key for the 1-RTT, so key
            // phase is always 0
            final int keyPhase = 0;
            if (clientMode) {
                readCipher = QuicCipher.createReadCipher(negotiatedCipherSuite,
                        server_application_traffic_secret_0, serverKeys.key,
                        serverKeys.iv, serverKeys.hp, keyPhase);
                writeCipher =
                        QuicCipher.createWriteCipher(negotiatedCipherSuite,
                        client_application_traffic_secret_0, clientKeys.key,
                        clientKeys.iv, clientKeys.hp, keyPhase);
            } else {
                readCipher = QuicCipher.createReadCipher(negotiatedCipherSuite,
                        client_application_traffic_secret_0, clientKeys.key,
                        clientKeys.iv, clientKeys.hp, keyPhase);
                writeCipher =
                        QuicCipher.createWriteCipher(negotiatedCipherSuite,
                        server_application_traffic_secret_0, serverKeys.key,
                        serverKeys.iv, serverKeys.hp, keyPhase);
            }
            // generate the next set of keys beforehand to prevent any timing
            // attacks
            // during key update
            final QuicReadCipher nPlus1ReadCipher =
                    generateNextReadCipher(version, readCipher);
            final QuicWriteCipher nPlus1WriteCipher =
                    generateNextWriteCipher(version, writeCipher);
            this.keySeriesLock.lock();
            try {
                if (this.keySeries != null) {
                    // don't allow deriving the first set of 1-RTT keys more
                    // than once
                    throw new IllegalStateException("Keys already available " +
                            "for keyspace: "
                            + this.keySpace);
                }
                this.keySeries = new KeySeries(null,
                        new CipherPair(readCipher, writeCipher),
                        new CipherPair(nPlus1ReadCipher, nPlus1WriteCipher));
            } finally {
                this.keySeriesLock.unlock();
            }
        }

        private static QuicWriteCipher generateNextWriteCipher(
                final QuicVersion quicVersion, final QuicWriteCipher current)
                throws IOException, GeneralSecurityException {
            final SSLKeyDerivation kd =
                    new QuicTLSKeyDerivation(current.getCipherSuite(),
                            current.getBaseSecret());
            final QuicTLSData tlsData = QuicKeyManager.getQuicData(quicVersion);
            final SecretKey nplus1Secret =
                    kd.deriveKey(tlsData.getTlsKeyUpdateLabel());
            final QuicKeys quicKeys =
                    QuicKeyManager.deriveQuicKeys(quicVersion,
                            current.getCipherSuite(),
                            nplus1Secret, current.getHeaderProtectionKey());
            final int nextKeyPhase = current.getKeyPhase() == 0 ? 1 : 0;
            // toggle the 1 bit keyphase
            final QuicWriteCipher next =
                    QuicCipher.createWriteCipher(current.getCipherSuite(),
                            nplus1Secret, quicKeys.key, quicKeys.iv,
                            quicKeys.hp, nextKeyPhase);
            return next;
        }

        private static QuicReadCipher generateNextReadCipher(
                final QuicVersion quicVersion, final QuicReadCipher current)
                throws IOException, GeneralSecurityException {
            final SSLKeyDerivation kd =
                    new QuicTLSKeyDerivation(current.getCipherSuite(),
                            current.getBaseSecret());
            final QuicTLSData tlsData = QuicKeyManager.getQuicData(quicVersion);
            final SecretKey nPlus1Secret =
                    kd.deriveKey(tlsData.getTlsKeyUpdateLabel());
            final QuicKeys quicKeys =
                    QuicKeyManager.deriveQuicKeys(quicVersion,
                            current.getCipherSuite(),
                            nPlus1Secret, current.getHeaderProtectionKey());
            final int nextKeyPhase = current.getKeyPhase() == 0 ? 1 : 0;
            // toggle the 1 bit keyphase
            final QuicReadCipher next =
                    QuicCipher.createReadCipher(current.getCipherSuite(),
                            nPlus1Secret, quicKeys.key,
                            quicKeys.iv, quicKeys.hp, nextKeyPhase);
            return next;
        }

        private KeySeries generateNextKeys(final QuicVersion version,
                final KeySeries currentSeries)
                throws GeneralSecurityException, IOException {
            this.keySeriesLock.lock();
            try {
                // nothing to do if some other thread
                // already changed the keySeries
                if (this.keySeries != currentSeries) {
                    return this.keySeries;
                }
                final QuicReadCipher nPlus1ReadCipher =
                        generateNextReadCipher(version,
                                currentSeries.current.readCipher);
                final QuicWriteCipher nPlus1WriteCipher =
                        generateNextWriteCipher(version,
                                currentSeries.current.writeCipher);
                // only the next keys will differ in the new series
                // as compared to the current series
                final KeySeries newSeries = new KeySeries(currentSeries.old,
                        currentSeries.current,
                        new CipherPair(nPlus1ReadCipher, nPlus1WriteCipher));
                this.keySeries = newSeries;
                return newSeries;
            } finally {
                this.keySeriesLock.unlock();
            }
        }

        /**
         * Updates the key series by "left shifting" the series of keys.
         * i.e. old keys (if any) are discarded, current keys
         * are moved to old keys and next keys are moved to current keys.
         * Note that no new keys will be generated by this method.
         * @return the key series that will be in use going forward
         */
        private KeySeries rolloverKeys(final QuicVersion version,
                final KeySeries currentSeries) {
            this.keySeriesLock.lock();
            try {
                // nothing to do if some other thread
                // already changed the keySeries
                if (this.keySeries != currentSeries) {
                    return this.keySeries;
                }
                assert currentSeries.next != null : "Key series missing next" +
                        " keys";
                // discard the old read cipher which will no longer be used
                final QuicReadCipher oldReadCipher = currentSeries.old;
                // once we move current key to old, we won't be using the
                // write cipher of that
                // moved pair
                final QuicWriteCipher writeCipherToDiscard =
                        currentSeries.current.writeCipher;
                final KeySeries newSeries = new KeySeries(
                        currentSeries.current.readCipher, currentSeries.next,
                        null);
                // update the key series
                this.keySeries = newSeries;
                if (oldReadCipher != null) {
                    if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                        SSLLogger.finest(
                                "discarding old read key of key phase: " +
                                oldReadCipher.getKeyPhase());
                    }
                    oldReadCipher.discard(false);
                }
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.finest("discarding write key of key phase: " +
                            writeCipherToDiscard.getKeyPhase());
                }
                writeCipherToDiscard.discard(false);
                return newSeries;
            } finally {
                this.keySeriesLock.unlock();
            }
        }
    }

    private static final class QuicTLSKeyDerivation
            implements SSLKeyDerivation {

        private enum HkdfLabel {
            // RFC 9001: quic version 1
            quickey("quic key"),
            quiciv("quic iv"),
            quichp("quic hp"),
            quicku("quic ku"),

            // RFC 9369: quic version 2
            quicv2key("quicv2 key"),
            quicv2iv("quicv2 iv"),
            quicv2hp("quicv2 hp"),
            quicv2ku("quicv2 ku");

            private final String label;
            private final byte[] tls13LabelBytes;

            HkdfLabel(final String label) {
                Objects.requireNonNull(label);
                this.label = label;
                this.tls13LabelBytes =
                        ("tls13 " + label).getBytes(StandardCharsets.UTF_8);
            }

            private static HkdfLabel fromLabel(final String label) {
                Objects.requireNonNull(label);
                for (final HkdfLabel hkdfLabel : HkdfLabel.values()) {
                    if (hkdfLabel.label.equals(label)) {
                        return hkdfLabel;
                    }
                }
                throw new IllegalArgumentException(
                        "unrecognized label: " + label);
            }
        }

        private final CipherSuite cs;
        private final SecretKey secret;

        private QuicTLSKeyDerivation(final CipherSuite cs,
                final SecretKey secret) {
            this.cs = Objects.requireNonNull(cs);
            this.secret = Objects.requireNonNull(secret);
        }

        @Override
        public SecretKey deriveKey(final String algorithm) throws IOException {
            final HkdfLabel hkdfLabel = HkdfLabel.fromLabel(algorithm);
            try {
                final KDF hkdf = KDF.getInstance(this.cs.hashAlg.hkdfAlgorithm);
                final int keyLength = getKeyLength(hkdfLabel);
                final byte[] hkdfInfo =
                        createHkdfInfo(hkdfLabel.tls13LabelBytes, keyLength);
                final String keyAlgo = getKeyAlgorithm(hkdfLabel);
                return hkdf.deriveKey(keyAlgo,
                        HKDFParameterSpec.expandOnly(
                                secret, hkdfInfo, keyLength));
            } catch (GeneralSecurityException gse) {
                throw new SSLHandshakeException("Could not derive key", gse);
            }
        }

        @Override
        public byte[] deriveData(final String algorithm) throws IOException {
            final HkdfLabel hkdfLabel = HkdfLabel.fromLabel(algorithm);
            try {
                final KDF hkdf = KDF.getInstance(this.cs.hashAlg.hkdfAlgorithm);
                final int keyLength = getKeyLength(hkdfLabel);
                final byte[] hkdfInfo =
                        createHkdfInfo(hkdfLabel.tls13LabelBytes, keyLength);
                return hkdf.deriveData(HKDFParameterSpec.expandOnly(
                        secret, hkdfInfo, keyLength));
            } catch (GeneralSecurityException gse) {
                throw new SSLHandshakeException("Could not derive key", gse);
            }
        }

        private int getKeyLength(final HkdfLabel hkdfLabel) {
            return switch (hkdfLabel) {
                case quicku, quicv2ku -> {
                    // RFC-9001, section 6.1:
                    // secret_<n+1> = HKDF-Expand-Label(secret_<n>, "quic
                    // ku", "", Hash.length)
                    yield this.cs.hashAlg.hashLength;
                }
                case quiciv, quicv2iv -> this.cs.bulkCipher.ivSize;
                default -> this.cs.bulkCipher.keySize;
            };
        }

        private String getKeyAlgorithm(final HkdfLabel hkdfLabel) {
            return switch (hkdfLabel) {
                case quicku, quicv2ku -> "TlsUpdateNplus1";
                case quiciv, quicv2iv ->
                        throw new IllegalArgumentException("IV not expected");
                default -> this.cs.bulkCipher.algorithm;
            };
        }
    }

    private enum QuicTLSData {
        V1("38762cf7f55934b34d179ae6a4c80cadccbb7f0a",
                "be0c690b9f66575a1d766b54e368c84e",
                "461599d35d632bf2239825bb",
                "quic key", "quic iv", "quic hp", "quic ku"),
        V2("0dede3def700a6db819381be6e269dcbf9bd2ed9",
                "8fb4b01b56ac48e260fbcbcead7ccc92",
                "d86969bc2d7c6d9990efb04a",
                "quicv2 key", "quicv2 iv", "quicv2 hp", "quicv2 ku");

        private final byte[] initialSalt;
        private final SecretKey retryKey;
        private final GCMParameterSpec retryIvSpec;
        private final String keyLabel;
        private final String ivLabel;
        private final String hpLabel;
        private final String kuLabel;

        QuicTLSData(String initialSalt, String retryKey, String retryIv,
                     String keyLabel, String ivLabel, String hpLabel,
                     String kuLabel) {
            this.initialSalt = HexFormat.of()
                    .parseHex(initialSalt);
            this.retryKey = new SecretKeySpec(HexFormat.of()
                    .parseHex(retryKey), "AES");
            retryIvSpec = new GCMParameterSpec(128,
                    HexFormat.of().parseHex(retryIv));
            this.keyLabel = keyLabel;
            this.ivLabel = ivLabel;
            this.hpLabel = hpLabel;
            this.kuLabel = kuLabel;
        }

        public byte[] getInitialSalt() {
            return initialSalt;
        }

        public Cipher getRetryCipher(boolean incoming) throws QuicTransportException {
            Cipher retryCipher = null;
            try {
                retryCipher = Cipher.getInstance("AES/GCM/NoPadding");
                retryCipher.init(incoming ? Cipher.DECRYPT_MODE :
                                Cipher.ENCRYPT_MODE,
                        retryKey, retryIvSpec);
            } catch (Exception e) {
                throw new QuicTransportException("Cipher not available",
                        null, 0, BASE_CRYPTO_ERROR + Alert.INTERNAL_ERROR.id, e);
            }
            return retryCipher;
        }

        public String getTlsKeyLabel() {
            return keyLabel;
        }

        public String getTlsIvLabel() {
            return ivLabel;
        }

        public String getTlsHpLabel() {
            return hpLabel;
        }

        public String getTlsKeyUpdateLabel() {
            return kuLabel;
        }
    }
}
