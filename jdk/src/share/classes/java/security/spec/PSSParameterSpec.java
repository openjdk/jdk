/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.security.spec;

import java.math.BigInteger;
import java.security.spec.MGF1ParameterSpec;

/**
 * This class specifies a parameter spec for RSA-PSS signature scheme,
 * as defined in the
 * <a href="http://www.ietf.org/rfc/rfc3447.txt">PKCS#1 v2.1</a>
 * standard.
 *
 * <p>Its ASN.1 definition in PKCS#1 standard is described below:
 * <pre>
 * RSASSA-PSS-params ::= SEQUENCE {
 *   hashAlgorithm      [0] OAEP-PSSDigestAlgorithms  DEFAULT sha1,
 *   maskGenAlgorithm   [1] PKCS1MGFAlgorithms  DEFAULT mgf1SHA1,
 *   saltLength         [2] INTEGER  DEFAULT 20,
 *   trailerField       [3] INTEGER  DEFAULT 1
 * }
 * </pre>
 * where
 * <pre>
 * OAEP-PSSDigestAlgorithms    ALGORITHM-IDENTIFIER ::= {
 *   { OID id-sha1 PARAMETERS NULL   }|
 *   { OID id-sha224 PARAMETERS NULL   }|
 *   { OID id-sha256 PARAMETERS NULL }|
 *   { OID id-sha384 PARAMETERS NULL }|
 *   { OID id-sha512 PARAMETERS NULL },
 *   ...  -- Allows for future expansion --
 * }
 *
 * PKCS1MGFAlgorithms    ALGORITHM-IDENTIFIER ::= {
 *   { OID id-mgf1 PARAMETERS OAEP-PSSDigestAlgorithms },
 *   ...  -- Allows for future expansion --
 * }
 * </pre>
 * <p>Note: the PSSParameterSpec.DEFAULT uses the following:
 *     message digest  -- "SHA-1"
 *     mask generation function (mgf) -- "MGF1"
 *     parameters for mgf -- MGF1ParameterSpec.SHA1
 *     SaltLength   -- 20
 *     TrailerField -- 1
 *
 * @see MGF1ParameterSpec
 * @see AlgorithmParameterSpec
 * @see java.security.Signature
 *
 * @author Valerie Peng
 *
 *
 * @since 1.4
 */

public class PSSParameterSpec implements AlgorithmParameterSpec {

    private String mdName = "SHA-1";
    private String mgfName = "MGF1";
    private AlgorithmParameterSpec mgfSpec = MGF1ParameterSpec.SHA1;
    private int saltLen = 20;
    private int trailerField = 1;

    /**
     * The PSS parameter set with all default values.
     * @since 1.5
     */
    public static final PSSParameterSpec DEFAULT = new PSSParameterSpec();

    /**
     * Constructs a new {@code PSSParameterSpec} as defined in
     * the PKCS #1 standard using the default values.
     */
    private PSSParameterSpec() {
    }

    /**
     * Creates a new {@code PSSParameterSpec} as defined in
     * the PKCS #1 standard using the specified message digest,
     * mask generation function, parameters for mask generation
     * function, salt length, and trailer field values.
     *
     * @param mdName the algorithm name of the hash function.
     * @param mgfName the algorithm name of the mask generation
     * function.
     * @param mgfSpec the parameters for the mask generation
     * function. If null is specified, null will be returned by
     * getMGFParameters().
     * @param saltLen the length of salt.
     * @param trailerField the value of the trailer field.
     * @exception NullPointerException if {@code mdName},
     * or {@code mgfName} is null.
     * @exception IllegalArgumentException if {@code saltLen}
     * or {@code trailerField} is less than 0.
     * @since 1.5
     */
    public PSSParameterSpec(String mdName, String mgfName,
                            AlgorithmParameterSpec mgfSpec,
                            int saltLen, int trailerField) {
        if (mdName == null) {
            throw new NullPointerException("digest algorithm is null");
        }
        if (mgfName == null) {
            throw new NullPointerException("mask generation function " +
                                           "algorithm is null");
        }
        if (saltLen < 0) {
            throw new IllegalArgumentException("negative saltLen value: " +
                                               saltLen);
        }
        if (trailerField < 0) {
            throw new IllegalArgumentException("negative trailerField: " +
                                               trailerField);
        }
        this.mdName = mdName;
        this.mgfName = mgfName;
        this.mgfSpec = mgfSpec;
        this.saltLen = saltLen;
        this.trailerField = trailerField;
    }

    /**
     * Creates a new {@code PSSParameterSpec}
     * using the specified salt length and other default values as
     * defined in PKCS#1.
     *
     * @param saltLen the length of salt in bits to be used in PKCS#1
     * PSS encoding.
     * @exception IllegalArgumentException if {@code saltLen} is
     * less than 0.
     */
    public PSSParameterSpec(int saltLen) {
        if (saltLen < 0) {
            throw new IllegalArgumentException("negative saltLen value: " +
                                               saltLen);
        }
        this.saltLen = saltLen;
    }

    /**
     * Returns the message digest algorithm name.
     *
     * @return the message digest algorithm name.
     * @since 1.5
     */
    public String getDigestAlgorithm() {
        return mdName;
    }

    /**
     * Returns the mask generation function algorithm name.
     *
     * @return the mask generation function algorithm name.
     *
     * @since 1.5
     */
    public String getMGFAlgorithm() {
        return mgfName;
    }

    /**
     * Returns the parameters for the mask generation function.
     *
     * @return the parameters for the mask generation function.
     * @since 1.5
     */
    public AlgorithmParameterSpec getMGFParameters() {
        return mgfSpec;
    }

    /**
     * Returns the salt length in bits.
     *
     * @return the salt length.
     */
    public int getSaltLength() {
        return saltLen;
    }

    /**
     * Returns the value for the trailer field, i.e. bc in PKCS#1 v2.1.
     *
     * @return the value for the trailer field, i.e. bc in PKCS#1 v2.1.
     * @since 1.5
     */
    public int getTrailerField() {
        return trailerField;
    }
}
