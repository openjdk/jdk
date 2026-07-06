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
#include "runtime/javaThread.hpp"

inline oop ShenandoahForwarding::get_forwardee_raw(oop obj) {
  shenandoah_assert_in_heap_bounds(nullptr, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline oop ShenandoahForwarding::get_forwardee_raw_unchecked(oop obj) {
  // JVMTI and JFR code use mark words for marking objects for their needs.
  // On this path, we can encounter the "marked" object, but with null
  // fwdptr. That object is still not forwarded, and we need to return
  // the object itself.
  markWord mark = obj->mark();
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    if (fwdptr != nullptr) {
      return cast_to_oop(fwdptr);
    }
  }
  // Self-forwarded (evacuation failure): the object stays put; the
  // self-fwd bit is set alongside normal lock bits.
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee_mutator(oop obj) {
  // Same as above, but mutator thread cannot ever see null forwardee.
  shenandoah_assert_correct(nullptr, obj);
  assert(Thread::current()->is_Java_thread(), "Must be a mutator thread");

  markWord mark = obj->mark();
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    assert(fwdptr != nullptr, "Forwarding pointer is never null here");
    return cast_to_oop(fwdptr);
  }
  // Self-forwarded or not forwarded: return the object itself.
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee(oop obj) {
  shenandoah_assert_correct(nullptr, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline bool ShenandoahForwarding::is_forwarded(oop obj) {
  return obj->mark().is_forwarded();
}

inline bool ShenandoahForwarding::is_self_forwarded(oop obj) {
  return obj->mark().is_self_forwarded();
}

inline oop ShenandoahForwarding::try_update_forwardee(oop obj, oop update) {
  markWord old_mark = obj->mark();
  if (old_mark.is_marked()) {
    return cast_to_oop(old_mark.clear_lock_bits().to_pointer());
  }
  if (old_mark.is_self_forwarded()) {
    // Another thread lost the evacuation race; the object stays put.
    return obj;
  }

  markWord new_mark = markWord::encode_pointer_as_mark(update);
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_conservative);
  if (prev_mark == old_mark) {
    return update;
  }
  // Concurrent writers on a cset object's mark can only be other evacuation
  // threads installing forwarding (real or self). Mutators cannot reach the
  // mark of a not-yet-forwarded cset object: LRB + stack watermark barriers
  // redirect all reference uses before a Java-level operation can touch it.
  // So the only possible failure modes are a regular forwardee (marked) or
  // a self-forward (possibly with mutator lock/hash mods layered on top
  // after the self-forward became visible).
  if (prev_mark.is_marked()) {
    return cast_to_oop(prev_mark.clear_lock_bits().to_pointer());
  }
  assert(prev_mark.is_self_forwarded(),
         "concurrent writers on cset objects must install forwarding: prev=" INTPTR_FORMAT,
         prev_mark.value());
  return obj;
}

inline oop ShenandoahForwarding::try_forward_to_self(oop obj, markWord old_mark) {
  assert(!old_mark.is_forwarded(),
         "caller must pass a non-forwarded mark: old=" INTPTR_FORMAT, old_mark.value());
  markWord new_mark = old_mark.set_self_forwarded();
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_conservative);
  if (prev_mark == old_mark) {
    // We installed the self-forward.
    return nullptr;
  }
  // Same invariant as in try_update_forwardee: the only races on a
  // cset object's mark come from other evac threads installing forwarding.
  if (prev_mark.is_marked()) {
    return cast_to_oop(prev_mark.clear_lock_bits().to_pointer());
  }
  assert(prev_mark.is_self_forwarded(),
         "concurrent writers on cset objects must install forwarding: prev=" INTPTR_FORMAT,
         prev_mark.value());
  return obj;
}

inline Klass* ShenandoahForwarding::klass(oop obj) {
  if (UseCompactObjectHeaders) {
    markWord mark = obj->mark();
    if (mark.is_marked()) {
      oop fwd = cast_to_oop(mark.clear_lock_bits().to_pointer());
      mark = fwd->mark();
    }
    return mark.klass();
  } else {
    return obj->klass();
  }
}

inline size_t ShenandoahForwarding::size(oop obj) {
  return obj->size_given_klass(klass(obj));
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
