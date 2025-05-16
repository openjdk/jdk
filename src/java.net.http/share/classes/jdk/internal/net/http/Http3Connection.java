/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse.PushPromiseHandler.PushId;
import java.net.http.HttpResponse.PushPromiseHandler.PushId.Http3PushId;
import java.net.http.StreamLimitException;
import java.net.http.UnsupportedProtocolVersionException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import jdk.internal.net.http.Http3PushManager.CancelPushReason;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.CancelPushFrame;
import jdk.internal.net.http.http3.frames.FramesDecoder;
import jdk.internal.net.http.http3.frames.GoAwayFrame;
import jdk.internal.net.http.http3.frames.Http3Frame;
import jdk.internal.net.http.http3.frames.Http3FrameType;
import jdk.internal.net.http.http3.frames.MalformedFrame;
import jdk.internal.net.http.http3.frames.MaxPushIdFrame;
import jdk.internal.net.http.http3.frames.PartialFrame;
import jdk.internal.net.http.http3.frames.SettingsFrame;
import jdk.internal.net.http.http3.streams.Http3Streams.StreamType;
import jdk.internal.net.http.http3.streams.Http3Streams;
import jdk.internal.net.http.http3.streams.PeerUniStreamDispatcher;
import jdk.internal.net.http.http3.streams.QueuingStreamPair;
import jdk.internal.net.http.http3.streams.UniStreamPair;
import jdk.internal.net.http.qpack.Decoder;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.TableEntry;
import jdk.internal.net.http.quic.QuicConnection;
import jdk.internal.net.http.quic.QuicStreamLimitException;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicStream;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;
import jdk.internal.net.http.quic.streams.QuicStreams;

import static java.net.http.HttpClient.Version.HTTP_3;
import static jdk.internal.net.http.Http3ClientProperties.MAX_STREAM_LIMIT_WAIT_TIMEOUT;
import static jdk.internal.net.http.http3.Http3Error.H3_CLOSED_CRITICAL_STREAM;
import static jdk.internal.net.http.http3.Http3Error.H3_INTERNAL_ERROR;
import static jdk.internal.net.http.http3.Http3Error.H3_NO_ERROR;
import static jdk.internal.net.http.http3.Http3Error.H3_STREAM_CREATION_ERROR;

/**
 * An HTTP/3 connection wraps an HttpQuicConnection and implements
 * HTTP/3 on top it.
 */
public final class Http3Connection implements AutoCloseable {

    private final Logger debug = Utils.getDebugLogger(this::dbgTag);
    private final Http3ClientImpl client;
    private final HttpQuicConnection connection;
    private final QuicConnection quicConnection;
    // key by which this connection will be referred to within the connection pool
    private final String connectionKey;
    private final String dbgTag;
    private final UniStreamPair controlStreamPair;
    private final UniStreamPair qpackEncoderStreams;
    private final UniStreamPair qpackDecoderStreams;
    private final Encoder qpackEncoder;
    private final Decoder qpackDecoder;
    private final FramesDecoder controlFramesDecoder;
    private final Set<PeerUniStreamDispatcher> dispatchers = ConcurrentHashMap.newKeySet();
    private final Predicate<? super QuicReceiverStream> remoteStreamListener;
    private final H3FrameOrderVerifier frameOrderVerifier = H3FrameOrderVerifier.newForControlStream();
    // streams for HTTP3 exchanges
    private final ConcurrentMap<Long, QuicBidiStream> exchangeStreams = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Http3ExchangeImpl<?>> exchanges = new ConcurrentHashMap<>();
    // true when the settings frame has been received on the control stream of this connection
    private volatile boolean settingsFrameReceived;
    // the settings we received from the peer
    private volatile ConnectionSettings peerSettings;
    // the settings we send to our peer
    private volatile ConnectionSettings ourSettings;
    // for tests
    private final MinimalFuture<ConnectionSettings> peerSettingsCF = new MinimalFuture<>();
    // the (lowest) request stream id received in GOAWAY frames on this connection.
    // subsequent request stream id(s) (if any) must always be equal to lesser than this value
    // as per spec
    // -1 is used to imply no GOAWAY received so far
    private final AtomicLong lowestGoAwayReceipt = new AtomicLong(-1);
    private volatile IdleConnectionTimeoutEvent idleConnectionTimeoutEvent;
    // value of true implies no more streams will be initiated on this connection,
    // and the connection will be closed once the in-progress streams complete.
    private volatile boolean finalStream;
    // set to true if we decide to open a new connection
    // due to stream limit reached
    private volatile boolean streamLimitReached;

    private static final int GOAWAY_SENT = 1; // local endpoint sent GOAWAY
    private static final int GOAWAY_RECEIVED = 2; // received GOAWAY from remote peer
    private static final int CLOSED = 4; // close called on QUIC connection
    // state when idle connection management initiates a shutdown of the connection, after
    // which the connection will go into SHUTDOWN_REQUESTED state
    private static final int IDLE_SHUTDOWN_INITIATED = 8;
    volatile int closedState;

    private final ReentrantLock lock = new ReentrantLock();
    private final Http3PushManager pushManager;
    private final AtomicLong reservedStreamCount = new AtomicLong();

    // The largest pushId for a remote created stream.
    // After GOAWAY has been sent, we will not accept
    // any larger pushId.
    private final AtomicLong largestPushId = new AtomicLong();

    // The max pushId for which a frame was scheduled to be sent.
    // This should always be less or equal to pushManager.maxPushId
    private final AtomicLong maxPushIdSent = new AtomicLong();


    /**
     * Creates a new HTTP/3 connection over a given {@link HttpQuicConnection}.
     *
     * @apiNote
     * This constructor is invoked upon a successful quic connection establishment,
     * typically after a successful Quic handshake. Creating the Http3Connection
     * earlier, for instance, after receiving the Server Hello, could also be considered.
     *
     * @implNote
     * Creating an HTTP/3 connection will trigger the creation of the HTTP/3 control
     * stream, sending of the HTTP/3 Settings frame, and creation of the QPack
     * encoder/decoder streams.
     *
     * @param request     the request which triggered the creation of the connection
     * @param client      the Http3Client instance this connection belongs to
     * @param connection  the {@code HttpQuicConnection} that was established
     */
    Http3Connection(HttpRequestImpl request, Http3ClientImpl client, HttpQuicConnection connection) {
        this.connectionKey = client.connectionKey(request);
        this.client = client;
        this.connection = connection;
        this.quicConnection = connection.quicConnection();
        var qdb = quicConnection.dbgTag();
        this.dbgTag = "H3(" + qdb +")";
        this.pushManager = new Http3PushManager(this); // OK to leak this
        controlFramesDecoder = new FramesDecoder("H3-control("+qdb+")",
                FramesDecoder::isAllowedOnControlStream);
        controlStreamPair = new UniStreamPair(
                StreamType.CONTROL,
                quicConnection,
                this::processPeerControlBytes,
                this::lcsWriterLoop,
                this::controlStreamFailed,
                debug);

        qpackEncoder = new Encoder(Http3Connection::shouldUpdateDynamicTable,
                                   this::createEncoderStreams, this::connectionError);
        qpackEncoderStreams = qpackEncoder.encoderStreams();
        qpackDecoder = new Decoder(this::createDecoderStreams, this::connectionError);
        qpackDecoderStreams = qpackDecoder.decoderStreams();
        // register a listener which will be called when the underlying QUIC connection
        // is being prepared to be idle timed out
        quicConnection.registerIdleTerminationApprover(this::allowsQuicIdleTermination);
        // Register listener to be called when the peer opens a new stream
        remoteStreamListener = this::onOpenRemoteStream;
        quicConnection.addRemoteStreamListener(remoteStreamListener);

        // Registers dependent actions with the controlStreamPair
        //   .futureSenderStreamWriter() CF, in order to send
        // the SETTINGS and MAX_PUSHID frames.
        // These actions will be executed when the stream writer is
        // available.
        //
        // This will schedule the SETTINGS and MAX_PUSHID frames
        // for writing, buffering them if necessary until control
        // flow credits are available.
        //
        // If an exception happens the connection will be
        // closed abruptly (by closing the underlying quic connection)
        // with an error of type Http3Error.H3_INTERNAL_ERROR
        controlStreamPair.futureSenderStreamWriter()
                // Send SETTINGS first
                .thenApply(this::sendSettings)
                // Chains to sending MAX_PUSHID after SETTINGS
                .thenApply(this::sendMaxPushId)
                // arranges for the connection to be closed
                // in case of exception. Throws in the dependent
                // action after wrapping the exception if needed.
                .exceptionally(this::exceptionallyAndClose);
        if (Log.http3()) {
            Log.logHttp3("HTTP/3 connection created for " + quicConnectionTag() + " - local address: "
                    + quicConnection.localAddress());
        }
    }

    public String quicConnectionTag() {
        return quicConnection.logTag();
    }

    private static boolean shouldUpdateDynamicTable(TableEntry tableEntry) {
        if (tableEntry.type() == TableEntry.EntryType.NAME_VALUE) {
            return false;
        }
        return switch(tableEntry.name().toString()) {
            case ":authority", "user-agent" -> !tableEntry.value().isEmpty();
            default -> false;
        };
    }

    private void lock() {
        lock.lock();
    }

    private void unlock() {
        lock.unlock();
    }

    /**
     * Debug tag used to create the debug logger for this
     * HTTP/3 connection instance.
     * @return a debug tag
     */
    String dbgTag() {
        return dbgTag;
    }

    /**
     * Asynchronously create an instance of an HTTP/3 connection, if the
     * server has a known HTTP/3 endpoint.
     * @param request   the first request that will go over this connection
     * @param h3client  the HTTP/3 client
     * @param exchange  the exchange for which this connection is created
     * @return a completable future that will be completed with a new
     *         HTTP/3 connection, or {@code null} if no usable HTTP/3 endpoint
     *         was found, or completed exceptionally if an error occurred
     */
    static CompletableFuture<Http3Connection> createAsync(HttpRequestImpl request,
                                                          Http3ClientImpl h3client,
                                                          Exchange<?> exchange) {
        assert request.secure();
        final HttpConnection connection = HttpConnection.getConnection(request.getAddress(),
                h3client.client(),
                exchange,
                request,
                HTTP_3);
        var debug = h3client.debug();
        var where = "Http3Connection.createAsync";
        if (!(connection instanceof HttpQuicConnection httpQuicConnection)) {
            if (debug.on())
                debug.log("%s: Connection is not an HttpQuicConnection: %s", where, connection);
            if (request.isHttp3Only(exchange.version())) {
                // should not happen unless HttpConnection.getConnection() returns null, which
                // should not happen.
                return MinimalFuture.failedFuture(new UnsupportedProtocolVersionException(
                        "cannot establish exchange to requested origin with HTTP/3"));
            }
            return MinimalFuture.completedFuture(null);
        }
        if (debug.on()) {
            debug.log("%s: Got HttpQuicConnection: %s", where, connection);
        }

        // Expose the underlying connection to the exchange's aborter so it can
        // be closed if a timeout occurs.
        exchange.connectionAborter.connection(httpQuicConnection);

        return httpQuicConnection.connectAsync(exchange)
                .thenCompose(unused -> httpQuicConnection.finishConnect())
                .thenCompose(unused -> checkSSLConfig(httpQuicConnection))
                .thenCompose(notused-> {
                    CompletableFuture<Http3Connection> cf = new MinimalFuture<>();
                    try {
                        if (debug.on())
                            debug.log("creating Http3Connection for %s", httpQuicConnection);
                        Http3Connection hc = new Http3Connection(request, h3client, httpQuicConnection);
                        if (!hc.isFinalStream()) {
                            exchange.connectionAborter.clear(httpQuicConnection);
                            cf.complete(hc);
                        } else {
                            var io = new IOException("can't reserve first stream");
                            if (Log.http3()) {
                                Log.logHttp3(" Unable to use HTTP/3 connection over {0}: {1}",
                                            hc.quicConnectionTag(),
                                            io);
                            }
                            hc.protocolError(io);
                            cf.complete(null);
                        }
                    } catch (Exception e) {
                        cf.completeExceptionally(e);
                    }
                    return cf; } )
                .whenComplete(httpQuicConnection::connectionEstablished);
    }

    private static CompletableFuture<Void> checkSSLConfig(HttpQuicConnection quic) {
        // HTTP/2 checks ALPN here; with HTTP/3, we only offer one ALPN,
        // and TLS verifies that it's negotiated.

        // We can examine the negotiated parameters here and possibly fail
        // if they are not satisfactory.
        return MinimalFuture.completedFuture(null);
    }

    HttpQuicConnection connection() {
        return connection;
    }

    String key() {
        return connectionKey;
    }

    /**
     * Whether the final stream (last stream allowed on a connection), has
     * been set.
     * @return true if the final stream has been set.
     */
    boolean isFinalStream() {
        return this.finalStream;
    }

    /**
     * Sets the final stream to be the next stream opened on
     * the connection. No other stream will be opened after this.
     */
    void setFinalStream() {
        this.finalStream = true;
    }

    boolean isClosed() {
       return closedState != 0 || !quicConnection.isOpen();
    }

    boolean isOpen() {
        return closedState == 0 && quicConnection.isOpen();
    }

    private IOException checkConnectionError() {
        final TerminationCause tc = quicConnection.terminationCause();
        return tc == null ? null : tc.getCloseCause();
    }

    // Used only by tests
    CompletableFuture<ConnectionSettings> peerSettingsCF() {
        return peerSettingsCF;
    }

    private boolean reserveStream() {
        lock();
        try {
            if (finalStream) {
                return false;
            }
            reservedStreamCount.incrementAndGet();
            return true;
        } finally {
            unlock();
        }
    }

    <U> CompletableFuture<? extends ExchangeImpl<U>>
    createStream(final Exchange<U> exchange) throws IOException {
        // check if this connection is closing before initiating this new stream
        if (!reserveStream()) {
            if (Log.http3()) {
                Log.logHttp3("Cannot initiate new stream on connection {0} for exchange {1}" ,
                        quicConnectionTag(), exchange);
            }
            // we didn't create the stream and thus the server hasn't yet processed this request.
            // mark the request as unprocessed to allow it to be retried on a different connection.
            exchange.markUnprocessedByPeer();
            String message = "cannot initiate additional new streams on chosen connection";
            IOException cause = streamLimitReached
                    ? new StreamLimitException(HTTP_3, message)
                    : new IOException(message);
            return MinimalFuture.failedFuture(cause);
        }
        // TODO: this duration is currently "computed" from the request timeout duration.
        // this computation needs a bit more thought
        final Duration streamLimitIncreaseDuration = exchange.request.timeout()
                .map((reqTimeout) -> reqTimeout.dividedBy(2))
                .orElse(Duration.ofMillis(MAX_STREAM_LIMIT_WAIT_TIMEOUT));
        final CompletableFuture<QuicBidiStream> bidiStream =
                quicConnection.openNewLocalBidiStream(streamLimitIncreaseDuration);
        // once the bidi stream creation completes:
        //  - if completed exceptionally, we transform any QuicStreamLimitException into a
        //    StreamLimitException
        //  - if completed successfully, we create a Http3 exchange and return that as the result
        final CompletableFuture<CompletableFuture<ExchangeImpl<U>>> h3ExchangeCf =
                bidiStream.handle((stream, t) -> {
                    if (t == null) {
                        // no exception occurred and a bidi stream was created on the quic
                        // connection, but check if the connection has been terminated
                        // in the meantime
                        final var terminationCause = checkConnectionError();
                        if (terminationCause != null) {
                            // connection already closed and we haven't yet issued the request.
                            // mark the exchange as unprocessed to allow it to be retried on
                            // a different connection.
                            exchange.markUnprocessedByPeer();
                            return MinimalFuture.failedFuture(terminationCause);
                        }
                        // creation of bidi stream succeeded, now create the H3 exchange impl
                        // and return it
                        final Http3ExchangeImpl<U> h3Exchange = createHttp3ExchangeImpl(exchange, stream);
                        return MinimalFuture.completedFuture(h3Exchange);
                    }
                    // failed to open a bidi stream
                    reservedStreamCount.decrementAndGet();
                    final Throwable cause = Utils.getCompletionCause(t);
                    if (cause instanceof QuicStreamLimitException) {
                        if (Log.http3()) {
                            Log.logHttp3("Maximum stream limit reached on {0} for exchange {1}",
                                    quicConnectionTag(), exchange.multi.streamLimitState());
                        }
                        if (debug.on()) {
                            debug.log("bidi stream creation failed due to stream limit: "
                                    + cause + ", connection will be marked as unusable for subsequent" +
                                    " requests");
                        }
                        // Since we have reached the stream creation limit (which translates to not
                        // being able to initiate new requests on this connection), we mark the
                        // connection as "final stream" (i.e. don't consider this (pooled)
                        // connection for subsequent requests)
                        this.streamLimitReachedWith(exchange);
                        return MinimalFuture.failedFuture(new StreamLimitException(HTTP_3,
                                "No more streams allowed on connection"));
                    } else if (cause instanceof ClosedChannelException) {
                        // stream creation failed due to the connection (that was chosen)
                        // got closed. Thus the request wasn't processed by the server.
                        // mark the request as unprocessed to allow it to be
                        // initiated on a different connection
                        exchange.markUnprocessedByPeer();
                        return MinimalFuture.failedFuture(cause);
                    }
                    return MinimalFuture.failedFuture(cause);
                });
        return h3ExchangeCf.thenCompose(Function.identity());
    }

    private void streamLimitReachedWith(Exchange<?> exchange) {
        streamLimitReached = true;
        client.streamLimitReached(this, exchange.request);
        setFinalStream();
    }

    private <T> Http3ExchangeImpl<T> createHttp3ExchangeImpl(Exchange<T> exchange, QuicBidiStream stream) {
        if (debug.on()) {
            debug.log("Temporary reference h3 stream: " + stream.streamId());
        }
        if (Log.http3()) {
            Log.logHttp3("Creating HTTP/3 exchange for {0}/streamId={1}",
                    quicConnectionTag(), Long.toString(stream.streamId()));
        }
        client.client.h3StreamReference();
        try {
            lock();
            try {
                this.exchangeStreams.put(stream.streamId(), stream);
                reservedStreamCount.decrementAndGet();
                var te = idleConnectionTimeoutEvent;
                if (te != null) {
                    client.client().cancelTimer(te);
                    idleConnectionTimeoutEvent = null;
                }
            } finally {
                unlock();
            }
            var http3Exchange = new Http3ExchangeImpl<>(this, exchange, stream);
            return registerAndStartExchange(http3Exchange);
        } finally {
            if (debug.on()) {
                debug.log("Temporary unreference h3 stream: " + stream.streamId());
            }
            client.client.h3StreamUnreference();
        }
    }

    private <T> Http3ExchangeImpl<T> registerAndStartExchange(Http3ExchangeImpl<T> exchange) {
        var streamId = exchange.streamId();
        if (debug.on()) debug.log("Reference h3 stream: " + streamId);
        client.client.h3StreamReference();
        exchanges.put(streamId, exchange);
        exchange.start();
        return exchange;
    }

    private boolean allowsQuicIdleTermination() {
        if (!isOpen()) {
            return true;
        }
        lock();
        final boolean okToIdleTimeout;
        try {
            if (markIdleShutdownInitiated()) {
                // don't allow any new streams to be created
                setFinalStream();
                okToIdleTimeout = finalStreamClosed();
            } else {
                // already marked for idle shutdown previously.
                // check if all streams on the connection have now closed.
                okToIdleTimeout = finalStreamClosed();
            }
        } finally {
            unlock();
        }
        if (debug.on()) {
            debug.log((okToIdleTimeout ? "allowing" : "disallowing") + " QUIC connection" +
                    " to idle timeout");
        }
        return okToIdleTimeout;
    }

    // marks this connection as no longer available for creating additional streams. current
    // streams will run to completion. marking the connection as gracefully shutdown
    // can involve sending the necessary protocol message(s) to the peer.
    private void sendGoAway() throws IOException {
        if (markSentGoAway()) {
            // already sent (either successfully or an attempt was made) GOAWAY, nothing more to do
            return;
        }
        // RFC-9114, section 5.2: Endpoints initiate the graceful shutdown of an HTTP/3 connection
        // by sending a GOAWAY frame.
        final QuicStreamWriter writer = controlStreamPair.localWriter();
        if (writer != null && quicConnection.isOpen()) {
            try {
                // We send here the largest pushId for which the peer has
                // opened a stream. We won't process pushIds larger than that, and
                // we will later cancel any pending push promises anyway.
                final long lastProcessedPushId = largestPushId.get();
                final GoAwayFrame goAwayFrame = new GoAwayFrame(lastProcessedPushId);
                final long size = goAwayFrame.size();
                assert size >= 0 && size < Integer.MAX_VALUE;
                final var buf = ByteBuffer.allocate((int) size);
                goAwayFrame.writeFrame(buf);
                buf.flip();
                if (debug.on()) {
                    debug.log("Sending GOAWAY frame %s from client connection %s", goAwayFrame, this);
                }
                writer.scheduleForWriting(buf, false);
            } catch (Exception e) {
                // ignore - we couldn't send a GOAWAY
                if (debug.on()) {
                    debug.log("Failed to send GOAWAY from client " + this, e);
                }
                Log.logError("Could not send a GOAWAY from client {0}", this);
                Log.logError(e);
            }
        }
    }

    @Override
    public void close() {
        try {
            sendGoAway();
        } catch (IOException ioe) {
            // log and ignore the failure
            // failure to send a GOAWAY shouldn't prevent closing a connection
            if (debug.on()) {
                debug.log("failed to send a GOAWAY frame before initiating a close: " + ioe);
            }
        }
         // TODO: ideally we should hava flushForClose() which goes all the way to terminator to flush
        //    streams and increasing the chances of GOAWAY being sent.
        // check RFC-9114, section 5.3 which seems to allow including GOAWAY and CONNECTION_CLOSE
        //    frames in same packet (optionally)
        close(Http3Error.H3_NO_ERROR, "H3 connection closed - no error");
    }

    void close(final Throwable throwable) {
        close(H3_INTERNAL_ERROR, null, throwable);
    }

    void close(final Http3Error error, final String message) {
        if (error != H3_NO_ERROR) {
            // construct a IOException representing the connection termination cause
            final IOException cause = new IOException(message);
            close(error, message, cause);
        } else {
            close(error, message, null);
        }
    }

    void close(final Http3Error error, final String logMsg,
                     final Throwable closeCause) {
        if (!markClosed()) {
            // already closed, nothing to do
            return;
        }
        if (debug.on()) {
            debug.log("Closing HTTP/3 connection: %s %s %s", error, logMsg == null ? "" : logMsg,
                    closeCause == null ? "" : closeCause.toString());
            debug.log("State is: " + describeClosedState(closedState));
        }
        exchanges.values().forEach(e -> e.recordError(closeCause));
        // close the underlying QUIC connection
        connection.close(error.code(), logMsg, closeCause);
        final TerminationCause tc = connection.quicConnection.terminationCause();
        assert tc != null : "termination cause is null";
        // close all HTTP streams
        exchanges.values().forEach(exchange -> exchange.cancelImpl(tc.getCloseCause(), error));
        pushManager.cancelAllPromises(tc.getCloseCause(), error);
        discardConnectionState();
        // No longer wait for reading HTTP/3 stream types:
        // stop waiting on any stream for which we haven't received the stream
        // type yet.
        try {
            var listener = remoteStreamListener;
            if (listener != null) {
                quicConnection.removeRemoteStreamListener(listener);
                for (PeerUniStreamDispatcher dispatcher : dispatchers) {
                    dispatcher.stop();
                }
            }
        } finally {
            client.connectionClosed(this);
        }
        if (!peerSettingsCF.isDone()) {
            peerSettingsCF.completeExceptionally(tc.getCloseCause());
        }
    }

    private void discardConnectionState() {
        controlStreamPair.stopSchedulers();
        controlFramesDecoder.clear();
        qpackDecoderStreams.stopSchedulers();
        qpackEncoderStreams.stopSchedulers();
    }

    private boolean markClosed() {
        return markClosedState(CLOSED);
    }

    void protocolError(IOException error) {
        connectionError(error, Http3Error.H3_GENERAL_PROTOCOL_ERROR);
    }

    void connectionError(Throwable throwable, Http3Error error) {
        connectionError(null, throwable, error.code(), null);
    }

    void connectionError(Http3Stream<?> exchange, Throwable throwable, long errorCode,
                                String logMsg) {
        final Optional<Http3Error> error = Http3Error.fromCode(errorCode);
        assert error.isPresent() : "not a HTTP3 error code: " + errorCode;
        close(error.get(), logMsg, throwable);
    }

    public String toString() {
        return String.format("Http3Connection(%s)", connection());
    }

    private boolean finalStreamClosed() {
        lock();
        try {
            return this.finalStream && this.exchangeStreams.isEmpty() && this.reservedStreamCount.get() == 0;
        } finally {
            unlock();
        }
    }

    /**
     * Called by the {@link Http3ExchangeImpl} when the exchange is closed.
     * @param streamId The request stream id
     */
    void onExchangeClose(Http3ExchangeImpl<?> exch, final long streamId) {
        // we expect it to be a request/response stream
        if (!(QuicStreams.isClientInitiated(streamId) && QuicStreams.isBidirectional(streamId))) {
            throw new IllegalArgumentException("Not a client initiated bidirectional stream");
        }
        if (this.exchangeStreams.remove(streamId) != null) {
            if (connection().quicConnection().isOpen()) {
                qpackDecoder.cancelStream(streamId);
            }
            decrementStreamsCount(exch, streamId);
            exchanges.remove(streamId);
        }

        if (finalStreamClosed()) {
            // no more streams open on this connection. close the connection
            if (Log.http3()) {
                Log.logHttp3("Closing HTTP/3 connection {0} on final stream (streamId={1})",
                        quicConnectionTag(), Long.toString(streamId));
            }
            // close will take care of canceling all pending push promises
            // if any push promises are left pending
            close();
        } else {
            if (Log.http3()) {
                Log.logHttp3("HTTP/3 connection {0} left open: exchanged streamId={1} closed; " +
                                "finalStream={2}, exchangeStreams={3}, reservedStreamCount={4}",
                        quicConnectionTag(), Long.toString(streamId), finalStream,
                        exchangeStreams.size(), reservedStreamCount.get());
            }
            lock();
            try {
                var te = idleConnectionTimeoutEvent;
                if (te == null && exchangeStreams.isEmpty()) {
                    te = idleConnectionTimeoutEvent = client.client().idleConnectionTimeout(HTTP_3)
                            .map(IdleConnectionTimeoutEvent::new).orElse(null);
                    if (te != null) {
                        client.client().registerTimer(te);
                    }
                }
            } finally {
                unlock();
            }
        }
    }

    void decrementStreamsCount(Http3ExchangeImpl<?> exch, long streamid) {
        if (exch.deRegister()) {
            debug.log("Unreference h3 stream: " + streamid);
            client.client.h3StreamUnreference();
        } else {
            debug.log("Already unreferenced h3 stream: " + streamid);
        }
    }

    // Called from Http3PushPromiseStream::start (via Http3ExchangeImpl)
    <T> void onPushPromiseStreamStarted(Http3PushPromiseStream<T> http3PushPromiseStream, long streamId) {
        // HTTP/3 push promises are not refcounted.
        // At the moment an ongoing push promise will not prevent the client
        // to exit normally, if all request-response streams are finished.
        // Here would be the place to increment ref-counting if we wanted to
    }

    // Called by Http3PushPromiseStream::close
    <T> void onPushPromiseStreamClosed(Http3PushPromiseStream<T> http3PushPromiseStream, long streamId) {
        // HTTP/3 push promises are not refcounted.
        // At the moment an ongoing push promise will not prevent the client
        // to exit normally, if all request-response streams are finished.
        // Here would be the place to decrement ref-counting if we wanted to
        if (connection().quicConnection().isOpen()) {
            qpackDecoder.cancelStream(streamId);
        }
    }

    /**
     * A class used to dispatch peer initiated unidirectional streams
     * according to their HTTP/3 stream type.
     * The type of an HTTP/3 unidirectional stream is determined by
     * reading a variable length integer code off the stream, which
     * indicates the type of stream.
     * @see Http3Streams
     */
    private final class Http3StreamDispatcher extends PeerUniStreamDispatcher {
        Http3StreamDispatcher(QuicReceiverStream stream) {
            super(dispatchers, stream);
        }

        @Override
        protected Logger debug() { return debug; }

        @Override
        protected void onStreamAbandoned(QuicReceiverStream stream) {
            if (debug.on()) debug.log("Stream " + stream.streamId() + " abandoned!");
            qpackDecoder.cancelStream(stream.streamId());
        }

        @Override
        protected void onControlStreamCreated(String description, QuicReceiverStream stream) {
            complete(description, stream, controlStreamPair.futureReceiverStream());
        }

        @Override
        protected void onEncoderStreamCreated(String description, QuicReceiverStream stream) {
            complete(description, stream, qpackDecoderStreams.futureReceiverStream());
        }

        @Override
        protected void onDecoderStreamCreated(String description, QuicReceiverStream stream) {
            complete(description, stream, qpackEncoderStreams.futureReceiverStream());
        }

        @Override
        protected void onPushStreamCreated(String description, QuicReceiverStream stream, long pushId) {
            Http3Connection.this.onPushStreamCreated(stream, pushId);
        }

        // completes the given completable future with the given stream
        private void complete(String description, QuicReceiverStream stream, CompletableFuture<QuicReceiverStream> cf) {
            debug.log("completing CF for %s with stream %s", description, stream.streamId());
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

        /**
         * Dispatches the given remote initiated unidirectional stream to the
         * given Http3Connection after reading the stream type off the stream.
         * @param conn the Http3Connection with which the stream is associated
         * @param stream a newly opened remote unidirectional stream.
         */
        static CompletableFuture<QuicReceiverStream> dispatch(Http3Connection conn, QuicReceiverStream stream) {
            assert stream.isRemoteInitiated();
            assert !stream.isBidirectional();
            var dispatcher = conn.new Http3StreamDispatcher(stream);
            dispatcher.start();
            conn.lock();
            try {
                if (conn.isClosed()) {
                    dispatcher.stop();
                }
            } finally {
                conn.unlock();
            }
            return dispatcher.dispatchCF();
        }
    }

    /**
     * Attempts to notify the idle connection management that this connection should
     * be considered "in use". This way the idle connection management doesn't close
     * this connection during the time the connection is handed out from the pool and any
     * new stream created on that connection.
     * @return true if the connection has been successfully reserved and is {@link #isOpen()}. false
     *          otherwise; in which case the connection must not be handed out from the pool.
     */
    boolean tryReserveForPoolCheckout() {
        // must be done with "stateLock" held to co-ordinate idle connection management
        lock();
        try {
            cancelIdleShutdownEvent();
            // consider the reservation successful only if the connection's state hasn't moved
            // to "being closed"
            return !isClosed() && finalStream == false;
        } finally {
            unlock();
        }
    }

    /**
     * Cancels any event that might have been scheduled to shutdown this connection. Must be called
     * with the stateLock held.
     */
    private void cancelIdleShutdownEvent() {
        assert lock.isHeldByCurrentThread() : "Current thread doesn't hold " + lock;
        if (idleConnectionTimeoutEvent == null) return;
        idleConnectionTimeoutEvent.cancel();
        idleConnectionTimeoutEvent = null;
    }

    // An Idle connection is one that has no active streams
    // and has not sent the final stream flag
    final class IdleConnectionTimeoutEvent extends TimeoutEvent {

        private boolean cancelled;

        IdleConnectionTimeoutEvent(Duration duration) {
            super(duration);
        }

        @Override
        public void handle() {
            boolean okToIdleTimeout;
            lock();
            try {
                if (cancelled) return;
                if (!markIdleShutdownInitiated()) {
                    if (debug.on()) {
                        debug.log("Idle shutdown already initiated");
                    }
                    return;
                }
                setFinalStream();
                okToIdleTimeout = finalStreamClosed();
            } finally {
                unlock();
            }
            if (okToIdleTimeout) {
                if (debug.on()) {
                    debug.log("closing idle H3 connection");
                }
                close();
            }
        }

        /**
         * Cancels this event. Should be called with stateLock held
         */
        void cancel() {
            assert lock.isHeldByCurrentThread() : "Current thread doesn't hold " + lock;
            // mark as cancelled to prevent potentially already triggered event from actually
            // doing the shutdown
            this.cancelled = true;
            // cancel the timer to prevent the event from being triggered (if it hasn't already)
            client.client().cancelTimer(this);
        }

        @Override
        public String toString() {
            return "IdleConnectionTimeoutEvent, " + super.toString();
        }

    }

    /**
     * This method is called when the peer opens a new stream.
     * The stream can be unidirectional or bidirectional.
     * @param stream the new stream
     * @return always returns true (see {@link
     * QuicConnection#addRemoteStreamListener(Predicate)}
     */
    private boolean onOpenRemoteStream(QuicReceiverStream stream) {
        debug.log("on open remote stream: " + stream.streamId());
        if (stream instanceof QuicBidiStream bidi) {
            // A server will never open a bidirectional stream
            // with the client. A client opens a new bidirectional
            // stream for each request/response exchange.
            return onRemoteBidirectionalStream(bidi);
        } else {
            // Four types of unidirectional stream are defined:
            // control stream, qpack encoder, qpack decoder, push
            // promise stream
            return onRemoteUnidirectionalStream(stream);
        }
    }

    /**
     * This method is called when the peer opens a unidirectional stream.
     * @param uni the unidirectional stream opened by the peer
     * @return always returns true ({@link
     *         QuicConnection#addRemoteStreamListener(Predicate)}
     */
    protected boolean onRemoteUnidirectionalStream(QuicReceiverStream uni) {
        assert !uni.isBidirectional();
        assert uni.isRemoteInitiated();
        if (!isOpen()) return false;
        debug.log("dispatching unidirectional remote stream: " + uni.streamId());
        Http3StreamDispatcher.dispatch(this, uni).whenComplete((r, t)-> {
            if (t!=null) this.dispatchingFailed(uni, t);
        });
        return true;
    }

    /**
     * Called when the peer opens a bidirectional stream.
     * On the client side, this method should never be called.
     * @param bidi the new bidirectional stream opened by the
     *             peer.
     * @return always returns false ({@link
     *         QuicConnection#addRemoteStreamListener(Predicate)}
     */
    protected boolean onRemoteBidirectionalStream(QuicBidiStream bidi) {
        assert bidi.isRemoteInitiated();
        assert bidi.isBidirectional();

        // From RFC 9114, Section 6.1:
        //   Clients MUST treat receipt of a server-initiated bidirectional
        //   stream as a connection error of type H3_STREAM_CREATION_ERROR
        //   [ unless such an extension has been negotiated].
        // We don't support any extension, so this is a connection error.
        close(Http3Error.H3_STREAM_CREATION_ERROR,
                "Bidirectional stream %s opened by server peer"
                        .formatted(bidi.streamId()));
        return false;
    }

    /**
     * Called if the dispatch failed.
     * @param reason the reason of the failure
     */
    protected void dispatchingFailed(QuicReceiverStream uni, Throwable reason) {
        debug.log("dispatching failed for streamId=%s: %s", uni.streamId(), reason);
        close(H3_STREAM_CREATION_ERROR, "failed to dispatch remote stream " + uni.streamId(), reason);
    }


    /**
     * Schedules sending of client settings.
     * @return a completable future that will be completed with the
     * {@link QuicStreamWriter} allowing to write to the local control
     * stream
     */
    QuicStreamWriter sendSettings(QuicStreamWriter writer) {
        try {
            final SettingsFrame settings = QPACK.updateDecoderSettings(SettingsFrame.defaultRFCSettings());
            this.ourSettings = ConnectionSettings.createFrom(settings);
            this.qpackDecoder.configure(ourSettings);
            if (debug.on()) {
                debug.log("Sending client settings %s for connection %s", this.ourSettings, this);
            }
            long size = settings.size();
            assert size >= 0 && size < Integer.MAX_VALUE;
            var buf = ByteBuffer.allocate((int) size);
            settings.writeFrame(buf);
            buf.flip();
            writer.scheduleForWriting(buf, false);
            return writer;
        } catch (IOException io) {
            throw new CompletionException(io);
        }
    }

    /**
     * Schedules sending of max push id that this (client) connection allows.
     * @param writer the control stream writer
     * @return the {@link QuicStreamWriter} passed as parameter
     */
    private QuicStreamWriter sendMaxPushId(QuicStreamWriter writer) {
        try {
            long maxPushId = pushManager.getMaxPushId();
            if (maxPushId > 0 && maxPushId > maxPushIdSent.get()) {
                return sendMaxPushId(writer, maxPushId);
            } else {
                return writer;
            }
        } catch (IOException io) {
            // will wrap the io exception in CompletionException,
            // close the connection, and throw.
            throw new CompletionException(io);
        }
    }

    // local control stream write loop
    void lcsWriterLoop() {
        // since we do not write much data on the control stream
        // we don't check for credit and always directly buffer
        // the data to send in the writer. Therefore, there is
        // nothing to do in the control stream writer loop.
        //
        // When more credit is available, check if we need
        // to send maxpushid;
        if (maxPushIdSent.get() < pushManager.getMaxPushId()) {
            var writer = controlStreamPair.localWriter();
            if (writer != null && writer.connected()) {
                sendMaxPushId(writer);
            }
        }
    }

    void controlStreamFailed(final QuicStream stream, final UniStreamPair uniStreamPair,
                             final Throwable throwable) {
        Http3Streams.debugErrorCode(debug, stream, "Control stream failed");
        if (stream.state() instanceof QuicReceiverStream.ReceivingStreamState rcvrStrmState) {
            if (rcvrStrmState.isReset() && quicConnection.isOpen()) {
                // RFC-9114, section 6.2.1:
                // If either control stream is closed at any point,
                // this MUST be treated as a connection error of type H3_CLOSED_CRITICAL_STREAM.
                final String logMsg = "control stream " + stream.streamId()
                        + " was reset even though disallowed";
                close(H3_CLOSED_CRITICAL_STREAM, logMsg, throwable);
                return;
            }
        }
        if (isOpen()) {
            if (debug.on()) {
                debug.log("closing connection since control stream " + stream.mode()
                        + " failed", throwable);
            }
        }
        close(throwable);
    }

    /**
     * This method is called to process bytes received on the peer
     * control stream.
     * @param buffer the bytes received
     */
    private void processPeerControlBytes(final ByteBuffer buffer) {
        debug.log("received server control: %s bytes", buffer.remaining());
        controlFramesDecoder.submit(buffer);
        Http3Frame frame;
        while ((frame = controlFramesDecoder.poll()) != null) {
            final long frameType = frame.type();
            debug.log("server control frame: %s", Http3FrameType.asString(frameType));
            if (frame instanceof MalformedFrame malformed) {
                var cause = malformed.getCause();
                if (cause != null && debug.on()) {
                    debug.log(malformed.toString(), cause);
                }
                final Http3Error error = Http3Error.fromCode(malformed.getErrorCode())
                        .orElse(H3_INTERNAL_ERROR);
                close(error, malformed.getMessage());
                controlStreamPair.stopSchedulers();
                controlFramesDecoder.clear();
                return;
            }
            final boolean settingsRcvd = this.settingsFrameReceived;
            if ((frameType == SettingsFrame.TYPE && settingsRcvd)
                    || !this.frameOrderVerifier.allowsProcessing(frame)) {
                final String unexpectedFrameType = Http3FrameType.asString(frameType);
                // not expected to be arriving now, we either use H3_FRAME_UNEXPECTED
                // or H3_MISSING_SETTINGS for the connection error, depending on the context.
                //
                // RFC-9114, section 4.1: Receipt of an invalid sequence of frames MUST be
                // treated as a connection error of type H3_FRAME_UNEXPECTED.
                //
                // RFC-9114, section 6.2.1: If the first frame of the control stream
                // is any other frame type, this MUST be treated as a connection error of
                // type H3_MISSING_SETTINGS.
                final String logMsg = "unexpected (order of) frame type: " + unexpectedFrameType
                        + " on control stream";
                if (!settingsRcvd) {
                    close(Http3Error.H3_MISSING_SETTINGS, logMsg);
                } else {
                    close(Http3Error.H3_FRAME_UNEXPECTED, logMsg);
                }
                controlStreamPair.stopSchedulers();
                controlFramesDecoder.clear();
                return;
            }
            if (frame instanceof SettingsFrame settingsFrame) {
                this.settingsFrameReceived = true;
                this.peerSettings = ConnectionSettings.createFrom(settingsFrame);
                if (debug.on()) {
                    debug.log("Received peer settings %s for connection %s", this.peerSettings, this);
                }
                // We can only initialize encoder's DT only when we get Settings frame with all parameters
                qpackEncoder().configure(peerSettings);
                peerSettingsCF.completeAsync(() -> peerSettings,
                        client.client().theExecutor().safeDelegate());
                // Send DT capacity update instruction if the peer negotiated non-zero
                // max table capacity
                long maxCapacity = peerSettings.qpackMaxTableCapacity();
                if (QPACK.ENCODER_TABLE_CAPACITY_LIMIT > 0 && maxCapacity > 0) {
                    long encoderCapacity = Math.min(maxCapacity, QPACK.ENCODER_TABLE_CAPACITY_LIMIT);
                    qpackEncoder().setTableCapacity(encoderCapacity);
                }
            }
            if (frame instanceof CancelPushFrame cancelPush) {
                pushManager.cancelPushPromise(cancelPush.getPushId(), null, CancelPushReason.CANCEL_RECEIVED);
            }
            if (frame instanceof GoAwayFrame goaway) {
                handleIncomingGoAway(goaway);
            }
            if (frame instanceof PartialFrame partial) {
                var payloadBytes = controlFramesDecoder.readPayloadBytes();
                debug.log("added %s bytes to %s",
                        payloadBytes == null ? 0 : Utils.remaining(payloadBytes),
                        frame);
                if (partial.remaining() == 0) {
                    this.frameOrderVerifier.completed(frame);
                } else if (payloadBytes == null || payloadBytes.isEmpty()) {
                    break;
                }
                // only reserved frames reach here; just drop the payload
            } else {
                this.frameOrderVerifier.completed(frame);
            }
            if (controlFramesDecoder.eof()) {
                break;
            }
        }
        if (controlFramesDecoder.eof()) {
            close(H3_CLOSED_CRITICAL_STREAM, "EOF reached while reading server control stream");
        }
    }

    /**
     * Called when a new push promise stream is created by the peer.
     * @apiNote this method gives an opportunity to cancel the stream
     *          before reading the pushId, if it is known that no push
     *          will be accepted anyway.
     * @param pushStream the new push promise stream
     * @param pushId or -1 if the pushId is not available yet
     */
    private void onPushStreamCreated(QuicReceiverStream pushStream, long pushId) {
        assert pushStream.isRemoteInitiated();
        assert !pushStream.isBidirectional();

        if (pushId < 0) return; // pushId not received yet.
        onPushPromiseStream(pushStream, pushId);
    }

    /**
     * Called when a new push promise stream is created by the peer, and
     * the pushId has been read.
     * @param pushStream the new push promise stream
     * @param pushId the pushId
     */
    void onPushPromiseStream(QuicReceiverStream pushStream, long pushId) {
        assert pushId >= 0;
        pushManager.onPushPromiseStream(pushStream, pushId);
    }

    /**
     * This method is called by the {@link Http3PushManager} to figure out whether
     * a push stream or a push promise should be processed, with respect to the
     * GOAWAY state. Any pushId larger than what was sent in the GOAWAY frame
     * should be cancelled /rejected.
     * @param pushStream a push stream (may be null if not yet materialized)
     * @param pushId a pushId, must be > 0
     * @return true if the pushId can be processed
     */
    boolean acceptLargerPushPromise(QuicReceiverStream pushStream, long pushId) {
        // if GOAWAY has been sent, just cancel the push promise
        // otherwise - track this as the maxPushId that will be
        // sent in GOAWAY
        if (checkMaxPushId(pushId) != null) return false; // connection will be closed
        while (true) {
            long largestPushId = this.largestPushId.get();
            if ((closedState & GOAWAY_SENT) == GOAWAY_SENT) {
                if (pushId >= largestPushId) {
                    if (pushStream != null) {
                        pushStream.requestStopSending(H3_NO_ERROR.code());
                    }
                    pushManager.cancelPushPromise(pushId, null, CancelPushReason.PUSH_CANCELLED);
                    return false;
                }
            }
            if (pushId <= largestPushId) break;
            if (!this.largestPushId.compareAndSet(largestPushId, pushId)) continue;
            if ((closedState & GOAWAY_SENT) == 0) break;
        }
        // If we reach here, then either GOAWAY has been sent with a largestPushId >= pushId,
        // or GOAWAY has not been sent yet.
        return true;
    }

    QueuingStreamPair createEncoderStreams(Consumer<ByteBuffer> encoderReceiver) {
        return new QueuingStreamPair(StreamType.QPACK_ENCODER, quicConnection,
                encoderReceiver, this::onEncoderStreamsFailed, debug);
    }

    private void onEncoderStreamsFailed(final QuicStream stream, final UniStreamPair uniStreamPair,
                                        final Throwable throwable) {
        Http3Streams.debugErrorCode(debug, stream, "Encoder stream failed");
        if (stream.state() instanceof QuicReceiverStream.ReceivingStreamState rcvrStrmState) {
            if (rcvrStrmState.isReset() && quicConnection.isOpen()) {
                // RFC-9204, section 4.2:
                // Closure of either unidirectional stream type MUST be treated as a connection
                // error of type H3_CLOSED_CRITICAL_STREAM.
                final String logMsg = "QPACK encoder stream " + stream.streamId()
                        + " was reset even though disallowed";
                close(H3_CLOSED_CRITICAL_STREAM, logMsg, throwable);
                return;
            }
        }
        if (isOpen()) {
            if (debug.on()) {
                debug.log("closing connection since QPack encoder stream " + stream.streamId()
                        + " failed", throwable);
            }
        }
        close(throwable);
    }

    QueuingStreamPair createDecoderStreams(Consumer<ByteBuffer> encoderReceiver) {
        return new QueuingStreamPair(StreamType.QPACK_DECODER, quicConnection,
                encoderReceiver, this::onDecoderStreamsFailed, debug);
    }

    private void onDecoderStreamsFailed(final QuicStream stream, final UniStreamPair uniStreamPair,
                                        final Throwable throwable) {
        Http3Streams.debugErrorCode(debug, stream, "Decoder stream failed");
        if (stream.state() instanceof QuicReceiverStream.ReceivingStreamState rcvrStrmState) {
            if (rcvrStrmState.isReset() && quicConnection.isOpen()) {
                // RFC-9204, section 4.2:
                // Closure of either unidirectional stream type MUST be treated as a connection
                // error of type H3_CLOSED_CRITICAL_STREAM.
                final String logMsg = "QPACK decoder stream " + stream.streamId()
                        + " was reset even though disallowed";
                close(H3_CLOSED_CRITICAL_STREAM, logMsg, throwable);
                return;
            }
        }
        if (isOpen()) {
            if (debug.on()) {
                debug.log("closing connection since QPack decoder stream " + stream.streamId()
                        + " failed", throwable);
            }
        }
        close(throwable);
    }

    // This method never returns anything: it always throws
    private <T> T exceptionallyAndClose(Throwable t) {
        try {
            return exceptionally(t);
        } finally {
           close(t);
        }
    }

    // This method never returns anything: it always throws
    private <T> T exceptionally(Throwable t) {
        try {
            debug.log(t.getMessage(), t);
            throw t;
        } catch (RuntimeException | Error r) {
            throw r;
        } catch (ExecutionException x) {
            throw new CompletionException(x.getMessage(), x.getCause());
        } catch (Throwable e) {
            throw new CompletionException(e.getMessage(), e);
        }
    }

    Decoder qpackDecoder() {
        return qpackDecoder;
    }

    Encoder qpackEncoder() {
        return qpackEncoder;
    }

    /**
     * {@return the settings, sent by the peer, for this connection. If none is present, due to the SETTINGS
     * frame not yet arriving from the peer, this method returns {@link Optional#empty()}}
     */
    Optional<ConnectionSettings> getPeerSettings() {
        return Optional.ofNullable(this.peerSettings);
    }

    private void handleIncomingGoAway(final GoAwayFrame incomingGoAway) {
        final long quicStreamId = incomingGoAway.getTargetId();
        if (debug.on()) {
            debug.log("Received GOAWAY %s", incomingGoAway);
        }
        // ensure request stream id is a bidirectional stream originating from the client.
        // RFC-9114, section 7.2.6: A client MUST treat receipt of a GOAWAY frame containing
        // a stream ID of any other type as a connection error of type H3_ID_ERROR.
        if (!(QuicStreams.isClientInitiated(quicStreamId)
                && QuicStreams.isBidirectional(quicStreamId))) {
            close(Http3Error.H3_ID_ERROR, "Invalid stream id in GOAWAY frame");
            return;
        }
        boolean validStreamId = false;
        long current = lowestGoAwayReceipt.get();
        while (current == -1 || quicStreamId <= current) {
            if (lowestGoAwayReceipt.compareAndSet(current, quicStreamId)) {
                validStreamId = true;
                break;
            }
            current = lowestGoAwayReceipt.get();
        }
        if (!validStreamId) {
            // the request stream id received in the GOAWAY frame is greater than the one received
            // in some previous GOAWAY frame. This isn't allowed by spec.
            // RFC-9114, section 5.2: An endpoint MAY send multiple GOAWAY frames indicating
            // different identifiers, but the identifier in each frame MUST NOT be greater than
            // the identifier in any previous frame, ... Receiving a GOAWAY containing a larger
            // identifier than previously received MUST be treated as a connection error of
            // type H3_ID_ERROR.
            close(Http3Error.H3_ID_ERROR, "Invalid stream id in newer GOAWAY frame");
            return;
        }
        markReceivedGoAway();
        // mark a state on this connection to let it know that no new streams are allowed on this
        // connection.
        // RFC-9114, section 5.2: Endpoints MUST NOT initiate new requests or promise new pushes on
        // the connection after receipt of a GOAWAY frame from the peer.
        setFinalStream();
        if (debug.on()) {
            debug.log("Connection will no longer allow new streams due to receipt of GOAWAY" +
                    " from peer");
        }
        handlePeerUnprocessedStreams(quicStreamId);
    }

    private void handlePeerUnprocessedStreams(final long leastUnprocessedStreamId) {
        this.exchanges.forEach((id, exchange) -> {
            if (id >= leastUnprocessedStreamId) {
                // close the exchange as unprocessed
                client.client().theExecutor().execute(exchange::closeAsUnprocessed);
            }
        });
    }

    private boolean isMarked(int state, int mask) {
        return (state & mask) == mask;
    }

    private boolean markSentGoAway() {
        return markClosedState(GOAWAY_SENT);
    }

    private boolean markReceivedGoAway() {
        return markClosedState(GOAWAY_RECEIVED);
    }

    private boolean markIdleShutdownInitiated() {
        return markClosedState(IDLE_SHUTDOWN_INITIATED);
    }

    private boolean markClosedState(int flag) {
        int state, desired;
        do {
            state = closedState;
            if ((state & flag) == flag) return false;
            desired = state | flag;
        } while (!CLOSED_STATE.compareAndSet(this, state, desired));
        return true;
    }

    String describeClosedState(int state) {
        if (state == 0) return "active";
        String desc = null;
        if (isMarked(state, IDLE_SHUTDOWN_INITIATED)) {
            desc = "idle-shutdown-initiated";
        }
        if (isMarked(state, GOAWAY_SENT)) {
            if (desc == null) desc = "goaway-sent";
            else desc += "+goaway-sent";
        }
        if (isMarked(state, GOAWAY_RECEIVED)) {
            if (desc == null) desc = "goaway-rcvd";
            else desc += "+goaway-rcvd";
        }
        if (isMarked(state, CLOSED)) {
            if (desc == null) desc = "quic-closed";
            else desc += "+quic-closed";
        }
        return desc != null ? desc : "0x" + Integer.toHexString(state);
    }

    // PushPromise handling
    // ====================

    /**
     * {@return a new PushId for the given pushId}
     * @param pushId the pushId
     */
    PushId newPushId(long pushId) {
        return new Http3PushId(pushId, connection.label());
    }

    /**
     * Called when a pushId needs to be cancelled.
     * @param pushId  the pushId to cancel
     * @param cause   the cause (may be {@code null}).
     */
    void pushCancelled(long pushId, Throwable cause) {
        pushManager.cancelPushPromise(pushId, cause, CancelPushReason.PUSH_CANCELLED);
    }

    /**
     * Called if a PushPromiseFrame is received by an exchange that doesn't have any
     * {@link java.net.http.HttpResponse.PushPromiseHandler}. The pushId will be
     * cancelled, unless it's already been accepted by another exchange.
     *
     * @param pushId the pushId
     */
    void noPushHandlerFor(long pushId) {
        pushManager.cancelPushPromise(pushId, null, CancelPushReason.NO_HANDLER);
    }

    boolean acceptPromises() {
        return exchanges.values().stream().anyMatch(Http3ExchangeImpl::acceptPushPromise);
    }

    /**
     * {@return a completable future that will be completed when a pushId has been
     * accepted by the exchange in charge of creating the response body}
     *
     * The completable future is complete with {@code true} if the pushId is
     * accepted, and with {@code false} if the pushId was rejected or cancelled.
     *
     * @apiNote
     * This method is intended to be called when {@link
     * #onPushPromiseFrame(Http3ExchangeImpl, long, HttpHeaders)}, returns false,
     * indicating that the push promise is being delegated to another request/response
     * exchange.
     * On completion of the future returned here, if the future is completed
     * with {@code true}, the caller is expected to call {@link
     * PushGroup#acceptPushPromiseId(PushId)} in order  to notify the {@link
     * java.net.http.HttpResponse.PushPromiseHandler} of the received {@code pushId}.
     * <p>
     * Callers should not forward the pushId to a {@link
     * java.net.http.HttpResponse.PushPromiseHandler} unless the future is completed
     * with {@code true}
     *
     * @param pushId  the pushId
     */
    CompletableFuture<Boolean> whenPushAccepted(long pushId) {
        return pushManager.whenAccepted(pushId);
    }

    /**
     * Called when a PushPromiseFrame has been decoded.
     * @param exchange        The HTTP/3 exchange that received the frame
     * @param pushId          The pushId contained in the frame
     * @param promiseHeaders  The push promise headers contained in the frame
     * @return true if the exchange should take care of creating the HttpResponse body,
     *              false otherwise
     */
    boolean onPushPromiseFrame(Http3ExchangeImpl<?> exchange, long pushId, HttpHeaders promiseHeaders)
        throws IOException {
        return pushManager.onPushPromiseFrame(exchange, pushId, promiseHeaders);
    }

    /**
     * Checks whether a MAX_PUSH_ID frame should be sent.
     */
    void checkSendMaxPushId() {
        pushManager.checkSendMaxPushId();
    }

    /**
     * Schedules sending of max push id that this (client) connection allows.
     * @return a completable future that will be completed with the
     * {@link QuicStreamWriter} allowing to write to the local control
     * stream
     */
    private QuicStreamWriter sendMaxPushId(QuicStreamWriter writer, long maxPushId) throws IOException {
        debug.log("Sending max push id frame with max push id set to " + maxPushId);
        final MaxPushIdFrame maxPushIdFrame = new MaxPushIdFrame(maxPushId);
        final long frameSize = maxPushIdFrame.size();
        assert frameSize >= 0 && frameSize < Integer.MAX_VALUE;
        final ByteBuffer buf = ByteBuffer.allocate((int) frameSize);
        maxPushIdFrame.writeFrame(buf);
        buf.flip();
        if (writer.credit() > buf.remaining()) {
            long previous;
            do {
                previous = maxPushIdSent.get();
                if (previous >= maxPushId) return writer;
            } while (!maxPushIdSent.compareAndSet(previous, maxPushId));
            writer.scheduleForWriting(buf, false);
        }
        return writer;
    }

    /**
     * Send a MAX_PUSH_ID frame on the control stream with the given {@code maxPushId}
     *
     * @param maxPushId the new maxPushId
     *
     * @throws IOException if the pushId could not be sent
     */
    void sendMaxPushId(long maxPushId) throws IOException {
        sendMaxPushId(controlStreamPair.localWriter(), maxPushId);
    }

    /**
     * Sends a CANCEL_PUSH frame for the given {@code pushId}.
     * If not null, the cause may indicate why the push is cancelled.
     *
     * @apiNote  the cause is only used for logging
     *
     * @param pushId the pushId to cancel
     * @param cause  the reason for cancelling, may be {@code null}
     */
    void sendCancelPush(long pushId, Throwable cause) {
        // send CANCEL_PUSH frame here
        if (debug.on()) {
            if (cause != null) {
                debug.log("Push Promise %s cancelled: %s", pushId, cause.getMessage());
            } else {
                debug.log("Push Promise %s cancelled", pushId);
            }
        }
        try {
            CancelPushFrame cancelPush = new CancelPushFrame(pushId);
            long size = cancelPush.size();
            // frame should contain type, length, pushId
            assert size <= 3 * VariableLengthEncoder.MAX_INTEGER_LENGTH;
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            cancelPush.writeFrame(buffer);
            controlStreamPair.localWriter().scheduleForWriting(buffer, false);
        } catch (IOException io) {
            debug.log("Failed to cancel pushId: " + pushId);
        }
    }

    /**
     * Checks whether the given pushId exceed the maximum pushId allowed
     * to the peer, and if so, closes the connection.
     * @param pushId the pushId
     * @return an {@code IOException} that can be used to complete a completable
     *         future if the maximum pushId is exceeded, {@code null}
     *         otherwise
     */
    IOException checkMaxPushId(long pushId) {
        return checkMaxPushId(pushId, maxPushIdSent.get());
    }

    /**
     * Checks whether the given pushId exceed the maximum pushId allowed
     * to the peer, and if so, closes the connection.
     * @param pushId the pushId
     * @return an {@code IOException} that can be used to complete a completable
     *         future if the maximum pushId is exceeded, {@code null}
     *         otherwise
     */
    private IOException checkMaxPushId(long pushId, long max) {
        if (pushId >= max) {
            var io = new IOException("Max pushId exceeded (%s >= %s)".formatted(pushId, max));
            connectionError(io, Http3Error.H3_ID_ERROR);
            return io;
        }
        return null;
    }

    /**
     * {@return the minimum pushId that can be accepted from the peer}
     * Any pushId strictly less than this value must be ignored.
     *
     * @apiNote The minimum pushId represents the smallest pushId that
     * was recorded in our history. For smaller pushId, no history has
     * been kept, due to history size constraints. Any pushId strictly
     * less than this value must be ignored.
     */
    public long getMinPushId() {
        return pushManager.getMinPushId();
    }

    private static final VarHandle CLOSED_STATE;
    static {
        try {
            CLOSED_STATE = MethodHandles.lookup().findVarHandle(Http3Connection.class, "closedState", int.class);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }
}
