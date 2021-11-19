/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
 * @summary NMT can safely shutdown
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail ShutdownTest
 */

import java.util.ArrayList;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;
import sun.hotspot.WhiteBox;

public class ShutdownTest {
  private static volatile boolean shutdown = false;
  private static WhiteBox wb = WhiteBox.getWhiteBox();
  private static ArrayList<Long> mallocList = new ArrayList<>();
  private static ArrayList<Long> vmList = new ArrayList<>();

  private static final long PAGE = 4 * 1024;
  private static void triggerNMTActivities() {
    Thread[] thrs = new Thread[10];
    while (!shutdown) {
      for (int index = 0; index < thrs.length; index++) {
        thrs[index] = new Thread(() -> {
          long toRemove = 0;
          long toAdd = 0;

          // trigger malloc tracking
          toAdd = wb.NMTMalloc(7);
          synchronized (mallocList) {
            if (mallocList.size() > 20) {
              toRemove = mallocList.remove(0);
            }
            mallocList.add(toAdd);
          }
          if (toRemove != 0) {
            wb.NMTFree(toRemove);
            toRemove = 0;
          }

          toAdd = wb.NMTReserveMemory(PAGE);
          synchronized (vmList) {
            if (vmList.size() > 10) {
              toRemove = vmList.remove(0);
            }
            vmList.add(toAdd);
          }
          if (toRemove != 0) {
            wb.NMTReleaseMemory(toRemove, PAGE);
          }
        });
        thrs[index].start();
      }

      for (int index = 0; index < thrs.length; index++) {
        try {
          thrs[index].join();
        } catch (InterruptedException e) { }
      }
    }
  }

  public static void main(String args[]) throws Exception {
    // Trigger NMT activities
    new Thread(()-> {
      triggerNMTActivities();
    }).start();

    // Grab my own PID
    String pid = Long.toString(ProcessTools.getProcessId());
    OutputAnalyzer output;

    ProcessBuilder pb = new ProcessBuilder();

    // Run 'jcmd <pid> VM.native_memory shutdown'
    pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "shutdown"});
    output = new OutputAnalyzer(pb.start());

    // Verify that jcmd reports that NMT is shutting down
    output.shouldContain("Native memory tracking has been turned off");

    shutdown = true;
  }
}
