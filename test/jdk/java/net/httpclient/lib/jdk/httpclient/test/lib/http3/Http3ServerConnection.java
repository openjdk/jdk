/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

import javax.net.ssl.SSLSession;

import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.common.ValidatingHeadersConsumer;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.CancelPushFrame;
import jdk.internal.net.http.http3.frames.DataFrame;
import jdk.internal.net.http.http3.frames.FramesDecoder;
import jdk.internal.net.http.http3.frames.GoAwayFrame;
import jdk.internal.net.http.http3.frames.HeadersFrame;
import jdk.internal.net.http.http3.frames.Http3Frame;
import jdk.internal.net.http.http3.frames.MalformedFrame;
import jdk.internal.net.http.http3.frames.MaxPushIdFrame;
import jdk.internal.net.http.http3.frames.PartialFrame;
import jdk.internal.net.http.http3.frames.PushPromiseFrame;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.http3.frames.UnknownFrame;
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
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;
import jdk.internal.net.http.quic.BuffersReader;
import jdk.internal.net.http.quic.BuffersReader.ListBuffersReader;

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
    private final Set<PeerUniStreamDispatcher> dispatchers = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextPushId = new AtomicLong();
    private volatile long maxPushId = 0;
    private final ReentrantLock pushIdLock = new ReentrantLock();
    private final Condition pushIdChanged = pushIdLock.newCondition();
    private volatile boolean closeRequested;
    // the max stream id of a processed H3 request. -1 implies none were processed.
    private final AtomicLong maxProcessedRequestStreamId = new AtomicLong(-1);
    // the stream id that was sent in a GOAWAY frame. -1 implies no GOAWAY frame was sent.
    private final AtomicLong goAwayRequestStreamId = new AtomicLong(-1);

    private final CompletableFuture<QuicSenderStream> afterSettings = new MinimalFuture<>();


    private final ConcurrentLinkedQueue<ByteBuffer> lcsWriterQueue =
            new ConcurrentLinkedQueue<>();

    // A class used to dispatch peer initiated unidirectional streams
    // according to their type.
    private final class Http3StreamDispatcher extends PeerUniStreamDispatcher {
        Http3StreamDispatcher(QuicReceiverStream stream) {
            super(dispatchers, stream);
        }

        @Override
        protected Logger debug() {
            return debug;
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
            if (cf.isDone()) {
                if (debug.on()) debug.log("CF for %s already completed!", description);
                if (!cf.isCompletedExceptionally() && cf.resultNow() != stream) {
                    close(H3_STREAM_CREATION_ERROR,
                            "%s already created".formatted(description));
                }
            }
            cf.complete(stream);
        }

        static CompletableFuture<QuicReceiverStream> dispatch(Http3ServerConnection conn,
                                                              QuicReceiverStream stream) {
            var dispatcher = conn.new Http3StreamDispatcher(stream);
            dispatcher.start();
            return conn.closeIfRequested(dispatcher).dispatchCF();
        }
    }

    private Http3StreamDispatcher closeIfRequested(Http3StreamDispatcher dispatcher) {
        synchronized (this) {
            if (closeRequested) {
                // OK - does not log
                dispatcher.stop();
            }
        }
        return dispatcher;
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
                if (!closeRequested && Http3Streams.hasError(stream)) {
                    if (debug.on()) debug.log("Failed to write to control stream", t);
                    close(Http3Error.H3_CLOSED_CRITICAL_STREAM, "Failed to write to control stream");
                }
            }
        }
    }


    private void onControlStreamError(final QuicStream stream, final UniStreamPair uniStreamPair,
                                      final Throwable throwable) {
        // TODO: implement this!
        try {
            Http3Streams.debugErrorCode(debug, stream, "Control stream");
            if (!closeRequested && Http3Streams.hasError(stream)) {
                if (debug.on()) {
                    debug.log("control stream " + stream.mode() + " failed", throwable);
                }
            }
        } catch (Throwable t) {
            if (debug.on()) {
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
                // TODO: we might need client settings, maybe we should save it similar
                //  to HTTP/2 test server
                ConnectionSettings clientSettings = ConnectionSettings.createFrom(sf);
                // Set max and current capacity of the QPack encoder
                qpackEncoder.configure(clientSettings);
                long capacity = Math.min(Http3TestServer.ENCODER_CAPACITY_LIMIT,
                                         clientSettings.qpackMaxTableCapacity());
                qpackEncoder.setTableCapacity(capacity);
            }
            if (controlFramesDecoder.eof()) break;
        }
        if (controlFramesDecoder.eof()) {
            // TODO: close connection unless it's already closed?
            //       Error should be H3_CRITICAL_STREAM_CLOSED
            if (debug.on()) {
                debug.log("EOF reached while reading client control stream");
            }
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

    private String message(Throwable t) {
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

    final ConcurrentHashMap<Long, CompletableFuture<Http3ServerStreamImpl>> requests =
            new ConcurrentHashMap<>();
    private void onNewHttpRequest(QuicBidiStream stream) {
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
        exchCf.complete(new Http3ServerStreamImpl(stream));
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
            if (debug.on()) {
                debug.log("QPack encoder stream " + message, throwable);
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
            if (debug.on()) {
                debug.log("QPack decoder stream " + message, throwable);
            }
        }
    }

    private void sendGoAway() throws IOException {
        final QuicStreamWriter writer = controlStreams.localWriter();
        if (writer == null || !quicConnection.isOpen()) {
            return;
        }
        // RFC-9114, section 5.2:
        // Requests ... with the indicated identifier or greater
        // are rejected ... by the sender of the GOAWAY.
        final long streamIdToReject = maxProcessedRequestStreamId.get() + 1;
        // An endpoint MAY send multiple GOAWAY frames indicating different
        // identifiers, but the identifier in each frame MUST NOT be greater
        // than the identifier in any previous frame, since clients might
        // already have retried unprocessed requests on another HTTP connection.
        long currentGoAwayReqStrmId = goAwayRequestStreamId.get();
        while (streamIdToReject < currentGoAwayReqStrmId) {
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
            } finally {
                for (PeerUniStreamDispatcher dispatcher : dispatchers) {
                    dispatcher.stop();
                }
            }
        }
    }

    private HeaderFrameReader newHeaderFrameReader(DecodingCallback decodingCallback) {
        return qpackDecoder.newHeaderFrameReader(decodingCallback);
    }

    class Http3ServerStreamImpl {
        final QuicBidiStream stream;
        final SequentialScheduler readScheduler = SequentialScheduler.lockingScheduler(this::readLoop);
        final SequentialScheduler writeScheduler = SequentialScheduler.lockingScheduler(this::writeLoop);
        final QuicStreamReader reader;
        final QuicStreamWriter writer;
        final ListBuffersReader incoming = BuffersReader.list();
        final HeaderFrameReader qpackReader;
        final HttpHeadersBuilder requestHeadersBuilder;
        final ReentrantLock writeLock = new ReentrantLock();
        final Condition writeEnabled = writeLock.newCondition();
        final CompletableFuture<HttpHeaders> requestHeadersCF = new MinimalFuture<>();
        final CompletableFuture<Http3ExchangeImpl> exchangeCF;
        final BlockingQueue<ByteBuffer> requestBodyQueue = new LinkedBlockingQueue<>();
        private volatile boolean eof;

        volatile PartialFrame partialFrame;
        Http3ServerStreamImpl(QuicBidiStream stream) {
            this.stream = stream;
            requestHeadersBuilder = new HttpHeadersBuilder();
            qpackReader = newHeaderFrameReader(new HeadersConsumer());
            writer = stream.connectWriter(writeScheduler);
            reader = stream.connectReader(readScheduler);
            exchangeCF = requestHeadersCF.thenApply(this::startExchange);
            // TODO: add a start() method that calls reader.start(), and
            //       call it outside of the constructor
            reader.start();
        }

        private void readLoop() {
            ByteBuffer buffer;

            // reader can be null if the readLoop is invoked
            // before reader is assigned.
            if (reader == null) return;

            if (debug.on()) {
                debug.log("H3Server: entering readLoop(stream=%s)", stream.streamId());
            }
            try {
                while (!reader.isReset() && (buffer = reader.poll()) != null) {
                    if (buffer == QuicStreamReader.EOF) {
                        if (debug.on()) {
                            debug.log("H3Server: EOF on stream=" + stream.streamId());
                        }
                        if (!eof) requestBodyQueue.add(buffer);
                        eof = true;
                        return;
                    }
                    if (debug.on()) {
                        debug.log("H3Server: got %s bytes on stream %s", buffer.remaining(), stream.streamId());
                    }

                    var partialFrame = this.partialFrame;
                    if (partialFrame != null && partialFrame.remaining() == 0) {
                        this.partialFrame = partialFrame = null;
                    }
                    if (partialFrame instanceof HeadersFrame partialHeaders) {
                        receiveHeaders(partialHeaders, buffer);
                    } else if (partialFrame instanceof DataFrame partialData) {
                        receiveData(partialData, buffer);
                    } else if (partialFrame != null) {
                        partialFrame.nextPayloadBytes(buffer);
                    }
                    if (!buffer.hasRemaining()) {
                        continue;
                    }

                    incoming.add(buffer);
                    Http3Frame frame = Http3Frame.decode(incoming, FramesDecoder::isAllowedOnRequestStream, debug);
                    if (frame == null) continue;
                    if (frame instanceof PartialFrame partial) {
                        this.partialFrame = partialFrame = partial;
                        if (frame instanceof HeadersFrame partialHeaders) {
                            if (debug.on()) {
                                debug.log("H3Server Got headers: " + frame + " on stream=" + stream.streamId());
                            }
                            long remaining = partial.remaining();
                            long available = incoming.remaining();
                            long read = Math.min(remaining, available);
                            if (read > 0) {
                                for (ByteBuffer buf : incoming.getAndRelease(read)) {
                                    receiveHeaders(partialHeaders, buf);
                                }
                            }
                        } else if (frame instanceof DataFrame partialData) {
                            if (debug.on()) {
                                debug.log("H3Server Got request body: " + frame + " on stream=" + stream.streamId());
                            }
                            long remaining = partial.remaining();
                            long available = incoming.remaining();
                            long read = Math.min(remaining, available);
                            if (read > 0) {
                                for (ByteBuffer buf : incoming.getAndRelease(read)) {
                                    receiveData(partialData, buf);
                                }
                            }

                        } else if (frame instanceof UnknownFrame unknown) {
                            unknown.nextPayloadBytes(incoming);
                        } else {
                            if (debug.on()) {
                                debug.log("H3Server Got unexpected partial frame: "
                                        + frame + " on stream=" + stream.streamId());
                            }
                            // TODO: let the HTTP/3 connection close the quic connection?
                            Http3ServerConnection.this.close(Http3Error.H3_FRAME_UNEXPECTED,
                                    "unexpected frame type=" + frame.type()
                                            + " on stream=" + stream.streamId());
                            readScheduler.stop();
                            writeScheduler.stop();
                            return;
                        }
                    } else if (frame instanceof MalformedFrame malformed) {
                        if (debug.on()) {
                            debug.log("H3Server Got frame: " + frame + " on stream=" + stream.streamId());
                        }
                        Http3ServerConnection.this.close(malformed.getErrorCode(),
                                malformed.getMessage());
                        readScheduler.stop();
                        writeScheduler.stop();
                        return;
                    } else {
                        if (debug.on()) {
                            debug.log("H3Server Got frame: " + frame + " on stream=" + stream.streamId());
                        }
                    }
                }
                if (reader.isReset()) {
                    if (debug.on()) debug.log("H3 Server: stream %s reset", reader.stream().streamId());
                    readScheduler.stop();
                    resetReceived();
                }
                if (debug.on()) debug.log("H3 Server: exiting read loop");
            } catch (Throwable t) {
                if (debug.on()) debug.log("H3 Server: read loop failed: " + t);
                if (reader.isReset()) {
                    if (debug.on()) {
                        debug.log("H3 Server: stream %s reset", reader.stream());
                    }
                    readScheduler.stop();
                    resetReceived();
                } else {
                    if (debug.on()) {
                        debug.log("H3 Server: closing connection due to: " + t, t);
                    }
                    Http3ServerConnection.this.close(Http3Error.H3_INTERNAL_ERROR, message(t));
                    readScheduler.stop();
                    writeScheduler.stop();
                }
            }
        }

        String readErrorString(String defVal) {
            return Http3Streams.errorCodeAsString(reader.stream()).orElse(defVal);
        }

        void resetReceived() {
            // If stop_sending sent and reset received (implied by this method being called)
            // then exit normally and don't send a reset
            if (debug.on()) {
                debug.log("resetReceived: stream:%s, isStopSendingRequested:%s, errorCode:%s, isNoError:%s",
                        stream.streamId(), stream.isStopSendingRequested(), stream.rcvErrorCode(),
                        Http3Error.isNoError(reader.stream().rcvErrorCode()));
            }

            if (reader.stream().isStopSendingRequested()
                    && requestHeadersCF.isDone()) {
                // we can only request stop sending in the handler after having
                // parsed the headers, therefore, if requestHeadersCF is not
                // completed when we reach here we should reset the stream.

                // We have requested stopSending and received a reset in response:
                // nothing to do - let the response be sent to the client, but throw an
                // exception if `is` is used again.
                exchangeCF.thenApply(en -> {en.is.close(new IOException("stopSendingRequested")); return en;});
                return;
            }

            String msg = "Stream %s reset by peer: %s"
                        .formatted(streamId(), readErrorString("no error code"));
            if (debug.on()) debug.log("H3 Server: reset received: " + msg);
            var io = new IOException(msg);
            requestHeadersCF.completeExceptionally(io);
            exchangeCF.thenApply((e) -> e.streamResetByPeer(io));
        }

        void receiveHeaders(HeadersFrame partialHeadersFrame, ByteBuffer buffer) throws IOException {
            ByteBuffer received = partialHeadersFrame.nextPayloadBytes(buffer);
            boolean done = partialHeadersFrame.remaining() == 0;
            qpackDecoder.decodeHeader(received, done, qpackReader);
        }

        void receiveData(DataFrame partialDataFrame, ByteBuffer buffer) {
            if (debug.on()) {
                debug.log("receiving data: " + buffer.remaining() + " on stream=" + stream.streamId());
            }
            ByteBuffer received = partialDataFrame.nextPayloadBytes(buffer);
            requestBodyQueue.add(received);
        }

        void cancelPushFrameReceived(CancelPushFrame cancel) {
            cancelPush(cancel.getPushId());
        }

        class RequestBodyInputStream extends InputStream {
            volatile IOException error;
            volatile boolean closed;
            // uses an unbounded blocking queue in which the readrLoop
            // publishes the DataFrames payload...
            ByteBuffer current;
            // Use lock to avoid pinned threads on the blocking queue
            final ReentrantLock lock = new ReentrantLock();
            ByteBuffer current() throws IOException {
                lock.lock();
                try {
                    while (true) {
                        if (current != null && current.hasRemaining()) {
                            return current;
                        }
                        if (current == QuicStreamReader.EOF) return current;
                        try {
                            if (debug.on()) debug.log("Taking buffer from queue");
                            // Blocking call
                            current = requestBodyQueue.take();
                        } catch (InterruptedException e) {
                            var io = new InterruptedIOException();
                            Thread.currentThread().interrupt();
                            io.initCause(e);
                            close(io);
                            var error = this.error;
                            if (error != null) throw error;
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public int read() throws IOException {
                ByteBuffer buffer = current();
                if (buffer == QuicStreamReader.EOF) {
                    var error = this.error;
                    if (error == null) return -1;
                    throw error;
                }
                return buffer.get();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                Objects.checkFromIndexSize(off, len, b.length);
                int remaining = len;
                while (remaining > 0) {
                    ByteBuffer buffer = current();
                    if (buffer == QuicStreamReader.EOF)  {
                        if (len == remaining) {
                            var error = this.error;
                            if (error == null) return -1;
                            throw error;
                        } else return len - remaining;
                    }
                    int count = Math.min(buffer.remaining(), remaining);
                    buffer.get(b, off + (len - remaining), count);
                    remaining -= count;
                }
                return len - remaining;
            }

            @Override
            public void close() throws IOException {
                lock.lock();
                try {
                    if (closed) return;
                    closed = true;

                } finally {
                    lock.unlock();
                }
                if (debug.on()) debug.log("Closing request body input stream");
                requestBodyQueue.add(QuicStreamReader.EOF);
            }

            void close(IOException io) {
                lock.lock();
                try {
                    if (closed) return;
                    closed = true;
                    error = io;
                } finally {
                    lock.unlock();
                }
                if (debug.on()) {
                    debug.log("Closing request body input stream: " + io);
                }
                requestBodyQueue.clear();
                requestBodyQueue.add(QuicStreamReader.EOF);
            }
        }

        Http3ExchangeImpl startExchange(HttpHeaders headers) {
            Http3ExchangeImpl exchange = new Http3ExchangeImpl(headers,
                    new RequestBodyInputStream(),
                    quicConnection.getTLSEngine().getSession());
            try {
                server.submitExchange(exchange);
            } catch (Exception e) {
                try {
                    exchange.close(new IOException(e));
                } catch (IOException ex) {
                    if (debug.on()) debug.log("Failed to close exchange: " + ex);
                }
            }
            return exchange;
        }

        long streamId() {
            return stream.streamId();
        }

        Http3ServerConnection http3ServerConnection() {
            return Http3ServerConnection.this;
        }

        private void writeLoop() {
            writeLock.lock();
            try {
                writeEnabled.signalAll();
            } finally {
                writeLock.unlock();
            }
        }

        private final class Http3ExchangeImpl implements Http2TestExchange {

            final HttpHeaders requestHeaders;
            final String method;
            final String scheme;
            final String authority;
            final String path;
            final URI uri;
            final HttpHeadersBuilder rspheadersBuilder;
            final SSLSession sslSession;
            volatile long responseLength = 0; // 0 is unknown, -1 is 0
            volatile int responseCode;
            final RequestBodyInputStream is;
            final ResponseBodyOutputStream os;
            private boolean unknownReservedFrameAlreadySent;

            Http3ExchangeImpl(HttpHeaders requestHeaders, RequestBodyInputStream is, SSLSession sslSession) {
                this.requestHeaders = requestHeaders;
                this.sslSession = sslSession;
                this.is = is;
                this.os = new ResponseBodyOutputStream(connectionTag(), debug, writer, writeLock,
                        writeEnabled, this::getResponseLength);
                method = requestHeaders.firstValue(":method").orElse("");
                //System.out.println("method = " + method);
                path = requestHeaders.firstValue(":path").orElse("");
                //System.out.println("path = " + path);
                scheme =  requestHeaders.firstValue(":scheme").orElse("");
                //System.out.println("scheme = " + scheme);
                authority = requestHeaders.firstValue(":authority").orElse("");
                if (!path.isEmpty() && !path.startsWith("/")) {
                    throw new IllegalArgumentException("Path is not absolute: " + path);
                }
                uri = URI.create(scheme + "://" + authority + path);
                rspheadersBuilder = new HttpHeadersBuilder();
            }

            String connectionTag() {
                return quicConnection.logTag();
            }

            long getResponseLength() {
                return responseLength;
            }

            @Override
            public String toString() {
                return "H3Server Http3ExchangeImpl(%s)".formatted(streamId());
            }

            @Override
            public HttpHeaders getRequestHeaders() {
                return requestHeaders;
            }

            @Override
            public HttpHeadersBuilder getResponseHeaders() {
                return rspheadersBuilder;
            }

            @Override
            public URI getRequestURI() {
                return uri;
            }

            @Override
            public String getRequestMethod() {
                return method;
            }

            @Override
            public SSLSession getSSLSession() {
                return sslSession;
            }

            @Override
            public void close() {
                try {
                    is.close();
                    os.close();
                    Http3ServerStreamImpl.this.close();
                } catch (IOException e) {
                    if (debug.on()) {
                        debug.log(this + ".close exception: " + e, e);
                    }
                }
            }

            @Override
            public void close(IOException io) throws IOException {
                if (debug.on()) {
                    debug.log(this + " closed with exception: " + io);
                }
                if (writer.sendingState().isSending()) {
                    if (debug.on()) {
                        debug.log(this + " resetting writer with H3_INTERNAL_ERROR");
                    }
                    writer.reset(Http3Error.H3_INTERNAL_ERROR.code());
                }
                is.close(io);
                os.closeInternal();
                close();
            }

            public Http3ExchangeImpl streamResetByPeer(IOException io) {
                try {
                    if (debug.on()) debug.log("H3 Server closing exchange: " + io);
                    close(io);
                } catch (IOException e) {
                    if (debug.on()) debug.log("Failed to close stream %s", streamId());
                }
                return this;
            }

            @Override
            public InputStream getRequestBody() {
                return is;
            }

            @Override
            public OutputStream getResponseBody() {
                return os;
            }

            @Override
            public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
                // occasionally send an unknown/reserved HTTP3 frame to exercise the case
                // where the client is expected to ignore such frames
                optionallySendUnknownOrReservedFrame();
                this.responseLength = responseLength;
                sendResponseHeaders(streamId(), writer, isHeadRequest(),
                        rCode, responseLength, rspheadersBuilder, os);
            }

            // WARNING: this method is also called for PushStreams, which has
            //          a different writer, streamId, request etc...
            // The only fields that can be safely used in this method is debug and
            // http3ServerConnection
            private void sendResponseHeaders(long streamId,
                                             QuicStreamWriter writer,
                                             boolean isHeadRequest,
                                             int rCode,
                                             long responseLength,
                                             HttpHeadersBuilder rspheadersBuilder,
                                             ResponseBodyOutputStream os)
                    throws IOException {
                String tag = "streamId="+streamId+" ";
                if (responseLength !=0 && rCode != 204 && isHeadRequest) {
                    long clen = responseLength > 0 ? responseLength : 0;
                    rspheadersBuilder.setHeader("Content-length", Long.toString(clen));
                }
                final HttpHeadersBuilder pseudoHeadersBuilder = new HttpHeadersBuilder();
                pseudoHeadersBuilder.setHeader(":status", Integer.toString(rCode));
                final HttpHeaders pseudoHeaders = pseudoHeadersBuilder.build();
                final HttpHeaders headers = rspheadersBuilder.build();
                // order of headers matters - pseudo headers first followed by rest of the headers
                var payload = http3ServerConnection()
                        .encodeHeaders(1024, streamId, pseudoHeaders, headers);
                if (debug.on()) debug.log(tag + "headers payload: " + Utils.remaining(payload));
                HeadersFrame frame = new HeadersFrame(Utils.remaining(payload));
                ByteBuffer buffer = ByteBuffer.allocate(frame.headersSize());
                frame.writeHeaders(buffer);
                buffer.flip();
                if (debug.on()) {
                    debug.log(tag+"Writing HeaderFrame headers: " + Utils.asHexString(buffer));
                }
                boolean noBody = rCode >= 200 && (responseLength < 0 || rCode == 204);
                boolean last = frame.length() == 0 && noBody;
                if (last) {
                    if (debug.on()) {
                        debug.log(tag + "last payload sent: empty headers, no body");
                    }
                    writer.scheduleForWriting(buffer, true);
                } else {
                    writer.queueForWriting(buffer);
                }
                int size = payload.size();
                for (int i = 0; i < size; i++) {
                    last = i == size - 1;
                    var buf = payload.get(i);
                    if (debug.on()) {
                        debug.log(tag+"Writing HeaderFrame payload: " + Utils.asHexString(buf));
                    }
                    if (last) {
                        if (debug.on()) {
                            debug.log(tag + "last headers bytes sent, %s",
                                    noBody ? "no body" : "body should follow");
                        }
                        writer.scheduleForWriting(buf, noBody);
                    } else {
                        writer.queueForWriting(buf);
                    }
                }
                if (noBody) {
                    if (debug.on()) {
                        debug.log(tag + "no body: closing os");
                    }
                    os.closeInternal();
                }
                os.goodToGo();
                if (debug.on()) {
                    debug.log(this + " Sent response headers " + tag + rCode);
                }
            }

            private void optionallySendUnknownOrReservedFrame() {
                if (this.unknownReservedFrameAlreadySent) {
                    // don't send it more than once
                    return;
                }
                UnknownOrReservedFrame.tryGenerateFrame().ifPresent((f) -> {
                    if (debug.on()) {
                        debug.log("queueing to send an unknown/reserved HTTP3 frame: " + f);
                    }
                    try {
                        writer.queueForWriting(f.toByteBuffer());
                    } catch (IOException e) {
                        // ignore
                        if (debug.on()) {
                            debug.log("failed to queue unknown/reserved HTTP3 frame: " + f, e);
                        }
                    }
                    this.unknownReservedFrameAlreadySent = true;
                });
            }

            @Override
            public InetSocketAddress getRemoteAddress() {
                return http3ServerConnection().quicConnection.peerAddress();
            }

            @Override
            public int getResponseCode() {
                return responseCode;
            }

            @Override
            public InetSocketAddress getLocalAddress() {
                return (InetSocketAddress)quicConnection.localAddress();
            }

            @Override
            public String getConnectionKey() {
                // assuming the localConnectionId never changes;
                // this will return QuicServerConnectionId(NNN), which should
                // be enough to detect whether two exchanges are made on the
                // same connection
                return quicConnection.logTag();
            }

            @Override
            public String getProtocol() {
                return "HTTP/3";
            }

            @Override
            public Version getServerVersion() { return Version.HTTP_3; }

            @Override
            public Version getExchangeVersion() { return Version.HTTP_3; }

            @Override
            public boolean serverPushAllowed() {
                return true;
            }

            @Override
            public void serverPush(URI uri, HttpHeaders headers, InputStream content)
                    throws IOException {
                try {
                    serverPushWithId(uri, headers, content);
                } catch (IOException io) {
                    if (debug.on()) debug.log("Failed to push " + uri + ": " + io);
                    throw io;
                }
            }

            @Override
            public long serverPushWithId(URI uri, HttpHeaders headers, InputStream content)
                throws IOException {
                HttpHeaders combinedHeaders = combinePromiseHeaders(uri, headers);
                long pushId = nextPushId.getAndIncrement();
                if (debug.on()) {
                    debug.log("Server sending serverPushWithId(" + pushId + "): " + uri);
                }
                // send PUSH_PROMISE frame
                sendPushPromiseFrame(pushId, uri, combinedHeaders);
                if (debug.on()) debug.log("Server sent PUSH_PROMISE(" + pushId + ")");
                // now open push stream and send response headers + body
                PushPromise pp = sendPushResponse(pushId, combinedHeaders, content);
                assert pushId == pp.pushId();
                return pp.pushId();
            }

            @Override
            public long sendPushId(long pushId, URI uri, HttpHeaders headers) throws IOException {
                HttpHeaders httpHeaders = combinePromiseHeaders(uri, headers);
                return sendPushPromiseFrame(pushId, uri, httpHeaders);
            }

            @Override
            public void sendPushResponse(long pushId, URI uri, HttpHeaders headers, InputStream content)
                throws IOException {
                HttpHeaders httpHeaders = combinePromiseHeaders(uri, headers);
                PushPromise pp = sendPushResponse(pushId, httpHeaders, content);
                assert pushId == pp.pushId();
            }

            @Override
            public void resetStream(long code) throws IOException {
                os.resetStream(code);
            }

            @Override
            public void cancelPushId(long pushId) throws IOException {
                sendCancelPush(pushId);
            }

            @Override
            public long waitForMaxPushId(long pushId)
                throws InterruptedException {
                long maxPushId = Http3ServerConnection.this.maxPushId;
                if (maxPushId > pushId) return maxPushId;
                do {
                    pushIdLock.lock();
                    try {
                        maxPushId = Http3ServerConnection.this.maxPushId;
                        if (maxPushId > pushId) return maxPushId;
                        pushIdChanged.await();
                    } finally {
                        pushIdLock.unlock();
                    }
                } while(true);
            }

            private long sendPushPromiseFrame(long pushId, URI uri, HttpHeaders headers)
                    throws IOException {
                if (pushId == -1) pushId = nextPushId.getAndIncrement();
                List<ByteBuffer> payload = encodeHeaders(1024, streamId(), headers);
                PushPromiseFrame frame = new PushPromiseFrame(pushId, Utils.remaining(payload));
                ByteBuffer buffer = ByteBuffer.allocate(frame.headersSize());
                frame.writeHeaders(buffer);
                buffer.flip();
                boolean last = frame.length() == 0;
                if (last) {
                    if (debug.on()) {
                        debug.log("last payload sent: empty headers, no body");
                    }
                    writer.scheduleForWriting(buffer, false);
                } else {
                    writer.queueForWriting(buffer);
                }
                int size = payload.size();
                for (int i = 0; i < size; i++) {
                    last = i == size - 1;
                    var buf = payload.get(i);
                    if (last) {
                        writer.scheduleForWriting(buf, false);
                    } else {
                        writer.queueForWriting(buf);
                    }
                }
                return pushId;
            }

            private static HttpHeaders combinePromiseHeaders(URI uri, HttpHeaders headers) {
                HttpHeadersBuilder headersBuilder = new HttpHeadersBuilder();
                headersBuilder.setHeader(":method", "GET");
                headersBuilder.setHeader(":scheme", uri.getScheme());
                headersBuilder.setHeader(":authority", uri.getAuthority());
                headersBuilder.setHeader(":path", uri.getPath());
                for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
                    for (String value : entry.getValue())
                        headersBuilder.addHeader(entry.getKey(), value);
                }
                return headersBuilder.build();
            }

            @Override
            public void requestStopSending(long errorCode) {
                reader.stream().requestStopSending(errorCode);
            }

            private QuicSenderStream cancel(QuicSenderStream s) {
                try {
                    switch (s.sendingState()) {
                        case READY, SEND, DATA_SENT ->
                            s.reset(Http3Error.H3_REQUEST_CANCELLED.code());
                    }
                } catch (IOException io) {
                    throw new UncheckedIOException(io);
                }
                return s;
            }

            private PushPromise sendPushResponse(long pushId, HttpHeaders headers, InputStream body) {
                var stream = quicConnection
                        .openNewLocalUniStream(Duration.ofSeconds(10));
                var promise = addPendingPush(pushId, stream, headers, this);
                if (!(promise instanceof PendingPush ppp) || ppp.stream() != stream) {
                    stream.thenApply(this::cancel);
                    return promise;
                }
                stream.thenApplyAsync(s -> {
                    if (debug.on()) {
                        debug.log("Server open(streamId=" + s.streamId() +", pushId="+ pushId + ")");
                    }
                    String tag = "streamId=" + s.streamId() + ": ";
                    var push = promiseMap.get(pushId);
                    if (!(push instanceof PendingPush)) {
                        this.cancel(s);
                        return push;
                    }
                    // no write loop: just buffer everything
                    final ReentrantLock pushLock = new ReentrantLock();
                    final Condition writePushEnabled = pushLock.newCondition();
                    final Runnable writeLoop = () -> {
                        pushLock.lock();
                        try {
                            writePushEnabled.signalAll();
                        } finally {
                            pushLock.unlock();
                        }
                    };
                    var pushw = s.connectWriter(SequentialScheduler.lockingScheduler(writeLoop));
                    int tlen = VariableLengthEncoder.getEncodedSize(Http3Streams.PUSH_STREAM_CODE);
                    int plen = VariableLengthEncoder.getEncodedSize(pushId);
                    ByteBuffer buf = ByteBuffer.allocate(tlen + plen);
                    VariableLengthEncoder.encode(buf, Http3Streams.PUSH_STREAM_CODE);
                    VariableLengthEncoder.encode(buf, pushId);
                    buf.flip();
                    try {
                        pushw.queueForWriting(buf);
                        if (debug.on()) {
                            debug.log(tag + "Server queued push stream type pushId=" + pushId
                                    + " 0x" + Utils.asHexString(buf));
                        }
                        ResponseBodyOutputStream os = new ResponseBodyOutputStream(connectionTag(),
                                debug, pushw, pushLock, writePushEnabled, () -> 0);
                        sendResponseHeaders(s.streamId(), pushw, false, 200, 0, new HttpHeadersBuilder(), os);
                        if (debug.on()) {
                            debug.log(tag + "Server push response headers sent pushId=" + pushId);
                        }
                        switch (s.sendingState()) {
                            case SEND, READY -> {
                                if (!s.stopSendingReceived()) {
                                    body.transferTo(os);
                                    promiseMap.put(pushId, new CompletedPush(pushId, headers));
                                    os.close();
                                    if (debug.on()) {
                                        debug.log(tag + "Server push response body sent pushId=" + pushId);
                                    }
                                    promiseMap.remove(pushId); // we're done
                                } else {
                                    if (debug.on()) {
                                        debug.log(tag + "Server push response body cancelled pushId=" + pushId);
                                    }
                                    promiseMap.put(pushId, new CancelledPush(pushId));
                                    cancel(s);
                                }
                            }
                            case RESET_SENT, RESET_RECVD -> {
                                if (debug.on()) {
                                    debug.log(tag + "Server push response body cancelled pushId=" + pushId);
                                }
                                // benign race if already cancelled, stateless marker
                                promiseMap.put(pushId, new CancelledPush(pushId));
                            }
                            default -> {
                                if (debug.on()) {
                                    debug.log(tag + "Server push response body cancelled pushId=" + pushId);
                                }
                                promiseMap.put(pushId, new CancelledPush(pushId));
                                cancel(s);
                            }
                        }
                        body.close();
                    } catch (IOException io) {
                        if (debug.on()) {
                            debug.log(tag + "Server failed to send pushId=" + pushId +": " + io);
                        }
                        throw new UncheckedIOException(io);
                    }
                    return promiseMap.get(pushId);
                }, server.getQuicServer().executor()).exceptionally(t -> {
                    if (debug.on()) {
                        debug.log( "Server failed to send PushPromise(pushId=" + pushId +"): " + t);
                    }
                    promiseMap.put(pushId, new CancelledPush(pushId));
                    try {
                        body.close();
                    } catch (IOException io) {
                        if (debug.on()) {
                            debug.log("Failed to close PushPromise stream(pushId="
                                    + pushId + "): " + io);
                        }
                    }
                    return promiseMap.get(pushId);
                });
                return promise;
            }

            @Override
            public CompletableFuture<Long> sendPing() {
                var executor = quicConnection.quicInstance().executor();
                return quicConnection.requestSendPing()
                        // ensure that dependent actions will not be executed in the
                        // thread that completes the CF
                        .thenApplyAsync(Function.identity(), executor)
                        .exceptionallyAsync(this::rethrow, executor);
            }

            private <T> T rethrow(Throwable t) {
                if (t instanceof RuntimeException r) throw r;
                if (t instanceof Error e) throw e;
                if (t instanceof ExecutionException x) return rethrow(x.getCause());
                throw new CompletionException(t);
            }

            private boolean isHeadRequest() {
                return "HEAD".equals(method);
            }

            static class ResponseBodyOutputStream extends OutputStream {

                volatile boolean closed;
                volatile boolean goodToGo;
                boolean headersWritten;
                long sent;
                private final QuicStreamWriter osw;
                private final ReentrantLock writeLock;
                private final Condition writeEnabled;
                private final LongSupplier responseLength;
                private final Logger debug;
                private final String connectionTag;
                ResponseBodyOutputStream(String connectionTag,
                                         Logger debug,
                                         QuicStreamWriter writer,
                                         ReentrantLock writeLock,
                                         Condition writeEnabled,
                                         LongSupplier responseLength) {
                    this.debug = debug;
                    this.writeLock = writeLock;
                    this.writeEnabled = writeEnabled;
                    this.responseLength = responseLength;
                    this.osw = writer;
                    this.connectionTag = connectionTag;
                }

                private void writeHeadersIfNeeded(ByteBuffer buffer)
                        throws IOException {
                    assert writeLock.isHeldByCurrentThread();
                    long responseLength = this.responseLength.getAsLong();
                    boolean streaming = responseLength == 0;
                    if (streaming) {
                        if (buffer.hasRemaining()) {
                            // TODO: add a static method that takes the length instead!
                            int len = buffer.remaining();
                            if (debug.on()) {
                                debug.log("Streaming BodyResponse: streamId=%s writing DataFrame(%s)",
                                        osw.stream().streamId(), len);
                            }
                            var data = new DataFrame(len);
                            var headers = ByteBuffer.allocate(data.headersSize());
                            data.writeHeaders(headers);
                            headers.flip();
                            osw.queueForWriting(headers);
                        }
                    } else if (!headersWritten) {
                        long len = responseLength > 0 ? responseLength : 0;
                        if (debug.on()) {
                            debug.log("BodyResponse: streamId=%s writing DataFrame(%s)",
                                    osw.stream().streamId(), len);
                        }
                        var data = new DataFrame(len);
                        var headers = ByteBuffer.allocate(data.headersSize());
                        data.writeHeaders(headers);
                        headers.flip();
                        osw.queueForWriting(headers);
                        headersWritten = true;
                    }
                }

                @Override
                public void write(int b) throws IOException {
                    var buffer  = ByteBuffer.allocate(1);
                    buffer.put((byte)b);
                    buffer.flip();
                    submit(buffer);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    Objects.checkFromIndexSize(off, len, b.length);
                    // the data is not written immediately, and therefore
                    // it needs to be copied.
                    // maybe we should find a way to wait until the data
                    // has been written, but that sounds complex.
                    ByteBuffer buffer = ByteBuffer.wrap(b.clone(), off, len);
                    submit(buffer);
                }

                String logTag() {
                    return connectionTag + " streamId=" + osw.stream().streamId();
                }

                /**
                 * Schedule the ByteBuffer for writing. The buffer must never
                 * be reused.
                 * @param buffer response data
                 * @throws IOException if the channel is closed
                 */
                public void submit(ByteBuffer buffer) throws IOException {
                    writeLock.lock();
                    try {
                        if (closed && buffer.hasRemaining()) {
                            throw new ClosedChannelException();
                        }
                        if (osw.credit() <= 0) {
                            if (Log.requests()) {
                                Log.logResponse(() -> logTag() + ": HTTP/3 Server waiting for credits");
                            }
                            writeEnabled.awaitUninterruptibly();
                            if (Log.requests()) {
                                Log.logResponse(() -> logTag() + ": HTTP/3 Server unblocked");
                            }
                        }
                        if (closed) {
                            if (buffer.hasRemaining()) {
                                throw new ClosedChannelException();
                            } else return;
                        }
                        int len = buffer.remaining();
                        sent = sent + len;
                        writeHeadersIfNeeded(buffer);
                        long responseLength = this.responseLength.getAsLong();
                        boolean streaming = responseLength == 0;
                        boolean last = !streaming && (sent == responseLength
                                || (sent == 0 && responseLength == -1));
                        osw.scheduleForWriting(buffer, last);
                        if (last) closeInternal();
                        if (!streaming && sent != 0 && sent > responseLength) {
                            throw new IOException("sent more bytes than expected");
                        }
                    } finally {
                        writeLock.unlock();
                    }
                }

                public void closeInternal() {
                    if (debug.on()) {
                        debug.log("BodyResponse: streamId=%s closeInternal", osw.stream().streamId());
                    }
                    if (closed) return;
                    writeLock.lock();
                    try {
                        closed = true;
                    } finally {
                        writeLock.unlock();
                    }
                }

                public void close() throws IOException {
                    if (debug.on()) {
                        debug.log("BodyResponse: streamId=%s close", osw.stream().streamId());
                    }
                    if (closed) return;
                    writeLock.lock();
                    try {
                        if (closed) return;
                        closed = true;
                        switch (osw.sendingState()) {
                            case READY, SEND -> {
                                if (debug.on()) {
                                    debug.log("BodyResponse: streamId=%s sending EOF",
                                            osw.stream().streamId());
                                }
                                osw.scheduleForWriting(QuicStreamReader.EOF, true);
                                writeEnabled.signalAll();
                            }
                            default -> {}
                        }
                    } catch (IOException io) {
                        throw new IOException(io);
                    } finally {
                        writeLock.unlock();
                    }
                }

                public void goodToGo() {
                    this.goodToGo = true;
                }

                public void resetStream(long code) throws IOException {
                    if (closed) return;
                    writeLock.lock();
                    try {
                        if (closed) return;
                        closed = true;
                        switch (osw.sendingState()) {
                            case READY, SEND, DATA_SENT:
                                osw.reset(code);
                            default:
                                break;
                        }
                    } catch (IOException io) {
                        throw new IOException(io);
                    } finally {
                        writeLock.unlock();
                    }
                }
            }
        }

        private void close() {
            http3ServerConnection().exchangeClosed(this);
        }

        private final class HeadersConsumer extends ValidatingHeadersConsumer
                implements DecodingCallback {

            private HeadersConsumer() {
                super(Context.REQUEST);
            }

            @Override
            public void reset() {
                super.reset();
                requestHeadersBuilder.clear();
                if (debug.on()) {
                    debug.log("Response builder cleared, ready to receive new headers.");
                }
            }

            @Override
            public void onDecoded(CharSequence name, CharSequence value)
                    throws UncheckedIOException {
                String n = name.toString();
                String v = value.toString();
                super.onDecoded(n, v);
                requestHeadersBuilder.addHeader(n, v);
                if (Log.headers() && Log.trace()) {
                    Log.logTrace("RECEIVED HEADER (streamid={0}): {1}: {2}",
                            streamId(), n, v);
                }
            }

            @Override
            public void onComplete() {
                // TODO: it's up to the decoder to tell us when it's done!
                //       possibly via a call to headersConsumer
                HttpHeaders requestHeaders = requestHeadersBuilder.build();
                qpackReader.reset();
                requestHeadersCF.complete(requestHeaders);
            }

            @Override
            public void onError(Throwable throwable, Http3Error http3Error) {
                // TODO: Revisit this error handler during QPACK work
                try {
                    stream.reset(http3Error.code());
                } catch (IOException ioe) {
                    Http3ServerConnection.this.close(http3Error.code(),
                            ioe.getMessage());
                }
            }

            @Override
            public long streamId() {
                return stream.streamId();
            }
        }
    }

    private void exchangeClosed(Http3ServerStreamImpl http3ServerExchange) {
        requests.remove(http3ServerExchange.streamId());
    }

    sealed interface PushPromise permits CancelledPush, CompletedPush, PendingPush {
        long pushId();
    }

    private record CancelledPush(long pushId) implements PushPromise {}
    private record CompletedPush(long pushId, HttpHeaders headers) implements PushPromise {}
    private record PendingPush(long pushId,
                               CompletableFuture<QuicSenderStream> stream,
                               HttpHeaders headers,
                               Http3ServerStreamImpl.Http3ExchangeImpl exchange) implements PushPromise {
    }

    private final Map<Long, PushPromise> promiseMap = new ConcurrentHashMap<>();

    PushPromise addPendingPush(long pushId,
                               CompletableFuture<QuicSenderStream> stream,
                               HttpHeaders headers,
                               Http3ServerStreamImpl.Http3ExchangeImpl exchange) {
        var push = new PendingPush(pushId, stream, headers, exchange);
        expungePromiseMap();
        var previous = promiseMap.putIfAbsent(pushId, push);
        return previous == null ? push : previous;
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
        // TODO: Try to enable DT usage with setting server CAPACITY to non-zero value
        HeaderFrameWriter writer = qpackEncoder.newHeaderFrameWriter();
        return qpackEncoder.encodeHeaders(writer, streamId, bufferSize, headers);
    }
}
