/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

// See ../../../../RunStatReqSelect.java for the jtreg header

package sun.security.ssl;

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.nio.*;
import java.security.cert.X509Certificate;
import java.security.cert.Extension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import sun.security.provider.certpath.OCSPNonceExtension;
import sun.security.provider.certpath.ResponderId;
import sun.security.testlibrary.SimpleOCSPServer;
import sun.security.testlibrary.CertificateBuilder;

public class StatusReqSelection {

    /*
     * Enables logging of the SSLEngine operations.
     */
    private static final boolean logging = true;

    /*
     * Enables the JSSE system debugging system property:
     *
     *     -Djavax.net.debug=all
     *
     * This gives a lot of low-level information about operations underway,
     * including specific handshake messages, and might be best examined
     * after gaining some familiarity with this application.
     */
    private static final boolean debug = false;

    // The following items are used to set up the keystores.
    private static final String passwd = "passphrase";
    private static final String ROOT_ALIAS = "root";
    private static final String INT_ALIAS = "intermediate";
    private static final String SSL_ALIAS = "ssl";

    // PKI and server components we will need for this test
    private static KeyManagerFactory kmf;
    private static TrustManagerFactory tmf;
    private static KeyStore rootKeystore;       // Root CA Keystore
    private static KeyStore intKeystore;        // Intermediate CA Keystore
    private static KeyStore serverKeystore;     // SSL Server Keystore
    private static KeyStore trustStore;         // SSL Client trust store
    private static SimpleOCSPServer rootOcsp;   // Root CA OCSP Responder
    private static int rootOcspPort;            // Port for root OCSP
    private static SimpleOCSPServer intOcsp;    // Intermediate CA OCSP server
    private static int intOcspPort;             // Port for intermediate OCSP
    private static SSLContext ctxStaple;        // SSLContext for all tests

    // Some useful objects we will need for test purposes
    private static final SecureRandom RNG = new SecureRandom();

    // We'll be using these objects repeatedly to make hello messages
    private static final ProtocolVersion VER_1_0 = ProtocolVersion.TLS10;
    private static final ProtocolVersion VER_1_2 = ProtocolVersion.TLS12;
    private static final CipherSuiteList SUITES = new CipherSuiteList(
            CipherSuite.valueOf("TLS_RSA_WITH_AES_128_GCM_SHA256"));
    private static final SessionId SID = new SessionId(new byte[0]);
    private static final HelloExtension RNIEXT =
            new RenegotiationInfoExtension(new byte[0], new byte[0]);
    private static final List<SignatureAndHashAlgorithm> algList =
            new ArrayList<SignatureAndHashAlgorithm>() {{
               add(SignatureAndHashAlgorithm.valueOf(4, 1, 0));
            }};         // List with only SHA256withRSA
    private static final SignatureAlgorithmsExtension SIGALGEXT =
            new SignatureAlgorithmsExtension(algList);

    /*
     * Main entry point for this test.
     */
    public static void main(String args[]) throws Exception {
        int testsPassed = 0;

        if (debug) {
            System.setProperty("javax.net.debug", "ssl");
        }

        // All tests will have stapling enabled on the server side
        System.setProperty("jdk.tls.server.enableStatusRequestExtension",
                "true");

        // Create a single SSLContext that we can use for all tests
        ctxStaple = SSLContext.getInstance("TLS");

        // Create the PKI we will use for the test and start the OCSP servers
        createPKI();

        // Set up the KeyManagerFactory and TrustManagerFactory
        kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(serverKeystore, passwd.toCharArray());
        tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);

        List<TestCase> testList = new ArrayList<TestCase>() {{
            add(new TestCase("ClientHello: No stapling extensions",
                    makeHelloNoStaplingExts(), false, false));
            add(new TestCase("ClientHello: Default status_request only",
                    makeDefaultStatReqOnly(), true, false));
            add(new TestCase("ClientHello: Default status_request_v2 only",
                    makeDefaultStatReqV2Only(), false, true));
            add(new TestCase("ClientHello: Both status_request exts, default",
                    makeDefaultStatReqBoth(), false, true));
            add(new TestCase(
                    "ClientHello: Hello with status_request and responder IDs",
                    makeStatReqWithRid(), false, false));
            add(new TestCase(
                    "ClientHello: Hello with status_request using no " +
                    "responder IDs but provides the OCSP nonce extension",
                    makeStatReqNoRidNonce(), true, false));
            add(new TestCase("ClientHello with default status_request and " +
                    "status_request_v2 with ResponderIds",
                    makeStatReqDefV2WithRid(), true, false));
            add(new TestCase("ClientHello with default status_request and " +
                    "status_request_v2 (OCSP_MULTI with ResponderId, " +
                    "OCSP as a default request)",
                    makeStatReqDefV2MultiWithRidSingleDef(), false, true));
            add(new TestCase("ClientHello with status_request and " +
                    "status_request_v2 and all OCSPStatusRequests use " +
                    "Responder IDs",
                    makeStatReqAllWithRid(), false, false));
            add(new TestCase("ClientHello with default status_request and " +
                    "status_request_v2 that has a default OCSP item and " +
                    "multiple OCSP_MULTI items, only one is default",
                    makeHelloMultiV2andSingle(), false, true));
        }};

        // Run the client and server property tests
        for (TestCase test : testList) {
            try {
                log("*** Test: " + test.testName);
                if (runTest(test)) {
                    log("PASS: status_request: " + test.statReqEnabled +
                            ", status_request_v2: " + test.statReqV2Enabled);
                    testsPassed++;
                }
            } catch (Exception e) {
                // If we get an exception, we'll count it as a failure
                log("Test failure due to exception: " + e);
            }
            log("");
        }

        // Summary
        if (testsPassed != testList.size()) {
            throw new RuntimeException(testList.size() - testsPassed +
                    " tests failed out of " + testList.size() + " total.");
        } else {
            log("Total tests: " + testList.size() + ", all passed");
        }
    }

    private static boolean runTest(TestCase test) throws Exception {
        SSLEngineResult serverResult;

        // Create a Server SSLEngine to receive our customized ClientHello
        ctxStaple.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        SSLEngine engine = ctxStaple.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(false);

        SSLSession session = engine.getSession();
        ByteBuffer serverOut = ByteBuffer.wrap("I'm a Server".getBytes());
        ByteBuffer serverIn =
                ByteBuffer.allocate(session.getApplicationBufferSize() + 50);
        ByteBuffer sTOc =
                ByteBuffer.allocateDirect(session.getPacketBufferSize());

        // Send the ClientHello ByteBuffer in the test case
        if (debug) {
            System.out.println("Sending Client Hello:\n" +
                    dumpHexBytes(test.data));
        }

        // Consume the client hello
        serverResult = engine.unwrap(test.data, serverIn);
        if (debug) {
            log("server unwrap: ", serverResult);
        }
        if (serverResult.getStatus() != SSLEngineResult.Status.OK) {
            throw new SSLException("Server unwrap got status: " +
                    serverResult.getStatus());
        } else if (serverResult.getHandshakeStatus() !=
                SSLEngineResult.HandshakeStatus.NEED_TASK) {
             throw new SSLException("Server unwrap expected NEED_TASK, got: " +
                    serverResult.getHandshakeStatus());
        }
        runDelegatedTasks(serverResult, engine);
        if (engine.getHandshakeStatus() !=
                SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            throw new SSLException("Expected NEED_WRAP, got: " +
                    engine.getHandshakeStatus());
        }

        // Generate a TLS record with the ServerHello
        serverResult = engine.wrap(serverOut, sTOc);
        if (debug) {
            log("client wrap: ", serverResult);
        }
        if (serverResult.getStatus() != SSLEngineResult.Status.OK) {
            throw new SSLException("Client wrap got status: " +
                    serverResult.getStatus());
        }
        sTOc.flip();

        if (debug) {
            log("Server Response:\n" + dumpHexBytes(sTOc));
        }

        return checkServerHello(sTOc, test.statReqEnabled,
                test.statReqV2Enabled);
    }

    /**
     * Make a TLSv1.2 ClientHello with only RNI and no stapling extensions
     */
    private static ByteBuffer makeHelloNoStaplingExts() throws IOException {
        // Craft the ClientHello byte buffer
        HelloExtensions exts = new HelloExtensions();
        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Make a TLSv1.2 ClientHello with the RNI and Status Request extensions
     */
    private static ByteBuffer makeDefaultStatReqOnly() throws IOException {
        // Craft the ClientHello byte buffer
        HelloExtensions exts = new HelloExtensions();
        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        exts.add(new CertStatusReqExtension(StatusRequestType.OCSP,
                new OCSPStatusRequest(null, null)));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Make a TLSv1.2 ClientHello with the RNI and Status Request V2 extension
     */
    private static ByteBuffer makeDefaultStatReqV2Only() throws IOException {
        // Craft the ClientHello byte buffer
        HelloExtensions exts = new HelloExtensions();
        OCSPStatusRequest osr = new OCSPStatusRequest();
        List<CertStatusReqItemV2> itemList = new ArrayList<>(2);
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                osr));
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP, osr));

        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        exts.add(new CertStatusReqListV2Extension(itemList));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }
    /**
     * Make a TLSv1.2 ClientHello with Status Request and Status Request V2
     * extensions.
     */
    private static ByteBuffer makeDefaultStatReqBoth() throws IOException {
        // Craft the ClientHello byte buffer
        HelloExtensions exts = new HelloExtensions();
        OCSPStatusRequest osr = new OCSPStatusRequest();
        List<CertStatusReqItemV2> itemList = new ArrayList<>(2);
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                osr));
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP, osr));

        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        exts.add(new CertStatusReqExtension(StatusRequestType.OCSP,
                new OCSPStatusRequest(null, null)));
        exts.add(new CertStatusReqListV2Extension(itemList));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Make a ClientHello using a status_request that has a single
     * responder ID in it.
     */
    private static ByteBuffer makeStatReqWithRid() throws IOException {
        HelloExtensions exts = new HelloExtensions();
        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        List<ResponderId> rids = new ArrayList<ResponderId>() {{
            add(new ResponderId(new X500Principal("CN=Foo")));
        }};
        exts.add(new CertStatusReqExtension(StatusRequestType.OCSP,
                new OCSPStatusRequest(rids, null)));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Make a ClientHello using a status_request that has no
     * responder IDs but does provide the nonce extension.
     */
    private static ByteBuffer makeStatReqNoRidNonce() throws IOException {
        HelloExtensions exts = new HelloExtensions();
        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        List<Extension> ocspExts = new ArrayList<Extension>() {{
            add(new OCSPNonceExtension(16));
        }};
        exts.add(new CertStatusReqExtension(StatusRequestType.OCSP,
                new OCSPStatusRequest(null, ocspExts)));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Make a ClientHello using a default status_request and a
     * status_request_v2 that has a single responder ID in it.
     */
    private static ByteBuffer makeStatReqDefV2WithRid() throws IOException {
        HelloExtensions exts = new HelloExtensions();
        List<ResponderId> rids = new ArrayList<ResponderId>() {{
            add(new ResponderId(new X500Principal("CN=Foo")));
        }};
        List<CertStatusReqItemV2> itemList = new ArrayList<>(2);
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                new OCSPStatusRequest(rids, null)));
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP,
                new OCSPStatusRequest(rids, null)));

        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        exts.add(new CertStatusReqExtension(StatusRequestType.OCSP,
                new OCSPStatusRequest(null, null)));
        exts.add(new CertStatusReqListV2Extension(itemList));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Make a ClientHello using a default status_request and a
     * status_request_v2 that has a single responder ID in it for the
     * OCSP_MULTI request item and a default OCSP request item.
     */
    private static ByteBuffer makeStatReqDefV2MultiWithRidSingleDef()
            throws IOException {
        HelloExtensions exts = new HelloExtensions();
        List<ResponderId> rids = new ArrayList<ResponderId>() {{
            add(new ResponderId(new X500Principal("CN=Foo")));
        }};
        List<CertStatusReqItemV2> itemList = new ArrayList<>(2);
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                new OCSPStatusRequest(rids, null)));
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP,
                new OCSPStatusRequest(null, null)));

        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        exts.add(new CertStatusReqExtension(StatusRequestType.OCSP,
                new OCSPStatusRequest(null, null)));
        exts.add(new CertStatusReqListV2Extension(itemList));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Make a ClientHello using status_request and status_request_v2 where
     * all underlying OCSPStatusRequests use responder IDs.
     */
    private static ByteBuffer makeStatReqAllWithRid() throws IOException {
        HelloExtensions exts = new HelloExtensions();
        List<ResponderId> rids = new ArrayList<ResponderId>() {{
            add(new ResponderId(new X500Principal("CN=Foo")));
        }};
        List<CertStatusReqItemV2> itemList = new ArrayList<>(2);
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                new OCSPStatusRequest(rids, null)));
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP,
                new OCSPStatusRequest(rids, null)));

        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        exts.add(new CertStatusReqExtension(StatusRequestType.OCSP,
                new OCSPStatusRequest(rids, null)));
        exts.add(new CertStatusReqListV2Extension(itemList));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Make a TLSv1.2 ClientHello multiple CertStatusReqItemV2s of different
     * types.  One of the middle items should be acceptable while the others
     * have responder IDs.  The status_request (v1) should also be acceptable
     * but should be overridden in favor of the status_request_v2.
     */
    private static ByteBuffer makeHelloMultiV2andSingle() throws IOException {
        // Craft the ClientHello byte buffer
        HelloExtensions exts = new HelloExtensions();
        List<ResponderId> fooRid = Collections.singletonList(
                new ResponderId(new X500Principal("CN=Foo")));
        List<ResponderId> barRid = Collections.singletonList(
                new ResponderId(new X500Principal("CN=Bar")));
        List<CertStatusReqItemV2> itemList = new ArrayList<>();
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP,
                new OCSPStatusRequest(null, null)));
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                new OCSPStatusRequest(fooRid, null)));
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                new OCSPStatusRequest(null, null)));
        itemList.add(new CertStatusReqItemV2(StatusRequestType.OCSP_MULTI,
                new OCSPStatusRequest(barRid, null)));

        exts.add(RNIEXT);
        exts.add(SIGALGEXT);
        exts.add(new CertStatusReqExtension(StatusRequestType.OCSP,
                new OCSPStatusRequest(null, null)));
        exts.add(new CertStatusReqListV2Extension(itemList));
        return createTlsRecord(Record.ct_handshake, VER_1_2,
                createClientHelloMsg(VER_1_2, SID, SUITES, exts));
    }

    /**
     * Wrap a TLS content message into a TLS record header
     *
     * @param contentType a byte containing the content type value
     * @param pv the protocol version for this record
     * @param data a byte buffer containing the message data
     * @return
     */
    private static ByteBuffer createTlsRecord(byte contentType,
            ProtocolVersion pv, ByteBuffer data) {
        int msgLen = (data != null) ? data.limit() : 0;

        // Allocate enough space to hold the TLS record header + the message
        ByteBuffer recordBuf = ByteBuffer.allocate(msgLen + 5);
        recordBuf.put(contentType);
        recordBuf.putShort((short)pv.v);
        recordBuf.putShort((short)msgLen);
        if (msgLen > 0) {
            recordBuf.put(data);
        }

        recordBuf.flip();
        return recordBuf;
    }

    /**
     * Craft and encode a ClientHello message as a byte array.
     *
     * @param pv the protocol version asserted in the hello message.
     * @param sessId the session ID for this hello message.
     * @param suites a list consisting of one or more cipher suite objects
     * @param extensions a list of HelloExtension objects
     *
     * @return a byte array containing the encoded ClientHello message.
     */
    private static ByteBuffer createClientHelloMsg(ProtocolVersion pv,
            SessionId sessId, CipherSuiteList suites,
            HelloExtensions extensions) throws IOException {
        ByteBuffer msgBuf;

        HandshakeOutStream hsos =
                new HandshakeOutStream(new SSLEngineOutputRecord());

        // Construct the client hello object from the first 3 parameters
        HandshakeMessage.ClientHello cHello =
                new HandshakeMessage.ClientHello(RNG, pv, sessId, suites,
                        false);

        // Use the HelloExtensions provided by the caller
        if (extensions != null) {
            cHello.extensions = extensions;
        }

        cHello.send(hsos);
        msgBuf = ByteBuffer.allocate(hsos.size() + 4);

        // Combine the handshake type with the length
        msgBuf.putInt((HandshakeMessage.ht_client_hello << 24) |
                (hsos.size() & 0x00FFFFFF));
        msgBuf.put(hsos.toByteArray());
        msgBuf.flip();
        return msgBuf;
    }

    /*
     * If the result indicates that we have outstanding tasks to do,
     * go ahead and run them in this thread.
     */
    private static void runDelegatedTasks(SSLEngineResult result,
            SSLEngine engine) throws Exception {

        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                if (debug) {
                    log("\trunning delegated task...");
                }
                runnable.run();
            }
            HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new Exception(
                    "handshake shouldn't need additional tasks");
            }
            if (debug) {
                log("\tnew HandshakeStatus: " + hsStatus);
            }
        }
    }

    private static void log(String str, SSLEngineResult result) {
        if (!logging) {
            return;
        }
        HandshakeStatus hsStatus = result.getHandshakeStatus();
        log(str +
            result.getStatus() + "/" + hsStatus + ", " +
            result.bytesConsumed() + "/" + result.bytesProduced() +
            " bytes");
        if (hsStatus == HandshakeStatus.FINISHED) {
            log("\t...ready for application data");
        }
    }

    private static void log(String str) {
        if (logging) {
            System.out.println(str);
        }
    }

    /**
     * Dump a ByteBuffer as a hexdump to stdout.  The dumping routine will
     * start at the current position of the buffer and run to its limit.
     * After completing the dump, the position will be returned to its
     * starting point.
     *
     * @param data the ByteBuffer to dump to stdout.
     *
     * @return the hexdump of the byte array.
     */
    private static String dumpHexBytes(ByteBuffer data) {
        StringBuilder sb = new StringBuilder();
        if (data != null) {
            int i = 0;
            data.mark();
            while (data.hasRemaining()) {
                if (i % 16 == 0 && i != 0) {
                    sb.append("\n");
                }
                sb.append(String.format("%02X ", data.get()));
                i++;
            }
            data.reset();
        }

        return sb.toString();
    }

    /**
     * Tests the ServerHello for the presence (or not) of the status_request
     * or status_request_v2 hello extension.  It is assumed that the provided
     * ByteBuffer has its position set at the first byte of the TLS record
     * containing the ServerHello and contains the entire hello message.  Upon
     * successful completion of this method the ByteBuffer will have its
     * position reset to the initial offset in the buffer.  If an exception is
     * thrown the position at the time of the exception will be preserved.
     *
     * @param statReqPresent true if the status_request hello extension should
     * be present.
     * @param statReqV2Present true if the status_request_v2 hello extension
     * should be present.
     *
     * @return true if the ServerHello's extension set matches the presence
     *      booleans for status_request and status_request_v2.  False if
     *      not, or if the TLS record or message is of the wrong type.
     */
    private static boolean checkServerHello(ByteBuffer data,
            boolean statReqPresent, boolean statReqV2Present) {
        boolean hasV1 = false;
        boolean hasV2 = false;
        Objects.requireNonNull(data);
        int startPos = data.position();
        data.mark();

        // Process the TLS record header
        int type = Byte.toUnsignedInt(data.get());
        int ver_major = Byte.toUnsignedInt(data.get());
        int ver_minor = Byte.toUnsignedInt(data.get());
        int recLen = Short.toUnsignedInt(data.getShort());

        // Simple sanity checks
        if (type != 22) {
            log("Not a handshake: Type = " + type);
            return false;
        } else if (recLen > data.remaining()) {
            log("Incomplete record in buffer: Record length = " + recLen +
                    ", Remaining = " + data.remaining());
            return false;
        }

        // Grab the handshake message header.
        int msgHdr = data.getInt();
        int msgType = (msgHdr >> 24) & 0x000000FF;
        int msgLen = msgHdr & 0x00FFFFFF;

        // More simple sanity checks
        if (msgType != 2) {
            log("Not a ServerHello: Type = " + msgType);
            return false;
        }

        // Skip over the protocol version and server random
        data.position(data.position() + 34);

        // Jump past the session ID
        int sessLen = Byte.toUnsignedInt(data.get());
        if (sessLen != 0) {
            data.position(data.position() + sessLen);
        }

        // Skip the cipher suite and compression method
        data.position(data.position() + 3);

        // Go through the extensions and look for the request extension
        // expected by the caller.
        int extsLen = Short.toUnsignedInt(data.getShort());
        while (data.position() < recLen + startPos + 5) {
            int extType = Short.toUnsignedInt(data.getShort());
            int extLen = Short.toUnsignedInt(data.getShort());
            hasV1 |= (extType == ExtensionType.EXT_STATUS_REQUEST.id);
            hasV2 |= (extType == ExtensionType.EXT_STATUS_REQUEST_V2.id);
            data.position(data.position() + extLen);
        }

        if (hasV1 != statReqPresent) {
            log("The status_request extension is " +
                    "inconsistent with the expected result: expected = " +
                    statReqPresent + ", actual = " + hasV1);
        }
        if (hasV2 != statReqV2Present) {
            log("The status_request_v2 extension is " +
                    "inconsistent with the expected result: expected = " +
                    statReqV2Present + ", actual = " + hasV2);
        }

        // Reset the position to the initial spot at the start of this method.
        data.reset();

        return ((hasV1 == statReqPresent) && (hasV2 == statReqV2Present));
    }

    /**
     * Creates the PKI components necessary for this test, including
     * Root CA, Intermediate CA and SSL server certificates, the keystores
     * for each entity, a client trust store, and starts the OCSP responders.
     */
    private static void createPKI() throws Exception {
        CertificateBuilder cbld = new CertificateBuilder();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyStore.Builder keyStoreBuilder =
                KeyStore.Builder.newInstance("PKCS12", null,
                        new KeyStore.PasswordProtection(passwd.toCharArray()));

        // Generate Root, IntCA, EE keys
        KeyPair rootCaKP = keyGen.genKeyPair();
        log("Generated Root CA KeyPair");
        KeyPair intCaKP = keyGen.genKeyPair();
        log("Generated Intermediate CA KeyPair");
        KeyPair sslKP = keyGen.genKeyPair();
        log("Generated SSL Cert KeyPair");

        // Set up the Root CA Cert
        cbld.setSubjectName("CN=Root CA Cert, O=SomeCompany");
        cbld.setPublicKey(rootCaKP.getPublic());
        cbld.setSerialNumber(new BigInteger("1"));
        // Make a 3 year validity starting from 60 days ago
        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
        long end = start + TimeUnit.DAYS.toMillis(1085);
        cbld.setValidity(new Date(start), new Date(end));
        addCommonExts(cbld, rootCaKP.getPublic(), rootCaKP.getPublic());
        addCommonCAExts(cbld);
        // Make our Root CA Cert!
        X509Certificate rootCert = cbld.build(null, rootCaKP.getPrivate(),
                "SHA256withRSA");
        log("Root CA Created:\n" + certInfo(rootCert));

        // Now build a keystore and add the keys and cert
        rootKeystore = keyStoreBuilder.getKeyStore();
        java.security.cert.Certificate[] rootChain = {rootCert};
        rootKeystore.setKeyEntry(ROOT_ALIAS, rootCaKP.getPrivate(),
                passwd.toCharArray(), rootChain);

        // Now fire up the OCSP responder
        rootOcsp = new SimpleOCSPServer(rootKeystore, passwd, ROOT_ALIAS, null);
        rootOcsp.enableLog(debug);
        rootOcsp.setNextUpdateInterval(3600);
        rootOcsp.start();

        // Wait 5 seconds for server ready
        for (int i = 0; (i < 100 && !rootOcsp.isServerReady()); i++) {
            Thread.sleep(50);
        }
        if (!rootOcsp.isServerReady()) {
            throw new RuntimeException("Server not ready yet");
        }

        rootOcspPort = rootOcsp.getPort();
        String rootRespURI = "http://localhost:" + rootOcspPort;
        log("Root OCSP Responder URI is " + rootRespURI);

        // Now that we have the root keystore and OCSP responder we can
        // create our intermediate CA.
        cbld.reset();
        cbld.setSubjectName("CN=Intermediate CA Cert, O=SomeCompany");
        cbld.setPublicKey(intCaKP.getPublic());
        cbld.setSerialNumber(new BigInteger("100"));
        // Make a 2 year validity starting from 30 days ago
        start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        end = start + TimeUnit.DAYS.toMillis(730);
        cbld.setValidity(new Date(start), new Date(end));
        addCommonExts(cbld, intCaKP.getPublic(), rootCaKP.getPublic());
        addCommonCAExts(cbld);
        cbld.addAIAExt(Collections.singletonList(rootRespURI));
        // Make our Intermediate CA Cert!
        X509Certificate intCaCert = cbld.build(rootCert, rootCaKP.getPrivate(),
                "SHA256withRSA");
        log("Intermediate CA Created:\n" + certInfo(intCaCert));

        // Provide intermediate CA cert revocation info to the Root CA
        // OCSP responder.
        Map<BigInteger, SimpleOCSPServer.CertStatusInfo> revInfo =
            new HashMap<>();
        revInfo.put(intCaCert.getSerialNumber(),
                new SimpleOCSPServer.CertStatusInfo(
                        SimpleOCSPServer.CertStatus.CERT_STATUS_GOOD));
        rootOcsp.updateStatusDb(revInfo);

        // Now build a keystore and add the keys, chain and root cert as a TA
        intKeystore = keyStoreBuilder.getKeyStore();
        java.security.cert.Certificate[] intChain = {intCaCert, rootCert};
        intKeystore.setKeyEntry(INT_ALIAS, intCaKP.getPrivate(),
                passwd.toCharArray(), intChain);
        intKeystore.setCertificateEntry(ROOT_ALIAS, rootCert);

        // Now fire up the Intermediate CA OCSP responder
        intOcsp = new SimpleOCSPServer(intKeystore, passwd,
                INT_ALIAS, null);
        intOcsp.enableLog(debug);
        intOcsp.setNextUpdateInterval(3600);
        intOcsp.start();

        // Wait 5 seconds for server ready
        for (int i = 0; (i < 100 && !intOcsp.isServerReady()); i++) {
            Thread.sleep(50);
        }
        if (!intOcsp.isServerReady()) {
            throw new RuntimeException("Server not ready yet");
        }

        intOcspPort = intOcsp.getPort();
        String intCaRespURI = "http://localhost:" + intOcspPort;
        log("Intermediate CA OCSP Responder URI is " + intCaRespURI);

        // Last but not least, let's make our SSLCert and add it to its own
        // Keystore
        cbld.reset();
        cbld.setSubjectName("CN=SSLCertificate, O=SomeCompany");
        cbld.setPublicKey(sslKP.getPublic());
        cbld.setSerialNumber(new BigInteger("4096"));
        // Make a 1 year validity starting from 7 days ago
        start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        end = start + TimeUnit.DAYS.toMillis(365);
        cbld.setValidity(new Date(start), new Date(end));

        // Add extensions
        addCommonExts(cbld, sslKP.getPublic(), intCaKP.getPublic());
        boolean[] kuBits = {true, false, true, false, false, false,
            false, false, false};
        cbld.addKeyUsageExt(kuBits);
        List<String> ekuOids = new ArrayList<>();
        ekuOids.add("1.3.6.1.5.5.7.3.1");
        ekuOids.add("1.3.6.1.5.5.7.3.2");
        cbld.addExtendedKeyUsageExt(ekuOids);
        cbld.addSubjectAltNameDNSExt(Collections.singletonList("localhost"));
        cbld.addAIAExt(Collections.singletonList(intCaRespURI));
        // Make our SSL Server Cert!
        X509Certificate sslCert = cbld.build(intCaCert, intCaKP.getPrivate(),
                "SHA256withRSA");
        log("SSL Certificate Created:\n" + certInfo(sslCert));

        // Provide SSL server cert revocation info to the Intermeidate CA
        // OCSP responder.
        revInfo = new HashMap<>();
        revInfo.put(sslCert.getSerialNumber(),
                new SimpleOCSPServer.CertStatusInfo(
                        SimpleOCSPServer.CertStatus.CERT_STATUS_GOOD));
        intOcsp.updateStatusDb(revInfo);

        // Now build a keystore and add the keys, chain and root cert as a TA
        serverKeystore = keyStoreBuilder.getKeyStore();
        java.security.cert.Certificate[] sslChain = {sslCert, intCaCert, rootCert};
        serverKeystore.setKeyEntry(SSL_ALIAS, sslKP.getPrivate(),
                passwd.toCharArray(), sslChain);
        serverKeystore.setCertificateEntry(ROOT_ALIAS, rootCert);

        // And finally a Trust Store for the client
        trustStore = keyStoreBuilder.getKeyStore();
        trustStore.setCertificateEntry(ROOT_ALIAS, rootCert);
    }

    private static void addCommonExts(CertificateBuilder cbld,
            PublicKey subjKey, PublicKey authKey) throws IOException {
        cbld.addSubjectKeyIdExt(subjKey);
        cbld.addAuthorityKeyIdExt(authKey);
    }

    private static void addCommonCAExts(CertificateBuilder cbld)
            throws IOException {
        cbld.addBasicConstraintsExt(true, true, -1);
        // Set key usage bits for digitalSignature, keyCertSign and cRLSign
        boolean[] kuBitSettings = {true, false, false, false, false, true,
            true, false, false};
        cbld.addKeyUsageExt(kuBitSettings);
    }

    /**
     * Helper routine that dumps only a few cert fields rather than
     * the whole toString() output.
     *
     * @param cert an X509Certificate to be displayed
     *
     * @return the String output of the issuer, subject and
     * serial number
     */
    private static String certInfo(X509Certificate cert) {
        StringBuilder sb = new StringBuilder();
        sb.append("Issuer: ").append(cert.getIssuerX500Principal()).
                append("\n");
        sb.append("Subject: ").append(cert.getSubjectX500Principal()).
                append("\n");
        sb.append("Serial: ").append(cert.getSerialNumber()).append("\n");
        return sb.toString();
    }

    private static class TestCase {
        public final String testName;
        public final ByteBuffer data;
        public final boolean statReqEnabled;
        public final boolean statReqV2Enabled;

        TestCase(String name, ByteBuffer buffer, boolean srEn, boolean srv2En) {
            testName = (name != null) ? name : "";
            data = Objects.requireNonNull(buffer,
                    "TestCase requires a non-null ByteBuffer");
            statReqEnabled = srEn;
            statReqV2Enabled = srv2En;
        }
    }
}
