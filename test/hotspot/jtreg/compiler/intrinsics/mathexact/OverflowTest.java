/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Math.*Exact intrinsics, especially in case of overflow
 *          The base case
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -XX:+UnlockDiagnosticVMOptions -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.intrinsics.mathexact.OverflowTest::comp_*
 *      -XX:CompileCommand=dontinline,compiler.intrinsics.mathexact.OverflowTest::*
 *      compiler.intrinsics.mathexact.OverflowTest
 */

/*
 * @test
 * @summary Math.*Exact intrinsics, especially in case of overflow
 *          With ProfileTraps enabled to allow builtin_throw to work
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.intrinsics.mathexact.OverflowTest::comp_*
 *      -XX:CompileCommand=dontinline,compiler.intrinsics.mathexact.OverflowTest::*
 *      -XX:+ProfileTraps -XX:+StackTraceInThrowable -XX:+OmitStackTraceInFastThrow
 *      compiler.intrinsics.mathexact.OverflowTest
 */

/*
 * @test
 * @summary Math.*Exact intrinsics, especially in case of overflow
 *          ProfileTraps off => throw will never be hot for builtin_throw
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.intrinsics.mathexact.OverflowTest::comp_*
 *      -XX:CompileCommand=dontinline,compiler.intrinsics.mathexact.OverflowTest::*
 *      -XX:-ProfileTraps
 *      compiler.intrinsics.mathexact.OverflowTest
 */

/*
 * @test
 * @summary Math.*Exact intrinsics, especially in case of overflow
 *          OmitStackTraceInFastThrow off => can_omit_stack_trace is false => no builtin_throw
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.intrinsics.mathexact.OverflowTest::comp_*
 *      -XX:CompileCommand=dontinline,compiler.intrinsics.mathexact.OverflowTest::*
 *      -XX:+ProfileTraps -XX:+StackTraceInThrowable -XX:-OmitStackTraceInFastThrow
 *      compiler.intrinsics.mathexact.OverflowTest
 */

/*
 * @test
 * @summary Math.*Exact intrinsics, especially in case of overflow
 *          StackTraceInThrowable off => can_omit_stack_trace is true => yes builtin_throw
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.intrinsics.mathexact.OverflowTest::comp_*
 *      -XX:CompileCommand=dontinline,compiler.intrinsics.mathexact.OverflowTest::*
 *      -XX:+ProfileTraps -XX:-StackTraceInThrowable -XX:+OmitStackTraceInFastThrow
 *      compiler.intrinsics.mathexact.OverflowTest
 */

/*
 * @test
 * @summary Math.*Exact intrinsics, especially in case of overflow
 *          StackTraceInThrowable off && OmitStackTraceInFastThrow off => can_omit_stack_trace is true => yes builtin_throw
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.intrinsics.mathexact.OverflowTest::comp_*
 *      -XX:CompileCommand=dontinline,compiler.intrinsics.mathexact.OverflowTest::*
 *      -XX:+ProfileTraps -XX:-StackTraceInThrowable -XX:-OmitStackTraceInFastThrow
 *      compiler.intrinsics.mathexact.OverflowTest
 */

/*
 * @test
 * @summary Math.*Exact intrinsics, especially in case of overflow
 *          Without intrinsics
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -XX:+UnlockDiagnosticVMOptions -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *      -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.intrinsics.mathexact.OverflowTest::comp_*
 *      -XX:CompileCommand=dontinline,compiler.intrinsics.mathexact.OverflowTest::*
 *      -XX:DisableIntrinsic=_addExactI,_incrementExactI,_addExactL,_incrementExactL,_subtractExactI,_decrementExactI,_subtractExactL,_decrementExactL,_negateExactI,_negateExactL,_multiplyExactI,_multiplyExactL
 *      compiler.intrinsics.mathexact.OverflowTest
 */

package compiler.intrinsics.mathexact;

import java.lang.reflect.Method;

import compiler.lib.generators.RestrictableGenerator;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import static compiler.lib.generators.Generators.G;

public class OverflowTest {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final RestrictableGenerator<Integer> int_gen = G.ints();
    private static final int LIMIT = 10_000;

    public static void main(String... args) throws NoSuchMethodException {
        OverflowTest t = new OverflowTest();
        t.run();
    }

    void run() throws NoSuchMethodException {
        check_compilation();
        check_multiplyI();
    }

    void check_compilation() throws NoSuchMethodException {
        // Force Math loading
        Math.min(2, 3);
        comp_multiplyI(0, 0);
        comp_multiplyI_no_catch(0, 0);
        int_multiplyI(0, 0);

        Method comp_multiplyI_meth = OverflowTest.class.getDeclaredMethod("comp_multiplyI", int.class, int.class);
        Asserts.assertTrue(WHITE_BOX.isMethodCompiled(comp_multiplyI_meth), "comp_multiplyI(int, int) is not compiled");

        Method comp_multiplyI_no_catch_meth = OverflowTest.class.getDeclaredMethod("comp_multiplyI_no_catch", int.class, int.class);
        Asserts.assertTrue(WHITE_BOX.isMethodCompiled(comp_multiplyI_no_catch_meth), "comp_multiplyI_no_catch(int, int) is not compiled");

        Method int_multiplyI_meth = OverflowTest.class.getDeclaredMethod("int_multiplyI", int.class, int.class);
        Asserts.assertFalse(WHITE_BOX.isMethodCompiled(int_multiplyI_meth), "int_multiplyI(int, int) is compiled");
    }

    void assert_consistent(Integer comp_res, Integer int_res) {
        if (int_res == null) {
            Asserts.assertNull(comp_res);
        } else {
            Asserts.assertNotNull(comp_res);
            Asserts.assertEquals(comp_res, int_res);
        }
    }

    Integer comp_multiplyI(int a, int b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    int comp_multiplyI_no_catch(int a, int b) {
        return Math.multiplyExact(a, b);
    }

    Integer int_multiplyI(int a, int b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    void check_multiplyI() {
        // 46_340 < 2 ^ 15.5 < 46_341 =>
        // 46_340^2 < 2 ^ 31 < 46_341^2
        int limit_square_do_not_overflow = 46_340;

        // In bound cases
        for (int i = 0; i < LIMIT; i++) {
            int a = limit_square_do_not_overflow - i;
            Integer comp_res = comp_multiplyI(a, a);
            Integer int_res = int_multiplyI(a, a);
            Asserts.assertNotNull(int_res);
            assert_consistent(comp_res, int_res);
        }
        for (int i = 0; i < LIMIT; i++) {
            int a = limit_square_do_not_overflow - i;
            Integer comp_res;
            try {
                comp_res = comp_multiplyI_no_catch(a, a);
            } catch (ArithmeticException _) {
                comp_res = null;
            }
            Integer int_res = int_multiplyI(a, a);
            Asserts.assertNotNull(int_res);
            assert_consistent(comp_res, int_res);
        }

        // Out of bound cases
        for (int i = 0; i < LIMIT; i++) {
            int a = limit_square_do_not_overflow + 1 + i;
            Integer comp_res = comp_multiplyI(a, a);
            Integer int_res = int_multiplyI(a, a);
            Asserts.assertNull(int_res);
            assert_consistent(comp_res, int_res);
        }
        for (int i = 0; i < LIMIT; i++) {
            int a = limit_square_do_not_overflow + 1 + i;
            Integer comp_res;
            try {
                comp_res = comp_multiplyI_no_catch(a, a);
            } catch (ArithmeticException _) {
                comp_res = null;
            }
            Integer int_res = int_multiplyI(a, a);
            Asserts.assertNull(int_res);
            assert_consistent(comp_res, int_res);
        }

        // Random slice
        int lhs = int_gen.next();
        int rhs_start = int_gen.next() & 0xff_ff_00_00;
        for (int i = 0; i < 0x1_00_00; i++) {
            int rhs = rhs_start | i;
            Integer comp_res = comp_multiplyI(lhs, rhs);
            Integer int_res = int_multiplyI(lhs, rhs);
            assert_consistent(comp_res, int_res);
        }
        for (int i = 0; i < 0x1_00_00; i++) {
            int rhs = rhs_start | i;
            Integer comp_res;
            try {
                comp_res = comp_multiplyI_no_catch(lhs, rhs);
            } catch (ArithmeticException _) {
                comp_res = null;
            }
            Integer int_res = int_multiplyI(lhs, rhs);
            assert_consistent(comp_res, int_res);
        }
    }
}
