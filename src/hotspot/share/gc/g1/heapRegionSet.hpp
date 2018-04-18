/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_HEAPREGIONSET_HPP
#define SHARE_VM_GC_G1_HEAPREGIONSET_HPP

#include "gc/g1/heapRegion.hpp"
#include "utilities/macros.hpp"

#define assert_heap_region_set(p, message) \
  do {                                     \
    assert((p), "[%s] %s ln: %u",          \
           name(), message, length());     \
  } while (0)

#define guarantee_heap_region_set(p, message) \
  do {                                        \
    guarantee((p), "[%s] %s ln: %u",          \
              name(), message, length());     \
  } while (0)

#define assert_free_region_list(p, message)                          \
  do {                                                               \
    assert((p), "[%s] %s ln: %u hd: " PTR_FORMAT " tl: " PTR_FORMAT, \
           name(), message, length(), p2i(_head), p2i(_tail));       \
  } while (0)


class HRSMtSafeChecker : public CHeapObj<mtGC> {
public:
  virtual void check() = 0;
};

class MasterFreeRegionListMtSafeChecker    : public HRSMtSafeChecker { public: void check(); };
class HumongousRegionSetMtSafeChecker      : public HRSMtSafeChecker { public: void check(); };
class OldRegionSetMtSafeChecker            : public HRSMtSafeChecker { public: void check(); };

// Base class for all the classes that represent heap region sets. It
// contains the basic attributes that each set needs to maintain
// (e.g., length, region num, used bytes sum) plus any shared
// functionality (e.g., verification).

class HeapRegionSetBase {
  friend class VMStructs;
private:
  bool _is_humongous;
  bool _is_free;
  HRSMtSafeChecker* _mt_safety_checker;

protected:
  // The number of regions in to the set.
  uint _length;

  const char* _name;

  bool _verify_in_progress;

  // verify_region() is used to ensure that the contents of a region
  // added to / removed from a set are consistent.
  void verify_region(HeapRegion* hr) PRODUCT_RETURN;

  // Indicates whether all regions in the set should be humongous or
  // not. Only used during verification.
  bool regions_humongous() { return _is_humongous; }

  // Indicates whether all regions in the set should be free or
  // not. Only used during verification.
  bool regions_free() { return _is_free; }

  void check_mt_safety() {
    if (_mt_safety_checker != NULL) {
      _mt_safety_checker->check();
    }
  }

  HeapRegionSetBase(const char* name, bool humongous, bool free, HRSMtSafeChecker* mt_safety_checker);

public:
  const char* name() { return _name; }

  uint length() const { return _length; }

  bool is_empty() { return _length == 0; }

  // It updates the fields of the set to reflect hr being added to
  // the set and tags the region appropriately.
  inline void add(HeapRegion* hr);

  // It updates the fields of the set to reflect hr being removed
  // from the set and tags the region appropriately.
  inline void remove(HeapRegion* hr);

  virtual void verify();
  void verify_start();
  void verify_next_region(HeapRegion* hr);
  void verify_end();

  void verify_optional() { DEBUG_ONLY(verify();) }

  virtual void print_on(outputStream* out, bool print_contents = false);
};

#define hrs_assert_sets_match(_set1_, _set2_)                                  \
  do {                                                                         \
    assert(((_set1_)->regions_humongous() == (_set2_)->regions_humongous()) && \
           ((_set1_)->regions_free() == (_set2_)->regions_free()),             \
           "the contents of set %s and set %s should match",                   \
           (_set1_)->name(),                                                   \
           (_set2_)->name());                                                  \
  } while (0)

// This class represents heap region sets whose members are not
// explicitly tracked. It's helpful to group regions using such sets
// so that we can reason about all the region groups in the heap using
// the same interface (namely, the HeapRegionSetBase API).

class HeapRegionSet : public HeapRegionSetBase {
public:
  HeapRegionSet(const char* name, bool humongous, HRSMtSafeChecker* mt_safety_checker):
    HeapRegionSetBase(name, humongous, false /* free */, mt_safety_checker) { }

  void bulk_remove(const uint removed) {
    _length -= removed;
  }
};

// A set that links all the regions added to it in a doubly-linked
// sorted list. We should try to avoid doing operations that iterate over
// such lists in performance critical paths. Typically we should
// add / remove one region at a time or concatenate two lists.

class FreeRegionListIterator;

class FreeRegionList : public HeapRegionSetBase {
  friend class FreeRegionListIterator;

private:
  HeapRegion* _head;
  HeapRegion* _tail;

  // _last is used to keep track of where we added an element the last
  // time. It helps to improve performance when adding several ordered items in a row.
  HeapRegion* _last;

  static uint _unrealistically_long_length;

  inline HeapRegion* remove_from_head_impl();
  inline HeapRegion* remove_from_tail_impl();

protected:
  // See the comment for HeapRegionSetBase::clear()
  virtual void clear();

public:
  FreeRegionList(const char* name, HRSMtSafeChecker* mt_safety_checker = NULL):
    HeapRegionSetBase(name, false /* humongous */, true /* empty */, mt_safety_checker) {
    clear();
  }

  void verify_list();

#ifdef ASSERT
  bool contains(HeapRegion* hr) const {
    return hr->containing_set() == this;
  }
#endif

  static void set_unrealistically_long_length(uint len);

  // Add hr to the list. The region should not be a member of another set.
  // Assumes that the list is ordered and will preserve that order. The order
  // is determined by hrm_index.
  inline void add_ordered(HeapRegion* hr);

  // Removes from head or tail based on the given argument.
  HeapRegion* remove_region(bool from_head);

  // Merge two ordered lists. The result is also ordered. The order is
  // determined by hrm_index.
  void add_ordered(FreeRegionList* from_list);

  // It empties the list by removing all regions from it.
  void remove_all();

  // Remove all (contiguous) regions from first to first + num_regions -1 from
  // this list.
  // Num_regions must be > 1.
  void remove_starting_at(HeapRegion* first, uint num_regions);

  virtual void verify();
};

// Iterator class that provides a convenient way to iterate over the
// regions of a FreeRegionList.

class FreeRegionListIterator : public StackObj {
private:
  FreeRegionList* _list;
  HeapRegion*     _curr;

public:
  bool more_available() {
    return _curr != NULL;
  }

  HeapRegion* get_next() {
    assert(more_available(),
           "get_next() should be called when more regions are available");

    // If we are going to introduce a count in the iterator we should
    // do the "cycle" check.

    HeapRegion* hr = _curr;
    _list->verify_region(hr);
    _curr = hr->next();
    return hr;
  }

  FreeRegionListIterator(FreeRegionList* list) : _curr(NULL), _list(list) {
    _curr = list->_head;
  }
};

#endif // SHARE_VM_GC_G1_HEAPREGIONSET_HPP
