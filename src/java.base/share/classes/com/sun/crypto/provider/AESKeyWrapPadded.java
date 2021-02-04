/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.sun.crypto.provider.KWUtil.*;

/**
 * This class implement the AES KeyWrap With Padding mode of operation as
 * defined in
 * <a href=https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-38F.pdf>
 * "Recommendation for Block Cipher Modes of Operation: Methods for Key Wrapping"</a>
 * and represents AES cipher in KWP mode.
 */
class AESKeyWrapPadded extends FeedbackCipher {

    // default integrity check value (icv) if iv is not supplied
    private static final byte[] ICV2 = {
        (byte) 0xA6, (byte) 0x59, (byte) 0x59, (byte) 0xA6,
    };

    private static final byte[] PAD_BLK = new byte[SEMI_BLKSIZE - 1];

    // generate internal 8-byte iv based on icv and input length
    private static byte[] generateIV(byte[] icv, int inLen) {
        if (icv.length != ICV2.length) throw new RuntimeException("Invalid iv");
        byte[] newIv = Arrays.copyOf(icv, SEMI_BLKSIZE);
        newIv[4] = (byte) ((inLen >>> 24) & 0xFF);
        newIv[5] = (byte) ((inLen >>> 16) & 0xFF);
        newIv[6] = (byte) ((inLen >>> 8) & 0xFF);
        newIv[7] = (byte) (inLen & 0xFF);
        return newIv;
    }

    // validate the recovered internal iv against icv and return the
    // expected input length
    private static int validateIV(byte[] ivAndLen, byte[] icv)
            throws IllegalBlockSizeException {
        // check against icv and fail if not match
        if (!Arrays.equals(ivAndLen, 0, ICV2.length, icv, 0, ICV2.length)) {
            throw new IllegalBlockSizeException("Integrity check failed");
        }
        int outLen = ivAndLen[4];

        for (int k = 5; k < SEMI_BLKSIZE; k++) {
            if (outLen != 0) {
                outLen <<= SEMI_BLKSIZE;
            }
            outLen |= ivAndLen[k] & 0xFF;
        }
        return outLen;
    }

    AESKeyWrapPadded() {
        super(new AESCrypt());
    }

    /**
     * Gets the name of this feedback mode.
     *
     * @return the string <code>KW</code>
     */
    @Override
    String getFeedback() {
        return "KWP";
    }

    /**
     * Save the current content of this cipher.
     */
    @Override
    void save() {
        throw new UnsupportedOperationException("save not supported");
    };

    /**
     * Restores the content of this cipher to the previous saved one.
     */
    @Override
    void restore() {
        throw new UnsupportedOperationException("restore not supported");
    };

    /**
     * Initializes the cipher in the specified mode with the given key
     * and iv.
     *
     * @param decrypting flag indicating encryption or decryption
     * @param algorithm the algorithm name
     * @param key the key
     * @param iv the iv
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     * @exception InvalidAlgorithmParameterException if the given iv is
     * non-null and not the right length
     */
    @Override
    void init(boolean decrypting, String algorithm, byte[] key, byte[] iv)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (key == null) {
            throw new InvalidKeyException("Invalid null key");
        }
        if (iv != null && iv.length != ICV2.length) {
            throw new InvalidAlgorithmParameterException("Invalid IV length");
        }
        embeddedCipher.init(decrypting, algorithm, key);
        // iv is retrieved from IvParameterSpec.getIV() which is already cloned
        this.iv = (iv == null? ICV2 : iv);
    }

    /**
     * Resets the iv to its original value.
     * This is used when doFinal is called in the Cipher class, so that the
     * cipher can be reused (with its original iv).
     */
    @Override
    void reset() {
        throw new UnsupportedOperationException("reset not supported");
    };

    // no support for multi-part encryption
    @Override
    int encrypt(byte[] pt, int ptOfs, int ptLen, byte[] ct, int ctOfs) {
        throw new UnsupportedOperationException("multi-part not supported");
    };

    // no support for multi-part decryption
    @Override
    int decrypt(byte[] ct, int ctOfs, int ctLen, byte[] pt, int ptOfs) {
        throw new UnsupportedOperationException("multi-part not supported");
    };

    /**
     * Performs single-part encryption operation.
     *
     * <p>The input <code>pt</code>, starting at <code>ptOfs</code>
     * and ending at <code>(ptOfs+ptLen-1)</code>, is encrypted.
     * The result is stored in <code>ct</code>, starting at <code>ctOfs</code>.
     *
     * <p>The subclass that implements Cipher should ensure that
     * <code>init</code> has been called before this method is called.
     *
     * @param pt the input buffer with the data to be encrypted
     * @param ptOfs the offset in <code>pt</code>
     * @param ptLen the length of the input data
     * @param ct the buffer for the encryption result
     * @param ctOfs the offset in <code>ct</code>
     * @return the number of bytes placed into <code>ct</code>
     */
    @Override
    int encryptFinal(byte[] pt, int dummy1, int ptLen, byte[] dummy3,
            int dummy4) throws IllegalBlockSizeException {
        // assert ptOfs == 0; ptLen == pt.length; ct == pt; ctOfs == 0
        int actualLen = ptLen - SEMI_BLKSIZE;
        if (actualLen < 1) {
            throw new IllegalBlockSizeException
                ("data should have at least 1 byte");
        }
        // overwrite the ZERO4 place holder with actual input length
        pt[4] = (byte) ((actualLen >>> 24) & 0xFF);
        pt[5] = (byte) ((actualLen >>> 16) & 0xFF);
        pt[6] = (byte) ((actualLen >>> 8) & 0xFF);
        pt[7] = (byte) (actualLen & 0xFF);

        if (ptLen % SEMI_BLKSIZE != 0) {
            int rem = SEMI_BLKSIZE - (ptLen % SEMI_BLKSIZE);
            System.arraycopy(PAD_BLK, 0, pt, ptLen, rem);
            ptLen += rem;
        }
        if (ptLen <= BLKSIZE) {
            embeddedCipher.encryptBlock(pt, 0, pt, 0);
        } else {
            W(pt, ptLen, embeddedCipher);
        }
        return ptLen;
    }

    /**
     * Performs single-part decryption operation.
     *
     * <p>The input <code>ct</code>, starting at <code>ctOfs</code>
     * and ending at <code>(ctOfs+ctLen-1)</code>, is decrypted.
     * The result is stored in <code>pt</code>, starting at <code>ptOfs</code>.
     *
     * <p>The subclass that implements Cipher should ensure that
     * <code>init</code> has been called before this method is called.
     *
     * @param ct the input buffer with the data to be decrypted
     * @param ctOfs the offset in <code>ct</code>
     * @param ctLen the length of the input data
     * @param pt the buffer for the decryption result
     * @param ptOfs the offset in <code>pt</code>
     * @return the number of bytes placed into <code>pt</code>
     */
    @Override
    int decryptFinal(byte[] ct, int dummy1, int ctLen, byte[] dummy3,
            int dummy4) throws IllegalBlockSizeException {
        // assert ctOfs == 0; ctLen == ct.length; pt == ct; ptOfs == 0
        if (ctLen < BLKSIZE || ctLen % SEMI_BLKSIZE != 0) {
            throw new IllegalBlockSizeException
                ("data should be at least 16 bytes and multiples of 8");
        }

        // We cannot directly use 'out' as we don't know whether there
        // are padding bytes
        byte[] ivAndLen = new byte[SEMI_BLKSIZE];
        if (ctLen == BLKSIZE) {
            embeddedCipher.decryptBlock(ct, 0, ct, 0);
            System.arraycopy(ct, 0, ivAndLen, 0, SEMI_BLKSIZE);
            System.arraycopy(ct, SEMI_BLKSIZE, ct, 0, SEMI_BLKSIZE);
        } else {
            W_INV(ct, ctLen, ivAndLen, embeddedCipher);
        }
        ctLen -= SEMI_BLKSIZE;

        int outLen = validateIV(ivAndLen, this.iv);
        // check padding bytes
        int padLen = ctLen - outLen;
        for (int k = padLen; k > 0; k--) {
            if (ct[ctLen - k] != 0) {
                throw new IllegalBlockSizeException("KWP Pad check failed");
            }
        }
        return outLen;
    }
}
