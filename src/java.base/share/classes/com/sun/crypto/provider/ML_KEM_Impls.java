/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.jca.JCAUtil;
import sun.security.provider.NamedKEM;
import sun.security.provider.NamedKeyFactory;
import sun.security.provider.NamedKeyPairGenerator;

import java.security.*;
import java.util.Arrays;

import javax.crypto.DecapsulateException;

public final class ML_KEM_Impls {

    public sealed static class KPG
        extends NamedKeyPairGenerator permits KPG2, KPG3, KPG5 {

        public KPG() {
            // ML-KEM-768 is the default
            super("ML-KEM", "ML-KEM-768", "ML-KEM-512", "ML-KEM-1024");
        }

        protected KPG(String pname) {
            super("ML-KEM", pname);
        }

        @Override
        protected byte[][] implGenerateKeyPair(String name, SecureRandom random) {
            byte[] seed = new byte[32];
            var r = random != null ? random : JCAUtil.getDefSecureRandom();
            r.nextBytes(seed);
            byte[] z = new byte[32];
            r.nextBytes(z);

            ML_KEM mlKem = new ML_KEM(name);
            ML_KEM.ML_KEM_KeyPair kp;
            try {
                kp = mlKem.generateKemKeyPair(seed, z);
            } finally {
                Arrays.fill(seed, (byte)0);
                Arrays.fill(z, (byte)0);
            }
            return new byte[][] {
                    kp.encapsulationKey().keyBytes(),
                    kp.decapsulationKey().keyBytes()
            };
        }
    }

    public final static class KPG2 extends KPG {
        public KPG2() {
            super("ML-KEM-512");
        }
    }

    public final static class KPG3 extends KPG {
        public KPG3() {
            super("ML-KEM-768");
        }
    }

    public final static class KPG5 extends KPG {
        public KPG5() {
            super("ML-KEM-1024");
        }
    }

    public sealed static class KF extends NamedKeyFactory permits KF2, KF3, KF5 {
        public KF() {
            super("ML-KEM", "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024");
        }
        public KF(String name) {
            super("ML-KEM", name);
        }
    }

    public final static class KF2 extends KF {
        public KF2() {
            super("ML-KEM-512");
        }
    }

    public final static class KF3 extends KF {
        public KF3() {
            super("ML-KEM-768");
        }
    }

    public final static class KF5 extends KF {
        public KF5() {
            super("ML-KEM-1024");
        }
    }

    public sealed static class K extends NamedKEM permits K2, K3, K5 {
        private static final int SEED_SIZE = 32;

        @Override
        protected byte[][] implEncapsulate(String name, byte[] encapsulationKey,
                                           Object ek, SecureRandom secureRandom) {

            byte[] randomBytes = new byte[SEED_SIZE];
            var r = secureRandom != null ? secureRandom : JCAUtil.getDefSecureRandom();
            r.nextBytes(randomBytes);

            ML_KEM mlKem = new ML_KEM(name);
            ML_KEM.ML_KEM_EncapsulateResult mlKemEncapsulateResult = null;
            try {
                mlKemEncapsulateResult = mlKem.encapsulate(
                        new ML_KEM.ML_KEM_EncapsulationKey(
                            encapsulationKey), randomBytes);
            } finally {
                Arrays.fill(randomBytes, (byte) 0);
            }

            return new byte[][] {
                mlKemEncapsulateResult.cipherText().encryptedBytes(),
                mlKemEncapsulateResult.sharedSecret()
            };
        }

        @Override
        protected byte[] implDecapsulate(String name, byte[] decapsulationKey,
                                         Object dk, byte[] cipherText)
            throws DecapsulateException {

            ML_KEM mlKem = new ML_KEM(name);
            var kpkeCipherText = new ML_KEM.K_PKE_CipherText(cipherText);
            return mlKem.decapsulate(new ML_KEM.ML_KEM_DecapsulationKey(
                    decapsulationKey), kpkeCipherText);
        }

        @Override
        protected int implSecretSize(String name) {
            return ML_KEM.SECRET_SIZE;
        }

        @Override
        protected int implEncapsulationSize(String name) {
            ML_KEM mlKem = new ML_KEM(name);
            return mlKem.getEncapsulationSize();
        }

        @Override
        protected Object implCheckPublicKey(String name, byte[] pk)
            throws InvalidKeyException {

            ML_KEM mlKem = new ML_KEM(name);
            return mlKem.checkPublicKey(pk);
        }

        @Override
        protected Object implCheckPrivateKey(String name, byte[] sk)
            throws InvalidKeyException {

            ML_KEM mlKem = new ML_KEM(name);
            return mlKem.checkPrivateKey(sk);
        }

        public K() {
            super("ML-KEM", "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024");
        }

        public K(String name) {
            super("ML-KEM", name);
        }
    }

    public final static class K2 extends K {
        public K2() {
            super("ML-KEM-512");
        }
    }

    public final static class K3 extends K {
        public K3() {
            super("ML-KEM-768");
        }
    }

    public final static class K5 extends K {
        public K5() {
            super("ML-KEM-1024");
        }
    }
}
