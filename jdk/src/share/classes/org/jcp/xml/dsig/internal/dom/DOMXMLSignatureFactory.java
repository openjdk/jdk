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
 * Copyright 2005-2008 Sun Microsystems, Inc. All rights reserved.
 */
/*
 * $Id: DOMXMLSignatureFactory.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * DOM-based implementation of XMLSignatureFactory.
 *
 * @author Sean Mullan
 */
public final class DOMXMLSignatureFactory extends XMLSignatureFactory {

    /**
     * Initializes a new instance of this class.
     */
    public DOMXMLSignatureFactory() {}

    public XMLSignature newXMLSignature(SignedInfo si, KeyInfo ki) {
        return new DOMXMLSignature(si, ki, null, null, null);
    }

    public XMLSignature newXMLSignature(SignedInfo si, KeyInfo ki,
        List objects, String id, String signatureValueId) {
        return new DOMXMLSignature(si, ki, objects, id, signatureValueId);
    }

    public Reference newReference(String uri, DigestMethod dm) {
        return newReference(uri, dm, null, null, null);
    }

    public Reference newReference(String uri, DigestMethod dm, List transforms,
        String type, String id) {
        return new DOMReference(uri, type, dm, transforms, id, getProvider());
    }

    public Reference newReference(String uri, DigestMethod dm,
        List appliedTransforms, Data result, List transforms, String type,
        String id) {
        if (appliedTransforms == null) {
            throw new NullPointerException("appliedTransforms cannot be null");
        }
        if (appliedTransforms.isEmpty()) {
            throw new NullPointerException("appliedTransforms cannot be empty");
        }
        if (result == null) {
            throw new NullPointerException("result cannot be null");
        }
        return new DOMReference
            (uri, type, dm, appliedTransforms, result, transforms, id, getProvider());
    }

    public Reference newReference(String uri, DigestMethod dm, List transforms,
        String type, String id, byte[] digestValue) {
        if (digestValue == null) {
            throw new NullPointerException("digestValue cannot be null");
        }
        return new DOMReference
            (uri, type, dm, null, null, transforms, id, digestValue, getProvider());
    }

    public SignedInfo newSignedInfo(CanonicalizationMethod cm,
        SignatureMethod sm, List references) {
        return newSignedInfo(cm, sm, references, null);
    }

    public SignedInfo newSignedInfo(CanonicalizationMethod cm,
        SignatureMethod sm, List references, String id) {
        return new DOMSignedInfo(cm, sm, references, id);
    }

    // Object factory methods
    public XMLObject newXMLObject(List content, String id, String mimeType,
        String encoding) {
        return new DOMXMLObject(content, id, mimeType, encoding);
    }

    public Manifest newManifest(List references) {
        return newManifest(references, null);
    }

    public Manifest newManifest(List references, String id) {
        return new DOMManifest(references, id);
    }

    public SignatureProperties newSignatureProperties(List props, String id) {
        return new DOMSignatureProperties(props, id);
    }

    public SignatureProperty newSignatureProperty
        (List info, String target, String id) {
        return new DOMSignatureProperty(info, target, id);
    }

    public XMLSignature unmarshalXMLSignature(XMLValidateContext context)
        throws MarshalException {

        if (context == null) {
            throw new NullPointerException("context cannot be null");
        }
        return unmarshal(((DOMValidateContext) context).getNode(), context);
    }

    public XMLSignature unmarshalXMLSignature(XMLStructure xmlStructure)
        throws MarshalException {

        if (xmlStructure == null) {
            throw new NullPointerException("xmlStructure cannot be null");
        }
        return unmarshal
            (((javax.xml.crypto.dom.DOMStructure) xmlStructure).getNode(),
             null);
    }

    private XMLSignature unmarshal(Node node, XMLValidateContext context)
        throws MarshalException {

        node.normalize();

        Element element = null;
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            element = ((Document) node).getDocumentElement();
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            element = (Element) node;
        } else {
            throw new MarshalException
                ("Signature element is not a proper Node");
        }

        // check tag
        String tag = element.getLocalName();
        if (tag == null) {
            throw new MarshalException("Document implementation must " +
                "support DOM Level 2 and be namespace aware");
        }
        if (tag.equals("Signature")) {
            return new DOMXMLSignature(element, context, getProvider());
        } else {
            throw new MarshalException("invalid Signature tag: " + tag);
        }
    }

    public boolean isFeatureSupported(String feature) {
        if (feature == null) {
            throw new NullPointerException();
        } else {
            return false;
        }
    }

    public DigestMethod newDigestMethod(String algorithm,
        DigestMethodParameterSpec params) throws NoSuchAlgorithmException,
        InvalidAlgorithmParameterException {
        if (algorithm == null) {
            throw new NullPointerException();
        }
        if (algorithm.equals(DigestMethod.SHA1)) {
            return new DOMDigestMethod.SHA1(params);
        } else if (algorithm.equals(DigestMethod.SHA256)) {
            return new DOMDigestMethod.SHA256(params);
        } else if (algorithm.equals(DOMDigestMethod.SHA384)) {
            return new DOMDigestMethod.SHA384(params);
        } else if (algorithm.equals(DigestMethod.SHA512)) {
            return new DOMDigestMethod.SHA512(params);
        } else {
            throw new NoSuchAlgorithmException("unsupported algorithm");
        }
    }

    public SignatureMethod newSignatureMethod(String algorithm,
        SignatureMethodParameterSpec params) throws NoSuchAlgorithmException,
        InvalidAlgorithmParameterException {
        if (algorithm == null) {
            throw new NullPointerException();
        }
        if (algorithm.equals(SignatureMethod.RSA_SHA1)) {
            return new DOMSignatureMethod.SHA1withRSA(params);
        } else if (algorithm.equals(DOMSignatureMethod.RSA_SHA256)) {
            return new DOMSignatureMethod.SHA256withRSA(params);
        } else if (algorithm.equals(DOMSignatureMethod.RSA_SHA384)) {
            return new DOMSignatureMethod.SHA384withRSA(params);
        } else if (algorithm.equals(DOMSignatureMethod.RSA_SHA512)) {
            return new DOMSignatureMethod.SHA512withRSA(params);
        } else if (algorithm.equals(SignatureMethod.DSA_SHA1)) {
            return new DOMSignatureMethod.SHA1withDSA(params);
        } else if (algorithm.equals(SignatureMethod.HMAC_SHA1)) {
            return new DOMHMACSignatureMethod.SHA1(params);
        } else if (algorithm.equals(DOMSignatureMethod.HMAC_SHA256)) {
            return new DOMHMACSignatureMethod.SHA256(params);
        } else if (algorithm.equals(DOMSignatureMethod.HMAC_SHA384)) {
            return new DOMHMACSignatureMethod.SHA384(params);
        } else if (algorithm.equals(DOMSignatureMethod.HMAC_SHA512)) {
            return new DOMHMACSignatureMethod.SHA512(params);
        } else {
            throw new NoSuchAlgorithmException("unsupported algorithm");
        }
    }

    public Transform newTransform(String algorithm,
        TransformParameterSpec params) throws NoSuchAlgorithmException,
        InvalidAlgorithmParameterException {
        TransformService spi;
        try {
            spi = TransformService.getInstance(algorithm, "DOM");
        } catch (NoSuchAlgorithmException nsae) {
            spi = TransformService.getInstance(algorithm, "DOM", getProvider());
        }
        spi.init(params);
        return new DOMTransform(spi);
    }

    public Transform newTransform(String algorithm,
        XMLStructure params) throws NoSuchAlgorithmException,
        InvalidAlgorithmParameterException {
        TransformService spi;
        try {
            spi = TransformService.getInstance(algorithm, "DOM");
        } catch (NoSuchAlgorithmException nsae) {
            spi = TransformService.getInstance(algorithm, "DOM", getProvider());
        }
        if (params == null) {
            spi.init(null);
        } else {
            spi.init(params, null);
        }
        return new DOMTransform(spi);
    }

    public CanonicalizationMethod newCanonicalizationMethod(String algorithm,
        C14NMethodParameterSpec params) throws NoSuchAlgorithmException,
        InvalidAlgorithmParameterException {
        TransformService spi;
        try {
            spi = TransformService.getInstance(algorithm, "DOM");
        } catch (NoSuchAlgorithmException nsae) {
            spi = TransformService.getInstance(algorithm, "DOM", getProvider());
        }
        spi.init(params);
        return new DOMCanonicalizationMethod(spi);
    }

    public CanonicalizationMethod newCanonicalizationMethod(String algorithm,
        XMLStructure params) throws NoSuchAlgorithmException,
        InvalidAlgorithmParameterException {
        TransformService spi;
        try {
            spi = TransformService.getInstance(algorithm, "DOM");
        } catch (NoSuchAlgorithmException nsae) {
            spi = TransformService.getInstance(algorithm, "DOM", getProvider());
        }
        if (params == null) {
            spi.init(null);
        } else {
            spi.init(params, null);
        }
        return new DOMCanonicalizationMethod(spi);
    }

    public URIDereferencer getURIDereferencer() {
        return DOMURIDereferencer.INSTANCE;
    }
}
