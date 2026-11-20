/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
 * @bug 8396865
 * @summary assert(_num_regs == reg || !_num_regs) failed with Long.expand on aarch64
 * @requires os.arch == "aarch64" & vm.compiler2.enabled &
 *           vm.cpu.features ~= ".*sve2.*" & vm.cpu.features ~= ".*svebitperm.*"
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileOnly=compiler.c2.aarch64.TestLongExpandCrash::test
 *                   ${test.main.class}
 */

package compiler.c2.aarch64;

import jdk.test.lib.Asserts;

public class TestLongExpandCrash {
    private static final long MASK = 0x0F0F0F0F0F0F0F0FL;
    private static final long[] arr = new long[64];
    private static final long[] masks = new long[16];

    // C2 will match expandBitsL_memcon/expandBitsL_reg rules in adfile
    static long test(int i, boolean constMask) {
        return constMask ? Long.expand(arr[i & 63], MASK)
                         : Long.expand(arr[i & 63], masks[i & 15]);
    }

    public static void main(String[] args) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = 0x0123456789ABCDEFL + i;
        }
        for (int i = 0; i < masks.length; i++) {
            masks[i] = 0x00FF00FF00FF00FFL;
        }

        long expectedConst = Long.expand(arr[0], MASK);
        long expectedVar = Long.expand(arr[1], masks[1]);

        Asserts.assertEquals(test(0, true), expectedConst, "const mask");
        Asserts.assertEquals(test(1, false), expectedVar, "var mask");
    }
}
