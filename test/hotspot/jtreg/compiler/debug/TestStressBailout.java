/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.debug;

import jdk.test.lib.Platform;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @key stress randomness
 * @bug 8330157
 * @requires vm.compiler2.enabled
 * @summary Basic tests for bailout stress flag.
 * @library /test/lib /
 * @run driver compiler.debug.TestStressBailout
 */

public class TestStressBailout {

    static void runTest(int interval) throws Exception {
        String[] procArgs = {"-Xcomp", "-XX:-TieredCompilation",
                             "-XX:+UnlockDiagnosticVMOptions", "-XX:+StressBailout",
                             "-XX:StressBailoutInterval=" + interval, "-version"};
        ProcessBuilder pb  = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);
        if (interval == 1 && !Platform.isDebugBuild()) {
            out.shouldContain("C2 initialization failed");
        }
    }

    public static void main(String[] args) throws Exception {
        // Likely bail out on -version, for some low probability values.
        for (int i = 2; i < 10; i += 1) {
            runTest(i);
        }
        // Higher values
        for (int i = 1; i < 1_000_000; i*=10) {
            runTest(i);
        }
        // Guaranteed bail out, check output
        runTest(1);
    }
}
