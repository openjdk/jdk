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

import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.streams.Http3Streams;
import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.http3.streams.UniStreamPair;
import jdk.internal.net.http.qpack.Decoder;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.qpack.InsertionPolicy;
import jdk.internal.net.http.qpack.QPACK.QPACKErrorHandler;
import jdk.internal.net.http.qpack.QPackException;
import jdk.internal.net.http.qpack.readers.IntegerReader;
import jdk.internal.net.http.quic.ConnectionTerminator;
import jdk.internal.net.http.quic.QuicConnection;
import jdk.internal.net.http.quic.QuicEndpoint;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;
import jdk.internal.net.quic.QuicTLSEngine;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;

/**
 * Instance of this class provides a stubbed Quic Connection implementation that
 * cross-wires encoder/decoder streams, and also provides access to
 * encoder and decoder dynamic tables.
 */
public class EncoderDecoderConnector {
    /**
     * Constructs test connector instance capable of instantiating cross-wired encoder/decoder pair.
     * It is achieved by implementing stubs for Quic connection and writer classes.
     */
    public EncoderDecoderConnector() {
        this(null, null);
    }

    /**
     * Constructs test connector instance capable of instantiating encoder/decoder pair.
     * The encoder/decoder connections are not cross-wired. The provided byte buffer consumers
     * are used by the underlying Quic connection instead.
     *
     * @param encoderBytesConsumer consumer of the encoder byte buffers
     * @param decoderBytesConsumer consumer of the decoder byte buffers
     */
    public EncoderDecoderConnector(Consumer<ByteBuffer> encoderBytesConsumer, Consumer<ByteBuffer> decoderBytesConsumer) {
        encoderReceiverFuture = new CompletableFuture<>();
        decoderReceiverFuture = new CompletableFuture<>();
        encoderConnection = new TestQuicConnection(decoderReceiverFuture, encoderBytesConsumer);
        decoderConnection = new TestQuicConnection(encoderReceiverFuture, decoderBytesConsumer);
    }

    /**
     * Create new encoder/decoder pair and establish Quic stub connection between them.
     *
     * @param encoderInsertionPolicy encoder insertion policy
     * @param encoderErrorHandler encoder stream error handler
     * @param decoderErrorHandler decoder stream error handler
     * @param streamsErrorHandler streams error handler
     * @return encoder/decoder pair
     */
    public EncoderDecoderConnector.EncoderDecoderPair
    newEncoderDecoderPair(InsertionPolicy encoderInsertionPolicy,
                          QPACKErrorHandler encoderErrorHandler,
                          QPACKErrorHandler decoderErrorHandler,
                          Consumer<Throwable> streamsErrorHandler) {
        // One instance of this class supports only one encoder/decoder pair
        if (connectionCreated) {
            throw new IllegalStateException("Encoder/decoder pair was already instantiated");
        }
        connectionCreated = true;

        // Create encoder
        var encoder = new Encoder(encoderInsertionPolicy, (receiver) ->
                createEncoderStreams(receiver, streamsErrorHandler),
                encoderErrorHandler);

        // Create decoder
        var decoder = new Decoder((receiver) ->
                createDecoderStreams(receiver, streamsErrorHandler),
                decoderErrorHandler);
        // Extract encoder and decoder dynamic tables
        DynamicTable encoderTable = dynamicTable(encoder);
        DynamicTable decoderTable = dynamicTable(decoder);
        return new EncoderDecoderConnector.EncoderDecoderPair(encoder, decoder,
                encoderTable, decoderTable, encoderStreamPair, decoderStreamPair);
    }

    /**
     * Record describing {@linkplain EncoderDecoderConnector#EncoderDecoderConnector() cross-wired}
     * OR {@link EncoderDecoderConnector#EncoderDecoderConnector(Consumer, Consumer) decoupled}
     * encoder and decoder pair.
     * The references for encoder and decoder dynamic tables also provided for testing purposes.
     *
     * @param encoder        encoder
     * @param decoder        decoder
     * @param encoderTable   encoder's dynamic table
     * @param decoderTable   decoder's dynamic table
     * @param encoderStreams encoder streams
     */
    record EncoderDecoderPair(Encoder encoder, Decoder decoder,
                              DynamicTable encoderTable,
                              DynamicTable decoderTable,
                              QueuingStreamPair encoderStreams,
                              QueuingStreamPair decoderStreams) {
    }


    private CompletableFuture<Consumer<ByteBuffer>> decoderReceiverFuture;
    private CompletableFuture<Consumer<ByteBuffer>> encoderReceiverFuture;
    private final QuicConnection encoderConnection;
    private final QuicConnection decoderConnection;
    private volatile QueuingStreamPair encoderStreamPair;
    private volatile QueuingStreamPair decoderStreamPair;

    private volatile boolean connectionCreated;

    private static DynamicTable dynamicTable(Encoder encoder) {
        return (DynamicTable) ENCODER_DT_VH.get(encoder);
    }

    private static DynamicTable dynamicTable(Decoder decoder) {
        return (DynamicTable) DECODER_DT_VH.get(decoder);
    }

    private static final MethodHandles.Lookup ENCODER_LOOKUP =
            initializeLookup(Encoder.class);
    private static final MethodHandles.Lookup DECODER_LOOKUP =
            initializeLookup(Decoder.class);
    private static final VarHandle ENCODER_DT_VH = findDynamicTableVH(
            ENCODER_LOOKUP, Encoder.class);
    private static final VarHandle DECODER_DT_VH = findDynamicTableVH(
            DECODER_LOOKUP, Decoder.class);


    private static MethodHandles.Lookup initializeLookup(Class<?> clz) {
        try {
            return MethodHandles.privateLookupIn(clz, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            Assertions.fail("Failed to initialize private Lookup instance", e);
            return null;
        }
    }

    private static VarHandle findDynamicTableVH(
            final MethodHandles.Lookup lookup, Class<?> recv) {
        try {
            return lookup.findVarHandle(recv, "dynamicTable", DynamicTable.class);
        } catch (Exception e) {
            Assertions.fail("Failed to acquire dynamic table VarHandle instance", e);
            return null;
        }
    }


    QueuingStreamPair createEncoderStreams(Consumer<ByteBuffer> receiver,
                                           Consumer<Throwable> errorHandler) {
        QueuingStreamPair streamPair = new QueuingStreamPair(
                Http3Streams.StreamType.QPACK_ENCODER,
                encoderConnection,
                receiver,
                TestErrorHandler.of(errorHandler),
                Utils.getDebugLogger(() -> "test-encoder"));
        encoderReceiverFuture.complete(receiver);
        encoderStreamPair = streamPair;
        return streamPair;
    }

    QueuingStreamPair createDecoderStreams(Consumer<ByteBuffer> receiver,
                                           Consumer<Throwable> errorHandler) {
        QueuingStreamPair streamPair = new QueuingStreamPair(
                Http3Streams.StreamType.QPACK_DECODER,
                decoderConnection,
                receiver,
                TestErrorHandler.of(errorHandler),
                Utils.getDebugLogger(() -> "test-decoder"));
        decoderReceiverFuture.complete(receiver);
        decoderStreamPair = streamPair;
        return streamPair;
    }

    private class TestQuicConnection extends QuicConnection {

        public TestQuicConnection(CompletableFuture<Consumer<ByteBuffer>> receiverFuture,
                                  Consumer<ByteBuffer> bytesWriter) {
            this.sender = new TestQuicSenderStream(receiverFuture, bytesWriter);
        }

        final TestQuicSenderStream sender;

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public TerminationCause terminationCause() {
            return null;
        }

        @Override
        public QuicTLSEngine getTLSEngine() {
            return null;
        }

        @Override
        public InetSocketAddress peerAddress() {
            return null;
        }

        @Override
        public SocketAddress localAddress() {
            return null;
        }

        @Override
        public CompletableFuture<QuicEndpoint> startHandshake() {
            return null;
        }

        @Override
        public CompletableFuture<QuicBidiStream> openNewLocalBidiStream(
                Duration duration) {
            return null;
        }

        @Override
        public CompletableFuture<QuicSenderStream> openNewLocalUniStream(
                Duration duration) {
            // This method is called to create two unidirectional streams:
            //  one for decoder, one for encoder
            return MinimalFuture.completedFuture(sender);
        }

        @Override
        public void addRemoteStreamListener(
                Predicate<? super QuicReceiverStream> streamConsumer) {
        }

        @Override
        public boolean removeRemoteStreamListener(
                Predicate<? super QuicReceiverStream> streamConsumer) {
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
        public String dbgTag() {
            return null;
        }

        @Override
        public String logTag() {
            return null;
        }
    }


    private class TestQuicStreamWriter extends QuicStreamWriter {
        final TestQuicSenderStream sender;
        volatile boolean gotStreamType;
        volatile Http3Streams.StreamType associatedStreamType;

        final CompletableFuture<Consumer<ByteBuffer>> receiverFuture;
        final Consumer<ByteBuffer> bytesWriter;

        TestQuicStreamWriter(SequentialScheduler scheduler, TestQuicSenderStream sender,
                             CompletableFuture<Consumer<ByteBuffer>> receiverFuture,
                             Consumer<ByteBuffer> bytesWriter) {
            super(scheduler);
            this.sender = sender;
            this.gotStreamType = false;
            this.receiverFuture = receiverFuture;
            this.bytesWriter = bytesWriter;
        }

        private void write(ByteBuffer bb) {
            if (bytesWriter == null) {
                if (!gotStreamType) {
                    IntegerReader integerReader = new IntegerReader();
                    integerReader.configure(8);
                    try {
                        integerReader.read(bb);
                    } catch (QPackException e) {
                        System.err.println("Can't read stream type byte");
                    }
                    Http3Streams.StreamType type = Http3Streams.StreamType.ofCode((int) integerReader.get()).get();
                    System.err.println("Stream opened with type=" + type);
                    gotStreamType = true;
                    associatedStreamType = type;
                } else {
                    if (receiverFuture.isDone() && !receiverFuture.isCompletedExceptionally()) {
                        Consumer<ByteBuffer> receiver = receiverFuture.getNow(null);
                        if (receiver != null) {
                            receiver.accept(bb);
                        }
                    }
                }
            } else {
                bytesWriter.accept(bb);
            }
        }

        @Override
        public QuicSenderStream.SendingStreamState sendingState() {
            return null;
        }

        @Override
        public void scheduleForWriting(ByteBuffer buffer, boolean last)
                throws IOException {
            write(buffer);
        }

        @Override
        public void queueForWriting(ByteBuffer buffer) throws IOException {
            write(buffer);
        }

        @Override
        public long credit() {
            return Long.MAX_VALUE;
        }

        @Override
        public void reset(long errorCode) {
        }

        @Override
        public QuicSenderStream stream() {
            return connected() ? sender : null;
        }

        @Override
        public boolean connected() {
            return sender.writer == this;
        }
    }

    class TestQuicSenderStream implements QuicSenderStream {
        private static AtomicLong ids = new AtomicLong();
        private final long id;
        TestQuicStreamWriter writer;
        Consumer<ByteBuffer> bytesWriter;
        final CompletableFuture<Consumer<ByteBuffer>> receiverFuture;

        TestQuicSenderStream(CompletableFuture<Consumer<ByteBuffer>> receiverFuture,
                             Consumer<ByteBuffer> bytesWriter) {
            id = ids.getAndIncrement() * 4 + type();
            this.receiverFuture = receiverFuture;
            this.bytesWriter = bytesWriter;
        }

        @Override
        public SendingStreamState sendingState() {
            return SendingStreamState.READY;
        }

        @Override
        public QuicStreamWriter connectWriter(SequentialScheduler scheduler) {
            return writer == null ? writer = new TestQuicStreamWriter(
                    scheduler, this, receiverFuture, bytesWriter) : writer;
        }

        @Override
        public void disconnectWriter(QuicStreamWriter writer) {
        }

        @Override
        public void reset(long errorCode) {
        }

        @Override
        public long dataSent() {
            return 0;
        }

        @Override
        public long streamId() {
            return id;
        }

        @Override
        public StreamMode mode() {
            return null;
        }

        @Override
        public boolean isClientInitiated() {
            return true;
        }

        @Override
        public boolean isServerInitiated() {
            return false;
        }

        @Override
        public boolean isBidirectional() {
            return false;
        }

        @Override
        public boolean isLocalInitiated() {
            return true;
        }

        @Override
        public boolean isRemoteInitiated() {
            return false;
        }

        @Override
        public int type() {
            return 0x02;
        }

        @Override
        public StreamState state() {
            return SendingStreamState.READY;
        }

        @Override
        public long sndErrorCode() {
            return -1;
        }

        @Override
        public boolean stopSendingReceived() {
            return false;
        }
    }

    private static class TestErrorHandler
            implements UniStreamPair.StreamErrorHandler {
        final Consumer<Throwable> handler;

        private TestErrorHandler(Consumer<Throwable> handler) {
            this.handler = handler;
        }

        @Override
        public void onError(QuicStream stream, UniStreamPair uniStreamPair,
                            Throwable throwable) {
            handler.accept(throwable);
        }

        public static TestErrorHandler of(Consumer<Throwable> handler) {
            return new TestErrorHandler(handler);
        }
    }
}
