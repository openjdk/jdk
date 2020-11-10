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
class G1FullGCHeapRegionAttr : public G1BiasedMappedArray<uint8_t> {
  static const uint8_t Normal = 0;        // Other kind of region
  static const uint8_t Pinned = 1;        // Region is a pinned (non-Closed Archive) region
  static const uint8_t ClosedArchive = 2; // Region is a (pinned) Closed Archive region

  STATIC_ASSERT(ClosedArchive > Pinned);

  static const uint8_t Invalid = 255;

  bool is_invalid(HeapWord* obj) const {
    return get_by_address(obj) == Invalid;
  }

protected:
  uint8_t default_value() const { return Invalid; }

public:
  void set_closed_archive(uint idx) { set_by_index(idx, ClosedArchive); }

  bool is_closed_archive(HeapWord* obj) const {
    assert(!is_invalid(obj), "not initialized yet");
    return get_by_address(obj) == ClosedArchive;
  }

  void set_pinned(uint idx) { set_by_index(idx, Pinned); }

  bool is_pinned_or_closed(HeapWord* obj) const {
    assert(!is_invalid(obj), "not initialized yet");
    return get_by_address(obj) >= Pinned;
  }

  bool is_pinned(HeapWord* obj) const {
    assert(!is_invalid(obj), "not initialized yet");
    return get_by_address(obj) == Pinned;
  }

  void set_normal(uint idx) { set_by_index(idx, Normal); }

  bool is_normal(HeapWord* obj) const {
    assert(!is_invalid(obj), "not initialized yet");
    return get_by_address(obj) == Normal;
  }
};

#endif // SHARE_GC_G1_G1FULLGCHEAPREGIONATTR_HPP
