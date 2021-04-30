/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZITERATOR_HPP
#define SHARE_GC_Z_ZITERATOR_HPP

#include "memory/allocation.hpp"
#include "memory/iterator.hpp"

class ZIterator : AllStatic {
private:
  static bool is_invisible_object(oop obj);
  static bool is_invisible_object_array(oop obj);

public:
  // This iterator skips invisible roots
  template <typename OopClosureT>
  static void oop_iterate_safe(oop obj, OopClosureT* cl);

  template <typename OopClosureT>
  static void oop_iterate(oop obj, OopClosureT* cl);

  template <typename OopClosureT>
  static void oop_iterate_range(objArrayOop obj, OopClosureT* cl, int start, int end);

  // This function skips invisible roots
  template <typename Function>
  static void basic_oop_iterate_safe(oop obj, Function function);

  template <typename Function>
  static void basic_oop_iterate(oop obj, Function function);
};

template <typename Function>
class ZObjectClosure : public ObjectClosure {
private:
  Function _function;

public:
  ZObjectClosure(Function function);
  virtual void do_object(oop obj);
};

#endif // SHARE_GC_Z_ZITERATOR_HPP
