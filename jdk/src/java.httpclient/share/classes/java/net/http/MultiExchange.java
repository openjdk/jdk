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
 */

package java.net.http;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import static java.net.http.Pair.pair;

/**
 * Encapsulates multiple Exchanges belonging to one HttpRequestImpl.
 * - manages filters
 * - retries due to filters.
 * - I/O errors and most other exceptions get returned directly to user
 *
 * Creates a new Exchange for each request/response interaction
 */
class MultiExchange {

    final HttpRequestImpl request; // the user request
    final HttpClientImpl client;
    HttpRequestImpl currentreq; // used for async only
    Exchange exchange; // the current exchange
    Exchange previous;
    int attempts;
    // Maximum number of times a request will be retried/redirected
    // for any reason

    final static int DEFAULT_MAX_ATTEMPTS = 5;
    final static int max_attempts = Utils.getIntegerNetProperty(
            "java.net.httpclient.redirects.retrylimit", DEFAULT_MAX_ATTEMPTS
    );

    private final List<HeaderFilter> filters;
    TimedEvent td;
    boolean cancelled = false;

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
     */
    MultiExchange(HttpRequestImpl request) {
        this.exchange = new Exchange(request);
        this.previous = null;
        this.request = request;
        this.currentreq = request;
        this.attempts = 0;
        this.client = request.client();
        this.filters = client.filterChain();
    }

    public HttpResponseImpl response() throws IOException, InterruptedException {
        HttpRequestImpl r = request;
        if (r.timeval() != 0) {
            // set timer
            td = new TimedEvent(r.timeval());
            client.registerTimer(td);
        }
        while (attempts < max_attempts) {
            try {
                attempts++;
                Exchange currExchange = getExchange();
                requestFilters(r);
                HttpResponseImpl response = currExchange.response();
                Pair<HttpResponse, HttpRequestImpl> filterResult = responseFilters(response);
                HttpRequestImpl newreq = filterResult.second;
                if (newreq == null) {
                    if (attempts > 1) {
                        Log.logError("Succeeded on attempt: " + attempts);
                    }
                    cancelTimer();
                    return response;
                }
                response.body(HttpResponse.ignoreBody());
                setExchange(new Exchange(newreq, currExchange.getAccessControlContext() ));
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

    private synchronized Exchange getExchange() {
        return exchange;
    }

    private synchronized void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    private void cancelTimer() {
        if (td != null) {
            client.cancelTimer(td);
        }
    }

    private void requestFilters(HttpRequestImpl r) throws IOException {
        for (HeaderFilter filter : filters) {
            filter.request(r);
        }
    }

    // Filters are assumed to be non-blocking so the async
    // versions of these methods just call the blocking ones

    private CompletableFuture<Void> requestFiltersAsync(HttpRequestImpl r) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        try {
            requestFilters(r);
            cf.complete(null);
        } catch(Throwable e) {
            cf.completeExceptionally(e);
        }
        return cf;
    }


    private Pair<HttpResponse,HttpRequestImpl>
    responseFilters(HttpResponse response) throws IOException
    {
        for (HeaderFilter filter : filters) {
            HttpRequestImpl newreq = filter.response((HttpResponseImpl)response);
            if (newreq != null) {
                return pair(null, newreq);
            }
        }
        return pair(response, null);
    }

    private CompletableFuture<Pair<HttpResponse,HttpRequestImpl>>
    responseFiltersAsync(HttpResponse response)
    {
        CompletableFuture<Pair<HttpResponse,HttpRequestImpl>> cf = new CompletableFuture<>();
        try {
            Pair<HttpResponse,HttpRequestImpl> n = responseFilters(response); // assumed to be fast
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

    public CompletableFuture<HttpResponseImpl> responseAsync(Void v) {
        CompletableFuture<HttpResponseImpl> cf;
        if (++attempts > max_attempts) {
            cf = CompletableFuture.failedFuture(new IOException("Too many retries"));
        } else {
            if (currentreq.timeval() != 0) {
                // set timer
                td = new TimedEvent(currentreq.timeval());
                client.registerTimer(td);
            }
            Exchange exch = getExchange();
            cf = requestFiltersAsync(currentreq)
                .thenCompose(exch::responseAsync)
                .thenCompose(this::responseFiltersAsync)
                .thenCompose((Pair<HttpResponse,HttpRequestImpl> pair) -> {
                    HttpResponseImpl resp = (HttpResponseImpl)pair.first;
                    if (resp != null) {
                        if (attempts > 1) {
                            Log.logError("Succeeded on attempt: " + attempts);
                        }
                        return CompletableFuture.completedFuture(resp);
                    } else {
                        currentreq = pair.second;
                        Exchange previous = exch;
                        setExchange(new Exchange(currentreq,
                                                 currentreq.getAccessControlContext()));
                        //reads body off previous, and then waits for next response
                        return previous
                                .responseBodyAsync(HttpResponse.ignoreBody())
                                .thenCompose(this::responseAsync);
                    }
                })
            .handle((BiFunction<HttpResponse, Throwable, Pair<HttpResponse, Throwable>>) Pair::new)
            .thenCompose((Pair<HttpResponse,Throwable> obj) -> {
                HttpResponseImpl response = (HttpResponseImpl)obj.first;
                if (response != null) {
                    return CompletableFuture.completedFuture(response);
                }
                // all exceptions thrown are handled here
                CompletableFuture<HttpResponseImpl> error = getExceptionalCF(obj.second);
                if (error == null) {
                    cancelTimer();
                    return responseAsync(null);
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
    private CompletableFuture<HttpResponseImpl> getExceptionalCF(Throwable t) {
        if ((t instanceof CompletionException) || (t instanceof ExecutionException)) {
            if (t.getCause() != null) {
                t = t.getCause();
            }
        }
        if (cancelled && t instanceof IOException) {
            t = new HttpTimeoutException("request timed out");
        }
        return CompletableFuture.failedFuture(t);
    }

    <T> T responseBody(HttpResponse.BodyProcessor<T> processor) {
        return getExchange().responseBody(processor);
    }

    <T> CompletableFuture<T> responseBodyAsync(HttpResponse.BodyProcessor<T> processor) {
        return getExchange().responseBodyAsync(processor);
    }

    class TimedEvent extends TimeoutEvent {
        TimedEvent(long timeval) {
            super(timeval);
        }
        @Override
        public void handle() {
            cancel();
        }

    }
}
