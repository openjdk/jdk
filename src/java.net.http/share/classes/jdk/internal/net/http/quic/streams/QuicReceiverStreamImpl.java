/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.streams;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.OrderedFlow.StreamDataFlow;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.frames.ConnectionCloseFrame;
import jdk.internal.net.http.quic.frames.ResetStreamFrame;
import jdk.internal.net.http.quic.frames.StreamDataBlockedFrame;
import jdk.internal.net.http.quic.frames.StreamFrame;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import static jdk.internal.net.http.quic.QuicConnectionImpl.DEFAULT_INITIAL_STREAM_MAX_DATA;
import static jdk.internal.net.http.quic.frames.QuicFrame.MAX_VL_INTEGER;
import static jdk.internal.net.http.quic.streams.QuicReceiverStream.ReceivingStreamState.*;

/**
 * A class that implements the receiver part of a quic stream.
 */
public final class QuicReceiverStreamImpl extends AbstractQuicStream implements QuicReceiverStream {

    private static final int MAX_SMALL_FRAGMENTS =
            Utils.getIntegerProperty("jdk.httpclient.quic.maxSmallFragments", 100);
    private final Logger debug = Utils.getDebugLogger(this::dbgTag);
    private final String dbgTag;

    // The dataFlow reorders incoming stream frames and removes duplicates.
    // It contains frames that cannot be delivered yet because they are not
    // at the expected offset.
    private final StreamDataFlow dataFlow = new StreamDataFlow();
    // The orderedQueue contains frames that can be delivered to the application now.
    // They are inserted in the queue in order.
    // The QuicStreamReader's scheduler loop consumes this queue.
    private final ConcurrentLinkedQueue<ByteBuffer> orderedQueue = new ConcurrentLinkedQueue<>();
    // Desired buffer size; used when updating maxStreamData
    private final long desiredBufferSize;
    // Maximum stream data
    private volatile long maxStreamData;
    // how much data has been processed on this stream.
    // This is data that was poll'ed from orderedQueue or dropped after stream reset.
    private volatile long processed;
    // how much data has been delivered to orderedQueue. This doesn't take into account
    // frames that may be stored in the dataFlow.
    private volatile long received;
    // maximum of offset+length across all received frames
    private volatile long maxReceivedData;
    // the size of the stream, when known. Defaults to 0 when unknown.
    private volatile long knownSize;
    // the connected reader
    private volatile QuicStreamReaderImpl reader;
    // eof when the last payload has been polled by the application
    private volatile boolean eof;
    // the state of the receiving stream
    private volatile ReceivingStreamState receivingState;
    private volatile boolean requestedStopSending;
    private volatile long errorCode;

    private final static long MIN_BUFFER_SIZE = 16L << 10;
    QuicReceiverStreamImpl(QuicConnectionImpl connection, long streamId) {
        super(connection, validateStreamId(connection, streamId));
        errorCode = -1;
        receivingState = ReceivingStreamState.RECV;
        dbgTag = connection.streamDbgTag(streamId, "R");
        long bufsize = DEFAULT_INITIAL_STREAM_MAX_DATA;
        desiredBufferSize = Math.clamp(bufsize, MIN_BUFFER_SIZE, MAX_VL_INTEGER);
    }

    private static long validateStreamId(QuicConnectionImpl connection, long streamId) {
        if (QuicStreams.isBidirectional(streamId)) return streamId;
        if (connection.isClientConnection() == QuicStreams.isClientInitiated(streamId)) {
            throw new IllegalArgumentException("A locally initiated stream can't be read-only");
        }
        return streamId;
    }

    /**
     * Sends a {@link ConnectionCloseFrame} due to MAX_STREAM_DATA exceeded
     * for the stream.
     * @param streamFrame the stream frame that caused the excess
     * @param maxData the value of MAX_STREAM_DATA which was exceeded
     */
    private static QuicTransportException streamControlOverflow(StreamFrame streamFrame, long maxData) throws QuicTransportException {
        String reason = "Stream max data exceeded: offset=%s, length=%s, max stream data=%s"
                .formatted(streamFrame.offset(), streamFrame.dataLength(), maxData);
        throw new QuicTransportException(reason,
                QuicTLSEngine.KeySpace.ONE_RTT, streamFrame.getTypeField(), QuicTransportErrors.FLOW_CONTROL_ERROR);
    }

    // debug tag for debug logger
    String dbgTag() {
        return dbgTag;
    }

    @Override
    public StreamState state() {
        return receivingState();
    }

    @Override
    public ReceivingStreamState receivingState() {
        return receivingState;
    }

    @Override
    public QuicStreamReader connectReader(SequentialScheduler scheduler) {
        var reader = this.reader;
        if (reader == null) {
            reader = new QuicStreamReaderImpl(scheduler);
            if (Handles.READER.compareAndSet(this, null, reader)) {
                if (debug.on()) debug.log("reader connected");
                return reader;
            }
        }
        throw new IllegalStateException("reader already connected");
    }

    @Override
    public void disconnectReader(QuicStreamReader reader) {
        var previous = this.reader;
        if (reader == previous) {
            if (Handles.READER.compareAndSet(this, reader, null)) {
                if (debug.on()) debug.log("reader disconnected");
                return;
            }
        }
        throw new IllegalStateException("reader not connected");
    }

    @Override
    public boolean isStopSendingRequested() {
        return requestedStopSending;
    }

    @Override
    public void requestStopSending(final long errorCode) {
        if (Handles.STOP_SENDING.compareAndSet(this, false, true)) {
            assert requestedStopSending : "requestedStopSending should be true!";
            if (debug.on()) debug.log("requestedStopSending: true");
            var state = receivingState;
            try {
                setErrorCode(errorCode);
                switch(state) {
                    case RECV, SIZE_KNOWN -> {
                        connection().scheduleStopSendingFrame(streamId(), errorCode);
                    }
                    // otherwise do nothing
                }
            } finally {
                // RFC-9000, section 3.5: "If an application is no longer interested in the data it is
                // receiving on a stream, it can abort reading the stream and specify an application
                // error code."
                // So it implies that the application isn't anymore interested in receiving the data
                // that has been buffered in the stream, so we drop all buffered data on this stream
                if (state != RECV && state != DATA_READ) {
                    // we know the final size; we can remove the stream
                    increaseProcessedData(knownSize);
                    if (switchReceivingState(RESET_READ)) {
                        eof = false;
                    }
                }
                dataFlow.clear();
                orderedQueue.clear();
                if (debug.on()) {
                    debug.log("Dropped all buffered frames on stream %d after STOP_SENDING was requested" +
                            " with error code 0x%s", streamId(), Long.toHexString(errorCode));
                }
            }
        }
    }

    @Override
    public long dataReceived() {
        return received;
    }

    @Override
    public long maxStreamData() {
        return maxStreamData;
    }

    @Override
    public boolean isDone() {
        return switch (receivingState()) {
            case DATA_READ, DATA_RECVD, RESET_READ, RESET_RECVD ->
                // everything received from peer
                true;
            default ->
                // the stream is only half closed
                false;
        };
    }

    /**
     * Receives a QuicFrame from the remote peer.
     *
     * @param resetStreamFrame the frame received
     */
    void processIncomingResetFrame(final ResetStreamFrame resetStreamFrame)
            throws QuicTransportException {
        try {
            checkUpdateState(resetStreamFrame);
            if (requestedStopSending) {
                increaseProcessedData(knownSize);
                switchReceivingState(RESET_READ);
            }
        } finally {
            // make sure the state is switched to reset received.
            // even if we're closing the connection
            switchReceivingState(RESET_RECVD);
            // wakeup reader, then throw exception.
            QuicStreamReaderImpl reader = this.reader;
            if (reader != null) reader.wakeup();
        }
    }

    void processIncomingFrame(final StreamDataBlockedFrame streamDataBlocked) {
        assert streamDataBlocked.streamId() == streamId() : "unexpected stream id";
        final long peerBlockedOn = streamDataBlocked.maxStreamData();
        final long currentLimit = this.maxStreamData;
        if (peerBlockedOn > currentLimit) {
            // shouldn't have happened. ignore and don't increase the limit.
            return;
        }
        // the peer has stated that the stream is blocked due to flow control limit that we have
        // imposed and has requested for increasing the limit. we approve that request
        // and increase the limit only if the amount of received data that we have received and
        // processed on this stream is more than 1/4 of the credit window.
        if (!requestedStopSending
                && currentLimit - processed < (desiredBufferSize - desiredBufferSize / 4)) {
            this.reader.demand(desiredBufferSize);
        } else {
            if (debug.on()) {
                debug.log("ignoring STREAM_DATA_BLOCKED frame %s," +
                        " since current limit %d is large enough", streamDataBlocked, currentLimit);
            }
        }
    }

    /**
     * Called when the connection is closed
     * @param terminationCause the termination cause
     */
    void terminate(final TerminationCause terminationCause) {
        setErrorCode(terminationCause.getCloseCode());
        final QuicStreamReaderImpl reader = this.reader;
        if (reader != null) {
            reader.wakeup();
        }
    }

    @Override
    public long rcvErrorCode() {
        return errorCode;
    }

    /**
     * Receives a QuicFrame from the remote peer.
     *
     * @param streamFrame the frame received
     */
    public void processIncomingFrame(final StreamFrame streamFrame)
            throws QuicTransportException {
        // RFC-9000, section 3.5: "STREAM frames received after sending a STOP_SENDING frame
        // are still counted toward connection and stream flow control, even though these
        // frames can be discarded upon receipt."
        // so we do the necessary data size checks before checking if we sent a "STOP_SENDING"
        // frame
        checkUpdateState(streamFrame);
        final ReceivingStreamState state = receivingState;
        if (debug.on()) debug.log("receivingState: " + state);
        long knownSize = this.knownSize;
        // RESET was read or received: drop the frame.
        if (state == RESET_READ || state == RESET_RECVD) {
            if (debug.on()) {
                debug.log("Dropping frame on stream %d since state is %s",
                        streamId(), state);
            }
            return;
        }
        if (requestedStopSending) {
            // drop the frame
            if (debug.on()) {
                debug.log("Dropping frame that was received after a STOP_SENDING" +
                        " frame was sent on stream %d", streamId());
            }
            increaseProcessedData(maxReceivedData);
            if (state != RECV) {
                // we know the final size; we can remove the stream
                switchReceivingState(RESET_READ);
            }
            return;
        }

        var readyFrame = dataFlow.receive(streamFrame);
        var received = this.received;
        boolean needWakeup = false;
        while (readyFrame != null) {
            // check again - this avoids a race condition where a frame
            // would be considered ready if requestStopSending had been
            // called concurrently, and `receive` was called after the
            // state had been switched
            if (requestedStopSending) {
                return;
            }
            assert received == readyFrame.offset()
                    : "data received (%s) doesn't match offset (%s)"
                    .formatted(received, readyFrame.offset());
            this.received = received = received + readyFrame.dataLength();
            offer(readyFrame);
            needWakeup = true;
            readyFrame = dataFlow.poll();
        }
        if (state == SIZE_KNOWN && received == knownSize) {
            if (switchReceivingState(DATA_RECVD)) {
                offerEof();
                needWakeup = true;
            }
        }
        if (needWakeup) {
            var reader = this.reader;
            if (reader != null) reader.wakeup();
        } else {
            int numFrames = dataFlow.size();
            long numBytes = dataFlow.buffered();
            if (numFrames > MAX_SMALL_FRAGMENTS && numBytes / numFrames < 400) {
                // The peer sent a large number of small fragments
                // that follow a gap and can't be immediately released to the reader;
                // we need to buffer them, and the memory overhead is unreasonably high.
                throw new QuicTransportException("Excessive stream fragmentation",
                        QuicTLSEngine.KeySpace.ONE_RTT, streamFrame.frameType(),
                        QuicTransportErrors.INTERNAL_ERROR);
            }
        }
    }

    /**
     * Checks for error conditions:
     * - max stream data errors
     * - max data errors
     * - final size errors
     * If everything checks OK, updates counters and returns, otherwise throws.
     *
     * @implNote
     * This method may update counters before throwing. This is OK
     * because we do not expect to use them again in this case.
     * @param streamFrame received stream frame
     * @throws QuicTransportException if frame is invalid
     */
    private void checkUpdateState(StreamFrame streamFrame) throws QuicTransportException {
        long offset = streamFrame.offset();
        long length = streamFrame.dataLength();
        assert offset >= 0;
        assert length >= 0;

        // check maxStreamData
        long maxData = maxStreamData;
        assert maxData >= 0;
        long size;
        try {
            size = Math.addExact(offset, length);
        } catch (ArithmeticException x) {
            // should not happen
            if (debug.on()) {
                debug.log("offset + length exceeds max value", x);
            }
            throw streamControlOverflow(streamFrame, Long.MAX_VALUE);
        }
        if (size > maxData) {
            throw streamControlOverflow(streamFrame, maxData);
        }
        ReceivingStreamState state = receivingState;
        // check finalSize if known
        long knownSize = this.knownSize;
        assert knownSize >= 0;
        if (state != RECV && size > knownSize) {
            String reason = "Stream final size exceeded: offset=%s, length=%s, final size=%s"
                    .formatted(streamFrame.offset(), streamFrame.dataLength(), knownSize);
            throw new QuicTransportException(reason,
                    QuicTLSEngine.KeySpace.ONE_RTT, streamFrame.getTypeField(), QuicTransportErrors.FINAL_SIZE_ERROR);
        }
        // check maxData
        updateMaxReceivedData(size, streamFrame.getTypeField());
        if (streamFrame.isLast()) {
            // check max received data, throw if we have data beyond the (new) EOF
            if (size < maxReceivedData) {
                String reason = "Stream truncated: offset=%s, length=%s, max received=%s"
                        .formatted(streamFrame.offset(), streamFrame.dataLength(), maxReceivedData);
                throw new QuicTransportException(reason,
                        QuicTLSEngine.KeySpace.ONE_RTT, streamFrame.getTypeField(), QuicTransportErrors.FINAL_SIZE_ERROR);
            }
            if (state == RECV && switchReceivingState(SIZE_KNOWN)) {
                this.knownSize = size;
            } else {
                if (size != knownSize) {
                    String reason = "Stream final size changed: offset=%s, length=%s, final size=%s"
                            .formatted(streamFrame.offset(), streamFrame.dataLength(), knownSize);
                    throw new QuicTransportException(reason,
                            QuicTLSEngine.KeySpace.ONE_RTT, streamFrame.getTypeField(), QuicTransportErrors.FINAL_SIZE_ERROR);
                }
            }
        }
    }

    /**
     * Checks for error conditions:
     * - max stream data errors
     * - max data errors
     * - final size errors
     * If everything checks OK, updates counters and returns, otherwise throws.
     *
     * @implNote
     * This method may update counters before throwing. This is OK
     * because we do not expect to use them again in this case.
     * @param resetStreamFrame received reset stream frame
     * @throws QuicTransportException if frame is invalid
     */
    private void checkUpdateState(ResetStreamFrame resetStreamFrame) throws QuicTransportException {
        // check maxStreamData
        long maxData = maxStreamData;
        assert maxData >= 0;
        long size = resetStreamFrame.finalSize();
        long errorCode = resetStreamFrame.errorCode();
        setErrorCode(errorCode);
        if (size > maxData) {
            String reason = "Stream max data exceeded: finalSize=%s, max stream data=%s"
                    .formatted(size, maxData);
            throw new QuicTransportException(reason,
                    QuicTLSEngine.KeySpace.ONE_RTT, resetStreamFrame.getTypeField(), QuicTransportErrors.FLOW_CONTROL_ERROR);
        }
        ReceivingStreamState state = receivingState;
        updateMaxReceivedData(size, resetStreamFrame.getTypeField());
        // check max received data, throw if we have data beyond the (new) EOF
        if (size < maxReceivedData) {
            String reason = "Stream truncated: finalSize=%s, max received=%s"
                    .formatted(size, maxReceivedData);
            throw new QuicTransportException(reason,
                    QuicTLSEngine.KeySpace.ONE_RTT, resetStreamFrame.getTypeField(), QuicTransportErrors.FINAL_SIZE_ERROR);
        }
        if (state == RECV && switchReceivingState(RESET_RECVD)) {
            this.knownSize = size;
        } else {
            if (state == SIZE_KNOWN) {
                switchReceivingState(RESET_RECVD);
            }
            if (size != knownSize) {
                String reason = "Stream final size changed: new finalSize=%s, old final size=%s"
                        .formatted(size, knownSize);
                throw new QuicTransportException(reason,
                        QuicTLSEngine.KeySpace.ONE_RTT, resetStreamFrame.getTypeField(), QuicTransportErrors.FINAL_SIZE_ERROR);
            }
        }
    }

    void checkOpened() throws IOException {
        final TerminationCause terminationCause = connection().terminationCause();
        if (terminationCause == null) {
            return;
        }
        throw terminationCause.getCloseCause();
    }

    private void offer(StreamFrame frame) {
        var payload = frame.payload();
        var isLast = frame.isLast();
        if (payload.hasRemaining()) {
            orderedQueue.add(payload.slice());
        }
    }

    private void offerEof() {
        orderedQueue.add(QuicStreamReader.EOF);
    }

    /**
     * Update the value of MAX_STREAM_DATA for this stream
     * @param newMaxStreamData
     */
    public void updateMaxStreamData(long newMaxStreamData) {
        long maxStreamData = this.maxStreamData;
        boolean updated = false;
        while (maxStreamData < newMaxStreamData) {
            if (updated = Handles.MAX_STREAM_DATA.compareAndSet(this, maxStreamData, newMaxStreamData)) break;
            maxStreamData = this.maxStreamData;
        }
        if (updated) {
            if (debug.on()) {
                debug.log("updateMaxStreamData: max stream data updated from %s to %s",
                        maxStreamData, newMaxStreamData);
            }
        }
    }

    /**
     * Update the {@code maxReceivedData} value, and return the amount
     * by which {@code maxReceivedData} was increased. This method is a
     * no-op and returns 0 if {@code maxReceivedData >= newMax}.
     *
     * @param newMax the new max offset - typically obtained
     *               by adding the length of a frame to its
     *               offset
     * @param frameType type of frame received
     * @throws QuicTransportException if flow control was violated
     */
    private void updateMaxReceivedData(long newMax, long frameType) throws QuicTransportException {
        assert newMax >= 0;
        var max = this.maxReceivedData;
        while (max < newMax) {
            if (Handles.MAX_RECEIVED_DATA.compareAndSet(this, max, newMax)) {
                // report accepted data to connection flow control,
                // and update the amount of data received in the
                // connection. This will also check whether connection
                // flow control is exceeded, and throw in
                // this case
                connection().increaseReceivedData(newMax - max, frameType);
                return;
            }
            max = this.maxReceivedData;
        }
    }

    /**
     * Notifies the connection about received data that is no longer buffered.
     */
    private void increaseProcessedDataBy(int diff) {
        assert diff >= 0;
        if (diff <= 0) return;
        synchronized (this) {
            if (requestedStopSending) {
                // once we request stop sending, updates are handled by increaseProcessedData
                return;
            }
            assert processed + diff <= received : processed+"+"+diff+">"+received+"("+maxReceivedData+")";
            processed += diff;
        }
        connection().increaseProcessedData(diff);
    }

    /**
     * Notifies the connection about received data that is no longer buffered.
     */
    private void increaseProcessedData(long newProcessed) {
        long diff;
        synchronized (this) {
            if (newProcessed > processed) {
                diff = newProcessed - processed;
                processed = newProcessed;
            } else {
                diff = 0;
            }
        }
        if (diff > 0) {
            connection().increaseProcessedData(diff);
        }
    }

    // private implementation of a QuicStreamReader for this stream
    private final class QuicStreamReaderImpl extends QuicStreamReader {

        static final int STARTED = 1;
        static final int PENDING = 2;
        // should not need volatile here, as long as we
        // switch to using synchronize whenever state & STARTED == 0
        // Once state & STARTED != 0 the state should no longer change
        private int state;

        QuicStreamReaderImpl(SequentialScheduler scheduler) {
            super(scheduler);
        }

        @Override
        public ReceivingStreamState receivingState() {
            checkConnected();
            return QuicReceiverStreamImpl.this.receivingState();
        }

        @Override
        public ByteBuffer poll() throws IOException {
            checkConnected();
            var buffer = orderedQueue.poll();
            if (buffer == null) {
                if (eof) return EOF;
                var state = receivingState;
                if (state == RESET_RECVD) {
                    increaseProcessedData(knownSize);
                    switchReceivingState(RESET_READ);
                }
                checkReset(true);
                // unfulfilled = maxStreamData - received;
                // if we have received more than 1/4 of the buffer, update maxStreamData
                if (!requestedStopSending && unfulfilled() < desiredBufferSize - desiredBufferSize / 4) {
                    demand(desiredBufferSize);
                }
                return null;
            }

            if (requestedStopSending) {
                // check reset again
                checkReset(true);
                return null;
            }
            increaseProcessedDataBy(buffer.capacity());
            if (buffer == EOF) {
                eof = true;
                assert processed == received : processed + "!=" + received;
                switchReceivingState(DATA_READ);
                return EOF;
            }
            // if the amount of received data that has been processed on this stream is
            // more than 1/4 of the credit window then send a MaxStreamData frame.
            if (!requestedStopSending && maxStreamData - processed < desiredBufferSize - desiredBufferSize / 4) {
                demand(desiredBufferSize);
            }
            return buffer;
        }

        /**
         * Check whether the stream was reset and throws an exception if
         * yes.
         * If {@code throwIfClosed} is true and the state is DATA_READ
         * also throws an exception.
         *
         * @apiNote
         * Typically - peek will call this method with `false` and just
         * return null if all data has been read, while poll() will throw
         * if it's called again after EOF.
         *
         * @param throwIfClosed whether an exception should be thrown
         *                      when the stream state is DATA_READ
         * @throws IOException if the stream is closed or reset
         */
        private void checkReset(boolean throwIfClosed) throws IOException {
            var state = receivingState;
            if (state == RESET_READ || state == RESET_RECVD) {
                if (state == RESET_READ) {
                    switchReceivingState(RESET_RECVD);
                }
                if (requestedStopSending) {
                    throw new IOException("Stream %s closed".formatted(streamId()));
                } else {
                    throw new IOException("Stream %s reset by peer".formatted(streamId()));
                }
            }
            if (state == DATA_READ && throwIfClosed) {
                throw new IOException("Stream %s closed".formatted(streamId()));
            }
            checkOpened();
        }

        @Override
        public ByteBuffer peek() throws IOException {
            checkConnected();
            var buffer = orderedQueue.peek();
            if (buffer == null) {
                checkReset(false);
                return eof ? EOF : null;
            }
            return buffer;
        }

        private void demand(final long additional) {
            assert additional > 0 && additional < MAX_VL_INTEGER : "invalid demand: " + additional;
            var received = dataReceived();
            var maxStreamData = maxStreamData();

            final long newMax = Math.clamp(received + additional, maxStreamData, MAX_VL_INTEGER);
            if (newMax > maxStreamData) {
                connection().requestSendMaxStreamData(streamId(), newMax);
                updateMaxStreamData(newMax);
            }
        }

        private long unfulfilled() {
            // TODO: should we synchronize to ensure consistency?
            var max = maxStreamData;
            var rcved = received;
            return max - rcved;
        }

        @Override
        public QuicReceiverStream stream() {
            var stream = QuicReceiverStreamImpl.this;
            var reader = stream.reader;
            return reader == this ? stream : null;
        }

        @Override
        public boolean connected() {
            var reader = QuicReceiverStreamImpl.this.reader;
            return reader == this;
        }

        @Override
        public boolean started() {
            int state = this.state;
            if ((state & STARTED) == STARTED) return true;
            synchronized (this) {
                state = this.state;
                return (state & STARTED) == STARTED;
            }
        }

        private boolean wakeupOnStart(int state) {
            assert Thread.holdsLock(this);
            return (state & PENDING) != 0
                    || !orderedQueue.isEmpty()
                    || receivingState != RECV;
        }

        @Override
        public void start() {
            // Run the scheduler if woken up before starting
            int state = this.state;
            if ((state & STARTED) == 0) {
                boolean wakeup = false;
                synchronized (this) {
                    state = this.state;
                    if ((state & STARTED) == 0) {
                        wakeup = wakeupOnStart(state);
                        state = this.state = STARTED;
                    }
                }
                assert started();
                if (debug.on()) {
                    debug.log("reader started (wakeup: %s)", wakeup);
                }
                if (wakeup || !orderedQueue.isEmpty() || receivingState != RECV) wakeup();
            }
            assert started();
        }

        private void checkConnected() {
            if (!connected()) throw new IllegalStateException("reader not connected");
        }

        void wakeup() {
            // Only run the scheduler after the reader is started.
            int state = this.state;
            boolean notstarted, pending = false;
            if (notstarted = ((state & STARTED) == 0)) {
                synchronized (this) {
                    state = this.state;
                    if (notstarted = ((state & STARTED) == 0)) {
                        state = this.state = (state | PENDING);
                        pending = (state & PENDING) == PENDING;
                        assert !started();
                    }
                }
            }
            if (notstarted) {
                if (debug.on()) {
                    debug.log("reader not started (pending: %s)", pending);
                }
                return;
            }
            assert started();
            scheduler.runOrSchedule(connection().quicInstance().executor());
        }
    }

    /**
     * Called when a state change is needed
     * @param newState the new state.
     */
    private boolean switchReceivingState(ReceivingStreamState newState) {
        ReceivingStreamState oldState = receivingState;
        if (debug.on()) {
            debug.log("switchReceivingState %s -> %s",
                    oldState, newState);
        }
        boolean switched = switch(newState) {
            case SIZE_KNOWN ->  markSizeKnown();
            case DATA_RECVD ->  markDataRecvd();
            case RESET_RECVD -> markResetRecvd();
            case RESET_READ ->  markResetRead();
            case DATA_READ ->   markDataRead();
            default -> throw new UnsupportedOperationException("switch state to " + newState);
        };
        if (debug.on()) {
            if (switched) {
                debug.log("switched receiving state from %s to %s", oldState, newState);
            } else {
                debug.log("receiving state not switched; state is %s", receivingState);
            }
        }

        if (switched && newState.isTerminal()) {
            notifyTerminalState(newState);
        }

        return switched;
    }

    private void notifyTerminalState(ReceivingStreamState state) {
        assert state == DATA_READ || state == RESET_READ : state;
        connection().notifyTerminalState(streamId(), state);
    }

    // DATA_RECV is reached when the last frame is received,
    // and there's no gap
    private boolean markDataRecvd() {
        boolean done, switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case RECV, SIZE_KNOWN -> switched =
                        Handles.RECEIVING_STATE.compareAndSet(this,
                                oldState, DATA_RECVD);
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ -> true;
            };
        } while (!done);
        return switched;
    }

    // SIZE_KNOWN is reached when a stream frame with the FIN bit is received
    private boolean markSizeKnown() {
        boolean done, switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case RECV -> switched =
                        Handles.RECEIVING_STATE.compareAndSet(this,
                                oldState, SIZE_KNOWN);
                case DATA_RECVD, DATA_READ, SIZE_KNOWN, RESET_RECVD, RESET_READ -> true;
            };
        } while(!done);
        return switched;
    }

    // RESET_RECV is reached when a RESET_STREAM frame is received
    private boolean markResetRecvd() {
        boolean done, switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case RECV, SIZE_KNOWN -> switched =
                        Handles.RECEIVING_STATE.compareAndSet(this,
                                oldState, RESET_RECVD);
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ -> true;
            };
        } while(!done);
        return switched;
    }

    // Called when the consumer has polled the last data
    // DATA_READ is a terminal state
    private boolean markDataRead() {
        boolean done, switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case SIZE_KNOWN, DATA_RECVD, RESET_RECVD -> switched =
                        Handles.RECEIVING_STATE.compareAndSet(this,
                                oldState, DATA_READ);
                case RESET_READ, DATA_READ -> true;
                default -> throw new IllegalStateException("%s: %s -> %s"
                        .formatted(streamId(), oldState, DATA_READ));
            };
        } while(!done);
        return switched;
    }

    // Called when the consumer has read the reset
    // RESET_READ is a terminal state
    private boolean markResetRead() {
        boolean done, switched = false;
        ReceivingStreamState oldState;
        do {
            oldState = receivingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case SIZE_KNOWN, DATA_RECVD, RESET_RECVD -> switched =
                        Handles.RECEIVING_STATE.compareAndSet(this,
                                oldState, RESET_READ);
                case RESET_READ, DATA_READ -> true;
                default -> throw new IllegalStateException("%s: %s -> %s"
                        .formatted(streamId(), oldState, RESET_READ));
            };
        } while(!done);
        return switched;
    }

    private void setErrorCode(long code) {
        Handles.ERROR_CODE.compareAndSet(this, -1, code);
    }

    private static final class Handles {
        static final VarHandle READER;
        static final VarHandle RECEIVING_STATE;
        static final VarHandle MAX_STREAM_DATA;
        static final VarHandle MAX_RECEIVED_DATA;
        static final VarHandle STOP_SENDING;
        static final VarHandle ERROR_CODE;
        static {
            try {
                var lookup = MethodHandles.lookup();
                RECEIVING_STATE = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                        "receivingState", ReceivingStreamState.class);
                READER = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                        "reader", QuicStreamReaderImpl.class);
                MAX_STREAM_DATA = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                        "maxStreamData", long.class);
                MAX_RECEIVED_DATA = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                        "maxReceivedData", long.class);
                STOP_SENDING = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                        "requestedStopSending", boolean.class);
                ERROR_CODE = lookup.findVarHandle(QuicReceiverStreamImpl.class,
                        "errorCode", long.class);
            } catch (Exception x) {
                throw new ExceptionInInitializerError(x);
            }
        }
    }

}
