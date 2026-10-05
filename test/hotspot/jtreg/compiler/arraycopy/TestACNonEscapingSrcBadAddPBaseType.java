/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug 8380158
 * @summary C2: compiler/c2/TestGVNCrash.java asserts with adr and adr_type must agree
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileOnly=compiler.arraycopy.TestACNonEscapingSrcBadAddPBaseType::test1
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=946074051 ${test.main.class}
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileOnly=compiler.arraycopy.TestACNonEscapingSrcBadAddPBaseType::test1
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.arraycopy;

public class TestACNonEscapingSrcBadAddPBaseType {
    private static volatile int volatileField;

    public static void main(String[] args) {
        int[] dst = new int[2];
        for (int i = 0; i < 20_000; i++) {
            test1(dst);
        }
    }

    private static void test1(int[] dst) {
        int[] array = new int[2];
        A a = new A(array);
        volatileField = 42;
        int[] src = a.src;
        System.arraycopy(src, 0, dst, 0, src.length);
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    private static class A {
        private final int[] src;

        public A(int[] src) {
            this.src = src;
        }
    }
}
