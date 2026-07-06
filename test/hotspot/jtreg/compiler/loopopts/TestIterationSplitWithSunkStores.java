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
package compiler.loopopts;

import java.util.Objects;
import jdk.internal.misc.Unsafe;

/*
 * @test
 * @bug 8377163
 * @summary Iteration splitting a counted loop with sunk stores should connect the memory phi of
 *          the post loop to the sunk store in the main loop, not the store at the loop back input
 *          of the corresponding phi of the main loop.
 * @modules java.base/jdk.internal.misc
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation ${test.main.class}
 */
public class TestIterationSplitWithSunkStores {
    private static final Unsafe U = Unsafe.getUnsafe();

    public static void main(String[] args) {
        test1();

        int[] array = new int[1000];
        MyInteger v = new MyInteger(0);
        for (int i = 0; i < 100; i++) {
            test2(array, v, v, v, v);
        }
    }

    private static void test1() {
        int[] dst = new int[5];
        for (long i = 0L; i < 20_000; i++) {
            test1(dst, 1);
            for (int j = 1; j < 5; j++) {
                if (dst[j] != j) {
                    throw new RuntimeException("Bad copy");
                }
            }
        }
    }

    private static void test1(int[] dst, int dstPos) {
        int[] src = new int[4];
        src[0] = new MyInteger(1).v();
        src[1] = 2;
        src[2] = 3;
        src[3] = 4;
        System.arraycopy(src, 0, dst, dstPos, 4);
    }

    private static void test2(int[] array, MyInteger v1, MyInteger v2, MyInteger v3, MyInteger v4) {
        Objects.requireNonNull(array);
        Objects.requireNonNull(v1);
        Objects.requireNonNull(v2);
        Objects.requireNonNull(v3);
        Objects.requireNonNull(v4);

        // Using Unsafe to access the array so that the stores can be sunk without loop
        // predication. This is because store sinking is only attempted during the first and the
        // last loop opt passes, and we need it to occur before iteration splitting.
        for (int i = 0; i < array.length; i++) {
            long elemOffset = Unsafe.ARRAY_INT_BASE_OFFSET + (long) i * Unsafe.ARRAY_INT_INDEX_SCALE;
            int e = U.getInt(array, elemOffset);
            U.putInt(array, elemOffset, e + 1);

            // These 4 stores can all be sunk, but depending on the order in which they are
            // visited, it is most likely that only some of them are actually sunk
            v1.v = e + 1;
            v2.v = e + 2;
            v3.v = e + 3;
            v4.v = e + 4;
        }
    }

    static class MyInteger {
        public int v;

        public MyInteger(int v) {
            for (int i = 0; i < 32; i++) {
                if (i < 10) {
                    this.v = v;
                }
            }
            this.v = v;
        }

        public int v() {
            return v;
        }
    }
}
