/*
 * Copyright (c) 2020, Red Hat Inc.
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
 * @test CgroupSubsystemFactory
 * @requires os.family == "linux"
 * @library /testlibrary /test/lib
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI CgroupSubsystemFactory
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.util.FileUtils;
import sun.hotspot.WhiteBox;

/*
 * Verify hotspot's detection heuristics of CgroupSubsystemFactory::create()
 */
public class CgroupSubsystemFactory {

    // Mirrored from src/hotspot/os/linux/cgroupSubsystem_linux.hpp
    private static final int CGROUPS_V1 = 1;
    private static final int CGROUPS_V2 = 2;
    private static final int INVALID_CGROUPS_V2 = 3;
    private static final int INVALID_CGROUPS_V1 = 4;
    private static final int INVALID_CGROUPS_NO_MOUNT = 5;
    private Path existingDirectory;
    private Path cgroupv1CgInfoZeroHierarchy;
    private Path cgroupv1MntInfoZeroHierarchy;
    private Path cgroupv2CgInfoZeroHierarchy;
    private Path cgroupv2MntInfoZeroHierarchy;
    private Path cgroupv1CgInfoNonZeroHierarchy;
    private Path cgroupv1MntInfoNonZeroHierarchyOtherOrder;
    private Path cgroupv1MntInfoNonZeroHierarchy;
    private String mntInfoEmpty = "";
    private Path cgroupV1SelfCgroup;
    private Path cgroupV2SelfCgroup;
    private Path cgroupV2MntInfoMissingCgroupv2;
    private Path cgroupv1MntInfoMissingMemoryController;
    private String procSelfCgroupHybridContent = "11:hugetlb:/\n" +
            "10:devices:/user.slice\n" +
            "9:pids:/user.slice/user-15263.slice/user@15263.service\n" +
            "8:cpu,cpuacct:/\n" +
            "7:perf_event:/\n" +
            "6:freezer:/\n" +
            "5:blkio:/\n" +
            "4:net_cls,net_prio:/\n" +
            "3:cpuset:/\n" +
            "2:memory:/user.slice/user-15263.slice/user@15263.service\n" +
            "1:name=systemd:/user.slice/user-15263.slice/user@15263.service/gnome-terminal-server.service\n" +
            "0::/user.slice/user-15263.slice/user@15263.service/gnome-terminal-server.service";
    private String procSelfCgroupV2UnifiedContent = "0::/user.slice/user-1000.slice/session-3.scope";
    private String cgroupsZeroHierarchy =
            "#subsys_name hierarchy num_cgroups enabled\n" +
            "cpuset 0 1 1\n" +
            "cpu 0 1 1\n" +
            "cpuacct 0 1 1\n" +
            "memory 0 1 1\n" +
            "devices 0 1 1\n" +
            "freezer 0 1 1\n" +
            "net_cls 0 1 1\n" +
            "blkio 0 1 1\n" +
            "perf_event 0 1 1 ";
    private String cgroupV2LineHybrid = "31 30 0:27 / /sys/fs/cgroup/unified rw,nosuid,nodev,noexec,relatime shared:5 - cgroup2 cgroup2 rw,seclabel,nsdelegate\n";
    private String cgroupv1MountInfoLineMemory = "35 30 0:31 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:7 - cgroup cgroup rw,seclabel,memory\n";
    private String mntInfoHybridStub =
            "30 23 0:26 / /sys/fs/cgroup ro,nosuid,nodev,noexec shared:4 - tmpfs tmpfs ro,seclabel,mode=755\n" +
            "32 30 0:28 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime shared:6 - cgroup cgroup rw,seclabel,xattr,name=systemd\n" +
            "36 30 0:32 / /sys/fs/cgroup/pids rw,nosuid,nodev,noexec,relatime shared:8 - cgroup cgroup rw,seclabel,pids\n" +
            "37 30 0:33 / /sys/fs/cgroup/perf_event rw,nosuid,nodev,noexec,relatime shared:9 - cgroup cgroup rw,seclabel,perf_event\n" +
            "38 30 0:34 / /sys/fs/cgroup/net_cls,net_prio rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,net_cls,net_prio\n" +
            "39 30 0:35 / /sys/fs/cgroup/hugetlb rw,nosuid,nodev,noexec,relatime shared:11 - cgroup cgroup rw,seclabel,hugetlb\n" +
            "40 30 0:36 / /sys/fs/cgroup/cpu,cpuacct rw,nosuid,nodev,noexec,relatime shared:12 - cgroup cgroup rw,seclabel,cpu,cpuacct\n" +
            "41 30 0:37 / /sys/fs/cgroup/devices rw,nosuid,nodev,noexec,relatime shared:13 - cgroup cgroup rw,seclabel,devices\n" +
            "42 30 0:38 / /sys/fs/cgroup/cpuset rw,nosuid,nodev,noexec,relatime shared:14 - cgroup cgroup rw,seclabel,cpuset\n" +
            "43 30 0:39 / /sys/fs/cgroup/blkio rw,nosuid,nodev,noexec,relatime shared:15 - cgroup cgroup rw,seclabel,blkio\n" +
            "44 30 0:40 / /sys/fs/cgroup/freezer rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup rw,seclabel,freezer";
    private String mntInfoHybridRest = cgroupv1MountInfoLineMemory + mntInfoHybridStub;
    private String mntInfoHybridMissingMemory = mntInfoHybridStub;
    private String mntInfoHybrid = cgroupV2LineHybrid + mntInfoHybridRest;
    private String mntInfoHybridFlippedOrder = mntInfoHybridRest + cgroupV2LineHybrid;
    private String cgroupsNonZeroHierarchy =
            "#subsys_name hierarchy   num_cgroups enabled\n" +
            "cpuset  3   1   1\n" +
            "cpu 8   1   1\n" +
            "cpuacct 8   1   1\n" +
            "blkio   10  1   1\n" +
            "memory  2   90  1\n" +
            "devices 8   74  1\n" +
            "freezer 11  1   1\n" +
            "net_cls 5   1   1\n" +
            "perf_event  4   1   1\n" +
            "net_prio    5   1   1\n" +
            "hugetlb 6   1   1\n" +
            "pids    3   80  1";
    private String mntInfoCgroupsV2Only =
            "28 21 0:25 / /sys/fs/cgroup rw,nosuid,nodev,noexec,relatime shared:4 - cgroup2 cgroup2 rw,seclabel,nsdelegate";

    private void setup() {
        try {
            existingDirectory = Utils.createTempDirectory(CgroupSubsystemFactory.class.getSimpleName());
            Path cgroupsZero = Paths.get(existingDirectory.toString(), "cgroups_zero");
            Files.writeString(cgroupsZero, cgroupsZeroHierarchy, StandardCharsets.UTF_8);
            cgroupv1CgInfoZeroHierarchy = cgroupsZero;
            cgroupv2CgInfoZeroHierarchy = cgroupsZero;
            cgroupv1MntInfoZeroHierarchy = Paths.get(existingDirectory.toString(), "mountinfo_empty");
            Files.writeString(cgroupv1MntInfoZeroHierarchy, mntInfoEmpty);

            cgroupv2MntInfoZeroHierarchy = Paths.get(existingDirectory.toString(), "mountinfo_cgroupv2");
            Files.writeString(cgroupv2MntInfoZeroHierarchy, mntInfoCgroupsV2Only);

            cgroupv1CgInfoNonZeroHierarchy = Paths.get(existingDirectory.toString(), "cgroups_non_zero");
            Files.writeString(cgroupv1CgInfoNonZeroHierarchy, cgroupsNonZeroHierarchy);

            cgroupv1MntInfoNonZeroHierarchy = Paths.get(existingDirectory.toString(), "mountinfo_non_zero");
            Files.writeString(cgroupv1MntInfoNonZeroHierarchy, mntInfoHybrid);

            cgroupv1MntInfoNonZeroHierarchyOtherOrder = Paths.get(existingDirectory.toString(), "mountinfo_non_zero_cgroupv2_last");
            Files.writeString(cgroupv1MntInfoNonZeroHierarchyOtherOrder, mntInfoHybridFlippedOrder);

            cgroupV1SelfCgroup = Paths.get(existingDirectory.toString(), "cgroup_self_hybrid");
            Files.writeString(cgroupV1SelfCgroup, procSelfCgroupHybridContent);

            cgroupV2SelfCgroup = Paths.get(existingDirectory.toString(), "cgroup_self_v2");
            Files.writeString(cgroupV2SelfCgroup, procSelfCgroupV2UnifiedContent);

            cgroupv1MntInfoMissingMemoryController = Paths.get(existingDirectory.toString(), "mnt_info_missing_memory");
            Files.writeString(cgroupv1MntInfoMissingMemoryController, mntInfoHybridMissingMemory);

            cgroupV2MntInfoMissingCgroupv2 = Paths.get(existingDirectory.toString(), "mnt_info_missing_cgroup2");
            Files.writeString(cgroupV2MntInfoMissingCgroupv2, mntInfoHybridStub);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void teardown() {
        try {
            FileUtils.deleteFileTreeWithRetry(existingDirectory);
        } catch (IOException e) {
            System.err.println("Teardown failed. " + e.getMessage());
        }
    }

    private boolean isValidCgroup(int value) {
        return value == CGROUPS_V1 || value == CGROUPS_V2;
    }

    public void testCgroupv1NoMounts(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoZeroHierarchy.toString();
        int retval = wb.validateCgroup(procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_NO_MOUNT, retval, "No cgroups mounted in /proc/self/mountinfo. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv1NoMounts PASSED!");
    }

    public void testCgroupv2NoCgroup2Fs(WhiteBox wb) {
        String procCgroups = cgroupv2CgInfoZeroHierarchy.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = cgroupV2MntInfoMissingCgroupv2.toString();
        int retval = wb.validateCgroup(procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_V2, retval, "No cgroup2 filesystem in /proc/self/mountinfo. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv2NoCgroup2Fs PASSED!");
    }

    public void testCgroupv1MissingMemoryController(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoMissingMemoryController.toString();
        int retval = wb.validateCgroup(procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_V1, retval, "Required memory controller path missing in mountinfo. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv1MissingMemoryController PASSED!");
    }

    public void testCgroupv2(WhiteBox wb) {
        String procCgroups = cgroupv2CgInfoZeroHierarchy.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = cgroupv2MntInfoZeroHierarchy.toString();
        int retval = wb.validateCgroup(procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V2, retval, "Expected");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv2 PASSED!");
    }

    public void testCgroupV1Hybrid(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoNonZeroHierarchy.toString();
        int retval = wb.validateCgroup(procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V1, retval, "Hybrid cgroups expected as cgroups v1");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv1Hybrid PASSED!");
    }

    public void testCgroupV1HybridMntInfoOrder(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoNonZeroHierarchyOtherOrder.toString();
        int retval = wb.validateCgroup(procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V1, retval, "Hybrid cgroups expected as cgroups v1");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv1HybridMntInfoOrder PASSED!");
    }


    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        CgroupSubsystemFactory test = new CgroupSubsystemFactory();
        test.setup();
        try {
            test.testCgroupv1NoMounts(wb);
            test.testCgroupv2(wb);
            test.testCgroupV1Hybrid(wb);
            test.testCgroupV1HybridMntInfoOrder(wb);
            test.testCgroupv1MissingMemoryController(wb);
            test.testCgroupv2NoCgroup2Fs(wb);
        } finally {
            test.teardown();
        }
    }
}
