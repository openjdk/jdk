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

import jdk.test.lib.Asserts;
import sun.security.util.InternalPrivateKey;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.List;

/*
 * @test
 * @bug 8305310
 * @library /test/lib
 * @summary Calculate PublicKey from PrivateKey
 * @modules java.base/sun.security.util
 */
public class Correctness {
    public static void main(String[] args) throws Exception {
        for (String alg : List.of("secp256r1", "secp384r1", "secp521r1",
                "X25519", "X448")) {
            KeyPairGenerator g;
            if (alg.startsWith("X")) {
                g = KeyPairGenerator.getInstance(alg);
            } else {
                g = KeyPairGenerator.getInstance("EC");
                g.initialize(new ECGenParameterSpec(alg));
            }
            KeyPair kp = g.generateKeyPair();
            PublicKey p1 = kp.getPublic();
            PrivateKey s1 = kp.getPrivate();

            if (s1 instanceof InternalPrivateKey ipk) {
                PublicKey p2 = ipk.calculatePublicKey();
                Asserts.assertTrue(Arrays.equals(p2.getEncoded(), p1.getEncoded()));
                Asserts.assertEQ(p2.getAlgorithm(), p1.getAlgorithm());
                Asserts.assertEQ(p2.getFormat(), p1.getFormat());
            } else {
                throw new RuntimeException("Not an InternalPrivateKey: "
                        + s1.getClass());
            }
        }
    }
}
