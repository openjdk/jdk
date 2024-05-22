/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/* @test
 * @bug 6766775
 * @summary X509 certificate hostname checking is broken in JDK1.6.0_10
 * @library /test/jdk/java/security/testlibrary
 * @modules java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 *          java.base/sun.security.validator
 *          java.base/sun.security.x509
 * @build CertificateBuilder
 * @run main/othervm Identities
 * @author Xuelei Fan
 */

import java.net.*;
import java.security.*;
import java.io.*;
import javax.net.ssl.*;
import sun.security.testlibrary.CertificateBuilder;
import sun.security.x509.*;


public class Identities extends IdentitiesBase {


    static char passphrase[] = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Is the connection ready to close?
     */
    volatile static boolean closeReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = Boolean.getBoolean("test.debug");

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = getSSLContext(serverCertificate, serverKeysRsa1024,
                passphrase);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        // doClientSide() connects to "localhost"
        InetAddress localHost = InetAddress.getByName("localhost");
        InetSocketAddress address = new InetSocketAddress(localHost, serverPort);

        SSLServerSocket sslServerSocket = (SSLServerSocket) sslssf.createServerSocket();
        sslServerSocket.bind(address);
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        sslSocket.setNeedClientAuth(true);

        PrintStream out =
                new PrintStream(sslSocket.getOutputStream());

        try {
            // ignore request data

            // send the response
            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Content-Type: text/html; charset=iso-8859-1\r\n");
            out.print("Content-Length: "+ 9 +"\r\n");
            out.print("\r\n");
            out.print("Testing\r\n");
            out.flush();
        } finally {
             // close the socket
             while (!closeReady) {
                 Thread.sleep(50);
             }

             System.out.println("Server closing socket");
             sslSocket.close();
             serverReady = false;
        }
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {
        SSLContext reservedSSLContext = SSLContext.getDefault();
        try {
            SSLContext context = getSSLContext(clientCertificate, clientKeysRsa1024,
                    passphrase);

            SSLContext.setDefault(context);

            /*
             * Wait for server to get started.
             */
            while (!serverReady) {
                Thread.sleep(50);
            }

            HttpsURLConnection http = null;

            /* establish http connection to server */
            URL url = new URL("https://localhost:" + serverPort+"/");
            System.out.println("url is "+url.toString());

            try {
                http = (HttpsURLConnection)url.openConnection(Proxy.NO_PROXY);

                int respCode = http.getResponseCode();
                System.out.println("respCode = "+respCode);
            } finally {
                if (http != null) {
                    http.disconnect();
                }
                closeReady = true;
            }
        } finally {
            SSLContext.setDefault(reservedSSLContext);
        }
    }

    // use any free port by default
    volatile int serverPort = 0;

    public static void main(String args[]) throws Exception {
        // MD5 is used in this test case, don't disable MD5 algorithm.
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                "MD2, RSA keySize < 1024");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DH keySize < 768");

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new Identities().run();
    }

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    Identities() throws Exception {
        GeneralNames gns = new GeneralNames();
        gns.add(new GeneralName(new IPAddressName("127.0.0.1")));
        gns.add(new GeneralName(new DNSName("localhost")));

        serverCertificate = CertificateBuilder.newEndEntity(
            "C = US, ST = Some-State, L = Some-City, O = Some-Org, OU = SSL-Server, CN = localhost",
            serverKeysRsa1024.getPublic(), caKeysRsa1024.getPublic(),
            new SubjectAlternativeNameExtension(true, gns))
            .build(caCertificate, caKeysRsa1024.getPrivate(), "MD5withRSA");


        clientCertificate = CertificateBuilder.newEndEntity(
            "C = US, ST = Some-State, L = Some-City, O = Some-Org, OU = SSL-Client, CN = localhost",
            clientKeysRsa1024.getPublic(), caKeysRsa1024.getPublic(),
            new SubjectAlternativeNameExtension(true, gns))
            .build(caCertificate, caKeysRsa1024.getPrivate(), "MD5withRSA");

        if (debug) {
            printCertificate("Server", serverCertificate);
            printCertificate("Client", clientCertificate);
        }
    }
}
