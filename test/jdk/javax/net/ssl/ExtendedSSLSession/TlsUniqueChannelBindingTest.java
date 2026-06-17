/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.security.Security;
import java.util.Arrays;
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

/*
 * @test
 * @bug <TBD>
 * @summary Verify tls-unique channel binding (RFC 5929) via ExtendedSSLSession
 * @library /javax/net/ssl/templates /test/lib
 * @build SSLEngineTemplate
 * @run main/othervm TlsUniqueChannelBindingTest
 */

public class TlsUniqueChannelBindingTest extends SSLEngineTemplate {

    private final String protocol;
    private final String ciphersuite;

    TlsUniqueChannelBindingTest(String protocol, String ciphersuite)
            throws Exception {
        super();
        this.protocol = protocol;
        this.ciphersuite = ciphersuite;
    }

    private void configureEngines(SSLEngine clientEngine,
            SSLEngine serverEngine) {
        clientEngine.setUseClientMode(true);
        SSLParameters clientParams = clientEngine.getSSLParameters();
        clientParams.setProtocols(new String[] { protocol });
        clientParams.setCipherSuites(new String[] { ciphersuite });
        clientEngine.setSSLParameters(clientParams);

        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);
        SSLParameters serverParams = serverEngine.getSSLParameters();
        serverParams.setProtocols(new String[]{
                "TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1"});
        serverEngine.setSSLParameters(serverParams);
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        // TLS 1.2 full handshake should produce non-null channel binding
        new TlsUniqueChannelBindingTest(
                "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
                .runTest(true);

        new TlsUniqueChannelBindingTest(
                "TLSv1.2", "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")
                .runTest(true);

        // TLS 1.3 should return null (use tls-exporter instead)
        new TlsUniqueChannelBindingTest(
                "TLSv1.3", "TLS_AES_128_GCM_SHA256")
                .runTest(false);

        System.out.println("All tests PASSED");
    }

    private void runTest(boolean expectNonNull) throws Exception {
        configureEngines(clientEngine, serverEngine);

        boolean dataDone = false;
        while (isOpen(clientEngine) || isOpen(serverEngine)) {
            clientEngine.wrap(clientOut, cTOs);
            runDelegatedTasks(clientEngine);

            serverEngine.wrap(serverOut, sTOc);
            runDelegatedTasks(serverEngine);

            cTOs.flip();
            sTOc.flip();

            clientEngine.unwrap(sTOc, clientIn);
            runDelegatedTasks(clientEngine);

            serverEngine.unwrap(cTOs, serverIn);
            runDelegatedTasks(serverEngine);

            cTOs.compact();
            sTOc.compact();

            if (!dataDone && (clientOut.limit() == serverIn.position()) &&
                    (serverOut.limit() == clientIn.position())) {

                ExtendedSSLSession clientSession =
                        (ExtendedSSLSession) clientEngine.getSession();
                ExtendedSSLSession serverSession =
                        (ExtendedSSLSession) serverEngine.getSession();

                verifyChannelBinding(clientSession, serverSession,
                        expectNonNull);

                clientEngine.closeOutbound();
                serverEngine.closeOutbound();
                dataDone = true;
            }
        }
    }

    private static void verifyChannelBinding(
            ExtendedSSLSession clientSession,
            ExtendedSSLSession serverSession,
            boolean expectNonNull) throws Exception {

        byte[] clientBinding = clientSession.getTlsUniqueChannelBinding();
        byte[] serverBinding = serverSession.getTlsUniqueChannelBinding();

        if (!expectNonNull) {
            if (clientBinding != null || serverBinding != null) {
                throw new Exception(
                        "Expected null channel binding for TLS 1.3");
            }
            System.out.println("TLS 1.3: null binding as expected");
            return;
        }

        if (clientBinding == null) {
            throw new Exception("Client channel binding is null");
        }
        if (serverBinding == null) {
            throw new Exception("Server channel binding is null");
        }
        if (clientBinding.length != 12) {
            throw new Exception(
                    "Expected 12 bytes, got " + clientBinding.length);
        }
        if (!Arrays.equals(clientBinding, serverBinding)) {
            throw new Exception(
                    "Client and server channel bindings do not match");
        }

        // Verify defensive copy
        byte[] second = clientSession.getTlsUniqueChannelBinding();
        if (clientBinding == second) {
            throw new Exception("Same array instance returned (no clone)");
        }
        if (!Arrays.equals(clientBinding, second)) {
            throw new Exception("Subsequent calls return different values");
        }

        System.out.println("PASSED: " + clientSession.getProtocol() +
                " binding=" + clientBinding.length + " bytes, both sides match");
    }
}
