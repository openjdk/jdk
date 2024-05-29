/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1CollectionSetCandidates.inline.hpp"
#include "gc/g1/g1CollectionSetChooser.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/growableArray.hpp"

G1CollectionCandidateList::G1CollectionCandidateList() : _candidates(2, mtGC) { }

void G1CollectionCandidateList::set(G1CollectionSetCandidateInfo* candidate_infos, uint num_infos) {
  assert(_candidates.is_empty(), "must be");

  GrowableArrayFromArray<G1CollectionSetCandidateInfo> a(candidate_infos, (int)num_infos);
  _candidates.appendAll(&a);
}

void G1CollectionCandidateList::append_unsorted(G1HeapRegion* r) {
  G1CollectionSetCandidateInfo c(r, r->calc_gc_efficiency());
  _candidates.append(c);
}

void G1CollectionCandidateList::sort_by_efficiency() {
  _candidates.sort(compare_gc_efficiency);
}

void G1CollectionCandidateList::remove(G1CollectionCandidateRegionList* other) {
  guarantee((uint)_candidates.length() >= other->length(), "must be");

  if (other->length() == 0) {
    // Nothing to remove or nothing in the original set.
    return;
  }

  // Create a list from scratch, copying over the elements from the candidate
  // list not in the other list. Finally deallocate and overwrite the old list.
  int new_length = _candidates.length() - other->length();
  GrowableArray<G1CollectionSetCandidateInfo> new_list(new_length, mtGC);

  uint other_idx = 0;

  for (uint candidate_idx = 0; candidate_idx < (uint)_candidates.length(); candidate_idx++) {
    if ((other_idx == other->length()) || _candidates.at(candidate_idx)._r != other->at(other_idx)) {
      new_list.append(_candidates.at(candidate_idx));
    } else {
      other_idx++;
    }
  }
  _candidates.swap(&new_list);

  verify();
  assert(_candidates.length() == new_length, "must be %u %u", _candidates.length(), new_length);
}

void G1CollectionCandidateList::clear() {
  _candidates.clear();
}

#ifndef PRODUCT
void G1CollectionCandidateList::verify() {
  G1CollectionSetCandidateInfo* prev = nullptr;

  for (uint i = 0; i < (uint)_candidates.length(); i++) {
    G1CollectionSetCandidateInfo& ci = _candidates.at(i);
    assert(prev == nullptr || prev->_gc_efficiency >= ci._gc_efficiency,
           "Stored gc efficiency must be descending from region %u to %u",
           prev->_r->hrm_index(), ci._r->hrm_index());
    prev = &ci;
    assert(ci._r->rem_set()->is_tracked(), "remset for region %u must be tracked", ci._r->hrm_index());
  }
}
#endif

int G1CollectionCandidateList::compare_gc_efficiency(G1CollectionSetCandidateInfo* ci1, G1CollectionSetCandidateInfo* ci2) {
  assert(ci1->_r != nullptr && ci2->_r != nullptr, "Should not be!");

  double gc_eff1 = ci1->_gc_efficiency;
  double gc_eff2 = ci2->_gc_efficiency;

  if (gc_eff1 > gc_eff2) {
    return -1;
  } else if (gc_eff1 < gc_eff2) {
    return 1;
  } else {
    return 0;
  }
}

int G1CollectionCandidateList::compare_reclaimble_bytes(G1CollectionSetCandidateInfo* ci1, G1CollectionSetCandidateInfo* ci2) {
  // Make sure that null entries are moved to the end.
  if (ci1->_r == nullptr) {
    if (ci2->_r == nullptr) {
      return 0;
    } else {
      return 1;
    }
  } else if (ci2->_r == nullptr) {
    return -1;
  }

  size_t reclaimable1 = ci1->_r->reclaimable_bytes();
  size_t reclaimable2 = ci2->_r->reclaimable_bytes();

  if (reclaimable1 > reclaimable2) {
    return -1;
  } else if (reclaimable1 < reclaimable2) {
    return 1;
  } else {
    return 0;
  }
}

G1CollectionCandidateRegionList::G1CollectionCandidateRegionList() : _regions(2, mtGC) { }

void G1CollectionCandidateRegionList::append(G1HeapRegion* r) {
  assert(!_regions.contains(r), "must be");
  _regions.append(r);
}

void G1CollectionCandidateRegionList::remove_prefix(G1CollectionCandidateRegionList* other) {
#ifdef ASSERT
  // Check that the given list is a prefix of this list.
  int i = 0;
  for (G1HeapRegion* r : *other) {
    assert(_regions.at(i) == r, "must be in order, but element %d is not", i);
    i++;
  }
#endif

  if (other->length() == 0) {
    return;
  }
  _regions.remove_till(other->length());
}

G1HeapRegion* G1CollectionCandidateRegionList::at(uint index) {
  return _regions.at(index);
}

void G1CollectionCandidateRegionList::clear() {
  _regions.clear();
}

G1CollectionSetCandidates::G1CollectionSetCandidates() :
  _marking_regions(),
  _retained_regions(),
  _contains_map(nullptr),
  _max_regions(0),
  _last_marking_candidates_length(0)
{ }

G1CollectionSetCandidates::~G1CollectionSetCandidates() {
  FREE_C_HEAP_ARRAY(CandidateOrigin, _contains_map);
}

bool G1CollectionSetCandidates::is_from_marking(G1HeapRegion* r) const {
  assert(contains(r), "must be");
  return _contains_map[r->hrm_index()] == CandidateOrigin::Marking;
}

void G1CollectionSetCandidates::initialize(uint max_regions) {
  assert(_contains_map == nullptr, "already initialized");
  _max_regions = max_regions;
  _contains_map = NEW_C_HEAP_ARRAY(CandidateOrigin, max_regions, mtGC);
  clear();
}

void G1CollectionSetCandidates::clear() {
  _marking_regions.clear();
  _retained_regions.clear();
  for (uint i = 0; i < _max_regions; i++) {
    _contains_map[i] = CandidateOrigin::Invalid;
  }
  _last_marking_candidates_length = 0;
}

void G1CollectionSetCandidates::sort_marking_by_efficiency() {
  G1CollectionCandidateListIterator iter = _marking_regions.begin();
  for (; iter != _marking_regions.end(); ++iter) {
    G1HeapRegion* hr = (*iter)->_r;
    (*iter)->_gc_efficiency = hr->calc_gc_efficiency();
  }
  _marking_regions.sort_by_efficiency();

  _marking_regions.verify();
}

void G1CollectionSetCandidates::set_candidates_from_marking(G1CollectionSetCandidateInfo* candidate_infos,
                                                            uint num_infos) {
  assert(_marking_regions.length() == 0, "must be empty before adding new ones");

  verify();

  _marking_regions.set(candidate_infos, num_infos);
  for (uint i = 0; i < num_infos; i++) {
    G1HeapRegion* r = candidate_infos[i]._r;
    assert(!contains(r), "must not contain region %u", r->hrm_index());
    _contains_map[r->hrm_index()] = CandidateOrigin::Marking;
  }
  _last_marking_candidates_length = num_infos;

  verify();
}

void G1CollectionSetCandidates::sort_by_efficiency() {
  // From marking regions must always be sorted so no reason to actually sort
  // them.
  _marking_regions.verify();
  _retained_regions.sort_by_efficiency();
  _retained_regions.verify();
}

void G1CollectionSetCandidates::add_retained_region_unsorted(G1HeapRegion* r) {
  assert(!contains(r), "must not contain region %u", r->hrm_index());
  _contains_map[r->hrm_index()] = CandidateOrigin::Retained;
  _retained_regions.append_unsorted(r);
}

void G1CollectionSetCandidates::remove(G1CollectionCandidateRegionList* other) {
  // During removal, we exploit the fact that elements in the marking_regions,
  // retained_regions and other list are sorted by gc_efficiency. Furthermore,
  // all regions in the passed other list are in one of the two other lists.
  //
  // Split original list into elements for the marking list and elements from the
  // retained list.
  G1CollectionCandidateRegionList other_marking_regions;
  G1CollectionCandidateRegionList other_retained_regions;

  for (G1HeapRegion* r : *other) {
    if (is_from_marking(r)) {
      other_marking_regions.append(r);
    } else {
      other_retained_regions.append(r);
    }
  }

  _marking_regions.remove(&other_marking_regions);
  _retained_regions.remove(&other_retained_regions);

  for (G1HeapRegion* r : *other) {
    assert(contains(r), "must contain region %u", r->hrm_index());
    _contains_map[r->hrm_index()] = CandidateOrigin::Invalid;
  }

  verify();
}

bool G1CollectionSetCandidates::is_empty() const {
  return length() == 0;
}

bool G1CollectionSetCandidates::has_more_marking_candidates() const {
  return marking_regions_length() != 0;
}

uint G1CollectionSetCandidates::marking_regions_length() const {
  return _marking_regions.length();
}

uint G1CollectionSetCandidates::retained_regions_length() const {
  return _retained_regions.length();
}

#ifndef PRODUCT
void G1CollectionSetCandidates::verify_helper(G1CollectionCandidateList* list, uint& from_marking, CandidateOrigin* verify_map) {
  list->verify();

  for (uint i = 0; i < (uint)list->length(); i++) {
    G1HeapRegion* r = list->at(i)._r;

    if (is_from_marking(r)) {
      from_marking++;
    }
    const uint hrm_index = r->hrm_index();
    assert(_contains_map[hrm_index] == CandidateOrigin::Marking || _contains_map[hrm_index] == CandidateOrigin::Retained,
           "must be %u is %u", hrm_index, (uint)_contains_map[hrm_index]);
    assert(verify_map[hrm_index] == CandidateOrigin::Invalid, "already added");

    verify_map[hrm_index] = CandidateOrigin::Verify;
  }
}

void G1CollectionSetCandidates::verify() {
  uint from_marking = 0;

  CandidateOrigin* verify_map = NEW_C_HEAP_ARRAY(CandidateOrigin, _max_regions, mtGC);
  for (uint i = 0; i < _max_regions; i++) {
    verify_map[i] = CandidateOrigin::Invalid;
  }

  verify_helper(&_marking_regions, from_marking, verify_map);
  assert(from_marking == marking_regions_length(), "must be");

  uint from_marking_retained = 0;
  verify_helper(&_retained_regions, from_marking_retained, verify_map);
  assert(from_marking_retained == 0, "must be");

  assert(length() >= marking_regions_length(), "must be");

  // Check whether the _contains_map is consistent with the list.
  for (uint i = 0; i < _max_regions; i++) {
    assert(_contains_map[i] == verify_map[i] ||
           (_contains_map[i] != CandidateOrigin::Invalid && verify_map[i] == CandidateOrigin::Verify),
           "Candidate origin does not match for region %u, is %u but should be %u",
           i,
           static_cast<std::underlying_type<CandidateOrigin>::type>(_contains_map[i]),
           static_cast<std::underlying_type<CandidateOrigin>::type>(verify_map[i]));
  }

  FREE_C_HEAP_ARRAY(CandidateOrigin, verify_map);
}
#endif

bool G1CollectionSetCandidates::contains(const G1HeapRegion* r) const {
  const uint index = r->hrm_index();
  assert(index < _max_regions, "must be");
  return _contains_map[index] != CandidateOrigin::Invalid;
}

const char* G1CollectionSetCandidates::get_short_type_str(const G1HeapRegion* r) const {
  static const char* type_strings[] = {
    "Ci",  // Invalid
    "Cm",  // Marking
    "Cr",  // Retained
    "Cv"   // Verification
  };

  uint8_t kind = static_cast<std::underlying_type<CandidateOrigin>::type>(_contains_map[r->hrm_index()]);
  return type_strings[kind];
}
