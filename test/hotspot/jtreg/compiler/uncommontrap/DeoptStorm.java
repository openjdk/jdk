/*
 * Copyright (c) 2026, BELLSOFT. All rights reserved.
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

package compiler.uncommontrap;

import java.util.List;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8374307
 * @summary The change reproduces the unstable_if action_none deoptimization storm
 * @library /test/lib
 *
 * @run main/othervm compiler.uncommontrap.DeoptStorm
 */
public class DeoptStorm {

    private static final int MAX_DEOPT_LINES = 100;
    private static final String DEOPT_TAG = "[debug][deoptimization]";

    public static void main(String[] args) throws Exception {
        if (args.length >= 1) {
            // Child process: execute workload that produces deoptimizations.
            new DeoptStorm().run();
            return;
        }

        // Parent process: spawn child with deopt logging enabled and validate output.
        String className = DeoptStorm.class.getName();
        String[] procArgs = {
            "-XX:PerMethodRecompilationCutoff=2",
            "-XX:CompileCommand=dontinline,compiler.uncommontrap::*",
            "-Xlog:deoptimization=debug",
            className, "dummy"};
        ProcessBuilder pb  = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        List<String> lines = out.asLines();
        long deoptCount = lines.stream().filter(l -> l.contains(DEOPT_TAG)).count();
        if (deoptCount > MAX_DEOPT_LINES) {
            System.out.println(out.getStdout());
            throw new RuntimeException("Failed: too many deoptimization report lines: " + deoptCount + " > " + MAX_DEOPT_LINES);
        }
        out.shouldHaveExitValue(0);
    }

    private static int iteration;
    private static char[] data;
    private static int max_index;

    public void run() {
        max_index = 999_999;
        data = new char[max_index + 1];
        data[max_index] = 'a';
        iteration = 0;
        for (int i = 0; i < 100_000_000; i++) {
            deoptStorm();
        }
    }

    // Run with: -XX:PerMethodRecompilationCutoff=2
    // Expect: the 3rd deoptimization reaches the recompilation cutoff;
    //         the method is no longer recompiled or made non-entrant,
    //         so each call repeatedly triggers the same [unstable_if trap -> deopt].
    public int deoptStorm() {
        iteration = (iteration < max_index) ? iteration + 1 : 0;

        // First array pass: profiling makes C2 believe this condition is always true,
        // so it compiles the false branch as an uncommon trap (action=reinterpret).
        // When the false branch executes, it triggers uncommon_trap and deoptimizes.
        if (data[iteration] == 'a') { data[iteration] = 'b'; return 1; }

        // Second pass: we hit the unstable_if uncommon trap again (now with the recompiled nmethod).
        if (data[iteration] == 'b') { data[iteration] = 'c'; return 2; }

        // After reaching the recompilation cutoff, C2 may emit uncommon_trap with action=none,
        // so deoptimization no longer triggers recompilation. If executed frequently, this can
        // produce a deoptimization storm.
        if (data[iteration] == 'c') { max_index = 1; data[max_index] = 'c'; return 3; }

        return 0;
    }
}