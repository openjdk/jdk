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

import sun.nio.ch.DirectBuffer;
import sun.security.util.ArrayUtil;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;

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
abstract class GaloisCounterMode extends CipherSpi {

    SymmetricCipher blockCipher;
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

    // buffer data for crypto operation
    private ByteArrayOutputStream ibuffer = null;

    // Original dst buffer if there was an overlap situation
    private ByteBuffer originalDst = null;

    // Default value is 128bits, this in stores bytes.
    private int tagLenBytes = 16;

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

    /**
     * Calculate if the given data lengths and the already processed data
     * exceeds the maximum allowed processed data by GCM.
     * @param lengths lengths of unprocessed data.
     */
    private void checkDataLength(int ... lengths) {
        int max = MAX_BUF_SIZE;
        for (int len : lengths) {
            max = Math.subtractExact(max, len);
        }
        if (processed > max) {
            throw new ProviderException("SunJCE provider only supports " +
                "input size up to " + MAX_BUF_SIZE + " bytes");
        }
    }

    int keySize;

    GaloisCounterMode(int keySize, SymmetricCipher embeddedCipher) {
        blockCipher = embeddedCipher;
        this.keySize = keySize;
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
    void init(boolean decrypting, String algorithm, byte[] key, byte[] iv)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        //init(decrypting, algorithm, key, iv, DEFAULT_TAG_LEN);
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
    int blockSize;
    private GCMEngine engine;

    void init(int optmode, Key key, GCMParameterSpec spec)
        throws InvalidKeyException, InvalidAlgorithmParameterException {

        // Check the Key object is valid and the right size
        if (key == null) {
            throw new InvalidKeyException("The key must not be null");
        }
        byte[] keyValue = key.getEncoded();
        if (keyValue == null) {
            throw new InvalidKeyException("Key encoding must not be null");
        } else if (keySize != -1 && keyValue.length != keySize) {
            throw new InvalidKeyException("The key must be " +
                keySize + " bytes");
        }
        int tagLen = spec.getTLen();
        if (tagLen < 96 || tagLen > 128 || ((tagLen & 0x07) != 0)) {
            throw new InvalidAlgorithmParameterException
                ("Unsupported TLen value.  Must be one of " +
                    "{128, 120, 112, 104, 96}");
        }
        tagLenBytes = tagLen >> 3;

        // always encrypt mode for embedded cipher
        blockCipher.init(false, key.getAlgorithm(), keyValue);
        blockSize = blockCipher.getBlockSize();
        iv = spec.getIV();

        // This can be in-place because subkeyH is zeroed data
        subkeyH = new byte[blockSize];
        blockCipher.encryptBlock(subkeyH, 0, subkeyH,0);

        preCounterBlock = getJ0(iv, subkeyH);
        byte[] j0Plus1 = preCounterBlock.clone();
        increment32(j0Plus1);
        gctrPAndC = new GCTR(blockCipher, j0Plus1);
        ghashAllToS = new GHASH(subkeyH);

        if (aadBuffer == null) {
            aadBuffer = new ByteArrayOutputStream();
        } else {
            aadBuffer.reset();
        }
        processed = 0;
        sizeOfAAD = 0;
        if (optmode == Cipher.DECRYPT_MODE) {
            engine = new GCMDecrypt();
        } else {
            engine = new GCMEncrypt();
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
/*
        int bufLen = getBufferedLength();
        byte[] block;
        if (bufLen > 0) {
            block = new byte[blockSize];
            System.arraycopy(ibuffer.toByteArray(), 0, block,
                0, bufLen);
            System.arraycopy(in, inOfs, block, bufLen, len);
            ilen += bufLen;
            in = block;
        }

 */

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

        if (buffer != null && buffer.remaining() > 0) {
            // en/decrypt on how much buffer there is in AES_BLOCK_SIZE
            processed += gctrPAndC.update(buffer, dst);

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
                //gctrPAndC.update(block, 0, AES_BLOCK_SIZE, dst); // this code writes past the limit
                gctrPAndC.update(block, 0, AES_BLOCK_SIZE, block, 0);
                dst.put(block, 0, Math.min(block.length, dst.remaining()));
                processed += len;
            }
        }

        // en/decrypt whatever remains in src.
        // If src has been consumed, this will be a no-op
        processed += gctrPAndC.doFinal(src, dst);
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

    /**
     * Check for overlap. If the src and dst buffers are using shared data and
     * if dst will overwrite src data before src can be processed.  If so, make
     * a copy to put the dst data in.
     */
    ByteBuffer overlapDetection(ByteBuffer src, ByteBuffer dst) {
        if (src.isDirect() && dst.isDirect()) {
            DirectBuffer dsrc = (DirectBuffer) src;
            DirectBuffer ddst = (DirectBuffer) dst;

            // Get the current memory address for the given ByteBuffers
            long srcaddr = dsrc.address();
            long dstaddr = ddst.address();

            // Find the lowest attachment that is the base memory address of the
            // shared memory for the src object
            while (dsrc.attachment() != null) {
                srcaddr = ((DirectBuffer) dsrc.attachment()).address();
                dsrc = (DirectBuffer) dsrc.attachment();
            }

            // Find the lowest attachment that is the base memory address of the
            // shared memory for the dst object
            while (ddst.attachment() != null) {
                dstaddr = ((DirectBuffer) ddst.attachment()).address();
                ddst = (DirectBuffer) ddst.attachment();
            }

            // If the base addresses are not the same, there is no overlap
            if (srcaddr != dstaddr) {
                return dst;
            }
            // At this point we know these objects share the same memory.
            // This checks the starting position of the src and dst address for
            // overlap.
            // It uses the base address minus the passed object's address to get
            // the offset from the base address, then add the position() from
            // the passed object.  That gives up the true offset from the base
            // address.  As long as the src side is >= the dst side, we are not
            // in overlap.
            if (((DirectBuffer) src).address() - srcaddr + src.position() >=
                ((DirectBuffer) dst).address() - dstaddr + dst.position()) {
                return dst;
            }

        } else if (!src.isDirect() && !dst.isDirect()) {
            if (!src.isReadOnly()) {
                // If using the heap, check underlying byte[] address.
                if (!src.array().equals(dst.array()) ) {
                    return dst;
                }

                // Position plus arrayOffset() will give us the true offset from
                // the underlying byte[] address.
                if (src.position() + src.arrayOffset() >=
                    dst.position() + dst.arrayOffset()) {
                    return dst;
                }
            }
        } else {
            // buffer types aren't the same
            return dst;
        }

        // Create a copy
        ByteBuffer tmp = dst.duplicate();
        // We can use a heap buffer for internal use, save on alloc cost
        ByteBuffer bb = ByteBuffer.allocate(dst.remaining());
        tmp.limit(dst.limit());
        tmp.position(dst.position());
        bb.put(tmp);
        bb.flip();
        originalDst = dst;
        return bb;
    }

    /**
     * If originalDst exists, dst is an internal dst buffer, then copy the data
     * into the original dst buffer
     */
    void restoreDst(ByteBuffer dst) {
        if (originalDst == null) {
            return;
        }

        dst.flip();
        originalDst.put(dst);
        originalDst = null;
    }

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (mode.equalsIgnoreCase("GCM") == false) {
            throw new NoSuchAlgorithmException("Mode must be GCM");
        }
    }

    @Override
    protected void engineSetPadding(String padding)
        throws NoSuchPaddingException {
        if (padding.equalsIgnoreCase("NoPadding") == false) {
            throw new NoSuchPaddingException("Padding must be NoPadding");
        }
    }

    @Override
    protected int engineGetBlockSize() {
        return blockCipher.getBlockSize();
    }

    @Override
    protected int engineGetOutputSize(int inputLen) {
        return engine.getOutputSize(inputLen, true);
    }

    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        return super.engineGetKeySize(key);
    }

    byte[] iv = null;
    @Override
    protected byte[] engineGetIV() {
        return iv.clone();
    }

    /**
     * Create a random 16-byte iv.
     *
     * @param random a {@code SecureRandom} object.  If {@code null} is
     * provided a new {@code SecureRandom} object will be instantiated.
     *
     * @return a 16-byte array containing the random nonce.
     */
    private static byte[] createIv(SecureRandom random) {
        byte[] iv = new byte[DEFAULT_IV_LEN];
        SecureRandom rand = (random != null) ? random : new SecureRandom();
        rand.nextBytes(iv);
        return iv;
    }

    private GCMParameterSpec getParameterSpec() {
        return new GCMParameterSpec(getTagLen() * 8,
            createIv(random));
    }

    //AlgorithmParameters params = null;
    SecureRandom random = null;
    @Override
    protected AlgorithmParameters engineGetParameters() {
        GCMParameterSpec spec = null;
        if (iv != null) {
            spec = getParameterSpec();
        } else {
            spec = new GCMParameterSpec(tagLenBytes, iv.clone());
        }
        try {
            AlgorithmParameters params =
                AlgorithmParameters.getInstance("GCM",
                    SunJCE.getInstance());
            params.init(spec);
            return params;
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
            throw new RuntimeException(e);
        }
    }

    void init(int opmode, Key key, SecureRandom random)
        throws InvalidKeyException {
        this.random = random;
        try {
            init(opmode, key, getParameterSpec());
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        init(opmode, key, random);
    }

    @Override
    protected void engineInit(int opmode, Key key,
        AlgorithmParameterSpec params, SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {

        GCMParameterSpec spec;
        if (params == null) {
            spec = new GCMParameterSpec(getTagLen() * 8, createIv(random));
        } else {
            if (!(params instanceof GCMParameterSpec)) {
                throw new InvalidAlgorithmParameterException(
                    "AlgorithmParameterSpec not of GCMParameterSpec");
            }
            spec = (GCMParameterSpec)params;
            if (spec.getIV() == null) {
                throw new InvalidAlgorithmParameterException("IV is null");
            }
            if (spec.getIV().length == 0) {
                throw new InvalidAlgorithmParameterException("IV is empty");
            }
        }
        init(opmode, key, spec);
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameters params,
        SecureRandom random) throws InvalidKeyException,
        InvalidAlgorithmParameterException {
        GCMParameterSpec spec = null;
        if (params != null) {
            try {
                spec = params.getParameterSpec(GCMParameterSpec.class);
            } catch (InvalidParameterSpecException e) {
                throw new InvalidAlgorithmParameterException(e);
            }
        }
        init(opmode, key, spec);
    }

    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        return engine.doUpdate(input, inputOffset, inputLen);
    }

    @Override
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen,
        byte[] output, int outputOffset) throws ShortBufferException {
        int len = engine.getOutputSize(inputLen, false);
        if (len > output.length - outputOffset) {
            throw new ShortBufferException("Output buffer must be (at least) " +
                len + " bytes long");
        }
        return engine.doUpdate(input, inputOffset, inputLen, output,
            outputOffset);
    }

    @Override
    protected int engineUpdate(ByteBuffer src, ByteBuffer dst)
        throws ShortBufferException {
        int len = engine.getOutputSize(src.remaining(), false);
        if (len > dst.remaining()) {
            throw new ShortBufferException(
                "Output buffer must be (at least) " + len + " bytes long");
        }
        return engine.doUpdate(src, dst);
    }

    @Override
    protected void engineUpdateAAD(byte[] src, int offset, int len) {
        if (aadBuffer != null) {
            aadBuffer.write(src, offset, len);
        } else {
            // update has already been called
            throw new IllegalStateException
                ("Update has been called; no more AAD data");
        }
    }

    @Override
    protected void engineUpdateAAD(ByteBuffer src) {
        if (src.hasArray()) {
            engineUpdateAAD(src.array(), src.arrayOffset(), src.remaining());
        } else {
            byte[] aad = new byte[src.remaining()];
            src.get(aad);
            engineUpdateAAD(aad, 0, aad.length);
        }
    }

    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset,
        int inputLen) throws IllegalBlockSizeException, BadPaddingException {
        byte[] output = new byte[engine.getOutputSize(inputLen, true)];
        if (input == null) {
            input = emptyBuf;
        }
        try {
            engine.doFinal(input, inputOffset, inputLen, output, 0);
        } catch (ShortBufferException e) {
            throw new RuntimeException(e);
        }
        return output;
    }

    @Override
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen,
        byte[] output, int outputOffset) throws ShortBufferException,
        IllegalBlockSizeException, BadPaddingException {
        ArrayUtil.nullAndBoundsCheck(input, inputOffset, inputLen);
        return engine.doFinal(input, inputOffset, inputLen, output, outputOffset);
    }

    @Override
    protected int engineDoFinal(ByteBuffer input, ByteBuffer output)
        throws ShortBufferException, IllegalBlockSizeException,
        BadPaddingException {
        return engine.doFinal(input, output);
    }

    @Override
    protected byte[] engineWrap(Key key) throws IllegalBlockSizeException,
        InvalidKeyException {
        return super.engineWrap(key);
    }

    @Override
    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
        int wrappedKeyType) throws InvalidKeyException, NoSuchAlgorithmException {
        return super.engineUnwrap(wrappedKey, wrappedKeyAlgorithm,
            wrappedKeyType);
    }

    final static byte[] emptyBuf = new byte[0];

    abstract class GCMEngine {


        /**
         *
         * @param inLen Contains the length of the input data and buffered data.
         * @param isFinal true if this is a doFinal operation
         * @return If it's an update operation, inLen must blockSize
         *         divisible.  If it's a final operation, output will
         *         include the tag.
         */
        abstract int getOutputSize(int inLen, boolean isFinal);
        abstract byte[] doUpdate(byte[] in, int inOff, int inLen);
        abstract int doUpdate(byte[] in, int inOff, int inLen, byte[] out, int outOff)
            throws ShortBufferException;
        abstract int doUpdate(ByteBuffer src, ByteBuffer dst)
            throws ShortBufferException;
        abstract int doFinal(byte[] in, int inOff, int inLen, byte[] out, int outOff)
            throws IllegalBlockSizeException, AEADBadTagException, ShortBufferException;
        abstract int doFinal(ByteBuffer src, ByteBuffer dst)
            throws IllegalBlockSizeException, AEADBadTagException, ShortBufferException;
        abstract int cryptBlocks(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs);
        abstract int cryptBlocks(byte[] in, int inOfs, int inLen, ByteBuffer dst);
        abstract int cryptBlocks(ByteBuffer src, ByteBuffer dst);

        int processBlocks(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {

            processAAD();
            // 'inLen' stores the length to use with buffer 'in'.
            // 'len' stores the length returned by the method.
            int len = inLen;

            // if there is enough data in the ibuffer and 'in', encrypt it.
            if (ibuffer != null && ibuffer.size() > 0) {
                byte[] buffer = ibuffer.toByteArray();
                // number of bytes not filling a block
                int remainder = ibuffer.size() % blockSize;
                // number of bytes along block boundary
                int blen = ibuffer.size() - remainder;

                // If there is enough bytes in ibuffer for a block or more,
                // encrypt that first.
                if (blen > 0) {
                    cryptBlocks(buffer, 0, blen, out, outOfs);
                    outOfs += blen;
                }

                // blen is now the offset for 'buffer'

                // Construct and encrypt a block if there is enough 'buffer' and
                // 'in' to make one
                if ((inLen + remainder) >= blockSize) {
                    byte[] block = new byte[blockSize];

                    System.arraycopy(buffer, blen, block, 0, remainder);
                    int inLenUsed = blockSize - remainder;
                    System.arraycopy(in, inOfs, block, remainder, inLenUsed);

                    cryptBlocks(block, 0, blockSize, out, outOfs);
                    inOfs += inLenUsed;
                    inLen -= inLenUsed;
                    len += (blockSize - inLenUsed);
                    outOfs += blockSize;
                    ibuffer.reset();
                    // Code below will write the remainder from 'in' to ibuffer
                } else if (remainder > 0) {
                    // If a block or more was encrypted from 'buffer' only, but the
                    // rest of 'buffer' with 'in' could not construct a block, then
                    // put the rest of 'buffer' back into ibuffer.
                    ibuffer.reset();
                    ibuffer.write(buffer, blen, remainder);
                    // Code below will write the remainder from 'in' to ibuffer
                }
                // If blen == 0 and there was not enough to construct a block
                // from 'buffer' and 'in', then let the below code append 'in' to
                // the ibuffer.
            }

            // Write any remaining bytes outside the blockSize into ibuffer.
            int remainder = inLen % blockSize;
            if (remainder > 0) {
                if (ibuffer == null) {
                    ibuffer = new ByteArrayOutputStream(inLen % blockSize);
                }
                len -= remainder;
                inLen -= remainder;
                // remainder offset is based on original buffer length
                ibuffer.write(in, inOfs + inLen, remainder);
            }

            // Encrypt the remaining blocks inside of 'in'
            if (inLen > 0) {
                cryptBlocks(in, inOfs, inLen, out, outOfs);
            }

            return len;
        }

        int processBlocks(ByteBuffer buffer, ByteBuffer src, ByteBuffer dst) {
            processAAD();
            // 'inLen' stores the length to use with buffer 'in'.
            // 'len' stores the length returned by the method.
            int len = 0;

            // if there is enough data in the ibuffer and 'in', encrypt it.
            if (buffer != null && buffer.remaining() > 0) {
                // number of bytes not filling a block
                int remainder = buffer.remaining() % blockSize;

                /*
                // If there is enough bytes in ibuffer for a block or more,
                // encrypt that first.
                if (buffer.remaining() > blockSize) {
                    len = cryptBlocks(buffer, dst);
                }
*/
                // Construct and encrypt a block if there is enough 'buffer' and
                // 'in' to make one
                if ((buffer.remaining() + src.remaining()) >= blockSize) {
                    byte[] block = new byte[blockSize];
                    buffer.get(block, 0, remainder);
                    src.get(block, remainder, blockSize - remainder);
                    len += cryptBlocks(block, 0, blockSize, dst);
                    ibuffer.reset();
                    // Code below will write the remainder from 'in' to ibuffer
                } /*else if (remainder > 0) {
                    // If a block or more was encrypted from 'buffer' only, but the
                    // rest of 'buffer' with 'in' could not construct a block, then
                    // put the rest of 'buffer' back into ibuffer.
                    byte[] b = new byte[remainder];
                    buffer.put(b);
                    ibuffer.reset();
                    try {
                        ibuffer.write(b);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    // Code below will write the remainder from 'in' to ibuffer
                }
             */
                // If buflen == 0 and there was not enough to construct a block
                // from 'buffer' and 'in', then let the below code append 'in' to
                // the ibuffer.
            }

            if (src.remaining() >= blockSize) {
                len += cryptBlocks(src, dst);
            }

            // Write any remaining bytes outside the blockSize into ibuffer.
            // Encrypt the remaining blocks inside of 'in'

            if (src.remaining() > 0) {
                if (ibuffer == null) {
                    ibuffer = new ByteArrayOutputStream(src.remaining());
                }
                byte[] b = new byte[src.remaining()];
                src.get(b);
                // remainder offset is based on original buffer length
                try {
                    ibuffer.write(b);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            return len;
        }

        int mergeBlock(byte[] buffer, int bufOfs, byte[] in, int inOfs, int inLen,
            byte[] block) {
            return mergeBlock(buffer, bufOfs, buffer.length - bufOfs, in,
                inOfs, inLen, block);
        }

        // Note: This in only called when buffer length is less that a blockSize
        int mergeBlock(byte[] buffer, int bufOfs, int bufLen, byte[] in, int inOfs, int inLen,
            byte[] block) {
            if (bufLen > blockSize) {
                throw new RuntimeException("mergeBlock called on an ibuffer " +
                    "too big:  " + bufLen + " bytes");
            }

            System.arraycopy(buffer, bufOfs, block, 0,
                bufLen);// + (getTagOfs() < 0 ? getTagOfs() : 0));
            int inUsed = Math.min(block.length - bufLen,
                (inLen > block.length ? block.length : inLen));
            System.arraycopy(in, inOfs, block, bufLen, inUsed);
            return inUsed;
        }
    }

    class GCMEncrypt extends GCMEngine {

        public int getOutputSize(int inLen, boolean isFinal) {
            int len = getBufferedLength();
            if (isFinal) {
                return len + inLen + tagLenBytes;
            } else {
                len += inLen;
                return len - (len % blockCipher.getBlockSize());
            }
        }

        @Override
        byte[] doUpdate(byte[] in, int inOff, int inLen) {
            byte[] output = new byte[engine.getOutputSize(inLen, false)];
            try {
                doUpdate(in, inOff, inLen, output, 0);
            } catch (ShortBufferException e) {
                // update decryption has no output
            }
            return output;
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
         * @param inLen the length of the input data
         * @param out the buffer for the result
         * @param outOfs the offset in <code>out</code>
         * @return the number of bytes placed into the <code>out</code> buffer
         */
        @Override
        public int doUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
            checkDataLength(inLen, getBufferedLength());
            ArrayUtil.nullAndBoundsCheck(in, inOfs, inLen);
            processAAD();

            ArrayUtil.nullAndBoundsCheck(out, outOfs, inLen);
            return processBlocks(in, inOfs, inLen, out, outOfs);
        }

        int cryptBlocks(byte[] in, int inOfs, int len, byte[] out, int outOfs) {
            len = gctrPAndC.update(in, inOfs, len, out, outOfs);
            processed += len;
            ghashAllToS.update(out, outOfs, len);
            return len;
        }

        int cryptBlocks(ByteBuffer src, ByteBuffer dst) {
            dst.mark();
            int len = gctrPAndC.update(src, dst);
            processed += len;
            dst.reset();
            ghashAllToS.update(dst, len);
            return len;
        }

        int cryptBlocks(byte[] in, int inOfs, int inLen, ByteBuffer dst) {
            dst.mark();
            int len = gctrPAndC.update(in, inOfs, inLen, dst);
            processed += len;
            dst.reset();
            ghashAllToS.update(dst, len);
            return len;
        }

        /*
         * This method is for CipherCore to insert the remainder of its buffer
         * into the ibuffer before a doFinal(ByteBuffer, ByteBuffer) operation
         */
        @Override
        public int doUpdate(ByteBuffer src, ByteBuffer dst)
            throws ShortBufferException {

            int len = processBlocks((ibuffer == null || ibuffer.size() == 0) ?
                    null : ByteBuffer.wrap(ibuffer.toByteArray()), src, dst);
/*
            if (len < ibuffer.size()) {
                // store internally until encryptFinal
                //ArrayUtil.nullAndBoundsCheck(in, inOfs, len);
                if (ibuffer == null) {
                    ibuffer = new ByteArrayOutputStream();
                }
                ibuffer.write(src.array(), len, src.array().length);
            }
            */
            return len;
        }

        /**
         * Performs encryption operation for the last time.
         *
         * @param in the input buffer with the data to be encrypted
         * @param inOfs the offset in <code>in</code>
         * @param inLen the length of the input data
         * @param out the buffer for the encryption result
         * @param outOfs the offset in <code>out</code>
         * @return the number of bytes placed into the <code>out</code> buffer
         */
        @Override
        public int doFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws IllegalBlockSizeException, ShortBufferException {
            checkDataLength(inLen, getBufferedLength(), tagLenBytes);

//            ByteBuffer src = ByteBuffer.wrap(in, inOfs, len);
//            ByteBuffer dst = ByteBuffer.wrap(out, outOfs, out.length - outOfs);
//            return doFinal(src, dst);

            try {
                ArrayUtil.nullAndBoundsCheck(out, outOfs,
                    (inLen + tagLenBytes));
            } catch (ArrayIndexOutOfBoundsException aiobe) {
                throw new ShortBufferException("Output buffer too small");
            }
            int resultLen = inLen;
            byte[] block;
            if (ibuffer != null) {
                int bufLen = ibuffer.size();
                int r, bufOfs = 0;
                byte[] buffer = ibuffer.toByteArray();

                // Add ibuffer to resulting length
                resultLen += bufLen;

                // If more than one block is in ibuffer, call doUpdate()
                if (bufLen >= blockSize) {
                    r = doUpdate(buffer, 0, bufLen, out, outOfs);
                    bufLen -= r;
                    inOfs += r;
                    outOfs += r;
                    bufOfs += r;
                }
                // Make a block if the remaining ibuffer and 'in' can make one.
                if (bufLen > 0 && bufLen + inLen >= blockSize) {
                    block = new byte[blockSize];
                    r = mergeBlock(buffer, bufOfs, in, inOfs, inLen, block);
                    inOfs += r;
                    inLen -= r;
                    r = cryptBlocks(block, 0, blockSize, out, outOfs);
                    outOfs += r;
                    bufLen = 0;
                }

                // Need to consume all the ibuffer here to prepare for doFinal()
                if (bufLen > 0) {
                    block = new byte[bufLen + inLen];
                    System.arraycopy(buffer, 0, block, 0, bufLen);
                    System.arraycopy(in, inOfs, block, bufLen, inLen);
                    inLen += bufLen;
                    in = block;
                    inOfs = 0;
                }
            }

            processAAD();
            if (inLen > 0) {
                ArrayUtil.nullAndBoundsCheck(in, inOfs, inLen);

                doLastBlock(in, inOfs, inLen, out, outOfs, true);
            }

            block = getLengthBlock(sizeOfAAD, processed);
            ghashAllToS.update(block);
            block = ghashAllToS.digest();
            new GCTR(blockCipher, preCounterBlock).doFinal(block, 0, tagLenBytes,
                block, 0);

            // copy the tag to the end of the buffer
            System.arraycopy(block, 0, out, (outOfs + inLen), tagLenBytes);
            return (resultLen + tagLenBytes);

        }

        @Override
        public int doFinal(ByteBuffer src, ByteBuffer dst) throws IllegalBlockSizeException,
            ShortBufferException {
            dst = overlapDetection(src, dst);
            int len = src.remaining() + getBufferedLength();

            // 'len' includes ibuffer data
            checkDataLength(len, tagLenBytes);
            dst.mark();
            if (dst.remaining() < len + tagLenBytes) {
                throw new ShortBufferException("Output buffer too small");
            }

            processAAD();
            if (len > 0) {
                doLastBlock((ibuffer == null || ibuffer.size() == 0) ?
                    null : ByteBuffer.wrap(ibuffer.toByteArray()), src, dst);
                dst.reset();
                ghashAllToS.doLastBlock(dst, len);
            }

            byte[] block = getLengthBlock(sizeOfAAD, processed);
            ghashAllToS.update(block);
            block = ghashAllToS.digest();
            new GCTR(blockCipher, preCounterBlock).doFinal(block, 0, tagLenBytes, block, 0);
            dst.put(block, 0, tagLenBytes);
            restoreDst(dst);

            return (len + tagLenBytes);
        }
    }

    interface cryptoOp {
        int cryptoCall(byte[] in, int inOfs, int inLen, byte[] out, int outOfs);
    }

    class GCTROp implements cryptoOp {

        @Override
        public int cryptoCall(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
            return gctrPAndC.update(in, inOfs, inLen, out, outOfs);
        }
    }

    class GHASHOp implements cryptoOp {

        @Override
        public int cryptoCall(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
            ghashAllToS.update(in, inOfs, inLen);
            return 0;
        }
    }

    class GCMDecrypt extends GCMEngine {

        class GCTRDecrypt extends GCMDecrypt {

            @Override
            int cryptBlocks(byte[] in, int inOfs, int inLen, byte[] out,
                int outLen) {
                return super.cryptBlocks(in, inOfs, inLen, out, outLen);
            }
        }

        GCMDecrypt() {
            ibuffer = new ByteArrayOutputStream();
        }

        public int getOutputSize(int inLen, boolean isFinal) {
            if (!isFinal) {
                return 0;
            }
            if (ibuffer != null) {
                inLen += ibuffer.size();
            }
            return Math.max(inLen - tagLenBytes, 0);
        }

        @Override
        byte[] doUpdate(byte[] in, int inOff, int inLen) {
            try {
                doUpdate(in, inOff, inLen, null, 0);
            } catch (ShortBufferException e) {
                // update decryption has no output
            }
            return new byte[0];
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
         * @param inLen the length of the input data
         * @param out the buffer for the result
         * @param outOfs the offset in <code>out</code>
         * @exception ProviderException if <code>len</code> is not
         * a multiple of the block size
         * @return the number of bytes placed into the <code>out</code> buffer
         */
        @Override
        public int doUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {

            if (inLen > 0) {
                // store internally until decryptFinal is called because
                // spec mentioned that only return recovered data after tag
                // is successfully verified
                ArrayUtil.nullAndBoundsCheck(in, inOfs, inLen);
                ibuffer.write(in, inOfs, inLen);
            }
            return 0;
        }


        @Override
        public int doUpdate(ByteBuffer src, ByteBuffer dst)
            throws ShortBufferException {

            if (src.remaining() > 0) {
                // If there is an array, use that to avoid the extra copy to
                // take the src data out of the bytebuffer.
                if (src.hasArray()) {
                    doUpdate(src.array(), src.arrayOffset() + src.position(),
                        src.remaining(), null, 0);
                    src.position(src.limit());
                } else {
                    byte[] b = new byte[src.remaining()];
                    src.get(b);
                    try {
                        ibuffer.write(b);
                    } catch (IOException e) {
                        throw new ProviderException(
                            "Unable to add remaining input to the buffer", e);
                    }
                }
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
         * @param inLen the length of the input data
         * @param out the buffer for the decryption result
         * @param outOfs the offset in <code>plain</code>
         * @return the number of bytes placed into the <code>out</code> buffer
         */

        byte[] tag;
        int tagOfs = 0;

        /*
         * If tagOfs > 0, the tag is inside 'in' along with encrypted data
         * If tagOfs = 0, 'in' contains only the tag
         * if tagOfs = blockSize, there is no data in 'in' and all the tag
         *   is in ibuffer
         * If tagOfs < 0, that tag is split between ibuffer and 'in'
         */
        void findTag(byte[] in, int inOfs, int inLen) {
            if (inLen >= tagLenBytes) {
                tagOfs = inLen - tagLenBytes;
                // XXX maybe eliminate this by using the tagOfs to make inLen smaller
                tag = new byte[tagLenBytes];
                System.arraycopy(in, inOfs + tagOfs, tag, 0, tagLenBytes);
            } else {
                // tagOfs will be negative
                tag = new byte[tagLenBytes];
                byte[] buffer = ibuffer.toByteArray();
                tagOfs = mergeBlock(buffer, buffer.length - (blockSize - inLen), in, inOfs, inLen,
                    tag) - tagLenBytes;
            }
        }

        int processGHASH(byte[] in, int inOfs, int inLen) {
            return process(true, in, inOfs, inLen, null, 0);
        }

        int processGCTR(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
            return process(false, in, inOfs, inLen, out, outOfs);
        }

        int process(boolean ghash, byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
            byte[] buffer = null;
            byte[] block = null;
            int resultLen = 0;
            int bufLen = getBufferedLength();
            int len = 0;

            int cipherLen = bufLen + tagOfs;
            if (tagOfs < 0) {
                inLen = 0;
            } else {
                inLen -= tagLenBytes;
            }

            // If the tag is the only data, complete the processing
            if (cipherLen == 0) {
                if (ghash) {
                    return ghashAllToS.doLastBlock(in, inOfs, 0);
                } else {
                    return gctrPAndC.doFinal(in, inOfs, 0, out, outOfs);
                }
            }

            if (bufLen > 0) {
                buffer = ibuffer.toByteArray();
            }

            int bufRemainder = bufLen + (tagOfs < 0 ? tagOfs : 0);

            // If ibuffer has at least a block size worth of data, decrypt it
            //if ((bufLen + (tagOfs < 0 ? tagOfs : 0)) >= blockSize) {
            if (bufRemainder >= blockSize) {
                if (ghash) {
                    // the length cannot be greater than the bufLen
                    resultLen = ghashAllToS.update(buffer, 0, bufRemainder);
                } else {
                    int l = bufRemainder - (bufRemainder % blockSize);
                    //int l = bufLen - (bufLen % blockSize) + (tagOfs < 0 ? tagOfs : 0);
                    //                    int l = bufLen - (bufLen % blockSize) + (tagOfs < 0 ? tagOfs : 0);
                    resultLen = gctrPAndC.update(buffer, 0, l, out, outOfs);
                    outOfs += resultLen;
                }
                len += resultLen;
                // Preserve resultLen, as it becomes the ibuffer offset, if
                // needed, in the next op
            }

            bufRemainder -= resultLen;
            // merge the remaining ibuffer with the 'in'
            if (bufRemainder > 0) {
                block = new byte[blockSize];
                resultLen = mergeBlock(buffer, resultLen, bufRemainder, in, inOfs,
                    Math.max(cipherLen - resultLen - bufRemainder, 0), block);
                if (len + resultLen + bufRemainder == cipherLen) {
                    in = block;
                    inOfs = 0;
                    inLen = cipherLen - len;
                } else {
                    if (ghash) {
                        ghashAllToS.update(block, 0, blockSize);
                    } else {
                        gctrPAndC.update(block, 0, blockSize, out, outOfs);
                        outOfs += block.length;
                    }
                    // update buffers for amount of 'in' buffer used
                    inOfs += resultLen;
                    inLen -= resultLen;
                    len += blockSize;
                }
            }

            if (ghash) {
                len += ghashAllToS.doLastBlock(in, inOfs, inLen);
            } else {
                len += gctrPAndC.doFinal(in, inOfs, inLen, out, outOfs);
            }
            return len;
        }
        /*
                    // If ibuffer and 'in' can create a full block, do a block merge
                    if (bufRemainder + (inLen - tagLenBytes) >= blockSize) {
                        block = new byte[blockSize];
                        resultLen = mergeBlock(buffer, resultLen, in, inOfs, inLen,
                            block);
                        if (ghash) {
                            ghashAllToS.update(block, 0, blockSize);
                        } else {
                            gctrPAndC.update(block, 0, blockSize, out, outOfs);
                            outOfs += block.length;
                        }
                        // update buffers for amount of 'in' buffer used
                        inOfs += resultLen;
                        len += block.length;
                    }

            if (bufLen > 0)    // If tagOfs > 0, 'in' has bytes to be consumed
                        if (tagOfs > 0) {
                            // check if a merge block is needed for the rest of ibuffer
                            if (bufRemainder > 0) {
                                // If the buffer and 'in' can make a block, do update()
                                if (bufRemainder + (inLen - tagLenBytes) >= blockSize) {
                                    block = new byte[blockSize];
                                    // resultLen as a parameter is the offset in the ibuffer
                                    // resultLen is set to the bytes used from 'in'
                                    resultLen = mergeBlock(buffer, resultLen, in, inOfs,
                                        inLen, block);
                                    if (ghash) {
                                        ghashAllToS.update(block, 0, blockSize);
                                    } else {
                                        gctrPAndC.update(block, 0, blockSize, out,
                                            outOfs);
                                        outOfs += block.length;
                                    }
                                    // update buffers for amount of 'in' buffer used
                                    inOfs += resultLen;
                                    len += block.length;
                                } else {
                                    // If there is not enough to make a block, copy 'in'
                            // into ibuffer and use that as input
                            block = new byte[bufRemainder + inLen - tagLenBytes];
                            System.arraycopy(buffer, resultLen, block, 0, bufRemainder);
                            System.arraycopy(in, inOfs, block, bufRemainder, inLen - tagLenBytes);
                            if (ghash) {
                                len += ghashAllToS.doLastBlock(block, 0, block.length);
                            } else {
                                len += gctrPAndC.doFinal(block, 0, block.length, out,
                                    outOfs);
                            }
                            return len;
                        }
                    } else {
                        resultLen = 0;
                    }
                }
            }
            // decrypt the 'in' data, if any left
            if (inLen - tagLenBytes > resultLen) {
        }
*/
        @Override
        public int doFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException {

            int bufLen = getBufferedLength();
            int len = inLen + bufLen;
            if (len < tagLenBytes) {
                throw new AEADBadTagException("Input too short - need tag");
            }

            // do this check here can also catch the potential integer overflow
            // scenario for the subsequent output buffer capacity check.
            checkDataLength(len - tagLenBytes);

            try {
                ArrayUtil.nullAndBoundsCheck(out, outOfs, len - tagLenBytes);
            } catch (ArrayIndexOutOfBoundsException aiobe) {
                throw new ShortBufferException("Output buffer too small");
            }

            ArrayUtil.nullAndBoundsCheck(in, inOfs, inLen);

            findTag(in, inOfs, inLen);
            processAAD();
            byte[] block = getLengthBlock(sizeOfAAD,
                processGHASH(in, inOfs, inLen));
            ghashAllToS.update(block);
            block = ghashAllToS.digest();
            new GCTR(blockCipher, preCounterBlock).doFinal(block, 0, tagLenBytes, block, 0);

            // check entire authentication tag for time-consistency
            int mismatch = 0;
            for (int i = 0; i < tagLenBytes; i++) {
                mismatch |= tag[i] ^ block[i];
            }

            if (mismatch != 0) {
                throw new AEADBadTagException("Tag mismatch!");
            }

            return processGCTR(in, inOfs, inLen, out, outOfs);
        }

        @Override
        public int doFinal(ByteBuffer src, ByteBuffer dst)
            throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException {

            // Check for overlap in the bytebuffers
            dst = overlapDetection(src, dst);

            // Length of the input
            ByteBuffer tag;
            ByteBuffer ct = src.duplicate();

            ByteBuffer buffer = ((ibuffer == null || ibuffer.size() == 0) ? null :
                ByteBuffer.wrap(ibuffer.toByteArray()));
            int len;

            if (ct.remaining() >= tagLenBytes) {
                tag = src.duplicate();
                tag.position(ct.limit() - tagLenBytes);
                ct.limit(ct.limit() - tagLenBytes);
                len = ct.remaining();
                if (buffer != null) {
                    len += buffer.remaining();
                }
            } else if (buffer != null && ct.remaining() < tagLenBytes) {
                // It's unlikely the tag will be between the buffer and data
                tag = ByteBuffer.allocate(tagLenBytes);
                int limit = buffer.remaining() - (tagLenBytes - ct.remaining());
                buffer.mark();
                buffer.position(limit);
                // Read from "new" limit to buffer's end
                tag.put(buffer);
                // reset buffer to data only
                buffer.reset();
                buffer.limit(limit);
                tag.put(ct);
                tag.flip();
                // Limit is how much of the ibuffer has been chopped off.
                len = buffer.remaining();
            } else {
                throw new AEADBadTagException("Input too short - need tag");
            }

            // 'len' contains the length in ibuffer and src
            checkDataLength(len);

            if (len > dst.remaining()) {
                throw new ShortBufferException("Output buffer too small");
            }

            processAAD();
            // Set the mark for a later reset. Either it will be zero, or the tag
            // buffer creation above will have consume some or all of it.
            ct.mark();

            // Perform GHASH check on data

            // If there is data stored in the buffer
            if (buffer != null && buffer.remaining() > 0) {
                ghashAllToS.update(buffer, buffer.remaining());
                // Process the overage
                if (buffer.remaining() > 0) {
                    // Fill out block between two buffers
                    if (ct.remaining() > 0) {
                    int over = buffer.remaining();
                        byte[] block = new byte[AES_BLOCK_SIZE];
                        // Copy the remainder of the buffer into the extra block
                        buffer.get(block, 0, over);

                        // Fill out block with what is in data
                        if (ct.remaining() > AES_BLOCK_SIZE - over) {
                            ct.get(block, over, AES_BLOCK_SIZE - over);
                            ghashAllToS.update(block, 0, AES_BLOCK_SIZE);
                        } else {
                            // If the remaining in buffer + data does not fill a
                            // block, complete the ghash operation
                            int l = ct.remaining();
                            ct.get(block, over, l);
                            ghashAllToS.doLastBlock(ByteBuffer.wrap(block), over + l);
                        }
                    } else {
                        // data is empty, so complete the ghash op with the
                        // remaining buffer
                        ghashAllToS.doLastBlock(buffer, buffer.remaining());
                    }
                }
                // Prepare buffer for decryption
                buffer.flip();
            }

            if (ct.remaining() > 0) {
                ghashAllToS.doLastBlock(ct, ct.remaining());
            }
            // Prepare buffer for decryption if available
            ct.reset();

            byte[] block = getLengthBlock(sizeOfAAD, len);
            ghashAllToS.update(block);
            block = ghashAllToS.digest();
            new GCTR(blockCipher, preCounterBlock).doFinal(block, 0, tagLenBytes, block, 0);

            // check entire authentication tag for time-consistency
            int mismatch = 0;
            for (int i = 0; i < tagLenBytes; i++) {
                mismatch |= tag.get() ^ block[i];
            }

            if (mismatch != 0) {
                throw new AEADBadTagException("Tag mismatch!");
            }

            // Decrypt the all the input data and put it into dst
            doLastBlock(buffer, ct, dst);
            restoreDst(dst);
            // 'processed' from the gctr decryption operation, not ghash
            return processed;
        }

        @Override
        int cryptBlocks(byte[] in, int inOfs, int inLen, byte[] out,
            int outLen) {
            return 0;
        }

        @Override
        int cryptBlocks(byte[] in, int inOfs, int inLen, ByteBuffer dst) {
            return 0;
        }

        @Override
        int cryptBlocks(ByteBuffer src, ByteBuffer dst) {
            return 0;
        }
    }

    public static final class AESGCM extends GaloisCounterMode {
        public AESGCM() {
            super(-1, new AESCrypt());
        }
    }

    public static final class AES128 extends GaloisCounterMode {
        public AES128() {
            super(16, new AESCrypt());
        }
    }

    public static final class AES192 extends GaloisCounterMode {
        public AES192() {
            super(24, new AESCrypt());
        }
    }

    public static final class AES256 extends GaloisCounterMode {
        public AES256() {
            super(32, new AESCrypt());
        }
    }
}
