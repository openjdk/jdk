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

package sun.security.provider;

import sun.security.jca.JCAUtil;
import java.security.*;
import java.security.SecureRandom;
import java.util.Arrays;

public class ML_DSA_Provider {

    public enum Version {
        DRAFT, FINAL
    }

    // This implementation works in FIPS 204 final. If for some reason
    // (for example, interop with an old version, or running an old test),
    // set the version to an older one. The following VM option is required:
    //
    // --add-exports java.base/sun.security.provider=ALL-UNNAMED
    public static Version version = Version.DRAFT;

    static int name2int(String name) {
        if (name.endsWith("44")) return 2;
        else if (name.endsWith("65")) return 3;
        else if (name.endsWith("87")) return 5;
        else throw new ProviderException();
    }

    public static class KPG extends NamedKeyPairGenerator {
        public KPG() {
            // ML-DSA-65 is default
            super("ML-DSA", "ML-DSA-65", "ML-DSA-44", "ML-DSA-87");
        }

        public KPG(String pname) {
            super("ML-DSA", pname);
        }

        @Override
        public byte[][] implGenerateKeyPair(String name, SecureRandom sr) {
            byte[] seed = new byte[32];
            var r = sr != null ? sr : JCAUtil.getDefSecureRandom();
            r.nextBytes(seed);
            ML_DSA mlDsa = new ML_DSA(name2int(name));
            ML_DSA.ML_DSA_KeyPair kp = mlDsa.generateKeyPairInternal(seed);
            try {
                return new byte[][]{
                        mlDsa.pkEncode(kp.publicKey()),
                        mlDsa.skEncode(kp.privateKey())};
            } finally {
                kp.privateKey().destroy();
                Arrays.fill(seed, (byte)0);
            }
        }
    }

    public static class KPG2 extends KPG {
        public KPG2() {
            super("ML-DSA-44");
        }
    }

    public static class KPG3 extends KPG {
        public KPG3() {
            super("ML-DSA-65");
        }
    }

    public static class KPG5 extends KPG {
        public KPG5() {
            super("ML-DSA-87");
        }
    }

    public static class KF extends NamedKeyFactory {
        public KF() {
            super("ML-DSA", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");
        }
        public KF(String name) {
            super("ML-DSA", name);
        }
    }

    public static class KF2 extends KF {
        public KF2() {
            super("ML-DSA-44");
        }
    }

    public static class KF3 extends KF {
        public KF3() {
            super("ML-DSA-65");
        }
    }

    public static class KF5 extends KF {
        public KF5() {
            super("ML-DSA-87");
        }
    }

    // TODO: check key in initSign and initVerify?
    public static class SIG extends NamedSignature {
        public SIG() {
            super("ML-DSA", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");
        }
        public SIG(String name) {
            super("ML-DSA", name);
        }

        @Override
        public byte[] implSign(String name, byte[] skBytes, Object sk2, byte[] msg, SecureRandom sr) {
            var size = name2int(name);
            var r = sr != null ? sr : JCAUtil.getDefSecureRandom();
            byte[] rnd = new byte[32];
            r.nextBytes(rnd);
            var mlDsa = new ML_DSA(size);
            if (version == Version.FINAL) {
                // FIPS 204 Algorithm 2 ML-DSA.Sign prepend {0, len(ctx)}
                // to message before passing it to Sign_internal.
                var m = new byte[msg.length + 2];
                System.arraycopy(msg, 0, m, 2, msg.length); // len(ctx) = 0
                msg = m;
            }
            ML_DSA.ML_DSA_Signature sig = mlDsa.signInternal(msg, rnd, skBytes);
            return mlDsa.sigEncode(sig);
        }

        @Override
        public boolean implVerify(String name, byte[] pkBytes, Object pk2, byte[] msg, byte[] sigBytes) {
            var size = name2int(name);
            var mlDsa = new ML_DSA(size);
            if (version == Version.FINAL) {
                // FIPS 204 Algorithm 3 ML-DSA.Verify prepend {0, len(ctx)}
                // to message before passing it to Verify_internal.
                var m = new byte[msg.length + 2];
                System.arraycopy(msg, 0, m, 2, msg.length); // len(ctx) = 0
                msg = m;
            }
            return mlDsa.verifyInternal(pkBytes, msg, sigBytes);
        }

        @Override
        public Object implCheckPublicKey(String name, byte[] pk) throws InvalidKeyException {
            ML_DSA mlDsa = new ML_DSA(name2int(name));
            int k = mlDsa.mlDsa_k;

            //PK size is 32 + 32 * k * (bitlen(q-1) - d), where bitlen(q-1) = 23
            int pk_size = 32 + (k * 32 * (23 - ML_DSA.ML_DSA_D));
            if (pk.length != pk_size) {
                throw new InvalidKeyException("Incorrect public key size");
            }
            return null;
        }

        @Override
        public Object implCheckPrivateKey(String name, byte[] sk) throws InvalidKeyException {
            int size = name2int(name);
            ML_DSA mlDsa = new ML_DSA(size);
            int k = mlDsa.mlDsa_k;
            int eta_bits = size == 3 ? 4 : 3;

            //SK size is 128 + 32 * ((l + k) * bitlen(2*eta) + d*k)
            int sk_size = 128 + 32 * ((mlDsa.mlDsa_l + k) * eta_bits + ML_DSA.ML_DSA_D * k);
            if (sk.length != sk_size) {
                throw new InvalidKeyException("Incorrect private key size");
            }
            return null;
        }
    }

    public static class SIG2 extends SIG {
        public SIG2() {
            super("ML-DSA-44");
        }
    }

    public static class SIG3 extends SIG {
        public SIG3() {
            super("ML-DSA-65");
        }
    }

    public static class SIG5 extends SIG {
        public SIG5() {
            super("ML-DSA-87");
        }
    }
}
