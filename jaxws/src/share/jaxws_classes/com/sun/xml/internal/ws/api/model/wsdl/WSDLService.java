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

package com.sun.xml.internal.ws.api.model.wsdl;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import javax.xml.namespace.QName;

/**
 * Abstracts wsdl:service.
 *
 * @author Vivek Pandey
 */
public interface WSDLService extends WSDLObject, WSDLExtensible {
    /**
     * Gets the {@link WSDLModel} that owns this service.
     */
    @NotNull
    WSDLModel getParent();

    /**
     * Gets the name of the wsdl:service@name attribute value as local name and wsdl:definitions@targetNamespace
     * as the namespace uri.
     */
    @NotNull
    QName getName();

    /**
     * Gets the {@link WSDLPort} for a given port name
     *
     * @param portName non-null operationName
     * @return null if a {@link WSDLPort} is not found
     */
    WSDLPort get(QName portName);

    /**
     * Gets the first {@link WSDLPort} if any, or otherwise null.
     */
    WSDLPort getFirstPort();

    /**
    * Gets the first port in this service which matches the portType
    */
    @Nullable
    WSDLPort getMatchingPort(QName portTypeName);

    /**
     * Gives all the {@link WSDLPort} in a wsdl:service {@link WSDLService}
     */
    Iterable<? extends WSDLPort> getPorts();
}
