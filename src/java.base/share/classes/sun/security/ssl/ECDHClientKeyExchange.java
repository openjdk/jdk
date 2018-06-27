/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.text.MessageFormat;
import java.util.EnumSet;
import java.util.Locale;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLHandshakeException;
import sun.security.ssl.ECDHKeyExchange.ECDHECredentials;
import sun.security.ssl.ECDHKeyExchange.ECDHEPossession;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.ssl.SupportedGroupsExtension.NamedGroup;
import sun.security.ssl.X509Authentication.X509Credentials;
import sun.security.ssl.X509Authentication.X509Possession;
import sun.security.util.HexDumpEncoder;

/**
 * Pack of the "ClientKeyExchange" handshake message.
 */
final class ECDHClientKeyExchange {
    static final SSLConsumer ecdhHandshakeConsumer =
            new ECDHClientKeyExchangeConsumer();
    static final HandshakeProducer ecdhHandshakeProducer =
            new ECDHClientKeyExchangeProducer();

    static final SSLConsumer ecdheHandshakeConsumer =
            new ECDHEClientKeyExchangeConsumer();
    static final HandshakeProducer ecdheHandshakeProducer =
            new ECDHEClientKeyExchangeProducer();

    /**
     * The ECDH/ECDHE ClientKeyExchange handshake message.
     */
    private static final
            class ECDHClientKeyExchangeMessage extends HandshakeMessage {
        private final byte[] encodedPoint;

        ECDHClientKeyExchangeMessage(HandshakeContext handshakeContext,
                ECPublicKey publicKey) {
            super(handshakeContext);

            ECPoint point = publicKey.getW();
            ECParameterSpec params = publicKey.getParams();
            encodedPoint = JsseJce.encodePoint(point, params.getCurve());
        }

        ECDHClientKeyExchangeMessage(HandshakeContext handshakeContext,
                ByteBuffer m) throws IOException {
            super(handshakeContext);
            if (m.remaining() != 0) {       // explicit PublicValueEncoding
                this.encodedPoint = Record.getBytes8(m);
            } else {
                this.encodedPoint = new byte[0];
            }
        }

        // Check constraints of the specified EC public key.
        static void checkConstraints(AlgorithmConstraints constraints,
                ECPublicKey publicKey,
                byte[] encodedPoint) throws SSLHandshakeException {

            try {
                ECParameterSpec params = publicKey.getParams();
                ECPoint point =
                        JsseJce.decodePoint(encodedPoint, params.getCurve());
                ECPublicKeySpec spec = new ECPublicKeySpec(point, params);

                KeyFactory kf = JsseJce.getKeyFactory("EC");
                ECPublicKey peerPublicKey =
                        (ECPublicKey)kf.generatePublic(spec);

                // check constraints of ECPublicKey
                if (!constraints.permits(
                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                        peerPublicKey)) {
                    throw new SSLHandshakeException(
                        "ECPublicKey does not comply to algorithm constraints");
                }
            } catch (GeneralSecurityException | java.io.IOException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                        "Could not generate ECPublicKey").initCause(e);
            }
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.CLIENT_KEY_EXCHANGE;
        }

        @Override
        public int messageLength() {
            if (encodedPoint == null || encodedPoint.length == 0) {
                return 0;
            } else {
                return 1 + encodedPoint.length;
            }
        }

        @Override
        public void send(HandshakeOutStream hos) throws IOException {
            if (encodedPoint != null && encodedPoint.length != 0) {
                hos.putBytes8(encodedPoint);
            }
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"ECDH ClientKeyExchange\": '{'\n" +
                "  \"ecdh public\": '{'\n" +
                "{0}\n" +
                "  '}',\n" +
                "'}'",
                Locale.ENGLISH);
            if (encodedPoint == null || encodedPoint.length == 0) {
                Object[] messageFields = {
                    "    <implicit>"
                };
                return messageFormat.format(messageFields);
            } else {
                HexDumpEncoder hexEncoder = new HexDumpEncoder();
                Object[] messageFields = {
                    Utilities.indent(
                            hexEncoder.encodeBuffer(encodedPoint), "    "),
                };
                return messageFormat.format(messageFields);
            }
        }
    }

    /**
     * The ECDH "ClientKeyExchange" handshake message producer.
     */
    private static final
            class ECDHClientKeyExchangeProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private ECDHClientKeyExchangeProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            X509Credentials x509Credentials = null;
            for (SSLCredentials credential : chc.handshakeCredentials) {
                if (credential instanceof X509Credentials) {
                    x509Credentials = (X509Credentials)credential;
                    break;
                }
            }

            if (x509Credentials == null) {
                chc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "No server certificate for ECDH client key exchange");
            }

            PublicKey publicKey = x509Credentials.popPublicKey;
            if (!publicKey.getAlgorithm().equals("EC")) {
                chc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Not EC server certificate for ECDH client key exchange");
            }

            ECParameterSpec params = ((ECPublicKey)publicKey).getParams();
            NamedGroup namedGroup = NamedGroup.valueOf(params);
            if (namedGroup == null) {
                chc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Unsupported EC server cert for ECDH client key exchange");
            }

            ECDHEPossession ecdhePossession = new ECDHEPossession(
                    namedGroup, chc.sslContext.getSecureRandom());
            chc.handshakePossessions.add(ecdhePossession);
            ECDHClientKeyExchangeMessage cke =
                    new ECDHClientKeyExchangeMessage(
                            chc, ecdhePossession.publicKey);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Produced ECDH ClientKeyExchange handshake message", cke);
            }

            // Output the handshake message.
            cke.write(chc.handshakeOutput);
            chc.handshakeOutput.flush();

            // update the states
            SSLKeyExchange ke = SSLKeyExchange.valueOf(
                    chc.negotiatedCipherSuite.keyExchange,
                    chc.negotiatedProtocol);
            if (ke == null) {
                // unlikely
                chc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key exchange type");
            } else {
                SSLKeyDerivation masterKD = ke.createKeyDerivation(chc);
                SecretKey masterSecret =
                        masterKD.deriveKey("MasterSecret", null);
                chc.handshakeSession.setMasterSecret(masterSecret);

                SSLTrafficKeyDerivation kd =
                        SSLTrafficKeyDerivation.valueOf(chc.negotiatedProtocol);
                if (kd == null) {
                    // unlikely
                    chc.conContext.fatal(Alert.INTERNAL_ERROR,
                            "Not supported key derivation: " +
                            chc.negotiatedProtocol);
                } else {
                    chc.handshakeKeyDerivation =
                        kd.createKeyDerivation(chc, masterSecret);
                }
            }

            // The handshake message has been delivered.
            return null;
        }
    }

    /**
     * The ECDH "ClientKeyExchange" handshake message consumer.
     */
    private static final
            class ECDHClientKeyExchangeConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private ECDHClientKeyExchangeConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            X509Possession x509Possession = null;
            for (SSLPossession possession : shc.handshakePossessions) {
                if (possession instanceof X509Possession) {
                    x509Possession = (X509Possession)possession;
                    break;
                }
            }

            if (x509Possession == null) {
                // unlikely, have been checked during cipher suite negotiation.
                shc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "No expected EC server cert for ECDH client key exchange");
                return;     // make the compiler happy
            }

            PrivateKey privateKey = x509Possession.popPrivateKey;
            if (!privateKey.getAlgorithm().equals("EC")) {
                // unlikely, have been checked during cipher suite negotiation.
                shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Not EC server cert for ECDH client key exchange");
            }

            ECParameterSpec params = ((ECPrivateKey)privateKey).getParams();
            NamedGroup namedGroup = NamedGroup.valueOf(params);
            if (namedGroup == null) {
                // unlikely, have been checked during cipher suite negotiation.
                shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Unsupported EC server cert for ECDH client key exchange");
            }

            SSLKeyExchange ke = SSLKeyExchange.valueOf(
                    shc.negotiatedCipherSuite.keyExchange,
                    shc.negotiatedProtocol);
            if (ke == null) {
                // unlikely
                shc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key exchange type");
                return;     // make the compiler happy
            }

            // parse the handshake message
            ECDHClientKeyExchangeMessage cke =
                    new ECDHClientKeyExchangeMessage(shc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Consuming ECDH ClientKeyExchange handshake message", cke);
            }

            // create the credentials
            try {
                ECPoint point =
                    JsseJce.decodePoint(cke.encodedPoint, params.getCurve());
                ECPublicKeySpec spec = new ECPublicKeySpec(point, params);

                KeyFactory kf = JsseJce.getKeyFactory("EC");
                ECPublicKey peerPublicKey =
                        (ECPublicKey)kf.generatePublic(spec);

                // check constraints of peer ECPublicKey
                if (!shc.algorithmConstraints.permits(
                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                        peerPublicKey)) {
                    throw new SSLHandshakeException(
                        "ECPublicKey does not comply to algorithm constraints");
                }

                shc.handshakeCredentials.add(new ECDHECredentials(
                        peerPublicKey, namedGroup));
            } catch (GeneralSecurityException | java.io.IOException e) {
                throw (SSLHandshakeException)(new SSLHandshakeException(
                        "Could not generate ECPublicKey").initCause(e));
            }

            // update the states
            SSLKeyDerivation masterKD = ke.createKeyDerivation(shc);
            SecretKey masterSecret =
                    masterKD.deriveKey("MasterSecret", null);
            shc.handshakeSession.setMasterSecret(masterSecret);

            SSLTrafficKeyDerivation kd =
                    SSLTrafficKeyDerivation.valueOf(shc.negotiatedProtocol);
            if (kd == null) {
                // unlikely
                shc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "Not supported key derivation: " + shc.negotiatedProtocol);
            } else {
                shc.handshakeKeyDerivation =
                    kd.createKeyDerivation(shc, masterSecret);
            }
        }
    }

    /**
     * The ECDHE "ClientKeyExchange" handshake message producer.
     */
    private static final
            class ECDHEClientKeyExchangeProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private ECDHEClientKeyExchangeProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            ECDHECredentials ecdheCredentials = null;
            for (SSLCredentials cd : chc.handshakeCredentials) {
                if (cd instanceof ECDHECredentials) {
                    ecdheCredentials = (ECDHECredentials)cd;
                    break;
                }
            }

            if (ecdheCredentials == null) {
                chc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "No ECDHE credentials negotiated for client key exchange");
            }

            ECDHEPossession ecdhePossession = new ECDHEPossession(
                    ecdheCredentials, chc.sslContext.getSecureRandom());
            chc.handshakePossessions.add(ecdhePossession);
            ECDHClientKeyExchangeMessage cke =
                    new ECDHClientKeyExchangeMessage(
                            chc, ecdhePossession.publicKey);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Produced ECDHE ClientKeyExchange handshake message", cke);
            }

            // Output the handshake message.
            cke.write(chc.handshakeOutput);
            chc.handshakeOutput.flush();

            // update the states
            SSLKeyExchange ke = SSLKeyExchange.valueOf(
                    chc.negotiatedCipherSuite.keyExchange,
                    chc.negotiatedProtocol);
            if (ke == null) {
                // unlikely
                chc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key exchange type");
            } else {
                SSLKeyDerivation masterKD = ke.createKeyDerivation(chc);
                SecretKey masterSecret =
                        masterKD.deriveKey("MasterSecret", null);
                chc.handshakeSession.setMasterSecret(masterSecret);

                SSLTrafficKeyDerivation kd =
                        SSLTrafficKeyDerivation.valueOf(chc.negotiatedProtocol);
                if (kd == null) {
                    // unlikely
                    chc.conContext.fatal(Alert.INTERNAL_ERROR,
                            "Not supported key derivation: " +
                            chc.negotiatedProtocol);
                } else {
                    chc.handshakeKeyDerivation =
                        kd.createKeyDerivation(chc, masterSecret);
                }
            }

            // The handshake message has been delivered.
            return null;
        }
    }

    /**
     * The ECDHE "ClientKeyExchange" handshake message consumer.
     */
    private static final
            class ECDHEClientKeyExchangeConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private ECDHEClientKeyExchangeConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            ECDHEPossession ecdhePossession = null;
            for (SSLPossession possession : shc.handshakePossessions) {
                if (possession instanceof ECDHEPossession) {
                    ecdhePossession = (ECDHEPossession)possession;
                    break;
                }
            }
            if (ecdhePossession == null) {
                // unlikely
                shc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "No expected ECDHE possessions for client key exchange");
                return;     // make the compiler happy
            }

            ECParameterSpec params = ecdhePossession.publicKey.getParams();
            NamedGroup namedGroup = NamedGroup.valueOf(params);
            if (namedGroup == null) {
                // unlikely, have been checked during cipher suite negotiation.
                shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Unsupported EC server cert for ECDHE client key exchange");
            }

            SSLKeyExchange ke = SSLKeyExchange.valueOf(
                    shc.negotiatedCipherSuite.keyExchange,
                    shc.negotiatedProtocol);
            if (ke == null) {
                // unlikely
                shc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key exchange type");
                return;     // make the compiler happy
            }

            // parse the handshake message
            ECDHClientKeyExchangeMessage cke =
                    new ECDHClientKeyExchangeMessage(shc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Consuming ECDHE ClientKeyExchange handshake message", cke);
            }

            // create the credentials
            try {
                ECPoint point =
                    JsseJce.decodePoint(cke.encodedPoint, params.getCurve());
                ECPublicKeySpec spec = new ECPublicKeySpec(point, params);

                KeyFactory kf = JsseJce.getKeyFactory("EC");
                ECPublicKey peerPublicKey =
                        (ECPublicKey)kf.generatePublic(spec);

                // check constraints of peer ECPublicKey
                if (!shc.algorithmConstraints.permits(
                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT),
                        peerPublicKey)) {
                    throw new SSLHandshakeException(
                        "ECPublicKey does not comply to algorithm constraints");
                }

                shc.handshakeCredentials.add(new ECDHECredentials(
                        peerPublicKey, namedGroup));
            } catch (GeneralSecurityException | java.io.IOException e) {
                throw (SSLHandshakeException)(new SSLHandshakeException(
                        "Could not generate ECPublicKey").initCause(e));
            }

            // update the states
            SSLKeyDerivation masterKD = ke.createKeyDerivation(shc);
            SecretKey masterSecret =
                    masterKD.deriveKey("MasterSecret", null);
            shc.handshakeSession.setMasterSecret(masterSecret);

            SSLTrafficKeyDerivation kd =
                    SSLTrafficKeyDerivation.valueOf(shc.negotiatedProtocol);
            if (kd == null) {
                // unlikely
                shc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "Not supported key derivation: " + shc.negotiatedProtocol);
            } else {
                shc.handshakeKeyDerivation =
                    kd.createKeyDerivation(shc, masterSecret);
            }
        }
    }
}
