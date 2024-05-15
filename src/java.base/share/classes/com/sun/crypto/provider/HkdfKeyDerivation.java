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

import javax.crypto.KDFSpi;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.KDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * KDF implementation for the HKDF function.
 * <p>
 * This class implements the HKDF-Extract and HKDF-Expand functions from RFC
 * 5869.  This implementation provides the complete Extract-then-Expand HKDF
 * function as well as Extract-only and Expand-only variants.
 */
abstract class HkdfKeyDerivation extends KDFSpi {

    protected final int hmacLen;
    protected final String hmacAlgName;

    /**
     * The sole constructor.
     *
     * @param algParameterSpec
     *     the initialization parameters (may be {@code null})
     *
     * @throws InvalidAlgorithmParameterException
     *     if the initialization parameters are inappropriate for this
     *     {@code KDFSpi}
     */
    HkdfKeyDerivation(String hmacAlgName, int hmacLen,
                      AlgorithmParameterSpec algParameterSpec)
        throws InvalidAlgorithmParameterException {
        super(algParameterSpec);
        if (algParameterSpec != null) {
            throw new InvalidAlgorithmParameterException(
                "RFC 5869 has no parameters for its KDF algorithms");
        }
        this.hmacAlgName = hmacAlgName;
        this.hmacLen = hmacLen;
    }

    /**
     * Derive a key, returned as a {@code SecretKey}.
     *
     * @return a derived {@code SecretKey} object of the specified algorithm
     *
     * @throws InvalidParameterSpecException
     *     if the information contained within the {@code KDFParameterSpec} is
     *     invalid or incorrect for the type of key to be derived, or specifies
     *     a type of output that is not a key (e.g. raw data)
     */
    @Override
    protected SecretKey engineDeriveKey(String alg,
                                        KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException {

        if (alg == null || alg.isEmpty()) {
            throw new InvalidParameterSpecException(
                "the algorithm for the resultant SecretKey may not be null or"
                + " empty");
        }

        return new SecretKeySpec(engineDeriveData(kdfParameterSpec), alg);

    }

    /**
     * Obtain raw data from a key derivation function.
     *
     * @return a derived {@code byte[]}
     *
     * @throws InvalidParameterSpecException
     *     if the information contained within the {@code KDFParameterSpec} is
     *     invalid or incorrect for the type of key to be derived
     * @throws UnsupportedOperationException
     *     if the derived key material is not extractable
     */
    @Override
    protected byte[] engineDeriveData(KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException {
        List<SecretKey> ikms;
        List<SecretKey> salts;
        SecretKey inputKeyMaterial;
        SecretKey salt;
        SecretKey pseudoRandomKey;
        byte[] info;
        int length;
        // A switch would be nicer, but we may need to backport this before
        // JDK 17
        // Also, JEP 305 came out in JDK 14, so we can't declare a variable
        // in instanceof either
        if (kdfParameterSpec instanceof HKDFParameterSpec.Extract) {
            HKDFParameterSpec.Extract anExtract =
                (HKDFParameterSpec.Extract) kdfParameterSpec;
            ikms = anExtract.ikms();
            salts = anExtract.salts();
            // we should be able to combine these Lists of keys into single
            // SecretKey Objects,
            // unless we were passed something bogus or an unexportable P11 key
            try {
                inputKeyMaterial = consolidateKeyMaterial(ikms);
                salt = consolidateKeyMaterial(salts);
            } catch (InvalidKeyException ike) {
                throw (InvalidParameterSpecException) new InvalidParameterSpecException(
                    "Issue encountered when combining ikm or salt values into"
                    + " single keys").initCause(ike);
            }
            // perform extract
            try {
                return hkdfExtract(inputKeyMaterial,
                                   (salt == null) ? null : salt.getEncoded());
            } catch (InvalidKeyException ike) {
                throw (InvalidParameterSpecException) new InvalidParameterSpecException(
                    "an HKDF Extract could not be initialized with the given "
                    + "key or salt material").initCause(ike);
            } catch (NoSuchAlgorithmException nsae) {
                // This is bubbling up from the getInstance of the Mac/Hmac.
                // Since we're defining these values internally, it should be
                // safe to "eat" this NSAE.
            }
        } else if (kdfParameterSpec instanceof HKDFParameterSpec.Expand) {
            HKDFParameterSpec.Expand anExpand =
                (HKDFParameterSpec.Expand) kdfParameterSpec;
            // set this value in the "if"
            if ((pseudoRandomKey = anExpand.prk()) == null) {
                throw new InvalidParameterSpecException(
                    "PRK is required for HKDFParameterSpec.Expand");
            }
            // set this value in the "if"
            if ((info = anExpand.info()) == null) {
                info = new byte[0];
            }
            length = anExpand.length();
            if (length > (hmacLen * 255)) {
                throw new InvalidParameterSpecException(
                    "Requested length exceeds maximum allowed key stream "
                    + "length");
            }
            // perform expand
            try {
                return Arrays.copyOf(hkdfExpand(pseudoRandomKey, info, length),
                                     length);
            } catch (InvalidKeyException ike) {
                throw (InvalidParameterSpecException) new InvalidParameterSpecException(
                    "an HKDF Expand could not be initialized with the given "
                    + "key material").initCause(ike);
            } catch (NoSuchAlgorithmException nsae) {
                // This is bubbling up from the getInstance of the Mac/Hmac.
                // Since we're defining these values internally, it should be
                // safe to "eat" this NSAE.
            }
        } else if (kdfParameterSpec instanceof HKDFParameterSpec.ExtractThenExpand) {
            HKDFParameterSpec.ExtractThenExpand anExtractThenExpand =
                (HKDFParameterSpec.ExtractThenExpand) kdfParameterSpec;
            ikms = anExtractThenExpand.ikms();
            salts = anExtractThenExpand.salts();
            // we should be able to combine these Lists of keys into single
            // SecretKey Objects,
            // unless we were passed something bogus or an unexportable P11 key
            try {
                inputKeyMaterial = consolidateKeyMaterial(ikms);
                salt = consolidateKeyMaterial(salts);
            } catch (InvalidKeyException ike) {
                throw (InvalidParameterSpecException) new InvalidParameterSpecException(
                    "Issue encountered when combining ikm or salt values into"
                    + " single keys").initCause(ike);
            }
            // set this value in the "if"
            if ((info = anExtractThenExpand.info()) == null) {
                info = new byte[0];
            }
            length = anExtractThenExpand.length();
            if (length > (hmacLen * 255)) {
                throw new InvalidParameterSpecException(
                    "Requested length exceeds maximum allowed key stream " 
                    + "length");
            }
            // perform extract and then expand
            try {
                byte[] extractResult = hkdfExtract(inputKeyMaterial, (salt
                                                                      == null) ? null : salt.getEncoded());
                pseudoRandomKey = new SecretKeySpec(extractResult, "RAW");
                return Arrays.copyOf(hkdfExpand(pseudoRandomKey, info, length),
                                     length);
            } catch (InvalidKeyException ike) {
                throw (InvalidParameterSpecException) new InvalidParameterSpecException(
                    "an HKDF ExtractThenExpand could not be initialized with "
                    + "the given key or salt material").initCause(ike);
            } catch (NoSuchAlgorithmException nsae) {
                // This is bubbling up from the getInstance of the Mac/Hmac.
                // Since we're defining these values internally, it should be
                // safe to "eat" this NSAE.
            }
        }
        throw new InvalidParameterSpecException(
            "an HKDF could not be initialized with the given KDFParameterSpec");
    }

    private static boolean isNullOrEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    private SecretKey consolidateKeyMaterial(List<SecretKey> keys)
        throws InvalidKeyException {
        if (keys != null && !keys.isEmpty()) {
            ArrayList<SecretKey> localKeys = new ArrayList<>(keys);
            if (localKeys.size() == 1) {
                // return this element
                return localKeys.get(0);
            } else {
                byte[] bb = new byte[0];
                for (SecretKey workItem : localKeys) {
                    byte[] workItemBytes = CipherCore.getKeyBytes(workItem);

                    bb = Arrays.copyOf(bb, bb.length + workItemBytes.length);
                    System.arraycopy(workItemBytes, 0, bb,
                                     bb.length - workItemBytes.length,
                                     workItemBytes.length);
                }
                return new SecretKeySpec(bb, "RAW");
            }
        } else if (keys.isEmpty()) {
            return null;
        } else {
            throw new InvalidKeyException(
                "List of keys could not be consolidated");
        }
    }

    /**
     * Perform the HMAC-Extract operation.
     *
     * @param inputKey
     *     the input keying material used for the HKDF-Extract operation.
     * @param salt
     *     the salt value used for HKDF-Extract.  If no salt is to be used a
     *     {@code null} value should be provided.
     *
     * @return a byte array containing the pseudorandom key (PRK)
     *
     * @throws InvalidKeyException
     *     if an invalid salt was provided through the
     *     {@code HkdfParameterSpec}
     */
    protected byte[] hkdfExtract(SecretKey inputKey, byte[] salt)
        throws InvalidKeyException, NoSuchAlgorithmException {

        if (salt == null) {
            salt = new byte[hmacLen];
        }
        Mac hmacObj = Mac.getInstance(hmacAlgName);
        hmacObj.init(new SecretKeySpec(salt, "HKDF-Salt"));

        if (inputKey == null) {
            return hmacObj.doFinal();
        } else {
            return hmacObj.doFinal(inputKey.getEncoded());
        }
    }

    /**
     * Perform the HMAC-Expand operation.  At the end of the operation, the
     * keyStream instance variable will contain the complete KDF output based on
     * the input values and desired length.
     *
     * @param prk
     *     the pseudorandom key used for HKDF-Expand
     * @param info
     *     optional context and application specific information or {@code null}
     *     if no info data is provided.
     * @param outLen
     *     the length in bytes of the required output
     *
     * @return a byte array containing the complete KDF output.  This will be at
     *     least as long as the requested length in the {@code outLen}
     *     parameter, but will be rounded up to the nearest multiple of the HMAC
     *     output length.
     *
     * @throws InvalidKeyException
     *     if an invalid key was provided through the {@code HkdfParameterSpec}
     *     or derived during the generation of the PRK.
     */
    protected byte[] hkdfExpand(SecretKey prk, byte[] info, int outLen)
        throws InvalidKeyException, NoSuchAlgorithmException {
        byte[] kdfOutput;
        Mac hmacObj = Mac.getInstance(hmacAlgName);

        // Calculate the number of rounds of HMAC that are needed to
        // meet the requested data.  Then set up the buffers we will need.
        hmacObj.init(prk);
        if (info == null) {
            info = new byte[0];
        }
        int rounds = (outLen + hmacLen - 1) / hmacLen;
        kdfOutput = new byte[rounds * hmacLen];
        int offset = 0;
        int tLength = 0;

        for (int i = 0; i < rounds; i++) {

            // Calculate this round
            try {
                // Add T(i).  This will be an empty string on the first
                // iteration since tLength starts at zero.  After the first
                // iteration, tLength is changed to the HMAC length for the
                // rest of the loop.
                hmacObj.update(kdfOutput, Math.max(0, offset - hmacLen),
                               tLength);
                hmacObj.update(info);                       // Add info
                hmacObj.update((byte) (i + 1));              // Add round number
                hmacObj.doFinal(kdfOutput, offset);

                tLength = hmacLen;
                offset += hmacLen;                       // For next iteration
            } catch (ShortBufferException sbe) {
                // This really shouldn't happen given that we've
                // sized the buffers to their largest possible size up-front,
                // but just in case...
                throw new RuntimeException(sbe);
            }
        }

        return kdfOutput;
    }

    public static final class HkdfSHA256 extends HkdfKeyDerivation {
        public HkdfSHA256(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException,
                   NoSuchAlgorithmException {
            super("HmacSHA256", 32, algParameterSpec);
        }
    }

    public static final class HkdfSHA384 extends HkdfKeyDerivation {
        public HkdfSHA384(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException,
                   NoSuchAlgorithmException {
            super("HmacSHA384", 48, algParameterSpec);
        }
    }

    public static final class HkdfSHA512 extends HkdfKeyDerivation {
        public HkdfSHA512(AlgorithmParameterSpec algParameterSpec)
            throws InvalidAlgorithmParameterException,
                   NoSuchAlgorithmException {
            super("HmacSHA512", 64, algParameterSpec);
        }
    }

}