/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/g1/youngList.hpp"
#include "logging/log.hpp"
#include "utilities/ostream.hpp"

YoungList::YoungList(G1CollectedHeap* g1h) :
    _g1h(g1h), _head(NULL), _length(0),
    _survivor_head(NULL), _survivor_tail(NULL), _survivor_length(0) {
  guarantee(check_list_empty(), "just making sure...");
}

void YoungList::push_region(HeapRegion *hr) {
  assert(!hr->is_young(), "should not already be young");
  assert(hr->get_next_young_region() == NULL, "cause it should!");

  hr->set_next_young_region(_head);
  _head = hr;

  _g1h->g1_policy()->set_region_eden(hr, (int) _length);
  ++_length;
}

void YoungList::add_survivor_region(HeapRegion* hr) {
  assert(hr->is_survivor(), "should be flagged as survivor region");
  assert(hr->get_next_young_region() == NULL, "cause it should!");

  hr->set_next_young_region(_survivor_head);
  if (_survivor_head == NULL) {
    _survivor_tail = hr;
  }
  _survivor_head = hr;
  ++_survivor_length;
}

void YoungList::empty_list(HeapRegion* list) {
  while (list != NULL) {
    HeapRegion* next = list->get_next_young_region();
    list->set_next_young_region(NULL);
    list->uninstall_surv_rate_group();
    // This is called before a Full GC and all the non-empty /
    // non-humongous regions at the end of the Full GC will end up as
    // old anyway.
    list->set_old();
    list = next;
  }
}

void YoungList::empty_list() {
  assert(check_list_well_formed(), "young list should be well formed");

  empty_list(_head);
  _head = NULL;
  _length = 0;

  empty_list(_survivor_head);
  _survivor_head = NULL;
  _survivor_tail = NULL;
  _survivor_length = 0;

  assert(check_list_empty(), "just making sure...");
}

bool YoungList::check_list_well_formed() {
  bool ret = true;

  uint length = 0;
  HeapRegion* curr = _head;
  HeapRegion* last = NULL;
  while (curr != NULL) {
    if (!curr->is_young()) {
      log_error(gc, verify)("### YOUNG REGION " PTR_FORMAT "-" PTR_FORMAT " "
                            "incorrectly tagged (y: %d, surv: %d)",
                            p2i(curr->bottom()), p2i(curr->end()),
                            curr->is_young(), curr->is_survivor());
      ret = false;
    }
    ++length;
    last = curr;
    curr = curr->get_next_young_region();
  }
  ret = ret && (length == _length);

  if (!ret) {
    log_error(gc, verify)("### YOUNG LIST seems not well formed!");
    log_error(gc, verify)("###   list has %u entries, _length is %u", length, _length);
  }

  return ret;
}

bool YoungList::check_list_empty() {
  bool ret = true;

  if (_length != 0) {
    log_error(gc, verify)("### YOUNG LIST should have 0 length, not %u", _length);
    ret = false;
  }
  if (_head != NULL) {
    log_error(gc, verify)("### YOUNG LIST does not have a NULL head");
    ret = false;
  }
  if (!ret) {
    log_error(gc, verify)("### YOUNG LIST does not seem empty");
  }

  return ret;
}

void
YoungList::reset_auxilary_lists() {
  guarantee( is_empty(), "young list should be empty" );
  assert(check_list_well_formed(), "young list should be well formed");

  // Add survivor regions to SurvRateGroup.
  _g1h->g1_policy()->note_start_adding_survivor_regions();
  _g1h->g1_policy()->finished_recalculating_age_indexes(true /* is_survivors */);

  int young_index_in_cset = 0;
  for (HeapRegion* curr = _survivor_head;
       curr != NULL;
       curr = curr->get_next_young_region()) {
    _g1h->g1_policy()->set_region_survivor(curr, young_index_in_cset);

    // The region is a non-empty survivor so let's add it to
    // the incremental collection set for the next evacuation
    // pause.
    _g1h->collection_set()->add_survivor_regions(curr);
    young_index_in_cset += 1;
  }
  assert((uint) young_index_in_cset == _survivor_length, "post-condition");
  _g1h->g1_policy()->note_stop_adding_survivor_regions();

  _head   = _survivor_head;
  _length = _survivor_length;
  if (_survivor_head != NULL) {
    assert(_survivor_tail != NULL, "cause it shouldn't be");
    assert(_survivor_length > 0, "invariant");
    _survivor_tail->set_next_young_region(NULL);
  }

  // Don't clear the survivor list handles until the start of
  // the next evacuation pause - we need it in order to re-tag
  // the survivor regions from this evacuation pause as 'young'
  // at the start of the next.

  _g1h->g1_policy()->finished_recalculating_age_indexes(false /* is_survivors */);

  assert(check_list_well_formed(), "young list should be well formed");
}

void YoungList::print() {
  HeapRegion* lists[] = {_head,   _survivor_head};
  const char* names[] = {"YOUNG", "SURVIVOR"};

  for (uint list = 0; list < ARRAY_SIZE(lists); ++list) {
    tty->print_cr("%s LIST CONTENTS", names[list]);
    HeapRegion *curr = lists[list];
    if (curr == NULL) {
      tty->print_cr("  empty");
    }
    while (curr != NULL) {
      tty->print_cr("  " HR_FORMAT ", P: " PTR_FORMAT ", N: " PTR_FORMAT ", age: %4d",
                             HR_FORMAT_PARAMS(curr),
                             p2i(curr->prev_top_at_mark_start()),
                             p2i(curr->next_top_at_mark_start()),
                             curr->age_in_surv_rate_group_cond());
      curr = curr->get_next_young_region();
    }
  }

  tty->cr();
}
