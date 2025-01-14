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
// Memory regions can be reserved/committed/uncommitted/released by calling MemTracker API which in turn call the corresponding functions in VMT.
// VMT uses RegionsTree to hold and manage the memory regions. Each region has two nodes that each one has address of the region (start/end) and
// state (reserved/released/committed) and MemTag of the regions before and after it.
//
// The memory operations of Reserve/Commit/Uncommit/Release (RCUR) are tracked by updating/inserting/deleting the nodes in the tree. When an operation
// changes nodes in the tree, the summary of the changes is returned back in a SummaryDiff struct. This struct shows that how much reserve/commit amount
// of any specific MemTag is changed. The summary of every operation is accumulated in VirtualMemorySummary class.
//
// Not all operations are valid in VMT. The following predicates are checked before the operation is applied to the tree nad/or VirtualMemorySummary:
//   - committed size of a MemTag should be <= of its reserved size
//   - uncommitted size of a MemTag should be <= of its committed size
//   - released size of a MemTag should be <= of its reserved size
//   - reserving an already reserved/committed region is not valid

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