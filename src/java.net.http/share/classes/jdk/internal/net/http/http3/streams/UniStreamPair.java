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
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import jdk.internal.net.http.Http3Connection;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.streams.Http3Streams.StreamType;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.QuicConnection;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import static jdk.internal.net.http.http3.Http3Error.H3_STREAM_CREATION_ERROR;
import static jdk.internal.net.http.quic.TerminationCause.appLayerClose;

/**
 * A class that models a pair of HTTP/3 unidirectional streams.
 * This class implements a read loop that calls a {@link
 * #UniStreamPair(StreamType, QuicConnection, Consumer, Runnable, StreamErrorHandler, Logger)
 * receiver} every time a {@code ByteBuffer} is read from
 * the receiver part.
 * The {@linkplain #futureSenderStreamWriter() sender stream writer},
 * when available, can be used to write to the sender part.
 * The {@link #UniStreamPair(StreamType, QuicConnection, Consumer, Runnable,StreamErrorHandler, Logger)
 * writerLoop} is invoked whenever the writer part becomes unblocked, and
 * writing can be resumed.
 * <p>
 * @apiNote
 * The creator of the stream pair (typically {@link Http3Connection}) is expected
 * to complete the {@link #futureReceiverStream()} completable future when the remote
 * part of the stream pair is created by the remote peer. This class will not
 * listen directly for creation of new remote streams.
 * <p>
 * The {@link QueuingStreamPair} class is a subclass of this class which
 * implements a writer loop over an unbounded queue of {@code ByteBuffer}, and
 * can be used when unlimited buffering of data for writing is not an issue.
 */
public class UniStreamPair {

    // The sequential scheduler for the local control stream (LCS) writer loop
    private final SequentialScheduler localWriteScheduler;
    // The QuicStreamWriter for the local control stream
    private volatile QuicStreamWriter localWriter;
    private final CompletableFuture<QuicStreamWriter> streamWriterCF;
    // A completable future that will be completed when the local sender
    // stream is opened and the stream type has been queued to the
    // writer queue.
    private volatile CompletableFuture<QuicSenderStream> localSenderStreamCF;

    // The sequential scheduler for the peer receiver stream (PRS) reader loop
    final SequentialScheduler peerReadScheduler =
            SequentialScheduler.lockingScheduler(this::peerReaderLoop);
    // The QuicStreamReader for the peer control stream
    volatile QuicStreamReader peerReader;
    private final CompletableFuture<QuicStreamReader> streamReaderCF;
    // A completable future that will be completed when the peer opens
    // the receiver part of the stream pair
    private final CompletableFuture<QuicReceiverStream> peerReceiverStreamCF = new MinimalFuture<>();
    private final ReentrantLock lock = new ReentrantLock();


    private final StreamType localStreamType;  // The HTTP/3 stream type of the sender part
    private final StreamType remoteStreamType; // The HTTP/3 stream type of the receiver part
    private final QuicConnection quicConnection; // the underlying quic connection
    private final Consumer<ByteBuffer> receiver; // called when a ByteBuffer is received
    final StreamErrorHandler errorHandler; // used by QueuingStreamPair
    final Logger debug;                        // the debug logger

    /**
     * Creates a new {@code UniStreamPair} for the given HTTP/3 {@code streamType}.
     * Valid values for {@code streamType} are {@link StreamType#CONTROL},
     * {@link StreamType#QPACK_ENCODER}, and {@link StreamType#QPACK_DECODER}.
     * <p>
     * This class implements a read loop that will call the given {@code receiver}
     * whenever a {@code ByteBuffer} is received.
     * <p>
     * Writing to the sender part can be done by interacting directly with
     * the writer. If the writer is blocked due to flow control, and becomes
     * unblocked again, the {@code writeLoop} is invoked.
     * The {@link QueuingStreamPair} subclass provides a convenient implementation
     * of a {@code writeLoop} based on an unbounded queue of {@code ByteBuffer}.
     *
     * @param streamType      the HTTP/3 stream type
     * @param quicConnection  the underlying Quic connection
     * @param receiver        the receiver callback
     * @param writerLoop      the writer loop
     * @param errorHandler    the error handler invoked in case of read errors
     * @param logger          the debug logger
     */
    public UniStreamPair(StreamType streamType,
                         QuicConnection quicConnection,
                         Consumer<ByteBuffer> receiver,
                         Runnable writerLoop,
                         StreamErrorHandler errorHandler,
                         Logger logger) {
        this(local(streamType), remote(streamType),
                Objects.requireNonNull(quicConnection),
                Objects.requireNonNull(receiver),
                Optional.of(writerLoop),
                Objects.requireNonNull(errorHandler),
                Objects.requireNonNull(logger));
    }

    /**
     * A constructor used by the {@link QueuingStreamPair} subclass
     * @param streamType      the HTTP/3 stream type
     * @param quicConnection  the underlying Quic connection
     * @param receiver        the receiver callback
     * @param errorHandler    the error handler invoked in case
     *                        of read or write errors
     * @param logger
     */
    UniStreamPair(StreamType streamType,
                  QuicConnection quicConnection,
                  Consumer<ByteBuffer> receiver,
                  StreamErrorHandler errorHandler,
                  Logger logger) {
        this(local(streamType), remote(streamType),
                Objects.requireNonNull(quicConnection),
                Objects.requireNonNull(receiver),
                Optional.empty(),
                errorHandler,
                Objects.requireNonNull(logger));
    }

    // all constructors delegate here
    private UniStreamPair(StreamType localStreamType,
                          StreamType remoteStreamType,
                          QuicConnection quicConnection,
                          Consumer<ByteBuffer> receiver,
                          Optional<Runnable> writerLoop,
                          StreamErrorHandler errorHandler,
                          Logger logger) {
        assert this.getClass() != UniStreamPair.class
                || writerLoop.isPresent();
        this.debug            = logger;
        this.localStreamType  = localStreamType;
        this.remoteStreamType = remoteStreamType;
        this.quicConnection   = quicConnection;
        this.receiver         = receiver;
        this.errorHandler     = errorHandler;
        var localWriterLoop = writerLoop.orElse(this::localWriterLoop);
        this.localWriteScheduler =
                SequentialScheduler.lockingScheduler(localWriterLoop);
        this.streamWriterCF = startSending();
        this.streamReaderCF = startReceiving();
    }

    private static StreamType local(StreamType localStreamType) {
        return switch (localStreamType) {
            case CONTROL -> localStreamType;
            case QPACK_ENCODER -> localStreamType;
            case QPACK_DECODER -> localStreamType;
            default -> throw new IllegalArgumentException(localStreamType
                    + " cannot be part of a stream pair");
        };
    }

    private static StreamType remote(StreamType localStreamType) {
        return switch (localStreamType) {
            case CONTROL -> localStreamType;
            case QPACK_ENCODER -> StreamType.QPACK_DECODER;
            case QPACK_DECODER -> StreamType.QPACK_ENCODER;
            default -> throw new IllegalArgumentException(localStreamType
                    + " cannot be part of a stream pair");
        };
    }

    /**
     * {@return the HTTP/3 stream type of the sender part of the stream pair}
     */
    public final StreamType localStreamType() {
        return localStreamType;
    }

    /**
     * {@return the HTTP/3 stream type of the receiver part of the stream pair}
     */
    public final StreamType remoteStreamType() {
        return remoteStreamType;
    }

    /**
     * {@return a completable future that will be completed with a writer connected
     *  to the sender part of this stream pair after the local HTTP/3 stream type
     *  has been queued for writing on the writing queue}
     */
    public final CompletableFuture<QuicStreamWriter> futureSenderStreamWriter() {
        return streamWriterCF;
    }

    /**
     * {@return a completable future that will be completed with a reader connected
     *  to the receiver part of this stream pair after the remote HTTP/3 stream
     *  type has been read off the remote initiated stream}
     */
    public final CompletableFuture<QuicStreamReader> futureReceiverStreamReader() {
        return streamReaderCF;
    }

    /**
     * {@return a completable future that will be completed with the sender part
     *  of this stream pair after the local HTTP/3 stream type
     *  has been queued for writing on the writing queue}
     */
    public CompletableFuture<QuicSenderStream> futureSenderStream() {
        return localSenderStream();
    }

    /**
     * {@return a completable future that will be completed with the receiver part
     *  of this stream pair after the remote HTTP/3 stream type has been read off
     *  the remote initiated stream}
     */
    public CompletableFuture<QuicReceiverStream> futureReceiverStream() {
        return peerReceiverStreamCF;
    }

    /**
     * {@return the scheduler for the local writer loop}
     */
    public SequentialScheduler localWriteScheduler() {
        return localWriteScheduler;
    }

    /**
     * {@return the writer connected to the sender part of this stream or
     * {@code null} if no writer is connected yet}
     */
    public QuicStreamWriter localWriter() {return localWriter; }

    /**
     * Stops schedulers. Can be called when the connection is
     * closed to stop the reading and writing loops.
     */
    public void stopSchedulers() {
        peerReadScheduler.stop();
        localWriteScheduler.stop();
    }

    //  Hooks for QueuingStreamPair
    // ============================

    /**
     * This method is overridden by {@link QueuingStreamPair} to implement
     * a writer loop for this stream. It is only called when the concrete
     * subclass is {@link QueuingStreamPair}.
     */
    void localWriterLoop() {
        if (debug.on()) debug.log("writing loop not implemented");
    }


    /**
     * Used by subclasses to redirect queuing of data to the
     * subclass queue.
     * @param writer the downstream writer
     * @return a writer that can be safely used.
     */
    QuicStreamWriter wrap(QuicStreamWriter writer) {
        return writer;
    }

    // Undidirectional Stream Pair Implementation
    // ==========================================


    /**
     * This method is called to process bytes received on the peer
     * control stream.
     * @param buffer the bytes received
     */
    private void processPeerControlBytes(ByteBuffer buffer) {
        receiver.accept(buffer);
    }

    /**
     * Creates the local sender stream and queues the stream
     * type code in its writer queue.
     * @return a completable future that will be completed with the
     * local sender stream
     */
    private CompletableFuture<QuicSenderStream> localSenderStream() {
        CompletableFuture<QuicSenderStream> lcs = localSenderStreamCF;
        if (lcs != null) return lcs;
        StreamType type = localStreamType();
        lock.lock();
        try {
            if ((lcs = localSenderStreamCF) != null) return lcs;
            if (debug.on()) {
                debug.log("Opening local stream: %s(%s)",
                        type, type.code());
            }
            // TODO: review this duration
            final Duration streamLimitIncreaseDuration = Duration.ZERO;
            localSenderStreamCF = lcs = quicConnection
                    .openNewLocalUniStream(streamLimitIncreaseDuration)
                    .thenApply( s -> openLocalStream(s, type.code()));
            // TODO: use thenApplyAsync with the executor instead
        } finally {
            lock.unlock();
        }
        return lcs;
    }


    /**
     * Schedules sending of client settings.
     * @return a completable future that will be completed with the
     * {@link QuicStreamWriter} allowing to write to the local control
     * stream
     */
    private CompletableFuture<QuicStreamWriter> startSending() {
        return localSenderStream().thenApply((stream) -> {
            if (debug.on()) {
                debug.log("stream %s is ready for sending", stream.streamId());
            }
            var controlWriter = stream.connectWriter(localWriteScheduler);
            localWriter = controlWriter;
            localWriteScheduler.runOrSchedule();
            return wrap(controlWriter);
        });
    }

    /**
     * Schedules the receiving of server settings
     * @return a completable future that will be completed with the
     * {@link QuicStreamReader} allowing to read from the remote control
     *  stream.
     */
    private CompletableFuture<QuicStreamReader> startReceiving() {
        if (debug.on()) {
            debug.log("prepare to receive");
        }
        return peerReceiverStreamCF.thenApply(this::connectReceiverStream);
    }

    /**
     * Connects the peer control stream reader and
     * schedules the receiving of the peer settings from the given
     * {@code peerControlStream}.
     * @param peerControlStream the peer control stream
     * @return the peer control stream reader
     */
    private QuicStreamReader connectReceiverStream(QuicReceiverStream peerControlStream) {
        var reader = peerControlStream.connectReader(peerReadScheduler);
        var streamType = remoteStreamType();
        if (debug.on()) {
            debug.log("peer %s stream reader connected (stream %s)",
                    streamType, peerControlStream.streamId());
        }
        peerReader = reader;
        reader.start();
        return reader;
    }

    // The peer receiver stream reader loop
    private void peerReaderLoop() {
        var reader = peerReader;
        if (reader == null) return;
        ByteBuffer buffer;
        long bytes = 0;
        var streamType = remoteStreamType();
        try {
            // TODO: Revisit: if the underlying quic connection is closed
            //   by the peer, we might get a ClosedChannelException from poll()
            //   here before the upper layer connection (HTTP/3 connection) is
            //   marked closed.
            if (debug.on()) {
                debug.log("start reading from peer %s stream", streamType);
            }
            while ((buffer = reader.poll()) != null) {
                final int remaining = buffer.remaining();
                if (remaining == 0 && buffer != QuicStreamReader.EOF) {
                    continue; // not yet EOF, so poll more
                }
                bytes += remaining;
                processPeerControlBytes(buffer);
                if (buffer == QuicStreamReader.EOF) {
                    // a EOF was processed, don't poll anymore
                    break;
                }
            }
            if (debug.on()) {
                debug.log("stop reading peer %s stream after %s bytes",
                        streamType, bytes);
            }
        } catch (IOException | RuntimeException | Error throwable) {
            if (debug.on()) {
                debug.log("Reading peer %s stream failed: %s", streamType, throwable);
            }
            // call the error handler and pass it the stream on which the error happened
            errorHandler.onError(reader.stream(), this, throwable);
        }
    }

    /**
     * Queues the given HTTP/3 stream type code on the given local unidirectional
     * stream writer queue.
     * @param stream a new local unidirectional stream
     * @param code   the code to queue up on the stream writer queue
     * @return the given {@code stream}
     */
    private QuicSenderStream openLocalStream(QuicSenderStream stream, int code)  {
        var streamType = localStreamType();
        if (debug.on()) {
            debug.log("Opening local stream: %s %s(code=%s)",
                    stream.streamId(), streamType, code);
        }
        var scheduler = SequentialScheduler.lockingScheduler(() -> {
        });
        var writer = stream.connectWriter(scheduler);
        try {
            if (debug.on()) {
                debug.log("Writing local stream type: stream %s %s(code=%s)",
                        stream.streamId(), streamType, code);
            }
            var buffer = ByteBuffer.allocate(VariableLengthEncoder.getEncodedSize(code));
            VariableLengthEncoder.encode(buffer, code);
            buffer.flip();
            writer.queueForWriting(buffer);
            scheduler.stop();
            stream.disconnectWriter(writer);
        } catch (Throwable t) {
            if (debug.on()) {
                debug.log("failed to create stream %s %s(code=%s): %s",
                        stream.streamId(), streamType, code, t);
            }
            try {
                switch (streamType) {
                    case CONTROL, QPACK_ENCODER, QPACK_DECODER -> {
                        final String logMsg = "stream %s %s(code=%s)"
                                .formatted(stream.streamId(), streamType, code);
                        // TODO: revisit - we should probably invoke a method
                        //       on the HttpQuicConnection or H3Connection instead of
                        //       dealing directly with QuicConnection here.
                        final TerminationCause terminationCause =
                                appLayerClose(H3_STREAM_CREATION_ERROR.code()).loggedAs(logMsg);
                        quicConnection.connectionTerminator().terminate(terminationCause);
                    }
                    default -> writer.reset(H3_STREAM_CREATION_ERROR.code());
                }
            } catch (Throwable suppressed) {
                if (debug.on()) {
                    debug.log("couldn't close connection or reset stream: " + suppressed);
                }
                Utils.addSuppressed(t, suppressed);
                throw new CompletionException(t);
            }
        }
        return stream;
    }

    public static interface StreamErrorHandler {

        /**
         * Will be invoked when there is an error on a {@code QuicStream} handled by
         * the {@code UniStreamPair}
         *
         * @param stream the stream on which the error occurred
         * @param uniStreamPair the UniStreamPair to which the stream belongs
         * @param error the error that occurred
         */
        void onError(QuicStream stream, UniStreamPair uniStreamPair, Throwable error);
    }
}
