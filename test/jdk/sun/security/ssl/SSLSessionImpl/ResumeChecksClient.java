/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary ensure that client only resumes a session if certain properties
 *    of the session are compatible with the new connection
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.2 ResumeChecksClient BASIC
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.3 ResumeChecksClient BASIC
 * @run main/othervm ResumeChecksClient BASIC
 * @run main/othervm ResumeChecksClient VERSION_2_TO_3
 * @run main/othervm ResumeChecksClient VERSION_3_TO_2
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.3 ResumeChecksClient CIPHER_SUITE
 * @run main/othervm -Djdk.tls.client.protocols=TLSv1.3 ResumeChecksClient SIGNATURE_SCHEME
 *
 */

import javax.net.*;
import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.net.*;
import java.util.*;

public class ResumeChecksClient {

    static String pathToStores = "../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    enum TestMode {
        BASIC,
        VERSION_2_TO_3,
        VERSION_3_TO_2,
        CIPHER_SUITE,
        SIGNATURE_SCHEME
    }

    public static void main(String[] args) throws Exception {

        TestMode mode = TestMode.valueOf(args[0]);

        String keyFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + keyStoreFile;
        String trustFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + trustStoreFile;

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);

        Server server = startServer();
        server.signal();
        SSLContext sslContext = SSLContext.getDefault();
        while (!server.started) {
            Thread.yield();
        }
        connect(sslContext, server.port, mode, false);

        server.signal();
        long secondStartTime = System.currentTimeMillis();
        Thread.sleep(10);
        SSLSession secondSession = connect(sslContext, server.port, mode, true);

        server.go = false;
        server.signal();

        switch (mode) {
        case BASIC:
            // fail if session is not resumed
            if (secondSession.getCreationTime() > secondStartTime) {
                throw new RuntimeException("Session was not reused");
            }
            break;
        case VERSION_2_TO_3:
        case VERSION_3_TO_2:
        case CIPHER_SUITE:
        case SIGNATURE_SCHEME:
            // fail if a new session is not created
            if (secondSession.getCreationTime() <= secondStartTime) {
                throw new RuntimeException("Existing session was used");
            }
            break;
        default:
            throw new RuntimeException("unknown mode: " + mode);
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

    private static SSLSession connect(SSLContext sslContext, int port,
        TestMode mode, boolean second) {

        try {
            SSLSocket sock = (SSLSocket)
                sslContext.getSocketFactory().createSocket();
            SSLParameters params = sock.getSSLParameters();

            switch (mode) {
            case BASIC:
                // do nothing to ensure resumption works
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
                        new String[] {"TLS_AES_256_GCM_SHA384"});
                } else {
                    params.setCipherSuites(
                        new String[] {"TLS_AES_128_GCM_SHA256"});
                }
                break;
            case SIGNATURE_SCHEME:
                AlgorithmConstraints constraints =
                    params.getAlgorithmConstraints();
                if (second) {
                    params.setAlgorithmConstraints(new NoSig("ecdsa"));
                } else {
                    params.setAlgorithmConstraints(new NoSig("rsa"));
                }
                break;
            default:
                throw new RuntimeException("unknown mode: " + mode);
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
            throw new RuntimeException(ex);
        }
    }

    private static Server startServer() {
        Server server = new Server();
        new Thread(server).start();
        return server;
    }

    private static class Server implements Runnable {

        public volatile boolean go = true;
        private boolean signal = false;
        public volatile int port = 0;
        public volatile boolean started = false;

        private synchronized void waitForSignal() {
            while (!signal) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    // do nothing
                }
            }
            signal = false;
        }
        public synchronized void signal() {
            signal = true;
            notify();
        }

        public void run() {
            try {

                SSLContext sc = SSLContext.getDefault();
                ServerSocketFactory fac = sc.getServerSocketFactory();
                SSLServerSocket ssock = (SSLServerSocket)
                    fac.createServerSocket(0);
                this.port = ssock.getLocalPort();

                waitForSignal();
                started = true;
                while (go) {
                    try {
                        System.out.println("Waiting for connection");
                        Socket sock = ssock.accept();
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(sock.getInputStream()));
                        String line = reader.readLine();
                        System.out.println("server read: " + line);
                        PrintWriter out = new PrintWriter(
                            new OutputStreamWriter(sock.getOutputStream()));
                        out.println(line);
                        out.flush();
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
