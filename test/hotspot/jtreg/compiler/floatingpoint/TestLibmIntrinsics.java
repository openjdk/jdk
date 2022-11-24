/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Alibaba Group Holding Limited. All rights reserved.
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
 * @summary Test libm intrinsics
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   compiler.floatingpoint.TestLibmIntrinsics
 */

package compiler.floatingpoint;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;

public class TestLibmIntrinsics {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static final double pi = 3.1415926;

    private static final double expected = 2.5355263553695413;

    static double m() {
        return Math.pow(pi, Math.sin(Math.cos(Math.tan(Math.log(Math.log10(Math.exp(pi)))))));
    }

    static public void main(String[] args) throws NoSuchMethodException {
        Method test_method = compiler.floatingpoint.TestLibmIntrinsics.class.getDeclaredMethod("m");

        double interpreter_result = m();

        // Compile with C1 if possible
        WHITE_BOX.enqueueMethodForCompilation(test_method, CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE);

        double c1_result = m();

        WHITE_BOX.deoptimizeMethod(test_method);

        // Compile it with C2 if possible
        WHITE_BOX.enqueueMethodForCompilation(test_method, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);

        double c2_result = m();

        if (interpreter_result != c1_result ||
            interpreter_result != c2_result ||
            c1_result != c2_result) {
            System.out.println("interpreter = " + interpreter_result + " c1 = " + c1_result + " c2 = " + c2_result);
            throw new RuntimeException("Test Failed");
        }
    }
}
