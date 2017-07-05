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
 * $Id: DOMKeyName.java,v 1.12 2005/05/10 18:15:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.keyinfo.KeyName;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * DOM-based implementation of KeyName.
 *
 * @author Sean Mullan
 */
public final class DOMKeyName extends DOMStructure implements KeyName {

    private final String name;

    /**
     * Creates a <code>DOMKeyName</code>.
     *
     * @param name the name of the key identifier
     * @throws NullPointerException if <code>name</code> is null
     */
    public DOMKeyName(String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        this.name = name;
    }

    /**
     * Creates a <code>DOMKeyName</code> from a KeyName element.
     *
     * @param knElem a KeyName element
     */
    public DOMKeyName(Element knElem) {
        name = knElem.getFirstChild().getNodeValue();
    }

    public String getName() {
        return name;
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);
        // prepend namespace prefix, if necessary
        Element knElem = DOMUtils.createElement
            (ownerDoc, "KeyName", XMLSignature.XMLNS, dsPrefix);
        knElem.appendChild(ownerDoc.createTextNode(name));
        parent.appendChild(knElem);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof KeyName)) {
            return false;
        }
        KeyName okn = (KeyName) obj;
        return name.equals(okn.getName());
    }
}
