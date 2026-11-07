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

package gc.z;

/*
 * @test TestMaxTenuringThreshold
 * @bug 8394681
 * @requires vm.gc.Z & vm.flagless
 * @summary Check ZGC tenuring ergonomics after NUMA initialization
 * @library / /test/lib
 * @run driver gc.z.TestMaxTenuringThreshold
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gc.arguments.FlagsValue;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestMaxTenuringThreshold {
    private static OutputAnalyzer run(String... options) throws Exception {
        List<String> args = new ArrayList<>(List.of(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+UseZGC",
                "-XX:-UseNUMA",
                "-Xms128m",
                "-Xmx128m",
                "-XX:ActiveProcessorCount=4",
                "-XX:ConcGCThreads=1",
                "-XX:ZYoungCompactionLimit=25",
                "-XX:+PrintFlagsFinal"));
        Collections.addAll(args, options);
        args.add("-version");
        return ProcessTools.executeProcess(
                ProcessTools.createLimitedTestJavaProcessBuilder(args));
    }

    private static void check(String name, long expected, boolean alwaysTenure,
                              String... options) throws Exception {
        OutputAnalyzer output = run(options).shouldHaveExitValue(0);
        String stdout = output.getStdout();
        long actual = FlagsValue.getFlagLongValue("MaxTenuringThreshold", stdout);
        System.out.println(name + ": MaxTenuringThreshold=" + actual);
        if (actual != expected) {
            throw new RuntimeException(name + ": expected MaxTenuringThreshold=" + expected
                    + ", actual=" + actual);
        }
        if (FlagsValue.getFlagBoolValue("AlwaysTenure", stdout) != alwaysTenure
                || FlagsValue.getFlagBoolValue("NeverTenure", stdout)) {
            throw new RuntimeException("Unexpected tenuring flags for " + name + ":\n" + stdout);
        }
    }

    public static void main(String[] args) throws Exception {
        // One worker, 2M small pages and 4M medium pages give 6M headroom per age.
        // The existing heuristic stops at age 6, when the estimate reaches 25% of 128M.
        check("Default", 6, false);
        check("Explicit maximum", 10, false, "-XX:MaxTenuringThreshold=10");
        check("Fixed threshold", 10, false, "-XX:ZTenuringThreshold=10");
        check("Zero compaction limit", 0, true, "-XX:ZYoungCompactionLimit=0");

        run("-XX:ZYoungCompactionLimit=0", "-XX:-AlwaysTenure")
                .shouldHaveExitValue(1)
                .shouldContain("MaxTenuringThreshold (0) should match");
        System.out.println("Incompatible zero threshold: rejected");
    }
}
