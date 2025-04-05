/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import javax.crypto.KDFParameters;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * KDF implementation for the HKDF function.
 * <p>
 * This class implements the HKDF-Extract and HKDF-Expand functions from RFC
 * 5869.  This implementation provides the complete Extract-then-Expand HKDF
 * function as well as Extract-only and Expand-only variants.
 *
 * @spec https://www.rfc-editor.org/info/rfc5869
 *      RFC 5869: HMAC-based Extract-and-Expand Key Derivation Function (HKDF)
 */
abstract class HKDFKeyDerivation extends KDFSpi {

    private final int hmacLen;
    private final String hmacAlgName;

    private enum SupportedHmac {
        SHA256("HmacSHA256", 32),
        SHA384("HmacSHA384", 48),
        SHA512("HmacSHA512", 64);

        private final String hmacAlg;
        private final int hmacLen;
        SupportedHmac(String hmacAlg, int hmacLen) {
            this.hmacAlg = hmacAlg;
            this.hmacLen = hmacLen;
        }
    };

    /**
     * The sole constructor.
     *
     * @param kdfParameters
     *         the initialization parameters (may be {@code null})
     *
     * @throws InvalidAlgorithmParameterException
     *         if the initialization parameters are inappropriate for this
     *         {@code KDFSpi}
     */
    private HKDFKeyDerivation(SupportedHmac supportedHmac,
                              KDFParameters kdfParameters)
            throws InvalidAlgorithmParameterException {
        super(kdfParameters);
        if (kdfParameters != null) {
            throw new InvalidAlgorithmParameterException(
                    supportedHmac.hmacAlg + " does not support parameters");
        }
        this.hmacAlgName = supportedHmac.hmacAlg;
        this.hmacLen = supportedHmac.hmacLen;
    }

    /**
     * Derive a key, returned as a {@code SecretKey} object.
     *
     * @return a derived {@code SecretKey} object of the specified algorithm
     *
     * @throws InvalidAlgorithmParameterException
     *         if the information contained within the {@code derivationSpec} is
     *         invalid or if the combination of {@code alg} and the
     *         {@code derivationSpec} results in something invalid
     * @throws NoSuchAlgorithmException
     *         if {@code alg} is empty
     * @throws NullPointerException
     *         if {@code alg} is {@code null}
     */
    @Override
    protected SecretKey engineDeriveKey(String alg,
                                        AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException,
                   NoSuchAlgorithmException {

        if (alg == null) {
            throw new NullPointerException(
                    "the algorithm for the SecretKey return value must not be"
                    + " null");
        }
        if (alg.isEmpty()) {
            throw new NoSuchAlgorithmException(
                    "the algorithm for the SecretKey return value must not be "
                    + "empty");
        }

        return new SecretKeySpec(engineDeriveData(derivationSpec), alg);

    }

    /**
     * Obtain raw data from a key derivation function.
     *
     * @return a derived {@code byte[]}
     *
     * @throws InvalidAlgorithmParameterException
     *         if the information contained within the {@code KDFParameterSpec}
     *         is invalid or incorrect for the type of key to be derived
     * @throws UnsupportedOperationException
     *         if the derived keying material is not extractable
     */
    @Override
    protected byte[] engineDeriveData(AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException {
        List<SecretKey> ikms, salts;
        byte[] inputKeyMaterial, salt, pseudoRandomKey, info;
        int length;
        if (derivationSpec instanceof HKDFParameterSpec.Extract anExtract) {
            ikms = anExtract.ikms();
            salts = anExtract.salts();
            // we should be able to combine both of the above Lists of key
            // segments into one SecretKey object each, unless we were passed
            // something bogus or an unexportable P11 key
            inputKeyMaterial = null;
            salt = null;
            try {
                inputKeyMaterial = consolidateKeyMaterial(ikms);
                salt = consolidateKeyMaterial(salts);

                // perform extract
                return hkdfExtract(inputKeyMaterial, salt);
            } catch (InvalidKeyException ike) {
                throw new InvalidAlgorithmParameterException(
                        "an HKDF Extract could not be initialized with the "
                        + "given key or salt material", ike);
            } catch (NoSuchAlgorithmException nsae) {
                // This is bubbling up from the getInstance of the Mac/Hmac.
                // Since we're defining these values internally, it is unlikely.
                throw new ProviderException(
                        "could not instantiate a Mac with the provided "
                        + "algorithm",
                        nsae);
            } finally {
                if (inputKeyMaterial != null) {
                    Arrays.fill(inputKeyMaterial, (byte) 0x00);
                }
                if (salt != null) {
                    Arrays.fill(salt, (byte) 0x00);
                }
            }
        } else if (derivationSpec instanceof HKDFParameterSpec.Expand anExpand) {
            // set this value in the "if"
            if ((pseudoRandomKey = anExpand.prk().getEncoded()) == null) {
                throw new InvalidAlgorithmParameterException(
                        "Cannot retrieve PRK for HKDFParameterSpec.Expand");
            }
            // set this value in the "if"
            if ((info = anExpand.info()) == null) {
                info = new byte[0];
            }
            length = anExpand.length();
            if (length > (hmacLen * 255)) {
                throw new InvalidAlgorithmParameterException(
                        "Requested length exceeds maximum allowed length");
            }
            // perform expand
            try {
                return hkdfExpand(pseudoRandomKey, info, length);
            } catch (InvalidKeyException ike) {
                throw new InvalidAlgorithmParameterException(
                        "an HKDF Expand could not be initialized with the "
                        + "given keying material", ike);
            } catch (NoSuchAlgorithmException nsae) {
                // This is bubbling up from the getInstance of the Mac/Hmac.
                // Since we're defining these values internally, it is unlikely.
                throw new ProviderException(
                        "could not instantiate a Mac with the provided "
                        + "algorithm",
                        nsae);
            } finally {
                Arrays.fill(pseudoRandomKey, (byte) 0x00);
            }
        } else if (derivationSpec instanceof HKDFParameterSpec.ExtractThenExpand anExtractThenExpand) {
            ikms = anExtractThenExpand.ikms();
            salts = anExtractThenExpand.salts();
            // we should be able to combine both of the above Lists of key
            // segments into one SecretKey object each, unless we were passed
            // something bogus or an unexportable P11 key
            inputKeyMaterial = null;
            salt = null;
            pseudoRandomKey = null;
            try {
                inputKeyMaterial = consolidateKeyMaterial(ikms);
                salt = consolidateKeyMaterial(salts);

                // set this value in the "if"
                if ((info = anExtractThenExpand.info()) == null) {
                    info = new byte[0];
                }
                length = anExtractThenExpand.length();
                if (length > (hmacLen * 255)) {
                    throw new InvalidAlgorithmParameterException(
                            "Requested length exceeds maximum allowed length");
                }

                // perform extract and then expand
                pseudoRandomKey = hkdfExtract(inputKeyMaterial, salt);
                return hkdfExpand(pseudoRandomKey, info, length);
            } catch (InvalidKeyException ike) {
                throw new InvalidAlgorithmParameterException(
                        "an HKDF ExtractThenExpand could not be initialized "
                        + "with the given key or salt material", ike);
            } catch (NoSuchAlgorithmException nsae) {
                // This is bubbling up from the getInstance of the Mac/HMAC.
                // Since we're defining these values internally, it is unlikely.
                throw new ProviderException(
                        "could not instantiate a Mac with the provided "
                        + "algorithm",
                        nsae);
            } finally {
                if (inputKeyMaterial != null) {
                    Arrays.fill(inputKeyMaterial, (byte) 0x00);
                }
                if (salt != null) {
                    Arrays.fill(salt, (byte) 0x00);
                }
                if (pseudoRandomKey != null) {
                    Arrays.fill(pseudoRandomKey, (byte) 0x00);
                }
            }
        }
        throw new InvalidAlgorithmParameterException(
                "an HKDF derivation requires a valid HKDFParameterSpec");
    }

    // throws an InvalidKeyException if any key is unextractable
    private byte[] consolidateKeyMaterial(List<SecretKey> keys)
            throws InvalidKeyException {
        if (keys != null && !keys.isEmpty()) {
            ArrayList<SecretKey> localKeys = new ArrayList<>(keys);
            if (localKeys.size() == 1) {
                // return this element
                SecretKey checkIt = localKeys.get(0);
                return CipherCore.getKeyBytes(checkIt);
            } else {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                for (SecretKey workItem : localKeys) {
                    os.writeBytes(CipherCore.getKeyBytes(workItem));
                }
                // deliberately omitting os.flush(), since we are writing to
                // memory, and toByteArray() reads like there isn't an explicit
                // need for this call
                return os.toByteArray();
            }
        } else if (keys != null) {
            return new byte[0];
        } else {
            throw new InvalidKeyException(
                    "List of key segments could not be consolidated");
        }
    }

    /**
     * Perform the HKDF-Extract operation.
     *
     * @param inputKeyMaterial
     *         the input keying material used for the HKDF-Extract operation.
     * @param salt
     *         the salt value used for HKDF-Extract
     *
     * @return a byte array containing the pseudorandom key (PRK)
     *
     * @throws InvalidKeyException
     *         if an invalid salt was provided through the
     *         {@code HKDFParameterSpec}
     */
    private byte[] hkdfExtract(byte[] inputKeyMaterial, byte[] salt)
            throws InvalidKeyException, NoSuchAlgorithmException {

        // salt will not be null
        if (salt.length == 0) {
            salt = new byte[hmacLen];
        }
        Mac hmacObj = Mac.getInstance(hmacAlgName);
        hmacObj.init(new SecretKeySpec(salt, hmacAlgName));

        // inputKeyMaterial will not be null
        return hmacObj.doFinal(inputKeyMaterial);
    }

    /**
     * Perform the HKDF-Expand operation.
     *
     * @param prk
     *         the pseudorandom key used for HKDF-Expand
     * @param info
     *         optional context and application specific information or
     *         {@code null} if no info data is provided.
     * @param outLen
     *         the length in bytes of the required output
     *
     * @return a byte array containing the complete {@code KDF} output.  This
     *         will be at least as long as the requested length in the
     *         {@code outLen} parameter, but will be rounded up to the nearest
     *         multiple of the HMAC output length.
     *
     * @throws InvalidKeyException
     *         if an invalid PRK was provided through the
     *         {@code HKDFParameterSpec} or derived during the extract phase.
     */
    private byte[] hkdfExpand(byte[] prk, byte[] info, int outLen)
            throws InvalidKeyException, NoSuchAlgorithmException {
        byte[] kdfOutput;

        if (prk == null || prk.length < hmacLen) {
            throw new InvalidKeyException(
                    "prk must be at least " + hmacLen + " bytes");
        }

        SecretKey pseudoRandomKey = new SecretKeySpec(prk, hmacAlgName);

        Mac hmacObj = Mac.getInstance(hmacAlgName);

        // Calculate the number of rounds of HMAC that are needed to
        // meet the requested data.  Then set up the buffers we will need.
        hmacObj.init(pseudoRandomKey);
        int rounds = (outLen + hmacLen - 1) / hmacLen;
        kdfOutput = new byte[outLen];
        int i = 0;
        int offset = 0;
        try {
            while (i < rounds) {
                if (i > 0) {
                    hmacObj.update(kdfOutput, offset - hmacLen,
                                   hmacLen); // add T(i-1)
                }
                hmacObj.update(info);                   // Add info
                hmacObj.update((byte) ++i);             // Add round number
                if (i == rounds && (outLen - offset < hmacLen)) {
                    // special handling for last chunk
                    byte[] tmp = hmacObj.doFinal();
                    System.arraycopy(tmp, 0, kdfOutput, offset,
                                     outLen - offset);
                    Arrays.fill(tmp, (byte) 0x00);
                    offset = outLen;
                } else {
                    hmacObj.doFinal(kdfOutput, offset);
                    offset += hmacLen;
                }
            }
        } catch (ShortBufferException sbe) {
            // This really shouldn't happen given that we've
            // sized the buffers to their largest possible size up-front,
            // but just in case...
            throw new ProviderException(sbe);
        }
        return kdfOutput;
    }

    protected KDFParameters engineGetParameters() {
        return null;
    }

    public static final class HKDFSHA256 extends HKDFKeyDerivation {
        public HKDFSHA256(KDFParameters kdfParameters)
                throws InvalidAlgorithmParameterException {
            super(SupportedHmac.SHA256, kdfParameters);
        }
    }

    public static final class HKDFSHA384 extends HKDFKeyDerivation {
        public HKDFSHA384(KDFParameters kdfParameters)
                throws InvalidAlgorithmParameterException {
            super(SupportedHmac.SHA384, kdfParameters);
        }
    }

    public static final class HKDFSHA512 extends HKDFKeyDerivation {
        public HKDFSHA512(KDFParameters kdfParameters)
                throws InvalidAlgorithmParameterException {
            super(SupportedHmac.SHA512, kdfParameters);
        }
    }

}
