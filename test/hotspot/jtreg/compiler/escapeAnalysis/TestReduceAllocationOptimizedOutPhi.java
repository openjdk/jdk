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
 * @bug 8389873
 * @summary Check that Reduce Allocation Merges correctly handle the situation
 *          where the Phi was optimized out by IGVN during CG construction.
 * @run main/othervm -XX:CompileCommand=compileonly,*${test.main.class}*::*
 *                   -Xcomp -XX:-TieredCompilation ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.escapeAnalysis;

public class TestReduceAllocationOptimizedOutPhi {
    static int var_369;

    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            test();
        }
    }

    static void test() {
        switch (new Foo().var_2) {
            case 3:
                var_369 = (1.0002043F == new Foo().var_2 ? new Bar() : new Bar()).var_151;
                var_369 = 4;
        }
        for (short var_480 = 0; var_480 < 1; var_480++) {
            var_369 = new Bar().var_151;
        }
    }
}

class Foo {
    long var_1 = 9;
    char var_2 = 'B';

    Foo() {
        int var_121 = 9;
        for (var_121 = 9; var_121 >= 0; var_121--) {
            double var_122 = 2.2250738585072014E-308 - (Integer) 0;
            float var_123 = '@' * (Integer) (Byte.valueOf((byte) 9) * var_121);
        }
        long var_124 = (Float.intBitsToFloat(608) == '(' ? 7L : 5);
        byte var_127 = 5;
        for (var_127 = 55; var_127 >= 0; var_127--) {
            if (370 <= -Byte.valueOf((byte) 14)) {
                long var_131 = 71L + (Integer) (-Character.valueOf('d'));
                ++var_131;
                var_131--;
                var_131 >>>= var_131;
            }
        }
        byte var_132 = 0;
        for (var_132 = 0; var_132 < 6; var_132++) {
            byte var_134 = ++var_127;
            short var_135 = 2046;
            double var_136 = -var_132 < 0.18568599F ? 0.44624205067529954 * Long.valueOf(1048575) + '~' : Short.valueOf((short) 4085);
            byte var_137 = 3;
            double var_138 = -Integer.valueOf(6) - 0.9994681372166424 * Long.valueOf(3) + (Integer) (+Short.valueOf((short) 512));
            int var_141 = (Integer) (Byte.valueOf((byte) 7) * Character.valueOf('U')) - 2;
        }
    }
}

class Bar {
    Byte var_151 = 3;
}

