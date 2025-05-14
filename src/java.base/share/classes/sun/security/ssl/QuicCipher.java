/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.security.auth.DestroyFailedException;

import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import static jdk.internal.net.quic.QuicTLSEngine.KeySpace.ONE_RTT;

abstract class QuicCipher {
    private static final String
            SEC_PROP_QUIC_TLS_KEY_LIMITS = "jdk.quic.tls.keyLimits";

    private static final Map<String, Long> KEY_LIMITS;

    static {
        final String propVal = Security.getProperty(
                                SEC_PROP_QUIC_TLS_KEY_LIMITS);
        if (propVal == null) {
            KEY_LIMITS = Map.of(); // no specific limits
        } else {
            final Map<String, Long> limits = new HashMap<>();
            for (final String entry : propVal.split(",")) {
                // each entry is of the form <cipher> <limit>
                // example:
                // AES/GCM/NoPadding 2^23
                // ChaCha20-Poly1305 -1
                final String[] parts = entry.trim().split(" ");
                if (parts.length != 2) {
                    // TODO: exception type
                    throw new RuntimeException("invalid value for "
                            + SEC_PROP_QUIC_TLS_KEY_LIMITS
                            + " security property");
                }
                final String cipher = parts[0];
                if (limits.containsKey(cipher)) {
                    throw new RuntimeException(
                            "key limit defined more than once for cipher "
                            + cipher);
                }
                final String limitVal = parts[1];
                final long limit;
                final int index = limitVal.indexOf("^");
                if (index >= 0) {
                    // of the form x^y (example: 2^23)
                    limit = (long) Math.pow(
                            Integer.parseInt(limitVal.substring(0, index)),
                            Integer.parseInt(limitVal.substring(index + 1)));
                } else {
                    limit = Long.parseLong(limitVal);
                }
                if (limit == 0 || limit < -1) {
                    // we allow -1 to imply no limits, but any other zero
                    // or negative value is invalid
                    // TODO: exception type
                    throw new RuntimeException("invalid value for "
                            + SEC_PROP_QUIC_TLS_KEY_LIMITS
                            + " security property");
                }
                limits.put(cipher, limit);
            }
            KEY_LIMITS = Collections.unmodifiableMap(limits);
        }
    }

    private final CipherSuite cipherSuite;
    private final QuicHeaderProtectionCipher hpCipher;
    private final SecretKey secret;
    private final int keyPhase;

    protected QuicCipher(final CipherSuite cipherSuite, final SecretKey secret,
            final QuicHeaderProtectionCipher hpCipher, final int keyPhase) {
        assert keyPhase == 0 || keyPhase == 1 :
                "invalid key phase: " + keyPhase;
        this.cipherSuite = cipherSuite;
        this.secret = secret;
        this.hpCipher = hpCipher;
        this.keyPhase = keyPhase;
    }

    final SecretKey getSecret() {
        return this.secret;
    }

    final CipherSuite getCipherSuite() {
        return this.cipherSuite;
    }

    final SecretKey getHeaderProtectionKey() {
        return this.hpCipher.headerProtectionKey;
    }

    final ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) {
        return hpCipher.computeHeaderProtectionMask(sample);
    }

    final int getKeyPhase() {
        return this.keyPhase;
    }

    final void discard() {
        safeDiscard(this.secret);
        this.hpCipher.discard();
        this.doDiscard();
    }

    protected abstract void doDiscard();

    static QuicReadCipher createReadCipher(final CipherSuite cipherSuite,
            final SecretKey secret, final SecretKey key,
            final byte[] iv, final SecretKey hp,
            final int keyPhase) throws GeneralSecurityException {
        return switch (cipherSuite) {
            case TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384 ->
                    new T13GCMReadCipher(
                            cipherSuite, secret, key, iv, hp, keyPhase);
            case TLS_CHACHA20_POLY1305_SHA256 ->
                    new T13CC20P1305ReadCipher(
                            cipherSuite, secret, key, iv, hp, keyPhase);
            default -> throw new IllegalArgumentException("Cipher suite "
                    + cipherSuite + " not supported");
        };
    }

    static QuicWriteCipher createWriteCipher(final CipherSuite cipherSuite,
            final SecretKey secret, final SecretKey key,
            final byte[] iv, final SecretKey hp,
            final int keyPhase) throws GeneralSecurityException {
        return switch (cipherSuite) {
            case TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384 ->
                    new T13GCMWriteCipher(cipherSuite, secret, key, iv, hp,
                            keyPhase);
            case TLS_CHACHA20_POLY1305_SHA256 ->
                    new T13CC20P1305WriteCipher(cipherSuite, secret, key, iv,
                            hp, keyPhase);
            default -> throw new IllegalArgumentException("Cipher suite "
                    + cipherSuite + " not supported");
        };
    }

    static void safeDiscard(final SecretKey secretKey) {
        try {
            secretKey.destroy();
        } catch (DestroyFailedException | SecurityException e) {
            // ignore
            // TODO: log this
        }
    }

    abstract static class QuicReadCipher extends QuicCipher {
        private final AtomicLong lowestDecryptedPktNum = new AtomicLong(-1);

        QuicReadCipher(CipherSuite cipherSuite, SecretKey secret,
                       QuicHeaderProtectionCipher hpCipher, int keyPhase) {
            super(cipherSuite, secret, hpCipher, keyPhase);
        }

        final void decryptPacket(long packetNumber, ByteBuffer packet,
                int headerLength, ByteBuffer output) throws AEADBadTagException {
            doDecrypt(packetNumber, packet, headerLength, output);
            boolean updated;
            do {
                final long current = lowestDecryptedPktNum.get();
                assert packetNumber >= 0 :
                        "unexpected packet number: " + packetNumber;
                final long newLowest = current == -1 ? packetNumber :
                        Math.min(current, packetNumber);
                updated = lowestDecryptedPktNum.compareAndSet(current,
                        newLowest);
            } while (!updated);
        }

        protected abstract void doDecrypt(long packetNumber,
                ByteBuffer packet, int headerLength, ByteBuffer output)
                throws AEADBadTagException;

        /**
         * Returns the maximum limit on the number of packets that fail
         * decryption, across all key (updates), using this
         * {@code QuicReadCipher}. This method must not return a value less
         * than 0.
         *
         * @return the limit
         */
        // RFC-9001, section 6.6
        abstract long integrityLimit();

        /**
         * {@return the lowest packet number that this {@code QuicReadCipher}
         * has decrypted. If no packets have yet been decrypted by this
         * instance, then this method returns -1}
         */
        final long lowestDecryptedPktNum() {
            return this.lowestDecryptedPktNum.get();
        }

        /**
         * {@return true if this {@code QuicReadCipher} has successfully
         * decrypted any packet sent by the peer, else returns false}
         */
        final boolean hasDecryptedAny() {
            return this.lowestDecryptedPktNum.get() != -1;
        }

        @Override
        protected abstract void doDiscard();
    }

    abstract static class QuicWriteCipher extends QuicCipher {
        private final AtomicLong numPacketsEncrypted = new AtomicLong();
        private final AtomicLong lowestEncryptedPktNum = new AtomicLong(-1);

        QuicWriteCipher(CipherSuite cipherSuite, SecretKey secret,
                QuicHeaderProtectionCipher hpCipher, int keyPhase) {
            super(cipherSuite, secret, hpCipher, keyPhase);
        }

        final void encryptPacket(final long packetNumber,
                final ByteBuffer packetHeader,
                final ByteBuffer packetPayload,
                final ByteBuffer output) throws QuicTransportException {
            final long confidentialityLimit = confidentialityLimit();
            final long numEncrypted = this.numPacketsEncrypted.get();
            if (confidentialityLimit > 0 &&
                    numEncrypted > confidentialityLimit) {
                // the OneRttKeyManager is responsible for detecting and
                // initiating a key update before this limit is hit. The fact
                // that we hit this limit indicates that either the key
                // update wasn't initiated or the key update failed. In
                // either case we just throw an exception which
                // should lead to the connection being closed as required by
                // RFC-9001, section 6.6:
                // If a key update is not possible or integrity limits are
                // reached, the endpoint MUST stop using the connection and
                // only send stateless resets in response to receiving
                // packets. It is RECOMMENDED that endpoints immediately
                // close the connection with a connection error of type
                // AEAD_LIMIT_REACHED before reaching a state where key
                // updates are not possible.
                throw new QuicTransportException("confidentiality limit " +
                        "reached", ONE_RTT, 0,
                        QuicTransportErrors.AEAD_LIMIT_REACHED);
            }
            this.numPacketsEncrypted.incrementAndGet();
            doEncryptPacket(packetNumber, packetHeader, packetPayload, output);
            boolean updated;
            do {
                final long current = lowestEncryptedPktNum.get();
                assert packetNumber >= 0 :
                        "unexpected packet number: " + packetNumber;
                final long newLowest = current == -1 ? packetNumber :
                        Math.min(current, packetNumber);
                updated = lowestEncryptedPktNum.compareAndSet(current,
                        newLowest);
            } while (!updated);
        }

        /**
         * {@return the lowest packet number that this {@code QuicWriteCipher}
         * has encrypted. If no packets have yet been encrypted by this
         * instance, then this method returns -1}
         */
        final long lowestEncryptedPktNum() {
            return this.lowestEncryptedPktNum.get();
        }

        /**
         * {@return true if this {@code QuicWriteCipher} has successfully
         * encrypted any packet to send to the peer, else returns false}
         */
        final boolean hasEncryptedAny() {
            // rely on the lowestEncryptedPktNum field instead of the
            // numPacketsEncrypted field. this avoids a race where the
            // lowestEncryptedPktNum() might return a value contradicting
            // the return value of this method.
            return this.lowestEncryptedPktNum.get() != -1;
        }

        /**
         * {@return the number of packets encrypted by this {@code
         * QuicWriteCipher}}
         */
        final long getNumEncrypted() {
            return this.numPacketsEncrypted.get();
        }

        abstract void doEncryptPacket(long packetNumber, ByteBuffer packetHeader,
                                      ByteBuffer packetPayload, ByteBuffer output);

        /**
         * Returns the maximum limit on the number of packets that are allowed
         * to be encrypted with this instance of {@code QuicWriteCipher}. A
         * value less than 0 implies that there's no limit.
         *
         * @return the limit or -1
         */
        // RFC-9001, section 6.6: The confidentiality limit applies to the
        // number of
        // packets encrypted with a given key.
        abstract long confidentialityLimit();

        @Override
        protected abstract void doDiscard();
    }

    abstract static class QuicHeaderProtectionCipher {
        protected final SecretKey headerProtectionKey;

        protected QuicHeaderProtectionCipher(
                final SecretKey headerProtectionKey) {
            this.headerProtectionKey = headerProtectionKey;
        }

        int getHeaderProtectionSampleSize() {
            return 16;
        }

        abstract ByteBuffer computeHeaderProtectionMask(ByteBuffer sample);

        final void discard() {
            safeDiscard(this.headerProtectionKey);
        }
    }

    static final class T13GCMReadCipher extends QuicReadCipher {
        // RFC-9001, section 6.6: For AEAD_AES_128_GCM and AEAD_AES_256_GCM,
        // the integrity limit is 2^52 invalid packets
        private static final long INTEGRITY_LIMIT = 1L << 52;

        private final Cipher cipher;
        private final SecretKey key;
        private final byte[] iv;

        T13GCMReadCipher(final CipherSuite cipherSuite, final SecretKey secret,
                final SecretKey key, final byte[] iv, final SecretKey hp,
                final int keyPhase)
                throws GeneralSecurityException {
            super(cipherSuite, secret, new T13AESHPCipher(hp), keyPhase);
            this.key = key;
            this.iv = iv;
            this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
        }

        @Override
        protected void doDecrypt(long packetNumber, ByteBuffer packet,
                                 int headerLength, ByteBuffer output) throws AEADBadTagException {
            byte[] iv = this.iv.clone();

            // apply packet number to IV
            int i = 11;
            while (packetNumber > 0) {
                iv[i] ^= (byte) (packetNumber & 0xFF);
                packetNumber = packetNumber >>> 8;
                i--;
            }
            final GCMParameterSpec ivSpec = new GCMParameterSpec(128, iv);
            synchronized (cipher) {
                try {
                    cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
                } catch (InvalidKeyException |
                         InvalidAlgorithmParameterException e) {
                    throw new AssertionError("Should never happen", e);
                }

                try {
                    int limit = packet.limit();
                    packet.limit(packet.position() + headerLength);
                    cipher.updateAAD(packet);
                    packet.limit(limit);
                    cipher.doFinal(packet, output);
                } catch (AEADBadTagException e) {
                    throw e;
                } catch (IllegalBlockSizeException | BadPaddingException |
                         ShortBufferException e) {
                    throw new AssertionError("Should never happen", e);
                }
            }
        }

        @Override
        long integrityLimit() {
            return INTEGRITY_LIMIT;
        }

        @Override
        protected final void doDiscard() {
            safeDiscard(this.key);
        }
    }

    static final class T13GCMWriteCipher extends QuicWriteCipher {
        private static final String CIPHER_ALGORITHM_NAME = "AES/GCM/NoPadding";
        private static final long CONFIDENTIALITY_LIMIT;

        static {
            // RFC-9001, section 6.6: For AEAD_AES_128_GCM and AEAD_AES_256_GCM,
            // the confidentiality limit is 2^23 encrypted packets
            final long defaultVal = 1 << 23;
            long limit =
                    KEY_LIMITS.getOrDefault(CIPHER_ALGORITHM_NAME, defaultVal);
            // don't allow the configuration to increase the confidentiality
            // limit, but only let it lower the limit
            limit = limit > defaultVal ? defaultVal : limit;
            CONFIDENTIALITY_LIMIT = limit;
        }

        private final SecretKey key;
        private final Cipher cipher;
        private final byte[] iv;

        T13GCMWriteCipher(final CipherSuite cipherSuite, final SecretKey secret,
                final SecretKey key, final byte[] iv, final SecretKey hp,
                final int keyPhase) throws GeneralSecurityException {
            super(cipherSuite, secret, new T13AESHPCipher(hp), keyPhase);
            this.key = key;
            this.iv = iv;
            this.cipher = Cipher.getInstance(CIPHER_ALGORITHM_NAME);
        }

        @Override
        void doEncryptPacket(long packetNumber, ByteBuffer packetHeader,
                             ByteBuffer packetPayload, ByteBuffer output) {
            byte[] iv = this.iv.clone();

            // apply packet number to IV
            int i = 11;
            while (packetNumber > 0) {
                iv[i] ^= (byte) (packetNumber & 0xFF);
                packetNumber = packetNumber >>> 8;
                i--;
            }
            final GCMParameterSpec ivSpec = new GCMParameterSpec(128, iv);
            synchronized (cipher) {
                try {
                    cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
                } catch (InvalidKeyException |
                         InvalidAlgorithmParameterException e) {
                    throw new AssertionError("Should never happen", e);
                }
                try {
                    cipher.updateAAD(packetHeader);
                    cipher.doFinal(packetPayload, output);
                } catch (IllegalBlockSizeException | BadPaddingException |
                         ShortBufferException e) {
                    throw new AssertionError("Should never happen", e);
                }
            }
        }

        @Override
        long confidentialityLimit() {
            return CONFIDENTIALITY_LIMIT;
        }

        @Override
        protected final void doDiscard() {
            safeDiscard(this.key);
        }
    }

    static final class T13AESHPCipher extends QuicHeaderProtectionCipher {
        private final Cipher cipher;

        T13AESHPCipher(SecretKey hp) throws GeneralSecurityException {
            super(hp);
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
        }

        @Override
        public ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) {
            if (sample.remaining() != getHeaderProtectionSampleSize()) {
                throw new IllegalArgumentException("Invalid sample size");
            }
            ByteBuffer output = ByteBuffer.allocate(sample.remaining());
            try {
                synchronized (cipher) {
                    // Some providers (Jipher) don't re-initialize the cipher
                    // after doFinal, and need init every time.
                    cipher.init(Cipher.ENCRYPT_MODE, headerProtectionKey);
                    cipher.doFinal(sample, output);
                }
                output.flip();
                assert output.remaining() >= 5;
                return output;
            } catch (IllegalBlockSizeException | BadPaddingException |
                     ShortBufferException | InvalidKeyException e) {
                throw new AssertionError("Should never happen", e);
            }
        }
    }

    static final class T13CC20P1305ReadCipher extends QuicReadCipher {
        // RFC-9001, section 6.6: For AEAD_CHACHA20_POLY1305,
        // the integrity limit is 2^36 invalid packets
        private static final long INTEGRITY_LIMIT = 1L << 36;

        private final SecretKey key;
        private final Cipher cipher;
        private final byte[] iv;

        T13CC20P1305ReadCipher(final CipherSuite cipherSuite,
                final SecretKey secret, final SecretKey key,
                final byte[] iv, final SecretKey hp, final int keyPhase)
                throws GeneralSecurityException {
            super(cipherSuite, secret, new T13CC20HPCipher(hp), keyPhase);
            this.key = key;
            this.iv = iv;
            this.cipher = Cipher.getInstance("ChaCha20-Poly1305");
        }

        @Override
        protected void doDecrypt(long packetNumber, ByteBuffer packet,
                                 int headerLength, ByteBuffer output) throws AEADBadTagException {
            byte[] iv = this.iv.clone();

            // apply packet number to IV
            int i = 11;
            while (packetNumber > 0) {
                iv[i] ^= (byte) (packetNumber & 0xFF);
                packetNumber = packetNumber >>> 8;
                i--;
            }
            final IvParameterSpec ivSpec = new IvParameterSpec(iv);
            synchronized (cipher) {
                try {
                    cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
                } catch (InvalidKeyException |
                         InvalidAlgorithmParameterException e) {
                    throw new AssertionError("Should never happen", e);
                }

                try {
                    int limit = packet.limit();
                    packet.limit(packet.position() + headerLength);
                    cipher.updateAAD(packet);
                    packet.limit(limit);
                    cipher.doFinal(packet, output);
                } catch (AEADBadTagException e) {
                    throw e;
                } catch (IllegalBlockSizeException | BadPaddingException |
                         ShortBufferException e) {
                    throw new AssertionError("Should never happen", e);
                }
            }
        }

        @Override
        long integrityLimit() {
            return INTEGRITY_LIMIT;
        }

        @Override
        protected final void doDiscard() {
            safeDiscard(this.key);
        }
    }

    static final class T13CC20P1305WriteCipher extends QuicWriteCipher {
        private static final String CIPHER_ALGORITHM_NAME = "ChaCha20-Poly1305";
        private static final long CONFIDENTIALITY_LIMIT;

        static {
            // RFC-9001, section 6.6: For AEAD_CHACHA20_POLY1305, the
            // confidentiality limit is greater than the number of possible
            // packets (2^62) and so can be disregarded.
            final long defaultVal = -1; // no limit
            long limit =
                    KEY_LIMITS.getOrDefault(CIPHER_ALGORITHM_NAME, defaultVal);
            limit = limit < 0 ? -1 /* no limit */ : limit;
            CONFIDENTIALITY_LIMIT = limit;
        }

        private final SecretKey key;
        private final Cipher cipher;
        private final byte[] iv;

        T13CC20P1305WriteCipher(final CipherSuite cipherSuite,
                                final SecretKey secret, final SecretKey key,
                                final byte[] iv, final SecretKey hp,
                                final int keyPhase)
                throws GeneralSecurityException {
            super(cipherSuite, secret, new T13CC20HPCipher(hp), keyPhase);
            this.key = key;
            this.iv = iv;
            this.cipher = Cipher.getInstance(CIPHER_ALGORITHM_NAME);
        }

        @Override
        void doEncryptPacket(final long packetNumber, final ByteBuffer packetHeader,
                             final ByteBuffer packetPayload, final ByteBuffer output) {
            byte[] iv = this.iv.clone();

            // apply packet number to IV
            int i = 11;
            long pn = packetNumber;
            while (pn > 0) {
                iv[i] ^= (byte) (pn & 0xFF);
                pn = pn >>> 8;
                i--;
            }
            final IvParameterSpec ivSpec = new IvParameterSpec(iv);
            synchronized (cipher) {
                try {
                    cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
                } catch (InvalidKeyException |
                         InvalidAlgorithmParameterException e) {
                    throw new AssertionError("Should never happen", e);
                }
                try {
                    cipher.updateAAD(packetHeader);
                    cipher.doFinal(packetPayload, output);
                } catch (IllegalBlockSizeException | BadPaddingException |
                         ShortBufferException e) {
                    throw new AssertionError("Should never happen", e);
                }
            }
        }

        @Override
        long confidentialityLimit() {
            return CONFIDENTIALITY_LIMIT;
        }

        @Override
        protected final void doDiscard() {
            safeDiscard(this.key);
        }
    }

    static final class T13CC20HPCipher extends QuicHeaderProtectionCipher {
        private final Cipher cipher;

        T13CC20HPCipher(final SecretKey hp) throws GeneralSecurityException {
            super(hp);
            cipher = Cipher.getInstance("ChaCha20");
        }

        @Override
        public ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) {
            if (sample.remaining() != getHeaderProtectionSampleSize()) {
                throw new IllegalArgumentException("Invalid sample size");
            }
            try {
                // RFC 7539: [counter is a] 32-bit block count parameter,
                // treated as a 32-bit little-endian integer
                // RFC 9001:
                //  counter = sample[0..3]
                //  nonce = sample[4..15]
                //  mask = ChaCha20(hp_key, counter, nonce, {0,0,0,0,0})

                sample.order(ByteOrder.LITTLE_ENDIAN);
                byte[] nonce = new byte[12];
                int counter = sample.getInt();
                sample.get(nonce);
                ChaCha20ParameterSpec ivSpec =
                        new ChaCha20ParameterSpec(nonce, counter);
                byte[] output = new byte[5];

                synchronized (cipher) {
                    // DECRYPT produces the same output as ENCRYPT, but does
                    // not throw when the same IV is used repeatedly
                    cipher.init(Cipher.DECRYPT_MODE, headerProtectionKey,
                            ivSpec);
                    int numBytes = cipher.doFinal(output, 0, 5, output);
                    assert numBytes == 5;
                }
                return ByteBuffer.wrap(output);
            } catch (IllegalBlockSizeException | BadPaddingException |
                     ShortBufferException | InvalidKeyException |
                     InvalidAlgorithmParameterException e) {
                throw new AssertionError("Should never happen", e);
            }
        }
    }
}
