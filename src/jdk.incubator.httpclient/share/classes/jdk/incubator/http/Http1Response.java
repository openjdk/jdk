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

import java.io.EOFException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.incubator.http.ResponseContent.BodyParser;
import jdk.incubator.http.internal.common.Log;
import static jdk.incubator.http.HttpClient.Version.HTTP_1_1;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;

/**
 * Handles a HTTP/1.1 response (headers + body).
 * There can be more than one of these per Http exchange.
 */
class Http1Response<T> {

    private volatile ResponseContent content;
    private final HttpRequestImpl request;
    private Response response;
    private final HttpConnection connection;
    private HttpHeaders headers;
    private int responseCode;
    private final Http1Exchange<T> exchange;
    private boolean return2Cache; // return connection to cache when finished
    private final HeadersReader headersReader; // used to read the headers
    private final BodyReader bodyReader; // used to read the body
    private final Http1AsyncReceiver asyncReceiver;
    private volatile EOFException eof;
    // max number of bytes of (fixed length) body to ignore on redirect
    private final static int MAX_IGNORE = 1024;

    // Revisit: can we get rid of this?
    static enum State {INITIAL, READING_HEADERS, READING_BODY, DONE}
    private volatile State readProgress = State.INITIAL;
    static final boolean DEBUG = Utils.DEBUG; // Revisit: temporary dev flag.
    final System.Logger  debug = Utils.getDebugLogger(this.getClass()::getSimpleName, DEBUG);


    Http1Response(HttpConnection conn,
                  Http1Exchange<T> exchange,
                  Http1AsyncReceiver asyncReceiver) {
        this.readProgress = State.INITIAL;
        this.request = exchange.request();
        this.exchange = exchange;
        this.connection = conn;
        this.asyncReceiver = asyncReceiver;
        headersReader = new HeadersReader(this::advance);
        bodyReader = new BodyReader(this::advance);
    }

   public CompletableFuture<Response> readHeadersAsync(Executor executor) {
        debug.log(Level.DEBUG, () -> "Reading Headers: (remaining: "
                + asyncReceiver.remaining() +") "  + readProgress);
        // with expect continue we will resume reading headers + body.
        asyncReceiver.unsubscribe(bodyReader);
        bodyReader.reset();
        Http1HeaderParser hd = new Http1HeaderParser();
        readProgress = State.READING_HEADERS;
        headersReader.start(hd);
        asyncReceiver.subscribe(headersReader);
        CompletableFuture<State> cf = headersReader.completion();
        assert cf != null : "parsing not started";

        Function<State, Response> lambda = (State completed) -> {
                assert completed == State.READING_HEADERS;
                debug.log(Level.DEBUG, () ->
                            "Reading Headers: creating Response object;"
                            + " state is now " + readProgress);
                asyncReceiver.unsubscribe(headersReader);
                responseCode = hd.responseCode();
                headers = hd.headers();

                response = new Response(request,
                                        exchange.getExchange(),
                                        headers,
                                        responseCode,
                                        HTTP_1_1);
                return response;
            };

        if (executor != null) {
            return cf.thenApplyAsync(lambda, executor);
        } else {
            return cf.thenApply(lambda);
        }
    }

    private boolean finished;

    synchronized void completed() {
        finished = true;
    }

    synchronized boolean finished() {
        return finished;
    }

    int fixupContentLen(int clen) {
        if (request.method().equalsIgnoreCase("HEAD")) {
            return 0;
        }
        if (clen == -1) {
            if (headers.firstValue("Transfer-encoding").orElse("")
                       .equalsIgnoreCase("chunked")) {
                return -1;
            }
            return 0;
        }
        return clen;
    }

    /**
     * Read up to MAX_IGNORE bytes discarding
     */
    public CompletableFuture<Void> ignoreBody(Executor executor) {
        int clen = (int)headers.firstValueAsLong("Content-Length").orElse(-1);
        if (clen == -1 || clen > MAX_IGNORE) {
            connection.close();
            return MinimalFuture.completedFuture(null); // not treating as error
        } else {
            return readBody(HttpResponse.BodySubscriber.discard((Void)null), true, executor);
        }
    }

    public <U> CompletableFuture<U> readBody(HttpResponse.BodySubscriber<U> p,
                                         boolean return2Cache,
                                         Executor executor) {
        this.return2Cache = return2Cache;
        final HttpResponse.BodySubscriber<U> pusher = p;
        final CompletionStage<U> bodyCF = p.getBody();
        final CompletableFuture<U> cf = MinimalFuture.of(bodyCF);

        int clen0 = (int)headers.firstValueAsLong("Content-Length").orElse(-1);

        final int clen = fixupContentLen(clen0);

        // expect-continue reads headers and body twice.
        // if we reach here, we must reset the headersReader state.
        asyncReceiver.unsubscribe(headersReader);
        headersReader.reset();

        executor.execute(() -> {
            try {
                HttpClientImpl client = connection.client();
                content = new ResponseContent(
                        connection, clen, headers, pusher,
                        this::onFinished
                );
                if (cf.isCompletedExceptionally()) {
                    // if an error occurs during subscription
                    connection.close();
                    return;
                }
                // increment the reference count on the HttpClientImpl
                // to prevent the SelectorManager thread from exiting until
                // the body is fully read.
                client.reference();
                bodyReader.start(content.getBodyParser(
                    (t) -> {
                        try {
                            if (t != null) {
                                pusher.onError(t);
                                connection.close();
                                if (!cf.isDone())
                                    cf.completeExceptionally(t);
                            }
                        } finally {
                            // decrement the reference count on the HttpClientImpl
                            // to allow the SelectorManager thread to exit if no
                            // other operation is pending and the facade is no
                            // longer referenced.
                            client.unreference();
                            bodyReader.onComplete(t);
                        }
                    }));
                CompletableFuture<State> bodyReaderCF = bodyReader.completion();
                asyncReceiver.subscribe(bodyReader);
                assert bodyReaderCF != null : "parsing not started";
                // Make sure to keep a reference to asyncReceiver from
                // within this
                CompletableFuture<?> trailingOp = bodyReaderCF.whenComplete((s,t) ->  {
                    t = Utils.getCompletionCause(t);
                    try {
                        if (t != null) {
                            debug.log(Level.DEBUG, () ->
                                    "Finished reading body: " + s);
                            assert s == State.READING_BODY;
                        }
                        if (t != null && !cf.isDone()) {
                            pusher.onError(t);
                            cf.completeExceptionally(t);
                        }
                    } catch (Throwable x) {
                        // not supposed to happen
                        asyncReceiver.onReadError(x);
                    }
                });
                connection.addTrailingOperation(trailingOp);
            } catch (Throwable t) {
               debug.log(Level.DEBUG, () -> "Failed reading body: " + t);
                try {
                    if (!cf.isDone()) {
                        pusher.onError(t);
                        cf.completeExceptionally(t);
                    }
                } finally {
                    asyncReceiver.onReadError(t);
                }
            }
        });
        return cf;
    }


    private void onFinished() {
        asyncReceiver.clear();
        if (return2Cache) {
            Log.logTrace("Attempting to return connection to the pool: {0}", connection);
            // TODO: need to do something here?
            // connection.setAsyncCallbacks(null, null, null);

            // don't return the connection to the cache if EOF happened.
            debug.log(Level.DEBUG, () -> connection.getConnectionFlow()
                                   + ": return to HTTP/1.1 pool");
            connection.closeOrReturnToCache(eof == null ? headers : null);
        }
    }

    HttpHeaders responseHeaders() {
        return headers;
    }

    int responseCode() {
        return responseCode;
    }

// ================ Support for plugging into Http1Receiver   =================
// ============================================================================

    // Callback: Error receiver: Consumer of Throwable.
    void onReadError(Throwable t) {
        Log.logError(t);
        Receiver<?> receiver = receiver(readProgress);
        if (t instanceof EOFException) {
            debug.log(Level.DEBUG, "onReadError: received EOF");
            eof = (EOFException) t;
        }
        CompletableFuture<?> cf = receiver == null ? null : receiver.completion();
        debug.log(Level.DEBUG, () -> "onReadError: cf is "
                + (cf == null  ? "null"
                : (cf.isDone() ? "already completed"
                               : "not yet completed")));
        if (cf != null && !cf.isDone()) cf.completeExceptionally(t);
        else { debug.log(Level.DEBUG, "onReadError", t); }
        debug.log(Level.DEBUG, () -> "closing connection: cause is " + t);
        connection.close();
    }

    // ========================================================================

    private State advance(State previous) {
        assert readProgress == previous;
        switch(previous) {
            case READING_HEADERS:
                asyncReceiver.unsubscribe(headersReader);
                return readProgress = State.READING_BODY;
            case READING_BODY:
                asyncReceiver.unsubscribe(bodyReader);
                return readProgress = State.DONE;
            default:
                throw new InternalError("can't advance from " + previous);
        }
    }

    Receiver<?> receiver(State state) {
        switch(state) {
            case READING_HEADERS: return headersReader;
            case READING_BODY: return bodyReader;
            default: return null;
        }

    }

    static abstract class Receiver<T>
            implements Http1AsyncReceiver.Http1AsyncDelegate {
        abstract void start(T parser);
        abstract CompletableFuture<State> completion();
        // accepts a buffer from upstream.
        // this should be implemented as a simple call to
        // accept(ref, parser, cf)
        public abstract boolean tryAsyncReceive(ByteBuffer buffer);
        public abstract void onReadError(Throwable t);
        // handle a byte buffer received from upstream.
        // this method should set the value of Http1Response.buffer
        // to ref.get() before beginning parsing.
        abstract void handle(ByteBuffer buf, T parser,
                             CompletableFuture<State> cf);
        // resets this objects state so that it can be reused later on
        // typically puts the reference to parser and completion to null
        abstract void reset();

        // accepts a byte buffer received from upstream
        // returns true if the buffer is fully parsed and more data can
        // be accepted, false otherwise.
        final boolean accept(ByteBuffer buf, T parser,
                CompletableFuture<State> cf) {
            if (cf == null || parser == null || cf.isDone()) return false;
            handle(buf, parser, cf);
            return !cf.isDone();
        }
        public abstract void onSubscribe(AbstractSubscription s);
        public abstract AbstractSubscription subscription();

    }

    // Invoked with each new ByteBuffer when reading headers...
    final class HeadersReader extends Receiver<Http1HeaderParser> {
        final Consumer<State> onComplete;
        volatile Http1HeaderParser parser;
        volatile CompletableFuture<State> cf;
        volatile long count; // bytes parsed (for debug)
        volatile AbstractSubscription subscription;

        HeadersReader(Consumer<State> onComplete) {
            this.onComplete = onComplete;
        }

        @Override
        public AbstractSubscription subscription() {
            return subscription;
        }

        @Override
        public void onSubscribe(AbstractSubscription s) {
            this.subscription = s;
            s.request(1);
        }

        @Override
        void reset() {
            cf = null;
            parser = null;
            count = 0;
            subscription = null;
        }

        // Revisit: do we need to support restarting?
        @Override
        final void start(Http1HeaderParser hp) {
            count = 0;
            cf = new MinimalFuture<>();
            parser = hp;
        }

        @Override
        CompletableFuture<State> completion() {
            return cf;
        }

        @Override
        public final boolean tryAsyncReceive(ByteBuffer ref) {
            boolean hasDemand = subscription.demand().tryDecrement();
            assert hasDemand;
            boolean needsMore = accept(ref, parser, cf);
            if (needsMore) subscription.request(1);
            return needsMore;
        }

        @Override
        public final void onReadError(Throwable t) {
            Http1Response.this.onReadError(t);
        }

        @Override
        final void handle(ByteBuffer b,
                          Http1HeaderParser parser,
                          CompletableFuture<State> cf) {
            assert cf != null : "parsing not started";
            assert parser != null : "no parser";
            try {
                count += b.remaining();
                debug.log(Level.DEBUG, () -> "Sending " + b.remaining()
                        + "/" + b.capacity() + " bytes to header parser");
                if (parser.parse(b)) {
                    count -= b.remaining();
                    debug.log(Level.DEBUG, () ->
                            "Parsing headers completed. bytes=" + count);
                    onComplete.accept(State.READING_HEADERS);
                    cf.complete(State.READING_HEADERS);
                }
            } catch (Throwable t) {
                debug.log(Level.DEBUG,
                        () -> "Header parser failed to handle buffer: " + t);
                cf.completeExceptionally(t);
            }
        }
    }

    // Invoked with each new ByteBuffer when reading bodies...
    final class BodyReader extends Receiver<BodyParser> {
        final Consumer<State> onComplete;
        volatile BodyParser parser;
        volatile CompletableFuture<State> cf;
        volatile AbstractSubscription subscription;
        BodyReader(Consumer<State> onComplete) {
            this.onComplete = onComplete;
        }

        @Override
        void reset() {
            parser = null;
            cf = null;
            subscription = null;
        }

        // Revisit: do we need to support restarting?
        @Override
        final void start(BodyParser parser) {
            cf = new MinimalFuture<>();
            this.parser = parser;
        }

        @Override
        CompletableFuture<State> completion() {
            return cf;
        }

        @Override
        public final boolean tryAsyncReceive(ByteBuffer b) {
            return accept(b, parser, cf);
        }

        @Override
        public final void onReadError(Throwable t) {
            Http1Response.this.onReadError(t);
        }

        @Override
        public AbstractSubscription subscription() {
            return subscription;
        }

        @Override
        public void onSubscribe(AbstractSubscription s) {
            this.subscription = s;
            parser.onSubscribe(s);
        }

        @Override
        final void handle(ByteBuffer b,
                          BodyParser parser,
                          CompletableFuture<State> cf) {
            assert cf != null : "parsing not started";
            assert parser != null : "no parser";
            try {
                debug.log(Level.DEBUG, () -> "Sending " + b.remaining()
                        + "/" + b.capacity() + " bytes to body parser");
                parser.accept(b);
            } catch (Throwable t) {
                debug.log(Level.DEBUG,
                        () -> "Body parser failed to handle buffer: " + t);
                if (!cf.isDone()) {
                    cf.completeExceptionally(t);
                }
            }
        }

        final void onComplete(Throwable closedExceptionally) {
            if (cf.isDone()) return;
            if (closedExceptionally != null) {
                cf.completeExceptionally(closedExceptionally);
            } else {
                onComplete.accept(State.READING_BODY);
                cf.complete(State.READING_BODY);
            }
        }

        @Override
        public String toString() {
            return super.toString() + "/parser=" + String.valueOf(parser);
        }

    }
}
