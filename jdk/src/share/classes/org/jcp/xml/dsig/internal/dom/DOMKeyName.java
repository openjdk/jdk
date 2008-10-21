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
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 */
/*
 * $Id: DOMKeyName.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
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
