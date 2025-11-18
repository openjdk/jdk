/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_OSCONTAINER_LINUX_HPP
#define OS_LINUX_OSCONTAINER_LINUX_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

// Some cgroup interface files define the value 'max' for unlimited.
// Define this constant value to indicate this value.
const uint64_t value_unlimited = std::numeric_limits<uint64_t>::max();

// 20ms timeout between re-reads of memory limit and _active_processor_count.
#define OSCONTAINER_CACHE_TIMEOUT (NANOSECS_PER_SEC/50)

// Carrier object for print_container_helper()
class MetricResult: public StackObj {
  private:
    static const uint64_t value_unused = 0;
    bool _success = false;
    physical_memory_size_type _value = value_unused;
  public:
    void set_value(physical_memory_size_type val) {
      // having a value means success
      _success = true;
      _value = val;
    }

    bool success() { return _success; }
    physical_memory_size_type value() { return _value; }
};

class OSContainer: AllStatic {

 private:
  static bool   _is_initialized;
  static bool   _is_containerized;
  static int    _active_processor_count;

 public:
  static void init();
  static void print_version_specific_info(outputStream* st);
  static void print_container_helper(outputStream* st, MetricResult& res, const char* metrics);

  static inline bool is_containerized();
  static const char * container_type();

  static bool available_memory_in_bytes(physical_memory_size_type& value);
  static bool available_swap_in_bytes(physical_memory_size_type host_free_swap,
                                      physical_memory_size_type& value);
  static bool memory_limit_in_bytes(physical_memory_size_type& value);
  static bool memory_and_swap_limit_in_bytes(physical_memory_size_type& value);
  static bool memory_and_swap_usage_in_bytes(physical_memory_size_type& value);
  static bool memory_soft_limit_in_bytes(physical_memory_size_type& value);
  static bool memory_throttle_limit_in_bytes(physical_memory_size_type& value);
  static bool memory_usage_in_bytes(physical_memory_size_type& value);
  static bool memory_max_usage_in_bytes(physical_memory_size_type& value);
  static bool rss_usage_in_bytes(physical_memory_size_type& value);
  static bool cache_usage_in_bytes(physical_memory_size_type& value);

  static bool active_processor_count(int& value);

  static char * cpu_cpuset_cpus();
  static char * cpu_cpuset_memory_nodes();

  static bool cpu_quota(int& value);
  static bool cpu_period(int& value);

  static bool cpu_shares(int& value);

  static bool cpu_usage_in_micros(uint64_t& value);

  static bool pids_max(uint64_t& value);
  static bool pids_current(uint64_t& value);
};

inline bool OSContainer::is_containerized() {
  return _is_containerized;
}

#endif // OS_LINUX_OSCONTAINER_LINUX_HPP
