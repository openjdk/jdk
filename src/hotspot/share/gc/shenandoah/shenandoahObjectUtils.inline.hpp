/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTUTILS_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTUTILS_INLINE_HPP

#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahObjectUtils.hpp"
#include "oops/klass.hpp"
#include "oops/markWord.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/thread.hpp"

// This is a variant of ObjectSynchronizer::stable_mark(), which does the same thing, but also
// handles forwarded objects. This is intended to be used by concurrent evacuation only. No other
// code is supposed to observe from-space objects.
#ifdef _LP64
markWord ShenandoahObjectUtils::stable_mark(oop obj) {
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  for (;;) {
    assert(heap->is_in(obj), "object not in heap: " PTR_FORMAT, p2i(obj));
    markWord mark = obj->mark_acquire();

    // The mark can be in one of the following states:
    // *  Inflated     - just return mark from inflated monitor
    // *  Stack-locked - coerce it to inflating, and then return displaced mark
    // *  INFLATING    - busy wait for conversion to complete
    // *  Neutral      - return mark
    // *  Marked       - object is forwarded, try again on forwardee

    // Most common case first.
    if (mark.is_neutral() || mark.is_fast_locked()) {
      return mark;
    }

    // If object is already forwarded, then resolve it, and try again.
    if (mark.is_marked()) {
      if (heap->is_full_gc_move_in_progress()) {
        // In these cases, we want to return the header as-is: the Klass* would not be overloaded.
        return mark;
      }
      obj = cast_to_oop(mark.decode_pointer());
      continue;
    }

    // CASE: inflated
    if (mark.has_monitor()) {
      // It is safe to access the object monitor because all Java and GC worker threads
      // participate in the monitor deflation protocol (i.e, they react to handshakes and STS requests).
      ObjectMonitor* inf = mark.monitor();
      markWord dmw = inf->header();
      assert(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT ", original mark: " INTPTR_FORMAT, dmw.value(), mark.value());
      return dmw;
    }

    // CASE: inflating
    if (mark.is_being_inflated()) {
      // Interference, try again.
      continue;
    }

    // CASE: stack-locked
    if (mark.has_locker()) {
      if (Thread::current()->is_lock_owned((address)mark.locker())) {
        // This thread owns the lock. We can safely access it.
        markWord dmw = mark.displaced_mark_helper();
        assert(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT ", original mark: " INTPTR_FORMAT, dmw.value(), mark.value());
        return dmw;
      }

      // Else we try to install INFLATING into the header. This will (temporarily) prevent other
      // threads from stack-locking or evacuating the object.
      markWord cmp = obj->cas_set_mark(markWord::INFLATING(), mark);
      if (cmp != mark) {
        continue;       // Interference -- just retry
      }

      // We've successfully installed INFLATING (0) into the mark-word.
      // This is the only case where 0 will appear in a mark-word.
      // Only the singular thread that successfully swings the mark-word
      // to 0 can fetch the stack-lock and safely read the displaced header.

      // fetch the displaced mark from the owner's stack.
      // The owner can't die or unwind past the lock while our INFLATING
      // object is in the mark.  Furthermore the owner can't complete
      // an unlock on the object, either. No other thread can do evacuation, either.
      markWord dmw = mark.displaced_mark_helper();
      // Catch if the object's header is not neutral (not locked and
      // not marked is what we care about here).
      assert(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT, dmw.value());

      // Must preserve store ordering. The monitor state must
      // be stable at the time of publishing the monitor address.
      guarantee(obj->mark() == markWord::INFLATING(), "invariant");
      // Release semantics so that above set_object() is seen first.
      obj->release_set_mark(mark);

      return dmw;
    }
  }
}
#endif

Klass* ShenandoahObjectUtils::klass(oop obj) {
  if (!UseCompactObjectHeaders) {
    return obj->klass();
  }
#ifdef _LP64
  markWord header = stable_mark(obj);
  assert(header.narrow_klass() != 0, "klass must not be NULL: " INTPTR_FORMAT, header.value());
  return header.klass();
#else
  return obj->klass();
#endif
}

size_t ShenandoahObjectUtils::size(oop obj) {
  if (!UseCompactObjectHeaders) {
    return obj->size();
  }
  Klass* kls = klass(obj);
  return obj->size_given_klass(kls);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTUTILS_HPP
