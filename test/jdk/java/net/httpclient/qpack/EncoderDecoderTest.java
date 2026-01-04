/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.hpack.QuickHuffman;
import jdk.internal.net.http.qpack.StaticTable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
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
 * @run junit/othervm EncoderDecoderTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EncoderDecoderTest {
    private final Random random = new Random();

    private static final int TEST_STR_MAX_LENGTH = 10;


    private static void qpackErrorHandler(Throwable error, Http3Error http3Error) {
        fail(http3Error + "QPACK error:" + http3Error, error);
    }

    public Object[][] indexProvider() {
        AtomicLong index = new AtomicLong();
        return StaticTable.HTTP3_HEADER_FIELDS.stream()
                .map(headerField -> List.of(index.getAndIncrement(), headerField))
                .map(List::toArray)
                .toArray(Object[][]::new);
    }

    public Object[][] nameReferenceProvider() {
        AtomicLong tableIndex = new AtomicLong();
        Map<String, List<Long>> map = new HashMap<>();
        for (var headerField : StaticTable.HTTP3_HEADER_FIELDS) {
            var name = headerField.name();
            var index = tableIndex.getAndIncrement();

            if (!map.containsKey(name))
                map.put(name, new ArrayList<>());
            map.get(name).add(index);
        }
        return map.entrySet().stream()
                .map(e -> List.of(e.getKey(), randomString(), e.getValue()))
                .map(List::toArray).toArray(Object[][]::new);
    }

    public Object[][] literalProvider() {
        var output = new String[100][];
        for (int i = 0; i < 100; i++) {
            output[i] = new String[]{randomString(), randomString()};
        }
        return output;
    }

    private void assertNotFailed(AtomicReference<Throwable> errorRef) {
        var error = errorRef.get();
        if (error != null) throw new AssertionError(error);
    }

    @ParameterizedTest
    @MethodSource("indexProvider")
    public void encodeDecodeIndexedOnStaticTable(long index, HeaderField h) throws IOException {
        var actual = allocateIndexTestBuffer(index);
        List<ByteBuffer> buffers = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ByteBuffer> writerStub = new AtomicReference<>();
        EncoderDecoderConnector connector = new EncoderDecoderConnector(writerStub::set, writerStub::set);
        var conn = connector.newEncoderDecoderPair(e -> false,
                EncoderDecoderTest::qpackErrorHandler,
                EncoderDecoderTest::qpackErrorHandler,
                error::set);

        // Create encoder and decoder
        var encoder = conn.encoder();

        // Set encoder maximum dynamic table capacity
        conn.encoderTable().setMaxTableCapacity(256);
        // Set dynamic table capacity that doesn't exceed the max capacity value
        conn.encoderTable().setCapacity(256);

        var decoder = conn.decoder();

        // Create header frame reader and writer
        var callback = new TestingCallBack(index, h.name(), h.value());
        var headerFrameReader = decoder.newHeaderFrameReader(callback);
        var headerFrameWriter = encoder.newHeaderFrameWriter();

        // create encoding context
        Encoder.EncodingContext context = encoder.newEncodingContext(
                0, 0, headerFrameWriter);

        // Configures encoder for writing the header name:value pair
        encoder.header(context, h.name(), h.value(), false);

        // Write the header
        headerFrameWriter.write(actual);
        assertNotEquals(0, actual.position());
        actual.flip();
        buffers.add(actual);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode generated prefix bytes and encoded headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);
        assertNotFailed(error);
    }

    @ParameterizedTest
    @MethodSource("nameReferenceProvider")
    public void encodeDecodeLiteralWithNameRefOnStaticTable(String name, String value, List<Long> validIndices) throws IOException {
        long index = Collections.max(validIndices);
        boolean sensitive = random.nextBoolean();

        var actual = allocateNameRefBuffer(index, value);
        List<ByteBuffer> buffers = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ByteBuffer> writerStub = new AtomicReference<>();

        // Create encoder and decoder
        EncoderDecoderConnector connector = new EncoderDecoderConnector(writerStub::set, writerStub::set);
        var conn = connector.newEncoderDecoderPair(e -> false,
                EncoderDecoderTest::qpackErrorHandler,
                EncoderDecoderTest::qpackErrorHandler,
                error::set);
        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // Create header frame reader and writer
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var callback = new TestingCallBack(validIndices, name, value, sensitive);
        var headerFrameReader = decoder.newHeaderFrameReader(callback);

        // create encoding context
        Encoder.EncodingContext context = encoder.newEncodingContext(
                0, 0, headerFrameWriter);

        // Configures encoder for writing the header name:value pair
        encoder.header(context, name, value, sensitive);

        // Write the header
        headerFrameWriter.write(actual);
        assertNotEquals(0, actual.position());
        actual.flip();
        buffers.add(actual);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);
        assertNotFailed(error);
    }

    @ParameterizedTest
    @MethodSource("literalProvider")
    public void encodeDecodeLiteralWithLiteralNameOnStaticTable(String name, String value) throws IOException {
        boolean sensitive = random.nextBoolean();
        List<ByteBuffer> buffers = new ArrayList<>();
        var actual = allocateLiteralBuffer(name, value);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ByteBuffer> writerStub = new AtomicReference<>();

        // Create encoder and decoder
        EncoderDecoderConnector connector = new EncoderDecoderConnector(writerStub::set, writerStub::set);
        var conn = connector.newEncoderDecoderPair(e -> false,
                EncoderDecoderTest::qpackErrorHandler,
                EncoderDecoderTest::qpackErrorHandler,
                error::set);
        var encoder = conn.encoder();
        var decoder = conn.decoder();

        // Create header frame reader and writer
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        var callback = new TestingCallBack(name, value, sensitive);
        var headerFrameReader = decoder.newHeaderFrameReader(callback);

        // create encoding context
        Encoder.EncodingContext context = encoder.newEncodingContext(
                0, 0, headerFrameWriter);

        // Configures encoder for writing the header name:value conn
        encoder.header(context, name, value, sensitive);
        // Write the header
        headerFrameWriter.write(actual);
        assertNotEquals(0, actual.position());
        actual.flip();
        buffers.add(actual);

        // Generate field section prefix bytes
        encoder.generateFieldLineSectionPrefix(context, buffers);

        // Decode headers
        decoder.decodeHeader(buffers.get(0), false, headerFrameReader);
        decoder.decodeHeader(buffers.get(1), true, headerFrameReader);
        assertNotFailed(error);
    }

    /* Test Methods */
    private void debug(ByteBuffer bb, String msg, boolean verbose) {
        if (verbose)
            System.out.printf("DEBUG[%s]: pos=%d, limit=%d, remaining=%d%n",
                    msg, bb.position(), bb.limit(), bb.remaining());
        System.out.printf("DEBUG[%s]: ", msg);
        for (byte b : bb.array()) {
            System.out.printf("(%s,%d) ", Integer.toBinaryString(b & 0xFF), b & 0xFF);
        }
        System.out.println();
    }

    private ByteBuffer allocateIndexTestBuffer(long index) {
        /*
         * Note on Integer Representation used for storing the length of name and value strings.
         * Taken from RFC 7541 Section 5.1
         *
         * "An integer is represented in two parts: a prefix that fills the current octet and an
         * optional list of octets that are used if the integer value does not fit within the
         * prefix. The number of bits of the prefix (called N) is a parameter of the integer
         * representation. If the integer value is small enough, i.e., strictly less than 2N-1, it
         * is encoded within the N-bit prefix.
         *
         * ...
         *
         * Otherwise, all the bits of the prefix are set to 1, and the value, decreased by 2N-1, is
         * encoded using a list of one or more octets. The most significant bit of each octet is
         * used as a continuation flag: its value is set to 1 except for the last octet in the list.
         * The remaining bits of the octets are used to encode the decreased value."
         *
         * Use "null" for name, if name isn't being provided (i.e. for a nameRef); otherwise, buffer
         * will be too large.
         *
         */
        int N = 6; // bits available in first byte
        int size = 1;
        index -= Math.pow(2, N) - 1; // number that you can store in first N bits
        while (index >= 0) {
            index -= 127;
            size++;
        }
        return ByteBuffer.allocate(size + 2);
    }

    private ByteBuffer allocateNameRefBuffer(long index, CharSequence value) {
        int N = 4;
        return allocateNameRefBuffer(N, index, value);
    }

    private ByteBuffer allocateNameRefBuffer(int N, long index, CharSequence value) {
        int vlen = Math.min(QuickHuffman.lengthOf(value), value.length());
        int size = 1 + vlen;

        index -= Math.pow(2, N) - 1;
        while (index >= 0) {
            index -= 127;
            size++;
        }
        vlen -= 127;
        size++;
        while (vlen >= 0) {
            vlen -= 127;
            size++;
        }
        return ByteBuffer.allocate(size + 2);
    }

    private ByteBuffer allocateLiteralBuffer(CharSequence name, CharSequence value) {
        int N = 3;
        return allocateLiteralBuffer(N, name, value);
    }

    private ByteBuffer allocateLiteralBuffer(int N, CharSequence name, CharSequence value) {
        int nlen = Math.min(QuickHuffman.lengthOf(name), name.length());
        int vlen = Math.min(QuickHuffman.lengthOf(value), value.length());
        int size = nlen + vlen;

        nlen -= Math.pow(2, N) - 1;
        size++;
        while (nlen >= 0) {
            nlen -= 127;
            size++;
        }

        vlen -= 127;
        size++;
        while (vlen >= 0) {
            vlen -= 127;
            size++;
        }
        return ByteBuffer.allocate(size + 2);
    }

    static final String LOREM = """
            Lorem ipsum dolor sit amet, consectetur adipiscing
            elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
            Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris
            nisi ut aliquip ex ea commodo consequat.Duis aute irure dolor in
            reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla
            pariatur.Excepteur sint occaecat cupidatat non proident, sunt in
            culpa qui officia deserunt mollit anim id est laborum.""";

    private String randomString() {
        int lower = random.nextInt(LOREM.length() - TEST_STR_MAX_LENGTH);
        /**
         *   The empty string ("") is a valid value String in the static table and the random
         *   String returned cannot refer to an entry in the table. Therefore, we set the upper
         *   bound below to a minimum of 1.
         */
        return LOREM.substring(lower, 1 + lower + random.nextInt(TEST_STR_MAX_LENGTH));
    }

    private static class TestingCallBack implements DecodingCallback {
        final long index;
        final boolean huffmanName, huffmanValue;
        final boolean sensitive;
        final String name, value;
        final List<Long> validIndices;

        // Indexed
        TestingCallBack(long index, String name, String value) {
            this(index, null, name, value, false);
        }
        // Literal w/Literal Name
        TestingCallBack(String name, String value, boolean sensitive) {
            this(-1L, null, name, value, sensitive);
        }
        // Literal w/Name Reference
        TestingCallBack(List<Long> validIndices, String name, String value, boolean sensitive) {
            this(-1L, validIndices, name, value, sensitive);
        }
        TestingCallBack(long index, List<Long> validIndices, String name, String value, boolean sensitive) {
            this.index = index;
            this.validIndices = validIndices;
            this.huffmanName = QuickHuffman.isHuffmanBetterFor(name);
            this.huffmanValue = QuickHuffman.isHuffmanBetterFor(value);
            this.sensitive = sensitive;
            this.name = name;
            this.value = value;
        }

        @Override
        public void onDecoded(CharSequence actualName, CharSequence value) {
            fail("onDecoded should not be called");
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onConnectionError(Throwable throwable, Http3Error http3Error) {
            fail("Decoding error: " + http3Error, throwable);
        }

        @Override
        public long streamId() {
            return 0;
        }

        @Override
        public void onIndexed(long actualIndex, CharSequence actualName, CharSequence actualValue) {
            assertEquals(index, actualIndex);
            assertEquals(name, actualName);
            assertEquals(value, actualValue);
        }

        @Override
        public void onLiteralWithNameReference(long actualIndex, CharSequence actualName,
                                               CharSequence actualValue, boolean huffmanValue,
                                               boolean actualHideIntermediary) {
            assertTrue(validIndices.contains(actualIndex));
            assertEquals(name, actualName.toString());
            assertEquals(value, actualValue.toString());
            assertEquals(huffmanValue, huffmanValue);
            assertEquals(sensitive, actualHideIntermediary);
        }

        @Override
        public void onLiteralWithLiteralName(CharSequence actualName, boolean actualHuffmanName,
                                             CharSequence actualValue, boolean actualHuffmanValue,
                                             boolean actualHideIntermediary) {
            assertEquals(name, actualName.toString());
            assertEquals(huffmanName, actualHuffmanName);
            assertEquals(value, actualValue.toString());
            assertEquals(huffmanValue, actualHuffmanValue);
            assertEquals(sensitive, actualHideIntermediary);
        }
    }
}
