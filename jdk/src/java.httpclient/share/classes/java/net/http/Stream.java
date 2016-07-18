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

package java.net.http;

import sun.net.httpclient.hpack.DecodingCallback;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.LongConsumer;

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
class Stream extends ExchangeImpl {

    final Queue<Http2Frame> inputQ;

    volatile int streamid;

    long responseContentLen = -1;
    long responseBytesProcessed = 0;
    long requestContentLen;

    Http2Connection connection;
    HttpClientImpl client;
    final HttpRequestImpl request;
    final DecodingCallback rspHeadersConsumer;
    HttpHeadersImpl responseHeaders;
    final HttpHeadersImpl requestHeaders;
    final HttpHeadersImpl requestPseudoHeaders;
    HttpResponse.BodyProcessor<?> responseProcessor;
    final HttpRequest.BodyProcessor requestProcessor;
    HttpResponse response;

    // state flags
    boolean requestSent, responseReceived;

    final FlowController userRequestFlowController =
            new FlowController();
    final FlowController remoteRequestFlowController =
            new FlowController();
    final FlowController responseFlowController =
            new FlowController();

    final ExecutorWrapper executor;

    @Override
    @SuppressWarnings("unchecked")
    <T> CompletableFuture<T> responseBodyAsync(HttpResponse.BodyProcessor<T> processor) {
        this.responseProcessor = processor;
        CompletableFuture<T> cf;
        try {
            T body = processor.onResponseBodyStart(
                    responseContentLen, responseHeaders,
                    responseFlowController); // TODO: filter headers
            if (body != null) {
                cf = CompletableFuture.completedFuture(body);
                receiveDataAsync(processor);
            } else
                cf = receiveDataAsync(processor);
        } catch (IOException e) {
            cf = CompletableFuture.failedFuture(e);
        }
        PushGroup<?> pg = request.pushGroup();
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

    // pushes entire response body into response processor
    // blocking when required by local or remote flow control
    void receiveData() throws IOException {
        Http2Frame frame;
        DataFrame df = null;
        try {
            do {
                frame = inputQ.take();
                if (!(frame instanceof DataFrame)) {
                    assert false;
                    continue;
                }
                df = (DataFrame) frame;
                int len = df.getDataLength();
                ByteBuffer[] buffers = df.getData();
                for (ByteBuffer b : buffers) {
                    responseFlowController.take();
                    responseProcessor.onResponseBodyChunk(b);
                }
                sendWindowUpdate(len);
            } while (!df.getFlag(DataFrame.END_STREAM));
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private <T> CompletableFuture<T> receiveDataAsync(HttpResponse.BodyProcessor<T> processor) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                receiveData();
                T body = processor.onResponseComplete();
                cf.complete(body);
                responseReceived();
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        }, null);
        return cf;
    }

    private void sendWindowUpdate(int increment)
            throws IOException, InterruptedException {
        if (increment == 0)
            return;
        LinkedList<Http2Frame> list = new LinkedList<>();
        WindowUpdateFrame frame = new WindowUpdateFrame();
        frame.streamid(streamid);
        frame.setUpdate(increment);
        list.add(frame);
        frame = new WindowUpdateFrame();
        frame.streamid(0);
        frame.setUpdate(increment);
        list.add(frame);
        connection.sendFrames(list);
    }

    @Override
    CompletableFuture<Void> sendBodyAsync() {
        final CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                sendBodyImpl();
                cf.complete(null);
            } catch (IOException | InterruptedException e) {
                cf.completeExceptionally(e);
            }
        }, null);
        return cf;
    }

    @SuppressWarnings("unchecked")
    Stream(HttpClientImpl client, Http2Connection connection, Exchange e) {
        super(e);
        this.client = client;
        this.connection = connection;
        this.request = e.request();
        this.requestProcessor = request.requestProcessor();
        responseHeaders = new HttpHeadersImpl();
        requestHeaders = new HttpHeadersImpl();
        rspHeadersConsumer = (name, value) -> {
            responseHeaders.addHeader(name.toString(), value.toString());
        };
        this.executor = client.executorWrapper();
        //this.response_cf = new CompletableFuture<HttpResponseImpl>();
        this.requestPseudoHeaders = new HttpHeadersImpl();
        // NEW
        this.inputQ = new Queue<>();
    }

    @SuppressWarnings("unchecked")
    Stream(HttpClientImpl client, Http2Connection connection, HttpRequestImpl req) {
        super(null);
        this.client = client;
        this.connection = connection;
        this.request = req;
        this.requestProcessor = null;
        responseHeaders = new HttpHeadersImpl();
        requestHeaders = new HttpHeadersImpl();
        rspHeadersConsumer = (name, value) -> {
            responseHeaders.addHeader(name.toString(), value.toString());
        };
        this.executor = client.executorWrapper();
        //this.response_cf = new CompletableFuture<HttpResponseImpl>();
        this.requestPseudoHeaders = new HttpHeadersImpl();
        // NEW
        this.inputQ = new Queue<>();
    }

    /**
     * Entry point from Http2Connection reader thread.
     *
     * Data frames will be removed by response body thread.
     *
     * @param frame
     * @throws IOException
     */
    void incoming(Http2Frame frame) throws IOException, InterruptedException {
        if ((frame instanceof HeaderFrame) && ((HeaderFrame)frame).endHeaders()) {
            // Complete headers accumulated. handle response.
            // It's okay if there are multiple HeaderFrames.
            handleResponse();
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

    // create and return the HttpResponseImpl
    protected void handleResponse() throws IOException {
        HttpConnection c = connection.connection; // TODO: improve
        long statusCode = responseHeaders
                .firstValueAsLong(":status")
                .orElseThrow(() -> new IOException("no statuscode in response"));

        this.response = new HttpResponseImpl((int)statusCode, exchange, responseHeaders, null,
                c.sslParameters(), HttpClient.Version.HTTP_2, c);
        this.responseContentLen = responseHeaders
                .firstValueAsLong("content-length")
                .orElse(-1L);
        // different implementations for normal streams and pushed streams
        completeResponse(response);
    }

    void incoming_reset(ResetFrame frame) {
        // TODO: implement reset
        int error = frame.getErrorCode();
        IOException e = new IOException(ErrorFrame.stringForCode(error));
        completeResponseExceptionally(e);
        throw new UnsupportedOperationException("Not implemented");
    }

    void incoming_priority(PriorityFrame frame) {
        // TODO: implement priority
        throw new UnsupportedOperationException("Not implemented");
    }

    void incoming_windowUpdate(WindowUpdateFrame frame) {
        int amount = frame.getUpdate();
        if (amount > 0)
            remoteRequestFlowController.accept(amount);
    }

    void incoming_pushPromise(HttpRequestImpl pushReq, PushedStream pushStream) throws IOException {
        if (Log.requests()) {
            Log.logRequest("PUSH_PROMISE: " + pushReq.toString());
        }
        PushGroup<?> pushGroup = request.pushGroup();
        if (pushGroup == null) {
            cancelImpl(new IllegalStateException("unexpected push promise"));
        }
        // get the handler and call it.
        BiFunction<HttpRequest,CompletableFuture<HttpResponse>,Boolean> ph =
            pushGroup.pushHandler();

        CompletableFuture<HttpResponse> pushCF = pushStream
                .getResponseAsync(null)
                .thenApply(r -> (HttpResponse)r);
        boolean accept = ph.apply(pushReq, pushCF);
        if (!accept) {
            IOException ex = new IOException("Stream cancelled by user");
            cancelImpl(ex);
            pushCF.completeExceptionally(ex);
        } else {
            pushStream.requestSent();
            pushGroup.addPush();
        }
    }

    private OutgoingHeaders headerFrame(long contentLength) {
        HttpHeadersImpl h = request.getSystemHeaders();
        if (contentLength > 0) {
            h.setHeader("content-length", Long.toString(contentLength));
        }
        setPseudoHeaderFields();
        OutgoingHeaders f = new OutgoingHeaders(h, request.getUserHeaders(), this);
        if (contentLength == 0) {
            f.setFlag(HeadersFrame.END_STREAM);
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
        if (path == null) {
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
    HttpResponseImpl getResponse() throws IOException {
        try {
            if (request.timeval() > 0) {
                return getResponseAsync(null).get(
                        request.timeval(), TimeUnit.MILLISECONDS);
            } else {
                return getResponseAsync(null).join();
            }
        } catch (TimeoutException e) {
            throw new HttpTimeoutException("Response timed out");
        } catch (InterruptedException | ExecutionException | CompletionException e) {
            Throwable t = e.getCause();
            if (t instanceof IOException) {
                throw (IOException)t;
            }
            throw new IOException(e);
        }
    }

    @Override
    void sendRequest() throws IOException, InterruptedException {
        sendHeadersOnly();
        sendBody();
    }

    /**
     * A simple general purpose blocking flow controller
     */
    class FlowController implements LongConsumer {
        int permits;

        FlowController() {
            this.permits = 0;
        }

        @Override
        public synchronized void accept(long n) {
            if (n < 1) {
                throw new InternalError("FlowController.accept called with " + n);
            }
            if (permits == 0) {
                permits += n;
                notifyAll();
            } else {
                permits += n;
            }
        }

        public synchronized void take() throws InterruptedException {
            take(1);
        }

        public synchronized void take(int amount) throws InterruptedException {
            assert permits >= 0;
            while (permits < amount) {
                int n = Math.min(amount, permits);
                permits -= n;
                amount -= n;
                if (amount > 0)
                    wait();
            }
        }
    }

    @Override
    void sendHeadersOnly() throws IOException, InterruptedException {
        if (Log.requests() && request != null) {
            Log.logRequest(request.toString());
        }
        requestContentLen = requestProcessor.onRequestStart(request, userRequestFlowController);
        OutgoingHeaders f = headerFrame(requestContentLen);
        connection.sendFrame(f);
    }

    @Override
    void sendBody() throws IOException, InterruptedException {
        sendBodyImpl();
    }

    void registerStream(int id) {
        this.streamid = id;
        connection.putStream(this, streamid);
    }

    DataFrame getDataFrame() throws IOException, InterruptedException {
        userRequestFlowController.take();
        int maxpayloadLen = connection.getMaxSendFrameSize() - 9;
        ByteBuffer buffer = connection.getBuffer();
        buffer.limit(maxpayloadLen);
        boolean complete = requestProcessor.onRequestBodyChunk(buffer);
        buffer.flip();
        int amount = buffer.remaining();
        // wait for flow control if necessary. Following method will block
        // until after headers frame is sent, so correct streamid is set.
        remoteRequestFlowController.take(amount);
        connection.obtainSendWindow(amount);

        DataFrame df = new DataFrame();
        df.streamid(streamid);
        if (complete) {
            df.setFlag(DataFrame.END_STREAM);
        }
        df.setData(buffer);
        df.computeLength();
        return df;
    }


    @Override
    CompletableFuture<Void> sendHeadersAsync() {
        try {
            sendHeadersOnly();
            return CompletableFuture.completedFuture(null);
        } catch (IOException | InterruptedException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * A List of responses relating to this stream. Normally there is only
     * one response, but intermediate responses like 100 are allowed
     * and must be passed up to higher level before continuing. Deals with races
     * such as if responses are returned before the CFs get created by
     * getResponseAsync()
     */

    final List<CompletableFuture<HttpResponseImpl>> response_cfs = new ArrayList<>(5);

    @Override
    CompletableFuture<HttpResponseImpl> getResponseAsync(Void v) {
        CompletableFuture<HttpResponseImpl> cf;
        synchronized (response_cfs) {
            if (!response_cfs.isEmpty()) {
                cf = response_cfs.remove(0);
            } else {
                cf = new CompletableFuture<>();
                response_cfs.add(cf);
            }
        }
        PushGroup<?> pg = request.pushGroup();
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
    void completeResponse(HttpResponse r) {
        HttpResponseImpl resp = (HttpResponseImpl)r;
        synchronized (response_cfs) {
            int cfs_len = response_cfs.size();
            for (int i=0; i<cfs_len; i++) {
                CompletableFuture<HttpResponseImpl> cf = response_cfs.get(i);
                if (!cf.isDone()) {
                    cf.complete(resp);
                    response_cfs.remove(cf);
                    return;
                }
            }
            response_cfs.add(CompletableFuture.completedFuture(resp));
        }
    }

    // methods to update state and remove stream when finished

    synchronized void requestSent() {
        requestSent = true;
        if (responseReceived)
            connection.deleteStream(this);
    }

    synchronized void responseReceived() {
        responseReceived = true;
        if (requestSent)
            connection.deleteStream(this);
        PushGroup<?> pg = request.pushGroup();
        if (pg != null)
            pg.noMorePushes();
    }

    /**
     * same as above but for errors
     *
     * @param t
     */
    void completeResponseExceptionally(Throwable t) {
        synchronized (response_cfs) {
            for (CompletableFuture<HttpResponseImpl> cf : response_cfs) {
                if (!cf.isDone()) {
                    cf.completeExceptionally(t);
                    response_cfs.remove(cf);
                    return;
                }
            }
            response_cfs.add(CompletableFuture.failedFuture(t));
        }
    }

    void sendBodyImpl() throws IOException, InterruptedException {
        if (requestContentLen == 0) {
            // no body
            requestSent();
            return;
        }
        DataFrame df;
        do {
            df = getDataFrame();
            // TODO: check accumulated content length (if not checked below)
            connection.sendFrame(df);
        } while (!df.getFlag(DataFrame.END_STREAM));
        requestSent();
    }

    @Override
    void cancel() {
        cancelImpl(new Exception("Cancelled"));
    }


    void cancelImpl(Throwable e) {
        Log.logTrace("cancelling stream: {0}\n", e.toString());
        inputQ.close();
        completeResponseExceptionally(e);
        try {
            connection.resetStream(streamid, ResetFrame.CANCEL);
        } catch (IOException | InterruptedException ex) {
            Log.logError(ex);
        }
    }

    @Override
    CompletableFuture<Void> sendRequestAsync() {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
           try {
               sendRequest();
               cf.complete(null);
           } catch (IOException |InterruptedException e) {
               cf.completeExceptionally(e);
           }
        }, null);
        return cf;
    }

    @Override
    <T> T responseBody(HttpResponse.BodyProcessor<T> processor) throws IOException {
        this.responseProcessor = processor;
        T body = processor.onResponseBodyStart(
                    responseContentLen, responseHeaders,
                    responseFlowController); // TODO: filter headers
        if (body == null) {
            receiveData();
            body = processor.onResponseComplete();
        } else
            receiveDataAsync(processor);
        responseReceived();
        return body;
    }

    // called from Http2Connection reader thread
    synchronized void updateOutgoingWindow(int update) {
        remoteRequestFlowController.accept(update);
    }

    void close(String msg) {
        cancel();
    }

    static class PushedStream extends Stream {
        final PushGroup<?> pushGroup;
        final private Stream parent;      // used by server push streams
        // push streams need the response CF allocated up front as it is
        // given directly to user via the multi handler callback function.
        final CompletableFuture<HttpResponseImpl> pushCF;
        final HttpRequestImpl pushReq;

        PushedStream(PushGroup<?> pushGroup, HttpClientImpl client,
                Http2Connection connection, Stream parent,
                HttpRequestImpl pushReq) {
            super(client, connection, pushReq);
            this.pushGroup = pushGroup;
            this.pushReq = pushReq;
            this.pushCF = new CompletableFuture<>();
            this.parent = parent;
        }

        // Following methods call the super class but in case of
        // error record it in the PushGroup. The error method is called
        // with a null value when no error occurred (is a no-op)
        @Override
        CompletableFuture<Void> sendBodyAsync() {
            return super.sendBodyAsync()
                        .whenComplete((v, t) -> pushGroup.pushError(t));
        }

        @Override
        CompletableFuture<Void> sendHeadersAsync() {
            return super.sendHeadersAsync()
                        .whenComplete((v, t) -> pushGroup.pushError(t));
        }

        @Override
        CompletableFuture<Void> sendRequestAsync() {
            return super.sendRequestAsync()
                        .whenComplete((v, t) -> pushGroup.pushError(t));
        }

        @Override
        CompletableFuture<HttpResponseImpl> getResponseAsync(Void vo) {
            return pushCF.whenComplete((v, t) -> pushGroup.pushError(t));
        }

        @Override
        <T> CompletableFuture<T> responseBodyAsync(HttpResponse.BodyProcessor<T> processor) {
            return super.responseBodyAsync(processor)
                        .whenComplete((v, t) -> pushGroup.pushError(t));
        }

        @Override
        void completeResponse(HttpResponse r) {
            HttpResponseImpl resp = (HttpResponseImpl)r;
            Utils.logResponse(resp);
            pushCF.complete(resp);
        }

        @Override
        void completeResponseExceptionally(Throwable t) {
            pushCF.completeExceptionally(t);
        }

        @Override
        synchronized void responseReceived() {
            super.responseReceived();
            pushGroup.pushCompleted();
        }

        // create and return the PushResponseImpl
        @Override
        protected void handleResponse() {
            HttpConnection c = connection.connection; // TODO: improve
            long statusCode = responseHeaders
                .firstValueAsLong(":status")
                .orElse(-1L);

            if (statusCode == -1L)
                completeResponseExceptionally(new IOException("No status code"));
            ImmutableHeaders h = new ImmutableHeaders(responseHeaders, Utils.ALL_HEADERS);
            this.response = new HttpResponseImpl((int)statusCode, pushReq, h, this,
                c.sslParameters());
            this.responseContentLen = responseHeaders
                .firstValueAsLong("content-length")
                .orElse(-1L);
            // different implementations for normal streams and pushed streams
            completeResponse(response);
        }
    }

    /**
     * One PushGroup object is associated with the parent Stream of
     * the pushed Streams. This keeps track of all common state associated
     * with the pushes.
     */
    static class PushGroup<T> {
        // the overall completion object, completed when all pushes are done.
        final CompletableFuture<T> resultCF;
        Throwable error; // any exception that occured during pushes

        // CF for main response
        final CompletableFuture<HttpResponse> mainResponse;

        // user's processor object
        final HttpResponse.MultiProcessor<T> multiProcessor;

        // per push handler function provided by processor
        final private BiFunction<HttpRequest,
                           CompletableFuture<HttpResponse>,
                           Boolean> pushHandler;
        int numberOfPushes;
        int remainingPushes;
        boolean noMorePushes = false;

        PushGroup(HttpResponse.MultiProcessor<T> multiProcessor, HttpRequestImpl req) {
            this.resultCF = new CompletableFuture<>();
            this.mainResponse = new CompletableFuture<>();
            this.multiProcessor = multiProcessor;
            this.pushHandler = multiProcessor.onStart(req, mainResponse);
        }

        CompletableFuture<T> groupResult() {
            return resultCF;
        }

        CompletableFuture<HttpResponse> mainResponse() {
            return mainResponse;
        }

        private BiFunction<HttpRequest,
            CompletableFuture<HttpResponse>, Boolean> pushHandler()
        {
                return pushHandler;
        }

        synchronized void addPush() {
            numberOfPushes++;
            remainingPushes++;
        }

        synchronized int numberOfPushes() {
            return numberOfPushes;
        }
        // This is called when the main body response completes because it means
        // no more PUSH_PROMISEs are possible
        synchronized void noMorePushes() {
            noMorePushes = true;
            checkIfCompleted();
        }

        synchronized void pushCompleted() {
            remainingPushes--;
            checkIfCompleted();
        }

        synchronized void checkIfCompleted() {
            if (remainingPushes == 0 && error == null && noMorePushes) {
                T overallResult = multiProcessor.onComplete();
                resultCF.complete(overallResult);
            }
        }

        synchronized void pushError(Throwable t) {
            if (t == null)
                return;
            this.error = t;
            resultCF.completeExceptionally(t);
        }
    }
}
