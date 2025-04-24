/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.util.List;

/**
 * This immutable class defines the parameters to be used with ML-DSA.
 * <p>
 * Note: this is not a public API. There is a strict domain separation defined
 * between ML-DSA and HashML-DSA, and they are treated as totally different
 * algorithms. Therefore, keys created for one algorithm must not be used in
 * signature generation or verification of the other algorithm.
 * <p>
 * This class provides 3 features:
 * <ol>
 * <li> When a pre-hash algorithm is specified, the message is first processed
 * by the message digest algorithm, and only the resulting hash is signed.
 * <li> The message can be concatenated with a context string to enable
 * (application-level) domain separation.
 * <li> More fine-tuned features, represented as case-sensitive strings, can be
 * specified.
 * <ul>
 * <li><code>deterministic</code>: signature generation uses constant byte
 *  string <code>new byte[32]</code>.
 * <li><code>internal</code>: the <code>ML-DSA.Sign_internal</code> defined
 *  in Section 6.2 of FIPS 204 is used.
 * <li><code>externalMu</code>: the b4-byte message representative mu inside
 *  <code>ML-DSA.Sign_internal</code> is computed in a different cryptographic
 *  module and directly provided as the input message.
 * </ul>
 * </ol>
 * Call {@link java.security.Signature#setParameter(AlgorithmParameterSpec)}
 * to configure parameters before calling {@link java.security.Signature#initSign(PrivateKey)}
 * or {@link java.security.Signature#initVerify(PublicKey)}.
 *
 * @since 25
 */
public final class SignatureParameterSpec implements AlgorithmParameterSpec {

    /**
     * The "pure" parameters with no preHash or context string.
     */
    public static final SignatureParameterSpec PURE
            = new SignatureParameterSpec(null, null);

    private final String preHash;
    private final byte[] context;
    private final List<String> features;

    /**
     * Creates a new {@code SignatureParameterSpec} using the specified
     * preHash algorithm, the context string, and optional algorithm-specific
     * feature strings.
     *
     * @param preHash the preHash algorithm to use; {@code null} if none.
     * @param context the context string; {@code null} or empty if none.
     * @param features the algorithm-specific features; empty if none.
     *
     * @throws IllegalArgumentException if {@code context} is longer than 255 bytes,
     *      or if no {@code preHash} is provided but {@code preHashParams} is
     *      not {@code null};
     * @throws NullPointerException if any of {@code features} is {@code null}
     */
    public SignatureParameterSpec(String preHash, byte[] context, String... features) {
        if (context != null && context.length > 255) {
            throw new IllegalArgumentException(
                    "Illegal context length: " + context.length);
        }
        this.preHash = preHash;
        this.context = context == null ? new byte[0] : context.clone();
        this.features = List.of(features);
    }

    /**
     * Checks if a feature is turned on.
     *
     * @param feature the feature in string, case-insensitive
     * @return {@code true} if the feature is on
     */
    public boolean hasFeature(String feature) {
        for (String f : features) {
            if (f != null && f.equalsIgnoreCase(feature)) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return context != null && context.length > 0 ? "hasContext" : "noContext"
                + ", preHash=" + preHash
                + (!features.isEmpty() ? ", features: " + features : "");
    }

    /**
     * Returns the preHash algorithm.
     * @return the preHash algorithm; {@code null} if none.
     */
    public String preHash() {
        return preHash;
    }

    /**
     * Returns the context string.
     * @return the context string; empty if none.
     */
    public byte[] context() {
        return context.clone();
    }

    /**
     * Returns the features.
     * @return the features; empty if none.
     */
    public List<String> features() {
        return features;
    }
}
