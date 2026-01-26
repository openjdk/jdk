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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import jdk.httpclient.test.lib.common.TestUtil;
import jdk.httpclient.test.lib.quic.ClientConnection;
import jdk.httpclient.test.lib.quic.ConnectedBidiStream;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.httpclient.test.lib.quic.QuicServerHandler;
import jdk.httpclient.test.lib.quic.QuicStandaloneServer;
import jdk.internal.net.http.quic.QuicClient;
import jdk.internal.net.http.quic.QuicConnection;
import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import sun.security.ssl.QuicTLSEngineImpl;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @summary verifies the QUIC TLS key update process
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @modules java.net.http/jdk.internal.net.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.packets
 *          java.net.http/jdk.internal.net.http.quic.frames
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *
 * @modules java.base/jdk.internal.util
 *          java.base/sun.security.ssl
 * @build jdk.httpclient.test.lib.quic.QuicStandaloneServer
 *        jdk.httpclient.test.lib.quic.ClientConnection
 *        jdk.httpclient.test.lib.common.TestUtil
 *        jdk.test.lib.net.SimpleSSLContext
 * @comment the test is run with -Djava.security.properties=<URL> to augment
 *          the master java.security file
 * @run testng/othervm -Djava.security.properties=${test.src}/quic-tls-keylimits-java.security
 *                     -Djdk.internal.httpclient.debug=true
 *                     -Djavax.net.debug=all
 *                     KeyUpdateTest
 */
public class KeyUpdateTest {

    private QuicStandaloneServer server;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private ExecutorService executor;

    private static final byte[] HELLO_MSG = "Hello Quic".getBytes(StandardCharsets.UTF_8);
    private static final EchoHandler handler = new EchoHandler(HELLO_MSG.length);

    @BeforeClass
    public void beforeClass() throws Exception {
        executor = Executors.newCachedThreadPool();
        server = QuicStandaloneServer.newBuilder()
                .availableVersions(new QuicVersion[]{QuicVersion.QUIC_V1})
                .sslContext(sslContext)
                .build();
        // add a handler which deals with incoming connections
        server.addHandler(handler);
        server.start();
        System.out.println("Server started at " + server.getAddress());
    }

    @AfterClass
    public void afterClass() throws Exception {
        if (server != null) {
            System.out.println("Stopping server " + server.getAddress());
            server.close();
        }
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

    @Test
    public void test() throws Exception {
        try (final QuicClient client = createClient()) {
            // create a QUIC connection to the server
            final ClientConnection conn = ClientConnection.establishConnection(client,
                    server.getAddress());
            final Stack<Integer> clientConnKeyPhases = new Stack<>();
            for (int i = 1; i <= 100; i++) {
                System.out.println("Iteration: " + i);
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
                // keep track of the 1-RTT key phase that was used by the client connection
                final int invocation = i;
                getKeyPhase(conn.underlyingQuicConnection()).ifPresent((keyPhase) -> {
                    if (clientConnKeyPhases.empty() || clientConnKeyPhases.peek() != keyPhase) {
                        // new key phase detected, add it
                        clientConnKeyPhases.push(keyPhase);
                        System.out.println("Detected client 1-RTT key phase " + keyPhase
                                + " on connection " + conn + " for invocation " + invocation);
                    }
                });
            }
            // verify that the client and server did do a key update
            // stacks should contain at least a sequence of 0, 1, 0
            System.out.println("Number of 1-RTT keys used by client connection: "
                    + clientConnKeyPhases.size() + ", key phase switches: " + clientConnKeyPhases);
            System.out.println("Number of 1-RTT keys used by server connection: "
                    + handler.serverConnKeyPhases.size() + ", key phase switches: "
                    + handler.serverConnKeyPhases);
            assertTrue(clientConnKeyPhases.size() >= 3, "Client connection" +
                    " didn't do a key update");
            assertTrue(handler.serverConnKeyPhases.size() >= 3, "Server connection" +
                    " didn't do a key update");

            assertEquals(0, (int) clientConnKeyPhases.getFirst(), "Client connection used" +
                    " unexpected first key phase");
            assertEquals(0, (int) handler.serverConnKeyPhases.getFirst(), "Server connection used" +
                    " unexpected first key phase");

            assertEquals(1, (int) clientConnKeyPhases.get(1), "Client connection used" +
                    " unexpected second key phase");
            assertEquals(1, (int) handler.serverConnKeyPhases.get(1), "Server connection used" +
                    " unexpected second key phase");

            assertEquals(0, (int) clientConnKeyPhases.get(2), "Client connection used" +
                    " unexpected third key phase");
            assertEquals(0, (int) handler.serverConnKeyPhases.get(2), "Server connection used" +
                    " unexpected third key phase");
        }
    }

    /**
     * Reads data from incoming client initiated bidirectional stream of a Quic connection
     * and writes back a response which is same as the read data
     */
    private static final class EchoHandler implements QuicServerHandler {

        private final int numBytesToRead;
        private final AtomicInteger numInvocations = new AtomicInteger();
        private final Stack<Integer> serverConnKeyPhases = new Stack<>();

        private EchoHandler(final int numBytesToRead) {
            this.numBytesToRead = numBytesToRead;
        }

        @Override
        public void handleBidiStream(final QuicServerConnection conn,
                                     final ConnectedBidiStream bidiStream) throws IOException {
            final int invocation = numInvocations.incrementAndGet();
            System.out.println("Handling incoming bidi stream " + bidiStream
                    + " on connection " + conn);
            // keep track of the 1-RTT key phase that was used by the server connection
            getKeyPhase(conn).ifPresent((keyPhase) -> {
                if (this.serverConnKeyPhases.empty()
                        || this.serverConnKeyPhases.peek() != keyPhase) {
                    // new key phase detected, add it
                    this.serverConnKeyPhases.push(keyPhase);
                    System.out.println("Detected server 1-RTT key phase " + keyPhase
                            + " on connection " + conn + " for invocation " + invocation);
                }
            });
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

    private static Optional<Integer> getKeyPhase(final QuicConnection conn) throws IOException {
        if (!(conn.getTLSEngine() instanceof QuicTLSEngineImpl qtls)) {
            return Optional.empty();
        }
        final int keyPhase;
        try {
            keyPhase = qtls.getOneRttKeyPhase();
        } catch (QuicKeyUnavailableException e) {
            throw new IOException("failed to get key phase, reason: " + e.getMessage());
        }
        if (keyPhase != 0 && keyPhase != 1) {
            throw new IOException("Unexpected 1-RTT key phase on connection: " + conn);
        }
        return Optional.of(keyPhase);
    }
}
