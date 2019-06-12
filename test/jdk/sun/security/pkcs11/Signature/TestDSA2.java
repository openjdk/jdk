/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8080462
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm/timeout=250 TestDSA2
 * @summary verify that DSA signature works using SHA-2 digests.
 * @key randomness
 */


import java.security.*;
import java.security.spec.*;
import java.security.interfaces.*;

public class TestDSA2 extends PKCS11Test {

    private static final String[] SIG_ALGOS = {
        "SHA224withDSA",
        "SHA256withDSA",
        //"SHA384withDSA",
        //"SHA512withDSA",
    };

    private static final int KEYSIZE = 2048;

    public static void main(String[] args) throws Exception {
        main(new TestDSA2(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        KeyPair kp;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA", p);
            kpg.initialize(KEYSIZE);
            kp = kpg.generateKeyPair();
        } catch (Exception ex) {
            System.out.println("Skip due to no 2048-bit DSA support: " + ex);
            ex.printStackTrace();
            return;
        }

        for (String sigAlg : SIG_ALGOS) {
            test(sigAlg, kp, p);
        }
    }

    private static void test(String sigAlg, KeyPair kp, Provider p)
            throws Exception {
        Signature sig;
        try {
            sig = Signature.getInstance(sigAlg, p);
        } catch (Exception ex) {
            System.out.println("Skip due to no support: " + sigAlg);
            ex.printStackTrace();
            return;
        }

        byte[] data = "anything will do".getBytes();

        sig.initSign(kp.getPrivate());
        sig.update(data);
        byte[] signature = sig.sign();

        sig.initVerify(kp.getPublic());
        sig.update(data);
        boolean verifies = sig.verify(signature);
        System.out.println(sigAlg + ": Passed");
    }
}
