/*
 * Copyright (c) 2021, Amazon.com Inc. or its affiliates. All rights reserved.
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
 * @test TestOnSpinWaitImplC1AArch64
 * @summary Checks that java.lang.Thread.onSpinWait is intrinsified
 * @bug 8186670
 * @library /test/lib
 *
 * @requires vm.flagless
 * @requires os.arch=="aarch64"
 * @requires vm.compiler1.enabled
 *
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplC1AArch64 4 nop
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplC1AArch64 3 isb
 * @run driver compiler.onSpinWait.TestOnSpinWaitImplC1AArch64 2 yield
 */

package compiler.onSpinWait;

import java.util.ArrayList;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOnSpinWaitImplC1AArch64 {

    public static void main(String[] args) throws Exception {
        String pauseImplInst = args[1];
        String pauseImplInstCount = args[0];
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-showversion");
        command.add("-XX:+TieredCompilation");
        command.add("-XX:TieredStopAtLevel=1");
        command.add("-Xbatch");
        command.add("-XX:+PrintCompilation");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+PrintInlining");
        command.add("-XX:UsePauseImpl=" + pauseImplInstCount + pauseImplInst);
        command.add(Launcher.class.getName());

        // Test C1 compiler
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain("java.lang.Thread::onSpinWait (1 bytes)   intrinsic");
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
