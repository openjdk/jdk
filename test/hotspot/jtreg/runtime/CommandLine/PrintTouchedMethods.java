/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025692 8273333
 * @requires vm.flavor != "zero"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @library /test/lib
 * @run driver PrintTouchedMethods
 */

import java.io.File;
import java.util.List;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;

public class PrintTouchedMethods {

    public static void main(String args[]) throws Exception {
      ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
          "-XX:-UnlockDiagnosticVMOptions",
          "-XX:+LogTouchedMethods",
          "-XX:+PrintTouchedMethodsAtExit",
          TestLogTouchedMethods.class.getName());

      // UnlockDiagnostic turned off, should fail
      OutputAnalyzer output = new OutputAnalyzer(pb.start());
      output.shouldNotHaveExitValue(0);
      output.shouldContain("Error: VM option 'LogTouchedMethods' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.");
      output.shouldContain("Error: Could not create the Java Virtual Machine.");

      pb = ProcessTools.createJavaProcessBuilder(
          "-XX:+UnlockDiagnosticVMOptions",
          "-XX:+LogTouchedMethods",
          "-XX:+PrintTouchedMethodsAtExit",
          TestLogTouchedMethods.class.getName());
      output = new OutputAnalyzer(pb.start());
      // check order:
      // 1 "# Method::print_touched_methods version 1" is the first in first line
      // 2 should contain TestLogMethods.methodA:()V
      // 3 should not contain TestLogMethods.methodB:()V
      // Repeat above for another run with -Xint
      List<String> lines = output.asLines();

      if (lines.size() < 1) {
        throw new Exception("Empty output");
      }

      String first = lines.get(0);
      if (!first.equals("# Method::print_touched_methods version 1")) {
        throw new Exception("First line mismatch");
      }

      output.shouldContain("TestLogTouchedMethods.methodA:()V");
      output.shouldNotContain("TestLogTouchedMethods.methodB:()V");
      output.shouldHaveExitValue(0);

      pb = ProcessTools.createJavaProcessBuilder(
          "-XX:+UnlockDiagnosticVMOptions",
          "-Xint",
          "-XX:+LogTouchedMethods",
          "-XX:+PrintTouchedMethodsAtExit",
          TestLogTouchedMethods.class.getName());
      output = new OutputAnalyzer(pb.start());
      lines = output.asLines();

      if (lines.size() < 1) {
        throw new Exception("Empty output");
      }

      first = lines.get(0);
      if (!first.equals("# Method::print_touched_methods version 1")) {
        throw new Exception("First line mismatch");
      }

      output.shouldContain("TestLogTouchedMethods.methodA:()V");
      output.shouldNotContain("TestLogTouchedMethods.methodB:()V");
      output.shouldHaveExitValue(0);

      pb = ProcessTools.createJavaProcessBuilder(
          "-XX:+UnlockDiagnosticVMOptions",
          "-Xint",
          "-XX:+LogTouchedMethods",
          "-XX:+PrintTouchedMethodsAtExit",
          "-XX:-TieredCompilation",
          TestLogTouchedMethods.class.getName());
      output = new OutputAnalyzer(pb.start());
      lines = output.asLines();

      if (lines.size() < 1) {
        throw new Exception("Empty output");
      }

      first = lines.get(0);
      if (!first.equals("# Method::print_touched_methods version 1")) {
        throw new Exception("First line mismatch");
      }

      output.shouldContain("TestLogTouchedMethods.methodA:()V");
      output.shouldNotContain("TestLogTouchedMethods.methodB:()V");
      output.shouldHaveExitValue(0);
    }
}
