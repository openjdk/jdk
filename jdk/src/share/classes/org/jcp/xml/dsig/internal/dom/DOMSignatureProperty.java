/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * $Id: DOMSignatureProperty.java,v 1.14 2005/05/12 19:28:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;

import java.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * DOM-based implementation of SignatureProperty.
 *
 * @author Sean Mullan
 */
public final class DOMSignatureProperty extends DOMStructure
    implements SignatureProperty {

    private final String id;
    private final String target;
    private final List content;

    /**
     * Creates a <code>SignatureProperty</code> from the specified parameters.
     *
     * @param content a list of one or more {@link XMLStructure}s. The list
     *    is defensively copied to protect against subsequent modification.
     * @param target the target URI
     * @param id the Id (may be <code>null</code>)
     * @return a <code>SignatureProperty</code>
     * @throws ClassCastException if <code>content</code> contains any
     *    entries that are not of type {@link XMLStructure}
     * @throws IllegalArgumentException if <code>content</code> is empty
     * @throws NullPointerException if <code>content</code> or
     *    <code>target</code> is <code>null</code>
     */
    public DOMSignatureProperty(List content, String target, String id) {
        if (target == null) {
            throw new NullPointerException("target cannot be null");
        } else if (content == null) {
            throw new NullPointerException("content cannot be null");
        } else if (content.isEmpty()) {
            throw new IllegalArgumentException("content cannot be empty");
        } else {
            List contentCopy = new ArrayList(content);
            for (int i = 0, size = contentCopy.size(); i < size; i++) {
                if (!(contentCopy.get(i) instanceof XMLStructure)) {
                    throw new ClassCastException
                        ("content["+i+"] is not a valid type");
                }
            }
            this.content = Collections.unmodifiableList(contentCopy);
        }
        this.target = target;
        this.id = id;
    }

    /**
     * Creates a <code>DOMSignatureProperty</code> from an element.
     *
     * @param propElem a SignatureProperty element
     */
    public DOMSignatureProperty(Element propElem) throws MarshalException {
        // unmarshal attributes
        target = DOMUtils.getAttributeValue(propElem, "Target");
        if (target == null) {
            throw new MarshalException("target cannot be null");
        }
        id = DOMUtils.getAttributeValue(propElem, "Id");

        NodeList nodes = propElem.getChildNodes();
        int length = nodes.getLength();
        List content = new ArrayList(length);
        for (int i = 0; i < length; i++) {
            content.add(new javax.xml.crypto.dom.DOMStructure(nodes.item(i)));
        }
        if (content.isEmpty()) {
            throw new MarshalException("content cannot be empty");
        } else {
            this.content = Collections.unmodifiableList(content);
        }
    }

    public List getContent() {
        return content;
    }

    public String getId() {
        return id;
    }

    public String getTarget() {
        return target;
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element propElem = DOMUtils.createElement
            (ownerDoc, "SignatureProperty", XMLSignature.XMLNS, dsPrefix);

        // set attributes
        DOMUtils.setAttributeID(propElem, "Id", id);
        DOMUtils.setAttribute(propElem, "Target", target);

        // create and append any elements and mixed content
        for (int i = 0, size = content.size(); i < size; i++) {
            javax.xml.crypto.dom.DOMStructure property =
                (javax.xml.crypto.dom.DOMStructure) content.get(i);
            DOMUtils.appendChild(propElem, property.getNode());
        }

        parent.appendChild(propElem);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SignatureProperty)) {
            return false;
        }
        SignatureProperty osp = (SignatureProperty) o;

        boolean idsEqual = (id == null ? osp.getId() == null :
            id.equals(osp.getId()));

        return (equalsContent(osp.getContent()) &&
            target.equals(osp.getTarget()) && idsEqual);
    }

    private boolean equalsContent(List otherContent) {
        int osize = otherContent.size();
        if (content.size() != osize) {
            return false;
        }
        for (int i = 0; i < osize; i++) {
            XMLStructure oxs = (XMLStructure) otherContent.get(i);
            XMLStructure xs = (XMLStructure) content.get(i);
            if (oxs instanceof javax.xml.crypto.dom.DOMStructure) {
                if (!(xs instanceof javax.xml.crypto.dom.DOMStructure)) {
                    return false;
                }
                Node onode =
                    ((javax.xml.crypto.dom.DOMStructure) oxs).getNode();
                Node node =
                    ((javax.xml.crypto.dom.DOMStructure) xs).getNode();
                if (!DOMUtils.nodesEqual(node, onode)) {
                    return false;
                }
            } else {
                if (!(xs.equals(oxs))) {
                    return false;
                }
            }
        }

        return true;
    }
}
