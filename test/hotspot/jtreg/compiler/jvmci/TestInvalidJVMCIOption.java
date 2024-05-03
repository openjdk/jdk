/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestInvalidJVMCIOption
 * @bug 8257220
 * @summary Ensures invalid JVMCI options do not crash the VM with a hs-err log.
 * @requires vm.flagless
 * @requires vm.jvmci
 * @library /test/lib
 * @run driver TestInvalidJVMCIOption
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestInvalidJVMCIOption {

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+EagerJVMCI",
            "-XX:+UseJVMCICompiler",
            "-Djvmci.XXXXXXXXX=true");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        String expectStdout = String.format(
            "Error parsing JVMCI options: Could not find option jvmci.XXXXXXXXX%n" +
            "Error: A fatal exception has occurred. Program will exit.%n");

        // Test for containment instead of equality as -XX:+EagerJVMCI means
        // the main thread and one or more libjvmci compiler threads
        // may initialize libjvmci at the same time and thus the error
        // message can appear multiple times.
        output.stdoutShouldContain(expectStdout);

        output.stderrShouldBeEmpty();
        output.shouldHaveExitValue(1);
    }
}
