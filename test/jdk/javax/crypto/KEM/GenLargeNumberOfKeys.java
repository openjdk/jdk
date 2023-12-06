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
 * @library /test/lib
 * @run main/othervm -Djava.security.egd=file:/dev/urandom GenLargeNumberOfKeys
 */
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import javax.crypto.KEM;
import javax.crypto.SecretKey;

import java.security.spec.ECGenParameterSpec;
import jdk.test.lib.Asserts;

public class GenLargeNumberOfKeys {

    private static final int COUNT = 1000;

    public static void main(String[] args) throws Exception {
        KEM kem = KEM.getInstance("DHKEM");
        testAlgo(kem, "X448", null);
        testAlgo(kem, "EC", "secp521r1");
    }

    private static void testAlgo(KEM kem, String algo, String curveId) throws Exception {
        KeyPair kp = genKeyPair(algo, curveId);
        KEM.Encapsulator e = kem.newEncapsulator(kp.getPublic());
        KEM.Decapsulator d = kem.newDecapsulator(kp.getPrivate());
        for (int i = 0; i < COUNT; i++) {
            test(e, d);
        }
        System.out.println(algo + ": test Successful");
    }

    private static KeyPair genKeyPair(String algo, String curveId) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(algo);
        if (curveId != null) {
            kpg.initialize(new ECGenParameterSpec(curveId));
        }
        return kpg.generateKeyPair();
    }

    private static void test(KEM.Encapsulator e, KEM.Decapsulator d)
            throws Exception {
        KEM.Encapsulated enc = e.encapsulate();
        SecretKey sk = enc.key();
        Asserts.assertEQ(d.encapsulationSize(), enc.encapsulation().length);
        Asserts.assertEQ(d.secretSize(), enc.key().getEncoded().length);
        Asserts.assertTrue(Arrays.equals(d.decapsulate(enc.encapsulation()).getEncoded(),
                sk.getEncoded()));
        // Repeated calls to encapsulation() on Encapsulated object don't change anything
        Asserts.assertTrue(Arrays.equals(d.decapsulate(enc.encapsulation()).getEncoded(),
                sk.getEncoded()));
        Asserts.assertTrue(Arrays.equals(d.decapsulate(enc.encapsulation()).getEncoded(),
                enc.key().getEncoded()));
    }

}
