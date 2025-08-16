/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.Builder;

/**
 * This interface is used to provide additional request configuration
 * option hints on how an HTTP request/response exchange should
 * be carried out by the {@link HttpClient} implementation.
 * Request configuration option hints can be provided to an
 * {@link HttpRequest} with the {@link
 * Builder#setOption(HttpOption, Object) HttpRequest.Builder
 * setOption} method.
 *
 * <p> Concrete instances of this class and its subclasses are immutable.
 *
 * @apiNote
 * In this version, the {@code HttpOption} interface is sealed and
 * only allows the {@link #H3_DISCOVERY} option. However, it could be
 * extended in the future to support additional options.
 * <p>
 * The {@link #H3_DISCOVERY} option can be used to help the
 * {@link HttpClient} decide how to select or establish an
 * HTTP/3 connection through which to carry out an HTTP/3
 * request/response exchange.
 *
 * @param <T> The {@linkplain #type() type of the option value}
 *
 * @since TBD
 */
public sealed interface HttpOption<T> permits HttpRequestOptionImpl {
    /**
     * {@return the option name}
     *
     * @implSpec Different options must have different names.
     */
    String name();

    /**
     * {@return the type of the value associated with the option}
     *
     * @apiNote Different options may have the same type.
     */
    Class<T> type();

    /**
     * An option that can be used to configure how the {@link HttpClient} will
     * select or establish an HTTP/3 connection through which to carry out
     * the request. If {@link Version#HTTP_3} is not selected either as
     * the {@linkplain Builder#version(Version) request preferred version}
     * or the {@linkplain HttpClient.Builder#version(Version) HttpClient
     * preferred version} setting this option on the request has no effect.
     * <p>
     * The {@linkplain #name() name of this option} is {@code "H3_DISCOVERY"}.
     *
     * @implNote
     * The JDK built-in implementation of the {@link HttpClient} understands the
     * request option {@link #H3_DISCOVERY} hint.
     * <br>
     * If no H3_DISCOVERY hint is provided, and {@linkplain  Version#HTTP_3
     * HTTP/3 version} is selected, either as {@linkplain Builder#version(Version)
     * request preferred version} or {@linkplain HttpClient.Builder#version(Version)
     * client preferred version}, the JDK built-in implementation of
     * the {@link HttpClient} will select one:
     * <ul>
     *     <li> If the {@linkplain Builder#version(Version) request preferred version} is
     *          explicitly set to {@linkplain Version#HTTP_3 HTTP/3},
     *          the exchange will be established as per {@link
     *          Http3DiscoveryMode#ANY}.</li>
     *     <li> Otherwise, if no request preferred version is explicitly provided
     *          and the {@linkplain HttpClient.Builder#version(Version) HttpClient
     *          preferred version} is {@linkplain Version#HTTP_3 HTTP/3},
     *          the exchange will be established as per {@link
     *          Http3DiscoveryMode#ALT_SVC}.</li>
     * </ul>
     * In case of {@linkplain HttpClient.Redirect redirect}, the
     * {@link #H3_DISCOVERY} option is always transferred to
     * the new request.
     * <p>
     * In this implementation, HTTP/3 through proxies is not supported.
     * Unless {@link  Http3DiscoveryMode#HTTP_3_URI_ONLY} is specified, if
     * a {@linkplain HttpClient.Builder#proxy(ProxySelector) proxy} is {@linkplain
     * ProxySelector#select(URI) selected} for the {@linkplain HttpRequest#uri()
     * request URI}, the protocol version is downgraded to HTTP/2 or
     * HTTP/1.1 and the {@link #H3_DISCOVERY} option is ignored. If, on the
     * other hand, {@link Http3DiscoveryMode#HTTP_3_URI_ONLY} is specified,
     * the request will fail.
     *
     * @see Http3DiscoveryMode
     * @see Builder#setOption(HttpOption, Object)
     */
    HttpOption<Http3DiscoveryMode> H3_DISCOVERY =
            new HttpRequestOptionImpl<>(Http3DiscoveryMode.class, "H3_DISCOVERY");

    /**
     * This enumeration can be used to help the {@link HttpClient} decide
     * how an HTTP/3 exchange should be established, and can be provided
     * as the value of the {@link HttpOption#H3_DISCOVERY} option
     * to {@link Builder#setOption(HttpOption, Object) Builder.setOption}.
     * <p>
     * Note that if neither the {@linkplain Builder#version(Version) request preferred
     * version} nor the {@linkplain HttpClient.Builder#version(Version) client preferred
     * version} is {@linkplain Version#HTTP_3 HTTP/3}, no HTTP/3 exchange will
     * be established and the {@code Http3DiscoveryMode} is ignored.
     *
     * @since TBD
     */
    enum Http3DiscoveryMode {
        /**
         * This instructs the {@link HttpClient} to only use the
         * <a href="https://www.rfc-editor.org/rfc/rfc7838">HTTP Alternative Services</a>
         * to find or establish an HTTP/3 connection with the origin server.
         * The exchange may then be carried out with any of the {@linkplain
         * Version three HTTP protocol versions}, depending on
         * whether an Alternate Service record for HTTP/3 could be found, and which HTTP version
         * was negotiated with the origin server, if no such record could be found.
         * <p>
         * In this mode, requests sent to the origin server will be sent through HTTP/1.1 or HTTP/2
         * until a {@code h3} <a href="https://www.rfc-editor.org/rfc/rfc7838">HTTP Alternative Services</a>
         * endpoint for that server is advertised to the client. Usually, an alternate service is
         * advertised by a server when responding to a request, so that subsequent requests can make
         * use of that alternative service.
         */
        ALT_SVC,
        /**
         * This instructs the {@link HttpClient} to use its own implementation
         * specific algorithm to find or establish a connection for the exchange.
         * Typically, if no connection was previously established with the origin
         * server defined by the request URI, the {@link HttpClient} implementation
         * may attempt to establish both an HTTP/3 connection over QUIC and an HTTP
         * connection over TLS/TCP at the authority present in the request URI,
         * and use the first that succeeds. The exchange may then be carried out with
         * any of the {@linkplain Version
         * three HTTP protocol versions}, depending on which method succeeded first.
         *
         * @implNote
         * If the {@linkplain Builder#version(Version) request preferred version} is {@linkplain
         * Version#HTTP_3 HTTP/3}, the {@code HttpClient} will first attempt to
         * establish an HTTP/3 connection, before attempting a TLS connection over TCP.
         * If, after an implementation specific timeout, no reply is obtained to the first
         * initial QUIC packet, the TLS/TCP connection will be attempted.
         * <p>
         * When attempting an HTTP/3 connection in this mode, the {@code HttpClient} will
         * use any <a href="https://www.rfc-editor.org/rfc/rfc7838">HTTP Alternative Services</a>
         * information it may have previously obtained from the origin server. If no
         * such information is available, a direct HTTP/3 connection at the authority (host, port)
         * present in the {@linkplain HttpRequest#uri() request URI} will be attempted.
         */
        ANY,
        /**
         * This instructs the {@link HttpClient} to only attempt an HTTP/3 connection
         * with the origin server. The connection will only succeed if the origin server
         * is listening for incoming HTTP/3 connections over QUIC at the same authority (host, port)
         * as defined in the {@linkplain HttpRequest#uri() request URI}. In this mode,
         * <a href="https://www.rfc-editor.org/rfc/rfc7838">HTTP Alternative Services</a>
         * are not used.
         */
        HTTP_3_URI_ONLY
    }

}
