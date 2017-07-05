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
 * @key nmt regression
 * @bug 8005936
 * @summary Make sure PrintNMTStatistics works on normal JVM exit
 * @library /testlibrary /testlibrary/whitebox
 * @build PrintNMTStatistics
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main PrintNMTStatistics
 */

import com.oracle.java.testlibrary.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import sun.hotspot.WhiteBox;

public class PrintNMTStatistics {

  public static void main(String args[]) throws Exception {

    // We start a new java process running with an argument and use WB API to ensure
    // we have data for NMT on VM exit
    if (args.length > 0) {
      // Use WB API to ensure that all data has been merged before we continue
      if (!WhiteBox.getWhiteBox().NMTWaitForDataMerge()) {
        throw new Exception("Call to WB API NMTWaitForDataMerge() failed");
      }
      return;
    }

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-Xbootclasspath/a:.",
        "-XX:+WhiteBoxAPI",
        "-XX:NativeMemoryTracking=summary",
        "-XX:+PrintNMTStatistics",
        "PrintNMTStatistics",
        "test");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldContain("Java Heap (reserved=");
    output.shouldNotContain("error");
    output.shouldHaveExitValue(0);
  }
}
