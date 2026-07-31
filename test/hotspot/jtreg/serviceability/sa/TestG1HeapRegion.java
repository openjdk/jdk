/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.gc.g1.G1CollectedHeap;
import sun.jvm.hotspot.gc.g1.G1HeapRegion;
import sun.jvm.hotspot.HotSpotAgent;
import sun.jvm.hotspot.runtime.VM;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8194249
 * @library /test/lib
 * @requires vm.hasSA
 * @requires (os.arch != "riscv64" | !(vm.cpu.features ~= ".*qemu.*"))
 * @requires vm.gc.G1
 * @modules jdk.hotspot.agent/sun.jvm.hotspot
 *          jdk.hotspot.agent/sun.jvm.hotspot.debugger
 *          jdk.hotspot.agent/sun.jvm.hotspot.gc.g1
 *          jdk.hotspot.agent/sun.jvm.hotspot.memory
 *          jdk.hotspot.agent/sun.jvm.hotspot.runtime
 * @run driver TestG1HeapRegion
 */

public class TestG1HeapRegion {

    private static LingeredApp theApp = null;

    private static void checkHeapRegion(String pid) throws Exception {
        HotSpotAgent agent = new HotSpotAgent();

        try {
            agent.attach(Integer.parseInt(pid));

            G1CollectedHeap heap = (G1CollectedHeap)VM.getVM().getUniverse().heap();
            heap.printOn(System.out);

            // Print each region first.
            System.out.println();
            Iterator<G1HeapRegion> hri  = heap.hrm().heapRegionIterator();
            G1HeapRegion hr = hri.next();
            while (hr != null) {
                hr.printOn(System.out);
                hr = hri.next();
            }
            System.out.println();

            // Iterate over each region and confirm that getByAddress(top) returns
            // the same address as the region being looked at.
            hri  = heap.hrm().heapRegionIterator();
            hr = hri.next();
            while (hr != null) {
                hr.printOn(System.out);
                Address top = hr.top();
                if (top.equals(hr.end())) {
                    // The end of the region is actually the first address after
                    // the end, so it points to the start of the next region. We need to
                    // subtract to avoid getByAddress(top) returning the next region.
                    top = top.addOffsetTo(-1);
                }
                G1HeapRegion hrTop = heap.hrm().getByAddress(top);
                System.out.format("hr.top():0x%x <--> hrTop.top():0x%x\n",
                                  hr.top().asLongValue(), hrTop.top().asLongValue());
                Asserts.assertEquals(hr.top(), hrTop.top(),
                                     "Address of G1HeapRegion does not match.");
                hr = hri.next();
            }
        } finally {
            agent.detach();
        }
    }

    private static void createAnotherToAttach(long lingeredAppPid)
                                                         throws Exception {
        // Start a new process to attach to the lingered app
        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
            "--add-modules=jdk.hotspot.agent",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.gc.g1=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.memory=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.runtime=ALL-UNNAMED",
            "TestG1HeapRegion",
            Long.toString(lingeredAppPid));
        SATestUtils.addPrivilegesIfNeeded(processBuilder);
        OutputAnalyzer SAOutput = ProcessTools.executeProcess(processBuilder);
        SAOutput.shouldHaveExitValue(0);
        System.out.println(SAOutput.getOutput());
    }

    public static void main (String... args) throws Exception {
        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.
        if (args == null || args.length == 0) {
            try {
                theApp = new LingeredApp();
                LingeredApp.startApp(theApp, "-XX:+UsePerfData", "-XX:+UseG1GC");
                createAnotherToAttach(theApp.getPid());
            } finally {
                LingeredApp.stopApp(theApp);
            }
        } else {
            checkHeapRegion(args[0]);
        }
    }
}
