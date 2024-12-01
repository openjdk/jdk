/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/shared/memset_with_concurrent_readers.hpp"
#include "logging/log.hpp"

void G1CardTable::g1_mark_as_young(const MemRegion& mr) {
  CardValue *const first = byte_for(mr.start());
  CardValue *const last = byte_after(mr.last());

  memset_with_concurrent_readers(first, g1_young_gen, pointer_delta(last, first, sizeof(CardValue)));
}

#ifndef PRODUCT
void G1CardTable::verify_g1_young_region(MemRegion mr) {
  verify_region(mr, g1_young_gen,  true);
}
#endif

void G1CardTableChangedListener::on_commit(uint start_idx, size_t num_regions, bool zero_filled) {
  // Default value for a clean card on the card table is -1. So we cannot take advantage of the zero_filled parameter.
  MemRegion mr(G1CollectedHeap::heap()->bottom_addr_for_region(start_idx), num_regions * G1HeapRegion::GrainWords);
  _card_table->clear_MemRegion(mr);
}

void G1CardTable::initialize(G1RegionToSpaceMapper* mapper) {
  mapper->set_mapping_changed_listener(&_listener);

  _byte_map_size = mapper->reserved().byte_size();

  HeapWord* low_bound  = _whole_heap.start();
  HeapWord* high_bound = _whole_heap.end();

  _covered[0] = _whole_heap;

  _byte_map = (CardValue*) mapper->reserved().start();
  _byte_map_base = _byte_map - (uintptr_t(low_bound) >> _card_shift);
  assert(byte_for(low_bound) == &_byte_map[0], "Checking start of map");
  assert(byte_for(high_bound-1) <= &_byte_map[last_valid_index()], "Checking end of map");

  log_trace(gc, barrier)("G1CardTable::G1CardTable: ");
  log_trace(gc, barrier)("    &_byte_map[0]: " PTR_FORMAT "  &_byte_map[last_valid_index()]: " PTR_FORMAT,
                         p2i(&_byte_map[0]), p2i(&_byte_map[last_valid_index()]));
  log_trace(gc, barrier)("    _byte_map_base: " PTR_FORMAT,  p2i(_byte_map_base));
}

bool G1CardTable::is_in_young(const void* p) const {
  volatile CardValue* card = byte_for(p);
  return *card == G1CardTable::g1_young_card_val();
}
