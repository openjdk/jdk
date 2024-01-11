/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNMT.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "memory/allocation.hpp"
#include "nmt/memTracker.hpp"
#include "utilities/nativeCallStack.hpp"

ZNMT::Reservation ZNMT::_reservations[ZMaxVirtualReservations] = {};
size_t ZNMT::_num_reservations = 0;

size_t ZNMT::reservation_index(zoffset offset, size_t* offset_in_reservation) {
  assert(_num_reservations > 0, "at least one reservation must exist");

  size_t index = 0;
  *offset_in_reservation = untype(offset);
  for (; index < _num_reservations; ++index) {
    const size_t reservation_size = _reservations[index]._size;
    if (*offset_in_reservation < reservation_size) {
      break;
    }
    *offset_in_reservation -= reservation_size;
  }

  assert(index != _num_reservations, "failed to find reservation index");
  return index;
}

void ZNMT::process_fake_mapping(zoffset offset, size_t size, bool commit) {
  // In order to satisfy NTM's requirement of an 1:1 mapping between committed
  // and reserved addresses, a fake mapping from the offset into the reservation
  // is used.
  //
  // These mappings from
  //   [offset, offset + size) -> {[virtual address range], ...}
  // are stable after the heap has been reserved. No commits proceed any
  // reservations. Committing and uncommitting the same [offset, offset + size)
  // range will result in same virtual memory ranges.

  size_t left_to_process = size;
  size_t offset_in_reservation;
  for (size_t i = reservation_index(offset, &offset_in_reservation); i < _num_reservations; ++i) {
    const zaddress_unsafe reservation_start = _reservations[i]._start;
    const size_t reservation_size = _reservations[i]._size;
    const size_t sub_range_size = MIN2(left_to_process, reservation_size - offset_in_reservation);
    const uintptr_t sub_range_addr = untype(reservation_start) + offset_in_reservation;

    // commit / uncommit memory
    if (commit) {
      MemTracker::record_virtual_memory_commit((void*)sub_range_addr, sub_range_size, CALLER_PC);
    } else {
      if (MemTracker::enabled()) {
        Tracker tracker(Tracker::uncommit);
        tracker.record((address)sub_range_addr, sub_range_size);
      }
    }

    left_to_process -= sub_range_size;
    if (left_to_process == 0) {
      // Processed all nmt registrations
      return;
    }

    offset_in_reservation = 0;
  }

  assert(left_to_process == 0, "everything was not commited");
}

void ZNMT::reserve(zaddress_unsafe start, size_t size) {
  assert(_num_reservations < ZMaxVirtualReservations, "too many reservations");
  // Keep track of the reservations made in order to create fake mappings
  // between the reserved and commited memory.
  // See details in ZNMT::process_fake_mapping
  _reservations[_num_reservations++] = {start, size};

  MemTracker::record_virtual_memory_reserve((void*)untype(start), size, CALLER_PC, mtJavaHeap);
}

void ZNMT::commit(zoffset offset, size_t size) {
  // NMT expects a 1-to-1 mapping between virtual and physical memory.
  // ZGC can temporarily have multiple virtual addresses pointing to
  // the same physical memory.
  //
  // When this function is called we don't know where in the virtual memory
  // this physical memory will be mapped. So we fake the virtual memory
  // address by mapping the physical offset into offsets in the reserved
  // memory space.
  process_fake_mapping(offset, size, true);
}

void ZNMT::uncommit(zoffset offset, size_t size) {
  // We fake the virtual memory address by mapping the physical offset
  // into offsets in the reserved memory space.
  // See comment in ZNMT::commit
  process_fake_mapping(offset, size, false);
}
