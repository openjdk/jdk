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

import java.security.Security;
import java.util.Arrays;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

/*
 * @test
 * @bug <TBD>
 * @summary Verify tls-unique channel binding (RFC 5929) via ExtendedSSLSession
 * @library /javax/net/ssl/templates /test/lib
 * @build SSLEngineTemplate
 * @run main/othervm -Dsun.security.ssl.enableTlsUniqueChannelBinding=true TlsUniqueChannelBindingTest TLS12_ENABLED
 * @run main/othervm TlsUniqueChannelBindingTest DISABLED
 * @run main/othervm -Dsun.security.ssl.enableTlsUniqueChannelBinding=true TlsUniqueChannelBindingTest TLS13_NULL
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
        String testCase = args.length > 0 ? args[0] : "TLS12_ENABLED";
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        switch (testCase) {
            case "TLS12_ENABLED":
                testTls12Enabled();
                break;
            case "DISABLED":
                testDisabled();
                break;
            case "TLS13_NULL":
                testTls13Null();
                break;
            default:
                throw new RuntimeException("Unknown test case: " + testCase);
        }
    }

    private static void testTls12Enabled() throws Exception {
        new TlsUniqueChannelBindingTest(
                "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
                .runTest(true);
    }

    private static void testDisabled() throws Exception {
        new TlsUniqueChannelBindingTest(
                "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
                .runTest(false);
    }

    private static void testTls13Null() throws Exception {
        new TlsUniqueChannelBindingTest(
                "TLSv1.3", "TLS_AES_128_GCM_SHA256")
                .runTest(false);
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

            if (!dataDone
                    && (clientOut.limit() == serverIn.position())
                    && (serverOut.limit() == clientIn.position())) {

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

        byte[] clientClientFirst =
                clientSession.getTlsUniqueClientFirstFinishedVerifyData();
        byte[] serverClientFirst =
                serverSession.getTlsUniqueClientFirstFinishedVerifyData();
        byte[] clientFirstFinished =
                clientSession.getTlsUniqueFirstFinishedVerifyData();
        byte[] serverFirstFinished =
                serverSession.getTlsUniqueFirstFinishedVerifyData();

        if (!expectNonNull) {
            assertAllNull(clientClientFirst, serverClientFirst,
                    clientFirstFinished, serverFirstFinished);
            return;
        }

        assertPair("Client-first", clientClientFirst, serverClientFirst);
        assertPair("First-finished", clientFirstFinished, serverFirstFinished);

        byte[] second = clientSession.getTlsUniqueFirstFinishedVerifyData();
        if (clientFirstFinished == second) {
            throw new Exception("Same array instance returned (no clone)");
        }
        if (!Arrays.equals(clientFirstFinished, second)) {
            throw new Exception("Subsequent calls return different values");
        }
    }

    private static void assertAllNull(byte[]... values) throws Exception {
        for (byte[] value : values) {
            if (value != null) {
                throw new Exception("Expected null channel binding");
            }
        }
    }

    private static void assertPair(String label, byte[] clientValue,
            byte[] serverValue) throws Exception {
        if (clientValue == null || serverValue == null) {
            throw new Exception(label + " verify_data is null");
        }
        if (clientValue.length != 12 || serverValue.length != 12) {
            throw new Exception(label + " verify_data length is not 12");
        }
        if (!Arrays.equals(clientValue, serverValue)) {
            throw new Exception(label + " verify_data does not match");
        }
    }
}
