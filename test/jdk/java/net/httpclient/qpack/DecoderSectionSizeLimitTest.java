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

/*
 * @test
 * @key randomness
 * @library /test/lib
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
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=NORMAL
 *                    DecoderSectionSizeLimitTest
 */

import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.Encoder;
import jdk.test.lib.RandomFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DecoderSectionSizeLimitTest {
    @ParameterizedTest
    @MethodSource("headerSequences")
    public void fieldSectionSizeLimitExceeded(List<TestHeader> headersSequence,
                                              long maxFieldSectionSize) {

        boolean decoderErrorExpected =
                maxFieldSectionSize > 0 && maxFieldSectionSize < REQUIRED_FIELD_SECTION_SIZE;

        System.err.println("=".repeat(50));
        System.err.println("Max Field Section Size = " + maxFieldSectionSize);
        System.err.println("Max Field Section Size is" + (decoderErrorExpected ? " not" : "") +
                           " enough to encode headers");

        AtomicReference<Throwable> error = new AtomicReference<>();
        EncoderDecoderConnector encoderDecoderConnector = new EncoderDecoderConnector();
        DecoderSectionSizeLimitTest.TestErrorHandler encoderErrorHandler =
                new DecoderSectionSizeLimitTest.TestErrorHandler();
        DecoderSectionSizeLimitTest.TestErrorHandler decoderErrorHandler =
                new DecoderSectionSizeLimitTest.TestErrorHandler();
        var conn = encoderDecoderConnector.newEncoderDecoderPair(entry -> false,
                encoderErrorHandler::qpackErrorHandler, decoderErrorHandler::qpackErrorHandler,
                error::set);

        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // This test emulates a scenario with an Encoder that doesn't respect
        // the SETTINGS_MAX_FIELD_SECTION_SIZE setting value while encoding the headers frame
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, 512L);
        settingsFrame.setParameter(SettingsFrame.SETTINGS_MAX_FIELD_SECTION_SIZE, maxFieldSectionSize);
        ConnectionSettings decoderConnectionSetting = ConnectionSettings.createFrom(settingsFrame);

        // Encoder imposes no limit on the field section size
        settingsFrame.setParameter(SettingsFrame.SETTINGS_MAX_FIELD_SECTION_SIZE, -1L);
        ConnectionSettings encoderConnectionSetting = ConnectionSettings.createFrom(settingsFrame);

        // Configure encoder and decoder
        encoder.configure(encoderConnectionSetting);
        decoder.configure(decoderConnectionSetting);

        // Configure dynamic tables
        configureDynamicTable(conn.encoderTable());
        configureDynamicTable(conn.decoderTable());

        // Encode headers
        // Create header frame writer
        var headerFrameWriter = encoder.newHeaderFrameWriter();

        // create encoding context
        Encoder.EncodingContext context = encoder.newEncodingContext(
                0, BASE, headerFrameWriter);

        ByteBuffer buffer = ByteBuffer.allocate(RANDOM.nextInt(1, 65));
        List<ByteBuffer> buffers = new ArrayList<>();
        for (TestHeader header : headersSequence) {
            // Configures encoder for writing the header name:value pair
            encoder.header(context, header.name, header.value,
                    false, -1L);

            // Write the header
            while (!headerFrameWriter.write(buffer)) {
                buffer.flip();
                buffers.add(buffer);
                buffer = ByteBuffer.allocate(RANDOM.nextInt(1, 65));
            }
        }
        buffer.flip();
        buffers.add(buffer);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);
        System.err.println("Number of generated header buffers:" + buffers.size());

        // Decode header buffers and check if expected HTTP/3 error is reported
        // via decoding callback
        var decodingCallback = new TestDecodingCallback();
        var headerFrameReader = decoder.newHeaderFrameReader(decodingCallback);
        for (int bufferIdx = 0; bufferIdx < buffers.size(); bufferIdx++) {
            headerFrameReader.read(buffers.get(bufferIdx),
                    bufferIdx == buffers.size() - 1);
            Http3Error decodingError = decodingCallback.lastHttp3Error.get();
            if (decodingError != null) {
                System.err.printf("Decoding error observed during buffer #%d processing: %s throwable: %s%n",
                        bufferIdx, decodingError, decodingCallback.lastThrowable.get());
                if (decoderErrorExpected) {
                    Assertions.assertEquals(Http3Error.QPACK_DECOMPRESSION_FAILED, decodingError);
                    return;
                } else {
                    Assertions.fail("No HTTP/3 error was expected");
                }
            } else {
                System.err.println("Buffer #" + bufferIdx + " readout completed without errors");
            }
        }
        if (decoderErrorExpected) {
            Assertions.fail("HTTP/3 error was expected but was not observed");
        }
    }

    public Object[][] headerSequences() {
        List<Object[]> testCases = new ArrayList<>();
        for (var sequence : generateHeaderSequences()) {
            // Decoding should complete without failure
            testCases.add(new Object[]{sequence, -1L});
            // No failure since it is enough bytes specified in the SETTINGS_MAX_FIELD_SECTION_SIZE
            // setting value
            testCases.add(new Object[]{sequence, REQUIRED_FIELD_SECTION_SIZE});
            // Failure is expected - not enough bytes specified in the SETTINGS_MAX_FIELD_SECTION_SIZE
            // setting value
            testCases.add(new Object[]{sequence, REQUIRED_FIELD_SECTION_SIZE - 1});
        }
        return testCases.toArray(Object[][]::new);
    }

    private static List<List<TestHeader>> generateHeaderSequences() {
        List<List<TestHeader>> headersSequences = new ArrayList<>();
        headersSequences.add(TEST_HEADERS);
        // startIndex == 0 - the TEST_HEADERS sequence that is already
        // added to the sequences list
        for (int startIndex = 1; startIndex < TEST_HEADERS.size(); startIndex++) {
            List<TestHeader> firstPart = TEST_HEADERS.subList(startIndex, TEST_HEADERS.size());
            List<TestHeader> secondPart = TEST_HEADERS.subList(0, startIndex);
            List<TestHeader> sequence = new ArrayList<>();
            sequence.addAll(firstPart);
            sequence.addAll(secondPart);
            headersSequences.add(sequence);
        }
        return headersSequences;
    }

    record TestHeader(String name, String value, long size) {
        public TestHeader(String name, String value) {
            this(name, value, name.length() + value.length() + 32L);
        }
    }

    private static void configureDynamicTable(DynamicTable table) {
        table.setCapacity(512L);
        table.insert(NAME_IN_TABLE, VALUE_IN_TABLE);
        table.insert(NAME_IN_TABLE_POSTBASE, VALUE_IN_TABLE_POSTBASE);
    }

    private static class TestErrorHandler {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<Http3Error> http3Error = new AtomicReference<>();

        public void qpackErrorHandler(Throwable error, Http3Error http3Error) {
            this.error.set(error);
            this.http3Error.set(http3Error);
        }
    }

    private static class TestDecodingCallback implements DecodingCallback {

        final AtomicReference<Http3Error> lastHttp3Error = new AtomicReference<>();
        final AtomicReference<Throwable> lastThrowable = new AtomicReference<>();

        @Override
        public void onDecoded(CharSequence name, CharSequence value) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onConnectionError(Throwable throwable, Http3Error http3Error) {
            lastHttp3Error.set(http3Error);
            lastThrowable.set(throwable);
        }

        @Override
        public long streamId() {
            return 0;
        }
    }

    private static final String NAME_IN_TABLE = "HEADER_NAME_FROM_TABLE";
    private static final String VALUE_IN_TABLE = "HEADER_VALUE_FROM_TABLE";
    private static final String NAME_IN_TABLE_POSTBASE = "HEADER_NAME_FROM_TABLE_POSTBASE";
    private static final String VALUE_IN_TABLE_POSTBASE = "HEADER_VALUE_FROM_TABLE_POSTBASE";
    private static final String NAME_NOT_IN_TABLE = "NAME_NOT_IN_TABLE";
    private static final String VALUE_NOT_IN_TABLE = "VALUE_NOT_IN_TABLE";

    private static List<TestHeader> TEST_HEADERS = List.of(
            // Relative index
            new TestHeader(NAME_IN_TABLE, VALUE_IN_TABLE),
            // Relative name index
            new TestHeader(NAME_IN_TABLE, VALUE_NOT_IN_TABLE),
            // Post-base index
            new TestHeader(NAME_IN_TABLE_POSTBASE, VALUE_IN_TABLE_POSTBASE),
            // Post-base name index
            new TestHeader(NAME_IN_TABLE_POSTBASE, VALUE_NOT_IN_TABLE),
            // Literal
            new TestHeader(NAME_NOT_IN_TABLE, VALUE_NOT_IN_TABLE)
    );

    private static long REQUIRED_FIELD_SECTION_SIZE = TEST_HEADERS.stream()
            .mapToLong(TestHeader::size)
            .sum();
    private static final long BASE = 1L;
    private static final Random RANDOM = RandomFactory.getRandom();
}
