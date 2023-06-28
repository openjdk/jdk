/*
 * Copyright (c) 2023, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @bug 8311010
 * @summary C1 array access causes SIGSEGV due to lack of range check
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @run main/othervm -XX:TieredStopAtLevel=1 -XX:+TieredCompilation -XX:+RangeCheckElimination
 *                   -XX:CompileCommand=compileonly,*RangeCheckOverflow.test
 *                   compiler.c1.RangeCheckOverflow
 */

package compiler.c1;

public class RangeCheckOverflow {
    static int b = 0;

    private static void test() {
        int[] a = { 11 } ;
        for (int i = -1; i <= 0; i++) {
            for (int j = -3; j <= 2147483646 * i - 3; j++) {
                b += a[j + 3];
            }
        }
    }
    public static void main(String... args) {
        try {
            test();
        } catch(ArrayIndexOutOfBoundsException e) {
            System.out.println("Expected");
        }
    }
}
