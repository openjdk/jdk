/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZITERATOR_INLINE_HPP
#define SHARE_GC_Z_ZITERATOR_INLINE_HPP

#include "gc/z/zIterator.hpp"

#include "gc/z/zVerify.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"

inline bool ZIterator::is_invisible_object(oop obj) {
  // This is a good place to make sure that we can't concurrently iterate over
  // objects while VMThread operations think they have exclusive access to the
  // object graph.
  //
  // One example that have caused problems is the JFR Leak Profiler, which
  // sets the mark word to a value that makes the object arrays look like
  // invisible objects.
  z_verify_safepoints_are_blocked();

  return obj->mark_acquire().is_marked();
}

inline bool ZIterator::is_invisible_object_array(oop obj) {
  return obj->klass()->is_objArray_klass() && is_invisible_object(obj);
}

// This iterator skips invisible object arrays
template <typename OopClosureT>
void ZIterator::oop_iterate_safe(oop obj, OopClosureT* cl) {
  // Skip invisible object arrays - we only filter out *object* arrays,
  // because that check is arguably faster than the is_invisible_object
  // check, and primitive arrays are cheap to call oop_iterate on.
  if (!is_invisible_object_array(obj)) {
    obj->oop_iterate(cl);
  }
}

template <typename OopClosureT>
void ZIterator::oop_iterate(oop obj, OopClosureT* cl) {
  assert(!is_invisible_object_array(obj), "not safe");
  obj->oop_iterate(cl);
}

template <typename OopClosureT>
void ZIterator::oop_iterate_range(objArrayOop obj, OopClosureT* cl, int start, int end) {
  assert(!is_invisible_object_array(obj), "not safe");
  obj->oop_iterate_range(cl, start, end);
}

template <typename Function>
class ZBasicOopIterateClosure : public BasicOopIterateClosure {
private:
  Function _function;

public:
  ZBasicOopIterateClosure(Function function)
    : _function(function) {}

  virtual void do_oop(oop* p) {
    _function((volatile zpointer*)p);
  }

  virtual void do_oop(narrowOop* p_) {
    ShouldNotReachHere();
  }
};

// This function skips invisible roots
template <typename Function>
void ZIterator::basic_oop_iterate_safe(oop obj, Function function) {
  ZBasicOopIterateClosure<Function> cl(function);
  oop_iterate_safe(obj, &cl);
}

template <typename Function>
void ZIterator::basic_oop_iterate(oop obj, Function function) {
  ZBasicOopIterateClosure<Function> cl(function);
  oop_iterate(obj, &cl);
}

template <typename Function>
ZObjectClosure<Function>::ZObjectClosure(Function function)
  : _function(function) {}

template <typename Function>
void ZObjectClosure<Function>::do_object(oop obj) {
  _function(obj);
}

#endif // SHARE_GC_Z_ZITERATOR_INLINE_HPP
