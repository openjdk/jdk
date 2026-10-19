/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.DecapsulateException;
import javax.crypto.KDF;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;

import sun.security.util.KeyUtil;

/**
 * A common class for creating various KeyDerivation types.
 */
public class KAKeyDerivation implements SSLKeyDerivation {

    // Algorithm used to derive TLS 1.3 shared secrets
    private static final String t13KeyDerivationAlgorithm =
            System.getProperty("jdk.tls.t13KeyDerivationAlgorithm", "Generic");
    private final String algorithmName;
    private final HandshakeContext context;
    private final PrivateKey localPrivateKey;
    private final PublicKey peerPublicKey;
    private final byte[] keyshare;
    private final NamedGroup namedGroup;

    // Constructor called by Key Agreement
    KAKeyDerivation(String algorithmName,
            HandshakeContext context,
            PrivateKey localPrivateKey,
            PublicKey peerPublicKey) {
        this(algorithmName, null, context, localPrivateKey,
                peerPublicKey, null);
    }

    // When the constructor called by KEM: store the client's public key or the
    // encapsulated message in keyshare.
    KAKeyDerivation(String algorithmName,
                    NamedGroup namedGroup,
                    HandshakeContext context,
                    PrivateKey localPrivateKey,
                    PublicKey peerPublicKey,
                    byte[] keyshare) {
        this.algorithmName = algorithmName;
        this.context = context;
        this.localPrivateKey = localPrivateKey;
        this.peerPublicKey = peerPublicKey;
        this.keyshare = keyshare;
        this.namedGroup = namedGroup;
    }

    @Override
    public SecretKey deriveKey(String type) throws IOException {
        if (!context.negotiatedProtocol.useTLS13PlusSpec()) {
            return t12DeriveKey();
        } else {
            return t13DeriveKey(type);
        }
    }

    /**
     * Handle the TLSv1-1.2 objects, which don't use the HKDF algorithms.
     */
    private SecretKey t12DeriveKey() throws IOException {
        SecretKey preMasterSecret = null;
        try {
            KeyAgreement ka = KeyAgreement.getInstance(algorithmName);
            ka.init(localPrivateKey);
            ka.doPhase(peerPublicKey, true);
            preMasterSecret = ka.generateSecret("TlsPremasterSecret");
            SSLMasterKeyDerivation mskd =
                    SSLMasterKeyDerivation.valueOf(context.negotiatedProtocol);
            if (mskd == null) {
                // unlikely
                throw new SSLHandshakeException(
                        "No expected master key derivation for protocol: "
                        + context.negotiatedProtocol.name);
            }
            SSLKeyDerivation kd = mskd.createKeyDerivation(
                    context, preMasterSecret);
            return kd.deriveKey("MasterSecret");
        } catch (GeneralSecurityException gse) {
            throw new SSLHandshakeException("Could not generate secret", gse);
        } finally {
            KeyUtil.destroySecretKeys(preMasterSecret);
        }
    }

    private SecretKey deriveHandshakeSecret(String label,
            SecretKey sharedSecret)
            throws GeneralSecurityException, IOException {
        SecretKey earlySecret = null;
        SecretKey saltSecret = null;

        CipherSuite.HashAlg hashAlg = context.negotiatedCipherSuite.hashAlg;
        SSLKeyDerivation kd = context.handshakeKeyDerivation;
        try {
            if (kd == null) {   // No PSK is in use.
                // If PSK is not in use, Early Secret will still be
                // HKDF-Extract(0, 0).
                byte[] zeros = new byte[hashAlg.hashLength];
                KDF hkdf = KDF.getInstance(hashAlg.hkdfAlgorithm);
                earlySecret = hkdf.deriveKey("TlsEarlySecret",
                        HKDFParameterSpec.ofExtract().addSalt(zeros)
                        .addIKM(zeros).extractOnly());
                kd = new SSLSecretDerivation(context, earlySecret);
            }

            // derive salt secret
            saltSecret = kd.deriveKey("TlsSaltSecret");

            // derive handshake secret
            // NOTE: do not reuse the HKDF object for "TlsEarlySecret" for
            // the handshake secret key derivation (below) as it may not
            // work with the "sharedSecret" obj.
            KDF hkdf = getTls13KDF(hashAlg, sharedSecret);
            var spec = HKDFParameterSpec.ofExtract().addSalt(saltSecret)
                    .addIKM(sharedSecret);

            return hkdf.deriveKey(label, spec.extractOnly());
        } finally {
            KeyUtil.destroySecretKeys(earlySecret, saltSecret);
        }
    }

    private Provider getKEMProvider() {
        return (namedGroup != null) ? namedGroup.getKEMProvider() : null;
    }

    private KDF getTls13KDF(CipherSuite.HashAlg hashAlg, SecretKey ikm)
            throws GeneralSecurityException {
        if (ikm != null && !"RAW".equalsIgnoreCase(ikm.getFormat())) {
            Provider provider = getKEMProvider();
            if (provider != null) {
                // A non-RAW secret from a KEM can be provider-opaque. It
                // must be consumed by a KDF supplied by the provider that
                // created it.
                return KDF.getInstance(hashAlg.hkdfAlgorithm, provider);
            }
        }

        // A RAW secret such as a TLS hybrid KEM secret, can be consumed by
        // an HKDF selected through normal JCA provider preference. This also
        // preserves the existing behavior for traditional key agreement.
        return KDF.getInstance(hashAlg.hkdfAlgorithm);
    }

    /**
     * This method is called by the server to perform KEM encapsulation.
     * It uses the client's public key (sent by the client as a keyshare)
     * to encapsulate a shared secret and returns the encapsulated message.
     *
     * Package-private, used from KeyShareExtension.SHKeyShareProducer::
     * produce().
     */
    KEM.Encapsulated encapsulate(String algorithm, SecureRandom random)
            throws IOException {
        SecretKey sharedSecret = null;

        if (keyshare == null) {
            throw new IOException("No keyshare available for KEM " +
                    "encapsulation");
        }

        // All exceptions thrown during KEM encapsulation are mapped
        // to TLS fatal alerts:
        // illegal_parameter alert or internal_error alert.
        try {
            // Hybrid TLS keys have no X.509 encoding. The KEM receives the
            // TLS key_share directly as a RAW public key.
            PublicKey pk = KeyUtil.newRawPublicKey(algorithmName, keyshare);
            Provider provider = getKEMProvider();
            KEM kem = (provider != null) ?
                    KEM.getInstance(algorithmName, provider) :
                    KEM.getInstance(algorithmName);
            KEM.Encapsulator e = kem.newEncapsulator(pk, random);
            KEM.Encapsulated enc = e.encapsulate();
            sharedSecret = enc.key();

            SecretKey derived = deriveHandshakeSecret(algorithm, sharedSecret);

            return new KEM.Encapsulated(derived, enc.encapsulation(), null);
        } catch (IllegalArgumentException | InvalidKeyException e) {
            // Peer validation failure
            // ECDH all-zero shared secret (RFC 8446 section 7.4.2),
            // ML-KEM encapsulation key check failure (FIPS-203 section 7.2)
            throw context.conContext.fatal(Alert.ILLEGAL_PARAMETER, e);
        } catch (GeneralSecurityException e) {
            // Cryptographic failure,
            // deriveHandshakeSecret failure.
            throw context.conContext.fatal(Alert.INTERNAL_ERROR, e);
        } catch (RuntimeException e) {
            // unexpected provider/runtime failure
            throw context.conContext.fatal(Alert.INTERNAL_ERROR, e);
        } finally {
            KeyUtil.destroySecretKeys(sharedSecret);
        }
    }

    /**
     * Handle the TLSv1.3 objects, which use the HKDF algorithms.
     */
    private SecretKey t13DeriveKey(String type)
            throws IOException {
        SecretKey sharedSecret = null;

        try {
            if (keyshare != null) {
                // Using KEM: called by the client after receiving the KEM
                // ciphertext (keyshare) from the server in ServerHello.
                // The client decapsulates it using its private key.

                // All exceptions thrown during KEM decapsulation are mapped
                // to TLS fatal alerts:
                // illegal_parameter alert or internal_error alert.
                try {
                    Provider provider = getKEMProvider();
                    KEM kem = (provider != null)
                            ? KEM.getInstance(algorithmName, provider)
                            : KEM.getInstance(algorithmName);
                    var decapsulator = kem.newDecapsulator(localPrivateKey);
                    sharedSecret = decapsulator.decapsulate(
                            keyshare, 0, decapsulator.secretSize(),
                            t13KeyDerivationAlgorithm);
                } catch (IllegalArgumentException | InvalidKeyException |
                        DecapsulateException e) {
                    // Peer validation failure
                    // ECDH all-zero shared secret (RFC 8446 section 7.4.2)
                    throw context.conContext.fatal(Alert.ILLEGAL_PARAMETER, e);
                } catch (GeneralSecurityException e) {
                    // cryptographic failure
                    throw context.conContext.fatal(Alert.INTERNAL_ERROR, e);
                } catch (RuntimeException e) {
                    // unexpected provider/runtime failure
                    throw context.conContext.fatal(Alert.INTERNAL_ERROR, e);
                }
            } else {
                // Using traditional DH-style Key Agreement
                KeyAgreement ka = KeyAgreement.getInstance(algorithmName);
                ka.init(localPrivateKey);
                ka.doPhase(peerPublicKey, true);
                sharedSecret = ka.generateSecret(t13KeyDerivationAlgorithm);
            }

            return deriveHandshakeSecret(type, sharedSecret);
        } catch (GeneralSecurityException gse) {
            // deriveHandshakeSecret() failure
            throw new SSLHandshakeException("Could not generate secret", gse);
        } finally {
            KeyUtil.destroySecretKeys(sharedSecret);
        }
    }
}
