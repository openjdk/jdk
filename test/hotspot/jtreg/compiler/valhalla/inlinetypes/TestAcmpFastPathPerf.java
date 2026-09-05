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

/*
 * @test
 * @summary Test acmp fast path with value classes
 * @requires vm.flagless
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @run main/othervm/timeout=30 -Xbatch
 *                              -XX:CompileCommand=CompileOnly,${test.main.class}::test
 *                              ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

// In debug build, it's about 1s with acmp fast path, ~ 4 min without, and < 0.5s without Valhalla
// In product build, it's resp. 1s, 1 min, < 0.5s.
public class TestAcmpFastPathPerf {
    static final int BIG_SIZE = 10_000;

    public static void main(String[] args) {
        // First, let's ruin profiling for ::test, with a mix of value classes, identity classes, null,
        // giving successful and failed tests.
        Object[] l = new Object[]{null, "abc", 1, (short)1};
        Object[] r = new Object[]{2, "abc", 1, null};
        for (int i = 0; i < 10_000; i++) {
            test(l, r);
        }

        // Now, we can peacefully try the fast path without being bothered by profiling.
        l = new Object[2 * BIG_SIZE];
        r = new Object[2 * BIG_SIZE];

        for (int i = 0; i < BIG_SIZE; i++) {
            l[2*i    ] = i;
            r[2*i    ] = i;
            l[2*i + 1] = i;
            r[2*i + 1] = -i;
        }
        test(l, r);
    }

    static int test(Object[] l, Object[] r) {
        int s = 0;
        for (int i = 0; i < l.length; i++) {
            for (int j = 0; j < r.length; j++) {
                if (l[i] == r[j]) {
                    s++;
                } else {
                    s--;
                }
            }
        }
        return s;
    }

}
