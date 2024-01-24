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
#include "utilities/globalDefinitions.hpp"

// Holds the pinned object count increment for the given region for a Java thread.
// I.e. the _count value may actually be negative temporarily if pinning operations
// were interleaved between two regions.
class G1RegionPinCache : public StackObj {
  uint _region_idx;
  size_t _count;

  void flush_and_set(uint new_region_idx, size_t new_count);

public:
  G1RegionPinCache() : _region_idx(0), _count(0) { }
  ~G1RegionPinCache();

#ifdef ASSERT
  size_t count() const { return _count; }
#endif

  void inc_count(uint region_idx);
  void dec_count(uint region_idx);

  void flush();
};

#endif /* SHARE_GC_G1_G1REGIONPINCACHE_HPP */
