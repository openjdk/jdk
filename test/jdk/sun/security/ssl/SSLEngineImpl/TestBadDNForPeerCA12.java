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
 * @run main/othervm -Djavax.net.debug=all TestBadDNForPeerCA12
 */

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.Base64;

public class TestBadDNForPeerCA12 {

    // Test was originally written for TLSv1.2
    private static final String proto = "TLSv1.2";

    private final SSLContext sslc;

    protected SSLEngine clientEngine;     // client Engine
    protected SSLEngine serverEngine;     // server Engine
    protected ByteBuffer clientOut;       // write side of clientEngine
    protected ByteBuffer serverOut;       // write side of serverEngine
    protected ByteBuffer clientIn;        // read side of clientEngine
    protected ByteBuffer serverIn;        // read side of serverEngine
    private ByteBuffer cTOs;            // "reliable" transport client->server
    protected ByteBuffer sTOc;          // "reliable" transport server->client

    private static final String keyStoreFile =
        System.getProperty("test.src", "./")
            + "/../../../../javax/net/ssl/etc/keystore";

    // this contains a server response with invalid DNs
    private static final byte[] serverPayload = Base64.getDecoder().decode(
        "FgMDBhICAABVAwPORrwPxSL0DOnCC+cCvQcXxeU1ugjN5XyT0r9qOrlT0iD4I0BgFq"
        + "2Hbt7a9cGreNkhniEEhgQIuxa2Ur21VJr9/AA1AAANABcAAAAjAAD/AQABAAsAA1UAA1"
        + "IAA08wggNLMIICMwIEVzmbhzANBgkqhkiG9w0BAQsFADBqMQswCQYDVQQGEwJVUzELMA"
        + "kGA1UECAwCQ0ExEjAQBgNVBAcMCUN1cGVydGlubzEOMAwGA1UECgwFRHVtbXkxDjAMBg"
        + "NVBAsMBUR1bW15MRowGAYDVQQDDBFkdW1teS5leGFtcGxlLmNvbTAeFw0xNjA1MTYxMD"
        + "A2MzhaFw0yNjA1MTYxMDA2MzhaMGoxCzAJBgNVBAYTAlVTMQswCQYDVQQIDAJDQTESMB"
        + "AGA1UEBwwJQ3VwZXJ0aW5vMQ4wDAYDVQQKDAVEdW1teTEOMAwGA1UECwwFRHVtbXkxGj"
        + "AYBgNVBAMMEWR1bW15LmV4YW1wbGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMI"
        + "IBCgKCAQEAyRtAPlvIbvGfI5ZXN4jBu0dU96b8smVcAdxYnDPylnvmsYGdmYC2C6ddT7"
        + "7I9Nlk6BhNmkz6pCGsXLZnUOL+9XOGVWlw5kHDVEGUjeza5BhpZW0G0q00QthZcRuF/F"
        + "UkUGzmUuaxgm59VqwxP7dfMERG4gRRXjclMpLm23CShWBhFfooOsiPSFgDtmY4H/LkTU"
        + "EbaYuxKRfRKhMKm6GBjCVY7iS9iga728dJ+6BTNAGpKITXI35B+Xf7vpTbc+Zub9vL2f"
        + "czcChQvGTZedCaAFi3NWJXR/UTeuv/vte8jJ1YscHSSi2k0P5k3gi9PCmve/sjLrBuh+"
        + "D466e/B/swowIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQBZFaKJtN/1RkCVev7ZmYEwww"
        + "42kE5RpJt7Es2zoxqEGaNx0TA5D6XnEB1XjFUQOgOG7SbUl4NfLpJejuZiQzaX27+7Pu"
        + "1FK24SIz61sINpyVtb8flA52mIjH26HzpwSAGmTjFQ7m9Josj/25IqAaRM0AWuPLcwTf"
        + "B9zRx3me1LxxrzGhtyZDn1Jhlv0aLS79g33Kuj1HAYMvw7UGan372ufmGiv+g5UYeVvP"
        + "Yw3jeahJkSIh96Bb05aJpaogaoE5e+gQanR7E36WGGaicjfN1gIHSOyzZBibcTUhaplS"
        + "Q06DfK6UjGmHcVi8X5wD+9NWWiGrlUHcOwKueQOaptTaaXDQACWANAAQIAKgQDBQMGAw"
        + "gHCAgIBAgFCAYICQgKCAsEAQUBBgEEAgMDAwEDAgIDAgECAgImAGwwajELMAkRA1UEBh"
        + "MCVVMxCzAJBgNVBAgTAkNBMRIwEAYDVQQHEwlDdXBlcnRpbm8xDjAMBgNVBAoTBUR1bW"
        + "15MQ4wDAYDVQQLEwVEdW1teTEaMBgGA1UEAxMRZHVtbXkuZXhhbXBsZS5jb20AbDBqMQ"
        + "swCREDVQQGEwJVUzELMAkGA1UECAwCQ0ExEjAQBgNVBAcMCUN1cGVydGlubzEOMAwGA1"
        + "UECgwFRHVtbXkxDjAMBgNVBAsMBUR1bW15MRowGAYDVQQDDBFkdW1teS5leGFtcGxlLm"
        + "NvbQBsMGoxCzAJEQNVBAYTAlVTMQswCQYDVQQIDAJDQTESMBAGA1UEBwwJQ3VwZXJ0aW"
        + "5vMQ4wDAYDVQQKDAVEdW1teTEOMAwGA1UECwwFRHVtbXkxGjAYBgNVBAMMEWR1bW15Lm"
        + "V4YW1wbGUuY29tAGwwajELMAkRA1UEBhMCVVMxCzAJBgNVBAgTAkNBMRIwEAYDVQQHEw"
        + "lDdXBlcnRpbm8xDjAMBgNVBAoTBUR1bW15MQ4wDAYDVQQLEwVEdW1teTEaMBgGA1UEAx"
        + "MRZHVtbXkuZXhhbXBsZS5jb20AbDBqMQswCREDVQQGEwJVUzELMAkGA1UECBMCQ0ExEj"
        + "AQBgNVBAcTCUN1cGVydGlubzEOMAwGA1UEChMFRHVtbXkxDjAMBgNVBAsTBUR1bW15MR"
        + "owGAYDVQQDExFkdW1teS5leGFtcGxlLmNvbQ4AAAA="
    );

    /*
     * The following is to set up the keystores.
     */
    private static final String passwd = "passphrase";

    /*
     * Main entry point for this demo.
     */
    public static void main(String[] args) throws Exception {

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

        char[] passphrase = passwd.toCharArray();

        KeyStore ks = KeyStore.getInstance(new File(keyStoreFile), passphrase);
        KeyStore ts = KeyStore.getInstance(new File(keyStoreFile), passphrase);

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

        /*
         * the following was used to generate the serverPayload value
         */
        // ignore output
        /*SSLEngineResult clientResult = clientEngine.wrap(clientOut, cTOs);
        runDelegatedTasks(clientResult, clientEngine);
        cTOs.flip();

        // ignore output
        SSLEngineResult serverResult = serverEngine.unwrap(cTOs, serverIn);
        runDelegatedTasks(serverResult, serverEngine);
        // server hello, cert material, etc
        SSLEngineResult serverWrapResult = serverEngine.wrap(serverOut, sTOc);
        runDelegatedTasks(serverWrapResult, serverEngine);
        sTOc.flip();
        ByteBuffer sTOcBuff = sTOc.asReadOnlyBuffer();
        byte[] serverContents = new byte[sTOcBuff.remaining()];
        sTOcBuff.get(serverContents);
        System.out.println("sw: " + Base64.getEncoder().encodeToString
        (serverContents));*/

        System.out.println("sending client hello");
        SSLEngineResult clientResult = clientEngine.wrap(clientOut, cTOs);
        runDelegatedTasks(clientResult, clientEngine);

        cTOs.flip();

        sTOc = ByteBuffer.wrap(serverPayload);

        SSLEngineResult clientHelloResult = clientEngine.unwrap(sTOc, clientIn);
        System.out.println("client unwrap: " + clientHelloResult);
        runDelegatedTasks(clientHelloResult, clientEngine);

        SSLEngineResult clientExGen = clientEngine.wrap(clientIn, cTOs);
        runDelegatedTasks(clientExGen, clientEngine);

    }

    private void createSSLEngines() {
        clientEngine = sslc.createSSLEngine();
        clientEngine.setEnabledProtocols(new String[] {proto});
        clientEngine.setUseClientMode(true);
        clientEngine.setEnabledCipherSuites(new String[]
            {"TLS_RSA_WITH_AES_256_CBC_SHA"});

        serverEngine = sslc.createSSLEngine();
        serverEngine.setEnabledProtocols(new String[] {proto});
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);
        serverEngine.setEnabledCipherSuites(new String[]
            {"TLS_RSA_WITH_AES_256_CBC_SHA"});
    }

    private void createBuffers() {
        cTOs = ByteBuffer.allocateDirect(65536);

        clientIn = ByteBuffer.allocateDirect(65536);

        clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());

        sTOc = ByteBuffer.allocateDirect(65536);

        serverOut = ByteBuffer.wrap("Hi Client, I'm Server".getBytes());

        serverIn = ByteBuffer.allocateDirect(65536);
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
