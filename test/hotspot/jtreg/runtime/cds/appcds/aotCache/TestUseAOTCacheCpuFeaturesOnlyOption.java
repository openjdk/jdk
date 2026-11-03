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
/*
 * @test
 * @summary Test UseAOTCacheCpuFeaturesOnly option works correctly
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compMode != "Xcomp" & vm.compMode != "Xint"
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*sse3.*") |
             (os.simpleArch == "aarch64" & vm.cpu.features ~= ".*crc32.*") |
             (os.simpleArch == "riscv64" & vm.cpu.features ~= ".*zba.*")
 * @library /test/lib /test/setup_aot
 * @build HelloWorld
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar HelloWorld
 * @run driver TestUseAOTCacheCpuFeaturesOnlyOption
 */

import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

import java.util.ArrayList;
import java.util.List;

public class TestUseAOTCacheCpuFeaturesOnlyOption {
    static int testNum = 0;

    enum OptionValue {
        DEFAULT,
        TRUE,
        FALSE
    }

    public static void main(String... args) throws Exception {
        Tester tester = new Tester();
        // This creates AOTCache with a cpu feature (like sse3 on x86) disabled, even though the
        // cpu supports it.
        tester.runAOTTrainingAndAssemblyWorkflow();

        // Production run with default value of UseAOTCacheCpuFeaturesOnlyOption.
        // UseAOTCacheCpuFeaturesOnlyOption should get set ergonomically and the test should pass
        printTestCase("Use default value of UseAOTCacheCpuFeaturesOnlyOption");
        tester.setOptionValue(OptionValue.DEFAULT);
        tester.setCheckExitValue(true);
        tester.productionRun();

        printTestCase("Turn on UseAOTCacheCpuFeaturesOnlyOption on command line");
        tester.setOptionValue(OptionValue.TRUE);
        // Production run with UseAOTCacheCpuFeaturesOnlyOption should pass
        tester.setCheckExitValue(true);
        tester.productionRun();

        printTestCase("Turn off UseAOTCacheCpuFeaturesOnlyOption on command line");
        tester.setOptionValue(OptionValue.FALSE);
        // Production run should fail due to incompatible cpu features, so avoid checking exit value
        tester.setCheckExitValue(false);
        tester.productionRun();
    }

    static void printTestCase(String s) {
        System.out.println("vvvvvvv TEST CASE " + testNum + ": " + s + ": starts here vvvvvvv");
        testNum++;
    }

    static class Tester extends CDSAppTester {
        static final String appJar = ClassFileInstaller.getJarPath("app.jar");
        static final String mainClass = "HelloWorld";
        private OptionValue useAOTCacheCpuFeaturesOnlyValue;

        public Tester() {
            super(mainClass);
        }

        void setOptionValue(OptionValue value) {
            useAOTCacheCpuFeaturesOnlyValue = value;
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.ASSEMBLY) {
                if (Platform.isX64()) {
                    return new String[] {"-XX:UseSSE=2"};
                } else if (Platform.isAArch64()) {
                    return new String[] {"-XX:-UseCRC32"};
                } else if (Platform.isRISCV64()) {
                    return new String[] {"-XX:+UnlockDiagnosticVMOptions", "-XX:-UseZba"};
                }
            } else if (runMode == RunMode.PRODUCTION) {
                List<String> vmOptions = new ArrayList<>();
                if (Platform.isRISCV64()) {
                    vmOptions.add("-XX:+UnlockDiagnosticVMOptions");
                    vmOptions.add("-XX:+UseZba");
                }
                vmOptions.add("-Xlog:aot");
                vmOptions.add("-Xlog:os+cpu");
                switch(useAOTCacheCpuFeaturesOnlyValue) {
                case OptionValue.TRUE:
                    vmOptions.add("-XX:+UseAOTCacheCpuFeaturesOnly");
                    break;
                case OptionValue.FALSE:
                    vmOptions.add("-XX:-UseAOTCacheCpuFeaturesOnly");
                    break;
                case OptionValue.DEFAULT:
                    break;
                }
                return vmOptions.toArray(new String[0]);
            }
            return new String[0];
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode == RunMode.PRODUCTION) {
                switch(useAOTCacheCpuFeaturesOnlyValue) {
                case OptionValue.DEFAULT:
                    // If UseAOTCacheCpuFeaturesOnly is not specified on command line, then
                    // it is ergonomicallly set to true. So fallthrough.
                case OptionValue.TRUE:
                    out.shouldMatch("\\[info\\s*\\]\\[os,cpu\\s*\\] CPU features turned off by UseAOTCacheCpuFeaturesOnly");
                    out.shouldMatch("\\[warning\\s*\\]\\[aot\\s*\\] Some CPU features are turned off by UseAOTCacheCpuFeaturesOnly");
                    out.shouldMatch("\\[warning\\s*]\\[aot\\s*\\] CPU features disabled are");
                    out.shouldContain("HelloWorld");
                    break;
                case OptionValue.FALSE:
                    out.shouldContain("AOT Code Cache disabled: cpu features are incompatible");
                    break;
                }
            }
        }
    }
}
