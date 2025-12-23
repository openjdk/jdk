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


import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.QPackException;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.qpack.readers.StringReader;
import jdk.internal.net.http.qpack.writers.HeaderFrameWriter;
import jdk.internal.net.http.qpack.writers.IntegerWriter;
import jdk.internal.net.http.qpack.writers.StringWriter;

import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
 * @run junit/othervm -Djdk.http.qpack.allowBlockingEncoding=true
 *                      StringLengthLimitsTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StringLengthLimitsTest {

    Object[][] stringReaderLimitsData() {
        return new Object[][]{
                {STRING_READER_STRING_LENGTH, STRING_READER_STRING_LENGTH, false, false},
                {STRING_READER_STRING_LENGTH, STRING_READER_STRING_LENGTH - 1, false, true},
                {STRING_READER_STRING_LENGTH, STRING_READER_STRING_LENGTH / 4, true, false},
                {STRING_READER_STRING_LENGTH, STRING_READER_STRING_LENGTH / 4 - 1, true, true}
        };
    }

    @ParameterizedTest
    @MethodSource("stringReaderLimitsData")
    public void stringReaderLimits(int length, int limit, boolean huffmanBit,
                                   boolean exceptionExpected) throws IOException {
        IntegerWriter intWriter = new IntegerWriter();
        intWriter.configure(length, 7, huffmanBit ? STRING_READER_HUFFMAN_PAYLOAD : STRING_READER_PAYLOAD);

        var byteBuffer = ByteBuffer.allocate(2);
        if (!intWriter.write(byteBuffer)) {
            Assertions.fail("Error with test buffer preparations");
        }
        byteBuffer.flip();

        StringReader stringReader = new StringReader();
        StringBuilder unusedOutput = new StringBuilder();
        if (exceptionExpected) {
            QPackException exception = Assertions.assertThrows(QPackException.class,
                    () -> stringReader.read(byteBuffer, unusedOutput, limit));
            Throwable cause = exception.getCause();
            Assertions.assertNotNull(cause);
            Assertions.assertTrue(cause instanceof ProtocolException);
            System.err.println("Got expected ProtocolException: " + cause);
        } else {
            boolean done = stringReader.read(byteBuffer, unusedOutput, limit);
            Assertions.assertFalse(done, "read done");
        }
    }

    Object[][] encoderInstructionLimitsData() {
        int maxEntrySize = ENCODER_INSTRUCTIONS_DT_CAPACITY - 32;
        return new Object[][]{
                // "Insert with Literal Name" instruction tests
                // No Huffman, incomplete instruction, enough space in the DT
                {EncoderInstruction.INSERT_LITERAL_NAME, maxEntrySize,
                        false, DO_NOT_GENERATE_PART, false, true},
                // No Huffman, incomplete instruction, not enough space in the DT
                {EncoderInstruction.INSERT_LITERAL_NAME, maxEntrySize + 1,
                        false, DO_NOT_GENERATE_PART, false, false},
                // No Huffman, full instruction, enough space in the DT
                {EncoderInstruction.INSERT_LITERAL_NAME, maxEntrySize / 2,
                        false, maxEntrySize / 2, false, true},
                // No Huffman, full instruction, not enough space in the DT
                {EncoderInstruction.INSERT_LITERAL_NAME, maxEntrySize / 2,
                        false, 1 + maxEntrySize / 2, false, false},
                // Huffman (name + value), full instruction, enough space
                // in the DT
                {EncoderInstruction.INSERT_LITERAL_NAME, maxEntrySize / 4 / 2,
                        true, maxEntrySize / 4 / 2, true, true},
                // Huffman (value only), full instruction, not enough space
                // in the DT.
                // +16 term is added to make sure huffman estimate exceeds the limit
                {EncoderInstruction.INSERT_LITERAL_NAME, maxEntrySize / 2,
                        false, 2 * maxEntrySize + 16, true, false},

                // "Insert with Name Reference" instruction tests
                // Enough space in the DT for the value part
                {EncoderInstruction.INSERT_NAME_REFERENCE, DO_NOT_GENERATE_PART,
                        false, maxEntrySize, false, true},
                // Not enough space in the DT for the value part
                {EncoderInstruction.INSERT_NAME_REFERENCE, DO_NOT_GENERATE_PART,
                        false, maxEntrySize + 1, false, false},
                // Enough space in the DT for the Huffman encoded value part
                {EncoderInstruction.INSERT_NAME_REFERENCE, DO_NOT_GENERATE_PART,
                        false, maxEntrySize * 4, true, true},
                // Not enough space in the DT for the Huffman encoded value part
                {EncoderInstruction.INSERT_NAME_REFERENCE, DO_NOT_GENERATE_PART,
                        false, maxEntrySize * 4 + 4, true, false}
        };
    }

    @ParameterizedTest
    @MethodSource("encoderInstructionLimitsData")
    public void encoderInstructionLimits(EncoderInstruction instruction,
                                         int nameLength, boolean nameHuffman,
                                         int valueLength, boolean valueHuffman,
                                         boolean successExpected) {
        // Encoder/decoder pair with instruction that has string and with length > limit
        var connector = new EncoderDecoderConnector();
        AtomicReference<Throwable> observedError = new AtomicReference<>();
        QPACK.QPACKErrorHandler errorHandler = (throwable, error) -> {
            System.err.println("QPACK error observed: " + error);
            observedError.set(throwable);
        };
        var streamError = new AtomicReference<Throwable>();
        var pair = connector.newEncoderDecoderPair((_) -> true, errorHandler, errorHandler, streamError::set);

        // Configure dynamic tables
        var decoderDT = pair.decoderTable();
        var encoderDT = pair.encoderTable();
        encoderDT.setMaxTableCapacity(ENCODER_INSTRUCTIONS_DT_CAPACITY);
        encoderDT.setCapacity(ENCODER_INSTRUCTIONS_DT_CAPACITY);
        decoderDT.setMaxTableCapacity(ENCODER_INSTRUCTIONS_DT_CAPACITY);
        decoderDT.setCapacity(ENCODER_INSTRUCTIONS_DT_CAPACITY);

        // Generate buffers with encoder instruction bytes
        var instructionBuffers = generateInstructionBuffers(instruction,
                nameLength, nameHuffman, valueLength, valueHuffman);
        for (var buffer : instructionBuffers) {
            // Submit encoder instruction with test instructions which
            // could be incomplete
            pair.encoderStreams().submitData(buffer);
        }
        Throwable error = observedError.get();
        if (successExpected && error != null) {
            Assertions.fail("Unexpected error", error);
        } else if (error == null && !successExpected) {
            Assertions.fail("Expected error");
        }
    }

    /*
     * Generate a list of instruction buffers.
     * First buffer contains a name part (index or String),
     * Second buffer contains a value part (index or String).
     * If instruction type is INSERT_NAME_REFERENCE -
     *  the nameLength and nameHuffman are ignored.
     */
    private static List<ByteBuffer> generateInstructionBuffers(
            EncoderInstruction instruction,
            int nameLength, boolean nameHuffman,
            int valueLength, boolean valueHuffman) {
        IntegerWriter intWriter = new IntegerWriter();
        StringWriter stringWriter = new StringWriter();
        List<ByteBuffer> instructionBuffers = new ArrayList<>();
        int valuePartPayload = valueHuffman ?
                STRING_READER_HUFFMAN_PAYLOAD : STRING_READER_PAYLOAD;
        // Configure writers for an instruction
        switch (instruction) {
            case INSERT_LITERAL_NAME:
                int namePartPayload = nameHuffman ?
                        INSERT_INSTRUCTION_LITERAL_NAME_HUFFMAN_PAYLOAD :
                        INSERT_INSTRUCTION_LITERAL_NAME_PAYLOAD;
                if (valueLength != DO_NOT_GENERATE_PART) {
                    // Generate data for the name part
                    var namePartBB = ByteBuffer.allocate(nameLength + 1);
                    stringWriter.configure("T".repeat(nameLength), 5, namePartPayload, valueHuffman);
                    boolean nameDone = stringWriter.write(namePartBB);
                    assert nameDone;
                    namePartBB.flip();
                    instructionBuffers.add(namePartBB);
                    // Generate data for the value  part
                    var valuePartBB = generatePartialString(7, valuePartPayload, valueLength);
                    instructionBuffers.add(valuePartBB);
                } else {
                    // Generate data for the name part only
                    var namePartBB = generatePartialString(5, namePartPayload, nameLength);
                    instructionBuffers.add(namePartBB);
                }
                break;
            case INSERT_NAME_REFERENCE:
                var nameIndexPart = ByteBuffer.allocate(1);
                // Write some static table name id
                // Referencing static table entry with id = 16, ie ":method"
                // nameLength and nameHuffman are ignored
                intWriter.configure(16, 6, INSERT_INSTRUCTION_WITH_NAME_REFERENCE_PAYLOAD);
                boolean nameIndexDone = intWriter.write(nameIndexPart);
                assert nameIndexDone;
                nameIndexPart.flip();
                intWriter.reset();
                // Write value part with specified length and huffman encoding
                // Generate data for the value part
                var valueLengthPart =
                        generatePartialString(7, valuePartPayload, valueLength);
                // Add both parts to the list of forged instruction buffers
                instructionBuffers.add(nameIndexPart);
                instructionBuffers.add(valueLengthPart);
                break;
        }
        return instructionBuffers;
    }

    Object[][] fieldLineLimitsData() {
        return new Object[][]{
                // Post-Base Index
                {-1, -1, ENTRY_NAME, ENTRY_VALUE, true, true},
                // Relative Index
                {-1, -1, ENTRY_NAME, ENTRY_VALUE, false, true},
                // Post-Base Name Index
                {-1, -1, ENTRY_NAME, "X".repeat(ENTRY_VALUE.length()), true, true},
                // Relative Name Index
                {-1, -1, ENTRY_NAME, "X".repeat(ENTRY_VALUE.length()), false, true},
                // Post-Base Index, limit is exceeded
                {-1, -1, BIG_ENTRY_NAME, BIG_ENTRY_VALUE, true, false},
                // Relative Index, limit is exceeded
                {-1, -1, BIG_ENTRY_NAME, BIG_ENTRY_VALUE, false, false},
                // Post-Base Name Index, limit is exceeded
                {-1, -1, BIG_ENTRY_NAME, ENTRY_VALUE, true, false},
                // Relative Name Index, limit is exceeded
                {-1, -1, ENTRY_NAME, BIG_ENTRY_VALUE, false, false},
                // Name and Value are literals, limit is not exceeded
                {ENTRY_NAME.length(), ENTRY_VALUE.length(), null, null, false, true},
                // Name and Value are literals, limit is exceeded in name part
                {ENTRY_NAME.length() + ENTRY_VALUE.length() + 1, 0, null, null, false, false},
                // Name and Value are literals, limit is exceeded in value part
                {1, ENTRY_NAME.length() + ENTRY_VALUE.length(), null, null, false, false}

        };
    }

    @ParameterizedTest
    @MethodSource("fieldLineLimitsData")
    public void fieldLineLimits(int nameLength, int valueLength,
                                String name, String value,
                                boolean isPostBase, boolean successExpected) throws IOException {
        // QPACK writers for test data generations
        IntegerWriter intWriter = new IntegerWriter();
        StringWriter stringWriter = new StringWriter();

        // QPACK error handlers, stream error capture and decoding callback
        var encoderError = new AtomicReference<Throwable>();
        var decoderError = new AtomicReference<Throwable>();
        var streamError = new AtomicReference<Throwable>();
        var decodingCallbackError = new AtomicReference<Throwable>();

        QPACK.QPACKErrorHandler encoderErrorHandler = (throwable, error) -> {
            System.err.println("Encoder error observed: " + error);
            encoderError.set(throwable);
        };

        QPACK.QPACKErrorHandler decoderErrorHandler = (throwable, error) -> {
            System.err.println("Decoder error observed: " + error);
            decoderError.set(throwable);
        };

        var decodingCallback = new FieldLineDecodingCallback(decodingCallbackError);

        // Create encoder/decoder pair
        var conn = new EncoderDecoderConnector();
        var pair = conn.newEncoderDecoderPair(
                // Disallow entries insertion.
                // The dynamic table is pre-populated with needed entries
                // before the test execution
                _ -> false,
                encoderErrorHandler,
                decoderErrorHandler,
                streamError::set);
        var encoder = pair.encoder();
        var decoder = pair.decoder();

        // Set MAX_HEADER_SIZE limit on a decoder side
        // Create settings frame with MAX_FIELD_SECTION_SIZE
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        settingsFrame.setParameter(SettingsFrame.SETTINGS_MAX_FIELD_SECTION_SIZE, MAX_FIELD_SECTION_SIZE_SETTING_VALUE);
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, FIELD_LINES_DT_CAPACITY);
        pair.decoder().configure(ConnectionSettings.createFrom(settingsFrame));

        // Configure tables
        configureTablesForFieldLinesTest(pair);

        // Encode the section prefix
        long RIC = pair.encoderTable().insertCount();
        long base = isPostBase ? 0 : RIC;
        HeaderFrameWriter writer = encoder.newHeaderFrameWriter();
        HeaderFrameReader reader = decoder.newHeaderFrameReader(decodingCallback);
        var encodingContext = encoder.newEncodingContext(123, base, writer);
        List<ByteBuffer> buffers = new ArrayList<>();

        if (nameLength == -1 && valueLength == -1) {
            // Test configuration for all indexed field line wire formats
            // (indexed name and indexed entry)
            var headersBuffer = ByteBuffer.allocate(1024);
            // knownReceivedCount == InsertCount to allow DT reference encodings,
            // since there is one entry in the test dynamic table
            encoder.header(encodingContext, name, value, false, RIC);
            writer.write(headersBuffer);
            headersBuffer.flip();
            buffers.add(headersBuffer);
        } else {
            if (nameLength > MAX_FIELD_SECTION_SIZE_SETTING_VALUE - DynamicTable.ENTRY_SIZE) {
                // if nameLength > limit - only need to generate name part
                // We only write partial name part of the "Literal Field Line with Literal
                // Name" instruction with String length value
                var nameLengthBB =
                        generatePartialString(3, FIELD_LINE_NAME_VALUE_LITERALS_PAYLOAD, nameLength);
                buffers.add(nameLengthBB);
            } else if (nameLength + valueLength >
                    MAX_FIELD_SECTION_SIZE_SETTING_VALUE - DynamicTable.ENTRY_SIZE) {
                // if nameLength + valueLength > limit -
                // the whole instruction needs to be generated with basic writers
                var fieldLineBB = ByteBuffer.allocate(1024);
                stringWriter.configure("Z".repeat(nameLength), 3,
                        FIELD_LINE_NAME_VALUE_LITERALS_PAYLOAD, false);
                intWriter.configure(valueLength, 7, 0);
                stringWriter.write(fieldLineBB);
                intWriter.write(fieldLineBB);
                fieldLineBB.flip();
                buffers.add(fieldLineBB);
            } else {
                // name + value doesn't exceed MAX_FIELD_SECTION_SIZE
                var headersBuffer = ByteBuffer.allocate(1024);
                // We use 'X' and 'Z' letters to prevent encoder from
                // huffman encoding.
                encoder.header(encodingContext,
                        "X".repeat(nameLength), "Z".repeat(valueLength),
                        false, RIC);
                writer.write(headersBuffer);
                headersBuffer.flip();
                buffers.add(headersBuffer);
            }
        }
        // Generate field lines section prefix
        encoder.generateFieldLineSectionPrefix(encodingContext, buffers);
        assert buffers.size() == 2;

        // Decode generated header buffers
        decoder.decodeHeader(buffers.get(0), false, reader);
        decoder.decodeHeader(buffers.get(1), true, reader);

        // Check if any error is observed and it meets the test expectations
        var error = decodingCallbackError.get();
        System.err.println("Decoding callback error: " + error);
        if (successExpected && error != null) {
            Assertions.fail("Unexpected error", error);
        } else if (error == null && !successExpected) {
            Assertions.fail("Error expected");
        }
    }

    private static void configureTablesForFieldLinesTest(
            EncoderDecoderConnector.EncoderDecoderPair pair) {
        // Encoder
        pair.encoderTable().setMaxTableCapacity(StringLengthLimitsTest.FIELD_LINES_DT_CAPACITY);
        pair.encoderTable().setCapacity(StringLengthLimitsTest.FIELD_LINES_DT_CAPACITY);

        // Decoder max table capacity is set via the settings frame
        pair.decoderTable().setCapacity(StringLengthLimitsTest.FIELD_LINES_DT_CAPACITY);

        // Insert test entry to both tables
        pair.decoderTable().insert(ENTRY_NAME, ENTRY_VALUE);
        pair.encoderTable().insert(ENTRY_NAME, ENTRY_VALUE);
        pair.decoderTable().insert(BIG_ENTRY_NAME, BIG_ENTRY_VALUE);
        pair.encoderTable().insert(BIG_ENTRY_NAME, BIG_ENTRY_VALUE);
    }

    // Encoder instructions under test
    public enum EncoderInstruction {
        INSERT_LITERAL_NAME,
        INSERT_NAME_REFERENCE,
    }

    // Decoding callback used by Field Line Representation tests
    private static class FieldLineDecodingCallback implements DecodingCallback {
        private final AtomicReference<Throwable> decodingError;

        public FieldLineDecodingCallback(AtomicReference<Throwable> decodingCallbackError) {
            this.decodingError = decodingCallbackError;
        }

        @Override
        public void onDecoded(CharSequence name, CharSequence value) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onConnectionError(Throwable throwable, Http3Error http3Error) {
            decodingError.set(throwable);
        }

        @Override
        public long streamId() {
            return 0;
        }
    }

    // Utility method to generate partial QPack string with length part only
    private static ByteBuffer generatePartialString(int N, int payload, int length) {
        IntegerWriter intWriter = new IntegerWriter();
        var partialStringBB = ByteBuffer.allocate(
                IntegerWriter.requiredBufferSize(N, length));
        intWriter.configure(length, N, payload);
        boolean done = intWriter.write(partialStringBB);
        assert done;
        partialStringBB.flip();
        return partialStringBB;
    }

    // Constants for StringReader tests
    private static final int STRING_READER_HUFFMAN_PAYLOAD = 0b1000_0000;
    private static final int STRING_READER_PAYLOAD = 0b0000_0000;
    private static final int STRING_READER_STRING_LENGTH = 32;

    // Constants for Encoder Instructions Reader tests
    private static final int ENCODER_INSTRUCTIONS_DT_CAPACITY = 64;
    // This constant is used to instruct test data generator not to generate
    // value or name (if name is referenced by the DT index) part of the
    // decoder instruction
    private static final int DO_NOT_GENERATE_PART = -1;
    // Encoder instruction payloads used to forge instruction buffers
    private static final int INSERT_INSTRUCTION_LITERAL_NAME_HUFFMAN_PAYLOAD = 0b0110_0000;
    private static final int INSERT_INSTRUCTION_LITERAL_NAME_PAYLOAD = 0b0100_0000;
    private static final int INSERT_INSTRUCTION_WITH_NAME_REFERENCE_PAYLOAD = 0b1100_0000;
    private static final int FIELD_LINE_NAME_VALUE_LITERALS_PAYLOAD = 0b0010_0000;

    // Constants for Field Line Representation tests
    // Table capacity big enough for insertion of all entries
    private static final long FIELD_LINES_DT_CAPACITY = 1024;
    private static final String ENTRY_NAME = "FullEntryName";
    private static final String ENTRY_VALUE = "FullEntryValue";
    private static final String BIG_ENTRY_NAME = "FullEntryName_Big";
    private static final String BIG_ENTRY_VALUE = "FullEntryValue_Big";
    private static final long MAX_FIELD_SECTION_SIZE_SETTING_VALUE =
            ENTRY_NAME.length() + ENTRY_VALUE.length() + DynamicTable.ENTRY_SIZE;

}
