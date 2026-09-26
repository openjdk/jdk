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
 *
 */

/**
 * @test
 * @summary Test AOT code invocation counters in C2 compiled AOT code
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @comment C2 JIT compiler is required because the test verifies
 *          compiled code generation.
 * @library /test/lib /test/setup_aot
 * @build ${test.main.class} AOTCodeSimpleTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AOTCodeSimpleTestApp
 * @run driver/timeout=480 ${test.main.class}
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;

public class TestAOTCodeCounters {
    private static final Random RANDOM = Utils.getRandomInstance();

    // Test default (Base=100. Scale=1.) and limit values.
    static String randomBase  = String.valueOf(RANDOM.nextDouble(1.0, 10000.0));
    static String randomScale = String.valueOf(RANDOM.nextDouble(0.001, 1000.0));
    static String[][] flags = {
        {"-XX:AOTCodeInvokeBase=1.0",     "-XX:AOTCodeInvokeScale=0.001"},
        {"-XX:AOTCodeInvokeBase=1.0",     "-XX:AOTCodeInvokeScale=1.0"},
        {"-XX:AOTCodeInvokeBase=1.0",     "-XX:AOTCodeInvokeScale=1000.0"},
        {"-XX:AOTCodeInvokeBase=100.0",   "-XX:AOTCodeInvokeScale=0.001"},
        {"-XX:AOTCodeInvokeBase=100.0",   "-XX:AOTCodeInvokeScale=1.0"},
        {"-XX:AOTCodeInvokeBase=100.0",   "-XX:AOTCodeInvokeScale=1000.0"},
        {"-XX:AOTCodeInvokeBase=10000.0", "-XX:AOTCodeInvokeScale=0.001"},
        {"-XX:AOTCodeInvokeBase=10000.0", "-XX:AOTCodeInvokeScale=1.0"},
        {"-XX:AOTCodeInvokeBase=10000.0", "-XX:AOTCodeInvokeScale=1000.0"},
        {"-XX:AOTCodeInvokeBase=" + randomBase, "-XX:AOTCodeInvokeScale=" + randomScale}
    };

    public static void main(String... args) throws Exception {
        System.out.println("Random: -XX:AOTCodeInvokeBase=" + randomBase + " -XX:AOTCodeInvokeScale=" + randomScale);
        for (int i = 0; i < flags.length; i++) {
          Tester t = new Tester(flags[i]);
          t.run(new String[] {"AOT", "--two-step-training"});
        }
    }

    static class Tester extends CDSAppTester {
        private String[] Counterflags;

        public Tester(String[] flags) {
            super("TestAOTCodeCounters");
            Counterflags = flags;
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            List<String> args = new ArrayList<String>();

            // Ensure compilations are finished before the JVM exits.
            args.add("-Xbatch");

            // Add flags for logs
            args.addAll(List.of("-Xlog:aot+codecache+init=debug",
                                "-Xlog:aot+codecache+nmethod=info",
                                "-Xlog:aot+codecache+exit=debug"));
            // Add diagnostic flags
            args.addAll(List.of("-XX:+UnlockDiagnosticVMOptions",
                                "-XX:+UseAOTCodeCounters",
                                "-XX:+AbortVMOnAOTCodeFailure"));
            // Add feature flag
            args.addAll(Arrays.asList(Counterflags));
            return args.toArray(new String[args.size()]);
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] { "AOTCodeSimpleTestApp" };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.ASSEMBLY) {
                out.shouldMatch("aot,codecache,exit.*\\s+AOT code cache size: [1-9]\\d+ bytes");
                out.shouldMatch("Nmethod:\\s+total=[1-9][0-9]+");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldMatch("aot,codecache,init.*\\s+Loaded [1-9]\\d+ AOT code entries from AOT Code Cache");
                out.shouldMatch("Loaded nmethod .*");
            }
        }
    }
}
