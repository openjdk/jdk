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
/*
 * $Id: DOMKeyInfo.java,v 1.19 2005/05/12 19:28:30 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dom.*;

import java.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * DOM-based implementation of KeyInfo.
 *
 * @author Sean Mullan
 */
public final class DOMKeyInfo extends DOMStructure implements KeyInfo {

    private final String id;
    private final List keyInfoTypes;

    /**
     * Creates a <code>DOMKeyInfo</code>.
     *
     * @param content a list of one or more {@link XMLStructure}s representing
     *    key information types. The list is defensively copied to protect
     *    against subsequent modification.
     * @param id an ID attribute
     * @throws NullPointerException if <code>content</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>content</code> is empty
     * @throws ClassCastException if <code>content</code> contains any entries
     *    that are not of type {@link XMLStructure}
     */
    public DOMKeyInfo(List content, String id) {
        if (content == null) {
            throw new NullPointerException("content cannot be null");
        }
        List typesCopy = new ArrayList(content);
        if (typesCopy.isEmpty()) {
            throw new IllegalArgumentException("content cannot be empty");
        }
        for (int i = 0, size = typesCopy.size(); i < size; i++) {
            if (!(typesCopy.get(i) instanceof XMLStructure)) {
                throw new ClassCastException
                    ("content["+i+"] is not a valid KeyInfo type");
            }
        }
        this.keyInfoTypes = Collections.unmodifiableList(typesCopy);
        this.id = id;
    }

    /**
     * Creates a <code>DOMKeyInfo</code> from XML.
     *
     * @param input XML input
     */
    public DOMKeyInfo(Element kiElem, XMLCryptoContext context)
        throws MarshalException {
        // get Id attribute, if specified
        id = DOMUtils.getAttributeValue(kiElem, "Id");

        // get all children nodes
        NodeList nl = kiElem.getChildNodes();
        int length = nl.getLength();
        if (length < 1) {
            throw new MarshalException
                ("KeyInfo must contain at least one type");
        }
        List content = new ArrayList(length);
        for (int i = 0; i < length; i++) {
            Node child = nl.item(i);
            // ignore all non-Element nodes
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element) child;
            String localName = childElem.getLocalName();
            if (localName.equals("X509Data")) {
                content.add(new DOMX509Data(childElem));
            } else if (localName.equals("KeyName")) {
                content.add(new DOMKeyName(childElem));
            } else if (localName.equals("KeyValue")) {
                content.add(new DOMKeyValue(childElem));
            } else if (localName.equals("RetrievalMethod")) {
                content.add(new DOMRetrievalMethod(childElem, context));
            } else { //may be MgmtData, SPKIData or element from other namespace
                content.add(new javax.xml.crypto.dom.DOMStructure((childElem)));
            }
        }
        keyInfoTypes = Collections.unmodifiableList(content);
    }

    public String getId() {
        return id;
    }

    public List getContent() {
        return keyInfoTypes;
    }

    public void marshal(XMLStructure parent, XMLCryptoContext context)
        throws MarshalException {
        if (parent == null) {
            throw new NullPointerException("parent is null");
        }

        Node pNode = ((javax.xml.crypto.dom.DOMStructure) parent).getNode();
        String dsPrefix = DOMUtils.getSignaturePrefix(context);
        Element kiElem = DOMUtils.createElement
            (DOMUtils.getOwnerDocument(pNode), "KeyInfo",
             XMLSignature.XMLNS, dsPrefix);
        if (dsPrefix == null) {
            kiElem.setAttributeNS
                ("http://www.w3.org/2000/xmlns/", "xmlns", XMLSignature.XMLNS);
        } else {
            kiElem.setAttributeNS
                ("http://www.w3.org/2000/xmlns/", "xmlns:" + dsPrefix,
                 XMLSignature.XMLNS);
        }
        marshal(pNode, kiElem, null, dsPrefix, (DOMCryptoContext) context);
    }

    public void marshal(Node parent, String dsPrefix,
        DOMCryptoContext context) throws MarshalException {
        marshal(parent, null, dsPrefix, context);
    }

    public void marshal(Node parent, Node nextSibling, String dsPrefix,
        DOMCryptoContext context) throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element kiElem = DOMUtils.createElement
            (ownerDoc, "KeyInfo", XMLSignature.XMLNS, dsPrefix);
        marshal(parent, kiElem, nextSibling, dsPrefix, context);
    }

    private void marshal(Node parent, Element kiElem, Node nextSibling,
        String dsPrefix, DOMCryptoContext context) throws MarshalException {
        // create and append KeyInfoType elements
        for (int i = 0, size = keyInfoTypes.size(); i < size; i++) {
            XMLStructure kiType = (XMLStructure) keyInfoTypes.get(i);
            if (kiType instanceof DOMStructure) {
                ((DOMStructure) kiType).marshal(kiElem, dsPrefix, context);
            } else {
                DOMUtils.appendChild(kiElem,
                    ((javax.xml.crypto.dom.DOMStructure) kiType).getNode());
            }
        }

        // append id attribute
        DOMUtils.setAttributeID(kiElem, "Id", id);

        parent.insertBefore(kiElem, nextSibling);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof KeyInfo)) {
            return false;
        }
        KeyInfo oki = (KeyInfo) o;

        boolean idsEqual = (id == null ? oki.getId() == null :
            id.equals(oki.getId()));

        return (keyInfoTypes.equals(oki.getContent()) && idsEqual);
    }
}
