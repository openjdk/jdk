

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.IOException;

import jdk.test.whitebox.WhiteBox;



/*
 * @test id=testSuccessfulFlow
 * @summary Test that memory allocation logging works when allocation operations run without error
 * @library /test/lib
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testSuccessfulFlow
 */

/*
 * @test id=testAttemptedReserveFailed
 * @summary Test that memory allocation logging warns when attempted reservation fails
 * @library /test/lib
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testAttemptedReserveFailed
 */

/*
 * @test id=testReserveFailed
 * @summary Test that memory allocation logging warns when reservation fails
 * @library /test/lib
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testReserveFailed
 */

/*
 * @test id=testCommitFailed
 * @summary Test that memory allocation logging warns when commit attempts fail
 * @library /test/lib
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testCommitFailed
 */

/*
 * @test id=testUncommitFailed
 * @summary Test that memory allocation logging warns when memory uncommitment fails
 * @library /test/lib
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testUncommitFailed
 */

/*
 * @test id=testReleaseFailed
 * @summary Test that memory allocation logging warns when memory release fails
 * @library /test/lib
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestMemoryAllocationLogging testReleaseFailed
 */

public class TestMemoryAllocationLogging {

    protected static final long PAGE_SIZE = 64 * 1024; // 64Kb - largest page size in any system
    protected static final long COMMIT_SIZE = 1024;
    protected static long tooBig = 1024L * 1000000000000L; // 1Tb

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new RuntimeException("Argument error");
        }
        String[] options = new String[] {
                "-Xlog:os+map=debug",
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
        switch (args[0]) {
            case "testSuccessfulFlow": {
                expectedLogs = new String[]{
                        String.format("Reserved \\[0x.* - 0x.*\\] \\(%d bytes\\).", PAGE_SIZE),
                        String.format("Committed \\[0x.* - 0x.*\\] \\(%d bytes\\).", COMMIT_SIZE),
                        String.format("Uncommitted \\[0x.* - 0x.*\\] \\(%d bytes\\).", COMMIT_SIZE),
                        String.format("Released \\[0x.* - 0x.*\\] \\(%d bytes\\).", PAGE_SIZE)
                };
                break;
            }
            case "testAttemptedReserveFailed": {
                expectedLogs = new String[] {
                        String.format("Reserved \\[0x.* - 0x.*\\] \\(%d bytes\\).", PAGE_SIZE),
                        String.format("Attempt to reserve \\[0x.* - 0x.*\\] \\(%d bytes\\) failed, errno", PAGE_SIZE)
                };
                break;
            }
            case "testReserveFailed": {
                expectedLogs = new String[] { "Reserve failed \\(.* bytes\\), errno"
                };
                break;
            }
            case "testCommitFailed": {
                expectedLogs = new String[] {
                        String.format("Failed to commit \\[0x.* - 0x.*\\] \\(%d bytes\\), errno", COMMIT_SIZE)
                };
                break;
            }
            case "testUncommitFailed": {
                expectedLogs = new String[] {
                        String.format("Reserved \\[0x.* - 0x.*\\] \\(%d bytes\\).", PAGE_SIZE),
                       "Failed to uncommit \\[0x.* - 0x.*\\] \\(.* bytes\\), errno"
                };
                break;
            }
            case "testReleaseFailed": {
                expectedLogs = new String[] {
                        "Failed to release \\[0x.* - 0x.*\\] \\(.* bytes\\), errno"
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
        output.shouldHaveExitValue(0);
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
                    wb.NMTAttemptReserveMemoryAt(addr, PAGE_SIZE);
                    break;
                }
                case "testReserveFailed": {
                    wb.NMTReserveMemory(tooBig);
                    break;
                }
                case "testCommitFailed": {
                    wb.NMTCommitMemory(1, COMMIT_SIZE);
                    break;
                }
                case "testUncommitFailed": {
                    long addr = wb.NMTReserveMemory(PAGE_SIZE);
                    wb.NMTUncommitMemory(addr, tooBig);
                    break;
                }
                case "testReleaseFailed": {
                    wb.NMTReleaseMemory(-1, tooBig);
                    break;
                }
                default: {
                    throw new RuntimeException("Invalid test " + args[0]);
                }
            }
        }
    }
}
