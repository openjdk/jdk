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
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.hpack.QuickHuffman;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.http3.streams.Http3Streams.StreamType;
import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.http3.streams.UniStreamPair;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.writers.IntegerWriter;
import jdk.internal.net.http.qpack.writers.StringWriter;
import jdk.internal.net.http.qpack.StaticTable;
import jdk.internal.net.http.quic.ConnectionTerminator;
import jdk.internal.net.http.quic.QuicConnection;
import jdk.internal.net.http.quic.QuicEndpoint;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream.SendingStreamState;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;
import jdk.internal.net.quic.QuicTLSEngine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @modules java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 * @run junit/othervm EncoderTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EncoderTest {
    private final Random random = new Random();
    private final IntegerWriter intWriter = new IntegerWriter();
    private final StringWriter stringWriter = new StringWriter();
    private static final int TEST_STR_MAX_LENGTH = 10;

    public Object[][] indexProvider() {
        AtomicInteger tableIndex = new AtomicInteger();
        return StaticTable.HTTP3_HEADER_FIELDS.stream()
                .map(headerField -> List.of(tableIndex.getAndIncrement(), headerField))
                .map(List::toArray)
                .toArray(Object[][]::new);
    }

    public Object[][] staticNameReferenceProvider() {
        AtomicInteger tableIndex = new AtomicInteger();
        Map<String, List<Integer>> map = new HashMap<>();
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

    public Object[][] literalsProvider() {
        var output = new String[100][];
        for (int i = 0; i < 100; i++) {
            output[i] = new String[]{ randomString(), randomString() };
        }
        return output;
    }

    private static class TestErrorHandler implements
            UniStreamPair.StreamErrorHandler {
        final Consumer<Throwable> handler;
        private TestErrorHandler(Consumer<Throwable> handler) {
            this.handler = handler;
        }
        @Override
        public void onError(QuicStream stream, UniStreamPair uniStreamPair, Throwable throwable) {
            handler.accept(throwable);
        }
        public static TestErrorHandler of(Consumer<Throwable> handler) {
            return new TestErrorHandler(handler);
        }
    }

    QueuingStreamPair createEncoderStreams(Consumer<ByteBuffer> receiver,
                                           Consumer<Throwable> errorHandler,
                                           TestQuicConnection quicConnection) {
        return new QueuingStreamPair(StreamType.QPACK_ENCODER,
                quicConnection,
                receiver,
                TestErrorHandler.of(errorHandler),
                Utils.getDebugLogger(() -> "quic-encoder-test")
                );
    }

    private void assertNotFailed(AtomicReference<Throwable> errorRef) {
        var error = errorRef.get();
        if (error != null) throw new AssertionError(error);
    }

    private static void qpackErrorHandler(Throwable error, Http3Error http3Error) {
        fail(http3Error + "QPACK error:" + http3Error, error);
    }

    @ParameterizedTest
    @MethodSource("indexProvider")
    public void testFieldLineWriterWithStaticIndex(int index, HeaderField h) {
        var actual = allocateIndexBuffer(index);
        var expected = writeIndex(index);
        var quicConnection = new TestQuicConnection();
        AtomicReference<Throwable> error = new AtomicReference<>();
        var encoder = new Encoder(insert -> false,
                (receiver) -> createEncoderStreams(receiver, error::set, quicConnection),
                EncoderTest::qpackErrorHandler);
        var headerFrameWriter = encoder.newHeaderFrameWriter();
        // create encoding context
        Encoder.EncodingContext context =
                encoder.newEncodingContext(0, 0, headerFrameWriter);

        encoder.header(context, h.name(), h.value(), false);
        headerFrameWriter.write(actual);
        assertNotEquals(0, actual.position());
        actual.flip();

        assertEquals(expected, actual, debug(h.name(), h.value(), actual, expected));
        assertNotFailed(error);
    }

    @ParameterizedTest
    @MethodSource("staticNameReferenceProvider")
    public void testInsertWithStaticTableNameReference(String name, String value, List<Integer> validIndices) {
        int index = Collections.max(validIndices);

        var actual = allocateInsertNameRefBuffer(index, value);
        var expected = writeInsertNameRef(value, validIndices);
        var quicConnection = new TestQuicConnection();
        AtomicReference<Throwable> error = new AtomicReference<>();
        var encoder = new Encoder(insert -> true,
                (receiver) -> createEncoderStreams(receiver, error::set, quicConnection),
                EncoderTest::qpackErrorHandler);
        configureDynamicTableSize(encoder);

        var headerFrameWriter = encoder.newHeaderFrameWriter();
        // create encoding context
        Encoder.EncodingContext context =
                encoder.newEncodingContext(0, 0, headerFrameWriter);
        encoder.header(context, name, value, false);
        headerFrameWriter.write(actual);
        assertNotEquals(0, actual.position());
        actual.flip();

        TestQuicStreamWriter quicStreamWriter = quicConnection.sender.writer;

        assertTrue(expected.contains(quicStreamWriter.get()), debug(name, value, quicStreamWriter.get(), expected));
        assertNotFailed(error);
    }

    @ParameterizedTest
    @MethodSource("staticNameReferenceProvider")
    public void testFieldLineWithStaticTableNameReference(String name, String value, List<Integer> validIndices) {
        int index = Collections.max(validIndices);
        boolean sensitive = random.nextBoolean();

        var actual = allocateNameRefBuffer(index, value);
        var expected = writeNameRef(sensitive, value, validIndices);
        var quicConnection = new TestQuicConnection();
        AtomicReference<Throwable> error = new AtomicReference<>();
        var encoder = new Encoder(insert -> false, (receiver) ->
                createEncoderStreams(receiver, error::set, quicConnection),
                EncoderTest::qpackErrorHandler);

        var headerFrameWriter = encoder.newHeaderFrameWriter();
        // create encoding context
        Encoder.EncodingContext context =
                encoder.newEncodingContext(0, 0, headerFrameWriter);
        encoder.header(context, name, value, sensitive);
        headerFrameWriter.write(actual);
        assertNotEquals(0, actual.position());
        actual.flip();

        assertTrue(expected.contains(actual), debug(name, value, actual, expected));
        assertNotFailed(error);
    }

    @ParameterizedTest
    @MethodSource("literalsProvider")
    public void testInsertWithLiterals(String name, String value) {
        var expected = writeInsertLiteral(name, value);
        var actual = allocateInsertLiteralBuffer(name, value);
        var quicConnection = new TestQuicConnection();
        AtomicReference<Throwable> error = new AtomicReference<>();
        var encoder = new Encoder(insert -> true, (receiver) ->
                createEncoderStreams(receiver, error::set, quicConnection),
                EncoderTest::qpackErrorHandler);
        configureDynamicTableSize(encoder);

        var headerFrameWriter = encoder.newHeaderFrameWriter();
        // create encoding context
        Encoder.EncodingContext context =
                encoder.newEncodingContext(0, 0, headerFrameWriter);
        encoder.header(context, name, value, false);
        headerFrameWriter.write(actual);
        assertNotEquals(0, actual.position());
        actual.flip();
        TestQuicStreamWriter quicStreamWriter = quicConnection.sender.writer;
        assertEquals(expected, quicStreamWriter.get(), debug(name, value, quicStreamWriter.get(), expected));
        assertNotFailed(error);
    }

    @ParameterizedTest
    @MethodSource("literalsProvider")
    public void testFieldLineEncodingWithLiterals(String name, String value) {
        boolean sensitive = random.nextBoolean();

        var expected = writeLiteral(sensitive, name, value);
        var actual = allocateLiteralBuffer(name, value);
        var quicConnection = new TestQuicConnection();
        AtomicReference<Throwable> error = new AtomicReference<>();
        var encoder = new Encoder(insert -> false, (receiver) ->
                createEncoderStreams(receiver, error::set, quicConnection),
                EncoderTest::qpackErrorHandler);

        var headerFrameWriter = encoder.newHeaderFrameWriter();
        // create encoding context
        Encoder.EncodingContext context =
                encoder.newEncodingContext(0, 0, headerFrameWriter);
        encoder.header(context, name, value, sensitive);
        headerFrameWriter.write(actual);
        assertNotEquals(0, actual.position());
        actual.flip();

        assertEquals(expected, actual, debug(name, value, actual, expected));
        assertNotFailed(error);
    }

    // Test cases which test insertion of entries to the dynamic need to have
    //  dynamic table with non-zero capacity
    private static void configureDynamicTableSize(Encoder encoder) {
        // Set encoder maximum dynamic table capacity
        SettingsFrame settingsFrame = SettingsFrame.defaultRFCSettings();
        settingsFrame.setParameter(SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY, 256);
        ConnectionSettings settings = ConnectionSettings.createFrom(settingsFrame);
        encoder.configure(settings);
        // Set dynamic table capacity that doesn't exceed the max capacity value
        encoder.setTableCapacity(256);
    }

    /* Test Methods */
    private class TestQuicStreamWriter extends QuicStreamWriter {
        volatile ByteBuffer b = null;
        final TestQuicSenderStream sender;

        TestQuicStreamWriter(SequentialScheduler scheduler, TestQuicSenderStream sender) {
            super(scheduler);
            this.sender = sender;
        }

        private void write(ByteBuffer bb) {
            b = bb;
        }
        public ByteBuffer get() {
            if (b == null) {
                fail("TestQuicStreamWriter buffer is null");
            }
            return b;
        }
        @Override
        public SendingStreamState sendingState() { return null;}
        @Override
        public void scheduleForWriting(ByteBuffer buffer, boolean last) throws IOException {
            write(buffer);
        }
        @Override
        public void queueForWriting(ByteBuffer buffer) throws IOException {
            write(buffer);
        }
        @Override
        public long credit() { return Long.MAX_VALUE;}
        @Override
        public void reset(long errorCode) {}
        @Override
        public QuicSenderStream stream() {
            return connected() ? sender : null;
        }
        @Override
        public boolean connected() {
            return sender.writer == this;
        }
    }

    private class TestQuicConnection extends QuicConnection {
        final TestQuicSenderStream sender = new TestQuicSenderStream();
        @Override
        public boolean isOpen() {return true;}
        @Override
        public TerminationCause terminationCause() {return null;}
        @Override
        public QuicTLSEngine getTLSEngine() {return null;}
        @Override
        public InetSocketAddress peerAddress() {return null;}
        @Override
        public SocketAddress localAddress() {return null;}
        @Override
        public CompletableFuture<QuicEndpoint> startHandshake() {return null;}
        @Override
        public CompletableFuture<QuicBidiStream> openNewLocalBidiStream(Duration duration) {
            return null;
        }
        @Override
        public CompletableFuture<QuicSenderStream> openNewLocalUniStream(Duration duration) {
            return MinimalFuture.completedFuture(sender);
        }
        @Override
        public void addRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
        }
        @Override
        public boolean removeRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
            return false;
        }
        @Override
        public Stream<? extends QuicStream> quicStreams() {
            return null;
        }
        @Override
        public CompletableFuture<Void> handshakeReachedPeer() {
            return MinimalFuture.completedFuture(null);
        }
        @Override
        public CompletableFuture<Long> requestSendPing() {
            return MinimalFuture.completedFuture(-1L);
        }

        @Override
        public ConnectionTerminator connectionTerminator() {
            return null;
        }

        @Override
        public String dbgTag() { return null; }

        @Override
        public String logTag() {
            return null;
        }
    }

    class TestQuicSenderStream implements QuicSenderStream {
        private static AtomicLong ids = new AtomicLong();
        private final long id;
        TestQuicStreamWriter writer;
        TestQuicSenderStream() {
            id = ids.getAndIncrement() * 4 + type();
        }
        @Override
        public SendingStreamState sendingState() { return SendingStreamState.READY; }
        @Override
        public QuicStreamWriter connectWriter(SequentialScheduler scheduler) {
            return writer = new TestQuicStreamWriter(scheduler, this);
        }
        @Override
        public void disconnectWriter(QuicStreamWriter writer) { }
        @Override
        public void reset(long errorCode) { }
        @Override
        public long dataSent() { return 0; }
        @Override
        public long streamId() { return id; }
        @Override
        public StreamMode mode() { return null; }
        @Override
        public boolean isClientInitiated() { return true; }
        @Override
        public boolean isServerInitiated() { return false; }
        @Override
        public boolean isBidirectional() { return false; }
        @Override
        public boolean isLocalInitiated() { return true; }
        @Override
        public boolean isRemoteInitiated() { return false; }
        @Override
        public int type() { return 0x02; }
        @Override
        public StreamState state() { return SendingStreamState.READY; }

        @Override
        public long sndErrorCode() { return -1; }
        @Override
        public boolean stopSendingReceived() { return false; }
    }

    private String debug(String name, String value, ByteBuffer actual, ByteBuffer expected) {
        return debug(name, value, actual, List.of(expected));
    }

    private String debug(String name, String value, ByteBuffer actual, List<ByteBuffer> expected) {
        var output = new StringBuilder();
        output.append("\n\nBUFFER CONTENTS\n");
        output.append("----------------\n");
        output.append("DEBUG[NAME]: %s\nDEBUG[VALUE]: %s\n".formatted(name, value));
        output.append("DEBUG[ACTUAL]:   ");
        for (byte b : actual.array()) {
            output.append("(%s,%d) ".formatted(Integer.toBinaryString(b & 0xFF), (int)(b & 0xFF)));
        }
        output.append("\n");

        output.append("DEBUG[EXPECTED]: ");
        for (var bb : expected) {
            for (byte b : bb.array()) {
                output.append("(%s,%d) ".formatted(Integer.toBinaryString(b & 0xFF), (int) (b & 0xFF)));
            }
            output.append("\n");
        }
        return output.toString();
    }

    private ByteBuffer writeIndex(int index) {
        int N = 6;
        int payload = 0b1100_0000; // use static table = true;
        var bb = ByteBuffer.allocate(2);

        intWriter.configure(index, N, payload);
        intWriter.write(bb);
        intWriter.reset();

        bb.flip();
        return bb;
    }

    private List<ByteBuffer> writeNameRef(boolean sensitive, String value, List<Integer> validIndices) {
        int N = 4;
        int payload = 0b0101_0000; // static table = true
        return writeNameRef(N, payload, sensitive, value, validIndices);
    }

    private List<ByteBuffer> writeInsertNameRef(String value, List<Integer> validIndices) {
        int N = 6;
        int payload = 0b1100_0000; // static table = true
        return writeNameRef(N, payload, false, value, validIndices);
    }

    private List<ByteBuffer> writeNameRef(int N, int payload, boolean sensitive, String value, List<Integer> validIndices) {
        // Each Header name may have several valid indices associated with it.
        List<ByteBuffer> output = new ArrayList<>();
        for (int index : validIndices) {
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
            output.add(bb);
        }

        return output;
    }

    private ByteBuffer writeInsertLiteral(String name, String value) {
        int N = 5;
        int payload = 0b0100_0000;
        boolean huffmanName = QuickHuffman.isHuffmanBetterFor(name);
        if (huffmanName)
            payload |= 0b0010_0000;
        return writeLiteral(N, payload, name, value);
    }

    private ByteBuffer writeLiteral(boolean sensitive, String name, String value) {
        int N = 3;
        int payload = 0b0010_0000; // static table = true
        if (sensitive)
            payload |= 0b0001_0000;

        if (QuickHuffman.isHuffmanBetterFor(name))
            payload |= 0b0000_1000;
        return writeLiteral(N, payload, name, value);
    }

    private ByteBuffer writeLiteral(int N, int payload, String name, String value) {
        var bb = allocateLiteralBuffer(N, name, value);

        boolean huffmanName = QuickHuffman.isHuffmanBetterFor(name);
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

    private ByteBuffer allocateInsertNameRefBuffer(int index, CharSequence value) {
        int N = 6;
        return allocateNameRefBuffer(N, index, value);
    }

    private ByteBuffer allocateNameRefBuffer(int index, CharSequence value) {
        int N = 4;
        return allocateNameRefBuffer(N, index, value);
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

    private ByteBuffer allocateInsertLiteralBuffer(CharSequence name, CharSequence value) {
        int N = 5;
        return allocateLiteralBuffer(N, name, value);
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
        return ByteBuffer.allocate(size);
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
         *   String returned cannot refer to a entry in the table. Therefore, we set the upper
         *   bound below to a minimum of 1.
         */
        return LOREM.substring(lower, 1 + lower + random.nextInt(TEST_STR_MAX_LENGTH));
    }
}
