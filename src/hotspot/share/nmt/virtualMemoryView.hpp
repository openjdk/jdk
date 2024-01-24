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

#include "memory/allocation.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

/*
  Remaining issues:
  1. No VirtualMemorySummary accounting.
     This is pretty simple. We need to store a VirtualMemorySnapshot for each space (as we need to save the peak values).
     Then, we need to reset the _reserved and _committed but not _peak_size.

  3. No baseline diffing
  4. Reporting not part of Reporter class but part of VirtualMemoryView, not too bad.
  5. Insufficient amount of unit tests
  6. Need to fix includes, copyright stmts etc
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

  using OffsetRegionStorage = GrowableArrayCHeap<TrackedOffsetRange, mtNMT>;
  using RegionStorage = GrowableArrayCHeap<TrackedRange, mtNMT>;

  struct VirtualMemory : public CHeapObj<mtNMT> {
    // Reserved memory within this process' memory map
    RegionStorage reserved_regions;
    // Committed memory per PhysicalMemorySpace
    GrowableArrayCHeap<RegionStorage, mtNMT> committed_regions;
    // Mappings from virtual memory space to committed memory, per PhysicalMemorySpace.
    GrowableArrayCHeap<OffsetRegionStorage, mtNMT> mapped_regions;
    // Summary tracking per PhysicalMemorySpace
    GrowableArrayCHeap<VirtualMemorySnapshot, mtNMT> summary;
    VirtualMemory()
     : reserved_regions(),
       committed_regions(),
       mapped_regions(),
       summary() {
    }
    VirtualMemory(const VirtualMemory& other) {
      *this = other;
    }
    // Deep copying of VirtualMemory
    VirtualMemory& operator=(const VirtualMemory& other) {
      if (this != &other) {
        this->reserved_regions = RegionStorage{other.reserved_regions.length()};
        this->mapped_regions =
            GrowableArrayCHeap<OffsetRegionStorage, mtNMT>{other.mapped_regions.length()};
        this->committed_regions =
            GrowableArrayCHeap<RegionStorage, mtNMT>{other.committed_regions.length()};
        this->summary = GrowableArrayCHeap<VirtualMemorySnapshot, mtNMT>(other.summary.length());

        for (int i = 0; i < other.reserved_regions.length(); i++) {
          this->reserved_regions.push(other.reserved_regions.at(i));
        }
        for (int i = 0; i < other.mapped_regions.length(); i++) {
          const OffsetRegionStorage& ith = other.mapped_regions.at(i);
          this->mapped_regions.push(ith);
          OffsetRegionStorage& vith = this->mapped_regions.at(i);
          for (int j = 0; j < ith.length(); j++) {
            vith.push(ith.at(j));
          }
        }
        for (int i = 0; i < other.committed_regions.length(); i++) {
          const RegionStorage& ith = other.committed_regions.at(i);
          this->committed_regions.push(ith);
          RegionStorage& vith = this->committed_regions.at(i);
          for (int j = 0; j < ith.length(); j++) {
            vith.push(ith.at(j));
          }
        }
        for (int i = 0; i < other.summary.length(); i++) {
          summary.push(other.summary.at(i));
        }
      }
      return *this;
    }
  };

private:
  // Thread stack tracking
  address thread_stack_uncommitted_bottom(TrackedRange& rng, RegionStorage& committed_ranges);
  void merge_thread_stacks(GrowableArrayCHeap<Range, mtNMT>& ranges);
  // Iterate the range, find committed region within its bound.
  class RegionIterator : public StackObj {
  private:
    const address _start;
    const size_t _size;

    address _current_start;

  public:
    RegionIterator(address start, size_t size)
      : _start(start),
        _size(size),
        _current_start(start) {
    }

    // return true if committed region is found
    bool next_committed(address& start, size_t& size);

  private:
    address end() const {
      return _start + _size;
    }
  };
  void snapshot_thread_stacks();
private:
  // Utilities
  static bool adjacent(Range a, Range b);
  static bool after(Range a, Range b); // a < b
  static bool disjoint(Range a, Range b);
  static bool overlaps(Range a, Range b);
  static bool is_same(Range a, Range b);
  static Range union_of(Range a, Range b);
  static Range overlap_of(Range a, Range b);
  static bool same_stack(TrackedRange a, TrackedRange b);

  // Pre-condition: ranges is sorted in a left-aligned fashion
  // That is: (a,b) comes before (c,d) if a <= c
  // Merges the ranges into a minimal sequence, taking into account that two ranges can only be merged if:
  // 1. Their NativeCallStacks are the same
  // 2. Their starts align correctly
  static void merge_ranges(GrowableArray<Range>& ranges);
  static void merge_committed(RegionStorage& ranges);
  static void merge_mapped(OffsetRegionStorage& ranges);

  static void sort_regions(GrowableArrayCHeap<VirtualMemoryView::Range, mtNMT>& storage);
  static void sort_regions(OffsetRegionStorage& storage);
  static void sort_regions(RegionStorage& storage);

  static bool equal_stacks(NativeCallStackStorage::StackIndex a, NativeCallStackStorage::StackIndex b) {
    return (a.index() == b.index() && a.chunk() == b.chunk()) ||
            _stack_storage->get(a).equals(_stack_storage->get(b));
  }

  // Split the range to_split by removing to_remove from it, storing the remaining parts in out.
  // Returns an OverlappingResult and will fill the out array with at most 2 elements.
  // The integer pointed to by len will be  set to the number of resulting TrackedRanges put into the out array.
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

  static VirtualMemory* _virt_mem;
  static GrowableArrayCHeap<Range, mtNMT>* thread_stacks; // Committed thread stacks are handled specially
  static GrowableArrayCHeap<const char*, mtNMT>* _names; // Map memory space to name

  static NativeCallStackStorage* _stack_storage;
  static bool _is_detailed_mode;

  static void register_memory(RegionStorage& storage, address base_addr, size_t size, MEMFLAGS flag, const NativeCallStack& stack);
  static void unregister_memory(RegionStorage& storage, address base_addr, size_t size);

public:
  // A default PhysicalMemorySpace for when allocating to the heap.
  static PhysicalMemorySpace heap;
  static void initialize(bool is_detailed_mode);

  static PhysicalMemorySpace register_space(const char* descriptive_name);

  static void reserve_memory(address base_addr, size_t size, MEMFLAGS flag, const NativeCallStack& stack);
  static void release_memory(address base_addr, size_t size);
  static void commit_memory(address base_addr, size_t size, const NativeCallStack& stack);
  static void uncommit_memory(address base_addr, size_t size);

  static void add_view_into_space(const PhysicalMemorySpace& space, address base_addr, size_t size,
                                  address offset, MEMFLAGS flag, const NativeCallStack& stack);
  static void remove_view_into_space(const PhysicalMemorySpace& space, address base_addr,
                                     size_t size);

  static void commit_memory_into_space(const PhysicalMemorySpace& space, address offset, size_t size,
                                       const NativeCallStack& stack);
  static void uncommit_memory_into_space(const PhysicalMemorySpace& space, address offset,
                                         size_t size);

  // Produce a report on output.
  static void report(VirtualMemory& mem, outputStream* output, size_t scale = K);
  static const VirtualMemory& virtual_memory() {
    return *_virt_mem;
  }

  static VirtualMemorySnapshot summary_snapshot(PhysicalMemorySpace space) {
    VirtualMemorySnapshot& snap = _virt_mem->summary.at(space.id);
    // Reset all memory, keeping peak values
    for (int i = 0; i < mt_number_of_types; i++) {
      MEMFLAGS flag = NMTUtil::index_to_flag(i);
      ::VirtualMemory* mem = snap.by_type(flag);
      mem->release_memory(mem->reserved());
      mem->uncommit_memory(mem->committed());
    }
    // Fill out summary
    const RegionStorage& reserved_ranges = virtual_memory().reserved_regions;
    for (int i = 0; i < virtual_memory().reserved_regions.length(); i++) {
      const TrackedRange& range = reserved_ranges.at(i);
      snap.by_type(range.flag)->reserve_memory(range.size);
    }
    const RegionStorage& committed_ranges = virtual_memory().committed_regions.at(space.id);
    for (int i = 0; i < committed_ranges.length(); i++) {
      const TrackedRange& range = committed_ranges.at(i);
      snap.by_type(range.flag)->commit_memory(range.size);
    }

    return snap;
  }
};

#endif // SHARE_NMT_VIRTUALMEMORYVIEW_HPP
