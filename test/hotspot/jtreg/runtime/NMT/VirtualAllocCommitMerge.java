/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test merging of committed virtual memory and that we track it correctly
 * @comment needs to be executed with -Xint (or, alternatively, -Xcomp -Xbatch) since it relies on comparing
 *          NMT call stacks, and we must make sure that all functions on the stack that NMT sees are either compiled
 *          from the get-go or stay always interpreted.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xint -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail VirtualAllocCommitMerge
 *
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;

import jdk.test.whitebox.WhiteBox;

public class VirtualAllocCommitMerge {

    public static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String args[]) throws Exception {
        OutputAnalyzer output;
        long commitSize = 128 * 1024; // 128KB
        long reserveSize = 4 * 1024 * 1024; // 4096KB
        long addr;

        // reserve
        addr = wb.NMTReserveMemory(reserveSize);
        output = NMTTestUtils.startJcmdVMNativeMemory("detail");
        checkReservedCommittedSummary(output, 4096, 0);
        checkReserved(output, addr, reserveSize, "4096KB");

        long addrA = addr + (0 * commitSize);
        long addrB = addr + (1 * commitSize);
        long addrC = addr + (2 * commitSize);
        long addrD = addr + (3 * commitSize);
        long addrE = addr + (4 * commitSize);

        {
            // commit overlapping ABC, A, B, C
            wb.NMTCommitMemory(addrA, 3 * commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");


            wb.NMTCommitMemory(addrA, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");


            wb.NMTCommitMemory(addrB, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");

            wb.NMTCommitMemory(addrC, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");

            // uncommit
            wb.NMTUncommitMemory(addrA, 3 * commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        // Test discontigous areas
        {
            // commit ACE
            wb.NMTCommitMemory(addrA, commitSize);
            wb.NMTCommitMemory(addrC, commitSize);
            wb.NMTCommitMemory(addrE, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, commitSize, "128KB");
            checkCommitted(output, addrC, commitSize, "128KB");
            checkCommitted(output, addrE, commitSize, "128KB");

            // uncommit ACE
            wb.NMTUncommitMemory(addrA, commitSize);
            wb.NMTUncommitMemory(addrC, commitSize);
            wb.NMTUncommitMemory(addrE, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        // Test contiguous areas
        {
            // commit AB
            wb.NMTCommitMemory(addrA, commitSize);
            wb.NMTCommitMemory(addrB, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 256);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 2 * commitSize, "256KB");

            // uncommit AB
            wb.NMTUncommitMemory(addrA, commitSize);
            wb.NMTUncommitMemory(addrB, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        {
            // commit BA
            wb.NMTCommitMemory(addrB, commitSize);
            wb.NMTCommitMemory(addrA, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 256);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 2 * commitSize, "256KB");

            // uncommit AB
            wb.NMTUncommitMemory(addrB, commitSize);
            wb.NMTUncommitMemory(addrA, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        {
            // commit ABC
            wb.NMTCommitMemory(addrA, commitSize);
            wb.NMTCommitMemory(addrB, commitSize);
            wb.NMTCommitMemory(addrC, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");

            // uncommit
            wb.NMTUncommitMemory(addrA, commitSize);
            wb.NMTUncommitMemory(addrB, commitSize);
            wb.NMTUncommitMemory(addrC, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        {
            // commit ACB
            wb.NMTCommitMemory(addrA, commitSize);
            wb.NMTCommitMemory(addrC, commitSize);
            wb.NMTCommitMemory(addrB, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");

            // uncommit
            wb.NMTUncommitMemory(addrA, commitSize);
            wb.NMTUncommitMemory(addrC, commitSize);
            wb.NMTUncommitMemory(addrB, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        {
            // commit BAC
            wb.NMTCommitMemory(addrB, commitSize);
            wb.NMTCommitMemory(addrA, commitSize);
            wb.NMTCommitMemory(addrC, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");

            // uncommit
            wb.NMTUncommitMemory(addrB, commitSize);
            wb.NMTUncommitMemory(addrA, commitSize);
            wb.NMTUncommitMemory(addrC, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        {
            // commit BCA
            wb.NMTCommitMemory(addrB, commitSize);
            wb.NMTCommitMemory(addrC, commitSize);
            wb.NMTCommitMemory(addrA, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");

            // uncommit
            wb.NMTUncommitMemory(addrB, commitSize);
            wb.NMTUncommitMemory(addrC, commitSize);
            wb.NMTUncommitMemory(addrA, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        {
            // commit CAB
            wb.NMTCommitMemory(addrC, commitSize);
            wb.NMTCommitMemory(addrA, commitSize);
            wb.NMTCommitMemory(addrB, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");

            // uncommit
            wb.NMTUncommitMemory(addrC, commitSize);
            wb.NMTUncommitMemory(addrA, commitSize);
            wb.NMTUncommitMemory(addrB, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

        {
            // commit CBA
            wb.NMTCommitMemory(addrC, commitSize);
            wb.NMTCommitMemory(addrB, commitSize);
            wb.NMTCommitMemory(addrA, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 384);
            checkReserved(output, addr, reserveSize, "4096KB");

            checkCommitted(output, addrA, 3 * commitSize, "384KB");

            // uncommit
            wb.NMTUncommitMemory(addrC, commitSize);
            wb.NMTUncommitMemory(addrB, commitSize);
            wb.NMTUncommitMemory(addrA, commitSize);

            output = NMTTestUtils.startJcmdVMNativeMemoryDetail();
            checkReservedCommittedSummary(output, 4096, 0);
        }

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

    public static void checkReserved(OutputAnalyzer output, long addr, long size, String sizeString) {
        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                           + Long.toHexString(addr + size)
                           + "\\] reserved 4096KB for Test");
    }

    public static void checkCommitted(OutputAnalyzer output, long addr, long size, String sizeString) {
        // On ARM Thumb the stack is not walkable, so the location is not available and
        // "from" string will not be present in the output.
        // Disable assertion for ARM32.
        String fromString = Platform.isARM() ? "" : "from.*";
        output.shouldMatch("\\[0x[0]*" + Long.toHexString(addr) + " - 0x[0]*"
                           + Long.toHexString(addr + size)
                           + "\\] committed " + sizeString + " " + fromString);
    }
}
