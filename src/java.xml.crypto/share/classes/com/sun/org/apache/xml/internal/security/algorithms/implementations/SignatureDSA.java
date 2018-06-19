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

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.DSAKey;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;
import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithmSpi;
import com.sun.org.apache.xml.internal.security.signature.XMLSignature;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.JavaUtils;

public class SignatureDSA extends SignatureAlgorithmSpi {

    public static final String URI = Constants.SignatureSpecNS + "dsa-sha1";

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(SignatureDSA.class);

    /** Field algorithm */
    private Signature signatureAlgorithm;

    /** size of Q */
    private int size;

    /**
     * Method engineGetURI
     *
     * {@inheritDoc}
     */
    protected String engineGetURI() {
        return XMLSignature.ALGO_ID_SIGNATURE_DSA;
    }

    /**
     * Constructor SignatureDSA
     *
     * @throws XMLSignatureException
     */
    public SignatureDSA() throws XMLSignatureException {
        String algorithmID = JCEMapper.translateURItoJCEID(engineGetURI());
        LOG.debug("Created SignatureDSA using {}", algorithmID);

        String provider = JCEMapper.getProviderId();
        try {
            if (provider == null) {
                this.signatureAlgorithm = Signature.getInstance(algorithmID);
            } else {
                this.signatureAlgorithm =
                    Signature.getInstance(algorithmID, provider);
            }
        } catch (java.security.NoSuchAlgorithmException ex) {
            Object[] exArgs = { algorithmID, ex.getLocalizedMessage() };
            throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs);
        } catch (java.security.NoSuchProviderException ex) {
            Object[] exArgs = { algorithmID, ex.getLocalizedMessage() };
            throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void engineSetParameter(AlgorithmParameterSpec params)
        throws XMLSignatureException {
        try {
            this.signatureAlgorithm.setParameter(params);
        } catch (InvalidAlgorithmParameterException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected boolean engineVerify(byte[] signature)
        throws XMLSignatureException {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Called DSA.verify() on " + Base64.getMimeEncoder().encodeToString(signature));
            }

            byte[] jcebytes = JavaUtils.convertDsaXMLDSIGtoASN1(signature,
                                                                size/8);

            return this.signatureAlgorithm.verify(jcebytes);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        } catch (IOException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
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
        size = ((DSAKey)publicKey).getParams().getQ().bitLength();
    }

    /**
     * {@inheritDoc}
     */
    protected byte[] engineSign() throws XMLSignatureException {
        try {
            byte jcebytes[] = this.signatureAlgorithm.sign();

            return JavaUtils.convertDsaASN1toXMLDSIG(jcebytes, size/8);
        } catch (IOException ex) {
            throw new XMLSignatureException(ex);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
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
        size = ((DSAKey)privateKey).getParams().getQ().bitLength();
    }

    /**
     * {@inheritDoc}
     */
    protected void engineInitSign(Key privateKey) throws XMLSignatureException {
        engineInitSign(privateKey, (SecureRandom)null);
    }

    /**
     * {@inheritDoc}
     */
    protected void engineUpdate(byte[] input) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(input);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void engineUpdate(byte input) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(input);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void engineUpdate(byte buf[], int offset, int len) throws XMLSignatureException {
        try {
            this.signatureAlgorithm.update(buf, offset, len);
        } catch (SignatureException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * Method engineGetJCEAlgorithmString
     *
     * {@inheritDoc}
     */
    protected String engineGetJCEAlgorithmString() {
        return this.signatureAlgorithm.getAlgorithm();
    }

    /**
     * Method engineGetJCEProviderName
     *
     * {@inheritDoc}
     */
    protected String engineGetJCEProviderName() {
        return this.signatureAlgorithm.getProvider().getName();
    }

    /**
     * Method engineSetHMACOutputLength
     *
     * @param HMACOutputLength
     * @throws XMLSignatureException
     */
    protected void engineSetHMACOutputLength(int HMACOutputLength) throws XMLSignatureException {
        throw new XMLSignatureException("algorithms.HMACOutputLengthOnlyForHMAC");
    }

    /**
     * Method engineInitSign
     *
     * @param signingKey
     * @param algorithmParameterSpec
     * @throws XMLSignatureException
     */
    protected void engineInitSign(
        Key signingKey, AlgorithmParameterSpec algorithmParameterSpec
    ) throws XMLSignatureException {
        throw new XMLSignatureException("algorithms.CannotUseAlgorithmParameterSpecOnDSA");
    }

    public static class SHA256 extends SignatureDSA {

        public SHA256() throws XMLSignatureException {
            super();
        }

        public String engineGetURI() {
            return XMLSignature.ALGO_ID_SIGNATURE_DSA_SHA256;
        }
    }
}
