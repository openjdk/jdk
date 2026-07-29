/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHREGIONPINCACHE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHREGIONPINCACHE_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

// Holds (caches) the pending pinned object count adjustment for the region
// _region_idx on a per thread basis.
// Keeping such a cache avoids the expensive atomic operations when updating the
// pin count for the very common case that the application pins and unpins the
// same object without any interleaving by a garbage collection or pinning/unpinning
// to an object in another region.
class ShenandoahRegionPinCache : public StackObj {
  size_t _region_idx;
  size_t _count;

  void flush_and_set(size_t new_region_idx, size_t new_count);

public:
  ShenandoahRegionPinCache() : _region_idx(SIZE_MAX), _count(0) { }

  void inc_count(size_t region_idx);
  void dec_count(size_t region_idx);

  void flush();
};

#endif /* SHARE_GC_SHENANDOAH_SHENANDOAHREGIONPINCACHE_HPP */
