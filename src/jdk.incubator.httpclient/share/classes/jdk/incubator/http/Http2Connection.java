/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.io.EOFException;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.net.ssl.SSLEngine;
import jdk.incubator.http.HttpConnection.HttpPublisher;
import jdk.incubator.http.internal.common.FlowTube;
import jdk.incubator.http.internal.common.FlowTube.TubeSubscriber;
import jdk.incubator.http.internal.common.HttpHeadersImpl;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.SequentialScheduler;
import jdk.incubator.http.internal.common.Utils;
import jdk.incubator.http.internal.frame.ContinuationFrame;
import jdk.incubator.http.internal.frame.DataFrame;
import jdk.incubator.http.internal.frame.ErrorFrame;
import jdk.incubator.http.internal.frame.FramesDecoder;
import jdk.incubator.http.internal.frame.FramesEncoder;
import jdk.incubator.http.internal.frame.GoAwayFrame;
import jdk.incubator.http.internal.frame.HeaderFrame;
import jdk.incubator.http.internal.frame.HeadersFrame;
import jdk.incubator.http.internal.frame.Http2Frame;
import jdk.incubator.http.internal.frame.MalformedFrame;
import jdk.incubator.http.internal.frame.OutgoingHeaders;
import jdk.incubator.http.internal.frame.PingFrame;
import jdk.incubator.http.internal.frame.PushPromiseFrame;
import jdk.incubator.http.internal.frame.ResetFrame;
import jdk.incubator.http.internal.frame.SettingsFrame;
import jdk.incubator.http.internal.frame.WindowUpdateFrame;
import jdk.incubator.http.internal.hpack.Encoder;
import jdk.incubator.http.internal.hpack.Decoder;
import jdk.incubator.http.internal.hpack.DecodingCallback;

import static jdk.incubator.http.internal.frame.SettingsFrame.*;


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

    static final boolean DEBUG = Utils.DEBUG; // Revisit: temporary dev flag.
    static final boolean DEBUG_HPACK = Utils.DEBUG_HPACK; // Revisit: temporary dev flag.
    final System.Logger  debug = Utils.getDebugLogger(this::dbgString, DEBUG);
    final static System.Logger  DEBUG_LOGGER =
            Utils.getDebugLogger("Http2Connection"::toString, DEBUG);
    private final System.Logger debugHpack =
                  Utils.getHpackLogger(this::dbgString, DEBUG_HPACK);
    static final ByteBuffer EMPTY_TRIGGER = ByteBuffer.allocate(0);

    private boolean singleStream; // used only for stream 1, then closed

    /*
     *  ByteBuffer pooling strategy for HTTP/2 protocol:
     *
     * In general there are 4 points where ByteBuffers are used:
     *  - incoming/outgoing frames from/to ByteBuffers plus incoming/outgoing encrypted data
     *    in case of SSL connection.
     *
     * 1. Outgoing frames encoded to ByteBuffers.
     *    Outgoing ByteBuffers are created with requited size and frequently small (except DataFrames, etc)
     *    At this place no pools at all. All outgoing buffers should be collected by GC.
     *
     * 2. Incoming ByteBuffers (decoded to frames).
     *    Here, total elimination of BB pool is not a good idea.
     *    We don't know how many bytes we will receive through network.
     * So here we allocate buffer of reasonable size. The following life of the BB:
     * - If all frames decoded from the BB are other than DataFrame and HeaderFrame (and HeaderFrame subclasses)
     *     BB is returned to pool,
     * - If we decoded DataFrame from the BB. In that case DataFrame refers to subbuffer obtained by slice() method.
     *     Such BB is never returned to pool and will be GCed.
     * - If we decoded HeadersFrame from the BB. Then header decoding is performed inside processFrame method and
     *     the buffer could be release to pool.
     *
     * 3. SLL encrypted buffers. Here another pool was introduced and all net buffers are to/from the pool,
     *    because of we can't predict size encrypted packets.
     *
     */


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
                debug.log(Level.DEBUG, "Preface is not sent: buffering %d",
                          buf.remaining());
                synchronized (this) {
                    if (!prefaceSent) {
                        if (pending == null) pending = new ArrayList<>();
                        pending.add(buf);
                        debug.log(Level.DEBUG, () -> "there are now "
                              + Utils.remaining(pending)
                              + " bytes buffered waiting for preface to be sent");
                        return false;
                    }
                }
            }

            // Preface is sent. Checks for pending data and flush it.
            // We rely on this method being called from within the Http2TubeSubscriber
            // scheduler, so we know that no other thread could execute this method
            // concurrently while we're here.
            // This ensures that later incoming buffers will not
            // be processed before we have flushed the pending queue.
            // No additional synchronization is therefore necessary here.
            List<ByteBuffer> pending = this.pending;
            this.pending = null;
            if (pending != null) {
                // flush pending data
                debug.log(Level.DEBUG, () -> "Processing buffered data: "
                      + Utils.remaining(pending));
                for (ByteBuffer b : pending) {
                    decoder.decode(b);
                }
            }
            // push the received buffer to the frames decoder.
            if (buf != EMPTY_TRIGGER) {
                debug.log(Level.DEBUG, "Processing %d", buf.remaining());
                decoder.decode(buf);
            }
            return true;
        }

        // Mark that the connection preface is sent
        void markPrefaceSent() {
            assert !prefaceSent;
            synchronized (this) {
                prefaceSent = true;
            }
        }
    }

    volatile boolean closed;

    //-------------------------------------
    final HttpConnection connection;
    private final Http2ClientImpl client2;
    private final Map<Integer,Stream<?>> streams = new ConcurrentHashMap<>();
    private int nextstreamid;
    private int nextPushStream = 2;
    private final Encoder hpackOut;
    private final Decoder hpackIn;
    final SettingsFrame clientSettings;
    private volatile SettingsFrame serverSettings;
    private final String key; // for HttpClientImpl.connections map
    private final FramesDecoder framesDecoder;
    private final FramesEncoder framesEncoder = new FramesEncoder();

    /**
     * Send Window controller for both connection and stream windows.
     * Each of this connection's Streams MUST use this controller.
     */
    private final WindowController windowController = new WindowController();
    private final FramesController framesController = new FramesController();
    private final Http2TubeSubscriber subscriber = new Http2TubeSubscriber();
    final ConnectionWindowUpdateSender windowUpdater;
    private volatile Throwable cause;
    private volatile Supplier<ByteBuffer> initial;

    static final int DEFAULT_FRAME_SIZE = 16 * 1024;


    // TODO: need list of control frames from other threads
    // that need to be sent

    private Http2Connection(HttpConnection connection,
                            Http2ClientImpl client2,
                            int nextstreamid,
                            String key) {
        this.connection = connection;
        this.client2 = client2;
        this.nextstreamid = nextstreamid;
        this.key = key;
        this.clientSettings = this.client2.getClientSettings();
        this.framesDecoder = new FramesDecoder(this::processFrame,
                clientSettings.getParameter(SettingsFrame.MAX_FRAME_SIZE));
        // serverSettings will be updated by server
        this.serverSettings = SettingsFrame.getDefaultSettings();
        this.hpackOut = new Encoder(serverSettings.getParameter(HEADER_TABLE_SIZE));
        this.hpackIn = new Decoder(clientSettings.getParameter(HEADER_TABLE_SIZE));
        debugHpack.log(Level.DEBUG, () -> "For the record:" + super.toString());
        debugHpack.log(Level.DEBUG, "Decoder created: %s", hpackIn);
        debugHpack.log(Level.DEBUG, "Encoder created: %s", hpackOut);
        this.windowUpdater = new ConnectionWindowUpdateSender(this,
                client2.getConnectionWindowSize(clientSettings));
    }

    /**
     * Case 1) Create from upgraded HTTP/1.1 connection.
     * Is ready to use. Can be SSL. exchange is the Exchange
     * that initiated the connection, whose response will be delivered
     * on a Stream.
     */
    private Http2Connection(HttpConnection connection,
                    Http2ClientImpl client2,
                    Exchange<?> exchange,
                    Supplier<ByteBuffer> initial)
        throws IOException, InterruptedException
    {
        this(connection,
                client2,
                3, // stream 1 is registered during the upgrade
                keyFor(connection));
        Log.logTrace("Connection send window size {0} ", windowController.connectionWindowSize());

        Stream<?> initialStream = createStream(exchange);
        initialStream.registerStream(1);
        windowController.registerStream(1, getInitialSendWindowSize());
        initialStream.requestSent();
        // Upgrading:
        //    set callbacks before sending preface - makes sure anything that
        //    might be sent by the server will come our way.
        this.initial = initial;
        connectFlows(connection);
        sendConnectionPreface();
    }

    // Used when upgrading an HTTP/1.1 connection to HTTP/2 after receiving
    // agreement from the server. Async style but completes immediately, because
    // the connection is already connected.
    static CompletableFuture<Http2Connection> createAsync(HttpConnection connection,
                                                          Http2ClientImpl client2,
                                                          Exchange<?> exchange,
                                                          Supplier<ByteBuffer> initial)
    {
        return MinimalFuture.supply(() -> new Http2Connection(connection, client2, exchange, initial));
    }

    // Requires TLS handshake. So, is really async
    static CompletableFuture<Http2Connection> createAsync(HttpRequestImpl request,
                                                          Http2ClientImpl h2client) {
        assert request.secure();
        AbstractAsyncSSLConnection connection = (AbstractAsyncSSLConnection)
        HttpConnection.getConnection(request.getAddress(),
                                     h2client.client(),
                                     request,
                                     HttpClient.Version.HTTP_2);

        return connection.connectAsync()
                  .thenCompose(unused -> checkSSLConfig(connection))
                  .thenCompose(notused-> {
                      CompletableFuture<Http2Connection> cf = new MinimalFuture<>();
                      try {
                          Http2Connection hc = new Http2Connection(request, h2client, connection);
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
                            HttpConnection connection)
        throws IOException
    {
        this(connection,
             h2client,
             1,
             keyFor(request.uri(), request.proxy()));

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

    /**
     * Throws an IOException if h2 was not negotiated
     */
    private static CompletableFuture<?> checkSSLConfig(AbstractAsyncSSLConnection aconn) {
        assert aconn.isSecure();

        Function<String, CompletableFuture<Void>> checkAlpnCF = (alpn) -> {
            CompletableFuture<Void> cf = new MinimalFuture<>();
            SSLEngine engine = aconn.getEngine();
            assert Objects.equals(alpn, engine.getApplicationProtocol());

            DEBUG_LOGGER.log(Level.DEBUG, "checkSSLConfig: alpn: %s", alpn );

            if (alpn == null || !alpn.equals("h2")) {
                String msg;
                if (alpn == null) {
                    Log.logSSL("ALPN not supported");
                    msg = "ALPN not supported";
                } else {
                    switch (alpn) {
                        case "":
                            Log.logSSL(msg = "No ALPN negotiated");
                            break;
                        case "http/1.1":
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
            cf.complete(null);
            return cf;
        };

        return aconn.getALPN().thenCompose(checkAlpnCF);
    }

    synchronized boolean singleStream() {
        return singleStream;
    }

    synchronized void setSingleStream(boolean use) {
        singleStream = use;
    }

    static String keyFor(HttpConnection connection) {
        boolean isProxy = connection.isProxied();
        boolean isSecure = connection.isSecure();
        InetSocketAddress addr = connection.address();

        return keyString(isSecure, isProxy, addr.getHostString(), addr.getPort());
    }

    static String keyFor(URI uri, InetSocketAddress proxy) {
        boolean isSecure = uri.getScheme().equalsIgnoreCase("https");
        boolean isProxy = proxy != null;

        String host;
        int port;

        if (proxy != null) {
            host = proxy.getHostString();
            port = proxy.getPort();
        } else {
            host = uri.getHost();
            port = uri.getPort();
        }
        return keyString(isSecure, isProxy, host, port);
    }

    // {C,S}:{H:P}:host:port
    // C indicates clear text connection "http"
    // S indicates secure "https"
    // H indicates host (direct) connection
    // P indicates proxy
    // Eg: "S:H:foo.com:80"
    static String keyString(boolean secure, boolean proxy, String host, int port) {
        if (secure && port == -1)
            port = 443;
        else if (!secure && port == -1)
            port = 80;
        return (secure ? "S:" : "C:") + (proxy ? "P:" : "H:") + host + ":" + port;
    }

    String key() {
        return this.key;
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
        debugHpack.log(Level.DEBUG, "decodeHeaders(%s)", decoder);

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

    void close() {
        Log.logTrace("Closing HTTP/2 connection: to {0}", connection.address());
        GoAwayFrame f = new GoAwayFrame(0, ErrorFrame.NO_ERROR, "Requested by user".getBytes());
        // TODO: set last stream. For now zero ok.
        sendFrame(f);
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
                    debug.log(Level.DEBUG, () -> "H2 Receiving Initial("
                        + c +"): " + b.remaining());
                    framesController.processReceivedData(framesDecoder, b);
                }
            }
            ByteBuffer b = buffer;
            // the Http2TubeSubscriber scheduler ensures that the order of incoming
            // buffers is preserved.
            if (b == EMPTY_TRIGGER) {
                debug.log(Level.DEBUG, "H2 Received EMPTY_TRIGGER");
                boolean prefaceSent = framesController.prefaceSent;
                assert prefaceSent;
                // call framesController.processReceivedData to potentially
                // trigger the processing of all the data buffered there.
                framesController.processReceivedData(framesDecoder, buffer);
                debug.log(Level.DEBUG, "H2 processed buffered data");
            } else {
                long c = ++count;
                debug.log(Level.DEBUG, "H2 Receiving(%d): %d", c, b.remaining());
                framesController.processReceivedData(framesDecoder, buffer);
                debug.log(Level.DEBUG, "H2 processed(%d)", c);
            }
        } catch (Throwable e) {
            String msg = Utils.stackTrace(e);
            Log.logTrace(msg);
            shutdown(e);
        }
    }

    Throwable getRecordedCause() {
        return cause;
    }

    void shutdown(Throwable t) {
        debug.log(Level.DEBUG, () -> "Shutting down h2c (closed="+closed+"): " + t);
        if (closed == true) return;
        synchronized (this) {
            if (closed == true) return;
            closed = true;
        }
        Log.logError(t);
        Throwable initialCause = this.cause;
        if (initialCause == null) this.cause = t;
        client2.deleteConnection(this);
        List<Stream<?>> c = new LinkedList<>(streams.values());
        for (Stream<?> s : c) {
            s.cancelImpl(t);
        }
        connection.close();
    }

    /**
     * Streams initiated by a client MUST use odd-numbered stream
     * identifiers; those initiated by the server MUST use even-numbered
     * stream identifiers.
     */
    private static final boolean isSeverInitiatedStream(int streamid) {
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
                debug.log(Level.DEBUG, () -> "Reset stream: "
                          + ((MalformedFrame) frame).getMessage());
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

            Stream<?> stream = getStream(streamid);
            if (stream == null) {
                // Should never receive a frame with unknown stream id

                if (frame instanceof HeaderFrame) {
                    // always decode the headers as they may affect
                    // connection-level HPACK decoding state
                    HeaderDecoder decoder = new LoggingHeaderDecoder(new HeaderDecoder());
                    decodeHeaders((HeaderFrame) frame, decoder);
                }

                if (!(frame instanceof ResetFrame)) {
                    if (isSeverInitiatedStream(streamid)) {
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
            if (frame instanceof PushPromiseFrame) {
                PushPromiseFrame pp = (PushPromiseFrame)frame;
                handlePushPromise(stream, pp);
            } else if (frame instanceof HeaderFrame) {
                // decode headers (or continuation)
                decodeHeaders((HeaderFrame) frame, stream.rspHeadersConsumer());
                stream.incoming(frame);
            } else {
                stream.incoming(frame);
            }
        }
    }

    private <T> void handlePushPromise(Stream<T> parent, PushPromiseFrame pp)
        throws IOException
    {
        // always decode the headers as they may affect connection-level HPACK
        // decoding state
        HeaderDecoder decoder = new LoggingHeaderDecoder(new HeaderDecoder());
        decodeHeaders(pp, decoder);

        HttpRequestImpl parentReq = parent.request;
        int promisedStreamid = pp.getPromisedStream();
        if (promisedStreamid != nextPushStream) {
            resetStream(promisedStreamid, ResetFrame.PROTOCOL_ERROR);
            return;
        } else {
            nextPushStream += 2;
        }

        HttpHeadersImpl headers = decoder.headers();
        HttpRequestImpl pushReq = HttpRequestImpl.createPushRequest(parentReq, headers);
        Exchange<T> pushExch = new Exchange<>(pushReq, parent.exchange.multi);
        Stream.PushedStream<?,T> pushStream = createPushStream(parent, pushExch);
        pushExch.exchImpl = pushStream;
        pushStream.registerStream(promisedStreamid);
        parent.incoming_pushPromise(pushReq, pushStream);
    }

    private void handleConnectionFrame(Http2Frame frame)
        throws IOException
    {
        switch (frame.type()) {
          case SettingsFrame.TYPE:
              handleSettings((SettingsFrame)frame);
              break;
          case PingFrame.TYPE:
              handlePing((PingFrame)frame);
              break;
          case GoAwayFrame.TYPE:
              handleGoAway((GoAwayFrame)frame);
              break;
          case WindowUpdateFrame.TYPE:
              handleWindowUpdate((WindowUpdateFrame)frame);
              break;
          default:
            protocolError(ErrorFrame.PROTOCOL_ERROR);
        }
    }

    void resetStream(int streamid, int code) throws IOException {
        Log.logError(
            "Resetting stream {0,number,integer} with error code {1,number,integer}",
            streamid, code);
        ResetFrame frame = new ResetFrame(streamid, code);
        sendFrame(frame);
        closeStream(streamid);
    }

    void closeStream(int streamid) {
        debug.log(Level.DEBUG, "Closed stream %d", streamid);
        Stream<?> s = streams.remove(streamid);
        if (s != null) {
            // decrement the reference count on the HttpClientImpl
            // to allow the SelectorManager thread to exit if no
            // other operation is pending and the facade is no
            // longer referenced.
            client().unreference();
        }
        // ## Remove s != null. It is a hack for delayed cancellation,reset
        if (s != null && !(s instanceof Stream.PushedStream)) {
            // Since PushStreams have no request body, then they have no
            // corresponding entry in the window controller.
            windowController.removeStream(streamid);
        }
        if (singleStream() && streams.isEmpty()) {
            // should be only 1 stream, but there might be more if server push
            close();
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
        GoAwayFrame frame = new GoAwayFrame(0, errorCode);
        sendFrame(frame);
        shutdown(new IOException("protocol error" + (msg == null?"":(": " + msg))));
    }

    private void handleSettings(SettingsFrame frame)
        throws IOException
    {
        assert frame.streamid() == 0;
        if (!frame.getFlag(SettingsFrame.ACK)) {
            int oldWindowSize = serverSettings.getParameter(INITIAL_WINDOW_SIZE);
            int newWindowSize = frame.getParameter(INITIAL_WINDOW_SIZE);
            int diff = newWindowSize - oldWindowSize;
            if (diff != 0) {
                windowController.adjustActiveStreams(diff);
            }
            serverSettings = frame;
            sendFrame(new SettingsFrame(SettingsFrame.ACK));
        }
    }

    private void handlePing(PingFrame frame)
        throws IOException
    {
        frame.setFlag(PingFrame.ACK);
        sendUnorderedFrame(frame);
    }

    private void handleGoAway(GoAwayFrame frame)
        throws IOException
    {
        shutdown(new IOException(
                        String.valueOf(connection.channel().getLocalAddress())
                        +": GOAWAY received"));
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
        int initialWindowSize = sf.getParameter(INITIAL_WINDOW_SIZE);
        ByteBuffer buf = framesEncoder.encodeConnectionPreface(PREFACE_BYTES, sf);
        Log.logFrames(sf, "OUT");
        // send preface bytes and SettingsFrame together
        HttpPublisher publisher = publisher();
        publisher.enqueue(List.of(buf));
        publisher.signalEnqueued();
        // mark preface sent.
        framesController.markPrefaceSent();
        Log.logTrace("PREFACE_BYTES sent");
        Log.logTrace("Settings Frame sent");

        // send a Window update for the receive buffer we are using
        // minus the initial 64 K specified in protocol
        final int len = windowUpdater.initialWindowSize - initialWindowSize;
        if (len > 0) {
            windowUpdater.sendWindowUpdate(len);
        }
        // there will be an ACK to the windows update - which should
        // cause any pending data stored before the preface was sent to be
        // flushed (see PrefaceController).
        Log.logTrace("finished sending connection preface");
        debug.log(Level.DEBUG, "Triggering processing of buffered data"
                  + " after sending connection preface");
        subscriber.onNext(List.of(EMPTY_TRIGGER));
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

    <T> Stream.PushedStream<?,T> createPushStream(Stream<T> parent, Exchange<T> pushEx) {
        PushGroup<?,T> pg = parent.exchange.getPushGroup();
        return new Stream.PushedStream<>(pg, this, pushEx);
    }

    <T> void putStream(Stream<T> stream, int streamid) {
        // increment the reference count on the HttpClientImpl
        // to prevent the SelectorManager thread from exiting until
        // the stream is closed.
        client().reference();
        streams.put(streamid, stream);
    }

    /**
     * Encode the headers into a List<ByteBuffer> and then create HEADERS
     * and CONTINUATION frames from the list and return the List<Http2Frame>.
     */
    private List<HeaderFrame> encodeHeaders(OutgoingHeaders<Stream<?>> frame) {
        List<ByteBuffer> buffers = encodeHeadersImpl(
                getMaxSendFrameSize(),
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

    private ByteBuffer getHeaderBuffer(int maxFrameSize) {
        ByteBuffer buf = ByteBuffer.allocate(maxFrameSize);
        buf.limit(maxFrameSize);
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
    private List<ByteBuffer> encodeHeadersImpl(int maxFrameSize, HttpHeaders... headers) {
        ByteBuffer buffer = getHeaderBuffer(maxFrameSize);
        List<ByteBuffer> buffers = new ArrayList<>();
        for(HttpHeaders header : headers) {
            for (Map.Entry<String, List<String>> e : header.map().entrySet()) {
                String lKey = e.getKey().toLowerCase();
                List<String> values = e.getValue();
                for (String value : values) {
                    hpackOut.header(lKey, value);
                    while (!hpackOut.encode(buffer)) {
                        buffer.flip();
                        buffers.add(buffer);
                        buffer =  getHeaderBuffer(maxFrameSize);
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
            StringBuilder sb = new StringBuilder("HEADERS FRAME (stream=");
            sb.append(stream.streamid).append(")\n");
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
        int streamid = nextstreamid;
        nextstreamid += 2;
        stream.registerStream(streamid);
        // set outgoing window here. This allows thread sending
        // body to proceed.
        windowController.registerStream(streamid, getInitialSendWindowSize());
        return stream;
    }

    private final Object sendlock = new Object();

    void sendFrame(Http2Frame frame) {
        try {
            HttpPublisher publisher = publisher();
            synchronized (sendlock) {
                if (frame instanceof OutgoingHeaders) {
                    @SuppressWarnings("unchecked")
                    OutgoingHeaders<Stream<?>> oh = (OutgoingHeaders<Stream<?>>) frame;
                    Stream<?> stream = registerNewStream(oh);
                    // provide protection from inserting unordered frames between Headers and Continuation
                    publisher.enqueue(encodeHeaders(oh, stream));
                } else {
                    publisher.enqueue(encodeFrame(frame));
                }
            }
            publisher.signalEnqueued();
        } catch (IOException e) {
            if (!closed) {
                Log.logError(e);
                shutdown(e);
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
            if (!closed) {
                Log.logError(e);
                shutdown(e);
            }
        }
    }

    /*
     * Direct call of the method bypasses synchronization on "sendlock" and
     * allowed only of control frames: WindowUpdateFrame, PingFrame and etc.
     * prohibited for such frames as DataFrame, HeadersFrame, ContinuationFrame.
     */
    void sendUnorderedFrame(Http2Frame frame) {
        try {
            HttpPublisher publisher = publisher();
            publisher.enqueueUnordered(encodeFrame(frame));
            publisher.signalEnqueued();
        } catch (IOException e) {
            if (!closed) {
                Log.logError(e);
                shutdown(e);
            }
        }
    }

    /**
     * A simple tube subscriber for reading from the connection flow.
     */
    final class Http2TubeSubscriber implements TubeSubscriber {
        volatile Flow.Subscription subscription;
        volatile boolean completed;
        volatile boolean dropped;
        volatile Throwable error;
        final ConcurrentLinkedQueue<ByteBuffer> queue
                = new ConcurrentLinkedQueue<>();
        final SequentialScheduler scheduler =
                SequentialScheduler.synchronizedScheduler(this::processQueue);

        final void processQueue() {
            try {
                while (!queue.isEmpty() && !scheduler.isStopped()) {
                    ByteBuffer buffer = queue.poll();
                    debug.log(Level.DEBUG,
                              "sending %d to Http2Connection.asyncReceive",
                              buffer.remaining());
                    asyncReceive(buffer);
                }
            } catch (Throwable t) {
                Throwable x = error;
                if (x == null) error = t;
            } finally {
                Throwable x = error;
                if (x != null) {
                    debug.log(Level.DEBUG, "Stopping scheduler", x);
                    scheduler.stop();
                    Http2Connection.this.shutdown(x);
                }
            }
        }


        public void onSubscribe(Flow.Subscription subscription) {
            // supports being called multiple time.
            // doesn't cancel the previous subscription, since that is
            // most probably the same as the new subscription.
            assert this.subscription == null || dropped == false;
            this.subscription = subscription;
            dropped = false;
            // TODO FIXME: request(1) should be done by the delegate.
            if (!completed) {
                debug.log(Level.DEBUG, "onSubscribe: requesting Long.MAX_VALUE for reading");
                subscription.request(Long.MAX_VALUE);
            } else {
                debug.log(Level.DEBUG, "onSubscribe: already completed");
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            debug.log(Level.DEBUG, () -> "onNext: got " + Utils.remaining(item)
                    + " bytes in " + item.size() + " buffers");
            queue.addAll(item);
            scheduler.deferOrSchedule(client().theExecutor());
        }

        @Override
        public void onError(Throwable throwable) {
            debug.log(Level.DEBUG, () -> "onError: " + throwable);
            error = throwable;
            completed = true;
            scheduler.deferOrSchedule(client().theExecutor());
        }

        @Override
        public void onComplete() {
            debug.log(Level.DEBUG, "EOF");
            error = new EOFException("EOF reached while reading");
            completed = true;
            scheduler.deferOrSchedule(client().theExecutor());
        }

        public void dropSubscription() {
            debug.log(Level.DEBUG, "dropSubscription");
            // we could probably set subscription to null here...
            // then we might not need the 'dropped' boolean?
            dropped = true;
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

    final class LoggingHeaderDecoder extends HeaderDecoder {

        private final HeaderDecoder delegate;
        private final System.Logger debugHpack =
                Utils.getHpackLogger(this::dbgString, DEBUG_HPACK);

        LoggingHeaderDecoder(HeaderDecoder delegate) {
            this.delegate = delegate;
        }

        String dbgString() {
            return Http2Connection.this.dbgString() + "/LoggingHeaderDecoder";
        }

        @Override
        public void onDecoded(CharSequence name, CharSequence value) {
            delegate.onDecoded(name, value);
        }

        @Override
        public void onIndexed(int index,
                              CharSequence name,
                              CharSequence value) {
            debugHpack.log(Level.DEBUG, "onIndexed(%s, %s, %s)%n",
                           index, name, value);
            delegate.onIndexed(index, name, value);
        }

        @Override
        public void onLiteral(int index,
                              CharSequence name,
                              CharSequence value,
                              boolean valueHuffman) {
            debugHpack.log(Level.DEBUG, "onLiteral(%s, %s, %s, %s)%n",
                              index, name, value, valueHuffman);
            delegate.onLiteral(index, name, value, valueHuffman);
        }

        @Override
        public void onLiteral(CharSequence name,
                              boolean nameHuffman,
                              CharSequence value,
                              boolean valueHuffman) {
            debugHpack.log(Level.DEBUG, "onLiteral(%s, %s, %s, %s)%n",
                           name, nameHuffman, value, valueHuffman);
            delegate.onLiteral(name, nameHuffman, value, valueHuffman);
        }

        @Override
        public void onLiteralNeverIndexed(int index,
                                          CharSequence name,
                                          CharSequence value,
                                          boolean valueHuffman) {
            debugHpack.log(Level.DEBUG, "onLiteralNeverIndexed(%s, %s, %s, %s)%n",
                           index, name, value, valueHuffman);
            delegate.onLiteralNeverIndexed(index, name, value, valueHuffman);
        }

        @Override
        public void onLiteralNeverIndexed(CharSequence name,
                                          boolean nameHuffman,
                                          CharSequence value,
                                          boolean valueHuffman) {
            debugHpack.log(Level.DEBUG, "onLiteralNeverIndexed(%s, %s, %s, %s)%n",
                           name, nameHuffman, value, valueHuffman);
            delegate.onLiteralNeverIndexed(name, nameHuffman, value, valueHuffman);
        }

        @Override
        public void onLiteralWithIndexing(int index,
                                          CharSequence name,
                                          CharSequence value,
                                          boolean valueHuffman) {
            debugHpack.log(Level.DEBUG, "onLiteralWithIndexing(%s, %s, %s, %s)%n",
                           index, name, value, valueHuffman);
            delegate.onLiteralWithIndexing(index, name, value, valueHuffman);
        }

        @Override
        public void onLiteralWithIndexing(CharSequence name,
                                          boolean nameHuffman,
                                          CharSequence value,
                                          boolean valueHuffman) {
            debugHpack.log(Level.DEBUG, "onLiteralWithIndexing(%s, %s, %s, %s)%n",
                              name, nameHuffman, value, valueHuffman);
            delegate.onLiteralWithIndexing(name, nameHuffman, value, valueHuffman);
        }

        @Override
        public void onSizeUpdate(int capacity) {
            debugHpack.log(Level.DEBUG, "onSizeUpdate(%s)%n", capacity);
            delegate.onSizeUpdate(capacity);
        }

        @Override
        HttpHeadersImpl headers() {
            return delegate.headers();
        }
    }

    static class HeaderDecoder implements DecodingCallback {
        HttpHeadersImpl headers;

        HeaderDecoder() {
            this.headers = new HttpHeadersImpl();
        }

        @Override
        public void onDecoded(CharSequence name, CharSequence value) {
            headers.addHeader(name.toString(), value.toString());
        }

        HttpHeadersImpl headers() {
            return headers;
        }
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
    }

    /**
     * Thrown when https handshake negotiates http/1.1 alpn instead of h2
     */
    static final class ALPNException extends IOException {
        private static final long serialVersionUID = 23138275393635783L;
        final AbstractAsyncSSLConnection connection;

        ALPNException(String msg, AbstractAsyncSSLConnection connection) {
            super(msg);
            this.connection = connection;
        }

        AbstractAsyncSSLConnection getConnection() {
            return connection;
        }
    }
}
