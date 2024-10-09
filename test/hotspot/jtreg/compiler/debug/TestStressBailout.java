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

import java.util.Random;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;

/*
 * @test
 * @key stress randomness
 * @bug 8330157
 * @requires vm.debug == true & vm.compiler2.enabled & (vm.opt.AbortVMOnCompilationFailure == "null" | !vm.opt.AbortVMOnCompilationFailure)
 * @summary Basic tests for bailout stress flag.
 * @library /test/lib /
 * @run driver compiler.debug.TestStressBailout
 */

public class TestStressBailout {

    static void runTest(int invprob) throws Exception {
        String[] procArgs = {"-Xcomp", "-XX:-TieredCompilation", "-XX:+StressBailout",
                             "-XX:StressBailoutMean=" + invprob, "-version"};
        ProcessBuilder pb  = ProcessTools.createTestJavaProcessBuilder(procArgs);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);
    }

    public static void main(String[] args) throws Exception {
        Random r = Utils.getRandomInstance();
        // Likely bail out on -version, for some low Mean value.
        runTest(r.nextInt(1, 10));
        // Higher value
        runTest(r.nextInt(10, 1_000_000));
    }
}
