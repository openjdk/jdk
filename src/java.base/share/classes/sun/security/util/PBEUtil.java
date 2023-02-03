/*
 * Copyright (c) 2023, Red Hat, Inc.
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

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public final class PBEUtil {

    // Used by SunJCE and SunPKCS11
    public final static class PBES2Params {
        private static final int DEFAULT_SALT_LENGTH = 20;
        private static final int DEFAULT_ITERATIONS = 4096;

        private int iCount;
        private byte[] salt;
        private IvParameterSpec ivSpec;

        public IvParameterSpec getIvSpec() {
            return ivSpec;
        }

        public AlgorithmParameters getAlgorithmParameters(int blkSize,
                String pbeAlgo, Provider algParamsProv, SecureRandom random) {
            AlgorithmParameters params = null;
            if (salt == null) {
                // generate random salt and use default iteration count
                salt = new byte[DEFAULT_SALT_LENGTH];
                random.nextBytes(salt);
                iCount = DEFAULT_ITERATIONS;
            }
            if (ivSpec == null) {
                // generate random IV
                byte[] ivBytes = new byte[blkSize];
                random.nextBytes(ivBytes);
                ivSpec = new IvParameterSpec(ivBytes);
            }
            PBEParameterSpec pbeSpec = new PBEParameterSpec(
                    salt, iCount, ivSpec);
            try {
                params = AlgorithmParameters.getInstance(pbeAlgo,
                        algParamsProv);
                params.init(pbeSpec);
            } catch (NoSuchAlgorithmException nsae) {
                // should never happen
                throw new RuntimeException("AlgorithmParameters for "
                        + pbeAlgo + " not configured");
            } catch (InvalidParameterSpecException ipse) {
                // should never happen
                throw new RuntimeException("PBEParameterSpec not supported");
            }
            return params;
        }

        public PBEKeySpec getPBEKeySpec(int blkSize, int keyLength, int opmode,
                Key key, AlgorithmParameterSpec params, SecureRandom random)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            if (key == null) {
                throw new InvalidKeyException("Null key");
            }

            byte[] passwdBytes = key.getEncoded();
            char[] passwdChars = null;
            salt = null;
            iCount = 0;
            ivSpec = null;

            PBEKeySpec pbeSpec;
            try {
                if ((passwdBytes == null) ||
                        !(key.getAlgorithm().regionMatches(true, 0, "PBE", 0,
                                3))) {
                    throw new InvalidKeyException("Missing password");
                }

                boolean doEncrypt = ((opmode == Cipher.ENCRYPT_MODE) ||
                            (opmode == Cipher.WRAP_MODE));

                // Extract from the supplied PBE params, if present
                if (params instanceof PBEParameterSpec pbeParams) {
                    // salt should be non-null per PBEParameterSpec
                    salt = check(pbeParams.getSalt());
                    iCount = check(pbeParams.getIterationCount());
                    AlgorithmParameterSpec ivParams =
                            pbeParams.getParameterSpec();
                    if (ivParams instanceof IvParameterSpec iv) {
                        ivSpec = iv;
                    } else if (ivParams == null && doEncrypt) {
                        // generate random IV
                        byte[] ivBytes = new byte[blkSize];
                        random.nextBytes(ivBytes);
                        ivSpec = new IvParameterSpec(ivBytes);
                    } else {
                        throw new InvalidAlgorithmParameterException(
                                "Wrong parameter type: IV expected");
                    }
                } else if (params == null && doEncrypt) {
                    // Try extracting from the key if present. If unspecified,
                    // PBEKey returns null and 0 respectively.
                    if (key instanceof javax.crypto.interfaces.PBEKey pbeKey) {
                        salt = check(pbeKey.getSalt());
                        iCount = check(pbeKey.getIterationCount());
                    }
                    if (salt == null) {
                        // generate random salt
                        salt = new byte[DEFAULT_SALT_LENGTH];
                        random.nextBytes(salt);
                    }
                    if (iCount == 0) {
                        // use default iteration count
                        iCount = DEFAULT_ITERATIONS;
                    }
                    // generate random IV
                    byte[] ivBytes = new byte[blkSize];
                    random.nextBytes(ivBytes);
                    ivSpec = new IvParameterSpec(ivBytes);
                } else {
                    throw new InvalidAlgorithmParameterException(
                            "Wrong parameter type: PBE expected");
                }
                passwdChars = new char[passwdBytes.length];
                for (int i = 0; i < passwdChars.length; i++) {
                    passwdChars[i] = (char) (passwdBytes[i] & 0x7f);
                }

                pbeSpec = new PBEKeySpec(passwdChars, salt, iCount, keyLength);
            } finally {
                // password char[] was cloned in PBEKeySpec constructor,
                // so we can zero it out here
                if (passwdChars != null) Arrays.fill(passwdChars, '\0');
                if (passwdBytes != null) Arrays.fill(passwdBytes, (byte)0x00);
            }
            return pbeSpec;
        }

        public static AlgorithmParameterSpec getParameterSpec(
                AlgorithmParameters params)
                throws InvalidAlgorithmParameterException {
            AlgorithmParameterSpec pbeSpec = null;
            if (params != null) {
                try {
                    pbeSpec = params.getParameterSpec(PBEParameterSpec.class);
                } catch (InvalidParameterSpecException ipse) {
                    throw new InvalidAlgorithmParameterException(
                            "Wrong parameter type: PBE expected");
                }
            }
            return pbeSpec;
        }

        private static byte[] check(byte[] salt)
                throws InvalidAlgorithmParameterException {
            if (salt != null && salt.length < 8) {
                throw new InvalidAlgorithmParameterException(
                        "Salt must be at least 8 bytes long");
            }
            return salt;
        }

        private static int check(int iCount)
                throws InvalidAlgorithmParameterException {
            if (iCount < 0) {
                throw new InvalidAlgorithmParameterException(
                        "Iteration count must be a positive number");
            }
            return iCount == 0 ? DEFAULT_ITERATIONS : iCount;
        }
    }

    // Used by SunJCE and SunPKCS11
    public static PBEKeySpec getPBAKeySpec(Key key,
            AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        char[] passwdChars;
        byte[] salt = null;
        int iCount = 0;
        if (key instanceof javax.crypto.interfaces.PBEKey pbeKey) {
            passwdChars = pbeKey.getPassword();
            salt = pbeKey.getSalt(); // maybe null if unspecified
            iCount = pbeKey.getIterationCount(); // maybe 0 if unspecified
        } else if (key instanceof SecretKey) {
            byte[] passwdBytes;
            if (!(key.getAlgorithm().regionMatches(true, 0, "PBE", 0, 3)) ||
                    (passwdBytes = key.getEncoded()) == null) {
                throw new InvalidKeyException("Missing password");
            }
            passwdChars = new char[passwdBytes.length];
            for (int i = 0; i < passwdChars.length; i++) {
                passwdChars[i] = (char) (passwdBytes[i] & 0x7f);
            }
            Arrays.fill(passwdBytes, (byte)0x00);
        } else {
            throw new InvalidKeyException("SecretKey of PBE type required");
        }

        try {
            if (params == null) {
                // should not auto-generate default values since current
                // javax.crypto.Mac api does not have any method for caller to
                // retrieve the generated defaults.
                if ((salt == null) || (iCount == 0)) {
                    throw new InvalidAlgorithmParameterException(
                            "PBEParameterSpec required for salt " +
                            "and iteration count");
                }
            } else if (!(params instanceof PBEParameterSpec)) {
                throw new InvalidAlgorithmParameterException(
                        "PBEParameterSpec type required");
            } else {
                PBEParameterSpec pbeParams = (PBEParameterSpec) params;
                // make sure the parameter values are consistent
                if (salt != null) {
                    if (!Arrays.equals(salt, pbeParams.getSalt())) {
                        throw new InvalidAlgorithmParameterException(
                                "Inconsistent value of salt " +
                                "between key and params");
                    }
                } else {
                    salt = pbeParams.getSalt();
                }
                if (iCount != 0) {
                    if (iCount != pbeParams.getIterationCount()) {
                        throw new InvalidAlgorithmParameterException(
                                "Different iteration count " +
                                "between key and params");
                    }
                } else {
                    iCount = pbeParams.getIterationCount();
                }
            }
            // For security purpose, we need to enforce a minimum length
            // for salt; just require the minimum salt length to be 8-byte
            // which is what PKCS#5 recommends and openssl does.
            if (salt.length < 8) {
                throw new InvalidAlgorithmParameterException(
                        "Salt must be at least 8 bytes long");
            }
            if (iCount <= 0) {
                throw new InvalidAlgorithmParameterException(
                        "IterationCount must be a positive number");
            }
            return new PBEKeySpec(passwdChars, salt, iCount);
        } finally {
            Arrays.fill(passwdChars, '\0');
        }
    }

    public static AlgorithmParameterSpec checkKeyParams(Key key,
            AlgorithmParameterSpec params, String algorithm)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (key instanceof javax.crypto.interfaces.PBEKey pbeKey) {
            if (params instanceof PBEParameterSpec pbeParams) {
                if (pbeParams.getIterationCount() !=
                        pbeKey.getIterationCount() ||
                        !Arrays.equals(pbeParams.getSalt(), pbeKey.getSalt())) {
                    throw new InvalidAlgorithmParameterException(
                            "Salt or iteration count parameters are " +
                            "not consistent with PBE key");
                }
                return pbeParams.getParameterSpec();
            }
        } else {
            throw new InvalidKeyException(
                    "Cannot use a " + algorithm + " service with a key that " +
                    "does not implement javax.crypto.interfaces.PBEKey");
        }
        return params;
    }
}
