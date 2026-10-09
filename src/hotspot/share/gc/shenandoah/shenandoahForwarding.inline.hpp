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

inline oop ShenandoahForwarding::forwardee_raw(oop obj) {
  return forwardee_raw(obj, obj->mark());
}

inline oop ShenandoahForwarding::forwardee_raw(oop obj, markWord mark) {
  assert(mark.is_forwarded(), "Must only be here for forwarded objects");
  if (mark.is_marked()) {
    // Historically, JVMTI and JFR used mark words for marking objects
    // for their needs, without regard for GC. This path is now unreachable:
    // incompatible JFR code is disabled with Shenandoah. Assert paranoidly.
    HeapWord* fwd = (HeapWord*) mark.clear_lock_bits().to_pointer();
    assert(fwd != nullptr, "Sanity: forwardee must be set");
    return cast_to_oop(fwd);
  }
  // Self-forwarded. Report itself.
  assert(mark.is_self_forwarded(), "The only remaining case");
  return obj;
}

inline oop ShenandoahForwarding::forwardee(oop obj) {
  shenandoah_assert_correct(nullptr, obj);
  return forwardee_raw(obj);
}

inline oop ShenandoahForwarding::forwardee_or_null(oop obj) {
  shenandoah_assert_correct(nullptr, obj);
  markWord mark = obj->mark();
  if (mark.is_forwarded()) {
    return forwardee_raw(obj, mark);
  } else {
    return nullptr;
  }
}

inline bool ShenandoahForwarding::is_forwarded(oop obj) {
  return obj->mark().is_forwarded();
}

inline bool ShenandoahForwarding::is_real_forwarded(oop obj) {
  return obj->mark().is_marked();
}

inline bool ShenandoahForwarding::is_self_forwarded(oop obj) {
  return obj->mark().is_self_forwarded();
}

inline oop ShenandoahForwarding::try_forward_to(oop obj, oop update) {
  shenandoah_assert_correct(nullptr, obj);

  // Optimistic: check if object is already forwarded.
  markWord old_mark = obj->mark();
  if (old_mark.is_forwarded()) {
    return forwardee_raw(obj, old_mark);
  }

  // Attempt to install and return on success.
  markWord new_mark = (update != obj) ?
    markWord::encode_pointer_as_mark(update) :
    old_mark.set_self_forwarded();
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_conservative);
  if (prev_mark == old_mark) {
    return update;
  }

  // Lost the update race. Pick the forwarding from the existing mark.
  // Barriers guarantee that we can see only forwardings here, either real or self.
  // Mutators cannot modify mark without executing barriers first and
  // completing the forwarding install. Self-forwarded objects can have
  // more data layered on top of mark word, this code handles it too.
  assert(prev_mark.is_forwarded(), "Must be forwarded: prev=" INTPTR_FORMAT, prev_mark.value());
  return forwardee_raw(obj, prev_mark);
}

inline void ShenandoahForwarding::unset_self_forwarded(oop obj) {
  markWord m = obj->mark();
  if (m.is_self_forwarded()) {
    obj->set_mark(m.unset_self_forwarded());
  }
}

inline Klass* ShenandoahForwarding::klass(oop obj) {
  if (UseCompactObjectHeaders) {
    markWord mark = obj->mark();
    if (mark.is_marked()) {
      oop fwd = cast_to_oop(mark.clear_lock_bits().to_pointer());
      assert(fwd != nullptr, "Sanity: forwarding pointer must be set");
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

inline uint ShenandoahForwarding::age(oop obj) {
  markWord mark = obj->mark();
  uint age;
  if (!mark.is_marked()) {
    // Object has trustworthy mark.
    age = mark.age();
  } else {
    // Otherwise resolve the forwardee and pick age from there.
    oop fwd = forwardee_raw(obj, mark);
    markWord fwd_mark = fwd->mark();
    assert(!fwd_mark.is_marked(), "Must not be");
    age = fwd_mark.age();
  }
  assert(age <= markWord::max_age, "Age is in bounds");
  return age;
}

inline void ShenandoahForwarding::increase_age(oop obj, uint add) {
  // This method is expected to be called on new copy before it is exposed.
  // It also means mark is safe to modify with a non-CAS store.
  markWord mark = obj->mark();
  assert(!mark.is_marked(), "Must not be");
  mark = mark.set_age(MIN2(markWord::max_age, mark.age() + add));
  obj->set_mark(mark);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
