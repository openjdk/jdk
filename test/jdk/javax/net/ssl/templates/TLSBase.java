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
import java.security.KeyStore;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
    static String pathToStores = "../etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    SSLContext sslContext;
    // Server's port
    static int serverPort;
    // Name shown during read and write ops
    String name;

    TLSBase() {
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
    }

    // Base read operation
    byte[] read(SSLSocket sock) throws Exception {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(sock.getInputStream()));
        String s = reader.readLine();
        System.err.println("(read) " + name + ": " + s);
        return s.getBytes();
    }

    // Base write operation
    public void write(SSLSocket sock, byte[] data) throws Exception {
        PrintWriter out = new PrintWriter(
            new OutputStreamWriter(sock.getOutputStream()));
        out.println(new String(data));
        out.flush();
        System.err.println("(write)" + name + ": " + new String(data));
    }

    private static KeyManager[] getKeyManager(boolean empty) throws Exception {
        FileInputStream fis = null;
        if (!empty) {
            fis = new FileInputStream(System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + keyStoreFile);
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
            fis = new FileInputStream(System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + trustStoreFile);
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
        boolean exit = false;
        Thread t;
        List<Exception> exceptionList = new ArrayList<>();

        Server(ServerBuilder builder) {
            super();
            name = "server";
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(TLSBase.getKeyManager(builder.km), TLSBase.getTrustManager(builder.tm), null);
                fac = sslContext.getServerSocketFactory();
                ssock = (SSLServerSocket) fac.createServerSocket(0);
                ssock.setNeedClientAuth(builder.clientauth);
                serverPort = ssock.getLocalPort();
            } catch (Exception e) {
                System.err.println(e.getMessage());
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
                sslContext.init(TLSBase.getKeyManager(km), TLSBase.getTrustManager(tm), null);
                fac = sslContext.getServerSocketFactory();
                ssock = (SSLServerSocket) fac.createServerSocket(0);
                ssock.setNeedClientAuth(true);
                serverPort = ssock.getLocalPort();
            } catch (Exception e) {
                System.err.println(e.getMessage());
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
                                e.printStackTrace();
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
                t.interrupt();
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
            super();
            this.km = km;
            this.tm = tm;
            connect();
        }

        // Connect to server.  Maybe runnable in the future
        public SSLSocket connect() {
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(TLSBase.getKeyManager(km), TLSBase.getTrustManager(tm), null);
                sock = (SSLSocket)sslContext.getSocketFactory().createSocket();
                sock.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), serverPort));
                System.err.println("Client connected using port " +
                        sock.getLocalPort());
                name = "client(" + sock.toString() + ")";
                write("Hello");
                read();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return sock;
        }

        // Read from the client socket
        byte[] read() throws Exception {
            return read(sock);
        }

        // Write to the client socket
        void write(byte[] data) throws Exception {
            write(sock, data);
        }
        void write(String s) throws Exception {
            write(sock, s.getBytes());
        }

        // Client writes to the server, then reads from the server.
        // Return true if the read & write data match, false if not.
        boolean writeRead(Server server, String s) throws Exception {
            write(s.getBytes());
            return (Arrays.compare(s.getBytes(), server.read(this)) == 0);
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
