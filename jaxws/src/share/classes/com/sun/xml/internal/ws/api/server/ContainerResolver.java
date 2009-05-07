/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.xml.internal.ws.api.server;

import com.sun.istack.internal.NotNull;

/**
 * This class determines an instance of {@link Container} for the runtime.
 * It applies for both server and client runtimes(for e.g in Servlet could
 * be accessing a Web Service). Always call {@link #setInstance} when the
 * application's environment is initailized and a Container instance should
 * be associated with an application.
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

    private static final ContainerResolver NONE = new ContainerResolver() {
        public Container getContainer() {
            return Container.NONE;
        }
    };

    private static volatile ContainerResolver theResolver = NONE;

    /**
     * Sets the custom container resolver which can be used to get client's
     * {@link Container}.
     *
     * @param resolver container resolver
     */
    public static void setInstance(ContainerResolver resolver) {
        if(resolver==null)
            resolver = NONE;
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
    public static ContainerResolver getDefault() {
        return NONE;
    }

    /**
     * Returns the {@link Container} context in which client is running.
     *
     * @return container instance for the client
     */
    public abstract @NotNull Container getContainer();

}
