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
 * @test
 *
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *
 *
 * @run main/othervm
 *      -Xmx1g
 *      -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceReturnTest
 *
 * @run main/othervm
 *      -Xmx1g
 *      -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceReturnTest
 */

/*
 * @test
 * @requires vm.bits == "64"
 *
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceReturnTest
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceReturnTest
 */

package compiler.blackhole;

import sun.hotspot.WhiteBox;

public class BlackholeInstanceReturnTest {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        // Warmup/resolve methods
        BlackholeTarget.clear();
        test_boolean();

        BlackholeTarget.clear();
        test_byte();

        BlackholeTarget.clear();
        test_char();

        BlackholeTarget.clear();
        test_short();

        BlackholeTarget.clear();
        test_int();

        BlackholeTarget.clear();
        test_float();

        BlackholeTarget.clear();
        test_long();

        BlackholeTarget.clear();
        test_double();

        BlackholeTarget.clear();
        test_Object();

        // Now that all tests are guaranteed to be linked, recompile.
        // Then seee if targets are still entered, despite the compiler commands.

        WB.deoptimizeAll();

        BlackholeTarget.clear();
        test_boolean();
        BlackholeTarget.shouldBeEntered();

        BlackholeTarget.clear();
        test_byte();
        BlackholeTarget.shouldBeEntered();

        BlackholeTarget.clear();
        test_char();
        BlackholeTarget.shouldBeEntered();

        BlackholeTarget.clear();
        test_short();
        BlackholeTarget.shouldBeEntered();

        BlackholeTarget.clear();
        test_int();
        BlackholeTarget.shouldBeEntered();

        BlackholeTarget.clear();
        test_float();
        BlackholeTarget.shouldBeEntered();

        BlackholeTarget.clear();
        test_long();
        BlackholeTarget.shouldBeEntered();

        BlackholeTarget.clear();
        test_double();
        BlackholeTarget.shouldBeEntered();

        BlackholeTarget.clear();
        test_Object();
        BlackholeTarget.shouldBeEntered();
    }

    private static void test_boolean() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_boolean((c & 0x1) == 0) != false) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_byte() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_byte((byte)c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_char() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_char((char)c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_short() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_short((short)c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_int() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_int(c) != 0) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_float() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_float(c) != 0F) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_long() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_long(c) != 0L) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_double() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_double(c) != 0D) {
                throw new AssertionError("Return value error");
            }
        }
    }

    private static void test_Object() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            if (t.bh_ir_Object(Integer.valueOf(c)) != null) {
                throw new AssertionError("Return value error");
            }
        }
    }

}
