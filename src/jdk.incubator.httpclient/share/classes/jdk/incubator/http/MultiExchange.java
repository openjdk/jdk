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
import java.time.Duration;
import java.util.List;
import java.security.AccessControlContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import jdk.incubator.http.HttpResponse.UntrustedBodyHandler;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.ConnectionExpiredException;
import jdk.incubator.http.internal.common.Utils;
import static jdk.incubator.http.internal.common.MinimalFuture.completedFuture;
import static jdk.incubator.http.internal.common.MinimalFuture.failedFuture;

/**
 * Encapsulates multiple Exchanges belonging to one HttpRequestImpl.
 * - manages filters
 * - retries due to filters.
 * - I/O errors and most other exceptions get returned directly to user
 *
 * Creates a new Exchange for each request/response interaction
 */
class MultiExchange<U,T> {

    static final boolean DEBUG = Utils.DEBUG; // Revisit: temporary dev flag.
    static final System.Logger DEBUG_LOGGER =
            Utils.getDebugLogger("MultiExchange"::toString, DEBUG);

    private final HttpRequest userRequest; // the user request
    private final HttpRequestImpl request; // a copy of the user request
    final AccessControlContext acc;
    final HttpClientImpl client;
    final HttpResponse.BodyHandler<T> responseHandler;
    final Executor executor;
    final HttpResponse.MultiSubscriber<U,T> multiResponseSubscriber;
    final AtomicInteger attempts = new AtomicInteger();
    HttpRequestImpl currentreq; // used for async only
    Exchange<T> exchange; // the current exchange
    Exchange<T> previous;
    volatile Throwable retryCause;
    volatile boolean expiredOnce;
    volatile HttpResponse<T> response = null;

    // Maximum number of times a request will be retried/redirected
    // for any reason

    static final int DEFAULT_MAX_ATTEMPTS = 5;
    static final int max_attempts = Utils.getIntegerNetProperty(
            "jdk.httpclient.redirects.retrylimit", DEFAULT_MAX_ATTEMPTS
    );

    private final List<HeaderFilter> filters;
    TimedEvent timedEvent;
    volatile boolean cancelled;
    final PushGroup<U,T> pushGroup;

    /**
     * Filter fields. These are attached as required by filters
     * and only used by the filter implementations. This could be
     * generalised into Objects that are passed explicitly to the filters
     * (one per MultiExchange object, and one per Exchange object possibly)
     */
    volatile AuthenticationFilter.AuthInfo serverauth, proxyauth;
    // RedirectHandler
    volatile int numberOfRedirects = 0;

    /**
     * MultiExchange with one final response.
     */
    MultiExchange(HttpRequest userRequest,
                  HttpRequestImpl requestImpl,
                  HttpClientImpl client,
                  HttpResponse.BodyHandler<T> responseHandler,
                  AccessControlContext acc) {
        this.previous = null;
        this.userRequest = userRequest;
        this.request = requestImpl;
        this.currentreq = request;
        this.client = client;
        this.filters = client.filterChain();
        this.acc = acc;
        this.executor = client.theExecutor();
        this.responseHandler = responseHandler;
        if (acc != null) {
            // Restricts the file publisher with the senders ACC, if any
            if (responseHandler instanceof UntrustedBodyHandler)
                ((UntrustedBodyHandler)this.responseHandler).setAccessControlContext(acc);
        }
        this.exchange = new Exchange<>(request, this);
        this.multiResponseSubscriber = null;
        this.pushGroup = null;
    }

    /**
     * MultiExchange with multiple responses (HTTP/2 server pushes).
     */
    MultiExchange(HttpRequest userRequest,
                  HttpRequestImpl requestImpl,
                  HttpClientImpl client,
                  HttpResponse.MultiSubscriber<U, T> multiResponseSubscriber,
                  AccessControlContext acc) {
        this.previous = null;
        this.userRequest = userRequest;
        this.request = requestImpl;
        this.currentreq = request;
        this.client = client;
        this.filters = client.filterChain();
        this.acc = acc;
        this.executor = client.theExecutor();
        this.multiResponseSubscriber = multiResponseSubscriber;
        this.pushGroup = new PushGroup<>(multiResponseSubscriber, request, acc);
        this.exchange = new Exchange<>(request, this);
        this.responseHandler = pushGroup.mainResponseHandler();
    }

//    CompletableFuture<Void> multiCompletionCF() {
//        return pushGroup.groupResult();
//    }

    private synchronized Exchange<T> getExchange() {
        return exchange;
    }

    HttpClientImpl client() {
        return client;
    }

//    HttpClient.Redirect followRedirects() {
//        return client.followRedirects();
//    }

    HttpClient.Version version() {
        return request.version().orElse(client.version());
    }

    private synchronized void setExchange(Exchange<T> exchange) {
        if (this.exchange != null && exchange != this.exchange) {
            this.exchange.released();
        }
        this.exchange = exchange;
    }

    private void cancelTimer() {
        if (timedEvent != null) {
            client.cancelTimer(timedEvent);
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
        for (HeaderFilter filter : filters) {
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

//    public void cancel() {
//        cancelled = true;
//        getExchange().cancel();
//    }

    public void cancel(IOException cause) {
        cancelled = true;
        getExchange().cancel(cause);
    }

    public CompletableFuture<HttpResponse<T>> responseAsync() {
        CompletableFuture<Void> start = new MinimalFuture<>();
        CompletableFuture<HttpResponse<T>> cf = responseAsync0(start);
        start.completeAsync( () -> null, executor); // trigger execution
        return cf;
    }

    private CompletableFuture<HttpResponse<T>>
    responseAsync0(CompletableFuture<Void> start) {
        return start.thenCompose( v -> responseAsyncImpl())
                    .thenCompose((Response r) -> {
                        Exchange<T> exch = getExchange();
                        return exch.readBodyAsync(responseHandler)
                            .thenApply((T body) -> {
                                this.response =
                                    new HttpResponseImpl<>(userRequest, r, this.response, body, exch);
                                return this.response;
                            });
                    });
    }

    CompletableFuture<U> multiResponseAsync() {
        CompletableFuture<Void> start = new MinimalFuture<>();
        CompletableFuture<HttpResponse<T>> cf = responseAsync0(start);
        CompletableFuture<HttpResponse<T>> mainResponse =
                cf.thenApply(b -> {
                        multiResponseSubscriber.onResponse(b);
                        pushGroup.noMorePushes(true);
                        return b; });
        pushGroup.setMainResponse(mainResponse);
        CompletableFuture<U> res = multiResponseSubscriber.completion(pushGroup.groupResult(),
                                                                      pushGroup.pushesCF());
        start.completeAsync( () -> null, executor); // trigger execution
        return res;
    }

    private CompletableFuture<Response> responseAsyncImpl() {
        CompletableFuture<Response> cf;
        if (attempts.incrementAndGet() > max_attempts) {
            cf = failedFuture(new IOException("Too many retries", retryCause));
        } else {
            if (currentreq.timeout().isPresent()) {
                timedEvent = new TimedEvent(currentreq.timeout().get());
                client.registerTimer(timedEvent);
            }
            try {
                // 1. apply request filters
                requestFilters(currentreq);
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
                        } catch (IOException e) {
                            return failedFuture(e);
                        }
                        // 4. check filter result and repeat or continue
                        if (newrequest == null) {
                            if (attempts.get() > 1) {
                                Log.logError("Succeeded on attempt: " + attempts);
                            }
                            return completedFuture(response);
                        } else {
                            this.response =
                                new HttpResponseImpl<>(currentreq, response, this.response, null, exch);
                            Exchange<T> oldExch = exch;
                            return exch.ignoreBody().handle((r,t) -> {
                                currentreq = newrequest;
                                expiredOnce = false;
                                setExchange(new Exchange<>(currentreq, this, acc));
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
                        CompletableFuture<Response> errorCF = getExceptionalCF(ex);
                        if (errorCF == null) {
                            return responseAsyncImpl();
                        } else {
                            return errorCF;
                        } })
                     .thenCompose(Function.identity());
        }
        return cf;
    }

    /**
     * Takes a Throwable and returns a suitable CompletableFuture that is
     * completed exceptionally, or null.
     */
    private CompletableFuture<Response> getExceptionalCF(Throwable t) {
        if ((t instanceof CompletionException) || (t instanceof ExecutionException)) {
            if (t.getCause() != null) {
                t = t.getCause();
            }
        }
        if (cancelled && t instanceof IOException) {
            t = new HttpTimeoutException("request timed out");
        } else if (t instanceof ConnectionExpiredException) {
            // allow the retry mechanism to do its work
            // ####: method (GET,HEAD, not POST?), no bytes written or read ( differentiate? )
            if (t.getCause() != null) retryCause = t.getCause();
            if (!expiredOnce) {
                DEBUG_LOGGER.log(Level.DEBUG,
                    "MultiExchange: ConnectionExpiredException (async): retrying...",
                    t);
                expiredOnce = true;
                return null;
            } else {
                DEBUG_LOGGER.log(Level.DEBUG,
                    "MultiExchange: ConnectionExpiredException (async): already retried once.",
                    t);
                if (t.getCause() != null) t = t.getCause();
            }
        }
        return failedFuture(t);
    }

    class TimedEvent extends TimeoutEvent {
        TimedEvent(Duration duration) {
            super(duration);
        }
        @Override
        public void handle() {
            DEBUG_LOGGER.log(Level.DEBUG,
                    "Cancelling MultiExchange due to timeout for request %s",
                     request);
            cancel(new HttpTimeoutException("request timed out"));
        }
    }
}
