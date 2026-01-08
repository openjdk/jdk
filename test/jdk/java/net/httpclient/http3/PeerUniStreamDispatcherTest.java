/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm
 *      -Djdk.internal.httpclient.debug=out
 *      PeerUniStreamDispatcherTest
 * @summary Unit test for the PeerUniStreamDispatcher
 */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.streams.Http3Streams;
import jdk.internal.net.http.http3.streams.PeerUniStreamDispatcher;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreams;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class PeerUniStreamDispatcherTest {

    final Logger debug = Utils.getDebugLogger(() -> "PeerUniStreamDispatcherStub");

    enum DISPATCHED_STREAM {
        CONTROL, ENCODER, DECODER, PUSH, RESERVED, UNKNOWN
    }

    sealed interface DispatchedStream {
        record StandardStream(DISPATCHED_STREAM type, String description, QuicReceiverStream stream)
            implements DispatchedStream { }
        record PushStream(DISPATCHED_STREAM type, String description, QuicReceiverStream stream, long pushId)
                implements DispatchedStream { }
        record UnknownStream(DISPATCHED_STREAM type, long code, QuicReceiverStream stream)
                implements DispatchedStream { }
        record ReservedStream(DISPATCHED_STREAM type, long code, QuicReceiverStream stream)
                implements DispatchedStream { }

        static DispatchedStream of(DISPATCHED_STREAM type, String description, QuicReceiverStream stream) {
            return new StandardStream(type, description, stream);
        }
        static DispatchedStream of(DISPATCHED_STREAM type, String description, QuicReceiverStream stream, long pushId) {
            return new PushStream(type, description, stream, pushId);
        }
        static DispatchedStream reserved(DISPATCHED_STREAM type, long code, QuicReceiverStream stream) {
            return new ReservedStream(type, code, stream);
        }
        static DispatchedStream unknown(DISPATCHED_STREAM type, long code, QuicReceiverStream stream) {
            return new UnknownStream(type, code, stream);
        }
    }

    class PeerUniStreamDispatcherStub extends PeerUniStreamDispatcher {

        final List<DispatchedStream> dispatched = new CopyOnWriteArrayList<>();

        PeerUniStreamDispatcherStub(QuicReceiverStream stream) {
            super(stream);
        }

        private void dispatched(DISPATCHED_STREAM type, String description, QuicReceiverStream stream) {
            dispatched.add(DispatchedStream.of(type, description, stream));
        }

        private void dispatched(DISPATCHED_STREAM type, String description, QuicReceiverStream stream, long pushId) {
            dispatched.add(DispatchedStream.of(type, description, stream, pushId));
        }

        private void dispatched(DISPATCHED_STREAM type, long code, QuicReceiverStream stream) {
            dispatched.add(switch (type) {
                case UNKNOWN -> DispatchedStream.unknown(type, code, stream);
                case RESERVED -> DispatchedStream.reserved(type, code, stream);
                default -> throw new IllegalArgumentException(String.valueOf(type));
            });
        }

        @Override
        protected Logger debug() {
            return debug;
        }

        @Override
        protected void onControlStreamCreated(String description, QuicReceiverStream stream) {
            dispatched(DISPATCHED_STREAM.CONTROL, description, stream);
        }

        @Override
        protected void onEncoderStreamCreated(String description, QuicReceiverStream stream) {
            dispatched(DISPATCHED_STREAM.ENCODER, description, stream);
        }

        @Override
        protected void onDecoderStreamCreated(String description, QuicReceiverStream stream) {
            dispatched(DISPATCHED_STREAM.DECODER, description, stream);
        }

        @Override
        protected void onPushStreamCreated(String description, QuicReceiverStream stream, long pushId) {
            dispatched(DISPATCHED_STREAM.PUSH, description, stream, pushId);
        }

        @Override
        protected void onReservedStreamType(long code, QuicReceiverStream stream) {
            dispatched(DISPATCHED_STREAM.RESERVED, code, stream);
            super.onReservedStreamType(code, stream);
        }

        @Override
        protected void onUnknownStreamType(long code, QuicReceiverStream stream) {
            dispatched(DISPATCHED_STREAM.UNKNOWN, code, stream);
            super.onUnknownStreamType(code, stream);
        }

        @Override
        public void start() {
            super.start();
        }
    }

    static class QuicReceiverStreamStub implements QuicReceiverStream {

        class QuicStreamReaderStub extends QuicStreamReader {

            volatile boolean connected, started;
            QuicStreamReaderStub(SequentialScheduler scheduler) {
                super(scheduler);
            }

            @Override
            public ReceivingStreamState receivingState() {
                return QuicReceiverStreamStub.this.receivingState();
            }

            @Override
            public ByteBuffer poll() throws IOException {
                return buffers.poll();
            }

            @Override
            public ByteBuffer peek() throws IOException {
                return buffers.peek();
            }

            @Override
            public QuicReceiverStream stream() {
                return QuicReceiverStreamStub.this;
            }

            @Override
            public boolean connected() {
                return connected;
            }

            @Override
            public boolean started() {
                return started;
            }

            @Override
            public void start() {
                started = true;
                if (!buffers.isEmpty()) scheduler.runOrSchedule();
            }
        }

        volatile QuicStreamReaderStub reader;
        volatile SequentialScheduler scheduler;
        volatile long errorCode;
        final long streamId;
        ConcurrentLinkedQueue<ByteBuffer> buffers = new ConcurrentLinkedQueue<>();

        QuicReceiverStreamStub(long streamId) {
            this.streamId = streamId;
        }


        @Override
        public ReceivingStreamState receivingState() {
            return ReceivingStreamState.RECV;
        }

        @Override
        public QuicStreamReader connectReader(SequentialScheduler scheduler) {
            this.scheduler = scheduler;
            var reader = this.reader
                    = new QuicStreamReaderStub(scheduler);
            reader.connected = true;
            return reader;
        }

        @Override
        public void disconnectReader(QuicStreamReader reader) {
            this.scheduler = null;
            this.reader = null;
            ((QuicStreamReaderStub) reader).connected = false;
        }

        @Override
        public void requestStopSending(long errorCode) {
            this.errorCode = errorCode;
        }

        @Override
        public long dataReceived() {
            return 0;
        }

        @Override
        public long maxStreamData() {
            return 0;
        }

        @Override
        public long rcvErrorCode() {
            return errorCode;
        }

        @Override
        public long streamId() {
            return streamId;
        }

        @Override
        public StreamMode mode() {
            return StreamMode.READ_ONLY;
        }

        @Override
        public boolean isClientInitiated() {
            return QuicStreams.isClientInitiated(streamId);
        }

        @Override
        public boolean isServerInitiated() {
            return QuicStreams.isServerInitiated(streamId);
        }

        @Override
        public boolean isBidirectional() {
            return QuicStreams.isBidirectional(streamId);
        }

        @Override
        public boolean isLocalInitiated() {
            return isClientInitiated();
        }

        @Override
        public boolean isRemoteInitiated() {
            return !isClientInitiated();
        }

        @Override
        public int type() {
            return QuicStreams.streamType(streamId);
        }

        @Override
        public StreamState state() {
            return ReceivingStreamState.RECV;
        }

    }

    private void simpleStreamType(DISPATCHED_STREAM type, long code) {
        System.out.println("Testing " + type + " with " + code);
        QuicReceiverStreamStub stream = new QuicReceiverStreamStub(QuicStreams.UNI_MASK + QuicStreams.SRV_MASK);
        PeerUniStreamDispatcherStub dispatcher = new PeerUniStreamDispatcherStub(stream);
        QuicStreamReader reader = stream.reader;
        SequentialScheduler scheduler = stream.scheduler;
        assertTrue(reader.connected());
        int size = VariableLengthEncoder.getEncodedSize(code);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        assertEquals(buffer.remaining(), size);
        VariableLengthEncoder.encode(buffer, code);
        buffer.flip();
        stream.buffers.add(buffer);
        scheduler.runOrSchedule();
        dispatcher.start();
        if (type == DISPATCHED_STREAM.PUSH) {
            // we want to encode the pushId in multiple buffers, but call
            // the scheduler only once to check that the dispatcher
            // will loop correctly.
            size = VariableLengthEncoder.getEncodedSize(1L << 62 - 5);
            ByteBuffer buffer2 = ByteBuffer.allocate(size);
            assertEquals(buffer2.remaining(), size);
            VariableLengthEncoder.encode(buffer2, 1L << 62 - 5);
            buffer2.flip();
            stream.buffers.add(ByteBuffer.wrap(new byte[] {buffer2.get()}));
            scheduler.runOrSchedule(); // call runOrSchedule after supplying the first byte.
            assertTrue(reader.connected());
            assert buffer2.remaining() > 1; // should always be true
            while (buffer2.hasRemaining()) {
                stream.buffers.add(ByteBuffer.wrap(new byte[] {buffer2.get()}));
            }
        }
        scheduler.runOrSchedule();
        assertFalse(reader.connected());
        assertFalse(dispatcher.dispatched.isEmpty());
        assertTrue(stream.buffers.isEmpty());
        assertEquals(dispatcher.dispatched.size(), 1);
        var dispatched = dispatcher.dispatched.get(0);
        checkDispatched(type, code, stream, dispatched);
    }

    private void checkDispatched(DISPATCHED_STREAM type,
                                 long code,
                                 QuicReceiverStream stream,
                                 DispatchedStream dispatched) {
        var streamClass = switch (type) {
            case CONTROL, ENCODER, DECODER -> DispatchedStream.StandardStream.class;
            case PUSH -> DispatchedStream.PushStream.class;
            case RESERVED -> DispatchedStream.ReservedStream.class;
            case UNKNOWN -> DispatchedStream.UnknownStream.class;
        };
        assertEquals(dispatched.getClass(), streamClass,
                "unexpected dispatched class " + dispatched + " for " + type);
        if (dispatched instanceof DispatchedStream.StandardStream st) {
            System.out.println("Got expected stream: " + st);
            assertEquals(st.type(), type);
            assertEquals(st.stream, stream);
        } else if (dispatched instanceof  DispatchedStream.ReservedStream res) {
            System.out.println("Got expected stream: " + res);
            assertEquals(res.type(), type);
            assertEquals(res.stream, stream);
            assertEquals(res.code(), code);
            assertTrue(Http3Streams.isReserved(res.code()));
        } else if (dispatched instanceof  DispatchedStream.UnknownStream unk) {
            System.out.println("Got expected stream: " + unk);
            assertEquals(unk.type(), type);
            assertEquals(unk.stream, stream);
            assertEquals(unk.code(), code);
            assertFalse(Http3Streams.isReserved(unk.code()));
        } else if (dispatched instanceof DispatchedStream.PushStream push) {
            System.out.println("Got expected stream: " + push);
            assertEquals(push.type(), type);
            assertEquals(push.stream, stream);
            assertEquals(push.pushId, 1L << 62 - 5);
            assertEquals(push.type(), DISPATCHED_STREAM.PUSH);
        }

    }
    @Test
    public void simpleControl() {
        simpleStreamType(DISPATCHED_STREAM.CONTROL, Http3Streams.CONTROL_STREAM_CODE);
    }
    @Test
    public void simpleDecoder() {
        simpleStreamType(DISPATCHED_STREAM.DECODER, Http3Streams.QPACK_DECODER_STREAM_CODE);
    }
    @Test
    public void simpleEncoder() {
        simpleStreamType(DISPATCHED_STREAM.ENCODER, Http3Streams.QPACK_ENCODER_STREAM_CODE);
    }
    @Test
    public void simplePush() {
        simpleStreamType(DISPATCHED_STREAM.PUSH, Http3Streams.PUSH_STREAM_CODE);
    }
    @Test
    public void simpleUknown() {
        simpleStreamType(DISPATCHED_STREAM.UNKNOWN, VariableLengthEncoder.MAX_ENCODED_INTEGER);
    }
    @Test
    public void simpleReserved() {
        simpleStreamType(DISPATCHED_STREAM.RESERVED, 31 * 256 + 2);
    }

    @Test
    public void multyBytes() {
        DISPATCHED_STREAM type = DISPATCHED_STREAM.UNKNOWN;
        long code = VariableLengthEncoder.MAX_ENCODED_INTEGER;
        System.out.println("Testing multi byte " + type + " with " + code);
        QuicReceiverStreamStub stream = new QuicReceiverStreamStub(QuicStreams.UNI_MASK + QuicStreams.SRV_MASK);
        PeerUniStreamDispatcherStub dispatcher = new PeerUniStreamDispatcherStub(stream);
        QuicStreamReader reader = stream.reader;
        SequentialScheduler scheduler = stream.scheduler;
        assertTrue(reader.connected());
        int size = VariableLengthEncoder.getEncodedSize(code);
        assertEquals(size, 8);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        assertEquals(buffer.remaining(), size);
        VariableLengthEncoder.encode(buffer, code);
        buffer.flip();
        dispatcher.start();
        for (int i=0; i<size; i++) {
            System.out.printf("Submitting buffer[%s]=%s%n", i, buffer.get(buffer.position()) & 0xFF);
            ByteBuffer buf = ByteBuffer.allocate(1);
            buf.put(buffer.get());
            buf.flip();
            stream.buffers.add(buf);
            scheduler.runOrSchedule();
            if (i + 1 < size) {
                assertTrue(reader.connected());
                assertTrue(dispatcher.dispatched.isEmpty());
            }

        }
        assertFalse(reader.connected());
        assertFalse(dispatcher.dispatched.isEmpty());
        assertTrue(stream.buffers.isEmpty());
        assertEquals(dispatcher.dispatched.size(), 1);
        var dispatched = dispatcher.dispatched.get(0);
        checkDispatched(type, code, stream, dispatched);
    }

}
