/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2005 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMSignatureProperties.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
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
 * DOM-based implementation of SignatureProperties.
 *
 * @author Sean Mullan
 */
public final class DOMSignatureProperties extends DOMStructure
    implements SignatureProperties {

    private final String id;
    private final List properties;

    /**
     * Creates a <code>DOMSignatureProperties</code> from the specified
     * parameters.
     *
     * @param properties a list of one or more {@link SignatureProperty}s. The
     *    list is defensively copied to protect against subsequent modification.
     * @param id the Id (may be <code>null</code>)
     * @return a <code>DOMSignatureProperties</code>
     * @throws ClassCastException if <code>properties</code> contains any
     *    entries that are not of type {@link SignatureProperty}
     * @throws IllegalArgumentException if <code>properties</code> is empty
     * @throws NullPointerException if <code>properties</code>
     */
    public DOMSignatureProperties(List properties, String id) {
        if (properties == null) {
            throw new NullPointerException("properties cannot be null");
        } else if (properties.isEmpty()) {
            throw new IllegalArgumentException("properties cannot be empty");
        } else {
            List propsCopy = new ArrayList(properties);
            for (int i = 0, size = propsCopy.size(); i < size; i++) {
                if (!(propsCopy.get(i) instanceof SignatureProperty)) {
                    throw new ClassCastException
                        ("properties["+i+"] is not a valid type");
                }
            }
            this.properties = Collections.unmodifiableList(propsCopy);
        }
        this.id = id;
    }

    /**
     * Creates a <code>DOMSignatureProperties</code> from an element.
     *
     * @param propsElem a SignatureProperties element
     * @throws MarshalException if a marshalling error occurs
     */
    public DOMSignatureProperties(Element propsElem) throws MarshalException{
        // unmarshal attributes
        id = DOMUtils.getAttributeValue(propsElem, "Id");

        NodeList nodes = propsElem.getChildNodes();
        int length = nodes.getLength();
        List properties = new ArrayList(length);
        for (int i = 0; i < length; i++) {
            Node child = nodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                properties.add(new DOMSignatureProperty((Element) child));
            }
        }
        if (properties.isEmpty()) {
            throw new MarshalException("properties cannot be empty");
        } else {
            this.properties = Collections.unmodifiableList(properties);
        }
    }

    public List getProperties() {
        return properties;
    }

    public String getId() {
        return id;
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element propsElem = DOMUtils.createElement
            (ownerDoc, "SignatureProperties", XMLSignature.XMLNS, dsPrefix);

        // set attributes
        DOMUtils.setAttributeID(propsElem, "Id", id);

        // create and append any properties
        for (int i = 0, size = properties.size(); i < size; i++) {
            DOMSignatureProperty property =
                (DOMSignatureProperty) properties.get(i);
            property.marshal(propsElem, dsPrefix, context);
        }

        parent.appendChild(propsElem);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SignatureProperties)) {
            return false;
        }
        SignatureProperties osp = (SignatureProperties) o;

        boolean idsEqual = (id == null ? osp.getId() == null :
            id.equals(osp.getId()));

        return (properties.equals(osp.getProperties()) && idsEqual);
    }
}
