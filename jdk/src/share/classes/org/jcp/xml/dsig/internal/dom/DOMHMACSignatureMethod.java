/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2005 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
/*
 * Copyright 2005-2009 Sun Microsystems, Inc. All rights reserved.
 */
/*
 * $Id: DOMHMACSignatureMethod.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.spec.HMACParameterSpec;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.jcp.xml.dsig.internal.MacOutputStream;

/**
 * DOM-based implementation of HMAC SignatureMethod.
 *
 * @author Sean Mullan
 */
public abstract class DOMHMACSignatureMethod extends DOMSignatureMethod {

    private static Logger log =
        Logger.getLogger("org.jcp.xml.dsig.internal.dom");
    private Mac hmac;
    private int outputLength;
    private boolean outputLengthSet;

    /**
     * Creates a <code>DOMHMACSignatureMethod</code> with the specified params
     *
     * @param params algorithm-specific parameters (may be <code>null</code>)
     * @throws InvalidAlgorithmParameterException if params are inappropriate
     */
    DOMHMACSignatureMethod(AlgorithmParameterSpec params)
        throws InvalidAlgorithmParameterException {
        super(params);
    }

    /**
     * Creates a <code>DOMHMACSignatureMethod</code> from an element.
     *
     * @param smElem a SignatureMethod element
     */
    DOMHMACSignatureMethod(Element smElem) throws MarshalException {
        super(smElem);
    }

    void checkParams(SignatureMethodParameterSpec params)
        throws InvalidAlgorithmParameterException {
        if (params != null) {
            if (!(params instanceof HMACParameterSpec)) {
                throw new InvalidAlgorithmParameterException
                    ("params must be of type HMACParameterSpec");
            }
            outputLength = ((HMACParameterSpec) params).getOutputLength();
            outputLengthSet = true;
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE,
                    "Setting outputLength from HMACParameterSpec to: "
                    + outputLength);
            }
        } else {
            outputLength = -1;
        }
    }

    SignatureMethodParameterSpec unmarshalParams(Element paramsElem)
        throws MarshalException {
        outputLength = new Integer
            (paramsElem.getFirstChild().getNodeValue()).intValue();
        outputLengthSet = true;
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "unmarshalled outputLength: " + outputLength);
        }
        return new HMACParameterSpec(outputLength);
    }

    void marshalParams(Element parent, String prefix)
        throws MarshalException {

        Document ownerDoc = DOMUtils.getOwnerDocument(parent);
        Element hmacElem = DOMUtils.createElement(ownerDoc, "HMACOutputLength",
            XMLSignature.XMLNS, prefix);
        hmacElem.appendChild(ownerDoc.createTextNode
           (String.valueOf(outputLength)));

        parent.appendChild(hmacElem);
    }

    boolean verify(Key key, DOMSignedInfo si, byte[] sig,
        XMLValidateContext context)
        throws InvalidKeyException, SignatureException, XMLSignatureException {
        if (key == null || si == null || sig == null) {
            throw new NullPointerException();
        }
        if (!(key instanceof SecretKey)) {
            throw new InvalidKeyException("key must be SecretKey");
        }
        if (hmac == null) {
            try {
                hmac = Mac.getInstance(getSignatureAlgorithm());
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        if (outputLengthSet && outputLength < getDigestLength()) {
            throw new XMLSignatureException
                ("HMACOutputLength must not be less than " + getDigestLength());
        }
        hmac.init((SecretKey) key);
        si.canonicalize(context, new MacOutputStream(hmac));
        byte[] result = hmac.doFinal();

        return MessageDigest.isEqual(sig, result);
    }

    byte[] sign(Key key, DOMSignedInfo si, XMLSignContext context)
        throws InvalidKeyException, XMLSignatureException {
        if (key == null || si == null) {
            throw new NullPointerException();
        }
        if (!(key instanceof SecretKey)) {
            throw new InvalidKeyException("key must be SecretKey");
        }
        if (hmac == null) {
            try {
                hmac = Mac.getInstance(getSignatureAlgorithm());
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        if (outputLengthSet && outputLength < getDigestLength()) {
            throw new XMLSignatureException
                ("HMACOutputLength must not be less than " + getDigestLength());
        }
        hmac.init((SecretKey) key);
        si.canonicalize(context, new MacOutputStream(hmac));
        return hmac.doFinal();
    }

    boolean paramsEqual(AlgorithmParameterSpec spec) {
        if (getParameterSpec() == spec) {
            return true;
        }
        if (!(spec instanceof HMACParameterSpec)) {
            return false;
        }
        HMACParameterSpec ospec = (HMACParameterSpec) spec;

        return (outputLength == ospec.getOutputLength());
    }

    /**
     * Returns the output length of the hash/digest.
     */
    abstract int getDigestLength();

    static final class SHA1 extends DOMHMACSignatureMethod {
        SHA1(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA1(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return SignatureMethod.HMAC_SHA1;
        }
        String getSignatureAlgorithm() {
            return "HmacSHA1";
        }
        int getDigestLength() {
            return 160;
        }
    }

    static final class SHA256 extends DOMHMACSignatureMethod {
        SHA256(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA256(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return HMAC_SHA256;
        }
        String getSignatureAlgorithm() {
            return "HmacSHA256";
        }
        int getDigestLength() {
            return 256;
        }
    }

    static final class SHA384 extends DOMHMACSignatureMethod {
        SHA384(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA384(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return HMAC_SHA384;
        }
        String getSignatureAlgorithm() {
            return "HmacSHA384";
        }
        int getDigestLength() {
            return 384;
        }
    }

    static final class SHA512 extends DOMHMACSignatureMethod {
        SHA512(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA512(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return HMAC_SHA512;
        }
        String getSignatureAlgorithm() {
            return "HmacSHA512";
        }
        int getDigestLength() {
            return 512;
        }
    }
}
