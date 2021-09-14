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

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * A view of the immutable request state of an HTTP exchange.
 *
 * @since 18
 */
public interface Request {

    /**
     * Returns the request {@link URI}.
     *
     * @return the request {@code URI}
     */
    URI getRequestURI();

    /**
     * Returns the request method.
     *
     * @return the request method string
     */
    String getRequestMethod();

    /**
     * Returns an immutable {@link Headers} containing the HTTP headers that
     * were included with this request.
     *
     * <p> The keys in this {@code Headers} are the header names, while the
     * values are a {@link java.util.List} of
     * {@linkplain java.lang.String Strings} containing each value that was
     * included in the request, in the order they were included. Header fields
     * appearing multiple times are represented as multiple string values.
     *
     * <p> The keys in {@code Headers} are case-insensitive.
     *
     * @return a read-only {@code Headers} which can be used to access request
     *         headers.
     */
    Headers getRequestHeaders();

    /**
     * Returns an identical {@code Request} with an additional header.
     *
     * <p> The returned {@code Request} has the same set of
     * {@link #getRequestHeaders() headers} as {@code this} request, but with
     * the addition of the given header. All other request state remains
     * unchanged.
     *
     * <p> If {@code this} request already contains a header with the same name
     * as the given {@code headerName}, then its value is not replaced.
     *
     * @implSpec
     * The default implementation first creates a new {@code Headers}, {@code h},
     * then adds all the request headers from {@code this} request to {@code h},
     * then adds the given name-values mapping if {@code headerName} is
     * not present in {@code h}. Then an unmodifiable view, {@code h'}, of
     * {@code h} and a new {@code Request}, {@code r}, are created.
     * The {@code getRequestMethod} and {@code getRequestURI} methods of
     * {@code r} simply invoke the equivalently named method of {@code this}
     * request. The {@code getRequestHeaders} method returns {@code h'}. Lastly,
     * {@code r} is returned.
     *
     * @param headerName   the header name
     * @param headerValues the list of header values
     * @return a request
     * @throws NullPointerException if any argument is null, or if any element
     *                              of headerValues is null.
     */
    default Request with(String headerName, List<String> headerValues) {
        Objects.requireNonNull(headerName);
        Objects.requireNonNull(headerValues);
        final Request r = this;

        var h = new Headers();
        h.putAll(r.getRequestHeaders());
        if (!h.containsKey(headerName)) {
            h.put(headerName, headerValues);
        }
        var unmodifiableHeaders = Headers.of(h);
        return new Request() {
            @Override
            public URI getRequestURI() { return r.getRequestURI(); }

            @Override
            public String getRequestMethod() { return r.getRequestMethod(); }

            @Override
            public Headers getRequestHeaders() { return unmodifiableHeaders; }
        };
    }
}
