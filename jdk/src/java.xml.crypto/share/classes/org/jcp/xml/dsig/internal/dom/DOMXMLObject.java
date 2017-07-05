/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMXMLObject.java 1333415 2012-05-03 12:03:51Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;

import java.security.Provider;
import java.util.*;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * DOM-based implementation of XMLObject.
 *
 * @author Sean Mullan
 */
public final class DOMXMLObject extends DOMStructure implements XMLObject {

    private final String id;
    private final String mimeType;
    private final String encoding;
    private final List<XMLStructure> content;
    private Element objectElem;

    /**
     * Creates an <code>XMLObject</code> from the specified parameters.
     *
     * @param content a list of {@link XMLStructure}s. The list
     *    is defensively copied to protect against subsequent modification.
     *    May be <code>null</code> or empty.
     * @param id the Id (may be <code>null</code>)
     * @param mimeType the mime type (may be <code>null</code>)
     * @param encoding the encoding (may be <code>null</code>)
     * @return an <code>XMLObject</code>
     * @throws ClassCastException if <code>content</code> contains any
     *    entries that are not of type {@link XMLStructure}
     */
    public DOMXMLObject(List<? extends XMLStructure> content, String id,
                        String mimeType, String encoding)
    {
        if (content == null || content.isEmpty()) {
            this.content = Collections.emptyList();
        } else {
            this.content = Collections.unmodifiableList(
                new ArrayList<XMLStructure>(content));
            for (int i = 0, size = this.content.size(); i < size; i++) {
                if (!(this.content.get(i) instanceof XMLStructure)) {
                    throw new ClassCastException
                        ("content["+i+"] is not a valid type");
                }
            }
        }
        this.id = id;
        this.mimeType = mimeType;
        this.encoding = encoding;
    }

    /**
     * Creates an <code>XMLObject</code> from an element.
     *
     * @param objElem an Object element
     * @throws MarshalException if there is an error when unmarshalling
     */
    public DOMXMLObject(Element objElem, XMLCryptoContext context,
                        Provider provider)
    throws MarshalException
    {
        // unmarshal attributes
        this.encoding = DOMUtils.getAttributeValue(objElem, "Encoding");

        Attr attr = objElem.getAttributeNodeNS(null, "Id");
        if (attr != null) {
            this.id = attr.getValue();
            objElem.setIdAttributeNode(attr, true);
        } else {
            this.id = null;
        }
        this.mimeType = DOMUtils.getAttributeValue(objElem, "MimeType");

        NodeList nodes = objElem.getChildNodes();
        int length = nodes.getLength();
        List<XMLStructure> content = new ArrayList<XMLStructure>(length);
        for (int i = 0; i < length; i++) {
            Node child = nodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElem = (Element)child;
                String tag = childElem.getLocalName();
                if (tag.equals("Manifest")) {
                    content.add(new DOMManifest(childElem, context, provider));
                    continue;
                } else if (tag.equals("SignatureProperties")) {
                    content.add(new DOMSignatureProperties(childElem, context));
                    continue;
                } else if (tag.equals("X509Data")) {
                    content.add(new DOMX509Data(childElem));
                    continue;
                }
                //@@@FIXME: check for other dsig structures
            }
            content.add(new javax.xml.crypto.dom.DOMStructure(child));
        }
        if (content.isEmpty()) {
            this.content = Collections.emptyList();
        } else {
            this.content = Collections.unmodifiableList(content);
        }
        this.objectElem = objElem;
    }

    public List<XMLStructure> getContent() {
        return content;
    }

    public String getId() {
        return id;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getEncoding() {
        return encoding;
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element objElem = objectElem != null ? objectElem : null;
        if (objElem == null) {
            objElem = DOMUtils.createElement(ownerDoc, "Object",
                                             XMLSignature.XMLNS, dsPrefix);

            // set attributes
            DOMUtils.setAttributeID(objElem, "Id", id);
            DOMUtils.setAttribute(objElem, "MimeType", mimeType);
            DOMUtils.setAttribute(objElem, "Encoding", encoding);

            // create and append any elements and mixed content, if necessary
            for (XMLStructure object : content) {
                if (object instanceof DOMStructure) {
                    ((DOMStructure)object).marshal(objElem, dsPrefix, context);
                } else {
                    javax.xml.crypto.dom.DOMStructure domObject =
                        (javax.xml.crypto.dom.DOMStructure)object;
                    DOMUtils.appendChild(objElem, domObject.getNode());
                }
            }
        }

        parent.appendChild(objElem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof XMLObject)) {
            return false;
        }
        XMLObject oxo = (XMLObject)o;

        boolean idsEqual = (id == null ? oxo.getId() == null
                                       : id.equals(oxo.getId()));
        boolean encodingsEqual =
            (encoding == null ? oxo.getEncoding() == null
                              : encoding.equals(oxo.getEncoding()));
        boolean mimeTypesEqual =
            (mimeType == null ? oxo.getMimeType() == null
                              : mimeType.equals(oxo.getMimeType()));

        @SuppressWarnings("unchecked")
        List<XMLStructure> oxoContent = oxo.getContent();
        return (idsEqual && encodingsEqual && mimeTypesEqual &&
                equalsContent(oxoContent));
    }

    @Override
    public int hashCode() {
        int result = 17;
        if (id != null) {
            result = 31 * result + id.hashCode();
        }
        if (encoding != null) {
            result = 31 * result + encoding.hashCode();
        }
        if (mimeType != null) {
            result = 31 * result + mimeType.hashCode();
        }
        result = 31 * result + content.hashCode();

        return result;
    }

    private boolean equalsContent(List<XMLStructure> otherContent) {
        if (content.size() != otherContent.size()) {
            return false;
        }
        for (int i = 0, osize = otherContent.size(); i < osize; i++) {
            XMLStructure oxs = otherContent.get(i);
            XMLStructure xs = content.get(i);
            if (oxs instanceof javax.xml.crypto.dom.DOMStructure) {
                if (!(xs instanceof javax.xml.crypto.dom.DOMStructure)) {
                    return false;
                }
                Node onode = ((javax.xml.crypto.dom.DOMStructure)oxs).getNode();
                Node node = ((javax.xml.crypto.dom.DOMStructure)xs).getNode();
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
