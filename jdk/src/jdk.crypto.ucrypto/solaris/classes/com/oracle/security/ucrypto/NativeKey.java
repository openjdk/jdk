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
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

/**
 * Wrapper class for native keys needed for using ucrypto APIs.
 * This class currently supports native RSA private/public keys.
 *
 * @since 1.9
 */
abstract class NativeKey implements Key {

    private static final long serialVersionUID = 6812507588904302830L;

    private final int numComponents;

    NativeKey(int numComponents) {
        this.numComponents = numComponents;
    }

    abstract long value();

    int length() {
        return numComponents;
    }

    public String getAlgorithm() { return "RSA"; }
    public String getFormat() { return "RAW"; }
    public byte[] getEncoded() {
        // not used; so not generated
        return null;
    }

    private native static void nativeFree(long id, int numComponents);

    static byte[] getMagnitude(BigInteger bi) {
        byte[] b = bi.toByteArray();
        if ((b.length > 1) && (b[0] == 0)) {
            int n = b.length - 1;
            byte[] newarray = new byte[n];
            System.arraycopy(b, 1, newarray, 0, n);
            b = newarray;
        }
        return b;
    }

    static final class RSAPrivate extends NativeKey implements RSAPrivateKey {

        private static final long serialVersionUID = 1622705588904302831L;

        private final RSAPrivateKeySpec keySpec;
        private final long keyId;

        RSAPrivate(KeySpec keySpec) throws InvalidKeySpecException {
            super(2);
            long pKey = 0L;
            if (keySpec instanceof RSAPrivateKeySpec) {
                RSAPrivateKeySpec ks = (RSAPrivateKeySpec) keySpec;
                BigInteger mod = ks.getModulus();
                BigInteger privateExp =  ks.getPrivateExponent();
                pKey = nativeInit(NativeKey.getMagnitude(mod),
                                  NativeKey.getMagnitude(privateExp));
            } else {
                throw new InvalidKeySpecException("Only supports RSAPrivateKeySpec." +
                    " Received: " + keySpec.getClass().getName());
            }
            if (pKey == 0L) {
                throw new UcryptoException("Error constructing RSA PrivateKey");
            }
            // track native resource clean up
            new KeyRef(this, pKey);
            this.keySpec = (RSAPrivateKeySpec) keySpec;
            this.keyId = pKey;
        }

        long value() { return keyId; }
        public BigInteger getModulus() { return keySpec.getModulus(); };
        public BigInteger getPrivateExponent() { return keySpec.getPrivateExponent(); };

        private native static long nativeInit(byte[] mod, byte[] privExp);
    }

    static final class RSAPrivateCrt extends NativeKey implements RSAPrivateCrtKey {

        private static final long serialVersionUID = 6812507588904302831L;

        private final RSAPrivateCrtKeySpec keySpec;
        private final long keyId;

        RSAPrivateCrt(KeySpec keySpec) throws InvalidKeySpecException {
            super(8);
            long pKey = 0L;
            if (keySpec instanceof RSAPrivateCrtKeySpec) {
                RSAPrivateCrtKeySpec ks = (RSAPrivateCrtKeySpec) keySpec;
                BigInteger mod = ks.getModulus();
                BigInteger publicExp =  ks.getPublicExponent();
                BigInteger privateExp =  ks.getPrivateExponent();
                BigInteger primeP = ks.getPrimeP();
                BigInteger primeQ = ks.getPrimeQ();
                BigInteger primeExpP = ks.getPrimeExponentP();
                BigInteger primeExpQ = ks.getPrimeExponentQ();
                BigInteger crtCoeff = ks.getCrtCoefficient();
                pKey = nativeInit(NativeKey.getMagnitude(mod),
                                  NativeKey.getMagnitude(publicExp),
                                  NativeKey.getMagnitude(privateExp),
                                  NativeKey.getMagnitude(primeP),
                                  NativeKey.getMagnitude(primeQ),
                                  NativeKey.getMagnitude(primeExpP),
                                  NativeKey.getMagnitude(primeExpQ),
                                  NativeKey.getMagnitude(crtCoeff));
            } else {
                throw new InvalidKeySpecException("Only supports RSAPrivateCrtKeySpec."
                    + " Received: " + keySpec.getClass().getName());
            }
            if (pKey == 0L) {
                throw new UcryptoException("Error constructing RSA PrivateCrtKey");
            }
            // track native resource clean up
            new KeyRef(this, pKey);
            this.keySpec = (RSAPrivateCrtKeySpec) keySpec;
            this.keyId = pKey;
        }

        long value() { return keyId; }
        public BigInteger getModulus() { return keySpec.getModulus(); };
        public BigInteger getPublicExponent() { return keySpec.getPublicExponent(); };
        public BigInteger getPrivateExponent() { return keySpec.getPrivateExponent(); };
        public BigInteger getPrimeP() { return keySpec.getPrimeP(); };
        public BigInteger getPrimeQ() { return keySpec.getPrimeQ(); };
        public BigInteger getPrimeExponentP() { return keySpec.getPrimeExponentP(); };
        public BigInteger getPrimeExponentQ() { return keySpec.getPrimeExponentQ(); };
        public BigInteger getCrtCoefficient() { return keySpec.getCrtCoefficient(); };

        private native static long nativeInit(byte[] mod, byte[] pubExp, byte[] privExp,
                                      byte[] p, byte[] q,
                                      byte[] expP, byte[] expQ, byte[] crtCoeff);
    }

    static final class RSAPublic extends NativeKey implements RSAPublicKey {

        private static final long serialVersionUID = 6812507588904302832L;

        private final RSAPublicKeySpec keySpec;
        private final long keyId;

        RSAPublic(KeySpec keySpec) throws InvalidKeySpecException {
            super(2);
            long pKey = 0L;
            if (keySpec instanceof RSAPublicKeySpec) {
                RSAPublicKeySpec ks = (RSAPublicKeySpec) keySpec;
                BigInteger mod = ks.getModulus();
                BigInteger publicExp = ks.getPublicExponent();
                pKey = nativeInit(NativeKey.getMagnitude(mod),
                                  NativeKey.getMagnitude(publicExp));
            } else {
                throw new InvalidKeySpecException("Only supports RSAPublicKeySpec." +
                    " Received: " + keySpec.getClass().getName());
            }
            if (pKey == 0L) {
                throw new UcryptoException("Error constructing RSA PublicKey");
            }
            // track native resource clean up
            new KeyRef(this, pKey);
            this.keySpec = (RSAPublicKeySpec) keySpec;
            this.keyId = pKey;
        }

        long value() { return keyId; }
        public BigInteger getModulus() { return keySpec.getModulus(); };
        public BigInteger getPublicExponent() { return keySpec.getPublicExponent(); };

        private native static long nativeInit(byte[] mod, byte[] pubExp);
    }

    // internal class for native resource cleanup
    private static class KeyRef extends PhantomReference<NativeKey>
        implements Comparable<KeyRef> {

        private static ReferenceQueue<NativeKey> refQueue =
            new ReferenceQueue<NativeKey>();

        // Needed to keep these references from being GC'ed until when their
        // referents are GC'ed so we can do post-mortem processing
        private static Set<KeyRef> refList =
            new ConcurrentSkipListSet<KeyRef>();

        private final long id;
        private final int length;

        private static void drainRefQueueBounded() {
            while (true) {
                KeyRef next = (KeyRef) refQueue.poll();
                if (next == null) break;
                next.dispose();
            }
        }

        KeyRef(NativeKey nk, long id) {
            super(nk, refQueue);
            this.id = id;
            this.length = nk.length();
            refList.add(this);
            UcryptoProvider.debug("Resource: track NativeKey " + this.id);
            drainRefQueueBounded();
        }

        public int compareTo(KeyRef other) {
            if (this.id == other.id) {
                return 0;
            } else {
                return (this.id < other.id) ? -1 : 1;
            }
        }

        void dispose() {
            refList.remove(this);
            UcryptoProvider.debug("Resource: free NativeKey " + this.id);
            try {
                NativeKey.nativeFree(id, length);
            } finally {
                this.clear();
            }
        }
    }
}
