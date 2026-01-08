/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

package gc.arguments;

/*
 * @test id=Serial
 * @summary Test defaults processing for -XX:+ParallelRefProcEnabled with Serial GC.
 * @library /test/lib
 * @library /
 * @requires vm.gc.Serial
 * @run driver gc.arguments.TestParallelRefProc Serial
 */

/*
 * @test id=Parallel
 * @summary Test defaults processing for -XX:+ParallelRefProcEnabled with Parallel GC.
 * @library /test/lib
 * @library /
 * @requires vm.gc.Parallel
 * @run driver gc.arguments.TestParallelRefProc Parallel
 */

/*
 * @test id=G1
 * @summary Test defaults processing for -XX:+ParallelRefProcEnabled with G1 GC.
 * @library /test/lib
 * @library /
 * @requires vm.gc.G1
 * @run driver gc.arguments.TestParallelRefProc G1
 */

import java.util.Arrays;
import java.util.ArrayList;

import jdk.test.lib.process.OutputAnalyzer;

public class TestParallelRefProc {

    public static void main(String args[]) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Test type must be specified as argument");
        }

        String testType = args[0];

        switch (testType) {
            case "Serial":
                testSerial();
                break;
            case "Parallel":
                testParallel();
                break;
            case "G1":
                testG1();
                break;
            default:
                throw new IllegalArgumentException("Unknown test type \"" + testType + "\"");
        }
    }

    private static void testSerial() throws Exception {
        testFlag(new String[] { "-XX:+UseSerialGC" }, false);
    }

    private static void testParallel() throws Exception {
        testFlag(new String[] { "-XX:+UseParallelGC", "-XX:ParallelGCThreads=1" }, false);
        testFlag(new String[] { "-XX:+UseParallelGC", "-XX:ParallelGCThreads=2" }, true);
        testFlag(new String[] { "-XX:+UseParallelGC", "-XX:-ParallelRefProcEnabled", "-XX:ParallelGCThreads=2" }, false);
    }

    private static void testG1() throws Exception {
        testFlag(new String[] { "-XX:+UseG1GC", "-XX:ParallelGCThreads=1" }, false);
        testFlag(new String[] { "-XX:+UseG1GC", "-XX:ParallelGCThreads=2" }, true);
        testFlag(new String[] { "-XX:+UseG1GC", "-XX:-ParallelRefProcEnabled", "-XX:ParallelGCThreads=2" }, false);
    }

    private static final String parallelRefProcEnabledPattern =
        " *bool +ParallelRefProcEnabled *= *true +\\{product\\}";

    private static final String parallelRefProcDisabledPattern =
        " *bool +ParallelRefProcEnabled *= *false +\\{product\\}";

    private static void testFlag(String[] args, boolean expectedTrue) throws Exception {
        ArrayList<String> result = new ArrayList<String>();
        result.addAll(Arrays.asList(args));
        result.add("-XX:+PrintFlagsFinal");
        result.add("-version");
        OutputAnalyzer output = GCArguments.executeTestJava(result);

        output.shouldHaveExitValue(0);

        final String expectedPattern = expectedTrue ? parallelRefProcEnabledPattern : parallelRefProcDisabledPattern;

        String value = output.firstMatch(expectedPattern);
        if (value == null) {
            throw new RuntimeException(
                Arrays.toString(args) + " didn't set ParallelRefProcEnabled to " + expectedTrue + " as expected");
        }
    }
}
