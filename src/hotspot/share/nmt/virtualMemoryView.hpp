/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_VIRTUALMEMORYVIEW_HPP
#define SHARE_NMT_VIRTUALMEMORYVIEW_HPP

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspace.hpp" // For MetadataType
#include "memory/metaspaceStats.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/allocationSite.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/nmtCommon.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/linkedlist.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"
#include "utilities/pair.hpp"

/*
  Remaining issues:
  3. No baselining
  4. Reporting not part of Reporter class but part of VirtualMemoryView
  5. Insufficient amount of unit tests
  6. Need to fix includes, copyright stmts etc

  I've gotten some input on just how large these mappings can be: Approx. 16 million mappings at once.
  Perhaps we can introduce an alignment+maximum size for this in order to solve the problem? We can then statically allocate the mapping area and get
  O(1) mapping info.
*/

class VirtualMemoryView {
  friend class NmtVirtualMemoryViewTest;

  using Id = uint32_t;
public:
   struct PhysicalMemorySpace {
    Id id; // Uniquely identifies the device
    static Id unique_id;
    static Id next_unique() {
      return unique_id++;
    }
   };

  // Some memory range
  struct Range {
    address start;
    size_t size;
    Range(address start = 0, size_t size = 0)
    : start(start), size(size) {}
    address end() const {
      return start + size;
    }
  };
  // Add tracking information
  struct TrackedRange : public Range {
    NativeCallStackStorage::StackIndex stack_idx; // From whence did this happen?
    MEMFLAGS flag; // What flag does it have? Guaranteed to be mtNone for committed range.
    TrackedRange(address start = 0, size_t size = 0, NativeCallStackStorage::StackIndex stack_idx = {0,0}, MEMFLAGS flag = mtNone) :
      Range(start, size), stack_idx(stack_idx), flag(flag) {}
  };
  // Give it the possibility of being offset
  struct TrackedOffsetRange : public TrackedRange {
    address physical_address;
    TrackedOffsetRange(address start = 0, size_t size = 0, address physical_address = 0, NativeCallStackStorage::StackIndex stack_idx = {0,0}, MEMFLAGS flag = mtNone)
      :  TrackedRange(start, size, stack_idx, flag),
      physical_address(physical_address) {}
    explicit TrackedOffsetRange(TrackedRange& rng)
    : TrackedOffsetRange(rng.start, rng.size, rng.start, rng.stack_idx, rng.flag) {}
    TrackedOffsetRange(const TrackedOffsetRange& rng) = default;
    TrackedOffsetRange& operator=(const TrackedOffsetRange& rng) {
      this->start = rng.start;
      this->size = rng.size;
      this->physical_address = rng.physical_address;
      this->stack_idx = rng.stack_idx;
      this->flag = rng.flag;
      return *this;
    }
    TrackedOffsetRange(TrackedOffsetRange&& rng)
      : TrackedRange(rng.start, rng.size, rng.stack_idx, rng.flag), physical_address(rng.physical_address) {}

    address physical_end() const {
      return physical_address + size;
    }
  };

private:
  using OffsetRegionStorage = GrowableArrayCHeap<TrackedOffsetRange, mtNMT>;
  using RegionStorage = GrowableArrayCHeap<TrackedRange, mtNMT>;

  // Utilities
  static bool overlaps(Range a, Range b);
  static bool adjacent(Range a, Range b);

  // Pre-condition: ranges is sorted in a left-aligned fashion
  // That is: (a,b) comes before (c,d) if a <= c
  // Merges the ranges into a minimal sequence, taking into account that two ranges can only be merged if:
  // 1. Their NativeCallStacks are the same
  // 2. Their starts align correctly
  static void merge_committed(RegionStorage& ranges);
  static void merge_mapped(OffsetRegionStorage& ranges);

  static void sort_regions(GrowableArrayCHeap<VirtualMemoryView::Range, mtNMT>& storage);
  static void sort_regions(OffsetRegionStorage& storage);
  static void sort_regions(RegionStorage& storage);

  // Split the range to_split by removing to_remove from it, storing the remaining parts in out.
  // Returns true if an overlap was found and will fill the out array with at most 2 elements.
  // The integer pointed to by len will be  set to the number of resulting TrackedRanges.
  // The physical address is managed appropriately for the out array.
  enum class OverlappingResult {
    NoOverlap,
    EntirelyEnclosed,
    SplitInMiddle,
    ShortenedFromLeft,
    ShortenedFromRight,
  };
  static OverlappingResult overlap_of(TrackedOffsetRange to_split, Range to_remove,
                                      TrackedOffsetRange* out, int* len);

  struct VirtualMemory {
    RegionStorage* reserved_regions;
    GrowableArrayCHeap<OffsetRegionStorage, mtNMT>* mapped_regions;
    GrowableArrayCHeap<RegionStorage, mtNMT>* committed_regions;
    VirtualMemory() :
      reserved_regions(nullptr),
      mapped_regions(nullptr),
      committed_regions(nullptr) {
    }

    // Performing a deep copy is expensive, so we want to be very explicit when we do this.
    // Therefore we make this a method instead of copy assignment operator.
    VirtualMemory deep_copy() {
      VirtualMemory virt_mem;
      virt_mem.reserved_regions  = new RegionStorage{this->reserved_regions->length()};
      virt_mem.mapped_regions    = new GrowableArrayCHeap<OffsetRegionStorage, mtNMT>{this->mapped_regions->length()};
      virt_mem.committed_regions = new GrowableArrayCHeap<RegionStorage, mtNMT>{this->committed_regions->length()};
      
      return virt_mem;
    }
  };
  static VirtualMemory virt_mem;
  static RegionStorage* _reserved_regions;
  static GrowableArrayCHeap<OffsetRegionStorage, mtNMT>* _mapped_regions;
  static GrowableArrayCHeap<RegionStorage, mtNMT>* _committed_regions;
  static GrowableArrayCHeap<const char*, mtNMT>* _names; // Map memory space to name

  static NativeCallStackStorage* _stack_storage;
  static bool _is_detailed_mode;

  static void register_memory(RegionStorage& storage, address base_addr, size_t size, MEMFLAGS flag, const NativeCallStack& stack);
  static void unregister_memory(RegionStorage& storage, address base_addr, size_t size);
public:
  static void initialize(bool is_detailed_mode);

  static PhysicalMemorySpace register_space(const char* descriptive_name);

  static void reserve_memory(address base_addr, size_t size, MEMFLAGS flag, const NativeCallStack& stack);
  static void release_memory(address base_addr, size_t size);
  static void commit_memory();
  static void uncommit_memory();

  static void add_view_into_space(const PhysicalMemorySpace& space, address base_addr, size_t size,
                                  address offset, MEMFLAGS flag, const NativeCallStack& stack);
  static void remove_view_into_space(const PhysicalMemorySpace& space, address base_addr,
                                     size_t size);

  static void commit_memory_into_space(const PhysicalMemorySpace& space, address offset, size_t size,
                                       const NativeCallStack& stack);
  static void uncommit_memory_into_space(const PhysicalMemorySpace& space, address offset,
                                         size_t size);

  // Produce a report on output.
  static void report(outputStream* output, size_t scale = K);

};

#endif // SHARE_NMT_VIRTUALMEMORYVIEW_HPP
