/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef NMT_VIRTUALMEMORYTRACKER_HPP
#define NMT_VIRTUALMEMORYTRACKER_HPP

#include "nmt/nmtCommon.hpp"
#include "nmt/regionsTree.hpp"
#include "runtime/atomic.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

// VirtualMemoryTracker (VMT) is the internal class of NMT that only the MemTracker class uses it for performing the NMT operations.
// All the Hotspot code use only the MemTracker interface to register the memory operations in NMT.
//
// VMT design:
// - It has a AllStatic member Instance that is used statically
// - It is also possible to create new instances of VMT to use it in a certain workflow, e.g. for tests.
// - The memory addresses of regions are held and managed in a tree (RegionsTree -> VMATree).
// - RegionsTree has methods for visiting reserved-regions and also visiting committed regions of a memory section.
//   These methods use the VMATree methods of `visit_in_order` and `visit_range_in_order` to fulfil the task.
// - The memory operations of Reserve/Commit/Uncommit/Release (RCUR) are tracked by updating the underlying tree.
//   The RCUR request is done by the RegionsTree class and all the changes of reserved/committed of the affected
//   regions are returned back via a SummaryDiff structure. This structure contains for each memory tag the amounts
//   of changes in reserved and committed of each memory tag that to be added or subtracted. These amounts are directly
//   applied to the VirtualMemorySummary of NMT.
// - Memory tag of a memory region can be changed by calling `set_reserved_region_tag` method which in turn calls the
//   set_tag of VMATree and applies the changes.
// - In the current implementation, all the RCUR operations are allowed with no restriction and in any order, as
//   long as the following predicates are true:
//     - committed size of a memory tag is <= its reserved size
//     - uncommitted size of a memory tag is <= its committed size
//     - released size of a memory tag is <= its reserved size
// - In future expansion of VMT:
//   - if it is desired to restrict the RCUR operations, the RegionsTree/VMATree classes
//     need to provide corresponding API to:
//       - distinguish RCUR ops based on whether a memory tag and/or call-stack are provided or not.
//       - check validity of the requested RCUR with the existing state of the memory regions in the tree.
//   - potential restrictions can be listed as:
//     - reserving an already committed region
//     - committing (when tag+stack are provided) an already committed region
//     - committing (when tag+stack are not provided) an already released region



class VirtualMemoryTracker {
 private:
  RegionsTree _tree;

 public:
  VirtualMemoryTracker(bool is_detailed_mode) : _tree(is_detailed_mode) { }

  bool add_reserved_region       (address base_addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone);
  bool add_committed_region      (address base_addr, size_t size, const NativeCallStack& stack);
  bool remove_uncommitted_region (address base_addr, size_t size);
  bool remove_released_region    (address base_addr, size_t size);
  bool remove_released_region    (ReservedMemoryRegion* rgn);
  void set_reserved_region_tag   (address addr, size_t size, MemTag mem_tag);

  // Given an existing memory mapping registered with NMT, split the mapping in
  //  two. The newly created two mappings will be registered under the call
  //  stack and the memory tags of the original section.
  bool split_reserved_region(address addr, size_t size, size_t split, MemTag mem_tag, MemTag split_mem_tag);

  // Walk virtual memory data structure for creating baseline, etc.
  bool walk_virtual_memory(VirtualMemoryWalker* walker);

  // If p is contained within a known memory region, print information about it to the
  // given stream and return true; false otherwise.
  bool print_containing_region(const void* p, outputStream* st);

  // Snapshot current thread stacks
  void snapshot_thread_stacks();
  void apply_summary_diff(VMATree::SummaryDiff diff);
  RegionsTree* tree() { return &_tree; }

  class Instance : public AllStatic {
    friend class VirtualMemoryTrackerTest;
    friend class CommittedVirtualMemoryTest;

    static VirtualMemoryTracker* _tracker;

   public:
    using RegionData = VMATree::RegionData;
    static bool initialize(NMT_TrackingLevel level);

    static bool add_reserved_region       (address base_addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone);
    static bool add_committed_region      (address base_addr, size_t size, const NativeCallStack& stack);
    static bool remove_uncommitted_region (address base_addr, size_t size);
    static bool remove_released_region    (address base_addr, size_t size);
    static bool remove_released_region    (ReservedMemoryRegion* rgn);
    static void set_reserved_region_tag   (address addr, size_t size, MemTag mem_tag);
    static bool split_reserved_region(address addr, size_t size, size_t split, MemTag mem_tag, MemTag split_mem_tag);
    static bool walk_virtual_memory(VirtualMemoryWalker* walker);
    static bool print_containing_region(const void* p, outputStream* st);
    static void snapshot_thread_stacks();
    static void apply_summary_diff(VMATree::SummaryDiff diff);

    static RegionsTree* tree() { return _tracker->tree(); }
  };
};

#endif // NMT_VIRTUALMEMORYTRACKER_HPP