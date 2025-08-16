/*
 * Copyright (c) 2025, Red Hat, Inc.
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
 * @bug 8360651
 * @summary Verify OSContainer::has_memory_limit() in a systemd slice
 * @requires systemd.support
 * @library /test/lib ../
 * @modules java.base/jdk.internal.platform
 * @build ContainerMemory jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:whitebox.jar -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI MemoryLimitTest
 */
public class MemoryLimitTest {

    private static final int MB = 1024 * 1024;
    private static final WhiteBox wb = WhiteBox.getWhiteBox();
    private static final String TEST_SLICE_NAME = MemoryLimitTest.class.getSimpleName() + "HS";

    public static void main(String[] args) throws Exception {
       testHasMemoryLimit();
    }

    private static void testHasMemoryLimit() throws Exception {
        SystemdRunOptions opts = SystemdTestUtils.newOpts("ContainerMemory");
        opts.addClassOptions("hasMemoryLimit", "true");
        opts.memoryLimit("100M");
        opts.sliceName(TEST_SLICE_NAME);
        SystemdTestUtils.addWhiteBoxOpts(opts);

        OutputAnalyzer out = SystemdTestUtils.buildAndRunSystemdJava(opts);
        out.shouldHaveExitValue(0);
        try {
            out.shouldContain("hasMemoryLimit=true");
        } catch (RuntimeException e) {
            // memory delegation needs to be enabled when run as user on cg v2
            if (SystemdTestUtils.RUN_AS_USER) {
                String hint = "When run as user on cg v2 memory delegation needs to be configured!";
                throw new SkippedException(hint);
            }
            throw e;
        }
    }

}
