/*
 * Portions Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * ===========================================================================
 *
 * (C) Copyright IBM Corp. 2003 All Rights Reserved.
 *
 * ===========================================================================
 */
/*
 * $Id: DOMXPathFilter2Transform.java,v 1.18 2005/09/19 18:30:30 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.crypto.dsig.spec.XPathType;
import javax.xml.crypto.dsig.spec.XPathFilter2ParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

/**
 * DOM-based implementation of XPath Filter 2.0 Transform.
 * (Uses Apache XML-Sec Transform implementation)
 *
 * @author Joyce Leung
 */
public final class DOMXPathFilter2Transform extends ApacheTransform {

    public void init(TransformParameterSpec params)
        throws InvalidAlgorithmParameterException {
        if (params == null) {
            throw new InvalidAlgorithmParameterException("params are required");
        } else if (!(params instanceof XPathFilter2ParameterSpec)) {
            throw new InvalidAlgorithmParameterException
                ("params must be of type XPathFilter2ParameterSpec");
        }
        this.params = params;
    }

    public void init(XMLStructure parent, XMLCryptoContext context)
        throws InvalidAlgorithmParameterException {

        super.init(parent, context);
        try {
            unmarshalParams(DOMUtils.getFirstChildElement(transformElem));
        } catch (MarshalException me) {
            throw (InvalidAlgorithmParameterException)
                new InvalidAlgorithmParameterException().initCause(me);
        }
    }

    private void unmarshalParams(Element curXPathElem) throws MarshalException {
        List list = new ArrayList();
        while (curXPathElem != null) {
            String xPath = curXPathElem.getFirstChild().getNodeValue();
            String filterVal =
                DOMUtils.getAttributeValue(curXPathElem, "Filter");
            if (filterVal == null) {
                throw new MarshalException("filter cannot be null");
            }
            XPathType.Filter filter = null;
            if (filterVal.equals("intersect")) {
                filter = XPathType.Filter.INTERSECT;
            } else if (filterVal.equals("subtract")) {
                filter = XPathType.Filter.SUBTRACT;
            } else if (filterVal.equals("union")) {
                filter = XPathType.Filter.UNION;
            } else {
                throw new MarshalException("Unknown XPathType filter type"
                    + filterVal);
            }
            NamedNodeMap attributes = curXPathElem.getAttributes();
            if (attributes != null) {
                int length = attributes.getLength();
                Map namespaceMap = new HashMap(length);
                for (int i = 0; i < length; i++) {
                    Attr attr = (Attr) attributes.item(i);
                    String prefix = attr.getPrefix();
                    if (prefix != null && prefix.equals("xmlns")) {
                        namespaceMap.put(attr.getLocalName(), attr.getValue());
                    }
                }
                list.add(new XPathType(xPath, filter, namespaceMap));
            } else {
                list.add(new XPathType(xPath, filter));
            }

            curXPathElem = DOMUtils.getNextSiblingElement(curXPathElem);
        }
        this.params = new XPathFilter2ParameterSpec(list);
    }

    public void marshalParams(XMLStructure parent, XMLCryptoContext context)
        throws MarshalException {

        super.marshalParams(parent, context);
        XPathFilter2ParameterSpec xp =
            (XPathFilter2ParameterSpec) getParameterSpec();
        String prefix = DOMUtils.getNSPrefix(context, Transform.XPATH2);
        String qname = (prefix == null) ? "xmlns" : "xmlns:" + prefix;
        List list = xp.getXPathList();
        for (int i = 0, size = list.size(); i < size; i++) {
            XPathType xpathType = (XPathType) list.get(i);
            Element elem = DOMUtils.createElement
                (ownerDoc, "XPath", Transform.XPATH2, prefix);
            elem.appendChild
                (ownerDoc.createTextNode(xpathType.getExpression()));
            DOMUtils.setAttribute
                (elem, "Filter", xpathType.getFilter().toString());
            elem.setAttributeNS("http://www.w3.org/2000/xmlns/", qname,
                Transform.XPATH2);

            // add namespace attributes, if necessary
            Iterator it = xpathType.getNamespaceMap().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                elem.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:"
                    + (String) entry.getKey(), (String) entry.getValue());
            }

            transformElem.appendChild(elem);
        }
    }
}
