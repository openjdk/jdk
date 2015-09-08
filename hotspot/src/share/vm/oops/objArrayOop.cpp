/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/specialized_oop_closures.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"

oop objArrayOopDesc::atomic_compare_exchange_oop(int index, oop exchange_value,
                                                 oop compare_value) {
  volatile HeapWord* dest;
  if (UseCompressedOops) {
    dest = (HeapWord*)obj_at_addr<narrowOop>(index);
  } else {
    dest = (HeapWord*)obj_at_addr<oop>(index);
  }
  oop res = oopDesc::atomic_compare_exchange_oop(exchange_value, dest, compare_value, true);
  // update card mark if success
  if (res == compare_value) {
    update_barrier_set((void*)dest, exchange_value);
  }
  return res;
}

#define ObjArrayOop_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)                    \
                                                                                   \
void objArrayOopDesc::oop_iterate_range(OopClosureType* blk, int start, int end) {  \
  ((ObjArrayKlass*)klass())->oop_oop_iterate_range##nv_suffix(this, blk, start, end); \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(ObjArrayOop_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(ObjArrayOop_OOP_ITERATE_DEFN)
