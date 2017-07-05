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

/**
 * This class determines an instance of {@link Container} for the runtime.
 * It applies for both server and client runtimes(for e.g in Servlet could
 * be accessing a Web Service).
 *
 * A client that is invoking a web service may be running in a
 * container(for e.g servlet). T
 *
 * <p>
 * ContainerResolver uses a static field to keep the instance of the resolver object.
 * Typically appserver may set its custom container resolver using the static method
 * {@link #setInstance(ContainerResolver)}
 *
 * @author Jitendra Kotamraju
 */
public abstract class ContainerResolver {

    private static final ThreadLocalContainerResolver DEFAULT = new ThreadLocalContainerResolver();

    private static volatile ContainerResolver theResolver = DEFAULT;

    /**
     * Sets the custom container resolver which can be used to get client's
     * {@link Container}.
     *
     * @param resolver container resolver
     */
    public static void setInstance(ContainerResolver resolver) {
        if(resolver==null)
            resolver = DEFAULT;
        theResolver = resolver;
    }

    /**
     * Returns the container resolver which can be used to get client's {@link Container}.
     *
     * @return container resolver instance
     */
    public static @NotNull ContainerResolver getInstance() {
        return theResolver;
    }

    /**
     * Returns the default container resolver which can be used to get {@link Container}.
     *
     * @return default container resolver
     */
    public static ThreadLocalContainerResolver getDefault() {
        return DEFAULT;
    }

    /**
     * Returns the {@link Container} context in which client is running.
     *
     * @return container instance for the client
     */
    public abstract @NotNull Container getContainer();

}
