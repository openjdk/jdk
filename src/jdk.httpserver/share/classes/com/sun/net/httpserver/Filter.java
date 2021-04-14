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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A filter used to pre- and post-process incoming requests. Pre-processing occurs
 * before the application's exchange handler is invoked, and post-processing
 * occurs after the exchange handler returns. Filters are organised in chains,
 * and are associated with {@link HttpContext} instances.
 *
 * <p> Each {@code Filter} in the chain, invokes the next filter within its own
 * {@link #doFilter(HttpExchange, Chain)} implementation. The final {@code Filter}
 * in the chain invokes the applications exchange handler.
 *
 * @since 1.6
 */
public abstract class Filter {

    /**
     * Constructor for subclasses to call.
     */
    protected Filter () {}

    /**
     * A chain of filters associated with a {@link HttpServer}.
     * Each filter in the chain is given one of these so it can invoke the
     * next filter in the chain.
     */
    public static class Chain {

        /**
         * The last element in the chain must invoke the user's
         * handler.
         */
        private ListIterator<Filter> iter;
        private HttpHandler handler;

        /**
         * Creates a {@code Chain} instance with given filters and handler.
         *
         * @param filters the filters that make up the {@code Chain}
         * @param handler the {@link HttpHandler} that will be invoked after
         *                the final {@code Filter} has finished
         */
        public Chain (List<Filter> filters, HttpHandler handler) {
            iter = filters.listIterator();
            this.handler = handler;
        }

        /**
         * Calls the next filter in the chain, or else the users exchange
         * handler, if this is the final filter in the chain. The {@code Filter}
         * may decide to terminate the chain, by not calling this method.
         * In this case, the filter <b>must</b> send the response to the
         * request, because the application's {@linkplain HttpExchange exchange}
         * handler will not be invoked.
         *
         * @param exchange the {@code HttpExchange}
         * @throws IOException if an I/O error occurs
         * @throws NullPointerException if exchange is {@code null}
         */
        public void doFilter (HttpExchange exchange) throws IOException {
            if (!iter.hasNext()) {
                handler.handle (exchange);
            } else {
                Filter f = iter.next();
                f.doFilter (exchange, this);
            }
        }
    }

    /**
     * Asks this filter to pre/post-process the given exchange. The filter
     * can:
     *
     * <ul>
     *     <li> Examine or modify the request headers.
     *     <li> Filter the request body or the response body, by creating suitable
     *     filter streams and calling {@link HttpExchange#setStreams(InputStream, OutputStream)}.
     *     <li> Set attribute objects in the exchange, which other filters or
     *     the exchange handler can access.
     *     <li> Decide to either:
     *
     *     <ol>
     *         <li> Invoke the next filter in the chain, by calling
     *         {@link Filter.Chain#doFilter(HttpExchange)}.
     *         <li> Terminate the chain of invocation, by <b>not</b> calling
     *         {@link Filter.Chain#doFilter(HttpExchange)}.
     *     </ol>
     *
     *     <li> If option 1. above is taken, then when doFilter() returns all subsequent
     *     filters in the Chain have been called, and the response headers can be
     *     examined or modified.
     *     <li> If option 2. above is taken, then this Filter must use the HttpExchange
     *     to send back an appropriate response.
     * </ul>
     *
     * @param exchange the {@code HttpExchange} to be filtered
     * @param chain the {@code Chain} which allows the next filter to be invoked
     * @throws IOException may be thrown by any filter module, and if caught,
     * must be rethrown again
     * @throws NullPointerException if either exchange or chain are {@code null}
     */
    public abstract void doFilter (HttpExchange exchange, Chain chain)
        throws IOException;

    /**
     * Returns a short description of this {@code Filter}.
     *
     * @return a {@code String} describing the {@code Filter}
     */
    public abstract String description ();

    /**
     * Returns a pre-processing Filter with the given description and operation.
     *
     * <p>The {@link Consumer operation} is the effective implementation of
     * the returned Filter and is executed for each {@code HttpExchange} before
     * invoking the next filter in the chain, or the exchange handler
     * (if this is the final filter in the chain).
     *
     * @param description the description of the returned filter
     * @param operation the operation of the returned filter
     * @return a filter
     * @throws NullPointerException if any argument is null
     * @since 17
     */
    public static Filter beforeResponse(String description,
                                        Consumer<HttpExchange> operation) {
        Objects.requireNonNull(description);
        Objects.requireNonNull(operation);
        return new Filter() {
            @Override
            public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                operation.accept(exchange);
                chain.doFilter(exchange);
            }
            @Override
            public String description() {
                return description;
            }
        };
    }

    /**
     * Returns a post-processing Filter with the given description and operation.
     *
     * <p>The {@link Consumer operation} is the effective implementation of
     * the returned Filter and is executed for each {@code HttpExchange} after
     * invoking the next filter in the chain, or the exchange handler
     * (if this is the final filter in the chain).
     *
     * @param description the description of the returned filter
     * @param operation the operation of the returned filter
     * @return a filter
     * @throws NullPointerException if any argument is null
     * @since 17
     */
    public static Filter afterResponse(String description,
                                       Consumer<HttpExchange> operation) {
        Objects.requireNonNull(description);
        Objects.requireNonNull(operation);
        return new Filter() {
            @Override
            public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                chain.doFilter(exchange);
                operation.accept(exchange);
            }
            @Override
            public String description() {
                return description;
            }
        };
    }
}
