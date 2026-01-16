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
import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.hpack.QuickHuffman;
import jdk.internal.net.http.qpack.Decoder;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.qpack.writers.IntegerWriter;
import jdk.internal.net.http.qpack.StaticTable;
import jdk.internal.net.http.qpack.writers.StringWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @modules java.net.http/jdk.internal.net.http.hpack
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 * @run junit/othervm DecoderTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DecoderTest {

    private final Random random = new Random();
    private final IntegerWriter intWriter = new IntegerWriter();
    private final StringWriter stringWriter = new StringWriter();

    record DecoderWithReader(Decoder decoder, HeaderFrameReader reader) {
    }

    private static DecoderWithReader newDecoderWithReader(DecodingCallback decodingCallback) throws IOException {
        var decoder = new Decoder(DecoderTest::createDecoderStreams, DecoderTest::qpackErrorHandler);
        var headerFrameReader = decoder.newHeaderFrameReader(decodingCallback);
        // Supply byte buffer with two bytes of zeroed Field Section Prefix
        ByteBuffer prefix = ByteBuffer.allocate(2);
        decoder.decodeHeader(prefix, true, headerFrameReader);
        // Return record with decoder/reader tuple
        return new DecoderWithReader(decoder, headerFrameReader);
    }

    private static final int TEST_STR_MAX_LENGTH = 10;

    static QueuingStreamPair createDecoderStreams(Consumer<ByteBuffer> receiver) {
        return null;
    }

    public Object[][] indexProvider() {
        AtomicInteger tableIndex = new AtomicInteger();
        return StaticTable.HTTP3_HEADER_FIELDS.stream()
                .map(headerField -> List.of(tableIndex.getAndIncrement(), headerField))
                .map(List::toArray)
                .toArray(Object[][]::new);
    }

    public Object[][] nameReferenceProvider() {
        AtomicInteger tableIndex = new AtomicInteger();
        return StaticTable.HTTP3_HEADER_FIELDS.stream()
                .map(h -> List.of(tableIndex.getAndIncrement(), h.name(), randomString()))
                .map(List::toArray).toArray(Object[][]::new);
    }

    public Object[][] literalProvider() {
        var output = new String[100][];
        for (int i = 0; i < 100; i++) {
            output[i] = new String[]{ randomString(), randomString() };
        }
        return output;
    }

    @ParameterizedTest
    @MethodSource("indexProvider")
    public void testIndexedOnStaticTable(int index, HeaderField h) throws IOException {
        var actual = writeIndex(index);
        var callback = new TestingCallBack(index, h.name(), h.value());
        var dr = newDecoderWithReader(callback);
        dr.decoder().decodeHeader(actual, true, dr.reader());
    }

    @ParameterizedTest
    @MethodSource("nameReferenceProvider")
    public void testLiteralWithNameReferenceOnStaticTable(int index, String name, String value) throws IOException {
        boolean sensitive = random.nextBoolean();

        var actual = writeNameRef(index, sensitive, value);
        var callback = new TestingCallBack(index, sensitive, name, value);
        var dr = newDecoderWithReader(callback);
        dr.decoder().decodeHeader(actual, true, dr.reader());
    }

    @ParameterizedTest
    @MethodSource("literalProvider")
    public void testLiteralWithLiteralNameOnStaticTable(String name, String value) throws IOException {
        boolean sensitive = random.nextBoolean();

        var actual = writeLiteral(sensitive, name, value);
        var callback = new TestingCallBack(sensitive, name, value);
        var dr = newDecoderWithReader(callback);
        dr.decoder().decodeHeader(actual, true, dr.reader());
    }

    @Test
    public void stateCheckSingle() throws IOException {
        boolean sensitive = random.nextBoolean();
        var name = "foo";
        var value  = "bar";

        var bb = writeLiteral(sensitive, name, value);
        var callback = new TestingCallBack(sensitive, name, value);

        var dr = newDecoderWithReader(callback);
        int len = bb.capacity();
        for (int i = 0; i < len; i++) {
            var b = ByteBuffer.wrap(new byte[]{ bb.get() });
            dr.decoder().decodeHeader(b, (i == len - 1), dr.reader());
        }
    }

    /* Test Methods */
    private void debug(ByteBuffer bb, String msg, boolean verbose) {
        if (verbose) {
            System.out.printf("DEBUG[%s]: pos=%d, limit=%d, remaining=%d\n",
                    msg, bb.position(), bb.limit(), bb.remaining());
        }
        System.out.printf("DEBUG[%s]: ", msg);
        for (byte b : bb.array()) {
            System.out.printf("(%s,%d) ", Integer.toBinaryString(b & 0xFF), (int)(b & 0xFF));
        }
        System.out.print("\n");
    }

    private ByteBuffer writeIndex(int index) {
        int N = 6;
        int payload = 0b1100_0000; // static table = true
        var bb = ByteBuffer.allocate(2);

        intWriter.configure(index, N, payload);
        intWriter.write(bb);
        intWriter.reset();

        bb.flip();
        return bb;
    }

    private ByteBuffer writeNameRef(int index, boolean sensitive, String value) {
        int N = 4;
        int payload = 0b0101_0000;  // static table = true
        if (sensitive)
            payload |= 0b0010_0000;
        var bb = allocateNameRefBuffer(N, index, value);
        intWriter.configure(index, N, payload);
        intWriter.write(bb);
        intWriter.reset();

        boolean huffman = QuickHuffman.isHuffmanBetterFor(value);
        int huffmanMask = 0b0000_0000;
        if (huffman)
            huffmanMask = 0b1000_0000;
        stringWriter.configure(value, 7, huffmanMask, huffman);
        stringWriter.write(bb);
        stringWriter.reset();

        bb.flip();
        return bb;
    }

    private ByteBuffer writeLiteral(boolean sensitive, String name, String value) {
        int N = 3;
        //boolean hasInSt = Sta;
        int payload = 0b0010_0000; // static table = true
        var bb = allocateLiteralBuffer(N, name, value);

        if (sensitive)
            payload |= 0b0001_0000;
        boolean huffmanName = QuickHuffman.isHuffmanBetterFor(name);
        if (huffmanName)
            payload |= 0b0000_1000;
        stringWriter.configure(name, N, payload, huffmanName);
        stringWriter.write(bb);
        stringWriter.reset();

        boolean huffmanValue = QuickHuffman.isHuffmanBetterFor(value);
        int huffmanMask = 0b0000_0000;
        if (huffmanValue)
            huffmanMask = 0b1000_0000;
        stringWriter.configure(value, 7, huffmanMask, huffmanValue);
        stringWriter.write(bb);
        stringWriter.reset();

        bb.flip();
        return bb;
    }

    private ByteBuffer allocateIndexBuffer(int index) {
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
        return ByteBuffer.allocate(size);
    }

    private ByteBuffer allocateNameRefBuffer(int N, int index, CharSequence value) {
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
        return ByteBuffer.allocate(size);
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
        return ByteBuffer.allocate(size);
    }

    private static final String LOREM = """
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
         *   String returned cannot refer to a entry in the table. Therefore, we set the upper
         *   bound below to a minimum of 1.
         */
        return LOREM.substring(lower, 1 + lower + random.nextInt(TEST_STR_MAX_LENGTH));
    }

    private static class TestingCallBack implements DecodingCallback {
        final int index;
        final boolean huffmanName, huffmanValue;
        final boolean sensitive;
        final String name, value;

        // Indexed
        TestingCallBack(int index, String name, String value) {
            this(index, false, name, value);
        }
        // Literal w/Literal Name
        TestingCallBack(boolean sensitive, String name, String value) {
            this(-1, sensitive, name, value);
        }
        // Literal w/Name Reference
        TestingCallBack(int index, boolean sensitive, String name, String value) {
            this.index = index;
            this.sensitive = sensitive;
            this.name = name;
            this.value = value;
            this.huffmanName = QuickHuffman.isHuffmanBetterFor(name);
            this.huffmanValue = QuickHuffman.isHuffmanBetterFor(value);
        }

        @Override
        public void onDecoded(CharSequence name, CharSequence value) {
            fail("should not be called");
        }

        @Override
        public void onIndexed(long index, CharSequence name, CharSequence value) {
            assertEquals(index, this.index);
            assertEquals(name.toString(), this.name);
            assertEquals(value.toString(), this.value);
        }

        @Override
        public void onLiteralWithNameReference(long index, CharSequence name,
                                               CharSequence value, boolean huffmanValue,
                                               boolean sensitive) {
            assertEquals(index, this.index);
            assertEquals(value.toString(), this.value);
            assertEquals(huffmanValue, this.huffmanValue);
            assertEquals(sensitive, this.sensitive);
        }

        @Override
        public void onLiteralWithLiteralName(CharSequence name, boolean huffmanName,
                                             CharSequence value, boolean huffmanValue,
                                             boolean sensitive) {
            assertEquals(name.toString(), this.name);
            assertEquals(huffmanName, this.huffmanName);
            assertEquals(value.toString(), this.value);
            assertEquals(huffmanValue, this.huffmanValue);
            assertEquals(sensitive, this.sensitive);
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onConnectionError(Throwable throwable, Http3Error http3Error) {
            fail(http3Error + "Decoding error:" + http3Error, throwable);
        }

        @Override
        public long streamId() {
            return 0;
        }
    }

    private static void qpackErrorHandler(Throwable error, Http3Error http3Error) {
        fail("QPACK error:" + http3Error, error);
    }
}
