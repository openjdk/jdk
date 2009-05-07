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

package com.sun.xml.internal.ws.wsdl.parser;

import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressingFeature;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLFeaturedObject;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.model.wsdl.WSDLBoundPortTypeImpl;
import com.sun.xml.internal.ws.model.wsdl.WSDLOperationImpl;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

/**
 * Member Submission WS-Addressing Runtime WSDL parser extension
 *
 * @author Arun Gupta
 */
public class MemberSubmissionAddressingWSDLParserExtension extends W3CAddressingWSDLParserExtension {
    @Override
    public boolean bindingElements(WSDLBoundPortType binding, XMLStreamReader reader) {
        return addressibleElement(reader, binding);
    }

    @Override
    public boolean portElements(WSDLPort port, XMLStreamReader reader) {
        return addressibleElement(reader, port);
    }

    private boolean addressibleElement(XMLStreamReader reader, WSDLFeaturedObject binding) {
        QName ua = reader.getName();
        if (ua.equals(AddressingVersion.MEMBER.wsdlExtensionTag)) {
            String required = reader.getAttributeValue(WSDLConstants.NS_WSDL, "required");
            binding.addFeature(new MemberSubmissionAddressingFeature(Boolean.parseBoolean(required)));
            XMLStreamReaderUtil.skipElement(reader);
            return true;        // UsingAddressing is consumed
        }

        return false;
    }

    @Override
    public boolean bindingOperationElements(WSDLBoundOperation operation, XMLStreamReader reader) {
        return false;
    }

    @Override
    public boolean portTypeOperationInput(WSDLOperation o, XMLStreamReader reader) {
        WSDLOperationImpl impl = (WSDLOperationImpl)o;

        String action = ParserUtil.getAttribute(reader, AddressingVersion.MEMBER.wsdlActionTag);
        if (action != null) {
            impl.getInput().setAction(action);
            impl.getInput().setDefaultAction(false);
        }

        return false;
    }

    @Override
    public boolean portTypeOperationOutput(WSDLOperation o, XMLStreamReader reader) {
        WSDLOperationImpl impl = (WSDLOperationImpl)o;

        String action = ParserUtil.getAttribute(reader, AddressingVersion.MEMBER.wsdlActionTag);
        if (action != null) {
            impl.getOutput().setAction(action);
        }

        return false;
    }

    @Override
    public boolean portTypeOperationFault(WSDLOperation o, XMLStreamReader reader) {
        WSDLOperationImpl impl = (WSDLOperationImpl)o;

        String action = ParserUtil.getAttribute(reader, AddressingVersion.MEMBER.wsdlActionTag);
        if (action != null) {
            String name = ParserUtil.getMandatoryNonEmptyAttribute(reader, "name");
            impl.getFaultActionMap().put(name, action);
        }

        return false;
    }

    @Override
    protected void patchAnonymousDefault(WSDLBoundPortTypeImpl binding) {
    }

    @Override
    protected String getNamespaceURI() {
        return AddressingVersion.MEMBER.wsdlNsUri;
    }
}
