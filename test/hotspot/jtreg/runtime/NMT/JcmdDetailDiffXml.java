/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary run NMT baseline, allocate memory and verify output from detail.diff
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail JcmdDetailDiffXml
 */

import java.io.File;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.JDKToolFinder;

import jdk.test.whitebox.WhiteBox;

public class JcmdDetailDiffXml {

    public static WhiteBox wb = WhiteBox.getWhiteBox();

    public static NMTXmlUtils runAndCreateXmlReport(String xmlFilename) throws Exception {
      File xmlFile = File.createTempFile(xmlFilename, ".xml");
      ProcessBuilder pb = new ProcessBuilder();
      String pid = Long.toString(ProcessTools.getProcessId());

      pb.redirectOutput(xmlFile)
        .command(new String[] { JDKToolFinder.getJDKTool("jcmd"),
                                pid,
                                "VM.native_memory",
                                "detail.diff",
                                "scale=KB",
                                "format=xml"})
        .start()
        .waitFor();
      return new NMTXmlUtils(xmlFile);
    }

    public static void main(String args[]) throws Exception {
        OutputAnalyzer output;
        NMTXmlUtils nmtXml;

        long commitSize = 128 * 1024;
        long reserveSize = 256 * 1024;
        long addr;

        // Run 'jcmd <pid> VM.native_memory baseline=true'
        output = NMTTestUtils.startJcmdVMNativeMemory("baseline=true");
        output.shouldContain("Baseline taken");

        addr = wb.NMTReserveMemory(reserveSize);
        nmtXml = runAndCreateXmlReport("nmt_detail_diff_1_");
        nmtXml.shouldBeReportType("Detail Diff")
              .shouldBeReservedCurrentOfTest("256")
              .shouldBeReservedDiffOfTest("+256")
              .shouldBeCommittedCurrentOfTest("0");

        wb.NMTCommitMemory(addr, commitSize);
        nmtXml = runAndCreateXmlReport("nmt_detail_diff_2_");
        nmtXml.shouldBeReportType("Detail Diff")
              .shouldBeReservedCurrentOfTest("256")
              .shouldBeReservedDiffOfTest("+256")
              .shouldBeCommittedCurrentOfTest("128")
              .shouldBeCommittedDiffOfTest("+128");

        wb.NMTUncommitMemory(addr, commitSize);
        nmtXml = runAndCreateXmlReport("nmt_detail_diff_3_");
        nmtXml.shouldBeReportType("Detail Diff")
              .shouldBeReservedCurrentOfTest("256")
              .shouldBeReservedDiffOfTest("+256")
              .shouldBeCommittedCurrentOfTest("0");

        wb.NMTReleaseMemory(addr, reserveSize);
        nmtXml = runAndCreateXmlReport("nmt_detail_diff_4_");
        nmtXml.shouldBeReportType("Detail Diff")
              .shouldNotExistTestTag();
    }
}
