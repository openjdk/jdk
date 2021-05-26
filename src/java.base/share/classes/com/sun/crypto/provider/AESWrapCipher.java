/*
 * Copyright (c) 2004, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * This class implements the AES KeyWrap algorithm as defined
 * in <a href=http://www.w3.org/TR/xmlenc-core/#sec-Alg-SymmetricKeyWrap>
 * "XML Encryption Syntax and Processing" section 5.6.3 "AES Key Wrap".
 * Note: only <code>ECB</code> mode and <code>NoPadding</code> padding
 * can be used for this algorithm.
 *
 * @author Valerie Peng
 *
 *
 * @see AESCipher
 */
abstract class AESWrapCipher extends CipherSpi {
    public static final class General extends AESWrapCipher {
        public General() {
            super(-1);
        }
    }
    public static final class AES128 extends AESWrapCipher {
        public AES128() {
            super(16);
        }
    }
    public static final class AES192 extends AESWrapCipher {
        public AES192() {
            super(24);
        }
    }
    public static final class AES256 extends AESWrapCipher {
        public AES256() {
            super(32);
        }
    }
    private static final byte[] IV = {
        (byte) 0xA6, (byte) 0xA6, (byte) 0xA6, (byte) 0xA6,
        (byte) 0xA6, (byte) 0xA6, (byte) 0xA6, (byte) 0xA6
    };

    private static final int blksize = AESConstants.AES_BLOCK_SIZE;

    /*
     * internal cipher object which does the real work.
     */
    private AESCrypt cipher;

    /*
     * are we encrypting or decrypting?
     */
    private boolean decrypting = false;

    /*
     * needed to support AES oids which associates a fixed key size
     * to the cipher object.
     */
    private final int fixedKeySize; // in bytes, -1 if no restriction

    /**
     * Creates an instance of AES KeyWrap cipher with default
     * mode, i.e. "ECB" and padding scheme, i.e. "NoPadding".
     */
    public AESWrapCipher(int keySize) {
        cipher = new AESCrypt();
        fixedKeySize = keySize;

    }

    /**
     * Sets the mode of this cipher. Only "ECB" mode is accepted for this
     * cipher.
     *
     * @param mode the cipher mode
     *
     * @exception NoSuchAlgorithmException if the requested cipher mode
     * is not "ECB".
     */
    protected void engineSetMode(String mode)
        throws NoSuchAlgorithmException {
        if (!mode.equalsIgnoreCase("ECB")) {
            throw new NoSuchAlgorithmException(mode + " cannot be used");
        }
    }

    /**
     * Sets the padding mechanism of this cipher. Only "NoPadding" schmem
     * is accepted for this cipher.
     *
     * @param padding the padding mechanism
     *
     * @exception NoSuchPaddingException if the requested padding mechanism
     * is not "NoPadding".
     */
    protected void engineSetPadding(String padding)
        throws NoSuchPaddingException {
        if (!padding.equalsIgnoreCase("NoPadding")) {
            throw new NoSuchPaddingException(padding + " cannot be used");
        }
    }

    /**
     * Returns the block size (in bytes). i.e. 16 bytes.
     *
     * @return the block size (in bytes), i.e. 16 bytes.
     */
    protected int engineGetBlockSize() {
        return blksize;
    }

    /**
     * Returns the length in bytes that an output buffer would need to be
     * given the input length <code>inputLen</code> (in bytes).
     *
     * <p>The actual output length of the next <code>update</code> or
     * <code>doFinal</code> call may be smaller than the length returned
     * by this method.
     *
     * @param inputLen the input length (in bytes)
     *
     * @return the required output buffer size (in bytes)
     */
    protected int engineGetOutputSize(int inputLen) {
        // can only return an upper-limit if not initialized yet.
        int result = 0;
        if (decrypting) {
            result = inputLen - 8;
        } else {
            result = Math.addExact(inputLen, 8);
        }
        return (result < 0? 0:result);
    }

    /**
     * Returns the initialization vector (IV) which is null for this cipher.
     *
     * @return null for this cipher.
     */
    protected byte[] engineGetIV() {
        return null;
    }

    /**
     * Initializes this cipher with a key and a source of randomness.
     *
     * <p>The cipher only supports the following two operation modes:<b>
     * Cipher.WRAP_MODE, and <b>
     * Cipher.UNWRAP_MODE.
     * <p>For modes other than the above two, UnsupportedOperationException
     * will be thrown.
     *
     * @param opmode the operation mode of this cipher. Only
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>) are accepted.
     * @param key the secret key.
     * @param random the source of randomness.
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher.
     */
    protected void engineInit(int opmode, Key key, SecureRandom random)
        throws InvalidKeyException {
        if (opmode == Cipher.WRAP_MODE) {
            decrypting = false;
        } else if (opmode == Cipher.UNWRAP_MODE) {
            decrypting = true;
        } else {
            throw new UnsupportedOperationException("This cipher can " +
                "only be used for key wrapping and unwrapping");
        }
        AESCipher.checkKeySize(key, fixedKeySize);
        byte[] encoded = key.getEncoded();
        try {
            cipher.init(decrypting, key.getAlgorithm(), encoded);
        } finally {
            Arrays.fill(encoded, (byte)0);
        }
    }

    /**
     * Initializes this cipher with a key, a set of algorithm parameters,
     * and a source of randomness.
     *
     * <p>The cipher only supports the following two operation modes:<b>
     * Cipher.WRAP_MODE, and <b>
     * Cipher.UNWRAP_MODE.
     * <p>For modes other than the above two, UnsupportedOperationException
     * will be thrown.
     *
     * @param opmode the operation mode of this cipher. Only
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>) are accepted.
     * @param key the secret key.
     * @param params the algorithm parameters; must be null for this cipher.
     * @param random the source of randomness.
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     * @exception InvalidAlgorithmParameterException if the given algorithm
     * parameters is not null.
     */
    protected void engineInit(int opmode, Key key,
                              AlgorithmParameterSpec params,
                              SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException("This cipher " +
                "does not accept any parameters");
        }
        engineInit(opmode, key, random);
    }

    /**
     * Initializes this cipher with a key, a set of algorithm parameters,
     * and a source of randomness.
     *
     * <p>The cipher only supports the following two operation modes:<b>
     * Cipher.WRAP_MODE, and <b>
     * Cipher.UNWRAP_MODE.
     * <p>For modes other than the above two, UnsupportedOperationException
     * will be thrown.
     *
     * @param opmode the operation mode of this cipher. Only
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>) are accepted.
     * @param key the secret key.
     * @param params the algorithm parameters; must be null for this cipher.
     * @param random the source of randomness.
     *
     * @exception InvalidKeyException if the given key is inappropriate.
     * @exception InvalidAlgorithmParameterException if the given algorithm
     * parameters is not null.
     */
    protected void engineInit(int opmode, Key key,
                              AlgorithmParameters params,
                              SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException("This cipher " +
                "does not accept any parameters");
        }
        engineInit(opmode, key, random);
    }

    /**
     * This operation is not supported by this cipher.
     * Since it's impossible to initialize this cipher given the
     * current Cipher.engineInit(...) implementation,
     * IllegalStateException will always be thrown upon invocation.
     *
     * @param in the input buffer.
     * @param inOffset the offset in <code>in</code> where the input
     * starts.
     * @param inLen the input length.
     *
     * @return n/a.
     *
     * @exception IllegalStateException upon invocation of this method.
     */
    protected byte[] engineUpdate(byte[] in, int inOffset, int inLen) {
        throw new IllegalStateException("Cipher has not been initialized");
    }

    /**
     * This operation is not supported by this cipher.
     * Since it's impossible to initialize this cipher given the
     * current Cipher.engineInit(...) implementation,
     * IllegalStateException will always be thrown upon invocation.
     *
     * @param in the input buffer.
     * @param inOffset the offset in <code>in</code> where the input
     * starts.
     * @param inLen the input length.
     * @param out the buffer for the result.
     * @param outOffset the offset in <code>out</code> where the result
     * is stored.
     *
     * @return n/a.
     *
     * @exception IllegalStateException upon invocation of this method.
     */
    protected int engineUpdate(byte[] in, int inOffset, int inLen,
                               byte[] out, int outOffset)
        throws ShortBufferException {
        throw new IllegalStateException("Cipher has not been initialized");
    }

    /**
     * This operation is not supported by this cipher.
     * Since it's impossible to initialize this cipher given the
     * current Cipher.engineInit(...) implementation,
     * IllegalStateException will always be thrown upon invocation.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>in</code> where the input
     * starts
     * @param inputLen the input length.
     *
     * @return n/a.
     *
     * @exception IllegalStateException upon invocation of this method.
     */
    protected byte[] engineDoFinal(byte[] input, int inputOffset,
                                   int inputLen)
        throws IllegalBlockSizeException, BadPaddingException {
        throw new IllegalStateException("Cipher has not been initialized");
    }

    /**
     * This operation is not supported by this cipher.
     * Since it's impossible to initialize this cipher given the
     * current Cipher.engineInit(...) implementation,
     * IllegalStateException will always be thrown upon invocation.
     *
     * @param in the input buffer.
     * @param inOffset the offset in <code>in</code> where the input
     * starts.
     * @param inLen the input length.
     * @param out the buffer for the result.
     * @param outOffset the ofset in <code>out</code> where the result
     * is stored.
     *
     * @return n/a.
     *
     * @exception IllegalStateException upon invocation of this method.
     */
    protected int engineDoFinal(byte[] in, int inOffset, int inLen,
                                byte[] out, int outOffset)
        throws IllegalBlockSizeException, ShortBufferException,
               BadPaddingException {
        throw new IllegalStateException("Cipher has not been initialized");
    }

    /**
     * Returns the parameters used with this cipher which is always null
     * for this cipher.
     *
     * @return null since this cipher does not use any parameters.
     */
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    /**
     * Returns the key size of the given key object in number of bits.
     *
     * @param key the key object.
     *
     * @return the "effective" key size of the given key object.
     *
     * @exception InvalidKeyException if <code>key</code> is invalid.
     */
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        byte[] encoded = key.getEncoded();
        Arrays.fill(encoded, (byte)0);
        if (!AESCrypt.isKeySizeValid(encoded.length)) {
            throw new InvalidKeyException("Invalid key length: " +
                                          encoded.length + " bytes");
        }
        return Math.multiplyExact(encoded.length, 8);
    }

    /**
     * Wrap a key.
     *
     * @param key the key to be wrapped.
     *
     * @return the wrapped key.
     *
     * @exception IllegalBlockSizeException if this cipher is a block
     * cipher, no padding has been requested, and the length of the
     * encoding of the key to be wrapped is not a
     * multiple of the block size.
     *
     * @exception InvalidKeyException if it is impossible or unsafe to
     * wrap the key with this cipher (e.g., a hardware protected key is
     * being passed to a software only cipher).
     */
    protected byte[] engineWrap(Key key)
        throws IllegalBlockSizeException, InvalidKeyException {
        byte[] keyVal = key.getEncoded();
        if ((keyVal == null) || (keyVal.length == 0)) {
            throw new InvalidKeyException("Cannot get an encoding of " +
                                          "the key to be wrapped");
        }
        try {
            byte[] out = new byte[Math.addExact(keyVal.length, 8)];

            if (keyVal.length == 8) {
                System.arraycopy(IV, 0, out, 0, IV.length);
                System.arraycopy(keyVal, 0, out, IV.length, 8);
                cipher.encryptBlock(out, 0, out, 0);
            } else {
                if (keyVal.length % 8 != 0) {
                    throw new IllegalBlockSizeException("length of the " +
                            "to be wrapped key should be multiples of 8 bytes");
                }
                System.arraycopy(IV, 0, out, 0, IV.length);
                System.arraycopy(keyVal, 0, out, IV.length, keyVal.length);
                int N = keyVal.length / 8;
                byte[] buffer = new byte[blksize];
                for (int j = 0; j < 6; j++) {
                    for (int i = 1; i <= N; i++) {
                        int T = i + j * N;
                        System.arraycopy(out, 0, buffer, 0, IV.length);
                        System.arraycopy(out, i * 8, buffer, IV.length, 8);
                        cipher.encryptBlock(buffer, 0, buffer, 0);
                        for (int k = 1; T != 0; k++) {
                            byte v = (byte) T;
                            buffer[IV.length - k] ^= v;
                            T >>>= 8;
                        }
                        System.arraycopy(buffer, 0, out, 0, IV.length);
                        System.arraycopy(buffer, 8, out, 8 * i, 8);
                    }
                }
            }
            return out;
        } finally {
            Arrays.fill(keyVal, (byte)0);
        }
    }

    /**
     * Unwrap a previously wrapped key.
     *
     * @param wrappedKey the key to be unwrapped.
     *
     * @param wrappedKeyAlgorithm the algorithm the wrapped key is for.
     *
     * @param wrappedKeyType the type of the wrapped key.
     * This is one of <code>Cipher.SECRET_KEY</code>,
     * <code>Cipher.PRIVATE_KEY</code>, or <code>Cipher.PUBLIC_KEY</code>.
     *
     * @return the unwrapped key.
     *
     * @exception NoSuchAlgorithmException if no installed providers
     * can create keys of type <code>wrappedKeyType</code> for the
     * <code>wrappedKeyAlgorithm</code>.
     *
     * @exception InvalidKeyException if <code>wrappedKey</code> does not
     * represent a wrapped key of type <code>wrappedKeyType</code> for
     * the <code>wrappedKeyAlgorithm</code>.
     */
    protected Key engineUnwrap(byte[] wrappedKey,
                               String wrappedKeyAlgorithm,
                               int wrappedKeyType)
        throws InvalidKeyException, NoSuchAlgorithmException {
        int wrappedKeyLen = wrappedKey.length;
        // ensure the wrappedKey length is multiples of 8 bytes and non-zero
        if (wrappedKeyLen == 0) {
            throw new InvalidKeyException("The wrapped key is empty");
        }
        if (wrappedKeyLen % 8 != 0) {
            throw new InvalidKeyException
                ("The wrapped key has invalid key length");
        }
        byte[] out = new byte[wrappedKeyLen - 8];
        byte[] buffer = new byte[blksize];
        try {
            if (wrappedKeyLen == 16) {
                cipher.decryptBlock(wrappedKey, 0, buffer, 0);
                for (int i = 0; i < IV.length; i++) {
                    if (IV[i] != buffer[i]) {
                        throw new InvalidKeyException("Integrity check failed");
                    }
                }
                System.arraycopy(buffer, IV.length, out, 0, out.length);
            } else {
                System.arraycopy(wrappedKey, 0, buffer, 0, IV.length);
                System.arraycopy(wrappedKey, IV.length, out, 0, out.length);
                int N = out.length / 8;
                for (int j = 5; j >= 0; j--) {
                    for (int i = N; i > 0; i--) {
                        int T = i + j * N;
                        System.arraycopy(out, 8 * (i - 1), buffer, IV.length, 8);
                        for (int k = 1; T != 0; k++) {
                            byte v = (byte) T;
                            buffer[IV.length - k] ^= v;
                            T >>>= 8;
                        }
                        cipher.decryptBlock(buffer, 0, buffer, 0);
                        System.arraycopy(buffer, IV.length, out, 8 * (i - 1), 8);
                    }
                }
                for (int i = 0; i < IV.length; i++) {
                    if (IV[i] != buffer[i]) {
                        throw new InvalidKeyException("Integrity check failed");
                    }
                }
            }
            return ConstructKeys.constructKey(out, wrappedKeyAlgorithm,
                    wrappedKeyType);
        } finally {
            Arrays.fill(out, (byte)0);
            Arrays.fill(buffer, (byte)0);
        }
    }
}
