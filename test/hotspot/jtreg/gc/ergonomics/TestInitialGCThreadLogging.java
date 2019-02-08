/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.ergonomics;

/*
 * @test TestInitialGCThreadLogging
 * @bug 8157240
 * @summary Check trace logging of initial GC threads.
 * @requires vm.gc=="null"
 * @key gc
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI gc.ergonomics.TestInitialGCThreadLogging
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import sun.hotspot.gc.GC;

public class TestInitialGCThreadLogging {
  public static void main(String[] args) throws Exception {

    testInitialGCThreadLogging("UseConcMarkSweepGC", "GC Thread");

    testInitialGCThreadLogging("UseG1GC", "GC Thread");

    testInitialGCThreadLogging("UseParallelGC", "ParGC Thread");

    if (GC.Shenandoah.isSupported()) {
        testInitialGCThreadLogging("UseShenandoahGC", "Shenandoah GC Thread");
    }
  }

  private static void verifyDynamicNumberOfGCThreads(OutputAnalyzer output, String threadName) {
    output.shouldHaveExitValue(0); // test should run succesfully
    output.shouldContain(threadName);
  }

  private static void testInitialGCThreadLogging(String gcFlag, String threadName) throws Exception {
    // UseDynamicNumberOfGCThreads and TraceDynamicGCThreads enabled
    String[] baseArgs = {"-XX:+UnlockExperimentalVMOptions", "-XX:+" + gcFlag, "-Xmx10M", "-XX:+UseDynamicNumberOfGCThreads", "-Xlog:gc+task=trace", "-version"};

    // Base test with gc and +UseDynamicNumberOfGCThreads:
    ProcessBuilder pb_enabled = ProcessTools.createJavaProcessBuilder(baseArgs);
    verifyDynamicNumberOfGCThreads(new OutputAnalyzer(pb_enabled.start()), threadName);
  }
}
