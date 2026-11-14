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

/**
 * @test
 * @key randomness
 * @bug 8389111
 * @summary Verify that results of computations are the same w/
 *          and w/o usage of BZHI instruction
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI compiler.intrinsics.bmi.TestBzhiI
 */

package compiler.intrinsics.bmi;

import jdk.test.whitebox.cpuinfo.CPUInfo;

public class TestBzhiI {

    public static void main(String args[]) throws Throwable {
        if (!CPUInfo.hasFeature("bmi2")) {
            System.out.println("INFO: CPU does not support bmi2 feature.");
        }

        BMITestRunner.runTests(BzhiIExpr.class, args,
                               "-XX:+IgnoreUnrecognizedVMOptions",
                               "-XX:+UseBMI2Instructions");
        BMITestRunner.runTests(BzhiICommutativeExpr.class, args,
                               "-XX:+IgnoreUnrecognizedVMOptions",
                               "-XX:+UseBMI2Instructions");
        BMITestRunner.runTests(BzhiIShiftedSourceExpr.class, args,
                               "-XX:+IgnoreUnrecognizedVMOptions",
                               "-XX:+UseBMI2Instructions");
        BMITestRunner.runTests(BzhiISameValueExpr.class, args,
                               "-XX:+IgnoreUnrecognizedVMOptions",
                               "-XX:+UseBMI2Instructions");
    }

    public static class BzhiIExpr extends Expr.BMIBinaryIntExpr {

        public int intExpr(int src, int bits) {
            return src & ((1 << bits) - 1);
        }

        public int intExpr(int src, Expr.MemI bits) {
            return src & ((1 << bits.value) - 1);
        }

        public int intExpr(Expr.MemI src, int bits) {
            return src.value & ((1 << bits) - 1);
        }

        public int intExpr(Expr.MemI src, Expr.MemI bits) {
            return src.value & ((1 << bits.value) - 1);
        }
    }

    public static class BzhiICommutativeExpr extends Expr.BMIBinaryIntExpr {

        public int intExpr(int src, int bits) {
            return ((1 << bits) - 1) & src;
        }

        public int intExpr(int src, Expr.MemI bits) {
            return ((1 << bits.value) - 1) & src;
        }

        public int intExpr(Expr.MemI src, int bits) {
            return ((1 << bits) - 1) & src.value;
        }

        public int intExpr(Expr.MemI src, Expr.MemI bits) {
            return ((1 << bits.value) - 1) & src.value;
        }
    }

    // a shift in the source as well as in the mask
    public static class BzhiIShiftedSourceExpr extends Expr.BMIBinaryIntExpr {

        public int intExpr(int src, int bits) {
            return ((src << 3) + bits) & ((1 << bits) - 1);
        }

        public int intExpr(int src, Expr.MemI bits) {
            return ((src << 3) + bits.value) & ((1 << bits.value) - 1);
        }

        public int intExpr(Expr.MemI src, int bits) {
            return ((src.value << 3) + bits) & ((1 << bits) - 1);
        }

        public int intExpr(Expr.MemI src, Expr.MemI bits) {
            return ((src.value << 3) + bits.value) & ((1 << bits.value) - 1);
        }
    }

    // the source is also the count, so both can end up in one register
    public static class BzhiISameValueExpr extends Expr.BMIUnaryIntExpr {

        public int intExpr(int src) {
            return src & ((1 << src) - 1);
        }

        public int intExpr(Expr.MemI src) {
            return src.value & ((1 << src.value) - 1);
        }
    }
}
