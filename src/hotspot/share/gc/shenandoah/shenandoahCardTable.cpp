/*
 * Copyright (c) 2020, 2021, Amazon.com, Inc. and/or its affiliates. All rights reserved.
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
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

void ShenandoahCardTable::initialize() {
  CardTable::initialize();
  _write_byte_map = _byte_map;
  _write_byte_map_base = _byte_map_base;
  const size_t rs_align = _page_size == (size_t) os::vm_page_size() ? 0 :
    MAX2(_page_size, (size_t) os::vm_allocation_granularity());

  ReservedSpace heap_rs(_byte_map_size, rs_align, false);
  if (!heap_rs.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for second copy of card marking array");
  }
  os::commit_memory_or_exit(heap_rs.base(), _byte_map_size, rs_align, false, "Cannot commit memory for second copy of card table");

  HeapWord* low_bound  = _whole_heap.start();
  _read_byte_map = (CardValue*) heap_rs.base();
  _read_byte_map_base = _read_byte_map - (uintptr_t(low_bound) >> card_shift);

  log_trace(gc, barrier)("ShenandoahCardTable::ShenandoahCardTable: ");
  log_trace(gc, barrier)("    &_read_byte_map[0]: " INTPTR_FORMAT "  &_read_byte_map[_last_valid_index]: " INTPTR_FORMAT,
                  p2i(&_read_byte_map[0]), p2i(&_read_byte_map[_last_valid_index]));
  log_trace(gc, barrier)("    _read_byte_map_base: " INTPTR_FORMAT, p2i(_read_byte_map_base));

  // TODO: As currently implemented, we do not swap pointers between _read_byte_map and _write_byte_map
  // because the mutator write barrier hard codes the address of the _write_byte_map_base.  Instead,
  // the current implementation simply copies contents of _write_byte_map onto _read_byte_map and cleans
  // the entirety of _write_byte_map at the init_mark safepoint.
  //
  // If we choose to modify the mutator write barrier so that we can swap _read_byte_map_base and
  // _write_byte_map_base pointers, we may also have to figure out certain details about how the
  // _guard_region is implemented so that we can replicate the read and write versions of this region.
  //
  // Alternatively, we may switch to a SATB-based write barrier and replace the direct card-marking
  // remembered set with something entirely different.

  resize_covered_region(_whole_heap);
}

bool ShenandoahCardTable::is_in_young(oop obj) const {
  return ShenandoahHeap::heap()->is_in_young(obj);
}

bool ShenandoahCardTable::is_dirty(MemRegion mr) {
  for (size_t i = index_for(mr.start()); i <= index_for(mr.end() - 1); i++) {
    CardValue* byte = byte_for_index(i);
    if (*byte == CardTable::dirty_card_val()) {
      return true;
    }
  }
  return false;
}

void ShenandoahCardTable::clear() {
  CardTable::clear(_whole_heap);
}

// TODO: This service is not currently used because we are not able to swap _read_byte_map_base and
// _write_byte_map_base pointers.  If we were able to do so, we would invoke clear_read_table "immediately"
// following the end of concurrent remembered set scanning so that this read card table would be ready
// to serve as the new write card table at the time these pointer values were next swapped.
//
// In the current implementation, the write-table is cleared immediately after its contents is copied to
// the read table, obviating the need for this service.
void ShenandoahCardTable::clear_read_table() {
  for (size_t i = 0; i < _byte_map_size; i++) {
    _read_byte_map[i] = clean_card;
  }
}

// TODO: This service is not currently used because the mutator write barrier implementation hard codes the
// location of the _write_byte_may_base.  If we change the mutator's write barrier implementation, then we
// may use this service to exchange the roles of the read-card-table and write-card-table.
void ShenandoahCardTable::swap_card_tables() {
  shenandoah_assert_safepoint();

  CardValue* save_value = _read_byte_map;
  _read_byte_map = _write_byte_map;
  _write_byte_map = save_value;

  save_value = _read_byte_map_base;
  _read_byte_map_base = _write_byte_map_base;
  _write_byte_map_base = save_value;

  // update the superclass instance variables
  _byte_map = _write_byte_map;
  _byte_map_base = _write_byte_map_base;
}
