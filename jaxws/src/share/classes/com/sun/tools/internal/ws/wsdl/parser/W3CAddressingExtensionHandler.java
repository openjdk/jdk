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

package com.sun.tools.internal.ws.wsdl.parser;

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext;
import com.sun.tools.internal.ws.resources.WsdlMessages;
import com.sun.tools.internal.ws.util.xml.XmlUtil;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wsdl.document.Fault;
import com.sun.tools.internal.ws.wsdl.document.Input;
import com.sun.tools.internal.ws.wsdl.document.Output;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import org.w3c.dom.Element;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.Map;

/**
 * @author Arun Gupta
 */
public class W3CAddressingExtensionHandler extends AbstractExtensionHandler {
    private ErrorReceiver errReceiver;

    public W3CAddressingExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap) {
        this(extensionHandlerMap, null);
    }

    public W3CAddressingExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap, ErrorReceiver errReceiver) {
        super(extensionHandlerMap);
        this.errReceiver = errReceiver;
    }

    @Override
    public String getNamespaceURI() {
        return AddressingVersion.W3C.wsdlNsUri;
    }

    protected QName getActionQName() {
        return AddressingVersion.W3C.wsdlActionTag;
    }

    protected QName getWSDLExtensionQName() {
        return AddressingVersion.W3C.wsdlExtensionTag;
    }

    @Override
    public boolean handleBindingExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if (XmlUtil.matchesTagNS(e, getWSDLExtensionQName())) {
            context.push();
            context.registerNamespaces(e);

            // TODO: read UsingAddressing extensibility element and store
            // TODO: it as extension in "parent". It may be used to generate
            // TODO: @Action/@FaultAction later.

            context.pop();
            return true;
        }
        return false; // keep compiler happy
    }

    @Override
    public boolean handleInputExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        String actionValue = XmlUtil.getAttributeNSOrNull(e, getActionQName());
        if (actionValue == null || actionValue.equals("")) {
            return warnEmptyAction(parent, context.getLocation(e));
        }

        context.push();
        ((Input)parent).setAction(actionValue);
        context.pop();

        return true;
    }

    @Override
    public boolean handleOutputExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        String actionValue = XmlUtil.getAttributeNSOrNull(e, getActionQName());
        if (actionValue == null || actionValue.equals("")) {
            return warnEmptyAction(parent,context.getLocation(e));
        }

        context.push();
        ((Output)parent).setAction(actionValue);
        context.pop();

        return true;
    }

    @Override
    public boolean handleFaultExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        String actionValue = XmlUtil.getAttributeNSOrNull(e, getActionQName());
        if (actionValue == null || actionValue.equals("")) {
            errReceiver.warning(context.getLocation(e), WsdlMessages.WARNING_FAULT_EMPTY_ACTION(parent.getNameValue(), parent.getWSDLElementName().getLocalPart(), parent.getParent().getNameValue()));
            return false; // keep compiler happy
        }

        context.push();
        ((Fault)parent).setAction(actionValue);
        context.pop();

        return true;
    }

    @Override
    public boolean handlePortExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        return handleBindingExtension(context, parent, e);
    }

    private boolean warnEmptyAction(TWSDLExtensible parent, Locator pos) {
        errReceiver.warning(pos, WsdlMessages.WARNING_INPUT_OUTPUT_EMPTY_ACTION(parent.getWSDLElementName().getLocalPart(), parent.getParent().getNameValue()));
        return false; // keep compiler happy
    }
}
