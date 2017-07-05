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

package com.sun.xml.internal.ws.api;

import com.sun.xml.internal.ws.api.server.Container;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Used to locate resources for jax-ws extensions. Using this, extensions
 * do not to have to write container specific code to locate resources.
 *
 * @author Jitendra Kotamraju
 */
public abstract class ResourceLoader {

    /**
     * Returns the actual location of the resource from the 'resource' arg
     * that represents a virtual locaion of a file understood by a container.
     * ResourceLoader impl for a Container knows how to map this
     * virtual location to actual location.
     * <p>
     * Extensions can get hold of this object using {@link Container}.
     * <p/>
     * for e.g.:
     * <pre>
     * ResourceLoader loader = container.getSPI(ResourceLoader.class);
     * URL catalog = loader.get("jax-ws-catalog.xml");
     * </pre>
     * A ResourceLoader for servlet environment, may do the following.
     * <pre>
     * URL getResource(String resource) {
     *     return servletContext.getResource("/WEB-INF/"+resource);
     * }
     * </pre>
     *
     * @param resource Designates a path that is understood by the container. The
     *             implementations must support "jax-ws-catalog.xml" resource.
     * @return the actual location, if found, or null if not found.
     * @throws MalformedURLException if there is an error in creating URL
     */
    public abstract URL getResource(String resource) throws MalformedURLException;

}
