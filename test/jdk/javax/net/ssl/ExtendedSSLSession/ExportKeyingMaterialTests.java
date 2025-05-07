/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 8341346
 * @summary Add support for exporting TLS Keying Material
 * @library /javax/net/ssl/templates /test/lib
 * @build SSLContextTemplate
 * @run main/othervm ExportKeyingMaterialTests
 */

import java.security.Security;
import java.util.Arrays;
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import java.nio.ByteBuffer;
import java.util.Random;

import static jdk.test.lib.Asserts.*;

/**
 * A SSLEngine usage example which simplifies the presentation
 * by removing the I/O and multi-threading concerns.
 * <P>
 * The test creates two SSLEngines, simulating a client and server.
 * The "transport" layer consists two byte buffers:  think of them
 * as directly connected pipes.
 * <P>
 * Note, this is a *very* simple example: real code will be much more
 * involved.  For example, different threading and I/O models could be
 * used, transport mechanisms could close unexpectedly, and so on.
 * <P>
 * When this application runs, notice that several messages
 * (wrap/unwrap) pass before any application data is consumed or
 * produced.
 */
public class ExportKeyingMaterialTests extends SSLContextTemplate {
    protected final SSLEngine clientEngine;     // client Engine
    protected final ByteBuffer clientOut;       // write side of clientEngine
    protected final ByteBuffer clientIn;        // read side of clientEngine

    protected final SSLEngine serverEngine;     // server Engine
    protected final ByteBuffer serverOut;       // write side of serverEngine
    protected final ByteBuffer serverIn;        // read side of serverEngine

    // For data transport, this example uses local ByteBuffers.  This
    // isn't really useful, but the purpose of this example is to show
    // SSLEngine concepts, not how to do network transport.
    protected final ByteBuffer cTOs;      // "reliable" transport client->server
    protected final ByteBuffer sTOc;      // "reliable" transport server->client

    protected ExportKeyingMaterialTests(String protocol, String ciphersuite)
            throws Exception {
        serverEngine = configureServerEngine(
                createServerSSLContext().createSSLEngine());

        clientEngine = configureClientEngine(
                createClientSSLContext().createSSLEngine(),
                protocol, ciphersuite);

        // We'll assume the buffer sizes are the same
        // between client and server.
        SSLSession session = clientEngine.getSession();
        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();

        // We'll make the input buffers a bit bigger than the max needed
        // size, so that unwrap()s following a successful data transfer
        // won't generate BUFFER_OVERFLOWS.
        //
        // We'll use a mix of direct and indirect ByteBuffers for
        // tutorial purposes only.  In reality, only use direct
        // ByteBuffers when they give a clear performance enhancement.
        clientIn = ByteBuffer.allocate(appBufferMax + 50);
        serverIn = ByteBuffer.allocate(appBufferMax + 50);

        cTOs = ByteBuffer.allocateDirect(netBufferMax);
        sTOc = ByteBuffer.allocateDirect(netBufferMax);

        clientOut = createClientOutputBuffer();
        serverOut = createServerOutputBuffer();
    }

    protected ByteBuffer createServerOutputBuffer() {
        return ByteBuffer.wrap("Hello Client, I'm Server".getBytes());
    }

    //
    // Protected methods could be used to customize the test case.
    //

    protected ByteBuffer createClientOutputBuffer() {
        return ByteBuffer.wrap("Hi Server, I'm Client".getBytes());
    }

    /*
     * Configure the client side engine.
     */
    protected SSLEngine configureClientEngine(SSLEngine clientEngine,
            String protocol, String ciphersuite) {
        clientEngine.setUseClientMode(true);

        // Get/set parameters if needed
        SSLParameters paramsClient = clientEngine.getSSLParameters();
        paramsClient.setProtocols(new String[] { protocol });
        paramsClient.setCipherSuites(new String[] { ciphersuite });
        clientEngine.setSSLParameters(paramsClient);

        return clientEngine;
    }

    /*
     * Configure the server side engine.
     */
    protected SSLEngine configureServerEngine(SSLEngine serverEngine) {
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

        // Get/set parameters if needed
        //
        SSLParameters paramsServer = serverEngine.getSSLParameters();
        paramsServer.setProtocols(new String[] {
                "TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3"
        });
        serverEngine.setSSLParameters(paramsServer);

        return serverEngine;
    }

    public static void main(String[] args) throws Exception {
        // Turn off the disabled Algorithms so we can also test SSLv3/TLSv1/etc.
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        // Exercise all of the triggers which capture data
        // in the various key exchange algorithms.

        // Use appropriate protocol/ciphersuite combos for TLSv1.3
        new ExportKeyingMaterialTests(
                "TLSv1.3", "TLS_AES_128_GCM_SHA256").runTest();
        new ExportKeyingMaterialTests(
                "TLSv1.3", "TLS_AES_256_GCM_SHA384").runTest();
        new ExportKeyingMaterialTests(
                "TLSv1.3", "TLS_CHACHA20_POLY1305_SHA256").runTest();

        // Try the various GCM suites for TLSv1.2
        new ExportKeyingMaterialTests(
                "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384").runTest();
        new ExportKeyingMaterialTests(
                "TLSv1.2", "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256").runTest();
        new ExportKeyingMaterialTests(
                "TLSv1.2", "TLS_RSA_WITH_AES_256_GCM_SHA384").runTest();

        // Try one TLSv1.2/CBC suite just for grins, the triggers are the same.
        new ExportKeyingMaterialTests(
                "TLSv1.2", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256").runTest();

        // Use appropriate protocol/ciphersuite combos.  Some of the 1.2
        // suites (e.g. GCM) can't be used in earlier TLS versions.
        new ExportKeyingMaterialTests(
                "TLSv1.1", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA").runTest();
        new ExportKeyingMaterialTests(
                "TLSv1.1", "TLS_DHE_RSA_WITH_AES_256_CBC_SHA").runTest();
        new ExportKeyingMaterialTests(
                "TLSv1.1", "TLS_RSA_WITH_AES_256_CBC_SHA").runTest();

        new ExportKeyingMaterialTests(
                "TLSv1", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA").runTest();
        new ExportKeyingMaterialTests(
                "TLSv1", "TLS_DHE_RSA_WITH_AES_256_CBC_SHA").runTest();
        new ExportKeyingMaterialTests(
                "TLSv1", "TLS_RSA_WITH_AES_256_CBC_SHA").runTest();

        try {
            new ExportKeyingMaterialTests(
                    "SSLv3", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA").runTest();
            throw new Exception("SSLv3 export test should not have passed");
        } catch (SSLException e) {
            System.out.println("SSLv3 test failed as expected");
        }

        System.out.println("All tests PASSED");
    }

    //
    // Private methods that used to build the common part of the test.
    //

    private void runTest() throws Exception {
        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        boolean dataDone = false;
        while (isOpen(clientEngine) || isOpen(serverEngine)) {
            log("=================");

            // client wrap
            log("---Client Wrap---");
            clientResult = clientEngine.wrap(clientOut, cTOs);
            logEngineStatus(clientEngine, clientResult);
            runDelegatedTasks(clientEngine);

            // server wrap
            log("---Server Wrap---");
            serverResult = serverEngine.wrap(serverOut, sTOc);
            logEngineStatus(serverEngine, serverResult);
            runDelegatedTasks(serverEngine);

            cTOs.flip();
            sTOc.flip();

            // client unwrap
            log("---Client Unwrap---");
            clientResult = clientEngine.unwrap(sTOc, clientIn);
            logEngineStatus(clientEngine, clientResult);
            runDelegatedTasks(clientEngine);

            // server unwrap
            log("---Server Unwrap---");
            serverResult = serverEngine.unwrap(cTOs, serverIn);
            logEngineStatus(serverEngine, serverResult);
            runDelegatedTasks(serverEngine);

            cTOs.compact();
            sTOc.compact();

            // After we've transferred all application data between the client
            // and server, we close the clientEngine's outbound stream.
            // This generates a close_notify handshake message, which the
            // server engine receives and responds by closing itself.
            if (!dataDone && (clientOut.limit() == serverIn.position()) &&
                    (serverOut.limit() == clientIn.position())) {

                runExporterTests(
                        (ExtendedSSLSession) clientEngine.getSession(),
                        (ExtendedSSLSession) serverEngine.getSession());

                // A sanity check to ensure we got what was sent.
                checkTransfer(serverOut, clientIn);
                checkTransfer(clientOut, serverIn);

                log("\tClosing clientEngine's *OUTBOUND*...");
                clientEngine.closeOutbound();
                logEngineStatus(clientEngine);

                dataDone = true;
                log("\tClosing serverEngine's *OUTBOUND*...");
                serverEngine.closeOutbound();
                logEngineStatus(serverEngine);
            }
        }
    }

    private static void runExporterTests(
            ExtendedSSLSession clientSession,
            ExtendedSSLSession serverSession) throws Exception {

        // Create output arrays
        byte[] clientBytes, serverBytes;

        // Create various input arrays and fill with junk.
        Random random = new Random();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);

        // Slightly change 1 byte in the middle
        byte[] bytesDiff = Arrays.copyOf(bytes, bytes.length);
        bytesDiff[bytes.length/2]++;

        byte[] bytesDiffSize = new byte[21];
        random.nextBytes(bytesDiffSize);

        // Inputs exactly equal.  Use exportKeyMaterialKey()
        clientBytes = clientSession.exportKeyingMaterialKey("hello",
                bytes, 128).getEncoded();
        serverBytes = serverSession.exportKeyingMaterialKey("hello",
                bytes, 128).getEncoded();
        assertEqualsByteArray(clientBytes, serverBytes,
                "Equal inputs but exporters are not equal");
        log("Equal inputs test passed");

        // Empty label.  I don't see anything that says this is
        // forbidden.  There is some verbiage about: labels being registered
        // with IANA, must not collide with existing PRF labels, SHOULD use
        // "EXPORTER"/"EXPERIMENTAL" prefixes, etc.
        clientBytes = clientSession.exportKeyingMaterialKey("",
                bytes, 128).getEncoded();
        serverBytes = serverSession.exportKeyingMaterialKey("",
                bytes, 128).getEncoded();
        assertEqualsByteArray(clientBytes, serverBytes,
                "Empty label and exporters are equal");
        log("Empty label test passed");

        // Different labels, now use exportKeyMaterialData() for coverage
        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("goodbye",
                bytes, 128);
        assertNotEqualsByteArray(clientBytes, serverBytes,
                "Different labels but exporters same");
        log("Different labels test passed");

        // Different output sizes
        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytes, 127);
        assertEquals(clientBytes.length, 128, "client length != 128");
        assertEquals(serverBytes.length, 127, "server length != 127");
        assertNotEqualsByteArray(clientBytes, serverBytes,
                "Different output sizes but exporters same");
        log("Different output size test passed");

        // Different context values
        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytesDiff, 128);
        assertNotEqualsByteArray(clientBytes, serverBytes,
                "Different context but exporters same");
        log("Different context test passed");

        // Different context sizes
        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytesDiffSize, 128);
        assertNotEqualsByteArray(clientBytes, serverBytes,
                "Different context sizes but exporters same");
        log("Different context sizes test passed");

        // No context, but otherwise the same
        clientBytes = clientSession.exportKeyingMaterialData("hello",
                null, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                null, 128);
        assertEqualsByteArray(clientBytes, serverBytes,
                "No context and exporters are not the same");
        log("No context test passed");

        // Smaller key size
        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 40);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytes, 40);
        assertEqualsByteArray(clientBytes, serverBytes,
                "Smaller key size should be the same");
        log("Smaller key size test passed");

        // Check error conditions
        try {
            clientSession.exportKeyingMaterialData(null, bytes, 128);
            throw new Exception("null label accepted");
        } catch (NullPointerException e) {
            log("null label test passed");
        }

        try {
            clientSession.exportKeyingMaterialData("hello",
                    new byte[1<<16], 128);
            if (!clientSession.getProtocol().equals("TLSv1.3")) {
                throw new Exception("large context accepted in " +
                        "SSLv3/TLSv1/TLSv1.1/TLSv1.2");
            } else {
                log("large context test passed in TLSv1.3");
            }
        } catch (IllegalArgumentException e) {
            log("large context test passed in " +
                    "SSLv3/TLSv1/TLSv1.1/TLSv1.2");
        }

        try {
            clientSession.exportKeyingMaterialData("hello", bytes, -20);
            throw new Exception("negative length accepted");
        } catch (IllegalArgumentException e) {
            log("negative length test passed");
        }
    }

    static boolean isOpen(SSLEngine engine) {
        return (!engine.isOutboundDone() || !engine.isInboundDone());
    }

    private static void logEngineStatus(SSLEngine engine) {
        log("\tCurrent HS State: " + engine.getHandshakeStatus());
        log("\tisInboundDone() : " + engine.isInboundDone());
        log("\tisOutboundDone(): " + engine.isOutboundDone());
    }

    private static void logEngineStatus(
            SSLEngine engine, SSLEngineResult result) {
        log("\tResult Status    : " + result.getStatus());
        log("\tResult HS Status : " + result.getHandshakeStatus());
        log("\tEngine HS Status : " + engine.getHandshakeStatus());
        log("\tisInboundDone()  : " + engine.isInboundDone());
        log("\tisOutboundDone() : " + engine.isOutboundDone());
        log("\tMore Result      : " + result);
    }

    private static void log(String message) {
        System.err.println(message);
    }

    // If the result indicates that we have outstanding tasks to do,
    // go ahead and run them in this thread.
    protected static void runDelegatedTasks(SSLEngine engine) throws Exception {
        if (engine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                log("    running delegated task...");
                runnable.run();
            }
            HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new Exception(
                        "handshake shouldn't need additional tasks");
            }
            logEngineStatus(engine);
        }
    }

    // Simple check to make sure everything came across as expected.
    static void checkTransfer(ByteBuffer a, ByteBuffer b)
            throws Exception {
        a.flip();
        b.flip();

        if (!a.equals(b)) {
            throw new Exception("Data didn't transfer cleanly");
        } else {
            log("\tData transferred cleanly");
        }

        a.position(a.limit());
        b.position(b.limit());
        a.limit(a.capacity());
        b.limit(b.capacity());
    }
}
