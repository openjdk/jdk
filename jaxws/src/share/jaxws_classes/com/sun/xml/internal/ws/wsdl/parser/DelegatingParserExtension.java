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
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtensionContext;

import javax.xml.stream.XMLStreamReader;

/**
 * Delegate to another {@link WSDLParserExtension}
 * useful for the base class for filtering.
 *
 * @author Kohsuke Kawaguchi
 */
class DelegatingParserExtension extends WSDLParserExtension {
    protected final WSDLParserExtension core;

    public DelegatingParserExtension(WSDLParserExtension core) {
        this.core = core;
    }

    public void start(WSDLParserExtensionContext context) {
        core.start(context);
    }

    public void serviceAttributes(EditableWSDLService service, XMLStreamReader reader) {
        core.serviceAttributes(service, reader);
    }

    public boolean serviceElements(EditableWSDLService service, XMLStreamReader reader) {
        return core.serviceElements(service, reader);
    }

    public void portAttributes(EditableWSDLPort port, XMLStreamReader reader) {
        core.portAttributes(port, reader);
    }

    public boolean portElements(EditableWSDLPort port, XMLStreamReader reader) {
        return core.portElements(port, reader);
    }

    public boolean portTypeOperationInput(EditableWSDLOperation op, XMLStreamReader reader) {
        return core.portTypeOperationInput(op, reader);
    }

    public boolean portTypeOperationOutput(EditableWSDLOperation op, XMLStreamReader reader) {
        return core.portTypeOperationOutput(op, reader);
    }

    public boolean portTypeOperationFault(EditableWSDLOperation op, XMLStreamReader reader) {
        return core.portTypeOperationFault(op, reader);
    }

    public boolean definitionsElements(XMLStreamReader reader) {
        return core.definitionsElements(reader);
    }

    public boolean bindingElements(EditableWSDLBoundPortType binding, XMLStreamReader reader) {
        return core.bindingElements(binding, reader);
    }

    public void bindingAttributes(EditableWSDLBoundPortType binding, XMLStreamReader reader) {
        core.bindingAttributes(binding, reader);
    }

    public boolean portTypeElements(EditableWSDLPortType portType, XMLStreamReader reader) {
        return core.portTypeElements(portType, reader);
    }

    public void portTypeAttributes(EditableWSDLPortType portType, XMLStreamReader reader) {
        core.portTypeAttributes(portType, reader);
    }

    public boolean portTypeOperationElements(EditableWSDLOperation operation, XMLStreamReader reader) {
        return core.portTypeOperationElements(operation, reader);
    }

    public void portTypeOperationAttributes(EditableWSDLOperation operation, XMLStreamReader reader) {
        core.portTypeOperationAttributes(operation, reader);
    }

    public boolean bindingOperationElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        return core.bindingOperationElements(operation, reader);
    }

    public void bindingOperationAttributes(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        core.bindingOperationAttributes(operation, reader);
    }

    public boolean messageElements(EditableWSDLMessage msg, XMLStreamReader reader) {
        return core.messageElements(msg, reader);
    }

    public void messageAttributes(EditableWSDLMessage msg, XMLStreamReader reader) {
        core.messageAttributes(msg, reader);
    }

    public boolean portTypeOperationInputElements(EditableWSDLInput input, XMLStreamReader reader) {
        return core.portTypeOperationInputElements(input, reader);
    }

    public void portTypeOperationInputAttributes(EditableWSDLInput input, XMLStreamReader reader) {
        core.portTypeOperationInputAttributes(input, reader);
    }

    public boolean portTypeOperationOutputElements(EditableWSDLOutput output, XMLStreamReader reader) {
        return core.portTypeOperationOutputElements(output, reader);
    }

    public void portTypeOperationOutputAttributes(EditableWSDLOutput output, XMLStreamReader reader) {
        core.portTypeOperationOutputAttributes(output, reader);
    }

    public boolean portTypeOperationFaultElements(EditableWSDLFault fault, XMLStreamReader reader) {
        return core.portTypeOperationFaultElements(fault, reader);
    }

    public void portTypeOperationFaultAttributes(EditableWSDLFault fault, XMLStreamReader reader) {
        core.portTypeOperationFaultAttributes(fault, reader);
    }

    public boolean bindingOperationInputElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        return core.bindingOperationInputElements(operation, reader);
    }

    public void bindingOperationInputAttributes(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        core.bindingOperationInputAttributes(operation, reader);
    }

    public boolean bindingOperationOutputElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        return core.bindingOperationOutputElements(operation, reader);
    }

    public void bindingOperationOutputAttributes(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        core.bindingOperationOutputAttributes(operation, reader);
    }

    public boolean bindingOperationFaultElements(EditableWSDLBoundFault fault, XMLStreamReader reader) {
        return core.bindingOperationFaultElements(fault, reader);
    }

    public void bindingOperationFaultAttributes(EditableWSDLBoundFault fault, XMLStreamReader reader) {
        core.bindingOperationFaultAttributes(fault, reader);
    }

    public void finished(WSDLParserExtensionContext context) {
        core.finished(context);
    }

    public void postFinished(WSDLParserExtensionContext context) {
        core.postFinished(context);
    }
}
