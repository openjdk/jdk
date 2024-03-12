/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XBARRIER_INLINE_HPP
#define SHARE_GC_X_XBARRIER_INLINE_HPP

#include "gc/x/xBarrier.hpp"

#include "code/codeCache.hpp"
#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xOop.inline.hpp"
#include "gc/x/xResurrection.inline.hpp"
#include "oops/oop.hpp"
#include "runtime/atomic.hpp"
#include "runtime/continuation.hpp"

// A self heal must always "upgrade" the address metadata bits in
// accordance with the metadata bits state machine, which has the
// valid state transitions as described below (where N is the GC
// cycle).
//
// Note the subtleness of overlapping GC cycles. Specifically that
// oops are colored Remapped(N) starting at relocation N and ending
// at marking N + 1.
//
//              +--- Mark Start
//              | +--- Mark End
//              | | +--- Relocate Start
//              | | | +--- Relocate End
//              | | | |
// Marked       |---N---|--N+1--|--N+2--|----
// Finalizable  |---N---|--N+1--|--N+2--|----
// Remapped     ----|---N---|--N+1--|--N+2--|
//
// VALID STATE TRANSITIONS
//
//   Marked(N)           -> Remapped(N)
//                       -> Marked(N + 1)
//                       -> Finalizable(N + 1)
//
//   Finalizable(N)      -> Marked(N)
//                       -> Remapped(N)
//                       -> Marked(N + 1)
//                       -> Finalizable(N + 1)
//
//   Remapped(N)         -> Marked(N + 1)
//                       -> Finalizable(N + 1)
//
// PHASE VIEW
//
// XPhaseMark
//   Load & Mark
//     Marked(N)         <- Marked(N - 1)
//                       <- Finalizable(N - 1)
//                       <- Remapped(N - 1)
//                       <- Finalizable(N)
//
//   Mark(Finalizable)
//     Finalizable(N)    <- Marked(N - 1)
//                       <- Finalizable(N - 1)
//                       <- Remapped(N - 1)
//
//   Load(AS_NO_KEEPALIVE)
//     Remapped(N - 1)   <- Marked(N - 1)
//                       <- Finalizable(N - 1)
//
// XPhaseMarkCompleted (Resurrection blocked)
//   Load & Load(ON_WEAK/PHANTOM_OOP_REF | AS_NO_KEEPALIVE) & KeepAlive
//     Marked(N)         <- Marked(N - 1)
//                       <- Finalizable(N - 1)
//                       <- Remapped(N - 1)
//                       <- Finalizable(N)
//
//   Load(ON_STRONG_OOP_REF | AS_NO_KEEPALIVE)
//     Remapped(N - 1)   <- Marked(N - 1)
//                       <- Finalizable(N - 1)
//
// XPhaseMarkCompleted (Resurrection unblocked)
//   Load
//     Marked(N)         <- Finalizable(N)
//
// XPhaseRelocate
//   Load & Load(AS_NO_KEEPALIVE)
//     Remapped(N)       <- Marked(N)
//                       <- Finalizable(N)

template <XBarrierFastPath fast_path>
inline void XBarrier::self_heal(volatile oop* p, uintptr_t addr, uintptr_t heal_addr) {
  if (heal_addr == 0) {
    // Never heal with null since it interacts badly with reference processing.
    // A mutator clearing an oop would be similar to calling Reference.clear(),
    // which would make the reference non-discoverable or silently dropped
    // by the reference processor.
    return;
  }

  assert(!fast_path(addr), "Invalid self heal");
  assert(fast_path(heal_addr), "Invalid self heal");

  for (;;) {
    // Heal
    const uintptr_t prev_addr = Atomic::cmpxchg((volatile uintptr_t*)p, addr, heal_addr, memory_order_relaxed);
    if (prev_addr == addr) {
      // Success
      return;
    }

    if (fast_path(prev_addr)) {
      // Must not self heal
      return;
    }

    // The oop location was healed by another barrier, but still needs upgrading.
    // Re-apply healing to make sure the oop is not left with weaker (remapped or
    // finalizable) metadata bits than what this barrier tried to apply.
    assert(XAddress::offset(prev_addr) == XAddress::offset(heal_addr), "Invalid offset");
    addr = prev_addr;
  }
}

template <XBarrierFastPath fast_path, XBarrierSlowPath slow_path>
inline oop XBarrier::barrier(volatile oop* p, oop o) {
  const uintptr_t addr = XOop::to_address(o);

  // Fast path
  if (fast_path(addr)) {
    return XOop::from_address(addr);
  }

  // Slow path
  const uintptr_t good_addr = slow_path(addr);

  if (p != nullptr) {
    self_heal<fast_path>(p, addr, good_addr);
  }

  return XOop::from_address(good_addr);
}

template <XBarrierFastPath fast_path, XBarrierSlowPath slow_path>
inline oop XBarrier::weak_barrier(volatile oop* p, oop o) {
  const uintptr_t addr = XOop::to_address(o);

  // Fast path
  if (fast_path(addr)) {
    // Return the good address instead of the weak good address
    // to ensure that the currently active heap view is used.
    return XOop::from_address(XAddress::good_or_null(addr));
  }

  // Slow path
  const uintptr_t good_addr = slow_path(addr);

  if (p != nullptr) {
    // The slow path returns a good/marked address or null, but we never mark
    // oops in a weak load barrier so we always heal with the remapped address.
    self_heal<fast_path>(p, addr, XAddress::remapped_or_null(good_addr));
  }

  return XOop::from_address(good_addr);
}

template <XBarrierFastPath fast_path, XBarrierSlowPath slow_path>
inline void XBarrier::root_barrier(oop* p, oop o) {
  const uintptr_t addr = XOop::to_address(o);

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
  *p = XOop::from_address(good_addr);
}

inline bool XBarrier::is_good_or_null_fast_path(uintptr_t addr) {
  return XAddress::is_good_or_null(addr);
}

inline bool XBarrier::is_weak_good_or_null_fast_path(uintptr_t addr) {
  return XAddress::is_weak_good_or_null(addr);
}

inline bool XBarrier::is_marked_or_null_fast_path(uintptr_t addr) {
  return XAddress::is_marked_or_null(addr);
}

inline bool XBarrier::during_mark() {
  return XGlobalPhase == XPhaseMark;
}

inline bool XBarrier::during_relocate() {
  return XGlobalPhase == XPhaseRelocate;
}

//
// Load barrier
//
inline oop XBarrier::load_barrier_on_oop(oop o) {
  return load_barrier_on_oop_field_preloaded((oop*)nullptr, o);
}

inline oop XBarrier::load_barrier_on_oop_field(volatile oop* p) {
  const oop o = Atomic::load(p);
  return load_barrier_on_oop_field_preloaded(p, o);
}

inline oop XBarrier::load_barrier_on_oop_field_preloaded(volatile oop* p, oop o) {
  return barrier<is_good_or_null_fast_path, load_barrier_on_oop_slow_path>(p, o);
}

inline void XBarrier::load_barrier_on_oop_array(volatile oop* p, size_t length) {
  for (volatile const oop* const end = p + length; p < end; p++) {
    load_barrier_on_oop_field(p);
  }
}

inline oop XBarrier::load_barrier_on_weak_oop_field_preloaded(volatile oop* p, oop o) {
  verify_on_weak(p);

  if (XResurrection::is_blocked()) {
    return barrier<is_good_or_null_fast_path, weak_load_barrier_on_weak_oop_slow_path>(p, o);
  }

  return load_barrier_on_oop_field_preloaded(p, o);
}

inline oop XBarrier::load_barrier_on_phantom_oop_field_preloaded(volatile oop* p, oop o) {
  if (XResurrection::is_blocked()) {
    return barrier<is_good_or_null_fast_path, weak_load_barrier_on_phantom_oop_slow_path>(p, o);
  }

  return load_barrier_on_oop_field_preloaded(p, o);
}

inline void XBarrier::load_barrier_on_root_oop_field(oop* p) {
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, load_barrier_on_oop_slow_path>(p, o);
}

inline void XBarrier::load_barrier_on_invisible_root_oop_field(oop* p) {
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, load_barrier_on_invisible_root_oop_slow_path>(p, o);
}

//
// Weak load barrier
//
inline oop XBarrier::weak_load_barrier_on_oop_field(volatile oop* p) {
  assert(!XResurrection::is_blocked(), "Should not be called during resurrection blocked phase");
  const oop o = Atomic::load(p);
  return weak_load_barrier_on_oop_field_preloaded(p, o);
}

inline oop XBarrier::weak_load_barrier_on_oop_field_preloaded(volatile oop* p, oop o) {
  return weak_barrier<is_weak_good_or_null_fast_path, weak_load_barrier_on_oop_slow_path>(p, o);
}

inline oop XBarrier::weak_load_barrier_on_weak_oop(oop o) {
  return weak_load_barrier_on_weak_oop_field_preloaded((oop*)nullptr, o);
}

inline oop XBarrier::weak_load_barrier_on_weak_oop_field_preloaded(volatile oop* p, oop o) {
  verify_on_weak(p);

  if (XResurrection::is_blocked()) {
    return barrier<is_good_or_null_fast_path, weak_load_barrier_on_weak_oop_slow_path>(p, o);
  }

  return weak_load_barrier_on_oop_field_preloaded(p, o);
}

inline oop XBarrier::weak_load_barrier_on_phantom_oop(oop o) {
  return weak_load_barrier_on_phantom_oop_field_preloaded((oop*)nullptr, o);
}

inline oop XBarrier::weak_load_barrier_on_phantom_oop_field_preloaded(volatile oop* p, oop o) {
  if (XResurrection::is_blocked()) {
    return barrier<is_good_or_null_fast_path, weak_load_barrier_on_phantom_oop_slow_path>(p, o);
  }

  return weak_load_barrier_on_oop_field_preloaded(p, o);
}

//
// Is alive barrier
//
inline bool XBarrier::is_alive_barrier_on_weak_oop(oop o) {
  // Check if oop is logically non-null. This operation
  // is only valid when resurrection is blocked.
  assert(XResurrection::is_blocked(), "Invalid phase");
  return weak_load_barrier_on_weak_oop(o) != nullptr;
}

inline bool XBarrier::is_alive_barrier_on_phantom_oop(oop o) {
  // Check if oop is logically non-null. This operation
  // is only valid when resurrection is blocked.
  assert(XResurrection::is_blocked(), "Invalid phase");
  return weak_load_barrier_on_phantom_oop(o) != nullptr;
}

//
// Keep alive barrier
//
inline void XBarrier::keep_alive_barrier_on_weak_oop_field(volatile oop* p) {
  assert(XResurrection::is_blocked(), "This operation is only valid when resurrection is blocked");
  const oop o = Atomic::load(p);
  barrier<is_good_or_null_fast_path, keep_alive_barrier_on_weak_oop_slow_path>(p, o);
}

inline void XBarrier::keep_alive_barrier_on_phantom_oop_field(volatile oop* p) {
  assert(XResurrection::is_blocked(), "This operation is only valid when resurrection is blocked");
  const oop o = Atomic::load(p);
  barrier<is_good_or_null_fast_path, keep_alive_barrier_on_phantom_oop_slow_path>(p, o);
}

inline void XBarrier::keep_alive_barrier_on_phantom_root_oop_field(oop* p) {
  // The keep alive operation is only valid when resurrection is blocked.
  //
  // Except with Loom, where we intentionally trigger arms nmethods after
  // unlinking, to get a sense of what nmethods are alive. This will trigger
  // the keep alive barriers, but the oops are healed and the slow-paths
  // will not trigger. We have stronger checks in the slow-paths.
  assert(XResurrection::is_blocked() || (CodeCache::contains((void*)p)),
         "This operation is only valid when resurrection is blocked");
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, keep_alive_barrier_on_phantom_oop_slow_path>(p, o);
}

inline void XBarrier::keep_alive_barrier_on_oop(oop o) {
  const uintptr_t addr = XOop::to_address(o);
  assert(XAddress::is_good(addr), "Invalid address");

  if (during_mark()) {
    keep_alive_barrier_on_oop_slow_path(addr);
  }
}

//
// Mark barrier
//
inline void XBarrier::mark_barrier_on_oop_field(volatile oop* p, bool finalizable) {
  const oop o = Atomic::load(p);

  if (finalizable) {
    barrier<is_marked_or_null_fast_path, mark_barrier_on_finalizable_oop_slow_path>(p, o);
  } else {
    const uintptr_t addr = XOop::to_address(o);
    if (XAddress::is_good(addr)) {
      // Mark through good oop
      mark_barrier_on_oop_slow_path(addr);
    } else {
      // Mark through bad oop
      barrier<is_good_or_null_fast_path, mark_barrier_on_oop_slow_path>(p, o);
    }
  }
}

inline void XBarrier::mark_barrier_on_oop_array(volatile oop* p, size_t length, bool finalizable) {
  for (volatile const oop* const end = p + length; p < end; p++) {
    mark_barrier_on_oop_field(p, finalizable);
  }
}

#endif // SHARE_GC_X_XBARRIER_INLINE_HPP
