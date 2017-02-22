/*
 * Copyright (c) 2002, 2017, Oracle and/or its affiliates. All rights reserved.
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
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.BadPaddingException;
import java.nio.ByteBuffer;

/**
 * This class implements the AES algorithm in its various modes
 * (<code>ECB</code>, <code>CFB</code>, <code>OFB</code>, <code>CBC</code>,
 * <code>PCBC</code>) and padding schemes (<code>PKCS5Padding</code>,
 * <code>NoPadding</code>, <code>ISO10126Padding</code>).
 *
 * @author Valerie Peng
 *
 *
 * @see AESCrypt
 * @see CipherBlockChaining
 * @see ElectronicCodeBook
 * @see CipherFeedback
 * @see OutputFeedback
 */

abstract class AESCipher extends CipherSpi {
    public static final class General extends AESCipher {
        public General() {
            super(-1);
        }
    }
    abstract static class OidImpl extends AESCipher {
        protected OidImpl(int keySize, String mode, String padding) {
            super(keySize);
            try {
                engineSetMode(mode);
                engineSetPadding(padding);
            } catch (GeneralSecurityException gse) {
                // internal error; re-throw as provider exception
                ProviderException pe =new ProviderException("Internal Error");
                pe.initCause(gse);
                throw pe;
            }
        }
    }
    public static final class AES128_ECB_NoPadding extends OidImpl {
        public AES128_ECB_NoPadding() {
            super(16, "ECB", "NOPADDING");
        }
    }
    public static final class AES192_ECB_NoPadding extends OidImpl {
        public AES192_ECB_NoPadding() {
            super(24, "ECB", "NOPADDING");
        }
    }
    public static final class AES256_ECB_NoPadding extends OidImpl {
        public AES256_ECB_NoPadding() {
            super(32, "ECB", "NOPADDING");
        }
    }
    public static final class AES128_CBC_NoPadding extends OidImpl {
        public AES128_CBC_NoPadding() {
            super(16, "CBC", "NOPADDING");
        }
    }
    public static final class AES192_CBC_NoPadding extends OidImpl {
        public AES192_CBC_NoPadding() {
            super(24, "CBC", "NOPADDING");
        }
    }
    public static final class AES256_CBC_NoPadding extends OidImpl {
        public AES256_CBC_NoPadding() {
            super(32, "CBC", "NOPADDING");
        }
    }
    public static final class AES128_OFB_NoPadding extends OidImpl {
        public AES128_OFB_NoPadding() {
            super(16, "OFB", "NOPADDING");
        }
    }
    public static final class AES192_OFB_NoPadding extends OidImpl {
        public AES192_OFB_NoPadding() {
            super(24, "OFB", "NOPADDING");
        }
    }
    public static final class AES256_OFB_NoPadding extends OidImpl {
        public AES256_OFB_NoPadding() {
            super(32, "OFB", "NOPADDING");
        }
    }
    public static final class AES128_CFB_NoPadding extends OidImpl {
        public AES128_CFB_NoPadding() {
            super(16, "CFB", "NOPADDING");
        }
    }
    public static final class AES192_CFB_NoPadding extends OidImpl {
        public AES192_CFB_NoPadding() {
            super(24, "CFB", "NOPADDING");
        }
    }
    public static final class AES256_CFB_NoPadding extends OidImpl {
        public AES256_CFB_NoPadding() {
            super(32, "CFB", "NOPADDING");
        }
    }
    public static final class AES128_GCM_NoPadding extends OidImpl {
        public AES128_GCM_NoPadding() {
            super(16, "GCM", "NOPADDING");
        }
    }
    public static final class AES192_GCM_NoPadding extends OidImpl {
        public AES192_GCM_NoPadding() {
            super(24, "GCM", "NOPADDING");
        }
    }
    public static final class AES256_GCM_NoPadding extends OidImpl {
        public AES256_GCM_NoPadding() {
            super(32, "GCM", "NOPADDING");
        }
    }

    // utility method used by AESCipher and AESWrapCipher
    static final void checkKeySize(Key key, int fixedKeySize)
        throws InvalidKeyException {
        if (fixedKeySize != -1) {
            if (key == null) {
                throw new InvalidKeyException("The key must not be null");
            }
            byte[] value = key.getEncoded();
            if (value == null) {
                throw new InvalidKeyException("Key encoding must not be null");
            } else if (value.length != fixedKeySize) {
                throw new InvalidKeyException("The key must be " +
                    fixedKeySize + " bytes");
            }
        }
    }

    /*
     * internal CipherCore object which does the real work.
     */
    private CipherCore core = null;

    /*
     * needed to support AES oids which associates a fixed key size
     * to the cipher object.
     */
    private final int fixedKeySize; // in bytes, -1 if no restriction

    /*
     * needed to enforce ISE thrown when updateAAD is called after update for GCM mode.
     */
    private boolean updateCalled;

    /**
     * Creates an instance of AES cipher with default ECB mode and
     * PKCS5Padding.
     */
    protected AESCipher(int keySize) {
        core = new CipherCore(new AESCrypt(), AESConstants.AES_BLOCK_SIZE);
        fixedKeySize = keySize;
    }

    /**
     * Sets the mode of this cipher.
     *
     * @param mode the cipher mode
     *
     * @exception NoSuchAlgorithmException if the requested cipher mode does
     * not exist
     */
    protected void engineSetMode(String mode)
        throws NoSuchAlgorithmException {
        core.setMode(mode);
    }

    /**
     * Sets the padding mechanism of this cipher.
     *
     * @param padding the padding mechanism
     *
     * @exception NoSuchPaddingException if the requested padding mechanism
     * does not exist
     */
    protected void engineSetPadding(String paddingScheme)
        throws NoSuchPaddingException {
        core.setPadding(paddingScheme);
    }

    /**
     * Returns the block size (in bytes).
     *
     * @return the block size (in bytes), or 0 if the underlying algorithm is
     * not a block cipher
     */
    protected int engineGetBlockSize() {
        return AESConstants.AES_BLOCK_SIZE;
    }

    /**
     * Returns the length in bytes that an output buffer would need to be in
     * order to hold the result of the next <code>update</code> or
     * <code>doFinal</code> operation, given the input length
     * <code>inputLen</code> (in bytes).
     *
     * <p>This call takes into account any unprocessed (buffered) data from a
     * previous <code>update</code> call, and padding.
     *
     * <p>The actual output length of the next <code>update</code> or
     * <code>doFinal</code> call may be smaller than the length returned by
     * this method.
     *
     * @param inputLen the input length (in bytes)
     *
     * @return the required output buffer size (in bytes)
     */
    protected int engineGetOutputSize(int inputLen) {
        return core.getOutputSize(inputLen);
    }

    /**
     * Returns the initialization vector (IV) in a new buffer.
     *
     * <p>This is useful in the case where a random IV has been created
     * (see <a href = "#init">init</a>),
     * or in the context of password-based encryption or
     * decryption, where the IV is derived from a user-provided password.
     *
     * @return the initialization vector in a new buffer, or null if the
     * underlying algorithm does not use an IV, or if the IV has not yet
     * been set.
     */
    protected byte[] engineGetIV() {
        return core.getIV();
    }

    /**
     * Returns the parameters used with this cipher.
     *
     * <p>The returned parameters may be the same that were used to initialize
     * this cipher, or may contain the default set of parameters or a set of
     * randomly generated parameters used by the underlying cipher
     * implementation (provided that the underlying cipher implementation
     * uses a default set of parameters or creates new parameters if it needs
     * parameters but was not initialized with any).
     *
     * @return the parameters used with this cipher, or null if this cipher
     * does not use any parameters.
     */
    protected AlgorithmParameters engineGetParameters() {
        return core.getParameters("AES");
    }

    /**
     * Initializes this cipher with a key and a source of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or key unwrapping, depending on
     * the value of <code>opmode</code>.
     *
     * <p>If this cipher requires an initialization vector (IV), it will get
     * it from <code>random</code>.
     * This behaviour should only be used in encryption or key wrapping
     * mode, however.
     * When initializing a cipher that requires an IV for decryption or
     * key unwrapping, the IV
     * (same IV that was used for encryption or key wrapping) must be provided
     * explicitly as a
     * parameter, in order to get the correct result.
     *
     * <p>This method also cleans existing buffer and other related state
     * information.
     *
     * @param opmode the operation mode of this cipher (this is one of
     * the following:
     * <code>ENCRYPT_MODE</code>, <code>DECRYPT_MODE</code>,
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>)
     * @param key the secret key
     * @param random the source of randomness
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     */
    protected void engineInit(int opmode, Key key, SecureRandom random)
        throws InvalidKeyException {
        checkKeySize(key, fixedKeySize);
        updateCalled = false;
        core.init(opmode, key, random);
    }

    /**
     * Initializes this cipher with a key, a set of
     * algorithm parameters, and a source of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or key unwrapping, depending on
     * the value of <code>opmode</code>.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes, it will get them from <code>random</code>.
     *
     * @param opmode the operation mode of this cipher (this is one of
     * the following:
     * <code>ENCRYPT_MODE</code>, <code>DECRYPT_MODE</code>,
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>)
     * @param key the encryption key
     * @param params the algorithm parameters
     * @param random the source of randomness
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     * @exception InvalidAlgorithmParameterException if the given algorithm
     * parameters are inappropriate for this cipher
     */
    protected void engineInit(int opmode, Key key,
                              AlgorithmParameterSpec params,
                              SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        checkKeySize(key, fixedKeySize);
        updateCalled = false;
        core.init(opmode, key, params, random);
    }

    protected void engineInit(int opmode, Key key,
                              AlgorithmParameters params,
                              SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        checkKeySize(key, fixedKeySize);
        updateCalled = false;
        core.init(opmode, key, params, random);
    }

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code>, are processed, and the
     * result is stored in a new buffer.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     *
     * @return the new buffer with the result
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     */
    protected byte[] engineUpdate(byte[] input, int inputOffset,
                                  int inputLen) {
        updateCalled = true;
        return core.update(input, inputOffset, inputLen);
    }

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code>, are processed, and the
     * result is stored in the <code>output</code> buffer, starting at
     * <code>outputOffset</code>.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     * @param output the buffer for the result
     * @param outputOffset the offset in <code>output</code> where the result
     * is stored
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception ShortBufferException if the given output buffer is too small
     * to hold the result
     */
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen,
                               byte[] output, int outputOffset)
        throws ShortBufferException {
        updateCalled = true;
        return core.update(input, inputOffset, inputLen, output,
                           outputOffset);
    }

    /**
     * Encrypts or decrypts data in a single-part operation,
     * or finishes a multiple-part operation.
     * The data is encrypted or decrypted, depending on how this cipher was
     * initialized.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code>, and any input bytes that
     * may have been buffered during a previous <code>update</code> operation,
     * are processed, with padding (if requested) being applied.
     * The result is stored in a new buffer.
     *
     * <p>The cipher is reset to its initial state (uninitialized) after this
     * call.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     *
     * @return the new buffer with the result
     *
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     */
    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
        throws IllegalBlockSizeException, BadPaddingException {
        byte[] out = core.doFinal(input, inputOffset, inputLen);
        updateCalled = false;
        return out;
    }

    /**
     * Encrypts or decrypts data in a single-part operation,
     * or finishes a multiple-part operation.
     * The data is encrypted or decrypted, depending on how this cipher was
     * initialized.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code>, and any input bytes that
     * may have been buffered during a previous <code>update</code> operation,
     * are processed, with padding (if requested) being applied.
     * The result is stored in the <code>output</code> buffer, starting at
     * <code>outputOffset</code>.
     *
     * <p>The cipher is reset to its initial state (uninitialized) after this
     * call.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     * @param output the buffer for the result
     * @param outputOffset the offset in <code>output</code> where the result
     * is stored
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size
     * @exception ShortBufferException if the given output buffer is too small
     * to hold the result
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     */
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen,
                                byte[] output, int outputOffset)
        throws IllegalBlockSizeException, ShortBufferException,
               BadPaddingException {
        int outLen = core.doFinal(input, inputOffset, inputLen, output,
                                  outputOffset);
        updateCalled = false;
        return outLen;
    }

    /**
     *  Returns the key size of the given key object.
     *
     * @param key the key object.
     *
     * @return the key size of the given key object.
     *
     * @exception InvalidKeyException if <code>key</code> is invalid.
     */
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        byte[] encoded = key.getEncoded();
        if (!AESCrypt.isKeySizeValid(encoded.length)) {
            throw new InvalidKeyException("Invalid AES key length: " +
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
        return core.wrap(key);
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
        return core.unwrap(wrappedKey, wrappedKeyAlgorithm,
                           wrappedKeyType);
    }

    /**
     * Continues a multi-part update of the Additional Authentication
     * Data (AAD), using a subset of the provided buffer.
     * <p>
     * Calls to this method provide AAD to the cipher when operating in
     * modes such as AEAD (GCM/CCM).  If this cipher is operating in
     * either GCM or CCM mode, all AAD must be supplied before beginning
     * operations on the ciphertext (via the {@code update} and {@code
     * doFinal} methods).
     *
     * @param src the buffer containing the AAD
     * @param offset the offset in {@code src} where the AAD input starts
     * @param len the number of AAD bytes
     *
     * @throws IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized), does not accept AAD, or if
     * operating in either GCM or CCM mode and one of the {@code update}
     * methods has already been called for the active
     * encryption/decryption operation
     * @throws UnsupportedOperationException if this method
     * has not been overridden by an implementation
     *
     * @since 1.8
     */
    @Override
    protected void engineUpdateAAD(byte[] src, int offset, int len) {
        if (core.getMode() == CipherCore.GCM_MODE && updateCalled) {
            throw new IllegalStateException("AAD must be supplied before encryption/decryption starts");
        }
        core.updateAAD(src, offset, len);
    }

    /**
     * Continues a multi-part update of the Additional Authentication
     * Data (AAD).
     * <p>
     * Calls to this method provide AAD to the cipher when operating in
     * modes such as AEAD (GCM/CCM).  If this cipher is operating in
     * either GCM or CCM mode, all AAD must be supplied before beginning
     * operations on the ciphertext (via the {@code update} and {@code
     * doFinal} methods).
     * <p>
     * All {@code src.remaining()} bytes starting at
     * {@code src.position()} are processed.
     * Upon return, the input buffer's position will be equal
     * to its limit; its limit will not have changed.
     *
     * @param src the buffer containing the AAD
     *
     * @throws IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized), does not accept AAD, or if
     * operating in either GCM or CCM mode and one of the {@code update}
     * methods has already been called for the active
     * encryption/decryption operation
     * @throws UnsupportedOperationException if this method
     * has not been overridden by an implementation
     *
     * @since 1.8
     */
    @Override
    protected void engineUpdateAAD(ByteBuffer src) {
        if (core.getMode() == CipherCore.GCM_MODE && updateCalled) {
            throw new IllegalStateException("AAD must be supplied before encryption/decryption starts");
        }
        if (src != null) {
            int aadLen = src.limit() - src.position();
            if (aadLen > 0) {
                if (src.hasArray()) {
                    int aadOfs = Math.addExact(src.arrayOffset(), src.position());
                    core.updateAAD(src.array(), aadOfs, aadLen);
                    src.position(src.limit());
                } else {
                    byte[] aad = new byte[aadLen];
                    src.get(aad);
                    core.updateAAD(aad, 0, aadLen);
                }
            }
        }
    }
}

