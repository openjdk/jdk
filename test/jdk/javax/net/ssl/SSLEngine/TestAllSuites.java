/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4495742
 * @library /test/lib
 *
 * @run main/othervm/timeout=180 TestAllSuites
 *
 * @summary Add non-blocking SSL/TLS functionality, usable with any
 *      I/O abstraction
 *
 * Iterate through all the suites using both TLS and SSLv3, and turn
 * SSLv2Hello off and on.  Exchange some bytes and shutdown.
 *
 * @author Brad Wetmore
 */

import jdk.test.lib.security.SecurityUtils;

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import java.io.*;
import java.security.*;
import java.nio.*;
import java.util.*;
import java.util.Arrays;

public class TestAllSuites {

    private static final boolean DEBUG = Boolean.getBoolean("test.debug");

    private final SSLContext SSL_CONTEXT;
    private SSLEngine clientEngine;
    private SSLEngine serverEngine;

    private static final String PATH_TO_STORES = "../etc";
    private static final String KEYSTORE_FILENAME = "keystore";
    private static final String TRUSTSTORE_FILENAME = "truststore";

    private static final String KEYSTORE_PATH =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                "/" + KEYSTORE_FILENAME;
    private static final String TRUSTSTORE_PATH =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                "/" + TRUSTSTORE_FILENAME;

    private ByteBuffer clientOut;
    private ByteBuffer clientIn;
    private ByteBuffer serverOut;
    private ByteBuffer serverIn;

    private ByteBuffer clientToServer;
    private ByteBuffer serverToClient;


    private void createSSLEngines() throws Exception {
        clientEngine = SSL_CONTEXT.createSSLEngine("client", 1);
        clientEngine.setUseClientMode(true);

        serverEngine = SSL_CONTEXT.createSSLEngine("server", 2);
        serverEngine.setUseClientMode(false);
    }

    private void test() throws Exception {

        createSSLEngines();
        List<String> supportedSuites = List.of(clientEngine.getSupportedCipherSuites());

        for (SupportedCipherSuites tls : SupportedCipherSuites.values()) {
            for (String cipherSuite : tls.cipherSuites) {
                if (supportedSuites.contains(cipherSuite)) {
                    createSSLEngines();
                    runTest(cipherSuite, tls.protocol);
                } else {
                    System.out.printf("Skipping unsupported cipher suite %s with %s%n",
                            tls.protocol,
                            cipherSuite);
                }
            }
        }
    }

    private void runTest(String suite, String protocol) throws Exception {

        boolean dataDone = false;

        System.out.println("======================================");
        System.out.printf("Testing: %s with %s%n", protocol, suite);

        String [] suites = new String [] { suite };

        clientEngine.setEnabledCipherSuites(suites);
        serverEngine.setEnabledCipherSuites(suites);

        clientEngine.setEnabledProtocols(new String[]{protocol});
        serverEngine.setEnabledProtocols(new String[]{protocol});

        createBuffers();

        SSLEngineResult result1;        // ssle1's results from last operation
        SSLEngineResult result2;        // ssle2's results from last operation

        Date start = new Date();
        int counter = 0;
        while (!isEngineClosed(clientEngine) || !isEngineClosed(serverEngine)) {

            log("----------------");

            result1 = clientEngine.wrap(clientOut, clientToServer);
            result2 = serverEngine.wrap(serverOut, serverToClient);

            log("wrap1:  " + result1);
            log("clientToServer  = " + clientToServer);
            log("");

            log("wrap2:  " + result2);
            log("serverToClient  = " + serverToClient);

            runDelegatedTasks(result1, clientEngine);
            runDelegatedTasks(result2, serverEngine);

            clientToServer.flip();
            serverToClient.flip();

            log("----");

            result1 = clientEngine.unwrap(serverToClient, clientIn);
            result2 = serverEngine.unwrap(clientToServer, serverIn);

            log("unwrap1: " + result1);
            log("serverToClient  = " + serverToClient);
            log("");

            log("unwrap2: " + result2);
            log("clientToServer  = " + clientToServer);

            runDelegatedTasks(result1, clientEngine);
            runDelegatedTasks(result2, serverEngine);

            clientToServer.compact();
            serverToClient.compact();

            /*
             * If we've transfered all the data between app1 and app2,
             * we try to close and see what that gets us.
             */
            if (!dataDone && (clientOut.limit() == serverIn.position()) &&
                    (serverOut.limit() == clientIn.position())) {

                checkTransfer(clientOut, serverIn);
                checkTransfer(serverOut, clientIn);

                clientEngine.closeOutbound();
                serverEngine.closeOutbound();
                dataDone = true;
            }
        }

        /*
         * Just for grins, try closing again, make sure nothing
         * strange is happening after we're closed.
         */
        clientEngine.closeInbound();
        clientEngine.closeOutbound();

        serverEngine.closeInbound();
        serverEngine.closeOutbound();

        clientOut.rewind();
        clientIn.clear();
        clientToServer.clear();

        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(result1);

        result1 = clientEngine.unwrap(clientToServer, clientIn);
        checkResult(result1);

        System.out.println("Test Passed.");
        System.out.println("\n======================================");

        Date end = new Date();
        elapsed += end.getTime() - start.getTime();

    }

    static long elapsed = 0;

    private static void checkResult(SSLEngineResult result) throws Exception {
        if ((result.getStatus() != Status.CLOSED) ||
                (result.getHandshakeStatus() !=
                    HandshakeStatus.NOT_HANDSHAKING) ||
                (result.bytesConsumed() != 0) ||
                (result.bytesProduced() != 0)) {
            throw new Exception("Unexpected close status");
        }
    }

    public static void main(String args[]) throws Exception {
        SecurityUtils.removeFromDisabledTlsAlgs("TLSv1.1");
        TestAllSuites tas;

        tas = new TestAllSuites();

        tas.createSSLEngines();

        tas.test();

        System.out.println("All Tests Passed.");
        System.out.println("Elapsed time: " + elapsed / 1000.0);
    }

    /*
     * **********************************************************
     * Majority of the test case is above, below is just setup stuff
     * **********************************************************
     */

    public TestAllSuites() throws Exception {
        SSL_CONTEXT = getSSLContext(KEYSTORE_PATH, TRUSTSTORE_PATH);
    }

    /*
     * Create an initialized SSLContext to use for this test.
     */
    private SSLContext getSSLContext(String keyFile, String trustFile)
            throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = "passphrase".toCharArray();

        ks.load(new FileInputStream(keyFile), passphrase);
        ts.load(new FileInputStream(trustFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslCtx = SSLContext.getInstance("TLS");

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslCtx;
    }

    private void createBuffers() {
        // Size the buffers as appropriate.

        SSLSession session = clientEngine.getSession();
        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();

        clientIn = ByteBuffer.allocateDirect(appBufferMax + 50);
        serverIn = ByteBuffer.allocateDirect(appBufferMax + 50);

        clientToServer = ByteBuffer.allocateDirect(netBufferMax);
        serverToClient = ByteBuffer.allocateDirect(netBufferMax);

        clientOut = ByteBuffer.wrap("Hi Engine2, I'm SSLEngine1".getBytes());
        serverOut = ByteBuffer.wrap("Hello Engine1, I'm SSLEngine2".getBytes());

        log("ClientOut = " + clientOut);
        log("ServerOut = " + serverOut);
        log("");
    }

    private static void runDelegatedTasks(SSLEngineResult result,
            SSLEngine engine) throws Exception {

        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                log("running delegated task...");
                runnable.run();
            }
        }
    }

    private static boolean isEngineClosed(SSLEngine engine) {
        return (engine.isOutboundDone() && engine.isInboundDone());
    }

    private static void checkTransfer(ByteBuffer a, ByteBuffer b)
            throws Exception {
        a.flip();
        b.flip();

        if (!a.equals(b)) {
            throw new Exception("Data didn't transfer cleanly");
        } else {
            log("Data transferred cleanly");
        }

        a.position(a.limit());
        b.position(b.limit());
        a.limit(a.capacity());
        b.limit(b.capacity());
    }

    private static void log(String str) {
        if (DEBUG) {
            System.out.println(str);
        }
    }

    enum SupportedCipherSuites {
        TLSv11("TLSv1.1", new String []{
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_256_CBC_SHA",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_256_CBC_SHA",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
        }),

        TLSv12("TLSv1.2", new String []{
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
                "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
                "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
                "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
                "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
        }),

        TLSv13("TLSv1.3", new String[] {
                "TLS_AES_128_GCM_SHA256",
                "TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256"
        });

        final String protocol;
        final String[] cipherSuites;

        SupportedCipherSuites(String protocol, String [] supportedCipherSuites) {
            this.protocol = protocol;
            this.cipherSuites = Arrays.copyOf(supportedCipherSuites,
                    supportedCipherSuites.length);
        }
    }
}
