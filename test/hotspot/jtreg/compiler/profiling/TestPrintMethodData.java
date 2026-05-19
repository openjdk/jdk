/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @test
 * @bug 8382777
 * @summary Test that PrintMethodData output matches expectations
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.profiling;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestPrintMethodData {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintMethodData",
            "-XX:CompileCommand=compileonly,compiler.profiling.TestPrintMethodData::test*",
            Launcher.class.getName());

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain("BranchData");
        analyzer.shouldMatch("taken\\(\\d+\\)");
    }

    static long testLongReductionSimpleMax(long[] array) {
        long result = Long.MIN_VALUE;
        for (int i = 0; i < array.length; i++) {
            var v = array[i];
            result = Math.max(v, result);
        }
        return result;
    }

    static class Launcher {
        private static final Generator<Long> GEN_LONG = Generators.G.longs();
        private static final int SIZE = 1024;

        static void main(String[] args) throws Exception {
            long[] longs = new long[SIZE];
            Generators.G.fill(GEN_LONG, longs);

            for (int i = 0; i < 20_000; i++) {
                testLongReductionSimpleMax(longs);
            }
        }
    }
}
