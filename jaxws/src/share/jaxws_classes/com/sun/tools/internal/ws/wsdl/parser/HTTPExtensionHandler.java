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
import com.sun.tools.internal.ws.util.xml.XmlUtil;
import com.sun.tools.internal.ws.wsdl.document.http.*;
import org.w3c.dom.Element;

import java.util.Map;

/**
 * The HTTP extension handler for WSDL.
 *
 * @author WS Development Team
 */
public class HTTPExtensionHandler extends AbstractExtensionHandler {


    public HTTPExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap) {
        super(extensionHandlerMap);
    }

    public String getNamespaceURI() {
        return Constants.NS_WSDL_HTTP;
    }

    public boolean handleDefinitionsExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false;
    }

    public boolean handleTypesExtension(
        com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false;
    }

    public boolean handleBindingExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_BINDING)) {
            context.push();
            context.registerNamespaces(e);

            HTTPBinding binding = new HTTPBinding(context.getLocation(e));

            String verb = Util.getRequiredAttribute(e, Constants.ATTR_VERB);
            binding.setVerb(verb);

            parent.addExtension(binding);
            context.pop();
//            context.fireDoneParsingEntity(HTTPConstants.QNAME_BINDING, binding);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    public boolean handleOperationExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_OPERATION)) {
            context.push();
            context.registerNamespaces(e);

            HTTPOperation operation = new HTTPOperation(context.getLocation(e));

            String location =
                Util.getRequiredAttribute(e, Constants.ATTR_LOCATION);
            operation.setLocation(location);

            parent.addExtension(operation);
            context.pop();
//            context.fireDoneParsingEntity(
//                HTTPConstants.QNAME_OPERATION,
//                operation);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    public boolean handleInputExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_URL_ENCODED)) {
            parent.addExtension(new HTTPUrlEncoded(context.getLocation(e)));
            return true;
        } else if (
            XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_URL_REPLACEMENT)) {
            parent.addExtension(new HTTPUrlReplacement(context.getLocation(e)));
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    public boolean handleOutputExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false;
    }

    public boolean handleFaultExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false;
    }

    public boolean handleServiceExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false;
    }

    public boolean handlePortExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_ADDRESS)) {
            context.push();
            context.registerNamespaces(e);

            HTTPAddress address = new HTTPAddress(context.getLocation(e));

            String location =
                Util.getRequiredAttribute(e, Constants.ATTR_LOCATION);
            address.setLocation(location);

            parent.addExtension(address);
            context.pop();
//            context.fireDoneParsingEntity(HTTPConstants.QNAME_ADDRESS, address);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false;
        }
    }

    public boolean handlePortTypeExtension(TWSDLParserContext context, TWSDLExtensible parent, Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false;
    }
}
