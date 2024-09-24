/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.provider
 * @library /test/lib
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.security.provider.NamedKeyFactory;
import sun.security.provider.NamedKeyPairGenerator;
import sun.security.x509.NamedX509Key;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;

public class NamedKeyFactoryTest {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new ProviderImpl());

        var k = new NamedX509Key("sHa", "SHA-256", new byte[2]);
        var kf = KeyFactory.getInstance("ShA");

        Asserts.assertTrue(k.getAlgorithm().equalsIgnoreCase("SHA"));
        Asserts.assertEquals(k.getFormat(), "X.509");
        Asserts.assertEquals(k.getParams().getName(), "SHA-256");

        var spec = kf.getKeySpec(k, X509EncodedKeySpec.class);
        Asserts.assertEquals(kf.generatePublic(spec).getAlgorithm(), "SHA");

        var kf2 = KeyFactory.getInstance("Sha-256");
        var kf5 = KeyFactory.getInstance("Sha-512");

        var pk1 = new PublicKey() {
            public String getAlgorithm() { return "SHA"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return new byte[2]; }
        };
        var pk2 = new PublicKey() {
            public String getAlgorithm() { return "sHA-256"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return new byte[2]; }
        };
        var pk3 = new PublicKey() {
            public String getAlgorithm() { return "SHA"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return new byte[2]; }
            public AlgorithmParameterSpec getParams() { return new NamedParameterSpec("sHA-256"); }
        };

        Asserts.assertTrue(kf2.translateKey(pk1).toString().contains("SHA-256"));
        Asserts.assertTrue(kf.translateKey(pk2).toString().contains("SHA-256"));
        Asserts.assertTrue(kf.translateKey(pk3).toString().contains("SHA-256"));

        Utils.runAndCheckException(() -> kf.translateKey(pk1), InvalidKeyException.class);
        Utils.runAndCheckException(() -> kf5.translateKey(pk2), InvalidKeyException.class);
        Utils.runAndCheckException(() -> kf5.translateKey(pk3), InvalidKeyException.class);

        var kpg = KeyPairGenerator.getInstance("SHA");
        Asserts.assertTrue(kpg.generateKeyPair().getPublic().toString().contains("SHA-256"));

        kpg.initialize(new NamedParameterSpec("ShA-256"));
        Asserts.assertTrue(kpg.generateKeyPair().getPublic().toString().contains("SHA-256"));
        kpg.initialize(new NamedParameterSpec("SHa-512"));
        Asserts.assertTrue(kpg.generateKeyPair().getPublic().toString().contains("SHA-512"));

        var kpg1 = KeyPairGenerator.getInstance("ShA-256");
        Asserts.assertTrue(kpg1.generateKeyPair().getPublic().toString().contains("SHA-256"));

        var kpg2 = KeyPairGenerator.getInstance("sHA-512");
        Asserts.assertTrue(kpg2.generateKeyPair().getPublic().toString().contains("SHA-512"));
    }

    public static class ProviderImpl extends Provider {
        public ProviderImpl() {
            super("P", "1", "...");
            put("KeyFactory.SHA", KF.class.getName());
            put("KeyFactory.SHA-256", KF1.class.getName());
            put("KeyFactory.SHA-512", KF2.class.getName());
            put("KeyPairGenerator.SHA", KPG.class.getName());
            put("KeyPairGenerator.SHA-256", KPG1.class.getName());
            put("KeyPairGenerator.SHA-512", KPG2.class.getName());
        }
    }
    public static class KF extends NamedKeyFactory {
        public KF() {
            super("SHA", "SHA-256", "SHA-512");
        }
    }
    public static class KF1 extends NamedKeyFactory {
        public KF1() {
            super("SHA", "SHA-256");
        }
    }
    public static class KF2 extends NamedKeyFactory {
        public KF2() {
            super("SHA", "SHA-512");
        }
    }
    public static class KPG extends NamedKeyPairGenerator {
        public KPG() {
            super("SHA", "SHA-256", "SHA-512");
        }

        public KPG(String pname) {
            super("SHA", pname);
        }

        @Override
        public byte[][] implGenerateKeyPair(String name, SecureRandom sr) {
            var out = new byte[2][];
            out[0] = new byte[name.endsWith("256") ? 2 : 4];
            out[1] = new byte[name.endsWith("256") ? 2 : 4];
            return out;
        }
    }
    public static class KPG1 extends KPG {
        public KPG1() {
            super("SHA-256");
        }
    }
    public static class KPG2 extends KPG {
        public KPG2() {
            super("SHA-512");
        }
    }
}
