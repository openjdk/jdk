/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Locale;

import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.BadPaddingException;

/**
 * This class represents the symmetric algorithms in its various modes
 * (<code>ECB</code>, <code>CFB</code>, <code>OFB</code>, <code>CBC</code>,
 * <code>PCBC</code>, <code>CTR</code>, and <code>CTS</code>) and
 * padding schemes (<code>PKCS5Padding</code>, <code>NoPadding</code>,
 * <code>ISO10126Padding</code>).
 *
 * @author Gigi Ankeny
 * @author Jan Luehe
 * @see ElectronicCodeBook
 * @see CipherFeedback
 * @see OutputFeedback
 * @see CipherBlockChaining
 * @see PCBC
 * @see CounterMode
 * @see CipherTextStealing
 */

final class CipherCore {

    /*
     * internal buffer
     */
    private byte[] buffer = null;

    /*
     * internal buffer
     */
    private int blockSize = 0;

    /*
     * unit size (number of input bytes that can be processed at a time)
     */
    private int unitBytes = 0;

    /*
     * index of the content size left in the buffer
     */
    private int buffered = 0;

    /*
     * minimum number of bytes in the buffer required for
     * FeedbackCipher.encryptFinal()/decryptFinal() call.
     * update() must buffer this many bytes before before starting
     * to encrypt/decrypt data.
     * currently, only CTS mode has a non-zero value due to its special
     * handling on the last two blocks (the last one may be incomplete).
     */
    private int minBytes = 0;

    /*
     * number of bytes needed to make the total input length a multiple
     * of the blocksize (this is used in feedback mode, when the number of
     * input bytes that are processed at a time is different from the block
     * size)
     */
    private int diffBlocksize = 0;

    /*
     * padding class
     */
    private Padding padding = null;

    /*
     * internal cipher engine
     */
    private FeedbackCipher cipher = null;

    /*
     * the cipher mode
     */
    private int cipherMode = ECB_MODE;

    /*
     * are we encrypting or decrypting?
     */
    private boolean decrypting = false;

    /*
     * Block Mode constants
     */
    private static final int ECB_MODE = 0;
    private static final int CBC_MODE = 1;
    private static final int CFB_MODE = 2;
    private static final int OFB_MODE = 3;
    private static final int PCBC_MODE = 4;
    private static final int CTR_MODE = 5;
    private static final int CTS_MODE = 6;

    /**
     * Creates an instance of CipherCore with default ECB mode and
     * PKCS5Padding.
     */
    CipherCore(SymmetricCipher impl, int blkSize) {
        blockSize = blkSize;
        unitBytes = blkSize;
        diffBlocksize = blkSize;

        /*
         * The buffer should be usable for all cipher mode and padding
         * schemes. Thus, it has to be at least (blockSize+1) for CTS.
         * In decryption mode, it also hold the possible padding block.
         */
        buffer = new byte[blockSize*2];

        // set mode and padding
        cipher = new ElectronicCodeBook(impl);
        padding = new PKCS5Padding(blockSize);
    }

    /**
     * Sets the mode of this cipher.
     *
     * @param mode the cipher mode
     *
     * @exception NoSuchAlgorithmException if the requested cipher mode does
     * not exist
     */
    void setMode(String mode) throws NoSuchAlgorithmException {
        if (mode == null)
            throw new NoSuchAlgorithmException("null mode");

        String modeUpperCase = mode.toUpperCase(Locale.ENGLISH);

        if (modeUpperCase.equals("ECB")) {
            return;
        }

        SymmetricCipher rawImpl = cipher.getEmbeddedCipher();
        if (modeUpperCase.equals("CBC")) {
            cipherMode = CBC_MODE;
            cipher = new CipherBlockChaining(rawImpl);
        }
        else if (modeUpperCase.equals("CTS")) {
            cipherMode = CTS_MODE;
            cipher = new CipherTextStealing(rawImpl);
            minBytes = blockSize+1;
            padding = null;
        }
        else if (modeUpperCase.equals("CTR")) {
            cipherMode = CTR_MODE;
            cipher = new CounterMode(rawImpl);
            unitBytes = 1;
            padding = null;
        }
        else if (modeUpperCase.startsWith("CFB")) {
            cipherMode = CFB_MODE;
            unitBytes = getNumOfUnit(mode, "CFB".length(), blockSize);
            cipher = new CipherFeedback(rawImpl, unitBytes);
        }
        else if (modeUpperCase.startsWith("OFB")) {
            cipherMode = OFB_MODE;
            unitBytes = getNumOfUnit(mode, "OFB".length(), blockSize);
            cipher = new OutputFeedback(rawImpl, unitBytes);
        }
        else if (modeUpperCase.equals("PCBC")) {
            cipherMode = PCBC_MODE;
            cipher = new PCBC(rawImpl);
        }
        else {
            throw new NoSuchAlgorithmException("Cipher mode: " + mode
                                               + " not found");
        }
    }

    private static int getNumOfUnit(String mode, int offset, int blockSize)
        throws NoSuchAlgorithmException {
        int result = blockSize; // use blockSize as default value
        if (mode.length() > offset) {
            int numInt;
            try {
                Integer num = Integer.valueOf(mode.substring(offset));
                numInt = num.intValue();
                result = numInt >> 3;
            } catch (NumberFormatException e) {
                throw new NoSuchAlgorithmException
                    ("Algorithm mode: " + mode + " not implemented");
            }
            if ((numInt % 8 != 0) || (result > blockSize)) {
                throw new NoSuchAlgorithmException
                    ("Invalid algorithm mode: " + mode);
            }
        }
        return result;
    }

    /**
     * Sets the padding mechanism of this cipher.
     *
     * @param padding the padding mechanism
     *
     * @exception NoSuchPaddingException if the requested padding mechanism
     * does not exist
     */
    void setPadding(String paddingScheme)
        throws NoSuchPaddingException
    {
        if (paddingScheme == null) {
            throw new NoSuchPaddingException("null padding");
        }
        if (paddingScheme.equalsIgnoreCase("NoPadding")) {
            padding = null;
        } else if (paddingScheme.equalsIgnoreCase("ISO10126Padding")) {
            padding = new ISO10126Padding(blockSize);
        } else if (!paddingScheme.equalsIgnoreCase("PKCS5Padding")) {
            throw new NoSuchPaddingException("Padding: " + paddingScheme
                                             + " not implemented");
        }
        if ((padding != null) &&
            ((cipherMode == CTR_MODE) || (cipherMode == CTS_MODE))) {
            padding = null;
            throw new NoSuchPaddingException
                ((cipherMode == CTR_MODE? "CTR":"CTS") +
                 " mode must be used with NoPadding");
        }
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
    int getOutputSize(int inputLen) {
        int totalLen = buffered + inputLen;

        if (padding == null)
            return totalLen;

        if (decrypting)
            return totalLen;

        if (unitBytes != blockSize) {
            if (totalLen < diffBlocksize)
                return diffBlocksize;
            else
                return (totalLen + blockSize -
                        ((totalLen - diffBlocksize) % blockSize));
        } else {
            return totalLen + padding.padLength(totalLen);
        }
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
    byte[] getIV() {
        byte[] iv = cipher.getIV();
        return (iv == null) ? null : (byte[])iv.clone();
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
    AlgorithmParameters getParameters(String algName) {
        AlgorithmParameters params = null;
        if (cipherMode == ECB_MODE) return null;
        byte[] iv = getIV();
        if (iv != null) {
            AlgorithmParameterSpec ivSpec;
            if (algName.equals("RC2")) {
                RC2Crypt rawImpl = (RC2Crypt) cipher.getEmbeddedCipher();
                ivSpec = new RC2ParameterSpec(rawImpl.getEffectiveKeyBits(),
                                              iv);
            } else {
                ivSpec = new IvParameterSpec(iv);
            }
            try {
                params = AlgorithmParameters.getInstance(algName, "SunJCE");
            } catch (NoSuchAlgorithmException nsae) {
                // should never happen
                throw new RuntimeException("Cannot find " + algName +
                    " AlgorithmParameters implementation in SunJCE provider");
            } catch (NoSuchProviderException nspe) {
                // should never happen
                throw new RuntimeException("Cannot find SunJCE provider");
            }
            try {
                params.init(ivSpec);
            } catch (InvalidParameterSpecException ipse) {
                // should never happen
                throw new RuntimeException("IvParameterSpec not supported");
            }
        }
        return params;
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
    void init(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        try {
            init(opmode, key, (AlgorithmParameterSpec)null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException(e.getMessage());
        }
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
    void init(int opmode, Key key, AlgorithmParameterSpec params,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        decrypting = (opmode == Cipher.DECRYPT_MODE)
                  || (opmode == Cipher.UNWRAP_MODE);

        byte[] keyBytes = getKeyBytes(key);

        byte[] ivBytes;
        if (params == null) {
            ivBytes = null;
        } else if (params instanceof IvParameterSpec) {
            ivBytes = ((IvParameterSpec)params).getIV();
            if ((ivBytes == null) || (ivBytes.length != blockSize)) {
                throw new InvalidAlgorithmParameterException
                    ("Wrong IV length: must be " + blockSize +
                    " bytes long");
            }
        } else if (params instanceof RC2ParameterSpec) {
            ivBytes = ((RC2ParameterSpec)params).getIV();
            if ((ivBytes != null) && (ivBytes.length != blockSize)) {
                throw new InvalidAlgorithmParameterException
                    ("Wrong IV length: must be " + blockSize +
                    " bytes long");
            }
        } else {
            throw new InvalidAlgorithmParameterException("Wrong parameter "
                                                         + "type: IV "
                                                         + "expected");
        }

        if (cipherMode == ECB_MODE) {
            if (ivBytes != null) {
                throw new InvalidAlgorithmParameterException
                                                ("ECB mode cannot use IV");
            }
        } else if (ivBytes == null) {
            if (decrypting) {
                throw new InvalidAlgorithmParameterException("Parameters "
                                                             + "missing");
            }
            if (random == null) {
                random = SunJCE.RANDOM;
            }
            ivBytes = new byte[blockSize];
            random.nextBytes(ivBytes);
        }

        buffered = 0;
        diffBlocksize = blockSize;

        String algorithm = key.getAlgorithm();

        cipher.init(decrypting, algorithm, keyBytes, ivBytes);
    }

    void init(int opmode, Key key, AlgorithmParameters params,
              SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        IvParameterSpec ivSpec = null;
        if (params != null) {
            try {
                ivSpec = (IvParameterSpec)params.getParameterSpec
                    (IvParameterSpec.class);
            } catch (InvalidParameterSpecException ipse) {
                throw new InvalidAlgorithmParameterException("Wrong parameter "
                                                             + "type: IV "
                                                             + "expected");
            }
        }
        init(opmode, key, ivSpec, random);
    }

    /**
     * Return the key bytes of the specified key. Throw an InvalidKeyException
     * if the key is not usable.
     */
    static byte[] getKeyBytes(Key key) throws InvalidKeyException {
        if (key == null) {
            throw new InvalidKeyException("No key given");
        }
        // note: key.getFormat() may return null
        if (!"RAW".equalsIgnoreCase(key.getFormat())) {
            throw new InvalidKeyException("Wrong format: RAW bytes needed");
        }
        byte[] keyBytes = key.getEncoded();
        if (keyBytes == null) {
            throw new InvalidKeyException("RAW key bytes missing");
        }
        return keyBytes;
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
    byte[] update(byte[] input, int inputOffset, int inputLen) {
        byte[] output = null;
        byte[] out = null;
        try {
            output = new byte[getOutputSize(inputLen)];
            int len = update(input, inputOffset, inputLen, output,
                             0);
            if (len == output.length) {
                out = output;
            } else {
                out = new byte[len];
                System.arraycopy(output, 0, out, 0, len);
            }
        } catch (ShortBufferException e) {
            // never thrown
        }
        return out;
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
    int update(byte[] input, int inputOffset, int inputLen, byte[] output,
               int outputOffset) throws ShortBufferException {
        // figure out how much can be sent to crypto function
        int len = buffered + inputLen - minBytes;
        if (padding != null && decrypting) {
            // do not include the padding bytes when decrypting
            len -= blockSize;
        }
        // do not count the trailing bytes which do not make up a unit
        len = (len > 0 ? (len - (len%unitBytes)) : 0);

        // check output buffer capacity
        if ((output == null) || ((output.length - outputOffset) < len)) {
            throw new ShortBufferException("Output buffer must be "
                                           + "(at least) " + len
                                           + " bytes long");
        }
        if (len != 0) {
            // there is some work to do
            byte[] in = new byte[len];

            int inputConsumed = len - buffered;
            int bufferedConsumed = buffered;
            if (inputConsumed < 0) {
                inputConsumed = 0;
                bufferedConsumed = len;
            }

            if (buffered != 0) {
                System.arraycopy(buffer, 0, in, 0, bufferedConsumed);
            }
            if (inputConsumed > 0) {
                System.arraycopy(input, inputOffset, in,
                                 bufferedConsumed, inputConsumed);
            }

            if (decrypting) {
                cipher.decrypt(in, 0, len, output, outputOffset);
            } else {
                cipher.encrypt(in, 0, len, output, outputOffset);
            }

            // Let's keep track of how many bytes are needed to make
            // the total input length a multiple of blocksize when
            // padding is applied
            if (unitBytes != blockSize) {
                if (len < diffBlocksize)
                    diffBlocksize -= len;
                else
                    diffBlocksize = blockSize -
                        ((len - diffBlocksize) % blockSize);
            }

            inputLen -= inputConsumed;
            inputOffset += inputConsumed;
            outputOffset += len;
            buffered -= bufferedConsumed;
            if (buffered > 0) {
                System.arraycopy(buffer, bufferedConsumed, buffer, 0,
                                 buffered);
            }
        }
        // left over again
        if (inputLen > 0) {
            System.arraycopy(input, inputOffset, buffer, buffered,
                             inputLen);
        }
        buffered += inputLen;
        return len;
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
    byte[] doFinal(byte[] input, int inputOffset, int inputLen)
        throws IllegalBlockSizeException, BadPaddingException {
        byte[] output = null;
        byte[] out = null;
        try {
            output = new byte[getOutputSize(inputLen)];
            int len = doFinal(input, inputOffset, inputLen, output, 0);
            if (len < output.length) {
                out = new byte[len];
                if (len != 0)
                    System.arraycopy(output, 0, out, 0, len);
            } else {
                out = output;
            }
        } catch (ShortBufferException e) {
            // never thrown
        }
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
    int doFinal(byte[] input, int inputOffset, int inputLen, byte[] output,
                int outputOffset)
        throws IllegalBlockSizeException, ShortBufferException,
               BadPaddingException {

        // calculate the total input length
        int totalLen = buffered + inputLen;
        int paddedLen = totalLen;
        int paddingLen = 0;

        // will the total input length be a multiple of blockSize?
        if (unitBytes != blockSize) {
            if (totalLen < diffBlocksize) {
                paddingLen = diffBlocksize - totalLen;
            } else {
                paddingLen = blockSize -
                    ((totalLen - diffBlocksize) % blockSize);
            }
        } else if (padding != null) {
            paddingLen = padding.padLength(totalLen);
        }

        if ((paddingLen > 0) && (paddingLen != blockSize) &&
            (padding != null) && decrypting) {
            throw new IllegalBlockSizeException
                ("Input length must be multiple of " + blockSize +
                 " when decrypting with padded cipher");
        }

        // if encrypting and padding not null, add padding
        if (!decrypting && padding != null)
            paddedLen += paddingLen;

        // check output buffer capacity.
        // if we are decrypting with padding applied, we can perform this
        // check only after we have determined how many padding bytes there
        // are.
        if (output == null) {
            throw new ShortBufferException("Output buffer is null");
        }
        int outputCapacity = output.length - outputOffset;
        if (((!decrypting) || (padding == null)) &&
            (outputCapacity < paddedLen) ||
            (decrypting && (outputCapacity < (paddedLen - blockSize)))) {
            throw new ShortBufferException("Output buffer too short: "
                                           + outputCapacity + " bytes given, "
                                           + paddedLen + " bytes needed");
        }

        // prepare the final input avoiding copying if possible
        byte[] finalBuf = input;
        int finalOffset = inputOffset;
        if ((buffered != 0) || (!decrypting && padding != null)) {
            finalOffset = 0;
            finalBuf = new byte[paddedLen];
            if (buffered != 0) {
                System.arraycopy(buffer, 0, finalBuf, 0, buffered);
            }
            if (inputLen != 0) {
                System.arraycopy(input, inputOffset, finalBuf,
                                 buffered, inputLen);
            }
            if (!decrypting && padding != null) {
                padding.padWithLen(finalBuf, totalLen, paddingLen);
            }
        }

        if (decrypting) {
            // if the size of specified output buffer is less than
            // the length of the cipher text, then the current
            // content of cipher has to be preserved in order for
            // users to retry the call with a larger buffer in the
            // case of ShortBufferException.
            if (outputCapacity < paddedLen) {
                cipher.save();
            }
            // create temporary output buffer so that only "real"
            // data bytes are passed to user's output buffer.
            byte[] outWithPadding = new byte[totalLen];
            totalLen = finalNoPadding(finalBuf, finalOffset, outWithPadding,
                                      0, totalLen);

            if (padding != null) {
                int padStart = padding.unpad(outWithPadding, 0, totalLen);
                if (padStart < 0) {
                    throw new BadPaddingException("Given final block not "
                                                  + "properly padded");
                }
                totalLen = padStart;
            }
            if ((output.length - outputOffset) < totalLen) {
                // restore so users can retry with a larger buffer
                cipher.restore();
                throw new ShortBufferException("Output buffer too short: "
                                               + (output.length-outputOffset)
                                               + " bytes given, " + totalLen
                                               + " bytes needed");
            }
            for (int i = 0; i < totalLen; i++) {
                output[outputOffset + i] = outWithPadding[i];
            }
        } else { // encrypting
            totalLen = finalNoPadding(finalBuf, finalOffset, output,
                                      outputOffset, paddedLen);
        }

        buffered = 0;
        diffBlocksize = blockSize;
        if (cipherMode != ECB_MODE) {
            ((FeedbackCipher)cipher).reset();
        }
        return totalLen;
    }

    private int finalNoPadding(byte[] in, int inOff, byte[] out, int outOff,
                               int len)
        throws IllegalBlockSizeException
    {
        if (in == null || len == 0)
            return 0;

        if ((cipherMode != CFB_MODE) && (cipherMode != OFB_MODE)
            && ((len % unitBytes) != 0) && (cipherMode != CTS_MODE)) {
            if (padding != null) {
                throw new IllegalBlockSizeException
                    ("Input length (with padding) not multiple of " +
                     unitBytes + " bytes");
            } else {
                throw new IllegalBlockSizeException
                    ("Input length not multiple of " + unitBytes
                     + " bytes");
            }
        }

        if (decrypting) {
            cipher.decryptFinal(in, inOff, len, out, outOff);
        } else {
            cipher.encryptFinal(in, inOff, len, out, outOff);
        }

        return len;
    }

    // Note: Wrap() and Unwrap() are the same in
    // each of SunJCE CipherSpi implementation classes.
    // They are duplicated due to export control requirements:
    // All CipherSpi implementation must be final.
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
    byte[] wrap(Key key)
        throws IllegalBlockSizeException, InvalidKeyException {
        byte[] result = null;

        try {
            byte[] encodedKey = key.getEncoded();
            if ((encodedKey == null) || (encodedKey.length == 0)) {
                throw new InvalidKeyException("Cannot get an encoding of " +
                                              "the key to be wrapped");
            }
            result = doFinal(encodedKey, 0, encodedKey.length);
        } catch (BadPaddingException e) {
            // Should never happen
        }
        return result;
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
    Key unwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
               int wrappedKeyType)
        throws InvalidKeyException, NoSuchAlgorithmException {
        byte[] encodedKey;
        try {
            encodedKey = doFinal(wrappedKey, 0, wrappedKey.length);
        } catch (BadPaddingException ePadding) {
            throw new InvalidKeyException("The wrapped key is not padded " +
                                          "correctly");
        } catch (IllegalBlockSizeException eBlockSize) {
            throw new InvalidKeyException("The wrapped key does not have " +
                                          "the correct length");
        }
        return ConstructKeys.constructKey(encodedKey, wrappedKeyAlgorithm,
                                          wrappedKeyType);
    }
}
