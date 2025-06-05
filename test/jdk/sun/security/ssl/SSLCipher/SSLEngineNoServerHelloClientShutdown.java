/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331682
 * @summary Slow networks/Impatient clients can potentially send
 *          unencrypted TLSv1.3 alerts that won't parse on the server.
 * @library /javax/net/ssl/templates /test/lib
 * @run main/othervm SSLEngineNoServerHelloClientShutdown
 */

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.fail;
import static jdk.test.lib.security.SecurityUtils.inspectTlsBuffer;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSession;

/**
 * A SSLEngine usage example which simplifies the presentation
 * by removing the I/O and multi-threading concerns.
 * <p>
 * The test creates two SSLEngines, simulating a client and server.
 * The "transport" layer consists two byte buffers:  think of them
 * as directly connected pipes.
 * <p>
 * When this application runs, notice that several messages
 * (wrap/unwrap) pass before any application data is consumed or
 * produced.
 */
public class SSLEngineNoServerHelloClientShutdown extends SSLContextTemplate {

    protected static final String EXCEPTION_MSG =
        "Unexpected plaintext alert received: " +
        "Level: warning; Alert: user_canceled";

    protected SSLEngine clientEngine;     // client Engine
    protected ByteBuffer clientOut;       // write side of clientEngine
    protected final ByteBuffer clientIn;        // read side of clientEngine

    protected final SSLEngine serverEngine;     // server Engine
    protected final ByteBuffer serverOut;       // write side of serverEngine
    protected final ByteBuffer serverIn;        // read side of serverEngine

    protected ByteBuffer cTOs;        // "reliable" transport client->server
    protected final ByteBuffer sTOc;  // "reliable" transport server->client

    protected SSLEngineNoServerHelloClientShutdown() throws Exception {
        serverEngine = configureServerEngine(
                createServerSSLContext().createSSLEngine());

        clientEngine = configureClientEngine(
                createClientSSLContext().createSSLEngine());

        // We'll assume the buffer sizes are the same between client and server.
        SSLSession session = clientEngine.getSession();
        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();

        // We'll make the input buffers a bit bigger than the max needed
        // size, so that unwrap()s following a successful data transfer
        // won't generate BUFFER_OVERFLOWS.
        clientIn = ByteBuffer.allocate(appBufferMax + 50);
        serverIn = ByteBuffer.allocate(appBufferMax + 50);

        cTOs = ByteBuffer.allocateDirect(netBufferMax * 2);
        // Make it larger so subsequent server wraps won't generate
        // BUFFER_OVERFLOWS
        sTOc = ByteBuffer.allocateDirect(netBufferMax * 2);

        clientOut = createClientOutputBuffer();
        serverOut = createServerOutputBuffer();
    }

    protected ByteBuffer createServerOutputBuffer() {
        return ByteBuffer.wrap("Hello Client, I'm Server".getBytes());
    }

    protected ByteBuffer createClientOutputBuffer() {
        return ByteBuffer.wrap("Hi Server, I'm Client".getBytes());
    }

    /*
     * Configure the client side engine.
     */
    protected SSLEngine configureClientEngine(SSLEngine clientEngine) {
        clientEngine.setUseClientMode(true);
        clientEngine.setEnabledProtocols(new String[] {"TLSv1.3"});
        return clientEngine;
    }

    /*
     * Configure the server side engine.
     */
    protected SSLEngine configureServerEngine(SSLEngine serverEngine) {
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);
        return serverEngine;
    }

    public static void main(String[] args) throws Exception {
        new SSLEngineNoServerHelloClientShutdown().runTestUserCancelled();
    }

    //
    // Private methods that used to build the common part of the test.
    //

    private void runTestUserCancelled() throws Exception {
        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        log("=================");

        // Client: produce client_hello
        log("---Client Wrap client_hello---");
        clientResult = clientEngine.wrap(clientOut, cTOs);
        logEngineStatus(clientEngine, clientResult);
        runDelegatedTasks(clientEngine);

        cTOs.flip();

        // Server: consume client_hello
        log("---Server Unwrap client_hello---");
        serverResult = serverEngine.unwrap(cTOs, serverIn);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);

        cTOs.compact();

        // Server: produce server_hello
        log("---Server Wrap server_hello---");
        serverResult = serverEngine.wrap(serverOut, sTOc);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);
        // SH packet went missing.  Timeout on Client.

        // Server: produce other outbound messages
        log("---Server Wrap---");
        serverResult = serverEngine.wrap(serverOut, sTOc);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);
        // CCS packet went missing.  Timeout on Client.

        // Server: produce other outbound messages
        log("---Server Wrap---");
        serverResult = serverEngine.wrap(serverOut, sTOc);
        logEngineStatus(serverEngine, serverResult);
        runDelegatedTasks(serverEngine);
        // EE/etc. packet went missing.  Timeout on Client.

        // Shutdown client
        log("---Client closeOutbound---");
        clientEngine.closeOutbound();

        // Client:  produce an unencrypted user_canceled
        log("---Client Wrap user_canceled---");
        clientResult = clientEngine.wrap(clientOut, cTOs);
        logEngineStatus(clientEngine, clientResult);
        runDelegatedTasks(clientEngine);

        cTOs.flip();
        inspectTlsBuffer(cTOs);

        // Server unwrap should throw a proper exception when receiving an
        // unencrypted 2 byte packet user_canceled alert.
        log("---Server Unwrap user_canceled alert---");
        try {
            serverEngine.unwrap(cTOs, serverIn);
        } catch (SSLProtocolException e) {
            assertEquals(
                GeneralSecurityException.class, e.getCause().getClass());
            assertEquals(EXCEPTION_MSG, e.getCause().getMessage());
            return;
        }
        fail("Server should have thrown SSLProtocolException");
    }

    protected static void logEngineStatus(SSLEngine engine) {
        log("\tCurrent HS State: " + engine.getHandshakeStatus());
        log("\tisInboundDone() : " + engine.isInboundDone());
        log("\tisOutboundDone(): " + engine.isOutboundDone());
    }

    protected static void logEngineStatus(
            SSLEngine engine, SSLEngineResult result) {
        log("\tResult Status    : " + result.getStatus());
        log("\tResult HS Status : " + result.getHandshakeStatus());
        log("\tEngine HS Status : " + engine.getHandshakeStatus());
        log("\tisInboundDone()  : " + engine.isInboundDone());
        log("\tisOutboundDone() : " + engine.isOutboundDone());
        log("\tMore Result      : " + result);
    }

    protected static void log(String message) {
        System.err.println(message);
    }

    // If the result indicates that we have outstanding tasks to do,
    // go ahead and run them in this thread.
    protected static void runDelegatedTasks(SSLEngine engine)
        throws Exception {

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
}
