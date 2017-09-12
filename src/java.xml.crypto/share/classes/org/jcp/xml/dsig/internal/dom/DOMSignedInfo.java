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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMSignedInfo.java 1333415 2012-05-03 12:03:51Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.Provider;
import java.util.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.UnsyncBufferedOutputStream;

/**
 * DOM-based implementation of SignedInfo.
 *
 * @author Sean Mullan
 */
public final class DOMSignedInfo extends DOMStructure implements SignedInfo {

    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger("org.jcp.xml.dsig.internal.dom");

    private List<Reference> references;
    private CanonicalizationMethod canonicalizationMethod;
    private SignatureMethod signatureMethod;
    private String id;
    private Document ownerDoc;
    private Element localSiElem;
    private InputStream canonData;

    /**
     * Creates a <code>DOMSignedInfo</code> from the specified parameters. Use
     * this constructor when the <code>Id</code> is not specified.
     *
     * @param cm the canonicalization method
     * @param sm the signature method
     * @param references the list of references. The list is copied.
     * @throws NullPointerException if
     *    <code>cm</code>, <code>sm</code>, or <code>references</code> is
     *    <code>null</code>
     * @throws IllegalArgumentException if <code>references</code> is empty
     * @throws ClassCastException if any of the references are not of
     *    type <code>Reference</code>
     */
    public DOMSignedInfo(CanonicalizationMethod cm, SignatureMethod sm,
                         List<? extends Reference> references) {
        if (cm == null || sm == null || references == null) {
            throw new NullPointerException();
        }
        this.canonicalizationMethod = cm;
        this.signatureMethod = sm;
        List<Reference> tempList =
            Collections.checkedList(new ArrayList<Reference>(),
                                    Reference.class);
        tempList.addAll(references);
        if (tempList.isEmpty()) {
            throw new IllegalArgumentException("references cannot be empty");
        }
        this.references = Collections.unmodifiableList(tempList);
    }

    /**
     * Creates a <code>DOMSignedInfo</code> from the specified parameters.
     *
     * @param cm the canonicalization method
     * @param sm the signature method
     * @param references the list of references. The list is copied.
     * @param id an optional identifer that will allow this
     *    <code>SignedInfo</code> to be referenced by other signatures and
     *    objects
     * @throws NullPointerException if <code>cm</code>, <code>sm</code>,
     *    or <code>references</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>references</code> is empty
     * @throws ClassCastException if any of the references are not of
     *    type <code>Reference</code>
     */
    public DOMSignedInfo(CanonicalizationMethod cm, SignatureMethod sm,
                         List<? extends Reference> references, String id) {
        this(cm, sm, references);
        this.id = id;
    }

    /**
     * Creates a <code>DOMSignedInfo</code> from an element.
     *
     * @param siElem a SignedInfo element
     */
    public DOMSignedInfo(Element siElem, XMLCryptoContext context, Provider provider)
        throws MarshalException {
        localSiElem = siElem;
        ownerDoc = siElem.getOwnerDocument();

        // get Id attribute, if specified
        id = DOMUtils.getAttributeValue(siElem, "Id");

        // unmarshal CanonicalizationMethod
        Element cmElem = DOMUtils.getFirstChildElement(siElem,
                                                       "CanonicalizationMethod");
        canonicalizationMethod = new DOMCanonicalizationMethod(cmElem, context,
                                                               provider);

        // unmarshal SignatureMethod
        Element smElem = DOMUtils.getNextSiblingElement(cmElem,
                                                        "SignatureMethod");
        signatureMethod = DOMSignatureMethod.unmarshal(smElem);

        boolean secVal = Utils.secureValidation(context);

        String signatureMethodAlgorithm = signatureMethod.getAlgorithm();
        if (secVal && Policy.restrictAlg(signatureMethodAlgorithm)) {
            throw new MarshalException(
                "It is forbidden to use algorithm " + signatureMethodAlgorithm +
                " when secure validation is enabled"
            );
        }

        // unmarshal References
        ArrayList<Reference> refList = new ArrayList<Reference>(5);
        Element refElem = DOMUtils.getNextSiblingElement(smElem, "Reference");
        refList.add(new DOMReference(refElem, context, provider));

        refElem = DOMUtils.getNextSiblingElement(refElem);
        while (refElem != null) {
            String name = refElem.getLocalName();
            if (!name.equals("Reference")) {
                throw new MarshalException("Invalid element name: " +
                                           name + ", expected Reference");
            }
            refList.add(new DOMReference(refElem, context, provider));

            if (secVal && Policy.restrictNumReferences(refList.size())) {
                String error = "A maximum of " + Policy.maxReferences()
                    + " references per Manifest are allowed when"
                    + " secure validation is enabled";
                throw new MarshalException(error);
            }
            refElem = DOMUtils.getNextSiblingElement(refElem);
        }
        references = Collections.unmodifiableList(refList);
    }

    public CanonicalizationMethod getCanonicalizationMethod() {
        return canonicalizationMethod;
    }

    public SignatureMethod getSignatureMethod() {
        return signatureMethod;
    }

    public String getId() {
        return id;
    }

    public List<Reference> getReferences() {
        return references;
    }

    public InputStream getCanonicalizedData() {
        return canonData;
    }

    public void canonicalize(XMLCryptoContext context, ByteArrayOutputStream bos)
        throws XMLSignatureException {
        if (context == null) {
            throw new NullPointerException("context cannot be null");
        }

        OutputStream os = new UnsyncBufferedOutputStream(bos);

        DOMSubTreeData subTree = new DOMSubTreeData(localSiElem, true);
        try {
            ((DOMCanonicalizationMethod)
                canonicalizationMethod).canonicalize(subTree, context, os);
        } catch (TransformException te) {
            throw new XMLSignatureException(te);
        }

        try {
            os.flush();
        } catch (IOException e) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, e.getMessage(), e);
            }
            // Impossible
        }

        byte[] signedInfoBytes = bos.toByteArray();

        // this whole block should only be done if logging is enabled
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Canonicalized SignedInfo:");
            StringBuilder sb = new StringBuilder(signedInfoBytes.length);
            for (int i = 0; i < signedInfoBytes.length; i++) {
                sb.append((char)signedInfoBytes[i]);
            }
            log.log(java.util.logging.Level.FINE, sb.toString());
            log.log(java.util.logging.Level.FINE, "Data to be signed/verified:" + Base64.encode(signedInfoBytes));
        }

        this.canonData = new ByteArrayInputStream(signedInfoBytes);

        try {
            os.close();
        } catch (IOException e) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, e.getMessage(), e);
            }
            // Impossible
        }
    }

    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException
    {
        ownerDoc = DOMUtils.getOwnerDocument(parent);
        Element siElem = DOMUtils.createElement(ownerDoc, "SignedInfo",
                                                XMLSignature.XMLNS, dsPrefix);

        // create and append CanonicalizationMethod element
        DOMCanonicalizationMethod dcm =
            (DOMCanonicalizationMethod)canonicalizationMethod;
        dcm.marshal(siElem, dsPrefix, context);

        // create and append SignatureMethod element
        ((DOMStructure)signatureMethod).marshal(siElem, dsPrefix, context);

        // create and append Reference elements
        for (Reference reference : references) {
            ((DOMReference)reference).marshal(siElem, dsPrefix, context);
        }

        // append Id attribute
        DOMUtils.setAttributeID(siElem, "Id", id);

        parent.appendChild(siElem);
        localSiElem = siElem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SignedInfo)) {
            return false;
        }
        SignedInfo osi = (SignedInfo)o;

        boolean idEqual = (id == null ? osi.getId() == null
                                      : id.equals(osi.getId()));

        return (canonicalizationMethod.equals(osi.getCanonicalizationMethod())
                && signatureMethod.equals(osi.getSignatureMethod()) &&
                references.equals(osi.getReferences()) && idEqual);
    }

    @Override
    public int hashCode() {
        int result = 17;
        if (id != null) {
            result = 31 * result + id.hashCode();
        }
        result = 31 * result + canonicalizationMethod.hashCode();
        result = 31 * result + signatureMethod.hashCode();
        result = 31 * result + references.hashCode();

        return result;
    }
}
