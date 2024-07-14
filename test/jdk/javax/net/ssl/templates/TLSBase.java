/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.Buffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is a base setup for creating a server and clients.  All clients will
 * connect to the server on construction.  The server constructor must be run
 * first.  The idea is for the test code to be minimal as possible without
 * this library class being complicated.
 *
 * Server.done() must be called or the server will never exit and hang the test.
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
        /*
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(sock.getInputStream()));
        String s = null;
        while (reader.ready() && (s = reader.readLine()) != null);
         */
        BufferedInputStream is = new BufferedInputStream(sock.getInputStream());
        byte[] b = is.readNBytes(5);
        System.err.println("(read) " + Thread.currentThread().getName() + ": " + new String(b));
        return b;
    }

    // Base write operation
    public void write(SSLSocket sock, byte[] data) throws Exception {
        /*
        PrintWriter out = new PrintWriter(
            new OutputStreamWriter(sock.getOutputStream()));
        out.println(new String(data));
        out.flush();
         */
        sock.getOutputStream().write(data);
        System.err.println("(write)" + Thread.currentThread().getName() + ": " + new String(data));
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
        Thread t;
        List<Exception> exceptionList = new ArrayList<>();
        int c;
        ExecutorService threadPool = Executors.newFixedThreadPool(10,
            r -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                return t;
            });


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
                System.out.println("Server Port: " + serverPort);
            } catch (Exception e) {
                System.err.println("Failure during server initialization");
                e.printStackTrace();
            }

            // Thread to allow multiple clients to connect
            t = new Thread(() -> {
                //ExecutorService executor = Executors.newFixedThreadPool(5);
                try {
                    while (true) {
                        SSLSocket sock = (SSLSocket)ssock.accept();
                        threadPool.submit(new ServerThread(sock));
                    }
                } catch (Exception ex) {
                    System.err.println("Server Down");
                    ex.printStackTrace();
                } finally {
                    //executor.close();
                    threadPool.close();
                }
            });
            t.start();
        }

        class ServerThread extends Thread {
            SSLSocket sock;

            ServerThread(SSLSocket s) {
                this.sock = s;
                clientMap.put(sock.getPort(), sock);
            }

            public void run() {
                try {
                    write(sock, read(sock));
                } catch (Exception e) {
                    System.out.println("Caught " + e.getMessage());
                    e.printStackTrace();
                    exceptionList.add(e);
                }
            }
        }

        Server() {
            this(new ServerBuilder());
        }

        /**
         * @param km - true for an empty key manager
         * @param tm - true for an empty trust manager
         */
        Server(boolean km, boolean tm) {
            super();
            name = "server";
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(TLSBase.getKeyManager(km),
                    TLSBase.getTrustManager(tm), null);
                fac = sslContext.getServerSocketFactory();
                ssock = (SSLServerSocket) fac.createServerSocket(0);
                ssock.setNeedClientAuth(true);
                serverPort = ssock.getLocalPort();
            } catch (Exception e) {
                System.err.println("Failure during server initialization");
                e.printStackTrace();
            }

                // Thread to allow multiple clients to connect
                t = new Thread(() -> {
                    try {
                        while (true) {
                            System.err.println("Server ready on port " +
                                serverPort);
                            SSLSocket c = (SSLSocket)ssock.accept();
                            clientMap.put(c.getPort(), c);
                            try {
                                write(c, read(c));
                            } catch (Exception e) {
                                System.out.println("Caught " + e.getMessage());
                                e.printStackTrace();
                                exceptionList.add(e);
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Server Down");
                        ex.printStackTrace();
                    }
                });
                t.start();
            }

        // Exit test to quit the test.  This must be called at the end of the
        // test or the test will never end.
        void done() {
            try {
                t.join(5000);
                ssock.close();
            } catch (Exception e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
        }

        // Read from the client
        byte[] read(Client client) throws Exception {
            SSLSocket s = clientMap.get(Integer.valueOf(client.getPort()));
            if (s == null) {
                System.err.println("No socket found, port " + client.getPort());
            }
            return read(s);
        }

        // Write to the client
        void write(Client client, byte[] data) throws Exception {
            write(clientMap.get(client.getPort()), data);
        }

        // Server writes to the client, then reads from the client.
        // Return true if the read & write data match, false if not.
        boolean writeRead(Client client, String s) throws Exception{
            write(client, s.getBytes());
            return (Arrays.compare(s.getBytes(), client.read()) == 0);
        }

        // Get the SSLSession from the server side socket
        SSLSession getSession(Client c) {
            SSLSocket s = clientMap.get(Integer.valueOf(c.getPort()));
            return s.getSession();
        }

        // Close client socket
        void close(Client c) throws IOException {
            SSLSocket s = clientMap.get(Integer.valueOf(c.getPort()));
            s.close();
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
     * Client side will establish a connection from the constructor and wait.
     * It must be run after the Server constructor is called.
     */
    static class Client extends TLSBase {
        SSLSocket sock;
        boolean km, tm;
        Client() {
            this(false, false);
        }

        /**
         * @param km - true sets an empty key manager
         * @param tm - true sets an empty trust manager
         */
        Client(boolean km, boolean tm) {
            this(km, tm, true);
        }

        Client(boolean km, boolean tm, boolean connect) {
            super();
            this.km = km;
            this.tm = tm;
            if (connect) {
                this.sock = connect();
            }
        }

        private SSLSocket connect() {
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(TLSBase.getKeyManager(km), TLSBase.getTrustManager(tm), null);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return getNewSocket();
        }

        public SSLSession getSession() {
            return sock.getSession();
        }

        public SSLSocket getSocket() {
            return sock;
        }

        private SSLSocket getNewSocket() {
            SSLSocket newSock = null;
            try {
                newSock = (SSLSocket) sslContext.getSocketFactory().createSocket();
                newSock.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), serverPort));
                System.err.println("Client (" + Thread.currentThread().getName() + ") connected using port " +
                    newSock.getLocalPort() + " to " + newSock.getPort() + " socket: " + (sock == null? "null" : sock));
                name = "client(" + newSock + ")";
                writeRead(newSock);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return newSock;
        }

        public SSLSocket getResumptionSocket() {
            return getNewSocket();
        }

        public SSLSocket getNewFreshSocket() {
            try {
                return (SSLSocket) sslContext.getSocketFactory().createSocket();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }

        static public void resetSocket(SSLSocket socket) {
            try {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), serverPort));
                System.err.println("Client reset (" + Thread.currentThread().getName() + ") connected using port " +
                    socket.getLocalPort() + " to " + socket.getPort() + " socket: " + (socket == null ? "null" : socket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writeRead(SSLSocket socket) {
            try {
                write(socket, "Hello".getBytes(StandardCharsets.ISO_8859_1));
                read(socket);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public SSLSession getNewSession() {
            return getNewSocket().getSession();
        }

        byte[] read() throws Exception {
            return read(sock);
        }

        // Get port from the socket
        int getPort() {
            return sock.getLocalPort();
        }

        // Close socket
        void close() throws IOException {
            sock.close();
        }
    }
}
