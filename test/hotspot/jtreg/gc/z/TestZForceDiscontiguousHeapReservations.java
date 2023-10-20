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
 * @test TestZForceDiscontiguousHeapReservations
 * @requires vm.gc.ZGenerational & vm.debug
 * @summary Test the ZForceDiscontiguousHeapReservations development flag
 * @library /test/lib
 * @run driver gc.z.TestZForceDiscontiguousHeapReservations
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestZForceDiscontiguousHeapReservations {

    private static void testValue(int n) throws Exception  {
        /**
         *  Xmx is picked so that it is divisible by 'ZForceDiscontiguousHeapReservations * ZGranuleSize'
         *  Xms is picked so that it is less than '16 * Xmx / ZForceDiscontiguousHeapReservations' as ZGC
         *   cannot currently handle a discontiguous heap with an initial size larger than the individual
         *   reservations.
         */
        final int XmxInM = 2000;
        final int XmsInM = Math.min(16 * XmxInM / (n + 1), XmxInM);
        OutputAnalyzer oa = ProcessTools.executeProcess(ProcessTools.createTestJavaProcessBuilder(
                                                        "-XX:+UseZGC",
                                                        "-XX:+ZGenerational",
                                                        "-Xms" + XmsInM + "M",
                                                        "-Xmx" + XmxInM + "M",
                                                        "-Xlog:gc,gc+init",
                                                        "-XX:ZForceDiscontiguousHeapReservations=" + n,
                                                        "-version"))
                                        .outputTo(System.out)
                                        .errorTo(System.out)
                                        .shouldHaveExitValue(0);
        if (n > 1) {
            oa.shouldContain("Address Space Type: Discontiguous");
        }
    }

    public static void main(String[] args) throws Exception {
        testValue(0);
        testValue(1);
        testValue(2);
        testValue(100);
    }
}
