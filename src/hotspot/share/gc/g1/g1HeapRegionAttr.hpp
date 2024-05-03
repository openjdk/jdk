/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPREGIONATTR_HPP
#define SHARE_GC_G1_G1HEAPREGIONATTR_HPP

#include "gc/g1/g1BiasedArray.hpp"
#include "gc/g1/g1HeapRegion.hpp"

// Per-region attributes often used during garbage collection to avoid costly
// lookups for that information all over the place.
struct G1HeapRegionAttr {
public:
  typedef int8_t region_type_t;
  // remset_is_tracked_t is essentially bool, but we need precise control
  // on the size, and sizeof(bool) is implementation specific.
  typedef uint8_t remset_is_tracked_t;
  // _is_pinned_t is essentially bool, but we want precise control
  // on the size, and sizeof(bool) is implementation specific.
  typedef uint8_t is_pinned_t;

private:
  remset_is_tracked_t _remset_is_tracked;
  region_type_t _type;
  is_pinned_t _is_pinned;

public:
  // Selection of the values for the _type field were driven to micro-optimize the
  // encoding and frequency of the checks.
  // The most common check for a given reference is whether the region is in the
  // collection set or not, and which generation this region is in.
  // The selected encoding allows us to use a single check (> NotInCSet) for the
  // former.
  //
  // The other values are used for objects in regions requiring various special handling,
  // eager reclamation of humongous objects or optional regions.
  static const region_type_t Optional     =  -4;    // The region is optional not in the current collection set.
  static const region_type_t HumongousCandidate    =  -3;    // The region is a humongous candidate not in the current collection set.
  static const region_type_t NewSurvivor  =  -2;    // The region is a new (ly allocated) survivor region.
  static const region_type_t NotInCSet    =  -1;    // The region is not in the collection set.
  static const region_type_t Young        =   0;    // The region is in the collection set and a young region.
  static const region_type_t Old          =   1;    // The region is in the collection set and an old region.
  static const region_type_t Num          =   2;

  G1HeapRegionAttr(region_type_t type = NotInCSet, bool remset_is_tracked = false, bool is_pinned = false) :
    _remset_is_tracked(remset_is_tracked ? 1 : 0), _type(type), _is_pinned(is_pinned ? 1 : 0) {
    assert(is_valid(), "Invalid type %d", _type);
  }

  region_type_t type() const           { return _type; }

  const char* get_type_str() const {
    switch (type()) {
      case Optional: return "Optional";
      case HumongousCandidate: return "HumongousCandidate";
      case NewSurvivor: return "NewSurvivor";
      case NotInCSet: return "NotInCSet";
      case Young: return "Young";
      case Old: return "Old";
      default: ShouldNotReachHere(); return "";
    }
  }

  bool remset_is_tracked() const     { return _remset_is_tracked != 0; }

  void set_new_survivor()              { _type = NewSurvivor; }
  bool is_pinned() const               { return _is_pinned != 0; }

  void set_old()                       { _type = Old; }
  void clear_humongous_candidate()               {
    assert(is_humongous_candidate() || !is_in_cset(), "must be");
    _type = NotInCSet;
  }

  void set_remset_is_tracked(bool value)      { _remset_is_tracked = value ? 1 : 0; }
  void set_is_pinned(bool value)       { _is_pinned = value ? 1 : 0; }

  bool is_in_cset_or_humongous_candidate() const { return is_in_cset() || is_humongous_candidate(); }
  bool is_in_cset() const              { return type() >= Young; }

  bool is_humongous_candidate() const            { return type() == HumongousCandidate; }
  bool is_new_survivor() const         { return type() == NewSurvivor; }
  bool is_young() const                { return type() == Young; }
  bool is_old() const                  { return type() == Old; }
  bool is_optional() const             { return type() == Optional; }

#ifdef ASSERT
  bool is_default() const              { return type() == NotInCSet; }
  bool is_valid() const                { return (type() >= Optional && type() < Num); }
#endif
};

// Table for all regions in the heap for above.
//
// We use this to speed up reference processing during young collection and
// quickly reclaim humongous objects. For the latter, at the start of GC, by adding
// it as a humongous region we enable special handling for that region. During the
// reference iteration closures, when we see a humongous region, we then simply mark
// it as referenced, i.e. live, and remove it from this table to prevent further
// processing on it.
//
// This means that this does NOT completely correspond to the information stored
// in a HeapRegion, but only to what is interesting for the current young collection.
class G1HeapRegionAttrBiasedMappedArray : public G1BiasedMappedArray<G1HeapRegionAttr> {
 protected:
  G1HeapRegionAttr default_value() const { return G1HeapRegionAttr(G1HeapRegionAttr::NotInCSet); }
 public:
  void set_optional(uintptr_t index, bool remset_is_tracked) {
    assert(get_by_index(index).is_default(),
           "Region attributes at index " INTPTR_FORMAT " should be default but is %s", index, get_by_index(index).get_type_str());
    set_by_index(index, G1HeapRegionAttr(G1HeapRegionAttr::Optional, remset_is_tracked));
  }

  void set_new_survivor_region(uintptr_t index) {
    assert(get_by_index(index).is_default(),
           "Region attributes at index " INTPTR_FORMAT " should be default but is %s", index, get_by_index(index).get_type_str());
    get_ref_by_index(index)->set_new_survivor();
  }

  void set_humongous_candidate(uintptr_t index) {
    assert(get_by_index(index).is_default(),
           "Region attributes at index " INTPTR_FORMAT " should be default but is %s", index, get_by_index(index).get_type_str());
    // Humongous candidates must have complete remset.
    const bool remset_is_tracked = true;
    // Humongous candidates can not be pinned.
    const bool region_is_pinned = false;
    set_by_index(index, G1HeapRegionAttr(G1HeapRegionAttr::HumongousCandidate, remset_is_tracked, region_is_pinned));
  }

  void clear_humongous_candidate(uintptr_t index) {
    get_ref_by_index(index)->clear_humongous_candidate();
  }

  bool is_humongous_candidate(uintptr_t index) {
    return get_ref_by_index(index)->is_humongous_candidate();
  }

  void set_remset_is_tracked(uintptr_t index, bool remset_is_tracked) {
    get_ref_by_index(index)->set_remset_is_tracked(remset_is_tracked);
  }

  void set_is_pinned(uintptr_t index, bool is_pinned) {
    get_ref_by_index(index)->set_is_pinned(is_pinned);
  }

  void set_in_young(uintptr_t index, bool is_pinned) {
    assert(get_by_index(index).is_default(),
           "Region attributes at index " INTPTR_FORMAT " should be default but is %s", index, get_by_index(index).get_type_str());
    set_by_index(index, G1HeapRegionAttr(G1HeapRegionAttr::Young, true, is_pinned));
  }

  void set_in_old(uintptr_t index, bool remset_is_tracked) {
    assert(get_by_index(index).is_default(),
           "Region attributes at index " INTPTR_FORMAT " should be default but is %s", index, get_by_index(index).get_type_str());
    // We do not select regions with pinned objects into the collection set.
    const bool region_is_pinned = false;
    set_by_index(index, G1HeapRegionAttr(G1HeapRegionAttr::Old, remset_is_tracked, region_is_pinned));
  }

  bool is_in_cset_or_humongous_candidate(HeapWord* addr) const { return at(addr).is_in_cset_or_humongous_candidate(); }
  bool is_in_cset(HeapWord* addr) const { return at(addr).is_in_cset(); }
  bool is_in_cset(const HeapRegion* hr) const { return get_by_index(hr->hrm_index()).is_in_cset(); }
  G1HeapRegionAttr at(HeapWord* addr) const { return get_by_address(addr); }
  void clear() { G1BiasedMappedArray<G1HeapRegionAttr>::clear(); }
  void clear(const HeapRegion* hr) { return set_by_index(hr->hrm_index(), G1HeapRegionAttr(G1HeapRegionAttr::NotInCSet)); }
};

#endif // SHARE_GC_G1_G1HEAPREGIONATTR_HPP
