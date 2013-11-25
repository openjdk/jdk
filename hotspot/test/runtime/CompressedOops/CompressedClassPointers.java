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
 * @test
 * @bug 8024927
 * @summary Testing address of compressed class pointer space as best as possible.
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.*;

public class CompressedClassPointers {

    public static void smallHeapTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedBaseAddress=8g",
            "-Xmx128m",
            "-XX:+PrintCompressedOopsMode",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Narrow klass base: 0x0000000000000000");
        output.shouldHaveExitValue(0);
    }

    public static void smallHeapTestWith3G() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:CompressedClassSpaceSize=3g",
            "-Xmx128m",
            "-XX:+PrintCompressedOopsMode",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Narrow klass base: 0x0000000000000000, Narrow klass shift: 3");
        output.shouldHaveExitValue(0);
    }

    public static void largeHeapTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-Xmx30g",
            "-XX:+PrintCompressedOopsMode",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("Narrow klass base: 0x0000000000000000");
        output.shouldContain("Narrow klass shift: 0");
        output.shouldHaveExitValue(0);
    }

    public static void largePagesTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-Xmx128m",
            "-XX:+UseLargePages",
            "-XX:+PrintCompressedOopsMode",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Narrow klass base:");
        output.shouldHaveExitValue(0);
    }

    public static void sharingTest() throws Exception {
        // Test small heaps
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./sample.jsa",
            "-Xmx128m",
            "-XX:SharedBaseAddress=8g",
            "-XX:+PrintCompressedOopsMode",
            "-XX:+VerifyBeforeGC",
            "-Xshare:dump");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        try {
          output.shouldContain("Loading classes to share");
          output.shouldHaveExitValue(0);

          pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./sample.jsa",
            "-Xmx128m",
            "-XX:SharedBaseAddress=8g",
            "-XX:+PrintCompressedOopsMode",
            "-Xshare:on",
            "-version");
          output = new OutputAnalyzer(pb.start());
          output.shouldContain("sharing");
          output.shouldHaveExitValue(0);

        } catch (RuntimeException e) {
          output.shouldContain("Unable to use shared archive");
          output.shouldHaveExitValue(1);
        }
    }

  public static void main(String[] args) throws Exception {
      if (!Platform.is64bit()) {
          // Can't test this on 32 bit, just pass
          System.out.println("Skipping test on 32bit");
          return;
      }
      // Solaris 10 can't mmap compressed oops space without a base
      if (Platform.isSolaris()) {
           String name = System.getProperty("os.version");
           if (name.equals("5.10")) {
               System.out.println("Skipping test on Solaris 10");
               return;
           }
      }
      smallHeapTest();
      smallHeapTestWith3G();
      largeHeapTest();
      largePagesTest();
      sharingTest();
  }
}
