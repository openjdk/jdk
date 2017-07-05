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
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtension;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * {@link WSDLParserExtension} filter that checks if
 * another {@link WSDLParserExtension} is following the contract.
 *
 * <p>
 * This code protects the JAX-WS RI from broken extensions.
 *
 * <p>
 * For now it just checks if {@link XMLStreamReader} is placed
 * at the expected start/end element.
 *
 * @author Kohsuke Kawaguchi
 */
final class FoolProofParserExtension extends DelegatingParserExtension {

    public FoolProofParserExtension(WSDLParserExtension core) {
        super(core);
    }

    private QName pre(XMLStreamReader xsr) {
        return xsr.getName();
    }

    private boolean post(QName tagName, XMLStreamReader xsr, boolean result) {
        if(!tagName.equals(xsr.getName()))
            return foundFool();
        if(result) {
            if(xsr.getEventType()!=XMLStreamConstants.END_ELEMENT)
                foundFool();
        } else {
            if(xsr.getEventType()!=XMLStreamConstants.START_ELEMENT)
                foundFool();
        }
        return result;
    }

    private boolean foundFool() {
        throw new AssertionError("XMLStreamReader is placed at the wrong place after invoking "+core);
    }

    public boolean serviceElements(EditableWSDLService service, XMLStreamReader reader) {
        return post(pre(reader),reader,super.serviceElements(service, reader));
    }

    public boolean portElements(EditableWSDLPort port, XMLStreamReader reader) {
        return post(pre(reader),reader,super.portElements(port, reader));
    }

    public boolean definitionsElements(XMLStreamReader reader) {
        return post(pre(reader),reader,super.definitionsElements(reader));
    }

    public boolean bindingElements(EditableWSDLBoundPortType binding, XMLStreamReader reader) {
        return post(pre(reader),reader,super.bindingElements(binding, reader));
    }

    public boolean portTypeElements(EditableWSDLPortType portType, XMLStreamReader reader) {
        return post(pre(reader),reader,super.portTypeElements(portType, reader));
    }

    public boolean portTypeOperationElements(EditableWSDLOperation operation, XMLStreamReader reader) {
        return post(pre(reader),reader,super.portTypeOperationElements(operation, reader));
    }

    public boolean bindingOperationElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        return post(pre(reader),reader,super.bindingOperationElements(operation, reader));
    }

    public boolean messageElements(EditableWSDLMessage msg, XMLStreamReader reader) {
        return post(pre(reader),reader,super.messageElements(msg, reader));
    }

    public boolean portTypeOperationInputElements(EditableWSDLInput input, XMLStreamReader reader) {
        return post(pre(reader),reader,super.portTypeOperationInputElements(input, reader));
    }

    public boolean portTypeOperationOutputElements(EditableWSDLOutput output, XMLStreamReader reader) {
        return post(pre(reader),reader,super.portTypeOperationOutputElements(output, reader));
    }

    public boolean portTypeOperationFaultElements(EditableWSDLFault fault, XMLStreamReader reader) {
        return post(pre(reader),reader,super.portTypeOperationFaultElements(fault, reader));
    }

    public boolean bindingOperationInputElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        return super.bindingOperationInputElements(operation, reader);
    }

    public boolean bindingOperationOutputElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        return post(pre(reader),reader,super.bindingOperationOutputElements(operation, reader));
    }

    public boolean bindingOperationFaultElements(EditableWSDLBoundFault fault, XMLStreamReader reader) {
        return post(pre(reader),reader,super.bindingOperationFaultElements(fault, reader));
    }
}
