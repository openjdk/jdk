/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies `HttpRequest.BodyPublishers::ofFileChannel`
 * @library /test/lib
 *          /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run junit FileChannelPublisherTest
 */

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.net.http.HttpResponse.BodyHandlers.ofInputStream;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileChannelPublisherTest {

    private static final String CLASS_NAME = FileChannelPublisherTest.class.getSimpleName();

    private static final Logger LOGGER = Utils.getDebugLogger(CLASS_NAME::toString, Utils.DEBUG);

    private static final int DEFAULT_BUFFER_SIZE = Utils.getBuffer().capacity();

    private static final SSLContext SSL_CONTEXT = SimpleSSLContext.findSSLContext();

    private static final HttpClient CLIENT = HttpClient.newBuilder().sslContext(SSL_CONTEXT).proxy(NO_PROXY).build();

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private static final ServerRequestPair
            HTTP1 = ServerRequestPair.of(Version.HTTP_1_1, false),
            HTTPS1 = ServerRequestPair.of(Version.HTTP_1_1, true),
            HTTP2 = ServerRequestPair.of(Version.HTTP_2, false),
            HTTPS2 = ServerRequestPair.of(Version.HTTP_2, true);

    private record ServerRequestPair(
            String serverName,
            HttpTestServer server,
            BlockingQueue<byte[]> serverReadRequestBodyBytes,
            HttpRequest.Builder requestBuilder,
            boolean secure) {

        private static CountDownLatch SERVER_REQUEST_RECEIVED_SIGNAL = null;

        private static CountDownLatch SERVER_READ_PERMISSION = null;

        private static ServerRequestPair of(Version version, boolean secure) {

            // Create the server
            SSLContext sslContext = secure ? SSL_CONTEXT : null;
            HttpTestServer server = createServer(version, sslContext);
            String serverName = secure ? version.toString().replaceFirst("_", "S_") : version.toString();

            // Add the handler
            String handlerPath = "/%s/".formatted(CLASS_NAME);
            BlockingQueue<byte[]> serverReadRequestBodyBytes =
                    addRequestBodyConsumingServerHandler(serverName, server, handlerPath);

            // Create the request builder
            String requestUriScheme = secure ? "https" : "http";
            // `x` suffix in the URI is not a typo, but ensures that *only* the parent handler path is matched
            URI requestUri = URI.create("%s://%s%sx".formatted(requestUriScheme, server.serverAuthority(), handlerPath));
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(requestUri).version(version);

            // Create the pair
            ServerRequestPair pair = new ServerRequestPair(serverName, server, serverReadRequestBodyBytes, requestBuilder, secure);
            pair.server.start();
            LOGGER.log("Server[%s] is started at `%s`", pair, server.serverAuthority());

            return pair;

        }

        private static HttpTestServer createServer(Version version, SSLContext sslContext) {
            try {
                // The default HTTP/1.1 test server processes requests sequentially.
                // This causes a deadlock for concurrent tests such as `testSlicedUpload()`.
                // Hence, explicitly providing a multithreaded executor for HTTP/1.1.
                ExecutorService executor = Version.HTTP_1_1.equals(version) ? EXECUTOR : null;
                return HttpTestServer.create(version, sslContext, executor);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        private static BlockingQueue<byte[]> addRequestBodyConsumingServerHandler(
                String serverName, HttpTestServer server, String handlerPath) {
            BlockingQueue<byte[]> readRequestBodyBytes = new LinkedBlockingQueue<>();
            HttpTestHandler handler = exchange -> {
                // `HttpTestExchange::toString` changes on failure, pin it
                String exchangeName = exchange.toString();
                try (exchange) {

                    // Discard `HEAD` requests used for initial connection admission
                    if ("HEAD".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(200, -1L);
                        return;
                    }

                    signalServerRequestReceived(serverName, exchangeName);
                    awaitServerReadPermission(serverName, exchangeName);

                    LOGGER.log("Server[%s] is reading the request body (exchange=%s)", serverName, exchangeName);
                    byte[] requestBodyBytes = exchange.getRequestBody().readAllBytes();
                    LOGGER.log("Server[%s] has read %s bytes (exchange=%s)", serverName, requestBodyBytes.length, exchangeName);
                    readRequestBodyBytes.add(requestBodyBytes);

                    LOGGER.log("Server[%s] is writing the response (exchange=%s)", serverName, exchangeName);
                    exchange.sendResponseHeaders(200, requestBodyBytes.length);
                    exchange.getResponseBody().write(requestBodyBytes);

                } catch (Throwable exception) {
                    LOGGER.log(
                            "Server[%s] failed to process the request (exchange=%s)".formatted(serverName, exception),
                            exception);
                    readRequestBodyBytes.add(new byte[0]);
                } finally {
                    LOGGER.log("Server[%s] completed processing the request (exchange=%s)", serverName, exchangeName);
                }
            };
            server.addHandler(handler, handlerPath);
            return readRequestBodyBytes;
        }

        private static void signalServerRequestReceived(String serverName, String exchangeName) {
            if (SERVER_REQUEST_RECEIVED_SIGNAL != null) {
                LOGGER.log("Server[%s] is signaling that the request is received (exchange=%s)", serverName, exchangeName);
                SERVER_REQUEST_RECEIVED_SIGNAL.countDown();
            }
        }

        private static void awaitServerReadPermission(String serverName, String exchangeName) {
            if (SERVER_READ_PERMISSION != null) {
                LOGGER.log("Server[%s] is waiting for the read permission (exchange=%s)", serverName, exchangeName);
                try {
                    SERVER_READ_PERMISSION.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();     // Restore the `interrupted` flag
                    throw new RuntimeException(ie);
                }
            }
        }

        @Override
        public String toString() {
            return serverName;
        }

    }

    @AfterAll
    static void shutDown() {
        LOGGER.log("Closing the client");
        CLIENT.close();
        LOGGER.log("Closing servers");
        closeServers();
        LOGGER.log("Closing the executor");
        EXECUTOR.shutdownNow();
    }

    private static void closeServers() {
        Exception[] exceptionRef = {null};
        Stream
                .of(HTTP1, HTTPS1, HTTP2, HTTPS2)
                .map(pair -> (Runnable) pair.server::stop)
                .forEach(terminator -> {
                    try {
                        terminator.run();
                    } catch (Exception exception) {
                        if (exceptionRef[0] == null) {
                            exceptionRef[0] = exception;
                        } else {
                            exceptionRef[0].addSuppressed(exception);
                        }
                    }
                });
        if (exceptionRef[0] != null) {
            throw new RuntimeException("failed closing one or more server resources", exceptionRef[0]);
        }
    }

    /**
     * Resets {@link ServerRequestPair#serverReadRequestBodyBytes()} to avoid leftover state from a test leaking to the next.
     */
    @BeforeEach
    void resetServerHandlerResults() {
        Stream
                .of(HTTP1, HTTPS1, HTTP2, HTTPS2)
                .forEach(pair -> pair.serverReadRequestBodyBytes.clear());
    }

    static ServerRequestPair[] serverRequestPairs() {
        return new ServerRequestPair[]{
                HTTP1,
                HTTPS1,
                HTTP2,
                HTTPS2
        };
    }

    @Test
    void testNullFileChannel() {
        assertThrows(NullPointerException.class, () -> BodyPublishers.ofFileChannel(null, 0, 1));
    }

    @ParameterizedTest
    @CsvSource({
            "6,-1,1",   // offset < 0
            "6,7,1",    // offset > fileSize
            "6,0,-1",   // length < 0
            "6,0,7",    // length > fileSize
            "6,2,5"     // (offset + length) > fileSize
    })
    void testIllegalOffsetOrLength(
            int fileLength,
            int fileChannelOffset,
            int fileChannelLength,
            @TempDir Path tempDir) throws Exception {
        withFileChannel(tempDir.resolve("data.txt"), fileLength, (_, fileChannel) ->
                assertThrows(
                        IndexOutOfBoundsException.class,
                        () -> BodyPublishers.ofFileChannel(fileChannel, fileChannelOffset, fileChannelLength)));
    }

    /**
     * Stresses corner cases in {@linkplain
     * BodyPublishers#ofFileChannel(FileChannel, long, long) the file channel
     * publisher}, which uses a {@linkplain #DEFAULT_BUFFER_SIZE fixed size}
     * buffer to read files, by providing sub-ranges and files that are
     * <em>smaller</em> than the buffer size.
     */
    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testContentLessThanBufferSize(ServerRequestPair pair, @TempDir Path tempDir) throws Exception {

        // Use a file of length smaller than the default buffer size
        int fileLength = 6;
        assertTrue(fileLength < DEFAULT_BUFFER_SIZE);

        // Publish the `[0, fileLength)` sub-range
        testSuccessfulContentDelivery(
                "Complete content",
                pair, tempDir, fileLength, 0, fileLength);

        // Publish the `[1, fileLength)` sub-range to stress the inclusion of EOF
        {
            int fileChannelOffset = 1;
            int fileChannelLength = fileLength - 1;
            String debuggingContext = debuggingContext(fileLength, fileChannelOffset, fileChannelLength);
            assertEquals(
                    fileLength - fileChannelOffset, fileChannelLength,
                    "must be until EOF " + debuggingContext);
            testSuccessfulContentDelivery(
                    "Partial content until the EOF " + debuggingContext,
                    pair, tempDir, fileLength, fileChannelOffset, fileChannelLength);
        }

        // Publish the `[1, fileLength - 1)` sub-range to stress the exclusion of EOF
        {
            int fileChannelOffset = 1;
            int fileChannelLength = fileLength - 2;
            String debuggingContext = debuggingContext(fileLength, fileChannelOffset, fileChannelLength);
            assertTrue(
                    fileLength - fileChannelOffset > fileChannelLength,
                    "must end before EOF " + debuggingContext);
            testSuccessfulContentDelivery(
                    "Partial content *before* the EOF " + debuggingContext,
                    pair, tempDir, fileLength, fileChannelOffset, fileChannelLength);
        }

    }

    /**
     * Stresses corner cases in {@linkplain
     * BodyPublishers#ofFileChannel(FileChannel, long, long) the file channel
     * publisher}, which uses a {@linkplain #DEFAULT_BUFFER_SIZE fixed size}
     * buffer to read files, by providing sub-ranges and files that are
     * <em>bigger</em> than the buffer size.
     */
    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testContentMoreThanBufferSize(ServerRequestPair pair, @TempDir Path tempDir) throws Exception {

        // Use a file of length that is
        // 1. greater than the default buffer size
        // 2. *not* a multitude of the buffer size
        int fileLength = 1 + 3 * DEFAULT_BUFFER_SIZE;

        // Publish the `[0, fileLength)` sub-range
        testSuccessfulContentDelivery(
                "Complete content",
                pair, tempDir, fileLength, 0, fileLength);

        // Publish the `[1, fileLength)` sub-range such that
        // - EOF is included
        // - the total length is a multitude of the buffer size
        {
            int fileChannelOffset = 1;
            int fileChannelLength = 3 * DEFAULT_BUFFER_SIZE;
            String debuggingContext = debuggingContext(fileLength, fileChannelOffset, fileChannelLength);
            assertEquals(
                    fileLength - fileChannelOffset, fileChannelLength,
                    "must be until EOF " + debuggingContext);
            testSuccessfulContentDelivery(
                    "Partial content until the EOF. Occupies exactly 3 buffers. " + debuggingContext,
                    pair, tempDir, fileLength, fileChannelOffset, fileChannelLength);
        }

        // Publish the `[1, fileLength)` sub-range such that
        // - EOF is included
        // - the total length is *not* a multitude of the buffer size
        {
            int fileChannelOffset = 2;
            int fileChannelLength = 3 * DEFAULT_BUFFER_SIZE - 1;
            String debuggingContext = debuggingContext(fileLength, fileChannelOffset, fileChannelLength);
            assertEquals(
                    fileLength - fileChannelOffset, fileChannelLength,
                    "must be until EOF " + debuggingContext);
            testSuccessfulContentDelivery(
                    "Partial content until the EOF. Occupies 3 buffers, the last is custom sized. " + debuggingContext,
                    pair, tempDir, fileLength, fileChannelOffset, fileChannelLength);
        }

        // Publish the `[1, fileLength)` sub-range such that
        // - EOF is *not* included
        // - the total length is a multitude of the buffer size
        {
            int fileChannelOffset = 2;
            int fileChannelLength = 2 * DEFAULT_BUFFER_SIZE;
            String debuggingContext = debuggingContext(fileLength, fileChannelOffset, fileChannelLength);
            assertTrue(
                    fileLength - fileChannelOffset > fileChannelLength,
                    "must end before EOF " + debuggingContext);
            testSuccessfulContentDelivery(
                    "Partial content *before* the EOF. Occupies exactly 2 buffers. " + debuggingContext,
                    pair, tempDir, fileLength, fileChannelOffset, fileChannelLength);
        }

        // Publish the `[1, fileLength)` sub-range such that
        // - EOF is *not* included
        // - the total length is *not* a multitude of the buffer size
        {
            int fileChannelOffset = 2;
            int fileChannelLength = 3 * DEFAULT_BUFFER_SIZE - 2;
            String debuggingContext = debuggingContext(fileLength, fileChannelOffset, fileChannelLength);
            assertTrue(
                    fileLength - fileChannelOffset > fileChannelLength,
                    "must end before EOF " + debuggingContext);
            testSuccessfulContentDelivery(
                    "Partial content *before* the EOF. Occupies 3 buffers, the last is custom sized. "+ debuggingContext,
                    pair, tempDir, fileLength, fileChannelOffset, fileChannelLength);
        }

    }

    private static String debuggingContext(int fileLength, int fileChannelOffset, int fileChannelLength) {
        Map<String, Object> context = new LinkedHashMap<>();    // Using `LHM` to preserve the insertion order
        context.put("DEFAULT_BUFFER_SIZE", DEFAULT_BUFFER_SIZE);
        context.put("fileLength", fileLength);
        context.put("fileChannelOffset", fileChannelOffset);
        context.put("fileChannelLength", fileChannelLength);
        boolean customSizedBuffer = fileChannelLength % DEFAULT_BUFFER_SIZE == 0;
        context.put("customSizedBuffer", customSizedBuffer);
        return context.toString();
    }

    private void testSuccessfulContentDelivery(
            String caseDescription,
            ServerRequestPair pair,
            Path tempDir,
            int fileLength,
            int fileChannelOffset,
            int fileChannelLength) throws Exception {

        // Case names come handy even when no debug logging is enabled.
        // Hence, intentionally avoiding `Logger`.
        System.err.printf("Case: %s%n", caseDescription);

        // Create the file to upload
        String fileName = "data-%d-%d-%d.txt".formatted(fileLength, fileChannelOffset, fileChannelLength);
        Path filePath = tempDir.resolve(fileName);
        withFileChannel(filePath, fileLength, (fileBytes, fileChannel) -> {

            // Upload the file
            HttpRequest request = pair
                    .requestBuilder
                    .POST(BodyPublishers.ofFileChannel(fileChannel, fileChannelOffset, fileChannelLength))
                    .build();
            CLIENT.send(request, discarding());

            // Verify the received request body
            byte[] expectedRequestBodyBytes = new byte[fileChannelLength];
            System.arraycopy(fileBytes, fileChannelOffset, expectedRequestBodyBytes, 0, fileChannelLength);
            byte[] actualRequestBodyBytes = pair.serverReadRequestBodyBytes.take();
            assertArrayEquals(expectedRequestBodyBytes, actualRequestBodyBytes);

        });

    }

    /**
     * <em>Big enough</em> file length to observe the effects of publisher state corruption while uploading.
     * <p>
     * Certain tests follow below steps:
     * </p>
     * <ol>
     * <li>Issue the request</li>
     * <li>Wait for the server's signal that the request (not the body!) is received</li>
     * <li>Corrupt the publisher's state; modify the file, close the file channel, etc.</li>
     * <li>Signal the server to proceed with reading</li>
     * </ol>
     * <p>
     * With small files, even before we permit the server to read (step 4), file gets already uploaded.
     * This voids the effect of state corruption (step 3).
     * To circumvent this, use this <em>big enough</em> file size.
     * </p>
     *
     * @see #testChannelCloseDuringPublisherRead(ServerRequestPair, Path)
     * @see #testFileModificationDuringPublisherRead(ServerRequestPair, Path)
     */
    private static final int BIG_FILE_LENGTH = 8 * 1024 * 1024;  // 8 MiB

    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testChannelCloseDuringPublisherRead(ServerRequestPair pair, @TempDir Path tempDir) throws Exception {
        establishInitialConnection(pair);
        ServerRequestPair.SERVER_REQUEST_RECEIVED_SIGNAL = new CountDownLatch(1);
        ServerRequestPair.SERVER_READ_PERMISSION = new CountDownLatch(1);
        try {

            int fileLength = BIG_FILE_LENGTH;
            AtomicReference<Future<HttpResponse<Void>>> responseFutureRef = new AtomicReference<>();
            withFileChannel(tempDir.resolve("data.txt"), fileLength, ((_, fileChannel) -> {

                // Issue the request
                LOGGER.log("Issuing the request");
                HttpRequest request = pair
                        .requestBuilder
                        .POST(BodyPublishers.ofFileChannel(fileChannel, 0, fileLength))
                        .build();
                responseFutureRef.set(CLIENT.sendAsync(request, discarding()));

                // Wait for server to receive the request
                LOGGER.log("Waiting for the request to be received");
                ServerRequestPair.SERVER_REQUEST_RECEIVED_SIGNAL.await();

            }));

            LOGGER.log("File channel is closed");

            // Let the server proceed
            LOGGER.log("Permitting the server to proceed");
            ServerRequestPair.SERVER_READ_PERMISSION.countDown();

            // Verifying the client failure
            LOGGER.log("Verifying the client failure");
            Exception requestFailure = assertThrows(ExecutionException.class, () -> responseFutureRef.get().get());
            assertInstanceOf(ClosedChannelException.class, requestFailure.getCause());

            verifyServerIncompleteRead(pair, fileLength);

        } finally {
            ServerRequestPair.SERVER_REQUEST_RECEIVED_SIGNAL = null;
            ServerRequestPair.SERVER_READ_PERMISSION = null;
        }
    }

    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testFileModificationDuringPublisherRead(ServerRequestPair pair, @TempDir Path tempDir) throws Exception {
        establishInitialConnection(pair);
        ServerRequestPair.SERVER_REQUEST_RECEIVED_SIGNAL = new CountDownLatch(1);
        ServerRequestPair.SERVER_READ_PERMISSION = new CountDownLatch(1);
        try {

            int fileLength = BIG_FILE_LENGTH;
            Path filePath = tempDir.resolve("data.txt");
            withFileChannel(filePath, fileLength, ((_, fileChannel) -> {

                // Issue the request
                LOGGER.log("Issuing the request");
                HttpRequest request = pair
                        .requestBuilder
                        .POST(BodyPublishers.ofFileChannel(fileChannel, 0, fileLength))
                        .build();
                Future<HttpResponse<Void>> responseFuture = CLIENT.sendAsync(request, discarding());

                // Wait for server to receive the request
                LOGGER.log("Waiting for the request to be received");
                ServerRequestPair.SERVER_REQUEST_RECEIVED_SIGNAL.await();

                // Modify the file
                LOGGER.log("Modifying the file");
                Files.write(filePath, generateFileBytes(1));

                // Let the server proceed
                LOGGER.log("Permitting the server to proceed");
                ServerRequestPair.SERVER_READ_PERMISSION.countDown();

                // Verifying the client failure
                LOGGER.log("Verifying the client failure");
                Exception requestFailure0 = assertThrows(ExecutionException.class, responseFuture::get);
                Exception requestFailure1 = assertInstanceOf(IOException.class, requestFailure0.getCause());
                String requestFailure2Message = requestFailure1.getMessage();
                assertTrue(
                        requestFailure2Message.contains("Unexpected EOF"),
                        "unexpected message: " + requestFailure2Message);

                verifyServerIncompleteRead(pair, fileLength);

            }));

        } finally {
            ServerRequestPair.SERVER_REQUEST_RECEIVED_SIGNAL = null;
            ServerRequestPair.SERVER_READ_PERMISSION = null;
        }
    }

    private static void verifyServerIncompleteRead(ServerRequestPair pair, int fileLength) throws InterruptedException {
        LOGGER.log("Verifying the server's incomplete read");
        byte[] readRequestBodyBytes = pair.serverReadRequestBodyBytes.take();
        assertTrue(
                readRequestBodyBytes.length < fileLength,
                "was expecting `readRequestBodyBytes < fileLength` (%s < %s)".formatted(
                        readRequestBodyBytes.length, fileLength));
    }

    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testSlicedUpload(ServerRequestPair pair, @TempDir Path tempDir) throws Exception {

        // Populate the file
        int sliceCount = 4;
        int sliceLength = 14_281; // Intentionally using a prime number to increase the chances of hitting corner cases
        int fileLength = sliceCount * sliceLength;
        byte[] fileBytes = generateFileBytes(fileLength);
        Path filePath = tempDir.resolve("data.txt");
        Files.write(filePath, fileBytes, StandardOpenOption.CREATE);

        List<InputStream> responseBodyStreams = new ArrayList<>(sliceCount);
        try (FileChannel fileChannel = FileChannel.open(filePath)) {

            // Upload the complete file in mutually exclusive slices
            List<Future<HttpResponse<InputStream>>> responseFutures = new ArrayList<>(sliceCount);
            for (int sliceIndex = 0; sliceIndex < sliceCount; sliceIndex++) {
                LOGGER.log("Issuing request %d/%d", (sliceIndex + 1), sliceCount);
                HttpRequest request = pair
                        .requestBuilder
                        .POST(BodyPublishers.ofFileChannel(fileChannel, sliceIndex * sliceLength, sliceLength))
                        .build();
                responseFutures.add(CLIENT.sendAsync(
                        request,
                        // Intentionally using an `InputStream` response
                        // handler to defer consuming the response body
                        // until after the file channel is closed:
                        ofInputStream()));
            }

            // Collect response body `InputStream`s from all requests
            for (int sliceIndex = 0; sliceIndex < sliceCount; sliceIndex++) {
                LOGGER.log("Collecting response body `InputStream` for request %d/%d", (sliceIndex + 1), sliceCount);
                HttpResponse<InputStream> response = responseFutures.get(sliceIndex).get();
                assertEquals(200, response.statusCode());
                responseBodyStreams.add(response.body());
            }

        }

        LOGGER.log("File channel is closed");

        // Verify response bodies
        for (int sliceIndex = 0; sliceIndex < sliceCount; sliceIndex++) {
            LOGGER.log("Consuming response body %d/%d", (sliceIndex + 1), sliceCount);
            byte[] expectedResponseBodyBytes = new byte[sliceLength];
            System.arraycopy(fileBytes, sliceIndex * sliceLength, expectedResponseBodyBytes, 0, sliceLength);
            try (InputStream responseBodyStream = responseBodyStreams.get(sliceIndex)) {
                byte[] responseBodyBytes = responseBodyStream.readAllBytes();
                assertArrayEquals(expectedResponseBodyBytes, responseBodyBytes);
            }
        }

    }

    /**
     * Performs the initial {@code HEAD} request to the specified server. This
     * effectively admits a connection to the client's pool, where all protocol
     * upgrades, handshakes, etc. are already performed.
     * <p>
     * HTTP/2 test server consumes the complete request payload in the very
     * first upgrade frame. That is, if a client sends 100 MiB of data, all
     * of it will be consumed first before the configured handler is
     * invoked. Though certain tests expect the data to be consumed
     * piecemeal. To accommodate this, we ensure client has an upgraded
     * connection in the pool.
     * </p>
     */
    private static void establishInitialConnection(ServerRequestPair pair) {
        LOGGER.log("Server[%s] is getting queried for the initial connection pool admission", pair);
        try {
            CLIENT.send(pair.requestBuilder.HEAD().build(), discarding());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void withFileChannel(Path filePath, int fileLength, FileChannelConsumer fileChannelConsumer) throws Exception {
        byte[] fileBytes = generateFileBytes(fileLength);
        Files.write(filePath, fileBytes, StandardOpenOption.CREATE);
        try (FileChannel fileChannel = FileChannel.open(filePath)) {
            fileChannelConsumer.consume(fileBytes, fileChannel);
        }
    }

    @FunctionalInterface
    private interface FileChannelConsumer {

        void consume(byte[] fileBytes, FileChannel fileChannel) throws Exception;

    }

    private static byte[] generateFileBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

}
