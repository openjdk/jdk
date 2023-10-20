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
 */

/**
 * @test TestOnSpinWaitNoneAArch64
 * @summary Checks that java.lang.Thread.onSpinWait is not intrinsified when '-XX:OnSpinWaitInst=none' is used
 * @bug 8186670
 * @library /test/lib
 *
 * @requires vm.flagless
 * @requires os.arch=="aarch64"
 *
 * @run driver compiler.onSpinWait.TestOnSpinWaitNoneAArch64
 */

package compiler.onSpinWait;

import java.util.ArrayList;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOnSpinWaitNoneAArch64 {

    public static void main(String[] args) throws Exception {
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-showversion");
        command.add("-XX:-TieredCompilation");
        command.add("-Xbatch");
        command.add("-XX:+PrintCompilation");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+PrintInlining");
        command.add("-XX:OnSpinWaitInst=none");
        command.add(Launcher.class.getName());

        // Test C2 compiler
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        // The test is applicable only to C2 (present in Server VM).
        if (analyzer.getStderr().contains("Server VM")) {
            analyzer.shouldNotContain("java.lang.Thread::onSpinWait (1 bytes)   (intrinsic)");
        }
    }

    static class Launcher {

        public static void main(final String[] args) throws Exception {
            int end = 20_000;

            for (int i=0; i < end; i++) {
                test();
            }
        }
        static void test() {
            java.lang.Thread.onSpinWait();
        }
    }
}
