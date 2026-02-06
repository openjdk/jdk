/*
 * Copyright (c) 2026 IBM and/or its affiliates. All rights reserved.
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

package compiler.loopopts;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @test
 * @bug 8353290
 * @summary test loop limit checks are inserted when stressing int counted loops to long counted loops
 * @library /test/lib
 * @requires vm.debug == true & vm.compiler2.enabled
 * @run driver compiler.loopopts.TestStressLongCountedLoopLimitChecks
 */
public class TestStressLongCountedLoopLimitChecks {
    public static void main(String[] args) throws Exception {
        test(BasicLauncher.class, 1, "-XX:StressLongCountedLoop=0");
        test(BasicLauncher.class, 1, "-XX:StressLongCountedLoop=2000000");

        test(LargeStrideLauncher.class, 2, "-XX:StressLongCountedLoop=0");
        test(LargeStrideLauncher.class, 2, "-XX:StressLongCountedLoop=2000000");
    }

    private static void test(Class launcher, int limitChecks, String... flags) throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                Stream.concat(Arrays.stream(flags), Stream.of(
                        "-XX:+TraceLoopLimitCheck",
                        "-XX:CompileOnly=" + launcher.getName() + "::test*",
                        "-Xcomp",
                        launcher.getName()
                )).toList()
        );

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);

        analyzer.outputTo(System.out);
        analyzer.errorTo(System.err);

        Asserts.assertEQ(
                limitChecks,
                (int) analyzer.asLines().stream().filter(
                        l -> l.trim().matches("Counted Loop Limit Check generated:")
                ).count(),
                "wrong numbers of loop limit checks"
        );
    }

    public static class BasicLauncher {
        static int x, y, z;

        public static void main(String[] args) throws Exception {
            test();
        }

        static void test() {
            int i = x; // Any int
            do {
                x += y;
                i++; // Could overflow and thus we need a Loop Limit Check Predicate "i < z"
            } while (i < z);
        }
    }

    public static class LargeStrideLauncher {
        static final int STRIDE = 100_000;

        public static void main(String[] args) throws Exception {
            Asserts.assertEQ(10_000_000L / STRIDE, test(0, 10_000_000), "loop not stopped");
            Asserts.assertEQ(-1L, test(0, Integer.MAX_VALUE), "loop stopped prematurely");
        }

        static long ONE = 1; // Just so the compiler doesn't try to IV replace the whole thing

        public static long test(int init, int limit) {
            final int stride = 100_000;

            long iterations = 0;
            for (int i = init; i < limit; i += 100000) {
                iterations += ONE;

                if (iterations > (limit / stride) + 1) { // No it's not stopping, as we should expect.
                    return -1;
                }
            }
            return iterations; // Possibly stopping prematurely.
        }
    }
}
