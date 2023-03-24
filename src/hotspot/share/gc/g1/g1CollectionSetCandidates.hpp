/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/workerThread.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/growableArray.hpp"

class G1CollectionCandidateList;
class G1CollectionSetCandidates;
class HeapRegion;
class HeapRegionClosure;

using G1CollectionSetRegionListIterator = GrowableArrayIterator<HeapRegion*>;

// A set of HeapRegion*, elements typically sorted by decreasing gc efficiency
// (without their gc efficiencies).
class G1CollectionSetRegionList {
  GrowableArray<HeapRegion*> _regions;
  size_t _reclaimable_bytes;

public:
  G1CollectionSetRegionList();

  // Append a HeapRegion at the end of this list.
  void append(HeapRegion* r);
  // Remove the list of HeapRegion*.
  void remove(G1CollectionSetRegionList* list);

  HeapRegion* at(uint index);

  void clear();

  uint length() const { return (uint)_regions.length(); }

  size_t reclaimable_bytes() const { return _reclaimable_bytes; }

  G1CollectionSetRegionListIterator begin() const { return _regions.begin(); }
  G1CollectionSetRegionListIterator end() const { return _regions.end(); }

  void verify() PRODUCT_RETURN;

  void print(const char* prefix);
};

class G1CollectionCandidateListIterator : public StackObj {
  G1CollectionCandidateList* _which;
  uint _position;

public:
  G1CollectionCandidateListIterator(G1CollectionCandidateList* which, uint position);

  G1CollectionCandidateListIterator& operator++();
  HeapRegion* operator*();

  bool operator==(const G1CollectionCandidateListIterator& rhs);
  bool operator!=(const G1CollectionCandidateListIterator& rhs);
};

// List of collection set candidates (regions with their efficiency) ordered by
// decreasing gc efficiency.
class G1CollectionCandidateList : public CHeapObj<mtGC> {
  friend class G1CollectionCandidateListIterator;

public:
  struct CandidateInfo {
    HeapRegion* _r;
    double _gc_efficiency;

    CandidateInfo() : CandidateInfo(nullptr, 0.0) { }
    CandidateInfo(HeapRegion* r, double gc_efficiency) : _r(r), _gc_efficiency(gc_efficiency) { }
  };

private:
  GrowableArray<CandidateInfo> _candidates;

public:
  G1CollectionCandidateList();

  // Merge the given set of candidates into this list, preserving the efficiency condition.
  void merge(CandidateInfo* candidate_infos, uint num_infos);
  // Add the given element to this list at the end, making the list unsorted.
  void append_unsorted(HeapRegion* r);
  // Restore sorting order by decreasing gc efficiency.
  void sort_by_efficiency();
  // Removes any HeapRegions stored in this list also in the other list. The other
  // list may only contain regions in this list, sorted by gc efficiency. Returns
  // the number of regions removed.
  size_t remove(G1CollectionSetRegionList* other);

  void clear();

  CandidateInfo& at(uint position) { return _candidates.at(position); }

  uint length() const { return (uint)_candidates.length(); }

  void verify() PRODUCT_RETURN;

  // Comparison function to order regions in decreasing GC efficiency order. This
  // will cause regions with a lot of live objects and large remembered sets to end
  // up at the end of the list.
  static int order_regions(CandidateInfo* ci1, CandidateInfo* ci2);

  void print(const char* prefix);

  G1CollectionCandidateListIterator begin() {
    return G1CollectionCandidateListIterator(this, 0);
  }

  G1CollectionCandidateListIterator end() {
    return G1CollectionCandidateListIterator(this, length());
  }
};

// Iterator for G1CollectionSetCandidates.
class G1CollectionSetCandidatesIterator : public StackObj {
  G1CollectionSetCandidates* _which;
  uint _marking_position;

public:
  G1CollectionSetCandidatesIterator(G1CollectionSetCandidates* which, uint marking_position);

  G1CollectionSetCandidatesIterator& operator++();
  HeapRegion* operator*();

  bool operator==(const G1CollectionSetCandidatesIterator& rhs);
  bool operator!=(const G1CollectionSetCandidatesIterator& rhs);
};

// Tracks collection set candidates, i.e. regions that should be evacuated soon.
//
// The regions are tracked in two lists of regions, each sorted by decreasing
// "gc efficiency".
//
// * marking_regions: the set of regions selected by concurrent marking to be
//                    evacuated to keep overall heap occupancy stable.
//                    They are guaranteed to be handled out during the mixed phase.
//
// * retained regions: set of regions selected for evacuation during evacuation
//                     failure.
//                     Any young collection will try to evacuate them.
//
class G1CollectionSetCandidates : public CHeapObj<mtGC> {
  friend class G1CollectionSetCandidatesIterator;

  enum class CandidateOrigin : uint8_t {
    Invalid,
    Marking,                   // This region has been determined as candidate by concurrent marking.
    Verify                     // Special value for verification.
  };

  G1CollectionCandidateList _marking_regions;

  CandidateOrigin* _contains_map;
  uint _max_regions;

  // The number of regions from the last merge of candidates from the marking.
  uint _last_merge_length;

  size_t _reclaimable_bytes;

  bool is_from_marking(HeapRegion* r) const;
  
public:
  G1CollectionSetCandidates();
  ~G1CollectionSetCandidates();

  G1CollectionCandidateList& marking_regions() { return _marking_regions; }

  void initialize(uint max_regions);

  void clear();

  // Merge collection set candidates from marking into the current marking list
  // (which needs to be empty).
  void merge_candidates_from_marking(G1CollectionCandidateList::CandidateInfo* candidate_infos,
                                     uint num_infos,
                                     size_t reclaimable_bytes);
  // The most recent length of the list that had been merged last via
  // merge_candidates_from_marking(). Used for calculating minimum collection set
  // regions.
  uint last_merge_length() const { return _last_merge_length; }

  void sort_by_efficiency();

  // Remove the given regions from the candidates. All of the given regions must
  // be part of the candidates.
  void remove(G1CollectionSetRegionList* other);

  bool contains(const HeapRegion* r) const;

  const char* get_short_type_str(const HeapRegion* r) const;

  bool is_empty() const;
  bool has_no_more_marking_candidates() const;

  uint marking_regions_length() const { return _marking_regions.length(); }

private:
  void verify_helper(G1CollectionCandidateList* list, uint& from_marking, size_t& reclaimable_bytes, CandidateOrigin* verify_map) PRODUCT_RETURN;

public:
  void verify() PRODUCT_RETURN;

  uint length() const { return marking_regions_length(); }

  size_t total_reclaimable_bytes() const { return _reclaimable_bytes; }

  // Iteration
  G1CollectionSetCandidatesIterator begin() {
    return G1CollectionSetCandidatesIterator(this, 0);
  }

  G1CollectionSetCandidatesIterator end() {
    return G1CollectionSetCandidatesIterator(this, marking_regions_length());
  }

  void print(); // FIXME: debug, remove
};

#endif /* SHARE_GC_G1_G1COLLECTIONSETCANDIDATES_HPP */

