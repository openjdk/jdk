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
#include "oops/markWord.hpp"
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
 * ...Release-Acquire ordering might be considered.
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
 * The problematic places in C++ GC code are:
 *   *) evacuation that performs contended CASes for fwdptr installation. The failing
 *      CAS should "Acquire" the see the other forwardee;
 *   *) update-refs that reads the forwardee, and needs to see it completely. But, that
 *      phase happens after all forwardee installations happened, an update-refs-start
 *      pause happened, and so it should not observe anything in flight. Therefore,
 *      "Relaxed" is good for that path;
 *
 * The mutator code can access the fwdptr at arbitrary point in the GC. Therefore,
 * it can potentially race with the concurrent evacuation.
 *
 * The mutator runtime/C++ code accesses forwarded objects through the special method
 * that does "Acquire" for additional safety. That path is taken by self-healing paths,
 * which are relatively rare, and already paid the significant cost of going to runtime.
 *
 * The mutator interpreter/runtime accesses use the hand-written arch-specific assembly
 * code for barriers that is immune to C++ shenanigans, and does use data dependencies to
 * provide "Consume" semantics.
 *
 * The critical point where synchronization is needed are mark word accesses:
 *   1. markword loads by GC use "Relaxed";
 *   2. markword loads by mutator use "Acquire";
 *   3. markword load-stores (either by GC or mutator) use "Acquire" + "Release";
 *
 * TODO: When "Consume" is available, load mark words with "consume" for extra safety.
 */

inline oop ShenandoahForwarding::get_forwardee_raw(oop obj) {
  shenandoah_assert_in_heap(NULL, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline oop ShenandoahForwarding::get_forwardee_raw_unchecked(oop obj) {
  // JVMTI and JFR code use mark words for marking objects for their needs.
  // On this path, we can encounter the "marked" object, but with NULL
  // fwdptr. That object is still not forwarded, and we need to return
  // the object itself.
  markWord mark = obj->mark();
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    if (fwdptr != NULL) {
      return cast_to_oop(fwdptr);
    }
  }
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee_mutator(oop obj) {
  // Same as above, but mutator thread cannot ever see NULL forwardee.
  // It also performs the "acquire" read to coordinate with GC evacs.
  shenandoah_assert_correct(NULL, obj);
  assert(Thread::current()->is_Java_thread(), "Must be a mutator thread");

  markWord mark = obj->mark_acquire();
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    assert(fwdptr != NULL, "Forwarding pointer is never null here");
    return cast_to_oop(fwdptr);
  } else {
    return obj;
  }
}

inline oop ShenandoahForwarding::get_forwardee(oop obj) {
  shenandoah_assert_correct(NULL, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline bool ShenandoahForwarding::is_forwarded(oop obj) {
  return obj->mark().is_marked();
}

inline oop ShenandoahForwarding::try_update_forwardee(oop obj, oop update) {
  markWord old_mark = obj->mark();
  if (old_mark.is_marked()) {
    return cast_to_oop(old_mark.clear_lock_bits().to_pointer());
  }

  markWord new_mark = markWord::encode_pointer_as_mark(update);
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_acq_rel);
  if (prev_mark == old_mark) {
    return update;
  } else {
    return cast_to_oop(prev_mark.clear_lock_bits().to_pointer());
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
