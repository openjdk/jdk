/*
 * Copyright (c) 2024, Red Hat, Inc.
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
 * @summary Memory/CPU Metrics awareness for JDK-under-test inside a systemd slice.
 * @requires systemd.support
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:whitebox.jar -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SystemdMemoryAwarenessTest
 */
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

import jdk.test.lib.Platform;
import jdk.test.lib.containers.systemd.SystemdRunOptions;
import jdk.test.lib.containers.systemd.SystemdTestUtils;
import jdk.test.lib.containers.systemd.SystemdTestUtils.ResultFiles;
import jdk.test.whitebox.WhiteBox;

import jtreg.SkippedException;


public class SystemdMemoryAwarenessTest {

    private static final WhiteBox wb = WhiteBox.getWhiteBox();
    private static final int HUNDRED_THOUSEND = 100_000;
    private static final int GB = 1024 * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        if (!Platform.isRoot()) {
            throw new SkippedException("Test requires to be run as root");
        }
        testSystemSettings();
    }

    private static void testSystemSettings() throws Exception {
        SystemdRunOptions opts = SystemdTestUtils.newOpts("-version");
        opts.addJavaOpts("-XshowSettings:system");
        // 1 GB memory
        opts.memoryLimit(String.format("%d", 1 * GB));
        int physicalCpus = wb.hostCPUs();
        if (physicalCpus < 3) {
           System.err.println("WARNING: host system only has " + physicalCpus + " expected >= 3");
        }
        // 1 or 2 cores limit depending on physical CPUs
        int coreLimit = Math.min(physicalCpus, 2);
        System.out.println("DEBUG: Running test with a CPU limit of " + coreLimit);
        opts.cpuLimit(String.format("%d%%", coreLimit * 100));
        opts.sliceName(SystemdMemoryAwarenessTest.class.getSimpleName());

        ResultFiles files = SystemdTestUtils.buildSystemdSlices(opts);

        try {
            SystemdTestUtils.systemdRunJava(opts)
                .shouldHaveExitValue(0)
                .shouldContain("Operating System Metrics:")
                .shouldContain("Effective CPU Count: " + coreLimit)
                .shouldContain(String.format("CPU Period: %dus", HUNDRED_THOUSEND))
                .shouldContain(String.format("CPU Quota: %dus", (HUNDRED_THOUSEND * coreLimit)))
                .shouldContain("Memory Limit: 1.00G");
        } finally {
            try {
                Files.delete(files.memory());
                Files.delete(files.cpu());
            } catch (NoSuchFileException e) {
                // ignore
            }
        }
    }

}
