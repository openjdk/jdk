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

import utils.*;

/*
 * @test
 * @summary Test checks the consistency of the output
 * displayed with jstat -gccapacity.
 * @library /test/lib/share/classes
 * @library ../share
 * @requires vm.opt.ExplicitGCInvokesConcurrent != true
 * @build common.*
 * @build utils.*
 * @ignore 8149778
 * @run main/othervm -XX:+UsePerfData -Xmx128M GcCapacityTest
 */
public class GcCapacityTest {

    public static void main(String[] args) throws Exception {

        // We will be running "jstat -gc" tool
        JstatGcCapacityTool jstatGcTool = new JstatGcCapacityTool(ProcessHandle.current().getPid());

        // Run once and get the  results asserting that they are reasonable
        JstatGcCapacityResults measurement1 = jstatGcTool.measure();
        measurement1.assertConsistency();

        // Provoke a gc and verify the changed values
        GcProvoker gcProvoker = GcProvoker.createGcProvoker();
        gcProvoker.provokeGc();
        JstatGcCapacityResults measurement2 = jstatGcTool.measure();
        measurement2.assertConsistency();

        // Assert that the GC events count has increased
        JstatResults.assertGCEventsIncreased(measurement1, measurement2);

        // Provoke a gc again and verify the changed values
        gcProvoker.provokeGc();
        JstatGcCapacityResults measurement3 = jstatGcTool.measure();
        measurement3.assertConsistency();

        // Assert that the GC events count has increased
        JstatResults.assertGCEventsIncreased(measurement1, measurement2);
    }

}
