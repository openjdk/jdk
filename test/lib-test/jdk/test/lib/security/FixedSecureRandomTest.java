/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Utils;
import jdk.test.lib.security.FixedSecureRandom;

/*
 * @test
 * @library /test/lib
 * @summary ensure FixedSecureRandom works as expected
 */
public class FixedSecureRandomTest {
    public static void main(String[] args) throws Exception {
        var fsr = new FixedSecureRandom(new byte[] {1, 2, 3},
                new byte[] {4, 5, 6});
        var b1 = new byte[2];
        fsr.nextBytes(b1);
        Asserts.assertEqualsByteArray(new byte[] {1, 2}, b1);
        Asserts.assertTrue(fsr.hasRemaining());
        fsr.nextBytes(b1);
        Asserts.assertEqualsByteArray(new byte[] {3, 4}, b1);
        Asserts.assertTrue(fsr.hasRemaining());
        fsr.nextBytes(b1);
        Asserts.assertEqualsByteArray(new byte[] {5, 6}, b1);
        Asserts.assertFalse(fsr.hasRemaining());
        Utils.runAndCheckException(() -> fsr.nextBytes(b1),
                IllegalStateException.class);
    }
}
