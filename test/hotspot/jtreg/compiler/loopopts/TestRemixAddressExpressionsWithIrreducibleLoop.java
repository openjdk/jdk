/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8326638
 * @summary Test handling of irreducible loops in PhaseIdealLoop::remix_address_expressions.
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=compileonly,TestRemixAddressExpressionsWithIrreducibleLoop::test
 *                   TestRemixAddressExpressionsWithIrreducibleLoop
 */

public class TestRemixAddressExpressionsWithIrreducibleLoop {

    public static void main(String[] args) {
        test("4");
    }

    public static void test(String arg) {
        for (int i = 0; i < 100_000; ++i) {
            int j = 0;
            while (true) {
                boolean tmp = "1\ufff0".startsWith(arg, 2 - arg.length());
                if (j++ > 100)
                    break;
            }
        loop:
            while (i >= 100) {
                for (int i2 = 0; i2 < 1; i2 = 1)
                    if (j > 300)
                        break loop;
                j++;
            }
        }
    }
}
