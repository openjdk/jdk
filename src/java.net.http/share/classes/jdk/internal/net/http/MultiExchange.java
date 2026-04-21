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
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.ProtocolException;
import java.net.http.HttpClient.Version;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.StreamLimitException;
import java.time.Duration;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import java.net.http.HttpClient;
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
import static jdk.internal.net.http.AltSvcProcessor.processAltSvcHeader;
import static jdk.internal.net.http.common.Utils.readContentLength;


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

    private record RetryContext(Throwable requestFailureCause,
                                boolean shouldRetry,
                                AtomicInteger reqAttemptCounter,
                                boolean shouldResetConnectTimer) {
        private static RetryContext doNotRetry(Throwable requestFailureCause) {
            return new RetryContext(requestFailureCause, false, null, false);
        }
    }

    private static final AtomicLong IDS = new AtomicLong();
    private final HttpRequest userRequest; // the user request
    private final HttpRequestImpl request; // a copy of the user request
    private final ConnectTimeoutTracker connectTimeout; // null if no timeout
    final HttpClientImpl client;
    final HttpResponse.BodyHandler<T> responseHandler;
    final HttpClientImpl.DelegatingExecutor executor;
    final AtomicInteger attempts = new AtomicInteger();
    final long id = IDS.incrementAndGet();
    HttpRequestImpl currentreq; // used for retries & redirect
    HttpRequestImpl previousreq; // used for retries & redirect
    Exchange<T> exchange; // the current exchange
    Exchange<T> previous;
    volatile HttpResponse<T> response;

    // Maximum number of times a request will be retried/redirected
    // for any reason
    static final int DEFAULT_MAX_ATTEMPTS = 5;
    static final int max_attempts = Utils.getIntegerNetProperty(
            "jdk.httpclient.redirects.retrylimit", DEFAULT_MAX_ATTEMPTS
    );

    // Maximum number of times a request should be retried when
    // max streams limit is reached
    static final int max_stream_limit_attempts = Utils.getIntegerNetProperty(
            "jdk.httpclient.retryOnStreamlimit", max_attempts
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
    // StreamLimit
    private final AtomicInteger streamLimitRetries = new AtomicInteger();

    // This class is used to keep track of the connection timeout
    // across retries, when a ConnectException causes a retry.
    // In that case - we will retry the connect, but we don't
    // want to double the timeout by starting a new timer with
    // the full connectTimeout again.
    // Instead, we use the ConnectTimeoutTracker to return a new
    // duration that takes into account the time spent in the
    // first connect attempt.
    // If however, the connection gets connected, but we later
    // retry the whole operation, then we reset the timer before
    // retrying (since the connection used for the second request
    // will not necessarily be the same: it could be a new
    // unconnected connection) - see checkRetryEligible().
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
        if (vers != Version.HTTP_1_1
                && !request.secure() && request.proxy() != null
                && !request.isHttp3Only(vers)) {
            // downgrade to HTTP_1_1 unless HTTP_3_URI_ONLY.
            // if HTTP_3_URI_ONLY and not secure it will fail down the road, so
            // we don't downgrade here.
            vers = HttpClient.Version.HTTP_1_1;
        }
        if (vers == Version.HTTP_3 && request.secure() && !client.client3().isPresent()) {
            if (!request.isHttp3Only(vers)) {
                // HTTP/3 not supported with the client config.
                // Downgrade to HTTP/2, unless HTTP_3_URI_ONLY is specified
                vers = Version.HTTP_2;
                if (debug.on()) debug.log("HTTP_3 downgraded to " + vers);
            }
        }
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

    void cancelTimer() {
        if (responseTimerEvent != null) {
            client.cancelTimer(responseTimerEvent);
            responseTimerEvent = null;
        }
    }

    private void requestFilters(HttpRequestImpl r) throws IOException {
        if (Log.trace()) Log.logTrace("Applying request filters");
        for (HeaderFilter filter : filters) {
            if (Log.trace()) Log.logTrace("Applying {0}", filter);
            filter.request(r, this);
        }
        if (Log.trace()) Log.logTrace("All filters applied");
    }

    private HttpRequestImpl responseFilters(Response response) throws IOException
    {
        if (Log.trace()) Log.logTrace("Applying response filters");
        ListIterator<HeaderFilter> reverseItr = filters.listIterator(filters.size());
        while (reverseItr.hasPrevious()) {
            HeaderFilter filter = reverseItr.previous();
            if (Log.trace()) Log.logTrace("Applying {0}", filter);
            HttpRequestImpl newreq = filter.response(response);
            if (newreq != null) {
                if (Log.trace()) Log.logTrace("New request: stopping filters");
                return newreq;
            }
        }
        if (Log.trace()) Log.logTrace("All filters applied");
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
                if (debug.on()) {
                    debug.log("multi exchange already cancelled: " + interrupted.get());
                }
            } else {
                if (debug.on()) {
                    debug.log("multi exchange mayInterruptIfRunning=" + mayInterruptIfRunning);
                }
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
    // info in the header (e.g. 205). Those cases do not have to be handled specially

    private static boolean bodyNotPermitted(Response r) {
        return r.statusCode == 204;
    }

    private void ensureNoBody(HttpHeaders headers) throws ProtocolException {

        // Check `Content-Length`
        var contentLength = readContentLength(headers, "", 0);
        if (contentLength > 0) {
            throw new ProtocolException(
                    "Unexpected \"Content-Length\" header in a 204 response: " + contentLength);
        }

        // Check `Transfer-Encoding`
        var transferEncoding = headers.firstValue("Transfer-Encoding");
        if (transferEncoding.isPresent()) {
            throw new ProtocolException(
                    "Unexpected \"Transfer-Encoding\" header in a 204 response: " + transferEncoding.get());
        }

    }

    // Call the user's body handler to get an empty body object

    private CompletableFuture<HttpResponse<T>> handleNoBody(Response r, Exchange<T> exch) {
        BodySubscriber<T> bs = responseHandler.apply(new ResponseInfoImpl(r.statusCode(),
                r.headers(), r.version()));
        Objects.requireNonNull(bs, "BodyHandler returned a null BodySubscriber");
        bs.onSubscribe(new NullSubscription());
        bs.onComplete();
        CompletionStage<T> cs = ResponseSubscribers.getBodyAsync(executor, bs);
        MinimalFuture<HttpResponse<T>> result = new MinimalFuture<>();
        cs.whenComplete((nullBody, exception) -> {
            if (exception != null)
                result.completeExceptionally(exception);
            else {
                result.complete(setNewResponse(r.request(), r, nullBody, exch));
            }
        });
        // ensure that the connection is closed or returned to the pool.
        return result.whenComplete(exch::nullBody);
    }

    // creates a new HttpResponseImpl object and assign it to this.response
    private HttpResponse<T> setNewResponse(HttpRequest request, Response r, T body, Exchange<T> exch) {
        HttpResponse<T> previousResponse = this.response;
        return this.response = new HttpResponseImpl<>(request, r, previousResponse, body, exch);
    }

    private CompletableFuture<HttpResponse<T>>
    responseAsync0(CompletableFuture<Void> start) {
        return start.thenCompose( _ -> {
                    // this is the first attempt to have the request processed by the server
                    attempts.set(1);
                    return responseAsyncImpl(true);
                }).thenCompose((Response r) -> {
                        processAltSvcHeader(r, client(), currentreq);
                        Exchange<T> exch = getExchange();
                        if (bodyNotPermitted(r)) {
                            // No response body consumption is expected, we can cancel the timer right away
                            cancelTimer();
                            try {
                                ensureNoBody(r.headers);
                            } catch (ProtocolException pe) {
                                exch.cancel(pe);
                                return MinimalFuture.failedFuture(pe);
                            }
                            return handleNoBody(r, exch);
                        }
                        return exch.readBodyAsync(responseHandler)
                            .thenApply((T body) -> setNewResponse(r.request, r, body, exch));
                    }).exceptionallyCompose(this::whenCancelled);
    }

    // returns a CancellationException that wraps the given cause
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

    // we call this only when a request is being retried
    private CompletableFuture<Response> retryRequest() {
        // maintain state indicating a request being retried
        previousreq = currentreq;
        // request is being retried, so the filters have already
        // been applied once. Applying them a second time might
        // cause some headers values to be added twice: for
        // instance, the same cookie might be added again.
        final boolean applyReqFilters = false;
        return responseAsyncImpl(applyReqFilters);
    }

    private CompletableFuture<Response> responseAsyncImpl(final boolean applyReqFilters) {
        if (currentreq.timeout().isPresent()) {
            // Retried/Forwarded requests should reset the timer, if present
            cancelTimer();
            responseTimerEvent = ResponseTimerEvent.of(this);
            client.registerTimer(responseTimerEvent);
        }
        try {
            // 1. apply request filters
            if (applyReqFilters) {
                requestFilters(currentreq);
            }
        } catch (IOException e) {
            return failedFuture(e);
        }
        final Exchange<T> exch = getExchange();
        // 2. get response
        final CompletableFuture<Response> cf = exch.responseAsync()
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
                            if (Log.requests()) {
                                Log.logResponse(() -> String.format(
                                        "%s #%s Succeeded on attempt %s: statusCode=%s",
                                        request, id, attempts, response.statusCode));
                            }
                        }
                        return completedFuture(response);
                    } else {
                        setNewResponse(currentreq, response, null, exch);
                        if (currentreq.isWebSocket()) {
                            // need to close the connection and open a new one.
                            exch.exchImpl.connection().close();
                        }
                        return exch.ignoreBody().handle((r,t) -> {
                            previousreq = currentreq;
                            currentreq = newrequest;
                            // this is the first attempt to have the new request
                            // processed by the server
                            attempts.set(1);
                            setExchange(new Exchange<>(currentreq, this));
                            return responseAsyncImpl(true);
                        }).thenCompose(Function.identity());
                    } })
                .handle((response, ex) -> {
                    // 5. handle errors and cancel any timer set
                    if (ex == null) {
                        assert response != null;
                        return completedFuture(response);
                    }

                    // Cancel the timer. Note that we only do so if the
                    // response has completed exceptionally. That is, we don't
                    // cancel the timer if there are no exceptions, since the
                    // response body might still get consumed, and it is
                    // still subject to the response timer.
                    cancelTimer();

                    // all exceptions thrown are handled here
                    final RetryContext retryCtx = checkRetryEligible(ex, exch);
                    assert retryCtx != null : "retry context is null";
                    if (retryCtx.shouldRetry()) {
                        // increment the request attempt counter and retry the request
                        assert retryCtx.reqAttemptCounter != null : "request attempt counter is null";
                        final int numAttempt = retryCtx.reqAttemptCounter.incrementAndGet();
                        if (debug.on()) {
                            debug.log("Retrying request: " + currentreq + " id: " + id
                                    + " attempt: " + numAttempt + " due to: "
                                    + retryCtx.requestFailureCause);
                        }
                        // reset the connect timer if necessary
                        if (retryCtx.shouldResetConnectTimer && this.connectTimeout != null) {
                            this.connectTimeout.reset();
                        }
                        return retryRequest();
                    } else {
                        assert retryCtx.requestFailureCause != null : "missing request failure cause";
                        return MinimalFuture.<Response>failedFuture(retryCtx.requestFailureCause);
                    } })
                .thenCompose(Function.identity());
        return cf;
    }

    private static boolean retryPostValue() {
        String s = Utils.getNetProperty("jdk.httpclient.enableAllMethodRetry");
        if (s == null)
            return false;
        return s.isEmpty() || Boolean.parseBoolean(s);
    }

    private static boolean disableRetryConnect() {
        String s = Utils.getNetProperty("jdk.httpclient.disableRetryConnect");
        if (s == null)
            return false;
        return s.isEmpty() || Boolean.parseBoolean(s);
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
    private static boolean isHttpMethodRetriable(HttpRequest request) {
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

    String streamLimitState() {
        return id + " attempt:" + streamLimitRetries.get();
    }

    /**
     * This method determines if a failed request can be retried. The returned RetryContext
     * will contain the {@linkplain RetryContext#shouldRetry() retry decision} and the
     * {@linkplain RetryContext#requestFailureCause() underlying
     * cause} (computed out of the given {@code requestFailureCause}) of the request failure.
     *
     * @param requestFailureCause the exception that caused the request to fail
     * @param exchg               the Exchange
     * @return a non-null RetryContext which contains the result of retry eligibility
     */
    private RetryContext checkRetryEligible(final Throwable requestFailureCause,
                                            final Exchange<?> exchg) {
        assert requestFailureCause != null : "request failure cause is missing";
        assert exchg != null : "exchange cannot be null";
        // determine the underlying cause for the request failure
        final Throwable t = Utils.getCompletionCause(requestFailureCause);
        final Throwable underlyingCause = switch (t) {
            case IOException ioe -> {
                if (cancelled && !requestCancelled() && !(ioe instanceof HttpTimeoutException)) {
                    yield toTimeoutException(ioe);
                }
                yield ioe;
            }
            default -> {
                yield t;
            }
        };
        if (requestCancelled()) {
            // request has been cancelled, do not retry
            return RetryContext.doNotRetry(underlyingCause);
        }
        // check if retry limited is reached. if yes then don't retry.
        record Limit(int numAttempts, int maxLimit) {
            boolean retryLimitReached() {
                return Limit.this.numAttempts >= Limit.this.maxLimit;
            }
        };
        final Limit limit = switch (underlyingCause) {
            case StreamLimitException _ -> {
                yield new Limit(streamLimitRetries.get(), max_stream_limit_attempts);
            }
            case ConnectException _ -> {
                // for ConnectException (i.e. inability to establish a connection to the server)
                // we currently retry the request only once and don't honour the
                // "jdk.httpclient.redirects.retrylimit" configuration value.
                yield new Limit(attempts.get(), 2);
            }
            default -> {
                yield new Limit(attempts.get(), max_attempts);
            }
        };
        if (limit.retryLimitReached()) {
            if (debug.on()) {
                debug.log("request already attempted "
                        + limit.numAttempts + " times, won't be retried again "
                        + currentreq + " " + id, underlyingCause);
            }
            final var x = underlyingCause instanceof ConnectionExpiredException cee
                    ? cee.getCause() == null ? cee : cee.getCause()
                    : underlyingCause;
            // do not retry anymore
            return RetryContext.doNotRetry(x);
        }
        return switch (underlyingCause) {
            case ConnectException _ -> {
                // connection attempt itself failed, so the request hasn't reached the server.
                // check if retry on connection failure is enabled, if not then we don't retry
                // the request.
                if (!RETRY_CONNECT) {
                    // do not retry
                    yield RetryContext.doNotRetry(underlyingCause);
                }
                // OK to retry. Since the failure is due to a connection/stream being unavailable
                // we mark the retry context to not allow the connect timer to be reset
                // when the retry is actually attempted.
                yield new RetryContext(underlyingCause, true, attempts, false);
            }
            case StreamLimitException sle -> {
                // make a note that the stream limit was reached for a particular HTTP version
                exchg.streamLimitReached(true);
                // OK to retry. Since the failure is due to a connection/stream being unavailable
                // we mark the retry context to not allow the connect timer to be reset
                // when the retry is actually attempted.
                yield new RetryContext(underlyingCause, true, streamLimitRetries, false);
            }
            case ConnectionExpiredException cee -> {
                final Throwable cause = cee.getCause() == null ? cee : cee.getCause();
                // check if the request was explicitly marked as unprocessed, in which case
                // we retry
                if (exchg.isUnprocessedByPeer()) {
                    // OK to retry and allow for the connect timer to be reset
                    yield new RetryContext(cause, true, attempts, true);
                }
                // the request which failed hasn't been marked as unprocessed which implies that
                // it could be processed by the server. check if the request's METHOD allows
                // for retry.
                if (!isHttpMethodRetriable(currentreq)) {
                    // request METHOD doesn't allow for retry
                    yield RetryContext.doNotRetry(cause);
                }
                // OK to retry and allow for the connect timer to be reset
                yield new RetryContext(cause, true, attempts, true);
            }
            default -> {
                // some other exception that caused the request to fail.
                // we check if the request has been explicitly marked as "unprocessed",
                // which implies the server hasn't processed the request and is thus OK to retry.
                if (exchg.isUnprocessedByPeer()) {
                    // OK to retry and allow for resetting the connect timer
                    yield new RetryContext(underlyingCause, true, attempts, false);
                }
                // some other cause of failure, do not retry.
                yield RetryContext.doNotRetry(underlyingCause);
            }
        };
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
