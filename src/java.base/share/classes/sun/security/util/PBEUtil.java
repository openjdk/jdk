/*
 * Copyright (c) 2023, 2025, Red Hat, Inc.
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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
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

    /*
     * PBES2Params is an auxiliary class that represents the state needed for
     * PBES2 operations (iterations count, salt and IV) and its (re)
     * initialization logic. Users of this class are CipherSpi implementors that
     * support PBES2 cryptography (RFC #8018), such as PBES2Core (SunJCE) and
     * P11PBECipher (SunPKCS11).
     *
     * CipherSpi implementors must call ::getPBEKeySpec in every engine
     * initialization (CipherSpi::engineInit override) to reset the state and
     * get new values in a PBEKeySpec instance. These new values are taken
     * from parameters, defaults or generated randomly.
     *
     * After engine initialization, values in effect can be extracted with
     * ::getAlgorithmParameters (as AlgorithmParameters) or ::getIvSpec (as
     * IvParameterSpec).
     */
    public static final class PBES2Params {
        private static final int DEFAULT_SALT_LENGTH = 20;
        private static final int DEFAULT_ITERATIONS = 4096;

        private int iCount;
        private byte[] salt;
        private IvParameterSpec ivSpec;

        /*
         * Initialize a PBES2Params instance. May generate random salt and
         * IV if not passed and the operation is encryption. If initialization
         * fails, values are reset. Used by PBES2Params and P11PBECipher
         * (SunPKCS11).
         */
        public void initialize(int blkSize, int opmode, int iCount, byte[] salt,
                AlgorithmParameterSpec ivSpec, SecureRandom random)
                throws InvalidAlgorithmParameterException {
            try {
                boolean doEncrypt = opmode == Cipher.ENCRYPT_MODE ||
                        opmode == Cipher.WRAP_MODE;
                if (ivSpec instanceof IvParameterSpec iv) {
                    this.ivSpec = iv;
                } else if (ivSpec == null && doEncrypt) {
                    byte[] ivBytes = new byte[blkSize];
                    random.nextBytes(ivBytes);
                    this.ivSpec = new IvParameterSpec(ivBytes);
                } else {
                    throw new InvalidAlgorithmParameterException("Wrong " +
                            "parameter type: IvParameterSpec " +
                            (doEncrypt ? "or null " : "") + "expected");
                }
                this.iCount = iCount == 0 ? DEFAULT_ITERATIONS : iCount;
                if (salt == null) {
                    if (doEncrypt) {
                        salt = new byte[DEFAULT_SALT_LENGTH];
                        random.nextBytes(salt);
                    } else {
                        throw new InvalidAlgorithmParameterException("Salt " +
                                "needed for decryption");
                    }
                }
                this.salt = salt;
            } catch (InvalidAlgorithmParameterException e) {
                this.ivSpec = null;
                this.iCount = 0;
                this.salt = null;
                throw e;
            }
        }

        /*
         * Obtain an IvParameterSpec for Cipher services. This method returns
         * null when the state is not initialized. Used by PBES2Core (SunJCE)
         * and P11PBECipher (SunPKCS11).
         */
        public IvParameterSpec getIvSpec() {
            return ivSpec;
        }

        /*
         * Obtain AlgorithmParameters for Cipher services. This method will
         * initialize PBES2Params if needed, generating new values randomly or
         * assigning from defaults. If PBES2Params is initialized, existing
         * values will be returned. Used by PBES2Core (SunJCE) and
         * P11PBECipher (SunPKCS11).
         */
        public AlgorithmParameters getAlgorithmParameters(int blkSize,
                String pbeAlgo, Provider algParamsProv, SecureRandom random) {
            AlgorithmParameters params;
            try {
                if (iCount == 0 && salt == null && ivSpec == null) {
                    initialize(blkSize, Cipher.ENCRYPT_MODE, 0, null, null,
                            random);
                }
                params = AlgorithmParameters.getInstance(pbeAlgo,
                        algParamsProv);
                params.init(new PBEParameterSpec(salt, iCount, ivSpec));
            } catch (NoSuchAlgorithmException nsae) {
                // should never happen
                throw new RuntimeException("AlgorithmParameters for "
                        + pbeAlgo + " not configured");
            } catch (InvalidParameterSpecException ipse) {
                // should never happen
                throw new RuntimeException("PBEParameterSpec not supported");
            } catch (InvalidAlgorithmParameterException iape) {
                // should never happen
                throw new RuntimeException("Error initializing PBES2Params");
            }
            return params;
        }

        /*
         * Initialize PBES2Params and obtain a PBEKeySpec for Cipher services.
         * Data from the key, parameters, defaults or random may be used for
         * initialization. Used by PBES2Core (SunJCE) and P11PBECipher
         * (SunPKCS11).
         */
        public PBEKeySpec getPBEKeySpec(int blkSize, int keyLength, int opmode,
                Key key, AlgorithmParameterSpec params, SecureRandom random)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            if (key == null) {
                throw new InvalidKeyException("Null key");
            }
            byte[] passwdBytes;
            char[] passwdChars = null;
            if (!(key.getAlgorithm().regionMatches(true, 0, "PBE", 0, 3)) ||
                    (passwdBytes = key.getEncoded()) == null) {
                throw new InvalidKeyException("Missing password");
            }
            try {
                int iCountInit;
                byte[] saltInit;
                AlgorithmParameterSpec ivSpecInit;
                // Extract from the supplied PBE params, if present
                if (params instanceof PBEParameterSpec pbeParams) {
                    // salt should be non-null per PBEParameterSpec
                    iCountInit = check(pbeParams.getIterationCount());
                    saltInit = check(pbeParams.getSalt());
                    ivSpecInit = pbeParams.getParameterSpec();
                } else if (params == null) {
                    // Try extracting from the key if present. If unspecified,
                    // PBEKey returns 0 and null respectively.
                    if (key instanceof javax.crypto.interfaces.PBEKey pbeKey) {
                        iCountInit = check(pbeKey.getIterationCount());
                        saltInit = check(pbeKey.getSalt());
                    } else {
                        iCountInit = 0;
                        saltInit = null;
                    }
                    ivSpecInit = null;
                } else {
                    throw new InvalidAlgorithmParameterException(
                            "Wrong parameter type: PBE expected");
                }
                initialize(blkSize, opmode, iCountInit, saltInit, ivSpecInit,
                        random);
                passwdChars = decodePassword(passwdBytes);
                return new PBEKeySpec(passwdChars, salt, iCount, keyLength);
            } finally {
                // password char[] was cloned in PBEKeySpec constructor,
                // so we can zero it out here
                if (passwdChars != null) Arrays.fill(passwdChars, '\0');
                if (passwdBytes != null) Arrays.fill(passwdBytes, (byte)0x00);
            }
        }

        /*
         * Obtain an AlgorithmParameterSpec from an AlgorithmParameters
         * instance, for Cipher services. Used by PBES2Core (SunJCE) and
         * P11PBECipher (SunPKCS11).
         */
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
            return iCount;
        }
    }

    /*
     * Obtain a PBEKeySpec for Mac services, after key and parameters
     * validation. Used by HmacPKCS12PBECore (SunJCE) and P11Mac (SunPKCS11).
     */
    public static PBEKeySpec getPBAKeySpec(Key key,
            AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        char[] passwdChars = null;
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
            passwdChars = decodePassword(passwdBytes);
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
            } else if (params instanceof PBEParameterSpec pbeParams) {
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
            } else {
                throw new InvalidAlgorithmParameterException(
                        "PBEParameterSpec type required");
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
            if (passwdChars != null) {
                Arrays.fill(passwdChars, '\0');
            }
        }
    }

    /*
     * Converts the password char[] to the UTF-8 encoded byte[]. Used by PBEKey
     * and PBKDF2KeyImpl (SunJCE).
     */
    public static byte[] encodePassword(char[] passwd) {
        ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(passwd));
        int len = bb.limit();
        byte[] passwdBytes = new byte[len];
        bb.get(passwdBytes, 0, len);
        bb.clear().put(new byte[len]);

        return passwdBytes;
    }

    // converts the UTF-8 encoded byte[] to the password char[]
    private static char[] decodePassword(byte[] passwdBytes) {
        CharBuffer cb = StandardCharsets.UTF_8.decode(
                ByteBuffer.wrap(passwdBytes));
        int len = cb.limit();
        char[] passwd = new char[len];
        cb.get(passwd);
        cb.clear().put(new char[len]);
        return passwd;
    }
}
