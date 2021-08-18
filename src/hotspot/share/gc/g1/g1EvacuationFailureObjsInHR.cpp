/*
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "g1EvacuationFailureObjsInHR.hpp"
#include "utilities/quickSort.hpp"


static intptr_t order_oop(oop a, oop b) {
  return static_cast<intptr_t>(a-b);
}

void G1EvacuationFailureObjsInHR::compact() {
  assert(_oop_array == NULL, "Must be");
  _oop_array = NEW_C_HEAP_ARRAY(oop, _objs_num, mtGC);
  Node* cur = _head._next;
  uint i = 0;
  while (cur != NULL) {
    assert(cur->_obj != NULL, "Must be");
    _oop_array[i++] = cur->_obj;
    cur = cur->_next;
  }
  clear_list();
}

void G1EvacuationFailureObjsInHR::sort() {
  QuickSort::sort(_oop_array, _objs_num, order_oop, true);
}

void G1EvacuationFailureObjsInHR::iterate_internal(ObjectClosure* closure) {
  oop prev = NULL;
  for (uint i = 0; i < _objs_num; i++) {
    assert(prev < _oop_array[i], "sanity");
    closure->do_object(prev = _oop_array[i]);
  }
  clear_array();
}

void G1EvacuationFailureObjsInHR::clear_list() {
  DEBUG_ONLY(uint i = _objs_num);
  Node* cur = _head._next;
  _head._next = NULL;

  while (cur != NULL) {
    Node* next = cur->_next;
    cur->_next = NULL;
    delete cur;
    cur = next;
    DEBUG_ONLY(i--);
  }
  assert(i == 0, "Must be");
}

void G1EvacuationFailureObjsInHR::clear_array() {
  FREE_C_HEAP_ARRAY(oop, _oop_array);
  _oop_array = NULL;
  _objs_num = 0;
}

G1EvacuationFailureObjsInHR::G1EvacuationFailureObjsInHR(uint region_idx) :
  _region_idx(region_idx),
  _objs_num(0),
  _oop_array(NULL) {
  Atomic::store(&_tail, &_head);
}

G1EvacuationFailureObjsInHR::~G1EvacuationFailureObjsInHR() {
  clear_list();
  clear_array();
}

void G1EvacuationFailureObjsInHR::record(oop obj) {
  assert(obj != NULL, "Must be");
  Node* new_one = new Node(obj);
  while (true) {
    Node* t = Atomic::load(&_tail);
    Node* next = Atomic::load(&t->_next);
    while (next != NULL) {
      t = next;
      next = Atomic::load(&next->_next);
    }
    Node* old_one = Atomic::cmpxchg(&t->_next, (Node*)NULL, new_one);
    if (old_one == NULL) {
      Atomic::store(&_tail, new_one);
      Atomic::inc(&_objs_num);
      break;
    }
  }
}

void G1EvacuationFailureObjsInHR::iterate(ObjectClosure* closure) {
  compact();
  sort();
  iterate_internal(closure);
}
