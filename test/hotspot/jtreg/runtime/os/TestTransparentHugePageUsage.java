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
 * @library /test/lib
 * @requires vm.gc.Serial & os.family == "linux" & os.maxMemory > 2G
 * @summary Check that a pretouched java heap appears to use THPs by checking
 *          AnonHugePages in smaps
 * @comment Use SerialGC to increase the time window for pretouching
 * @run driver runtime.os.TestTransparentHugePageUsage
 */

package runtime.os;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.process.ProcessTools;

public class TestTransparentHugePageUsage {
  private static final String[] fixedCmdLine = {
    "-XX:+UseTransparentHugePages", "-XX:+AlwaysPreTouch",
    "-Xlog:startuptime,pagesize,gc+heap=debug",
    "-XX:+UseSerialGC", "-Xms1G", "-Xmx1G",
  };

  public static void main(String[] args) throws Exception {
    ArrayList<String> cmdLine = new ArrayList<>(Arrays.asList(fixedCmdLine));
    cmdLine.add("runtime.os.TestTransparentHugePageUsage$CatSmaps");
    ProcessBuilder builder = ProcessTools.createTestJavaProcessBuilder(cmdLine);
    checkUsage(new BufferedReader(new InputStreamReader(builder.start().getInputStream())));
  }

  private static void checkUsage(BufferedReader reader) throws Exception {
    final Pattern useThp = Pattern.compile(".*\\[info\\]\\[pagesize\\].+UseTransparentHugePages=1.*");
    // Ensure THP is not disabled by OS.
    if (reader.lines().filter(line -> useThp.matcher(line).matches()).findFirst().isPresent()) {
      final Pattern heapAddr = Pattern.compile(".*\\sHeap:\\s.+base=0x0*(\\p{XDigit}+).*");
      final Optional<Long> addr = reader.lines()
          .map(line -> new SimpleEntry<String, Matcher>(line, heapAddr.matcher(line)))
          .filter(e -> e.getValue().matches())
          .findFirst()
          .map(e -> Long.valueOf(e.getKey().substring(e.getValue().start(1), e.getValue().end(1)), 16));
      if (!addr.isPresent()) throw new RuntimeException("Heap base was not found in smaps.");
      // Match the start of a mapping, for example:
      // 200000000-800000000 rw-p 00000000 00:00 0
      final Pattern mapping = Pattern.compile("^(\\p{XDigit}+)-\\p{XDigit}+.*");
      reader.lines()
            .filter(line -> {
                  Matcher matcher = mapping.matcher(line);
                  if (matcher.matches()) {
                    Long mappingAddr = Long.valueOf(line.substring(matcher.start(1), matcher.end(1)), 16);
                    if (mappingAddr.equals(addr.get())) return true;
                  }
                  return false;
                })
            .findFirst();
      final Pattern thpUsage = Pattern.compile("^AnonHugePages:\\s+(\\d+)\\skB");
      final Optional<Long> usage = reader.lines()
          .map(line -> new SimpleEntry<String, Matcher>(line, thpUsage.matcher(line)))
          .filter(e -> e.getValue().matches())
          .findFirst()
          .map(e -> Long.valueOf(e.getKey().substring(e.getValue().start(1), e.getValue().end(1))));
      if (!usage.isPresent()) throw new RuntimeException("The usage of THP was not found.");
      // Even with MADV_POPULATE_WRITE, the usage of THP is still one page less than the whole heap.
      if (usage.get() < 524288) throw new RuntimeException("The usage of THP is not enough.");
    }
  }

  public static class CatSmaps {
    public static void main(String[] args) throws Exception {
      new BufferedReader(new FileReader("/proc/self/smaps"))
          .lines()
          .forEach(line -> System.out.println(line));
    }
  }
}
