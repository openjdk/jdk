/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2023, Huawei Technologies Co., Ltd. All rights reserved.
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
 * @test TestOnSpinWaitRISCV64
 * @summary Checks that java.lang.Thread.onSpinWait is intrinsified with instructions
 * @library /test/lib
 *
 * @requires vm.flagless
 * @requires os.arch=="riscv64"
 *
 * @run driver compiler.onSpinWait.TestOnSpinWaitRISCV64 c1
 * @run driver compiler.onSpinWait.TestOnSpinWaitRISCV64 c2
 */

package compiler.onSpinWait;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOnSpinWaitRISCV64 {
    public static void main(String[] args) throws Exception {
        String compiler = args[0];
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-showversion");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+PrintCompilation");
        command.add("-XX:+PrintInlining");
        command.add("-XX:+UnlockExperimentalVMOptions");
        command.add("-XX:+UseZihintpause");
        if (compiler.equals("c2")) {
            command.add("-XX:-TieredCompilation");
        } else if (compiler.equals("c1")) {
            command.add("-XX:+TieredCompilation");
            command.add("-XX:TieredStopAtLevel=1");
        } else {
            throw new RuntimeException("Unknown compiler: " + compiler);
        }
        command.add("-Xbatch");
        command.add(Launcher.class.getName());

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        if (compiler.equals("c2")) {
            analyzer.shouldContain("java.lang.Thread::onSpinWait (1 bytes)   (intrinsic)");
        } else {
            analyzer.shouldContain("java.lang.Thread::onSpinWait (1 bytes)   intrinsic");
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
