/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.provider.NamedKEM;
import sun.security.provider.NamedKeyFactory;
import sun.security.provider.NamedKeyPairGenerator;
import sun.security.util.KeyUtil;
import sun.security.x509.NamedX509Key;

import java.security.*;
import java.util.Arrays;

import javax.crypto.DecapsulateException;

public final class ML_KEM_Impls {

    private static final int SEED_LEN = 64;

    public static byte[] seedToExpanded(String pname, byte[] seed) {
        return new ML_KEM(pname).generateKemKeyPair(seed)
                .decapsulationKey()
                .keyBytes();
    }

    public static NamedX509Key privKeyToPubKey(NamedPKCS8Key npk) {
        return new NamedX509Key(npk.getAlgorithm(),
                npk.getParams().getName(),
                new ML_KEM(npk.getParams().getName()).privKeyToPubKey(npk.getExpanded()));
    }

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
        protected byte[][] implGenerateKeyPair(String pname, SecureRandom random) {
            byte[] seed = new byte[SEED_LEN];
            var r = random != null ? random : JCAUtil.getDefSecureRandom();
            r.nextBytes(seed);

            ML_KEM mlKem = new ML_KEM(pname);
            ML_KEM.ML_KEM_KeyPair kp;
            kp = mlKem.generateKemKeyPair(seed);
            var expanded = kp.decapsulationKey().keyBytes();

            try {
                return new byte[][]{
                        kp.encapsulationKey().keyBytes(),
                        KeyUtil.writeToChoices(pname, "mlkem", seed, expanded, null),
                        expanded
                };
            } finally {
                Arrays.fill(seed, (byte) 0);
            }
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
        public KF(String pname) {
            super("ML-KEM", pname);
        }

        @Override
        protected byte[] implExpand(String pname, byte[] input)
                throws InvalidKeyException {
            var parts = KeyUtil.splitChoices(SEED_LEN, input);
            if (parts[0] != null && parts[1] != null) {
                var calculated = seedToExpanded(pname, parts[0]);
                if (!Arrays.equals(parts[1], calculated)) {
                    throw new InvalidKeyException("seed and expandedKey do not match");
                }
                Arrays.fill(calculated, (byte)0);
            }
            try {
                if (parts[1] != null) {
                    return parts[1];
                }
                return seedToExpanded(pname, parts[0]);
            } finally {
                if (parts[0] != null) {
                    Arrays.fill(parts[0], (byte)0);
                }
            }
        }

        @Override
        protected Key engineTranslateKey(Key key) throws InvalidKeyException {
            var nk = toNamedKey(key);
            if (nk instanceof NamedPKCS8Key npk) {
                var parts = KeyUtil.splitChoices(SEED_LEN, npk.getRawBytes());
                var encoding = KeyUtil.writeToChoices(npk.getParams().getName(),
                        "mlkem", parts[0], parts[1],
                        ML_KEM_Impls::seedToExpanded);
                if (parts[0] != null) {
                    Arrays.fill(parts[0], (byte)0);
                }
                if (parts[1] != null) {
                    Arrays.fill(parts[1], (byte)0);
                }
                if (encoding == null) {
                    throw new InvalidKeyException("key contains not enough info to translate");
                }
                nk = new NamedPKCS8Key(
                        npk.getAlgorithm(),
                        npk.getParams().getName(),
                        encoding,
                        npk.getExpanded().clone());
                if (npk != key) {
                    npk.destroy();
                }
            }
            return nk;
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
        protected byte[][] implEncapsulate(String pname, byte[] encapsulationKey,
                                           Object ek, SecureRandom secureRandom) {

            byte[] randomBytes = new byte[SEED_SIZE];
            var r = secureRandom != null ? secureRandom : JCAUtil.getDefSecureRandom();
            r.nextBytes(randomBytes);

            ML_KEM mlKem = new ML_KEM(pname);
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
        protected byte[] implDecapsulate(String pname, byte[] decapsulationKey,
                                         Object dk, byte[] cipherText)
            throws DecapsulateException {

            ML_KEM mlKem = new ML_KEM(pname);
            var kpkeCipherText = new ML_KEM.K_PKE_CipherText(cipherText);
            return mlKem.decapsulate(new ML_KEM.ML_KEM_DecapsulationKey(
                    decapsulationKey), kpkeCipherText);
        }

        @Override
        protected int implSecretSize(String pname) {
            return ML_KEM.SECRET_SIZE;
        }

        @Override
        protected int implEncapsulationSize(String pname) {
            ML_KEM mlKem = new ML_KEM(pname);
            return mlKem.getEncapsulationSize();
        }

        @Override
        protected Object implCheckPublicKey(String pname, byte[] pk)
            throws InvalidKeyException {

            ML_KEM mlKem = new ML_KEM(pname);
            return mlKem.checkPublicKey(pk);
        }

        @Override
        protected Object implCheckPrivateKey(String pname, byte[] sk)
            throws InvalidKeyException {

            ML_KEM mlKem = new ML_KEM(pname);
            return mlKem.checkPrivateKey(sk);
        }

        public K() {
            super("ML-KEM", new KF(), "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024");
        }

        public K(String pname) {
            super("ML-KEM", new KF(pname), pname);
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
