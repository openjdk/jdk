/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import javax.net.*;
import javax.net.ssl.*;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 4423074
 * @modules java.base/sun.net.www
 * @summary This test case is written to test the https POST through a proxy
 *          with proxy authentication. It includes a simple server that serves
 *          http POST method requests in secure channel, and a client that
 *          makes https POST request through a proxy.
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Platform
 *        jdk.test.lib.process.*
 * @compile OriginServer.java ProxyTunnelServer.java
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes= PostThruProxyWithAuth
 */
public class PostThruProxyWithAuth {

    private static final String TEST_SRC = System.getProperty("test.src", ".");
    private static final int TIMEOUT = 30000;

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
            return
                "Https POST thru proxy is successful with proxy authentication".
                getBytes();
        }
    }

    /*
     * Main method to create the server and client
     */
    public static void main(String args[]) throws Exception {
        String keyFilename = TEST_SRC + "/" + pathToStores + "/" + keyStoreFile;
        String trustFilename = TEST_SRC + "/" + pathToStores + "/"
                + trustStoreFile;

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);

        boolean useSSL = true;
        /*
         * setup the server
         */
        try {
            ServerSocketFactory ssf = getServerSocketFactory(useSSL);
            ServerSocket ss = ssf.createServerSocket(serverPort);
            ss.setSoTimeout(TIMEOUT);  // 30 seconds
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
            System.out.println("Client side failed: " +
                                e.getMessage());
            throw e;
          }
    }

    private static ServerSocketFactory getServerSocketFactory
                   (boolean useSSL) throws Exception {
        if (useSSL) {
            // set up key manager to do server authentication
            SSLContext ctx = SSLContext.getInstance("TLS");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            KeyStore ks = KeyStore.getInstance("JKS");
            char[] passphrase = passwd.toCharArray();

            ks.load(new FileInputStream(System.getProperty(
                        "javax.net.ssl.keyStore")), passphrase);
            kmf.init(ks, passphrase);
            ctx.init(kmf.getKeyManagers(), null, null);

            return ctx.getServerSocketFactory();
        } else {
            return ServerSocketFactory.getDefault();
        }
    }

    /*
     * Message to be posted
     */
    static String postMsg = "Testing HTTP post on a https server";

    static void doClientSide() throws Exception {
        /*
         * setup up a proxy
         */
        SocketAddress pAddr = setupProxy();

        /*
         * we want to avoid URLspoofCheck failures in cases where the cert
         * DN name does not match the hostname in the URL.
         */
        HttpsURLConnection.setDefaultHostnameVerifier(
                                      new NameVerifier());
        URL url = new URL("https://" + getHostname() + ":" + serverPort);

        Proxy p = new Proxy(Proxy.Type.HTTP, pAddr);
        HttpsURLConnection https = (HttpsURLConnection)url.openConnection(p);
        https.setConnectTimeout(TIMEOUT);
        https.setReadTimeout(TIMEOUT);
        https.setDoOutput(true);
        https.setRequestMethod("POST");
        PrintStream ps = null;
        try {
           ps = new PrintStream(https.getOutputStream());
           ps.println(postMsg);
           ps.flush();
           if (https.getResponseCode() != 200) {
                throw new RuntimeException("test Failed");
           }
           ps.close();
           // clear the pipe
           BufferedReader in = new BufferedReader(
                                new InputStreamReader(
                                https.getInputStream()));
           String inputLine;
           while ((inputLine = in.readLine()) != null)
                 System.out.println("Client received: " + inputLine);
           in.close();
        } catch (SSLException e) {
            if (ps != null)
                ps.close();
            throw e;
        } catch (SocketTimeoutException e) {
            System.out.println("Client can not get response in time: "
                    + e.getMessage());
        }
    }

    static class NameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    static SocketAddress setupProxy() throws IOException {
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

        return new InetSocketAddress("localhost", pserver.getPort());
    }

    public static class TestAuthenticator extends Authenticator {
        public PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication("Test",
                                         "test123".toCharArray());
        }
    }

    private static String getHostname() {
        try {
            OutputAnalyzer oa = ProcessTools.executeCommand("hostname");
            return oa.getOutput().trim();
        } catch (Throwable e) {
            throw new RuntimeException("Get hostname failed.", e);
        }
    }
}
