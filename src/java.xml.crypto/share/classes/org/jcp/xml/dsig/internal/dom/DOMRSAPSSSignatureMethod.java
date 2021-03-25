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
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;

import java.io.IOException;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.jcp.xml.dsig.internal.SignerOutputStream;
import com.sun.org.apache.xml.internal.security.algorithms.implementations.SignatureBaseRSA.SignatureRSASSAPSS.DigestAlgorithm;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;

/**
 * DOM-based abstract implementation of SignatureMethod for RSA-PSS.
 *
 */
public abstract class DOMRSAPSSSignatureMethod extends AbstractDOMSignatureMethod {

    private static final String DOM_SIGNATURE_PROVIDER = "org.jcp.xml.dsig.internal.dom.SignatureProvider";

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(DOMRSAPSSSignatureMethod.class);

    private final SignatureMethodParameterSpec params;
    private Signature signature;

    // see RFC 6931 for these algorithm definitions
    static final String RSA_PSS =
        "http://www.w3.org/2007/05/xmldsig-more#rsa-pss";

    private int trailerField = 1;
    private int saltLength = 32;
    private String digestName = "SHA-256";

    /**
     * Creates a {@code DOMSignatureMethod}.
     *
     * @param params the algorithm-specific params (may be {@code null})
     * @throws InvalidAlgorithmParameterException if the parameters are not
     *    appropriate for this signature method
     */
    DOMRSAPSSSignatureMethod(AlgorithmParameterSpec params)
        throws InvalidAlgorithmParameterException
    {
        if (params != null &&
            !(params instanceof SignatureMethodParameterSpec)) {
            throw new InvalidAlgorithmParameterException
                ("params must be of type SignatureMethodParameterSpec");
        }
        if (params == null) {
            params = getDefaultParameterSpec();
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
    DOMRSAPSSSignatureMethod(Element smElem) throws MarshalException {
        Element paramsElem = DOMUtils.getFirstChildElement(smElem);
        if (paramsElem != null) {
            params = unmarshalParams(paramsElem);
        } else {
            params = getDefaultParameterSpec();
        }
        try {
            checkParams(params);
        } catch (InvalidAlgorithmParameterException iape) {
            throw new MarshalException(iape);
        }
    }

    @Override
    void checkParams(SignatureMethodParameterSpec params)
        throws InvalidAlgorithmParameterException
    {
        if (params != null) {
            if (!(params instanceof RSAPSSParameterSpec)) {
                throw new InvalidAlgorithmParameterException
                    ("params must be of type RSAPSSParameterSpec");
            }

            if (((RSAPSSParameterSpec)params).getTrailerField() > 0) {
                trailerField = ((RSAPSSParameterSpec)params).getTrailerField();
                LOG.debug("Setting trailerField from RSAPSSParameterSpec to: {}", trailerField);
            }
            if (((RSAPSSParameterSpec)params).getSaltLength() > 0) {
                saltLength = ((RSAPSSParameterSpec)params).getSaltLength();
                LOG.debug("Setting saltLength from RSAPSSParameterSpec to: {}", saltLength);
            }
            if (((RSAPSSParameterSpec)params).getDigestName() != null) {
                digestName = ((RSAPSSParameterSpec)params).getDigestName();
                LOG.debug("Setting digestName from RSAPSSParameterSpec to: {}", digestName);
            }
        }
    }

    public final AlgorithmParameterSpec getParameterSpec() {
        return params;
    }

    void marshalParams(Element parent, String prefix)
        throws MarshalException
    {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element rsaPssParamsElement = ownerDoc.createElementNS(Constants.XML_DSIG_NS_MORE_07_05, "pss" + ":" + Constants._TAG_RSAPSSPARAMS);
        rsaPssParamsElement.setAttributeNS(Constants.NamespaceSpecNS, "xmlns:" + "pss", Constants.XML_DSIG_NS_MORE_07_05);

        Element digestMethodElement = DOMUtils.createElement(rsaPssParamsElement.getOwnerDocument(), Constants._TAG_DIGESTMETHOD,
                                                             XMLSignature.XMLNS, prefix);
        try {
            digestMethodElement.setAttributeNS(null, Constants._ATT_ALGORITHM, DigestAlgorithm.fromDigestAlgorithm(digestName).getXmlDigestAlgorithm());
        } catch (DOMException | com.sun.org.apache.xml.internal.security.signature.XMLSignatureException e) {
            throw new MarshalException("Invalid digest name supplied: " + digestName);
        }
        rsaPssParamsElement.appendChild(digestMethodElement);

        Element saltLengthElement = rsaPssParamsElement.getOwnerDocument().createElementNS(Constants.XML_DSIG_NS_MORE_07_05, "pss" + ":" + Constants._TAG_SALTLENGTH);
        Text saltLengthText = rsaPssParamsElement.getOwnerDocument().createTextNode(String.valueOf(saltLength));
        saltLengthElement.appendChild(saltLengthText);

        rsaPssParamsElement.appendChild(saltLengthElement);

        Element trailerFieldElement = rsaPssParamsElement.getOwnerDocument().createElementNS(Constants.XML_DSIG_NS_MORE_07_05, "pss" + ":" + Constants._TAG_TRAILERFIELD);
        Text trailerFieldText = rsaPssParamsElement.getOwnerDocument().createTextNode(String.valueOf(trailerField));
        trailerFieldElement.appendChild(trailerFieldText);

        rsaPssParamsElement.appendChild(trailerFieldElement);

        parent.appendChild(rsaPssParamsElement);
    }

    SignatureMethodParameterSpec unmarshalParams(Element paramsElem)
        throws MarshalException
    {
        if (paramsElem != null) {
            Element saltLengthNode = XMLUtils.selectNode(paramsElem.getFirstChild(), Constants.XML_DSIG_NS_MORE_07_05, Constants._TAG_SALTLENGTH, 0);
            Element trailerFieldNode = XMLUtils.selectNode(paramsElem.getFirstChild(), Constants.XML_DSIG_NS_MORE_07_05, Constants._TAG_TRAILERFIELD, 0);
            int trailerField = 1;
            if (trailerFieldNode != null) {
                try {
                    trailerField = Integer.parseInt(trailerFieldNode.getTextContent());
                } catch (NumberFormatException ex) {
                    throw new MarshalException("Invalid trailer field supplied: " + trailerFieldNode.getTextContent());
                }
            }
            String xmlAlgorithm = XMLUtils.selectDsNode(paramsElem.getFirstChild(), Constants._TAG_DIGESTMETHOD, 0).getAttribute(Constants._ATT_ALGORITHM);
            DigestAlgorithm digestAlgorithm;
            try {
                digestAlgorithm = DigestAlgorithm.fromXmlDigestAlgorithm(xmlAlgorithm);
            } catch (com.sun.org.apache.xml.internal.security.signature.XMLSignatureException e) {
                throw new MarshalException("Invalid digest algorithm supplied: " + xmlAlgorithm);
            }
            String digestName = digestAlgorithm.getDigestAlgorithm();

            RSAPSSParameterSpec params = new RSAPSSParameterSpec();
            params.setTrailerField(trailerField);
            try {
                int saltLength = saltLengthNode == null ? digestAlgorithm.getSaltLength() : Integer.parseInt(saltLengthNode.getTextContent());
                params.setSaltLength(saltLength);
            } catch (NumberFormatException ex) {
                throw new MarshalException("Invalid salt length supplied: " + saltLengthNode.getTextContent());
            }
            params.setDigestName(digestName);
            return params;
        }
        return getDefaultParameterSpec();
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
                Provider p = (Provider)context.getProperty(DOM_SIGNATURE_PROVIDER);
                signature = (p == null)
                    ? Signature.getInstance(getJCAAlgorithm())
                    : Signature.getInstance(getJCAAlgorithm(), p);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initVerify((PublicKey)key);
        try {
            signature.setParameter(new PSSParameterSpec(digestName, "MGF1", new MGF1ParameterSpec(digestName), saltLength, trailerField));
        } catch (InvalidAlgorithmParameterException e) {
            throw new XMLSignatureException(e);
        }
        LOG.debug("Signature provider: {}", signature.getProvider());
        LOG.debug("Verifying with key: {}", key);
        LOG.debug("JCA Algorithm: {}", getJCAAlgorithm());
        LOG.debug("Signature Bytes length: {}", sig.length);

        try (SignerOutputStream outputStream = new SignerOutputStream(signature)) {
            ((DOMSignedInfo)si).canonicalize(context, outputStream);

            return signature.verify(sig);
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
                Provider p = (Provider)context.getProperty(DOM_SIGNATURE_PROVIDER);
                signature = (p == null)
                    ? Signature.getInstance(getJCAAlgorithm())
                    : Signature.getInstance(getJCAAlgorithm(), p);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initSign((PrivateKey)key);
        try {
            signature.setParameter(new PSSParameterSpec(digestName, "MGF1", new MGF1ParameterSpec(digestName), saltLength, trailerField));
        } catch (InvalidAlgorithmParameterException e) {
            throw new XMLSignatureException(e);
        }
        LOG.debug("Signature provider: {}", signature.getProvider());
        LOG.debug("Signing with key: {}", key);
        LOG.debug("JCA Algorithm: {}", getJCAAlgorithm());

        try (SignerOutputStream outputStream = new SignerOutputStream(signature)) {
            ((DOMSignedInfo)si).canonicalize(context, outputStream);

            return signature.sign();
        } catch (SignatureException | IOException e) {
            throw new XMLSignatureException(e);
        }
    }

    @Override
    boolean paramsEqual(AlgorithmParameterSpec spec) {
        return getParameterSpec().equals(spec);
    }

    private SignatureMethodParameterSpec getDefaultParameterSpec() {
        RSAPSSParameterSpec params = new RSAPSSParameterSpec();
        params.setTrailerField(trailerField);
        params.setSaltLength(saltLength);
        params.setDigestName(digestName);
        return params;
    }

    static final class RSAPSS extends DOMRSAPSSSignatureMethod {
        RSAPSS(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super(params);
        }
        RSAPSS(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        @Override
        public String getAlgorithm() {
            return RSA_PSS;
        }
        @Override
        String getJCAAlgorithm() {
            return "RSASSA-PSS";
        }
        @Override
        Type getAlgorithmType() {
            return Type.RSA;
        }
    }

}
