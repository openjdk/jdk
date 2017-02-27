/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
// Please run in othervm mode.  SunJSSE does not support dynamic system
// properties, no way to re-use system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 8161106 8170329
 * @modules jdk.crypto.ec
 * @summary Improve SSLSocket test template
 * @run main/othervm SSLSocketTemplate
 */

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Template to help speed your client/server tests.
 *
 * Two examples that use this template:
 *    test/sun/security/ssl/ServerHandshaker/AnonCipherWithWantClientAuth.java
 *    test/sun/net/www/protocol/https/HttpsClient/ServerIdentityTest.java
 */
public class SSLSocketTemplate {

    /*
     * ==================
     * Run the test case.
     */
    public static void main(String[] args) throws Exception {
        (new SSLSocketTemplate()).run();
    }

    /*
     * Run the test case.
     */
    public void run() throws Exception {
        bootup();
    }

    /*
     * Define the server side application of the test for the specified socket.
     */
    protected void runServerApplication(SSLSocket socket) throws Exception {
        // here comes the test logic
        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();
    }

    /*
     * Define the client side application of the test for the specified socket.
     * This method is used if the returned value of
     * isCustomizedClientConnection() is false.
     *
     * @param socket may be null is no client socket is generated.
     *
     * @see #isCustomizedClientConnection()
     */
    protected void runClientApplication(SSLSocket socket) throws Exception {
        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();
    }

    /*
     * Define the client side application of the test for the specified
     * server port.  This method is used if the returned value of
     * isCustomizedClientConnection() is true.
     *
     * Note that the client need to connect to the server port by itself
     * for the actual message exchange.
     *
     * @see #isCustomizedClientConnection()
     */
    protected void runClientApplication(int serverPort) throws Exception {
        // blank
    }

    /*
     * Create an instance of SSLContext for client use.
     */
    protected SSLContext createClientSSLContext() throws Exception {
        return createSSLContext(trustedCertStrs,
                endEntityCertStrs, endEntityPrivateKeys,
                endEntityPrivateKeyNames,
                getClientContextParameters());
    }

    /*
     * Create an instance of SSLContext for server use.
     */
    protected SSLContext createServerSSLContext() throws Exception {
        return createSSLContext(trustedCertStrs,
                endEntityCertStrs, endEntityPrivateKeys,
                endEntityPrivateKeyNames,
                getServerContextParameters());
    }

    /*
     * The parameters used to configure SSLContext.
     */
    protected static final class ContextParameters {
        final String contextProtocol;
        final String tmAlgorithm;
        final String kmAlgorithm;

        ContextParameters(String contextProtocol,
                String tmAlgorithm, String kmAlgorithm) {

            this.contextProtocol = contextProtocol;
            this.tmAlgorithm = tmAlgorithm;
            this.kmAlgorithm = kmAlgorithm;
        }
    }

    /*
     * Get the client side parameters of SSLContext.
     */
    protected ContextParameters getClientContextParameters() {
        return new ContextParameters("TLS", "PKIX", "NewSunX509");
    }

    /*
     * Get the server side parameters of SSLContext.
     */
    protected ContextParameters getServerContextParameters() {
        return new ContextParameters("TLS", "PKIX", "NewSunX509");
    }

    /*
     * Does the client side use customized connection other than
     * explicit Socket.connect(), for example, URL.openConnection()?
     */
    protected boolean isCustomizedClientConnection() {
        return false;
    }

    /*
     * Configure the server side socket.
     */
    protected void configureServerSocket(SSLServerSocket socket) {

    }

    /*
     * =============================================
     * Define the client and server side operations.
     *
     * If the client or server is doing some kind of object creation
     * that the other side depends on, and that thread prematurely
     * exits, you may experience a hang.  The test harness will
     * terminate all hung threads after its timeout has expired,
     * currently 3 minutes by default, but you might try to be
     * smart about it....
     */

    /*
     * Is the server ready to serve?
     */
    private final CountDownLatch serverCondition = new CountDownLatch(1);

    /*
     * Is the client ready to handshake?
     */
    private final CountDownLatch clientCondition = new CountDownLatch(1);

    /*
     * What's the server port?  Use any free port by default
     */
    private volatile int serverPort = 0;

    /*
     * Define the server side of the test.
     */
    private void doServerSide() throws Exception {
        // kick start the server side service
        SSLContext context = createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        SSLServerSocket sslServerSocket =
                (SSLServerSocket)sslssf.createServerSocket(serverPort);
        configureServerSocket(sslServerSocket);
        serverPort = sslServerSocket.getLocalPort();

        // Signal the client, the server is ready to accept connection.
        serverCondition.countDown();

        // Try to accept a connection in 30 seconds.
        SSLSocket sslSocket;
        try {
            sslServerSocket.setSoTimeout(30000);
            sslSocket = (SSLSocket)sslServerSocket.accept();
        } catch (SocketTimeoutException ste) {
            // Ignore the test case if no connection within 30 seconds.
            System.out.println(
                "No incoming client connection in 30 seconds. " +
                "Ignore in server side.");
            return;
        } finally {
            sslServerSocket.close();
        }

        // handle the connection
        try {
            // Is it the expected client connection?
            //
            // Naughty test cases or third party routines may try to
            // connection to this server port unintentionally.  In
            // order to mitigate the impact of unexpected client
            // connections and avoid intermittent failure, it should
            // be checked that the accepted connection is really linked
            // to the expected client.
            boolean clientIsReady =
                    clientCondition.await(30L, TimeUnit.SECONDS);

            if (clientIsReady) {
                // Run the application in server side.
                runServerApplication(sslSocket);
            } else {    // Otherwise, ignore
                // We don't actually care about plain socket connections
                // for TLS communication testing generally.  Just ignore
                // the test if the accepted connection is not linked to
                // the expected client or the client connection timeout
                // in 30 seconds.
                System.out.println(
                        "The client is not the expected one or timeout. " +
                        "Ignore in server side.");
            }
        } finally {
            sslSocket.close();
        }
    }

    /*
     * Define the client side of the test.
     */
    private void doClientSide() throws Exception {

        // Wait for server to get started.
        //
        // The server side takes care of the issue if the server cannot
        // get started in 90 seconds.  The client side would just ignore
        // the test case if the serer is not ready.
        boolean serverIsReady =
                serverCondition.await(90L, TimeUnit.SECONDS);
        if (!serverIsReady) {
            System.out.println(
                    "The server is not ready yet in 90 seconds. " +
                    "Ignore in client side.");
            return;
        }

        if (isCustomizedClientConnection()) {
            // Signal the server, the client is ready to communicate.
            clientCondition.countDown();

            // Run the application in client side.
            runClientApplication(serverPort);

            return;
        }

        SSLContext context = createClientSSLContext();
        SSLSocketFactory sslsf = context.getSocketFactory();

        try (SSLSocket sslSocket = (SSLSocket)sslsf.createSocket()) {
            try {
                sslSocket.connect(
                        new InetSocketAddress("localhost", serverPort), 15000);
            } catch (IOException ioe) {
                // The server side may be impacted by naughty test cases or
                // third party routines, and cannot accept connections.
                //
                // Just ignore the test if the connection cannot be
                // established.
                System.out.println(
                        "Cannot make a connection in 15 seconds. " +
                        "Ignore in client side.");
                return;
            }

            // OK, here the client and server get connected.

            // Signal the server, the client is ready to communicate.
            clientCondition.countDown();

            // There is still a chance in theory that the server thread may
            // wait client-ready timeout and then quit.  The chance should
            // be really rare so we don't consider it until it becomes a
            // real problem.

            // Run the application in client side.
            runClientApplication(sslSocket);
        }
    }

    /*
     * =============================================
     * Stuffs to customize the SSLContext instances.
     */

    /*
     * =======================================
     * Certificates and keys used in the test.
     */
    // Trusted certificates.
    private final static String[] trustedCertStrs = {
        // SHA256withECDSA, curve prime256v1
        // Validity
        //    Not Before: Nov 25 04:19:51 2016 GMT
        //    Not After : Nov  5 04:19:51 2037 GMT
        // Subject Key Identifier:
        //    CA:48:E8:00:C1:42:BD:59:9B:79:D9:B4:B4:CE:3F:68:0C:C8:C4:0C
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICHDCCAcGgAwIBAgIJAJtKs6ZEcVjxMAoGCCqGSM49BAMCMDsxCzAJBgNVBAYT\n" +
        "AlVTMQ0wCwYDVQQKEwRKYXZhMR0wGwYDVQQLExRTdW5KU1NFIFRlc3QgU2VyaXZj\n" +
        "ZTAeFw0xNjExMjUwNDE5NTFaFw0zNzExMDUwNDE5NTFaMDsxCzAJBgNVBAYTAlVT\n" +
        "MQ0wCwYDVQQKEwRKYXZhMR0wGwYDVQQLExRTdW5KU1NFIFRlc3QgU2VyaXZjZTBZ\n" +
        "MBMGByqGSM49AgEGCCqGSM49AwEHA0IABKMO/AFDHZia65RaqMIBX7WBdtzFj8fz\n" +
        "ggqMADLJhoszS6qfTUDYskETw3uHfB3KAOENsoKX446qFFPuVjxS1aejga0wgaow\n" +
        "HQYDVR0OBBYEFMpI6ADBQr1Zm3nZtLTOP2gMyMQMMGsGA1UdIwRkMGKAFMpI6ADB\n" +
        "Qr1Zm3nZtLTOP2gMyMQMoT+kPTA7MQswCQYDVQQGEwJVUzENMAsGA1UEChMESmF2\n" +
        "YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2WCCQCbSrOmRHFY8TAPBgNV\n" +
        "HRMBAf8EBTADAQH/MAsGA1UdDwQEAwIBBjAKBggqhkjOPQQDAgNJADBGAiEA5cJ/\n" +
        "jirBbXxzpZ6kdp/Zb/yrIBnr4jiPGJTLgRTb8s4CIQChUDfP1Zqg0qJVfqFNaL4V\n" +
        "a0EAeJHXGZnvCGGqHzoxkg==\n" +
        "-----END CERTIFICATE-----",

        // SHA256withRSA, 2048 bits
        // Validity
        //     Not Before: Nov 25 04:20:02 2016 GMT
        //     Not After : Nov  5 04:20:02 2037 GMT
        // Subject Key Identifier:
        //     A2:DC:55:38:E4:47:7C:8B:D3:E0:CA:FA:AD:3A:C8:4A:DD:12:A0:8E
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDpzCCAo+gAwIBAgIJAO586A+hYNXaMA0GCSqGSIb3DQEBCwUAMDsxCzAJBgNV\n" +
        "BAYTAlVTMQ0wCwYDVQQKEwRKYXZhMR0wGwYDVQQLExRTdW5KU1NFIFRlc3QgU2Vy\n" +
        "aXZjZTAeFw0xNjExMjUwNDIwMDJaFw0zNzExMDUwNDIwMDJaMDsxCzAJBgNVBAYT\n" +
        "AlVTMQ0wCwYDVQQKEwRKYXZhMR0wGwYDVQQLExRTdW5KU1NFIFRlc3QgU2VyaXZj\n" +
        "ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMm3veDSU4zKXO0aAHos\n" +
        "cFRXGLBTe+MUJXAtlkNyx7VKaMZNt5wrUuqzyi/r0LFUdRfNCzZf3X8s8HPHQVii\n" +
        "29tK0y/yeTn4sJTATSmGaAysMJQpKQcfAQ79ItcEGQ721TFQZ3kOBdgp3t/yUYAP\n" +
        "K2tFze/QbIw72LE52SBnPPPTzyimNw7Ai2MLl4eQlyMkTs7JS07zIiAO5QYbS8s+\n" +
        "1NW0A3Y+d0B0q8wYEoHGq7QVjOKlSAksfO0tzi4l0Zu6Uf+J5kMAyZ4ZFgEJvGvw\n" +
        "y/OKJ+soRFH/5cy1SL8B6AWD1y7WXugeeHTHRW1eOjTxqfG1rZqTVd2lfOMER8y1\n" +
        "bXcCAwEAAaOBrTCBqjAdBgNVHQ4EFgQUotxVOORHfIvT4Mr6rTrISt0SoI4wawYD\n" +
        "VR0jBGQwYoAUotxVOORHfIvT4Mr6rTrISt0SoI6hP6Q9MDsxCzAJBgNVBAYTAlVT\n" +
        "MQ0wCwYDVQQKEwRKYXZhMR0wGwYDVQQLExRTdW5KU1NFIFRlc3QgU2VyaXZjZYIJ\n" +
        "AO586A+hYNXaMA8GA1UdEwEB/wQFMAMBAf8wCwYDVR0PBAQDAgEGMA0GCSqGSIb3\n" +
        "DQEBCwUAA4IBAQAtNZJSFeFU6Yid0WSCs2qLAVaTyHsUNSUPUFIIhFAKxdP4DFS0\n" +
        "+aeOFwdqizAU3kReAYULsfwEBgO51lPBSpB+9coUNQwu7cc8Q5Xqw/llRB0PrINS\n" +
        "pZl7PW6Ur2ExTBocnUT9A/nhm8iO4PFD/Ly11sf5OdZihhX69NJ2h3a3gcrLjIpO\n" +
        "L/ewTOgSi5xs+AGGQa+huN3YVL7dh+/rCUvMZVSBX5PloxWS5MMJi0Ui6YjwCFGO\n" +
        "J4G9m7pI+gJs/x1UG0AgplMF2UCFfiY1SAeE2nKAeOOXAXEwEjFy0ToVTmqXx7fg\n" +
        "m9YjhuePxlBrd2DF/YW0pc8gmLnrtm4rKPLz\n" +
        "-----END CERTIFICATE-----",

        // SHA256withDSA, 2048 bits
        // Validity
        //     Not Before: Nov 25 04:19:56 2016 GMT
        //     Not After : Nov  5 04:19:56 2037 GMT
        // Subject Key Identifier:
        //     19:46:10:43:24:6A:A5:14:BE:E2:92:01:79:F0:4C:5F:E1:AE:81:B5
        "-----BEGIN CERTIFICATE-----\n" +
        "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\n" +
        "-----END CERTIFICATE-----"
        };

    // End entity certificate.
    private final static String[] endEntityCertStrs = {
        // SHA256withECDSA, curve prime256v1
        // Validity
        //     Not Before: Nov 25 04:19:51 2016 GMT
        //     Not After : Aug 12 04:19:51 2036 GMT
        // Authority Key Identifier:
        //     CA:48:E8:00:C1:42:BD:59:9B:79:D9:B4:B4:CE:3F:68:0C:C8:C4:0C
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIB1zCCAXygAwIBAgIJAPFq2QL/nUNZMAoGCCqGSM49BAMCMDsxCzAJBgNVBAYT\n" +
        "AlVTMQ0wCwYDVQQKEwRKYXZhMR0wGwYDVQQLExRTdW5KU1NFIFRlc3QgU2VyaXZj\n" +
        "ZTAeFw0xNjExMjUwNDE5NTFaFw0zNjA4MTIwNDE5NTFaMFUxCzAJBgNVBAYTAlVT\n" +
        "MQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZjZTEY\n" +
        "MBYGA1UEAwwPUmVncmVzc2lvbiBUZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD\n" +
        "QgAE4yvRGVvy9iVATyuHPJVdX6+lh/GLm/sRJ5qLT/3PVFOoNIvlEVNiARo7xhyj\n" +
        "2p6bnf32gNg5Ye+QCw20VUv9E6NPME0wCwYDVR0PBAQDAgPoMB0GA1UdDgQWBBSO\n" +
        "hHlHZQp9hyBfSGTSQWeszqMXejAfBgNVHSMEGDAWgBTKSOgAwUK9WZt52bS0zj9o\n" +
        "DMjEDDAKBggqhkjOPQQDAgNJADBGAiEAu3t6cvFglBAZfkhZlEwB04ZjUFqyfiRj\n" +
        "4Hr275E4ZoQCIQDUEonJHlmA19J6oobfR5lYsmoqPm1r0DPm/IiNNKGKKA==\n" +
        "-----END CERTIFICATE-----",

        // SHA256withRSA, 2048 bits
        // Validity
        //     Not Before: Nov 25 04:20:02 2016 GMT
        //     Not After : Aug 12 04:20:02 2036 GMT
        // Authority Key Identifier:
        //     A2:DC:55:38:E4:47:7C:8B:D3:E0:CA:FA:AD:3A:C8:4A:DD:12:A0:8E
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDdjCCAl6gAwIBAgIJAJDcIGOlAmBmMA0GCSqGSIb3DQEBCwUAMDsxCzAJBgNV\n" +
        "BAYTAlVTMQ0wCwYDVQQKEwRKYXZhMR0wGwYDVQQLExRTdW5KU1NFIFRlc3QgU2Vy\n" +
        "aXZjZTAeFw0xNjExMjUwNDIwMDJaFw0zNjA4MTIwNDIwMDJaMFUxCzAJBgNVBAYT\n" +
        "AlVTMQ0wCwYDVQQKDARKYXZhMR0wGwYDVQQLDBRTdW5KU1NFIFRlc3QgU2VyaXZj\n" +
        "ZTEYMBYGA1UEAwwPUmVncmVzc2lvbiBUZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOC\n" +
        "AQ8AMIIBCgKCAQEAp0dHrifTg2aY0sH03f2JjK2fW4DL6gLDKq0YirsNE07z54LF\n" +
        "IeeDio49XwPjB7OpbUTC1hf/YKZ7XiRWyPa1rYozZ88emhZt+cpkyKz+nmW4avlA\n" +
        "WnrV+gx4+bU9T+WuBWdAraBcq27Y1I26yfCEtC8k3+O0sdlHbhasF+BUDmX/n4+n\n" +
        "ifJdbNm5eSDx8eFYHFTdjhAud3An2X6QD9WWSoJpPdDi4utHhFAlxW6osjJxsAPv\n" +
        "zo8YsqmpCMjZcEks4ZsqiZKKiWUWUAjCcbMerDPDX29fyeo348uztvJsmNRzfcwl\n" +
        "FtwxpYdxuZqwHi2QoNaQTGXjjbZFmjn7qEkjXwIDAQABo2MwYTALBgNVHQ8EBAMC\n" +
        "A+gwHQYDVR0OBBYEFP+henfufE6Znr60lRkmayadVdxnMB8GA1UdIwQYMBaAFKLc\n" +
        "VTjkR3yL0+DK+q06yErdEqCOMBIGA1UdEQEB/wQIMAaHBH8AAAEwDQYJKoZIhvcN\n" +
        "AQELBQADggEBAK56pV2XoAIkrHFTCkWtYX518nuvkzN6a6BqPKALQlmlbJnq/lhV\n" +
        "tPQx79b0j7tow28l2ze/3M0hRb5Ft/d/7mITZNMR+0owk4U51AU2NacRt7fpoxu5\n" +
        "wX3hTa4VgX2+BAXeoWF+Yzy6Jj5gAVmSLzBnkTUH0d+EyL1pp+DFE3QdvZqf3+nP\n" +
        "zkxz15h3iW8FwI+7/19MX2j2XB/sG8mJpqoszWw8lM4qCa2eWyCbqSHhPi+/+rGg\n" +
        "dDG5uzZeOC845GEH2T3tHDA+F3WwcZG/W+4RR6ZaaHlqPKKMcwFL73YbsqdCiKBv\n" +
        "p6sXrhIiP0oXImRBRLDlidj5TIOLfAtNM9A=\n" +
        "-----END CERTIFICATE-----",

        // SHA256withDSA, 2048 bits
        // Validity
        //    Not Before: Nov 25 04:19:56 2016 GMT
        //    Not After : Aug 12 04:19:56 2036 GMT
        // Authority Key Identifier:
        //     19:46:10:43:24:6A:A5:14:BE:E2:92:01:79:F0:4C:5F:E1:AE:81:B5
        "-----BEGIN CERTIFICATE-----\n" +
        "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\n" +
        "-----END CERTIFICATE-----"
        };

    // Private key in the format of PKCS#8.
    private final static String[] endEntityPrivateKeys = {
        //
        // EC private key related to cert endEntityCertStrs[0].
        //
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgGAy4Pxrd2keM7AdP\n" +
        "VNUMEO5iO681v4/tstVGfdXkCTuhRANCAATjK9EZW/L2JUBPK4c8lV1fr6WH8Yub\n" +
        "+xEnmotP/c9UU6g0i+URU2IBGjvGHKPanpud/faA2Dlh75ALDbRVS/0T",

        //
        // RSA private key related to cert endEntityCertStrs[1].
        //
        "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCnR0euJ9ODZpjS\n" +
        "wfTd/YmMrZ9bgMvqAsMqrRiKuw0TTvPngsUh54OKjj1fA+MHs6ltRMLWF/9gpnte\n" +
        "JFbI9rWtijNnzx6aFm35ymTIrP6eZbhq+UBaetX6DHj5tT1P5a4FZ0CtoFyrbtjU\n" +
        "jbrJ8IS0LyTf47Sx2UduFqwX4FQOZf+fj6eJ8l1s2bl5IPHx4VgcVN2OEC53cCfZ\n" +
        "fpAP1ZZKgmk90OLi60eEUCXFbqiyMnGwA+/OjxiyqakIyNlwSSzhmyqJkoqJZRZQ\n" +
        "CMJxsx6sM8Nfb1/J6jfjy7O28myY1HN9zCUW3DGlh3G5mrAeLZCg1pBMZeONtkWa\n" +
        "OfuoSSNfAgMBAAECggEAWnAHKPkPObN2XDvQj1RL0WrtBSOVG2dy7Ne4tQh8ATxm\n" +
        "UXw56CKq03YjaANJ8xgHObQ7QlSnFTHs8PDkmrIHd1OIh09LVDNcMfhilLwyzKBi\n" +
        "HDO1vzU6Cn5DyX1bMJ8UfodcSIKyl1zOjdwyaItIs8HpRcJuJtk57SME18PIrh9H\n" +
        "EWchWSxTvPvKDY2bhb4vBMgVPfTQO3yc8gY/1J5vKPqDpyEuCGjV13zd/AoL/u5A\n" +
        "sG10hQ2veJ9KAn1xwPwEoAkCdNLL8vPB1rCbeMZNamqHZRihfoOgDnnJIf3FtUFF\n" +
        "8bS2FM2kGQR+05SZdhBmJl0obPrbBWil/2vxqeFrgQKBgQDZl1yQsFss2BKK2VAh\n" +
        "9PKc8tU1v6RpHQZhJEDSct2slHQS5DF6bWl5kJiPevXSvcnjgoPamZf7Joii+Rds\n" +
        "3xtPQmRw2vQmXBQzQS1YoRuh4hUlkdFWCjUNNg1kvipue6FO4PVg3ViP7llN8PXK\n" +
        "rSpVxn0a36UiN9jN2zMOUs6tJwKBgQDEzlqa7DghMli7TUdL44uvD2/ceiHqHMMw\n" +
        "5eLsEHeRVn/EVU99beKw/dAOGwRssTpCd9h7fwzQ2503/Qb/Goe0nKE7+xvt3/sE\n" +
        "n2Y8Qfv1W1+hGb2qU2jhQaR5bZrLZp0+BgRuQ4ttpYvzopYe4FLZWhDBA0zsGyu0\n" +
        "nCi7lUSrCQKBgGeGYW8hyS9r2l6fiEWvsiLEUnbRKFsuiRN82S6HojpzI0q9sWDL\n" +
        "X6yMBFn3qa/LxpttRGikPTAsJERN+Tw+ZlLuhrU/J3x8wMumDfomJOx/kYofd5bV\n" +
        "ImqXtgWhiLSqM5RA6d5dUb6hK3Iu2/LDMuo+ltVLZNkD8y32RbNh6J1vAoGAbLqQ\n" +
        "pgyhSf3Vtc0Q+aVB87p0k3tKJ1wynl4zSzYhyMLgHakAHIzL8/qVqmVUwXP8euJZ\n" +
        "UIk1nGHobxk0d1XB6Y+rKEcn+/iFZt1ljx7pQ3ly0L824NXqGKC6bHeYUI1li/Gp\n" +
        "Gv3oFvCh7D1D8NUAEKLIpMndAohUUhkAC/qAkHkCgYEAzSIarDNquayV+umC1SXm\n" +
        "Zo6XLuzWjudLxPd2lyCfwR2aRKlrb+5OFYErX+RSLyCJmaqVZMyXP09PBIvNXu2Z\n" +
        "+gbx5WUC+kA+6zdKEPXowei6i6EHMXYT2AL7395ZbPajZjsCduE3WuUztuHrhtMm\n" +
        "JI+k1o4rCnSLlX4gWdN1oTs=",

        //
        // DSA private key related to cert endEntityCertStrs[2].
        //
        "MIICZAIBADCCAjkGByqGSM44BAEwggIsAoIBAQCWte2WHSAob+cnmLjyt818YFBB\n" +
        "hrHVAwM30FMbTK8grXN2jYFi8bDdf0GL6fb/oYeLcJxttFXpghB/UJcdOj8YO+A8\n" +
        "EjgPN0nnDtsu3HYJqx6XqfXa8Sm11WWn8AYW00sIVfiAzuxDuvi2kb2bsmv2DqjS\n" +
        "BPRDcRVZNDazbAweqXwxF2bAmQCzDdpnJfChp9au73cyUFBFexerixj99851X5kM\n" +
        "liR92FtPVlQhVx7w82RxlwztjtkMCFNQeQLF5zhni5yoF8xKwqDSsFG1RT9FtDlX\n" +
        "iaOKXGXy//E1RCxhvdALrh9VdxrKuj3cwgz8OFL/7+Zu/+pMF6gUMQCQO44TAiEA\n" +
        "jkSBlZh8uZwS0YSD+Lwu77E8W+AkwKT1ZmXi+YEyMLUCggEAdlWS2NtpNviEDOnX\n" +
        "kRity5e7TwoRqypxpk27VE4SM2u9EqfQ5kwYi8zBGPiIBvJGLk+Uk1cVo7NHMVwi\n" +
        "mRjEFrYqDLYlzwNjNxwNP/BNJgy0ps29EQHdscZw8hrLqqNXlZsezy4zd259xI4Q\n" +
        "//o93Scd2VUnDBsqu7FVqaVL6bnRF4vtnrX+hlB463F9u47qO7uM0NXswvx2BtR/\n" +
        "oxBahoXUOm+x/iwR2R/S3oDsiGUzK8wGP08CK++7F6zSVkaMZgxKygfvG0Q8m2IT\n" +
        "SbMJWZtDeWGFJT5nqYG5el88Ar9MtHfNYd22sqiDy1xFLUQJfRrjNITQJQNWPqkp\n" +
        "/cSxWAQiAiAKHYbYwEy0XS9J0MeKQmqPswn0nCJKvH+esfMKkZvV3w=="
        };

    // Private key names of endEntityPrivateKeys.
    private final static String[] endEntityPrivateKeyNames = {
        "EC",
        "RSA",
        "DSA",
        };

    /*
     * Create an instance of SSLContext with the specified trust/key materials.
     */
    private SSLContext createSSLContext(
            String[] trustedMaterials,
            String[] keyMaterialCerts,
            String[] keyMaterialKeys,
            String[] keyMaterialKeyAlgs,
            ContextParameters params) throws Exception {

        KeyStore ts = null;     // trust store
        KeyStore ks = null;     // key store
        char passphrase[] = "passphrase".toCharArray();

        // Generate certificate from cert string.
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // Import the trused certs.
        ByteArrayInputStream is;
        if (trustedMaterials != null && trustedMaterials.length != 0) {
            ts = KeyStore.getInstance("JKS");
            ts.load(null, null);

            Certificate[] trustedCert =
                    new Certificate[trustedMaterials.length];
            for (int i = 0; i < trustedMaterials.length; i++) {
                String trustedCertStr = trustedMaterials[i];

                is = new ByteArrayInputStream(trustedCertStr.getBytes());
                try {
                    trustedCert[i] = cf.generateCertificate(is);
                } finally {
                    is.close();
                }

                ts.setCertificateEntry("trusted-cert-" + i, trustedCert[i]);
            }
        }

        // Import the key materials.
        //
        // Note that certification pathes bigger than one are not supported yet.
        boolean hasKeyMaterials =
            (keyMaterialCerts != null) && (keyMaterialCerts.length != 0) &&
            (keyMaterialKeys != null) && (keyMaterialKeys.length != 0) &&
            (keyMaterialKeyAlgs != null) && (keyMaterialKeyAlgs.length != 0) &&
            (keyMaterialCerts.length == keyMaterialKeys.length) &&
            (keyMaterialCerts.length == keyMaterialKeyAlgs.length);
        if (hasKeyMaterials) {
            ks = KeyStore.getInstance("JKS");
            ks.load(null, null);

            for (int i = 0; i < keyMaterialCerts.length; i++) {
                String keyCertStr = keyMaterialCerts[i];

                // generate the private key.
                PKCS8EncodedKeySpec priKeySpec = new PKCS8EncodedKeySpec(
                    Base64.getMimeDecoder().decode(keyMaterialKeys[i]));
                KeyFactory kf =
                    KeyFactory.getInstance(keyMaterialKeyAlgs[i]);
                PrivateKey priKey = kf.generatePrivate(priKeySpec);

                // generate certificate chain
                is = new ByteArrayInputStream(keyCertStr.getBytes());
                Certificate keyCert = null;
                try {
                    keyCert = cf.generateCertificate(is);
                } finally {
                    is.close();
                }

                Certificate[] chain = new Certificate[] { keyCert };

                // import the key entry.
                ks.setKeyEntry("cert-" + keyMaterialKeyAlgs[i],
                        priKey, passphrase, chain);
            }
        }

        // Create an SSLContext object.
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(params.tmAlgorithm);
        tmf.init(ts);

        SSLContext context = SSLContext.getInstance(params.contextProtocol);
        if (hasKeyMaterials && ks != null) {
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance(params.kmAlgorithm);
            kmf.init(ks, passphrase);

            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            context.init(null, tmf.getTrustManagers(), null);
        }

        return context;
    }

    /*
     * =================================================
     * Stuffs to boot up the client-server mode testing.
     */
    private Thread clientThread = null;
    private Thread serverThread = null;
    private volatile Exception serverException = null;
    private volatile Exception clientException = null;

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    private static final boolean separateServerThread = false;

    /*
     * Boot up the testing, used to drive remainder of the test.
     */
    private void bootup() throws Exception {
        Exception startException = null;
        try {
            if (separateServerThread) {
                startServer(true);
                startClient(false);
            } else {
                startClient(true);
                startServer(false);
            }
        } catch (Exception e) {
            startException = e;
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            if (serverThread != null) {
                serverThread.join();
            }
        } else {
            if (clientThread != null) {
                clientThread.join();
            }
        }

        /*
         * When we get here, the test is pretty much over.
         * Which side threw the error?
         */
        Exception local;
        Exception remote;

        if (separateServerThread) {
            remote = serverException;
            local = clientException;
        } else {
            remote = clientException;
            local = serverException;
        }

        Exception exception = null;

        /*
         * Check various exception conditions.
         */
        if ((local != null) && (remote != null)) {
            // If both failed, return the curthread's exception.
            local.initCause(remote);
            exception = local;
        } else if (local != null) {
            exception = local;
        } else if (remote != null) {
            exception = remote;
        } else if (startException != null) {
            exception = startException;
        }

        /*
         * If there was an exception *AND* a startException,
         * output it.
         */
        if (exception != null) {
            if (exception != startException && startException != null) {
                exception.addSuppressed(startException);
            }
            throw exception;
        }

        // Fall-through: no exception to throw!
    }

    private void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                @Override
                public void run() {
                    try {
                        doServerSide();
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        logException("Server died", e);
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            try {
                doServerSide();
            } catch (Exception e) {
                logException("Server failed", e);
                serverException = e;
            }
        }
    }

    private void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                @Override
                public void run() {
                    try {
                        doClientSide();
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        logException("Client died", e);
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            try {
                doClientSide();
            } catch (Exception e) {
                logException("Client failed", e);
                clientException = e;
            }
        }
    }

    private synchronized void logException(String prefix, Throwable cause) {
        System.out.println(prefix + ": " + cause);
        cause.printStackTrace(System.out);
    }
}
