/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javax.net.ssl.SSLProtocolException;
import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;

/**
 * Pack of the "compress_certificate" extensions [RFC 5246].
 */
final class CompressCertExtension {

    static final HandshakeProducer chNetworkProducer =
            new CHCompressCertificateProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new CHCompressCertificateConsumer();

    static final HandshakeProducer crNetworkProducer =
            new CRCompressCertificateProducer();
    static final ExtensionConsumer crOnLoadConsumer =
            new CRCompressCertificateConsumer();

    static final SSLStringizer ccStringizer =
            new CompressCertificateStringizer();

    /**
     * The "signature_algorithms" extension.
     */
    static final class CertCompressionSpec implements SSLExtensionSpec {

        private final int[] compressionAlgorithms;  // non-null

        CertCompressionSpec(
                Map<Integer, Function<byte[], byte[]>> certInflaters) {
            compressionAlgorithms = new int[certInflaters.size()];
            int i = 0;
            for (Integer id : certInflaters.keySet()) {
                compressionAlgorithms[i++] = id;
            }
        }

        CertCompressionSpec(HandshakeContext hc,
                ByteBuffer buffer) throws IOException {
            if (buffer.remaining() < 2) {      // 2: the length of the list
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                                "Invalid compress_certificate: insufficient data"));
            }

            byte[] algs = Record.getBytes8(buffer);
            if (buffer.hasRemaining()) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                                "Invalid compress_certificate: unknown extra data"));
            }

            if (algs.length == 0 || (algs.length & 0x01) != 0) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                                "Invalid compress_certificate: incomplete data"));
            }

            int[] compressionAlgs = new int[algs.length / 2];
            for (int i = 0, j = 0; i < algs.length; ) {
                byte hash = algs[i++];
                byte sign = algs[i++];
                compressionAlgs[j++] = ((hash & 0xFF) << 8) | (sign & 0xFF);
            }

            this.compressionAlgorithms = compressionAlgs;
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    "\"compression algorithms\": '['{0}']'", Locale.ENGLISH);

            if (compressionAlgorithms.length == 0) {
                Object[] messageFields = {
                        "<no supported compression algorithms specified>"
                };
                return messageFormat.format(messageFields);
            } else {
                StringBuilder builder = new StringBuilder(512);
                boolean isFirst = true;
                for (int ca : compressionAlgorithms) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        builder.append(", ");
                    }

                    builder.append(CompressionAlgorithm.nameOf(ca));
                }

                Object[] messageFields = {
                        builder.toString()
                };

                return messageFormat.format(messageFields);
            }
        }
    }

    private static final
    class CompressCertificateStringizer implements SSLStringizer {

        @Override
        public String toString(HandshakeContext hc, ByteBuffer buffer) {
            try {
                return (new CertCompressionSpec(hc, buffer)).toString();
            } catch (IOException ioe) {
                // For debug logging only, so please swallow exceptions.
                return ioe.getMessage();
            }
        }
    }

    /**
     * Network data producer of a "compress_certificate" extension in
     * the ClientHello handshake message.
     */
    private static final
    class CHCompressCertificateProducer implements HandshakeProducer {

        // Prevent instantiation of this class.
        private CHCompressCertificateProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in client side only.
            return produceCompCertExt(context,
                    SSLExtension.CH_COMPRESS_CERTIFICATE);
        }
    }

    /**
     * Network data consumer of a "compress_certificate" extension in
     * the ClientHello handshake message.
     */
    private static final
    class CHCompressCertificateConsumer implements ExtensionConsumer {

        // Prevent instantiation of this class.
        private CHCompressCertificateConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message, ByteBuffer buffer)
                throws IOException {
            // The consuming happens in server side only.
            consumeCompCertExt(context, buffer,
                    SSLExtension.CH_COMPRESS_CERTIFICATE);
        }
    }

    /**
     * Network data producer of a "compress_certificate" extension in
     * the CertificateRequest handshake message.
     */
    private static final
    class CRCompressCertificateProducer implements HandshakeProducer {

        // Prevent instantiation of this class.
        private CRCompressCertificateProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            return produceCompCertExt(context,
                    SSLExtension.CR_COMPRESS_CERTIFICATE);
        }
    }

    /**
     * Network data consumer of a "compress_certificate" extension in
     * the CertificateRequest handshake message.
     */
    private static final
    class CRCompressCertificateConsumer implements ExtensionConsumer {

        // Prevent instantiation of this class.
        private CRCompressCertificateConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message, ByteBuffer buffer)
                throws IOException {
            // The consuming happens in client side only.
            consumeCompCertExt(context, buffer,
                    SSLExtension.CR_COMPRESS_CERTIFICATE);
        }
    }

    private static byte[] produceCompCertExt(
            ConnectionContext context, SSLExtension extension)
            throws IOException {

        HandshakeContext hc = (HandshakeContext) context;
        // Is it a supported and enabled extension?
        if (!hc.sslConfig.isAvailable(extension)) {
            if (SSLLogger.isOn() && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Ignore unavailable " +
                        "compress_certificate extension");
            }
            return null;
        }

        // Produce the extension.
        if (hc.certInflaters == null) {
            hc.certInflaters =
                    CompressionAlgorithm.findInflaters(hc.sslConfig);
        }

        if (hc.certInflaters.isEmpty()) {
            if (SSLLogger.isOn() && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Ignore unsupported " +
                        "compress_certificate extension");
            }
            return null;
        }

        int vectorLen = CompressionAlgorithm.sizeInRecord() *
                hc.certInflaters.size();
        byte[] extData = new byte[vectorLen + 1];
        ByteBuffer m = ByteBuffer.wrap(extData);
        Record.putInt8(m, vectorLen);
        for (Integer algId : hc.certInflaters.keySet()) {
            Record.putInt16(m, algId);
        }

        // Update the context.
        hc.handshakeExtensions.put(
                extension, new CertCompressionSpec(hc.certInflaters));

        return extData;
    }

    private static void consumeCompCertExt(ConnectionContext context,
            ByteBuffer buffer, SSLExtension extension) throws IOException {

        HandshakeContext hc = (HandshakeContext) context;
        // Is it a supported and enabled extension?
        if (!hc.sslConfig.isAvailable(extension)) {
            if (SSLLogger.isOn() && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Ignore unavailable " +
                        "compress_certificate extension");
            }
            return;     // ignore the extension
        }

        if (hc.sslConfig.certDeflaters == null ||
                hc.sslConfig.certDeflaters.isEmpty()) {
            if (SSLLogger.isOn() && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Ignore unsupported " +
                        "compress_certificate extension");
            }
            return;     // ignore the extension
        }

        // Parse the extension.
        CertCompressionSpec spec = new CertCompressionSpec(hc, buffer);

        // Update the context.
        hc.certDeflater = CompressionAlgorithm.selectDeflater(
                hc.sslConfig, spec.compressionAlgorithms);
        if (hc.certDeflater == null) {
            if (SSLLogger.isOn() && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Ignore, no supported " +
                        "certificate compression algorithms");
            }
        }
        // No impact on session resumption.
    }
}
