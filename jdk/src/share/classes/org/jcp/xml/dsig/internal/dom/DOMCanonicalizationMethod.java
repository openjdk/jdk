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
 * $Id: DOMCanonicalizationMethod.java,v 1.25 2005/05/10 18:15:31 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;

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
    public DOMCanonicalizationMethod(Element cmElem, XMLCryptoContext context)
        throws MarshalException{
        super(cmElem, context);
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
     * @throws XMLSignatureException if an unexpected error occurs while
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
