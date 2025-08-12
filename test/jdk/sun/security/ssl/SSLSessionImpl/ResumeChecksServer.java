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
 * @bug 8206929 8333857
 * @summary ensure that server only resumes a session if certain properties
 *    of the session are compatible with the new connection
 * @modules java.base/sun.security.x509
 * @library /javax/net/ssl/templates
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksServer BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksServer BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksServer BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksServer CLIENT_AUTH
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksServer CLIENT_AUTH
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksServer CLIENT_AUTH
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksServer VERSION_2_TO_3
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksServer VERSION_2_TO_3
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksServer VERSION_2_TO_3
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksServer VERSION_3_TO_2
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=true ResumeChecksServer VERSION_3_TO_2
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=false ResumeChecksServer VERSION_3_TO_2
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

import sun.security.x509.X509CertImpl;

public class ResumeChecksServer extends SSLContextTemplate {

    enum TestMode {
        BASIC,
        CLIENT_AUTH,
        VERSION_2_TO_3,
        VERSION_3_TO_2,
        CIPHER_SUITE,
        SIGNATURE_SCHEME,
        LOCAL_CERTS
    }

    static CountDownLatch latch = new CountDownLatch(1);
    static TestMode testMode;
    static int serverPort;

    public static void main(String[] args) throws Exception {
        testMode = TestMode.valueOf(args[0]);
        new ResumeChecksServer().test();
    }

    private void test() throws Exception {
        SSLSession firstSession, secondSession;
        HexFormat hex = HexFormat.of();

        serverPort = new Server().port;
        latch.await();
        Client c = new Client(serverPort);

        System.out.println("Waiting for connection");
        long firstStartTime = System.currentTimeMillis();
        firstSession = c.test();

        System.err.println("firstStartTime = " + firstStartTime);
        System.err.println("firstId = " + hex.formatHex(firstSession.getId()));
        System.err.println("firstSession.getCreationTime() = " +
            firstSession.getCreationTime());

        long secondStartTime = System.currentTimeMillis();
        secondSession = c.test();

        System.err.println("secondStartTime = " + secondStartTime);
        // Note: Ids will never match with TLS 1.3 due to spec
        System.err.println("secondId = " + hex.formatHex(secondSession.getId()));
        System.err.println("secondSession.getCreationTime() = " +
            secondSession.getCreationTime());

        switch (testMode) {
        case BASIC:
            // fail if session is not resumed
            if (firstSession.getCreationTime() !=
                secondSession.getCreationTime()) {
                throw new AssertionError("Session was not reused: FAIL");
            }

            // Fail if session's certificates are not restored correctly.
            if (!Arrays.equals(
                    firstSession.getLocalCertificates(),
                    secondSession.getLocalCertificates())) {
                throw new AssertionError("Certificates do not match: FAIL");
            }
            System.out.println("secondSession used resumption: PASS");
            break;
        case CLIENT_AUTH:
            // throws an exception if the client is not authenticated
            secondSession.getPeerCertificates();
            break;
        case VERSION_2_TO_3:
        case VERSION_3_TO_2:
        case CIPHER_SUITE:
        case SIGNATURE_SCHEME:
        case LOCAL_CERTS:
            // fail if a new session is not created
            if (secondSession.getCreationTime() < secondStartTime) {
                throw new AssertionError("Existing session was used: FAIL");
            }
            System.out.println("secondSession not resumed: PASS");
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

        public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
            return true;
        }

        public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, AlgorithmParameters parameters) {
            return test(algorithm);
        }

        public boolean permits(Set<CryptoPrimitive> primitives,
            String algorithm, Key key, AlgorithmParameters parameters) {
            return test(algorithm);
        }
    }

    private static class Client extends SSLContextTemplate {
        private final int port;
        private final SSLContext sc;
        public SSLSession session;

        Client(int port) throws Exception {
            sc = createClientSSLContext();
            this.port = port;
        }

        public SSLSession test() throws Exception {
            SSLSocket sock = null;
            latch.await();
            do {
                try {
                    sock = (SSLSocket) sc.getSocketFactory().createSocket();
                } catch (IOException e) {
                    // If the server never starts, test will time out.
                    System.err.println("client trying again to connect");
                    Thread.sleep(500);
                }
            } while (sock == null);
            sock.connect(new InetSocketAddress("localhost", port));
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(sock.getOutputStream()));
            out.println("message");
            out.flush();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream()));
            String inMsg = reader.readLine();
            System.out.println("Client received: " + inMsg);
            out.close();
            session = sock.getSession();
            sock.close();
            return session;
        }
    }

    // The server will only have two connections each tests
    private static class Server extends SSLContextTemplate {
        public int port;
        ExecutorService threadPool = Executors.newFixedThreadPool(1);
        // Stores the certs from the first connection in mode LOCAL_CERTS
        static X509CertImpl localCerts;
        // first connection to the server
        static boolean first = true;

        Server() throws Exception {
            SSLContext sc = createServerSSLContext();
            ServerSocketFactory fac = sc.getServerSocketFactory();
            SSLServerSocket ssock = (SSLServerSocket) fac.createServerSocket(0);
            port = ssock.getLocalPort();

            // Thread to allow multiple clients to connect
            new Thread(() -> {
                try {
                    System.err.println("Server starting to accept");
                    latch.countDown();
                    do {
                        threadPool.submit(new ServerThread(ssock.accept()));
                    } while (true);
                } catch (Exception ex) {
                    throw new AssertionError("Server Down", ex);
                } finally {
                    threadPool.close();
                }
            }).start();
        }

        static class ServerThread implements Runnable {
            final SSLSocket sock;

            ServerThread(Socket s) {
                this.sock = (SSLSocket) s;
                System.err.println("(Server) client connection on port " +
                    sock.getPort());
            }

            public void run() {
                try {
                    SSLParameters params = sock.getSSLParameters();
                    switch (testMode) {
                        case BASIC -> {}  // do nothing
                        case CLIENT_AUTH -> params.setNeedClientAuth(!first);
                        case VERSION_2_TO_3 -> params.setProtocols(new String[]{
                            first ? "TLSv1.2" : "TLSv1.3"});
                        case VERSION_3_TO_2 -> params.setProtocols(new String[]{
                            first ? "TLSv1.3" : "TLSv1.2"});
                        case CIPHER_SUITE -> params.setCipherSuites(
                            new String[]{
                                first ? "TLS_AES_256_GCM_SHA384" :
                                    "TLS_AES_128_GCM_SHA256"});
                        case SIGNATURE_SCHEME -> {
                            params.setNeedClientAuth(true);
                            params.setAlgorithmConstraints(new NoSig(
                                first ? "ecdsa_secp521r1_sha512" :
                                    "ecdsa_secp384r1_sha384"));
                        }
                        case LOCAL_CERTS -> {
                            if (!first) {
                                // Add first session's certificate signature
                                // algorithm to constraints so local certificates
                                // can't be restored from the session ticket.
                                params.setAlgorithmConstraints(
                                    new NoSig(X509CertImpl.toImpl(localCerts)
                                        .getSigAlgName()));
                            }
                        }
                        default ->
                            throw new AssertionError("Server: " +
                                "unknown mode: " + testMode);
                    }
                    sock.setSSLParameters(params);
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(sock.getInputStream()));
                    String line = reader.readLine();
                    System.err.println("server read: " + line);
                    PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(sock.getOutputStream()));
                    out.println(line);
                    out.flush();
                    out.close();
                    SSLSession session = sock.getSession();
                    if (testMode == TestMode.LOCAL_CERTS && first) {
                        localCerts = (X509CertImpl) session.
                            getLocalCertificates()[0];
                    }
                    first = false;
                    System.err.println("server socket closed: " + session);
                } catch (Exception e) {
                    throw new AssertionError("Server error", e);
                }
            }
        }
    }
}