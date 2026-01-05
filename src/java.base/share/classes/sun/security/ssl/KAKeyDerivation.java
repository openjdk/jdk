/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import sun.security.util.KeyUtil;

/**
 * A common class for creating various KeyDerivation types.
 */
public class KAKeyDerivation implements SSLKeyDerivation {

    private final String algorithmName;
    private final HandshakeContext context;
    private final PrivateKey localPrivateKey;
    private final PublicKey peerPublicKey;

    KAKeyDerivation(String algorithmName,
            HandshakeContext context,
            PrivateKey localPrivateKey,
            PublicKey peerPublicKey) {
        this.algorithmName = algorithmName;
        this.context = context;
        this.localPrivateKey = localPrivateKey;
        this.peerPublicKey = peerPublicKey;
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

    /**
     * Handle the TLSv1.3 objects, which use the HKDF algorithms.
     */
    private SecretKey t13DeriveKey(String type)
            throws IOException {
        SecretKey sharedSecret = null;
        SecretKey earlySecret = null;
        SecretKey saltSecret = null;
        try {
            KeyAgreement ka = KeyAgreement.getInstance(algorithmName);
            ka.init(localPrivateKey);
            ka.doPhase(peerPublicKey, true);
            sharedSecret = ka.generateSecret("TlsPremasterSecret");

            CipherSuite.HashAlg hashAlg = context.negotiatedCipherSuite.hashAlg;
            SSLKeyDerivation kd = context.handshakeKeyDerivation;
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
            return hkdf.deriveKey(type, HKDFParameterSpec.ofExtract()
                    .addSalt(saltSecret).addIKM(sharedSecret).extractOnly());
        } catch (GeneralSecurityException gse) {
            throw new SSLHandshakeException("Could not generate secret", gse);
        } finally {
            KeyUtil.destroySecretKeys(sharedSecret, earlySecret, saltSecret);
        }
    }
}
