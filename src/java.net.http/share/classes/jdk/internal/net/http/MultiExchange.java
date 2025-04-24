/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOError;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.net.http.HttpTimeoutException;
import jdk.internal.net.http.common.Cancelable;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.ConnectionExpiredException;
import jdk.internal.net.http.common.Utils;
import static jdk.internal.net.http.common.MinimalFuture.completedFuture;
import static jdk.internal.net.http.common.MinimalFuture.failedFuture;

/**
 * Encapsulates multiple Exchanges belonging to one HttpRequestImpl.
 * - manages filters
 * - retries due to filters.
 * - I/O errors and most other exceptions get returned directly to user
 *
 * Creates a new Exchange for each request/response interaction
 */
class MultiExchange<T> implements Cancelable {

    static final Logger debug =
            Utils.getDebugLogger("MultiExchange"::toString, Utils.DEBUG);

    private final HttpRequest userRequest; // the user request
    private final HttpRequestImpl request; // a copy of the user request
    private final ConnectTimeoutTracker connectTimeout; // null if no timeout
    final HttpClientImpl client;
    final HttpResponse.BodyHandler<T> responseHandler;
    final HttpClientImpl.DelegatingExecutor executor;
    final AtomicInteger attempts = new AtomicInteger();
    HttpRequestImpl currentreq; // used for retries & redirect
    HttpRequestImpl previousreq; // used for retries & redirect
    Exchange<T> exchange; // the current exchange
    Exchange<T> previous;
    volatile Throwable retryCause;
    volatile boolean retriedOnce;
    volatile HttpResponse<T> response;

    // Maximum number of times a request will be retried/redirected
    // for any reason
    static final int DEFAULT_MAX_ATTEMPTS = 5;
    static final int max_attempts = Utils.getIntegerNetProperty(
            "jdk.httpclient.redirects.retrylimit", DEFAULT_MAX_ATTEMPTS
    );

    private final List<HeaderFilter> filters;
    volatile ResponseTimerEvent responseTimerEvent;
    volatile boolean cancelled;
    AtomicReference<CancellationException> interrupted = new AtomicReference<>();
    final PushGroup<T> pushGroup;

    /**
     * Filter fields. These are attached as required by filters
     * and only used by the filter implementations. This could be
     * generalised into Objects that are passed explicitly to the filters
     * (one per MultiExchange object, and one per Exchange object possibly)
     */
    volatile AuthenticationFilter.AuthInfo serverauth, proxyauth;
    // RedirectHandler
    volatile int numberOfRedirects = 0;

    // This class is used to keep track of the connection timeout
    // across retries, when a ConnectException causes a retry.
    // In that case - we will retry the connect, but we don't
    // want to double the timeout by starting a new timer with
    // the full connectTimeout again.
    // Instead we use the ConnectTimeoutTracker to return a new
    // duration that takes into account the time spent in the
    // first connect attempt.
    // If however, the connection gets connected, but we later
    // retry the whole operation, then we reset the timer before
    // retrying (since the connection used for the second request
    // will not necessarily be the same: it could be a new
    // unconnected connection) - see getExceptionalCF().
    private static final class ConnectTimeoutTracker {
        final Duration max;
        final AtomicLong startTime = new AtomicLong();
        ConnectTimeoutTracker(Duration connectTimeout) {
            this.max = Objects.requireNonNull(connectTimeout);
        }

        Duration getRemaining() {
            long now = System.nanoTime();
            long previous = startTime.compareAndExchange(0, now);
            if (previous == 0 || max.isZero()) return max;
            Duration remaining = max.minus(Duration.ofNanos(now - previous));
            assert remaining.compareTo(max) <= 0;
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }

        void reset() { startTime.set(0); }
    }

    /**
     * MultiExchange with one final response.
     */
    MultiExchange(HttpRequest userRequest,
                  HttpRequestImpl requestImpl,
                  HttpClientImpl client,
                  HttpResponse.BodyHandler<T> responseHandler,
                  PushPromiseHandler<T> pushPromiseHandler) {
        this.previous = null;
        this.userRequest = userRequest;
        this.request = requestImpl;
        this.currentreq = request;
        this.previousreq = null;
        this.client = client;
        this.filters = client.filterChain();
        this.executor = client.theExecutor();
        this.responseHandler = responseHandler;

        if (pushPromiseHandler != null) {
            Executor executor = this.executor::ensureExecutedAsync;
            this.pushGroup = new PushGroup<>(pushPromiseHandler, request, executor);
        } else {
            pushGroup = null;
        }
        this.connectTimeout = client.connectTimeout()
                .map(ConnectTimeoutTracker::new).orElse(null);
        this.exchange = new Exchange<>(request, this);
    }

    static final class CancelableRef implements Cancelable {
        private final WeakReference<Cancelable> cancelableRef;
        CancelableRef(Cancelable cancelable) {
            cancelableRef = new WeakReference<>(cancelable);
        }
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            Cancelable cancelable = cancelableRef.get();
            if (cancelable != null) {
                return cancelable.cancel(mayInterruptIfRunning);
            } else return false;
        }
    }

    synchronized Exchange<T> getExchange() {
        return exchange;
    }

    HttpClientImpl client() {
        return client;
    }

    HttpClient.Version version() {
        HttpClient.Version vers = request.version().orElse(client.version());
        if (vers == HttpClient.Version.HTTP_2 && !request.secure() && request.proxy() != null)
            vers = HttpClient.Version.HTTP_1_1;
        return vers;
    }

    private void setExchange(Exchange<T> exchange) {
        Exchange<T> previousExchange;
        synchronized (this) {
            previousExchange = this.exchange;
            this.exchange = exchange;
        }
        if (previousExchange != null && exchange != previousExchange) {
            previousExchange.released();
        }
        if (cancelled) exchange.cancel();
    }

    public Optional<Duration> remainingConnectTimeout() {
        return Optional.ofNullable(connectTimeout)
                .map(ConnectTimeoutTracker::getRemaining);
    }

    private void cancelTimer() {
        if (responseTimerEvent != null) {
            client.cancelTimer(responseTimerEvent);
            responseTimerEvent = null;
        }
    }

    private void requestFilters(HttpRequestImpl r) throws IOException {
        Log.logTrace("Applying request filters");
        for (HeaderFilter filter : filters) {
            Log.logTrace("Applying {0}", filter);
            filter.request(r, this);
        }
        Log.logTrace("All filters applied");
    }

    private HttpRequestImpl responseFilters(Response response) throws IOException
    {
        Log.logTrace("Applying response filters");
        ListIterator<HeaderFilter> reverseItr = filters.listIterator(filters.size());
        while (reverseItr.hasPrevious()) {
            HeaderFilter filter = reverseItr.previous();
            Log.logTrace("Applying {0}", filter);
            HttpRequestImpl newreq = filter.response(response);
            if (newreq != null) {
                Log.logTrace("New request: stopping filters");
                return newreq;
            }
        }
        Log.logTrace("All filters applied");
        return null;
    }

    public void cancel(IOException cause) {
        cancelled = true;
        getExchange().cancel(cause);
    }

    /**
     * Used to relay a call from {@link CompletableFuture#cancel(boolean)}
     * to this multi exchange for the purpose of cancelling the
     * HTTP exchange.
     * @param mayInterruptIfRunning if true, and this exchange is not already
     *        cancelled, this method will attempt to interrupt and cancel the
     *        exchange. Otherwise, the exchange is allowed to proceed and this
     *        method does nothing.
     * @return true if the exchange was cancelled, false otherwise.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = this.cancelled;
        boolean firstCancel = false;
        if (!cancelled && mayInterruptIfRunning) {
            if (interrupted.get() == null) {
                firstCancel = interrupted.compareAndSet(null,
                        new CancellationException("Request cancelled"));
            }
            if (debug.on()) {
                if (firstCancel) {
                    debug.log("multi exchange recording: " + interrupted.get());
                } else {
                    debug.log("multi exchange recorded: " + interrupted.get());
                }
            }
            this.cancelled = true;
            var exchange = getExchange();
            if (exchange != null) {
                exchange.cancel();
            }
            return true;
        } else {
            if (cancelled) {
                debug.log("multi exchange already cancelled: " + interrupted.get());
            } else {
                debug.log("multi exchange mayInterruptIfRunning=" + mayInterruptIfRunning);
            }
        }
        return false;
    }

    public <U> MinimalFuture<U> newMinimalFuture() {
        return new MinimalFuture<>(new CancelableRef(this));
    }

    public CompletableFuture<HttpResponse<T>> responseAsync(Executor executor) {
        CompletableFuture<Void> start = newMinimalFuture();
        CompletableFuture<HttpResponse<T>> cf = responseAsync0(start);
        start.completeAsync( () -> null, executor); // trigger execution
        return cf;
    }

    // return true if the response is a type where a response body is never possible
    // and therefore doesn't have to include header information which indicates no
    // body is present. This is distinct from responses that also do not contain
    // response bodies (possibly ever) but which are required to have content length
    // info in the header (eg 205). Those cases do not have to be handled specially

    private static boolean bodyNotPermitted(Response r) {
        return r.statusCode == 204;
    }

    private boolean bodyIsPresent(Response r) {
        HttpHeaders headers = r.headers();
        if (headers.firstValueAsLong("Content-length").orElse(0L) != 0L)
            return true;
        if (headers.firstValue("Transfer-encoding").isPresent())
            return true;
        return false;
    }

    // Call the user's body handler to get an empty body object

    private CompletableFuture<HttpResponse<T>> handleNoBody(Response r, Exchange<T> exch) {
        BodySubscriber<T> bs = responseHandler.apply(new ResponseInfoImpl(r.statusCode(),
                r.headers(), r.version()));
        bs.onSubscribe(new NullSubscription());
        bs.onComplete();
        CompletionStage<T> cs = ResponseSubscribers.getBodyAsync(executor, bs);
        MinimalFuture<HttpResponse<T>> result = new MinimalFuture<>();
        cs.whenComplete((nullBody, exception) -> {
            if (exception != null)
                result.completeExceptionally(exception);
            else {
                this.response =
                        new HttpResponseImpl<>(r.request(), r, this.response, nullBody, exch);
                result.complete(this.response);
            }
        });
        // ensure that the connection is closed or returned to the pool.
        return result.whenComplete(exch::nullBody);
    }

    private CompletableFuture<HttpResponse<T>>
    responseAsync0(CompletableFuture<Void> start) {
        return start.thenCompose( v -> responseAsyncImpl())
                    .thenCompose((Response r) -> {
                        Exchange<T> exch = getExchange();
                        if (bodyNotPermitted(r)) {
                            if (bodyIsPresent(r)) {
                                IOException ioe = new IOException(
                                    "unexpected content length header with 204 response");
                                exch.cancel(ioe);
                                return MinimalFuture.failedFuture(ioe);
                            } else
                                return handleNoBody(r, exch);
                        }
                        return exch.readBodyAsync(responseHandler)
                            .thenApply((T body) -> {
                                this.response =
                                    new HttpResponseImpl<>(r.request(), r, this.response, body, exch);
                                return this.response;
                            });
                    }).exceptionallyCompose(this::whenCancelled);
    }

    // returns a CancellationExcpetion that wraps the given cause
    // if cancel(boolean) was called, the given cause otherwise
    private Throwable wrapIfCancelled(Throwable cause) {
        CancellationException interrupt = interrupted.get();
        if (interrupt == null) return cause;

        var cancel = new CancellationException(interrupt.getMessage());
        // preserve the stack trace of the original exception to
        // show where the call to cancel(boolean) came from
        cancel.setStackTrace(interrupt.getStackTrace());
        cancel.initCause(Utils.getCancelCause(cause));
        return cancel;
    }

    // if the request failed because the multi exchange was cancelled,
    // make sure the reported exception is wrapped in CancellationException
    private CompletableFuture<HttpResponse<T>> whenCancelled(Throwable t) {
        var x = wrapIfCancelled(t);
        if (x instanceof CancellationException) {
            if (debug.on()) {
                debug.log("MultiExchange interrupted with: " + x.getCause());
            }
        }
        return MinimalFuture.failedFuture(x);
    }

    static class NullSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
        }
    }

    private CompletableFuture<Response> responseAsyncImpl() {
        CompletableFuture<Response> cf;
        if (attempts.incrementAndGet() > max_attempts) {
            cf = failedFuture(new IOException("Too many retries", retryCause));
        } else {
            if (currentreq.timeout().isPresent()) {
                responseTimerEvent = ResponseTimerEvent.of(this);
                client.registerTimer(responseTimerEvent);
            }
            try {
                // 1. apply request filters
                // if currentreq == previousreq the filters have already
                // been applied once. Applying them a second time might
                // cause some headers values to be added twice: for
                // instance, the same cookie might be added again.
                if (currentreq != previousreq) {
                    requestFilters(currentreq);
                }
            } catch (IOException e) {
                return failedFuture(e);
            }
            Exchange<T> exch = getExchange();
            // 2. get response
            cf = exch.responseAsync()
                     .thenCompose((Response response) -> {
                        HttpRequestImpl newrequest;
                        try {
                            // 3. apply response filters
                            newrequest = responseFilters(response);
                        } catch (Throwable t) {
                            IOException e = t instanceof IOException io ? io : new IOException(t);
                            exch.exchImpl.cancel(e);
                            return failedFuture(e);
                        }
                        // 4. check filter result and repeat or continue
                        if (newrequest == null) {
                            if (attempts.get() > 1) {
                                Log.logError("Succeeded on attempt: " + attempts);
                            }
                            return completedFuture(response);
                        } else {
                            cancelTimer();
                            this.response =
                                new HttpResponseImpl<>(currentreq, response, this.response, null, exch);
                            Exchange<T> oldExch = exch;
                            if (currentreq.isWebSocket()) {
                                // need to close the connection and open a new one.
                                exch.exchImpl.connection().close();
                            }
                            return exch.ignoreBody().handle((r,t) -> {
                                previousreq = currentreq;
                                currentreq = newrequest;
                                retriedOnce = false;
                                setExchange(new Exchange<>(currentreq, this));
                                return responseAsyncImpl();
                            }).thenCompose(Function.identity());
                        } })
                     .handle((response, ex) -> {
                        // 5. handle errors and cancel any timer set
                        cancelTimer();
                        if (ex == null) {
                            assert response != null;
                            return completedFuture(response);
                        }
                        // all exceptions thrown are handled here
                        CompletableFuture<Response> errorCF = getExceptionalCF(ex, exch.exchImpl);
                        if (errorCF == null) {
                            return responseAsyncImpl();
                        } else {
                            return errorCF;
                        } })
                     .thenCompose(Function.identity());
        }
        return cf;
    }

    private static boolean retryPostValue() {
        String s = Utils.getNetProperty("jdk.httpclient.enableAllMethodRetry");
        if (s == null)
            return false;
        return s.isEmpty() ? true : Boolean.parseBoolean(s);
    }

    private static boolean disableRetryConnect() {
        String s = Utils.getNetProperty("jdk.httpclient.disableRetryConnect");
        if (s == null)
            return false;
        return s.isEmpty() ? true : Boolean.parseBoolean(s);
    }

    /** True if ALL ( even non-idempotent ) requests can be automatic retried. */
    private static final boolean RETRY_ALWAYS = retryPostValue();
    /** True if ConnectException should cause a retry. Enabled by default */
    static final boolean RETRY_CONNECT = !disableRetryConnect();

    /** Returns true is given request has an idempotent method. */
    private static boolean isIdempotentRequest(HttpRequest request) {
        String method = request.method();
        return switch (method) {
            case "GET", "HEAD" -> true;
            default -> false;
        };
    }

    /** Returns true if the given request can be automatically retried. */
    private static boolean canRetryRequest(HttpRequest request) {
        if (RETRY_ALWAYS)
            return true;
        if (isIdempotentRequest(request))
            return true;
        return false;
    }

    // Returns true if cancel(true) was called.
    // This is an important distinction in several scenarios:
    // for instance, if cancel(true) was called 1. we don't want
    // to retry, 2. we don't want to wrap the exception in
    // a timeout exception.
    boolean requestCancelled() {
        return interrupted.get() != null;
    }

    private boolean retryOnFailure(Throwable t) {
        if (requestCancelled()) return false;
        return t instanceof ConnectionExpiredException
                || (RETRY_CONNECT && (t instanceof ConnectException));
    }

    private Throwable retryCause(Throwable t) {
        Throwable cause = t instanceof ConnectionExpiredException ? t.getCause() : t;
        return cause == null ? t : cause;
    }

    /**
     * Takes a Throwable and returns a suitable CompletableFuture that is
     * completed exceptionally, or null.
     */
    private CompletableFuture<Response> getExceptionalCF(Throwable t, ExchangeImpl<?> exchImpl) {
        if ((t instanceof CompletionException) || (t instanceof ExecutionException)) {
            if (t.getCause() != null) {
                t = t.getCause();
            }
        }
        final boolean retryAsUnprocessed = exchImpl != null && exchImpl.isUnprocessedByPeer();
        if (cancelled && !requestCancelled() && t instanceof IOException) {
            if (!(t instanceof HttpTimeoutException)) {
                t = toTimeoutException((IOException)t);
            }
        } else if (retryAsUnprocessed || retryOnFailure(t)) {
            Throwable cause = retryCause(t);

            if (!(t instanceof ConnectException)) {
                // we may need to start a new connection, and if so
                // we want to start with a fresh connect timeout again.
                if (connectTimeout != null) connectTimeout.reset();
                if (!retryAsUnprocessed && !canRetryRequest(currentreq)) {
                    // a (peer) processed request which cannot be retried, fail with
                    // the original cause
                    return failedFuture(cause);
                }
            } // ConnectException: retry, but don't reset the connectTimeout.

            // allow the retry mechanism to do its work
            retryCause = cause;
            if (!retriedOnce) {
                if (debug.on()) {
                    debug.log(t.getClass().getSimpleName()
                            + " (async): retrying " + currentreq + " due to: ", t);
                }
                retriedOnce = true;
                // The connection was abruptly closed.
                // We return null to retry the same request a second time.
                // The request filters have already been applied to the
                // currentreq, so we set previousreq = currentreq to
                // prevent them from being applied again.
                previousreq = currentreq;
                return null;
            } else {
                if (debug.on()) {
                    debug.log(t.getClass().getSimpleName()
                            + " (async): already retried once " + currentreq, t);
                }
                t = cause;
            }
        }
        return failedFuture(t);
    }

    private HttpTimeoutException toTimeoutException(IOException ioe) {
        HttpTimeoutException t = null;

        // more specific, "request timed out", when connected
        Exchange<?> exchange = getExchange();
        if (exchange != null) {
            ExchangeImpl<?> exchangeImpl = exchange.exchImpl;
            if (exchangeImpl != null) {
                if (exchangeImpl.connection().connected()) {
                    t = new HttpTimeoutException("request timed out");
                    t.initCause(ioe);
                }
            }
        }
        if (t == null) {
            t = new HttpConnectTimeoutException("HTTP connect timed out");
            t.initCause(new ConnectException("HTTP connect timed out"));
        }
        return t;
    }
}
