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

import javax.xml.namespace.QName;

/**
 * Abstraction of wsdl:portType/wsdl:operation/wsdl:output
 *
 * @author Vivek Pandey
 */
public interface WSDLOutput extends WSDLObject, WSDLExtensible {
    /**
     * Gives the wsdl:portType/wsdl:operation/wsdl:output@name
     */
    String getName();

    /**
     * Gives the WSDLMessage corresponding to wsdl:output@message
     * <p/>
     * This method should not be called before the entire WSDLModel is built. Basically after the WSDLModel is built
     * all the references are resolve in a post processing phase. IOW, the WSDL extensions should
     * not call this method.
     *
     * @return Always returns null when called from inside WSDL extensions.
     */
    WSDLMessage getMessage();

    /**
     * Gives the Action Message Addressing Property value for
     * {@link WSDLOutput} message.
     * <p/>
     * This method provides the correct value irrespective of
     * whether the Action is explicitly specified in the WSDL or
     * implicitly derived using the rules defined in WS-Addressing.
     *
     * @return Action
     */
    String getAction();

    /**
     * Gives the owning {@link WSDLOperation}
     */
    @NotNull
    WSDLOperation getOperation();

    /**
     * Gives qualified name of the wsdl:output 'name' attribute value. If there is no name, then it computes the name from:
     *
     * wsdl:operation@name+"Response", which is local name of {@link WSDLOperation#getName()} + "Response"
     * <p/>
     *
     * The namespace uri is determined from the enclosing wsdl:operation.
     */
    @NotNull
    QName getQName();

    /**
     * Checks if the Action value is implicitly derived using the rules defined in WS-Addressing.
     *
     * @return true if the Action value is implicitly derived using the rules defined in WS-Addressing.
     */
    public boolean isDefaultAction();
}
