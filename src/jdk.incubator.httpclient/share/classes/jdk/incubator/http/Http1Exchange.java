/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.net.InetSocketAddress;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import jdk.incubator.http.internal.common.Demand;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.FlowTube;
import jdk.incubator.http.internal.common.SequentialScheduler;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;
import static jdk.incubator.http.HttpClient.Version.HTTP_1_1;

/**
 * Encapsulates one HTTP/1.1 request/response exchange.
 */
class Http1Exchange<T> extends ExchangeImpl<T> {

    static final boolean DEBUG = Utils.DEBUG; // Revisit: temporary dev flag.
    final System.Logger  debug = Utils.getDebugLogger(this::dbgString, DEBUG);
    private static final System.Logger DEBUG_LOGGER =
            Utils.getDebugLogger("Http1Exchange"::toString, DEBUG);

    final HttpRequestImpl request; // main request
    final Http1Request requestAction;
    private volatile Http1Response<T> response;
    final HttpConnection connection;
    final HttpClientImpl client;
    final Executor executor;
    private final Http1AsyncReceiver asyncReceiver;

    /** Records a possible cancellation raised before any operation
     * has been initiated, or an error received while sending the request. */
    private Throwable failed;
    private final List<CompletableFuture<?>> operations; // used for cancel

    /** Must be held when operating on any internal state or data. */
    private final Object lock = new Object();

    /** Holds the outgoing data, either the headers or a request body part. Or
     * an error from the request body publisher. At most there can be ~2 pieces
     * of outgoing data ( onComplete|onError can be invoked without demand ).*/
    final ConcurrentLinkedDeque<DataPair> outgoing = new ConcurrentLinkedDeque<>();

    /** The write publisher, responsible for writing the complete request ( both
     * headers and body ( if any ). */
    private final Http1Publisher writePublisher = new Http1Publisher();

    /** Completed when the header have been published, or there is an error */
    private volatile CompletableFuture<ExchangeImpl<T>> headersSentCF  = new MinimalFuture<>();
     /** Completed when the body has been published, or there is an error */
    private volatile CompletableFuture<ExchangeImpl<T>> bodySentCF = new MinimalFuture<>();

    /** The subscriber to the request's body published. Maybe null. */
    private volatile Http1BodySubscriber bodySubscriber;

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
    static abstract class Http1BodySubscriber implements Flow.Subscriber<ByteBuffer> {
        protected volatile Flow.Subscription subscription;
        protected volatile boolean complete;

        /** Final sentinel in the stream of request body. */
        static final List<ByteBuffer> COMPLETED = List.of(ByteBuffer.allocate(0));

        void request(long n) {
            DEBUG_LOGGER.log(Level.DEBUG, () ->
                "Http1BodySubscriber requesting " + n + ", from " + subscription);
            subscription.request(n);
        }

        static Http1BodySubscriber completeSubscriber() {
            return new Http1BodySubscriber() {
                @Override public void onSubscribe(Flow.Subscription subscription) { error(); }
                @Override public void onNext(ByteBuffer item) { error(); }
                @Override public void onError(Throwable throwable) { error(); }
                @Override public void onComplete() { error(); }
                private void error() {
                    throw new InternalError("should not reach here");
                }
            };
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
        this.operations = new LinkedList<>();
        operations.add(headersSentCF);
        operations.add(bodySentCF);
        if (connection != null) {
            this.connection = connection;
        } else {
            InetSocketAddress addr = request.getAddress();
            this.connection = HttpConnection.getConnection(addr, client, request, HTTP_1_1);
        }
        this.requestAction = new Http1Request(request, this);
        this.asyncReceiver = new Http1AsyncReceiver(executor, this);
        asyncReceiver.subscribe(new InitialErrorReceiver());
    }

    /** An initial receiver that handles no data, but cancels the request if
     * it receives an error. Will be replaced when reading response body. */
    final class InitialErrorReceiver implements Http1AsyncReceiver.Http1AsyncDelegate {
        volatile AbstractSubscription s;
        @Override
        public boolean tryAsyncReceive(ByteBuffer ref) {
            return false;  // no data has been processed, leave it in the queue
        }

        @Override
        public void onReadError(Throwable ex) {
            cancelImpl(ex);
        }

        @Override
        public void onSubscribe(AbstractSubscription s) {
            this.s = s;
        }

        public AbstractSubscription subscription() {
            return s;
        }
    }

    @Override
    HttpConnection connection() {
        return connection;
    }

    private void connectFlows(HttpConnection connection) {
        FlowTube tube =  connection.getConnectionFlow();
        debug.log(Level.DEBUG, "%s connecting flows", tube);

        // Connect the flow to our Http1TubeSubscriber:
        //   asyncReceiver.subscriber().
        tube.connectFlows(writePublisher,
                          asyncReceiver.subscriber());
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendHeadersAsync() {
        // create the response before sending the request headers, so that
        // the response can set the appropriate receivers.
        debug.log(Level.DEBUG, "Sending headers only");
        if (response == null) {
            response = new Http1Response<>(connection, this, asyncReceiver);
        }

        debug.log(Level.DEBUG, "response created in advance");
        // If the first attempt to read something triggers EOF, or
        // IOException("channel reset by peer"), we're going to retry.
        // Instruct the asyncReceiver to throw ConnectionExpiredException
        // to force a retry.
        asyncReceiver.setRetryOnError(true);

        CompletableFuture<Void> connectCF;
        if (!connection.connected()) {
            debug.log(Level.DEBUG, "initiating connect async");
            connectCF = connection.connectAsync();
            synchronized (lock) {
                operations.add(connectCF);
            }
        } else {
            connectCF = new MinimalFuture<>();
            connectCF.complete(null);
        }

        return connectCF
                .thenCompose(unused -> {
                    CompletableFuture<Void> cf = new MinimalFuture<>();
                    try {
                        connectFlows(connection);

                        debug.log(Level.DEBUG, "requestAction.headers");
                        List<ByteBuffer> data = requestAction.headers();
                        synchronized (lock) {
                            state = State.HEADERS;
                        }
                        debug.log(Level.DEBUG, "setting outgoing with headers");
                        assert outgoing.isEmpty() : "Unexpected outgoing:" + outgoing;
                        appendToOutgoing(data);
                        cf.complete(null);
                        return cf;
                    } catch (Throwable t) {
                        debug.log(Level.DEBUG, "Failed to send headers: %s", t);
                        connection.close();
                        cf.completeExceptionally(t);
                        return cf;
                    } })
                .thenCompose(unused -> headersSentCF);
    }

    @Override
    CompletableFuture<ExchangeImpl<T>> sendBodyAsync() {
        assert headersSentCF.isDone();
        try {
            bodySubscriber = requestAction.continueRequest();
            if (bodySubscriber == null) {
                bodySubscriber = Http1BodySubscriber.completeSubscriber();
                appendToOutgoing(Http1BodySubscriber.COMPLETED);
            } else {
                bodySubscriber.request(1);  // start
            }
        } catch (Throwable t) {
            connection.close();
            bodySentCF.completeExceptionally(t);
        }
        return bodySentCF;
    }

    @Override
    CompletableFuture<Response> getResponseAsync(Executor executor) {
        CompletableFuture<Response> cf = response.readHeadersAsync(executor);
        Throwable cause;
        synchronized (lock) {
            operations.add(cf);
            cause = failed;
            failed = null;
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
            debug.log(Level.DEBUG,
                      () -> acknowledged
                            ? ("completed response with " + cause)
                            : ("response already completed, ignoring " + cause));
        }
        return cf;
    }

    @Override
    CompletableFuture<T> readBodyAsync(BodyHandler<T> handler,
                                       boolean returnConnectionToPool,
                                       Executor executor)
    {
        BodySubscriber<T> bs = handler.apply(response.responseCode(),
                                             response.responseHeaders());
        CompletableFuture<T> bodyCF = response.readBody(bs,
                                                        returnConnectionToPool,
                                                        executor);
        return bodyCF;
    }

    @Override
    CompletableFuture<Void> ignoreBody() {
        return response.ignoreBody(executor);
    }

    ByteBuffer drainLeftOverBytes() {
        synchronized (lock) {
            asyncReceiver.stop();
            return asyncReceiver.drain(Utils.EMPTY_BYTEBUFFER);
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

    private void cancelImpl(Throwable cause) {
        LinkedList<CompletableFuture<?>> toComplete = null;
        int count = 0;
        synchronized (lock) {
            if (failed == null)
                failed = cause;
            if (requestAction != null && requestAction.finished()
                    && response != null && response.finished()) {
                return;
            }
            connection.close();   // TODO: ensure non-blocking if holding the lock
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
        }
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
            while (!toComplete.isEmpty()) {
                CompletableFuture<?> cf = toComplete.poll();
                exec.execute(() -> {
                    if (cf.completeExceptionally(cause)) {
                        debug.log(Level.DEBUG, "completed cf with %s",
                                 (Object) cause);
                    }
                });
            }
        }
    }

    private void runInline(Runnable run) {
        assert !client.isSelectorThread();
        run.run();
    }

    /** Returns true if this exchange was canceled. */
    boolean isCanceled() {
        synchronized (lock) {
            return failed != null;
        }
    }

    /** Returns the cause for which this exchange was canceled, if available. */
    Throwable getCancelCause() {
        synchronized (lock) {
            return failed;
        }
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
        debug.log(Level.DEBUG, "appending to outgoing " + dp);
        outgoing.add(dp);
        writePublisher.writeScheduler.runOrSchedule();
    }

    /** Tells whether, or not, there is any outgoing data that can be published,
     * or if there is an error. */
    private boolean hasOutgoing() {
        return !outgoing.isEmpty();
    }

    // Invoked only by the publisher
    // ALL tasks should execute off the Selector-Manager thread
    /** Returns the next portion of the HTTP request, or the error. */
    private DataPair getOutgoing() {
        final Executor exec = client.theExecutor();
        final DataPair dp = outgoing.pollFirst();

        if (dp == null)  // publisher has not published anything yet
            return null;

        synchronized (lock) {
            if (dp.throwable != null) {
                state = State.ERROR;
                exec.execute(() -> {
                    connection.close();
                    headersSentCF.completeExceptionally(dp.throwable);
                    bodySentCF.completeExceptionally(dp.throwable);
                });
                return dp;
            }

            switch (state) {
                case HEADERS:
                    state = State.BODY;
                    // completeAsync, since dependent tasks should run in another thread
                    debug.log(Level.DEBUG, "initiating completion of headersSentCF");
                    headersSentCF.completeAsync(() -> this, exec);
                    break;
                case BODY:
                    if (dp.data == Http1BodySubscriber.COMPLETED) {
                        state = State.COMPLETING;
                        debug.log(Level.DEBUG, "initiating completion of bodySentCF");
                        bodySentCF.completeAsync(() -> this, exec);
                    } else {
                        debug.log(Level.DEBUG, "requesting more body from the subscriber");
                        exec.execute(() -> bodySubscriber.request(1));
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
    }

    /** A Publisher of HTTP/1.1 headers and request body. */
    final class Http1Publisher implements FlowTube.TubePublisher {

        final System.Logger  debug = Utils.getDebugLogger(this::dbgString);
        volatile Flow.Subscriber<? super List<ByteBuffer>> subscriber;
        volatile boolean cancelled;
        final Http1WriteSubscription subscription = new Http1WriteSubscription();
        final Demand demand = new Demand();
        final SequentialScheduler writeScheduler =
                SequentialScheduler.synchronizedScheduler(new WriteTask());

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> s) {
            assert state == State.INITIAL;
            Objects.requireNonNull(s);
            assert subscriber == null;

            subscriber = s;
            debug.log(Level.DEBUG, "got subscriber: %s", s);
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

        final class WriteTask implements Runnable {
            @Override
            public void run() {
                assert state != State.COMPLETED : "Unexpected state:" + state;
                debug.log(Level.DEBUG, "WriteTask");
                if (subscriber == null) {
                    debug.log(Level.DEBUG, "no subscriber yet");
                    return;
                }
                debug.log(Level.DEBUG, () -> "hasOutgoing = " + hasOutgoing());
                while (hasOutgoing() && demand.tryDecrement()) {
                    DataPair dp = getOutgoing();

                    if (dp.throwable != null) {
                        debug.log(Level.DEBUG, "onError");
                        // Do not call the subscriber's onError, it is not required.
                        writeScheduler.stop();
                    } else {
                        List<ByteBuffer> data = dp.data;
                        if (data == Http1BodySubscriber.COMPLETED) {
                            synchronized (lock) {
                                assert state == State.COMPLETING : "Unexpected state:" + state;
                                state = State.COMPLETED;
                            }
                            debug.log(Level.DEBUG,
                                     "completed, stopping %s", writeScheduler);
                            writeScheduler.stop();
                            // Do nothing more. Just do not publish anything further.
                            // The next Subscriber will eventually take over.

                        } else {
                            debug.log(Level.DEBUG, () ->
                                    "onNext with " + Utils.remaining(data) + " bytes");
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
                debug.log(Level.DEBUG,
                        "subscription request(%d), demand=%s", n, demand);
                writeScheduler.deferOrSchedule(client.theExecutor());
            }

            @Override
            public void cancel() {
                debug.log(Level.DEBUG, "subscription cancelled");
                if (cancelled)
                    return;  //no-op
                cancelled = true;
                writeScheduler.stop();
            }
        }
    }

    String dbgString() {
        return "Http1Exchange";
    }
}
