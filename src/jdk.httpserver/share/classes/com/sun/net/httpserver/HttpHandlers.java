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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Implementations of {@link com.sun.net.httpserver.HttpHandler HttpHandler}
 * that implement various useful handlers, such as a static response handler,
 * or a conditional handler that complements one handler with another.
 *
 * <p> The factory method {@link #of(int, Headers, String)} provides a
 * means to create handlers with pre-set static response state. For example, a
 * {@code jsonHandler} that always returns <i>200</i> with the same json:
 * <pre>{@code
 *    HttpHandlers.of(200,
 *                    Headers.of("Content-Type", "application/json"),
 *                    Files.readString(Path.of("some.json")));
 * }</pre>
 * or a {@code notAllowedHandler} that always replies with <i>405</i> -
 * Method Not Allowed, and indicates the set of methods that are allowed:
 * <pre>{@code
 *    HttpHandlers.of(405, Headers.of("Allow", "GET"), "");
 * }</pre>
 *
 * <p> The functionality of a handler can be extended or enhanced through the
 * use of {@link #handleOrElse(Predicate, HttpHandler, HttpHandler) handleOrElse},
 * which allows to complement a given handler. For example, complementing a
 * {@code jsonHandler} with <i>notAllowedHandler</i>:
 *
 * <pre>{@code
 *    Predicate<Request> IS_GET = r -> r.getRequestMethod().equals("GET");
 *    var handler = HttpHandlers.handleOrElse(IS_GET, jsonHandler, notAllowedHandler);
 * }</pre>
 *
 * The above <i>handleOrElse</i> {@code handler} offers an if-else like construct;
 * if the request method is "GET" then handling of the exchange is delegated to
 * the {@code jsonHandler}, otherwise handling of the exchange is delegated to
 * the {@code notAllowedHandler}.
 *
 * @since 18
 */
public final class HttpHandlers {

    private HttpHandlers() { }

    /**
     * Complements a conditional {@code HttpHandler} with another handler.
     *
     * <p> This method creates a <i>handleOrElse</i> handler; an if-else like
     * construct. Exchanges who's request matches the {@code handlerTest}
     * predicate are handled by the {@code handler}. All remaining exchanges
     * are handled by the {@code fallbackHandler}.
     *
     * <p> Example of a nested handleOrElse handler:
     * <pre>{@code
     *    Predicate<Request> IS_GET = r -> r.getRequestMethod().equals("GET");
     *    Predicate<Request> WANTS_DIGEST =  r -> r.getRequestHeaders().containsKey("Want-Digest");
     *
     *    var h1 = new SomeHandler();
     *    var h2 = HttpHandlers.handleOrElse(IS_GET, new SomeGetHandler(), h1);
     *    var h3 = HttpHandlers.handleOrElse(WANTS_DIGEST.and(IS_GET), new SomeDigestHandler(), h2);
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
     * @throws NullPointerException if any argument is null
     */
    public static HttpHandler handleOrElse(Predicate<Request> handlerTest,
                                           HttpHandler handler,
                                           HttpHandler fallbackHandler) {
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
     * Returns an {@code HttpHandler} that sends a response comprising the given
     * {@code statusCode}, {@code headers}, and {@code body}.
     *
     * <p> This method creates a handler that reads and discards the request
     * body before it sets the response state and sends the response.
     *
     * <p> {@code headers} are the effective headers of the response. The
     * response <i>body bytes</i> are a {@code UTF-8} encoded byte sequence of
     * {@code body}. The response headers
     * {@linkplain HttpExchange#sendResponseHeaders(int, long) are sent} with
     * the given {@code statusCode} and the body bytes' length (or {@code -1}
     * if the body is empty). The body bytes are then sent as response body,
     * unless the body is empty, in which case no response body is sent.
     *
     * @param statusCode a response status code
     * @param headers a headers
     * @param body a response body string
     * @return a handler
     * @throws IllegalArgumentException if statusCode is not a positive 3-digit
     *                                  integer, as per rfc2616, section 6.1.1
     * @throws NullPointerException     if headers or body are null
     */
    public static HttpHandler of(int statusCode, Headers headers, String body) {
        if (statusCode < 100 || statusCode > 999)
            throw new IllegalArgumentException("statusCode must be 3-digit: "
                    + statusCode);
        Objects.requireNonNull(headers);
        Objects.requireNonNull(body);

        final var headersCopy = Headers.of(headers);
        final var bytes = body.getBytes(StandardCharsets.UTF_8);

        return exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().putAll(headersCopy);
                if (exchange.getRequestMethod().equals("HEAD")) {
                    exchange.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
                    exchange.sendResponseHeaders(statusCode, -1);
                }
                else if (bytes.length == 0) {
                    exchange.sendResponseHeaders(statusCode, -1);
                } else {
                    exchange.sendResponseHeaders(statusCode, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }
        };
    }
}
