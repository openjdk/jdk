/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.pkcs11;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import sun.nio.ch.DirectBuffer;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * Cipher implementation class. This class currently supports
 * DES, DESede, AES, ARCFOUR, and Blowfish.
 *
 * This class is designed to support ECB, CBC, CTR with NoPadding
 * and ECB, CBC with PKCS5Padding. It will use its own padding impl
 * if the native mechanism does not support padding.
 *
 * Note that PKCS#11 currently only supports ECB, CBC, and CTR.
 * There are no provisions for other modes such as CFB, OFB, and PCBC.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11Cipher extends CipherSpi {

    // mode constant for ECB mode
    private final static int MODE_ECB = 3;
    // mode constant for CBC mode
    private final static int MODE_CBC = 4;
    // mode constant for CTR mode
    private final static int MODE_CTR = 5;

    // padding constant for NoPadding
    private final static int PAD_NONE = 5;
    // padding constant for PKCS5Padding
    private final static int PAD_PKCS5 = 6;

    private static interface Padding {
        // ENC: format the specified buffer with padding bytes and return the
        // actual padding length
        int setPaddingBytes(byte[] paddingBuffer, int padLen);

        // DEC: return the length of trailing padding bytes given the specified
        // padded data
        int unpad(byte[] paddedData, int len)
                throws BadPaddingException, IllegalBlockSizeException;
    }

    private static class PKCS5Padding implements Padding {

        private final int blockSize;

        PKCS5Padding(int blockSize)
                throws NoSuchPaddingException {
            if (blockSize == 0) {
                throw new NoSuchPaddingException
                        ("PKCS#5 padding not supported with stream ciphers");
            }
            this.blockSize = blockSize;
        }

        public int setPaddingBytes(byte[] paddingBuffer, int padLen) {
            Arrays.fill(paddingBuffer, 0, padLen, (byte) (padLen & 0x007f));
            return padLen;
        }

        public int unpad(byte[] paddedData, int len)
                throws BadPaddingException, IllegalBlockSizeException {
            if ((len < 1) || (len % blockSize != 0)) {
                throw new IllegalBlockSizeException
                    ("Input length must be multiples of " + blockSize);
            }
            byte padValue = paddedData[len - 1];
            if (padValue < 1 || padValue > blockSize) {
                throw new BadPaddingException("Invalid pad value!");
            }
            // sanity check padding bytes
            int padStartIndex = len - padValue;
            for (int i = padStartIndex; i < len; i++) {
                if (paddedData[i] != padValue) {
                    throw new BadPaddingException("Invalid pad bytes!");
                }
            }
            return padValue;
        }
    }

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;

    // name of the key algorithm, e.g. DES instead of algorithm DES/CBC/...
    private final String keyAlgorithm;

    // mechanism id
    private final long mechanism;

    // associated session, if any
    private Session session;

    // key, if init() was called
    private P11Key p11Key;

    // flag indicating whether an operation is initialized
    private boolean initialized;

    // falg indicating encrypt or decrypt mode
    private boolean encrypt;

    // mode, one of MODE_* above (MODE_ECB for stream ciphers)
    private int blockMode;

    // block size, 0 for stream ciphers
    private final int blockSize;

    // padding type, on of PAD_* above (PAD_NONE for stream ciphers)
    private int paddingType;

    // when the padding is requested but unsupported by the native mechanism,
    // we use the following to do padding and necessary data buffering.
    // padding object which generate padding and unpad the decrypted data
    private Padding paddingObj;
    // buffer for holding back the block which contains padding bytes
    private byte[] padBuffer;
    private int padBufferLen;

    // original IV, if in MODE_CBC or MODE_CTR
    private byte[] iv;

    // number of bytes buffered internally by the native mechanism and padBuffer
    // if we do the padding
    private int bytesBuffered;

    // length of key size in bytes; currently only used by AES given its oid
    // specification mandates a fixed size of the key
    private int fixedKeySize = -1;

    P11Cipher(Token token, String algorithm, long mechanism)
            throws PKCS11Exception, NoSuchAlgorithmException {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.mechanism = mechanism;

        String algoParts[] = algorithm.split("/");

        if (algoParts[0].startsWith("AES")) {
            blockSize = 16;
            int index = algoParts[0].indexOf('_');
            if (index != -1) {
                // should be well-formed since we specify what we support
                fixedKeySize = Integer.parseInt(algoParts[0].substring(index+1))/8;
            }
            keyAlgorithm = "AES";
        } else {
            keyAlgorithm = algoParts[0];
            if (keyAlgorithm.equals("RC4") ||
                    keyAlgorithm.equals("ARCFOUR")) {
                blockSize = 0;
            } else { // DES, DESede, Blowfish
                blockSize = 8;
            }
        }
        this.blockMode =
            (algoParts.length > 1 ? parseMode(algoParts[1]) : MODE_ECB);
        String defPadding = (blockSize == 0 ? "NoPadding" : "PKCS5Padding");
        String paddingStr =
                (algoParts.length > 2 ? algoParts[2] : defPadding);
        try {
            engineSetPadding(paddingStr);
        } catch (NoSuchPaddingException nspe) {
            // should not happen
            throw new ProviderException(nspe);
        }
    }

    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        // Disallow change of mode for now since currently it's explicitly
        // defined in transformation strings
        throw new NoSuchAlgorithmException("Unsupported mode " + mode);
    }

    private int parseMode(String mode) throws NoSuchAlgorithmException {
        mode = mode.toUpperCase(Locale.ENGLISH);
        int result;
        if (mode.equals("ECB")) {
            result = MODE_ECB;
        } else if (mode.equals("CBC")) {
            if (blockSize == 0) {
                throw new NoSuchAlgorithmException
                        ("CBC mode not supported with stream ciphers");
            }
            result = MODE_CBC;
        } else if (mode.equals("CTR")) {
            result = MODE_CTR;
        } else {
            throw new NoSuchAlgorithmException("Unsupported mode " + mode);
        }
        return result;
    }

    // see JCE spec
    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException {
        paddingObj = null;
        padBuffer = null;
        padding = padding.toUpperCase(Locale.ENGLISH);
        if (padding.equals("NOPADDING")) {
            paddingType = PAD_NONE;
        } else if (padding.equals("PKCS5PADDING")) {
            if (this.blockMode == MODE_CTR) {
                throw new NoSuchPaddingException
                    ("PKCS#5 padding not supported with CTR mode");
            }
            paddingType = PAD_PKCS5;
            if (mechanism != CKM_DES_CBC_PAD && mechanism != CKM_DES3_CBC_PAD &&
                    mechanism != CKM_AES_CBC_PAD) {
                // no native padding support; use our own padding impl
                paddingObj = new PKCS5Padding(blockSize);
                padBuffer = new byte[blockSize];
            }
        } else {
            throw new NoSuchPaddingException("Unsupported padding " + padding);
        }
    }

    // see JCE spec
    protected int engineGetBlockSize() {
        return blockSize;
    }

    // see JCE spec
    protected int engineGetOutputSize(int inputLen) {
        return doFinalLength(inputLen);
    }

    // see JCE spec
    protected byte[] engineGetIV() {
        return (iv == null) ? null : iv.clone();
    }

    // see JCE spec
    protected AlgorithmParameters engineGetParameters() {
        if (iv == null) {
            return null;
        }
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        try {
            AlgorithmParameters params =
                    AlgorithmParameters.getInstance(keyAlgorithm,
                    P11Util.getSunJceProvider());
            params.init(ivSpec);
            return params;
        } catch (GeneralSecurityException e) {
            // NoSuchAlgorithmException, NoSuchProviderException
            // InvalidParameterSpecException
            throw new ProviderException("Could not encode parameters", e);
        }
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        try {
            implInit(opmode, key, null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException("init() failed", e);
        }
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        byte[] ivValue;
        if (params != null) {
            if (params instanceof IvParameterSpec == false) {
                throw new InvalidAlgorithmParameterException
                        ("Only IvParameterSpec supported");
            }
            IvParameterSpec ivSpec = (IvParameterSpec) params;
            ivValue = ivSpec.getIV();
        } else {
            ivValue = null;
        }
        implInit(opmode, key, ivValue, random);
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        byte[] ivValue;
        if (params != null) {
            try {
                IvParameterSpec ivSpec =
                        params.getParameterSpec(IvParameterSpec.class);
                ivValue = ivSpec.getIV();
            } catch (InvalidParameterSpecException e) {
                throw new InvalidAlgorithmParameterException
                        ("Could not decode IV", e);
            }
        } else {
            ivValue = null;
        }
        implInit(opmode, key, ivValue, random);
    }

    // actual init() implementation
    private void implInit(int opmode, Key key, byte[] iv,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        cancelOperation();
        if (fixedKeySize != -1 && key.getEncoded().length != fixedKeySize) {
            throw new InvalidKeyException("Key size is invalid");
        }
        switch (opmode) {
            case Cipher.ENCRYPT_MODE:
                encrypt = true;
                break;
            case Cipher.DECRYPT_MODE:
                encrypt = false;
                break;
            default:
                throw new InvalidAlgorithmParameterException
                        ("Unsupported mode: " + opmode);
        }
        if (blockMode == MODE_ECB) { // ECB or stream cipher
            if (iv != null) {
                if (blockSize == 0) {
                    throw new InvalidAlgorithmParameterException
                            ("IV not used with stream ciphers");
                } else {
                    throw new InvalidAlgorithmParameterException
                            ("IV not used in ECB mode");
                }
            }
        } else { // MODE_CBC or MODE_CTR
            if (iv == null) {
                if (encrypt == false) {
                    String exMsg =
                        (blockMode == MODE_CBC ?
                         "IV must be specified for decryption in CBC mode" :
                         "IV must be specified for decryption in CTR mode");
                    throw new InvalidAlgorithmParameterException(exMsg);
                }
                // generate random IV
                if (random == null) {
                    random = new SecureRandom();
                }
                iv = new byte[blockSize];
                random.nextBytes(iv);
            } else {
                if (iv.length != blockSize) {
                    throw new InvalidAlgorithmParameterException
                            ("IV length must match block size");
                }
            }
        }
        this.iv = iv;
        p11Key = P11SecretKeyFactory.convertKey(token, key, keyAlgorithm);
        try {
            initialize();
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("Could not initialize cipher", e);
        }
    }

    private void cancelOperation() {
        if (initialized == false) {
            return;
        }
        initialized = false;
        if ((session == null) || (token.explicitCancel == false)) {
            return;
        }
        // cancel operation by finishing it
        int bufLen = doFinalLength(0);
        byte[] buffer = new byte[bufLen];
        try {
            if (encrypt) {
                token.p11.C_EncryptFinal(session.id(), 0, buffer, 0, bufLen);
            } else {
                token.p11.C_DecryptFinal(session.id(), 0, buffer, 0, bufLen);
            }
        } catch (PKCS11Exception e) {
            throw new ProviderException("Cancel failed", e);
        } finally {
            reset();
        }
    }

    private void ensureInitialized() throws PKCS11Exception {
        if (initialized == false) {
            initialize();
        }
    }

    private void initialize() throws PKCS11Exception {
        if (session == null) {
            session = token.getOpSession();
        }
        CK_MECHANISM mechParams = (blockMode == MODE_CTR?
            new CK_MECHANISM(mechanism, new CK_AES_CTR_PARAMS(iv)) :
            new CK_MECHANISM(mechanism, iv));

        try {
            if (encrypt) {
                token.p11.C_EncryptInit(session.id(), mechParams, p11Key.keyID);
            } else {
                token.p11.C_DecryptInit(session.id(), mechParams, p11Key.keyID);
            }
        } catch (PKCS11Exception ex) {
            // release session when initialization failed
            session = token.releaseSession(session);
            throw ex;
        }
        bytesBuffered = 0;
        padBufferLen = 0;
        initialized = true;
    }

    // if update(inLen) is called, how big does the output buffer have to be?
    private int updateLength(int inLen) {
        if (inLen <= 0) {
            return 0;
        }

        int result = inLen + bytesBuffered;
        if (blockSize != 0 && blockMode != MODE_CTR) {
            // minus the number of bytes in the last incomplete block.
            result -= (result & (blockSize - 1));
        }
        return result;
    }

    // if doFinal(inLen) is called, how big does the output buffer have to be?
    private int doFinalLength(int inLen) {
        if (inLen < 0) {
            return 0;
        }

        int result = inLen + bytesBuffered;
        if (blockSize != 0 && encrypt && paddingType != PAD_NONE) {
            // add the number of bytes to make the last block complete.
            result += (blockSize - (result & (blockSize - 1)));
        }
        return result;
    }

    // reset the states to the pre-initialized values
    private void reset() {
        initialized = false;
        bytesBuffered = 0;
        padBufferLen = 0;
        if (session != null) {
            session = token.releaseSession(session);
        }
    }

    // see JCE spec
    protected byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        try {
            byte[] out = new byte[updateLength(inLen)];
            int n = engineUpdate(in, inOfs, inLen, out, 0);
            return P11Util.convert(out, 0, n);
        } catch (ShortBufferException e) {
            // convert since the output length is calculated by updateLength()
            throw new ProviderException(e);
        }
    }

    // see JCE spec
    protected int engineUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
        int outLen = out.length - outOfs;
        return implUpdate(in, inOfs, inLen, out, outOfs, outLen);
    }

    // see JCE spec
    @Override
    protected int engineUpdate(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException {
        return implUpdate(inBuffer, outBuffer);
    }

    // see JCE spec
    protected byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
        try {
            byte[] out = new byte[doFinalLength(inLen)];
            int n = engineDoFinal(in, inOfs, inLen, out, 0);
            return P11Util.convert(out, 0, n);
        } catch (ShortBufferException e) {
            // convert since the output length is calculated by doFinalLength()
            throw new ProviderException(e);
        }
    }

    // see JCE spec
    protected int engineDoFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int n = 0;
        if ((inLen != 0) && (in != null)) {
            n = engineUpdate(in, inOfs, inLen, out, outOfs);
            outOfs += n;
        }
        n += implDoFinal(out, outOfs, out.length - outOfs);
        return n;
    }

    // see JCE spec
    @Override
    protected int engineDoFinal(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int n = engineUpdate(inBuffer, outBuffer);
        n += implDoFinal(outBuffer);
        return n;
    }

    private int implUpdate(byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs, int outLen) throws ShortBufferException {
        if (outLen < updateLength(inLen)) {
            throw new ShortBufferException();
        }
        try {
            ensureInitialized();
            int k = 0;
            if (encrypt) {
                k = token.p11.C_EncryptUpdate(session.id(), 0, in, inOfs, inLen,
                        0, out, outOfs, outLen);
            } else {
                int newPadBufferLen = 0;
                if (paddingObj != null) {
                    if (padBufferLen != 0) {
                        // NSS throws up when called with data not in multiple
                        // of blocks. Try to work around this by holding the
                        // extra data in padBuffer.
                        if (padBufferLen != padBuffer.length) {
                            int bufCapacity = padBuffer.length - padBufferLen;
                            if (inLen > bufCapacity) {
                                bufferInputBytes(in, inOfs, bufCapacity);
                                inOfs += bufCapacity;
                                inLen -= bufCapacity;
                            } else {
                                bufferInputBytes(in, inOfs, inLen);
                                return 0;
                            }
                        }
                        k = token.p11.C_DecryptUpdate(session.id(),
                                0, padBuffer, 0, padBufferLen,
                                0, out, outOfs, outLen);
                        padBufferLen = 0;
                    }
                    newPadBufferLen = inLen & (blockSize - 1);
                    if (newPadBufferLen == 0) {
                        newPadBufferLen = padBuffer.length;
                    }
                    inLen -= newPadBufferLen;
                }
                if (inLen > 0) {
                    k += token.p11.C_DecryptUpdate(session.id(), 0, in, inOfs,
                            inLen, 0, out, (outOfs + k), (outLen - k));
                }
                // update 'padBuffer' if using our own padding impl.
                if (paddingObj != null) {
                    bufferInputBytes(in, inOfs + inLen, newPadBufferLen);
                }
            }
            bytesBuffered += (inLen - k);
            return k;
        } catch (PKCS11Exception e) {
            if (e.getErrorCode() == CKR_BUFFER_TOO_SMALL) {
                throw (ShortBufferException)
                        (new ShortBufferException().initCause(e));
            }
            reset();
            throw new ProviderException("update() failed", e);
        }
    }

    private int implUpdate(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException {
        int inLen = inBuffer.remaining();
        if (inLen <= 0) {
            return 0;
        }

        int outLen = outBuffer.remaining();
        if (outLen < updateLength(inLen)) {
            throw new ShortBufferException();
        }
        int origPos = inBuffer.position();
        try {
            ensureInitialized();

            long inAddr = 0;
            int inOfs = 0;
            byte[] inArray = null;

            if (inBuffer instanceof DirectBuffer) {
                inAddr = ((DirectBuffer) inBuffer).address();
                inOfs = origPos;
            } else if (inBuffer.hasArray()) {
                inArray = inBuffer.array();
                inOfs = (origPos + inBuffer.arrayOffset());
            }

            long outAddr = 0;
            int outOfs = 0;
            byte[] outArray = null;
            if (outBuffer instanceof DirectBuffer) {
                outAddr = ((DirectBuffer) outBuffer).address();
                outOfs = outBuffer.position();
            } else {
                if (outBuffer.hasArray()) {
                    outArray = outBuffer.array();
                    outOfs = (outBuffer.position() + outBuffer.arrayOffset());
                } else {
                    outArray = new byte[outLen];
                }
            }

            int k = 0;
            if (encrypt) {
                if (inAddr == 0 && inArray == null) {
                    inArray = new byte[inLen];
                    inBuffer.get(inArray);
                } else {
                    inBuffer.position(origPos + inLen);
                }
                k = token.p11.C_EncryptUpdate(session.id(),
                        inAddr, inArray, inOfs, inLen,
                        outAddr, outArray, outOfs, outLen);
            } else {
                int newPadBufferLen = 0;
                if (paddingObj != null) {
                    if (padBufferLen != 0) {
                        // NSS throws up when called with data not in multiple
                        // of blocks. Try to work around this by holding the
                        // extra data in padBuffer.
                        if (padBufferLen != padBuffer.length) {
                            int bufCapacity = padBuffer.length - padBufferLen;
                            if (inLen > bufCapacity) {
                                bufferInputBytes(inBuffer, bufCapacity);
                                inOfs += bufCapacity;
                                inLen -= bufCapacity;
                            } else {
                                bufferInputBytes(inBuffer, inLen);
                                return 0;
                            }
                        }
                        k = token.p11.C_DecryptUpdate(session.id(), 0,
                                padBuffer, 0, padBufferLen, outAddr, outArray,
                                outOfs, outLen);
                        padBufferLen = 0;
                    }
                    newPadBufferLen = inLen & (blockSize - 1);
                    if (newPadBufferLen == 0) {
                        newPadBufferLen = padBuffer.length;
                    }
                    inLen -= newPadBufferLen;
                }
                if (inLen > 0) {
                    if (inAddr == 0 && inArray == null) {
                        inArray = new byte[inLen];
                        inBuffer.get(inArray);
                    } else {
                        inBuffer.position(inBuffer.position() + inLen);
                    }
                    k += token.p11.C_DecryptUpdate(session.id(), inAddr,
                            inArray, inOfs, inLen, outAddr, outArray,
                            (outOfs + k), (outLen - k));
                }
                // update 'padBuffer' if using our own padding impl.
                if (paddingObj != null && newPadBufferLen != 0) {
                    bufferInputBytes(inBuffer, newPadBufferLen);
                }
            }
            bytesBuffered += (inLen - k);
            if (!(outBuffer instanceof DirectBuffer) &&
                    !outBuffer.hasArray()) {
                outBuffer.put(outArray, outOfs, k);
            } else {
                outBuffer.position(outBuffer.position() + k);
            }
            return k;
        } catch (PKCS11Exception e) {
            // Reset input buffer to its original position for
            inBuffer.position(origPos);
            if (e.getErrorCode() == CKR_BUFFER_TOO_SMALL) {
                throw (ShortBufferException)
                        (new ShortBufferException().initCause(e));
            }
            reset();
            throw new ProviderException("update() failed", e);
        }
    }

    private int implDoFinal(byte[] out, int outOfs, int outLen)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int requiredOutLen = doFinalLength(0);
        if (outLen < requiredOutLen) {
            throw new ShortBufferException();
        }
        try {
            ensureInitialized();
            int k = 0;
            if (encrypt) {
                if (paddingObj != null) {
                    int actualPadLen = paddingObj.setPaddingBytes(padBuffer,
                            requiredOutLen - bytesBuffered);
                    k = token.p11.C_EncryptUpdate(session.id(),
                            0, padBuffer, 0, actualPadLen,
                            0, out, outOfs, outLen);
                }
                k += token.p11.C_EncryptFinal(session.id(),
                        0, out, (outOfs + k), (outLen - k));
            } else {
                if (paddingObj != null) {
                    if (padBufferLen != 0) {
                        k = token.p11.C_DecryptUpdate(session.id(), 0,
                                padBuffer, 0, padBufferLen, 0, padBuffer, 0,
                                padBuffer.length);
                    }
                    k += token.p11.C_DecryptFinal(session.id(), 0, padBuffer, k,
                            padBuffer.length - k);
                    int actualPadLen = paddingObj.unpad(padBuffer, k);
                    k -= actualPadLen;
                    System.arraycopy(padBuffer, 0, out, outOfs, k);
                } else {
                    k = token.p11.C_DecryptFinal(session.id(), 0, out, outOfs,
                            outLen);
                }
            }
            return k;
        } catch (PKCS11Exception e) {
            handleException(e);
            throw new ProviderException("doFinal() failed", e);
        } finally {
            reset();
        }
    }

    private int implDoFinal(ByteBuffer outBuffer)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int outLen = outBuffer.remaining();
        int requiredOutLen = doFinalLength(0);
        if (outLen < requiredOutLen) {
            throw new ShortBufferException();
        }

        try {
            ensureInitialized();

            long outAddr = 0;
            byte[] outArray = null;
            int outOfs = 0;
            if (outBuffer instanceof DirectBuffer) {
                outAddr = ((DirectBuffer) outBuffer).address();
                outOfs = outBuffer.position();
            } else {
                if (outBuffer.hasArray()) {
                    outArray = outBuffer.array();
                    outOfs = outBuffer.position() + outBuffer.arrayOffset();
                } else {
                    outArray = new byte[outLen];
                }
            }

            int k = 0;

            if (encrypt) {
                if (paddingObj != null) {
                    int actualPadLen = paddingObj.setPaddingBytes(padBuffer,
                            requiredOutLen - bytesBuffered);
                    k = token.p11.C_EncryptUpdate(session.id(),
                            0, padBuffer, 0, actualPadLen,
                            outAddr, outArray, outOfs, outLen);
                }
                k += token.p11.C_EncryptFinal(session.id(),
                        outAddr, outArray, (outOfs + k), (outLen - k));
            } else {
                if (paddingObj != null) {
                    if (padBufferLen != 0) {
                        k = token.p11.C_DecryptUpdate(session.id(),
                                0, padBuffer, 0, padBufferLen,
                                0, padBuffer, 0, padBuffer.length);
                        padBufferLen = 0;
                    }
                    k += token.p11.C_DecryptFinal(session.id(),
                            0, padBuffer, k, padBuffer.length - k);
                    int actualPadLen = paddingObj.unpad(padBuffer, k);
                    k -= actualPadLen;
                    outArray = padBuffer;
                    outOfs = 0;
                } else {
                    k = token.p11.C_DecryptFinal(session.id(),
                            outAddr, outArray, outOfs, outLen);
                }
            }
            if ((!encrypt && paddingObj != null) ||
                    (!(outBuffer instanceof DirectBuffer) &&
                    !outBuffer.hasArray())) {
                outBuffer.put(outArray, outOfs, k);
            } else {
                outBuffer.position(outBuffer.position() + k);
            }
            return k;
        } catch (PKCS11Exception e) {
            handleException(e);
            throw new ProviderException("doFinal() failed", e);
        } finally {
            reset();
        }
    }

    private void handleException(PKCS11Exception e)
            throws ShortBufferException, IllegalBlockSizeException {
        long errorCode = e.getErrorCode();
        if (errorCode == CKR_BUFFER_TOO_SMALL) {
            throw (ShortBufferException)
                    (new ShortBufferException().initCause(e));
        } else if (errorCode == CKR_DATA_LEN_RANGE ||
                   errorCode == CKR_ENCRYPTED_DATA_LEN_RANGE) {
            throw (IllegalBlockSizeException)
                    (new IllegalBlockSizeException(e.toString()).initCause(e));
        }
    }

    // see JCE spec
    protected byte[] engineWrap(Key key) throws IllegalBlockSizeException,
            InvalidKeyException {
        // XXX key wrapping
        throw new UnsupportedOperationException("engineWrap()");
    }

    // see JCE spec
    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
            int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {
        // XXX key unwrapping
        throw new UnsupportedOperationException("engineUnwrap()");
    }

    // see JCE spec
    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        int n = P11SecretKeyFactory.convertKey
                (token, key, keyAlgorithm).length();
        return n;
    }

    private final void bufferInputBytes(byte[] in, int inOfs, int len) {
        System.arraycopy(in, inOfs, padBuffer, padBufferLen, len);
        padBufferLen += len;
        bytesBuffered += len;
    }

    private final void bufferInputBytes(ByteBuffer inBuffer, int len) {
        inBuffer.get(padBuffer, padBufferLen, len);
        padBufferLen += len;
        bytesBuffered += len;
    }
}
