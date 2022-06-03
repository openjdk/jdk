/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.net.httpserver;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;

import com.sun.net.httpserver.spi.HttpServerProvider;

/**
 * This class is an extension of {@link HttpServer} which provides support for
 * HTTPS.
 *
 * <p>A {@code HttpsServer} must have an associated {@link HttpsConfigurator} object
 * which is used to establish the SSL configuration for the SSL connections.
 *
 * <p>All other configuration is the same as for {@code HttpServer}.
 *
 * @since 1.6
 */

public abstract class HttpsServer extends HttpServer {

    /**
     * Constructor for subclasses to call.
     */
    protected HttpsServer() {
    }

    /**
     * Creates a {@code HttpsServer} instance which is initially not bound to any
     * local address/port. The {@code HttpsServer} is acquired from the currently
     * installed {@link HttpServerProvider}. The server must be bound using
     * {@link #bind(InetSocketAddress,int)} before it can be used. The server
     * must also have a {@code HttpsConfigurator} established with
     * {@link #setHttpsConfigurator(HttpsConfigurator)}.
     *
     * @return an instance of {@code HttpsServer}
     * @throws IOException if an I/O error occurs
     */
    public static HttpsServer create() throws IOException {
        return create(null, 0);
    }

    /**
     * Create a {@code HttpsServer} instance which will bind to the specified
     * {@link java.net.InetSocketAddress} (IP address and port number).
     *
     * A maximum backlog can also be specified. This is the maximum number of
     * queued incoming connections to allow on the listening socket. Queued TCP
     * connections exceeding this limit may be rejected by the TCP implementation.
     * The {@code HttpsServer} is acquired from the currently installed
     * {@link HttpServerProvider}. The server must have a {@code HttpsConfigurator}
     * established with {@link #setHttpsConfigurator(HttpsConfigurator)}.
     *
     * @param addr the address to listen on, if {@code null} then
     *             {@link #bind(InetSocketAddress,int)} must be called to set
     *             the address
     * @param backlog the socket backlog. If this value is less than or equal to
     *               zero, then a system default value is used.
     * @return an instance of {@code HttpsServer}
     * @throws BindException if the server cannot bind to the requested address,
     *          or if the server is already bound
     * @throws IOException if an I/O error occurs
     */

    public static HttpsServer create(InetSocketAddress addr, int backlog) throws IOException {
        HttpServerProvider provider = HttpServerProvider.provider();
        return provider.createHttpsServer(addr, backlog);
    }

    /**
     * Creates an {@code HttpsServer} instance with an initial context.
     *
     * <p> The server is created with an <i>initial context</i> that maps the
     * URI {@code path} to the exchange {@code handler}. The initial context is
     * created as if by an invocation of
     * {@link HttpsServer#createContext(String) createContext(path)}. The
     * {@code filters}, if any, are added to the initial context, in the order
     * they are given. The returned server is not started so can be configured
     * further if required.
     *
     * <p> The server instance will bind to the given
     * {@link java.net.InetSocketAddress}.
     *
     * <p> A maximum backlog can also be specified. This is the maximum number
     * of queued incoming connections to allow on the listening socket.
     * Queued TCP connections exceeding this limit may be rejected by
     * the TCP implementation. The HttpsServer is acquired from the currently
     * installed {@link HttpServerProvider}.
     *
     * <p> The server must have an HttpsConfigurator established with
     * {@link #setHttpsConfigurator(HttpsConfigurator)}.
     *
     * @param addr    the address to listen on, if {@code null} then
     *                {@link #bind bind} must be called to set the address
     * @param backlog the socket backlog. If this value is less than or
     *                equal to zero, then a system default value is used
     * @param path    the root URI path of the context, must be absolute
     * @param handler the HttpHandler for the context
     * @param filters the Filters for the context, optional
     * @return the HttpsServer
     * @throws BindException            if the server cannot bind to the address
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if path is invalid
     * @throws NullPointerException     if any of: {@code path}, {@code handler},
     *        {@code filters}, or any element of {@code filters}, are {@code null}
     * @since 18
     */
    public static HttpsServer create(InetSocketAddress addr,
                                    int backlog,
                                    String path,
                                    HttpHandler handler,
                                    Filter... filters) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(handler);
        Objects.requireNonNull(filters);
        Arrays.stream(filters).forEach(Objects::requireNonNull);

        HttpsServer server = HttpsServer.create(addr, backlog);
        HttpContext context = server.createContext(path);
        context.setHandler(handler);
        Arrays.stream(filters).forEach(f -> context.getFilters().add(f));
        return server;
    }

    /**
     * Sets this server's {@link HttpsConfigurator} object.
     *
     * @param config the {@code HttpsConfigurator} to set
     * @throws NullPointerException if config is {@code null}
     */
    public abstract void setHttpsConfigurator(HttpsConfigurator config);

    /**
     * Gets this server's {@link HttpsConfigurator} object, if it has been set.
     *
     * @return the {@code HttpsConfigurator} for this server, or {@code null} if
     * not set
     */
    public abstract HttpsConfigurator getHttpsConfigurator();
}
