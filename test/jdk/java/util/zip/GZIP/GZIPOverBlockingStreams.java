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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @summary Verifies that the GZIPInputStream works as expected when the underlying
 *          InputStream is a blocking stream
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder jdk.test.lib.RandomFactory
 * @modules java.base/java.util.zip:open
 * @run junit GZIPOverBlockingStreams
 * @comment verify it behaves the same when jdk.util.gzip.tryReadAheadAfterTrailer system property
 *          is set to false
 * @run junit/othervm -Djdk.util.gzip.tryReadAheadAfterTrailer=false GZIPOverBlockingStreams
 */
class GZIPOverBlockingStreams {

    private static final Random random = RandomFactory.getRandom();
    private static final String MEMBER_CONTENT_FORMAT = "Hello member %d, foo bar hello world\n";
    private static final ExecutorService httpServerExecutor = Executors.newCachedThreadPool();

    private static Server nonHttpServer;
    private static HttpServer httpServer;

    // 'GZIPInputStream.alwaysReadNextMember' configures whether GZIPInputStream skips the call to
    // 'InputStream.available()' when checking for additional GZIP members in a stream. Tests which
    // specifically test the non-blocking behavior (i.e. for the 'alwaysReadNextMember=false' case)
    // have to be skipped if 'GZIPInputStream.alwaysReadNextMember' is 'true'.
    static boolean alwaysReadNextMember = false;
    static {
        try {
            Field alwaysReadNextMemberField = GZIPInputStream.class.getDeclaredField("alwaysReadNextMember");
            alwaysReadNextMemberField.setAccessible(true);
            alwaysReadNextMember = alwaysReadNextMemberField.getBoolean(null);
        } catch (Exception e) {
            System.out.println("Warning: can't get value of 'GZIPInputStream.alwaysReadNextMember'");
            e.printStackTrace(System.out);
        }
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        // create a socket based (non-HTTP) server
        nonHttpServer = new Server();
        nonHttpServer.start();
        System.err.println("(non-HTTP) server started at " + nonHttpServer.getAddress());

        // create a HTTP server
        final InetAddress loopback = InetAddress.getLoopbackAddress();
        final InetSocketAddress serverAddr = new InetSocketAddress(loopback, 0);
        httpServer = HttpServer.create(serverAddr, 0);
        httpServer.setExecutor(httpServerExecutor);
        httpServer.createContext("/", new HttpReqHandler());
        httpServer.start();
        System.err.println("started HTTP server at " + httpServer.getAddress());

    }

    @AfterAll
    static void afterAll() throws Exception {
        if (nonHttpServer != null) {
            System.err.println("stopping server " + nonHttpServer.getAddress());
            nonHttpServer.close();
        }
        if (httpServer != null) {
            System.err.println("stopping HTTP server " + httpServer.getAddress());
            httpServer.stop(0);
        }
        httpServerExecutor.shutdownNow();
    }

    static List<Integer> numGZIPMembers() {
        return List.of(1,
                13,
                42,
                random.nextInt(2, 101) // a reasonable number of members, not too many
        );
    }

    static List<Arguments> socketStreamTestArgs() {
        final List<Arguments> args = new ArrayList<>();
        final List<Integer> numMembers = numGZIPMembers();
        for (boolean shouldCloseSocket : new boolean[]{true, alwaysReadNextMember ? true : false}) {
            for (int n : numMembers) {
                args.add(Arguments.of(n, shouldCloseSocket));
            }
        }
        return args;
    }

    /*
     * Verifies that when the GZIPInputStream is used to read GZIP content
     * over a socket stream, it does not block when reading past a member trailer to determine
     * the presence of a subsequent member.
     */
    @ParameterizedTest
    @MethodSource("socketStreamTestArgs")
    void testSocketStream(final int numMembers, final boolean shouldCloseSocket) throws Exception {
        final InetSocketAddress serverAddr = nonHttpServer.getAddress();
        try (final Socket socket = new Socket(serverAddr.getAddress(), serverAddr.getPort())) {
            System.err.println("connect established " + socket);
            try (final OutputStream os = socket.getOutputStream();
                 final DataOutputStream dos = new DataOutputStream(os)) {
                // instruct the server side the number of GZIP members we want in the response
                dos.writeInt(numMembers);
                // instruct the server side whether to close the socket after writing out the
                // response
                dos.writeBoolean(shouldCloseSocket);
                System.err.println("sent request for GZIP stream with " + numMembers + " members");
                // read the response
                try (final InputStream in = socket.getInputStream();
                     final GZIPInputStream gzipInputStream = new GZIPInputStream(in)) {
                    final byte[] decompressed = gzipInputStream.readAllBytes();
                    System.err.println("read " + decompressed.length
                            + " bytes of decompressed response");
                    // verify it's the expected content
                    assertDecompressedContent(numMembers, decompressed);
                }
            }
        }
        final Throwable serverFailure = nonHttpServer.failure;
        if (serverFailure != null) {
            fail("Server ran into an error", serverFailure);
        }
    }

    static List<Arguments> httpTestArgs() {
        final List<Arguments> args = new ArrayList<>();
        final List<Integer> numMembers = numGZIPMembers();
        for (boolean chunkedOrNot : new boolean[]{alwaysReadNextMember ? false : true, false}) {
            for (int n : numMembers) {
                args.add(Arguments.of(n, chunkedOrNot));
            }
        }
        return args;
    }

    /*
     * Verifies that when the GZIPInputStream is used to read GZIP content,
     * over a stream obtained from a HTTP response, it does not block when reading past a member
     * trailer to determine the presence of a subsequent member.
     */
    @ParameterizedTest
    @MethodSource("httpTestArgs")
    void testHttpStream(final int numMembers, final boolean httpResponseChunked) throws Exception {
        final URI reqURI = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(httpServer.getAddress().getPort())
                .path("/")
                .build();
        final HttpURLConnection conn = (HttpURLConnection) reqURI.toURL().openConnection();
        conn.setRequestProperty("numMembers", String.valueOf(numMembers));
        conn.setRequestProperty("chunkedResponse", String.valueOf(httpResponseChunked));
        System.err.println("issuing request " + reqURI);
        try (final InputStream in = conn.getInputStream();
             final GZIPInputStream gzipInputStream = new GZIPInputStream(in)) {
            final byte[] decompressed = gzipInputStream.readAllBytes();
            System.err.println("read " + decompressed.length
                    + " bytes of decompressed response");
            assertDecompressedContent(numMembers, decompressed);
        }
    }

    /*
     * Creates and returns bytes representing a GZIP stream consisting of the given number of
     * members.
     */
    private static byte[] createGZIPStream(final int numMembers) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 1; i <= numMembers; i++) {
            final ByteArrayOutputStream member = new ByteArrayOutputStream();
            try (final OutputStream gzip = new GZIPOutputStream(member)) {
                final String memberContent = String.format(MEMBER_CONTENT_FORMAT, i);
                gzip.write(memberContent.getBytes(US_ASCII));
            }
            // write out the GZIP member to the stream which accumulates all the members
            baos.write(member.toByteArray());
        }
        return baos.toByteArray();
    }

    /*
     * Verifies that the given decompressed bytes, representing the given number of
     * GZIP members, do match the expected content.
     */
    private static void assertDecompressedContent(final int numMembers,
                                                  final byte[] decompressed) {
        final String actual = new String(decompressed, US_ASCII);
        final StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= numMembers; i++) {
            sb.append(String.format(MEMBER_CONTENT_FORMAT, i));
        }
        final String expected = sb.toString();
        assertEquals(expected, actual, "unexpected decompressed content");
    }

    /*
     * A server which communicates over a socket to receive a request consisting of an integer
     * representing the number of GZIP members to respond with. The server then responds back
     * on the socket's OutputStream with GZIP content representing those many members.
     */
    private static final class Server implements AutoCloseable, Runnable {
        private final ServerSocket serverSocket;
        private volatile boolean stop;
        private volatile Throwable failure;

        private Server() throws IOException {
            this.serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        }

        private InetSocketAddress getAddress() {
            return (InetSocketAddress) this.serverSocket.getLocalSocketAddress();
        }

        @Override
        public void close() throws IOException {
            this.stop = true;
            System.err.println("closing server: " + this.serverSocket);
            this.serverSocket.close();
        }

        private void start() {
            final Thread t = new Thread(this);
            t.setName("server");
            t.setDaemon(true);
            t.start();
        }

        private synchronized void recordServerFailure(final Throwable t) {
            Throwable previous = this.failure;
            if (previous != null && previous != t) {
                previous.addSuppressed(t);
                return;
            }
            this.failure = t;
        }

        @Override
        public void run() {
            System.err.println("server started accepting requests at " + this.serverSocket);
            try {
                doRun();
            } catch (Throwable t) {
                if (!stop) { // ignore failures if the server is stopped
                    recordServerFailure(t);
                    System.err.println("server ran into error: " + t);
                    t.printStackTrace();
                }
            } finally {
                try {
                    this.close();
                } catch (IOException ioe) {
                    System.err.println("ignoring excpetion " +
                            "that happened during closing server: " + ioe);
                    ioe.printStackTrace();
                }
            }
        }

        private void doRun() throws Exception {
            while (!this.stop) {
                // we intentionally do not close the Socket. It's upto the
                // sendGZIPResponse(...) method to do that only if the test
                // request has instructed it to do so. This allows the test
                // method to exercise the case where the socket is open
                // but doesn't have any more data to send (and thus read() blocks)
                final Socket socket = this.serverSocket.accept();
                System.err.println("accepted connection from " + socket);
                // handle the request on a separate thread
                final Thread handler = new Thread(() -> {
                    try {
                        handleRequest(socket);
                    } catch (Throwable t) {
                        // keep track of the failure
                        recordServerFailure(t);
                        System.err.println("failure when handling request on socket "
                                + socket + ", exception: " + t);
                        t.printStackTrace();
                    }
                });
                handler.setName("request-handler-" + socket.getRemoteSocketAddress());
                handler.setDaemon(true);
                handler.start();
            }
        }

        private static void handleRequest(final Socket socket) throws IOException {
            final int numMembers;
            final boolean shouldCloseSocket;
            try {
                final InputStream in = socket.getInputStream();
                final DataInputStream dis = new DataInputStream(in);
                // read the socket's inputstream to determine how many GZIP members are
                // expected in the response stream, by the client
                numMembers = dis.readInt();
                // whether the socket should be closed after writing out the response
                shouldCloseSocket = dis.readBoolean();
            } catch (IOException ioe) {
                // could be a socket connection from an unexpected client, so ignore any
                // failure when reading the request
                System.err.println("Ignoring exception that happened when reading" +
                        " request from client socket " + socket + ", exception: " + ioe);
                ioe.printStackTrace();
                // close the unexpected client connection
                socket.close();
                return;
            }
            // valid request, respond to it with a GZIP response
            sendGZIPResponse(socket, numMembers, shouldCloseSocket);
        }

        /*
         * Sends GZIP content over the socket's OutputStream. This method closes the socket
         * only if the test request (read over the socket's InputStream) instructs it to do so.
         */
        private static void sendGZIPResponse(final Socket socket, final int numMembers,
                                             final boolean shouldCloseSocket) throws IOException {
            // respond back with a GZIP output, containing the expected number of members
            final byte[] gzipResponse = createGZIPStream(numMembers);
            System.err.println("responding to " + socket + " with a GZIP stream of size "
                    + gzipResponse.length + " with " + numMembers + " members");
            final OutputStream os = socket.getOutputStream();
            os.write(gzipResponse);
            System.err.println("done responding to " + socket);
            // close the socket only if the test request wants us to
            if (shouldCloseSocket) {
                System.err.println("closing " + socket);
                socket.close();
            }
        }
    }

    /*
     * A HTTP request handler which responds back with chunked or non-chunked
     * response containing GZIP content.
     */
    private static final class HttpReqHandler implements HttpHandler {

        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final URI reqURI = exchange.getRequestURI();
            System.err.println("handling request: " + reqURI + " from "
                    + exchange.getRemoteAddress());

            final String val = exchange.getRequestHeaders().getFirst("numMembers");
            final int numMembers = Integer.parseInt(val);
            final boolean respChunked = Boolean.parseBoolean(
                    exchange.getRequestHeaders().getFirst("chunkedResponse"));

            final byte[] gzipResponse = createGZIPStream(numMembers);
            System.err.println("responding to " + reqURI
                    + " with a GZIP stream of size " + gzipResponse.length
                    + " with " + numMembers + " members"
                    + " with chunked response = " + respChunked);

            // drain the inputstream and write out the response
            exchange.getRequestBody().readAllBytes();
            if (respChunked) {
                exchange.sendResponseHeaders(200, 0); // 0 = Chunked response
            } else {
                exchange.sendResponseHeaders(200, gzipResponse.length);
            }
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(gzipResponse);
            }
            System.err.println("done responding to " + reqURI);
        }
    }
}
