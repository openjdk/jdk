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

#ifndef SHARE_GC_G1_G1FULLGCHEAPREGIONATTR_HPP
#define SHARE_GC_G1_G1FULLGCHEAPREGIONATTR_HPP

#include "gc/g1/g1BiasedArray.hpp"

// This table is used to store attribute values of all HeapRegions that need
// fast access during the full collection. In particular some parts of the region
// type information is encoded in these per-region bytes.
// Value encoding has been specifically chosen to make required accesses fast.
// In particular, the table collects whether a region should be compacted, not
// compacted, or not even marked through.
// Reasons for not compacting a region:
// (1) the HeapRegion itself has been pinned at the start of Full GC.
// (2) the occupancy of the region is too high to be considered eligible for compaction.
// Not marked through regions are neither compacted nor even marked through for
// performance reasons. Currently only Closed Archive regions are of this kind.
class G1FullGCHeapRegionAttr : public G1BiasedMappedArray<uint8_t> {
  static const uint8_t Compacted = 0;        // Region will be compacted.
  static const uint8_t NotCompacted = 1;     // Region should not be compacted, but otherwise handled as usual.
  static const uint8_t NotMarkedThrough = 2; // Region should not even be marked through.

  static const uint8_t Invalid = 255;

  bool is_invalid(HeapWord* obj) const {
    return get_by_address(obj) == Invalid;
  }

protected:
  uint8_t default_value() const { return Invalid; }

public:
  void set_invalid(uint idx) { set_by_index(idx, Invalid); }

  void set_not_marked_through(uint idx) { set_by_index(idx, NotMarkedThrough); }

  bool is_not_marked_through(HeapWord* obj) const {
    assert(!is_invalid(obj), "not initialized yet");
    return get_by_address(obj) == NotMarkedThrough;
  }

  void set_not_compacted(uint idx) { set_by_index(idx, NotCompacted); }

  bool is_not_compacted_but_marked_through(uint idx) const {
    return get_by_index(idx) == NotCompacted;
  }

  void set_compacted(uint idx) { set_by_index(idx, Compacted); }

  bool is_compacted(HeapWord* obj) const {
    assert(!is_invalid(obj), "not initialized yet");
    return get_by_address(obj) == Compacted;
  }
};

#endif // SHARE_GC_G1_G1FULLGCHEAPREGIONATTR_HPP
