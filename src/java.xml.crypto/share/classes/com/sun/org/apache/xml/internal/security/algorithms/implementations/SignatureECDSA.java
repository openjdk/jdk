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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
package com.sun.org.apache.xml.internal.security.algorithms.implementations;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.AlgorithmParameterSpec;

import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;
import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithmSpi;
import com.sun.org.apache.xml.internal.security.signature.XMLSignature;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;

/**
 *
 */
public abstract class SignatureECDSA extends SignatureAlgorithmSpi {

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(SignatureECDSA.class);

    private final Signature signatureAlgorithm;

    /** Length for each integer in signature */
    private int signIntLen = -1;

    /**
     * Converts an ASN.1 ECDSA value to a XML Signature ECDSA Value.
     *
     * The JAVA JCE ECDSA Signature algorithm creates ASN.1 encoded (r, s) value
     * pairs; the XML Signature requires the core BigInteger values.
     *
     * @param asn1Bytes
     * @param rawLen
     * @return the decode bytes
     *
     * @throws IOException
     * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#dsa-sha1">6.4.1 DSA</A>
     * @see <A HREF="ftp://ftp.rfc-editor.org/in-notes/rfc4050.txt">3.3. ECDSA Signatures</A>
     */
    public static byte[] convertASN1toXMLDSIG(byte[] asn1Bytes, int rawLen) throws IOException {
        return ECDSAUtils.convertASN1toXMLDSIG(asn1Bytes, rawLen);
    }

    /**
     * Converts a XML Signature ECDSA Value to an ASN.1 DSA value.
     *
     * The JAVA JCE ECDSA Signature algorithm creates ASN.1 encoded (r, s) value
     * pairs; the XML Signature requires the core BigInteger values.
     *
     * @param xmldsigBytes
     * @return the encoded ASN.1 bytes
     *
     * @throws IOException
     * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#dsa-sha1">6.4.1 DSA</A>
     * @see <A HREF="ftp://ftp.rfc-editor.org/in-notes/rfc4050.txt">3.3. ECDSA Signatures</A>
     */
    public static byte[] convertXMLDSIGtoASN1(byte[] xmldsigBytes) throws IOException {
        return ECDSAUtils.convertXMLDSIGtoASN1(xmldsigBytes);
    }

    /**
     * Constructor SignatureRSA
     *
     * @throws XMLSignatureException
     */
    public SignatureECDSA() throws XMLSignatureException {
        this(null);
    }

    public SignatureECDSA(Provider provider) throws XMLSignatureException {
        String algorithmID = JCEMapper.translateURItoJCEID(this.engineGetURI());
        LOG.debug("Created SignatureECDSA using {}", algorithmID);

        try {
            if (provider == null) {
                String providerId = JCEMapper.getProviderId();
                if (providerId == null) {
                    this.signatureAlgorithm = Signature.getInstance(algorithmID);

                } else {
                    this.signatureAlgorithm = Signature.getInstance(algorithmID, providerId);
                }

            } else {
                this.signatureAlgorithm = Signature.getInstance(algorithmID, provider);
            }

        } catch (NoSuchAlgorithmException | NoSuchProviderException ex) {
            Object[] exArgs = { algorithmID, ex.getLocalizedMessage() };
            throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void engineSetParameter(AlgorithmParameterSpec params)
        throws XMLSignatureException {
        try {
            this.signatureAlgorithm.setParameter(params);
        } catch (InvalidAlgorithmParameterException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected boolean engineVerify(byte[] signature) throws XMLSignatureException {
        try {
            byte[] jcebytes = SignatureECDSA.convertXMLDSIGtoASN1(signature);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Called ECDSA.verify() on " + XMLUtils.encodeToString(signature));
            }

            return this.signatureAlgorithm.verify(jcebytes);
        } catch (SignatureException | IOException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void engineInitVerify(Key publicKey) throws XMLSignatureException {
        engineInitVerify(publicKey, signatureAlgorithm);
    }

    /** {@inheritDoc} */
    @Override
    protected byte[] engineSign() throws XMLSignatureException {
        try {
            byte[] jcebytes = this.signatureAlgorithm.sign();
            return SignatureECDSA.convertASN1toXMLDSIG(jcebytes, signIntLen);
        } catch (SignatureException | IOException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void engineInitSign(Key privateKey, SecureRandom secureRandom)
        throws XMLSignatureException {
        if (privateKey instanceof ECPrivateKey) {
            ECPrivateKey ecKey = (ECPrivateKey) privateKey;
            signIntLen = (ecKey.getParams().getCurve().getField().getFieldSize() + 7) / 8;
            // If not ECPrivateKey, signIntLen remains -1
        }
        engineInitSign(privateKey, secureRandom, this.signatureAlgorithm);
    }

    /** {@inheritDoc} */
    @Override
    protected void engineInitSign(Key privateKey) throws XMLSignatureException {
        engineInitSign(privateKey, (SecureRandom)null);
    }

    /** {@inheritDoc} */
    @Override
    protected void engineUpdate(byte[] input) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(input);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void engineUpdate(byte input) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(input);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void engineUpdate(byte[] buf, int offset, int len) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(buf, offset, len);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String engineGetJCEAlgorithmString() {
        return this.signatureAlgorithm.getAlgorithm();
    }

    /** {@inheritDoc} */
    @Override
    protected String engineGetJCEProviderName() {
        return this.signatureAlgorithm.getProvider().getName();
    }

    /** {@inheritDoc} */
    @Override
    protected void engineSetHMACOutputLength(int HMACOutputLength)
        throws XMLSignatureException {
        throw new XMLSignatureException("algorithms.HMACOutputLengthOnlyForHMAC");
    }

    /** {@inheritDoc} */
    @Override
    protected void engineInitSign(
        Key signingKey, AlgorithmParameterSpec algorithmParameterSpec
    ) throws XMLSignatureException {
        throw new XMLSignatureException("algorithms.CannotUseAlgorithmParameterSpecOnRSA");
    }

    /**
     * Class SignatureECDSASHA1
     *
     */
    public static class SignatureECDSASHA1 extends SignatureECDSA {
        /**
         * Constructor SignatureECDSASHA1
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA1() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA1(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA1;
        }
    }

    /**
     * Class SignatureECDSASHA224
     */
    public static class SignatureECDSASHA224 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSASHA224
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA224() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA224(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA224;
        }
    }

    /**
     * Class SignatureECDSASHA256
     *
     */
    public static class SignatureECDSASHA256 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSASHA256
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA256() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA256(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA256;
        }
    }

    /**
     * Class SignatureECDSASHA384
     *
     */
    public static class SignatureECDSASHA384 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSASHA384
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA384() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA384(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA384;
        }
    }

    /**
     * Class SignatureECDSASHA512
     *
     */
    public static class SignatureECDSASHA512 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSASHA512
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA512() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA512(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA512;
        }
    }

    /**
     * Class SignatureECDSASHA3-224
     *
     */
    public static class SignatureECDSASHA3_224 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSASHA3-224
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA3_224() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA3_224(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA3_224;
        }
    }

    /**
     * Class SignatureECDSASHA3-256
     *
     */
    public static class SignatureECDSASHA3_256 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSASHA3-256
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA3_256() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA3_256(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA3_256;
        }
    }

    /**
     * Class SignatureECDSASHA3-384
     *
     */
    public static class SignatureECDSASHA3_384 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSASHA3-384
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA3_384() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA3_384(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA3_384;
        }
    }

    /**
     * Class SignatureECDSASHA3-512
     *
     */
    public static class SignatureECDSASHA3_512 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSASHA3-512
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSASHA3_512() throws XMLSignatureException {
            super();
        }

        public SignatureECDSASHA3_512(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA3_512;
        }
    }

    /**
     * Class SignatureECDSARIPEMD160
     */
    public static class SignatureECDSARIPEMD160 extends SignatureECDSA {

        /**
         * Constructor SignatureECDSARIPEMD160
         *
         * @throws XMLSignatureException
         */
        public SignatureECDSARIPEMD160() throws XMLSignatureException {
            super();
        }

        public SignatureECDSARIPEMD160(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_RIPEMD160;
        }
    }

}
