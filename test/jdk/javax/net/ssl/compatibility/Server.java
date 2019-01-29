/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

/*
 * A simple SSL socket server.
 */
public class Server {

    private final SSLServerSocket serverSocket;

    public Server(SSLContext context, int port) throws Exception {
        SSLServerSocketFactory serverFactory = context.getServerSocketFactory();
        serverSocket = (SSLServerSocket) serverFactory.createServerSocket(port);
        serverSocket.setSoTimeout(Utils.TIMEOUT);
    }

    public Server(Cert[] certs, int port) throws Exception {
        this(Utils.createSSLContext(certs), port);
    }

    public Server(Cert[] certs) throws Exception {
        this(certs, 0);
    }

    private void setEnabledCipherSuites(String... cipherSuites) {
        serverSocket.setEnabledCipherSuites(cipherSuites);
    }

    private void setEnabledProtocols(String... protocols) {
        serverSocket.setEnabledProtocols(protocols);
    }

    private void setNeedClientAuth(boolean needClientAuth) {
        serverSocket.setNeedClientAuth(needClientAuth);
    }

    private void setApplicationProtocols(String... protocols) {
        SSLParameters params = serverSocket.getSSLParameters();
        params.setApplicationProtocols(protocols);
        serverSocket.setSSLParameters(params);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void accept() throws IOException {
        SSLSocket socket = null;
        try {
            socket = (SSLSocket) serverSocket.accept();

            InputStream in = socket.getInputStream();
            in.read();

            OutputStream out = socket.getOutputStream();
            out.write('S');
            out.flush();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    public void close() throws IOException {
        serverSocket.close();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("----- Server start -----");
        String protocol = System.getProperty(Utils.PROP_PROTOCOL);
        String cipherSuite = System.getProperty(Utils.PROP_CIPHER_SUITE);
        boolean clientAuth
                = Utils.getBoolProperty(Utils.PROP_CLIENT_AUTH);
        String appProtocols = System.getProperty(Utils.PROP_APP_PROTOCOLS);
        boolean supportsALPN
                = Utils.getBoolProperty(Utils.PROP_SUPPORTS_ALPN_ON_SERVER);
        boolean negativeCase
                = Utils.getBoolProperty(Utils.PROP_NEGATIVE_CASE_ON_SERVER);

        System.out.println(Utils.join(Utils.PARAM_DELIMITER,
                "ServerJDK=" + System.getProperty(Utils.PROP_SERVER_JDK),
                "Protocol=" + protocol,
                "CipherSuite=" + cipherSuite,
                "ClientAuth=" + clientAuth,
                "AppProtocols=" + appProtocols));

        Status status = Status.SUCCESS;
        Server server = null;
        try {
            server = new Server(Cert.getCerts(CipherSuite.cipherSuite(cipherSuite)));
            System.out.println("port=" + server.getPort());
            server.setNeedClientAuth(clientAuth);
            server.setEnabledProtocols(protocol);
            server.setEnabledCipherSuites(cipherSuite);
            if (appProtocols != null) {
                if (supportsALPN) {
                    server.setApplicationProtocols(
                            Utils.split(appProtocols, Utils.VALUE_DELIMITER));
                } else {
                    System.out.println(
                            "Ignored due to server doesn't support ALPN.");
                }
            }

            savePort(server.getPort());
            server.accept();

            status = negativeCase ? Status.UNEXPECTED_SUCCESS : Status.SUCCESS;
        } catch (Exception exception) {
            status = Utils.handleException(exception, negativeCase);
        } finally {
            if (server != null) {
                server.close();
            }

            deletePortFile();
        }

        System.out.println("STATUS: " + status);
        System.out.println("----- Server end -----");
    }

    private static void deletePortFile() {
        File portFile = new File(Utils.PORT_LOG);
        if (portFile.exists() && !portFile.delete()) {
            throw new RuntimeException("Cannot delete port log");
        }
    }

    private static void savePort(int port) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(new File(Utils.PORT_LOG));
            writer.write(port + "");
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
