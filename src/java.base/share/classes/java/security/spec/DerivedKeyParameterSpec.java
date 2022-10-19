/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

/**
 * A specification of cryptographic parameters for keys derived from a
 * Key Derivation Function (KDF).
 *
 * @since 18.9
 */

public class DerivedKeyParameterSpec {

    private final String algName;
    private final int keyLength;
    private final AlgorithmParameterSpec parameters;

    /**
     * Create a {@code DerivedKeyParameterSpec} that has no key-specific
     * parameters.
     *
     * @param algorithm the algorithm assigned to the resulting key.
     * @param length the length of the key in bytes.
     *
     * @throws IllegalArgumentException if the {@code algorithm} parameter
     * is {@code null} or the {@code length} parameter is not a positive
     * integer.
     */
    public DerivedKeyParameterSpec(String algorithm, int length) {
        this(algorithm, length, null);
    }

    /**
     * Create a {@code DerivedKeyParameterSpec} that has no key-specific
     * parameters.
     *
     * @param algorithm the algorithm assigned to the resulting key.
     * @param length the length of the key in bytes.
     * @param params an {@code AlgorithmParameterSpec} implementation
     * containing parameters specific to the key being derived.
     *
     * @throws NullPointerException if the {@code algorithm} parameter
     * is {@code null}.
     * @throws IllegalArgumentException if the {@code length} parameter is
     * not a positive integer.
     */
    public DerivedKeyParameterSpec(String algorithm, int length,
            AlgorithmParameterSpec params) {
        algName = Objects.requireNonNull(algorithm,
                "A null algorithm name is not allowed.");
        if (length < 0) {
            throw new IllegalArgumentException("The derived key length must " +
                    "be a non-negative integer in bytes");
        }
        keyLength = length;
        parameters = params;
    }

    /**
     * Returns the algorithm name for this {@code DerivedKeyParameterSpec}.
     *
     * @return the algorithm name.
     */
    public String getAlgorithm() {
        return algName;
    }

    /**
     * Obtains the key length in bytes for this {@code DerivedKeyParameterSpec}.
     *
     * @return the key length in bytes.
     */
    public int getKeyLength() {
        return keyLength;
    }

    /**
     * Retrieve any parameters specific to the key being derived.
     *
     * @return an {@code AlgorithmParameterSpec} if one was provided during
     * construction of this object, otherwise {@code null}.
     */
    public AlgorithmParameterSpec getParameterSpec() {
        return parameters;
    }

}
