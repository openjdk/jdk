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

import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtensionContext;
import com.sun.xml.internal.ws.api.model.wsdl.editable.*;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.Location;

import org.xml.sax.Locator;
import org.xml.sax.helpers.LocatorImpl;

/**
 * {@link WSDLParserExtension} that delegates to
 * multiple {@link WSDLParserExtension}s.
 *
 * <p>
 * This simplifies {@link RuntimeWSDLParser} since it now
 * only needs to work with one {@link WSDLParserExtension}.
 *
 * <p>
 * This class is guaranteed to return true from
 * all the extension callback methods.
 *
 * @author Kohsuke Kawaguchi
 */
final class WSDLParserExtensionFacade extends WSDLParserExtension {
    private final WSDLParserExtension[] extensions;

    WSDLParserExtensionFacade(WSDLParserExtension... extensions) {
        assert extensions!=null;
        this.extensions = extensions;
    }

    public void start(WSDLParserExtensionContext context) {
        for (WSDLParserExtension e : extensions) {
            e.start(context);
        }
    }

    public boolean serviceElements(EditableWSDLService service, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if(e.serviceElements(service,reader))
                return true;
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void serviceAttributes(EditableWSDLService service, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions)
            e.serviceAttributes(service,reader);
    }

    public boolean portElements(EditableWSDLPort port, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if(e.portElements(port,reader))
                return true;
        }
        //extension is not understood by any WSDlParserExtension
        //Check if it must be understood.
        if(isRequiredExtension(reader)) {
            port.addNotUnderstoodExtension(reader.getName(),getLocator(reader));
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public boolean portTypeOperationInput(EditableWSDLOperation op, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions)
            e.portTypeOperationInput(op,reader);

        return false;
    }

    public boolean portTypeOperationOutput(EditableWSDLOperation op, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions)
            e.portTypeOperationOutput(op,reader);

        return false;
    }

    public boolean portTypeOperationFault(EditableWSDLOperation op, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions)
            e.portTypeOperationFault(op,reader);

        return false;
    }

    public void portAttributes(EditableWSDLPort port, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions)
            e.portAttributes(port,reader);
    }

    public boolean definitionsElements(XMLStreamReader reader){
        for (WSDLParserExtension e : extensions) {
            if (e.definitionsElements(reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public boolean bindingElements(EditableWSDLBoundPortType binding, XMLStreamReader reader){
        for (WSDLParserExtension e : extensions) {
            if (e.bindingElements(binding, reader)) {
                return true;
            }
        }
        //extension is not understood by any WSDlParserExtension
        //Check if it must be understood.
        if (isRequiredExtension(reader)) {
            binding.addNotUnderstoodExtension(
                    reader.getName(), getLocator(reader));
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void bindingAttributes(EditableWSDLBoundPortType binding, XMLStreamReader reader){
        for (WSDLParserExtension e : extensions) {
            e.bindingAttributes(binding, reader);
        }
    }

    public boolean portTypeElements(EditableWSDLPortType portType, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.portTypeElements(portType, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void portTypeAttributes(EditableWSDLPortType portType, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.portTypeAttributes(portType, reader);
        }
    }

    public boolean portTypeOperationElements(EditableWSDLOperation operation, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.portTypeOperationElements(operation, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void portTypeOperationAttributes(EditableWSDLOperation operation, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.portTypeOperationAttributes(operation, reader);
        }
    }

    public boolean bindingOperationElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.bindingOperationElements(operation, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void bindingOperationAttributes(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.bindingOperationAttributes(operation, reader);
        }
    }

    public boolean messageElements(EditableWSDLMessage msg, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.messageElements(msg, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void messageAttributes(EditableWSDLMessage msg, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.messageAttributes(msg, reader);
        }
    }

    public boolean portTypeOperationInputElements(EditableWSDLInput input, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.portTypeOperationInputElements(input, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void portTypeOperationInputAttributes(EditableWSDLInput input, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.portTypeOperationInputAttributes(input, reader);
        }
    }

    public boolean portTypeOperationOutputElements(EditableWSDLOutput output, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.portTypeOperationOutputElements(output, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void portTypeOperationOutputAttributes(EditableWSDLOutput output, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.portTypeOperationOutputAttributes(output, reader);
        }
    }

    public boolean portTypeOperationFaultElements(EditableWSDLFault fault, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.portTypeOperationFaultElements(fault, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void portTypeOperationFaultAttributes(EditableWSDLFault fault, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.portTypeOperationFaultAttributes(fault, reader);
        }
    }

    public boolean bindingOperationInputElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.bindingOperationInputElements(operation, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void bindingOperationInputAttributes(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.bindingOperationInputAttributes(operation, reader);
        }
    }

    public boolean bindingOperationOutputElements(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.bindingOperationOutputElements(operation, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void bindingOperationOutputAttributes(EditableWSDLBoundOperation operation, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.bindingOperationOutputAttributes(operation, reader);
        }
    }

    public boolean bindingOperationFaultElements(EditableWSDLBoundFault fault, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            if (e.bindingOperationFaultElements(fault, reader)) {
                return true;
            }
        }
        XMLStreamReaderUtil.skipElement(reader);
        return true;
    }

    public void bindingOperationFaultAttributes(EditableWSDLBoundFault fault, XMLStreamReader reader) {
        for (WSDLParserExtension e : extensions) {
            e.bindingOperationFaultAttributes(fault, reader);
        }
    }

    public void finished(WSDLParserExtensionContext context) {
        for (WSDLParserExtension e : extensions) {
            e.finished(context);
        }
    }

    public void postFinished(WSDLParserExtensionContext context) {
        for (WSDLParserExtension e : extensions) {
            e.postFinished(context);
        }
    }
    /**
     *
     * @param reader
     * @return If the element has wsdl:required attribute set to true
     */

    private boolean isRequiredExtension(XMLStreamReader reader) {
        String required = reader.getAttributeValue(WSDLConstants.NS_WSDL, "required");
        if(required != null)
            return Boolean.parseBoolean(required);
        return false;
    }

    private Locator getLocator(XMLStreamReader reader) {
        Location location = reader.getLocation();
            LocatorImpl loc = new LocatorImpl();
            loc.setSystemId(location.getSystemId());
            loc.setLineNumber(location.getLineNumber());
        return loc;
    }

}
