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
 * @library /testlibrary /../../test/lib
 * @modules java.base/sun.misc
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
        long addr;

        String pid = Integer.toString(ProcessTools.getProcessId());
        ProcessBuilder pb = new ProcessBuilder();

        addr = wb.NMTReserveMemory(8*size);        // [                ]
        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "detail"});
        // Test output before commits
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("(reserved=256KB, committed=0KB)");

        // Commit regions 1 and 2, then test output.
        wb.NMTCommitMemory(addr + 0*size, 3*size); // [       ]
        wb.NMTCommitMemory(addr + 4*size, 3*size); //           [      ]

        output = new OutputAnalyzer(pb.start());
        output.shouldContain("(reserved=256KB, committed=192KB)");

        // Commit the final region which overlaps partially with both regions.
        wb.NMTCommitMemory(addr + 2*size, 3*size); //     [        ]

        output = new OutputAnalyzer(pb.start());
        output.getOutput();
        output.shouldContain("(reserved=256KB, committed=224KB)");
    }
}
