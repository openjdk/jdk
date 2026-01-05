/*
 * Copyright (c) 2024, 2025, Red Hat, Inc.
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
 * @bug 8322420 8217338
 * @summary Memory/CPU awareness test for JDK-under-test inside a systemd slice.
 * @requires systemd.support
 * @library /test/lib
 * @modules java.base/jdk.internal.platform
 * @build HelloSystemd jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:whitebox.jar -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SystemdMemoryAwarenessTest
 */
public class SystemdMemoryAwarenessTest {

    private static final int MB = 1024 * 1024;
    private static final WhiteBox wb = WhiteBox.getWhiteBox();
    private static final String TEST_SLICE_NAME = SystemdMemoryAwarenessTest.class.getSimpleName() + "HS";

    public static void main(String[] args) throws Exception {
       testHelloSystemd();
    }

    private static void testHelloSystemd() throws Exception {
        SystemdRunOptions opts = SystemdTestUtils.newOpts("HelloSystemd");
        // 1 GB memory, but the limit in the lower hierarchy is 512M
        opts.memoryLimit("1024M");
        int expectedMemLimit = 512;
        // expected detected limit we test for, 512MB
        opts.sliceDMemoryLimit(String.format("%dM", expectedMemLimit));
        int physicalCpus = wb.hostCPUs();
        if (physicalCpus < 2) {
           System.err.println("WARNING: host system only has " + physicalCpus + " cpus. Expected >= 2");
           System.err.println("The active_processor_count assertion will trivially pass.");
        }
        // Use a CPU core limit of 1 for best coverage
        int coreLimit = 1;
        System.out.println("DEBUG: Running test with a CPU limit of " + coreLimit);
        opts.cpuLimit(String.format("%d%%", coreLimit * 100));
        opts.sliceName(TEST_SLICE_NAME);

        OutputAnalyzer out = SystemdTestUtils.buildAndRunSystemdJava(opts);
        out.shouldHaveExitValue(0)
           .shouldContain("Hello Systemd");
        try {
            out.shouldContain(String.format("Memory Limit is: %d", (expectedMemLimit * MB)));
            out.shouldContain("OSContainer::active_processor_count: " + coreLimit);
        } catch (RuntimeException e) {
            // CPU/memory delegation needs to be enabled when run as user on cg v2
            if (SystemdTestUtils.RUN_AS_USER) {
                String hint = "When run as user on cg v2 cpu/memory delegation needs to be configured!";
                throw new SkippedException(hint);
            }
            throw e;
        }
    }

}
