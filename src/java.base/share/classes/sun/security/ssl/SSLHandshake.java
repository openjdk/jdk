/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import javax.net.ssl.SSLException;

/**
 * An enum of the defined TLS handshake message types.
 * <p>
 * These are defined in the IANA TLS Parameters.
 * https://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-parameters-7
 * <p>
 * Most of these come from the SLS/TLS specs in RFCs 6601/2246/4346/8446 and
 * friends.  Others are called out where defined.
 */
enum SSLHandshake implements SSLConsumer, HandshakeProducer {
    @SuppressWarnings({"unchecked", "rawtypes"})
    HELLO_REQUEST ((byte)0x00, "hello_request",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            HelloRequest.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            HelloRequest.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    CLIENT_HELLO ((byte)0x01, "client_hello",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ClientHello.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ClientHello.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_13
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    SERVER_HELLO ((byte)0x02, "server_hello",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ServerHello.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ServerHello.t12HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    ),
                    new SimpleImmutableEntry<>(
                            ServerHello.t13HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            }),

    // Even though there is a TLS HandshakeType entry for
    // hello_retry_request_RESERVED (0x06), the TLSv1.3 (RFC 8446)
    // HelloRetryRequest is actually a ServerHello with a specific Random value
    // (Section 4.1.3).
    @SuppressWarnings({"unchecked", "rawtypes"})
    HELLO_RETRY_REQUEST ((byte)0x02, "hello_retry_request",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            // Use ServerHello consumer
                            ServerHello.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ServerHello.hrrHandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    HELLO_VERIFY_REQUEST        ((byte)0x03, "hello_verify_request",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            HelloVerifyRequest.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            HelloVerifyRequest.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    NEW_SESSION_TICKET          ((byte)0x04, "new_session_ticket",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            NewSessionTicket.handshake12Consumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    ),
                    new SimpleImmutableEntry<>(
                            NewSessionTicket.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            NewSessionTicket.handshake12Producer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            }),

    END_OF_EARLY_DATA           ((byte)0x05, "end_of_early_data"),

    @SuppressWarnings({"unchecked", "rawtypes"})
    ENCRYPTED_EXTENSIONS        ((byte)0x08, "encrypted_extensions",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            EncryptedExtensions.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            EncryptedExtensions.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            }),

    // RFC 9147 - DTLS 1.3
    REQUEST_CONNECTION_ID       ((byte)0x09, "request_connection_id"),
    NEW_CONNECTION_ID           ((byte)0x0a, "new_connection_id"),

    @SuppressWarnings({"unchecked", "rawtypes"})
    CERTIFICATE                 ((byte)0x0B, "certificate",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateMessage.t12HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateMessage.t13HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateMessage.t12HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateMessage.t13HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    SERVER_KEY_EXCHANGE         ((byte)0x0C, "server_key_exchange",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ServerKeyExchange.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ServerKeyExchange.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    CERTIFICATE_REQUEST         ((byte)0x0D, "certificate_request",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateRequest.t10HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_11
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateRequest.t12HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_12
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateRequest.t13HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateRequest.t10HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_11
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateRequest.t12HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_12
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateRequest.t13HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    SERVER_HELLO_DONE           ((byte)0x0E, "server_hello_done",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ServerHelloDone.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ServerHelloDone.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    CERTIFICATE_VERIFY          ((byte)0x0F, "certificate_verify",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateVerify.s30HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_30
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateVerify.t10HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_10_11
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateVerify.t12HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_12
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateVerify.t13HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateVerify.s30HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_30
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateVerify.t10HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_10_11
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateVerify.t12HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_12
                    ),
                    new SimpleImmutableEntry<>(
                            CertificateVerify.t13HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            }),

    @SuppressWarnings({"unchecked", "rawtypes"})
    CLIENT_KEY_EXCHANGE         ((byte)0x10, "client_key_exchange",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ClientKeyExchange.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            ClientKeyExchange.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            }),

    // RFC 9261 - Exported Authenticators
    CLIENT_CERTIFICATE_REQUEST  ((byte)0x11, "client_certificate_request"),

    @SuppressWarnings({"unchecked", "rawtypes"})
    FINISHED                    ((byte)0x14, "finished",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            Finished.t12HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    ),
                    new SimpleImmutableEntry<>(
                            Finished.t13HandshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            Finished.t12HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    ),
                    new SimpleImmutableEntry<>(
                            Finished.t13HandshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            }),

    CERTIFICATE_URL             ((byte)0x15, "certificate_url"),

    @SuppressWarnings({"unchecked", "rawtypes"})
    CERTIFICATE_STATUS          ((byte)0x16, "certificate_status",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateStatus.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateStatus.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            CertificateStatus.handshakeAbsence,
                            ProtocolVersion.PROTOCOLS_TO_12
                    )
            }),

    SUPPLEMENTAL_DATA           ((byte)0x17, "supplemental_data"),

    @SuppressWarnings({"unchecked", "rawtypes"})
    KEY_UPDATE                  ((byte)0x18, "key_update",
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            KeyUpdate.handshakeConsumer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            },
            new Map.Entry[] {
                    new SimpleImmutableEntry<>(
                            KeyUpdate.handshakeProducer,
                            ProtocolVersion.PROTOCOLS_OF_13
                    )
            }),

    // RFC 8879 - TLS Certificate Compression
    COMPRESSED_CERTIFICATE      ((byte)0x19, "compressed_certificate"),

    // RFC 8870 - Encrypted Key Transport for DTLS/Secure RTP
    EKT_KEY                     ((byte)0x1A, "ekt_key"),

    MESSAGE_HASH                ((byte)0xFE, "message_hash"),
    NOT_APPLICABLE              ((byte)0xFF, "not_applicable");

    final byte id;
    final String name;
    final Map.Entry<SSLConsumer, ProtocolVersion[]>[] handshakeConsumers;
    final Map.Entry<HandshakeProducer, ProtocolVersion[]>[] handshakeProducers;
    final Map.Entry<HandshakeAbsence, ProtocolVersion[]>[] handshakeAbsences;

    @SuppressWarnings({"unchecked", "rawtypes"})
    SSLHandshake(byte id, String name) {
        this(id, name,
                new Map.Entry[0],
                new Map.Entry[0],
                new Map.Entry[0]);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    SSLHandshake(byte id, String name,
        Map.Entry<SSLConsumer, ProtocolVersion[]>[] handshakeConsumers,
        Map.Entry<HandshakeProducer, ProtocolVersion[]>[] handshakeProducers) {

        this(id, name, handshakeConsumers, handshakeProducers,
                new Map.Entry[0]);
    }

    SSLHandshake(byte id, String name,
        Map.Entry<SSLConsumer, ProtocolVersion[]>[] handshakeConsumers,
        Map.Entry<HandshakeProducer, ProtocolVersion[]>[] handshakeProducers,
        Map.Entry<HandshakeAbsence, ProtocolVersion[]>[] handshakeAbsence) {

        this.id = id;
        this.name = name;
        this.handshakeConsumers = handshakeConsumers;
        this.handshakeProducers = handshakeProducers;
        this.handshakeAbsences = handshakeAbsence;
    }

    @Override
    public void consume(ConnectionContext context,
            ByteBuffer message) throws IOException {
        SSLConsumer hc = getHandshakeConsumer(context);
        if (hc != null) {
            hc.consume(context, message);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported handshake consumer: " + this.name);
        }
    }

    private SSLConsumer getHandshakeConsumer(ConnectionContext context) {
        if (handshakeConsumers.length == 0) {
            return null;
        }

        // The consuming happens in handshake context only.
        HandshakeContext hc = (HandshakeContext)context;
        ProtocolVersion protocolVersion;
        if ((hc.negotiatedProtocol == null) ||
                (hc.negotiatedProtocol == ProtocolVersion.NONE)) {
            if (hc.conContext.isNegotiated &&
                    hc.conContext.protocolVersion != ProtocolVersion.NONE) {
                protocolVersion = hc.conContext.protocolVersion;
            } else {
                protocolVersion = hc.maximumActiveProtocol;
            }
        } else {
            protocolVersion = hc.negotiatedProtocol;
        }

        for (Map.Entry<SSLConsumer,
                ProtocolVersion[]> phe : handshakeConsumers) {
            for (ProtocolVersion pv : phe.getValue()) {
                if (protocolVersion == pv) {
                    return phe.getKey();
                }
            }
        }

        return null;
    }

    @Override
    public byte[] produce(ConnectionContext context,
            HandshakeMessage message) throws IOException {
        HandshakeProducer hp = getHandshakeProducer(context);
        if (hp != null) {
            return hp.produce(context, message);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported handshake producer: " + this.name);
        }
    }

    private HandshakeProducer getHandshakeProducer(
            ConnectionContext context) {
        if (handshakeProducers.length == 0) {
            return null;
        }

        // The consuming happens in handshake context only.
        HandshakeContext hc = (HandshakeContext)context;
        ProtocolVersion protocolVersion;
        if ((hc.negotiatedProtocol == null) ||
                (hc.negotiatedProtocol == ProtocolVersion.NONE)) {
            if (hc.conContext.isNegotiated &&
                    hc.conContext.protocolVersion != ProtocolVersion.NONE) {
                protocolVersion = hc.conContext.protocolVersion;
            } else {
                protocolVersion = hc.maximumActiveProtocol;
            }
        } else {
            protocolVersion = hc.negotiatedProtocol;
        }

        for (Map.Entry<HandshakeProducer,
                ProtocolVersion[]> phe : handshakeProducers) {
            for (ProtocolVersion pv : phe.getValue()) {
                if (protocolVersion == pv) {
                    return phe.getKey();
                }
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    static String nameOf(byte id) {
        // If two handshake message share the same handshake type, returns
        // the first handshake message name.
        //
        // It is not a big issue at present as only ServerHello and
        // HellRetryRequest share a handshake type.
        for (SSLHandshake hs : SSLHandshake.values()) {
            if (hs.id == id) {
                return hs.name;
            }
        }

        return "UNKNOWN-HANDSHAKE-MESSAGE(" + id + ")";
    }

    static boolean isKnown(byte id) {
        for (SSLHandshake hs : SSLHandshake.values()) {
            if (hs.id == id && id != NOT_APPLICABLE.id) {
                return true;
            }
        }

        return false;
    }

    static final void kickstart(HandshakeContext context) throws IOException {
        if (context instanceof ClientHandshakeContext) {
            // For initial handshaking, including session resumption,
            // ClientHello message is used as the kickstart message.
            //
            // (D)TLS 1.2 and older protocols support renegotiation on existing
            // connections.  A ClientHello messages is used to kickstart the
            // renegotiation.
            //
            // (D)TLS 1.3 forbids renegotiation.  The post-handshake KeyUpdate
            // message is used to update the sending cryptographic keys.
            if (context.conContext.isNegotiated &&
                    context.conContext.protocolVersion.useTLS13PlusSpec()) {
                // Use KeyUpdate message for renegotiation.
                KeyUpdate.kickstartProducer.produce(context);
            } else {
                // Using ClientHello message for the initial handshaking
                // (including session resumption) or renegotiation.
                // SSLHandshake.CLIENT_HELLO.produce(context);
                ClientHello.kickstartProducer.produce(context);
            }
        } else {
            // The server side can deliver kickstart message after the
            // connection has been established.
            //
            // (D)TLS 1.2 and older protocols use HelloRequest to begin a
            // negotiation process anew.
            //
            // While (D)TLS 1.3 uses the post-handshake KeyUpdate message
            // to update the sending cryptographic keys.
            if (context.conContext.protocolVersion.useTLS13PlusSpec()) {
                // Use KeyUpdate message for renegotiation.
                KeyUpdate.kickstartProducer.produce(context);
            } else {
                // SSLHandshake.HELLO_REQUEST.produce(context);
                HelloRequest.kickstartProducer.produce(context);
            }
        }
    }

    /**
     * A (transparent) specification of handshake message.
     */
    abstract static class HandshakeMessage {
        final HandshakeContext      handshakeContext;

        HandshakeMessage(HandshakeContext handshakeContext) {
            this.handshakeContext = handshakeContext;
        }

        abstract SSLHandshake handshakeType();
        abstract int messageLength();
        abstract void send(HandshakeOutStream hos) throws IOException;

        void write(HandshakeOutStream hos) throws IOException {
            int len = messageLength();
            if (len >= Record.OVERFLOW_OF_INT24) {
                throw new SSLException("Handshake message is overflow"
                        + ", type = " + handshakeType() + ", len = " + len);
            }
            hos.write(handshakeType().id);
            hos.putInt24(len);
            send(hos);
            hos.complete();
        }
    }
}
