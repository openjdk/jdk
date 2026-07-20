/*
 * Copyright (c) 2022, 2025, Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import jdk.internal.platform.cgroupv1.CgroupV1SubsystemController;

/*
 * @test
 * @key cgroups
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform.cgroupv1
 * @library /test/lib
 * @run junit/othervm CgroupV1SubsystemControllerTest
 */
public class CgroupV1SubsystemControllerTest {


    /*
     * Common case: Containers
     */
    @Test
    public void testCgPathEqualsRoot() {
        String root = "/machine.slice/libpod-7145e2e7dbeab5aa96bd79beed79eda286a2d299a0ee386e704cad9f53a70979.scope";
        String mountPoint = "/somemount";
        CgroupV1SubsystemController ctrl = new CgroupV1SubsystemController(root, mountPoint);
        ctrl.setPath("/machine.slice/libpod-7145e2e7dbeab5aa96bd79beed79eda286a2d299a0ee386e704cad9f53a70979.scope");
        assertEquals(mountPoint, ctrl.path());
    }

    /*
     * Common case: Host
     */
    @Test
    public void testCgPathNonEmptyRoot() {
        String root = "/";
        String mountPoint = "/sys/fs/cgroup/memory";
        CgroupV1SubsystemController ctrl = new CgroupV1SubsystemController(root, mountPoint);
        String cgroupPath = "/subpath";
        ctrl.setPath(cgroupPath);
        String expectedPath = mountPoint + cgroupPath;
        assertEquals(expectedPath, ctrl.path());
    }

    /*
     * Less common cases: Containers
     */
    @Test
    public void testCgPathSubstring() {
        String root = "/foo/bar/baz";
        String mountPoint = "/sys/fs/cgroup/memory";
        CgroupV1SubsystemController ctrl = new CgroupV1SubsystemController(root, mountPoint);
        String cgroupPath = "/foo/bar/baz/some";
        ctrl.setPath(cgroupPath);
        String expectedPath = mountPoint;
        assertEquals(expectedPath, ctrl.path());
    }

    @Test
    public void testCgPathToMovedPath() {
        String root = "/system.slice/garden.service/garden/good/2f57368b-0eda-4e52-64d8-af5c";
        String mountPoint = "/sys/fs/cgroup/cpu,cpuacct";
        CgroupV1SubsystemController ctrl = new CgroupV1SubsystemController(root, mountPoint);
        String cgroupPath = "/system.slice/garden.service/garden/bad/2f57368b-0eda-4e52-64d8-af5c";
        ctrl.setPath(cgroupPath);
        String expectedPath = mountPoint;
        assertEquals(expectedPath, ctrl.path());
    }
}
