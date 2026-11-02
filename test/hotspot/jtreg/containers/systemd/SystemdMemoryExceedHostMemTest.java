/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

import jdk.test.lib.containers.systemd.SystemdRunOptions;
import jdk.test.lib.containers.systemd.SystemdTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8390314
 * @summary Verify no asserts are triggered in cgroup adjusting code
 *          when memory limit exceeds physical host memory.
 * @requires systemd.support
 * @library /test/lib
 * @modules java.base/jdk.internal.platform
 * @build HelloSystemd jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:whitebox.jar -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SystemdMemoryExceedHostMemTest
 */
public class SystemdMemoryExceedHostMemTest {

    private static final int MB = 1024 * 1024;
    private static final WhiteBox wb = WhiteBox.getWhiteBox();
    private static final String TEST_SLICE_NAME = SystemdMemoryExceedHostMemTest.class.getSimpleName() + "HS";

    public static void main(String[] args) throws Exception {
       testMemExceedsPhysical();
    }

    private static void testMemExceedsPhysical() throws Exception {
        SystemdRunOptions opts = SystemdTestUtils.newOpts("HelloSystemd");
        int expectedMemLimit = 1024;
        // 1 GB memory, the lower hierarchy has a value exceeding physical memory
        opts.memoryLimit(String.format("%dM", expectedMemLimit));
        // Set the memory limit of a slice stricly larger than the host
        // max memory
        String exceedingHostMem = getHostMaxMemory() + "0"; // add a zero
        opts.sliceDMemoryLimit(exceedingHostMem);
        opts.cpuLimit("100%"); // 1 core
        opts.sliceName(TEST_SLICE_NAME);

        OutputAnalyzer out = SystemdTestUtils.buildAndRunSystemdJava(opts);
        // On affected systems this asserts in fastdebug
        out.shouldHaveExitValue(0)
           .shouldContain("Hello Systemd");
        try {
            out.shouldContain(String.format("Memory Limit is: %d", (expectedMemLimit * MB)));
        } catch (RuntimeException e) {
            // memory delegation needs to be enabled when run as user on cg v2
            if (SystemdTestUtils.RUN_AS_USER) {
                String hint = "When run as user on cg v2 memory delegation needs to be configured!";
                throw new SkippedException(hint);
            }
            throw e;
        }
    }

    private static String getHostMaxMemory() {
        return Long.valueOf(wb.hostPhysicalMemory()).toString();
    }
}
