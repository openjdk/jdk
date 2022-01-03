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
 * @bug 8223923
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -Xbatch compiler.unsafe.MismatchedUnsafeAccessTest
 */
package compiler.unsafe;

import jdk.internal.misc.Unsafe;

public class MismatchedUnsafeAccessTest {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static class Test {
        public int x = 0;
        public int y = 0;

        public static final long offsetX;
        public static final long offsetY;

        static {
            try {
                offsetX = UNSAFE.objectFieldOffset(Test.class.getField("x"));
                offsetY = UNSAFE.objectFieldOffset(Test.class.getField("y"));
            } catch (NoSuchFieldException e) {
                throw new InternalError(e);
            }
            // Validate offsets
            if (offsetX >= offsetY || offsetY - offsetX != 4) {
                throw new InternalError("Wrong offsets: " + offsetX + " " + offsetY);
            }
        }
    }

    public static int test(long l) {
        Test a = new Test();
        UNSAFE.putLong(a, Test.offsetX, l); // mismatched access; interferes with subsequent load
        return UNSAFE.getInt(a, Test.offsetY);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            int res = test(-1L);
            if (res != -1) {
                throw new AssertionError("Wrong result: " + res);
            }
        }
        System.out.println("TEST PASSED");
    }
}
