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

package com.sun.tools.internal.ws.wsdl.parser;

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLParserContext;
import com.sun.tools.internal.ws.util.xml.XmlUtil;
import com.sun.tools.internal.ws.wsdl.document.WSDLConstants;
import com.sun.tools.internal.ws.wsdl.document.mime.*;
import org.w3c.dom.Element;

import java.util.Iterator;
import java.util.Map;

/**
 * The MIME extension handler for WSDL.
 *
 * @author WS Development Team
 */
public class MIMEExtensionHandler extends AbstractExtensionHandler {

    public MIMEExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap) {
        super(extensionHandlerMap);
    }

    public String getNamespaceURI() {
        return Constants.NS_WSDL_MIME;
    }

    @Override
    public boolean doHandleExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (parent.getWSDLElementName().equals(WSDLConstants.QNAME_OUTPUT)) {
            return handleInputOutputExtension(context, parent, e);
        } else if (parent.getWSDLElementName().equals(WSDLConstants.QNAME_INPUT)) {
            return handleInputOutputExtension(context, parent, e);
        } else if (parent.getWSDLElementName().equals(MIMEConstants.QNAME_PART)) {
            return handleMIMEPartExtension(context, parent, e);
        } else {
//            context.fireIgnoringExtension(
//                new QName(e.getNamespaceURI(), e.getLocalName()),
//                parent.getWSDLElementName());
            return false;
        }
    }

    protected boolean handleInputOutputExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, MIMEConstants.QNAME_MULTIPART_RELATED)) {
            context.push();
            context.registerNamespaces(e);

            MIMEMultipartRelated mpr = new MIMEMultipartRelated(context.getLocation(e));

            for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
                Element e2 = Util.nextElement(iter);
                if (e2 == null)
                    break;

                if (XmlUtil.matchesTagNS(e2, MIMEConstants.QNAME_PART)) {
                    context.push();
                    context.registerNamespaces(e2);

                    MIMEPart part = new MIMEPart(context.getLocation(e2));

                    String name =
                        XmlUtil.getAttributeOrNull(e2, Constants.ATTR_NAME);
                    if (name != null) {
                        part.setName(name);
                    }

                    for (Iterator iter2 = XmlUtil.getAllChildren(e2);
                         iter2.hasNext();
                        ) {
                        Element e3 = Util.nextElement(iter2);
                        if (e3 == null)
                            break;

                        AbstractExtensionHandler h = getExtensionHandlers().get(e3.getNamespaceURI());
                        boolean handled = false;
                        if (h != null) {
                            handled = h.doHandleExtension(context, part, e3);
                        }

                        if (!handled) {
                            String required =
                                XmlUtil.getAttributeNSOrNull(
                                    e3,
                                    Constants.ATTR_REQUIRED,
                                    Constants.NS_WSDL);
                            if (required != null
                                && required.equals(Constants.TRUE)) {
                                Util.fail(
                                    "parsing.requiredExtensibilityElement",
                                    e3.getTagName(),
                                    e3.getNamespaceURI());
                            } else {
//                                context.fireIgnoringExtension(
//                                    new QName(
//                                        e3.getNamespaceURI(),
//                                        e3.getLocalName()),
//                                    part.getElementName());
                            }
                        }
                    }

                    mpr.add(part);
                    context.pop();
//                    context.fireDoneParsingEntity(
//                        MIMEConstants.QNAME_PART,
//                        part);
                } else {
                    Util.fail(
                        "parsing.invalidElement",
                        e2.getTagName(),
                        e2.getNamespaceURI());
                }
            }

            parent.addExtension(mpr);
            context.pop();
//            context.fireDoneParsingEntity(
//                MIMEConstants.QNAME_MULTIPART_RELATED,
//                mpr);
            return true;
        } else if (XmlUtil.matchesTagNS(e, MIMEConstants.QNAME_CONTENT)) {
            MIMEContent content = parseMIMEContent(context, e);
            parent.addExtension(content);
            return true;
        } else if (XmlUtil.matchesTagNS(e, MIMEConstants.QNAME_MIME_XML)) {
            MIMEXml mimeXml = parseMIMEXml(context, e);
            parent.addExtension(mimeXml);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    @Override
    protected boolean handleMIMEPartExtension(
        TWSDLParserContext context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, MIMEConstants.QNAME_CONTENT)) {
            MIMEContent content = parseMIMEContent(context, e);
            parent.addExtension(content);
            return true;
        } else if (XmlUtil.matchesTagNS(e, MIMEConstants.QNAME_MIME_XML)) {
            MIMEXml mimeXml = parseMIMEXml(context, e);
            parent.addExtension(mimeXml);
            return true;
        } else {
            Util.fail(
                "parsing.invalidExtensionElement",
                e.getTagName(),
                e.getNamespaceURI());
            return false; // keep compiler happy
        }
    }

    protected MIMEContent parseMIMEContent(TWSDLParserContext context, Element e) {
        context.push();
        context.registerNamespaces(e);

        MIMEContent content = new MIMEContent(context.getLocation(e));

        String part = XmlUtil.getAttributeOrNull(e, Constants.ATTR_PART);
        if (part != null) {
            content.setPart(part);
        }

        String type = XmlUtil.getAttributeOrNull(e, Constants.ATTR_TYPE);
        if (type != null) {
            content.setType(type);
        }

        context.pop();
//        context.fireDoneParsingEntity(MIMEConstants.QNAME_CONTENT, content);
        return content;
    }

    protected MIMEXml parseMIMEXml(TWSDLParserContext context, Element e) {
        context.push();
        context.registerNamespaces(e);

        MIMEXml mimeXml = new MIMEXml(context.getLocation(e));

        String part = XmlUtil.getAttributeOrNull(e, Constants.ATTR_PART);
        if (part != null) {
            mimeXml.setPart(part);
        }

        context.pop();
//        context.fireDoneParsingEntity(MIMEConstants.QNAME_MIME_XML, mimeXml);
        return mimeXml;
    }
}
