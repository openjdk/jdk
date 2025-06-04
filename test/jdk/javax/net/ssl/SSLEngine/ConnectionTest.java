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
 * @summary Add non-blocking SSL/TLS functionality, usable with any
 *      I/O abstraction
 * @author Brad Wetmore
 *
 * @run main/othervm ConnectionTest TLSv1.2 TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
 * @run main/othervm ConnectionTest TLSv1.3 TLS_AES_256_GCM_SHA384
 */

/*
 * This is a bit hacky, meant to test various conditions.  The main
 * thing I wanted to do with this was to do buffer reads/writes
 * when buffers were not empty.  (buffer.position() = 10)
 * The code could certainly be tightened up a lot.
 */
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import java.io.*;
import java.security.*;
import java.nio.*;

public class ConnectionTest {

    private final SSLEngine clientEngine;
    private final SSLEngine serverEngine;

    private static final String PATH_TO_STORES = "../etc";
    private static final String KEYSTORE_FILE = "keystore";
    private static final String TRUSTSTORE_FILE = "truststore";

    private static final String KEYSTORE_PATH =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                "/" + KEYSTORE_FILE;
    private static final String TRUSTSTORE_PATH =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                "/" + TRUSTSTORE_FILE;

    private ByteBuffer clientIn, clientOut;
    private ByteBuffer serverIn, serverOut;
    private ByteBuffer clientToServer, serverToClient;
    private ByteBuffer emptyBuffer;

    private ByteBuffer clientToServerShifter, serverToClientShifter;

    private final String HOSTNAME = "hostname";

    private final int PORT_NUMBER = 77;

    public ConnectionTest(String enabledProtocol, String enabledCipherSuite)
            throws Exception {

        SSLContext sslContext = getSSLContext();
        clientEngine = sslContext.createSSLEngine(HOSTNAME, PORT_NUMBER);
        serverEngine = sslContext.createSSLEngine();

        clientEngine.setEnabledCipherSuites(new String [] {
            enabledCipherSuite});
        clientEngine.setEnabledProtocols(new String[]{enabledProtocol});

        serverEngine.setEnabledCipherSuites(new String [] {
                enabledCipherSuite});
        serverEngine.setEnabledProtocols(new String[]{enabledProtocol});


        createBuffers();
    }

    private SSLContext getSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");
        char[] passphrase = "passphrase".toCharArray();

        ks.load(new FileInputStream(KEYSTORE_PATH), passphrase);
        ts.load(new FileInputStream(TRUSTSTORE_PATH), passphrase);

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

        clientIn = ByteBuffer.allocateDirect(appBufferMax + 10);
        serverIn = ByteBuffer.allocateDirect(appBufferMax + 10);

        clientIn.position(10);
        serverIn.position(10);

        clientToServer = ByteBuffer.allocateDirect(netBufferMax + 10);
        serverToClient = ByteBuffer.allocateDirect(netBufferMax + 10);

        clientToServer.position(10);
        serverToClient.position(10);
        clientToServerShifter = clientToServer.slice();
        serverToClientShifter = serverToClient.slice();

        clientOut = ByteBuffer.wrap("Hi Engine2, I'm SSLEngine1".getBytes());
        serverOut = ByteBuffer.wrap("Hello Engine1, I'm SSLEngine2".getBytes());

        emptyBuffer = ByteBuffer.allocate(10);
        emptyBuffer.limit(5);
        emptyBuffer.position(emptyBuffer.limit());

        log("clientOut = " + clientOut);
        log("serverOut = " + serverOut);
        log("");
    }

    private void checkResult(SSLEngineResult result, Status status,
            HandshakeStatus hsStatus, int consumed, int produced,
            boolean done) {

        if ((status != null) && (result.getStatus() != status)) {
            throw new RuntimeException("Unexpected Status: need = " + status +
                " got = " + result.getStatus());
        }

        if ((hsStatus != null) && (result.getHandshakeStatus() != hsStatus)) {
            throw new RuntimeException("Unexpected hsStatus: need = " + hsStatus +
                " got = " + result.getHandshakeStatus());
        }

        if ((consumed != -1) && (consumed != result.bytesConsumed())) {
            throw new RuntimeException("Unexpected consumed: need = " + consumed +
                " got = " + result.bytesConsumed());
        }

        if ((produced != -1) && (produced != result.bytesProduced())) {
            throw new RuntimeException("Unexpected produced: need = " + produced +
                " got = " + result.bytesProduced());
        }

        if (done && (hsStatus == HandshakeStatus.FINISHED)) {
            throw new RuntimeException(
                "Handshake already reported finished");
        }

    }

    private boolean isHandshaking(SSLEngine e) {
        return (e.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING);
    }

    private void test() throws Exception {
        clientEngine.setUseClientMode(true);
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

        log("Testing for early unwrap/wrap");
        SSLEngineResult result1 = clientEngine.unwrap(serverToClient, clientIn);
        SSLEngineResult result2 = serverEngine.wrap(serverOut, clientToServer);

        /*
         * These should not consume/produce data, because they
         * are client and server, respectively, and don't
         * start handshaking this way.
         */
        checkResult(result1, Status.OK, HandshakeStatus.NEED_WRAP,
            0, 0, false);
        checkResult(result2, Status.OK, HandshakeStatus.NEED_UNWRAP,
            0, 0, false);

        log("Doing Initial Handshake");

        /*
         * Do initial handshaking
         */
        handshake();

        checkEngineAndSession();

        SSLSession clientSession1 = clientEngine.getSession();
        SSLSession serverSession1 = serverEngine.getSession();

        /*
         * Should be able to write/read a small buffer like this.
         */
        int appOut1Len = clientOut.remaining();
        int appOut2Len = serverOut.remaining();
        int net1Len;
        int net2Len;

        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(result1, Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            appOut1Len, -1, false);
        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(result2, Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            appOut2Len, -1, false);
        net1Len = result1.bytesProduced();
        net2Len = result2.bytesProduced();

        log("wrap1 = " + result1);
        log("wrap2 = " + result2);

        clientToServer.flip();
        serverToClient.flip();

        clientToServer.position(10);
        serverToClient.position(10);

        log("----");

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(result1, Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            net2Len, appOut2Len, false);
        result2 = serverEngine.unwrap(clientToServer, serverIn);
        checkResult(result2, Status.OK, HandshakeStatus.NOT_HANDSHAKING,
            net1Len, appOut1Len, false);

        log("unwrap1 = " + result1);
        log("unwrap2 = " + result2);

        updateByteBuffers();

        serverSession1.invalidate();
        serverEngine.beginHandshake();

        log("\nRENEGOTIATING");
        log("=============");

        clientIn.clear();
        serverIn.clear();

        /*
         * Do a quick test to see if this can do a switch
         * into client mode, at this point, you shouldn't be able
         * to switch back.
         */
        try {
            log("Try to change client mode");
            serverEngine.setUseClientMode(true);
            throw new RuntimeException("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            log("Caught correct IllegalArgumentException");
        }

        handshake();

        SSLSession clientSession2 = clientEngine.getSession();
        SSLSession serverSession2 = serverEngine.getSession();


        log("\nDoing close");
        log("===========");

        clientEngine.closeOutbound();
        serverEngine.closeOutbound();

        clientToServer.flip();
        serverToClient.flip();
        clientToServer.position(10);
        serverToClient.position(10);

        clientIn.clear();
        serverIn.clear();

        log("LAST UNWRAP");
        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(result1, Status.BUFFER_UNDERFLOW,
            HandshakeStatus.NEED_WRAP, 0, 0, false);
        result2 = serverEngine.unwrap(clientToServer, serverIn);
        checkResult(result2, Status.BUFFER_UNDERFLOW,
            HandshakeStatus.NEED_WRAP, 0, 0, false);

        log("unwrap1 = " + result1);
        log("unwrap2 = " + result2);

        updateByteBuffers();

        log("LAST WRAP");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(result1, Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
            0, -1, false);
        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(result2, Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
            0, -1, false);

        log("wrap1 = " + result1);
        log("wrap2 = " + result2);

        net1Len = result1.bytesProduced();
        net2Len = result2.bytesProduced();

        clientToServer.flip();
        serverToClient.flip();

        clientToServer.position(10);
        serverToClient.position(10);

        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(result1, Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
            net1Len, 0, false);
        result2 = serverEngine.unwrap(clientToServer, serverIn);
        checkResult(result2, Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
            net2Len, 0, false);

        log("unwrap1 = " + result1);
        log("unwrap2 = " + result2);

        updateByteBuffers();

        log("EXTRA WRAP");
        result1 = clientEngine.wrap(clientOut, clientToServer);
        checkResult(result1, Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
            0, 0, false);
        result2 = serverEngine.wrap(serverOut, serverToClient);
        checkResult(result2, Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
            0, 0, false);

        log("wrap1 = " + result1);
        log("wrap2 = " + result2);

        clientToServer.flip();
        serverToClient.flip();
        clientToServer.position(10);
        serverToClient.position(10);

        log("EXTRA UNWRAP");
        result1 = clientEngine.unwrap(serverToClient, clientIn);
        checkResult(result1, Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
            0, 0, false);
        result2 = serverEngine.unwrap(clientToServer, serverIn);
        checkResult(result2, Status.CLOSED, HandshakeStatus.NOT_HANDSHAKING,
            0, 0, false);

        log("unwrap1 = " + result1);
        log("unwrap2 = " + result2);

        checkSession(clientSession1, serverSession1, clientSession2, serverSession2);
        log(clientEngine);
        log(serverEngine);
    }

    private void handshake() throws Exception {
        boolean clientDone = false;
        boolean serverDone = false;
        SSLEngineResult result2;
        SSLEngineResult result1;
        while (isHandshaking(clientEngine) ||
                isHandshaking(serverEngine)) {

            log("================");

            result1 = clientEngine.wrap(emptyBuffer, clientToServer);
            checkResult(result1, null, null, 0, -1, clientDone);
            result2 = serverEngine.wrap(emptyBuffer, serverToClient);
            checkResult(result2, null, null, 0, -1, serverDone);

            if (result1.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                clientDone = true;
            }

            if (result2.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                serverDone = true;
            }

            log("wrap1 = " + result1);
            log("wrap2 = " + result2);

            if (result1.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = clientEngine.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            if (result2.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = serverEngine.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            clientToServer.flip();
            serverToClient.flip();

            clientToServer.position(10);
            serverToClient.position(10);

            log("----");

            result1 = clientEngine.unwrap(serverToClient, clientIn);
            checkResult(result1, null, null, -1, 0, clientDone);
            result2 = serverEngine.unwrap(clientToServer, serverIn);
            checkResult(result2, null, null, -1, 0, serverDone);

            if (result1.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                clientDone = true;
            }

            if (result2.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                serverDone = true;
            }

            log("unwrap1 = " + result1);
            log("unwrap2 = " + result2);

            if (result1.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = clientEngine.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            if (result2.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = serverEngine.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            updateByteBuffers();
        }


        log("\nDONE HANDSHAKING");
        log("================");

        if (!clientDone || !serverDone) {
            throw new RuntimeException("Both should be true:\n" +
                    " clientDone = " + clientDone + " serverDone = " + serverDone);
        }
    }

    private void updateByteBuffers() {
        clientToServerShifter.position(clientToServer.position() - 10);
        clientToServerShifter.limit(clientToServer.limit() - 10);
        serverToClientShifter.position(serverToClient.position() - 10);
        serverToClientShifter.limit(serverToClient.limit() - 10);
        clientToServerShifter.compact();
        serverToClientShifter.compact();
        clientToServer.position(clientToServerShifter.position() + 10);
        clientToServer.limit(clientToServerShifter.limit() + 10);
        serverToClient.position(serverToClientShifter.position() + 10);
        serverToClient.limit(serverToClientShifter.limit() + 10);
    }

    private static void checkSession(SSLSession clientSession1, SSLSession serverSession1,
            SSLSession clientSession2, SSLSession serverSession2) {
        log("\nSession Info for client SSLEngine 1");
        log(clientSession1);
        log(clientSession1.getCreationTime());

        String peer1 = clientSession1.getPeerHost();
        log(peer1);

        String protocol1 = clientSession1.getProtocol();
        log(protocol1);

        String ciphersuite1 = clientSession1.getCipherSuite();
        log(ciphersuite1);
        log("");

        log("\nSession Info for server SSLEngine 1");
        log(serverSession1);
        log(serverSession1.getCreationTime());

        String peer2 = serverSession1.getPeerHost();
        log(peer2);

        String protocol2 = serverSession1.getProtocol();
        log(protocol2);

        String ciphersuite2 = serverSession1.getCipherSuite();
        log(ciphersuite2);
        log("");

        if (peer1.equals(peer2)) {
            throw new RuntimeException("peer hostnames not equal");
        }

        if (!protocol1.equals(protocol2)) {
            throw new RuntimeException("protocols not equal");
        }

        compareCertificates(clientSession1, serverSession1);
        compareCertificates(clientSession2, serverSession2);

        if (!ciphersuite1.equals(ciphersuite2)) {
            throw new RuntimeException("ciphersuites not equal");
        }

        log("\nSession Info for client SSLEngine 2");
        log(clientSession2);
        log("\nSession Info for server SSLEngine 2");
        log(serverSession2);
    }


    private static void compareCertificates(SSLSession client, SSLSession server) {
        try {
            java.security.cert.Certificate clientLocal = client.getLocalCertificates()[0];
            java.security.cert.Certificate clientPeer = client.getPeerCertificates()[0];
            java.security.cert.Certificate serverLocal = server.getLocalCertificates()[0];
            java.security.cert.Certificate serverPeer = server.getPeerCertificates()[0];

            log(String.format("Client local cert: %s%nClient peer cert: %s%n"
                    + "Server local cert: %s%nServer peer cert: %s%n",
                    clientLocal, clientPeer, serverLocal, serverPeer));

            if (!clientLocal.equals(serverPeer)) {
                throw new RuntimeException("Client's local certificate does "
                        + "not match server's peer certificate");
            }

            if (!clientPeer.equals(serverLocal)) {
                throw new RuntimeException("Client's peer certificate does "
                        + "not match server's local certificate");
            }

        } catch (SSLPeerUnverifiedException e) {
            throw new RuntimeException("Could not get peer certificate!", e);
        }
    }

    private void checkEngineAndSession()
            throws Exception {
        String host = clientEngine.getPeerHost();
        int port = clientEngine.getPeerPort();
        if (!host.equals(HOSTNAME) || (port != PORT_NUMBER)) {
            throw new Exception("Unexpected host/port from client engine."
                    + " Expected " + HOSTNAME + ":" + PORT_NUMBER
                    + " Received " +host + ":" + port);
        }

        host = serverEngine.getPeerHost();
        port = serverEngine.getPeerPort();
        if ((host != null) || (port != -1)) {
            throw new Exception("Unexpected host/port from server engine."
                    + " Expected null:-1"
                    + " Received " + host + ":" + port);
        }

        SSLSession clientSession = clientEngine.getSession();

        host = clientSession.getPeerHost();
        port = clientSession.getPeerPort();
        if (!host.equals(HOSTNAME) || (port != PORT_NUMBER)) {
            throw new Exception("Unexpected host/port from client session."
                    + " Expected " + HOSTNAME + ":" + PORT_NUMBER
                    + " Received " + host + ":" + port);
        }

        SSLSession serverSession = serverEngine.getSession();

        host = serverSession.getPeerHost();
        port = serverSession.getPeerPort();
        if ((host != null) || (port != -1)) {
            throw new Exception("Unexpected host/port from server session."
                    + " Expected null:-1"
                    + " Received " + host + ":" + port);
        }

    }

    private static void log(Object msg) {
        System.out.println(msg);
    }

    public static void main(String args[]) throws Exception {
        // reset the security property to make sure that the algorithms
        // and keys used in this test are not disabled.
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        log(String.format("Running with %s and %s%n", args[0], args[1]));
        ConnectionTest ct = new ConnectionTest(args[0], args[1]);
        ct.test();
    }
}