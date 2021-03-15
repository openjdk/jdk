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
import java.net.URI;;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import sun.net.httpserver.DelegatingHttpExchange;

/**
 * A handler which is invoked to process HTTP exchanges. Each
 * HTTP exchange is handled by one of these handlers.
 *
 * <P> The functionality of a handler can be extended or enhanced through
 * the use of {@link #adaptRequest(HttpHandler, UnaryOperator) adaptRequest} and
 * {@link #complement(HttpHandler, Predicate, HttpHandler) complement}, which
 * allows to adapt and complement a given handler, respectively.
 *
 * <p> Example of an adapted and complemented handler:
 * <pre>{@code var h = HttpHandler
 *       .complement(new SomeHandler(), r -> r.getRequestMethod().equals("PUT"), new SomePutHandler());
 *   var handler = HttpHandler.adaptRequest(h, r -> r.with("X-Foo", List.of("Bar")));
 * }</pre>
 *
 * The above {@code handler} adds the "X-Foo" request header to all incoming
 * requests before handling of the exchange is passed to the next handler,
 * the <i>complemented</i> handler. The <i>complemented</i> handler makes
 * exchange handling conditional; handling of the exchange is passed to the
 * {@code SomeHandler} unless the request method is "PUT", in which case
 * handling of the exchange is passed to the {@code SomePutHandler}.
 *
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
    public abstract void handle (HttpExchange exchange) throws IOException;


    /**
     * Returns a handler that inspects, and possibly adapts, the request state
     * before the exchange is handled by the given handler. The {@code Request}
     * returned by the operator will be the effective request state of the
     * exchange when handled.
     *
     * @implSpec
     * The default implementation returns a handler that when invoked; first
     * invokes the {@code requestOperator} with the given exchange, {@code ex},
     * in order to retrieve the <i>adapted request state</i>. Second, the
     * {@code handle} method of the {@code handler} is invoked passing an
     * exchange equivalent to {@code ex} with the <i>adapted request state</i>
     * set as the effective request state.
     *
     * @param handler a handler
     * @param requestOperator the request operator
     * @return a handler
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if the argument is null
     * @since 17
     */
    static HttpHandler adaptRequest(HttpHandler handler,
                                    UnaryOperator<Request> requestOperator)
            throws IOException
    {
        Objects.requireNonNull(requestOperator);
        return exchange -> {
            var request = requestOperator.apply(exchange);
            var newExchange = new DelegatingHttpExchange(exchange) {
                @Override
                public URI getRequestURI() { return request.getRequestURI(); }

                @Override
                public String getRequestMethod() { return request.getRequestMethod(); }

                @Override
                public Headers getRequestHeaders() { return request.getRequestHeaders(); }
            };
            handler.handle(newExchange);
        };
    }

    /**
     * Complements a handler with a conditional other handler.
     *
     * <p> This method creates a <i>complemented</i> handler; exchanges are
     * handled by the given {@code handler} unless their request matches the
     * {@code otherHandlerTest} predicate, in which case they are handled by the
     * {@code otherHandler}.
     *
     * <p> Example of a nested complemented handler:
     * <pre>{@code    Predicate<Request> IS_GET = r -> r.getRequestMethod().equals("GET");
     *   Predicate<Request> WANTS_DIGEST =  r -> r.getRequestHeaders().containsKey("Want-Digest");
     *
     *   var h1 = new SomeHandler();
     *   var h2 = HttpHandler.complement(h1, IS_GET, new SomeGetHandler());
     *   var h3 = HttpHandler.complement(h2, WANTS_DIGEST.and(IS_GET), new SomeDigestHandler());
     * }</pre>
     * The {@code h3} complemented handler passes handling of the exchange to
     * the {@code h2} handler unless the "Want-Digest" request header is present
     * and the request method is {@code GET}, in which case it passes handling of
     * the exchange to {@code SomeDigestHandler}. The {@code h2} complemented
     * handler, in turn, passes handling of the exchange to the {@code h1}
     * handler unless the request method is {@code GET}, in which case it passes
     * passes handling of the exchange to the {@code SomeGetHandler}. The {@code
     * h1} handler handles all exchanges that are not previously passed to
     * either {@code SomeGetHandler} or {@code SomeDigestHandler}.
     *
     * @implSpec
     * The default implementation returns a handler that when invoked behaves
     * as if:
     * <pre>{@code    if (otherHandlerTest.test(exchange))
     *       otherHandler.handle(exchange);
     *   else
     *       handler.handle(exchange);
     * }</pre>
     *
     * @param handler a handler
     * @param otherHandlerTest a request predicate
     * @param otherHandler a conditional handler
     * @return a handler
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if any argument is null
     * @since 17
     */
    static HttpHandler complement(HttpHandler handler,
                                   Predicate<Request> otherHandlerTest,
                                   HttpHandler otherHandler)
        throws IOException
    {
        Objects.requireNonNull(otherHandlerTest);
        Objects.requireNonNull(otherHandler);
        return exchange -> {
            if (otherHandlerTest.test(exchange))
                otherHandler.handle(exchange);
            else
                handler.handle(exchange);
        };
    }
}
