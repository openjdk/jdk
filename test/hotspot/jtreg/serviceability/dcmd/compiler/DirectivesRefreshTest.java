/*
 * Copyright (c) 2023, BELLSOFT. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test DirectivesRefreshTest
 * @summary Test of forced recompile after compiler directives changes by diagnostic command
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-BackgroundCompilation
 *                   serviceability.dcmd.compiler.DirectivesRefreshTest
 */

package serviceability.dcmd.compiler;

import jdk.test.whitebox.WhiteBox;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Method;
import java.util.Random;

import static jdk.test.lib.Asserts.assertEQ;

import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_NONE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE;
import static compiler.whitebox.CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

public class DirectivesRefreshTest {

    static Path cmdPath = Paths.get(System.getProperty("test.src", "."), "refresh_control.txt");
    static WhiteBox wb = WhiteBox.getWhiteBox();
    static Random random = new Random();

    static Method method;
    static CommandExecutor executor;

    static int callable() {
        int result = 0;
        for (int i = 0; i < 100; i++) {
            result += random.nextInt(100);
        }
        return result;
    }

    static void checkCompilationLevel(Method method, int level) {
        assertEQ(wb.getMethodCompilationLevel(method), level, "Compilation level");
    }

    static void setup() throws Exception {
        method = DirectivesRefreshTest.class.getDeclaredMethod("callable");
        executor = new JMXExecutor();

        System.out.println("Compilation with C2");

        // Happens with fairly hot methods.
        wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);
        checkCompilationLevel(method, COMP_LEVEL_FULL_OPTIMIZATION);
    }

    static void testDirectivesAddRefresh() {
        System.out.println("Force forbid C2 via directive, method deoptimized");

        var output = executor.execute("Compiler.directives_add -r " + cmdPath.toString());
        output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");
        // Current handling of 'Exclude' for '-r' clears flags.
        checkCompilationLevel(method, COMP_LEVEL_NONE);

        System.out.println("C2 is excluded, re-compilation with C1");

        // Sanity check for the directive.
        wb.enqueueMethodForCompilation(method, COMP_LEVEL_FULL_OPTIMIZATION);
        checkCompilationLevel(method, COMP_LEVEL_NONE);

        // Happens with fairly hot methods.
        wb.enqueueMethodForCompilation(method, COMP_LEVEL_SIMPLE);
        checkCompilationLevel(method, COMP_LEVEL_SIMPLE);
    }

    static void testDirectivesClearRefresh() {
        System.out.println("Re-compilation with C2 due to removed restriction");

        var output = executor.execute("Compiler.directives_clear -r");
        output.stderrShouldBeEmpty().stdoutShouldBeEmpty();

        // No need to enqueue the method, "immediate" effect of '-r' without deoptimization.
        checkCompilationLevel(method, COMP_LEVEL_FULL_OPTIMIZATION);
    }

    static void testDirectivesAddRegular() {
        System.out.println("No changes if the restriction is not forced");

        // According to original JEP 165, the directive will be handled
        // "when a method is submitted for a compilation".
        var output = executor.execute("Compiler.directives_add " + cmdPath.toString());
        output.stderrShouldBeEmpty().shouldContain("1 compiler directives added");

        // In this program the method is not called, and here it is not enqueued.
        checkCompilationLevel(method, COMP_LEVEL_FULL_OPTIMIZATION);
    }

    public static void main(String[] args) throws Exception {
        setup();
        testDirectivesAddRefresh();
        testDirectivesClearRefresh();
        testDirectivesAddRegular();
    }
}
