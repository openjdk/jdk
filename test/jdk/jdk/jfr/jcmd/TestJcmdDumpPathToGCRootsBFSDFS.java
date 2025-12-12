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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.Enabled;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.jfr.EventNames;

/**
 * @test id=dfs-only
 * @summary Test dumping with path-to-gc-roots and DFS only
 * @bug 8373490
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 *
 * @run main/othervm -XX:TLABSize=2k -Xmx256m jdk.jfr.jcmd.TestJcmdDumpPathToGCRootsBFSDFS dfs-only
 */

/**
 * @test id=bfs-only
 * @summary Test dumping with path-to-gc-roots and BFS only
 * @bug 8373490
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 *
 * @run main/othervm -XX:TLABSize=2k -Xmx256m jdk.jfr.jcmd.TestJcmdDumpPathToGCRootsBFSDFS bfs-only
 */

/**
 * @test id=bfsdfs
 * @summary Test dumping with path-to-gc-roots and mixed BFS+DFS
 * @bug 8373490
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 *
 * @run main/othervm -XX:TLABSize=2k -Xmx256m jdk.jfr.jcmd.TestJcmdDumpPathToGCRootsBFSDFS bfsdfs
 */
public class TestJcmdDumpPathToGCRootsBFSDFS {

    // Note:
    // - We start with a small heap of 256M in order to get the minimum Edge Queue size in BFS (lower cap is 32MB, enough to hold ~2mio edges)
    // - We build a leak with an array containing more than 2mio entries
    // That will hit BFS first, then fall back to DFS, showing the performance problem JDK-8373490 describes.
    // DFS-only mode should work well, and so should BFS-only mode.

    // The minimum size of the edge queue in BFS (keep in sync with hotspot)
    private final static int minimumEdgeQueueSizeCap = 32 * 1024 * 1024;
    // The size of the Edge structure (keep in sync with hotspot)
    private final static int edgeSizeBytes = 16;

    public static List<Object[]> leak;

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        String settingName = EventNames.OldObjectSample + "#" + "cutoff";

        int edgesPerMinSizedQueue = minimumEdgeQueueSizeCap / 16;
        int lower = 1_000_000;
        int upper = 3_000_000;
        int fudge = 250_000;
        if (edgesPerMinSizedQueue < (lower + fudge)) {
            throw new RuntimeException("edgesPerMinSizedQueue lower bound wrong?");
        }
        if (edgesPerMinSizedQueue > (upper - fudge)) {
            throw new RuntimeException("edgesPerMinSizedQueue upper bound wrong?");
        }

        int leakedObjectCount;
        boolean skipBFS;
        switch (args[0]) {
            case "bfsdfs" -> {
                // Mixed mode: enough objects to saturate BFS queue
                leakedObjectCount = upper;
                skipBFS = false;
            }
            case "dfs-only" -> {
                // DFS-only mode: object count does not matter, we enter DFS right away
                leakedObjectCount = upper;
                skipBFS = true;
            }
            case "bfs-only" -> {
                // BFS-only mode: not enough objects to saturate BFS queue
                leakedObjectCount = lower;
                skipBFS = false;
            }
            default -> {
                throw new RuntimeException("Invalid argument");
            }
        };

        WhiteBox.setSkipBFS(skipBFS);

        testDump("path-to-gc-roots=true", Collections.singletonMap(settingName, "infinity"), leakedObjectCount, true);
    }

    private static void testDump(String pathToGcRoots, Map<String, String> settings, int leakedObjectCount, boolean expectedChains) throws Exception {
        while (true) {
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
                System.out.println("Chains expected: " + expectedChains);
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
                boolean chains = hasChains(events);
                if (expectedChains && !chains) {
                    System.out.println(events);
                    System.out.println("Expected chains but found none. Retrying.");
                    continue;
                }
                if (!expectedChains && chains) {
                    System.out.println(events);
                    System.out.println("Didn't expect chains but found some. Retrying.");
                    continue;
                }
                return; // Success
            }
        }
    }

    private static void clearLeak() {
      leak = null;
      System.gc();
    }

    private static boolean hasChains(List<RecordedEvent> events) throws IOException {
        for (RecordedEvent e : events) {
            RecordedObject ro = e.getValue("object");
            if (ro.getValue("referrer") != null) {
                return true;
            }
        }
        return false;
    }

    private static void buildLeak(int objectCount) {
        leak = new ArrayList<Object[]>(objectCount);
        for (int i = 0; i < objectCount;i ++) {
            leak.add(new Object[0]);
        }
    }
}
