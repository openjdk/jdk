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
#include "gc/shenandoah/shenandoahCollectionSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
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
  assert(!ShenandoahHeap::heap()->collection_set()->use_forward_table(obj), "Must not call with forwarding table");
  markWord mark = obj->mark();
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    if (fwdptr != nullptr) {
      return cast_to_oop(fwdptr);
    }
  }
  return obj;
}

inline oop ShenandoahForwarding::get_forwardee_mutator(oop obj) {
  // Same as above, but mutator thread cannot ever see null forwardee.
  //shenandoah_assert_correct(nullptr, obj);
  assert(Thread::current()->is_Java_thread(), "Must be a mutator thread");

  // We cannot assert the below here, because that region forwarding state might
  // be switched to FWD_TABLE concurrently. However, that is fine, we don't overwrite
  // the header-based forwarding until we've seen a handshake, at which point the
  // region forwarding state is stable (and we should not get here).
  // assert(!ShenandoahHeap::heap()->collection_set()->use_forward_table(obj), "Must not call with forwarding table");
  markWord mark = obj->mark();
  if (mark.is_marked()) {
    HeapWord* fwdptr = (HeapWord*) mark.clear_lock_bits().to_pointer();
    assert(fwdptr != nullptr, "Forwarding pointer is never null here");
    return cast_to_oop(fwdptr);
  } else {
    return obj;
  }
}

inline oop ShenandoahForwarding::get_forwardee(oop obj) {
  //shenandoah_assert_correct(nullptr, obj);
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
  markWord prev_mark = obj->cas_set_mark(new_mark, old_mark, memory_order_conservative);
  if (prev_mark == old_mark) {
    return update;
  } else {
    return cast_to_oop(prev_mark.clear_lock_bits().to_pointer());
  }
}

union _metadata {
  Klass*      _klass;
  narrowKlass _compressed_klass;
};

static _metadata safe_load_metadata(oop obj) {
  _metadata klass_word = *(cast_from_oop<_metadata*>(obj) + 1);
  OrderAccess::loadload();
  markWord mark = obj->mark();
  if (mark.is_forwarded()) {
    obj = mark.forwardee();
    klass_word = *(cast_from_oop<_metadata*>(obj) + 1);
  }
  return klass_word;
}

inline Klass* ShenandoahForwarding::klass(oop obj) {
  switch (ObjLayout::klass_mode()) {
    case ObjLayout::Compact: {
      markWord mark = obj->mark();
      if (mark.is_marked()) {
        oop fwd = cast_to_oop(mark.clear_lock_bits().to_pointer());
        mark = fwd->mark();
      }
      return mark.klass();
    }
    case ObjLayout::Compressed: {
      _metadata klass_word = safe_load_metadata(obj);
     return CompressedKlassPointers::decode(klass_word._compressed_klass);
    }
    default: {
      _metadata klass_word = safe_load_metadata(obj);
      return klass_word._klass;
    }
  }
}

inline size_t ShenandoahForwarding::size(oop obj) {
  obj = get_forwardee_raw(obj);
  return obj->size_given_klass(klass(obj));
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_INLINE_HPP
