/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CardSetGroup.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "logging/log.hpp"

G1CardSetGroup::G1CardSetGroup(G1CardSetConfiguration* config, G1MonotonicArenaFreePool* card_set_freelist_pool, uint group_id) :
  _items(4, mtGCCardSet),
  _card_set_mm(config, card_set_freelist_pool),
  _card_set(config, &_card_set_mm),
  _reclaimable_bytes(size_t(0)),
  _gc_efficiency(0.0),
  _group_id(group_id)
{ }

G1CardSetGroup::G1CardSetGroup() :
  G1CardSetGroup(G1CollectedHeap::heap()->card_set_config(), G1CollectedHeap::heap()->card_set_freelist_pool(), InvalidId)
{ }

void G1CardSetGroup::add(G1HeapRegion* hr) {
  precond(hr->is_young() == (_group_id == YoungId));

  if (_items.is_empty() && _group_id != YoungId) {
    precond(_group_id == InvalidId);
    _group_id = FirstNonYoungId + hr->hrm_index();
  }
  G1CardSetGroupItem c(hr);
  _items.append(c);
  hr->install_card_set_group(this);
}

void G1CardSetGroup::calculate_efficiency() {
  _reclaimable_bytes = 0;
  uint num_items = _items.length();
  for (uint i = 0; i < num_items; i++) {
    G1HeapRegion* hr = region_at(i);
    _reclaimable_bytes += hr->reclaimable_bytes();
  }
  _gc_efficiency = _reclaimable_bytes / predict_group_total_time_ms();
}

double G1CardSetGroup::liveness_percent() const {
  assert(num_regions() > 0, "must be");
  size_t capacity = num_regions() * G1HeapRegion::GrainBytes;
  return ((capacity - _reclaimable_bytes) * 100.0) / capacity;
}

void G1CardSetGroup::clear(bool uninstall_card_set_group) {
  clear_card_set();
  if (uninstall_card_set_group) {
    for (G1CardSetGroupItem ci : _items) {
      G1HeapRegion* r = ci._r;
      r->uninstall_card_set_group();
      r->rem_set()->set_state_untracked();
    }
  }
  _items.clear();
  if (_group_id != YoungId) {
    _group_id = InvalidId;
  }
}

void G1CardSetGroup::clear_card_set() {
  _card_set.clear();
}

double G1CardSetGroup::predict_group_total_time_ms() const {
  G1Policy* p = G1CollectedHeap::heap()->policy();

  double predicted_copy_time_ms = 0.0;
  double predict_code_root_scan_time_ms = 0.0;
  size_t predict_bytes_to_copy = 0.0;

  for (G1CardSetGroupItem ci : _items) {
    G1HeapRegion* r = ci._r;
    assert(r->rem_set()->card_set_group() == this, "Must be!");

    predict_bytes_to_copy += p->predict_bytes_to_copy(r);
    predicted_copy_time_ms += p->predict_region_copy_time_ms(r, false /* for_young_only_phase */);
    predict_code_root_scan_time_ms += p->predict_region_code_root_scan_time(r, false /* for_young_only_phase */);
  }

  size_t card_rs_length = _card_set.occupied();

  double merge_scan_time_ms = p->predict_merge_scan_time(card_rs_length);
  double non_young_other_time_ms = p->predict_non_young_other_time_ms(num_regions());

  double total_time_ms = merge_scan_time_ms +
                         predict_code_root_scan_time_ms +
                         predicted_copy_time_ms +
                         non_young_other_time_ms;

  log_trace(gc, ergo, cset) ("Prediction for card set group %u (%u regions): total_time %.2fms card_rs_length %zu merge_scan_time %.2fms code_root_scan_time_ms %.2fms evac_time_ms %.2fms other_time %.2fms bytes_to_copy %zu",
                             group_id(),
                             num_regions(),
                             total_time_ms,
                             card_rs_length,
                             merge_scan_time_ms,
                             predict_code_root_scan_time_ms,
                             predicted_copy_time_ms,
                             non_young_other_time_ms,
                             predict_bytes_to_copy);

  return total_time_ms;
}

int G1CardSetGroup::compare_gc_efficiency(G1CardSetGroup** gr1, G1CardSetGroup** gr2) {
  G1CardSetGroup* group_1 = *gr1;
  G1CardSetGroup* group_2 = *gr2;
  double gc_eff1 = group_1->gc_efficiency();
  double gc_eff2 = group_2->gc_efficiency();

  if (gc_eff1 > gc_eff2) {
    return -1;
  } else if (gc_eff1 < gc_eff2) {
    return 1;
  }

  // Make ordering deterministic by breaking ties with group ids.
  if (group_1->group_id() < group_2->group_id()) {
    return -1;
  } else if (group_1->group_id() > group_2->group_id()) {
    return 1;
  }
  return 0;
}

G1CardSetGroupList::G1CardSetGroupList() : _groups(8, mtGC), _num_regions(0) { }

void G1CardSetGroupList::append(G1CardSetGroup* group) {
  assert(group->num_regions() > 0, "Do not add empty groups");
  assert(!_groups.contains(group), "Already added to list");
  _groups.append(group);
  _num_regions.store_relaxed(num_regions() + group->num_regions());
}

G1CardSetGroup* G1CardSetGroupList::at(uint index) {
  return _groups.at(index);
}

void G1CardSetGroupList::clear(bool uninstall_card_set_group) {
  for (G1CardSetGroup* gr : _groups) {
    gr->clear(uninstall_card_set_group);
    delete gr;
  }
  _groups.clear();
  _num_regions.store_relaxed(0);
}

void G1CardSetGroupList::prepare_for_scan() {
  for (G1CardSetGroup* gr : _groups) {
    gr->card_set()->reset_table_scanner_for_groups();
  }
}

void G1CardSetGroupList::remove_selected(uint count, uint num_regions_to_remove) {
  _groups.remove_till(count);
  _num_regions.store_relaxed(num_regions() - num_regions_to_remove);
}

void G1CardSetGroupList::remove(G1CardSetGroupList* other) {
  guarantee((uint)_groups.length() >= other->length(), "Other should be a subset of this list");

  if (other->length() == 0) {
    // Nothing to remove or nothing in the original set.
    return;
  }

  // Create a list from scratch, copying over the elements from the original
  // list not in the other list. Finally deallocate and overwrite the old list.
  int new_length = _groups.length() - other->length();
  _num_regions.store_relaxed(num_regions() - other->num_regions());
  GrowableArray<G1CardSetGroup*> new_list(new_length, mtGC);

  uint other_idx = 0;
  for (G1CardSetGroup* gr : _groups) {
    if (other_idx == other->length() || gr != other->at(other_idx)) {
      new_list.append(gr);
    } else {
      other_idx++;
    }
  }
  _groups.swap(&new_list);

  verify();
  assert(_groups.length() == new_length, "Must be");
}

void G1CardSetGroupList::sort_by_efficiency() {
  _groups.sort(G1CardSetGroup::compare_gc_efficiency);
}

#ifndef PRODUCT
void G1CardSetGroupList::verify() const {
  G1CardSetGroup* prev = nullptr;

  for (G1CardSetGroup* gr : _groups) {
    assert(prev == nullptr || prev->gc_efficiency() >= gr->gc_efficiency(),
           "Stored gc efficiency must be descending");
    prev = gr;
  }
}
#endif
