/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

/*
 * @test
 * @bug 8395992
 * @requires vm.gc.G1
 * @summary Test valid and invalid combinations of ConcGCThreads and ParallelGCThreads and the expected error message when invalid.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run driver gc.g1.TestConcParallelGCThreadsCombinations
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestConcParallelGCThreadsCombinations {

    public static void runTest(int concGCThreads, int parallelGCThreads, boolean expectSuccess) throws Exception {
        final String[] arguments = {
            "-XX:+UseG1GC",
            "-Xmx10M",
            "-XX:ConcGCThreads=" + concGCThreads,
            "-XX:ParallelGCThreads=" + parallelGCThreads,
            GCTest.class.getName()
            };

        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(arguments);

        System.out.println(output.getStdout());

        if (expectSuccess) {
            output.shouldHaveExitValue(0);
        } else {
            output.shouldNotHaveExitValue(0);

            String pattern = "G1 expects ConcGCThreads (" + concGCThreads + ") <= ParallelGCThreads (" + parallelGCThreads + "), " +
                             "verify the values of -XX:ParallelGCThreads and -XX:ConcGCThreads";
            if (output.getStdout().indexOf(pattern) == -1) {
                throw new RuntimeException("Could not find expected error message.");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        runTest(0, 1, true);
        runTest(1, 2, true);
        runTest(2, 1, false);
    }

    static class GCTest {
        public static void main(String [] args) {
            // Nothing to do.
        }
    }

}
