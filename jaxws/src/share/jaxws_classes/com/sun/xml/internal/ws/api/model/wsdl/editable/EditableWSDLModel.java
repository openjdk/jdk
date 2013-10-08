/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.model.wsdl.editable;

import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.internal.ws.policy.PolicyMap;

public interface EditableWSDLModel extends WSDLModel {

    @Override
    EditableWSDLPortType getPortType(@NotNull QName name);

    /**
     * Add Binding
     *
     * @param portType Bound port type
     */
    void addBinding(EditableWSDLBoundPortType portType);

    @Override
    EditableWSDLBoundPortType getBinding(@NotNull QName name);

    @Override
    EditableWSDLBoundPortType getBinding(@NotNull QName serviceName, @NotNull QName portName);

    @Override
    EditableWSDLService getService(@NotNull QName name);

    @Override
    @NotNull
    Map<QName, ? extends EditableWSDLMessage> getMessages();

    /**
     * Add message
     *
     * @param msg Message
     */
    public void addMessage(EditableWSDLMessage msg);

    @Override
    @NotNull
    Map<QName, ? extends EditableWSDLPortType> getPortTypes();

    /**
     * Add port type
     *
     * @param pt Port type
     */
    public void addPortType(EditableWSDLPortType pt);

    @Override
    @NotNull
    Map<QName, ? extends EditableWSDLBoundPortType> getBindings();

    @Override
    @NotNull
    Map<QName, ? extends EditableWSDLService> getServices();

    /**
     * Add service
     *
     * @param svc Service
     */
    public void addService(EditableWSDLService svc);

    @Override
    public EditableWSDLMessage getMessage(QName name);

    /**
     * @param policyMap
     * @deprecated
     */
    public void setPolicyMap(PolicyMap policyMap);

    /**
     * Finalize rpc-lit binding
     *
     * @param portType Binding
     */
    public void finalizeRpcLitBinding(EditableWSDLBoundPortType portType);

    /**
     * Freezes WSDL model to prevent further modification
     */
    public void freeze();

}
