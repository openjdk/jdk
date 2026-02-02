/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

inline uint G1EvacStats::regions_filled() const {
  return _regions_filled.load_relaxed();
}

inline size_t G1EvacStats::num_plab_filled() const {
  return _num_plab_filled.load_relaxed();
}

inline size_t G1EvacStats::region_end_waste() const {
  return _region_end_waste.load_relaxed();
}

inline size_t G1EvacStats::direct_allocated() const {
  return _direct_allocated.load_relaxed();
}

inline size_t G1EvacStats::num_direct_allocated() const {
  return _num_direct_allocated.load_relaxed();
}

inline size_t G1EvacStats::failure_used() const {
  return _failure_used.load_relaxed();
}

inline size_t G1EvacStats::failure_waste() const {
  return _failure_waste.load_relaxed();
}

inline void G1EvacStats::add_direct_allocated(size_t value) {
  _direct_allocated.add_then_fetch(value, memory_order_relaxed);
}

inline void G1EvacStats::add_num_plab_filled(size_t value) {
  _num_plab_filled.add_then_fetch(value, memory_order_relaxed);
}

inline void G1EvacStats::add_num_direct_allocated(size_t value) {
  _num_direct_allocated.add_then_fetch(value, memory_order_relaxed);
}

inline void G1EvacStats::add_region_end_waste(size_t value) {
  _region_end_waste.add_then_fetch(value, memory_order_relaxed);
  _regions_filled.add_then_fetch(1u, memory_order_relaxed);
}

inline void G1EvacStats::add_failure_used_and_waste(size_t used, size_t waste) {
  _failure_used.add_then_fetch(used, memory_order_relaxed);
  _failure_waste.add_then_fetch(waste, memory_order_relaxed);
}

#endif // SHARE_GC_G1_G1EVACSTATS_INLINE_HPP
