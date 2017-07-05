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
package com.sun.xml.internal.ws.model.wsdl;

import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
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
public final class WSDLPortImpl extends AbstractFeaturedObjectImpl implements WSDLPort {
    private final QName name;
    private EndpointAddress address;
    private final QName bindingName;
    private final WSDLServiceImpl owner;
    private WSEndpointReference epr;

    /**
     * To be set after the WSDL parsing is complete.
     */
    private WSDLBoundPortTypeImpl boundPortType;

    public WSDLPortImpl(XMLStreamReader xsr,WSDLServiceImpl owner, QName name, QName binding) {
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

    public WSDLServiceImpl getOwner() {
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
        this.epr = epr;
    }

    public @Nullable WSEndpointReference getEPR() {
        return epr;
    }
    public WSDLBoundPortTypeImpl getBinding() {
        return boundPortType;
    }

    public SOAPVersion getSOAPVersion(){
        return boundPortType.getSOAPVersion();
    }

    void freeze(WSDLModelImpl root) {
        boundPortType = root.getBinding(bindingName);
        if(boundPortType==null) {
            throw new LocatableWebServiceException(
                ClientMessages.UNDEFINED_BINDING(bindingName), getLocation());
        }
        if(features == null)
            features =  new WebServiceFeatureList();
        features.setParentFeaturedObject(boundPortType);
        notUnderstoodExtensions.addAll(boundPortType.notUnderstoodExtensions);
    }
}
