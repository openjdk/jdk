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

TEST(os_linux_cgroup, set_cgroup1_subsystem_path) {
  int length = 5;
  const char* mount_paths[] =     { "/sys/fs/cgroup/memory",
                                    "/sys/fs/cgroup/memory", /* non-matched cg path  */
                                    "/sys/fs/cgroup/mem",    /* root matched cg path */
                                    "/sys/fs/cgroup/memory", /* substring match      */
                                    "/sys/fs/cgroup/m"       /* non-matched cg path  */
                                  };
  const char* root_paths[] =      { "/",
                                    "/user.slice/user-1000.slice/session-50.scope",  /* non-matched cg path  */
                                    "/user.slice/user-1000.slice/user@1000.service", /* root matched cg path */
                                    "/user.slice/user-1000.slice",                   /* substring match */
                                    "/machine.slice/user-2002.slice"                 /* root match only */
                                  };
  const char* cgroup_paths[] =    { "/user.slice/user-1000.slice/user@1000.service",
                                    "/user.slice/user-1000.slice/session-3.scope",   /* non-matched cg path  */
                                    "/user.slice/user-1000.slice/user@1000.service", /* root matched cg path */
                                    "/user.slice/user-1000.slice/user@1001.service", /* substring match */
                                    "/user.sl/user-3000.slice/user@3001.service"     /* root match only */
                                  };
  const char* expected_cg_paths[] { "/sys/fs/cgroup/memory/user.slice/user-1000.slice/user@1000.service",
                                    "/sys/fs/cgroup/memory/user.slice/user-1000.slice", /* closest substring match */
                                    "/sys/fs/cgroup/mem",                               /* root matched cg path    */
                                    "/sys/fs/cgroup/memory/user@1001.service",          /* substring match */
                                    "/sys/fs/cgroup/m"                                  /* root match only */
                                  };

  for (int i = 0; i < length; i++) {
    CgroupV1Controller* ctrl = new CgroupV1Controller((char*)root_paths[i], (char*)mount_paths[i]);
    ctrl->set_subsystem_path((char*)cgroup_paths[i]);
    ASSERT_STREQ(expected_cg_paths[i], ctrl->subsystem_path());
  }
}

#endif
