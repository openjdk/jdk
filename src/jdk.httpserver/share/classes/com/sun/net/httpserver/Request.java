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

import sun.net.httpserver.UnmodifiableHeaders;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * The immutable HTTP request state of an {@code HttpExchange}.
 *
 * @since 17
 */
public interface Request {

    /**
     * Returns the request {@link URI}. See {@link HttpExchange#getRequestURI()}.
     *
     * @return the request {@code URI}
     */
    URI getRequestURI();

    /**
     * Returns the request method. See {@link HttpExchange#getRequestMethod()}}.
     *
     * @return the request method string
     */
    String getRequestMethod();

    /**
     * Returns an immutable {@link Map} containing the headers of this request.
     * See {@link HttpExchange#getRequestHeaders()}.
     *
     * @return a read-only {@code Map} to access request headers
     */
    Headers getRequestHeaders();

    /**
     * Returns a {@code Request} that adds a header to this request.
     * <p>
     * The passed name-values mapping is added to the {@link Headers} of this
     * request. If a header with this name already exists, its value is not
     * replaced. All other request state remains unchanged.
     * TODO: mention null? Or is link to Headers sufficient (its class-level doc mentions null)?
     *
     * @param headerName   the header name
     * @param headerValues the list of header values
     * @return a request
     */
    default Request with(String headerName, List<String> headerValues) {
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
