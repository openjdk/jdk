/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSET_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSET_HPP

#include "gc_implementation/g1/heapRegion.hpp"

// Large buffer for some cases where the output might be larger than normal.
#define HRS_ERR_MSG_BUFSZ 512
typedef FormatBuffer<HRS_ERR_MSG_BUFSZ> hrs_err_msg;

// Set verification will be forced either if someone defines
// HEAP_REGION_SET_FORCE_VERIFY to be 1, or in builds in which
// asserts are compiled in.
#ifndef HEAP_REGION_SET_FORCE_VERIFY
#define HEAP_REGION_SET_FORCE_VERIFY defined(ASSERT)
#endif // HEAP_REGION_SET_FORCE_VERIFY

//////////////////// HeapRegionSetBase ////////////////////

// Base class for all the classes that represent heap region sets. It
// contains the basic attributes that each set needs to maintain
// (e.g., length, region num, used bytes sum) plus any shared
// functionality (e.g., verification).

class hrs_ext_msg;

typedef enum {
  HRSPhaseNone,
  HRSPhaseEvacuation,
  HRSPhaseCleanup,
  HRSPhaseFullGC
} HRSPhase;

class HRSPhaseSetter;

class HeapRegionSetBase VALUE_OBJ_CLASS_SPEC {
  friend class hrs_ext_msg;
  friend class HRSPhaseSetter;
  friend class VMStructs;

protected:
  static uint _unrealistically_long_length;

  // The number of regions added to the set. If the set contains
  // only humongous regions, this reflects only 'starts humongous'
  // regions and does not include 'continues humongous' ones.
  uint _length;

  // The total number of regions represented by the set. If the set
  // does not contain humongous regions, this should be the same as
  // _length. If the set contains only humongous regions, this will
  // include the 'continues humongous' regions.
  uint _region_num;

  // We don't keep track of the total capacity explicitly, we instead
  // recalculate it based on _region_num and the heap region size.

  // The sum of used bytes in the all the regions in the set.
  size_t _total_used_bytes;

  const char* _name;

  bool        _verify_in_progress;
  uint        _calc_length;
  uint        _calc_region_num;
  size_t      _calc_total_capacity_bytes;
  size_t      _calc_total_used_bytes;

  // This is here so that it can be used in the subclasses to assert
  // something different depending on which phase the GC is in. This
  // can be particularly helpful in the check_mt_safety() methods.
  static HRSPhase _phase;

  // Only used by HRSPhaseSetter.
  static void clear_phase();
  static void set_phase(HRSPhase phase);

  // verify_region() is used to ensure that the contents of a region
  // added to / removed from a set are consistent. Different sets
  // make different assumptions about the regions added to them. So
  // each set can override verify_region_extra(), which is called
  // from verify_region(), and do any extra verification it needs to
  // perform in that.
  virtual const char* verify_region_extra(HeapRegion* hr) { return NULL; }
  bool verify_region(HeapRegion* hr,
                     HeapRegionSetBase* expected_containing_set);

  // Indicates whether all regions in the set should be humongous or
  // not. Only used during verification.
  virtual bool regions_humongous() = 0;

  // Indicates whether all regions in the set should be empty or
  // not. Only used during verification.
  virtual bool regions_empty() = 0;

  // Subclasses can optionally override this to do MT safety protocol
  // checks. It is called in an assert from all methods that perform
  // updates on the set (and subclasses should also call it too).
  virtual bool check_mt_safety() { return true; }

  // fill_in_ext_msg() writes the the values of the set's attributes
  // in the custom err_msg (hrs_ext_msg). fill_in_ext_msg_extra()
  // allows subclasses to append further information.
  virtual void fill_in_ext_msg_extra(hrs_ext_msg* msg) { }
  void fill_in_ext_msg(hrs_ext_msg* msg, const char* message);

  // It updates the fields of the set to reflect hr being added to
  // the set.
  inline void update_for_addition(HeapRegion* hr);

  // It updates the fields of the set to reflect hr being added to
  // the set and tags the region appropriately.
  inline void add_internal(HeapRegion* hr);

  // It updates the fields of the set to reflect hr being removed
  // from the set.
  inline void update_for_removal(HeapRegion* hr);

  // It updates the fields of the set to reflect hr being removed
  // from the set and tags the region appropriately.
  inline void remove_internal(HeapRegion* hr);

  // It clears all the fields of the sets. Note: it will not iterate
  // over the set and remove regions from it. It assumes that the
  // caller has already done so. It will literally just clear the fields.
  virtual void clear();

  HeapRegionSetBase(const char* name);

public:
  static void set_unrealistically_long_length(uint len);

  const char* name() { return _name; }

  uint length() { return _length; }

  bool is_empty() { return _length == 0; }

  uint region_num() { return _region_num; }

  size_t total_capacity_bytes() {
    return (size_t) region_num() << HeapRegion::LogOfHRGrainBytes;
  }

  size_t total_used_bytes() { return _total_used_bytes; }

  virtual void verify();
  void verify_start();
  void verify_next_region(HeapRegion* hr);
  void verify_end();

#if HEAP_REGION_SET_FORCE_VERIFY
  void verify_optional() {
    verify();
  }
#else // HEAP_REGION_SET_FORCE_VERIFY
  void verify_optional() { }
#endif // HEAP_REGION_SET_FORCE_VERIFY

  virtual void print_on(outputStream* out, bool print_contents = false);
};

// Customized err_msg for heap region sets. Apart from a
// assert/guarantee-specific message it also prints out the values of
// the fields of the associated set. This can be very helpful in
// diagnosing failures.

class hrs_ext_msg : public hrs_err_msg {
public:
  hrs_ext_msg(HeapRegionSetBase* set, const char* message) : hrs_err_msg("") {
    set->fill_in_ext_msg(this, message);
  }
};

class HRSPhaseSetter {
public:
  HRSPhaseSetter(HRSPhase phase) {
    HeapRegionSetBase::set_phase(phase);
  }
  ~HRSPhaseSetter() {
    HeapRegionSetBase::clear_phase();
  }
};

// These two macros are provided for convenience, to keep the uses of
// these two asserts a bit more concise.

#define hrs_assert_mt_safety_ok(_set_)                                        \
  do {                                                                        \
    assert((_set_)->check_mt_safety(), hrs_ext_msg((_set_), "MT safety"));    \
  } while (0)

#define hrs_assert_region_ok(_set_, _hr_, _expected_)                         \
  do {                                                                        \
    assert((_set_)->verify_region((_hr_), (_expected_)),                      \
           hrs_ext_msg((_set_), "region verification"));                      \
  } while (0)

//////////////////// HeapRegionSet ////////////////////

#define hrs_assert_sets_match(_set1_, _set2_)                                 \
  do {                                                                        \
    assert(((_set1_)->regions_humongous() ==                                  \
                                            (_set2_)->regions_humongous()) && \
           ((_set1_)->regions_empty() == (_set2_)->regions_empty()),          \
           hrs_err_msg("the contents of set %s and set %s should match",      \
                       (_set1_)->name(), (_set2_)->name()));                  \
  } while (0)

// This class represents heap region sets whose members are not
// explicitly tracked. It's helpful to group regions using such sets
// so that we can reason about all the region groups in the heap using
// the same interface (namely, the HeapRegionSetBase API).

class HeapRegionSet : public HeapRegionSetBase {
protected:
  virtual const char* verify_region_extra(HeapRegion* hr) {
    if (hr->next() != NULL) {
      return "next() should always be NULL as we do not link the regions";
    }

    return HeapRegionSetBase::verify_region_extra(hr);
  }

  HeapRegionSet(const char* name) : HeapRegionSetBase(name) {
    clear();
  }

public:
  // It adds hr to the set. The region should not be a member of
  // another set.
  inline void add(HeapRegion* hr);

  // It removes hr from the set. The region should be a member of
  // this set.
  inline void remove(HeapRegion* hr);

  // It removes a region from the set. Instead of updating the fields
  // of the set to reflect this removal, it accumulates the updates
  // in proxy_set. The idea is that proxy_set is thread-local to
  // avoid multiple threads updating the fields of the set
  // concurrently and having to synchronize. The method
  // update_from_proxy() will update the fields of the set from the
  // proxy_set.
  inline void remove_with_proxy(HeapRegion* hr, HeapRegionSet* proxy_set);

  // After multiple calls to remove_with_proxy() the updates to the
  // fields of the set are accumulated in proxy_set. This call
  // updates the fields of the set from proxy_set.
  void update_from_proxy(HeapRegionSet* proxy_set);
};

//////////////////// HeapRegionLinkedList ////////////////////

// A set that links all the regions added to it in a singly-linked
// list. We should try to avoid doing operations that iterate over
// such lists in performance critical paths. Typically we should
// add / remove one region at a time or concatenate two lists. All
// those operations are done in constant time.

class HeapRegionLinkedListIterator;

class HeapRegionLinkedList : public HeapRegionSetBase {
  friend class HeapRegionLinkedListIterator;

private:
  HeapRegion* _head;
  HeapRegion* _tail;

  // These are provided for use by the friend classes.
  HeapRegion* head() { return _head; }
  HeapRegion* tail() { return _tail; }

protected:
  virtual void fill_in_ext_msg_extra(hrs_ext_msg* msg);

  // See the comment for HeapRegionSetBase::clear()
  virtual void clear();

  HeapRegionLinkedList(const char* name) : HeapRegionSetBase(name) {
    clear();
  }

public:
  // It adds hr to the list as the new head. The region should not be
  // a member of another set.
  inline void add_as_head(HeapRegion* hr);

  // It adds hr to the list as the new tail. The region should not be
  // a member of another set.
  inline void add_as_tail(HeapRegion* hr);

  // It removes and returns the head of the list. It assumes that the
  // list is not empty so it will return a non-NULL value.
  inline HeapRegion* remove_head();

  // Convenience method.
  inline HeapRegion* remove_head_or_null();

  // It moves the regions from from_list to this list and empties
  // from_list. The new regions will appear in the same order as they
  // were in from_list and be linked in the beginning of this list.
  void add_as_head(HeapRegionLinkedList* from_list);

  // It moves the regions from from_list to this list and empties
  // from_list. The new regions will appear in the same order as they
  // were in from_list and be linked in the end of this list.
  void add_as_tail(HeapRegionLinkedList* from_list);

  // It empties the list by removing all regions from it.
  void remove_all();

  // It removes all regions in the list that are pending for removal
  // (i.e., they have been tagged with "pending_removal"). The list
  // must not be empty, target_count should reflect the exact number
  // of regions that are pending for removal in the list, and
  // target_count should be > 1 (currently, we never need to remove a
  // single region using this).
  void remove_all_pending(uint target_count);

  virtual void verify();

  virtual void print_on(outputStream* out, bool print_contents = false);
};

//////////////////// HeapRegionLinkedListIterator ////////////////////

// Iterator class that provides a convenient way to iterate over the
// regions of a HeapRegionLinkedList instance.

class HeapRegionLinkedListIterator : public StackObj {
private:
  HeapRegionLinkedList* _list;
  HeapRegion*           _curr;

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
    assert(_list->verify_region(hr, _list), "region verification");
    _curr = hr->next();
    return hr;
  }

  HeapRegionLinkedListIterator(HeapRegionLinkedList* list)
    : _curr(NULL), _list(list) {
    _curr = list->head();
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSET_HPP
