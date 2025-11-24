/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.util.HexDumpEncoder;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;

/**
 * Pack of the CompressedCertificate handshake message.
 */
final class CompressedCertificate {
    static final SSLConsumer handshakeConsumer =
        new CompressedCertConsumer();
    static final HandshakeProducer handshakeProducer =
        new CompressedCertProducer();

    /**
     * The CompressedCertificate handshake message for TLS 1.3.
     */
    static final class CompressedCertMessage extends HandshakeMessage {
        private final int algorithmId;
        private final int uncompressedLength;
        private final byte[] compressedCert;

        CompressedCertMessage(HandshakeContext context,
                int algorithmId, int uncompressedLength,
                byte[] compressedCert) {
            super(context);

            this.algorithmId = algorithmId;
            this.uncompressedLength = uncompressedLength;
            this.compressedCert = compressedCert;
        }

        CompressedCertMessage(HandshakeContext handshakeContext,
                ByteBuffer m) throws IOException {
            super(handshakeContext);

            // struct {
            //     CertificateCompressionAlgorithm algorithm;
            //     uint24 uncompressed_length;
            //     opaque compressed_certificate_message<1..2^24-1>;
            // } CompressedCertificate;
            if (m.remaining() < 9) {
                throw new SSLProtocolException(
                        "Invalid CompressedCertificate message: " +
                        "insufficient data (length=" + m.remaining() + ")");
            }
            this.algorithmId = Record.getInt16(m);
            this.uncompressedLength = Record.getInt24(m);
            this.compressedCert = Record.getBytes24(m);

            if (m.hasRemaining()) {
                throw handshakeContext.conContext.fatal(
                        Alert.HANDSHAKE_FAILURE,
                        "Invalid CompressedCertificate message: " +
                        "unknown extra data");
            }
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.COMPRESSED_CERTIFICATE;
        }

        @Override
        public int messageLength() {
            return 8 + compressedCert.length;
        }

        @Override
        public void send(HandshakeOutStream hos) throws IOException {
            hos.putInt16(algorithmId);
            hos.putInt24(uncompressedLength);
            hos.putBytes24(compressedCert);
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "CompressedCertificate": '{'
                              "algorithm": "{0}",
                              "uncompressed_length": {1}
                              "compressed_certificate_message": [
                            {2}
                              ]
                            '}'""",
                Locale.ENGLISH);

            HexDumpEncoder hexEncoder = new HexDumpEncoder();
            Object[] messageFields = {
                CompressionAlgorithm.nameOf(algorithmId),
                uncompressedLength,
                Utilities.indent(hexEncoder.encode(compressedCert), "    ")
            };

            return messageFormat.format(messageFields);
        }
    }

    /**
     * The "Certificate" handshake message producer for TLS 1.3.
     */
    private static final
            class CompressedCertProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private CompressedCertProducer() {
            // blank
        }

        // Note this is a special producer, which can only be called from
        // the CertificateMessage producer.  The input 'message' parameter
        // represents the Certificate handshake message.
        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in handshake context only.
            HandshakeContext hc = (HandshakeContext)context;

            // Compress the Certificate message.
            HandshakeOutStream hos = new HandshakeOutStream(null);
            message.send(hos);
            byte[] certMsg = hos.toByteArray();
            byte[] compressedCertMsg =
                    hc.certDeflater.getValue().apply(certMsg);
            if (compressedCertMsg == null || compressedCertMsg.length == 0) {
                throw hc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                        "No compressed Certificate data");
            }

            CompressedCertMessage ccm = new CompressedCertMessage(hc,
                    hc.certDeflater.getKey(), certMsg.length,
                    compressedCertMsg);

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Produced Compressed Certificate message", ccm);
            }

            ccm.write(hc.handshakeOutput);
            hc.handshakeOutput.flush();

            // The handshake message has been delivered.
            return null;
        }
    }

    /**
     * The "Certificate" handshake message consumer for TLS 1.3.
     */
    private static final class CompressedCertConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private CompressedCertConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            // The consuming happens in handshake context only.
            HandshakeContext hc = (HandshakeContext)context;

            // clean up this consumer
            hc.handshakeConsumers.remove(SSLHandshake.COMPRESSED_CERTIFICATE.id);
            hc.handshakeConsumers.remove(SSLHandshake.CERTIFICATE.id);

            // Parse the handshake message
            CompressedCertMessage ccm = new CompressedCertMessage(hc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Consuming CompressedCertificate handshake message", ccm);
            }

            // check the compression algorithm
            Function<byte[], byte[]> inflater =
                    hc.certInflaters.get(ccm.algorithmId);
            if (inflater == null) {
                throw hc.conContext.fatal(Alert.BAD_CERTIFICATE,
                    "Unsupported certificate compression algorithm");
            }

            // decompress
            byte[] certificateMessage = inflater.apply(ccm.compressedCert);

            // check the uncompressed length
            if (certificateMessage == null ||
                    certificateMessage.length != ccm.uncompressedLength) {
                throw hc.conContext.fatal(Alert.BAD_CERTIFICATE,
                    "Improper certificate compression");
            }

            // Call the Certificate handshake message consumer.
            CertificateMessage.t13HandshakeConsumer.consume(hc,
                    ByteBuffer.wrap(certificateMessage));
        }
    }
}
