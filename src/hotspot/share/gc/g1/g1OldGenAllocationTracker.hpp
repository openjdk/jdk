/*
 * Copyright (c) 2020, Amazon.com, Inc. or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1OLDGENALLOCATIONTRACKER_HPP
#define SHARE_VM_GC_G1_G1OLDGENALLOCATIONTRACKER_HPP

#include "gc/g1/heapRegion.hpp"
#include "memory/allocation.hpp"

// Track allocation details in the old generation.
class G1OldGenAllocationTracker : public CHeapObj<mtGC> {
  // New bytes allocated in old gen between the end of the last GC and
  // the end of the GC before that.
  size_t _last_cycle_old_bytes;
  // The number of seconds between the end of the last GC and
  // the end of the GC before that.
  double _last_cycle_duration;

  size_t _allocated_bytes_since_last_gc;

  void reset_cycle_after_gc() {
    _last_cycle_old_bytes = _allocated_bytes_since_last_gc;
    _allocated_bytes_since_last_gc = 0;
  }

public:
  G1OldGenAllocationTracker();
  // Add the given number of bytes to the total number of allocated bytes in the old gen.
  void add_allocated_bytes_since_last_gc(size_t bytes) { _allocated_bytes_since_last_gc += bytes; }

  size_t last_cycle_old_bytes() { return _last_cycle_old_bytes; }

  double last_cycle_duration() { return _last_cycle_duration; }

  // Reset stats after a collection.
  void reset_after_full_gc();
  void reset_after_young_gc(double allocation_duration_s);
};

#endif // SHARE_VM_GC_G1_G1OLDGENALLOCATIONTRACKER_HPP