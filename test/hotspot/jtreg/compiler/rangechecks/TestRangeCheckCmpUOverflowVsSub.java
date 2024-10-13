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

/*
 * @test
 * @bug 8299959
 * @summary In CmpU::Value, the sub computation may be narrower than the overflow computation.
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+StressCCP -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.rangechecks.TestRangeCheckCmpUOverflowVsSub::test
 *                   -XX:RepeatCompilation=50
 *                   compiler.rangechecks.TestRangeCheckCmpUOverflowVsSub
*/

package compiler.rangechecks;

public class TestRangeCheckCmpUOverflowVsSub {
    static int arr[] = new int[400];

    public static void main(String[] strArr) {
        for (int i = 0; i < 10; i++) {
            test(); // repeat for multiple compilations
        }
    }

    static void test() {
        for(int i = 0; i < 50_000; i++) {} //empty loop - trigger OSR faster
        int val;
        int zero = arr[5];
        int i = 1;
        do {
            for (int j = 1; j < 3; j++) {
                for (int k = 2; k > i; k -= 3) {
                    try {
                        val = arr[i + 1] % k;
                        val = arr[i - 1] % zero;
                        val = arr[k - 1];
                    } catch (ArithmeticException e) {} // catch div by zero
                }
            }
        } while (++i < 3);
    }
}

