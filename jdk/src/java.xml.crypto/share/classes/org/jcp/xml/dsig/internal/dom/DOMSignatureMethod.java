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
 * $Id: DOMSignatureMethod.java 1333415 2012-05-03 12:03:51Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;

import java.io.IOException;
import java.security.*;
import java.security.interfaces.DSAKey;
import java.security.spec.AlgorithmParameterSpec;
import org.w3c.dom.Element;

import com.sun.org.apache.xml.internal.security.algorithms.implementations.SignatureECDSA;
import com.sun.org.apache.xml.internal.security.utils.JavaUtils;
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
    static final String DSA_SHA256 =
        "http://www.w3.org/2009/xmldsig11#dsa-sha256";

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

    /**
     * Returns the signature bytes with any additional formatting
     * necessary for the signature algorithm used. For RSA signatures,
     * no changes are required, and this method should simply return
     * back {@code sig}. For DSA and ECDSA, this method should return the
     * signature in the IEEE P1363 format, the concatenation of r and s.
     *
     * @param key the key used to sign
     * @param sig the signature returned by {@code Signature.sign()}
     * @return the formatted signature
     * @throws IOException
     */
    abstract byte[] postSignFormat(Key key, byte[] sig) throws IOException;

    /**
     * Returns the signature bytes with any conversions that are necessary
     * before the signature can be verified. For RSA signatures,
     * no changes are required, and this method should simply
     * return back {@code sig}. For DSA and ECDSA, this method should
     * return the signature in the DER-encoded ASN.1 format.
     *
     * @param key the key used to sign
     * @param sig the signature
     * @return the formatted signature
     * @throws IOException
     */
    abstract byte[] preVerifyFormat(Key key, byte[] sig) throws IOException;

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
        } else if (alg.equals(DSA_SHA256)) {
            return new SHA256withDSA(smElem);
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

    /**
     * Returns an instance of Signature from the specified Provider.
     * The algorithm is specified by the {@code getJCAAlgorithm()} method.
     *
     * @param p the Provider to use
     * @return an instance of Signature implementing the algorithm
     *    specified by {@code getJCAAlgorithm()}
     * @throws NoSuchAlgorithmException if the Provider does not support the
     *    signature algorithm
     */
    Signature getSignature(Provider p)
            throws NoSuchAlgorithmException {
        return (p == null)
            ? Signature.getInstance(getJCAAlgorithm())
            : Signature.getInstance(getJCAAlgorithm(), p);
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
            Provider p = (Provider)context.getProperty(
                    "org.jcp.xml.dsig.internal.dom.SignatureProvider");
            try {
                signature = getSignature(p);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initVerify((PublicKey)key);
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE,
                    "Signature provider:" + signature.getProvider());
            log.log(java.util.logging.Level.FINE, "verifying with key: " + key);
        }
        ((DOMSignedInfo)si).canonicalize(context,
                                         new SignerOutputStream(signature));
        byte[] s;
        try {
            // Do any necessary format conversions
            s = preVerifyFormat(key, sig);
        } catch (IOException ioe) {
            throw new XMLSignatureException(ioe);
        }
        return signature.verify(s);
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
            Provider p = (Provider)context.getProperty(
                    "org.jcp.xml.dsig.internal.dom.SignatureProvider");
            try {
                signature = getSignature(p);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initSign((PrivateKey)key);
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE,
                    "Signature provider:" + signature.getProvider());
            log.log(java.util.logging.Level.FINE, "Signing with key: " + key);
        }

        ((DOMSignedInfo)si).canonicalize(context,
                                         new SignerOutputStream(signature));

        try {
            // Return signature with any necessary format conversions
            return postSignFormat(key, signature.sign());
        } catch (SignatureException | IOException ex){
            throw new XMLSignatureException(ex);
        }
    }

    abstract static class AbstractRSASignatureMethod
            extends DOMSignatureMethod {

        AbstractRSASignatureMethod(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }

        AbstractRSASignatureMethod(Element dmElem) throws MarshalException {
            super(dmElem);
        }

        /**
         * Returns {@code sig}. No extra formatting is necessary for RSA.
         */
        @Override
        byte[] postSignFormat(Key key, byte[] sig) {
            return sig;
        }

        /**
         * Returns {@code sig}. No extra formatting is necessary for RSA.
         */
        @Override
        byte[] preVerifyFormat(Key key, byte[] sig) {
            return sig;
        }
    }

    /**
     * Abstract class to support signature algorithms that sign and verify
     * signatures in the IEEE P1363 format. The P1363 format is the
     * concatenation of r and s in DSA and ECDSA signatures, and thus, only
     * DSA and ECDSA signature methods should extend this class. Subclasses
     * must supply a fallback algorithm to be used when the provider does
     * not offer signature algorithms that use the P1363 format.
     */
    abstract static class AbstractP1363FormatSignatureMethod
            extends DOMSignatureMethod {

        /* Set to true when the fallback algorithm is used */
        boolean asn1;

        AbstractP1363FormatSignatureMethod(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }

        AbstractP1363FormatSignatureMethod(Element dmElem)
                throws MarshalException {
            super(dmElem);
        }

        /**
         * Return the fallback algorithm to be used when the provider does not
         * support signatures in the IEEE P1363 format. This algorithm should
         * return signatures in the DER-encoded ASN.1 format.
         */
        abstract String getJCAFallbackAlgorithm();

        /*
         * Try to return an instance of Signature implementing signatures
         * in the IEEE P1363 format. If the provider doesn't support the
         * P1363 format, return an instance of Signature implementing
         * signatures in the DER-encoded ASN.1 format.
         */
        @Override
        Signature getSignature(Provider p)
                throws NoSuchAlgorithmException {
            try {
                return (p == null)
                    ? Signature.getInstance(getJCAAlgorithm())
                    : Signature.getInstance(getJCAAlgorithm(), p);
            } catch (NoSuchAlgorithmException nsae) {
                Signature s = (p == null)
                    ? Signature.getInstance(getJCAFallbackAlgorithm())
                    : Signature.getInstance(getJCAFallbackAlgorithm(), p);
                asn1 = true;
                return s;
            }
        }
    }

    abstract static class AbstractDSASignatureMethod
        extends AbstractP1363FormatSignatureMethod {

        AbstractDSASignatureMethod(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }

        AbstractDSASignatureMethod(Element dmElem) throws MarshalException {
            super(dmElem);
        }

        @Override
        byte[] postSignFormat(Key key, byte[] sig) throws IOException {
            // If signature is in ASN.1 (i.e., if the fallback algorithm
            // was used), convert the signature to the P1363 format
            if (asn1) {
                int size = ((DSAKey) key).getParams().getQ().bitLength();
                return JavaUtils.convertDsaASN1toXMLDSIG(sig, size / 8);
            } else {
                return sig;
            }
        }

        @Override
        byte[] preVerifyFormat(Key key, byte[] sig) throws IOException {
            // If signature needs to be in ASN.1 (i.e., if the fallback
            // algorithm will be used to verify the sig), convert the signature
            // to the ASN.1 format
            if (asn1) {
                int size = ((DSAKey) key).getParams().getQ().bitLength();
                return JavaUtils.convertDsaXMLDSIGtoASN1(sig, size / 8);
            } else {
                return sig;
            }
        }
    }

    abstract static class AbstractECDSASignatureMethod
        extends AbstractP1363FormatSignatureMethod {

        AbstractECDSASignatureMethod(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }

        AbstractECDSASignatureMethod(Element dmElem) throws MarshalException {
            super(dmElem);
        }

        @Override
        byte[] postSignFormat(Key key, byte[] sig) throws IOException {
            // If signature is in ASN.1 (i.e., if the fallback algorithm
            // was used), convert the signature to the P1363 format
            if (asn1) {
                return SignatureECDSA.convertASN1toXMLDSIG(sig);
            } else {
                return sig;
            }
        }

        @Override
        byte[] preVerifyFormat(Key key, byte[] sig) throws IOException {
            // If signature needs to be in ASN.1 (i.e., if the fallback
            // algorithm will be used to verify the sig), convert the signature
            // to the ASN.1 format
            if (asn1) {
                return SignatureECDSA.convertXMLDSIGtoASN1(sig);
            } else {
                return sig;
            }
        }
    }

    static final class SHA1withRSA extends AbstractRSASignatureMethod {
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

    static final class SHA256withRSA extends AbstractRSASignatureMethod {
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

    static final class SHA384withRSA extends AbstractRSASignatureMethod {
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

    static final class SHA512withRSA extends AbstractRSASignatureMethod {
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

    static final class SHA1withDSA extends AbstractDSASignatureMethod {
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
            return "SHA1withDSAinP1363Format";
        }
        String getJCAFallbackAlgorithm() {
            return "SHA1withDSA";
        }
        Type getAlgorithmType() {
            return Type.DSA;
        }
    }

    static final class SHA256withDSA extends AbstractDSASignatureMethod {
        SHA256withDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA256withDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return DSA_SHA256;
        }
        String getJCAAlgorithm() {
            return "SHA256withDSAinP1363Format";
        }
        String getJCAFallbackAlgorithm() {
            return "SHA256withDSA";
        }
        Type getAlgorithmType() {
            return Type.DSA;
        }
    }

    static final class SHA1withECDSA extends AbstractECDSASignatureMethod {
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
            return "SHA1withECDSAinP1363Format";
        }
        String getJCAFallbackAlgorithm() {
            return "SHA1withECDSA";
        }
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }

    static final class SHA256withECDSA extends AbstractECDSASignatureMethod {
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
            return "SHA256withECDSAinP1363Format";
        }
        String getJCAFallbackAlgorithm() {
            return "SHA256withECDSA";
        }
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }

    static final class SHA384withECDSA extends AbstractECDSASignatureMethod {
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
            return "SHA384withECDSAinP1363Format";
        }
        String getJCAFallbackAlgorithm() {
            return "SHA384withECDSA";
        }
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }

    static final class SHA512withECDSA extends AbstractECDSASignatureMethod {
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
            return "SHA512withECDSAinP1363Format";
        }
        String getJCAFallbackAlgorithm() {
            return "SHA512withECDSA";
        }
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }
}
