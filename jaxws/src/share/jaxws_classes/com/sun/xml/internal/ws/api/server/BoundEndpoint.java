/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.server;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.Component;

import java.net.URI;

/**
 * Represents the {@link WSEndpoint} bound to a particular transport.
 *
 * @see Module#getBoundEndpoints()
 * @author Kohsuke Kawaguchi
 */
public interface BoundEndpoint extends Component {
    /**
     * The endpoint that was bound.
     *
     * <p>
     * Multiple {@link BoundEndpoint}s may point to the same {@link WSEndpoint},
     * if it's bound to multiple transports.
     *
     * @return the endpoint
     */
    @NotNull WSEndpoint getEndpoint();

    /**
     * The address of the bound endpoint.
     *
     * <p>
     * For example, if this endpoint is bound to a servlet endpoint
     * "http://foobar/myapp/myservice", then this method should
     * return that address.
     *
     * @return address of the endpoint
     */
    @NotNull URI getAddress();

    /**
     * The address of the bound endpoint using the base address. Often
     * times, baseAddress is only avaialble during the request.
     *
     * <p>
     * If the endpoint is bound to a servlet endpoint, the base address
     * won't include the url-pattern, so the base address would be
     * "http://host:port/context". This method would include url-pattern
     * for the endpoint and return that address
     * for e.g. "http://host:port/context/url-pattern"
     *
     * @param baseAddress that is used in computing the full address
     * @return address of the endpoint
     */
    @NotNull URI getAddress(String baseAddress);
}
