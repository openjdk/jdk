/*
 * Copyright (c) 2026, Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

/*
 * @test
 * @bug 8396170
 * @summary A PRIORITY frame with a length other than 5 is a stream error
 *          (RFC 9113, 6.3). RST_STREAM is sent only if the stream is open or
 *          half-closed, never on idle or closed streams (RFC 9113, 5.1, 6.4).
 *          The connection must survive in all cases.
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
 * @run junit/othervm -Djdk.internal.httpclient.debug=true ${test.main.class}
 */

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MalformedPriorityFrameTest {

    static final int HEADERS = 0x1, PRIORITY = 0x2, RST_STREAM = 0x3,
            SETTINGS = 0x4, PING = 0x6, GOAWAY = 0x7;
    static final int FRAME_SIZE_ERROR = 0x6;

    // RST_STREAM(FRAME_SIZE_ERROR) is sent and the connection survives.
    @Test
    void activeStream() throws Exception {
        try (TestServer server = new TestServer(1, true);
             HttpClient client = newClient()) {
            assertThrows(IOException.class, () -> get(client, server.uri()));
            int[] rst = server.rst.get(30, TimeUnit.SECONDS);
            assertEquals(1, rst[0], "RST_STREAM stream id");
            assertEquals(FRAME_SIZE_ERROR, rst[1], "RST_STREAM error code");
            server.pingAck.get(30, TimeUnit.SECONDS);
            assertEquals(200, get(client, server.uri()).statusCode());
            assertFalse(server.goAway.isDone(), "unexpected GOAWAY");
        }
    }

    // END_STREAM sent and received and the frame is ignored.
    @Test
    void closedStream() throws Exception {
        checkIgnored(1);
    }

    // Idle client-initiated (101) and server-initiated (2) streams
    // and the frame is ignored.
    @ParameterizedTest
    @ValueSource(ints = {101, 2})
    void idleStream(int streamId) throws Exception {
        checkIgnored(streamId);
    }

    static void checkIgnored(int badStreamId) throws Exception {
        try (TestServer server = new TestServer(badStreamId, false);
             HttpClient client = newClient()) {
            assertEquals(200, get(client, server.uri()).statusCode());
            server.pingAck.get(30, TimeUnit.SECONDS);
            assertEquals(200, get(client, server.uri()).statusCode());
            assertFalse(server.rst.isDone(), "unexpected RST_STREAM");
            assertFalse(server.goAway.isDone(), "unexpected GOAWAY");
        }
    }

    static HttpClient newClient() {
        return HttpClient.newBuilder().version(HTTP_2).proxy(NO_PROXY).build();
    }

    static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static byte[] frame(int type, int flags, int streamId, byte[] payload) {
        int len = payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(9 + len);
        buffer.put((byte) (len >> 16));
        buffer.put((byte) (len >> 8));
        buffer.put((byte) len);
        buffer.put((byte) type);
        buffer.put((byte) flags);
        buffer.putInt(streamId);
        buffer.put(payload);
        return buffer.array();
    }

    static final class TestServer implements AutoCloseable {
        final ServerSocket ss;
        final CompletableFuture<int[]> rst = new CompletableFuture<>();
        final CompletableFuture<Void> goAway = new CompletableFuture<>();
        final CompletableFuture<Void> pingAck = new CompletableFuture<>();

        TestServer(int badStreamId, boolean badBeforeResponse) throws IOException {
            ss = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            byte[] bad = frame(PRIORITY, 0, badStreamId, new byte[4]);
            Thread.ofPlatform().daemon().start(() -> {
                serve(bad, badBeforeResponse);
            });
        }

        URI uri() {
            return URIBuilder.newBuilder().scheme("http").loopback()
                    .port(ss.getLocalPort()).path("/").buildUnchecked();
        }

        void serve(byte[] bad, boolean badBeforeResponse) {
            try (Socket s = ss.accept()) {
                DataInputStream in = new DataInputStream(s.getInputStream());
                OutputStream out = s.getOutputStream();
                StringBuilder request = new StringBuilder();
                while (!request.toString().endsWith("\r\n\r\n")) {
                    int c = in.read();
                    if (c < 0) throw new IOException("EOF in HTTP/1.1 request");
                    request.append((char) c);
                }
                write(out, new byte[][]{("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Connection: Upgrade\r\nUpgrade: h2c\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII)});
                write(out, new byte[][]{frame(SETTINGS, 0, 0, new byte[0])});
                in.readFully(new byte[24]);
                byte[] response = frame(HEADERS, 0x5, 1, new byte[] {(byte) 0x88});
                byte[] ping = frame(PING, 0, 0, new byte[8]);
                if (badBeforeResponse) {
                    write(out, new byte[][]{bad, response, ping});
                } else {
                    write(out, new byte[][]{response, bad, ping});
                }
                while (true) {
                    byte[] hdr = new byte[9];
                    in.readFully(hdr);
                    int len = ((hdr[0] & 0xff) << 16) | ((hdr[1] & 0xff) << 8) | (hdr[2] & 0xff);
                    int type = hdr[3] & 0xff;
                    int flags = hdr[4] & 0xff;
                    int sid = ByteBuffer.wrap(hdr, 5, 4).getInt() & 0x7fffffff;
                    byte[] payload = new byte[len];
                    in.readFully(payload);
                    switch (type) {
                        case SETTINGS:
                            if ((flags & 0x1) == 0) {
                                write(out, new byte[][]{frame(SETTINGS, 0x1, 0, new byte[0])});
                            }
                            break;
                        case PING:
                            if ((flags & 0x1) != 0) {
                                pingAck.complete(null);
                            }
                            break;
                        case HEADERS:
                            write(out, new byte[][]{frame(HEADERS, 0x5, sid, new byte[]{(byte) 0x88})});
                            break;
                        case RST_STREAM:
                            rst.complete(new int[]{sid, ByteBuffer.wrap(payload).getInt()});
                            break;
                        case GOAWAY:
                            goAway.complete(null);
                            break;
                        default:
                            break;
                    }
                }
            } catch (IOException e) {
                pingAck.completeExceptionally(e);
            }
        }

        static void write(OutputStream out, byte[][] frames) throws IOException {
            for (byte[] frame : frames) {
                out.write(frame);
            }
            out.flush();
        }

        @Override
        public void close() throws IOException {
            ss.close();
        }
    }
}