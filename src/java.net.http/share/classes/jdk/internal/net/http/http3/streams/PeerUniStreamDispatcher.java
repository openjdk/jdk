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
import java.util.concurrent.CompletableFuture;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.streams.Http3Streams.StreamType;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;

/**
 * A class that analyzes the first byte of the stream to figure
 * out where to dispatch it.
 */
public abstract class PeerUniStreamDispatcher {
    private final QuicStreamIntReader reader;
    private final QuicReceiverStream stream;
    private final CompletableFuture<QuicReceiverStream> cf = new MinimalFuture<>();

    /**
     * Creates a {@code PeerUniStreamDispatcher} for the given stream.
     * @param stream a new unidirectional stream opened by the peer
     */
    protected PeerUniStreamDispatcher(QuicReceiverStream stream) {
        this.reader = new QuicStreamIntReader(checkStream(stream), debug());
        this.stream = stream;
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

    // The dispatch function.
    private void dispatch(Long result, Throwable error) {
        if (result != null && result == Http3Streams.PUSH_STREAM_CODE) {
            reader.readInt().whenComplete(this::dispatchPushStream);
            return;
        }
        reader.stop();
        if (result != null) {
            cf.complete(stream);
            if (Http3Streams.isReserved(result)) {
                // reserved stream type, 0x1f * N + 0x21
                reservedStreamType(result, stream);
                return;
            }
            if (result < 0) {
                debug().log("stream %s EOF, cannot dispatch!",
                        stream.streamId());
                abandon();
            }
            if (result > Integer.MAX_VALUE) {
                unknownStreamType(result, stream);
                return;
            }
            int code = (int)(long)result;
            switch (code) {
                case Http3Streams.CONTROL_STREAM_CODE -> {
                    controlStream("peer control stream", StreamType.CONTROL);
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
        } else if (error instanceof IOException io) {
            if (stream.receivingState().isReset()) {
                debug().log("stream %s %s before stream type received, cannot dispatch!",
                        stream.streamId(), stream.receivingState());
                // RFC 9114: https://www.rfc-editor.org/rfc/rfc9114.html#section-6.2-10
                // > A receiver MUST tolerate unidirectional streams being closed or reset
                // > prior to the reception of the unidirectional stream header
                cf.complete(stream);
                abandon();
                return;
            }
            abort(io);
        } else {
            // We shouldn't come here, so if we do, it's closer to an
            // internal error than a stream creation error.
            abort(error);
        }
    }

    private void dispatchPushStream(Long result, Throwable error) {
        reader.stop();
        if (result != null) {
            cf.complete(stream);
            if (result < 0) {
                debug().log("stream %s EOF, cannot dispatch!",
                        stream.streamId());
                abandon();
            } else {
                pushStream("push stream", StreamType.PUSH, result);
            }
        } else if (error instanceof IOException io) {
            if (stream.receivingState().isReset()) {
                debug().log("stream %s %s before push stream ID received, cannot dispatch!",
                        stream.streamId(), stream.receivingState());
                // RFC 9114: https://www.rfc-editor.org/rfc/rfc9114.html#section-6.2-10
                // > A receiver MUST tolerate unidirectional streams being closed or reset
                // > prior to the reception of the unidirectional stream header
                cf.complete(stream);
                abandon();
                return;
            }
            abort(io);
        } else {
            // We shouldn't come here, so if we do, it's closer to an
            // internal error than a stream creation error.
            abort(error);
        }

    }

    // dispatches the peer control stream
    private void controlStream(String description, StreamType type) {
        assert type.code() == Http3Streams.CONTROL_STREAM_CODE;
        debug().log("dispatching %s %s(%s)", description, type, type.code());
        onControlStreamCreated(description, stream);
    }

    // dispatches the peer encoder stream
    private void qpackEncoderStream(String description, StreamType type) {
        assert type.code() == Http3Streams.QPACK_ENCODER_STREAM_CODE;
        debug().log("dispatching %s %s(%s)", description, type, type.code());
        onEncoderStreamCreated(description, stream);
    }

    // dispatches the peer decoder stream
    private void qpackDecoderStream(String description, StreamType type) {
        assert type.code() == Http3Streams.QPACK_DECODER_STREAM_CODE;
        debug().log("dispatching %s %s(%s)", description, type, type.code());
        onDecoderStreamCreated(description, stream);
    }

    // dispatches a push stream initiated by the peer
    private void pushStream(String description, StreamType type, long pushId) {
        assert type.code() == Http3Streams.PUSH_STREAM_CODE;
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
        reader.readInt().whenComplete(this::dispatch);
    }

    /**
     * This method disconnects the reader, stops the dispatch, and unless
     * the stream type could be decoded and was a {@linkplain  Http3Streams#isReserved(long)
     * reserved type}, calls {@link #onStreamAbandoned(QuicReceiverStream)}
     */
    protected void abandon() {
        onStreamAbandoned(stream);
    }

    /**
     * Aborts the dispatch - for instance, if the stream type
     * can't be read, or isn't recognized.
     * <p>
     * This method requests the peer to stop sending this stream,
     * and completes the {@link #dispatchCF() dispatchCF} exceptionally
     * with the provided throwable.
     *
     * @param throwable the reason for aborting the dispatch
     */
    private void abort(Throwable throwable) {
        try {
            var debug = debug();
            if (debug.on()) debug.log("aborting dispatch: " + throwable, throwable);
            if (!stream.receivingState().isReset() && !stream.isStopSendingRequested()) {
                stream.requestStopSending(Http3Error.H3_INTERNAL_ERROR.code());
            }
        } finally {
            abandon();
            cf.completeExceptionally(throwable);
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
     * }
     *
     * @param code    the unrecognized stream type
     * @param stream  the peer initiated stream
     */
    protected void onReservedStreamType(long code, QuicReceiverStream stream) {
        debug().log("Ignoring reserved stream type %s", code);
        stream.requestStopSending(Http3Error.H3_STREAM_CREATION_ERROR.code());
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
     * Called after disconnecting to abandon a peer initiated stream.
     * @param stream a peer initiated stream which was abandoned due to having an
     *               unknown type, or which was abandoned due to being reset
     *               before being dispatched.
     * @apiNote
     * A subclass may want to override this method in order to, e.g, emit a
     * QPack Stream Cancellation instruction;
     * See https://www.rfc-editor.org/rfc/rfc9204.html#name-abandonment-of-a-stream
     */
    protected void onStreamAbandoned(QuicReceiverStream stream) {}

    /**
     * Called after disconnecting to handle the peer control stream.
     * The stream type has already been read off the stream.
     * @param description a brief description of the stream for logging purposes
     * @param stream the peer control stream
     */
    protected abstract void onControlStreamCreated(String description, QuicReceiverStream stream);

    /**
     * Called after disconnecting to handle the peer encoder stream.
     * The stream type has already been read off the stream.
     * @param description a brief description of the stream for logging purposes
     * @param stream the peer encoder stream
     */
    protected abstract void onEncoderStreamCreated(String description, QuicReceiverStream stream);

    /**
     * Called after disconnecting to handle the peer decoder stream.
     * The stream type has already been read off the stream.
     * @param description a brief description of the stream for logging purposes
     * @param stream the peer decoder stream
     */
    protected abstract void onDecoderStreamCreated(String description, QuicReceiverStream stream);

    /**
     * Called after disconnecting to handle a peer initiated push stream.
     * The stream type has already been read off the stream.
     * @param description a brief description of the stream for logging purposes
     * @param stream a peer initiated push stream
     */
    protected abstract void onPushStreamCreated(String description, QuicReceiverStream stream, long pushId);

}
