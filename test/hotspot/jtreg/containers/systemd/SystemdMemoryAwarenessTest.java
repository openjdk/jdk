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
 * @summary Memory/CPU awareness test for JDK-under-test inside a systemd slice.
 * @requires systemd.support
 * @library /test/lib
 * @build HelloSystemd
 * @run driver SystemdMemoryAwarenessTest
 */
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import jdk.test.lib.containers.systemd.SystemdRunOptions;
import jdk.test.lib.containers.systemd.SystemdTestUtils;
import jdk.test.lib.containers.systemd.SystemdTestUtils.ResultFiles;


public class SystemdMemoryAwarenessTest {

    public static void main(String[] args) throws Exception {
       testHelloSystemd();
    }

    private static void testHelloSystemd() throws Exception {
        SystemdRunOptions opts = SystemdTestUtils.newOpts("HelloSystemd");
        // 1 GB memory and 2 cores CPU
        opts.memoryLimit("1000M").cpuLimit("200%");
        opts.sliceName(SystemdMemoryAwarenessTest.class.getSimpleName());

        ResultFiles files = SystemdTestUtils.buildSystemdSlices(opts);

        try {
            SystemdTestUtils.systemdRunJava(opts)
                .shouldHaveExitValue(0)
                .shouldContain("Hello Systemd")
                .shouldContain("OSContainer::active_processor_count: 2")
                .shouldContain("Memory Limit is: 1048576000"); // 1 GB
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
