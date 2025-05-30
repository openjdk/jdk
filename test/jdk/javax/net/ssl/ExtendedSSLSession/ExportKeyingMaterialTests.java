/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /javax/net/ssl/templates /test/lib /test/jdk/sun/security/pkcs11
 * @build SSLEngineTemplate
 * @run main/othervm ExportKeyingMaterialTests
 * @run main/othervm ExportKeyingMaterialTests PKCS11
 */

import java.security.Security;
import java.util.Arrays;
import javax.crypto.SecretKey;
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
public class ExportKeyingMaterialTests extends SSLEngineTemplate {

    private String protocol;
    private String ciphersuite;

    protected ExportKeyingMaterialTests(String protocol, String ciphersuite)
            throws Exception {
        super();
        this.protocol = protocol;
        this.ciphersuite = ciphersuite;
    }

    /*
     * Configure the engines.
     */
    private void configureEngines(SSLEngine clientEngine,
            SSLEngine serverEngine) {

        clientEngine.setUseClientMode(true);

        // Get/set parameters if needed
        SSLParameters paramsClient = clientEngine.getSSLParameters();
        paramsClient.setProtocols(new String[] { protocol });
        paramsClient.setCipherSuites(new String[] { ciphersuite });
        clientEngine.setSSLParameters(paramsClient);

        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

        // Get/set parameters if needed
        //
        SSLParameters paramsServer = serverEngine.getSSLParameters();
        paramsServer.setProtocols(new String[] {
                "TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3"
        });
        serverEngine.setSSLParameters(paramsServer);
    }

    public static void main(String[] args) throws Exception {
        // Turn off the disabled Algorithms so we can also test SSLv3/TLSv1/etc.
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        if ((args.length > 0) && (args[0].equals("PKCS11"))) {
            Security.insertProviderAt(
                    PKCS11Test.getSunPKCS11(PKCS11Test.getNssConfig()), 1);
        }

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

        configureEngines(clientEngine, serverEngine);

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

        SecretKey clientKey, serverKey;

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

        // Run a bunch of similar derivations using both the Key/Data methods,
        // exercising the various valid/invalid combinations.

        // We may need to adjust if it turns out that this is run with
        // non-extractable keys if .equals() doesn't work.

        // Inputs exactly equal.
        clientKey = clientSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 128);
        serverKey = serverSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 128);
        assertEquals(clientKey, serverKey,
                "Key: Equal inputs but exporters are not equal");
        log("Key: Equal inputs test passed");

        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytes, 128);
        assertEqualsByteArray(clientBytes, serverBytes,
                "Data: Equal inputs but exporters are not equal");
        log("Data: Equal inputs test passed");

        // Different labels, now use exportKeyingMaterialData() for coverage
        clientKey = clientSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 128);
        serverKey = serverSession.exportKeyingMaterialKey(
                "Generic", "goodbye", bytes, 128);
        assertNotEquals(clientKey, serverKey,
                "Key: Different labels but exporters same");
        log("Key: Different labels test passed");

        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("goodbye",
                bytes, 128);
        assertNotEqualsByteArray(clientBytes, serverBytes,
                "Data: Different labels but exporters same");
        log("Data: Different labels test passed");

        // Different output sizes
        clientKey = clientSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 128);
        serverKey = serverSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 127);
        assertNotEquals(clientKey, serverKey,
                "Key: Different output sizes but exporters same");
        log("Key: Different output size test passed");

        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytes, 127);
        assertEquals(clientBytes.length, 128, "client length != 128");
        assertEquals(serverBytes.length, 127, "server length != 127");
        assertNotEqualsByteArray(clientBytes, serverBytes,
                "Data: Different output sizes but exporters same");
        log("Data: Different output size test passed");

        // Different context values
        clientKey = clientSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 128);
        serverKey = serverSession.exportKeyingMaterialKey(
                "Generic", "hello", bytesDiff, 128);
        assertNotEquals(clientKey, serverKey,
                "Key: Different context but exporters same");
        log("Key: Different context test passed");

        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytesDiff, 128);
        assertNotEqualsByteArray(clientBytes, serverBytes,
                "Data: Different context but exporters same");
        log("Data: Different context test passed");

        // Different context sizes
        clientKey = clientSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 128);
        serverKey = serverSession.exportKeyingMaterialKey(
                "Generic", "hello", bytesDiffSize, 128);
        assertNotEquals(clientKey, serverKey,
                "Key: Different context sizes but exporters same");
        log("Key: Different context sizes test passed");

        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytesDiffSize, 128);
        assertNotEqualsByteArray(clientBytes, serverBytes,
                "Data: Different context sizes but exporters same");
        log("Data: Different context sizes test passed");

        // No context, but otherwise the same
        clientKey = clientSession.exportKeyingMaterialKey(
                "Generic", "hello", null, 128);
        serverKey = serverSession.exportKeyingMaterialKey(
                "Generic", "hello", null, 128);
        assertEquals(clientKey, serverKey,
                "Key: No context and exporters are not the same");
        log("Key: No context test passed");

        clientBytes = clientSession.exportKeyingMaterialData("hello",
                null, 128);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                null, 128);
        assertEqualsByteArray(clientBytes, serverBytes,
                "Data: No context and exporters are not the same");
        log("Data: No context test passed");

        // Smaller key size
        clientKey = clientSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 5);
        serverKey = serverSession.exportKeyingMaterialKey(
                "Generic", "hello", bytes, 5);
        assertEquals(clientKey, serverKey,
                "Key: Smaller key size should be the same");
        log("Key: Smaller key size test passed");

        clientBytes = clientSession.exportKeyingMaterialData("hello",
                bytes, 5);
        serverBytes = serverSession.exportKeyingMaterialData("hello",
                bytes, 5);
        assertEqualsByteArray(clientBytes, serverBytes,
                "Data: Smaller key size should be the same");
        log("Data: Smaller key size test passed");

        // Check error conditions

        try {
            clientSession.exportKeyingMaterialKey(null, "hello", bytes, 32);
            throw new Exception("null keyAlg accepted");
        } catch (NullPointerException e) {
            log("null keyAlg test passed");
        }

        try {
            clientSession.exportKeyingMaterialKey("", "hello", bytes, 32);
            throw new Exception("empty keyAlg accepted");
        } catch (IllegalArgumentException e) {
            log("empty keyAlg test passed");
        }

        try {
            clientSession.exportKeyingMaterialData("hello", bytes, -1);
            throw new Exception("negative length accepted");
        } catch (IllegalArgumentException e) {
            log("negative length test passed");
        }

        try {
            clientSession.exportKeyingMaterialData("hello", bytes, 0);
            throw new Exception("zero length accepted");
        } catch (IllegalArgumentException e) {
            log("zero length test passed");
        }

        try {
            clientSession.exportKeyingMaterialData(null, bytes, 128);
            throw new Exception("null label accepted");
        } catch (NullPointerException e) {
            log("null label test passed");
        }

        try {
            clientSession.exportKeyingMaterialData("", bytes, 128);
            throw new Exception("empty label accepted");
        } catch (IllegalArgumentException e) {
            log("empty label test passed");
        }

        switch (clientSession.getProtocol()) {

        case "TLSv1.3":
            // 249 bytes is the max label we can accept (<7..255>, since
            // "tls13 " is added in HkdfLabel)
            String longString =
                    "12345678901234567890123456789012345678901234567890" +
                    "12345678901234567890123456789012345678901234567890" +
                    "12345678901234567890123456789012345678901234567890" +
                    "12345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789";

            clientSession.exportKeyingMaterialData(longString, bytes, 128);
            log("large label test passed in TLSv1.3");

            try {
                clientSession.exportKeyingMaterialData(
                        longString + "0", bytes, 128);
                throw new Exception("too large label accepted in TLSv1.3");
            } catch (IllegalArgumentException e) {
                log("too large label test passed in TLSv1.3");
            }

            // 255 bytes is the max context we can accept (<0..255>)
            clientSession.exportKeyingMaterialData(
                    longString, new byte[255], 128);
            log("large context test passed in TLSv1.3");

            try {
                clientSession.exportKeyingMaterialData(
                        longString, new byte[256], 128);
                throw new Exception("too large context accepted in TLSv1.3");
            } catch (IllegalArgumentException e) {
                log("too large context test passed in TLSv1.3");
            }

            // RFC 5869 says 255*HashLen bytes is the max length we can accept.
            // So we'll choose something a bit bigger than the largest
            // hashLen/ciphertext which is 384 (48 bytes) so this will always
            // fail.
            try {
                clientSession.exportKeyingMaterialData(
                        longString, new byte[256], 12240);
                throw new Exception("too large length accepted in TLSv1.3");
            } catch (IllegalArgumentException e) {
                log("too large length test passed in TLSv1.3");
            }

            // Do a null/byte[0] comparison.  Exporters should be the same.
            clientBytes = clientSession.exportKeyingMaterialData("hello",
                    null, 128);
            serverBytes = serverSession.exportKeyingMaterialData("hello",
                    new byte[0], 128);
            assertEqualsByteArray(clientBytes, serverBytes,
                    "Data: null vs. empty context should be the same");

            break;

        case "TLSv1":
        case "TLSv1.1":
        case "TLSv1.2":
            // Don't see a limit of the label.length or output length.

            // Check for large context.length
            try {
                clientSession.exportKeyingMaterialData("hello",
                        new byte[1 << 16], 128);
                throw new Exception("large context accepted in " +
                        "TLSv1/TLSv1.1/TLSv1.2");
            } catch (IllegalArgumentException e) {
                log("large context passed in TLSv1/TLSv1.1/TLSv1.2");
            }

            // Do a null/byte[0] comparison.  Should NOT be the same.
            clientBytes = clientSession.exportKeyingMaterialData(
                    "hello", null, 128);
            serverBytes = serverSession.exportKeyingMaterialData(
                    "hello", new byte[0], 128);
            assertNotEqualsByteArray(clientBytes, serverBytes,
                    "empty vs. null context but exporters same");
            log("Data: empty vs. null context, " +
                    "different exporters test passed");

            break;

        default:
            throw new RuntimeException("Unknown protocol: " + clientSession.getProtocol());
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
