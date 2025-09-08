/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "memory/memoryReserver.hpp"
#include "memory/reservedSpace.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/init.hpp"

void ShenandoahCardTable::initialize() {
  size_t num_cards = cards_required(_whole_heap.word_size());

  // each card takes 1 byte; + 1 for the guard card
  size_t num_bytes = num_cards + 1;
  const size_t granularity = os::vm_allocation_granularity();
  _byte_map_size = align_up(num_bytes, MAX2(_page_size, granularity));

  HeapWord* low_bound  = _whole_heap.start();
  HeapWord* high_bound = _whole_heap.end();

  // ReservedSpace constructor would assert rs_align >= os::vm_page_size().
  const size_t rs_align = MAX2(_page_size, granularity);

  ReservedSpace write_space = MemoryReserver::reserve(_byte_map_size, rs_align, _page_size, mtGC);
  initialize(write_space);

  // The assembler store_check code will do an unsigned shift of the oop,
  // then add it to _byte_map_base, i.e.
  //
  //   _byte_map = _byte_map_base + (uintptr_t(low_bound) >> card_shift)
  _byte_map = (CardValue*) write_space.base();
  _byte_map_base = _byte_map - (uintptr_t(low_bound) >> _card_shift);
  assert(byte_for(low_bound) == &_byte_map[0], "Checking start of map");
  assert(byte_for(high_bound-1) <= &_byte_map[last_valid_index()], "Checking end of map");

  _write_byte_map = _byte_map;
  _write_byte_map_base = _byte_map_base;

  ReservedSpace read_space = MemoryReserver::reserve(_byte_map_size, rs_align, _page_size, mtGC);
  initialize(read_space);

  _read_byte_map = (CardValue*) read_space.base();
  _read_byte_map_base = _read_byte_map - (uintptr_t(low_bound) >> card_shift());
  assert(read_byte_for(low_bound) == &_read_byte_map[0], "Checking start of map");
  assert(read_byte_for(high_bound-1) <= &_read_byte_map[last_valid_index()], "Checking end of map");

  _covered[0] = _whole_heap;

  log_trace(gc, barrier)("ShenandoahCardTable::ShenandoahCardTable:");
  log_trace(gc, barrier)("    &_write_byte_map[0]: " INTPTR_FORMAT "  &_write_byte_map[_last_valid_index]: " INTPTR_FORMAT,
                         p2i(&_write_byte_map[0]), p2i(&_write_byte_map[last_valid_index()]));
  log_trace(gc, barrier)("    _write_byte_map_base: " INTPTR_FORMAT, p2i(_write_byte_map_base));
  log_trace(gc, barrier)("    &_read_byte_map[0]: " INTPTR_FORMAT "  &_read_byte_map[_last_valid_index]: " INTPTR_FORMAT,
                  p2i(&_read_byte_map[0]), p2i(&_read_byte_map[last_valid_index()]));
  log_trace(gc, barrier)("    _read_byte_map_base: " INTPTR_FORMAT, p2i(_read_byte_map_base));
}

void ShenandoahCardTable::initialize(const ReservedSpace& card_table) {
  if (!card_table.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for the card marking array");
  }

  MemTracker::record_virtual_memory_tag(card_table, mtGC);

  os::trace_page_sizes("Card Table", _byte_map_size, _byte_map_size,
                       card_table.base(), card_table.size(), _page_size);
  os::commit_memory_or_exit(card_table.base(), _byte_map_size, card_table.alignment(), false,
                            "Cannot commit memory for card table");
}

bool ShenandoahCardTable::is_in_young(const void* obj) const {
  return ShenandoahHeap::heap()->is_in_young(obj);
}

CardValue* ShenandoahCardTable::read_byte_for(const void* p) {
    CardValue* result = &_read_byte_map_base[uintptr_t(p) >> _card_shift];
    assert(result >= _read_byte_map && result < _read_byte_map + _byte_map_size,
           "out of bounds accessor for card marking array");
    return result;
}

size_t ShenandoahCardTable::last_valid_index() {
  return CardTable::last_valid_index();
}
