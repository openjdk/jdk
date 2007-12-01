/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * $Id: DOMTransform.java,v 1.25 2005/05/10 18:15:34 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;

/**
 * DOM-based abstract implementation of Transform.
 *
 * @author Sean Mullan
 */
public class DOMTransform extends DOMStructure implements Transform {

    protected TransformService spi;

    /**
     * Creates a <code>DOMTransform</code>.
     *
     * @param spi the TransformService
     */
    public DOMTransform(TransformService spi) {
        this.spi = spi;
    }

    /**
     * Creates a <code>DOMTransform</code> from an element. This constructor
     * invokes the abstract {@link #unmarshalParams unmarshalParams} method to
     * unmarshal any algorithm-specific input parameters.
     *
     * @param transElem a Transform element
     */
    public DOMTransform(Element transElem, XMLCryptoContext context)
        throws MarshalException {
        Document ownerDoc = transElem.getOwnerDocument();
        String algorithm = DOMUtils.getAttributeValue(transElem, "Algorithm");
        try {
            spi = TransformService.getInstance(algorithm, "DOM");
        } catch (NoSuchAlgorithmException e) {
            throw new MarshalException(e);
        }
        try {
            spi.init(new javax.xml.crypto.dom.DOMStructure(transElem), context);
        } catch (InvalidAlgorithmParameterException iape) {
            throw new MarshalException(iape);
        }
    }

    public final AlgorithmParameterSpec getParameterSpec() {
        return spi.getParameterSpec();
    }

    public final String getAlgorithm() {
        return spi.getAlgorithm();
    }

    /**
     * This method invokes the abstract {@link #marshalParams marshalParams}
     * method to marshal any algorithm-specific parameters.
     */
    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element transformElem = null;
        if (parent.getLocalName().equals("Transforms")) {
            transformElem = DOMUtils.createElement
                (ownerDoc, "Transform", XMLSignature.XMLNS, dsPrefix);
        } else {
            transformElem = DOMUtils.createElement
            (ownerDoc, "CanonicalizationMethod", XMLSignature.XMLNS, dsPrefix);
        }
        DOMUtils.setAttribute(transformElem, "Algorithm", getAlgorithm());

        spi.marshalParams
            (new javax.xml.crypto.dom.DOMStructure(transformElem), context);

        parent.appendChild(transformElem);
    }

    /**
     * Transforms the specified data using the underlying transform algorithm.
     *
     * @param data the data to be transformed
     * @param sc the <code>XMLCryptoContext</code> containing
     *    additional context (may be <code>null</code> if not applicable)
     * @return the transformed data
     * @throws NullPointerException if <code>data</code> is <code>null</code>
     * @throws XMLSignatureException if an unexpected error occurs while
     *    executing the transform
     */
    public Data transform(Data data, XMLCryptoContext xc)
        throws TransformException {
        return spi.transform(data, xc);
    }

    /**
     * Transforms the specified data using the underlying transform algorithm.
     *
     * @param data the data to be transformed
     * @param sc the <code>XMLCryptoContext</code> containing
     *    additional context (may be <code>null</code> if not applicable)
     * @param os the <code>OutputStream</code> that should be used to write
     *    the transformed data to
     * @return the transformed data
     * @throws NullPointerException if <code>data</code> is <code>null</code>
     * @throws XMLSignatureException if an unexpected error occurs while
     *    executing the transform
     */
    public Data transform(Data data, XMLCryptoContext xc, OutputStream os)
        throws TransformException {
        return spi.transform(data, xc, os);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Transform)) {
            return false;
        }
        Transform otransform = (Transform) o;

        return (getAlgorithm().equals(otransform.getAlgorithm()) &&
            DOMUtils.paramsEqual
                (getParameterSpec(), otransform.getParameterSpec()));
    }

    /**
     * Transforms the specified data using the underlying transform algorithm.
     * This method invokes the {@link #marshal marshal} method and passes it
     * the specified <code>DOMSignContext</code> before transforming the data.
     *
     * @param data the data to be transformed
     * @param sc the <code>XMLCryptoContext</code> containing
     *    additional context (may be <code>null</code> if not applicable)
     * @param context the marshalling context
     * @return the transformed data
     * @throws MarshalException if an exception occurs while marshalling
     * @throws NullPointerException if <code>data</code> or <code>context</code>
     *    is <code>null</code>
     * @throws XMLSignatureException if an unexpected error occurs while
     *    executing the transform
     */
    Data transform(Data data, XMLCryptoContext xc, DOMSignContext context)
        throws MarshalException, TransformException {
        marshal(context.getParent(),
            DOMUtils.getSignaturePrefix(context), context);
        return transform(data, xc);
    }
}
