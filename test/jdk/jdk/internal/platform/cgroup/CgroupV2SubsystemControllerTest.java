/*
 * Copyright (c) 2022, Red Hat, Inc.
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

import jdk.internal.platform.cgroupv2.CgroupV2SubsystemController;

/*
 * @test
 * @key cgroups
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform.cgroupv2
 * @library /test/lib
 * @run junit/othervm CgroupV2SubsystemControllerTest
 */
public class CgroupV2SubsystemControllerTest {


    /*
     * Common case: No nested cgroup path (i.e. at the unified root)
     */
    @Test
    public void testCgPathAtRoot() {
        String mountPoint = "/sys/fs/cgroup";
        String cgroupPath = "/";
        CgroupV2SubsystemController ctrl = new CgroupV2SubsystemController(mountPoint, cgroupPath);
        assertEquals(mountPoint, ctrl.path());
    }

    /*
     * Cgroup path at a sub-path
     */
    @Test
    public void testCgPathNonEmptyRoot() {
        String mountPoint = "/sys/fs/cgroup";
        String cgroupPath = "/foobar";
        CgroupV2SubsystemController ctrl = new CgroupV2SubsystemController(mountPoint, cgroupPath);
        String expectedPath = mountPoint + cgroupPath;
        assertEquals(expectedPath, ctrl.path());
    }

}
