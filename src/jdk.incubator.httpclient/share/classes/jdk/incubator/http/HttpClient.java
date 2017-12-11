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
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * A container for configuration information common to multiple {@link
 * HttpRequest}s. All requests are sent through a {@code HttpClient}.
 * {@Incubating}
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
     * <p> Equivalent to {@code newBuilder().build()}.
     *
     * <p> The default settings include: the "GET" request method, a preference
     * of {@linkplain HttpClient.Version#HTTP_2 HTTP/2}, a redirection policy of
     * {@linkplain Redirect#NEVER NEVER}, the {@linkplain
     * ProxySelector#getDefault() default proxy selector}, and the {@linkplain
     * SSLContext#getDefault() default SSL context}.
     *
     * @implNote The system-wide default values are retrieved at the time the
     * {@code HttpClient} instance is constructed. Changing the system-wide
     * values after an {@code HttpClient} instance has been built, for
     * instance, by calling {@link ProxySelector#setDefault(ProxySelector)}
     * or {@link SSLContext#setDefault(SSLContext)}, has no effect on already
     * built instances.
     *
     * @return a new HttpClient
     */
    public static HttpClient newHttpClient() {
        return newBuilder().build();
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
     * A builder of immutable {@link HttpClient}s.
     * {@Incubating}
     *
     * <p> Builders are created by invoking {@linkplain HttpClient#newBuilder()
     * newBuilder}. Each of the setter methods modifies the state of the builder
     * and returns the same instance. Builders are not thread-safe and should not be
     * used concurrently from multiple threads without external synchronization.
     *
     * @since 9
     */
    public abstract static class Builder {

        /**
         * A proxy selector that always return {@link Proxy#NO_PROXY} implying
         * a direct connection.
         * This is a convenience object that can be passed to {@link #proxy(ProxySelector)}
         * in order to build an instance of {@link HttpClient} that uses no
         * proxy.
         */
        public static final ProxySelector NO_PROXY = ProxySelector.of(null);

        /**
         * Creates a Builder.
         */
        protected Builder() {}

        /**
         * Sets a cookie handler.
         *
         * @param cookieHandler the cookie handler
         * @return this builder
         */
        public abstract Builder cookieHandler(CookieHandler cookieHandler);

        /**
         * Sets an {@code SSLContext}.
         *
         * <p> If this method is not invoked prior to {@linkplain #build()
         * building}, then newly built clients will use the {@linkplain
         * SSLContext#getDefault() default context}, which is normally adequate
         * for client applications that do not need to specify protocols, or
         * require client authentication.
         *
         * @param sslContext the SSLContext
         * @return this builder
         */
        public abstract Builder sslContext(SSLContext sslContext);

        /**
         * Sets an {@code SSLParameters}.
         *
         * <p> If this method is not invoked prior to {@linkplain #build()
         * building}, then newly built clients will use a default,
         * implementation specific, set of parameters.
         *
         * <p> Some parameters which are used internally by the HTTP Client
         * implementation (such as the application protocol list) should not be
         * set by callers, as they may be ignored. The contents of the given
         * object are copied.
         *
         * @param sslParameters the SSLParameters
         * @return this builder
         */
        public abstract Builder sslParameters(SSLParameters sslParameters);

        /**
         * Sets the executor to be used for asynchronous and dependent tasks.
         *
         * <p> If this method is not invoked prior to {@linkplain #build()
         * building}, a default executor is created for each newly built {@code
         * HttpClient}. The default executor uses a {@linkplain
         * Executors#newCachedThreadPool(ThreadFactory) cached thread pool},
         * with a custom thread factory.
         *
         * @implNote If a security manager has been installed, the thread
         * factory creates threads that run with an access control context that
         * has no permissions.
         *
         * @param executor the Executor
         * @return this builder
         */
        public abstract Builder executor(Executor executor);

        /**
         * Specifies whether requests will automatically follow redirects issued
         * by the server.
         *
         * <p> If this method is not invoked prior to {@linkplain #build()
         * building}, then newly built clients will use a default redirection
         * policy of {@link Redirect#NEVER NEVER}.
         *
         * @param policy the redirection policy
         * @return this builder
         */
        public abstract Builder followRedirects(Redirect policy);

        /**
         * Requests a specific HTTP protocol version where possible.
         *
         * <p> If this method is not invoked prior to {@linkplain #build()
         * building}, then newly built clients will prefer {@linkplain
         * Version#HTTP_2 HTTP/2}.
         *
         * <p> If set to {@linkplain Version#HTTP_2 HTTP/2}, then each request
         * will attempt to upgrade to HTTP/2. If the upgrade succeeds, then the
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
         * Sets a {@link java.net.ProxySelector}.
         *
         * @apiNote {@link ProxySelector#of(InetSocketAddress)}
         * provides a {@code ProxySelector} which uses a single proxy for all
         * requests. The system-wide proxy selector can be retrieved by
         * {@link ProxySelector#getDefault()}.
         *
         * @implNote
         * If this method is not invoked prior to {@linkplain #build()
         * building}, then newly built clients will use the {@linkplain
         * ProxySelector#getDefault() default proxy selector}, which
         * is normally adequate for client applications. This default
         * behavior can be turned off by supplying an explicit proxy
         * selector to this method, such as {@link #NO_PROXY} or one
         * returned by {@link ProxySelector#of(InetSocketAddress)},
         * before calling {@link #build()}.
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
         * Returns a new {@link HttpClient} built from the current state of this
         * builder.
         *
         * @return this builder
         */
        public abstract HttpClient build();
    }


    /**
     * Returns an {@code Optional} containing this client's {@linkplain
     * CookieHandler}. If no {@code CookieHandler} was set in this client's
     * builder, then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's {@code CookieHandler}
     */
    public abstract Optional<CookieHandler> cookieHandler();

    /**
     * Returns the follow redirects policy for this client. The default value
     * for client's built by builders that do not specify a redirect policy is
     * {@link HttpClient.Redirect#NEVER NEVER}.
     *
     * @return this client's follow redirects setting
     */
    public abstract Redirect followRedirects();

    /**
     * Returns an {@code Optional} containing the {@code ProxySelector}
     * supplied to this client. If no proxy selector was set in this client's
     * builder, then the {@code Optional} is empty.
     *
     * <p> Even though this method may return an empty optional, the {@code
     * HttpClient} may still have an non-exposed {@linkplain
     * Builder#proxy(ProxySelector) default proxy selector} that is
     * used for sending HTTP requests.
     *
     * @return an {@code Optional} containing the proxy selector supplied
     *        to this client.
     */
    public abstract Optional<ProxySelector> proxy();

    /**
     * Returns this client's {@code SSLContext}.
     *
     * <p> If no {@code SSLContext} was set in this client's builder, then the
     * {@linkplain SSLContext#getDefault() default context} is returned.
     *
     * @return this client's SSLContext
     */
    public abstract SSLContext sslContext();

    /**
     * Returns a copy of this client's {@link SSLParameters}.
     *
     * <p> If no {@code SSLParameters} were set in the client's builder, then an
     * implementation specific default set of parameters, that the client will
     * use, is returned.
     *
     * @return this client's {@code SSLParameters}
     */
    public abstract SSLParameters sslParameters();

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
     * value is {@link HttpClient.Version#HTTP_2}
     *
     * @return the HTTP protocol version requested
     */
    public abstract HttpClient.Version version();

    /**
     * Returns an {@code Optional} containing this client's {@linkplain
     * Executor}. If no {@code Executor} was set in the client's builder,
     * then the {@code Optional} is empty.
     *
     * <p> Even though this method may return an empty optional, the {@code
     * HttpClient} may still have an non-exposed {@linkplain
     * HttpClient.Builder#executor(Executor) default executor} that is used for
     * executing asynchronous and dependent tasks.
     *
     * @return an {@code Optional} containing this client's {@code Executor}
     */
    public abstract Optional<Executor> executor();

    /**
     * The HTTP protocol version.
     * {@Incubating}
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
     * Defines automatic redirection policy.
     * {@Incubating}
     *
     * <p> This is checked whenever a {@code 3XX} response code is received. If
     * redirection does not happen automatically then the response is returned
     * to the user, where it can be handled manually.
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
     * @throws IOException if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     * @throws IllegalArgumentException if the request method is not supported
     * @throws SecurityException If a security manager has been installed
     *          and it denies {@link java.net.URLPermission access} to the
     *          URL in the given request, or proxy if one is configured.
     *          See HttpRequest for further information about
     *          <a href="HttpRequest.html#securitychecks">security checks</a>.
     */
    public abstract <T> HttpResponse<T>
    send(HttpRequest req, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException;

    /**
     * Sends the given request asynchronously using this client and the given
     * response handler.
     *
     * <p> The returned completable future completes exceptionally with:
     * <ul>
     * <li>{@link IOException} - if an I/O error occurs when sending or receiving</li>
     * <li>{@link IllegalArgumentException} - if the request method is not supported</li>
     * <li>{@link SecurityException} - If a security manager has been installed
     *          and it denies {@link java.net.URLPermission access} to the
     *          URL in the given request, or proxy if one is configured.
     *          See HttpRequest for further information about
     *          <a href="HttpRequest.html#securitychecks">security checks</a>.</li>
     * </ul>
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
     * <p> The returned completable future completes exceptionally with:
     * <ul>
     * <li>{@link IOException} - if an I/O error occurs when sending or receiving</li>
     * <li>{@link IllegalArgumentException} - if the request method is not supported</li>
     * <li>{@link SecurityException} - If a security manager has been installed
     *          and it denies {@link java.net.URLPermission access} to the
     *          URL in the given request, or proxy if one is configured.
     *          See HttpRequest for further information about
     *          <a href="HttpRequest.html#securitychecks">security checks</a>.</li>
     * </ul>
     *
     * @param <U> a type representing the aggregated results
     * @param <T> a type representing all of the response bodies
     * @param req the request
     * @param multiSubscriber the multiSubscriber for the request
     * @return a {@code CompletableFuture<U>}
     */
    public abstract <U, T> CompletableFuture<U>
    sendAsync(HttpRequest req, HttpResponse.MultiSubscriber<U, T> multiSubscriber);

    /**
     * Creates a new {@code WebSocket} builder (optional operation).
     *
     * <p> <b>Example</b>
     * <pre>{@code
     *     HttpClient client = HttpClient.newHttpClient();
     *     CompletableFuture<WebSocket> ws = client.newWebSocketBuilder()
     *             .buildAsync(URI.create("ws://websocket.example.com"), listener);
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
     *     CompletableFuture<WebSocket> ws = client.newWebSocketBuilder()
     *             .buildAsync(URI.create("ws://websocket.example.com"), listener);
     * }</pre>
     *
     * <p> A {@code WebSocket.Builder} returned from this method is not safe for
     * use by multiple threads without external synchronization.
     *
     * @implSpec The default implementation of this method throws
     * {@code UnsupportedOperationException}. Clients obtained through
     * {@link HttpClient#newHttpClient()} or {@link HttpClient#newBuilder()}
     * return a {@code WebSocket} builder.
     *
     * @implNote Both builder and {@code WebSocket}s created with it operate in
     * a non-blocking fashion. That is, their methods do not block before
     * returning a {@code CompletableFuture}. Asynchronous tasks are executed in
     * this {@code HttpClient}'s executor.
     *
     * <p> When a {@code CompletionStage} returned from
     * {@link WebSocket.Listener#onClose Listener.onClose} completes,
     * the {@code WebSocket} will send a Close message that has the same code
     * the received message has and an empty reason.
     *
     * @return a {@code WebSocket.Builder}
     * @throws UnsupportedOperationException
     *         if this {@code HttpClient} does not provide WebSocket support
     */
    public WebSocket.Builder newWebSocketBuilder() {
        throw new UnsupportedOperationException();
    }
}
