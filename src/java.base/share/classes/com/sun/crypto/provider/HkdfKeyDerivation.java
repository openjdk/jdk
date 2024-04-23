/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import javax.crypto.*;
import javax.crypto.spec.KDFParameterSpec;

/**
 * KeyDerivation implementation for the HKDF function.
 * <p>
 * This class implements the HKDF-Extract and HKDF-Expand functions from RFC 5869.  This
 * implementation provides the complete Extract-then-Expand HKDF function as well as Extract-only
 * and Expand-only variants.
 */
abstract class HkdfKeyDerivation extends KDFSpi {

    protected String hmacAlgName;

    /**
     * The sole constructor.
     *
     * @param algParameterSpec the initialization parameters (may be {@code null})
     * @throws InvalidAlgorithmParameterException if the initialization parameters are inappropriate
     * for this {@code KDFSpi}
     */
    protected HkdfKeyDerivation(AlgorithmParameterSpec algParameterSpec)
        throws InvalidAlgorithmParameterException {
        super(algParameterSpec);
    }

    /**
     * Consume key stream data produced by the KDF and return it as a {@code Key} object.
     * <p>
     * This method may be called multiple times, depending on if the algorithm and provider supports
     * it.  Generally HKDF extract-then-expand and expand-only functions will allow multiple calls
     * if the key stream is long enough to support multiple key objects, while the HKDF extract-only
     * function is designed for a single key output.
     * <p>
     * Each call will build a {@code Key} object in accordance with the
     * {@code DerivationParameterSpec}.
     *
     * @return a {@code SecretKey} object composed from the available bytes in the key stream.
     * @throws InvalidParameterSpecException if the information contained within the current
     * {@code DerivationParameterSpec} is invalid or incorrect for the type of key to be derived, or
     * specifies a type of output that is not a key (e.g. raw data)
     * @throws IllegalStateException if the key derivation implementation cannot support additional
     * calls to {@code deriveKey} or if all {@code DerivationParameterSpec} objects provided at
     * initialization have been processed.
     */
    @Override
    protected SecretKey engineDeriveKey(String alg, KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException {
        return null;
    }

    /**
     * Consume key stream data produced by the KDF and return it as a {@code byte[]}.
     * <p>
     * This method may be called multiple times, depending on if the algorithm and provider supports
     * it.  Generally HKDF extract-then-expand and expand-only functions will allow multiple calls
     * if the key stream is long enough to support multiple key objects, while the HKDF extract-only
     * function is designed for a single key output.
     * <p>
     * Each call will build a {@code byte[]} in accordance with the next {@code length} parameter.
     *
     * @return a {@code byte[]} composed from the available bytes in the key stream.
     * @throws InvalidParameterException if value of {@code length} is invalid or incorrect
     * @throws IllegalStateException if the key derivation implementation cannot support additional
     * calls to {@code deriveData}
     */
    @Override
    protected byte[] engineDeriveData(KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterException {
        return new byte[0];
    }

    private static class HkdfExtExpBase extends HkdfKeyDerivation {

        /**
         * No-args constructor for HKDF Key Derivation
         *
         * @param algParameterSpec
         */
        public HkdfExtExpBase(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
        }

    }

    public static final class HkdfMD5 extends HkdfExtExpBase {
        public HkdfMD5(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacMD5";
        }
    }

    public static final class HkdfSHA1 extends HkdfExtExpBase {
        public HkdfSHA1(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA1";
        }
    }

    public static final class HkdfSHA224 extends HkdfExtExpBase {
        public HkdfSHA224(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA224";
        }
    }

    public static final class HkdfSHA256 extends HkdfExtExpBase {
        public HkdfSHA256(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA256";
        }
    }

    public static final class HkdfSHA384 extends HkdfExtExpBase {
        public HkdfSHA384(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA384";
        }
    }

    public static final class HkdfSHA512 extends HkdfExtExpBase {
        public HkdfSHA512(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA512";
        }
    }

    private static class HkdfExtractBase extends HkdfKeyDerivation {

        /**
         * No-args constructor for HKDF Key Derivation
         *
         * @param algParameterSpec
         */
        public HkdfExtractBase(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
        }
    }

    public static final class HkdfExtractMD5 extends HkdfExtractBase {
        public HkdfExtractMD5(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacMD5";
        }
    }

    public static final class HkdfExtractSHA1 extends HkdfExtractBase {
        public HkdfExtractSHA1(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA1";
        }
    }

    public static final class HkdfExtractSHA224 extends HkdfExtractBase {
        public HkdfExtractSHA224(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA224";
        }
    }

    public static final class HkdfExtractSHA256 extends HkdfExtractBase {
        public HkdfExtractSHA256(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA256";
        }
    }

    public static final class HkdfExtractSHA384 extends HkdfExtractBase {
        public HkdfExtractSHA384(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA384";
        }
    }

    public static final class HkdfExtractSHA512 extends HkdfExtractBase {
        public HkdfExtractSHA512(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA512";
        }
    }

    private static class HkdfExpandBase extends HkdfKeyDerivation {

        /**
         * No-args constructor for HKDF Key Derivation
         *
         * @param algParameterSpec
         */
        public HkdfExpandBase(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
        }
    }

    public static final class HkdfExpandMD5 extends HkdfExpandBase {
        public HkdfExpandMD5(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacMD5";
        }
    }

    public static final class HkdfExpandSHA1 extends HkdfExpandBase {
        public HkdfExpandSHA1(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA1";
        }
    }

    public static final class HkdfExpandSHA224 extends HkdfExpandBase {
        public HkdfExpandSHA224(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA224";
        }
    }

    public static final class HkdfExpandSHA256 extends HkdfExpandBase {
        public HkdfExpandSHA256(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA256";
        }
    }

    public static final class HkdfExpandSHA384 extends HkdfExpandBase {
        public HkdfExpandSHA384(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA384";
        }
    }

    public static final class HkdfExpandSHA512 extends HkdfExpandBase {
        public HkdfExpandSHA512(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException {
            super(algParameterSpec);
            hmacAlgName = "HmacSHA512";
        }
    }
}