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
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.common.ValidatingHeadersConsumer;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.CancelPushFrame;
import jdk.internal.net.http.http3.frames.DataFrame;
import jdk.internal.net.http.http3.frames.FramesDecoder;
import jdk.internal.net.http.http3.frames.HeadersFrame;
import jdk.internal.net.http.http3.frames.Http3Frame;
import jdk.internal.net.http.http3.frames.MalformedFrame;
import jdk.internal.net.http.http3.frames.PartialFrame;
import jdk.internal.net.http.http3.frames.UnknownFrame;
import jdk.internal.net.http.http3.streams.Http3Streams;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.QPackException;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.quic.BuffersReader;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;

final class Http3ServerStreamImpl {
    private final Http3ServerConnection serverConn;
    private final Logger debug;
    final QuicBidiStream stream;
    final SequentialScheduler readScheduler = SequentialScheduler.lockingScheduler(this::readLoop);
    final SequentialScheduler writeScheduler = SequentialScheduler.lockingScheduler(this::writeLoop);
    final QuicStreamReader reader;
    final QuicStreamWriter writer;
    final BuffersReader.ListBuffersReader incoming = BuffersReader.list();
    final DecodingCallback headersConsumer;
    final HeaderFrameReader headersReader;
    final HttpHeadersBuilder requestHeadersBuilder;
    final ReentrantLock writeLock = new ReentrantLock();
    final Condition writeEnabled = writeLock.newCondition();
    final CompletableFuture<HttpHeaders> requestHeadersCF = new MinimalFuture<>();
    final CompletableFuture<Http3ServerExchange> exchangeCF;
    final BlockingQueue<ByteBuffer> requestBodyQueue = new LinkedBlockingQueue<>();
    private volatile boolean eof;

    volatile PartialFrame partialFrame;

    Http3ServerStreamImpl(Http3ServerConnection http3ServerConnection, QuicBidiStream stream) {
        this.serverConn = http3ServerConnection;
        this.debug = Utils.getDebugLogger(this.serverConn::dbgTag);
        this.stream = stream;
        requestHeadersBuilder = new HttpHeadersBuilder();
        headersConsumer = new HeadersConsumer();
        headersReader = http3ServerConnection.newHeaderFrameReader(headersConsumer);
        writer = stream.connectWriter(writeScheduler);
        reader = stream.connectReader(readScheduler);
        exchangeCF = requestHeadersCF.thenApply(this::startExchange);
        // TODO: add a start() method that calls reader.start(), and
        //       call it outside of the constructor
        reader.start();
    }

    Http3ServerConnection serverConnection() {
        return this.serverConn;
    }

    private void readLoop() {
        try {
            readLoop0();
        } catch (QPackException qe) {
            boolean isConnectionError = qe.isConnectionError();
            Http3Error error = qe.http3Error();
            Throwable cause = qe.getCause();
            if (isConnectionError) {
                headersConsumer.onConnectionError(cause, error);
            } else {
                headersConsumer.onStreamError(cause, error);
            }
        }
    }

    private void readLoop0() {
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
                    serverConn.decodeHeaders(partialHeaders, buffer, headersReader);
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
                                serverConn.decodeHeaders(partialHeaders, buf, headersReader);
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
                        serverConn.close(Http3Error.H3_FRAME_UNEXPECTED,
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
                    serverConn.close(malformed.getErrorCode(),
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
                if (debug.on())
                    debug.log("H3 Server: stream %s reset", reader.stream().streamId());
                readScheduler.stop();
                resetReceived();
            }
            if (debug.on())
                debug.log("H3 Server: exiting read loop");
        } catch (Throwable t) {
            if (debug.on())
                debug.log("H3 Server: read loop failed: " + t);
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
                serverConn.close(Http3Error.H3_INTERNAL_ERROR, serverConn.message(t));
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
            exchangeCF.thenApply(en -> {
                en.is.resetStream(new IOException("stopSendingRequested"));
                return en;
            });
            return;
        }

        String msg = "Stream %s reset by peer: %s"
                .formatted(streamId(), readErrorString("no error code"));
        if (debug.on())
            debug.log("H3 Server: reset received: " + msg);
        var io = new IOException(msg);
        requestHeadersCF.completeExceptionally(io);
        exchangeCF.thenApply((e) -> e.streamResetByPeer(io));
    }

    void receiveData(DataFrame partialDataFrame, ByteBuffer buffer) {
        if (debug.on()) {
            debug.log("receiving data: " + buffer.remaining() + " on stream=" + stream.streamId());
        }
        ByteBuffer received = partialDataFrame.nextPayloadBytes(buffer);
        requestBodyQueue.add(received);
    }

    void cancelPushFrameReceived(CancelPushFrame cancel) {
        serverConn.cancelPush(cancel.getPushId());
    }

    class RequestBodyInputStream extends InputStream {
        // Non-null if the stream is terminated.
        // Points to an IOException on error, or Boolean.TRUE on EOF.
        private final AtomicReference<Object> closeReason = new AtomicReference<>();
        // uses an unbounded blocking queue in which the readrLoop
        // publishes the DataFrames payload...
        ByteBuffer current;

        ByteBuffer current() throws IOException {
            while (true) {
                Object reason = closeReason.get();
                if (reason != null) {
                    if (reason == Boolean.TRUE) {
                        throw new IOException("Stream is closed");
                    } else {
                        throw new IOException((IOException)reason);
                    }
                }
                if (current != null && (current.hasRemaining() || current == QuicStreamReader.EOF)) {
                    return current;
                }
                try {
                    if (debug.on())
                        debug.log("Taking buffer from queue");
                    // Blocking call
                    current = requestBodyQueue.take();
                } catch (InterruptedException e) {
                    var io = new InterruptedIOException();
                    Thread.currentThread().interrupt();
                    io.initCause(e);
                    throw io;
                }
            }
        }

        @Override
        public int read() throws IOException {
            ByteBuffer buffer = current();
            if (buffer == QuicStreamReader.EOF) {
                return -1;
            }
            return buffer.get() & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            int remaining = len;
            while (remaining > 0) {
                ByteBuffer buffer = current();
                if (buffer == QuicStreamReader.EOF) {
                    return len == remaining ? -1 : len - remaining;
                }
                int count = Math.min(buffer.remaining(), remaining);
                buffer.get(b, off + (len - remaining), count);
                remaining -= count;
            }
            return len - remaining;
        }

        @Override
        public void close() throws IOException {
            if (closeReason.getAndSet(Boolean.TRUE) == Boolean.TRUE) return;
            if (debug.on())
                debug.log("Closing request body input stream");
            requestBodyQueue.add(QuicStreamReader.EOF);
            stream.requestStopSending(Http3Error.H3_NO_ERROR.code());
        }

        void resetStream(IOException io) {
            if (!closeReason.compareAndSet(null, io)) return;
            if (debug.on()) {
                debug.log("Closing request body input stream: " + io);
            }
            requestBodyQueue.add(QuicStreamReader.EOF);
            stream.requestStopSending(Http3Error.H3_NO_ERROR.code());
        }
    }

    Http3ServerExchange startExchange(HttpHeaders headers) {
        Http3ServerExchange exchange = new Http3ServerExchange(this, headers,
                new RequestBodyInputStream(),
                serverConn.quicConnection().getTLSEngine().getSession());
        try {
            serverConn.server().submitExchange(exchange);
        } catch (Exception e) {
            try {
                exchange.close(new IOException(e));
            } catch (IOException ex) {
                if (debug.on())
                    debug.log("Failed to close exchange: " + ex);
            }
        }
        return exchange;
    }

    long streamId() {
        return stream.streamId();
    }

    private void writeLoop() {
        writeLock.lock();
        try {
            writeEnabled.signalAll();
        } finally {
            writeLock.unlock();
        }
    }

    void close() {
        serverConn.exchangeClosed(this);
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
            HttpHeaders requestHeaders = requestHeadersBuilder.build();
            headersReader.reset();
            requestHeadersCF.complete(requestHeaders);
        }

        @Override
        public void onConnectionError(Throwable throwable, Http3Error http3Error) {
            try {
                stream.reset(http3Error.code());
                serverConn.connectionError(throwable, http3Error);
            } catch (IOException ioe) {
                serverConn.close(http3Error.code(),
                        ioe.getMessage());
            }
        }

        @Override
        public void onStreamError(Throwable throwable, Http3Error http3Error) {
            try {
                stream.reset(http3Error.code());
            } catch (IOException ioe) {
                serverConn.close(http3Error.code(),
                        ioe.getMessage());
            }
        }

        @Override
        public long streamId() {
            return stream.streamId();
        }
    }
}
