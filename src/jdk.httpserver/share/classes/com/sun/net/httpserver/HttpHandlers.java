/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import sun.net.httpserver.DelegatingHttpExchange;

/**
 * This class allows customization of {@link HttpHandler HttpHandler} instances
 * via static methods.
 *
 * <p> The functionality of a handler can be extended or enhanced through the
 * use of {@link #handleOrElse(Predicate, HttpHandler, HttpHandler) handleOrElse}
 * and {@link #adaptRequest(HttpHandler, UnaryOperator) adaptRequest}, which
 * allows to complement and adapt a given handler, respectively.
 *
 * <p> Example of a complemented and adapted handler:
 * <pre>{@code var h = HttpHandler.handleOrElse(r -> r.getRequestMethod().equals("PUT"), new SomePutHandler(), new SomeHandler())
 *   var handler = HttpHandler.adaptRequest(h, r -> r.with("X-Foo", List.of("Bar")));
 * }</pre>
 *
 * The above {@code handler} adds the "X-Foo" request header to all incoming
 * requests before handling of the exchange is delegated to the next handler,
 * the <i>handleOrElse</i> handler. The <i>handleOrElse</i> handler offers an
 * if-else like construct; if the request method is "PUT" then handling of the
 * exchange is delegated to the {@code SomePutHandler}, otherwise handling of
 * the exchange is delegated to {@code SomeHandler}.
 *
 * @since 17
 */
public class HttpHandlers {

    private HttpHandlers() { }

    /**
     * Complements a conditional handler with another handler.
     *
     * <p> This method creates a <i>handleOrElse</i> handler; an if-else like
     * construct. Exchanges who's request matches the {@code handlerTest}
     * predicate are handled by the {@code handler}. All remaining exchanges
     * are handled by the {@code fallbackHandler}.
     *
     * <p> Example of a nested handleOrElse handler:
     * <pre>{@code    Predicate<Request> IS_GET = r -> r.getRequestMethod().equals("GET");
     *   Predicate<Request> WANTS_DIGEST =  r -> r.getRequestHeaders().containsKey("Want-Digest");
     *
     *   var h1 = new SomeHandler();
     *   var h2 = HttpHandler.handleOrElse(IS_GET, new SomeGetHandler(), h1);
     *   var h3 = HttpHandler.handleOrElse(WANTS_DIGEST.and(IS_GET), new SomeDigestHandler(), h2);
     * }</pre>
     * The {@code h3} handleOrElse handler delegates handling of the exchange to
     * {@code SomeDigestHandler} if the "Want-Digest" request header is present
     * and the request method is {@code GET}, otherwise it delegates handling of
     * the exchange to the {@code h2} handler. The {@code h2} handleOrElse
     * handler, in turn, delegates handling of the exchange to {@code
     * SomeGetHandler} if the request method is {@code GET}, otherwise it
     * delegates handling of the exchange to the {@code h1} handler. The {@code
     * h1} handler handles all exchanges that are not previously delegated to
     * either {@code SomeGetHandler} or {@code SomeDigestHandler}.
     *
     * @param handlerTest a request predicate
     * @param handler a conditional handler
     * @param fallbackHandler a fallback handler
     * @return a handler
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if any argument is null
     * @since 17
     */
    public static HttpHandler handleOrElse(Predicate<Request> handlerTest,
                                    HttpHandler handler,
                                    HttpHandler fallbackHandler)
            throws IOException
    {
        Objects.requireNonNull(handlerTest);
        Objects.requireNonNull(handler);
        Objects.requireNonNull(fallbackHandler);
        return exchange -> {
            if (handlerTest.test(exchange))
                handler.handle(exchange);
            else
                fallbackHandler.handle(exchange);
        };
    }

    /**
     * Returns a handler that inspects, and possibly adapts, the request state
     * before the exchange is handled by the given handler. The {@code Request}
     * returned by the operator will be the effective request state of the
     * exchange when handled.
     *
     * <p> When the returned handler is invoked, it first invokes the
     * {@code requestOperator} with the given exchange, {@code ex}, in order
     * to retrieve the <i>adapted request state</i>. It then invokes the
     * {@code handle} method of the given {@code handler}, passing an exchange
     * equivalent to {@code ex} with the <i>adapted request state</i> set as
     * the effective request state.
     *
     * @param handler a handler
     * @param requestOperator the request operator
     * @return a handler
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if the argument is null
     * @since 17
     */
    public static HttpHandler adaptRequest(HttpHandler handler,
                                    UnaryOperator<Request> requestOperator)
            throws IOException
    {
        Objects.requireNonNull(handler);
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
}
