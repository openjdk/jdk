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
 * @test ReplaceNMethodVerifyNoRecomp
 * @bug 8316694
 * @summary test that WB::replaceNMethod() correctly creates a new nmethod and can be used without recompiling
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm compiler.whitebox.ReplaceNMethodVerifyNoRecomp
 */

package compiler.whitebox;

import compiler.whitebox.CompilerWhiteBoxTest;
import java.lang.reflect.Method;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.NMethod;

public class ReplaceNMethodVerifyNoRecomp {

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-Xbootclasspath/a:.", "-Xbatch", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintCompilation",
            "-XX:+WhiteBoxAPI", ReplaceNMethodVerifyNoRecomp.ReplaceNMethod.class.getName()
        );
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldHaveExitValue(0);

        verifyOutput(output.getOutput());
    }

    // Matches output for function being compiled. Does not match to deoptimization outputs for that function
    // Example: 4       compiler.whitebox.ReplaceNMethodVerifyNoRecomp$ReplaceNMethod::function (20 bytes)
    private static String methodCompiledRegex = "^4\\s+compiler\\.whitebox\\.ReplaceNMethodVerifyNoRecomp\\$ReplaceNMethod::function\\s+\\(\\d+\\s+bytes\\)\\\n$";

    public static void verifyOutput(String text) {
        int notCompiled = text.indexOf("Should not be compiled");
        if (notCompiled == -1) {
            throw new RuntimeException("Output does not include required out");
        }

        int functionCompiled = text.indexOf("4       compiler.whitebox.ReplaceNMethodVerifyNoRecomp$ReplaceNMethod::function");
        if (functionCompiled == -1) {
            throw new RuntimeException("Function was never compiled");
        }

        // Confirm that the function was not compiled when it was not supposed to be
        if (functionCompiled < notCompiled) {
            throw new RuntimeException("Function was compiled before it should be");
        }

        int isCompiled = text.indexOf("Should be compiled");
        if (isCompiled == -1) {
            throw new RuntimeException("Output does not include required out");
        }

        // Confirm that the function was compled when it was supposed to be
        if (functionCompiled > isCompiled) {
            throw new RuntimeException("Function was not compiled after it should be");
        }

        // Confirm that the function never gets recompiled
        String remainingOutput = text.substring(functionCompiled + 1);

        // Confirm that the function never gets recompiled
        boolean functionRecompiled = remainingOutput.matches(methodCompiledRegex);
        if (functionRecompiled) {
            throw new RuntimeException("Function was recompiled when it should not be");
        }
    }

    private static class ReplaceNMethod {
        private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
        public static double FUNCTION_RESULT = 0;

        public static void main(String [] args) throws Exception {
            // Get method that will be relocated
            Method method = ReplaceNMethodVerifyNoRecomp.ReplaceNMethod.class.getMethod("function");
            WHITE_BOX.testSetDontInlineMethod(method, true);

            // Verify not initially compiled
            System.out.println("Should not be compiled");
            CompilerWhiteBoxTest.checkNotCompiled(method, false);

            // Call function enough to compile
            callFunction();

            // Verify now compiled
            System.out.println("Should be compiled");
            CompilerWhiteBoxTest.checkCompiled(method, false);

            // Get newly created nmethod
            NMethod origNmethod = NMethod.get(method, false);

            // Relocate nmethod and mark old for cleanup
            WHITE_BOX.replaceNMethod(method, false);

            // Trigger GC to clean up old nmethod
            WHITE_BOX.fullGC();

            // Verify function still compiled after old was cleaned up
            CompilerWhiteBoxTest.checkCompiled(method, false);

            // Get new nmethod and verify it's actually new
            NMethod newNmethod = NMethod.get(method, false);
            if (origNmethod.entry_point == newNmethod.entry_point) {
                throw new RuntimeException("Did not create new nmethod");
            }

            // Call function again to verify it does not get recompiled
            callFunction();
            CompilerWhiteBoxTest.checkCompiled(method, false);
        }

        // Call function multiple times to trigger compilation
        private static void callFunction() {
            for (int i = 0; i < CompilerWhiteBoxTest.THRESHOLD; i++) {
                function();
            }
        }

        public static void function() {
            FUNCTION_RESULT = Math.random();
        }
    }
}
