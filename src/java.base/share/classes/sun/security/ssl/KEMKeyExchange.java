/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.security.*;
import java.security.spec.NamedParameterSpec;

import sun.security.ssl.NamedGroup.NamedGroupSpec;
import sun.security.util.*;
import sun.security.x509.X509Key;

import javax.crypto.SecretKey;

/**
 * Specifics for single or hybrid Key exchanges based on KEM
 */
final class KEMKeyExchange {

    static final SSLKeyAgreementGenerator kemKAGenerator
            = new KEMKAGenerator();

    static final class KEMCredentials implements NamedGroupCredentials {

        final NamedGroup namedGroup;
        // Unlike other credentials, we directly store the key share
        // value here, no need to convert to a key
        final byte[] keyshare;

        KEMCredentials(byte[] keyshare, NamedGroup namedGroup) {
            this.keyshare = keyshare;
            this.namedGroup = namedGroup;
        }

        // For KEM, server performs encapsulation and the resulting
        // encapsulated message becomes the key_share value sent to
        // the client. It is not a public key, so no PublicKey object
        // to return.
        @Override
        public PublicKey getPublicKey() {
            throw new UnsupportedOperationException(
                    "KEMCredentials stores raw keyshare, not a PublicKey");
        }

        public byte[] getKeyshare() {
            return keyshare;
        }

        @Override
        public NamedGroup getNamedGroup() {
            return namedGroup;
        }

        /**
         * Parse the encoded Point into the KEMCredentials using the
         * namedGroup.
         */
        static KEMCredentials valueOf(NamedGroup namedGroup,
                byte[] encodedPoint) throws IOException,
                GeneralSecurityException {

            if (namedGroup.spec != NamedGroupSpec.NAMED_GROUP_KEM) {
                throw new RuntimeException(
                        "Credentials decoding:  Not KEM named group");
            }

            if (encodedPoint == null || encodedPoint.length == 0) {
                return null;
            }

            return new KEMCredentials(encodedPoint, namedGroup);
        }
    }

    static class KEMPossession implements SSLPossession {
        private final NamedGroup namedGroup;
        public KEMPossession(NamedGroup ng) {
            this.namedGroup = ng;
        }
        public NamedGroup getNamedGroup() {
            return namedGroup;
        }
    }

    static final class KEMReceiverPossession extends KEMPossession {

        private final PrivateKey privateKey;
        private final PublicKey publicKey;

        KEMReceiverPossession(NamedGroup namedGroup, SecureRandom random) {
            super(namedGroup);
            try {
                // For KEM: This receiver side (client) generates a key pair.
                String algName = ((NamedParameterSpec)namedGroup.keAlgParamSpec).
                        getName();
                Provider provider = namedGroup.getProvider();
                KeyPairGenerator kpg = (provider != null) ?
                        KeyPairGenerator.getInstance(algName, provider) :
                        KeyPairGenerator.getInstance(algName);

                KeyPair kp = kpg.generateKeyPair();
                privateKey = kp.getPrivate();
                publicKey = kp.getPublic();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(
                        "Could not generate XDH keypair", e);
            }
        }

        @Override
        public byte[] encode() {
            if (publicKey instanceof X509Key xk) {
                return xk.getKeyAsBytes();
            } else if (publicKey instanceof Hybrid.PublicKeyImpl hk) {
                return hk.getEncoded();
            }
            throw new ProviderException("Unsupported key type: " + publicKey);
        }

        public PublicKey getPublicKey() {
            return publicKey;
        }

        public PrivateKey getPrivateKey() {
            return privateKey;
        }
    }

    static final class KEMSenderPossession extends KEMPossession {

        public SecretKey getKey() {
            return key;
        }

        public void setKey(SecretKey key) {
            this.key = key;
        }

        private SecretKey key;

        KEMSenderPossession(NamedGroup namedGroup) {
            super(namedGroup);
        }

        @Override
        public byte[] encode() {
            throw new UnsupportedOperationException("encode() not supported");
        }
    }

    private static final class KEMKAGenerator
            implements SSLKeyAgreementGenerator {

        // Prevent instantiation of this class.
        private KEMKAGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext context) throws IOException {
            for (SSLPossession poss : context.handshakePossessions) {
                if (poss instanceof KEMReceiverPossession kposs) {
                    NamedGroup ng = kposs.getNamedGroup();
                    for (SSLCredentials cred : context.handshakeCredentials) {
                        if (cred instanceof KEMCredentials kcred &&
                                ng.equals(kcred.namedGroup)) {
                            String name = ((NamedParameterSpec) ng.keAlgParamSpec).
                                    getName();
                            return new KAKeyDerivation(name, ng, context,
                                    kposs.getPrivateKey(), null,
                                    kcred.getKeyshare());
                        }
                    }
                }
            }
            context.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No sufficient XDHE key agreement "
                    + "parameters negotiated");
            return null;
        }
    }
}
