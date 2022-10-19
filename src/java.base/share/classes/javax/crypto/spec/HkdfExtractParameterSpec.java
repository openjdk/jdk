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

package javax.crypto.spec;

import java.security.spec.AlgorithmParameterSpec;

/**
 * Parameters for the Extract operation of the HMAC-based Extract-and-Expand
 * Key Derivation Function (HKDF). The HKDF function is defined in
 * <a href="http://tools.ietf.org/html/rfc5869">RFC 5869</a>.
 * This class is used to initialize KeyGenerators in the HKDF family of
 * generators, specifically for the HKDF Extract function.
 * <p>
 * Here is an example of how an HkdfExtractParameterSpec would be used to
 * initialize a HKDF KeyDerivation object:
 * <pre>
 *     byte[] salt;
 *     SecretKey inputKey;
 *     SecretKey resultingPRK;
 *
 *     // salt and inputKey values populated with data
 *     ...
 *
 *     // Get an instance of the HKDF KeyGenerator
 *     hkdfGen = KeyGenerator.getInstance("HkdfSHA256");
 *
 *     // Create the spec object and use it to initialize the generator.
 *     hkdfGen.init(inputKey, new HkdfExtractParameterSpec(salt);
 *
 *     // Derive the PRK
 *     DerivedKeyParameterSpec keyParams =
 *                  new DerivedKeyParameterSpec("PRK", 32);
 *     resultingPRK = hkdfGen.generateKey(keyParams);
 * </pre>
 *
 * @since 9
 */
public final class HkdfExtractParameterSpec implements AlgorithmParameterSpec {

    private final byte[] salt;

    /**
     * Constructs a new HkdfExtractParameterSpec with the given salt value
     * and key material
     *
     * @param salt the salt value, or {@code null} if not specified.  The
     *      contents of the array are copied to protect against subsequent
     *      modification.
     *
     * @throws NullPointerException if {@code inputKey} is {@code null}.
     */
    public HkdfExtractParameterSpec(byte[] salt) {
        this.salt = (salt != null) ? salt.clone() : null;
    }

    /**
     * Returns the salt value.
     *
     * @return a copy of the salt value or {@code null} if no salt was provided.
     */
    public byte[] getSalt() {
        return (salt != null) ? salt.clone() : salt;
    }
}
