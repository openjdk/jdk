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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jdk.internal.platform.CgroupSubsystemFactory;
import jdk.internal.platform.CgroupSubsystemFactory.CgroupTypeResult;
import jdk.test.lib.Utils;
import jdk.test.lib.util.FileUtils;


/*
 * @test
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform
 * @library /test/lib
 * @run junit/othervm TestCgroupSubsystemFactory
 */
public class TestCgroupSubsystemFactory {

    private Path existingDirectory;
    private Path cgroupv1CgInfoZeroHierarchy;
    private Path cgroupv1MntInfoZeroHierarchy;
    private Path cgroupv2CgInfoZeroHierarchy;
    private Path cgroupv2MntInfoZeroHierarchy;
    private Path cgroupv1CgInfoNonZeroHierarchy;
    private Path cgroupv1MntInfoNonZeroHierarchy;
    private String mntInfoEmpty = "";
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
    private String mntInfoHybrid =
            "30 23 0:26 / /sys/fs/cgroup ro,nosuid,nodev,noexec shared:4 - tmpfs tmpfs ro,seclabel,mode=755\n" +
            "31 30 0:27 / /sys/fs/cgroup/unified rw,nosuid,nodev,noexec,relatime shared:5 - cgroup2 cgroup2 rw,seclabel,nsdelegate\n" +
            "32 30 0:28 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime shared:6 - cgroup cgroup rw,seclabel,xattr,name=systemd\n" +
            "35 30 0:31 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:7 - cgroup cgroup rw,seclabel,memory\n" +
            "36 30 0:32 / /sys/fs/cgroup/pids rw,nosuid,nodev,noexec,relatime shared:8 - cgroup cgroup rw,seclabel,pids\n" +
            "37 30 0:33 / /sys/fs/cgroup/perf_event rw,nosuid,nodev,noexec,relatime shared:9 - cgroup cgroup rw,seclabel,perf_event\n" +
            "38 30 0:34 / /sys/fs/cgroup/net_cls,net_prio rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,net_cls,net_prio\n" +
            "39 30 0:35 / /sys/fs/cgroup/hugetlb rw,nosuid,nodev,noexec,relatime shared:11 - cgroup cgroup rw,seclabel,hugetlb\n" +
            "40 30 0:36 / /sys/fs/cgroup/cpu,cpuacct rw,nosuid,nodev,noexec,relatime shared:12 - cgroup cgroup rw,seclabel,cpu,cpuacct\n" +
            "41 30 0:37 / /sys/fs/cgroup/devices rw,nosuid,nodev,noexec,relatime shared:13 - cgroup cgroup rw,seclabel,devices\n" +
            "42 30 0:38 / /sys/fs/cgroup/cpuset rw,nosuid,nodev,noexec,relatime shared:14 - cgroup cgroup rw,seclabel,cpuset\n" +
            "43 30 0:39 / /sys/fs/cgroup/blkio rw,nosuid,nodev,noexec,relatime shared:15 - cgroup cgroup rw,seclabel,blkio\n" +
            "44 30 0:40 / /sys/fs/cgroup/freezer rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup rw,seclabel,freezer";
    private String cgroupsNonZeroHierarchy =
            "#subsys_name hierarchy   num_cgroups enabled\n" +
            "cpuset  9   1   1\n" +
            "cpu 7   1   1\n" +
            "cpuacct 7   1   1\n" +
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

    @Before
    public void setup() {
        try {
            existingDirectory = Utils.createTempDirectory(TestCgroupSubsystemFactory.class.getSimpleName());
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void teardown() {
        try {
            FileUtils.deleteFileTreeWithRetry(existingDirectory);
        } catch (IOException e) {
            System.err.println("Teardown failed. " + e.getMessage());
        }
    }

    @Test
    public void testHybridCgroupsV1() throws IOException {
        String cgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String mountInfo = cgroupv1MntInfoNonZeroHierarchy.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("Expected non-empty cgroup result", result.isPresent());
        CgroupTypeResult res = result.get();
        assertFalse("hybrid hierarchy expected as cgroups v1", res.isCgroupV2());
    }

    @Test
    public void testZeroHierarchyCgroupsV1() throws IOException {
        String cgroups = cgroupv1CgInfoZeroHierarchy.toString();
        String mountInfo = cgroupv1MntInfoZeroHierarchy.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("zero hierarchy ids with no mounted controllers => empty result", result.isEmpty());
    }

    @Test
    public void testZeroHierarchyCgroupsV2() throws IOException {
        String cgroups = cgroupv2CgInfoZeroHierarchy.toString();
        String mountInfo = cgroupv2MntInfoZeroHierarchy.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("Expected non-empty cgroup result", result.isPresent());
        CgroupTypeResult res = result.get();

        assertTrue("zero hierarchy ids with mounted controllers expected cgroups v2", res.isCgroupV2());
    }

    @Test(expected = IOException.class)
    public void mountInfoFileNotFound() throws IOException {
        String cgroups = cgroupv1CgInfoZeroHierarchy.toString(); // any existing file
        String mountInfo = Paths.get(existingDirectory.toString(), "not-existing-mountinfo").toString();

        CgroupSubsystemFactory.determineType(mountInfo, cgroups);
    }

    @Test(expected = IOException.class)
    public void cgroupsFileNotFound() throws IOException {
        String cgroups = Paths.get(existingDirectory.toString(), "not-existing-cgroups").toString();
        String mountInfo = cgroupv2MntInfoZeroHierarchy.toString(); // any existing file
        CgroupSubsystemFactory.determineType(mountInfo, cgroups);
    }
}
