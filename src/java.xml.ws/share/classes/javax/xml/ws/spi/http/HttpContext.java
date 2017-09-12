/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws.spi.http;

import javax.xml.ws.Endpoint;
import java.util.Set;

/**
 * HttpContext represents a mapping between the root URI path of a web
 * service to a {@link HttpHandler} which is invoked to handle requests
 * destined for that path on the associated container.
 * <p>
 * Container provides the implementation for this and it matches
 * web service requests to corresponding HttpContext objects.
 *
 * @author Jitendra Kotamraju
 * @since 1.7, JAX-WS 2.2
 */
public abstract class HttpContext {

    /**
     * The handler to set for this context.
     */
    protected HttpHandler handler;

    /**
     * JAX-WS runtime sets its handler during
     * {@link Endpoint#publish(HttpContext)} to handle
     * HTTP requests for this context. Container or its extensions
     * use this handler to process the requests.
     *
     * @param handler the handler to set for this context
     */
    public void setHandler(HttpHandler handler) {
        this.handler = handler;
    }

    /**
     * Returns the path for this context. This path uniquely identifies
     * an endpoint inside an application and the path is relative to
     * application's context path. Container should give this
     * path based on how it matches request URIs to this HttpContext object.
     *
     * <p>
     * For servlet container, this is typically a url-pattern for an endpoint.
     *
     * <p>
     * Endpoint's address for this context can be computed as follows:
     * <pre>
     *  HttpExchange exch = ...;
     *  String endpointAddress =
     *      exch.getScheme() + "://"
     *      + exch.getLocalAddress().getHostName()
     *      + ":" + exch.getLocalAddress().getPort()
     *      + exch.getContextPath() + getPath();
     * </pre>
     *
     * @return this context's path
     */
    public abstract String getPath();

    /**
     * Returns an attribute value for container's configuration
     * and other data that can be used by jax-ws runtime.
     *
     * @param name attribute name
     * @return attribute value
     */
    public abstract Object getAttribute(String name);

    /**
     * Returns all attribute names for container's configuration
     * and other data that can be used by jax-ws runtime.
     *
     * @return set of all attribute names
     */
    public abstract Set<String> getAttributeNames();

}
