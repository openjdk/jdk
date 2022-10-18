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
  markWord mark = obj->mark_acquire();

  if (mark.is_neutral() || mark.is_fast_locked()) {
    return mark;
  } else if (mark.is_marked()) {
    // If object is already forwarded, then resolve it, and try again.
    if (ShenandoahHeap::heap()->is_full_gc_move_in_progress()) {
      // In these cases, we want to return the header as-is: the Klass* would not be overloaded.
      return mark;
    }
    return stable_mark(cast_to_oop(mark.decode_pointer()));
  } else {
    assert(mark.has_monitor(), "must be inflated here");
    ObjectMonitor* monitor = mark.monitor();
    markWord dmw = monitor->header();
    assert(dmw.is_neutral(), "invariant");
    return dmw;
  }
}
#endif

Klass* ShenandoahObjectUtils::klass(oop obj) {
#ifdef _LP64
  markWord header = stable_mark(obj);
  assert(header.narrow_klass() != 0, "klass must not be NULL: " INTPTR_FORMAT, header.value());
  return header.klass();
#else
  return obj->klass();
#endif
}

size_t ShenandoahObjectUtils::size(oop obj) {
  Klass* kls = klass(obj);
  return obj->size_given_klass(kls);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHOBJECTUTILS_HPP
