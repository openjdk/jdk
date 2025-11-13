/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=tls12
 * @bug 6668231
 * @summary Presence of a critical subjectAltName causes JSSE's SunX509 to
 *          fail trusted checks
 * @library /test/lib
 * @modules java.base/sun.security.x509 java.base/sun.security.util
 * @run main/othervm CriticalSubjectAltName TLSv1.2 MD5withRSA
 * @author Xuelei Fan
 */


/*
 * @test id=tls13
 * @bug 6668231
 * @summary Presence of a critical subjectAltName causes JSSE's SunX509 to
 *          fail trusted checks
 * @library /test/lib
 * @modules java.base/sun.security.x509 java.base/sun.security.util
 * @run main/othervm CriticalSubjectAltName TLSv1.3 SHA256withRSA
 * @author Xuelei Fan
 */


import jdk.test.lib.security.CertificateBuilder;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.OIDName;
import sun.security.x509.RFC822Name;
import sun.security.x509.SubjectAlternativeNameExtension;


import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;

import jdk.test.lib.security.SecurityUtils;

public class CriticalSubjectAltName implements HostnameVerifier {
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
    public static final char[] PASSPHRASE = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    private final CountDownLatch serverReady = new CountDownLatch(1);
    private final int SERVER_WAIT_SECS = 10;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = Boolean.getBoolean("test.debug");

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
        SSLContext ctx = createServerContext();
        SSLServerSocketFactory sslssf = ctx.getServerSocketFactory();
        SSLServerSocket sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket(serverPort);
        sslServerSocket.setEnabledProtocols(new String[]{protocol});
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady.countDown();

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        OutputStream sslOS = sslSocket.getOutputStream();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(sslOS));
        bw.write("HTTP/1.1 200 OK\r\n\r\n\r\n");
        bw.flush();
        Thread.sleep(5000);
        sslSocket.close();
    }

    private SSLContext createServerContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("Trusted Cert", trustedCert);

        Certificate[] chain = new Certificate[] {serverCert};
        ks.setKeyEntry("Server key", serverKeys.getPrivate(),
                PASSPHRASE, chain);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance(protocol);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, PASSPHRASE);
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    private SSLContext createClientContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("Trusted Cert", trustedCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);
        SSLContext ctx = SSLContext.getInstance(protocol);
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {

        serverReady.await();

        SSLContext ctx = createClientContext();
        URL url = new URL("https://localhost:"+serverPort+"/index.html");
        HttpsURLConnection urlc = (HttpsURLConnection)url.openConnection();
        urlc.setSSLSocketFactory(ctx.getSocketFactory());
        urlc.setHostnameVerifier(this);
        urlc.getInputStream();

        if (urlc.getResponseCode() == -1) {
            throw new RuntimeException("getResponseCode() returns -1");
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

    public static void main(String[] args) throws Exception {
        if (args[1].contains("MD5")) {
            SecurityUtils.removeFromDisabledAlgs(
                    "jdk.certpath.disabledAlgorithms", List.of("MD5"));
            SecurityUtils.removeFromDisabledTlsAlgs("MD5");
        }

        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        /*
         * Start the tests.
         */
        new CriticalSubjectAltName(args[0], args[1]);
    }

    Thread clientThread = null;
    Thread serverThread = null;
    private final String protocol;
    private KeyPair serverKeys;
    private X509Certificate trustedCert;
    private X509Certificate serverCert;

    private void setupCertificates(String signatureAlg) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        KeyPair caKeys = kpg.generateKeyPair();
        serverKeys = kpg.generateKeyPair();

        trustedCert = CertificateBuilder.newCertificateBuilder(
            "CN=Someone, O=Some Org, ST=Some-State, C=US",
                caKeys.getPublic(), caKeys.getPublic())
                .addBasicConstraintsExt(true, true, -1)
                .setOneHourValidity()
                .build(null, caKeys.getPrivate(), signatureAlg);
        if (debug) {
            System.out.println("Trusted Certificate");
            CertificateBuilder.printCertificate(trustedCert, System.out);
        }

        GeneralNames gns = new GeneralNames();
        gns.add(new GeneralName(new RFC822Name("example@openjdk.net")));
        gns.add(new GeneralName(new OIDName("1.2.3.4")));

        serverCert = CertificateBuilder.newCertificateBuilder("",
                serverKeys.getPublic(), caKeys.getPublic())
                .setOneHourValidity()
                .addBasicConstraintsExt(false, false, -1)
                .addExtension(new SubjectAlternativeNameExtension(true, gns))
                .setOneHourValidity()
                .build(trustedCert, caKeys.getPrivate(), signatureAlg);
        if (debug) {
            System.out.println("Server Certificate");
            CertificateBuilder.printCertificate(serverCert, System.out);
        }
    }


    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    CriticalSubjectAltName(String protocol, String signatureAlg) throws Exception {
        this.protocol = protocol;

        setupCertificates(signatureAlg);

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
                        serverReady.countDown();
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

    // Simple test method to blindly agree that hostname and certname match
    public boolean verify(String hostname, SSLSession session) {
        return true;
    }

}
