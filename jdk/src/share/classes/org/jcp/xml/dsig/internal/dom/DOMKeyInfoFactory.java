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
 * $Id: DOMKeyInfoFactory.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.math.BigInteger;
import java.security.KeyException;
import java.security.PublicKey;
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
            return new DOMKeyInfo(element, null, getProvider());
        } else {
            throw new MarshalException("invalid KeyInfo tag: " + tag);
        }
    }
}
