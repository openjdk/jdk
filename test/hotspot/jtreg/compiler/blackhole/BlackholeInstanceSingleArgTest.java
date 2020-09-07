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
 * @run main/othervm
 *      -Xmx1g
 *      -XX:TieredStopAtLevel=1
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceSingleArgTest
 *
 * @run main/othervm
 *      -Xmx1g
 *      -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceSingleArgTest
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
 *      compiler.blackhole.BlackholeInstanceSingleArgTest
 *
 * @run main/othervm
 *      -Xmx1g -XX:-UseCompressedOops
 *      -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *      -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      -XX:CompileCommand=blackhole,compiler/blackhole/BlackholeTarget.bh_*
 *      compiler.blackhole.BlackholeInstanceSingleArgTest
 */

package compiler.blackhole;

import sun.hotspot.WhiteBox;

public class BlackholeInstanceSingleArgTest {

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
        // Then make sure targets can still be called.

        WB.deoptimizeAll();

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
    }

    private static void test_boolean() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_boolean((c & 0x1) == 0);
        }
    }

    private static void test_byte() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_byte((byte)c);
        }
    }

    private static void test_char() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_char((char)c);
        }
    }

    private static void test_short() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_short((short)c);
        }
    }

    private static void test_int() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_int(c);
        }
    }

    private static void test_float() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_float(c);
        }
    }

    private static void test_long() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_long(c);
        }
    }

    private static void test_double() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_double(c);
        }
    }

    private static void test_Object() {
        BlackholeTarget t = new BlackholeTarget();
        for (int c = 0; c < 1_000_000; c++) {
            t.bh_i_Object(Integer.valueOf(c));
        }
    }
}
