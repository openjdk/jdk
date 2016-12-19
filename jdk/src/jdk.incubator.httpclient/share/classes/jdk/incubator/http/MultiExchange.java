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
import java.time.Duration;
import java.util.List;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.concurrent.Executor;
import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Pair;
import jdk.incubator.http.internal.common.Utils;
import static jdk.incubator.http.internal.common.Pair.pair;

/**
 * Encapsulates multiple Exchanges belonging to one HttpRequestImpl.
 * - manages filters
 * - retries due to filters.
 * - I/O errors and most other exceptions get returned directly to user
 *
 * Creates a new Exchange for each request/response interaction
 */
class MultiExchange<U,T> {

    private final HttpRequest userRequest; // the user request
    private final HttpRequestImpl request; // a copy of the user request
    final AccessControlContext acc;
    final HttpClientImpl client;
    final HttpResponse.BodyHandler<T> responseHandler;
    final ExecutorWrapper execWrapper;
    final Executor executor;
    final HttpResponse.MultiProcessor<U,T> multiResponseHandler;
    HttpRequestImpl currentreq; // used for async only
    Exchange<T> exchange; // the current exchange
    Exchange<T> previous;
    int attempts;
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
    MultiExchange(HttpRequest req,
                  HttpClientImpl client,
                  HttpResponse.BodyHandler<T> responseHandler) {
        this.previous = null;
        this.userRequest = req;
        this.request = new HttpRequestImpl(req);
        this.currentreq = request;
        this.attempts = 0;
        this.client = client;
        this.filters = client.filterChain();
        if (System.getSecurityManager() != null) {
            this.acc = AccessController.getContext();
        } else {
            this.acc = null;
        }
        this.execWrapper = new ExecutorWrapper(client.executor(), acc);
        this.executor = execWrapper.executor();
        this.responseHandler = responseHandler;
        this.exchange = new Exchange<>(request, this);
        this.multiResponseHandler = null;
        this.pushGroup = null;
    }

    /**
     * MultiExchange with multiple responses (HTTP/2 server pushes).
     */
    MultiExchange(HttpRequest req,
                  HttpClientImpl client,
                  HttpResponse.MultiProcessor<U, T> multiResponseHandler) {
        this.previous = null;
        this.userRequest = req;
        this.request = new HttpRequestImpl(req);
        this.currentreq = request;
        this.attempts = 0;
        this.client = client;
        this.filters = client.filterChain();
        if (System.getSecurityManager() != null) {
            this.acc = AccessController.getContext();
        } else {
            this.acc = null;
        }
        this.execWrapper = new ExecutorWrapper(client.executor(), acc);
        this.executor = execWrapper.executor();
        this.multiResponseHandler = multiResponseHandler;
        this.pushGroup = new PushGroup<>(multiResponseHandler, request);
        this.exchange = new Exchange<>(request, this);
        this.responseHandler = pushGroup.mainResponseHandler();
    }

    public HttpResponseImpl<T> response() throws IOException, InterruptedException {
        HttpRequestImpl r = request;
        if (r.duration() != null) {
            timedEvent = new TimedEvent(r.duration());
            client.registerTimer(timedEvent);
        }
        while (attempts < max_attempts) {
            try {
                attempts++;
                Exchange<T> currExchange = getExchange();
                requestFilters(r);
                Response response = currExchange.response();
                Pair<Response, HttpRequestImpl> filterResult = responseFilters(response);
                HttpRequestImpl newreq = filterResult.second;
                if (newreq == null) {
                    if (attempts > 1) {
                        Log.logError("Succeeded on attempt: " + attempts);
                    }
                    T body = currExchange.readBody(responseHandler);
                    cancelTimer();
                    return new HttpResponseImpl<>(userRequest, response, body, currExchange);
                }
                //response.body(HttpResponse.ignoreBody());
                setExchange(new Exchange<>(newreq, this, acc));
                r = newreq;
            } catch (IOException e) {
                if (cancelled) {
                    throw new HttpTimeoutException("Request timed out");
                }
                throw e;
            }
        }
        cancelTimer();
        throw new IOException("Retry limit exceeded");
    }

    CompletableFuture<Void> multiCompletionCF() {
        return pushGroup.groupResult();
    }

    private synchronized Exchange<T> getExchange() {
        return exchange;
    }

    HttpClientImpl client() {
        return client;
    }

    HttpClient.Redirect followRedirects() {
        return client.followRedirects();
    }

    HttpClient.Version version() {
        return client.version();
    }

    private synchronized void setExchange(Exchange<T> exchange) {
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

    // Filters are assumed to be non-blocking so the async
    // versions of these methods just call the blocking ones

    private CompletableFuture<Void> requestFiltersAsync(HttpRequestImpl r) {
        CompletableFuture<Void> cf = new MinimalFuture<>();
        try {
            requestFilters(r);
            cf.complete(null);
        } catch(Throwable e) {
            cf.completeExceptionally(e);
        }
        return cf;
    }


    private Pair<Response,HttpRequestImpl>
    responseFilters(Response response) throws IOException
    {
        Log.logTrace("Applying response filters");
        for (HeaderFilter filter : filters) {
            Log.logTrace("Applying {0}", filter);
            HttpRequestImpl newreq = filter.response(response);
            if (newreq != null) {
                Log.logTrace("New request: stopping filters");
                return pair(null, newreq);
            }
        }
        Log.logTrace("All filters applied");
        return pair(response, null);
    }

    private CompletableFuture<Pair<Response,HttpRequestImpl>>
    responseFiltersAsync(Response response)
    {
        CompletableFuture<Pair<Response,HttpRequestImpl>> cf = new MinimalFuture<>();
        try {
            Pair<Response,HttpRequestImpl> n = responseFilters(response); // assumed to be fast
            cf.complete(n);
        } catch (Throwable e) {
            cf.completeExceptionally(e);
        }
        return cf;
    }

    public void cancel() {
        cancelled = true;
        getExchange().cancel();
    }

    public void cancel(IOException cause) {
        cancelled = true;
        getExchange().cancel(cause);
    }

    public CompletableFuture<HttpResponseImpl<T>> responseAsync(Void v) {
        return responseAsync1(null)
            .thenCompose((Response r) -> {
                Exchange<T> exch = getExchange();
                return exch.readBodyAsync(responseHandler)
                        .thenApply((T body) -> {
                            Pair<Response,T> result = new Pair<>(r, body);
                            return result;
                        });
            })
            .thenApply((Pair<Response,T> result) -> {
                return new HttpResponseImpl<>(userRequest, result.first, result.second, getExchange());
            });
    }

    CompletableFuture<U> multiResponseAsync() {
        CompletableFuture<HttpResponse<T>> mainResponse = responseAsync(null)
                  .thenApply((HttpResponseImpl<T> b) -> {
                      multiResponseHandler.onResponse(b);
                      return (HttpResponse<T>)b;
                   });

        pushGroup.setMainResponse(mainResponse);
        // set up house-keeping related to multi-response
        mainResponse.thenAccept((r) -> {
            // All push promises received by now.
            pushGroup.noMorePushes(true);
        });
        return multiResponseHandler.completion(pushGroup.groupResult(), pushGroup.pushesCF());
    }

    private CompletableFuture<Response> responseAsync1(Void v) {
        CompletableFuture<Response> cf;
        if (++attempts > max_attempts) {
            cf = MinimalFuture.failedFuture(new IOException("Too many retries"));
        } else {
            if (currentreq.duration() != null) {
                timedEvent = new TimedEvent(currentreq.duration());
                client.registerTimer(timedEvent);
            }
            Exchange<T> exch = getExchange();
            // 1. Apply request filters
            cf = requestFiltersAsync(currentreq)
                // 2. get response
                .thenCompose((v1) -> {
                    return exch.responseAsync();
                })
                // 3. Apply response filters
                .thenCompose(this::responseFiltersAsync)
                // 4. Check filter result and repeat or continue
                .thenCompose((Pair<Response,HttpRequestImpl> pair) -> {
                    Response resp = pair.first;
                    if (resp != null) {
                        if (attempts > 1) {
                            Log.logError("Succeeded on attempt: " + attempts);
                        }
                        return MinimalFuture.completedFuture(resp);
                    } else {
                        currentreq = pair.second;
                        Exchange<T> previous = exch;
                        setExchange(new Exchange<>(currentreq, this, acc));
                        //reads body off previous, and then waits for next response
                        return responseAsync1(null);
                    }
                })
            // 5. Convert result to Pair
            .handle((BiFunction<Response, Throwable, Pair<Response, Throwable>>) Pair::new)
            // 6. Handle errors and cancel any timer set
            .thenCompose((Pair<Response,Throwable> obj) -> {
                Response response = obj.first;
                if (response != null) {
                    return MinimalFuture.completedFuture(response);
                }
                // all exceptions thrown are handled here
                CompletableFuture<Response> error = getExceptionalCF(obj.second);
                if (error == null) {
                    cancelTimer();
                    return responseAsync1(null);
                } else {
                    return error;
                }
            });
        }
        return cf;
    }

    /**
     * Take a Throwable and return a suitable CompletableFuture that is
     * completed exceptionally.
     */
    private CompletableFuture<Response> getExceptionalCF(Throwable t) {
        if ((t instanceof CompletionException) || (t instanceof ExecutionException)) {
            if (t.getCause() != null) {
                t = t.getCause();
            }
        }
        if (cancelled && t instanceof IOException) {
            t = new HttpTimeoutException("request timed out");
        }
        return MinimalFuture.failedFuture(t);
    }

    class TimedEvent extends TimeoutEvent {
        TimedEvent(Duration duration) {
            super(duration);
        }
        @Override
        public void handle() {
            cancel(new HttpTimeoutException("request timed out"));
        }
        @Override
        public String toString() {
            return "[deadline = " + deadline() + "]";
        }
    }
}
