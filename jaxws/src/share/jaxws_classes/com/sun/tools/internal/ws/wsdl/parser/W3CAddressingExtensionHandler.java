/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
    public W3CAddressingExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap) {
        this(extensionHandlerMap, null);
    }

    public W3CAddressingExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap, ErrorReceiver errReceiver) {
        super(extensionHandlerMap);
    }

    @Override
    public String getNamespaceURI() {
        return AddressingVersion.W3C.wsdlNsUri;
    }


    protected QName getWSDLExtensionQName() {
        return AddressingVersion.W3C.wsdlExtensionTag;
    }

    @Override
    public boolean handleBindingExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        if (XmlUtil.matchesTagNS(e, getWSDLExtensionQName())) {
            /*
            context.push();
            context.registerNamespaces(e);

            // TODO: read UsingAddressing extensibility element and store
            // TODO: it as extension in "parent". It may be used to generate
            // TODO: @Action/@FaultAction later.

            context.pop();
            */
            return true;
        }
        return false; // keep compiler happy
    }

    @Override
    public boolean handlePortExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        return handleBindingExtension(context, parent, e);
    }


}
