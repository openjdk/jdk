/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8347938
 * @library /test/lib
 * @modules java.base/com.sun.crypto.provider
 *          java.base/sun.security.pkcs
 *          java.base/sun.security.provider
 *          java.base/sun.security.util
 *          java.base/sun.security.x509
 * @summary check key reading compatibility
 * @run main/othervm SeedOrExpanded
 */

import com.sun.crypto.provider.ML_KEM_Impls;
import jdk.test.lib.Asserts;
import jdk.test.lib.security.DerUtils;
import jdk.test.lib.security.FixedSecureRandom;
import jdk.test.lib.security.SeededSecureRandom;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.provider.ML_DSA_Impls;
import sun.security.util.DerValue;

import javax.crypto.KEM;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class SeedOrExpanded {

    static final SeededSecureRandom RAND = SeededSecureRandom.one();

    public static void main(String[] args) throws Exception {
        test("mlkem", "ML-KEM-768");
        test("mldsa", "ML-DSA-65");
    }

    static void test(String type, String alg) throws Exception {

        var seed = RAND.nBytes(alg.contains("ML-KEM") ? 64 : 32);
        var g = KeyPairGenerator.getInstance(alg);

        // Generation

        g.initialize(-1, new FixedSecureRandom(seed));
        var kp = g.generateKeyPair();
        var pk = kp.getPublic();
        var kDefault = kp.getPrivate();

        // Property value is case-insensitive
        System.setProperty("jdk." + type + ".pkcs8.encoding", "SEED");
        g.initialize(-1, new FixedSecureRandom(seed));
        var kSeed = g.generateKeyPair().getPrivate();
        System.setProperty("jdk." + type + ".pkcs8.encoding", "expandedkey");
        g.initialize(-1, new FixedSecureRandom(seed));
        var kExpanded = g.generateKeyPair().getPrivate();
        System.setProperty("jdk." + type + ".pkcs8.encoding", "boTH");
        g.initialize(-1, new FixedSecureRandom(seed));
        var kBoth = g.generateKeyPair().getPrivate();

        // Invalid property value
        System.setProperty("jdk." + type + ".pkcs8.encoding", "bogus");
        g.initialize(-1, new FixedSecureRandom(seed));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> g.generateKeyPair().getPrivate());

        byte[] kExpandedEncoded = kExpanded.getEncoded();
        byte[] kSeedEncoded = kSeed.getEncoded();
        byte[] kBothEncoded = kBoth.getEncoded();

        // Ensure tags match the CHOICE definition
        Asserts.assertEquals((byte) 0x80, DerUtils.innerDerValue(kSeedEncoded, "2c").tag);
        Asserts.assertEquals((byte) 0x04, DerUtils.innerDerValue(kExpandedEncoded, "2c").tag);
        Asserts.assertEquals((byte) 0x30, DerUtils.innerDerValue(kBothEncoded, "2c").tag);

        byte[] seedData = DerUtils.innerDerValue(kSeedEncoded, "2c")
                .withTag(DerValue.tag_OctetString).getOctetString();
        byte[] expandedData = DerUtils.innerDerValue(kExpandedEncoded, "2c").getOctetString();

        Asserts.assertEqualsByteArray(seed, seedData);
        Asserts.assertEqualsByteArray(
                seedData,
                DerUtils.innerDerValue(kBothEncoded, "2c0").getOctetString());
        Asserts.assertEqualsByteArray(
                expandedData,
                DerUtils.innerDerValue(kBothEncoded, "2c1").getOctetString());

        // Ensure seedToExpanded correctly called
        if (alg.contains("ML-KEM")) {
            Asserts.assertEqualsByteArray(expandedData,
                    ML_KEM_Impls.seedToExpanded(alg, seedData));
        } else {
            Asserts.assertEqualsByteArray(expandedData,
                    ML_DSA_Impls.seedToExpanded(alg, seedData));
        }

        test(alg, pk, kSeed);
        test(alg, pk, kExpanded);
        test(alg, pk, kBoth);

        var kf = KeyFactory.getInstance(alg);

        System.setProperty("jdk." + type + ".pkcs8.encoding", "seed");
        Asserts.assertEqualsByteArray(
                test(alg, pk, kf.translateKey(kBoth)).getEncoded(),
                kSeedEncoded);
        Asserts.assertTrue(kf.translateKey(kSeed) == kSeed);
        Asserts.assertThrows(InvalidKeyException.class, () -> kf.translateKey(kExpanded));

        System.setProperty("jdk." + type + ".pkcs8.encoding", "expandedkey");
        Asserts.assertEqualsByteArray(
                test(alg, pk, kf.translateKey(kBoth)).getEncoded(),
                kExpandedEncoded);
        Asserts.assertEqualsByteArray(
                test(alg, pk, kf.translateKey(kSeed)).getEncoded(),
                kExpandedEncoded);
        Asserts.assertTrue(kf.translateKey(kExpanded) == kExpanded);

        System.setProperty("jdk." + type + ".pkcs8.encoding", "both");
        Asserts.assertTrue(kf.translateKey(kBoth) == kBoth);
        Asserts.assertEqualsByteArray(
                test(alg, pk, kf.translateKey(kSeed)).getEncoded(),
                kBothEncoded);
        Asserts.assertThrows(InvalidKeyException.class, () -> kf.translateKey(kExpanded));

        // The following makes sure key is not mistakenly cleaned during
        // translations.
        var xk = new PrivateKey() {
            public String getAlgorithm() { return alg; }
            public String getFormat() { return "PKCS#8"; }
            public byte[] getEncoded() { return kBothEncoded.clone(); }
        };
        test(alg, pk, xk);
        var xk2 = (PrivateKey) kf.translateKey(xk);
        test(alg, pk, xk2);
        test(alg, pk, xk);
    }

    static PrivateKey test(String alg, PublicKey pk, Key k) throws Exception {
        var sk = (PrivateKey) k;
        if (alg.contains("ML-KEM")) {
            var kem = KEM.getInstance("ML-KEM");
            var e = kem.newEncapsulator(pk, RAND);
            var enc = e.encapsulate();
            var k1 = kem.newDecapsulator(sk).decapsulate(enc.encapsulation());
            Asserts.assertEqualsByteArray(k1.getEncoded(), enc.key().getEncoded());
            if (k instanceof NamedPKCS8Key npk) {
                Asserts.assertEqualsByteArray(
                        ML_KEM_Impls.privKeyToPubKey(npk).getEncoded(), pk.getEncoded());
            }
        } else {
            var s = Signature.getInstance("ML-DSA");
            var rnd = RAND.nBytes(32); // randomness for signature generation
            var msg = RAND.nBytes(20);
            s.initSign(sk, new FixedSecureRandom(rnd));
            s.update(msg);
            var sig1 = s.sign();
            s.initVerify(pk);
            s.update(msg);
            Asserts.assertTrue(s.verify(sig1));
            if (k instanceof NamedPKCS8Key npk) {
                Asserts.assertEqualsByteArray(
                        ML_DSA_Impls.privKeyToPubKey(npk).getEncoded(), pk.getEncoded());
            }
        }
        return sk;
    }
}
