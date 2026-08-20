/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8261848
 * @summary Test that clhsdb 'revptrs' finds references from a virtual thread's stack chunk
 * @requires vm.hasSA
 * @requires vm.gc.Serial
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm/timeout=1200 ClhsdbRevPtrsForVirtualThread
 */

import java.util.List;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

public class ClhsdbRevPtrsForVirtualThread {

    private static final String TARGET_TYPE =
        "LingeredAppWithUnmountedVirtualThread$ChunkReferenced";

    public static void main(String[] args) throws Exception {
        LingeredApp theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();

            theApp = new LingeredAppWithUnmountedVirtualThread();
            LingeredApp.startApp(theApp, "-XX:+UseSerialGC", "-XX:InitialHeapSize=100M");
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            String universeOutput = test.run(theApp.getPid(), List.of("universe"), null, null);

            // The instance was created before the System.gc() in the app, so
            // look for it in the old gen first and fall back to eden.
            String addr = scan(test, theApp, universeOutput, "old  \\[");
            if (addr == null) {
                addr = scan(test, theApp, universeOutput, "eden \\[");
            }
            if (addr == null) {
                throw new RuntimeException("No " + TARGET_TYPE + " instance found in the heap");
            }

            String output = test.run(theApp.getPid(), List.of("revptrs " + addr), null, null);
            OutputAnalyzer out = new OutputAnalyzer(output);
            out.shouldContain("StackChunk");
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    private static String scan(ClhsdbLauncher test, LingeredApp theApp,
                               String universeOutput, String regionMarker) throws Exception {
        String[] snippets = universeOutput.split(regionMarker);
        if (snippets.length < 2) {
            return null;
        }
        String[] words = snippets[1].split(",");
        String start = words[0].replace("[", "");
        String end = words[1];
        String cmd = "scanoops " + start + " " + end;
        String output = test.run(theApp.getPid(), List.of(cmd), null, null);
        for (String line : output.split("\\R")) {
            if (line.contains(TARGET_TYPE)) {
                return line.trim().split("\\s+")[0];
            }
        }
        return null;
    }
}
