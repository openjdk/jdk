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

package compiler.igvn;

/*
 * @test id=reduced1
 * @bug 8396171
 * @summary C2: segfault while adding node to IGVN worklist.
 * @requires vm.cpu.features ~= ".*avx.*"
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:UseAVX=2
 *                   -XX:CompileCommand=compileonly,compiler.igvn.TestReduced1::test
 *                   ${test.main.class}
 */

/*
 * @test id=reduced2
 * @bug 8396171
 * @summary C2: segfault while adding node to IGVN worklist.
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.igvn.TestReduced2::test
 *                   ${test.main.class}
 */

public class TestVectorReassociationIGVN {
    public static void main(String[] args) {
        TestReduced1.run();
        TestReduced2.run();
    }
}

class TestReduced1 extends TestVectorReassociationIGVN {
    static void run() {
        for (int i = 0; i < 10_000; i++) {
            test();
        }
    }

    static void test() {
        long arr[] = new long[74];
        for (int i = 6; i < 67; i++) {
            for (int j = 1; j < 5; ++j) {
                arr[i] ^= j;
                arr[j] -= 1.234;
            }
        }
    }
}

class TestReduced2 extends TestVectorReassociationIGVN {
    static void run() {
        for (int i = 0; i < 10; i++) {
            test();
        }
    }

    static void test() {
        int i5 = -89;
        long l2 = -8L;
        long lArr[] = new long[400];
        for (int i = 0; i < 400; i++) {
            lArr[i] = 189L;
        }
        for (int i12 = 6; i12 < 367; i12++) {
            i5 += i12;
            for (int i14 = 1; i14 < 5; ++i14) {
                lArr[i12 - 1] ^= i14;
                lArr[i14] -= l2;
            }
        }
    }
}
