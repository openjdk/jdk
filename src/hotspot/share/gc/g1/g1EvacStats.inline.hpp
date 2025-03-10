/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1EVACSTATS_INLINE_HPP
#define SHARE_GC_G1_G1EVACSTATS_INLINE_HPP

#include "gc/g1/g1EvacStats.hpp"

#include "runtime/atomic.hpp"

inline void G1EvacStats::add_direct_allocated(size_t value) {
  Atomic::add(&_direct_allocated, value, memory_order_relaxed);
}

inline void G1EvacStats::add_num_plab_filled(size_t value) {
  Atomic::add(&_num_plab_filled, value, memory_order_relaxed);
}

inline void G1EvacStats::add_num_direct_allocated(size_t value) {
  Atomic::add(&_num_direct_allocated, value, memory_order_relaxed);
}

inline void G1EvacStats::add_region_end_waste(size_t value) {
  Atomic::add(&_region_end_waste, value, memory_order_relaxed);
  Atomic::inc(&_regions_filled, memory_order_relaxed);
}

inline void G1EvacStats::add_failure_used_and_waste(size_t used, size_t waste) {
  Atomic::add(&_failure_used, used, memory_order_relaxed);
  Atomic::add(&_failure_waste, waste, memory_order_relaxed);
}

#endif // SHARE_GC_G1_G1EVACSTATS_INLINE_HPP
