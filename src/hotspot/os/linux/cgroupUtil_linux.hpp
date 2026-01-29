/*
 * Copyright (c) 2024, Red Hat, Inc.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef CGROUP_UTIL_LINUX_HPP
#define CGROUP_UTIL_LINUX_HPP

#include "cgroupSubsystem_linux.hpp"
#include "utilities/globalDefinitions.hpp"

class CgroupUtil: AllStatic {

  public:
    static bool processor_count(CgroupCpuController* cpu, int upper_bound, double& value);
    // Given a memory controller, adjust its path to a point in the hierarchy
    // that represents the closest memory limit.
    static void adjust_controller(CgroupMemoryController* m);
    // Given a cpu controller, adjust its path to a point in the hierarchy
    // that represents the closest cpu limit.
    static void adjust_controller(CgroupCpuController* c);
  private:
    static physical_memory_size_type get_updated_mem_limit(CgroupMemoryController* m,
                                                           physical_memory_size_type lowest,
                                                           physical_memory_size_type upper_bound);
    static double get_updated_cpu_limit(CgroupCpuController* c, int lowest, int upper_bound);
};

#endif // CGROUP_UTIL_LINUX_HPP
