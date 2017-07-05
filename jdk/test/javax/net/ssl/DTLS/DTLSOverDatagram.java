/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8043758
 * @summary Datagram Transport Layer Security (DTLS)
 * @modules java.base/sun.security.util
 * @run main/othervm DTLSOverDatagram
 */

import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
import java.util.concurrent.*;

import sun.security.util.HexDumpEncoder;

/**
 * An example to show the way to use SSLEngine in datagram connections.
 */
public class DTLSOverDatagram {

    private static int MAX_HANDSHAKE_LOOPS = 200;
    private static int MAX_APP_READ_LOOPS = 60;
    private static int SOCKET_TIMEOUT = 10 * 1000; // in millis
    private static int BUFFER_SIZE = 1024;
    private static int MAXIMUM_PACKET_SIZE = 1024;

    /*
     * The following is to set up the keystores.
     */
    private static String pathToStores = "../etc";
    private static String keyStoreFile = "keystore";
    private static String trustStoreFile = "truststore";
    private static String passwd = "passphrase";

    private static String keyFilename =
            System.getProperty("test.src", ".") + "/" + pathToStores +
                "/" + keyStoreFile;
    private static String trustFilename =
            System.getProperty("test.src", ".") + "/" + pathToStores +
                "/" + trustStoreFile;
    private static Exception clientException = null;
    private static Exception serverException = null;

    private static ByteBuffer serverApp =
                ByteBuffer.wrap("Hi Client, I'm Server".getBytes());
    private static ByteBuffer clientApp =
                ByteBuffer.wrap("Hi Server, I'm Client".getBytes());

    /*
     * =============================================================
     * The test case
     */
    public static void main(String[] args) throws Exception {
        DTLSOverDatagram testCase = new DTLSOverDatagram();
        testCase.runTest(testCase);
    }

    /*
     * Define the server side of the test.
     */
    void doServerSide(DatagramSocket socket, InetSocketAddress clientSocketAddr)
            throws Exception {

        // create SSLEngine
        SSLEngine engine = createSSLEngine(false);

        // handshaking
        handshake(engine, socket, clientSocketAddr, "Server");

        // read client application data
        receiveAppData(engine, socket, clientApp);

        // write server application data
        deliverAppData(engine, socket, serverApp, clientSocketAddr);
    }

    /*
     * Define the client side of the test.
     */
    void doClientSide(DatagramSocket socket, InetSocketAddress serverSocketAddr)
            throws Exception {

        // create SSLEngine
        SSLEngine engine = createSSLEngine(true);

        // handshaking
        handshake(engine, socket, serverSocketAddr, "Client");

        // write client application data
        deliverAppData(engine, socket, clientApp, serverSocketAddr);

        // read server application data
        receiveAppData(engine, socket, serverApp);
    }

    /*
     * =============================================================
     * The remainder is support stuff for DTLS operations.
     */
    SSLEngine createSSLEngine(boolean isClient) throws Exception {
        SSLContext context = getDTLSContext();
        SSLEngine engine = context.createSSLEngine();

        SSLParameters paras = engine.getSSLParameters();
        paras.setMaximumPacketSize(MAXIMUM_PACKET_SIZE);

        engine.setUseClientMode(isClient);
        engine.setSSLParameters(paras);

        return engine;
    }

    // handshake
    void handshake(SSLEngine engine, DatagramSocket socket,
            SocketAddress peerAddr, String side) throws Exception {

        boolean endLoops = false;
        int loops = MAX_HANDSHAKE_LOOPS;
        engine.beginHandshake();
        while (!endLoops &&
                (serverException == null) && (clientException == null)) {

            if (--loops < 0) {
                throw new RuntimeException(
                        "Too much loops to produce handshake packets");
            }

            SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
            log(side, "=======handshake(" + loops + ", " + hs + ")=======");
            if (hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP ||
                hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN) {

                log(side, "Receive DTLS records, handshake status is " + hs);

                ByteBuffer iNet;
                ByteBuffer iApp;
                if (hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    try {
                        socket.receive(packet);
                    } catch (SocketTimeoutException ste) {
                        log(side, "Warning: " + ste);

                        List<DatagramPacket> packets = new ArrayList<>();
                        boolean finished = onReceiveTimeout(
                                engine, peerAddr, side, packets);

                        log(side, "Reproduced " + packets.size() + " packets");
                        for (DatagramPacket p : packets) {
                            printHex("Reproduced packet",
                                p.getData(), p.getOffset(), p.getLength());
                            socket.send(p);
                        }

                        if (finished) {
                            log(side, "Handshake status is FINISHED "
                                    + "after calling onReceiveTimeout(), "
                                    + "finish the loop");
                            endLoops = true;
                        }

                        log(side, "New handshake status is "
                                + engine.getHandshakeStatus());

                        continue;
                    }

                    iNet = ByteBuffer.wrap(buf, 0, packet.getLength());
                    iApp = ByteBuffer.allocate(BUFFER_SIZE);
                } else {
                    iNet = ByteBuffer.allocate(0);
                    iApp = ByteBuffer.allocate(BUFFER_SIZE);
                }

                SSLEngineResult r = engine.unwrap(iNet, iApp);
                SSLEngineResult.Status rs = r.getStatus();
                hs = r.getHandshakeStatus();
                if (rs == SSLEngineResult.Status.OK) {
                    // OK
                } else if (rs == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    log(side, "BUFFER_OVERFLOW, handshake status is " + hs);

                    // the client maximum fragment size config does not work?
                    throw new Exception("Buffer overflow: " +
                        "incorrect client maximum fragment size");
                } else if (rs == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    log(side, "BUFFER_UNDERFLOW, handshake status is " + hs);

                    // bad packet, or the client maximum fragment size
                    // config does not work?
                    if (hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                        throw new Exception("Buffer underflow: " +
                            "incorrect client maximum fragment size");
                    } // otherwise, ignore this packet
                } else if (rs == SSLEngineResult.Status.CLOSED) {
                    throw new Exception(
                            "SSL engine closed, handshake status is " + hs);
                } else {
                    throw new Exception("Can't reach here, result is " + rs);
                }

                if (hs == SSLEngineResult.HandshakeStatus.FINISHED) {
                    log(side, "Handshake status is FINISHED, finish the loop");
                    endLoops = true;
                }
            } else if (hs == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                List<DatagramPacket> packets = new ArrayList<>();
                boolean finished = produceHandshakePackets(
                    engine, peerAddr, side, packets);

                log(side, "Produced " + packets.size() + " packets");
                for (DatagramPacket p : packets) {
                    socket.send(p);
                }

                if (finished) {
                    log(side, "Handshake status is FINISHED "
                            + "after producing handshake packets, "
                            + "finish the loop");
                    endLoops = true;
                }
            } else if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                runDelegatedTasks(engine);
            } else if (hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                log(side,
                    "Handshake status is NOT_HANDSHAKING, finish the loop");
                endLoops = true;
            } else if (hs == SSLEngineResult.HandshakeStatus.FINISHED) {
                throw new Exception(
                        "Unexpected status, SSLEngine.getHandshakeStatus() "
                                + "shouldn't return FINISHED");
            } else {
                throw new Exception(
                        "Can't reach here, handshake status is " + hs);
            }
        }

        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
        log(side, "Handshake finished, status is " + hs);

        if (engine.getHandshakeSession() != null) {
            throw new Exception(
                    "Handshake finished, but handshake session is not null");
        }

        SSLSession session = engine.getSession();
        if (session == null) {
            throw new Exception("Handshake finished, but session is null");
        }
        log(side, "Negotiated protocol is " + session.getProtocol());
        log(side, "Negotiated cipher suite is " + session.getCipherSuite());

        // handshake status should be NOT_HANDSHAKING
        //
        // According to the spec, SSLEngine.getHandshakeStatus() can't
        // return FINISHED.
        if (hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            throw new Exception("Unexpected handshake status " + hs);
        }
    }

    // deliver application data
    void deliverAppData(SSLEngine engine, DatagramSocket socket,
            ByteBuffer appData, SocketAddress peerAddr) throws Exception {

        // Note: have not consider the packet loses
        List<DatagramPacket> packets =
                produceApplicationPackets(engine, appData, peerAddr);
        appData.flip();
        for (DatagramPacket p : packets) {
            socket.send(p);
        }
    }

    // receive application data
    void receiveAppData(SSLEngine engine,
            DatagramSocket socket, ByteBuffer expectedApp) throws Exception {

        int loops = MAX_APP_READ_LOOPS;
        while ((serverException == null) && (clientException == null)) {
            if (--loops < 0) {
                throw new RuntimeException(
                        "Too much loops to receive application data");
            }

            byte[] buf = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            ByteBuffer netBuffer = ByteBuffer.wrap(buf, 0, packet.getLength());
            ByteBuffer recBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            SSLEngineResult rs = engine.unwrap(netBuffer, recBuffer);
            recBuffer.flip();
            if (recBuffer.remaining() != 0) {
                printHex("Received application data", recBuffer);
                if (!recBuffer.equals(expectedApp)) {
                    System.out.println("Engine status is " + rs);
                    throw new Exception("Not the right application data");
                }
                break;
            }
        }
    }

    // produce handshake packets
    boolean produceHandshakePackets(SSLEngine engine, SocketAddress socketAddr,
            String side, List<DatagramPacket> packets) throws Exception {

        boolean endLoops = false;
        int loops = MAX_HANDSHAKE_LOOPS / 2;
        while (!endLoops &&
                (serverException == null) && (clientException == null)) {

            if (--loops < 0) {
                throw new RuntimeException(
                        "Too much loops to produce handshake packets");
            }

            ByteBuffer oNet = ByteBuffer.allocate(32768);
            ByteBuffer oApp = ByteBuffer.allocate(0);
            SSLEngineResult r = engine.wrap(oApp, oNet);
            oNet.flip();

            SSLEngineResult.Status rs = r.getStatus();
            SSLEngineResult.HandshakeStatus hs = r.getHandshakeStatus();
            log(side, "----produce handshake packet(" +
                    loops + ", " + rs + ", " + hs + ")----");
            if (rs == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                // the client maximum fragment size config does not work?
                throw new Exception("Buffer overflow: " +
                            "incorrect server maximum fragment size");
            } else if (rs == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                log(side,
                        "Produce handshake packets: BUFFER_UNDERFLOW occured");
                log(side,
                        "Produce handshake packets: Handshake status: " + hs);
                // bad packet, or the client maximum fragment size
                // config does not work?
                if (hs != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    throw new Exception("Buffer underflow: " +
                            "incorrect server maximum fragment size");
                } // otherwise, ignore this packet
            } else if (rs == SSLEngineResult.Status.CLOSED) {
                throw new Exception("SSLEngine has closed");
            } else if (rs == SSLEngineResult.Status.OK) {
                // OK
            } else {
                throw new Exception("Can't reach here, result is " + rs);
            }

            // SSLEngineResult.Status.OK:
            if (oNet.hasRemaining()) {
                byte[] ba = new byte[oNet.remaining()];
                oNet.get(ba);
                DatagramPacket packet = createHandshakePacket(ba, socketAddr);
                packets.add(packet);
            }

            if (hs == SSLEngineResult.HandshakeStatus.FINISHED) {
                log(side, "Produce handshake packets: "
                            + "Handshake status is FINISHED, finish the loop");
                return true;
            }

            boolean endInnerLoop = false;
            SSLEngineResult.HandshakeStatus nhs = hs;
            while (!endInnerLoop) {
                if (nhs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                    runDelegatedTasks(engine);
                } else if (nhs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP ||
                    nhs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN ||
                    nhs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

                    endInnerLoop = true;
                    endLoops = true;
                } else if (nhs == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    endInnerLoop = true;
                } else if (nhs == SSLEngineResult.HandshakeStatus.FINISHED) {
                    throw new Exception(
                            "Unexpected status, SSLEngine.getHandshakeStatus() "
                                    + "shouldn't return FINISHED");
                } else {
                    throw new Exception("Can't reach here, handshake status is "
                            + nhs);
                }
                nhs = engine.getHandshakeStatus();
            }
        }

        return false;
    }

    DatagramPacket createHandshakePacket(byte[] ba, SocketAddress socketAddr) {
        return new DatagramPacket(ba, ba.length, socketAddr);
    }

    // produce application packets
    List<DatagramPacket> produceApplicationPackets(
            SSLEngine engine, ByteBuffer source,
            SocketAddress socketAddr) throws Exception {

        List<DatagramPacket> packets = new ArrayList<>();
        ByteBuffer appNet = ByteBuffer.allocate(32768);
        SSLEngineResult r = engine.wrap(source, appNet);
        appNet.flip();

        SSLEngineResult.Status rs = r.getStatus();
        if (rs == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            // the client maximum fragment size config does not work?
            throw new Exception("Buffer overflow: " +
                        "incorrect server maximum fragment size");
        } else if (rs == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            // unlikely
            throw new Exception("Buffer underflow during wraping");
        } else if (rs == SSLEngineResult.Status.CLOSED) {
                throw new Exception("SSLEngine has closed");
        } else if (rs == SSLEngineResult.Status.OK) {
            // OK
        } else {
            throw new Exception("Can't reach here, result is " + rs);
        }

        // SSLEngineResult.Status.OK:
        if (appNet.hasRemaining()) {
            byte[] ba = new byte[appNet.remaining()];
            appNet.get(ba);
            DatagramPacket packet =
                    new DatagramPacket(ba, ba.length, socketAddr);
            packets.add(packet);
        }

        return packets;
    }

    // Get a datagram packet for the specified handshake type.
    static DatagramPacket getPacket(
            List<DatagramPacket> packets, byte handshakeType) {
        boolean matched = false;
        for (DatagramPacket packet : packets) {
            byte[] data = packet.getData();
            int offset = packet.getOffset();
            int length = packet.getLength();

            // Normally, this pakcet should be a handshake message
            // record.  However, even if the underlying platform
            // splits the record more, we don't really worry about
            // the improper packet loss because DTLS implementation
            // should be able to handle packet loss properly.
            //
            // See RFC 6347 for the detailed format of DTLS records.
            if (handshakeType == -1) {      // ChangeCipherSpec
                // Is it a ChangeCipherSpec message?
                matched = (length == 14) && (data[offset] == 0x14);
            } else if ((length >= 25) &&    // 25: handshake mini size
                (data[offset] == 0x16)) {   // a handshake message

                // check epoch number for initial handshake only
                if (data[offset + 3] == 0x00) {     // 3,4: epoch
                    if (data[offset + 4] == 0x00) { // plaintext
                        matched =
                            (data[offset + 13] == handshakeType);
                    } else {                        // cipherext
                        // The 1st ciphertext is a Finished message.
                        //
                        // If it is not proposed to loss the Finished
                        // message, it is not necessary to check the
                        // following packets any mroe as a Finished
                        // message is the last handshake message.
                        matched = (handshakeType == 20);
                    }
                }
            }

            if (matched) {
                return packet;
            }
        }

        return null;
    }

    // run delegated tasks
    void runDelegatedTasks(SSLEngine engine) throws Exception {
        Runnable runnable;
        while ((runnable = engine.getDelegatedTask()) != null) {
            runnable.run();
        }

        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
        if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            throw new Exception("handshake shouldn't need additional tasks");
        }
    }

    // retransmission if timeout
    boolean onReceiveTimeout(SSLEngine engine, SocketAddress socketAddr,
            String side, List<DatagramPacket> packets) throws Exception {

        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
        if (hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            return false;
        } else {
            // retransmission of handshake messages
            return produceHandshakePackets(engine, socketAddr, side, packets);
        }
    }

    // get DTSL context
    SSLContext getDTLSContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = "passphrase".toCharArray();

        try (FileInputStream fis = new FileInputStream(keyFilename)) {
            ks.load(fis, passphrase);
        }

        try (FileInputStream fis = new FileInputStream(trustFilename)) {
            ts.load(fis, passphrase);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslCtx = SSLContext.getInstance("DTLS");

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslCtx;
    }


    /*
     * =============================================================
     * The remainder is support stuff to kickstart the testing.
     */

    // Will the handshaking and application data exchange succeed?
    public boolean isGoodJob() {
        return true;
    }

    public final void runTest(DTLSOverDatagram testCase) throws Exception {
        try (DatagramSocket serverSocket = new DatagramSocket();
                DatagramSocket clientSocket = new DatagramSocket()) {

            serverSocket.setSoTimeout(SOCKET_TIMEOUT);
            clientSocket.setSoTimeout(SOCKET_TIMEOUT);

            InetSocketAddress serverSocketAddr = new InetSocketAddress(
                    InetAddress.getLocalHost(), serverSocket.getLocalPort());

            InetSocketAddress clientSocketAddr = new InetSocketAddress(
                    InetAddress.getLocalHost(), clientSocket.getLocalPort());

            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<String> server, client;

            try {
                server = pool.submit(new ServerCallable(
                        testCase, serverSocket, clientSocketAddr));
                client = pool.submit(new ClientCallable(
                        testCase, clientSocket, serverSocketAddr));
            } finally {
                pool.shutdown();
            }

            boolean failed = false;

            // wait for client to finish
            try {
                System.out.println("Client finished: " + client.get());
            } catch (CancellationException | InterruptedException
                        | ExecutionException e) {
                System.out.println("Exception on client side: ");
                e.printStackTrace(System.out);
                failed = true;
            }

            // wait for server to finish
            try {
                System.out.println("Client finished: " + server.get());
            } catch (CancellationException | InterruptedException
                        | ExecutionException e) {
                System.out.println("Exception on server side: ");
                e.printStackTrace(System.out);
                failed = true;
            }

            if (failed) {
                throw new RuntimeException("Test failed");
            }
        }
    }

    final static class ServerCallable implements Callable<String> {

        private final DTLSOverDatagram testCase;
        private final DatagramSocket socket;
        private final InetSocketAddress clientSocketAddr;

        ServerCallable(DTLSOverDatagram testCase, DatagramSocket socket,
                InetSocketAddress clientSocketAddr) {

            this.testCase = testCase;
            this.socket = socket;
            this.clientSocketAddr = clientSocketAddr;
        }

        @Override
        public String call() throws Exception {
            try {
                testCase.doServerSide(socket, clientSocketAddr);
            } catch (Exception e) {
                System.out.println("Exception in  ServerCallable.call():");
                e.printStackTrace(System.out);
                serverException = e;

                if (testCase.isGoodJob()) {
                    throw e;
                } else {
                    return "Well done, server!";
                }
            }

            if (testCase.isGoodJob()) {
                return "Well done, server!";
            } else {
                throw new Exception("No expected exception");
            }
        }
    }

    final static class ClientCallable implements Callable<String> {

        private final DTLSOverDatagram testCase;
        private final DatagramSocket socket;
        private final InetSocketAddress serverSocketAddr;

        ClientCallable(DTLSOverDatagram testCase, DatagramSocket socket,
                InetSocketAddress serverSocketAddr) {

            this.testCase = testCase;
            this.socket = socket;
            this.serverSocketAddr = serverSocketAddr;
        }

        @Override
        public String call() throws Exception {
            try {
                testCase.doClientSide(socket, serverSocketAddr);
            } catch (Exception e) {
                System.out.println("Exception in ClientCallable.call():");
                e.printStackTrace(System.out);
                clientException = e;

                if (testCase.isGoodJob()) {
                    throw e;
                } else {
                    return "Well done, client!";
                }
            }

            if (testCase.isGoodJob()) {
                return "Well done, client!";
            } else {
                throw new Exception("No expected exception");
            }
        }
    }

    final static void printHex(String prefix, ByteBuffer bb) {
        HexDumpEncoder  dump = new HexDumpEncoder();

        synchronized (System.out) {
            System.out.println(prefix);
            try {
                dump.encodeBuffer(bb.slice(), System.out);
            } catch (Exception e) {
                // ignore
            }
            System.out.flush();
        }
    }

    final static void printHex(String prefix,
            byte[] bytes, int offset, int length) {

        HexDumpEncoder  dump = new HexDumpEncoder();

        synchronized (System.out) {
            System.out.println(prefix);
            try {
                ByteBuffer bb = ByteBuffer.wrap(bytes, offset, length);
                dump.encodeBuffer(bb, System.out);
            } catch (Exception e) {
                // ignore
            }
            System.out.flush();
        }
    }

    static void log(String side, String message) {
        System.out.println(side + ": " + message);
    }
}
