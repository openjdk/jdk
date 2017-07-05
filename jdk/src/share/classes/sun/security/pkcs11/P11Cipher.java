/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.pkcs11;

import java.nio.ByteBuffer;

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
 * This class is designed to support ECB and CBC with NoPadding and
 * PKCS5Padding for both. However, currently only CBC/NoPadding (and
 * ECB/NoPadding for stream ciphers) is functional.
 *
 * Note that PKCS#11 current only supports ECB and CBC. There are no
 * provisions for other modes such as CFB, OFB, PCBC, or CTR mode.
 * However, CTR could be implemented relatively easily (and efficiently)
 * on top of ECB mode in this class, if need be.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11Cipher extends CipherSpi {

    // mode constant for ECB mode
    private final static int MODE_ECB = 3;
    // mode constant for CBC mode
    private final static int MODE_CBC = 4;

    // padding constant for NoPadding
    private final static int PAD_NONE  = 5;
    // padding constant for PKCS5Padding
    private final static int PAD_PKCS5 = 6;

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

    // original IV, if in MODE_CBC
    private byte[] iv;

    // total number of bytes processed
    private int bytesProcessed;

    P11Cipher(Token token, String algorithm, long mechanism)
            throws PKCS11Exception {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.mechanism = mechanism;
        keyAlgorithm = algorithm.split("/")[0];
        if (keyAlgorithm.equals("AES")) {
            blockSize = 16;
            blockMode = MODE_CBC;
            // XXX change default to PKCS5Padding
            paddingType = PAD_NONE;
        } else if (keyAlgorithm.equals("RC4") || keyAlgorithm.equals("ARCFOUR")) {
            blockSize = 0;
            blockMode = MODE_ECB;
            paddingType = PAD_NONE;
        } else { // DES, DESede, Blowfish
            blockSize = 8;
            blockMode = MODE_CBC;
            // XXX change default to PKCS5Padding
            paddingType = PAD_NONE;
        }
        session = token.getOpSession();
    }

    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        mode = mode.toUpperCase();
        if (mode.equals("ECB")) {
            this.blockMode = MODE_ECB;
        } else if (mode.equals("CBC")) {
            if (blockSize == 0) {
                throw new NoSuchAlgorithmException
                        ("CBC mode not supported with stream ciphers");
            }
            this.blockMode = MODE_CBC;
        } else {
            throw new NoSuchAlgorithmException("Unsupported mode " + mode);
        }
    }

    // see JCE spec
    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException {
        if (padding.equalsIgnoreCase("NoPadding")) {
            paddingType = PAD_NONE;
        } else if (padding.equalsIgnoreCase("PKCS5Padding")) {
            if (blockSize == 0) {
                throw new NoSuchPaddingException
                        ("PKCS#5 padding not supported with stream ciphers");
            }
            paddingType = PAD_PKCS5;
            // XXX PKCS#5 not yet implemented
            throw new NoSuchPaddingException("pkcs5");
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
        return (iv == null) ? null : (byte[])iv.clone();
    }

    // see JCE spec
    protected AlgorithmParameters engineGetParameters() {
        if (iv == null) {
            return null;
        }
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance
                (keyAlgorithm, P11Util.getSunJceProvider());
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
        byte[] iv;
        if (params != null) {
            if (params instanceof IvParameterSpec == false) {
                throw new InvalidAlgorithmParameterException
                        ("Only IvParameterSpec supported");
            }
            IvParameterSpec ivSpec = (IvParameterSpec)params;
            iv = ivSpec.getIV();
        } else {
            iv = null;
        }
        implInit(opmode, key, iv, random);
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        byte[] iv;
        if (params != null) {
            try {
                IvParameterSpec ivSpec = (IvParameterSpec)
                        params.getParameterSpec(IvParameterSpec.class);
                iv = ivSpec.getIV();
            } catch (InvalidParameterSpecException e) {
                throw new InvalidAlgorithmParameterException
                        ("Could not decode IV", e);
            }
        } else {
            iv = null;
        }
        implInit(opmode, key, iv, random);
    }

    // actual init() implementation
    private void implInit(int opmode, Key key, byte[] iv,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        cancelOperation();
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
        } else { // MODE_CBC
            if (iv == null) {
                if (encrypt == false) {
                    throw new InvalidAlgorithmParameterException
                        ("IV must be specified for decryption in CBC mode");
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
        if (encrypt) {
            token.p11.C_EncryptInit
                (session.id(), new CK_MECHANISM(mechanism, iv), p11Key.keyID);
        } else {
            token.p11.C_DecryptInit
                (session.id(), new CK_MECHANISM(mechanism, iv), p11Key.keyID);
        }
        bytesProcessed = 0;
        initialized = true;
    }

    // XXX the calculations below assume the PKCS#11 implementation is smart.
    // conceivably, not all implementations are and we may need to estimate
    // more conservatively

    private int bytesBuffered(int totalLen) {
        if (paddingType == PAD_NONE) {
            // with NoPadding, buffer only the current unfinished block
            return totalLen & (blockSize - 1);
        } else { // PKCS5
            // with PKCS5Padding in decrypt mode, the buffer must never
            // be empty. Buffer a full block instead of nothing.
            int buffered = totalLen & (blockSize - 1);
            if ((buffered == 0) && (encrypt == false)) {
                buffered = blockSize;
            }
            return buffered;
        }
    }

    // if update(inLen) is called, how big does the output buffer have to be?
    private int updateLength(int inLen) {
        if (inLen <= 0) {
            return 0;
        }
        if (blockSize == 0) {
            return inLen;
        } else {
            // bytes that need to be buffered now
            int buffered = bytesBuffered(bytesProcessed);
            // bytes that need to be buffered after this update
            int newBuffered = bytesBuffered(bytesProcessed + inLen);
            return inLen + buffered - newBuffered;
        }
    }

    // if doFinal(inLen) is called, how big does the output buffer have to be?
    private int doFinalLength(int inLen) {
        if (paddingType == PAD_NONE) {
            return updateLength(inLen);
        }
        if (inLen < 0) {
            return 0;
        }
        int buffered = bytesBuffered(bytesProcessed);
        int newProcessed = bytesProcessed + inLen;
        int paddedProcessed = (newProcessed + blockSize) & ~(blockSize - 1);
        return paddedProcessed - bytesProcessed + buffered;
    }

    // see JCE spec
    protected byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        try {
            byte[] out = new byte[updateLength(inLen)];
            int n = engineUpdate(in, inOfs, inLen, out, 0);
            return P11Util.convert(out, 0, n);
        } catch (ShortBufferException e) {
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
            throw new ProviderException(e);
        }
    }

    // see JCE spec
    protected int engineDoFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException, IllegalBlockSizeException {
            // BadPaddingException {
        int n = 0;
        if ((inLen != 0) && (in != null)) {
            n = engineUpdate(in, inOfs, inLen, out, outOfs);
            outOfs += n;
        }
        n += implDoFinal(out, outOfs, out.length - outOfs);
        return n;
    }

    // see JCE spec
    protected int engineDoFinal(ByteBuffer inBuffer, ByteBuffer outBuffer)
    throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
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
            int k;
            if (encrypt) {
                k = token.p11.C_EncryptUpdate
                (session.id(), 0, in, inOfs, inLen, 0, out, outOfs, outLen);
            } else {
                k = token.p11.C_DecryptUpdate
                (session.id(), 0, in, inOfs, inLen, 0, out, outOfs, outLen);
            }
            bytesProcessed += inLen;
            return k;
        } catch (PKCS11Exception e) {
            // XXX throw correct exception
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
        boolean inPosChanged = false;
        try {
            ensureInitialized();

            long inAddr = 0;
            int inOfs = inBuffer.position();
            byte[] inArray = null;
            if (inBuffer instanceof DirectBuffer) {
                inAddr = ((DirectBuffer)inBuffer).address();
            } else {
                if (inBuffer.hasArray()) {
                    inArray = inBuffer.array();
                    inOfs += inBuffer.arrayOffset();
                } else {
                    inArray = new byte[inLen];
                    inBuffer.get(inArray);
                    inOfs = 0;
                    inPosChanged = true;
                }
            }

            long outAddr = 0;
            int outOfs = outBuffer.position();
            byte[] outArray = null;
            if (outBuffer instanceof DirectBuffer) {
                outAddr = ((DirectBuffer)outBuffer).address();
            } else {
                if (outBuffer.hasArray()) {
                    outArray = outBuffer.array();
                    outOfs += outBuffer.arrayOffset();
                } else {
                    outArray = new byte[outLen];
                    outOfs = 0;
                }
            }

            int k;
            if (encrypt) {
                k = token.p11.C_EncryptUpdate
                    (session.id(), inAddr, inArray, inOfs, inLen,
                     outAddr, outArray, outOfs, outLen);
            } else {
                k = token.p11.C_DecryptUpdate
                    (session.id(), inAddr, inArray, inOfs, inLen,
                     outAddr, outArray, outOfs, outLen);
            }
            bytesProcessed += inLen;
            if (!inPosChanged) {
                inBuffer.position(inBuffer.position() + inLen);
            }
            if (!(outBuffer instanceof DirectBuffer) &&
                !outBuffer.hasArray()) {
                outBuffer.put(outArray, outOfs, k);
            } else {
                outBuffer.position(outBuffer.position() + k);
            }
            return k;
        } catch (PKCS11Exception e) {
            // Un-read the bytes back to input buffer
            if (inPosChanged) {
                inBuffer.position(inBuffer.position() - inLen);
            }
            // XXX throw correct exception
            throw new ProviderException("update() failed", e);
        }
    }

    private int implDoFinal(byte[] out, int outOfs, int outLen)
            throws ShortBufferException, IllegalBlockSizeException {
        if (outLen < doFinalLength(0)) {
            throw new ShortBufferException();
        }
        try {
            ensureInitialized();
            if (encrypt) {
                return token.p11.C_EncryptFinal
                                (session.id(), 0, out, outOfs, outLen);
            } else {
                return token.p11.C_DecryptFinal
                                (session.id(), 0, out, outOfs, outLen);
            }
        } catch (PKCS11Exception e) {
            handleException(e);
            throw new ProviderException("doFinal() failed", e);
        } finally {
            initialized = false;
            bytesProcessed = 0;
            session = token.releaseSession(session);
        }
    }

    private int implDoFinal(ByteBuffer outBuffer)
            throws ShortBufferException, IllegalBlockSizeException {
        int outLen = outBuffer.remaining();
        if (outLen < doFinalLength(0)) {
            throw new ShortBufferException();
        }

        try {
            ensureInitialized();

            long outAddr = 0;
            int outOfs = outBuffer.position();
            byte[] outArray = null;
            if (outBuffer instanceof DirectBuffer) {
                outAddr = ((DirectBuffer)outBuffer).address();
            } else {
                if (outBuffer.hasArray()) {
                    outArray = outBuffer.array();
                    outOfs += outBuffer.arrayOffset();
                } else {
                    outArray = new byte[outLen];
                    outOfs = 0;
                }
            }

            int k;
            if (encrypt) {
                k = token.p11.C_EncryptFinal
                    (session.id(), outAddr, outArray, outOfs, outLen);
            } else {
                k = token.p11.C_DecryptFinal
                    (session.id(), outAddr, outArray, outOfs, outLen);
            }
            if (!(outBuffer instanceof DirectBuffer) &&
                !outBuffer.hasArray()) {
                outBuffer.put(outArray, outOfs, k);
            } else {
                outBuffer.position(outBuffer.position() + k);
            }
            return k;
        } catch (PKCS11Exception e) {
            handleException(e);
            throw new ProviderException("doFinal() failed", e);
        } finally {
            initialized = false;
            bytesProcessed = 0;
            session = token.releaseSession(session);
        }
    }

    private void handleException(PKCS11Exception e)
            throws IllegalBlockSizeException {
        long errorCode = e.getErrorCode();
        // XXX better check
        if (errorCode == CKR_DATA_LEN_RANGE) {
            throw (IllegalBlockSizeException)new
                IllegalBlockSizeException(e.toString()).initCause(e);
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
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        int n = P11SecretKeyFactory.convertKey
                                (token, key, keyAlgorithm).keyLength();
        return n;
    }

    protected void finalize() throws Throwable {
        try {
            if ((session != null) && token.isValid()) {
                cancelOperation();
                session = token.releaseSession(session);
            }
        } finally {
            super.finalize();
        }
    }

}
