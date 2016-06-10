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
 * @test
 * @summary Test commits of overlapping regions of memory.
 * @key nmt jcmd
 * @library /testlibrary /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build   CommitOverlappingRegions
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail CommitOverlappingRegions
 */

import jdk.test.lib.*;
import sun.hotspot.WhiteBox;

public class CommitOverlappingRegions {
    public static WhiteBox wb = WhiteBox.getWhiteBox();
    public static void main(String args[]) throws Exception {
        OutputAnalyzer output;
        long size = 32 * 1024;
        long addr = wb.NMTReserveMemory(8*size);

        String pid = Long.toString(ProcessTools.getProcessId());
        ProcessBuilder pb = new ProcessBuilder();

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "detail"});
        System.out.println("Address is " + Long.toHexString(addr));

        // Start: . . . . . . . .
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=0KB)");

        // Committing: * * * . . . . .
        // Region:     * * * . . . . .
        // Expected Total: 3 x 32KB = 96KB
        wb.NMTCommitMemory(addr + 0*size, 3*size);

        // Committing: . . . . * * * .
        // Region:     * * * . * * * .
        // Expected Total: 6 x 32KB = 192KB
        wb.NMTCommitMemory(addr + 4*size, 3*size);

        // Check output after first 2 commits.
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=192KB)");

        // Committing: . . * * * . . .
        // Region:     * * * * * * * .
        // Expected Total: 7 x 32KB = 224KB
        wb.NMTCommitMemory(addr + 2*size, 3*size);

        // Check output after overlapping commit.
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=224KB)");

        // Uncommitting: * * * * * * * *
        // Region:       . . . . . . . .
        // Expected Total: 0 x 32KB = 0KB
        wb.NMTUncommitMemory(addr + 0*size, 8*size);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=0KB)");

        // Committing: * * . . . . . .
        // Region:     * * . . . . . .
        // Expected Total: 2 x 32KB = 64KB
        wb.NMTCommitMemory(addr + 0*size, 2*size);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=64KB)");

        // Committing: . * * * . . . .
        // Region:     * * * * . . . .
        // Expected Total: 4 x 32KB = 128KB
        wb.NMTCommitMemory(addr + 1*size, 3*size);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=128KB)");

        // Uncommitting: * * * . . . . .
        // Region:       . . . * . . . .
        // Expected Total: 1 x 32KB = 32KB
        wb.NMTUncommitMemory(addr + 0*size, 3*size);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=32KB)");

        // Committing: . . . * * . . .
        // Region:     . . . * * . . .
        // Expected Total: 2 x 32KB = 64KB
        wb.NMTCommitMemory(addr + 3*size, 2*size);
        System.out.println("Address is " + Long.toHexString(addr + 3*size));
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=64KB)");

        // Committing: . . . . * * . .
        // Region:     . . . * * * . .
        // Expected Total: 3 x 32KB = 96KB
        wb.NMTCommitMemory(addr + 4*size, 2*size);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=96KB)");

        // Committing: . . . . . * * .
        // Region:     . . . * * * * .
        // Expected Total: 4 x 32KB = 128KB
        wb.NMTCommitMemory(addr + 5*size, 2*size);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=128KB)");

        // Committing: . . . . . . * *
        // Region:     . . . * * * * *
        // Expected Total: 5 x 32KB = 160KB
        wb.NMTCommitMemory(addr + 6*size, 2*size);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=160KB)");

        // Uncommitting: * * * * * * * *
        // Region:       . . . . . . . .
        // Expected Total: 0 x 32KB = 32KB
        wb.NMTUncommitMemory(addr + 0*size, 8*size);
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Test (reserved=256KB, committed=0KB)");
    }
}
