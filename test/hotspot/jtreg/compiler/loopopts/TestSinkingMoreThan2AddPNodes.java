/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8315377
 * @requires vm.compiler2.enabled
 * @summary C2: assert(u->find_out_with(Op_AddP) == nullptr) failed: more than 2 chained AddP nodes?
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileOnly=TestSinkingMoreThan2AddPNodes::test TestSinkingMoreThan2AddPNodes
 *
 */

import jdk.test.lib.Utils;

public class TestSinkingMoreThan2AddPNodes {
    public static void main(String[] strArr) throws Exception {
        Thread t = new Thread(new Runnable() {
                public void run() {
                    test();
                }
            });
        t.setDaemon(true);
        t.start();
        Thread.sleep(Utils.adjustTimeout(500));
    }

    static void test() {
        double dArr[] = new double[10];
        int i4 = 5, i11, i12 = 2, iArr[] = new int[400];
        long l1;
        byte by1 = 0;
        short s1 = 8;

        for (int i = 0; i < iArr.length; i++) {
            iArr[i] = (i % 2 == 0) ? 23 : 34;
        }

        for (i11 = 10; i11 > 9; ) {
            l1 = 1;
            do {
                try {
                    i4 = 6 % i4;
                    i12 = iArr[(int) l1];
                } catch (ArithmeticException a_e) {
                }
                by1 += 8;
                iArr = iArr;
            } while (++l1 < 11);
        }

        long meth_res = i12;
    }
}

