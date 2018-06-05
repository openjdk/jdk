/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that touching noaccess area in class ReservedHeapSpace results in SIGSEGV/ACCESS_VIOLATION
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main ReadFromNoaccessArea
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import sun.hotspot.WhiteBox;

public class ReadFromNoaccessArea {

  public static void main(String args[]) throws Exception {
    if (!Platform.is64bit()) {
      System.out.println("ReadFromNoaccessArea tests is useful only on 64bit architecture. Passing silently.");
      return;
    }

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
          "-Xbootclasspath/a:.",
          "-XX:+UnlockDiagnosticVMOptions",
          "-XX:+WhiteBoxAPI",
          "-XX:+UseCompressedOops",
          "-XX:HeapBaseMinAddress=33G",
          "-XX:-CreateCoredumpOnCrash",
          "-Xmx128m",
          DummyClassWithMainTryingToReadFromNoaccessArea.class.getName());

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    System.out.println("******* Printing stdout for analysis in case of failure *******");
    System.out.println(output.getStdout());
    System.out.println("******* Printing stderr for analysis in case of failure *******");
    System.out.println(output.getStderr());
    System.out.println("***************************************************************");
    if (output.getStdout() != null && output.getStdout().contains("WB_ReadFromNoaccessArea method is useless")) {
      // Test conditions broken. There is no protected page in ReservedHeapSpace in these circumstances. Silently passing test.
      return;
    }
    if (Platform.isWindows()) {
      output.shouldContain("EXCEPTION_ACCESS_VIOLATION");
    } else if (Platform.isOSX()) {
      output.shouldContain("SIGBUS");
    } else {
      output.shouldContain("SIGSEGV");
    }
  }

  public static class DummyClassWithMainTryingToReadFromNoaccessArea {

    // This method calls whitebox method reading from noaccess area
    public static void main(String args[]) throws Exception {
      WhiteBox.getWhiteBox().readFromNoaccessArea();
      throw new Exception("Call of readFromNoaccessArea succeeded! This is wrong. Crash expected. Test failed.");
    }
  }

}
