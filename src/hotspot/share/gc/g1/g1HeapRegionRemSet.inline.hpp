/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPREGIONREMSET_INLINE_HPP
#define SHARE_GC_G1_G1HEAPREGIONREMSET_INLINE_HPP

#include "gc/g1/g1HeapRegionRemSet.hpp"

#include "gc/g1/g1CardSet.inline.hpp"
#include "gc/g1/g1FromCardCache.inline.hpp"
#include "gc/shared/cardTable.hpp"

size_t G1HeapRegionRemSet::occupied() const {
  return has_card_set_group() ? card_set()->occupied() : 0;
}

uintptr_t G1HeapRegionRemSet::to_card(OopOrNarrowOopStar from) const {
  return pointer_delta(from, _heap_base_address, 1) >> CardTable::card_shift();
}

G1CardSet* G1HeapRegionRemSet::card_set() {
  assert(has_card_set_group(), "pre-condition");
  return card_set_group()->card_set();
}

const G1CardSet* G1HeapRegionRemSet::card_set() const {
  assert(has_card_set_group(), "pre-condition");
  return card_set_group()->card_set();
}

bool G1HeapRegionRemSet::card_set_is_empty() const {
  return !has_card_set_group() || card_set()->is_empty();
}

uint G1HeapRegionRemSet::card_set_group_id() const {
  assert(has_card_set_group(), "pre-condition");
  return card_set_group()->group_id();
}

bool G1HeapRegionRemSet::is_empty() const {
  return (code_roots_length() == 0) && card_set_is_empty();
}

bool G1HeapRegionRemSet::occupancy_less_or_equal_than(size_t occ) const {
  return (code_roots_length() == 0) && card_set()->occupancy_less_or_equal_to(occ);
}

bool G1HeapRegionRemSet::is_tracked() const {
  return has_card_set_group();
}

bool G1HeapRegionRemSet::is_updating() const {
  if (has_card_set_group()) {
    return card_set_group()->is_updating();
  } else {
    return false;
  }
}

bool G1HeapRegionRemSet::is_complete() const {
  if (has_card_set_group()) {
    return card_set_group()->is_complete();
  } else {
    return false;
  }
}

const char* G1HeapRegionRemSet::get_short_state_str() const {
  return has_card_set_group() ? card_set_group()->get_short_state_str() : "UNTRA";
}

const char* G1HeapRegionRemSet::get_state_str() const {
  return has_card_set_group() ? card_set_group()->get_state_str() : "Untracked";
}

void G1HeapRegionRemSet::add_reference(OopOrNarrowOopStar from, G1FromCardCache& from_card_cache) {
  precond(is_tracked());

  uintptr_t from_card = uintptr_t(from) >> CardTable::card_shift();

  if (from_card_cache.contains_or_add(from_card, card_set_group()->group_id())) {
    // We can't check whether the card is in the remembered set - the card container
    // may be coarsened just now.
    return;
  }

  card_set()->add_card(to_card(from));
}

bool G1HeapRegionRemSet::contains_reference(OopOrNarrowOopStar from) {
  return card_set()->contains_card(to_card(from));
}

void G1HeapRegionRemSet::print_info(outputStream* st, OopOrNarrowOopStar from) {
  card_set()->print_info(st, to_card(from));
}

#endif // SHARE_GC_G1_G1HEAPREGIONREMSET_INLINE_HPP
