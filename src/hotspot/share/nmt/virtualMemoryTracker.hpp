/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "nmt/allocationSite.hpp"
#include "nmt/vmatree.hpp"
#include "nmt/regionsTree.hpp"
#include "runtime/atomic.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

// VirtualMemoryTracker (VMT) is an internal class of the MemTracker.
// All the Hotspot code use only the MemTracker interface to register the memory operations in NMT.
// Memory regions can be reserved/committed/uncommitted/released by calling MemTracker API which in turn call the corresponding functions in VMT.
// VMT uses RegionsTree to hold and manage the memory regions. Each region has two nodes that each one has address of the region (start/end) and
// state (reserved/released/committed) and MemTag of the regions before and after it.
//
// The memory operations of Reserve/Commit/Uncommit/Release are tracked by updating/inserting/deleting the nodes in the tree. When an operation
// changes nodes in the tree, the summary of the changes is returned back in a SummaryDiff struct. This struct shows that how much reserve/commit amount
// of any specific MemTag is changed. The summary of every operation is accumulated in VirtualMemorySummary class.
//
// Not all operations are valid in VMT. The following predicates are checked before the operation is applied to the tree and/or VirtualMemorySummary:
//   - committed size of a MemTag should be <= of its reserved size
//   - uncommitted size of a MemTag should be <= of its committed size
//   - released size of a MemTag should be <= of its reserved size


/*
 * Virtual memory counter
 */
class VirtualMemory {
 private:
  size_t     _reserved;
  size_t     _committed;

  volatile size_t _peak_size;
  void update_peak(size_t size);

 public:
  VirtualMemory() : _reserved(0), _committed(0), _peak_size(0) {}

  inline void reserve_memory(size_t sz) { _reserved += sz; }
  inline void commit_memory (size_t sz) {
    _committed += sz;
    assert(_committed <= _reserved, "Sanity check");
    update_peak(_committed);
  }

  inline void release_memory (size_t sz) {
    assert(_reserved >= sz, "Negative amount");
    _reserved -= sz;
  }

  inline void uncommit_memory(size_t sz) {
    assert(_committed >= sz, "Negative amount");
    _committed -= sz;
  }

  inline size_t reserved()  const { return _reserved;  }
  inline size_t committed() const { return _committed; }
  inline size_t peak_size() const {
    return Atomic::load(&_peak_size);
  }
};

// Virtual memory allocation site, keeps track where the virtual memory is reserved.
class VirtualMemoryAllocationSite : public AllocationSite {
  VirtualMemory _c;
 public:
  VirtualMemoryAllocationSite(const NativeCallStack& stack, MemTag mem_tag) :
    AllocationSite(stack, mem_tag) { }

  inline void reserve_memory(size_t sz)  { _c.reserve_memory(sz);  }
  inline void commit_memory (size_t sz)  { _c.commit_memory(sz);   }
  inline size_t reserved() const  { return _c.reserved(); }
  inline size_t committed() const { return _c.committed(); }
  inline size_t peak_size() const { return _c.peak_size(); }
};

class VirtualMemorySummary;

// This class represents a snapshot of virtual memory at a given time.
// The latest snapshot is saved in a static area.
class VirtualMemorySnapshot : public ResourceObj {
  friend class VirtualMemorySummary;

 private:
  VirtualMemory  _virtual_memory[mt_number_of_tags];

 public:
  inline VirtualMemory* by_tag(MemTag mem_tag) {
    int index = NMTUtil::tag_to_index(mem_tag);
    return &_virtual_memory[index];
  }

  inline const VirtualMemory* by_tag(MemTag mem_tag) const {
    int index = NMTUtil::tag_to_index(mem_tag);
    return &_virtual_memory[index];
  }

  inline size_t total_reserved() const {
    size_t amount = 0;
    for (int index = 0; index < mt_number_of_tags; index ++) {
      amount += _virtual_memory[index].reserved();
    }
    return amount;
  }

  inline size_t total_committed() const {
    size_t amount = 0;
    for (int index = 0; index < mt_number_of_tags; index ++) {
      amount += _virtual_memory[index].committed();
    }
    return amount;
  }

  void copy_to(VirtualMemorySnapshot* s) {
    for (int index = 0; index < mt_number_of_tags; index ++) {
      s->_virtual_memory[index] = _virtual_memory[index];
    }
  }
};

class VirtualMemorySummary : AllStatic {
 public:

  static inline void record_reserved_memory(size_t size, MemTag mem_tag) {
    as_snapshot()->by_tag(mem_tag)->reserve_memory(size);
  }

  static inline void record_committed_memory(size_t size, MemTag mem_tag) {
    as_snapshot()->by_tag(mem_tag)->commit_memory(size);
  }

  static inline void record_uncommitted_memory(size_t size, MemTag mem_tag) {
    as_snapshot()->by_tag(mem_tag)->uncommit_memory(size);
  }

  static inline void record_released_memory(size_t size, MemTag mem_tag) {
    as_snapshot()->by_tag(mem_tag)->release_memory(size);
  }

  // Move virtual memory from one memory tag to another.
  // Virtual memory can be reserved before it is associated with a memory tag, and tagged
  // as 'unknown'. Once the memory is tagged, the virtual memory will be moved from 'unknown'
  // tag to specified memory tag.
  static inline void move_reserved_memory(MemTag from, MemTag to, size_t size) {
    as_snapshot()->by_tag(from)->release_memory(size);
    as_snapshot()->by_tag(to)->reserve_memory(size);
  }

  static inline void move_committed_memory(MemTag from, MemTag to, size_t size) {
    as_snapshot()->by_tag(from)->uncommit_memory(size);
    as_snapshot()->by_tag(to)->commit_memory(size);
  }

  static void snapshot(VirtualMemorySnapshot* s);

  static VirtualMemorySnapshot* as_snapshot() {
    return &_snapshot;
  }

 private:
  static VirtualMemorySnapshot _snapshot;
};



/*
 * A virtual memory region
 */
class VirtualMemoryRegion {
 private:
  address      _base_address;
  size_t       _size;

 public:
  VirtualMemoryRegion(address addr, size_t size) :
    _base_address(addr), _size(size) {
     assert(addr != nullptr, "Invalid address");
     assert(size > 0, "Invalid size");
   }

  inline address base() const { return _base_address;   }
  inline address end()  const { return base() + size(); }
  inline size_t  size() const { return _size;           }

  inline bool is_empty() const { return size() == 0; }

  inline bool contain_address(address addr) const {
    return (addr >= base() && addr < end());
  }


  inline bool contain_region(address addr, size_t size) const {
    return contain_address(addr) && contain_address(addr + size - 1);
  }

  inline bool same_region(address addr, size_t sz) const {
    return (addr == base() && sz == size());
  }


  inline bool overlap_region(address addr, size_t sz) const {
    assert(sz > 0, "Invalid size");
    assert(size() > 0, "Invalid size");
    return MAX2(addr, base()) < MIN2(addr + sz, end());
  }

  inline bool adjacent_to(address addr, size_t sz) const {
    return (addr == end() || (addr + sz) == base());
  }

  void exclude_region(address addr, size_t sz) {
    assert(contain_region(addr, sz), "Not containment");
    assert(addr == base() || addr + sz == end(), "Can not exclude from middle");
    size_t new_size = size() - sz;

    if (addr == base()) {
      set_base(addr + sz);
    }
    set_size(new_size);
  }

  void expand_region(address addr, size_t sz) {
    assert(adjacent_to(addr, sz), "Not adjacent regions");
    if (base() == addr + sz) {
      set_base(addr);
    }
    set_size(size() + sz);
  }

  // Returns 0 if regions overlap; 1 if this region follows rgn;
  //  -1 if this region precedes rgn.
  inline int compare(const VirtualMemoryRegion& rgn) const {
    if (overlap_region(rgn.base(), rgn.size())) {
      return 0;
    } else if (base() >= rgn.end()) {
      return 1;
    } else {
      assert(rgn.base() >= end(), "Sanity");
      return -1;
    }
  }

  // Returns true if regions overlap, false otherwise.
  inline bool equals(const VirtualMemoryRegion& rgn) const {
    return compare(rgn) == 0;
  }

 protected:
  void set_base(address base) {
    assert(base != nullptr, "Sanity check");
    _base_address = base;
  }

  void set_size(size_t  size) {
    assert(size > 0, "Sanity check");
    _size = size;
  }
};


class CommittedMemoryRegion : public VirtualMemoryRegion {
 private:
  NativeCallStack  _stack;

 public:
  CommittedMemoryRegion()
    : VirtualMemoryRegion((address)1, 1), _stack(NativeCallStack::empty_stack()) { }

  CommittedMemoryRegion(address addr, size_t size, const NativeCallStack& stack)
    : VirtualMemoryRegion(addr, size), _stack(stack) { }

  inline void set_call_stack(const NativeCallStack& stack) { _stack = stack; }
  inline const NativeCallStack* call_stack() const         { return &_stack; }
  bool equals(const ReservedMemoryRegion& other) const;
};

class ReservedMemoryRegion : public VirtualMemoryRegion {
 private:
  NativeCallStack  _stack;
  MemTag         _mem_tag;

 public:
  bool is_valid() { return base() != (address)1 && size() != 1;}

  ReservedMemoryRegion()
    : VirtualMemoryRegion((address)1, 1), _stack(NativeCallStack::empty_stack()), _mem_tag(mtNone) { }

  ReservedMemoryRegion(address base, size_t size, const NativeCallStack& stack,
    MemTag mem_tag = mtNone)
    : VirtualMemoryRegion(base, size), _stack(stack), _mem_tag(mem_tag) { }


  ReservedMemoryRegion(address base, size_t size)
    : VirtualMemoryRegion(base, size), _stack(NativeCallStack::empty_stack()), _mem_tag(mtNone) { }

  // Copy constructor
  ReservedMemoryRegion(const ReservedMemoryRegion& rr)
    : VirtualMemoryRegion(rr.base(), rr.size()) {
    *this = rr;
  }

  inline void  set_call_stack(const NativeCallStack& stack) { _stack = stack; }
  inline const NativeCallStack* call_stack() const          { return &_stack;  }

  inline MemTag mem_tag() const            { return _mem_tag;  }

  // uncommitted thread stack bottom, above guard pages if there is any.
  address thread_stack_uncommitted_bottom() const;

  size_t committed_size() const;


  ReservedMemoryRegion& operator= (const ReservedMemoryRegion& other) {
    set_base(other.base());
    set_size(other.size());

    _stack = *other.call_stack();
    _mem_tag = other.mem_tag();

    return *this;
  }

  const char* tag_name() const { return NMTUtil::tag_to_name(_mem_tag); }
};

class VirtualMemoryWalker : public StackObj {
 public:
   virtual bool do_allocation_site(const ReservedMemoryRegion* rgn) { return false; }
};


class VirtualMemoryTracker {
  RegionsTree _tree;

 public:
  VirtualMemoryTracker(bool is_detailed_mode) : _tree(is_detailed_mode) { }

  void add_reserved_region       (address base_addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone);
  void add_committed_region      (address base_addr, size_t size, const NativeCallStack& stack);
  void remove_uncommitted_region (address base_addr, size_t size);
  void remove_released_region    (address base_addr, size_t size);
  void set_reserved_region_tag   (address addr, size_t size, MemTag mem_tag);

  // Given an existing memory mapping registered with NMT, split the mapping in
  //  two. The newly created two mappings will be registered under the call
  //  stack and the memory tags of the original section.
  void split_reserved_region(address addr, size_t size, size_t split, MemTag mem_tag, MemTag split_mem_tag);

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

    static void add_reserved_region       (address base_addr, size_t size, const NativeCallStack& stack, MemTag mem_tag = mtNone);
    static void add_committed_region      (address base_addr, size_t size, const NativeCallStack& stack);
    static void remove_uncommitted_region (address base_addr, size_t size);
    static void remove_released_region    (address base_addr, size_t size);
    static void set_reserved_region_tag   (address addr, size_t size, MemTag mem_tag);
    static void split_reserved_region(address addr, size_t size, size_t split, MemTag mem_tag, MemTag split_mem_tag);
    static bool walk_virtual_memory(VirtualMemoryWalker* walker);
    static bool print_containing_region(const void* p, outputStream* st);
    static void snapshot_thread_stacks();
    static void apply_summary_diff(VMATree::SummaryDiff diff);

    static RegionsTree* tree() { return _tracker->tree(); }
  };
};

#endif // SHARE_NMT_VIRTUALMEMORYTRACKER_HPP