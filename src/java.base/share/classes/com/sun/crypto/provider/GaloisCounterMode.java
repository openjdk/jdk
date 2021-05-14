/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    // Engine instance for encryption or decryption
    private GCMEngine engine;

    private boolean encryption = true;
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
    //private static final int TRIGGERLEN = 65536;  // 64k
    private static final int TRIGGERLEN = 4096;  // 64k

    private boolean initialized = false;
    // Default value is 128bits, this in stores bytes.
    int tagLenBytes = 16;
    // Key size if the value is passed
    int keySize;
    // Prevent reuse of iv or key
    boolean reInit = false;
    final static byte[] emptyBuf = new byte[0];
    byte[] lastKey = emptyBuf;
    byte[] lastIv = emptyBuf;
    byte[] iv = null;

    static enum THROTTLE { GHASH, GCTR, BOTH }


    /**
     *
     * @param keySize length of key.
     * @param embeddedCipher Cipher object, such as AESCrypt.
     */
    GaloisCounterMode(int keySize, SymmetricCipher embeddedCipher) {
        blockCipher = embeddedCipher;
        this.keySize = keySize;
    }

    /**
     * Initializes the cipher in the specified mode with the given key
     * and iv.
     */
    void init(int optmode, Key key, GCMParameterSpec spec)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        encryption = (optmode == Cipher.ENCRYPT_MODE) ||
            (optmode == Cipher.WRAP_MODE);

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

        // Check for reuse
        if (encryption) {
            if (Arrays.compare(keyValue, lastKey) == 0 && Arrays.compare(iv,
                lastIv) == 0) {
                throw new InvalidAlgorithmParameterException(
                    "Cannot reuse iv for GCM encryption");
            }

            // Both values are already clones
            lastKey = keyValue;
            lastIv = iv;
        } else {
            if (spec == null) {
                throw new InvalidKeyException("No GCMParameterSpec specified");
            }
        }

        reInit = false;

        int tagLen = spec.getTLen();
        if (tagLen < 96 || tagLen > 128 || ((tagLen & 0x07) != 0)) {
            throw new InvalidAlgorithmParameterException
                ("Unsupported TLen value.  Must be one of " +
                    "{128, 120, 112, 104, 96}");
        }
        tagLenBytes = tagLen >> 3;

        // always encrypt mode for embedded cipher
        blockCipher.init(false, key.getAlgorithm(), keyValue);
    }

    // return tag length in bytes
    int getTagLen() {
        return this.tagLenBytes;
    }

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (!mode.equalsIgnoreCase("GCM")) {
            throw new NoSuchAlgorithmException("Mode must be GCM");
        }
    }

    @Override
    protected void engineSetPadding(String padding)
        throws NoSuchPaddingException {
        if (!padding.equalsIgnoreCase("NoPadding")) {
            throw new NoSuchPaddingException("Padding must be NoPadding");
        }
    }

    @Override
    protected int engineGetBlockSize() {
        return blockCipher.getBlockSize();
    }

    @Override
    protected int engineGetOutputSize(int inputLen) {
        checkInit();
        return engine.getOutputSize(inputLen, true);
    }

    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        return super.engineGetKeySize(key);
    }

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
        if (random != null) {
            random.nextBytes(iv);
        } else {
            new SecureRandom().nextBytes(iv);
        }
        return iv;
    }

    SecureRandom random = null;
    @Override
    protected AlgorithmParameters engineGetParameters() {
        GCMParameterSpec spec;
        spec = new GCMParameterSpec(getTagLen() * 8,
            iv == null ? createIv(random) : iv.clone());
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

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random)
        throws InvalidKeyException {
        if (opmode == Cipher.DECRYPT_MODE || opmode == Cipher.UNWRAP_MODE) {
            throw new InvalidKeyException("No GCMParameterSpec specified");
        }
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException e) {
            // never happen
        }
    }

    @Override
    protected void engineInit(int opmode, Key key,
        AlgorithmParameterSpec params, SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {

        GCMParameterSpec spec;
        if (params == null) {
            iv = createIv(random);
            spec = new GCMParameterSpec(getTagLen() * 8, iv);
        } else {
            if (!(params instanceof GCMParameterSpec)) {
                throw new InvalidAlgorithmParameterException(
                    "AlgorithmParameterSpec not of GCMParameterSpec");
            }
            spec = (GCMParameterSpec)params;
            iv = spec.getIV();
            if (iv == null) {
                throw new InvalidAlgorithmParameterException("IV is null");
            }
            if (iv.length == 0) {
                throw new InvalidAlgorithmParameterException("IV is empty");
            }
        }
        init(opmode, key, spec);
        initialized = true;
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
        engineInit(opmode, key, spec, random);
    }

    void checkInit() {
        if (!initialized) {
            throw new IllegalStateException("Operation not initialized.");
        }

        if (engine == null) {
            if (encryption) {
                engine = new GCMEncrypt(blockCipher);
            } else {
                engine = new GCMDecrypt(blockCipher);
            }
        }
    }

    void checkReInit() {
        if (reInit) {
            throw new IllegalStateException(
                "Must use either different key or " + " iv for GCM encryption");
        }
    }

    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        checkInit();
        return engine.doUpdate(input, inputOffset, inputLen);
    }

    @Override
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen,
        byte[] output, int outputOffset) throws ShortBufferException {
        checkInit();
        int len = engine.getOutputSize(inputLen, false);
        if (len > output.length - outputOffset) {
            throw new ShortBufferException("Output buffer too small, must be " +
                "at least " + len + " bytes long");
        }
        return engine.doUpdate(input, inputOffset, inputLen, output,
            outputOffset);
    }

    @Override
    protected int engineUpdate(ByteBuffer src, ByteBuffer dst)
        throws ShortBufferException {
        checkInit();
        int len = engine.getOutputSize(src.remaining(), false);
        if (len > dst.remaining()) {
            throw new ShortBufferException(
                "Output buffer must be at least " + len + " bytes long");
        }
        return engine.doUpdate(src, dst);
    }

    @Override
    protected void engineUpdateAAD(byte[] src, int offset, int len) {
        checkInit();
        engine.updateAAD(src, offset, len);
    }

    @Override
    protected void engineUpdateAAD(ByteBuffer src) {
        checkInit();
        if (src.hasArray()) {
            int pos = src.position();
            int len = src.remaining();
            engine.updateAAD(src.array(), src.arrayOffset() + pos, len);
            src.position(pos + len);
        } else {
            byte[] aad = new byte[src.remaining()];
            src.get(aad);
            engine.updateAAD(aad, 0, aad.length);
        }
    }

    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset,
        int inputLen) throws IllegalBlockSizeException, BadPaddingException {
        checkInit();
        byte[] output = new byte[engine.getOutputSize(inputLen, true)];
        if (input == null) {
            input = emptyBuf;
        }
        try {
            engine.doFinal(input, inputOffset, inputLen, output, 0);
        } catch (ShortBufferException e) {
            throw new ProviderException(e);
        } finally {
            // Release crypto engine
            engine = null;
        }
        return output;
    }

    @Override
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen,
        byte[] output, int outputOffset) throws ShortBufferException,
        IllegalBlockSizeException, BadPaddingException {
        checkInit();
        if (input == null) {
            input = emptyBuf;
        }
        try {
            ArrayUtil.nullAndBoundsCheck(input, inputOffset, inputLen);
        } catch (Exception e) {
            // Release crypto engine
            engine = null;
            throw new IllegalBlockSizeException("input array invalid");
        }
        int len = engine.doFinal(input, inputOffset, inputLen, output,
            outputOffset);

        // Release crypto engine
        engine = null;

        return len;
    }

    @Override
    protected int engineDoFinal(ByteBuffer src, ByteBuffer dst)
        throws ShortBufferException, IllegalBlockSizeException,
        BadPaddingException {
        checkInit();

        int len = engine.doFinal(src, dst);

        // Release crypto engine
        engine = null;

        return len;
    }

    @Override
    protected byte[] engineWrap(Key key) throws IllegalBlockSizeException,
        InvalidKeyException {
        checkInit();
        try {
            byte[] encodedKey = key.getEncoded();
            if ((encodedKey == null) || (encodedKey.length == 0)) {
                throw new InvalidKeyException(
                    "Cannot get an encoding of the key to be wrapped");
            }
            return engineDoFinal(encodedKey, 0, encodedKey.length);
        } catch (BadPaddingException e) {
            // should never happen
        } finally {
            // Release crypto engine
            engine = null;
        }
        return null;
    }

    @Override
    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
        int wrappedKeyType) throws InvalidKeyException,
        NoSuchAlgorithmException {
        checkInit();

        byte[] encodedKey;
        try {
            encodedKey = engineDoFinal(wrappedKey, 0,
                wrappedKey.length);
        } catch (BadPaddingException ePadding) {
            throw new InvalidKeyException(
                "The wrapped key is not padded correctly");
        } catch (IllegalBlockSizeException eBlockSize) {
            throw new InvalidKeyException(
                "The wrapped key does not have the correct length");
        }
        return ConstructKeys.constructKey(encodedKey, wrappedKeyAlgorithm,
                                          wrappedKeyType);
    }

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

    private static final VarHandle wrapToByteArray =
        MethodHandles.byteArrayViewVarHandle(long[].class,
            ByteOrder.BIG_ENDIAN);

    private static byte[] getLengthBlock(int ivLenInBytes) {
        byte[] out = new byte[AES_BLOCK_SIZE];
        wrapToByteArray.set(out, 8, ((long)ivLenInBytes  & 0xFFFFFFFFL) << 3);
        return out;
    }

    private static byte[] getLengthBlock(int aLenInBytes, int cLenInBytes) {
        byte[] out = new byte[AES_BLOCK_SIZE];
        wrapToByteArray.set(out, 0, ((long)aLenInBytes & 0xFFFFFFFFL) << 3);
        wrapToByteArray.set(out, 8, ((long)cLenInBytes & 0xFFFFFFFFL) << 3);
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
            g.update(getLengthBlock(iv.length));
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
        if (engine.processed > max) {
            throw new ProviderException("SunJCE provider only supports " +
                "input size up to " + MAX_BUF_SIZE + " bytes");
        }
    }

    /**
     * Abstract class for GCMEncrypt and GCMDecrypt internal context objects
     */
    abstract class GCMEngine {
        byte[] preCounterBlock;

        GCTR gctrPAndC;
        GHASH ghashAllToS;

        // Block size of the algorithm
        int blockSize;

        // length of total data, i.e. len(C)
        int processed = 0;

        // buffer for AAD data; if null, meaning update has been called
        ByteArrayOutputStream aadBuffer = null;
        int sizeOfAAD = 0;
        boolean aadProcessed = false;

        // buffer data for crypto operation
        ByteArrayOutputStream ibuffer = null;

        // Original dst buffer if there was an overlap situation
        ByteBuffer originalDst = null;
        byte[] originalOut = null;
        int originalOutOfs = 0;


        GCMEngine(SymmetricCipher blockCipher) {
            blockSize = blockCipher.getBlockSize();
            byte[] subkeyH = new byte[blockSize];

            blockCipher.encryptBlock(subkeyH, 0, subkeyH,0);
            preCounterBlock = getJ0(iv, subkeyH);
            byte[] j0Plus1 = preCounterBlock.clone();
            increment32(j0Plus1);
            gctrPAndC = new GCTR(blockCipher, j0Plus1);
            ghashAllToS = new GHASH(subkeyH);
        }

        /**
         * Get output buffer size
         * @param inLen Contains the length of the input data and buffered data.
         * @param isFinal true if this is a doFinal operation
         * @return If it's an update operation, inLen must blockSize
         *         divisible.  If it's a final operation, output will
         *         include the tag.
         */
        abstract int getOutputSize(int inLen, boolean isFinal);

        // Update operations
        abstract byte[] doUpdate(byte[] in, int inOff, int inLen);
        abstract int doUpdate(byte[] in, int inOff, int inLen, byte[] out,
            int outOff) throws ShortBufferException;
        abstract int doUpdate(ByteBuffer src, ByteBuffer dst)
            throws ShortBufferException;

        // Final operations
        abstract int doFinal(byte[] in, int inOff, int inLen, byte[] out,
            int outOff) throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException;
        abstract int doFinal(ByteBuffer src, ByteBuffer dst)
            throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException;

        //abstract int throttleData(THROTTLE ghash, ByteBuffer src, ByteBuffer dst);
//        abstract int cryptBlocks(GCM op, byte[] in, int inOfs, int inLen, byte[] out, int outOfs);
//        abstract int cryptBlocks(GCM op, ByteBuffer src, ByteBuffer dst);


        // Initialize internal data buffer, if not already.
        void initBuffer(int len) {
            if (ibuffer == null) {
                ibuffer = new ByteArrayOutputStream(len);
            }
        }

        // Helper method for getting ibuffer size
        int getBufferedLength() {
            return (ibuffer == null ? 0 : ibuffer.size());
        }

        int mergeBlock(byte[] buffer, int bufOfs, byte[] in, int inOfs,
            int inLen, byte[] block) {
            return mergeBlock(buffer, bufOfs, buffer.length - bufOfs, in,
                inOfs, inLen, block);
        }

        /**
         * The method takes two buffers to create one block of data
         *
         * This in only called when buffer length is less that a blockSize
         * @return number of bytes used from 'in'
         */
        int mergeBlock(byte[] buffer, int bufOfs, int bufLen, byte[] in,
            int inOfs, int inLen, byte[] block) {
            if (bufLen > blockSize) {
                throw new RuntimeException("mergeBlock called on an ibuffer " +
                    "too big:  " + bufLen + " bytes");
            }

            System.arraycopy(buffer, bufOfs, block, 0, bufLen);
            int inUsed = Math.min(block.length - bufLen,
                (inLen > block.length ? block.length : inLen));
            System.arraycopy(in, inOfs, block, bufLen, inUsed);
            return inUsed;
        }

        /**
         * Continues a multi-part update of the Additional Authentication
         * Data (AAD), using a subset of the provided buffer.  All AAD must be
         * supplied before beginning operations on the ciphertext (via the
         * {@code update} and {@code doFinal} methods).
         *
         * @param src the buffer containing the AAD
         * @param offset the offset in {@code src} where the AAD input starts
         * @param len the number of AAD bytes
         *
         * @throws IllegalStateException if this cipher is in a wrong state
         * (e.g., has not been initialized) or does not accept AAD, and one of
         * the {@code update} methods has already been called for the active
         * encryption/decryption operation
         * @throws UnsupportedOperationException if this method
         * has not been overridden by an implementation
         */
        void updateAAD(byte[] src, int offset, int len) {
            if (encryption) {
                checkReInit();
            }

            if (aadBuffer == null) {
                if (sizeOfAAD == 0 && !aadProcessed) {
                    aadBuffer = new ByteArrayOutputStream(len);
                } else {
                    // update has already been called
                    throw new IllegalStateException
                        ("Update has been called; no more AAD data");
                }
            }
            aadBuffer.write(src, offset, len);
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
                        byte[] padded = expandToOneBlock(aad,
                            aad.length - lastLen, lastLen);
                        ghashAllToS.update(padded);
                    } else {
                        ghashAllToS.update(aad);
                    }
                }
                aadBuffer = null;
            }
            aadProcessed = true;
        }

        /**
         * Process en/decryption all the way to the last block.  It takes both
         * For input it takes the ibuffer which is wrapped in 'buffer' and 'src'
         * from doFinal.
         */
        void doLastBlock(GCM op, ByteBuffer buffer, ByteBuffer src, ByteBuffer dst) {

            if (buffer != null && buffer.remaining() > 0) {
                // If there is no src, just finish the rest of the buffer.
                if (src.remaining() == 0) {
                    //processed += cryptBlocks(buffer, dst);
                    /*
                    dst.mark();
                    int l = gctrPAndC.doFinal(buffer, dst);
                    dst.reset();
                    ghashAllToS.doFinal(dst, l);
                    processed += l;
                     */
                    processed += op.doFinal(buffer, dst);
                    ibuffer.reset();
                    return;
                }
                // en/decrypt on how much buffer there is in blockSize
                if (buffer.remaining() >= blockSize) {
                    processed += op.update(buffer, dst);
                }
                // Process the remainder in the ibuffer with src data
                if (src.remaining() + buffer.remaining() >= blockSize) {
                    byte[] block = new byte[AES_BLOCK_SIZE];
                    // Copy the remainder of the buffer into the extra block
                    int over = buffer.remaining();
                    buffer.get(block, 0, over);
                    src.get(block, over, AES_BLOCK_SIZE - over);
                    processed += op.update(ByteBuffer.wrap(block), dst);
                } else {
                    byte[] block =
                        new byte[src.remaining() + buffer.remaining()];
                    int over = buffer.remaining();
                    buffer.get(block, 0, over);
                    src.get(block, over, src.remaining());
                    //processed += gctrPAndC.doFinal(ByteBuffer.wrap(block), dst);
                    /*dst.mark();
                    int l = gctrPAndC.doFinal(ByteBuffer.wrap(block), dst);
                    dst.reset();
                    ghashAllToS.doFinal(dst, l);
                    processed += l;
                     */
                    processed += op.doFinal(ByteBuffer.wrap(block), dst);
                    ibuffer.reset();
                    return;
                }
                ibuffer.reset();
            }

            /*
             * At this point there are two scenarios.  Either there is
             * remaining data in the buffer that does not fill a block,
             * or there is only src data remaining of any length.
             *
             * doFinal must be called so it can reset the object.
             */

            if (src.remaining() > 0) {
                processed += op.update(src, dst);
                /*
                dst.mark();
                int l = gctrPAndC.doFinal(src, dst);
                dst.reset();
                ghashAllToS.doFinal(dst, l);
                processed += l;
                 */
                processed += op.doFinal(src, dst);
            }
        }


        /**
         * Check for overlap. If the src and dst buffers are using shared data
         * and if dst will overwrite src data before src can be processed.
         * If so, make a copy to put the dst data in.
         */
        ByteBuffer overlapDetection(ByteBuffer src, ByteBuffer dst) {
            if (src.isDirect() && dst.isDirect()) {
                DirectBuffer dsrc = (DirectBuffer) src;
                DirectBuffer ddst = (DirectBuffer) dst;

                // Get the current memory address for the given ByteBuffers
                long srcaddr = dsrc.address();
                long dstaddr = ddst.address();

                // Find the lowest attachment that is the base memory address
                // of the shared memory for the src object
                while (dsrc.attachment() != null) {
                    srcaddr = ((DirectBuffer) dsrc.attachment()).address();
                    dsrc = (DirectBuffer) dsrc.attachment();
                }

                // Find the lowest attachment that is the base memory address
                // of the shared memory for the dst object
                while (ddst.attachment() != null) {
                    dstaddr = ((DirectBuffer) ddst.attachment()).address();
                    ddst = (DirectBuffer) ddst.attachment();
                }

                // If the base addresses are not the same, there is no overlap
                if (srcaddr != dstaddr) {
                    return dst;
                }
                // At this point we know these objects share the same memory.
                // This checks the starting position of the src and dst address
                // for overlap.
                // It uses the base address minus the passed object's address to
                // get the offset from the base address, then add the position()
                // from the passed object.  That gives up the true offset from
                // the base address.  As long as the src side is >= the dst
                // side, we are not in overlap.
                if (((DirectBuffer) src).address() - srcaddr + src.position() >=
                    ((DirectBuffer) dst).address() - dstaddr + dst.position()) {
                    return dst;
                }

            } else if (!src.isDirect() && !dst.isDirect()) {
                // if src is read only, then we need a copy
                if (!src.isReadOnly()) {
                    // If using the heap, check underlying byte[] address.
                    if (!src.array().equals(dst.array()) ) {
                        return dst;
                    }

                    // Position plus arrayOffset() will give us the true offset
                    // from the underlying byte[] address.
                    if (src.position() + src.arrayOffset() >=
                        dst.position() + dst.arrayOffset()) {
                        return dst;
                    }
                }
            } else {
                // buffer types are not the same and can be used as-is
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
         * Overlap detection for data using byte array.
         * If an intermediate array is needed, the whole original out array
         * length is allocated because it's simpler than keep the same offset
         * and hope the expected output is
         */
        byte[] overlapDetection(byte[] in, int inOfs, byte[] out, int outOfs) {
            if (in == out && inOfs < outOfs) {
                originalOut = out;
                originalOutOfs = outOfs;
                return new byte[out.length];
            }
            return out;
        }

        /**
         * If originalDst is not null, 'dst' is an internal buffer and it's
         * data will be copied to the original dst buffer
         */
        void restoreDst(ByteBuffer dst) {
            if (originalDst == null) {
                return;
            }

            dst.flip();
            originalDst.put(dst);
        }

        /**
         * If originalOut is not null, the 'out' is an internal buffer and it's
         * data will be copied into original out byte[];
         * @param out
         * @param len
         */
        void restoreOut(byte[] out, int len) {
            if (originalOut == null) {
                return;
            }

            System.arraycopy(out, originalOutOfs, originalOut, originalOutOfs,
                len);
        }
    }

    /**
     * Encryption Engine object
     */
    class GCMEncrypt extends GCMEngine {
        GCTRGHASH op;

        GCMEncrypt(SymmetricCipher blockCipher) {
            super(blockCipher);
            op = new GCTRGHASH(gctrPAndC, ghashAllToS);
        }

        @Override
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
            checkReInit();
            byte[] output = new byte[getOutputSize(inLen, false)];
            try {
                doUpdate(in, inOff, inLen, output, 0);
            } catch (ShortBufferException e) {
                // update decryption has no output
            }
            return output;
        }

        /**
         * Encrypt update operation.  This uses both the ibuffer and 'in' to
         * encrypt as many blocksize data as possible.  Any remainding data is
         * put into the ibuffer.
         */
        @Override
        public int doUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
            checkReInit();
            checkDataLength(inLen, getBufferedLength());
            ArrayUtil.nullAndBoundsCheck(in, inOfs, inLen);
            ArrayUtil.nullAndBoundsCheck(out, outOfs, out.length - outOfs);
            processAAD();

            //ArrayUtil.nullAndBoundsCheck(out, outOfs, inLen);
            return encryptBlocks(in, inOfs, inLen, out, outOfs);
        }

        /**
         * Encrypt update operation.  This uses both the ibuffer and 'src' to
         * encrypt as many blocksize data as possible.  Any remainding data is
         * put into the ibuffer.
         */
        @Override
        public int doUpdate(ByteBuffer src, ByteBuffer dst)
            throws ShortBufferException {
            checkReInit();
            return encryptBlocks((ibuffer == null || ibuffer.size() == 0) ?
                null : ByteBuffer.wrap(ibuffer.toByteArray()), src, dst);
        }

        /**
         * Return final encrypted data with auth tag using byte[]
         */
        @Override
        public int doFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws IllegalBlockSizeException, ShortBufferException {
            checkReInit();
            try {
                ArrayUtil.nullAndBoundsCheck(out, outOfs, getOutputSize(inLen,
                    true));
            } catch (ArrayIndexOutOfBoundsException aiobe) {
                throw new ShortBufferException("Output buffer invalid");
            }

            int bufLen = getBufferedLength();
            checkDataLength(inLen, bufLen, tagLenBytes);
            processAAD();
            out = overlapDetection(in, inOfs, out, outOfs);

            int resultLen = inLen;
            byte[] block;

            // process what is in the ibuffer
            if (bufLen > 0) {
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
                if (bufLen > 0 && inLen > 0 && bufLen + inLen >= blockSize) {
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

            // process what is left in the input buffer
            if (inLen > 0) {
                if (inLen > blockSize) {
                    int r = cryptBlocks(in, inOfs, inLen, out, outOfs);
                    inOfs += r;
                    inLen -= r;
                    outOfs += r;
                }
                doLastBlock(in, inOfs, inLen, out, outOfs);
            }

            block = getLengthBlock(sizeOfAAD, processed);
            ghashAllToS.update(block);
            block = ghashAllToS.digest();
            new GCTR(blockCipher, preCounterBlock).doFinal(block, 0,
                tagLenBytes, block, 0);

            // copy the tag to the end of the buffer
            System.arraycopy(block, 0, out, (outOfs + inLen), tagLenBytes);
            restoreOut(out, resultLen + tagLenBytes);

            reInit = true;

            return (resultLen + tagLenBytes);
        }

        /**
         * Return final encrypted data with auth tag using bytebuffers
         */
        @Override
        public int doFinal(ByteBuffer src, ByteBuffer dst) throws
            IllegalBlockSizeException, ShortBufferException {
            checkReInit();
            dst = overlapDetection(src, dst);
            int len = src.remaining() + getBufferedLength();

            // 'len' includes ibuffer data
            checkDataLength(len, tagLenBytes);
            //dst.mark();
            if (dst.remaining() < len + tagLenBytes) {
                throw new ShortBufferException("Output buffer too small, must" +
                    "be at least " + (len + tagLenBytes) + " bytes long");
            }

            processAAD();
            if (len > 0) {
                doLastBlock(new GCTRGHASH(gctrPAndC, ghashAllToS), (ibuffer == null || ibuffer.size() == 0) ?
                    null : ByteBuffer.wrap(ibuffer.toByteArray()), src, dst);
                //dst.mark();
                //int l = gctrPAndC.doFinal(src, dst);
                //dst.reset();
                //ghashAllToS.doLastBlock(dst, l);
                //processed += l;

                //dst.reset();
                //ghashAllToS.doLastBlock(dst, len);
            }

            byte[] block = getLengthBlock(sizeOfAAD, processed);
            ghashAllToS.update(block);
            block = ghashAllToS.digest();
            new GCTR(blockCipher, preCounterBlock).doFinal(block, 0,
                tagLenBytes, block, 0);
            dst.put(block, 0, tagLenBytes);
            restoreDst(dst);

            reInit = true;

            return (len + tagLenBytes);
        }


        void doLastBlock(byte[] in, int inOfs, int inLen, byte[] out, int outOfs) {
            int ilen = inLen;
            gctrPAndC.doFinal(in, inOfs, inLen, out, outOfs);
            processed += inLen;

            int lastLen = ilen % AES_BLOCK_SIZE;
            if (lastLen != 0) {
                ghashAllToS.update(out, outOfs, ilen - lastLen);
                ghashAllToS.update(
                    expandToOneBlock(out, (outOfs + ilen - lastLen), lastLen));
            } else {
                ghashAllToS.update(out, outOfs, ilen);
            }
        }
        /*
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

            // Divide up larger data sizes to trigger CTR & GHASH intrinsic
            // quicker
            if (len > TRIGGERLEN) {
                int i = 0;
                int tlen;  // incremental lengths
                final int plen = AES_BLOCK_SIZE * 6;
                // arbitrary formula to aid intrinsic w/o reaching buffer end
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

         */

        /**
         * Take any less than blocksize data from ibuffer and combine it with
         * 'in' to encrypt or decrypt a block, then encrypted what it can
         * from the remaining blocksize bytes in 'in'.  Any bytes under a
         * blocksize are put into the 'ibuffer'.
         */
        int encryptBlocks(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
            // 'inLen' stores the length to use with buffer 'in'.
            // 'len' stores the length returned by the method.
            int len = 0;
            int bLen = getBufferedLength();

            processAAD();

            // if there is enough data in the ibuffer and 'in', encrypt it.
            if (bLen > 0) {
                byte[] buffer = ibuffer.toByteArray();
                // number of bytes not filling a block
                int remainder = bLen % blockSize;
                // number of bytes along block boundary
                bLen -= remainder;

                // If there is enough bytes in ibuffer for a block or more,
                // encrypt that first.
                if (bLen > 0) {
                    len += cryptBlocks(buffer, 0, bLen, out, outOfs);
                    outOfs += bLen;
                }

                // blen is now the offset for 'buffer'

                // Construct and encrypt a block if there is enough 'buffer' and
                // 'in' to make one
                if ((inLen + remainder) >= blockSize) {
                    byte[] block = new byte[blockSize];

                    System.arraycopy(buffer, bLen, block, 0, remainder);
                    int inLenUsed = blockSize - remainder;
                    System.arraycopy(in, inOfs, block, remainder, inLenUsed);

                    len += cryptBlocks(block, 0, blockSize, out, outOfs);
                    inOfs += inLenUsed;
                    inLen -= inLenUsed;
                    outOfs += blockSize;
                    ibuffer.reset();
                    // Code below will write the remainder from 'in' to ibuffer
                } else if (remainder > 0) {
                    // If a block or more was encrypted from 'buffer' only, but
                    // the rest of 'buffer' with 'in' could not construct a
                    //  block, then put the rest of 'buffer' back into ibuffer.
                    ibuffer.reset();
                    ibuffer.write(buffer, bLen, remainder);
                    // Code below will write the remainder from 'in' to ibuffer
                }
            }

            // Encrypt the remaining blocks inside of 'in'
            if (inLen > 0) {
                len += cryptBlocks(in, inOfs, inLen, out, outOfs);
            }

            // Write any remaining bytes less than a blockSize into ibuffer.
            int remainder = inLen % blockSize;
            if (remainder > 0) {
                initBuffer(remainder);
                inLen -= remainder;
                // remainder offset is based on original buffer length
                ibuffer.write(in, inOfs + inLen, remainder);
            }

            return len;
        }

        /**
         * Take any less than blocksize data from ibuffer and combine it with
         * 'src' to encrypt or decrypt a block, then encrypted what it can
         * from the remaining blocksize bytes in 'src'.  Any bytes under a
         * blocksize are put into the 'ibuffer'.
         *
         * Because this does encrypt and decrypt, ibuffer could be > blockSize
         */
        int encryptBlocks(ByteBuffer buffer, ByteBuffer src, ByteBuffer dst) {
            processAAD();
            // 'inLen' stores the length to use with buffer 'in'.
            // 'len' stores the length returned by the method.
            int len = 0;

            // if there is enough data in the ibuffer and 'in', encrypt it.
            if (buffer != null && buffer.remaining() > 0) {
                // number of bytes not filling a block
                int remainder = buffer.remaining() % blockSize;
                // number of bytes along block boundary
                int blen = ibuffer.size() - remainder;

                // If there is enough bytes in ibuffer for a block or more,
                // en/decrypt that first.
                if (blen > 0) {
                    len += cryptBlocks(buffer, dst);
                }

                // Check if there is any data left in the buffer, if there is,
                // try to construct a block with data from the buffer and src
                // to en/decrypt.
                if (buffer.remaining() == 0) {
                    ibuffer.reset();
                } else {
                    if ((buffer.remaining() + src.remaining()) >= blockSize) {
                        byte[] block = new byte[blockSize];
                        buffer.get(block, 0, remainder);
                        src.get(block, remainder, blockSize - remainder);
                        len += cryptBlocks(ByteBuffer.wrap(block, 0, blockSize), dst);
                        ibuffer.reset();
                    }
                }
            }

            // encrypt any blocksized data in 'src'
            if (src.remaining() >= blockSize) {
                len += cryptBlocks(src, dst);
            }

            // Write the remaining bytes into the 'ibuffer'
            if (src.remaining() > 0) {
                initBuffer(src.remaining());
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


        int throttleData(byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs) {

            if (inLen < TRIGGERLEN) {
                return 0;
            }
            int segments = (inLen / 6);
            segments -= segments % blockSize;
            int len, resultLen = 0;
            int i = 0;
            while (i < 5) {
                len = op.update(in, inOfs, segments, out, outOfs);
                outOfs += len;
                inOfs += len;
                inLen -= len;
                resultLen += len;
                i++;
            }
            resultLen += op.update(in, inOfs, inLen, out, outOfs);
            return resultLen;
        }

        int cryptBlocks(byte[] in, int inOfs, int inLen, byte[] out, int outOfs) {
            int len;
            if (inLen > TRIGGERLEN) {
                len = throttleData(in, inOfs, inLen, out, outOfs);
            } else {
                len = op.update(in, inOfs, inLen, out, outOfs);
            }
            processed += len;
            return len;
        }

        // Encrypt byte[] input and output
        int cryptBlocksImpl(byte[] in, int inOfs, int len, byte[] out, int outOfs) {
            len = gctrPAndC.update(in, inOfs, len, out, outOfs);
            processed += len;
            ghashAllToS.update(out, outOfs, len);
            return len;
        }

        /**
         * This segments large data into smaller chunks so hotspot will start
         * using CTR and GHASH intrinsics sooner.  This is a problem for app & perf tests
         * that only use large input sizes.
         */
        int throttleData(ByteBuffer src, ByteBuffer dst) {
            int inLen = src.limit();
            int segments = (src.remaining() / 6);
            segments -= segments % blockSize;
            int i = 0, resultLen = 0;
            do {
                src.limit(src.position() + segments);
                resultLen += op.update(src, dst);
            } while (++i < 5);

            src.limit(inLen);
            // If there is still at least a blockSize left
            if (src.remaining() > blockSize) {
                resultLen += op.update(src, dst);
            }

            return resultLen;
        }

        // Handler method to encryptiong blocks
        int cryptBlocks(ByteBuffer src, ByteBuffer dst) {
            int len;
            if (src.remaining() > TRIGGERLEN) {
                len = throttleData(src, dst);
            } else {
                len = op.update(src, dst);
            }
            processed += len;
            return len;
        }

        // Encrypt ByteBuffer input and output
        int cryptBlocksImpl(ByteBuffer src, ByteBuffer dst) {
            dst.mark();
            int len = gctrPAndC.update(src, dst);
            processed += len;
            dst.reset();
            ghashAllToS.update(dst, len);
            return len;
        }

        /*
         * Encrypt a byte[blockSize] and output it to a ByteBuffer.
         * This method is for merging data into one block from ibuffer and
         * an input buffer.  If dst.remaining() is less than the blockSize, it
         * substitute a byte[] to avoid overwriting
         */
        int cryptBlocks(byte[] in, int inOfs, int inLen, ByteBuffer dst) {
            dst.mark();
            int len = gctrPAndC.update(in, inOfs, inLen, dst);
            processed += len;
            dst.reset();
            ghashAllToS.update(dst, len);
            return len;
        }

    }

    /**
     * Decryption Engine object
     */
    class GCMDecrypt extends GCMEngine {
        // byte array of tag
        byte[] tag;
        // offset for byte[] operations
        int tagOfs = 0;

        GCMDecrypt(SymmetricCipher blockCipher) {
            super(blockCipher);
        }

        @Override
        public int getOutputSize(int inLen, boolean isFinal) {
            if (!isFinal) {
                return 0;
            }
            return Math.max(inLen + getBufferedLength() - tagLenBytes, 0);
        }

        // Put the input data into the ibuffer
        @Override
        byte[] doUpdate(byte[] in, int inOff, int inLen) {
            try {
                doUpdate(in, inOff, inLen, null, 0);
            } catch (ShortBufferException e) {
                // update decryption has no output
            }
            return new byte[0];
        }

        // Put the input data into the ibuffer
        @Override
        public int doUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {

            if (inLen > 0) {
                // store internally until decryptFinal is called because
                // spec mentioned that only return recovered data after tag
                // is successfully verified
                ArrayUtil.nullAndBoundsCheck(in, inOfs, inLen);
                initBuffer(inLen);
                ibuffer.write(in, inOfs, inLen);
            }
            return 0;
        }


        // Put the src data into the ibuffer
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
                    initBuffer(b.length);
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
         * Use any data from ibuffer and 'in' to first verify the auth tag. If
         * the tag is valid, decrypt the data.
         */
        @Override
        public int doFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException {
            GHASH save = null;

            int bufLen = getBufferedLength();
            int len = inLen + bufLen;
            if (len < tagLenBytes) {
                throw new AEADBadTagException("Input too short - need tag");
            }

            if (len - tagLenBytes > out.length - outOfs) {
                save = ghashAllToS.clone();
            }

            checkDataLength(len - tagLenBytes);
            processAAD();

            findTag(in, inOfs, inLen);
            byte[] block = getLengthBlock(sizeOfAAD,
                decryptBlocks(ghashAllToS, in, inOfs, inLen, null, 0));
            ghashAllToS.update(block);
            block = ghashAllToS.digest();
            new GCTR(blockCipher, preCounterBlock).doFinal(block, 0,
                tagLenBytes, block, 0);

            // check entire authentication tag for time-consistency
            int mismatch = 0;
            for (int i = 0; i < tagLenBytes; i++) {
                mismatch |= tag[i] ^ block[i];
            }

            if (mismatch != 0) {
                throw new AEADBadTagException("Tag mismatch!");
            }

            try {
                ArrayUtil.nullAndBoundsCheck(out, outOfs, len - tagLenBytes);
            } catch (ArrayIndexOutOfBoundsException aiobe) {
                throw new ShortBufferException("Output buffer invalid");
            }

            if (save != null) {
                throw new ShortBufferException("Output buffer too small, must" +
                    "be at least " + (len - tagLenBytes) + " bytes long");
            }

            out = overlapDetection(in, inOfs, out, outOfs);
            len = decryptBlocks(gctrPAndC, in, inOfs, inLen, out, outOfs);
            restoreOut(out, len);
            return len;
        }

        /**
         * Use any data from ibuffer and 'src' to first verify the auth tag. If
         * the tag is valid, decrypt the data.
         */
        @Override
        public int doFinal(ByteBuffer src, ByteBuffer dst)
            throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException {
            GHASH save = null;

            // If these are array backed ByteBuffers, use the underlying array
            if (src.hasArray() && dst.hasArray()) {
                int len = doFinal(src.array(),
                    src.arrayOffset() + src.position(),
                    src.remaining(), dst.array(),
                    dst.arrayOffset() + dst.position());
                src.position(src.limit());
                dst.position(dst.position() + len);
                return len;
            }

            // Check for overlap in the bytebuffers
            dst = overlapDetection(src, dst);

            // Length of the input
            ByteBuffer tag;
            ByteBuffer ct = src.duplicate();

            ByteBuffer buffer = ((ibuffer == null || ibuffer.size() == 0) ?
                null : ByteBuffer.wrap(ibuffer.toByteArray()));
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

            // Save GHASH context to allow the tag to be checked even though
            // the dst buffer is too short.  Context will be restored so the
            // method can be called again with the proper sized dst buffer.
            if (len > dst.remaining()) {
                save = ghashAllToS.clone();
            }

            processAAD();
            // Set the mark for a later reset. Either it will be zero, or the
            // tag buffer creation above will have consume some or all of it.
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
                        byte[] block = new byte[blockSize];
                        // Copy the remainder of the buffer into the extra block
                        buffer.get(block, 0, over);

                        // Fill out block with what is in data
                        if (ct.remaining() > blockSize - over) {
                            ct.get(block, over, blockSize - over);
                            ghashAllToS.update(block, 0, blockSize);
                        } else {
                            // If the remaining in buffer + data does not fill a
                            // block, complete the ghash operation
                            int l = ct.remaining();
                            ct.get(block, over, l);
                            ghashAllToS.doFinal(ByteBuffer.wrap(block),
                                over + l);
                        }
                    } else {
                        // 'ct' is empty, so complete the ghash op with the
                        // remaining buffer
                        ghashAllToS.doFinal(buffer, buffer.remaining());
                    }
                }
                // Prepare buffer for decryption
                buffer.flip();
            }

            if (ct.remaining() > 0) {
                throttleData(ghashAllToS, ct, null);
                ghashAllToS.doFinal(ct, ct.remaining());
            }

            // Prepare buffer for decryption if available
            ct.reset();

            byte[] block = getLengthBlock(sizeOfAAD, len);
            ghashAllToS.update(block);
            block = ghashAllToS.digest();
            new GCTR(blockCipher, preCounterBlock).doFinal(block, 0,
                tagLenBytes, block, 0);

            // check entire authentication tag for time-consistency
            int mismatch = 0;
            for (int i = 0; i < tagLenBytes; i++) {
                mismatch |= tag.get() ^ block[i];
            }

            if (mismatch != 0) {
                throw new AEADBadTagException("Tag mismatch!");
            }

            if (save != null) {
                ghashAllToS = save;
                throw new ShortBufferException("Output buffer too small, must" +
                    " be at least " + len + " bytes long");
            }

            // Decrypt the all the input data and put it into dst
            doLastBlock(gctrPAndC, buffer, ct, dst);
            restoreDst(dst);
            src.position(src.limit());
            engine = null;
            // 'processed' from the gctr decryption operation, not ghash
            return processed;
        }

        /**
         * Find the tag in a given input buffer
         *
         * If tagOfs > 0, the tag is inside 'in' along with encrypted data
         * If tagOfs = 0, 'in' contains only the tag
         * if tagOfs = blockSize, there is no data in 'in' and all the tag
         *   is in ibuffer
         * If tagOfs < 0, that tag is split between ibuffer and 'in'
         */
        void findTag(byte[] in, int inOfs, int inLen) {
            if (inLen >= tagLenBytes) {
                tagOfs = inLen - tagLenBytes;
                tag = new byte[tagLenBytes];
                System.arraycopy(in, inOfs + tagOfs, tag, 0,
                    tagLenBytes);
            } else {
                // tagOfs will be negative
                tag = new byte[tagLenBytes];
                byte[] buffer = ibuffer.toByteArray();
                tagOfs = mergeBlock(buffer,
                    buffer.length - (tagLenBytes - inLen), in, inOfs, inLen,
                    tag) - tagLenBytes;
            }
        }

        /**
         * This method organizes the data from the ibuffer and 'in' to
         * blocksize operations for GHASH and GCTR decryption operations.
         * When this method is used, all the data is either in the ibuffer
         * or in 'in'.
         */
        int decryptBlocks(GCM op, byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs) {
            byte[] buffer = null;
            byte[] block = null;
            int resultLen = 0;
            int bLen = getBufferedLength();
            int len = 0;

            // Calculate the encrypted data length inside the ibuffer
            // considering the tag location
            int ctBufLen = bLen;
            // Change the inLen based of the tag location.
            if (tagOfs < 0) {
                inLen = 0;
                ctBufLen += tagOfs;
            } else {
                inLen -= tagLenBytes;
            }

            // If there is no buffered data, only process the 'in'
            if (ctBufLen == 0) {
                int l = throttleData(op, in, inOfs, inLen, out, outOfs);
                if (l > 0) {
                    inOfs += l;
                    inLen -= l;
                    outOfs += l; // noop for ghash
                    len += l;
                }

                return len + op.doFinal(in, inOfs, inLen, out, outOfs);
            }

            if (bLen > 0) {
                buffer = ibuffer.toByteArray();
            }

            // If ibuffer has at least a block size worth of data, decrypt it
            if (ctBufLen >= blockSize) {
                resultLen = op.update(buffer, 0, ctBufLen, out, outOfs);
                outOfs += resultLen; // noop for ghash
                len += resultLen;
                // Preserve resultLen, as it becomes the ibuffer offset, if
                // needed, in the next op
            }

            // merge the remaining ibuffer with the 'in'
            int bufRemainder = ctBufLen - resultLen;

            if (bufRemainder > 0) {
                block = new byte[blockSize];
                int inUsed = mergeBlock(buffer, resultLen, bufRemainder, in,
                    inOfs, inLen, block);
                // update the input parameters for what was taken out of 'in'
                inOfs += inUsed;
                inLen -= inUsed;
                if ((bufRemainder + inUsed <= blockSize) && inLen == 0) {
                    // If there is no more data in the inLen, send this to
                    // doFinal below
                    in = block;
                    inOfs = 0;
                    inLen = bufRemainder + inUsed;
                } else {
                    // Do an update for the merged block and doFinal on the
                    // remainder in 'in'
                    len += op.update(block, 0, blockSize, out, outOfs);
                    outOfs += blockSize; // noop for ghash
                }
            }

//            return len + doLastBlock(ghash, in, inOfs, inLen, out, outOfs);
            int l = throttleData(op, in, inOfs, inLen, out, outOfs);
            if (l > 0) {
                inOfs += l;
                inLen -= l;
                outOfs += l; // noop for ghash
                len += l;
            }

            // Finish off the operation
            return len + op.doFinal(in, inOfs, inLen, out, outOfs);
        }

        int doLastBlock(GCM op, byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs) {
            int len, resultLen;

            len = throttleData(op, in, inOfs, inLen, out, outOfs);
            if (len > 0) {
                inOfs += len;
                inLen -= len;
                outOfs += len; // noop for ghash
            }

            resultLen = len;
            len = op.doFinal(in, inOfs, inLen, out, outOfs);
            resultLen += len;
            return resultLen;
        }

        int throttleData(GCM op, byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs) {

            if (inLen < TRIGGERLEN) {
                return 0;
            }
            int segments = (inLen / 6);
            int len = 0, resultLen = 0;
            segments -= segments % blockSize;
            int i = 0;
            while (i < 5) {
                len = op.update(in, inOfs, segments, out, outOfs);
                outOfs += len; // noop for ghash
                inOfs += len;
                inLen -= len;
                resultLen += len;
                i++;
            }

            resultLen += op.update(in, inOfs, inLen, out, outOfs);
            return resultLen;
        }

        int throttleData(GCM op, ByteBuffer src, ByteBuffer dst) {
            int inLen = src.limit();
            int segments = (src.remaining() / 6);
            segments -= segments % blockSize;
            int i = 0, resultLen = 0;
            do {
                src.limit(src.position() + segments);
                resultLen += op.update(src, dst);
            } while (++i < 6);

            src.limit(inLen);
            // If there is still at least a blockSize left
            if (src.remaining() > blockSize) {
                resultLen += op.update(src, dst);
            }

            return resultLen;
        }

        //@Override
        int cryptBlocks(GCM op, byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
            if (inLen > TRIGGERLEN) {
                return throttleData(op, in, inOfs, inLen, out, outOfs);
            }
            return op.update(in, inOfs, inLen, out, outOfs);
        }

        //@Override
        int cryptBlocks(GCM op, ByteBuffer src, ByteBuffer dst) {
            if (src.remaining() > TRIGGERLEN) {
                return throttleData(op, src, dst);
            }
            return op.update(src, dst);
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

    public static final class GCTRGHASH implements GCM {
        GCTR gctr;
        GHASH ghash;

        GCTRGHASH(GCTR c, GHASH g) {
            gctr = c;
            ghash = g;
        }

        @Override
        public int update(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
            int len = gctr.update(in, inOfs, inLen, out, outOfs);
            ghash.update(out, outOfs, len);
            return len;
        }

        @Override
        public int update(byte[] in, int inOfs, int inLen, ByteBuffer dst) {
            dst.mark();
            int len = gctr.update(in, inOfs, inLen, dst);
            dst.reset();
            ghash.update(dst, len);
            return len;
        }

        @Override
        public int update(ByteBuffer src, ByteBuffer dst) {
            dst.mark();
            int len = gctr.update(src, dst);
            dst.reset();
            ghash.update(dst, len);
            return len;
        }

        @Override
        public int doFinal(byte[] in, int inOfs, int inLen, byte[] out, int outOfs) {
            return 0;
        }

        @Override
        public int doFinal(ByteBuffer src, ByteBuffer dst) {
            dst.mark();
            int l = gctr.doFinal(src, dst);
            dst.reset();
            ghash.doFinal(dst, l);
            return l;
        }
    }
}
