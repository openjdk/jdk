/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.IOException;

import jdk.test.whitebox.WhiteBox;



/*
 * @test id=testSuccessfulFlow
 * @summary Test that memory allocation logging works when allocation operations run without error
 * @library /test/lib
 * @requires os.family != "windows" & os.family != "aix"
 * @requires vm.flagless
 * @requires vm.bits == "64"
 * @requires vm.debug == false
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testSuccessfulFlow
 */

/*
 * @test id=testAttemptedReserveFailed
 * @summary Test that memory allocation logging warns when attempted reservation fails
 * @library /test/lib
 * @requires os.family != "windows" & os.family != "aix"
 * @requires vm.flagless
 * @requires vm.bits == "64"
 * @requires vm.debug == false
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testAttemptedReserveFailed
 */

/*
 * @test id=testCommitFailed
 * @summary Test that memory allocation logging warns when commit attempts fail
 * @library /test/lib
 * @requires os.family != "windows" & os.family != "aix"
 * @requires vm.flagless
 * @requires vm.bits == "64"
 * @requires vm.debug == false
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testCommitFailed
 */

/*
 * @test id=testUncommitFailed
 * @summary Test that memory allocation logging warns when memory uncommitment fails
 * @library /test/lib
 * @requires os.family != "windows" & os.family != "aix"
 * @requires vm.flagless
 * @requires vm.bits == "64"
 * @requires vm.debug == false
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testUncommitFailed
 */

/*
 * @test id=testReleaseFailed
 * @summary Test that memory allocation logging warns when memory release fails
 * @library /test/lib
 * @requires os.family != "windows" & os.family != "aix"
 * @requires vm.flagless
 * @requires vm.bits == "64"
 * @requires vm.debug == false
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testReleaseFailed
 */

public class TestMemoryAllocationLogging {

    protected static final long PAGE_SIZE = 64 * 1024; // 64Kb - largest page size in any system
    protected static final long COMMIT_SIZE = 1024;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new RuntimeException("Argument error");
        }
        String[] options = new String[] {
                "-Xlog:os+map=trace", // trace level will also print debug level
                "-XX:-CreateCoredumpOnCrash",
                "-Xms17m",
                "-Xmx17m",
                // Options for WhiteBox below
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-Xbootclasspath/a:.",
                TestMemoryAllocationLogging.Tester.class.getName(),
                args[0]};

        String[] expectedLogs;

        /* Debug logging level tests */
        switch (args[0]) {
            case "testSuccessfulFlow": {
                expectedLogs = new String[]{
                        /* Debug level log */
                        String.format("Reserved \\[0x.* - 0x.*\\), \\(%d bytes\\)", PAGE_SIZE),
                        String.format("Committed \\[0x.* - 0x.*\\), \\(%d bytes\\)", COMMIT_SIZE),
                        String.format("Uncommitted \\[0x.* - 0x.*\\), \\(%d bytes\\)", COMMIT_SIZE),
                        String.format("Released \\[0x.* - 0x.*\\), \\(%d bytes\\)", PAGE_SIZE)
                };
                break;
            }
            case "testAttemptedReserveFailed": {
                expectedLogs = new String[] {
                        /* Debug level log */
                        String.format("Reserved \\[0x.* - 0x.*\\), \\(%d bytes\\)", PAGE_SIZE),
                        String.format("Attempt to reserve \\[0x.* - 0x.*\\), \\(.* bytes\\) failed"),
                };
                break;
            }
            case "testCommitFailed": {
                expectedLogs = new String[] {
                        /* Debug level log */
                        String.format("Failed to commit \\[0x.* - 0x.*\\), \\(%d bytes\\)", COMMIT_SIZE),
                        /* Trace level log */
                        "mmap failed: \\[0x.* - 0x.*\\), \\(.* bytes\\) errno=\\(Invalid argument\\)"
                };
                break;
            }
            case "testUncommitFailed": {
                expectedLogs = new String[] {
                        /* Debug level log */
                        String.format("Reserved \\[0x.* - 0x.*\\), \\(%d bytes\\)", PAGE_SIZE),
                        "fatal error: Failed to uncommit \\[0x.* - 0x.*\\), \\(.* bytes\\).*",
                        /* Trace level log */
                        "mmap failed: \\[0x.* - 0x.*\\), \\(.* bytes\\) errno=\\(Invalid argument\\)"
                };
                break;
            }
            case "testReleaseFailed": {
                expectedLogs = new String[] {
                        /* Debug level log */
                        "Failed to release \\[0x.* - 0x.*\\), \\(.* bytes\\)",
                        /* Trace level log */
                        "munmap failed: \\[0x.* - 0x.*\\), \\(.* bytes\\) errno=\\(Invalid argument\\)"
                };
                break;
            }

            default: {
                throw new RuntimeException("Invalid test " + args[0]);
            }
        }

        OutputAnalyzer output = runTestWithOptions(options);
        checkExpectedLogMessages(output, expectedLogs);
    }

    private static OutputAnalyzer runTestWithOptions(String[] options) throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(options);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        return output;
    }

    private static void checkExpectedLogMessages(OutputAnalyzer output, String[] regexs) throws RuntimeException {
        for (String regex : regexs) {
            output.shouldMatch(regex);
        }
    }

    static class Tester {
        public static void main(String[] args) throws Exception {
            System.out.println("Tester execution...");
            WhiteBox wb = WhiteBox.getWhiteBox();

            switch (args[0]) {
                case "testSuccessfulFlow": {
                    long addr = wb.NMTReserveMemory(PAGE_SIZE);
                    wb.NMTCommitMemory(addr, COMMIT_SIZE);
                    wb.NMTUncommitMemory(addr, COMMIT_SIZE);
                    wb.NMTReleaseMemory(addr, PAGE_SIZE);
                    break;
                }
                case "testAttemptedReserveFailed": {
                    long addr = wb.NMTReserveMemory(PAGE_SIZE);
                    /* attempting to reserve the same address should fail */
                    wb.NMTAttemptReserveMemoryAt(addr, PAGE_SIZE);
                    break;
                }
                case "testCommitFailed": {
                    long addr = wb.NMTReserveMemory(PAGE_SIZE);
                    /* addr is not a multiple of system page size, so it should fail */
                    wb.NMTCommitMemory(addr - 1, COMMIT_SIZE);
                    break;
                }
                case "testUncommitFailed": {
                    long addr = wb.NMTReserveMemory(PAGE_SIZE);
                    wb.NMTCommitMemory(addr, PAGE_SIZE);
                    /* addr is not a multiple of a system page size, so it should fail */
                    wb.NMTUncommitMemory(addr - 1, PAGE_SIZE);
                    break;
                }
                case "testReleaseFailed": {
                    long addr = wb.NMTReserveMemory(PAGE_SIZE);
                    /* addr is not a multiple of system page size, so it should fail */
                    wb.NMTReleaseMemory(addr - 1, PAGE_SIZE);
                    break;
                }
                default: {
                    throw new RuntimeException("Invalid test " + args[0]);
                }
            }
        }
    }
}
