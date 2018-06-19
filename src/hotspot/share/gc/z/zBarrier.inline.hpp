/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.hpp"
#include "gc/z/zOop.inline.hpp"
#include "gc/z/zResurrection.inline.hpp"
#include "runtime/atomic.hpp"

template <ZBarrierFastPath fast_path, ZBarrierSlowPath slow_path>
inline oop ZBarrier::barrier(volatile oop* p, oop o) {
  uintptr_t addr = ZOop::to_address(o);

retry:
  // Fast path
  if (fast_path(addr)) {
    return ZOop::to_oop(addr);
  }

  // Slow path
  const uintptr_t good_addr = slow_path(addr);

  // Self heal, but only if the address was actually updated by the slow path,
  // which might not be the case, e.g. when marking through an already good oop.
  if (p != NULL && good_addr != addr) {
    const uintptr_t prev_addr = Atomic::cmpxchg(good_addr, (volatile uintptr_t*)p, addr);
    if (prev_addr != addr) {
      // Some other thread overwrote the oop. If this oop was updated by a
      // weak barrier the new oop might not be good, in which case we need
      // to re-apply this barrier.
      addr = prev_addr;
      goto retry;
    }
  }

  return ZOop::to_oop(good_addr);
}

template <ZBarrierFastPath fast_path, ZBarrierSlowPath slow_path>
inline oop ZBarrier::weak_barrier(volatile oop* p, oop o) {
  const uintptr_t addr = ZOop::to_address(o);

  // Fast path
  if (fast_path(addr)) {
    // Return the good address instead of the weak good address
    // to ensure that the currently active heap view is used.
    return ZOop::to_oop(ZAddress::good_or_null(addr));
  }

  // Slow path
  uintptr_t good_addr = slow_path(addr);

  // Self heal unless the address returned from the slow path is null,
  // in which case resurrection was blocked and we must let the reference
  // processor clear the oop. Mutators are not allowed to clear oops in
  // these cases, since that would be similar to calling Reference.clear(),
  // which would make the reference non-discoverable or silently dropped
  // by the reference processor.
  if (p != NULL && good_addr != 0) {
    // The slow path returns a good/marked address, but we never mark oops
    // in a weak load barrier so we always self heal with the remapped address.
    const uintptr_t weak_good_addr = ZAddress::remapped(good_addr);
    const uintptr_t prev_addr = Atomic::cmpxchg(weak_good_addr, (volatile uintptr_t*)p, addr);
    if (prev_addr != addr) {
      // Some other thread overwrote the oop. The new
      // oop is guaranteed to be weak good or null.
      assert(ZAddress::is_weak_good_or_null(prev_addr), "Bad weak overwrite");

      // Return the good address instead of the weak good address
      // to ensure that the currently active heap view is used.
      good_addr = ZAddress::good_or_null(prev_addr);
    }
  }

  return ZOop::to_oop(good_addr);
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
  // since we are always healing roots in a safepoint, which means we are
  // never racing with mutators modifying roots while we are healing them.
  // It's also safe in case multiple GC threads try to heal the same root,
  // since they would always heal the root in the same way and it does not
  // matter in which order it happens.
  *p = ZOop::to_oop(good_addr);
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

inline bool ZBarrier::is_resurrection_blocked(volatile oop* p, oop* o) {
  const bool is_blocked = ZResurrection::is_blocked();

  // Reload oop after checking the resurrection blocked state. This is
  // done to prevent a race where we first load an oop, which is logically
  // null but not yet cleared, then this oop is cleared by the reference
  // processor and resurrection is unblocked. At this point the mutator
  // would see the unblocked state and pass this invalid oop through the
  // normal barrier path, which would incorrectly try to mark this oop.
  if (p != NULL) {
    // First assign to reloaded_o to avoid compiler warning about
    // implicit dereference of volatile oop.
    const oop reloaded_o = *p;
    *o = reloaded_o;
  }

  return is_blocked;
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

inline oop ZBarrier::load_barrier_on_weak_oop_field_preloaded(volatile oop* p, oop o) {
  if (is_resurrection_blocked(p, &o)) {
    return weak_barrier<is_good_or_null_fast_path, weak_load_barrier_on_weak_oop_slow_path>(p, o);
  }

  return load_barrier_on_oop_field_preloaded(p, o);
}

inline oop ZBarrier::load_barrier_on_phantom_oop_field_preloaded(volatile oop* p, oop o) {
  if (is_resurrection_blocked(p, &o)) {
    return weak_barrier<is_good_or_null_fast_path, weak_load_barrier_on_phantom_oop_slow_path>(p, o);
  }

  return load_barrier_on_oop_field_preloaded(p, o);
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
  if (is_resurrection_blocked(p, &o)) {
    return weak_barrier<is_good_or_null_fast_path, weak_load_barrier_on_weak_oop_slow_path>(p, o);
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
  if (is_resurrection_blocked(p, &o)) {
    return weak_barrier<is_good_or_null_fast_path, weak_load_barrier_on_phantom_oop_slow_path>(p, o);
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

//
// Relocate barrier
//
inline void ZBarrier::relocate_barrier_on_root_oop_field(oop* p) {
  const oop o = *p;
  root_barrier<is_good_or_null_fast_path, relocate_barrier_on_root_oop_slow_path>(p, o);
}

#endif // SHARE_GC_Z_ZBARRIER_INLINE_HPP
