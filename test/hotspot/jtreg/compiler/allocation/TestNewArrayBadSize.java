/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * bug 8288184
 * @summary C2 compilation asserts with "Bad graph detected in compute_lca_of_uses"
 * @run main/othervm -XX:-BackgroundCompilation TestNewArrayBadSize
 */

public class TestNewArrayBadSize {
    long instanceCount;
    int iFld;

    void vMeth(int i, long l) {
        int i1, i19 = -845;
        for (i1 = 5; i1 > 1; i1 -= 2)
            try {
                int ax$0 = i19;
                try {
                    for (Object temp = new byte[i19]; ; i19 = "1".equals("0") ? 2 : 1) {}
                } finally {
                    i19 = ax$0;
                }
            } catch (Throwable ax$3) {
            }
    }

    void mainTest(String[] strArr1) {
        vMeth(iFld, instanceCount);
    }

    public static void main(String[] strArr) {
        TestNewArrayBadSize _instance = new TestNewArrayBadSize();
        for (int i = 0; i < 10_000; ++i) _instance.mainTest(strArr);
    }
}
