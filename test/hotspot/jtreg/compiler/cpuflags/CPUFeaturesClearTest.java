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

/*
 * @test
 * @bug 8364584 8381988
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @requires os.simpleArch == "x64" | os.simpleArch == "aarch64"
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch
 *                   compiler.cpuflags.CPUFeaturesClearTest
 */

package compiler.cpuflags;

import java.util.List;
import java.util.Map;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.cpuinfo.CPUInfo;
import static jdk.test.lib.cli.CommandLineOptionTest.*;

public class CPUFeaturesClearTest {
    private static List<String> cpuFeaturesList;
    public void runTestCases() throws Throwable {
        if (Platform.isX64()) {
            testX86Flags();
        } else if (Platform.isAArch64()) {
            testAArch64Flags();
        }
    }

    String[] generateArgs(String vmFlag) {
        String[] args = {"-Xlog:os+cpu", "-XX:+UnlockDiagnosticVMOptions", vmFlag, "-version"};
        return args;
    }

    public void testX86Flags() throws Throwable {
        Map<String, String> vmFlagToCpuFeatureMap = Map.of("UseCLMUL", "clmul",
                                                           "UseAES", "aes",
                                                           "UseFMA", "fma",
                                                           "UseCountLeadingZerosInstruction", "lzcnt",
                                                           "UseBMI1Instructions", "bmi1",
                                                           "UseBMI2Instructions", "bmi2",
                                                           "UsePopCountInstruction", "popcnt",
                                                           "UseSHA", "sha");
        vmFlagToCpuFeatureMap.forEach((vmFlag, cpuFeature) -> {
            try {
                OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareBooleanFlag(vmFlag, false)));
                outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* " + cpuFeature + ".*");
            } catch (Exception e) {
                throw new RuntimeException (e);
            }
        });
        OutputAnalyzer outputAnalyzer;
        if (isCpuFeatureSupported("sse4")) {
            outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareNumericFlag("UseSSE", 3)));
            outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* sse4.*");
        }
        if (isCpuFeatureSupported("sse3")) {
            outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareNumericFlag("UseSSE", 2)));
            outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* sse3.*");
            outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* ssse3.*");
        }
        if (isCpuFeatureSupported("avx512f")) {
            outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareNumericFlag("UseAVX", 2)));
            outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* avx512.*");
        }
        if (isCpuFeatureSupported("avx2")) {
            outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareNumericFlag("UseAVX", 1)));
            outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* avx2.*");
        }
        if (isCpuFeatureSupported("avx")) {
            outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareNumericFlag("UseAVX", 0)));
            outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* avx.*");
        }
    }

    public void testAArch64Flags() throws Throwable {
        Map<String, String> vmFlagToCpuFeatureMap = Map.of("UseCRC32", "crc32",
                                                           "UseLSE", "lse",
                                                           "UseAES", "aes");
        vmFlagToCpuFeatureMap.forEach((vmFlag, cpuFeature) -> {
            try {
                OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareBooleanFlag(vmFlag, false)));
                outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* " + cpuFeature + ".*");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Disabling UseSHA should clear all shaXXX cpu features
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareBooleanFlag("UseSHA", false)));
        outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* sha1.*");
        outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* sha256.*");
        outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* sha3.*");
        outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* sha512.*");

        if (isCpuFeatureSupported("sve2")) {
            outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareNumericFlag("UseSVE", 1)));
            outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* sve2.*");
        }
        if (isCpuFeatureSupported("sve")) {
            outputAnalyzer = ProcessTools.executeTestJava(generateArgs(prepareNumericFlag("UseSVE", 0)));
            outputAnalyzer.shouldNotMatch("[os,cpu] CPU: .* sve.*");
        }
    }

    static boolean isCpuFeatureSupported(String feature) {
        return cpuFeaturesList.contains(feature);
    }

    public static void main(String args[]) throws Throwable {
        cpuFeaturesList = CPUInfo.getFeatures();
        new CPUFeaturesClearTest().runTestCases();
    }
}
