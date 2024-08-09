/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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
 * @test ClearDirectivesTest
 * @bug 8333891
 * @summary Test Java methods with a directive disabling compilation can get
 *          compilable if the directive is removed.
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   serviceability.dcmd.compiler.ClearDirectivesTest
 */

package serviceability.dcmd.compiler;

import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

import java.lang.reflect.Method;

import static jdk.test.lib.Asserts.assertEQ;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE;

public class ClearDirectivesTest {

    static int calc(int v) {
        int result = 0;
        for (int i = 0; i < v; ++i) {
          result += result * v + i;
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Method method = ClearDirectivesTest.class.getDeclaredMethod("calc", int.class);
        String dirs = """
        [{
           match: "*::calc",
           Exclude: true
        }]""";
        WhiteBox wb = WhiteBox.getWhiteBox();
        wb.addCompilerDirective(dirs);
        new JMXExecutor().execute("Compiler.directives_print");

        wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);
        while (wb.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }
        assertEQ(COMP_LEVEL_NONE, wb.getMethodCompilationLevel(method), "Compilation level");

        wb.enqueueMethodForCompilation(method, COMP_LEVEL_SIMPLE);
        while (wb.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }
        assertEQ(COMP_LEVEL_NONE, wb.getMethodCompilationLevel(method), "Compilation level");

        new JMXExecutor().execute("Compiler.directives_clear");
        new JMXExecutor().execute("Compiler.directives_print");

        wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);
        while (wb.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }
        assertEQ(COMP_LEVEL_FULL_OPTIMIZATION, wb.getMethodCompilationLevel(method), "Compilation level");

        wb.deoptimizeMethod(method);
        assertEQ(COMP_LEVEL_NONE, wb.getMethodCompilationLevel(method), "Compilation level");

        wb.enqueueMethodForCompilation(method, COMP_LEVEL_SIMPLE);
        while (wb.isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }
        assertEQ(COMP_LEVEL_SIMPLE, wb.getMethodCompilationLevel(method), "Compilation level");
    }
}
