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
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMKeyValue.java 1333415 2012-05-03 12:03:51Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.keyinfo.KeyValue;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECField;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

/**
 * DOM-based implementation of KeyValue.
 *
 * @author Sean Mullan
 */
public abstract class DOMKeyValue extends DOMStructure implements KeyValue {

    private static final String XMLDSIG_11_XMLNS
        = "http://www.w3.org/2009/xmldsig11#";
    private final PublicKey publicKey;

    public DOMKeyValue(PublicKey key) throws KeyException {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        this.publicKey = key;
    }

    /**
     * Creates a <code>DOMKeyValue</code> from an element.
     *
     * @param kvtElem a KeyValue child element
     */
    public DOMKeyValue(Element kvtElem) throws MarshalException {
        this.publicKey = unmarshalKeyValue(kvtElem);
    }

    static KeyValue unmarshal(Element kvElem) throws MarshalException {
        Element kvtElem = DOMUtils.getFirstChildElement(kvElem);
        if (kvtElem.getLocalName().equals("DSAKeyValue")) {
            return new DSA(kvtElem);
        } else if (kvtElem.getLocalName().equals("RSAKeyValue")) {
            return new RSA(kvtElem);
        } else if (kvtElem.getLocalName().equals("ECKeyValue")) {
            return new EC(kvtElem);
        } else {
            return new Unknown(kvtElem);
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
        throws MarshalException
    {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        // create KeyValue element
        Element kvElem = DOMUtils.createElement(ownerDoc, "KeyValue",
                                                XMLSignature.XMLNS, dsPrefix);
        marshalPublicKey(kvElem, ownerDoc, dsPrefix, context);

        parent.appendChild(kvElem);
    }

    abstract void marshalPublicKey(Node parent, Document doc, String dsPrefix,
        DOMCryptoContext context) throws MarshalException;

    abstract PublicKey unmarshalKeyValue(Element kvtElem)
        throws MarshalException;

    private static PublicKey generatePublicKey(KeyFactory kf, KeySpec keyspec) {
        try {
            return kf.generatePublic(keyspec);
        } catch (InvalidKeySpecException e) {
            //@@@ should dump exception to log
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof KeyValue)) {
            return false;
        }
        try {
            KeyValue kv = (KeyValue)obj;
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

    @Override
    public int hashCode() {
        int result = 17;
        if (publicKey != null) {
            result = 31 * result + publicKey.hashCode();
        }

        return result;
    }

    static final class RSA extends DOMKeyValue {
        // RSAKeyValue CryptoBinaries
        private DOMCryptoBinary modulus, exponent;
        private KeyFactory rsakf;

        RSA(PublicKey key) throws KeyException {
            super(key);
            RSAPublicKey rkey = (RSAPublicKey)key;
            exponent = new DOMCryptoBinary(rkey.getPublicExponent());
            modulus = new DOMCryptoBinary(rkey.getModulus());
        }

        RSA(Element elem) throws MarshalException {
            super(elem);
        }

        void marshalPublicKey(Node parent, Document doc, String dsPrefix,
            DOMCryptoContext context) throws MarshalException {
            Element rsaElem = DOMUtils.createElement(doc, "RSAKeyValue",
                                                     XMLSignature.XMLNS,
                                                     dsPrefix);
            Element modulusElem = DOMUtils.createElement(doc, "Modulus",
                                                         XMLSignature.XMLNS,
                                                         dsPrefix);
            Element exponentElem = DOMUtils.createElement(doc, "Exponent",
                                                          XMLSignature.XMLNS,
                                                          dsPrefix);
            modulus.marshal(modulusElem, dsPrefix, context);
            exponent.marshal(exponentElem, dsPrefix, context);
            rsaElem.appendChild(modulusElem);
            rsaElem.appendChild(exponentElem);
            parent.appendChild(rsaElem);
        }

        PublicKey unmarshalKeyValue(Element kvtElem)
            throws MarshalException
        {
            if (rsakf == null) {
                try {
                    rsakf = KeyFactory.getInstance("RSA");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException
                        ("unable to create RSA KeyFactory: " + e.getMessage());
                }
            }
            Element modulusElem = DOMUtils.getFirstChildElement(kvtElem,
                                                                "Modulus");
            modulus = new DOMCryptoBinary(modulusElem.getFirstChild());
            Element exponentElem = DOMUtils.getNextSiblingElement(modulusElem,
                                                                  "Exponent");
            exponent = new DOMCryptoBinary(exponentElem.getFirstChild());
            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus.getBigNum(),
                                                         exponent.getBigNum());
            return generatePublicKey(rsakf, spec);
        }
    }

    static final class DSA extends DOMKeyValue {
        // DSAKeyValue CryptoBinaries
        private DOMCryptoBinary p, q, g, y, j; //, seed, pgen;
        private KeyFactory dsakf;

        DSA(PublicKey key) throws KeyException {
            super(key);
            DSAPublicKey dkey = (DSAPublicKey) key;
            DSAParams params = dkey.getParams();
            p = new DOMCryptoBinary(params.getP());
            q = new DOMCryptoBinary(params.getQ());
            g = new DOMCryptoBinary(params.getG());
            y = new DOMCryptoBinary(dkey.getY());
        }

        DSA(Element elem) throws MarshalException {
            super(elem);
        }

        void marshalPublicKey(Node parent, Document doc, String dsPrefix,
                              DOMCryptoContext context)
            throws MarshalException
        {
            Element dsaElem = DOMUtils.createElement(doc, "DSAKeyValue",
                                                     XMLSignature.XMLNS,
                                                     dsPrefix);
            // parameters J, Seed & PgenCounter are not included
            Element pElem = DOMUtils.createElement(doc, "P", XMLSignature.XMLNS,
                                                   dsPrefix);
            Element qElem = DOMUtils.createElement(doc, "Q", XMLSignature.XMLNS,
                                                   dsPrefix);
            Element gElem = DOMUtils.createElement(doc, "G", XMLSignature.XMLNS,
                                                   dsPrefix);
            Element yElem = DOMUtils.createElement(doc, "Y", XMLSignature.XMLNS,
                                                   dsPrefix);
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

        PublicKey unmarshalKeyValue(Element kvtElem)
            throws MarshalException
        {
            if (dsakf == null) {
                try {
                    dsakf = KeyFactory.getInstance("DSA");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException
                        ("unable to create DSA KeyFactory: " + e.getMessage());
                }
            }
            Element curElem = DOMUtils.getFirstChildElement(kvtElem);
            // check for P and Q
            if (curElem.getLocalName().equals("P")) {
                p = new DOMCryptoBinary(curElem.getFirstChild());
                curElem = DOMUtils.getNextSiblingElement(curElem, "Q");
                q = new DOMCryptoBinary(curElem.getFirstChild());
                curElem = DOMUtils.getNextSiblingElement(curElem);
            }
            if (curElem.getLocalName().equals("G")) {
                g = new DOMCryptoBinary(curElem.getFirstChild());
                curElem = DOMUtils.getNextSiblingElement(curElem, "Y");
            }
            y = new DOMCryptoBinary(curElem.getFirstChild());
            curElem = DOMUtils.getNextSiblingElement(curElem);
            if (curElem != null && curElem.getLocalName().equals("J")) {
                j = new DOMCryptoBinary(curElem.getFirstChild());
                // curElem = DOMUtils.getNextSiblingElement(curElem);
            }
            /*
            if (curElem != null) {
                seed = new DOMCryptoBinary(curElem.getFirstChild());
                curElem = DOMUtils.getNextSiblingElement(curElem);
                pgen = new DOMCryptoBinary(curElem.getFirstChild());
            }
            */
            //@@@ do we care about j, pgenCounter or seed?
            DSAPublicKeySpec spec = new DSAPublicKeySpec(y.getBigNum(),
                                                         p.getBigNum(),
                                                         q.getBigNum(),
                                                         g.getBigNum());
            return generatePublicKey(dsakf, spec);
        }
    }

    static final class EC extends DOMKeyValue {
        // ECKeyValue CryptoBinaries
        private byte[] ecPublicKey;
        private KeyFactory eckf;
        private ECParameterSpec ecParams;

        // The supported curve, secp256r1
        private static final Curve SECP256R1;
        static {
            final String name, oid, sfield, a, b, x, y, n;
            name = "secp256r1 [NIST P-256, X9.62 prime256v1]";
            oid  = "1.2.840.10045.3.1.7";
            sfield =
             "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF";
            a =
             "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC";
            b =
             "5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B";
            x =
             "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296";
            y =
             "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5";
            n =
             "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551";
            final int h = 1;

            BigInteger p = bigInt(sfield);
            ECField field = new ECFieldFp(p);
            EllipticCurve curve = new EllipticCurve(field, bigInt(a),
                                                    bigInt(b));
            ECPoint g = new ECPoint(bigInt(x), bigInt(y));
            SECP256R1 = new Curve(name, oid, curve, g, bigInt(n), h);
        }

        EC(PublicKey key) throws KeyException {
            super(key);
            ECPublicKey ecKey = (ECPublicKey)key;
            ECPoint ecPoint = ecKey.getW();
            ecParams = ecKey.getParams();
            ecPublicKey = encodePoint(ecPoint, ecParams.getCurve());
        }

        EC(Element dmElem) throws MarshalException {
            super(dmElem);
        }

        private static ECPoint decodePoint(byte[] data, EllipticCurve curve)
                throws IOException {
            if ((data.length == 0) || (data[0] != 4)) {
                throw new IOException("Only uncompressed point format " +
                                      "supported");
            }
            // Per ANSI X9.62, an encoded point is a 1 byte type followed by
            // ceiling(log base 2 field-size / 8) bytes of x and the same of y.
            int n = (data.length - 1) / 2;
            if (n != ((curve.getField().getFieldSize() + 7) >> 3)) {
                throw new IOException("Point does not match field size");
            }

            byte[] xb = Arrays.copyOfRange(data, 1, 1 + n);
            byte[] yb = Arrays.copyOfRange(data, n + 1, n + 1 + n);

            return new ECPoint(new BigInteger(1, xb), new BigInteger(1, yb));
        }

        private static byte[] encodePoint(ECPoint point, EllipticCurve curve) {
            // get field size in bytes (rounding up)
            int n = (curve.getField().getFieldSize() + 7) >> 3;
            byte[] xb = trimZeroes(point.getAffineX().toByteArray());
            byte[] yb = trimZeroes(point.getAffineY().toByteArray());
            if ((xb.length > n) || (yb.length > n)) {
                throw new RuntimeException("Point coordinates do not " +
                                           "match field size");
            }
            byte[] b = new byte[1 + (n << 1)];
            b[0] = 4; // uncompressed
            System.arraycopy(xb, 0, b, n - xb.length + 1, xb.length);
            System.arraycopy(yb, 0, b, b.length - yb.length, yb.length);
            return b;
        }

        private static byte[] trimZeroes(byte[] b) {
            int i = 0;
            while ((i < b.length - 1) && (b[i] == 0)) {
                i++;
            }
            if (i == 0) {
                return b;
            }
            return Arrays.copyOfRange(b, i, b.length);
        }

        private static String getCurveOid(ECParameterSpec params) {
            // Check that the params represent the secp256r1 curve
            // If so, return the object identifier of the curve
            int fieldSize = params.getCurve().getField().getFieldSize();
            if (SECP256R1.getCurve().getField().getFieldSize() == fieldSize
                && SECP256R1.getCurve().equals(params.getCurve())
                && SECP256R1.getGenerator().equals(params.getGenerator())
                && SECP256R1.getOrder().equals(params.getOrder())
                && SECP256R1.getCofactor() == params.getCofactor()) {
                return SECP256R1.getObjectId();
            }
            return null;
        }

        private static ECParameterSpec getECParameterSpec(String oid) {
            if (oid.equals(SECP256R1.getObjectId())) {
                return SECP256R1;
            }
            return null;
        }

        void marshalPublicKey(Node parent, Document doc, String dsPrefix,
                              DOMCryptoContext context)
            throws MarshalException
        {
            String prefix = DOMUtils.getNSPrefix(context, XMLDSIG_11_XMLNS);
            Element ecKeyValueElem = DOMUtils.createElement(doc, "ECKeyValue",
                                                            XMLDSIG_11_XMLNS,
                                                            prefix);
            Element namedCurveElem = DOMUtils.createElement(doc, "NamedCurve",
                                                            XMLDSIG_11_XMLNS,
                                                            prefix);
            Element publicKeyElem = DOMUtils.createElement(doc, "PublicKey",
                                                           XMLDSIG_11_XMLNS,
                                                           prefix);
            Object[] args = new Object[] { ecParams };
            String oid = getCurveOid(ecParams);
            if (oid == null) {
                throw new MarshalException("Invalid ECParameterSpec");
            }
            DOMUtils.setAttribute(namedCurveElem, "URI", "urn:oid:" + oid);
            String qname = (prefix == null || prefix.length() == 0)
                       ? "xmlns" : "xmlns:" + prefix;
            namedCurveElem.setAttributeNS("http://www.w3.org/2000/xmlns/",
                                          qname, XMLDSIG_11_XMLNS);
            ecKeyValueElem.appendChild(namedCurveElem);
            String encoded = Base64.encode(ecPublicKey);
            publicKeyElem.appendChild
                (DOMUtils.getOwnerDocument(publicKeyElem).createTextNode(encoded));
            ecKeyValueElem.appendChild(publicKeyElem);
            parent.appendChild(ecKeyValueElem);
        }

        PublicKey unmarshalKeyValue(Element kvtElem)
            throws MarshalException
        {
            if (eckf == null) {
                try {
                    eckf = KeyFactory.getInstance("EC");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException
                        ("unable to create EC KeyFactory: " + e.getMessage());
                }
            }
            ECParameterSpec ecParams = null;
            Element curElem = DOMUtils.getFirstChildElement(kvtElem);
            if (curElem.getLocalName().equals("ECParameters")) {
                throw new UnsupportedOperationException
                    ("ECParameters not supported");
            } else if (curElem.getLocalName().equals("NamedCurve")) {
                String uri = DOMUtils.getAttributeValue(curElem, "URI");
                // strip off "urn:oid"
                if (uri.startsWith("urn:oid:")) {
                    String oid = uri.substring(8);
                    ecParams = getECParameterSpec(oid);
                    if (ecParams == null) {
                        throw new MarshalException("Invalid curve OID");
                    }
                } else {
                    throw new MarshalException("Invalid NamedCurve URI");
                }
            } else {
                throw new MarshalException("Invalid ECKeyValue");
            }
            curElem = DOMUtils.getNextSiblingElement(curElem, "PublicKey");
            ECPoint ecPoint = null;

            try {
                ecPoint = decodePoint(Base64.decode(curElem),
                                      ecParams.getCurve());
            } catch (Base64DecodingException bde) {
                throw new MarshalException("Invalid EC PublicKey", bde);
            } catch (IOException ioe) {
                throw new MarshalException("Invalid EC Point", ioe);
            }

            ECPublicKeySpec spec = new ECPublicKeySpec(ecPoint, ecParams);
            return generatePublicKey(eckf, spec);
        }

        static final class Curve extends ECParameterSpec {
            private final String name;
            private final String oid;

            Curve(String name, String oid, EllipticCurve curve,
                  ECPoint g, BigInteger n, int h) {
                super(curve, g, n, h);
                this.name = name;
                this.oid = oid;
            }

            private String getName() {
                return name;
            }

            private String getObjectId() {
                return oid;
            }
        }
    }

    private static BigInteger bigInt(String s) {
        return new BigInteger(s, 16);
    }

    static final class Unknown extends DOMKeyValue {
        private javax.xml.crypto.dom.DOMStructure externalPublicKey;
        Unknown(Element elem) throws MarshalException {
            super(elem);
        }
        PublicKey unmarshalKeyValue(Element kvElem) throws MarshalException {
            externalPublicKey = new javax.xml.crypto.dom.DOMStructure(kvElem);
            return null;
        }
        void marshalPublicKey(Node parent, Document doc, String dsPrefix,
                              DOMCryptoContext context)
            throws MarshalException
        {
            parent.appendChild(externalPublicKey.getNode());
        }
    }
}
