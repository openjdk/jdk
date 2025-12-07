/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8206929 8212885 8333857
 * @summary ensure that client only resumes a session if certain properties
 *    of the session are compatible with the new connection
 * @library /javax/net/ssl/templates
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksClient BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksClient BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.3 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.3 -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient BASIC
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient BASIC
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient VERSION_2_TO_3
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient VERSION_3_TO_2
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.3 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient CIPHER_SUITE
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.3 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksClient SIGNATURE_SCHEME
 *
 */

import javax.net.*;
import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResumeChecksClient extends SSLContextTemplate {
    enum TestMode {
        BASIC,
        VERSION_2_TO_3,
        VERSION_3_TO_2,
        CIPHER_SUITE,
        SIGNATURE_SCHEME
    }
    static TestMode testMode;

    public static void main(String[] args) throws Exception {
        testMode = TestMode.valueOf(args[0]);
        new ResumeChecksClient().test();
    }

    private void test() throws Exception {
        Server server = new Server();
        SSLContext sslContext = createClientSSLContext();
        HexFormat hex = HexFormat.of();
        long firstStartTime = System.currentTimeMillis();
        SSLSession firstSession = connect(sslContext, server.port, true);
        System.err.println("firstStartTime = " + firstStartTime);
        System.err.println("firstId = " + hex.formatHex(firstSession.getId()));
        System.err.println("firstSession.getCreationTime() = " +
            firstSession.getCreationTime());

        long secondStartTime = System.currentTimeMillis();
        SSLSession secondSession = connect(sslContext, server.port, false);
        System.err.println("secondStartTime = " + secondStartTime);
        // Note: Ids will never match with TLS 1.3 due to spec
        System.err.println("secondId = " + hex.formatHex(secondSession.getId()));
        System.err.println("secondSession.getCreationTime() = " +
            secondSession.getCreationTime());

        switch (testMode) {
        case BASIC:
            // fail if session is not resumed
            try {
                checkResumedSession(firstSession, secondSession);
            } catch (Exception e) {
                throw new AssertionError("secondSession did not resume: FAIL",
                    e);
            }
            System.out.println("secondSession used resumption: PASS");
            break;
        case VERSION_2_TO_3:
        case VERSION_3_TO_2:
        case CIPHER_SUITE:
        case SIGNATURE_SCHEME:
            // fail if a new session is not created
            try {
                checkResumedSession(firstSession, secondSession);
                System.err.println("firstSession  = " + firstSession);
                System.err.println("secondSession = " + secondSession);
                throw new AssertionError("Second connection should not " +
                    "have resumed first session:  FAIL");
            } catch (Exception e) {
                System.out.println("secondSession didn't use resumption: PASS");
            }
            break;
        default:
            throw new AssertionError("unknown mode: " + testMode);
        }
    }

    private static class NoSig implements AlgorithmConstraints {

        private final String alg;

        NoSig(String alg) {
            this.alg = alg;
        }


        private boolean test(String a) {
            return !a.toLowerCase().contains(alg.toLowerCase());
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
            return true;
        }
        @Override
        public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, AlgorithmParameters parameters) {

            return test(algorithm);
        }
        @Override
        public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {

            return test(algorithm);
        }
    }

    private static SSLSession connect(SSLContext sslContext, int port,
        boolean first) {

        try {
            SSLSocket sock = (SSLSocket)
                sslContext.getSocketFactory().createSocket();
            SSLParameters params = sock.getSSLParameters();

            switch (testMode) {
                case BASIC -> {}  // do nothing
                case VERSION_2_TO_3 -> params.setProtocols(new String[]{
                    first ? "TLSv1.2" : "TLSv1.3"});
                case VERSION_3_TO_2 -> params.setProtocols(new String[]{
                    first ? "TLSv1.3" : "TLSv1.2"});
                case CIPHER_SUITE -> params.setCipherSuites(
                    new String[]{
                        first ? "TLS_AES_128_GCM_SHA256" :
                            "TLS_AES_256_GCM_SHA384"});
                case SIGNATURE_SCHEME ->
                    params.setAlgorithmConstraints(new NoSig(
                        first ? "rsa" : "ecdsa"));
                default ->
                    throw new AssertionError("unknown mode: " +
                        testMode);
            }
            sock.setSSLParameters(params);
            sock.connect(new InetSocketAddress("localhost", port));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(sock.getOutputStream()));
            out.println("message");
            out.flush();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream()));
            String inMsg = reader.readLine();
            System.out.println("Client received: " + inMsg);
            SSLSession result = sock.getSession();
            sock.close();
            return result;
        } catch (Exception ex) {
            // unexpected exception
            throw new AssertionError(ex);
        }
    }

    private static void checkResumedSession(SSLSession initSession,
            SSLSession resSession) throws Exception {
        StringBuilder diffLog = new StringBuilder();

        // Initial and resumed SSLSessions should have the same creation
        // times so they get invalidated together.
        long initCt = initSession.getCreationTime();
        long resumeCt = resSession.getCreationTime();
        if (initCt != resumeCt) {
            diffLog.append("Session creation time is different. Initial: ").
                    append(initCt).append(", Resumed: ").append(resumeCt).
                    append("\n");
        }

        // Ensure that peer and local certificate lists are preserved
        if (!Arrays.equals(initSession.getLocalCertificates(),
                resSession.getLocalCertificates())) {
            diffLog.append("Local certificate mismatch between initial " +
                    "and resumed sessions\n");
        }

        if (!Arrays.equals(initSession.getPeerCertificates(),
                resSession.getPeerCertificates())) {
            diffLog.append("Peer certificate mismatch between initial " +
                    "and resumed sessions\n");
        }

        // Buffer sizes should also be the same
        if (initSession.getApplicationBufferSize() !=
                resSession.getApplicationBufferSize()) {
            diffLog.append(String.format(
                    "App Buffer sizes differ: Init: %d, Res: %d\n",
                    initSession.getApplicationBufferSize(),
                    resSession.getApplicationBufferSize()));
        }

        if (initSession.getPacketBufferSize() !=
                resSession.getPacketBufferSize()) {
            diffLog.append(String.format(
                    "Packet Buffer sizes differ: Init: %d, Res: %d\n",
                    initSession.getPacketBufferSize(),
                    resSession.getPacketBufferSize()));
        }

        // Cipher suite should match
        if (!initSession.getCipherSuite().equals(
                resSession.getCipherSuite())) {
            diffLog.append(String.format(
                    "CipherSuite does not match - Init: %s, Res: %s\n",
                    initSession.getCipherSuite(), resSession.getCipherSuite()));
        }

        // Peer host/port should match
        if (!initSession.getPeerHost().equals(resSession.getPeerHost()) ||
                initSession.getPeerPort() != resSession.getPeerPort()) {
            diffLog.append(String.format(
                    "Host/Port mismatch - Init: %s/%d, Res: %s/%d\n",
                    initSession.getPeerHost(), initSession.getPeerPort(),
                    resSession.getPeerHost(), resSession.getPeerPort()));
        }

        // Check protocol
        if (!initSession.getProtocol().equals(resSession.getProtocol())) {
            diffLog.append(String.format(
                    "Protocol mismatch - Init: %s, Res: %s\n",
                    initSession.getProtocol(), resSession.getProtocol()));
        }

        // If the StringBuilder has any data in it then one of the checks
        // above failed and we should throw an exception.
        if (diffLog.length() > 0) {
            throw new RuntimeException(diffLog.toString());
        }
    }

    private static class Server extends SSLContextTemplate {
        public int port;
        private final SSLServerSocket ssock;
        ExecutorService threadPool = Executors.newFixedThreadPool(1);
        CountDownLatch serverLatch = new CountDownLatch(1);

        Server() {
            try {
                SSLContext sc = createServerSSLContext();
                ServerSocketFactory fac = sc.getServerSocketFactory();
                ssock = (SSLServerSocket) fac.createServerSocket(0);
                port = ssock.getLocalPort();

                // Thread to allow multiple clients to connect
                new Thread(() -> {
                    try {
                        System.err.println("Server starting to accept");
                        serverLatch.countDown();
                        do {
                            threadPool.submit(
                                new ServerThread((SSLSocket) ssock.accept()));
                        } while (true);
                    } catch (Exception ex) {
                        throw new AssertionError("Server Down", ex);
                    } finally {
                        threadPool.close();
                    }
                }).start();

            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        static class ServerThread extends Thread {
            SSLSocket sock;

            ServerThread(SSLSocket s) {
                this.sock = s;
                System.err.println("(Server) client connection on port " +
                    sock.getPort());
            }

            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(sock.getInputStream()));
                    String line = reader.readLine();
                    System.out.println("server read: " + line);
                    PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(sock.getOutputStream()));
                    out.println(line);
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    throw new AssertionError("Server thread error", e);
                }
            }
        }
    }
}
