/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.UnsupportedProtocolVersionException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.common.ConnectionExpiredException;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.QuicClient;
import jdk.internal.net.http.quic.QuicTransportParameters;
import jdk.internal.net.quic.QuicVersion;
import jdk.internal.net.quic.QuicTLSContext;

import static java.net.http.HttpClient.Version.HTTP_3;
import static jdk.internal.net.http.Http3ClientProperties.WAIT_FOR_PENDING_CONNECT;
import static jdk.internal.net.http.common.Alpns.H3;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_remote;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.initial_max_streams_bidi;
import static jdk.internal.net.http.quic.QuicTransportParameters.ParameterId.max_idle_timeout;

/**
 *  Http3 specific aspects of HttpClientImpl
 */
public final class Http3ClientImpl implements AutoCloseable {
    // Setting this property disables HTTPS hostname verification. Use with care.
    private static final boolean disableHostnameVerification = Utils.isHostnameVerificationDisabled();
    // QUIC versions in their descending order of preference
    private static final List<QuicVersion> availableQuicVersions;
    static {
        // we default to QUIC v1 followed by QUIC v2, if no specific preference cannot be
        // determined
        final List<QuicVersion> defaultPref = List.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2);
        // check user specified preference
        final String sysPropVal = Utils.getProperty("jdk.httpclient.quic.available.versions");
        if (sysPropVal == null || sysPropVal.isBlank()) {
            // default to supporting both v1 and v2, with v1 given preference
            availableQuicVersions = defaultPref;
        } else {
            final List<QuicVersion> descendingPref = new ArrayList<>();
            for (final String val : sysPropVal.split(",")) {
                final QuicVersion qv;
                try {
                    // parse QUIC version number represented as a hex string
                    final var vernum = Integer.parseInt(val.trim(), 16);
                    qv = QuicVersion.of(vernum).orElse(null);
                } catch (NumberFormatException nfe) {
                    // ignore and continue with next
                    continue;
                }
                if (qv == null) {
                    continue;
                }
                descendingPref.add(qv);
            }
            availableQuicVersions = descendingPref.isEmpty() ? defaultPref : descendingPref;
        }
    }

    private final Logger debug = Utils.getDebugLogger(this::dbgString);

    final HttpClientImpl client;
    /* Map key is "scheme:host:port" */
    private final Map<String,Http3Connection> connections = new ConcurrentHashMap<>();
    private final Map<String,ConnectionRecovery> reconnections = new ConcurrentHashMap<>();
    private final Set<Http3Connection> pendingClose = ConcurrentHashMap.newKeySet();
    private final Set<String> noH3 = ConcurrentHashMap.newKeySet();

    private final QuicClient quicClient;
    private volatile boolean closed;
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();

    Http3ClientImpl(HttpClientImpl client) {
        this.client = client;
        var executor = client.theExecutor().safeDelegate();
        var context = client.theSSLContext();
        var parameters = client.sslParameters();
        if (!disableHostnameVerification) {
            // setting the endpoint identification algo to HTTPS ensures that
            // during the TLS handshake, the cert presented by the server is verified
            // for hostname checks against the SNI hostname(s) set by the client
            // or in its absence the peer's hostname.
            // see sun.security.ssl.X509TrustManagerImpl#checkIdentity(...)
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
        }
        final QuicTLSContext quicTLSContext = new QuicTLSContext(context);
        final QuicClient.Builder builder = new QuicClient.Builder();
        builder.availableVersions(availableQuicVersions)
                .tlsContext(quicTLSContext)
                .sslParameters(parameters)
                .executor(executor)
                .applicationErrors(Http3Error::stringForCode)
                .clientId(client.dbgString());
        if (client.localAddress() != null) {
            builder.bindAddress(new InetSocketAddress(client.localAddress(), 0));
        }
        final QuicTransportParameters transportParameters = new QuicTransportParameters();
        // HTTP/3 doesn't allow remote bidirectional stream
        transportParameters.setIntParameter(initial_max_streams_bidi, 0);
        // HTTP/3 doesn't allow remote bidirectional stream: no need to allow data
        transportParameters.setIntParameter(initial_max_stream_data_bidi_remote, 0);
        final Duration h3IdleTimeout = client.idleConnectionTimeout(HTTP_3).orElse(null);
        if (h3IdleTimeout != null) {
            final long defaultQuicIdleTimeout =
                    TimeUnit.SECONDS.toMillis(Utils.getLongProperty("jdk.httpclient.quic.idleTimeout", 30));
            final long h3Millis = h3IdleTimeout.toMillis();
            // If a h3 idle timeout has been configured, then we introduce a quic idle timeout
            // which is (much) higher than the h3 idle timeout. This gives the h3 layer enough
            // time to do a graceful close (through GOAWAY frame and then a CONNECTION_CLOSE
            // frame).
            if (h3Millis > 0) {
                final long quicIdleMillis;
                if (h3Millis <= 60000) {
                    quicIdleMillis = Math.max(defaultQuicIdleTimeout,
                            Math.max(30000, h3Millis * 2)); // at least 30 seconds
                } else {
                    quicIdleMillis = Math.max(defaultQuicIdleTimeout, h3Millis + 60000); // a minute more than h3 timeout
                }
                transportParameters.setIntParameter(max_idle_timeout, quicIdleMillis);
            }
        }
        builder.transportParameters(transportParameters);
        this.quicClient = builder.build();
    }

    // Records an exchange waiting for a connection recovery to complete.
    // A connection recovery happens when a connection has maxed out its number
    // of streams, and no MAX_STREAM frame has arrived. In that case, the connection
    // is abandoned (marked with setFinalStream() and taken out of the pool) and a
    // new connection is initiated. Waiters are waiting for the new connection
    // handshake to finish and for the connection to be put in the pool.
    record Waiter(MinimalFuture<Http3Connection> cf, HttpRequestImpl request, Exchange<?> exchange) {
        void complete(Http3Connection conn, Throwable error) {
            if (error != null) cf.completeExceptionally(error);
            else cf.complete(conn);
        }
        static Waiter of(HttpRequestImpl request, Exchange<?> exchange) {
            return new Waiter(new MinimalFuture<>(), request, exchange);
        }
    }

    // Indicates that recovery is needed, or in progress, for a given
    // connection
    sealed interface ConnectionRecovery permits PendingConnection, StreamLimitReached {
    }

    // Indicates that recovery of a connection has been initiated.
    // Waiters will be put in wait until the handshake is completed
    // and the connection is inserted in the pool
    record PendingConnection(ConcurrentLinkedQueue<Waiter> waiters)
            implements ConnectionRecovery {
        PendingConnection(ConcurrentLinkedQueue<Waiter> waiters) {
            this.waiters = Objects.requireNonNull(waiters);
        }
        PendingConnection() {
            this(new ConcurrentLinkedQueue<>());
        }
    }

    // Indicates that a connection that was in the pool has maxed out
    // its stream limit and will be taken out of the pool. A new connection
    // will be created for the first request/response exchange that needs
    // it.
    record StreamLimitReached(Http3Connection connection) implements ConnectionRecovery {}

    // Called when recovery is needed for a given connection, with
    // the request that got the StreamLimitException
    public void streamLimitReached(Http3Connection connection, HttpRequestImpl request) {
        lock.lock();
        try {
            reconnections.computeIfAbsent(connectionKey(request), k -> new StreamLimitReached(connection));
        } finally {
            lock.unlock();
        }
    }

    HttpClientImpl client() {
        return client;
    }

    String dbgString() {
        return "Http3ClientImpl(" + client.dbgString() + ")";
    }

    QuicClient quicClient() {
        return this.quicClient;
    }

    String connectionKey(HttpRequestImpl request) {
        var uri = request.uri();
        var scheme = uri.getScheme();
        var host = uri.getHost();
        var port = uri.getPort();
        assert scheme.toLowerCase(Locale.ROOT).equals("https");
        if (port < 0) port = 443; // https
        return String.format("%s:%s:%d", scheme, host, port);
    }

    Http3Connection findPooledConnectionFor(HttpRequestImpl request,
                                            Exchange<?> exchange)
                throws IOException {
        if (request.secure() && request.proxy() == null) {
            var config = request.http3Discovery();
            final var pooled = connections.get(connectionKey(request));
            if (pooled == null) {
                return null;
            }
            if (pooled.tryReserveForPoolCheckout() && !pooled.isFinalStream()) {
                final var altService = pooled.connection()
                        .getSourceAltService().orElse(null);
                if (altService != null) {
                    // if this connection was created because it was advertised by some alt-service
                    // then verify that the alt-service is still valid/active
                    if (altService.wasAdvertised() && !client.registry().isActive(altService)) {
                        if (debug.on()) {
                            debug.log("Alt-Service %s for pooled connection has expired," +
                                    " marking the connection as unusable for new streams", altService);
                        }
                        // alt-service that was the reason for this H3 connection to be created (and pooled)
                        // is no longer valid. We set a state on the connection to disallow any new streams
                        // and be auto-closed when all current streams are done
                        pooled.setFinalStream();
                        return null;
                    }
                }
                boolean suitable = switch (config) {
                    case HTTP_3_URI_ONLY -> {
                        if (altService == null) {
                            // the pooled connection was created as a result of a direct connection
                            // against the origin, so is a valid one to use for HTTP_3_URI_ONLY request
                            yield true;
                        }
                        // At this point, we have found a pooled connection which matches the request's
                        // authority and that pooled connection was created because some origin
                        // advertised the authority as an alternate service. We can use this pooled
                        // connection with HTTP_3_URI_ONLY, only if the authority of the
                        // alternate service is the same as the authority of the origin that
                        // advertised it
                        yield altService.originHasSameAuthority();
                    }
                    // can't use the connection with ALT_SVC unless the endpoint
                    // was advertised through altService
                    case ALT_SVC -> altService != null && altService.wasAdvertised();
                    default -> true;
                };
                if (suitable) {
                    // found a valid connection in pool, return it
                    if (debug.on()) {
                        debug.log("Found Http3Connection in connection pool");
                    }
                    return pooled;
                }
                if (debug.on()) {
                    if (altService != null) {
                        debug.log("pooled connection for alt-service %s cannot be used for %s",
                                altService, config);
                    }
                }
                return null;
            } else {
                removeFromPool(pooled);
            }
        }
        return null;
    }

    // Called after a recovered connection has been put back in the pool
    // (or when recovery has failed), or when a new connection handshake
    // has completed.
    // Waiters, if any, will be notified.
    private void connectionCompleted(String connectionKey, Exchange<?> origExchange, Http3Connection conn, Throwable error) {
        lock.lock();
        // There should be a connection in the pool at this point,
        // so we can remove the PendingConnection from the reconnections list;
        PendingConnection pendingConnection;
        try {
            var recovery = reconnections.remove(connectionKey);
            if (recovery instanceof PendingConnection pending) {
                pendingConnection = pending;
            } else {
                return;
            }
        } finally {
            lock.unlock();
        }

        int waitersCount = pendingConnection.waiters.size();
        if (waitersCount != 0 && Log.http3()) {
            Log.logHttp3("Completing " + waitersCount
                    + " waiters on recreated connection " + conn);
        }

        // now for each waiter we're going to try to complete it.
        // however, there may be more waiters than available streams!
        // so it's rinse and repeat at this point
        boolean origExchangeCancelled = origExchange == null ? false : origExchange.multi.requestCancelled();
        int completedWaiters = 0;
        int errorWaiters = 0;
        int retriedWaiters = 0;
        try {
            while (!pendingConnection.waiters.isEmpty()) {
                var waiter = pendingConnection.waiters.poll();
                if (error != null && (!origExchangeCancelled || waiter.exchange == origExchange)) {
                    if (Log.http3()) {
                        Log.logHttp3("Completing pending waiter for: " + waiter.request + " #"
                                + waiter.exchange.multi.id + " with " + error);
                    } else if (debug.on()) {
                        debug.log("Completing waiter for: " + waiter.request
                                + " #" + waiter.exchange.multi.id + " with " + conn + " error=" + error);
                    }
                    errorWaiters++;
                    waiter.complete(conn, error);
                } else {
                    var request = waiter.request;
                    var exchange = waiter.exchange;
                    try {
                        Http3Connection pooled = findPooledConnectionFor(request, exchange);
                        if (pooled != null && !pooled.isFinalStream() && !waiter.cf.isDone()) {
                            if (Log.http3()) {
                                Log.logHttp3("Completing pending waiter for: " + waiter.request + " #"
                                        + waiter.exchange.multi.id + " with " + pooled.dbgTag());
                            } else if (debug.on()) {
                                debug.log("Completing waiter for: " + waiter.request
                                        + " #" + waiter.exchange.multi.id + " with pooled conn " + conn);
                            }
                            completedWaiters++;
                            waiter.cf.complete(pooled);
                        } else if (!waiter.cf.isDone()) {
                            // we call getConnectionFor: it should put waiter in the
                            // new waiting list, or attempt to open a connection again
                            if (conn != null) {
                                if (debug.on()) {
                                    debug.log("Not enough streams on recreated connection for: " + waiter.request
                                            + " #" + waiter.exchange.multi.id + ": retrying on new connection");
                                }
                                retriedWaiters++;
                                getConnectionFor(request, exchange, waiter);
                            } else {
                                if (debug.on()) {
                                    debug.log("No HTTP/3 connection for: " + waiter.request
                                            + " #" + waiter.exchange.multi.id + ": will downgrade or fail");
                                }
                                completedWaiters++;
                                waiter.complete(null, error);
                            }
                        }
                    } catch (Throwable t) {
                        if (debug.on()) {
                            debug.log("Completing waiter for: " + waiter.request
                                    + " #" + waiter.exchange.multi.id + " with error: "
                                    + Utils.getCompletionCause(t));
                        }
                        var cause = Utils.getCompletionCause(t);
                        if (cause instanceof ClosedChannelException) {
                            cause = new ConnectionExpiredException(cause);
                        }
                        if (Log.http3()) {
                            Log.logHttp3("Completing pending waiter for: " + waiter.request + " #"
                                    + waiter.exchange.multi.id + " with " + cause);
                        }
                        errorWaiters++;
                        waiter.cf.completeExceptionally(cause);
                    }
                }
            }
        } finally {
            if (conn != null) {
                if (Log.http3()) {
                    Log.logHttp3(("Connection creation completed for requests to %s: " +
                            "waiters[%s](completed:%s, retried:%s, errors:%s)")
                            .formatted(connectionKey, waitersCount, completedWaiters, retriedWaiters, errorWaiters));
                }
            } else {
                if (Log.http3()) {
                    Log.logHttp3(("No HTTP/3 connection created for requests to %s, will fail or downgrade: " +
                            "waiters[%s](completed:%s, retried:%s, errors:%s)")
                            .formatted(connectionKey, waitersCount, completedWaiters, retriedWaiters, errorWaiters));
                }
            }
        }
    }

    CompletableFuture<Http3Connection> getConnectionFor(HttpRequestImpl request, Exchange<?> exchange) {
        return getConnectionFor(request, exchange, null);
    }

    private void completeWaiter(Logger debug, Waiter pendingWaiter, Http3Connection r, Throwable t) {
        // the recovery was done on behalf of a pending waiter.
        // this can happen if the new connection has already maxed out,
        // and recovery was initiated on behalf of the next waiter.
        if (Log.http3()) {
            Log.logHttp3("Completing waiter for: " + pendingWaiter.request + " #"
                    + pendingWaiter.exchange.multi.id + " with (conn: " + r + " error: " + t +")");
        } else if (debug.on()) {
            debug.log("Completing pending waiter for " + pendingWaiter.request + " #"
                    + pendingWaiter.exchange.multi.id + " with (conn: " + r + " error: " + t +")");
        }
        pendingWaiter.complete(r, t);
    }

    private CompletableFuture<Http3Connection> wrapForDebug(CompletableFuture<Http3Connection> h3Cf,
                                                    Exchange<?> exchange,
                                                    HttpRequestImpl request) {
        if (debug.on() || Log.http3()) {
            if (Log.http3()) {
                Log.logHttp3("Recreating connection for: " + request + " #"
                        + exchange.multi.id);
            } else if (debug.on()) {
                debug.log("Recreating connection for: " + request + " #"
                        + exchange.multi.id);
            }
            return  h3Cf.whenComplete((r, t) -> {
                if (Log.http3()) {
                    if (r != null && t == null) {
                        Log.logHttp3("Connection recreated for " + request + " #"
                                + exchange.multi.id + " on " + r.quicConnectionTag());
                    } else if (t != null) {
                        Log.logHttp3("Connection creation failed for " + request + " #"
                                + exchange.multi.id + ": " + t);
                    } else if (r == null) {
                        Log.logHttp3("No connection found for " + request + " #"
                                + exchange.multi.id);
                    }
                } else if (debug.on()) {
                    debug.log("Connection recreated for " + request + " #"
                            + exchange.multi.id);
                }
            });
        } else {
            return h3Cf;
        }
    }

    CompletableFuture<Http3Connection> getConnectionFor(HttpRequestImpl request,
                                                        Exchange<?> exchange,
                                                        Waiter pendingWaiter) {
        try {
            Http3Connection pooled = findPooledConnectionFor(request, exchange);
            if (pooled != null) {
                if (pendingWaiter != null) {
                    if (Log.http3()) {
                        Log.logHttp3("Completing pending waiter for: " + request + " #"
                                + exchange.multi.id + " with " + pooled.dbgTag());
                    } else if (debug.on()) {
                        debug.log("Completing pending waiter for: " + request + " #"
                                + exchange.multi.id + " with " + pooled.dbgTag());
                    }
                    pendingWaiter.cf.complete(pooled);
                    return pendingWaiter.cf;
                } else {
                    return MinimalFuture.completedFuture(pooled);
                }
            }
            if (request.secure() && request.proxy() == null) {
                boolean reconnecting, waitForPendingConnect;
                PendingConnection pendingConnection = null;
                String key;
                Waiter waiter = null;
                if (reconnecting = exchange.hasReachedStreamLimit(HTTP_3)) {
                    if (debug.on()) {
                        debug.log("Exchange has reached limit for: " + request + " #"
                                + exchange.multi.id);
                    }
                }
                if (pendingWaiter != null) reconnecting = true;
                lock.lock();
                try {
                    key = connectionKey(request);
                    var recovery = reconnections.get(key);
                    if (recovery instanceof PendingConnection pending) {
                        // Recovery already initiated. Add waiter to the list!
                        pendingConnection = pending;
                        waiter = pendingWaiter == null
                                ? Waiter.of(request, exchange)
                                : pendingWaiter;
                        exchange.streamLimitReached(null);
                        pendingConnection.waiters.add(waiter);
                        return waiter.cf;
                    } else if (recovery instanceof StreamLimitReached) {
                        // A connection to this server has maxed out its allocated
                        // streams and will be taken out of the pool, but recovery
                        // has not been initiated yet. Do that now.
                        reconnecting = waitForPendingConnect = true;
                    } else waitForPendingConnect = WAIT_FOR_PENDING_CONNECT;
                    // By default we allow concurrent attempts to
                    // create HTTP/3 connections to the same host, except when
                    // one connection has reached the maximum number of streams
                    // it is allowed to use. However,
                    // if waitForPendingConnect is set to `true` above we will
                    // only allow one connection to attempt handshake at a given
                    // time, other requests will be added to a pending list so
                    // that they can go through that connection.
                    if (waitForPendingConnect) {
                        // check again
                        if ((pooled = findPooledConnectionFor(request, exchange)) == null) {
                            // initiate recovery
                            pendingConnection = new PendingConnection();
                            reconnections.put(key, pendingConnection);
                        } else if (pendingWaiter != null) {
                            if (Log.http3()) {
                                Log.logHttp3("Completing pending waiter for: " + request + " #"
                                        + exchange.multi.id + " with " + pooled.dbgTag());
                            } else if (debug.on()) {
                                debug.log("Completing pending waiter for: " + request + " #"
                                        + exchange.multi.id + " with " + pooled.dbgTag());
                            }
                            pendingWaiter.cf.complete(pooled);
                            return pendingWaiter.cf;
                        } else {
                            return MinimalFuture.completedFuture(pooled);
                        }
                    }
                } finally {
                    lock.unlock();
                    if (waiter != null && waiter != pendingWaiter && Log.http3()) {
                        Log.logHttp3("Waiting for connection for: " + request + " #"
                                + exchange.multi.id);
                    } else if (debug.on() && waiter != null) {
                        debug.log("Waiting for connection for: " + request + " #"
                                + exchange.multi.id + (waiter == pendingWaiter ? " (still pending)" : ""));
                    }
                }

                CompletableFuture<Http3Connection> h3Cf = Http3Connection
                        .createAsync(request, this, exchange);
                if (reconnecting) {
                    // System.err.println("Recreating connection for: " + request + " #"
                    //        + exchange.multi.id);
                    h3Cf = wrapForDebug(h3Cf, exchange, request);
                }
                if (pendingWaiter != null) {
                    // the connection was done on behalf of a pending waiter.
                    // this can happen if the new connection has already maxed out,
                    // and recovery was initiated on behalf of the next waiter.
                    h3Cf = h3Cf.whenComplete((r,t) -> completeWaiter(debug, pendingWaiter, r, t));
                }
                h3Cf = h3Cf.thenApply(conn -> {
                        if (conn != null) {
                            return offerConnection(conn);
                        } else {
                            return null;
                        }
                });
                if (pendingConnection != null) {
                    // need to wake up waiters after successful handshake and recovery
                    h3Cf.whenComplete((r, t) -> connectionCompleted(key, exchange, r, t));
                }
                return h3Cf;
            } else {
                if (debug.on())
                    debug.log("Request is unsecure, or proxy isn't null: can't use HTTP/3");
                if (request.isHttp3Only(exchange.version())) {
                    return MinimalFuture.failedFuture(new UnsupportedProtocolVersionException(
                            "can't use HTTP/3 with proxied or unsecured connection"));
                }
                return MinimalFuture.completedFuture(null);
            }
        } catch (Throwable t) {
            return MinimalFuture.failedFuture(t);
        }
    }

    /*
     * Cache the given connection, if no connection to the same
     * destination exists. If one exists, then we let the initial stream
     * complete but allow it to close itself upon completion.
     * This situation should not arise with https because the request
     * has not been sent as part of the initial alpn negotiation
     */
    Http3Connection offerConnection(Http3Connection c) {
        if (debug.on()) debug.log("offering to the connection pool: %s", c);
        if (c.isClosed() || c.isFinalStream()) {
            if (debug.on())
                debug.log("skipping offered closed or closing connection: %s", c);
            return null;
        }

        String key = c.key();
        lock.lock();
        try {
            if (closed) {
                var error = errorRef.get();
                if (error == null) error = new IOException("client closed");
                c.connectionError(error, Http3Error.H3_INTERNAL_ERROR);
                return null;
            }
            Http3Connection c1 = connections.putIfAbsent(key, c);
            if (c1 != null) {
                if (!c1.isFinalStream() || c.isFinalStream()) {
                    c.setFinalStream();
                    if (debug.on())
                        debug.log("existing entry %s in connection pool for %s", c1, key);

                    return c;
                }
                connections.put(key, c);
            }
            if (debug.on())
                debug.log("put in the connection pool: %s", c);
            return c;
        } finally {
            lock.unlock();
        }
    }

    void removeFromPool(Http3Connection c) {
        lock.lock();
        try {
            if (connections.remove(c.key(), c)) {
                if (debug.on())
                    debug.log("removed from the connection pool: %s", c);
            }
            if (!c.isClosed()) {
                if (debug.on())
                    debug.log("adding to pending close: %s", c);
                pendingClose.add(c);
            }
        } finally {
            lock.unlock();
        }
    }

    void connectionClosed(Http3Connection c) {
        removeFromPool(c);
        if (pendingClose.remove(c)) {
            if (debug.on())
                debug.log("removed from pending close: %s", c);
        }
    }

    public Logger debug() { return debug;}

    @Override
    public void close()  {
        try {
            lock.lock();
            try {
                closed = true;
                pendingClose.clear();
                connections.clear();
            } finally {
                lock.unlock();
            }
            // The client itself is being closed, so we don't individually close the connections
            // here and instead just close the QuicClient which then initiates the close of
            // the QUIC endpoint. That will silently terminate the underlying QUIC connections
            // without exchanging any datagram packets with the peer, since there's no point
            // sending/receiving those (including GOAWAY frame) when the endpoint (socket channel)
            // itself won't be around after this point.
        } finally {
            quicClient.close();
        }
    }

    // Called in case of RejectedExecutionException, or shutdownNow;
    public void abort(Throwable t) {
        if (debug.on()) {
            debug.log("HTTP/3 client aborting due to " + t);
        }
        try {
            errorRef.compareAndSet(null, t);
            List<Http3Connection> connectionList;
            lock.lock();
            try {
                closed = true;
                connectionList = new ArrayList<>(connections.values());
                connectionList.addAll(pendingClose);
                pendingClose.clear();
                connections.clear();
            } finally {
                lock.unlock();
            }
            for (var conn : connectionList) {
                conn.close(t);
            }
        } finally {
            quicClient.abort(t);
        }
    }

    public void stop() {
        close();
    }

    /**
     * After an unsuccessful H3 direct connection attempt,
     * mark the authority as not supporting h3.
     * @param rawAuthority the raw authority (host:port)
     */
    public void noH3(String rawAuthority) {
        noH3.add(rawAuthority);
    }

    /**
     * Tells whether the given authority has been marked as
     * not supporting h3
     * @param rawAuthority the raw authority (host:port)
     * @return true if the given authority is believed to not support h3
     */
    public boolean hasNoH3(String rawAuthority) {
        return noH3.contains(rawAuthority);
    }

    /**
     * A direct HTTP/3 attempt may be attempted if we don't have an
     * AltService h3 endpoint recorded for it, and if the given request
     * URI's raw authority hasn't been marked as not supporting HTTP/3,
     * and if the request discovery config is not ALT_SVC.
     * Note that a URI may be marked has not supporting H3 if it doesn't
     * acknowledge the first initial quic packet in the time defined
     * by {@systemProperty jdk.httpclient.http3.maxDirectConnectionTimeout}.
     * @param request the request that may go through h3
     * @return true if there's no h3 endpoint already registered for the given uri.
     */
    public boolean mayAttemptDirectConnection(HttpRequestImpl request) {
        return request.http3Discovery() != Http3DiscoveryMode.ALT_SVC
                && client().registry().lookup(request.uri(), H3).findFirst().isEmpty()
                && !hasNoH3(request.uri().getRawAuthority());
    }
}
