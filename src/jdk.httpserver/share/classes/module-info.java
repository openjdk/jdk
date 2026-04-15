/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.*;

/**
 * Defines the JDK-specific HTTP server API, and provides the jwebserver tool
 * for running a minimal HTTP server.
 *
 * <p>The {@link com.sun.net.httpserver} package defines a high-level API for
 * building servers that support HTTP and HTTPS. The SimpleFileServer class
 * implements a simple HTTP-only file server intended for testing, development
 * and debugging purposes. A default implementation is provided via the
 * {@code jwebserver} tool and the main entry point of the module, which can
 * also be invoked with {@code java -m jdk.httpserver}.
 *
 * <p>The {@link com.sun.net.httpserver.spi} package specifies a Service Provider
 * Interface (SPI) for locating HTTP server implementations based on the
 * {@code com.sun.net.httpserver} API.
 * <p>
 * <b id="httpserverprops">System properties used by the HTTP server API</b>
 * <p>
 * The following is a list of JDK specific system properties used by the default HTTP
 * server implementation in the JDK. Any properties below that take a numeric value
 * assume the default value if given a string that does not parse as a number.
 * <ul>
 * <li><p><b>{@systemProperty sun.net.httpserver.idleInterval}</b> (default: 30 sec)<br>
 * Maximum duration in seconds which an idle connection is kept open. This timer
 * has an implementation specific granularity that may mean that idle connections are
 * closed later than the specified interval. Values less than or equal to zero are mapped
 * to the default setting.
 * </li>
 * <li><p><b>{@systemProperty jdk.httpserver.maxConnections}</b> (default: -1)<br>
 * The maximum number of open connections at a time. This includes active and idle connections.
 * If zero or negative, then no limit is enforced.
 * </li>
 * <li><p><b>{@systemProperty sun.net.httpserver.maxIdleConnections}</b> (default: 200)<br>
 * The maximum number of idle connections at a time. If set to zero or a negative value
 * then connections are closed after use.
 * </li>
 * <li><p><b>{@systemProperty sun.net.httpserver.drainAmount}</b> (default: 65536)<br>
 * The maximum number of bytes that will be automatically read and discarded from a
 * request body that has not been completely consumed by its
 * {@link com.sun.net.httpserver.HttpHandler HttpHandler}. If the number of remaining
 * unread bytes are less than this limit then the connection will be put in the idle connection
 * cache. If not, then it will be closed.
 * </li>
 * <li><p><b>{@systemProperty sun.net.httpserver.maxReqHeaders}</b> (default: 200)<br>
 * The maxiumum number of header fields accepted in a request. If this limit is exceeded
 * while the headers are being read, then the connection is terminated and the request ignored.
 * If the value is less than or equal to zero, then the default value is used.
 * </li>
 * <li><p><b>{@systemProperty sun.net.httpserver.maxReqHeaderSize}</b> (default: 393216 or 384kB)<br>
 *  The maximum header field section size that the server is prepared to accept.
 *  This is computed as the sum of the size of the header name, plus
 *  the size of the header value, plus an overhead of 32 bytes for
 *  each field section line. The request line counts as a first field section line,
 *  where the name is empty and the value is the whole line.
 *  If this limit is exceeded while the headers are being read, then the connection
 *  is terminated and the request ignored.
 *  If the value is less than or equal to zero, there is no limit.
 * </li>
 * <li><p><b>{@systemProperty sun.net.httpserver.maxReqTime}</b> (default: -1)<br>
 * The maximum time in milliseconds allowed to receive a request headers and body.
 * In practice, the actual time is a function of request size, network speed, and handler
 * processing delays. A value less than or equal to zero means the time is not limited.
 * If the limit is exceeded then the connection is terminated and the handler will receive a
 * {@link java.io.IOException}. This timer has an implementation specific granularity
 * that may mean requests are aborted later than the specified interval.
 * </li>
 * <li><p><b>{@systemProperty sun.net.httpserver.maxRspTime}</b> (default: -1)<br>
 * The maximum time in milliseconds allowed to receive a response headers and body.
 * In practice, the actual time is a function of response size, network speed, and handler
 * processing delays. A value less than or equal to zero means the time is not limited.
 * If the limit is exceeded then the connection is terminated and the handler will receive a
 * {@link java.io.IOException}. This timer has an implementation specific granularity
 * that may mean responses are aborted later than the specified interval.
 * </li>
 * <li><p><b>{@systemProperty sun.net.httpserver.nodelay}</b> (default: false)<br>
 * Boolean value, which if true, sets the {@link java.net.StandardSocketOptions#TCP_NODELAY TCP_NODELAY}
 * socket option on all incoming connections.
 * </li>
 * <li>
 * <p><b>{@systemProperty sun.net.httpserver.pathMatcher}</b> (default:
 * {@code pathPrefix})<br/>
 *
 * The path matching scheme used to route requests to context handlers.
 * The property can be configured with one of the following values:</p>
 *
 * <blockquote>
 * <dl>
 * <dt>{@code pathPrefix} (default)</dt>
 * <dd>The request path must begin with the context path and all matching path
 * segments must be identical. For instance, the context path {@code /foo}
 * would match request paths {@code /foo}, {@code /foo/}, and {@code /foo/bar},
 * but not {@code /foobar}.</dd>
 * <dt>{@code stringPrefix}</dt>
 * <dd>The request path string must begin with the context path string. For
 * instance, the context path {@code /foo} would match request paths
 * {@code /foo}, {@code /foo/}, {@code /foo/bar}, and {@code /foobar}.
 * </dd>
 * </dl>
 * </blockquote>
 *
 * <p>In case of a blank or invalid value, the default will be used.</p>
 *
 * <p>This property and the ability to restore the string prefix matching
 * behavior may be removed in a future release.</p>
 * </li>
 * </ul>
 *
 * @apiNote The API and SPI in this module are designed and implemented to support a minimal
 * HTTP server and simple HTTP semantics primarily.
 *
 * @implNote The default implementation of the HTTP server provided in this module is intended
 * for simple usages like local testing, development, and debugging. Accordingly, the design
 * and implementation of the server does not intend to be a full-featured, high performance
 * HTTP server.
 *
 * @implNote
 * Prior to JDK 26, in the JDK default implementation, the {@link HttpExchange} attribute map was
 * shared with the enclosing {@link HttpContext}.
 * Since JDK 26, by default, exchange attributes are per-exchange and the context attributes must
 * be accessed by calling {@link HttpExchange#getHttpContext() getHttpContext()}{@link
 * HttpContext#getAttributes() .getAttributes()}. <br>
 * A new system property, <b>{@systemProperty jdk.httpserver.attributes}</b> (default value: {@code ""})
 * allows to revert this new behavior. Set this property to "context" to restore the pre JDK 26 behavior.
 * @toolGuide jwebserver
 *
 * @uses com.sun.net.httpserver.spi.HttpServerProvider
 *
 * @moduleGraph
 * @since 9
 */
module jdk.httpserver {

    exports com.sun.net.httpserver;
    exports com.sun.net.httpserver.spi;

    uses com.sun.net.httpserver.spi.HttpServerProvider;
}
