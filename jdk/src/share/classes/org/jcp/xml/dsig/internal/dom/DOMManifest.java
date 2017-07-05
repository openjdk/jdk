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
 * $Id: DOMManifest.java,v 1.16 2005/05/12 19:28:31 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;

import java.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * DOM-based implementation of Manifest.
 *
 * @author Sean Mullan
 */
public final class DOMManifest extends DOMStructure implements Manifest {

    private final List references;
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
    public DOMManifest(List references, String id) {
        if (references == null) {
            throw new NullPointerException("references cannot be null");
        }
        List refCopy = new ArrayList(references);
        if (refCopy.isEmpty()) {
            throw new IllegalArgumentException("list of references must " +
                "contain at least one entry");
        }
        for (int i = 0, size = refCopy.size(); i < size; i++) {
            if (!(refCopy.get(i) instanceof Reference)) {
                throw new ClassCastException
                    ("references["+i+"] is not a valid type");
            }
        }
        this.references = Collections.unmodifiableList(refCopy);
        this.id = id;
    }

    /**
     * Creates a <code>DOMManifest</code> from an element.
     *
     * @param manElem a Manifest element
     */
    public DOMManifest(Element manElem, XMLCryptoContext context)
        throws MarshalException {
        this.id = DOMUtils.getAttributeValue(manElem, "Id");
        Element refElem = DOMUtils.getFirstChildElement(manElem);
        List refs = new ArrayList();
        while (refElem != null) {
            refs.add(new DOMReference(refElem, context));
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
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element manElem = DOMUtils.createElement
            (ownerDoc, "Manifest", XMLSignature.XMLNS, dsPrefix);

        DOMUtils.setAttributeID(manElem, "Id", id);

        // add references
        for (int i = 0, size = references.size(); i < size; i++) {
            DOMReference ref = (DOMReference) references.get(i);
            ref.marshal(manElem, dsPrefix, context);
        }
        parent.appendChild(manElem);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Manifest)) {
            return false;
        }
        Manifest oman = (Manifest) o;

        boolean idsEqual = (id == null ? oman.getId() == null :
            id.equals(oman.getId()));

        return (idsEqual && references.equals(oman.getReferences()));
    }
}
