/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * This is a base setup for creating a server and clients.  All clients will
 * connect to the server on construction.  The server constructor must be run
 * first.  The idea is for the test code to be minimal as possible without
 * this library class being complicated.
 *
 * Server.close() must be called so the server will exit and end threading.
 *
 * After construction, reading and writing are allowed from either side,
 * or a combination write/read from both sides for verifying text.
 *
 * The TLSBase.Server and TLSBase.Client classes are to allow full access to
 * the SSLSession for verifying data.
 *
 * See SSLSession/CheckSessionContext.java for an example
 *
 */

abstract public class TLSBase {
    static String pathToStores = "javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    static final String TESTROOT =
        System.getProperty("test.root", "../../../..");

    SSLContext sslContext;
    // Server's port
    static int serverPort;
    // Name shown during read and write ops
    public String name;

    TLSBase() {

        String keyFilename = TESTROOT +  "/" + pathToStores + "/" + keyStoreFile;
        String trustFilename = TESTROOT + "/" + pathToStores + "/" +
            trustStoreFile;
        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);
    }

    // Base read operation
    byte[] read(SSLSocket sock) throws Exception {
        BufferedInputStream is = new BufferedInputStream(sock.getInputStream());
        byte[] b = is.readNBytes(5);
        System.err.println("(read) " + Thread.currentThread().getName() + ": " +
            new String(b));
        return b;
    }

    // Base write operation
    public void write(SSLSocket sock, byte[] data) throws Exception {
        sock.getOutputStream().write(data);
        System.err.println("(write)" + Thread.currentThread().getName() + ": " +
            new String(data));
    }

    private static KeyManager[] getKeyManager(boolean empty) throws Exception {
        FileInputStream fis = null;
        if (!empty) {
            fis = new FileInputStream(System.getProperty("test.root", "./") +
                "/" + pathToStores + "/" + keyStoreFile);
        }
        // Load the keystore
        char[] pwd = passwd.toCharArray();
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(fis, pwd);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(ks, pwd);
        return kmf.getKeyManagers();
    }

    private static TrustManager[] getTrustManager(boolean empty) throws Exception {
        FileInputStream fis = null;
        if (!empty) {
            fis = new FileInputStream(System.getProperty("test.root", "./") +
                "/" + pathToStores + "/" + trustStoreFile);
        }
        // Load the keystore
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(fis, passwd.toCharArray());

        PKIXBuilderParameters pkixParams =
            new PKIXBuilderParameters(ks, new X509CertSelector());

        // Explicitly set revocation based on the command-line
        // parameters, default false
        pkixParams.setRevocationEnabled(false);

        // Register the PKIXParameters with the trust manager factory
        ManagerFactoryParameters trustParams =
            new CertPathTrustManagerParameters(pkixParams);

        // Create the Trust Manager Factory using the PKIX variant
        // and initialize it with the parameters configured above
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustParams);
        return tmf.getTrustManagers();
    }

    /**
     * Server constructor must be called before any client operation so the
     * tls server is ready.  There should be no timing problems as the
     */
    static class Server extends TLSBase {
        SSLServerSocketFactory fac;
        SSLServerSocket ssock;
        // Clients sockets are kept in a hash table with the port as the key.
        ConcurrentHashMap<Integer, SSLSocket> clientMap =
                new ConcurrentHashMap<>();
        List<Exception> exceptionList = new ArrayList<>();
        ExecutorService threadPool = Executors.newFixedThreadPool(1);
        CountDownLatch serverLatch = new CountDownLatch(1);
        Server(ServerBuilder builder) {
            super();
            name = "server";
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(TLSBase.getKeyManager(builder.km),
                    TLSBase.getTrustManager(builder.tm), null);
                fac = sslContext.getServerSocketFactory();
                ssock = (SSLServerSocket) fac.createServerSocket(0);
                ssock.setReuseAddress(true);
                ssock.setNeedClientAuth(builder.clientauth);
                serverPort = ssock.getLocalPort();
                System.err.println("Server Port: " + serverPort);
            } catch (Exception e) {
                System.err.println("Failure during server initialization");
                e.printStackTrace();
            }

            // Thread to allow multiple clients to connect
            new Thread(() -> {
                try {
                    System.err.println("Server starting to accept");
                    serverLatch.countDown();
                    do {
                        SSLSocket sock = (SSLSocket)ssock.accept();
                        threadPool.submit(new ServerThread(sock));
                    } while (true);
                } catch (Exception ex) {
                    System.err.println("Server Down");
                    ex.printStackTrace();
                } finally {
                    threadPool.close();
                }
            }).start();
        }

        class ServerThread extends Thread {
            SSLSocket sock;

            ServerThread(SSLSocket s) {
                this.sock = s;
                System.err.println("(Server) client connection on port " +
                    sock.getPort());
                clientMap.put(sock.getPort(), sock);
            }

            public void run() {
                try {
                    write(sock, read(sock));
                } catch (Exception e) {
                    System.err.println("Caught " + e.getMessage());
                    e.printStackTrace();
                    exceptionList.add(e);
                }
            }
        }

        Server() {
            this(new ServerBuilder());
        }

        public SSLSession getSession(Client client) throws Exception {
            System.err.println("getSession("+client.getPort()+")");
            SSLSocket clientSocket = clientMap.get(client.getPort());
            if (clientSocket == null) {
                throw new Exception("Server can't find client socket");
            }
            return clientSocket.getSession();
        }

        void close(Client client) {
            try {
                System.err.println("close("+client.getPort()+")");
                clientMap.remove(client.getPort()).close();
            } catch (Exception e) {
                ;
            }
        }
        void close() throws InterruptedException {
            clientMap.values().stream().forEach(s -> {
                try {
                    s.close();
                } catch (IOException e) {}
            });
            threadPool.awaitTermination(500, TimeUnit.MILLISECONDS);
        }

        List<Exception> getExceptionList() {
            return exceptionList;
        }
    }

    static class ServerBuilder {
        boolean km = false, tm = false, clientauth = false;

        ServerBuilder setKM(boolean b) {
            km = b;
            return this;
        }

        ServerBuilder setTM(boolean b) {
            tm = b;
            return this;
        }

        ServerBuilder setClientAuth(boolean b) {
            clientauth = b;
            return this;
        }

        Server build() {
            return new Server(this);
        }
    }
    /**
     * Client side will establish a SSLContext instance.
     * It must be run after the Server constructor is called.
     */
    static class Client extends TLSBase {
        public SSLSocket socket;
        boolean km, tm;
        Client() {
            this(false, false);
        }

        /**
         * @param km - true sets an empty key manager
         * @param tm - true sets an empty trust manager
         */
        Client(boolean km, boolean tm) {
            super();
            this.km = km;
            this.tm = tm;
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(TLSBase.getKeyManager(km),
                    TLSBase.getTrustManager(tm), null);
                socket = createSocket();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        Client(Client cl) {
            sslContext = cl.sslContext;
            socket = createSocket();
        }

        public SSLSocket createSocket() {
            try {
                return (SSLSocket) sslContext.getSocketFactory().createSocket();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }

        public SSLSocket connect() {
            try {
                socket.connect(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), serverPort));
                System.err.println("Client (" +
                    Thread.currentThread().getName() +
                    ") connected using port " + socket.getLocalPort() + " to " +
                    socket.getPort());
                writeRead();
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
            return socket;
        }

        public SSLSession getSession() {
            return socket.getSession();
        }
        public void close() {
            try {
                socket.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public int getPort() {
            return socket.getLocalPort();
        }

        private SSLSocket writeRead() {
            try {
                write(socket, "Hello".getBytes(StandardCharsets.ISO_8859_1));
                read(socket);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return socket;
        }

    }
}
