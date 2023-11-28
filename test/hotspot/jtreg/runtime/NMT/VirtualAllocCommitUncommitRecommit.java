/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test reserve/commit/uncommit/release of virtual memory and that we track it correctly
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail VirtualAllocCommitUncommitRecommit
 *
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

public class VirtualAllocCommitUncommitRecommit {

    public static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String args[]) throws Exception {
        OutputAnalyzer output;
        long commitSize = 128 * 1024; // 128KB
        long reserveSize = 4 * 1024 * 1024; // 4096KB
        long addr;

        // reserve
        addr = wb.NMTReserveMemory(reserveSize);
        output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
        checkReservedCommittedSummary(output, 4096, 0);
        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                           + Long.toHexString(addr + reserveSize)
                           + "\\] reserved 4096KB for Test");

        long addrA = addr;
        long addrB = addr + commitSize;
        long addrC = addr + (2 * commitSize);
        long addrD = addr + (3 * commitSize);
        long addrE = addr + (4 * commitSize);
        long addrF = addr + (5 * commitSize);

        // commit ABCD
        wb.NMTCommitMemory(addrA, commitSize);
        wb.NMTCommitMemory(addrB, commitSize);
        wb.NMTCommitMemory(addrC, commitSize);
        wb.NMTCommitMemory(addrD, commitSize);

        output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
        checkReservedCommittedSummary(output, 4096, 512);

        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                           + Long.toHexString(addr + reserveSize)
                           + "\\] reserved 4096KB for Test");
        // uncommit BC
        wb.NMTUncommitMemory(addrB, commitSize);
        wb.NMTUncommitMemory(addrC, commitSize);

        output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
        checkReservedCommittedSummary(output, 4096, 256);
        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                            + Long.toHexString(addr + reserveSize)
                            + "\\] reserved 4096KB for Test");

        // commit EF
        wb.NMTCommitMemory(addrE, commitSize);
        wb.NMTCommitMemory(addrF, commitSize);

        output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
        checkReservedCommittedSummary(output, 4096, 512);
        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                           + Long.toHexString(addr + reserveSize)
                           + "\\] reserved 4096KB for Test");

        // uncommit A
        wb.NMTUncommitMemory(addrA, commitSize);

        output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
        checkReservedCommittedSummary(output, 4096, 384);
        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                           + Long.toHexString(addr + reserveSize)
                           + "\\] reserved 4096KB for Test");

        // commit ABC
        wb.NMTCommitMemory(addrA, commitSize);
        wb.NMTCommitMemory(addrB, commitSize);
        wb.NMTCommitMemory(addrC, commitSize);

        output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
        checkReservedCommittedSummary(output, 4096, 768);
        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                           + Long.toHexString(addr + reserveSize)
                           + "\\] reserved 4096KB for Test");

        // uncommit ABCDEF
        wb.NMTUncommitMemory(addrA, commitSize);
        wb.NMTUncommitMemory(addrB, commitSize);
        wb.NMTUncommitMemory(addrC, commitSize);
        wb.NMTUncommitMemory(addrD, commitSize);
        wb.NMTUncommitMemory(addrE, commitSize);
        wb.NMTUncommitMemory(addrF, commitSize);

        output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
        checkReservedCommittedSummary(output, 4096, 0);
        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                           + Long.toHexString(addr + reserveSize)
                           + "\\] reserved 4096KB for Test");

        // release
        wb.NMTReleaseMemory(addr, reserveSize);
        output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
        checkReservedCommittedSummary(output, 0, 0);
        output.shouldNotMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                + Long.toHexString(addr + reserveSize) + "\\] reserved 4096KB for Test");
    }

    // running peak counter
    static long peakKB = 0;

    public static void checkReservedCommittedSummary(OutputAnalyzer output, long reservedKB, long committedKB) {
        if (committedKB > peakKB) {
            peakKB = committedKB;
        }
        NMTTestUtils.checkReservedCommittedSummary(output, reservedKB, committedKB, peakKB);
    }
}
