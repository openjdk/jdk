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

package com.sun.xml.internal.ws.wsdl.parser;

import com.sun.xml.internal.ws.api.model.wsdl.editable.*;
import javax.xml.stream.XMLStreamReader;
import javax.xml.namespace.QName;

/**
 * W3C WS-Addressing Runtime WSDL parser extension that parses
 * WS-Addressing Metadata wsdl extensibility elements
 * This mainly reads wsam:Action element on input/output/fault messages in wsdl.
 *
 * @author Rama Pulavarthi
 */
public class W3CAddressingMetadataWSDLParserExtension extends W3CAddressingWSDLParserExtension {

    String METADATA_WSDL_EXTN_NS = "http://www.w3.org/2007/05/addressing/metadata";
    QName METADATA_WSDL_ACTION_TAG = new QName(METADATA_WSDL_EXTN_NS, "Action", "wsam");
    @Override
    public boolean bindingElements(EditableWSDLBoundPortType binding, XMLStreamReader reader) {
        return false;
    }

    @Override
    public boolean portElements(EditableWSDLPort port, XMLStreamReader reader) {
        return false;
    }

    @Override
    public boolean bindingOperationElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        return false;
    }

    @Override
    protected void patchAnonymousDefault(EditableWSDLBoundPortType binding) {
    }

    @Override
    protected String getNamespaceURI() {
        return METADATA_WSDL_EXTN_NS;
    }

    @Override
    protected QName getWsdlActionTag() {
        return  METADATA_WSDL_ACTION_TAG;
    }
}
