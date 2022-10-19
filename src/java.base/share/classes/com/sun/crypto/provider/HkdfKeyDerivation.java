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

package com.sun.crypto.provider;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.DerivedKeyParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.List;
import java.util.ArrayList;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.HkdfParameterSpec;
import javax.crypto.spec.HkdfExtractParameterSpec;
import javax.crypto.spec.HkdfExpandParameterSpec;
import java.util.Objects;

/**
 * KeyGenerator implementation for the HKDF function.
 * <p>
 * This class implements the HKDF-Extract and HKDF-Expand functions
 * from RFC 5869.  Both Extract and Expand functions use the same
 * HkdfParameterSpec class as input.
 *
 * @since 9
 */
abstract class HkdfKeyDerivation extends KeyDerivationSpi {

    private enum Operation {
        EXTRACT,
        EXPAND,
        EXTRACTEXPAND
    }

    private static final String MSG = "HkdfGenerator must be "
        + "initialized using an AlgorithmParameterSpec";
    protected Operation opType;
    protected String hmacAlgName;
    protected AlgorithmParameterSpec spec;
    protected Mac hmacObj;
    protected int hmacLen;
    protected SecretKey key;

    /**
     * No-args constructor for HKDF Key Derivation
     */
    protected HkdfKeyDerivation() { }

    /**
     * Initialize the HKDF Key Derivation engine.
     *
     * This version of the HkdfGenerator will perform an Extract-then-Expand
     * function using the salt and info values provided through a
     * {@code HkdfParameterSpec}.
     *
     * @param key the {@code SecretKey} that acts as the initial keying
     *      material to the HKDF algorithm.  This can be the IKM for HKDF
     *      running in Extract-only or Extract-then-Expand modes, or the PRK
     *      when running in Expand-only mode.
     * @param params a {@code HkdfParameterSpec} containing the necessary
     *      parameters for the specified HKDF function.  If {@code null}
     *      is provided, a default {@code HkdfParameterSpec} will be created.
     *
     * @throws InvalidParameterSpecException if the incoming
     *      {@code AlgorithmParameterSpec} is not of type
     *      {@code HkdfParameterSpec}.
     * @throws ProviderException if the underlying HMAC algorithm cannot be
     *      obtained from any providers.
     */
    @Override
    protected void engineInit(SecretKey key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidParameterSpecException {
        try {
            this.key = Objects.requireNonNull(key, "invalid null key");
            hmacObj = Mac.getInstance(hmacAlgName);
            hmacLen = hmacObj.getMacLength();

            // The incoming parameter object must be provided and must be
            // one of the three accepted HKDF parameter spec classes
            spec = Objects.requireNonNull(params,
                    "null parameters not allowed");
            if (spec instanceof HkdfParameterSpec) {
                opType = Operation.EXTRACTEXPAND;
            } else if (spec instanceof HkdfExtractParameterSpec) {
                opType = Operation.EXTRACT;
            } else if (spec instanceof HkdfExpandParameterSpec) {
                opType = Operation.EXPAND;
            } else {
                throw new InvalidParameterException(
                        "Parameters must be of type HkdfParameterSpec, " +
                        "HkdfExtractParameterSpec or HkdfExpandParameterSpec");
            }
        } catch (NoSuchAlgorithmException nsae) {
            throw new ProviderException(nsae);
        }
    }

    /**
     * Generate the HKDF data and return it as a {@code SecretKey}.
     *
     * @param params a {@code List} of pairs implemented as {@code Map.Entry}
     * objects.  Each entry consists of a {@code String} key label paired
     * with a {@code KDFAlgorithmParameterSpec} that holds any additional
     * key attributes.
     *
     * @return a {@code List} containing one or more {@code SecretKey} objects.
     * Objects will be returned in the order the key type/param pairs were
     * submitted in the {@code params} parameter.
     *
     * @throws InvalidParameterSpecException if any of the
     * {@code KDFAlgorithmParameterSpec} objects contain invalid data.
     */
    @Override
    protected List<SecretKey> engineDeriveKeys(
            List<DerivedKeyParameterSpec> params)
            throws InvalidParameterSpecException {
        List<SecretKey> keyList = new ArrayList<>();
        int totalLength = 0;
        SecretKey derivedKey;

        try {
            // If the engine has not been initialized yet, don't allow things
            // to proceed.
            if (spec == null) {
                throw new InvalidParameterSpecException(
                        "HKDF implementation must first be initialized");
            }

            // Get the total length of key material to be derived.
            for (DerivedKeyParameterSpec keyInfo : params) {
                totalLength += keyInfo.getKeyLength();
            }

            // Generate the byte stream that will account for all
            // key material requested.
            byte[] byteStream;
            switch (opType) {
                case EXTRACT:
                    byteStream = hkdfExtract(
                            ((HkdfExtractParameterSpec)spec).getSalt(), key);
                    break;
                case EXPAND:
                    byteStream = hkdfExpand(key,
                            ((HkdfExpandParameterSpec)spec).getInfo(),
                            totalLength);
                    break;
                case EXTRACTEXPAND:
                    HkdfParameterSpec hkdfSpec = (HkdfParameterSpec)spec;
                    SecretKey prkKey = new SecretKeySpec(
                            hkdfExtract(hkdfSpec.getSalt(), key), "PRK");
                    byteStream = hkdfExpand(prkKey, hkdfSpec.getInfo(),
                                    totalLength);
                    break;
                default:
                    throw new InvalidParameterSpecException(
                            "Invalid HKDF mode");
            }

            // Next, segment the key stream into the keys needed and
            // return it in a list.
            int byteStreamOffset = 0;
            for (DerivedKeyParameterSpec keyInfo : params) {
                int keyLen = (opType != Operation.EXTRACT) ?
                        keyInfo.getKeyLength() : hmacObj.getMacLength();
                derivedKey = new SecretKeySpec(byteStream, byteStreamOffset,
                    keyLen, keyInfo.getAlgorithm());
                byteStreamOffset += keyLen;
                keyList.add(derivedKey);
            }
        } catch (InvalidKeyException ike) {
            InvalidParameterSpecException exc =
                    new InvalidParameterSpecException();
            exc.initCause(ike);
            throw exc;
        }

        return keyList;
    }

    /**
     * Perform the HMAC-Extract operation.
     *
     * @param salt the salt value used for HKDF-Extract.  If no salt is to
     *      be used a {@code null} value should be provided.
     * @param inputKey the input keying material used for the HKDF-Extract
     *      operation.
     *
     * @return a byte array containing the pseudorandom key (PRK)
     *
     * @throws InvalidKeyException if an invalid salt was provided
     *      through the {@code HkdfParameterSpec}
     */
    protected byte[] hkdfExtract(byte[] salt, SecretKey inputKey)
            throws InvalidKeyException {

        if (salt == null) {
            salt = new byte[hmacLen];
        }
        hmacObj.init(new SecretKeySpec(salt, "HKDF-Salt"));

        return hmacObj.doFinal(inputKey.getEncoded());
    }

    /**
     * Perform the HMAC-Expand operation
     *
     * @param prk the pseudorandom key used for HKDF-Expand
     * @param info optional context and application specific information or
     *      {@code null} if no info data is provided.
     * @param outLen the length in bytes of the output data
     *
     * @return a byte array whose size corresponds to the
     *      {@code HkdfParameterSpec.getOutputLength()} method and contains
     *      the key stream output from this operation.
     *
     * @throws InvalidKeyException if an invalid key was provided
     *      through the {@code HkdfParameterSpec} or derived during the
     *      generation of the PRK.
     */
    protected byte[] hkdfExpand(SecretKey prk, byte[] info, int outLen)
            throws InvalidKeyException {

        // Calculate the number of rounds of HMAC that are needed to
        // meet the requested data.  Then set up the buffers we will need.
        hmacObj.init(prk);
        if (info == null) {
            info = new byte[0];
        }
        int rounds = (outLen + hmacLen - 1) / hmacLen;
        byte[] keyStream = new byte[outLen];
        byte[] inputBuffer = new byte[hmacLen + info.length + 1];
        int offset = 0;

        for (int i = 0; i < rounds ; i++) {
            // Prepare the input
            System.arraycopy(info, 0, inputBuffer, offset, info.length);
            offset += info.length;
            inputBuffer[offset++] = (byte)(i + 1);

            // Calculate this round
            try {
                hmacObj.update(inputBuffer, 0, offset);
                hmacObj.doFinal(inputBuffer, 0);        // T(i+1)
                System.arraycopy(inputBuffer, 0, keyStream, i * hmacLen,
                        Math.min(hmacLen, keyStream.length - (i * hmacLen)));
                offset = hmacLen;                       // For next iteration
            } catch (ShortBufferException sbe) {
                // This really shouldn't happen given that we've
                // sized the buffers to their largest possible size up-front,
                // but just in case...
                throw new RuntimeException(sbe);
            }
        }

        return keyStream;
    }

    public static final class HkdfMD5 extends HkdfKeyDerivation {
        public HkdfMD5() {
            hmacAlgName = "HmacMD5";
        }
    }

    public static final class HkdfSHA1 extends HkdfKeyDerivation {
        public HkdfSHA1() {
            hmacAlgName = "HmacSHA1";
        }
    }

    public static final class HkdfSHA224 extends HkdfKeyDerivation {
        public HkdfSHA224() {
            hmacAlgName = "HmacSHA224";
        }
    }

    public static final class HkdfSHA256 extends HkdfKeyDerivation {
        public HkdfSHA256() {
            hmacAlgName = "HmacSHA256";
        }
    }

    public static final class HkdfSHA384 extends HkdfKeyDerivation {
        public HkdfSHA384() {
            hmacAlgName = "HmacSHA384";
        }
    }

    public static final class HkdfSHA512 extends HkdfKeyDerivation {
        public HkdfSHA512() {
            hmacAlgName = "HmacSHA512";
        }
    }
}



