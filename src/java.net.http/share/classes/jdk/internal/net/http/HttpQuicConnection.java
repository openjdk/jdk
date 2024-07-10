/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.NetworkChannel;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;

import jdk.internal.net.http.ConnectionPool.CacheKey;
import jdk.internal.net.http.AltServicesRegistry.AltService;
import jdk.internal.net.http.common.FlowTube;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.ConnectionTerminator;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.QuicConnection;

import static jdk.internal.net.http.Http3ClientProperties.MAX_DIRECT_CONNECTION_TIMEOUT;
import static jdk.internal.net.http.common.Alpns.H3;
import static jdk.internal.net.http.http3.Http3Error.H3_INTERNAL_ERROR;
import static jdk.internal.net.http.http3.Http3Error.H3_NO_ERROR;
import static jdk.internal.net.http.quic.TerminationCause.appLayerClose;
import static jdk.internal.net.http.quic.TerminationCause.appLayerException;

/**
 * An {@code HttpQuicConnection} models an HTTP connection over
 * QUIC.
 * The particulars of the HTTP/3 protocol are handled by the
 * Http3Connection class.
 */
abstract class HttpQuicConnection extends HttpConnection {

    final Logger debug = Utils.getDebugLogger(this::quicDbgString);

    final QuicConnection quicConnection;
    final ConnectionTerminator quicConnTerminator;
    // the alt-service which was advertised, from some origin, for this connection co-ordinates.
    // can be null, which indicates this wasn't created because of an alt-service
    private final AltService sourceAltService;
    // HTTP/2 MUST use TLS version 1.3 or higher for HTTP/3 over TLS
    private static final Predicate<String> testRequiredHTTP3TLSVersion = proto ->
            proto.equals("TLSv1.3");


    HttpQuicConnection(InetSocketAddress address, HttpClientImpl client,
                       QuicConnection quicConnection, AltService sourceAltService) {
        super(address, client);
        Objects.requireNonNull(quicConnection);
        this.quicConnection = quicConnection;
        this.quicConnTerminator = quicConnection.connectionTerminator();
        this.sourceAltService = sourceAltService;
    }

    /**
     * A HTTP QUIC connection could be created due to an alt-service that was advertised
     * from some origin. This method returns that source alt-service if there was one.
     * @return The source alt-service if present
     */
    Optional<AltService> getSourceAltService() {
        return Optional.ofNullable(this.sourceAltService);
    }

    @Override
    public List<SNIServerName> getSNIServerNames() {
        final SSLParameters sslParams = this.quicConnection.getTLSEngine().getSSLParameters();
        if (sslParams == null) {
            return List.of();
        }
        final List<SNIServerName> sniServerNames = sslParams.getServerNames();
        if (sniServerNames == null) {
            return List.of();
        }
        return List.copyOf(sniServerNames);
    }

    final String quicDbgString() {
        String tag = dbgTag;
        if (tag == null) tag = dbgTag = "Http" + quicConnection.dbgTag();
        return tag;
    }

    /**
     * Initiates the connect phase.
     *
     * Returns a CompletableFuture that completes when the underlying
     * TCP connection has been established or an error occurs.
     */
    public abstract CompletableFuture<Void> connectAsync(Exchange<?> exchange);

    /**
     * Finishes the connection phase.
     *
     * Returns a CompletableFuture that completes when any additional,
     * type specific, setup has been done. Must be called after connectAsync. */
    public abstract CompletableFuture<Void> finishConnect();

    /** Tells whether, or not, this connection is connected to its destination. */
    boolean connected() {
        return quicConnection.connected();
    }

    /** Tells whether, or not, this connection is secure ( over SSL ) */
    final boolean isSecure() { return true; } // QUIC is secure

    /**
     * Tells whether, or not, this connection is proxied.
     * Returns true for tunnel connections, or clear connection to
     * any host through proxy.
     */
    final boolean isProxied() { return false;} // Proxy not supported

    /**
     * Returns the address of the proxy used by this connection.
     * Returns the proxy address for tunnel connections, or
     * clear connection to any host through proxy.
     * Returns {@code null} otherwise.
     */
    final InetSocketAddress proxy() { return null; } // Proxy not supported

    /**
     * This method throws an {@link UnsupportedOperationException}
     */
    @Override
    final HttpPublisher publisher() {
        throw new UnsupportedOperationException("no publisher for a quic connection");
    }

    QuicConnection quicConnection() {
        return quicConnection;
    }

    /**
     * {@return an hexadecimal string identifying the underlying
     *          quic connection}
     * @apiNote This is usually the string representation of the
     *          underlying local quic connection id bytes.
     */
    public String toHexString() {
        return quicConnection.toHexString();
    }

    /**
     * Returns true if the given client's SSL parameter protocols contains at
     * least one TLS version that HTTP/3 requires.
     */
    private static boolean hasRequiredHTTP3TLSVersion(HttpClient client) {
        String[] protos = client.sslParameters().getProtocols();
        if (protos != null) {
            return Arrays.stream(protos).anyMatch(testRequiredHTTP3TLSVersion);
        } else {
            return false;
        }
    }

    /**
     * Called when the HTTP/3 connection is established, either successfully or
     * unsuccessfully
     * @apiNote
     *   In case of a direct connection attempt we want to downgrade to
     *   HTTP/2 if the connection over Quic was unsuccessful.
     * @param  connection the HTTP/3 connection, if successful, or null, otherwise
     * @param  throwable  the excpetion encountered, if unsuccessful
     * @return a completable future - either completed with the connection, if
     *         successful, or with null, if downgrading to HTTP/2 is desired,
     *         or completed exceptionally, if the operation should fail.
     */
    public abstract CompletableFuture<Http3Connection> connectionEstablished(Http3Connection connection,
                                                                    Throwable throwable);

    /**
     * A functional interface used to update the Alternate Service Registry
     * after a direct connection attempt.
     */
    @FunctionalInterface
    private interface DirectConnectionUpdater {
        /**
         * This method can be used to implement the semantics of
         * {@link HttpQuicConnection#connectionEstablished(Http3Connection, Throwable)}.
         *
         * This method may additionally update the HttpClient registry, or
         * {@linkplain Http3ClientImpl#noH3(String) record the unsuccessful}
         * direct connection attempt.
         *
         * @param conn       the connection or null
         * @param throwable  the exception or null
         * @return a completable future completed as specified in
         *  {@link HttpQuicConnection#connectionEstablished(Http3Connection, Throwable)}
         */
        CompletableFuture<Http3Connection> onConnectionEstablished(
                Http3Connection conn, Throwable throwable);

        /**
         * Trivially returns a failed or completed future depending on
         * whether the throwable argument is non-null.
         * @param conn       the connection
         * @param throwable  the exception
         * @return a completed future
         */
        static CompletableFuture<Http3Connection> noUpdate(
                Http3Connection conn, Throwable throwable) {
            if (throwable != null) {
                return MinimalFuture.failedFuture(Utils.getCompletionCause(throwable));
            }
            return MinimalFuture.completedFuture(conn);
        }
    }

    /**
     * This method create and return a new unconnected HttpQuicConnection,
     * wrapping a {@link QuicConnection}. May return {@code null} if
     * HTTP/3 is not supported with the given parameters. For instance,
     * if TLSv1.3 isn't available/enabled in the client's SSLParameters,
     * or if ALT_SERVICE is required but no alt service is found.
     *
     * @param addr     the HTTP/3 peer endpoint address, if direct connection
     * @param proxy    the proxy address, if a proxy is used, in which case this
     *                 method will return {@code null} as proxying is not supported
     *                 with HTTP/3
     * @param request  the request for which the connection is being created
     * @param exchange the exchange for which the connection is being created
     * @param client   the HttpClientImpl instance
     * @return A new HttpQuicConnection or {@code null}
     */
    public static HttpQuicConnection getHttpQuicConnection(final InetSocketAddress addr,
                                                           final InetSocketAddress proxy,
                                                           final HttpRequestImpl request,
                                                           final Exchange<?> exchange,
                                                           final HttpClientImpl client) {
        if (!client.client3().isPresent()) {
            if (Log.http3()) {
                Log.logHttp3("HTTP3 isn't supported by the client");
            }
            return null;
        }

        final Http3ClientImpl h3client = client.client3().get();
        // HTTP_3 with proxy not supported; In this case we will downgrade
        // to using HTTP/2
        var debug = h3client.debug();
        var where = "HttpQuicConnection.getHttpQuicConnection";
        if (proxy != null || !hasRequiredHTTP3TLSVersion(client)) {
            if (debug.on())
                debug.log("%s: proxy required or SSL version mismatch", where);
            return null;
        }

        assert request.secure();
        // Question: reread RFC to figure out whether we need this scaffolding
        // I mean - could Http3Connection and HttpQuicConnection be the same
        // object?
        // Answer: Http3Connection models an established connection which is
        //         ready to be used.
        //         HttpQuicConnection serves at establishing a new Http3Connection
        //         => Http3Connection is pooled, HttpQuicConnection is not.
        //         => Do we need HttpQuicConnection vs QuicConnection?
        //         => yes: HttpQuicConnection can access all package protected
        //                 APIs in HttpConnection & al
        //                 QuicConnection is in the quic subpackage.
        //                 HttpQuicConnection makes the necessary adaptation between
        //                 HttpConnection and QuicConnection.

        // find whether we have an alternate service access point for HTTP/3
        // if we do, create a new QuicConnection and a new Http3Connection over it.
        var uri = request.uri();
        // we only support H3 right now
        var altSvc = client.registry()
                .lookup(uri, H3::equals)
                .filter(exchange::isPermitted)
                .findFirst().orElse(null);
        if (debug.on()) debug.log("%s: ALT-SVC: %s", where, altSvc);
        if (altSvc == null && Log.altsvc()) {
            Log.logAltSvc("No AltService found for {0}", uri.getRawAuthority());
        }
        Optional<Duration> directTimeout = Optional.empty();
        final boolean advertisedAltSvc = altSvc != null && altSvc.wasAdvertised();
        var config = request.http3Discovery();
        switch (config) {
            case HTTP_3_ALT_SVC: {
                if (!advertisedAltSvc) {
                    // fallback to HTTP/2
                    if (altSvc != null) {
                        if (Log.altsvc()) {
                            Log.logAltSvc("{0}: Cannot use unadvertised AltService: {1}",
                                    config, altSvc);
                        }
                    }
                    return null;
                }
                assert altSvc != null && altSvc.wasAdvertised();
                break;
            }
            // attempt direct connection if HTTP/3 only
            case HTTP_3_ONLY: {
                if (advertisedAltSvc && !altSvc.originHasSameAuthority()) {
                    if (altSvc != null) {
                        if (Log.altsvc()) {
                            Log.logAltSvc("{0}: Cannot use advertised AltService: {1}",
                                    config, altSvc);
                        }
                        altSvc = null;
                    }
                }
                assert altSvc == null || altSvc.originHasSameAuthority();
                break;
            }
            default: {
                // if direct connection already attempted and failed,
                // fallback to HTTP/2
                if (altSvc == null && h3client.hasNoH3(uri.getRawAuthority())) {
                    return null;
                }
                if (!advertisedAltSvc) {
                    // directTimeout is only used for happy eyeball
                    Duration def = Duration.ofMillis(MAX_DIRECT_CONNECTION_TIMEOUT);
                    Duration timeout = client.connectTimeout()
                            .map((d) -> d.compareTo(def) > 0 ? def : d)
                            .orElse(def);
                    directTimeout = Optional.of(timeout);
                }
                break;
            }
        }

        if (altSvc != null) {
            assert H3.equals(altSvc.alpn());
            Log.logAltSvc("{0}: Using AltService for {1}: {2}",
                    config, uri.getRawAuthority(), altSvc);
        }
        final QuicConnection quicConnection = (altSvc != null) ?
                h3client.quicClient().createConnectionFor(altSvc) :
                h3client.quicClient().createConnectionFor(addr, new String[] {H3});
        if (debug.on()) debug.log("%s: QuicConnection: %s", where, quicConnection);
        final DirectConnectionUpdater onConnectFinished = advertisedAltSvc
                                ? DirectConnectionUpdater::noUpdate
                                : (c,t) -> registerUnadvertised(client, uri, addr, c, t);
        // Note: we could get rid of the updater by introducing
        //       H3DirectQuicConnectionImpl extends H3QuicConnectionImpl
        HttpQuicConnection httpQuicConn = new H3QuicConnectionImpl(addr, client,
                    quicConnection, onConnectFinished, directTimeout, altSvc);
        // if we created a connection and if that connection is to an (advertised) alt service then
        // we setup the Exchange's request to include the "alt-used" header to refer to the
        // alt service that was used (section 5, RFC-7838)
        if (httpQuicConn != null && altSvc != null && advertisedAltSvc) {
            exchange.request().setSystemHeader("alt-used", altSvc.authority());
        }
        return httpQuicConn;
    }

    static CompletableFuture<Http3Connection> registerUnadvertised(final HttpClientImpl client,
                                                                   final URI requestURI,
                                                                   final InetSocketAddress destAddr,
                                                                   final Http3Connection connection,
                                                                   final Throwable t) {
        final URI originURI = requestURI.resolve("/");
        if (t == null && connection != null) {
            // There is an h3 endpoint at the given origin: update the registry
            var origin = AltServicesRegistry.Origin.from(originURI);
            assert origin.port() == destAddr.getPort();
            var id = new AltService.Identity(H3, origin.host(), origin.port());
            var altSvc = client.registry().registerUnadvertised(id, origin, connection.connection());
            return MinimalFuture.completedFuture(connection);
        }
        if (t != null) {
            assert client.client3().isPresent() : "HTTP3 isn't supported by the client";
            // record that there is no h3 at the given origin
            client.client3().get().noH3(originURI.getRawAuthority());
            // do not downgrade to HTTP/2, since HTTP/2 is attempted in
            // parallel
            return MinimalFuture.failedFuture(t);
        }
        return MinimalFuture.completedFuture(connection);
    }

    // TODO: we could probably merge H3QuicConnectionImpl with HttpQuicConnection now
    static class H3QuicConnectionImpl extends HttpQuicConnection {
        private final Optional<Duration> directTimeout;
        private final DirectConnectionUpdater connFinishedAction;
        H3QuicConnectionImpl(InetSocketAddress address,
                             HttpClientImpl client,
                             QuicConnection quic,
                             DirectConnectionUpdater connFinishedAction,
                             Optional<Duration> directTimeout,
                             AltService sourceAltService) {
            super(address, client, quic, sourceAltService);
            this.directTimeout = directTimeout;
            this.connFinishedAction = connFinishedAction;
        }

        @Override
        public CompletableFuture<Void> connectAsync(Exchange<?> exchange) {
            var request = exchange.request();
            var uri = request.uri();
            assert quicConnection instanceof QuicConnectionImpl
                    : "unexpected QUIC connection type: " + quicConnection.getClass();
            // Adapt HandshakeCF to CompletableFuture<Void>
            CompletableFuture<CompletableFuture<Void>> handshakeCfCf =
                    ((QuicConnectionImpl) quicConnection).startHandshake()
                    .handle((r, t) -> {
                        if (t == null) {
                            // successful handshake
                            return MinimalFuture.completedFuture(r);
                        }
                        final TerminationCause terminationCause = quicConnection.terminationCause();
                        final boolean appLayerTermination = terminationCause != null
                                && terminationCause.isAppLayer();
                        // QUIC connection handshake failed. we now decide whether we should
                        // unregister the alt-service (if any) that was the source of this
                        // connection attempt.
                        //
                        // handshake could have failed for one of several reasons, some of them:
                        // - something at QUIC layer caused the failure (either some internal
                        //   exception or protocol error or QUIC TLS error)
                        // - or the app layer, through the HttpClient/HttpConnection
                        //   could have triggered a connection close.
                        //
                        // we unregister the alt-service (if any) only if the termination cause
                        // originated in the QUIC layer. An app layer termination cause doesn't
                        // necessarily mean that the alt-service isn't valid for subsequent use.
                        if (!appLayerTermination && this.getSourceAltService().isPresent()) {
                            final AltService altSvc = this.getSourceAltService().get();
                            if (debug.on()) {
                                debug.log("connection attempt to an alternate service at "
                                        + altSvc.authority() + " failed during handshake: " + t);
                            }
                            client().registry().markInvalid(this.getSourceAltService().get());
                            // fail with ConnectException to allow the request to potentially
                            // be retried on a different connection
                            final ConnectException connectException = new ConnectException(
                                    "QUIC connection handshake to an alternate service failed");
                            connectException.initCause(t);
                            return MinimalFuture.failedFuture(connectException);
                        } else {
                            // alt service wasn't the cause of this failed connection attempt.
                            // return a failed future with the original cause
                            return MinimalFuture.failedFuture(t);
                        }
                    })
                    .thenApply((handshakeCompletion) -> {
                        if (handshakeCompletion.isCompletedExceptionally()) {
                            return MinimalFuture.failedFuture(handshakeCompletion.exceptionNow());
                        }
                        return MinimalFuture.completedFuture(null);
                    });

            // In case of direct connection, set up a timeout on the handshakeReachedPeerCf,
            // and arrange for it to complete the handshakeCfCf above with a timeout in
            // case that timeout expires...
            if (directTimeout.isPresent()) {
                debug.log("setting up quic direct connect timeout: " + directTimeout.get().toMillis());
                var handshakeReachedPeerCf = quicConnection.handshakeReachedPeer();
                CompletableFuture<CompletableFuture<Void>> fxi2 = handshakeReachedPeerCf
                        .thenApply((unused) -> MinimalFuture.completedFuture(null));
                fxi2 = fxi2.completeOnTimeout(
                        MinimalFuture.failedFuture(new HttpConnectTimeoutException("quic handshake timeout")),
                        directTimeout.get().toMillis(), TimeUnit.MILLISECONDS);
                fxi2.handleAsync((r, t) -> {
                    if (t != null) {
                        var cause = Utils.getCompletionCause(t);
                        // arrange for handshakeCfCf to timeout
                        handshakeCfCf.completeExceptionally(cause);
                    }
                    if (r.isCompletedExceptionally()) {
                        var cause = Utils.getCompletionCause(r.exceptionNow());
                        // arrange for handshakeCfCf to timeout
                        handshakeCfCf.completeExceptionally(cause);
                    }
                    return r;
                }, exchange.parentExecutor.safeDelegate());
            }

            Optional<Duration> timeout = client().connectTimeout();
            CompletableFuture<CompletableFuture<Void>> fxi = handshakeCfCf;

            // In case of connection timeout, set up a timeout on the handshakeCfCf.
            // Note: this is a different timeout than the direct connection timeout.
            if (timeout.isPresent()) {
                // In case of timeout we need to close the quic connection
                debug.log("setting up quic connect timeout: " + timeout.get().toMillis());
                fxi = handshakeCfCf.completeOnTimeout(
                        MinimalFuture.failedFuture(new HttpConnectTimeoutException("quic connect timeout")),
                        timeout.get().toMillis(), TimeUnit.MILLISECONDS);
            }

            // If we have set up any timeout, arrange to close the quicConnection
            // if one of the timeout expires
            if (timeout.isPresent() || directTimeout.isPresent()) {
                fxi = fxi.handleAsync(this::handleTimeout, exchange.parentExecutor.safeDelegate());
            }
            return fxi.thenCompose(Function.identity());
        }

        @Override
        public CompletableFuture<Http3Connection> connectionEstablished(Http3Connection connection,
                                                                        Throwable throwable) {
            return connFinishedAction.onConnectionEstablished(connection, throwable);
        }

        private <U> CompletableFuture<U> handleTimeout(CompletableFuture<U> r, Throwable t) {
            if (t != null) {
                if (Utils.getCompletionCause(t) instanceof HttpConnectTimeoutException te) {
                    debug.log("Timeout expired: " + te);
                    close(H3_NO_ERROR.code(), "timeout expired", te);
                    return MinimalFuture.failedFuture(te);
                }
                return MinimalFuture.failedFuture(t);
            } else if (r.isCompletedExceptionally()) {
                t = r.exceptionNow();
                if (Utils.getCompletionCause(t) instanceof HttpConnectTimeoutException te) {
                    debug.log("Completed in timeout: " + te);
                    close(H3_NO_ERROR.code(), "timeout expired", te);
                }
            }
            return r;
        }


        @Override
        public CompletableFuture<Void> finishConnect() {
            return quicConnection.finishConnect();
        }

        @Override
        NetworkChannel /* DatagramChannel */ channel() {
            // Note: revisit this
            //       - don't return a new instance each time
            //       - see if we could avoid exposing
            //         the channel in the first place
            H3QuicConnectionImpl self = this;
            return new NetworkChannel() {
                @Override
                public NetworkChannel bind(SocketAddress local) throws IOException {
                    throw new UnsupportedOperationException("no bind for a quic connection");
                }

                @Override
                public SocketAddress getLocalAddress() throws IOException {
                    return quicConnection.localAddress();
                }

                @Override
                public <T> NetworkChannel setOption(SocketOption<T> name, T value) throws IOException {
                    return this;
                }

                @Override
                public <T> T getOption(SocketOption<T> name) throws IOException {
                    return null;
                }

                @Override
                public Set<SocketOption<?>> supportedOptions() {
                    return Set.of();
                }

                @Override
                public boolean isOpen() {
                    return quicConnection.isOpen();
                }

                @Override
                public void close() throws IOException {
                    self.close();
                }
            };
        }

        @Override
        CacheKey cacheKey() {
            return null;
        }

        // close with H3_NO_ERROR
        @Override
        public final void close() {
            close(H3_NO_ERROR.code(), "connection closed", null);
        }

        @Override
        void close(final Throwable cause) {
            close(H3_INTERNAL_ERROR.code(), null, cause);
        }
    }

    /* Tells whether this connection is a tunnel through a proxy */
    boolean isTunnel() { return false; }

    abstract NetworkChannel /* DatagramChannel */ channel();

    abstract ConnectionPool.CacheKey cacheKey();

    /**
     * Closes the underlying transport connection with
     * the given {@code connCloseCode} code. This will be considered a application
     * layer close and will generate a {@code ConnectionCloseFrame}
     * of type {@code 0x1d} as the cause of the termination.
     *
     * @param connCloseCode the connection close code
     * @param logMsg        the message to be included in the logs as
     *                      the cause of the connection termination. can be null.
     * @param closeCause    the underlying cause of the connection termination. can be null,
     *                      in which case just the {@code error} will be recorded as the
     *                      cause of the connection termination.
     */
    final void close(final long connCloseCode, final String logMsg,
                     final Throwable closeCause) {
        final TerminationCause terminationCause;
        if (closeCause == null) {
            terminationCause = appLayerClose(connCloseCode);
        } else {
            terminationCause = appLayerException(connCloseCode, closeCause);
        }
        // set the log message only if non-null, else let it default to internal
        // implementation sensible default
        if (logMsg != null) {
            terminationCause.loggedAs(logMsg);
        }
        quicConnTerminator.terminate(terminationCause);
    }

    abstract void close(final Throwable t);

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * Unlike HTTP/1.1 and HTTP/2, an HTTP/3 connection is not
     * built on a single connection flow, since multiplexing is
     * provided by the lower layer. Therefore, the higher HTTP
     * layer should never call {@code getConnectionFlow()} on an
     * {@link HttpQuicConnection}. As a consequence, this method
     * always throws {@link IllegalStateException} unconditionally.
     *
     * @return nothing: this method always throw {@link IllegalStateException}
     *
     * @throws IllegalStateException always
     */
    @Override
    final FlowTube getConnectionFlow() {
        throw new IllegalStateException(
                "An HTTP/3 connection does not expose " +
                "a single connection flow");
    }

    /**
     * Unlike HTTP/1.1 and HTTP/2, an HTTP/3 connection is not
     * built on a single connection flow, since multiplexing is
     * provided by the lower layer. This method instead will
     * return {@code true} if the underlying quic connection
     * has been terminated, either exceptionally or normally.
     *
     * @return {@code true} if the underlying Quic connection
     *  has been terminated.
     */
    @Override
    boolean isFlowFinished() {
        return !quicConnection().isOpen();
    }

    @Override
    public String toString() {
        return quicDbgString();
    }

    @Override
    public String connectionLabel() {
        // The quicDbgString() appears in the logs, the toHexString() is
        // returned as part of Http3PushId - so here we return a combination
        // of both
        //
        // TODO: possibly log the correspondence between
        //       quicDbgString and toHexString when creating the
        //       connection, and then only use toHexString here?
        //
        return quicDbgString() + "[connectionId=" + toHexString() + "]";
    }
}
