/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8338587
 * @summary Ensure squeeze and digest always have the same output
 * @library /test/lib
 * @modules java.base/sun.security.provider
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.security.SeededSecureRandom;
import sun.security.provider.SHA3;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class SHAKEsqueeze {
    public static void main(String[] args) throws Exception {
        resetFix();
        random();
    }

    static void resetFix() throws Exception {
        var s = new SHA3.SHAKE256();
        var d1 = s.squeeze(10);
        s.reset();
        Asserts.assertEqualsByteArray(d1, s.squeeze(10));
    }

    static void random() throws Exception {
        var r = SeededSecureRandom.one();
        var atlast = 0;
        // Random test on SHAKE
        for (var i = 0; i < 1_000_000; i++) {
            var s = new SHA3.SHAKE256(0);
            var in = new ByteArrayOutputStream();
            while (r.nextBoolean()) {
                var b = r.nBytes(r.nextInt(200));
                if (b.length > 0 && r.nextBoolean()) {
                    // Test update(b)
                    s.update(b[0]);
                    in.write(b[0]);
                } else if (r.nextBoolean()) {
                    // Test update(byte[])
                    s.update(b);
                    in.write(b);
                } else {
                    // Test update(byte[], offset, len)
                    var prepend = r.nextInt(100);
                    var append = r.nextInt(100);
                    var bb = new byte[prepend + b.length + append];
                    r.nextBytes(bb);
                    System.arraycopy(b, 0, bb, prepend, b.length);
                    s.update(bb, prepend, b.length);
                    in.write(b);
                }
            }

            // Squeeze for multiple times
            var out = new ByteArrayOutputStream();
            do {
                var n = r.nextInt(200);
                out.write(s.squeeze(n));
            } while (out.size() == 0 || r.nextBoolean());
            var b1 = out.toByteArray();

            // Digest for one time
            var s2 = new SHA3.SHAKE256(b1.length);
            s2.update(in.toByteArray());
            var b2 = s2.digest();

            atlast = Arrays.hashCode(b2) * 17 + atlast;
            Asserts.assertEqualsByteArray(b1, b2);
        }
        // Just to provide a visual clue to show that the same
        // SeededSecureRandom seed results in same final result
        // so that the test can be exactly reproduced.
        System.out.println("Final hash: " + atlast);
    }
}
