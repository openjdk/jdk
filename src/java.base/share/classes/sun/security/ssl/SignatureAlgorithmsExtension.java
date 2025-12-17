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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;

/**
 * Pack of the "signature_algorithms" extensions [RFC 5246].
 */
final class SignatureAlgorithmsExtension {
    static final HandshakeProducer chNetworkProducer =
            new CHSignatureSchemesProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new CHSignatureSchemesConsumer();
    static final HandshakeAbsence chOnLoadAbsence =
            new CHSignatureSchemesOnLoadAbsence();
    static final HandshakeConsumer chOnTradeConsumer =
            new CHSignatureSchemesUpdate();
    static final HandshakeAbsence chOnTradeAbsence =
            new CHSignatureSchemesOnTradeAbsence();

    static final HandshakeProducer crNetworkProducer =
            new CRSignatureSchemesProducer();
    static final ExtensionConsumer crOnLoadConsumer =
            new CRSignatureSchemesConsumer();
    static final HandshakeAbsence crOnLoadAbsence =
            new CRSignatureSchemesAbsence();
    static final HandshakeConsumer crOnTradeConsumer =
            new CRSignatureSchemesUpdate();

    static final SSLStringizer ssStringizer =
            new SignatureSchemesStringizer();

    /**
     * The "signature_algorithms" extension.
     */
    static final class SignatureSchemesSpec implements SSLExtensionSpec {
        final int[] signatureSchemes;

        SignatureSchemesSpec(List<SignatureScheme> schemes) {
            if (schemes != null) {
                signatureSchemes = new int[schemes.size()];
                int i = 0;
                for (SignatureScheme scheme : schemes) {
                    signatureSchemes[i++] = scheme.id;
                }
            } else {
                this.signatureSchemes = new int[0];
            }
        }

        SignatureSchemesSpec(HandshakeContext hc,
                ByteBuffer buffer) throws IOException {
            if (buffer.remaining() < 2) {      // 2: the length of the list
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid signature_algorithms: insufficient data"));
            }

            byte[] algs = Record.getBytes16(buffer);
            if (buffer.hasRemaining()) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid signature_algorithms: unknown extra data"));
            }

            if (algs.length == 0 || (algs.length & 0x01) != 0) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid signature_algorithms: incomplete data"));
            }

            int[] schemes = new int[algs.length / 2];
            for (int i = 0, j = 0; i < algs.length;) {
                byte hash = algs[i++];
                byte sign = algs[i++];
                schemes[j++] = ((hash & 0xFF) << 8) | (sign & 0xFF);
            }

            this.signatureSchemes = schemes;
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"signature schemes\": '['{0}']'", Locale.ENGLISH);

            if (signatureSchemes == null || signatureSchemes.length == 0) {
                Object[] messageFields = {
                        "<no supported signature schemes specified>"
                    };
                return messageFormat.format(messageFields);
            } else {
                StringBuilder builder = new StringBuilder(512);
                boolean isFirst = true;
                for (int pv : signatureSchemes) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        builder.append(", ");
                    }

                    builder.append(SignatureScheme.nameOf(pv));
                }

                Object[] messageFields = {
                        builder.toString()
                    };

                return messageFormat.format(messageFields);
            }
        }
    }

    private static final
            class SignatureSchemesStringizer implements SSLStringizer {
        @Override
        public String toString(HandshakeContext hc, ByteBuffer buffer) {
            try {
                return (new SignatureSchemesSpec(hc, buffer)).toString();
            } catch (IOException ioe) {
                // For debug logging only, so please swallow exceptions.
                return ioe.getMessage();
            }
        }
    }

    /**
     * Network data producer of a "signature_algorithms" extension in
     * the ClientHello handshake message.
     */
    private static final
            class CHSignatureSchemesProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private CHSignatureSchemesProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!chc.sslConfig.isAvailable(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS)) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine(
                        "Ignore unavailable signature_algorithms extension");
                }
                return null;
            }

            // Produce the extension.
            SignatureScheme.updateHandshakeLocalSupportedAlgs(chc);

            return produceNetworkLoad(chc,
                    SSLExtension.CH_SIGNATURE_ALGORITHMS,
                    SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT);
        }
    }

    /**
     * Network data consumer of a "signature_algorithms" extension in
     * the ClientHello handshake message.
     */
    private static final
            class CHSignatureSchemesConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private CHSignatureSchemesConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS)) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine(
                        "Ignore unavailable signature_algorithms extension");
                }
                return;     // ignore the extension
            }

            // Parse the extension.
            SignatureSchemesSpec spec = new SignatureSchemesSpec(shc, buffer);

            // Update the context.
            shc.handshakeExtensions.put(
                    SSLExtension.CH_SIGNATURE_ALGORITHMS, spec);

            // No impact on session resumption.
        }
    }

    /**
     * After session creation consuming of a "signature_algorithms"
     * extension in the ClientHello handshake message.
     */
    private static final class CHSignatureSchemesUpdate
            implements HandshakeConsumer {
        // Prevent instantiation of this class.
        private CHSignatureSchemesUpdate() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            SignatureSchemesSpec spec =
                    (SignatureSchemesSpec)shc.handshakeExtensions.get(
                            SSLExtension.CH_SIGNATURE_ALGORITHMS);
            if (spec == null) {
                // Ignore, no "signature_algorithms" extension requested.
                return;
            }

            updateHandshakeContext(shc, spec.signatureSchemes,
                    SSLExtension.CH_SIGNATURE_ALGORITHMS_CERT);

            if (!shc.isResumption &&
                    shc.negotiatedProtocol.useTLS13PlusSpec()) {
                if (shc.sslConfig.clientAuthType !=
                        ClientAuthType.CLIENT_AUTH_NONE) {
                    shc.handshakeProducers.putIfAbsent(
                            SSLHandshake.CERTIFICATE_REQUEST.id,
                            SSLHandshake.CERTIFICATE_REQUEST);
                }
                shc.handshakeProducers.put(
                        SSLHandshake.CERTIFICATE.id,
                        SSLHandshake.CERTIFICATE);
                shc.handshakeProducers.putIfAbsent(
                        SSLHandshake.CERTIFICATE_VERIFY.id,
                        SSLHandshake.CERTIFICATE_VERIFY);
            }
        }
    }

    /**
     * The absence processing if a "signature_algorithms" extension is
     * not present in the ClientHello handshake message.
     */
    private static final
            class CHSignatureSchemesOnLoadAbsence implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // This is a mandatory extension for certificate authentication
            // in TLS 1.3.
            //
            // We may support the server authentication other than X.509
            // certificate later.
            if (shc.negotiatedProtocol.useTLS13PlusSpec()) {
                throw shc.conContext.fatal(Alert.MISSING_EXTENSION,
                    "No mandatory signature_algorithms extension in the " +
                    "received ClientHello handshake message");
            }
        }
    }

    /**
     * The absence processing if a "signature_algorithms" extension is
     * not present in the ClientHello handshake message.
     */
    private static final
            class CHSignatureSchemesOnTradeAbsence implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The consuming happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            if (shc.negotiatedProtocol.useTLS12PlusSpec()) {
                // Use default hash and signature algorithm:
                //      {sha1,rsa}
                //      {sha1,dsa}
                //      {sha1,ecdsa}
                // Per RFC 5246, If the client supports only the default hash
                // and signature algorithms, it MAY omit the
                // signature_algorithms extension.  If the client does not
                // support the default algorithms, or supports other hash
                // and signature algorithms (and it is willing to use them
                // for verifying messages sent by the server, i.e., server
                // certificates and server key exchange), it MUST send the
                // signature_algorithms extension, listing the algorithms it
                // is willing to accept.
                List<SignatureScheme> schemes = Arrays.asList(
                        SignatureScheme.RSA_PKCS1_SHA1,
                        SignatureScheme.DSA_SHA1,
                        SignatureScheme.ECDSA_SHA1
                );

                shc.peerRequestedSignatureSchemes = schemes;
                if (shc.peerRequestedCertSignSchemes == null ||
                        shc.peerRequestedCertSignSchemes.isEmpty()) {
                    shc.peerRequestedCertSignSchemes = schemes;
                }

                // Use the default peer signature algorithms.
                shc.handshakeSession.setUseDefaultPeerSignAlgs();
            }
        }
    }

    /**
     * Network data producer of a "signature_algorithms" extension in
     * the CertificateRequest handshake message.
     */
    private static final
            class CRSignatureSchemesProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private CRSignatureSchemesProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // Is it a supported and enabled extension?
            //
            // Note that this is a mandatory extension for CertificateRequest
            // handshake message in TLS 1.3.
            if (!shc.sslConfig.isAvailable(
                    SSLExtension.CR_SIGNATURE_ALGORITHMS)) {
                throw shc.conContext.fatal(Alert.MISSING_EXTENSION,
                        "No available signature_algorithms extension " +
                        "for client certificate authentication");
            }

            // Produce the extension.
            // localSupportedSignAlgs and localSupportedCertSignAlgs have been
            // already updated when we set the negotiated protocol.
            return produceNetworkLoad(shc,
                    SSLExtension.CR_SIGNATURE_ALGORITHMS,
                    SSLExtension.CR_SIGNATURE_ALGORITHMS_CERT);
        }
    }

    /**
     * Network data consumer of a "signature_algorithms" extension in
     * the CertificateRequest handshake message.
     */
    private static final
            class CRSignatureSchemesConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private CRSignatureSchemesConsumer() {
            // blank
        }
        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // Is it a supported and enabled extension?
            //
            // Note that this is a mandatory extension for CertificateRequest
            // handshake message in TLS 1.3.
            if (!chc.sslConfig.isAvailable(
                    SSLExtension.CR_SIGNATURE_ALGORITHMS)) {
                throw chc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                        "No available signature_algorithms extension " +
                        "for client certificate authentication");
            }

            // Parse the extension.
            SignatureSchemesSpec spec = new SignatureSchemesSpec(chc, buffer);

            // Update the context.
            //
            // Note: the chc.peerRequestedSignatureSchemes will be updated
            // in the CRSignatureSchemesUpdate.consume() implementation.
            chc.handshakeExtensions.put(
                    SSLExtension.CR_SIGNATURE_ALGORITHMS, spec);

            // No impact on session resumption.
        }
    }

    /**
     * After session creation consuming of a "signature_algorithms"
     * extension in the CertificateRequest handshake message.
     */
    private static final class CRSignatureSchemesUpdate
            implements HandshakeConsumer {
        // Prevent instantiation of this class.
        private CRSignatureSchemesUpdate() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            SignatureSchemesSpec spec =
                    (SignatureSchemesSpec)chc.handshakeExtensions.get(
                            SSLExtension.CR_SIGNATURE_ALGORITHMS);
            if (spec == null) {
                // Ignore, no "signature_algorithms" extension requested.
                return;
            }

            updateHandshakeContext(chc, spec.signatureSchemes,
                    SSLExtension.CR_SIGNATURE_ALGORITHMS_CERT);
        }
    }

    /**
     * The absence processing if a "signature_algorithms" extension is
     * not present in the CertificateRequest handshake message.
     */
    private static final
            class CRSignatureSchemesAbsence implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The consuming happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // This is a mandatory extension for CertificateRequest handshake
            // message in TLS 1.3.
            throw chc.conContext.fatal(Alert.MISSING_EXTENSION,
                    "No mandatory signature_algorithms extension in the " +
                    "received CertificateRequest handshake message");
        }
    }

    // Updates given HandshakeContext with peer signature schemes.
    private static void updateHandshakeContext(HandshakeContext hc,
            int[] signatureSchemes, SSLExtension signatureAlgorithmsCertExt)
            throws SSLException {
        List<SignatureScheme> handshakeSS =
                SignatureScheme.getSupportedAlgorithms(
                        hc.sslConfig,
                        hc.algorithmConstraints,
                        hc.negotiatedProtocol,
                        signatureSchemes,
                        HANDSHAKE_SCOPE);

        if (handshakeSS.isEmpty()) {
            throw hc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No supported signature algorithm");
        }

        hc.peerRequestedSignatureSchemes = handshakeSS;

        // If no "signature_algorithms_cert" extension is present, then
        // the "signature_algorithms" extension also applies to
        // signatures appearing in certificates.
        SignatureSchemesSpec certSpec =
                (SignatureSchemesSpec) hc.handshakeExtensions.get(
                        signatureAlgorithmsCertExt);

        if (certSpec == null) {
            List<SignatureScheme> certSS =
                    SignatureScheme.getSupportedAlgorithms(
                            hc.sslConfig,
                            hc.algorithmConstraints,
                            hc.negotiatedProtocol,
                            signatureSchemes,
                            CERTIFICATE_SCOPE);

            if (certSS.isEmpty()) {
                throw hc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                        "No supported signature algorithm");
            }

            hc.peerRequestedCertSignSchemes = certSS;
            hc.handshakeSession.setPeerSupportedSignatureAlgorithms(certSS);
        }
    }

    /**
     * Produce network load and update context.
     *
     * @param hc HandshakeContext
     * @param signatureAlgorithmsExt "signature_algorithms" extension
     * @param signatureAlgorithmsCertExt "signature_algorithms_cert"
     *         extension
     * @return network load as byte array
     */
    private static byte[] produceNetworkLoad(
            HandshakeContext hc, SSLExtension signatureAlgorithmsExt,
            SSLExtension signatureAlgorithmsCertExt) throws IOException {

        List<SignatureScheme> sigAlgs;

        // If we don't produce "signature_algorithms_cert" extension, then
        // the "signature_algorithms" extension should contain signatures
        // supported for both: handshake signatures and certificate signatures.
        if (hc.sslConfig.isAvailable(signatureAlgorithmsCertExt)) {
            sigAlgs = hc.localSupportedSignAlgs;
        } else {
            sigAlgs = new ArrayList<>(hc.localSupportedSignAlgs);
            sigAlgs.retainAll(hc.localSupportedCertSignAlgs);
        }

        int vectorLen = SignatureScheme.sizeInRecord() * sigAlgs.size();
        byte[] extData = new byte[vectorLen + 2];
        ByteBuffer m = ByteBuffer.wrap(extData);
        Record.putInt16(m, vectorLen);

        for (SignatureScheme ss : sigAlgs) {
            Record.putInt16(m, ss.id);
        }

        // Update the context.
        hc.handshakeExtensions.put(
                signatureAlgorithmsExt, new SignatureSchemesSpec(sigAlgs));

        return extData;
    }
}
