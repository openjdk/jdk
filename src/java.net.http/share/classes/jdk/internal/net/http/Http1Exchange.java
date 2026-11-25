/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.HttpClientImpl.DelegatingExecutor;
import jdk.internal.net.http.common.Demand;
import jdk.internal.net.http.common.HttpBodySubscriberWrapper;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.FlowTube;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.Utils;
import static java.net.http.HttpClient.Version.HTTP_1_1;

/**
 * Encapsulates one HTTP/1.1 request/response exchange.
 */
class Http1Exchange<T> extends ExchangeImpl<T> {

    final Logger debug = Utils.getDebugLogger(this::dbgString, Utils.DEBUG);
    final HttpRequestImpl request; // main request
    final Http1Request requestAction;
    private volatile Http1Response<T> response;
    final HttpConnection connection;
    final HttpClientImpl client;
    final DelegatingExecutor executor;
    private final Http1AsyncReceiver asyncReceiver;
    private volatile boolean upgraded;

    /** Records a possible cancellation raised before any operation
     * has been initiated, or an error received while sending the request. */
    private final AtomicReference<Throwable> failedRef = new AtomicReference<>();
    private final ConcurrentLinkedDeque<CompletableFuture<?>> operations =
            new ConcurrentLinkedDeque<>(); // used for cancel

    /** Must be held when operating on any internal state or data. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Holds the outgoing data, either the headers or a request body part. Or
     * an error from the request body publisher. At most there can be ~2 pieces
     * of outgoing data ( onComplete|onError can be invoked without demand ).*/
    final ConcurrentLinkedDeque<DataPair> outgoing = new ConcurrentLinkedDeque<>();

    /** The write publisher, responsible for writing the complete request ( both
     * headers and body ( if any )). */
    private final Http1Publisher writePublisher = new Http1Publisher();

    /** Completed when the header have been published, or there is an error */
    private final CompletableFuture<ExchangeImpl<T>> headersSentCF  = new MinimalFuture<>();
     /** Completed when the body has been published, or there is an error */
    private final CompletableFuture<ExchangeImpl<T>> bodySentCF = new MinimalFuture<>();

    /** The subscriber to the request's body published. May be null. */
    private volatile Http1RequestBodySubscriber bodySubscriber;
    /** The subscriber to the response's body received. May be null. */
    private volatile BodySubscriber<T> responseSubscriber;

    enum State { INITIAL,
                 HEADERS,
                 BODY,
                 ERROR,          // terminal state
                 COMPLETING,
                 COMPLETED }     // terminal state

    private State state = State.INITIAL;

    /** A carrier for either data or an error. Used to carry data, and communicate
     * errors from the request ( both headers and body ) to the exchange. */
    static class DataPair {
        Throwable throwable;
        List<ByteBuffer> data;
        DataPair(List<ByteBuffer> data, Throwable throwable){
            this.data = data;
            this.throwable = throwable;
        }
        @Override
        public String toString() {
            return "DataPair [data=" + data + ", throwable=" + throwable + "]";
        }
    }

    /** An abstract supertype for HTTP/1.1 body subscribers. There are two
     * concrete implementations: {@link Http1Request.StreamSubscriber}, and
     * {@link Http1Request.FixedContentSubscriber}, for receiving chunked and
     * fixed length bodies, respectively. */
    abstract static class Http1RequestBodySubscriber implements Flow.Subscriber<ByteBuffer> {
        final MinimalFuture<Flow.Subscription> whenSubscribed = new MinimalFuture<>();
        private volatile Flow.Subscription subscription;
        volatile boolean complete;
        private final Logger debug;
        Http1RequestBodySubscriber(Logger debug) {
            assert debug != null;
            this.debug = debug;
        }

        /** Final sentinel in the stream of request body. */
        static final List<ByteBuffer> COMPLETED = List.of(ByteBuffer.allocate(0));

        final void request(long n) {
            if (debug.on())
                debug.log("Http1BodySubscriber requesting %d, from %s",
                          n, subscription);
            subscription.request(n);
        }

        /** A current-state message suitable for inclusion in an exception detail message. */
        abstract String currentStateMessage();

        final boolean isSubscribed() {
            return subscription != null;
        }

        final void setSubscription(Flow.Subscription subscription) {
            Flow.Subscription sub;
            synchronized (this) {
                if ((sub = this.subscription) == null) {
                    this.subscription = sub = subscription;
                }
            }
            if (sub == subscription) {
                whenSubscribed.complete(subscription);
            } else subscription.cancel();
        }

        final void cancelSubscription() {
            try {
                Flow.Subscription sub;
                synchronized (this) {
                    if ((sub = this.subscription) == null) {
                        this.subscription = sub = HttpBodySubscriberWrapper.NOP;
                    }
                }
                sub.cancel();
            } catch(Throwable t) {
                String msg = "Ignoring exception raised when canceling BodyPublisher subscription";
                if (debug.on()) debug.log("%s: %s", msg, t);
                Log.logError("{0}: {1}", msg, (Object)t);
            }
        }

        static Http1RequestBodySubscriber completeSubscriber(Logger debug) {
            return new Http1RequestBodySubscriber(debug) {
                @Override public void onSubscribe(Flow.Subscription subscription) { error(); }
                @Override public void onNext(ByteBuffer item) { error(); }
                @Override public void onError(Throwable throwable) { error(); }
                @Override public void onComplete() { error(); }
                @Override String currentStateMessage() { return null; }
                private void error() {
                    throw new InternalError("should not reach here");
                }
            };
        }
    }

    /**
     * The Http1AsyncReceiver ensures that all calls to
     * the subscriber, including onSubscribe, occur sequentially.
     * There could however be some race conditions that could happen
     * in case of unexpected errors thrown at unexpected places, which
     * may cause onError to be called multiple times.
     * The Http1BodySubscriber will ensure that the user subscriber
     * is actually completed only once - and only after it is
     * subscribed.
     * @param <U> The type of response.
     */
    static final class Http1ResponseBodySubscriber<U> extends HttpBodySubscriberWrapper<U> {
        final Http1Exchange<U> exchange;

        private final boolean cancelTimerOnTermination;

        Http1ResponseBodySubscriber(
                BodySubscriber<U> userSubscriber,
                boolean cancelTimerOnTermination,
                Http1Exchange<U> exchange) {
            super(userSubscriber);
            this.cancelTimerOnTermination = cancelTimerOnTermination;
            this.exchange = exchange;
        }

        @Override
        protected void register() {
            exchange.registerResponseSubscriber(this);
        }

        @Override
        protected void unregister() {
            exchange.unregisterResponseSubscriber(this);
        }

        @Override
        protected void onTermination() {
            if (cancelTimerOnTermination) {
                exchange.exchange.multi.cancelTimer();
            }
        }

    }

    @Override
    public String toString() {
        return "HTTP/1.1 " + request.toString();
    }

    HttpRequestImpl request() {
        return request;
    }

    Http1Exchange(Exchange<T> exchange, HttpConnection connection)
        throws IOException
    {
        super(exchange);
        this.request = exchange.request();
        this.client = exchange.client();
        this.executor = exchange.executor();
        operations.add(headersSentCF);
        operations.add(bodySentCF);
        if (connection != null) {
            this.connection = connection;
        } else {
            InetSocketAddress addr = request.getAddress();
            this.connection = HttpConnection.getConnection(addr, client, exchange, request, HTTP_1_1);
        }
        this.requestAction = new Http1Request(request, this);
        this.asyncReceiver = new Http1AsyncReceiver(executor, this);
    }

    @Override
    HttpConnection connection() {
        return connection;
    }

    private void connectFlows(HttpConnection connection) {
        FlowTube tube =  connection.getConnectionFlow();
        if (debug.on()) debug.log("%s connecting flows %s", tube, writePublisher);

        // Connect the flow to our Http1TubeSubscriber:
        //   asyncReceiver.subscriber().
        tube.connectFlows(writePublisher,
                          asyncReceiver.subscriber());
    }

    // The Http1ResponseBodySubscriber is registered with the HttpClient
    // to ensure that it gets completed if the SelectorManager aborts due
    // to unexpected exceptions.
    private boolean registerResponseSubscriber(Http1ResponseBodySubscriber<T> subscriber) {
        Throwable failed = null;
        lock.lock();
        try {
            failed = failedRef.get();
            if (failed == null) {
                this.responseSubscriber = subscriber;
            }
        } finally {
            lock.unlock();
        }
        if (failed != null) {
            subscriber.onError(failed);
            return false;
        } else {
            return client.registerSubscriber(subscriber);
        }
    }

    private boolean unregisterResponseSubscriber(Http1ResponseBodySubscriber<T> subscriber) {
        return client.unregisterSubscriber(subscriber);
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendHeadersAsync() {
        // create the response before sending the request headers, so that
        // the response can set the appropriate receivers.
        if (debug.on()) debug.log("Sending headers only");
        // If the first attempt to read something triggers EOF, or
        // IOException("channel reset by peer"), we're going to retry.
        // Instruct the asyncReceiver to throw ConnectionExpiredException
        // to force a retry.
        asyncReceiver.setRetryOnError(true);
        if (response == null) {
            response = new Http1Response<>(connection, this, asyncReceiver);
        }

        if (debug.on()) debug.log("response created in advance");

        CompletableFuture<Void> connectCF;
        if (!connection.connected()) {
            if (debug.on()) debug.log("initiating connect async");
            connectCF = connection.connectAsync(exchange)
                    .thenCompose(unused -> connection.finishConnect());
            Throwable cancelled;
            lock.lock();
            try {
                if ((cancelled = failedRef.get()) == null) {
                    operations.add(connectCF);
                }
            } finally {
                lock.unlock();
            }
            if (cancelled != null) {
                if (client.isSelectorThread()) {
                    executor.execute(() ->
                        connectCF.completeExceptionally(cancelled));
                } else {
                    connectCF.completeExceptionally(cancelled);
                }
            }
        } else {
            connectCF = new MinimalFuture<>();
            connectCF.complete(null);
        }

        return connectCF
                .thenCompose(unused -> {
                    CompletableFuture<Void> cf = new MinimalFuture<>();
                    try {
                        asyncReceiver.whenFinished.whenComplete((r,t) -> {
                            if (t != null) {
                                if (debug.on())
                                    debug.log("asyncReceiver finished (failed=%s)", (Object)t);
                                if (!headersSentCF.isDone())
                                    headersSentCF.completeAsync(() -> this, executor);
                            }
                        });
                        connectFlows(connection);

                        if (debug.on()) debug.log("requestAction.headers");
                        List<ByteBuffer> data = requestAction.headers();
                        switchState(State.HEADERS);
                        if (debug.on()) debug.log("setting outgoing with headers");
                        assert outgoing.isEmpty() : "Unexpected outgoing:" + outgoing;
                        appendToOutgoing(data);
                        cf.complete(null);
                        return cf;
                    } catch (Throwable t) {
                        if (debug.on()) debug.log("Failed to send headers: %s", t);
                        headersSentCF.completeExceptionally(t);
                        bodySentCF.completeExceptionally(t);
                        connection.close(t);
                        cf.completeExceptionally(t);
                        return cf;
                    } })
                .thenCompose(unused -> headersSentCF);
    }

    private void cancelIfFailed(Flow.Subscription s) {
        asyncReceiver.whenFinished.whenCompleteAsync((r,t) -> {
            if (debug.on())
                debug.log("asyncReceiver finished (failed=%s)", (Object)t);
            if (t != null) {
                s.cancel();
                // Don't complete exceptionally here as 't'
                // might not be the right exception: it will
                // not have been decorated yet.
                // t is an exception raised by the read side,
                // an EOFException or Broken Pipe...
                // We are cancelling the BodyPublisher subscription
                // and completing bodySentCF to allow the next step
                // to flow and call readHeaderAsync, which will
                // get the right exception from the asyncReceiver.
                bodySentCF.complete(this);
            }
        }, executor);
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendBodyAsync() {
        assert headersSentCF.isDone();
        if (debug.on()) debug.log("sendBodyAsync");
        try {
            bodySubscriber = requestAction.continueRequest();
            if (debug.on()) debug.log("bodySubscriber is %s",
                    bodySubscriber == null ? null : bodySubscriber.getClass());
            if (bodySubscriber == null) {
                bodySubscriber = Http1RequestBodySubscriber.completeSubscriber(debug);
                appendToOutgoing(Http1RequestBodySubscriber.COMPLETED);
            } else {
                // start
                bodySubscriber.whenSubscribed
                        .thenAccept(this::cancelIfFailed)
                        .thenAccept((s) -> requestMoreBody());
            }
        } catch (Throwable t) {
            cancelImpl(t);
            bodySentCF.completeExceptionally(t);
        }
        return Utils.wrapForDebug(debug, "sendBodyAsync", bodySentCF);
    }

    @Override
    CompletableFuture<Response> getResponseAsync(Executor executor) {
        if (debug.on()) debug.log("reading headers");
        CompletableFuture<Response> cf = response.readHeadersAsync(executor);
        Throwable cause;
        lock.lock();
        try {
            operations.add(cf);
            cause = failedRef.get();
        } finally {
            lock.unlock();
        }

        if (cause != null) {
            Log.logTrace("Http1Exchange: request [{0}/timeout={1}ms]"
                            + "\n\tCompleting exceptionally with {2}\n",
                         request.uri(),
                         request.timeout().isPresent() ?
                            // calling duration.toMillis() can throw an exception.
                            // this is just debugging, we don't care if it overflows.
                            (request.timeout().get().getSeconds() * 1000
                             + request.timeout().get().getNano() / 1000000) : -1,
                         cause);
            boolean acknowledged = cf.completeExceptionally(cause);
            if (debug.on())
                debug.log(acknowledged ? ("completed response with " + cause)
                          : ("response already completed, ignoring " + cause));
        }
        return Utils.wrapForDebug(debug, "getResponseAsync", cf);
    }

    @Override
    CompletableFuture<T> readBodyAsync(BodyHandler<T> handler,
                                       boolean returnConnectionToPool,
                                       Executor executor)
    {
        var responseInfo = new ResponseInfoImpl(response.responseCode(),
                response.responseHeaders(), HTTP_1_1);
        BodySubscriber<T> bs = createResponseSubscriber(handler, responseInfo);
        CompletableFuture<T> bodyCF = response.readBody(bs,
                                                        returnConnectionToPool,
                                                        executor);
        return bodyCF;
    }

    @Override
    Http1ResponseBodySubscriber<T> createResponseSubscriber(BodyHandler<T> handler, ResponseInfo response) {
        var cancelTimerOnTermination =
                cancelTimerOnResponseBodySubscriberTermination(
                        exchange.request().isWebSocket(), response.statusCode());
        return new Http1ResponseBodySubscriber<>(handler.apply(response), cancelTimerOnTermination, this);
    }

    @Override
    CompletableFuture<Void> ignoreBody() {
        return response.ignoreBody(executor);
    }

    // Used for those response codes that have no body associated
    @Override
    public void nullBody(HttpResponse<T> resp, Throwable t) {
       response.nullBody(resp, t);
    }


    ByteBuffer drainLeftOverBytes() {
        lock.lock();
        try {
            asyncReceiver.stop();
            return asyncReceiver.drain(Utils.EMPTY_BYTEBUFFER);
        } finally {
            lock.unlock();
        }
    }

    void released() {
        Http1Response<T> resp = this.response;
        if (resp != null) resp.completed();
        asyncReceiver.clear();
    }

    void completed() {
        Http1Response<T> resp = this.response;
        if (resp != null) resp.completed();
    }

    /**
     * Cancel checks to see if request and responseAsync finished already.
     * If not it closes the connection and completes all pending operations
     */
    @Override
    void cancel() {
        cancelImpl(new IOException("Request cancelled"));
    }

    /**
     * Cancel checks to see if request and responseAsync finished already.
     * If not it closes the connection and completes all pending operations
     */
    @Override
    void cancel(IOException cause) {
        cancelImpl(cause);
    }

    @Override
    void onProtocolError(final IOException cause) {
        if (debug.on()) {
            debug.log("cancelling exchange due to protocol error: %s", cause.getMessage());
        }
        Log.logError("cancelling exchange due to protocol error: {0}\n", cause);
        cancelImpl(cause);
    }

    private void cancelImpl(Throwable cause) {
        LinkedList<CompletableFuture<?>> toComplete = null;
        int count = 0;
        Throwable error;
        BodySubscriber<?> subscriber;
        lock.lock();
        try {
            subscriber = responseSubscriber;
            failedRef.compareAndSet(null, cause);
            error = failedRef.get();
            if (debug.on()) {
                debug.log(request.uri() + ": " + error);
            }
            if (requestAction != null && requestAction.finished()
                    && response != null && response.finished()) {
                return;
            }
            writePublisher.writeScheduler.stop();
            if (operations.isEmpty()) {
                Log.logTrace("Http1Exchange: request [{0}/timeout={1}ms] no pending operation."
                                + "\n\tCan''t cancel yet with {2}",
                             request.uri(),
                             request.timeout().isPresent() ?
                                // calling duration.toMillis() can throw an exception.
                                // this is just debugging, we don't care if it overflows.
                                (request.timeout().get().getSeconds() * 1000
                                 + request.timeout().get().getNano() / 1000000) : -1,
                             cause);
            } else {
                for (CompletableFuture<?> cf : operations) {
                    if (!cf.isDone()) {
                        if (toComplete == null) toComplete = new LinkedList<>();
                        toComplete.add(cf);
                        count++;
                    }
                }
                operations.clear();
            }
        } finally {
            lock.unlock();
        }

        // complete subscriber if needed
        if (subscriber != null && error != null) {
            var failure = error;
            if (client.isSelectorThread()) {
                executor.execute(() -> subscriber.onError(failure));
            } else subscriber.onError(failure);
        }

        try {
            Log.logError("Http1Exchange.cancel: count=" + count);
            if (toComplete != null) {
                // We might be in the selector thread in case of timeout, when
                // the SelectorManager calls purgeTimeoutsAndReturnNextDeadline()
                // There may or may not be other places that reach here
                // from the SelectorManager thread, so just make sure we
                // don't complete any CF from within the selector manager
                // thread.
                Executor exec = client.isSelectorThread()
                        ? executor
                        : this::runInline;
                Throwable x = error;
                while (!toComplete.isEmpty()) {
                    CompletableFuture<?> cf = toComplete.poll();
                    exec.execute(() -> {
                        if (cf.completeExceptionally(x)) {
                            if (debug.on())
                                debug.log("%s: completed cf with %s", request.uri(), x);
                        }
                    });
                }
            }
        } finally {
            if (!upgraded)
                connection.close(error);
        }
    }

    void upgraded() {
        upgraded = true;
    }

    private void runInline(Runnable run) {
        assert !client.isSelectorThread();
        run.run();
    }

    /** Returns true if this exchange was canceled. */
    boolean isCanceled() {
        return failedRef.get() != null;
    }

    /** Returns the cause for which this exchange was canceled, if available. */
    Throwable getCancelCause() {
        return failedRef.get();
    }

    /** Convenience for {@link #appendToOutgoing(DataPair)}, with just a Throwable. */
    void appendToOutgoing(Throwable throwable) {
        appendToOutgoing(new DataPair(null, throwable));
    }

    /** Convenience for {@link #appendToOutgoing(DataPair)}, with just data. */
    void appendToOutgoing(List<ByteBuffer> item) {
        appendToOutgoing(new DataPair(item, null));
    }

    private void appendToOutgoing(DataPair dp) {
        if (debug.on()) debug.log("appending to outgoing " + dp);
        outgoing.add(dp);
        writePublisher.writeScheduler.runOrSchedule();
    }

    /** Tells whether, or not, there is any outgoing data that can be published,
     * or if there is an error. */
    private boolean hasOutgoing() {
        return !outgoing.isEmpty();
    }

    private void requestMoreBody() {
        try {
            if (debug.on()) debug.log("requesting more request body from the subscriber");
            bodySubscriber.request(1);
        } catch (Throwable t) {
            if (debug.on()) debug.log("Subscription::request failed", t);
            cancelImpl(t);
            bodySentCF.completeExceptionally(t);
        }
    }

    private void cancelUpstreamSubscription() {
        if (debug.on()) debug.log("cancelling upstream publisher");
        if (bodySubscriber != null) {
            executor.execute(bodySubscriber::cancelSubscription);
        } else if (debug.on()) {
            debug.log("bodySubscriber is null");
        }
    }

    // Invoked only by the publisher
    // ALL tasks should execute off the Selector-Manager thread
    /** Returns the next portion of the HTTP request, or the error. */
    private DataPair getOutgoing() {
        final Executor exec = executor;
        final DataPair dp = outgoing.pollFirst();

        if (writePublisher.cancelled) {
            cancelUpstreamSubscription();
            headersSentCF.completeAsync(() -> this, exec);
            bodySentCF.completeAsync(() -> this, exec);
            return null;
        }

        if (dp == null)  // publisher has not published anything yet
            return null;

        if (dp.throwable != null) {
            switchState(State.ERROR);
            exec.execute(() -> {
                headersSentCF.completeExceptionally(dp.throwable);
                bodySentCF.completeExceptionally(dp.throwable);
                connection.close(dp.throwable);
            });
            return dp;
        }

        switch (state) {
            case HEADERS:
                switchState(State.BODY);
                // completeAsync, since dependent tasks should run in another thread
                if (debug.on()) debug.log("initiating completion of headersSentCF");
                headersSentCF.completeAsync(() -> this, exec);
                break;
            case BODY:
                if (dp.data == Http1RequestBodySubscriber.COMPLETED) {
                    switchState(State.COMPLETING);
                    if (debug.on()) debug.log("initiating completion of bodySentCF");
                    bodySentCF.completeAsync(() -> this, exec);
                } else {
                    exec.execute(this::requestMoreBody);
                }
                break;
            case INITIAL:
            case ERROR:
            case COMPLETING:
            case COMPLETED:
            default:
                assert false : "Unexpected state:" + state;
        }

        return dp;
    }

    State switchState(State newState) {
        lock.lock();
        try {
            return state = newState;
        } finally {
            lock.unlock();
        }
    }

    State switchAssertState(State expected, State newState) {
        lock.lock();
        try {
            assert state == expected : "Unexpected state:" + state + ", expected: " + expected;
            return state = newState;
        } finally {
            lock.unlock();
        }
    }

    /** A Publisher of HTTP/1.1 headers and request body. */
    final class Http1Publisher implements FlowTube.TubePublisher {

        final Logger debug = Utils.getDebugLogger(this::dbgString);
        volatile Flow.Subscriber<? super List<ByteBuffer>> subscriber;
        volatile boolean cancelled;
        final Http1WriteSubscription subscription = new Http1WriteSubscription();
        final Demand demand = new Demand();
        final SequentialScheduler writeScheduler =
                SequentialScheduler.lockingScheduler(new WriteTask());

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> s) {
            assert state == State.INITIAL;
            Objects.requireNonNull(s);
            assert subscriber == null;

            subscriber = s;
            if (debug.on()) debug.log("got subscriber: %s", s);
            s.onSubscribe(subscription);
        }

        volatile String dbgTag;
        String dbgString() {
            String tag = dbgTag;
            Object flow = connection.getConnectionFlow();
            if (tag == null && flow != null) {
                dbgTag = tag = "Http1Publisher(" + flow + ")";
            } else if (tag == null) {
                tag = "Http1Publisher(?)";
            }
            return tag;
        }

        @SuppressWarnings("fallthrough")
        private boolean checkRequestCancelled() {
            if (exchange.multi.requestCancelled()) {
                if (debug.on()) debug.log("request cancelled");
                if (subscriber == null) {
                    if (debug.on()) debug.log("no subscriber yet");
                    return true;
                }
                switch (state) {
                    case BODY:
                        cancelUpstreamSubscription();
                        // fall trough to HEADERS
                    case HEADERS:
                        Throwable cause = getCancelCause();
                        if (cause == null) cause = new IOException("Request cancelled");
                        subscriber.onError(cause);
                        writeScheduler.stop();
                        return true;
                }
            }
            return false;
        }


        final class WriteTask implements Runnable {
            @Override
            public void run() {
                assert state != State.COMPLETED : "Unexpected state:" + state;
                if (debug.on()) debug.log("WriteTask for " + request);

                if (cancelled) {
                    if (debug.on()) debug.log("handling cancellation");
                    writeScheduler.stop();
                    getOutgoing();
                    return;
                }

                if (checkRequestCancelled()) return;

                if (subscriber == null) {
                    if (debug.on()) debug.log("no subscriber yet");
                    return;
                }

                if (debug.on()) debug.log(() -> "hasOutgoing = " + hasOutgoing() + ", demand = " + demand.get());
                while (hasOutgoing() && demand.tryDecrement()) {
                    DataPair dp = getOutgoing();
                    if (debug.on()) debug.log("outgoing: " + dp);
                    if (dp == null)
                        break;

                    if (dp.throwable != null) {
                        if (debug.on()) debug.log("onError");
                        // Do not call the subscriber's onError, it is not required.
                        writeScheduler.stop();
                    } else {
                        List<ByteBuffer> data = dp.data;
                        if (data == Http1RequestBodySubscriber.COMPLETED) {
                            switchAssertState(State.COMPLETING, State.COMPLETED);
                            if (debug.on())
                                debug.log("completed, stopping %s", writeScheduler);
                            writeScheduler.stop();
                            // Do nothing more. Just do not publish anything further.
                            // The next Subscriber will eventually take over.

                        } else {
                            if (checkRequestCancelled()) {
                                if (debug.on()) debug.log("Request cancelled!");
                                return;
                            }
                            if (debug.on())
                                debug.log("onNext with " + Utils.remaining(data) + " bytes");
                            subscriber.onNext(data);
                        }
                    }
                }
            }
        }

        final class Http1WriteSubscription implements Flow.Subscription {

            @Override
            public void request(long n) {
                if (cancelled)
                    return;  //no-op
                demand.increase(n);
                if (debug.on())
                    debug.log("subscription request(%d), demand=%s", n, demand);
                writeScheduler.runOrSchedule(executor);
            }

            @Override
            public void cancel() {
                if (debug.on()) debug.log("subscription cancelled for " + request);
                if (cancelled)
                    return;  //no-op
                cancelled = true;
                writeScheduler.runOrSchedule(executor);
            }
        }
    }

    @Override
    final HttpClientImpl client() {
        return client;
    }

    String dbgString() {
        return "Http1Exchange";
    }

    @Override
    void expectContinueFailed(int rcode) {
        var response = this.response;
        if (response != null) {
            // Sets a flag which closes the connection locally when
            // onFinished() is called
            response.closeWhenFinished();
        }
    }
}
