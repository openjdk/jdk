/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZNUMA_INLINE_HPP
#define SHARE_GC_Z_ZNUMA_INLINE_HPP

#include "gc/z/zNUMA.hpp"

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zGlobals.hpp"
#include "utilities/align.hpp"

inline bool ZNUMA::is_enabled() {
  return _enabled;
}

inline bool ZNUMA::is_faked() {
  return ZFakeNUMA > 1;
}

inline uint32_t ZNUMA::count() {
  return _count;
}

inline size_t ZNUMA::calculate_share(uint32_t numa_id, size_t total, size_t granule, uint32_t ignore_count) {
  assert(total % granule == 0, "total must be divisible by granule");
  assert(ignore_count < count(), "must not ignore all nodes");
  assert(numa_id < count() - ignore_count, "numa_id must be in bounds");

  const uint32_t num_nodes = count() - ignore_count;
  const size_t base_share = ((total / num_nodes) / granule) * granule;

  const size_t extra_share_nodes = (total - base_share * num_nodes) / granule;
  if (numa_id < extra_share_nodes) {
    return base_share + granule;
  }

  return base_share;
}

#endif // SHARE_GC_Z_ZNUMA_INLINE_HPP
