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
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * A container for configuration information common to multiple {@link
 * HttpRequest}s. All requests are sent through a {@code HttpClient}.
 *
 * <p> {@code HttpClient}s are immutable and created from a builder returned
 * from {@link HttpClient#newBuilder()}. Request builders are created by calling
 * {@link HttpRequest#newBuilder() }.
 * <p>
 * See {@link HttpRequest} for examples of usage of this API.
 *
 * @since 9
 */
public abstract class HttpClient {

    /**
     * Creates an HttpClient.
     */
    protected HttpClient() {}

    /**
     * Returns a new HttpClient with default settings.
     *
     * @return a new HttpClient
     */
    public static HttpClient newHttpClient() {
        return new HttpClientBuilderImpl().build();
    }

    /**
     * Creates a new {@code HttpClient} builder.
     *
     * @return a {@code HttpClient.Builder}
     */
    public static Builder newBuilder() {
        return new HttpClientBuilderImpl();
    }

    /**
     * A builder of immutable {@link HttpClient}s. {@code HttpClient.Builder}s
     * are created by calling {@link HttpClient#newBuilder() }
     *
     * <p> Each of the setter methods in this class modifies the state of the
     * builder and returns <i>this</i> (ie. the same instance). The methods are
     * not synchronized and should not be called from multiple threads without
     * external synchronization.
     *
     * <p> {@link #build()} returns a new {@code HttpClient} each time it is
     * called.
     *
     * @since 9
     */
    public abstract static class Builder {

        protected Builder() {}

        /**
         * Sets a cookie manager.
         *
         * @param cookieManager the cookie manager
         * @return this builder
         */
        public abstract Builder cookieManager(CookieManager cookieManager);

        /**
         * Sets an {@code SSLContext}. If a security manager is set, then the caller
         * must have the {@link java.net.NetPermission NetPermission}
         * ({@code "setSSLContext"})
         *
         * <p> The effect of not calling this method, is that a default {@link
         * javax.net.ssl.SSLContext} is used, which is normally adequate for
         * client applications that do not need to specify protocols, or require
         * client authentication.
         *
         * @param sslContext the SSLContext
         * @return this builder
         * @throws SecurityException if a security manager is set and the
         *                           caller does not have any required permission
         */
        public abstract Builder sslContext(SSLContext sslContext);

        /**
         * Sets an {@code SSLParameters}. If this method is not called, then a default
         * set of parameters are used. The contents of the given object are
         * copied. Some parameters which are used internally by the HTTP protocol
         * implementation (such as application protocol list) should not be set
         * by callers, as they are ignored.
         *
         * @param sslParameters the SSLParameters
         * @return this builder
         */
        public abstract Builder sslParameters(SSLParameters sslParameters);

        /**
         * Sets the executor to be used for asynchronous tasks. If this method is
         * not called, a default executor is set, which is the one returned from {@link
         * java.util.concurrent.Executors#newCachedThreadPool()
         * Executors.newCachedThreadPool}.
         *
         * @param executor the Executor
         * @return this builder
         */
        public abstract Builder executor(Executor executor);

        /**
         * Specifies whether requests will automatically follow redirects issued
         * by the server. This setting can be overridden on each request. The
         * default value for this setting is {@link Redirect#NEVER NEVER}
         *
         * @param policy the redirection policy
         * @return this builder
         */
        public abstract Builder followRedirects(Redirect policy);

        /**
         * Requests a specific HTTP protocol version where possible. If not set,
         * the version defaults to {@link HttpClient.Version#HTTP_1_1}. If
         * {@link HttpClient.Version#HTTP_2} is set, then each request will
         * attempt to upgrade to HTTP/2.  If the upgrade succeeds, then the
         * response to this request will use HTTP/2 and all subsequent requests
         * and responses to the same
         * <a href="https://tools.ietf.org/html/rfc6454#section-4">origin server</a>
         * will use HTTP/2. If the upgrade fails, then the response will be
         * handled using HTTP/1.1
         *
         * @param version the requested HTTP protocol version
         * @return this builder
         */
        public abstract Builder version(HttpClient.Version version);

        /**
         * Sets the default priority for any HTTP/2 requests sent from this
         * client. The value provided must be between {@code 1} and {@code 256}
         * (inclusive).
         *
         * @param priority the priority weighting
         * @return this builder
         * @throws IllegalArgumentException if the given priority is out of range
         */
        public abstract Builder priority(int priority);

        /**
         * Sets a {@link java.net.ProxySelector} for this client. If no selector
         * is set, then no proxies are used. If a {@code null} parameter is
         * given then the system wide default proxy selector is used.
         *
         * @implNote {@link java.net.ProxySelector#of(InetSocketAddress)}
         * provides a {@code ProxySelector} which uses one proxy for all requests.
         *
         * @param selector the ProxySelector
         * @return this builder
         */
        public abstract Builder proxy(ProxySelector selector);

        /**
         * Sets an authenticator to use for HTTP authentication.
         *
         * @param a the Authenticator
         * @return this builder
         */
        public abstract Builder authenticator(Authenticator a);

        /**
         * Returns a {@link HttpClient} built from the current state of this
         * builder.
         *
         * @return this builder
         */
        public abstract HttpClient build();
    }


    /**
     * Returns an {@code Optional} which contains this client's {@link
     * CookieManager}. If no {@code CookieManager} was set in this client's builder,
     * then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's {@code CookieManager}
     */
    public abstract Optional<CookieManager> cookieManager();

    /**
     * Returns the follow-redirects setting for this client. The default value
     * for this setting is {@link HttpClient.Redirect#NEVER}
     *
     * @return this client's follow redirects setting
     */
    public abstract Redirect followRedirects();

    /**
     * Returns an {@code Optional} containing the {@code ProxySelector} for this client.
     * If no proxy is set then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's proxy selector
     */
    public abstract Optional<ProxySelector> proxy();

    /**
     * Returns the {@code SSLContext}, if one was set on this client. If a security
     * manager is set, then the caller must have the
     * {@link java.net.NetPermission NetPermission}("getSSLContext") permission.
     * If no {@code SSLContext} was set, then the default context is returned.
     *
     * @return this client's SSLContext
     * @throws SecurityException if the caller does not have permission to get
     *         the SSLContext
     */
    public abstract SSLContext sslContext();

    /**
     * Returns an {@code Optional} containing the {@link SSLParameters} set on
     * this client. If no {@code SSLParameters} were set in the client's builder,
     * then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's {@code SSLParameters}
     */
    public abstract Optional<SSLParameters> sslParameters();

    /**
     * Returns an {@code Optional} containing the {@link Authenticator} set on
     * this client. If no {@code Authenticator} was set in the client's builder,
     * then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's {@code Authenticator}
     */
    public abstract Optional<Authenticator> authenticator();

    /**
     * Returns the HTTP protocol version requested for this client. The default
     * value is {@link HttpClient.Version#HTTP_1_1}
     *
     * @return the HTTP protocol version requested
     */
    public abstract HttpClient.Version version();

    /**
     * Returns the {@code Executor} set on this client. If an {@code
     * Executor} was not set on the client's builder, then a default
     * object is returned. The default {@code Executor} is created independently
     * for each client.
     *
     * @return this client's Executor
     */
    public abstract Executor executor();

    /**
     * The HTTP protocol version.
     *
     * @since 9
     */
    public enum Version {

        /**
         * HTTP version 1.1
         */
        HTTP_1_1,

        /**
         * HTTP version 2
         */
        HTTP_2
    }

    /**
     * Defines automatic redirection policy. This is checked whenever a {@code 3XX}
     * response code is received. If redirection does not happen automatically
     * then the response is returned to the user, where it can be handled
     * manually.
     *
     * <p> {@code Redirect} policy is set via the {@link
     * HttpClient.Builder#followRedirects(HttpClient.Redirect)} method.
     *
     * @since 9
     */
    public enum Redirect {

        /**
         * Never redirect.
         */
        NEVER,

        /**
         * Always redirect.
         */
        ALWAYS,

        /**
         * Redirect to same protocol only. Redirection may occur from HTTP URLs
         * to other HTTP URLs, and from HTTPS URLs to other HTTPS URLs.
         */
        SAME_PROTOCOL,

        /**
         * Redirect always except from HTTPS URLs to HTTP URLs.
         */
        SECURE
    }

    /**
     * Sends the given request using this client, blocking if necessary to get
     * the response. The returned {@link HttpResponse}{@code <T>} contains the
     * response status, headers, and body ( as handled by given response body
     * handler ).
     *
     * @param <T> the response body type
     * @param req the request
     * @param responseBodyHandler the response body handler
     * @return the response body
     * @throws java.io.IOException if an I/O error occurs when sending or receiving
     * @throws java.lang.InterruptedException if the operation is interrupted
     */
    public abstract <T> HttpResponse<T>
    send(HttpRequest req, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException;

    /**
     * Sends the given request asynchronously using this client and the given
     * response handler.
     *
     * @param <T> the response body type
     * @param req the request
     * @param responseBodyHandler the response body handler
     * @return a {@code CompletableFuture<HttpResponse<T>>}
     */
    public abstract <T> CompletableFuture<HttpResponse<T>>
    sendAsync(HttpRequest req, HttpResponse.BodyHandler<T> responseBodyHandler);

    /**
     * Sends the given request asynchronously using this client and the given
     * multi response handler.
     *
     * @param <U> a type representing the aggregated results
     * @param <T> a type representing all of the response bodies
     * @param req the request
     * @param multiProcessor the MultiProcessor for the request
     * @return a {@code CompletableFuture<U>}
     */
    public abstract <U, T> CompletableFuture<U>
    sendAsync(HttpRequest req, HttpResponse.MultiProcessor<U, T> multiProcessor);

    /**
     * Creates a builder of {@link WebSocket} instances connected to the given
     * URI and receiving events and messages with the given {@code Listener}.
     *
     * <p> <b>Example</b>
     * <pre>{@code
     *     HttpClient client = HttpClient.newHttpClient();
     *     WebSocket.Builder builder = client.newWebSocketBuilder(
     *             URI.create("ws://websocket.example.com"),
     *             listener);
     * }</pre>
     *
     * <p> Finer control over the WebSocket Opening Handshake can be achieved
     * by using a custom {@code HttpClient}.
     *
     * <p> <b>Example</b>
     * <pre>{@code
     *     InetSocketAddress addr = new InetSocketAddress("proxy.example.com", 80);
     *     HttpClient client = HttpClient.newBuilder()
     *             .proxy(ProxySelector.of(addr))
     *             .build();
     *     WebSocket.Builder builder = client.newWebSocketBuilder(
     *             URI.create("ws://websocket.example.com"),
     *             listener);
     * }</pre>
     *
     * @implSpec The default implementation of this method throws {@code
     * UnsupportedOperationException}. However, clients obtained through
     * {@link HttpClient#newHttpClient()} or {@link HttpClient#newBuilder()}
     * provide WebSocket capability.
     *
     * @param uri
     *         the WebSocket URI
     * @param listener
     *         the listener
     *
     * @return a builder of {@code WebSocket} instances
     * @throws UnsupportedOperationException
     *         if this {@code HttpClient} does not provide WebSocket support
     */
    public WebSocket.Builder newWebSocketBuilder(URI uri,
                                                 WebSocket.Listener listener)
    {
        throw new UnsupportedOperationException();
    }
}
