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

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.QuicStreamLimitException;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.frames.StreamsBlockedFrame;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTLSEngine.KeySpace;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.http.quic.frames.MaxStreamDataFrame;
import jdk.internal.net.http.quic.frames.MaxStreamsFrame;
import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.http.quic.frames.ResetStreamFrame;
import jdk.internal.net.http.quic.frames.StopSendingFrame;
import jdk.internal.net.http.quic.frames.StreamDataBlockedFrame;
import jdk.internal.net.http.quic.frames.StreamFrame;
import jdk.internal.net.http.quic.packets.QuicPacketEncoder;
import jdk.internal.net.http.quic.streams.QuicReceiverStream.ReceivingStreamState;
import jdk.internal.net.http.quic.streams.QuicStream.StreamMode;
import jdk.internal.net.http.quic.streams.QuicStream.StreamState;
import jdk.internal.net.http.quic.QuicTransportParameters;
import jdk.internal.net.http.quic.QuicTransportParameters.ParameterId;
import jdk.internal.net.quic.QuicTransportErrors;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static jdk.internal.net.http.quic.streams.QuicStreams.*;

/**
 * A helper class to help manage Quic streams in a Quic connection
 */
public final class QuicConnectionStreams {

    // the (sliding) window size of MAX_STREAMS limit
    private static final long MAX_BIDI_STREAMS_WINDOW_SIZE = QuicConnectionImpl.DEFAULT_MAX_BIDI_STREAMS;
    private static final long MAX_UNI_STREAMS_WINDOW_SIZE = QuicConnectionImpl.DEFAULT_MAX_UNI_STREAMS;

    // These atomic long ids record the expected next stream ID that
    // should be allocated for the next stream of a given type.
    // The type of a stream is a number in [0..3], and is used
    // as an index in this list.
    private final List<AtomicLong> nextStreamID = List.of(
            new AtomicLong(), // 0: client initiated bidi
            new AtomicLong(SRV_MASK), // 1: server initiated bidi
            new AtomicLong(UNI_MASK),  // 2: client initiated uni
            new AtomicLong(UNI_MASK | SRV_MASK)); // 3: server initiated uni

    // the max uni streams that the current endpoint is allowed to initiate against the peer
    private final StreamCreationPermit localUniMaxStreamLimit = new StreamCreationPermit(0);
    // the max bidi streams that the current endpoint is allowed to initiate against the peer
    private final StreamCreationPermit localBidiMaxStreamLimit = new StreamCreationPermit(0);
    // the max uni streams that the remote peer is allowed to initiate against the current endpoint
    private final AtomicLong remoteUniMaxStreamLimit = new AtomicLong(0);
    // the max bidi streams that the remote peer is allowed to initiate against the current endpoint
    private final AtomicLong remoteBidiMaxStreamLimit = new AtomicLong(0);

    private final StreamsContainer streams = new StreamsContainer();

    // A collection of senders which have available data ready to send, (or which possibly
    // are blocked and need to send STREAM_DATA_BLOCKED).
    // A stream stays in the queue until it is blocked or until it
    // has no more data available to send: when a stream has no more data available for sending it is not
    // put back in the queue. It will be put in the queue again when selectForSending is called.
    private final ReadyStreamCollection sendersReady;

    // A map that contains streams for which sending a RESET_STREAM frame was requested
    // and their corresponding error codes.
    // Once the frame has been sent (or has been scheduled to be sent) the stream removed from the map.
    private final ConcurrentMap<QuicSenderStream, Long> sendersReset = new ConcurrentHashMap<>();

    // A map that contains streams for which sending a MAX_STREAM_DATA frame was requested.
    // Once the frame has been sent (or has been scheduled to be sent) the stream removed from the map.
    private final ConcurrentMap<QuicReceiverStream, QuicFrame> receiversSend = new ConcurrentHashMap<>();

    // A queue of remote initiated streams that have not been acquired yet.
    // see pollNewRemoteStreams and addRemoteStreamListener
    private final ConcurrentLinkedQueue<QuicReceiverStream> newRemoteStreams = new ConcurrentLinkedQueue<>();
    // A set of listeners listening to new streams created by the peer
    private final Set<Predicate<? super QuicReceiverStream>> streamListeners = ConcurrentHashMap.newKeySet();
    // A lock to ensure consistency between invocation of streamListeners and
    // the content of the newRemoteStreams queue.
    private final Lock newRemoteStreamsLock = new ReentrantLock();

    // The connection to which the streams managed by this
    // instance of QuicConnectionStreams belong to.
    private final QuicConnectionImpl connection;

    // will hold the highest limit from a STREAMS_BLOCKED frame that was sent by a peer for uni
    // streams. this indicates the peer isn't able to create any more uni streams, past this limit
    private final AtomicLong peerUniStreamsBlocked = new AtomicLong(-1);
    // will hold the highest limit from a STREAMS_BLOCKED frame that was sent by a peer for bidi
    // streams. this indicates the peer isn't able to create any more bidi streams, past this limit
    private final AtomicLong peerBidiStreamsBlocked = new AtomicLong(-1);
    // will hold the highest limit at which the local endpoint couldn't create a uni stream
    // and a STREAMS_BLOCKED was required to be sent. -1 indicates the local endpoint hasn't yet
    // been blocked for stream creation
    private final AtomicLong uniStreamsBlocked = new AtomicLong(-1);
    // will hold the highest limit at which the local endpoint couldn't create a bidi stream
    // and a STREAMS_BLOCKED was required to be sent. -1 indicates the local endpoint hasn't yet
    // been blocked for stream creation
    private final AtomicLong bidiStreamsBlocked = new AtomicLong(-1);
    // will hold the limit with which the local endpoint last sent a STREAMS_BLOCKED frame to the
    // peer for uni streams. -1 indicates no STREAMS_BLOCKED frame has been sent yet. A new
    // STREAMS_BLOCKED will be sent only if "uniStreamsBlocked" exceeds this
    // "lastUniStreamsBlockedSent"
    private final AtomicLong lastUniStreamsBlockedSent = new AtomicLong(-1);
    // will hold the limit with which the local endpoint last sent a STREAMS_BLOCKED frame to the
    // peer for bidi streams. -1 indicates no STREAMS_BLOCKED frame has been sent yet. A new
    // STREAMS_BLOCKED will be sent only if "bidiStreamsBlocked" exceeds this
    // "lastBidiStreamsBlockedSent"
    private final AtomicLong lastBidiStreamsBlockedSent = new AtomicLong(-1);
    // streams that have been blocked and aren't able to send data to the peer,
    // due to reaching flow control limit imposed on those streams by the peer.
    private final Set<Long> flowControlBlockedStreams = Collections.synchronizedSet(new HashSet<>());

    private final Logger debug;

    // A QuicConnectionStream instance can be tied to a client connection
    // or a server connection.
    // If the connection is a client connection, then localFlag=0x00,
    //   localBidi=0x00, remoteBidi=0x01, localUni=0x02, remoteUni=0x03
    // If the connection is a server connection, then localFlag=0x01,
    //   localBidi=0x01, remoteBidi=0x00, localUni=0x03, remoteUni=0x02
    private final int localFlag, localBidi, remoteBidi, localUni, remoteUni;

    /**
     * Creates a new instance of {@code QuicConnectionStreams} for the
     * given connection. There is a 1-1 relationship between a
     * {@code QuicConnectionImpl} instance and a {@code QuicConnectionStreams}
     * instance.
     * @param connection the connection to which the streams managed by this
     *                   instance of {@code QuicConnectionStreams} belong.
     */
    public QuicConnectionStreams(QuicConnectionImpl connection, Logger debug) {
        this.connection = connection;
        this.debug = Objects.requireNonNull(debug);
        // implicit null check for connection
        boolean isClient = connection.isClientConnection();
        localFlag  = isClient ? 0 : SRV_MASK;
        localBidi  = isClient ? 0 : SRV_MASK;
        remoteBidi = isClient ? SRV_MASK : 0;
        localUni   = isClient ? UNI_MASK : UNI_MASK | SRV_MASK;
        remoteUni  = isClient ? UNI_MASK | SRV_MASK : UNI_MASK;
        sendersReady = isClient ?
                new ReadyStreamQueue() : // faster stream opening
                new ReadyStreamSortedQueue(); // faster stream closing
    }

    /**
     * {@return the next unallocated stream ID that would be expected
     * for a stream of the given type}
     * This method expects {@code streamType} to be a number in [0..3] but
     * does not check it. An assert may be fired if an invalid type is passed.
     * @param streamType The stream type, a number in [0..3]
     */
    public long peekNextStreamId(int streamType) {
        assert streamType >= 0 && streamType < 4;
        var id = nextStreamID.get(streamType & 0x03);
        return id.get();
    }

    /**
     * Creates a new locally initiated unidirectional stream.
     * <p>
     * If the stream cannot be created due to stream creation limit being reached, then this method
     * will return a {@code CompletableFuture} which will complete either when the {@code timeout}
     * has reached or the stream limit has been increased and the stream creation was successful.
     * If the stream creation doesn't complete within the specified timeout then the returned
     * {@code CompletableFuture} will complete exceptionally with a {@link QuicStreamLimitException}
     *
     * @param timeout the maximum duration to wait to acquire a permit for stream creation
     * @return a CompletableFuture whose result on successful completion will return the newly
     *        created {@code QuicSenderStream}
     */
    public CompletableFuture<QuicSenderStream> createNewLocalUniStream(final Duration timeout) {
        @SuppressWarnings("unchecked")
        final var streamCF = (CompletableFuture<QuicSenderStream>) createNewLocalStream(localUni,
                StreamMode.WRITE_ONLY, timeout);
        return streamCF;
    }

    /**
     * Creates a new locally initiated bidirectional stream.
     * <p>
     * If the stream cannot be created due to stream creation limit being reached, then this method
     * will return a {@code CompletableFuture} which will complete either when the {@code timeout}
     * has reached or the stream limit has been increased and the stream creation was successful.
     * If the stream creation doesn't complete within the specified timeout then the returned
     * {@code CompletableFuture} will complete exceptionally with a {@link QuicStreamLimitException}
     *
     * @param timeout the maximum duration to wait to acquire a permit for stream creation
     * @return a CompletableFuture whose result on successful completion will return the newly
     *         created {@code QuicBidiStream}
     */
    public CompletableFuture<QuicBidiStream> createNewLocalBidiStream(final Duration timeout) {
        @SuppressWarnings("unchecked")
        final var streamCF = (CompletableFuture<QuicBidiStream>) createNewLocalStream(localBidi,
                StreamMode.READ_WRITE, timeout);
        return streamCF;
    }

    private void register(long streamId, AbstractQuicStream stream) {
        var previous = streams.put(streamId, stream);
        assert previous == null : "stream " + streamId + " is already registered!";
        QuicTransportParameters peerParameters = connection.peerTransportParameters();
        if (peerParameters != null) {
            if (debug.on()) {
                debug.log("setting initial peer parameters on stream " + streamId);
            }
            newInitialPeerParameters(stream, peerParameters);
        }
        QuicTransportParameters localParameters = connection.localTransportParameters();
        if (localParameters != null) {
            if (debug.on()) {
                debug.log("setting initial local parameters on stream " + streamId);
            }
            newInitialLocalParameters(stream, localParameters);
        }
        if (stream instanceof QuicReceiverStream receiver && stream.isRemoteInitiated()) {
            if (debug.on()) {
                debug.log("accepting remote stream " + streamId);
            }
            newRemoteStreams.add(receiver);
            acceptRemoteStreams();
        }
        if (debug.on()) {
            debug.log("new stream %s %s registered", streamId, stream.mode());
        }
    }

    private void acceptRemoteStreams() {
        newRemoteStreamsLock.lock();
        try {
            for (var listener : streamListeners) {
                var iterator = newRemoteStreams.iterator();
                while (iterator.hasNext()) {
                    var stream = iterator.next();
                    if (debug.on()) {
                        debug.log("invoking remote stream listener for stream %s",
                                stream.streamId());
                    }
                    if (listener.test(stream)) iterator.remove();
                }
            }
        } finally {
            newRemoteStreamsLock.unlock();
        }
    }

    private CompletableFuture<? extends QuicStream> createNewLocalStream(
            final int localType, final StreamMode mode, final Duration timeout) {
        assert localType >= 0 && localType < 4 : "bad local stream type " + localType;
        assert (localType & SRV_MASK) == localFlag : "bad local stream type " + localType;
        assert (localType & UNI_MASK) == 0 || mode == StreamMode.WRITE_ONLY
                : "bad combination of local stream type (%s) and mode %s"
                .formatted(localType, mode);
        assert (localType & UNI_MASK) == UNI_MASK || mode == StreamMode.READ_WRITE
                : "bad combination of local stream type (%s) and mode %s"
                .formatted(localType, mode);
        final boolean bidi = isBidirectional(localType);
        final StreamCreationPermit permit = bidi ? this.localBidiMaxStreamLimit
                : this.localUniMaxStreamLimit;
        final CompletableFuture<Boolean> permitAcquisitionCF;
        final long currentLimit = permit.currentLimit();
        final boolean acquired = permit.tryAcquire();
        if (acquired) {
            permitAcquisitionCF = MinimalFuture.completedFuture(true);
        } else {
            // stream limit reached, request sending a STREAMS_BLOCKED frame
            announceStreamsBlocked(bidi, currentLimit);
            if (timeout.isPositive()) {
                final Executor executor = this.connection.quicInstance().executor();
                if (debug.on()) {
                    debug.log("stream creation limit = " + permit.currentLimit()
                            + " reached; waiting for it to increase, timeout=" + timeout);
                }
                permitAcquisitionCF = permit.tryAcquire(timeout.toNanos(), NANOSECONDS, executor);
            } else {
                permitAcquisitionCF = MinimalFuture.completedFuture(false);
            }
        }
        final CompletableFuture<? extends AbstractQuicStream> streamCF =
                permitAcquisitionCF.thenCompose((acq) -> {
                    if (!acq) {
                        final String msg = "Stream limit = " + permit.currentLimit()
                                + " reached for locally initiated "
                                + (bidi ? "bidi" : "uni") + " streams";
                        return MinimalFuture.failedFuture(new QuicStreamLimitException(msg));
                    }
                    // stream limit hasn't been reached, we are allowed to create new one
                    final long streamId = nextStreamID.get(localType).getAndAdd(4);
                    final AbstractQuicStream stream = QuicStreams.createStream(connection, streamId);
                    assert stream.mode() == mode;
                    assert stream.type() == localType;
                    if (debug.on()) {
                        var strtype = (localType & UNI_MASK) == UNI_MASK ? "uni" : "bidi";
                        debug.log("created new local %s stream type:%s, mode:%s, id:%s",
                                strtype, localType, mode, streamId);
                    }
                    register(streamId, stream);
                    return MinimalFuture.completedFuture(stream);
                });
        return streamCF;
    }

    /**
     * Runs the APPLICATION space packet transmitter, if necessary,
     * to potentially trigger sending a STREAMS_BLOCKED frame to the peer
     * @param bidi true if the local endpoint is blocked for bidi streams, false for uni streams
     * @param blockedOnLimit the stream creation limit due to which the local endpoint is
     *                       currently blocked
     */
    private void announceStreamsBlocked(final boolean bidi, final long blockedOnLimit) {
        boolean runTransmitter = false;
        if (bidi) {
            long prevBlockedLimit = this.bidiStreamsBlocked.get();
            while (blockedOnLimit > prevBlockedLimit) {
                if (this.bidiStreamsBlocked.compareAndSet(prevBlockedLimit, blockedOnLimit)) {
                    runTransmitter = true;
                    break;
                }
                prevBlockedLimit = this.bidiStreamsBlocked.get();
            }
        } else {
            long prevBlockedLimit = this.uniStreamsBlocked.get();
            while (blockedOnLimit > prevBlockedLimit) {
                if (this.uniStreamsBlocked.compareAndSet(prevBlockedLimit, blockedOnLimit)) {
                    runTransmitter = true;
                    break;
                }
                prevBlockedLimit = this.uniStreamsBlocked.get();
            }
        }
        if (runTransmitter) {
            if (debug.on()) {
                debug.log("requesting packet transmission to send " + (bidi ? "bidi" : "uni")
                        + " STREAMS_BLOCKED with limit " + blockedOnLimit);
            }
            this.connection.runAppPacketSpaceTransmitter();
        }
    }

    /**
     * Runs the APPLICATION space packet transmitter, if necessary, to potentially trigger
     * sending a MAX_STREAMS frame to the peer, upon receiving the STREAMS_BLOCKED {@code frame}
     * from that peer
     * @param frame the STREAMS_BLOCKED frame that was received from the peer
     */
    public void peerStreamsBlocked(final StreamsBlockedFrame frame) {
        final boolean bidi = frame.isBidi();
        final long blockedOnLimit = frame.maxStreams();
        boolean runTransmitter = false;
        if (bidi) {
            long prevBlockedLimit = this.peerBidiStreamsBlocked.get();
            while (blockedOnLimit > prevBlockedLimit) {
                if (this.peerBidiStreamsBlocked.compareAndSet(prevBlockedLimit, blockedOnLimit)) {
                    runTransmitter = true;
                    break;
                }
                prevBlockedLimit = this.peerBidiStreamsBlocked.get();
            }
        } else {
            long prevBlockedLimit = this.peerUniStreamsBlocked.get();
            while (blockedOnLimit > prevBlockedLimit) {
                if (this.peerUniStreamsBlocked.compareAndSet(prevBlockedLimit, blockedOnLimit)) {
                    runTransmitter = true;
                    break;
                }
                prevBlockedLimit = this.peerUniStreamsBlocked.get();
            }
        }
        if (runTransmitter) {
            if (debug.on()) {
                debug.log("requesting packet transmission in response to receiving "
                        + (bidi ? "bidi" : "uni") + " STREAMS_BLOCKED from peer," +
                        " blocked with limit " + blockedOnLimit);
            }
            this.connection.runAppPacketSpaceTransmitter();
        }
    }

    /**
     * Gets or opens a remotely initiated stream with the given stream ID.
     * Creates all streams with lower IDs if needed.
     * @param streamId the stream ID
     * @param frameType type of the frame received, used in exceptions
     * @return a remotely initiated stream with the given stream ID.
     *      May return null if the stream was already closed.
     * @throws IllegalArgumentException if the streamID is of the wrong type for
     *         a remote stream.
     * @throws QuicTransportException if the streamID is higher than allowed
     */
    public QuicStream getOrCreateRemoteStream(long streamId, long frameType)
            throws QuicTransportException {
        final int streamType = streamType(streamId);
        if ((streamId & SRV_MASK) == localFlag) {
            throw new IllegalArgumentException("bad remote stream type %s for stream %s"
                    .formatted(streamType, streamId));
        }
        final boolean bidi = isBidirectional(streamId);
        final long maxStreamLimit = bidi ? this.remoteBidiMaxStreamLimit.get()
                : this.remoteUniMaxStreamLimit.get();
        if (maxStreamLimit <= (streamId >> 2)) {
            throw new QuicTransportException("stream ID %s exceeds the number of allowed streams(%s)"
                    .formatted(streamId, maxStreamLimit), QuicTLSEngine.KeySpace.ONE_RTT, frameType,
                    QuicTransportErrors.STREAM_LIMIT_ERROR);
        }

        newRemoteStreamsLock.lock();
        try {
            var id = nextStreamID.get(streamType);
            long nextId = id.get();
            if (nextId > streamId) {
                // already created
                return streams.get(streamId);
            }
            // id must not be modified outside newRemoteStreamsLock
            long altId = id.getAndSet(streamId + 4);
            assert altId == nextId : "next ID concurrently modified";

            AbstractQuicStream stream = null;
            for (long i = nextId; i <= streamId; i += 4) {
                stream = QuicStreams.createStream(connection, i);
                assert stream.isRemoteInitiated();
                register(i, stream);
            }
            assert stream != null;
            assert stream.streamId() == streamId : stream.streamId();
            return stream;
        } finally {
            newRemoteStreamsLock.unlock();
        }
    }


    /**
     * Finds a stream with the given stream ID. Returns {@code null} if no
     * stream with that ID is found.
     * @param streamId a stream ID
     * @return the stream with the given stream ID if found, {@code null}
     * otherwise.
     */
    public QuicStream findStream(long streamId) {
        return streams.get(streamId);
    }

    /**
     * Adds a listener that will be invoked when a remote stream is
     * created.
     *
     * @apiNote The listener will be invoked with any remote streams
     * already opened, and not yet acquired by another listener.
     * Any stream passed to the listener is either a {@link QuicBidiStream}
     * or a {@link QuicReceiverStream} depending on the
     * {@linkplain QuicStreams#streamType(long)
     * stream type} of the given streamId.
     * The listener should return true if it wishes to acquire
     * the stream.
     *
     * @param streamConsumer the listener
     *
     */
    public void addRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
        newRemoteStreamsLock.lock();
        try {
            streamListeners.add(streamConsumer);
            acceptRemoteStreams();
        } finally {
            newRemoteStreamsLock.unlock();
        }
    }

    /**
     * Removes a listener previously added with {@link #addRemoteStreamListener(Predicate)}
     * @return {@code true} if the listener was found and removed, {@code false} otherwise
     */
    public boolean removeRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
        newRemoteStreamsLock.lock();
        try {
            return streamListeners.remove(streamConsumer);
        } finally {
            newRemoteStreamsLock.unlock();
        }
    }

    /**
     * {@return a stream of all currently active {@link QuicStream} in the connection}
     */
    public Stream<? extends QuicStream> quicStreams() {
        return streams.all();
    }

    /**
     * {@return {@code true} if there is some data to send}
     * @apiNote
     * This method may return true in the case where a
     * STREAM_DATA_BLOCKED frame needs to be sent, even if no
     * other data is available.
     */
    public boolean hasAvailableData() {
        return !sendersReady.isEmpty();
    }

    /**
     * {@return true if there are control frames to send}
     * Typically, these are STREAMS_BLOCKED, MAX_STREAMS, RESET_STREAM, STOP_SENDING, and
     * MAX_STREAM_DATA.
     */
    public boolean hasControlFrames() {
        return !sendersReset.isEmpty() || !receiversSend.isEmpty()
                // either of these imply we may send a MAX_STREAMS frame
                || peerUniStreamsBlocked.get() != -1 || peerBidiStreamsBlocked.get() != -1
                // either of these imply we should send a STREAMS_BLOCKED frame
                || uniStreamsBlocked.get() > lastUniStreamsBlockedSent.get()
                || bidiStreamsBlocked.get() > lastBidiStreamsBlockedSent.get();
    }

    /**
     * {@return {@code true} if the given {@code streamId} indicates a stream
     *  that has a receiving part}
     *  In other words, returns {@code true} if the given stream is either
     *  bidirectional or peer-initiated.
     * @param streamId a stream ID
     */
    public boolean isReceivingStream(long streamId) {
        return !isLocalUni(streamId);
    }

    /**
     * {@return {@code true} if the given {@code streamId} indicates a stream
     *  that has a sending part}
     *  In other words, returns {@code true} if the given stream is either
     *  bidirectional or local-initiated.
     * @param streamId a stream ID
     */
    public boolean isSendingStream(long streamId) {
        return !isRemoteUni(streamId);
    }

    /**
     * {@return {@code true} if the given {@code streamId} indicates a local
     *  unidirectional stream}
     * @param streamId a stream ID
     */
    public boolean isLocalUni(long streamId) {
        return streamType(streamId) == localUni;
    }

    /**
     * {@return {@code true} if the given {@code streamId} indicates a local
     *  bidirectional stream}
     * @param streamId a stream ID
     */
    public boolean isLocalBidi(long streamId) {
        return streamType(streamId) == localBidi;
    }

    /**
     * {@return {@code true} if the given {@code streamId} indicates a
     *  peer initiated unidirectional stream}
     * @param streamId a stream ID
     */
    public boolean isRemoteUni(long streamId) {
        return streamType(streamId) == remoteUni;
    }

    /**
     * {@return {@code true} if the given {@code streamId} indicates a
     *  peer initiated bidirectional stream}
     * @param streamId a stream ID
     */
    public boolean isRemoteBidi(long streamId) {
        return streamType(streamId) == remoteBidi;
    }

    /**
     * Mark the stream whose ID is encoded in the given
     * {@code ResetStreamFrame} as needing a RESET_STREAM frame to be sent.
     * It will put the stream and the frame in the {@code sendersReset} map.
     * @param streamId  the id of the stream that should be reset
     * @param errorCode the application error code
     */
    public void requestResetStream(long streamId, long errorCode) {
        assert isSendingStream(streamId);
        var stream = senderImpl(streams.get(streamId));
        if (stream == null) {
            if (debug.on()) {
                debug.log("Can't reset stream %d: no such stream", streamId);
            }
            return;
        }
        sendersReset.putIfAbsent(stream, errorCode);
        if (debug.on()) {
            debug.log("Reset stream scheduled");
        }
    }

    /**
     * Mark the stream whose ID is encoded in the given
     * {@code MaxStreamDataFrame} as needing a MAX_STREAM_DATA frame to be sent.
     * It will put the stream and the frame in the {@code receiversSend} map.
     * @param maxStreamDataFrame the MAX_STREAM_DATA frame to send
     */
    public void requestSendMaxStreamData(MaxStreamDataFrame maxStreamDataFrame) {
        Objects.requireNonNull(maxStreamDataFrame, "maxStreamDataFrame");
        long streamId = maxStreamDataFrame.streamID();
        assert isReceivingStream(streamId);
        var stream = streams.get(streamId);
        if (stream == null) {
            if (debug.on()) {
                debug.log("Can't send MaxStreamDataFrame %d: no such stream", streamId);
            }
            return;
        }
        if (stream instanceof QuicReceiverStream receiver) {
            // don't replace a stop sending frame, and don't replace
            // a max stream data frame if it has a bigger max stream data
            receiversSend.compute(receiver, (s, frame) -> {
                if (frame instanceof StopSendingFrame stopSendingFrame) {
                    assert s.streamId() == stopSendingFrame.streamID();
                    // no need to send max data frame if we are requesting
                    // stop sending
                    return frame;
                }
                if (frame instanceof MaxStreamDataFrame maxFrame) {
                    assert s.streamId() == maxFrame.streamID();
                    if (maxFrame.maxStreamData() > maxStreamDataFrame.maxStreamData()) {
                        // send the frame that has the greater max data
                        return maxFrame;
                    } else return maxStreamDataFrame;
                }
                assert frame == null;
                return maxStreamDataFrame;
            });
        } else {
            if (debug.on()) {
                debug.log("Can't send %s stream %d: not a receiver stream",
                        maxStreamDataFrame.getClass(), streamId);
            }
        }
    }


    /**
     * Mark the stream whose ID is encoded in the given
     * {@code StopSendingFrame} as needing a STOP_SENDING frame to be sent.
     * It will put the stream and the frame in the {@code receiversSend} map.
     * @param stopSendingFrame the STOP_SENDING frame to send
     */
    public void scheduleStopSendingFrame(StopSendingFrame stopSendingFrame) {
        Objects.requireNonNull(stopSendingFrame, "stopSendingFrame");
        long streamId = stopSendingFrame.streamID();
        assert isReceivingStream(streamId);
        var stream = streams.get(streamId);
        if (stream == null) {
            if (debug.on()) {
                debug.log("Can't send STOP_SENDING to stream %d: no such stream", streamId);
            }
            return;
        }
        if (stream instanceof QuicReceiverStream receiver) {
            // don't need to check if we already have a frame registered:
            // stop sending takes precedence.
            receiversSend.put(receiver, stopSendingFrame);
        } else {
            if (debug.on()) {
                debug.log("Can't send %s stream %d: not a receiver stream",
                        stopSendingFrame.getClass(), streamId);
            }
        }
    }

    /**
     * Called when the RESET_STREAM frame is acknowledged by the peer.
     * @param reset the RESET_STREAM frame
     */
    public void streamResetAcknowledged(ResetStreamFrame reset) {
        Objects.requireNonNull(reset, "reset");
        long streamId = reset.streamId();
        assert isSendingStream(streamId) :
                "stream %s is not a sending stream".formatted(streamId);
        final var stream = streams.get(streamId);
        if (stream == null) {
            return;
        }
        var sender = senderImpl(stream);
        if (sender != null) {
            sender.resetAcknowledged(reset.finalSize());
            assert !stream.isDone() || !streams.streams.containsKey(streamId)
                    : "resetAcknowledged() should have removed the stream";
            if (debug.on()) {
                debug.log("acknowledged reset for stream %d", streamId);
            }
        }
    }

    /**
     * Called when the final STREAM frame is acknowledged by the peer.
     * @param streamFrame the final STREAM frame
     */
    public void streamDataSentAcknowledged(StreamFrame streamFrame) {
        long streamId = streamFrame.streamId();
        assert isSendingStream(streamId) :
                "stream %s is not a sending stream".formatted(streamId);
        assert streamFrame.isLast();
        final var stream = streams.get(streamId);
        if (stream == null) {
            return;
        }
        var sender = senderImpl(stream);
        if (sender != null) {
            sender.dataAcknowledged(streamFrame.offset() + streamFrame.dataLength());
            assert !stream.isDone() || !streams.streams.containsKey(streamId)
                    : "dataAcknowledged() should have removed the stream";
            if (debug.on()) {
                debug.log("acknowledged data for stream %d", streamId);
            }
        }
    }

    /**
     * Tracks a stream, belonging to this connection, as being blocked from sending data
     * due to flow control limit.
     *
     * @param streamId the stream id
     */
    final void trackBlockedStream(final long streamId) {
        this.flowControlBlockedStreams.add(streamId);
    }

    /**
     * Stops tracking a stream, belonging to this connection, that may have been previously
     * tracked as being blocked due to flow control limit.
     *
     * @param streamId the stream id
     */
    final void untrackBlockedStream(final long streamId) {
        this.flowControlBlockedStreams.remove(streamId);
    }


    /**
     * Removes a stream from the stream map after its state has been
     * switched to DATA_RECVD or RESET_RECVD
     * @param streamId the stream id
     * @param stream the stream instance
     */
    private void removeStream(long streamId, QuicStream stream) {
        // if we were tracking this stream as blocked due to flow control, then
        // stop tracking the stream.
        untrackBlockedStream(streamId);
        if (stream instanceof AbstractQuicStream astream) {
            if (astream.isDone()) {
                if (debug.on()) {
                    debug.log("Removing stream %d (%s)",
                            stream.streamId(), stream.getClass().getSimpleName());
                }
                streams.remove(streamId, astream);
                if (stream.isRemoteInitiated()) {
                    // the queue is not expected to contain many elements.
                    newRemoteStreams.remove(stream);
                    if (shouldSendMaxStreams(stream.isBidirectional())) {
                        this.connection.runAppPacketSpaceTransmitter();
                    }
                }
            } else {
                if (debug.on()) {
                    debug.log("Can't remove stream yet: %d (%s) is %s",
                            stream.streamId(), stream.getClass().getSimpleName(),
                            stream.state());
                }
            }
        }
        assert stream instanceof AbstractQuicStream
                : "stream %s: unexpected stream class: %s"
                .formatted(streamId, stream.getClass());
    }

    /**
     * Called when new local transport parameters are available
     * @param params the new local transport parameters
     */
    public void newLocalTransportParameters(final QuicTransportParameters params) {
        // the limit imposed on the remote peer by the local endpoint
        final long newRemoteUniMax = params.getIntParameter(ParameterId.initial_max_streams_uni);
        tryIncreaseLimitTo(this.remoteUniMaxStreamLimit, newRemoteUniMax);
        final long newRemoteBidiMax = params.getIntParameter(ParameterId.initial_max_streams_bidi);
        tryIncreaseLimitTo(this.remoteBidiMaxStreamLimit, newRemoteBidiMax);
        streams.all().forEach(s -> newInitialLocalParameters(s, params));
    }

    /**
     * Called when new peer transport parameters are available
     * @param params the new local transport parameters
     */
    public void newPeerTransportParameters(final QuicTransportParameters params) {
        // the limit imposed on the local endpoint by the remote peer
        final long localUniMaxStreams = params.getIntParameter(ParameterId.initial_max_streams_uni);
        if (debug.on()) {
            debug.log("increasing localUniMaxStreamLimit to initial_max_streams_uni: "
                    + localUniMaxStreams);
        }
        this.localUniMaxStreamLimit.tryIncreaseLimitTo(localUniMaxStreams);
        final long localBidiMaxStreams = params.getIntParameter(ParameterId.initial_max_streams_bidi);
        if (debug.on()) {
            debug.log("increasing localBidiMaxStreamLimit to initial_max_streams_bidi: "
                    + localBidiMaxStreams);
        }
        this.localBidiMaxStreamLimit.tryIncreaseLimitTo(localBidiMaxStreams);
        // set initial parameters on streams
        streams.all().forEach(s -> newInitialPeerParameters(s, params));
        if (debug.on()) {
            debug.log("all streams updated (%s)", streams.streams.size());
        }
    }

    /**
     * Called to set initial peer parameters on a stream
     * @param stream the stream on which parameters might be set
     * @param params the peer transport parameters
     */
    private void newInitialPeerParameters(QuicStream stream, QuicTransportParameters params) {
        long streamId = stream.streamId();
        if (isLocalUni(stream.streamId())) {
            if (params.isPresent(ParameterId.initial_max_stream_data_uni)) {
                long maxData = params.getIntParameter(ParameterId.initial_max_stream_data_uni);
                senderImpl(stream).setMaxStreamData(maxData);
            }
        } else if (isLocalBidi(streamId)) {
            // remote for the peer is local for us
            if (params.isPresent(ParameterId.initial_max_stream_data_bidi_remote)) {
                long maxData = params.getIntParameter(ParameterId.initial_max_stream_data_bidi_remote);
                senderImpl(stream).setMaxStreamData(maxData);
            }
        } else if (isRemoteBidi(streamId)) {
            // local for the peer is remote for us
            if (params.isPresent(ParameterId.initial_max_stream_data_bidi_local)) {
                long maxData = params.getIntParameter(ParameterId.initial_max_stream_data_bidi_local);
                senderImpl(stream).setMaxStreamData(maxData);
            }
        }
    }

    private static boolean tryIncreaseLimitTo(final AtomicLong limit, final long newLimit) {
        long currentLimit = limit.get();
        while (currentLimit < newLimit) {
            if (limit.compareAndSet(currentLimit, newLimit)) {
                return true;
            }
            currentLimit = limit.get();
        }
        return false;
    }

    /**
     * Called to set initial peer parameters on a stream
     * @param stream the stream on which parameters might be set
     * @param params the peer transport parameters
     */
    private void newInitialLocalParameters(QuicStream stream, QuicTransportParameters params) {
        long streamId = stream.streamId();
        if (isRemoteUni(stream.streamId())) {
            if (params.isPresent(ParameterId.initial_max_stream_data_uni)) {
                long maxData = params.getIntParameter(ParameterId.initial_max_stream_data_uni);
                receiverImpl(stream).updateMaxStreamData(maxData);
            }
        } else if (isLocalBidi(streamId)) {
            if (params.isPresent(ParameterId.initial_max_stream_data_bidi_local)) {
                long maxData = params.getIntParameter(ParameterId.initial_max_stream_data_bidi_local);
                receiverImpl(stream).updateMaxStreamData(maxData);
            }
        } else if (isRemoteBidi(streamId)) {
            if (params.isPresent(ParameterId.initial_max_stream_data_bidi_remote)) {
                long maxData = params.getIntParameter(ParameterId.initial_max_stream_data_bidi_remote);
                receiverImpl(stream).updateMaxStreamData(maxData);
            }
        }
    }

    /**
     * Set max stream data for a stream.
     * Called when a {@link jdk.internal.net.http.quic.frames.MaxStreamDataFrame
     * MaxStreamDataFrame} is received.
     * @param stream the stream
     * @param maxStreamData the max data that the peer is willing to accept on this stream
     */
    public void setMaxStreamData(QuicSenderStream stream, long maxStreamData) {
        var sender = senderImpl(stream);
        if (sender != null) {
            final long newFinalizedLimit = sender.setMaxStreamData(maxStreamData);
            // if the connection was tracking this stream as blocked due to flow control
            // and if this new MAX_STREAM_DATA limit unblocked this stream, then
            // stop tracking the stream.
            if (newFinalizedLimit == maxStreamData) { // the proposed limit was accepted
                if (!sender.isBlocked()) {
                    untrackBlockedStream(stream.streamId());
                }
            }
        }
    }

    /**
     * This method is called when a {@link
     * jdk.internal.net.http.quic.frames.StopSendingFrame} is received
     * from the peer.
     *  @param stream  the stream for which stop sending was requested
     *                  by the peer
     * @param errorCode the error code
     */
    public void stopSendingReceived(QuicSenderStream stream, long errorCode) {
        var sender = senderImpl(stream);
        if (sender != null) {
            // if the stream was being tracked as blocked from sending data,
            // due to flow control limits imposed by the peer, then we now
            // stop tracking it since the peer no longer wants us to send data
            // on this stream.
            untrackBlockedStream(stream.streamId());
            sender.stopSendingReceived(errorCode);
        }
    }

    /**
     * Called when the receiving part or the sending part of a stream
     * reaches a terminal state.
     * @param streamId the id of the stream
     * @param state the terminal state
     */
    public void notifyTerminalState(long streamId, StreamState state) {
        assert state.isTerminal() : state;
        var stream = streams.get(streamId);
        if (stream != null) {
            removeStream(streamId, stream);
        }
    }

    /**
     * Called when the connection is closed by the higher level
     * protocol
     * @param terminationCause the termination cause
     */
    public void terminate(final TerminationCause terminationCause) {
        assert terminationCause != null : "termination cause is null";
        // make sure all active streams are woken up when we close a connection
        streams.all().forEach((stream) -> {
            if (stream instanceof QuicSenderStream) {
                var sender = senderImpl(stream);
                try {
                    sender.terminate(terminationCause);
                } catch (Throwable t) {
                    if (debug.on()) {
                        debug.log("failed to close sender stream %s: %s", sender.streamId(), t);
                    }
                }
            }
            if (stream instanceof QuicReceiverStream) {
                var receiver = receiverImpl(stream);
                try {
                    receiver.terminate(terminationCause);
                } catch (Throwable t) {
                    // log and ignore
                    if (debug.on()) {
                        debug.log("failed to close receiver stream %s: %s", receiver.streamId(), t);
                    }
                }
            }
        });
    }

    /**
     * This method is called by when a stream has data available for sending.
     *
     * @param streamId the stream id of the stream which is ready
     * @see QuicConnectionImpl#streamDataAvailableForSending
     */
    public void enqueueForSending(long streamId) {
        var stream = streams.get(streamId);
        if (stream == null) {
            if (debug.on())
                debug.log("WARNING: stream %d not found", streamId);
            return;
        }
        if (stream instanceof QuicSenderStream sender) {
            // No need to check/assert the presence of this sender in the queue.
            // In fact there is no guarantee that the sender isn't already in the
            // queue, since the scheduler loop can also put it back into the queue,
            // if for example, not everything that the sender wanted to send could
            // fit in the quic packet.
            sendersReady.add(sender);
        } else {
            String msg = String.format("Stream %s not a sending or bidi stream: %s",
                    streamId, stream.getClass().getName());
            if (debug.on()) {
                debug.log("WARNING: " + msg);
            }
            throw new AssertionError(msg);
        }
    }

    /**
     * If there are any streams in this connection that have been blocked from sending
     * data due to flow control limit on that stream, then this method enqueues a
     * {@code STREAM_DATA_BLOCKED} frame to be sent for each such stream.
     */
    public final void enqueueStreamDataBlocked() {
        connection.streamDataAvailableForSending(this.flowControlBlockedStreams);
    }

    /**
     * {@return the sender part implementation of the given stream, or {@code null}}
     * This method returns null if the given stream doesn't have a sending part
     * (that is, if it is a unidirectional peer initiated stream).
     * @param stream a sending or bidirectional stream
     */
    QuicSenderStreamImpl senderImpl(QuicStream stream) {
        if (stream instanceof QuicSenderStreamImpl sender) {
            return sender;
        } else if (stream instanceof QuicBidiStreamImpl bidi) {
            return bidi.senderPart();
        }
        return null;
    }

    /**
     * {@return the receiver part implementation of the given stream, or {@code null}}
     * This method returns null if the given stream doesn't have a receiver part
     * (that is, if it is a unidirectional local initiated stream).
     * @param stream a receiving or bidirectional stream
     */
    QuicReceiverStreamImpl receiverImpl(QuicStream stream) {
        if (stream instanceof QuicReceiverStreamImpl receiver) {
            return receiver;
        } else if (stream instanceof QuicBidiStreamImpl bidi) {
            return bidi.receiverPart();
        }
        return null;
    }

    /**
     * Called when a StreamFrame is received.
     * @param stream the stream for which the StreamFrame was received
     * @param frame the stream frame
     * @throws QuicTransportException if an error occurred processing the frame
     */
    public void processIncomingFrame(QuicStream stream, StreamFrame frame) throws QuicTransportException {
        var receiver = receiverImpl(stream);
        assert receiver != null;
        receiver.processIncomingFrame(frame);
    }

    /**
     * Called when a ResetStreamFrame is received.
     * @param stream the stream for which the ResetStreamFrame was received
     * @param frame the reset stream frame
     * @throws QuicTransportException if an error occurred processing the frame
     */
    public void processIncomingFrame(QuicStream stream, ResetStreamFrame frame) throws QuicTransportException {
        var receiver = receiverImpl(stream);
        assert receiver != null;
        receiver.processIncomingResetFrame(frame);
    }

    public void processIncomingFrame(final QuicStream stream, final StreamDataBlockedFrame frame) {
        assert stream.streamId() == frame.streamId() : "unexpected stream id " + frame.streamId()
                + " in frame, expected " + stream.streamId();
        final QuicReceiverStreamImpl rcvrStream = receiverImpl(stream);
        assert rcvrStream != null : "missing receiver stream for stream " + stream.streamId();
        rcvrStream.processIncomingFrame(frame);
    }

    public boolean tryIncreaseStreamLimit(final MaxStreamsFrame maxStreamsFrame) {
        final StreamCreationPermit permit = maxStreamsFrame.isBidi()
                ? localBidiMaxStreamLimit : localUniMaxStreamLimit;
        final long newLimit = maxStreamsFrame.maxStreams();
        if (debug.on()) {
            if (maxStreamsFrame.isBidi()) {
                debug.log("increasing localBidiMaxStreamLimit limit to: " + newLimit);
            } else {
                debug.log("increasing localUniMaxStreamLimit limit to: " + newLimit);
            }
        }
        return permit.tryIncreaseLimitTo(newLimit);
    }

    /**
     * Checks whether any stream needs to have a STOP_SENDING, RESET_STREAM or any connection
     * control frames like STREAMS_BLOCKED, MAX_STREAMS sent and adds the frame to the list
     * if there's room.
     * @param frames    list of frames
     * @param remaining maximum number of bytes that can be added by this method
     * @return number of bytes actually added
     */
    private long checkResetAndOtherControls(List<QuicFrame> frames, long remaining) {
        if (debug.on())
            debug.log("checking reset and other control frames...");
        long added = 0;
        // check STREAMS_BLOCKED, only send it if the local endpoint is blocked on a limit
        // for which we haven't yet sent a STREAMS_BLOCKED
        final long uniStreamsBlockedLimit = this.uniStreamsBlocked.get();
        final long lastUniStreamsBlockedSent = this.lastUniStreamsBlockedSent.get();
        if (uniStreamsBlockedLimit != -1 && uniStreamsBlockedLimit > lastUniStreamsBlockedSent) {
            final StreamsBlockedFrame frame = new StreamsBlockedFrame(false, uniStreamsBlockedLimit);
            final int size = frame.size();
            if (size > remaining - added) {
                if (debug.on()) {
                    debug.log("Not enough space to add a STREAMS_BLOCKED frame for uni streams");
                }
            } else {
                frames.add(frame);
                added += size;
                // now that we are sending a STREAMS_BLOCKED frame, keep track of the limit
                // that we sent it with
                this.lastUniStreamsBlockedSent.set(frame.maxStreams());
            }
        }
        final long bidiStreamsBlockedLimit = this.bidiStreamsBlocked.get();
        final long lastBidiStreamsBlockedSent = this.lastBidiStreamsBlockedSent.get();
        if (bidiStreamsBlockedLimit != -1 && bidiStreamsBlockedLimit > lastBidiStreamsBlockedSent) {
            final StreamsBlockedFrame frame = new StreamsBlockedFrame(true, bidiStreamsBlockedLimit);
            final int size = frame.size();
            if (size > remaining - added) {
                if (debug.on()) {
                    debug.log("Not enough space to add a STREAMS_BLOCKED frame for bidi streams");
                }
            } else {
                frames.add(frame);
                added += size;
                // now that we are sending a STREAMS_BLOCKED frame, keep track of the limit
                // that we sent it with
                this.lastBidiStreamsBlockedSent.set(frame.maxStreams());
            }
        }
        // check STOP_SENDING and MAX_STREAM_DATA
        var rcvIterator = receiversSend.entrySet().iterator();
        while (rcvIterator.hasNext()) {
            var entry = rcvIterator.next();
            var frame = entry.getValue();
            if (frame.size() > remaining - added) {
                if (debug.on()) {
                    debug.log("Stream %s: not enough space for %s",
                            entry.getKey().streamId(), frame);
                }
                break;
            }
            var receiver = receiverImpl(entry.getKey());
            var size = checkSendControlFrame(receiver, frame, frames);
            if (size > 0) {
                added += size;
            }
            rcvIterator.remove();
        }

        // check RESET_STREAM
        var sndIterator = sendersReset.entrySet().iterator();
        while (sndIterator.hasNext()) {
            Map.Entry<QuicSenderStream, Long> entry = sndIterator.next();
            var sender = senderImpl(entry.getKey());
            assert sender != null;
            long finalSize = sender.dataSent();
            ResetStreamFrame frame = new ResetStreamFrame(sender.streamId(), entry.getValue(), finalSize);
            final int size = frame.size();
            if (size > remaining - added) {
                if (debug.on()) {
                    debug.log("Stream %s: not enough space for ResetFrame",
                            sender.streamId());
                }
                break;
            }
            if (debug.on())
                debug.log("Stream %s: Adding ResetFrame", sender.streamId());
            frames.add(frame);
            added += size;
            sender.resetSent();
            sndIterator.remove();
        }

        if (remaining - added > 18) {
            // add MAX_STREAMS if necessary
            added += addMaxStreamsFrame(frames, false);
            added += addMaxStreamsFrame(frames, true);
        }
        return added;
    }

    private boolean shouldSendMaxStreams(final boolean bidi) {
        final boolean rcvdStreamsBlocked = bidi
                ? this.peerBidiStreamsBlocked.get() != -1
                : this.peerUniStreamsBlocked.get() != -1;
        // if we either received a STREAMS_BLOCKED from the peer for that stream type
        // or if our internal algorithm decides that the peer is about to reach the stream
        // creation limit
        return rcvdStreamsBlocked || nextMaxStreamsLimit(bidi) > 0;
    }

    private long addMaxStreamsFrame(final List<QuicFrame> frames, final boolean bidi) {
        final long newMaxStreamsLimit = connection.nextMaxStreamsLimit(bidi);
        if (newMaxStreamsLimit == 0) {
            return 0;
        }
        final boolean limitIncreased;
        if (bidi) {
            limitIncreased = tryIncreaseLimitTo(remoteBidiMaxStreamLimit, newMaxStreamsLimit);
        } else {
            limitIncreased = tryIncreaseLimitTo(remoteUniMaxStreamLimit, newMaxStreamsLimit);
        }
        if (!limitIncreased) {
            return 0;
        }
        final MaxStreamsFrame frame = new MaxStreamsFrame(bidi, newMaxStreamsLimit);
        frames.add(frame);
        // now that we are sending MAX_STREAMS frame to the peer, reset the relevant
        // STREAMS_BLOCKED flag that we might have set when/if we had received a STREAMS_BLOCKED
        // from the peer
        if (bidi) {
            this.peerBidiStreamsBlocked.set(-1);
        } else {
            this.peerUniStreamsBlocked.set(-1);
        }
        if (debug.on()) {
            debug.log("Increasing max remote %s streams to %s",
                    bidi ? "bidi" : "uni", newMaxStreamsLimit);
        }
        return frame.size();
    }

    public long nextMaxStreamsLimit(final boolean bidi) {
        return bidi ? streams.remoteBidiNextMaxStreams : streams.remoteUniNextMaxStreams;
    }

    /**
     * {@return true if there are any streams on this connection which are blocked from
     * sending data due to flow control limit, false otherwise}
     */
    public final boolean hasBlockedStreams() {
        return !this.flowControlBlockedStreams.isEmpty();
    }

    /**
     * Checks whether the given stream is recorded as needing a control
     * frame to be sent, and if so, add that frame to the list
     *
     * @param receiver  the receiver part of the stream
     * @param frame     the frame to send
     * @param frames    list of frames
     * @return size of the added frame, or zero if no frame was added
     * @apiNote Typically, the control frame that is sent is either a MAX_STREAM_DATA
     * or a STOP_SENDING frame
     */
    private long checkSendControlFrame(QuicReceiverStreamImpl receiver,
                                       QuicFrame frame,
                                       List<QuicFrame> frames) {
        if (frame == null) {
            if (debug.on())
                debug.log("Stream %s: no receiver frame to send", receiver.streamId());
            return 0;
        }
        if (frame instanceof MaxStreamDataFrame maxStreamDataFrame) {
            if (receiver.receivingState() == ReceivingStreamState.RECV) {
                // if we know the final size, no point in increasing max data
                if (debug.on())
                    debug.log("Stream %s: Adding MaxStreamDataFrame", receiver.streamId());
                frames.add(frame);
                receiver.updateMaxStreamData(maxStreamDataFrame.maxStreamData());
                return frame.size();
            }
            return 0;
        } else if (frame instanceof StopSendingFrame) {
            if (debug.on())
                debug.log("Stream %s: Adding StopSendingFrame", receiver.streamId());
            frames.add(frame);
            return frame.size();
        } else {
            throw new InternalError("Should not reach here - not a control frame: " + frame);
        }
    }

    /**
     * Package available data in {@link StreamFrame} instances and add them
     * to the provided frames list. Additional frames, like connection control frames
     * {@code STREAMS_BLOCKED}, {@code MAX_STREAMS} or stream flow control frames like
     * {@code STREAM_DATA_BLOCKED} may also be added if space allows. The {@link StreamDataBlockedFrame}
     * is added only once for a given stream, until the stream becomes ready again.
     * @implSpec
     * The total cumulated size of the returned frames must not exceed {@code maxSize}.
     * The total cumulated lengths of the returned frames must not exceed {@code maxConnectionData}.
     *
     * @param encoder the {@link QuicPacketEncoder}, used if anything is quic version
     *                dependent.
     * @param maxSize the cumulated maximum size of all the frames
     * @param maxConnectionData the maximum number of stream data bytes that can
     *                          be packaged to respect connection flow control
     *                          constraints
     * @param frames a list of frames in which to add the packaged data
     * @return the total number of stream data bytes packaged in the created
     *         frames. This will not exceed the given {@code maxConnectionData}.
     */
    public long produceFramesToSend(QuicPacketEncoder encoder, long maxSize,
                                    long maxConnectionData, List<QuicFrame> frames)
            throws QuicTransportException {
        long remaining = maxSize;
        long produced = 0;
        try {
            remaining -= checkResetAndOtherControls(frames, remaining);
            // scan the streams and compose a list of frames - possibly including
            // stream data blocked frames,
            QuicSenderStreamImpl sender;
            NEXT_STREAM: while ((sender = senderImpl(sendersReady.poll())) != null) {
                long streamId = sender.streamId();
                boolean stillReady = true;
                try {
                    do {
                        if (remaining == 0 || maxConnectionData == 0) break;
                        var state = sender.sendingState();
                        switch (state) {
                            case SEND -> {
                                long offset = sender.dataSent();
                                int headerSize = StreamFrame.headerSize(encoder, streamId, offset, remaining);
                                if (headerSize >= remaining) {
                                    break NEXT_STREAM;
                                }
                                long maxControlled = Math.min(maxConnectionData, remaining - headerSize);
                                int maxData = (int) Math.min(Integer.MAX_VALUE, maxControlled);
                                if (maxData <= 0) {
                                    break NEXT_STREAM;
                                }
                                ByteBuffer buffer = sender.poll(maxData);
                                if (buffer != null) {
                                    int length = buffer.remaining();
                                    assert length <= remaining;
                                    assert length <= maxData;
                                    long streamSize = sender.streamSize();
                                    boolean fin = streamSize >= 0 && streamSize == offset + length;
                                    if (fin) {
                                        stillReady = false;
                                    }
                                    if (length > 0 || fin) {
                                        StreamFrame frame = new StreamFrame(streamId, offset, length, fin, buffer);
                                        int size = frame.size();
                                        assert size <= remaining : "stream:%s: size %s > remaining %s"
                                                .formatted(streamId, size, remaining);
                                        if (debug.on()) {
                                            debug.log("stream:%s Adding StreamFrame: %s",
                                                    streamId, frame);
                                        }
                                        frames.add(frame);
                                        remaining -= size;
                                        produced += length;
                                        maxConnectionData -= length;
                                    }
                                }
                                var blocked = sender.isBlocked();
                                if (blocked) {
                                    // track this stream as blocked due to flow control
                                    trackBlockedStream(streamId);
                                    final var dataBlocked = new StreamDataBlockedFrame(streamId, sender.dataSent());
                                    // This might produce multiple StreamDataBlocked frames
                                    // if the stream was added to sendersReady multiple times, so
                                    // we check before actually sending a STREAM_DATA_BLOCKED frame
                                    if (!frames.contains(dataBlocked)) {
                                        var fdbSize = dataBlocked.size();
                                        if (dataBlocked.size() > remaining) {
                                            // keep the stream in the ready list if we haven't been
                                            // able to generate the StreamDataBlockedFrame
                                            break NEXT_STREAM;
                                        }
                                        if (debug.on()) {
                                            debug.log("stream:" + streamId + " sender is blocked: " + dataBlocked);
                                        }
                                        frames.add(dataBlocked);
                                        remaining -= fdbSize;
                                    }
                                    stillReady = false;
                                    continue NEXT_STREAM;
                                }
                                if (buffer == null) {
                                    stillReady = sender.available() != 0;
                                    continue NEXT_STREAM;
                                }
                            }
                            case DATA_SENT, DATA_RECVD, RESET_SENT, RESET_RECVD -> {
                                stillReady = false;
                                continue NEXT_STREAM;
                            }
                            case READY -> {
                                String msg = "stream:%s: illegal state %s".formatted(streamId, state);
                                throw new IllegalStateException(msg);
                            }
                        }
                        if (debug.on()) {
                            debug.log("packageStreamData: stream:%s, remaining:%s, " +
                                            "maxConnectionData: %s, produced:%s",
                                    streamId, remaining, maxConnectionData, produced);
                        }
                    } while (remaining > 0 && maxConnectionData > 0);
                } catch (RuntimeException | AssertionError x) {
                    stillReady = false;
                    throw new QuicTransportException("Failed to compose frames for stream " + streamId,
                            KeySpace.ONE_RTT, 0, QuicTransportErrors.INTERNAL_ERROR.code(), x);
                } finally {
                    if (stillReady) {
                        if (debug.on())
                            debug.log("stream:%s is still ready", streamId);
                        enqueueForSending(streamId);
                    } else {
                        if (debug.on())
                            debug.log("stream:%s is no longer ready", streamId);
                    }
                }
                assert maxConnectionData >= 0 : "produced " + produced + " max is " + maxConnectionData;
                if (remaining == 0 || maxConnectionData == 0) break;
            }
        } catch (RuntimeException | AssertionError x) {
            if (debug.on()) debug.log("Failed to compose frames", x);
            if (Log.errors()) {
                Log.logError(connection.logTag()
                        + ": Failed to compose frames", x);
            }
            throw new QuicTransportException("Failed to compose frames",
                    KeySpace.ONE_RTT, 0, QuicTransportErrors.INTERNAL_ERROR.code(), x);
        }
        return produced;
    }

    private interface ReadyStreamCollection {
        boolean isEmpty();

        void add(QuicSenderStream sender);

        QuicStream poll();
    }
    //This queue is used to ensure fair sending of stream data: the packageStreamData method
    // will pop and push streams from/to this queue in a round-robin fashion so that one stream
    // doesn't starve all the others.
    private static class ReadyStreamQueue implements ReadyStreamCollection {
        private ConcurrentLinkedQueue<QuicSenderStream> queue = new ConcurrentLinkedQueue<>();

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public void add(QuicSenderStream sender) {
            queue.add(sender);
        }

        public QuicStream poll() {
            return queue.poll();
        }
    }
    // This queue is used to ensure fast closing of streams: it always returns
    // the ready stream with the lowest ID.
    private static class ReadyStreamSortedQueue implements ReadyStreamCollection {
        private ConcurrentSkipListMap<Long, QuicSenderStream> queue = new ConcurrentSkipListMap<>();

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public void add(QuicSenderStream sender) {
            queue.put(sender.streamId(), sender);
        }

        public QuicStream poll() {
            Map.Entry<Long, QuicSenderStream> entry = queue.pollFirstEntry();
            if (entry == null) return null;
            return entry.getValue();
        }
    }

    // provides a limited view/operations over a ConcurrentHashMap(). we compute additional
    // state in the remove() and put() APIs. providing only a limited set of APIs allows us
    // to keep the places where we do that additional state computation, to minimal.
    private final class StreamsContainer {
        // A map of <Stream ID, Quic Stream>
        private final ConcurrentMap<Long, AbstractQuicStream> streams = new ConcurrentHashMap<>();
        // active remote bidi stream count
        private final AtomicLong remoteBidiActiveStreams = new AtomicLong();
        // active remote uni stream count
        private final AtomicLong remoteUniActiveStreams = new AtomicLong();

        private volatile long remoteBidiNextMaxStreams;
        private volatile long remoteUniNextMaxStreams;

        AbstractQuicStream get(final long streamId) {
            return streams.get(streamId);
        }

        boolean remove(final long streamId, final AbstractQuicStream stream) {
            if (!streams.remove(streamId, stream)) {
                return false;
            }
            final int streamType = (int) (stream.streamId() & TYPE_MASK);
            if (streamType == remoteBidi) {
                final long currentActive = remoteBidiActiveStreams.decrementAndGet();
                remoteBidiNextMaxStreams = computeNextMaxStreamsLimit(streamType, currentActive,
                        remoteBidiMaxStreamLimit.get());
            } else if (streamType == remoteUni) {
                final long currentActive = remoteUniActiveStreams.decrementAndGet();
                remoteUniNextMaxStreams = computeNextMaxStreamsLimit(streamType, currentActive,
                        remoteUniMaxStreamLimit.get());
            }
            return true;
        }

        AbstractQuicStream put(final long streamId, final AbstractQuicStream stream) {
            final AbstractQuicStream previous = streams.put(streamId, stream);
            final int streamType = (int) (stream.streamId() & TYPE_MASK);
            if (streamType == remoteBidi) {
                final long currentActive = remoteBidiActiveStreams.incrementAndGet();
                remoteBidiNextMaxStreams = computeNextMaxStreamsLimit(streamType, currentActive,
                        remoteBidiMaxStreamLimit.get());
            } else if (streamType == remoteUni) {
                final long currentActive = remoteUniActiveStreams.incrementAndGet();
                remoteUniNextMaxStreams = computeNextMaxStreamsLimit(streamType, currentActive,
                        remoteUniMaxStreamLimit.get());
            }
            return previous;
        }

        Stream<AbstractQuicStream> all() {
            return streams.values().stream();
        }

        /**
         * Returns the next (higher) max streams limit that can be advertised to the remote peer.
         * Returns {@code 0} if the limit should not be increased.
         */
        private long computeNextMaxStreamsLimit(
                final int streamType, final long currentActiveCount,
                final long currentMaxStreamsLimit) {
            // we only deal with remote bidi or remote uni
            assert (streamType == remoteBidi || streamType == remoteUni)
                    : "stream type is neither remote bidi nor remote uni: " + streamType;
            final long usedRemoteStreams = peekNextStreamId(streamType) >> 2;
            final boolean bidi = streamType == remoteBidi;
            final var desiredStreamCount =  bidi ? MAX_BIDI_STREAMS_WINDOW_SIZE
                    : MAX_UNI_STREAMS_WINDOW_SIZE;
            final long desiredMaxStreams = usedRemoteStreams - currentActiveCount + desiredStreamCount;
            // we compute a new limit after we consumed 25% (arbitrary decision) of the desired window
            if (desiredMaxStreams - currentMaxStreamsLimit > desiredStreamCount >> 2) {
                return desiredMaxStreams;
            }
            return 0;
        }
    }
}
