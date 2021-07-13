/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4856966 8242332
 * @summary
 * @author Andreas Sterbenz
 * @library /test/lib ..
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm ReinitDigest
 * @run main/othervm -Djava.security.manager=allow ReinitDigest sm
 */

import java.security.MessageDigest;
import java.security.Provider;
import java.util.Arrays;
import java.util.Random;
import java.util.List;

public class ReinitDigest extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new ReinitDigest(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        List<String> ALGS = getSupportedAlgorithms("MessageDigest",
                "SHA", p);
        Random r = new Random();
        byte[] data1 = new byte[10 * 1024];
        byte[] data2 = new byte[10 * 1024];
        r.nextBytes(data1);
        r.nextBytes(data2);

        boolean success = true;
        for (String alg : ALGS) {
            try {
                doTest(alg, p, data1, data2);
            } catch (Exception e) {
                System.out.println("Unexpected exception: " + e);
                e.printStackTrace();
                success = false;
            }
        }

        if (!success) {
            throw new RuntimeException("Test failed");
        }
        System.out.println("All tests passed");
    }

    private void doTest(String alg, Provider p, byte[] data1, byte[] data2)
            throws Exception {
        System.out.println("Testing " + alg);
        MessageDigest md = MessageDigest.getInstance(alg, "SUN");
        byte[] d1 = md.digest(data1);
        md = MessageDigest.getInstance(alg, p);
        byte[] d2 = md.digest(data1);
        check(d1, d2);
        byte[] d3 = md.digest(data1);
        check(d1, d3);
        md.update(data2);
        md.update((byte)0);
        md.reset();
        byte[] d4 = md.digest(data1);
        check(d1, d4);
    }

    private static void check(byte[] d1, byte[] d2) throws Exception {
        if (Arrays.equals(d1, d2) == false) {
            throw new RuntimeException("Digest mismatch");
        }
    }
}
