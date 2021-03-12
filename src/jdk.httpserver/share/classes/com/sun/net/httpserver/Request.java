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
import sun.net.httpserver.UnmodifiableHeaders;

/**
 * The immutable HTTP request state.
 *
 * @since 17
 */
public interface Request {

    /**
     * Get the request {@link URI}.
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
     * Returns an immutable {@link Headers} containing the HTTP headers that were
     * included with this request. The keys in this {@code Headers} will be the header
     * names, while the values will be a {@link java.util.List} of
     * {@linkplain java.lang.String Strings} containing each value that was
     * included (either for a header that was listed several times, or one that
     * accepts a comma-delimited list of values on a single line). In either of
     * these cases, the values for the header name will be presented in the
     * order that they were included in the request.
     *
     * <p> The keys in {@code Headers} are case-insensitive.
     *
     * @return a read-only {@code Headers} which can be used to access request headers
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
     * The default implementation; first creates a new {@code Headers}, {@code h},
     * then adds all the request headers from {@code this} request to {@code h},
     * then adds the given name-values mapping if {@code headerName} is
     * not present in {@code h}, finally an unmodifiable view, {@code h'}, of
     * {@code h} is created. Second, a new {@code Request}, {@code r}, is
     * created. The {@code getRequestMethod} and {@code getRequestURI} methods
     * of {@code r} simply invoke the equivalently named method of {@code this}
     * request. The {@code getRequestHeaders} method returns {@code h'}. Lastly,
     * {@code r} is returned.
     *
     * @param headerName   the header name
     * @param headerValues the list of header values
     * @return a request
     * @throws NullPointerException if any argument is null
     */
    default Request with(String headerName, List<String> headerValues) {
        Objects.requireNonNull(headerName);
        Objects.requireNonNull(headerValues);
        final Request r = this;

        Headers h = new Headers();
        h.putAll(r.getRequestHeaders());
        if (!h.containsKey(headerName)) {
            h.put(headerName, headerValues);
        }
        UnmodifiableHeaders unmodifiableHeaders = new UnmodifiableHeaders(h);
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
