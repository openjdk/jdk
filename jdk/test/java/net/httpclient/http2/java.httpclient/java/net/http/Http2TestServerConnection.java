/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import static java.net.http.SettingsFrame.HEADER_TABLE_SIZE;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import sun.net.httpclient.hpack.Decoder;
import sun.net.httpclient.hpack.DecodingCallback;
import sun.net.httpclient.hpack.Encoder;

/**
 * Represents one HTTP2 connection, either plaintext upgraded from HTTP/1.1
 * or HTTPS opened using "h2" ALPN.
 */
public class Http2TestServerConnection {
    final Http2TestServer server;
    @SuppressWarnings({"rawtypes","unchecked"})
    final Map<Integer, Queue> streams; // input q per stream
    final Queue<Http2Frame> outputQ;
    int nextstream;
    final Socket socket;
    final InputStream is;
    final OutputStream os;
    Encoder hpackOut;
    Decoder hpackIn;
    SettingsFrame clientSettings, serverSettings;
    final ExecutorService exec;
    final boolean secure;
    final Http2Handler handler;
    volatile boolean stopping;
    int nextPushStreamId = 2;

    final static ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    final static byte[] EMPTY_BARRAY = new byte[0];

    final static byte[] clientPreface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();

    Http2TestServerConnection(Http2TestServer server, Socket socket) throws IOException {
        System.err.println("New connection from " + socket);
        this.server = server;
        this.streams = Collections.synchronizedMap(new HashMap<>());
        this.outputQ = new Queue<>();
        this.socket = socket;
        this.clientSettings = server.clientSettings;
        this.serverSettings = server.serverSettings;
        this.exec = server.exec;
        this.secure = server.secure;
        this.handler = server.handler;
        is = new BufferedInputStream(socket.getInputStream());
        os = new BufferedOutputStream(socket.getOutputStream());
    }

    void close() {
        streams.forEach((i, q) -> {
            q.close();
        });
        stopping = true;
        try {
            socket.close();
            // TODO: put a reset on each stream
        } catch (IOException e) {
        }
    }

    private void readPreface() throws IOException {
        int len = clientPreface.length;
        byte[] bytes = new byte[len];
        is.readNBytes(bytes, 0, len);
        if (Arrays.compare(clientPreface, bytes) != 0) {
            throw new IOException("Invalid preface: " + new String(bytes, 0, len));
        }
    }

    String doUpgrade() throws IOException {
        String upgrade = readHttp1Request();
        String h2c = getHeader(upgrade, "Upgrade");
        if (h2c == null || !h2c.equals("h2c")) {
            throw new IOException("Bad upgrade 1 " + h2c);
        }

        sendHttp1Response(101, "Switching Protocols", "Connection", "Upgrade",
                "Upgrade", "h2c");

        sendSettingsFrame();
        readPreface();

        String clientSettingsString = getHeader(upgrade, "HTTP2-Settings");
        clientSettings = getSettingsFromString(clientSettingsString);

        return upgrade;
    }

    /**
     * Client settings payload provided in base64 HTTP1 header. Decode it
     * and add a header so we can interpret it.
     *
     * @param s
     * @return
     * @throws IOException
     */
    private SettingsFrame getSettingsFromString(String s) throws IOException {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        byte[] payload = decoder.decode(s);
        ByteBuffer bb1 = ByteBuffer.wrap(payload);
        // simulate header of Settings Frame
        ByteBuffer bb0 = ByteBuffer.wrap(
                new byte[] {0, 0, (byte)payload.length, 4, 0, 0, 0, 0, 0});
        ByteBufferConsumer bbc = new ByteBufferConsumer(
                new LinkedList<ByteBuffer>(List.of(bb0, bb1)),
                this::getBuffer);
        Http2Frame frame = Http2Frame.readIncoming(bbc);
        if (!(frame instanceof SettingsFrame))
            throw new IOException("Expected SettingsFrame");
        return (SettingsFrame)frame;
    }

    void run() throws Exception {
        String upgrade = null;
        if (!secure) {
            upgrade = doUpgrade();
        } else {
            readPreface();
            sendSettingsFrame(true);
            clientSettings = (SettingsFrame) readFrame();
            nextstream = 1;
        }

        hpackOut = new Encoder(serverSettings.getParameter(HEADER_TABLE_SIZE));
        hpackIn = new Decoder(clientSettings.getParameter(HEADER_TABLE_SIZE));

        exec.submit(() -> {
            readLoop();
        });
        exec.submit(() -> {
            writeLoop();
        });
        if (!secure) {
            createPrimordialStream(upgrade);
            nextstream = 3;
        }
    }

    static class BufferPool implements BufferHandler {

        public void setMinBufferSize(int size) {
        }

        public ByteBuffer getBuffer(int size) {
            if (size == -1)
                size = 32 * 1024;
            return ByteBuffer.allocate(size);
        }

        public void returnBuffer(ByteBuffer buffer) {
        }
    }

    static BufferPool bufferpool = new BufferPool();

    private void writeFrame(Http2Frame frame) throws IOException {
        ByteBufferGenerator bg = new ByteBufferGenerator(bufferpool);
        frame.computeLength();
        System.err.println("Writing frame " + frame.toString());
        frame.writeOutgoing(bg);
        ByteBuffer[] bufs = bg.getBufferArray();
        int c = 0;
        for (ByteBuffer buf : bufs) {
            byte[] ba = buf.array();
            int start = buf.arrayOffset() + buf.position();
            c += buf.remaining();
            os.write(ba, start, buf.remaining());
        }
        os.flush();
        System.err.printf("wrote %d bytes\n", c);
    }

    void handleStreamReset(ResetFrame resetFrame) throws IOException {
        // TODO: cleanup
        throw new IOException("Stream reset");
    }

    private void handleCommonFrame(Http2Frame f) throws IOException {
        if (f instanceof SettingsFrame) {
            serverSettings = (SettingsFrame) f;
            if (serverSettings.getFlag(SettingsFrame.ACK)) // ignore
            {
                return;
            }
            // otherwise acknowledge it
            SettingsFrame frame = new SettingsFrame();
            frame.setFlag(SettingsFrame.ACK);
            frame.streamid(0);
            outputQ.put(frame);
            return;
        }
        System.err.println("Received ---> " + f.toString());
        throw new UnsupportedOperationException("Not supported yet.");
    }

    void sendWindowUpdates(int len, int streamid) throws IOException {
        if (len == 0)
            return;
        WindowUpdateFrame wup = new WindowUpdateFrame();
        wup.streamid(streamid);
        wup.setUpdate(len);
        outputQ.put(wup);
        wup = new WindowUpdateFrame();
        wup.streamid(0);
        wup.setUpdate(len);
        outputQ.put(wup);
    }

    HttpHeadersImpl decodeHeaders(List<HeaderFrame> frames) {
        HttpHeadersImpl headers = new HttpHeadersImpl();

        DecodingCallback cb = (name, value) -> {
            headers.addHeader(name.toString(), value.toString());
        };

        for (HeaderFrame frame : frames) {
            ByteBuffer[] buffers = frame.getHeaderBlock();
            for (ByteBuffer buffer : buffers) {
                hpackIn.decode(buffer, false, cb);
            }
        }
        hpackIn.decode(EMPTY_BUFFER, true, cb);
        return headers;
    }

    String getRequestLine(String request) {
        int eol = request.indexOf(CRLF);
        return request.substring(0, eol);
    }

    // First stream (1) comes from a plaintext HTTP/1.1 request
    @SuppressWarnings({"rawtypes","unchecked"})
    void createPrimordialStream(String request) throws IOException {
        HttpHeadersImpl headers = new HttpHeadersImpl();
        String requestLine = getRequestLine(request);
        String[] tokens = requestLine.split(" ");
        if (!tokens[2].equals("HTTP/1.1")) {
            throw new IOException("bad request line");
        }
        URI uri = null;
        try {
            uri = new URI(tokens[1]);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        String host = getHeader(request, "Host");
        if (host == null) {
            throw new IOException("missing Host");
        }

        headers.setHeader(":method", tokens[0]);
        headers.setHeader(":scheme", "http"); // always in this case
        headers.setHeader(":authority", host);
        headers.setHeader(":path", uri.getPath());
        Queue q = new Queue();
        String body = getRequestBody(request);
        headers.setHeader("Content-length", Integer.toString(body.length()));

        addRequestBodyToQueue(body, q);
        streams.put(1, q);
        exec.submit(() -> {
            handleRequest(headers, q, 1);
        });
    }

    // all other streams created here
    @SuppressWarnings({"rawtypes","unchecked"})
    void createStream(HeaderFrame frame) throws IOException {
        List<HeaderFrame> frames = new LinkedList<>();
        frames.add(frame);
        int streamid = frame.streamid();
        if (streamid != nextstream) {
            throw new IOException("unexpected stream id");
        }
        nextstream += 2;

        while (!frame.getFlag(HeaderFrame.END_HEADERS)) {
            Http2Frame f = readFrame();
            if (!(f instanceof HeaderFrame)) {
                handleCommonFrame(f); // should only be error frames
            } else {
                frame = (HeaderFrame) f;
                frames.add(frame);
            }
        }
        HttpHeadersImpl headers = decodeHeaders(frames);
        Queue q = new Queue();
        streams.put(streamid, q);
        exec.submit(() -> {
            handleRequest(headers, q, streamid);
        });
    }

    // runs in own thread. Handles request from start to finish. Incoming frames
    // for this stream/request delivered on Q

    @SuppressWarnings({"rawtypes","unchecked"})
    void handleRequest(HttpHeadersImpl headers, Queue queue, int streamid) {
        String method = headers.firstValue(":method").orElse("");
        System.out.println("method = " + method);
        String path = headers.firstValue(":path").orElse("");
        System.out.println("path = " + path);
        String scheme = headers.firstValue(":scheme").orElse("");
        System.out.println("scheme = " + scheme);
        String authority = headers.firstValue(":authority").orElse("");
        System.out.println("authority = " + authority);
        HttpHeadersImpl rspheaders = new HttpHeadersImpl();
        int winsize = clientSettings.getParameter(
                        SettingsFrame.INITIAL_WINDOW_SIZE);
        System.err.println ("Stream window size = " + winsize);
        try (
            BodyInputStream bis = new BodyInputStream(queue, streamid, this);
            BodyOutputStream bos = new BodyOutputStream(streamid, winsize, this);
        )
        {
            String us = scheme + "://" + authority + path;
            URI uri = new URI(us);
            boolean pushAllowed = clientSettings.getParameter(SettingsFrame.ENABLE_PUSH) == 1;
            Http2TestExchange exchange = new Http2TestExchange(streamid, method,
                    headers, rspheaders, uri, bis, bos, this, pushAllowed);

            // give to user
            handler.handle(exchange);

            // everything happens in the exchange from here. Hopefully will
            // return though.
        } catch (Throwable e) {
            System.err.println("TestServer: handleRequest exception: " + e);
            e.printStackTrace();
        }
    }

    // Runs in own thread

    @SuppressWarnings({"rawtypes","unchecked"})
    void readLoop() {
        try {
            while (!stopping) {
                Http2Frame frame = readFrame();
                int stream = frame.streamid();
                if (stream == 0) {
                    if (frame.type() == WindowUpdateFrame.TYPE) {
                        WindowUpdateFrame wup = (WindowUpdateFrame) frame;
                        updateConnectionWindow(wup.getUpdate());
                    } else {
                        // other common frame types
                        handleCommonFrame(frame);
                    }
                } else {
                    Queue q = streams.get(stream);
                    if (frame.type() == HeadersFrame.TYPE) {
                        if (q != null) {
                            System.err.println("HEADERS frame for existing stream! Error.");
                            // TODO: close connection
                            continue;
                        } else {
                            createStream((HeadersFrame) frame);
                        }
                    } else {
                        if (q == null) {
                            System.err.printf("Non Headers frame received with"+
                                " non existing stream (%d) ", frame.streamid());
                            System.err.println(frame);
                            continue;
                        }
                        if (frame.type() == WindowUpdateFrame.TYPE) {
                            WindowUpdateFrame wup = (WindowUpdateFrame) frame;
                            synchronized (updaters) {
                                Consumer<Integer> r = updaters.get(stream);
                                r.accept(wup.getUpdate());
                            }
                        } else {
                            q.put(frame);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            close();
            if (!stopping) {
                System.err.println("Http server reader thread shutdown");
                e.printStackTrace();
            }
        }
    }

    // set streamid outside plus other specific fields
    void encodeHeaders(HttpHeadersImpl headers, HeaderFrame out) {
        List<ByteBuffer> buffers = new LinkedList<>();

        ByteBuffer buf = getBuffer();
        boolean encoded;
        for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
            List<String> values = entry.getValue();
            String key = entry.getKey().toLowerCase();
            for (String value : values) {
                do {
                    hpackOut.header(key, value);
                    encoded = hpackOut.encode(buf);
                    if (!encoded) {
                        buf.flip();
                        buffers.add(buf);
                        buf = getBuffer();
                    }
                } while (!encoded);
            }
        }
        buf.flip();
        buffers.add(buf);
        out.setFlags(HeaderFrame.END_HEADERS);
        out.setHeaderBlock(buffers.toArray(bbarray));
    }

    static void closeIgnore(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {}
    }

    // Runs in own thread
    void writeLoop() {
        try {
            while (!stopping) {
                Http2Frame frame = outputQ.take();
                if (frame instanceof ResponseHeaders) {
                    ResponseHeaders rh = (ResponseHeaders)frame;
                    HeadersFrame hf = new HeadersFrame();
                    encodeHeaders(rh.headers, hf);
                    hf.streamid(rh.streamid());
                    writeFrame(hf);
                } else if (frame instanceof OutgoingPushPromise) {
                    handlePush((OutgoingPushPromise)frame);
                } else
                    writeFrame(frame);
            }
            System.err.println("Connection writer stopping");
        } catch (Throwable e) {
            e.printStackTrace();
            /*close();
            if (!stopping) {
                e.printStackTrace();
                System.err.println("TestServer: writeLoop exception: " + e);
            }*/
        }
    }

    private void handlePush(OutgoingPushPromise op) throws IOException {
        PushPromiseFrame pp = new PushPromiseFrame();
        encodeHeaders(op.headers, pp);
        int promisedStreamid = nextPushStreamId;
        nextPushStreamId += 2;
        pp.streamid(op.parentStream);
        pp.setPromisedStream(promisedStreamid);
        writeFrame(pp);
        final InputStream ii = op.is;
        final BodyOutputStream oo = new BodyOutputStream(
                promisedStreamid,
                clientSettings.getParameter(
                        SettingsFrame.INITIAL_WINDOW_SIZE), this);
        oo.goodToGo();
        exec.submit(() -> {
            try {
                ResponseHeaders oh = getPushResponse(promisedStreamid);
                outputQ.put(oh);
                ii.transferTo(oo);
            } catch (Throwable ex) {
                System.err.printf("TestServer: pushing response error: %s\n",
                        ex.toString());
            } finally {
                closeIgnore(ii);
                closeIgnore(oo);
            }
        });

    }

    // returns a minimal response with status 200
    // that is the response to the push promise just sent
    private ResponseHeaders getPushResponse(int streamid) {
        HttpHeadersImpl h = new HttpHeadersImpl();
        h.addHeader(":status", "200");
        ResponseHeaders oh = new ResponseHeaders(h);
        oh.streamid(streamid);
        return oh;
    }

    private ByteBuffer getBuffer() {
        return ByteBuffer.allocate(8 * 1024);
    }

    private Http2Frame readFrame() throws IOException {
        byte[] buf = new byte[9];
        if (is.readNBytes(buf, 0, 9) != 9)
            throw new IOException("readFrame: connection closed");
        int len = 0;
        for (int i = 0; i < 3; i++) {
            int n = buf[i] & 0xff;
            //System.err.println("n = " + n);
            len = (len << 8) + n;
        }
        byte[] rest = new byte[len];
        int n = is.readNBytes(rest, 0, len);
        if (n != len)
            throw new IOException("Error reading frame");
        ByteBufferConsumer bc = new ByteBufferConsumer(
                new LinkedList<ByteBuffer>(List.of(ByteBuffer.wrap(buf), ByteBuffer.wrap(rest))),
                this::getBuffer);
        return Http2Frame.readIncoming(bc);
    }

    void sendSettingsFrame() throws IOException {
        sendSettingsFrame(false);
    }

    void sendSettingsFrame(boolean now) throws IOException {
        if (serverSettings == null) {
            serverSettings = SettingsFrame.getDefaultSettings();
        }
        if (now) {
            writeFrame(serverSettings);
        } else {
            outputQ.put(serverSettings);
        }
    }

    String readUntil(String end) throws IOException {
        int number = end.length();
        int found = 0;
        StringBuilder sb = new StringBuilder();
        while (found < number) {
            char expected = end.charAt(found);
            int c = is.read();
            if (c == -1) {
                throw new IOException("Connection closed");
            }
            char c0 = (char) c;
            sb.append(c0);
            if (c0 != expected) {
                found = 0;
                continue;
            }
            found++;
        }
        return sb.toString();
    }

    private int getContentLength(String headers) {
        return getIntHeader(headers, "Content-length");
    }

    private int getIntHeader(String headers, String name) {
        String val = getHeader(headers, name);
        if (val == null) {
            return -1;
        }
        return Integer.parseInt(val);
    }

    private String getHeader(String headers, String name) {
        String headers1 = headers.toLowerCase(); // not efficient
        name = CRLF + name.toLowerCase();
        int start = headers1.indexOf(name);
        if (start == -1) {
            return null;
        }
        start += 2;
        int end = headers1.indexOf(CRLF, start);
        String line = headers.substring(start, end);
        start = line.indexOf(':');
        if (start == -1) {
            return null;
        }
        return line.substring(start + 1).trim();
    }

    final static String CRLF = "\r\n";

    String readHttp1Request() throws IOException {
        String headers = readUntil(CRLF + CRLF);
        int clen = getContentLength(headers);
        // read the content. There shouldn't be content but ..
        byte[] buf = new byte[clen];
        is.readNBytes(buf, 0, clen);
        String body = new String(buf, "US-ASCII");
        return headers + body;
    }

    void sendHttp1Response(int code, String msg, String... headers) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ")
                .append(code)
                .append(' ')
                .append(msg)
                .append(CRLF);
        int numheaders = headers.length;
        for (int i = 0; i < numheaders; i += 2) {
            sb.append(headers[i])
                    .append(": ")
                    .append(headers[i + 1])
                    .append(CRLF);
        }
        sb.append(CRLF);
        String s = sb.toString();
        os.write(s.getBytes("US-ASCII"));
        os.flush();
    }

    private void unexpectedFrame(Http2Frame frame) {
        System.err.println("OOPS. Unexpected");
        assert false;
    }

    final static ByteBuffer[] bbarray = new ByteBuffer[0];

    // wrapper around a BlockingQueue that throws an exception when it's closed
    // Each stream has one of these

    String getRequestBody(String request) {
        int bodystart = request.indexOf(CRLF+CRLF);
        String body;
        if (bodystart == -1)
            body = "";
        else
            body = request.substring(bodystart+4);
        return body;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    void addRequestBodyToQueue(String body, Queue q) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(body.getBytes(StandardCharsets.US_ASCII));
        DataFrame df = new DataFrame();
        df.streamid(1); // only used for primordial stream
        df.setData(buf);
        df.computeLength();
        df.setFlag(DataFrame.END_STREAM);
        q.put(df);
    }

    // window updates done in main reader thread because they may
    // be used to unblock BodyOutputStreams waiting for WUPs

    HashMap<Integer,Consumer<Integer>> updaters = new HashMap<>();

    void registerStreamWindowUpdater(int streamid, Consumer<Integer> r) {
        synchronized(updaters) {
            updaters.put(streamid, r);
        }
    }

    int sendWindow = 64 * 1024 - 1; // connection level send window

    /**
     * BodyOutputStreams call this to get the connection window first.
     *
     * @param amount
     */
    synchronized void obtainConnectionWindow(int amount) throws InterruptedException {
       while (amount > 0) {
           int n = Math.min(amount, sendWindow);
           amount -= n;
           sendWindow -= n;
           if (amount > 0)
               wait();
       }
    }

    synchronized void updateConnectionWindow(int amount) {
        sendWindow += amount;
        notifyAll();
    }

    // simplified output headers class. really just a type safe container
    // for the hashmap.

    static class ResponseHeaders extends Http2Frame {
        HttpHeadersImpl headers;

        ResponseHeaders(HttpHeadersImpl headers) {
            this.headers = headers;
        }

        @Override
        void readIncomingImpl(ByteBufferConsumer bc) throws IOException {
            throw new UnsupportedOperationException("Not supported ever!");
        }

        @Override
        void computeLength() {
            throw new UnsupportedOperationException("Not supported ever!");
        }
    }
}
