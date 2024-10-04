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

public class ML_DSA_Provider {

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
            ML_DSA.ML_DSA_KeyPair kp = mlDsa.generateKeyPair(seed);
            return new byte[][] {
                    mlDsa.pkEncode(kp.publicKey()),
                    mlDsa.skEncode(kp.privateKey()) };
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
            var sk = mlDsa.skDecode(skBytes);
            ML_DSA.ML_DSA_Signature sig = mlDsa.sign(msg, rnd, sk);
            return mlDsa.sigEncode(sig);
        }

        @Override
        public boolean implVerify(String name, byte[] pkBytes, Object pk2, byte[] msg, byte[] sigBytes) {
            var size = name2int(name);
            var mlDsa = new ML_DSA(size);
            var pk = mlDsa.pkDecode(pkBytes);
            var sig = mlDsa.sigDecode(sigBytes);
            return mlDsa.verify(pk, msg, sig);
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
