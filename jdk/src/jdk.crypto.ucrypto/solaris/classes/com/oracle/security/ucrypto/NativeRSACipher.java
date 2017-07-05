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

import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.Map;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.SecretKey;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

import javax.crypto.spec.SecretKeySpec;

import sun.security.internal.spec.TlsRsaPremasterSecretParameterSpec;
import sun.security.jca.JCAUtil;
import sun.security.util.KeyUtil;

/**
 * Asymmetric Cipher wrapper class utilizing ucrypto APIs. This class
 * currently supports
 * - RSA/ECB/NOPADDING
 * - RSA/ECB/PKCS1PADDING
 *
 * @since 9
 */
public class NativeRSACipher extends CipherSpi {
    // fields set in constructor
    private final UcryptoMech mech;
    private final int padLen;
    private final NativeRSAKeyFactory keyFactory;
    private AlgorithmParameterSpec spec;
    private SecureRandom random;

    // Keep a cache of RSA keys and their RSA NativeKey for reuse.
    // When the RSA key is gc'ed, we let NativeKey phatom references cleanup
    // the native allocation
    private static final Map<Key, NativeKey> keyList =
            Collections.synchronizedMap(new WeakHashMap<Key, NativeKey>());

    //
    // fields (re)set in every init()
    //
    private NativeKey key = null;
    private int outputSize = 0; // e.g. modulus size in bytes
    private boolean encrypt = true;
    private byte[] buffer;
    private int bufOfs = 0;

    // public implementation classes
    public static final class NoPadding extends NativeRSACipher {
        public NoPadding() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_RSA_X_509, 0);
        }
    }

    public static final class PKCS1Padding extends NativeRSACipher {
        public PKCS1Padding() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_RSA_PKCS, 11);
        }
    }

    NativeRSACipher(UcryptoMech mech, int padLen)
        throws NoSuchAlgorithmException {
        this.mech = mech;
        this.padLen = padLen;
        this.keyFactory = new NativeRSAKeyFactory();
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
        return 0;
    }

    // see JCE spec
    @Override
    protected synchronized int engineGetOutputSize(int inputLen) {
        return outputSize;
    }

    // see JCE spec
    @Override
    protected byte[] engineGetIV() {
        return null;
    }

    // see JCE spec
    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        if (!(key instanceof RSAKey)) {
            throw new InvalidKeyException("RSAKey required. Got: " +
                key.getClass().getName());
        }
        int n = ((RSAKey)key).getModulus().bitLength();
        // strip off the leading extra 0x00 byte prefix
        int realByteSize = (n + 7) >> 3;
        return realByteSize * 8;
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec)null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException("init() failed", e);
        }
    }

    // see JCE spec
    @Override
    @SuppressWarnings("deprecation")
    protected synchronized void engineInit(int opmode, Key newKey,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (newKey == null) {
            throw new InvalidKeyException("Key cannot be null");
        }
        if (opmode != Cipher.ENCRYPT_MODE &&
            opmode != Cipher.DECRYPT_MODE &&
            opmode != Cipher.WRAP_MODE &&
            opmode != Cipher.UNWRAP_MODE) {
            throw new InvalidAlgorithmParameterException
                ("Unsupported mode: " + opmode);
        }
        if (params != null) {
            if (!(params instanceof TlsRsaPremasterSecretParameterSpec)) {
                throw new InvalidAlgorithmParameterException(
                        "No Parameters can be specified");
            }
            spec = params;
            if (random == null) {
                random = JCAUtil.getSecureRandom();
            }
            this.random = random;   // for TLS RSA premaster secret
        }
        boolean doEncrypt = (opmode == Cipher.ENCRYPT_MODE || opmode == Cipher.WRAP_MODE);

        // Make sure the proper opmode uses the proper key
        if (doEncrypt && (!(newKey instanceof RSAPublicKey))) {
            throw new InvalidKeyException("RSAPublicKey required for encryption." +
                " Received: " + newKey.getClass().getName());
        } else if (!doEncrypt && (!(newKey instanceof RSAPrivateKey))) {
            throw new InvalidKeyException("RSAPrivateKey required for decryption." +
                " Received: " + newKey.getClass().getName());
        }

        NativeKey nativeKey = null;
        // Check keyList cache for a nativeKey
        nativeKey = keyList.get(newKey);
        if (nativeKey == null) {
            // With no existing nativeKey for this newKey, create one
            if (doEncrypt) {
                RSAPublicKey publicKey = (RSAPublicKey) newKey;
                try {
                    nativeKey = (NativeKey) keyFactory.engineGeneratePublic
                        (new RSAPublicKeySpec(publicKey.getModulus(), publicKey.getPublicExponent()));
                } catch (InvalidKeySpecException ikse) {
                    throw new InvalidKeyException(ikse);
                }
            } else {
                try {
                    if (newKey instanceof RSAPrivateCrtKey) {
                        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) newKey;
                        nativeKey = (NativeKey) keyFactory.engineGeneratePrivate
                            (new RSAPrivateCrtKeySpec(privateKey.getModulus(),
                                                      privateKey.getPublicExponent(),
                                                      privateKey.getPrivateExponent(),
                                                      privateKey.getPrimeP(),
                                                      privateKey.getPrimeQ(),
                                                      privateKey.getPrimeExponentP(),
                                                      privateKey.getPrimeExponentQ(),
                                                      privateKey.getCrtCoefficient()));
                    } else if (newKey instanceof RSAPrivateKey) {
                        RSAPrivateKey privateKey = (RSAPrivateKey) newKey;
                        nativeKey = (NativeKey) keyFactory.engineGeneratePrivate
                            (new RSAPrivateKeySpec(privateKey.getModulus(),
                                                   privateKey.getPrivateExponent()));
                    } else {
                        throw new InvalidKeyException("Unsupported type of RSAPrivateKey." +
                            " Received: " + newKey.getClass().getName());
                    }
                } catch (InvalidKeySpecException ikse) {
                    throw new InvalidKeyException(ikse);
                }
            }

            // Add nativeKey to keyList cache and associate it with newKey
            keyList.put(newKey, nativeKey);
        }

        init(doEncrypt, nativeKey);
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException("No Parameters can be specified");
        }
        engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        if (inLen > 0) {
            update(in, inOfs, inLen);
        }
        return null;
    }

    // see JCE spec
    @Override
    protected synchronized int engineUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
        if (out.length - outOfs < outputSize) {
            throw new ShortBufferException("Output buffer too small. outputSize: " +
                outputSize + ". out.length: " + out.length + ". outOfs: " + outOfs);
        }
        if (inLen > 0) {
            update(in, inOfs, inLen);
        }
        return 0;
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
        byte[] out = new byte[outputSize];
        try {
            // delegate to the other engineDoFinal(...) method
            int actualLen = engineDoFinal(in, inOfs, inLen, out, 0);
            if (actualLen != outputSize) {
                return Arrays.copyOf(out, actualLen);
            } else {
                return out;
            }
        } catch (ShortBufferException e) {
            throw new UcryptoException("Internal Error", e);
        }
    }

    // see JCE spec
    @Override
    protected synchronized int engineDoFinal(byte[] in, int inOfs, int inLen, byte[] out,
                                             int outOfs)
        throws ShortBufferException, IllegalBlockSizeException,
               BadPaddingException {
        if (inLen != 0) {
            update(in, inOfs, inLen);
        }
        return doFinal(out, outOfs, out.length - outOfs);
    }


    // see JCE spec
    @Override
    protected synchronized byte[] engineWrap(Key key) throws IllegalBlockSizeException,
                                                             InvalidKeyException {
        try {
            byte[] encodedKey = key.getEncoded();
            if ((encodedKey == null) || (encodedKey.length == 0)) {
                throw new InvalidKeyException("Cannot get an encoding of " +
                                              "the key to be wrapped");
            }
            if (encodedKey.length > buffer.length) {
                throw new InvalidKeyException("Key is too long for wrapping. " +
                    "encodedKey.length: " + encodedKey.length +
                    ". buffer.length: " + buffer.length);
            }
            return engineDoFinal(encodedKey, 0, encodedKey.length);
        } catch (BadPaddingException e) {
            // Should never happen for key wrapping
            throw new UcryptoException("Internal Error", e);
        }
    }

    // see JCE spec
    @Override
    @SuppressWarnings("deprecation")
    protected synchronized Key engineUnwrap(byte[] wrappedKey,
            String wrappedKeyAlgorithm, int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {

        if (wrappedKey.length > buffer.length) {
            throw new InvalidKeyException("Key is too long for unwrapping." +
                " wrappedKey.length: " + wrappedKey.length +
                ". buffer.length: " + buffer.length);
        }

        boolean isTlsRsaPremasterSecret =
                wrappedKeyAlgorithm.equals("TlsRsaPremasterSecret");
        Exception failover = null;

        byte[] encodedKey = null;
        try {
            encodedKey = engineDoFinal(wrappedKey, 0, wrappedKey.length);
        } catch (BadPaddingException bpe) {
            if (isTlsRsaPremasterSecret) {
                failover = bpe;
            } else {
                throw new InvalidKeyException("Unwrapping failed", bpe);
            }
        } catch (Exception e) {
            throw new InvalidKeyException("Unwrapping failed", e);
        }

        if (isTlsRsaPremasterSecret) {
            if (!(spec instanceof TlsRsaPremasterSecretParameterSpec)) {
                throw new IllegalStateException(
                        "No TlsRsaPremasterSecretParameterSpec specified");
            }

            // polish the TLS premaster secret
            encodedKey = KeyUtil.checkTlsPreMasterSecretKey(
                ((TlsRsaPremasterSecretParameterSpec)spec).getClientVersion(),
                ((TlsRsaPremasterSecretParameterSpec)spec).getServerVersion(),
                random, encodedKey, (failover != null));
        }

        return NativeCipher.constructKey(wrappedKeyType,
                encodedKey, wrappedKeyAlgorithm);
    }

    /**
     * calls ucrypto_encrypt(...) or ucrypto_decrypt(...)
     * @return the length of output or an negative error status code
     */
    private native static int nativeAtomic(int mech, boolean encrypt,
                                           long keyValue, int keyLength,
                                           byte[] in, int inLen,
                                           byte[] out, int ouOfs, int outLen);

    // do actual initialization
    private void init(boolean encrypt, NativeKey key) {
        this.encrypt = encrypt;
        this.key = key;
        try {
            this.outputSize = engineGetKeySize(key)/8;
        } catch (InvalidKeyException ike) {
            throw new UcryptoException("Internal Error", ike);
        }
        this.buffer = new byte[outputSize];
        this.bufOfs = 0;
    }

    // store the specified input into the internal buffer
    private void update(byte[] in, int inOfs, int inLen) {
        if ((inLen <= 0) || (in == null)) {
            return;
        }
        // buffer bytes internally until doFinal is called
        if ((bufOfs + inLen + (encrypt? padLen:0)) > buffer.length) {
            // lead to IllegalBlockSizeException when doFinal() is called
            bufOfs = buffer.length + 1;
            return;
        }
        System.arraycopy(in, inOfs, buffer, bufOfs, inLen);
        bufOfs += inLen;
    }

    // return the actual non-negative output length
    private int doFinal(byte[] out, int outOfs, int outLen)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        if (bufOfs > buffer.length) {
            throw new IllegalBlockSizeException(
                "Data must not be longer than " +
                (buffer.length - (encrypt ? padLen : 0)) + " bytes");
        }
        if (outLen < outputSize) {
            throw new ShortBufferException();
        }
        try {
            long keyValue = key.value();
            int k = nativeAtomic(mech.value(), encrypt, keyValue,
                                 key.length(), buffer, bufOfs,
                                 out, outOfs, outLen);
            if (k < 0) {
                if ( k == -16 || k == -64) {
                    // -16: CRYPTO_ENCRYPTED_DATA_INVALID
                    // -64: CKR_ENCRYPTED_DATA_INVALID, see bug 17459266
                    UcryptoException ue = new UcryptoException(16);
                    BadPaddingException bpe =
                        new BadPaddingException("Invalid encryption data");
                    bpe.initCause(ue);
                    throw bpe;
                }
                throw new UcryptoException(-k);
            }

            return k;
        } finally {
            bufOfs = 0;
        }
    }
}
