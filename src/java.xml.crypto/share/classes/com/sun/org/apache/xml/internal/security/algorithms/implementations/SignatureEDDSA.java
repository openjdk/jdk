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

import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;
import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithmSpi;
import com.sun.org.apache.xml.internal.security.signature.XMLSignature;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;

import java.io.IOException;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

/**
 *
 */
public abstract class SignatureEDDSA extends SignatureAlgorithmSpi {

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(SignatureEDDSA.class);

    private final Signature signatureAlgorithm;


    /**
     * Constructor SignatureEDDSA
     *
     * @throws XMLSignatureException
     */
    public SignatureEDDSA() throws XMLSignatureException {
        this(null);
    }

    public SignatureEDDSA(Provider provider) throws XMLSignatureException {
        String algorithmID = JCEMapper.translateURItoJCEID(this.engineGetURI());
        LOG.debug("Created SignatureEDDSA using {}", algorithmID);

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

            if (LOG.isDebugEnabled()) {
                LOG.debug("Called SignatureEDDSA.verify() on " + XMLUtils.encodeToString(signature));
            }

            return this.signatureAlgorithm.verify(signature);
        } catch (SignatureException  ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /** {@inheritDoc} */
    protected void engineInitVerify(Key publicKey) throws XMLSignatureException {
        engineInitVerify(publicKey, signatureAlgorithm);
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

        engineInitSign(privateKey, secureRandom, this.signatureAlgorithm);
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
    protected void engineUpdate(byte[] buf, int offset, int len) throws XMLSignatureException {
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
        throw new XMLSignatureException("algorithms.CannotUseAlgorithmParameterSpecOnEdDSA");
    }

    /**
     * Class SignatureEd25519
     *
     */
    public static class SignatureEd25519 extends SignatureEDDSA {
        /**
         * Constructor SignatureEd25519
         *
         * @throws XMLSignatureException
         */
        public SignatureEd25519() throws XMLSignatureException {
            super();
        }

        public SignatureEd25519(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_EDDSA_ED25519;
        }
    }

    /**
     * Class SignatureEd448
     */
    public static class SignatureEd448 extends SignatureEDDSA {

        /**
         * Constructor SignatureEd448
         *
         * @throws XMLSignatureException
         */
        public SignatureEd448() throws XMLSignatureException {
            super();
        }

        public SignatureEd448(Provider provider) throws XMLSignatureException {
            super(provider);
        }

        /** {@inheritDoc} */
        @Override
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_EDDSA_ED448;
        }
    }
}
