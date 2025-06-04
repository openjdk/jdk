/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "utilities/growableArray.hpp"

class G1CollectionSetCandidates;
class G1CSetCandidateGroupList;
class G1HeapRegion;
class G1HeapRegionClosure;

struct G1CollectionSetCandidateInfo {
  G1HeapRegion* _r;
  uint _num_unreclaimed;          // Number of GCs this region has been found unreclaimable.

  G1CollectionSetCandidateInfo() : G1CollectionSetCandidateInfo(nullptr) { }
  G1CollectionSetCandidateInfo(G1HeapRegion* r) : _r(r), _num_unreclaimed(0) { }

  bool update_num_unreclaimed() {
    ++_num_unreclaimed;
    return _num_unreclaimed < G1NumCollectionsKeepPinned;
  }

  static int compare_region_gc_efficiency(G1CollectionSetCandidateInfo* ci1, G1CollectionSetCandidateInfo* ci2);
};

using G1CSetCandidateGroupIterator = GrowableArrayIterator<G1CollectionSetCandidateInfo>;

// G1CSetCandidateGroup groups candidate regions that will be selected for evacuation at the same time.
// Grouping occurs both for candidates from marking or regions retained during evacuation failure, but a group
// can not contain regions from both types of regions.
//
// Humongous objects are excluded from the candidate groups because regions associated with these
// objects are never selected for evacuation.
//
// All regions in the group share a G1CardSet instance, which tracks remembered set entries for the
// regions in the group. We do not have track to cross-region references for regions that are in the
// same group saving memory.
class G1CSetCandidateGroup : public CHeapObj<mtGCCardSet>{
  GrowableArray<G1CollectionSetCandidateInfo> _candidates;

  G1CardSetMemoryManager _card_set_mm;

  // The set of cards in the Java heap
  G1CardSet _card_set;

  size_t _reclaimable_bytes;
  double _gc_efficiency;

  // The _group_id is primarily used when printing out per-region liveness information,
  // making it easier to associate regions with their assigned G1CSetCandidateGroup, if any.
  // Note:
  // * _group_id 0 is reserved for special G1CSetCandidateGroups that hold only a single region,
  //    such as G1CSetCandidateGroups for retained regions.
  // * _group_id 1 is reserved for the G1CSetCandidateGroup that contains all young regions.
  const uint _group_id;
  static uint _next_group_id;
public:
  G1CSetCandidateGroup();
  G1CSetCandidateGroup(G1CardSetConfiguration* config, G1MonotonicArenaFreePool* card_set_freelist_pool, uint group_id);
  ~G1CSetCandidateGroup() {
    assert(length() == 0, "post condition!");
  }

  void add(G1HeapRegion* hr);
  void add(G1CollectionSetCandidateInfo& hr_info);

  uint length() const { return (uint)_candidates.length(); }

  G1CardSet* card_set() { return &_card_set; }
  const G1CardSet* card_set() const { return &_card_set; }

  uint group_id() const { return _group_id; }

  void calculate_efficiency();

  size_t liveness() const;
  // Comparison function to order regions in decreasing GC efficiency order. This
  // will cause regions with a lot of live objects and large remembered sets to end
  // up at the end of the list.
  static int compare_gc_efficiency(G1CSetCandidateGroup** gr1, G1CSetCandidateGroup** gr2);

  double gc_efficiency() const { return _gc_efficiency; }

  G1HeapRegion* region_at(uint i) const { return _candidates.at(i)._r; }

  G1CollectionSetCandidateInfo* at(uint i) { return &_candidates.at(i); }

  double predict_group_total_time_ms() const;

  G1MonotonicArenaMemoryStats card_set_memory_stats() const {
    return _card_set_mm.memory_stats();
  }

  void clear(bool uninstall_group_cardset = false);

  G1CSetCandidateGroupIterator begin() const {
    return _candidates.begin();
  }

  G1CSetCandidateGroupIterator end() const {
    return _candidates.end();
  }

  static void reset_next_group_id() {
    _next_group_id = 2;
  }
};

using G1CSetCandidateGroupListIterator = GrowableArrayIterator<G1CSetCandidateGroup*>;

class G1CSetCandidateGroupList {
  GrowableArray<G1CSetCandidateGroup*> _groups;
  volatile uint _num_regions;

public:
  G1CSetCandidateGroupList();
  void append(G1CSetCandidateGroup* group);

  // Delete all groups from the list. The cardset cleanup for regions within the
  // groups could have been done elsewhere (e.g. when adding groups to the
  // collection set or to retained regions). The uninstall_group_cardset is set to
  // true if cleanup needs to happen as we clear the groups from the list.
  void clear(bool uninstall_group_cardset = false);

  G1CSetCandidateGroup* at(uint index);

  uint length() const { return (uint)_groups.length(); }

  uint num_regions() const { return _num_regions; }

  void remove_selected(uint count, uint num_regions);

  // Removes any candidate groups stored in this list and also in the other list. The other
  // list may only contain candidate groups in this list, sorted by gc efficiency. It need
  // not be a prefix of this list.
  // E.g. if this list is "A B G H", the other list may be "A G H", but not "F" (not in
  // this list) or "A H G" (wrong order).
  void remove(G1CSetCandidateGroupList* other);

  void prepare_for_scan();

  void sort_by_efficiency();

  GrowableArray<G1CSetCandidateGroup*>*  groups() {
    return &_groups;
  }

  void verify() const PRODUCT_RETURN;

  G1CSetCandidateGroupListIterator begin() const {
    return _groups.begin();
  }

  G1CSetCandidateGroupListIterator end() const {
    return _groups.end();
  }

  template<typename Func>
  void iterate(Func&& f) const;
};

// Tracks all collection set candidates, i.e. region groups that could/should be evacuated soon.
//
// These candidate groups are tracked in two list of region groups, sorted by decreasing
// "gc efficiency".
//
// * from_marking_groups: the set of region groups selected by concurrent marking to be
//                        evacuated to keep overall heap occupancy stable.
//                        They are guaranteed to be evacuated and cleared out during
//                        the mixed phase.
//
// * retained_groups: set of region groups selected for evacuation during evacuation
//                    failure.
//                    Any young collection will try to evacuate them.
//
class G1CollectionSetCandidates : public CHeapObj<mtGC> {

  enum class CandidateOrigin : uint8_t {
    Invalid,
    Marking,                   // This region has been determined as candidate by concurrent marking.
    Retained,                  // This region has been added because it has been retained after evacuation.
    Verify                     // Special value for verification.
  };

  CandidateOrigin* _contains_map;
  G1CSetCandidateGroupList _from_marking_groups; // Set of regions selected by concurrent marking.
  // Set of regions retained due to evacuation failure. Groups added to this list
  // should contain only one region each, making it easier to evacuate retained regions
  // in any young collection.
  G1CSetCandidateGroupList _retained_groups;
  uint _max_regions;

  // The number of regions from the last merge of candidates from the marking.
  uint _last_marking_candidates_length;

  bool is_from_marking(G1HeapRegion* r) const;

public:
  G1CollectionSetCandidates();
  ~G1CollectionSetCandidates();

  G1CSetCandidateGroupList& from_marking_groups() { return _from_marking_groups; }
  G1CSetCandidateGroupList& retained_groups() { return _retained_groups; }

  void initialize(uint max_regions);

  void clear();

  // Merge collection set candidates from marking into the current marking list
  // (which needs to be empty).
  void set_candidates_from_marking(G1CollectionSetCandidateInfo* candidate_infos,
                                   uint num_infos);
  // The most recent length of the list that had been merged last via
  // set_candidates_from_marking(). Used for calculating minimum collection set
  // regions.
  uint last_marking_candidates_length() const { return _last_marking_candidates_length; }

  void sort_by_efficiency();

  void sort_marking_by_efficiency();

  // Add the given region to the set of retained regions without regards to the
  // gc efficiency sorting. The retained regions must be re-sorted manually later.
  void add_retained_region_unsorted(G1HeapRegion* r);
  // Remove the given groups from the candidates. All given regions must be part
  // of the candidates.
  void remove(G1CSetCandidateGroupList* other);

  bool contains(const G1HeapRegion* r) const;

  const char* get_short_type_str(const G1HeapRegion* r) const;

  bool is_empty() const;

  bool has_more_marking_candidates() const;
  uint marking_regions_length() const;
  uint retained_regions_length() const;

private:
  void verify_helper(G1CSetCandidateGroupList* list, uint& from_marking, CandidateOrigin* verify_map) PRODUCT_RETURN;

public:
  void verify() PRODUCT_RETURN;

  uint length() const { return marking_regions_length() + retained_regions_length(); }

  template<typename Func>
  void iterate_regions(Func&& f) const;
};

#endif /* SHARE_GC_G1_G1COLLECTIONSETCANDIDATES_HPP */
