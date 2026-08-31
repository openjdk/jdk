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
 * @summary Sanity test AOTCodeCache flags
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          compiler's runtime blobs generation.
 * @library /test/lib /test/setup_aot
 * @build TestAOTCodeFeatures JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=1500 ${test.main.class}
 */

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class TestAOTCodeFeatures {

    // Too long to test both, default and opposite, values.
    // Only test opposite.
    static String[] flags = {
        "-XX:-AOTCompileEagerly",
        "-XX:ClassInitBarrierMode=0",
        "-XX:-UseAOTCodeCounters",
        "-XX:-PreloadReduceTraps",
        "-XX:+AOTPreloadBlocking",
        "-XX:AOTCodePreloadStart=2G",
        "-XX:AOTCodePreloadStop=0"
    };

    public static void main(String... args) throws Exception {
        for (int i = 0; i < flags.length; i++) {
          Tester t = new Tester(flags[i]);
          t.run(new String[] {"AOT", "--two-step-training"});
        }
    }

    static class Tester extends CDSAppTester {
        private String flagName;

        public Tester(String name) {
            super("TestAOTCodeFeatures");
            flagName = name;
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            List<String> args = new ArrayList<String>();
            // Add flags for logs
            args.addAll(List.of("-Xlog:aot+codecache+init=debug",
                                "-Xlog:aot+codecache+exit=debug"));
            // Add diagnostic flags
            args.addAll(List.of("-XX:+UnlockDiagnosticVMOptions",
                                "-XX:+AbortVMOnAOTCodeFailure"));
            // Add feature flag
            args.add(flagName);
            return args.toArray(new String[args.size()]);
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] { "JavacBenchApp", "1" };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.ASSEMBLY) {
                out.shouldMatch("aot,codecache,exit.*\\s+AOT code cache size: [1-9]\\d+ bytes");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldMatch("aot,codecache,init.*\\s+Loaded [1-9]\\d+ AOT code entries from AOT Code Cache");
            }
        }
    }
}
