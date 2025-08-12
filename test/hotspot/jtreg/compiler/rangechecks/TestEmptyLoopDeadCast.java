/*
 * Copyright (c) 2024, Red Hat and/or its affiliates. All rights reserved.
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
 * @bug 8335393
 * @summary C2: assert(!had_error) failed: bad dominance
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:-UseLoopPredicate
 *                   -XX:LoopMaxUnroll=0 TestEmptyLoopDeadCast
 */

public class TestEmptyLoopDeadCast {
    public static void main(String[] args) {
        int[] array = new int[100];
        for (int i = 0; i < 20_000; i++) {
            test1Helper(1, 101, array);
            test1(0, array);
            test2Helper(0, -101, array);
            test2(0, array);
        }
    }

    private static int test1(int start, int[] array) {
        return test1Helper(start, 0, array);
    }

    private static int test1Helper(int start, int stop, int[] array) {
        if (array == null) {
        }
        int v = 0;
        for (int i = start; i < stop; i++) {
            v = array[i - 1];
        }
        return v;
    }

    private static int test2(int start, int[] array) {
        return test2Helper(start, -1, array);
    }

    private static int test2Helper(int start, int stop, int[] array) {
        if (array == null) {
        }
        int v = 0;
        for (int i = start-1; i > stop; i--) {
            v = array[-1 - i];
        }
        return v;
    }

}
