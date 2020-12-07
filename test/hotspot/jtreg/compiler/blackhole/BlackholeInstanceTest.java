/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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
 * @test id=c1
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g
 *      -Xbatch -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceTest
 */

/**
 * @test id=c2
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g
 *      -Xbatch -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceTest
 */

/**
 * @test id=c1-no-coops
 * @requires vm.bits == "64"
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -Xbatch -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceTest
 */

/**
 * @test id=c2-no-coops
 * @requires vm.bits == "64"
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -Xbatch -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceTest
 */

package compiler.blackhole;

public class BlackholeInstanceTest {

    public static void main(String[] args) {
        runTries(BlackholeInstanceTest::test_boolean_0);
        runTries(BlackholeInstanceTest::test_byte_0);
        runTries(BlackholeInstanceTest::test_char_0);
        runTries(BlackholeInstanceTest::test_short_0);
        runTries(BlackholeInstanceTest::test_int_0);
        runTries(BlackholeInstanceTest::test_float_0);
        runTries(BlackholeInstanceTest::test_long_0);
        runTries(BlackholeInstanceTest::test_double_0);
        runTries(BlackholeInstanceTest::test_Object_0);

        runTries(BlackholeInstanceTest::test_boolean_1);
        runTries(BlackholeInstanceTest::test_byte_1);
        runTries(BlackholeInstanceTest::test_char_1);
        runTries(BlackholeInstanceTest::test_short_1);
        runTries(BlackholeInstanceTest::test_int_1);
        runTries(BlackholeInstanceTest::test_float_1);
        runTries(BlackholeInstanceTest::test_long_1);
        runTries(BlackholeInstanceTest::test_double_1);
        runTries(BlackholeInstanceTest::test_Object_1);

        runTries(BlackholeInstanceTest::test_boolean_2);
        runTries(BlackholeInstanceTest::test_byte_2);
        runTries(BlackholeInstanceTest::test_char_2);
        runTries(BlackholeInstanceTest::test_short_2);
        runTries(BlackholeInstanceTest::test_int_2);
        runTries(BlackholeInstanceTest::test_float_2);
        runTries(BlackholeInstanceTest::test_long_2);
        runTries(BlackholeInstanceTest::test_double_2);
        runTries(BlackholeInstanceTest::test_Object_2);
    }

    private static final int CYCLES = 1_000_000;
    private static final int TRIES = 10;

    public static void runTries(Runnable r) {
        for (int t = 0; t < TRIES; t++) {
            BlackholeTarget.clear();
            r.run();
            if (t == TRIES - 1) {
               BlackholeTarget.shouldNotBeEntered();
            }
        }
    }

    private static void test_boolean_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_boolean_0();
        }
    }

    private static void test_byte_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_byte_0();
        }
    }

    private static void test_char_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_char_0();
        }
    }

    private static void test_short_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_short_0();
        }
    }

    private static void test_int_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_int_0();
        }
    }

    private static void test_float_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_float_0();
        }
    }

    private static void test_long_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_long_0();
        }
    }

    private static void test_double_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_double_0();
        }
    }

    private static void test_Object_0() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_Object_0();
        }
    }

    private static void test_boolean_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_boolean_1((c & 0x1) == 0);
        }
    }

    private static void test_byte_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_byte_1((byte)c);
        }
    }

    private static void test_char_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_char_1((char)c);
        }
    }

    private static void test_short_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_short_1((short)c);
        }
    }

    private static void test_int_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_int_1(c);
        }
    }

    private static void test_float_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_float_1(c);
        }
    }

    private static void test_long_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_long_1(c);
        }
    }

    private static void test_double_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_double_1(c);
        }
    }

    private static void test_Object_1() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            Object o = new Object();
            t.bh_i_Object_1(o);
        }
    }

    private static void test_boolean_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_boolean_2((c & 0x1) == 0, (c & 0x2) == 0);
        }
    }

    private static void test_byte_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_byte_2((byte)c, (byte)(c + 1));
        }
    }

    private static void test_char_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_char_2((char)c, (char)(c + 1));
        }
    }

    private static void test_short_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_short_2((short)c, (short)(c + 1));
        }
    }

    private static void test_int_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_int_2(c, c + 1);
        }
    }

    private static void test_float_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_float_2(c, c + 1);
        }
    }

    private static void test_long_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_long_2(c, c + 1);
        }
    }

    private static void test_double_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            t.bh_i_double_2(c, c + 1);
        }
    }

    private static void test_Object_2() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < CYCLES; c++) {
            Object o1 = new Object();
            Object o2 = new Object();
            t.bh_i_Object_2(o1, o2);
        }
    }
}
