/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.io.*;
import java.security.*;
import javax.crypto.*;
import static com.sun.crypto.provider.AESConstants.AES_BLOCK_SIZE;
import sun.security.util.ArrayUtil;


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

    // In NIST SP 800-38D, GCM input size is limited to be no longer
    // than (2^36 - 32) bytes. Otherwise, the counter will wrap
    // around and lead to a leak of plaintext.
    // However, given the current GCM spec requirement that recovered
    // text can only be returned after successful tag verification,
    // we are bound by limiting the data size to the size limit of
    // java byte array, e.g. Integer.MAX_VALUE, since all data
    // can only be returned by the doFinal(...) call.
    private static final int MAX_BUF_SIZE = Integer.MAX_VALUE;

    // data size when buffer is divided up to aid in intrinsics
    private static final int TRIGGERLEN = 65536;  // 64k

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

    private static byte[] getLengthBlock(int ivLenInBytes) {
        long ivLen = ((long)ivLenInBytes) << 3;
        byte[] out = new byte[AES_BLOCK_SIZE];
        out[8] = (byte)(ivLen >>> 56);
        out[9] = (byte)(ivLen >>> 48);
        out[10] = (byte)(ivLen >>> 40);
        out[11] = (byte)(ivLen >>> 32);
        out[12] = (byte)(ivLen >>> 24);
        out[13] = (byte)(ivLen >>> 16);
        out[14] = (byte)(ivLen >>> 8);
        out[15] = (byte)ivLen;
        return out;
    }

    private static byte[] getLengthBlock(int aLenInBytes, int cLenInBytes) {
        long aLen = ((long)aLenInBytes) << 3;
        long cLen = ((long)cLenInBytes) << 3;
        byte[] out = new byte[AES_BLOCK_SIZE];
        out[0] = (byte)(aLen >>> 56);
        out[1] = (byte)(aLen >>> 48);
        out[2] = (byte)(aLen >>> 40);
        out[3] = (byte)(aLen >>> 32);
        out[4] = (byte)(aLen >>> 24);
        out[5] = (byte)(aLen >>> 16);
        out[6] = (byte)(aLen >>> 8);
        out[7] = (byte)aLen;
        out[8] = (byte)(cLen >>> 56);
        out[9] = (byte)(cLen >>> 48);
        out[10] = (byte)(cLen >>> 40);
        out[11] = (byte)(cLen >>> 32);
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
            byte[] lengthBlock = getLengthBlock(iv.length);
            g.update(lengthBlock);
            j0 = g.digest();
        }
        return j0;
    }

    private static void checkDataLength(int processed, int len) {
        if (processed > MAX_BUF_SIZE - len) {
            throw new ProviderException("SunJCE provider only supports " +
                "input size up to " + MAX_BUF_SIZE + " bytes");
        }
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
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     */
    @Override
    void init(boolean decrypting, String algorithm, byte[] key, byte[] iv)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        init(decrypting, algorithm, key, iv, DEFAULT_TAG_LEN);
    }

    /**
     * Initializes the cipher in the specified mode with the given key
     * and iv.
     *
     * @param decrypting flag indicating encryption or decryption
     * @param algorithm the algorithm name
     * @param keyValue the key
     * @param ivValue the iv
     * @param tagLenBytes the length of tag in bytes
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     */
    void init(boolean decrypting, String algorithm, byte[] keyValue,
              byte[] ivValue, int tagLenBytes)
              throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (keyValue == null) {
            throw new InvalidKeyException("Internal error");
        }
        if (ivValue == null) {
            throw new InvalidAlgorithmParameterException("Internal error");
        }
        if (ivValue.length == 0) {
            throw new InvalidAlgorithmParameterException("IV is empty");
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
        if (aadBuffer != null) {
            if (aadBuffer.size() > 0) {
                byte[] aad = aadBuffer.toByteArray();
                sizeOfAAD = aad.length;

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
            aadBuffer = null;
        }
    }

    // Utility to process the last block; used by encryptFinal and decryptFinal
    void doLastBlock(byte[] in, int inOfs, int len, byte[] out, int outOfs,
                     boolean isEncrypt) throws IllegalBlockSizeException {
        byte[] ct;
        int ctOfs;
        int ilen = len;  // internal length

        if (isEncrypt) {
            ct = out;
            ctOfs = outOfs;
        } else {
            ct = in;
            ctOfs = inOfs;
        }

        // Divide up larger data sizes to trigger CTR & GHASH intrinsic quicker
        if (len > TRIGGERLEN) {
            int i = 0;
            int tlen;  // incremental lengths
            final int plen = AES_BLOCK_SIZE * 6;
            // arbitrary formula to aid intrinsic without reaching buffer end
            final int count = len / 1024;

            while (count > i) {
                tlen = gctrPAndC.update(in, inOfs, plen, out, outOfs);
                ghashAllToS.update(ct, ctOfs, tlen);
                inOfs += tlen;
                outOfs += tlen;
                ctOfs += tlen;
                i++;
            }
            ilen -= count * plen;
            processed += count * plen;
        }

        gctrPAndC.doFinal(in, inOfs, ilen, out, outOfs);
        processed += ilen;

        int lastLen = ilen % AES_BLOCK_SIZE;
        if (lastLen != 0) {
            ghashAllToS.update(ct, ctOfs, ilen - lastLen);
            ghashAllToS.update(
                    expandToOneBlock(ct, (ctOfs + ilen - lastLen), lastLen));
        } else {
            ghashAllToS.update(ct, ctOfs, ilen);
        }
    }

    // Process en/decryption all the way to the last block.  It takes both
    // For input it takes the ibuffer which is wrapped in 'buffer' and 'src'
    // from doFinal.
    void doLastBlock(ByteBuffer buffer, ByteBuffer src, ByteBuffer dst)
        throws IllegalBlockSizeException {

        processed = 0;
        if (buffer != null && buffer.remaining() > 0) {
            // en/decrypt on how much buffer there is in AES_BLOCK_SIZE
            processed = gctrPAndC.update(buffer, dst);

            // Process the remainder in the buffer
            if (buffer.remaining() > 0) {
                // Copy the remainder of the buffer into the extra block
                byte[] block = new byte[AES_BLOCK_SIZE];
                int over = buffer.remaining();
                int len = over;  // how much is processed by in the extra block
                buffer.get(block, 0, over);

                // if src is empty, update the final block and wait for later
                // to finalize operation
                if (src.remaining() > 0) {
                    // Fill out block with what is in data
                    if (src.remaining() > AES_BLOCK_SIZE - over) {
                        src.get(block, over, AES_BLOCK_SIZE - over);
                        len += AES_BLOCK_SIZE - over;
                    } else {
                        // If the remaining in buffer + data does not fill a
                        // block, complete the ghash operation
                        int l = src.remaining();
                        src.get(block, over, l);
                        len += l;
                    }
                }
                gctrPAndC.update(block, 0, AES_BLOCK_SIZE, dst);
                processed += len;
            }
        }

        // en/decrypt whatever remains in src.
        // If src has been consumed, this will be a no-op
        processed += gctrPAndC.doFinal(src, dst);
    }

    /**
     * Performs encryption operation.
     *
     * <p>The input plain text <code>in</code>, starting at <code>inOfs</code>
     * and ending at <code>(inOfs + len - 1)</code>, is encrypted. The result
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
        ArrayUtil.blockSizeCheck(len, blockSize);

        checkDataLength(processed, len);

        processAAD();

        if (len > 0) {
            ArrayUtil.nullAndBoundsCheck(in, inOfs, len);
            ArrayUtil.nullAndBoundsCheck(out, outOfs, len);

            gctrPAndC.update(in, inOfs, len, out, outOfs);
            processed += len;
            ghashAllToS.update(out, outOfs, len);
        }

        return len;
    }

    int decrypt(ByteBuffer src, ByteBuffer dst) {
        if (src.remaining() > 0) {
            byte[] b = new byte[src.remaining()];
            src.get(b);
            try {
                ibuffer.write(b);
            } catch (IOException e) {
                throw new ProviderException("Unable to add remaining input to the buffer", e);
            }
        }
        return 0;
    }


    int encrypt(ByteBuffer src, ByteBuffer dst) {
        int len = src.remaining();
        if (len == 0) {
            return 0;
        }

        ArrayUtil.blockSizeCheck(src.remaining(), blockSize);
        checkDataLength(processed, src.remaining());
        processAAD();

        if (len >= AES_BLOCK_SIZE) {
            ByteBuffer data = src.duplicate();
            len = gctrPAndC.update(data, dst);
        } else {
            if (src.remaining() > 0) {
                byte[] b = new byte[src.remaining()];
                src.get(b);
                try {
                    ibuffer.write(b);
                } catch (IOException e) {
                    throw new ProviderException(
                        "Unable to add remaining input to the buffer", e);
                }
            }
            return 0;
        }

        processed += len;
        ghashAllToS.update(src, len);
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
        if (len > MAX_BUF_SIZE - tagLenBytes) {
            throw new ShortBufferException
                ("Can't fit both data and tag into one buffer");
        }
        try {
            ArrayUtil.nullAndBoundsCheck(out, outOfs,
                (len + tagLenBytes));
        } catch (ArrayIndexOutOfBoundsException aiobe) {
            throw new ShortBufferException("Output buffer too small");
        }

        checkDataLength(processed, len);

        processAAD();
        if (len > 0) {
            ArrayUtil.nullAndBoundsCheck(in, inOfs, len);

            doLastBlock(in, inOfs, len, out, outOfs, true);
        }

        byte[] block = getLengthBlock(sizeOfAAD, processed);
        ghashAllToS.update(block);
        byte[] s = ghashAllToS.digest();
        if (tagLenBytes > block.length) {
            block = new byte[tagLenBytes];
        }
        GCTR gctrForSToTag = new GCTR(embeddedCipher, this.preCounterBlock);
        gctrForSToTag.doFinal(s, 0, s.length, block, 0);

        System.arraycopy(block, 0, out, (outOfs + len), tagLenBytes);
        return (len + tagLenBytes);
    }

    int encryptFinal(ByteBuffer src, ByteBuffer dst)
        throws IllegalBlockSizeException, ShortBufferException {
        int len = src.remaining();
        dst.mark();
        if (len > MAX_BUF_SIZE - tagLenBytes) {
            throw new ShortBufferException
                ("Can't fit both data and tag into one buffer");
        }

        if (dst.remaining() < len + tagLenBytes) {
            throw new ShortBufferException("Output buffer too small");
        }

        checkDataLength(processed, len);

        processAAD();
        if (len > 0) {
            doLastBlock((ibuffer == null || ibuffer.size() == 0) ?
                    null : ByteBuffer.wrap(ibuffer.toByteArray()), src, dst);
            dst.reset();
            ghashAllToS.doLastBlock(dst, processed);
        }

        byte[] block = getLengthBlock(sizeOfAAD, processed);
        ghashAllToS.update(block);

        byte[] s = ghashAllToS.digest();
        if (tagLenBytes > block.length) {
            block = new byte[tagLenBytes];
        }
        GCTR gctrForSToTag = new GCTR(embeddedCipher, this.preCounterBlock);
        gctrForSToTag.doFinal(s, 0, s.length, block, 0);
        dst.put(block, 0, tagLenBytes);

        return (processed + tagLenBytes);
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
        ArrayUtil.blockSizeCheck(len, blockSize);

        checkDataLength(ibuffer.size(), len);

        processAAD();

        if (len > 0) {
            // store internally until decryptFinal is called because
            // spec mentioned that only return recovered data after tag
            // is successfully verified
            ArrayUtil.nullAndBoundsCheck(in, inOfs, len);
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

        // do this check here can also catch the potential integer overflow
        // scenario for the subsequent output buffer capacity check.
        checkDataLength(ibuffer.size(), (len - tagLenBytes));

        try {
            ArrayUtil.nullAndBoundsCheck(out, outOfs,
                (ibuffer.size() + len) - tagLenBytes);
        } catch (ArrayIndexOutOfBoundsException aiobe) {
            throw new ShortBufferException("Output buffer too small");
        }

        processAAD();

        ArrayUtil.nullAndBoundsCheck(in, inOfs, len);

        // get the trailing tag bytes from 'in'
        byte[] tag = new byte[tagLenBytes];
        System.arraycopy(in, inOfs + len - tagLenBytes, tag, 0, tagLenBytes);
        len -= tagLenBytes;

        // If decryption is in-place or there is buffered "ibuffer" data, copy
        // the "in" byte array into the ibuffer before proceeding.
        if (in == out || ibuffer.size() > 0) {
            if (len > 0) {
                ibuffer.write(in, inOfs, len);
            }

            // refresh 'in' to all buffered-up bytes
            in = ibuffer.toByteArray();
            inOfs = 0;
            len = in.length;
            ibuffer.reset();
        }

        if (len > 0) {
            doLastBlock(in, inOfs, len, out, outOfs, false);
        }

        byte[] block = getLengthBlock(sizeOfAAD, processed);
        ghashAllToS.update(block);

        byte[] s = ghashAllToS.digest();
        //byte[] sOut = new byte[s.length];
        if (tagLenBytes != block.length) {
            block = new byte[tagLenBytes];
        }
        GCTR gctrForSToTag = new GCTR(embeddedCipher, this.preCounterBlock);
        gctrForSToTag.doFinal(s, 0, tagLenBytes, block, 0);

        // check entire authentication tag for time-consistency
        int mismatch = 0;
        for (int i = 0; i < tagLenBytes; i++) {
            mismatch |= tag[i] ^ block[i];
        }

        if (mismatch != 0) {
            throw new AEADBadTagException("Tag mismatch!");
        }

        return len;
    }

    // Note: In-place operations do not need an intermediary copy because
    // the GHASH check was performed before the decryption.
    int decryptFinal(ByteBuffer src, ByteBuffer dst)
        throws IllegalBlockSizeException, AEADBadTagException,
        ShortBufferException {
        // Length of the input
        ByteBuffer tag;
        ByteBuffer data = src.duplicate();
        data.mark();

        ByteBuffer buffer = ((ibuffer == null || ibuffer.size() == 0) ? null :
            ByteBuffer.wrap(ibuffer.toByteArray()));
        int len;

        if (data.remaining() >= tagLenBytes) {
            tag = src.duplicate();
            tag.position(data.limit() - tagLenBytes);
            data.limit(data.limit() - tagLenBytes);
            len = data.remaining();
            if (buffer != null) {
                len += buffer.remaining();
            }
        } else if (buffer != null && buffer.remaining() >= tagLenBytes) {
            // It's unlikely the tag will be between the buffer and data
            tag = ByteBuffer.allocate(tagLenBytes);
            int limit = buffer.remaining() - tagLenBytes - data.remaining();
            buffer.position(limit);
            tag.put(buffer);
            // reset buffer to data only
            buffer.position(0);  // ibuffer should always start at zero.
            buffer.limit(limit);
            data.mark();  // Maybe data position is not zero
            tag.put(data);
            tag.flip();
            data.reset();
            // Limit is how much of the ibuffer has been chopped off.
            len = buffer.remaining();
        } else {
            throw new AEADBadTagException("Input too short - need tag");
        }

        // do this check here can also catch the potential integer overflow
        // scenario for the subsequent output buffer capacity check.
        checkDataLength(0, len);

        if ((ibuffer.size() + data.remaining()) - tagLenBytes >
            dst.remaining()) {
            throw new ShortBufferException("Output buffer too small");
        }

        processAAD();

        // If there is data stored in the buffer
        if (buffer != null && buffer.remaining() > 0) {
            ghashAllToS.update(buffer, buffer.remaining());
            // Process the overage
            if (buffer.remaining() > 0) {
                // Fill out block between two buffers
                if (data.remaining() > 0) {
                    int over = buffer.remaining();
                    byte[] block = new byte[AES_BLOCK_SIZE];
                    // Copy the remainder of the buffer into the extra block
                    buffer.get(block, 0, over);

                    // Fill out block with what is in data
                    if (data.remaining() > AES_BLOCK_SIZE - over) {
                        data.get(block, over, AES_BLOCK_SIZE - over);
                        ghashAllToS.update(block, 0, AES_BLOCK_SIZE);
                    } else {
                        // If the remaining in buffer + data does not fill a
                        // block, complete the ghash operation
                        int l = data.remaining();
                        data.get(block, over, l);
                        ghashAllToS.doLastBlock(ByteBuffer.wrap(block), over + l);
                    }
                    processed += AES_BLOCK_SIZE;
                } else {
                    // data is empty, so complete the ghash op with the
                    // remaining buffer
                    ghashAllToS.doLastBlock(buffer, buffer.remaining());
                }
            }
        }

        if (data.remaining() > 0) {
            ghashAllToS.doLastBlock(data, data.remaining());
        }
        byte[] block = getLengthBlock(sizeOfAAD, len);
        ghashAllToS.update(block);
        byte[] s = ghashAllToS.digest();
        if (tagLenBytes > block.length) {
            block = new byte[tagLenBytes];
        }
       // byte[] sOut = new byte[s.length];
        GCTR gctrForSToTag = new GCTR(embeddedCipher, this.preCounterBlock);
        gctrForSToTag.doFinal(s, 0, s.length, block, 0);

        // check entire authentication tag for time-consistency
        int mismatch = 0;
        for (int i = 0; i < tagLenBytes; i++) {
            mismatch |= tag.get() ^ block[i];
        }

        if (mismatch != 0) {
            throw new AEADBadTagException("Tag mismatch!");
        }

        // Reset the buffers for the data decryption phase
        data.reset();
        if (buffer != null) {
            buffer.flip();
        }

        // Decrypt the all the input data and put it into dst
        doLastBlock(buffer, data, dst);
        dst.limit(dst.position());

        // 'processed' from the gctr decryption operation, not ghash
        return processed;
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
