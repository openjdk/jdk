/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136991
 * @summary Validate the reference processing logging
 * @key gc
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestPrintReferences {
  public static void main(String[] args) throws Exception {
    ProcessBuilder pb_enabled =
      ProcessTools.createJavaProcessBuilder("-Xlog:gc+ref=debug", "-Xmx10M", GCTest.class.getName());
    OutputAnalyzer output = new OutputAnalyzer(pb_enabled.start());

    String countRegex = "[0-9]+ refs";
    String timeRegex = "[0-9]+[.,][0-9]+ms";

    output.shouldMatch(".* GC\\([0-9]+\\) SoftReference " + timeRegex + "\n" +
                       ".* GC\\([0-9]+\\) WeakReference " + timeRegex + "\n" +
                       ".* GC\\([0-9]+\\) FinalReference " + timeRegex + "\n" +
                       ".* GC\\([0-9]+\\) PhantomReference " + timeRegex + "\n" +
                       ".* GC\\([0-9]+\\) JNI Weak Reference " + timeRegex + "\n" +
                       ".* GC\\([0-9]+\\) Ref Counts: Soft: [0-9]+ Weak: [0-9]+ Final: [0-9]+ Phantom: [0-9]+\n");

    output.shouldHaveExitValue(0);
  }

  static class GCTest {
    public static void main(String [] args) {
      System.gc();
    }
  }
}
