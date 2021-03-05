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
     * Returns a {@code Request} that adds a header to this request.
     *
     * <p> The passed name-values mapping is added to the {@link Headers} of this
     * request. If a header with this name already exists, its value is not
     * replaced. All other request state remains unchanged.
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
        ((UnmodifiableHeaders) r.getRequestHeaders()).map
                .putIfAbsent(headerName, headerValues);
        return new Request() {
            @Override
            public URI getRequestURI() { return r.getRequestURI(); }

            @Override
            public String getRequestMethod() { return r.getRequestMethod(); }

            @Override
            public Headers getRequestHeaders() { return r.getRequestHeaders(); }
        };
    }
}
