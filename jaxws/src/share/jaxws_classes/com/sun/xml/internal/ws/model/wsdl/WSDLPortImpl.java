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

package com.sun.xml.internal.ws.model.wsdl;

import java.util.List;

import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLModel;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLService;
import com.sun.xml.internal.ws.resources.ClientMessages;
import com.sun.xml.internal.ws.util.exception.LocatableWebServiceException;
import com.sun.xml.internal.ws.wsdl.parser.RuntimeWSDLParser;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.NotNull;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

/**
 * Implementation of {@link WSDLPort}
 *
 * @author Vivek Pandey
 */
public final class WSDLPortImpl extends AbstractFeaturedObjectImpl implements EditableWSDLPort {
    private final QName name;
    private EndpointAddress address;
    private final QName bindingName;
    private final EditableWSDLService owner;
    private WSEndpointReference epr;

    /**
     * To be set after the WSDL parsing is complete.
     */
    private EditableWSDLBoundPortType boundPortType;

    public WSDLPortImpl(XMLStreamReader xsr, EditableWSDLService owner, QName name, QName binding) {
        super(xsr);
        this.owner = owner;
        this.name = name;
        this.bindingName = binding;
    }

    public QName getName() {
        return name;
    }

    public QName getBindingName() {
        return bindingName;
    }

    public EndpointAddress getAddress() {
        return address;
    }

    public EditableWSDLService getOwner() {
        return owner;
    }

    /**
     * Only meant for {@link RuntimeWSDLParser} to call.
     */
    public void setAddress(EndpointAddress address) {
        assert address!=null;
        this.address = address;
    }

    /**
     * Only meant for {@link RuntimeWSDLParser} to call.
     */
    public void setEPR(@NotNull WSEndpointReference epr) {
        assert epr!=null;
        this.addExtension(epr);
        this.epr = epr;
    }

    public @Nullable WSEndpointReference getEPR() {
        return epr;
    }

    public EditableWSDLBoundPortType getBinding() {
        return boundPortType;
    }

    @SuppressWarnings("unchecked")
    public void freeze(EditableWSDLModel root) {
        boundPortType = root.getBinding(bindingName);
        if(boundPortType==null) {
            throw new LocatableWebServiceException(
                ClientMessages.UNDEFINED_BINDING(bindingName), getLocation());
        }
        if(features == null)
            features =  new WebServiceFeatureList();
        features.setParentFeaturedObject(boundPortType);
        notUnderstoodExtensions.addAll((List<UnknownWSDLExtension>)boundPortType.getNotUnderstoodExtensions());
    }
}
