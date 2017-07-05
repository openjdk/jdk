/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.config.management;

import com.sun.xml.internal.ws.api.server.Invoker;

import org.xml.sax.EntityResolver;

/**
 * Store the parameters that were passed into the original WSEndpoint instance
 * upon creation. This allows us to instantiate a new instance with the same
 * parameters.
 *
 * @author Fabian Ritzmann
 */
public class EndpointCreationAttributes {

    private final boolean processHandlerAnnotation;
    private final Invoker invoker;
    private final EntityResolver entityResolver;
    private final boolean isTransportSynchronous;

    /**
     * Instantiate this data access object.
     *
     * @param processHandlerAnnotation The original processHandlerAnnotation setting.
     * @param invoker The original Invoker instance.
     * @param resolver The original EntityResolver instance.
     * @param isTransportSynchronous The original isTransportSynchronous setting.
     */
    public EndpointCreationAttributes(final boolean processHandlerAnnotation,
            final Invoker invoker,
            final EntityResolver resolver,
            final boolean isTransportSynchronous) {
        this.processHandlerAnnotation = processHandlerAnnotation;
        this.invoker = invoker;
        this.entityResolver = resolver;
        this.isTransportSynchronous = isTransportSynchronous;
    }

    /**
     * Return the original processHandlerAnnotation setting.
     *
     * @return The original processHandlerAnnotation setting.
     */
    public boolean isProcessHandlerAnnotation() {
        return this.processHandlerAnnotation;
    }

    /**
     * Return the original Invoker instance.
     *
     * @return The original Invoker instance.
     */
    public Invoker getInvoker() {
        return this.invoker;
    }

    /**
     * Return the original EntityResolver instance.
     *
     * @return The original EntityResolver instance.
     */
    public EntityResolver getEntityResolver() {
        return this.entityResolver;
    }

    /**
     * Return the original isTransportSynchronous setting.
     *
     * @return The original isTransportSynchronous setting.
     */
    public boolean isTransportSynchronous() {
        return this.isTransportSynchronous;
    }
}
