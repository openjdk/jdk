/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_VIRTUAL_MEMORY_TRACKER_HPP
#define SHARE_VM_SERVICES_VIRTUAL_MEMORY_TRACKER_HPP

#if INCLUDE_NMT

#include "memory/allocation.hpp"
#include "services/allocationSite.hpp"
#include "services/nmtCommon.hpp"
#include "utilities/linkedlist.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"


/*
 * Virtual memory counter
 */
class VirtualMemory VALUE_OBJ_CLASS_SPEC {
 private:
  size_t     _reserved;
  size_t     _committed;

 public:
  VirtualMemory() : _reserved(0), _committed(0) { }

  inline void reserve_memory(size_t sz) { _reserved += sz; }
  inline void commit_memory (size_t sz) {
    _committed += sz;
    assert(_committed <= _reserved, "Sanity check");
  }

  inline void release_memory (size_t sz) {
    assert(_reserved >= sz, "Negative amount");
    _reserved -= sz;
  }

  inline void uncommit_memory(size_t sz) {
    assert(_committed >= sz, "Negative amount");
    _committed -= sz;
  }

  void reset() {
    _reserved  = 0;
    _committed = 0;
  }

  inline size_t reserved()  const { return _reserved;  }
  inline size_t committed() const { return _committed; }
};

// Virtual memory allocation site, keeps track where the virtual memory is reserved.
class VirtualMemoryAllocationSite : public AllocationSite<VirtualMemory> {
 public:
  VirtualMemoryAllocationSite(const NativeCallStack& stack) :
    AllocationSite<VirtualMemory>(stack) { }

  inline void reserve_memory(size_t sz)  { data()->reserve_memory(sz);  }
  inline void commit_memory (size_t sz)  { data()->commit_memory(sz);   }
  inline void uncommit_memory(size_t sz) { data()->uncommit_memory(sz); }
  inline void release_memory(size_t sz)  { data()->release_memory(sz);  }
  inline size_t reserved() const  { return peek()->reserved(); }
  inline size_t committed() const { return peek()->committed(); }
};

class VirtualMemorySummary;

// This class represents a snapshot of virtual memory at a given time.
// The latest snapshot is saved in a static area.
class VirtualMemorySnapshot : public ResourceObj {
  friend class VirtualMemorySummary;

 private:
  VirtualMemory  _virtual_memory[mt_number_of_types];

 public:
  inline VirtualMemory* by_type(MEMFLAGS flag) {
    int index = NMTUtil::flag_to_index(flag);
    return &_virtual_memory[index];
  }

  inline VirtualMemory* by_index(int index) {
    assert(index >= 0, "Index out of bound");
    assert(index < mt_number_of_types, "Index out of bound");
    return &_virtual_memory[index];
  }

  inline size_t total_reserved() const {
    size_t amount = 0;
    for (int index = 0; index < mt_number_of_types; index ++) {
      amount += _virtual_memory[index].reserved();
    }
    return amount;
  }

  inline size_t total_committed() const {
    size_t amount = 0;
    for (int index = 0; index < mt_number_of_types; index ++) {
      amount += _virtual_memory[index].committed();
    }
    return amount;
  }

  inline void reset() {
    for (int index = 0; index < mt_number_of_types; index ++) {
      _virtual_memory[index].reset();
    }
  }

  void copy_to(VirtualMemorySnapshot* s) {
    for (int index = 0; index < mt_number_of_types; index ++) {
      s->_virtual_memory[index] = _virtual_memory[index];
    }
  }
};

class VirtualMemorySummary : AllStatic {
 public:
  static void initialize();

  static inline void record_reserved_memory(size_t size, MEMFLAGS flag) {
    as_snapshot()->by_type(flag)->reserve_memory(size);
  }

  static inline void record_committed_memory(size_t size, MEMFLAGS flag) {
    as_snapshot()->by_type(flag)->commit_memory(size);
  }

  static inline void record_uncommitted_memory(size_t size, MEMFLAGS flag) {
    as_snapshot()->by_type(flag)->uncommit_memory(size);
  }

  static inline void record_released_memory(size_t size, MEMFLAGS flag) {
    as_snapshot()->by_type(flag)->release_memory(size);
  }

  // Move virtual memory from one memory type to another.
  // Virtual memory can be reserved before it is associated with a memory type, and tagged
  // as 'unknown'. Once the memory is tagged, the virtual memory will be moved from 'unknown'
  // type to specified memory type.
  static inline void move_reserved_memory(MEMFLAGS from, MEMFLAGS to, size_t size) {
    as_snapshot()->by_type(from)->release_memory(size);
    as_snapshot()->by_type(to)->reserve_memory(size);
  }

  static inline void move_committed_memory(MEMFLAGS from, MEMFLAGS to, size_t size) {
    as_snapshot()->by_type(from)->uncommit_memory(size);
    as_snapshot()->by_type(to)->commit_memory(size);
  }

  static inline void snapshot(VirtualMemorySnapshot* s) {
    as_snapshot()->copy_to(s);
  }

  static inline void reset() {
    as_snapshot()->reset();
  }

  static VirtualMemorySnapshot* as_snapshot() {
    return (VirtualMemorySnapshot*)_snapshot;
  }

 private:
  static size_t _snapshot[CALC_OBJ_SIZE_IN_TYPE(VirtualMemorySnapshot, size_t)];
};



/*
 * A virtual memory region
 */
class VirtualMemoryRegion VALUE_OBJ_CLASS_SPEC {
 private:
  address      _base_address;
  size_t       _size;

 public:
  VirtualMemoryRegion(address addr, size_t size) :
    _base_address(addr), _size(size) {
     assert(addr != NULL, "Invalid address");
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
    VirtualMemoryRegion rgn(addr, sz);
    return contain_address(addr) ||
           contain_address(addr + sz - 1) ||
           rgn.contain_address(base()) ||
           rgn.contain_address(end() - 1);
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

 protected:
  void set_base(address base) {
    assert(base != NULL, "Sanity check");
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
  CommittedMemoryRegion(address addr, size_t size, const NativeCallStack& stack) :
    VirtualMemoryRegion(addr, size), _stack(stack) { }

  inline int compare(const CommittedMemoryRegion& rgn) const {
    if (overlap_region(rgn.base(), rgn.size()) ||
        adjacent_to   (rgn.base(), rgn.size())) {
      return 0;
    } else {
      if (base() == rgn.base()) {
        return 0;
      } else if (base() > rgn.base()) {
        return 1;
      } else {
        return -1;
      }
    }
  }

  inline bool equals(const CommittedMemoryRegion& rgn) const {
    return compare(rgn) == 0;
  }

  inline void set_call_stack(const NativeCallStack& stack) { _stack = stack; }
  inline const NativeCallStack* call_stack() const         { return &_stack; }
};


typedef LinkedListIterator<CommittedMemoryRegion> CommittedRegionIterator;

int compare_committed_region(const CommittedMemoryRegion&, const CommittedMemoryRegion&);
class ReservedMemoryRegion : public VirtualMemoryRegion {
 private:
  SortedLinkedList<CommittedMemoryRegion, compare_committed_region>
    _committed_regions;

  NativeCallStack  _stack;
  MEMFLAGS         _flag;

  bool             _all_committed;

 public:
  ReservedMemoryRegion(address base, size_t size, const NativeCallStack& stack,
    MEMFLAGS flag = mtNone) :
    VirtualMemoryRegion(base, size), _stack(stack), _flag(flag),
    _all_committed(false) { }


  ReservedMemoryRegion(address base, size_t size) :
    VirtualMemoryRegion(base, size), _stack(emptyStack), _flag(mtNone),
    _all_committed(false) { }

  // Copy constructor
  ReservedMemoryRegion(const ReservedMemoryRegion& rr) :
    VirtualMemoryRegion(rr.base(), rr.size()) {
    *this = rr;
  }

  inline void  set_call_stack(const NativeCallStack& stack) { _stack = stack; }
  inline const NativeCallStack* call_stack() const          { return &_stack;  }

  void  set_flag(MEMFLAGS flag);
  inline MEMFLAGS flag() const            { return _flag;  }

  inline int compare(const ReservedMemoryRegion& rgn) const {
    if (overlap_region(rgn.base(), rgn.size())) {
      return 0;
    } else {
      if (base() == rgn.base()) {
        return 0;
      } else if (base() > rgn.base()) {
        return 1;
      } else {
        return -1;
      }
    }
  }

  inline bool equals(const ReservedMemoryRegion& rgn) const {
    return compare(rgn) == 0;
  }

  bool    add_committed_region(address addr, size_t size, const NativeCallStack& stack);
  bool    remove_uncommitted_region(address addr, size_t size);

  size_t  committed_size() const;

  // move committed regions that higher than specified address to
  // the new region
  void    move_committed_regions(address addr, ReservedMemoryRegion& rgn);

  inline bool all_committed() const { return _all_committed; }
  void        set_all_committed(bool b);

  CommittedRegionIterator iterate_committed_regions() const {
    return CommittedRegionIterator(_committed_regions.head());
  }

  ReservedMemoryRegion& operator= (const ReservedMemoryRegion& other) {
    set_base(other.base());
    set_size(other.size());

    _stack =         *other.call_stack();
    _flag  =         other.flag();
    _all_committed = other.all_committed();
    if (other.all_committed()) {
      set_all_committed(true);
    } else {
      CommittedRegionIterator itr = other.iterate_committed_regions();
      const CommittedMemoryRegion* rgn = itr.next();
      while (rgn != NULL) {
        _committed_regions.add(*rgn);
        rgn = itr.next();
      }
    }
    return *this;
  }

 private:
  // The committed region contains the uncommitted region, subtract the uncommitted
  // region from this committed region
  bool remove_uncommitted_region(LinkedListNode<CommittedMemoryRegion>* node,
    address addr, size_t sz);

  bool add_committed_region(const CommittedMemoryRegion& rgn) {
    assert(rgn.base() != NULL, "Invalid base address");
    assert(size() > 0, "Invalid size");
    return _committed_regions.add(rgn) != NULL;
  }
};

int compare_reserved_region_base(const ReservedMemoryRegion& r1, const ReservedMemoryRegion& r2);

class VirtualMemoryWalker : public StackObj {
 public:
   virtual bool do_allocation_site(const ReservedMemoryRegion* rgn) { return false; }
};

// Main class called from MemTracker to track virtual memory allocations, commits and releases.
class VirtualMemoryTracker : AllStatic {
 public:
  static bool initialize(NMT_TrackingLevel level);

  static bool add_reserved_region (address base_addr, size_t size, const NativeCallStack& stack,
    MEMFLAGS flag = mtNone, bool all_committed = false);

  static bool add_committed_region      (address base_addr, size_t size, const NativeCallStack& stack);
  static bool remove_uncommitted_region (address base_addr, size_t size);
  static bool remove_released_region    (address base_addr, size_t size);
  static void set_reserved_region_type  (address addr, MEMFLAGS flag);

  // Walk virtual memory data structure for creating baseline, etc.
  static bool walk_virtual_memory(VirtualMemoryWalker* walker);

  static bool transition(NMT_TrackingLevel from, NMT_TrackingLevel to);

 private:
  static SortedLinkedList<ReservedMemoryRegion, compare_reserved_region_base> _reserved_regions;
};


#endif // INCLUDE_NMT

#endif // SHARE_VM_SERVICES_VIRTUAL_MEMORY_TRACKER_HPP
