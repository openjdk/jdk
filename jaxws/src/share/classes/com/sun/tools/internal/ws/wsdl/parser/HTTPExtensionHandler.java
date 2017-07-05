/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.IOException;

import org.w3c.dom.Element;

import com.sun.tools.internal.ws.wsdl.document.http.HTTPAddress;
import com.sun.tools.internal.ws.wsdl.document.http.HTTPBinding;
import com.sun.tools.internal.ws.wsdl.document.http.HTTPConstants;
import com.sun.tools.internal.ws.wsdl.document.http.HTTPOperation;
import com.sun.tools.internal.ws.wsdl.document.http.HTTPUrlEncoded;
import com.sun.tools.internal.ws.wsdl.document.http.HTTPUrlReplacement;
import com.sun.tools.internal.ws.wsdl.framework.Extensible;
import com.sun.tools.internal.ws.wsdl.framework.Extension;
import com.sun.tools.internal.ws.wsdl.framework.ParserContext;
import com.sun.tools.internal.ws.wsdl.framework.WriterContext;
import com.sun.tools.internal.ws.util.xml.XmlUtil;

/**
 * The HTTP extension handler for WSDL.
 *
 * @author WS Development Team
 */
public class HTTPExtensionHandler extends ExtensionHandlerBase {

    public HTTPExtensionHandler() {
    }

    public String getNamespaceURI() {
        return Constants.NS_WSDL_HTTP;
    }

    protected boolean handleDefinitionsExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    protected boolean handleTypesExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    protected boolean handleBindingExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_BINDING)) {
            context.push();
            context.registerNamespaces(e);

            HTTPBinding binding = new HTTPBinding();

            String verb = Util.getRequiredAttribute(e, Constants.ATTR_VERB);
            binding.setVerb(verb);

            parent.addExtension(binding);
            context.pop();
            context.fireDoneParsingEntity(HTTPConstants.QNAME_BINDING, binding);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    protected boolean handleOperationExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_OPERATION)) {
            context.push();
            context.registerNamespaces(e);

            HTTPOperation operation = new HTTPOperation();

            String location =
                Util.getRequiredAttribute(e, Constants.ATTR_LOCATION);
            operation.setLocation(location);

            parent.addExtension(operation);
            context.pop();
            context.fireDoneParsingEntity(
                HTTPConstants.QNAME_OPERATION,
                operation);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    protected boolean handleInputExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_URL_ENCODED)) {
            parent.addExtension(new HTTPUrlEncoded());
            return true;
        } else if (
            XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_URL_REPLACEMENT)) {
            parent.addExtension(new HTTPUrlReplacement());
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    protected boolean handleOutputExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    protected boolean handleFaultExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    protected boolean handleServiceExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    protected boolean handlePortExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, HTTPConstants.QNAME_ADDRESS)) {
            context.push();
            context.registerNamespaces(e);

            HTTPAddress address = new HTTPAddress();

            String location =
                Util.getRequiredAttribute(e, Constants.ATTR_LOCATION);
            address.setLocation(location);

            parent.addExtension(address);
            context.pop();
            context.fireDoneParsingEntity(HTTPConstants.QNAME_ADDRESS, address);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    protected boolean handleMIMEPartExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }

    public void doHandleExtension(WriterContext context, Extension extension)
        throws IOException {
        if (extension instanceof HTTPAddress) {
            HTTPAddress address = (HTTPAddress) extension;
            context.writeStartTag(address.getElementName());
            context.writeAttribute(
                Constants.ATTR_LOCATION,
                address.getLocation());
            context.writeEndTag(address.getElementName());
        } else if (extension instanceof HTTPBinding) {
            HTTPBinding binding = (HTTPBinding) extension;
            context.writeStartTag(binding.getElementName());
            context.writeAttribute(Constants.ATTR_VERB, binding.getVerb());
            context.writeEndTag(binding.getElementName());
        } else if (extension instanceof HTTPOperation) {
            HTTPOperation operation = (HTTPOperation) extension;
            context.writeStartTag(operation.getElementName());
            context.writeAttribute(
                Constants.ATTR_LOCATION,
                operation.getLocation());
            context.writeEndTag(operation.getElementName());
        } else if (extension instanceof HTTPUrlEncoded) {
            context.writeStartTag(extension.getElementName());
            context.writeEndTag(extension.getElementName());
        } else if (extension instanceof HTTPUrlReplacement) {
            context.writeStartTag(extension.getElementName());
            context.writeEndTag(extension.getElementName());
        } else {
            throw new IllegalArgumentException();
        }
    }

    /* (non-Javadoc)
     * @see ExtensionHandlerBase#handlePortTypeExtension(ParserContext, Extensible, org.w3c.dom.Element)
     */
    protected boolean handlePortTypeExtension(ParserContext context, Extensible parent, Element e) {
        Util.fail(
            "parsing.invalidExtensionElement",
            e.getTagName(),
            e.getNamespaceURI());
        return false; // keep compiler happy
    }
}
