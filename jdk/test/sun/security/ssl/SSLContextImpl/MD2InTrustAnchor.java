/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @test
 * @bug 7113275
 * @summary compatibility issue with MD2 trust anchor and old X509TrustManager
 * @run main/othervm MD2InTrustAnchor PKIX TLSv1.1
 * @run main/othervm MD2InTrustAnchor SunX509 TLSv1.1
 * @run main/othervm MD2InTrustAnchor PKIX TLSv1.2
 * @run main/othervm MD2InTrustAnchor SunX509 TLSv1.2
 */

import java.net.*;
import java.util.*;
import java.io.*;
import javax.net.ssl.*;
import java.security.Security;
import java.security.KeyStore;
import java.security.KeyFactory;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.*;
import java.security.interfaces.*;
import java.util.Base64;

public class MD2InTrustAnchor {

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
    static boolean separateServerThread = false;

    /*
     * Certificates and key used in the test.
     */

    // It's a trust anchor signed with MD2 hash function.
    static String trustedCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICkjCCAfugAwIBAgIBADANBgkqhkiG9w0BAQIFADA7MQswCQYDVQQGEwJVUzEN\n" +
        "MAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UwHhcN\n" +
        "MTExMTE4MTExNDA0WhcNMzIxMDI4MTExNDA0WjA7MQswCQYDVQQGEwJVUzENMAsG\n" +
        "A1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UwgZ8wDQYJ\n" +
        "KoZIhvcNAQEBBQADgY0AMIGJAoGBAPGyB9tugUGgxtdeqe0qJEwf9x1Gy4BOi1yR\n" +
        "wzDZY4H5LquvIfQ2V3J9X1MQENVsFvkvp65ZcFcy+ObOucXUUPFcd/iw2DVb5QXA\n" +
        "ffyeVqWD56GPi8Qe37wrJO3L6fBhN9oxp/BbdRLgjU81zx8qLEyPODhPMxV4OkcA\n" +
        "SDwZTSxxAgMBAAGjgaUwgaIwHQYDVR0OBBYEFLOAtr/YrYj9H04EDLA0fd14jisF\n" +
        "MGMGA1UdIwRcMFqAFLOAtr/YrYj9H04EDLA0fd14jisFoT+kPTA7MQswCQYDVQQG\n" +
        "EwJVUzENMAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2\n" +
        "Y2WCAQAwDwYDVR0TAQH/BAUwAwEB/zALBgNVHQ8EBAMCAQYwDQYJKoZIhvcNAQEC\n" +
        "BQADgYEAr8ExpXu/FTIRiMzPm0ubqwME4lniilwQUiEOD/4DbksNjEIcUyS2hIk1\n" +
        "qsmjJz3SHBnwhxl9dhJVwk2tZLkPGW86Zn0TPVRsttK4inTgCC9GFGeqQBdrU/uf\n" +
        "lipBzXWljrfbg4N/kK8m2LabtKUMMnGysM8rN0Fx2PYm5xxGvtM=\n" +
        "-----END CERTIFICATE-----";

    // The certificate issued by above trust anchor, signed with MD5
    static String targetCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICeDCCAeGgAwIBAgIBAjANBgkqhkiG9w0BAQQFADA7MQswCQYDVQQGEwJVUzEN\n" +
        "MAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UwHhcN\n" +
        "MTExMTE4MTExNDA2WhcNMzEwODA1MTExNDA2WjBPMQswCQYDVQQGEwJVUzENMAsG\n" +
        "A1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UxEjAQBgNV\n" +
        "BAMTCWxvY2FsaG9zdDCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAwDnm96mw\n" +
        "fXCH4bgXk1US0VcJsQVxUtGMyncAveMuzBzNzOmKZPeqyYX1Fuh4q+cuza03WTJd\n" +
        "G9nOkNr364e3Rn1aaHjCMcBmFflObnGnhhufNmIGYogJ9dJPmhUVPEVAXrMG+Ces\n" +
        "NKy2E8woGnLMrqu6yiuTClbLBPK8fWzTXrECAwEAAaN4MHYwCwYDVR0PBAQDAgPo\n" +
        "MB0GA1UdDgQWBBSdRrpocLPJXyGfDmMWJrcEf29WGDAfBgNVHSMEGDAWgBSzgLa/\n" +
        "2K2I/R9OBAywNH3deI4rBTAnBgNVHSUEIDAeBggrBgEFBQcDAQYIKwYBBQUHAwIG\n" +
        "CCsGAQUFBwMDMA0GCSqGSIb3DQEBBAUAA4GBAKJ71ZiCUykkJrCLYUxlFlhvUcr9\n" +
        "sTcOc67QdroW5f412NI15SXWDiley/JOasIiuIFPjaJBjOKoHOvTjG/snVu9wEgq\n" +
        "YNR8dPsO+NM8r79C6jO+Jx5fYAC7os2XxS75h3NX0ElJcbwIXGBJ6xRrsFh/BGYH\n" +
        "yvudOlX4BkVR0l1K\n" +
        "-----END CERTIFICATE-----";

    // Private key in the format of PKCS#8.
    static String targetPrivateKey =
        "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAMA55vepsH1wh+G4\n" +
        "F5NVEtFXCbEFcVLRjMp3AL3jLswczczpimT3qsmF9RboeKvnLs2tN1kyXRvZzpDa\n" +
        "9+uHt0Z9Wmh4wjHAZhX5Tm5xp4YbnzZiBmKICfXST5oVFTxFQF6zBvgnrDSsthPM\n" +
        "KBpyzK6rusorkwpWywTyvH1s016xAgMBAAECgYEAn9bF3oRkdDoBU0i/mcww5I+K\n" +
        "SH9tFt+WQbiojjz9ac49trkvUfu7MO1Jui2+QbrvaSkyj+HYGFOJd1wMsPXeB7ck\n" +
        "5mOIYV4uZK8jfNMSQ8v0tFEeIPp5lKdw1XnrQfSe+abo2eL5Lwso437Y4s3w37+H\n" +
        "aY3d76hR5qly+Ys+Ww0CQQDjeOoX89d/xhRqGXKjCx8ImE/dPmsI8O27cwtKrDYJ\n" +
        "6t0v/xryVIdvOYcRBvKnqEogOH7T1kI+LnWKUTJ2ehJ7AkEA2FVloPVqCehXcc7e\n" +
        "z3TDpU9w1B0JXklcV5HddYsRqp9RukN/VK4szKE7F1yoarIUtfE9Lr9082Jwyp3M\n" +
        "L11xwwJBAKsZ+Hur3x0tUY29No2Nf/pnFyvEF57SGwA0uPmiL8Ol9lpz+UDudDEl\n" +
        "hIM6Rqv12kwCMuQE9i7vo1o3WU3k5KECQEqhg1L49yD935TqiiFFpe0Ur9btQXse\n" +
        "kdXAA4d2d5zGI7q/aGD9SYU6phkUJSHR16VA2RuUfzMrpb+wmm1IrmMCQFtLoKRT\n" +
        "A5kokFb+E3Gplu29tJvCUpfwgBFRS+wmkvtiaU/tiyDcVgDO+An5DwedxxdVzqiE\n" +
        "njWHoKY3axDQ8OU=\n";


    static char passphrase[] = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = generateSSLContext(trustedCertStr, targetCertStr,
                                            targetPrivateKey);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        SSLServerSocket sslServerSocket =
            (SSLServerSocket)sslssf.createServerSocket(serverPort);
        sslServerSocket.setNeedClientAuth(true);
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();
        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write('A');
        sslOS.flush();

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

        SSLContext context = generateSSLContext(trustedCertStr, targetCertStr,
                                            targetPrivateKey);
        SSLSocketFactory sslsf = context.getSocketFactory();

        SSLSocket sslSocket =
            (SSLSocket)sslsf.createSocket("localhost", serverPort);

        // enable the specified TLS protocol
        sslSocket.setEnabledProtocols(new String[] {tlsProtocol});

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write('B');
        sslOS.flush();
        sslIS.read();

        sslSocket.close();
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */
    private static String tmAlgorithm;        // trust manager
    private static String tlsProtocol;        // trust manager

    private static void parseArguments(String[] args) {
        tmAlgorithm = args[0];
        tlsProtocol = args[1];
    }

    private static SSLContext generateSSLContext(String trustedCertStr,
            String keyCertStr, String keySpecStr) throws Exception {

        // generate certificate from cert string
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        // import the trused cert
        Certificate trusedCert = null;
        ByteArrayInputStream is = null;
        if (trustedCertStr != null) {
            is = new ByteArrayInputStream(trustedCertStr.getBytes());
            trusedCert = cf.generateCertificate(is);
            is.close();

            ks.setCertificateEntry("RSA Export Signer", trusedCert);
        }

        if (keyCertStr != null) {
            // generate the private key.
            PKCS8EncodedKeySpec priKeySpec = new PKCS8EncodedKeySpec(
                                Base64.getMimeDecoder().decode(keySpecStr));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPrivateKey priKey =
                    (RSAPrivateKey)kf.generatePrivate(priKeySpec);

            // generate certificate chain
            is = new ByteArrayInputStream(keyCertStr.getBytes());
            Certificate keyCert = cf.generateCertificate(is);
            is.close();

            // It's not allowed to send MD2 signed certificate to peer,
            // even it may be a trusted certificate. Then we will not
            // place the trusted certficate in the chain.
            Certificate[] chain = new Certificate[1];
            chain[0] = keyCert;

            // import the key entry.
            ks.setKeyEntry("Whatever", priKey, passphrase, chain);
        }

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmAlgorithm);
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance(tlsProtocol);
        if (keyCertStr != null && !keyCertStr.isEmpty()) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
            kmf.init(ks, passphrase);

            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            ks = null;
        } else {
            ctx.init(null, tmf.getTrustManagers(), null);
        }

        return ctx;
    }


    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    public static void main(String[] args) throws Exception {
        // MD5 is used in this test case, don't disable MD5 algorithm.
        Security.setProperty(
                "jdk.certpath.disabledAlgorithms", "MD2, RSA keySize < 1024");

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Get the customized arguments.
         */
        parseArguments(args);

        /*
         * Start the tests.
         */
        new MD2InTrustAnchor();
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    MD2InTrustAnchor() throws Exception {
        try {
            if (separateServerThread) {
                startServer(true);
                startClient(false);
            } else {
                startClient(true);
                startServer(false);
            }
        } catch (Exception e) {
            // swallow for now.  Show later
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
         * Which side threw the error?
         */
        Exception local;
        Exception remote;
        String whichRemote;

        if (separateServerThread) {
            remote = serverException;
            local = clientException;
            whichRemote = "server";
        } else {
            remote = clientException;
            local = serverException;
            whichRemote = "client";
        }

        /*
         * If both failed, return the curthread's exception, but also
         * print the remote side Exception
         */
        if ((local != null) && (remote != null)) {
            System.out.println(whichRemote + " also threw:");
            remote.printStackTrace();
            System.out.println();
            throw local;
        }

        if (remote != null) {
            throw remote;
        }

        if (local != null) {
            throw local;
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
            try {
                doServerSide();
            } catch (Exception e) {
                serverException = e;
            } finally {
                serverReady = true;
            }
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
            try {
                doClientSide();
            } catch (Exception e) {
                clientException = e;
            }
        }
    }
}
