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

#ifndef SHARE_GC_Z_ZBARRIER_INLINE_HPP
#define SHARE_GC_Z_ZBARRIER_INLINE_HPP

#include "gc/z/zBarrier.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zResurrection.inline.hpp"
#include "gc/z/zThread.inline.hpp"
#include "oops/oop.hpp"
#include "runtime/atomic.hpp"

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
// ZPhase::Mark
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
// ZPhase::MarkComplete (Resurrection blocked)
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
// ZPhase::MarkComplete (Resurrection unblocked)
//   Load
//     Marked(N)         <- Finalizable(N)
//
// ZPhase::Relocate
//   Load & Load(AS_NO_KEEPALIVE)
//     Remapped(N)       <- Marked(N)
//                       <- Finalizable(N)

inline void ZBarrier::assert_transition_monotonicity(zpointer old_ptr, zpointer new_ptr) {
  const bool old_is_load_good = ZPointer::is_load_good(old_ptr);
  const bool old_is_mark_good = ZPointer::is_mark_good(old_ptr);
  const bool old_is_store_good = ZPointer::is_store_good(old_ptr);

  const bool new_is_load_good = ZPointer::is_load_good(new_ptr);
  const bool new_is_mark_good = ZPointer::is_mark_good(new_ptr);
  const bool new_is_store_good = ZPointer::is_store_good(new_ptr);

  assert(!old_is_load_good || new_is_load_good, "non-monotonic load good transition");
  assert(!old_is_mark_good || new_is_mark_good, "non-monotonic mark good transition");
  assert(!old_is_store_good || new_is_store_good, "non-monotonic store good transition");

  if (is_null_any(new_ptr)) {
    // Null is good enough at this point
    return;
  }

  const bool old_is_marked_minor = ZPointer::is_marked_minor(old_ptr);
  const bool old_is_marked_major = ZPointer::is_marked_major(old_ptr);
  const bool old_is_marked_finalizable = ZPointer::is_marked_finalizable(old_ptr);

  const bool new_is_marked_minor = ZPointer::is_marked_minor(new_ptr);
  const bool new_is_marked_major = ZPointer::is_marked_major(new_ptr);
  const bool new_is_marked_finalizable = ZPointer::is_marked_finalizable(new_ptr);

  assert(!old_is_marked_minor || new_is_marked_minor, "non-monotonic marked minor transition");
  assert(!old_is_marked_major || new_is_marked_major, "non-monotonic marked major transition");
  assert(!old_is_marked_finalizable || new_is_marked_finalizable || new_is_marked_major, "non-monotonic marked final transition");
}

inline void ZBarrier::self_heal(ZBarrierFastPath fast_path, volatile zpointer* p, zpointer ptr, zpointer heal_ptr, bool allow_null) {
  if (!allow_null && is_null_assert_load_good(heal_ptr) && !is_null_any(ptr)) {
    // Never heal with null since it interacts badly with reference processing.
    // A mutator clearing an oop would be similar to calling Reference.clear(),
    // which would make the reference non-discoverable or silently dropped
    // by the reference processor.
    return;
  }

  assert_is_valid(ptr);
  assert_is_valid(heal_ptr);
  assert(!fast_path(ptr), "Invalid self heal");
  assert(fast_path(heal_ptr), "Invalid self heal");

  assert(ZPointer::is_remapped(heal_ptr), "invariant");

  for (;;) {
    if (ptr == zpointer::null) {
      assert(!ZHeap::heap()->is_in(uintptr_t(p)) || !ZHeap::heap()->is_old(p), "No raw null in old");
    }

    assert_transition_monotonicity(ptr, heal_ptr);

    // Heal
    const zpointer prev_ptr = Atomic::cmpxchg(p, ptr, heal_ptr);
    if (prev_ptr == ptr) {
      // Success
      return;
    }

    if (fast_path(prev_ptr)) {
      // Must not self heal
      return;
    }

    // The oop location was healed by another barrier, but still needs upgrading.
    // Re-apply healing to make sure the oop is not left with weaker (remapped or
    // finalizable) metadata bits than what this barrier tried to apply.
    ptr = prev_ptr;
  }
}

inline zaddress ZBarrier::make_load_good(zpointer o) {
  if (is_null_any(o)) {
    return zaddress::null;
  }

  if (ZPointer::is_load_good_or_null(o)) {
    return ZPointer::uncolor(o);
  }

  return relocate_or_remap(ZPointer::uncolor_unsafe(o), ZHeap::heap()->remap_collector(o));
}

inline zaddress ZBarrier::make_load_good_no_relocate(zpointer o) {
  if (is_null_any(o)) {
    return zaddress::null;
  }

  if (ZPointer::is_load_good_or_null(o)) {
    return ZPointer::uncolor(o);
  }

  return remap(ZPointer::uncolor_unsafe(o), ZHeap::heap()->remap_collector(o));
}


#define z_assert_is_barrier_safe()                                                                                                  \
  assert(!Thread::current()->is_ConcurrentGC_thread() ||                           /* Need extra checks for ConcurrentGCThreads */  \
         Thread::current()->is_suspendible_thread() ||                             /* Thread prevents safepoints */                 \
         (ZThread::is_worker() && ZThread::coordinator_is_suspendible_thread()) || /* Coordinator thread prevents safepoints */     \
         SafepointSynchronize::is_at_safepoint(),                                  /* Is at safepoint */                            \
         "Shouldn't perform load barrier");

template <typename ZBarrierSlowPath>
inline zaddress ZBarrier::barrier(ZBarrierFastPath fast_path, ZBarrierSlowPath slow_path, ZBarrierColor color, volatile zpointer* p, zpointer o, bool allow_null) {
  z_assert_is_barrier_safe();

  // Fast path
  if (fast_path(o)) {
    return ZPointer::uncolor(o);
  }

  // Make load good
  const zaddress load_good_addr = make_load_good(o);

  // Slow path
  const zaddress good_addr = slow_path(load_good_addr);

  // Self heal
  if (p != NULL) {
    // Color
    const zpointer good_ptr = color(good_addr, o);

    assert(!is_null(good_ptr), "Always block raw null");

    self_heal(fast_path, p, o, good_ptr, allow_null);
  }

  return good_addr;
}

inline void ZBarrier::remap_minor_relocated(volatile zpointer* p, zpointer o) {
  assert(ZPointer::is_major_load_good(o), "Should be load good");
  assert(!ZPointer::is_minor_load_good(o), "Should be load good");

  // Make load good
  const zaddress load_good_addr = make_load_good_no_relocate(o);

  // Color
  const zpointer good_ptr = ZAddress::load_good(load_good_addr,  o);

  assert(!is_null(good_ptr), "Always block raw null");

  self_heal(is_load_good_fast_path, p, o, good_ptr, false /* allow_null */);
}

inline zpointer ZBarrier::load_atomic(volatile zpointer* p) {
  zpointer ptr = Atomic::load(p);
  assert_is_valid(ptr);
  return ptr;
}

//
// Fast paths
//

inline bool ZBarrier::is_load_good_fast_path(zpointer ptr) {
  return ZPointer::is_load_good(ptr);
}

inline bool ZBarrier::is_mark_good_fast_path(zpointer ptr) {
  return ZPointer::is_mark_good(ptr);
}

inline bool ZBarrier::is_store_good_fast_path(zpointer ptr) {
  return ZPointer::is_store_good(ptr);
}

inline bool ZBarrier::is_mark_minor_good_fast_path(zpointer ptr) {
  return ZPointer::is_load_good(ptr) && ZPointer::is_marked_minor(ptr);
}

inline bool ZBarrier::is_finalizable_good_fast_path(zpointer ptr) {
  return ZPointer::is_load_good(ptr) && ZPointer::is_marked_any_major(ptr);
}

//
// Color functions
//

inline zpointer color_load_good(zaddress new_addr, zpointer old_ptr) {
  return ZAddress::load_good(new_addr, old_ptr);
}

inline zpointer color_finalizable_good(zaddress new_addr, zpointer old_ptr) {
  if (ZPointer::is_marked_major(old_ptr)) {
    // Don't down-grade pointers
    return ZAddress::mark_major_good(new_addr, old_ptr);
  } else {
    return ZAddress::finalizable_good(new_addr, old_ptr);
  }
}

inline zpointer color_mark_good(zaddress new_addr, zpointer old_ptr) {
  return ZAddress::mark_good(new_addr, old_ptr);
}

inline zpointer color_mark_minor_good(zaddress new_addr, zpointer old_ptr) {
  return ZAddress::mark_minor_good(new_addr, old_ptr);
}

inline zpointer color_store_good(zaddress new_addr, zpointer old_ptr) {
  return ZAddress::store_good(new_addr);
}

//
// Load barrier
//

inline zaddress ZBarrier::load_barrier_on_oop_field(volatile zpointer* p) {
  const zpointer o = load_atomic(p);
  return load_barrier_on_oop_field_preloaded(p, o);
}

inline zaddress ZBarrier::load_barrier_on_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  auto slow_path = [](zaddress addr) -> zaddress {
    return addr;
  };

  return barrier(is_load_good_fast_path, slow_path, color_load_good, p, o);
}

inline zaddress ZBarrier::keep_alive_load_barrier_on_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  return barrier(is_mark_good_fast_path, keep_alive_slow_path, color_mark_good, p, o);
}

//
// Load barrier on non-strong oop refs
//

inline zaddress ZBarrier::load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  verify_on_weak(p);

  if (ZResurrection::is_blocked()) {
    return blocking_keep_alive_load_barrier_on_weak_oop_field_preloaded(p, o);
  }

  return keep_alive_load_barrier_on_oop_field_preloaded(p, o);
}

inline zaddress ZBarrier::load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  if (ZResurrection::is_blocked()) {
    return blocking_keep_alive_load_barrier_on_phantom_oop_field_preloaded(p, o);
  }

  return keep_alive_load_barrier_on_oop_field_preloaded(p, o);
}

inline zaddress ZBarrier::no_keep_alive_load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  verify_on_weak(p);

  if (ZResurrection::is_blocked()) {
    return blocking_load_barrier_on_weak_oop_field_preloaded(p, o);
  }

  // Normal load barrier doesn't keep the object alive
  return load_barrier_on_oop_field_preloaded(p, o);
}

inline zaddress ZBarrier::no_keep_alive_load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  if (ZResurrection::is_blocked()) {
    return blocking_load_barrier_on_phantom_oop_field_preloaded(p, o);
  }

  // Normal load barrier doesn't keep the object alive
  return load_barrier_on_oop_field_preloaded(p, o);
}

inline zaddress ZBarrier::blocking_keep_alive_load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  return barrier(is_mark_good_fast_path, blocking_keep_alive_on_weak_slow_path, color_mark_good, p, o);
}

inline zaddress ZBarrier::blocking_keep_alive_load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  return barrier(is_mark_good_fast_path, blocking_keep_alive_on_phantom_slow_path, color_mark_good, p, o);
}

inline zaddress ZBarrier::blocking_load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  return barrier(is_mark_good_fast_path, blocking_load_barrier_on_weak_slow_path, color_mark_good, p, o);
}

inline zaddress ZBarrier::blocking_load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  return barrier(is_mark_good_fast_path, blocking_load_barrier_on_phantom_slow_path, color_mark_good, p, o);
}

//
// Clean barrier
//

inline bool ZBarrier::clean_barrier_on_weak_oop_field(volatile zpointer* p) {
  assert(ZResurrection::is_blocked(), "Invalid phase");
  const zpointer o = load_atomic(p);
  return is_null(barrier(is_mark_good_fast_path, blocking_load_barrier_on_weak_slow_path, color_mark_good, p, o, true /* allow_null */));
}

inline bool ZBarrier::clean_barrier_on_phantom_oop_field(volatile zpointer* p) {
  assert(ZResurrection::is_blocked(), "Invalid phase");
  const zpointer o = load_atomic(p);
  return is_null(barrier(is_mark_good_fast_path, blocking_load_barrier_on_phantom_slow_path, color_mark_good, p, o, true /* allow_null */));
}

inline bool ZBarrier::clean_barrier_on_final_oop_field(volatile zpointer* p) {
  assert(ZResurrection::is_blocked(), "Invalid phase");

  // The referent in a FinalReference should never be cleared by the GC. Instead
  // it should just be healed (as if it was a phantom oop) and this function should
  // return true if the object pointer to by the referent is not strongly reachable.
  const zpointer o = load_atomic(p);

  const zaddress addr = barrier(is_mark_good_fast_path, blocking_load_barrier_on_phantom_slow_path, color_mark_good, p, o);
  assert(!is_null(addr), "Should be finalizable marked");

  return is_null(blocking_load_barrier_on_weak_slow_path(addr));
}

//
// Mark barrier
//
inline void ZBarrier::mark_barrier_on_oop_field(volatile zpointer* p, bool finalizable) {
  const zpointer o = load_atomic(p);

  if (finalizable) {
    // During marking, we mark through already marked oops to avoid having
    // some large part of the object graph hidden behind a pushed, but not
    // yet flushed, entry on a mutator mark stack. Always marking through
    // allows the GC workers to proceed through the object graph even if a
    // mutator touched an oop first, which in turn will reduce the risk of
    // having to flush mark stacks multiple times to terminate marking.
    //
    // However, when doing finalizable marking we don't always want to mark
    // through. First, marking through an already strongly marked oop would
    // be wasteful, since we will then proceed to do finalizable marking on
    // an object which is, or will be, marked strongly. Second, marking
    // through an already finalizable marked oop would also be wasteful,
    // since such oops can never end up on a mutator mark stack and can
    // therefore not hide some part of the object graph from GC workers.

    // Make the oop finalizable marked/good, instead of normal marked/good.
    // This is needed because an object might first becomes finalizable
    // marked by the GC, and then loaded by a mutator thread. In this case,
    // the mutator thread must be able to tell that the object needs to be
    // strongly marked. The finalizable bit in the oop exists to make sure
    // that a load of a finalizable marked oop will fall into the barrier
    // slow path so that we can mark the object as strongly reachable.

    // Note: that this does not color the pointer finalizable marked if it
    // is already colored marked major good.
    barrier(is_finalizable_good_fast_path, mark_finalizable_slow_path, color_finalizable_good, p, o);
  } else {
    barrier(is_mark_good_fast_path, mark_slow_path, color_mark_good, p, o);
  }
}

inline void ZBarrier::mark_barrier_on_young_oop_field(volatile zpointer* p) {
  const zpointer o = load_atomic(p);
  barrier(is_store_good_fast_path, mark_slow_path, color_store_good, p, o);
}

//
// Mark barrier
//
inline zaddress ZBarrier::mark_minor_good_barrier_on_oop_field(volatile zpointer* p) {
  zpointer o = load_atomic(p);
  return barrier(is_mark_minor_good_fast_path, mark_minor_slow_path, color_mark_minor_good, p, o);
}

//
// Store barrier
//

inline void ZBarrier::store_barrier_on_heap_oop_field(volatile zpointer* p, bool heal) {
  const zpointer prev = load_atomic(p);

  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::heap_store_slow_path(p, addr, prev, heal);
  };

  barrier(is_store_good_fast_path, slow_path, color_store_good, (heal ? p : NULL), prev);
}

inline void ZBarrier::store_barrier_on_native_oop_field(volatile zpointer* p, bool heal) {
  zpointer prev = load_atomic(p);

  barrier(is_store_good_fast_path, native_store_slow_path, color_store_good, (heal ? p : NULL), prev);
}

#endif // SHARE_GC_Z_ZBARRIER_INLINE_HPP
