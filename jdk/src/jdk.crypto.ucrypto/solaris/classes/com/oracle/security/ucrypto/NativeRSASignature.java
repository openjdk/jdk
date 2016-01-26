/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListSet;
import java.lang.ref.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import java.security.SignatureSpi;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidParameterException;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;

import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

import sun.nio.ch.DirectBuffer;
import java.nio.ByteBuffer;

/**
 * Signature implementation class. This class currently supports the
 * following algorithms:
 *
 * . RSA:
 *   . MD5withRSA
 *   . SHA1withRSA
 *   . SHA256withRSA
 *   . SHA384withRSA
 *   . SHA512withRSA
 *
 * @since 9
 */
class NativeRSASignature extends SignatureSpi {

    private static final int PKCS1PADDING_LEN = 11;

    // fields set in constructor
    private final UcryptoMech mech;
    private final int encodedLen;

    // field for ensuring native memory is freed
    private SignatureContextRef pCtxt = null;

    //
    // fields (re)set in every init()
    //
    private boolean initialized = false;
    private boolean sign = true;
    private int sigLength;
    private NativeKey key;
    private NativeRSAKeyFactory keyFactory; // may need a more generic type later

    // public implementation classes
    public static final class MD5 extends NativeRSASignature {
        public MD5() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_MD5_RSA_PKCS, 34);
        }
    }

    public static final class SHA1 extends NativeRSASignature {
        public SHA1() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_SHA1_RSA_PKCS, 35);
        }
    }

    public static final class SHA256 extends NativeRSASignature {
        public SHA256() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_SHA256_RSA_PKCS, 51);
        }
    }

    public static final class SHA384 extends NativeRSASignature {
        public SHA384() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_SHA384_RSA_PKCS, 67);
        }
    }

    public static final class SHA512 extends NativeRSASignature {
        public SHA512() throws NoSuchAlgorithmException {
            super(UcryptoMech.CRYPTO_SHA512_RSA_PKCS, 83);
        }
    }

    // internal class for native resource cleanup
    private static class SignatureContextRef extends PhantomReference<NativeRSASignature>
        implements Comparable<SignatureContextRef> {

        private static ReferenceQueue<NativeRSASignature> refQueue =
            new ReferenceQueue<NativeRSASignature>();

        // Needed to keep these references from being GC'ed until when their
        // referents are GC'ed so we can do post-mortem processing
        private static Set<SignatureContextRef> refList =
            new ConcurrentSkipListSet<SignatureContextRef>();
        //           Collections.synchronizedSortedSet(new TreeSet<SignatureContextRef>());

        private final long id;
        private final boolean sign;

        private static void drainRefQueueBounded() {
            while (true) {
                SignatureContextRef next = (SignatureContextRef) refQueue.poll();
                if (next == null) break;
                next.dispose(true);
            }
        }

        SignatureContextRef(NativeRSASignature ns, long id, boolean sign) {
            super(ns, refQueue);
            this.id = id;
            this.sign = sign;
            refList.add(this);
            UcryptoProvider.debug("Resource: track Signature Ctxt " + this.id);
            drainRefQueueBounded();
        }

        public int compareTo(SignatureContextRef other) {
            if (this.id == other.id) {
                return 0;
            } else {
                return (this.id < other.id) ? -1 : 1;
            }
        }

        void dispose(boolean doCancel) {
            refList.remove(this);
            try {
                if (doCancel) {
                    UcryptoProvider.debug("Resource: free Signature Ctxt " + this.id);
                    NativeRSASignature.nativeFinal(id, sign, null, 0, 0);
                } else {
                    UcryptoProvider.debug("Resource: stop tracking Signature Ctxt " + this.id);
                }
            } finally {
                this.clear();
            }
        }
    }

    NativeRSASignature(UcryptoMech mech, int encodedLen)
        throws NoSuchAlgorithmException {
        this.mech = mech;
        this.encodedLen = encodedLen;
        this.keyFactory = new NativeRSAKeyFactory();
    }

    // deprecated but abstract
    @SuppressWarnings("deprecation")
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        throw new UnsupportedOperationException("getParameter() not supported");
    }

    @Override
    protected synchronized void engineInitSign(PrivateKey privateKey)
            throws InvalidKeyException {
        if (privateKey == null) {
            throw new InvalidKeyException("Key must not be null");
        }
        NativeKey newKey = key;
        int newSigLength = sigLength;
        // Need to check RSA key length whenever a new private key is set
        if (privateKey != key) {
            if (!(privateKey instanceof RSAPrivateKey)) {
                throw new InvalidKeyException("RSAPrivateKey required. " +
                    "Received: " + privateKey.getClass().getName());
            }
            RSAPrivateKey rsaPrivKey = (RSAPrivateKey) privateKey;
            BigInteger mod = rsaPrivKey.getModulus();
            newSigLength = checkRSAKeyLength(mod);
            BigInteger pe = rsaPrivKey.getPrivateExponent();
            try {
                if (rsaPrivKey instanceof RSAPrivateCrtKey) {
                    RSAPrivateCrtKey rsaPrivCrtKey = (RSAPrivateCrtKey) rsaPrivKey;
                    newKey = (NativeKey) keyFactory.engineGeneratePrivate
                        (new RSAPrivateCrtKeySpec(mod,
                                                  rsaPrivCrtKey.getPublicExponent(),
                                                  pe,
                                                  rsaPrivCrtKey.getPrimeP(),
                                                  rsaPrivCrtKey.getPrimeQ(),
                                                  rsaPrivCrtKey.getPrimeExponentP(),
                                                  rsaPrivCrtKey.getPrimeExponentQ(),
                                                  rsaPrivCrtKey.getCrtCoefficient()));
                } else {
                    newKey = (NativeKey) keyFactory.engineGeneratePrivate
                           (new RSAPrivateKeySpec(mod, pe));
                }
            } catch (InvalidKeySpecException ikse) {
                throw new InvalidKeyException(ikse);
            }
        }
        init(true, newKey, newSigLength);
    }


    @Override
    protected synchronized void engineInitVerify(PublicKey publicKey)
            throws InvalidKeyException {
        if (publicKey == null) {
            throw new InvalidKeyException("Key must not be null");
        }
        NativeKey newKey = key;
        int newSigLength = sigLength;
        // Need to check RSA key length whenever a new public key is set
        if (publicKey != key) {
            if (publicKey instanceof RSAPublicKey) {
                BigInteger mod = ((RSAPublicKey) publicKey).getModulus();
                newSigLength = checkRSAKeyLength(mod);
                try {
                    newKey = (NativeKey) keyFactory.engineGeneratePublic
                        (new RSAPublicKeySpec(mod, ((RSAPublicKey) publicKey).getPublicExponent()));
                } catch (InvalidKeySpecException ikse) {
                    throw new InvalidKeyException(ikse);
                }
            } else {
                throw new InvalidKeyException("RSAPublicKey required. " +
                    "Received: " + publicKey.getClass().getName());
            }
        }
        init(false, newKey, newSigLength);
    }

    // deprecated but abstract
    @SuppressWarnings("deprecation")
    protected void engineSetParameter(String param, Object value) throws InvalidParameterException {
        throw new UnsupportedOperationException("setParameter() not supported");
    }

    @Override
    protected synchronized byte[] engineSign() throws SignatureException {
        byte[] sig = new byte[sigLength];
        int rv = doFinal(sig, 0, sigLength);
        if (rv < 0) {
            throw new SignatureException(new UcryptoException(-rv));
        }
        return sig;
    }

    @Override
    protected synchronized int engineSign(byte[] outbuf, int offset, int len)
        throws SignatureException {
        if (outbuf == null || (offset < 0) || (outbuf.length < (offset + sigLength))
            || (len < sigLength)) {
            throw new SignatureException("Invalid output buffer. offset: " +
                offset + ". len: " + len + ". sigLength: " + sigLength);
        }
        int rv = doFinal(outbuf, offset, sigLength);
        if (rv < 0) {
            throw new SignatureException(new UcryptoException(-rv));
        }
        return sigLength;
    }

    @Override
    protected synchronized void engineUpdate(byte b) throws SignatureException {
        byte[] in = { b };
        int rv = update(in, 0, 1);
        if (rv < 0) {
            throw new SignatureException(new UcryptoException(-rv));
        }
    }

    @Override
    protected synchronized void engineUpdate(byte[] in, int inOfs, int inLen)
            throws SignatureException {
        if (in == null || inOfs < 0 || inLen == 0) return;

        int rv = update(in, inOfs, inLen);
        if (rv < 0) {
            throw new SignatureException(new UcryptoException(-rv));
        }
    }

    @Override
    protected synchronized void engineUpdate(ByteBuffer in) {
        if (in == null || in.remaining() == 0) return;

        if (in instanceof DirectBuffer == false) {
            // cannot do better than default impl
            super.engineUpdate(in);
            return;
        }
        long inAddr = ((DirectBuffer)in).address();
        int inOfs = in.position();
        int inLen = in.remaining();

        int rv = update((inAddr + inOfs), inLen);
        if (rv < 0) {
            throw new UcryptoException(-rv);
        }
        in.position(inOfs + inLen);
    }

    @Override
    protected synchronized boolean engineVerify(byte[] sigBytes) throws SignatureException {
        return engineVerify(sigBytes, 0, sigBytes.length);
    }

    @Override
    protected synchronized boolean engineVerify(byte[] sigBytes, int sigOfs, int sigLen)
        throws SignatureException {
        if (sigBytes == null || (sigOfs < 0) || (sigBytes.length < (sigOfs + this.sigLength))
            || (sigLen < this.sigLength)) {
            throw new SignatureException("Invalid signature buffer. sigOfs: " +
                sigOfs + ". sigLen: " + sigLen + ". this.sigLength: " + this.sigLength);
        }

        int rv = doFinal(sigBytes, sigOfs, sigLen);
        if (rv == 0) {
            return true;
        } else {
            UcryptoProvider.debug("Signature: " + mech + " verification error " +
                             new UcryptoException(-rv).getMessage());
            return false;
        }
    }

    void reset(boolean doCancel) {
        initialized = false;
        if (pCtxt != null) {
            pCtxt.dispose(doCancel);
            pCtxt = null;
        }
    }

    /**
     * calls ucrypto_sign_init(...) or ucrypto_verify_init(...)
     * @return pointer to the context
     */
    private native static long nativeInit(int mech, boolean sign,
                                          long keyValue, int keyLength);

    /**
     * calls ucrypto_sign_update(...) or ucrypto_verify_update(...)
     * @return an error status code (0 means SUCCESS)
     */
    private native static int nativeUpdate(long pContext, boolean sign,
                                           byte[] in, int inOfs, int inLen);
    /**
     * calls ucrypto_sign_update(...) or ucrypto_verify_update(...)
     * @return an error status code (0 means SUCCESS)
     */
    private native static int nativeUpdate(long pContext, boolean sign,
                                           long pIn, int inLen);

    /**
     * calls ucrypto_sign_final(...) or ucrypto_verify_final(...)
     * @return the length of signature bytes or verification status.
     * If negative, it indicates an error status code
     */
    private native static int nativeFinal(long pContext, boolean sign,
                                          byte[] sig, int sigOfs, int sigLen);

    // actual init() implementation - caller should clone key if needed
    private void init(boolean sign, NativeKey key, int sigLength) {
        reset(true);
        this.sign = sign;
        this.sigLength = sigLength;
        this.key = key;
        long pCtxtVal = nativeInit(mech.value(), sign, key.value(),
                                   key.length());
        initialized = (pCtxtVal != 0L);
        if (initialized) {
            pCtxt = new SignatureContextRef(this, pCtxtVal, sign);
        } else {
            throw new UcryptoException("Cannot initialize Signature");
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            init(sign, key, sigLength);
            if (!initialized) {
                throw new UcryptoException("Cannot initialize Signature");
            }
        }
    }

    // returns 0 (success) or negative (ucrypto error occurred)
    private int update(byte[] in, int inOfs, int inLen) {
        if (inOfs < 0 || inOfs + inLen > in.length) {
            throw new ArrayIndexOutOfBoundsException("inOfs :" + inOfs +
                ". inLen: " + inLen + ". in.length: " + in.length);
        }
        ensureInitialized();
        int k = nativeUpdate(pCtxt.id, sign, in, inOfs, inLen);
        if (k < 0) {
            reset(false);
        }
        return k;
    }

    // returns 0 (success) or negative (ucrypto error occurred)
    private int update(long pIn, int inLen) {
        ensureInitialized();
        int k = nativeUpdate(pCtxt.id, sign, pIn, inLen);
        if (k < 0) {
            reset(false);
        }
        return k;
    }

    // returns 0 (success) or negative (ucrypto error occurred)
    private int doFinal(byte[] sigBytes, int sigOfs, int sigLen) {
        try {
            ensureInitialized();
            int k = nativeFinal(pCtxt.id, sign, sigBytes, sigOfs, sigLen);
            return k;
        } finally {
            reset(false);
        }
    }

    // check and return RSA key size in number of bytes
    private int checkRSAKeyLength(BigInteger mod) throws InvalidKeyException {
        int keySize = (mod.bitLength() + 7) >> 3;
        int maxDataSize = keySize - PKCS1PADDING_LEN;
        if (maxDataSize < encodedLen) {
            throw new InvalidKeyException
                ("Key is too short for this signature algorithm. maxDataSize: " +
                    maxDataSize + ". encodedLen: " + encodedLen);
        }
        return keySize;
    }
}
