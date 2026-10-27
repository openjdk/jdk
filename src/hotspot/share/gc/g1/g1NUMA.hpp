/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1NUMA_HPP
#define SHARE_GC_G1_G1NUMA_HPP

#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1NUMAStats.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"

class LogStream;

// Provides helpers for NUMA support.
//
// * mapping between preferred OS NUMA node ids and an internal zero-based index and back.
//
//   Preferred OS NUMA node ids for the current process may be sparse sets of integers. To avoid
//   unnecessary pollution of caches, we map these to internal indexes, that are then used to access
//   internal NUMA data structures.
//
// * finding internal node index for a given G1HeapRegion or an address in the Java heap.
//
// * assigning memory to preferred NUMA nodes
//
// * statistics
//
class G1NUMA: public CHeapObj<mtGC> {
  // Mapping of preferred OS NUMA node ids to a zero-based index which is internally used for
  // faster resource management.
  // Allowed OS NUMA node ids may be more than the preferred ones so this array conservatively covers
  // all possible OS NUMA node ids. OS NUMA node ids outside the preferred set map to UnknownNodeIndex.
  uint* _node_id_to_index_map;
  // Maximum number of (OS) NUMA nodes in the system, size of the _node_id_to_index_map.
  uint _max_num_node_ids;

  // Current active node ids.
  uint* _node_ids;
  // Total number of node ids.
  uint _num_active_node_ids;

  // G1HeapRegion size
  size_t _region_size;
  // Necessary when touching memory.
  size_t _page_size;

  // Stores statistic data.
  G1NUMAStats* _stats;

  size_t region_size() const;
  size_t page_size() const;

  // Returns node index of the given node id.
  inline uint index_of_node_id(uint node_id) const;

  static G1NUMA* _inst;

  G1NUMA();
  void initialize(bool use_numa);
  void initialize_without_numa();

public:
  static const uint UnknownNodeIndex = UINT_MAX;
  static const uint AnyNodeIndex = UnknownNodeIndex - 1;

  static G1NUMA* numa() { return _inst; }

  static G1NUMA* create();

  ~G1NUMA();

  // Sets heap region size and page size after those values
  // are determined at G1CollectedHeap::initialize().
  void set_region_info(size_t region_size, size_t page_size);

  // Returns active memory node count.
  uint num_active_nodes() const;

  bool is_enabled() const;

  uint numa_id(uint index) const;

  // Returns memory node ids
  const uint* node_ids() const;

  // Returns node index of current calling thread.
  uint index_of_current_thread() const;

  // Returns the preferred index for the given G1HeapRegion index.
  // This assumes that heap regions are evenly spit, so we can decide preferred index
  // with the given G1HeapRegion index.
  // Result is less than num_active_nodes().
  uint preferred_node_index_for_index(uint region_index) const;

  // Retrieves node index of the given address.
  // Result is less than num_active_nodes() or is UnknownNodeIndex.
  // Precondition: address is in reserved range for heap.
  uint index_of_address(HeapWord* address) const;

  // If AlwaysPreTouch is enabled, return actual node index via system call.
  // If disabled, return preferred node index of the given heap region.
  uint index_for_region(G1HeapRegion* hr) const;

  // Requests the given memory area to be located at the given node index.
  void request_memory_on_node(void* aligned_address, size_t size_in_bytes, uint region_index);

  // Returns maximum search depth which is used to limit heap region search iterations.
  // The number of active nodes, page size and heap region size are considered.
  uint max_search_depth() const;

  // Update the given phase of requested and allocated node index.
  void update_statistics(G1NUMAStats::NodeDataItems phase, uint requested_node_index, uint allocated_node_index);

  // Copy all allocated statistics of the given phase and requested node.
  // Precondition: allocated_stat should have same length of active nodes.
  void copy_statistics(G1NUMAStats::NodeDataItems phase, uint requested_node_index, size_t* allocated_stat);

  // Print all statistics.
  void print_statistics() const;
};

class G1NodeIndexCheckClosure : public G1HeapRegionClosure {
  const char* _desc;
  G1NUMA* _numa;
  // Records matched count of each node.
  uint* _matched;
  // Records mismatched count of each node.
  uint* _mismatched;
  // Records total count of each node.
  // Total = matched + mismatched + unknown.
  uint* _total;
  LogStream* _ls;

public:
  G1NodeIndexCheckClosure(const char* desc, G1NUMA* numa, LogStream* ls);
  ~G1NodeIndexCheckClosure();

  bool do_heap_region(G1HeapRegion* hr);
};

#endif // SHARE_GC_G1_G1NUMA_HPP
