/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (C) 2021, 2024 THL A29 Limited, a Tencent company. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test 8263188
 * @summary If TLS the server and client has no common signature algorithms,
 *     the connection should fail fast with "No supported signature algorithm".
 *     This test only covers TLS 1.2.
 *
 * @library /test/lib
 *          /javax/net/ssl/templates
 *
 * @run main/othervm
 *     -Djdk.tls.server.SignatureSchemes=ecdsa_secp384r1_sha384
 *     -Djdk.tls.client.SignatureSchemes=ecdsa_secp256r1_sha256,ecdsa_secp384r1_sha384
 *     -Dtest.clientAuth=false
 *     -Dtest.expectFail=false
 *     SigAlgosExtTestWithTLS12
 * @run main/othervm
 *     -Djdk.tls.server.SignatureSchemes=ecdsa_secp384r1_sha384
 *     -Djdk.tls.client.SignatureSchemes=ecdsa_secp256r1_sha256
 *     -Dtest.clientAuth=false
 *     -Dtest.expectFail=true
 *     SigAlgosExtTestWithTLS12
 * @run main/othervm
 *     -Djdk.tls.server.SignatureSchemes=ecdsa_secp256r1_sha256
 *     -Djdk.tls.client.SignatureSchemes=ecdsa_secp256r1_sha256
 *     -Dtest.clientAuth=true
 *     -Dtest.expectFail=true
 *     SigAlgosExtTestWithTLS12
 */

import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.util.*;

public class SigAlgosExtTestWithTLS12 extends SSLEngineTemplate {

    private static final boolean CLIENT_AUTH
            = Boolean.getBoolean("test.clientAuth");
    private static final boolean EXPECT_FAIL
            = Boolean.getBoolean("test.expectFail");

    private static final int TLS_HS_CERT_REQ = 13;

    public SigAlgosExtTestWithTLS12() throws Exception {
        super();
    }

    /*
     * Create an instance of KeyManager for client use.
     */
    @Override
    protected KeyManager createClientKeyManager() throws Exception {
        return createKeyManager(
                new Cert[]{Cert.EE_ECDSA_SECP256R1, Cert.EE_ECDSA_SECP384R1},
                getClientContextParameters());
    }

    @Override
    public TrustManager createClientTrustManager() throws Exception {
        return createTrustManager(
                new Cert[]{Cert.CA_ECDSA_SECP256R1, Cert.CA_ECDSA_SECP384R1},
                getServerContextParameters());
    }

    @Override
    public KeyManager createServerKeyManager() throws Exception {
        return createKeyManager(
                new Cert[]{Cert.EE_ECDSA_SECP256R1, Cert.EE_ECDSA_SECP384R1},
                getServerContextParameters());
    }

    @Override
    public TrustManager createServerTrustManager() throws Exception {
        return createTrustManager(
                new Cert[]{Cert.CA_ECDSA_SECP256R1, Cert.CA_ECDSA_SECP384R1},
                getServerContextParameters());
    }

    @Override
    protected SSLEngine configureServerEngine(SSLEngine serverEngine) {
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(CLIENT_AUTH);
        return serverEngine;
    }

    @Override
    protected SSLEngine configureClientEngine(SSLEngine clientEngine) {
        clientEngine.setUseClientMode(true);
        clientEngine.setEnabledProtocols(new String[] { "TLSv1.2" });
        return clientEngine;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.debug", "ssl:handshake");

        try {
            new SigAlgosExtTestWithTLS12().run();
            if (EXPECT_FAIL) {
                throw new RuntimeException(
                        "Expected SSLHandshakeException wasn't thrown");
            }
        } catch (SSLHandshakeException e) {
            if (EXPECT_FAIL && e.getMessage().endsWith(
                    "No supported signature algorithm")) {
                System.out.println("Expected SSLHandshakeException");
            } else {
                throw e;
            }
        }
    }

    private void run() throws Exception {
        boolean dataDone = false;
        while (isOpen(clientEngine) || isOpen(serverEngine)) {
            clientEngine.wrap(clientOut, cTOs);
            cTOs.flip();

            // Consume the ClientHello and get the server flight of handshake
            // messages.  We expect that it will be one TLS record containing
            // multiple handshake messages, one of which is a CertificateRequest
            // when the client authentication is required.
            serverEngine.unwrap(cTOs, serverIn);
            runDelegatedTasks(serverEngine);

            // Wrap the server flight
            serverEngine.wrap(serverOut, sTOc);
            sTOc.flip();

            if (CLIENT_AUTH && EXPECT_FAIL) {
                twistCertReqMsg(sTOc);
            }

            clientEngine.unwrap(sTOc, clientIn);
            runDelegatedTasks(clientEngine);

            serverEngine.unwrap(cTOs, serverIn);
            runDelegatedTasks(serverEngine);

            cTOs.compact();
            sTOc.compact();

            if (!dataDone && (clientOut.limit() == serverIn.position()) &&
                    (serverOut.limit() == clientIn.position())) {
                checkTransfer(serverOut, clientIn);
                checkTransfer(clientOut, serverIn);

                clientEngine.closeOutbound();
                dataDone = true;
                serverEngine.closeOutbound();
            }
        }
    }

    /**
     * Twists signature schemes in CertificateRequest message for negative
     * client authentication cases.
     *
     * @param tlsRecord a ByteBuffer containing a TLS record.  It is assumed
     *      that the position of the ByteBuffer is on the first byte of the TLS
     *      record header.
     *
     * @throws SSLException if the incoming ByteBuffer does not contain a
     *      well-formed TLS message.
     */
    private static void twistCertReqMsg(
            ByteBuffer tlsRecord) throws SSLException {
        Objects.requireNonNull(tlsRecord);
        tlsRecord.mark();

        // Process the TLS record header
        int type = Byte.toUnsignedInt(tlsRecord.get());
        int ver_major = Byte.toUnsignedInt(tlsRecord.get());
        int ver_minor = Byte.toUnsignedInt(tlsRecord.get());
        int recLen = Short.toUnsignedInt(tlsRecord.getShort());

        // Simple sanity checks
        if (type != 22) {
            throw new SSLException("Not a handshake: Type = " + type);
        } else if (recLen > tlsRecord.remaining()) {
            throw new SSLException("Incomplete record in buffer: " +
                    "Record length = " + recLen + ", Remaining = " +
                    tlsRecord.remaining());
        }

        while (tlsRecord.hasRemaining()) {
            // Grab the handshake message header.
            int msgHdr = tlsRecord.getInt();
            int msgType = (msgHdr >> 24) & 0x000000FF;
            int msgLen = msgHdr & 0x00FFFFFF;

            if (msgType == TLS_HS_CERT_REQ) {
                // Slice the buffer such that it contains the entire
                // handshake message (less the handshake header).
                int bufPos = tlsRecord.position();
                ByteBuffer buf = tlsRecord.slice(bufPos, msgLen);

                // Replace the signature scheme with an unknown value
                twistSigSchemesCertReq(buf, (short) 0x0000);
                byte[] bufBytes = new byte[buf.limit()];
                buf.get(bufBytes);
                tlsRecord.put(bufPos, bufBytes);

                break;
            } else {
                // Skip to the next handshake message, if there is one
                tlsRecord.position(tlsRecord.position() + msgLen);
            }
        }

        tlsRecord.reset();
    }

    /**
     * Replace the signature schemes in CertificateRequest message with an
     * alternative value.  It is assumed that the provided ByteBuffer has its
     * position set at the first byte of the CertificateRequest message body
     * (AFTER the handshake header) and contains the entire CR message.  Upon
     * successful completion of this method the ByteBuffer will have its
     * position reset to the initial offset in the buffer.
     * If an exception is thrown the position at the time of the exception
     * will be preserved.
     *
     * @param data the ByteBuffer containing the CertificateRequest bytes
     * @param altSigScheme an alternative signature scheme
     */
    private static void twistSigSchemesCertReq(ByteBuffer data,
                                               Short altSigScheme) {
        Objects.requireNonNull(data);
        data.mark();

        // Jump past the certificate types
        int certTypeLen = Byte.toUnsignedInt(data.get());
        if (certTypeLen != 0) {
            data.position(data.position() + certTypeLen);
        }

        int sigSchemeLen = Short.toUnsignedInt(data.getShort());
        for (int ssOff = 0; ssOff < sigSchemeLen; ssOff += 2) {
            System.err.println(
                    "Use alternative signature scheme: " + altSigScheme);
            data.putShort(data.position(), altSigScheme);
        }

        data.reset();
    }
}
