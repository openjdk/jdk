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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.ProtocolException;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;

import jdk.internal.net.http.PushGroup.Acceptor;
import jdk.internal.net.http.common.HttpBodySubscriberWrapper;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.SubscriptionBase;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.common.ValidatingHeadersConsumer;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.DataFrame;
import jdk.internal.net.http.http3.frames.FramesDecoder;
import jdk.internal.net.http.http3.frames.HeadersFrame;
import jdk.internal.net.http.http3.frames.PushPromiseFrame;
import jdk.internal.net.http.qpack.Decoder;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.qpack.QPackException;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.qpack.writers.HeaderFrameWriter;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.streams.QuicStreamWriter;
import static jdk.internal.net.http.http3.ConnectionSettings.UNLIMITED_MAX_FIELD_SECTION_SIZE;

/**
 * This class represents an HTTP/3 Request/Response stream.
 */
final class Http3ExchangeImpl<T> extends Http3Stream<T> {

    private static final String COOKIE_HEADER = "Cookie";
    private final Logger debug = Utils.getDebugLogger(this::dbgTag);
    private final Http3Connection connection;
    private final HttpRequestImpl request;
    private final BodyPublisher requestPublisher;
    private final HttpHeadersBuilder responseHeadersBuilder;
    private final HeadersConsumer rspHeadersConsumer;
    private final HttpHeaders requestPseudoHeaders;
    private final HeaderFrameReader headerFrameReader;
    private final HeaderFrameWriter headerFrameWriter;
    private final Decoder qpackDecoder;
    private final Encoder qpackEncoder;
    private final AtomicReference<Throwable> errorRef;
    private final CompletableFuture<Void> requestBodyCF;

    private final FramesDecoder framesDecoder =
            new FramesDecoder(this::dbgTag, FramesDecoder::isAllowedOnRequestStream);
    private final SequentialScheduler readScheduler =
            SequentialScheduler.lockingScheduler(this::processQuicData);
    private final SequentialScheduler writeScheduler =
            SequentialScheduler.lockingScheduler(this::sendQuicData);
    private final List<CompletableFuture<Response>> response_cfs = new ArrayList<>(5);
    private final ReentrantLock stateLock = new ReentrantLock();
    private final ReentrantLock response_cfs_lock = new ReentrantLock();
    private final H3FrameOrderVerifier frameOrderVerifier = H3FrameOrderVerifier.newForRequestResponseStream();


    final SubscriptionBase userSubscription =
            new SubscriptionBase(readScheduler, this::cancel, this::onSubscriptionError);

    private final QuicBidiStream stream;
    private final QuicStreamReader reader;
    private final QuicStreamWriter writer;
    volatile boolean closed;
    volatile RequestSubscriber requestSubscriber;
    volatile HttpResponse.BodySubscriber<T> pendingResponseSubscriber;
    volatile HttpResponse.BodySubscriber<T> responseSubscriber;
    volatile CompletableFuture<T> responseBodyCF;
    volatile boolean requestSent;
    volatile boolean responseReceived;
    volatile long requestContentLen;
    volatile int responseCode;
    volatile Response response;
    volatile boolean stopRequested;
    volatile boolean deRegistered;
    private String dbgTag = null;
    private final AtomicLong sentQuicBytes = new AtomicLong();

    Http3ExchangeImpl(final Http3Connection connection, final Exchange<T> exchange,
                      final QuicBidiStream stream) {
        super(exchange);
        this.errorRef = new AtomicReference<>();
        this.requestBodyCF = new MinimalFuture<>();
        this.connection = connection;
        this.request = exchange.request();
        this.requestPublisher = request.requestPublisher;  // may be null
        this.responseHeadersBuilder = new HttpHeadersBuilder();
        this.rspHeadersConsumer = new HeadersConsumer(ValidatingHeadersConsumer.Context.RESPONSE);
        this.qpackDecoder = connection.qpackDecoder();
        this.qpackEncoder = connection.qpackEncoder();
        this.headerFrameReader = qpackDecoder.newHeaderFrameReader(rspHeadersConsumer);
        this.headerFrameWriter = qpackEncoder.newHeaderFrameWriter();
        this.requestPseudoHeaders = Utils.createPseudoHeaders(request);
        this.stream = stream;
        this.reader = stream.connectReader(readScheduler);
        this.writer = stream.connectWriter(writeScheduler);
        if (debug.on()) debug.log("Http3ExchangeImpl created");
    }

    public void start() {
        if (exchange.pushGroup != null) {
            connection.checkSendMaxPushId();
        }
        if (Log.http3()) {
            Log.logHttp3("Starting HTTP/3 exchange for {0}/streamId={1} ({2} #{3})",
                    connection.quicConnectionTag(), Long.toString(stream.streamId()),
                    request, Long.toString(exchange.multi.id));
        }
        this.reader.start();
    }

    boolean acceptPushPromise() {
        return exchange.pushGroup != null;
    }

    String dbgTag() {
        if (dbgTag != null) return dbgTag;
        long streamId = streamId();
        String sid = streamId == -1 ? "?" : String.valueOf(streamId);
        String ctag = connection == null ? null : connection.dbgTag();
        String tag = "Http3ExchangeImpl(" + ctag + ", streamId=" + sid + ")";
        if (streamId == -1) return tag;
        return dbgTag = tag;
    }

    @Override
    long streamId() {
        var stream = this.stream;
        return stream == null ? -1 : stream.streamId();
    }

    Http3Connection http3Connection() {
        return connection;
    }

    void recordError(Throwable closeCause) {
        errorRef.compareAndSet(null, closeCause);
    }

    private sealed class HeadersConsumer extends StreamHeadersConsumer permits PushHeadersConsumer {

        private HeadersConsumer(Context context) {
            super(context);
        }

        @Override
        protected HeaderFrameReader headerFrameReader() {
            return headerFrameReader;
        }

        @Override
        protected HttpHeadersBuilder headersBuilder() {
            return responseHeadersBuilder;
        }

        @Override
        protected final Decoder qpackDecoder() {
            return qpackDecoder;
        }

        void resetDone() {
            if (debug.on()) {
                debug.log("Response builder cleared, ready to receive new headers.");
            }
        }


        @Override
        String headerFieldType() {
            return "RESPONSE HEADER FIELD";
        }

        @Override
        protected String formatMessage(String message, String header) {
            //  Malformed requests or responses that are detected MUST be
            //  treated as a stream error of type H3_MESSAGE_ERROR.
            return "malformed response: " + super.formatMessage(message, header);
        }

        @Override
        protected void headersCompleted() {
            handleResponse();
        }

        @Override
        public final long streamId() {
            return stream.streamId();
        }

    }

    private final class PushHeadersConsumer extends HeadersConsumer {
        volatile PushPromiseState state;

        private PushHeadersConsumer() {
            super(Context.REQUEST);
        }

        @Override
        protected HttpHeadersBuilder headersBuilder() {
            return state.headersBuilder();
        }

        @Override
        protected HeaderFrameReader headerFrameReader() {
            return state.reader();
        }

        @Override
        String headerFieldType() {
            return "PUSH REQUEST HEADER FIELD";
        }

        void resetDone() {
            if (debug.on()) {
                debug.log("Push request builder cleared.");
            }
        }

        @Override
        protected String formatMessage(String message, String header) {
            //  Malformed requests or responses that are detected MUST be
            //  treated as a stream error of type H3_MESSAGE_ERROR.
            return "malformed push request: " + super.formatMessage(message, header);
        }

        @Override
        protected void headersCompleted() {
            try {
                if (exchange.pushGroup == null) {
                    long pushId = state.frame().getPushId();
                    connection.noPushHandlerFor(pushId);
                    reset();
                } else {
                    handlePromise(this);
                }
            } catch (IOException io) {
                cancelPushPromise(state, io);
            }
        }

        public void setState(PushPromiseState state) {
            this.state = state;
        }
    }

    // TODO: this is also defined on Stream<T>
    //
    private static boolean hasProxyAuthorization(HttpHeaders headers) {
        return headers.firstValue("proxy-authorization")
                .isPresent();
    }

    // TODO: this is also defined on Stream<T>
    //
    // Determines whether we need to build a new HttpHeader object.
    //
    // Ideally we should pass the filter to OutgoingHeaders refactor the
    // code that creates the HeaderFrame to honor the filter.
    // We're not there yet - so depending on the filter we need to
    // apply and the content of the header we will try to determine
    //  whether anything might need to be filtered.
    // If nothing needs filtering then we can just use the
    // original headers.
    private static boolean needsFiltering(HttpHeaders headers,
                                   BiPredicate<String, String> filter) {
        if (filter == Utils.PROXY_TUNNEL_FILTER || filter == Utils.PROXY_FILTER) {
            // we're either connecting or proxying
            // slight optimization: we only need to filter out
            // disabled schemes, so if there are none just
            // pass through.
            return Utils.proxyHasDisabledSchemes(filter == Utils.PROXY_TUNNEL_FILTER)
                    && hasProxyAuthorization(headers);
        } else {
            // we're talking to a server, either directly or through
            // a tunnel.
            // Slight optimization: we only need to filter out
            // proxy authorization headers, so if there are none just
            // pass through.
            return hasProxyAuthorization(headers);
        }
    }

    // TODO: this is also defined on Stream<T>
    //
    private HttpHeaders filterHeaders(HttpHeaders headers) {
        HttpConnection conn = connection();
        BiPredicate<String, String> filter = conn.headerFilter(request);
        if (needsFiltering(headers, filter)) {
            return HttpHeaders.of(headers.map(), filter);
        }
        return headers;
    }

    @Override
    HttpQuicConnection connection() {
        return connection.connection();
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendHeadersAsync() {
        final MinimalFuture<Void> completable = MinimalFuture.completedFuture(null);
        return completable.thenApply(_ -> this.sendHeaders());
    }

    private Http3ExchangeImpl<T> sendHeaders() {
        assert stream != null;
        assert writer != null;

        if (debug.on()) debug.log("H3 sendHeaders");
        if (Log.requests()) {
            Log.logRequest(request.toString());
        }
        if (requestPublisher != null) {
            requestContentLen = requestPublisher.contentLength();
        } else {
            requestContentLen = 0;
        }

        Throwable t = errorRef.get();
        if (t != null) {
            if (debug.on()) debug.log("H3 stream already cancelled, headers not sent: %s", (Object) t);
            if (t instanceof CompletionException ce) throw ce;
            throw new CompletionException(t);
        }

        HttpHeadersBuilder h = request.getSystemHeadersBuilder();
        if (requestContentLen > 0) {
            h.setHeader("content-length", Long.toString(requestContentLen));
        }
        HttpHeaders sysh = filterHeaders(h.build());
        HttpHeaders userh = filterHeaders(request.getUserHeaders());
        // Filter context restricted from userHeaders
        userh = HttpHeaders.of(userh.map(), Utils.ACCEPT_ALL);
        Utils.setUserAuthFlags(request, userh);

        // Don't override Cookie values that have been set by the CookieHandler.
        final HttpHeaders uh = userh;
        BiPredicate<String, String> overrides =
                (k, v) -> COOKIE_HEADER.equalsIgnoreCase(k)
                        || uh.firstValue(k).isEmpty();

        // Filter any headers from systemHeaders that are set in userHeaders
        //   except for "Cookie:" - user cookies will be appended to system
        //   cookies
        sysh = HttpHeaders.of(sysh.map(), overrides);

        if (Log.headers() || debug.on()) {
            StringBuilder sb = new StringBuilder("H3 HEADERS FRAME (stream=");
            sb.append(streamId()).append(")\n");
            Log.dumpHeaders(sb, "    ", requestPseudoHeaders);
            Log.dumpHeaders(sb, "    ", sysh);
            Log.dumpHeaders(sb, "    ", userh);
            if (Log.headers()) {
                Log.logHeaders(sb.toString());
            } else if (debug.on()) {
                debug.log(sb);
            }
        }

        final Optional<ConnectionSettings> peerSettings = connection.getPeerSettings();
        // It's possible that the peer settings hasn't yet arrived, in which case we use the
        // default of "unlimited" header size limit and proceed with sending the request. As per
        // RFC-9114, section 7.2.4.2, this is allowed: All settings begin at an initial value. Each
        // endpoint SHOULD use these initial values to send messages before the peer's SETTINGS frame
        // has arrived, as packets carrying the settings can be lost or delayed.
        // When the SETTINGS frame arrives, any settings are changed to their new values. This
        // removes the need to wait for the SETTINGS frame before sending messages.
        final long headerSizeLimit = peerSettings.isEmpty() ? UNLIMITED_MAX_FIELD_SECTION_SIZE
                : peerSettings.get().maxFieldSectionSize();
        if (headerSizeLimit != UNLIMITED_MAX_FIELD_SECTION_SIZE) {
            // specific limit has been set on the header size for this connection.
            // we compute the header size and ensure that it doesn't exceed that limit
            final long computedHeaderSize = computeHeaderSize(requestPseudoHeaders, sysh, userh);
            if (computedHeaderSize > headerSizeLimit) {
                // RFC-9114, section 4.2.2: An implementation that has received this parameter
                // SHOULD NOT send an HTTP message header that exceeds the indicated size.
                // we fail the request.
                throw new CompletionException(new ProtocolException("Request headers size" +
                        " exceeds limit set by peer"));
            }
        }
        List<ByteBuffer> buffers = qpackEncoder.encodeHeaders(headerFrameWriter, streamId(),
                1024, requestPseudoHeaders, sysh, userh);
        HeadersFrame headersFrame = new HeadersFrame(Utils.remaining(buffers));
        ByteBuffer buffer = ByteBuffer.allocate(headersFrame.headersSize());
        headersFrame.writeHeaders(buffer);
        buffer.flip();
        long sentBytes = 0;
        try {
            boolean hasNoBody = requestContentLen == 0;
            int last = buffers.size() - 1;
            int toSend = buffer.remaining();
            if (last < 0) {
                writer.scheduleForWriting(buffer, hasNoBody);
            } else {
                writer.queueForWriting(buffer);
            }
            sentBytes += toSend;
            for (int i = 0; i <= last; i++) {
                var nextBuffer = buffers.get(i);
                toSend = nextBuffer.remaining();
                if (i == last) {
                    writer.scheduleForWriting(nextBuffer, hasNoBody);
                } else {
                    writer.queueForWriting(nextBuffer);
                }
                sentBytes += toSend;
            }
        } catch (QPackException qe) {
            if (qe.isConnectionError()) {
                // close the connection
                connection.close(qe.http3Error(), "QPack error", qe.getCause());
            }
            // fail the request
            throw new CompletionException(qe.getCause());
        } catch (IOException io) {
            throw new CompletionException(io);
        } finally {
            if (sentBytes != 0) sentQuicBytes.addAndGet(sentBytes);
        }
        return this;
    }

    private static long computeHeaderSize(final HttpHeaders... headers) {
        // RFC-9114, section 4.2.2 states: The size of a field list is calculated based on
        // the uncompressed size of fields, including the length of the name and value in bytes
        // plus an overhead of 32 bytes for each field.
        final int OVERHEAD_BYTES_PER_FIELD = 32;
        long computedHeaderSize = 0;
        for (final HttpHeaders h : headers) {
            for (final Map.Entry<String, List<String>> entry : h.map().entrySet()) {
                try {
                    computedHeaderSize = Math.addExact(computedHeaderSize,
                            entry.getKey().getBytes(StandardCharsets.US_ASCII).length);
                    for (final String v : entry.getValue()) {
                        computedHeaderSize = Math.addExact(computedHeaderSize,
                                v.getBytes(StandardCharsets.US_ASCII).length);
                    }
                    computedHeaderSize = Math.addExact(computedHeaderSize, OVERHEAD_BYTES_PER_FIELD);
                } catch (ArithmeticException ae) {
                    // overflow, no point trying to compute further, return MAX_VALUE
                    return Long.MAX_VALUE;
                }
            }
        }
        return computedHeaderSize;
    }


    @Override
    CompletableFuture<ExchangeImpl<T>> sendBodyAsync() {
        return sendBodyImpl().thenApply((e) -> this);
    }

    CompletableFuture<Void> sendBodyImpl() {
        requestBodyCF.whenComplete((v, t) -> requestSent());
        try {
            if (debug.on()) debug.log("H3 sendBodyImpl");
            if (requestPublisher != null && requestContentLen != 0) {
                final RequestSubscriber subscriber = new RequestSubscriber(requestContentLen);
                requestPublisher.subscribe(requestSubscriber = subscriber);
            } else {
                // there is no request body, therefore the request is complete,
                // END_STREAM has already sent with outgoing headers
                requestBodyCF.complete(null);
            }
        } catch (Throwable t) {
            cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED);
            requestBodyCF.completeExceptionally(t);
        }
        return requestBodyCF;
    }

    // The Http3StreamResponseSubscriber is registered with the HttpClient
    // to ensure that it gets completed if the SelectorManager aborts due
    // to unexpected exceptions.
    private void registerResponseSubscriber(Http3StreamResponseSubscriber<?> subscriber) {
        if (client().registerSubscriber(subscriber)) {
            if (debug.on()) {
                debug.log("Reference response body for h3 stream: " + streamId());
            }
            client().h3StreamReference();
        }
    }

    private void unregisterResponseSubscriber(Http3StreamResponseSubscriber<?> subscriber) {
        if (client().unregisterSubscriber(subscriber)) {
            if (debug.on()) {
                debug.log("Unreference response body for h3 stream: " + streamId());
            }
            client().h3StreamUnreference();
        }
    }

    final class Http3StreamResponseSubscriber<U> extends HttpBodySubscriberWrapper<U> {

        private final boolean cancelTimerOnTermination;

        Http3StreamResponseSubscriber(BodySubscriber<U> subscriber, boolean cancelTimerOnTermination) {
            super(subscriber);
            this.cancelTimerOnTermination = cancelTimerOnTermination;
        }

        @Override
        protected void unregister() {
            unregisterResponseSubscriber(this);
        }

        @Override
        protected void register() {
            registerResponseSubscriber(this);
        }

        @Override
        protected void onTermination() {
            if (cancelTimerOnTermination) {
                exchange.multi.cancelTimer();
            }
        }

        @Override
        protected void logComplete(Throwable error) {
            if (error == null) {
                if (Log.requests()) {
                    Log.logResponse(() -> "HTTP/3 body successfully completed for: " + request
                            + " #" + exchange.multi.id);
                }
            } else {
                if (Log.requests()) {
                    Log.logResponse(() -> "HTTP/3 body exceptionally completed for: "
                            + request + " (" + error + ")"
                            + " #" + exchange.multi.id);
                }
            }
        }
    }


    @Override
    Http3StreamResponseSubscriber<T> createResponseSubscriber(BodyHandler<T> handler,
                                                              ResponseInfo response) {
        if (debug.on()) debug.log("Creating body subscriber");
        var cancelTimerOnTermination =
                cancelTimerOnResponseBodySubscriberTermination(
                        exchange.request().isWebSocket(), response.statusCode());
        return new Http3StreamResponseSubscriber<>(handler.apply(response), cancelTimerOnTermination);
    }

    @Override
    CompletableFuture<T> readBodyAsync(BodyHandler<T> handler,
                                       boolean returnConnectionToPool,
                                       Executor executor) {
        try {
            if (Log.trace()) {
                Log.logTrace("Reading body on stream {0}", streamId());
            }
            if (debug.on()) debug.log("Getting BodySubscriber for: " + response);
            Http3StreamResponseSubscriber<T> bodySubscriber =
                    createResponseSubscriber(handler, new ResponseInfoImpl(response));
            CompletableFuture<T> cf = receiveResponseBody(bodySubscriber, executor);

            PushGroup<?> pg = exchange.getPushGroup();
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
            return MinimalFuture.failedFuture(t);
        }
    }

    @Override
    CompletableFuture<Void> ignoreBody() {
        try {
            if (debug.on()) debug.log("Ignoring body");
            reader.stream().requestStopSending(Http3Error.H3_REQUEST_CANCELLED.code());
            return MinimalFuture.completedFuture(null);
        } catch (Throwable e) {
            if (Log.trace()) {
                Log.logTrace("Error requesting stop sending for stream {0}: {1}",
                        streamId(), e.toString());
            }
            return MinimalFuture.failedFuture(e);
        }
    }

    @Override
    void cancel() {
        if (debug.on()) debug.log("cancel");
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
        long streamid = streamId();
        if (debug.on()) debug.log("Released stream %d", streamid);
        // remove this stream from the Http2Connection map.
        connection.onExchangeClose(this, streamid);
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
                    if (streamid == -1) debug.log("cancelling stream", e);
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
            if (!requestBodyCF.isDone()) {
                // complete requestBodyCF before cancelling subscription
                requestBodyCF.completeExceptionally(firstError); // we may be sending the body...
                var requestSubscriber = this.requestSubscriber;
                if (requestSubscriber != null) {
                    cancel(requestSubscriber.subscription.get());
                }
            }
            var responseBodyCF = this.responseBodyCF;
            if (responseBodyCF != null) {
                responseBodyCF.completeExceptionally(firstError);
            }
            // will send a RST_STREAM frame
            var stream = this.stream;
            if (connection.isOpen()) {
                if (stream != null && stream.sendingState().isSending()) {
                    // no use reset if already closed.
                    var cause = Utils.getCompletionCause(firstError);
                    if (!(cause instanceof EOFException)) {
                        if (debug.on())
                            debug.log("sending reset %s", error);
                        stream.reset(error.code());
                    }
                }
                if (stream != null) {
                    if (debug.on())
                        debug.log("request stop sending");
                    stream.requestStopSending(error.code());
                }
            }
        } catch (Throwable ex) {
            errorRef.compareAndSet(null, ex);
            if (debug.on())
                debug.log("failed cancelling request: ", ex);
            Log.logError(ex);
        } finally {
            close();
        }
    }

    // cancel subscription and ignore errors in order to continue with
    // the cancel/close sequence.
    private void cancel(Subscription subscription) {
        if (subscription == null) return;
        try { subscription.cancel(); }
        catch (Throwable t) {
            debug.log("Unexpected exception thrown by Subscription::cancel", t);
            if (Log.errors()) {
                Log.logError("Unexpected exception thrown by Subscription::cancel: " + t);
                Log.logError(t);
            }
        }
    }

    @Override
    CompletableFuture<Response> getResponseAsync(Executor executor) {
        CompletableFuture<Response> cf;
        // The code below deals with race condition that can be caused when
        // completeResponse() is being called before getResponseAsync()
        response_cfs_lock.lock();
        try {
            if (!response_cfs.isEmpty()) {
                // This CompletableFuture was created by completeResponse().
                // it will be already completed, unless the expect continue
                // timeout fired
                cf = response_cfs.get(0);
                if (cf.isDone()) {
                    cf = response_cfs.remove(0);
                }

                // if we find a cf here it should be already completed.
                // finding a non completed cf should not happen. just assert it.
                assert cf.isDone() || request.expectContinue && expectTimeoutRaised()
                        : "Removing uncompleted response: could cause code to hang!";
            } else {
                // getResponseAsync() is called first. Create a CompletableFuture
                // that will be completed by completeResponse() when
                // completeResponse() is called.
                cf = new MinimalFuture<>();
                response_cfs.add(cf);
            }
        } finally {
            response_cfs_lock.unlock();
        }
        if (executor != null && !cf.isDone()) {
            // protect from executing later chain of CompletableFuture operations from SelectorManager thread
            cf = cf.thenApplyAsync(r -> r, executor);
        }
        if (Log.trace()) {
            Log.logTrace("Response future (stream={0}) is: {1}", streamId(), cf);
        }
        PushGroup<?> pg = exchange.getPushGroup();
        if (pg != null) {
            // if an error occurs make sure it is recorded in the PushGroup
            cf = cf.whenComplete((t, e) -> pg.pushError(Utils.getCompletionCause(e)));
        }
        if (debug.on()) debug.log("Response future is %s", cf);
        return cf;
    }

    /**
     * Completes the first uncompleted CF on list, and removes it. If there is no
     * uncompleted CF then creates one (completes it) and adds to list
     */
    void completeResponse(Response resp) {
        if (debug.on()) debug.log("completeResponse: %s", resp);
        response_cfs_lock.lock();
        try {
            CompletableFuture<Response> cf;
            int cfs_len = response_cfs.size();
            for (int i = 0; i < cfs_len; i++) {
                cf = response_cfs.get(i);
                if (!cf.isDone() && !expectTimeoutRaised()) {
                    if (Log.trace()) {
                        Log.logTrace("Completing response (streamid={0}): {1}",
                                streamId(), cf);
                    }
                    if (debug.on())
                        debug.log("Completing responseCF(%d) with response headers", i);
                    response_cfs.remove(cf);
                    cf.complete(resp);
                    return;
                } else if (expectTimeoutRaised()) {
                    Log.logTrace("Completing response (streamid={0}): {1}",
                            streamId(), cf);
                    if (debug.on())
                        debug.log("Completing responseCF(%d) with response headers", i);
                    // The Request will be removed in getResponseAsync()
                    cf.complete(resp);
                    return;
                } // else we found the previous response: just leave it alone.
            }
            cf = MinimalFuture.completedFuture(resp);
            if (Log.trace()) {
                Log.logTrace("Created completed future (streamid={0}): {1}",
                        streamId(), cf);
            }
            if (debug.on())
                debug.log("Adding completed responseCF(0) with response headers");
            response_cfs.add(cf);
        } finally {
            response_cfs_lock.unlock();
        }
    }

    @Override
    void expectContinueFailed(int rcode) {
        // Have to mark request as sent, due to no request body being sent in the
        // event of a 417 Expectation Failed or some other non 100 response code
        requestSent();
    }

    // methods to update state and remove stream when finished

    void requestSent() {
        stateLock.lock();
        try {
            requestSent0();
        } finally {
            stateLock.unlock();
        }
    }

    private void requestSent0() {
        assert stateLock.isHeldByCurrentThread();
        requestSent = true;
        if (responseReceived) {
            if (debug.on()) debug.log("requestSent: streamid=%d", streamId());
            close();
        } else {
            if (debug.on()) {
                debug.log("requestSent: streamid=%d but response not received", streamId());
            }
        }
    }

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
        if (requestSent) {
            if (debug.on()) debug.log("responseReceived: streamid=%d", streamId());
            close();
        } else {
            if (debug.on()) {
                debug.log("responseReceived: streamid=%d but request not sent", streamId());
            }
        }
    }

    /**
     * Same as {@link #completeResponse(Response)} above but for errors
     */
    void completeResponseExceptionally(Throwable t) {
        response_cfs_lock.lock();
        try {
            // use index to avoid ConcurrentModificationException
            // caused by removing the CF from within the loop.
            for (int i = 0; i < response_cfs.size(); i++) {
                CompletableFuture<Response> cf = response_cfs.get(i);
                if (!cf.isDone()) {
                    response_cfs.remove(i);
                    cf.completeExceptionally(t);
                    return;
                }
            }
            response_cfs.add(MinimalFuture.failedFuture(t));
        } finally {
            response_cfs_lock.unlock();
        }
    }

    @Override
    void nullBody(HttpResponse<T> resp, Throwable t) {
        if (debug.on()) debug.log("nullBody: streamid=%d", streamId());
        // We should have an END_STREAM data frame waiting in the inputQ.
        // We need a subscriber to force the scheduler to process it.
        assert pendingResponseSubscriber == null;
        pendingResponseSubscriber = HttpResponse.BodySubscribers.replacing(null);
        readScheduler.runOrSchedule();
    }

    /**
     * An unprocessed exchange is one that hasn't been processed by a peer. The local end of the
     * connection would be notified about such exchanges in either of the following 2 ways:
     * <ul>
     *  <li> when it receives a GOAWAY frame with a stream id that tells which
     *    exchanges have been unprocessed.
     *  <li> or when a particular request's stream is reset with the H3_REQUEST_REJECTED error code.
     * </ul>
     * <p>
     * This method is called on such unprocessed exchanges and the implementation of this method
     * will arrange for the request, corresponding to this exchange, to be retried afresh.
     */
    void closeAsUnprocessed() {
        // null exchange implies a PUSH stream and those aren't
        // initiated by the client, so we don't expect them to be
        // considered unprocessed.
        assert this.exchange != null : "PUSH streams aren't expected to be closed as unprocessed";
        // We arrange for the request to be retried on a new connection as allowed
        // by RFC-9114, section 5.2
        this.exchange.markUnprocessedByPeer();
        this.errorRef.compareAndSet(null, new IOException("request not processed by peer"));
        // close the exchange and complete the response CF exceptionally
        close();
        completeResponseExceptionally(this.errorRef.get());
        if (debug.on()) {
            debug.log("request unprocessed by peer " + this.request);
        }
    }

    // This method doesn't send any frame
    void close() {
        if (closed) return;
        Throwable error;
        stateLock.lock();
        try {
            if (closed) return;
            closed = true;
            error = errorRef.get();
        } finally {
            stateLock.unlock();
        }
        if (Log.http3()) {
            if (error == null) {
                Log.logHttp3("Closed HTTP/3 exchange for {0}/streamId={1}",
                        connection.quicConnectionTag(), Long.toString(stream.streamId()));
            } else {
                Log.logHttp3("Closed HTTP/3 exchange for {0}/streamId={1} with error {2}",
                        connection.quicConnectionTag(), Long.toString(stream.streamId()),
                        error);
            }
        }
        if (debug.on()) {
            debug.log("stream %d is now closed with %s",
                    streamId(),
                    error == null ? "no error" : String.valueOf(error));
        }
        if (Log.trace()) {
            Log.logTrace("Stream {0} is now closed", streamId());
        }

        BodySubscriber<T> subscriber = responseSubscriber;
        if (subscriber == null) subscriber = pendingResponseSubscriber;
        if (subscriber instanceof Http3StreamResponseSubscriber<?> h3srs) {
            // ensure subscriber is unregistered
            h3srs.complete(error);
        }
        connection.onExchangeClose(this, streamId());
    }

    class RequestSubscriber implements Flow.Subscriber<ByteBuffer> {
        // can be < 0 if the actual length is not known.
        private final long contentLength;
        private volatile long remainingContentLength;
        private volatile boolean dataHeaderWritten;
        private volatile boolean completed;
        private final AtomicReference<Subscription> subscription = new AtomicReference<>();

        RequestSubscriber(long contentLen) {
            this.contentLength = contentLen;
            this.remainingContentLength = contentLen;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            if (!this.subscription.compareAndSet(null, subscription)) {
                subscription.cancel();
                throw new IllegalStateException("already subscribed");
            }
            if (debug.on())
                debug.log("RequestSubscriber: onSubscribe, request 1");
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            if (debug.on())
                debug.log("RequestSubscriber: onNext(%d)", item.remaining());
            var subscription = this.subscription.get();
            if (writer.stopSendingReceived()) {
                // whether StopSending contains NO_ERROR or not - we should
                // not fail the request and simply stop sending the body.
                // The sender should either reset the stream or send a full
                // response with an error status code if it wants to fail the request.
                Http3Error error = Http3Error.fromCode(writer.stream().sndErrorCode())
                        .orElse(Http3Error.H3_NO_ERROR);
                if (debug.on())
                    debug.log("Stop sending requested by peer (%s): canceling subscription", error);
                requestBodyCF.complete(null);
                subscription.cancel();
                return;
            }

            if (isCanceled() || errorRef.get() != null) {
                if (writer.sendingState().isSending()) {
                    try {
                        if (debug.on()) {
                            debug.log("onNext called after stream cancelled: " +
                                    "resetting stream %s", streamId());
                        }
                        writer.reset(Http3Error.H3_REQUEST_CANCELLED.code());
                    } catch (Throwable t) {
                        if (debug.on()) debug.log("Failed to reset stream: ", t);
                        errorRef.compareAndSet(null, t);
                        requestBodyCF.completeExceptionally(errorRef.get());
                    }
                }
                return;
            }
            long len = item.remaining();
            try {
                writeHeadersIfNeeded(item);
                var remaining = remainingContentLength;
                if (contentLength >= 0) {
                    remaining -= len;
                    remainingContentLength = remaining;
                    if (remaining < 0) {
                        lengthMismatch("Too many bytes in request body");
                        subscription.cancel();
                    }
                }
                var completed = remaining == 0;
                if (completed) this.completed = true;
                writer.scheduleForWriting(item, completed);
                sentQuicBytes.addAndGet(len);
                if (completed) {
                    requestBodyCF.complete(null);
                }
                if (writer.credit() > 0) {
                    if (debug.on())
                        debug.log("RequestSubscriber: request 1");
                    subscription.request(1);
                } else {
                    if (debug.on())
                        debug.log("RequestSubscriber: no more credit");
                }
            } catch (Throwable t) {
                if (writer.stopSendingReceived()) {
                    // We can reach here if we continue sending after stop sending
                    // was received, which may happen since stop sending is
                    // received asynchronously. In that case, we should
                    // not fail the request but simply stop sending the body.
                    // The sender will either reset the stream or send a full
                    // response with an error status code if it wants to fail
                    // or complete the request.
                    if (debug.on())
                        debug.log("Stop sending requested by peer: canceling subscription");
                    requestBodyCF.complete(null);
                    subscription.cancel();
                    return;
                }
                // stop sending was not received: cancel the stream
                errorRef.compareAndSet(null, t);
                if (debug.on()) {
                    debug.log("Unexpected exception in onNext: " + t);
                    debug.log("resetting stream %s", streamId());
                }
                try {
                    writer.reset(Http3Error.H3_REQUEST_CANCELLED.code());
                } catch (Throwable rt) {
                    if (debug.on())
                        debug.log("Failed to reset stream: %s", t);
                }
                cancelImpl(errorRef.get(), Http3Error.H3_REQUEST_CANCELLED);
            }

        }

        private void lengthMismatch(String what) {
            if (debug.on()) {
                debug.log(what + " (%s/%s)",
                        contentLength - remainingContentLength, contentLength);
            }
            try {
                var failed = new IOException("stream=" + streamId() + " "
                        + "[" + Thread.currentThread().getName() + "] "
                        + what + " ("
                        + (contentLength - remainingContentLength) + "/"
                        + contentLength + ")");
                errorRef.compareAndSet(null, failed);
                writer.reset(Http3Error.H3_REQUEST_CANCELLED.code());
                requestBodyCF.completeExceptionally(errorRef.get());
            } catch (Throwable t) {
                if (debug.on())
                    debug.log("Failed to reset stream: %s", t);
            }
            close();
        }

        private void writeHeadersIfNeeded(ByteBuffer item) throws IOException {
            long len = item.remaining();
            if (contentLength >= 0) {
                if (!dataHeaderWritten) {
                    dataHeaderWritten = true;
                    len = contentLength;
                } else {
                    // headers already written: nothing to do.
                    return;
                }
            }
            DataFrame df = new DataFrame(len);
            ByteBuffer headers = ByteBuffer.allocate(df.headersSize());
            df.writeHeaders(headers);
            headers.flip();
            int sent = headers.remaining();
            writer.queueForWriting(headers);
            if (sent != 0) sentQuicBytes.addAndGet(sent);
        }

        @Override
        public void onError(Throwable throwable) {
            if (debug.on())
                debug.log(() -> "RequestSubscriber: onError: " + throwable);
            // ensure that errors are handled within the flow.
            if (errorRef.compareAndSet(null, throwable)) {
                try {
                    writer.reset(Http3Error.H3_REQUEST_CANCELLED.code());
                } catch (Throwable t) {
                    if (debug.on()) debug.log("Failed to reset stream: %s", t);
                }
                requestBodyCF.completeExceptionally(throwable);
                // no need to cancel subscription
                close();
            }
        }

        @Override
        public void onComplete() {
            if (debug.on()) debug.log("RequestSubscriber: send request body completed");
            var completed = this.completed;
            if (completed || errorRef.get() != null) return;
            if (contentLength >= 0 && remainingContentLength != 0) {
                if (remainingContentLength < 0) {
                    lengthMismatch("Too many bytes in request body");
                } else {
                    lengthMismatch("Too few bytes returned by the publisher");
                }
                return;
            }
            this.completed = true;
            try {
                writer.scheduleForWriting(QuicStreamReader.EOF, true);
                requestBodyCF.complete(null);
            } catch (Throwable t) {
                if (debug.on()) debug.log("Failed to complete stream: " + t, t);
                requestBodyCF.completeExceptionally(t);
            }
        }

        void unblock() {
            if (completed || errorRef.get() != null) {
                return;
            }
            var subscription = this.subscription.get();
            try {
                if (writer.credit() > 0) {
                    if (subscription != null) {
                        subscription.request(1);
                    }
                }
            } catch (Throwable throwable) {
                if (debug.on())
                    debug.log(() -> "RequestSubscriber: unblock: " + throwable);
                // ensure that errors are handled within the flow.
                if (errorRef.compareAndSet(null, throwable)) {
                    try {
                        writer.reset(Http3Error.H3_REQUEST_CANCELLED.code());
                    } catch (Throwable t) {
                        if (debug.on()) debug.log("Failed to reset stream: %s", t);
                    }
                    requestBodyCF.completeExceptionally(throwable);
                    cancelImpl(throwable, Http3Error.H3_REQUEST_CANCELLED);
                    subscription.cancel();
                }
            }
        }

    }

    @Override
    Response newResponse(HttpHeaders responseHeaders, int responseCode) {
        this.responseCode = responseCode;
        return this.response = new Response(
                request, exchange, responseHeaders, connection(),
                responseCode, Version.HTTP_3);
    }

    protected void handleResponse() {
        handleResponse(responseHeadersBuilder, rspHeadersConsumer, readScheduler, debug);
    }

    protected void handlePromise(PushHeadersConsumer consumer) throws IOException {
        PushPromiseState state = consumer.state;
        PushPromiseFrame ppf = state.frame();
        promiseMap.remove(ppf);
        long pushId = ppf.getPushId();

        HttpHeaders promiseHeaders = state.headersBuilder().build();
        consumer.reset();

        if (debug.on()) {
            debug.log("received promise headers: %s",
                    promiseHeaders);
        }

        if (Log.headers() || debug.on()) {
            StringBuilder sb = new StringBuilder("PUSH_PROMISE HEADERS (pushId: ")
                    .append(pushId).append("):\n");
            Log.dumpHeaders(sb, "    ", promiseHeaders);
            if (Log.headers()) {
                Log.logHeaders(sb.toString());
            } else if (debug.on()) {
                debug.log(sb);
            }
        }

        String method = promiseHeaders.firstValue(":method")
                .orElseThrow(() -> new ProtocolException("no method in promise request"));
        String path = promiseHeaders.firstValue(":path")
                .orElseThrow(() -> new ProtocolException("no path in promise request"));
        String authority = promiseHeaders.firstValue(":authority")
                .orElseThrow(() -> new ProtocolException("no authority in promise request"));
        if (Set.of("PUT", "DELETE", "OPTIONS", "TRACE").contains(method)) {
            throw new ProtocolException("push method not allowed pushId=" + pushId);
        }
        long clen = promiseHeaders.firstValueAsLong("Content-Length").orElse(-1);
        if (clen > 0) {
            throw new ProtocolException("push headers contain non-zero Content-Length for pushId=" + pushId);
        }
        if (promiseHeaders.firstValue("Transfer-Encoding").isPresent()) {
            throw new ProtocolException("push headers contain Transfer-Encoding for pushId=" + pushId);
        }


        // this will clear the response headers
        //       At this point the push promise stream may not be opened yet
        if (connection.onPushPromiseFrame(this, pushId, promiseHeaders)) {
            // the promise response will be handled from a child of this exchange
            // once the push stream is open, we have nothing more to do here.
            if (debug.on()) {
                debug.log("handling push promise response for %s with request-response stream %s",
                        pushId, streamId());
            }
        } else {
            // the promise response is being handled by another exchange, just accept the id
            if (debug.on()) {
                debug.log("push promise response for %s is already handled by another stream",
                        pushId);
            }
            PushGroup<T> pushGroup = exchange.getPushGroup();
            connection.whenPushAccepted(pushId).thenAccept((accepted) -> {
                if (accepted) {
                    pushGroup.acceptPushPromiseId(connection.newPushId(pushId));
                }
            });
        }
    }

    private void cancelPushPromise(PushPromiseState state, IOException cause) {
        // send CANCEL_PUSH frame here
        long pushId = state.frame().getPushId();
        connection.pushCancelled(pushId, cause);
    }

    @Override
    void onPollException(QuicStreamReader reader, IOException io) {
        if (Log.http3()) {
            Log.logHttp3("{0}/streamId={1} {2} #{3} (requestSent={4}, responseReceived={5}, " +
                            "reader={6}, writer={7}, statusCode={8}, finalStream={9}, " +
                            "receivedQuicBytes={10}, sentQuicBytes={11}): {12}",
                    connection().quicConnection().logTag(),
                    String.valueOf(reader.stream().streamId()), request, String.valueOf(exchange.multi.id),
                    requestSent, responseReceived, reader.receivingState(), writer.sendingState(),
                    String.valueOf(responseCode), connection.isFinalStream(), String.valueOf(receivedQuicBytes()),
                    String.valueOf(sentQuicBytes.get()), io);
        }
    }

    void onReaderReset() {
        long errorCode = stream.rcvErrorCode();
        String resetReason = Http3Error.stringForCode(errorCode);
        Http3Error resetError = Http3Error.fromCode(errorCode)
                .orElse(Http3Error.H3_REQUEST_CANCELLED);
        if (debug.on()) {
            debug.log("Stream %s reset by peer [%s]: ", streamId(), resetReason);
        }
        // if the error is H3_REQUEST_REJECTED then it implies
        // the request wasn't processed and the client is allowed to reissue
        // that request afresh
        if (resetError == Http3Error.H3_REQUEST_REJECTED) {
            closeAsUnprocessed();
        } else if (!requestSent || !responseReceived) {
            cancelImpl(new IOException("Stream %s reset by peer: %s"
                            .formatted(streamId(), resetReason)),
                    resetError);
        }
        if (debug.on()) {
            debug.log("stopping scheduler for stream %s", streamId());
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
        //
        try {
            processQuicData(reader, framesDecoder, frameOrderVerifier, readScheduler, debug);
        } catch (Throwable t) {
            if (debug.on())
                debug.log("processQuicData - Unexpected exception", t);
            if (!requestSent) {
                cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED);
            } else if (!responseReceived) {
                cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED);
            }
        } finally {
            if (debug.on())
                debug.log("processQuicData - leaving - eof: %s", framesDecoder.eof());
        }
    }

    void connectionError(Throwable throwable, long errorCode, String errMsg) {
        if (errorRef.compareAndSet(null, throwable)) {
            var streamid = streamId();
            if (debug.on()) {
                if (streamid == -1) {
                    debug.log("cancelling stream due to connection error", throwable);
                } else {
                    debug.log("cancelling stream " + streamid + " due to connection error", throwable);
                }
            }
            if (Log.trace()) {
                if (streamid == -1) {
                    Log.logTrace("connection error: {0}", errMsg);
                } else {
                    var format = "cancelling stream {0} due to connection error: {1}";
                    Log.logTrace(format, streamid, errMsg);
                }
            }
        }
        connection.connectionError(this, throwable, errorCode, errMsg);
    }

    record PushPromiseState(PushPromiseFrame frame,
                            HeaderFrameReader reader,
                            HttpHeadersBuilder headersBuilder,
                            DecodingCallback consumer) {}
    final ConcurrentHashMap<PushPromiseFrame, PushPromiseState> promiseMap = new ConcurrentHashMap<>();

    private void ignorePushPromiseData(PushPromiseFrame ppf, List<ByteBuffer> payload) {
        boolean completed = ppf.remaining() == 0;
        boolean eof = false;
        if (payload != null) {
            int last = payload.size() - 1;
            for (int i = 0; i <= last; i++) {
                ByteBuffer buf = payload.get(i);
                buf.limit(buf.position());
                if (buf == QuicStreamReader.EOF) {
                    eof = true;
                }
            }
        }
        if (!completed && eof) {
            cancelImpl(new EOFException("EOF reached promise: " + ppf),
                    Http3Error.H3_FRAME_ERROR);
        }
    }

    private boolean ignorePushPromiseFrame(PushPromiseFrame ppf, List<ByteBuffer> payload)
        throws IOException {
        long pushId = ppf.getPushId();
        long minPushId = connection.getMinPushId();
        if (exchange.pushGroup == null) {
            IOException checkFailed = connection.checkMaxPushId(pushId);
            if (checkFailed != null) {
                // connection is closed
                throw checkFailed;
            }
            if (!connection.acceptPromises()) {
                // if no stream accept promises, we can ignore the data and
                // cancel the promise right away.
                if (debug.on()) {
                    debug.log("ignoring PushPromiseFrame (no promise handler): %s%n", ppf);
                }
                ignorePushPromiseData(ppf, payload);
                if (pushId >= minPushId) {
                    connection.noPushHandlerFor(pushId);
                }
                return true;
            }
        }
        if (pushId < minPushId) {
            if (debug.on()) {
                debug.log("ignoring PushPromiseFrame (pushId=%s < %s): %s%n",
                        pushId, minPushId, ppf);
            }
            ignorePushPromiseData(ppf, payload);
            return true;
        }
        return false;
    }

    void receivePushPromiseFrame(PushPromiseFrame ppf, List<ByteBuffer> payload)
        throws IOException {
        var state = promiseMap.get(ppf);
        if (state == null) {
            if (ignorePushPromiseFrame(ppf, payload)) return;
            if (debug.on())
                debug.log("received PushPromiseFrame: " + ppf);
            var checkFailed = connection.checkMaxPushId(ppf.getPushId());
            if (checkFailed != null) throw checkFailed;
            var builder = new HttpHeadersBuilder();
            var consumer = new PushHeadersConsumer();
            var reader = qpackDecoder.newHeaderFrameReader(consumer);
            state = new PushPromiseState(ppf, reader, builder, consumer);
            consumer.setState(state);
            promiseMap.put(ppf, state);
        }
        if (debug.on())
            debug.log("receive promise headers: buffer list: " + payload);
        HeaderFrameReader headerFrameReader = state.reader();
        boolean completed = ppf.remaining() == 0;
        boolean eof = false;
        if (payload != null) {
            int last = payload.size() - 1;
            for (int i = 0; i <= last; i++) {
                ByteBuffer buf = payload.get(i);
                boolean endOfHeaders = completed && i == last;
                if (debug.on())
                    debug.log("QPack decoding %s bytes from headers (last: %s)",
                            buf.remaining(), last);
                qpackDecoder.decodeHeader(buf,
                        endOfHeaders,
                        headerFrameReader);
                if (buf == QuicStreamReader.EOF) {
                    eof = true;
                }
            }
        }
        if (!completed && eof) {
            cancelImpl(new EOFException("EOF reached promise: " + ppf),
                    Http3Error.H3_FRAME_ERROR);
        }
    }

    /**
     * This method is called by the {@link Http3PushManager} in order to
     * invoke the {@link Acceptor} that will accept the push
     * promise. This method gets the acceptor, invokes its {@link
     * Acceptor#accepted()} method, and if {@code true}, returns the
     * {@code Acceptor}.
     * <p>
     * If the push request is not accepted this method returns {@code null}.
     *
     * @apiNote
     * This method is called upon reception of a {@link PushPromiseFrame}.
     * The quic stream that will carry the body may not be available yet.
     *
     * @param pushId        the pushId
     * @param pushRequest   the promised push request
     * @return an {@link Acceptor} to get the body handler for the
     *         push request, or {@code null}.
     */
    Acceptor<T> acceptPushPromise(long pushId, HttpRequestImpl pushRequest) {
        if (Log.requests()) {
            Log.logRequest("PUSH_PROMISE: " + pushRequest.toString());
        }
        PushGroup<T> pushGroup = exchange.getPushGroup();
        if (pushGroup == null || exchange.multi.requestCancelled()) {
            if (Log.trace()) {
                Log.logTrace("Rejecting push promise pushId: " + pushId);
            }
            connection.pushCancelled(pushId, null);
            return null;
        }

        Acceptor<T> acceptor = null;
        boolean accepted = false;
        try {
            acceptor = pushGroup.acceptPushRequest(pushRequest, connection.newPushId(pushId));
            accepted = acceptor.accepted();
        } catch (Throwable t) {
            if (debug.on())
                debug.log("PushPromiseHandler::applyPushPromise threw exception %s",
                        (Object)t);
        }
        if (!accepted) {
            // cancel / reject
            if (Log.trace()) {
                Log.logTrace("No body subscriber for {0}: {1}", pushRequest,
                        "Push " + pushId  + " cancelled by users handler");
            }
            connection.pushCancelled(pushId, null);
            return null;
        }

        assert accepted && acceptor != null;
        return acceptor;
    }

    /**
     * This method is called by the {@link Http3PushManager} once the {@link Acceptor#cf()
     * responseCF} has been obtained from the acceptor.
     * @param pushId        the pushId
     * @param responseCF    the response completable future
     */
    void onPushRequestAccepted(long pushId, CompletableFuture<HttpResponse<T>> responseCF) {
        PushGroup<T> pushGroup = getExchange().getPushGroup();
        assert pushGroup != null;
        // setup housekeeping for when the push is received
        // TODO: deal with ignoring of CF anti-pattern
        CompletableFuture<HttpResponse<T>> cf = responseCF;
        cf.whenComplete((HttpResponse<T> resp, Throwable t) -> {
            t = Utils.getCompletionCause(t);
            if (Log.trace()) {
                Log.logTrace("Push {0} completed for {1}{2}", pushId, resp,
                        ((t==null) ? "": " with exception " + t));
            }
            if (t != null) {
                if (debug.on()) {
                    debug.log("completing pushResponseCF for"
                            + ", pushId=" + pushId + " with: " + t);
                }
                pushGroup.pushError(t);
            } else {
                if (debug.on()) {
                    debug.log("completing pushResponseCF for"
                            + ", pushId=" + pushId + " with: " + resp);
                }
            }
            pushGroup.pushCompleted();
        });
    }

    /**
     * This method is called by the {@link Http3PushPromiseStream} when
     * starting
     * @param pushRequest   the pushRequest
     * @param pushStream    the pushStream
     */
    void onHttp3PushStreamStarted(HttpRequestImpl pushRequest,
                                  Http3PushPromiseStream<T> pushStream) {
        PushGroup<T> pushGroup = getExchange().getPushGroup();
        assert pushGroup != null;
        assert pushStream != null;
        connection.onPushPromiseStreamStarted(pushStream, pushStream.streamId());
    }

    // invoked when ByteBuffers containing the next payload bytes for the
    // given partial header frame are received
    void receiveHeaders(HeadersFrame headers, List<ByteBuffer> payload) {
        if (debug.on())
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
                        headerFrameReader);
                if (buf == QuicStreamReader.EOF) {
                    eof = true;
                    // we are at EOF - no need to pause reading
                    switchReadingPaused(false);
                }
            }
        }
        if (!completed && eof) {
            cancelImpl(new EOFException("EOF reached: " + headers),
                    Http3Error.H3_FRAME_ERROR);
        }
    }


    // Invoked when data can be pushed to the quic stream;
    // Headers may block the stream - but they will be buffered in the stream
    // so should not cause this method to be called.
    // We should reach here only when sending body bytes.
    private void sendQuicData() {
        // This method is invoked when the sending part of the
        // stream is unblocked.
        if (!requestBodyCF.isDone()) {
            if (!exchange.multi.requestCancelled()) {
                var requestSubscriber = this.requestSubscriber;
                // the requestSubscriber will request more data from
                // upstream if needed
                if (requestSubscriber != null) requestSubscriber.unblock();
            }
        }
    }

    // pushes entire response body into response subscriber
    // blocking when required by local or remote flow control
    CompletableFuture<T> receiveResponseBody(BodySubscriber<T> bodySubscriber, Executor executor) {
        // ensure that the body subscriber will be subscribed and onError() is
        // invoked
        pendingResponseSubscriber = bodySubscriber;

        // We want to allow the subscriber's getBody() method to block, so it
        // can work with InputStreams. So, we offload execution.
        responseBodyCF = ResponseSubscribers.getBodyAsync(executor, bodySubscriber,
                new MinimalFuture<>(), (t) -> this.cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED));

        if (isCanceled()) {
            Throwable t = getCancelCause();
            responseBodyCF.completeExceptionally(t);
        }

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
    // the body subscriber. It is called from the processQuicData
    // loop. However, we cannot call onNext() if we have no demands.
    // So we're using a responseData queue to buffer incoming data.
     void pushResponseData(ConcurrentLinkedQueue<List<ByteBuffer>> responseData) {
        if (debug.on()) debug.log("pushResponseData");
        HttpResponse.BodySubscriber<T> subscriber = responseSubscriber;
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
            while (!responseData.isEmpty() && errorRef.get() == null) {
                List<ByteBuffer> data = responseData.peek();
                List<ByteBuffer> dsts = Collections.unmodifiableList(data);
                long size = Utils.remaining(dsts, Long.MAX_VALUE);
                boolean finished = dsts.contains(QuicStreamReader.EOF);
                if (size == 0 && finished) {
                    responseData.remove();
                    if (Log.trace()) {
                        Log.logTrace("responseSubscriber.onComplete");
                    }
                    if (debug.on()) debug.log("pushResponseData: onComplete");
                    done = true;
                    subscriber.onComplete();
                    responseReceived();
                    return;
                } else if (userSubscription.tryDecrement()) {
                    responseData.remove();
                    if (Log.trace()) {
                        Log.logTrace("responseSubscriber.onNext {0}", size);
                    }
                    if (debug.on()) debug.log("pushResponseData: onNext(%d)", size);
                    subscriber.onNext(dsts);
                } else {
                    if (stopRequested) break;
                    if (debug.on()) debug.log("no demand");
                    return;
                }
            }
            if (framesDecoder.eof() && responseData.isEmpty()) {
                if (debug.on()) debug.log("pushResponseData: EOF");
                if (Log.trace()) {
                    Log.logTrace("responseSubscriber.onComplete");
                }
                if (debug.on()) debug.log("pushResponseData: onComplete");
                done = true;
                subscriber.onComplete();
                responseReceived();
                return;
            }
        } catch (Throwable throwable) {
            if (debug.on()) debug.log("pushResponseData: unexpected exception", throwable);
            errorRef.compareAndSet(null, throwable);
        } finally {
            if (done) responseData.clear();
        }

        Throwable t = errorRef.get();
        if (t != null) {
            try {
                if (debug.on())
                    debug.log("calling subscriber.onError: %s", (Object) t);
                subscriber.onError(t);
            } catch (Throwable x) {
                Log.logError("Subscriber::onError threw exception: {0}", x);
            } finally {
                cancelImpl(t, Http3Error.H3_REQUEST_CANCELLED);
                responseData.clear();
            }
        }
    }

    // This method is called by Http2Connection::decrementStreamCount in order
    // to make sure that the stream count is decremented only once for
    // a given stream.
    boolean deRegister() {
        return DEREGISTERED.compareAndSet(this, false, true);
    }

    private static final VarHandle DEREGISTERED;
    static {
        try {
            DEREGISTERED = MethodHandles.lookup()
                    .findVarHandle(Http3ExchangeImpl.class, "deRegistered", boolean.class);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }

}
