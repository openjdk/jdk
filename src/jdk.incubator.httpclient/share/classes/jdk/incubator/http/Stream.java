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

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;
import jdk.incubator.http.HttpResponse.BodySubscriber;
import jdk.incubator.http.internal.common.*;
import jdk.incubator.http.internal.frame.*;
import jdk.incubator.http.internal.hpack.DecodingCallback;

/**
 * Http/2 Stream handling.
 *
 * REQUESTS
 *
 * sendHeadersOnly() -- assembles HEADERS frame and puts on connection outbound Q
 *
 * sendRequest() -- sendHeadersOnly() + sendBody()
 *
 * sendBodyAsync() -- calls sendBody() in an executor thread.
 *
 * sendHeadersAsync() -- calls sendHeadersOnly() which does not block
 *
 * sendRequestAsync() -- calls sendRequest() in an executor thread
 *
 * RESPONSES
 *
 * Multiple responses can be received per request. Responses are queued up on
 * a LinkedList of CF<HttpResponse> and the the first one on the list is completed
 * with the next response
 *
 * getResponseAsync() -- queries list of response CFs and returns first one
 *               if one exists. Otherwise, creates one and adds it to list
 *               and returns it. Completion is achieved through the
 *               incoming() upcall from connection reader thread.
 *
 * getResponse() -- calls getResponseAsync() and waits for CF to complete
 *
 * responseBodyAsync() -- calls responseBody() in an executor thread.
 *
 * incoming() -- entry point called from connection reader thread. Frames are
 *               either handled immediately without blocking or for data frames
 *               placed on the stream's inputQ which is consumed by the stream's
 *               reader thread.
 *
 * PushedStream sub class
 * ======================
 * Sending side methods are not used because the request comes from a PUSH_PROMISE
 * frame sent by the server. When a PUSH_PROMISE is received the PushedStream
 * is created. PushedStream does not use responseCF list as there can be only
 * one response. The CF is created when the object created and when the response
 * HEADERS frame is received the object is completed.
 */
class Stream<T> extends ExchangeImpl<T> {

    final static boolean DEBUG = Utils.DEBUG; // Revisit: temporary developer's flag
    final System.Logger  debug = Utils.getDebugLogger(this::dbgString, DEBUG);

    final ConcurrentLinkedQueue<Http2Frame> inputQ = new ConcurrentLinkedQueue<>();
    final SequentialScheduler sched =
            SequentialScheduler.synchronizedScheduler(this::schedule);
    final SubscriptionBase userSubscription = new SubscriptionBase(sched, this::cancel);

    /**
     * This stream's identifier. Assigned lazily by the HTTP2Connection before
     * the stream's first frame is sent.
     */
    protected volatile int streamid;

    long requestContentLen;

    final Http2Connection connection;
    final HttpRequestImpl request;
    final DecodingCallback rspHeadersConsumer;
    HttpHeadersImpl responseHeaders;
    final HttpHeadersImpl requestPseudoHeaders;
    volatile HttpResponse.BodySubscriber<T> responseSubscriber;
    final HttpRequest.BodyPublisher requestPublisher;
    volatile RequestSubscriber requestSubscriber;
    volatile int responseCode;
    volatile Response response;
    volatile Throwable failed; // The exception with which this stream was canceled.
    final CompletableFuture<Void> requestBodyCF = new MinimalFuture<>();
    volatile CompletableFuture<T> responseBodyCF;

    /** True if END_STREAM has been seen in a frame received on this stream. */
    private volatile boolean remotelyClosed;
    private volatile boolean closed;
    private volatile boolean endStreamSent;

    // state flags
    private boolean requestSent, responseReceived;

    /**
     * A reference to this Stream's connection Send Window controller. The
     * stream MUST acquire the appropriate amount of Send Window before
     * sending any data. Will be null for PushStreams, as they cannot send data.
     */
    private final WindowController windowController;
    private final WindowUpdateSender windowUpdater;

    @Override
    HttpConnection connection() {
        return connection.connection;
    }

    /**
     * Invoked either from incoming() -> {receiveDataFrame() or receiveResetFrame() }
     * of after user subscription window has re-opened, from SubscriptionBase.request()
     */
    private void schedule() {
        if (responseSubscriber == null)
            // can't process anything yet
            return;

        while (!inputQ.isEmpty()) {
            Http2Frame frame  = inputQ.peek();
            if (frame instanceof ResetFrame) {
                inputQ.remove();
                handleReset((ResetFrame)frame);
                return;
            }
            DataFrame df = (DataFrame)frame;
            boolean finished = df.getFlag(DataFrame.END_STREAM);

            List<ByteBuffer> buffers = df.getData();
            List<ByteBuffer> dsts = Collections.unmodifiableList(buffers);
            int size = Utils.remaining(dsts, Integer.MAX_VALUE);
            if (size == 0 && finished) {
                inputQ.remove();
                Log.logTrace("responseSubscriber.onComplete");
                debug.log(Level.DEBUG, "incoming: onComplete");
                sched.stop();
                responseSubscriber.onComplete();
                setEndStreamReceived();
                return;
            } else if (userSubscription.tryDecrement()) {
                inputQ.remove();
                Log.logTrace("responseSubscriber.onNext {0}", size);
                debug.log(Level.DEBUG, "incoming: onNext(%d)", size);
                responseSubscriber.onNext(dsts);
                if (consumed(df)) {
                    Log.logTrace("responseSubscriber.onComplete");
                    debug.log(Level.DEBUG, "incoming: onComplete");
                    sched.stop();
                    responseSubscriber.onComplete();
                    setEndStreamReceived();
                    return;
                }
            } else {
                return;
            }
        }
        Throwable t = failed;
        if (t != null) {
            sched.stop();
            responseSubscriber.onError(t);
            close();
        }
    }

    // Callback invoked after the Response BodySubscriber has consumed the
    // buffers contained in a DataFrame.
    // Returns true if END_STREAM is reached, false otherwise.
    private boolean consumed(DataFrame df) {
        // RFC 7540 6.1:
        // The entire DATA frame payload is included in flow control,
        // including the Pad Length and Padding fields if present
        int len = df.payloadLength();
        connection.windowUpdater.update(len);

        if (!df.getFlag(DataFrame.END_STREAM)) {
            // Don't send window update on a stream which is
            // closed or half closed.
            windowUpdater.update(len);
            return false; // more data coming
        }
        return true; // end of stream
    }

    @Override
    CompletableFuture<T> readBodyAsync(HttpResponse.BodyHandler<T> handler,
                                       boolean returnConnectionToPool,
                                       Executor executor)
    {
        Log.logTrace("Reading body on stream {0}", streamid);
        BodySubscriber<T> bodySubscriber = handler.apply(responseCode, responseHeaders);
        CompletableFuture<T> cf = receiveData(bodySubscriber);

        PushGroup<?,?> pg = exchange.getPushGroup();
        if (pg != null) {
            // if an error occurs make sure it is recorded in the PushGroup
            cf = cf.whenComplete((t,e) -> pg.pushError(e));
        }
        return cf;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("streamid: ")
                .append(streamid);
        return sb.toString();
    }

    private void receiveDataFrame(DataFrame df) {
        inputQ.add(df);
        sched.runOrSchedule();
    }

    /** Handles a RESET frame. RESET is always handled inline in the queue. */
    private void receiveResetFrame(ResetFrame frame) {
        inputQ.add(frame);
        sched.runOrSchedule();
    }

    // pushes entire response body into response subscriber
    // blocking when required by local or remote flow control
    CompletableFuture<T> receiveData(BodySubscriber<T> bodySubscriber) {
        responseBodyCF = MinimalFuture.of(bodySubscriber.getBody());

        if (isCanceled()) {
            Throwable t = getCancelCause();
            responseBodyCF.completeExceptionally(t);
        } else {
            bodySubscriber.onSubscribe(userSubscription);
        }
        // Set the responseSubscriber field now that onSubscribe has been called.
        // This effectively allows the scheduler to start invoking the callbacks.
        responseSubscriber = bodySubscriber;
        sched.runOrSchedule(); // in case data waiting already to be processed
        return responseBodyCF;
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendBodyAsync() {
        return sendBodyImpl().thenApply( v -> this);
    }

    @SuppressWarnings("unchecked")
    Stream(Http2Connection connection,
           Exchange<T> e,
           WindowController windowController)
    {
        super(e);
        this.connection = connection;
        this.windowController = windowController;
        this.request = e.request();
        this.requestPublisher = request.requestPublisher;  // may be null
        responseHeaders = new HttpHeadersImpl();
        rspHeadersConsumer = (name, value) -> {
            responseHeaders.addHeader(name.toString(), value.toString());
            if (Log.headers() && Log.trace()) {
                Log.logTrace("RECEIVED HEADER (streamid={0}): {1}: {2}",
                             streamid, name, value);
            }
        };
        this.requestPseudoHeaders = new HttpHeadersImpl();
        // NEW
        this.windowUpdater = new StreamWindowUpdateSender(connection);
    }

    /**
     * Entry point from Http2Connection reader thread.
     *
     * Data frames will be removed by response body thread.
     */
    void incoming(Http2Frame frame) throws IOException {
        debug.log(Level.DEBUG, "incoming: %s", frame);
        if ((frame instanceof HeaderFrame)) {
            HeaderFrame hframe = (HeaderFrame)frame;
            if (hframe.endHeaders()) {
                Log.logTrace("handling response (streamid={0})", streamid);
                handleResponse();
                if (hframe.getFlag(HeaderFrame.END_STREAM)) {
                    receiveDataFrame(new DataFrame(streamid, DataFrame.END_STREAM, List.of()));
                }
            }
        } else if (frame instanceof DataFrame) {
            receiveDataFrame((DataFrame)frame);
        } else {
            otherFrame(frame);
        }
    }

    void otherFrame(Http2Frame frame) throws IOException {
        switch (frame.type()) {
            case WindowUpdateFrame.TYPE:
                incoming_windowUpdate((WindowUpdateFrame) frame);
                break;
            case ResetFrame.TYPE:
                incoming_reset((ResetFrame) frame);
                break;
            case PriorityFrame.TYPE:
                incoming_priority((PriorityFrame) frame);
                break;
            default:
                String msg = "Unexpected frame: " + frame.toString();
                throw new IOException(msg);
        }
    }

    // The Hpack decoder decodes into one of these consumers of name,value pairs

    DecodingCallback rspHeadersConsumer() {
        return rspHeadersConsumer;
    }

    protected void handleResponse() throws IOException {
        responseCode = (int)responseHeaders
                .firstValueAsLong(":status")
                .orElseThrow(() -> new IOException("no statuscode in response"));

        response = new Response(
                request, exchange, responseHeaders,
                responseCode, HttpClient.Version.HTTP_2);

        /* TODO: review if needs to be removed
           the value is not used, but in case `content-length` doesn't parse as
           long, there will be NumberFormatException. If left as is, make sure
           code up the stack handles NFE correctly. */
        responseHeaders.firstValueAsLong("content-length");

        if (Log.headers()) {
            StringBuilder sb = new StringBuilder("RESPONSE HEADERS:\n");
            Log.dumpHeaders(sb, "    ", responseHeaders);
            Log.logHeaders(sb.toString());
        }

        completeResponse(response);
    }

    void incoming_reset(ResetFrame frame) {
        Log.logTrace("Received RST_STREAM on stream {0}", streamid);
        if (endStreamReceived()) {
            Log.logTrace("Ignoring RST_STREAM frame received on remotely closed stream {0}", streamid);
        } else if (closed) {
            Log.logTrace("Ignoring RST_STREAM frame received on closed stream {0}", streamid);
        } else {
            // put it in the input queue in order to read all
            // pending data frames first. Indeed, a server may send
            // RST_STREAM after sending END_STREAM, in which case we should
            // ignore it. However, we won't know if we have received END_STREAM
            // or not until all pending data frames are read.
            receiveResetFrame(frame);
            // RST_STREAM was pushed to the queue. It will be handled by
            // asyncReceive after all pending data frames have been
            // processed.
            Log.logTrace("RST_STREAM pushed in queue for stream {0}", streamid);
        }
    }

    void handleReset(ResetFrame frame) {
        Log.logTrace("Handling RST_STREAM on stream {0}", streamid);
        if (!closed) {
            close();
            int error = frame.getErrorCode();
            completeResponseExceptionally(new IOException(ErrorFrame.stringForCode(error)));
        } else {
            Log.logTrace("Ignoring RST_STREAM frame received on closed stream {0}", streamid);
        }
    }

    void incoming_priority(PriorityFrame frame) {
        // TODO: implement priority
        throw new UnsupportedOperationException("Not implemented");
    }

    private void incoming_windowUpdate(WindowUpdateFrame frame)
        throws IOException
    {
        int amount = frame.getUpdate();
        if (amount <= 0) {
            Log.logTrace("Resetting stream: {0} %d, Window Update amount: %d\n",
                         streamid, streamid, amount);
            connection.resetStream(streamid, ResetFrame.FLOW_CONTROL_ERROR);
        } else {
            assert streamid != 0;
            boolean success = windowController.increaseStreamWindow(amount, streamid);
            if (!success) {  // overflow
                connection.resetStream(streamid, ResetFrame.FLOW_CONTROL_ERROR);
            }
        }
    }

    void incoming_pushPromise(HttpRequestImpl pushReq,
                              PushedStream<?,T> pushStream)
        throws IOException
    {
        if (Log.requests()) {
            Log.logRequest("PUSH_PROMISE: " + pushReq.toString());
        }
        PushGroup<?,T> pushGroup = exchange.getPushGroup();
        if (pushGroup == null) {
            Log.logTrace("Rejecting push promise stream " + streamid);
            connection.resetStream(pushStream.streamid, ResetFrame.REFUSED_STREAM);
            pushStream.close();
            return;
        }

        HttpResponse.MultiSubscriber<?,T> proc = pushGroup.subscriber();

        CompletableFuture<HttpResponse<T>> cf = pushStream.responseCF();

        Optional<HttpResponse.BodyHandler<T>> bpOpt =
                pushGroup.handlerForPushRequest(pushReq);

        if (!bpOpt.isPresent()) {
            IOException ex = new IOException("Stream "
                 + streamid + " cancelled by user");
            if (Log.trace()) {
                Log.logTrace("No body subscriber for {0}: {1}", pushReq,
                            ex.getMessage());
            }
            pushStream.cancelImpl(ex);
            cf.completeExceptionally(ex);
            return;
        }

        pushGroup.addPush();
        pushStream.requestSent();
        pushStream.setPushHandler(bpOpt.get());
        // setup housekeeping for when the push is received
        // TODO: deal with ignoring of CF anti-pattern
        cf.whenComplete((HttpResponse<T> resp, Throwable t) -> {
            t = Utils.getCompletionCause(t);
            if (Log.trace()) {
                Log.logTrace("Push completed on stream {0} for {1}{2}",
                             pushStream.streamid, resp,
                             ((t==null) ? "": " with exception " + t));
            }
            if (t != null) {
                pushGroup.pushError(t);
                proc.onError(pushReq, t);
            } else {
                proc.onResponse(resp);
            }
            pushGroup.pushCompleted();
        });

    }

    private OutgoingHeaders<Stream<T>> headerFrame(long contentLength) {
        HttpHeadersImpl h = request.getSystemHeaders();
        if (contentLength > 0) {
            h.setHeader("content-length", Long.toString(contentLength));
        }
        setPseudoHeaderFields();
        OutgoingHeaders<Stream<T>> f = new OutgoingHeaders<>(h, request.getUserHeaders(), this);
        if (contentLength == 0) {
            f.setFlag(HeadersFrame.END_STREAM);
            endStreamSent = true;
        }
        return f;
    }

    private void setPseudoHeaderFields() {
        HttpHeadersImpl hdrs = requestPseudoHeaders;
        String method = request.method();
        hdrs.setHeader(":method", method);
        URI uri = request.uri();
        hdrs.setHeader(":scheme", uri.getScheme());
        // TODO: userinfo deprecated. Needs to be removed
        hdrs.setHeader(":authority", uri.getAuthority());
        // TODO: ensure header names beginning with : not in user headers
        String query = uri.getQuery();
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            if (method.equalsIgnoreCase("OPTIONS")) {
                path = "*";
            } else {
                path = "/";
            }
        }
        if (query != null) {
            path += "?" + query;
        }
        hdrs.setHeader(":path", path);
    }

    HttpHeadersImpl getRequestPseudoHeaders() {
        return requestPseudoHeaders;
    }

    /** Sets endStreamReceived. Should be called only once. */
    void setEndStreamReceived() {
        assert remotelyClosed == false: "Unexpected endStream already set";
        remotelyClosed = true;
        responseReceived();
    }

    /** Tells whether, or not, the END_STREAM Flag has been seen in any frame
     *  received on this stream. */
    private boolean endStreamReceived() {
        return remotelyClosed;
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendHeadersAsync() {
        debug.log(Level.DEBUG, "sendHeadersOnly()");
        if (Log.requests() && request != null) {
            Log.logRequest(request.toString());
        }
        if (requestPublisher != null) {
            requestContentLen = requestPublisher.contentLength();
        } else {
            requestContentLen = 0;
        }
        OutgoingHeaders<Stream<T>> f = headerFrame(requestContentLen);
        connection.sendFrame(f);
        CompletableFuture<ExchangeImpl<T>> cf = new MinimalFuture<>();
        cf.complete(this);  // #### good enough for now
        return cf;
    }

    @Override
    void released() {
        if (streamid > 0) {
            debug.log(Level.DEBUG, "Released stream %d", streamid);
            // remove this stream from the Http2Connection map.
            connection.closeStream(streamid);
        } else {
            debug.log(Level.DEBUG, "Can't release stream %d", streamid);
        }
    }

    @Override
    void completed() {
        // There should be nothing to do here: the stream should have
        // been already closed (or will be closed shortly after).
    }

    void registerStream(int id) {
        this.streamid = id;
        connection.putStream(this, streamid);
        debug.log(Level.DEBUG, "Registered stream %d", id);
    }

    void signalWindowUpdate() {
        RequestSubscriber subscriber = requestSubscriber;
        assert subscriber != null;
        debug.log(Level.DEBUG, "Signalling window update");
        subscriber.sendScheduler.runOrSchedule();
    }

    static final ByteBuffer COMPLETED = ByteBuffer.allocate(0);
    class RequestSubscriber implements Flow.Subscriber<ByteBuffer> {
        // can be < 0 if the actual length is not known.
        private final long contentLength;
        private volatile long remainingContentLength;
        private volatile Subscription subscription;

        // Holds the outgoing data. There will be at most 2 outgoing ByteBuffers.
        //  1) The data that was published by the request body Publisher, and
        //  2) the COMPLETED sentinel, since onComplete can be invoked without demand.
        final ConcurrentLinkedDeque<ByteBuffer> outgoing = new ConcurrentLinkedDeque<>();

        private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        // A scheduler used to honor window updates. Writing must be paused
        // when the window is exhausted, and resumed when the window acquires
        // some space. The sendScheduler makes it possible to implement this
        // behaviour in an asynchronous non-blocking way.
        // See RequestSubscriber::trySend below.
        final SequentialScheduler sendScheduler;

        RequestSubscriber(long contentLen) {
            this.contentLength = contentLen;
            this.remainingContentLength = contentLen;
            this.sendScheduler =
                    SequentialScheduler.synchronizedScheduler(this::trySend);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                throw new IllegalStateException("already subscribed");
            }
            this.subscription = subscription;
            debug.log(Level.DEBUG, "RequestSubscriber: onSubscribe, request 1");
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            debug.log(Level.DEBUG, "RequestSubscriber: onNext(%d)", item.remaining());
            int size = outgoing.size();
            assert size == 0 : "non-zero size: " + size;
            onNextImpl(item);
        }

        private void onNextImpl(ByteBuffer item) {
            // Got some more request body bytes to send.
            if (requestBodyCF.isDone()) {
                // stream already cancelled, probably in timeout
                sendScheduler.stop();
                subscription.cancel();
                return;
            }
            outgoing.add(item);
            sendScheduler.runOrSchedule();
        }

        @Override
        public void onError(Throwable throwable) {
            debug.log(Level.DEBUG, () -> "RequestSubscriber: onError: " + throwable);
            // ensure that errors are handled within the flow.
            if (errorRef.compareAndSet(null, throwable)) {
                sendScheduler.runOrSchedule();
            }
        }

        @Override
        public void onComplete() {
            debug.log(Level.DEBUG, "RequestSubscriber: onComplete");
            int size = outgoing.size();
            assert size == 0 || size == 1 : "non-zero or one size: " + size;
            // last byte of request body has been obtained.
            // ensure that everything is completed within the flow.
            onNextImpl(COMPLETED);
        }

        // Attempts to send the data, if any.
        // Handles errors and completion state.
        // Pause writing if the send window is exhausted, resume it if the
        // send window has some bytes that can be acquired.
        void trySend() {
            try {
                // handle errors raised by onError;
                Throwable t = errorRef.get();
                if (t != null) {
                    sendScheduler.stop();
                    if (requestBodyCF.isDone()) return;
                    subscription.cancel();
                    requestBodyCF.completeExceptionally(t);
                    return;
                }

                do {
                    // handle COMPLETED;
                    ByteBuffer item = outgoing.peekFirst();
                    if (item == null) return;
                    else if (item == COMPLETED) {
                        sendScheduler.stop();
                        complete();
                        return;
                    }

                    // handle bytes to send downstream
                    while (item.hasRemaining()) {
                        debug.log(Level.DEBUG, "trySend: %d", item.remaining());
                        assert !endStreamSent : "internal error, send data after END_STREAM flag";
                        DataFrame df = getDataFrame(item);
                        if (df == null) {
                            debug.log(Level.DEBUG, "trySend: can't send yet: %d",
                                    item.remaining());
                            return; // the send window is exhausted: come back later
                        }

                        if (contentLength > 0) {
                            remainingContentLength -= df.getDataLength();
                            if (remainingContentLength < 0) {
                                String msg = connection().getConnectionFlow()
                                        + " stream=" + streamid + " "
                                        + "[" + Thread.currentThread().getName() + "] "
                                        + "Too many bytes in request body. Expected: "
                                        + contentLength + ", got: "
                                        + (contentLength - remainingContentLength);
                                connection.resetStream(streamid, ResetFrame.PROTOCOL_ERROR);
                                throw new IOException(msg);
                            } else if (remainingContentLength == 0) {
                                df.setFlag(DataFrame.END_STREAM);
                                endStreamSent = true;
                            }
                        }
                        debug.log(Level.DEBUG, "trySend: sending: %d", df.getDataLength());
                        connection.sendDataFrame(df);
                    }
                    assert !item.hasRemaining();
                    ByteBuffer b = outgoing.removeFirst();
                    assert b == item;
                } while (outgoing.peekFirst() != null);

                debug.log(Level.DEBUG, "trySend: request 1");
                subscription.request(1);
            } catch (Throwable ex) {
                debug.log(Level.DEBUG, "trySend: ", ex);
                sendScheduler.stop();
                subscription.cancel();
                requestBodyCF.completeExceptionally(ex);
            }
        }

        private void complete() throws IOException {
            long remaining = remainingContentLength;
            long written = contentLength - remaining;
            if (remaining > 0) {
                connection.resetStream(streamid, ResetFrame.PROTOCOL_ERROR);
                // let trySend() handle the exception
                throw new IOException(connection().getConnectionFlow()
                                     + " stream=" + streamid + " "
                                     + "[" + Thread.currentThread().getName() +"] "
                                     + "Too few bytes returned by the publisher ("
                                              + written + "/"
                                              + contentLength + ")");
            }
            if (!endStreamSent) {
                endStreamSent = true;
                connection.sendDataFrame(getEmptyEndStreamDataFrame());
            }
            requestBodyCF.complete(null);
        }
    }

    /**
     * Send a RESET frame to tell server to stop sending data on this stream
     */
    @Override
    public CompletableFuture<Void> ignoreBody() {
        try {
            connection.resetStream(streamid, ResetFrame.STREAM_CLOSED);
            return MinimalFuture.completedFuture(null);
        } catch (Throwable e) {
            Log.logTrace("Error resetting stream {0}", e.toString());
            return MinimalFuture.failedFuture(e);
        }
    }

    DataFrame getDataFrame(ByteBuffer buffer) {
        int requestAmount = Math.min(connection.getMaxSendFrameSize(), buffer.remaining());
        // blocks waiting for stream send window, if exhausted
        int actualAmount = windowController.tryAcquire(requestAmount, streamid, this);
        if (actualAmount <= 0) return null;
        ByteBuffer outBuf = Utils.sliceWithLimitedCapacity(buffer,  actualAmount);
        DataFrame df = new DataFrame(streamid, 0 , outBuf);
        return df;
    }

    private DataFrame getEmptyEndStreamDataFrame()  {
        return new DataFrame(streamid, DataFrame.END_STREAM, List.of());
    }

    /**
     * A List of responses relating to this stream. Normally there is only
     * one response, but intermediate responses like 100 are allowed
     * and must be passed up to higher level before continuing. Deals with races
     * such as if responses are returned before the CFs get created by
     * getResponseAsync()
     */

    final List<CompletableFuture<Response>> response_cfs = new ArrayList<>(5);

    @Override
    CompletableFuture<Response> getResponseAsync(Executor executor) {
        CompletableFuture<Response> cf;
        // The code below deals with race condition that can be caused when
        // completeResponse() is being called before getResponseAsync()
        synchronized (response_cfs) {
            if (!response_cfs.isEmpty()) {
                // This CompletableFuture was created by completeResponse().
                // it will be already completed.
                cf = response_cfs.remove(0);
                // if we find a cf here it should be already completed.
                // finding a non completed cf should not happen. just assert it.
                assert cf.isDone() : "Removing uncompleted response: could cause code to hang!";
            } else {
                // getResponseAsync() is called first. Create a CompletableFuture
                // that will be completed by completeResponse() when
                // completeResponse() is called.
                cf = new MinimalFuture<>();
                response_cfs.add(cf);
            }
        }
        if (executor != null && !cf.isDone()) {
            // protect from executing later chain of CompletableFuture operations from SelectorManager thread
            cf = cf.thenApplyAsync(r -> r, executor);
        }
        Log.logTrace("Response future (stream={0}) is: {1}", streamid, cf);
        PushGroup<?,?> pg = exchange.getPushGroup();
        if (pg != null) {
            // if an error occurs make sure it is recorded in the PushGroup
            cf = cf.whenComplete((t,e) -> pg.pushError(Utils.getCompletionCause(e)));
        }
        return cf;
    }

    /**
     * Completes the first uncompleted CF on list, and removes it. If there is no
     * uncompleted CF then creates one (completes it) and adds to list
     */
    void completeResponse(Response resp) {
        synchronized (response_cfs) {
            CompletableFuture<Response> cf;
            int cfs_len = response_cfs.size();
            for (int i=0; i<cfs_len; i++) {
                cf = response_cfs.get(i);
                if (!cf.isDone()) {
                    Log.logTrace("Completing response (streamid={0}): {1}",
                                 streamid, cf);
                    cf.complete(resp);
                    response_cfs.remove(cf);
                    return;
                } // else we found the previous response: just leave it alone.
            }
            cf = MinimalFuture.completedFuture(resp);
            Log.logTrace("Created completed future (streamid={0}): {1}",
                         streamid, cf);
            response_cfs.add(cf);
        }
    }

    // methods to update state and remove stream when finished

    synchronized void requestSent() {
        requestSent = true;
        if (responseReceived) {
            close();
        }
    }

    synchronized void responseReceived() {
        responseReceived = true;
        if (requestSent) {
            close();
        }
    }

    /**
     * same as above but for errors
     */
    void completeResponseExceptionally(Throwable t) {
        synchronized (response_cfs) {
            // use index to avoid ConcurrentModificationException
            // caused by removing the CF from within the loop.
            for (int i = 0; i < response_cfs.size(); i++) {
                CompletableFuture<Response> cf = response_cfs.get(i);
                if (!cf.isDone()) {
                    cf.completeExceptionally(t);
                    response_cfs.remove(i);
                    return;
                }
            }
            response_cfs.add(MinimalFuture.failedFuture(t));
        }
    }

    CompletableFuture<Void> sendBodyImpl() {
        requestBodyCF.whenComplete((v, t) -> requestSent());
        if (requestPublisher != null) {
            final RequestSubscriber subscriber = new RequestSubscriber(requestContentLen);
            requestPublisher.subscribe(requestSubscriber = subscriber);
        } else {
            // there is no request body, therefore the request is complete,
            // END_STREAM has already sent with outgoing headers
            requestBodyCF.complete(null);
        }
        return requestBodyCF;
    }

    @Override
    void cancel() {
        cancel(new IOException("Stream " + streamid + " cancelled"));
    }

    @Override
    void cancel(IOException cause) {
        cancelImpl(cause);
    }

    // This method sends a RST_STREAM frame
    void cancelImpl(Throwable e) {
        debug.log(Level.DEBUG, "cancelling stream {0}: {1}", streamid, e);
        if (Log.trace()) {
            Log.logTrace("cancelling stream {0}: {1}\n", streamid, e);
        }
        boolean closing;
        if (closing = !closed) { // assigning closing to !closed
            synchronized (this) {
                failed = e;
                if (closing = !closed) { // assigning closing to !closed
                    closed=true;
                }
            }
        }
        if (closing) { // true if the stream has not been closed yet
            if (responseSubscriber != null)
                sched.runOrSchedule();
        }
        completeResponseExceptionally(e);
        if (!requestBodyCF.isDone()) {
            requestBodyCF.completeExceptionally(e); // we may be sending the body..
        }
        if (responseBodyCF != null) {
            responseBodyCF.completeExceptionally(e);
        }
        try {
            // will send a RST_STREAM frame
            if (streamid != 0) {
                connection.resetStream(streamid, ResetFrame.CANCEL);
            }
        } catch (IOException ex) {
            Log.logError(ex);
        }
    }

    // This method doesn't send any frame
    void close() {
        if (closed) return;
        synchronized(this) {
            if (closed) return;
            closed = true;
        }
        Log.logTrace("Closing stream {0}", streamid);
        connection.closeStream(streamid);
        Log.logTrace("Stream {0} closed", streamid);
    }

    static class PushedStream<U,T> extends Stream<T> {
        final PushGroup<U,T> pushGroup;
        // push streams need the response CF allocated up front as it is
        // given directly to user via the multi handler callback function.
        final CompletableFuture<Response> pushCF;
        final CompletableFuture<HttpResponse<T>> responseCF;
        final HttpRequestImpl pushReq;
        HttpResponse.BodyHandler<T> pushHandler;

        PushedStream(PushGroup<U,T> pushGroup,
                     Http2Connection connection,
                     Exchange<T> pushReq) {
            // ## no request body possible, null window controller
            super(connection, pushReq, null);
            this.pushGroup = pushGroup;
            this.pushReq = pushReq.request();
            this.pushCF = new MinimalFuture<>();
            this.responseCF = new MinimalFuture<>();
        }

        CompletableFuture<HttpResponse<T>> responseCF() {
            return responseCF;
        }

        synchronized void setPushHandler(HttpResponse.BodyHandler<T> pushHandler) {
            this.pushHandler = pushHandler;
        }

        synchronized HttpResponse.BodyHandler<T> getPushHandler() {
            // ignored parameters to function can be used as BodyHandler
            return this.pushHandler;
        }

        // Following methods call the super class but in case of
        // error record it in the PushGroup. The error method is called
        // with a null value when no error occurred (is a no-op)
        @Override
        CompletableFuture<ExchangeImpl<T>> sendBodyAsync() {
            return super.sendBodyAsync()
                        .whenComplete((ExchangeImpl<T> v, Throwable t)
                                -> pushGroup.pushError(Utils.getCompletionCause(t)));
        }

        @Override
        CompletableFuture<ExchangeImpl<T>> sendHeadersAsync() {
            return super.sendHeadersAsync()
                        .whenComplete((ExchangeImpl<T> ex, Throwable t)
                                -> pushGroup.pushError(Utils.getCompletionCause(t)));
        }

        @Override
        CompletableFuture<Response> getResponseAsync(Executor executor) {
            CompletableFuture<Response> cf = pushCF.whenComplete(
                    (v, t) -> pushGroup.pushError(Utils.getCompletionCause(t)));
            if(executor!=null && !cf.isDone()) {
                cf  = cf.thenApplyAsync( r -> r, executor);
            }
            return cf;
        }

        @Override
        CompletableFuture<T> readBodyAsync(
                HttpResponse.BodyHandler<T> handler,
                boolean returnConnectionToPool,
                Executor executor)
        {
            return super.readBodyAsync(handler, returnConnectionToPool, executor)
                        .whenComplete((v, t) -> pushGroup.pushError(t));
        }

        @Override
        void completeResponse(Response r) {
            Log.logResponse(r::toString);
            pushCF.complete(r); // not strictly required for push API
            // start reading the body using the obtained BodySubscriber
            CompletableFuture<Void> start = new MinimalFuture<>();
            start.thenCompose( v -> readBodyAsync(getPushHandler(), false, getExchange().executor()))
                .whenComplete((T body, Throwable t) -> {
                    if (t != null) {
                        responseCF.completeExceptionally(t);
                    } else {
                        HttpResponseImpl<T> resp =
                                new HttpResponseImpl<>(r.request, r, null, body, getExchange());
                        responseCF.complete(resp);
                    }
                });
            start.completeAsync(() -> null, getExchange().executor());
        }

        @Override
        void completeResponseExceptionally(Throwable t) {
            pushCF.completeExceptionally(t);
        }

//        @Override
//        synchronized void responseReceived() {
//            super.responseReceived();
//        }

        // create and return the PushResponseImpl
        @Override
        protected void handleResponse() {
            responseCode = (int)responseHeaders
                .firstValueAsLong(":status")
                .orElse(-1);

            if (responseCode == -1) {
                completeResponseExceptionally(new IOException("No status code"));
            }

            this.response = new Response(
                pushReq, exchange, responseHeaders,
                responseCode, HttpClient.Version.HTTP_2);

            /* TODO: review if needs to be removed
               the value is not used, but in case `content-length` doesn't parse
               as long, there will be NumberFormatException. If left as is, make
               sure code up the stack handles NFE correctly. */
            responseHeaders.firstValueAsLong("content-length");

            if (Log.headers()) {
                StringBuilder sb = new StringBuilder("RESPONSE HEADERS");
                sb.append(" (streamid=").append(streamid).append("): ");
                Log.dumpHeaders(sb, "    ", responseHeaders);
                Log.logHeaders(sb.toString());
            }

            // different implementations for normal streams and pushed streams
            completeResponse(response);
        }
    }

    final class StreamWindowUpdateSender extends WindowUpdateSender {

        StreamWindowUpdateSender(Http2Connection connection) {
            super(connection);
        }

        @Override
        int getStreamId() {
            return streamid;
        }
    }

    /**
     * Returns true if this exchange was canceled.
     * @return true if this exchange was canceled.
     */
    synchronized boolean isCanceled() {
        return failed != null;
    }

    /**
     * Returns the cause for which this exchange was canceled, if available.
     * @return the cause for which this exchange was canceled, if available.
     */
    synchronized Throwable getCancelCause() {
        return failed;
    }

    final String dbgString() {
        return connection.dbgString() + "/Stream("+streamid+")";
    }
}
