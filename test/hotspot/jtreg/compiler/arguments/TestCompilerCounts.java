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

/*
 * @test
 * @library /test/lib /
 * @bug 8356000
 * @requires vm.flagless
 * @requires vm.bits == "64"
 * @run driver compiler.arguments.TestCompilerCounts
 */

package compiler.arguments;

import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.function.Function;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.cli.CommandLineOptionTest;

public class TestCompilerCounts {

    // Try not to go over max CPU count on the machine, since we do not
    // know if the rest of runtime would accept it.
    // For local testing, feel free to override this to a larger value
    // if you want to see how heuristics works on even larger machines.
    static final int MAX_CPUS = Runtime.getRuntime().availableProcessors();

    // Test at most 16 CPUs linearly, to limit test execution time.
    // After this limit, go for exponential steps.
    static final int MAX_LINEAR_CPUS = Math.min(16, MAX_CPUS);

    public static void main(String[] args) throws Throwable {
        // CICompilerCount=0 is incorrect in default modes.
        fail("-XX:CICompilerCount=0");

        // Interpreter-only mode accepts all values, but sets 0 threads
        pass(0, "-XX:CICompilerCount=1", "-XX:TieredStopAtLevel=0");

        // C1/C2 only modes accept CICompilerCount=1
        pass(1, "-XX:CICompilerCount=1", "-XX:TieredStopAtLevel=1");
        pass(1, "-XX:CICompilerCount=1", "-XX:TieredStopAtLevel=2");
        pass(1, "-XX:CICompilerCount=1", "-XX:TieredStopAtLevel=3");
        pass(1, "-XX:CICompilerCount=1", "-XX:-TieredCompilation");

        // C1+C2 modes need at least 2 threads
        fail("-XX:CICompilerCount=1");
        fail("-XX:CICompilerCount=1", "-XX:TieredStopAtLevel=4");

        // Overriding the CICompilerCount overrides compiler counts hard.
        for (int count = 2; count <= MAX_CPUS; count += (count >= MAX_LINEAR_CPUS ? count : 1)) {
            String opt = "-XX:CICompilerCount=" + count;

            // Interpreter-only mode always sets 0 threads
            pass(0, opt, "-XX:TieredStopAtLevel=0");

            // All compiled modes accept reasonable CICompilerCount
            pass(count, opt);
            pass(count, opt, "-XX:TieredStopAtLevel=1");
            pass(count, opt, "-XX:TieredStopAtLevel=2");
            pass(count, opt, "-XX:TieredStopAtLevel=3");
            pass(count, opt, "-XX:TieredStopAtLevel=4");
            pass(count, opt, "-XX:-TieredCompilation");
        }

        // Per CPU heuristics is disabled, we are going to set up defaults.

        for (int cpus = 2; cpus <= MAX_CPUS; cpus += (cpus >= MAX_LINEAR_CPUS ? cpus : 1)) {
            String opt = "-XX:ActiveProcessorCount=" + cpus;
            String opt2 = "-XX:-CICompilerCountPerCPU";

            // Interpreter-only mode always set 0 threads
            pass(0, opt, opt2, "-XX:TieredStopAtLevel=0");

            // All compiled modes default to 2 threads, statically compiled in
            pass(2, opt, opt2);
            pass(2, opt, opt2, "-XX:TieredStopAtLevel=1");
            pass(2, opt, opt2, "-XX:TieredStopAtLevel=2");
            pass(2, opt, opt2, "-XX:TieredStopAtLevel=3");
            pass(2, opt, opt2, "-XX:TieredStopAtLevel=4");
            pass(2, opt, opt2, "-XX:-TieredCompilation");
        }

        // Otherwise, we set CICompilerCount heuristically.

        // Check hitting the lower values exactly first.
        for (int cpus = 1; cpus <= 3; cpus++) {
            String opt = "-XX:ActiveProcessorCount=" + cpus;

            // Interpreter-only mode always set 0 threads
            pass(0, opt, "-XX:TieredStopAtLevel=0");

            // Non-tiered modes set 1 thread
            pass(1, opt, "-XX:TieredStopAtLevel=1");
            pass(1, opt, "-XX:TieredStopAtLevel=2");
            pass(1, opt, "-XX:TieredStopAtLevel=3");
            pass(1, opt, "-XX:-TieredCompilation");

            // Tiered modes set 2 threads
            pass(2, opt);
            pass(2, opt, "-XX:TieredStopAtLevel=4");
        }

        // Check what heuristics sets past the trivial number of CPUs.
        for (int cpus = 4; cpus <= MAX_CPUS; cpus += (cpus >= MAX_LINEAR_CPUS ? cpus : 1)) {
            String opt = "-XX:ActiveProcessorCount=" + cpus;

            // Interpreter-only mode always set 0 threads
            pass(0, opt, "-XX:TieredStopAtLevel=0");

            // Non-tiered modes
            int nonTieredCount = heuristicCount(cpus, false);
            pass(nonTieredCount, opt, "-XX:TieredStopAtLevel=1");
            pass(nonTieredCount, opt, "-XX:TieredStopAtLevel=2");
            pass(nonTieredCount, opt, "-XX:TieredStopAtLevel=3");
            pass(nonTieredCount, opt, "-XX:-TieredCompilation");

            // Tiered modes
            int tieredCount = heuristicCount(cpus, true);
            pass(tieredCount, opt);
            pass(tieredCount, opt, "-XX:TieredStopAtLevel=4");

            // Also check that heuristics did not set up more threads than CPUs available
            Asserts.assertTrue(nonTieredCount <= cpus,
                "Non-tiered count is larger than number of CPUs: " + nonTieredCount + " > " + cpus);
            Asserts.assertTrue(tieredCount <= cpus,
                "Tiered count is larger than number of CPUs: " + tieredCount + " > " + cpus);
        }
    }

    // Direct translation from CompilationPolicy::initialize:
    public static int heuristicCount(int cpus, boolean tiered) {
        int log_cpu = log2(cpus);
        int loglog_cpu = log2(Math.max(log_cpu, 1));
        int min_count = tiered ? 2 : 1;
        return Math.max(log_cpu * loglog_cpu * 3 / 2, min_count);
    }

    public static int log2(int v) {
        return (int)(Math.log(v) / Math.log(2));
    }

    public static void pass(int count, String... args) throws Throwable {
        CommandLineOptionTest.verifyOptionValueForSameVM("CICompilerCount", "" + count, "", args);
    }

    public static void fail(String... args) throws Throwable {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
    }

}
