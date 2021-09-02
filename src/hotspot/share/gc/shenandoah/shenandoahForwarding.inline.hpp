/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP

#include "gc/shenandoah/shenandoahForwarding.hpp"

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/markWord.hpp"
#include "runtime/atomic.hpp"
#include "runtime/thread.hpp"

/*
 * Implementation note on memory ordering:
 *
 * Since concurrent GC like Shenandoah effectively publishes the forwardee copy
 * to concurrently running mutators, we need to consider the memory ordering
 * that comes with it. Most crucially, we need to ensure that all the stores to
 * the forwardee before its publication are visible to readers of the forwardee.
 * This is the GC hotpath, and thus the weakest synchronization should be used.
 *
 * Because the whole thing is the pointer-mediated publishing, the weakest way
 * to achieve this is Release-Consume ordering. But, because:
 *   a) we do not have "Consume" for in Hotspot;
 *   b) "Consume" gets promoted to "Acquire" by most current compilers
 *      (because doing otherwise requires tracking load dependencies);
 *   c) the use of "Consume" is generally discouraged in current C++;
 *
 * ...Release-Acquire ordering should be considered.
 *
 * It is beyond doubt that forwardee installations need to be "Release".
 * But doing "Acquire" on hot-path, especially on weakly-ordered architectures,
 * would significantly penalize users. The rest of the discussion is about the
 * need for "Acquire" on some paths.
 *
 * There are several distinct places from where the access happens:
 *   1. C++ GC code
 *   2. Mutator code (through runtime/C++ barriers)
 *   3. Mutator code (through interpreter/assembly barriers)
 *
 * The problematic places in C++ GC code fall into two categories:
 *   *) Concurrent with evacuation: these need to to see the concurrently installed
 *      forwardee. This also affects the CAS for forwarding installation, as the failing
 *      CAS should to see the other forwardee. Therefore, these paths use
 *      "Acquire" in lieu of "Consume". This is also a default mode to get the forwardee,
 *      for extra safety.
 *   *) Happening past the evacuation: since all forwardee installations have happened,
 *      and there was a coordination event (safepoint) from the last evacuation,
 *      we should not observe anything in flight. That is a "stable" mode, and on
 *      that path, "Relaxed" is enough. This usually matters for a heavy-weight update
 *      heap operations.
 *
 * The mutator code can access the forwardee at arbitrary point during the GC. Therefore,
 * it can potentially race with the concurrent evacuation.
 *
 * The mutator runtime/C++ code accesses forwardees through the default method
 * that does "Acquire" for additional safety. That path is taken by self-healing paths,
 * which are relatively rare, and already paid the significant cost of going to runtime.
 *
 * The mutator interpreter/assembly accesses use the hand-written arch-specific assembly
 * code for barriers that is immune to C++ shenanigans, and does use data dependencies to
 * provide "Consume" semantics.
 *
 * TODO: When "Consume" is available, load mark words with "consume" everywhere,
 * and drop the distinction between default and stable accessors.
 */

inline oop ShenandoahForwarding::decode_forwardee(oop obj, markWord mark) {
  // JVMTI and JFR code use mark words for marking objects for their needs.
  // On this path, we can encounter the "marked" object, but with NULL
  // fwdptr. That object is still not forwarded, and we need to return
  // the object itself.
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    if (fwdptr != NULL) {
      return cast_to_oop(fwdptr);
    }
  }
  return obj;
}

inline oop ShenandoahForwarding::decode_forwardee_mutator(oop obj, markWord mark) {
  // Same as above, but mutator thread cannot ever see NULL forwardee.
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    if (fwdptr != NULL) {
      return cast_to_oop(fwdptr);
    }
  }
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee_raw(oop obj) {
  // Forwardee might be changing, acquire the mark
  markWord mark = obj->mark_acquire();
  return decode_forwardee(obj, mark);
}

inline oop ShenandoahForwarding::get_forwardee_stable_raw(oop obj) {
  // Forwardee is stable, non-acquiring mark is enough.
  markWord mark = obj->mark();
  return decode_forwardee(obj, mark);
}

inline oop ShenandoahForwarding::get_forwardee_mutator(oop obj) {
  // Same as above, but mutator thread cannot ever see NULL forwardee.
  // It also performs the "acquire" read to coordinate with GC evacs.
  shenandoah_assert_correct(NULL, obj);
  assert(Thread::current()->is_Java_thread(), "Must be a mutator thread");
  markWord mark = obj->mark_acquire();
  return decode_forwardee_mutator(obj, mark);
}

inline oop ShenandoahForwarding::get_forwardee(oop obj) {
  shenandoah_assert_correct(NULL, obj);
  return get_forwardee_raw(obj);
}

inline oop ShenandoahForwarding::get_forwardee_maybe_null(oop obj) {
  if (obj == NULL) return obj;
  shenandoah_assert_correct(NULL, obj);
  return get_forwardee_raw(obj);
}

inline oop ShenandoahForwarding::get_forwardee_stable(oop obj) {
  shenandoah_assert_correct(NULL, obj);
  return get_forwardee_stable_raw(obj);
}

inline bool ShenandoahForwarding::is_forwarded(oop obj) {
  return obj->mark().is_marked();
}

inline oop ShenandoahForwarding::try_update_forwardee(oop obj, oop update) {
  markWord old_mark = obj->mark();
  if (old_mark.is_marked()) {
    // Already forwarded, don't try to overwrite. Re-read with acquire semantics.
    return get_forwardee_raw(obj);
  }

  markWord new_mark = markWord::encode_pointer_as_mark(update);
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_release);
  if (prev_mark == old_mark) {
    return update;
  } else {
    // Lost the race. Re-read with acquire semantics.
    return get_forwardee_raw(obj);
  }
}

// Atomic updates of object with their forwardees. The reason why we need stronger-than-relaxed
// memory ordering has to do with coordination with GC barriers and mutator accesses.
//
// In essence, stronger CAS access is required to maintain the transitive chains that mutator
// accesses build by themselves. To illustrate this point, consider the following example.
//
// Suppose "o" is the object that has a field "x" and the reference to "o" is stored
// to field at "addr", which happens to be Java volatile field. Normally, the accesses to volatile
// field at "addr" would be matched with release/acquire barriers. This changes when GC moves
// the object under mutator feet.
//
// Thread 1 (Java)
//         // --- previous access starts here
//         ...
//   T1.1: store(&o.x, 1, mo_relaxed)
//   T1.2: store(&addr, o, mo_release) // volatile store
//
//         // --- new access starts here
//         // LRB: copy and install the new copy to fwdptr
//   T1.3: var copy = copy(o)
//   T1.4: cas(&fwd, t, copy, mo_release) // pointer-mediated publication
//         <access continues>
//
// Thread 2 (GC updater)
//   T2.1: var f = load(&fwd, mo_{consume|acquire}) // pointer-mediated acquisition
//   T2.2: cas(&addr, o, f, mo_release) // this method
//
// Thread 3 (Java)
//   T3.1: var o = load(&addr, mo_acquire) // volatile read
//   T3.2: if (o != null)
//   T3.3:   var r = load(&o.x, mo_relaxed)
//
// r is guaranteed to contain "1".
//
// Without GC involvement, there is synchronizes-with edge from T1.2 to T3.1,
// which guarantees this. With GC involvement, when LRB copies the object and
// another thread updates the reference to it, we need to have the transitive edge
// from T1.4 to T2.1 (that one is guaranteed by forwarding accesses), plus the edge
// from T2.2 to T3.1 (which is brought by this CAS).
//
// Note that we do not need to "acquire" in these methods, because we do not read the
// failure witnesses contents on any path, and "release" is enough.
//
// Note: this derivation is valid under quite weak C++ memory model. Real hardware can
// provide the stronger consistency model that would obviate the need for "release" here.
// Instead of relaxing everywhere based on specific hardware knowledge, we instead
// provide the "stable" fast-path versions of these in the next section.
//

inline void ShenandoahForwarding::update_with_forwarded(oop update, oop* addr, oop compare) {
  assert(is_aligned(addr, HeapWordSize), "Address should be aligned: " PTR_FORMAT, p2i(addr));
  Atomic::cmpxchg(addr, compare, update, memory_order_release);
}

inline void ShenandoahForwarding::update_with_forwarded(oop update, narrowOop* addr, oop compare) {
  assert(is_aligned(addr, sizeof(narrowOop)), "Address should be aligned: " PTR_FORMAT, p2i(addr));
  narrowOop c = CompressedOops::encode(compare);
  narrowOop u = CompressedOops::encode(update);
  Atomic::cmpxchg(addr, c, u, memory_order_release);
}

inline void ShenandoahForwarding::update_with_forwarded(oop update, narrowOop* addr, narrowOop compare) {
  assert(is_aligned(addr, sizeof(narrowOop)), "Address should be aligned: " PTR_FORMAT, p2i(addr));
  narrowOop u = CompressedOops::encode(update);
  Atomic::cmpxchg(addr, compare, u, memory_order_release);
}

/*
 * Stable versions of the above.
 *
 * These do not need any special memory semantics, as these are only called when no
 * forwardings are being installed. This is usually happens outside of evacuation,
 * during the bulk heap updates.
 */

inline void ShenandoahForwarding::update_with_forwarded_stable(oop update, oop* addr, oop compare) {
  assert(is_aligned(addr, HeapWordSize), "Address should be aligned: " PTR_FORMAT, p2i(addr));
  Atomic::cmpxchg(addr, compare, update, memory_order_relaxed);
}

inline void ShenandoahForwarding::update_with_forwarded_stable(oop update, narrowOop* addr, oop compare) {
  assert(is_aligned(addr, sizeof(narrowOop)), "Address should be aligned: " PTR_FORMAT, p2i(addr));
  narrowOop c = CompressedOops::encode(compare);
  narrowOop u = CompressedOops::encode(update);
  Atomic::cmpxchg(addr, c, u, memory_order_relaxed);
}

inline void ShenandoahForwarding::update_with_forwarded_stable(oop update, narrowOop* addr, narrowOop compare) {
  assert(is_aligned(addr, sizeof(narrowOop)), "Address should be aligned: " PTR_FORMAT, p2i(addr));
  narrowOop u = CompressedOops::encode(update);
  Atomic::cmpxchg(addr, compare, u, memory_order_relaxed);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
