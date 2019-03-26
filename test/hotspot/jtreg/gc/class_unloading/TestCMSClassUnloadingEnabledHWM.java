/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.class_unloading;

/*
 * @test
 * @key gc
 * @bug 8049831
 * @requires !vm.graal.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run driver gc.class_unloading.TestCMSClassUnloadingEnabledHWM
 * @summary Test that -XX:-CMSClassUnloadingEnabled will trigger a Full GC when more than MetaspaceSize metadata is allocated.
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import sun.hotspot.WhiteBox;

public class TestCMSClassUnloadingEnabledHWM {
  private static long MetaspaceSize = 32 * 1024 * 1024;
  private static long YoungGenSize  = 32 * 1024 * 1024;

  private static OutputAnalyzer run(boolean enableUnloading) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
      "-Xbootclasspath/a:.",
      "-XX:+UnlockDiagnosticVMOptions",
      "-XX:+WhiteBoxAPI",
      "-Xmx128m",
      "-XX:CMSMaxAbortablePrecleanTime=1",
      "-XX:CMSWaitDuration=50",
      "-XX:MetaspaceSize=" + MetaspaceSize,
      "-Xmn" + YoungGenSize,
      "-XX:+UseConcMarkSweepGC",
      "-XX:" + (enableUnloading ? "+" : "-") + "CMSClassUnloadingEnabled",
      "-Xlog:gc",
      TestCMSClassUnloadingEnabledHWM.AllocateBeyondMetaspaceSize.class.getName(),
      "" + MetaspaceSize);
    return new OutputAnalyzer(pb.start());
  }

  public static OutputAnalyzer runWithCMSClassUnloading() throws Exception {
    return run(true);
  }

  public static OutputAnalyzer runWithoutCMSClassUnloading() throws Exception {
    return run(false);
  }

  public static void testWithoutCMSClassUnloading() throws Exception {
    // -XX:-CMSClassUnloadingEnabled is used, so we expect a full GC instead of a concurrent cycle.
    OutputAnalyzer out = runWithoutCMSClassUnloading();

    out.shouldMatch(".*Pause Full.*");
    out.shouldNotMatch(".*Pause Initial Mark.*");
  }

  public static void testWithCMSClassUnloading() throws Exception {
    // -XX:+CMSClassUnloadingEnabled is used, so we expect a concurrent cycle instead of a full GC.
    OutputAnalyzer out = runWithCMSClassUnloading();

    out.shouldMatch(".*Pause Initial Mark.*");
    out.shouldNotMatch(".*Pause Full.*");
  }

  public static void main(String args[]) throws Exception {
    testWithCMSClassUnloading();
    testWithoutCMSClassUnloading();
  }

  public static class AllocateBeyondMetaspaceSize {
    public static void main(String [] args) throws Exception {
      if (args.length != 1) {
        throw new IllegalArgumentException("Usage: <MetaspaceSize>");
      }

      WhiteBox wb = WhiteBox.getWhiteBox();

      // Allocate past the MetaspaceSize limit.
      long metaspaceSize = Long.parseLong(args[0]);
      long allocationBeyondMetaspaceSize  = metaspaceSize * 2;
      long metaspace = wb.allocateMetaspace(null, allocationBeyondMetaspaceSize);

      // Wait for at least one GC to occur. The caller will parse the log files produced.
      GarbageCollectorMXBean cmsGCBean = getCMSGCBean();
      while (cmsGCBean.getCollectionCount() == 0) {
        Thread.sleep(100);
      }

      wb.freeMetaspace(null, metaspace, metaspace);
    }

    private static GarbageCollectorMXBean getCMSGCBean() {
      for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
        if (gcBean.getObjectName().toString().equals("java.lang:type=GarbageCollector,name=ConcurrentMarkSweep")) {
          return gcBean;
        }
      }
      return null;
    }
  }
}

