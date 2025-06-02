/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

/*
 * @test TestEagerReclaimHumongousRegionsClearMarkBits
 * @bug 8051973
 * @summary Test to make sure that eager reclaim of humongous objects correctly clears
 * mark bitmaps at reclaim.
 * @requires vm.gc.G1
 * @requires vm.debug
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.g1.TestEagerReclaimHumongousRegionsClearMarkBits
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestEagerReclaimHumongousRegionsClearMarkBits {
    public static void main(String[] args) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx20M",
                                                                    "-Xms20m",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+VerifyAfterGC",
                                                                    "-Xbootclasspath/a:.",
                                                                    "-Xlog:gc=debug,gc+humongous=debug",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-XX:+WhiteBoxAPI",
                                                                    TestEagerReclaimHumongousRegionsClearMarkBitsRunner.class.getName());

        String log = output.getStdout();
        System.out.println(log);
        output.shouldHaveExitValue(0);

        // Find the log output indicating that the humongous object has been reclaimed, and marked.
        Pattern pattern = Pattern.compile("Humongous region .* marked 1 .* reclaim candidate 1 type array 1");
        Asserts.assertTrue(pattern.matcher(log).find(), "Could not find log output matching marked humongous region.");

        pattern = Pattern.compile("Reclaimed humongous region .*");
        Asserts.assertTrue(pattern.matcher(log).find(), "Could not find log output reclaiming humongous region");
    }
}

class TestEagerReclaimHumongousRegionsClearMarkBitsRunner {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int M = 1024 * 1024;

    public static void main(String[] args) {
        WB.fullGC();

        Object largeObj = new int[M]; // Humongous object.

        WB.concurrentGCAcquireControl();
        WB.concurrentGCRunTo(WB.BEFORE_MARKING_COMPLETED);

        System.out.println("Large object at " + largeObj);

        largeObj = null;
        WB.youngGC(); // Should reclaim marked humongous object.

        WB.concurrentGCRunToIdle();
    }
}

