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
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMManifest.java 1333415 2012-05-03 12:03:51Z coheigea $
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

/**
 * DOM-based implementation of Manifest.
 *
 * @author Sean Mullan
 */
public final class DOMManifest extends DOMStructure implements Manifest {

    private final List<Reference> references;
    private final String id;

    /**
     * Creates a <code>DOMManifest</code> containing the specified
     * list of {@link Reference}s and optional id.
     *
     * @param references a list of one or more <code>Reference</code>s. The list
     *    is defensively copied to protect against subsequent modification.
     * @param id the id (may be <code>null</code>
     * @throws NullPointerException if <code>references</code> is
     *    <code>null</code>
     * @throws IllegalArgumentException if <code>references</code> is empty
     * @throws ClassCastException if <code>references</code> contains any
     *    entries that are not of type {@link Reference}
     */
    public DOMManifest(List<? extends Reference> references, String id) {
        if (references == null) {
            throw new NullPointerException("references cannot be null");
        }
        this.references =
            Collections.unmodifiableList(new ArrayList<Reference>(references));
        if (this.references.isEmpty()) {
            throw new IllegalArgumentException("list of references must " +
                "contain at least one entry");
        }
        for (int i = 0, size = this.references.size(); i < size; i++) {
            if (!(this.references.get(i) instanceof Reference)) {
                throw new ClassCastException
                    ("references["+i+"] is not a valid type");
            }
        }
        this.id = id;
    }

    /**
     * Creates a <code>DOMManifest</code> from an element.
     *
     * @param manElem a Manifest element
     */
    public DOMManifest(Element manElem, XMLCryptoContext context,
                       Provider provider)
        throws MarshalException
    {
        Attr attr = manElem.getAttributeNodeNS(null, "Id");
        if (attr != null) {
            this.id = attr.getValue();
            manElem.setIdAttributeNode(attr, true);
        } else {
            this.id = null;
        }

        boolean secVal = Utils.secureValidation(context);

        Element refElem = DOMUtils.getFirstChildElement(manElem, "Reference");
        List<Reference> refs = new ArrayList<Reference>();
        refs.add(new DOMReference(refElem, context, provider));

        refElem = DOMUtils.getNextSiblingElement(refElem);
        while (refElem != null) {
            String localName = refElem.getLocalName();
            if (!localName.equals("Reference")) {
                throw new MarshalException("Invalid element name: " +
                                           localName + ", expected Reference");
            }
            refs.add(new DOMReference(refElem, context, provider));
            if (secVal && (refs.size() > DOMSignedInfo.MAXIMUM_REFERENCE_COUNT)) {
                String error = "A maxiumum of " + DOMSignedInfo.MAXIMUM_REFERENCE_COUNT + " "
                    + "references per Manifest are allowed with secure validation";
                throw new MarshalException(error);
            }
            refElem = DOMUtils.getNextSiblingElement(refElem);
        }
        this.references = Collections.unmodifiableList(refs);
    }

    public String getId() {
        return id;
    }

    public List getReferences() {
        return references;
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException
    {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);
        Element manElem = DOMUtils.createElement(ownerDoc, "Manifest",
                                                 XMLSignature.XMLNS, dsPrefix);

        DOMUtils.setAttributeID(manElem, "Id", id);

        // add references
        for (Reference ref : references) {
            ((DOMReference)ref).marshal(manElem, dsPrefix, context);
        }
        parent.appendChild(manElem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Manifest)) {
            return false;
        }
        Manifest oman = (Manifest)o;

        boolean idsEqual = (id == null ? oman.getId() == null
                                       : id.equals(oman.getId()));

        return (idsEqual && references.equals(oman.getReferences()));
    }

    @Override
    public int hashCode() {
        int result = 17;
        if (id != null) {
            result = 31 * result + id.hashCode();
        }
        result = 31 * result + references.hashCode();

        return result;
    }
}
