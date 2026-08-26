/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/*
 * @test
 * @bug 8388519
 * @summary Verify that ExtendedSSLSession reports the negotiated named group.
 *          Verify that SSLEngine reports the supported named groups.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @build NamedGroupTestData
 * @run main SSLEngineNegotiatedSupportedNamedGroup
 */

public class SSLEngineNegotiatedSupportedNamedGroup extends SSLEngineTemplate {
    private static String protocol;

    private final String[] inputNamedGroups;
    private final String negotiatedNamedGroup;

    protected SSLEngineNegotiatedSupportedNamedGroup(String[] inputNamedGroups,
            String negotiatedNamedGroup) throws Exception {
        this.inputNamedGroups = inputNamedGroups;
        this.negotiatedNamedGroup = negotiatedNamedGroup;
    }

    @Override
    protected ContextParameters getClientContextParameters() {
        return new ContextParameters(protocol, "PKIX", "SunX509");
    }

    @Override
    protected ContextParameters getServerContextParameters() {
        return new ContextParameters(protocol, "PKIX", "SunX509");
    }

    public static void main(String[] args) throws Exception {
        // Run through test values
        for (String[] v : NamedGroupTestData.TEST_VALUES) {
            System.out.println("Running with test parameters: "
                    + Arrays.toString(v));
            protocol = v[2];
            new SSLEngineNegotiatedSupportedNamedGroup(
                    v[0] != null ? v[0].split(",") : null,
                    v[1]).run();
        }
    }

    private void run() throws Exception {
        for (SSLEngine engine : new SSLEngine[]{serverEngine, clientEngine}) {
            SSLParameters params = engine.getSSLParameters();
            if (protocol.endsWith("v1.2")) {
                params.setCipherSuites(new String[]{
                        NamedGroupTestData.TLS12_CIPHER_SUITE});
            }
            params.setProtocols(new String[]{protocol});
            params.setNamedGroups(inputNamedGroups);
            engine.setSSLParameters(params);
        }

        // Complete handshake.
        initialSession(clientEngine, serverEngine);

        checkNamedGroup(serverEngine);
        checkNamedGroup(clientEngine);
    }

    private void checkNamedGroup(SSLEngine engine) {
        // Check SSLEngine.getSupportedNamedGroups() call
        assertTrue(Arrays.equals(getDefaultSupportedGroups(),
                engine.getSupportedNamedGroups()), "Expected: "
                + Arrays.toString(getDefaultSupportedGroups())
                + "; Received: "
                + Arrays.toString(engine.getSupportedNamedGroups()));

        // Check ExtendedSSLSession.getNegotiatedNamedGroup() call
        ExtendedSSLSession session =
                (ExtendedSSLSession) engine.getSession();
        assertEquals(negotiatedNamedGroup, session.getNegotiatedNamedGroup());
    }

    private static String[] getDefaultSupportedGroups() {
        if (protocol.equals("DTLSv1.2")) {
            return NamedGroupTestData.DTLS12_SUPPORTED_NG;
        } else {
            return NamedGroupTestData.DEFAULT_SUPPORTED_NG;
        }
    }

    // Works for both TLS and DTLS.
    private static void initialSession(SSLEngine clientEngine,
            SSLEngine serverEngine) throws SSLException {
        boolean clientDone = false;
        boolean serverDone = false;
        boolean cliDataReady = false;
        boolean servDataReady = false;
        SSLEngineResult clientResult;
        SSLEngineResult serverResult;
        SSLSession session = clientEngine.getSession();
        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();
        ByteBuffer clientIn = ByteBuffer.allocate(appBufferMax + 50);
        ByteBuffer serverIn = ByteBuffer.allocate(appBufferMax + 50);
        ByteBuffer cTOs = ByteBuffer.allocateDirect(netBufferMax);
        ByteBuffer sTOc = ByteBuffer.allocateDirect(netBufferMax);
        HandshakeStatus hsStat;
        final ByteBuffer clientOut = ByteBuffer.wrap(
                "Hi Server, I'm Client".getBytes());
        final ByteBuffer serverOut = ByteBuffer.wrap(
                "Hello Client, I'm Server".getBytes());

        clientEngine.beginHandshake();
        serverEngine.beginHandshake();

        while (!clientDone && !serverDone) {
            // Client processing
            hsStat = clientEngine.getHandshakeStatus();
            log("Client HS Stat: " + hsStat);
            switch (hsStat) {
                case NOT_HANDSHAKING:
                    log("Closing client engine");
                    clientEngine.closeOutbound();
                    clientDone = true;
                    break;
                case NEED_WRAP:
                    log(String.format("CTOS: p:%d, l:%d, c:%d", cTOs.position(),
                            cTOs.limit(), cTOs.capacity()));
                    clientResult = clientEngine.wrap(clientOut, cTOs);
                    log("client wrap: ", clientResult);
                    if (clientResult.getStatus()
                            == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        // Get a larger buffer and try again
                        int updateSize = 2 * netBufferMax;
                        log("Resizing buffer to " + updateSize + " bytes");
                        cTOs = ByteBuffer.allocate(updateSize);
                        clientResult = clientEngine.wrap(clientOut, cTOs);
                        log("client wrap (resized): ", clientResult);
                    }
                    runDelegatedTasks(clientResult, clientEngine);
                    cTOs.flip();
                    cliDataReady = true;
                    break;
                case NEED_UNWRAP:
                    if (servDataReady) {
                        log(String.format("STOC: p:%d, l:%d, c:%d",
                                sTOc.position(),
                                sTOc.limit(), sTOc.capacity()));
                        clientResult = clientEngine.unwrap(sTOc, clientIn);
                        log("client unwrap: ", clientResult);
                        runDelegatedTasks(clientResult, clientEngine);
                        servDataReady = sTOc.hasRemaining();
                        sTOc.compact();
                    } else {
                        log("Server-to-client data not ready, skipping client" +
                                " unwrap");
                    }
                    break;
                case NEED_UNWRAP_AGAIN:
                    clientResult = clientEngine.unwrap(ByteBuffer.allocate(0),
                            clientIn);
                    log("client unwrap (again): ", clientResult);
                    runDelegatedTasks(clientResult, clientEngine);
                    break;
            }

            // Server processing
            hsStat = serverEngine.getHandshakeStatus();
            log("Server HS Stat: " + hsStat);
            switch (hsStat) {
                case NEED_WRAP:
                    log(String.format("STOC: p:%d, l:%d, c:%d", sTOc.position(),
                            sTOc.limit(), sTOc.capacity()));
                    serverResult = serverEngine.wrap(serverOut, sTOc);
                    log("server wrap: ", serverResult);
                    if (serverResult.getStatus()
                            == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        // Get a new buffer and try again
                        int updateSize = 2 * netBufferMax;
                        log("Resizing buffer to " + updateSize + " bytes");
                        sTOc = ByteBuffer.allocate(updateSize);
                        serverResult = serverEngine.wrap(clientOut, sTOc);
                        log("server wrap (resized): ", serverResult);
                    }
                    runDelegatedTasks(serverResult, serverEngine);
                    sTOc.flip();
                    servDataReady = true;
                    break;
                case NOT_HANDSHAKING:
                    log("Closing server engine");
                    serverEngine.closeOutbound();
                    serverDone = true;
                    break;
                case NEED_UNWRAP:
                    if (cliDataReady) {
                        log(String.format("CTOS: p:%d, l:%d, c:%d",
                                cTOs.position(),
                                cTOs.limit(), cTOs.capacity()));
                        serverResult = serverEngine.unwrap(cTOs, serverIn);
                        log("server unwrap: ", serverResult);
                        runDelegatedTasks(serverResult, serverEngine);
                        cliDataReady = cTOs.hasRemaining();
                        cTOs.compact();
                    } else {
                        log("Client-to-server data not ready, skipping server" +
                                " unwrap");
                    }
                    break;
                case NEED_UNWRAP_AGAIN:
                    serverResult = serverEngine.unwrap(ByteBuffer.allocate(0),
                            serverIn);
                    log("server unwrap (again): ", serverResult);
                    runDelegatedTasks(serverResult, serverEngine);
                    break;
            }
        }
    }

    private static void log(String str) {
        System.out.println(str);
    }

    private static void log(String str, SSLEngineResult result) {
        System.out.println("The format of the SSLEngineResult is: \n" +
                "\t\"getStatus() / getHandshakeStatus()\" +\n" +
                "\t\"bytesConsumed() / bytesProduced()\"\n");

        HandshakeStatus hsStatus = result.getHandshakeStatus();

        log(str +
                result.getStatus() + "/" + hsStatus + ", " +
                result.bytesConsumed() + "/" + result.bytesProduced() +
                " bytes");

        if (hsStatus == HandshakeStatus.FINISHED) {
            log("\t...ready for application data");
        }
    }

    private static void runDelegatedTasks(SSLEngineResult result,
            SSLEngine engine) {
        HandshakeStatus hsStatus = result.getHandshakeStatus();

        if (hsStatus == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                log("\trunning delegated task...");
                runnable.run();
            }
            hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new RuntimeException(
                        "handshake shouldn't need additional tasks");
            }
            log("\tnew HandshakeStatus: " + hsStatus);
        }
    }
}
