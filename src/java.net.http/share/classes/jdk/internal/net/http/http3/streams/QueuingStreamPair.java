/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.net.http.http3.streams;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.http3.streams.Http3Streams.StreamType;
import jdk.internal.net.http.quic.QuicConnection;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream.SendingStreamState;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;

/**
 * A class that models a pair of unidirectional streams, where
 * data to be written is simply submitted to a queue.
 */
public class QueuingStreamPair extends UniStreamPair {

    // a queue of ByteBuffers submitted for writing.
    protected final ConcurrentLinkedQueue<ByteBuffer> writerQueue;

    /**
     * Creates a new {@code QueuingStreamPair} for the given HTTP/3 {@code streamType}.
     * Valid values for {@code streamType} are {@link StreamType#CONTROL},
     * {@link StreamType#QPACK_ENCODER}, and {@link StreamType#QPACK_DECODER}.
     * <p>
     * This class implements a read loop and a write loop.
     * <p>
     * The read loop will call the given {@code receiver}
     * whenever a {@code ByteBuffer} is received.
     * <p>
     * Data can be written to the stream simply by {@linkplain
     * #submitData(ByteBuffer) submitting} it to the
     * internal unbounded queue managed by this stream.
     * When the stream becomes writable, the write loop is invoked and all
     * pending data in the queue is written to the stream, until the stream
     * is blocked or the queue is empty.
     *
     * @param streamType      the HTTP/3 stream type
     * @param quicConnection  the underlying Quic connection
     * @param receiver        the receiver callback
     * @param errorHandler    the error handler invoked in case of read errors
     * @param logger          the debug logger
     */
    public QueuingStreamPair(StreamType streamType,
                             QuicConnection quicConnection,
                             Consumer<ByteBuffer> receiver,
                             StreamErrorHandler errorHandler,
                             Logger logger) {
        // initialize writer queue before the parent constructor starts the writer loop
        writerQueue = new ConcurrentLinkedQueue<>();
        super(streamType, quicConnection, receiver, errorHandler, logger);
    }

    /**
     * {@return the available credit, taking into account data that has
     *   not been submitted yet}
     *   This is only weakly consistent.
     */
    public long credit() {
        var writer = localWriter();
        long credit = (writer == null) ? 0 : writer.credit();
        if (writerQueue.isEmpty()) return credit;
        return credit - writerQueue.stream().mapToLong(Buffer::remaining).sum();
    }

    /**
     * Submit data to be written to the sending stream via this
     * object's internal queue.
     * @param buffer the data to submit
     */
    public final void submitData(ByteBuffer buffer) {
        writerQueue.offer(buffer);
        localWriteScheduler().runOrSchedule();
    }

    // The local control stream write loop
    @Override
    void localWriterLoop() {
        var writer = localWriter();
        if (writer == null) return;
        assert !(writer instanceof QueuingWriter);
        ByteBuffer buffer;
        if (debug.on())
            debug.log("start control writing loop: credit=" + writer.credit());
        while (writer.credit() > 0 && (buffer = writerQueue.poll()) != null) {
            try {
                if (debug.on())
                    debug.log("schedule %s bytes for writing on control stream", buffer.remaining());
                writer.scheduleForWriting(buffer, buffer == QuicStreamReader.EOF);
            } catch (Throwable t) {
                if (debug.on()) {
                    debug.log("Failed to write to control stream", t);
                }
                errorHandler.onError(writer.stream(), this, t);
            }
        }
    }

    @Override
    QuicStreamWriter wrap(QuicStreamWriter writer) {
        return new QueuingWriter(writer);
    }

    /**
     * A class that wraps the actual {@code QuicStreamWriter}
     * and redirect everything to the QueuingStreamPair's
     * writerQueue - so that data is not sent out of order.
     */
    class QueuingWriter extends QuicStreamWriter {
        final QuicStreamWriter writer;
        QueuingWriter(QuicStreamWriter writer) {
            super(QueuingStreamPair.this.localWriteScheduler());
            this.writer = writer;
        }

        @Override
        public SendingStreamState sendingState() {
            return writer.sendingState();
        }

        @Override
        public void scheduleForWriting(ByteBuffer buffer, boolean last) throws IOException {
            if (!last || buffer.hasRemaining()) submitData(buffer);
            if (last) submitData(QuicStreamReader.EOF);
        }

        @Override
        public void queueForWriting(ByteBuffer buffer) throws IOException {
            QueuingStreamPair.this.writerQueue.offer(buffer);
        }

        @Override
        public long credit() {
            return QueuingStreamPair.this.credit();
        }

        @Override
        public void reset(long errorCode) throws IOException {
            writer.reset(errorCode);
        }

        @Override
        public QuicSenderStream stream() {
            return writer.stream();
        }

        @Override
        public boolean connected() {
            return writer.connected();
        }
    }

}
