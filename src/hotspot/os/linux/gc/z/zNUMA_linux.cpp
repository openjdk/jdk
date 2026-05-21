/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zCPU.inline.hpp"
#include "gc/z/zErrno.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zSyscall_linux.hpp"
#include "os_linux.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"

static uint* z_numa_id_to_node = nullptr;
static uint32_t* z_node_to_numa_id = nullptr;
static size_t z_node_to_numa_id_size = 0;

static uint32_t z_numa_id_from_node(int node) {
  assert(node >= 0, "Invalid NUMA node: %d", node);
  assert((size_t)node < z_node_to_numa_id_size,
         "NUMA node is out of bounds node=%d, max=%zu", node, z_node_to_numa_id_size);

  if (node < 0 || (size_t)node >= z_node_to_numa_id_size) {
    return (uint32_t)-1;
  }

  return z_node_to_numa_id[node];
}

void ZNUMA::pd_initialize() {
  _enabled = UseNUMA;

  uint32_t configured_nodes = 0;

  if (UseNUMA) {
    const int max_node = os::Linux::numa_max_node();
    const int configured_nodes_limit = os::Linux::numa_num_configured_nodes();
    assert(max_node >= 0, "Invalid highest NUMA node: %d", max_node);
    assert(configured_nodes_limit > 0, "Invalid number of configured NUMA nodes: %d", configured_nodes_limit);

    const size_t node_id_limit = (size_t)max_node + 1;
    const size_t configured_node_count = (size_t)configured_nodes_limit;
    z_node_to_numa_id_size = node_id_limit;

    z_numa_id_to_node = NEW_C_HEAP_ARRAY(uint, configured_node_count, mtGC);
    const size_t available_nodes = os::numa_get_leaf_groups(z_numa_id_to_node, configured_node_count);
    assert(available_nodes <= configured_node_count,
           "Too many NUMA nodes: %zu <= %d", available_nodes, configured_nodes_limit);
    configured_nodes = checked_cast<uint32_t>(MIN2(available_nodes, configured_node_count));

    z_node_to_numa_id = NEW_C_HEAP_ARRAY(uint32_t, node_id_limit, mtGC);

    // Fill the array with invalid NUMA ids
    for (size_t i = 0; i < node_id_limit; i++) {
      z_node_to_numa_id[i] = (uint32_t)-1;
    }

    // Fill the reverse mappings
    for (uint32_t i = 0; i < configured_nodes; i++) {
      assert(z_numa_id_to_node[i] < node_id_limit,
             "NUMA node is out of bounds node=%u, max=%zu", z_numa_id_to_node[i], node_id_limit);
      z_node_to_numa_id[z_numa_id_to_node[i]] = i;
    }
  }

  // UseNUMA and is_faked() are mutually excluded in zArguments.cpp.
  _count = UseNUMA
      ? configured_nodes
      : !FLAG_IS_DEFAULT(ZFakeNUMA)
            ? ZFakeNUMA
            : 1;  // No NUMA nodes
}

uint32_t ZNUMA::id() {
  if (is_faked()) {
    // ZFakeNUMA testing, ignores _enabled
    return ZCPU::id() % ZFakeNUMA;
  }

  if (!_enabled) {
    // NUMA support not enabled
    return 0;
  }

  const uint32_t id = z_numa_id_from_node(os::Linux::get_node_by_cpu(ZCPU::id()));
  assert(id != (uint32_t)-1, "Unknown NUMA node");
  return id;
}

uint32_t ZNUMA::memory_id(uintptr_t addr) {
  if (!_enabled) {
    // NUMA support not enabled, assume everything belongs to node zero
    return 0;
  }

  int node = -1;

  if (ZSyscall::get_mempolicy(&node, nullptr, 0, (void*)addr, MPOL_F_NODE | MPOL_F_ADDR) == -1) {
    ZErrno err;
    fatal("Failed to get NUMA id for memory at " PTR_FORMAT " (%s)", addr, err.to_string());
  }

  return z_numa_id_from_node(node);
}

int ZNUMA::numa_id_to_node(uint32_t numa_id) {
  assert(numa_id < _count, "NUMA id out of range 0 <= %ud <= %ud", numa_id, _count);

  return (int)z_numa_id_to_node[numa_id];
}
