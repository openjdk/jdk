/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8066670
 * @summary Testing -XX:+PrintSharedArchiveAndExit option
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class PrintSharedArchiveAndExit {
  public static void main(String[] args) throws Exception {
    String filename = "./PrintSharedArchiveAndExit.jsa";

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=" + filename, "-Xshare:dump");
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    try {
      output.shouldContain("Loading classes to share");
      output.shouldHaveExitValue(0);

      // (1) With a valid archive
      pb = ProcessTools.createJavaProcessBuilder(
          "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=" + filename,
          "-XX:+PrintSharedArchiveAndExit", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldContain("archive is valid");
      output.shouldNotContain("java version");     // Should not print JVM version
      output.shouldHaveExitValue(0);               // Should report success in error code.

      pb = ProcessTools.createJavaProcessBuilder(
          "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=" + filename,
          "-XX:+PrintSharedArchiveAndExit");
      output = new OutputAnalyzer(pb.start());
      output.shouldContain("archive is valid");
      output.shouldNotContain("Usage:");           // Should not print JVM help message
      output.shouldHaveExitValue(0);               // Should report success in error code.

    } catch (RuntimeException e) {
      e.printStackTrace();
      output.shouldContain("Unable to use shared archive");
      output.shouldHaveExitValue(1);
    }
  }
}
