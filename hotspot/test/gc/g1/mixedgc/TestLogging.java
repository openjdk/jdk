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

/*
 * @test TestLogging
 * @summary Check that a mixed GC is reflected in the gc logs
 * @requires vm.gc=="G1" | vm.gc=="null"
 * @library /testlibrary /test/lib
 * @ignore 8138607
 * @modules java.management
 * @build sun.hotspot.WhiteBox gc.g1.mixedgc.TestLogging
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver gc.g1.mixedgc.TestLogging
 */

package gc.g1.mixedgc;

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Test spawns MixedGCProvoker in a separate VM and expects to find a message
 * telling that a mixed gc has happened
 */
public class TestLogging {
    private static final String[] COMMON_OPTIONS = new String[]{
            "-Xbootclasspath/a:.", "-XX:+UseG1GC",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-XX:SurvivorRatio=1", // Survivor-to-eden ratio is 1:1
            "-Xms10M", "-Xmx10M",
            "-XX:MaxTenuringThreshold=1", // promote objects after first gc
            "-XX:InitiatingHeapOccupancyPercent=0", // marking cycle happens
            // each time
            "-XX:G1MixedGCCountTarget=4",
            "-XX:MaxGCPauseMillis=30000", // to have enough time
            "-XX:G1HeapRegionSize=1m", "-XX:G1HeapWastePercent=0",
            "-XX:G1MixedGCLiveThresholdPercent=100"};

    public static final int ALLOCATION_SIZE = 20000;
    public static final int ALLOCATION_COUNT = 15;

    public static void main(String args[]) throws Exception {
        // Test turns logging on by giving -Xlog:gc flag
        test("-Xlog:gc");
        // Test turns logging on by giving -Xlog:gc=debug flag
        test("-Xlog:gc=debug");
    }

    private static void test(String vmFlag) throws Exception {
        System.out.println(String.format("%s: running with %s flag", TestLogging.class.getSimpleName(), vmFlag));
        OutputAnalyzer output = spawnMixedGCProvoker(vmFlag);
        System.out.println(output.getStdout());
        output.shouldHaveExitValue(0);
        output.shouldContain("Pause Mixed (G1 Evacuation Pause)");
    }

    /**
     * Method spawns MixedGCProvoker with addition flags set
     *
     * @parameter extraFlags -flags to be added to the common options set
     */
    private static OutputAnalyzer spawnMixedGCProvoker(String... extraFlags)
            throws Exception {
        List<String> testOpts = new ArrayList<>();
        Collections.addAll(testOpts, COMMON_OPTIONS);
        Collections.addAll(testOpts, extraFlags);
        testOpts.add(MixedGCProvoker.class.getName());
        System.out.println(testOpts);
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(false,
                testOpts.toArray(new String[testOpts.size()]));
        return new OutputAnalyzer(pb.start());
    }
}

/**
 * Utility class to guarantee a mixed GC. The class allocates several arrays and
 * promotes them to the oldgen. After that it tries to provoke mixed GC by
 * allocating new objects.
 *
 * The necessary condition for guaranteed mixed GC is running MixedGCProvoker is
 * running in VM with the following flags: -XX:MaxTenuringThreshold=1, -Xms10M,
 * -Xmx10M, -XX:G1MixedGCLiveThresholdPercent=100, -XX:G1HeapWastePercent=0,
 * -XX:G1HeapRegionSize=1m
 */
class MixedGCProvoker {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final List<byte[]> liveOldObjects = new ArrayList<>();
    private static final List<byte[]> newObjects = new ArrayList<>();

    private static void allocateOldObjects() throws Exception {
        List<byte[]> deadOldObjects = new ArrayList<>();
        // Allocates buffer and promotes it to the old gen. Mix live and dead old
        // objects
        for (int i = 0; i < TestLogging.ALLOCATION_COUNT; ++i) {
            liveOldObjects.add(new byte[TestLogging.ALLOCATION_SIZE * 10]);
            deadOldObjects.add(new byte[TestLogging.ALLOCATION_SIZE * 10]);
        }

        // need only 2 promotions to promote objects to the old gen
        WB.youngGC();
        WB.youngGC();
        // check it is promoted & keep alive
        Asserts.assertTrue(WB.isObjectInOldGen(liveOldObjects),
                "List of the objects is suppose to be in OldGen");
        Asserts.assertTrue(WB.isObjectInOldGen(deadOldObjects),
                "List of the objects is suppose to be in OldGen");
    }


    /**
     * Waits until Concurent Mark Cycle finishes
     * @param wb  Whitebox instance
     * @param sleepTime sleep time
     */
    public static void waitTillCMCFinished(WhiteBox wb, int sleepTime) {
        while (wb.g1InConcurrentMark()) {
            if (sleepTime > -1) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    System.out.println("Got InterruptedException while waiting for ConcMarkCycle to finish");
                }
            }
        }
    }



    public static void main(String args[]) throws Exception {
        // allocate old objects
        allocateOldObjects();
        waitTillCMCFinished(WB, 0);
        WB.g1StartConcMarkCycle();
        waitTillCMCFinished(WB, 0);

        WB.youngGC();
        System.out.println("Allocating new objects to provoke mixed GC");
        // allocate more objects to provoke GC
        for (int i = 0; i < (TestLogging.ALLOCATION_COUNT * 20); i++) {
            newObjects.add(new byte[TestLogging.ALLOCATION_SIZE]);
        }
        // check that liveOldObjects still alive
        Asserts.assertTrue(WB.isObjectInOldGen(liveOldObjects),
                "List of the objects is suppose to be in OldGen");
    }
}
