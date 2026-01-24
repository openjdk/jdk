/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jcmd;

import jdk.jfr.Enabled;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.jfr.EventNames;

import java.io.File;
import java.io.IOException;
import java.util.*;

// This test tests that even with a rather small stack size we can execute path-to-gc-roots search in
// leak profiler.
// (Note: VMThreadStackSize dictates stack size for *all* VM threads, not just the VMThread; therefore
//  we cannot go infinitly low)

/**
 * @test id=dfs-only
 * @summary Test dumping with path-to-gc-roots and DFS only, with a very small VMThread stack size
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 *
 * @run main/othervm -Xmx1g -XX:VMThreadStackSize=128k -Xlog:jfr+system+dfs jdk.jfr.jcmd.TestJcmdDumpPathToGCRootsBFSDFS dfs-only
 */

/**
 * @test id=bfsdfs
 * @summary Test dumping with path-to-gc-roots and mixed BFS+DFS, with a very small VMThread stack size
 * @bug 8373490
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 *
 * @run main/othervm -Xmx1g -XX:VMThreadStackSize=128k -Xlog:jfr+system+dfs jdk.jfr.jcmd.TestJcmdDumpPathToGCRootsBFSDFS bfsdfs
 */
public class TestJcmdDumpPathToGCRootsDFSWithSmallStack {

    // Note for BFS-DFS: in order to force the JVM to take the BFS-DFS path instead of just doing things BFS-only,
    // we start with a small heap of 256M. That gives us a (low-capped) BFS edge queue size of 32M. We then build up
    // a leak with > 2mio entries, which will exhaust the edge queue eventually and cause BFS to invoke the DFS fallback.

    // The minimum size of the edge queue in BFS (keep in sync with hotspot)
    // see edge_queue_memory_reservation() in pathToGCRootsOperation.cpp
    private final static int minimumEdgeQueueSizeCap = 32 * 1024 * 1024;

    private static List<Object[]> leak;
    private final static int leakedObjectCount = 5_000_000;

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        String settingName = EventNames.OldObjectSample + "#" + "cutoff";

        boolean skipBFS = switch (args[0]) {
            case "bfsdfs" -> false;
            case "dfs-only" -> true;
            default -> {
                throw new RuntimeException("Invalid argument");
            }
        };

        WhiteBox.setSkipBFS(skipBFS);
        testDump(Collections.singletonMap(settingName, "infinity"));
    }

    private static void testDump(Map<String, String> settings) throws Exception {
        final String pathToGcRoots = "path-to-gc-roots=true";
        int numTries = 3;
        while (--numTries >= 0) {
            try (Recording r = new Recording()) {
                Map<String, String> p = new HashMap<>(settings);
                p.put(EventNames.OldObjectSample + "#" + Enabled.NAME, "true");
                r.setName("dodo");
                r.setSettings(p);
                r.setToDisk(true);
                r.start();
                clearLeak();
                System.out.println("Recording id: " + r.getId());
                System.out.println("Settings: " + settings.toString());
                System.out.println("Command: JFR.dump " + pathToGcRoots);
                buildLeak(leakedObjectCount);
                System.gc();
                System.gc();
                File recording = new File("TestJcmdDumpPathToGCRoots" + r.getId() + ".jfr");
                recording.delete();
                JcmdHelper.jcmd("JFR.dump", "name=dodo", pathToGcRoots, "filename=" + recording.getAbsolutePath());
                r.setSettings(Collections.emptyMap());
                List<RecordedEvent> events = RecordingFile.readAllEvents(recording.toPath());
                if (events.isEmpty()) {
                    System.out.println("No events found in recording. Retrying.");
                    continue;
                }
                int chains = countChains(events);
                final int minNumberOfChains = 20; // very conservative; normally 130-160
                if (chains < minNumberOfChains) {
                    System.out.println(events);
                    System.out.println("Not enough chains found (" + chains + "), retrying.");
                    continue;
                }
                return; // Success
            }
        }
        throw new RuntimeException("Failed");
    }

    private static void clearLeak() {
      leak = null;
      System.gc();
    }

    private static int countChains(List<RecordedEvent> events) throws IOException {
        int found = 0;
        for (RecordedEvent e : events) {
            RecordedObject ro = e.getValue("object");
            if (ro.getValue("referrer") != null) {
                found++;
            }
        }
        System.out.println("Found chains: " + found);
        return found;
    }

    private static void buildLeak(int objectCount) {
        leak = new ArrayList<Object[]>(objectCount);
        for (int i = 0; i < objectCount;i ++) {
            leak.add(new Object[0]);
        }
    }
}
