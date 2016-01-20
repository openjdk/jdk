/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.security.ucrypto;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListSet;
import java.lang.ref.*;

import java.security.*;
import java.security.spec.*;
import javax.crypto.*;

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

import sun.security.jca.JCAUtil;

/**
 * Cipher wrapper class utilizing ucrypto APIs. This class currently supports
 * - AES/ECB/NOPADDING
 * - AES/CBC/NOPADDING
 * - AES/CTR/NOPADDING
 * - AES/CFB128/NOPADDING
 * (Support for GCM mode is inside the child class NativeGCMCipher)
 *
 * @since 9
 */
class NativeCipher extends CipherSpi {

    // public implementation classes
    public static final class AesEcbNoPadding extends NativeCipher {
        public AesEcbNoPadding() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_AES_ECB);
        }
        public AesEcbNoPadding(int keySize) throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_AES_ECB, keySize);
        }
    }
    public static final class AesCbcNoPadding extends NativeCipher {
        public AesCbcNoPadding() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_AES_CBC);
        }
        public AesCbcNoPadding(int keySize) throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_AES_CBC, keySize);
        }
    }
    public static final class AesCtrNoPadding extends NativeCipher {
        public AesCtrNoPadding() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_AES_CTR);
        }
    }
    public static final class AesCfb128NoPadding extends NativeCipher {
        public AesCfb128NoPadding() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_AES_CFB128);
        }
    }

    // ok as constants since AES is all we support
    public static final int AES_BLOCK_SIZE = 16;
    public static final String AES_KEY_ALGO = "AES";

    // fields set in constructor
    protected final UcryptoMech mech;
    protected String keyAlgo;
    protected int blockSize;
    protected int fixedKeySize;

    //
    // fields (re)set in every init()
    //
    protected CipherContextRef pCtxt = null;
    protected byte[] keyValue = null;
    protected byte[] iv = null;
    protected boolean initialized = false;
    protected boolean encrypt = true;
    protected int bytesBuffered = 0;

    // private utility methods for key re-construction
    private static final PublicKey constructPublicKey(byte[] encodedKey,
                                              String encodedKeyAlgorithm)
        throws InvalidKeyException, NoSuchAlgorithmException {

        PublicKey key = null;
        try {
            KeyFactory keyFactory =
                KeyFactory.getInstance(encodedKeyAlgorithm);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
            key = keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException nsae) {
            throw new NoSuchAlgorithmException("No provider found for " +
                                               encodedKeyAlgorithm +
                                               " KeyFactory");
        } catch (InvalidKeySpecException ikse) {
            // Should never happen
            throw new InvalidKeyException("Cannot construct public key", ikse);
        }
        return key;
    }

    private static final PrivateKey constructPrivateKey(byte[] encodedKey,
                                                String encodedKeyAlgorithm)
        throws InvalidKeyException, NoSuchAlgorithmException {

        PrivateKey key = null;
        try {
            KeyFactory keyFactory =
                KeyFactory.getInstance(encodedKeyAlgorithm);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedKey);
            key = keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException nsae) {
            throw new NoSuchAlgorithmException("No provider found for " +
                                               encodedKeyAlgorithm +
                                               " KeyFactory");
        } catch (InvalidKeySpecException ikse) {
            // Should never happen
            throw new InvalidKeyException("Cannot construct private key", ikse);
        }
        return key;
    }

    private static final SecretKey constructSecretKey(byte[] encodedKey,
                                              String encodedKeyAlgorithm) {
        return new SecretKeySpec(encodedKey, encodedKeyAlgorithm);
    }

    // package-private utility method for general key re-construction
    static final Key constructKey(int keyType, byte[] encodedKey,
                                  String encodedKeyAlgorithm)
        throws InvalidKeyException, NoSuchAlgorithmException {
        Key result = null;
        switch (keyType) {
        case Cipher.SECRET_KEY:
            result = constructSecretKey(encodedKey,
                                        encodedKeyAlgorithm);
            break;
        case Cipher.PRIVATE_KEY:
            result = constructPrivateKey(encodedKey,
                                         encodedKeyAlgorithm);
            break;
        case Cipher.PUBLIC_KEY:
            result = constructPublicKey(encodedKey,
                                        encodedKeyAlgorithm);
            break;
        }
        return result;
    }

    NativeCipher(UcryptoMech mech, int fixedKeySize) throws NoSuchAlgorithmException {
        this.mech = mech;
        // defaults to AES - the only supported symmetric cipher algo
        this.blockSize = AES_BLOCK_SIZE;
        this.keyAlgo = AES_KEY_ALGO;
        this.fixedKeySize = fixedKeySize;
    }

    NativeCipher(UcryptoMech mech) throws NoSuchAlgorithmException {
        this(mech, -1);
    }

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        // Disallow change of mode for now since currently it's explicitly
        // defined in transformation strings
        throw new NoSuchAlgorithmException("Unsupported mode " + mode);
    }

    // see JCE spec
    @Override
    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException {
        // Disallow change of padding for now since currently it's explicitly
        // defined in transformation strings
        throw new NoSuchPaddingException("Unsupported padding " + padding);
    }

    // see JCE spec
    @Override
    protected int engineGetBlockSize() {
        return blockSize;
    }

    // see JCE spec
    @Override
    protected synchronized int engineGetOutputSize(int inputLen) {
        return getOutputSizeByOperation(inputLen, true);
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineGetIV() {
        return (iv != null? iv.clone() : null);
    }

    // see JCE spec
    @Override
    protected synchronized AlgorithmParameters engineGetParameters() {
        AlgorithmParameters params = null;
        try {
            if (iv != null) {
                IvParameterSpec ivSpec = new IvParameterSpec(iv.clone());
                params = AlgorithmParameters.getInstance(keyAlgo);
                params.init(ivSpec);
            }
        } catch (GeneralSecurityException e) {
            // NoSuchAlgorithmException, NoSuchProviderException
            // InvalidParameterSpecException
            throw new UcryptoException("Could not encode parameters", e);
        }
        return params;
    }

    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        return checkKey(key) * 8;
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key,
            SecureRandom random) throws InvalidKeyException {
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec)null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException("init() failed", e);
        }
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        checkKey(key);
        if (opmode != Cipher.ENCRYPT_MODE &&
            opmode != Cipher.DECRYPT_MODE &&
            opmode != Cipher.WRAP_MODE &&
            opmode != Cipher.UNWRAP_MODE) {
            throw new InvalidAlgorithmParameterException
                ("Unsupported mode: " + opmode);
        }
        boolean doEncrypt =
                (opmode == Cipher.ENCRYPT_MODE || opmode == Cipher.WRAP_MODE);

        byte[] ivBytes = null;
        if (mech == UcryptoMech.CRYPTO_AES_ECB) {
            if (params != null) {
                throw new InvalidAlgorithmParameterException
                        ("No Parameters for ECB mode");
            }
        } else {
            if (params != null) {
                if (!(params instanceof IvParameterSpec)) {
                    throw new InvalidAlgorithmParameterException
                            ("IvParameterSpec required. Received: " +
                            params.getClass().getName());
                } else {
                    ivBytes = ((IvParameterSpec) params).getIV();
                    if (ivBytes.length != blockSize) {
                        throw new InvalidAlgorithmParameterException
                             ("Wrong IV length: must be " + blockSize +
                              " bytes long. Received length:" + ivBytes.length);
                    }
                }
            } else {
                if (encrypt) {
                    // generate IV if none supplied for encryption
                    ivBytes = new byte[blockSize];
                    if (random == null) {
                        random = JCAUtil.getSecureRandom();
                    }
                    random.nextBytes(ivBytes);
                } else {
                    throw new InvalidAlgorithmParameterException
                            ("Parameters required for decryption");
                }
            }
        }
        init(doEncrypt, key.getEncoded().clone(), ivBytes);
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key,
            AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        AlgorithmParameterSpec spec = null;
        if (params != null) {
            try {
                spec = params.getParameterSpec(IvParameterSpec.class);
            } catch (InvalidParameterSpecException iaps) {
                throw new InvalidAlgorithmParameterException(iaps);
            }
        }
        engineInit(opmode, key, spec, random);
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineUpdate(byte[] in, int ofs, int len) {
        byte[] out = new byte[getOutputSizeByOperation(len, false)];
        int n = update(in, ofs, len, out, 0);
        if (n == 0) {
            return null;
        } else if (out.length != n) {
            out = Arrays.copyOf(out, n);
        }
        return out;
    }

    // see JCE spec
    @Override
    protected synchronized int engineUpdate(byte[] in, int inOfs, int inLen,
        byte[] out, int outOfs) throws ShortBufferException {
        int min = getOutputSizeByOperation(inLen, false);
        if (out.length - outOfs < min) {
            throw new ShortBufferException("min " + min + "-byte buffer needed");
        }
        return update(in, inOfs, inLen, out, outOfs);
    }

    // see JCE spec
    @Override
    protected synchronized void engineUpdateAAD(byte[] src, int ofs, int len)
            throws IllegalStateException {
        throw new IllegalStateException("No AAD can be supplied");
    }

    // see JCE spec
    @Override
    protected void engineUpdateAAD(ByteBuffer src)
            throws IllegalStateException {
        throw new IllegalStateException("No AAD can be supplied");
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineDoFinal(byte[] in, int ofs, int len)
            throws IllegalBlockSizeException, BadPaddingException {
        byte[] out = new byte[getOutputSizeByOperation(len, true)];
        try {
            // delegate to the other engineDoFinal(...) method
            int k = engineDoFinal(in, ofs, len, out, 0);
            if (out.length != k) {
                out = Arrays.copyOf(out, k);
            }
            return out;
        } catch (ShortBufferException e) {
            throw new UcryptoException("Internal Error", e);
        }
    }

    // see JCE spec
    @Override
    protected synchronized int engineDoFinal(byte[] in, int inOfs, int inLen,
                                             byte[] out, int outOfs)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int k = 0;
        int min = getOutputSizeByOperation(inLen, true);
        if (out.length - outOfs < min) {
            throw new ShortBufferException("min " + min + "-byte buffer needed");
        }
        if (inLen > 0) {
            k = update(in, inOfs, inLen, out, outOfs);
            outOfs += k;
        }
        k += doFinal(out, outOfs);
        return k;
    }


    // see JCE spec
    @Override
    protected synchronized byte[] engineWrap(Key key)
            throws IllegalBlockSizeException, InvalidKeyException {
        byte[] result = null;
        try {
            byte[] encodedKey = key.getEncoded();
            if ((encodedKey == null) || (encodedKey.length == 0)) {
                throw new InvalidKeyException("Cannot get an encoding of " +
                                              "the key to be wrapped");
            }
            result = engineDoFinal(encodedKey, 0, encodedKey.length);
        } catch (BadPaddingException e) {
            // Should never happen for key wrapping
            throw new UcryptoException("Internal Error" , e);
        }
        return result;
    }

    // see JCE spec
    @Override
    protected synchronized Key engineUnwrap(byte[] wrappedKey,
            String wrappedKeyAlgorithm, int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {

        byte[] encodedKey;
        Key result = null;
        try {
            encodedKey = engineDoFinal(wrappedKey, 0,
                                       wrappedKey.length);
        } catch (Exception e) {
            throw (InvalidKeyException)
                (new InvalidKeyException()).initCause(e);
        }

        return constructKey(wrappedKeyType, encodedKey, wrappedKeyAlgorithm);
    }

    final int checkKey(Key key) throws InvalidKeyException {
        if (key == null || key.getEncoded() == null) {
            throw new InvalidKeyException("Key cannot be null");
        } else {
            // check key algorithm and format
            if (!keyAlgo.equalsIgnoreCase(key.getAlgorithm())) {
                throw new InvalidKeyException("Key algorithm must be " +
                    keyAlgo);
            }
            if (!"RAW".equalsIgnoreCase(key.getFormat())) {
                throw new InvalidKeyException("Key format must be RAW");
            }
            int keyLen = key.getEncoded().length;
            if (fixedKeySize == -1) {
                // all 3 AES key lengths are allowed
                if (keyLen != 16 && keyLen != 24 && keyLen != 32) {
                    throw new InvalidKeyException("Key size is not valid." +
                        " Got key length of: " + keyLen);
                }
            } else {
                if (keyLen != fixedKeySize) {
                    throw new InvalidKeyException("Only " + fixedKeySize +
                        "-byte keys are accepted. Got: " + keyLen);
                }
            }
            // return the validated key length in bytes
            return keyLen;
        }
    }

    protected void reset(boolean doCancel) {
        initialized = false;
        bytesBuffered = 0;
        if (pCtxt != null) {
            pCtxt.dispose(doCancel);
            pCtxt = null;
        }
    }

    /**
     * calls ucrypto_encrypt_init(...) or ucrypto_decrypt_init(...)
     * @return pointer to the context
     */
    protected native static long nativeInit(int mech, boolean encrypt,
                                            byte[] key, byte[] iv,
                                            int tagLen, byte[] aad);

    /**
     * calls ucrypto_encrypt_update(...) or ucrypto_decrypt_update(...)
     * @return the length of output or if negative, an error status code
     */
    private native static int nativeUpdate(long pContext, boolean encrypt,
                                           byte[] in, int inOfs, int inLen,
                                           byte[] out, int outOfs);

    /**
     * calls ucrypto_encrypt_final(...) or ucrypto_decrypt_final(...)
     * @return the length of output or if negative, an error status code
     */
    native static int nativeFinal(long pContext, boolean encrypt,
                                          byte[] out, int outOfs);

    protected void ensureInitialized() {
        if (!initialized) {
            init(encrypt, keyValue, iv);
            if (!initialized) {
                throw new UcryptoException("Cannot initialize Cipher");
            }
        }
    }

    protected int getOutputSizeByOperation(int inLen, boolean isDoFinal) {
        if (inLen <= 0) {
            inLen = 0;
        }
        if (!isDoFinal && (inLen == 0)) {
            return 0;
        }
        return inLen + bytesBuffered;
    }

    // actual init() implementation - caller should clone key and iv if needed
    protected void init(boolean encrypt, byte[] keyVal, byte[] ivVal) {
        reset(true);
        this.encrypt = encrypt;
        this.keyValue = keyVal;
        this.iv = ivVal;
        long pCtxtVal = nativeInit(mech.value(), encrypt, keyValue, iv, 0, null);
        initialized = (pCtxtVal != 0L);
        if (initialized) {
            pCtxt = new CipherContextRef(this, pCtxtVal, encrypt);
        } else {
            throw new UcryptoException("Cannot initialize Cipher");
        }
    }

    // Caller MUST check and ensure output buffer has enough capacity
    private int update(byte[] in, int inOfs, int inLen, byte[] out, int outOfs) {
        ensureInitialized();
        if (inLen <= 0) { return 0; }

        int k = nativeUpdate(pCtxt.id, encrypt, in, inOfs, inLen, out, outOfs);
        if (k < 0) {
            reset(false);
            // cannot throw ShortBufferException here since it's too late
            // native context is invalid upon any failure
            throw new UcryptoException(-k);
        }
        bytesBuffered += (inLen - k);
        return k;
    }

    // Caller MUST check and ensure output buffer has enough capacity
    private int doFinal(byte[] out, int outOfs) throws IllegalBlockSizeException,
            BadPaddingException {
        try {
            ensureInitialized();

            int k = nativeFinal(pCtxt.id, encrypt, out, outOfs);
            if (k < 0) {
                String cause = UcryptoException.getErrorMessage(-k);
                if (cause.endsWith("_LEN_RANGE")) {
                    throw new IllegalBlockSizeException(cause);
                } else if (cause.endsWith("_DATA_INVALID")) {
                    throw new BadPaddingException(cause);
                } else {
                    throw new UcryptoException(-k);
                }
            }
            return k;
        } finally {
            reset(false);
        }
    }
}
