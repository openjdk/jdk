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
 * @library /javax/net/ssl/templates
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
        "FgMDBSYCAABVAwPU0IrHPvJuIvTO6/Y+FaKcEJQdaMtrQJqC4jWJ9gnUsyCTfLM7CCg8lCjgTKFBrf44AEvb5HXOmW56ssFpmgHKbMAsAAANABcAAAAjAAD/AQABAAsAAfYAAfMAAfAwggHsMIIBj6ADAgECAgRGZGRDMAwGCCqGSM49BAMCBQAwajELMAkGA1UEBhMCVVMxCzAJBgNVBAgTAkNBMRIwEAYDVQQHEwlDdXBlcnRpbm8xDjAMBgNVBAoTBUR1bW15MQ4wDAYDVQQLEwVEdW1teTEaMBgGA1UEAxMRZHVtbXkuZXhhbXBsZS5jb20wHhcNMTgwMzI3MjI0MTMxWhcNMjgwMzI2MjI0MTMxWjBqMQswCQYDVQQGEwJVUzELMAkGA1UECBMCQ0ExEjAQBgNVBAcTCUN1cGVydGlubzEOMAwGA1UEChMFRHVtbXkxDjAMBgNVBAsTBUR1bW15MRowGAYDVQQDExFkdW1teS5leGFtcGxlLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABI0sz/qT0Es+i5F3Ae3czRsbFsMntuLUnntoOGWqLAEPPsLM4GEEDFNzWrlGxBrPsILKArunM5jrkqsfEc1VqfyjITAfMB0GA1UdDgQWBBQLzGwgL76ANOYop3WzQ3XjT9ulQTAMBggqhkjOPQQDAgUAA0kAMEYCIQC9nJbGueD7SkKrJmGQLNE4mFjB4wJKRT8AnWoH5BltQAIhAMibIWGQmR1iIAcrdmho9vU6YV9y7Oh6gPeFzGkfYeJnDAAAbwMAHSA/fEXlxdJD/2SshJYmnuInis+G7Rl2syMQ3yFunfm7dQQDAEcwRQIgEef2rIOJK6G/JsM5CwRzANMUhlzqm9IzwtARgMhFpc8CIQDq5Rk317feUvqAgCJ+h9MT20ljXou9SBk3YjA9tQ89EA0AAlgDQAECACoEAwUDBgMIBwgICAQIBQgGCAkICggLBAEFAQYBBAIDAwMBAwICAwIBAgICJgBsMGoxCzAJBgNVBAYTAlVTMQswCQYDVQQIEwJDQTESMBAGA1UEBxMJQ3VwZXJ0aW5vMQ4wDAYDVQQKEwVEdW1teTEOMAwGA1UECxMFRHVtbXkxGjAYBgNVBAMTEWR1bW15LmV4YW1wbGUuY29tAGwwajELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRIwEAYDVQQHDAlDdXBlcnRpbm8xDjAMBgNVBAoMBUR1bW15MQ4wDAYDVQQLDAVEdW1teTEaMBgGA1UEAwwRZHVtbXkuZXhhbXBsZS5jb20AbDBqMQswCQYDVQQGEwJVUzELMAkGA1UECAwCQ0ExEjAQBgNVBAcMCUN1cGVydGlubzEOMAwGA1UECgwFRHVtbXkxDjAMBgNVBAsMBUR1bW15MRowGAYDVQQDDBFkdW1teS5leGFtcGxlLmNvbQBsMGoxCzAJBgNVBAYTAlVTMQswCQYDVQQIEwJDQTESMBAGA1UEBxMJQ3VwZXJ0aW5vMQ4wDAYDVQQKEwVEdW1teTEOMAwGA1UECxMFRHVtbXkxGjAYBgNVBAMTEWR1bW15LmV4YW1wbGUuY29tAGwwajELMAkGA1UEBhMCVVMxCzAJBgNVBAgTAkNBMRIwEAYDVQQHEwlDdXBlcnRpbm8xDjAMBgNVBAoTBUR1bW15MQ4wDAYDVQQLEwVEdW1teTEaMBgGA1UEAxMRZHVtbXkuZXhhbXBsZS5jb20OAAAA");

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
        (serverContents));
         */


        System.out.println("sending client hello");
        SSLEngineResult clientResult = clientEngine.wrap(clientOut, cTOs);
        runDelegatedTasks(clientResult, clientEngine);

        cTOs.flip();

        sTOc = ByteBuffer.wrap(serverPayload);

        SSLEngineResult clientHelloResult = clientEngine.unwrap(sTOc, clientIn);
        System.out.println("client unwrap: " + clientHelloResult);
        runDelegatedTasks(clientHelloResult, clientEngine);

        //sTOc.compact();
        //cTOs.compact();

        SSLEngineResult clientExGen = clientEngine.wrap(clientIn, cTOs);
        runDelegatedTasks(clientExGen, clientEngine);

    }

    private void createSSLEngines() {
        clientEngine = sslc.createSSLEngine();
        clientEngine.setEnabledProtocols(new String[] {proto});
        clientEngine.setUseClientMode(true);

        serverEngine = sslc.createSSLEngine();
        serverEngine.setEnabledProtocols(new String[] {proto});
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);
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
