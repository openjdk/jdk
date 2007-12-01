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
 * $Id: DOMKeyValue.java,v 1.18 2005/05/10 18:15:33 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.keyinfo.KeyValue;

import java.security.KeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * DOM-based implementation of KeyValue.
 *
 * @author Sean Mullan
 */
public final class DOMKeyValue extends DOMStructure implements KeyValue {

    private KeyFactory rsakf, dsakf;
    private PublicKey publicKey;
    private javax.xml.crypto.dom.DOMStructure externalPublicKey;

    // DSAKeyValue CryptoBinaries
    private DOMCryptoBinary p, q, g, y, j, seed, pgen;

    // RSAKeyValue CryptoBinaries
    private DOMCryptoBinary modulus, exponent;

    public DOMKeyValue(PublicKey key)  throws KeyException {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        this.publicKey = key;
        if (key instanceof DSAPublicKey) {
            DSAPublicKey dkey = (DSAPublicKey) key;
            DSAParams params = dkey.getParams();
            p = new DOMCryptoBinary(params.getP());
            q = new DOMCryptoBinary(params.getQ());
            g = new DOMCryptoBinary(params.getG());
            y = new DOMCryptoBinary(dkey.getY());
        } else if (key instanceof RSAPublicKey) {
            RSAPublicKey rkey = (RSAPublicKey) key;
            exponent = new DOMCryptoBinary(rkey.getPublicExponent());
            modulus = new DOMCryptoBinary(rkey.getModulus());
        } else {
            throw new KeyException("unsupported key algorithm: " +
                key.getAlgorithm());
        }
    }

    /**
     * Creates a <code>DOMKeyValue</code> from an element.
     *
     * @param kvElem a KeyValue element
     */
    public DOMKeyValue(Element kvElem) throws MarshalException {
        Element kvtElem = DOMUtils.getFirstChildElement(kvElem);
        if (kvtElem.getLocalName().equals("DSAKeyValue")) {
            publicKey = unmarshalDSAKeyValue(kvtElem);
        } else if (kvtElem.getLocalName().equals("RSAKeyValue")) {
            publicKey = unmarshalRSAKeyValue(kvtElem);
        } else {
            publicKey = null;
            externalPublicKey = new javax.xml.crypto.dom.DOMStructure(kvtElem);
        }
    }

    public PublicKey getPublicKey() throws KeyException {
        if (publicKey == null) {
            throw new KeyException("can't convert KeyValue to PublicKey");
        } else {
            return publicKey;
        }
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        // create KeyValue element
        Element kvElem = DOMUtils.createElement
            (ownerDoc, "KeyValue", XMLSignature.XMLNS, dsPrefix);
        marshalPublicKey(kvElem, ownerDoc, dsPrefix, context);

        parent.appendChild(kvElem);
    }

    private void marshalPublicKey(Node parent, Document doc, String dsPrefix,
        DOMCryptoContext context) throws MarshalException {
        if (publicKey != null) {
            if (publicKey instanceof DSAPublicKey) {
                // create and append DSAKeyValue element
                marshalDSAPublicKey(parent, doc, dsPrefix, context);
            } else if (publicKey instanceof RSAPublicKey) {
                // create and append RSAKeyValue element
                marshalRSAPublicKey(parent, doc, dsPrefix, context);
            } else {
                throw new MarshalException(publicKey.getAlgorithm() +
                    " public key algorithm not supported");
            }
        } else {
            parent.appendChild(externalPublicKey.getNode());
        }
    }

    private void marshalDSAPublicKey(Node parent, Document doc,
        String dsPrefix, DOMCryptoContext context) throws MarshalException {
        Element dsaElem = DOMUtils.createElement
            (doc, "DSAKeyValue", XMLSignature.XMLNS, dsPrefix);
        // parameters J, Seed & PgenCounter are not included
        Element pElem = DOMUtils.createElement
            (doc, "P", XMLSignature.XMLNS, dsPrefix);
        Element qElem = DOMUtils.createElement
            (doc, "Q", XMLSignature.XMLNS, dsPrefix);
        Element gElem = DOMUtils.createElement
            (doc, "G", XMLSignature.XMLNS, dsPrefix);
        Element yElem = DOMUtils.createElement
            (doc, "Y", XMLSignature.XMLNS, dsPrefix);
        p.marshal(pElem, dsPrefix, context);
        q.marshal(qElem, dsPrefix, context);
        g.marshal(gElem, dsPrefix, context);
        y.marshal(yElem, dsPrefix, context);
        dsaElem.appendChild(pElem);
        dsaElem.appendChild(qElem);
        dsaElem.appendChild(gElem);
        dsaElem.appendChild(yElem);
        parent.appendChild(dsaElem);
    }

    private void marshalRSAPublicKey(Node parent, Document doc,
        String dsPrefix, DOMCryptoContext context) throws MarshalException {
        Element rsaElem = DOMUtils.createElement
            (doc, "RSAKeyValue", XMLSignature.XMLNS, dsPrefix);
        Element modulusElem = DOMUtils.createElement
            (doc, "Modulus", XMLSignature.XMLNS, dsPrefix);
        Element exponentElem = DOMUtils.createElement
            (doc, "Exponent", XMLSignature.XMLNS, dsPrefix);
        modulus.marshal(modulusElem, dsPrefix, context);
        exponent.marshal(exponentElem, dsPrefix, context);
        rsaElem.appendChild(modulusElem);
        rsaElem.appendChild(exponentElem);
        parent.appendChild(rsaElem);
    }

    private DSAPublicKey unmarshalDSAKeyValue(Element kvtElem)
        throws MarshalException {
        if (dsakf == null) {
            try {
                dsakf = KeyFactory.getInstance("DSA");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("unable to create DSA KeyFactory: " +
                    e.getMessage());
            }
        }
        Element curElem = DOMUtils.getFirstChildElement(kvtElem);
        // check for P and Q
        if (curElem.getLocalName().equals("P")) {
            p = new DOMCryptoBinary(curElem.getFirstChild());
            curElem = DOMUtils.getNextSiblingElement(curElem);
            q = new DOMCryptoBinary(curElem.getFirstChild());
            curElem = DOMUtils.getNextSiblingElement(curElem);
        }
        if (curElem.getLocalName().equals("G")) {
            g = new DOMCryptoBinary(curElem.getFirstChild());
            curElem = DOMUtils.getNextSiblingElement(curElem);
        }
        y = new DOMCryptoBinary(curElem.getFirstChild());
        curElem = DOMUtils.getNextSiblingElement(curElem);
        if (curElem != null && curElem.getLocalName().equals("J")) {
            j = new DOMCryptoBinary(curElem.getFirstChild());
            curElem = DOMUtils.getNextSiblingElement(curElem);
        }
        if (curElem != null) {
            seed = new DOMCryptoBinary(curElem.getFirstChild());
            curElem = DOMUtils.getNextSiblingElement(curElem);
            pgen = new DOMCryptoBinary(curElem.getFirstChild());
        }
        //@@@ do we care about j, pgenCounter or seed?
        DSAPublicKeySpec spec = new DSAPublicKeySpec
            (y.getBigNum(), p.getBigNum(), q.getBigNum(), g.getBigNum());
        return (DSAPublicKey) generatePublicKey(dsakf, spec);
    }

    private RSAPublicKey unmarshalRSAKeyValue(Element kvtElem)
        throws MarshalException {
        if (rsakf == null) {
            try {
                rsakf = KeyFactory.getInstance("RSA");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("unable to create RSA KeyFactory: " +
                    e.getMessage());
            }
        }
        Element modulusElem = DOMUtils.getFirstChildElement(kvtElem);
        modulus = new DOMCryptoBinary(modulusElem.getFirstChild());
        Element exponentElem = DOMUtils.getNextSiblingElement(modulusElem);
        exponent = new DOMCryptoBinary(exponentElem.getFirstChild());
        RSAPublicKeySpec spec = new RSAPublicKeySpec
            (modulus.getBigNum(), exponent.getBigNum());
        return (RSAPublicKey) generatePublicKey(rsakf, spec);
    }

    private PublicKey generatePublicKey(KeyFactory kf, KeySpec keyspec) {
        try {
            return kf.generatePublic(keyspec);
        } catch (InvalidKeySpecException e) {
            //@@@ should dump exception to log
            return null;
        }
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof KeyValue)) {
            return false;
        }
        try {
            KeyValue kv = (KeyValue) obj;
            if (publicKey == null ) {
                if (kv.getPublicKey() != null) {
                    return false;
                }
            } else if (!publicKey.equals(kv.getPublicKey())) {
                return false;
            }
        } catch (KeyException ke) {
            // no practical way to determine if the keys are equal
            return false;
        }

        return true;
    }
}
