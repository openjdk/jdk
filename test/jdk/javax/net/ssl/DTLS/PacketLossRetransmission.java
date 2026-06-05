/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8161086 8367059
 * @summary DTLS handshaking fails if some messages were lost
 * @modules java.base/sun.security.util
 * @library /test/lib
 * @build DTLSOverDatagram
 *
 * @run main/othervm PacketLossRetransmission client full 1 client_hello
 * @run main/othervm PacketLossRetransmission client full 16 client_key_exchange
 * @run main/othervm PacketLossRetransmission client full 20 finished
 * @run main/othervm PacketLossRetransmission client full -1 change_cipher_spec
 * @run main/othervm PacketLossRetransmission server full 2 server_hello
 * @run main/othervm PacketLossRetransmission server full 3 hello_verify_request
 * @run main/othervm PacketLossRetransmission server full 11 certificate
 * @run main/othervm PacketLossRetransmission server full 12 server_key_exchange
 * @run main/othervm PacketLossRetransmission server full 14 server_hello_done
 * @run main/othervm PacketLossRetransmission server full 20 finished
 * @run main/othervm PacketLossRetransmission server full -1 change_cipher_spec
 * @run main/othervm PacketLossRetransmission server full 4 new_session_ticket
 * @run main/othervm PacketLossRetransmission client resume 1 client_hello
 * @run main/othervm PacketLossRetransmission client resume 20 finished
 * @run main/othervm PacketLossRetransmission client resume -1 change_cipher_spec
 * @run main/othervm PacketLossRetransmission server resume 2 server_hello
 * @run main/othervm PacketLossRetransmission server resume 3 hello_verify_request
 * @run main/othervm PacketLossRetransmission server resume 20 finished
 * @run main/othervm PacketLossRetransmission server resume -1 change_cipher_spec
 * @run main/othervm PacketLossRetransmission server resume 4 new_session_ticket
 */


import java.nio.ByteBuffer;
import java.util.List;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * Test that DTLS implementation is able to do retransmission internally
 * automatically if packet get lost.
 */
public class PacketLossRetransmission extends DTLSOverDatagram {

    private static boolean isClient;
    private static byte handshakeType;
    private static final int TIMEOUT = 500;

    private boolean needPacketLoss = true;
    private final SSLContext clientContext;
    private final SSLContext serverContext;

    protected PacketLossRetransmission() throws Exception {
        this.clientContext = getDTLSContext();
        this.serverContext = getDTLSContext();
    }

    public static void main(String[] args) throws Exception {
        isClient = args[0].equals("client");
        boolean isResume = args[1].equals("resume");
        handshakeType = Byte.parseByte(args[2]);

        PacketLossRetransmission testCase = new PacketLossRetransmission();
        testCase.setSocketTimeout(TIMEOUT);

        if (isResume) {
            System.out.println("Starting initial handshake");
            // The initial session will populate the TLS session cache.
            initialSession(testCase.createSSLEngine(true),
                    testCase.createSSLEngine(false));
        }

        testCase.runTest(testCase);
    }

    @Override
    protected SSLContext getClientDTLSContext() throws Exception {
        return clientContext;
    }

    @Override
    protected SSLContext getServerDTLSContext() throws Exception {
        return serverContext;
    }

    @Override
    boolean produceHandshakePackets(SSLEngine engine, SocketAddress socketAddr,
            String side, List<DatagramPacket> packets) throws Exception {

        boolean finished = super.produceHandshakePackets(
                engine, socketAddr, side, packets);

        if (needPacketLoss && (isClient == engine.getUseClientMode())) {
            DatagramPacket packet = getPacket(packets, handshakeType);
            if (packet != null) {
                needPacketLoss = false;

                System.out.println("Loss a packet of handshake message");
                packets.remove(packet);
            }
        }

        return finished;
    }

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
