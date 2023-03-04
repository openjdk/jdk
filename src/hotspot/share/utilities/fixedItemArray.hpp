/*
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_UTILITIES_FIXEDITEMARRAY_HPP
#define SHARE_UTILITIES_FIXEDITEMARRAY_HPP

//#include "memory/allocation.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"


// FixedItemArray:
// A container for items of type T with the following properties:
//
// - Elements are *address-stable*
// - Array can grow
// - Allocation is fast (pointer bump)
// - Releasing of the whole array is fast.
// - Individual items can be prematurely deallocated into an internal freelist
// - Container does not call any constructors or destructors, it just allocates space.
//   Construction and destruction are up to the caller (e.g. placement new)
// - Memory management is optionally left to the caller via "Allocator" template
//   parameter. Default allocators for C-heap and raw C-heap are provided.
// - T can be a POD or Non-POD.
//
// Implementation:
//
// Container is implemented as a growable list of arrays ("slabs") of T. Array size
//  ("items_per_slab") and optionally max. number of slabs can be configured.
//
// Container contains an in-place freelist that works with the memory of T, so it
//  does not cost additional memory.
//
// Note that the container can easily be used to maintain elements within a pre-given
//  address range (it would degenerate into a non-growable array).
//
// Limitations:
// - There is (for now) no way to iterate over all elements. Iterating is non-trivial
//   since the container has no cheap way to determine which elements are deallocated
//   (reside in freelist). We would have to either (1) store state with each item or (2)
//   walk the freelist for each item. (1) costs memory, (2) CPU. Left for later RFEs
//   if needed.
// - alignof(T) should be happy with natural malloc alignment. Larger alignments (e.g.
//   struct alignas(32) D) is not supported.
// - Array does not release memory before its destructed. The "deallocate(elem)" function
//   only puts elem into an internal freelist.
// All these limitations can be lifted with more work, but left for later RFEs.

// Let Allocators implement:
//  static void* allocate() { return nullptr; }
//  static void deallocate(void* p) { ShouldNotReachHere(); }
// To keep things simple, allocators are stateless for now.

// Uses os::malloc
struct CHeapAllocator {
  static void* allocate(size_t l);
  static void deallocate(void* p);
};

// Uses raw malloc
struct RawCHeapAllocator {
  static void* allocate(size_t l);
  static void deallocate(void* p);
};

template <class T, unsigned items_per_slab, unsigned max_slabs = 0, class Allocator = CHeapAllocator>
class FixedItemArray {

  // For now, we cannot store data with alignment requirement > 16 bytes since we lack
  // NMT wrapped versions of posix_memalign() and friends. Thus we cannot store
  // Slabs at the required alignment. Easy fix for this is to make sure the *size* of T is properly
  // aligned.
  STATIC_ASSERT(alignof(T) <= minimum_malloc_alignment);

  // We don't want to call constructors - we just want to provide a T-shaped, properly
  // aligned storage location to the caller.
  struct alignas(alignof(T)) T_shaped_storage {
    char bytes[sizeof(T)];
  };

  // Overlay a next pointer for freelist handling.
  union Slot {
    T_shaped_storage data;
    Slot* next;
    T* v() const { return (T*)(data.bytes); }
  };

  class Slab {
    Slot _slots[items_per_slab]; // first! to keep alignment
    Slab* _next;
    unsigned _hwm;
  public:

    Slab() : _next(nullptr), _hwm(0) {
      assert(offsetof(Slot, data) == 0 &&
             offsetof(Slot, next) == 0, "Sanity");
      assert(is_aligned(_slots, alignof(Slot)),
             "bad alignment for slab " PTR_FORMAT ".", p2i(&_slots));
    }

    unsigned hwm() const    { return _hwm; }
    bool full() const       { return hwm() == items_per_slab; }
    Slab* next() const      { return _next; }
    void set_next(Slab* p)  { _next = p; }

    T* allocate() {
      if (full()) {
        return nullptr;
      }
      Slot* slot = _slots + (_hwm++);
      return slot->v();
    }

#ifdef ASSERT
    bool contains(const void* p) const {
      return (Slot*)p >= _slots && (Slot*)p < (_slots + _hwm);
    }
#endif

  }; // Slab

  Slab* _first_slab;
  Slab* _current_slab;

  class FreeList {
    Slot* _first;
    unsigned _c;

  public:
    FreeList() : _first(nullptr), _c(0) {}

    T* remove_or_null() {
      if (_first != nullptr) {
        T* p = _first->v();
        _first = _first->next;
        _c--;
        return p;
      }
      return nullptr;
    }

    void add(T* p) {
      assert(is_aligned(p, sizeof(T*)), "bad alignment");
      Slot* slot = (Slot*)p;
      slot->next = _first;
      _first = slot;
      _c++;
    }

    unsigned count() const { return _c; }

    void reset() { _c = 0; _first = nullptr; }

#ifdef ASSERT
    void verify() const {
      unsigned counted = 0;
      for (const Slot* slot = _first; slot != nullptr; slot = slot->next) {
        counted++;
        assert(counted <= _c, "circle?");
      }
      assert(counted == _c,
             "freecount off (%u vs %u)", counted, _c);
    }
#endif
  };

  FreeList _freelist;

  // Statistics
  uintx _num_slabs;
  uintx _num_allocated;

  bool allocate_slab() {
    if (max_slabs > 0 && _num_slabs == max_slabs) {
      return false;
    }
    void* p = Allocator::allocate(sizeof(Slab));
    if (p != nullptr) {
      Slab* slab = new(p) Slab;
      if (_current_slab != nullptr) {
        _current_slab->set_next(slab);
        _current_slab = slab;
      } else {
        _first_slab = _current_slab = slab;
      }
      _num_slabs++;
    }
    return p != nullptr;
  }

  void free_all_slabs() {
    Slab* p = _first_slab;
    while (p != nullptr) {
      Slab* p2 = p->next();
      Allocator::deallocate(p);
      p = p2;
    }
  }

  T* allocate_impl() {
    // try freelist first
    T* p = _freelist.remove_or_null();
    if (p != nullptr) {
      return p;
    }
    // allocate slab if needed
    if ((_current_slab == nullptr || _current_slab->full()) &&
         !allocate_slab()) {
      return nullptr;
    }
    assert(_current_slab != nullptr && !_current_slab->full(), "We should have a valid slab");
    // allocate from slab
    return _current_slab->allocate();
  }

  void reset() {
    _freelist.reset();
    free_all_slabs();
    _first_slab = _current_slab = nullptr;
    _num_slabs = 0;
    _num_allocated = 0;
  }

public:

  // Allocate a growable array
  FixedItemArray() :
    _first_slab(nullptr), _current_slab(nullptr),
    _num_slabs(0), _num_allocated(0)
  {}

  ~FixedItemArray() {
    reset();
  }

  T* allocate() {
    T* p = allocate_impl();
    if (p != nullptr) {
      assert(is_aligned(p, alignof(T)), "bad alignment");
      _num_allocated++;
      return p;
    }
    return p;
  }

  void deallocate(T* p) {
    assert(_num_allocated > 0, "negative overflow");
    _freelist.add(p);
    _num_allocated--;
  }

#ifdef ASSERT
  // Returns true if array contains this pointer
  bool contains(const void* p) const {
    for (Slab* slab = _first_slab; slab != nullptr; slab = slab->next()) {
      if (slab->contains(p)) {
        return true;
      }
    }
    return false;
  }

  void verify() const {
    assert(max_slabs == 0 || num_slabs() <= max_slabs, "slab overflow (%u vs %u)", num_slabs(), max_slabs);
    assert((num_slabs() == 0 && !_current_slab && !_first_slab) ||                            // empty
           (num_slabs() == 1 && _current_slab && _first_slab == _current_slab) ||             // one slab
           (num_slabs() > 1 && _current_slab && _first_slab && _first_slab != _current_slab), // multiple slabs
           "invalid state: num_slabs %u, max_slabs %u, _current_slab " PTR_FORMAT ", _first_slab " PTR_FORMAT,
           num_slabs(), max_slabs, p2i(_current_slab), p2i(_first_slab));
    _freelist.verify();
    unsigned slabs_counted = 0;
    unsigned used_slots_counted = 0;
    const Slab* slab = _first_slab;
    while (slab != nullptr) {
      assert((slab->next() != nullptr && slab->hwm() == items_per_slab && _current_slab != slab) || // not last slab
             (slab->next() == nullptr && slab->hwm() <= items_per_slab && _current_slab == slab),   // last slab
             "invalid slab state");
      used_slots_counted += slab->hwm();
      slabs_counted++;
      assert(slabs_counted <= num_slabs(), "circle?");
      slab = slab->next();
    }
    assert(slabs_counted == num_slabs(), "slab count off (%u vs %u)", slabs_counted, num_slabs());
    unsigned expected_used_slots = num_free() + num_allocated();
    assert(used_slots_counted == expected_used_slots,
           "allocation count off (%u vs %u)", used_slots_counted, expected_used_slots);
  }
#endif // ASSERT

  // Number of items handed out
  unsigned num_allocated() const { return _num_allocated; }

  // Number of deallocated items
  unsigned num_free() const      { return _freelist.count(); }

  // Return number of slabs allocated
  unsigned num_slabs() const     { return _num_slabs; }

  size_t footprint() const       { return sizeof(Slab) * num_slabs(); }

  void* operator new(size_t l)   { return Allocator::allocate(l); }
  void  operator delete(void* p) { Allocator::deallocate(p); }

}; // FixedItemArray

#endif // SHARE_UTILITIES_FIXEDITEMARRAY_HPP
