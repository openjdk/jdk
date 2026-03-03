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

import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.Encoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @modules java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.net.http/jdk.internal.net.http.qpack:+open
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 * @build EncoderDecoderConnector
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=EXTRA BlockingDecodingTest
 */


public class BlockingDecodingTest {
    @Test
    public void blockedStreamsSettingDefaultValueTest() throws Exception {
        // Default SETTINGS_QPACK_BLOCKED_STREAMS value (0) doesn't allow blocked streams
        var encoderEh = new TestErrorHandler();
        var decoderEh = new TestErrorHandler();
        var streamError = new AtomicReference<Throwable>();
        EncoderDecoderConnector.EncoderDecoderPair pair =
                newPreconfiguredEncoderDecoder(encoderEh, decoderEh,
                        streamError, -1L, 1);

        // Get Encoder and Decoder instances from a newly established connector
        var encoder = pair.encoder();
        var decoder = pair.decoder();

        // Create a decoding callback to check for completion and to log failures
        TestDecodingCallback decodingCallback = new TestDecodingCallback();
        // Start encoding Headers Frame
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var headerFrameReader = decoder.newHeaderFrameReader(decodingCallback);

        // create encoding context and buffer to hold encoded headers
        List<ByteBuffer> buffers = new ArrayList<>();

        ByteBuffer headersBb = ByteBuffer.allocate(2048);
        Encoder.EncodingContext context =
                encoder.newEncodingContext(0, 0, headerFrameWriter);
        var header = TestHeader.withId(0);
        encoder.header(context, header.name(), header.value(),
                false, IGNORE_RECEIVED_COUNT_CHECK);
        headerFrameWriter.write(headersBb);
        assertNotEquals(0, headersBb.position());
        headersBb.flip();
        buffers.add(headersBb);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);

        // It is expected to get QPACK_DECOMPRESSION_FAILED here since decoder is
        // expected to be blocked due to missing entry with index 0 in the decoder table,
        // and the default number of blocked streams (0) will be exceeded (1).
        var lastHttp3Error = decodingCallback.lastHttp3Error.get();
        System.err.println("Last Http3Error: " + lastHttp3Error);
        Assertions.assertEquals(Http3Error.QPACK_DECOMPRESSION_FAILED, lastHttp3Error);
        Assertions.assertFalse(decodingCallback.completed.isDone());
    }

    @Test
    public void noBlockedStreamsTest() throws Exception {
        // No blocked streams - with default SETTINGS_QPACK_BLOCKED_STREAMS value
        // Default SETTINGS_QPACK_BLOCKED_STREAMS value (0) doesn't allow blocked streams
        var encoderEh = new TestErrorHandler();
        var decoderEh = new TestErrorHandler();
        var streamError = new AtomicReference<Throwable>();
        EncoderDecoderConnector.EncoderDecoderPair pair =
                newPreconfiguredEncoderDecoder(encoderEh, decoderEh,
                        streamError, -1L, 1);

        // Populate decoder table with an entry - there should be no blocked streams
        // observed during decoding
        prepopulateDynamicTable(pair.decoderTable(), 1);

        // Get Encoder and Decoder instances from a newly established connector
        var encoder = pair.encoder();
        var decoder = pair.decoder();

        // Create a decoding callback to check for completion and to log failures
        TestDecodingCallback decodingCallback = new TestDecodingCallback();
        // Start encoding Headers Frame
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var headerFrameReader = decoder.newHeaderFrameReader(decodingCallback);

        // create encoding context and buffer to hold encoded headers
        List<ByteBuffer> buffers = new ArrayList<>();

        ByteBuffer headersBb = ByteBuffer.allocate(2048);
        Encoder.EncodingContext context =
                encoder.newEncodingContext(0, 0, headerFrameWriter);
        var expectedHeader = TestHeader.withId(0);
        encoder.header(context, expectedHeader.name,
                expectedHeader.value, false, IGNORE_RECEIVED_COUNT_CHECK);
        headerFrameWriter.write(headersBb);
        assertNotEquals(0, headersBb.position());
        headersBb.flip();
        buffers.add(headersBb);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);

        // It is expected to get QPACK_DECOMPRESSION_FAILED here since decoder is
        // expected to be blocked due to missing entry with index 0 in the decoder table,
        // and the default number of blocked streams (0) will be exceeded (1).
        var lastHttp3Error = decodingCallback.lastHttp3Error.get();
        System.err.println("Last Http3Error: " + lastHttp3Error);
        Assertions.assertNull(lastHttp3Error);
        Assertions.assertNull(decodingCallback.lastThrowable.get());
        Assertions.assertTrue(decodingCallback.completed.isDone());
        // Check that onDecoded was called for the test entry
        var decodedHeader = decodingCallback.decodedHeaders.get(0);
        Assertions.assertEquals(expectedHeader, decodedHeader);
    }

    @Test
    public void awaitBlockedStreamsTest() throws Exception {
        // Max number of blocked streams is not exceeded
        // No blocked streams - with default SETTINGS_QPACK_BLOCKED_STREAMS value
        // Default SETTINGS_QPACK_BLOCKED_STREAMS value (0) doesn't allow blocked streams
        final int numberOfMaxAllowedBlockedStreams = 5;
        final int numberOfHeaders = 4;
        final int base = 2;
        var encoderEh = new TestErrorHandler();
        var decoderEh = new TestErrorHandler();
        var streamError = new AtomicReference<Throwable>();
        EncoderDecoderConnector.EncoderDecoderPair pair =
                newPreconfiguredEncoderDecoder(encoderEh, decoderEh,
                        streamError, numberOfMaxAllowedBlockedStreams, numberOfHeaders);

        // Create list of headers to encode for each thread
        List<TestHeader> expectedHeaders = Collections.synchronizedList(new ArrayList<>());
        for (int headerId = 0; headerId < numberOfHeaders; headerId++) {
            expectedHeaders.add(TestHeader.withId(headerId));
        }

        // Create virtual threads executor
        var vtExecutor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<TestDecodingCallback>> decodingTaskResults = new ArrayList<>();

        // Create 10 blocked tasks
        for (int taskCount = 0; taskCount < numberOfMaxAllowedBlockedStreams; taskCount++) {
            var decodingTask = new Callable<TestDecodingCallback>() {
                final EncoderDecoderConnector.EncoderDecoderPair ed = pair;
                @Override
                public TestDecodingCallback call() throws Exception {
                    var encoder = ed.encoder();
                    var decoder = ed.decoder();
                    // Create a decoding callback to check for completion and to log failures
                    TestDecodingCallback decodingCallback = new TestDecodingCallback();
                    // Start encoding Headers Frame
                    var headerFrameWriter = encoder.newHeaderFrameWriter();
                    var headerFrameReader = decoder.newHeaderFrameReader(decodingCallback);

                    // create encoding context and buffer to hold encoded headers
                    List<ByteBuffer> buffers = new ArrayList<>();

                    ByteBuffer headersBb = ByteBuffer.allocate(2048);
                    Encoder.EncodingContext context =
                            encoder.newEncodingContext(0, base, headerFrameWriter);

                    for (var header : expectedHeaders) {
                        encoder.header(context, header.name, header.value, false,
                                IGNORE_RECEIVED_COUNT_CHECK);
                        headerFrameWriter.write(headersBb);
                    }
                    assertNotEquals(0, headersBb.position());
                    headersBb.flip();
                    buffers.add(headersBb);

                    // Generate field section prefix bytes
                    encoder.generateFieldLineSectionPrefix(context, buffers);

                    // Decode headers
                    decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
                    decoder.decodeHeader(buffers.get(1), true, headerFrameReader);
                    return decodingCallback;
                }
            };
            decodingTaskResults.add(vtExecutor.submit(decodingTask));
        }

        // Schedule the delayed update to the decoders dynamic table
        var delayedExecutor = CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS,
                vtExecutor);
        AtomicLong updateDoneTimestamp = new AtomicLong();
        delayedExecutor.execute(() -> {
            updateDoneTimestamp.set(System.nanoTime());
            prepopulateDynamicTable(pair.decoderTable(), numberOfHeaders);
        });

        // Await completion of all tasks
        for (var decodingResultFuture : decodingTaskResults) {
            decodingResultFuture.get().completed.get();
        }
        // Acquire the timestamp
        long updateDoneTimeStamp = updateDoneTimestamp.get();

        System.err.println("All decoding tasks are done");
        System.err.println("Decoder table update timestamp: " + updateDoneTimeStamp);
        // Check results of each decoding task
        for (var decodingResultFuture : decodingTaskResults) {
            var taskCallback = decodingResultFuture.get();
            Assertions.assertNull(taskCallback.lastHttp3Error.get());
            Assertions.assertNull(taskCallback.lastThrowable.get());
            long decodingTaskCompleted = taskCallback.completedTimestamp.get();
            System.err.println("Decoding task completion timestamp: " + decodingTaskCompleted);
            Assertions.assertTrue(decodingTaskCompleted >= updateDoneTimeStamp);
            var decodedHeaders = taskCallback.decodedHeaders;
            Assertions.assertEquals(expectedHeaders, decodedHeaders);
        }
    }

    private static EncoderDecoderConnector.EncoderDecoderPair newPreconfiguredEncoderDecoder(
            TestErrorHandler encoderEh,
            TestErrorHandler decoderEh,
            AtomicReference<Throwable> streamError,
            long maxBlockedStreams,
            int numberOfEntriesInEncoderDT) {
        EncoderDecoderConnector conn = new EncoderDecoderConnector();
        var pair = conn.newEncoderDecoderPair(
                e -> false,
                encoderEh::qpackErrorHandler,
                decoderEh::qpackErrorHandler,
                streamError::set);
        // Create settings frame with dynamic table capacity and number of blocked streams
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        // 4k should be enough for storing dynamic table entries added by 'prepopulateDynamicTable'
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, DT_CAPACITY);
        if (maxBlockedStreams > 0) {
            // Set max number of blocked decoder streams if the provided value is positive, otherwise
            // use the default RFC setting which is 0
            settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_BLOCKED_STREAMS, maxBlockedStreams);
        }
        ConnectionSettings settings = ConnectionSettings.createFrom(settingsFrame);

        // Configure encoder and decoder with constructed ConnectionSettings
        pair.encoder().configure(settings);
        pair.decoder().configure(settings);
        pair.encoderTable().setCapacity(DT_CAPACITY);
        pair.decoderTable().setCapacity(DT_CAPACITY);

        // Prepopulate encoder dynamic table with test entries. Decoder dynamic table will be pre-populated with
        // a test-case specific code to reproduce blocked decoding scenario
        prepopulateDynamicTable(pair.encoderTable(), numberOfEntriesInEncoderDT);

        return pair;
    }

    private static void prepopulateDynamicTable(DynamicTable dynamicTable, int numEntries) {
        for (int count = 0; count < numEntries; count++) {
            var header = TestHeader.withId(count);
            dynamicTable.insert(header.name(), header.value());
        }
    }

    private static class TestDecodingCallback implements DecodingCallback {

        final List<TestHeader> decodedHeaders = new CopyOnWriteArrayList<>();
        final CompletableFuture<Void> completed = new CompletableFuture<>();
        final AtomicLong completedTimestamp = new AtomicLong();

        final AtomicReference<Throwable> lastThrowable = new AtomicReference<>();
        final AtomicReference<Http3Error> lastHttp3Error = new AtomicReference<>();

        @Override
        public void onDecoded(CharSequence name, CharSequence value) {
            var nameValue = new TestHeader(name.toString(), value.toString());
            decodedHeaders.add(nameValue);
            System.err.println("Decoding callback 'onDecoded': " + nameValue);
        }

        @Override
        public void onComplete() {
            System.err.println("Decoding callback 'onComplete'");
            completedTimestamp.set(System.nanoTime());
            completed.complete(null);
        }

        @Override
        public void onConnectionError(Throwable throwable, Http3Error http3Error) {
            System.err.println("Decoding callback 'onError': " + http3Error);
            lastThrowable.set(throwable);
            lastHttp3Error.set(http3Error);
        }

        @Override
        public long streamId() {
            return 0;
        }
    }

    private static class TestErrorHandler {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<Http3Error> http3Error = new AtomicReference<>();

        public void qpackErrorHandler(Throwable error, Http3Error http3Error) {
            this.error.set(error);
            this.http3Error.set(http3Error);
            throw new RuntimeException("http3 error: " + http3Error, error);
        }
    }

    record TestHeader(String name, String value) {
        public static TestHeader withId(int id) {
            return new TestHeader(NAME + id, VALUE + id);
        }
    }

    private static final String NAME = "test";
    private static final String VALUE = "valueTest";
    private static final long DT_CAPACITY = 4096L;
    private static final long IGNORE_RECEIVED_COUNT_CHECK = -1L;
}
