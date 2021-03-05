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
 * the use of {@link #handleOrElse(Predicate,HttpHandler) handleOrElse}
 * and {@link #adaptRequest(UnaryOperator) adaptRequest}, which allows to
 * complement and adapt a given handler, respectively.
 *
 * <p> Example of a complementing and adapting handler:
 * <pre>{@code var handler = new SomeHandler()
 *       .handleOrElse(r -> r.getRequestMethod().equals("PUT"), new SomePutHandler())
 *       .adaptRequest(r -> r.with("X-Foo", List.of("Bar")));
 * }</pre>
 *
 * The above {@code handler} adds the "X-Foo" request header to all incoming
 * requests before handling of the exchange is delegated to the next handler,
 * the <i>handleOrElse</i> handler. The <i>handleOrElse</i> handler offers an
 * if-else like construct; if the request method is "PUT" then handling of the
 * exchange is delegated to the {@code SomePutHandler}, otherwise handling of
 * the exchange is delegated to {@code SomeHandler}.
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
     * Complements this handler with a conditional other handler.
     *
     * <p> This method creates a <i>handleOrElse</i> handler; an if-else like
     * construct. Handling of exchanges who's request matches the {@code
     * otherHandlerTest} predicate are delegated to {@code otherHandler}. All
     * remaining exchanges are handled by {@code this} handler.
     *
     * <p> Example of a nested complementing handler:
     * <pre>{@code    Predicate<Request> IS_GET = r -> r.getRequestMethod().equals("GET");
     *   Predicate<Request> WANTS_DIGEST =  r -> r.getRequestHeaders().containsKey("Want-Digest");
     *
     *   var h1 = new SomeHandler();
     *   var h2 = h1.handleOrElse(IS_GET, new SomeGetHandler());
     *   var h3 = h2.handleOrElse(WANTS_DIGEST.and(IS_GET), new SomeDigestHandler());
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
     * @implSpec
     * The default implementation returns a handler that when invoked behaves
     * as if:
     * <pre>{@code    if (otherHandlerTest.test(exchange))
     *       otherHandler.handle(exchange);
     *   else
     *       this.handle(exchange);
     * }</pre>
     *
     * @param otherHandlerTest a request predicate
     * @param otherHandler a fallback handler
     * @return a handler
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if any argument is null
     * @since 17
     */
    default HttpHandler handleOrElse(Predicate<Request> otherHandlerTest,
                                     HttpHandler otherHandler)
        throws IOException
    {
        Objects.requireNonNull(otherHandlerTest);
        Objects.requireNonNull(otherHandler);
        return exchange -> {
            if (otherHandlerTest.test(exchange))
                otherHandler.handle(exchange);
            else
                this.handle(exchange);
        };
    }

    /**
     * Returns a handler that inspects, and possibly adapts, the request state
     * before delegating handling of the exchange. The {@code Request} returned
     * by the operator will be the effective request state of the exchange when
     * handled.
     *
     * @implSpec
     * The default implementation returns a handler that when invoked; first
     * invokes the {@code requestOperator} with the given exchange, {@code ex},
     * in order to retrieve the <i>adapted request state</i>. Second, the
     * {@code handle} method of {@code this} handler is invoked passing an
     * exchange equivalent to {@code ex} with the <i>adapted request state</i>
     * set as the effective request state.
     *
     * @param requestOperator the request operator
     * @return a handler
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if the argument is null
     * @since 17
     */
    default HttpHandler adaptRequest(UnaryOperator<Request> requestOperator)
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
            this.handle(newExchange);
        };
    }
}
