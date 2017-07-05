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
 * $Id: DOMKeyInfoFactory.java,v 1.24 2005/09/23 20:18:50 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.math.BigInteger;
import java.security.*;
import java.util.List;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dom.*;
import javax.xml.crypto.dsig.keyinfo.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * DOM-based implementation of KeyInfoFactory.
 *
 * @author Sean Mullan
 */
public final class DOMKeyInfoFactory extends KeyInfoFactory {


    public DOMKeyInfoFactory() { }

    public KeyInfo newKeyInfo(List content) {
        return newKeyInfo(content, null);
    }

    public KeyInfo newKeyInfo(List content, String id) {
        return new DOMKeyInfo(content, id);
    }

    public KeyName newKeyName(String name) {
        return new DOMKeyName(name);
    }

    public KeyValue newKeyValue(PublicKey key)  throws KeyException {
        return new DOMKeyValue(key);
    }

    public PGPData newPGPData(byte[] keyId) {
        return newPGPData(keyId, null, null);
    }

    public PGPData newPGPData(byte[] keyId, byte[] keyPacket, List other) {
        return new DOMPGPData(keyId, keyPacket, other);
    }

    public PGPData newPGPData(byte[] keyPacket, List other) {
        return new DOMPGPData(keyPacket, other);
    }

    public RetrievalMethod newRetrievalMethod(String uri) {
        return newRetrievalMethod(uri, null, null);
    }

    public RetrievalMethod newRetrievalMethod(String uri, String type,
        List transforms) {
        if (uri == null) {
            throw new NullPointerException("uri must not be null");
        }
        return new DOMRetrievalMethod(uri, type, transforms);
    }

    public X509Data newX509Data(List content) {
        return new DOMX509Data(content);
    }

    public X509IssuerSerial newX509IssuerSerial(String issuerName,
        BigInteger serialNumber) {
        return new DOMX509IssuerSerial(issuerName, serialNumber);
    }

    public boolean isFeatureSupported(String feature) {
        if (feature == null) {
            throw new NullPointerException();
        } else {
            return false;
        }
    }

    public URIDereferencer getURIDereferencer() {
        return DOMURIDereferencer.INSTANCE;
    }

    public KeyInfo unmarshalKeyInfo(XMLStructure xmlStructure)
        throws MarshalException {
        if (xmlStructure == null) {
            throw new NullPointerException("xmlStructure cannot be null");
        }
        Node node =
            ((javax.xml.crypto.dom.DOMStructure) xmlStructure).getNode();
        node.normalize();

        Element element = null;
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            element = ((Document) node).getDocumentElement();
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            element = (Element) node;
        } else {
            throw new MarshalException
                ("xmlStructure does not contain a proper Node");
        }

        // check tag
        String tag = element.getLocalName();
        if (tag == null) {
            throw new MarshalException("Document implementation must " +
                "support DOM Level 2 and be namespace aware");
        }
        if (tag.equals("KeyInfo")) {
            return new DOMKeyInfo(element, null);
        } else {
            throw new MarshalException("invalid KeyInfo tag: " + tag);
        }
    }
}
