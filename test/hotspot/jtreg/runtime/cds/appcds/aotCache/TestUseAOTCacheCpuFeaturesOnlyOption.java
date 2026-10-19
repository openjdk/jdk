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
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*sse3.*") | (os.simpleArch == "aarch64" & vm.cpu.features ~= ".*crc32.*")
 * @library /test/lib /test/setup_aot
 * @build HelloWorld
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar HelloWorld
 * @run driver TestUseAOTCacheCpuFeaturesOnlyOption
 */

import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class TestUseAOTCacheCpuFeaturesOnlyOption {
    public static void main(String... args) throws Exception {
        Tester tester = new Tester();
        tester.runAOTTrainingAndAssemblyWorkflow();

        // Production run should fail due to incompatible cpu features, so avoid checking exit value
        tester.setCheckExitValue(false);
        tester.productionRun();

        tester.enableUseAOTCacheCpuFeaturesOnlyOption();
        // Production run with UseAOTCacheCpuFeaturesOnlyOption should pass
        tester.setCheckExitValue(true);
        tester.productionRun();
    }

    static class Tester extends CDSAppTester {
        static final String appJar = ClassFileInstaller.getJarPath("app.jar");
        static final String mainClass = "HelloWorld";
        private boolean useAOTCacheCpuFeaturesOnlyOption = false;

        public Tester() {
            super(mainClass);
        }

        void enableUseAOTCacheCpuFeaturesOnlyOption() {
            useAOTCacheCpuFeaturesOnlyOption = true;
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
                }
            } else if (runMode == RunMode.PRODUCTION) {
                if (useAOTCacheCpuFeaturesOnlyOption) {
                    return new String[] {"-XX:+UseAOTCacheCpuFeaturesOnly"};
                }
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
                if (!useAOTCacheCpuFeaturesOnlyOption) {
                    out.shouldContain("AOT Code Cache disabled: cpu features are incompatible");
                    out.shouldNotContain("HelloWorld");
                } else {
                    out.shouldContain("HelloWorld");
                }
            }
        }
    }
}
