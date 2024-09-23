/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_VIRTUALMEMORYTRACKER_HPP
#define SHARE_NMT_VIRTUALMEMORYTRACKER_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace.hpp" // For MetadataType
#include "memory/metaspaceStats.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/regionsTree.hpp"
#include "nmt/virtualMemoryTrackerCommon.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

#undef VMT_OLD_VERSION
#define VMT_NEW_VERSION

// Main class called from MemTracker to track virtual memory allocations, commits and releases.
class VirtualMemoryTracker : AllStatic {
  friend class VirtualMemoryTrackerTest;
  friend class CommittedVirtualMemoryTest;

 public:
  static bool initialize(NMT_TrackingLevel level);

  static bool add_reserved_region (address base_addr, size_t size, const NativeCallStack& stack, MEMFLAGS flag = mtNone);

  static bool add_committed_region      (address base_addr, size_t size, const NativeCallStack& stack);
  static bool remove_uncommitted_region (address base_addr, size_t size);
  static bool remove_released_region    (address base_addr, size_t size);
  static bool remove_released_region    (ReservedMemoryRegion* rgn);
  static void set_reserved_region_type  (address addr, size_t size, MEMFLAGS flag);

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

 private:
  static SortedLinkedList<ReservedMemoryRegion, compare_reserved_region_base>* _reserved_regions;

};

#endif // SHARE_NMT_VIRTUALMEMORYTRACKER_HPP

