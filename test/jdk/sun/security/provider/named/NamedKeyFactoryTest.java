/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8340327 8347938
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.pkcs
 *          java.base/sun.security.provider
 *          java.base/sun.security.util
 * @library /test/lib
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.security.SeededSecureRandom;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.provider.NamedKeyFactory;
import sun.security.provider.NamedKeyPairGenerator;
import sun.security.util.RawKeySpec;
import sun.security.x509.NamedX509Key;

import java.security.*;
import java.security.spec.*;
import java.util.Arrays;

public class NamedKeyFactoryTest {

    private static final SeededSecureRandom RAND = SeededSecureRandom.one();
    private static final byte[] RAW_SK = RAND.nBytes(16);
    private static final byte[] RAW_PK = RAND.nBytes(16);

    public static void main(String[] args) throws Exception {
        Security.addProvider(new ProviderImpl());

        var g = KeyPairGenerator.getInstance("sHA");
        var g2 = KeyPairGenerator.getInstance("ShA-256");
        var g5 = KeyPairGenerator.getInstance("SHa-512");
        var kf = KeyFactory.getInstance("ShA");
        var kf2 = KeyFactory.getInstance("Sha-256");
        var kf5 = KeyFactory.getInstance("Sha-512");

        checkKeyPair(g.generateKeyPair(), "SHA", "SHA-256");
        checkKeyPair(g2.generateKeyPair(), "SHA", "SHA-256");
        checkKeyPair(g5.generateKeyPair(), "SHA", "SHA-512");

        checkKeyPair(g.generateKeyPair(), "SHA", "SHA-256");
        checkKeyPair(g2.generateKeyPair(), "SHA", "SHA-256");
        checkKeyPair(g5.generateKeyPair(), "SHA", "SHA-512");

        Utils.runAndCheckException(() -> g.initialize(NamedParameterSpec.ED448),
                InvalidAlgorithmParameterException.class); // wrong pname
        Utils.runAndCheckException(() -> g.initialize(new NamedParameterSpec("SHA-384")),
                InvalidAlgorithmParameterException.class); // wrong pname

        Utils.runAndCheckException(() -> g5.initialize(new NamedParameterSpec("SHA-256")),
                InvalidAlgorithmParameterException.class); // diff pname
        g5.initialize(new NamedParameterSpec("SHA-512"));

        g.initialize(new NamedParameterSpec("sHA-512"));
        checkKeyPair(g.generateKeyPair(), "SHA", "SHA-512");
        g.initialize(new NamedParameterSpec("ShA-256"));
        checkKeyPair(g.generateKeyPair(), "SHA", "SHA-256");

        var pk = new NamedX509Key("sHa", "ShA-256", RAW_PK);
        var sk = NamedPKCS8Key.internalCreate("sHa", "SHa-256", RAW_SK, null);
        checkKey(pk, "sHa", "ShA-256");
        checkKey(sk, "sHa", "SHa-256");

        Asserts.assertEquals("X.509", pk.getFormat());
        Asserts.assertEquals("PKCS#8", sk.getFormat());

        var pkSpec = kf.getKeySpec(pk, X509EncodedKeySpec.class);
        var skSpec = kf.getKeySpec(sk, PKCS8EncodedKeySpec.class);

        kf2.getKeySpec(pk, X509EncodedKeySpec.class);
        kf2.getKeySpec(sk, PKCS8EncodedKeySpec.class);
        Utils.runAndCheckException(() -> kf5.getKeySpec(pk, X509EncodedKeySpec.class),
                InvalidKeySpecException.class); // wrong KF
        Utils.runAndCheckException(() -> kf5.getKeySpec(sk, PKCS8EncodedKeySpec.class),
                InvalidKeySpecException.class);
        Utils.runAndCheckException(() -> kf.getKeySpec(pk, PKCS8EncodedKeySpec.class),
                InvalidKeySpecException.class); // wrong KeySpec
        Utils.runAndCheckException(() -> kf.getKeySpec(sk, X509EncodedKeySpec.class),
                InvalidKeySpecException.class);

        checkKey(kf.generatePublic(pkSpec), "SHA", "SHA-256");
        Utils.runAndCheckException(() -> kf.generatePrivate(pkSpec),
                InvalidKeySpecException.class);

        checkKey(kf.generatePrivate(skSpec), "SHA", "SHA-256");
        Utils.runAndCheckException(() -> kf.generatePublic(skSpec),
                InvalidKeySpecException.class);

        checkKey(kf2.generatePrivate(skSpec), "SHA", "SHA-256");
        checkKey(kf2.generatePublic(pkSpec), "SHA", "SHA-256");

        Utils.runAndCheckException(() -> kf5.generatePublic(pkSpec),
                InvalidKeySpecException.class); // wrong KF
        Utils.runAndCheckException(() -> kf5.generatePublic(skSpec),
                InvalidKeySpecException.class);

        // The private RawKeySpec and unnamed RAW EncodedKeySpec
        var prk = kf.getKeySpec(pk, RawKeySpec.class);
        Asserts.assertEqualsByteArray(prk.getKeyArr(), pk.getRawBytes());
        var prk2 = kf.getKeySpec(pk, EncodedKeySpec.class);
        Asserts.assertEquals("RAW", prk2.getFormat());
        Asserts.assertEqualsByteArray(prk.getKeyArr(), prk2.getEncoded());

        Asserts.assertEqualsByteArray(kf2.generatePublic(prk).getEncoded(), pk.getEncoded());
        Utils.runAndCheckException(() -> kf.generatePublic(prk), InvalidKeySpecException.class); // no pname
        Asserts.assertEqualsByteArray(kf2.generatePublic(prk2).getEncoded(), pk.getEncoded());
        Utils.runAndCheckException(() -> kf.generatePublic(prk2), InvalidKeySpecException.class); // no pname

        var srk = kf.getKeySpec(sk, RawKeySpec.class);
        Asserts.assertEqualsByteArray(srk.getKeyArr(), sk.getRawBytes());
        var srk2 = kf.getKeySpec(sk, EncodedKeySpec.class);
        Asserts.assertEquals("RAW", srk2.getFormat());
        Asserts.assertEqualsByteArray(srk2.getEncoded(), sk.getRawBytes());

        checkKey(kf2.generatePrivate(srk), "SHA", "SHA-256");
        Asserts.assertEqualsByteArray(kf2.generatePrivate(srk).getEncoded(), sk.getEncoded());
        Utils.runAndCheckException(() -> kf.generatePrivate(srk), InvalidKeySpecException.class); // no pname
        checkKey(kf2.generatePrivate(srk), "SHA", "SHA-256");
        Asserts.assertEqualsByteArray(kf2.generatePrivate(srk2).getEncoded(), sk.getEncoded());
        Utils.runAndCheckException(() -> kf.generatePrivate(srk2), InvalidKeySpecException.class); // no pname

        var pk1 = new PublicKey() {
            public String getAlgorithm() { return "SHA"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return RAW_PK; }
        };
        var pk2 = new PublicKey() {
            public String getAlgorithm() { return "sHA-256"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return RAW_PK; }
        };
        var pk3 = new PublicKey() {
            public String getAlgorithm() { return "SHA"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return RAW_PK; }
            public AlgorithmParameterSpec getParams() { return new NamedParameterSpec("sHA-256"); }
        };

        checkKey(kf2.translateKey(pk1), "SHA", "SHA-256");
        checkKey(kf.translateKey(pk2), "SHA", "SHA-256");
        checkKey(kf.translateKey(pk3), "SHA", "SHA-256");

        Utils.runAndCheckException(() -> kf.translateKey(pk1), InvalidKeyException.class);
        Utils.runAndCheckException(() -> kf5.translateKey(pk2), InvalidKeyException.class);
        Utils.runAndCheckException(() -> kf5.translateKey(pk3), InvalidKeyException.class);

        var sk1 = new PrivateKey() {
            public String getAlgorithm() { return "SHA"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return RAW_SK; }
        };
        var sk2 = new PrivateKey() {
            public String getAlgorithm() { return "sHA-256"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return RAW_SK; }
        };
        var sk3 = new PrivateKey() {
            public String getAlgorithm() { return "SHA"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return RAW_SK; }
            public AlgorithmParameterSpec getParams() { return new NamedParameterSpec("sHA-256"); }
        };

        checkKey(kf2.translateKey(sk1), "SHA", "SHA-256");
        checkKey(kf.translateKey(sk2), "SHA", "SHA-256");
        checkKey(kf.translateKey(sk3), "SHA", "SHA-256");

        Utils.runAndCheckException(() -> kf.translateKey(sk1), InvalidKeyException.class);
        Utils.runAndCheckException(() -> kf5.translateKey(sk2), InvalidKeyException.class);
        Utils.runAndCheckException(() -> kf5.translateKey(sk3), InvalidKeyException.class);
    }

    static void checkKeyPair(KeyPair kp, String algName, String toString) {
        checkKey(kp.getPrivate(), algName, toString);
        checkKey(kp.getPublic(), algName, toString);
    }

    static void checkKey(Key k, String algName, String pname) {
        Asserts.assertEquals(algName, k.getAlgorithm());
        Asserts.assertTrue(k.toString().contains(pname));
        if (k instanceof AsymmetricKey ak && ak.getParams() instanceof NamedParameterSpec nps) {
            Asserts.assertEquals(pname, nps.getName());
        }
        if (k instanceof NamedPKCS8Key nsk) {
            var raw = nsk.getRawBytes();
            Asserts.assertEqualsByteArray(Arrays.copyOf(RAW_SK, raw.length), raw);
        }
        if (k instanceof NamedX509Key npk) {
            var raw = npk.getRawBytes();
            Asserts.assertEqualsByteArray(Arrays.copyOf(RAW_PK, raw.length), raw);
        }
    }

    // Provider

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

        public KF(String name) {
            super("SHA", name);
        }

        @Override
        protected byte[] implExpand(String pname, byte[] input) throws InvalidKeyException {
            return null;
        }
    }
    public static class KF1 extends KF {
        public KF1() {
            super("SHA-256");
        }
    }
    public static class KF2 extends KF {
        public KF2() {
            super("SHA-512");
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
            out[0] = name.endsWith("256") ? Arrays.copyOf(RAW_PK, 8) : RAW_PK;
            out[1] = name.endsWith("256") ? Arrays.copyOf(RAW_SK, 8) : RAW_SK;
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
