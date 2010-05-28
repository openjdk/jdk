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
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMCanonicalizationMethod.java,v 1.2 2008/07/24 15:20:32 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.Provider;

import org.w3c.dom.Element;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;

/**
 * DOM-based abstract implementation of CanonicalizationMethod.
 *
 * @author Sean Mullan
 */
public class DOMCanonicalizationMethod extends DOMTransform
    implements CanonicalizationMethod {

    /**
     * Creates a <code>DOMCanonicalizationMethod</code>.
     *
     * @param spi TransformService
     */
    public DOMCanonicalizationMethod(TransformService spi)
        throws InvalidAlgorithmParameterException {
        super(spi);
    }

    /**
     * Creates a <code>DOMCanonicalizationMethod</code> from an element. This
     * ctor invokes the abstract {@link #unmarshalParams unmarshalParams}
     * method to unmarshal any algorithm-specific input parameters.
     *
     * @param cmElem a CanonicalizationMethod element
     */
    public DOMCanonicalizationMethod(Element cmElem, XMLCryptoContext context,
        Provider provider) throws MarshalException {
        super(cmElem, context, provider);
    }

    /**
     * Canonicalizes the specified data using the underlying canonicalization
     * algorithm. This is a convenience method that is equivalent to invoking
     * the {@link #transform transform} method.
     *
     * @param data the data to be canonicalized
     * @param xc the <code>XMLCryptoContext</code> containing
     *     additional context (may be <code>null</code> if not applicable)
     * @return the canonicalized data
     * @throws NullPointerException if <code>data</code> is <code>null</code>
     * @throws TransformException if an unexpected error occurs while
     *    canonicalizing the data
     */
    public Data canonicalize(Data data, XMLCryptoContext xc)
        throws TransformException {
        return transform(data, xc);
    }

    public Data canonicalize(Data data, XMLCryptoContext xc, OutputStream os)
        throws TransformException {
        return transform(data, xc, os);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CanonicalizationMethod)) {
            return false;
        }
        CanonicalizationMethod ocm = (CanonicalizationMethod) o;

        return (getAlgorithm().equals(ocm.getAlgorithm()) &&
            DOMUtils.paramsEqual(getParameterSpec(), ocm.getParameterSpec()));
    }
}
