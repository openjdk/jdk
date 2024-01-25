/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8271820
 * @run testng/othervm MethodArityLimit
 * @summary Method exceeds the method handle arity limit (255).
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class MethodArityLimit {
    @Test
    public void testArityLimit() throws Throwable {
        Method m = this.getClass().getMethod("f", long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, long.class,
                long.class, long.class, long.class, long.class, long.class, int.class);

        long resultViaMethod = (long) m.invoke(null, 0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L,
                8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L,
                23L, 24L, 25L, 26L, 27L, 28L, 29L, 30L, 31L, 32L, 33L, 34L, 35L, 36L,
                37L, 38L, 39L, 40L, 41L, 42L, 43L, 44L, 45L, 46L, 47L, 48L, 49L, 50L,
                51L, 52L, 53L, 54L, 55L, 56L, 57L, 58L, 59L, 60L, 61L, 62L, 63L, 64L,
                65L, 66L, 67L, 68L, 69L, 70L, 71L, 72L, 73L, 74L, 75L, 76L, 77L, 78L,
                79L, 80L, 81L, 82L, 83L, 84L, 85L, 86L, 87L, 88L, 89L, 90L, 91L, 92L,
                93L, 94L, 95L, 96L, 97L, 98L, 99L, 100L, 101L, 102L, 103L, 104L, 105L,
                106L, 107L, 108L, 109L, 110L, 111L, 112L, 113L, 114L, 115L, 116L, 117L,
                118L, 119L, 120L, 121L, 122L, 123L, 124L, 125L, 126L, 127);

        assertEquals(resultViaMethod, 127);

        try {
            MethodHandle mh = MethodHandles.lookup().unreflect(m);
            fail("should fail in creating the method handle");
        } catch (IllegalArgumentException e) {}
    }

    public static long f(long a0, long a1, long a2, long a3, long a4, long a5,
                         long a6, long a7, long a8, long a9, long a10, long a11, long a12,
                         long a13, long a14, long a15, long a16, long a17, long a18, long a19,
                         long a20, long a21, long a22, long a23, long a24, long a25, long a26,
                         long a27, long a28, long a29, long a30, long a31, long a32, long a33,
                         long a34, long a35, long a36, long a37, long a38, long a39, long a40,
                         long a41, long a42, long a43, long a44, long a45, long a46, long a47,
                         long a48, long a49, long a50, long a51, long a52, long a53, long a54,
                         long a55, long a56, long a57, long a58, long a59, long a60, long a61,
                         long a62, long a63, long a64, long a65, long a66, long a67, long a68,
                         long a69, long a70, long a71, long a72, long a73, long a74, long a75,
                         long a76, long a77, long a78, long a79, long a80, long a81, long a82,
                         long a83, long a84, long a85, long a86, long a87, long a88, long a89,
                         long a90, long a91, long a92, long a93, long a94, long a95, long a96,
                         long a97, long a98, long a99, long a100, long a101, long a102, long a103,
                         long a104, long a105, long a106, long a107, long a108, long a109,
                         long a110, long a111, long a112, long a113, long a114, long a115,
                         long a116, long a117, long a118, long a119, long a120, long a121,
                         long a122, long a123, long a124, long a125, long a126, int a127) {
        return a127;
    }
}
