/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
 * $Id: DOMHMACSignatureMethod.java,v 1.17 2005/09/15 14:29:04 mullan Exp $
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
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "outputLength = " + outputLength);
        }
        hmac.init((SecretKey) key);
        si.canonicalize(context, new MacOutputStream(hmac));
        byte[] result = hmac.doFinal();
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "resultLength = " + result.length);
        }
        if (outputLength != -1) {
            int byteLength = outputLength/8;
            if (result.length > byteLength) {
                byte[] truncated = new byte[byteLength];
                System.arraycopy(result, 0, truncated, 0, byteLength);
                result = truncated;
            }
        }

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
        hmac.init((SecretKey) key);
        si.canonicalize(context, new MacOutputStream(hmac));
        byte[] result = hmac.doFinal();
        if (outputLength != -1) {
            int byteLength = outputLength/8;
            if (result.length > byteLength) {
                byte[] truncated = new byte[byteLength];
                System.arraycopy(result, 0, truncated, 0, byteLength);
                result = truncated;
            }
        }
        return result;
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
    }
}
