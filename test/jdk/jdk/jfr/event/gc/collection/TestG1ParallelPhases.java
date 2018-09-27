/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.event.gc.collection;

import static java.lang.System.gc;
import static java.lang.Thread.sleep;
import static java.util.Set.of;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.IntStream.range;
import static jdk.jfr.event.gc.collection.Provoker.provokeMixedGC;
import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.jfr.Events.fromRecording;
import static sun.hotspot.WhiteBox.getWhiteBox;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import jdk.jfr.Recording;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import sun.hotspot.WhiteBox;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @requires vm.gc == "G1" | vm.gc == null
 * @library /test/lib /test/jdk
 * @build sun.hotspot.WhiteBox
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:MaxTenuringThreshold=1 -Xms20M -Xmx20M
 *      -XX:G1MixedGCLiveThresholdPercent=100 -XX:G1HeapWastePercent=0 -XX:G1HeapRegionSize=1m
 *      -XX:+UseG1GC -XX:+UseStringDeduplication
 *      -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      jdk.jfr.event.gc.collection.TestG1ParallelPhases
 */

public class TestG1ParallelPhases {
    public static List<WeakReference<byte[]>> weakRefs;

    public static void main(String[] args) throws IOException {
        Recording recording = new Recording();
        recording.enable(EventNames.GCPhaseParallel);
        recording.start();

        // create more weak garbage than can fit in this heap (-Xmx20m), will force collection of weak references
        weakRefs = range(1, 100)
            .mapToObj(n -> new WeakReference<>(new byte[1_000_000]))
            .collect(toList()); // force evaluation of lazy stream (all weak refs must be created)

        final var MEG = 1024 * 1024;
        provokeMixedGC(1 * MEG);
        recording.stop();

        Set<String> usedPhases = fromRecording(recording).stream()
            .map(e -> e.getValue("name").toString())
            .collect(toSet());

        Set<String> allPhases = of(
            "ExtRootScan",
            "ThreadRoots",
            "StringTableRoots",
            "UniverseRoots",
            "JNIRoots",
            "ObjectSynchronizerRoots",
            "ManagementRoots",
            "SystemDictionaryRoots",
            "CLDGRoots",
            "JVMTIRoots",
            "CMRefRoots",
            "WaitForStrongCLD",
            "WeakCLDRoots",
            "SATBFiltering",
            "UpdateRS",
            "ScanHCC",
            "ScanRS",
            "CodeRoots",
            "ObjCopy",
            "Termination",
            "StringDedupQueueFixup",
            "StringDedupTableFixup",
            "RedirtyCards",
       //     "PreserveCMReferents",
            "NonYoungFreeCSet",
            "YoungFreeCSet"
        );

        assertTrue(usedPhases.equals(allPhases), "Compare events expected and received"
            + ", Not found phases: " + allPhases.stream().filter(p -> !usedPhases.contains(p)).collect(joining(", "))
            + ", Not expected phases: " + usedPhases.stream().filter(p -> !allPhases.contains(p)).collect(joining(", ")));
    }
}

/**
 * Utility class to guarantee a mixed GC. The class allocates several arrays and
 * promotes them to the oldgen. After that it tries to provoke mixed GC by
 * allocating new objects.
 */
class Provoker {
    private static void allocateOldObjects(
            List<byte[]> liveOldObjects,
            int g1HeapRegionSize,
            int arraySize) {

        var toUnreachable = new ArrayList<byte[]>();

        // Allocates buffer and promotes it to the old gen. Mix live and dead old objects.
        // allocate about two regions of old memory. At least one full old region will guarantee
        // mixed collection in the future
        range(0, g1HeapRegionSize/arraySize).forEach(n -> {
            liveOldObjects.add(new byte[arraySize]);
            toUnreachable.add(new byte[arraySize]);
        });

        // Do two young collections, MaxTenuringThreshold=1 will force promotion.
        getWhiteBox().youngGC();
        getWhiteBox().youngGC();

        // Check it is promoted & keep alive
        Asserts.assertTrue(getWhiteBox().isObjectInOldGen(liveOldObjects), "List of the objects is suppose to be in OldGen");
        Asserts.assertTrue(getWhiteBox().isObjectInOldGen(toUnreachable), "List of the objects is suppose to be in OldGen");
    }

    private static void waitTillCMCFinished(int sleepTime) {
        while (getWhiteBox().g1InConcurrentMark()) {
              try {sleep(sleepTime);} catch (Exception e) {}
        }
    }

    /**
    * The necessary condition for guaranteed mixed GC is running in VM with the following flags:
    * -XX:+UnlockExperimentalVMOptions -XX:MaxTenuringThreshold=1 -Xms{HEAP_SIZE}M
    * -Xmx{HEAP_SIZE}M -XX:G1MixedGCLiveThresholdPercent=100 -XX:G1HeapWastePercent=0
    * -XX:G1HeapRegionSize={REGION_SIZE}m
    *
    * @param provokeSize The size to allocate to provoke the start of a mixed gc (half heap size?)
    * @param g1HeapRegionSize The size of your regions in bytes
    */
    public static void provokeMixedGC(int g1HeapRegionSize) {
        final var arraySize = 20_000;
        var liveOldObjects = new ArrayList<byte[]>();
        allocateOldObjects(liveOldObjects, g1HeapRegionSize, arraySize);
        waitTillCMCFinished(10);
        getWhiteBox().g1StartConcMarkCycle();
        waitTillCMCFinished(10);
        getWhiteBox().youngGC();
        getWhiteBox().youngGC();

        // check that liveOldObjects still alive
        assertTrue(getWhiteBox().isObjectInOldGen(liveOldObjects), "List of the objects is suppose to be in OldGen");
    }
}
