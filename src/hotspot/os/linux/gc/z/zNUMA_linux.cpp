/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/integerCast.hpp"

// Converts between ZGC NUMA ids and Linux NUMA node ids.
//
// A ZGC NUMA id is a dense zero-based index over the NUMA nodes that ZGC can
// allocate from. For example, with two available NUMA nodes, ids 0 and 1 are
// tracked.
//
// A Linux NUMA node id is the number used by native Linux NUMA APIs. These node
// ids usually reflect the hardware configuration, can be sparse, and do not
// have to start at 0.
class ZNUMAConverter {
private:
  bool      _initialized = false;

  uint*     _id_to_node = nullptr;
  uint32_t  _id_to_node_size = 0;

  uint32_t* _node_to_id = nullptr;
  size_t    _node_to_id_size = 0;

  void populate_id_mappings() {
    const int configured_nodes_limit = os::Linux::numa_num_configured_nodes();
    assert(configured_nodes_limit > 0, "Invalid number of configured NUMA nodes: %d", configured_nodes_limit);

    if (configured_nodes_limit <= 0) {
      vm_exit_during_initialization("Cannot determine number of available NUMA nodes. Run without NUMA using -XX:-UseNUMA");
    }

    // Allocate and populate mapping array (id -> node)
    _id_to_node = NEW_C_HEAP_ARRAY(uint, (size_t)configured_nodes_limit, mtGC);
    const size_t available_nodes = os::numa_get_leaf_groups(_id_to_node, (size_t)configured_nodes_limit);

    assert(available_nodes <= (size_t)configured_nodes_limit,
           "Too many NUMA nodes: %zu <= %d", available_nodes, configured_nodes_limit);

    _id_to_node_size = integer_cast<uint32_t>(MIN2(available_nodes, (size_t)configured_nodes_limit));
  }

  void populate_node_mappings() {
    assert(_id_to_node != nullptr, "id-to-node mapping must be populated first");

    const int max_node = os::Linux::numa_max_node();
    assert(max_node >= 0, "Invalid highest NUMA node: %d", max_node);

    if (max_node < 0) {
      vm_exit_during_initialization("Cannot determine the NUMA max node. Run without NUMA using -XX:-UseNUMA");
    }

    _node_to_id_size = (size_t)max_node + 1;

    // Allocate mapping array (node -> id)
    _node_to_id = NEW_C_HEAP_ARRAY(uint32_t, _node_to_id_size, mtGC);

    // Fill the array with invalid ids
    for (size_t i = 0; i < _node_to_id_size; i++) {
      _node_to_id[i] = (uint32_t)-1;
    }

    // Fill the reverse mappings
    for (uint32_t i = 0; i < _id_to_node_size; i++) {
      const uint node = _id_to_node[i];
      assert(node < _node_to_id_size, "NUMA node is out of bounds node=%u, max=%zu", node, _node_to_id_size);
      _node_to_id[node] = i;
    }
  }

public:
  void initialize() {
    precond(!_initialized);
    precond(UseNUMA);

    populate_id_mappings();
    populate_node_mappings();

    _initialized = true;
  }

  uint32_t count() const {
    precond(_initialized);
    return _id_to_node_size;
  }

  uint32_t node_to_id(int node) const {
    precond(_initialized);
    assert(node >= 0, "Invalid NUMA node: %d", node);
    assert((size_t)node < _node_to_id_size, "NUMA node is out of bounds node=%d, max=%zu", node, _node_to_id_size);

    if (node < 0 || (size_t)node >= _node_to_id_size) {
      return (uint32_t)-1;
    }

    return _node_to_id[node];
  }

  int id_to_node(uint32_t id) {
    precond(_initialized);
    assert(id < count(), "NUMA id out of range 0 <= %ud <= %ud", id, count());

    return (int)_id_to_node[id];
  }
};

static ZNUMAConverter z_numa_converter;

void ZNUMA::pd_initialize() {
  _enabled = UseNUMA;

  if (UseNUMA) {
    z_numa_converter.initialize();
    _count = z_numa_converter.count();
  } else {
    // UseNUMA and is_faked() are mutually excluded in zArguments.cpp.
    _count = !FLAG_IS_DEFAULT(ZFakeNUMA)
        ? ZFakeNUMA
        : 1; // No NUMA nodes
  }
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

  const uint32_t id = z_numa_converter.node_to_id(os::Linux::get_node_by_cpu(ZCPU::id()));
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

  return z_numa_converter.node_to_id(node);
}

int ZNUMA::numa_id_to_node(uint32_t numa_id) {
  return z_numa_converter.id_to_node(numa_id);
}
