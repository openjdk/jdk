/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1RegionMap.inline.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"

G1RegionMap::G1RegionMap() :
  _active(mtGC),
  _inactive(mtGC),
  _num_active(0),
  _num_inactive(0) { }

void G1RegionMap::initialize(uint num_regions) {
  _active.initialize(num_regions);
  _inactive.initialize(num_regions);
}

uint G1RegionMap::num_active() const {
  return _num_active;
}

uint G1RegionMap::num_inactive() const {
  return _num_inactive;
}

uint G1RegionMap::max_length() const {
  return (uint) _active.size();
}

void G1RegionMap::activate(uint start, uint end) {
  verify_active_count(start, end, 0);
  verify_inactive_count(start, end, 0);

  log_debug(gc, heap, region)("Activate regions [%u, %u)", start, end);

  active_set_range(start, end);
}

void G1RegionMap::reactivate(uint start, uint end) {
  verify_active_count(start, end, 0);
  verify_inactive_count(start, end, (end - start));

  log_debug(gc, heap, region)("Reactivate regions [%u, %u)", start, end);

  active_set_range(start, end);
  inactive_clear_range(start, end);
}

void G1RegionMap::deactivate(uint start, uint end) {
  verify_active_count(start, end, (end - start));
  verify_inactive_count(start, end, 0);

  log_debug(gc, heap, region)("Deactivate regions [%u, %u)", start, end);

  active_clear_range(start, end);
  inactive_set_range(start, end);
}

void G1RegionMap::free(uint start, uint end) {
  verify_active_count(start, end, 0);
  verify_inactive_count(start, end, (end-start));

  log_debug(gc, heap, region)("Free regions [%u, %u)", start, end);

  inactive_clear_range(start, end);
}

HeapRegionRange G1RegionMap::next_active_range(uint offset) const {
  // Find first active index from offset.
  uint start = (uint) _active.get_next_one_offset(offset);
  if (start == max_length()) {
    // Early out when no active regions are found.
    return HeapRegionRange(max_length(), max_length());
  }

  uint end = (uint) _active.get_next_zero_offset(start);
  verify_active_range(start, end);

  return HeapRegionRange(start, end);
}

HeapRegionRange G1RegionMap::next_free_range(uint offset) const {
  // We should only call this function when there are no inactive regions.
  verify_no_inactive_regons();

  // Find first free region from offset.
  uint start = (uint) _active.get_next_zero_offset(offset);
  if (start == max_length()) {
    // Early out when no free regions are found.
    return HeapRegionRange(max_length(), max_length());
  }

  uint end = (uint) _active.get_next_one_offset(start);
  verify_free_range(start, end);

  return HeapRegionRange(start, end);
}

HeapRegionRange G1RegionMap::next_inactive_range(uint offset) const {
  // Find first inactive region from offset.
  uint start = (uint) _inactive.get_next_one_offset(offset);

  if (start == max_length()) {
    // Early when no inactive regions are found.
    return HeapRegionRange(max_length(), max_length());
  }

  uint end = (uint) _inactive.get_next_zero_offset(start);
  verify_inactive_range(start, end);

  return HeapRegionRange(start, end);
}

void G1RegionMap::active_set_range(uint start, uint end) {
  guarantee_mt_safty_active();

  _active.par_set_range(start, end, BitMap::unknown_range);
  _num_active += (end - start);
}

void G1RegionMap::active_clear_range(uint start, uint end) {
  guarantee_mt_safty_active();

  _active.par_clear_range(start, end, BitMap::unknown_range);
  _num_active -= (end - start);
}

void G1RegionMap::inactive_set_range(uint start, uint end) {
  guarantee_mt_safty_inactive();

  _inactive.par_set_range(start, end, BitMap::unknown_range);
  _num_inactive += (end - start);
}

void G1RegionMap::inactive_clear_range(uint start, uint end) {
  guarantee_mt_safty_inactive();

  _inactive.par_clear_range(start, end, BitMap::unknown_range);
  _num_inactive -= (end - start);
}

void G1RegionMap::guarantee_mt_safty_active() const {
  // G1RegionMap _active-map MT safety protocol:
  // (a) If we're at a safepoint, the caller must either be the VM thread or
  //     hold the FreeList_lock.
  // (b) If we're not at a safepoint, the caller must hold the Heap_lock.
  // Protocol only applies after initialization is complete.

  if (!Universe::is_fully_initialized()) {
    return;
  }

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              FreeList_lock->owned_by_self(),
              "G1RegionMap _active-map MT safety protocol at a safepoint");
  } else {
    guarantee(Heap_lock->owned_by_self(),
              "G1RegionMap _active-map MT safety protocol outside a safepoint");
  }
}

void G1RegionMap::guarantee_mt_safty_inactive() const {
  // G1RegionMap _inactive-map MT safety protocol:
  // (a) If we're at a safepoint, the caller must either be the VM thread or
  //     hold the FreeList_lock.
  // (b) If we're not at a safepoint, the caller must hold the Uncommit_lock.
  // Protocol only applies after initialization is complete.

  if (!Universe::is_fully_initialized()) {
    return;
  }

  if (SafepointSynchronize::is_at_safepoint()) {
    guarantee(Thread::current()->is_VM_thread() ||
              FreeList_lock->owned_by_self(),
              "G1RegionMap MT safety protocol at a safepoint");
  } else {
    guarantee(Uncommit_lock->owned_by_self(),
              "G1RegionMap MT safety protocol outside a safepoint");
  }
}

#ifdef ASSERT
void G1RegionMap::verify_active_range(uint start, uint end) const {
  assert(active(start), "First region (%u) is not active", start);
  assert(active(end - 1), "Last region (%u) is not active", end - 1);
  assert(end == _active.size() || !active(end), "Region (%u) is active but not included in range", end);
}

void G1RegionMap::verify_inactive_range(uint start, uint end) const {
  assert(inactive(start), "First region (%u) is not inactive", start);
  assert(inactive(end - 1), "Last region (%u) in range is not inactive", end - 1);
  assert(end == _inactive.size() || !inactive(end), "Region (%u) is inactive but not included in range", end);
}

void G1RegionMap::verify_free_range(uint start, uint end) const {
  assert(!active(start), "First region (%u) is active", start);
  assert(!active(end - 1), "Last region (%u) in range is active", end - 1);
}

void G1RegionMap::verify_no_inactive_regons() const {
  BitMap::idx_t first_inactive = _inactive.get_next_one_offset(0);
  assert(first_inactive == _inactive.size(), "Should be no inactive regions, but was at index: " SIZE_FORMAT, first_inactive);
}

void G1RegionMap::verify_active_count(uint start, uint end, uint expected) const {
  uint found = (uint) _active.count_one_bits(start, end);
  assert(found == expected, "Unexpected number of active regions, found: %u, expected: %u", found, expected);
}

void G1RegionMap::verify_inactive_count(uint start, uint end, uint expected) const {
  uint found = (uint) _inactive.count_one_bits(start, end);
  assert(found == expected, "Unexpected number of inactive regions, found: %u, expected: %u", found, expected);
}

#endif //ASSERT
