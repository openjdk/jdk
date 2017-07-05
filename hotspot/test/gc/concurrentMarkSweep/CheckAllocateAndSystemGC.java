/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test CheckAllocateAndSystemGC
 * @summary CMS: assert(used() == used_after_gc && used_after_gc <= capacity()) failed: used: 0 used_after_gc: 292080 capacity: 1431699456
 * @bug 8013032
 * @key gc
 * @key regression
 * @library /testlibrary
 * @run main/othervm CheckAllocateAndSystemGC
 * @author jon.masamitsu@oracle.com
 */

import com.oracle.java.testlibrary.*;

public class CheckAllocateAndSystemGC {
  public static void main(String args[]) throws Exception {

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
      "-showversion",
      "-XX:+UseConcMarkSweepGC",
      "-Xmn4m",
      "-XX:MaxTenuringThreshold=1",
      "-XX:-UseCMSCompactAtFullCollection",
      "CheckAllocateAndSystemGC$AllocateAndSystemGC"
      );

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    output.shouldNotContain("error");

    output.shouldHaveExitValue(0);
  }
  static class AllocateAndSystemGC {
    public static void main(String [] args) {
      Integer x[] = new Integer [1000];
      // Allocate enough objects to cause a minor collection.
      // These allocations suffice for a 4m young geneneration.
      for (int i = 0; i < 100; i++) {
        Integer y[] = new Integer[10000];
      }
      System.gc();
    }
  }
}
