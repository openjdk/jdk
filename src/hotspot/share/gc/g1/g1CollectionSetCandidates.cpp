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

#include "gc/g1/g1CardSetGroup.inline.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "utilities/growableArray.hpp"

G1CollectionSetCandidates::G1CollectionSetCandidates() :
  _contains_map(nullptr),
  _from_marking_groups(),
  _retained_groups(),
  _max_num_regions(0),
  _num_last_marking_candidate_regions(0)
{ }

G1CollectionSetCandidates::~G1CollectionSetCandidates() {
  FREE_C_HEAP_ARRAY(_contains_map);
  _from_marking_groups.clear();
  _retained_groups.clear();
}

bool G1CollectionSetCandidates::is_from_marking(G1HeapRegion* r) const {
  assert(contains(r), "must be");
  return _contains_map[r->hrm_index()] == CandidateOrigin::Marking;
}

void G1CollectionSetCandidates::initialize(uint max_num_regions) {
  assert(_contains_map == nullptr, "already initialized");
  _max_num_regions = max_num_regions;
  _contains_map = NEW_C_HEAP_ARRAY(CandidateOrigin, max_num_regions, mtGC);
  clear();
}

void G1CollectionSetCandidates::clear() {
  _retained_groups.clear(true /* uninstall_card_set_group */);
  _from_marking_groups.clear(true /* uninstall_card_set_group */);
  for (uint i = 0; i < _max_num_regions; i++) {
    _contains_map[i] = CandidateOrigin::Invalid;
  }
  _num_last_marking_candidate_regions = 0;
}

void G1CollectionSetCandidates::sort_marking_by_efficiency() {
  for (G1CardSetGroup* gr : _from_marking_groups) {
    gr->calculate_efficiency();
  }
  _from_marking_groups.sort_by_efficiency();

  _from_marking_groups.verify();
}

void G1CollectionSetCandidates::set_candidates_from_marking(GrowableArrayCHeap<G1HeapRegion*, mtGC>* candidates) {
  uint num_candidates = candidates->length();

  if (num_candidates == 0) {
    log_debug(gc, ergo, cset) ("No regions selected from marking.");
    return;
  }

  assert(_from_marking_groups.length() == 0, "must be empty at the start of a cycle");
  verify();

  G1Policy* p = G1CollectedHeap::heap()->policy();
  // During each Mixed GC, we must collect at least G1Policy::calc_min_num_old_cset_regions regions to meet
  // the G1MixedGCCountTarget. For the first collection in a Mixed GC cycle, we can add all regions
  // required to meet this threshold to the same card set group. We are certain these will be collected in
  // the same Mixed GC.
  uint group_limit = p->calc_min_num_old_cset_regions(num_candidates);

  G1CardSetGroup* current = nullptr;

  current = new G1CardSetGroup();

  for (uint i = 0; i < num_candidates; i++) {
    G1HeapRegion* r = candidates->at(i);
    assert(!contains(r), "must not contain region %u", r->hrm_index());
    _contains_map[r->hrm_index()] = CandidateOrigin::Marking;

    if (current->num_regions() == group_limit) {
      if (group_limit != G1OldCardSetGroupSize) {
        group_limit = G1OldCardSetGroupSize;
      }

      _from_marking_groups.append(current);

      current = new G1CardSetGroup();
    }
    current->add(r);
  }

  _from_marking_groups.append(current);

  assert(_from_marking_groups.num_regions() == num_candidates, "Must be!");

  log_debug(gc, ergo, cset) ("Finished creating %u card set groups from %u regions", _from_marking_groups.length(), num_candidates);
  _num_last_marking_candidate_regions = num_candidates;

  verify();
}

void G1CollectionSetCandidates::sort_by_efficiency() {
  // From marking card set groups must always be sorted so no reason to actually sort
  // them.
  _from_marking_groups.verify();
  _retained_groups.sort_by_efficiency();
  _retained_groups.verify();
}

void G1CollectionSetCandidates::remove(G1CardSetGroupList* other) {
  // During removal, we exploit the fact that elements in the _from_marking_groups,
  // _retained_groups and other list are sorted by gc_efficiency. Furthermore,
  // all card set groups in the passed other list are in one of the two other lists.
  //
  // Split original list into elements for the marking list and elements from the
  // retained list.
  G1CardSetGroupList other_marking_groups;
  G1CardSetGroupList other_retained_groups;

  for (G1CardSetGroup* group : *other) {
    assert(group->num_regions() > 0, "Should not have empty groups");
    // Regions in the same group have the same source (i.e from_marking or retained).
    G1HeapRegion* r = group->region_at(0);
    if (is_from_marking(r)) {
      other_marking_groups.append(group);
    } else {
      other_retained_groups.append(group);
    }
  }

  _from_marking_groups.remove(&other_marking_groups);
  _retained_groups.remove(&other_retained_groups);

  other->iterate([&] (G1HeapRegion* r) {
    assert(contains(r), "Must contain region %u", r->hrm_index());
    _contains_map[r->hrm_index()] = CandidateOrigin::Invalid;
  });

  verify();
}

void G1CollectionSetCandidates::add_retained_region_unsorted(G1HeapRegion* r) {
  assert(!contains(r), "Must not already contain region %u", r->hrm_index());
  _contains_map[r->hrm_index()] = CandidateOrigin::Retained;

  G1CardSetGroup* gr = new G1CardSetGroup();
  gr->add(r);
  gr->calculate_efficiency();

  _retained_groups.append(gr);
}

bool G1CollectionSetCandidates::is_empty() const {
  return num_regions() == 0;
}

bool G1CollectionSetCandidates::has_more_marking_candidates() const {
  return num_marking_regions() != 0;
}

uint G1CollectionSetCandidates::num_marking_regions() const {
  return _from_marking_groups.num_regions();
}

uint G1CollectionSetCandidates::num_retained_regions() const {
  return _retained_groups.num_regions();
}

#ifndef PRODUCT
void G1CollectionSetCandidates::verify_helper(G1CardSetGroupList* list, uint& from_marking, CandidateOrigin* verify_map) {
  list->verify();

  for (G1CardSetGroup* gr : *list) {
    for (G1CardSetGroupItem ci : *gr) {
      G1HeapRegion* r = ci._r;

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
}

void G1CollectionSetCandidates::verify() {
  uint from_marking = 0;

  CandidateOrigin* verify_map = NEW_C_HEAP_ARRAY(CandidateOrigin, _max_num_regions, mtGC);
  for (uint i = 0; i < _max_num_regions; i++) {
    verify_map[i] = CandidateOrigin::Invalid;
  }

  verify_helper(&_from_marking_groups, from_marking, verify_map);
  assert(from_marking == num_marking_regions(), "must be");

  uint from_marking_retained = 0;
  verify_helper(&_retained_groups, from_marking_retained, verify_map);
  assert(from_marking_retained == 0, "must be");

  assert(num_regions() >= num_marking_regions(), "must be");

  // Check whether the _contains_map is consistent with the list.
  for (uint i = 0; i < _max_num_regions; i++) {
    assert(_contains_map[i] == verify_map[i] ||
           (_contains_map[i] != CandidateOrigin::Invalid && verify_map[i] == CandidateOrigin::Verify),
           "Candidate origin does not match for region %u, is %u but should be %u",
           i,
           static_cast<std::underlying_type<CandidateOrigin>::type>(_contains_map[i]),
           static_cast<std::underlying_type<CandidateOrigin>::type>(verify_map[i]));
  }

  FREE_C_HEAP_ARRAY(verify_map);
}
#endif

bool G1CollectionSetCandidates::contains(const G1HeapRegion* r) const {
  const uint index = r->hrm_index();
  assert(index < _max_num_regions, "must be");
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
