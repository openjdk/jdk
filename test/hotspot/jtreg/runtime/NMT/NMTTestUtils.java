/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 Red Hat, Inc. All rights reserved.
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

import jdk.test.lib.StringArrayUtils;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

public class NMTTestUtils {

    public static OutputAnalyzer startJcmdVMNativeMemory(String... additional_args) throws Exception {
        if (additional_args == null) {
            additional_args = new String[] {};
        }
        String fullargs[] = StringArrayUtils.concat("VM.native_memory", additional_args);
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(new PidJcmdExecutor().getCommandLine(fullargs));
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        return output;
    }

    public static OutputAnalyzer startJcmdVMNativeMemoryDetail(String... additional_args) throws Exception {
        return startJcmdVMNativeMemory("detail");
    }

    public static void runJcmdSummaryReportAndCheckOutput(String[] additional_args, String[] pattern, boolean verbose) throws Exception {
        OutputAnalyzer output = startJcmdVMNativeMemory(additional_args);
        output.stdoutShouldContainMultiLinePattern(pattern, true);
    }

    public static void runJcmdSummaryReportAndCheckOutput(String[] additional_args, String[] pattern) throws Exception {
        runJcmdSummaryReportAndCheckOutput(additional_args, pattern, true);
    }

    public static void runJcmdSummaryReportAndCheckOutput(String... pattern) throws Exception {
        runJcmdSummaryReportAndCheckOutput(null, pattern, true);
    }

    public static void checkReservedCommittedSummary(OutputAnalyzer output, long reservedKB, long committedKB, long peakKB) {
        String peakString = (committedKB == peakKB) ? "at peak" : "peak=" + peakKB + "KB";
        output.stdoutShouldContainMultiLinePattern(
                "Test (reserved=" + reservedKB + "KB, committed=" + committedKB + "KB)",
                "(mmap: reserved=" + reservedKB + "KB, committed=" + committedKB + "KB, " + peakString + ")"
        );
    }
}
