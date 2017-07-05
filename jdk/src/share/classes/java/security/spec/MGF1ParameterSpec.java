/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security.spec;

import java.security.spec.AlgorithmParameterSpec;

/**
 * This class specifies the set of parameters used with mask generation
 * function MGF1 in OAEP Padding and RSA-PSS signature scheme, as
 * defined in the
 * <a href="http://www.ietf.org/rfc/rfc3447.txt">PKCS #1 v2.1</a>
 * standard.
 *
 * <p>Its ASN.1 definition in PKCS#1 standard is described below:
 * <pre>
 * MGF1Parameters ::= OAEP-PSSDigestAlgorthms
 * </pre>
 * where
 * <pre>
 * OAEP-PSSDigestAlgorithms    ALGORITHM-IDENTIFIER ::= {
 *   { OID id-sha1 PARAMETERS NULL   }|
 *   { OID id-sha256 PARAMETERS NULL }|
 *   { OID id-sha384 PARAMETERS NULL }|
 *   { OID id-sha512 PARAMETERS NULL },
 *   ...  -- Allows for future expansion --
 * }
 * </pre>
 * @see PSSParameterSpec
 * @see javax.crypto.spec.OAEPParameterSpec
 *
 * @author Valerie Peng
 *
 * @since 1.5
 */
public class MGF1ParameterSpec implements AlgorithmParameterSpec {

    /**
     * The MGF1ParameterSpec which uses "SHA-1" message digest.
     */
    public static final MGF1ParameterSpec SHA1 =
        new MGF1ParameterSpec("SHA-1");
    /**
     * The MGF1ParameterSpec which uses "SHA-256" message digest.
     */
    public static final MGF1ParameterSpec SHA256 =
        new MGF1ParameterSpec("SHA-256");
    /**
     * The MGF1ParameterSpec which uses "SHA-384" message digest.
     */
    public static final MGF1ParameterSpec SHA384 =
        new MGF1ParameterSpec("SHA-384");
    /**
     * The MGF1ParameterSpec which uses SHA-512 message digest.
     */
    public static final MGF1ParameterSpec SHA512 =
        new MGF1ParameterSpec("SHA-512");

    private String mdName;

    /**
     * Constructs a parameter set for mask generation function MGF1
     * as defined in the PKCS #1 standard.
     *
     * @param mdName the algorithm name for the message digest
     * used in this mask generation function MGF1.
     * @exception NullPointerException if <code>mdName</code> is null.
     */
    public MGF1ParameterSpec(String mdName) {
        if (mdName == null) {
            throw new NullPointerException("digest algorithm is null");
        }
        this.mdName = mdName;
    }

    /**
     * Returns the algorithm name of the message digest used by the mask
     * generation function.
     *
     * @return the algorithm name of the message digest.
     */
    public String getDigestAlgorithm() {
        return mdName;
    }
}
