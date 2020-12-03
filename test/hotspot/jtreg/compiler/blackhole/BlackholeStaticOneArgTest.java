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
 *      -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeStaticOneArgTest
 */

/**
 * @test id=c2
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g
 *      -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeStaticOneArgTest
 */

/**
 * @test id=c1-no-coops
 * @requires vm.bits == "64"
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeStaticOneArgTest
 */

/**
 * @test id=c2-no-coops
 * @requires vm.bits == "64"
 * @build compiler.blackhole.BlackholeTarget
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeStaticOneArgTest
 */

package compiler.blackhole;

public class BlackholeStaticOneArgTest {

    public static void main(String[] args) {
        runTries(BlackholeStaticOneArgTest::test_boolean);
        runTries(BlackholeStaticOneArgTest::test_byte);
        runTries(BlackholeStaticOneArgTest::test_char);
        runTries(BlackholeStaticOneArgTest::test_short);
        runTries(BlackholeStaticOneArgTest::test_int);
        runTries(BlackholeStaticOneArgTest::test_float);
        runTries(BlackholeStaticOneArgTest::test_long);
        runTries(BlackholeStaticOneArgTest::test_double);
        runTries(BlackholeStaticOneArgTest::test_Object);
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

    private static void test_boolean() {
        for (int c = 0; c < CYCLES; c++) {
            BlackholeTarget.bh_s_boolean((c & 0x1) == 0);
        }
    }

    private static void test_byte() {
        for (int c = 0; c < CYCLES; c++) {
            BlackholeTarget.bh_s_byte((byte)c);
        }
    }

    private static void test_char() {
        for (int c = 0; c < CYCLES; c++) {
            BlackholeTarget.bh_s_char((char)c);
        }
    }

    private static void test_short() {
        for (int c = 0; c < CYCLES; c++) {
            BlackholeTarget.bh_s_short((short)c);
        }
    }

    private static void test_int() {
        for (int c = 0; c < CYCLES; c++) {
            BlackholeTarget.bh_s_int(c);
        }
    }

    private static void test_float() {
        for (int c = 0; c < CYCLES; c++) {
            BlackholeTarget.bh_s_float(c);
        }
    }

    private static void test_long() {
        for (int c = 0; c < CYCLES; c++) {
            BlackholeTarget.bh_s_long(c);
        }
    }

    private static void test_double() {
        for (int c = 0; c < CYCLES; c++) {
            BlackholeTarget.bh_s_double(c);
        }
    }

    private static void test_Object() {
        for (int c = 0; c < CYCLES; c++) {
            Object o = new Object();
            BlackholeTarget.bh_s_Object(o);
        }
    }
}
