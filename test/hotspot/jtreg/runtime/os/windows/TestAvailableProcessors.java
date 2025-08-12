/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6942632
 * @requires os.family == "windows"
 * @summary This test verifies that OpenJDK can use all available
 *          processors on Windows 11/Windows Server 2022 and later.
 * @requires vm.flagless
 * @library /test/lib
 * @compile GetAvailableProcessors.java
 * @run testng/othervm/native TestAvailableProcessors
 */

import java.io.IOException;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAvailableProcessors {

    private static final String totalProcessorCountMessage = "Active processor count across all processor groups: ";
    private static final String processorCountPerGroupMessage = "Active processors per group: ";
    private static final String isWindowsServerMessage = "IsWindowsServer: ";

    private static final String runtimeAvailableProcessorsMessage = "Runtime.availableProcessors: ";
    private static final String osVersionMessage = "OS Version: ";
    private static final String unsupportedPlatformMessage = "The UseAllWindowsProcessorGroups flag is not supported on this Windows version and will be ignored.";

    private static String getWindowsVersion() throws IOException {
        String systeminfoPath = "systeminfo.exe";

        var processBuilder = new ProcessBuilder(systeminfoPath);
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(processBuilder.start());
        outputAnalyzer.shouldHaveExitValue(0);
        outputAnalyzer.shouldContain(osVersionMessage);
        List<String> lines = outputAnalyzer.stdoutAsLines();

        String osVersion = null;
        for (var line: lines) {
            if (line.startsWith(osVersionMessage)) {
                osVersion = line.substring(osVersionMessage.length()).trim();
                break;
            }
        }

        System.out.println("Found OS version: " + osVersion);
        return osVersion;
    }

    private static boolean getSchedulesAllProcessorGroups(boolean isWindowsServer) throws IOException {
        String windowsVer = getWindowsVersion();
        String[] parts = windowsVer.split(" ");
        String[] versionParts = parts[0].split("\\.");

        if (versionParts.length != 3) {
            throw new RuntimeException("Unexpected Windows version format.");
        }

        int major = Integer.parseInt(versionParts[0]);
        int minor = Integer.parseInt(versionParts[1]);
        int build = Integer.parseInt(versionParts[2]);

        if (major > 10) {
            return true;
        }

        if (major < 10) {
            return false;
        }

        if (minor > 0) {
            return true;
        }

        if (isWindowsServer) {
            return build >= 20348;
        } else {
            return build >= 22000;
        }
    }

    private static OutputAnalyzer getAvailableProcessorsOutput(boolean productFlagEnabled) throws IOException {
        String productFlag = productFlagEnabled ? "-XX:+UseAllWindowsProcessorGroups" : "-XX:-UseAllWindowsProcessorGroups";

        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
            new String[] {productFlag, "GetAvailableProcessors"}
        );

        var output = new OutputAnalyzer(processBuilder.start());
        output.shouldHaveExitValue(0);
        output.shouldContain(runtimeAvailableProcessorsMessage);

        return output;
    }

    private static int getAvailableProcessors(OutputAnalyzer outputAnalyzer) {
        int runtimeAvailableProcs = 0;
        List<String> output = outputAnalyzer.stdoutAsLines();

        for (var line: output) {
            if (line.startsWith(runtimeAvailableProcessorsMessage)) {
                String runtimeAvailableProcsStr = line.substring(runtimeAvailableProcessorsMessage.length());
                runtimeAvailableProcs = Integer.parseInt(runtimeAvailableProcsStr);
            }
        }

        return runtimeAvailableProcs;
    }

    private static int getAvailableProcessors(boolean productFlagEnabled) throws IOException {
        OutputAnalyzer outputAnalyzer = getAvailableProcessorsOutput(productFlagEnabled);
        return getAvailableProcessors(outputAnalyzer);
    }

    private static void verifyAvailableProcessorsWithDisabledProductFlag(Set<Integer> processorGroupSizes) throws IOException {
        boolean productFlagEnabled = false;
        int runtimeAvailableProcs = getAvailableProcessors(productFlagEnabled);

        String error = String.format("Runtime.availableProcessors (%d) is not a valid processor group size on this machine.", runtimeAvailableProcs);
        Assert.assertTrue(processorGroupSizes.contains(runtimeAvailableProcs), error);
    }

    private static void verifyAvailableProcessorsWithEnabledProductFlag(boolean schedulesAllProcessorGroups, int totalProcessorCount, Set<Integer> processorGroupSizes) throws IOException {
        boolean productFlagEnabled = true;

        OutputAnalyzer outputAnalyzer = getAvailableProcessorsOutput(productFlagEnabled);
        int runtimeAvailableProcs = getAvailableProcessors(outputAnalyzer);

        if (schedulesAllProcessorGroups) {
            String error = String.format("Runtime.availableProcessors (%d) is not equal to the expected total processor count (%d)", runtimeAvailableProcs, totalProcessorCount);
            Assert.assertEquals(runtimeAvailableProcs, totalProcessorCount, error);
        } else {
            outputAnalyzer.shouldContain(unsupportedPlatformMessage);

            String error = String.format("Runtime.availableProcessors (%d) is not a valid processor group size on this machine.", runtimeAvailableProcs);
            Assert.assertTrue(processorGroupSizes.contains(runtimeAvailableProcs), error);
        }
    }

    @Test
    private static void testProcessorAvailability() throws IOException {
        // Launch "<nativepath>/GetProcessorInfo.exe" to gather processor counts
        var processBuilder = new ProcessBuilder("GetProcessorInfo.exe");
        var outputAnalyzer= new OutputAnalyzer(processBuilder.start());
        outputAnalyzer.shouldHaveExitValue(0);
        outputAnalyzer.shouldContain(totalProcessorCountMessage);
        outputAnalyzer.shouldContain(processorCountPerGroupMessage);
        outputAnalyzer.shouldContain(isWindowsServerMessage);

        int totalProcessorCount = 0;
        boolean isWindowsServer = false;
        var processorGroupSizes = new HashSet<Integer>();

        List<String> lines = outputAnalyzer.stdoutAsLines();

        for (var line: lines) {
            if (line.startsWith(totalProcessorCountMessage)) {
                String totalProcessorCountStr = line.substring(totalProcessorCountMessage.length());
                totalProcessorCount = Integer.parseInt(totalProcessorCountStr);
            } else if (line.startsWith(processorCountPerGroupMessage)) {
                String processorCountPerGroupStr = line.substring(processorCountPerGroupMessage.length());
                String[] processorCountsPerGroup = processorCountPerGroupStr.split(",");

                for (var processorCountStr: processorCountsPerGroup) {
                    int processorCount = Integer.parseInt(processorCountStr);
                    processorGroupSizes.add(processorCount);
                }
            } else if (line.startsWith(isWindowsServerMessage)) {
                String isWindowsServerStr = line.substring(isWindowsServerMessage.length());
                isWindowsServer = Integer.parseInt(isWindowsServerStr) > 0;
            }
        }

        // Launch java without the start command and with the product flag disabled
        verifyAvailableProcessorsWithDisabledProductFlag(processorGroupSizes);

        // Launch java without the start command and with the product flag enabled
        boolean schedulesAllProcessorGroups = getSchedulesAllProcessorGroups(isWindowsServer);
        verifyAvailableProcessorsWithEnabledProductFlag(schedulesAllProcessorGroups, totalProcessorCount, processorGroupSizes);
    }
}
