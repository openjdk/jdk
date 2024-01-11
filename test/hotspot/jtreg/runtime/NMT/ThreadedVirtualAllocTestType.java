/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail ThreadedVirtualAllocTestType
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

public class ThreadedVirtualAllocTestType {
  public static long addr;
  public static final WhiteBox wb = WhiteBox.getWhiteBox();
  public static final long commitSize = 128 * 1024;
  public static final long reserveSize = 512 * 1024;

  public static void main(String args[]) throws Exception {
    OutputAnalyzer output;


    Thread reserveThread = new Thread() {
      public void run() {
        addr = wb.NMTReserveMemory(reserveSize);
      }
    };
    reserveThread.start();
    reserveThread.join();

    output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
    checkReservedCommittedSummary(output,512, 0);
    output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*" + Long.toHexString(addr + reserveSize) + "\\] reserved 512KB for Test");

    Thread commitThread = new Thread() {
      public void run() {
        wb.NMTCommitMemory(addr, commitSize);
      }
    };
    commitThread.start();
    commitThread.join();

    output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
    checkReservedCommittedSummary(output,512, 128);
    output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*" + Long.toHexString(addr + commitSize) + "\\] committed 128KB");

    Thread uncommitThread = new Thread() {
      public void run() {
        wb.NMTUncommitMemory(addr, commitSize);
      }
    };
    uncommitThread.start();
    uncommitThread.join();

    output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
    checkReservedCommittedSummary(output,512, 0);
    output.shouldContain("Test (reserved=512KB, committed=0KB)");
    output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*" + Long.toHexString(addr + commitSize) + "\\] committed");

    Thread releaseThread = new Thread() {
      public void run() {
        wb.NMTReleaseMemory(addr, reserveSize);
      }
    };
    releaseThread.start();
    releaseThread.join();

    output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
    checkReservedCommittedSummary(output,0, 0);
    output.shouldNotContain("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*" + Long.toHexString(addr + reserveSize) + "\\] reserved");
  }

  static long peakKB = 0;

  public static void checkReservedCommittedSummary(OutputAnalyzer output, long reservedKB, long committedKB) {
    if (committedKB > peakKB) {
      peakKB = committedKB;
    }
    NMTTestUtils.checkReservedCommittedSummary(output, reservedKB, committedKB, peakKB);
  }

}
