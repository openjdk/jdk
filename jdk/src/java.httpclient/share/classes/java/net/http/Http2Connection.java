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
 */
package java.net.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpConnection.Mode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import sun.net.httpclient.hpack.Encoder;
import sun.net.httpclient.hpack.Decoder;
import static java.net.http.SettingsFrame.*;
import static java.net.http.Utils.BUFSIZE;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.stream.Collectors;
import sun.net.httpclient.hpack.DecodingCallback;

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
class Http2Connection implements BufferHandler {

    final Queue<Http2Frame> outputQ;
    volatile boolean closed;

    //-------------------------------------
    final HttpConnection connection;
    HttpClientImpl client;
    final Http2ClientImpl client2;
    Map<Integer,Stream> streams;
    int nextstreamid = 3; // stream 1 is registered separately
    int nextPushStream = 2;
    Encoder hpackOut;
    Decoder hpackIn;
    SettingsFrame clientSettings, serverSettings;
    ByteBufferConsumer bbc;
    final LinkedList<ByteBuffer> freeList;
    final String key; // for HttpClientImpl.connections map
    FrameReader reader;

    // Connection level flow control windows
    int sendWindow = INITIAL_WINDOW_SIZE;

    final static int DEFAULT_FRAME_SIZE = 16 * 1024;
    private static ByteBuffer[] empty = Utils.EMPTY_BB_ARRAY;

    final ExecutorWrapper executor;

    /**
     * This is established by the protocol spec and the peer will update it with
     * WINDOW_UPDATEs, which affects the sendWindow.
     */
    final static int INITIAL_WINDOW_SIZE = 64 * 1024 - 1;

    // TODO: need list of control frames from other threads
    // that need to be sent

    /**
     * Case 1) Create from upgraded HTTP/1.1 connection.
     * Is ready to use. Will not be SSL. exchange is the Exchange
     * that initiated the connection, whose response will be delivered
     * on a Stream.
     */
    Http2Connection(HttpConnection connection, Http2ClientImpl client2,
            Exchange exchange) throws IOException, InterruptedException {
        this.outputQ = new Queue<>();
        String msg = "Connection send window size " + Integer.toString(sendWindow);
        Log.logTrace(msg);

        //this.initialExchange = exchange;
        assert !(connection instanceof SSLConnection);
        this.connection = connection;
        this.client = client2.client();
        this.client2 = client2;
        this.executor = client.executorWrapper();
        this.freeList = new LinkedList<>();
        this.key = keyFor(connection);
        streams = Collections.synchronizedMap(new HashMap<>());
        initCommon();
        //sendConnectionPreface();
        Stream initialStream = createStream(exchange);
        initialStream.registerStream(1);
        initialStream.requestSent();
        sendConnectionPreface();
        connection.configureMode(Mode.ASYNC);
        // start reading and writing
        // start reading
        AsyncConnection asyncConn = (AsyncConnection)connection;
        asyncConn.setAsyncCallbacks(this::asyncReceive, this::shutdown);
        asyncReceive(connection.getRemaining());
        asyncConn.startReading();
    }

    // async style but completes immediately
    static CompletableFuture<Http2Connection> createAsync(HttpConnection connection,
            Http2ClientImpl client2, Exchange exchange) {
        CompletableFuture<Http2Connection> cf = new CompletableFuture<>();
        try {
            Http2Connection c = new Http2Connection(connection, client2, exchange);
            cf.complete(c);
        } catch (IOException | InterruptedException e) {
            cf.completeExceptionally(e);
        }
        return cf;
    }

    /**
     * Cases 2) 3)
     *
     * request is request to be sent.
     */
    Http2Connection(HttpRequestImpl request) throws IOException, InterruptedException {
        InetSocketAddress proxy = request.proxy();
        URI uri = request.uri();
        InetSocketAddress addr = Utils.getAddress(request);
        String msg = "Connection send window size " + Integer.toString(sendWindow);
        Log.logTrace(msg);
        this.key = keyFor(uri, proxy);
        this.connection = HttpConnection.getConnection(addr, request, this);
        streams = Collections.synchronizedMap(new HashMap<>());
        this.client = request.client();
        this.client2 = client.client2();
        this.executor = client.executorWrapper();
        this.freeList = new LinkedList<>();
        this.outputQ = new Queue<>();
        nextstreamid = 1;
        initCommon();
        connection.connect();
        connection.configureMode(Mode.ASYNC);
        // start reading
        AsyncConnection asyncConn = (AsyncConnection)connection;
        asyncConn.setAsyncCallbacks(this::asyncReceive, this::shutdown);
        sendConnectionPreface();
        asyncConn.startReading();
    }

    // NEW
    synchronized void obtainSendWindow(int amount) throws InterruptedException {
        while (amount > 0) {
            int n = Math.min(amount, sendWindow);
            sendWindow -= n;
            amount -= n;
            if (amount > 0)
                wait();
        }
    }

    synchronized void updateSendWindow(int amount) {
        if (sendWindow == 0) {
            sendWindow += amount;
            notifyAll();
        } else
            sendWindow += amount;
    }

    synchronized int sendWindow() {
        return sendWindow;
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
        char c1 = secure ? 'S' : 'C';
        char c2 = proxy ? 'P' : 'H';

        StringBuilder sb = new StringBuilder(128);
        sb.append(c1).append(':').append(c2).append(':')
                .append(host).append(':').append(port);
        return sb.toString();
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

        ByteBuffer[] buffers = frame.getHeaderBlock();
        for (int i = 0; i < buffers.length; i++) {
            hpackIn.decode(buffers[i], endOfHeaders, decoder);
        }
    }

    int getInitialSendWindowSize() {
        return serverSettings.getParameter(SettingsFrame.INITIAL_WINDOW_SIZE);
    }

    void close() {
        GoAwayFrame f = new GoAwayFrame();
        f.setDebugData("Requested by user".getBytes());
        // TODO: set last stream. For now zero ok.
        sendFrame(f);
    }

    // BufferHandler methods

    @Override
    public ByteBuffer getBuffer(int n) {
        return client.getBuffer(n);
    }

    @Override
    public void returnBuffer(ByteBuffer buf) {
        client.returnBuffer(buf);
    }

    @Override
    public void setMinBufferSize(int n) {
        client.setMinBufferSize(n);
    }

    private final Object readlock = new Object();

    void asyncReceive(ByteBuffer buffer) {
        synchronized (readlock) {
            try {
                if (reader == null) {
                    reader = new FrameReader(buffer);
                } else {
                    reader.input(buffer);
                }
                while (true) {
                    if (reader.haveFrame()) {
                        List<ByteBuffer> buffers = reader.frame();

                        ByteBufferConsumer bbc = new ByteBufferConsumer(buffers, this::getBuffer);
                        processFrame(bbc);
                        if (bbc.consumed()) {
                            reader = new FrameReader();
                            return;
                        } else {
                            reader = new FrameReader(reader);
                        }
                    } else
                        return;
                }
            } catch (Throwable e) {
                String msg = Utils.stackTrace(e);
                Log.logTrace(msg);
                shutdown(e);
            }
        }
    }

    void shutdown(Throwable t) {
        System.err.println("Shutdown: " + t);
        t.printStackTrace();
        closed = true;
        client2.deleteConnection(this);
        Collection<Stream> c = streams.values();
        for (Stream s : c) {
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
    void processFrame(ByteBufferConsumer bbc) throws IOException, InterruptedException {
        Http2Frame frame = Http2Frame.readIncoming(bbc);
        Log.logFrames(frame, "IN");
        int streamid = frame.streamid();
        if (streamid == 0) {
            handleCommonFrame(frame);
        } else {
            Stream stream = getStream(streamid);
            if (stream == null) {
                // should never receive a frame with unknown stream id
                resetStream(streamid, ResetFrame.PROTOCOL_ERROR);
            }
            if (frame instanceof PushPromiseFrame) {
                PushPromiseFrame pp = (PushPromiseFrame)frame;
                handlePushPromise(stream, pp);
            } else if (frame instanceof HeaderFrame) {
                // decode headers (or continuation)
                decodeHeaders((HeaderFrame) frame, stream.rspHeadersConsumer());
                stream.incoming(frame);
            } else
                stream.incoming(frame);
        }
    }

    private void handlePushPromise(Stream parent, PushPromiseFrame pp)
            throws IOException, InterruptedException {

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

        Stream.PushedStream pushStream = createPushStream(parent, pushReq);
        pushStream.registerStream(promisedStreamid);
        parent.incoming_pushPromise(pushReq, pushStream);
    }

    private void handleCommonFrame(Http2Frame frame)
            throws IOException, InterruptedException {

        switch (frame.type()) {
          case SettingsFrame.TYPE:
          { SettingsFrame f = (SettingsFrame)frame;
            handleSettings(f);}
            break;
          case PingFrame.TYPE:
          { PingFrame f = (PingFrame)frame;
            handlePing(f);}
            break;
          case GoAwayFrame.TYPE:
          { GoAwayFrame f = (GoAwayFrame)frame;
            handleGoAway(f);}
            break;
          case WindowUpdateFrame.TYPE:
          { WindowUpdateFrame f = (WindowUpdateFrame)frame;
            handleWindowUpdate(f);}
            break;
          default:
            protocolError(ErrorFrame.PROTOCOL_ERROR);
        }
    }

    void resetStream(int streamid, int code) throws IOException, InterruptedException {
        Log.logError(
            "Resetting stream {0,number,integer} with error code {1,number,integer}",
            streamid, code);
        ResetFrame frame = new ResetFrame();
        frame.streamid(streamid);
        frame.setErrorCode(code);
        sendFrame(frame);
        streams.remove(streamid);
    }

    private void handleWindowUpdate(WindowUpdateFrame f)
            throws IOException, InterruptedException {
        updateSendWindow(f.getUpdate());
    }

    private void protocolError(int errorCode)
            throws IOException, InterruptedException {
        GoAwayFrame frame = new GoAwayFrame();
        frame.setErrorCode(errorCode);
        sendFrame(frame);
        String msg = "Error code: " + errorCode;
        shutdown(new IOException("protocol error"));
    }

    private void handleSettings(SettingsFrame frame)
            throws IOException, InterruptedException {
        if (frame.getFlag(SettingsFrame.ACK)) {
            // ignore ack frames for now.
            return;
        }
        serverSettings = frame;
        SettingsFrame ack = getAckFrame(frame.streamid());
        sendFrame(ack);
    }

    private void handlePing(PingFrame frame)
            throws IOException, InterruptedException {
        frame.setFlag(PingFrame.ACK);
        sendFrame(frame);
    }

    private void handleGoAway(GoAwayFrame frame)
            throws IOException, InterruptedException {
        //System.err.printf("GoAWAY: %s\n", ErrorFrame.stringForCode(frame.getErrorCode()));
        shutdown(new IOException("GOAWAY received"));
    }

    private void initCommon() {
        clientSettings = client2.getClientSettings();

        // serverSettings will be updated by server
        serverSettings = SettingsFrame.getDefaultSettings();
        hpackOut = new Encoder(serverSettings.getParameter(HEADER_TABLE_SIZE));
        hpackIn = new Decoder(clientSettings.getParameter(HEADER_TABLE_SIZE));
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
        ByteBufferGenerator bg = new ByteBufferGenerator(this);
        bg.getBuffer(PREFACE_BYTES.length).put(PREFACE_BYTES);
        ByteBuffer[] ba = bg.getBufferArray();
        connection.write(ba, 0, ba.length);

        bg = new ByteBufferGenerator(this);
        SettingsFrame sf = client2.getClientSettings();
        Log.logFrames(sf, "OUT");
        sf.writeOutgoing(bg);
        WindowUpdateFrame wup = new WindowUpdateFrame();
        wup.streamid(0);
        // send a Window update for the receive buffer we are using
        // minus the initial 64 K specified in protocol
        wup.setUpdate(client2.client().getReceiveBufferSize() - (64 * 1024 - 1));
        wup.computeLength();
        wup.writeOutgoing(bg);
        Log.logFrames(wup, "OUT");
        ba = bg.getBufferArray();
        connection.write(ba, 0, ba.length);
    }

    /**
     * Returns an existing Stream with given id, or null if doesn't exist
     */
    Stream getStream(int streamid) {
        return streams.get(streamid);
    }

    /**
     * Creates Stream with given id.
     */
    Stream createStream(Exchange exchange) {
        Stream stream = new Stream(client, this, exchange);
        return stream;
    }

    Stream.PushedStream createPushStream(Stream parent, HttpRequestImpl pushReq) {
        Stream.PushGroup<?> pg = parent.request.pushGroup();
        return new Stream.PushedStream(pg, client, this, parent, pushReq);
    }

    void putStream(Stream stream, int streamid) {
        streams.put(streamid, stream);
    }

    void deleteStream(Stream stream) {
        streams.remove(stream.streamid);
    }

    static final int MAX_STREAM = Integer.MAX_VALUE - 2;

    // Number of header bytes in a Headers Frame
    final static int HEADERS_HEADER_SIZE = 15;

    // Number of header bytes in a Continuation frame
    final static int CONTIN_HEADER_SIZE = 9;

    /**
     * Encode the headers into a List<ByteBuffer> and then create HEADERS
     * and CONTINUATION frames from the list and return the List<Http2Frame>.
     *
     * @param frame
     * @return
     */
    private LinkedList<Http2Frame> encodeHeaders(OutgoingHeaders frame) {
        LinkedList<ByteBuffer> buffers = new LinkedList<>();
        ByteBuffer buf = getBuffer();
        buffers.add(buf);
        encodeHeadersImpl(frame.stream.getRequestPseudoHeaders(), buffers);
        encodeHeadersImpl(frame.getUserHeaders(), buffers);
        encodeHeadersImpl(frame.getSystemHeaders(), buffers);

        for (ByteBuffer b : buffers) {
            b.flip();
        }

        LinkedList<Http2Frame> frames = new LinkedList<>();
        int maxframesize = getMaxSendFrameSize();

        HeadersFrame oframe = new HeadersFrame();
        oframe.setFlags(frame.getFlags());
        oframe.streamid(frame.streamid());

        oframe.setHeaderBlock(getBufferArray(buffers, maxframesize));
        frames.add(oframe);
        // Any buffers left?
        boolean done = buffers.isEmpty();
        if (done) {
            oframe.setFlag(HeaderFrame.END_HEADERS);
        } else {
            ContinuationFrame cf = null;
            while (!done) {
                cf = new ContinuationFrame();
                cf.streamid(frame.streamid());
                cf.setHeaderBlock(getBufferArray(buffers, maxframesize));
                frames.add(cf);
                done = buffers.isEmpty();
            }
            cf.setFlag(HeaderFrame.END_HEADERS);
        }
        return frames;
    }

    // should always return at least one buffer
    private static ByteBuffer[] getBufferArray(LinkedList<ByteBuffer> list, int maxsize) {
        assert maxsize >= BUFSIZE;
        LinkedList<ByteBuffer> newlist = new LinkedList<>();
        int size = list.size();
        int nbytes = 0;
        for (int i=0; i<size; i++) {
            ByteBuffer buf = list.getFirst();
            if (nbytes + buf.remaining() <= maxsize) {
                nbytes += buf.remaining();
                newlist.add(buf);
                list.remove();
            } else {
                break;
            }
        }
        return newlist.toArray(empty);
    }

    /**
     * Encode all the headers from the given HttpHeadersImpl into the given List.
     */
    private void encodeHeadersImpl(HttpHeaders hdrs, LinkedList<ByteBuffer> buffers) {
        ByteBuffer buffer;
        if (!(buffer = buffers.getLast()).hasRemaining()) {
            buffer = getBuffer();
            buffers.add(buffer);
        }
        for (Map.Entry<String, List<String>> e : hdrs.map().entrySet()) {
            String key = e.getKey();
            String lkey = key.toLowerCase();
            List<String> values = e.getValue();
            for (String value : values) {
                hpackOut.header(lkey, value);
                boolean encoded = false;
                do {
                    encoded = hpackOut.encode(buffer);
                    if (!encoded) {
                        buffer = getBuffer();
                        buffers.add(buffer);
                    }
                } while (!encoded);
            }
        }
    }

    public void sendFrames(List<Http2Frame> frames) throws IOException, InterruptedException {
        for (Http2Frame frame : frames) {
            sendFrame(frame);
        }
    }

    static Throwable getExceptionFrom(CompletableFuture<?> cf) {
        try {
            cf.get();
            return null;
        } catch (Throwable e) {
            if (e.getCause() != null)
                return e.getCause();
            else
                return e;
        }
    }


    void execute(Runnable r) {
        executor.execute(r, null);
    }

    private final Object sendlock = new Object();

    /**
     *
     */
    void sendFrame(Http2Frame frame) {
        synchronized (sendlock) {
            try {
                if (frame instanceof OutgoingHeaders) {
                    OutgoingHeaders oh = (OutgoingHeaders) frame;
                    Stream stream = oh.getStream();
                    stream.registerStream(nextstreamid);
                    oh.streamid(nextstreamid);
                    nextstreamid += 2;
                    // set outgoing window here. This allows thread sending
                    // body to proceed.
                    stream.updateOutgoingWindow(getInitialSendWindowSize());
                    LinkedList<Http2Frame> frames = encodeHeaders(oh);
                    for (Http2Frame f : frames) {
                        sendOneFrame(f);
                    }
                } else {
                    sendOneFrame(frame);
                }

            } catch (IOException e) {
                if (!closed) {
                    Log.logError(e);
                    shutdown(e);
                }
            }
        }
    }

    /**
     * Send a frame.
     *
     * @param frame
     * @throws IOException
     */
    private void sendOneFrame(Http2Frame frame) throws IOException {
        ByteBufferGenerator bbg = new ByteBufferGenerator(this);
        frame.computeLength();
        Log.logFrames(frame, "OUT");
        frame.writeOutgoing(bbg);
        ByteBuffer[] currentBufs = bbg.getBufferArray();
        connection.write(currentBufs, 0, currentBufs.length);
    }


    private SettingsFrame getAckFrame(int streamid) {
        SettingsFrame frame = new SettingsFrame();
        frame.setFlag(SettingsFrame.ACK);
        frame.streamid(streamid);
        return frame;
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
}
