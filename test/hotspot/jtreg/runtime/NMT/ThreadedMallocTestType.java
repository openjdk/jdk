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
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=summary ThreadedMallocTestType
 */

import jdk.test.whitebox.WhiteBox;

public class ThreadedMallocTestType {
  public static long memAlloc1;
  public static long memAlloc2;
  public static long memAlloc3;

  public static void main(String args[]) throws Exception {
    final WhiteBox wb = WhiteBox.getWhiteBox();

    Thread allocThread = new Thread() {
      public void run() {
        // Alloc memory using the WB api
        memAlloc1 = wb.NMTMalloc(128 * 1024);
        memAlloc2 = wb.NMTMalloc(256 * 1024);
        memAlloc3 = wb.NMTMalloc(512 * 1024);
      }
    };

    allocThread.start();
    allocThread.join();

    System.out.println("memAlloc1:"+memAlloc1);
    System.out.println("memAlloc2:"+memAlloc2);
    System.out.println("memAlloc3:"+memAlloc3);

    // Run 'jcmd <pid> VM.native_memory summary'
    NMTTestUtils.runJcmdSummaryReportAndCheckOutput(
            "Test (reserved=896KB, committed=896KB)",
            "(malloc=896KB #3) (at peak)"
    );

    Thread freeThread = new Thread() {
      public void run() {
        // Free the memory allocated by NMTMalloc
        wb.NMTFree(memAlloc1);
        wb.NMTFree(memAlloc2);
        wb.NMTFree(memAlloc3);
      }
    };

    freeThread.start();
    freeThread.join();

    NMTTestUtils.runJcmdSummaryReportAndCheckOutput(
            "Test (reserved=0KB, committed=0KB)",
            "(malloc=0KB) (peak=896KB #3)"
    );
  }
}
