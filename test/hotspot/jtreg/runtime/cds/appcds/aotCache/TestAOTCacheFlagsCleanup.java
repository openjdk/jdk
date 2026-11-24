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
 * @summary This test checks that relevant flags related to AOTCache are turned off when the cache validation fails.
 * @bug 8386853
 * @requires vm.cds
 * @library /test/lib /test/setup_aot
 * @build jdk.test.whitebox.WhiteBox TestAOTCacheFlagsCleanup HelloWorld
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar HelloWorld
 * @run driver TestAOTCacheFlagsCleanup
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;

public class TestAOTCacheFlagsCleanup {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");

    public static void main(String args[]) throws Exception {
        System.out.println("Testing non-iterative AOT workflow");
        testNonIterativeFlow();
        System.out.println("Testing iterative AOT workflow");
        testIterativeFlow();
    }

    public static void testNonIterativeFlow() throws Exception {
        NonIterativeTester tester = new NonIterativeTester();
        tester.runAOTWorkflow();
    }

    public static void testIterativeFlow() throws Exception {
        IterativeTester tester = new IterativeTester();
        tester.runAOTRetrainWorkflow(true);
    }

    static class NonIterativeTester extends CDSAppTester {
        public NonIterativeTester() {
            super("NonIterativeTester");
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.PRODUCTION) {
                // Use ObjectAlignementInBytes=64 to trigger base AOTCache invalidation
                // Use AOTMode=auto to override the AOTMode=required which is default behavior of CDSAppTester
                // to avoid JVM exit on AOTCache invalidation
                return new String[] {
                    "-XX:ObjectAlignmentInBytes=64",
                    "-XX:+PrintFlagsFinal",
                    "-XX:AOTMode=auto"
                };
            }
            return new String[0];
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                "HelloWorld"
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.PRODUCTION) {
                out.shouldMatch("AOTReplayTraining\\s+= false");
                out.shouldMatch("AutoCreateSharedArchive\\s+= false");
                out.shouldMatch("AOTRecordTraining\\s+= false");
                out.shouldMatch("AOTCompileEagerly\\s+= false");
                out.shouldMatch("AOTCodeCaching\\s+= false");
                out.shouldMatch("AOTStubCaching\\s+= false");
                out.shouldMatch("AOTAdapterCaching\\s+= false");
                // When AOTCache header validation fails, UseAOTCacheCpuFeaturesOnly is set to false
                out.shouldMatch("UseAOTCacheCpuFeaturesOnly\\s+= false");
            }
        }
    }

    static class IterativeTester extends CDSAppTester {
        public IterativeTester() {
            super("IterativeTester");
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (isAOTRetraining()) {
                if ((runMode == RunMode.TRAINING) || (runMode == RunMode.ASSEMBLY)) {
                    // Base archive is created with default ObjectAlignementInBytes (8 bytes)).
                    // Use ObjectAlignementInBytes=64 to trigger base AOTCache invalidation.
                    // This test relies on output of +PrintFlagsFinal, therefore we also need to set
                    // +AOTIgnoreIncompatibleCacheInTraining to prevent JVM exit when base AOTCache
                    // is invalidated during the iterative training run. It also implies the new AOTCache
                    // is created with -XX:ObjectAlignmentInBytes=64. So to force AOTCache invalidation durign
                    // production run, we should not specify this option.
                    return new String[] {
                        "-XX:ObjectAlignmentInBytes=64",
                        "-XX:+PrintFlagsFinal",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+AOTIgnoreIncompatibleCacheInTraining"
                    };
                } else if (runMode == RunMode.PRODUCTION) {
                    // Use AOTMode=auto to override the AOTMode=required which is default behavior of CDSAppTester
                    // to avoid JVM exit on AOTCache invalidation
                    return new String[] {
                        "-XX:+PrintFlagsFinal",
                        "-XX:AOTMode=auto"
                    };
                }
            }
            return new String[0];
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                "HelloWorld"
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (isAOTRetraining()) {
                // If the base archive is invalidated, but the JVM continues to run iterative training run
                // (because +AOTIgnoreIncompatibleCacheInTraining is specified), not all flags are cleared.
                if (runMode == RunMode.TRAINING) {
                    out.shouldMatch("AOTReplayTraining\\s+= false");
                    out.shouldMatch("AutoCreateSharedArchive\\s+= false");
                    out.shouldMatch("AOTRecordTraining\\s+= true");
                    out.shouldMatch("AOTCompileEagerly\\s+= false");
                    out.shouldMatch("AOTCodeCaching\\s+= true");
                    out.shouldMatch("AOTStubCaching\\s+= true");
                    out.shouldMatch("AOTAdapterCaching\\s+= true");
                    // When AOTCache header validation fails, UseAOTCacheCpuFeaturesOnly is set to false
                    out.shouldMatch("UseAOTCacheCpuFeaturesOnly\\s+= false");
                }
                if (runMode == RunMode.PRODUCTION) {
                    out.shouldMatch("AOTReplayTraining\\s+= false");
                    out.shouldMatch("AutoCreateSharedArchive\\s+= false");
                    out.shouldMatch("AOTRecordTraining\\s+= false");
                    out.shouldMatch("AOTCompileEagerly\\s+= false");
                    out.shouldMatch("AOTCodeCaching\\s+= false");
                    out.shouldMatch("AOTStubCaching\\s+= false");
                    out.shouldMatch("AOTAdapterCaching\\s+= false");
                    // When AOTCache header validation fails, UseAOTCacheCpuFeaturesOnly is set to false
                    out.shouldMatch("UseAOTCacheCpuFeaturesOnly\\s+= false");
                }
            }
        }
    }
}
