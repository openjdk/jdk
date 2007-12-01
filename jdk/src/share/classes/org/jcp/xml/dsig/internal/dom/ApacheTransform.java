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
 * $Id: ApacheTransform.java,v 1.23 2005/09/15 14:29:03 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.transforms.Transform;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;

/**
 * This is a wrapper/glue class which invokes the Apache XML-Security
 * Transform.
 *
 * @author Sean Mullan
 * @author Erwin van der Koogh
 */
public abstract class ApacheTransform extends TransformService {

    private static Logger log = Logger.getLogger("org.jcp.xml.dsig.internal.dom");
    private Transform apacheTransform;
    protected Document ownerDoc;
    protected Element transformElem;
    protected TransformParameterSpec params;

    public final AlgorithmParameterSpec getParameterSpec() {
        return params;
    }

    public void init(XMLStructure parent, XMLCryptoContext context)
        throws InvalidAlgorithmParameterException {
        if (context != null && !(context instanceof DOMCryptoContext)) {
            throw new ClassCastException
                ("context must be of type DOMCryptoContext");
        }
        transformElem = (Element)
            ((javax.xml.crypto.dom.DOMStructure) parent).getNode();
        ownerDoc = DOMUtils.getOwnerDocument(transformElem);
    }

    public void marshalParams(XMLStructure parent, XMLCryptoContext context)
        throws MarshalException {
        if (context != null && !(context instanceof DOMCryptoContext)) {
            throw new ClassCastException
                ("context must be of type DOMCryptoContext");
        }
        transformElem = (Element)
            ((javax.xml.crypto.dom.DOMStructure) parent).getNode();
        ownerDoc = DOMUtils.getOwnerDocument(transformElem);
    }

    public Data transform(Data data, XMLCryptoContext xc)
        throws TransformException {
        if (data == null) {
            throw new NullPointerException("data must not be null");
        }
        return transformIt(data, xc, (OutputStream) null);
    }

    public Data transform(Data data, XMLCryptoContext xc, OutputStream os)
        throws TransformException {
        if (data == null) {
            throw new NullPointerException("data must not be null");
        }
        if (os == null) {
            throw new NullPointerException("output stream must not be null");
        }
        return transformIt(data, xc, os);
    }

    private Data transformIt(Data data, XMLCryptoContext xc, OutputStream os)
        throws TransformException {

        if (ownerDoc == null) {
            throw new TransformException("transform must be marshalled");
        }

        if (apacheTransform == null) {
            try {
                apacheTransform = Transform.getInstance
                    (ownerDoc, getAlgorithm(), transformElem.getChildNodes());
                apacheTransform.setElement(transformElem, xc.getBaseURI());
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "Created transform for algorithm: "
                        + getAlgorithm());
                }
            } catch (Exception ex) {
                throw new TransformException
                    ("Couldn't find Transform for: " + getAlgorithm(), ex);
            }
        }

        XMLSignatureInput in;
        if (data instanceof ApacheData) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "ApacheData = true");
            }
            in = ((ApacheData) data).getXMLSignatureInput();
        } else if (data instanceof NodeSetData) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "isNodeSet() = true");
            }
            if (data instanceof DOMSubTreeData) {
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "DOMSubTreeData = true");
                }
                DOMSubTreeData subTree = (DOMSubTreeData) data;
                in = new XMLSignatureInput(subTree.getRoot());
                in.setExcludeComments(subTree.excludeComments());
            } else {
                Set nodeSet =
                    Utils.toNodeSet(((NodeSetData) data).iterator());
                in = new XMLSignatureInput(nodeSet);
            }
        } else {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "isNodeSet() = false");
            }
            try {
                in = new XMLSignatureInput
                    (((OctetStreamData)data).getOctetStream());
            } catch (Exception ex) {
                throw new TransformException(ex);
            }
        }

        try {
            if (os != null) {
                in = apacheTransform.performTransform(in, os);
                if (!in.isNodeSet() && !in.isElement()) {
                    return null;
                }
            } else {
                in = apacheTransform.performTransform(in);
            }
            if (in.isOctetStream()) {
                return new ApacheOctetStreamData(in);
            } else {
                return new ApacheNodeSetData(in);
            }
        } catch (Exception ex) {
            throw new TransformException(ex);
        }
    }

    public final boolean isFeatureSupported(String feature) {
        if (feature == null) {
            throw new NullPointerException();
        } else {
            return false;
        }
    }
}
