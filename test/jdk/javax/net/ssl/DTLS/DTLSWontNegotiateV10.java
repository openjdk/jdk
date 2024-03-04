/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.security.SecurityUtils;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * @test
 * @bug 8301381
 * @library /test/lib /javax/net/ssl/templates
 * @summary DTLSv10 is now disabled. This test verifies that the server will
 *     not negotiate a connection if the client asks for it.
 * @run main/othervm DTLSWontNegotiateV10 DTLS
 * @run main/othervm DTLSWontNegotiateV10 DTLSv1.0
 */
public class DTLSWontNegotiateV10 {

    private static final int MTU = 1024;
    private static final String DTLSV_1_0 = "DTLSv1.0";
    private static final String DTLS = "DTLS";
    private static final String DTLSV_1_2 = "DTLSv1.2";

    private static final int READ_TIMEOUT_SECS = Integer.getInteger("readtimeout", 30);

    public static void main(String[] args) throws Exception {
        if (args[0].equals(DTLSV_1_0)) {
            SecurityUtils.removeFromDisabledTlsAlgs(DTLSV_1_0);
        }

        if (args.length > 1) {
            // running in client child process
            // args: protocol server-port
            try (DTLSClient client = new DTLSClient(args[0], Integer.parseInt(args[1]))) {
                client.run();
            }

        } else {
            // server process
            // args: protocol
            final int totalAttempts = 5;
            int tries;
            for (tries = 0 ; tries < totalAttempts ; ++tries) {
                try {
                    System.out.printf("Starting server %d/%d attempts%n", tries+1, totalAttempts);
                    runServer(args[0]);
                    break;
                } catch (SocketTimeoutException exc) {
                    System.out.println("The server timed-out waiting for packets from the client.");
                }
            }
            if (tries == totalAttempts) {
                throw new RuntimeException("The server/client communications timed-out after " + totalAttempts + " tries.");
            }
        }
    }

    private static void runServer(String protocol) throws Exception {
        // args: protocol
        Process clientProcess = null;
        try (DTLSServer server = new DTLSServer(protocol)) {
            List<String> command = List.of(
                    "DTLSWontNegotiateV10",
                    // if server is "DTLS" then the client should be v1.0 and vice versa
                    protocol.equals(DTLS) ? DTLSV_1_0 : DTLS,
                    Integer.toString(server.getListeningPortNumber())
            );

            ProcessBuilder builder = ProcessTools.createTestJavaProcessBuilder(command);
            clientProcess = builder.inheritIO().start();
            server.run();
            System.out.println("Success: DTLSv1.0 connection was not established.");

        } finally {
            if (clientProcess != null) {
                clientProcess.destroy();
            }
        }
    }

    private static class DTLSClient extends DTLSEndpoint {
        private final int remotePort;

        private final DatagramSocket socket = new DatagramSocket();

        public DTLSClient(String protocol, int portNumber) throws Exception {
            super(true, protocol);
            remotePort = portNumber;
            socket.setSoTimeout(READ_TIMEOUT_SECS * 1000);
            log("Client listening on port " + socket.getLocalPort()
                    + ". Sending data to server port " + remotePort);
            log("Enabled protocols: " + String.join(" ", engine.getEnabledProtocols()));
        }

        @Override
        public void run() throws Exception {
            doHandshake(socket);
            log("Client done handshaking. Protocol: " + engine.getSession().getProtocol());
        }

        @Override
        void setRemotePortNumber(int portNumber) {
            // don't do anything; we're using the one we already know
        }

        @Override
        int getRemotePortNumber() {
            return remotePort;
        }

        @Override
        public void close () {
            socket.close();
        }
    }

    private abstract static class DTLSEndpoint extends SSLContextTemplate implements AutoCloseable {
        protected final SSLEngine engine;
        protected final SSLContext context;
        private final String protocol;
        protected final InetAddress LOCALHOST;

        private final String tag;

        public DTLSEndpoint(boolean useClientMode, String protocol) throws Exception {
            this.protocol = protocol;
            if (useClientMode) {
                tag = "client";
                context = createClientSSLContext();
            } else {
                tag = "server";
                context = createServerSSLContext();
            }
            engine = context.createSSLEngine();
            engine.setUseClientMode(useClientMode);
            SSLParameters params = engine.getSSLParameters();
            params.setMaximumPacketSize(MTU);
            engine.setSSLParameters(params);
            if (protocol.equals(DTLS)) {
                // make sure both versions are "enabled"; 1.0 should be
                // disabled by policy now and won't be negotiated.
                engine.setEnabledProtocols(new String[]{DTLSV_1_0, DTLSV_1_2});
            } else {
                engine.setEnabledProtocols(new String[]{DTLSV_1_0});
            }

            LOCALHOST = InetAddress.getByName("localhost");
        }

        @Override
        protected ContextParameters getServerContextParameters() {
            return new ContextParameters(protocol, "PKIX", "NewSunX509");
        }

        @Override
        protected ContextParameters getClientContextParameters() {
            return new ContextParameters(protocol, "PKIX", "NewSunX509");
        }


        abstract void setRemotePortNumber(int portNumber);

        abstract int getRemotePortNumber();

        abstract void run() throws Exception;

        private boolean runDelegatedTasks() {
            log("Running delegated tasks.");
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                runnable.run();
            }

            SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
            if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw new RuntimeException(
                        "Handshake shouldn't need additional tasks");
            }

            return true;
        }

        protected void doHandshake(DatagramSocket socket) throws Exception {
            boolean handshaking = true;
            engine.beginHandshake();
            while (handshaking) {
                log("Handshake status = " + engine.getHandshakeStatus());
                handshaking = switch (engine.getHandshakeStatus()) {
                    case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> readFromServer(socket);
                    case NEED_WRAP -> sendHandshakePackets(socket);
                    case NEED_TASK -> runDelegatedTasks();
                    case NOT_HANDSHAKING, FINISHED -> false;
                };
            }
        }

        private boolean readFromServer(DatagramSocket socket) throws IOException {
            log("Reading data from remote endpoint.");
            ByteBuffer iNet, iApp;
            if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                byte[] buffer = new byte[MTU];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                setRemotePortNumber(packet.getPort());
                iNet = ByteBuffer.wrap(buffer, 0, packet.getLength());
                iApp = ByteBuffer.allocate(MTU);
            } else {
                iNet = ByteBuffer.allocate(0);
                iApp = ByteBuffer.allocate(MTU);
            }

            SSLEngineResult engineResult;
            do {
                engineResult = engine.unwrap(iNet, iApp);
            } while (iNet.hasRemaining());

            return switch (engineResult.getStatus()) {
                case CLOSED -> false;
                case OK -> true;
                case BUFFER_OVERFLOW -> throw new RuntimeException("Buffer overflow: "
                        + "incorrect server maximum fragment size");
                case BUFFER_UNDERFLOW -> throw new RuntimeException("Buffer underflow: "
                        + "incorrect server maximum fragment size");
            };
        }

        private boolean sendHandshakePackets(DatagramSocket socket) throws Exception {
            List<DatagramPacket> packets = generateHandshakePackets();
            log("Sending handshake packets.");
            packets.forEach((p) -> {
                try {
                    socket.send(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            return true;
        }

        private List<DatagramPacket> generateHandshakePackets() throws SSLException {
            log("Generating handshake packets.");
            List<DatagramPacket> packets = new ArrayList<>();
            ByteBuffer oNet = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            ByteBuffer oApp = ByteBuffer.allocate(0);

            while (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                SSLEngineResult result = engine.wrap(oApp, oNet);
                oNet.flip();

                switch (result.getStatus()) {
                    case BUFFER_UNDERFLOW -> {
                        if (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                            throw new RuntimeException("Buffer underflow: "
                                    + "incorrect server maximum fragment size");
                        }
                    }
                    case BUFFER_OVERFLOW -> throw new RuntimeException("Buffer overflow: "
                            + "incorrect server maximum fragment size");
                    case CLOSED -> throw new RuntimeException("SSLEngine has closed");
                }

                if (oNet.hasRemaining()) {
                    byte[] packetBuffer = new byte[oNet.remaining()];
                    oNet.get(packetBuffer);
                    packets.add(new DatagramPacket(packetBuffer, packetBuffer.length,
                            LOCALHOST, getRemotePortNumber()));
                }

                runDelegatedTasks();
                oNet.clear();
            }

            log("Generated " + packets.size() + " packets.");
            return packets;
        }

        protected void log(String msg) {
            System.out.println(tag + ": " + msg);
        }
    }

    private static class DTLSServer extends DTLSEndpoint implements AutoCloseable {

        private final AtomicInteger portNumber = new AtomicInteger(0);
        private final DatagramSocket socket = new DatagramSocket(0);

        public DTLSServer(String protocol) throws Exception {
            super(false, protocol);
            socket.setSoTimeout(READ_TIMEOUT_SECS * 1000);
            log("Server listening on port: " + socket.getLocalPort());
            log("Enabled protocols: " + String.join(" ", engine.getEnabledProtocols()));
        }

        @Override
        public void run() throws Exception {
            doHandshake(socket);
            if (!engine.getSession().getProtocol().equals("NONE")) {
                throw new RuntimeException("Negotiated protocol: "
                        + engine.getSession().getProtocol() +
                        ". No protocol should be negotated.");
            }
        }

        public int getListeningPortNumber() {
            return socket.getLocalPort();
        }

        void setRemotePortNumber(int portNumber) {
            this.portNumber.compareAndSet(0, portNumber);
        }

        int getRemotePortNumber() {
            return portNumber.get();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}