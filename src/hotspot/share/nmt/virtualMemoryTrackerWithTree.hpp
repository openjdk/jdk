/*
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

#ifndef NMT_VIRTUALMEMORYTRACKERWITHTREE_HPP
#define NMT_VIRTUALMEMORYTRACKERWITHTREE_HPP

#include "nmt/nmtCommon.hpp"
#include "nmt/regionsTree.hpp"
#include "runtime/atomic.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

class VirtualMemoryTrackerWithTree {
 private:
  RegionsTree* _tree;

 public:
  VirtualMemoryTrackerWithTree(bool is_detailed_mode) {
    _tree = new RegionsTree(is_detailed_mode);
  }
  bool add_reserved_region (address base_addr, size_t size, const NativeCallStack& stack, MEMFLAGS flag = mtNone);
  bool add_committed_region      (address base_addr, size_t size, const NativeCallStack& stack);
  bool remove_uncommitted_region (address base_addr, size_t size);
  bool remove_released_region    (address base_addr, size_t size);
  bool remove_released_region    (ReservedMemoryRegion* rgn);
  void set_reserved_region_type  (address addr, MEMFLAGS flag);

  // Given an existing memory mapping registered with NMT, split the mapping in
  //  two. The newly created two mappings will be registered under the call
  //  stack and the memory flags of the original section.
  bool split_reserved_region(address addr, size_t size, size_t split, MEMFLAGS flag, MEMFLAGS split_flag);

  // Walk virtual memory data structure for creating baseline, etc.
  bool walk_virtual_memory(VirtualMemoryWalker* walker);

  // If p is contained within a known memory region, print information about it to the
  // given stream and return true; false otherwise.
  bool print_containing_region(const void* p, outputStream* st);

  // Snapshot current thread stacks
  void snapshot_thread_stacks();
  void apply_summary_diff(VMATree::SummaryDiff diff);
  RegionsTree* tree() { return _tree; }
  class Instance : public AllStatic {
    friend class VirtualMemoryTrackerTest;
    friend class CommittedVirtualMemoryTest;
    friend class ReservedMemoryRegion;

   private:
    static VirtualMemoryTrackerWithTree* _tracker;

   public:
    using RegionData = VMATree::RegionData;
    static bool initialize(NMT_TrackingLevel level);

    static bool add_reserved_region (address base_addr, size_t size, const NativeCallStack& stack, MEMFLAGS flag = mtNone);

    static bool add_committed_region      (address base_addr, size_t size, const NativeCallStack& stack);
    static bool remove_uncommitted_region (address base_addr, size_t size);
    static bool remove_released_region    (address base_addr, size_t size);
    static bool remove_released_region    (ReservedMemoryRegion* rgn);
    static void set_reserved_region_type  (address addr, MEMFLAGS flag);

    // Given an existing memory mapping registered with NMT, split the mapping in
    //  two. The newly created two mappings will be registered under the call
    //  stack and the memory flags of the original section.
    static bool split_reserved_region(address addr, size_t size, size_t split, MEMFLAGS flag, MEMFLAGS split_flag);

    // Walk virtual memory data structure for creating baseline, etc.
    static bool walk_virtual_memory(VirtualMemoryWalker* walker);

    // If p is contained within a known memory region, print information about it to the
    // given stream and return true; false otherwise.
    static bool print_containing_region(const void* p, outputStream* st);

    // Snapshot current thread stacks
    static void snapshot_thread_stacks();
    static void apply_summary_diff(VMATree::SummaryDiff diff);
    static RegionsTree* tree() { return _tracker->tree(); }
  };
};

#endif // NMT_VIRTUALMEMORYTRACKERWITHTREE_HPP