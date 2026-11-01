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

package compiler.loopopts;

/*
 * @test
 * @bug 8386591
 * @summary Test case for TruncatedIncrement::build /
 *          CountedLoopConverter::has_truncation_wrap where we got wrong
 *          results, because we confused "& 0x7fff" as range [0..65535]
 *          instead of [0..32767].
 * @library /test/lib /
 * @run main/othervm -Xcomp
 *                   -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestTruncationWrapBadCharWrap {
    interface TestMethod {
        int call();
    }

    public static void main(String[] args) {
        int failures = 0;

        failures += run("test1", () -> test1(), 1402);
        failures += run("test2", () -> test2(), 2037);
        failures += run("test3", () -> test3(), 171761184);

        if (failures > 0) {
            throw new RuntimeException("failures: " + failures);
        }
    }

    static int run(String name, TestMethod t, int expected) {
        for (int i = 0; i < 10_000; i++) {
            int result = t.call();
            if (result != expected) {
                System.out.println(name + " wrong result: " + result + " vs " + expected);
                return 1;
            }
        }
        return 0;
    }


    static int test1() {
        int sum = 0;
        // The entry value is outside the range [0..32767], but inside [0..65535].
        int i = (char)38405;
        while (32 < i) {
            sum++;
            // Ignoring truncation would require values to be in range [0..32767].
            // But unfortunately, we classified this as CHAR, and checked for [0..65535].
            i = (i - 4) & 0x7fff;
        }
        return sum;
    }

    static int test2() {
        int sum = 0;
        // We have 32767 - 32758 = 9 < 48, so the limit is too close to the wrap
        // limit, and wrap is possible. But since 0x7fff got mapped to CHAR,
        // we accidentally checked 65535 - 32758 < 48, and conclude wrap is not
        // possible.
        for (int i = 519; i < 32758; i = (i + 48) & 0x7fff) {
            sum++;
        }
        return sum;
    }

    static int opaqueCounter;

    static boolean opaqueCheck() {
        return opaqueCounter++ > 10448;
    }

    static int test3() {
        opaqueCounter = 0;
        int sum = 0;
        int i;
        // Similar as with test2:
        // We should check 32767 - 32766 = 1 < 50, so wrap possible. But we
        // wrongly classified it as CHAR and checked 65535 - 32766 < 50, and
        // concluded there is no wrap.
        for (i = 2046; i <= 32766; i = (i + 50) & 0x7fff) {
            sum += i + 1;
            if (opaqueCheck()) { break; }
        }
        return sum + i;
    }
}
