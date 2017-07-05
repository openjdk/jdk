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

package com.sun.xml.internal.ws.api.server;

import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.pipe.ServerTubeAssemblerContext;
import com.sun.istack.internal.Nullable;

import javax.xml.namespace.QName;

/**
 * Implementations of this class can contribute properties associated with an Endpoint. The properties appear as
 * extensibility elements inside the EndpointReference of the endpoint. If any EPR extensibility elements are configured
 * for an endpoint, the EndpointReference is published inside the WSDL.
 *
 * @since JAX-WS 2.2
 * @author Rama Pulavarthi
 */
public abstract class EndpointReferenceExtensionContributor {
    /**
     *
     * @param extension EPRExtension is passed if an extension with same QName is already configured on the endpoint
     *      via other means (one possible way is by embedding EndpointReference in WSDL).
     *
     * @return  EPRExtension that should be finally configured on an Endpoint.
     */
    public abstract WSEndpointReference.EPRExtension getEPRExtension(WSEndpoint endpoint, @Nullable WSEndpointReference.EPRExtension extension );

    /**
     *
     * @return QName of the extensibility element that is contributed by this extension.
     */
    public abstract QName getQName();
}
