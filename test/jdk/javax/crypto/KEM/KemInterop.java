/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297878
 * @summary KEM using KeyPair generated through SunPKCS11 provider using NSS
 * @library /test/lib ../../../sun/security/pkcs11
 */
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.KEM;
import javax.crypto.SecretKey;
import jdk.test.lib.Asserts;

public class KemInterop extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new KemInterop(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        test("EC", "secp256r1", p);
        test("EC", "secp384r1", p);
        test("EC", "secp521r1", p);
        test("X25519", null, p);
        test("X448", null, p);
        test("XDH", null, p);
    }

    private static void test(String algo, String curveId, Provider p) throws Exception {

        @FunctionalInterface
        interface KemTest<KP, SR> {

            void test(KP kp, SR scr);
        }

        KemTest<KeyPair, SecureRandom> kemWithPKCSKeys = (kpr, scr) -> {
            try {
                KEM kem = KEM.getInstance("DHKEM", "SunJCE");
                KEM.Encapsulator encT = encapsulator(kem, kpr.getPublic(), scr);
                KEM.Encapsulator encT1 = encapsulator(kem, kpr.getPublic(), scr);
                KEM.Encapsulated enc = encT.encapsulate();
                KEM.Encapsulated enc1 = encT.encapsulate();
                KEM.Encapsulated enc2 = encT1.encapsulate();

                Asserts.assertTrue(Arrays.equals(enc.key().getEncoded(), enc.key().getEncoded()));
                Asserts.assertTrue(Arrays.equals(enc.encapsulation(), enc.encapsulation()));

                Asserts.assertFalse(Arrays.equals(enc.key().getEncoded(), enc1.key().getEncoded()));
                Asserts.assertFalse(Arrays.equals(enc.encapsulation(), enc1.encapsulation()));

                Asserts.assertFalse(Arrays.equals(enc.key().getEncoded(), enc2.key().getEncoded()));
                Asserts.assertFalse(Arrays.equals(enc.encapsulation(), enc2.encapsulation()));

                KEM.Decapsulator decT = kem.newDecapsulator(kpr.getPrivate());
                SecretKey dsk = decT.decapsulate(enc.encapsulation());
                Asserts.assertTrue(Arrays.equals(dsk.getEncoded(), enc.key().getEncoded()));

                Asserts.assertEQ(encT.encapsulationSize(), enc.encapsulation().length);
                Asserts.assertEQ(encT.encapsulationSize(), decT.encapsulationSize());
                Asserts.assertEQ(encT.secretSize(), enc.key().getEncoded().length);
                Asserts.assertEQ(encT.secretSize(), decT.secretSize());
                Asserts.assertEQ(decT.secretSize(), dsk.getEncoded().length);
                Asserts.assertEQ(decT.secretSize(),
                        decT.decapsulate(enc.encapsulation()).getEncoded().length);
                Asserts.assertEQ(decT.decapsulate(enc.encapsulation()).getEncoded().length,
                        enc.key().getEncoded().length);

                KEM.Encapsulated enc3 = encT.encapsulate(0, encT.secretSize(), "AES");
                KEM.Decapsulator decT1 = kem.newDecapsulator(kpr.getPrivate());
                SecretKey dsk1 = decT1.decapsulate(enc3.encapsulation(), 0, decT1.secretSize(), "AES");
                Asserts.assertTrue(Arrays.equals(dsk1.getEncoded(), enc3.key().getEncoded()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            System.out.println("Success");
        };

        KeyPair kp = null;
        SecureRandom sr = null;
        try {
            System.out.print("Algo-" + algo + ":" + "Curve-" + curveId + ":");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(algo, p);
            try {
                sr = SecureRandom.getInstance("PKCS11");
            } catch (NoSuchAlgorithmException e) {
                // Get default SecureRandom incase PKCS11 not found
                sr = new SecureRandom();
            }
            if (curveId != null) {
                kpg.initialize(new ECGenParameterSpec(curveId), sr);
            }
            kp = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Unsupported. Test execution Ignored");
            return;
        }

        kemWithPKCSKeys.test(kp, null);
        kemWithPKCSKeys.test(kp, sr);
    }

    private static KEM.Encapsulator encapsulator(KEM kem, PublicKey pk, SecureRandom sr)
            throws Exception {
        if (sr == null) {
            return kem.newEncapsulator(pk);
        } else {
            return kem.newEncapsulator(pk, sr);
        }
    }
}
