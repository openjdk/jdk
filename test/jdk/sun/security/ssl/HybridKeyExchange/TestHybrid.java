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
 * @bug 8379433
 * @summary Test expected DecapsulateException thrown by Hybrid KEM implementation
 * @modules java.base/sun.security.ssl
 * @run main/othervm TestHybrid
*/
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.util.Arrays;
import javax.crypto.DecapsulateException;
import javax.crypto.KEM;

public class TestHybrid {

    public static void main(String[] args) throws Exception {

        Class<?> clazz = Class.forName("sun.security.ssl.HybridProvider");
        Provider p = (Provider) clazz.getField("PROVIDER").get(null);

        String alg = "X25519MLKEM768";
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(alg, p);
        KeyPair kp = kpg.generateKeyPair();

        KEM kem = KEM.getInstance(alg, p);
        KEM.Encapsulator e = kem.newEncapsulator(kp.getPublic());
        KEM.Decapsulator d = kem.newDecapsulator(kp.getPrivate());

        int secretSize = e.secretSize();
        KEM.Encapsulated enc = e.encapsulate();
        byte[] ciphertext = enc.encapsulation();

        byte[] badCiphertext = Arrays.copyOf(ciphertext,
                ciphertext.length - 1);
        try {
            d.decapsulate(badCiphertext, 0, secretSize, "Generic");
            throw new RuntimeException(
                    "Expected DecapsulateException not thrown");
        } catch (DecapsulateException expected) {
            System.out.println("Expected DecapsulateException thrown");
        }
    }
}
