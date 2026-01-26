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
import java.util.*;

import jdk.jfr.Enabled;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.jfr.EventNames;

/**
 * @test
 * @summary Test dumping with path-to-gc-roots and DFS only with a very small stacksize for the VM thread
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 *
 * @run main/othervm -Xmx1g -XX:VMThreadStackSize=128 jdk.jfr.jcmd.TestJcmdDumpPathToGCRootsDFSWithSmallStack dfs-only
 */
public class TestJcmdDumpPathToGCRootsDFSWithSmallStack {

    // Tests the new non-recursive implementation of the JFR leak profiler path-to-gc-roots-search.

    // We build up an array of linked lists, each containing enough entries for DFS search to
    // max out max_dfs_depth (but not greatly surpass it).
    // The old recursive implementation, started with such a small stack, will fail to reach
    // max_dfs_depth, instead crashing with stack overflow.
    // The new non-recursive implementation should work; the majority of the linked list items
    // should be scanned (all those at the start of the lists, below max_dfs_depth) and contribute
    // old object samples to the result.

    // Note: VMThreadStackSize defines thread stack size for *all* VM-internal threads, not just the
    // VM thread. Make sure that whatever small size we use in this test is still fine for the rest of
    // the VM.

    private static final int TOTAL_OBJECTS = 10_000_000;
    private static final int OBJECTS_PER_LIST = 5_000;
    private static LinkedList[] leak;

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        String settingName = EventNames.OldObjectSample + "#" + "cutoff";

        int leakedObjectCount = 10_000_000;
        boolean skipBFS = true;

        WhiteBox.setSkipBFS(skipBFS);
        testDump(Collections.singletonMap(settingName, "infinity"), leakedObjectCount);
    }

    private static void testDump(Map<String, String> settings, int leakedObjectCount) throws Exception {
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
                File recording = new File("TestJcmdDumpPathToGCRootsDFSWithSmallStack" + r.getId() + ".jfr");
                recording.delete();
                JcmdHelper.jcmd("JFR.dump", "name=dodo", pathToGcRoots, "filename=" + recording.getAbsolutePath());
                r.setSettings(Collections.emptyMap());
                List<RecordedEvent> events = RecordingFile.readAllEvents(recording.toPath());
                if (events.isEmpty()) {
                    System.out.println("No events found in recording. Retrying.");
                    continue;
                }
                int chains = countChains(events);
                final int minNumberOfChains = 30; // very conservative; normally ~250
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
        leak = new LinkedList[TOTAL_OBJECTS/OBJECTS_PER_LIST];
        for (int i = 0; i < leak.length; i++) {
            leak[i] = new LinkedList();
            for (int j = 0; j < OBJECTS_PER_LIST; j++) {
                leak[i].add(new Object());
            }
        }
    }
}
