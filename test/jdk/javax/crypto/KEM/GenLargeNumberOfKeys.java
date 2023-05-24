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
 * @summary Generate large number of Secret Keys using same KEM
 * @run main/othervm -Djava.security.egd=file:/dev/urandom GenLargeNumberOfKeys
 */
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.KEM;
import javax.crypto.SecretKey;
import java.security.spec.ECGenParameterSpec;

public class GenLargeNumberOfKeys {

    private static final int COUNT = 1000;

    public static void main(String[] args) throws Exception {
        KEM kem = KEM.getInstance("DHKEM");
        testXDH(kem);
        testEC(kem);
    }

    /*
     * X448 produce keysize of 64 bytes which is larger in it's class
     */
    private static void testXDH(KEM kem) throws Exception {
        KeyPair kp = genKeyPair("XDH", "X448");
        Set<SecretKey> generated = new HashSet<>();
        for (int i = 0; i < COUNT; i++) {
            test(kem, kp, generated);
        }
        System.out.println("XDH test Successful");
    }

    /*
     * secp521r1 produce keysize of 64 bytes which is larger in it's class
     */
    private static void testEC(KEM kem) throws Exception {
        KeyPair kp = genKeyPair("EC", "secp521r1");
        Set<SecretKey> generated = new HashSet<>();
        for (int i = 0; i < COUNT; i++) {
            test(kem, kp, generated);
        }
        System.out.println("EC test Successful");
    }

    private static KeyPair genKeyPair(String algo, String curveid) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(algo);
        kpg.initialize(new ECGenParameterSpec(curveid));
        return kpg.generateKeyPair();
    }

    private static void test(KEM kem, KeyPair kp, Set<SecretKey> generated)
            throws Exception {
        KEM.Encapsulator e = kem.newEncapsulator(kp.getPublic());
        KEM.Encapsulated encap = e.encapsulate();
        SecretKey key = encap.key();
        if (generated.contains(key)) {
            throw new RuntimeException("Duplicate key found");
        }
        generated.add(key);
        KEM.Decapsulator d = kem.newDecapsulator(kp.getPrivate());
        SecretKey dKey = d.decapsulate(encap.encapsulation());
        if (!key.equals(dKey)) {
            throw new RuntimeException("Key Mismatched");
        }
    }

}
