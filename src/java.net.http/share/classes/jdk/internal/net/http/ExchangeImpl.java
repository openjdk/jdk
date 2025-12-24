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

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.ResponseInfo;
import java.net.http.UnsupportedProtocolVersionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import jdk.internal.net.http.Http2Connection.ALPNException;
import jdk.internal.net.http.common.HttpBodySubscriberWrapper;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.Utils;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;

/**
 * Splits request so that headers and body can be sent separately with optional
 * (multiple) responses in between (e.g. 100 Continue). Also request and
 * response always sent/received in different calls.
 *
 * Synchronous and asynchronous versions of each method are provided.
 *
 * Separate implementations of this class exist for HTTP/1.1 and HTTP/2
 *      Http1Exchange   (HTTP/1.1)
 *      Stream          (HTTP/2)
 *
 * These implementation classes are where work is allocated to threads.
 */
abstract class ExchangeImpl<T> {

    private static final Logger debug =
            Utils.getDebugLogger("ExchangeImpl"::toString, Utils.DEBUG);

    final Exchange<T> exchange;

    private volatile boolean expectTimeoutRaised;

    ExchangeImpl(Exchange<T> e) {
        // e == null means a http/2 pushed stream
        this.exchange = e;
    }

    final Exchange<T> getExchange() {
        return exchange;
    }

    final void setExpectTimeoutRaised() {
        expectTimeoutRaised = true;
    }

    final boolean expectTimeoutRaised() {
        return expectTimeoutRaised;
    }

    HttpClientImpl client() {
        return exchange.client();
    }

    /**
     * Returns the {@link HttpConnection} instance to which this exchange is
     * assigned.
     */
    abstract HttpConnection connection();

    /**
     * Initiates a new exchange and assigns it to a connection if one exists
     * already. connection usually null.
     */
    static <U> CompletableFuture<? extends ExchangeImpl<U>>
    get(Exchange<U> exchange, HttpConnection connection)
    {
        HttpRequestImpl request = exchange.request();
        var version = exchange.version();
        if (version == HTTP_1_1 || request.isWebSocket()) {
            if (debug.on())
                debug.log("get: HTTP/1.1: new Http1Exchange");
            return createHttp1Exchange(exchange, connection);
        } else if (!request.secure() && request.isHttp3Only(version)) {
            assert version == HTTP_3;
            assert !request.isWebSocket();
            if (debug.on())
                debug.log("get: HTTP/3: HTTP/3 is not supported on plain connections");
            return MinimalFuture.failedFuture(
                    new UnsupportedProtocolVersionException(
                            "HTTP/3 is not supported on plain connections"));
        } else if (version == HTTP_2 || isTCP(connection) || !request.secure()) {
            assert !request.isWebSocket();
            return attemptHttp2Exchange(exchange, connection);
        } else {
            assert request.secure();
            assert version == HTTP_3;
            assert !request.isWebSocket();
            return attemptHttp3Exchange(exchange, connection);
        }
    }

    private static boolean isTCP(HttpConnection connection) {
        if (connection instanceof HttpQuicConnection) return false;
        if (connection == null) return false;
        // if it's not an HttpQuicConnection and it's not null it's
        // a TCP connection
        return true;
    }

    private static <U> CompletableFuture<? extends ExchangeImpl<U>>
    attemptHttp2Exchange(Exchange<U> exchange, HttpConnection connection) {
        HttpRequestImpl request = exchange.request();
        Http2ClientImpl c2 = exchange.client().client2(); // #### improve
        CompletableFuture<Http2Connection> c2f = c2.getConnectionFor(request, exchange);
        if (debug.on())
            debug.log("get: Trying to get HTTP/2 connection");
        // local variable required here; see JDK-8223553
        CompletableFuture<CompletableFuture<? extends ExchangeImpl<U>>> fxi =
                c2f.handle((h2c, t) -> createExchangeImpl(h2c, t, exchange, connection));
        return fxi.thenCompose(x -> x);
    }

    private static <U> CompletableFuture<? extends ExchangeImpl<U>>
    attemptHttp3Exchange(Exchange<U> exchange, HttpConnection connection) {
        HttpRequestImpl request = exchange.request();
        var exchvers = exchange.version();
        assert request.secure() : request.uri() + " is not secure";
        assert exchvers == HTTP_3 : "expected HTTP/3, got " + exchvers;
        // when we reach here, it's guaranteed that the client supports HTTP3
        assert exchange.client().client3().isPresent() : "HTTP3 isn't supported by the client";
        var client3 = exchange.client().client3().get();
        CompletableFuture<Http3Connection> c3f;
        Supplier<CompletableFuture<Http2Connection>> c2fs;
        var config = request.http3Discovery();

        if (debug.on()) {
            debug.log("get: Trying to get HTTP/3 connection; config is %s", config);
        }
        // The algorithm here depends on whether HTTP/3 is specified on
        // the request itself, or on the HttpClient.
        // In both cases, we may attempt a direct HTTP/3 connection if
        // we don't have an H3 endpoint registered in the AltServicesRegistry.
        // However, if HTTP/3 is not specified explicitly on the request,
        // we will start both an HTTP/2 and an HTTP/3 connection at the
        // same time, and use the one that complete first. If HTTP/3 is
        // specified on the request, we will give priority to HTTP/3 ond
        // only start the HTTP/2 connection if the HTTP/3 connection fails,
        // or doesn't succeed in the imparted timeout. The timeout can be
        // specified with the property "jdk.httpclient.http3.maxDirectConnectionTimeout".
        // If unspecified it defaults to 2750ms.
        //
        // Because the HTTP/2 connection may start as soon as we create the
        // CompletableFuture<Http2Connection> returned by the Http2Client,
        // we are using a Supplier<CompletableFuture<Http2Connection>> to
        // set up the call chain that would start the HTTP/2 connection.
        try {
            // first look to see if we already have an HTTP/3 connection in
            // the pool. If we find one, we're almost done! We won't need
            // to start any HTTP/2 connection.
            Http3Connection pooled = client3.findPooledConnectionFor(request, exchange);
            if (pooled != null) {
                c3f = MinimalFuture.completedFuture(pooled);
                c2fs = null;
            } else {
                if (debug.on())
                    debug.log("get: no HTTP/3 pooled connection found");
                // possibly start an HTTP/3 connection
                boolean mayAttemptDirectConnection = client3.mayAttemptDirectConnection(request);
                c3f = client3.getConnectionFor(request, exchange);
                if ((!c3f.isDone() || c3f.isCompletedExceptionally()) && mayAttemptDirectConnection) {
                    // We don't know if the server supports HTTP/3.
                    // happy eyeball: prepare to try both HTTP/3 and HTTP/2 and
                    //      to use the first that succeeds
                    if (config != Http3DiscoveryMode.HTTP_3_URI_ONLY) {
                        if (debug.on()) {
                            debug.log("get: trying with both HTTP/3 and HTTP/2");
                        }
                        Http2ClientImpl client2 = exchange.client().client2();
                        c2fs = () -> client2.getConnectionFor(request, exchange);
                    } else {
                        if (debug.on()) {
                            debug.log("get: trying with HTTP/3 only");
                        }
                        c2fs = null;
                    }
                } else {
                    // We have a completed Http3Connection future.
                    // No need to attempt direct HTTP/3 connection.
                    c2fs = null;
                }
            }
        } catch (IOException io) {
            return MinimalFuture.failedFuture(io);
        }
        if (c2fs == null) {
            // Do not attempt a happy eyeball: go the normal route to
            //    attempt an HTTP/3 connection
            // local variable required here; see JDK-8223553
            if (debug.on()) debug.log("No HTTP/3 eyeball needed");
            CompletableFuture<CompletableFuture<? extends ExchangeImpl<U>>> fxi =
                    c3f.handle((h3c, t) -> createExchangeImpl(h3c, t, exchange, connection));
            return fxi.thenCompose(x->x);
        } else if (request.version().orElse(null) == HTTP_3) {
            // explicit request to use HTTP/3, only use HTTP/2 if HTTP/3 fails, but
            // still start both connections in parallel. HttpQuicConnection will
            // attempt a direct connection. Because we register
            // firstToComplete as a dependent action of c3f we will actually
            // only use HTTP/2 (or HTTP/1.1) if HTTP/3 failed
            CompletableFuture<CompletableFuture<? extends ExchangeImpl<U>>> fxi =
                    c3f.handle((h3c, e) -> firstToComplete(exchange, connection, c2fs, c3f));
            if (debug.on()) {
                debug.log("Explicit HTTP/3 request: " +
                        "attempt HTTP/3 first, then default to HTTP/2");
            }
            return fxi.thenCompose(x->x);
        }
        if (debug.on()) {
            debug.log("Attempt HTTP/3 and HTTP/2 in parallel, use the first that connects");
        }
        // default client version is HTTP/3 - request version is not set.
        //    so try HTTP/3 + HTTP/2 in parallel and take the first that completes.
        return firstToComplete(exchange, connection, c2fs, c3f);
    }

    // Use the first connection that successfully completes.
    // This is a bit hairy because HTTP/2 may be downgraded to HTTP/1 if the server
    // doesn't support HTTP/2. In which case the connection attempt will succeed but
    // c2f will be completed with a ALPNException.
    private static <U> CompletableFuture<? extends ExchangeImpl<U>> firstToComplete(
            Exchange<U> exchange,
            HttpConnection connection,
            Supplier<CompletableFuture<Http2Connection>> c2fs,
            CompletableFuture<Http3Connection> c3f) {
        if (debug.on()) {
            debug.log("firstToComplete(connection=%s)", connection);
            debug.log("Will use the first connection that succeeds from HTTP/2 or HTTP/3");
        }
        assert connection == null : "should not come here if connection is not null: " + connection;

        // Set up a completable future (cf) that will complete
        // when the first HTTP/3 or HTTP/2 connection result is
        // available. Error cases (when the result is exceptional)
        // is handled in a dependent action of cf later below
        final CompletableFuture<?> cf;
        // c3f is used for HTTP/3, c2f for HTTP/2
        final CompletableFuture<Http2Connection> c2f;
        if (c3f.isDone()) {
            // We already have a result for HTTP/3, consider that first;
            // There's no need to start HTTP/2 yet if the result is successful.
            c2f = null;
            cf = c3f;
        } else {
            // No result for HTTP/3 yet, start HTTP/2 now and wait for the
            // first that completes.
            c2f = c2fs.get();
            cf = CompletableFuture.anyOf(c2f, c3f);
        }

        CompletableFuture<CompletableFuture<? extends ExchangeImpl<U>>> cfxi = cf.handle((r, t) -> {
            if (debug.on()) {
                debug.log("Checking which from HTTP/2 or HTTP/3 succeeded first");
            }
            CompletableFuture<? extends ExchangeImpl<U>> res;
            // first check if c3f is completed successfully
            if (c3f.isDone()) {
                Http3Connection h3c = c3f.exceptionally((e) -> null).resultNow();
                if (h3c != null) {
                    // HTTP/3 success! Use HTTP/3
                    if (debug.on()) {
                        debug.log("HTTP/3 connect completed first, using HTTP/3");
                    }
                    res = createExchangeImpl(h3c, null, exchange, connection);
                    if (c2f != null) c2f.thenApply(c -> {
                        if (c != null) {
                            c.abandonStream();
                        }
                        return c;
                    });
                } else {
                    // HTTP/3 failed! Use HTTP/2
                    if (debug.on()) {
                        debug.log("HTTP/3 connect completed unsuccessfully," +
                                " either with null or with exception - waiting for HTTP/2");
                        c3f.handle((r3, t3) -> {
                            debug.log("\tcf3: result=%s, throwable=%s",
                                    r3, Utils.getCompletionCause(t3));
                            return r3;
                        }).exceptionally((e) -> null).join();
                    }
                    // c2f may be null here in the case where c3f was already completed
                    // when firstToComplete was called.
                    var h2cf = c2f == null ? c2fs.get() : c2f;
                    // local variable required here; see JDK-8223553
                    CompletableFuture<CompletableFuture<? extends ExchangeImpl<U>>> fxi = h2cf
                            .handle((h2c, e) -> createExchangeImpl(h2c, e, exchange, connection));
                    res = fxi.thenCompose(x -> x);
                }
            } else if (c2f != null && c2f.isDone()) {
                Http2Connection h2c = c2f.exceptionally((e) -> null).resultNow();
                if (h2c != null) {
                    // HTTP/2 succeeded first! Use it.
                    if (debug.on()) {
                        debug.log("HTTP/2 connect completed first, using HTTP/2");
                    }
                    res = createExchangeImpl(h2c, null, exchange, connection);
                } else if (exchange.multi.requestCancelled()) {
                    // special case for when the exchange is cancelled
                    if (debug.on()) {
                        debug.log("HTTP/2 connect completed unsuccessfully, but request cancelled");
                    }
                    CompletableFuture<CompletableFuture<? extends ExchangeImpl<U>>> fxi = c2f
                            .handle((c, e) -> createExchangeImpl(c, e, exchange, connection));
                    res = fxi.thenCompose(x -> x);
                } else {
                    if (debug.on()) {
                        debug.log("HTTP/2 connect completed unsuccessfully," +
                                " either with null or with exception");
                        c2f.handle((r2, t2) -> {
                            debug.log("\tcf2: result=%s, throwable=%s",
                                    r2, Utils.getCompletionCause(t2));
                            return r2;
                        }).exceptionally((e) -> null).join();
                    }

                    // Now is the more complex stuff.
                    // HTTP/2 could have failed in the ALPN, but we still
                    // created a valid TLS connection to the server => default
                    // to HTTP/1.1 over TLS
                    HttpConnection http1Connection = null;
                    if (c2f.isCompletedExceptionally() && !c2f.isCancelled()) {
                        Throwable cause = Utils.getCompletionCause(c2f.exceptionNow());
                        if (cause instanceof ALPNException alpn) {
                            debug.log("HTTP/2 downgraded to HTTP/1.1 - use HTTP/1.1");
                            http1Connection = alpn.getConnection();
                        }
                    }
                    if (http1Connection != null) {
                        if (debug.on()) {
                            debug.log("HTTP/1.1 connect completed first, using HTTP/1.1");
                        }
                        // ALPN failed - but we have a valid HTTP/1.1 connection
                        // to the server: use that.
                        res = createHttp1Exchange(exchange, http1Connection);
                    } else {
                        if (c2f.isCompletedExceptionally()) {
                            // Wait for HTTP/3 to complete, potentially fallback to
                            // HTTP/1.1
                            // local variable required here; see JDK-8223553
                            debug.log("HTTP/2 completed with exception, wait for HTTP/3, " +
                                    "possibly fallback to HTTP/1.1");
                            CompletableFuture<CompletableFuture<? extends ExchangeImpl<U>>> fxi = c3f
                                    .handle((h3c, e) -> fallbackToHttp1OnTimeout(h3c, e, exchange, connection));
                            res = fxi.thenCompose(x -> x);
                        } else {
                            //
                            //       r2 == null && t2 == null - which means we know the
                            //       server doesn't support h2, and we probably already
                            //       have an HTTP/1.1 connection to it
                            //
                            // If an HTTP/1.1 connection is available use it.
                            // Otherwise, wait for the HTTP/3 to complete,  potentially
                            // fallback to HTTP/1.1
                            HttpRequestImpl request = exchange.request();
                            InetSocketAddress proxy = Utils.resolveAddress(request.proxy());
                            InetSocketAddress addr  = request.getAddress();
                            ConnectionPool pool = exchange.client().connectionPool();
                            // if we have an HTTP/1.1 connection in the pool, use that.
                            http1Connection = pool.getConnection(true, addr, proxy);
                            if (http1Connection != null && http1Connection.isOpen()) {
                                debug.log("Server doesn't support HTTP/2, " +
                                        "but we have an HTTP/1.1 connection in the pool");
                                debug.log("Using HTTP/1.1");
                                res = createHttp1Exchange(exchange, http1Connection);
                            } else {
                                // we don't have anything ready to use in the pool:
                                // wait for http/3 to complete, possibly falling back
                                // to HTTP/1.1
                                debug.log("Server doesn't support HTTP/2, " +
                                        "and we do not have an HTTP/1.1 connection");
                                debug.log("Waiting for HTTP/3, possibly fallback to HTTP/1.1");
                                CompletableFuture<CompletableFuture<? extends ExchangeImpl<U>>> fxi = c3f
                                        .handle((h3c, e) -> fallbackToHttp1OnTimeout(h3c, e, exchange, connection));
                                res = fxi.thenCompose(x -> x);
                            }
                        }
                    }
                }
            } else {
                assert c2f != null;
                Throwable failed = t != null ? t : new InternalError("cf1 or cf2 should have completed");
                res = MinimalFuture.failedFuture(failed);
            }
            return res;
        });
        return cfxi.thenCompose(x -> x);
    }

    private static <U> CompletableFuture<? extends ExchangeImpl<U>>
    fallbackToHttp1OnTimeout(Http3Connection c,
                             Throwable t,
                             Exchange<U> exchange,
                             HttpConnection connection) {
        if (t != null) {
            Throwable cause = Utils.getCompletionCause(t);
            if (cause instanceof HttpConnectTimeoutException) {
                // when we reach here we already tried with HTTP/2,
                // and we most likely have an HTTP/1.1 connection in
                // the idle pool. So fallback to that.
                if (debug.on()) {
                    debug.log("HTTP/3 connection timed out: fall back to HTTP/1.1");
                }
                return createHttp1Exchange(exchange, null);
            }
        }
        return createExchangeImpl(c, t, exchange, connection);
    }



    // Creates an HTTP/3 exchange, possibly downgrading to HTTP/2
    private static <U> CompletableFuture<? extends ExchangeImpl<U>>
    createExchangeImpl(Http3Connection c,
                       Throwable t,
                       Exchange<U> exchange,
                       HttpConnection connection) {
        if (debug.on())
            debug.log("handling HTTP/3 connection creation result");
        if (t == null && exchange.multi.requestCancelled()) {
            return MinimalFuture.failedFuture(new IOException("Request cancelled"));
        }
        if (c == null && t == null) {
            if (debug.on())
                debug.log("downgrading to HTTP/2");
            return attemptHttp2Exchange(exchange, connection);
        } else if (t != null) {
            t = Utils.getCompletionCause(t);
            if (debug.on()) {
                if (t instanceof HttpConnectTimeoutException || t instanceof ConnectException) {
                    debug.log("HTTP/3 connection creation failed: " + t);
                } else {
                    debug.log("HTTP/3 connection creation failed "
                            + "with unexpected exception:", t);
                }
            }
            return MinimalFuture.failedFuture(t);
        } else {
            if (debug.on())
                debug.log("creating HTTP/3 exchange");
            try {
                if (exchange.hasReachedStreamLimit()) {
                    // clear the flag before attempting to create a stream again
                    exchange.streamLimitReached(false);
                }
                return c.createStream(exchange)
                        .thenApply(ExchangeImpl::checkCancelled);
            } catch (IOException e) {
                return MinimalFuture.failedFuture(e);
            }
        }
    }

    private static <U, T extends ExchangeImpl<U>> T checkCancelled(T exchangeImpl) {
        Exchange<?> e = exchangeImpl.getExchange();
        if (debug.on()) {
            debug.log("checking cancellation for: " + exchangeImpl);
        }
        if (e.multi.requestCancelled()) {
            if (debug.on()) {
                debug.log("request was cancelled");
            }
            if (!exchangeImpl.isCanceled()) {
                if (debug.on()) {
                    debug.log("cancelling exchange: " + exchangeImpl);
                }
                var cause = e.getCancelCause();
                if (cause == null) cause = new IOException("Request cancelled");
                exchangeImpl.cancel(cause);
            }
        }
        return exchangeImpl;
    }


    // Creates an HTTP/2 exchange, possibly downgrading to HTTP/1
    private static <U> CompletableFuture<? extends ExchangeImpl<U>>
    createExchangeImpl(Http2Connection c,
                       Throwable t,
                       Exchange<U> exchange,
                       HttpConnection connection)
    {
        if (debug.on())
            debug.log("handling HTTP/2 connection creation result");
        boolean secure = exchange.request().secure();
        if (t != null) {
            if (debug.on())
                debug.log("handling HTTP/2 connection creation failed: %s",
                                 (Object)t);
            t = Utils.getCompletionCause(t);
            if (t instanceof Http2Connection.ALPNException) {
                Http2Connection.ALPNException ee = (Http2Connection.ALPNException)t;
                AbstractAsyncSSLConnection as = ee.getConnection();
                if (debug.on())
                    debug.log("downgrading to HTTP/1.1 with: %s", as);
                CompletableFuture<? extends ExchangeImpl<U>> ex =
                        createHttp1Exchange(exchange, as);
                return ex;
            } else {
                if (debug.on())
                    debug.log("HTTP/2 connection creation failed "
                                     + "with unexpected exception: %s", (Object)t);
                return MinimalFuture.failedFuture(t);
            }
        }
        if (secure && c== null) {
            if (debug.on())
                debug.log("downgrading to HTTP/1.1 ");
            CompletableFuture<? extends ExchangeImpl<U>> ex =
                    createHttp1Exchange(exchange, null);
            return ex;
        }
        if (c == null) {
            // no existing connection. Send request with HTTP 1 and then
            // upgrade if successful
            if (debug.on())
                debug.log("new Http1Exchange, try to upgrade");
            return createHttp1Exchange(exchange, connection)
                    .thenApply((e) -> {
                        exchange.h2Upgrade();
                        return e;
                    });
        } else {
            if (debug.on()) debug.log("creating HTTP/2 streams");
            Stream<U> s = c.createStream(exchange);
            CompletableFuture<? extends ExchangeImpl<U>> ex = MinimalFuture.completedFuture(s);
            return ex;
        }
    }

    private static <T> CompletableFuture<Http1Exchange<T>>
    createHttp1Exchange(Exchange<T> ex, HttpConnection as)
    {
        try {
            return MinimalFuture.completedFuture(new Http1Exchange<>(ex, as));
        } catch (Throwable e) {
            return MinimalFuture.failedFuture(e);
        }
    }

    // Called for 204 response - when no body is permitted
    void nullBody(HttpResponse<T> resp, Throwable t) {
        // Needed for HTTP/1.1 to close the connection or return it to the pool
        // Needed for HTTP/2 to subscribe a dummy subscriber and close the stream
    }

    /**
     * {@return {@code true}, if it is allowed to cancel the request timer on
     * response body subscriber termination; {@code false}, otherwise}
     *
     * @param webSocket indicates if the associated request is a WebSocket handshake
     * @param statusCode the status code of the associated response
     */
    static boolean cancelTimerOnResponseBodySubscriberTermination(
            boolean webSocket, int statusCode) {
        return webSocket || statusCode < 100 || statusCode >= 200;
    }

    /* The following methods have separate HTTP/1.1 and HTTP/2 implementations */

    abstract CompletableFuture<ExchangeImpl<T>> sendHeadersAsync();

    /** Sends a request body, after request headers have been sent. */
    abstract CompletableFuture<ExchangeImpl<T>> sendBodyAsync();

    abstract CompletableFuture<T> readBodyAsync(HttpResponse.BodyHandler<T> handler,
                                                boolean returnConnectionToPool,
                                                Executor executor);

    /**
     * Creates and wraps an {@link HttpResponse.BodySubscriber} from a {@link
     * HttpResponse.BodyHandler} for the given {@link ResponseInfo}.
     * An {@code HttpBodySubscriberWrapper} wraps a response body subscriber and makes
     * sure its completed/onError methods are called only once, and that its onSusbscribe
     * is called before onError. This is useful when errors occur asynchronously, and
     * most typically when the error occurs before the {@code BodySubscriber} has
     * subscribed.
     * @param handler  a body handler
     * @param response a response info
     * @return a new {@code HttpBodySubscriberWrapper} to handle the response
     */
    HttpBodySubscriberWrapper<T> createResponseSubscriber(
            HttpResponse.BodyHandler<T> handler, ResponseInfo response) {
        return new HttpBodySubscriberWrapper<>(handler.apply(response));
    }

    /**
     * Ignore/consume the body.
     */
    abstract CompletableFuture<Void> ignoreBody();


    /** Gets the response headers. Completes before body is read. */
    abstract CompletableFuture<Response> getResponseAsync(Executor executor);


    /** Cancels a request.  Not currently exposed through API. */
    abstract void cancel();

    /**
     * Cancels a request with a cause.  Not currently exposed through API.
     */
    abstract void cancel(IOException cause);

    /**
     * Invoked whenever there is a (HTTP) protocol error when dealing with the response
     * from the server. The implementations of {@code ExchangeImpl} are then expected to
     * take necessary action that is expected by the corresponding specifications whenever
     * a protocol error happens. For example, in HTTP/1.1, such protocol error would result
     * in the connection being closed.
     * @param cause The cause of the protocol violation
     */
    abstract void onProtocolError(IOException cause);

    /**
     * Called when the exchange is released, so that cleanup actions may be
     * performed - such as deregistering callbacks.
     * Typically released is called during upgrade, when an HTTP/2 stream
     * takes over from an Http1Exchange, or when a new exchange is created
     * during a multi exchange before the final response body was received.
     */
    abstract void released();

    /**
     * Called when the exchange is completed, so that cleanup actions may be
     * performed - such as deregistering callbacks.
     * Typically, completed is called at the end of the exchange, when the
     * final response body has been received (or an error has caused the
     * completion of the exchange).
     */
    abstract void completed();

    /**
     * Returns true if this exchange was canceled.
     * @return true if this exchange was canceled.
     */
    abstract boolean isCanceled();

    /**
     * Returns the cause for which this exchange was canceled, if available.
     * @return the cause for which this exchange was canceled, if available.
     */
    abstract Throwable getCancelCause();

    // Mark the exchange as upgraded
    // Needed to handle cancellation during the upgrade from
    // Http1Exchange to Stream
    void upgraded() { }

    // Called when server returns non 100 response to
    // an Expect-Continue
    void expectContinueFailed(int rcode) { }

}
