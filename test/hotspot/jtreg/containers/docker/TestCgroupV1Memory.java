/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import jdk.internal.platform.Metrics;
import jdk.test.lib.containers.cgroup.CgroupV1TestUtils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @key cgroups
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform
 * @library /test/lib
 * @build sun.hotspot.WhiteBox PrintContainerInfo
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar sun.hotspot.WhiteBox
 * @run main TestCgroupV1Memory
 */
public class TestCgroupV1Memory {

    private static final String SUB_SYSTEM_PRE = "memory:";
    private static final String SUB_SYSTEM_NAME = "memorytest";

    public static void main(String[] args) throws Exception {
        // If cgroups is not configured, report success.
        Metrics metrics = Metrics.systemMetrics();
        if (metrics == null) {
            System.out.println("TEST PASSED!!!");
            return;
        }
        if ("cgroupv1".equals(metrics.getProvider())) {
            Common.prepareWhiteBox();
            CgroupV1TestUtils.createSubSystem(SUB_SYSTEM_PRE + SUB_SYSTEM_NAME);

            try {
                testMemoryLimit();
                testMemoryLimitWithSwappiness();
            } finally {
                CgroupV1TestUtils.deleteSubSystem(SUB_SYSTEM_PRE + SUB_SYSTEM_NAME);
            }
        }
        System.out.println("TEST PASSED!!!");
    }

    private static void testMemoryLimit() throws Exception {
        OutputAnalyzer out = commonMemorySetting();
        out.shouldContain("Memory Limit: 50.00M")
           .shouldContain("memory_and_swap_limit_in_bytes: 104857600")
           .shouldContain("Memory & Swap Limit: 100.00M");
    }

    private static void testMemoryLimitWithSwappiness() throws Exception {
        
        CgroupV1TestUtils.initSubSystem(SUB_SYSTEM_NAME, "memory.swappiness=0");
        OutputAnalyzer out = commonMemorySetting();
        out.shouldContain("Memory Limit: 50.00M")
           .shouldContain("memory_and_swap_limit_in_bytes: 52428800")
           .shouldContain("Memory & Swap Limit: 50.00M")
           .shouldContain("Memory and Swap Limit has been reset to 52428800 because of Swappiness is 0");
    }

    private static OutputAnalyzer commonMemorySetting() throws Exception {
        CgroupV1TestUtils.initSubSystem(SUB_SYSTEM_NAME, "memory.limit_in_bytes=52428800");
        CgroupV1TestUtils.initSubSystem(SUB_SYSTEM_NAME, "memory.memsw.limit_in_bytes=104857600");

        List<String> subSystems = new ArrayList<>();
        subSystems.add(SUB_SYSTEM_PRE + SUB_SYSTEM_NAME);

        List<String> jvmOps  = new ArrayList<>();
        jvmOps.add("-XshowSettings:system");
        jvmOps.add("-Xbootclasspath/a:whitebox.jar");
        jvmOps.add("-XX:+UnlockDiagnosticVMOptions");
        jvmOps.add("-XX:+WhiteBoxAPI");
        jvmOps.add("-Xlog:os+container=trace");
        OutputAnalyzer outputAnalyzer = CgroupV1TestUtils
                .runJavaApp(subSystems, jvmOps , "PrintContainerInfo");
        return outputAnalyzer;
    } 

}
