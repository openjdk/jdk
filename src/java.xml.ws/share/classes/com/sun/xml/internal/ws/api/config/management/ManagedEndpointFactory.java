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

import com.sun.xml.internal.ws.api.server.WSEndpoint;

/**
 * Interface to create a new WSEndpoint wrapper. This is intended to be implemented
 * by the configuration management to return a ManagedEndpoint that wraps the
 * original WSEndpoint instance.
 *
 * @author Fabian Ritzmann
 */
public interface ManagedEndpointFactory {

    /**
     * This method may return a WSEndpoint implementation that wraps the original
     * WSEndpoint instance. This allows to interject e.g. management code. If
     * management has not been enabled for this endpoint, it will return the original
     * WSEndpoint instance.
     *
     * @param <T> The endpoint implementation type.
     * @param endpoint The endpoint instance.
     * @param attributes The parameters with which the original endpoint instance
     *   was created.
     * @return A WSEndpoint that wraps the original WSEndpoint instance or the
     *   original WSEndpoint instance.
     */
    public <T> WSEndpoint<T> createEndpoint(WSEndpoint<T> endpoint, EndpointCreationAttributes attributes);

}
