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

/*
 * @test
 * @bug 8305189
 * @summary C2 failed "assert(_outcnt==1) failed: not unique"
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:CompileOnly=TestZeroTripGuardShared::* TestZeroTripGuardShared
 */

import jdk.test.lib.Utils;

public class TestZeroTripGuardShared {
    static double dFld1;
    static int iArrFld[];

    public static void main(String[] strArr) throws Exception {
        Thread thread = new Thread() {
                public void run() {
                    test();
                }
            };
        // Give thread some time to trigger compilation
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(500));
    }

    static void test() {
        int i5 = 2, i8 = 2, i9, i11, i12;
        while (i8 < 5) {
            for (i9 = i8; 6 > i9; i9++) {
                for (i11 = 1; i11 > i9; i11 -= 2) {
                    try {
                        i12 = iArrFld[i11];
                    } catch (ArithmeticException a_e) {
                    }
                    i5 += dFld1;
                }
            }
        }
    }
}
