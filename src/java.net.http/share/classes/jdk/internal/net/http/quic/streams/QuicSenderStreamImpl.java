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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.Set;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.TerminationCause;

/**
 * A class that implements the sending part of a quic stream.
 */
public final class QuicSenderStreamImpl extends AbstractQuicStream implements QuicSenderStream {
    private volatile SendingStreamState sendingState;
    private volatile QuicStreamWriterImpl writer;
    private final Logger debug = Utils.getDebugLogger(this::dbgTag);
    private final String dbgTag;
    private final StreamWriterQueueImpl queue = new StreamWriterQueueImpl();
    private volatile long errorCode;
    private volatile boolean stopSendingReceived;

    QuicSenderStreamImpl(QuicConnectionImpl connection, long streamId) {
        super(connection, validateStreamId(connection, streamId));
        errorCode = -1;
        sendingState = SendingStreamState.READY;
        dbgTag = connection.streamDbgTag(streamId, "W");
    }

    private static long validateStreamId(QuicConnectionImpl connection, long streamId) {
        if (QuicStreams.isBidirectional(streamId)) return streamId;
        if (connection.isClientConnection() != QuicStreams.isClientInitiated(streamId)) {
            throw new IllegalArgumentException("A remotely initiated stream can't be write-only");
        }
        return streamId;
    }

    String dbgTag() {
        return dbgTag;
    }

    @Override
    public SendingStreamState sendingState() {
        return sendingState;
    }

    @Override
    public QuicStreamWriter connectWriter(SequentialScheduler scheduler) {
        var writer = this.writer;
        if (writer == null) {
            writer = new QuicStreamWriterImpl(scheduler);
            if (Handles.WRITER.compareAndSet(this, null, writer)) {
                if (debug.on()) debug.log("writer connected");
                return writer;
            }
        }
        throw new IllegalStateException("writer already connected");
    }

    @Override
    public void disconnectWriter(QuicStreamWriter writer) {
        var previous = this.writer;
        if (writer == previous) {
            if (Handles.WRITER.compareAndSet(this, writer, null)) {
                if (debug.on()) debug.log("writer disconnected");
                return;
            }
        }
        throw new IllegalStateException("reader not connected");
    }


    @Override
    public void reset(long errorCode) throws IOException {
        if (debug.on()) {
            debug.log("Resetting stream %s due to %s", streamId(),
                   connection().quicInstance().appErrorToString(errorCode));
        }
        setErrorCode(errorCode);
        if (switchSendingState(SendingStreamState.RESET_SENT)) {
            long streamId = streamId();
            if (debug.on()) {
                debug.log("Requesting to send RESET_STREAM(%d, %d)",
                        streamId, errorCode);
            }
            queue.markReset();
            if (connection().isOpen()) {
                connection().requestResetStream(streamId, errorCode);
            }
        }
    }

    @Override
    public long sndErrorCode() {
        return errorCode;
    }

    @Override
    public boolean stopSendingReceived() {
        return stopSendingReceived;
    }

    @Override
    public long dataSent() {
        // returns the amount of data that has been submitted for
        // sending downstream. This will be the amount of data that
        // has been consumed by the downstream consumer.
        return queue.bytesConsumed();
    }

    /**
     * Called to set the max stream data for this stream.
     * @apiNote as per RFC 9000, any value less than the current
     *          max stream data is ignored
     * @param newLimit the proposed new max stream data
     * @return the new limit that has been finalized for max stream data.
     *         This new limit may or may not have been increased to the proposed {@code newLimit}.
     */
    public long setMaxStreamData(final long newLimit) {
        return queue.setMaxStreamData(newLimit);
    }

    /**
     * Called by {@link QuicConnectionStreams} after a RESET_STREAM frame
     * has been sent
     */
    public void resetSent() {
        queue.markReset();
        queue.close();
    }

    /**
     * Called when the packet containing the RESET_STREAM frame for this
     * stream has been acknowledged.
     * @param finalSize the final size acknowledged
     * @return true if the state was switched to RESET_RECVD as a result
     * of this method invocation
     */
    public boolean resetAcknowledged(long finalSize) {
        long queueSize = queue.bytesConsumed();
        if (switchSendingState(SendingStreamState.RESET_RECVD)) {
            if (debug.on()) {
                debug.log("Reset received: final: %d, processed: %d",
                        finalSize, queueSize);
            }
            if (finalSize != queueSize) {
                if (Log.errors()) {
                    Log.logError("Stream %d: Acknowledged reset has wrong size: acked: %d, expected: %d",
                            streamId(), finalSize, queueSize);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Called when the packet containing the final STREAM frame for this
     * stream has been acknowledged.
     * @param finalSize the final size acknowledged
     * @return true if the state was switched to DATA_RECVD as a result
     * of this method invocation
     */
    public boolean dataAcknowledged(long finalSize) {
        long queueSize = queue.bytesConsumed();
        if (switchSendingState(SendingStreamState.DATA_RECVD)) {
            if (debug.on()) {
                debug.log("Last data received: final: %d, processed: %d",
                        finalSize, queueSize);
            }
            if (finalSize != queueSize) {
                if (Log.errors()) {
                    Log.logError("Stream %d: Acknowledged data has wrong size: acked: %d, expected: %d",
                            streamId(), finalSize, queueSize);
                }
            }
        }
        return false;
    }

    /**
     * Called when a STOP_SENDING frame is received from the peer
     * @param errorCode the error code
     */
    public void stopSendingReceived(long errorCode) {
        if (queue.stopSending(errorCode)) {
            stopSendingReceived = true;
            setErrorCode(errorCode);
            try {
                if (connection().isOpen()) {
                    reset(errorCode);
                }
            } catch (IOException io) {
                if (debug.on()) debug.log("Reset failed: " + io);
            } finally {
                QuicStreamWriterImpl writer = this.writer;
                if (writer != null) writer.wakeupWriter();
            }
        }
    }

    /**
     * Called when the connection is closed locally
     * @param terminationCause the termination cause
     */
    void terminate(final TerminationCause terminationCause) {
        setErrorCode(terminationCause.getCloseCode());
        queue.close();
        final QuicStreamWriterImpl writer = this.writer;
        if (writer != null) {
            writer.wakeupWriter();
        }
    }

    /**
     * A concrete implementation of the {@link StreamWriterQueue} for this
     * stream.
     */
    private final class StreamWriterQueueImpl extends StreamWriterQueue {
        @Override
        protected void wakeupProducer() {
            // The scheduler is provided by the producer
            // to wakeup and run the producer's write loop.
            var writer = QuicSenderStreamImpl.this.writer;
            if (writer != null) {
                writer.wakeupWriter();
            }
        }

        @Override
        protected Logger debug() {
            return debug;
        }

        @Override
        protected void wakeupConsumer() {
            // Notify the connection impl that either the data is available
            // for writing or the stream is blocked and the peer needs to be
            // made aware. The connection should
            // eventually call QuicSenderStreamImpl::poll to
            // get the data available for writing and package it
            // in a StreamFrame or notice that the stream is blocked and send a
            // STREAM_DATA_BLOCKED frame.
            connection().streamDataAvailableForSending(Set.of(streamId()));
        }

        @Override
        protected void switchState(SendingStreamState state) {
            // called to indicate a change in the stream state.
            // at the moment the only expected value is DATA_SENT
            assert state == SendingStreamState.DATA_SENT;
            switchSendingState(state);
        }

        @Override
        protected long streamId() {
            return QuicSenderStreamImpl.this.streamId();
        }
    }


    /**
     * The stream internal implementation of a QuicStreamWriter.
     * Most of the logic is implemented in the StreamWriterQueue,
     * which is subclassed here to provide an implementation of its
     * few abstract methods.
     */
    private class QuicStreamWriterImpl extends QuicStreamWriter {
        QuicStreamWriterImpl(SequentialScheduler scheduler) {
            super(scheduler);
        }

        void wakeupWriter() {
            scheduler.runOrSchedule(connection().quicInstance().executor());
        }

        @Override
        public SendingStreamState sendingState() {
            checkConnected();
            return QuicSenderStreamImpl.this.sendingState();
        }

        @Override
        public void scheduleForWriting(ByteBuffer buffer, boolean last) throws IOException {
            checkConnected();
            SendingStreamState state = sending(last);
            switch (state) {
                // this isn't atomic but it doesn't really matter since reset
                // will be handled by the same thread that polls.
                case READY, SEND -> {
                    // allow a last empty buffer to be submitted even
                    // if the connection is closed. That can help
                    // unblock the consumer side.
                    if (buffer != QuicStreamReader.EOF || !last) {
                        checkOpened();
                    }
                    queue.submit(buffer, last);
                }
                case RESET_SENT, RESET_RECVD -> throw streamResetException();
                case DATA_SENT, DATA_RECVD -> throw streamClosedException();
            }
        }

        @Override
        public void queueForWriting(ByteBuffer buffer) throws IOException {
            checkConnected();
            SendingStreamState state = sending(false);
            switch (state) {
                // this isn't atomic but it doesn't really matter since reset
                // will be handled by the same thread that polls.
                case READY, SEND -> {
                    checkOpened();
                    queue.queue(buffer);
                }
                case RESET_SENT, RESET_RECVD -> throw streamResetException();
                case DATA_SENT, DATA_RECVD -> throw streamClosedException();
            }
        }

        /**
         * Compose an exception to throw if data is submitted after the
         * stream was reset
         * @return a new IOException
         */
        IOException streamResetException() {
            long resetByPeer = queue.resetByPeer();
            if (resetByPeer < 0) {
                return new IOException("stream %s reset by peer: errorCode %s"
                        .formatted(streamId(), - resetByPeer - 1));
            } else {
                return new IOException("stream %s has been reset".formatted(streamId()));
            }
        }

        /**
         * Compose an exception to throw if data is submitted after the
         * the final data has been sent
         * @return a new IOException
         */
        IOException streamClosedException() {
            return new IOException("stream %s is closed - all data has been sent"
                    .formatted(streamId()));
        }

        @Override
        public long credit() {
            checkConnected();
            // how much data the producer can send before
            // reaching the flow control limit. Could be
            // negative if the limit has been reached already.
            return queue.producerCredit();
        }

        @Override
        public void reset(long errorCode) throws IOException {
            setErrorCode(errorCode);
            checkConnected();
            QuicSenderStreamImpl.this.reset(errorCode);
        }

        @Override
        public QuicSenderStream stream() {
            var stream = QuicSenderStreamImpl.this;
            var writer = stream.writer;
            return writer == this ? stream : null;
        }

        @Override
        public boolean connected() {
            var writer = QuicSenderStreamImpl.this.writer;
            return writer == this;
        }

        private void checkConnected() {
            if (!connected()) {
                throw new IllegalStateException("writer not connected");
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

    /**
     * {@return the number of bytes that are available for sending, subject
     * to flow control}
     * @implSpec
     * This method does not return more than what flow control for this
     * stream would allow at the time the method is called.
     * @implNote
     * If the sender part is not finished initializing the default
     * implementation of this method will return 0.
     */
    public long available() {
        return queue.readyToSend();
    }

    /**
     * Whether the sending is blocked due to flow control.
     * @return {@code true} if sending is blocked due to flow control
     */
    public boolean isBlocked() {
        return queue.consumerBlocked();
    }

    /**
     * {@return the size of this stream, if known}
     * @implSpec
     * This method returns {@code -1} if the size of the stream is not
     * known.
     */
    public long streamSize() {
        return queue.streamSize();
    }

    /**
     * Polls at most {@code maxBytes} from the {@link StreamWriterQueue} of
     * this stream. The semantics are equivalent to that of {@link
     * StreamWriterQueue#poll(int)}
     * @param maxBytes the maximum number of bytes to poll for sending
     * @return a ByteBuffer containing at most {@code maxBytes} remaining
     *         bytes.
     */
    public ByteBuffer poll(int maxBytes) {
        return queue.poll(maxBytes);
    }

    @Override
    public boolean isDone() {
        return switch (sendingState()) {
            case DATA_RECVD, RESET_RECVD ->
                // everything acknowledged
                true;
            default ->
                // the stream is only half closed
                false;
        };
    }

    @Override
    public StreamState state() {
        return sendingState();
    }

    /**
     * Called when some data is submitted (or offered) by the
     * producer. If the stream is in the READY state, this will
     * switch the sending state to SEND.
     * @implNote
     * The parameter {@code last} is ignored at this stage.
     * {@link #switchSendingState(SendingStreamState)
     * switchSendingState(SendingStreamState.DATA_SENT)} will be called
     * later on when the last piece of data has been pushed downstream.
     *
     * @param last whether there will be no further data submitted
     *             by the producer.
     *
     * @return the state before switching to SEND.
     */
    private SendingStreamState sending(boolean last) {
        SendingStreamState state = sendingState;
        if (state == SendingStreamState.READY) {
            switchSendingState(SendingStreamState.SEND);
        }
        return state;
    }

    /**
     * Called when the StreamWriterQueue implementation notifies of
     * a state change.
     * @param newState the new state, according to the StreamWriterQueue.
     */
    private boolean switchSendingState(SendingStreamState newState) {
        SendingStreamState oldState = sendingState;
        if (debug.on()) {
            debug.log("switchSendingState %s -> %s",
                    oldState, newState);
        }
        boolean switched = switch(newState) {
            case SEND       -> markSending();
            case DATA_SENT  -> markDataSent();
            case DATA_RECVD -> markDataRecvd();
            case RESET_SENT -> markResetSent();
            case RESET_RECVD -> markResetRecvd();
            default -> throw new UnsupportedOperationException("switch state to " + newState);
        };
        if (debug.on()) {
            if (switched) {
                debug.log("switched sending state from %s to %s", oldState, newState);
            } else {
                debug.log("sending state not switched; state is %s", sendingState);
            }
        }

        if (switched && newState.isTerminal()) {
            notifyTerminalState(newState);
        }

        return switched;
    }

    private void notifyTerminalState(SendingStreamState state) {
        assert state.isTerminal() : state;
        connection().notifyTerminalState(streamId(), state);
    }

    // SEND can only be set from the READY state
    private boolean markSending() {
        boolean done, switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case READY -> switched =
                        Handles.SENDING_STATE.compareAndSet(this,
                            oldState, SendingStreamState.SEND);
                case SEND, RESET_RECVD, RESET_SENT -> true;
                // there should be no further submission of data after DATA_SENT
                case DATA_SENT, DATA_RECVD ->
                        throw new IllegalStateException(String.valueOf(oldState));
            };
        } while(!done);
        return switched;
    }

    // DATA_SENT can only be set from the SEND state
    private boolean markDataSent() {
        boolean done, switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case SEND -> switched =
                        Handles.SENDING_STATE.compareAndSet(this,
                                oldState, SendingStreamState.DATA_SENT);
                case DATA_SENT, RESET_RECVD, RESET_SENT, DATA_RECVD -> true;
                case READY -> throw new IllegalStateException(String.valueOf(oldState));
            };
        } while (!done);
        return switched;
    }

    // Reset can only be set in the READY, SEND, or DATA_SENT state
    private boolean markResetSent() {
        boolean done, switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case READY, SEND, DATA_SENT -> switched =
                        Handles.SENDING_STATE.compareAndSet(this,
                            oldState, SendingStreamState.RESET_SENT);
                case RESET_RECVD, RESET_SENT, DATA_RECVD -> true;
            };
        } while(!done);
        return switched;
    }

    // Called when the packet containing the last frame is acknowledged
    // DATA_RECVD is a terminal state
    private boolean markDataRecvd() {
        boolean done, switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case DATA_SENT, RESET_SENT -> switched =
                        Handles.SENDING_STATE.compareAndSet(this,
                                oldState, SendingStreamState.DATA_RECVD);
                case RESET_RECVD, DATA_RECVD -> true;
                default -> throw new IllegalStateException("%s: %s -> %s"
                        .formatted(streamId(), oldState, SendingStreamState.RESET_RECVD));
            };
        } while(!done);
        return switched;
    }

    // Called when the packet containing the reset frame is acknowledged
    // RESET_RECVD is a terminal state
    private boolean markResetRecvd() {
        boolean done, switched = false;
        SendingStreamState oldState;
        do {
            oldState = sendingState;
            done = switch (oldState) {
                // CAS: Compare And Set
                case DATA_SENT, RESET_SENT -> switched =
                        Handles.SENDING_STATE.compareAndSet(this,
                                oldState, SendingStreamState.RESET_RECVD);
                case RESET_RECVD, DATA_RECVD -> true;
                default -> throw new IllegalStateException("%s: %s -> %s"
                        .formatted(streamId(), oldState, SendingStreamState.RESET_RECVD));
            };
        } while(!done);
        return switched;
    }

    private void setErrorCode(long code) {
        Handles.ERROR_CODE.compareAndSet(this, -1, code);
    }

    // Some VarHandles to implement CAS semantics on top of plain
    // volatile fields in this class.
    private static class Handles {
        static final VarHandle SENDING_STATE;
        static final VarHandle WRITER;
        static final VarHandle ERROR_CODE;
        static {
            Lookup lookup = MethodHandles.lookup();
            try {
                SENDING_STATE = lookup.findVarHandle(QuicSenderStreamImpl.class,
                        "sendingState", SendingStreamState.class);
                WRITER = lookup.findVarHandle(QuicSenderStreamImpl.class,
                        "writer", QuicStreamWriterImpl.class);
                ERROR_CODE = lookup.findVarHandle(QuicSenderStreamImpl.class,
                        "errorCode", long.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

}
