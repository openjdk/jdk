/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZBARRIER_INLINE_HPP
#define SHARE_GC_Z_ZBARRIER_INLINE_HPP

#include "classfile/javaClasses.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.hpp"
#include "gc/z/zOop.inline.hpp"
#include "gc/z/zResurrection.inline.hpp"
#include "oops/oop.hpp"
#include "runtime/atomic.hpp"

inline void ZBarrier::self_heal(volatile oop* p, uintptr_t addr, uintptr_t heal_addr) {
  if (heal_addr == 0) {
    // Never heal with null since it interacts badly with reference processing.
    // A mutator clearing an oop would be similar to calling Reference.clear(),
    // which would make the reference non-discoverable or silently dropped
    // by the reference processor.
    return;
  }

  for (;;) {
    if (addr == heal_addr) {
      // Already healed
      return;
    }

    // Heal
    const uintptr_t prev_addr = Atomic::cmpxchg((volatile uintptr_t*)p, addr, heal_addr);
    if (prev_addr == addr) {
      // Success
      return;
    }

    if (ZAddress::is_good_or_null(prev_addr)) {
      // No need to heal
      return;
    }

    // The oop location was healed by another barrier, but it is still not
    // good or null. Re-apply healing to make sure the oop is not left with
    // weaker (remapped or finalizable) metadata bits than what this barrier
    // tried to apply.
    assert(ZAddress::offset(prev_addr) == ZAddress::offset(heal_addr), "Invalid offset");
    addr = prev_addr;
  }
}

template <ZBarrierFastPath fast_path, ZBarrierSlowPath slow_path>
inline oop ZBarrier::barrier(volatile oop* p, oop o) {
  uintptr_t addr = ZOop::to_address(o);

  // Fast path
  if (fast_path(addr)) {
    return ZOop::from_address(addr);
  }

  // Slow path
  const uintptr_t good_addr = slow_path(addr);

  if (p != NULL) {
    self_heal(p, addr, good_addr);
  }

  return ZOop::from_address(good_addr);
}

template <ZBarrierFastPath fast_path, ZBarrierSlowPath slow_path>
inline oop ZBarrier::weak_barrier(volatile oop* p, oop o) {
  const uintptr_t addr = ZOop::to_address(o);

  // Fast path
  if (fast_path(addr)) {
    // Return the good address instead of the weak good address
    // to ensure that the currently active heap view is used.
    return ZOop::from_address(ZAddress::good_or_null(addr));
  }

  // Slow path
  const uintptr_t good_addr = slow_path(addr);

  if (p != NULL) {
    // The slow path returns a good/marked address or null, but we never mark
    // oops in a weak load barrier so we always heal with the remapped address.
    self_heal(p, addr, ZAddress::remapped_or_null(good_addr));
  }

  return ZOop::from_address(good_addr);
}

template <ZBarrierFastPath fast_path, ZBarrierSlowPath slow_path>
inline void ZBarrier::root_barrier(oop* p, oop o) {
  const uintptr_t addr = ZOop::to_address(o);

  // Fast path
  if (fast_path(addr)) {
    return;
  }

  // Slow path
  const uintptr_t good_addr = slow_path(addr);

  // Non-atomic healing helps speed up root scanning. This is safe to do
  // since we are always healing roots in a safepoint, or under a lock,
  // which ensures we are never racing with mutators modifying roots while
  // we are healing them. It's also safe in case multiple GC threads try
  // to heal the same root if it is aligned, since they would always heal
  // the root in the same way and it does not matter in which order it
  // happens. For misaligned oops, there needs to be mutual exclusion.
  *p = ZOop::from_address(good_addr);
}

inline bool ZBarrier::is_null_fast_path(uintptr_t addr) {
  return ZAddress::is_null(addr);
}

inline bool ZBarrier::is_good_or_null_fast_path(uintptr_t addr) {
  return ZAddress::is_good_or_null(addr);
}

inline bool ZBarrier::is_weak_good_or_null_fast_path(uintptr_t addr) {
  return ZAddress::is_weak_good_or_null(addr);
}

inline bool ZBarrier::during_mark() {
  return ZGlobalPhase == ZPhaseMark;
}

inline bool ZBarrier::during_relocate() {
  return ZGlobalPhase == ZPhaseRelocate;
}

//
// Load barrier
//
inline oop ZBarrier::load_barrier_on_oop(oop o) {
  return load_barrier_on_oop_field_preloaded((oop*)NULL, o);
}

inline oop ZBarrier::load_barrier_on_oop_field(volatile oop* p) {
  const oop o = *p;
  return load_barrier_on_oop_field_preloaded(p, o);
}

inline oop ZBarrier::load_barrier_on_oop_field_preloaded(volatile oop* p, oop o) {
  return barrier<is_good_or_null_fast_path, load_barrier_on_oop_slow_path>(p, o);
}

inline void ZBarrier::load_barrier_on_oop_array(volatile oop* p, size_t length) {
  for (volatile const oop* const end = p + length; p < end; p++) {
    load_barrier_on_oop_field(p);
  }
}

// ON_WEAK barriers should only ever be applied to j.l.r.Reference.referents.
inline void verify_on_weak(volatile oop* referent_addr) {
#ifdef ASSERT
  if (referent_addr != NULL) {
    uintptr_t base = (uintptr_t)referent_addr - java_lang_ref_Reference::referent_offset;
    oop obj = cast_to_oop(base);
    assert(oopDesc::is_oop(obj), "Verification failed for: ref " PTR_FORMAT " obj: " PTR_FORMAT, (uintptr_t)referent_addr, base);
    assert(java_lang_ref_Reference::is_referent_field(obj, java_lang_ref_Reference::referent_offset), "Sanity");
  }
#endif
}

inline oop ZBarrier::load_barrier_on_weak_oop_field_preloaded(volatile oop* p, oop o) {
  verify_on_weak(p);

  if (ZResurrection::is_blocked()) {
    return barrier<is_good_or_null_fast_path, weak_load_barrier_on_weak_oop_slow_path>(p, o);
  }

  return load_barrier_on_oop_field_preloaded(p, o);
}

inline oop ZBarrier::load_barrier_on_phantom_oop_field_preloaded(volatile oop* p, oop o) {
  if (ZResurrection::is_blocked()) {
    return barrier<is_good_or_null_fast_path, weak_load_barrier_on_phantom_oop_slow_path>(p, o);
  }

  return load_barrier_on_oop_field_preloaded(p, o);
}

inline void ZBarrier::load_barrier_on_root_oop_field(oop* p) {
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, load_barrier_on_oop_slow_path>(p, o);
}

//
// Weak load barrier
//
inline oop ZBarrier::weak_load_barrier_on_oop_field(volatile oop* p) {
  assert(!ZResurrection::is_blocked(), "Should not be called during resurrection blocked phase");
  const oop o = *p;
  return weak_load_barrier_on_oop_field_preloaded(p, o);
}

inline oop ZBarrier::weak_load_barrier_on_oop_field_preloaded(volatile oop* p, oop o) {
  return weak_barrier<is_weak_good_or_null_fast_path, weak_load_barrier_on_oop_slow_path>(p, o);
}

inline oop ZBarrier::weak_load_barrier_on_weak_oop(oop o) {
  return weak_load_barrier_on_weak_oop_field_preloaded((oop*)NULL, o);
}

inline oop ZBarrier::weak_load_barrier_on_weak_oop_field(volatile oop* p) {
  const oop o = *p;
  return weak_load_barrier_on_weak_oop_field_preloaded(p, o);
}

inline oop ZBarrier::weak_load_barrier_on_weak_oop_field_preloaded(volatile oop* p, oop o) {
  verify_on_weak(p);

  if (ZResurrection::is_blocked()) {
    return barrier<is_good_or_null_fast_path, weak_load_barrier_on_weak_oop_slow_path>(p, o);
  }

  return weak_load_barrier_on_oop_field_preloaded(p, o);
}

inline oop ZBarrier::weak_load_barrier_on_phantom_oop(oop o) {
  return weak_load_barrier_on_phantom_oop_field_preloaded((oop*)NULL, o);
}

inline oop ZBarrier::weak_load_barrier_on_phantom_oop_field(volatile oop* p) {
  const oop o = *p;
  return weak_load_barrier_on_phantom_oop_field_preloaded(p, o);
}

inline oop ZBarrier::weak_load_barrier_on_phantom_oop_field_preloaded(volatile oop* p, oop o) {
  if (ZResurrection::is_blocked()) {
    return barrier<is_good_or_null_fast_path, weak_load_barrier_on_phantom_oop_slow_path>(p, o);
  }

  return weak_load_barrier_on_oop_field_preloaded(p, o);
}

//
// Is alive barrier
//
inline bool ZBarrier::is_alive_barrier_on_weak_oop(oop o) {
  // Check if oop is logically non-null. This operation
  // is only valid when resurrection is blocked.
  assert(ZResurrection::is_blocked(), "Invalid phase");
  return weak_load_barrier_on_weak_oop(o) != NULL;
}

inline bool ZBarrier::is_alive_barrier_on_phantom_oop(oop o) {
  // Check if oop is logically non-null. This operation
  // is only valid when resurrection is blocked.
  assert(ZResurrection::is_blocked(), "Invalid phase");
  return weak_load_barrier_on_phantom_oop(o) != NULL;
}

//
// Keep alive barrier
//
inline void ZBarrier::keep_alive_barrier_on_weak_oop_field(volatile oop* p) {
  // This operation is only valid when resurrection is blocked.
  assert(ZResurrection::is_blocked(), "Invalid phase");
  const oop o = *p;
  barrier<is_good_or_null_fast_path, keep_alive_barrier_on_weak_oop_slow_path>(p, o);
}

inline void ZBarrier::keep_alive_barrier_on_phantom_oop_field(volatile oop* p) {
  // This operation is only valid when resurrection is blocked.
  assert(ZResurrection::is_blocked(), "Invalid phase");
  const oop o = *p;
  barrier<is_good_or_null_fast_path, keep_alive_barrier_on_phantom_oop_slow_path>(p, o);
}

inline void ZBarrier::keep_alive_barrier_on_phantom_root_oop_field(oop* p) {
  // This operation is only valid when resurrection is blocked.
  assert(ZResurrection::is_blocked(), "Invalid phase");
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, keep_alive_barrier_on_phantom_oop_slow_path>(p, o);
}

inline void ZBarrier::keep_alive_barrier_on_oop(oop o) {
  if (during_mark()) {
    barrier<is_null_fast_path, mark_barrier_on_oop_slow_path>(NULL, o);
  }
}

//
// Mark barrier
//
inline void ZBarrier::mark_barrier_on_oop_field(volatile oop* p, bool finalizable) {
  // The fast path only checks for null since the GC worker
  // threads doing marking wants to mark through good oops.
  const oop o = *p;

  if (finalizable) {
    barrier<is_null_fast_path, mark_barrier_on_finalizable_oop_slow_path>(p, o);
  } else {
    barrier<is_null_fast_path, mark_barrier_on_oop_slow_path>(p, o);
  }
}

inline void ZBarrier::mark_barrier_on_oop_array(volatile oop* p, size_t length, bool finalizable) {
  for (volatile const oop* const end = p + length; p < end; p++) {
    mark_barrier_on_oop_field(p, finalizable);
  }
}

inline void ZBarrier::mark_barrier_on_root_oop_field(oop* p) {
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, mark_barrier_on_root_oop_slow_path>(p, o);
}

inline void ZBarrier::mark_barrier_on_invisible_root_oop_field(oop* p) {
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, mark_barrier_on_invisible_root_oop_slow_path>(p, o);
}

//
// Relocate barrier
//
inline void ZBarrier::relocate_barrier_on_root_oop_field(oop* p) {
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, relocate_barrier_on_root_oop_slow_path>(p, o);
}

#endif // SHARE_GC_Z_ZBARRIER_INLINE_HPP
