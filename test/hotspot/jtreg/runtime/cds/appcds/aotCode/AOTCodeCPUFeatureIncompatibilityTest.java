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
 * @summary CPU feature compatibility test for AOT Code Cache
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compMode != "Xcomp" & vm.compMode != "Xint"
 * @requires os.simpleArch == "x64" | os.simpleArch == "aarch64"
 * @comment The test verifies AOT checks during VM startup and not code generation.
 *          No need to run it with -Xcomp.
 * @library /test/lib /test/setup_aot
 * @build AOTCodeCPUFeatureIncompatibilityTest JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             JavacBenchApp
 *             JavacBenchApp$ClassFile
 *             JavacBenchApp$FileManager
 *             JavacBenchApp$SourceFile
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI AOTCodeCPUFeatureIncompatibilityTest
 */

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.cpuinfo.CPUInfo;

public class AOTCodeCPUFeatureIncompatibilityTest {
    public static void main(String... args) throws Exception {
        List<String> cpuFeatures = CPUInfo.getFeatures();
        if (Platform.isX64()) {
            // Minimum value of UseSSE required by JVM is 2. So the production run has to be executed with UseSSE=2.
            // To simulate the case of incmpatible SSE feature, we can run this test only on system with higher SSE level (sse3 or above).
            if (isSSE3Supported(cpuFeatures)) {
                testIncompatibleFeature("-XX:UseSSE=2", "sse3");
            }
            if (isAVXSupported(cpuFeatures)) {
                testIncompatibleFeature("-XX:UseAVX=0", "avx");
            }
        }
    }

    // vmOption = command line option to disable CPU feature
    // featureName = name of the CPU feature used by the JVM in the log messages
    public static void testIncompatibleFeature(String vmOption, String featureName) throws Exception {
        new CDSAppTester("AOTCodeCPUFeatureIncompatibilityTest") {
            @Override
            public String[] vmArgs(RunMode runMode) {
                if (runMode == RunMode.PRODUCTION) {
                    return new String[] {vmOption, "-Xlog:aot+codecache+init=debug"};
                }
                return new String[] {};
            }
            @Override
            public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
                if (runMode == RunMode.ASSEMBLY) {
                    out.shouldMatch("CPU features recorded in AOTCodeCache:.*" + featureName + ".*");
                } else if (runMode == RunMode.PRODUCTION) {
                    out.shouldMatch("AOT Code Cache disabled: required cpu features are missing:.*" + featureName + ".*");
                    out.shouldContain("Unable to use AOT Code Cache");
                }
            }
            @Override
            public String classpath(RunMode runMode) {
                return "app.jar";
            }
            @Override
            public String[] appCommandLine(RunMode runMode) {
                return new String[] {
                    "JavacBenchApp", "10"
                };
            }
        }.runAOTWorkflow("--two-step-training");
    }

    // Only used on x86-64 platform
    static boolean isSSE3Supported(List<String> cpuFeatures) {
        return cpuFeatures.contains("sse3");
    }

    // Only used on x86-64 platform
    static boolean isAVXSupported(List<String> cpuFeatures) {
        return cpuFeatures.contains("avx");
    }
}
