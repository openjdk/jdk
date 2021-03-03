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

import sun.net.httpserver.DelegatingHttpExchange;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A handler which is invoked to process HTTP exchanges. Each
 * HTTP exchange is handled by one of these handlers.
 *
 * @apiNote The methods {@link #handleOrElse(Predicate, HttpHandler)} and
 * {@link #adaptRequest(UnaryOperator)} are conveniences to complement and adapt
 * a given handler, in order to extend or modify its functionality.
 * <p>
 * Example of a complemented and adapted {@code HttpHandler}:
 * <pre>    {@code var handler = new SomeHandler().handleOrElse(r -> r.getRequestMethod().equals("PUT"), new SomePutHandler())
 *                                   .adaptRequest(r -> r.with("Foo", List.of("Bar")));
 *    var server = HttpServer.create(new InetSocketAddress(8080), 10, "/", handler);
 *    server.start();
 * }</pre>
 * The above handler adds a custom request header "Foo" to all incoming requests
 * before they are handled. It then delegates the handling of the exchange to
 * the {@code SomePutHandler} instance if the request method is {@code PUT},
 * otherwise it delegates the exchange to the {@code SomeHandler} instance.
 * <p>
 * Example of a nested complemented {@code HttpHandler}:
 * <pre>    {@code var handler = new SomeHandler().handleOrElse(r -> r.getRequestMethod().equals("GET"), new SomeGetHandler())
 *                                   .handleOrElse(r -> r.getRequestHeaders().containsKey("Want-Digest")
 *                                                   && r.getRequestMethod().equals("GET"), new SomeDigestHandler());
 * }</pre>
 * In the case of nested complemented handlers, delegation of the exchange
 * starts at the last method call. The handler delegates the exchange to the
 * {@code SomeDigestHandler} instance if the request header "Want-Digest" is
 * present and the request method is {@code GET}. Otherwise, delegation moves to
 * the second last method call, where the exchange is delegated to the
 * {@code SomeGetHandler} instance if the request method is {@code GET}. All
 * remaining exchanges that don't match either of the request tests are handled
 * by the initial {@code SomeHandler} instance.
 * @since 1.6
 */
public interface HttpHandler {
    /**
     * Handle the given request and generate an appropriate response.
     * See {@link HttpExchange} for a description of the steps
     * involved in handling an exchange.
     *
     * @param exchange the exchange containing the request from the
     *                 client and used to send the response
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if exchange is {@code null}
     */
    public abstract void handle(HttpExchange exchange) throws IOException;

    /**
     * Complements this handler with a fallback handler. Any request that
     * matches the {@code requestTest} is handled by the fallback handler. All
     * other requests are handled by this handler.
     *
     * @param requestTest     a predicate given the request
     * @param fallbackHandler another handler
     * @return a handler
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if any argument is null
     * @implNote This implementation delegates the handling of the exchange either to this
     * handler or the fallback handler, depending on the result of the predicate.
     * An implementation of this method does not need to be provided.
     * @since 17
     */
    default HttpHandler handleOrElse(Predicate<Request> requestTest,
                                     HttpHandler fallbackHandler) throws IOException {
        Objects.requireNonNull(fallbackHandler);
        Objects.requireNonNull(requestTest);
        return exchange -> {
            if (requestTest.test(exchange)) fallbackHandler.handle(exchange);
            else handle(exchange);
        };
    }

    /**
     * Allows this handler to inspect and adapt the request state, before
     * handling the exchange. The {@code Request} returned by the operator
     * will be the effective request state of the exchange when handled.
     *
     * @param requestOperator the request operator
     * @return a handler
     * @throws NullPointerException if the argument is null
     * @implNote This implementation passes the exchange to this handler after adapting it.
     * An implementation of this method does not need to be provided.
     * @since 17
     */
    default HttpHandler adaptRequest(UnaryOperator<Request> requestOperator) {
        Objects.requireNonNull(requestOperator);
        return exchange -> {
            var request = requestOperator.apply(exchange);
            var newExchange = new DelegatingHttpExchange(exchange) {
                @Override
                public URI getRequestURI() {
                    return request.getRequestURI();
                }

                @Override
                public String getRequestMethod() {
                    return request.getRequestMethod();
                }

                @Override
                public Headers getRequestHeaders() {
                    return request.getRequestHeaders();
                }
            };
            this.handle(newExchange);
        };
    }
}
