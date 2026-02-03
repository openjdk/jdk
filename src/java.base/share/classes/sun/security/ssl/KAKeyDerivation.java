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

import sun.security.util.RawKeySpec;

import javax.crypto.KDF;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import sun.security.util.KeyUtil;

/**
 * A common class for creating various KeyDerivation types.
 */
public class KAKeyDerivation implements SSLKeyDerivation {

    private final String algorithmName;
    private final HandshakeContext context;
    private final PrivateKey localPrivateKey;
    private final PublicKey peerPublicKey;
    private final byte[] keyshare;
    private final Provider provider;

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
        this.provider = (namedGroup != null) ? namedGroup.getProvider() : null;
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
            KDF hkdf = KDF.getInstance(hashAlg.hkdfAlgorithm);
            var spec = HKDFParameterSpec.ofExtract().addSalt(saltSecret);
            if (sharedSecret instanceof Hybrid.SecretKeyImpl hsk) {
                spec = spec.addIKM(hsk.k1()).addIKM(hsk.k2());
            } else {
                spec = spec.addIKM(sharedSecret);
            }

            return hkdf.deriveKey(label, spec.extractOnly());
        } finally {
            KeyUtil.destroySecretKeys(earlySecret, saltSecret);
        }
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

        try {
            KeyFactory kf = (provider != null) ?
                    KeyFactory.getInstance(algorithmName, provider) :
                    KeyFactory.getInstance(algorithmName);
            var pk = kf.generatePublic(new RawKeySpec(keyshare));

            KEM kem = (provider != null) ?
                    KEM.getInstance(algorithmName, provider) :
                    KEM.getInstance(algorithmName);
            KEM.Encapsulator e = kem.newEncapsulator(pk, random);
            KEM.Encapsulated enc = e.encapsulate();
            sharedSecret = enc.key();

            SecretKey derived = deriveHandshakeSecret(algorithm, sharedSecret);

            return new KEM.Encapsulated(derived, enc.encapsulation(), null);
        } catch (GeneralSecurityException gse) {
            throw new SSLHandshakeException("Could not generate secret", gse);
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
                KEM kem = (provider != null)
                        ? KEM.getInstance(algorithmName, provider)
                        : KEM.getInstance(algorithmName);
                var decapsulator = kem.newDecapsulator(localPrivateKey);
                sharedSecret = decapsulator.decapsulate(
                        keyshare, 0, decapsulator.secretSize(),
                        "Generic");
            } else {
                // Using traditional DH-style Key Agreement
                KeyAgreement ka = KeyAgreement.getInstance(algorithmName);
                ka.init(localPrivateKey);
                ka.doPhase(peerPublicKey, true);
                sharedSecret = ka.generateSecret("Generic");
            }

            return deriveHandshakeSecret(type, sharedSecret);
        } catch (GeneralSecurityException gse) {
            throw new SSLHandshakeException("Could not generate secret", gse);
        } finally {
            KeyUtil.destroySecretKeys(sharedSecret);
        }
    }
}
