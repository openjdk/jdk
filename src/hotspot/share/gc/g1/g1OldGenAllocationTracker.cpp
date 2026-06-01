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

#include "gc/g1/g1OldGenAllocationTracker.hpp"
#include "logging/log.hpp"

G1OldGenAllocationTracker::G1OldGenAllocationTracker() :
  _humongous_bytes_after_last_pause(0),
  _allocated_non_humongous_bytes_since_last_pause(0),
  _allocated_humongous_bytes_since_last_pause(0) {
}

G1AllocationIntervalStats G1OldGenAllocationTracker::end_allocation_interval(size_t humongous_bytes_after_pause) {
  // Calculate actual increase in old, taking eager reclaim into consideration.
  size_t last_interval_humongous_increase = 0;
  if (humongous_bytes_after_pause > _humongous_bytes_after_last_pause) {
    last_interval_humongous_increase = humongous_bytes_after_pause - _humongous_bytes_after_last_pause;
    assert(last_interval_humongous_increase <= _allocated_humongous_bytes_since_last_pause,
           "Increase larger than allocated %zu <= %zu",
           last_interval_humongous_increase, _allocated_humongous_bytes_since_last_pause);
  }

  size_t last_interval_old_gen_growth = _allocated_non_humongous_bytes_since_last_pause +
                                        last_interval_humongous_increase;

  G1AllocationIntervalStats allocation_interval_stats{
    _allocated_non_humongous_bytes_since_last_pause,
    _allocated_humongous_bytes_since_last_pause,
    _humongous_bytes_after_last_pause,
    humongous_bytes_after_pause
  };

  _humongous_bytes_after_last_pause = humongous_bytes_after_pause;

  log_debug(gc, alloc, stats)("Old generation allocation in the last allocation interval, "
                              "old gen allocated: %zuB, humongous allocated: %zuB, "
                              "old gen growth: %zuB.",
                              _allocated_non_humongous_bytes_since_last_pause,
                              _allocated_humongous_bytes_since_last_pause,
                              last_interval_old_gen_growth);

  // Reset for the next interval.
  _allocated_non_humongous_bytes_since_last_pause = 0;
  _allocated_humongous_bytes_since_last_pause = 0;

  return allocation_interval_stats;
}
