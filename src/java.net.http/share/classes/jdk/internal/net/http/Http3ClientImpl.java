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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.net.http.AltServicesRegistry.AltService;
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

/**
 *  Http3 specific aspects of HttpClientImpl
 */
final class Http3ClientImpl implements AutoCloseable {
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
    private final Http3ConnectionPool connections = new Http3ConnectionPool(debug);
    private final Http3PendingConnections reconnections = new Http3PendingConnections();
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
    record PendingConnection(AltService altSvc, Exchange<?> exchange, ConcurrentLinkedQueue<Waiter> waiters)
            implements ConnectionRecovery {
        PendingConnection(AltService altSvc, Exchange<?> exchange, ConcurrentLinkedQueue<Waiter> waiters) {
            this.altSvc = altSvc;
            this.waiters = Objects.requireNonNull(waiters);
            this.exchange = exchange;
        }
        PendingConnection(AltService altSvc, Exchange<?> exchange) {
            this(altSvc, exchange, new ConcurrentLinkedQueue<>());
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
            reconnections.streamLimitReached(connectionKey(request), connection);
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
        return connections.connectionKey(request);
    }

    Http3Connection findPooledConnectionFor(HttpRequestImpl request,
                                            Exchange<?> exchange)
                throws IOException {
        if (request.secure() && request.proxy() == null) {
            final var pooled = connections.lookupFor(request);
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
                        pooled.setFinalStreamAndCloseIfIdle();
                        return null;
                    }
                }
                if (debug.on()) {
                    debug.log("Found Http3Connection in connection pool");
                }
                // found a valid connection in pool, return it
                return pooled;
            } else {
                if (debug.on()) {
                    debug.log("Pooled connection expired. Removing it.");
                }
                removeFromPool(pooled);
            }
        }
        return null;
    }

    private static String label(Http3Connection conn) {
        return Optional.ofNullable(conn)
                .map(Http3Connection::connection)
                .map(HttpQuicConnection::label)
                .orElse("null");
    }

    private static String describe(HttpRequestImpl request, long id) {
        return String.format("%s #%s", request, id);
    }

    private static String describe(Exchange<?> exchange) {
        if (exchange == null) return "null";
        return describe(exchange.request, exchange.multi.id);
    }

    private static String describePendingExchange(String prefix, PendingConnection pending) {
        return String.format("%s %s", prefix, describe(pending.exchange));
    }

    private static String describeAltSvc(PendingConnection pendingConnection) {
        return Optional.ofNullable(pendingConnection)
                .map(PendingConnection::altSvc)
                .map(AltService::toString)
                .map(s -> "altsvc: " + s)
                .orElse("no altSvc");
    }

    // Called after a recovered connection has been put back in the pool
    // (or when recovery has failed), or when a new connection handshake
    // has completed.
    // Waiters, if any, will be notified.
    private void connectionCompleted(String connectionKey, Exchange<?> origExchange, Http3Connection conn, Throwable error) {
        try {
            if (Log.http3()) {
                Log.logHttp3("Checking waiters on completed connection {0} to {1} created for {2}",
                        label(conn), connectionKey, describe(origExchange));
            }
            connectionCompleted0(connectionKey, origExchange, conn, error);
        } catch (Throwable t) {
            if (Log.http3() || Log.errors()) {
                Log.logError(t);
            }
            throw t;
        }
    }

    private void connectionCompleted0(String connectionKey, Exchange<?> origExchange, Http3Connection conn, Throwable error) {
        lock.lock();
        // There should be a connection in the pool at this point,
        // so we can remove the PendingConnection from the reconnections list;
        PendingConnection pendingConnection = null;
        try {
            var recovery = reconnections.removeCompleted(connectionKey, origExchange, conn);
            if (recovery instanceof PendingConnection pending) {
                pendingConnection = pending;
            }
        } finally {
            lock.unlock();
        }
        if (pendingConnection == null) {
            if (Log.http3()) {
                Log.logHttp3("No waiters to complete for " + label(conn));
            }
            return;
        }

        int waitersCount = pendingConnection.waiters.size();
        if (waitersCount != 0 && Log.http3()) {
            Log.logHttp3("Completing " + waitersCount
                    + " waiters on recreated connection " + label(conn)
                    + describePendingExchange(" - originally created for", pendingConnection));
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
                                        + waiter.exchange.multi.id + " with " + label(pooled));
                            } else if (debug.on()) {
                                debug.log("Completing waiter for: " + waiter.request
                                        + " #" + waiter.exchange.multi.id + " with pooled conn " + label(pooled));
                            }
                            completedWaiters++;
                            waiter.cf.complete(pooled);
                        } else if (!waiter.cf.isDone()) {
                            // we call getConnectionFor: it should put waiter in the
                            // new waiting list, or attempt to open a connection again
                            if (conn != null) {
                                if (Log.http3()) {
                                    Log.logHttp3("Not enough streams on recreated connection for: " + waiter.request + " #"
                                            + waiter.exchange.multi.id + " with " + label(conn));
                                } else if (debug.on()) {
                                    debug.log("Not enough streams on recreated connection for: " + waiter.request
                                            + " #" + waiter.exchange.multi.id + " with " + label(conn)
                                            + ": retrying on new connection");
                                }
                                retriedWaiters++;
                                getConnectionFor(request, exchange, waiter);
                            } else {
                                if (Log.http3()) {
                                    Log.logHttp3("No HTTP/3 connection for:: " + waiter.request + " #"
                                            + waiter.exchange.multi.id + ": will downgrade or fail");
                                } else if (debug.on()) {
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
            if (Log.http3()) {
                String pendingInfo = describePendingExchange(" - originally created for", pendingConnection);

                if (conn != null) {
                    Log.logHttp3(("Connection creation completed for requests to %s: " +
                            "waiters[%s](completed:%s, retried:%s, errors:%s)%s")
                            .formatted(connectionKey, waitersCount, completedWaiters,
                                       retriedWaiters, errorWaiters, pendingInfo));
                } else {
                    Log.logHttp3(("No HTTP/3 connection created for requests to %s, will fail or downgrade: " +
                            "waiters[%s](completed:%s, retried:%s, errors:%s)%s")
                            .formatted(connectionKey, waitersCount, completedWaiters,
                                       retriedWaiters, errorWaiters, pendingInfo));
                }
            }
        }
    }

    CompletableFuture<Http3Connection> getConnectionFor(HttpRequestImpl request, Exchange<?> exchange) {
        assert request != null;
        return getConnectionFor(request, exchange, null);
    }

    private void completeWaiter(Logger debug, Waiter pendingWaiter, Http3Connection r, Throwable t) {
        // the recovery was done on behalf of a pending waiter.
        // this can happen if the new connection has already maxed out,
        // and recovery was initiated on behalf of the next waiter.
        if (Log.http3()) {
            Log.logHttp3("Completing waiter for: " + pendingWaiter.request + " #"
                    + pendingWaiter.exchange.multi.id + " with (conn: " + label(r) + " error: " + t +")");
        } else if (debug.on()) {
            debug.log("Completing pending waiter for " + pendingWaiter.request + " #"
                    + pendingWaiter.exchange.multi.id + " with (conn: " + label(r) + " error: " + t +")");
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
                                + exchange.multi.id + " on " + label(r));
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

    Optional<AltService> lookupAltSvc(HttpRequestImpl request) {
        return client.registry()
                .lookup(request.uri(), H3::equals)
                .findFirst();
    }

    CompletableFuture<Http3Connection> getConnectionFor(HttpRequestImpl request,
                                                        Exchange<?> exchange,
                                                        Waiter pendingWaiter) {
        assert request != null;
        if (Log.http3()) {
            if (pendingWaiter != null) {
                Log.logHttp3("getConnectionFor pendingWaiter {0}",
                         describe(pendingWaiter.request, pendingWaiter.exchange.multi.id));
            } else {
                Log.logHttp3("getConnectionFor exchange {0}",
                        describe(request, exchange.multi.id));
            }
        }
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
                if (reconnecting = exchange.hasReachedStreamLimit()) {
                    if (debug.on()) {
                        debug.log("Exchange has reached limit for: " + request + " #"
                                + exchange.multi.id);
                    }
                }
                if (pendingWaiter != null) reconnecting = true;
                lock.lock();
                try {
                    key = connectionKey(request);

                    var recovery = reconnections.lookupFor(key, request, client);
                    if (debug.on()) debug.log("lookup found %s for %s", recovery, request);
                    if (recovery instanceof PendingConnection pending) {
                        // Recovery already initiated. Add waiter to the list!
                        if (debug.on()) {
                            debug.log("PendingConnection (%s) found for %s",
                                    describePendingExchange("originally created for", pending),
                                    describe(request, exchange.multi.id));
                        }
                        pendingConnection = pending;
                        waiter = pendingWaiter == null
                                ? Waiter.of(request, exchange)
                                : pendingWaiter;
                        exchange.streamLimitReached(false);
                        pendingConnection.waiters.add(waiter);
                        return waiter.cf;
                    } else if (recovery instanceof StreamLimitReached) {
                        // A connection to this server has maxed out its allocated
                        // streams and will be taken out of the pool, but recovery
                        // has not been initiated yet. Do that now.
                        reconnecting = waitForPendingConnect = true;
                    } else waitForPendingConnect = WAIT_FOR_PENDING_CONNECT;
                    // By default, we allow concurrent attempts to
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
                            var altSvc = lookupAltSvc(request).orElse(null);
                            // maybe null if ALT_SVC && altSvc == null
                            pendingConnection = reconnections.addPending(key, request, altSvc, exchange);
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
                        var altSvc = describeAltSvc(pendingConnection);
                        var orig = Optional.of(pendingConnection)
                                        .map(PendingConnection::exchange)
                                        .map(e -> " created for #" + e.multi.id)
                                        .orElse("");
                        Log.logHttp3("Waiting for connection for: " + describe(request, exchange.multi.id)
                                + " " + altSvc + orig);
                    } else if (pendingWaiter != null && Log.http3()) {
                        var altSvc = describeAltSvc(pendingConnection);
                        Log.logHttp3("Creating connection for: " + describe(request, exchange.multi.id)
                                + " " + altSvc);
                    } else if (debug.on() && waiter != null) {
                        debug.log("Waiting for connection for: " + describe(request, exchange.multi.id)
                                + (waiter == pendingWaiter ? " (still pending)" : ""));
                    }
                }

                if (Log.http3()) {
                    Log.logHttp3("Creating connection for Exchange {0}", describe(exchange));
                } else if (debug.on()) {
                    debug.log("Creating connection for Exchange %s", describe(exchange));
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
                            if (debug.on()) {
                                debug.log("Offering connection %s created for %s",
                                        label(conn), exchange.multi.id);
                            }
                            var offered = offerConnection(conn);
                            if (debug.on()) {
                                debug.log("Connection offered %s created for %s",
                                        label(conn), exchange.multi.id);
                            }
                            // if we return null here, we will downgrade
                            // but if we return `conn` we will open a new connection.
                            return offered == null ? conn : offered;
                        } else {
                            if (debug.on()) {
                                debug.log("No connection for exchange #" + exchange.multi.id);
                            }
                            return null;
                        }
                });
                if (pendingConnection != null) {
                    // need to wake up waiters after successful handshake and recovery
                    h3Cf = h3Cf.whenComplete((r, t) -> connectionCompleted(key, exchange, r, t));
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
            if (Log.http3() || Log.errors()) {
                Log.logError("Failed to get connection for {0}: {1}",
                        describe(exchange), t);
            }
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
        if (!c.isOpen() || c.isFinalStream()) {
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
                // there was a connection in the pool
                if (!c1.isFinalStream() || c.isFinalStream()) {
                    if (!c.isFinalStream()) {
                        c.allowOnlyOneStream();
                        return c;
                    } else if (c1.isFinalStream()) {
                        return c;
                    }
                    if (debug.on())
                        debug.log("existing entry %s in connection pool for %s", c1, key);
                    // c1 will remain in the pool and we will use c for the given
                    // request.
                    if (Log.http3()) {
                        Log.logHttp3("Existing connection {0} for {1} found in the pool", label(c1), c1.key());
                        Log.logHttp3("New connection {0} marked final and not offered to the pool", label(c));
                    }
                    return c1;
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
            if (c.isOpen()) {
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
                connectionList = new ArrayList<>(connections.values().toList());
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
        var config = request.http3Discovery();
        return switch (config) {
            // never attempt direct connection with ALT_SVC
            case Http3DiscoveryMode.ALT_SVC -> false;
            // always attempt direct connection with HTTP_3_ONLY, unless
            // it was attempted before and failed
            case Http3DiscoveryMode.HTTP_3_URI_ONLY ->
                    !hasNoH3(request.uri().getRawAuthority());
            // otherwise, attempt direct connection only if we have no
            // alt service and it wasn't attempted and failed before
            default -> lookupAltSvc(request).isEmpty()
                    && !hasNoH3(request.uri().getRawAuthority());
        };
    }
}
