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
 * @summary Exercise the scalar Zbc (carry-less multiply) CRC32 fast path on
 *          riscv64. This path is only reachable when Zbc is enabled, and under
 *          default flags the RVV vector path is preferred, so the scalar Zbc
 *          folding code is never covered by a plain TestCRC32 run. This driver
 *          detects Zbc/RVV support and forces the flag combinations that route
 *          through kernel_crc32_zbc_fold(), including the RVC-disabled case
 *          that stresses the far-branch dispatch to the Zbc entry.
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @compile TestCRC32.java
 * @run main/othervm/timeout=720 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.intrinsics.zip.TestCRC32RiscvZbc
 */

package compiler.intrinsics.zip;

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.cpuinfo.CPUInfo;

public class TestCRC32RiscvZbc {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    // Marker passed to the child VM so it runs the workload instead of
    // re-entering the driver logic.
    private static final String WORKER_ARG = "--worker";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && WORKER_ARG.equals(args[0])) {
            runWorkload();
        } else {
            launchSupportedModes();
        }
    }

    // Driver: detect host CPU features and spawn a child VM for each flag
    // combination that routes through the scalar Zbc folding path.
    private static void launchSupportedModes() throws Exception {
        boolean hasZbc = CPUInfo.hasFeature("zbc");
        boolean hasRvv = CPUInfo.hasFeature("rvv");

        boolean ranAnyMode = false;

        // Scalar Zbc fast path with no RVV: every large input flows through
        // kernel_crc32_zbc_fold().
        ranAnyMode |= maybeRun("Zbc scalar", hasZbc,
                "-XX:+UseZbc", "-XX:-UseRVV", "-XX:-UseZvbc");

        // Zbc and RVV both enabled: the vector path wins for large inputs while
        // mid-sized inputs still exercise the scalar Zbc dispatch. Verifies the
        // two fast paths coexist and pick the right route by length.
        ranAnyMode |= maybeRun("Zbc + RVV", hasZbc && hasRvv,
                "-XX:+UseZbc", "-XX:+UseRVV", "-XX:-UseZvbc");

        // Same as above but with RVC disabled, which widens the dispatch
        // encoding and stresses the far-branch to the scalar Zbc entry.
        ranAnyMode |= maybeRun("Zbc + RVV without RVC", hasZbc && hasRvv,
                "-XX:-UseRVC", "-XX:+UseZbc", "-XX:+UseRVV", "-XX:-UseZvbc");

        if (!ranAnyMode) {
            System.out.println("No Zbc extension present on this CPU; nothing to exercise.");
        }
    }

    // Launch a child VM for the given mode when its extensions are supported.
    // Returns true if a child VM was actually launched.
    private static boolean maybeRun(String label, boolean supported, String... modeFlags) throws Exception {
        if (!supported) {
            System.out.println("Skipping mode [" + label + "]: required CPU features not present");
            return false;
        }
        System.out.println("Running mode [" + label + "]");

        List<String> cmd = new ArrayList<>();
        cmd.add("-Xbootclasspath/a:.");
        cmd.add("-Xbatch");
        cmd.add("-XX:+UnlockDiagnosticVMOptions");
        cmd.add("-XX:+WhiteBoxAPI");
        cmd.add("-XX:+UseCRC32Intrinsics");
        for (String flag : modeFlags) {
            cmd.add(flag);
        }
        cmd.add(TestCRC32RiscvZbc.class.getName());
        cmd.add(WORKER_ARG);

        OutputAnalyzer output = ProcessTools.executeTestJava(cmd);
        output.shouldHaveExitValue(0);
        return true;
    }

    // Child VM: verify the intrinsic stayed enabled and check correctness
    // across boundary lengths that straddle the scalar Zbc threshold (128) and
    // the 16-byte folding-loop alignment boundaries.
    private static void runWorkload() throws Exception {
        verifyIntrinsicEnablement();
        runBoundaryCoverage();
        TestCRC32.main(new String[] {"-m", "2000"});
    }

    private static void verifyIntrinsicEnablement() {
        boolean useCRC32Intrinsics = WB.getBooleanVMFlag("UseCRC32Intrinsics");

        if (!useCRC32Intrinsics) {
            throw new RuntimeException("UseCRC32Intrinsics must remain enabled when explicitly requested by the test");
        }
    }

    private static void runBoundaryCoverage() throws Exception {
        int[] offsets = {0, 1, 2, 3, 4, 5, 7};
        int[] sizes = {
                0, 1, 2, 3, 4, 7, 8, 15, 16, 17,
                31, 32, 33, 63, 64, 65,
                127, 128, 129,
                255, 256, 257,
                383, 384, 385
        };

        for (int offset : offsets) {
            for (int size : sizes) {
                TestCRC32.verifyArrayUpdate(offset, size);
                TestCRC32.verifyDirectByteBufferUpdate(offset, size);
            }
        }
    }
}
