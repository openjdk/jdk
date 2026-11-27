/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package gc.serial;

import java.lang.ref.Reference;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

/*
 * @test TestUpgradeToFullGC
 * @bug 8397891
 * @summary Tests that upgrades to full gc occur properly or not depending on the
 *          type of garbage collection if the old gen is expanded.
 * @requires vm.gc.Serial
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.serial.TestUpgradeToFullGC
 */
public class TestUpgradeToFullGC {

    private static final int ARRAYSIZE_IN_KB = 6 * 1024;
    private static final String UPGRADE_STRING = "Upgrade to full collection";

    private static void runTest(boolean expectUpgradeToFull) throws  Exception {
        // We set up the heap so that after a retained 6MB array that has been allocated in young gen
        // expands the old gen, that is only 2M in size initially.
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                "-Xms12m",
                "-Xmn10m",
                "-Xmx24m",
                "-XX:+UseSerialGC",
                "-Xbootclasspath/a:.",
                "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
                "-cp", System.getProperty("java.class.path"),
                "-Xlog:gc=trace",
                GCTest.class.getName(),
                "" + expectUpgradeToFull);
        System.out.println(output.getStdout());
        if (expectUpgradeToFull) {
            output.shouldContain("Pause Young (Allocation Failure)"); // Verify that the right kind of pause occurred.
            output.shouldContain(UPGRADE_STRING);
            output.shouldContain("Pause Full (Allocation Failure)"); // Observe the upgrade to full collection.
        } else {
            output.shouldContain("GC(0) Pause Full (WhiteBox Initiated Full GC)");
            output.shouldNotContain(UPGRADE_STRING);
            output.shouldContain("Pause Young (WhiteBox Initiated Young GC)");
            output.shouldNotContain("Pause Full (WhiteBox Initiated Young GC)");
        }
        output.shouldHaveExitValue(0);
    }

    public static void main(String args[]) throws Exception {
        runTest(true);
        runTest(false);
    }

    static class GCTest {

        static final WhiteBox wb = WhiteBox.getWhiteBox();
        static Object sink;

        public static void main(String[] args) {
            wb.fullGC();
            Object obj = new byte[ARRAYSIZE_IN_KB * 1024]; // Allocated in young gen because it fits.
            if (Boolean.parseBoolean(args[0]))  {
                // Induce and young collection with an allocation failure cause.
                for (int i = 0; i < 10000; i++) {
                    sink = new byte[1000];
                }
            } else {
                wb.youngGC();
            }
            Reference.reachabilityFence(obj);
        }
    }
}
