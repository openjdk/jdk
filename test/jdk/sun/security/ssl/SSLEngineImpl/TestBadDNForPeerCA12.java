/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8294985
 * @library /test/lib
 * @summary SSLEngine throws IAE during parsing of X500Principal
 * @run main/othervm TestBadDNForPeerCA12
 */

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.Base64;
import java.util.HexFormat;


public class TestBadDNForPeerCA12 {

    // Test was originally written for TLSv1.2
    private static final String proto = "TLSv1.2";

    private static final boolean debug = false;

    private final SSLContext sslc;

    protected SSLEngine clientEngine;     // client Engine
    protected ByteBuffer clientOut;       // write side of clientEngine
    protected ByteBuffer clientIn;        // read side of clientEngine
    private ByteBuffer cTOs;            // "reliable" transport client->server
    protected ByteBuffer sTOc;          // "reliable" transport server->client

    private static final String keyStoreFile =
        System.getProperty("test.src", "./")
            + "/../../../../javax/net/ssl/etc/keystore";

    // this contains a server response with invalid DNs
    private static final byte[] serverPayload = Base64.getDecoder().decode(
        "FgMDBD0CAABVAwNKWHt3CgoZpm/OCew8thyunjMH+x098nzpC1eKd/PNpCBRO9+fS2VPFA7Do9BOr/LUkTGhHZP9meD0Sm8zSccrPsAsAAANABcAAAAjAAD/AQABAAsAAnUAAnIAAm8wggJrMIIBU6ADAgECAgkA7b7I9wWvJRQwDQYJKoZIhvcNAQELBQAwOzELMAkGA1UEBhMCVVMxDTALBgNVBAoMBEphdmExHTAbBgNVBAsMFFN1bkpTU0UgVGVzdCBTZXJpdmNlMB4XDTE4MDUyMjA3MTgxNloXDTI4MDUyMTA3MTgxNlowVTELMAkGA1UEBhMCVVMxDTALBgNVBAoMBEphdmExHTAbBgNVBAsMFFN1bkpTU0UgVGVzdCBTZXJpdmNlMRgwFgYDVQQDDA9SZWdyZXNzaW9uIFRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATn0wRE1OVVnV56mzxnc657lmSBB0+0P5YgTq2Pc9sgqnEY8PG980/3n0DJCMQr96FZBWsIyY2gSQjNj4ggGAHToyMwITAfBgNVHSMEGDAWgBQN3ZPJ/ku9NbfomXiQ+9taPdsVTDANBgkqhkiG9w0BAQsFAAOCAQEAU5NUSqzvTKSFmIh2sSwXVFojcHasu/BdmTna5KW2Tb9vNW97ADnvtkKjFDWkvvKeSKw98E3W5Vi0eMzVUDdEL7j5MArvx2cjui1iNagenmu+ziXpz8hs6ylLb3QiU24NTEwSsSbnDEjCP4C6TH/XLryE7IK8cEsxjZzUuyQfUwwFsO5LVoj6WcalOiL4yhg5vEZxXjny/G5Pz7DBXwWSm92WXwggWZ409G/7UT6VdsRLoJI0JVpn+UWE+B2BgqV43ux+ymLPnQMbh2pp+4QBpfnForU1ZcnFNHPRE4f8hTOLK4NbqOmGuWCK3j7c0LOnq8/2EAlDxWFyPSfXkEochAwAAHADAB0gUsYb5i8nNJxMG2c6TY4P6TgXukhGhn2EqtOMCMYP1hUEAwBIMEYCIQCZN6uqbBUVnbWEVGacgAi8Inz+vbYB+nXxzXpS7B/rHgIhALedPTzefR4lNrQ5d4V42z0VIgIAEh811usG1hg58iFmDQAA7wNAAQIAKgQDBQMGAwgHCAgIBAgFCAYICQgKCAsEAQUBBgEEAgMDAwEDAgIDAgECAgC9AD0wOzELMAkRA1UEBhMCVVMxDTALBgNVBAoMBEphdmExHTAbBgNVBAsMFFN1bkpTU0UgVGVzdCBTZXJpdmNlAD0wOzELMAkRA1UEBhMCVVMxDTALBgNVBAoMBEphdmExHTAbBgNVBAsMFFN1bkpTU0UgVGVzdCBTZXJpdmNlAD0wOzELMAkRA1UEBhMCVVMxDTALBgNVBAoMBEphdmExHTAbBgNVBAsMFFN1bkpTU0UgVGVzdCBTZXJpdmNlDgAAAA==");

    /*
     * The following is to set up the keystores.
     */
    private static final String passwd = "passphrase";

    /*
     * Main entry point for this demo.
     */
    public static void main(String[] args) throws Exception {
        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        TestBadDNForPeerCA12 test = new TestBadDNForPeerCA12();

        try {
            test.runTest();
            throw new Exception(
                "TEST FAILED:  Didn't generate any exception");
        } catch (SSLHandshakeException she) {
            System.out.println("TEST PASSED:  Caught expected exception");
        }
    }

    /*
     * Create an initialized SSLContext to use for this demo.
     */

    public TestBadDNForPeerCA12() throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = passwd.toCharArray();

        ks.load(new FileInputStream(keyStoreFile), passphrase);
        ts.load(new FileInputStream(keyStoreFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslCtx = SSLContext.getInstance(proto);

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        sslc = sslCtx;
    }


    private void runTest() throws Exception {

        createSSLEngines();
        createBuffers();

        System.out.println("forcing client hello");
        //sTOc = ByteBuffer.wrap(serverHello);
        SSLEngineResult clientResult = clientEngine.wrap(clientOut, cTOs);
        runDelegatedTasks(clientResult, clientEngine);

        cTOs.flip();

        sTOc = ByteBuffer.wrap(serverPayload);

        SSLEngineResult clientHelloResult = clientEngine.unwrap(sTOc, clientIn);
        System.out.println("client unwrap: " + clientHelloResult);
        runDelegatedTasks(clientHelloResult, clientEngine);

        sTOc.compact();
        cTOs.compact();

        SSLEngineResult clientExGen = clientEngine.wrap(clientOut, cTOs);
        runDelegatedTasks(clientExGen, clientEngine);

    }

    private void createSSLEngines() {
        clientEngine = sslc.createSSLEngine();

        clientEngine.setEnabledProtocols(new String[] {proto});
        clientEngine.setUseClientMode(true);
    }

    private void createBuffers() {
        cTOs = ByteBuffer.allocateDirect(65536);

        clientIn = ByteBuffer.allocateDirect(65536);

        clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());

        sTOc = ByteBuffer.allocateDirect(65536);
    }

    private static void runDelegatedTasks(SSLEngineResult result,
                                          SSLEngine engine) throws Exception {

        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                System.out.println("\trunning delegated task...");
                runnable.run();
            }

            HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new Exception("handshake shouldn't need additional " +
                    "tasks");
            }
            System.out.println("\tnew HandshakeStatus: " + hsStatus);
        }
    }
}
