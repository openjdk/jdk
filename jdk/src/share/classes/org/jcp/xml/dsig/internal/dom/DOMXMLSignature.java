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
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * ===========================================================================
 *
 * (C) Copyright IBM Corp. 2003 All Rights Reserved.
 *
 * ===========================================================================
 */
/*
 * $Id: DOMXMLSignature.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.Provider;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

/**
 * DOM-based implementation of XMLSignature.
 *
 * @author Sean Mullan
 * @author Joyce Leung
 */
public final class DOMXMLSignature extends DOMStructure
    implements XMLSignature {

    private static Logger log = Logger.getLogger("org.jcp.xml.dsig.internal.dom");
    private String id;
    private SignatureValue sv;
    private KeyInfo ki;
    private List objects;
    private SignedInfo si;
    private Document ownerDoc = null;
    private Element localSigElem = null;
    private Element sigElem = null;
    private boolean validationStatus;
    private boolean validated = false;
    private KeySelectorResult ksr;
    private HashMap signatureIdMap;

    static {
        com.sun.org.apache.xml.internal.security.Init.init();
    }

    /**
     * Creates a <code>DOMXMLSignature</code> from the specified components.
     *
     * @param si the <code>SignedInfo</code>
     * @param ki the <code>KeyInfo</code>, or <code>null</code> if not specified
     * @param objs a list of <code>XMLObject</code>s or <code>null</code>
     *  if not specified. The list is copied to protect against subsequent
     *  modification.
     * @param id an optional id (specify <code>null</code> to omit)
     * @param signatureValueId an optional id (specify <code>null</code> to
     *  omit)
     * @throws NullPointerException if <code>si</code> is <code>null</code>
     */
    public DOMXMLSignature(SignedInfo si, KeyInfo ki, List objs, String id,
        String signatureValueId)
    {
        if (si == null) {
            throw new NullPointerException("signedInfo cannot be null");
        }
        this.si = si;
        this.id = id;
        this.sv = new DOMSignatureValue(signatureValueId);
        if (objs == null) {
            this.objects = Collections.EMPTY_LIST;
        } else {
            List objsCopy = new ArrayList(objs);
            for (int i = 0, size = objsCopy.size(); i < size; i++) {
                if (!(objsCopy.get(i) instanceof XMLObject)) {
                    throw new ClassCastException
                        ("objs["+i+"] is not an XMLObject");
                }
            }
            this.objects = Collections.unmodifiableList(objsCopy);
        }
        this.ki = ki;
    }

    /**
     * Creates a <code>DOMXMLSignature</code> from XML.
     *
     * @param sigElem Signature element
     * @throws MarshalException if XMLSignature cannot be unmarshalled
     */
    public DOMXMLSignature(Element sigElem, XMLCryptoContext context,
        Provider provider) throws MarshalException {
        localSigElem = sigElem;
        ownerDoc = localSigElem.getOwnerDocument();

        // get Id attribute, if specified
        id = DOMUtils.getAttributeValue(localSigElem, "Id");

        // unmarshal SignedInfo
        Element siElem = DOMUtils.getFirstChildElement(localSigElem);
        si = new DOMSignedInfo(siElem, context, provider);

        // unmarshal SignatureValue
        Element sigValElem = DOMUtils.getNextSiblingElement(siElem);
        sv = new DOMSignatureValue(sigValElem);

        // unmarshal KeyInfo, if specified
        Element nextSibling = DOMUtils.getNextSiblingElement(sigValElem);
        if (nextSibling != null && nextSibling.getLocalName().equals("KeyInfo")) {
            ki = new DOMKeyInfo(nextSibling, context, provider);
            nextSibling = DOMUtils.getNextSiblingElement(nextSibling);
        }

        // unmarshal Objects, if specified
        if (nextSibling == null) {
            objects = Collections.EMPTY_LIST;
        } else {
            List tempObjects = new ArrayList();
            while (nextSibling != null) {
                tempObjects.add
                    (new DOMXMLObject(nextSibling, context, provider));
                nextSibling = DOMUtils.getNextSiblingElement(nextSibling);
            }
            objects = Collections.unmodifiableList(tempObjects);
        }
    }

    public String getId() {
        return id;
    }

    public KeyInfo getKeyInfo() {
        return ki;
    }

    public SignedInfo getSignedInfo() {
        return si;
    }

    public List getObjects() {
        return objects;
    }

    public SignatureValue getSignatureValue() {
        return sv;
    }

    public KeySelectorResult getKeySelectorResult() {
        return ksr;
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        marshal(parent, null, dsPrefix, context);
    }

    public void marshal(Node parent, Node nextSibling, String dsPrefix,
        DOMCryptoContext context) throws MarshalException {
        ownerDoc = DOMUtils.getOwnerDocument(parent);

        sigElem = DOMUtils.createElement
            (ownerDoc, "Signature", XMLSignature.XMLNS, dsPrefix);

        // append xmlns attribute
        if (dsPrefix == null || dsPrefix.length() == 0) {
            sigElem.setAttributeNS
                ("http://www.w3.org/2000/xmlns/", "xmlns", XMLSignature.XMLNS);
        } else {
            sigElem.setAttributeNS
                ("http://www.w3.org/2000/xmlns/", "xmlns:" + dsPrefix,
                 XMLSignature.XMLNS);
        }

        // create and append SignedInfo element
        ((DOMSignedInfo) si).marshal(sigElem, dsPrefix, context);

        // create and append SignatureValue element
        ((DOMSignatureValue) sv).marshal(sigElem, dsPrefix, context);

        // create and append KeyInfo element if necessary
        if (ki != null) {
            ((DOMKeyInfo) ki).marshal(sigElem, null, dsPrefix, context);
        }

        // create and append Object elements if necessary
        for (int i = 0, size = objects.size(); i < size; i++) {
            ((DOMXMLObject) objects.get(i)).marshal(sigElem, dsPrefix, context);
        }

        // append Id attribute
        DOMUtils.setAttributeID(sigElem, "Id", id);

        parent.insertBefore(sigElem, nextSibling);
    }

    public boolean validate(XMLValidateContext vc)
        throws XMLSignatureException {

        if (vc == null) {
            throw new NullPointerException("validateContext is null");
        }

        if (!(vc instanceof DOMValidateContext)) {
            throw new ClassCastException
                ("validateContext must be of type DOMValidateContext");
        }

        if (validated) {
            return validationStatus;
        }

        // validate the signature
        boolean sigValidity = sv.validate(vc);
        if (!sigValidity) {
            validationStatus = false;
            validated = true;
            return validationStatus;
        }

        // validate all References
        List refs = this.si.getReferences();
        boolean validateRefs = true;
        for (int i = 0, size = refs.size(); validateRefs && i < size; i++) {
            Reference ref = (Reference) refs.get(i);
            boolean refValid = ref.validate(vc);
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Reference[" + ref.getURI() + "] is valid: "
                    + refValid);
            }
            validateRefs &= refValid;
        }
        if (!validateRefs) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Couldn't validate the References");
            }
            validationStatus = false;
            validated = true;
            return validationStatus;
        }

        // validate Manifests, if property set
        boolean validateMans = true;
        if (Boolean.TRUE.equals(vc.getProperty
            ("org.jcp.xml.dsig.validateManifests"))) {

            for (int i=0, size=objects.size(); validateMans && i < size; i++) {
                XMLObject xo = (XMLObject) objects.get(i);
                List content = xo.getContent();
                int csize = content.size();
                for (int j = 0; validateMans && j < csize; j++) {
                    XMLStructure xs = (XMLStructure) content.get(j);
                    if (xs instanceof Manifest) {
                        if (log.isLoggable(Level.FINE)) {
                            log.log(Level.FINE, "validating manifest");
                        }
                        Manifest man = (Manifest) xs;
                        List manRefs = man.getReferences();
                        int rsize = manRefs.size();
                        for (int k = 0; validateMans && k < rsize; k++) {
                            Reference ref = (Reference) manRefs.get(k);
                            boolean refValid = ref.validate(vc);
                            if (log.isLoggable(Level.FINE)) {
                                log.log(Level.FINE, "Manifest ref["
                                    + ref.getURI() + "] is valid: " + refValid);
                            }
                            validateMans &= refValid;
                        }
                    }
                }
            }
        }

        validationStatus = validateMans;
        validated = true;
        return validationStatus;
    }

    public void sign(XMLSignContext signContext)
        throws MarshalException, XMLSignatureException {
        if (signContext == null) {
            throw new NullPointerException("signContext cannot be null");
        }
        DOMSignContext context = (DOMSignContext) signContext;
        if (context != null) {
            marshal(context.getParent(), context.getNextSibling(),
                DOMUtils.getSignaturePrefix(context), context);
        }

        // generate references and signature value
        List allReferences = new ArrayList();

        // traverse the Signature and register all objects with IDs that
        // may contain References
        signatureIdMap = new HashMap();
        signatureIdMap.put(id, this);
        signatureIdMap.put(si.getId(), si);
        List refs = si.getReferences();
        for (int i = 0, size = refs.size(); i < size; i++) {
            Reference ref = (Reference) refs.get(i);
            signatureIdMap.put(ref.getId(), ref);
        }
        for (int i = 0, size = objects.size(); i < size; i++) {
            XMLObject obj = (XMLObject) objects.get(i);
            signatureIdMap.put(obj.getId(), obj);
            List content = obj.getContent();
            for (int j = 0, csize = content.size(); j < csize; j++) {
                XMLStructure xs = (XMLStructure) content.get(j);
                if (xs instanceof Manifest) {
                    Manifest man = (Manifest) xs;
                    signatureIdMap.put(man.getId(), man);
                    List manRefs = man.getReferences();
                    for (int k = 0, msize = manRefs.size(); k < msize; k++) {
                        Reference ref = (Reference) manRefs.get(k);
                        allReferences.add(ref);
                        signatureIdMap.put(ref.getId(), ref);
                    }
                }
            }
        }
        // always add SignedInfo references after Manifest references so
        // that Manifest reference are digested first
        allReferences.addAll(si.getReferences());

        // generate/digest each reference
        for (int i = 0, size = allReferences.size(); i < size; i++) {
            DOMReference ref = (DOMReference) allReferences.get(i);
            digestReference(ref, signContext);
        }

        // do final sweep to digest any references that were skipped or missed
        for (int i = 0, size = allReferences.size(); i < size; i++) {
            DOMReference ref = (DOMReference) allReferences.get(i);
            if (ref.isDigested()) {
                continue;
            }
            ref.digest(signContext);
        }

        Key signingKey = null;
        KeySelectorResult ksr = null;
        try {
            ksr = signContext.getKeySelector().select
                (ki, KeySelector.Purpose.SIGN,
                si.getSignatureMethod(), signContext);
            signingKey = ksr.getKey();
            if (signingKey == null) {
                throw new XMLSignatureException("the keySelector did not " +
                "find a signing key");
            }
        } catch (KeySelectorException kse) {
            throw new XMLSignatureException("cannot find signing key", kse);
        }

        // calculate signature value
        byte[] val = null;
        try {
            val = ((DOMSignatureMethod) si.getSignatureMethod()).sign
                (signingKey, (DOMSignedInfo) si, signContext);
        } catch (InvalidKeyException ike) {
            throw new XMLSignatureException(ike);
        }

        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "SignatureValue = " + val);
        }
        ((DOMSignatureValue) sv).setValue(val);

        this.localSigElem = sigElem;
        this.ksr = ksr;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof XMLSignature)) {
            return false;
        }
        XMLSignature osig = (XMLSignature) o;

        boolean idEqual =
            (id == null ? osig.getId() == null : id.equals(osig.getId()));
        boolean keyInfoEqual =
            (ki == null ? osig.getKeyInfo() == null :
             ki.equals(osig.getKeyInfo()));

        return (idEqual && keyInfoEqual &&
            sv.equals(osig.getSignatureValue()) &&
            si.equals(osig.getSignedInfo()) &&
            objects.equals(osig.getObjects()));
    }

    private void digestReference(DOMReference ref, XMLSignContext signContext)
        throws XMLSignatureException {
        if (ref.isDigested()) {
            return;
        }
        // check dependencies
        String uri = ref.getURI();
        if (Utils.sameDocumentURI(uri)) {
            String id = Utils.parseIdFromSameDocumentURI(uri);
            if (id != null && signatureIdMap.containsKey(id)) {
                Object obj = signatureIdMap.get(id);
                if (obj instanceof DOMReference) {
                    digestReference((DOMReference) obj, signContext);
                } else if (obj instanceof Manifest) {
                    Manifest man = (Manifest) obj;
                    List manRefs = man.getReferences();
                    for (int i = 0, size = manRefs.size(); i < size; i++) {
                        digestReference
                            ((DOMReference) manRefs.get(i), signContext);
                    }
                }
            }
            // if uri="" and there are XPath Transforms, there may be
            // reference dependencies in the XPath Transform - so be on
            // the safe side, and skip and do at end in the final sweep
            if (uri.length() == 0) {
                List transforms = ref.getTransforms();
                for (int i = 0, size = transforms.size(); i < size; i++) {
                    Transform transform = (Transform) transforms.get(i);
                    String transformAlg = transform.getAlgorithm();
                    if (transformAlg.equals(Transform.XPATH) ||
                        transformAlg.equals(Transform.XPATH2)) {
                        return;
                    }
                }
            }
        }
        ref.digest(signContext);
    }

    public class DOMSignatureValue extends DOMStructure
        implements SignatureValue {

        private String id;
        private byte[] value;
        private String valueBase64;
        private Element sigValueElem;
        private boolean validated = false;
        private boolean validationStatus;

        DOMSignatureValue(String id) {
            this.id = id;
        }

        DOMSignatureValue(Element sigValueElem) throws MarshalException {
            try {
                // base64 decode signatureValue
                value = Base64.decode(sigValueElem);
            } catch (Base64DecodingException bde) {
                throw new MarshalException(bde);
            }

            id = DOMUtils.getAttributeValue(sigValueElem, "Id");
            this.sigValueElem = sigValueElem;
        }

        public String getId() {
            return id;
        }

        public byte[] getValue() {
            return (value == null) ? null : (byte[]) value.clone();
        }

        public boolean validate(XMLValidateContext validateContext)
            throws XMLSignatureException {

            if (validateContext == null) {
                throw new NullPointerException("context cannot be null");
            }

            if (validated) {
                return validationStatus;
            }

            // get validating key
            SignatureMethod sm = si.getSignatureMethod();
            Key validationKey = null;
            KeySelectorResult ksResult;
            try {
                ksResult = validateContext.getKeySelector().select
                    (ki, KeySelector.Purpose.VERIFY, sm, validateContext);
                validationKey = ksResult.getKey();
                if (validationKey == null) {
                    throw new XMLSignatureException("the keyselector did " +
                        "not find a validation key");
                }
            } catch (KeySelectorException kse) {
                throw new XMLSignatureException("cannot find validation " +
                    "key", kse);
            }

            // canonicalize SignedInfo and verify signature
            try {
                validationStatus = ((DOMSignatureMethod) sm).verify
                    (validationKey, (DOMSignedInfo) si, value, validateContext);
            } catch (Exception e) {
                throw new XMLSignatureException(e);
            }

            validated = true;
            ksr = ksResult;
            return validationStatus;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof SignatureValue)) {
                return false;
            }
            SignatureValue osv = (SignatureValue) o;

            boolean idEqual =
                (id == null ? osv.getId() == null : id.equals(osv.getId()));

            //XXX compare signature values?
            return idEqual;
        }

        public void marshal(Node parent, String dsPrefix,
            DOMCryptoContext context) throws MarshalException {

            // create SignatureValue element
            sigValueElem = DOMUtils.createElement
                (ownerDoc, "SignatureValue", XMLSignature.XMLNS, dsPrefix);
            if (valueBase64 != null) {
                sigValueElem.appendChild(ownerDoc.createTextNode(valueBase64));
            }

            // append Id attribute, if specified
            DOMUtils.setAttributeID(sigValueElem, "Id", id);
            parent.appendChild(sigValueElem);
        }

        void setValue(byte[] value) {
            this.value = value;
            valueBase64 = Base64.encode(value);
            sigValueElem.appendChild(ownerDoc.createTextNode(valueBase64));
        }
    }
}
