/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4323990 4413069
 * @summary HttpsURLConnection doesn't send Proxy-Authorization on CONNECT
 *     Incorrect checking of proxy server response
 * @run main/othervm ProxyAuthTest
 *
 *     No way to reserve and restore java.lang.Authenticator, need to run this
 *     test in othervm mode.
 */

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import javax.net.*;
import javax.net.ssl.*;
import java.security.cert.*;

/*
 * ProxyAuthTest.java -- includes a simple server that can serve
 * Http get request in both clear and secure channel, and a client
 * that makes https requests behind the firewall through an
 * authentication proxy
 */

public class ProxyAuthTest {
    /*
     * Where do we find the keystores?
     */
    static String pathToStores = "../../../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    volatile private static int serverPort = 0;

    /*
     * The TestServer implements a OriginServer that
     * processes HTTP requests and responses.
     */
    static class TestServer extends OriginServer {
        public TestServer(ServerSocket ss) throws Exception {
            super(ss);
        }

        /*
         * Returns an array of bytes containing the bytes for
         * the data sent in the response.
         *
         * @return bytes for the data in the response
         */
        public byte[] getBytes() {
            return "Proxy authentication for tunneling succeeded ..".
                        getBytes();
        }
    }

    /*
     * Main method to create the server and the client
     */
    public static void main(String args[]) throws Exception {
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

        boolean useSSL = true;
        /*
         * setup the server
         */
        try {
            ServerSocketFactory ssf =
                ProxyAuthTest.getServerSocketFactory(useSSL);
            ServerSocket ss = ssf.createServerSocket(serverPort);
            serverPort = ss.getLocalPort();
            new TestServer(ss);
        } catch (Exception e) {
            System.out.println("Server side failed:" +
                                e.getMessage());
            throw e;
        }
        // trigger the client
        try {
            doClientSide();
        } catch (Exception e) {
            System.out.println("Client side failed: " + e.getMessage());
            throw e;
        }
    }

    private static ServerSocketFactory getServerSocketFactory
                   (boolean useSSL) throws Exception {
        if (useSSL) {
            SSLServerSocketFactory ssf = null;
            // set up key manager to do server authentication
            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            char[] passphrase = passwd.toCharArray();

            ctx = SSLContext.getInstance("TLS");
            kmf = KeyManagerFactory.getInstance("SunX509");
            ks = KeyStore.getInstance("JKS");

            ks.load(new FileInputStream(System.getProperty(
                        "javax.net.ssl.keyStore")), passphrase);
            kmf.init(ks, passphrase);
            ctx.init(kmf.getKeyManagers(), null, null);

            ssf = ctx.getServerSocketFactory();
            return ssf;
        } else {
            return ServerSocketFactory.getDefault();
        }
    }

    static void doClientSide() throws Exception {
        /*
         * setup up a proxy with authentication information
         */
        setupProxy();

        /*
         * we want to avoid URLspoofCheck failures in cases where the cert
         * DN name does not match the hostname in the URL.
         */
        HttpsURLConnection.setDefaultHostnameVerifier(
                                      new NameVerifier());
        URL url = new URL("https://" + "localhost:" + serverPort
                                + "/index.html");
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(
                               url.openStream()));
            String inputLine;
            System.out.print("Client recieved from the server: ");
            while ((inputLine = in.readLine()) != null)
                System.out.println(inputLine);
            in.close();
        } catch (SSLException e) {
            if (in != null)
                in.close();
            throw e;
        }
    }

    static class NameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    static void setupProxy() throws IOException {
        ProxyTunnelServer pserver = new ProxyTunnelServer();
        /*
         * register a system wide authenticator and setup the proxy for
         * authentication
         */
        Authenticator.setDefault(new TestAuthenticator());

        // register with the username and password
        pserver.needUserAuth(true);
        pserver.setUserAuth("Test", "test123");

        pserver.start();
        System.setProperty("https.proxyHost", "localhost");
        System.setProperty("https.proxyPort", String.valueOf(
                                        pserver.getPort()));
    }

    public static class TestAuthenticator extends Authenticator {

        public PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication("Test",
                                         "test123".toCharArray());
        }
    }
}
