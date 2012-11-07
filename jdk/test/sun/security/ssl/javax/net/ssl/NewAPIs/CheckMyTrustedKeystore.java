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
 * @bug 4329114
 * @summary Need better way of reflecting the reason when a chain is
 *      rejected as untrusted.
 * @run main/othervm CheckMyTrustedKeystore
 *
 *     SunJSSE does not support dynamic system properties, no way to re-use
 *     system properties in samevm/agentvm mode.
 * @ignore JSSE supports algorithm constraints with CR 6916074,
 *     need to update this test case in JDK 7 soon
 * This is a serious hack job!
 * @author Brad Wetmore
 */

import java.io.*;
import java.net.*;
import java.security.*;
import javax.net.ssl.*;
import java.security.cert.*;

public class CheckMyTrustedKeystore {

    /*
     * =============================================================
     * Set the various variables needed for the tests, then
     * specify what tests to run on each side.
     */

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;

    /*
     * Where do we find the keystores?
     */
    final static String pathToStores = "../../../../etc";
    final static String keyStoreFile = "keystore";
    final static String trustStoreFile = "truststore";
    final static String unknownStoreFile = "unknown_keystore";
    final static String passwd = "passphrase";
    final static char[] cpasswd = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    final static boolean debug = false;

    /*
     * If the client or server is doing some kind of object creation
     * that the other side depends on, and that thread prematurely
     * exits, you may experience a hang.  The test harness will
     * terminate all hung threads after its timeout has expired,
     * currently 3 minutes by default, but you might try to be
     * smart about it....
     */

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        com.sun.net.ssl.SSLContext ctx =
            com.sun.net.ssl.SSLContext.getInstance("TLS");
        com.sun.net.ssl.KeyManagerFactory kmf =
            com.sun.net.ssl.KeyManagerFactory.getInstance("SunX509");

        ks.load(new FileInputStream(keyFilename), cpasswd);
        kmf.init(ks, cpasswd);

        com.sun.net.ssl.TrustManager [] tms =
            new com.sun.net.ssl.TrustManager []
            { new MyComX509TrustManager() };

        ctx.init(kmf.getKeyManagers(), tms, null);

        SSLServerSocketFactory sslssf =
            (SSLServerSocketFactory) ctx.getServerSocketFactory();

        SSLServerSocket sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();

        sslServerSocket.setNeedClientAuth(true);

        /*
         * Create using the other type.
         */
        SSLContext ctx1 =
            SSLContext.getInstance("TLS");
        KeyManagerFactory kmf1 =
            KeyManagerFactory.getInstance("SunX509");

        TrustManager [] tms1 =
            new TrustManager []
            { new MyJavaxX509TrustManager() };

        kmf1.init(ks, cpasswd);

        ctx1.init(kmf1.getKeyManagers(), tms1, null);

        sslssf = (SSLServerSocketFactory) ctx1.getServerSocketFactory();

        SSLServerSocket sslServerSocket1 =
            (SSLServerSocket) sslssf.createServerSocket(serverPort1);
        serverPort1 = sslServerSocket1.getLocalPort();
        sslServerSocket1.setNeedClientAuth(true);

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        sslServerSocket.close();
        serverReady = false;

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();
        sslSocket.close();

        sslSocket = (SSLSocket) sslServerSocket1.accept();
        sslIS = sslSocket.getInputStream();
        sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();
        sslSocket.close();

        System.out.println("Server exiting!");
        System.out.flush();
    }

    void doTest(SSLSocket sslSocket) throws Exception {
        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        System.out.println("  Writing");
        sslOS.write(280);
        sslOS.flush();
        System.out.println("  Reading");
        sslIS.read();

        sslSocket.close();
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {

        /*
         * Wait for server to get started.
         */
        while (!serverReady) {
            Thread.sleep(50);
        }

        /*
         * See if an unknown keystore actually gets checked ok.
         */
        System.out.println("==============");
        System.out.println("Starting test0");
        KeyStore uks = KeyStore.getInstance("JKS");
        SSLContext ctx =
            SSLContext.getInstance("TLS");
        KeyManagerFactory kmf =
            KeyManagerFactory.getInstance("SunX509");

        uks.load(new FileInputStream(unknownFilename), cpasswd);
        kmf.init(uks, cpasswd);

        TrustManager [] tms = new TrustManager []
            { new MyJavaxX509TrustManager() };

        ctx.init(kmf.getKeyManagers(), tms, null);

        SSLSocketFactory sslsf =
            (SSLSocketFactory) ctx.getSocketFactory();

        System.out.println("Trying first socket " + serverPort);
        SSLSocket sslSocket = (SSLSocket)
            sslsf.createSocket("localhost", serverPort);

        doTest(sslSocket);

        /*
         * Now try the other way.
         */
        com.sun.net.ssl.SSLContext ctx1 =
            com.sun.net.ssl.SSLContext.getInstance("TLS");
        com.sun.net.ssl.KeyManagerFactory kmf1 =
            com.sun.net.ssl.KeyManagerFactory.getInstance("SunX509");
        kmf1.init(uks, cpasswd);

        com.sun.net.ssl.TrustManager [] tms1 =
            new com.sun.net.ssl.TrustManager []
            { new MyComX509TrustManager() };

        ctx1.init(kmf1.getKeyManagers(), tms1, null);

        sslsf = (SSLSocketFactory) ctx1.getSocketFactory();

        System.out.println("Trying second socket " + serverPort1);
        sslSocket = (SSLSocket) sslsf.createSocket("localhost",
            serverPort1);

        doTest(sslSocket);
        System.out.println("Completed test1");
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    int serverPort = 0;
    int serverPort1 = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    final static String keyFilename =
        System.getProperty("test.src", "./") + "/" + pathToStores +
        "/" + keyStoreFile;
    final static String unknownFilename =
        System.getProperty("test.src", "./") + "/" + pathToStores +
        "/" + unknownStoreFile;

    public static void main(String[] args) throws Exception {

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new CheckMyTrustedKeystore();
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    CheckMyTrustedKeystore() throws Exception {
        if (separateServerThread) {
            startServer(true);
            startClient(false);
        } else {
            startClient(true);
            startServer(false);
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            serverThread.join();
        } else {
            clientThread.join();
        }

        /*
         * When we get here, the test is pretty much over.
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (serverException != null) {
            System.out.print("Server Exception:");
            throw serverException;
        }
        if (clientException != null) {
            System.out.print("Client Exception:");
            throw clientException;
        }
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                public void run() {
                    try {
                        doServerSide();
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        System.err.println("Server died...");
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            doServerSide();
        }
    }

    void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                public void run() {
                    try {
                        doClientSide();
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        System.err.println("Client died...");
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            doClientSide();
        }
    }
}

class MyComX509TrustManager implements com.sun.net.ssl.X509TrustManager {

    public X509Certificate[] getAcceptedIssuers() {
        return (new X509Certificate[0]);
    }

    public boolean isClientTrusted(X509Certificate[] chain) {
        System.out.println("    IsClientTrusted?");
        return true;
    }

    public boolean isServerTrusted(X509Certificate[] chain) {
        System.out.println("    IsServerTrusted?");
        return true;
    }
}

class MyJavaxX509TrustManager implements X509TrustManager {

    public X509Certificate[] getAcceptedIssuers() {
        return (new X509Certificate[0]);
    }

    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        System.out.println("    CheckClientTrusted(" + authType + ")?");
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        System.out.println("    CheckServerTrusted(" + authType + ")?");
    }
}
