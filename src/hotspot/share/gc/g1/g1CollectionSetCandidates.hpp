/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1COLLECTIONSETCANDIDATES_HPP
#define SHARE_GC_G1_G1COLLECTIONSETCANDIDATES_HPP

#include "gc/g1/g1CardSetGroup.hpp"
#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"

class G1HeapRegion;

// Tracks collection set candidate regions organized in two card set group lists. Their
// groups are sorted by decreasing gc efficiency.
//
// * from_marking_groups: the set of card set groups selected by the concurrent cycle to be
//                        evacuated to keep overall heap occupancy stable.
//                        They are guaranteed to be evacuated and cleared out during
//                        the mixed phase.
//
// * retained_groups: contains the card set groups from regions whose evacuation in
//                    previous garbage collections failed.
//                    Any young collection will try to evacuate them.
//
class G1CollectionSetCandidates : public CHeapObj<mtGC> {

  enum class CandidateOrigin : uint8_t {
    Invalid,
    Marking,                   // This region has been determined as candidate by the concurrent cycle.
    Retained,                  // This region has been added because it has been retained after evacuation.
    Verify                     // Special value for verification.
  };

  CandidateOrigin* _contains_map;
  G1CardSetGroupList _from_marking_groups; // Set of groups selected by the concurrent cycle.
  // Set of regions retained due to evacuation failure. Groups added to this list
  // should contain only one region each, making it easier to evacuate retained regions
  // in any young collection.
  G1CardSetGroupList _retained_groups;
  uint _max_num_regions;

  // The number of regions from the last merge of candidates from the marking.
  uint _num_last_marking_candidate_regions;

  bool is_from_marking(G1HeapRegion* r) const;

public:
  G1CollectionSetCandidates();
  ~G1CollectionSetCandidates();

  G1CardSetGroupList& from_marking_groups() { return _from_marking_groups; }
  G1CardSetGroupList& retained_groups() { return _retained_groups; }

  void initialize(uint max_num_regions);

  void clear();

  // Merge collection set candidate regions from marking into the current from_marking candidate
  // group list (which needs to be empty).
  void set_candidates_from_marking(GrowableArrayCHeap<G1HeapRegion*, mtGC>* selected);
  // The number of regions most recently merged using set_candidates_from_marking(). Used for calculating
  // minimum collection set regions.
  uint num_last_marking_candidate_regions() const { return _num_last_marking_candidate_regions; }

  void sort_by_efficiency();

  void sort_marking_by_efficiency();

  // Add the given region to the set of retained regions without regards to the
  // gc efficiency sorting. The retained regions must be re-sorted manually later.
  void add_retained_region_unsorted(G1HeapRegion* r);
  // Remove the given groups from this list. All given card set groups must be part
  // of the candidates.
  void remove(G1CardSetGroupList* other);

  bool contains(const G1HeapRegion* r) const;

  const char* get_short_type_str(const G1HeapRegion* r) const;

  bool is_empty() const;

  bool has_more_marking_candidates() const;
  uint num_marking_regions() const;
  uint num_retained_regions() const;

private:
  void verify_helper(G1CardSetGroupList* list, uint& from_marking, CandidateOrigin* verify_map) PRODUCT_RETURN;

public:
  void verify() PRODUCT_RETURN;

  uint num_regions() const { return num_marking_regions() + num_retained_regions(); }

  template<typename Func>
  void iterate_regions(Func&& f) const;
};

#endif /* SHARE_GC_G1_G1COLLECTIONSETCANDIDATES_HPP */
