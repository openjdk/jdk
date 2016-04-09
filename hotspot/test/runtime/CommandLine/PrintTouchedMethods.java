/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025692
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @library /testlibrary
 * @compile TestLogTouchedMethods.java PrintTouchedMethods.java
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+LogTouchedMethods PrintTouchedMethods
 */

import java.io.File;
import java.util.List;
import jdk.test.lib.*;

public class PrintTouchedMethods {

    public static void main(String args[]) throws Exception {
      String[] javaArgs1 = {"-XX:-UnlockDiagnosticVMOptions", "-XX:+LogTouchedMethods", "-XX:+PrintTouchedMethodsAtExit", "TestLogTouchedMethods"};
      ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(javaArgs1);

      // UnlockDiagnostic turned off, should fail
      OutputAnalyzer output = new OutputAnalyzer(pb.start());
      output.shouldContain("Error: VM option 'LogTouchedMethods' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.");
      output.shouldContain("Error: Could not create the Java Virtual Machine.");

      String[] javaArgs2 = {"-XX:+UnlockDiagnosticVMOptions", "-XX:+LogTouchedMethods", "-XX:+PrintTouchedMethodsAtExit", "TestLogTouchedMethods"};
      pb = ProcessTools.createJavaProcessBuilder(javaArgs2);
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

      String[] javaArgs3 = {"-XX:+UnlockDiagnosticVMOptions", "-Xint", "-XX:+LogTouchedMethods", "-XX:+PrintTouchedMethodsAtExit", "TestLogTouchedMethods"};
      pb = ProcessTools.createJavaProcessBuilder(javaArgs3);
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

      String[] javaArgs4 = {"-XX:+UnlockDiagnosticVMOptions", "-Xint", "-XX:+LogTouchedMethods", "-XX:+PrintTouchedMethodsAtExit", "-XX:-TieredCompilation", "TestLogTouchedMethods"};
      pb = ProcessTools.createJavaProcessBuilder(javaArgs4);
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

      // Test jcmd PrintTouchedMethods VM.print_touched_methods
      String pid = Integer.toString(ProcessTools.getProcessId());
      pb = new ProcessBuilder();
      pb.command(new String[] {JDKToolFinder.getJDKTool("jcmd"), pid, "VM.print_touched_methods"});
      output = new OutputAnalyzer(pb.start());
      try {
        output.shouldContain("PrintTouchedMethods.main:([Ljava/lang/String;)V");
      } catch (RuntimeException e) {
        output.shouldContain("Unknown diagnostic command");
      }
  }
}
