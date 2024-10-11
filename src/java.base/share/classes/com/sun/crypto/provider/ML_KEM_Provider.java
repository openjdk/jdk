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
import java.util.Map;

import javax.crypto.DecapsulateException;

public final class ML_KEM_Provider {

    static int name2int(String name) {
        if (name.endsWith("512")) return 512;
        else if (name.endsWith("768")) return 768;
        else if (name.endsWith("1024")) return 1024;
        else throw new ProviderException();
    }

    public static class KPG extends NamedKeyPairGenerator {
        public KPG() {
            // ML-KEM-768 is the default
            super("ML-KEM", "ML-KEM-768", "ML-KEM-512", "ML-KEM-1024");
        }

        protected KPG(String pname) {
            super("ML-KEM", pname);
        }

        @Override
        public byte[][] implGenerateKeyPair(String name, SecureRandom random) {
            byte[] seed = new byte[32];
            random.nextBytes(seed);
            byte[] z = new byte[32];
            random.nextBytes(z);

            ML_KEM mlKem = new ML_KEM(name2int(name));
            ML_KEM.ML_KEM_KeyPair kp;
            try {
                kp = mlKem.generateKemKeyPair(seed, z);
            } catch (NoSuchAlgorithmException | DigestException e) {
                throw new RuntimeException("internal error", e);
            }
            return new byte[][] {
                kp.encapsulationKey().keyBytes(),
                kp.decapsulationKey().keyBytes() };
        }
    }

    public static class KPG2 extends KPG {
        public KPG2() {
            super("ML-KEM-512");
        }
    }

    public static class KPG3 extends KPG {
        public KPG3() {
            super("ML-KEM-768");
        }
    }

    public static class KPG5 extends KPG {
        public KPG5() {
            super("ML-KEM-1024");
        }
    }

    public static class KF extends NamedKeyFactory {
        public KF() {
            super("ML-KEM", "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024");
        }
        public KF(String name) {
            super("ML-KEM", name);
        }
    }

    public static class KF2 extends KF {
        public KF2() {
            super("ML-KEM-512");
        }
    }

    public static class KF3 extends KF {
        public KF3() {
            super("ML-KEM-768");
        }
    }

    public static class KF5 extends KF {
        public KF5() {
            super("ML-KEM-1024");
        }
    }

    public static class K extends NamedKEM {
        @Override
        public byte[][] implEncapsulate(String name, byte[] encapsulationKey, Object ek, SecureRandom secureRandom) {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);

            ML_KEM mlKem = new ML_KEM(name2int(name));
            ML_KEM.ML_KEM_EncapsulateResult mlKemEncapsulateResult = null;
            try {
                mlKemEncapsulateResult = mlKem.encapsulate(
                    new ML_KEM.ML_KEM_EncapsulationKey(encapsulationKey), randomBytes);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e); // should not happen
            }

            return new byte[][] {
                mlKemEncapsulateResult.cipherText().encryptedBytes(),
                mlKemEncapsulateResult.sharedSecret() };
        }

        @Override
        public byte[] implDecapsulate(String name, byte[] decapsulationKey, Object dk, byte[] cipherText) {
            ML_KEM mlKem = new ML_KEM(name2int(name));
            var kpkeCipherText = new ML_KEM.K_PKE_CipherText(cipherText);

            byte[] decapsulateResult = null;
            try {
                decapsulateResult = mlKem.decapsulate(
                    new ML_KEM.ML_KEM_DecapsulationKey(decapsulationKey), kpkeCipherText);
            } catch (NoSuchAlgorithmException | InvalidKeyException | DecapsulateException e) {
                throw new RuntimeException(e); // should not happen
            }

            return decapsulateResult;
        }

        @Override
        public int implSecretSize(String name) {return ML_KEM.secretSize;}

        @Override
        public int implEncapsulationSize(String name) {
            ML_KEM mlKem = new ML_KEM(name2int(name));
            return mlKem.encapsulationSize;
        }

        public K() {
            super("ML-KEM", "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024");
        }

        public K(String name) {
            super("ML-KEM", name);
        }
    }

    public static class K2 extends K {
        public K2() {
            super("ML-KEM-512");
        }
    }

    public static class K3 extends K {
        public K3() {
            super("ML-KEM-768");
        }
    }

    public static class K5 extends K {
        public K5() {
            super("ML-KEM-1024");
        }
    }
}
