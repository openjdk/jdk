/*
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

#ifndef SHARE_GC_G1_G1OLDGENALLOCATIONTRACKER_HPP
#define SHARE_GC_G1_G1OLDGENALLOCATIONTRACKER_HPP

#include "memory/allocation.hpp"

// Allocation statistics for an allocation interval, i.e. the interval between two
// consecutive STW pauses.
//
//  The allocation counters record allocations made during that interval.
//
//  _total_humongous_before_bytes is the humongous occupancy after the previous
//  pause (at the start of the allocation interval).
//
//  _total_humongous_after_bytes is the humongous occupancy after the current
//  pause (at the end of the allocation interval).
struct G1AllocationIntervalStats {
  size_t _non_humongous_allocated_bytes;
  size_t _humongous_allocated_bytes;
  size_t _total_humongous_before_bytes;
  size_t _total_humongous_after_bytes;

  G1AllocationIntervalStats(size_t non_humongous_allocated_bytes,
                            size_t humongous_allocated_bytes,
                            size_t total_humongous_before_bytes,
                            size_t total_humongous_after_bytes)
    : _non_humongous_allocated_bytes(non_humongous_allocated_bytes),
      _humongous_allocated_bytes(humongous_allocated_bytes),
      _total_humongous_before_bytes(total_humongous_before_bytes),
      _total_humongous_after_bytes(total_humongous_after_bytes)
    { }

  void record_humongous_allocation(size_t humongous_allocation_bytes) {
    _humongous_allocated_bytes += humongous_allocation_bytes;
    _total_humongous_after_bytes += humongous_allocation_bytes;
  }
};

// Track allocation details in the old generation.
class G1OldGenAllocationTracker : public CHeapObj<mtGC> {
  // Total size of humongous objects after the last STW pause.
  size_t _humongous_bytes_after_last_pause;

  // Non-humongous old generation allocations since the last STW pause.
  size_t _allocated_non_humongous_bytes_since_last_pause;
  // Humongous allocations during last allocation interval.
  size_t _allocated_humongous_bytes_since_last_pause;

public:
  G1OldGenAllocationTracker();

  void add_allocated_non_humongous_bytes(size_t bytes) {
    _allocated_non_humongous_bytes_since_last_pause += bytes;
  }
  void add_allocated_humongous_bytes(size_t bytes) {
    _allocated_humongous_bytes_since_last_pause += bytes;
  }

  // Record a humongous allocation in a collection pause. This allocation
  // is accounted to the previous allocation interval.
  void record_collection_pause_humongous_allocation(size_t bytes) {
    _humongous_bytes_after_last_pause += bytes;
  }

  // Calculate and reset allocation statistics after a pause.
  G1AllocationIntervalStats end_allocation_interval(size_t humongous_bytes_after_pause);
};

#endif // SHARE_GC_G1_G1OLDGENALLOCATIONTRACKER_HPP
