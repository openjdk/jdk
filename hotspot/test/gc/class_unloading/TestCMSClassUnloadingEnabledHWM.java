/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @key gc
 * @bug 8049831
 * @library /testlibrary /testlibrary/whitebox
 * @build TestCMSClassUnloadingEnabledHWM AllocateBeyondMetaspaceSize
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver TestCMSClassUnloadingEnabledHWM
 * @summary Test that -XX:-CMSClassUnloadingEnabled will trigger a Full GC when more than MetaspaceSize metadata is allocated.
 */

import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.ProcessTools;

import java.util.ArrayList;
import java.util.Arrays;

public class TestCMSClassUnloadingEnabledHWM {
  private static long MetaspaceSize = 32 * 1024 * 1024;
  private static long YoungGenSize  = 32 * 1024 * 1024;

  private static OutputAnalyzer run(boolean enableUnloading) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
      "-Xbootclasspath/a:.",
      "-XX:+WhiteBoxAPI",
      "-XX:MetaspaceSize=" + MetaspaceSize,
      "-Xmn" + YoungGenSize,
      "-XX:+UseConcMarkSweepGC",
      "-XX:" + (enableUnloading ? "+" : "-") + "CMSClassUnloadingEnabled",
      "-XX:+PrintHeapAtGC",
      "-XX:+PrintGCDetails",
      "AllocateBeyondMetaspaceSize",
      "" + MetaspaceSize,
      "" + YoungGenSize);
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

    out.shouldMatch(".*Full GC.*");
    out.shouldNotMatch(".*CMS Initial Mark.*");
  }

  public static void testWithCMSClassUnloading() throws Exception {
    // -XX:+CMSClassUnloadingEnabled is used, so we expect a concurrent cycle instead of a full GC.
    OutputAnalyzer out = runWithCMSClassUnloading();

    out.shouldMatch(".*CMS Initial Mark.*");
    out.shouldNotMatch(".*Full GC.*");
  }

  public static void main(String args[]) throws Exception {
    testWithCMSClassUnloading();
    testWithoutCMSClassUnloading();
  }
}

