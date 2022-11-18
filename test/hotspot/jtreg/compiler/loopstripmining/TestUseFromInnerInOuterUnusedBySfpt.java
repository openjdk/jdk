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

/**
 * @test
 * @bug 8295788
 * @summary C2 compilation hits "assert((mode == ControlAroundStripMined && use == sfpt) || !use->is_reachable_from_root()) failed: missed a node"
 *
 * @run main/othervm -Xcomp -XX:CompileOnly=TestUseFromInnerInOuterUnusedBySfpt TestUseFromInnerInOuterUnusedBySfpt
 *
 */

public class TestUseFromInnerInOuterUnusedBySfpt {

    public static final int N = 400;

    public static void dMeth(long l, int i5, int i6) {

        int i7=14, i8=-14, i9=7476, i11=0;
        long lArr[]=new long[N];

        for (i7 = 3; i7 < 177; i7++) {
            lArr[i7 + 1] >>= l;
            l -= i8;
            i6 = (int)l;
        }
        for (i9 = 15; i9 < 356; i9 += 3) {
            i11 = 14;
            do {
                i5 |= i6;
            } while (--i11 > 0);
        }
    }

    public static void main(String[] strArr) {
        TestUseFromInnerInOuterUnusedBySfpt _instance = new TestUseFromInnerInOuterUnusedBySfpt();
        for (int i = 0; i < 10; i++) {
            _instance.dMeth(-12L, -50242, 20);
        }
    }
}
