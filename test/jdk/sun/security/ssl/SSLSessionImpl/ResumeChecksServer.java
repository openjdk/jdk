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
 * @bug 8206929
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

    public static void main(String[] args) throws Exception {

        new ResumeChecksServer(TestMode.valueOf(args[0])).run();
    }
    private final TestMode testMode;

    public ResumeChecksServer(TestMode testMode) {
        this.testMode = testMode;
    }

    private void run() throws Exception {
        SSLSession firstSession;
        SSLSession secondSession = null;

        SSLContext sslContext = createServerSSLContext();
        ServerSocketFactory fac = sslContext.getServerSocketFactory();
        SSLServerSocket ssock = (SSLServerSocket)
            fac.createServerSocket(0);

        Client client = startClient(ssock.getLocalPort());

        try {
            firstSession = connect(client, ssock, testMode, null);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        long secondStartTime = System.currentTimeMillis();
        Thread.sleep(10);
        try {
            secondSession = connect(client, ssock, testMode, firstSession);
        } catch (SSLHandshakeException ex) {
            // this is expected
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        client.go = false;
        client.signal();

        switch (testMode) {
        case BASIC:
            // fail if session is not resumed
            if (secondSession.getCreationTime() > secondStartTime) {
                throw new RuntimeException("Session was not reused");
            }

            // Fail if session's certificates are not restored correctly.
            if (!java.util.Arrays.equals(
                    firstSession.getLocalCertificates(),
                    secondSession.getLocalCertificates())) {
                throw new RuntimeException("Certificates do not match");
            }

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
            if (secondSession.getCreationTime() <= secondStartTime) {
                throw new RuntimeException("Existing session was used");
            }
            break;
        default:
            throw new RuntimeException("unknown mode: " + testMode);
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

    private static SSLSession connect(Client client, SSLServerSocket ssock,
            TestMode mode, SSLSession firstSession) throws Exception {

        boolean second = firstSession != null;

        try {
            client.signal();
            System.out.println("Waiting for connection");
            SSLSocket sock = (SSLSocket) ssock.accept();
            SSLParameters params = sock.getSSLParameters();

            switch (mode) {
            case BASIC:
                // do nothing to ensure resumption works
                break;
            case CLIENT_AUTH:
                if (second) {
                    params.setNeedClientAuth(true);
                } else {
                    params.setNeedClientAuth(false);
                }
                break;
            case VERSION_2_TO_3:
                if (second) {
                    params.setProtocols(new String[] {"TLSv1.3"});
                } else {
                    params.setProtocols(new String[] {"TLSv1.2"});
                }
                break;
            case VERSION_3_TO_2:
                if (second) {
                    params.setProtocols(new String[] {"TLSv1.2"});
                } else {
                    params.setProtocols(new String[] {"TLSv1.3"});
                }
                break;
            case CIPHER_SUITE:
                if (second) {
                    params.setCipherSuites(
                        new String[] {"TLS_AES_128_GCM_SHA256"});
                } else {
                    params.setCipherSuites(
                        new String[] {"TLS_AES_256_GCM_SHA384"});
                }
                break;
            case SIGNATURE_SCHEME:
                params.setNeedClientAuth(true);
                AlgorithmConstraints constraints =
                    params.getAlgorithmConstraints();
                if (second) {
                    params.setAlgorithmConstraints(
                            new NoSig("ecdsa_secp384r1_sha384"));
                } else {
                    params.setAlgorithmConstraints(
                            new NoSig("ecdsa_secp521r1_sha512"));
                }
                break;
            case LOCAL_CERTS:
                if (second) {
                    // Add first session's certificate signature
                    // algorithm to constraints so local certificates
                    // can't be restored from the session ticket.
                    params.setAlgorithmConstraints(
                            new NoSig(X509CertImpl.toImpl((X509CertImpl)
                                            firstSession.getLocalCertificates()[0])
                                    .getSigAlgName()));
                }
                break;
            default:
                throw new RuntimeException("unknown mode: " + mode);
            }
            sock.setSSLParameters(params);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(sock.getInputStream()));
            String line = reader.readLine();
            System.out.println("server read: " + line);
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(sock.getOutputStream()));
            out.println(line);
            out.flush();
            out.close();
            SSLSession result = sock.getSession();
            sock.close();
            return result;
        } catch (SSLHandshakeException ex) {
            if (!second) {
                throw ex;
            }
        }
        return null;
    }

    private static Client startClient(int port) {
        Client client = new Client(port);
        new Thread(client).start();
        return client;
    }

    private static class Client extends SSLContextTemplate implements Runnable {

        public volatile boolean go = true;
        private boolean signal = false;
        private final int port;

        Client(int port) {
            this.port = port;
        }

        private synchronized void waitForSignal() {
            while (!signal) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    // do nothing
                }
            }
            signal = false;

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                // do nothing
            }
        }
        public synchronized void signal() {
            signal = true;
            notify();
        }

        public void run() {
            try {

                SSLContext sc = createClientSSLContext();

                waitForSignal();
                while (go) {
                    try {
                        SSLSocket sock = (SSLSocket)
                            sc.getSocketFactory().createSocket();
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
                        sock.close();
                        waitForSignal();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
