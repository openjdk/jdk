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

import java.security.InvalidAlgorithmParameterException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.List;
import javax.crypto.KDFSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
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
    protected List<SecretKey> ikms;
    protected List<SecretKey> salts;
    protected SecretKey prk;
    protected byte[] info;
    protected int length;

    protected enum HKDFTYPES {
        EXTRACT, EXPAND, EXTRACTEXPAND
    }

    protected HKDFTYPES HKDFTYPE;

    /**
     * The sole constructor.
     *
     * @param algParameterSpec
     *     the initialization parameters (may be {@code null})
     *
     * @throws InvalidAlgorithmParameterException
     *     if the initialization parameters are inappropriate for this {@code KDFSpi}
     */
    protected HkdfKeyDerivation(AlgorithmParameterSpec algParameterSpec)
        throws InvalidAlgorithmParameterException {
        super(algParameterSpec);
    }

    /**
     * TODO: description
     *
     * @return a derived {@code SecretKey} object of the specified algorithm
     *
     * @throws InvalidParameterSpecException
     *     if the information contained within the current {@code KDFParameterSpec} is invalid or
     *     incorrect for the type of key to be derived
     * @throws IllegalStateException
     *     if the key derivation implementation cannot support additional calls to
     *     {@code deriveKey}
     */
    @Override
    protected SecretKey engineDeriveKey(String alg, KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException {

        // inspect KDFParameterSpec object
        inspectKDFParameterSpec(kdfParameterSpec);

        return null;
    }

    /**
     * TODO: description
     *
     * @return a derived {@code byte[]}
     *
     * @throws InvalidParameterSpecException
     *     if the information contained within the current {@code KDFParameterSpec} is invalid or
     *     incorrect
     * @throws IllegalStateException
     *     if the key derivation implementation cannot support additional calls to
     *     {@code deriveData}
     */
    @Override
    protected byte[] engineDeriveData(KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException {

        // inspect KDFParameterSpec object
        inspectKDFParameterSpec(kdfParameterSpec);

        return new byte[0];
    }

    protected void inspectKDFParameterSpec(KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException {
        // A switch would be nicer, but we may need to backport this before JDK 17
        // Also, JEP 305 came out in JDK 14, so we can't declare a variable in instanceof either

        if (kdfParameterSpec instanceof HKDFParameterSpec.Extract) {
            HKDFParameterSpec.Extract anExtract = (HKDFParameterSpec.Extract) kdfParameterSpec;
            ikms = anExtract.ikms();
            salts = anExtract.salts();
            HKDFTYPE = HKDFTYPES.EXTRACT;
        } else if (kdfParameterSpec instanceof HKDFParameterSpec.Expand) {
            HKDFParameterSpec.Expand anExpand = (HKDFParameterSpec.Expand) kdfParameterSpec;
            prk = anExpand.prk();
            info = anExpand.info();
            length = anExpand.length();
            HKDFTYPE = HKDFTYPES.EXPAND;
        } else if (kdfParameterSpec instanceof HKDFParameterSpec.ExtractExpand) {
            HKDFParameterSpec.ExtractExpand anExtractExpand =
                (HKDFParameterSpec.ExtractExpand) kdfParameterSpec;
            ikms = anExtractExpand.ikms();
            salts = anExtractExpand.salts();
            info = anExtractExpand.info();
            length = anExtractExpand.length();
            HKDFTYPE = HKDFTYPES.EXTRACTEXPAND;
        } else {
            throw new InvalidParameterSpecException(
                "The KDFParameterSpec object was not of a recognized type");
        }
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