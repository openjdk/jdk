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

#include "precompiled.hpp"

#ifdef LINUX

#include "cgroupV1Subsystem_linux.hpp"
#include "unittest.hpp"

typedef struct {
  const char* mount_path;
  const char* root_path;
  const char* cgroup_path;
  const char* expected_path;
} TestCase;

TEST(os_linux_cgroup, set_cgroup1_subsystem_path) {
  TestCase host = {
    "/sys/fs/cgroup/memory",                                             // mount_path
    "/",                                                                 // root_path
    "/user.slice/user-1000.slice/user@1000.service",                     // cgroup_path
    "/sys/fs/cgroup/memory/user.slice/user-1000.slice/user@1000.service" // expected_path
  };
  TestCase container_engine = {
    "/sys/fs/cgroup/mem",                            // mount_path
    "/user.slice/user-1000.slice/user@1000.service", // root_path
    "/user.slice/user-1000.slice/user@1000.service", // cgroup_path
    "/sys/fs/cgroup/mem"                             // expected_path
  };
  TestCase prefix_matched_cg = {
    "/sys/fs/cgroup/memory",                           // mount_path
    "/user.slice/user-1000.slice/session-50.scope",    // root_path
    "/user.slice/user-1000.slice/session-3.scope",     // cgroup_path
    "/sys/fs/cgroup/memory/user.slice/user-1000.slice" // expected_path
  };
  TestCase substring_match = {
    "/sys/fs/cgroup/memory",                           // mount_path
    "/user.slice/user-1000.slice",                     // root_path
    "/user.slice/user-1000.slice/user@1001.service",   // cgroup_path
    "/sys/fs/cgroup/memory/user@1001.service"          // expected_path
  };
  TestCase root_only_match = {
    "/sys/fs/cgroup/m",                           // mount_path
    "/machine.slice/user-2002.slice",             // root_path
    "/user.sl/user-3000.slice/user@3001.service", // cgroup_path
    "/sys/fs/cgroup/m"                            // expected_path
  };
  int length = 5;
  TestCase* testCases[] = { &host,
                            &container_engine,
                            &prefix_matched_cg,
                            &substring_match,
                            &root_only_match };
  for (int i = 0; i < length; i++) {
    CgroupV1Controller* ctrl = new CgroupV1Controller( (char*)testCases[i]->root_path,
                                                       (char*)testCases[i]->mount_path);
    ctrl->set_subsystem_path((char*)testCases[i]->cgroup_path);
    ASSERT_STREQ(testCases[i]->expected_path, ctrl->subsystem_path());
  }
}

#endif
