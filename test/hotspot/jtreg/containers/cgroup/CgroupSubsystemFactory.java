/*
 * Copyright (c) 2020, 2022, Red Hat Inc.
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
 * @bug 8287107
 * @key cgroups
 * @requires os.family == "linux"
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
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
import jdk.test.whitebox.WhiteBox;

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
    private static final int INVALID_CGROUPS_GENERIC = 6;
    private Path existingDirectory;
    private Path cgroupv1CgroupsJoinControllers;
    private Path cgroupv1SelfCgroupsJoinControllers;
    private Path cgroupv1MountInfoJoinControllers;
    private Path cgroupv1CgInfoZeroHierarchy;
    private Path cgroupv1MntInfoZeroHierarchy;
    private Path cgroupv2MntInfoZeroHierarchy;
    private Path cgroupv2MntInfoDouble;
    private Path cgroupv2MntInfoDouble2;
    private Path cgroupv1CgInfoNonZeroHierarchy;
    private Path cgroupv1MntInfoNonZeroHierarchyOtherOrder;
    private Path cgroupv1MntInfoNonZeroHierarchy;
    private Path cgroupv1MntInfoDoubleCpuset;
    private Path cgroupv1MntInfoDoubleCpuset2;
    private Path cgroupv1MntInfoDoubleMemory;
    private Path cgroupv1MntInfoDoubleMemory2;
    private Path cgroupv1MntInfoDoubleCpu;
    private Path cgroupv1MntInfoDoubleCpu2;
    private Path cgroupv1MntInfoDoublePids;
    private Path cgroupv1MntInfoDoublePids2;
    private Path cgroupv1MntInfoSystemdOnly;
    private String mntInfoEmpty = "";
    private Path cgroupV1SelfCgroup;
    private Path cgroupV2SelfCgroup;
    private Path cgroupV2MntInfoMissingCgroupv2;
    private Path cgroupv1MntInfoMissingMemoryController;
    private Path cgroupv2MntInfoNoZeroHierarchyOnlyFreezer;
    private Path cgroupv2SelfNoZeroHierarchyOnlyFreezer;
    private Path sysFsCgroupCgroupControllersTypicalPath;
    private Path sysFsCgroupCgroupControllersEmptyPath;
    private Path sysFsCgroupCgroupControllersBlankLinePath;
    private Path sysFsCgroupCgroupControllersNoMemoryPath;
    private Path sysFsCgroupCgroupControllersNoCpuPath;
    private Path sysFsCgroupCgroupControllersNoPidsPath;
    private Path sysFsCgroupCgroupControllersCpuMemoryOnlyPath;
    private Path sysFsCgroupCgroupControllersExtraWhitespacePath;
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
    private String procSelfCgroupV1JoinControllers =
            "9:freezer:/\n" +
            "8:rdma:/\n" +
            "7:blkio:/user.slice\n" +
            "6:devices:/user.slice\n" +
            "5:pids:/user.slice/user-1000.slice/session-2.scope\n" +
            "4:cpu,cpuacct,memory,net_cls,net_prio,hugetlb:/user.slice/user-1000.slice/session-2.scope\n" +
            "3:cpuset:/\n" +
            "2:perf_event:/\n" +
            "1:name=systemd:/user.slice/user-1000.slice/session-2.scope\n" +
            "0::/user.slice/user-1000.slice/session-2.scope\n";
    private String sysFsCgroupCgroupControllersTypicalContent = "cpuset cpu io memory hugetlb pids rdma misc\n";
    private String sysFsCgroupCgroupControllersEmptyContent = "";
    private String sysFsCgroupCgroupControllersBlankLineContent = "\n";
    private String sysFsCgroupCgroupControllersNoMemoryContent = "cpuset cpu io hugetlb pids rdma misc\n";
    private String sysFsCgroupCgroupControllersNoCpuContent = "cpuset io memory hugetlb pids rdma misc\n";
    private String sysFsCgroupCgroupControllersNoPidsContent = "cpuset cpu io memory hugetlb rdma misc\n";
    private String sysFsCgroupCgroupControllersCpuMemoryOnlyContent = "memory cpu\n";
    private String sysFsCgroupCgroupControllersExtraWhitespaceContent = "   cpu\t  \fmemory\r \n";
    private String cgroupsZeroHierarchy =
            "#subsys_name hierarchy num_cgroups enabled\n" +
            "cpuset 0 1 1\n" +
            "cpu 0 1 1\n" +
            "cpuacct 0 1 1\n" +
            "memory 0 1 1\n" +
            "devices 0 1 1\n" +
            "freezer 0 1 1\n" +
            "net_cls 0 1 1\n" +
            "pids 0 1 1\n" +
            "blkio 0 1 1\n" +
            "perf_event 0 1 1 ";
    private String cgroupsNonZeroJoinControllers =
            "#subsys_name hierarchy num_cgroups enabled\n" +
            "cpuset\t3\t1\t1\n" +
            "cpu\t4\t153\t1\n" +
            "cpuacct\t4\t153\t1\n" +
            "blkio\t7\t87\t1\n" +
            "memory\t4\t153\t1\n" +
            "devices\t6\t87\t1\n" +
            "freezer\t9\t1\t1\n" +
            "net_cls\t4\t153\t1\n" +
            "perf_event\t2\t1\t1\n" +
            "net_prio\t4\t153\t1\n" +
            "hugetlb\t4\t153\t1\n" +
            "pids\t5\t95\t1\n" +
            "rdma\t8\t1\t1\n";
    private String cgroupV2LineHybrid = "31 30 0:27 / /sys/fs/cgroup/unified rw,nosuid,nodev,noexec,relatime shared:5 - cgroup2 none rw,seclabel,nsdelegate\n";
    private String cgroupv1MountInfoLineMemory = "35 30 0:31 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:7 - cgroup none rw,seclabel,memory\n";
    private String mntInfoHybridStub =
            "30 23 0:26 / /sys/fs/cgroup ro,nosuid,nodev,noexec shared:4 - tmpfs tmpfs ro,seclabel,mode=755\n" +
            "32 30 0:28 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime shared:6 - cgroup none rw,seclabel,xattr,name=systemd\n" +
            "36 30 0:32 / /sys/fs/cgroup/pids rw,nosuid,nodev,noexec,relatime shared:8 - cgroup none rw,seclabel,pids\n" +
            "37 30 0:33 / /sys/fs/cgroup/perf_event rw,nosuid,nodev,noexec,relatime shared:9 - cgroup none rw,seclabel,perf_event\n" +
            "38 30 0:34 / /sys/fs/cgroup/net_cls,net_prio rw,nosuid,nodev,noexec,relatime shared:10 - cgroup none rw,seclabel,net_cls,net_prio\n" +
            "39 30 0:35 / /sys/fs/cgroup/hugetlb rw,nosuid,nodev,noexec,relatime shared:11 - cgroup none rw,seclabel,hugetlb\n" +
            "40 30 0:36 / /sys/fs/cgroup/cpu,cpuacct rw,nosuid,nodev,noexec,relatime shared:12 - cgroup none rw,seclabel,cpu,cpuacct\n" +
            "41 30 0:37 / /sys/fs/cgroup/devices rw,nosuid,nodev,noexec,relatime shared:13 - cgroup none rw,seclabel,devices\n" +
            "42 30 0:38 / /sys/fs/cgroup/cpuset rw,nosuid,nodev,noexec,relatime shared:14 - cgroup none rw,seclabel,cpuset\n" +
            "43 30 0:39 / /sys/fs/cgroup/blkio rw,nosuid,nodev,noexec,relatime shared:15 - cgroup none rw,seclabel,blkio\n" +
            "44 30 0:40 / /sys/fs/cgroup/freezer rw,nosuid,nodev,noexec,relatime shared:16 - cgroup none rw,seclabel,freezer\n";
    private String mntInfoHybridRest = cgroupv1MountInfoLineMemory + mntInfoHybridStub;
    private String mntInfoHybridMissingMemory = mntInfoHybridStub;
    private String mntInfoHybrid = cgroupV2LineHybrid + mntInfoHybridRest;
    private String mntInfoHybridFlippedOrder = mntInfoHybridRest + cgroupV2LineHybrid;
    private String mntInfoCgroupv1JoinControllers =
            "31 22 0:26 / /sys/fs/cgroup ro,nosuid,nodev,noexec shared:9 - tmpfs tmpfs ro,mode=755\n" +
            "32 31 0:27 / /sys/fs/cgroup/unified rw,nosuid,nodev,noexec,relatime shared:10 - cgroup2 cgroup2 rw,nsdelegate\n" +
            "33 31 0:28 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime shared:11 - cgroup cgroup rw,xattr,name=systemd\n" +
            "36 31 0:31 / /sys/fs/cgroup/perf_event rw,nosuid,nodev,noexec,relatime shared:15 - cgroup cgroup rw,perf_event\n" +
            "37 31 0:32 / /sys/fs/cgroup/cpuset rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup rw,cpuset\n" +
            "38 31 0:33 / /sys/fs/cgroup/cpu,cpuacct,net_cls,net_prio,hugetlb,memory rw,nosuid,nodev,noexec,relatime shared:17 - cgroup cgroup rw,cpu,cpuacct,memory,net_cls,net_prio,hugetlb\n" +
            "39 31 0:34 / /sys/fs/cgroup/pids rw,nosuid,nodev,noexec,relatime shared:18 - cgroup cgroup rw,pids\n" +
            "40 31 0:35 / /sys/fs/cgroup/devices rw,nosuid,nodev,noexec,relatime shared:19 - cgroup cgroup rw,devices\n" +
            "41 31 0:36 / /sys/fs/cgroup/blkio rw,nosuid,nodev,noexec,relatime shared:20 - cgroup cgroup rw,blkio\n" +
            "42 31 0:37 / /sys/fs/cgroup/rdma rw,nosuid,nodev,noexec,relatime shared:21 - cgroup cgroup rw,rdma\n" +
            "43 31 0:38 / /sys/fs/cgroup/freezer rw,nosuid,nodev,noexec,relatime shared:22 - cgroup cgroup rw,freezer\n";
    private String mntInfoCgroupv1MoreCpusetLine = "121 32 0:37 / /cpusets rw,relatime shared:69 - cgroup none rw,cpuset\n";
    private String mntInfoCgroupv1DoubleCpuset = mntInfoCgroupv1MoreCpusetLine + mntInfoHybrid;
    private String mntInfoCgroupv1DoubleCpuset2 =  mntInfoHybrid + mntInfoCgroupv1MoreCpusetLine;
    private String mntInfoCgroupv1MoreMemoryLine = "1100 1098 0:28 / /memory rw,nosuid,nodev,noexec,relatime master:6 - cgroup cgroup rw,memory\n";
    private String mntInfoCgroupv1DoubleMemory = mntInfoCgroupv1MoreMemoryLine + mntInfoHybrid;
    private String mntInfoCgroupv1DoubleMemory2 = mntInfoHybrid + mntInfoCgroupv1MoreMemoryLine;
    private String mntInfoCgroupv1DoubleCpuLine = "1101 1098 0:29 / /cpu,cpuacct rw,nosuid,nodev,noexec,relatime master:7 - cgroup cgroup rw,cpu,cpuacct\n";
    private String mntInfoCgroupv1DoubleCpu = mntInfoCgroupv1DoubleCpuLine + mntInfoHybrid;
    private String mntInfoCgroupv1DoubleCpu2 = mntInfoHybrid + mntInfoCgroupv1DoubleCpuLine;
    private String mntInfoCgroupv1DoublePidsLine = "1107 1098 0:35 / /pids rw,nosuid,nodev,noexec,relatime master:13 - cgroup cgroup rw,pids\n";
    private String mntInfoCgroupv1DoublePids = mntInfoCgroupv1DoublePidsLine + mntInfoHybrid;
    private String mntInfoCgroupv1DoublePids2 = mntInfoHybrid + mntInfoCgroupv1DoublePidsLine;
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
            "pids    9   80  1";  // hierarchy has to match procSelfCgroupHybridContent
    private String mntInfoCgroupsV2Only =
            "28 21 0:25 / /sys/fs/cgroup rw,nosuid,nodev,noexec,relatime shared:4 - cgroup2 none rw,seclabel,nsdelegate\n";
    private String mntInfoCgroupsV2MoreLine =
            "240 232 0:24 /../.. /cgroup-in ro,relatime - cgroup2 cgroup2 rw,nsdelegate\n";
    private String mntInfoCgroupsV2Double = mntInfoCgroupsV2MoreLine + mntInfoCgroupsV2Only;
    private String mntInfoCgroupsV2Double2 = mntInfoCgroupsV2Only + mntInfoCgroupsV2MoreLine;
    private String mntInfoCgroupsV1SystemdOnly =
            "35 26 0:26 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime - cgroup systemd rw,name=systemd\n" +
            "26 18 0:19 / /sys/fs/cgroup rw,relatime - tmpfs none rw,size=4k,mode=755\n";

    // We have a mix of V1 and V2 controllers, but none of the V1 controllers
    // are used by Java, so the JDK should start in V2 mode.
    private String cgroupsNonZeroHierarchyOnlyFreezer =
            "#subsys_name hierarchy  num_cgroups  enabled\n" +
            "cpuset  0  171  1\n" +
            "cpu  0  171  1\n" +
            "cpuacct  0  171  1\n" +
            "blkio  0  171  1\n" +
            "memory  0  171  1\n" +
            "devices  0  171  1\n" +
            "freezer  1  1  1\n" +
            "net_cls  0  171  1\n" +
            "perf_event  0  171  1\n" +
            "net_prio  0  171  1\n" +
            "hugetlb  0  171  1\n" +
            "pids  0  171  1\n" +
            "rdma  0  171  1\n" +
            "misc  0  171  1\n";
    private String cgroupv1SelfOnlyFreezerContent = "1:freezer:/\n" +
            "0::/user.slice/user-1000.slice/session-2.scope";
    private String mntInfoOnlyFreezerInV1 =
            "32 23 0:27 / /sys/fs/cgroup rw,nosuid,nodev,noexec,relatime shared:9 - cgroup2 cgroup2 rw,nsdelegate,memory_recursiveprot\n" +
            "911 32 0:47 / /sys/fs/cgroup/freezer rw,relatime shared:476 - cgroup freezer rw,freezer\n";
    // Test RHEL 8 (cgroups v1) with cpuset controller disabled via the kernel command line.
    // # grep cgroup /boot/grub2/grubenv
    // kernelopts=[...] cgroup_disable=cpuset
    private String procCgroupsCgroupsV1CpusetDisabledContent =
            "#subsys_name\thierarchy\tnum_cgroups\tenabled\n" +
            "cpuset\t0\t1\t0\n" +
            "cpu\t8\t1\t1\n" +
            "cpuacct\t8\t1\t1\n" +
            "blkio\t7\t1\t1\n" +
            "memory\t9\t114\t1\n" +
            "devices\t3\t67\t1\n" +
            "freezer\t2\t1\t1\n" +
            "net_cls\t6\t1\t1\n" +
            "perf_event\t4\t1\t1\n" +
            "net_prio\t6\t1\t1\n" +
            "hugetlb\t11\t1\t1\n" +
            "pids\t10\t91\t1\n" +
            "rdma\t5\t1\t1\n";
    private String procSelfCgroupCgroupsV1CpusetDisabledContent =
            "11:hugetlb:/\n" +
            "10:pids:/user.slice/user-0.slice/session-1.scope\n" +
            "9:memory:/user.slice/user-0.slice/session-1.scope\n" +
            "8:cpu,cpuacct:/\n" +
            "7:blkio:/\n" +
            "6:net_cls,net_prio:/\n" +
            "5:rdma:/\n" +
            "4:perf_event:/\n" +
            "3:devices:/system.slice/sshd.service\n" +
            "2:freezer:/\n" +
            "1:name=systemd:/user.slice/user-0.slice/session-1.scope\n";
    private String procSelfMountinfoCgroupsV1CpusetDisabledContent =
            "22 93 0:21 / /sys rw,nosuid,nodev,noexec,relatime shared:2 - sysfs sysfs rw,seclabel\n" +
            "23 93 0:5 / /proc rw,nosuid,nodev,noexec,relatime shared:25 - proc proc rw\n" +
            "24 93 0:6 / /dev rw,nosuid shared:21 - devtmpfs devtmpfs rw,seclabel,size=632252k,nr_inodes=158063,mode=755\n" +
            "25 22 0:7 / /sys/kernel/security rw,nosuid,nodev,noexec,relatime shared:3 - securityfs securityfs rw\n" +
            "26 24 0:22 / /dev/shm rw,nosuid,nodev shared:22 - tmpfs tmpfs rw,seclabel\n" +
            "27 24 0:23 / /dev/pts rw,nosuid,noexec,relatime shared:23 - devpts devpts rw,seclabel,gid=5,mode=620,ptmxmode=000\n" +
            "28 93 0:24 / /run rw,nosuid,nodev shared:24 - tmpfs tmpfs rw,seclabel,mode=755\n" +
            "29 22 0:25 / /sys/fs/cgroup ro,nosuid,nodev,noexec shared:4 - tmpfs tmpfs ro,seclabel,mode=755\n" +
            "30 29 0:26 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime shared:5 - cgroup cgroup rw,seclabel,xattr,release_agent=/usr/lib/systemd/systemd-cgroups-agent,name=systemd\n" +
            "31 22 0:27 / /sys/fs/pstore rw,nosuid,nodev,noexec,relatime shared:16 - pstore pstore rw,seclabel\n" +
            "32 22 0:28 / /sys/fs/bpf rw,nosuid,nodev,noexec,relatime shared:17 - bpf bpf rw,mode=700\n" +
            "33 29 0:29 / /sys/fs/cgroup/freezer rw,nosuid,nodev,noexec,relatime shared:6 - cgroup cgroup rw,seclabel,freezer\n" +
            "34 29 0:30 / /sys/fs/cgroup/devices rw,nosuid,nodev,noexec,relatime shared:7 - cgroup cgroup rw,seclabel,devices\n" +
            "35 29 0:31 / /sys/fs/cgroup/perf_event rw,nosuid,nodev,noexec,relatime shared:8 - cgroup cgroup rw,seclabel,perf_event\n" +
            "36 29 0:32 / /sys/fs/cgroup/rdma rw,nosuid,nodev,noexec,relatime shared:9 - cgroup cgroup rw,seclabel,rdma\n" +
            "37 29 0:33 / /sys/fs/cgroup/net_cls,net_prio rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,net_cls,net_prio\n" +
            "38 29 0:34 / /sys/fs/cgroup/blkio rw,nosuid,nodev,noexec,relatime shared:11 - cgroup cgroup rw,seclabel,blkio\n" +
            "39 29 0:35 / /sys/fs/cgroup/cpu,cpuacct rw,nosuid,nodev,noexec,relatime shared:12 - cgroup cgroup rw,seclabel,cpu,cpuacct\n" +
            "40 29 0:36 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:13 - cgroup cgroup rw,seclabel,memory\n" +
            "41 29 0:37 / /sys/fs/cgroup/pids rw,nosuid,nodev,noexec,relatime shared:14 - cgroup cgroup rw,seclabel,pids\n" +
            "42 29 0:38 / /sys/fs/cgroup/hugetlb rw,nosuid,nodev,noexec,relatime shared:15 - cgroup cgroup rw,seclabel,hugetlb\n" +
            "43 22 0:12 / /sys/kernel/tracing rw,relatime shared:18 - tracefs none rw,seclabel\n" +
            "90 22 0:39 / /sys/kernel/config rw,relatime shared:19 - configfs configfs rw\n" +
            "93 1 253:0 / / rw,relatime shared:1 - xfs /dev/mapper/rhel-root rw,seclabel,attr2,inode64,logbufs=8,logbsize=32k,noquota\n" +
            "44 22 0:20 / /sys/fs/selinux rw,relatime shared:20 - selinuxfs selinuxfs rw\n" +
            "45 24 0:19 / /dev/mqueue rw,relatime shared:26 - mqueue mqueue rw,seclabel\n" +
            "46 23 0:40 / /proc/sys/fs/binfmt_misc rw,relatime shared:27 - autofs systemd-1 rw,fd=31,pgrp=1,timeout=0,minproto=5,maxproto=5,direct,pipe_ino=28718\n" +
            "47 24 0:41 / /dev/hugepages rw,relatime shared:28 - hugetlbfs hugetlbfs rw,seclabel,pagesize=2M\n" +
            "48 22 0:8 / /sys/kernel/debug rw,relatime shared:29 - debugfs debugfs rw,seclabel\n" +
            "49 22 0:42 / /sys/fs/fuse/connections rw,relatime shared:30 - fusectl fusectl rw\n" +
            "114 93 252:1 / /boot rw,relatime shared:61 - xfs /dev/vda1 rw,seclabel,attr2,inode64,logbufs=8,logbsize=32k,noquota\n" +
            "466 28 0:46 / /run/user/0 rw,nosuid,nodev,relatime shared:251 - tmpfs tmpfs rw,seclabel,size=130188k,mode=700\n";
    private Path procCgroupsCgroupsV1CpusetDisabledPath;
    private Path procSelfCgroupCgroupsV1CpusetDisabledPath;
    private Path procSelfMountinfoCgroupsV1CpusetDisabledPath;

    private void setup() {
        try {
            existingDirectory = Utils.createTempDirectory(CgroupSubsystemFactory.class.getSimpleName());
            Path cgroupsZero = Paths.get(existingDirectory.toString(), "cgroups_zero");
            Files.writeString(cgroupsZero, cgroupsZeroHierarchy, StandardCharsets.UTF_8);
            cgroupv1CgInfoZeroHierarchy = cgroupsZero;
            cgroupv1MntInfoZeroHierarchy = Paths.get(existingDirectory.toString(), "mountinfo_empty");
            Files.writeString(cgroupv1MntInfoZeroHierarchy, mntInfoEmpty);

            sysFsCgroupCgroupControllersTypicalPath = Paths.get(existingDirectory.toString(), "sys_fs_cgroup_cgroup_controllers_typical");
            Files.writeString(sysFsCgroupCgroupControllersTypicalPath, sysFsCgroupCgroupControllersTypicalContent, StandardCharsets.UTF_8);

            sysFsCgroupCgroupControllersEmptyPath = Paths.get(existingDirectory.toString(), "sys_fs_cgroup_cgroup_controllers_empty");
            Files.writeString(sysFsCgroupCgroupControllersEmptyPath, sysFsCgroupCgroupControllersEmptyContent, StandardCharsets.UTF_8);

            sysFsCgroupCgroupControllersBlankLinePath = Paths.get(existingDirectory.toString(), "sys_fs_cgroup_cgroup_controllers_blank_line");
            Files.writeString(sysFsCgroupCgroupControllersBlankLinePath, sysFsCgroupCgroupControllersBlankLineContent, StandardCharsets.UTF_8);

            sysFsCgroupCgroupControllersNoMemoryPath = Paths.get(existingDirectory.toString(), "sys_fs_cgroup_cgroup_controllers_no_memory");
            Files.writeString(sysFsCgroupCgroupControllersNoMemoryPath, sysFsCgroupCgroupControllersNoMemoryContent, StandardCharsets.UTF_8);

            sysFsCgroupCgroupControllersNoCpuPath = Paths.get(existingDirectory.toString(), "sys_fs_cgroup_cgroup_controllers_no_cpu");
            Files.writeString(sysFsCgroupCgroupControllersNoCpuPath, sysFsCgroupCgroupControllersNoCpuContent, StandardCharsets.UTF_8);

            sysFsCgroupCgroupControllersNoPidsPath = Paths.get(existingDirectory.toString(), "sys_fs_cgroup_cgroup_controllers_no_pids");
            Files.writeString(sysFsCgroupCgroupControllersNoPidsPath, sysFsCgroupCgroupControllersNoPidsContent, StandardCharsets.UTF_8);

            sysFsCgroupCgroupControllersCpuMemoryOnlyPath = Paths.get(existingDirectory.toString(), "sys_fs_cgroup_cgroup_controllers_cpu_memory_only");
            Files.writeString(sysFsCgroupCgroupControllersCpuMemoryOnlyPath, sysFsCgroupCgroupControllersCpuMemoryOnlyContent, StandardCharsets.UTF_8);

            sysFsCgroupCgroupControllersExtraWhitespacePath = Paths.get(existingDirectory.toString(), "sys_fs_cgroup_cgroup_controllers_extra_whitespace");
            Files.writeString(sysFsCgroupCgroupControllersExtraWhitespacePath, sysFsCgroupCgroupControllersExtraWhitespaceContent, StandardCharsets.UTF_8);

            cgroupv2MntInfoZeroHierarchy = Paths.get(existingDirectory.toString(), "mountinfo_cgroupv2");
            Files.writeString(cgroupv2MntInfoZeroHierarchy, mntInfoCgroupsV2Only);

            cgroupv2MntInfoDouble = Paths.get(existingDirectory.toString(), "mountinfo_cgroupv2_double");
            Files.writeString(cgroupv2MntInfoDouble, mntInfoCgroupsV2Double);

            cgroupv2MntInfoDouble2 = Paths.get(existingDirectory.toString(), "mountinfo_cgroupv2_double2");
            Files.writeString(cgroupv2MntInfoDouble2, mntInfoCgroupsV2Double2);

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

            cgroupv1MntInfoDoubleCpuset = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_double_cpuset");
            Files.writeString(cgroupv1MntInfoDoubleCpuset, mntInfoCgroupv1DoubleCpuset);

            cgroupv1MntInfoDoubleCpuset2 = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_double_cpuset2");
            Files.writeString(cgroupv1MntInfoDoubleCpuset2, mntInfoCgroupv1DoubleCpuset2);

            cgroupv1MntInfoDoubleMemory = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_double_memory");
            Files.writeString(cgroupv1MntInfoDoubleMemory, mntInfoCgroupv1DoubleMemory);

            cgroupv1MntInfoDoubleMemory2 = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_double_memory2");
            Files.writeString(cgroupv1MntInfoDoubleMemory2, mntInfoCgroupv1DoubleMemory2);

            cgroupv1MntInfoDoubleCpu = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_double_cpu");
            Files.writeString(cgroupv1MntInfoDoubleCpu, mntInfoCgroupv1DoubleCpu);

            cgroupv1MntInfoDoubleCpu2 = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_double_cpu2");
            Files.writeString(cgroupv1MntInfoDoubleCpu2, mntInfoCgroupv1DoubleCpu2);

            cgroupv1MntInfoDoublePids = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_double_pids");
            Files.writeString(cgroupv1MntInfoDoublePids, mntInfoCgroupv1DoublePids);

            cgroupv1MntInfoDoublePids2 = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_double_pids2");
            Files.writeString(cgroupv1MntInfoDoublePids2, mntInfoCgroupv1DoublePids2);

            cgroupv1MntInfoSystemdOnly = Paths.get(existingDirectory.toString(), "mnt_info_cgroupv1_systemd_only");
            Files.writeString(cgroupv1MntInfoSystemdOnly, mntInfoCgroupsV1SystemdOnly);

            cgroupv1CgroupsJoinControllers = Paths.get(existingDirectory.toString(), "cgroups_cgv1_join_controllers");
            Files.writeString(cgroupv1CgroupsJoinControllers, cgroupsNonZeroJoinControllers);

            cgroupv1SelfCgroupsJoinControllers = Paths.get(existingDirectory.toString(), "self_cgroup_cgv1_join_controllers");
            Files.writeString(cgroupv1SelfCgroupsJoinControllers, procSelfCgroupV1JoinControllers);

            cgroupv1MountInfoJoinControllers = Paths.get(existingDirectory.toString(), "mntinfo_cgv1_join_controllers");
            Files.writeString(cgroupv1MountInfoJoinControllers, mntInfoCgroupv1JoinControllers);

            cgroupv2SelfNoZeroHierarchyOnlyFreezer = Paths.get(existingDirectory.toString(), "self_cgroup_non_zero_only_freezer");
            Files.writeString(cgroupv2SelfNoZeroHierarchyOnlyFreezer, cgroupv1SelfOnlyFreezerContent);

            cgroupv2MntInfoNoZeroHierarchyOnlyFreezer = Paths.get(existingDirectory.toString(), "self_mountinfo_cgv2_non_zero_only_freezer");
            Files.writeString(cgroupv2MntInfoNoZeroHierarchyOnlyFreezer, mntInfoOnlyFreezerInV1);

            procCgroupsCgroupsV1CpusetDisabledPath = Paths.get(existingDirectory.toString(), "proc_cgroups_cgroups_v1_cpuset_disabled");
            Files.writeString(procCgroupsCgroupsV1CpusetDisabledPath, procCgroupsCgroupsV1CpusetDisabledContent);
            procSelfCgroupCgroupsV1CpusetDisabledPath = Paths.get(existingDirectory.toString(), "proc_self_cgroup_cgroups_v1_cpuset_disabled");
            Files.writeString(procSelfCgroupCgroupsV1CpusetDisabledPath, procSelfCgroupCgroupsV1CpusetDisabledContent);
            procSelfMountinfoCgroupsV1CpusetDisabledPath = Paths.get(existingDirectory.toString(), "proc_self_mountinfo_cgroups_v1_cpuset_disabled");
            Files.writeString(procSelfMountinfoCgroupsV1CpusetDisabledPath, procSelfMountinfoCgroupsV1CpusetDisabledContent);
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

    public void testCgroupv1JoinControllerCombo(WhiteBox wb) {
        String procCgroups = cgroupv1CgroupsJoinControllers.toString();
        String procSelfCgroup = cgroupv1SelfCgroupsJoinControllers.toString();
        String procSelfMountinfo = cgroupv1MountInfoJoinControllers.toString();
        int retval = wb.validateCgroup(false, procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V1, retval, "Join controllers should be properly detected");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv1JoinControllerMounts PASSED!");
    }

    public void testCgroupv1MultipleControllerMounts(WhiteBox wb, Path mountInfo) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(false, procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V1, retval, "Multiple controllers, but only one in /sys/fs/cgroup");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv1MultipleControllerMounts PASSED!");
    }

    public void testCgroupv1SystemdOnly(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoSystemdOnly.toString();
        int retval = wb.validateCgroup(false, procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_NO_MOUNT, retval, "Only systemd mounted. Invalid");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv1SystemdOnly PASSED!");
    }

    public void testCgroupv1NoMounts(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoZeroHierarchy.toString();
        int retval = wb.validateCgroup(false, procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_NO_MOUNT, retval, "No cgroups mounted in /proc/self/mountinfo. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv1NoMounts PASSED!");
    }

    public void testCgroupv2NoCgroup2Fs(WhiteBox wb) {
        String sysFsCgroupCgroupControllers = sysFsCgroupCgroupControllersTypicalPath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = cgroupV2MntInfoMissingCgroupv2.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_V2, retval, "No cgroup2 filesystem in /proc/self/mountinfo. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv2NoCgroup2Fs PASSED!");
    }

    public void testCgroupv1MissingMemoryController(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoMissingMemoryController.toString();
        int retval = wb.validateCgroup(false, procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_V1, retval, "Required memory controller path missing in mountinfo. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv1MissingMemoryController PASSED!");
    }

    public void testCgroupv2(WhiteBox wb, Path mountInfo) {
        String sysFsCgroupCgroupControllers  = sysFsCgroupCgroupControllersTypicalPath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V2, retval, "Expected");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv2 PASSED!");
    }

    public void testCgroupV1Hybrid(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoNonZeroHierarchy.toString();
        int retval = wb.validateCgroup(false, procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V1, retval, "Hybrid cgroups expected as cgroups v1");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv1Hybrid PASSED!");
    }

    public void testCgroupV1HybridMntInfoOrder(WhiteBox wb) {
        String procCgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String procSelfCgroup = cgroupV1SelfCgroup.toString();
        String procSelfMountinfo = cgroupv1MntInfoNonZeroHierarchyOtherOrder.toString();
        int retval = wb.validateCgroup(false, procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V1, retval, "Hybrid cgroups expected as cgroups v1");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv1HybridMntInfoOrder PASSED!");
    }

    public void testNonZeroHierarchyOnlyFreezer(WhiteBox wb) {
        String sysFsCgroupCgroupControllers = sysFsCgroupCgroupControllersTypicalPath.toString();
        String mountInfo = cgroupv2MntInfoNoZeroHierarchyOnlyFreezer.toString();
        String selfCgroup = cgroupv2SelfNoZeroHierarchyOnlyFreezer.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, selfCgroup, mountInfo);
        Asserts.assertEQ(CGROUPS_V2, retval, "All V1 controllers are ignored");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testNonZeroHierarchyOnlyFreezer PASSED!");
    }

    public void testCgroupv2ControllerFileEmpty(WhiteBox wb, Path mountInfo) {
        String sysFsCgroupCgroupControllers  = sysFsCgroupCgroupControllersEmptyPath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_V2, retval, "Empty cgroup v2 controllers file. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv2ControllerFileEmpty PASSED!");
    }

    public void testCgroupv2ControllerFileBlankLine(WhiteBox wb, Path mountInfo) {
        String sysFsCgroupCgroupControllers  = sysFsCgroupCgroupControllersBlankLinePath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_GENERIC, retval, "cgroup v2 controllers file contains a single blank line. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv2ControllerFileBlankLine PASSED!");
    }

    public void testCgroupv2ControllerFileNoMemory(WhiteBox wb, Path mountInfo) {
        String sysFsCgroupCgroupControllers  = sysFsCgroupCgroupControllersNoMemoryPath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_GENERIC, retval, "cgroup v2 memory controller disabled. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv2ControllerFileNoMemory PASSED!");
    }

    public void testCgroupv2ControllerFileNoCpu(WhiteBox wb, Path mountInfo) {
        String sysFsCgroupCgroupControllers  = sysFsCgroupCgroupControllersNoCpuPath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_GENERIC, retval, "cgroup v2 cpu controller disabled. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv2ControllerFileNoCpu PASSED!");
    }

    public void testCgroupv2ControllerFileNoPids(WhiteBox wb, Path mountInfo) {
        String sysFsCgroupCgroupControllers  = sysFsCgroupCgroupControllersNoPidsPath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V2, retval, "cgroup v2 pids controller disabled.  Valid.");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv2ControllerFileNoPids PASSED!");
    }

    public void testCgroupv2ControllerFileCpuMemoryOnly(WhiteBox wb, Path mountInfo) {
        String sysFsCgroupCgroupControllers  = sysFsCgroupCgroupControllersCpuMemoryOnlyPath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V2, retval, "only cgroup v2 memory and cpu controllers enabled.  Valid.");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv2ControllerFileCpuMemoryOnly PASSED!");
    }

    public void testCgroupv2ControllerFileExtraWhitespace(WhiteBox wb, Path mountInfo) {
        String sysFsCgroupCgroupControllers  = sysFsCgroupCgroupControllersExtraWhitespacePath.toString();
        String procSelfCgroup = cgroupV2SelfCgroup.toString();
        String procSelfMountinfo = mountInfo.toString();
        int retval = wb.validateCgroup(true, sysFsCgroupCgroupControllers, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(CGROUPS_V2, retval, "cgroup v2 controllers file contains extra whitespace.  Valid.");
        Asserts.assertTrue(isValidCgroup(retval));
        System.out.println("testCgroupv2ControllerFileExtraWhitespace PASSED!");
    }

    public void testCgroupv1CpusetDisabled(WhiteBox wb) {
        String procCgroups = procCgroupsCgroupsV1CpusetDisabledPath.toString();
        String procSelfCgroup = procSelfCgroupCgroupsV1CpusetDisabledPath.toString();
        String procSelfMountinfo = procSelfMountinfoCgroupsV1CpusetDisabledPath.toString();
        int retval = wb.validateCgroup(false, procCgroups, procSelfCgroup, procSelfMountinfo);
        Asserts.assertEQ(INVALID_CGROUPS_GENERIC, retval, "Required cpuset controller disabled in /proc/cgroups. Invalid.");
        Asserts.assertFalse(isValidCgroup(retval));
        System.out.println("testCgroupv1CpusetDisabled PASSED!");
    }

    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        CgroupSubsystemFactory test = new CgroupSubsystemFactory();
        test.setup();
        try {
            test.testCgroupv1SystemdOnly(wb);
            test.testCgroupv1NoMounts(wb);
            test.testCgroupv2(wb, test.cgroupv2MntInfoZeroHierarchy);
            test.testCgroupv2(wb, test.cgroupv2MntInfoDouble);
            test.testCgroupv2(wb, test.cgroupv2MntInfoDouble2);
            test.testCgroupv2ControllerFileEmpty(wb, test.cgroupv2MntInfoZeroHierarchy);
            test.testCgroupv2ControllerFileBlankLine(wb, test.cgroupv2MntInfoZeroHierarchy);
            test.testCgroupv2ControllerFileNoMemory(wb, test.cgroupv2MntInfoZeroHierarchy);
            test.testCgroupv2ControllerFileNoCpu(wb, test.cgroupv2MntInfoZeroHierarchy);
            test.testCgroupv2ControllerFileNoPids(wb, test.cgroupv2MntInfoZeroHierarchy);
            test.testCgroupv2ControllerFileCpuMemoryOnly(wb, test.cgroupv2MntInfoZeroHierarchy);
            test.testCgroupv2ControllerFileExtraWhitespace(wb, test.cgroupv2MntInfoZeroHierarchy);
            test.testCgroupV1Hybrid(wb);
            test.testCgroupV1HybridMntInfoOrder(wb);
            test.testCgroupv1MissingMemoryController(wb);
            test.testCgroupv2NoCgroup2Fs(wb);
            test.testCgroupv1MultipleControllerMounts(wb, test.cgroupv1MntInfoDoubleCpuset);
            test.testCgroupv1MultipleControllerMounts(wb, test.cgroupv1MntInfoDoubleCpuset2);
            test.testCgroupv1MultipleControllerMounts(wb, test.cgroupv1MntInfoDoubleMemory);
            test.testCgroupv1MultipleControllerMounts(wb, test.cgroupv1MntInfoDoubleMemory2);
            test.testCgroupv1MultipleControllerMounts(wb, test.cgroupv1MntInfoDoubleCpu);
            test.testCgroupv1MultipleControllerMounts(wb, test.cgroupv1MntInfoDoubleCpu2);
            test.testCgroupv1MultipleControllerMounts(wb, test.cgroupv1MntInfoDoublePids);
            test.testCgroupv1MultipleControllerMounts(wb, test.cgroupv1MntInfoDoublePids2);
            test.testCgroupv1JoinControllerCombo(wb);
            test.testNonZeroHierarchyOnlyFreezer(wb);
            test.testCgroupv1CpusetDisabled(wb);
        } finally {
            test.teardown();
        }
    }
}
