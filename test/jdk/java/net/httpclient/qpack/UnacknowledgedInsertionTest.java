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
import jdk.internal.net.http.qpack.Encoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @summary check that unacknowledged header is not inserted
 *          twice to the dynamic table
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
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=EXTRA UnacknowledgedInsertionTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UnacknowledgedInsertionTest {

    @ParameterizedTest
    @MethodSource("duplicateEntryInsertionsData")
    public void unacknowledgedDoubleInsertion(long knownReceiveCount, List<EncodingType> expectedHeadersEncodingType) throws Exception {
        // When knownReceiveCount is set to -1 the Encoder.knownReceiveCount()
        // value is used to encode headers - otherwise the provided value is used
        var encoderEh = new UnacknowledgedInsertionTest.TestErrorHandler();
        var decoderEh = new UnacknowledgedInsertionTest.TestErrorHandler();
        var streamError = new AtomicReference<Throwable>();
        EncoderDecoderConnector.EncoderDecoderPair ed =
                newPreconfiguredEncoderDecoder(encoderEh, decoderEh, streamError);


        var encoder = ed.encoder();
        var decoder = ed.decoder();
        // Create a decoding callback to check for completion and to log failures
        UnacknowledgedInsertionTest.TestDecodingCallback decodingCallback =
                new UnacknowledgedInsertionTest.TestDecodingCallback();
        // Start encoding Headers Frame
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var headerFrameReader = decoder.newHeaderFrameReader(decodingCallback);

        // create encoding context and buffer to hold encoded headers
        List<ByteBuffer> buffers = new ArrayList<>();

        ByteBuffer headersBb = ByteBuffer.allocate(2048);
        Encoder.EncodingContext context = encoder.newEncodingContext(0, 0,
                headerFrameWriter);

        String name = "name";
        String value = "value";

        for (int i = 0; i < 3; i++) {
            long krcToUse = knownReceiveCount == -1L ? encoder.knownReceivedCount() : knownReceiveCount;
            if (i == 1) {
                encoder.header(context, name, "nameMatchOnly", false, krcToUse);
            } else {
                encoder.header(context, name, value, false, krcToUse);
            }
            headerFrameWriter.write(headersBb);
        }

        // Only two entries are expected to be inserted to the dynamic table
        Assertions.assertEquals(2, ed.decoderTable().insertCount());

        // Check that headers byte buffer is not empty
        assertNotEquals(0, headersBb.position());
        headersBb.flip();
        buffers.add(headersBb);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Use decoder to process generated byte buffers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);

        var actualHeaderEncodingTypes = decodingCallback.decodedHeaders
                .stream()
                .map(DecodedHeader::encodingType)
                .toList();
        Assertions.assertEquals(expectedHeadersEncodingType, actualHeaderEncodingTypes);
    }


    private Object[][] duplicateEntryInsertionsData() {
        return new Object[][]{
                {0, List.of(EncodingType.LITERAL, EncodingType.LITERAL, EncodingType.LITERAL)},
                {-1, List.of(EncodingType.LITERAL, EncodingType.NAME_REF, EncodingType.INDEXED)},
        };
    }

    private static EncoderDecoderConnector.EncoderDecoderPair newPreconfiguredEncoderDecoder(
            UnacknowledgedInsertionTest.TestErrorHandler encoderEh,
            UnacknowledgedInsertionTest.TestErrorHandler decoderEh,
            AtomicReference<Throwable> streamError) {
        EncoderDecoderConnector conn = new EncoderDecoderConnector();
        var pair = conn.newEncoderDecoderPair(
                e -> true,
                encoderEh::qpackErrorHandler,
                decoderEh::qpackErrorHandler,
                streamError::set);
        // Create settings frame with dynamic table capacity and number of blocked streams
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        // 4k should be enough for storing dynamic table entries added by 'prepopulateDynamicTable'
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, DT_CAPACITY);
        ConnectionSettings settings = ConnectionSettings.createFrom(settingsFrame);

        // Configure encoder and decoder with constructed ConnectionSettings
        pair.encoder().configure(settings);
        pair.decoder().configure(settings);
        pair.encoderTable().setCapacity(DT_CAPACITY);
        pair.decoderTable().setCapacity(DT_CAPACITY);

        return pair;
    }

    private static class TestDecodingCallback implements DecodingCallback {

        final List<UnacknowledgedInsertionTest.DecodedHeader> decodedHeaders = new CopyOnWriteArrayList<>();
        final CompletableFuture<Void> completed = new CompletableFuture<>();
        final AtomicLong completedTimestamp = new AtomicLong();

        final AtomicReference<Throwable> lastThrowable = new AtomicReference<>();
        final AtomicReference<Http3Error> lastHttp3Error = new AtomicReference<>();

        @Override
        public void onDecoded(CharSequence name, CharSequence value) {
            Assertions.fail("onDecoded not expected to be called");
        }

        @Override
        public void onLiteralWithLiteralName(CharSequence name, boolean nameHuffman,
                                             CharSequence value, boolean valueHuffman,
                                             boolean hideIntermediary) {
            var header = new DecodedHeader(name.toString(), value.toString(), EncodingType.LITERAL);
            decodedHeaders.add(header);
            System.err.println("Decoding callback 'onLiteralWithLiteralName': " + header);
        }

        @Override
        public void onLiteralWithNameReference(long index,
                                               CharSequence name,
                                               CharSequence value,
                                               boolean valueHuffman,
                                               boolean hideIntermediary) {
            var header = new DecodedHeader(name.toString(), value.toString(), EncodingType.NAME_REF);
            decodedHeaders.add(header);
            System.err.println("Decoding callback 'onLiteralWithNameReference': " + header);

        }

        @Override
        public void onIndexed(long index, CharSequence name, CharSequence value) {
            var header = new DecodedHeader(name.toString(), value.toString(), EncodingType.INDEXED);
            decodedHeaders.add(header);
            System.err.println("Decoding callback 'onIndexed': " + header);
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

    private record DecodedHeader(String name, String value, EncodingType encodingType) {
    }

    enum EncodingType {
        LITERAL,
        NAME_REF,
        INDEXED
    }

    private static final long DT_CAPACITY = 4096L;
}
