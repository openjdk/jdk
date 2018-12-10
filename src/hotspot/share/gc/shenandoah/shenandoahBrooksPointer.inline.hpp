/*
 * Copyright (c) 2015, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHBROOKSPOINTER_INLINE_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHBROOKSPOINTER_INLINE_HPP

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahBrooksPointer.hpp"
#include "runtime/atomic.hpp"

inline HeapWord** ShenandoahBrooksPointer::brooks_ptr_addr(oop obj) {
  return (HeapWord**)((HeapWord*) obj + word_offset());
}

inline void ShenandoahBrooksPointer::initialize(oop obj) {
  shenandoah_assert_in_heap(NULL, obj);
  *brooks_ptr_addr(obj) = (HeapWord*) obj;
}

inline void ShenandoahBrooksPointer::set_raw(oop obj, HeapWord* update) {
  shenandoah_assert_in_heap(NULL, obj);
  *brooks_ptr_addr(obj) = update;
}

inline HeapWord* ShenandoahBrooksPointer::get_raw(oop obj) {
  shenandoah_assert_in_heap(NULL, obj);
  return *brooks_ptr_addr(obj);
}

inline HeapWord* ShenandoahBrooksPointer::get_raw_unchecked(oop obj) {
  return *brooks_ptr_addr(obj);
}

inline oop ShenandoahBrooksPointer::forwardee(oop obj) {
  shenandoah_assert_correct(NULL, obj);
  return oop(*brooks_ptr_addr(obj));
}

inline oop ShenandoahBrooksPointer::try_update_forwardee(oop obj, oop update) {
  oop result = (oop) Atomic::cmpxchg(update, (oop*)brooks_ptr_addr(obj), obj);
  shenandoah_assert_correct_except(NULL, obj, !oopDesc::equals_raw(result, obj));
  return result;
}

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHBROOKSPOINTER_INLINE_HPP
