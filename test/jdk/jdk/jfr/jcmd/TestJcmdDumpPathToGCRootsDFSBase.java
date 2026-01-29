/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

public abstract class TestJcmdDumpPathToGCRootsDFSBase {

    protected abstract void buildLeak();
    protected abstract void clearLeak();

    protected void testDump(String jfrFileName, int minChainsExpected) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        WhiteBox.setSkipBFS(true);
        final String pathToGcRoots = "path-to-gc-roots=true";
        int numTries = 10;
        while (--numTries >= 0) {
            try (Recording r = new Recording()) {
                r.setName("dodo");
                r.enable(EventNames.OldObjectSample);
                r.setToDisk(true);
                r.start();
                clearLeak();
                System.out.println("Recording id: " + r.getId());
                System.out.println("Command: JFR.dump " + pathToGcRoots);
                buildLeak();
                System.gc();
                System.gc();
                File recording = new File(jfrFileName + r.getId() + ".jfr");
                recording.delete();
                JcmdHelper.jcmd("JFR.dump", "name=dodo", pathToGcRoots, "filename=" + recording.getAbsolutePath());
                List<RecordedEvent> events = RecordingFile.readAllEvents(recording.toPath());
                if (events.isEmpty()) {
                    System.out.println("No events found in recording. Retrying.");
                    continue;
                }
                int chains = countChains(events);
                if (chains < minChainsExpected) {
                    System.out.println(events);
                    System.out.println("Not enough chains found (" + chains + "), retrying.");
                    continue;
                }
                return; // Success
            }
        }
        throw new RuntimeException("Failed");
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

}
