/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm/timeout=180 TestAllSuites TLSv1.1
 * @run main/othervm/timeout=180 TestAllSuites TLSv1.2
 * @run main/othervm/timeout=180 TestAllSuites TLSv1.3
 *
 * @summary Add non-blocking SSL/TLS functionality, usable with any
 *      I/O abstraction
 *
 * Iterate through all the suites, exchange some bytes and shutdown.
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
    private final String PROTOCOL;
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


    private void createSSLEngines() {
        clientEngine = SSL_CONTEXT.createSSLEngine("client", 1);
        clientEngine.setUseClientMode(true);

        serverEngine = SSL_CONTEXT.createSSLEngine("server", 2);
        serverEngine.setUseClientMode(false);

        clientEngine.setEnabledProtocols(new String[]{PROTOCOL});
        serverEngine.setEnabledProtocols(new String[]{PROTOCOL});
    }

    private void test() throws Exception {
        String [] suites = clientEngine.getEnabledCipherSuites();
        System.out.println("Enabled cipher suites for protocol " + PROTOCOL +
                ": " + Arrays.toString(suites));
        for (String suite: suites){
            // Need to recreate engines to override enabled ciphers
            createSSLEngines();
            runTest(suite);
        }
    }

    private void runTest(String suite) throws Exception {

        boolean dataDone = false;

        System.out.println("======================================");
        System.out.printf("Testing: %s with %s%n", PROTOCOL, suite);

        String [] suites = new String [] { suite };

        if(suite.equals("TLS_EMPTY_RENEGOTIATION_INFO_SCSV")) {
            System.out.println("Ignoring SCSV suite");
            return;
        }

        clientEngine.setEnabledCipherSuites(suites);
        serverEngine.setEnabledCipherSuites(suites);

        createBuffers();

        SSLEngineResult clientResult;        // clientEngine's results from last operation
        SSLEngineResult serverResult;        // serverEngine's results from last operation

        Date start = new Date();
        while (!isEngineClosed(clientEngine) || !isEngineClosed(serverEngine)) {

            log("----------------");

            clientResult = clientEngine.wrap(clientOut, clientToServer);
            serverResult = serverEngine.wrap(serverOut, serverToClient);

            log("Client engine wrap result:  " + clientResult);
            log("clientToServer  = " + clientToServer);
            log("");

            log("Server engine wrap result:  " + serverResult);
            log("serverToClient  = " + serverToClient);

            runDelegatedTasks(clientResult, clientEngine);
            runDelegatedTasks(serverResult, serverEngine);

            clientToServer.flip();
            serverToClient.flip();

            log("----");

            clientResult = clientEngine.unwrap(serverToClient, clientIn);
            serverResult = serverEngine.unwrap(clientToServer, serverIn);

            log("Client engine unrap result: " + clientResult);
            log("serverToClient  = " + serverToClient);
            log("");

            log("Server engine unwrap result: " + serverResult);
            log("clientToServer  = " + clientToServer);

            runDelegatedTasks(clientResult, clientEngine);
            runDelegatedTasks(serverResult, serverEngine);

            clientToServer.compact();
            serverToClient.compact();

            /*
             * If we've transferred all the data between client and server
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

        System.out.println("Negotiated protocol: " + clientEngine.getSession().getProtocol());
        System.out.println("Negotiated cipher: " + clientEngine.getSession().getCipherSuite());

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

        clientResult = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientResult);

        clientResult = clientEngine.unwrap(clientToServer, clientIn);
        checkResult(clientResult);

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

        if (args.length < 1) {
            throw new RuntimeException("Missing TLS protocol parameter.");
        }

        switch(args[0]) {
            case "TLSv1.1" -> SecurityUtils.removeFromDisabledTlsAlgs("TLSv1.1");
            case "TLSv1.3" -> SecurityUtils.addToDisabledTlsAlgs("TLSv1.2");
        }

        TestAllSuites testAllSuites = new TestAllSuites(args[0]);
        testAllSuites.createSSLEngines();
        testAllSuites.test();

        System.out.println("All Tests Passed.");
        System.out.println("Elapsed time: " + elapsed / 1000.0);
    }

    /*
     * **********************************************************
     * Majority of the test case is above, below is just setup stuff
     * **********************************************************
     */

    public TestAllSuites(String protocol) throws Exception {
        PROTOCOL = protocol;
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

        SSLContext sslCtx = SSLContext.getInstance(PROTOCOL);

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

        clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());
        serverOut = ByteBuffer.wrap("Hello Client, I'm Server".getBytes());

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
}
