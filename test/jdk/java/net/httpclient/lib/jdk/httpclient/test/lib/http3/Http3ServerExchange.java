/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongSupplier;

import javax.net.ssl.SSLSession;

import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.DataFrame;
import jdk.internal.net.http.http3.frames.HeadersFrame;
import jdk.internal.net.http.http3.frames.PushPromiseFrame;
import jdk.internal.net.http.http3.streams.Http3Streams;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.quic.streams.QuicSenderStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;

public final class Http3ServerExchange implements Http2TestExchange {

    private final Http3ServerStreamImpl serverStream;
    private final Http3ServerConnection serverConn;
    private final Logger debug;
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
    final Http3ServerStreamImpl.RequestBodyInputStream is;
    final ResponseBodyOutputStream os;
    private boolean unknownReservedFrameAlreadySent;

    Http3ServerExchange(Http3ServerStreamImpl serverStream, HttpHeaders requestHeaders,
                        Http3ServerStreamImpl.RequestBodyInputStream is, SSLSession sslSession) {
        this.serverStream = serverStream;
        this.serverConn = serverStream.serverConnection();
        this.debug = Utils.getDebugLogger(this.serverConn::dbgTag);
        this.requestHeaders = requestHeaders;
        this.sslSession = sslSession;
        this.is = is;
        this.os = new ResponseBodyOutputStream(connectionTag(), debug, serverStream.writer, serverStream.writeLock,
                serverStream.writeEnabled, this::getResponseLength);
        method = requestHeaders.firstValue(":method").orElse("");
        //System.out.println("method = " + method);
        path = requestHeaders.firstValue(":path").orElse("");
        //System.out.println("path = " + path);
        scheme = requestHeaders.firstValue(":scheme").orElse("");
        //System.out.println("scheme = " + scheme);
        authority = requestHeaders.firstValue(":authority").orElse("");
        if (!path.isEmpty() && !path.startsWith("/")) {
            throw new IllegalArgumentException("Path is not absolute: " + path);
        }
        uri = URI.create(scheme + "://" + authority + path);
        rspheadersBuilder = new HttpHeadersBuilder();
    }

    Http3ServerConnection http3Connection() {
        return serverConn;
    }


    String connectionTag() {
        return serverConn.quicConnection().logTag();
    }

    long getResponseLength() {
        return responseLength;
    }

    @Override
    public String toString() {
        return "H3Server Http3ServerExchange(%s)".formatted(serverStream.streamId());
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
            serverStream.close();
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
        if (serverStream.writer.sendingState().isSending()) {
            if (debug.on()) {
                debug.log(this + " resetting writer with H3_INTERNAL_ERROR");
            }
            serverStream.writer.reset(Http3Error.H3_INTERNAL_ERROR.code());
        }
        is.resetStream(io);
        os.closeInternal();
        close();
    }

    public Http3ServerExchange streamResetByPeer(IOException io) {
        try {
            if (debug.on())
                debug.log("H3 Server closing exchange: " + io);
            close(io);
        } catch (IOException e) {
            if (debug.on())
                debug.log("Failed to close stream %s", serverStream.streamId());
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
        try {
            optionallySendUnknownOrReservedFrame();
            this.responseLength = responseLength;
            sendResponseHeaders(serverStream.streamId(), serverStream.writer, isHeadRequest(),
                    rCode, responseLength, rspheadersBuilder, os);
        } catch (Exception ex) {
            throw new IOException("failed to send headers: " + ex, ex);
        }
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
        String tag = "streamId=" + streamId + " ";
        // in case of HEAD request the caller is supposed to set Content-Length
        // directly - and the responseLength passed here is supposed to be -1
        if (responseLength != 0 && rCode != 204 && !isHeadRequest) {
            long clen = responseLength > 0 ? responseLength : 0;
            rspheadersBuilder.setHeader("Content-length", Long.toString(clen));
        }
        final HttpHeadersBuilder pseudoHeadersBuilder = new HttpHeadersBuilder();
        pseudoHeadersBuilder.setHeader(":status", Integer.toString(rCode));
        final HttpHeaders pseudoHeaders = pseudoHeadersBuilder.build();
        final HttpHeaders headers = rspheadersBuilder.build();
        // order of headers matters - pseudo headers first followed by rest of the headers
        var payload = serverConn.encodeHeaders(1024, streamId, pseudoHeaders, headers);
        if (debug.on())
            debug.log(tag + "headers payload: " + Utils.remaining(payload));
        HeadersFrame frame = new HeadersFrame(Utils.remaining(payload));
        ByteBuffer buffer = ByteBuffer.allocate(frame.headersSize());
        frame.writeHeaders(buffer);
        buffer.flip();
        if (debug.on()) {
            debug.log(tag + "Writing HeaderFrame headers: " + Utils.asHexString(buffer));
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
                debug.log(tag + "Writing HeaderFrame payload: " + Utils.asHexString(buf));
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
                serverStream.writer.queueForWriting(f.toByteBuffer());
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
        return serverConn.quicConnection().peerAddress();
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) serverConn.quicConnection().localAddress();
    }

    @Override
    public String getConnectionKey() {
        return serverConn.connectionKey();
    }

    @Override
    public String getProtocol() {
        return "HTTP/3";
    }

    @Override
    public HttpClient.Version getServerVersion() {
        return HttpClient.Version.HTTP_3;
    }

    @Override
    public HttpClient.Version getExchangeVersion() {
        return HttpClient.Version.HTTP_3;
    }

    @Override
    public boolean serverPushAllowed() {
        return true;
    }

    @Override
    public void serverPush(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream content)
            throws IOException {
        try {
            serverPushWithId(uri, reqHeaders, rspHeaders, content);
        } catch (IOException io) {
            if (debug.on())
                debug.log("Failed to push " + uri + ": " + io);
            throw io;
        }
    }

    @Override
    public long serverPushWithId(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream content)
            throws IOException {
        HttpHeaders combinePromiseHeaders = combinePromiseHeaders(uri, reqHeaders);
        long pushId = serverConn.nextPushId();
        if (debug.on()) {
            debug.log("Server sending serverPushWithId(" + pushId + "): " + uri);
        }
        // send PUSH_PROMISE frame
        sendPushPromiseFrame(pushId, uri, combinePromiseHeaders);
        if (debug.on())
            debug.log("Server sent PUSH_PROMISE(" + pushId + ")");
        // now open push stream and send response headers + body
        Http3ServerConnection.PushPromise pp = sendPushResponse(pushId, combinePromiseHeaders, rspHeaders, content);
        assert pushId == pp.pushId();
        return pp.pushId();
    }

    @Override
    public long sendPushId(long pushId, URI uri, HttpHeaders headers) throws IOException {
        HttpHeaders combinePromiseHeaders = combinePromiseHeaders(uri, headers);
        return sendPushPromiseFrame(pushId, uri, combinePromiseHeaders);
    }

    @Override
    public void sendPushResponse(long pushId, URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream content)
            throws IOException {
        HttpHeaders combinePromiseHeaders = combinePromiseHeaders(uri, reqHeaders);
        Http3ServerConnection.PushPromise pp = sendPushResponse(pushId, combinePromiseHeaders, rspHeaders, content);
        assert pushId == pp.pushId();
    }

    @Override
    public void resetStream(long code) throws IOException {
        os.resetStream(code);
    }

    @Override
    public void cancelPushId(long pushId) throws IOException {
        serverConn.sendCancelPush(pushId);
    }

    @Override
    public long waitForMaxPushId(long pushId) throws InterruptedException {
        return serverConn.waitForMaxPushId(pushId);
    }

    @Override
    public Encoder qpackEncoder() {
        return serverConn.qpackEncoder();
    }

    @Override
    public CompletableFuture<ConnectionSettings> clientHttp3Settings() {
        return serverConn.clientHttp3Settings();
    }

    private long sendPushPromiseFrame(long pushId, URI uri, HttpHeaders headers)
            throws IOException {
        if (pushId == -1) pushId = serverConn.nextPushId();
        List<ByteBuffer> payload = serverConn.encodeHeaders(1024, serverStream.streamId(), headers);
        PushPromiseFrame frame = new PushPromiseFrame(pushId, Utils.remaining(payload));
        ByteBuffer buffer = ByteBuffer.allocate(frame.headersSize());
        frame.writeHeaders(buffer);
        buffer.flip();
        boolean last = frame.length() == 0;
        if (last) {
            if (debug.on()) {
                debug.log("last payload sent: empty headers, no body");
            }
            serverStream.writer.scheduleForWriting(buffer, false);
        } else {
            serverStream.writer.queueForWriting(buffer);
        }
        int size = payload.size();
        for (int i = 0; i < size; i++) {
            last = i == size - 1;
            var buf = payload.get(i);
            if (last) {
                serverStream.writer.scheduleForWriting(buf, false);
            } else {
                serverStream.writer.queueForWriting(buf);
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
        serverStream.reader.stream().requestStopSending(errorCode);
    }

    private QuicSenderStream cancel(QuicSenderStream s) {
        try {
            switch (s.sendingState()) {
                case READY, SEND, DATA_SENT -> s.reset(Http3Error.H3_REQUEST_CANCELLED.code());
            }
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
        return s;
    }

    private Http3ServerConnection.PushPromise sendPushResponse(long pushId,
                                                               HttpHeaders reqHeaders,
                                                               HttpHeaders rspHeaders,
                                                               InputStream body) {
        var stream = serverConn.quicConnection()
                .openNewLocalUniStream(Duration.ofSeconds(10));
        final Http3ServerConnection.PushPromise promise =
                serverConn.addPendingPush(pushId, stream, reqHeaders, this);
        if (!(promise instanceof Http3ServerConnection.PendingPush)) {
            stream.thenApply(this::cancel);
            return promise;
        }
        stream.thenApplyAsync(s -> {
            if (debug.on()) {
                debug.log("Server open(streamId=" + s.streamId() + ", pushId=" + pushId + ")");
            }
            String tag = "streamId=" + s.streamId() + ": ";
            var push = serverConn.getPushPromise(pushId);
            if (push instanceof Http3ServerConnection.CancelledPush) {
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
                sendResponseHeaders(s.streamId(), pushw, false, 200, 0,
                        new HttpHeadersBuilder(rspHeaders), os);
                if (debug.on()) {
                    debug.log(tag + "Server push response headers sent pushId=" + pushId);
                }
                switch (s.sendingState()) {
                    case SEND, READY -> {
                        if (!s.stopSendingReceived()) {
                            body.transferTo(os);
                            serverConn.addPushPromise(pushId, new Http3ServerConnection.CompletedPush(pushId, reqHeaders));
                            os.close();
                            if (debug.on()) {
                                debug.log(tag + "Server push response body sent pushId=" + pushId);
                            }
                        } else {
                            if (debug.on()) {
                                debug.log(tag + "Server push response body cancelled pushId=" + pushId);
                            }
                            serverConn.addPushPromise(pushId, new Http3ServerConnection.CancelledPush(pushId));
                            cancel(s);
                        }
                    }
                    case RESET_SENT, RESET_RECVD -> {
                        if (debug.on()) {
                            debug.log(tag + "Server push response body cancelled pushId=" + pushId);
                        }
                        // benign race if already cancelled, stateless marker
                        serverConn.addPushPromise(pushId, new Http3ServerConnection.CancelledPush(pushId));
                    }
                    default -> {
                        if (debug.on()) {
                            debug.log(tag + "Server push response body cancelled pushId=" + pushId);
                        }
                        serverConn.addPushPromise(pushId, new Http3ServerConnection.CancelledPush(pushId));
                        cancel(s);
                    }
                }
                body.close();
            } catch (IOException io) {
                if (debug.on()) {
                    debug.log(tag + "Server failed to send pushId=" + pushId + ": " + io);
                }
                throw new UncheckedIOException(io);
            }
            return serverConn.getPushPromise(pushId);
        }, serverConn.server().getQuicServer().executor()).exceptionally(t -> {
            if (debug.on()) {
                debug.log("Server failed to send PushPromise(pushId=" + pushId + "): " + t);
            }
            serverConn.addPushPromise(pushId, new Http3ServerConnection.CancelledPush(pushId));
            try {
                body.close();
            } catch (IOException io) {
                if (debug.on()) {
                    debug.log("Failed to close PushPromise stream(pushId="
                            + pushId + "): " + io);
                }
            }
            return serverConn.getPushPromise(pushId);
        });
        return promise;
    }

    @Override
    public CompletableFuture<Long> sendPing() {
        final QuicConnectionImpl quicConn = serverConn.quicConnection();
        var executor = quicConn.quicInstance().executor();
        return quicConn.requestSendPing()
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
            var buffer = ByteBuffer.allocate(1);
            buffer.put((byte) b);
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
         *
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
                        Log.logResponse(() -> logTag() + ": HTTP/3 Server unblocked - credits: "
                                + osw.credit() + ", closed: " + closed);
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
                    default -> {
                    }
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
