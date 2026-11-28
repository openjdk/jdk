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
package java.security.spec;

import java.util.Objects;

/**
 * Parameters for ML-DSA signature operations.
 *
 * <p>ML-DSA works in two mutually exclusive input modes:
 *
 * <ul>
 * <li><b>Message input mode with an optional context value</b><br>
 * In this mode, the {@linkplain java.security.Signature#update(byte[]) input
 * to the signature object} is the message to be signed, and an optional context
 * value may be supplied as a byte array by passing a {@link Context Context}
 * object to the signature's
 * {@link java.security.Signature#setParameter(AlgorithmParameterSpec) setParameter}
 * method. The context value provides domain separation between different
 * applications or protocol usages of ML-DSA. If this mode is enabled on the
 * signature generation side, the same mode with the same context string must
 * be enabled on the signature verification side.
 *
 * <li><b>External-mu input mode</b><br>
 * In this mode, the {@linkplain java.security.Signature#update(byte[]) input
 * to the signature object} is treated as the ML-DSA message representative
 * {@code mu}. See Algorithms 7 and 8 of FIPS 204 for a detailed definition
 * of {@code mu}. This mode is enabled by passing an
 * {@link ExternalMu ExternalMu} object to the signature's
 * {@link java.security.Signature#setParameter(AlgorithmParameterSpec) setParameter}
 * method. The total length of the data supplied must be exactly 64 bytes; otherwise,
 * a {@link java.security.SignatureException SignatureException} will
 * be thrown during signing or verification. If this mode is enabled on the
 * signature generation side, the same mode must be enabled on the signature
 * verification side.
 * </ul>
 *
 * <p>When no parameters are provided to an ML-DSA signature object, message
 * input mode with an empty context is assumed.
 *
 * @since 28
 * @spec https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.204.pdf
 *      FIPS 204: Module-Lattice-Based Digital Signature Standard
 */
public final class MLDSAParameterSpec {

    private MLDSAParameterSpec() {}

    /**
     * ML-DSA parameters using message input mode carrying an explicit context value.
     *
     * <p>Instances of this class are immutable. Use
     * {@link MLDSAParameterSpec#ofContext(byte[])} to obtain an instance.
     */
    public static final class Context implements AlgorithmParameterSpec {

        private final byte[] context;

        private Context(byte[] context) {
            Objects.requireNonNull(context);
            if (context.length > 255) {
                throw new IllegalArgumentException("context length must be <= 255");
            }
            this.context = context.clone();
        }

        /**
         * Returns the context value.
         *
         * @return the context value in a new array
         */
        public byte[] context() {
            return context.clone();
        }
    }

    /**
     * ML-DSA parameters using external-mu input mode.
     *
     * <p>Use {@link MLDSAParameterSpec#ofExternalMu()} to obtain an instance.
     */
    public static final class ExternalMu implements AlgorithmParameterSpec {
        private ExternalMu() {}
    }

    private static final ExternalMu EXTERNAL_MU = new ExternalMu();

    /**
     * Creates ML-DSA parameters using message input mode
     * with the specified context value.
     *
     * <p>The context value must be at most 255 bytes and is copied.
     *
     * @param context the context value to use (may be empty but not {@code null})
     * @return a {@code Context} object
     * @throws NullPointerException if {@code context} is {@code null}
     * @throws IllegalArgumentException if {@code context} is longer than 255 bytes
     */
    public static Context ofContext(byte[] context) {
        return new Context(context);
    }

    /**
     * Creates ML-DSA parameters using external-mu input mode.
     *
     * @return an {@code ExternalMu} object
     */
    public static ExternalMu ofExternalMu() {
        return EXTERNAL_MU;
    }
}
