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

/*
 * @test HeapChangeLogging.java
 * @bug 8027440
 * @library /testlibrary
 * @build HeapChangeLogging
 * @summary Allocate to get a promotion failure and verify that that heap change logging is present.
 * @run main HeapChangeLogging
 *
 * Test the output of G1SummarizeRSetStats in conjunction with G1SummarizeRSetStatsPeriod.
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.java.testlibrary.*;

public class HeapChangeLogging {
  public static void main(String[] args) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xmx128m", "-Xmn100m", "-XX:+UseSerialGC", "-XX:+PrintGC", "HeapFiller");
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    String stdout = output.getStdout();
    System.out.println(stdout);
    Matcher stdoutMatcher = Pattern.compile("\\[GC .Allocation Failure.*K->.*K\\(.*K\\), .* secs\\]", Pattern.MULTILINE).matcher(stdout);
    if (!stdoutMatcher.find()) {
      throw new RuntimeException("No proper GC log line found");
    }
    output.shouldHaveExitValue(0);
  }
}

class HeapFiller {
  public static Entry root;
  private static final int PAYLOAD_SIZE = 1000;

  public static void main(String[] args) {
    root = new Entry(PAYLOAD_SIZE, null);
    Entry current = root;
    try {
      while (true) {
        Entry newEntry = new Entry(PAYLOAD_SIZE, current);
        current = newEntry;
      }
    } catch (OutOfMemoryError e) {
      root = null;
    }

  }
}

class Entry {
  public Entry previous;
  public byte[] payload;

  Entry(int payloadSize, Entry previous) {
    payload = new byte[payloadSize];
    this.previous = previous;
  }
}