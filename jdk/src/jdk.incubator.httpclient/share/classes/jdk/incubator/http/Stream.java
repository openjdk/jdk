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
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

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
 * sendBody() -- in calling thread: obeys all flow control (so may block)
 *               obtains data from request body processor and places on connection
 *               outbound Q.
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
 * responseBody() -- in calling thread: blocks for incoming DATA frames on
 *               stream inputQ. Obeys remote and local flow control so may block.
 *               Calls user response body processor with data buffers.
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

    final AsyncDataReadQueue inputQ = new AsyncDataReadQueue();

    /**
     * This stream's identifier. Assigned lazily by the HTTP2Connection before
     * the stream's first frame is sent.
     */
    protected volatile int streamid;

    long responseContentLen = -1;
    long responseBytesProcessed = 0;
    long requestContentLen;

    final Http2Connection connection;
    HttpClientImpl client;
    final HttpRequestImpl request;
    final DecodingCallback rspHeadersConsumer;
    HttpHeadersImpl responseHeaders;
    final HttpHeadersImpl requestHeaders;
    final HttpHeadersImpl requestPseudoHeaders;
    HttpResponse.BodyProcessor<T> responseProcessor;
    final HttpRequest.BodyProcessor requestProcessor;
    volatile int responseCode;
    volatile Response response;
    volatile CompletableFuture<Response> responseCF;
    final AbstractPushPublisher<ByteBuffer> publisher;
    final CompletableFuture<Void> requestBodyCF = new MinimalFuture<>();

    /** True if END_STREAM has been seen in a frame received on this stream. */
    private volatile boolean remotelyClosed;
    private volatile boolean closed;
    private volatile boolean endStreamSent;

    // state flags
    boolean requestSent, responseReceived, responseHeadersReceived;

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

    @Override
    CompletableFuture<T> readBodyAsync(HttpResponse.BodyHandler<T> handler,
                                       boolean returnToCache,
                                       Executor executor)
    {
        Log.logTrace("Reading body on stream {0}", streamid);
        responseProcessor = handler.apply(responseCode, responseHeaders);
        setClientForResponse(responseProcessor);
        publisher.subscribe(responseProcessor);
        CompletableFuture<T> cf = receiveData(executor);

        PushGroup<?,?> pg = exchange.getPushGroup();
        if (pg != null) {
            // if an error occurs make sure it is recorded in the PushGroup
            cf = cf.whenComplete((t,e) -> pg.pushError(e));
        }
        return cf;
    }

    @Override
    T readBody(HttpResponse.BodyHandler<T> handler, boolean returnToCache)
        throws IOException
    {
        CompletableFuture<T> cf = readBodyAsync(handler,
                                                returnToCache,
                                                null);
        try {
            return cf.join();
        } catch (CompletionException e) {
            throw Utils.getIOException(e);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("streamid: ")
                .append(streamid);
        return sb.toString();
    }

    private boolean receiveDataFrame(Http2Frame frame) throws IOException, InterruptedException {
        if (frame instanceof ResetFrame) {
            handleReset((ResetFrame) frame);
            return true;
        } else if (!(frame instanceof DataFrame)) {
            assert false;
            return true;
        }
        DataFrame df = (DataFrame) frame;
        // RFC 7540 6.1:
        // The entire DATA frame payload is included in flow control,
        // including the Pad Length and Padding fields if present
        int len = df.payloadLength();
        ByteBufferReference[] buffers = df.getData();
        for (ByteBufferReference b : buffers) {
            ByteBuffer buf = b.get();
            if (buf.hasRemaining()) {
                publisher.acceptData(Optional.of(buf));
            }
        }
        connection.windowUpdater.update(len);
        if (df.getFlag(DataFrame.END_STREAM)) {
            setEndStreamReceived();
            publisher.acceptData(Optional.empty());
            return false;
        }
        // Don't send window update on a stream which is
        // closed or half closed.
        windowUpdater.update(len);
        return true;
    }

    // pushes entire response body into response processor
    // blocking when required by local or remote flow control
    CompletableFuture<T> receiveData(Executor executor) {
        CompletableFuture<T> cf = responseProcessor
                .getBody()
                .toCompletableFuture();
        Consumer<Throwable> onError = e -> {
            Log.logTrace("receiveData: {0}", e.toString());
            e.printStackTrace();
            cf.completeExceptionally(e);
            publisher.acceptError(e);
        };
        if (executor == null) {
            inputQ.blockingReceive(this::receiveDataFrame, onError);
        } else {
            inputQ.asyncReceive(executor, this::receiveDataFrame, onError);
        }
        return cf;
    }

    @Override
    void sendBody() throws IOException {
        try {
            sendBodyImpl().join();
        } catch (CompletionException e) {
            throw Utils.getIOException(e);
        }
    }

    CompletableFuture<ExchangeImpl<T>> sendBodyAsync() {
        return sendBodyImpl().thenApply( v -> this);
    }

    @SuppressWarnings("unchecked")
    Stream(HttpClientImpl client,
           Http2Connection connection,
           Exchange<T> e,
           WindowController windowController)
    {
        super(e);
        this.client = client;
        this.connection = connection;
        this.windowController = windowController;
        this.request = e.request();
        this.requestProcessor = request.requestProcessor;
        responseHeaders = new HttpHeadersImpl();
        requestHeaders = new HttpHeadersImpl();
        rspHeadersConsumer = (name, value) -> {
            responseHeaders.addHeader(name.toString(), value.toString());
            if (Log.headers() && Log.trace()) {
                Log.logTrace("RECEIVED HEADER (streamid={0}): {1}: {2}",
                             streamid, name, value);
            }
        };
        this.requestPseudoHeaders = new HttpHeadersImpl();
        // NEW
        this.publisher = new BlockingPushPublisher<>();
        this.windowUpdater = new StreamWindowUpdateSender(connection);
    }

    /**
     * Entry point from Http2Connection reader thread.
     *
     * Data frames will be removed by response body thread.
     */
    void incoming(Http2Frame frame) throws IOException {
        if ((frame instanceof HeaderFrame)) {
            HeaderFrame hframe = (HeaderFrame)frame;
            if (hframe.endHeaders()) {
                Log.logTrace("handling response (streamid={0})", streamid);
                handleResponse();
                if (hframe.getFlag(HeaderFrame.END_STREAM)) {
                    inputQ.put(new DataFrame(streamid, DataFrame.END_STREAM, new ByteBufferReference[0]));
                }
            }
        } else if (frame instanceof DataFrame) {
            inputQ.put(frame);
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
        synchronized(this) {
            responseHeadersReceived = true;
        }
        HttpConnection c = connection.connection; // TODO: improve
        responseCode = (int)responseHeaders
                .firstValueAsLong(":status")
                .orElseThrow(() -> new IOException("no statuscode in response"));

        response = new Response(
                request, exchange, responseHeaders,
                responseCode, HttpClient.Version.HTTP_2);

        this.responseContentLen = responseHeaders
                .firstValueAsLong("content-length")
                .orElse(-1L);

        if (Log.headers()) {
            StringBuilder sb = new StringBuilder("RESPONSE HEADERS:\n");
            Log.dumpHeaders(sb, "    ", responseHeaders);
            Log.logHeaders(sb.toString());
        }

        completeResponse(response);
    }

    void incoming_reset(ResetFrame frame) throws IOException {
        Log.logTrace("Received RST_STREAM on stream {0}", streamid);
        if (endStreamReceived()) {
            Log.logTrace("Ignoring RST_STREAM frame received on remotely closed stream {0}", streamid);
        } else if (closed) {
            Log.logTrace("Ignoring RST_STREAM frame received on closed stream {0}", streamid);
        } else {
            boolean pushedToQueue = false;
            synchronized(this) {
                // if the response headers are not yet
                // received, or the inputQueue is closed, handle reset directly.
                // Otherwise, put it in the input queue in order to read all
                // pending data frames first. Indeed, a server may send
                // RST_STREAM after sending END_STREAM, in which case we should
                // ignore it. However, we won't know if we have received END_STREAM
                // or not until all pending data frames are read.
                // Because the inputQ will not be read until the response
                // headers are received, and because response headers won't be
                // sent if the server sent RST_STREAM, then we must handle
                // reset here directly unless responseHeadersReceived is true.
                pushedToQueue = !closed && responseHeadersReceived && inputQ.tryPut(frame);
            }
            if (!pushedToQueue) {
                // RST_STREAM was not pushed to the queue: handle it.
                try {
                    handleReset(frame);
                } catch (IOException io) {
                    completeResponseExceptionally(io);
                }
            } else {
                // RST_STREAM was pushed to the queue. It will be handled by
                // asyncReceive after all pending data frames have been
                // processed.
                Log.logTrace("RST_STREAM pushed in queue for stream {0}", streamid);
            }
        }
    }

    void handleReset(ResetFrame frame) throws IOException {
        Log.logTrace("Handling RST_STREAM on stream {0}", streamid);
        if (!closed) {
            close();
            int error = frame.getErrorCode();
            throw new IOException(ErrorFrame.stringForCode(error));
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
        if (pushGroup == null || pushGroup.noMorePushes()) {
            cancelImpl(new IllegalStateException("unexpected push promise"
                + " on stream " + streamid));
        }

        HttpResponse.MultiProcessor<?,T> proc = pushGroup.processor();

        CompletableFuture<HttpResponse<T>> cf = pushStream.responseCF();

        Optional<HttpResponse.BodyHandler<T>> bpOpt = proc.onRequest(
                pushReq);

        if (!bpOpt.isPresent()) {
            IOException ex = new IOException("Stream "
                 + streamid + " cancelled by user");
            if (Log.trace()) {
                Log.logTrace("No body processor for {0}: {1}", pushReq,
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

    @Override
    Response getResponse() throws IOException {
        try {
            if (request.duration() != null) {
                Log.logTrace("Waiting for response (streamid={0}, timeout={1}ms)",
                             streamid,
                             request.duration().toMillis());
                return getResponseAsync(null).get(
                        request.duration().toMillis(), TimeUnit.MILLISECONDS);
            } else {
                Log.logTrace("Waiting for response (streamid={0})", streamid);
                return getResponseAsync(null).join();
            }
        } catch (TimeoutException e) {
            Log.logTrace("Response timeout (streamid={0})", streamid);
            throw new HttpTimeoutException("Response timed out");
        } catch (InterruptedException | ExecutionException | CompletionException e) {
            Throwable t = e.getCause();
            Log.logTrace("Response failed (streamid={0}): {1}", streamid, t);
            if (t instanceof IOException) {
                throw (IOException)t;
            }
            throw new IOException(e);
        } finally {
            Log.logTrace("Got response or failed (streamid={0})", streamid);
        }
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
    void sendHeadersOnly() throws IOException, InterruptedException {
        if (Log.requests() && request != null) {
            Log.logRequest(request.toString());
        }
        requestContentLen = requestProcessor.contentLength();
        OutgoingHeaders<Stream<T>> f = headerFrame(requestContentLen);
        connection.sendFrame(f);
    }

    void registerStream(int id) {
        this.streamid = id;
        connection.putStream(this, streamid);
    }

    class RequestSubscriber
        extends RequestProcessors.ProcessorBase
        implements Flow.Subscriber<ByteBuffer>
    {
        // can be < 0 if the actual length is not known.
        private volatile long remainingContentLength;
        private volatile Subscription subscription;

        RequestSubscriber(long contentLen) {
            this.remainingContentLength = contentLen;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                throw new IllegalStateException();
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            if (requestBodyCF.isDone()) {
                throw new IllegalStateException();
            }

            try {
                while (item.hasRemaining()) {
                    assert !endStreamSent : "internal error, send data after END_STREAM flag";
                    DataFrame df = getDataFrame(item);
                    if (remainingContentLength > 0) {
                        remainingContentLength -= df.getDataLength();
                        assert remainingContentLength >= 0;
                        if (remainingContentLength == 0) {
                            df.setFlag(DataFrame.END_STREAM);
                            endStreamSent = true;
                        }
                    }
                    connection.sendDataFrame(df);
                }
                subscription.request(1);
            } catch (InterruptedException ex) {
                subscription.cancel();
                requestBodyCF.completeExceptionally(ex);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (requestBodyCF.isDone()) {
                return;
            }
            subscription.cancel();
            requestBodyCF.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            assert endStreamSent || remainingContentLength < 0;
            try {
                if (!endStreamSent) {
                    endStreamSent = true;
                    connection.sendDataFrame(getEmptyEndStreamDataFrame());
                }
                requestBodyCF.complete(null);
            } catch (InterruptedException ex) {
                requestBodyCF.completeExceptionally(ex);
            }
        }
    }

    DataFrame getDataFrame(ByteBuffer buffer) throws InterruptedException {
        int requestAmount = Math.min(connection.getMaxSendFrameSize(), buffer.remaining());
        // blocks waiting for stream send window, if exhausted
        int actualAmount = windowController.tryAcquire(requestAmount, streamid);
        ByteBuffer outBuf = Utils.slice(buffer,  actualAmount);
        DataFrame df = new DataFrame(streamid, 0 , ByteBufferReference.of(outBuf));
        return df;
    }

    private DataFrame getEmptyEndStreamDataFrame() throws InterruptedException {
        return new DataFrame(streamid, DataFrame.END_STREAM, new ByteBufferReference[0]);
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
        synchronized (response_cfs) {
            if (!response_cfs.isEmpty()) {
                cf = response_cfs.remove(0);
            } else {
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
            cf = cf.whenComplete((t,e) -> pg.pushError(e));
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
                }
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

    final synchronized boolean isResponseReceived() {
        return responseReceived;
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
            for (CompletableFuture<Response> cf : response_cfs) {
                if (!cf.isDone()) {
                    cf.completeExceptionally(t);
                    response_cfs.remove(cf);
                    return;
                }
            }
            response_cfs.add(MinimalFuture.failedFuture(t));
        }
    }

    CompletableFuture<Void> sendBodyImpl() {
        RequestSubscriber subscriber = new RequestSubscriber(requestContentLen);
        subscriber.setClient(client);
        requestProcessor.subscribe(subscriber);
        requestBodyCF.whenComplete((v,t) -> requestSent());
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
        if (Log.trace()) {
            Log.logTrace("cancelling stream {0}: {1}\n", streamid, e);
        }
        boolean closing;
        if (closing = !closed) { // assigning closing to !closed
            synchronized (this) {
                if (closing = !closed) { // assigning closing to !closed
                    closed=true;
                }
            }
        }
        if (closing) { // true if the stream has not been closed yet
            inputQ.close();
        }
        completeResponseExceptionally(e);
        try {
            // will send a RST_STREAM frame
            connection.resetStream(streamid, ResetFrame.CANCEL);
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
        inputQ.close();
        connection.closeStream(streamid);
        Log.logTrace("Stream {0} closed", streamid);
    }

    static class PushedStream<U,T> extends Stream<T> {
        final PushGroup<U,T> pushGroup;
        private final Stream<T> parent;      // used by server push streams
        // push streams need the response CF allocated up front as it is
        // given directly to user via the multi handler callback function.
        final CompletableFuture<Response> pushCF;
        final CompletableFuture<HttpResponse<T>> responseCF;
        final HttpRequestImpl pushReq;
        HttpResponse.BodyHandler<T> pushHandler;

        PushedStream(PushGroup<U,T> pushGroup, HttpClientImpl client,
                Http2Connection connection, Stream<T> parent,
                Exchange<T> pushReq) {
            // ## no request body possible, null window controller
            super(client, connection, pushReq, null);
            this.pushGroup = pushGroup;
            this.pushReq = pushReq.request();
            this.pushCF = new MinimalFuture<>();
            this.responseCF = new MinimalFuture<>();
            this.parent = parent;
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
                        .whenComplete((ExchangeImpl<T> v, Throwable t) -> pushGroup.pushError(t));
        }

        @Override
        CompletableFuture<ExchangeImpl<T>> sendHeadersAsync() {
            return super.sendHeadersAsync()
                        .whenComplete((ExchangeImpl<T> ex, Throwable t) -> pushGroup.pushError(t));
        }

        @Override
        CompletableFuture<Response> getResponseAsync(Executor executor) {
            CompletableFuture<Response> cf = pushCF.whenComplete((v, t) -> pushGroup.pushError(t));
            if(executor!=null && !cf.isDone()) {
                cf  = cf.thenApplyAsync( r -> r, executor);
            }
            return cf;
        }

        @Override
        CompletableFuture<T> readBodyAsync(
                HttpResponse.BodyHandler<T> handler,
                boolean returnToCache,
                Executor executor)
        {
            return super.readBodyAsync(handler, returnToCache, executor)
                        .whenComplete((v, t) -> pushGroup.pushError(t));
        }

        @Override
        void completeResponse(Response r) {
            HttpResponseImpl.logResponse(r);
            pushCF.complete(r); // not strictly required for push API
            // start reading the body using the obtained BodyProcessor
            CompletableFuture<Void> start = new MinimalFuture<>();
            start.thenCompose( v -> readBodyAsync(getPushHandler(), false, getExchange().executor()))
                .whenComplete((T body, Throwable t) -> {
                    if (t != null) {
                        responseCF.completeExceptionally(t);
                    } else {
                        HttpResponseImpl<T> response = new HttpResponseImpl<>(r.request, r, body, getExchange());
                        responseCF.complete(response);
                    }
                });
            start.completeAsync(() -> null, getExchange().executor());
        }

        @Override
        void completeResponseExceptionally(Throwable t) {
            pushCF.completeExceptionally(t);
        }

        @Override
        synchronized void responseReceived() {
            super.responseReceived();
        }

        // create and return the PushResponseImpl
        @Override
        protected void handleResponse() {
            HttpConnection c = connection.connection; // TODO: improve
            responseCode = (int)responseHeaders
                .firstValueAsLong(":status")
                .orElse(-1);

            if (responseCode == -1) {
                completeResponseExceptionally(new IOException("No status code"));
            }

            this.response = new Response(
                pushReq, exchange, responseHeaders,
                responseCode, HttpClient.Version.HTTP_2);

            this.responseContentLen = responseHeaders
                .firstValueAsLong("content-length")
                .orElse(-1L);

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

}
