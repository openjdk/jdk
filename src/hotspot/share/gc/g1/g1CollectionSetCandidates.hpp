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

#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/growableArray.hpp"

class G1CollectionCandidateList;
class G1CollectionSetCandidates;
class G1HeapRegion;
class G1HeapRegionClosure;

using G1CollectionCandidateRegionListIterator = GrowableArrayIterator<G1HeapRegion*>;

// A set of G1HeapRegion*, a thin wrapper around GrowableArray.
class G1CollectionCandidateRegionList {
  GrowableArray<G1HeapRegion*> _regions;

public:
  G1CollectionCandidateRegionList();

  // Append a G1HeapRegion to the end of this list. The region must not be in the list
  // already.
  void append(G1HeapRegion* r);
  // Remove the given list of G1HeapRegion* from this list. The given list must be a prefix
  // of this list.
  void remove_prefix(G1CollectionCandidateRegionList* list);

  // Empty contents of the list.
  void clear();

  G1HeapRegion* at(uint index);

  uint length() const { return (uint)_regions.length(); }

  G1CollectionCandidateRegionListIterator begin() const { return _regions.begin(); }
  G1CollectionCandidateRegionListIterator end() const { return _regions.end(); }
};

struct G1CollectionSetCandidateInfo {
  G1HeapRegion* _r;
  double _gc_efficiency;
  uint _num_unreclaimed;          // Number of GCs this region has been found unreclaimable.

  G1CollectionSetCandidateInfo() : G1CollectionSetCandidateInfo(nullptr, 0.0) { }
  G1CollectionSetCandidateInfo(G1HeapRegion* r, double gc_efficiency) : _r(r), _gc_efficiency(gc_efficiency), _num_unreclaimed(0) { }

  bool update_num_unreclaimed() {
    ++_num_unreclaimed;
    return _num_unreclaimed < G1NumCollectionsKeepPinned;
  }
};

class G1CollectionCandidateListIterator : public StackObj {
  G1CollectionCandidateList* _which;
  uint _position;

public:
  G1CollectionCandidateListIterator(G1CollectionCandidateList* which, uint position);

  G1CollectionCandidateListIterator& operator++();
  G1CollectionSetCandidateInfo* operator*();

  bool operator==(const G1CollectionCandidateListIterator& rhs);
  bool operator!=(const G1CollectionCandidateListIterator& rhs);
};

// List of collection set candidates (regions with their efficiency) ordered by
// decreasing gc efficiency.
class G1CollectionCandidateList : public CHeapObj<mtGC> {
  friend class G1CollectionCandidateListIterator;

  GrowableArray<G1CollectionSetCandidateInfo> _candidates;

public:
  G1CollectionCandidateList();

  // Put the given set of candidates into this list, preserving the efficiency ordering.
  void set(G1CollectionSetCandidateInfo* candidate_infos, uint num_infos);
  // Add the given G1HeapRegion to this list at the end, (potentially) making the list unsorted.
  void append_unsorted(G1HeapRegion* r);
  // Restore sorting order by decreasing gc efficiency, using the existing efficiency
  // values.
  void sort_by_efficiency();
  // Removes any heap regions stored in this list also in the other list. The other
  // list may only contain regions in this list, sorted by gc efficiency. It need
  // not be a prefix of this list. Returns the number of regions removed.
  // E.g. if this list is "A B G H", the other list may be "A G H", but not "F" (not in
  // this list) or "A H G" (wrong order).
  void remove(G1CollectionCandidateRegionList* other);

  void clear();

  G1CollectionSetCandidateInfo& at(uint position) { return _candidates.at(position); }

  uint length() const { return (uint)_candidates.length(); }

  void verify() PRODUCT_RETURN;

  // Comparison function to order regions in decreasing GC efficiency order. This
  // will cause regions with a lot of live objects and large remembered sets to end
  // up at the end of the list.
  static int compare_gc_efficiency(G1CollectionSetCandidateInfo* ci1, G1CollectionSetCandidateInfo* ci2);

  static int compare_reclaimble_bytes(G1CollectionSetCandidateInfo* ci1, G1CollectionSetCandidateInfo* ci2);

  G1CollectionCandidateListIterator begin() {
    return G1CollectionCandidateListIterator(this, 0);
  }

  G1CollectionCandidateListIterator end() {
    return G1CollectionCandidateListIterator(this, length());
  }
};

// Iterator for G1CollectionSetCandidates. There are no guarantees on the order
// of the regions returned.
class G1CollectionSetCandidatesIterator : public StackObj {
  G1CollectionSetCandidates* _which;
    uint _position;

  public:
  G1CollectionSetCandidatesIterator(G1CollectionSetCandidates* which, uint position);

  G1CollectionSetCandidatesIterator& operator++();
  G1HeapRegion* operator*();

  bool operator==(const G1CollectionSetCandidatesIterator& rhs);
  bool operator!=(const G1CollectionSetCandidatesIterator& rhs);
};

// Tracks all collection set candidates, i.e. regions that could/should be evacuated soon.
//
// These candidate regions are tracked in two list of regions, sorted by decreasing
// "gc efficiency".
//
// * marking_regions: the set of regions selected by concurrent marking to be
//                    evacuated to keep overall heap occupancy stable.
//                    They are guaranteed to be evacuated and cleared out during
//                    the mixed phase.
//
// * retained_regions: set of regions selected for evacuation during evacuation
//                     failure.
//                     Any young collection will try to evacuate them.
//
class G1CollectionSetCandidates : public CHeapObj<mtGC> {
  friend class G1CollectionSetCandidatesIterator;

  enum class CandidateOrigin : uint8_t {
    Invalid,
    Marking,                   // This region has been determined as candidate by concurrent marking.
    Retained,                  // This region has been added because it has been retained after evacuation.
    Verify                     // Special value for verification.
  };

  G1CollectionCandidateList _marking_regions;  // Set of regions selected by concurrent marking.
  G1CollectionCandidateList _retained_regions; // Set of regions selected from evacuation failed regions.

  CandidateOrigin* _contains_map;
  uint _max_regions;

  // The number of regions from the last merge of candidates from the marking.
  uint _last_marking_candidates_length;

  bool is_from_marking(G1HeapRegion* r) const;

public:
  G1CollectionSetCandidates();
  ~G1CollectionSetCandidates();

  G1CollectionCandidateList& marking_regions() { return _marking_regions; }
  G1CollectionCandidateList& retained_regions() { return _retained_regions; }

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
  // Remove the given regions from the candidates. All given regions must be part
  // of the candidates.
  void remove(G1CollectionCandidateRegionList* other);

  bool contains(const G1HeapRegion* r) const;

  const char* get_short_type_str(const G1HeapRegion* r) const;

  bool is_empty() const;

  bool has_more_marking_candidates() const;
  uint marking_regions_length() const;
  uint retained_regions_length() const;

private:
  void verify_helper(G1CollectionCandidateList* list, uint& from_marking, CandidateOrigin* verify_map) PRODUCT_RETURN;

public:
  void verify() PRODUCT_RETURN;

  uint length() const { return marking_regions_length() + retained_regions_length(); }

  // Iteration
  G1CollectionSetCandidatesIterator begin() {
    return G1CollectionSetCandidatesIterator(this, 0);
  }

  G1CollectionSetCandidatesIterator end() {
    return G1CollectionSetCandidatesIterator(this, length());
  }
};

#endif /* SHARE_GC_G1_G1COLLECTIONSETCANDIDATES_HPP */
