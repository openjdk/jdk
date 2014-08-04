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
 * @build TestCMSClassUnloadingDisabledHWM
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver TestCMSClassUnloadingDisabledHWM
 * @summary Test that -XX:-CMSClassUnloadingEnabled will trigger a Full GC when more than MetaspaceSize metadata is allocated.
 */

import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.ProcessTools;
import sun.hotspot.WhiteBox;

import java.util.ArrayList;
import java.util.Arrays;

public class TestCMSClassUnloadingDisabledHWM {

  private static OutputAnalyzer run(long metaspaceSize, long youngGenSize) throws Exception {
      ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
        "-Xbootclasspath/a:.",
        "-XX:+WhiteBoxAPI",
        "-XX:MetaspaceSize=" + metaspaceSize,
        "-Xmn" + youngGenSize,
        "-XX:+UseConcMarkSweepGC",
        "-XX:-CMSClassUnloadingEnabled",
        "-XX:+PrintHeapAtGC",
        "-XX:+PrintGCDetails",
        "AllocateBeyondMetaspaceSize",
        "" + metaspaceSize,
        "" + youngGenSize);
    return new OutputAnalyzer(pb.start());
  }

  public static void main(String args[]) throws Exception {
    long metaspaceSize = 32 * 1024 * 1024;
    long youngGenSize = 32 * 1024 * 1024;

    OutputAnalyzer out = run(metaspaceSize, youngGenSize);

    // -XX:-CMSClassUnloadingEnabled is used, so we expect a full GC instead of a concurrent cycle.
    out.shouldMatch(".*Full GC.*");
    out.shouldNotMatch(".*CMS Initial Mark.*");
  }
}

class AllocateBeyondMetaspaceSize {
  public static Object dummy;

  public static void main(String [] args) {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: <MetaspaceSize> <YoungGenSize>");
    }

    long metaspaceSize = Long.parseLong(args[0]);
    long youngGenSize = Long.parseLong(args[1]);

    run(metaspaceSize, youngGenSize);
  }

  private static void run(long metaspaceSize, long youngGenSize) {
    WhiteBox wb = WhiteBox.getWhiteBox();

    long allocationBeyondMetaspaceSize  = metaspaceSize * 2;
    long metaspace = wb.allocateMetaspace(null, allocationBeyondMetaspaceSize);

    triggerYoungGC(youngGenSize);

    wb.freeMetaspace(null, metaspace, metaspace);
  }

  private static void triggerYoungGC(long youngGenSize) {
    long approxAllocSize = 32 * 1024;
    long numAllocations  = 2 * youngGenSize / approxAllocSize;

    for (long i = 0; i < numAllocations; i++) {
      dummy = new byte[(int)approxAllocSize];
    }
  }
}
