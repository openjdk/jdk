/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import java.util.Set;
import java.util.Arrays;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;

import sun.security.jca.JCAUtil;

/**
 * Cipher wrapper class utilizing ucrypto APIs. This class currently supports
 * - AES/GCM/NoPADDING
 *
 * @since 9
 */
class NativeGCMCipher extends NativeCipher {

    public static final class AesGcmNoPadding extends NativeGCMCipher {
        public AesGcmNoPadding() throws NoSuchAlgorithmException {
            super(-1);
        }
        public AesGcmNoPadding(int keySize) throws NoSuchAlgorithmException {
            super(keySize);
        }
    }

    private static final int DEFAULT_TAG_LEN = 128; // same as SunJCE provider

    // buffer for storing AAD data; if null, meaning buffer content has been
    // supplied to native context
    private ByteArrayOutputStream aadBuffer;

    // buffer for storing input in decryption, not used for encryption
    private ByteArrayOutputStream ibuffer;

    private int tagLen = DEFAULT_TAG_LEN;

    /*
     * variables used for performing the GCM (key+iv) uniqueness check.
     * To use GCM mode safely, the cipher object must be re-initialized
     * with a different combination of key + iv values for each
     * ENCRYPTION operation. However, checking all past key + iv values
     * isn't feasible. Thus, we only do a per-instance check of the
     * key + iv values used in previous encryption.
     * For decryption operations, no checking is necessary.
     */
    private boolean requireReinit;
    private byte[] lastEncKey = null;
    private byte[] lastEncIv = null;

    NativeGCMCipher(int fixedKeySize) throws NoSuchAlgorithmException {
        super(UcryptoMech.CRYPTO_AES_GCM, fixedKeySize);
    }

    @Override
    protected void ensureInitialized() {
        if (!initialized) {
            byte[] aad = null;
            if (aadBuffer != null) {
                if (aadBuffer.size() > 0) {
                    aad = aadBuffer.toByteArray();
                }
            }
            init(encrypt, keyValue, iv, tagLen, aad);
            aadBuffer = null;
            if (!initialized) {
                throw new UcryptoException("Cannot initialize Cipher");
            }
        }
    }

    @Override
    protected int getOutputSizeByOperation(int inLen, boolean isDoFinal) {
        if (inLen < 0) return 0;

        if (!isDoFinal && (inLen == 0)) {
            return 0;
        }

        int result = inLen + bytesBuffered;
        if (encrypt) {
            if (isDoFinal) {
                result += tagLen/8;
            }
        } else {
            if (ibuffer != null) {
                result += ibuffer.size();
            }
            result -= tagLen/8;
        }
        if (result < 0) {
            result = 0;
        }
        return result;
    }

    @Override
    protected void reset(boolean doCancel) {
        super.reset(doCancel);
        if (aadBuffer == null) {
            aadBuffer = new ByteArrayOutputStream();
        } else {
            aadBuffer.reset();
        }

        if (ibuffer != null) {
            ibuffer.reset();
        }
        if (!encrypt) requireReinit = false;
    }

    // actual init() implementation - caller should clone key and iv if needed
    protected void init(boolean encrypt, byte[] keyVal, byte[] ivVal, int tLen, byte[] aad) {
        reset(true);
        this.encrypt = encrypt;
        this.keyValue = keyVal;
        this.iv = ivVal;
        long pCtxtVal = NativeCipher.nativeInit(mech.value(), encrypt, keyValue, iv,
            tLen, aad);
        initialized = (pCtxtVal != 0L);
        if (initialized) {
            pCtxt = new CipherContextRef(this, pCtxtVal, encrypt);
        } else {
            throw new UcryptoException("Cannot initialize Cipher");
        }
    }

    // see JCE spec
    @Override
    protected synchronized AlgorithmParameters engineGetParameters() {
        AlgorithmParameters params = null;
        try {
            if (iv != null) {
                GCMParameterSpec gcmSpec = new GCMParameterSpec(tagLen, iv.clone());
                params = AlgorithmParameters.getInstance("GCM");
                params.init(gcmSpec);
            }
        } catch (GeneralSecurityException e) {
            // NoSuchAlgorithmException, NoSuchProviderException
            // InvalidParameterSpecException
            throw new UcryptoException("Could not encode parameters", e);
        }
        return params;
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
        aadBuffer = new ByteArrayOutputStream();
        boolean doEncrypt = (opmode == Cipher.ENCRYPT_MODE || opmode == Cipher.WRAP_MODE);
        byte[] keyBytes = key.getEncoded().clone();
        byte[] ivBytes = null;
        if (params != null) {
            if (!(params instanceof GCMParameterSpec)) {
                throw new InvalidAlgorithmParameterException("GCMParameterSpec required." +
                    " Received: " + params.getClass().getName());
            } else {
                tagLen = ((GCMParameterSpec) params).getTLen();
                ivBytes = ((GCMParameterSpec) params).getIV();
            }
        } else {
            if (doEncrypt) {
                tagLen = DEFAULT_TAG_LEN;

                // generate IV if none supplied for encryption
                ivBytes = new byte[blockSize];
                if (random == null) {
                    random = JCAUtil.getSecureRandom();
                }
                random.nextBytes(ivBytes);
            } else {
                throw new InvalidAlgorithmParameterException("Parameters required for decryption");
            }
        }
        if (doEncrypt) {
            requireReinit = Arrays.equals(ivBytes, lastEncIv) &&
                MessageDigest.isEqual(keyBytes, lastEncKey);
            if (requireReinit) {
                throw new InvalidAlgorithmParameterException
                    ("Cannot reuse iv for GCM encryption");
            }
            lastEncIv = ivBytes;
            lastEncKey = keyBytes;
            ibuffer = null;
        } else {
            requireReinit = false;
            ibuffer = new ByteArrayOutputStream();
        }
        init(doEncrypt, keyBytes, ivBytes, tagLen, null);
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        AlgorithmParameterSpec spec = null;
        if (params != null) {
            try {
                // mech must be UcryptoMech.CRYPTO_AES_GCM
                spec = params.getParameterSpec(GCMParameterSpec.class);
            } catch (InvalidParameterSpecException iaps) {
                throw new InvalidAlgorithmParameterException(iaps);
            }
        }
        engineInit(opmode, key, spec, random);
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        if (aadBuffer != null) {
            if (aadBuffer.size() > 0) {
                // init again with AAD data
                init(encrypt, keyValue, iv, tagLen, aadBuffer.toByteArray());
            }
            aadBuffer = null;
        }
        if (requireReinit) {
            throw new IllegalStateException
                ("Must use either different key or iv for GCM encryption");
        }
        if (inLen > 0) {
            if (!encrypt) {
                ibuffer.write(in, inOfs, inLen);
                return null;
            }
            return super.engineUpdate(in, inOfs, inLen);
        } else return null;
    }

    // see JCE spec
    @Override
    protected synchronized int engineUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
        int len = getOutputSizeByOperation(inLen, false);
        if (out.length - outOfs < len) {
            throw new ShortBufferException("Output buffer must be " +
                 "(at least) " + len + " bytes long. Got: " +
                 (out.length - outOfs));
        }
        if (aadBuffer != null) {
            if (aadBuffer.size() > 0) {
                // init again with AAD data
                init(encrypt, keyValue, iv, tagLen, aadBuffer.toByteArray());
            }
            aadBuffer = null;
        }
        if (requireReinit) {
            throw new IllegalStateException
                ("Must use either different key or iv for GCM encryption");
        }
        if (inLen > 0) {
            if (!encrypt) {
                ibuffer.write(in, inOfs, inLen);
                return 0;
            } else {
                return super.engineUpdate(in, inOfs, inLen, out, outOfs);
            }
        }
        return 0;
    }

    // see JCE spec
    @Override
    protected synchronized void engineUpdateAAD(byte[] src, int srcOfs, int srcLen)
            throws IllegalStateException {

        if ((src == null) || (srcOfs < 0) || (srcOfs + srcLen > src.length)) {
            throw new IllegalArgumentException("Invalid AAD");
        }
        if (keyValue == null) {
            throw new IllegalStateException("Need to initialize Cipher first");
        }
        if (requireReinit) {
            throw new IllegalStateException
                ("Must use either different key or iv for GCM encryption");
        }
        if (aadBuffer != null) {
            aadBuffer.write(src, srcOfs, srcLen);
        } else {
            // update has already been called
            throw new IllegalStateException
                ("Update has been called; no more AAD data");
        }
    }

    // see JCE spec
    @Override
    protected void engineUpdateAAD(ByteBuffer src)
            throws IllegalStateException {
        if (src == null) {
            throw new IllegalArgumentException("Invalid AAD");
        }
        if (keyValue == null) {
            throw new IllegalStateException("Need to initialize Cipher first");
        }
        if (requireReinit) {
            throw new IllegalStateException
                ("Must use either different key or iv for GCM encryption");
        }
        if (aadBuffer != null) {
            if (src.hasRemaining()) {
                byte[] srcBytes = new byte[src.remaining()];
                src.get(srcBytes);
                aadBuffer.write(srcBytes, 0, srcBytes.length);
            }
        } else {
            // update has already been called
            throw new IllegalStateException
                ("Update has been called; no more AAD data");
        }
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
        byte[] out = new byte[getOutputSizeByOperation(inLen, true)];
        try {
            // delegate to the other engineDoFinal(...) method
            int k = engineDoFinal(in, inOfs, inLen, out, 0);
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
        int len = getOutputSizeByOperation(inLen, true);
        if (out.length - outOfs < len) {
            throw new ShortBufferException("Output buffer must be "
                + "(at least) " + len + " bytes long. Got: " +
                (out.length - outOfs));
        }
        if (aadBuffer != null) {
            if (aadBuffer.size() > 0) {
                // init again with AAD data
                init(encrypt, keyValue, iv, tagLen, aadBuffer.toByteArray());
            }
            aadBuffer = null;
        }
        if (requireReinit) {
            throw new IllegalStateException
                ("Must use either different key or iv for GCM encryption");
        }
        if (!encrypt) {
            if (inLen > 0) {
                ibuffer.write(in, inOfs, inLen);
            }
            inLen = ibuffer.size();
            if (inLen < tagLen/8) {
                // Otherwise, Solaris lib will error out w/ CRYPTO_BUFFER_TOO_SMALL
                // when ucrypto_decrypt_final() is called
                throw new AEADBadTagException("Input too short - need tag." +
                    " inLen: " + inLen + ". tagLen: " + tagLen);
            }
            // refresh 'in' to all buffered-up bytes
            in = ibuffer.toByteArray();
            inOfs = 0;
            ibuffer.reset();
        }
        try {
            return super.engineDoFinal(in, inOfs, inLen, out, outOfs);
        } catch (UcryptoException ue) {
            if (ue.getMessage().equals("CRYPTO_INVALID_MAC")) {
                throw new AEADBadTagException("Tag does not match");
            } else {
                // pass it up
                throw ue;
            }
        } finally {
            requireReinit = encrypt;
        }
    }
}
