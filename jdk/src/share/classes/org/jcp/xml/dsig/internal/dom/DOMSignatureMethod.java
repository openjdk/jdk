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
 * $Id: DOMSignatureMethod.java 1333415 2012-05-03 12:03:51Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;

import java.io.IOException;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import org.w3c.dom.Element;

import com.sun.org.apache.xml.internal.security.algorithms.implementations.SignatureECDSA;
import org.jcp.xml.dsig.internal.SignerOutputStream;

/**
 * DOM-based abstract implementation of SignatureMethod.
 *
 * @author Sean Mullan
 */
public abstract class DOMSignatureMethod extends AbstractDOMSignatureMethod {

    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger("org.jcp.xml.dsig.internal.dom");

    private SignatureMethodParameterSpec params;
    private Signature signature;

    // see RFC 4051 for these algorithm definitions
    static final String RSA_SHA256 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    static final String RSA_SHA384 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384";
    static final String RSA_SHA512 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512";
    static final String ECDSA_SHA1 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1";
    static final String ECDSA_SHA256 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256";
    static final String ECDSA_SHA384 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384";
    static final String ECDSA_SHA512 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512";

    /**
     * Creates a <code>DOMSignatureMethod</code>.
     *
     * @param params the algorithm-specific params (may be <code>null</code>)
     * @throws InvalidAlgorithmParameterException if the parameters are not
     *    appropriate for this signature method
     */
    DOMSignatureMethod(AlgorithmParameterSpec params)
        throws InvalidAlgorithmParameterException
    {
        if (params != null &&
            !(params instanceof SignatureMethodParameterSpec)) {
            throw new InvalidAlgorithmParameterException
                ("params must be of type SignatureMethodParameterSpec");
        }
        checkParams((SignatureMethodParameterSpec)params);
        this.params = (SignatureMethodParameterSpec)params;
    }

    /**
     * Creates a <code>DOMSignatureMethod</code> from an element. This ctor
     * invokes the {@link #unmarshalParams unmarshalParams} method to
     * unmarshal any algorithm-specific input parameters.
     *
     * @param smElem a SignatureMethod element
     */
    DOMSignatureMethod(Element smElem) throws MarshalException {
        Element paramsElem = DOMUtils.getFirstChildElement(smElem);
        if (paramsElem != null) {
            params = unmarshalParams(paramsElem);
        }
        try {
            checkParams(params);
        } catch (InvalidAlgorithmParameterException iape) {
            throw new MarshalException(iape);
        }
    }

    static SignatureMethod unmarshal(Element smElem) throws MarshalException {
        String alg = DOMUtils.getAttributeValue(smElem, "Algorithm");
        if (alg.equals(SignatureMethod.RSA_SHA1)) {
            return new SHA1withRSA(smElem);
        } else if (alg.equals(RSA_SHA256)) {
            return new SHA256withRSA(smElem);
        } else if (alg.equals(RSA_SHA384)) {
            return new SHA384withRSA(smElem);
        } else if (alg.equals(RSA_SHA512)) {
            return new SHA512withRSA(smElem);
        } else if (alg.equals(SignatureMethod.DSA_SHA1)) {
            return new SHA1withDSA(smElem);
        } else if (alg.equals(ECDSA_SHA1)) {
            return new SHA1withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA256)) {
            return new SHA256withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA384)) {
            return new SHA384withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA512)) {
            return new SHA512withECDSA(smElem);
        } else if (alg.equals(SignatureMethod.HMAC_SHA1)) {
            return new DOMHMACSignatureMethod.SHA1(smElem);
        } else if (alg.equals(DOMHMACSignatureMethod.HMAC_SHA256)) {
            return new DOMHMACSignatureMethod.SHA256(smElem);
        } else if (alg.equals(DOMHMACSignatureMethod.HMAC_SHA384)) {
            return new DOMHMACSignatureMethod.SHA384(smElem);
        } else if (alg.equals(DOMHMACSignatureMethod.HMAC_SHA512)) {
            return new DOMHMACSignatureMethod.SHA512(smElem);
        } else {
            throw new MarshalException
                ("unsupported SignatureMethod algorithm: " + alg);
        }
    }

    public final AlgorithmParameterSpec getParameterSpec() {
        return params;
    }

    boolean verify(Key key, SignedInfo si, byte[] sig,
                   XMLValidateContext context)
        throws InvalidKeyException, SignatureException, XMLSignatureException
    {
        if (key == null || si == null || sig == null) {
            throw new NullPointerException();
        }

        if (!(key instanceof PublicKey)) {
            throw new InvalidKeyException("key must be PublicKey");
        }
        if (signature == null) {
            try {
                Provider p = (Provider)context.getProperty
                    ("org.jcp.xml.dsig.internal.dom.SignatureProvider");
                signature = (p == null)
                    ? Signature.getInstance(getJCAAlgorithm())
                    : Signature.getInstance(getJCAAlgorithm(), p);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initVerify((PublicKey)key);
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Signature provider:" + signature.getProvider());
            log.log(java.util.logging.Level.FINE, "verifying with key: " + key);
        }
        ((DOMSignedInfo)si).canonicalize(context,
                                         new SignerOutputStream(signature));

        try {
            Type type = getAlgorithmType();
            if (type == Type.DSA) {
                return signature.verify(convertXMLDSIGtoASN1(sig));
            } else if (type == Type.ECDSA) {
                return signature.verify(SignatureECDSA.convertXMLDSIGtoASN1(sig));
            } else {
                return signature.verify(sig);
            }
        } catch (IOException ioe) {
            throw new XMLSignatureException(ioe);
        }
    }

    byte[] sign(Key key, SignedInfo si, XMLSignContext context)
        throws InvalidKeyException, XMLSignatureException
    {
        if (key == null || si == null) {
            throw new NullPointerException();
        }

        if (!(key instanceof PrivateKey)) {
            throw new InvalidKeyException("key must be PrivateKey");
        }
        if (signature == null) {
            try {
                Provider p = (Provider)context.getProperty
                    ("org.jcp.xml.dsig.internal.dom.SignatureProvider");
                signature = (p == null)
                    ? Signature.getInstance(getJCAAlgorithm())
                    : Signature.getInstance(getJCAAlgorithm(), p);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initSign((PrivateKey)key);
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Signature provider:" + signature.getProvider());
            log.log(java.util.logging.Level.FINE, "Signing with key: " + key);
        }

        ((DOMSignedInfo)si).canonicalize(context,
                                         new SignerOutputStream(signature));

        try {
            Type type = getAlgorithmType();
            if (type == Type.DSA) {
                return convertASN1toXMLDSIG(signature.sign());
            } else if (type == Type.ECDSA) {
                return SignatureECDSA.convertASN1toXMLDSIG(signature.sign());
            } else {
                return signature.sign();
            }
        } catch (SignatureException se) {
            throw new XMLSignatureException(se);
        } catch (IOException ioe) {
            throw new XMLSignatureException(ioe);
        }
    }

    /**
     * Converts an ASN.1 DSA value to a XML Signature DSA Value.
     *
     * The JAVA JCE DSA Signature algorithm creates ASN.1 encoded (r,s) value
     * pairs; the XML Signature requires the core BigInteger values.
     *
     * @param asn1Bytes
     *
     * @throws IOException
     * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#dsa-sha1">6.4.1 DSA</A>
     */
    private static byte[] convertASN1toXMLDSIG(byte asn1Bytes[])
        throws IOException
    {
        byte rLength = asn1Bytes[3];
        int i;

        for (i = rLength; (i > 0) && (asn1Bytes[(4 + rLength) - i] == 0); i--);

        byte sLength = asn1Bytes[5 + rLength];
        int j;

        for (j = sLength;
            (j > 0) && (asn1Bytes[(6 + rLength + sLength) - j] == 0); j--);

        if ((asn1Bytes[0] != 48) || (asn1Bytes[1] != asn1Bytes.length - 2)
            || (asn1Bytes[2] != 2) || (i > 20)
            || (asn1Bytes[4 + rLength] != 2) || (j > 20)) {
            throw new IOException("Invalid ASN.1 format of DSA signature");
        } else {
            byte xmldsigBytes[] = new byte[40];

            System.arraycopy(asn1Bytes, (4+rLength)-i, xmldsigBytes, 20-i, i);
            System.arraycopy(asn1Bytes, (6+rLength+sLength)-j, xmldsigBytes,
                             40 - j, j);

            return xmldsigBytes;
        }
    }

    /**
     * Converts a XML Signature DSA Value to an ASN.1 DSA value.
     *
     * The JAVA JCE DSA Signature algorithm creates ASN.1 encoded (r,s) value
     * pairs; the XML Signature requires the core BigInteger values.
     *
     * @param xmldsigBytes
     *
     * @throws IOException
     * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#dsa-sha1">6.4.1 DSA</A>
     */
    private static byte[] convertXMLDSIGtoASN1(byte xmldsigBytes[])
        throws IOException
    {
        if (xmldsigBytes.length != 40) {
            throw new IOException("Invalid XMLDSIG format of DSA signature");
        }

        int i;

        for (i = 20; (i > 0) && (xmldsigBytes[20 - i] == 0); i--);

        int j = i;

        if (xmldsigBytes[20 - i] < 0) {
            j += 1;
        }

        int k;

        for (k = 20; (k > 0) && (xmldsigBytes[40 - k] == 0); k--);

        int l = k;

        if (xmldsigBytes[40 - k] < 0) {
            l += 1;
        }

        byte asn1Bytes[] = new byte[6 + j + l];

        asn1Bytes[0] = 48;
        asn1Bytes[1] = (byte)(4 + j + l);
        asn1Bytes[2] = 2;
        asn1Bytes[3] = (byte)j;

        System.arraycopy(xmldsigBytes, 20 - i, asn1Bytes, (4 + j) - i, i);

        asn1Bytes[4 + j] = 2;
        asn1Bytes[5 + j] = (byte) l;

        System.arraycopy(xmldsigBytes, 40 - k, asn1Bytes, (6 + j + l) - k, k);

        return asn1Bytes;
    }

    static final class SHA1withRSA extends DOMSignatureMethod {
        SHA1withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA1withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return SignatureMethod.RSA_SHA1;
        }
        String getJCAAlgorithm() {
            return "SHA1withRSA";
        }
        Type getAlgorithmType() {
            return Type.RSA;
        }
    }

    static final class SHA256withRSA extends DOMSignatureMethod {
        SHA256withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA256withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return RSA_SHA256;
        }
        String getJCAAlgorithm() {
            return "SHA256withRSA";
        }
        Type getAlgorithmType() {
            return Type.RSA;
        }
    }

    static final class SHA384withRSA extends DOMSignatureMethod {
        SHA384withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA384withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return RSA_SHA384;
        }
        String getJCAAlgorithm() {
            return "SHA384withRSA";
        }
        Type getAlgorithmType() {
            return Type.RSA;
        }
    }

    static final class SHA512withRSA extends DOMSignatureMethod {
        SHA512withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA512withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return RSA_SHA512;
        }
        String getJCAAlgorithm() {
            return "SHA512withRSA";
        }
        Type getAlgorithmType() {
            return Type.RSA;
        }
    }

    static final class SHA1withDSA extends DOMSignatureMethod {
        SHA1withDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA1withDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return SignatureMethod.DSA_SHA1;
        }
        String getJCAAlgorithm() {
            return "SHA1withDSA";
        }
        Type getAlgorithmType() {
            return Type.DSA;
        }
    }

    static final class SHA1withECDSA extends DOMSignatureMethod {
        SHA1withECDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA1withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return ECDSA_SHA1;
        }
        String getJCAAlgorithm() {
            return "SHA1withECDSA";
        }
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }

    static final class SHA256withECDSA extends DOMSignatureMethod {
        SHA256withECDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA256withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return ECDSA_SHA256;
        }
        String getJCAAlgorithm() {
            return "SHA256withECDSA";
        }
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }

    static final class SHA384withECDSA extends DOMSignatureMethod {
        SHA384withECDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA384withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return ECDSA_SHA384;
        }
        String getJCAAlgorithm() {
            return "SHA384withECDSA";
        }
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }

    static final class SHA512withECDSA extends DOMSignatureMethod {
        SHA512withECDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA512withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return ECDSA_SHA512;
        }
        String getJCAAlgorithm() {
            return "SHA512withECDSA";
        }
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }
}
