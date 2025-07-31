/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Reserve/Commit/Uncommit/Release of virtual memory and that we track it correctly
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail VirtualAllocTestType
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

public class VirtualAllocTestType {

  public static WhiteBox wb = WhiteBox.getWhiteBox();
  public static void main(String args[]) throws Exception {
    OutputAnalyzer output;
    long commitSize = 128 * 1024;
    long reserveSize = 256 * 1024;
    long addr1, addr2;

    String info = "start";

    try {
      // ------
      // Reserve first mapping
      addr1 = wb.NMTReserveMemory(reserveSize);
      info = "reserve 1: addr1=" + addr1;

      output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
      checkReservedCommittedSummary(output, 256, 0);
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + reserveSize) + "\\] reserved 256KB for Test");

      // ------
      // Reserve second mapping
      addr2 = wb.NMTReserveMemory(reserveSize);
      info = "reserve 2: addr2=" + addr2;

      // For this test, we want to see two disjunct mappings.
      if ((addr2 == addr1 + reserveSize) || (addr2 == addr1 - reserveSize)) {
        //               <---r1---><---r2--->...<---tmp--->
        // <---tmp--->...<---r1---><---r2--->
        //
        // Reserve a new region and find whether r1 or r2 to be released.
        long tmp = wb.NMTReserveMemory(reserveSize);
        long r1 = addr1 < addr2 ? addr1 : addr2;
        long r2 = addr1 > addr2 ? addr1 : addr2;
        long tmp_end = tmp + reserveSize;
        long r1_end = r1 + reserveSize;
        long r2_end = r2 + reserveSize;
        if (tmp >= r2_end) {
          wb.NMTReleaseMemory(r2, reserveSize);
          addr1 = r1;
          addr2 = tmp;
        } else if (tmp_end <= r1) {
          wb.NMTReleaseMemory(r1, reserveSize);
          addr1 = tmp;
          addr2 = r2;
        }
      }

      output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
      checkReservedCommittedSummary(output, 512, 0);
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + reserveSize) + "\\] reserved 256KB for Test");

      // ------
      // Now commit the first mapping
      wb.NMTCommitMemory(addr1, commitSize);
      info = "commit 1";

      output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
      checkReservedCommittedSummary(output, 512, 128);
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + commitSize) + "\\] committed 128KB");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + reserveSize) + "\\] reserved 256KB for Test");

      // ------
      // Now commit the second mapping
      wb.NMTCommitMemory(addr2, commitSize);
      info = "commit 2";

      output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
      checkReservedCommittedSummary(output, 512, 256);
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + commitSize) + "\\] committed 128KB");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + commitSize) + "\\] committed 128KB");

      // ------
      // Now uncommit the second mapping
      wb.NMTUncommitMemory(addr2, commitSize);
      info = "uncommit 2";

      output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
      checkReservedCommittedSummary(output, 512, 128);
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + commitSize) + "\\] committed 128KB");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + commitSize) + "\\] committed 128KB");

      // ------
      // Now uncommit the first mapping
      wb.NMTUncommitMemory(addr1, commitSize);
      info = "uncommit 1";

      output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
      checkReservedCommittedSummary(output, 512, 0);
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + commitSize) + "\\] committed 128KB");
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + commitSize) + "\\] committed 128KB");

      // ----------
      // Release second mapping
      wb.NMTReleaseMemory(addr2, reserveSize);
      info = "release 2";

      output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
      checkReservedCommittedSummary(output, 256, 0);
      output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + commitSize) + "\\] committed 128KB");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + commitSize) + "\\] committed 128KB");

      // ----------
      // Release first mapping
      wb.NMTReleaseMemory(addr1, reserveSize);
      info = "release 1";

      output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
      checkReservedCommittedSummary(output, 0, 0);
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr1) + " - 0x[0]*" + Long.toHexString(addr1 + commitSize) + "\\] committed 128KB");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + reserveSize) + "\\] reserved 256KB for Test");
      output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr2) + " - 0x[0]*" + Long.toHexString(addr2 + commitSize) + "\\] committed 128KB");

    } catch (Exception e) {
      throw new RuntimeException(e.getMessage() + " (" + info + ")");
    }
  }

  static long peakKB = 0;

  public static void checkReservedCommittedSummary(OutputAnalyzer output, long reservedKB, long committedKB) {
    if (committedKB > peakKB) {
      peakKB = committedKB;
    }
    NMTTestUtils.checkReservedCommittedSummary(output, reservedKB, committedKB, peakKB);
  }
}
