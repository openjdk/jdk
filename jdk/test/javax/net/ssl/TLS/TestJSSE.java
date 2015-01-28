/**
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 only, as published by
 * the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License version 2 for more
 * details (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version 2
 * along with this work; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA or
 * visit www.oracle.com if you need additional information or have any
 * questions.
 */

import static java.lang.System.out;
import java.security.Provider;
import java.security.Security;

/**
 * @test
 * @bug 8049429
 * @library ../../../../lib/testlibrary/
 * @build jdk.testlibrary.Utils
 * @compile CipherTestUtils.java JSSEClient.java JSSEServer.java
 * @summary Test that all cipher suites work in all versions and all client
 * authentication types. The way this is setup the server is stateless and
 * all checking is done on the client side.
 * @run main/othervm -DSERVER_PROTOCOL=SSLv3
 *        -DCLIENT_PROTOCOL=SSLv3
 *        -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=TLSv1
 *        -DCLIENT_PROTOCOL=SSLv3,TLSv1,TLSv1.1,TLSv1.2
 *        -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=TLSv1.1
 *        -DCLIENT_PROTOCOL=SSLv3,TLSv1,TLSv1.1,TLSv1.2
 *        -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=TLSv1.2
 *        -DCLIENT_PROTOCOL=SSLv3,TLSv1,TLSv1.1,TLSv1.2
 *        -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=SSLv3,TLSv1
 *        -DCLIENT_PROTOCOL=TLSv1 -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=SSLv3,TLSv1,TLSv1.1
 *        -DCLIENT_PROTOCOL=TLSv1.1 -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=SSLv3
 *        -DCLIENT_PROTOCOL=TLSv1.1,TLSv1.2
 *        -DCIPHER=SSL_RSA_WITH_RC4_128_MD5
 *        TestJSSE javax.net.ssl.SSLHandshakeException
 * @run main/othervm -DSERVER_PROTOCOL=TLSv1
 *        -DCLIENT_PROTOCOL=TLSv1.1,TLSv1.2
 *        -DCIPHER=SSL_RSA_WITH_RC4_128_MD5
 *        TestJSSE javax.net.ssl.SSLHandshakeException
 * @run main/othervm -DSERVER_PROTOCOL=SSLv3,TLSv1,TLSv1.1,TLSv1.2
 *        -DCLIENT_PROTOCOL=TLSv1.2 -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=SSLv2Hello,SSLv3,TLSv1
 *        -DCLIENT_PROTOCOL=DEFAULT -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=SSLv2Hello,SSLv3,TLSv1,TLSv1.1,TLSv1.2
 *        -DCLIENT_PROTOCOL=DEFAULT -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=SSLv2Hello,SSLv3,TLSv1,TLSv1.1,TLSv1.2
 *        -DCLIENT_PROTOCOL=DEFAULT -Djdk.tls.client.protocols=TLSv1
 *        -DCIPHER=SSL_RSA_WITH_RC4_128_MD5 TestJSSE
 * @run main/othervm -DSERVER_PROTOCOL=SSLv2Hello,SSLv3,TLSv1
 *        -DCLIENT_PROTOCOL=DEFAULT -Djdk.tls.client.protocols=TLSv1.2
 *        -DCIPHER=SSL_RSA_WITH_RC4_128_MD5
 *        TestJSSE javax.net.ssl.SSLHandshakeException
 *
 */

public class TestJSSE {

    private static final String LOCAL_IP = "127.0.0.1";

    public static void main(String... args) throws Exception {
        // reset the security property to make sure that the algorithms
        // and keys used in this test are not disabled.
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        String serverProtocol = System.getProperty("SERVER_PROTOCOL");
        String clientProtocol = System.getProperty("CLIENT_PROTOCOL");
        int port = jdk.testlibrary.Utils.getFreePort();
        String cipher = System.getProperty("CIPHER");
        if (serverProtocol == null
                || clientProtocol == null
                || cipher == null) {
            throw new IllegalArgumentException("SERVER_PROTOCOL "
                    + "or CLIENT_PROTOCOL or CIPHER is missing");
        }
        out.println("ServerProtocol =" + serverProtocol);
        out.println("ClientProtocol =" + clientProtocol);
        out.println("Cipher         =" + cipher);
        server(serverProtocol, cipher, port, args);
        client(port, clientProtocol, cipher, args);

    }

    public static void client(int testPort,
            String testProtocols, String testCipher,
            String... exception) throws Exception {
        String expectedException = exception.length >= 1
                ? exception[0] : null;
        out.println("=========================================");
        out.println(" Testing - https://" + LOCAL_IP + ":" + testPort);
        out.println(" Testing - Protocol : " + testProtocols);
        out.println(" Testing - Cipher : " + testCipher);
        Provider p = new sun.security.ec.SunEC();
        Security.insertProviderAt(p, 1);
        try {
            CipherTestUtils.main(new JSSEFactory(LOCAL_IP,
                    testPort, testProtocols,
                    testCipher, "client JSSE"),
                    "client", expectedException);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void server(String testProtocol, String testCipher,
            int testPort,
            String... exception) throws Exception {
        String expectedException = exception.length >= 1
                ? exception[0] : null;
        out.println(" This is Server");
        out.println(" Testing Protocol: " + testProtocol);
        out.println(" Testing Cipher: " + testCipher);
        out.println(" Testing Port: " + testPort);
        Provider p = new sun.security.ec.SunEC();
        Security.insertProviderAt(p, 1);
        try {
            CipherTestUtils.main(new JSSEFactory(null, testPort,
                    testProtocol, testCipher, "Server JSSE"),
                    "Server", expectedException);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class JSSEFactory extends CipherTestUtils.PeerFactory {

        final String testedCipherSuite, testedProtocol, testHost;
        final int testPort;
        final String name;

        JSSEFactory(String testHost, int testPort, String testedProtocol,
                String testedCipherSuite, String name) {
            this.testedCipherSuite = testedCipherSuite;
            this.testedProtocol = testedProtocol;
            this.testHost = testHost;
            this.testPort = testPort;
            this.name = name;
        }

        @Override
        String getName() {
            return name;
        }

        @Override
        String getTestedCipher() {
            return testedCipherSuite;
        }

        @Override
        String getTestedProtocol() {
            return testedProtocol;
        }

        @Override
        CipherTestUtils.Client newClient(CipherTestUtils cipherTest)
                throws Exception {
            return new JSSEClient(cipherTest, testHost, testPort,
                    testedProtocol, testedCipherSuite);
        }

        @Override
        CipherTestUtils.Server newServer(CipherTestUtils cipherTest)
                throws Exception {
            return new JSSEServer(cipherTest, testPort,
                    testedProtocol, testedCipherSuite);
        }
    }
}
