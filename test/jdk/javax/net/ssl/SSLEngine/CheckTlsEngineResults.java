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
 * @bug 4948079
 * @summary Verify return values from SSLEngine wrap/unwrap (TLSv1.2) operations
 *
 * @run main CheckTlsEngineResults
 *
 * @author Brad Wetmore
 */

/*
 * This is a simple hack to test a bunch of conditions and check
 * their return codes.
 */
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import java.io.*;
import java.security.*;
import java.nio.*;

public class CheckTlsEngineResults {

    private final SSLContext SSL_CONTEXT;
    private SSLEngine clientEngine;    // client
    private SSLEngine serverEngine;    // server

    private static final String PATH_TO_STORES = "../etc";

    private static final String KEYSTORE_FILE = "keystore";
    private static final String TRUSTSTORE_FILE = "truststore";

    private static final String keyFilename =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                "/" + KEYSTORE_FILE;
    private static final String trustFilename =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                "/" + TRUSTSTORE_FILE;

    private ByteBuffer clientOut;         // write side of clientEngine
    private ByteBuffer clientIn;          // read side of clientEngine
    private ByteBuffer serverOut;         // write side of serverEngine
    private ByteBuffer serverIn;          // read side of serverEngine

    private ByteBuffer clientToServer;        // "reliable" transport clientEngine->serverEngine
    private ByteBuffer serverToClient;        // "reliable" transport serverEngine->clientEngine

    /*
     * Majority of the test case is here, setup is done below.
     */

    private void createSSLEngines() throws Exception {
        clientEngine = SSL_CONTEXT.createSSLEngine("client", 1);
        clientEngine.setUseClientMode(true);

        serverEngine = SSL_CONTEXT.createSSLEngine("server", 2);
        serverEngine.setUseClientMode(false);
    }

    private boolean isHandshaking(SSLEngine e) {
        return (e.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING);
    }

    private void checkResult(ByteBuffer bbIn, ByteBuffer bbOut,
            SSLEngineResult result,
            Status status, HandshakeStatus hsStatus,
            int consumed, int produced)
            throws Exception {

        if ((status != null) && (result.getStatus() != status)) {
            throw new Exception("Unexpected Status: need = " + status +
                " got = " + result.getStatus());
        }

        if ((hsStatus != null) && (result.getHandshakeStatus() != hsStatus)) {
            throw new Exception("Unexpected hsStatus: need = " + hsStatus +
                " got = " + result.getHandshakeStatus());
        }

        if ((consumed != -1) && (consumed != result.bytesConsumed())) {
            throw new Exception("Unexpected consumed: need = " + consumed +
                " got = " + result.bytesConsumed());
        }

        if ((produced != -1) && (produced != result.bytesProduced())) {
            throw new Exception("Unexpected produced: need = " + produced +
                " got = " + result.bytesProduced());
        }

        if ((consumed != -1) && (bbIn.position() != result.bytesConsumed())) {
            throw new Exception("Consumed " + bbIn.position() +
                " != " + consumed);
        }

        if ((produced != -1) && (bbOut.position() != result.bytesProduced())) {
            throw new Exception("produced " + bbOut.position() +
                " != " + produced);
        }
    }

    private void test() throws Exception {
        createSSLEngines();
        createBuffers();

        SSLEngineResult result1;        // clientEngine's results from last operation
        SSLEngineResult result2;        // serverEngine's results from last operation
        String [] suite1 = new String [] {
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA" };
        String [] suite2 = new String [] {
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" };

        clientEngine.setEnabledCipherSuites(suite1);
        serverEngine.setEnabledCipherSuites(suite1);

        log("================");

        log("unexpected empty unwrap");
        serverToClient.limit(0);
        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_WRAP, 0, 0);
        serverToClient.limit(serverToClient.capacity());

        log("======================================");
        log("Client -> Server [ClientHello]");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
             Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);

        clientToServer.flip();
        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.OK, HandshakeStatus.NEED_TASK, result1.bytesProduced(), 0);
        runDelegatedTasks(serverEngine);

        clientToServer.compact();

        log("Check for unwrap when wrap needed");
        result2 = serverEngine.unwrap(clientToServer, serverIn);
        checkResult(clientToServer, serverIn, result2,
            Status.OK, HandshakeStatus.NEED_WRAP, 0, 0);

        log("======================================");
        log("Server -> Client [ServerHello]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_TASK, result2.bytesProduced(), 0);
        serverToClient.compact();

        runDelegatedTasks(clientEngine);

        log("======================================");
        log("Client -> Server [ClientKeyExchange]");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
             Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);

        clientToServer.flip();
        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.OK, HandshakeStatus.NEED_TASK, result1.bytesProduced(), 0);
        runDelegatedTasks(serverEngine);

        clientToServer.compact();

        log("======================================");
        log("Client -> Server [ChangeCipherSpec]");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
             Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);

        clientToServer.flip();
        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.OK, HandshakeStatus.NEED_UNWRAP,
             result1.bytesProduced(), 0);

        clientToServer.compact();

        log("======================================");
        log("Client -> Server [Finished]");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
             Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);

        clientToServer.flip();
        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.OK, HandshakeStatus.NEED_WRAP, result1.bytesProduced(), 0);

        clientToServer.compact();

        log("======================================");
        log("Server -> Client [NewSessionTicket]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_UNWRAP, result2.bytesProduced(), 0);
        serverToClient.compact();

        log("======================================");
        log("Server -> Client [ChangeCipherSpec]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_UNWRAP, result2.bytesProduced(), 0);
        serverToClient.compact();

        log("======================================");
        log("Server -> Client [Finished]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
                Status.OK, HandshakeStatus.FINISHED, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
                Status.OK, HandshakeStatus.FINISHED, result2.bytesProduced(), 0);
        serverToClient.compact();


        log("======================================");
        log("Check Session/Ciphers");

        String suite = clientEngine.getSession().getCipherSuite();
        if (!suite.equals(suite1[0])) {
            throw new Exception("suites not equal: " + suite + "/" +
                suite1[0]);
        }

        suite = serverEngine.getSession().getCipherSuite();
        if (!suite.equals(suite1[0])) {
            throw new Exception("suites not equal: " + suite + "/" +
                suite1[0]);
        }

        log("======================================");
        log("DATA");

        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
            Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            clientOut.capacity(), -1);
        clientToServer.flip();

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            serverOut.capacity(), -1);
        serverToClient.flip();

        SSLEngineResult result3 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result3,
            Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            result2.bytesProduced(), result2.bytesConsumed());
        serverToClient.compact();

        SSLEngineResult result4 = serverEngine.unwrap(clientToServer, serverIn);
        checkResult(clientToServer, serverIn, result4,
            Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            result1.bytesProduced(), result1.bytesConsumed());
        clientToServer.compact();

        clientIn.clear();
        serverIn.clear();
        clientOut.rewind();
        serverOut.rewind();

        log("======================================");
        log("RENEGOTIATE");

        serverEngine.getSession().invalidate();
        serverEngine.setNeedClientAuth(true);

        clientEngine.setEnabledCipherSuites(suite2);
        serverEngine.setEnabledCipherSuites(suite2);

        serverEngine.beginHandshake();

        log("======================================");
        log("Server -> Client [HelloRequest]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_TASK, result2.bytesProduced(), 0);
        serverToClient.compact();

        runDelegatedTasks(clientEngine);

        log("======================================");
        log("CLient -> Server [ClientHello]");

        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
             Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);

        clientToServer.flip();
        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.OK, HandshakeStatus.NEED_TASK, result1.bytesProduced(), 0);
        runDelegatedTasks(serverEngine);

        clientToServer.compact();

        log("======================================");
        log("CLIENT->SERVER DATA IN MIDDLE OF HANDSHAKE");

        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
            Status.OK, HandshakeStatus.NEED_UNWRAP,
            clientOut.capacity(), -1);
        clientToServer.flip();

        result4 = serverEngine.unwrap(clientToServer, serverIn);
        checkResult(clientToServer, serverIn, result4,
            Status.OK, HandshakeStatus.NEED_WRAP,
            result1.bytesProduced(), result1.bytesConsumed());
        clientToServer.compact();

        serverIn.clear();
        clientOut.rewind();

        log("======================================");
        log("Server -> Client [ServerHello]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_TASK, result2.bytesProduced(), 0);
        serverToClient.compact();

        runDelegatedTasks(clientEngine);

        log("======================================");
        log("SERVER->CLIENT DATA IN MIDDLE OF HANDSHAKE");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NEED_UNWRAP,
            serverOut.capacity(), -1);
        serverToClient.flip();

        result3 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result3,
            Status.OK, HandshakeStatus.NEED_WRAP,
            result2.bytesProduced(), result2.bytesConsumed());
        serverToClient.compact();

        clientIn.clear();
        serverOut.rewind();

        log("======================================");
        log("Client -> Server [Certificate] and [ClientKeyExchange]");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
             Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);

        clientToServer.flip();
        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.OK, HandshakeStatus.NEED_TASK, result1.bytesProduced(), 0);
        runDelegatedTasks(serverEngine);

        clientToServer.compact();

        log("======================================");
        log("Client -> Server [ChangeCipherSpec]");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
             Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);

        clientToServer.flip();
        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.OK, HandshakeStatus.NEED_UNWRAP,
             result1.bytesProduced(), 0);

        clientToServer.compact();

        log("======================================");
        log("Client -> Server [Finished]");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
             Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);

        clientToServer.flip();
        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.OK, HandshakeStatus.NEED_WRAP, result1.bytesProduced(), 0);

        clientToServer.compact();

        log("======================================");
        log("Server -> Client [NewSessionTicket]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_UNWRAP, result2.bytesProduced(), 0);
        serverToClient.compact();

        log("======================================");
        log("Server -> Client [ChangeCipherSpec]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
                Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
                Status.OK, HandshakeStatus.NEED_UNWRAP, result2.bytesProduced(), 0);
        serverToClient.compact();

        log("======================================");
        log("Server -> Client [Finished]");

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.FINISHED, 0, -1);
        serverToClient.flip();

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.OK, HandshakeStatus.FINISHED, result2.bytesProduced(), 0);
        serverToClient.compact();

        log("======================================");
        log("Check Session/Ciphers");

        suite = clientEngine.getSession().getCipherSuite();
        if (!suite.equals(suite2[0])) {
            throw new Exception("suites not equal: " + suite + "/" +
                suite2[0]);
        }

        suite = serverEngine.getSession().getCipherSuite();
        if (!suite.equals(suite2[0])) {
            throw new Exception("suites not equal: " + suite + "/" +
                suite2[0]);
        }

        log("======================================");
        log("DATA USING NEW SESSION");

        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
            Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            clientOut.capacity(), -1);
        clientToServer.flip();

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            serverOut.capacity(), -1);
        serverToClient.flip();

        result3 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result3,
            Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            result2.bytesProduced(), result2.bytesConsumed());
        serverToClient.compact();

        result4 = serverEngine.unwrap(clientToServer, serverIn);
        checkResult(clientToServer, serverIn, result4,
            Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            result1.bytesProduced(), result1.bytesConsumed());
        clientToServer.compact();

        clientIn.clear();
        serverIn.clear();
        clientOut.rewind();
        serverOut.rewind();

        log("======================================");
        log("Server -> Client [CloseNotify]");

        if (isHandshaking(clientEngine)) {
            throw new Exception("clientEngine IS handshaking");
        }

        if (isHandshaking(serverEngine)) {
            throw new Exception("serverEngine IS handshaking");
        }

        serverEngine.closeOutbound();

        if (!isHandshaking(serverEngine)) {
            throw new Exception("serverEngine IS NOT handshaking");
        }

        clientOut.rewind();
        serverOut.rewind();

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING, 0, -1);
        serverToClient.flip();

        if (clientEngine.isInboundDone()) {
            throw new Exception("clientEngine inboundDone");
        }

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(serverToClient, clientIn, result1,
            Status.CLOSED, HandshakeStatus.NEED_WRAP,
            result2.bytesProduced(), 0);
        serverToClient.compact();

        if (!clientEngine.isInboundDone()) {
            throw new Exception("clientEngine inboundDone");
        }

        if (!isHandshaking(clientEngine)) {
            throw new Exception("clientEngine IS NOT handshaking");
        }

        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(serverOut, serverToClient, result2,
            Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING, 0, 0);
        serverToClient.flip();

        log("======================================");
        log("CloseNotify response");

        if (clientEngine.isOutboundDone()) {
            throw new Exception("clientEngine outboundDone");
        }

        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(clientOut, clientToServer, result1,
                        Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING, 0, -1);

        if (!clientEngine.isOutboundDone()) {
            throw new Exception("clientEngine outboundDone is NOT done");
        }

        if (isHandshaking(clientEngine)) {
            throw new Exception("clientEngine IS handshaking");
        }

        clientToServer.flip();

        if (!serverEngine.isOutboundDone()) {
            throw new Exception("clientEngine outboundDone");
        }

        if (serverEngine.isInboundDone()) {
            throw new Exception("clientEngine inboundDone");
        }

        result2 = serverEngine.unwrap(clientToServer, serverIn);

        checkResult(clientToServer, serverIn, result2,
             Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
             result1.bytesProduced(), 0);

        if (!serverEngine.isOutboundDone()) {
            throw new Exception("clientEngine outboundDone is NOT done");
        }

        if (!serverEngine.isInboundDone()) {
            throw new Exception("clientEngine inboundDone is NOT done");
        }

        if (isHandshaking(serverEngine)) {
            throw new Exception("clientEngine IS handshaking");
        }

        clientToServer.compact();
    }

    public static void main(String args[]) throws Exception {
        CheckTlsEngineResults cs = new CheckTlsEngineResults();
        cs.test();
        System.out.println("Test Passed.");
    }

    /*
     * **********************************************************
     * Majority of the test case is above, below is just setup stuff
     * **********************************************************
     */

    public CheckTlsEngineResults() throws Exception {
        SSL_CONTEXT = getSSLContext(keyFilename, trustFilename);
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

        SSLContext sslCtx = SSLContext.getInstance("TLSv1.2");

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

        log("Client out = " + clientOut);
        log("Server out = " + serverOut);
        log("");
    }

    private static void runDelegatedTasks(SSLEngine engine) {

        Runnable runnable;
        while ((runnable = engine.getDelegatedTask()) != null) {
            log("Running delegated task...");
            runnable.run();
        }
    }

    private static void log(String str) {
        System.out.println(str);
    }
}
