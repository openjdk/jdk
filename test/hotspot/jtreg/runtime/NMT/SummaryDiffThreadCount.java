/*
 * Copyright (c) 2023, Azul Systems, Inc. All rights reserved.
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
 * @summary run NMT baseline, create threads and verify output from summary.diff
 * @author Evgeny Ignatenko
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=summary SummaryDiffThreadCount
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;

public class SummaryDiffThreadCount {
    public static void main(String args[]) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        OutputAnalyzer output;
        // Grab my own PID.
        String pid = Long.toString(ProcessTools.getProcessId());

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "baseline=true"});
        pb.start().waitFor();

        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Baseline taken");

        // Creating 10 threads.
        for (int i = 0; i < 10; i++) {
            new Thread(()-> {
                while (true) { continue; }
            }).start();
        }

        // Running "jcmd <pid> VM.native_memory summary.diff" and checking for five new threads reported.
        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "summary.diff"});
        output = new OutputAnalyzer(pb.start());

        // Trailing '+' is needed to check that NMT now reports that now we have more threads than it
        // was during the baseline.
        output.shouldMatch("threads #\\d+ \\+");
    }
}
