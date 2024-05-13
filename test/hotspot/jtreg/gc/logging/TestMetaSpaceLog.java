/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jdk.test.lib.Asserts;
import jdk.test.lib.ByteCodeLoader;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

/*
 * @test TestMetaSpaceLog
 * @bug 8211123
 * @summary Ensure that the Metaspace is updated in the log
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @requires vm.gc != "Epsilon"
 * @requires vm.gc != "Z"
 * @requires os.maxMemory >= 2G
 *
 * @compile TestMetaSpaceLog.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.logging.TestMetaSpaceLog
 */

public class TestMetaSpaceLog {
  private static Pattern metaSpaceRegexp;

  static {
    // Do this once here.
    // Scan for Metaspace update notices as part of the GC log, e.g. in this form:
    // [gc,metaspace   ] GC(0) Metaspace: 11895K(14208K)->11895K(14208K) NonClass: 10552K(12544K)->10552K(12544K) Class: 1343K(1664K)->1343K(1664K)
    // This regex has to be up-to-date with the format used in hotspot to print metaspace change.
    final String NUM_K = "\\d+K";
    final String GP_NUM_K = "(\\d+)K";
    final String BR_NUM_K = "\\(" + NUM_K + "\\)";
    final String SIZE_CHG = NUM_K + BR_NUM_K + "->" + NUM_K + BR_NUM_K;
    metaSpaceRegexp = Pattern.compile(".* Metaspace: " + GP_NUM_K + BR_NUM_K + "->" + GP_NUM_K + BR_NUM_K
                                      + "( NonClass: " + SIZE_CHG + " Class: " + SIZE_CHG + ")?$");
  }

  public static void main(String[] args) throws Exception {
    testMetaSpaceUpdate();
  }

  private static void verifyContainsMetaSpaceUpdate(OutputAnalyzer output) {
    // At least one metaspace line from GC should show GC being collected.
    boolean foundCollectedMetaSpace = output.asLines().stream()
        .filter(s -> s.contains("[gc,metaspace"))
        .anyMatch(TestMetaSpaceLog::check);
    Asserts.assertTrue(foundCollectedMetaSpace);
  }

  private static boolean check(String line) {
    Matcher m = metaSpaceRegexp.matcher(line);
    if (m.matches()) {
      // Numbers for Metaspace occupation should grow.
      long before = Long.parseLong(m.group(1));
      long after = Long.parseLong(m.group(2));
      return before > after;
    }
    return false;
  }

  private static void testMetaSpaceUpdate() throws Exception {
    OutputAnalyzer output = null;
    try {
      output = ProcessTools.executeTestJava(
            "-Xlog:gc*",
            "-Xbootclasspath/a:.",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-Xmx1000M",
            "-Xms1000M",
            StressMetaSpace.class.getName());

      verifyContainsMetaSpaceUpdate(output);
    } catch (Exception e) {
      // For error diagnosis: print and throw.
      e.printStackTrace();
      if (output != null) {
        output.reportDiagnosticSummary();
      }
      throw e;
    }
  }

  static class StressMetaSpace {

    public static void main(String args[]) {
      loadManyClasses();
      WhiteBox.getWhiteBox().fullGC();
    }

    public static void loadManyClasses() {
      String className = "Tmp";
      String sourceCode = "public class Tmp {}";
      byte[] byteCode = InMemoryJavaCompiler.compile(className, sourceCode);
      try {
        for (int i = 0; i < 10000; i++) {
          ByteCodeLoader.load(className, byteCode);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
