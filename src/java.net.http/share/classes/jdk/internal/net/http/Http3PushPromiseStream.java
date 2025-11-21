/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.ProtocolException;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.Http3PushManager.CancelPushReason;
import jdk.internal.net.http.common.HttpBodySubscriberWrapper;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.SubscriptionBase;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.FramesDecoder;
import jdk.internal.net.http.http3.frames.HeadersFrame;
import jdk.internal.net.http.http3.frames.PushPromiseFrame;
import jdk.internal.net.http.qpack.Decoder;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.quic.streams.QuicReceiverStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;

import static jdk.internal.net.http.http3.Http3Error.H3_FRAME_UNEXPECTED;

/**
 * This class represents an HTTP/3 PushPromise stream.
 */
final class Http3PushPromiseStream<T> extends Http3Stream<T> {

    private final Logger debug = Utils.getDebugLogger(this::dbgTag);
    private final Http3Connection connection;
    private final HttpHeadersBuilder respHeadersBuilder;
    private final PushRespHeadersConsumer respHeadersConsumer;
    private final HeaderFrameReader respHeaderFrameReader;
    private final Decoder qpackDecoder;
    private final AtomicReference<Throwable> errorRef;
    private final CompletableFuture<Response> pushCF = new MinimalFuture<>();
    private final CompletableFuture<HttpResponse<T>> responseCF;
    private final QuicReceiverStream stream;
    private final QuicStreamReader reader;
    private final Http3ExchangeImpl<T> parent;
    private final long pushId;
    private final Http3PushManager pushManager;
    private final BodyHandler<T> pushHandler;

    private final FramesDecoder framesDecoder =
            new FramesDecoder(this::dbgTag, FramesDecoder::isAllowedOnPromiseStream);
    private final SequentialScheduler readScheduler =
            SequentialScheduler.lockingScheduler(this::processQuicData);
    private final ReentrantLock stateLock = new ReentrantLock();
    private final H3FrameOrderVerifier frameOrderVerifier = H3FrameOrderVerifier.newForPushPromiseStream();

    final SubscriptionBase userSubscription =
            new SubscriptionBase(readScheduler, this::cancel, this::onSubscriptionError);

    volatile boolean closed;
    volatile BodySubscriber<T> pendingResponseSubscriber;
    volatile BodySubscriber<T> responseSubscriber;
    volatile CompletableFuture<T> responseBodyCF;
    volatile boolean responseReceived;
    volatile int responseCode;
    volatile Response response;
    volatile boolean stopRequested;
    private String dbgTag = null;

    Http3PushPromiseStream(Exchange<T> exchange,
                           final Http3Connection connection,
                           final Http3PushManager pushManager,
                           final QuicReceiverStream stream,
                           final CompletableFuture<HttpResponse<T>> responseCF,
                           final BodyHandler<T> pushHandler,
                           Http3ExchangeImpl<T> parent,
                           long pushId) {
        super(exchange);
        this.responseCF = responseCF;
        this.pushHandler = pushHandler;
        this.errorRef = new AtomicReference<>();
        this.pushId = pushId;
        this.connection = connection;
        this.pushManager = pushManager;
        this.stream = stream;
        this.parent = parent;
        this.respHeadersBuilder = new HttpHeadersBuilder();
        this.respHeadersConsumer = new PushRespHeadersConsumer();
        this.qpackDecoder = connection.qpackDecoder();
        this.respHeaderFrameReader = qpackDecoder.newHeaderFrameReader(respHeadersConsumer);
        this.reader = stream.connectReader(readScheduler);
        debug.log("Http3PushPromiseStream created");
    }

    void start() {
        exchange.exchImpl = this;
        parent.onHttp3PushStreamStarted(exchange.request(), this);
        this.reader.start();
    }

    long pushId() {
        return pushId;
    }

    String dbgTag() {
        if (dbgTag != null) return dbgTag;
        long streamId = streamId();
        String sid = streamId == -1 ? "?" : String.valueOf(streamId);
        String ctag = connection == null ? null : connection.dbgTag();
        String tag = "Http3PushPromiseStream(" + ctag + ", streamId=" + sid + ", pushId="+ pushId + ")";
        if (streamId == -1) return tag;
        return dbgTag = tag;
    }

    @Override
    long streamId() {
        var stream = this.stream;
        return stream == null ? -1 : stream.streamId();
    }

    private final class PushRespHeadersConsumer extends StreamHeadersConsumer {

        public PushRespHeadersConsumer() {
            super(Context.RESPONSE);
        }

        void resetDone() {
            if (debug.on()) {
                debug.log("Response builder cleared, ready to receive new headers.");
            }
        }

        @Override
        String headerFieldType() {
            return "PUSH RESPONSE HEADER FIELD";
        }

        @Override
        Decoder qpackDecoder() {
            return qpackDecoder;
        }

        @Override
        protected String formatMessage(String message, String header) {
            //  Malformed requests or responses that are detected MUST be
            //  treated as a stream error of type H3_MESSAGE_ERROR.
            return "malformed push response: " + super.formatMessage(message, header);
        }


        @Override
        HeaderFrameReader headerFrameReader() {
            return respHeaderFrameReader;
        }

        @Override
        HttpHeadersBuilder headersBuilder() {
            return respHeadersBuilder;
        }

        @Override
        void headersCompleted() {
            handleResponse();
        }

        @Override
        public long streamId() {
            return stream.streamId();
        }
    }

    @Override
    HttpQuicConnection connection() {
        return connection.connection();
    }


    // The Http3StreamResponseSubscriber is registered with the HttpClient
    // to ensure that it gets completed if the SelectorManager aborts due
    // to unexpected exceptions.
    private void registerResponseSubscriber(Http3PushStreamResponseSubscriber<?> subscriber) {
        if (client().registerSubscriber(subscriber)) {
            debug.log("Reference response body for h3 stream: " + streamId());
            client().h3StreamReference();
        }
    }

    private void unregisterResponseSubscriber(Http3PushStreamResponseSubscriber<?> subscriber) {
        if (client().unregisterSubscriber(subscriber)) {
            debug.log("Unreference response body for h3 stream: " + streamId());
            client().h3StreamUnreference();
        }
    }

    final class Http3PushStreamResponseSubscriber<U> extends HttpBodySubscriberWrapper<U> {
        Http3PushStreamResponseSubscriber(BodySubscriber<U> subscriber) {
            super(subscriber);
        }

        @Override
        protected void unregister() {
            unregisterResponseSubscriber(this);
        }

        @Override
        protected void register() {
            registerResponseSubscriber(this);
        }
    }

    Http3PushStreamResponseSubscriber<T> createResponseSubscriber(BodyHandler<T> handler,
                                                              ResponseInfo response) {
        debug.log("Creating body subscriber");
        return new Http3PushStreamResponseSubscriber<>(handler.apply(response));
    }

    @Override
    CompletableFuture<Void> ignoreBody() {
        try {
            debug.log("Ignoring body");
            reader.stream().requestStopSending(Http3Error.H3_REQUEST_CANCELLED.code());
            return MinimalFuture.completedFuture(null);
        } catch (Throwable e) {
            Log.logTrace("Error requesting stop sending for stream {0}: {1}",
                    streamId(), e.toString());
            return MinimalFuture.failedFuture(e);
        }
    }

    @Override
    void cancel() {
        debug.log("cancel");
        var stream = this.stream;
        if ((stream == null)) {
            cancel(new IOException("Stream cancelled before streamid assigned"));
        } else {
            cancel(new IOException("Stream " + stream.streamId() + " cancelled"));
        }
    }

    @Override
    void cancel(IOException cause) {
        cancelImpl(cause, Http3Error.H3_REQUEST_CANCELLED);
    }

    @Override
    void onProtocolError(IOException cause) {
        final long streamId = stream.streamId();
        if (debug.on()) {
            debug.log("cancelling exchange on stream %d due to protocol error: %s", streamId, cause.getMessage());
        }
        Log.logError("cancelling exchange on stream {0} due to protocol error: {1}\n", streamId, cause);
        cancelImpl(cause, Http3Error.H3_GENERAL_PROTOCOL_ERROR);
    }

    @Override
    void released() {

    }

    @Override
    void completed() {

    }

    @Override
    boolean isCanceled() {
        return errorRef.get() != null;
    }

    @Override
    Throwable getCancelCause() {
        return errorRef.get();
    }

    @Override
    void cancelImpl(Throwable e, Http3Error error) {
        try {
            var streamid = streamId();
            if (errorRef.compareAndSet(null, e)) {
                if (debug.on()) {
                    if (streamid == -1) debug.log("cancelling stream: %s", e);
                    else debug.log("cancelling stream " + streamid + ":", e);
                }
                if (Log.trace()) {
                    if (streamid == -1) Log.logTrace("cancelling stream: {0}\n", e);
                    else Log.logTrace("cancelling stream {0}: {1}\n", streamid, e);
                }
            } else {
                if (debug.on()) {
                    if (streamid == -1) debug.log("cancelling stream: %s", (Object) e);
                    else debug.log("cancelling stream %s: %s", streamid, e);
                }
            }

            var firstError = errorRef.get();
            completeResponseExceptionally(firstError);
            if (responseBodyCF != null) {
                responseBodyCF.completeExceptionally(firstError);
            }
            // will send a RST_STREAM frame
            var stream = this.stream;
            if (connection.isOpen()) {
                if (stream != null) {
                    if (debug.on())
                        debug.log("request stop sending");
                    stream.requestStopSending(error.code());
                }
            }
        } catch (Throwable ex) {
            debug.log("failed cancelling request: ", ex);
            Log.logError(ex);
        } finally {
            close();
        }
    }

    @Override
    CompletableFuture<Response> getResponseAsync(Executor executor) {
        var cf = pushCF;
        if (executor != null && !cf.isDone()) {
            // protect from executing later chain of CompletableFuture operations from SelectorManager thread
            cf = cf.thenApplyAsync(r -> r, executor);
        }
        Log.logTrace("Response future (stream={0}) is: {1}", streamId(), cf);
        if (debug.on()) debug.log("Response future is %s", cf);
        return cf;
    }

    void completeResponse(Response r) {
        debug.log("Response: " + r);
        Log.logResponse(r::toString);
        pushCF.complete(r); // not strictly required for push API
        // start reading the body using the obtained BodySubscriber
        CompletableFuture<Void> start = new MinimalFuture<>();
        start.thenCompose( v -> readBodyAsync(getPushHandler(), false, getExchange().executor()))
                .whenComplete((T body, Throwable t) -> {
                    if (t != null) {
                        responseCF.completeExceptionally(t);
                        debug.log("Cancelling push promise %s (stream %s) due to: %s", pushId, streamId(), t);
                        pushManager.cancelPushPromise(pushId, t, CancelPushReason.PUSH_CANCELLED);
                        cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED);
                    } else {
                        HttpResponseImpl<T> resp =
                                new HttpResponseImpl<>(r.request, r, null, body, getExchange());
                        debug.log("Completing responseCF: " + resp);
                        pushManager.pushPromiseProcessed(pushId);
                        responseCF.complete(resp);
                    }
                });
        start.completeAsync(() -> null, getExchange().executor());
    }

    // methods to update state and remove stream when finished

    void responseReceived() {
        stateLock.lock();
        try {
            responseReceived0();
        } finally {
            stateLock.unlock();
        }
    }

    private void responseReceived0() {
        assert stateLock.isHeldByCurrentThread();
        responseReceived = true;
        if (debug.on()) debug.log("responseReceived: streamid=%d", streamId());
        close();
    }

    /**
     * same as above but for errors
     */
    void completeResponseExceptionally(Throwable t) {
        pushManager.cancelPushPromise(pushId, t, CancelPushReason.PUSH_CANCELLED);
        responseCF.completeExceptionally(t);
    }

    void nullBody(HttpResponse<T> resp, Throwable t) {
        if (debug.on()) debug.log("nullBody: streamid=%d", streamId());
        // We should have an END_STREAM data frame waiting in the inputQ.
        // We need a subscriber to force the scheduler to process it.
        assert pendingResponseSubscriber == null;
        pendingResponseSubscriber = HttpResponse.BodySubscribers.replacing(null);
        readScheduler.runOrSchedule();
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendHeadersAsync() {
        return MinimalFuture.completedFuture(this);
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendBodyAsync() {
        return MinimalFuture.completedFuture(this);
    }

    CompletableFuture<HttpResponse<T>> responseCF() {
        return responseCF;
    }


    BodyHandler<T> getPushHandler() {
        // ignored parameters to function can be used as BodyHandler
        return this.pushHandler;
    }

    @Override
    CompletableFuture<T> readBodyAsync(BodyHandler<T> handler,
                                       boolean returnConnectionToPool,
                                       Executor executor) {
        try {
            Log.logTrace("Reading body on stream {0}", streamId());
            debug.log("Getting BodySubscriber for: " + response);
            Http3PushStreamResponseSubscriber<T> bodySubscriber =
                    createResponseSubscriber(handler, new ResponseInfoImpl(response));
            CompletableFuture<T> cf = receiveResponseBody(bodySubscriber, executor);

            PushGroup<?> pg = parent.exchange.getPushGroup();
            if (pg != null) {
                // if an error occurs make sure it is recorded in the PushGroup
                cf = cf.whenComplete((t, e) -> pg.pushError(e));
            }
            var bodyCF = cf;
            return bodyCF;
        } catch (Throwable t) {
            // may be thrown by handler.apply
            // TODO: Is this the right error code?
            cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED);
            PushGroup<?> pg = parent.exchange.getPushGroup();
            if (pg != null) {
                // if an error occurs make sure it is recorded in the PushGroup
                pg.pushError(t);
            }
            return MinimalFuture.failedFuture(t);
        }
    }

    // This method doesn't send any frame
    void close() {
        if (closed) return;
        stateLock.lock();
        try {
            if (closed) return;
            closed = true;
        } finally {
            stateLock.unlock();
        }
        if (debug.on()) debug.log("stream %d is now closed", streamId());
        Log.logTrace("Stream {0} is now closed", streamId());

        BodySubscriber<T> subscriber = responseSubscriber;
        if (subscriber == null) subscriber = pendingResponseSubscriber;
        if (subscriber instanceof Http3PushStreamResponseSubscriber<?> h3srs) {
            // ensure subscriber is unregistered
            h3srs.complete(errorRef.get());
        }
        connection.onPushPromiseStreamClosed(this, streamId());
    }

    @Override
    Response newResponse(HttpHeaders responseHeaders, int responseCode) {
        return this.response = new Response(
                exchange.request, exchange, responseHeaders, connection(),
                responseCode, Version.HTTP_3);
    }

    protected void handleResponse() {
        handleResponse(respHeadersBuilder, respHeadersConsumer, readScheduler, debug);
    }

    @Override
    void receivePushPromiseFrame(PushPromiseFrame ppf, List<ByteBuffer> payload) throws IOException {
        readScheduler.stop();
        connectionError(new ProtocolException("Unexpected PUSH_PROMISE on push response stream"), H3_FRAME_UNEXPECTED);
    }

    @Override
    void onPollException(QuicStreamReader reader, IOException io) {
        if (Log.http3()) {
            Log.logHttp3("{0}/streamId={1} pushId={2} #{3} (responseReceived={4}, " +
                            "reader={5}, statusCode={6}, finalStream={9}): {10}",
                    connection().quicConnection().logTag(),
                    String.valueOf(reader.stream().streamId()), pushId, String.valueOf(exchange.multi.id),
                    responseReceived, reader.receivingState(),
                    String.valueOf(responseCode), connection.isFinalStream(), io);
        }
    }

    @Override
    void onReaderReset() {
        long errorCode = stream.rcvErrorCode();
        String resetReason = Http3Error.stringForCode(errorCode);
        Http3Error resetError = Http3Error.fromCode(errorCode)
                .orElse(Http3Error.H3_REQUEST_CANCELLED);
        if (!responseReceived) {
            cancelImpl(new IOException("Stream %s reset by peer: %s"
                            .formatted(streamId(), resetReason)),
                    resetError);
        }
        if (debug.on()) {
            debug.log("Stream %s reset by peer [%s]: Stopping scheduler",
                    streamId(), resetReason);
        }
        readScheduler.stop();
    }

    // Invoked when some data is received from the request-response
    // Quic stream
    private void processQuicData() {
        // Poll bytes from the request-response stream
        // and parses the data to read HTTP/3 frames.
        //
        // If the frame being read is a header frame, send the
        // compacted header field data to QPack.
        //
        // Otherwise, if it's a data frame, send the bytes
        // to the response body subscriber.
        //
        // Finally, if the frame being read is a PushPromiseFrame,
        // sends the compressed field data to the QPack decoder to
        // decode the push promise request headers.
        try {
            processQuicData(reader, framesDecoder, frameOrderVerifier, readScheduler, debug);
        } catch (Throwable t) {
            debug.log("processQuicData - Unexpected exception", t);
            if (!responseReceived) {
                cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED);
            }
        } finally {
            debug.log("processQuicData - leaving - eof: %s", framesDecoder.eof());
        }
    }

    // invoked when ByteBuffers containing the next payload bytes for the
    // given partial header frame are received
    void receiveHeaders(HeadersFrame headers, List<ByteBuffer> payload)
            throws IOException {
        debug.log("receive headers: buffer list: " + payload);
        boolean completed = headers.remaining() == 0;
        boolean eof = false;
        if (payload != null) {
            int last = payload.size() - 1;
            for (int i = 0; i <= last; i++) {
                ByteBuffer buf = payload.get(i);
                boolean endOfHeaders = completed && i == last;
                if (debug.on())
                    debug.log("QPack decoding %s bytes from headers (last: %s)",
                            buf.remaining(), last);
                // if we have finished receiving the header frame, pause reading until
                // the status code has been decoded
                if (endOfHeaders) switchReadingPaused(true);
                qpackDecoder.decodeHeader(buf,
                        endOfHeaders,
                        respHeaderFrameReader);
                if (buf == QuicStreamReader.EOF) {
                    // we are at EOF - no need to pause reading
                    switchReadingPaused(false);
                    eof = true;
                }
            }
        }
        if (!completed && eof) {
            cancelImpl(new EOFException("EOF reached: " + headers),
                    Http3Error.H3_REQUEST_CANCELLED);
        }
    }

    void connectionError(Throwable throwable, long errorCode, String errMsg) {
        if (errorRef.compareAndSet(null, throwable)) {
            var streamid = streamId();
            if (debug.on()) {
                if (streamid == -1) {
                    debug.log("cancelling stream due to connection error", throwable);
                } else {
                    debug.log("cancelling stream " + streamid
                            + " due to connection error", throwable);
                }
            }
            if (Log.trace()) {
                if (streamid == -1) {
                    Log.logTrace( "connection error: {0}", errMsg);
                } else {
                    var format = "cancelling stream {0} due to connection error: {1}";
                    Log.logTrace(format, streamid, errMsg);
                }
            }
        }
        connection.connectionError(this, throwable, errorCode, errMsg);
    }


    // pushes entire response body into response subscriber
    // blocking when required by local or remote flow control
    CompletableFuture<T> receiveResponseBody(BodySubscriber<T> bodySubscriber, Executor executor) {
        // We want to allow the subscriber's getBody() method to block so it
        // can work with InputStreams. So, we offload execution.
        responseBodyCF = ResponseSubscribers.getBodyAsync(executor, bodySubscriber,
                new MinimalFuture<>(), (t) -> this.cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED));

        if (isCanceled()) {
            Throwable t = getCancelCause();
            responseBodyCF.completeExceptionally(t);
        }

        // ensure that the body subscriber will be subsribed and onError() is
        // invoked
        pendingResponseSubscriber = bodySubscriber;
        readScheduler.runOrSchedule(); // in case data waiting already to be processed, or error

        return responseBodyCF;
    }

    void onSubscriptionError(Throwable t) {
        errorRef.compareAndSet(null, t);
        if (debug.on()) debug.log("Got subscription error: %s", (Object) t);
        // This is the special case where the subscriber
        // has requested an illegal number of items.
        // In this case, the error doesn't come from
        // upstream, but from downstream, and we need to
        // handle the error without waiting for the inputQ
        // to be exhausted.
        stopRequested = true;
        readScheduler.runOrSchedule();
    }

    // This loop is triggered to push response body data into
    // the body subscriber.
    void pushResponseData(ConcurrentLinkedQueue<List<ByteBuffer>> responseData) {
        debug.log("pushResponseData");
        boolean onCompleteCalled = false;
        BodySubscriber<T> subscriber = responseSubscriber;
        boolean done = false;
        try {
            if (subscriber == null) {
                subscriber = responseSubscriber = pendingResponseSubscriber;
                if (subscriber == null) {
                    // can't process anything yet
                    return;
                } else {
                    if (debug.on()) debug.log("subscribing user subscriber");
                    subscriber.onSubscribe(userSubscription);
                }
            }
            while (!responseData.isEmpty()) {
                List<ByteBuffer> data = responseData.peek();
                List<ByteBuffer> dsts = Collections.unmodifiableList(data);
                long size = Utils.remaining(dsts, Long.MAX_VALUE);
                boolean finished = dsts.contains(QuicStreamReader.EOF);
                if (size == 0 && finished) {
                    responseData.remove();
                    Log.logTrace("responseSubscriber.onComplete");
                    if (debug.on()) debug.log("pushResponseData: onComplete");
                    subscriber.onComplete();
                    done = true;
                    onCompleteCalled = true;
                    responseReceived();
                    return;
                } else if (userSubscription.tryDecrement()) {
                    responseData.remove();
                    Log.logTrace("responseSubscriber.onNext {0}", size);
                    if (debug.on()) debug.log("pushResponseData: onNext(%d)", size);
                    subscriber.onNext(dsts);
                } else {
                    if (stopRequested) break;
                    debug.log("no demand");
                    return;
                }
            }
            if (framesDecoder.eof() && responseData.isEmpty()) {
                debug.log("pushResponseData: EOF");
                if (!onCompleteCalled) {
                    Log.logTrace("responseSubscriber.onComplete");
                    if (debug.on()) debug.log("pushResponseData: onComplete");
                    subscriber.onComplete();
                    done = true;
                    onCompleteCalled = true;
                    responseReceived();
                    return;
                }
            }
        } catch (Throwable throwable) {
            debug.log("pushResponseData: unexpected exception", throwable);
            errorRef.compareAndSet(null, throwable);
        } finally {
            if (done) responseData.clear();
        }

        Throwable t = errorRef.get();
        if (t != null) {
            try {
                if (!onCompleteCalled) {
                    if (debug.on())
                        debug.log("calling subscriber.onError: %s", (Object) t);
                    subscriber.onError(t);
                } else {
                    if (debug.on())
                        debug.log("already completed: dropping error %s", (Object) t);
                }
            } catch (Throwable x) {
                Log.logError("Subscriber::onError threw exception: {0}", t);
            } finally {
                cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED);
                responseData.clear();
            }
        }
    }

}
