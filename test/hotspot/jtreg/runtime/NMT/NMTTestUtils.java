/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class NMTTestUtils {

private long startTime = System.currentTimeMillis();

    public static OutputAnalyzer startJcmdVMNativeMemory(String... additional_args) throws Exception {
System.out("[" + (System.currentTimeMillis() - startTime) + "]: >> startJcmdVMNativeMemory");
        if (additional_args == null) {
            additional_args = new String[] {};
        }
        String fullargs[] = new String[3 + additional_args.length];
        fullargs[0] = JDKToolFinder.getJDKTool("jcmd");
        fullargs[1] = Long.toString(ProcessTools.getProcessId());
        fullargs[2] = "VM.native_memory";
        System.arraycopy(additional_args, 0, fullargs, 3, additional_args.length);
        ProcessBuilder pb = new ProcessBuilder();
System.out("[" + (System.currentTimeMillis() - startTime) + "]:   startJcmdVMNativeMemory, args = " + fullargs);
        pb.command(fullargs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
System.out("[" + (System.currentTimeMillis() - startTime) + "]: << startJcmdVMNativeMemory");
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
