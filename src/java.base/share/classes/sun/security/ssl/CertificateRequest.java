/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static sun.security.ssl.SignatureScheme.CERTIFICATE_SCOPE;
import static sun.security.ssl.SignatureScheme.HANDSHAKE_SCOPE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.*;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.security.auth.x500.X500Principal;
import sun.security.ssl.CipherSuite.KeyExchange;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.ssl.X509Authentication.X509Possession;

/**
 * Pack of the CertificateRequest handshake message.
 */
final class CertificateRequest {
    static final SSLConsumer t10HandshakeConsumer =
        new T10CertificateRequestConsumer();
    static final HandshakeProducer t10HandshakeProducer =
        new T10CertificateRequestProducer();

    static final SSLConsumer t12HandshakeConsumer =
        new T12CertificateRequestConsumer();
    static final HandshakeProducer t12HandshakeProducer =
        new T12CertificateRequestProducer();

    static final SSLConsumer t13HandshakeConsumer =
        new T13CertificateRequestConsumer();
    static final HandshakeProducer t13HandshakeProducer =
        new T13CertificateRequestProducer();

    // TLS 1.2 and prior versions
    private enum ClientCertificateType {
        // RFC 2246
        RSA_SIGN            ((byte)0x01, "rsa_sign", List.of("RSA"), true),
        DSS_SIGN            ((byte)0x02, "dss_sign", List.of("DSA"), true),
        RSA_FIXED_DH        ((byte)0x03, "rsa_fixed_dh"),
        DSS_FIXED_DH        ((byte)0x04, "dss_fixed_dh"),

        // RFC 4346
        RSA_EPHEMERAL_DH    ((byte)0x05, "rsa_ephemeral_dh"),
        DSS_EPHEMERAL_DH    ((byte)0x06, "dss_ephemeral_dh"),
        FORTEZZA_DMS        ((byte)0x14, "fortezza_dms"),

        // RFC 4492 and 8442
        ECDSA_SIGN          ((byte)0x40, "ecdsa_sign",
                                            List.of("EC", "EdDSA"),
                                            JsseJce.isEcAvailable()),
        RSA_FIXED_ECDH      ((byte)0x41, "rsa_fixed_ecdh"),
        ECDSA_FIXED_ECDH    ((byte)0x42, "ecdsa_fixed_ecdh");

        private static final byte[] CERT_TYPES =
                JsseJce.isEcAvailable() ? new byte[] {
                        ECDSA_SIGN.id,
                        RSA_SIGN.id,
                        DSS_SIGN.id
                    } :  new byte[] {
                        RSA_SIGN.id,
                        DSS_SIGN.id
                    };

        final byte id;
        final String name;
        final List<String> keyAlgorithm;
        final boolean isAvailable;

        ClientCertificateType(byte id, String name) {
            this(id, name, null, false);
        }

        ClientCertificateType(byte id, String name,
                List<String> keyAlgorithm, boolean isAvailable) {
            this.id = id;
            this.name = name;
            this.keyAlgorithm = keyAlgorithm;
            this.isAvailable = isAvailable;
        }

        private static String nameOf(byte id) {
            for (ClientCertificateType cct : ClientCertificateType.values()) {
                if (cct.id == id) {
                    return cct.name;
                }
            }
            return "UNDEFINED-CLIENT-CERTIFICATE-TYPE(" + (int)id + ")";
        }

        private static ClientCertificateType valueOf(byte id) {
            for (ClientCertificateType cct : ClientCertificateType.values()) {
                if (cct.id == id) {
                    return cct;
                }
            }

            return null;
        }

        private static String[] getKeyTypes(byte[] ids) {
            ArrayList<String> keyTypes = new ArrayList<>(3);
            for (byte id : ids) {
                ClientCertificateType cct = ClientCertificateType.valueOf(id);
                if (cct != null && cct.isAvailable) {
                    cct.keyAlgorithm.forEach(key -> {
                        if (!keyTypes.contains(key)) {
                            keyTypes.add(key);
                        }
                    });
                }
            }

            return keyTypes.toArray(new String[0]);
        }
    }

    /**
     * The "CertificateRequest" handshake message for SSL 3.0 and TLS 1.0/1.1.
     */
    static final class T10CertificateRequestMessage extends HandshakeMessage {
        final byte[] types;                 // certificate types
        final List<byte[]> authorities;     // certificate authorities

        T10CertificateRequestMessage(HandshakeContext handshakeContext,
                X509Certificate[] trustedCerts, KeyExchange keyExchange) {
            super(handshakeContext);

            this.authorities = new ArrayList<>(trustedCerts.length);
            for (X509Certificate cert : trustedCerts) {
                X500Principal x500Principal = cert.getSubjectX500Principal();
                authorities.add(x500Principal.getEncoded());
            }

            this.types = ClientCertificateType.CERT_TYPES;
        }

        T10CertificateRequestMessage(HandshakeContext handshakeContext,
                ByteBuffer m) throws IOException {
            super(handshakeContext);

            // struct {
            //     ClientCertificateType certificate_types<1..2^8-1>;
            //     DistinguishedName certificate_authorities<0..2^16-1>;
            // } CertificateRequest;
            if (m.remaining() < 4) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Incorrect CertificateRequest message: no sufficient data");
            }
            this.types = Record.getBytes8(m);

            int listLen = Record.getInt16(m);
            if (listLen > m.remaining()) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Incorrect CertificateRequest message:no sufficient data");
            }

            if (listLen > 0) {
                this.authorities = new LinkedList<>();
                while (listLen > 0) {
                    // opaque DistinguishedName<1..2^16-1>;
                    byte[] encoded = Record.getBytes16(m);
                    listLen -= (2 + encoded.length);
                    authorities.add(encoded);
                }
            } else {
                this.authorities = Collections.emptyList();
            }
        }

        String[] getKeyTypes() {
            return  ClientCertificateType.getKeyTypes(types);
        }

        // This method will throw IllegalArgumentException if the
        // X500Principal cannot be parsed.
        X500Principal[] getAuthorities() {
            X500Principal[] principals = new X500Principal[authorities.size()];
            int i = 0;

            for (byte[] encoded : authorities) {
                principals[i++] = new X500Principal(encoded);
            }

            return principals;
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.CERTIFICATE_REQUEST;
        }

        @Override
        public int messageLength() {
            int len = 1 + types.length + 2;
            for (byte[] encoded : authorities) {
                len += encoded.length + 2;
            }
            return len;
        }

        @Override
        public void send(HandshakeOutStream hos) throws IOException {
            hos.putBytes8(types);

            int listLen = 0;
            for (byte[] encoded : authorities) {
                listLen += encoded.length + 2;
            }

            hos.putInt16(listLen);
            for (byte[] encoded : authorities) {
                hos.putBytes16(encoded);
            }
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "CertificateRequest": '{'
                              "certificate types": {0}
                              "certificate authorities": {1}
                            '}'""",
                    Locale.ENGLISH);

            List<String> typeNames = new ArrayList<>(types.length);
            for (byte type : types) {
                typeNames.add(ClientCertificateType.nameOf(type));
            }

            List<String> authorityNames = new ArrayList<>(authorities.size());
            for (byte[] encoded : authorities) {
                try {
                    X500Principal principal = new X500Principal(encoded);
                    authorityNames.add(principal.toString());
                } catch (IllegalArgumentException iae) {
                    authorityNames.add("unparseable distinguished name: " + iae);
                }
            }
            Object[] messageFields = {
                typeNames,
                authorityNames
            };

            return messageFormat.format(messageFields);
        }
    }

    /**
     * The "CertificateRequest" handshake message producer for SSL 3.0 and
     * TLS 1.0/1.1.
     */
    private static final
            class T10CertificateRequestProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private T10CertificateRequestProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            X509Certificate[] caCerts =
                    shc.sslContext.getX509TrustManager().getAcceptedIssuers();
            T10CertificateRequestMessage crm = new T10CertificateRequestMessage(
                    shc, caCerts, shc.negotiatedCipherSuite.keyExchange);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Produced CertificateRequest handshake message", crm);
            }

            // Output the handshake message.
            crm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            //
            // update
            //
            shc.handshakeConsumers.put(SSLHandshake.CERTIFICATE.id,
                    SSLHandshake.CERTIFICATE);
            shc.handshakeConsumers.put(SSLHandshake.CERTIFICATE_VERIFY.id,
                    SSLHandshake.CERTIFICATE_VERIFY);

            // The handshake message has been delivered.
            return null;
        }
    }

    /**
     * The "CertificateRequest" handshake message consumer for SSL 3.0 and
     * TLS 1.0/1.1.
     */
    private static final
            class T10CertificateRequestConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private T10CertificateRequestConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // clean up this consumer
            chc.handshakeConsumers.remove(SSLHandshake.CERTIFICATE_REQUEST.id);
            chc.receivedCertReq = true;

            // If we're processing this message and the server's certificate
            // message consumer has not already run then this is a state
            // machine violation.
            if (chc.handshakeConsumers.containsKey(
                    SSLHandshake.CERTIFICATE.id)) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unexpected CertificateRequest handshake message");
            }

            SSLConsumer certStatCons = chc.handshakeConsumers.remove(
                    SSLHandshake.CERTIFICATE_STATUS.id);
            if (certStatCons != null) {
                // Stapling was active but no certificate status message
                // was sent.  We need to run the absence handler which will
                // check the certificate chain.
                CertificateStatus.handshakeAbsence.absent(context, null);
            }

            T10CertificateRequestMessage crm =
                    new T10CertificateRequestMessage(chc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming CertificateRequest handshake message", crm);
            }

            //
            // validate
            //
            // blank

            //
            // update
            //

            // An empty client Certificate handshake message may be allowed.
            chc.handshakeProducers.put(SSLHandshake.CERTIFICATE.id,
                    SSLHandshake.CERTIFICATE);

            X509ExtendedKeyManager km = chc.sslContext.getX509KeyManager();
            String clientAlias = null;

            try {
                if (chc.conContext.transport instanceof SSLSocketImpl) {
                    clientAlias = km.chooseClientAlias(crm.getKeyTypes(),
                        crm.getAuthorities(),
                        (SSLSocket) chc.conContext.transport);
                } else if (chc.conContext.transport instanceof SSLEngineImpl) {
                    clientAlias =
                        km.chooseEngineClientAlias(crm.getKeyTypes(),
                            crm.getAuthorities(),
                            (SSLEngine) chc.conContext.transport);
                }
            } catch (IllegalArgumentException iae) {
                chc.conContext.fatal(Alert.DECODE_ERROR,
                    "The distinguished names of the peer's "
                    + "certificate authorities could not be parsed",
                        iae);
            }

            if (clientAlias == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning("No available client authentication");
                }
                return;
            }

            PrivateKey clientPrivateKey = km.getPrivateKey(clientAlias);
            if (clientPrivateKey == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning("No available client private key");
                }
                return;
            }

            X509Certificate[] clientCerts = km.getCertificateChain(clientAlias);
            if ((clientCerts == null) || (clientCerts.length == 0)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning("No available client certificate");
                }
                return;
            }

            chc.handshakePossessions.add(
                    new X509Possession(clientPrivateKey, clientCerts));
            chc.handshakeProducers.put(SSLHandshake.CERTIFICATE_VERIFY.id,
                    SSLHandshake.CERTIFICATE_VERIFY);
        }
    }

    /**
     * The CertificateRequest handshake message for TLS 1.2.
     */
    static final class T12CertificateRequestMessage extends HandshakeMessage {
        final byte[] types;                 // certificate types
        final int[] algorithmIds;           // supported signature algorithms
        final List<byte[]> authorities;     // certificate authorities

        T12CertificateRequestMessage(HandshakeContext handshakeContext,
                X509Certificate[] trustedCerts, KeyExchange keyExchange,
                List<SignatureScheme> signatureSchemes) throws IOException {
            super(handshakeContext);

            this.types = ClientCertificateType.CERT_TYPES;

            if (signatureSchemes == null || signatureSchemes.isEmpty()) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "No signature algorithms specified for " +
                        "CertificateRequest handshake message");
            }
            this.algorithmIds = new int[signatureSchemes.size()];
            int i = 0;
            for (SignatureScheme scheme : signatureSchemes) {
                algorithmIds[i++] = scheme.id;
            }

            this.authorities = new ArrayList<>(trustedCerts.length);
            for (X509Certificate cert : trustedCerts) {
                X500Principal x500Principal = cert.getSubjectX500Principal();
                authorities.add(x500Principal.getEncoded());
            }
        }

        T12CertificateRequestMessage(HandshakeContext handshakeContext,
                ByteBuffer m) throws IOException {
            super(handshakeContext);

            // struct {
            //     ClientCertificateType certificate_types<1..2^8-1>;
            //     SignatureAndHashAlgorithm
            //       supported_signature_algorithms<2..2^16-2>;
            //     DistinguishedName certificate_authorities<0..2^16-1>;
            // } CertificateRequest;

            // certificate_authorities
            if (m.remaining() < 8) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Invalid CertificateRequest handshake message: " +
                        "no sufficient data");
            }
            this.types = Record.getBytes8(m);

            // supported_signature_algorithms
            if (m.remaining() < 6) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Invalid CertificateRequest handshake message: " +
                        "no sufficient data");
            }

            byte[] algs = Record.getBytes16(m);
            if (algs.length == 0 || (algs.length & 0x01) != 0) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Invalid CertificateRequest handshake message: " +
                        "incomplete signature algorithms");
            }

            this.algorithmIds = new int[(algs.length >> 1)];
            for (int i = 0, j = 0; i < algs.length;) {
                byte hash = algs[i++];
                byte sign = algs[i++];
                algorithmIds[j++] = ((hash & 0xFF) << 8) | (sign & 0xFF);
            }

            // certificate_authorities
            if (m.remaining() < 2) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Invalid CertificateRequest handshake message: " +
                        "no sufficient data");
            }

            int listLen = Record.getInt16(m);
            if (listLen > m.remaining()) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Invalid CertificateRequest message: no sufficient data");
            }

            if (listLen > 0) {
                this.authorities = new LinkedList<>();
                while (listLen > 0) {
                    // opaque DistinguishedName<1..2^16-1>;
                    byte[] encoded = Record.getBytes16(m);
                    listLen -= (2 + encoded.length);
                    authorities.add(encoded);
                }
            } else {
                this.authorities = Collections.emptyList();
            }
        }

        String[] getKeyTypes() {
            return ClientCertificateType.getKeyTypes(types);
        }

        // This method will throw IllegalArgumentException if the
        // X500Principal cannot be parsed.
        X500Principal[] getAuthorities() {
            X500Principal[] principals = new X500Principal[authorities.size()];
            int i = 0;

            for (byte[] encoded : authorities) {
                principals[i++] = new X500Principal(encoded);
            }

            return principals;
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.CERTIFICATE_REQUEST;
        }

        @Override
        public int messageLength() {
            int len = 1 + types.length + 2 + (algorithmIds.length << 1) + 2;
            for (byte[] encoded : authorities) {
                len += encoded.length + 2;
            }
            return len;
        }

        @Override
        public void send(HandshakeOutStream hos) throws IOException {
            hos.putBytes8(types);

            int listLen = 0;
            for (byte[] encoded : authorities) {
                listLen += encoded.length + 2;
            }

            hos.putInt16(algorithmIds.length << 1);
            for (int algorithmId : algorithmIds) {
                hos.putInt16(algorithmId);
            }

            hos.putInt16(listLen);
            for (byte[] encoded : authorities) {
                hos.putBytes16(encoded);
            }
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "CertificateRequest": '{'
                              "certificate types": {0}
                              "supported signature algorithms": {1}
                              "certificate authorities": {2}
                            '}'""",
                    Locale.ENGLISH);

            List<String> typeNames = new ArrayList<>(types.length);
            for (byte type : types) {
                typeNames.add(ClientCertificateType.nameOf(type));
            }

            List<String> algorithmNames = new ArrayList<>(algorithmIds.length);
            for (int algorithmId : algorithmIds) {
                algorithmNames.add(SignatureScheme.nameOf(algorithmId));
            }

            List<String> authorityNames = new ArrayList<>(authorities.size());
            for (byte[] encoded : authorities) {
                try {
                    X500Principal principal = new X500Principal(encoded);
                    authorityNames.add(principal.toString());
                } catch (IllegalArgumentException iae) {
                    authorityNames.add("unparseable distinguished name: " +
                        iae);
                }
            }
            Object[] messageFields = {
                typeNames,
                algorithmNames,
                authorityNames
            };

            return messageFormat.format(messageFields);
        }
    }

    /**
     * The "CertificateRequest" handshake message producer for TLS 1.2.
     */
    private static final
            class T12CertificateRequestProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private T12CertificateRequestProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext) context;

            // According to TLSv1.2 RFC, CertificateRequest message must
            // contain signature schemes supported for both:
            // handshake signatures and certificate signatures.
            // localSupportedSignAlgs and localSupportedCertSignAlgs have been
            // already updated when we set the negotiated protocol.
            List<SignatureScheme> certReqSignAlgs =
                    new ArrayList<>(shc.localSupportedSignAlgs);
            certReqSignAlgs.retainAll(shc.localSupportedCertSignAlgs);

            if (certReqSignAlgs.isEmpty()) {
                throw shc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No supported signature algorithm");
            }

            X509Certificate[] caCerts =
                    shc.sslContext.getX509TrustManager().getAcceptedIssuers();
            T12CertificateRequestMessage crm = new T12CertificateRequestMessage(
                    shc, caCerts, shc.negotiatedCipherSuite.keyExchange,
                    certReqSignAlgs);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Produced CertificateRequest handshake message", crm);
            }

            // Output the handshake message.
            crm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            //
            // update
            //
            shc.handshakeConsumers.put(SSLHandshake.CERTIFICATE.id,
                    SSLHandshake.CERTIFICATE);
            shc.handshakeConsumers.put(SSLHandshake.CERTIFICATE_VERIFY.id,
                    SSLHandshake.CERTIFICATE_VERIFY);

            // The handshake message has been delivered.
            return null;
        }
    }

    /**
     * The "CertificateRequest" handshake message consumer for TLS 1.2.
     */
    private static final
            class T12CertificateRequestConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private T12CertificateRequestConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // clean up this consumer
            chc.handshakeConsumers.remove(SSLHandshake.CERTIFICATE_REQUEST.id);
            chc.receivedCertReq = true;

            // If we're processing this message and the server's certificate
            // message consumer has not already run then this is a state
            // machine violation.
            if (chc.handshakeConsumers.containsKey(
                    SSLHandshake.CERTIFICATE.id)) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unexpected CertificateRequest handshake message");
            }

            SSLConsumer certStatCons = chc.handshakeConsumers.remove(
                    SSLHandshake.CERTIFICATE_STATUS.id);
            if (certStatCons != null) {
                // Stapling was active but no certificate status message
                // was sent.  We need to run the absence handler which will
                // check the certificate chain.
                CertificateStatus.handshakeAbsence.absent(context, null);
            }

            T12CertificateRequestMessage crm =
                    new T12CertificateRequestMessage(chc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming CertificateRequest handshake message", crm);
            }

            //
            // validate
            //
            // blank

            //
            // update
            //

            // An empty client Certificate handshake message may be allowed.
            chc.handshakeProducers.put(SSLHandshake.CERTIFICATE.id,
                    SSLHandshake.CERTIFICATE);

            List<SignatureScheme> signAlgs =
                    SignatureScheme.getSupportedAlgorithms(
                            chc.sslConfig,
                            chc.algorithmConstraints, chc.negotiatedProtocol,
                            crm.algorithmIds,
                            HANDSHAKE_SCOPE);

            List<SignatureScheme> signCertAlgs =
                    SignatureScheme.getSupportedAlgorithms(
                            chc.sslConfig,
                            chc.algorithmConstraints, chc.negotiatedProtocol,
                            crm.algorithmIds,
                            CERTIFICATE_SCOPE);

            if (signAlgs.isEmpty() || signCertAlgs.isEmpty()) {
                throw chc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                        "No supported signature algorithm");
            }

            chc.peerRequestedSignatureSchemes = signAlgs;
            chc.peerRequestedCertSignSchemes = signCertAlgs;
            chc.handshakeSession.setPeerSupportedSignatureAlgorithms(signCertAlgs);

            try {
                chc.peerSupportedAuthorities = crm.getAuthorities();
            } catch (IllegalArgumentException iae) {
                chc.conContext.fatal(Alert.DECODE_ERROR, "The "
                    + "distinguished names of the peer's certificate "
                    + "authorities could not be parsed", iae);
            }
            // For TLS 1.2, we no longer use the certificate_types field
            // from the CertificateRequest message to directly determine
            // the SSLPossession.  Instead, the choosePossession method
            // will use the accepted signature schemes in the message to
            // determine the set of acceptable certificate types to select from.
            SSLPossession pos = choosePossession(chc, crm);
            if (pos == null) {
                return;
            }

            chc.handshakePossessions.add(pos);
            chc.handshakeProducers.put(SSLHandshake.CERTIFICATE_VERIFY.id,
                    SSLHandshake.CERTIFICATE_VERIFY);
        }

        private static SSLPossession choosePossession(HandshakeContext hc,
                T12CertificateRequestMessage crm) {
            if (hc.peerRequestedCertSignSchemes == null ||
                    hc.peerRequestedCertSignSchemes.isEmpty()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning("No signature and hash algorithms " +
                            "in CertificateRequest");
                }
                return null;
            }

            // Put the CR key type into a more friendly format for searching
            List<String> crKeyTypes = new ArrayList<>(
                    Arrays.asList(crm.getKeyTypes()));
            // For TLS 1.2 only if RSA is a requested key type then we
            // should also allow RSASSA-PSS.
            if (crKeyTypes.contains("RSA")) {
                crKeyTypes.add("RSASSA-PSS");
            }

            String[] supportedKeyTypes = hc.peerRequestedCertSignSchemes
                    .stream()
                    .map(ss -> ss.keyAlgorithm)
                    .distinct()
                    .filter(ka -> SignatureScheme.getPreferableAlgorithm(   // Don't select a signature scheme unless
                            hc.algorithmConstraints,                        //  we will be able to produce
                            hc.peerRequestedSignatureSchemes,               //  a CertificateVerify message later
                            ka, hc.negotiatedProtocol) != null
                            || SSLLogger.logWarning("ssl,handshake",
                                    "Unable to produce CertificateVerify for key algorithm: " + ka))
                    .filter(ka -> {
                        var xa = X509Authentication.valueOfKeyAlgorithm(ka);
                        // Any auth object will have a set of allowed key types.
                        // This set should share at least one common algorithm with
                        // the CR's allowed key types.
                        return xa != null && !Collections.disjoint(crKeyTypes, Arrays.asList(xa.keyTypes))
                                || SSLLogger.logWarning("ssl,handshake", "Unsupported key algorithm: " + ka);
                    })
                    .toArray(String[]::new);

            SSLPossession pos = X509Authentication
                    .createPossession(hc, supportedKeyTypes);
            if (pos == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning("No available authentication scheme");
                }
            }
            return pos;
        }
    }

    /**
     * The CertificateRequest handshake message for TLS 1.3.
     */
    static final class T13CertificateRequestMessage extends HandshakeMessage {
        private final byte[] requestContext;
        private final SSLExtensions extensions;

        T13CertificateRequestMessage(
                HandshakeContext handshakeContext) {
            super(handshakeContext);

            this.requestContext = new byte[0];
            this.extensions = new SSLExtensions(this);
        }

        T13CertificateRequestMessage(HandshakeContext handshakeContext,
                ByteBuffer m) throws IOException {
            super(handshakeContext);

            // struct {
            //      opaque certificate_request_context<0..2^8-1>;
            //      Extension extensions<2..2^16-1>;
            //  } CertificateRequest;
            if (m.remaining() < 5) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Invalid CertificateRequest handshake message: " +
                        "no sufficient data");
            }
            this.requestContext = Record.getBytes8(m);

            if (m.remaining() < 4) {
                throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Invalid CertificateRequest handshake message: " +
                        "no sufficient extensions data");
            }
            SSLExtension[] enabledExtensions =
                handshakeContext.sslConfig.getEnabledExtensions(
                        SSLHandshake.CERTIFICATE_REQUEST);
            this.extensions = new SSLExtensions(this, m, enabledExtensions);
        }

        @Override
        SSLHandshake handshakeType() {
            return SSLHandshake.CERTIFICATE_REQUEST;
        }

        @Override
        int messageLength() {
            // In TLS 1.3, use of certain extensions is mandatory.
            return 1 + requestContext.length + extensions.length();
        }

        @Override
        void send(HandshakeOutStream hos) throws IOException {
            hos.putBytes8(requestContext);

            // In TLS 1.3, use of certain extensions is mandatory.
            extensions.send(hos);
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "CertificateRequest": '{'
                              "certificate_request_context": "{0}",
                              "extensions": [
                            {1}
                              ]
                            '}'""",
                Locale.ENGLISH);
            Object[] messageFields = {
                Utilities.toHexString(requestContext),
                Utilities.indent(Utilities.indent(extensions.toString()))
            };

            return messageFormat.format(messageFields);
        }
    }

    /**
     * The "CertificateRequest" handshake message producer for TLS 1.3.
     */
    private static final
            class T13CertificateRequestProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private T13CertificateRequestProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            T13CertificateRequestMessage crm =
                    new T13CertificateRequestMessage(shc);
            // Produce extensions for CertificateRequest handshake message.
            SSLExtension[] extTypes = shc.sslConfig.getEnabledExtensions(
                    SSLHandshake.CERTIFICATE_REQUEST, shc.negotiatedProtocol);
            crm.extensions.produce(shc, extTypes);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Produced CertificateRequest message", crm);
            }

            // Output the handshake message.
            crm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            //
            // update
            //
            shc.certRequestContext = crm.requestContext.clone();
            shc.handshakeConsumers.put(SSLHandshake.CERTIFICATE.id,
                    SSLHandshake.CERTIFICATE);
            shc.handshakeConsumers.put(SSLHandshake.CERTIFICATE_VERIFY.id,
                    SSLHandshake.CERTIFICATE_VERIFY);

            // The handshake message has been delivered.
            return null;
        }
    }

    /**
     * The "CertificateRequest" handshake message consumer for TLS 1.3.
     */
    private static final
            class T13CertificateRequestConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private T13CertificateRequestConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // clean up this consumer
            chc.handshakeConsumers.remove(SSLHandshake.CERTIFICATE_REQUEST.id);
            chc.receivedCertReq = true;

            // Ensure that the CertificateRequest has not been sent prior
            // to EncryptedExtensions
            if (chc.handshakeConsumers.containsKey(
                    SSLHandshake.ENCRYPTED_EXTENSIONS.id)) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Unexpected CertificateRequest handshake message");
            }

            T13CertificateRequestMessage crm =
                    new T13CertificateRequestMessage(chc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming CertificateRequest handshake message", crm);
            }

            //
            // validate
            //
            SSLExtension[] extTypes = chc.sslConfig.getEnabledExtensions(
                    SSLHandshake.CERTIFICATE_REQUEST);
            crm.extensions.consumeOnLoad(chc, extTypes);

            //
            // update
            //
            crm.extensions.consumeOnTrade(chc, extTypes);

            //
            // produce
            //
            chc.certRequestContext = crm.requestContext.clone();
            chc.handshakeProducers.put(SSLHandshake.CERTIFICATE.id,
                    SSLHandshake.CERTIFICATE);
            chc.handshakeProducers.put(SSLHandshake.CERTIFICATE_VERIFY.id,
                    SSLHandshake.CERTIFICATE_VERIFY);
        }
    }
}
