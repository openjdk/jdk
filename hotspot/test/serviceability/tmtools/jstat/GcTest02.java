/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import utils.*;
/*
 * @test
 * @summary Test checks output displayed with jstat -gc.
 *          Test scenario:
 *          tests forces debuggee application eat ~70% of heap and runs jstat.
 *          jstat should show that ~70% of heap is utilized (OC/OU ~= 70%).
 * @library /test/lib/share/classes
 * @library ../share
 * @build common.*
 * @build utils.*
 * @run main/othervm -XX:+UsePerfData -Xms128M -XX:MaxMetaspaceSize=128M GcTest02
 */

public class GcTest02 {

    private final static float targetMemoryUsagePercent = 0.7f;

    public static void main(String[] args) throws Exception {

        // We will be running "jstat -gc" tool
        JstatGcTool jstatGcTool = new JstatGcTool(ProcessHandle.current().getPid());

        // Run once and get the  results asserting that they are reasonable
        JstatGcResults measurement1 = jstatGcTool.measure();
        measurement1.assertConsistency();

        GcProvoker gcProvoker = GcProvoker.createGcProvoker();

        // Eat metaspace and heap then run the tool again and get the results  asserting that they are reasonable
        gcProvoker.eatMetaspaceAndHeap(targetMemoryUsagePercent);
        JstatGcResults measurement2 = jstatGcTool.measure();
        measurement2.assertConsistency();

        // Assert that space has been utilized acordingly
        JstatResults.assertSpaceUtilization(measurement2, targetMemoryUsagePercent);
    }

    private static void assertThat(boolean result, String message) {
        if (!result) {
            throw new RuntimeException(message);
        };
    }
}
