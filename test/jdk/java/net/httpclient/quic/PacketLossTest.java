/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import jdk.httpclient.test.lib.common.TestUtil;
import jdk.httpclient.test.lib.quic.ClientConnection;
import jdk.httpclient.test.lib.quic.ConnectedBidiStream;
import jdk.httpclient.test.lib.quic.DatagramDeliveryPolicy;
import jdk.httpclient.test.lib.quic.QuicServer;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.httpclient.test.lib.quic.QuicServerHandler;
import jdk.httpclient.test.lib.quic.QuicStandaloneServer;
import jdk.internal.net.http.quic.QuicClient;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @summary Verifies QUIC client interaction against servers which exhibit packet loss
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.quic.QuicStandaloneServer
 *        jdk.httpclient.test.lib.quic.ClientConnection
 *        jdk.httpclient.test.lib.common.TestUtil
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit/othervm/timeout=240 -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.quic.minPtoBackoffTime=60
 *                     -Djdk.httpclient.quic.maxPtoBackoffTime=10
 *                     -Djdk.httpclient.quic.maxPtoBackoff=9
 *                     -Djdk.httpclient.HttpClient.log=quic,errors PacketLossTest
 */
public class PacketLossTest {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static ExecutorService executor;

    private static final byte[] HELLO_MSG = "Hello Quic".getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    public static void beforeAll() throws Exception {
        executor = Executors.newCachedThreadPool();
    }

    @AfterAll
    public static void afterAll() throws Exception {
        if (executor != null) {
            executor.close();
        }
    }

    private QuicClient createClient() {
        var versions = List.of(QuicVersion.QUIC_V1);
        var context = new QuicTLSContext(sslContext);
        var params = new SSLParameters();
        return new QuicClient.Builder()
                .availableVersions(versions)
                .tlsContext(context)
                .sslParameters(params)
                .executor(executor)
                .bindAddress(TestUtil.chooseClientBindAddress().orElse(null))
                .build();
    }

    sealed interface DropPolicy {
        final record DropRandomly() implements DropPolicy {}
        final record DropEveryNth(int n) implements DropPolicy {}
        final record DropNone() implements DropPolicy {}
        final record DropAll() implements DropPolicy {}
        default DatagramDeliveryPolicy policy() {
            if (this instanceof DropRandomly) {
                return DatagramDeliveryPolicy.dropRandomly();
            } else if (this instanceof DropEveryNth en) {
                return DatagramDeliveryPolicy.dropEveryNth(en.n());
            } else if (this instanceof DropNone) {
                return DatagramDeliveryPolicy.alwaysDeliver();
            } else if (this instanceof DropAll) {
                return DatagramDeliveryPolicy.neverDeliver();
            }
            throw new IllegalStateException("Unknown policy: " + this);
        }
    }

    record DropServer(String name, QuicStandaloneServer server) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            server.close();
        }
        public InetSocketAddress getAddress() {
            return server.getAddress();
        }
    }

    DropServer of(QuicServer.Builder<QuicStandaloneServer> builder, DropPolicy incomimg, DropPolicy outgoing)
            throws IOException {
        QuicStandaloneServer server = builder
                .incomingDeliveryPolicy(incomimg.policy())
                .outgoingDeliveryPolicy(outgoing.policy())
                .build();
        String name = "DropServer(%s, in: %s, out: %s)".formatted(server.name(), incomimg, outgoing);
        return new DropServer(name, server);
    }

    DropPolicy dropEveryNth(int n) {
        return new DropPolicy.DropEveryNth(n);
    }
    DropPolicy dropRandomly() {
        return new DropPolicy.DropRandomly();
    }

    // returns a List of unstarted Quic servers configured with different incoming/outgoing
    // datagram delivery policies
    private List<DropServer> unstartedServers() throws Exception {
        final QuicServer.Builder<QuicStandaloneServer> builder = QuicStandaloneServer.newBuilder()
                .availableVersions(new QuicVersion[]{QuicVersion.QUIC_V1})
                .sslContext(sslContext);
        final List<DropServer> servers = new ArrayList<>();
        servers.add(of(builder, dropEveryNth(3), dropEveryNth(7)));
        servers.add(of(builder, dropRandomly(), dropRandomly()));
        servers.add(of(builder, dropEveryNth(5), dropRandomly()));
        return servers;
    }

    private static void startServer(final QuicStandaloneServer server) throws IOException {
        // add a handler which deals with incoming connections
        server.addHandler(new EchoHandler(HELLO_MSG.length));
        server.start();
        System.out.println("Server " + server.name() + " started at " + server.getAddress());
    }

    /**
     * Uses {@link QuicClient} to pass data and expect back the data to/from Quic servers which
     * might drop incoming/outgoing packets.
     */
    @Test
    public void testDataTransfer() throws Exception {
        for (final DropServer server : unstartedServers()) {
            startServer(server.server());
            try (server) {
                System.out.printf("%n%n===== %s =====%n%n", server.name());
                System.err.printf("%n%n===== %s =====%n%n", server.name());
                final int numTimes = 20;
                try (final QuicClient client = createClient()) {
                    final InetSocketAddress serverAddr = server.getAddress();
                    // create a QUIC connection to the server
                    final ClientConnection conn = ClientConnection.establishConnection(client, serverAddr);
                    for (int i = 1; i <= numTimes; i++) {
                        System.out.println("iteration " + i + " against server: " + server.name() +
                                ", server addr: " + serverAddr);
                        // open a bidi stream
                        final ConnectedBidiStream bidiStream = conn.initiateNewBidiStream();
                        // write data on the stream
                        try (final OutputStream os = bidiStream.outputStream()) {
                            os.write(HELLO_MSG);
                            System.out.println("client: Client wrote message to bidi stream's output stream");
                        }
                        // wait for response
                        try (final InputStream is = bidiStream.inputStream()) {
                            System.out.println("client: reading from bidi stream's input stream");
                            final byte[] data = is.readAllBytes();
                            System.out.println("client: Received response of size " + data.length);
                            final String response = new String(data, StandardCharsets.UTF_8);
                            // verify response
                            System.out.println("client: Response: " + response);
                            if (!Arrays.equals(response.getBytes(StandardCharsets.UTF_8), HELLO_MSG)) {
                                throw new AssertionError("Unexpected response: " + response);
                            }
                        } finally {
                            System.err.println("client: Closing bidi stream from test");
                            bidiStream.close();
                        }
                    }
                }
            }
        }
    }

    /**
     * Reads data from incoming client initiated bidirectional stream of a Quic connection
     * and writes back a response which is same as the read data
     */
    private static final class EchoHandler implements QuicServerHandler {

        private final int numBytesToRead;

        private EchoHandler(final int numBytesToRead) {
            this.numBytesToRead = numBytesToRead;
        }

        @Override
        public void handleBidiStream(final QuicServerConnection conn,
                                     final ConnectedBidiStream bidiStream) throws IOException {
            System.out.println("Handling incoming bidi stream " + bidiStream
                    + " on connection " + conn);
            final byte[] data;
            // read the request content
            try (final InputStream is = bidiStream.inputStream()) {
                System.out.println("Handler reading data from bidi stream's inputstream " + is);
                data = is.readAllBytes();
                System.out.println("Handler read " + data.length + " bytes of data");
            }
            if (data.length != numBytesToRead) {
                throw new IOException("Expected to read " + numBytesToRead
                        + " bytes but read only " + data.length + " bytes");
            }
            // write response
            try (final OutputStream os = bidiStream.outputStream()) {
                System.out.println("Handler writing data to bidi stream's outputstream " + os);
                os.write(data);
            }
            System.out.println("Handler invocation complete");
        }
    }
}
