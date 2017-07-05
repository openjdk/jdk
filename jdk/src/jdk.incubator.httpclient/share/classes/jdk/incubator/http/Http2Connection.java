/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import jdk.incubator.http.HttpConnection.Mode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import javax.net.ssl.SSLEngine;
import jdk.incubator.http.internal.common.*;
import jdk.incubator.http.internal.frame.*;
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
    /*
     *  ByteBuffer pooling strategy for HTTP/2 protocol:
     *
     * In general there are 4 points where ByteBuffers are used:
     *  - incoming/outgoing frames from/to ByteBufers plus incoming/outgoing encrypted data
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


    // A small class that allows to control the state of
    // the connection preface. This is just a thin wrapper
    // over a CountDownLatch.
    private final class PrefaceController {
        volatile boolean prefaceSent;
        private final CountDownLatch latch = new CountDownLatch(1);

        // This method returns immediately if the preface is sent,
        // and blocks until the preface is sent if not.
        // In the common case this where the preface is already sent
        // this will cost not more than a volatile read.
        void waitUntilPrefaceSent() {
            if (!prefaceSent) {
                try {
                    // If the preface is not sent then await on the latch
                    Log.logTrace("Waiting until connection preface is sent");
                    latch.await();
                    Log.logTrace("Preface sent: resuming reading");
                    assert prefaceSent;
                 } catch (InterruptedException e) {
                    String msg = Utils.stackTrace(e);
                    Log.logTrace(msg);
                    shutdown(e);
                }
            }
        }

        // Mark that the connection preface is sent
        void markPrefaceSent() {
            assert !prefaceSent;
            prefaceSent = true;
            // Release the latch. If asyncReceive was scheduled it will
            // be waiting for the release and will be woken up by this
            // call. If not, then the semaphore will no longer be used after
            // this.
            latch.countDown();
        }

        boolean isPrefaceSent() {
            return prefaceSent;
        }
    }

    volatile boolean closed;

    //-------------------------------------
    final HttpConnection connection;
    private final HttpClientImpl client;
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
    private final PrefaceController prefaceController = new PrefaceController();
    final WindowUpdateSender windowUpdater;

    static final int DEFAULT_FRAME_SIZE = 16 * 1024;


    // TODO: need list of control frames from other threads
    // that need to be sent

    private Http2Connection(HttpConnection connection,
                            Http2ClientImpl client2,
                            int nextstreamid,
                            String key) {
        this.connection = connection;
        this.client = client2.client();
        this.client2 = client2;
        this.nextstreamid = nextstreamid;
        this.key = key;
        this.clientSettings = this.client2.getClientSettings();
        this.framesDecoder = new FramesDecoder(this::processFrame, clientSettings.getParameter(SettingsFrame.MAX_FRAME_SIZE));
        // serverSettings will be updated by server
        this.serverSettings = SettingsFrame.getDefaultSettings();
        this.hpackOut = new Encoder(serverSettings.getParameter(HEADER_TABLE_SIZE));
        this.hpackIn = new Decoder(clientSettings.getParameter(HEADER_TABLE_SIZE));
        this.windowUpdater = new ConnectionWindowUpdateSender(this, client.getReceiveBufferSize());
    }
        /**
         * Case 1) Create from upgraded HTTP/1.1 connection.
         * Is ready to use. Will not be SSL. exchange is the Exchange
         * that initiated the connection, whose response will be delivered
         * on a Stream.
         */
    Http2Connection(HttpConnection connection,
                    Http2ClientImpl client2,
                    Exchange<?> exchange,
                    ByteBuffer initial)
        throws IOException, InterruptedException
    {
        this(connection,
                client2,
                3, // stream 1 is registered during the upgrade
                keyFor(connection));
        assert !(connection instanceof SSLConnection);
        Log.logTrace("Connection send window size {0} ", windowController.connectionWindowSize());

        Stream<?> initialStream = createStream(exchange);
        initialStream.registerStream(1);
        windowController.registerStream(1, getInitialSendWindowSize());
        initialStream.requestSent();
        sendConnectionPreface();
        // start reading and writing
        // start reading
        AsyncConnection asyncConn = (AsyncConnection)connection;
        asyncConn.setAsyncCallbacks(this::asyncReceive, this::shutdown, this::getReadBuffer);
        connection.configureMode(Mode.ASYNC); // set mode only AFTER setAsyncCallbacks to provide visibility.
        asyncReceive(ByteBufferReference.of(initial));
        asyncConn.startReading();
    }

    // async style but completes immediately
    static CompletableFuture<Http2Connection> createAsync(HttpConnection connection,
                                                          Http2ClientImpl client2,
                                                          Exchange<?> exchange,
                                                          ByteBuffer initial) {
        return MinimalFuture.supply(() -> new Http2Connection(connection, client2, exchange, initial));
    }

    /**
     * Cases 2) 3)
     *
     * request is request to be sent.
     */
    Http2Connection(HttpRequestImpl request, Http2ClientImpl h2client)
        throws IOException, InterruptedException
    {
        this(HttpConnection.getConnection(request.getAddress(h2client.client()), h2client.client(), request, true),
                h2client,
                1,
                keyFor(request.uri(), request.proxy(h2client.client())));
        Log.logTrace("Connection send window size {0} ", windowController.connectionWindowSize());

        // start reading
        AsyncConnection asyncConn = (AsyncConnection)connection;
        asyncConn.setAsyncCallbacks(this::asyncReceive, this::shutdown, this::getReadBuffer);
        connection.connect();
        checkSSLConfig();
        // safe to resume async reading now.
        asyncConn.enableCallback();
        sendConnectionPreface();
    }

    /**
     * Throws an IOException if h2 was not negotiated
     */
    private void checkSSLConfig() throws IOException {
        AsyncSSLConnection aconn = (AsyncSSLConnection)connection;
        SSLEngine engine = aconn.getEngine();
        String alpn = engine.getApplicationProtocol();
        if (alpn == null || !alpn.equals("h2")) {
            String msg;
            if (alpn == null) {
                Log.logSSL("ALPN not supported");
                msg = "ALPN not supported";
            } else switch (alpn) {
              case "":
                Log.logSSL("No ALPN returned");
                msg = "No ALPN negotiated";
                break;
              case "http/1.1":
                Log.logSSL("HTTP/1.1 ALPN returned");
                msg = "HTTP/1.1 ALPN returned";
                break;
              default:
                Log.logSSL("unknown ALPN returned");
                msg = "Unexpected ALPN: " + alpn;
                throw new IOException(msg);
            }
            throw new ALPNException(msg, aconn);
        }
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

        if (isProxy) {
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
        return (secure ? "S:" : "C:") + (proxy ? "P:" : "H:") + host + ":" + port;
    }

    String key() {
        return this.key;
    }

    void putConnection() {
        client2.putConnection(this);
    }

    private static String toHexdump1(ByteBuffer bb) {
        bb.mark();
        StringBuilder sb = new StringBuilder(512);
        Formatter f = new Formatter(sb);

        while (bb.hasRemaining()) {
            int i =  Byte.toUnsignedInt(bb.get());
            f.format("%02x:", i);
        }
        sb.deleteCharAt(sb.length()-1);
        bb.reset();
        return sb.toString();
    }

    private static String toHexdump(ByteBuffer bb) {
        List<String> words = new ArrayList<>();
        int i = 0;
        bb.mark();
        while (bb.hasRemaining()) {
            if (i % 2 == 0) {
                words.add("");
            }
            byte b = bb.get();
            String hex = Integer.toHexString(256 + Byte.toUnsignedInt(b)).substring(1);
            words.set(i / 2, words.get(i / 2) + hex);
            i++;
        }
        bb.reset();
        return words.stream().collect(Collectors.joining(" "));
    }

    private void decodeHeaders(HeaderFrame frame, DecodingCallback decoder) {
        boolean endOfHeaders = frame.getFlag(HeaderFrame.END_HEADERS);

        ByteBufferReference[] buffers = frame.getHeaderBlock();
        for (int i = 0; i < buffers.length; i++) {
            hpackIn.decode(buffers[i].get(), endOfHeaders && (i == buffers.length - 1), decoder);
        }
    }

    int getInitialSendWindowSize() {
        return serverSettings.getParameter(INITIAL_WINDOW_SIZE);
    }

    void close() {
        GoAwayFrame f = new GoAwayFrame(0, ErrorFrame.NO_ERROR, "Requested by user".getBytes());
        // TODO: set last stream. For now zero ok.
        sendFrame(f);
    }

    private ByteBufferPool readBufferPool = new ByteBufferPool();

    // provides buffer to read data (default size)
    public ByteBufferReference getReadBuffer() {
        return readBufferPool.get(getMaxReceiveFrameSize() + Http2Frame.FRAME_HEADER_SIZE);
    }

    private final Object readlock = new Object();

    public void asyncReceive(ByteBufferReference buffer) {
        // We don't need to read anything and
        // we don't want to send anything back to the server
        // until the connection preface has been sent.
        // Therefore we're going to wait if needed before reading
        // (and thus replying) to anything.
        // Starting to reply to something (e.g send an ACK to a
        // SettingsFrame sent by the server) before the connection
        // preface is fully sent might result in the server
        // sending a GOAWAY frame with 'invalid_preface'.
        prefaceController.waitUntilPrefaceSent();
        synchronized (readlock) {
            assert prefaceController.isPrefaceSent();
            try {
                framesDecoder.decode(buffer);
            } catch (Throwable e) {
                String msg = Utils.stackTrace(e);
                Log.logTrace(msg);
                shutdown(e);
            }
        }
    }


    void shutdown(Throwable t) {
        Log.logError(t);
        closed = true;
        client2.deleteConnection(this);
        List<Stream<?>> c = new LinkedList<>(streams.values());
        for (Stream<?> s : c) {
            s.cancelImpl(t);
        }
        connection.close();
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
                protocolError(((MalformedFrame) frame).getErrorCode());
            } else {
                resetStream(streamid, ((MalformedFrame) frame).getErrorCode());
            }
            return;
        }
        if (streamid == 0) {
            handleConnectionFrame(frame);
        } else {
            if (frame instanceof SettingsFrame) {
                // The stream identifier for a SETTINGS frame MUST be zero
                protocolError(GoAwayFrame.PROTOCOL_ERROR);
                return;
            }

            Stream<?> stream = getStream(streamid);
            if (stream == null) {
                // Should never receive a frame with unknown stream id

                // To avoid looping, an endpoint MUST NOT send a RST_STREAM in
                // response to a RST_STREAM frame.
                if (!(frame instanceof ResetFrame)) {
                    resetStream(streamid, ResetFrame.PROTOCOL_ERROR);
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
        HttpRequestImpl parentReq = parent.request;
        int promisedStreamid = pp.getPromisedStream();
        if (promisedStreamid != nextPushStream) {
            resetStream(promisedStreamid, ResetFrame.PROTOCOL_ERROR);
            return;
        } else {
            nextPushStream += 2;
        }
        HeaderDecoder decoder = new HeaderDecoder();
        decodeHeaders(pp, decoder);
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
        Stream<?> s = streams.remove(streamid);
        // ## Remove s != null. It is a hack for delayed cancellation,reset
        if (s != null && !(s instanceof Stream.PushedStream)) {
            // Since PushStreams have no request body, then they have no
            // corresponding entry in the window controller.
            windowController.removeStream(streamid);
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
        GoAwayFrame frame = new GoAwayFrame(0, errorCode);
        sendFrame(frame);
        shutdown(new IOException("protocol error"));
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

    // Not sure how useful this is.
    public int getMaxHeadersSize() {
        return serverSettings.getParameter(MAX_HEADER_LIST_SIZE);
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
        SettingsFrame sf = client2.getClientSettings();
        ByteBufferReference ref = framesEncoder.encodeConnectionPreface(PREFACE_BYTES, sf);
        Log.logFrames(sf, "OUT");
        // send preface bytes and SettingsFrame together
        connection.write(ref.get());

        Log.logTrace("PREFACE_BYTES sent");
        Log.logTrace("Settings Frame sent");

        // send a Window update for the receive buffer we are using
        // minus the initial 64 K specified in protocol
        final int len = client2.client().getReceiveBufferSize() - (64 * 1024 - 1);
        windowUpdater.sendWindowUpdate(len);
        Log.logTrace("finished sending connection preface");
        prefaceController.markPrefaceSent();
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
    <T> Stream<T> createStream(Exchange<T> exchange) {
        Stream<T> stream = new Stream<>(client, this, exchange, windowController);
        return stream;
    }

    <T> Stream.PushedStream<?,T> createPushStream(Stream<T> parent, Exchange<T> pushEx) {
        PushGroup<?,T> pg = parent.exchange.getPushGroup();
        return new Stream.PushedStream<>(pg, client, this, parent, pushEx);
    }

    <T> void putStream(Stream<T> stream, int streamid) {
        streams.put(streamid, stream);
    }

    void deleteStream(int streamid) {
        streams.remove(streamid);
        windowController.removeStream(streamid);
    }

    /**
     * Encode the headers into a List<ByteBuffer> and then create HEADERS
     * and CONTINUATION frames from the list and return the List<Http2Frame>.
     */
    private List<HeaderFrame> encodeHeaders(OutgoingHeaders<Stream<?>> frame) {
        List<ByteBufferReference> buffers = encodeHeadersImpl(
                getMaxSendFrameSize(),
                frame.getAttachment().getRequestPseudoHeaders(),
                frame.getUserHeaders(),
                frame.getSystemHeaders());

        List<HeaderFrame> frames = new ArrayList<>(buffers.size());
        Iterator<ByteBufferReference> bufIterator = buffers.iterator();
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
    private ByteBufferPool headerEncodingPool = new ByteBufferPool();

    private ByteBufferReference getHeaderBuffer(int maxFrameSize) {
        ByteBufferReference ref = headerEncodingPool.get(maxFrameSize);
        ref.get().limit(maxFrameSize);
        return ref;
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
    private List<ByteBufferReference> encodeHeadersImpl(int maxFrameSize, HttpHeaders... headers) {
        ByteBufferReference buffer = getHeaderBuffer(maxFrameSize);
        List<ByteBufferReference> buffers = new ArrayList<>();
        for(HttpHeaders header : headers) {
            for (Map.Entry<String, List<String>> e : header.map().entrySet()) {
                String lKey = e.getKey().toLowerCase();
                List<String> values = e.getValue();
                for (String value : values) {
                    hpackOut.header(lKey, value);
                    while (!hpackOut.encode(buffer.get())) {
                        buffer.get().flip();
                        buffers.add(buffer);
                        buffer =  getHeaderBuffer(maxFrameSize);
                    }
                }
            }
        }
        buffer.get().flip();
        buffers.add(buffer);
        return buffers;
    }

    private ByteBufferReference[] encodeHeaders(OutgoingHeaders<Stream<?>> oh, Stream<?> stream) {
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

    private ByteBufferReference[] encodeFrames(List<HeaderFrame> frames) {
        if (Log.frames()) {
            frames.forEach(f -> Log.logFrames(f, "OUT"));
        }
        return framesEncoder.encodeFrames(frames);
    }

    static Throwable getExceptionFrom(CompletableFuture<?> cf) {
        try {
            cf.get();
            return null;
        } catch (Throwable e) {
            if (e.getCause() != null) {
                return e.getCause();
            } else {
                return e;
            }
        }
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
            synchronized (sendlock) {
                if (frame instanceof OutgoingHeaders) {
                    @SuppressWarnings("unchecked")
                    OutgoingHeaders<Stream<?>> oh = (OutgoingHeaders<Stream<?>>) frame;
                    Stream<?> stream = registerNewStream(oh);
                    // provide protection from inserting unordered frames between Headers and Continuation
                    connection.writeAsync(encodeHeaders(oh, stream));
                } else {
                    connection.writeAsync(encodeFrame(frame));
                }
            }
            connection.flushAsync();
        } catch (IOException e) {
            if (!closed) {
                Log.logError(e);
                shutdown(e);
            }
        }
    }

    private ByteBufferReference[] encodeFrame(Http2Frame frame) {
        Log.logFrames(frame, "OUT");
        return framesEncoder.encodeFrame(frame);
    }

    void sendDataFrame(DataFrame frame) {
        try {
            connection.writeAsync(encodeFrame(frame));
            connection.flushAsync();
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
            connection.writeAsyncUnordered(encodeFrame(frame));
            connection.flushAsync();
        } catch (IOException e) {
            if (!closed) {
                Log.logError(e);
                shutdown(e);
            }
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

        public ConnectionWindowUpdateSender(Http2Connection connection,
                                            int initialWindowSize) {
            super(connection, initialWindowSize);
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
        final AsyncSSLConnection connection;

        ALPNException(String msg, AsyncSSLConnection connection) {
            super(msg);
            this.connection = connection;
        }

        AsyncSSLConnection getConnection() {
            return connection;
        }
    }
}
