/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8361702
 * @summary C2: assert(is_dominator(compute_early_ctrl(limit, limit_ctrl), pre_end)) failed: node pinned on loop exit test?
  *
 * @run main/othervm -XX:CompileCommand=compileonly,*TestSunkRangeFromPreLoopRCE2*::* -Xbatch TestSunkRangeFromPreLoopRCE2
 * @run main TestSunkRangeFromPreLoopRCE2
 */

public class TestSunkRangeFromPreLoopRCE2 {
    static int iFld;
    static long lArr[] = new long[400];

    public static void main(String[] strArr) {
        for (int i = 0; i < 1000; i++) {
            test();
        }
    }

    static void test() {
        int iArr[] = new int[400];
        for (int i = 8; i < 128; i++) {
            for (int j = 209; j > 9; j--) {
                switch ((j % 5) + 58) {
                    case 58:
                        iArr[i] = 194;
                        break;
                    case 59:
                        iFld = 3;
                    case 62:
                    default:
                        iArr[1] = i;
                }
                for (int k = 2; k > 1; --k) {
                    lArr[k] -= iFld;
                }
            }
        }
    }
}

