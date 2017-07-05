/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.*;
import java.security.*;
import javax.crypto.*;
import static com.sun.crypto.provider.AESConstants.AES_BLOCK_SIZE;

/**
 * This class represents ciphers in GaloisCounter (GCM) mode.
 *
 * <p>This mode currently should only be used w/ AES cipher.
 * Although no checking is done, caller should only pass AES
 * Cipher to the constructor.
 *
 * <p>NOTE: Unlike other modes, when used for decryption, this class
 * will buffer all processed outputs internally and won't return them
 * until the tag has been successfully verified.
 *
 * @since 1.8
 */
final class GaloisCounterMode extends FeedbackCipher {

    static int DEFAULT_TAG_LEN = AES_BLOCK_SIZE;
    static int DEFAULT_IV_LEN = 12; // in bytes

    // buffer for AAD data; if null, meaning update has been called
    private ByteArrayOutputStream aadBuffer = new ByteArrayOutputStream();
    private int sizeOfAAD = 0;

    // buffer for storing input in decryption, not used for encryption
    private ByteArrayOutputStream ibuffer = null;

    // in bytes; need to convert to bits (default value 128) when needed
    private int tagLenBytes = DEFAULT_TAG_LEN;

    // these following 2 fields can only be initialized after init() is
    // called, e.g. after cipher key k is set, and STAY UNCHANGED
    private byte[] subkeyH = null;
    private byte[] preCounterBlock = null;

    private GCTR gctrPAndC = null;
    private GHASH ghashAllToS = null;

    // length of total data, i.e. len(C)
    private int processed = 0;

    // additional variables for save/restore calls
    private byte[] aadBufferSave = null;
    private int sizeOfAADSave = 0;
    private byte[] ibufferSave = null;
    private int processedSave = 0;

    // value must be 16-byte long; used by GCTR and GHASH as well
    static void increment32(byte[] value) {
        if (value.length != AES_BLOCK_SIZE) {
            // should never happen
            throw new ProviderException("Illegal counter block length");
        }
        // start from last byte and only go over 4 bytes, i.e. total 32 bits
        int n = value.length - 1;
        while ((n >= value.length - 4) && (++value[n] == 0)) {
            n--;
        }
    }

    // ivLen in bits
    private static byte[] getLengthBlock(int ivLen) {
        byte[] out = new byte[AES_BLOCK_SIZE];
        out[12] = (byte)(ivLen >>> 24);
        out[13] = (byte)(ivLen >>> 16);
        out[14] = (byte)(ivLen >>> 8);
        out[15] = (byte)ivLen;
        return out;
    }

    // aLen and cLen both in bits
    private static byte[] getLengthBlock(int aLen, int cLen) {
        byte[] out = new byte[AES_BLOCK_SIZE];
        out[4] = (byte)(aLen >>> 24);
        out[5] = (byte)(aLen >>> 16);
        out[6] = (byte)(aLen >>> 8);
        out[7] = (byte)aLen;
        out[12] = (byte)(cLen >>> 24);
        out[13] = (byte)(cLen >>> 16);
        out[14] = (byte)(cLen >>> 8);
        out[15] = (byte)cLen;
        return out;
    }

    private static byte[] expandToOneBlock(byte[] in, int inOfs, int len) {
        if (len > AES_BLOCK_SIZE) {
            throw new ProviderException("input " + len + " too long");
        }
        if (len == AES_BLOCK_SIZE && inOfs == 0) {
            return in;
        } else {
            byte[] paddedIn = new byte[AES_BLOCK_SIZE];
            System.arraycopy(in, inOfs, paddedIn, 0, len);
            return paddedIn;
        }
    }

    private static byte[] getJ0(byte[] iv, byte[] subkeyH) {
        byte[] j0;
        if (iv.length == 12) { // 96 bits
            j0 = expandToOneBlock(iv, 0, iv.length);
            j0[AES_BLOCK_SIZE - 1] = 1;
        } else {
            GHASH g = new GHASH(subkeyH);
            int lastLen = iv.length % AES_BLOCK_SIZE;
            if (lastLen != 0) {
                g.update(iv, 0, iv.length - lastLen);
                byte[] padded =
                    expandToOneBlock(iv, iv.length - lastLen, lastLen);
                g.update(padded);
            } else {
                g.update(iv);
            }
            byte[] lengthBlock = getLengthBlock(iv.length*8);
            g.update(lengthBlock);
            j0 = g.digest();
        }
        return j0;
    }

    GaloisCounterMode(SymmetricCipher embeddedCipher) {
        super(embeddedCipher);
        aadBuffer = new ByteArrayOutputStream();
    }

    /**
     * Gets the name of the feedback mechanism
     *
     * @return the name of the feedback mechanism
     */
    String getFeedback() {
        return "GCM";
    }

    /**
     * Resets the cipher object to its original state.
     * This is used when doFinal is called in the Cipher class, so that the
     * cipher can be reused (with its original key and iv).
     */
    void reset() {
        if (aadBuffer == null) {
            aadBuffer = new ByteArrayOutputStream();
        } else {
            aadBuffer.reset();
        }
        if (gctrPAndC != null) gctrPAndC.reset();
        if (ghashAllToS != null) ghashAllToS.reset();
        processed = 0;
        sizeOfAAD = 0;
        if (ibuffer != null) {
            ibuffer.reset();
        }
    }

    /**
     * Save the current content of this cipher.
     */
    void save() {
        processedSave = processed;
        sizeOfAADSave = sizeOfAAD;
        aadBufferSave =
            ((aadBuffer == null || aadBuffer.size() == 0)?
             null : aadBuffer.toByteArray());
        if (gctrPAndC != null) gctrPAndC.save();
        if (ghashAllToS != null) ghashAllToS.save();
        if (ibuffer != null) {
            ibufferSave = ibuffer.toByteArray();
        }
    }

    /**
     * Restores the content of this cipher to the previous saved one.
     */
    void restore() {
        processed = processedSave;
        sizeOfAAD = sizeOfAADSave;
        if (aadBuffer != null) {
            aadBuffer.reset();
            if (aadBufferSave != null) {
                aadBuffer.write(aadBufferSave, 0, aadBufferSave.length);
            }
        }
        if (gctrPAndC != null) gctrPAndC.restore();
        if (ghashAllToS != null) ghashAllToS.restore();
        if (ibuffer != null) {
            ibuffer.reset();
            ibuffer.write(ibufferSave, 0, ibufferSave.length);
        }
    }

    /**
     * Initializes the cipher in the specified mode with the given key
     * and iv.
     *
     * @param decrypting flag indicating encryption or decryption
     * @param algorithm the algorithm name
     * @param key the key
     * @param iv the iv
     * @param tagLenBytes the length of tag in bytes
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     */
    void init(boolean decrypting, String algorithm, byte[] key, byte[] iv)
            throws InvalidKeyException {
        init(decrypting, algorithm, key, iv, DEFAULT_TAG_LEN);
    }

    /**
     * Initializes the cipher in the specified mode with the given key
     * and iv.
     *
     * @param decrypting flag indicating encryption or decryption
     * @param algorithm the algorithm name
     * @param key the key
     * @param iv the iv
     * @param tagLenBytes the length of tag in bytes
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     */
    void init(boolean decrypting, String algorithm, byte[] keyValue,
              byte[] ivValue, int tagLenBytes)
              throws InvalidKeyException {
        if (keyValue == null || ivValue == null) {
            throw new InvalidKeyException("Internal error");
        }

        // always encrypt mode for embedded cipher
        this.embeddedCipher.init(false, algorithm, keyValue);
        this.subkeyH = new byte[AES_BLOCK_SIZE];
        this.embeddedCipher.encryptBlock(new byte[AES_BLOCK_SIZE], 0,
                this.subkeyH, 0);

        this.iv = ivValue.clone();
        preCounterBlock = getJ0(iv, subkeyH);
        byte[] j0Plus1 = preCounterBlock.clone();
        increment32(j0Plus1);
        gctrPAndC = new GCTR(embeddedCipher, j0Plus1);
        ghashAllToS = new GHASH(subkeyH);

        this.tagLenBytes = tagLenBytes;
        if (aadBuffer == null) {
            aadBuffer = new ByteArrayOutputStream();
        } else {
            aadBuffer.reset();
        }
        processed = 0;
        sizeOfAAD = 0;
        if (decrypting) {
            ibuffer = new ByteArrayOutputStream();
        }
    }

    /**
     * Continues a multi-part update of the Additional Authentication
     * Data (AAD), using a subset of the provided buffer. If this
     * cipher is operating in either GCM or CCM mode, all AAD must be
     * supplied before beginning operations on the ciphertext (via the
     * {@code update} and {@code doFinal} methods).
     * <p>
     * NOTE: Given most modes do not accept AAD, default impl for this
     * method throws IllegalStateException.
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
    void updateAAD(byte[] src, int offset, int len) {
        if (aadBuffer != null) {
            aadBuffer.write(src, offset, len);
        } else {
            // update has already been called
            throw new IllegalStateException
                ("Update has been called; no more AAD data");
        }
    }

    // Feed the AAD data to GHASH, pad if necessary
    void processAAD() {
        if (aadBuffer != null && aadBuffer.size() > 0) {
            byte[] aad = aadBuffer.toByteArray();
            sizeOfAAD = aad.length;
            aadBuffer = null;

            int lastLen = aad.length % AES_BLOCK_SIZE;
            if (lastLen != 0) {
                ghashAllToS.update(aad, 0, aad.length - lastLen);
                byte[] padded = expandToOneBlock(aad, aad.length - lastLen,
                                                 lastLen);
                ghashAllToS.update(padded);
            } else {
                ghashAllToS.update(aad);
            }
        }
    }

    // Utility to process the last block; used by encryptFinal and decryptFinal
    void doLastBlock(byte[] in, int inOfs, int len, byte[] out, int outOfs,
                     boolean isEncrypt) throws IllegalBlockSizeException {
        // process data in 'in'
        gctrPAndC.doFinal(in, inOfs, len, out, outOfs);
        processed += len;

        byte[] ct;
        int ctOfs;
        if (isEncrypt) {
            ct = out;
            ctOfs = outOfs;
        } else {
            ct = in;
            ctOfs = inOfs;
        }
        int lastLen = len  % AES_BLOCK_SIZE;
        if (lastLen != 0) {
            ghashAllToS.update(ct, ctOfs, len - lastLen);
            byte[] padded =
                expandToOneBlock(ct, (ctOfs + len - lastLen), lastLen);
            ghashAllToS.update(padded);
        } else {
            ghashAllToS.update(ct, ctOfs, len);
        }
    }


    /**
     * Performs encryption operation.
     *
     * <p>The input plain text <code>in</code>, starting at <code>inOff</code>
     * and ending at <code>(inOff + len - 1)</code>, is encrypted. The result
     * is stored in <code>out</code>, starting at <code>outOfs</code>.
     *
     * @param in the buffer with the input data to be encrypted
     * @param inOfs the offset in <code>in</code>
     * @param len the length of the input data
     * @param out the buffer for the result
     * @param outOfs the offset in <code>out</code>
     * @exception ProviderException if <code>len</code> is not
     * a multiple of the block size
     * @return the number of bytes placed into the <code>out</code> buffer
     */
    int encrypt(byte[] in, int inOfs, int len, byte[] out, int outOfs) {
        if ((len % blockSize) != 0) {
             throw new ProviderException("Internal error in input buffering");
        }
        processAAD();
        if (len > 0) {
            gctrPAndC.update(in, inOfs, len, out, outOfs);
            processed += len;
            ghashAllToS.update(out, outOfs, len);
        }
        return len;
    }

    /**
     * Performs encryption operation for the last time.
     *
     * @param in the input buffer with the data to be encrypted
     * @param inOfs the offset in <code>in</code>
     * @param len the length of the input data
     * @param out the buffer for the encryption result
     * @param outOfs the offset in <code>out</code>
     * @return the number of bytes placed into the <code>out</code> buffer
     */
    int encryptFinal(byte[] in, int inOfs, int len, byte[] out, int outOfs)
        throws IllegalBlockSizeException, ShortBufferException {
        if (out.length - outOfs < (len + tagLenBytes)) {
            throw new ShortBufferException("Output buffer too small");
        }

        processAAD();
        if (len > 0) {
            doLastBlock(in, inOfs, len, out, outOfs, true);
        }

        byte[] lengthBlock =
            getLengthBlock(sizeOfAAD*8, processed*8);
        ghashAllToS.update(lengthBlock);
        byte[] s = ghashAllToS.digest();
        byte[] sOut = new byte[s.length];
        GCTR gctrForSToTag = new GCTR(embeddedCipher, this.preCounterBlock);
        gctrForSToTag.doFinal(s, 0, s.length, sOut, 0);

        System.arraycopy(sOut, 0, out, (outOfs + len), tagLenBytes);
        return (len + tagLenBytes);
    }

    /**
     * Performs decryption operation.
     *
     * <p>The input cipher text <code>in</code>, starting at
     * <code>inOfs</code> and ending at <code>(inOfs + len - 1)</code>,
     * is decrypted. The result is stored in <code>out</code>, starting at
     * <code>outOfs</code>.
     *
     * @param in the buffer with the input data to be decrypted
     * @param inOfs the offset in <code>in</code>
     * @param len the length of the input data
     * @param out the buffer for the result
     * @param outOfs the offset in <code>out</code>
     * @exception ProviderException if <code>len</code> is not
     * a multiple of the block size
     * @return the number of bytes placed into the <code>out</code> buffer
     */
    int decrypt(byte[] in, int inOfs, int len, byte[] out, int outOfs) {
        if ((len % blockSize) != 0) {
             throw new ProviderException("Internal error in input buffering");
        }
        processAAD();

        if (len > 0) {
            // store internally until decryptFinal is called because
            // spec mentioned that only return recovered data after tag
            // is successfully verified
            ibuffer.write(in, inOfs, len);
        }
        return 0;
    }

    /**
     * Performs decryption operation for the last time.
     *
     * <p>NOTE: For cipher feedback modes which does not perform
     * special handling for the last few blocks, this is essentially
     * the same as <code>encrypt(...)</code>. Given most modes do
     * not do special handling, the default impl for this method is
     * to simply call <code>decrypt(...)</code>.
     *
     * @param in the input buffer with the data to be decrypted
     * @param inOfs the offset in <code>cipher</code>
     * @param len the length of the input data
     * @param out the buffer for the decryption result
     * @param outOfs the offset in <code>plain</code>
     * @return the number of bytes placed into the <code>out</code> buffer
     */
    int decryptFinal(byte[] in, int inOfs, int len,
                     byte[] out, int outOfs)
        throws IllegalBlockSizeException, AEADBadTagException,
        ShortBufferException {
        if (len < tagLenBytes) {
            throw new AEADBadTagException("Input too short - need tag");
        }
        if (out.length - outOfs < ((ibuffer.size() + len) - tagLenBytes)) {
            throw new ShortBufferException("Output buffer too small");
        }
        processAAD();
        if (len != 0) {
            ibuffer.write(in, inOfs, len);
        }

        // refresh 'in' to all buffered-up bytes
        in = ibuffer.toByteArray();
        inOfs = 0;
        len = in.length;
        ibuffer.reset();

        byte[] tag = new byte[tagLenBytes];
        // get the trailing tag bytes from 'in'
        System.arraycopy(in, len - tagLenBytes, tag, 0, tagLenBytes);
        len -= tagLenBytes;

        if (len > 0) {
            doLastBlock(in, inOfs, len, out, outOfs, false);
        }

        byte[] lengthBlock =
            getLengthBlock(sizeOfAAD*8, processed*8);
        ghashAllToS.update(lengthBlock);

        byte[] s = ghashAllToS.digest();
        byte[] sOut = new byte[s.length];
        GCTR gctrForSToTag = new GCTR(embeddedCipher, this.preCounterBlock);
        gctrForSToTag.doFinal(s, 0, s.length, sOut, 0);
        for (int i = 0; i < tagLenBytes; i++) {
            if (tag[i] != sOut[i]) {
                throw new AEADBadTagException("Tag mismatch!");
            }
        }
        return len;
    }

    // return tag length in bytes
    int getTagLen() {
        return this.tagLenBytes;
    }

    int getBufferedLength() {
        if (ibuffer == null) {
            return 0;
        } else {
            return ibuffer.size();
        }
    }
}
