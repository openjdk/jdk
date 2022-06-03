/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javax.crypto.spec;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;

/**
 * This class specifies the set of parameters used with OAEP Padding,
 * as defined in the
 * <a href="https://tools.ietf.org/rfc/rfc8017.txt">PKCS#1 v2.2</a> standard.
 *
 * Its ASN.1 definition in PKCS#1 standard is described below:
 * <pre>
 * RSAES-OAEP-params ::= SEQUENCE {
 *   hashAlgorithm      [0] HashAlgorithm     DEFAULT sha1,
 *   maskGenAlgorithm   [1] MaskGenAlgorithm  DEFAULT mgf1SHA1,
 *   pSourceAlgorithm   [2] PSourceAlgorithm  DEFAULT pSpecifiedEmpty
 * }
 * </pre>
 * where
 * <pre>
 * HashAlgorithm ::= AlgorithmIdentifier {
 *   {OAEP-PSSDigestAlgorithms}
 * }
 * MaskGenAlgorithm ::= AlgorithmIdentifier { {PKCS1MGFAlgorithms} }
 * PSourceAlgorithm ::= AlgorithmIdentifier {
 *   {PKCS1PSourceAlgorithms}
 * }
 *
 * OAEP-PSSDigestAlgorithms    ALGORITHM-IDENTIFIER ::= {
 *   { OID id-sha1       PARAMETERS NULL }|
 *   { OID id-sha224     PARAMETERS NULL }|
 *   { OID id-sha256     PARAMETERS NULL }|
 *   { OID id-sha384     PARAMETERS NULL }|
 *   { OID id-sha512     PARAMETERS NULL }|
 *   { OID id-sha512-224 PARAMETERS NULL }|
 *   { OID id-sha512-256 PARAMETERS NULL },
 *   ...  -- Allows for future expansion --
 * }
 * PKCS1MGFAlgorithms    ALGORITHM-IDENTIFIER ::= {
 *   { OID id-mgf1 PARAMETERS HashAlgorithm },
 *   ...  -- Allows for future expansion --
 * }
 * PKCS1PSourceAlgorithms    ALGORITHM-IDENTIFIER ::= {
 *   { OID id-pSpecified PARAMETERS EncodingParameters },
 *   ...  -- Allows for future expansion --
 * }
 * EncodingParameters ::= OCTET STRING(SIZE(0..MAX))
 * </pre>
 *
 * @see java.security.spec.MGF1ParameterSpec
 * @see PSource
 *
 * @author Valerie Peng
 *
 * @since 1.5
 */
public class OAEPParameterSpec implements AlgorithmParameterSpec {

    private final String mdName;
    private final String mgfName;
    private final AlgorithmParameterSpec mgfSpec;
    private final PSource pSrc;

    /**
     * The OAEP parameter set with all default values, i.e. "SHA-1" as message
     * digest algorithm, "MGF1" as mask generation function (mgf) algorithm,
     * {@code MGF1ParameterSpec.SHA1} as parameters for the mask generation
     * function, and {@code PSource.PSpecified.DEFAULT} as the source of the
     * encoding input.
     *
     * @deprecated This field uses the default values defined in the PKCS #1
     *         standard. Some of these defaults are no longer recommended due
     *         to advances in cryptanalysis -- see
     *         <a href="https://www.rfc-editor.org/rfc/rfc8017#appendix-B.1">Appendix B.1 of PKCS #1</a>
     *         for more details. Thus, it is recommended to create
     *         a new {@code OAEPParameterSpec} with the desired parameter values
     *         using the
     *         {@link #OAEPParameterSpec(String, String, AlgorithmParameterSpec, PSource)} constructor.
     *
     */
    @Deprecated(since="19")
    public static final OAEPParameterSpec DEFAULT = new OAEPParameterSpec(
            "SHA-1", "MGF1", MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT);

    /**
     * Constructs a parameter set for OAEP padding as defined in
     * the PKCS #1 standard using the specified message digest
     * algorithm {@code mdName}, mask generation function
     * algorithm {@code mgfName}, parameters for the mask
     * generation function {@code mgfSpec}, and source of
     * the encoding input P {@code pSrc}.
     *
     * @param mdName the algorithm name for the message digest
     * @param mgfName the algorithm name for the mask generation function
     * @param mgfSpec the parameters for the mask generation function;
     * if {@code null} is specified, {@code null} will be returned by
     * {@link #getMGFParameters()}
     * @param pSrc the source of the encoding input P
     * @throws NullPointerException if {@code mdName},
     * {@code mgfName}, or {@code pSrc} is {@code null}
     */
    public OAEPParameterSpec(String mdName, String mgfName,
                             AlgorithmParameterSpec mgfSpec,
                             PSource pSrc) {
        if (mdName == null) {
            throw new NullPointerException("digest algorithm is null");
        }
        if (mgfName == null) {
            throw new NullPointerException("mask generation function " +
                                           "algorithm is null");
        }
        if (pSrc == null) {
            throw new NullPointerException("source of the encoding input " +
                                           "is null");
        }
        this.mdName =  mdName;
        this.mgfName =  mgfName;
        this.mgfSpec =  mgfSpec;
        this.pSrc =  pSrc;
    }

    /**
     * Returns the message digest algorithm name.
     *
     * @return the message digest algorithm name.
     */
    public String getDigestAlgorithm() {
        return mdName;
    }

    /**
     * Returns the mask generation function algorithm name.
     *
     * @return the mask generation function algorithm name.
     */
    public String getMGFAlgorithm() {
        return mgfName;
    }

    /**
     * Returns the parameters for the mask generation function.
     *
     * @return the parameters for the mask generation function.
     */
    public AlgorithmParameterSpec getMGFParameters() {
        return mgfSpec;
    }

    /**
     * Returns the source of encoding input P.
     *
     * @return the source of encoding input P.
     */
    public PSource getPSource() {
        return pSrc;
    }
}
