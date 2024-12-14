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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.jcp.xml.dsig.internal.dom;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.DSAKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.XMLSignContext;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLValidateContext;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;

import org.jcp.xml.dsig.internal.SignerOutputStream;
import com.sun.org.apache.xml.internal.security.algorithms.implementations.SignatureECDSA;
import com.sun.org.apache.xml.internal.security.utils.JavaUtils;
import org.w3c.dom.Element;

import sun.security.util.KeyUtil;

/**
 * DOM-based abstract implementation of SignatureMethod.
 *
 */
public abstract class DOMSignatureMethod extends AbstractDOMSignatureMethod {

    private static final String DOM_SIGNATURE_PROVIDER = "org.jcp.xml.dsig.internal.dom.SignatureProvider";

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(DOMSignatureMethod.class);

    private SignatureMethodParameterSpec params;
    private Signature signature;

    // see RFC 4051 for these algorithm definitions
    static final String RSA_SHA224 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha224";
    static final String RSA_SHA256 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    static final String RSA_SHA384 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384";
    static final String RSA_SHA512 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512";
    static final String RSA_RIPEMD160 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-ripemd160";
    static final String ECDSA_SHA1 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha1";
    static final String ECDSA_SHA224 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha224";
    static final String ECDSA_SHA256 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256";
    static final String ECDSA_SHA384 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384";
    static final String ECDSA_SHA512 =
        "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512";
    static final String DSA_SHA256 =
        "http://www.w3.org/2009/xmldsig11#dsa-sha256";

    // see RFC 9231 for these algorithm definitions
    static final String ED25519 =
        "http://www.w3.org/2021/04/xmldsig-more#eddsa-ed25519";
    static final String ED448 =
        "http://www.w3.org/2021/04/xmldsig-more#eddsa-ed448";
    static final String ECDSA_SHA3_224 =
        "http://www.w3.org/2021/04/xmldsig-more#ecdsa-sha3-224";
    static final String ECDSA_SHA3_256 =
        "http://www.w3.org/2021/04/xmldsig-more#ecdsa-sha3-256";
    static final String ECDSA_SHA3_384 =
        "http://www.w3.org/2021/04/xmldsig-more#ecdsa-sha3-384";
    static final String ECDSA_SHA3_512 =
        "http://www.w3.org/2021/04/xmldsig-more#ecdsa-sha3-512";

    // see RFC 6931 for these algorithm definitions
    static final String ECDSA_RIPEMD160 =
        "http://www.w3.org/2007/05/xmldsig-more#ecdsa-ripemd160";
    static final String RSA_SHA1_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha1-rsa-MGF1";
    static final String RSA_SHA224_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha224-rsa-MGF1";
    static final String RSA_SHA256_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1";
    static final String RSA_SHA384_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha384-rsa-MGF1";
    static final String RSA_SHA512_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha512-rsa-MGF1";
    static final String RSA_RIPEMD160_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#ripemd160-rsa-MGF1";
    static final String RSA_SHA3_224_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha3-224-rsa-MGF1";
    static final String RSA_SHA3_256_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha3-256-rsa-MGF1";
    static final String RSA_SHA3_384_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha3-384-rsa-MGF1";
    static final String RSA_SHA3_512_MGF1 =
        "http://www.w3.org/2007/05/xmldsig-more#sha3-512-rsa-MGF1";

    /**
     * Creates a {@code DOMSignatureMethod}.
     *
     * @param params the algorithm-specific params (may be {@code null})
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
     * Creates a {@code DOMSignatureMethod} from an element. This ctor
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
        } else if (alg.equals(RSA_SHA224)) {
            return new SHA224withRSA(smElem);
        } else if (alg.equals(RSA_SHA256)) {
            return new SHA256withRSA(smElem);
        } else if (alg.equals(RSA_SHA384)) {
            return new SHA384withRSA(smElem);
        } else if (alg.equals(RSA_SHA512)) {
            return new SHA512withRSA(smElem);
        } else if (alg.equals(RSA_RIPEMD160)) {
            return new RIPEMD160withRSA(smElem);
        } else if (alg.equals(RSA_SHA1_MGF1)) {
            return new SHA1withRSAandMGF1(smElem);
        } else if (alg.equals(RSA_SHA224_MGF1)) {
            return new SHA224withRSAandMGF1(smElem);
        } else if (alg.equals(RSA_SHA256_MGF1)) {
            return new SHA256withRSAandMGF1(smElem);
        } else if (alg.equals(RSA_SHA384_MGF1)) {
            return new SHA384withRSAandMGF1(smElem);
        } else if (alg.equals(RSA_SHA512_MGF1)) {
            return new SHA512withRSAandMGF1(smElem);
        } else if (alg.equals(RSA_SHA3_224_MGF1)) {
            return new SHA3_224withRSAandMGF1(smElem);
        } else if (alg.equals(RSA_SHA3_256_MGF1)) {
            return new SHA3_256withRSAandMGF1(smElem);
        } else if (alg.equals(RSA_SHA3_384_MGF1)) {
            return new SHA3_384withRSAandMGF1(smElem);
        } else if (alg.equals(RSA_SHA3_512_MGF1)) {
            return new SHA3_512withRSAandMGF1(smElem);
        } else if (alg.equals(DOMRSAPSSSignatureMethod.RSA_PSS)) {
            return new DOMRSAPSSSignatureMethod.RSAPSS(smElem);
        } else if (alg.equals(RSA_RIPEMD160_MGF1)) {
            return new RIPEMD160withRSAandMGF1(smElem);
        } else if (alg.equals(SignatureMethod.DSA_SHA1)) {
            return new SHA1withDSA(smElem);
        } else if (alg.equals(DSA_SHA256)) {
            return new SHA256withDSA(smElem);
        } else if (alg.equals(ECDSA_SHA1)) {
            return new SHA1withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA224)) {
            return new SHA224withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA256)) {
            return new SHA256withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA384)) {
            return new SHA384withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA512)) {
            return new SHA512withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA3_224)) {
            return new SHA3_224withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA3_256)) {
            return new SHA3_256withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA3_384)) {
            return new SHA3_384withECDSA(smElem);
        } else if (alg.equals(ECDSA_SHA3_512)) {
            return new SHA3_512withECDSA(smElem);
        } else if (alg.equals(ECDSA_RIPEMD160)) {
            return new RIPEMD160withECDSA(smElem);
        } else if (alg.equals(SignatureMethod.HMAC_SHA1)) {
            return new DOMHMACSignatureMethod.SHA1(smElem);
        } else if (alg.equals(DOMHMACSignatureMethod.HMAC_SHA224)) {
            return new DOMHMACSignatureMethod.SHA224(smElem);
        } else if (alg.equals(DOMHMACSignatureMethod.HMAC_SHA256)) {
            return new DOMHMACSignatureMethod.SHA256(smElem);
        } else if (alg.equals(DOMHMACSignatureMethod.HMAC_SHA384)) {
            return new DOMHMACSignatureMethod.SHA384(smElem);
        } else if (alg.equals(DOMHMACSignatureMethod.HMAC_SHA512)) {
            return new DOMHMACSignatureMethod.SHA512(smElem);
        } else if (alg.equals(DOMHMACSignatureMethod.HMAC_RIPEMD160)) {
            return new DOMHMACSignatureMethod.RIPEMD160(smElem);
        } else if (alg.equals(ED25519)) {
            return new EDDSA_ED25519(smElem);
        } else if (alg.equals(ED448)) {
            return new EDDSA_ED448(smElem);
        } else {
            throw new MarshalException
                ("unsupported SignatureMethod algorithm: " + alg);
        }
    }

    @Override
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

    @Override
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
        checkKeySize(context, key);
        if (signature == null) {
            Provider p = (Provider)context.getProperty(DOM_SIGNATURE_PROVIDER);
            try {
                signature = getSignature(p);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initVerify((PublicKey)key);
        LOG.debug("Signature provider: {}", signature.getProvider());
        LOG.debug("Verifying with key: {}", key);
        LOG.debug("JCA Algorithm: {}", getJCAAlgorithm());
        LOG.debug("Signature Bytes length: {}", sig.length);

        byte[] s;
        try (SignerOutputStream outputStream = new SignerOutputStream(signature)) {
            ((DOMSignedInfo)si).canonicalize(context, outputStream);
            // Do any necessary format conversions
            s = preVerifyFormat(key, sig);
        } catch (IOException ioe) {
            throw new XMLSignatureException(ioe);
        }
        return signature.verify(s);
    }

    /**
     * If secure validation mode is enabled, checks that the key size is
     * restricted.
     *
     * @param context the context
     * @param key the key to check
     * @throws XMLSignatureException if the key size is restricted
     */
    private static void checkKeySize(XMLCryptoContext context, Key key)
        throws XMLSignatureException {
        if (Utils.secureValidation(context)) {
            int size = KeyUtil.getKeySize(key);
            if (size == -1) {
                // key size cannot be determined, so we cannot check against
                // restrictions. Note that a DSA key w/o params will be
                // rejected later if the certificate chain is validated.
                LOG.debug("Size for " +
                            key.getAlgorithm() + " key cannot be determined");
                return;
            }
            if (Policy.restrictKey(key.getAlgorithm(), size)) {
                throw new XMLSignatureException(key.getAlgorithm() +
                    " keys less than " +
                    Policy.minKeySize(key.getAlgorithm()) + " bits are" +
                    " forbidden when secure validation is enabled");
            }
        }
    }

    @Override
    byte[] sign(Key key, SignedInfo si, XMLSignContext context)
        throws InvalidKeyException, XMLSignatureException
    {
        if (key == null || si == null) {
            throw new NullPointerException();
        }

        if (!(key instanceof PrivateKey)) {
            throw new InvalidKeyException("key must be PrivateKey");
        }
        checkKeySize(context, key);
        if (signature == null) {
            Provider p = (Provider)context.getProperty(DOM_SIGNATURE_PROVIDER);
            try {
                signature = getSignature(p);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initSign((PrivateKey)key);
        LOG.debug("Signature provider: {}", signature.getProvider());
        LOG.debug("JCA Algorithm: {}", getJCAAlgorithm());

        try (SignerOutputStream outputStream = new SignerOutputStream(signature)) {
            ((DOMSignedInfo)si).canonicalize(context, outputStream);
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

        @Override
        Type getAlgorithmType() {
            return Type.RSA;
        }
    }

    abstract static class AbstractRSAPSSSignatureMethod
            extends AbstractRSASignatureMethod {

        AbstractRSAPSSSignatureMethod(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }

        AbstractRSAPSSSignatureMethod(Element dmElem) throws MarshalException {
            super(dmElem);
        }

        public abstract PSSParameterSpec getPSSParameterSpec();

        @Override
        Signature getSignature(Provider p)
                throws NoSuchAlgorithmException {
            try {
                Signature s = (p == null)
                        ? Signature.getInstance("RSASSA-PSS")
                        : Signature.getInstance("RSASSA-PSS", p);
                try {
                    s.setParameter(getPSSParameterSpec());
                } catch (InvalidAlgorithmParameterException e) {
                    throw new NoSuchAlgorithmException("Should not happen", e);
                }
                return s;
            } catch (NoSuchAlgorithmException nsae) {
                return super.getSignature(p);
            }
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

        @Override
        Type getAlgorithmType() {
            return Type.DSA;
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
                int rawLen = -1;
                if (key instanceof ECPrivateKey) {
                    ECPrivateKey ecKey = (ECPrivateKey)key;
                    rawLen = (ecKey.getParams().getCurve().getField().getFieldSize() + 7) / 8;
                }
                return SignatureECDSA.convertASN1toXMLDSIG(sig, rawLen);
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

        @Override
        Type getAlgorithmType() {
            return Type.ECDSA;
        }
    }

    abstract static class AbstractEDDSASignatureMethod
            extends DOMSignatureMethod {


        AbstractEDDSASignatureMethod(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }

        AbstractEDDSASignatureMethod(Element dmElem) throws MarshalException {
            super(dmElem);
        }

        /**
         * Returns {@code sig}. No extra formatting is necessary for EDDSA
         * See the RFC8032
         */
        @Override
        byte[] postSignFormat(Key key, byte[] sig) {
            return sig;
        }

        /**
         * Returns {@code sig}. No extra formatting is necessary for EDDSA
         * See the RFC8032
         */
        @Override
        byte[] preVerifyFormat(Key key, byte[] sig) {
            return sig;
        }

        @Override
        Type getAlgorithmType() {
            return Type.EDDSA;
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
        @Override
        public String getAlgorithm() {
            return SignatureMethod.RSA_SHA1;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA1withRSA";
        }
    }

    static final class SHA224withRSA extends AbstractRSASignatureMethod {
        SHA224withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA224withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA224;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA224withRSA";
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
        @Override
        public String getAlgorithm() {
            return RSA_SHA256;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA256withRSA";
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
        @Override
        public String getAlgorithm() {
            return RSA_SHA384;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA384withRSA";
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
        @Override
        public String getAlgorithm() {
            return RSA_SHA512;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA512withRSA";
        }
    }

    static final class RIPEMD160withRSA extends AbstractRSASignatureMethod {
        RIPEMD160withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        RIPEMD160withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_RIPEMD160;
        }
        @Override
        String getJCAAlgorithm() {
            return "RIPEMD160withRSA";
        }
    }

    static final class SHA1withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA1_MGF1_PARAMS
                = new PSSParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1,
                20, PSSParameterSpec.TRAILER_FIELD_BC);

        SHA1withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA1withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA1_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA1_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA1withRSAandMGF1";
        }
    }

    static final class SHA224withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA224_MGF1_PARAMS
                = new PSSParameterSpec("SHA-224", "MGF1", MGF1ParameterSpec.SHA224,
                28, PSSParameterSpec.TRAILER_FIELD_BC);

        SHA224withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA224withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA224_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA224_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA224withRSAandMGF1";
        }
    }

    static final class SHA256withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA256_MGF1_PARAMS
                = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                32, PSSParameterSpec.TRAILER_FIELD_BC);

        SHA256withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA256withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA256_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA256_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA256withRSAandMGF1";
        }
    }

    static final class SHA384withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA384_MGF1_PARAMS
                = new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384,
                48, PSSParameterSpec.TRAILER_FIELD_BC);

        SHA384withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA384withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA384_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA384_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA384withRSAandMGF1";
        }
    }

    static final class SHA512withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA512_MGF1_PARAMS
                = new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512,
                64, PSSParameterSpec.TRAILER_FIELD_BC);

        SHA512withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA512withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA512_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA512_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA512withRSAandMGF1";
        }
    }

    static final class SHA3_224withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA3_224_MGF1_PARAMS
                = new PSSParameterSpec("SHA3-224", "MGF1",
                new MGF1ParameterSpec("SHA3-224"), 28,
                PSSParameterSpec.TRAILER_FIELD_BC);

        SHA3_224withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA3_224withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA3_224_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA3_224_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA3-224withRSAandMGF1";
        }
    }

    static final class SHA3_256withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA3_256_MGF1_PARAMS
                = new PSSParameterSpec("SHA3-256", "MGF1",
                new MGF1ParameterSpec("SHA3-256"), 32,
                PSSParameterSpec.TRAILER_FIELD_BC);

        SHA3_256withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA3_256withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA3_256_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA3_256_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA3-256withRSAandMGF1";
        }
    }

    static final class SHA3_384withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA3_384_MGF1_PARAMS
                = new PSSParameterSpec("SHA3-384", "MGF1",
                new MGF1ParameterSpec("SHA3-384"), 48,
                PSSParameterSpec.TRAILER_FIELD_BC);

        SHA3_384withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA3_384withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA3_384_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA3_384_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA3-384withRSAandMGF1";
        }
    }

    static final class SHA3_512withRSAandMGF1 extends AbstractRSAPSSSignatureMethod {

        private static final PSSParameterSpec SHA3_512_MGF1_PARAMS
                = new PSSParameterSpec("SHA3-512", "MGF1",
                new MGF1ParameterSpec("SHA3-512"), 64,
                PSSParameterSpec.TRAILER_FIELD_BC);

        SHA3_512withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA3_512withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_SHA3_512_MGF1;
        }
        @Override
        public PSSParameterSpec getPSSParameterSpec() {
            return SHA3_512_MGF1_PARAMS;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA3-512withRSAandMGF1";
        }
    }

    static final class RIPEMD160withRSAandMGF1 extends AbstractRSASignatureMethod {
        RIPEMD160withRSAandMGF1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        RIPEMD160withRSAandMGF1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_RIPEMD160_MGF1;
        }
        @Override
        String getJCAAlgorithm() {
            return "RIPEMD160withRSAandMGF1";
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
        @Override
        public String getAlgorithm() {
            return SignatureMethod.DSA_SHA1;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA1withDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA1withDSA";
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
        @Override
        public String getAlgorithm() {
            return DSA_SHA256;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA256withDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA256withDSA";
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
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA1;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA1withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA1withECDSA";
        }
    }

    static final class SHA224withECDSA extends AbstractECDSASignatureMethod {
        SHA224withECDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA224withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA224;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA224withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA224withECDSA";
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
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA256;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA256withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA256withECDSA";
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
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA384;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA384withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA384withECDSA";
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
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA512;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA512withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA512withECDSA";
        }
    }

    static final class SHA3_224withECDSA extends AbstractECDSASignatureMethod {
        SHA3_224withECDSA(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA3_224withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA3_224;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA3-224withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA3-224withECDSA";
        }
    }

    static final class SHA3_256withECDSA extends AbstractECDSASignatureMethod {
        SHA3_256withECDSA(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA3_256withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA3_256;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA3-256withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA3-256withECDSA";
        }
    }

    static final class SHA3_384withECDSA extends AbstractECDSASignatureMethod {
        SHA3_384withECDSA(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA3_384withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA3_384;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA3-384withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA3-384withECDSA";
        }
    }

    static final class SHA3_512withECDSA extends AbstractECDSASignatureMethod {
        SHA3_512withECDSA(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA3_512withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return ECDSA_SHA3_512;
        }
        @Override
        String getJCAAlgorithm() {
            return "SHA3-512withECDSAinP1363Format";
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "SHA3-512withECDSA";
        }
    }

    static final class RIPEMD160withECDSA extends AbstractECDSASignatureMethod {
        RIPEMD160withECDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        RIPEMD160withECDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return ECDSA_RIPEMD160;
        }
        @Override
        String getJCAAlgorithm() {
            return "RIPEMD160withECDSAinP1363Format"; // Is this real?
        }
        @Override
        String getJCAFallbackAlgorithm() {
            return "RIPEMD160withECDSA";
        }
    }

    static final class EDDSA_ED25519 extends AbstractEDDSASignatureMethod {

        EDDSA_ED25519(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }

        EDDSA_ED25519(Element dmElem) throws MarshalException {
            super(dmElem);
        }

        @Override
        public String getAlgorithm() {
            return ED25519;
        }

        @Override
        String getJCAAlgorithm() {
            return "Ed25519";
        }
    }

    static final class EDDSA_ED448 extends AbstractEDDSASignatureMethod {
        EDDSA_ED448(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }

        EDDSA_ED448(Element dmElem) throws MarshalException {
            super(dmElem);
        }

        @Override
        public String getAlgorithm() {
            return ED448;
        }

        @Override
        String getJCAAlgorithm() {
            return "Ed448";
        }
    }
}
