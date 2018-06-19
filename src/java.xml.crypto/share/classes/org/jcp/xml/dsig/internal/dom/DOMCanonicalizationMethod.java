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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMCanonicalizationMethod.java 1788465 2017-03-24 15:10:51Z coheigea $
 */
package org.jcp.xml.dsig.internal.dom;

import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.Provider;
import java.security.spec.AlgorithmParameterSpec;

import org.w3c.dom.Element;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;

/**
 * DOM-based abstract implementation of CanonicalizationMethod.
 *
 */
public class DOMCanonicalizationMethod extends DOMTransform
    implements CanonicalizationMethod {

    /**
     * Creates a {@code DOMCanonicalizationMethod}.
     *
     * @param spi TransformService
     */
    public DOMCanonicalizationMethod(TransformService spi)
        throws InvalidAlgorithmParameterException
    {
        super(spi);
        if (!(spi instanceof ApacheCanonicalizer) && !isC14Nalg(spi.getAlgorithm())) {
            throw new InvalidAlgorithmParameterException("Illegal CanonicalizationMethod");
        }
    }

    /**
     * Creates a {@code DOMCanonicalizationMethod} from an element. It unmarshals any
     * algorithm-specific input parameters.
     *
     * @param cmElem a CanonicalizationMethod element
     */
    public DOMCanonicalizationMethod(Element cmElem, XMLCryptoContext context,
                                     Provider provider)
        throws MarshalException
    {
        super(cmElem, context, provider);
        if (!(spi instanceof ApacheCanonicalizer) && !isC14Nalg(spi.getAlgorithm())) {
            throw new MarshalException("Illegal CanonicalizationMethod");
        }
    }

    /**
     * Canonicalizes the specified data using the underlying canonicalization
     * algorithm. This is a convenience method that is equivalent to invoking
     * the {@link #transform transform} method.
     *
     * @param data the data to be canonicalized
     * @param xc the {@code XMLCryptoContext} containing
     *     additional context (may be {@code null} if not applicable)
     * @return the canonicalized data
     * @throws NullPointerException if {@code data} is {@code null}
     * @throws TransformException if an unexpected error occurs while
     *    canonicalizing the data
     */
    public Data canonicalize(Data data, XMLCryptoContext xc)
        throws TransformException
    {
        return transform(data, xc);
    }

    public Data canonicalize(Data data, XMLCryptoContext xc, OutputStream os)
        throws TransformException
    {
        return transform(data, xc, os);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CanonicalizationMethod)) {
            return false;
        }
        CanonicalizationMethod ocm = (CanonicalizationMethod)o;

        return getAlgorithm().equals(ocm.getAlgorithm()) &&
            DOMUtils.paramsEqual(getParameterSpec(), ocm.getParameterSpec());
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + getAlgorithm().hashCode();
        AlgorithmParameterSpec spec = getParameterSpec();
        if (spec != null) {
            result = 31 * result + spec.hashCode();
        }

        return result;
    }

    private static boolean isC14Nalg(String alg) {
        return isInclusiveC14Nalg(alg) || isExclusiveC14Nalg(alg) || isC14N11alg(alg);
    }

    private static boolean isInclusiveC14Nalg(String alg) {
        return alg.equals(CanonicalizationMethod.INCLUSIVE)
            || alg.equals(CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS);
    }

    private static boolean isExclusiveC14Nalg(String alg) {
        return alg.equals(CanonicalizationMethod.EXCLUSIVE)
            || alg.equals(CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS);
    }

    private static boolean isC14N11alg(String alg) {
        return alg.equals(DOMCanonicalXMLC14N11Method.C14N_11)
            || alg.equals(DOMCanonicalXMLC14N11Method.C14N_11_WITH_COMMENTS);
    }
}
