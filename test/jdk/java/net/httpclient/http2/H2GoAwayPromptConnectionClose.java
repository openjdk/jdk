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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

import jdk.internal.net.http.frame.DataFrame;
import jdk.internal.net.http.frame.FramesEncoder;
import jdk.internal.net.http.frame.HeadersFrame;
import jdk.internal.net.http.frame.Http2Frame;
import jdk.internal.net.http.frame.SettingsFrame;
import jdk.internal.net.http.hpack.Decoder;
import jdk.internal.net.http.hpack.Encoder;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8385131 8394607
 * @summary Verifies that if the HttpClient receives a GOAWAY frame on a HTTP/2 connection,
 *          then the HttpClient terminates the connection as promptly as appropriate instead
 *          of relying on the idle connection management to do so. Also verifies that if
 *          the server closes the connection first, the client can still receive the response
 *          bytes after the connection is closed.
 * @library /test/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.test.lib.RandomFactory jdk.test.lib.net.URIBuilder
 * @comment An arbitrary high value for idle connection timeout to prevent idle
 *          connection management from closing the HTTP/2 connection
 * @run junit/othervm -Djdk.httpclient.keepalive.timeout.h2=36000
 *                    -Djdk.internal.httpclient.debug=true
 *                    ${test.main.class}
 */
class H2GoAwayPromptConnectionClose {

    private static final int MAX_HEADER_TABLE_CAPACITY = 4096;
    private static final String REQUEST_PATH =
            "/" + H2GoAwayPromptConnectionClose.class.getSimpleName();

    private static final String XKEY = "x-send-response-and-close";
    private static final String RESPONSE = """
            Lorem ipsum dolor sit amet,
            caesarem legato alacrem.
            """;
    private static final Random random = RandomFactory.getRandom();

    private static final SSLContext sslCtx = SimpleSSLContext.findSSLContext();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static HttpClient client;
    private static MinimalH2OnlyServer server;

    // a semaphore that is released when the server notices an EOF on the connection's
    // input stream due to the client closing the connection, or when the server
    // closes the connection without waiting for the client
    private static final Semaphore connTerminationLatch = new Semaphore(0);

    @BeforeAll
    static void beforeAll() throws Exception {
        client = HttpClient.newBuilder().proxy(NO_PROXY).sslContext(sslCtx).build();
        server = new MinimalH2OnlyServer();
        System.err.println("server listening at " + server.getServerAddress());
        executor.submit(server);
    }

    @AfterAll
    static void afterAll() throws Exception {
        System.err.println("stopping server " + server.getServerAddress());
        closeQuietly(server);
        closeQuietly(client);
        executor.shutdownNow();
    }

    private static void closeQuietly(final AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable t) {
            System.err.println("ignoring exception: " + t + " that occurred during closing "
                    + closeable);
        }
    }


    /*
     * Issues a "https" HTTP/2 request and expects the request to complete normally.
     * On the server side, in addition to sending the HTTP/2 response, the server also sends
     * a GOAWAY frame to the client.
     * After the response is received, the test waits to be notified about the termination
     * of the underlying connection. The test completes successfully only if it receives the
     * connection termination notification.
     * If it isn't notified of the connection termination, then that's a sign (of a bug) that
     * the HttpClient did not promptly close the connection even after receiving a GOAWAY frame
     * with no additional requests in progress.
     */
    @Test
    void testConnectionTermination() throws Exception {
        final InetSocketAddress serverAddr = server.getServerAddress();
        final URI reqURI = URIBuilder.newBuilder()
                .scheme("https")
                .host(serverAddr.getAddress())
                .port(serverAddr.getPort())
                .path(REQUEST_PATH)
                .build();
        assertEquals(0, connTerminationLatch.availablePermits());
        final HttpRequest req = HttpRequest.newBuilder().version(HTTP_2).uri(reqURI).build();
        System.err.println("issuing request: " + req);
        final HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "unexpected status code");
        assertEquals(HTTP_2, resp.version(), "unexpected HTTP version in response");

        System.err.println("awaiting connection termination");
        connTerminationLatch.acquire();
    }

    /*
     * Issues a "https" HTTP/2 request and expects the request to complete normally.
     * On the server side, in addition to sending the HTTP/2 response, the server also sends
     * a GOAWAY frame to the client. In this scenario, the server sends a response body
     * composed of several DATA frames, then immediately closes the connection after
     * having sent the response body bytes and GOAWAY.
     * The client waits for the server to close the connection, then waits for a short
     * delay before starting to pull the response body bytes from the HttpResponseInputStream.
     * The test completes successfully only if the client is able to pull all the response bytes
     * from the input stream, despite the server having already closed the connection.
     * If it fails to pull all the bytes, then it's a sign that the exchange was
     * cancelled immediately on reception of the connection FIN, despite having buffered all
     * the response bytes.
     * This scenario will usually fail without the fix for JDK-8394607.
     */
    @Test
    void testBufferedResponseReceived() throws Exception {
        System.out.println("testBufferedResponseReceived");
        final InetSocketAddress serverAddr = server.getServerAddress();
        final URI reqURI = URIBuilder.newBuilder()
                .scheme("https")
                .host(serverAddr.getAddress())
                .port(serverAddr.getPort())
                .path(REQUEST_PATH)
                .build();
        final HttpRequest req = HttpRequest.newBuilder().version(HTTP_2)
                .uri(reqURI)
                .header(XKEY, "yes")
                .build();
        for (int i=0; i < 5; i++) {
            assertEquals(0, connTerminationLatch.availablePermits());
            System.out.printf("issuing request[%s]: %s%n", i, req);
            System.err.printf("issuing request[%s]: %s%n", i, req);
            final HttpResponse<InputStream> resp = client.send(req, BodyHandlers.ofInputStream());
            assertEquals(200, resp.statusCode(), "unexpected status code");
            assertEquals(HTTP_2, resp.version(), "unexpected HTTP version in response");
            System.out.println("awaiting connection termination");
            System.err.println("awaiting connection termination");
            connTerminationLatch.acquire();

            // At this point the server has closed the connection.
            // Wait a little bit to give a chance to the client to
            // observe connection closure, since having closed the
            // connection from the server side doesn't guarantee that
            // the TCP FIN has reached the client side and has already
            // been processed.
            Thread.sleep(random.nextLong(100, 1000));

            // Start pulling the bytes from the input stream. The bytes
            // should have been all received and buffered, so it should
            // be possible to pull them even though the connection was closed
            System.out.println("Pulling response bytes");
            System.err.println("Pulling response bytes");
            byte[] bytes = resp.body().readAllBytes();
            assertArrayEquals(RESPONSE.getBytes(StandardCharsets.UTF_8), bytes);
            System.out.printf("Got expected response for request[%s]%n", i);
            System.err.printf("Got expected response for request[%s]%n", i);
        }
    }

    /*
     * The test requires fine-grained control over when the server sends the GOAWAY frame
     * over the HTTP/2 connection and also to detect that the client has closed the underlying
     * connection. So we read/write HTTP/2 protocol messages directly over a
     * ServerSocket/Socket pair, instead of using the HTTP servers in the test library.
     * This server only support "https" and negotiates "h2" protocol during the TLS handshake.
     */
    private static final class MinimalH2OnlyServer implements Runnable, AutoCloseable {
        private final ServerSocket serverSocket;
        private volatile boolean stop;

        private MinimalH2OnlyServer() throws IOException {
            // create the SSLServerSocket
            final SSLServerSocket ss = (SSLServerSocket) sslCtx.getServerSocketFactory()
                    .createServerSocket(0, 0, Inet4Address.getLoopbackAddress());
            // configure "h2" ALPN on the server socket
            final SSLParameters sslParams = new SSLParameters();
            sslParams.setApplicationProtocols(new String[]{"h2"});
            ss.setSSLParameters(sslParams);

            this.serverSocket = ss;
        }

        private InetSocketAddress getServerAddress() {
            return (InetSocketAddress) this.serverSocket.getLocalSocketAddress();
        }

        @Override
        public void run() {
            try {
                doRun();
            } catch (Throwable t) {
                if (!stop) {
                    System.err.println("Exception occurred in server " + t);
                    t.printStackTrace();
                }
            }
        }

        // accept socket connections and process them
        private void doRun() throws IOException {
            while (!stop) {
                final Socket socket = serverSocket.accept();
                System.err.println("accepted connection from " + socket);
                // submit for processing the request
                executor.submit(new Handler(socket));
            }
        }

        @Override
        public void close() throws IOException {
            this.stop = true;
            this.serverSocket.close();
        }
    }

    // Processes the data on a single HTTP/2 socket connection
    private static final class Handler implements Runnable {
        // number of bytes that are taken by the (common) Length and Type fields of
        // a HTTP/2 frame
        private static final int SIZE_OF_LENGTH_AND_TYPE_FIELDS = 9;
        // empty SETTINGS frame
        private static final byte[] SERVER_SETTINGS_FRAME = new byte[]{0, 0, 0, 0x04,
                0, 0, 0, 0, 0};
        // SETTINGS frame ACKing the receipt of a SETTINGS frame from the client
        private static final byte[] ACK_CLIENT_SETTINGS_FRAME = new byte[]{0, 0, 0, 0x04, 0x01,
                0, 0, 0, 0};

        private final Socket socket;
        private final String logId;

        private final FramesEncoder framesEncoder = new FramesEncoder();

        private record ParsedRequest(int streamId, Map<String, List<String>> headers) {

            /*
             * Returns the {@code :path} header value (if any)
             */
            private String getRequestPath() {
                final List<String> vals = headers.get(":path");
                if (vals == null || vals.isEmpty()) {
                    return null;
                }
                return vals.getFirst();
            }

            // the XKEY header indicates whether the client
            // wants the server to send response bytes and
            // immediately close the connection after that
            private String getXKey() {
                final List<String> vals = headers.get(XKEY);
                if (vals == null || vals.isEmpty()) {
                    return null;
                }
                return vals.getFirst();
            }
        }

        private Handler(final Socket socket) {
            this.socket = socket;
            this.logId = "local=" + socket.getLocalSocketAddress()
                    + ", remote=" + socket.getRemoteSocketAddress();
        }

        private void log(final String msg) {
            System.err.println("[" + this.logId + "] " + msg);
            System.out.println("[" + this.logId + "] " + msg);
        }

        @Override
        public void run() {
            try {
                handleSocketConnection();
            } catch (Throwable t) {
                // either an unexpected connection or something went wrong with the
                // HTTP/2 request issued in this test. just log it; the test
                // doesn't rely on this exception to be propagated to notice any failures.
                log("ignoring exception " + t + " that occurred when handling" +
                        " connection from: " + socket);
                t.printStackTrace();
            }
        }

        /*
         * Parse and respond to the HTTP/2 data received over the socket. Also sends a GOAWAY frame
         * to the client. We don't close the socket connection ourselves and instead expect
         * the client to close it once it receives the GOAWAY frame and with no active streams
         * on the connection.
         */
        private void handleSocketConnection() throws IOException {
            // read the HTTP/2 client preface
            readClientPreface();
            log("received client preface");
            // next read the client SETTINGS and send the corresponding SETTINGS ACK.
            // then send the server's SETTINGS and wait for a SETTINGS ACK from the client.
            final List<byte[]> allRecvdFrames = exchangeConnectionSettings();
            log("completed exchanging SETTINGS frames between client and server");
            // now read the HTTP/2 request's HEADERS frame
            final ParsedRequest req = waitForRequest(allRecvdFrames);
            final String reqPath = req.getRequestPath();
            if (!REQUEST_PATH.equals(reqPath)) {
                throw new IOException("unexpected request path " + reqPath);
            }
            log("received HTTP/2 request " + req.headers + " on stream " + req.streamId);

            // if XKEY is "yes" we will send a response body and close
            // the socket from the server side immediately after
            log(XKEY +": " + req.getXKey());
            final String responseBody = "yes".equals(req.getXKey())
                    ? RESPONSE
                    : null;

            // send the HTTP/2 response for the stream as well as a GOAWAY frame on the connection.
            // we randomly decide which one gets sent first and it shouldn't matter
            // which order they are sent, the end result for the client should be the same
            // (i.e. the HTTP request should complete normally and the underlying connection
            // is closed)
            final boolean sendGoAwayFirst = random.nextBoolean();
            if (sendGoAwayFirst) {
                sendGoAway(req.streamId);
                log("sent GOAWAY");
                sendResponse(req.streamId, responseBody);
                log("sent a HTTP/2 response");
            } else {
                sendResponse(req.streamId, responseBody);
                log("sent a HTTP/2 response");
                sendGoAway(req.streamId);
                log("sent GOAWAY");
            }

            if (responseBody == null) {
                // testConnectionTermination:
                //    wait for the client to close the connection
                waitForEOF();
            } else {
                // testBufferedResponseReceived:
                //    close the connection and notify the client
                socket.close();
                connTerminationLatch.release();
            }
        }

        private void readClientPreface() throws IOException {
            final byte[] preface = socket.getInputStream().readNBytes(24);
            if (preface.length != 24) {
                throw new IOException("Not a HTTP/2 client preface, received only "
                        + preface.length + " bytes");
            }
        }

        private List<byte[]> exchangeConnectionSettings() throws IOException {
            final List<byte[]> allRcvdFrames = new ArrayList<>();
            // read the SETTINGS frame from the client
            final byte[] settingsFrame = expectFrame(socket.getInputStream(), SettingsFrame.TYPE);
            allRcvdFrames.add(settingsFrame);
            log("received SETTINGS frame");

            final OutputStream out = socket.getOutputStream();
            // send the SETTINGS ACK
            out.write(ACK_CLIENT_SETTINGS_FRAME);
            out.flush();
            // respond with server SETTINGS
            out.write(SERVER_SETTINGS_FRAME);
            out.flush();
            // expect the client to send the SETTINGS ACK frame
            final List<byte[]> moreFrames = waitForSettingsAckFrame();
            allRcvdFrames.addAll(moreFrames);
            return allRcvdFrames;
        }

        private List<byte[]> waitForSettingsAckFrame() throws IOException {
            log("accumulating frames till a SETTINGS ACK frame is received");
            final List<byte[]> recvdFrames = new ArrayList<>();
            int recvdFrameType = -1;
            byte[] frame;
            do {
                frame = readHTTP2Frame(socket.getInputStream());
                // keep track of received frames
                recvdFrames.add(frame);
                recvdFrameType = frameType(frame);
                log("received frame type " + recvdFrameType
                        + " (" + Http2Frame.asString(recvdFrameType) + ")");
            } while (recvdFrameType != SettingsFrame.TYPE);
            // received the SETTINGS frame, verify it has the ACK bit set
            final boolean ackBitSet = ((frame[4] & 0x01) == 1);
            if (!ackBitSet) {
                throw new IOException("ACK bit not set on SETTINGS frame from " + socket);
            }
            // return all the received frames
            return recvdFrames;
        }

        private ParsedRequest waitForRequest(final List<byte[]> alreadyRcvdFrames)
                throws IOException {
            byte[] headersFrame = findHeadersFrame(alreadyRcvdFrames);
            if (headersFrame == null) {
                // no HEADERS frame have been read so far, expect it to arrive
                headersFrame = expectFrame(socket.getInputStream(), HeadersFrame.TYPE);
            }
            // parse the HEADERS frame
            final int streamId = getStreamId(headersFrame);
            if (streamId <= 0) {
                throw new IOException("invalid stream id: " + streamId + " in HEADERS" +
                        " frame on connection " + socket);
            }
            final Map<String, List<String>> reqHeaders = parseHeadersFrame(headersFrame);
            return new ParsedRequest(streamId, reqHeaders);
        }

        private static byte[] findHeadersFrame(final List<byte[]> frames) {
            for (final byte[] frame : frames) {
                final int frameType = frameType(frame);
                if (frameType == HeadersFrame.TYPE) {
                    return frame; // found
                }
            }
            return null; // no HEADERS frame found
        }

        // parses the HTTP/2 HEADERS frame and returns back a Map of HTTP headers from the request
        private Map<String, List<String>> parseHeadersFrame(final byte[] frame) throws IOException {
            final ByteBuffer fieldBlock = ByteBuffer.wrap(frame, SIZE_OF_LENGTH_AND_TYPE_FIELDS,
                    frame.length - SIZE_OF_LENGTH_AND_TYPE_FIELDS);
            // HPACK decoder
            final Decoder decoder = new Decoder(MAX_HEADER_TABLE_CAPACITY);
            final Map<String, List<String>> reqHeaders = new HashMap<>();
            decoder.decode(fieldBlock, true, (name, value) -> {
                final String headerName = name.toString();
                reqHeaders.putIfAbsent(headerName, new ArrayList<>());
                reqHeaders.get(headerName).add(value.toString());
            });
            return reqHeaders;
        }

        private static byte[] expectFrame(final InputStream in, final int expectedFrameType)
                throws IOException {
            final byte[] frame = readHTTP2Frame(in);
            final int frameType = frameType(frame);
            if (frameType != expectedFrameType) {
                throw new IOException("unexpected frame, got type " + frameType
                        + ", expected " + expectedFrameType);
            }
            return frame;
        }

        private static byte[] readHTTP2Frame(final InputStream in)
                throws IOException {
            final byte[] header = in.readNBytes(SIZE_OF_LENGTH_AND_TYPE_FIELDS);
            if (header.length != SIZE_OF_LENGTH_AND_TYPE_FIELDS) {
                throw new IOException("Incomplete HTTP/2 frame header, received only "
                        + header.length + " bytes, expected "
                        + SIZE_OF_LENGTH_AND_TYPE_FIELDS + " bytes");
            }
            // 3 bytes of Length field that represent the payload length
            final int payloadLength = ((header[0] & 0xFF) << 16)
                    | ((header[1] & 0xFF) << 8)
                    | (header[2] & 0xFF);
            final byte[] payload = in.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new IOException("Incomplete HTTP/2 frame payload, received only "
                        + payload.length + " bytes, expected " + payloadLength + " bytes");
            }
            final byte[] frame = new byte[header.length + payload.length];
            System.arraycopy(header, 0, frame, 0, header.length);
            System.arraycopy(payload, 0, frame, header.length, payload.length);
            return frame;
        }

        private void sendGoAway(final int lastStreamId) throws IOException {
            // 4 bytes of lastStreamId + 4 bytes of error code + 0 bytes of debug data
            final int payloadLength = 8;
            byte[] frame = new byte[SIZE_OF_LENGTH_AND_TYPE_FIELDS + payloadLength];
            frame[0] = (byte) ((payloadLength >> 16) & 0xFF);
            frame[1] = (byte) ((payloadLength >> 8) & 0xFF);
            frame[2] = (byte) (payloadLength & 0xFF);
            frame[3] = 0x07; // GOAWAY frame type
            frame[4] = 0; // no flags
            // stream id = 0, since GOAWAY belongs to the connection
            frame[5] = 0;
            frame[6] = 0;
            frame[7] = 0;
            frame[8] = 0;
            // last stream ID representing the last processed stream on the connection
            frame[9] = (byte) ((lastStreamId >> 24) & 0x7F);
            frame[10] = (byte) ((lastStreamId >> 16) & 0xFF);
            frame[11] = (byte) ((lastStreamId >> 8) & 0xFF);
            frame[12] = (byte) (lastStreamId & 0xFF);
            // Error code == NO_ERROR
            frame[13] = 0;
            frame[14] = 0;
            frame[15] = 0;
            frame[16] = 0;

            final OutputStream out = socket.getOutputStream();
            out.write(frame);
            out.flush();
        }

        private void sendResponse(final int streamId, String response) throws IOException {
            // HPACK encoder
            final Encoder encoder = new Encoder(MAX_HEADER_TABLE_CAPACITY);
            encoder.header(":status", "200");
            final ByteBuffer buf = ByteBuffer.allocate(1024);
            if (!encoder.encode(buf)) {
                throw new IOException("HPACK encoding didn't complete");
            }
            buf.flip();
            final byte[] fieldBlock = new byte[buf.remaining()];
            buf.get(fieldBlock);

            final byte[] frame = new byte[SIZE_OF_LENGTH_AND_TYPE_FIELDS + fieldBlock.length];
            frame[0] = (byte) ((fieldBlock.length >> 16) & 0xFF);
            frame[1] = (byte) ((fieldBlock.length >> 8) & 0xFF);
            frame[2] = (byte) (fieldBlock.length & 0xFF);
            frame[3] = 0x01; // HEADERS frame type
            if (response == null) {
                frame[4] = 0x05; // flags =  END_STREAM (0x01) | END_HEADERS (0x04)
            } else {
                frame[4] = 0x04; // flags =  END_HEADERS (0x04)
            }
            frame[5] = (byte) ((streamId >> 24) & 0x7F);
            frame[6] = (byte) ((streamId >> 16) & 0xFF);
            frame[7] = (byte) ((streamId >> 8) & 0xFF);
            frame[8] = (byte) (streamId & 0xFF);

            System.arraycopy(fieldBlock, 0, frame, SIZE_OF_LENGTH_AND_TYPE_FIELDS, fieldBlock.length);

            final OutputStream out = socket.getOutputStream();
            out.write(frame);
            out.flush();
            if (response != null) {
                writeResponseBody(out, streamId, response);
            }
        }

        private void writeResponseBody(OutputStream out, int streamId, String response) throws IOException {
            log("Sending response body");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            ByteBuffer payload = ByteBuffer.wrap(bytes);
            assert bytes.length > 10 : "response body needs more than 10 bytes";
            // send body in several data frames, by chunks of 10 bytes
            for (int i = 0 ; i < bytes.length ; i += 10) {
                payload.position(i);
                payload.limit(Math.min(i + 10, bytes.length));
                int flags = payload.limit() < bytes.length
                        ? 0
                        : DataFrame.END_STREAM;
                DataFrame dataFrame = new DataFrame(streamId, flags, payload);
                log("Sending DataFrame: " + dataFrame);
                for (ByteBuffer buffer : framesEncoder.encodeFrame(dataFrame)) {
                    if (buffer.hasRemaining()) {
                        byte[] towrite = new byte[buffer.remaining()];
                        log("Writing %s bytes".formatted(towrite.length));
                        buffer.get(towrite);
                        out.write(towrite);
                        out.flush();
                    }
                }
            }
        }

        private void waitForEOF() throws IOException {
            log("waiting to receive EOF");
            final Instant start = Instant.now();
            this.socket.getInputStream().readAllBytes();
            // let the test know that the connection has been terminated
            connTerminationLatch.release();
            final Instant end = Instant.now();
            log("took " + Duration.between(start, end) + " to reach EOF");
        }

        private static int frameType(final byte[] frame) {
            assert frame.length >= 4 : "expected at least 4 bytes, but found only " + frame.length;
            return frame[3];
        }

        private static int getStreamId(final byte[] frame) {
            // Stream Identifier starts at the 6th byte in the frame and takes
            // up 4 bytes
            assert frame.length >= 9 : "expected at least 9 bytes, but found only " + frame.length;
            return ((frame[5] & 0x7F) << 24) | ((frame[6] & 0xFF) << 16) |
                    ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);
        }
    }
}
