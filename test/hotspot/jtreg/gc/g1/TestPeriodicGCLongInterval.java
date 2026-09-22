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

package gc.g1;

/**
 * @test TestPeriodicGCLongInterval.java
 * @bug 8392849
 * @requires vm.gc.G1
 * @summary Verify that periodic gc interval setting does not overflow the scheduling calculation.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 * @modules java.management/sun.management
 * @run driver gc.g1.TestPeriodicGCLongInterval
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestPeriodicGCLongInterval {

    private static final String PERIODIC_GC_SCHEDULED = "Checking for periodic GC";

    private static boolean containsOnce(String haystack, String needle) {
        return haystack.indexOf(needle) != -1 && haystack.indexOf(needle) == haystack.lastIndexOf(needle);
    }

    public static void main(String[] args) throws Exception {
        long interval = Platform.is64bit() ? Long.MAX_VALUE : 0xFFFF_FFFFL;
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-XX:G1PeriodicGCInterval=" + interval,
                                                                    "-Xlog:gc+init,gc+periodic=debug",
                                                                    "-Xmx10M",
                                                                    GCTest.class.getName());

        System.out.println(output.getStdout());

        if (!containsOnce(output.getStdout(), PERIODIC_GC_SCHEDULED)) {
            throw new Exception("Periodic task executed more than once.");
        }

        output.shouldContain("Periodic GC: Enabled");
        output.shouldContain("Checking for periodic GC");
        output.shouldHaveExitValue(0);
    }

    static class GCTest {
        public static void main(String [] args) throws Exception {
            System.out.println("Waiting for messages...");
            Thread.sleep(1000);
            System.out.println("Done");
        }
    }
}
