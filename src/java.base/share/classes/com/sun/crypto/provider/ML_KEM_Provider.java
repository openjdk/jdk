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

import java.security.SecureRandom;
import java.util.Map;

public final class ML_KEM_Provider {

    record Param(ML_KEM.Params params, int sslen, int clen) {}
    static final Map<String, Param> PARAMS = Map.of(
            "ML-KEM-512", new Param(ML_KEM.params512, 32, 768),
            "ML-KEM-768", new Param(ML_KEM.params768, 32, 1088),
            "ML-KEM-1024", new Param(ML_KEM.params1024, 32, 1568));

    public static class KPG extends NamedKeyPairGenerator {
        public KPG() {
            // ML-KEM-768 is the default
            super("ML-KEM", "ML-KEM-768", "ML-KEM-512", "ML-KEM-1024");
        }

        protected KPG(String pname) {
            super("ML-KEM", pname);
        }

        @Override
        public byte[][] implGenerateKeyPair(String name, SecureRandom sr) {
            var seed = new byte[64];
            if (sr == null) sr = JCAUtil.getDefSecureRandom();
            sr.nextBytes(seed);
            return ML_KEM.KeyGen(seed, PARAMS.get(name).params());
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

    // TODO: check key in newEnc and newDec? No interfaces. Name is checked
    public static class K extends NamedKEM {
        @Override
        public byte[][] implEncapsulate(String name, byte[] pk, Object pk2, SecureRandom sr) {
            var seed = new byte[32];
            if (sr == null) sr = JCAUtil.getDefSecureRandom();
            sr.nextBytes(seed);
            return ML_KEM.Enc(pk, seed, PARAMS.get(name).params());
        }

        @Override
        public byte[] implDecapsulate(String name, byte[] sk, Object sk2, byte[] encap) {
            return ML_KEM.Dec(sk, encap, PARAMS.get(name).params());
        }

        @Override
        public int implSecretSize(String name) {
            return PARAMS.get(name).sslen();
        }

        @Override
        public int implEncapsulationSize(String name) {
            return PARAMS.get(name).clen();
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
