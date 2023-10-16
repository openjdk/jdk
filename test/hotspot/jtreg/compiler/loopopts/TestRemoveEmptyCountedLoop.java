/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289748
 * @summary SIGFPE caused by C2 IdealLoopTree::do_remove_empty_loop
 * @key stress randomness
 *
 * @run main/othervm -XX:-TieredCompilation -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestRemoveEmptyCountedLoop::test*
 *                   compiler.loopopts.TestRemoveEmptyCountedLoop
 * @run main/othervm -XX:-TieredCompilation -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=2160808391
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestRemoveEmptyCountedLoop::test*
 *                   compiler.loopopts.TestRemoveEmptyCountedLoop
 */

package compiler.loopopts;

public class TestRemoveEmptyCountedLoop {

    public static void test1() {
        int k = 3;
        for (int i=9; i>0; i--) {
            for (int j=2; j<i; j++) {
                k = k;
                k = (1 % j);
            }
        }
    }

    public static void test2() {
        int k = 3;
        for (int i=9; i>0; i--) {
            int j = 2;
            do {
                try {
                    k = k;
                    k = (1 % j);
                } catch (Exception e) {}
            } while (++j < i);
        }
    }

    public static void main(String[] args) {
        test1();
        test2();
        System.out.println("Test passed.");
    }
}
