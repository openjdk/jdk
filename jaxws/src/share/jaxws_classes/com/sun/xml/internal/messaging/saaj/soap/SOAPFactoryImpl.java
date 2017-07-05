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

package com.sun.xml.internal.messaging.saaj.soap;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.soap.impl.ElementFactory;
import com.sun.xml.internal.messaging.saaj.soap.impl.ElementImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.*;

import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Attr;

public abstract class SOAPFactoryImpl extends SOAPFactory {

    protected static final Logger
        log = Logger.getLogger(LogDomainConstants.SOAP_DOMAIN,
                               "com.sun.xml.internal.messaging.saaj.soap.LocalStrings");

    protected abstract SOAPDocumentImpl createDocument();

    public SOAPElement createElement(String tagName) throws SOAPException {
         if (tagName == null) {
             log.log(
                 Level.SEVERE,"SAAJ0567.soap.null.input",
                 new Object[] {"tagName","SOAPFactory.createElement"});
             throw new SOAPException("Null tagName argument passed to createElement");
         }
        return ElementFactory.createElement(createDocument(),
                        NameImpl.createFromTagName(tagName));
    }

    public SOAPElement createElement(Name name) throws SOAPException {
        // @since SAAJ 1.3
        // If the Name was null it would cause a NullPointerException in earlier release
        if (name == null) {
            log.log(Level.SEVERE,"SAAJ0567.soap.null.input",
                        new Object[] {"name","SOAPFactory.createElement"});
            throw new SOAPException("Null name argument passed to createElement");
        }
        return ElementFactory.createElement(createDocument(), name);
    }

    public SOAPElement createElement(QName qname) throws SOAPException {
        if (qname == null) {
            log.log(Level.SEVERE,"SAAJ0567.soap.null.input",
                        new Object[] {"qname","SOAPFactory.createElement"});
            throw new SOAPException("Null qname argument passed to createElement");
        }
        return ElementFactory.createElement(createDocument(),qname);
    }

    public SOAPElement createElement(
        String localName,
        String prefix,
        String uri)  throws SOAPException {

        // @since SAAJ 1.3
        // if prefix !=null but localName== null then in earlier releases it would create
        // a Qualified Name  <prefix>:null which is not meaningful
        if (localName == null) {
            log.log(Level.SEVERE,"SAAJ0567.soap.null.input",
                        new Object[] {"localName","SOAPFactory.createElement"});
            throw new SOAPException("Null localName argument passed to createElement");
        }
        return ElementFactory.createElement(createDocument(), localName, prefix, uri);
    }

    public Name createName(String localName, String prefix, String uri)
        throws SOAPException {
        // @since SAAJ 1.3
        // if localName==null, earlier impl would create Name with localName=""
        // which is absurd.
        if (localName == null) {
            log.log(
                 Level.SEVERE,"SAAJ0567.soap.null.input",
                 new Object[] {"localName","SOAPFactory.createName"});
            throw new SOAPException("Null localName argument passed to createName");
        }
        return NameImpl.create(localName, prefix, uri);
    }

    public Name createName(String localName) throws SOAPException {
        // @since SAAJ 1.3
        // if localName==null, earlier impl would create Name with localName=null
        // which is absurd.
        if (localName == null) {
            log.log(
                Level.SEVERE,"SAAJ0567.soap.null.input",
                new Object[] {"localName","SOAPFactory.createName"});
            throw new SOAPException("Null localName argument passed to createName");
        }
        return NameImpl.createFromUnqualifiedName(localName);
    }

    // Note: the child elements might still be org.w3c.dom.Element's, but the
    // getChildElements will do the conversion to SOAPElement when called.
    public SOAPElement createElement(Element domElement) throws SOAPException {
        if (domElement == null) {
            return null;
        }
        return convertToSoapElement(domElement);
    }

    private  SOAPElement convertToSoapElement(Element element) throws SOAPException {

        if (element instanceof SOAPElement) {
            return (SOAPElement) element;
        }

        SOAPElement copy = createElement(
                                element.getLocalName(),
                                element.getPrefix(),
                                element.getNamespaceURI());

        Document ownerDoc = copy.getOwnerDocument();

        NamedNodeMap attrMap = element.getAttributes();
        for (int i=0; i < attrMap.getLength(); i++) {
            Attr nextAttr = (Attr)attrMap.item(i);
            Attr importedAttr = (Attr)ownerDoc.importNode(nextAttr, true);
            copy.setAttributeNodeNS(importedAttr);
        }


        NodeList nl = element.getChildNodes();
        for (int i=0; i < nl.getLength(); i++) {
            org.w3c.dom.Node next = nl.item(i);
            org.w3c.dom.Node imported = ownerDoc.importNode(next, true);
            copy.appendChild(imported);
        }

        return copy;
    }

    public Detail createDetail() throws SOAPException {
        throw new UnsupportedOperationException();
    }

    public  SOAPFault createFault(String reasonText, QName faultCode) throws SOAPException {
        throw new UnsupportedOperationException();
    }

    public SOAPFault createFault() throws SOAPException {
        throw new UnsupportedOperationException();
    }

}
