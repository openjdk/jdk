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

import com.sun.xml.internal.ws.api.model.wsdl.*;
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

    public void serviceAttributes(WSDLService service, XMLStreamReader reader) {
        core.serviceAttributes(service, reader);
    }

    public boolean serviceElements(WSDLService service, XMLStreamReader reader) {
        return core.serviceElements(service, reader);
    }

    public void portAttributes(WSDLPort port, XMLStreamReader reader) {
        core.portAttributes(port, reader);
    }

    public boolean portElements(WSDLPort port, XMLStreamReader reader) {
        return core.portElements(port, reader);
    }

    public boolean portTypeOperationInput(WSDLOperation op, XMLStreamReader reader) {
        return core.portTypeOperationInput(op, reader);
    }

    public boolean portTypeOperationOutput(WSDLOperation op, XMLStreamReader reader) {
        return core.portTypeOperationOutput(op, reader);
    }

    public boolean portTypeOperationFault(WSDLOperation op, XMLStreamReader reader) {
        return core.portTypeOperationFault(op, reader);
    }

    public boolean definitionsElements(XMLStreamReader reader) {
        return core.definitionsElements(reader);
    }

    public boolean bindingElements(WSDLBoundPortType binding, XMLStreamReader reader) {
        return core.bindingElements(binding, reader);
    }

    public void bindingAttributes(WSDLBoundPortType binding, XMLStreamReader reader) {
        core.bindingAttributes(binding, reader);
    }

    public boolean portTypeElements(WSDLPortType portType, XMLStreamReader reader) {
        return core.portTypeElements(portType, reader);
    }

    public void portTypeAttributes(WSDLPortType portType, XMLStreamReader reader) {
        core.portTypeAttributes(portType, reader);
    }

    public boolean portTypeOperationElements(WSDLOperation operation, XMLStreamReader reader) {
        return core.portTypeOperationElements(operation, reader);
    }

    public void portTypeOperationAttributes(WSDLOperation operation, XMLStreamReader reader) {
        core.portTypeOperationAttributes(operation, reader);
    }

    public boolean bindingOperationElements(WSDLBoundOperation operation, XMLStreamReader reader) {
        return core.bindingOperationElements(operation, reader);
    }

    public void bindingOperationAttributes(WSDLBoundOperation operation, XMLStreamReader reader) {
        core.bindingOperationAttributes(operation, reader);
    }

    public boolean messageElements(WSDLMessage msg, XMLStreamReader reader) {
        return core.messageElements(msg, reader);
    }

    public void messageAttributes(WSDLMessage msg, XMLStreamReader reader) {
        core.messageAttributes(msg, reader);
    }

    public boolean portTypeOperationInputElements(WSDLInput input, XMLStreamReader reader) {
        return core.portTypeOperationInputElements(input, reader);
    }

    public void portTypeOperationInputAttributes(WSDLInput input, XMLStreamReader reader) {
        core.portTypeOperationInputAttributes(input, reader);
    }

    public boolean portTypeOperationOutputElements(WSDLOutput output, XMLStreamReader reader) {
        return core.portTypeOperationOutputElements(output, reader);
    }

    public void portTypeOperationOutputAttributes(WSDLOutput output, XMLStreamReader reader) {
        core.portTypeOperationOutputAttributes(output, reader);
    }

    public boolean portTypeOperationFaultElements(WSDLFault fault, XMLStreamReader reader) {
        return core.portTypeOperationFaultElements(fault, reader);
    }

    public void portTypeOperationFaultAttributes(WSDLFault fault, XMLStreamReader reader) {
        core.portTypeOperationFaultAttributes(fault, reader);
    }

    public boolean bindingOperationInputElements(WSDLBoundOperation operation, XMLStreamReader reader) {
        return core.bindingOperationInputElements(operation, reader);
    }

    public void bindingOperationInputAttributes(WSDLBoundOperation operation, XMLStreamReader reader) {
        core.bindingOperationInputAttributes(operation, reader);
    }

    public boolean bindingOperationOutputElements(WSDLBoundOperation operation, XMLStreamReader reader) {
        return core.bindingOperationOutputElements(operation, reader);
    }

    public void bindingOperationOutputAttributes(WSDLBoundOperation operation, XMLStreamReader reader) {
        core.bindingOperationOutputAttributes(operation, reader);
    }

    public boolean bindingOperationFaultElements(WSDLBoundFault fault, XMLStreamReader reader) {
        return core.bindingOperationFaultElements(fault, reader);
    }

    public void bindingOperationFaultAttributes(WSDLBoundFault fault, XMLStreamReader reader) {
        core.bindingOperationFaultAttributes(fault, reader);
    }

    public void finished(WSDLParserExtensionContext context) {
        core.finished(context);
    }

    public void postFinished(WSDLParserExtensionContext context) {
        core.postFinished(context);
    }
}
