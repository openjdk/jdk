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
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;
import com.sun.org.apache.xml.internal.security.algorithms.MessageDigestAlgorithm;
import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithmSpi;
import com.sun.org.apache.xml.internal.security.signature.XMLSignature;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

public abstract class IntegrityHmac extends SignatureAlgorithmSpi {

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(IntegrityHmac.class);

    /** Field macAlgorithm */
    private Mac macAlgorithm;

    /** Field HMACOutputLength */
    private int HMACOutputLength;
    private boolean HMACOutputLengthSet = false;

    /**
     * Method engineGetURI
     *
     *{@inheritDoc}
     */
    public abstract String engineGetURI();

    /**
     * Returns the output length of the hash/digest.
     */
    abstract int getDigestLength();

    /**
     * Method IntegrityHmac
     *
     * @throws XMLSignatureException
     */
    public IntegrityHmac() throws XMLSignatureException {
        String algorithmID = JCEMapper.translateURItoJCEID(this.engineGetURI());
        LOG.debug("Created IntegrityHmacSHA1 using {}", algorithmID);

        try {
            this.macAlgorithm = Mac.getInstance(algorithmID);
        } catch (java.security.NoSuchAlgorithmException ex) {
            Object[] exArgs = { algorithmID, ex.getLocalizedMessage() };

            throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs);
        }
    }

    /**
     * Proxy method for {@link java.security.Signature#setParameter(
     * java.security.spec.AlgorithmParameterSpec)}
     * which is executed on the internal {@link java.security.Signature} object.
     *
     * @param params
     * @throws XMLSignatureException
     */
    protected void engineSetParameter(AlgorithmParameterSpec params) throws XMLSignatureException {
        throw new XMLSignatureException("empty", new Object[]{"Incorrect method call"});
    }

    public void reset() {
        HMACOutputLength = 0;
        HMACOutputLengthSet = false;
        this.macAlgorithm.reset();
    }

    /**
     * Proxy method for {@link java.security.Signature#verify(byte[])}
     * which is executed on the internal {@link java.security.Signature} object.
     *
     * @param signature
     * @return true if the signature is correct
     * @throws XMLSignatureException
     */
    protected boolean engineVerify(byte[] signature) throws XMLSignatureException {
        try {
            if (this.HMACOutputLengthSet && this.HMACOutputLength < getDigestLength()) {
                LOG.debug("HMACOutputLength must not be less than {}", getDigestLength());
                Object[] exArgs = { String.valueOf(getDigestLength()) };
                throw new XMLSignatureException("algorithms.HMACOutputLengthMin", exArgs);
            } else {
                byte[] completeResult = this.macAlgorithm.doFinal();
                return MessageDigestAlgorithm.isEqual(completeResult, signature);
            }
        } catch (IllegalStateException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * Proxy method for {@link java.security.Signature#initVerify(java.security.PublicKey)}
     * which is executed on the internal {@link java.security.Signature} object.
     *
     * @param secretKey
     * @throws XMLSignatureException
     */
    protected void engineInitVerify(Key secretKey) throws XMLSignatureException {
        if (!(secretKey instanceof SecretKey)) {
            String supplied = null;
            if (secretKey != null) {
                supplied = secretKey.getClass().getName();
            }
            String needed = SecretKey.class.getName();
            Object exArgs[] = { supplied, needed };

            throw new XMLSignatureException("algorithms.WrongKeyForThisOperation", exArgs);
        }

        try {
            this.macAlgorithm.init(secretKey);
        } catch (InvalidKeyException ex) {
            // reinstantiate Mac object to work around bug in JDK
            // see: http://bugs.java.com/view_bug.do?bug_id=4953555
            Mac mac = this.macAlgorithm;
            try {
                this.macAlgorithm = Mac.getInstance(macAlgorithm.getAlgorithm());
            } catch (Exception e) {
                // this shouldn't occur, but if it does, restore previous Mac
                LOG.debug("Exception when reinstantiating Mac: {}", e);
                this.macAlgorithm = mac;
            }
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * Proxy method for {@link java.security.Signature#sign()}
     * which is executed on the internal {@link java.security.Signature} object.
     *
     * @return the result of the {@link java.security.Signature#sign()} method
     * @throws XMLSignatureException
     */
    protected byte[] engineSign() throws XMLSignatureException {
        try {
            if (this.HMACOutputLengthSet && this.HMACOutputLength < getDigestLength()) {
                LOG.debug("HMACOutputLength must not be less than {}", getDigestLength());
                Object[] exArgs = { String.valueOf(getDigestLength()) };
                throw new XMLSignatureException("algorithms.HMACOutputLengthMin", exArgs);
            } else {
                return this.macAlgorithm.doFinal();
            }
        } catch (IllegalStateException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * Method engineInitSign
     *
     * @param secretKey
     * @throws XMLSignatureException
     */
    protected void engineInitSign(Key secretKey) throws XMLSignatureException {
        engineInitSign(secretKey, (AlgorithmParameterSpec)null);
    }

    /**
     * Method engineInitSign
     *
     * @param secretKey
     * @param algorithmParameterSpec
     * @throws XMLSignatureException
     */
    protected void engineInitSign(
        Key secretKey, AlgorithmParameterSpec algorithmParameterSpec
    ) throws XMLSignatureException {
        if (!(secretKey instanceof SecretKey)) {
            String supplied = null;
            if (secretKey != null) {
                supplied = secretKey.getClass().getName();
            }
            String needed = SecretKey.class.getName();
            Object exArgs[] = { supplied, needed };

            throw new XMLSignatureException("algorithms.WrongKeyForThisOperation", exArgs);
        }

        try {
            if (algorithmParameterSpec == null) {
                this.macAlgorithm.init(secretKey);
            } else {
                this.macAlgorithm.init(secretKey, algorithmParameterSpec);
            }
        } catch (InvalidKeyException ex) {
            throw new XMLSignatureException(ex);
        } catch (InvalidAlgorithmParameterException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * Method engineInitSign
     *
     * @param secretKey
     * @param secureRandom
     * @throws XMLSignatureException
     */
    protected void engineInitSign(Key secretKey, SecureRandom secureRandom)
        throws XMLSignatureException {
        throw new XMLSignatureException("algorithms.CannotUseSecureRandomOnMAC");
    }

    /**
     * Proxy method for {@link java.security.Signature#update(byte[])}
     * which is executed on the internal {@link java.security.Signature} object.
     *
     * @param input
     * @throws XMLSignatureException
     */
    protected void engineUpdate(byte[] input) throws XMLSignatureException {
        try {
            this.macAlgorithm.update(input);
        } catch (IllegalStateException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * Proxy method for {@link java.security.Signature#update(byte)}
     * which is executed on the internal {@link java.security.Signature} object.
     *
     * @param input
     * @throws XMLSignatureException
     */
    protected void engineUpdate(byte input) throws XMLSignatureException {
        try {
            this.macAlgorithm.update(input);
        } catch (IllegalStateException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * Proxy method for {@link java.security.Signature#update(byte[], int, int)}
     * which is executed on the internal {@link java.security.Signature} object.
     *
     * @param buf
     * @param offset
     * @param len
     * @throws XMLSignatureException
     */
    protected void engineUpdate(byte buf[], int offset, int len) throws XMLSignatureException {
        try {
            this.macAlgorithm.update(buf, offset, len);
        } catch (IllegalStateException ex) {
            throw new XMLSignatureException(ex);
        }
    }

    /**
     * Method engineGetJCEAlgorithmString
     * {@inheritDoc}
     *
     */
    protected String engineGetJCEAlgorithmString() {
        return this.macAlgorithm.getAlgorithm();
    }

    /**
     * Method engineGetJCEAlgorithmString
     *
     * {@inheritDoc}
     */
    protected String engineGetJCEProviderName() {
        return this.macAlgorithm.getProvider().getName();
    }

    /**
     * Method engineSetHMACOutputLength
     *
     * @param HMACOutputLength
     */
    protected void engineSetHMACOutputLength(int HMACOutputLength) {
        this.HMACOutputLength = HMACOutputLength;
        this.HMACOutputLengthSet = true;
    }

    /**
     * Method engineGetContextFromElement
     *
     * @param element
     */
    protected void engineGetContextFromElement(Element element) {
        super.engineGetContextFromElement(element);

        if (element == null) {
            throw new IllegalArgumentException("element null");
        }

        Text hmaclength =
            XMLUtils.selectDsNodeText(element.getFirstChild(), Constants._TAG_HMACOUTPUTLENGTH, 0);

        if (hmaclength != null) {
            this.HMACOutputLength = Integer.parseInt(hmaclength.getData());
            this.HMACOutputLengthSet = true;
        }
    }

    /**
     * Method engineAddContextToElement
     *
     * @param element
     */
    public void engineAddContextToElement(Element element) {
        if (element == null) {
            throw new IllegalArgumentException("null element");
        }

        if (this.HMACOutputLengthSet) {
            Document doc = element.getOwnerDocument();
            Element HMElem =
                XMLUtils.createElementInSignatureSpace(doc, Constants._TAG_HMACOUTPUTLENGTH);
            Text HMText =
                doc.createTextNode("" + this.HMACOutputLength);

            HMElem.appendChild(HMText);
            XMLUtils.addReturnToElement(element);
            element.appendChild(HMElem);
            XMLUtils.addReturnToElement(element);
        }
    }

    /**
     * Class IntegrityHmacSHA1
     */
    public static class IntegrityHmacSHA1 extends IntegrityHmac {

        /**
         * Constructor IntegrityHmacSHA1
         *
         * @throws XMLSignatureException
         */
        public IntegrityHmacSHA1() throws XMLSignatureException {
            super();
        }

        /**
         * Method engineGetURI
         * {@inheritDoc}
         *
         */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_MAC_HMAC_SHA1;
        }

        int getDigestLength() {
            return 160;
        }
    }

    /**
     * Class IntegrityHmacSHA224
     */
    public static class IntegrityHmacSHA224 extends IntegrityHmac {

        /**
         * Constructor IntegrityHmacSHA224
         *
         * @throws XMLSignatureException
         */
        public IntegrityHmacSHA224() throws XMLSignatureException {
            super();
        }

        /**
         * Method engineGetURI
         *
         * {@inheritDoc}
         */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_MAC_HMAC_SHA224;
        }

        int getDigestLength() {
            return 224;
        }
    }

    /**
     * Class IntegrityHmacSHA256
     */
    public static class IntegrityHmacSHA256 extends IntegrityHmac {

        /**
         * Constructor IntegrityHmacSHA256
         *
         * @throws XMLSignatureException
         */
        public IntegrityHmacSHA256() throws XMLSignatureException {
            super();
        }

        /**
         * Method engineGetURI
         *
         * {@inheritDoc}
         */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_MAC_HMAC_SHA256;
        }

        int getDigestLength() {
            return 256;
        }
    }

    /**
     * Class IntegrityHmacSHA384
     */
    public static class IntegrityHmacSHA384 extends IntegrityHmac {

        /**
         * Constructor IntegrityHmacSHA384
         *
         * @throws XMLSignatureException
         */
        public IntegrityHmacSHA384() throws XMLSignatureException {
            super();
        }

        /**
         * Method engineGetURI
         * {@inheritDoc}
         *
         */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_MAC_HMAC_SHA384;
        }

        int getDigestLength() {
            return 384;
        }
    }

    /**
     * Class IntegrityHmacSHA512
     */
    public static class IntegrityHmacSHA512 extends IntegrityHmac {

        /**
         * Constructor IntegrityHmacSHA512
         *
         * @throws XMLSignatureException
         */
        public IntegrityHmacSHA512() throws XMLSignatureException {
            super();
        }

        /**
         * Method engineGetURI
         * {@inheritDoc}
         *
         */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_MAC_HMAC_SHA512;
        }

        int getDigestLength() {
            return 512;
        }
    }

    /**
     * Class IntegrityHmacRIPEMD160
     */
    public static class IntegrityHmacRIPEMD160 extends IntegrityHmac {

        /**
         * Constructor IntegrityHmacRIPEMD160
         *
         * @throws XMLSignatureException
         */
        public IntegrityHmacRIPEMD160() throws XMLSignatureException {
            super();
        }

        /**
         * Method engineGetURI
         *
         * {@inheritDoc}
         */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_MAC_HMAC_RIPEMD160;
        }

        int getDigestLength() {
            return 160;
        }
    }

    /**
     * Class IntegrityHmacMD5
     */
    public static class IntegrityHmacMD5 extends IntegrityHmac {

        /**
         * Constructor IntegrityHmacMD5
         *
         * @throws XMLSignatureException
         */
        public IntegrityHmacMD5() throws XMLSignatureException {
            super();
        }

        /**
         * Method engineGetURI
         *
         * {@inheritDoc}
         */
        public String engineGetURI() {
            return XMLSignature.ALGO_ID_MAC_HMAC_NOT_RECOMMENDED_MD5;
        }

        int getDigestLength() {
            return 128;
        }
    }
}
