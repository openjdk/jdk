/*
 * Copyright (c) 2016, 2018, Red Hat, Inc. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary Test that Shenandoah arguments are checked for ranges where applicable
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestArgumentRanges
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestArgumentRanges {
    public static void main(String[] args) throws Exception {
        testRange("ShenandoahGarbageThreshold", 0, 100);
        testRange("ShenandoahMinFreeThreshold", 0, 100);
        testRange("ShenandoahAllocationThreshold", 0, 100);
        testHeuristics();
    }

    private static void testHeuristics() throws Exception {

        {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahGCHeuristics=aggressive",
                    "-version");
            output.shouldHaveExitValue(0);
        }
        {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahGCHeuristics=static",
                    "-version");
            output.shouldHaveExitValue(0);
        }
        {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:ShenandoahGCHeuristics=fluff",
                    "-version");
            output.shouldMatch("Unknown -XX:ShenandoahGCHeuristics option");
            output.shouldHaveExitValue(1);
        }
    }

    private static void testRange(String option, int min, int max) throws Exception {
        {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:" + option + "=" + (max + 1),
                    "-version");
            output.shouldHaveExitValue(1);
        }
        {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:" + option + "=" + max,
                    "-version");
            output.shouldHaveExitValue(0);
        }
        {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:" + option + "=" + (min - 1),
                    "-version");
            output.shouldHaveExitValue(1);
        }
        {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xmx128m",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC",
                    "-XX:" + option + "=" + min,
                    "-version");
            output.shouldHaveExitValue(0);
        }
    }
}
