/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1REGIONPINCACHE_HPP
#define SHARE_GC_G1_G1REGIONPINCACHE_HPP

#include "memory/allocation.hpp"
#include "utilities/pair.hpp"
#include "utilities/globalDefinitions.hpp"

// Holds the pinned object count increment for the given region for a Java thread.
// I.e. the _count value may actually be negative temporarily if pinning operations
// were interleaved between two regions.
class G1RegionPinCache : public StackObj {
  uint _region_idx;
  size_t _count;

public:
  G1RegionPinCache() : _region_idx(0), _count(0) { }
  ~G1RegionPinCache();

  uint region_idx() const { return _region_idx; }
  size_t count() const { return _count; }

  void inc_count() { ++_count; }
  void dec_count() { --_count; }

  size_t get_and_set(uint new_region_idx, size_t new_count) {
    size_t result = _count;
    _region_idx = new_region_idx;
    _count = new_count;
    return result;
  }

  // Gets current region and pin count and resets the values to defaults.
  Pair<uint, size_t> get_and_reset() {
    return Pair<uint, size_t> { _region_idx, get_and_set(0, 0) };
  }
};

#endif /* SHARE_GC_G1_G1REGIONPINCACHE_HPP */
