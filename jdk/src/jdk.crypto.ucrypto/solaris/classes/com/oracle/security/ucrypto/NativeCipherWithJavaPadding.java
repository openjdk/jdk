/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListSet;
import java.lang.ref.*;

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;


import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

import javax.crypto.spec.IvParameterSpec;

/**
 * Wrapper class which uses NativeCipher class and Java impls of padding scheme.
 * This class currently supports
 * - AES/ECB/PKCS5PADDING
 * - AES/CBC/PKCS5PADDING
 * - AES/CFB128/PKCS5PADDING
 *
 * @since 1.9
 */
public class NativeCipherWithJavaPadding extends CipherSpi {

    private static interface Padding {
        // ENC: generate and return the necessary padding bytes
        int getPadLen(int dataLen);

        // ENC: generate and return the necessary padding bytes
        byte[] getPaddingBytes(int dataLen);

        // DEC: process the decrypted data and buffer up the potential padding
        // bytes
        byte[] bufferBytes(byte[] intermediateData);

        // DEC: return the length of internally buffered pad bytes
        int getBufferedLength();

        // DEC: unpad and place the output in 'out', starting from outOfs
        // and return the number of bytes unpadded into 'out'.
        int unpad(byte[] paddedData, byte[] out, int outOfs)
                throws BadPaddingException, IllegalBlockSizeException,
                ShortBufferException;

        // DEC: Clears the padding object to the initial state
        void clear();
    }

    private static class PKCS5Padding implements Padding {
        private final int blockSize;
        // buffer for storing the potential padding bytes
        private ByteBuffer trailingBytes = null;

        PKCS5Padding(int blockSize)
            throws NoSuchPaddingException {
            if (blockSize == 0) {
                throw new NoSuchPaddingException
                        ("PKCS#5 padding not supported with stream ciphers");
            }
            this.blockSize = blockSize;
        }

        public int getPadLen(int dataLen) {
            return (blockSize - (dataLen & (blockSize - 1)));
        }

        public byte[] getPaddingBytes(int dataLen) {
            byte padValue = (byte) getPadLen(dataLen);
            byte[] paddingBytes = new byte[padValue];
            Arrays.fill(paddingBytes, padValue);
            return paddingBytes;
        }

        public byte[] bufferBytes(byte[] dataFromUpdate) {
            if (dataFromUpdate == null || dataFromUpdate.length == 0) {
                return null;
            }
            byte[] result = null;
            if (trailingBytes == null) {
                trailingBytes = ByteBuffer.wrap(new byte[blockSize]);
            }
            int tbSize = trailingBytes.position();
            if (dataFromUpdate.length > trailingBytes.remaining()) {
                int totalLen = dataFromUpdate.length + tbSize;
                int newTBSize = totalLen % blockSize;
                if (newTBSize == 0) {
                    newTBSize = blockSize;
                }
                if (tbSize == 0) {
                    result = Arrays.copyOf(dataFromUpdate, totalLen - newTBSize);
                } else {
                    // combine 'trailingBytes' and 'dataFromUpdate'
                    result = Arrays.copyOf(trailingBytes.array(),
                                           totalLen - newTBSize);
                    if (result.length != tbSize) {
                        System.arraycopy(dataFromUpdate, 0, result, tbSize,
                                         result.length - tbSize);
                    }
                }
                // update 'trailingBytes' w/ remaining bytes in 'dataFromUpdate'
                trailingBytes.clear();
                trailingBytes.put(dataFromUpdate,
                                  dataFromUpdate.length - newTBSize, newTBSize);
            } else {
                trailingBytes.put(dataFromUpdate);
            }
            return result;
        }

        public int getBufferedLength() {
            if (trailingBytes != null) {
                return trailingBytes.position();
            }
            return 0;
        }

        public int unpad(byte[] lastData, byte[] out, int outOfs)
                throws BadPaddingException, IllegalBlockSizeException,
                ShortBufferException {
            int tbSize = (trailingBytes == null? 0:trailingBytes.position());
            int dataLen = tbSize + lastData.length;
            // check total length
            if ((dataLen < 1) || (dataLen % blockSize != 0)) {
                UcryptoProvider.debug("PKCS5Padding: unpad, buffered " + tbSize +
                                 " bytes, last block " + lastData.length + " bytes");

                throw new IllegalBlockSizeException
                    ("Input length must be multiples of " + blockSize);
            }

            // check padding bytes
            if (lastData.length == 0) {
                if (tbSize != 0) {
                    // work on 'trailingBytes' directly
                    lastData = Arrays.copyOf(trailingBytes.array(), tbSize);
                    trailingBytes.clear();
                    tbSize = 0;
                } else {
                    throw new BadPaddingException("No pad bytes found!");
                }
            }
            byte padValue = lastData[lastData.length - 1];
            if (padValue < 1 || padValue > blockSize) {
                UcryptoProvider.debug("PKCS5Padding: unpad, lastData: " + Arrays.toString(lastData));
                UcryptoProvider.debug("PKCS5Padding: unpad, padValue=" + padValue);
                throw new BadPaddingException("Invalid pad value!");
            }

            // sanity check padding bytes
            int padStartIndex = lastData.length - padValue;
            for (int i = padStartIndex; i < lastData.length; i++) {
                if (lastData[i] != padValue) {
                    UcryptoProvider.debug("PKCS5Padding: unpad, lastData: " + Arrays.toString(lastData));
                    UcryptoProvider.debug("PKCS5Padding: unpad, padValue=" + padValue);
                    throw new BadPaddingException("Invalid padding bytes!");
                }
            }

            int actualOutLen = dataLen - padValue;
            // check output buffer capacity
            if (out.length - outOfs < actualOutLen) {
                throw new ShortBufferException("Output buffer too small, need " + actualOutLen +
                    ", got " + (out.length - outOfs));
            }
            try {
                if (tbSize != 0) {
                    trailingBytes.rewind();
                    if (tbSize < actualOutLen) {
                        trailingBytes.get(out, outOfs, tbSize);
                        outOfs += tbSize;
                    } else {
                        // copy from trailingBytes and we are done
                        trailingBytes.get(out, outOfs, actualOutLen);
                        return actualOutLen;
                    }
                }
                if (lastData.length > padValue) {
                    System.arraycopy(lastData, 0, out, outOfs,
                                     lastData.length - padValue);
                }
                return actualOutLen;
            } finally {
                clear();
            }
        }

        public void clear() {
            if (trailingBytes != null) trailingBytes.clear();
        }
    }

    public static final class AesEcbPKCS5 extends NativeCipherWithJavaPadding {
        public AesEcbPKCS5() throws NoSuchAlgorithmException, NoSuchPaddingException {
            super(new NativeCipher.AesEcbNoPadding(), "PKCS5Padding");
        }
    }

    public static final class AesCbcPKCS5 extends NativeCipherWithJavaPadding {
        public AesCbcPKCS5() throws NoSuchAlgorithmException, NoSuchPaddingException {
            super(new NativeCipher.AesCbcNoPadding(), "PKCS5Padding");
        }
    }

    public static final class AesCfb128PKCS5 extends NativeCipherWithJavaPadding {
        public AesCfb128PKCS5() throws NoSuchAlgorithmException, NoSuchPaddingException {
            super(new NativeCipher.AesCfb128NoPadding(), "PKCS5Padding");
        }
    }

    // fields (re)set in every init()
    private final NativeCipher nc;
    private final Padding padding;
    private final int blockSize;
    private int lastBlockLen = 0;

    // Only ECB, CBC, CTR, and CFB128 modes w/ NOPADDING for now
    NativeCipherWithJavaPadding(NativeCipher nc, String paddingScheme)
        throws NoSuchAlgorithmException, NoSuchPaddingException {
        this.nc = nc;
        this.blockSize = nc.engineGetBlockSize();
        if (paddingScheme.toUpperCase().equals("PKCS5PADDING")) {
            padding = new PKCS5Padding(blockSize);
        } else {
            throw new NoSuchAlgorithmException("Unsupported padding scheme: " + paddingScheme);
        }
    }

    void reset() {
        padding.clear();
        lastBlockLen = 0;
    }

    @Override
    protected synchronized void engineSetMode(String mode) throws NoSuchAlgorithmException {
        nc.engineSetMode(mode);
    }

    // see JCE spec
    @Override
    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException {
        // Disallow change of padding for now since currently it's explicitly
        // defined in transformation strings
        throw new NoSuchPaddingException("Unsupported padding " + padding);
    }

    // see JCE spec
    @Override
    protected int engineGetBlockSize() {
        return blockSize;
    }

    // see JCE spec
    @Override
    protected synchronized int engineGetOutputSize(int inputLen) {
        int result = nc.engineGetOutputSize(inputLen);
        if (nc.encrypt) {
            result += padding.getPadLen(result);
        } else {
            result += padding.getBufferedLength();
        }
        return result;
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineGetIV() {
        return nc.engineGetIV();
    }

    // see JCE spec
    @Override
    protected synchronized AlgorithmParameters engineGetParameters() {
        return nc.engineGetParameters();
    }

    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        return nc.engineGetKeySize(key);
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        reset();
        nc.engineInit(opmode, key, random);
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        reset();
        nc.engineInit(opmode, key, params, random);
    }

    // see JCE spec
    @Override
    protected synchronized void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        reset();
        nc.engineInit(opmode, key, params, random);
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        if (nc.encrypt) {
            lastBlockLen += inLen;
            lastBlockLen &= (blockSize - 1);
            return nc.engineUpdate(in, inOfs, inLen);
        } else {
            return padding.bufferBytes(nc.engineUpdate(in, inOfs, inLen));
        }
    }

    // see JCE spec
    @Override
    protected synchronized int engineUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
        if (nc.encrypt) {
            lastBlockLen += inLen;
            lastBlockLen &= (blockSize - 1);
            return nc.engineUpdate(in, inOfs, inLen, out, outOfs);
        } else {
            byte[] result = padding.bufferBytes(nc.engineUpdate(in, inOfs, inLen));
            if (result != null) {
                System.arraycopy(result, 0, out, outOfs, result.length);
                return result.length;
            } else return 0;
        }
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
        int estimatedOutLen = engineGetOutputSize(inLen);
        byte[] out = new byte[estimatedOutLen];
        try {
            int actualOut = this.engineDoFinal(in, inOfs, inLen, out, 0);
            // truncate off extra bytes
            if (actualOut != out.length) {
                out = Arrays.copyOf(out, actualOut);
            }
        } catch (ShortBufferException sbe) {
            throw new UcryptoException("Internal Error");
        } finally {
            reset();
        }
        return out;
    }

    // see JCE spec
    @Override
    protected synchronized int engineDoFinal(byte[] in, int inOfs, int inLen, byte[] out,
                                             int outOfs)
        throws ShortBufferException, IllegalBlockSizeException,
               BadPaddingException {
        int estimatedOutLen = engineGetOutputSize(inLen);

        if (out.length - outOfs < estimatedOutLen) {
            throw new ShortBufferException();
        }
        try {
            if (nc.encrypt) {
                int k = nc.engineUpdate(in, inOfs, inLen, out, outOfs);
                lastBlockLen += inLen;
                lastBlockLen &= (blockSize - 1);
                byte[] padBytes = padding.getPaddingBytes(lastBlockLen);
                k += nc.engineDoFinal(padBytes, 0, padBytes.length, out, (outOfs + k));
                return k;
            } else {
                byte[] tempOut = nc.engineDoFinal(in, inOfs, inLen);
                int len = padding.unpad(tempOut, out, outOfs);
                return len;
            }
        } finally {
            reset();
        }
    }

    // see JCE spec
    @Override
    protected synchronized byte[] engineWrap(Key key) throws IllegalBlockSizeException,
                                                InvalidKeyException {
        byte[] result = null;
        try {
            byte[] encodedKey = key.getEncoded();
            if ((encodedKey == null) || (encodedKey.length == 0)) {
                throw new InvalidKeyException("Cannot get an encoding of " +
                                              "the key to be wrapped");
            }
            result = engineDoFinal(encodedKey, 0, encodedKey.length);
        } catch (BadPaddingException e) {
            // Should never happen for key wrapping
            throw new UcryptoException("Internal Error", e);
        }
        return result;
    }

    // see JCE spec
    @Override
    protected synchronized Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
                               int wrappedKeyType)
        throws InvalidKeyException, NoSuchAlgorithmException {

        byte[] encodedKey;
        try {
            encodedKey = engineDoFinal(wrappedKey, 0,
                                       wrappedKey.length);
        } catch (Exception e) {
            throw (InvalidKeyException)
                (new InvalidKeyException()).initCause(e);
        }

        return NativeCipher.constructKey(wrappedKeyType, encodedKey,
                                         wrappedKeyAlgorithm);
    }
}
