/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * This class implements password-base encryption algorithm with
 * SHA1 digest and the following Ciphers in CBC mode
 * - DESede cipher and
 * - RC2 Cipher with 40-bit effective key length
 * as defined by PKCS #12 version 1.0 standard.
 *
 * @author Valerie Peng
 * @see javax.crypto.CipherSpi
 */
final class PKCS12PBECipherCore {
    private CipherCore cipher;
    private int blockSize;
    private int keySize;
    private String algo = null;
    private byte[] salt = null;
    private int iCount = 0;

    private static final int DEFAULT_SALT_LENGTH = 20;
    private static final int DEFAULT_COUNT = 1024;

    static final int CIPHER_KEY = 1;
    static final int CIPHER_IV = 2;
    static final int MAC_KEY = 3;

    static byte[] derive(char[] chars, byte[] salt,
                         int ic, int n, int type) {
        // Add in trailing NULL terminator.
        int length = chars.length*2;
        if (length != 0) {
            length += 2;
        }
        byte[] passwd = new byte[length];
        for (int i = 0, j = 0; i < chars.length; i++, j+=2) {
            passwd[j] = (byte) ((chars[i] >>> 8) & 0xFF);
            passwd[j+1] = (byte) (chars[i] & 0xFF);
        }
        int v = 512 / 8;
        int u = 160 / 8;
        int c = roundup(n, u) / u;
        byte[] D = new byte[v];
        int s = roundup(salt.length, v);
        int p = roundup(passwd.length, v);
        byte[] I = new byte[s + p];
        byte[] key = new byte[n];

        Arrays.fill(D, (byte)type);
        concat(salt, I, 0, s);
        concat(passwd, I, s, p);

        try {
            MessageDigest sha = MessageDigest.getInstance("SHA1");
            byte[] Ai;
            byte[] B = new byte[v];
            byte[] tmp = new byte[v];

            int i = 0;
            for (; ; i++, n -= u) {
                sha.update(D);
                sha.update(I);
                Ai = sha.digest();
                for (int r = 1; r < ic; r++)
                    Ai = sha.digest(Ai);
                System.arraycopy(Ai, 0, key, u * i, Math.min(n, u));
                if (i + 1 == c)
                    break;
                concat(Ai, B, 0, B.length);
                BigInteger B1;
                B1 = new BigInteger(1, B).add(BigInteger.ONE);

                for (int j = 0; j < I.length; j += v) {
                    BigInteger Ij;
                    int trunc;

                    if (tmp.length != v)
                        tmp = new byte[v];
                    System.arraycopy(I, j, tmp, 0, v);
                    Ij = new BigInteger(1, tmp);
                    Ij = Ij.add(B1);
                    tmp = Ij.toByteArray();
                    trunc = tmp.length - v;
                    if (trunc >= 0) {
                        System.arraycopy(tmp, trunc, I, j, v);
                    } else if (trunc < 0) {
                        Arrays.fill(I, j, j + (-trunc), (byte)0);
                        System.arraycopy(tmp, 0, I, j + (-trunc), tmp.length);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("internal error: " + e);
        }
        return key;
    }

    private static int roundup(int x, int y) {
        return ((x + (y - 1)) / y) * y;
    }

    private static void concat(byte[] src, byte[] dst, int start, int len) {
        int loop = len / src.length;
        int off, i;
        for (i = 0, off = 0; i < loop; i++, off += src.length)
            System.arraycopy(src, 0, dst, off + start, src.length);
        System.arraycopy(src, 0, dst, off + start, len - off);
    }

    PKCS12PBECipherCore(String symmCipherAlg, int defKeySize)
        throws NoSuchAlgorithmException {
        algo = symmCipherAlg;
        SymmetricCipher symmCipher = null;
        if (algo.equals("DESede")) {
            symmCipher = new DESedeCrypt();
        } else if (algo.equals("RC2")) {
            symmCipher = new RC2Crypt();
        } else {
            throw new NoSuchAlgorithmException("No Cipher implementation " +
                       "for PBEWithSHA1And" + algo);
        }
        blockSize = symmCipher.getBlockSize();
        cipher = new CipherCore(symmCipher, blockSize);
        cipher.setMode("CBC");
        try {
            cipher.setPadding("PKCS5Padding");
        } catch (NoSuchPaddingException nspe) {
            // should not happen
        }
        keySize = defKeySize;
    }

    void implSetMode(String mode) throws NoSuchAlgorithmException {
        if ((mode != null) && (!mode.equalsIgnoreCase("CBC"))) {
            throw new NoSuchAlgorithmException("Invalid cipher mode: "
                                               + mode);
        }
    }

    void implSetPadding(String padding) throws NoSuchPaddingException {
        if ((padding != null) &&
            (!padding.equalsIgnoreCase("PKCS5Padding"))) {
            throw new NoSuchPaddingException("Invalid padding scheme: " +
                                             padding);
        }
    }

    int implGetBlockSize() {
        return blockSize;
    }

    int implGetOutputSize(int inLen) {
        return cipher.getOutputSize(inLen);
    }

    byte[] implGetIV() {
        return cipher.getIV();
    }

    AlgorithmParameters implGetParameters() {
        AlgorithmParameters params = null;
        if (salt == null) {
            // Cipher is not initialized with parameters;
            // follow the recommendation in PKCS12 v1.0
            // section B.4 to generate salt and iCount.
            salt = new byte[DEFAULT_SALT_LENGTH];
            SunJCE.RANDOM.nextBytes(salt);
            iCount = DEFAULT_COUNT;
        }
        PBEParameterSpec pbeSpec = new PBEParameterSpec(salt, iCount);
        try {
            params = AlgorithmParameters.getInstance("PBEWithSHA1And" +
                (algo.equalsIgnoreCase("RC2")?"RC2_40":algo), "SunJCE");
        } catch (GeneralSecurityException gse) {
            // should never happen
            throw new RuntimeException("SunJCE provider is not configured properly");
        }
        try {
            params.init(pbeSpec);
        } catch (InvalidParameterSpecException ipse) {
            // should never happen
            throw new RuntimeException("PBEParameterSpec not supported");
        }
        return params;
    }

    void implInit(int opmode, Key key, AlgorithmParameterSpec params,
                  SecureRandom random) throws InvalidKeyException,
        InvalidAlgorithmParameterException {
        char[] passwdChars = null;
        salt = null;
        iCount = 0;
        if (key instanceof javax.crypto.interfaces.PBEKey) {
            javax.crypto.interfaces.PBEKey pbeKey =
                (javax.crypto.interfaces.PBEKey) key;
            passwdChars = pbeKey.getPassword();
            salt = pbeKey.getSalt(); // maybe null if unspecified
            iCount = pbeKey.getIterationCount(); // maybe 0 if unspecified
        } else if (key instanceof SecretKey) {
            byte[] passwdBytes = key.getEncoded();
            if ((passwdBytes == null) ||
                !(key.getAlgorithm().regionMatches(true, 0, "PBE", 0, 3))) {
                throw new InvalidKeyException("Missing password");
            }
            passwdChars = new char[passwdBytes.length];
            for (int i=0; i<passwdChars.length; i++) {
                passwdChars[i] = (char) (passwdBytes[i] & 0x7f);
            }
        } else {
            throw new InvalidKeyException("SecretKey of PBE type required");
        }

        if (((opmode == Cipher.DECRYPT_MODE) ||
             (opmode == Cipher.UNWRAP_MODE)) &&
            ((params == null) && ((salt == null) || (iCount == 0)))) {
            throw new InvalidAlgorithmParameterException
                ("Parameters missing");
        }

        if (params == null) {
            // generate default for salt and iteration count if necessary
            if (salt == null) {
                salt = new byte[DEFAULT_SALT_LENGTH];
                if (random != null) {
                    random.nextBytes(salt);
                } else {
                    SunJCE.RANDOM.nextBytes(salt);
                }
            }
            if (iCount == 0) iCount = DEFAULT_COUNT;
        } else if (!(params instanceof PBEParameterSpec)) {
            throw new InvalidAlgorithmParameterException
                ("PBEParameterSpec type required");
        } else {
            PBEParameterSpec pbeParams = (PBEParameterSpec) params;
            // make sure the parameter values are consistent
            if (salt != null) {
                if (!Arrays.equals(salt, pbeParams.getSalt())) {
                    throw new InvalidAlgorithmParameterException
                        ("Inconsistent value of salt between key and params");
                }
            } else {
                salt = pbeParams.getSalt();
            }
            if (iCount != 0) {
                if (iCount != pbeParams.getIterationCount()) {
                    throw new InvalidAlgorithmParameterException
                        ("Different iteration count between key and params");
                }
            } else {
                iCount = pbeParams.getIterationCount();
            }
        }
        // salt is recommended to be ideally as long as the output
        // of the hash function. However, it may be too strict to
        // force this; so instead, we'll just require the minimum
        // salt length to be 8-byte which is what PKCS#5 recommends
        // and openssl does.
        if (salt.length < 8) {
            throw new InvalidAlgorithmParameterException
                ("Salt must be at least 8 bytes long");
        }
        if (iCount <= 0) {
            throw new InvalidAlgorithmParameterException
                ("IterationCount must be a positive number");
        }
        byte[] derivedKey = derive(passwdChars, salt, iCount,
                                   keySize, CIPHER_KEY);
        SecretKey cipherKey = new SecretKeySpec(derivedKey, algo);
        byte[] derivedIv = derive(passwdChars, salt, iCount, 8,
                                  CIPHER_IV);
        IvParameterSpec ivSpec = new IvParameterSpec(derivedIv, 0, 8);

        // initialize the underlying cipher
        cipher.init(opmode, cipherKey, ivSpec, random);
    }

    void implInit(int opmode, Key key, AlgorithmParameters params,
                  SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        AlgorithmParameterSpec paramSpec = null;
        if (params != null) {
            try {
                paramSpec = params.getParameterSpec(PBEParameterSpec.class);
            } catch (InvalidParameterSpecException ipse) {
                throw new InvalidAlgorithmParameterException("requires PBE parameters");
            }
        }
        implInit(opmode, key, paramSpec, random);
    }

    void implInit(int opmode, Key key, SecureRandom random)
        throws InvalidKeyException {
        try {
            implInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException iape) {
            throw new InvalidKeyException("requires PBE parameters");
        }
    }

    byte[] implUpdate(byte[] in, int inOff, int inLen) {
        return cipher.update(in, inOff, inLen);
    }

    int implUpdate(byte[] in, int inOff, int inLen, byte[] out, int outOff)
        throws ShortBufferException {
        return cipher.update(in, inOff, inLen, out, outOff);
    }

    byte[] implDoFinal(byte[] in, int inOff, int inLen)
        throws IllegalBlockSizeException, BadPaddingException {
        return cipher.doFinal(in, inOff, inLen);
    }

    int implDoFinal(byte[] in, int inOff, int inLen, byte[] out, int outOff)
        throws ShortBufferException, IllegalBlockSizeException,
               BadPaddingException {
        return cipher.doFinal(in, inOff, inLen, out, outOff);
    }

    int implGetKeySize(Key key) throws InvalidKeyException {
        return keySize;
    }

    byte[] implWrap(Key key) throws IllegalBlockSizeException,
        InvalidKeyException {
        return cipher.wrap(key);
    }

    Key implUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
                   int wrappedKeyType)
        throws InvalidKeyException, NoSuchAlgorithmException {
        return cipher.unwrap(wrappedKey, wrappedKeyAlgorithm,
                             wrappedKeyType);
    }

    public static final class PBEWithSHA1AndDESede extends CipherSpi {
        private final PKCS12PBECipherCore core;
        public PBEWithSHA1AndDESede() throws NoSuchAlgorithmException {
            core = new PKCS12PBECipherCore("DESede", 24);
        }
        protected byte[] engineDoFinal(byte[] in, int inOff, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
            return core.implDoFinal(in, inOff, inLen);
        }
        protected int engineDoFinal(byte[] in, int inOff, int inLen,
                                    byte[] out, int outOff)
            throws ShortBufferException, IllegalBlockSizeException,
                   BadPaddingException {
            return core.implDoFinal(in, inOff, inLen, out, outOff);
        }
        protected int engineGetBlockSize() {
            return core.implGetBlockSize();
        }
        protected byte[] engineGetIV() {
            return core.implGetIV();
        }
        protected int engineGetKeySize(Key key) throws InvalidKeyException {
            return core.implGetKeySize(key);
        }
        protected int engineGetOutputSize(int inLen) {
            return core.implGetOutputSize(inLen);
        }
        protected AlgorithmParameters engineGetParameters() {
            return core.implGetParameters();
        }
        protected void engineInit(int opmode, Key key,
                                  AlgorithmParameterSpec params,
                                  SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
            core.implInit(opmode, key, params, random);
        }
        protected void engineInit(int opmode, Key key,
                                  AlgorithmParameters params,
                                  SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
            core.implInit(opmode, key, params, random);
        }
        protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
            core.implInit(opmode, key, random);
        }
        protected void engineSetMode(String mode)
            throws NoSuchAlgorithmException {
            core.implSetMode(mode);
        }
        protected void engineSetPadding(String paddingScheme)
            throws NoSuchPaddingException {
            core.implSetPadding(paddingScheme);
        }
        protected Key engineUnwrap(byte[] wrappedKey,
                                   String wrappedKeyAlgorithm,
                                   int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {
            return core.implUnwrap(wrappedKey, wrappedKeyAlgorithm,
                                   wrappedKeyType);
        }
        protected byte[] engineUpdate(byte[] in, int inOff, int inLen) {
            return core.implUpdate(in, inOff, inLen);
        }
        protected int engineUpdate(byte[] in, int inOff, int inLen,
                                   byte[] out, int outOff)
            throws ShortBufferException {
            return core.implUpdate(in, inOff, inLen, out, outOff);
        }
        protected byte[] engineWrap(Key key)
            throws IllegalBlockSizeException, InvalidKeyException {
            return core.implWrap(key);
        }
    }

    public static final class PBEWithSHA1AndRC2_40 extends CipherSpi {
        private final PKCS12PBECipherCore core;
        public PBEWithSHA1AndRC2_40() throws NoSuchAlgorithmException {
            core = new PKCS12PBECipherCore("RC2", 5);
        }
        protected byte[] engineDoFinal(byte[] in, int inOff, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
            return core.implDoFinal(in, inOff, inLen);
        }
        protected int engineDoFinal(byte[] in, int inOff, int inLen,
                                    byte[] out, int outOff)
            throws ShortBufferException, IllegalBlockSizeException,
                   BadPaddingException {
            return core.implDoFinal(in, inOff, inLen, out, outOff);
        }
        protected int engineGetBlockSize() {
            return core.implGetBlockSize();
        }
        protected byte[] engineGetIV() {
            return core.implGetIV();
        }
        protected int engineGetKeySize(Key key) throws InvalidKeyException {
            return core.implGetKeySize(key);
        }
        protected int engineGetOutputSize(int inLen) {
            return core.implGetOutputSize(inLen);
        }
        protected AlgorithmParameters engineGetParameters() {
            return core.implGetParameters();
        }
        protected void engineInit(int opmode, Key key,
                                  AlgorithmParameterSpec params,
                                  SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
            core.implInit(opmode, key, params, random);
        }
        protected void engineInit(int opmode, Key key,
                                  AlgorithmParameters params,
                                  SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
            core.implInit(opmode, key, params, random);
        }
        protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
            core.implInit(opmode, key, random);
        }
        protected void engineSetMode(String mode)
            throws NoSuchAlgorithmException {
            core.implSetMode(mode);
        }
        protected void engineSetPadding(String paddingScheme)
            throws NoSuchPaddingException {
            core.implSetPadding(paddingScheme);
        }
        protected Key engineUnwrap(byte[] wrappedKey,
                                   String wrappedKeyAlgorithm,
                                   int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {
            return core.implUnwrap(wrappedKey, wrappedKeyAlgorithm,
                                   wrappedKeyType);
        }
        protected byte[] engineUpdate(byte[] in, int inOff, int inLen) {
            return core.implUpdate(in, inOff, inLen);
        }
        protected int engineUpdate(byte[] in, int inOff, int inLen,
                                   byte[] out, int outOff)
            throws ShortBufferException {
            return core.implUpdate(in, inOff, inLen, out, outOff);
        }
        protected byte[] engineWrap(Key key)
            throws IllegalBlockSizeException, InvalidKeyException {
            return core.implWrap(key);
        }
    }
}
