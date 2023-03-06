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
#include "oops/oop.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/synchronizer.hpp"

inline oop ShenandoahForwarding::get_forwardee_raw(oop obj) {
  shenandoah_assert_in_heap(nullptr, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline oop ShenandoahForwarding::get_forwardee_raw_unchecked(oop obj) {
  // JVMTI and JFR code use mark words for marking objects for their needs.
  // On this path, we can encounter the "marked" object, but with null
  // fwdptr. That object is still not forwarded, and we need to return
  // the object itself.
  markWord mark = obj->mark();
  if (mark.is_marked()) {
    assert((mark.value() & self_forwarded_mask_in_place) == 0, "must not be self-forwarded");
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    if (fwdptr != nullptr) {
      return cast_to_oop(fwdptr);
    }
  }
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee_mutator(oop obj) {
  // Same as above, but mutator thread cannot ever see null forwardee.
  shenandoah_assert_correct(nullptr, obj);
  assert(Thread::current()->is_Java_thread(), "Must be a mutator thread");

  markWord mark = obj->mark();
  if (mark.is_marked()) {
    assert((mark.value() & self_forwarded_mask_in_place) == 0, "must not be self-forwarded");
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    assert(fwdptr != nullptr, "Forwarding pointer is never null here");
    return cast_to_oop(fwdptr);
  } else {
    return obj;
  }
}

inline oop ShenandoahForwarding::get_forwardee(oop obj) {
  shenandoah_assert_correct(nullptr, obj);
  return get_forwardee_raw_unchecked(obj);
}

inline bool ShenandoahForwarding::is_forwarded(oop obj) {
  markWord mark = obj->mark();
  return mark.is_marked() /*|| ((mark.value() & self_forwarded_mask_in_place) != 0)*/;
}

inline ShenandoahForwardingScope::ShenandoahForwardingScope(oop obj) :
    _obj(obj), _mark(obj->mark_acquire()) {}

inline oop ShenandoahForwardingScope::forwardee(markWord mark) const {
  uintptr_t markv = mark.value();
  uintptr_t mark_flipped = markv ^ markWord::lock_mask_in_place;
  assert(ShenandoahForwarding::self_forwarded_mask_in_place == 4, "sanity");
  assert((markv & ShenandoahForwarding::self_forwarded_mask_in_place) == (mark_flipped & ShenandoahForwarding::self_forwarded_mask_in_place),
         "sanity: mark: " INTPTR_FORMAT " (masked: " INTPTR_FORMAT "), flipped: " INTPTR_FORMAT " (masked: " INTPTR_FORMAT ")",
         markv, (markv & ShenandoahForwarding::self_forwarded_mask_in_place), mark_flipped, (mark_flipped & ShenandoahForwarding::self_forwarded_mask_in_place));
  if (mark.is_marked()) assert((mark_flipped & markWord::lock_mask_in_place) == 0, "sanity");
  if ((mark_flipped & markWord::lock_mask_in_place) == 0) {
    assert((mark.value() & ShenandoahForwarding::self_forwarded_mask_in_place) == 0, "must not be self-forwarded");
    oop fwd = cast_to_oop(mark_flipped);
    assert(fwd != nullptr, "forwardee must not be null");
    assert(markv == mark.value(), "mark must not change in flight");
    return fwd;
  } else if ((mark_flipped & ShenandoahForwarding::self_forwarded_mask_in_place) != 0) {
    assert(markv == mark.value(), "mark must not change in flight");
    return _obj;
  } else {
    assert(markv == mark.value(), "mark must not change in flight");
    return nullptr;
  }
}

inline oop ShenandoahForwardingScope::forwardee() const {
  return forwardee(_mark);
}

inline oop ShenandoahForwardingScope::forward_to_impl(markWord new_mark) {
  uintptr_t markv = _mark.value();
  assert((_mark.value() & ShenandoahForwarding::self_forwarded_mask_in_place) == 0, "must not be self-forwarded");
  assert((_mark.value() & markWord::lock_mask_in_place) != markWord::marked_value, "must not be forwarded");
  markWord prev_mark = _obj->cas_set_mark(new_mark, _mark, memory_order_conservative);
  if (prev_mark == _mark) {
    // We succeeded.
    assert(markv == _mark.value(), "mark must not change in flight");
    return nullptr;
  } else {
    oop other = forwardee(prev_mark);
    assert(other != nullptr, "must be forwarded: " INTPTR_FORMAT, prev_mark.value());
    assert(markv == _mark.value(), "mark must not change in flight");
    return other;
  }
}

inline oop ShenandoahForwardingScope::forward_to(oop fwd) {
  return forward_to_impl(markWord::encode_pointer_as_mark(fwd));
}

inline oop ShenandoahForwardingScope::forward_to_self() {
  if (_mark.has_locker()) {
    // When the object is stack-locked, then the real mark is displaced
    // and would overwrite the self-locked mark as soon as the object unlocks.
    // This must not happen. We would have to set the self-forwarded bit in
    // the displaced header (in addition to the actual header), but this is not
    // safe - the stack lock could become unlocked at any time. Instead, we
    // inflate to a full monitor here. It is safe to modify the displaced
    // header in the monitor because the GC will coordinate with the deflation
    // thread.
    ObjectMonitor* mon = ObjectSynchronizer::inflate(Thread::current(), _obj, ObjectSynchronizer::inflate_cause_vm_internal);
    _mark = _obj->mark_acquire();
    assert(_mark.has_monitor(), "must be inflated now");
    markWord displaced_mark = mon->header();
    mon->set_header(markWord(displaced_mark.value() | ShenandoahForwarding::self_forwarded_mask_in_place));
  }
  markWord new_mark = markWord(_mark.value() | ShenandoahForwarding::self_forwarded_mask_in_place);
  return forward_to_impl(new_mark);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
