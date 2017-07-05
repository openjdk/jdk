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
import java.util.Iterator;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import com.sun.tools.internal.ws.wsdl.document.soap.SOAPAddress;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPBinding;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPBody;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPConstants;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPFault;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPHeader;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPHeaderFault;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPOperation;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPStyle;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPUse;
import com.sun.tools.internal.ws.wsdl.framework.Extensible;
import com.sun.tools.internal.ws.wsdl.framework.Extension;
import com.sun.tools.internal.ws.wsdl.framework.ParserContext;
import com.sun.tools.internal.ws.wsdl.framework.WriterContext;
import com.sun.tools.internal.ws.util.xml.XmlUtil;

/**
 * The SOAP extension handler for WSDL.
 *
 * @author WS Development Team
 */
public class SOAPExtensionHandler extends ExtensionHandlerBase {

    public SOAPExtensionHandler() {
    }

    public String getNamespaceURI() {
        return Constants.NS_WSDL_SOAP;
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

    protected SOAPBinding getSOAPBinding(){
        return new SOAPBinding();
    }

    protected boolean handleBindingExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, getBindingQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPBinding binding = getSOAPBinding();

            // NOTE - the "transport" attribute is required according to section 3.3 of the WSDL 1.1 spec,
            // but optional according to the schema in appendix A 4.2 of the same document!
            String transport =
                Util.getRequiredAttribute(e, Constants.ATTR_TRANSPORT);
            binding.setTransport(transport);

            String style = XmlUtil.getAttributeOrNull(e, Constants.ATTR_STYLE);
            if (style != null) {
                if (style.equals(Constants.ATTRVALUE_RPC)) {
                    binding.setStyle(SOAPStyle.RPC);
                } else if (style.equals(Constants.ATTRVALUE_DOCUMENT)) {
                    binding.setStyle(SOAPStyle.DOCUMENT);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_STYLE,
                        style);
                }
            }
            parent.addExtension(binding);
            context.pop();
            context.fireDoneParsingEntity(getBindingQName(), binding);
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
        if (XmlUtil.matchesTagNS(e, getOperationQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPOperation operation = new SOAPOperation();

            String soapAction =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_SOAP_ACTION);
            if (soapAction != null) {
                operation.setSOAPAction(soapAction);
            }

            String style = XmlUtil.getAttributeOrNull(e, Constants.ATTR_STYLE);
            if (style != null) {
                if (style.equals(Constants.ATTRVALUE_RPC)) {
                    operation.setStyle(SOAPStyle.RPC);
                } else if (style.equals(Constants.ATTRVALUE_DOCUMENT)) {
                    operation.setStyle(SOAPStyle.DOCUMENT);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_STYLE,
                        style);
                }
            }
            parent.addExtension(operation);
            context.pop();
            context.fireDoneParsingEntity(
                getOperationQName(),
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
        return handleInputOutputExtension(context, parent, e);
    }
    protected boolean handleOutputExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        return handleInputOutputExtension(context, parent, e);
    }

    protected boolean handleMIMEPartExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        return handleInputOutputExtension(context, parent, e);
    }

    protected boolean handleInputOutputExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, getBodyQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPBody body = new SOAPBody();

            String use = XmlUtil.getAttributeOrNull(e, Constants.ATTR_USE);
            if (use != null) {
                if (use.equals(Constants.ATTRVALUE_LITERAL)) {
                    body.setUse(SOAPUse.LITERAL);
                } else if (use.equals(Constants.ATTRVALUE_ENCODED)) {
                    body.setUse(SOAPUse.ENCODED);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_USE,
                        use);
                }
            }

            String namespace =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAMESPACE);
            if (namespace != null) {
                body.setNamespace(namespace);
            }

            String encodingStyle =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_ENCODING_STYLE);
            if (encodingStyle != null) {
                body.setEncodingStyle(encodingStyle);
            }

            String parts = XmlUtil.getAttributeOrNull(e, Constants.ATTR_PARTS);
            if (parts != null) {
                body.setParts(parts);
            }

            parent.addExtension(body);
            context.pop();
            context.fireDoneParsingEntity(getBodyQName(), body);
            return true;
        } else if (XmlUtil.matchesTagNS(e, getHeaderQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPHeader header = new SOAPHeader();

            String use = XmlUtil.getAttributeOrNull(e, Constants.ATTR_USE);
            if (use != null) {
                if (use.equals(Constants.ATTRVALUE_LITERAL)) {
                    header.setUse(SOAPUse.LITERAL);
                } else if (use.equals(Constants.ATTRVALUE_ENCODED)) {
                    header.setUse(SOAPUse.ENCODED);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_USE,
                        use);
                }
            }

            String namespace =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAMESPACE);
            if (namespace != null) {
                header.setNamespace(namespace);
            }

            String encodingStyle =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_ENCODING_STYLE);
            if (encodingStyle != null) {
                header.setEncodingStyle(encodingStyle);
            }

            String part = XmlUtil.getAttributeOrNull(e, Constants.ATTR_PART);
            if (part != null) {
                header.setPart(part);
            }

            String messageAttr =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_MESSAGE);
            if (messageAttr != null) {
                header.setMessage(context.translateQualifiedName(messageAttr));
            }

            for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;

                if (XmlUtil
                    .matchesTagNS(e2, getHeaderfaultQName())) {
                    context.push();
                    context.registerNamespaces(e);

                    SOAPHeaderFault headerfault = new SOAPHeaderFault();

                    String use2 =
                        XmlUtil.getAttributeOrNull(e2, Constants.ATTR_USE);
                    if (use2 != null) {
                        if (use2.equals(Constants.ATTRVALUE_LITERAL)) {
                            headerfault.setUse(SOAPUse.LITERAL);
                        } else if (use.equals(Constants.ATTRVALUE_ENCODED)) {
                            headerfault.setUse(SOAPUse.ENCODED);
                        } else {
                            Util.fail(
                                "parsing.invalidAttributeValue",
                                Constants.ATTR_USE,
                                use2);
                        }
                    }

                    String namespace2 =
                        XmlUtil.getAttributeOrNull(
                            e2,
                            Constants.ATTR_NAMESPACE);
                    if (namespace2 != null) {
                        headerfault.setNamespace(namespace2);
                    }

                    String encodingStyle2 =
                        XmlUtil.getAttributeOrNull(
                            e2,
                            Constants.ATTR_ENCODING_STYLE);
                    if (encodingStyle2 != null) {
                        headerfault.setEncodingStyle(encodingStyle2);
                    }

                    String part2 =
                        XmlUtil.getAttributeOrNull(e2, Constants.ATTR_PART);
                    if (part2 != null) {
                        headerfault.setPart(part2);
                    }

                    String messageAttr2 =
                        XmlUtil.getAttributeOrNull(e2, Constants.ATTR_MESSAGE);
                    if (messageAttr2 != null) {
                        headerfault.setMessage(
                            context.translateQualifiedName(messageAttr2));
                    }

                    header.add(headerfault);
                    context.pop();
                } else {
                    Util.fail(
                        "parsing.invalidElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                }
            }

            parent.addExtension(header);
            context.pop();
            context.fireDoneParsingEntity(getHeaderQName(), header);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    protected boolean handleFaultExtension(
        ParserContext context,
        Extensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, getFaultQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPFault fault = new SOAPFault();

            String name = XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAME);
            if (name != null) {
                fault.setName(name);
            }

            String use = XmlUtil.getAttributeOrNull(e, Constants.ATTR_USE);
            if (use != null) {
                if (use.equals(Constants.ATTRVALUE_LITERAL)) {
                    fault.setUse(SOAPUse.LITERAL);
                } else if (use.equals(Constants.ATTRVALUE_ENCODED)) {
                    fault.setUse(SOAPUse.ENCODED);
                } else {
                    Util.fail(
                        "parsing.invalidAttributeValue",
                        Constants.ATTR_USE,
                        use);
                }
            }

            String namespace =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAMESPACE);
            if (namespace != null) {
                fault.setNamespace(namespace);
            }

            String encodingStyle =
                XmlUtil.getAttributeOrNull(e, Constants.ATTR_ENCODING_STYLE);
            if (encodingStyle != null) {
                fault.setEncodingStyle(encodingStyle);
            }

            parent.addExtension(fault);
            context.pop();
            context.fireDoneParsingEntity(getFaultQName(), fault);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
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
        if (XmlUtil.matchesTagNS(e, getAddressQName())) {
            context.push();
            context.registerNamespaces(e);

            SOAPAddress address = new SOAPAddress();

            String location =
                Util.getRequiredAttribute(e, Constants.ATTR_LOCATION);
            address.setLocation(location);

            parent.addExtension(address);
            context.pop();
            context.fireDoneParsingEntity(getAddressQName(), address);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    public void doHandleExtension(WriterContext context, Extension extension)
        throws IOException {
        // NOTE - this ugliness can be avoided by moving all the XML parsing/writing code
        // into the document classes themselves
        if (extension instanceof SOAPAddress) {
            SOAPAddress address = (SOAPAddress) extension;
            context.writeStartTag(address.getElementName());
            context.writeAttribute(
                Constants.ATTR_LOCATION,
                address.getLocation());
            context.writeEndTag(address.getElementName());
        } else if (extension instanceof SOAPBinding) {
            SOAPBinding binding = (SOAPBinding) extension;
            context.writeStartTag(binding.getElementName());
            context.writeAttribute(
                Constants.ATTR_TRANSPORT,
                binding.getTransport());
            String style =
                (binding.getStyle() == null
                    ? null
                    : (binding.getStyle() == SOAPStyle.DOCUMENT
                        ? Constants.ATTRVALUE_DOCUMENT
                        : Constants.ATTRVALUE_RPC));
            context.writeAttribute(Constants.ATTR_STYLE, style);
            context.writeEndTag(binding.getElementName());
        } else if (extension instanceof SOAPBody) {
            SOAPBody body = (SOAPBody) extension;
            context.writeStartTag(body.getElementName());
            context.writeAttribute(
                Constants.ATTR_ENCODING_STYLE,
                body.getEncodingStyle());
            context.writeAttribute(Constants.ATTR_PARTS, body.getParts());
            String use =
                (body.getUse() == null
                    ? null
                    : (body.getUse() == SOAPUse.LITERAL
                        ? Constants.ATTRVALUE_LITERAL
                        : Constants.ATTRVALUE_ENCODED));
            context.writeAttribute(Constants.ATTR_USE, use);
            context.writeAttribute(
                Constants.ATTR_NAMESPACE,
                body.getNamespace());
            context.writeEndTag(body.getElementName());
        } else if (extension instanceof SOAPFault) {
            SOAPFault fault = (SOAPFault) extension;
            context.writeStartTag(fault.getElementName());
            context.writeAttribute(Constants.ATTR_NAME, fault.getName());
            context.writeAttribute(
                Constants.ATTR_ENCODING_STYLE,
                fault.getEncodingStyle());
            String use =
                (fault.getUse() == null
                    ? null
                    : (fault.getUse() == SOAPUse.LITERAL
                        ? Constants.ATTRVALUE_LITERAL
                        : Constants.ATTRVALUE_ENCODED));
            context.writeAttribute(Constants.ATTR_USE, use);
            context.writeAttribute(
                Constants.ATTR_NAMESPACE,
                fault.getNamespace());
            context.writeEndTag(fault.getElementName());
        } else if (extension instanceof SOAPHeader) {
            SOAPHeader header = (SOAPHeader) extension;
            context.writeStartTag(header.getElementName());
            context.writeAttribute(Constants.ATTR_MESSAGE, header.getMessage());
            context.writeAttribute(Constants.ATTR_PART, header.getPart());
            context.writeAttribute(
                Constants.ATTR_ENCODING_STYLE,
                header.getEncodingStyle());
            String use =
                (header.getUse() == null
                    ? null
                    : (header.getUse() == SOAPUse.LITERAL
                        ? Constants.ATTRVALUE_LITERAL
                        : Constants.ATTRVALUE_ENCODED));
            context.writeAttribute(Constants.ATTR_USE, use);
            context.writeAttribute(
                Constants.ATTR_NAMESPACE,
                header.getNamespace());
            context.writeEndTag(header.getElementName());
        } else if (extension instanceof SOAPHeaderFault) {
            SOAPHeaderFault headerfault = (SOAPHeaderFault) extension;
            context.writeStartTag(headerfault.getElementName());
            context.writeAttribute(
                Constants.ATTR_MESSAGE,
                headerfault.getMessage());
            context.writeAttribute(Constants.ATTR_PART, headerfault.getPart());
            context.writeAttribute(
                Constants.ATTR_ENCODING_STYLE,
                headerfault.getEncodingStyle());
            String use =
                (headerfault.getUse() == null
                    ? null
                    : (headerfault.getUse() == SOAPUse.LITERAL
                        ? Constants.ATTRVALUE_LITERAL
                        : Constants.ATTRVALUE_ENCODED));
            context.writeAttribute(Constants.ATTR_USE, use);
            context.writeAttribute(
                Constants.ATTR_NAMESPACE,
                headerfault.getNamespace());
            context.writeEndTag(headerfault.getElementName());
        } else if (extension instanceof SOAPOperation) {
            SOAPOperation operation = (SOAPOperation) extension;
            context.writeStartTag(operation.getElementName());
            context.writeAttribute(
                Constants.ATTR_SOAP_ACTION,
                operation.getSOAPAction());
            String style =
                (operation.getStyle() == null
                    ? null
                    : (operation.isDocument()
                        ? Constants.ATTRVALUE_DOCUMENT
                        : Constants.ATTRVALUE_RPC));
            context.writeAttribute(Constants.ATTR_STYLE, style);
            context.writeEndTag(operation.getElementName());
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

    protected QName getBodyQName(){
        return SOAPConstants.QNAME_BODY;
    }

    protected QName getHeaderQName(){
        return SOAPConstants.QNAME_HEADER;
    }

    protected QName getHeaderfaultQName(){
        return SOAPConstants.QNAME_HEADERFAULT;
    }

    protected QName getOperationQName(){
        return SOAPConstants.QNAME_OPERATION;
    }

    protected QName getFaultQName(){
        return SOAPConstants.QNAME_FAULT;
    }

    protected QName getAddressQName(){
        return SOAPConstants.QNAME_ADDRESS;
    }

    protected QName getBindingQName(){
        return SOAPConstants.QNAME_BINDING;
    }
}
