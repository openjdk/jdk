/*
* Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestParallelGCThreads
 * @key gc
 * @bug 8059527
 * @summary Tests argument processing for ParallelGCThreads
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @run driver TestParallelGCThreads
 */

import jdk.test.lib.*;

public class TestParallelGCThreads {

  public static void main(String args[]) throws Exception {

    // For each parallel collector (G1, Parallel, ParNew/CMS)
    for (String gc : new String[] {"G1", "Parallel", "ConcMarkSweep"}) {

      // Make sure the VM does not allow ParallelGCThreads set to 0
      String[] flags = new String[] {"-XX:+Use" + gc + "GC", "-XX:ParallelGCThreads=0", "-XX:+PrintFlagsFinal", "-version"};
      ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(flags);
      OutputAnalyzer output = new OutputAnalyzer(pb.start());
      output.shouldHaveExitValue(1);

      // Do some basic testing to ensure the flag updates the count
      for (long i = 1; i <= 3; i++) {
        flags = new String[] {"-XX:+Use" + gc + "GC", "-XX:ParallelGCThreads=" + i, "-XX:+PrintFlagsFinal", "-version"};
        long count = getParallelGCThreadCount(flags);
        Asserts.assertEQ(count, i, "Specifying ParallelGCThreads=" + i + " for " + gc + "GC does not set the thread count properly!");
      }
    }
  }

  public static long getParallelGCThreadCount(String flags[]) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(flags);
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldHaveExitValue(0);
    String stdout = output.getStdout();
    return FlagsValue.getFlagLongValue("ParallelGCThreads", stdout);
  }
}
