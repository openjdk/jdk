/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Google and/or its affiliates. All rights reserved.
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

package gc.logging;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import sun.hotspot.WhiteBox;

/*
 * @test TestMetaSpaceLog
 * @bug 8211123
 * @summary Ensure that the Metaspace is updated in the log
 * @requires vm.gc=="null"
 * @key gc
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @compile TestMetaSpaceLog.java
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main gc.logging.TestMetaSpaceLog
 */

public class TestMetaSpaceLog {
  private static Pattern metaSpaceRegexp;

  static {
    // Do this once here.
    metaSpaceRegexp = Pattern.compile(".*Metaspace: ([0-9]+).*->([0-9]+).*");
  }

  public static void main(String[] args) throws Exception {
    testMetaSpaceUpdate("UseParallelGC");
    testMetaSpaceUpdate("UseG1GC");
    testMetaSpaceUpdate("UseConcMarkSweepGC");
    testMetaSpaceUpdate("UseSerialGC");
  }

  private static void verifyContainsMetaSpaceUpdate(OutputAnalyzer output) {
    Predicate<String> collectedMetaSpace = line -> check(line);

    // At least one metaspace line from GC should show GC being collected.
    boolean foundCollectedMetaSpace = output.asLines().stream()
        .filter(s -> s.contains("[gc,metaspace"))
        .anyMatch(TestMetaSpaceLog::check);
    Asserts.assertTrue(foundCollectedMetaSpace);
  }

  private static boolean check(String line) {
    Matcher m = metaSpaceRegexp.matcher(line);
    Asserts.assertTrue(m.matches(), "Unexpected line for metaspace logging: " + line);
    long before = Long.parseLong(m.group(1));
    long after  = Long.parseLong(m.group(2));
    return before > after;
  }

  private static void testMetaSpaceUpdate(String gcFlag) throws Exception {
    // Propagate test.src for the jar file.
    String testSrc= "-Dtest.src=" + System.getProperty("test.src", ".");

    System.err.println("Testing with GC Flag: " + gcFlag);
    ProcessBuilder pb =
      ProcessTools.createJavaProcessBuilder(
          "-XX:+" + gcFlag,
          "-Xlog:gc*",
          "-Xbootclasspath/a:.",
          "-XX:+UnlockDiagnosticVMOptions",
          "-XX:+WhiteBoxAPI",
          "-Xmx1000M",
          "-Xms1000M",
          testSrc, StressMetaSpace.class.getName());

    OutputAnalyzer output = null;
    try {
      output = new OutputAnalyzer(pb.start());
      verifyContainsMetaSpaceUpdate(output);
    } catch (Exception e) {
      // For error diagnosis: print and throw.
      e.printStackTrace();
      output.reportDiagnosticSummary();
      throw e;
    }
  }

  static class StressMetaSpace {
    private static URL[] urls = new URL[1];

    static {
      try {
        File jarFile = new File(System.getProperty("test.src") + "/testcases.jar");
        urls[0] = jarFile.toURI().toURL();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public static void main(String args[]) {
      WhiteBox wb = WhiteBox.getWhiteBox();
      for(int i = 0; i < 10000; i++) {
        loadClass(wb);
      }
      wb.fullGC();
    }

    public static void loadClass(WhiteBox wb) {
      try {
        URLClassLoader ucl = new URLClassLoader(urls);
        Class.forName("case00", false, ucl);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
