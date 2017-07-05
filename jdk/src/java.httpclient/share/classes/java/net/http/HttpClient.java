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

package java.net.http;

import java.net.Authenticator;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.NetPermission;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * A container for configuration information common to multiple {@link
 * HttpRequest}s. All requests are associated with, and created from a {@code
 * HttpClient}.
 *
 * <p> {@code HttpClient}s are immutable and created from a builder returned
 * from {@link HttpClient#create()}. Request builders that are associated with
 * an application created client, are created by calling {@link #request(URI) }.
 * It is also possible to create a request builder directly which is associated
 * with the <i>default</i> {@code HttpClient} by calling {@link
 * HttpRequest#create(URI)}.
 *
 * <p> The HTTP API functions asynchronously (using {@link
 * java.util.concurrent.CompletableFuture}) and also in a simple synchronous
 * mode, where all work may be done on the calling thread. In asynchronous mode,
 * work is done on the threads supplied by the client's {@link
 * java.util.concurrent.ExecutorService}.
 *
 * <p> <a name="defaultclient"></a> The <i>default</i> {@code HttpClient} is
 * used whenever a request is created without specifying a client explicitly
 * (by calling {@link HttpRequest#create(java.net.URI) HttpRequest.create}).
 * There is only one static instance of this {@code HttpClient}. A reference to
 * the default client can be obtained by calling {@link #getDefault() }. If a
 * security manager is set, then a permission is required for this.
 *
 * <p> See {@link HttpRequest} for examples of usage of this API.
 *
 * @since 9
 */
public abstract class HttpClient {

    HttpClient() {}

    private static HttpClient defaultClient;

    /**
     * Creates a new {@code HttpClient} builder.
     *
     * @return a {@code HttpClient.Builder}
     */
    public static Builder  create() {
        return new HttpClientBuilderImpl();
    }

    //public abstract void debugPrint();

    /**
     * Returns the default {@code HttpClient} that is used when a {@link
     * HttpRequest} is created without specifying a client. If a security
     * manager is set, then its {@code checkPermission} method is called with a
     * {@link java.net.NetPermission} specifying the name "getDefaultHttpClient".
     * If the caller does not possess this permission a {@code SecurityException}
     * is thrown.
     *
     * @implNote Code running under a security manager can avoid the security
     * manager check by creating a {@code HttpClient} explicitly.
     *
     * @return the default {@code HttpClient}
     * @throws SecurityException if the caller does not have the required
     *                           permission
     */
    public synchronized static HttpClient getDefault() {
        Utils.checkNetPermission("getDefaultHttpClient");
        if (defaultClient == null) {
            Builder b = create();
            defaultClient = b.executorService(Executors.newCachedThreadPool())
                             .build();
        }
        return defaultClient;
    }

    /**
     * Creates a {@code HttpRequest} builder associated with this client.
     *
     * @return a new builder
     */
    public abstract HttpRequest.Builder request();

    /**
     * Creates a {@code HttpRequest} builder associated with this client and
     * using the given request URI.
     *
     * @param uri the request URI
     * @return a new builder
     */
    public abstract HttpRequest.Builder request(URI uri);

    /**
     * A builder of immutable {@link HttpClient}s. {@code HttpClient.Builder}s
     * are created by calling {@link HttpClient#create()}.
     *
     * <p> Each of the setter methods in this class modifies the state of the
     * builder and returns <i>this</i> (ie. the same instance). The methods are
     * not synchronized and should not be called from multiple threads without
     * external synchronization.
     *
     * <p> {@link #build() } returns a new {@code HttpClient} each time it is
     * called.
     *
     * @since 9
     */
    public abstract static class Builder {

        Builder() {}

        /**
         * Sets a cookie manager.
         *
         * @param manager the CookieManager
         * @return this builder
         * @throws NullPointerException if {@code manager} is null
         */
        public abstract Builder cookieManager(CookieManager manager);

        /**
         * Sets an SSLContext. If a security manager is set, then the caller
         * must have the {@link java.net.NetPermission NetPermission}
         * ("setSSLContext")
         *
         * <p> The effect of not calling this method, is that a default {@link
         * javax.net.ssl.SSLContext} is used, which is normally adequate for
         * client applications that do not need to specify protocols, or require
         * client authentication.
         *
         * @param sslContext the SSLContext
         * @return this builder
         * @throws NullPointerException if {@code sslContext} is null
         * @throws SecurityException if a security manager is set and the
         *                           caller does not have any required permission
         */
        public abstract Builder sslContext(SSLContext sslContext);

        /**
         * Sets an SSLParameters. If this method is not called, then a default
         * set of parameters are used. The contents of the given object are
         * copied. Some parameters which are used internally by the HTTP protocol
         * implementation (such as application protocol list) should not be set
         * by callers, as they are ignored.
         *
         * @param sslParameters the SSLParameters
         * @return this builder
         * @throws NullPointerException if {@code sslParameters} is null
         */
        public abstract Builder sslParameters(SSLParameters sslParameters);

        /**
         * Sets the ExecutorService to be used for sending and receiving
         * asynchronous requests. If this method is not called, a default
         * executor service is set, which is the one returned from {@link
         * java.util.concurrent.Executors#newCachedThreadPool()
         * Executors.newCachedThreadPool}.
         *
         * @param s the ExecutorService
         * @return this builder
         * @throws NullPointerException if {@code s} is null
         */
        public abstract Builder executorService(ExecutorService s);

        /**
         * Specifies whether requests will automatically follow redirects issued
         * by the server. This setting can be overridden on each request. The
         * default value for this setting is {@link Redirect#NEVER NEVER}
         *
         * @param policy the redirection policy
         * @return this builder
         * @throws NullPointerException if {@code policy} is null
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
         * <p>This setting can be over-ridden per request.
         *
         * @param version the requested HTTP protocol version
         * @return this builder
         * @throws NullPointerException if {@code version} is null
         */
        public abstract Builder version(HttpClient.Version version);

        /**
         * Sets the default priority for any HTTP/2 requests sent from this
         * client. The value provided must be between {@code 1} and {@code 255}.
         *
         * @param priority the priority weighting
         * @return this builder
         * @throws IllegalArgumentException if the given priority is out of range
         */
        public abstract Builder priority(int priority);

        /**
         * Enables pipelining mode for HTTP/1.1 requests sent through this
         * client. When pipelining is enabled requests to the same destination
         * are sent over existing TCP connections that may already have requests
         * outstanding. This reduces the number of connections, but may have
         * a performance impact since responses must be delivered in the same
         * order that they were sent. By default, pipelining is disabled.
         *
         * @param enable {@code true} enables pipelining
         * @return this builder
         * @throws UnsupportedOperationException if pipelining mode is not
         *                                       supported by this implementation
         */
        public abstract Builder pipelining(boolean enable);

        /**
         * Sets a {@link java.net.ProxySelector} for this client. If no selector
         * is set, then no proxies are used. If a {@code null} parameter is
         * given then the system wide default proxy selector is used.
         *
         * @implNote {@link java.net.ProxySelector#of(InetSocketAddress)}
         * provides a ProxySelector which uses one proxy for all requests.
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
     * CookieManager}. If no CookieManager was set in this client's builder,
     * then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's CookieManager
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
     * Returns an {@code Optional} containing the ProxySelector for this client.
     * If no proxy is set then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's proxy selector
     */
    public abstract Optional<ProxySelector> proxy();

    /**
     * Returns the SSLContext, if one was set on this client. If a security
     * manager is set then then caller must then the caller must have the
     * {@link java.net.NetPermission NetPermission}("getSSLContext") permission.
     * If no SSLContext was set, then the default context is returned.
     *
     * @return this client's SSLContext
     */
    public abstract SSLContext sslContext();

    /**
     * Returns an {@code Optional} containing the {@link SSLParameters} set on
     * this client. If no {@code SSLParameters} were set in the client's builder,
     * then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's SSLParameters
     */
    public abstract Optional<SSLParameters> sslParameters();

    /**
     * Returns an {@code Optional} containing the {@link Authenticator} set on
     * this client. If no {@code Authenticator} was set in the client's builder,
     * then the {@code Optional} is empty.
     *
     * @return an {@code Optional} containing this client's Authenticator
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
     * Returns whether this client supports HTTP/1.1 pipelining.
     *
     * @return whether pipelining allowed
     */
    public abstract boolean pipelining();

    /**
     * Returns the {@code ExecutorService} set on this client. If an {@code
     * ExecutorService} was not set on the client's builder, then a default
     * object is returned. The default ExecutorService is created independently
     * for each client.
     *
     * @return this client's ExecutorService
     */
    public abstract ExecutorService executorService();

    /**
     * The HTTP protocol version.
     *
     * @since 9
     */
    public static enum Version {

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
     * Defines automatic redirection policy. This is checked whenever a 3XX
     * response code is received. If redirection does not happen automatically
     * then the response is returned to the user, where it can be handled
     * manually.
     *
     * <p> {@code Redirect} policy is set via the {@link
     * HttpClient.Builder#followRedirects(HttpClient.Redirect)} method.
     *
     * @since 9
     */
    public static enum Redirect {

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

}
