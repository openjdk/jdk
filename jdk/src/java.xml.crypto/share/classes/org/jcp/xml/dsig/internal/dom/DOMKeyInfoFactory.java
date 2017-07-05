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
 * $Id: DOMKeyInfoFactory.java 1333869 2012-05-04 10:42:44Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import java.math.BigInteger;
import java.security.KeyException;
import java.security.PublicKey;
import java.util.List;
import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
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

    @SuppressWarnings("rawtypes")
    public KeyInfo newKeyInfo(List content) {
        return newKeyInfo(content, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public KeyInfo newKeyInfo(List content, String id) {
        return new DOMKeyInfo(content, id);
    }

    public KeyName newKeyName(String name) {
        return new DOMKeyName(name);
    }

    public KeyValue newKeyValue(PublicKey key)  throws KeyException {
        String algorithm = key.getAlgorithm();
        if (algorithm.equals("DSA")) {
            return new DOMKeyValue.DSA(key);
        } else if (algorithm.equals("RSA")) {
            return new DOMKeyValue.RSA(key);
        } else if (algorithm.equals("EC")) {
            return new DOMKeyValue.EC(key);
        } else {
            throw new KeyException("unsupported key algorithm: " + algorithm);
        }
    }

    public PGPData newPGPData(byte[] keyId) {
        return newPGPData(keyId, null, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public PGPData newPGPData(byte[] keyId, byte[] keyPacket, List other) {
        return new DOMPGPData(keyId, keyPacket, other);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public PGPData newPGPData(byte[] keyPacket, List other) {
        return new DOMPGPData(keyPacket, other);
    }

    public RetrievalMethod newRetrievalMethod(String uri) {
        return newRetrievalMethod(uri, null, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public RetrievalMethod newRetrievalMethod(String uri, String type,
        List transforms) {
        if (uri == null) {
            throw new NullPointerException("uri must not be null");
        }
        return new DOMRetrievalMethod(uri, type, transforms);
    }

    @SuppressWarnings("rawtypes")
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
        if (!(xmlStructure instanceof javax.xml.crypto.dom.DOMStructure)) {
            throw new ClassCastException("xmlStructure must be of type DOMStructure");
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
            return new DOMKeyInfo(element, new UnmarshalContext(), getProvider());
        } else {
            throw new MarshalException("invalid KeyInfo tag: " + tag);
        }
    }

    private static class UnmarshalContext extends DOMCryptoContext {
        UnmarshalContext() {}
    }

}
