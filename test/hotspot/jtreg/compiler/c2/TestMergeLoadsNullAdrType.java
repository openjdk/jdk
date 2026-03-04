/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All rights reserved.
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
 * or visit www.oracle.com if you need any additional information or have
 * any questions.
 */

package compiler.c2;

/*
 * @test
 * @bug 8345845
 * @summary Test merge loads, when "adr_type() == nullptr" because of TOP somewhere in the address.
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.c2.TestMergeLoadsNullAdrType::test
 *                   -XX:-TieredCompilation -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:+StressCCP
 *                   -XX:RepeatCompilation=1000
 *                   compiler.c2.TestMergeLoadsNullAdrType
 * @run main compiler.c2.TestMergeLoadsNullAdrType
 */

public class TestMergeLoadsNullAdrType {
    static int arr[] = new int[100];

    static int test() {
        boolean b = false;
        int result = 0;
        for (int k = 269; k > 10; --k) {
            b = b;
            int j = 6;
            while ((j -= 3) > 0) {
                if (b) {
                } else {
                    // Load operations that may have adr_type() == nullptr
                    result += arr[j];
                    result += (arr[j] >> 8);
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        // Initialize array
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i;
        }

        // Capture interpreter result
        int gold = test();

        // Repeat until compilation
        for (int i = 0; i < 10_000; i++) {
            int result = test();
            if (result != gold) {
                throw new RuntimeException("ERROR: wrong result: " + result + " != " + gold);
            }
        }
        System.out.println("PASSED");
    }
}
