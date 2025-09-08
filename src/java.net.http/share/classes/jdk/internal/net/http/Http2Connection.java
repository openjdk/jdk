/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import jdk.internal.net.http.HttpConnection.HttpPublisher;
import jdk.internal.net.http.common.Alpns;
import jdk.internal.net.http.common.FlowTube;
import jdk.internal.net.http.common.FlowTube.TubeSubscriber;
import jdk.internal.net.http.common.HeaderDecoder;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.common.ValidatingHeadersConsumer;
import jdk.internal.net.http.common.ValidatingHeadersConsumer.Context;
import jdk.internal.net.http.frame.ContinuationFrame;
import jdk.internal.net.http.frame.DataFrame;
import jdk.internal.net.http.frame.ErrorFrame;
import jdk.internal.net.http.frame.FramesDecoder;
import jdk.internal.net.http.frame.FramesEncoder;
import jdk.internal.net.http.frame.GoAwayFrame;
import jdk.internal.net.http.frame.HeaderFrame;
import jdk.internal.net.http.frame.HeadersFrame;
import jdk.internal.net.http.frame.Http2Frame;
import jdk.internal.net.http.frame.MalformedFrame;
import jdk.internal.net.http.frame.OutgoingHeaders;
import jdk.internal.net.http.frame.PingFrame;
import jdk.internal.net.http.frame.PushPromiseFrame;
import jdk.internal.net.http.frame.ResetFrame;
import jdk.internal.net.http.frame.SettingsFrame;
import jdk.internal.net.http.frame.WindowUpdateFrame;
import jdk.internal.net.http.hpack.Decoder;
import jdk.internal.net.http.hpack.DecodingCallback;
import jdk.internal.net.http.hpack.Encoder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static jdk.internal.net.http.frame.SettingsFrame.ENABLE_PUSH;
import static jdk.internal.net.http.frame.SettingsFrame.HEADER_TABLE_SIZE;
import static jdk.internal.net.http.frame.SettingsFrame.INITIAL_CONNECTION_WINDOW_SIZE;
import static jdk.internal.net.http.frame.SettingsFrame.INITIAL_WINDOW_SIZE;
import static jdk.internal.net.http.frame.SettingsFrame.MAX_CONCURRENT_STREAMS;
import static jdk.internal.net.http.frame.SettingsFrame.MAX_FRAME_SIZE;
import static jdk.internal.net.http.frame.SettingsFrame.MAX_HEADER_LIST_SIZE;

/**
 * An Http2Connection. Encapsulates the socket(channel) and any SSLEngine used
 * over it. Contains an HttpConnection which hides the SocketChannel SSL stuff.
 *
 * Http2Connections belong to a Http2ClientImpl, (one of) which belongs
 * to a HttpClientImpl.
 *
 * Creation cases:
 * 1) upgraded HTTP/1.1 plain tcp connection
 * 2) prior knowledge directly created plain tcp connection
 * 3) directly created HTTP/2 SSL connection which uses ALPN.
 *
 * Sending is done by writing directly to underlying HttpConnection object which
 * is operating in async mode. No flow control applies on output at this level
 * and all writes are just executed as puts to an output Q belonging to HttpConnection
 * Flow control is implemented by HTTP/2 protocol itself.
 *
 * Hpack header compression
 * and outgoing stream creation is also done here, because these operations
 * must be synchronized at the socket level. Stream objects send frames simply
 * by placing them on the connection's output Queue. sendFrame() is called
 * from a higher level (Stream) thread.
 *
 * asyncReceive(ByteBuffer) is always called from the selector thread. It assembles
 * incoming Http2Frames, and directs them to the appropriate Stream.incoming()
 * or handles them directly itself. This thread performs hpack decompression
 * and incoming stream creation (Server push). Incoming frames destined for a
 * stream are provided by calling Stream.incoming().
 */
class Http2Connection  {

    final Logger debug = Utils.getDebugLogger(this::dbgString);
    static final Logger DEBUG_LOGGER =
            Utils.getDebugLogger("Http2Connection"::toString);
    private final Logger debugHpack =
            Utils.getHpackLogger(this::dbgString);
    static final ByteBuffer EMPTY_TRIGGER = ByteBuffer.allocate(0);

    private static final int MAX_CLIENT_STREAM_ID = Integer.MAX_VALUE; // 2147483647
    private static final int MAX_SERVER_STREAM_ID = Integer.MAX_VALUE - 1; // 2147483646
    // may be null; must be accessed/updated with the stateLock held
    private IdleConnectionTimeoutEvent idleConnectionTimeoutEvent;

    /**
     * Flag set when no more streams to be opened on this connection.
     * Two cases where it is used.
     *
     * 1. Two connections to the same server were opened concurrently, in which
     *    case one of them will be put in the cache, and the second will expire
     *    when all its opened streams (which usually should be a single client
     *    stream + possibly some additional push-promise server streams) complete.
     * 2. A cached connection reaches its maximum number of streams (~ 2^31-1)
     *    either server / or client allocated, in which case it will be taken
     *    out of the cache - allowing a new connection to replace it. It will
     *    expire when all its still open streams (which could be many) eventually
     *    complete.
     */
    private volatile boolean finalStream;

    /*
     * ByteBuffer pooling strategy for HTTP/2 protocol.
     *
     * In general there are 4 points where ByteBuffers are used:
     *  - incoming/outgoing frames from/to ByteBuffers plus incoming/outgoing
     *    encrypted data in case of SSL connection.
     *
     * 1. Outgoing frames encoded to ByteBuffers.
     *
     *  Outgoing ByteBuffers are created with required size and frequently
     *  small (except DataFrames, etc). At this place no pools at all. All
     *  outgoing buffers should eventually be collected by GC.
     *
     * 2. Incoming ByteBuffers (decoded to frames).
     *
     *  Here, total elimination of BB pool is not a good idea.
     *  We don't know how many bytes we will receive through network.
     *
     *  A possible future improvement ( currently not implemented ):
     *  Allocate buffers of reasonable size. The following life of the BB:
     *   - If all frames decoded from the BB are other than DataFrame and
     *     HeaderFrame (and HeaderFrame subclasses) BB is returned to pool,
     *   - If a DataFrame is decoded from the BB. In that case DataFrame refers
     *     to sub-buffer obtained by slice(). Such a BB is never returned to the
     *     pool and will eventually be GC'ed.
     *   - If a HeadersFrame is decoded from the BB. Then header decoding is
     *     performed inside processFrame method and the buffer could be release
     *     back to pool.
     *
     * 3. SSL encrypted buffers ( received ).
     *
     *  The current implementation recycles encrypted buffers read from the
     *  channel. The pool of buffers has a maximum size of 3, SocketTube.MAX_BUFFERS,
     *  direct buffers which are shared by all connections on a given client.
     *  The pool is used by all SSL connections - whether HTTP/1.1 or HTTP/2,
     *  but only for SSL encrypted buffers that circulate between the SocketTube
     *  Publisher and the SSLFlowDelegate Reader. Limiting the pool to this
     *  particular segment allows the use of direct buffers, thus avoiding any
     *  additional copy in the NIO socket channel implementation. See
     *  HttpClientImpl.SSLDirectBufferSupplier, SocketTube.SSLDirectBufferSource,
     *  and SSLTube.recycler.
     */

    // An Idle connection is one that has no active streams
    // and has not sent the final stream flag
    final class IdleConnectionTimeoutEvent extends TimeoutEvent {

        // expected to be accessed/updated with "stateLock" being held
        private boolean cancelled;

        IdleConnectionTimeoutEvent(Duration duration) {
            super(duration);
        }

        /**
         * {@link #shutdown(Throwable) Shuts down} the connection, unless this event is
         * {@link #cancelled}
         */
        @Override
        public void handle() {
            // first check if the connection is still idle.
            // must be done with the "stateLock" held, to allow for synchronizing actions like
            // closing the connection and checking out from connection pool (which too is expected
            // to use this same lock)
            stateLock.lock();
            try {
                if (cancelled) {
                    if (debug.on()) {
                        debug.log("Not initiating idle connection shutdown");
                    }
                    return;
                }
                if (!markIdleShutdownInitiated()) {
                    if (debug.on()) {
                        debug.log("Unexpected state %s, skipping idle connection shutdown",
                                describeClosedState(closedState));
                    }
                    return;
                }
            } finally {
                stateLock.unlock();
            }
            if (debug.on()) {
                debug.log("Initiating shutdown of HTTP connection which is idle for too long");
            }
            HttpConnectTimeoutException hte = new HttpConnectTimeoutException(
                    "HTTP connection idle, no active streams. Shutting down.");
            shutdown(hte);
        }

        /**
         * Cancels this event. Should be called with stateLock held
         */
        void cancel() {
            assert stateLock.isHeldByCurrentThread() : "Current thread doesn't hold " + stateLock;
            // mark as cancelled to prevent potentially already triggered event from actually
            // doing the shutdown
            this.cancelled = true;
            // cancel the timer to prevent the event from being triggered (if it hasn't already)
            client().cancelTimer(this);
        }

        @Override
        public String toString() {
            return "IdleConnectionTimeoutEvent, " + super.toString();
        }
    }

    // A small class that allows to control frames with respect to the state of
    // the connection preface. Any data received before the connection
    // preface is sent will be buffered.
    private final class FramesController {
        volatile boolean prefaceSent;
        volatile List<ByteBuffer> pending;

        boolean processReceivedData(FramesDecoder decoder, ByteBuffer buf)
                throws IOException
        {
            // if preface is not sent, buffers data in the pending list
            if (!prefaceSent) {
                if (debug.on())
                    debug.log("Preface not sent: buffering %d", buf.remaining());
                stateLock.lock();
                try {
                    if (!prefaceSent) {
                        if (pending == null) pending = new ArrayList<>();
                        pending.add(buf);
                        if (debug.on())
                            debug.log("there are now %d bytes buffered waiting for preface to be sent"
                                    + Utils.remaining(pending)
                            );
                        return false;
                    }
                } finally {
                    stateLock.unlock();
                }
            }

            // Preface is sent. Checks for pending data and flush it.
            // We rely on this method being called from within the Http2TubeSubscriber
            // scheduler, so we know that no other thread could execute this method
            // concurrently while we're here.
            // This ensures that later incoming buffers will not
            // be processed before we have flushed the pending queue.
            // No additional locking is therefore necessary here.
            List<ByteBuffer> pending = this.pending;
            this.pending = null;
            if (pending != null) {
                // flush pending data
                if (debug.on()) debug.log(() -> "Processing buffered data: "
                      + Utils.remaining(pending));
                for (ByteBuffer b : pending) {
                    decoder.decode(b);
                }
            }
            // push the received buffer to the frames decoder.
            if (buf != EMPTY_TRIGGER) {
                if (debug.on()) debug.log("Processing %d", buf.remaining());
                decoder.decode(buf);
            }
            return true;
        }

        // Mark that the connection preface is sent
        void markPrefaceSent() {
            assert !prefaceSent;
            stateLock.lock();
            try {
                prefaceSent = true;
            } finally {
                stateLock.unlock();
            }
        }
    }

    private final class PushPromiseDecoder extends HeaderDecoder implements DecodingCallback {

        final int parentStreamId;
        final int pushPromiseStreamId;
        final Stream<?> parent;
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        PushPromiseDecoder(int parentStreamId, int pushPromiseStreamId, Stream<?> parent) {
            super(Context.REQUEST);
            this.parentStreamId = parentStreamId;
            this.pushPromiseStreamId = pushPromiseStreamId;
            this.parent = parent;
        }

        @Override
        protected void addHeader(String name, String value) {
            if (errorRef.get() == null) {
                super.addHeader(name, value);
            }
        }

        @Override
        public void onMaxHeaderListSizeReached(long size, int maxHeaderListSize) throws ProtocolException {
            try {
                DecodingCallback.super.onMaxHeaderListSizeReached(size, maxHeaderListSize);
            } catch (ProtocolException pe) {
                if (parent != null) {
                    if (errorRef.compareAndSet(null, pe)) {
                        // cancel the parent stream
                        resetStream(pushPromiseStreamId, ResetFrame.REFUSED_STREAM);
                        parent.onProtocolError(pe);
                    }
                } else {
                    // interrupt decoding and closes the connection
                    throw pe;
                }
            }
        }
    }


    private static final int HALF_CLOSED_LOCAL  = 1;
    private static final int HALF_CLOSED_REMOTE = 2;
    private static final int SHUTDOWN_REQUESTED = 4;
    // state when idle connection management initiates a shutdown of the connection, after
    // which the connection will go into SHUTDOWN_REQUESTED state
    private static final int IDLE_SHUTDOWN_INITIATED = 8;
    private final ReentrantLock stateLock = new ReentrantLock();
    private volatile int closedState;

    //-------------------------------------
    final HttpConnection connection;
    private final Http2ClientImpl client2;
    private final ConcurrentHashMap<Integer,Stream<?>> streams = new ConcurrentHashMap<>();
    private int nextstreamid;
    private int nextPushStream = 2;
    // actual stream ids are not allocated until the Headers frame is ready
    // to be sent. The following two fields are updated as soon as a stream
    // is created and assigned to a connection. They are checked before
    // assigning a stream to a connection.
    private int lastReservedClientStreamid = 1;
    private int lastReservedServerStreamid = 0;
    private int numReservedClientStreams = 0; // count of current streams
    private int numReservedServerStreams = 0; // count of current streams
    private final Encoder hpackOut;
    private final Decoder hpackIn;
    final SettingsFrame clientSettings;
    private volatile SettingsFrame serverSettings;
    private record PushContinuationState(PushPromiseDecoder pushContDecoder, PushPromiseFrame pushContFrame) {}
    private volatile PushContinuationState pushContinuationState;
    private final String key; // for HttpClientImpl.connections map
    private final FramesDecoder framesDecoder;
    private final FramesEncoder framesEncoder = new FramesEncoder();
    private final AtomicLong lastProcessedStreamInGoAway = new AtomicLong(-1);

    /**
     * Send Window controller for both connection and stream windows.
     * Each of this connection's Streams MUST use this controller.
     */
    private final WindowController windowController = new WindowController();
    private final FramesController framesController = new FramesController();
    private final Http2TubeSubscriber subscriber;
    final ConnectionWindowUpdateSender windowUpdater;
    private final AtomicReference<Throwable> cause = new AtomicReference<>();
    private volatile Supplier<ByteBuffer> initial;
    private volatile Stream<?> initialStream;

    private ValidatingHeadersConsumer orphanedConsumer;
    private final AtomicInteger orphanedHeaders = new AtomicInteger();

    static final int DEFAULT_FRAME_SIZE = 16 * 1024;
    static final int MAX_LITERAL_WITH_INDEXING =
            Utils.getIntegerNetProperty("jdk.httpclient.maxLiteralWithIndexing",512);

    // The maximum number of HEADER frames, CONTINUATION frames, or PUSH_PROMISE frames
    // referring to an already closed or non-existent stream that a client will accept to
    // process. Receiving frames referring to non-existent or closed streams doesn't necessarily
    // constitute an HTTP/2 protocol error, but receiving too many may indicate a problem
    // with the connection. If this limit is reached, a {@link java.net.ProtocolException
    // ProtocolException} will be raised and the connection will be closed.
    static final int MAX_ORPHANED_HEADERS = 1024;

    // TODO: need list of control frames from other threads
    // that need to be sent

    private Http2Connection(HttpConnection connection,
                            Http2ClientImpl client2,
                            int nextstreamid,
                            String key,
                            boolean defaultServerPush) {
        this.connection = connection;
        this.client2 = client2;
        this.subscriber = new Http2TubeSubscriber(client2.client());
        this.nextstreamid = nextstreamid;
        this.key = key;
        this.clientSettings = this.client2.getClientSettings(defaultServerPush);
        this.framesDecoder = new FramesDecoder(this::processFrame,
                clientSettings.getParameter(SettingsFrame.MAX_FRAME_SIZE));
        // serverSettings will be updated by server
        this.serverSettings = SettingsFrame.defaultRFCSettings();
        this.hpackOut = new Encoder(serverSettings.getParameter(HEADER_TABLE_SIZE));
        this.hpackIn = new Decoder(clientSettings.getParameter(HEADER_TABLE_SIZE),
                clientSettings.getParameter(MAX_HEADER_LIST_SIZE), MAX_LITERAL_WITH_INDEXING);
        if (debugHpack.on()) {
            debugHpack.log("For the record:" + super.toString());
            debugHpack.log("Decoder created: %s", hpackIn);
            debugHpack.log("Encoder created: %s", hpackOut);
        }
        this.windowUpdater = new ConnectionWindowUpdateSender(this,
                client2.getConnectionWindowSize(clientSettings));
    }

    /**
     * Case 1) Create from upgraded HTTP/1.1 connection.
     * Is ready to use. Can't be SSL. exchange is the Exchange
     * that initiated the connection, whose response will be delivered
     * on a Stream.
     */
    private Http2Connection(HttpConnection connection,
                    Http2ClientImpl client2,
                    Exchange<?> exchange,
                    Supplier<ByteBuffer> initial,
                    boolean defaultServerPush)
        throws IOException, InterruptedException
    {
        this(connection,
                client2,
                3, // stream 1 is registered during the upgrade
                keyFor(connection),
                defaultServerPush);
        reserveStream(true, clientSettings.getFlag(ENABLE_PUSH));
        Log.logTrace("Connection send window size {0} ", windowController.connectionWindowSize());

        Stream<?> initialStream = createStream(exchange);
        boolean opened = initialStream.registerStream(1, true);
        this.initialStream = initialStream;
        if (debug.on() && !opened) {
            debug.log("Initial stream was cancelled - but connection is maintained: " +
                    "reset frame will need to be sent later");
        }
        windowController.registerStream(1, getInitialSendWindowSize());
        initialStream.requestSent();
        // Upgrading:
        //    set callbacks before sending preface - makes sure anything that
        //    might be sent by the server will come our way.
        this.initial = initial;
        connectFlows(connection);
        sendConnectionPreface();
        if (!opened) {
            debug.log("ensure reset frame is sent to cancel initial stream");
            initialStream.sendResetStreamFrame(ResetFrame.CANCEL);
        }

    }

    // Used when upgrading an HTTP/1.1 connection to HTTP/2 after receiving
    // agreement from the server. Async style but completes immediately, because
    // the connection is already connected.
    static CompletableFuture<Http2Connection> createAsync(HttpConnection connection,
                                                          Http2ClientImpl client2,
                                                          Exchange<?> exchange,
                                                          Supplier<ByteBuffer> initial)
    {
        return MinimalFuture.supply(() -> new Http2Connection(connection, client2, exchange, initial,
                exchange.pushEnabled()));
    }

    // Requires TLS handshake. So, is really async
    static CompletableFuture<Http2Connection> createAsync(HttpRequestImpl request,
                                                          Http2ClientImpl h2client,
                                                          Exchange<?> exchange) {
        assert request.secure();
        AbstractAsyncSSLConnection connection = (AbstractAsyncSSLConnection)
        HttpConnection.getConnection(request.getAddress(),
                                     h2client.client(),
                                     request,
                                     HttpClient.Version.HTTP_2);

        // Expose the underlying connection to the exchange's aborter so it can
        // be closed if a timeout occurs.
        exchange.connectionAborter.connection(connection);

        return connection.connectAsync(exchange)
                  .thenCompose(unused -> connection.finishConnect())
                  .thenCompose(unused -> checkSSLConfig(connection))
                  .thenCompose(notused-> {
                      CompletableFuture<Http2Connection> cf = new MinimalFuture<>();
                      try {
                          Http2Connection hc = new Http2Connection(request, h2client,
                                  connection, exchange.pushEnabled());
                          cf.complete(hc);
                      } catch (IOException e) {
                          cf.completeExceptionally(e);
                      }
                      return cf; } );
    }

    /**
     * Cases 2) 3)
     *
     * request is request to be sent.
     */
    private Http2Connection(HttpRequestImpl request,
                            Http2ClientImpl h2client,
                            HttpConnection connection,
                            boolean defaultServerPush)
        throws IOException
    {
        this(connection,
             h2client,
             1,
             keyFor(request),
             defaultServerPush);

        Log.logTrace("Connection send window size {0} ", windowController.connectionWindowSize());

        // safe to resume async reading now.
        connectFlows(connection);
        sendConnectionPreface();
    }

    private void connectFlows(HttpConnection connection) {
        FlowTube tube =  connection.getConnectionFlow();
        // Connect the flow to our Http2TubeSubscriber:
        tube.connectFlows(connection.publisher(), subscriber);
    }

    final HttpClientImpl client() {
        return client2.client();
    }

    // call these before assigning a request/stream to a connection
    // if false returned then a new Http2Connection is required
    // if true, the stream may be assigned to this connection
    // for server push, if false returned, then the stream should be cancelled
    boolean reserveStream(boolean clientInitiated, boolean pushEnabled) throws IOException {
        stateLock.lock();
        try {
            return reserveStream0(clientInitiated, pushEnabled);
        } finally {
            stateLock.unlock();
        }
    }

    private boolean reserveStream0(boolean clientInitiated, boolean pushEnabled) throws IOException {
        if (finalStream()) {
            return false;
        }
        // If requesting to reserve a stream for an exchange for which push is enabled,
        // we will reserve the stream in this connection only if this connection is also
        // push enabled, unless pushes are globally disabled.
        boolean pushCompatible = !clientInitiated || !pushEnabled
                || this.serverPushEnabled()
                || client2.serverPushDisabled();
        if (clientInitiated && (lastReservedClientStreamid >= MAX_CLIENT_STREAM_ID -2  || !pushCompatible)) {
            setFinalStream();
            client2.removeFromPool(this);
            return false;
        } else if (!clientInitiated && (lastReservedServerStreamid >= MAX_SERVER_STREAM_ID - 2)) {
            setFinalStream();
            client2.removeFromPool(this);
            return false;
        }
        if (clientInitiated)
            lastReservedClientStreamid+=2;
        else
            lastReservedServerStreamid+=2;

        assert numReservedClientStreams >= 0;
        assert numReservedServerStreams >= 0;
        if (clientInitiated &&numReservedClientStreams >= maxConcurrentClientInitiatedStreams()) {
            throw new IOException("too many concurrent streams");
        } else if (clientInitiated) {
            numReservedClientStreams++;
        }
        if (!clientInitiated && numReservedServerStreams >= maxConcurrentServerInitiatedStreams()) {
            return false;
        } else if (!clientInitiated) {
            numReservedServerStreams++;
        }
        return true;
    }

    boolean shouldClose() {
        stateLock.lock();
        try {
            return finalStream() && streams.isEmpty();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Throws an IOException if h2 was not negotiated
     */
    private static CompletableFuture<?> checkSSLConfig(AbstractAsyncSSLConnection aconn) {
        assert aconn.isSecure();

        Function<String, CompletableFuture<Void>> checkAlpnCF = (alpn) -> {
            CompletableFuture<Void> cf = new MinimalFuture<>();
            SSLEngine engine = aconn.getEngine();
            String engineAlpn = engine.getApplicationProtocol();
            DEBUG_LOGGER.log("checkSSLConfig: alpn: '%s', engine: '%s'", alpn, engineAlpn);
            if (alpn == null && engineAlpn != null) {
                alpn = engineAlpn;
            }
            DEBUG_LOGGER.log("checkSSLConfig: alpn: '%s'", alpn );

            if (alpn == null || !alpn.equals(Alpns.H2)) {
                String msg;
                if (alpn == null) {
                    Log.logSSL("ALPN not supported");
                    msg = "ALPN not supported";
                } else {
                    switch (alpn) {
                        case "":
                            Log.logSSL(msg = "No ALPN negotiated");
                            break;
                        case Alpns.HTTP_1_1:
                            Log.logSSL( msg = "HTTP/1.1 ALPN returned");
                            break;
                        default:
                            Log.logSSL(msg = "Unexpected ALPN: " + alpn);
                            cf.completeExceptionally(new IOException(msg));
                    }
                }
                cf.completeExceptionally(new ALPNException(msg, aconn));
                return cf;
            }
            assert Objects.equals(alpn, engineAlpn)
                    : "alpn: %s, engine: %s".formatted(alpn, engineAlpn);
            cf.complete(null);
            return cf;
        };

        return aconn.getALPN()
                .whenComplete((r,t) -> {
                    if (t != null && t instanceof SSLException) {
                        // something went wrong during the initial handshake
                        // close the connection
                        aconn.close(t);
                    }
                })
                .thenCompose(checkAlpnCF);
    }

    boolean finalStream() {
        return finalStream;
    }

    /**
     * Mark this connection so no more streams created on it and it will close when
     * all are complete.
     */
    void setFinalStream() {
        finalStream = true;
    }

    static String keyFor(HttpConnection connection) {
        boolean isProxy = connection.isProxied(); // tunnel or plain clear connection through proxy
        boolean isSecure = connection.isSecure();
        InetSocketAddress addr = connection.address();
        InetSocketAddress proxyAddr = connection.proxy();
        assert isProxy == (proxyAddr != null);

        return keyString(isSecure, proxyAddr, addr.getHostString(), addr.getPort());
    }

    static String keyFor(final HttpRequestImpl request) {
        final InetSocketAddress targetAddr = request.getAddress();
        final InetSocketAddress proxy = request.proxy();
        final boolean secure = request.secure();
        return keyString(secure, proxy, targetAddr.getHostString(), targetAddr.getPort());
    }

    // Compute the key for an HttpConnection in the Http2ClientImpl pool:
    // The key string follows one of the three forms below:
    //    {C,S}:H:host:port
    //    C:P:proxy-host:proxy-port
    //    S:T:H:host:port;P:proxy-host:proxy-port
    // C indicates clear text connection "http"
    // S indicates secure "https"
    // H indicates host (direct) connection
    // P indicates proxy
    // T indicates a tunnel connection through a proxy
    //
    // The first form indicates a direct connection to a server:
    //   - direct clear connection to an HTTP host:
    //     e.g.: "C:H:foo.com:80"
    //   - direct secure connection to an HTTPS host:
    //     e.g.: "S:H:foo.com:443"
    // The second form indicates a clear connection to an HTTP/1.1 proxy:
    //     e.g.: "C:P:myproxy:8080"
    // The third form indicates a secure tunnel connection to an HTTPS
    // host through an HTTP/1.1 proxy:
    //     e.g: "S:T:H:foo.com:80;P:myproxy:8080"
    static String keyString(boolean secure, InetSocketAddress proxy, String host, int port) {
        if (secure && port == -1)
            port = 443;
        else if (!secure && port == -1)
            port = 80;
        var key = (secure ? "S:" : "C:");
        if (proxy != null && !secure) {
            // clear connection through proxy
            key = key + "P:" + proxy.getHostString() + ":" + proxy.getPort();
        } else if (proxy == null) {
            // direct connection to host
            key = key + "H:" + host + ":" + port;
        } else {
            // tunnel connection through proxy
            key = key + "T:H:" + host + ":" + port + ";P:" + proxy.getHostString() + ":" + proxy.getPort();
        }
        return  key;
    }

    String key() {
        return this.key;
    }

    public boolean serverPushEnabled() {
        return clientSettings.getParameter(SettingsFrame.ENABLE_PUSH) == 1;
    }

    boolean offerConnection() {
        return client2.offerConnection(this);
    }

    private HttpPublisher publisher() {
        return connection.publisher();
    }

    private void decodeHeaders(HeaderFrame frame, DecodingCallback decoder)
            throws IOException
    {
        if (debugHpack.on()) debugHpack.log("decodeHeaders(%s)", decoder);

        boolean endOfHeaders = frame.getFlag(HeaderFrame.END_HEADERS);

        List<ByteBuffer> buffers = frame.getHeaderBlock();
        int len = buffers.size();
        for (int i = 0; i < len; i++) {
            ByteBuffer b = buffers.get(i);
            hpackIn.decode(b, endOfHeaders && (i == len - 1), decoder);
        }
    }

    final int getInitialSendWindowSize() {
        return serverSettings.getParameter(INITIAL_WINDOW_SIZE);
    }

    final int maxConcurrentClientInitiatedStreams() {
        return serverSettings.getParameter(MAX_CONCURRENT_STREAMS);
    }

    final int maxConcurrentServerInitiatedStreams() {
        return clientSettings.getParameter(MAX_CONCURRENT_STREAMS);
    }

    void close() {
        if (markHalfClosedLocal()) {
            // we send a GOAWAY frame only if the remote side hasn't already indicated
            // the intention to close the connection by previously sending a GOAWAY of its own
            if (connection.channel().isOpen() && !isMarked(closedState, HALF_CLOSED_REMOTE)) {
                Log.logTrace("Closing HTTP/2 connection: to {0}", connection.address());
                GoAwayFrame f = new GoAwayFrame(0,
                        ErrorFrame.NO_ERROR,
                        "Requested by user".getBytes(UTF_8));
                // TODO: set last stream. For now zero ok.
                sendFrame(f);
            }
        }
    }

    long count;
    final void asyncReceive(ByteBuffer buffer) {
        // We don't need to read anything and
        // we don't want to send anything back to the server
        // until the connection preface has been sent.
        // Therefore we're going to wait if needed before reading
        // (and thus replying) to anything.
        // Starting to reply to something (e.g send an ACK to a
        // SettingsFrame sent by the server) before the connection
        // preface is fully sent might result in the server
        // sending a GOAWAY frame with 'invalid_preface'.
        //
        // Note: asyncReceive is only called from the Http2TubeSubscriber
        //       sequential scheduler.
        try {
            Supplier<ByteBuffer> bs = initial;
            // ensure that we always handle the initial buffer first,
            // if any.
            if (bs != null) {
                initial = null;
                ByteBuffer b = bs.get();
                if (b.hasRemaining()) {
                    long c = ++count;
                    if (debug.on())
                        debug.log(() -> "H2 Receiving Initial(" + c +"): " + b.remaining());
                    framesController.processReceivedData(framesDecoder, b);
                }
            }
            ByteBuffer b = buffer;
            // the Http2TubeSubscriber scheduler ensures that the order of incoming
            // buffers is preserved.
            if (b == EMPTY_TRIGGER) {
                if (debug.on()) debug.log("H2 Received EMPTY_TRIGGER");
                boolean prefaceSent = framesController.prefaceSent;
                assert prefaceSent;
                // call framesController.processReceivedData to potentially
                // trigger the processing of all the data buffered there.
                framesController.processReceivedData(framesDecoder, buffer);
                if (debug.on()) debug.log("H2 processed buffered data");
            } else {
                long c = ++count;
                if (debug.on())
                    debug.log("H2 Receiving(%d): %d", c, b.remaining());
                framesController.processReceivedData(framesDecoder, buffer);
                if (debug.on()) debug.log("H2 processed(%d)", c);
            }
        } catch (Throwable e) {
            String msg = Utils.stackTrace(e);
            Log.logTrace(msg);
            shutdown(e);
        }
    }

    Throwable getRecordedCause() {
        return cause.get();
    }

    void shutdown(Throwable t) {
        int state = closedState;
        if (debug.on()) debug.log(() -> "Shutting down h2c (state="+describeClosedState(state)+"): " + t);
        stateLock.lock();
        try {
            if (!markShutdownRequested()) return;
            cause.compareAndSet(null, t);
        } finally {
            stateLock.unlock();
        }

        if (Log.errors()) {
            if (t!= null && (!(t instanceof EOFException) || isActive())) {
                Log.logError(t);
            } else if (t != null) {
                Log.logError("Shutting down connection: {0}", t.getMessage());
            } else {
                Log.logError("Shutting down connection");
            }
        }
        client2.removeFromPool(this);
        subscriber.stop(cause.get());
        for (Stream<?> s : streams.values()) {
            try {
                s.connectionClosing(t);
            } catch (Throwable e) {
                Log.logError("Failed to close stream {0}: {1}", s.streamid, e);
            }
        }
        connection.close(cause.get());
    }

    /**
     * Streams initiated by a client MUST use odd-numbered stream
     * identifiers; those initiated by the server MUST use even-numbered
     * stream identifiers.
     */
    private static final boolean isServerInitiatedStream(int streamid) {
        return (streamid & 0x1) == 0;
    }

    /**
     * Handles stream 0 (common) frames that apply to whole connection and passes
     * other stream specific frames to that Stream object.
     *
     * Invokes Stream.incoming() which is expected to process frame without
     * blocking.
     */
    void processFrame(Http2Frame frame) throws IOException {
        Log.logFrames(frame, "IN");
        int streamid = frame.streamid();
        if (frame instanceof MalformedFrame) {
            Log.logError(((MalformedFrame) frame).getMessage());
            if (streamid == 0) {
                framesDecoder.close("Malformed frame on stream 0");
                protocolError(((MalformedFrame) frame).getErrorCode(),
                        ((MalformedFrame) frame).getMessage());
            } else {
                if (debug.on())
                    debug.log(() -> "Reset stream: " + ((MalformedFrame) frame).getMessage());
                resetStream(streamid, ((MalformedFrame) frame).getErrorCode());
            }
            return;
        }
        if (streamid == 0) {
            handleConnectionFrame(frame);
        } else {
            if (frame instanceof SettingsFrame) {
                // The stream identifier for a SETTINGS frame MUST be zero
                framesDecoder.close(
                        "The stream identifier for a SETTINGS frame MUST be zero");
                protocolError(GoAwayFrame.PROTOCOL_ERROR);
                return;
            }

            if (frame instanceof PushPromiseFrame && !serverPushEnabled()) {
                String protocolError = "received a PUSH_PROMISE when SETTINGS_ENABLE_PUSH is 0";
                protocolError(ResetFrame.PROTOCOL_ERROR, protocolError);
                return;
            }

            Stream<?> stream = getStream(streamid);
            var nextstreamid = this.nextstreamid;
            if (stream == null && (streamid & 0x01) == 0x01 && streamid >= nextstreamid) {
                String protocolError = String.format(
                        "received a frame for a non existing streamid(%s) >= nextstreamid(%s)",
                        streamid, nextstreamid);
                protocolError(ResetFrame.PROTOCOL_ERROR, protocolError);
                return;
            }
            PushContinuationState pcs = pushContinuationState;
            if (stream == null && pcs == null) {
                // Should never receive a frame with unknown stream id

                if (frame instanceof HeaderFrame hf) {
                    String protocolError = checkMaxOrphanedHeadersExceeded(hf);
                    if (protocolError != null) {
                        protocolError(ResetFrame.PROTOCOL_ERROR, protocolError);
                        return;
                    }
                    // always decode the headers as they may affect
                    // connection-level HPACK decoding state
                    if (orphanedConsumer == null || frame.getClass() != ContinuationFrame.class) {
                        orphanedConsumer = new ValidatingHeadersConsumer(
                                frame instanceof PushPromiseFrame ?
                                        Context.REQUEST :
                                        Context.RESPONSE);
                    }
                    DecodingCallback decoder = orphanedConsumer::onDecoded;
                    try {
                        decodeHeaders(hf, decoder);
                    } catch (IOException | UncheckedIOException e) {
                        protocolError(ResetFrame.PROTOCOL_ERROR, e.getMessage());
                        return;
                    }
                }

                if (!(frame instanceof ResetFrame)) {
                    if (frame instanceof DataFrame df) {
                        dropDataFrame(df);
                    }
                    if (isServerInitiatedStream(streamid)) {
                        if (streamid < nextPushStream) {
                            // trailing data on a cancelled push promise stream,
                            // reset will already have been sent, ignore
                            Log.logTrace("Ignoring cancelled push promise frame " + frame);
                        } else {
                            resetStream(streamid, ResetFrame.PROTOCOL_ERROR);
                        }
                    } else if (streamid >= nextstreamid) {
                        // otherwise the stream has already been reset/closed
                        resetStream(streamid, ResetFrame.PROTOCOL_ERROR);
                    }
                }
                return;
            }

            // While push frame is not null, the only acceptable frame on this
            // stream is a Continuation frame
            if (pcs != null) {
                if (frame instanceof ContinuationFrame cf) {
                    if (stream == null) {
                        String protocolError = checkMaxOrphanedHeadersExceeded(cf);
                        if (protocolError != null) {
                            protocolError(ResetFrame.PROTOCOL_ERROR, protocolError);
                            return;
                        }
                    }
                    try {
                        if (streamid == pcs.pushContFrame.streamid())
                            handlePushContinuation(pcs, stream, cf);
                        else {
                            String protocolError = "Received a CONTINUATION with " +
                                    "unexpected stream id: " + streamid + " != "
                                    + pcs.pushContFrame.streamid();
                            protocolError(ErrorFrame.PROTOCOL_ERROR, protocolError);
                        }
                    } catch (IOException | UncheckedIOException e) {
                        debug.log("Error handling Push Promise with Continuation: " + e.getMessage(), e);
                        protocolError(ErrorFrame.PROTOCOL_ERROR, e.getMessage());
                        return;
                    }
                } else {
                    pushContinuationState = null;
                    String protocolError = "Expected a CONTINUATION frame but received " + frame;
                    protocolError(ErrorFrame.PROTOCOL_ERROR, protocolError);
                    return;
                }
            } else {
                if (frame instanceof PushPromiseFrame pp) {
                    try {
                        handlePushPromise(stream, pp);
                    } catch (IOException | UncheckedIOException e) {
                        protocolError(ErrorFrame.PROTOCOL_ERROR, e.getMessage());
                        return;
                    }
                } else if (frame instanceof HeaderFrame hf) {
                    // decode headers
                    try {
                        decodeHeaders(hf, stream.rspHeadersConsumer());
                    } catch (IOException | UncheckedIOException e) {
                        debug.log("Error decoding headers: " + e.getMessage(), e);
                        protocolError(ErrorFrame.PROTOCOL_ERROR, e.getMessage());
                        return;
                    }
                    stream.incoming(frame);
                } else {
                    stream.incoming(frame);
                }
            }
        }
    }

    private String checkMaxOrphanedHeadersExceeded(HeaderFrame hf) {
        if (MAX_ORPHANED_HEADERS > 0 ) {
            int orphaned = orphanedHeaders.incrementAndGet();
            if (orphaned < 0 || orphaned > MAX_ORPHANED_HEADERS) {
               return "Too many orphaned header frames received on connection";
            }
        }
        return null;
    }

    // This method is called when a DataFrame that was added
    // to a Stream::inputQ is later dropped from the queue
    // without being consumed.
    //
    // Before adding a frame to the queue, the Stream calls
    // connection.windowUpdater.canBufferUnprocessedBytes(), which
    // increases the count of unprocessed bytes in the connection.
    // After consuming the frame, it calls connection.windowUpdater::processed,
    // which decrements the count of unprocessed bytes, and possibly
    // sends a window update to the peer.
    //
    // This method is called when connection.windowUpdater::processed
    // will not be called, which can happen when consuming the frame
    // fails, or when an empty DataFrame terminates the stream,
    // or when the stream is cancelled while data is still
    // sitting in its inputQ. In the later case, it is called for
    // each frame that is dropped from the queue.
    final void releaseUnconsumed(DataFrame df) {
        windowUpdater.released(df.payloadLength());
        dropDataFrame(df);
    }

    // This method can be called directly when a DataFrame is dropped
    // before/without having been added to any Stream::inputQ.
    // In that case, the number of unprocessed bytes hasn't been incremented
    // by the stream, and does not need to be decremented.
    // Otherwise, if the frame is dropped after having been added to the
    // inputQ, releaseUnconsumed above should be called.
    final void dropDataFrame(DataFrame df) {
        if (isMarked(closedState, SHUTDOWN_REQUESTED)) return;
        if (debug.on()) {
            debug.log("Dropping data frame for stream %d (%d payload bytes)",
                    df.streamid(), df.payloadLength());
        }
        ensureWindowUpdated(df);
    }

    final void ensureWindowUpdated(DataFrame df) {
        try {
            if (isMarked(closedState, SHUTDOWN_REQUESTED)) return;
            int length = df.payloadLength();
            if (length > 0) {
                windowUpdater.update(length);
            }
        } catch(Throwable t) {
            Log.logError("Unexpected exception while updating window: {0}", (Object)t);
        }
    }

    private <T> void handlePushPromise(Stream<T> parent, PushPromiseFrame pp)
        throws IOException
    {
        int promisedStreamid = pp.getPromisedStream();
        if ((promisedStreamid & 0x01) != 0x00) {
            throw new ProtocolException("Received PUSH_PROMISE for stream " + promisedStreamid);
        }
        int streamId = pp.streamid();
        if ((streamId & 0x01) != 0x01) {
            throw new ProtocolException("Received PUSH_PROMISE on stream " + streamId);
        }
        // always decode the headers as they may affect connection-level HPACK
        // decoding state
        assert pushContinuationState == null;
        PushPromiseDecoder decoder = new PushPromiseDecoder(streamId, promisedStreamid, parent);
        decodeHeaders(pp, decoder);
        if (pp.endHeaders()) {
            if (decoder.errorRef.get() == null) {
                completePushPromise(promisedStreamid, parent, decoder.headers());
            }
        } else {
            pushContinuationState = new PushContinuationState(decoder, pp);
        }
    }

    private <T> void handlePushContinuation(PushContinuationState pcs, Stream<T> parent, ContinuationFrame cf)
            throws IOException {
        assert pcs.pushContFrame.streamid() == cf.streamid() : String.format(
                    "Received CONTINUATION on a different stream %s != %s",
                    cf.streamid(), pcs.pushContFrame.streamid());
        decodeHeaders(cf, pcs.pushContDecoder);
        // if all continuations are sent, set pushWithContinuation to null
        if (cf.endHeaders()) {
            if (pcs.pushContDecoder.errorRef.get() == null) {
                completePushPromise(pcs.pushContFrame.getPromisedStream(), parent,
                        pcs.pushContDecoder.headers());
            }
            pushContinuationState = null;
        }
    }

    private <T> void completePushPromise(int promisedStreamid, Stream<T> parent, HttpHeaders headers)
            throws IOException {
        if (parent == null) {
            resetStream(promisedStreamid, ResetFrame.REFUSED_STREAM);
            return;
        }
        HttpRequestImpl parentReq = parent.request;
        if (promisedStreamid < nextPushStream) {
            // From RFC 9113 section 5.1.1:
            // The identifier of a newly established stream MUST be numerically
            // greater than all streams that the initiating endpoint has
            // opened or reserved.
            protocolError(ResetFrame.PROTOCOL_ERROR, String.format(
                    "Unexpected stream identifier: %s < %s", promisedStreamid, nextPushStream));
            return;
        }
        if (promisedStreamid != nextPushStream) {
            // we don't support skipping stream ids;
            resetStream(promisedStreamid, ResetFrame.PROTOCOL_ERROR);
            return;
        } else if (!reserveStream(false, true)) {
            resetStream(promisedStreamid, ResetFrame.REFUSED_STREAM);
            return;
        } else {
            nextPushStream += 2;
        }

        HttpRequestImpl pushReq = HttpRequestImpl.createPushRequest(parentReq, headers);
        Exchange<T> pushExch = new Exchange<>(pushReq, parent.exchange.multi);
        Stream.PushedStream<T> pushStream = createPushStream(parent, pushExch);
        pushExch.exchImpl = pushStream;
        pushStream.registerStream(promisedStreamid, true);
        parent.incoming_pushPromise(pushReq, pushStream);
    }

    private void handleConnectionFrame(Http2Frame frame)
        throws IOException
    {
        switch (frame.type()) {
            case SettingsFrame.TYPE     -> handleSettings((SettingsFrame) frame);
            case PingFrame.TYPE         -> handlePing((PingFrame) frame);
            case GoAwayFrame.TYPE       -> handleGoAway((GoAwayFrame) frame);
            case WindowUpdateFrame.TYPE -> handleWindowUpdate((WindowUpdateFrame) frame);

            default -> protocolError(ErrorFrame.PROTOCOL_ERROR);
        }
    }

    boolean isOpen() {
        return !isMarkedForShutdown() && connection.channel().isOpen();
    }

    void resetStream(int streamid, int code) {
        try {
            if (connection.channel().isOpen()) {
                // no need to try & send a reset frame if the
                // connection channel is already closed.
                Log.logError(
                        "Resetting stream {0,number,integer} with error code {1,number,integer}",
                        streamid, code);
                markStream(streamid, code);
                ResetFrame frame = new ResetFrame(streamid, code);
                sendFrame(frame);
            } else if (debug.on()) {
                debug.log("Channel already closed, no need to reset stream %d",
                          streamid);
            }
        } finally {
            decrementStreamsCount(streamid);
            closeStream(streamid);
        }
    }

    private void markStream(int streamid, int code) {
        Stream<?> s = streams.get(streamid);
        if (s != null) s.markStream(code);
    }

    // reduce count of streams by 1 if stream still exists
    void decrementStreamsCount(int streamid) {
        stateLock.lock();
        try {
            decrementStreamsCount0(streamid);
        } finally {
            stateLock.unlock();
        }

    }
    private void decrementStreamsCount0(int streamid) {
        Stream<?> s = streams.get(streamid);
        if (s == null || !s.deRegister())
            return;
        if (streamid % 2 == 1) {
            numReservedClientStreams--;
            assert numReservedClientStreams >= 0 :
                    "negative client stream count for stream=" + streamid;
        } else {
            numReservedServerStreams--;
            assert numReservedServerStreams >= 0 :
                    "negative server stream count for stream=" + streamid;
        }
    }

    // This method is called when the HTTP/2 client is being
    // stopped. Do not call it from anywhere else.
    void closeAllStreams() {
        if (debug.on()) debug.log("Close all streams");
        for (var streamId : streams.keySet()) {
            // safe to call without locking - see Stream::deRegister
            decrementStreamsCount(streamId);
            closeStream(streamId);
        }
    }

    // Check if this is the last stream aside from stream 0,
    // arm timeout
    void closeStream(int streamid) {
        if (debug.on()) debug.log("Closed stream %d", streamid);

        Stream<?> s;
        stateLock.lock();
        try {
            s = streams.remove(streamid);
            if (s != null) {
                // decrement the reference count on the HttpClientImpl
                // to allow the SelectorManager thread to exit if no
                // other operation is pending and the facade is no
                // longer referenced.
                client().streamUnreference();
            }
        } finally {
            stateLock.unlock();
        }
        // ## Remove s != null. It is a hack for delayed cancellation,reset
        if (s != null && !(s instanceof Stream.PushedStream)) {
            // Since PushStreams have no request body, then they have no
            // corresponding entry in the window controller.
            windowController.removeStream(streamid);
        }
        if (finalStream() && streams.isEmpty()) {
            // should be only 1 stream, but there might be more if server push
            close();
        } else {
            // Start timer if property present and not already created
            stateLock.lock();
            try {
                // idleConnectionTimeoutEvent is always accessed within a lock protected block
                if (streams.isEmpty() && idleConnectionTimeoutEvent == null) {
                    idleConnectionTimeoutEvent = client().idleConnectionTimeout()
                            .map(IdleConnectionTimeoutEvent::new)
                            .orElse(null);
                    if (idleConnectionTimeoutEvent != null) {
                        client().registerTimer(idleConnectionTimeoutEvent);
                    }
                }
            } finally {
                stateLock.unlock();
            }
        }
    }

    /**
     * Increments this connection's send Window by the amount in the given frame.
     */
    private void handleWindowUpdate(WindowUpdateFrame f)
        throws IOException
    {
        int amount = f.getUpdate();
        if (amount <= 0) {
            // ## temporarily disable to workaround a bug in Jetty where it
            // ## sends Window updates with a 0 update value.
            //protocolError(ErrorFrame.PROTOCOL_ERROR);
        } else {
            boolean success = windowController.increaseConnectionWindow(amount);
            if (!success) {
                protocolError(ErrorFrame.FLOW_CONTROL_ERROR);  // overflow
            }
        }
    }

    private void protocolError(int errorCode)
        throws IOException
    {
        protocolError(errorCode, null);
    }

    private void protocolError(int errorCode, String msg)
        throws IOException
    {
        String protocolError = "protocol error" + (msg == null?"":(": " + msg));
        ProtocolException protocolException =
                new ProtocolException(protocolError);
        if (markHalfClosedLocal()) {
            framesDecoder.close(protocolError);
            subscriber.stop(protocolException);
            if (debug.on()) debug.log("Sending GOAWAY due to " + protocolException);
            GoAwayFrame frame = new GoAwayFrame(0, errorCode);
            sendFrame(frame);
        }
        shutdown(protocolException);
    }

    private void handleSettings(SettingsFrame frame)
        throws IOException
    {
        assert frame.streamid() == 0;
        if (!frame.getFlag(SettingsFrame.ACK)) {
            int newWindowSize = frame.getParameter(INITIAL_WINDOW_SIZE);
            if (newWindowSize != -1) {
                int oldWindowSize = serverSettings.getParameter(INITIAL_WINDOW_SIZE);
                int diff = newWindowSize - oldWindowSize;
                if (diff != 0) {
                    windowController.adjustActiveStreams(diff);
                }
            }

            serverSettings.update(frame);
            sendFrame(new SettingsFrame(SettingsFrame.ACK));
        }
    }

    private void handlePing(PingFrame frame)
        throws IOException
    {
        frame.setFlag(PingFrame.ACK);
        sendUnorderedFrame(frame);
    }

    private void handleGoAway(final GoAwayFrame frame) {
        final long lastProcessedStream = frame.getLastStream();
        assert lastProcessedStream >= 0 : "unexpected last stream id: "
                + lastProcessedStream + " in GOAWAY frame";

        markHalfClosedRemote();
        setFinalStream(); // don't allow any new streams on this connection
        if (debug.on()) {
            debug.log("processing incoming GOAWAY with last processed stream id:%s in frame %s",
                    lastProcessedStream, frame);
        }
        // see if this connection has previously received a GOAWAY from the peer and if yes
        // then check if this new last processed stream id is lesser than the previous
        // known last processed stream id. Only update the last processed stream id if the new
        // one is lesser than the previous one.
        long prevLastProcessed = lastProcessedStreamInGoAway.get();
        while (prevLastProcessed == -1 || lastProcessedStream < prevLastProcessed) {
            if (lastProcessedStreamInGoAway.compareAndSet(prevLastProcessed,
                    lastProcessedStream)) {
                break;
            }
            prevLastProcessed = lastProcessedStreamInGoAway.get();
        }
        handlePeerUnprocessedStreams(lastProcessedStreamInGoAway.get());
    }

    private void handlePeerUnprocessedStreams(final long lastProcessedStream) {
        final AtomicInteger numClosed = new AtomicInteger(); // atomic merely to allow usage within lambda
        streams.forEach((id, exchange) -> {
            if (id > lastProcessedStream) {
                // any streams with an stream id higher than the last processed stream
                // can be retried (on a new connection). we close the exchange as unprocessed
                // to facilitate the retrying.
                client2.client().theExecutor().ensureExecutedAsync(exchange::closeAsUnprocessed);
                numClosed.incrementAndGet();
            }
        });
        if (debug.on()) {
            debug.log(numClosed.get() + " stream(s), with id greater than " + lastProcessedStream
                    + ", will be closed as unprocessed");
        }
    }

    /**
     * Max frame size we are allowed to send
     */
    public int getMaxSendFrameSize() {
        int param = serverSettings.getParameter(MAX_FRAME_SIZE);
        if (param == -1) {
            param = DEFAULT_FRAME_SIZE;
        }
        return param;
    }

    /**
     * Max frame size we will receive
     */
    public int getMaxReceiveFrameSize() {
        return clientSettings.getParameter(MAX_FRAME_SIZE);
    }

    private static final String CLIENT_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n";

    private static final byte[] PREFACE_BYTES =
        CLIENT_PREFACE.getBytes(StandardCharsets.ISO_8859_1);

    /**
     * Sends Connection preface and Settings frame with current preferred
     * values
     */
    private void sendConnectionPreface() throws IOException {
        Log.logTrace("{0}: start sending connection preface to {1}",
                     connection.channel().getLocalAddress(),
                     connection.address());
        SettingsFrame sf = new SettingsFrame(clientSettings);
        ByteBuffer buf = framesEncoder.encodeConnectionPreface(PREFACE_BYTES, sf);
        Log.logFrames(sf, "OUT");
        // send preface bytes and SettingsFrame together
        HttpPublisher publisher = publisher();
        publisher.enqueueUnordered(List.of(buf));
        publisher.signalEnqueued();
        // mark preface sent.
        framesController.markPrefaceSent();
        Log.logTrace("PREFACE_BYTES sent");
        Log.logTrace("Settings Frame sent");

        // send a Window update for the receive buffer we are using
        // minus the initial 64 K -1 specified in protocol:
        // RFC 7540, Section 6.9.2:
        // "[...] the connection flow-control window is set to the default
        // initial window size until a WINDOW_UPDATE frame is received."
        //
        // Note that the default initial window size, not to be confused
        // with the initial window size, is defined by RFC 7540 as
        // 64K -1.
        final int len = windowUpdater.initialWindowSize - INITIAL_CONNECTION_WINDOW_SIZE;
        assert len >= 0;
        if (len > 0) {
            if (Log.channel()) {
                Log.logChannel("Sending initial connection window update frame: {0} ({1} - {2})",
                        len, windowUpdater.initialWindowSize, INITIAL_CONNECTION_WINDOW_SIZE);
            }
            windowUpdater.sendWindowUpdate(len);
        }
        // there will be an ACK to the windows update - which should
        // cause any pending data stored before the preface was sent to be
        // flushed (see PrefaceController).
        Log.logTrace("finished sending connection preface");
        if (debug.on())
            debug.log("Triggering processing of buffered data"
                      + " after sending connection preface");
        subscriber.onNext(List.of(EMPTY_TRIGGER));
    }

    /**
     * Called to get the initial stream after a connection upgrade.
     * If the stream was cancelled, it might no longer be in the
     * stream map. Therefore - we use the initialStream field
     * instead, and reset it to null after returning it.
     * @param <T> the response type
     * @return the initial stream created during the upgrade.
     */
    @SuppressWarnings("unchecked")
    <T> Stream<T> getInitialStream() {
         var s = (Stream<T>) initialStream;
         initialStream = null;
         return s;
    }

    /**
     * Returns an existing Stream with given id, or null if doesn't exist
     */
    @SuppressWarnings("unchecked")
    <T> Stream<T> getStream(int streamid) {
        return (Stream<T>)streams.get(streamid);
    }

    /**
     * Creates Stream with given id.
     */
    final <T> Stream<T> createStream(Exchange<T> exchange) {
        Stream<T> stream = new Stream<>(this, exchange, windowController);
        return stream;
    }

    <T> Stream.PushedStream<T> createPushStream(Stream<T> parent, Exchange<T> pushEx) {
        PushGroup<T> pg = parent.exchange.getPushGroup();
        return new Stream.PushedStream<>(parent, pg, this, pushEx);
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
        stateLock.lock();
        try {
            cancelIdleShutdownEvent();
            // consider the reservation successful only if the connection's state hasn't moved
            // to "being closed"
            return isOpen();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Cancels any event that might have been scheduled to shutdown this connection. Must be called
     * with the stateLock held.
     */
    private void cancelIdleShutdownEvent() {
        assert stateLock.isHeldByCurrentThread() : "Current thread doesn't hold " + stateLock;
        if (idleConnectionTimeoutEvent == null) {
            return;
        }
        idleConnectionTimeoutEvent.cancel();
        idleConnectionTimeoutEvent = null;
    }

    <T> void putStream(Stream<T> stream, int streamid) {
        // increment the reference count on the HttpClientImpl
        // to prevent the SelectorManager thread from exiting until
        // the stream is closed.
        stateLock.lock();
        try {
            if (!isMarkedForShutdown()) {
                if (debug.on()) {
                    debug.log("Opened stream %d", streamid);
                }
                client().streamReference();
                streams.put(streamid, stream);
                cancelIdleShutdownEvent();
                return;
            }
        } finally {
            stateLock.unlock();
        }
        if (debug.on()) debug.log("connection closed: closing stream %d", stream);
        stream.cancel(new IOException("Stream " + streamid + " cancelled", cause.get()));
    }

    /**
     * Encode the headers into a List<ByteBuffer> and then create HEADERS
     * and CONTINUATION frames from the list and return the List<Http2Frame>.
     */
    private List<HeaderFrame> encodeHeaders(OutgoingHeaders<Stream<?>> frame) {
        // max value of frame size is clamped by default frame size to avoid OOM
        int bufferSize = Math.min(Math.max(getMaxSendFrameSize(), 1024), DEFAULT_FRAME_SIZE);
        List<ByteBuffer> buffers = encodeHeadersImpl(
                bufferSize,
                frame.getAttachment().getRequestPseudoHeaders(),
                frame.getUserHeaders(),
                frame.getSystemHeaders());

        List<HeaderFrame> frames = new ArrayList<>(buffers.size());
        Iterator<ByteBuffer> bufIterator = buffers.iterator();
        HeaderFrame oframe = new HeadersFrame(frame.streamid(), frame.getFlags(), bufIterator.next());
        frames.add(oframe);
        while(bufIterator.hasNext()) {
            oframe = new ContinuationFrame(frame.streamid(), bufIterator.next());
            frames.add(oframe);
        }
        oframe.setFlag(HeaderFrame.END_HEADERS);
        return frames;
    }

    // Dedicated cache for headers encoding ByteBuffer.
    // There can be no concurrent access to this  buffer as all access to this buffer
    // and its content happen within a single critical code block section protected
    // by the sendLock. / (see sendFrame())
    // private final ByteBufferPool headerEncodingPool = new ByteBufferPool();

    private ByteBuffer getHeaderBuffer(int size) {
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.limit(size);
        return buf;
    }

    /*
     * Encodes all the headers from the given HttpHeaders into the given List
     * of buffers.
     *
     * From https://tools.ietf.org/html/rfc7540#section-8.1.2 :
     *
     *     ...Just as in HTTP/1.x, header field names are strings of ASCII
     *     characters that are compared in a case-insensitive fashion.  However,
     *     header field names MUST be converted to lowercase prior to their
     *     encoding in HTTP/2...
     */
    private List<ByteBuffer> encodeHeadersImpl(int bufferSize, HttpHeaders... headers) {
        ByteBuffer buffer = getHeaderBuffer(bufferSize);
        List<ByteBuffer> buffers = new ArrayList<>();
        for (HttpHeaders header : headers) {
            for (Map.Entry<String, List<String>> e : header.map().entrySet()) {
                String lKey = e.getKey().toLowerCase(Locale.US);
                List<String> values = e.getValue();
                for (String value : values) {
                    hpackOut.header(lKey, value);
                    while (!hpackOut.encode(buffer)) {
                        if (!buffer.hasRemaining()) {
                            buffer.flip();
                            buffers.add(buffer);
                            buffer = getHeaderBuffer(bufferSize);
                        }
                    }
                }
            }
        }
        buffer.flip();
        buffers.add(buffer);
        return buffers;
    }


    private List<ByteBuffer> encodeHeaders(OutgoingHeaders<Stream<?>> oh, Stream<?> stream) {
        oh.streamid(stream.streamid);
        if (Log.headers()) {
            StringBuilder sb = new StringBuilder("HEADERS FRAME (streamid=%s):\n".formatted(stream.streamid));
            sb.append("  %s %s\n".formatted(stream.request.method(), stream.request.uri()));
            Log.dumpHeaders(sb, "    ", oh.getAttachment().getRequestPseudoHeaders());
            Log.dumpHeaders(sb, "    ", oh.getSystemHeaders());
            Log.dumpHeaders(sb, "    ", oh.getUserHeaders());
            Log.logHeaders(sb.toString());
        }
        List<HeaderFrame> frames = encodeHeaders(oh);
        return encodeFrames(frames);
    }

    private List<ByteBuffer> encodeFrames(List<HeaderFrame> frames) {
        if (Log.frames()) {
            frames.forEach(f -> Log.logFrames(f, "OUT"));
        }
        return framesEncoder.encodeFrames(frames);
    }

    private Stream<?> registerNewStream(OutgoingHeaders<Stream<?>> oh) {
        Stream<?> stream = oh.getAttachment();
        assert stream.streamid == 0;
        int streamid = nextstreamid;
        Throwable cause = null;
        synchronized (this) {
            if (isMarked(closedState, SHUTDOWN_REQUESTED)) {
                cause = this.cause.get();
                if (cause == null) {
                    cause = new IOException("Connection closed");
                }
            }
        }
        if (cause != null) {
            stream.cancelImpl(cause);
            return null;
        }
        if (stream.registerStream(streamid, false)) {
            // set outgoing window here. This allows thread sending
            // body to proceed.
            nextstreamid += 2;
            windowController.registerStream(streamid, getInitialSendWindowSize());
            return stream;
        } else {
            stream.cancelImpl(new IOException("Request cancelled"));
            if (finalStream() && streams.isEmpty()) {
                close();
            }
            return null;
        }
    }

    private final Lock sendlock = new ReentrantLock();

    void sendFrame(Http2Frame frame) {
        try {
            if (debug.on()) debug.log("sending frame: " + frame);
            HttpPublisher publisher = publisher();
            sendlock.lock();
            try {
                if (frame instanceof OutgoingHeaders) {
                    @SuppressWarnings("unchecked")
                    OutgoingHeaders<Stream<?>> oh = (OutgoingHeaders<Stream<?>>) frame;
                    Stream<?> stream = registerNewStream(oh);
                    // provide protection from inserting unordered frames between Headers and Continuation
                    if (stream != null) {
                        // we are creating a new stream: reset orphaned header count
                        orphanedHeaders.set(0);
                        publisher.enqueue(encodeHeaders(oh, stream));
                    }
                } else {
                    publisher.enqueue(encodeFrame(frame));
                }
            } finally {
                sendlock.unlock();
            }
            publisher.signalEnqueued();
        } catch (IOException e) {
            if (!isMarked(closedState, SHUTDOWN_REQUESTED)) {
                if (!client2.stopping()) {
                    Log.logError(e);
                    shutdown(e);
                } else if (debug.on()) {
                    debug.log("Failed to send %s while stopping: %s", frame, e);
                }
            }
        }
    }

    private List<ByteBuffer> encodeFrame(Http2Frame frame) {
        Log.logFrames(frame, "OUT");
        return framesEncoder.encodeFrame(frame);
    }

    void sendDataFrame(DataFrame frame) {
        try {
            HttpPublisher publisher = publisher();
            publisher.enqueue(encodeFrame(frame));
            publisher.signalEnqueued();
        } catch (IOException e) {
            if (!isMarked(closedState, SHUTDOWN_REQUESTED)) {
                if (!client2.stopping()) {
                    Log.logError(e);
                    shutdown(e);
                } else if (debug.on()) {
                    debug.log("Failed to send %s while stopping: %s", frame, e);
                }
            }
        }
    }

    /*
     * Direct call of the method bypasses locking on "sendlock" and
     * allowed only of control frames: WindowUpdateFrame, PingFrame and etc.
     * prohibited for such frames as DataFrame, HeadersFrame, ContinuationFrame.
     */
    void sendUnorderedFrame(Http2Frame frame) {
        try {
            HttpPublisher publisher = publisher();
            publisher.enqueueUnordered(encodeFrame(frame));
            publisher.signalEnqueued();
        } catch (IOException e) {
            if (!isMarked(closedState, SHUTDOWN_REQUESTED)) {
                Log.logError(e);
                shutdown(e);
            }
        }
    }

    /**
     * A simple tube subscriber for reading from the connection flow.
     */
    final class Http2TubeSubscriber implements TubeSubscriber {
        private volatile Flow.Subscription subscription;
        private volatile boolean completed;
        private volatile boolean dropped;
        private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        private final ConcurrentLinkedQueue<ByteBuffer> queue
                = new ConcurrentLinkedQueue<>();
        private final SequentialScheduler scheduler =
                SequentialScheduler.lockingScheduler(this::processQueue);
        private final HttpClientImpl client;

        Http2TubeSubscriber(HttpClientImpl client) {
            this.client = Objects.requireNonNull(client);
        }

        final void processQueue() {
            try {
                while (!queue.isEmpty() && !scheduler.isStopped()) {
                    ByteBuffer buffer = queue.poll();
                    if (debug.on())
                        debug.log("sending %d to Http2Connection.asyncReceive",
                                  buffer.remaining());
                    asyncReceive(buffer);
                }
            } catch (Throwable t) {
                errorRef.compareAndSet(null, t);
            } finally {
                Throwable x = errorRef.get();
                if (x != null) {
                    if (debug.on()) debug.log("Stopping scheduler", x);
                    scheduler.stop();
                    Http2Connection.this.shutdown(x);
                }
            }
        }

        private final void runOrSchedule() {
            if (client.isSelectorThread()) {
                scheduler.runOrSchedule(client.theExecutor());
            } else scheduler.runOrSchedule();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            // supports being called multiple time.
            // doesn't cancel the previous subscription, since that is
            // most probably the same as the new subscription.
            assert this.subscription == null || dropped == false;
            this.subscription = subscription;
            dropped = false;
            // TODO FIXME: request(1) should be done by the delegate.
            if (!completed) {
                if (debug.on())
                    debug.log("onSubscribe: requesting Long.MAX_VALUE for reading");
                subscription.request(Long.MAX_VALUE);
            } else {
                if (debug.on()) debug.log("onSubscribe: already completed");
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            if (completed) return;
            if (debug.on()) debug.log(() -> "onNext: got " + Utils.remaining(item)
                    + " bytes in " + item.size() + " buffers");
            queue.addAll(item);
            runOrSchedule();
        }

        @Override
        public void onError(Throwable throwable) {
            if (completed) return;
            if (debug.on()) debug.log(() -> "onError: " + throwable);
            errorRef.compareAndSet(null, throwable);
            completed = true;
            runOrSchedule();
        }

        @Override
        public void onComplete() {
            if (completed) return;
            String msg = isActive()
                    ? "EOF reached while reading"
                    : "Idle connection closed by HTTP/2 peer";
            if (debug.on()) debug.log(msg);
            errorRef.compareAndSet(null, new EOFException(msg));
            completed = true;
            runOrSchedule();
        }

        @Override
        public void dropSubscription() {
            if (debug.on()) debug.log("dropSubscription");
            // we could probably set subscription to null here...
            // then we might not need the 'dropped' boolean?
            dropped = true;
        }

        void stop(Throwable error) {
            if (errorRef.compareAndSet(null, error)) {
                completed = true;
                scheduler.stop();
                queue.clear();
                if (subscription != null) {
                    subscription.cancel();
                }
                queue.clear();
            }
        }
    }

    boolean isActive() {
        stateLock.lock();
        try {
            return numReservedClientStreams > 0 || numReservedServerStreams > 0;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public final String toString() {
        return dbgString();
    }

    final String dbgString() {
        return "Http2Connection("
                    + connection.getConnectionFlow() + ")";
    }

    static final class ConnectionWindowUpdateSender extends WindowUpdateSender {

        final int initialWindowSize;
        public ConnectionWindowUpdateSender(Http2Connection connection,
                                            int initialWindowSize) {
            super(connection, initialWindowSize);
            this.initialWindowSize = initialWindowSize;
        }

        @Override
        int getStreamId() {
            return 0;
        }

        @Override
        protected boolean windowSizeExceeded(long received) {
            if (connection.isOpen()) {
                try {
                    connection.protocolError(ErrorFrame.FLOW_CONTROL_ERROR,
                            "connection window exceeded (%s > %s)"
                                    .formatted(received, windowSize));
                } catch (IOException io) {
                    connection.shutdown(io);
                }
            }
            return true;
        }
    }

    /**
     * Thrown when https handshake negotiates http/1.1 alpn instead of h2
     */
    static final class ALPNException extends IOException {
        private static final long serialVersionUID = 0L;
        final transient AbstractAsyncSSLConnection connection;

        ALPNException(String msg, AbstractAsyncSSLConnection connection) {
            super(msg);
            this.connection = connection;
        }

        AbstractAsyncSSLConnection getConnection() {
            return connection;
        }
    }

    private boolean isMarked(int state, int mask) {
        return (state & mask) == mask;
    }

    private boolean isMarkedForShutdown() {
        final int closedSt = closedState;
        return isMarked(closedSt, IDLE_SHUTDOWN_INITIATED)
                || isMarked(closedSt, SHUTDOWN_REQUESTED);
    }

    private boolean markShutdownRequested() {
        return markClosedState(SHUTDOWN_REQUESTED);
    }

    private boolean markHalfClosedLocal() {
        return markClosedState(HALF_CLOSED_LOCAL);
    }

    private boolean markHalfClosedRemote() {
        return markClosedState(HALF_CLOSED_REMOTE);
    }

    private boolean markIdleShutdownInitiated() {
        return markClosedState(IDLE_SHUTDOWN_INITIATED);
    }

    private boolean markClosedState(int flag) {
        int state, desired;
        do {
            state = desired = closedState;
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
        if (isMarked(state, SHUTDOWN_REQUESTED)) {
            desc = desc == null ? "shutdown" : desc + "+shutdown";
        }
        if (isMarked(state, HALF_CLOSED_LOCAL | HALF_CLOSED_REMOTE)) {
            if (desc == null) return "closed";
            else return desc + "+closed";
        }
        if (isMarked(state, HALF_CLOSED_LOCAL)) {
            if (desc == null) return "half-closed-local";
            else return desc + "+half-closed-local";
        }
        if (isMarked(state, HALF_CLOSED_REMOTE)) {
            if (desc == null) return "half-closed-remote";
            else return desc + "+half-closed-remote";
        }
        return "0x" + Integer.toString(state, 16);
    }

    private static final VarHandle CLOSED_STATE;
    static {
        try {
            CLOSED_STATE = MethodHandles.lookup().findVarHandle(Http2Connection.class, "closedState", int.class);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }
}
