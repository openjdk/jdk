/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZBARRIER_HPP
#define SHARE_GC_Z_ZBARRIER_HPP

#include "gc/z/zAddress.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"

// == Shift based load barrier ==
//
// The load barriers of ZGC check if a loaded value is safe to expose or not, and
// then shifts the pointer to remove metadata bits, such that it points to mapped
// memory.
// A pointer is safe to expose if it does not have any load-bad bits set in its
// metadata bits. In the C++ code and non-nmethod generated code, that is checked
// by testing the pointer value against a load-bad mask, checking that no bad bit
// is set, followed by a shift, removing the metadata bits if they were good.
// However, for nmethod code, the test + shift sequence is optimized in such
// a way that the shift both tests if the pointer is exposable or not, and removes
// the metadata bits, with the same instruction. This is a speculative optimization
// that assumes that the loaded pointer is frequently going to be load-good or null
// when checked. Therefore, the nmethod load barriers just apply the shift with the
// current "good" shift (which is patched with nmethod entry barriers for each GC
// phase). If the result of that shift was a raw NULL value, then the ZF flag is set.
// If the result is a good pointer, then the very last bit that was removed by the
// shift, must have been a 1, which would have set the CF flag. Therefore, the "above"
// branch condition code is used to take a slowpath only iff CF == 0 and ZF == 0.
// CF == 0 implies it was not a good pointer, and ZF == 0 implies the resulting address
// was not a NULL value. Then we decide that the pointer is bad. This optimization
// is necessary to get satisfactory performance, but does come with a few constraints:
// 1) The load barrier can only recognize 4 different good patterns across all GC phases.
//    The reason is that when a load barrier applies the currently good shift, then
//    the value of said shift may differ only by 3, until we risk shifting away more
//    than the low order three zeroes of an addres, given a bad pointer, which would
//    yield spurious false positives.
// 2) Those bit patterns must have only a single bit set. We achieve that by moving
//    non-relocation work to store barriers.
// Another consequence of this speculative optimization, is that when the compiled code
// takes a slow path, it needs to reload the oop, because the shifted oop is now
// broken after being shifted with a different shift to what was used when the oop
// was stored.

typedef bool (*ZBarrierFastPath)(zpointer);
typedef zpointer (*ZBarrierColor)(zaddress, zpointer);

class ZCycle;

class ZBarrier : public AllStatic {
  friend class ZStoreBarrierBuffer;
  friend class ZUncoloredRoot;

private:
  static void self_heal(ZBarrierFastPath fast_path, volatile zpointer* p, zpointer ptr, zpointer heal_ptr, bool allow_null);

  template <typename ZBarrierSlowPath>
  static zaddress barrier(ZBarrierFastPath fast_path, ZBarrierSlowPath slow_path, ZBarrierColor color, volatile zpointer* p, zpointer o, bool allow_null = false);

  static zaddress make_load_good(zpointer ptr);
  static zaddress make_load_good_no_relocate(zpointer ptr);
  static zaddress relocate_or_remap(zaddress_unsafe addr, ZCycle* cycle);
  static zaddress remap(zaddress_unsafe addr, ZCycle* cycle);
  static void keep_alive(zaddress addr);
  static void remember(volatile zpointer* p);
  static void keep_alive_and_remember(volatile zpointer* p, zaddress addr);

  // Fast paths in increasing strength level
  static bool is_load_good_fast_path(zpointer ptr);
  static bool is_mark_good_fast_path(zpointer ptr);
  static bool is_store_good_fast_path(zpointer ptr);

  static bool is_mark_minor_good_fast_path(zpointer ptr);
  static bool is_finalizable_good_fast_path(zpointer ptr);

  // Slow paths
  static zaddress is_object_strongly_live_filter_slow_path(zaddress addr);
  static zaddress is_old_object_strongly_live_filter_slow_path(zaddress addr);
  static zaddress blocking_keep_alive_on_weak_slow_path(zaddress addr);
  static zaddress blocking_keep_alive_on_phantom_slow_path(zaddress addr);
  static zaddress blocking_load_barrier_on_weak_slow_path(zaddress addr);
  static zaddress blocking_load_barrier_on_phantom_slow_path(zaddress addr);
  static zaddress is_object_live_filter_slow_path(zaddress addr);
  static zaddress is_old_object_live_filter_slow_path(zaddress addr);

  static zaddress verify_old_object_live_slow_path(zaddress addr);

  static zaddress mark_slow_path(zaddress addr);
  static zaddress mark_minor_slow_path(zaddress addr);
  static zaddress mark_finalizable_slow_path(zaddress addr);

  static zaddress keep_alive_and_remember_slow_path(volatile zpointer* p, zaddress addr, zpointer prev, bool heal);
  static zaddress keep_alive_slow_path(zaddress addr);

  // Helpers for non-strong oop refs barriers
  static zaddress blocking_keep_alive_load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o);
  static zaddress blocking_keep_alive_load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o);
  static zaddress blocking_load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o);
  static zaddress blocking_load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o);

  // Verification
  static void verify_on_weak(volatile zpointer* referent_addr) NOT_DEBUG_RETURN;

public:
  static zpointer load_atomic(volatile zpointer* p);

  // Helper for relocation
  static void remap_minor_relocated(volatile zpointer* p, zpointer o);

  // Load barrier
  static zaddress load_barrier_on_oop_field(volatile zpointer* p);
  static zaddress load_barrier_on_oop_field_preloaded(volatile zpointer* p, zpointer o);

  static zaddress keep_alive_load_barrier_on_oop_field_preloaded(volatile zpointer* p, zpointer o);

  // Load barriers on non-strong oop refs
  static zaddress load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o);
  static zaddress load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o);

  static zaddress no_keep_alive_load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o);
  static zaddress no_keep_alive_load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o);

  // Reference processor / weak cleaning barriers
  static bool clean_barrier_on_weak_oop_field(volatile zpointer* p);
  static bool clean_barrier_on_phantom_oop_field(volatile zpointer* p);
  static bool clean_barrier_on_final_oop_field(volatile zpointer* p);

  // Mark barrier
  static void mark_barrier_on_young_oop_field(volatile zpointer* p);
  static void mark_barrier_on_oop_field(volatile zpointer* p, bool finalizable);
  static zaddress mark_minor_good_barrier_on_oop_field(volatile zpointer* p);

  // Store barrier
  static void store_barrier_on_heap_oop_field(volatile zpointer* p, bool heal);
  static void store_barrier_on_native_oop_field(volatile zpointer* p, bool heal);
};

#endif // SHARE_GC_Z_ZBARRIER_HPP
