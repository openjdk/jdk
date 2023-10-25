/*
 * Copyright (c) Ampere Computing and/or its affiliates. All rights reserved.
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
 * @test TestTransparentHugePageUsage
 * @bug 8315923
 * @requires vm.gc.Parallel & os.family == "linux" & os.maxMemory > 2G
 * @summary Check if the usage of THP is zero when enabled.
 * @comment The test is not ParallelGC-specific, but a multi-threaded GC is
 *          required. So ParallelGC is used here.
 *
 * @run main/othervm -XX:+UseTransparentHugePages
 *                   -XX:+UseParallelGC -XX:ParallelGCThreads=${os.processors}
 *                   -Xlog:startuptime,pagesize,gc+heap=debug
 *                   -Xms1G -Xmx1G -XX:+AlwaysPreTouch
 *                   runtime.os.TestTransparentHugePageUsage
 */

package runtime.os;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestTransparentHugePageUsage {
  private static boolean foundHeapFrom(BufferedReader reader) throws Exception {
    String line = null;
    // Read the size. It is given right after the start of the mapping.
    Pattern size = Pattern.compile("^Size:\\s+(\\d+)\\skB");
    if ((line = reader.readLine()) != null) {
      Matcher matcher = size.matcher(line);
      // Found the heap based on its size.
      if (matcher.matches() &&
          Integer.valueOf(line.substring(matcher.start(1), matcher.end(1))) >= 1 * 1024 * 1024) {
        Pattern thpUsage = Pattern.compile("^AnonHugePages:\\s+(\\d+)\\skB");
        while ((line = reader.readLine()) != null) {
          matcher = thpUsage.matcher(line);
          if (matcher.matches()) {
            if (Integer.valueOf(line.substring(matcher.start(1), matcher.end(1))) == 0) {
              // Trigger failure when the usage is 0. This does not cover
              // all cases considered to be failures, but we can just say
              // the non-usage of THP failes for sure.
              throw new RuntimeException("The usage of THP should not be zero.");
            }
            break;
          }
        }
        return true;
      }
    }
    return false;
  }

  public static void main(String[] args) throws Exception {
    HotSpotDiagnosticMXBean mxBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
    // Ensure THP is not disabled by OS.
    if (mxBean.getVMOption("UseTransparentHugePages").getValue() == "true") {
      BufferedReader reader = new BufferedReader(new FileReader("/proc/self/smaps"));
      // Match the start of a mapping, for example:
      // 200000000-800000000 rw-p 00000000 00:00 0
      Pattern mapping = Pattern.compile("^\\p{XDigit}+-\\p{XDigit}+.*");
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (mapping.matcher(line).matches()) {
          if (foundHeapFrom(reader)) break;
        }
      }
    }
  }
}
