/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8357781
 * @summary Triggered a stack overflow in PhaseCFG::set_next_call due to a legitimately big (mostly deep and not wide) graph.
 *
 * @run main/othervm -Xcomp -XX:LoopUnrollLimit=8192 -XX:CompileCommand=compileonly,StackOverflowInSetNextCall::test StackOverflowInSetNextCall
 * @run main StackOverflowInSetNextCall
 *
 */

import java.util.Arrays;

public class StackOverflowInSetNextCall {
    public static Double d;

    static Long[] arr = new Long[500];

    public static double test() {
        long x = 0;
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 100; j++) {
                if (!Arrays.equals(arr, arr)) {
                    for (int k = 0; k < 100; k++) {
                        for (int l = 0; l < 100; ++l) {
                            x -= (j - k) * x;
                            for (int m = 0; m < 100; m++) {
                                x += x;
                                x += x - (x - arr[i]);
                            }
                        }
                        d = 0.0d;
                    }
                }
            }
        }
        return x;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 400; ++i) {
            test();
        }
    }
}
