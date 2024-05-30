/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zResurrection.inline.hpp"
#include "gc/z/zVerify.hpp"
#include "oops/oop.hpp"
#include "runtime/atomic.hpp"

// A self heal must always "upgrade" the address metadata bits in
// accordance with the metadata bits state machine. The following
// assert verifies the monotonicity of the transitions.

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

  const bool old_is_marked_young = ZPointer::is_marked_young(old_ptr);
  const bool old_is_marked_old = ZPointer::is_marked_old(old_ptr);
  const bool old_is_marked_finalizable = ZPointer::is_marked_finalizable(old_ptr);

  const bool new_is_marked_young = ZPointer::is_marked_young(new_ptr);
  const bool new_is_marked_old = ZPointer::is_marked_old(new_ptr);
  const bool new_is_marked_finalizable = ZPointer::is_marked_finalizable(new_ptr);

  assert(!old_is_marked_young || new_is_marked_young, "non-monotonic marked young transition");
  assert(!old_is_marked_old || new_is_marked_old, "non-monotonic marked old transition");
  assert(!old_is_marked_finalizable || new_is_marked_finalizable || new_is_marked_old, "non-monotonic marked final transition");
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
      assert(!ZVerifyOops || !ZHeap::heap()->is_in(uintptr_t(p)) || !ZHeap::heap()->is_old(p), "No raw null in old");
    }

    assert_transition_monotonicity(ptr, heal_ptr);

    // Heal
    const zpointer prev_ptr = Atomic::cmpxchg(p, ptr, heal_ptr, memory_order_relaxed);
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

inline ZGeneration* ZBarrier::remap_generation(zpointer ptr) {
  assert(!ZPointer::is_load_good(ptr), "no need to remap load-good pointer");

  if (ZPointer::is_old_load_good(ptr)) {
    return ZGeneration::young();
  }

  if (ZPointer::is_young_load_good(ptr)) {
    return ZGeneration::old();
  }

  // Double remap bad - the pointer is neither old load good nor
  // young load good. First the code ...

  const uintptr_t remembered_bits = untype(ptr) & ZPointerRememberedMask;
  const bool old_to_old_ptr = remembered_bits == ZPointerRememberedMask;

  if (old_to_old_ptr) {
    return ZGeneration::old();
  }

  const zaddress_unsafe addr = ZPointer::uncolor_unsafe(ptr);
  if (ZGeneration::young()->forwarding(addr) != nullptr) {
    assert(ZGeneration::old()->forwarding(addr) == nullptr, "Mutually exclusive");
    return ZGeneration::young();
  } else {
    return ZGeneration::old();
  }

  // ... then the explanation. Time to put your seat belt on.

  // In this context we only have access to the ptr (colored oop), but we
  // don't know if this refers to a stale young gen or old gen object.
  // However, by being careful with when we run young and old collections,
  // and by explicitly remapping roots we can figure this out by looking
  // at the metadata bits in the pointer.

  // *Roots (including remset)*:
  //
  // will never have double remap bit errors,
  // and will never enter this path. The reason is that there's always a
  // phase that remaps all roots between all relocation phases:
  //
  // 1) Young marking remaps the roots, before the young relocation runs
  //
  // 2) The old roots_remap phase blocks out young collections and runs just
  //    before old relocation starts

  // *Heap object fields*:
  //
  // could have double remap bit errors, and may enter this path. We are using
  // knowledge about how *remember* bits are set, to narrow down the
  // possibilities.

  // Short summary:
  //
  // If both remember bits are set, when we have a double
  // remap bit error, then we know that we are dealing with
  // an old-to-old pointer.
  //
  // Otherwise, we are dealing with a young-to-any pointer,
  // and the address that contained the pointed-to object, is
  // guaranteed to have only been used by either the young gen
  // or the old gen.

  // Longer explanation:

  // Double remap bad pointers in young gen:
  //
  // After young relocation, the young gen objects were promoted to old gen,
  // and we keep track of those old-to-young pointers via the remset
  // (described above in the roots section).
  //
  // However, when young marking started, the current set of young gen objects
  // are snapshotted, and subsequent allocations end up in the next young
  // collection. Between young mark start, and young relocate start, stores
  // can happen to either the "young allocating" objects, or objects that
  // are about to become survivors. For both survivors and young-allocating
  // objects, it is true that their zpointers will be store good when
  // young marking finishes, and can not get demoted. These pointers will become
  // young remap bad after young relocate start. We don't maintain a remset
  // for the young allocating objects, so we don't have the same guarantee as
  // we have for roots (including remset). Pointers in these objects are
  // therefore therefore susceptible to become double remap bad.
  //
  // The scenario that can happen is:
  //   - Store in young allocating or future survivor happens between young mark
  //     start and young relocate start
  //   - Young relocate start makes this pointer young remap bad
  //   - It is NOT fixed in roots_remap (it is not part of the remset or roots)
  //   - Old relocate start makes this pointer also old remap bad

  // Double remap bad pointers in old gen:
  //
  // When an object is promoted, all oop*s are added to the remset. (Could
  // have either double or single remember bits at this point)
  //
  // As long as we have a remset entry for the oop*, we ensure that the pointer
  // is not double remap bad. See the roots section.
  //
  // However, at some point the GC notices that the pointer points to an old
  // object, and that there's no need for a remset entry. Because of that,
  // the young collection will not visit the pointer, and the pointer can
  // become double remap bad.
  //
  // The scenario that can happen is:
  //   - Old marking visits the object
  //   - Old relocation starts and then young relocation starts
  //      or
  //   - Young relocation starts and then old relocation starts

  // About double *remember* bits:
  //
  // Whenever we:
  // - perform a store barrier, we heal with one remember bit.
  // - mark objects in young gen, we heal with one remember bit.
  // - perform a non-store barrier outside of young gen, we heal with
  //   double remember bits.
  // - "remset forget" a pointer in an old object, we heal with double
  //   remember bits.
  //
  // Double remember bits ensures that *every* store that encounters it takes
  // a slow path.
  //
  // If we encounter a pointer that is both double remap bad *and* has double
  // remember bits, we know that it can't be young and it has to be old!
  //
  // Pointers in young objects:
  //
  // The only double remap bad young pointers are inside "young allocating"
  // objects and survivors, as described above. When such a pointer was written
  // into the young allocating memory, or marked in young gen, the pointer was
  // remap good and the store/young mark barrier healed with a single remember bit.
  // No other barrier could replace that bit, because store good is the greatest
  // barrier, and all other barriers will take the fast-path. This is true until
  // the young relocation starts.
  //
  // After the young relocation has started, the pointer became young remap
  // bad, and maybe we even started an old relocation, and the pointer became
  // double remap bad. When the next load barrier triggers, it will self heal
  // with double remember bits, but *importantly* it will at the same time
  // heal with good remap bits.
  //
  // So, if we have entered this "double remap bad" path, and the pointer was
  // located in young gen, then it was young allocating or a survivor, and it
  // must only have one remember bit set!
  //
  // Pointers in old objects:
  //
  // When pointers become forgotten, they are tagged with double remembered
  // bits. Only way to convert the pointer into having only one remembered
  // bit, is to perform a store. When that happens, the pointer becomes both
  // remap good and remembered again, and will be handled as the roots
  // described above.

  // With the above information:
  //
  // Iff we find a double remap bad pointer with *double remember bits*,
  // then we know that it is an old-to-old pointer, and we should use the
  // forwarding table of the old generation.
  //
  // Iff we find a double remap bad pointer with a *single remember bit*,
  // then we know that it is a young-to-any pointer. We still don't know
  // if the pointed-to object is young or old.

  // Figuring out if a double remap bad pointer in young pointed at
  // young or old:
  //
  // The scenario that created a double remap bad pointer in the young
  // allocating or survivor memory is that it was written during the last
  // young marking before the old relocation started. At that point, the old
  // generation collection has already taken its marking snapshot, and
  // determined what pages will be marked and therefore eligible to become
  // part of the old relocation set. If the young generation relocated/freed
  // a page (address range), and that address range was then reused for an old
  // page, it won't be part of the old snapshot and it therefore won't be
  // selected for old relocation.
  //
  // Because of this, we know that the object written into the young
  // allocating page will at most belong to one of the two relocation sets,
  // and we can therefore simply check in which table we installed
  // ZForwarding.
}

inline zaddress ZBarrier::make_load_good(zpointer o) {
  if (is_null_any(o)) {
    return zaddress::null;
  }

  if (ZPointer::is_load_good_or_null(o)) {
    return ZPointer::uncolor(o);
  }

  return relocate_or_remap(ZPointer::uncolor_unsafe(o), remap_generation(o));
}

inline zaddress ZBarrier::make_load_good_no_relocate(zpointer o) {
  if (is_null_any(o)) {
    return zaddress::null;
  }

  if (ZPointer::is_load_good_or_null(o)) {
    return ZPointer::uncolor(o);
  }

  return remap(ZPointer::uncolor_unsafe(o), remap_generation(o));
}

template <typename ZBarrierSlowPath>
inline zaddress ZBarrier::barrier(ZBarrierFastPath fast_path, ZBarrierSlowPath slow_path, ZBarrierColor color, volatile zpointer* p, zpointer o, bool allow_null) {
  z_verify_safepoints_are_blocked();

  // Fast path
  if (fast_path(o)) {
    return ZPointer::uncolor(o);
  }

  // Make load good
  const zaddress load_good_addr = make_load_good(o);

  // Slow path
  const zaddress good_addr = slow_path(load_good_addr);

  // Self heal
  if (p != nullptr) {
    // Color
    const zpointer good_ptr = color(good_addr, o);

    assert(!is_null(good_ptr), "Always block raw null");

    self_heal(fast_path, p, o, good_ptr, allow_null);
  }

  return good_addr;
}

inline void ZBarrier::remap_young_relocated(volatile zpointer* p, zpointer o) {
  assert(ZPointer::is_old_load_good(o), "Should be old load good");
  assert(!ZPointer::is_young_load_good(o), "Should not be young load good");

  // Make load good
  const zaddress load_good_addr = make_load_good_no_relocate(o);

  // Color
  const zpointer good_ptr = ZAddress::load_good(load_good_addr,  o);

  assert(!is_null(good_ptr), "Always block raw null");

  // Despite knowing good_ptr isn't null in this context, we use the
  // load_good_or_null fast path, because it is faster.
  self_heal(is_load_good_or_null_fast_path, p, o, good_ptr, false /* allow_null */);
}

inline zpointer ZBarrier::load_atomic(volatile zpointer* p) {
  const zpointer ptr = Atomic::load(p);
  assert_is_valid(ptr);
  return ptr;
}

//
// Fast paths
//

inline bool ZBarrier::is_load_good_or_null_fast_path(zpointer ptr) {
  return ZPointer::is_load_good_or_null(ptr);
}

inline bool ZBarrier::is_mark_good_fast_path(zpointer ptr) {
  return ZPointer::is_mark_good(ptr);
}

inline bool ZBarrier::is_store_good_fast_path(zpointer ptr) {
  return ZPointer::is_store_good(ptr);
}

inline bool ZBarrier::is_store_good_or_null_fast_path(zpointer ptr) {
  return ZPointer::is_store_good_or_null(ptr);
}

inline bool ZBarrier::is_store_good_or_null_any_fast_path(zpointer ptr) {
  return is_null_any(ptr) || !ZPointer::is_store_bad(ptr);
}

inline bool ZBarrier::is_mark_young_good_fast_path(zpointer ptr) {
  return ZPointer::is_load_good(ptr) && ZPointer::is_marked_young(ptr);
}

inline bool ZBarrier::is_finalizable_good_fast_path(zpointer ptr) {
  return ZPointer::is_load_good(ptr) && ZPointer::is_marked_any_old(ptr);
}

//
// Slow paths
//

inline zaddress ZBarrier::promote_slow_path(zaddress addr) {
  // No need to do anything
  return addr;
}

//
// Color functions
//

inline zpointer color_load_good(zaddress new_addr, zpointer old_ptr) {
  return ZAddress::load_good(new_addr, old_ptr);
}

inline zpointer color_finalizable_good(zaddress new_addr, zpointer old_ptr) {
  if (ZPointer::is_marked_old(old_ptr)) {
    // Don't down-grade pointers
    return ZAddress::mark_old_good(new_addr, old_ptr);
  } else {
    return ZAddress::finalizable_good(new_addr, old_ptr);
  }
}

inline zpointer color_mark_good(zaddress new_addr, zpointer old_ptr) {
  return ZAddress::mark_good(new_addr, old_ptr);
}

inline zpointer color_mark_young_good(zaddress new_addr, zpointer old_ptr) {
  return ZAddress::mark_young_good(new_addr, old_ptr);
}

inline zpointer color_remset_good(zaddress new_addr, zpointer old_ptr) {
  if (new_addr == zaddress::null || ZHeap::heap()->is_young(new_addr)) {
    return ZAddress::mark_good(new_addr, old_ptr);
  } else {
    // If remembered set scanning finds an old-to-old pointer, we won't mark it
    // and hence only really care about setting remembered bits to 11 so that
    // subsequent stores trip on the store-bad bit pattern. However, the contract
    // with the fast path check, is that the pointer should invariantly be young
    // mark good at least, so we color it as such.
    return ZAddress::mark_young_good(new_addr, old_ptr);
  }
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

  return barrier(is_load_good_or_null_fast_path, slow_path, color_load_good, p, o);
}

inline zaddress ZBarrier::keep_alive_load_barrier_on_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  assert(!ZResurrection::is_blocked(), "This operation is only valid when resurrection is not blocked");
  return barrier(is_mark_good_fast_path, keep_alive_slow_path, color_mark_good, p, o);
}

inline void ZBarrier::load_barrier_on_oop_array(volatile zpointer* p, size_t length) {
  for (volatile const zpointer* const end = p + length; p < end; p++) {
    load_barrier_on_oop_field(p);
  }
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
  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::blocking_keep_alive_on_weak_slow_path(p, addr);
  };
  return barrier(is_mark_good_fast_path, slow_path, color_mark_good, p, o);
}

inline zaddress ZBarrier::blocking_keep_alive_load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::blocking_keep_alive_on_phantom_slow_path(p, addr);
  };
  return barrier(is_mark_good_fast_path, slow_path, color_mark_good, p, o);
}

inline zaddress ZBarrier::blocking_load_barrier_on_weak_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::blocking_load_barrier_on_weak_slow_path(p, addr);
  };
  return barrier(is_mark_good_fast_path, slow_path, color_mark_good, p, o);
}

inline zaddress ZBarrier::blocking_load_barrier_on_phantom_oop_field_preloaded(volatile zpointer* p, zpointer o) {
  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::blocking_load_barrier_on_phantom_slow_path(p, addr);
  };
  return barrier(is_mark_good_fast_path, slow_path, color_mark_good, p, o);
}

//
// Clean barrier
//

inline bool ZBarrier::clean_barrier_on_weak_oop_field(volatile zpointer* p) {
  assert(ZResurrection::is_blocked(), "This operation is only valid when resurrection is blocked");
  const zpointer o = load_atomic(p);
  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::blocking_load_barrier_on_weak_slow_path(p, addr);
  };
  return is_null(barrier(is_mark_good_fast_path, slow_path, color_mark_good, p, o, true /* allow_null */));
}

inline bool ZBarrier::clean_barrier_on_phantom_oop_field(volatile zpointer* p) {
  assert(ZResurrection::is_blocked(), "This operation is only valid when resurrection is blocked");
  const zpointer o = load_atomic(p);
  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::blocking_load_barrier_on_phantom_slow_path(p, addr);
  };
  return is_null(barrier(is_mark_good_fast_path, slow_path, color_mark_good, p, o, true /* allow_null */));
}

inline bool ZBarrier::clean_barrier_on_final_oop_field(volatile zpointer* p) {
  assert(ZResurrection::is_blocked(), "Invalid phase");

  // The referent in a FinalReference should never be cleared by the GC. Instead
  // it should just be healed (as if it was a phantom oop) and this function should
  // return true if the object pointer to by the referent is not strongly reachable.
  const zpointer o = load_atomic(p);
  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::blocking_load_barrier_on_phantom_slow_path(p, addr);
  };
  const zaddress addr = barrier(is_mark_good_fast_path, slow_path, color_mark_good, p, o);
  assert(!is_null(addr), "Should be finalizable marked");

  return is_null(blocking_load_barrier_on_weak_slow_path(p, addr));
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
    // is already colored marked old good.
    barrier(is_finalizable_good_fast_path, mark_finalizable_slow_path, color_finalizable_good, p, o);
  } else {
    barrier(is_mark_good_fast_path, mark_slow_path, color_mark_good, p, o);
  }
}

inline void ZBarrier::mark_barrier_on_old_oop_field(volatile zpointer* p, bool finalizable) {
  assert(ZHeap::heap()->is_old(p), "Should be from old");
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
    // is already colored marked old good.
    barrier(is_finalizable_good_fast_path, mark_finalizable_from_old_slow_path, color_finalizable_good, p, o);
  } else {
    barrier(is_mark_good_fast_path, mark_from_old_slow_path, color_mark_good, p, o);
  }
}

inline void ZBarrier::mark_barrier_on_young_oop_field(volatile zpointer* p) {
  assert(ZHeap::heap()->is_young(p), "Should be from young");
  const zpointer o = load_atomic(p);
  barrier(is_store_good_or_null_any_fast_path, mark_from_young_slow_path, color_store_good, p, o);
}

inline void ZBarrier::promote_barrier_on_young_oop_field(volatile zpointer* p) {
  const zpointer o = load_atomic(p);
  // Objects that get promoted to the old generation, must invariantly contain
  // only store good pointers. However, the young marking code above filters
  // out null pointers, so we need to explicitly ensure even null pointers are
  // store good, before objects may get promoted (and before relocate start).
  // This barrier ensures that.
  // This could simply be ensured in the marking above, but promotion rates
  // are typically rather low, and fixing all null pointers strictly, when
  // only a few had to be store good due to promotions, is generally not favourable
  barrier(is_store_good_fast_path, promote_slow_path, color_store_good, p, o);
}

inline zaddress ZBarrier::remset_barrier_on_oop_field(volatile zpointer* p) {
  const zpointer o = load_atomic(p);
  return barrier(is_mark_young_good_fast_path, mark_young_slow_path, color_remset_good, p, o);
}

inline void ZBarrier::mark_young_good_barrier_on_oop_field(volatile zpointer* p) {
  const zpointer o = load_atomic(p);
  barrier(is_mark_young_good_fast_path, mark_young_slow_path, color_mark_young_good, p, o);
}

//
// Store barrier
//

inline void ZBarrier::store_barrier_on_heap_oop_field(volatile zpointer* p, bool heal) {
  const zpointer prev = load_atomic(p);

  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::heap_store_slow_path(p, addr, prev, heal);
  };

  if (heal) {
    barrier(is_store_good_fast_path, slow_path, color_store_good, p, prev);
  } else {
    barrier(is_store_good_or_null_fast_path, slow_path, color_store_good, nullptr, prev);
  }
}

inline void ZBarrier::store_barrier_on_native_oop_field(volatile zpointer* p, bool heal) {
  const zpointer prev = load_atomic(p);

  if (heal) {
    barrier(is_store_good_fast_path, native_store_slow_path, color_store_good, p, prev);
  } else {
    barrier(is_store_good_or_null_fast_path, native_store_slow_path, color_store_good, nullptr, prev);
  }
}

inline void ZBarrier::no_keep_alive_store_barrier_on_heap_oop_field(volatile zpointer* p) {
  const zpointer prev = load_atomic(p);

  auto slow_path = [=](zaddress addr) -> zaddress {
    return ZBarrier::no_keep_alive_heap_store_slow_path(p, addr);
  };

  barrier(is_store_good_fast_path, slow_path, color_store_good, nullptr, prev);
}

inline void ZBarrier::remember(volatile zpointer* p) {
  if (ZHeap::heap()->is_old(p)) {
    ZGeneration::young()->remember(p);
  }
}

inline void ZBarrier::mark_and_remember(volatile zpointer* p, zaddress addr) {
  if (!is_null(addr)) {
    mark<ZMark::DontResurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong>(addr);
  }
  remember(p);
}

template <bool resurrect, bool gc_thread, bool follow, bool finalizable>
inline void ZBarrier::mark(zaddress addr) {
  assert(!ZVerifyOops || oopDesc::is_oop(to_oop(addr), false), "must be oop");

  if (ZHeap::heap()->is_old(addr)) {
    ZGeneration::old()->mark_object_if_active<resurrect, gc_thread, follow, finalizable>(addr);
  } else {
    ZGeneration::young()->mark_object_if_active<resurrect, gc_thread, follow, ZMark::Strong>(addr);
  }
}

template <bool resurrect, bool gc_thread, bool follow>
inline void ZBarrier::mark_young(zaddress addr) {
  assert(ZGeneration::young()->is_phase_mark(), "Should only be called during marking");
  assert(!ZVerifyOops || oopDesc::is_oop(to_oop(addr), false), "must be oop");
  assert(ZHeap::heap()->is_young(addr), "Must be young");

  ZGeneration::young()->mark_object<resurrect, gc_thread, follow, ZMark::Strong>(addr);
}

template <bool resurrect, bool gc_thread, bool follow>
inline void ZBarrier::mark_if_young(zaddress addr) {
  if (ZHeap::heap()->is_young(addr)) {
    mark_young<resurrect, gc_thread, follow>(addr);
  }
}

#endif // SHARE_GC_Z_ZBARRIER_INLINE_HPP
