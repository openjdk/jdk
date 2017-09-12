/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestPrintReferences
 * @bug 8136991 8186402
 * @summary Validate the reference processing logging
 * @key gc
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import java.lang.ref.SoftReference;
import java.util.ArrayList;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestPrintReferences {
  public static void main(String[] args) throws Exception {
    ProcessBuilder pb_enabled = ProcessTools.createJavaProcessBuilder("-Xlog:gc+phases+ref=debug",
                                                                      "-XX:+UseG1GC",
                                                                      "-Xmx10M",
                                                                      // Explicit thread setting is required to avoid using only 1 thread
                                                                      "-XX:ParallelGCThreads=2",
                                                                      GCTest.class.getName());
    OutputAnalyzer output = new OutputAnalyzer(pb_enabled.start());

    String indent_4 = "    ";
    String indent_6 = "      ";
    String indent_8 = "        ";
    String gcLogTimeRegex = ".* GC\\([0-9]+\\) ";
    String countRegex = "[0-9]+";
    String timeRegex = "[0-9]+[.,][0-9]+ms";
    String totalRegex = gcLogTimeRegex + indent_4 + "Reference Processing: " + timeRegex + "\n";
    String balanceRegex = gcLogTimeRegex + indent_8 + "Balance queues: " + timeRegex + "\n";
    String softRefRegex = gcLogTimeRegex + indent_6 + "SoftReference: " + timeRegex + "\n";
    String weakRefRegex = gcLogTimeRegex + indent_6 + "WeakReference: " + timeRegex + "\n";
    String finalRefRegex = gcLogTimeRegex + indent_6 + "FinalReference: " + timeRegex + "\n";
    String phantomRefRegex = gcLogTimeRegex + indent_6 + "PhantomReference: " + timeRegex + "\n";
    String refDetailRegex = gcLogTimeRegex + indent_8 + "Phase2: " + timeRegex + "\n" +
                            gcLogTimeRegex + indent_8 + "Phase3: " + timeRegex + "\n" +
                            gcLogTimeRegex + indent_8 + "Discovered: " + countRegex + "\n" +
                            gcLogTimeRegex + indent_8 + "Cleared: " + countRegex + "\n";
    String softRefDetailRegex = gcLogTimeRegex + indent_8 + "Phase1: " + timeRegex + "\n" + refDetailRegex;
    String enqueueRegex = gcLogTimeRegex + indent_4 + "Reference Enqueuing: " + timeRegex + "\n";
    String enqueueDetailRegex = gcLogTimeRegex + indent_6 + "Reference Counts:  Soft: " + countRegex +
                                "  Weak: " + countRegex + "  Final: " + countRegex + "  Phantom: " + countRegex + "\n";

    output.shouldMatch(/* Total Reference processing time */
                       totalRegex +
                       /* SoftReference processing */
                       softRefRegex + balanceRegex + softRefDetailRegex +
                       /* WeakReference processing */
                       weakRefRegex + balanceRegex + refDetailRegex +
                       /* FinalReference processing */
                       finalRefRegex + balanceRegex + refDetailRegex +
                       /* PhantomReference processing */
                       phantomRefRegex + balanceRegex + refDetailRegex +
                       /* Total Enqueuing time */
                       enqueueRegex +
                         /* Enqueued Stats */
                       enqueueDetailRegex
                       );

    output.shouldHaveExitValue(0);
  }

  static class GCTest {
    static final int M = 1024 * 1024;

    public static void main(String [] args) {

      ArrayList arrSoftRefs = new ArrayList();

      // Populate to triger GC and then Reference related logs will be printed.
      for (int i = 0; i < 10; i++) {
        byte[] tmp = new byte[M];

        arrSoftRefs.add(new SoftReference(tmp));
      }
    }
  }
}
