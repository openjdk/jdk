/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
 * @summary Verify that SA continuation entry frame pointers account for native size alignment
 * @requires vm.hasSA
 * @requires vm.gc != "Z"
 * @requires (os.arch == "amd64") | (os.arch == "x86_64") | (os.arch == "aarch64") | (os.arch == "riscv64")
 * @requires (os.arch != "riscv64" | !(vm.cpu.features ~= ".*qemu.*"))
 * @library /test/lib
 * @modules jdk.hotspot.agent/sun.jvm.hotspot
 *          jdk.hotspot.agent/sun.jvm.hotspot.debugger
 *          jdk.hotspot.agent/sun.jvm.hotspot.runtime
 *          jdk.hotspot.agent/sun.jvm.hotspot.types
 * @run driver TestContinuationEntry
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import sun.jvm.hotspot.HotSpotAgent;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.ContinuationEntry;
import sun.jvm.hotspot.runtime.VM;

public class TestContinuationEntry {
    private static void checkEntry(int pid) {
        HotSpotAgent agent = new HotSpotAgent();
        agent.attach(pid);
        try {
            long rawSize = agent.getTypeDataBase().lookupType("ContinuationEntry").getSize();
            long wordSize = VM.getVM().getAddressSize();
            long alignment = 2 * wordSize;
            long alignedSize = ((rawSize + alignment - 1) / alignment) * alignment;
            String cpu = agent.getDebugger().getCPU();
            // RISC-V's entry FP is above the saved caller FP and return address (two words).
            // See ContinuationEntry::entry_fp() in continuationEntry_riscv.inline.hpp and
            // RISCV64ContinuationEntry.getEntryFP() in SA.
            long expectedOffset = alignedSize + (cpu.equals("riscv64") ? 2 * wordSize : 0);

            // SP and FP accessors only do address arithmetic; they do not dereference this synthetic entry.
            Address base = agent.getDebugger().newAddress(0x10000);
            ContinuationEntry entry = ContinuationEntry.create(base);
            Asserts.assertEquals(base, entry.getEntrySP(), "Continuation entry SP");
            long actualOffset = entry.getEntryFP().minus(entry.getEntrySP());
            System.out.printf("cpu=%s rawSize=%d wordSize=%d expectedOffset=%d actualOffset=%d%n",
                              cpu, rawSize, wordSize, expectedOffset, actualOffset);
            Asserts.assertEquals(expectedOffset, actualOffset, "Continuation entry FP offset");
        } finally {
            agent.detach();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            checkEntry(Integer.parseInt(args[0]));
            return;
        }

        SATestUtils.skipIfCannotAttach();
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp();
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "--add-modules=jdk.hotspot.agent",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.runtime=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.types=ALL-UNNAMED",
                TestContinuationEntry.class.getName(), Long.toString(app.getPid()));
            SATestUtils.addPrivilegesIfNeeded(pb);
            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            System.out.println(output.getOutput());
            output.shouldHaveExitValue(0);
        } finally {
            LingeredApp.stopApp(app);
        }
    }
}
