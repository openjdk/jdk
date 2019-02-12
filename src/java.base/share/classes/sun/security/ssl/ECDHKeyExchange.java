/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.EnumSet;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLHandshakeException;
import sun.security.ssl.CipherSuite.HashAlg;
import sun.security.ssl.SupportedGroupsExtension.NamedGroup;
import sun.security.ssl.SupportedGroupsExtension.NamedGroupType;
import sun.security.ssl.SupportedGroupsExtension.SupportedGroups;
import sun.security.ssl.X509Authentication.X509Credentials;
import sun.security.ssl.X509Authentication.X509Possession;
import sun.security.util.ECUtil;

final class ECDHKeyExchange {
    static final SSLPossessionGenerator poGenerator =
            new ECDHEPossessionGenerator();
    static final SSLKeyAgreementGenerator ecdheKAGenerator =
            new ECDHEKAGenerator();
    static final SSLKeyAgreementGenerator ecdhKAGenerator =
            new ECDHKAGenerator();

    static final class ECDHECredentials implements SSLCredentials {
        final ECPublicKey popPublicKey;
        final NamedGroup namedGroup;

        ECDHECredentials(ECPublicKey popPublicKey, NamedGroup namedGroup) {
            this.popPublicKey = popPublicKey;
            this.namedGroup = namedGroup;
        }

        static ECDHECredentials valueOf(NamedGroup namedGroup,
            byte[] encodedPoint) throws IOException, GeneralSecurityException {

            if (namedGroup.type != NamedGroupType.NAMED_GROUP_ECDHE) {
                throw new RuntimeException(
                    "Credentials decoding:  Not ECDHE named group");
            }

            if (encodedPoint == null || encodedPoint.length == 0) {
                return null;
            }

            ECParameterSpec parameters =
                    ECUtil.getECParameterSpec(null, namedGroup.oid);
            if (parameters == null) {
                return null;
            }

            ECPoint point = ECUtil.decodePoint(
                    encodedPoint, parameters.getCurve());
            KeyFactory factory = KeyFactory.getInstance("EC");
            ECPublicKey publicKey = (ECPublicKey)factory.generatePublic(
                    new ECPublicKeySpec(point, parameters));
            return new ECDHECredentials(publicKey, namedGroup);
        }
    }

    static final class ECDHEPossession implements SSLPossession {
        final PrivateKey privateKey;
        final ECPublicKey publicKey;
        final NamedGroup namedGroup;

        ECDHEPossession(NamedGroup namedGroup, SecureRandom random) {
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                ECGenParameterSpec params =
                        (ECGenParameterSpec)namedGroup.getParameterSpec();
                kpg.initialize(params, random);
                KeyPair kp = kpg.generateKeyPair();
                privateKey = kp.getPrivate();
                publicKey = (ECPublicKey)kp.getPublic();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(
                    "Could not generate ECDH keypair", e);
            }

            this.namedGroup = namedGroup;
        }

        ECDHEPossession(ECDHECredentials credentials, SecureRandom random) {
            ECParameterSpec params = credentials.popPublicKey.getParams();
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                kpg.initialize(params, random);
                KeyPair kp = kpg.generateKeyPair();
                privateKey = kp.getPrivate();
                publicKey = (ECPublicKey)kp.getPublic();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(
                    "Could not generate ECDH keypair", e);
            }

            this.namedGroup = credentials.namedGroup;
        }

        @Override
        public byte[] encode() {
            return ECUtil.encodePoint(
                    publicKey.getW(), publicKey.getParams().getCurve());
        }

        // called by ClientHandshaker with either the server's static or
        // ephemeral public key
        SecretKey getAgreedSecret(
                PublicKey peerPublicKey) throws SSLHandshakeException {

            try {
                KeyAgreement ka = KeyAgreement.getInstance("ECDH");
                ka.init(privateKey);
                ka.doPhase(peerPublicKey, true);
                return ka.generateSecret("TlsPremasterSecret");
            } catch (GeneralSecurityException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate secret").initCause(e);
            }
        }

        // called by ServerHandshaker
        SecretKey getAgreedSecret(
                byte[] encodedPoint) throws SSLHandshakeException {
            try {
                ECParameterSpec params = publicKey.getParams();
                ECPoint point =
                        ECUtil.decodePoint(encodedPoint, params.getCurve());
                KeyFactory kf = KeyFactory.getInstance("EC");
                ECPublicKeySpec spec = new ECPublicKeySpec(point, params);
                PublicKey peerPublicKey = kf.generatePublic(spec);
                return getAgreedSecret(peerPublicKey);
            } catch (GeneralSecurityException | java.io.IOException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate secret").initCause(e);
            }
        }

        // Check constraints of the specified EC public key.
        void checkConstraints(AlgorithmConstraints constraints,
                byte[] encodedPoint) throws SSLHandshakeException {
            try {

                ECParameterSpec params = publicKey.getParams();
                ECPoint point =
                        ECUtil.decodePoint(encodedPoint, params.getCurve());
                ECPublicKeySpec spec = new ECPublicKeySpec(point, params);

                KeyFactory kf = KeyFactory.getInstance("EC");
                ECPublicKey pubKey = (ECPublicKey)kf.generatePublic(spec);

                // check constraints of ECPublicKey
                if (!constraints.permits(
                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), pubKey)) {
                    throw new SSLHandshakeException(
                        "ECPublicKey does not comply to algorithm constraints");
                }
            } catch (GeneralSecurityException | java.io.IOException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                        "Could not generate ECPublicKey").initCause(e);
            }
        }
    }

    private static final
            class ECDHEPossessionGenerator implements SSLPossessionGenerator {
        // Prevent instantiation of this class.
        private ECDHEPossessionGenerator() {
            // blank
        }

        @Override
        public SSLPossession createPossession(HandshakeContext context) {
            NamedGroup preferableNamedGroup = null;
            if ((context.clientRequestedNamedGroups != null) &&
                    (!context.clientRequestedNamedGroups.isEmpty())) {
                preferableNamedGroup = SupportedGroups.getPreferredGroup(
                        context.negotiatedProtocol,
                        context.algorithmConstraints,
                        NamedGroupType.NAMED_GROUP_ECDHE,
                        context.clientRequestedNamedGroups);
            } else {
                preferableNamedGroup = SupportedGroups.getPreferredGroup(
                        context.negotiatedProtocol,
                        context.algorithmConstraints,
                        NamedGroupType.NAMED_GROUP_ECDHE);
            }

            if (preferableNamedGroup != null) {
                return new ECDHEPossession(preferableNamedGroup,
                            context.sslContext.getSecureRandom());
            }

            // no match found, cannot use this cipher suite.
            //
            return null;
        }
    }

    private static final
            class ECDHKAGenerator implements SSLKeyAgreementGenerator {
        // Prevent instantiation of this class.
        private ECDHKAGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext context) throws IOException {
            if (context instanceof ServerHandshakeContext) {
                return createServerKeyDerivation(
                        (ServerHandshakeContext)context);
            } else {
                return createClientKeyDerivation(
                        (ClientHandshakeContext)context);
            }
        }

        private SSLKeyDerivation createServerKeyDerivation(
                ServerHandshakeContext shc) throws IOException {
            X509Possession x509Possession = null;
            ECDHECredentials ecdheCredentials = null;
            for (SSLPossession poss : shc.handshakePossessions) {
                if (!(poss instanceof X509Possession)) {
                    continue;
                }

                PrivateKey privateKey = ((X509Possession)poss).popPrivateKey;
                if (!privateKey.getAlgorithm().equals("EC")) {
                    continue;
                }

                ECParameterSpec params = ((ECPrivateKey)privateKey).getParams();
                NamedGroup ng = NamedGroup.valueOf(params);
                if (ng == null) {
                    // unlikely, have been checked during cipher suite negotiation.
                    throw shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Unsupported EC server cert for ECDH key exchange");
                }

                for (SSLCredentials cred : shc.handshakeCredentials) {
                    if (!(cred instanceof ECDHECredentials)) {
                        continue;
                    }
                    if (ng.equals(((ECDHECredentials)cred).namedGroup)) {
                        ecdheCredentials = (ECDHECredentials)cred;
                        break;
                    }
                }

                if (ecdheCredentials != null) {
                    x509Possession = (X509Possession)poss;
                    break;
                }
            }

            if (x509Possession == null || ecdheCredentials == null) {
                throw shc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No sufficient ECDHE key agreement parameters negotiated");
            }

            return new ECDHEKAKeyDerivation(shc,
                x509Possession.popPrivateKey, ecdheCredentials.popPublicKey);
        }

        private SSLKeyDerivation createClientKeyDerivation(
                ClientHandshakeContext chc) throws IOException {
            ECDHEPossession ecdhePossession = null;
            X509Credentials x509Credentials = null;
            for (SSLPossession poss : chc.handshakePossessions) {
                if (!(poss instanceof ECDHEPossession)) {
                    continue;
                }

                NamedGroup ng = ((ECDHEPossession)poss).namedGroup;
                for (SSLCredentials cred : chc.handshakeCredentials) {
                    if (!(cred instanceof X509Credentials)) {
                        continue;
                    }

                    PublicKey publicKey = ((X509Credentials)cred).popPublicKey;
                    if (!publicKey.getAlgorithm().equals("EC")) {
                        continue;
                    }
                    ECParameterSpec params =
                            ((ECPublicKey)publicKey).getParams();
                    NamedGroup namedGroup = NamedGroup.valueOf(params);
                    if (namedGroup == null) {
                        // unlikely, should have been checked previously
                        throw chc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                            "Unsupported EC server cert for ECDH key exchange");
                    }

                    if (ng.equals(namedGroup)) {
                        x509Credentials = (X509Credentials)cred;
                        break;
                    }
                }

                if (x509Credentials != null) {
                    ecdhePossession = (ECDHEPossession)poss;
                    break;
                }
            }

            if (ecdhePossession == null || x509Credentials == null) {
                throw chc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No sufficient ECDH key agreement parameters negotiated");
            }

            return new ECDHEKAKeyDerivation(chc,
                ecdhePossession.privateKey, x509Credentials.popPublicKey);
        }
    }

    private static final
            class ECDHEKAGenerator implements SSLKeyAgreementGenerator {
        // Prevent instantiation of this class.
        private ECDHEKAGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext context) throws IOException {
            ECDHEPossession ecdhePossession = null;
            ECDHECredentials ecdheCredentials = null;
            for (SSLPossession poss : context.handshakePossessions) {
                if (!(poss instanceof ECDHEPossession)) {
                    continue;
                }

                NamedGroup ng = ((ECDHEPossession)poss).namedGroup;
                for (SSLCredentials cred : context.handshakeCredentials) {
                    if (!(cred instanceof ECDHECredentials)) {
                        continue;
                    }
                    if (ng.equals(((ECDHECredentials)cred).namedGroup)) {
                        ecdheCredentials = (ECDHECredentials)cred;
                        break;
                    }
                }

                if (ecdheCredentials != null) {
                    ecdhePossession = (ECDHEPossession)poss;
                    break;
                }
            }

            if (ecdhePossession == null || ecdheCredentials == null) {
                throw context.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No sufficient ECDHE key agreement parameters negotiated");
            }

            return new ECDHEKAKeyDerivation(context,
                ecdhePossession.privateKey, ecdheCredentials.popPublicKey);
        }
    }

    private static final
            class ECDHEKAKeyDerivation implements SSLKeyDerivation {
        private final HandshakeContext context;
        private final PrivateKey localPrivateKey;
        private final PublicKey peerPublicKey;

        ECDHEKAKeyDerivation(HandshakeContext context,
                PrivateKey localPrivateKey,
                PublicKey peerPublicKey) {
            this.context = context;
            this.localPrivateKey = localPrivateKey;
            this.peerPublicKey = peerPublicKey;
        }

        @Override
        public SecretKey deriveKey(String algorithm,
                AlgorithmParameterSpec params) throws IOException {
            if (!context.negotiatedProtocol.useTLS13PlusSpec()) {
                return t12DeriveKey(algorithm, params);
            } else {
                return t13DeriveKey(algorithm, params);
            }
        }

        private SecretKey t12DeriveKey(String algorithm,
                AlgorithmParameterSpec params) throws IOException {
            try {
                KeyAgreement ka = KeyAgreement.getInstance("ECDH");
                ka.init(localPrivateKey);
                ka.doPhase(peerPublicKey, true);
                SecretKey preMasterSecret =
                        ka.generateSecret("TlsPremasterSecret");

                SSLMasterKeyDerivation mskd =
                        SSLMasterKeyDerivation.valueOf(
                                context.negotiatedProtocol);
                if (mskd == null) {
                    // unlikely
                    throw new SSLHandshakeException(
                            "No expected master key derivation for protocol: " +
                            context.negotiatedProtocol.name);
                }
                SSLKeyDerivation kd = mskd.createKeyDerivation(
                        context, preMasterSecret);
                return kd.deriveKey("MasterSecret", params);
            } catch (GeneralSecurityException gse) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate secret").initCause(gse);
            }
        }

        private SecretKey t13DeriveKey(String algorithm,
                AlgorithmParameterSpec params) throws IOException {
            try {
                KeyAgreement ka = KeyAgreement.getInstance("ECDH");
                ka.init(localPrivateKey);
                ka.doPhase(peerPublicKey, true);
                SecretKey sharedSecret =
                        ka.generateSecret("TlsPremasterSecret");

                HashAlg hashAlg = context.negotiatedCipherSuite.hashAlg;
                SSLKeyDerivation kd = context.handshakeKeyDerivation;
                HKDF hkdf = new HKDF(hashAlg.name);
                if (kd == null) {   // No PSK is in use.
                    // If PSK is not in use Early Secret will still be
                    // HKDF-Extract(0, 0).
                    byte[] zeros = new byte[hashAlg.hashLength];
                    SecretKeySpec ikm =
                            new SecretKeySpec(zeros, "TlsPreSharedSecret");
                    SecretKey earlySecret =
                            hkdf.extract(zeros, ikm, "TlsEarlySecret");
                    kd = new SSLSecretDerivation(context, earlySecret);
                }

                // derive salt secret
                SecretKey saltSecret = kd.deriveKey("TlsSaltSecret", null);

                // derive handshake secret
                return hkdf.extract(saltSecret, sharedSecret, algorithm);
            } catch (GeneralSecurityException gse) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate secret").initCause(gse);
            }
        }
    }
}
