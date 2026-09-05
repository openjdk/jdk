/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test id=tls12
 * @bug 6766775
 * @summary X509 certificate hostname checking is broken in JDK1.6.0_10
 * @library /test/lib
 * @run main/othervm DNSIdentities TLSv1.2 MD5withRSA
 * @author Xuelei Fan
 */

/* @test id=tls13
 * @bug 6766775
 * @summary X509 certificate hostname checking is broken in JDK1.6.0_10
 * @library /test/lib
 * @run main/othervm DNSIdentities TLSv1.3 SHA256withRSA
 * @author Xuelei Fan
 */

import jdk.test.lib.security.CertificateBuilder;
import jdk.test.lib.security.SecurityUtils;
import sun.security.x509.DNSName;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.SubjectAlternativeNameExtension;


import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.List;


public class DNSIdentities extends IdentitiesBase {
    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;


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
    static boolean debug = false;

    private SSLServerSocket sslServerSocket = null;

    @Override
    protected CertificateBuilder customizeServerCert(CertificateBuilder builder) throws Exception {
        GeneralNames gns = new GeneralNames();
        gns.add(new GeneralName(new DNSName("localhost")));
        return builder.addExtension(new SubjectAlternativeNameExtension(true, gns));
    }

    @Override
    protected CertificateBuilder customizeClientCert(CertificateBuilder builder) throws Exception {
        GeneralNames gns = new GeneralNames();
        gns.add(new GeneralName(new DNSName("localhost")));
        return builder.addExtension(new SubjectAlternativeNameExtension(true, gns));
    }

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = getServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        // doClientSide() connects to "localhost"
        InetAddress localHost = InetAddress.getByName("localhost");
        InetSocketAddress address = new InetSocketAddress(localHost, serverPort);

        sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket();
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
            SSLContext context = getClientSSLContext();

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

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    public static void main(String [] args) throws Exception {
        if (args[1].contains("MD5")) {
            SecurityUtils.removeFromDisabledAlgs(
                    "jdk.certpath.disabledAlgorithms", List.of("MD5"));
            SecurityUtils.removeFromDisabledTlsAlgs("MD5");
        }

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new DNSIdentities(args[0], args[1]);
    }

    Thread clientThread = null;
    Thread serverThread = null;
    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    DNSIdentities(String protocol, String signatureAlg) throws Exception {
        super(protocol, signatureAlg);
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
        if (serverException != null)
            throw serverException;
        if (clientException != null)
            throw clientException;
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
