/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import java.util.Map;

import jdk.jfr.Enabled;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.jfr.EventNames;

/**
 * @test id=path-to-gc-roots-true-cutoff-inf
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx256M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots true infinity true
 */
/**
 * @test id=path-to-gc-roots-true-cutoff-0
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx256M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots true 0 true
 */

/**
 * @test
 * @test id=path-to-gc-roots-true-cutoff-default
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx256M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots true - true
 */

/**
 * @test id=path-to-gc-roots-false-cutoff-inf
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx256M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots false infinity false
 */

/**
 * @test id=path-to-gc-roots-false-cutoff-0
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx256M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots false 0 false
 */

/**
 * @test id=path-to-gc-roots-false-cutoff-default
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx256M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots false - false
 */

/**
 * @test id=path-to-gc-roots-default-cutoff-inf
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx512M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots - infinity true
 */

/**
 * @test id=path-to-gc-roots-default-cutoff-0
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx512M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots - 0 false
 */

/**
 * @test id=path-to-gc-roots-default-cutoff-default
 * @requires vm.hasJFR & vm.flagless
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:TLABSize=2k -Xmx512M -XX:ErrorLogTimeout=1 jdk.jfr.jcmd.TestJcmdDumpPathToGCRoots - - false
 */
public class TestJcmdDumpPathToGCRoots {

    // Comfortably large enough to saturate the 32M EdgeQueue in PathToGcRootsOperation
    // and thus exercise both BFS and DFS paths
    private static final int OBJECT_COUNT = 2_500_000;
    public static List<Object> leak = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);

        if (args.length != 3) {
            throw new RuntimeException("Expected 2 arguments");
        }

        String settingName = EventNames.OldObjectSample + "#" + "cutoff";

        final String ptgcr =
            switch (args[0]) {
                case "-" -> "";
                default -> "path-to-gc-roots=" + args[0];
            };

        final Map settings = switch (args[1]) {
            case "infinity" -> Collections.singletonMap(settingName, "infinity");
            case "0" -> Collections.singletonMap(settingName, "0 ns");
            case "-" -> Collections.emptyMap();
            default -> throw new RuntimeException("Invalid " + args[1]);
        };

        final boolean expectChains = Boolean.valueOf(args[2]);

        // dump parameter trumps previous setting
        testDump(ptgcr, settings, expectChains);

    }

    private static void testDump(String pathToGcRoots, Map<String, String> settings, boolean expectedChains) throws Exception {
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
                buildLeak();
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
      leak.clear();
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

    private static void buildLeak() {
        int chainlen = 10000;
        for (int i = 0; i < OBJECT_COUNT/chainlen; i ++) {
            LinkedList<Object> l = new LinkedList();
            for (int j = 0; j < chainlen; j ++) {
                l.add(new Object());
            }
            leak.add(l);
        }
    }
}
