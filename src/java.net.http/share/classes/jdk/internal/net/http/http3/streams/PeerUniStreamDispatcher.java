/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.streams.Http3Streams.StreamType;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.VariableLengthEncoder;

/**
 * A class that analyzes the first byte of the stream to figure
 * out where to dispatch it.
 */
public abstract class PeerUniStreamDispatcher {
    private final SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(this::dispatch);
    private final QuicReceiverStream stream;
    private final CompletableFuture<QuicReceiverStream> cf = new MinimalFuture<>();
    private final QuicStreamReader reader;
    private int vlongSize = 0;
    private ByteBuffer vlongBuf = null; // accumulate bytes until stream type can be decoded
    int longCount;
    private int streamType = -1;
    private long firstLong = -1;
    private long nextLong = -1;

    /**
     * Creates a {@code PeerUniStreamDispatcher} for the given stream.
     * @param stream a new unidirectional stream opened by the peer
     */
    protected PeerUniStreamDispatcher(QuicReceiverStream stream) {
        this.stream = checkStream(stream);
        this.reader = stream.connectReader(scheduler);
        debug().log("dispatcher created for stream " + stream.streamId());
    }

    private static QuicReceiverStream checkStream(QuicReceiverStream stream) {
        if (!stream.isRemoteInitiated()) {
            throw new IllegalArgumentException("stream " + stream.streamId() + " is not peer initiated");
        }
        if (stream.isBidirectional()) {
            throw new IllegalArgumentException("stream " + stream.streamId() + " is not unidirectional");
        }
        return stream;
    }

    /**
     * {@return a completable future that will contain the dispatched stream,
     * once dispatched, or a throwable if dispatching the stream failed}
     */
    public CompletableFuture<QuicReceiverStream> dispatchCF() {
        return cf;
    }

    // The dispatch loop. Connects the reader, then attempts to read
    // the stream type, and dispatches accordingly.
    private void dispatch() {
        try {
            // Bidirectional streams don't have a StreamType
            assert !stream.isBidirectional();
            ByteBuffer buffer;
            ByteBuffer toDecode = null;
            while ((buffer = reader.peek()) != null) {
                if (buffer == QuicStreamReader.EOF) {
                    debug().log("stream %s EOF, cannot dispatch!",
                            stream.streamId());
                    // RFC 9114: https://www.rfc-editor.org/rfc/rfc9114.html#section-6.2-10
                    // > A receiver MUST tolerate unidirectional streams being closed or reset
                    // > prior to the reception of the unidirectional stream header
                    abandon();
                    return;
                }
                if (buffer.remaining() == 0) {
                    // not supposed to happen!
                    debug().log("stream %d: buffer has zero bytes? cannot dispatch!",
                            stream.streamId());
                    // should we poll this buffer?
                    var polled = reader.poll();
                    assert buffer == polled;
                }
                // peeking the stream type.
                if (vlongBuf == null) {
                    // first time around: attempt to read size of the stream type
                    vlongSize = VariableLengthEncoder.peekEncodedValueSize(buffer, buffer.position());
                    assert vlongSize > 0 && vlongSize <= VariableLengthEncoder.MAX_INTEGER_LENGTH
                            : vlongSize + " is out of bound for a variable integer size (should be in [1..8]";
                    int remaining = buffer.remaining();
                    if (remaining >= vlongSize) {
                        // we have all necessary bytes - just use the buffer
                        toDecode = buffer;
                        if (remaining == vlongSize) {
                            // we're going to read all the bytes: poll the buffer
                            var polled = reader.poll();
                            assert polled == buffer;
                        }
                        break; // we have everything we need, break out of the loop
                    } else {
                        // we don't have enough bytes: start accumulating them
                        vlongBuf = ByteBuffer.allocate(vlongSize);
                        vlongBuf.put(buffer);
                        assert buffer.remaining() == 0;
                        var polled = reader.poll();
                        assert polled == buffer;
                        // continue and wait for more
                    }
                } else {
                    // there wasn't enough bytes the first time around, accumulate
                    // missing bytes
                    int missing = vlongSize - vlongBuf.position();
                    int available = Math.min(missing, buffer.remaining());
                    for (int i = 0; i < available; i++) {
                        vlongBuf.put(buffer.get());
                    }
                    // if we have exhausted the buffer, poll it.
                    if (!buffer.hasRemaining()) {
                        var polled = reader.poll();
                        assert polled == buffer;
                    }
                    // if we have all bytes, we can proceed and decode the stream type
                    if (vlongBuf.position() == vlongSize) {
                        toDecode = vlongBuf;
                        toDecode.flip();
                        vlongBuf = null;
                        break;  // we have everything we need, break out of the loop
                    } // otherwise, wait for more
                }
            }
            if (toDecode == null) {
                // we reach here if there's no buffer to poll, and
                // we are waiting for more bytes
                assert buffer == null;
                return;
            }

            assert toDecode.remaining() >= vlongSize;
            long vlong = VariableLengthEncoder.decode(toDecode); // consume the bytes
            assert vlongBuf == null;

            if (longCount == 0) {
                firstLong = vlong;
                if (Http3Streams.isReserved(vlong)) {
                    // reserved stream type, 0x1f * N + 0x21
                    reservedStreamType(vlong, stream);
                    return;
                }
                if (vlong < 0 || vlong > Integer.MAX_VALUE) {
                    unknownStreamType(vlong, stream);
                    return;
                }
                streamType = (int) vlong;
                longCount = 1;
            } else {
                nextLong = vlong;
                longCount = 2;
            }
            assert longCount == 1 || streamType == Http3Streams.PUSH_STREAM_CODE;
            int code = streamType;
            switch (code) {
                case Http3Streams.CONTROL_STREAM_CODE -> {
                    controlStream("peer control stream", StreamType.CONTROL);
                }
                case Http3Streams.PUSH_STREAM_CODE -> {
                    assert nextLong == -1 || longCount == 2;
                    // pushStream is called twice: once before reading the pushId and
                    // once after reading it. This allows an implementation to
                    // emit a protocol error immediately, without waiting for the
                    // bytes of the pushId to be delivered if needed.
                    pushStream("push stream", StreamType.PUSH, nextLong);
                    if (longCount == 1) dispatch(); // read next long
                }
                case Http3Streams.QPACK_ENCODER_STREAM_CODE -> {
                    qpackEncoderStream("peer qpack encoder stream", StreamType.QPACK_ENCODER);
                }
                case Http3Streams.QPACK_DECODER_STREAM_CODE -> {
                    qpackDecoderStream("peer qpack decoder stream", StreamType.QPACK_DECODER);
                }
                default -> {
                    unknownStreamType(code, stream);
                }
            }
        } catch (IOException io) {
            if (stream.receivingState().isReset() || stream.receivingState().isTerminal()) {
                if (firstLong == -1) {
                    debug().log("stream %s %s before stream type received, cannot dispatch!",
                            stream.streamId(), stream.receivingState());
                    // RFC 9114: https://www.rfc-editor.org/rfc/rfc9114.html#section-6.2-10
                    // > A receiver MUST tolerate unidirectional streams being closed or reset
                    // > prior to the reception of the unidirectional stream header
                    abandon();
                    return;
                }
            }
            abort(Http3Error.H3_INTERNAL_ERROR, io);
        } catch (Throwable throwable) {
            // We shouldn't come here, so if we do, it's closer to an
            // internal error than a stream creation error.
            abort(Http3Error.H3_INTERNAL_ERROR, throwable);
        }
    }

    public void stop() {
        scheduler.stop();
    }

    // dispatches the peer control stream
    private void controlStream(String description, StreamType type) {
        assert type.code() == Http3Streams.CONTROL_STREAM_CODE;
        disconnect();
        debug().log("dispatching %s %s(%s)", description, type, type.code());
        onControlStreamCreated(description, stream);
    }

    // dispatches the peer encoder stream
    private void qpackEncoderStream(String description, StreamType type) {
        assert type.code() == Http3Streams.QPACK_ENCODER_STREAM_CODE;
        disconnect();
        debug().log("dispatching %s %s(%s)", description, type, type.code());
        onEncoderStreamCreated(description, stream);
    }

    // dispatches the peer decoder stream
    private void qpackDecoderStream(String description, StreamType type) {
        assert type.code() == Http3Streams.QPACK_DECODER_STREAM_CODE;
        disconnect();
        debug().log("dispatching %s %s(%s)", description, type, type.code());
        onDecoderStreamCreated(description, stream);
    }

    // dispatches a push stream initiated by the peer
    private void pushStream(String description, StreamType type, long pushId) {
        assert type.code() == Http3Streams.PUSH_STREAM_CODE;
        if (pushId >= 0) disconnect();
        debug().log("dispatching %s %s(%s, %s)", description, type, type.code(), pushId);
        onPushStreamCreated(description, stream, pushId);
    }

    // dispatches a stream whose stream type was recognized as a reserved stream type
    private void reservedStreamType(long code, QuicReceiverStream stream) {
        onReservedStreamType(code, stream);
    }

    // dispatches a stream whose stream type was not recognized
    private void unknownStreamType(long code, QuicReceiverStream stream) {
        onUnknownStreamType(code, stream);
        // if an exception is thrown above, abort will be called.
    }

    /**
     * {@return the debug logger that should be used}
     */
    protected abstract Logger debug();

    /**
     * Starts the dispatcher.
     * @apiNote
     * The dispatcher should be explicitly started after
     * creating the dispatcher.
     */
    protected void start() {
        reader.start();
    }

    /**
     * This method disconnects the temporary reader used
     * to read the stream type off the stream.
     */
    protected void disconnect() {
        disconnect(null);
    }

    protected void disconnect(Throwable failed) {
        try {
            try {
                stream.disconnectReader(reader);
            } finally {
                stop();
            }
            if (failed == null) {
                cf.complete(stream);
            } else {
                cf.completeExceptionally(failed);
            }
        } catch (Throwable throwable) {
            failed = failed == null ? throwable : failed;
            cf.completeExceptionally(failed);
        }
    }

    /**
     * This method disconnects the reader, stops the dispatch, and unless
     * the stream type could be decoded and was a {@linkplain  Http3Streams#isReserved(long)
     * reserved type}, calls {@link #onStreamAbandoned(QuicReceiverStream)}
     */
    protected void abandon() {
        Throwable failed = null;
        try {
            try {
                stream.disconnectReader(reader);
            } finally {
                stop();
            }
            cf.complete(stream);
            if (firstLong > 0 && !Http3Streams.isReserved(firstLong)) {
                onStreamAbandoned(stream);
            }
        } catch (Throwable throwable) {
            cf.completeExceptionally(failed);
        }
    }

    /**
     * Aborts the dispatch - for instance, if the stream type
     * can't be read, or isn't recognized.
     *
     * This method requests the peer to stop sending this stream,
     * and completes the {@link #dispatchCF() dispatchCF} exceptionally
     * with the provided throwable.
     *
     * @param error an HTTP/3 error code to send in the HTTP/3 STOP_SENDING frame
     * @param throwable the reason for aborting the dispatch
     */
    private void abort(Http3Error error, Throwable throwable) {
        try {
            if (Http3Error.isNoError(stream.rcvErrorCode())) {
                var debug = debug();
                if (debug.on()) debug.log("aborting dispatch: " + throwable);
            } else {
                var debug = debug();
                if (debug.on()) debug.log("aborting dispatch: " + throwable, throwable);
            }
            if (!stream.receivingState().isReset() && !stream.isStopSendingRequested()) {
                stream.requestStopSending(error.code());
            }
        } finally {
            // error getting/parsing pushId => just abandon the stream
            if (streamType == Http3Streams.PUSH_STREAM_CODE) {
                abandon();
            } else {
                disconnect(throwable);
            }
        }
    }

    /**
     * Called when a reserved stream type is read off the
     * stream.
     *
     * @implSpec
     * The default implementation of this method calls
     * {@snippet :
     *   stream.requestStopSending(Http3Error.H3_STREAM_CREATION_ERROR.code());
     *   abandon();
     * }
     *
     * @param code    the unrecognized stream type
     * @param stream  the peer initiated stream
     */
    protected void onReservedStreamType(long code, QuicReceiverStream stream) {
        debug().log("Ignoring reserved stream type %s", code);
        stream.requestStopSending(Http3Error.H3_STREAM_CREATION_ERROR.code());
        abandon();
    }

    /**
     * Called when an unrecognized stream type is read off the
     * stream.
     *
     * @implSpec
     * The default implementation of this method calls
     * {@snippet :
     *   stream.requestStopSending(Http3Error.H3_STREAM_CREATION_ERROR.code());
     *   abandon();
     * }
     *
     * @param code    the unrecognized stream type
     * @param stream  the peer initiated stream
     */
    protected void onUnknownStreamType(long code, QuicReceiverStream stream) {
        debug().log("Ignoring unknown stream type %s", code);
        stream.requestStopSending(Http3Error.H3_STREAM_CREATION_ERROR.code());
        abandon();
    }

    /**
     * Called after {@linkplain QuicReceiverStream#disconnectReader(QuicStreamReader)}
     * to abandon a peer initiated push stream.
     * @param stream a peer initiated stream which was abandoned due to having an
     *               unknown or reserved type, or which was abandoned due to being reset
     *               before being dispatched.
     * @apiNote
     * A subclass may want to override this method in order to, e.g, emit a
     * QPack Stream Cancellation instruction;
     * See https://www.rfc-editor.org/rfc/rfc9204.html#name-abandonment-of-a-stream
     */
    protected void onStreamAbandoned(QuicReceiverStream stream) {}

    /**
     * Called after {@linkplain #disconnect()} to handle the peer control stream.
     * The stream type has already been read off the stream.
     * @param description a brief description of the stream for logging purposes
     * @param stream the peer control stream
     */
    protected abstract void onControlStreamCreated(String description, QuicReceiverStream stream);

    /**
     * Called after {@linkplain #disconnect()} to handle the peer encoder stream.
     * The stream type has already been read off the stream.
     * @param description a brief description of the stream for logging purposes
     * @param stream the peer encoder stream
     */
    protected abstract void onEncoderStreamCreated(String description, QuicReceiverStream stream);

    /**
     * Called after {@linkplain #disconnect()} to handle the peer decoder stream.
     * The stream type has already been read off the stream.
     * @param description a brief description of the stream for logging purposes
     * @param stream the peer decoder stream
     */
    protected abstract void onDecoderStreamCreated(String description, QuicReceiverStream stream);

    /**
     * Called after {@linkplain #disconnect()} to handle a peer initiated push stream.
     * The stream type has already been read off the stream.
     * @param description a brief description of the stream for logging purposes
     * @param stream a peer initiated push stream
     */
    protected abstract void onPushStreamCreated(String description, QuicReceiverStream stream, long pushId);

}
