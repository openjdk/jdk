/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8353266
 * @summary Integer.bitCount modifies input register
 * @library /test/lib /
 *
 * @run main/othervm
 *      -Xbatch
 *      -XX:CompileOnly=compiler.intrinsics.BitCountIAarch64PreservesArgument::test
 *      compiler.intrinsics.BitCountIAarch64PreservesArgument
 */

/**
 * @test
 * @bug 8353266
 * @library /test/lib /
 *
 * @run main compiler.intrinsics.BitCountIAarch64PreservesArgument
 */

package compiler.intrinsics;

import static compiler.lib.generators.Generators.G;

public class BitCountIAarch64PreservesArgument {
    static long lFld;
    static long result;

    public static void main(String[] args) {
        lFld = 0xfedc_ba98_7654_3210L;
        for (int i = 0; i < 10_000; i++) {
            test();
            if (result != 0xfedc_ba98_7654_3210L) {
                // Wrongly outputs the cut input 0x7654_3210 == 1985229328
                throw new RuntimeException("Wrong result. Expected result = " + lFld + "; Actual result = " + result);
            }
        }

        lFld = G.longs().next();
        for (int i = 0; i < 10_000; i++) {
            test();
            if (result != lFld) {
                throw new RuntimeException("Wrong result. Expected result = " + lFld + "; Actual result = " + result);
            }
        }
    }

    static void test() {
        long x = lFld;
        try {
            result = Integer.bitCount((int) x); // Cut input: 0x7654_3210 == 1985229328
            throw new RuntimeException();
        } catch (RuntimeException _) {
        }
        result = x;
    }
}
