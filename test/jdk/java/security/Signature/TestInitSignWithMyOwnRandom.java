/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4716321
 * @library /test/lib
 * @summary Ensure the random source supplied in
 * Signature.initSign(PrivateKey, SecureRandom) is used.
 * @run main TestInitSignWithMyOwnRandom DSA 512
 * @run main TestInitSignWithMyOwnRandom SHA256withDSA 2048
 */
import java.security.*;
import jdk.test.lib.security.SecurityUtils;

public class TestInitSignWithMyOwnRandom {

    public static void main(String[] args) throws Exception {
        // any signature implementation will do as long as
        // it needs a random source
        Provider p = Security.getProvider(
                System.getProperty("test.provider.name", "SUN"));
        String kpgAlgorithm = "DSA";
        int keySize = Integer.parseInt(args[1]);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(kpgAlgorithm, p);
        kpg.initialize(keySize);
        KeyPair kp = kpg.generateKeyPair();
        TestRandomSource rand = new TestRandomSource();
        String signAlgo = args[0];
        Signature sig = Signature.getInstance(signAlgo, p);
        sig.initSign(kp.getPrivate(), rand);
        sig.update(new byte[20]);
        sig.sign();
        if (rand.isUsed()) {
            System.out.println("Custom random source is used.");
        } else {
            throw new Exception("Custom random source is not used");
        }
    }
}

class TestRandomSource extends SecureRandom {

    int count = 0;

    @Override
    public void nextBytes(byte[] rs) {
        count++;
    }

    public boolean isUsed() {
        return (count != 0);
    }
}
