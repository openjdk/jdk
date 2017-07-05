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
 * @test SystemGCOnForegroundCollector
 * @summary CMS: Call reset_after_compaction() only if a compaction has been done
 * @bug 8013184
 * @key gc
 * @key regression
 * @library /testlibrary
 * @run main/othervm SystemGCOnForegroundCollector
 * @author jon.masamitsu@oracle.com
 */

import com.oracle.java.testlibrary.*;

public class SystemGCOnForegroundCollector {
  public static void main(String args[]) throws Exception {

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
      "-showversion",
      "-XX:+UseConcMarkSweepGC",
      "-XX:MaxTenuringThreshold=1",
      "-XX:-UseCMSCompactAtFullCollection",
      ThreePlusMSSystemGC.class.getName()
      );

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    output.shouldNotContain("error");

    output.shouldHaveExitValue(0);
  }

  static class ThreePlusMSSystemGC {
    public static void main(String [] args) {
      // From running this test 3 System.gc() were always
      // enough to see the failure but the cause of the failure
      // depends on how objects are allocated in the CMS generation
      // which is non-deterministic.  Use 30 iterations for a more
      // reliable test.
      for (int i = 0; i < 30; i++) {
        System.gc();
      }
    }
  }
}
