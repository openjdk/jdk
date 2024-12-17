/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.net.ProtocolException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.common.Log;

/**
 * One request/response exchange (handles 100/101 intermediate response also).
 * depth field used to track number of times a new request is being sent
 * for a given API request. If limit exceeded exception is thrown.
 *
 */
final class Exchange<T> {

    static final int MAX_NON_FINAL_RESPONSES =
            Utils.getIntegerNetProperty("jdk.httpclient.maxNonFinalResponses", 8);
    final Logger debug = Utils.getDebugLogger(this::dbgString, Utils.DEBUG);

    final HttpRequestImpl request;
    final HttpClientImpl client;
    volatile ExchangeImpl<T> exchImpl;
    volatile CompletableFuture<? extends ExchangeImpl<T>> exchangeCF;
    volatile CompletableFuture<Void> bodyIgnored;

    // used to record possible cancellation raised before the exchImpl
    // has been established.
    private volatile IOException failed;
    final MultiExchange<T> multi;
    final Executor parentExecutor;
    volatile boolean upgrading; // to HTTP/2
    volatile boolean upgraded;  // to HTTP/2
    final PushGroup<T> pushGroup;
    final String dbgTag;

    // Keeps track of the underlying connection when establishing an HTTP/2
    // exchange so that it can be aborted/timed out mid setup.
    final ConnectionAborter connectionAborter = new ConnectionAborter();

    final AtomicInteger nonFinalResponses = new AtomicInteger();

    Exchange(HttpRequestImpl request, MultiExchange<T> multi) {
        this.request = request;
        this.upgrading = false;
        this.client = multi.client();
        this.multi = multi;
        this.parentExecutor = multi.executor;
        this.pushGroup = multi.pushGroup;
        this.dbgTag = "Exchange";
    }

    PushGroup<T> getPushGroup() {
        return pushGroup;
    }

    Executor executor() {
        return parentExecutor;
    }

    public HttpRequestImpl request() {
        return request;
    }

    public Optional<Duration> remainingConnectTimeout() {
        return multi.remainingConnectTimeout();
    }

    HttpClientImpl client() {
        return client;
    }

    // Keeps track of the underlying connection when establishing an HTTP/2
    // exchange so that it can be aborted/timed out mid setup.
    static final class ConnectionAborter {
        private volatile HttpConnection connection;
        private volatile boolean closeRequested;
        private volatile Throwable cause;

        void connection(HttpConnection connection) {
            boolean closeRequested;
            synchronized (this) {
                // check whether this new connection should be
                // closed
                closeRequested = this.closeRequested;
                if (!closeRequested) {
                    this.connection = connection;
                } else {
                    // assert this.connection == null
                    this.closeRequested = false;
                }
            }
            if (closeRequested) closeConnection(connection, cause);
        }

        void closeConnection(Throwable error) {
            HttpConnection connection;
            Throwable cause;
            synchronized (this) {
                cause = this.cause;
                if (cause == null) {
                    cause = error;
                }
                connection = this.connection;
                if (connection == null) {
                    closeRequested = true;
                    this.cause = cause;
                } else {
                    this.connection = null;
                    this.cause = null;
                }
            }
            closeConnection(connection, cause);
        }

        HttpConnection disable() {
            HttpConnection connection;
            synchronized (this) {
                connection = this.connection;
                this.connection = null;
                this.closeRequested = false;
                this.cause = null;
            }
            return connection;
        }

        private static void closeConnection(HttpConnection connection, Throwable cause) {
            if (connection != null) {
                try {
                    connection.close(cause);
                } catch (Throwable t) {
                    // ignore
                }
            }
        }
    }

    // Called for 204 response - when no body is permitted
    // This is actually only needed for HTTP/1.1 in order
    // to return the connection to the pool (or close it)
    void nullBody(HttpResponse<T> resp, Throwable t) {
        exchImpl.nullBody(resp, t);
    }

    public CompletableFuture<T> readBodyAsync(HttpResponse.BodyHandler<T> handler) {
        // If we received a 407 while establishing the exchange
        // there will be no body to read: bodyIgnored will be true,
        // and exchImpl will be null (if we were trying to establish
        // an HTTP/2 tunnel through an HTTP/1.1 proxy)
        if (bodyIgnored != null) return MinimalFuture.completedFuture(null);

        // The connection will not be returned to the pool in the case of WebSocket
        return exchImpl.readBodyAsync(handler, !request.isWebSocket(), parentExecutor)
                .whenComplete((r,t) -> exchImpl.completed());
    }

    /**
     * Called after a redirect or similar kind of retry where a body might
     * be sent but we don't want it. Should send a RESET in h2. For http/1.1
     * we can consume small quantity of data, or close the connection in
     * other cases.
     */
    public CompletableFuture<Void> ignoreBody() {
        if (bodyIgnored != null) return bodyIgnored;
        return exchImpl.ignoreBody();
    }

    /**
     * Called when a new exchange is created to replace this exchange.
     * At this point it is guaranteed that readBody/readBodyAsync will
     * not be called.
     */
    public void released() {
        ExchangeImpl<?> impl = exchImpl;
        if (impl != null) impl.released();
        // Don't set exchImpl to null here. We need to keep
        // it alive until it's replaced by a Stream in wrapForUpgrade.
        // Setting it to null here might get it GC'ed too early, because
        // the Http1Response is now only weakly referenced by the Selector.
    }

    public void cancel() {
        // cancel can be called concurrently before or at the same time
        // that the exchange impl is being established.
        // In that case we won't be able to propagate the cancellation
        // right away
        if (exchImpl != null) {
            exchImpl.cancel();
        } else {
            // no impl - can't cancel impl yet.
            // call cancel(IOException) instead which takes care
            // of race conditions between impl/cancel.
            cancel(new IOException("Request cancelled"));
        }
    }

    public void cancel(IOException cause) {
        if (debug.on()) debug.log("cancel exchImpl: %s, with \"%s\"", exchImpl, cause);
        // If the impl is non null, propagate the exception right away.
        // Otherwise record it so that it can be propagated once the
        // exchange impl has been established.
        ExchangeImpl<?> impl = exchImpl;
        if (impl != null) {
            // propagate the exception to the impl
            if (debug.on()) debug.log("Cancelling exchImpl: %s", exchImpl);
            impl.cancel(cause);
        } else {
            // no impl yet. record the exception
            IOException failed = this.failed;
            if (failed == null) {
                synchronized (this) {
                    failed = this.failed;
                    if (failed == null) {
                        failed = this.failed = cause;
                    }
                }
            }

            // abort/close the connection if setting up the exchange. This can
            // be important when setting up HTTP/2
            connectionAborter.closeConnection(failed);

            // now call checkCancelled to recheck the impl.
            // if the failed state is set and the impl is not null, reset
            // the failed state and propagate the exception to the impl.
            checkCancelled();
        }
    }

    // This method will raise an exception if one was reported and if
    // it is possible to do so. If the exception can be raised, then
    // the failed state will be reset. Otherwise, the failed state
    // will persist until the exception can be raised and the failed state
    // can be cleared.
    // Takes care of possible race conditions.
    private void checkCancelled() {
        ExchangeImpl<?> impl = null;
        IOException cause = null;
        CompletableFuture<? extends ExchangeImpl<T>> cf = null;
        if (failed != null) {
            synchronized (this) {
                cause = failed;
                impl = exchImpl;
                cf = exchangeCF;
            }
        }
        if (cause == null) return;
        if (impl != null) {
            // The exception is raised by propagating it to the impl.
            if (debug.on()) debug.log("Cancelling exchImpl: %s", impl);
            impl.cancel(cause);
            failed = null;
        } else {
            Log.logTrace("Exchange: request [{0}/timeout={1}ms] no impl is set."
                         + "\n\tCan''t cancel yet with {2}",
                         request.uri(),
                         request.timeout().isPresent() ?
                         // calling duration.toMillis() can throw an exception.
                         // this is just debugging, we don't care if it overflows.
                         (request.timeout().get().getSeconds() * 1000
                          + request.timeout().get().getNano() / 1000000) : -1,
                         cause);
            if (cf != null) cf.completeExceptionally(cause);
        }
    }

    <U> CompletableFuture<U> checkCancelled(CompletableFuture<U> cf, HttpConnection connection) {
        return cf.handle((r,t) -> {
            if (t == null) {
                if (multi.requestCancelled()) {
                    // if upgraded, we don't close the connection.
                    // cancelling will be handled by the HTTP/2 exchange
                    // in its own time.
                    if (!upgraded) {
                        t = getCancelCause();
                        if (t == null) t = new IOException("Request cancelled");
                        if (debug.on()) debug.log("exchange cancelled during connect: " + t);
                        try {
                            connection.close();
                        } catch (Throwable x) {
                            if (debug.on()) debug.log("Failed to close connection", x);
                        }
                        return MinimalFuture.<U>failedFuture(t);
                    }
                }
            }
            return cf;
        }).thenCompose(Function.identity());
    }

    public void h2Upgrade() {
        upgrading = true;
        request.setH2Upgrade(this);
    }

    synchronized IOException getCancelCause() {
        return failed;
    }

    // get/set the exchange impl, solving race condition issues with
    // potential concurrent calls to cancel() or cancel(IOException)
    private CompletableFuture<? extends ExchangeImpl<T>>
    establishExchange(HttpConnection connection) {
        if (debug.on()) {
            debug.log("establishing exchange for %s,%n\t proxy=%s",
                      request, request.proxy());
        }
        // check if we have been cancelled first.
        Throwable t = getCancelCause();
        checkCancelled();
        if (t != null) {
            if (debug.on()) {
                debug.log("exchange was cancelled: returned failed cf (%s)", String.valueOf(t));
            }
            return exchangeCF = MinimalFuture.failedFuture(t);
        }

        CompletableFuture<? extends ExchangeImpl<T>> cf, res;
        cf = ExchangeImpl.get(this, connection);
        // We should probably use a VarHandle to get/set exchangeCF
        // instead - as we need CAS semantics.
        synchronized (this) { exchangeCF = cf; };
        res = cf.whenComplete((r,x) -> {
            synchronized (Exchange.this) {
                if (exchangeCF == cf) exchangeCF = null;
            }
        });
        checkCancelled();
        return res.thenCompose((eimpl) -> {
                    // recheck for cancelled, in case of race conditions
                    exchImpl = eimpl;
                    IOException tt = getCancelCause();
                    checkCancelled();
                    if (tt != null) {
                        return MinimalFuture.failedFuture(tt);
                    } else {
                        // Now we're good to go. Because exchImpl is no longer
                        // null cancel() will be able to propagate directly to
                        // the impl after this point ( if needed ).
                        return MinimalFuture.completedFuture(eimpl);
                    } });
    }

    // Completed HttpResponse will be null if response succeeded
    // will be a non null responseAsync if expect continue returns an error

    public CompletableFuture<Response> responseAsync() {
        return responseAsyncImpl(null);
    }

    // check whether the headersSentCF was completed exceptionally with
    // ProxyAuthorizationRequired. If so the Response embedded in the
    // exception is returned. Otherwise we proceed.
    private CompletableFuture<Response> checkFor407(ExchangeImpl<T> ex, Throwable t,
                                                    Function<ExchangeImpl<T>,CompletableFuture<Response>> andThen) {
        t = Utils.getCompletionCause(t);
        if (t instanceof ProxyAuthenticationRequired) {
            if (debug.on()) debug.log("checkFor407: ProxyAuthenticationRequired: building synthetic response");
            bodyIgnored = MinimalFuture.completedFuture(null);
            Response proxyResponse = ((ProxyAuthenticationRequired)t).proxyResponse;
            HttpConnection c = ex == null ? null : ex.connection();
            Response syntheticResponse = new Response(request, this,
                    proxyResponse.headers, c, proxyResponse.statusCode,
                    proxyResponse.version, true);
            return MinimalFuture.completedFuture(syntheticResponse);
        } else if (t != null) {
            if (debug.on()) debug.log("checkFor407: no response - %s", (Object)t);
            return MinimalFuture.failedFuture(t);
        } else {
            if (debug.on()) debug.log("checkFor407: all clear");
            return andThen.apply(ex);
        }
    }

    // After sending the request headers, if no ProxyAuthorizationRequired
    // was raised and the expectContinue flag is on, we need to wait
    // for the 100-Continue response
    private CompletableFuture<Response> expectContinue(ExchangeImpl<T> ex) {
        assert request.expectContinue();

        long responseTimeoutMillis = 5000;
        if (request.timeout().isPresent()) {
            final long timeoutMillis = request.timeout().get().toMillis();
            responseTimeoutMillis = Math.min(responseTimeoutMillis, timeoutMillis);
        }

        return ex.getResponseAsync(parentExecutor)
                .completeOnTimeout(null, responseTimeoutMillis, TimeUnit.MILLISECONDS)
                .thenCompose((Response r1) -> {
                    // The response will only be null if there was a timeout
                    // send body regardless
                    if (r1 == null) {
                        if (debug.on())
                            debug.log("Setting ExpectTimeoutRaised and sending request body");
                        exchImpl.setExpectTimeoutRaised();
                        CompletableFuture<Response> cf =
                                exchImpl.sendBodyAsync()
                                        .thenCompose(exIm -> exIm.getResponseAsync(parentExecutor));
                        cf = wrapForUpgrade(cf);
                        cf = wrapForLog(cf);
                        return cf;
                    }

                    Log.logResponse(r1::toString);
                    int rcode = r1.statusCode();
                    if (rcode == 100) {
                        nonFinalResponses.incrementAndGet();
                        Log.logTrace("Received 100-Continue: sending body");
                        if (debug.on()) debug.log("Received 100-Continue for %s", r1);
                        CompletableFuture<Response> cf =
                                exchImpl.sendBodyAsync()
                                        .thenCompose(exIm -> exIm.getResponseAsync(parentExecutor));
                        cf = wrapForUpgrade(cf);
                        cf = wrapForLog(cf);
                        return cf;
                    } else {
                        Log.logTrace("Expectation failed: Received {0}",
                                rcode);
                        if (debug.on()) debug.log("Expect-Continue failed (%d) for: %s", rcode, r1);
                        if (upgrading && rcode == 101) {
                            IOException failed = new IOException(
                                    "Unable to handle 101 while waiting for 100");
                            return MinimalFuture.failedFuture(failed);
                        }
                        exchImpl.expectContinueFailed(rcode);
                        return MinimalFuture.completedFuture(r1);
                    }
                });
    }

    // After sending the request headers, if no ProxyAuthorizationRequired
    // was raised and the expectContinue flag is off, we can immediately
    // send the request body and proceed.
    private CompletableFuture<Response> sendRequestBody(ExchangeImpl<T> ex) {
        assert !request.expectContinue();
        if (debug.on()) debug.log("sendRequestBody");
        CompletableFuture<Response> cf = ex.sendBodyAsync()
                .thenCompose(exIm -> exIm.getResponseAsync(parentExecutor));
        cf = wrapForUpgrade(cf);
        // after 101 is handled we check for other 1xx responses
        cf = cf.thenCompose(this::ignore1xxResponse);
        cf = wrapForLog(cf);
        return cf;
    }

    /**
     * Checks whether the passed Response has a status code between 102 and 199 (both inclusive).
     * If so, then that {@code Response} is considered intermediate informational response and is
     * ignored by the client. This method then creates a new {@link CompletableFuture} which
     * completes when a subsequent response is sent by the server. Such newly constructed
     * {@link CompletableFuture} will not complete till a "final" response (one which doesn't have
     * a response code between 102 and 199 inclusive) is sent by the server. The returned
     * {@link CompletableFuture} is thus capable of handling multiple subsequent intermediate
     * informational responses from the server.
     * <p>
     * If the passed Response doesn't have a status code between 102 and 199 (both inclusive) then
     * this method immediately returns back a completed {@link CompletableFuture} with the passed
     * {@code Response}.
     * </p>
     *
     * @param rsp The response
     * @return A {@code CompletableFuture} with the final response from the server
     */
    private CompletableFuture<Response> ignore1xxResponse(final Response rsp) {
        final int statusCode = rsp.statusCode();
        // we ignore any response code which is 1xx.
        // For 100 (with the request configured to expect-continue) and 101, we handle it
        // specifically as defined in the RFC-9110, outside of this method.
        // As noted in RFC-9110, section 15.2.1, if response code is 100 and if the request wasn't
        // configured with expectContinue, then we ignore the 100 response and wait for the final
        // response (just like any other 1xx response).
        // Any other response code between 102 and 199 (both inclusive) aren't specified in the
        // "HTTP semantics" RFC-9110. The spec states that these 1xx response codes are informational
        // and interim and the client can choose to ignore them and continue to wait for the
        // final response (headers)
        if ((statusCode >= 102 && statusCode <= 199)
                || (statusCode == 100 && !request.expectContinue)) {
            Log.logTrace("Ignoring (1xx informational) response code {0}", rsp.statusCode());
            if (debug.on()) {
                debug.log("Ignoring (1xx informational) response code "
                        + rsp.statusCode());
            }
            assert exchImpl != null : "Illegal state - current exchange isn't set";
            int count = nonFinalResponses.incrementAndGet();
            if (MAX_NON_FINAL_RESPONSES > 0 && (count < 0 || count > MAX_NON_FINAL_RESPONSES)) {
                return MinimalFuture.failedFuture(
                        new ProtocolException(String.format(
                                "Too many interim responses received: %s > %s",
                                count, MAX_NON_FINAL_RESPONSES)));
            } else {
                // ignore this Response and wait again for the subsequent response headers
                final CompletableFuture<Response> cf = exchImpl.getResponseAsync(parentExecutor);
                // we recompose the CF again into the ignore1xxResponse check/function because
                // the 1xx response is allowed to be sent multiple times for a request, before
                // a final response arrives
                return cf.thenCompose(this::ignore1xxResponse);
            }
        } else {
            // return the already completed future
            return MinimalFuture.completedFuture(rsp);
        }
    }

    CompletableFuture<Response> responseAsyncImpl(HttpConnection connection) {
        Function<ExchangeImpl<T>, CompletableFuture<Response>> after407Check;
        bodyIgnored = null;
        if (request.expectContinue()) {
            request.setSystemHeader("Expect", "100-Continue");
            Log.logTrace("Sending Expect: 100-Continue");
            // wait for 100-Continue before sending body
            after407Check = this::expectContinue;
        } else {
            // send request body and proceed.
            after407Check = this::sendRequestBody;
        }
        // The ProxyAuthorizationRequired can be triggered either by
        // establishExchange (case of HTTP/2 SSL tunneling through HTTP/1.1 proxy
        // or by sendHeaderAsync (case of HTTP/1.1 SSL tunneling through HTTP/1.1 proxy
        // Therefore we handle it with a call to this checkFor407(...) after these
        // two places.
        Function<ExchangeImpl<T>, CompletableFuture<Response>> afterExch407Check =
                (ex) -> ex.sendHeadersAsync()
                        .handle((r,t) -> this.checkFor407(r, t, after407Check))
                        .thenCompose(Function.identity());
        return establishExchange(connection)
                .handle((r,t) -> this.checkFor407(r,t, afterExch407Check))
                .thenCompose(Function.identity());
    }

    private CompletableFuture<Response> wrapForUpgrade(CompletableFuture<Response> cf) {
        if (upgrading) {
            return cf.thenCompose(r -> checkForUpgradeAsync(r, exchImpl));
        }
        // websocket requests use "Connection: Upgrade" and "Upgrade: websocket" headers.
        // however, the "upgrading" flag we maintain in this class only tracks a h2 upgrade
        // that we internally triggered. So it will be false in the case of websocket upgrade, hence
        // this additional check. If it's a websocket request we allow 101 responses and we don't
        // require any additional checks when a response arrives.
        if (request.isWebSocket()) {
            return cf;
        }
        // not expecting an upgrade, but if the server sends a 101 response then we fail the
        // request and also let the ExchangeImpl deal with it as a protocol error
        return cf.thenCompose(r -> {
            if (r.statusCode == 101) {
                final ProtocolException protoEx = new ProtocolException("Unexpected 101 " +
                        "response, when not upgrading");
                assert exchImpl != null : "Illegal state - current exchange isn't set";
                try {
                    exchImpl.onProtocolError(protoEx);
                } catch (Throwable ignore){
                    // ignored
                }
                return MinimalFuture.failedFuture(protoEx);
            }
            return MinimalFuture.completedFuture(r);
        });
    }

    private CompletableFuture<Response> wrapForLog(CompletableFuture<Response> cf) {
        if (Log.requests()) {
            return cf.thenApply(response -> {
                Log.logResponse(response::toString);
                return response;
            });
        }
        return cf;
    }

    HttpResponse.BodySubscriber<T> ignoreBody(HttpResponse.ResponseInfo hdrs) {
        return HttpResponse.BodySubscribers.replacing(null);
    }

    // if this response was received in reply to an upgrade
    // then create the Http2Connection from the HttpConnection
    // initialize it and wait for the real response on a newly created Stream

    private CompletableFuture<Response>
    checkForUpgradeAsync(Response resp,
                         ExchangeImpl<T> ex) {

        int rcode = resp.statusCode();
        if (upgrading && (rcode == 101)) {
            Http1Exchange<T> e = (Http1Exchange<T>)ex;
            // check for 101 switching protocols
            // 101 responses are not supposed to contain a body.
            //    => should we fail if there is one?
            if (debug.on()) debug.log("Upgrading async %s", e.connection());
            return e.readBodyAsync(this::ignoreBody, false, parentExecutor)
                .thenCompose((T v) -> {// v is null
                    debug.log("Ignored body");
                    // we pass e::getBuffer to allow the ByteBuffers to accumulate
                    // while we build the Http2Connection
                    ex.upgraded();
                    upgraded = true;
                    return Http2Connection.createAsync(e.connection(),
                                                 client.client2(),
                                                 this, e::drainLeftOverBytes)
                        .thenCompose((Http2Connection c) -> {
                            HttpConnection connection = connectionAborter.disable();
                            boolean cached = c.offerConnection();
                            if (!cached && connection != null) {
                                connectionAborter.connection(connection);
                            }
                            Stream<T> s = c.getInitialStream();

                            if (s == null) {
                                // s can be null if an exception occurred
                                // asynchronously while sending the preface.
                                Throwable t = c.getRecordedCause();
                                IOException ioe;
                                if (t != null) {
                                    if (!cached)
                                        c.close();
                                    ioe = new IOException("Can't get stream 1: " + t, t);
                                } else {
                                    ioe = new IOException("Can't get stream 1");
                                }
                                return MinimalFuture.failedFuture(ioe);
                            }
                            exchImpl.released();
                            Throwable t;
                            // There's a race condition window where an external
                            // thread (SelectorManager) might complete the
                            // exchange in timeout at the same time where we're
                            // trying to switch the exchange impl.
                            // 'failed' will be reset to null after
                            // exchImpl.cancel() has completed, so either we
                            // will observe failed != null here, or we will
                            // observe e.getCancelCause() != null, or the
                            // timeout exception will be routed to 's'.
                            // Either way, we need to relay it to s.
                            synchronized (this) {
                                exchImpl = s;
                                t = failed;
                            }
                            // Check whether the HTTP/1.1 was cancelled.
                            if (t == null) t = e.getCancelCause();
                            // if HTTP/1.1 exchange was timed out, or the request
                            // was cancelled don't try to go further.
                            if (t instanceof HttpTimeoutException || multi.requestCancelled()) {
                                if (t == null) t = new IOException("Request cancelled");
                                s.cancelImpl(t);
                                return MinimalFuture.failedFuture(t);
                            }
                            if (debug.on())
                                debug.log("Getting response async %s", s);
                            return s.getResponseAsync(null);
                        });}
                );
        }
        return MinimalFuture.completedFuture(resp);
    }

    HttpClient.Version version() {
        return multi.version();
    }

    boolean pushEnabled() {
        return pushGroup != null;
    }

    String h2cSettingsStrings() {
        return client.client2().getSettingsString(pushEnabled());
    }

    String dbgString() {
        return dbgTag;
    }
}
