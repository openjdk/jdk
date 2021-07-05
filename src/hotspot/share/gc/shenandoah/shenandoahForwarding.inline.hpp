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
 * ...Release-Acquire ordering might be considered. But, on weakly-ordered
 * architectures, doing "Acquire" on hot-path would significantly penalize users.
 *
 * We can recognize that C++ GC code hardly ever accesses the object contents after
 * the evacuation: the marking is done by the time evacuations happen, the evacuation
 * code only reads the contents of the from-copy (that is not protected by
 * synchronization anyhow), and update-refs only writes the object pointers themselves.
 * Therefore, "Relaxed" still works, "Consume" is good as the additional safety measure,
 * but the cost of "Acquire" is too high.
 *
 * The mutator code accesses forwarded objects through runtime interface, which
 * among other things inhibits the problematic C++ optimizations that are otherwise
 * would require "Consume".
 *
 * Hand-written arch-specific assembly code for barriers uses data dependencies to
 * provide "Consume" semantics that would not be affected by C++ compilers.
 *
 * The critical point where synchronization is needed are mark word accesses:
 *   1. markword loads are using the "relaxed" loads, due to the reasons above;
 *   2. markword stores that publish new forwardee are marked with "release";
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
  shenandoah_assert_correct(NULL, obj);
  assert(Thread::current()->is_Java_thread(), "Must be a mutator thread");

  markWord mark = obj->mark();
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
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_release);
  if (prev_mark == old_mark) {
    return update;
  } else {
    return cast_to_oop(prev_mark.clear_lock_bits().to_pointer());
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
