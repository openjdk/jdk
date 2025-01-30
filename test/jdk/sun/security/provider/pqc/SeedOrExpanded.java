/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8347938 8347941
 * @library /test/lib
 * @modules java.base/com.sun.crypto.provider
 *          java.base/sun.security.provider
 *          java.base/sun.security.util
 * @summary check key reading compatibility
 * @run main/othervm SeedOrExpanded
 */

import com.sun.crypto.provider.ML_KEM_Impls;
import jdk.test.lib.Asserts;
import jdk.test.lib.security.FixedSecureRandom;
import jdk.test.lib.security.SeededSecureRandom;
import sun.security.provider.ML_DSA_Impls;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.KnownOIDs;
import sun.security.util.ObjectIdentifier;

import javax.crypto.KEM;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

public class SeedOrExpanded {

    static final SeededSecureRandom RAND = SeededSecureRandom.one();

    public static void main(String[] args) throws Exception {
        test("ML-KEM-768");
        test("ML-DSA-65");
    }

    static void test(String alg) throws Exception {

        var seed = RAND.nBytes(alg.contains("ML-KEM") ? 64 : 32);
        var g = KeyPairGenerator.getInstance(alg);

        // Generation

        g.initialize(-1, new FixedSecureRandom(seed));
        var kp = g.generateKeyPair();
        var kseed = kp.getPrivate().getEncoded();

        var ex = alg.contains("ML-KEM")
                ? ML_KEM_Impls.seedToExpandedPrivate(alg, seed)
                : ML_DSA_Impls.seedToExpandedPrivate(alg, seed);
        var kexpanded = new DerOutputStream().write(DerValue.tag_Sequence,
                new DerOutputStream().putInteger(0)
                        .write(DerValue.tag_Sequence, new DerOutputStream()
                                .putOID(ObjectIdentifier.of(KnownOIDs.findMatch(alg))))
                        .putOctetString(ex)).toByteArray();

        // Seed encoding is usually shorter than expanded
        Asserts.assertTrue(kseed.length < kexpanded.length);
        Asserts.assertEqualsByteArray( // ... and encoding ends with seed
                Arrays.copyOfRange(kseed, kseed.length - seed.length, kseed.length),
                seed);

        // Key loading

        var f = KeyFactory.getInstance(alg);
        var sk1 = f.generatePrivate(new PKCS8EncodedKeySpec(kseed));
        var sk2 = f.generatePrivate(new PKCS8EncodedKeySpec(kexpanded));
        // Key factory never tries to reformat keys
        Asserts.assertEqualsByteArray(sk1.getEncoded(), kseed);
        Asserts.assertEqualsByteArray(sk2.getEncoded(), kexpanded);

        // Key using

        if (alg.contains("ML-KEM")) {
            var kem = KEM.getInstance("ML-KEM");
            var e = kem.newEncapsulator(kp.getPublic(), RAND);
            var enc = e.encapsulate();
            var k1 = kem.newDecapsulator(sk1).decapsulate(enc.encapsulation());
            var k2 = kem.newDecapsulator(sk2).decapsulate(enc.encapsulation());
            Asserts.assertEqualsByteArray(k1.getEncoded(), k2.getEncoded());
            Asserts.assertEqualsByteArray(k1.getEncoded(), enc.key().getEncoded());
        } else {
            var s = Signature.getInstance("ML-DSA");
            var rnd = RAND.nBytes(32); // randomness for signature generation
            var msg = RAND.nBytes(20);
            s.initSign(sk1, new FixedSecureRandom(rnd));
            s.update(msg);
            var sig1 = s.sign();
            s.initSign(sk2, new FixedSecureRandom(rnd));
            s.update(msg);
            var sig2 = s.sign();
            Asserts.assertEqualsByteArray(sig1, sig2);
            s.initVerify(kp.getPublic());
            s.update(msg);
            Asserts.assertTrue(s.verify(sig1));
        }
    }
}
