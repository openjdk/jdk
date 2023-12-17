/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc.z;

/**
 * @test TestZNMT
 * @bug 8310743
 * @requires vm.gc.ZGenerational & vm.debug
 * @summary Test NMT and ZGenerational heap reservation / commits interactions.
 * @library / /test/lib
 * @run driver gc.z.TestZNMT
 */

import static gc.testlibrary.Allocation.blackHole;
import java.util.ArrayList;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestZNMT {
    private static final int XmxInM = 2000;
    static class Test {
        private static final int K = 1024;
        private static final int M = K * K;
        public static void main(String[] args) throws Exception {
            final int zForceDiscontiguousHeapReservations = Integer.parseInt(args[0]);
            final int XmsInM = Integer.parseInt(args[1]);
            // 75% of the largest allocation that fits within one reservation
            // (or Xmx / zForceDiscontiguousHeapReservations), whichever is smallest
            final int allocationInM = (int)(Math.min(zForceDiscontiguousHeapReservations == 0
                                                        ? XmxInM
                                                        : XmxInM / zForceDiscontiguousHeapReservations,
                                                     XmsInM) * 0.75);
            ArrayList<byte[]> list = new ArrayList<>(zForceDiscontiguousHeapReservations);
            for (int i = 0; i < zForceDiscontiguousHeapReservations; i++) {
                list.add(new byte[allocationInM * M]);
            }
            blackHole(list);
        }
    }


    private static void testValue(int zForceDiscontiguousHeapReservations) throws Exception  {
        /**
         *  Xmx is picked so that it is divisible by 'ZForceDiscontiguousHeapReservations * ZGranuleSize'
         *  Xms is picked so that it is less than '16 * Xmx / ZForceDiscontiguousHeapReservations' as ZGC
         *   cannot currently handle a discontiguous heap with an initial size larger than the individual
         *   reservations.
         */
        final int XmsInM = Math.min(16 * XmxInM / (zForceDiscontiguousHeapReservations + 1), XmxInM);
        OutputAnalyzer oa = ProcessTools.executeProcess(ProcessTools.createTestJavaProcessBuilder(
                                                        "-XX:+UseZGC",
                                                        "-XX:+ZGenerational",
                                                        "-Xms" + XmsInM + "M",
                                                        "-Xmx" + XmxInM + "M",
                                                        "-Xlog:gc,gc+init",
                                                        "-XX:ZForceDiscontiguousHeapReservations=" + zForceDiscontiguousHeapReservations,
                                                        "-XX:NativeMemoryTracking=detail",
                                                        "-XX:+PrintNMTStatistics",
                                                        Test.class.getName(),
                                                        Integer.toString(zForceDiscontiguousHeapReservations),
                                                        Integer.toString(XmxInM)))
                                        .outputTo(System.out)
                                        .errorTo(System.out)
                                        .shouldHaveExitValue(0);
        if (zForceDiscontiguousHeapReservations > 1) {
            oa.shouldContain("Address Space Type: Discontiguous");
        }

        if (XmsInM < XmxInM) {
            // There will be reservations which are smaller than the total
            // memory allocated in TestZNMT.Test.main. This means that some
            // reservation will be completely committed and print the following
            // in the NMT statistics.
            oa.shouldMatch("reserved and committed \\d+ for Java Heap");
        }
    }

    public static void main(String[] args) throws Exception {
        testValue(0);
        testValue(1);
        testValue(2);
        testValue(100);
    }
}
