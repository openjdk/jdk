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
import com.sun.xml.internal.ws.transport.http.HttpAdapter;

/**
 * Light-weight http server transport for {@link WSEndpoint}.
 * It provides a way to start the transport at a local http address and
 * to stop the transport.
 *
 * @author Jitendra Kotamraju
 */
public abstract class HttpEndpoint {

    /**
     * Factory to deploy {@link WSEndpoint} on light-weight
     * http server container.
     *
     * @param endpoint that needs to be deployed at http server
     * @return transport object for the endpoint
     */
    public static HttpEndpoint create(@NotNull WSEndpoint endpoint) {
        return new com.sun.xml.internal.ws.transport.http.server.HttpEndpoint(null, HttpAdapter.createAlone(endpoint));
    }

    /**
     * Publishes this endpoint at a localhost's http address.
     *
     * @param address endpoint's http address
     *        for e.g http://localhost:8080/ctxt/pattern
     */
    public abstract void publish(@NotNull String address);

    /**
     * Stops the published endpoint
     */
    public abstract void stop();

}
