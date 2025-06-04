/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.security.spec.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

import jdk.internal.access.SharedSecrets;
import sun.security.util.PBEUtil;

/**
 * This class represents password-based encryption as defined by the PKCS #5
 * standard.
 * These algorithms implement PBE with HmacSHA1/HmacSHA2-family and AES-CBC.
 * Padding is done as described in PKCS #5.
 *
 * @author Jan Luehe
 *
 *
 * @see javax.crypto.Cipher
 */
abstract class PBES2Core extends CipherSpi {
    // the encapsulated cipher
    private final CipherCore cipher;
    private final int keyLength; // in bits
    private final int blkSize; // in bits
    private final PBKDF2Core kdf;
    private final String pbeAlgo;
    private final String cipherAlgo;
    private final PBEUtil.PBES2Params pbes2Params = new PBEUtil.PBES2Params();

    /**
     * Creates an instance of PBE Scheme 2 according to the selected
     * password-based key derivation function and encryption scheme.
     */
    PBES2Core(String kdfAlgo, String cipherAlgo, int keySize)
        throws NoSuchAlgorithmException, NoSuchPaddingException {

        this.cipherAlgo = cipherAlgo;
        keyLength = keySize * 8;
        pbeAlgo = "PBEWith" + kdfAlgo + "And" + cipherAlgo + "_" + keyLength;

        if (cipherAlgo.equals("AES")) {
            blkSize = AESConstants.AES_BLOCK_SIZE;
            cipher = new CipherCore(new AESCrypt(), blkSize);

            switch(kdfAlgo) {
            case "HmacSHA1":
                kdf = new PBKDF2Core.HmacSHA1();
                break;
            case "HmacSHA224":
                kdf = new PBKDF2Core.HmacSHA224();
                break;
            case "HmacSHA256":
                kdf = new PBKDF2Core.HmacSHA256();
                break;
            case "HmacSHA384":
                kdf = new PBKDF2Core.HmacSHA384();
                break;
            case "HmacSHA512":
                kdf = new PBKDF2Core.HmacSHA512();
                break;
            case "HmacSHA512/224":
                kdf = new PBKDF2Core.HmacSHA512_224();
                break;
            case "HmacSHA512/256":
                kdf = new PBKDF2Core.HmacSHA512_256();
                break;
            default:
                throw new NoSuchAlgorithmException(
                    "No Cipher implementation for " + kdfAlgo);
            }
        } else {
            throw new NoSuchAlgorithmException("No Cipher implementation for " +
                                               pbeAlgo);
        }
        cipher.setMode("CBC");
        cipher.setPadding("PKCS5Padding");
    }

    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if ((mode != null) && (!mode.equalsIgnoreCase("CBC"))) {
            throw new NoSuchAlgorithmException("Invalid cipher mode: " + mode);
        }
    }

    protected void engineSetPadding(String paddingScheme)
        throws NoSuchPaddingException {
        if ((paddingScheme != null) &&
            (!paddingScheme.equalsIgnoreCase("PKCS5Padding"))) {
            throw new NoSuchPaddingException("Invalid padding scheme: " +
                                             paddingScheme);
        }
    }

    protected int engineGetBlockSize() {
        return blkSize;
    }

    protected int engineGetOutputSize(int inputLen) {
        return cipher.getOutputSize(inputLen);
    }

    protected byte[] engineGetIV() {
        return cipher.getIV();
    }

    protected AlgorithmParameters engineGetParameters() {
        return pbes2Params.getAlgorithmParameters(
                blkSize, pbeAlgo, SunJCE.getInstance(), SunJCE.getRandom());
    }

    protected void engineInit(int opmode, Key key, SecureRandom random)
        throws InvalidKeyException {
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException ie) {
            throw new InvalidKeyException("requires PBE parameters", ie);
        }
    }

    protected void engineInit(int opmode, Key key,
                              AlgorithmParameterSpec params,
                              SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {

        PBEKeySpec pbeSpec = pbes2Params.getPBEKeySpec(blkSize, keyLength,
                opmode, key, params, random);
        PBKDF2KeyImpl s = null;
        byte[] derivedKey;
        try {
            s = (PBKDF2KeyImpl)kdf.engineGenerateSecret(pbeSpec);
            derivedKey = s.getEncoded();
        } catch (InvalidKeySpecException ikse) {
            throw new InvalidKeyException("Cannot construct PBE key", ikse);
        } finally {
            if (s != null) {
                s.clear();
            }
            pbeSpec.clearPassword();
        }

        SecretKeySpec cipherKey = null;
        try {
            cipherKey = new SecretKeySpec(derivedKey, cipherAlgo);
            // initialize the underlying cipher
            cipher.init(opmode, cipherKey, pbes2Params.getIvSpec(), random);
        } finally {
            if (cipherKey != null) {
                SharedSecrets.getJavaxCryptoSpecAccess()
                        .clearSecretKeySpec(cipherKey);
            }
            Arrays.fill(derivedKey, (byte) 0);
        }
    }

    protected void engineInit(int opmode, Key key, AlgorithmParameters params,
                              SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        engineInit(opmode, key, PBEUtil.PBES2Params.getParameterSpec(params),
                random);
    }

    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        return cipher.update(input, inputOffset, inputLen);
    }

    protected int engineUpdate(byte[] input, int inputOffset, int inputLen,
                               byte[] output, int outputOffset)
        throws ShortBufferException {
        return cipher.update(input, inputOffset, inputLen,
                             output, outputOffset);
    }

    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
        throws IllegalBlockSizeException, BadPaddingException {
        return cipher.doFinal(input, inputOffset, inputLen);
    }

    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen,
                                byte[] output, int outputOffset)
        throws ShortBufferException, IllegalBlockSizeException,
               BadPaddingException {
        return cipher.doFinal(input, inputOffset, inputLen,
                              output, outputOffset);
    }

    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        return keyLength;
    }

    protected byte[] engineWrap(Key key)
        throws IllegalBlockSizeException, InvalidKeyException {
        return cipher.wrap(key);
    }

    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
                               int wrappedKeyType)
        throws InvalidKeyException, NoSuchAlgorithmException {
        byte[] encodedKey;
        return cipher.unwrap(wrappedKey, wrappedKeyAlgorithm,
                             wrappedKeyType);
    }

    public static final class HmacSHA1AndAES_128 extends PBES2Core {
        public HmacSHA1AndAES_128()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA1", "AES", 16);
        }
    }

    public static final class HmacSHA224AndAES_128 extends PBES2Core {
        public HmacSHA224AndAES_128()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA224", "AES", 16);
        }
    }

    public static final class HmacSHA256AndAES_128 extends PBES2Core {
        public HmacSHA256AndAES_128()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA256", "AES", 16);
        }
    }

    public static final class HmacSHA384AndAES_128 extends PBES2Core {
        public HmacSHA384AndAES_128()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA384", "AES", 16);
        }
    }

    public static final class HmacSHA512AndAES_128 extends PBES2Core {
        public HmacSHA512AndAES_128()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA512", "AES", 16);
        }
    }

    public static final class HmacSHA512_224AndAES_128 extends PBES2Core {
        public HmacSHA512_224AndAES_128()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA512/224", "AES", 16);
        }
    }

    public static final class HmacSHA512_256AndAES_128 extends PBES2Core {
        public HmacSHA512_256AndAES_128()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA512/256", "AES", 16);
        }
    }

    public static final class HmacSHA1AndAES_256 extends PBES2Core {
        public HmacSHA1AndAES_256()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA1", "AES", 32);
        }
    }

    public static final class HmacSHA224AndAES_256 extends PBES2Core {
        public HmacSHA224AndAES_256()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA224", "AES", 32);
        }
    }

    public static final class HmacSHA256AndAES_256 extends PBES2Core {
        public HmacSHA256AndAES_256()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA256", "AES", 32);
        }
    }

    public static final class HmacSHA384AndAES_256 extends PBES2Core {
        public HmacSHA384AndAES_256()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA384", "AES", 32);
        }
    }

    public static final class HmacSHA512AndAES_256 extends PBES2Core {
        public HmacSHA512AndAES_256()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA512", "AES", 32);
        }
    }

    public static final class HmacSHA512_224AndAES_256 extends PBES2Core {
        public HmacSHA512_224AndAES_256()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA512/224", "AES", 32);
        }
    }
    public static final class HmacSHA512_256AndAES_256 extends PBES2Core {
        public HmacSHA512_256AndAES_256()
            throws NoSuchAlgorithmException, NoSuchPaddingException {
            super("HmacSHA512/256", "AES", 32);
        }
    }
}
