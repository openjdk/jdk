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
 *      compiler.blackhole.BlackholeStaticReturnTest
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
 *      compiler.blackhole.BlackholeStaticReturnTest
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
 *      compiler.blackhole.BlackholeStaticReturnTest
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
 *      compiler.blackhole.BlackholeStaticReturnTest
 */

package compiler.blackhole;

public class BlackholeStaticReturnTest {

    public static void main(String[] args) {
        runTries(BlackholeStaticReturnTest::test_boolean);
        runTries(BlackholeStaticReturnTest::test_byte);
        runTries(BlackholeStaticReturnTest::test_char);
        runTries(BlackholeStaticReturnTest::test_short);
        runTries(BlackholeStaticReturnTest::test_int);
        runTries(BlackholeStaticReturnTest::test_float);
        runTries(BlackholeStaticReturnTest::test_long);
        runTries(BlackholeStaticReturnTest::test_double);
        runTries(BlackholeStaticReturnTest::test_Object);
    }

    private static final int CYCLES = 1_000_000;
    private static final int TRIES = 10;

    public static void runTries(Runnable r) {
        for (int t = 0; t < TRIES; t++) {
            BlackholeTarget.clear();
            r.run();
            BlackholeTarget.shouldBeEntered();
        }
    }

    private static void test_boolean() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_boolean((c & 0x1) == 0) != false) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_byte() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_byte((byte)c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_char() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_char((char)c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_short() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_short((short)c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_int() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_int(c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_float() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_float(c) != 0F) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_long() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_long(c) != 0L) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_double() {
        for (int c = 0; c < CYCLES; c++) {
            if (BlackholeTarget.bh_sr_double(c) != 0D) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_Object() {
        for (int c = 0; c < CYCLES; c++) {
            Object o = new Object();
            if (BlackholeTarget.bh_sr_Object(o) != null) {
                throw new AssertionError("Return value error");
            }
        }
    }

}
