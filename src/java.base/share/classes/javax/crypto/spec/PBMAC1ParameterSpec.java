/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * comment
 *
 * @since 28
 */
public final class PBMAC1ParameterSpec implements AlgorithmParameterSpec {
    private final int keyLength; // bits
    private final String prfAlgorithm;

    /**
     * Constructs a PBMAC1 parameter set as defined in RFC 9579.
     * @param keyLength in bits
     *
     * @since 28
     */
    public PBMAC1ParameterSpec(int keyLength) {
        this(keyLength, null);
    }

    /**
     * Constructs a PBMAC1 parameter set as defined in RFC 9579.
     * @param keyLength in bits
     * @param prfAlgorithm the pseudo-random algorithm used for key derivation
     *
     * @since 28
     */
    public PBMAC1ParameterSpec(int keyLength, String prfAlgorithm) {
        if (keyLength <= 0) {
            throw new IllegalArgumentException("keyLength must be positive");
        }
        this.keyLength = keyLength;
        this.prfAlgorithm = prfAlgorithm;
    }

    /**
     * comment
     * @return keyLength
     *
     * @since 28
     */
    public int getKeyLength() {
        return keyLength;
    }

    /**
     * comment
     * @return prfAlgorithm used for key derivation
     *
     * @since 28
     */
    public String getPrfAlgorithm() {
        return prfAlgorithm;
    }
}
