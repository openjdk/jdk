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

/**
 * Root of the SPI implemented by the container
 * (such as application server.)
 *
 * <p>
 * Often technologies that are built on top of JAX-WS
 * (such as Tango) needs to negotiate private contracts between
 * them and the container. This interface allows such technologies
 * to query the negotiated SPI by using the {@link #getSPI(Class)}.
 *
 * <p>
 * For example, if a security pipe needs to get some information
 * from a container, they can do the following:
 * <ol>
 *  <li>Negotiate an interface with the container and define it.
 *      (let's call it <tt>ContainerSecuritySPI</tt>.)
 *  <li>The container will implement <tt>ContainerSecuritySPI</tt>.
 *  <li>At the runtime, a security pipe gets
 *      {@link WSEndpoint} and then to {@link Container}.
 *  <li>It calls <tt>container.getSPI(ContainerSecuritySPI.class)</tt>
 *  <li>The container returns an instance of <tt>ContainerSecuritySPI</tt>.
 *  <li>The security pipe talks to the container through this SPI.
 * </ol>
 *
 * <p>
 * This protects JAX-WS from worrying about the details of such contracts,
 * while still providing the necessary service of hooking up those parties.
 *
 * <p>
 * Technologies that run inside JAX-WS server runtime can access this object through
 * {@link WSEndpoint#getContainer()}. In the client runtime, it can be accessed from
 * {@link ContainerResolver#getContainer()}
 *
 * @author Kohsuke Kawaguchi
 * @see WSEndpoint
 */
public abstract class Container {
    /**
     * For derived classes.
     */
    protected Container() {
    }

    /**
     * Gets the specified SPI.
     *
     * <p>
     * This method works as a kind of directory service
     * for SPIs between technologies on top of JAX-WS
     * and the container.
     *
     * @param spiType
     *      Always non-null.
     *
     * @return
     *      null if such an SPI is not implemented by this container.
     */
    public abstract <T> T getSPI(Class<T> spiType);


    /**
     * Constant that represents a "no {@link Container}",
     * which always returns null from {@link #getSPI(Class)}.
     */
    public static final Container NONE = new Container() {
        public <T> T getSPI(Class<T> spiType) {
            return null;
        }
    };
}
