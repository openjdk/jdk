/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.httpclient.test.lib.http3;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.CancelPushFrame;
import jdk.internal.net.http.http3.frames.FramesDecoder;
import jdk.internal.net.http.http3.frames.GoAwayFrame;
import jdk.internal.net.http.http3.frames.HeadersFrame;
import jdk.internal.net.http.http3.frames.Http3Frame;
import jdk.internal.net.http.http3.frames.MalformedFrame;
import jdk.internal.net.http.http3.frames.MaxPushIdFrame;
import jdk.internal.net.http.http3.frames.PartialFrame;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.http3.streams.Http3Streams;
import jdk.internal.net.http.http3.streams.Http3Streams.StreamType;
import jdk.internal.net.http.http3.streams.PeerUniStreamDispatcher;
import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.http3.streams.UniStreamPair;
import jdk.internal.net.http.qpack.Decoder;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.qpack.writers.HeaderFrameWriter;
import jdk.internal.net.http.qpack.TableEntry;
import jdk.internal.net.http.quic.ConnectionTerminator;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;

import static jdk.internal.net.http.http3.Http3Error.H3_STREAM_CREATION_ERROR;
import static jdk.internal.net.http.http3.frames.SettingsFrame.SETTINGS_MAX_FIELD_SECTION_SIZE;
import static jdk.internal.net.http.http3.frames.SettingsFrame.SETTINGS_QPACK_BLOCKED_STREAMS;
import static jdk.internal.net.http.http3.frames.SettingsFrame.SETTINGS_QPACK_MAX_TABLE_CAPACITY;
import static jdk.internal.net.http.quic.TerminationCause.appLayerClose;

public class Http3ServerConnection {
    private final Http3TestServer server;
    private final QuicServerConnection quicConnection;
    private final ConnectionTerminator quicConnTerminator;
    private final SocketAddress peerAddress;
    private final String dbgTag;
    private final Logger debug;
    private final UniStreamPair controlStreams;
    private final UniStreamPair encoderStreams;
    private final UniStreamPair decoderStreams;
    private final Encoder qpackEncoder;
    private final Decoder qpackDecoder;
    private final FramesDecoder controlFramesDecoder;
    private final AtomicLong nextPushId = new AtomicLong();
    private volatile long maxPushId = 0;
    private final ReentrantLock pushIdLock = new ReentrantLock();
    private final Condition pushIdChanged = pushIdLock.newCondition();
    private final ConcurrentHashMap<Long, CompletableFuture<Http3ServerStreamImpl>> requests =
            new ConcurrentHashMap<>();
    private volatile boolean closeRequested;
    // the max stream id of a processed H3 request. -1 implies none were processed.
    private final AtomicLong maxProcessedRequestStreamId = new AtomicLong(-1);
    // the stream id that was sent in a GOAWAY frame. -1 implies no GOAWAY frame was sent.
    private final AtomicLong goAwayRequestStreamId = new AtomicLong(-1);

    private final CompletableFuture<QuicSenderStream> afterSettings = new MinimalFuture<>();
    private final CompletableFuture<ConnectionSettings> clientSettings = new MinimalFuture<>();

    private final ConcurrentLinkedQueue<ByteBuffer> lcsWriterQueue =
            new ConcurrentLinkedQueue<>();

    // A class used to dispatch peer initiated unidirectional streams
    // according to their type.
    private final class Http3StreamDispatcher extends PeerUniStreamDispatcher {
        Http3StreamDispatcher(QuicReceiverStream stream) {
            super(stream);
        }

        @Override
        protected Logger debug() {
            return debug;
        }

        @Override
        protected void onStreamAbandoned(QuicReceiverStream stream) {
            if (debug.on()) debug.log("Stream " + stream.streamId() + " abandoned!");
            qpackDecoder.cancelStream(stream.streamId());
        }

        @Override
        protected void onControlStreamCreated(String description, QuicReceiverStream stream) {
            if (debug.on()) {
                debug.log("peerControlStream %s dispatched", stream.streamId());
            }
            complete(description, stream, controlStreams.futureReceiverStream());
        }

        @Override
        protected void onEncoderStreamCreated(String description, QuicReceiverStream stream) {
            if (debug.on()) debug.log("peer opened QPack encoder stream");
            complete(description, stream, decoderStreams.futureReceiverStream());
        }

        @Override
        protected void onDecoderStreamCreated(String description, QuicReceiverStream stream) {
            if (debug.on()) debug.log("peer opened QPack decoder stream");
            complete(description, stream, encoderStreams.futureReceiverStream());
        }

        @Override
        protected void onPushStreamCreated(String description, QuicReceiverStream stream, long pushId) {
            // From RFC 9114:
            // Only servers can push; if a server receives a client-initiated push stream,
            // this MUST be treated as a connection error of type H3_STREAM_CREATION_ERROR.
            close(H3_STREAM_CREATION_ERROR.code(),
                    "Push Stream %s opened by client"
                            .formatted(stream.streamId()));
        }

        // completes the given completable future with the given stream
        private void complete(String description, QuicReceiverStream stream,
                              CompletableFuture<QuicReceiverStream> cf) {
            if (debug.on()) {
                debug.log("completing CF for %s with stream %s", description, stream.streamId());
            }
            boolean completed = cf.complete(stream);
            if (!completed) {
                if (!cf.isCompletedExceptionally()) {
                    debug.log("CF for %s already completed with stream %s!", description, cf.resultNow().streamId());
                    close(Http3Error.H3_STREAM_CREATION_ERROR,
                            "%s already created".formatted(description));
                } else {
                    debug.log("CF for %s already completed exceptionally!", description);
                }
            }
        }

        static CompletableFuture<QuicReceiverStream> dispatch(Http3ServerConnection conn,
                                                              QuicReceiverStream stream) {
            var dispatcher = conn.new Http3StreamDispatcher(stream);
            dispatcher.start();
            return dispatcher.dispatchCF();
        }
    }

    /**
     * Creates a new {@code Http3ServerConnection}.
     * Once created, the connection must be {@linkplain #start()} started.
     * @param server     the HTTP/3 server creating this connection
     * @param connection the underlying Quic connection
     */
    Http3ServerConnection(Http3TestServer server,
                          QuicServerConnection connection,
                          SocketAddress peerAddress) {
        this.server = server;
        this.quicConnection = connection;
        this.quicConnTerminator = connection.connectionTerminator();
        this.peerAddress = peerAddress;
        var qtag = connection.dbgTag();
        dbgTag = "H3-Server(" + qtag + ")";
        debug = Utils.getDebugLogger(this::dbgTag);
        controlFramesDecoder = new FramesDecoder("H3-Server-control("+qtag+")",
                FramesDecoder::isAllowedOnClientControlStream);
        controlStreams = new UniStreamPair(StreamType.CONTROL,
                quicConnection, this::receiveControlBytes,
                this::lcsWriterLoop,
                this::onControlStreamError, debug);
        qpackEncoder = new Encoder(this::qpackInsertionPolicy,
                                   this::createEncoderStreams,
                                   this::connectionError);
        encoderStreams = qpackEncoder.encoderStreams();
        qpackDecoder = new Decoder(this::createDecoderStreams,
                                   this::connectionError);
        decoderStreams = qpackDecoder.decoderStreams();
    }

    boolean qpackInsertionPolicy(TableEntry entry) {
        List<String> allowedHeaders = Http3TestServer.ENCODER_ALLOWED_HEADERS;
        if (allowedHeaders.isEmpty()) {
            return false;
        }
        if (allowedHeaders.contains(Http3TestServer.ALL_ALLOWED)) {
            return true;
        }
        return allowedHeaders.contains(entry.name());
    }

    /**
     * Starts this {@code Http3ServerConnection}.
     */
    public void start() {
        quicConnection.addRemoteStreamListener(this::onNewRemoteStream);
        quicConnection.onHandshakeCompletion(this::handshakeDone);
    }

    // push bytes to the local control stream queue
    void writeControlStream(ByteBuffer buffer) {
        lcsWriterQueue.add(buffer);
        controlStreams.localWriteScheduler().runOrSchedule();
    }

    QuicConnectionImpl quicConnection() {
        return this.quicConnection;
    }

    Http3TestServer server() {
        return this.server;
    }

    String connectionKey() {
        // assuming the localConnectionId never changes;
        // this will return QuicServerConnectionId(NNN), which should
        // be enough to detect whether two exchanges are made on the
        // same connection
        return quicConnection.logTag();
    }

    // The local control stream write loop
    private void lcsWriterLoop() {
        var controlStreams = this.controlStreams;
        if (controlStreams == null) return;
        var writer = controlStreams.localWriter();
        if (writer == null) return;
        ByteBuffer buffer;
        if (debug.on())
            debug.log("start control writing loop: credit=" + writer.credit());
        while (writer.credit() > 0 && (buffer = lcsWriterQueue.poll()) != null) {
            try {
                if (debug.on())
                    debug.log("schedule %s bytes for writing on control stream", buffer.remaining());
                writer.scheduleForWriting(buffer, buffer == QuicStreamReader.EOF);
            } catch (Throwable t) {
                var stream = writer.stream();
                Http3Streams.debugErrorCode(debug, stream, "Control stream");
                if (!closeRequested && quicConnection.isOpen()) {
                    if (!Http3Error.isNoError(stream.sndErrorCode())) {
                        if (debug.on()) debug.log("Failed to write to control stream", t);
                    }
                    close(Http3Error.H3_CLOSED_CRITICAL_STREAM, "Failed to write to control stream");
                    return;
                }
            }
        }
    }

    private boolean hasHttp3Error(QuicStream stream) {
        if (stream instanceof QuicReceiverStream rcvs) {
            var code = rcvs.rcvErrorCode();
            if (code > 0 && !Http3Error.isNoError(code)) return true;
        }
        if (stream instanceof QuicSenderStream snds) {
            var code = snds.sndErrorCode();
            if (code > 0 && !Http3Error.isNoError(code)) return true;
        }
        return false;
    }


    private void onControlStreamError(final QuicStream stream, final UniStreamPair uniStreamPair,
                                      final Throwable throwable) {
        // TODO: implement this!
        try {
            Http3Streams.debugErrorCode(debug, stream, "Control stream");
            if (!closeRequested && quicConnection.isOpen()) {
                if (hasHttp3Error(stream)) {
                    if (debug.on()) {
                        debug.log("control stream " + stream.mode() + " failed", throwable);
                    }
                }
                close(Http3Error.H3_CLOSED_CRITICAL_STREAM,
                        "Control stream " + stream.mode() + " failed");
            }
        } catch (Throwable t) {
            if (debug.on() && !closeRequested) {
                debug.log("onControlStreamError: handling ", throwable);
                debug.log("onControlStreamError: exception while handling error: ", t);
            }
        }
    }

    private void receiveControlBytes(ByteBuffer buffer) {
        if (debug.on()) debug.log("received client control: %s bytes", buffer.remaining());
        controlFramesDecoder.submit(buffer);
        Http3Frame frame;
        while ((frame = controlFramesDecoder.poll()) != null) {
            if (debug.on()) debug.log("client control frame: %s", frame);
            if (frame instanceof MalformedFrame malformed) {
                var cause = malformed.getCause();
                if (cause != null && debug.on()) {
                    debug.log(malformed.toString(), cause);
                }
                close(malformed.getErrorCode(), malformed.getMessage());
                controlStreams.stopSchedulers();
                controlFramesDecoder.clear();
                return;
            } else if (frame instanceof PartialFrame) {
                var payloadBytes = controlFramesDecoder.readPayloadBytes();
                if (debug.on()) {
                    debug.log("added %s bytes to %s",
                            Utils.remaining(payloadBytes),
                            frame);
                }
            } else if (frame instanceof CancelPushFrame cpf) {
                cancelPushReceived(cpf.getPushId());
            } else if (frame instanceof MaxPushIdFrame mpf) {
                maxPushIdReceived(mpf.getMaxPushId());
            } else if (frame instanceof SettingsFrame sf) {
                ConnectionSettings clientSettings = ConnectionSettings.createFrom(sf);
                // Set max and current capacity of the QPack encoder
                qpackEncoder.configure(clientSettings);
                long clientMaxTableCapacity = clientSettings.qpackMaxTableCapacity();
                long capacity = Math.min(Http3TestServer.ENCODER_CAPACITY_LIMIT,
                                         clientMaxTableCapacity);
                // RFC9204 3.2.3. Maximum Dynamic Table Capacity:
                // "When the maximum table capacity is zero, the encoder MUST NOT
                // insert entries into the dynamic table and MUST NOT send any encoder
                // instructions on the encoder stream."
                if (clientMaxTableCapacity != 0) {
                    qpackEncoder.setTableCapacity(capacity);
                }
                this.clientSettings.complete(clientSettings);
            }
            if (controlFramesDecoder.eof()) break;
        }
        if (controlFramesDecoder.eof()) {
            close(Http3Error.H3_CLOSED_CRITICAL_STREAM,
                    "EOF reached while reading client control stream");
        }
    }

    private void handshakeDone(Throwable t) {
        if (t == null) {
            controlStreams.futureSenderStream()
                    .thenApply(this::sendSettings)
                    .exceptionally(this::exceptionallyAndClose)
                    .thenApply(afterSettings::complete);
        } else {
            if (debug.on()) debug.log("Handshake failed: " + t, t);
            // the connection is probably closed already, but just in case...
            close(Http3Error.H3_INTERNAL_ERROR, "Handshake failed");
        }
    }

    private <T> T exceptionallyAndClose(Throwable t) {
        try {
            return exceptionally(t);
        } finally {
            // TODO: should we distinguish close due to
            //       exception from graceful close?
            close(Http3Error.H3_INTERNAL_ERROR, message(t));
        }
    }

    String message(Throwable t) {
        return t == null ? "No Error" : t.getClass().getSimpleName();
    }

    private <T> T exceptionally(Throwable t) {
        try {
            if (debug.on()) debug.log(t.getMessage(), t);
            throw t;
        } catch (RuntimeException | Error r) {
            throw r;
        } catch (ExecutionException x) {
            throw new CompletionException(x.getMessage(), x.getCause());
        } catch (Throwable e) {
            throw new CompletionException(e.getMessage(), e);
        }
    }

    private QuicSenderStream sendSettings(final QuicSenderStream localControlStream) {
        final ConnectionSettings settings = server.getConfiguredConnectionSettings();
        final SettingsFrame settingsFrame = new SettingsFrame();

        settingsFrame.setParameter(SETTINGS_MAX_FIELD_SECTION_SIZE, settings.maxFieldSectionSize());
        settingsFrame.setParameter(SETTINGS_QPACK_MAX_TABLE_CAPACITY, settings.qpackMaxTableCapacity());
        settingsFrame.setParameter(SETTINGS_QPACK_BLOCKED_STREAMS, settings.qpackBlockedStreams());
        qpackDecoder.configure(settings);

        if (debug.on()) {
            debug.log("sending server settings %s for connection %s", settingsFrame, this);
        }
        final long size = settingsFrame.size();
        assert size >= 0 && size < Integer.MAX_VALUE;
        var buf = ByteBuffer.allocate((int)settingsFrame.size());
        settingsFrame.writeFrame(buf);
        buf.flip();
        writeControlStream(buf);
        return localControlStream;
    }


    private boolean onNewRemoteStream(QuicReceiverStream stream) {
        boolean closeRequested = this.closeRequested;
        if (closeRequested) return false;

        if (stream instanceof QuicBidiStream bidiStream) {
            onNewHttpRequest(bidiStream);
        } else {
            Http3StreamDispatcher.dispatch(this, stream).whenComplete((r, t) -> {
                if (t != null) dispatchFailed(t);
            });
        }
        if (debug.on()) {
            debug.log("New stream %s accepted", stream.streamId());
        }
        return true;
    }

    private void onNewHttpRequest(QuicBidiStream stream) {
        if (!this.server.shouldProcessNewHTTPRequest(this)) {
            if (debug.on()) {
                debug.log("Rejecting HTTP request on stream %s of connection %s",
                        stream.streamId(), this);
            }
            // consider the request as unprocessed and send a GOAWAY on the connection
            try {
                sendGoAway();
            } catch (IOException ioe) {
                System.err.println("Failed to send GOAWAY on connection " + this
                        + " due to: " + ioe);
                ioe.printStackTrace();
            }
            return;
        }
        var streamId = stream.streamId();
        // keep track of the largest request id that we have processed
        long currentLargest = maxProcessedRequestStreamId.get();
        while (streamId > currentLargest) {
            if (maxProcessedRequestStreamId.compareAndSet(currentLargest, streamId)) {
                break;
            }
            currentLargest = maxProcessedRequestStreamId.get();
        }
        if (debug.on()) {
            debug.log("new incoming HTTP request on stream %s", streamId);
        }
        if (requests.containsKey(stream.streamId())) {
            if (debug.on()) {
                debug.log("Stream %s already created!", streamId);
            }
            quicConnTerminator.terminate(appLayerClose(H3_STREAM_CREATION_ERROR.code())
                    .loggedAs("stream already created"));
            return;
        }
        // creation of the Http3ServerExchangeImpl involves connecting its reader, which
        // takes a fair amount of (JIT?) time. Since this method is called from
        // within the decrypt loop, it prevents decrypting the following ONERTT packets,
        // which can unnecessarily delay the processing of ACKs and cause excessive
        // retransmission
        MinimalFuture<Http3ServerStreamImpl> exchCf = new MinimalFuture<>();
        requests.put(stream.streamId(), exchCf);
        if (debug.on()) {
            debug.log("HTTP/3 exchange future for stream %s registered", streamId);
        }
        server.getQuicServer().executor().execute(() -> createExchange(exchCf, stream));
        if (debug.on()) {
            debug.log("HTTP/3 exchange creation for stream %s triggered", streamId);
        }
    }

    private void createExchange(CompletableFuture<Http3ServerStreamImpl> exchCf,
                                QuicBidiStream stream) {
        var streamId = stream.streamId();
        if (debug.on()) {
            debug.log("Completing HTTP/3 exchange future for stream %s", streamId);
        }
        exchCf.complete(new Http3ServerStreamImpl(this, stream));
        if (debug.on()) {
            debug.log("HTTP/3 exchange future for stream %s Completed", streamId);
        }
    }

    public final String dbgTag() { return dbgTag; }

    private void dispatchFailed(Throwable throwable) {
        // TODO: anything to do?
        if (debug.on()) debug.log("dispatch failed: " + throwable);
    }

    QueuingStreamPair createEncoderStreams(Consumer<ByteBuffer> encoderReceiver) {
        return new QueuingStreamPair(StreamType.QPACK_ENCODER, quicConnection,
                encoderReceiver, this::onEncoderStreamsFailed, debug);
    }

    private void onEncoderStreamsFailed(final QuicStream stream, final UniStreamPair uniStreamPair,
                                        final Throwable throwable) {
        // TODO: implement this!
        // close connection here.
        if (!closeRequested) {
            String message = stream != null ? stream.mode() + " failed" : "is null";
            if (quicConnection().isOpen()) {
                if (debug.on()) {
                    debug.log("QPack encoder stream " + message, throwable);
                }
            } else {
                if (debug.on()) {
                    debug.log("QPack encoder stream " + message + ": " + throwable);
                }
            }
        }
    }

    QueuingStreamPair createDecoderStreams(Consumer<ByteBuffer> encoderReceiver) {
        return new QueuingStreamPair(StreamType.QPACK_DECODER, quicConnection,
                encoderReceiver, this::onDecoderStreamsFailed, debug);
    }

    private void onDecoderStreamsFailed(final QuicStream stream, final UniStreamPair uniStreamPair,
                                        final Throwable throwable) {
        // TODO: implement this!
        // close connection here.
        if (!closeRequested) {
            String message = stream != null ? stream.mode() + " failed" : "is null";
            if (quicConnection().isOpen()) {
                if (debug.on()) {
                    debug.log("QPack decoder stream " + message, throwable);
                }
            } else {
                debug.log("QPack decoder stream " + message + ": " + throwable);
            }
        }
    }

    // public, to allow invocations from within tests
    public void sendGoAway() throws IOException {
        final QuicStreamWriter writer = controlStreams.localWriter();
        if (writer == null || !quicConnection.isOpen()) {
            return;
        }
        // RFC-9114, section 5.2:
        // Requests ... with the indicated identifier or greater
        // are rejected ... by the sender of the GOAWAY.
        final long maxProcessedStreamId = maxProcessedRequestStreamId.get();
        // adding 4 gets us the next stream id for the stream type
        final long streamIdToReject = maxProcessedStreamId == -1 ? 0 : maxProcessedStreamId + 4;
        // An endpoint MAY send multiple GOAWAY frames indicating different
        // identifiers, but the identifier in each frame MUST NOT be greater
        // than the identifier in any previous frame, since clients might
        // already have retried unprocessed requests on another HTTP connection.
        long currentGoAwayReqStrmId = goAwayRequestStreamId.get();
        while (currentGoAwayReqStrmId != -1 && streamIdToReject < currentGoAwayReqStrmId) {
            if (goAwayRequestStreamId.compareAndSet(currentGoAwayReqStrmId, streamIdToReject)) {
                break;
            }
            currentGoAwayReqStrmId = goAwayRequestStreamId.get();
        }
        final GoAwayFrame frame = new GoAwayFrame(streamIdToReject);
        final long size = frame.size();
        assert size >= 0 && size < Integer.MAX_VALUE;
        final var buf = ByteBuffer.allocate((int) size);
        frame.writeFrame(buf);
        buf.flip();
        if (debug.on()) {
            debug.log("Sending GOAWAY frame %s from server connection %s", frame, this);
        }
        writer.scheduleForWriting(buf, false);
    }

    public void close(Http3Error error, String reason) {
        close(error.code(), reason);
    }

    void connectionError(Throwable throwable, Http3Error error) {
        close(error, throwable.getMessage());
    }

    private boolean markCloseRequested() {
        var closeRequested = this.closeRequested;
        if (!closeRequested) {
            synchronized (this) {
                closeRequested = this.closeRequested;
                if (!closeRequested) {
                    return this.closeRequested = true;
                }
            }
        }
        assert closeRequested;
        if (debug.on()) debug.log("close already requested");
        return false;
    }

    public void close(long error, String reason) {
        if (markCloseRequested()) {
            try {
                sendGoAway();
            } catch (IOException e) {
                // it's OK if we couldn't send a GOAWAY
                if (debug.on()) {
                    debug.log("ignoring failure to send GOAWAY from server connection "
                            + this + " due to " + e);
                }
            }
            if (quicConnection.isOpen()) {
                if (debug.on()) debug.log("closing quic connection: " + reason);
                quicConnTerminator.terminate(appLayerClose(error).loggedAs(reason));
            } else {
                if (debug.on()) debug.log("quic connection already closed");
            }
        }
    }

    HeaderFrameReader newHeaderFrameReader(DecodingCallback decodingCallback) {
        return qpackDecoder.newHeaderFrameReader(decodingCallback);
    }

    void exchangeClosed(Http3ServerStreamImpl http3ServerExchange) {
        requests.remove(http3ServerExchange.streamId());
    }

    sealed interface PushPromise permits CancelledPush, CompletedPush, PendingPush {
        long pushId();
    }

    record CancelledPush(long pushId) implements PushPromise {}
    record CompletedPush(long pushId, HttpHeaders headers) implements PushPromise {}
    record PendingPush(long pushId,
                               CompletableFuture<QuicSenderStream> stream,
                               HttpHeaders headers,
                               Http3ServerExchange exchange) implements PushPromise {
    }

    private final Map<Long, PushPromise> promiseMap = new ConcurrentHashMap<>();

    PushPromise addPendingPush(long pushId,
                               CompletableFuture<QuicSenderStream> stream,
                               HttpHeaders headers,
                               Http3ServerExchange exchange) {
        var push = new PendingPush(pushId, stream, headers, exchange);
        expungePromiseMap();
        var previous = promiseMap.putIfAbsent(pushId, push);
        if (previous == null || !(previous instanceof CancelledPush)) {
            // allow to open multiple streams for the same pushId
            // in order to test client behavior. We will return
            // push even if the map contains a pending or completed
            // push;
            return push;
        }
        return previous;
    }

    void addPushPromise(final long promiseId, final PushPromise promise) {
        this.promiseMap.put(promiseId, promise);
    }

    PushPromise getPushPromise(final long promiseId) {
        return this.promiseMap.get(promiseId);
    }

    void cancelPush(long pushId) {
        expungePromiseMap();
        var push = promiseMap.putIfAbsent(pushId, new CancelledPush(pushId));
        if (push == null || push instanceof CancelledPush) return;
        if (push instanceof CompletedPush) return;
        if (push instanceof PendingPush pp) {
            promiseMap.put(pushId, new CancelledPush(pushId));
            var ps = pp.stream();
            if (ps == null) {
                try {
                    sendCancelPush(pushId);
                } catch (IOException io) {
                    if (debug.on()) {
                        debug.log("Failed to send CANCEL_PUSH pushId=%s: %s", pushId, io);
                    }
                }
            } else {
                ps.thenAccept(s -> {
                    try {
                        s.reset(Http3Error.H3_REQUEST_CANCELLED.code());
                    } catch (IOException io) {
                        if (debug.on()) {
                            debug.log("Failed to reset push stream pushId=%s, stream=%s: %s",
                                    pushId, s.streamId(), io);
                        }
                    }
                });
            }
        }
    }

    void sendCancelPush(long pushId) throws IOException {
        CancelPushFrame cancelPushFrame = new CancelPushFrame(pushId);
        ByteBuffer buf = ByteBuffer.allocate((int)cancelPushFrame.size());
        cancelPushFrame.writeFrame(buf);
        buf.flip();
        // need to wait until after settings are sent.
        afterSettings.thenAccept((s) -> writeControlStream(buf));
    }


    void cancelPushReceived(long pushId) {
        cancelPush(pushId);
    }

    void maxPushIdReceived(long pushId) {
        pushIdLock.lock();
        try {
            if (pushId > maxPushId) {
                if (debug.on()) debug.log("max pushId: " + pushId);
                maxPushId = pushId;
                pushIdChanged.signalAll();
            }
        } finally {
            pushIdLock.unlock();
        }
    }

    final AtomicLong minPush = new AtomicLong();
    static final int MAX_PUSH_HISTORY = 100;
    void expungePromiseMap() {
        assert MAX_PUSH_HISTORY > 0;
        while (promiseMap.size() >= MAX_PUSH_HISTORY) {
            long lowest = minPush.getAndIncrement();
            var pp = promiseMap.remove(lowest);
            if (pp instanceof PendingPush ppp) {
                cancelPush(ppp.pushId);
            }
        }
    }

    List<ByteBuffer> encodeHeaders(int bufferSize, long streamId, HttpHeaders... headers) {
        HeaderFrameWriter writer = qpackEncoder.newHeaderFrameWriter();
        return qpackEncoder.encodeHeaders(writer, streamId, bufferSize, headers);
    }

    void decodeHeaders(final HeadersFrame partialHeadersFrame, final ByteBuffer buffer,
                       final HeaderFrameReader headersReader) throws IOException {
        ByteBuffer received = partialHeadersFrame.nextPayloadBytes(buffer);
        boolean done = partialHeadersFrame.remaining() == 0;
        this.qpackDecoder.decodeHeader(received, done, headersReader);
    }

    long nextPushId() {
        return this.nextPushId.getAndIncrement();
    }

    long waitForMaxPushId(long pushId) throws InterruptedException {
        long maxPushId = this.maxPushId;
        if (maxPushId > pushId) return maxPushId;
        do {
            this.pushIdLock.lock();
            try {
                maxPushId = this.maxPushId;
                if (maxPushId > pushId) return maxPushId;
                this.pushIdChanged.await();
            } finally {
                this.pushIdLock.unlock();
            }
        } while (true);
    }

    public Encoder qpackEncoder() {
        return qpackEncoder;
    }

    public CompletableFuture<ConnectionSettings> clientHttp3Settings() {
        return clientSettings;
    }
}
