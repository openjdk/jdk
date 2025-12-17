/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.httpclient.test.lib.quic.QuicStandaloneServer;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.QuicConnectionId;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.Utils;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8373409
 * @key intermittent
 * @comment testResetControlStream may fail if the client doesn't read the stream type
 *              before the stream is reset,
 *          testConnectionCloseXXX may fail because connection_close frame is not retransmitted
 * @summary Verifies that the HTTP client responds with the right error codes and types
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @library ../access
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @build java.net.http/jdk.internal.net.http.Http3ConnectionAccess
 * @run testng/othervm
 *              -Djdk.internal.httpclient.debug=true
 *              -Djdk.httpclient.HttpClient.log=requests,responses,errors H3ErrorHandlingTest
 */
public class H3ErrorHandlingTest implements HttpServerAdapters {

    private SSLContext sslContext;
    private QuicStandaloneServer server;
    private String requestURIBase;

    @DataProvider
    public static Object[][] controlStreams() {
        // control / encoder / decoder
        return new Object[][] {{(byte)0}, {(byte)2}, {(byte)3}};
    }

    static final byte[] data = new byte[]{(byte)0,(byte)0};
    static final byte[] headers = new byte[]{(byte)1,(byte)0};
    static final byte[] reserved1 = new byte[]{(byte)2,(byte)0};
    static final byte[] cancel_push = new byte[]{(byte)3,(byte)1,(byte)0};
    static final byte[] settings = new byte[]{(byte)4,(byte)0};
    static final byte[] push_promise = new byte[]{(byte)5,(byte)1,(byte)0};
    // 48 bytes, ID 0, 47 byte headers
    static final byte[] valid_push_promise = HexFormat.of().parseHex(
            "0530000000"+ // push promise, length 48, id 0, section prefix
                    "508b089d5c0b8170dc702fbce7"+ // :authority
                    "d1"+ // :method:get
                    "51856272d141ff"+ // :path
                    "d7"+ // :scheme:https
                    "5f5094ca3ee35a74a6b589418b5258132b1aa496ca8747"); //user-agent
    static final byte[] reserved2 = new byte[]{(byte)6,(byte)0};
    static final byte[] goaway = new byte[]{(byte)7,(byte)1,(byte)4};
    static final byte[] reserved3 = new byte[]{(byte)8,(byte)0};
    static final byte[] reserved4 = new byte[]{(byte)9,(byte)0};
    static final byte[] max_push_id = new byte[]{(byte)13,(byte)1,(byte)0};
    static final byte[] huge_id_push_promise = new byte[]{(byte)5,(byte)10,
            (byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,
            (byte)0, (byte)0};

    /*
    Truncates or expands the frame to the specified length
     */
    private static Object[][] chopFrame(byte[] frame, int... lengths) {
        var result = new Object[lengths.length][];
        for (int i = 0; i< lengths.length; i++) {
            int length = lengths[i];
            byte[] choppedFrame = Arrays.copyOf(frame, length + 2);
            choppedFrame[1] = (byte)length;
            result[i] = new Object[] {choppedFrame, lengths[i]};
        }
        return result;
    }

    /*
    Truncates or expands the byte array to the specified length
     */
    private static Object[][] chopBytes(byte[] bytes, int... lengths) {
        var result = new Object[lengths.length][];
        for (int i = 0; i< lengths.length; i++) {
            int length = lengths[i];
            byte[] choppedBytes = Arrays.copyOf(bytes, length);
            result[i] = new Object[] {choppedBytes, lengths[i]};
        }
        return result;
    }

    @DataProvider
    public static Object[][] malformedSettingsFrames() {
        // 2-byte ID, 2-byte value
        byte[] settingsFrame = new byte[]{(byte)4,(byte)4,(byte)0x40, (byte)6, (byte)0x40, (byte)6};
        return chopFrame(settingsFrame, 1, 2, 3);
    }

    @DataProvider
    public static Object[][] malformedCancelPushFrames() {
        byte[] cancelPush = new byte[]{(byte)3,(byte)2, (byte)0x40, (byte)0};
        return chopFrame(cancelPush, 0, 1, 3, 9);
    }

    @DataProvider
    public static Object[][] malformedGoawayFrames() {
        byte[] goaway = new byte[]{(byte)7,(byte)2, (byte)0x40, (byte)0};
        return chopFrame(goaway, 0, 1, 3, 9);
    }

    @DataProvider
    public static Object[][] malformedResponseHeadersFrames() {
        byte[] responseHeaders = HexFormat.of().parseHex(
                "011a0000"+ // headers, length 26, section prefix
                        "d9"+ // :status:200
                        "5f5094ca3ee35a74a6b589418b5258132b1aa496ca8747"); //user-agent
        return chopFrame(responseHeaders, 0, 1, 4, 5, 6, 7);
    }

    @DataProvider
    public static Object[][] truncatedResponseFrames() {
        byte[] response = HexFormat.of().parseHex(
                "01030000"+ // headers, length 3, section prefix
                        "d9"+ // :status:200
                "000100"+ // data, 1 byte
                "210100" // reserved, 1 byte
                        );
        return chopBytes(response, 1, 2, 3, 4, 6, 7, 9, 10);
    }

    @DataProvider
    public static Object[][] truncatedControlFrames() {
        byte[] response = HexFormat.of().parseHex(
                "00"+ // stream type: control
                        "04022100"+ //settings, reserved
                        "070104"+ //goaway, 4
                        "210100" // reserved, 1 byte
        );
        return chopBytes(response, 2, 3, 4, 6, 7, 9, 10);
    }

    @DataProvider
    public static Object[][] malformedPushPromiseFrames() {
        return chopFrame(valid_push_promise, 0, 1, 2, 4, 5, 6);
    }

    @DataProvider
    public static Object[][] invalidControlFrames() {
        // frames not valid on the server control stream (after settings)
        // all except cancel_push / goaway (max_push_id is client-only)
        return new Object[][] {{data}, {headers}, {settings}, {push_promise}, {max_push_id},
                {reserved1}, {reserved2}, {reserved3}, {reserved4}};
    }

    @DataProvider
    public static Object[][] invalidResponseFrames() {
        // frames not valid on the response stream
        // all except headers / push_promise
        // data is not valid as the first frame
        return new Object[][] {{data}, {cancel_push}, {settings}, {goaway}, {max_push_id},
                {reserved1}, {reserved2}, {reserved3}, {reserved4}};
    }

    @DataProvider
    public static Object[][] invalidPushFrames() {
        // frames not valid on the push promise stream
        // all except headers
        // data is not valid as the first frame
        return new Object[][] {{data}, {cancel_push}, {settings}, {push_promise}, {goaway}, {max_push_id},
                {reserved1}, {reserved2}, {reserved3}, {reserved4}};
    }

    @BeforeClass
    public void beforeClass() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        server = QuicStandaloneServer.newBuilder()
                .availableVersions(new QuicVersion[]{QuicVersion.QUIC_V1})
                .sslContext(sslContext)
                .alpn("h3")
                .build();
        server.start();
        System.out.println("Server started at " + server.getAddress());
        requestURIBase = URIBuilder.newBuilder().scheme("https").loopback()
                .port(server.getAddress().getPort()).build().toString();
    }

    @AfterClass
    public void afterClass() throws Exception {
        if (server != null) {
            System.out.println("Stopping server " + server.getAddress());
            server.close();
        }
    }

    /**
     * Server sends a non-settings frame on the control stream
     */
    @Test
    public void testNonSettingsFrame() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream, reserved frame, length 0
            byte[] bytesToWrite = new byte[] { 0, 0x21, 0 };
            writer.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_MISSING_SETTINGS);
    }

    /**
     * Server opens 2 control streams
     */
    @Test(dataProvider = "controlStreams")
    public void testTwoControlStreams(byte type) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {

            QuicSenderStream controlStream, controlStream2;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            controlStream2 = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            var writer2 = controlStream2.connectWriter(scheduler);
            // control stream
            byte[] bytesToWrite = new byte[] { type };
            writer.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), false);
            writer2.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_STREAM_CREATION_ERROR);
    }

    /**
     * Server closes control stream
     */
    @Test(dataProvider = "controlStreams")
    public void testCloseControlStream(byte type) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var controlscheduler = SequentialScheduler.lockingScheduler(() -> {});
            var writer = controlStream.connectWriter(controlscheduler);

            byte[] bytesToWrite = new byte[] { type };
            writer.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), true);
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_CLOSED_CRITICAL_STREAM);
    }

    public static class RetryOnce implements IRetryAnalyzer {
        boolean retried;

        @Override
        public boolean retry(ITestResult iTestResult) {
            if (!retried) {
                retried = true;
                return true;
            }
            return false;
        }
    }

    /**
     * Server resets control stream
     */
    @Test(dataProvider = "controlStreams", retryAnalyzer = RetryOnce.class)
    public void testResetControlStream(byte type) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var controlscheduler = SequentialScheduler.lockingScheduler(() -> {});
            var writer = controlStream.connectWriter(controlscheduler);

            byte[] bytesToWrite = new byte[] { type };
            writer.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), false);
            // wait for the stream data to be sent before resetting
            System.out.println("Server: sending first ping");
            c.requestSendPing().join();
            // sometimes the first ping succeeds before the stream frame is delivered.
            // Send another one just in case.
            System.out.println("Server: sending second ping");
            c.requestSendPing().join();
            System.out.println("Server: resetting control stream " + writer.stream().streamId());
            // the test may fail if the stream type byte is not processed by HTTP3
            // before the reset is received.
            writer.reset(0);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_CLOSED_CRITICAL_STREAM);
    }

    /**
     * Server sends unexpected frame on control stream
     */
    @Test(dataProvider = "invalidControlFrames")
    public void testUnexpectedControlFrame(byte[] frame) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream, settings frame, length 0
            byte[] bytesToWrite = new byte[] { 0, 4, 0 };
            ByteBuffer buf = ByteBuffer.allocate(3 + frame.length);
            buf.put(bytesToWrite);
            buf.put(frame);
            buf.flip();
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_FRAME_UNEXPECTED);
    }

    /**
     * Server sends malformed settings frame
     */
    @Test(dataProvider = "malformedSettingsFrames")
    public void testMalformedSettingsFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream
            byte[] bytesToWrite = new byte[] { 0 };
            ByteBuffer buf = ByteBuffer.allocate(3 + frame.length);
            buf.put(bytesToWrite);
            buf.put(frame);
            buf.flip();
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_FRAME_ERROR);
    }

    /**
     * Server sends malformed goaway frame
     */
    @Test(dataProvider = "malformedGoawayFrames")
    public void testMalformedGoawayFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream, settings frame, length 0
            byte[] bytesToWrite = new byte[] { 0, 4, 0 };
            ByteBuffer buf = ByteBuffer.allocate(3 + frame.length);
            buf.put(bytesToWrite);
            buf.put(frame);
            buf.flip();
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_FRAME_ERROR);
    }

    /**
     * Server sends malformed cancel push frame
     */
    @Test(dataProvider = "malformedCancelPushFrames")
    public void testMalformedCancelPushFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream, settings frame, length 0
            byte[] bytesToWrite = new byte[] { 0, 4, 0 };
            ByteBuffer buf = ByteBuffer.allocate(3 + frame.length);
            buf.put(bytesToWrite);
            buf.put(frame);
            buf.flip();
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerPushError(errorCF, Http3Error.H3_FRAME_ERROR);
    }

    /**
     * Server sends invalid GOAWAY frame sequence
     */
    @Test
    public void testInvalidGoAwaySequence() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream, settings frame, length 0, GOAWAY, id = 4, GOAWAY, id = 8
            byte[] bytesToWrite = new byte[] { 0, 4, 0, 7, 1, 4, 7, 1, 8};
            ByteBuffer buf = ByteBuffer.wrap(bytesToWrite);
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_ID_ERROR);
    }

    /**
     * Server sends invalid GOAWAY stream ID
     */
    @Test
    public void testInvalidGoAwayId() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream, settings frame, length 0, GOAWAY, id = 7
            byte[] bytesToWrite = new byte[] { 0, 4, 0, 7, 1, 7};
            ByteBuffer buf = ByteBuffer.wrap(bytesToWrite);
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_ID_ERROR);
    }

    /**
     * Server sends invalid CANCEL_PUSH stream ID
     */
    @Test
    public void testInvalidCancelPushId() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream, settings frame, length 0, CANCEL_PUSH, id = MAX_VL_INTEGER
            byte[] bytesToWrite = new byte[] { 0, 4, 0, 3, 8, (byte)255, (byte)255,
                    (byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255};
            ByteBuffer buf = ByteBuffer.wrap(bytesToWrite);
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_ID_ERROR);
    }

    /**
     * Server sends unexpected frame on push stream
     */
    @Test(dataProvider = "invalidPushFrames")
    public void testUnexpectedPushFrame(byte[] frame) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream pushStream;
            pushStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            // write PUSH_PROMISE frame
            s.outputStream().write(valid_push_promise);
            var writer = pushStream.connectWriter(scheduler);
            // push stream, id 0
            byte[] bytesToWrite = new byte[] { 1, 0 };
            ByteBuffer buf = ByteBuffer.allocate(2 + frame.length);
            buf.put(bytesToWrite);
            buf.put(frame);
            buf.flip();
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerPushError(errorCF, Http3Error.H3_FRAME_UNEXPECTED);
    }

    /**
     * Server sends malformed frame on push stream
     */
    @Test(dataProvider = "malformedResponseHeadersFrames")
    public void testMalformedPushStreamFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream pushStream;
            pushStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            // write PUSH_PROMISE frame
            s.outputStream().write(valid_push_promise);
            var writer = pushStream.connectWriter(scheduler);
            // push stream, id 0
            byte[] bytesToWrite = new byte[] { 1, 0 };
            ByteBuffer buf = ByteBuffer.allocate(2 + frame.length);
            buf.put(bytesToWrite);
            buf.put(frame);
            buf.flip();
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerPushError(errorCF, frame.length == 2
                ? Http3Error.H3_FRAME_ERROR
                : Http3Error.QPACK_DECOMPRESSION_FAILED);
    }

    /**
     * Server sends malformed frame on push stream
     */
    @Test(dataProvider = "malformedPushPromiseFrames")
    public void testMalformedPushPromiseFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            // write PUSH_PROMISE frame
            s.outputStream().write(frame);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerPushError(errorCF, frame.length <= 3
                ? Http3Error.H3_FRAME_ERROR
                : Http3Error.QPACK_DECOMPRESSION_FAILED);
    }

    /**
     * Server reuses push stream ID
     */
    @Test
    public void testDuplicatePushStream() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream pushStream, pushStream2;
            pushStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            pushStream2 = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            // write PUSH_PROMISE frame
            s.outputStream().write(valid_push_promise);

            var writer = pushStream.connectWriter(scheduler);
            // push stream, id 0
            byte[] bytesToWrite = new byte[] { 1, 0 };
            writer.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), false);

            writer = pushStream2.connectWriter(scheduler);
            // push stream, id 0
            writer.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerPushError(errorCF, Http3Error.H3_ID_ERROR);
    }

    /**
     * Server sends push promise with ID > MAX_PUSH_ID
     */
    @Test
    public void testInvalidPushPromiseId() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            // write PUSH_PROMISE frame
            s.outputStream().write(huge_id_push_promise);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_ID_ERROR);
    }

    /**
     * Server opens a push stream ID > MAX_PUSH_ID
     */
    @Test
    public void testInvalidPushStreamId() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream pushStream;
            pushStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = pushStream.connectWriter(scheduler);
            // push stream, id MAX_VL_INTEGER
            byte[] bytesToWrite = new byte[] { 1,
                    (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255 };
            ByteBuffer buf = ByteBuffer.wrap(bytesToWrite);
            writer.scheduleForWriting(buf, false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_ID_ERROR);
    }

    /**
     * Server sends unexpected frame on response stream
     */
    @Test(dataProvider = "invalidResponseFrames")
    public void testUnexpectedResponseFrame(byte[] frame) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            s.outputStream().write(frame);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_FRAME_UNEXPECTED);
    }

    /**
     * Server sends malformed headers frame on response stream
     */
    @Test(dataProvider = "malformedResponseHeadersFrames")
    public void testMalformedResponseFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            s.outputStream().write(frame);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, frame.length == 2
                ? Http3Error.H3_FRAME_ERROR
                : Http3Error.QPACK_DECOMPRESSION_FAILED);
    }

    /**
     * Server truncates a frame on the response stream
     */
    @Test(dataProvider = "truncatedResponseFrames")
    public void testTruncatedResponseFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            try (OutputStream outputStream = s.outputStream()) {
                outputStream.write(frame);
            }
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_FRAME_ERROR);
    }

    /**
     * Server truncates a frame on the control stream
     */
    @Test(dataProvider = "truncatedControlFrames")
    public void testTruncatedControlFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var controlscheduler = SequentialScheduler.lockingScheduler(() -> {});
            var writer = controlStream.connectWriter(controlscheduler);

            writer.scheduleForWriting(ByteBuffer.wrap(frame), true);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        // H3_CLOSED_CRITICAL_STREAM is also acceptable here
        triggerError(errorCF, Http3Error.H3_FRAME_ERROR, Http3Error.H3_CLOSED_CRITICAL_STREAM);
    }

    /**
     * Server truncates a frame on the push stream
     */
    @Test(dataProvider = "truncatedResponseFrames")
    public void testTruncatedPushStreamFrame(byte[] frame, int bytes) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream pushStream;
            pushStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            // write PUSH_PROMISE frame
            s.outputStream().write(valid_push_promise);
            var writer = pushStream.connectWriter(scheduler);
            // push stream, id 0
            byte[] bytesToWrite = new byte[] { 1, 0 };
            ByteBuffer buf = ByteBuffer.allocate(2 + frame.length);
            buf.put(bytesToWrite);
            buf.put(frame);
            buf.flip();
            writer.scheduleForWriting(buf, true);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerPushError(errorCF, Http3Error.H3_FRAME_ERROR);
    }

    /**
     * Server sends a settings frame with reserved HTTP2 settings
     */
    @Test
    public void testReservedSettingsFrames() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream controlStream;
            controlStream = c.openNewLocalUniStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = controlStream.connectWriter(scheduler);
            // control stream, settings frame, length 2, setting 4 = 0
            byte[] bytesToWrite = new byte[] { 0, 4, 2, 4, 0 };
            writer.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_SETTINGS_ERROR);
    }

    /**
     * Server sends a stateless reset
     */
    @Test
    public void testStatelessReset() throws Exception {
        server.addHandler((c,s)-> {
            // stateless reset
            QuicConnectionId localConnId = c.localConnectionId();
            ByteBuffer resetDatagram = c.endpoint().idFactory().statelessReset(localConnId.asReadOnlyBuffer(), 43);
            ((DatagramChannel)c.channel()).send(resetDatagram, c.peerAddress());
            // ignore the request stream; we're expecting the client to close the connection.
            // The server won't receive any notification from the client here.
            // The connection will leak.
        });
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<Void> response = client.sendAsync(
                            request,
                            BodyHandlers.discarding())
                    .get(Utils.adjustTimeout(10), TimeUnit.SECONDS);
            fail("Expected the request to fail, got " + response);
        } catch (Exception e) {
            final String expectedMsg = "stateless reset from peer";
            if (e.getMessage() != null && e.getMessage().contains(expectedMsg)) {
                // got the expected exception
                return;
            }
            // unexpected exception, throw it back
            throw e;
        } finally {
            client.shutdownNow();
        }
    }

    /**
     * Server opens a bidi stream
     */
    @Test
    @Ignore("BiDi streams are rejected by H3 client at QUIC level")
    public void testBidiStream() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            QuicSenderStream bidiStream;
            bidiStream = c.openNewLocalBidiStream(Duration.ZERO).resultNow();
            var scheduler = SequentialScheduler.lockingScheduler(() -> {
            });
            var writer = bidiStream.connectWriter(scheduler);
            // some data
            byte[] bytesToWrite = new byte[] { 0, 4, 2, 4, 0 };
            writer.scheduleForWriting(ByteBuffer.wrap(bytesToWrite), false);
            // ignore the request stream; we're expecting the client to close the connection
            completeUponTermination(c, errorCF);
        });
        triggerError(errorCF, Http3Error.H3_STREAM_CREATION_ERROR);
    }

    /**
     * Server closes the connection with a known QUIC error
     */
    @Test
    public void testConnectionCloseQUIC() throws Exception {
        server.addHandler((c,s)-> {
            TerminationCause tc = TerminationCause.forException(
                    new QuicTransportException("ignored", null, 0,
                            QuicTransportErrors.INTERNAL_ERROR)
            );
            tc.peerVisibleReason("testtest");
            c.connectionTerminator().terminate(tc);

        });
        triggerClose("INTERNAL_ERROR", "testtest");
    }

    /**
     * Server closes the connection with a known crypto error
     */
    @Test
    public void testConnectionCloseCryptoQUIC() throws Exception {
        server.addHandler((c,s)-> {
            TerminationCause tc = TerminationCause.forException(
                    new QuicTransportException("ignored", null, 0,
                            QuicTransportErrors.CRYPTO_ERROR.from() + 80 /*Alert.INTERNAL_ERROR.id*/, null)
            );
            tc.peerVisibleReason("testtest");
            c.connectionTerminator().terminate(tc);

        });
        triggerClose("CRYPTO_ERROR", "internal_error", "testtest");
    }

    /**
     * Server closes the connection with an unknown crypto error
     */
    @Test
    public void testConnectionCloseUnknownCryptoQUIC() throws Exception {
        server.addHandler((c,s)-> {
            TerminationCause tc = TerminationCause.forException(
                    new QuicTransportException("ignored", null, 0,
                            QuicTransportErrors.CRYPTO_ERROR.from() + 5, null)
            );
            tc.peerVisibleReason("testtest");
            c.connectionTerminator().terminate(tc);

        });
        triggerClose("CRYPTO_ERROR", "5", "testtest");
    }

    /**
     * Server closes the connection with an unknown QUIC error
     */
    @Test
    public void testConnectionCloseUnknownQUIC() throws Exception {
        server.addHandler((c,s)-> {
            TerminationCause tc = TerminationCause.forException(
                    new QuicTransportException("ignored", null, 0,
                            QuicTransportErrors.CRYPTO_ERROR.to() + 1 /*0x200*/, null)
            );
            tc.peerVisibleReason("testtest");
            c.connectionTerminator().terminate(tc);

        });
        triggerClose("200", "testtest");
    }

    /**
     * Server closes the connection with a known H3 error
     */
    @Test
    public void testConnectionCloseH3() throws Exception {
        server.addHandler((c,s)-> {
            TerminationCause tc = TerminationCause.appLayerClose(Http3Error.H3_EXCESSIVE_LOAD.code());
            tc.peerVisibleReason("testtest");
            c.connectionTerminator().terminate(tc);

        });
        triggerClose("H3_EXCESSIVE_LOAD", "testtest");
    }

    /**
     * Server closes the connection with an unknown H3 error
     */
    @Test
    public void testConnectionCloseH3Unknown() throws Exception {
        server.addHandler((c,s)-> {
            TerminationCause tc = TerminationCause.appLayerClose(0x1f21);
            tc.peerVisibleReason("testtest");
            c.connectionTerminator().terminate(tc);

        });
        triggerClose("1F21", "testtest");
    }


    private void triggerClose(String... reasons) throws Exception {
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<Void> response = client.sendAsync(
                            request,
                            BodyHandlers.discarding())
                    .get(Utils.adjustTimeout(10), TimeUnit.SECONDS);
            fail("Expected the request to fail, got " + response);
        } catch (ExecutionException e) {
            System.out.println("Client exception [expected]: " + e);
            var cause = e.getCause();
            assertTrue(cause instanceof IOException, "Expected IOException");
            for (String reason : reasons) {
                assertTrue(cause.getMessage().contains(reason),
                        cause.getMessage() + " does not contain " + reason);
            }
        } finally {
            client.shutdownNow();
        }
    }


    private void triggerError(CompletableFuture<TerminationCause> errorCF, Http3Error expected) throws Exception {
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<Void> response = client.sendAsync(
                    request,
                    BodyHandlers.discarding())
                    .get(Utils.adjustTimeout(20), TimeUnit.SECONDS);
            fail("Expected the request to fail, got " + response);
        } catch (ExecutionException e) {
            System.out.println("Client exception [expected]: " + e);
            var cause = e.getCause();
            assertTrue(cause instanceof ProtocolException, "Expected ProtocolException");
            TerminationCause terminationCause = errorCF.get(Utils.adjustTimeout(10), TimeUnit.SECONDS);
            System.out.println("Server reason: \"" + terminationCause.getPeerVisibleReason()+'"');
            final long actual = terminationCause.getCloseCode();
            // expected
            assertEquals(actual, expected.code(), "Expected " + toHexString(expected) + " got 0x" + Long.toHexString(actual));
        } finally {
            client.shutdownNow();
        }
    }

    private void triggerError(CompletableFuture<TerminationCause> errorCF, Http3Error... expected) throws Exception {
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<Void> response = client.sendAsync(
                            request,
                            BodyHandlers.discarding())
                    .get(Utils.adjustTimeout(10), TimeUnit.SECONDS);
            fail("Expected the request to fail, got " + response);
        } catch (ExecutionException e) {
            System.out.println("Client exception [expected]: " + e);
            var cause = e.getCause();
            assertTrue(cause instanceof ProtocolException, "Expected ProtocolException");
            TerminationCause terminationCause = errorCF.get(Utils.adjustTimeout(10), TimeUnit.SECONDS);
            System.out.println("Server reason: \"" + terminationCause.getPeerVisibleReason()+'"');
            final long actual = terminationCause.getCloseCode();
            // expected
            Optional<Http3Error> h3Actual = Http3Error.fromCode(actual);
            assertTrue(h3Actual.isPresent(), "Expected HTTP3 error, got 0x" + Long.toHexString(actual));
            Set<Http3Error> expectedErrors = Set.of(expected);
            assertTrue(expectedErrors.contains(h3Actual.get()), "Expected "+expectedErrors+
                    ", got: "+h3Actual);
        } finally {
            client.shutdownNow();
        }
    }

    private void triggerPushError(CompletableFuture<TerminationCause> errorCF, Http3Error http3Error) throws Exception {
        HttpClient client = getHttpClient();
        // close might block; use shutdownNow instead
        try {
            HttpRequest request = getRequest();
            final HttpResponse<Void> response = client.sendAsync(
                    request,
                    BodyHandlers.discarding(),
                    (initiatingRequest, pushPromiseRequest, acceptor) ->
                            acceptor.apply(BodyHandlers.discarding())
            ).get(Utils.adjustTimeout(10), TimeUnit.SECONDS);
            fail("Expected the request to fail, got " + response);
        } catch (ExecutionException e) {
            System.out.println("Client exception [expected]: " + e);
            var cause = e.getCause();
            assertTrue(cause instanceof ProtocolException, "Expected ProtocolException");
            TerminationCause terminationCause = errorCF.get(Utils.adjustTimeout(10), TimeUnit.SECONDS);
            System.out.println("Server reason: \"" + terminationCause.getPeerVisibleReason()+'"');
            final long actual = terminationCause.getCloseCode();
            // expected
            assertEquals(actual, http3Error.code(), "Expected " + toHexString(http3Error) + " got 0x" + Long.toHexString(actual));
        } finally {
            client.shutdownNow();
        }
    }

    private HttpRequest getRequest() throws URISyntaxException {
        final URI reqURI = new URI(requestURIBase + "/hello");
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(reqURI)
                .version(HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        return reqBuilder.build();
    }

    private HttpClient getHttpClient() {
        final HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(HTTP_3)
                .sslContext(sslContext).build();
        return client;
    }

    private static String toHexString(final Http3Error error) {
        return error.name() + "(0x" + Long.toHexString(error.code()) + ")";
    }

    private static void completeUponTermination(final QuicServerConnection serverConnection,
                                                final CompletableFuture<TerminationCause> cf) {
        serverConnection.futureTerminationCause().handle(
                (r,t) -> t != null ? cf.completeExceptionally(t) : cf.complete(r));
    }
}
