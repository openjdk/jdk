/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @summary Ensure the HotCodeCollector detects a very hot method and relocates
 *          it to the HotCodeHeap. Sampling is best effort, so for reliability
 *          we spawn a seperate test process to manage the VM flags.
 * @requires vm.flagless
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver compiler.hotcode.HotCodeCollectorMoveFunction
 */

package compiler.hotcode;

import java.lang.reflect.Method;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

public class HotCodeCollectorMoveFunction {

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xbootclasspath/a:.",
            "-Xbatch",
            "-XX:-TieredCompilation",
            "-XX:+SegmentedCodeCache",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+HotCodeHeap",
            "-XX:+NMethodRelocation",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-XX:HotCodeIntervalSeconds=0",
            "-XX:HotCodeCallLevel=0",
            "-XX:HotCodeSampleSeconds=5",
            "-XX:HotCodeStablePercent=-1",
            "-XX:HotCodeSamplePercent=100",
            "-XX:HotCodeStartupDelaySeconds=0",
            "-XX:CompileCommand=compileonly," + Runner.class.getName() + "::func",
            Runner.class.getName()
        );

        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);
    }

    static class Runner {
        private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
        private static final Method method;
        private static final int C2_LEVEL = 4;
        private static final int FUNC_RUN_MILLIS = 60_000;

        private static volatile int blackholeCount = 0;

        static {
            try {
                method = Runner.class.getMethod("func");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        public static void main(String[] args) {
            WHITE_BOX.testSetDontInlineMethod(method, true);

            compileFunc();

            // Call function so collector samples and relocates
            func();

            // Function should now be in the Hot code heap after collector has had time to relocate
            NMethod relocatedNMethod = NMethod.get(method, false);
            Asserts.assertNotNull(relocatedNMethod);
            Asserts.assertEQ(BlobType.MethodHot, relocatedNMethod.code_blob_type);
        }

        private static void compileFunc() {
            WHITE_BOX.enqueueMethodForCompilation(method, C2_LEVEL);

            if (WHITE_BOX.getMethodCompilationLevel(method) != C2_LEVEL) {
                throw new IllegalStateException("Method " + method + " is not compiled by C2.");
            }
        }

        public static void func() {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < FUNC_RUN_MILLIS) {
                // Perform multiplicative LCG to ensure the compiler does not optimize away the code.
                // Integer overflow is used for the modulus so the loop terminates after (2^32)/4 iterations
                int num = 1;
                do {
                    blackholeCount++;
                    num *= 69069;
                } while (num != 1);
            }
        }
    }
}
