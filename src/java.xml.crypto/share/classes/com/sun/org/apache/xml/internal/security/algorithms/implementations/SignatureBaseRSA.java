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
package com.sun.org.apache.xml.internal.security.algorithms.implementations;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;

import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;
import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithmSpi;
import com.sun.org.apache.xml.internal.security.signature.XMLSignature;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;

public abstract class SignatureBaseRSA extends SignatureAlgorithmSpi {

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(SignatureBaseRSA.class);

    /** {@inheritDoc} */
    public abstract String engineGetURI();

    /** Field algorithm */
    private Signature signatureAlgorithm;

    /**
     * Constructor SignatureRSA
     *
     * @throws XMLSignatureException
     */
    public SignatureBaseRSA() throws XMLSignatureException {
        String algorithmID = JCEMapper.translateURItoJCEID(this.engineGetURI());

        LOG.debug("Created SignatureRSA using {}", algorithmID);
        String provider = JCEMapper.getProviderId();
        try {
            if (provider == null) {
                this.signatureAlgorithm = Signature.getInstance(algorithmID);
            } else {
                this.signatureAlgorithm = Signature.getInstance(algorithmID, provider);
            }
        } catch (java.security.NoSuchAlgorithmException ex) {
            Object[] exArgs = { algorithmID, ex.getLocalizedMessage() };

            throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs);
        } catch (NoSuchProviderException ex) {
            Object[] exArgs = { algorithmID, ex.getLocalizedMessage() };

            throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs);
        }
    }

    /** {@inheritDoc} */
    protected void engineSetParameter(AlgorithmParameterSpec params)
        throws XMLSignatureException {
        try {
            this.signatureAlgorithm.setParameter(params);
        } catch (InvalidAlgorithmParameterException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected boolean engineVerify(byte[] signature) throws XMLSignatureException {
        try {
            return this.signatureAlgorithm.verify(signature);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected void engineInitVerify(Key publicKey) throws XMLSignatureException {
        if (!(publicKey instanceof PublicKey)) {
            String supplied = null;
            if (publicKey != null) {
                supplied = publicKey.getClass().getName();
            }
            String needed = PublicKey.class.getName();
            Object exArgs[] = { supplied, needed };

            throw new XMLSignatureException("algorithms.WrongKeyForThisOperation", exArgs);
        }

        try {
            this.signatureAlgorithm.initVerify((PublicKey) publicKey);
        } catch (InvalidKeyException ex) {
            // reinstantiate Signature object to work around bug in JDK
            // see: http://bugs.java.com/view_bug.do?bug_id=4953555
            Signature sig = this.signatureAlgorithm;
            try {
                this.signatureAlgorithm = Signature.getInstance(signatureAlgorithm.getAlgorithm());
            } catch (Exception e) {
                // this shouldn't occur, but if it does, restore previous
                // Signature
                LOG.debug("Exception when reinstantiating Signature: {}", e);
                this.signatureAlgorithm = sig;
            }
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected byte[] engineSign() throws XMLSignatureException {
        try {
            return this.signatureAlgorithm.sign();
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected void engineInitSign(Key privateKey, SecureRandom secureRandom)
        throws XMLSignatureException {
        if (!(privateKey instanceof PrivateKey)) {
            String supplied = null;
            if (privateKey != null) {
                supplied = privateKey.getClass().getName();
            }
            String needed = PrivateKey.class.getName();
            Object exArgs[] = { supplied, needed };

            throw new XMLSignatureException("algorithms.WrongKeyForThisOperation", exArgs);
        }

        try {
            if (secureRandom == null) {
                this.signatureAlgorithm.initSign((PrivateKey) privateKey);
            } else {
                this.signatureAlgorithm.initSign((PrivateKey) privateKey, secureRandom);
            }
        } catch (InvalidKeyException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected void engineInitSign(Key privateKey) throws XMLSignatureException {
        engineInitSign(privateKey, (SecureRandom)null);
    }

    /** {@inheritDoc} */
    protected void engineUpdate(byte[] input) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(input);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected void engineUpdate(byte input) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(input);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected void engineUpdate(byte buf[], int offset, int len) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(buf, offset, len);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected String engineGetJCEAlgorithmString() {
        return this.signatureAlgorithm.getAlgorithm();
    }

    /** {@inheritDoc} */
    protected String engineGetJCEProviderName() {
        return this.signatureAlgorithm.getProvider().getName();
    }

    /** {@inheritDoc} */
    protected void engineSetHMACOutputLength(int HMACOutputLength)
        throws XMLSignatureException {
        throw new XMLSignatureException("algorithms.HMACOutputLengthOnlyForHMAC");
    }

    /** {@inheritDoc} */
    protected void engineInitSign(
        Key signingKey, AlgorithmParameterSpec algorithmParameterSpec
    ) throws XMLSignatureException {
        throw new XMLSignatureException("algorithms.CannotUseAlgorithmParameterSpecOnRSA");
    }

    /**
     * Class SignatureRSASHA1
     */
    public static class SignatureRSASHA1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1;
        }
    }

    /**
     * Class SignatureRSASHA224
     */
    public static class SignatureRSASHA224 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA224
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA224() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA224;
        }
    }

    /**
     * Class SignatureRSASHA256
     */
    public static class SignatureRSASHA256 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA256
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA256() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256;
        }
    }

    /**
     * Class SignatureRSASHA384
     */
    public static class SignatureRSASHA384 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA384
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA384() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA384;
        }
    }

    /**
     * Class SignatureRSASHA512
     */
    public static class SignatureRSASHA512 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA512
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA512() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA512;
        }
    }

    /**
     * Class SignatureRSARIPEMD160
     */
    public static class SignatureRSARIPEMD160 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSARIPEMD160
         *
         * @throws XMLSignatureException
         */
        public SignatureRSARIPEMD160() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_RIPEMD160;
        }
    }

    /**
     * Class SignatureRSAMD5
     */
    public static class SignatureRSAMD5 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSAMD5
         *
         * @throws XMLSignatureException
         */
        public SignatureRSAMD5() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_NOT_RECOMMENDED_RSA_MD5;
        }
    }

    /**
     * Class SignatureRSASHA1MGF1
     */
    public static class SignatureRSASHA1MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA1MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA1MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1_MGF1;
        }
    }

    /**
     * Class SignatureRSASHA224MGF1
     */
    public static class SignatureRSASHA224MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA224MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA224MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA224_MGF1;
        }
    }

    /**
     * Class SignatureRSASHA256MGF1
     */
    public static class SignatureRSASHA256MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA256MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA256MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256_MGF1;
        }
    }

    /**
     * Class SignatureRSASHA384MGF1
     */
    public static class SignatureRSASHA384MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA384MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA384MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA384_MGF1;
        }
    }

    /**
     * Class SignatureRSASHA512MGF1
     */
    public static class SignatureRSASHA512MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA512MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA512MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA512_MGF1;
        }
    }

    /**
     * Class SignatureRSA3_SHA224MGF1
     */
    public static class SignatureRSASHA3_224MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA3_224MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA3_224MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA3_224_MGF1;
        }
    }

    /**
     * Class SignatureRSA3_SHA256MGF1
     */
    public static class SignatureRSASHA3_256MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA3_256MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA3_256MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA3_256_MGF1;
        }
    }

    /**
     * Class SignatureRSA3_SHA384MGF1
     */
    public static class SignatureRSASHA3_384MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA3_384MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA3_384MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA3_384_MGF1;
        }
    }

    /**
     * Class SignatureRSASHA3_512MGF1
     */
    public static class SignatureRSASHA3_512MGF1 extends SignatureBaseRSA {

        /**
         * Constructor SignatureRSASHA3_512MGF1
         *
         * @throws XMLSignatureException
         */
        public SignatureRSASHA3_512MGF1() throws XMLSignatureException {
            super();
        }

        /** {@inheritDoc} */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA3_512_MGF1;
        }
    }
}
