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
 * @bug 8182621
 * @summary Verify the SSLEngine rejects empty Handshake, Alert, and ChangeCipherSpec messages.
 * @library /javax/net/ssl/templates
 * @run main SSLEngineEmptyFragments
 */
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.*;

public class SSLEngineEmptyFragments extends SSLContextTemplate {
    private static final byte HANDSHAKE_TYPE = 22;
    private static final byte ALERT_TYPE = 21;
    private static final byte CHANGE_CIPHERSPEC_TYPE = 20;
    private static final String TLSv12 = "TLSv1.2";
    private static final String TLSv13 = "TLSv1.3";

    private SSLEngine serverEngine;
    private SSLEngine clientEngine;
    private ByteBuffer clientIn;
    private ByteBuffer serverIn;
    private ByteBuffer clientToServer;
    private ByteBuffer serverToClient;
    private ByteBuffer clientOut;
    private ByteBuffer serverOut;

    private final String protocol;

    public SSLEngineEmptyFragments(String protocol) {
        this.protocol = protocol;
    }

    private void initialize() throws Exception {
        initialize(null);
    }

    private void initialize(String [] protocols) throws Exception {
        serverEngine = createServerSSLContext().createSSLEngine();
        clientEngine = createClientSSLContext().createSSLEngine();

        serverEngine.setUseClientMode(false);
        clientEngine.setUseClientMode(true);

        if (protocols != null) {
            clientEngine.setEnabledProtocols(protocols);
            serverEngine.setEnabledProtocols(protocols);
        }

        // do one legitimate handshake packet, then send a zero-length alert.
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

        clientToServer = ByteBuffer.allocateDirect(netBufferMax);
        serverToClient = ByteBuffer.allocateDirect(netBufferMax);

        clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());
        serverOut = ByteBuffer.wrap("Hello Client, I'm Server".getBytes());
    }

    private void testAlertPacketNotHandshaking() throws Exception {
        log("**** Empty alert packet/not handshaking");
        initialize();

        ByteBuffer alert = ByteBuffer.allocate(5);
        alert.put(new byte[]{ALERT_TYPE, 3, 3, 0, 0});
        alert.flip();

        try {
            unwrap(serverEngine, alert, serverIn);
            throw new RuntimeException("Expected exception was not thrown.");
        } catch (SSLHandshakeException exc) {
            log("Got the exception I wanted.");
        }
    }

    private void testAlertPacketMidHandshake() throws Exception {
        log("**** Empty alert packet during handshake.");
        initialize(new String[]{protocol});

        wrap(clientEngine, clientOut, clientToServer);
        runDelegatedTasks(clientEngine);
        clientToServer.flip();

        unwrap(serverEngine, clientToServer, serverIn);
        runDelegatedTasks(serverEngine);

        while(serverEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            wrap(serverEngine, serverOut, serverToClient);
            runDelegatedTasks(serverEngine);
            serverToClient.flip();
        }

        ByteBuffer alert = ByteBuffer.allocate(5);
        alert.put(new byte[]{ALERT_TYPE, 3, 3, 0, 0});
        alert.flip();

        try {
            unwrap(serverEngine, alert, serverIn);
            log("Server unwrap was successful when it should have failed.");
            throw new RuntimeException("Expected exception was not thrown.");
        } catch (SSLHandshakeException exc) {
            log("Got the exception I wanted.");
        }
    }

    private void testHandshakePacket() throws NoSuchAlgorithmException, SSLException {
        log("**** Empty handshake package.");
        SSLContext ctx = SSLContext.getDefault();
        SSLEngine engine = ctx.createSSLEngine();
        engine.setUseClientMode(false);

        try {
            ByteBuffer bb = ByteBuffer.allocate(5);
            bb.put(new byte[]{HANDSHAKE_TYPE, 3, 3, 0, 0});
            bb.flip();
            ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            engine.unwrap(bb, out);
            throw new RuntimeException("SSLEngine did not throw an exception for a zero-length fragment.");
        } catch (SSLProtocolException exc) {
            log("Received expected exception");
        }
    }

    private void testEmptyChangeCipherSpec() throws Exception {
        initialize(new String[]{protocol});

        boolean foundCipherSpecMsg = false;
        do {
            log("Client wrap");
            wrap(clientEngine, clientOut, clientToServer);
            runDelegatedTasks(clientEngine);

            if(clientToServer.get(0) == CHANGE_CIPHERSPEC_TYPE) {
                foundCipherSpecMsg = true;
                break;
            }

            log("server wrap");
            wrap(serverEngine, serverOut, serverToClient);
            runDelegatedTasks(serverEngine);

            clientToServer.flip();
            serverToClient.flip();

            log("client unwrap");
            unwrap(clientEngine, serverToClient, clientIn);
            runDelegatedTasks(clientEngine);

            log("server unwrap");
            unwrap(serverEngine, clientToServer, serverIn);
            runDelegatedTasks(serverEngine);

            clientToServer.compact();
            serverToClient.compact();
        } while(clientEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED
            && serverEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED);

        if (!foundCipherSpecMsg) {
            // performed TLS handshaking but didn't catch change-cipherspec message.
            throw new RuntimeException("Did not intercept ChangeCipherSpec message.");
        }

        ByteBuffer changeCipher = ByteBuffer.allocate(5);
        changeCipher.put(new byte[]{CHANGE_CIPHERSPEC_TYPE, 3, 3, 0, 0});
        changeCipher.flip();
        try {
            unwrap(serverEngine, changeCipher, serverIn);
            throw new RuntimeException("Didn't get the expected SSL exception");
        } catch (SSLProtocolException exc) {
            log("Received expected exception.");
        }
    }

    private SSLEngineResult wrap(SSLEngine engine, ByteBuffer src, ByteBuffer dst) throws SSLException {
        SSLEngineResult result = engine.wrap(src, dst);
        logEngineStatus(engine, result);
        return result;
    }

    private SSLEngineResult unwrap(SSLEngine engine, ByteBuffer src, ByteBuffer dst) throws SSLException {
        SSLEngineResult result = engine.unwrap(src, dst);
        logEngineStatus(engine, result);
        return result;
    }

    protected void runDelegatedTasks(SSLEngine engine) throws Exception {
        if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                log("    running delegated task...");
                runnable.run();
            }
            SSLEngineResult.HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw new Exception(
                        "handshake shouldn't need additional tasks");
            }
            logEngineStatus(engine);
        }
    }

    private void logEngineStatus(SSLEngine engine) {
        log("\tCurrent HS State: " + engine.getHandshakeStatus());
        log("\tisInboundDone() : " + engine.isInboundDone());
        log("\tisOutboundDone(): " + engine.isOutboundDone());
    }

    private void logEngineStatus(
            SSLEngine engine, SSLEngineResult result) {
        log("\tResult Status    : " + result.getStatus());
        log("\tResult HS Status : " + result.getHandshakeStatus());
        log("\tEngine HS Status : " + engine.getHandshakeStatus());
        log("\tisInboundDone()  : " + engine.isInboundDone());
        log("\tisOutboundDone() : " + engine.isOutboundDone());
        log("\tMore Result      : " + result);
    }

    private void log(String message) {
        System.err.println(message);
    }

    public static void main(String [] args) throws Exception {
        SSLEngineEmptyFragments tests = new SSLEngineEmptyFragments(TLSv12);
        tests.testHandshakePacket();
        tests.testAlertPacketNotHandshaking();
        tests.testAlertPacketMidHandshake();
        tests.testEmptyChangeCipherSpec();

        tests = new SSLEngineEmptyFragments(TLSv13);
        tests.testHandshakePacket();
        tests.testAlertPacketNotHandshaking();
    }
}
