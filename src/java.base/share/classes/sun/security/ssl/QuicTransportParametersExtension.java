/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.quic.QuicTransportException;
import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLHandshake.HandshakeMessage;

/**
 * Pack of the "quic_transport_parameters" extensions [RFC 9001].
 */
final class QuicTransportParametersExtension {

    static final HandshakeProducer chNetworkProducer =
            new T13CHQuicParametersProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new T13CHQuicParametersConsumer();
    static final HandshakeAbsence chOnLoadAbsence =
            new T13CHQuicParametersAbsence();
    static final HandshakeProducer eeNetworkProducer =
            new T13EEQuicParametersProducer();
    static final ExtensionConsumer eeOnLoadConsumer =
            new T13EEQuicParametersConsumer();
    static final HandshakeAbsence eeOnLoadAbsence =
            new T13EEQuicParametersAbsence();

    private static final class T13CHQuicParametersProducer
            implements HandshakeProducer {
        // Prevent instantiation of this class.
        private T13CHQuicParametersProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                              HandshakeMessage message) throws IOException {

            ClientHandshakeContext chc = (ClientHandshakeContext) context;
            if (!chc.sslConfig.isQuic) {
                return null;
            }
            QuicTLSEngineImpl quicTLSEngine =
                    (QuicTLSEngineImpl) chc.conContext.transport;

            return quicTLSEngine.getLocalQuicTransportParameters();
        }

    }

    private static final class T13CHQuicParametersConsumer
            implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private T13CHQuicParametersConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                            HandshakeMessage message, ByteBuffer buffer)
                throws IOException {
            ServerHandshakeContext shc = (ServerHandshakeContext) context;
            if (!shc.sslConfig.isQuic) {
                throw shc.conContext.fatal(Alert.UNSUPPORTED_EXTENSION,
                        "Client sent the quic_transport_parameters " +
                                "extension in a non-QUIC context");
            }
            QuicTLSEngineImpl quicTLSEngine =
                    (QuicTLSEngineImpl) shc.conContext.transport;
            try {
                quicTLSEngine.processRemoteQuicTransportParameters(buffer);
            } catch (QuicTransportException e) {
                throw shc.conContext.fatal(Alert.DECODE_ERROR, e);
            }

        }
    }

    private static final class T13CHQuicParametersAbsence
            implements HandshakeAbsence {
        // Prevent instantiation of this class.
        private T13CHQuicParametersAbsence() {
        }

        @Override
        public void absent(ConnectionContext context,
                           HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            if (shc.sslConfig.isQuic) {
                // RFC 9001: endpoints MUST send quic_transport_parameters
                throw shc.conContext.fatal(
                        Alert.MISSING_EXTENSION,
                        "Client did not send QUIC transport parameters");
            }
        }
    }

    private static final class T13EEQuicParametersProducer
            implements HandshakeProducer {
        // Prevent instantiation of this class.
        private T13EEQuicParametersProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                              HandshakeMessage message) {

            ServerHandshakeContext shc = (ServerHandshakeContext) context;
            if (!shc.sslConfig.isQuic) {
                return null;
            }
            QuicTLSEngineImpl quicTLSEngine =
                    (QuicTLSEngineImpl) shc.conContext.transport;

            return quicTLSEngine.getLocalQuicTransportParameters();
        }
    }

    private static final class T13EEQuicParametersConsumer
            implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private T13EEQuicParametersConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                            HandshakeMessage message, ByteBuffer buffer)
                throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext) context;
            if (!chc.sslConfig.isQuic) {
                throw chc.conContext.fatal(Alert.UNSUPPORTED_EXTENSION,
                        "Server sent the quic_transport_parameters " +
                                "extension in a non-QUIC context");
            }
            QuicTLSEngineImpl quicTLSEngine =
                    (QuicTLSEngineImpl) chc.conContext.transport;
            try {
                quicTLSEngine.processRemoteQuicTransportParameters(buffer);
            } catch (QuicTransportException e) {
                throw chc.conContext.fatal(Alert.DECODE_ERROR, e);
            }
        }
    }

    private static final class T13EEQuicParametersAbsence
            implements HandshakeAbsence {
        // Prevent instantiation of this class.
        private T13EEQuicParametersAbsence() {
        }

        @Override
        public void absent(ConnectionContext context,
                           HandshakeMessage message) throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext) context;

            if (chc.sslConfig.isQuic) {
                // RFC 9001: endpoints MUST send quic_transport_parameters
                throw chc.conContext.fatal(
                        Alert.MISSING_EXTENSION,
                        "Server did not send QUIC transport parameters");
            }
        }
    }
}
