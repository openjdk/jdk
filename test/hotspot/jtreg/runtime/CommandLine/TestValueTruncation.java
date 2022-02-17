/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8281472
 * @summary Test that values for numeric options fail if too large or too small.
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestValueTruncation
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestValueTruncation {

    public static void main(String args[]) throws Exception {
      // Test some large valid values for int, uint, and intx options.
      ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
          "-XX:+PrintFlagsFinal", "-XX:+UnlockDiagnosticVMOptions",
          "-XX:CompilerDirectivesLimit=2147483647",       // 0x7fffffff
          "-XX:HandshakeTimeout=4294967295",              // 0xffffffff
          "-XX:MaxJNILocalCapacity=9223372036854775807",  // 0x7fffffffffffffff
          "-version");
      OutputAnalyzer output = new OutputAnalyzer(pb.start());
      output.shouldNotContain("Could not create the Java Virtual Machine");
      output.shouldMatch("CompilerDirectivesLimit += 2147483647");
      output.shouldMatch("HandshakeTimeout += 4294967295");
      output.shouldMatch("MaxJNILocalCapacity += 9223372036854775807");
      output.shouldHaveExitValue(0);

      // Test an int option with value 0x100000000.  It should fail.
      pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
          "-XX:CompilerDirectivesLimit=4294967296", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldContain("Improperly specified VM option 'CompilerDirectivesLimit=4294967296'");
      output.shouldContain("Could not create the Java Virtual Machine");
      output.shouldHaveExitValue(1);

      // Test an int option with value max_int + 1.  It should fail.
      pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
          "-XX:CompilerDirectivesLimit=2147483648", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldContain("Improperly specified VM option 'CompilerDirectivesLimit=2147483648'");
      output.shouldContain("Could not create the Java Virtual Machine");
      output.shouldHaveExitValue(1);

      // Test an int option with value min_int.  It should succeed.
      pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
          "-XX:+PrintFlagsFinal", "-XX:CompilerDirectivesLimit=-2147483648", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldNotContain("Could not create the Java Virtual Machine");
      output.shouldMatch("CompilerDirectivesLimit += -2147483648");
      output.shouldHaveExitValue(0);

      // Test an int option with value min_int - 1.  It should fail.
      pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
          "-XX:CompilerDirectivesLimit=-2147483649", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldContain("Improperly specified VM option 'CompilerDirectivesLimit=-2147483649'");
      output.shouldContain("Could not create the Java Virtual Machine");
      output.shouldHaveExitValue(1);

      // Test an uint option with value 0x100000000.  It should return 0.
      pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
          "-XX:+PrintFlagsFinal", "-XX:HandshakeTimeout=4294967296", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldMatch("HandshakeTimeout += 0");
      output.shouldNotContain("Could not create the Java Virtual Machine");
      output.shouldNotContain("Improperly specified VM option");
      output.shouldHaveExitValue(0);

      // Test an uint option with value 0x100000001.  It should fail.
      pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
          "-XX:HandshakeTimeout=4294967297", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldContain("Improperly specified VM option 'HandshakeTimeout=4294967297'");
      output.shouldContain("Could not create the Java Virtual Machine");
      output.shouldHaveExitValue(1);

      // Test that an intx option with a value of min_jint succeeds.
      pb = ProcessTools.createJavaProcessBuilder("-XX:+PrintFlagsFinal",
          "-XX:MaxJNILocalCapacity=-9223372036854775808", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldMatch("MaxJNILocalCapacity += -9223372036854775808");
      output.shouldHaveExitValue(0);

      // Test that an intx option with a value < min_jint fails.
      pb = ProcessTools.createJavaProcessBuilder(
          "-XX:MaxJNILocalCapacity=-9223372036854775809", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldContain("Improperly specified VM option 'MaxJNILocalCapacity=-9223372036854775809'");
      output.shouldContain("Could not create the Java Virtual Machine");
      output.shouldHaveExitValue(1);

      // Test that an intx option with a value > max_jint fails.
      pb = ProcessTools.createJavaProcessBuilder(
          "-XX:MaxJNILocalCapacity=9223372036854775808", "-version");
      output = new OutputAnalyzer(pb.start());
      output.shouldContain("Improperly specified VM option 'MaxJNILocalCapacity=9223372036854775808'");
      output.shouldContain("Could not create the Java Virtual Machine");
      output.shouldHaveExitValue(1);
  }
}
