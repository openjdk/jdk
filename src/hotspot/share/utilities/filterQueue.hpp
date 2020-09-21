/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_FILTERQUEUE_HPP
#define SHARE_UTILITIES_FILTERQUEUE_HPP

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

template <class E>
class FilterQueue {
 private:
  class FilterQueueNode : public CHeapObj<mtInternal> {
   public:
    FilterQueueNode(const E& e): _next(NULL), _data(e) { }
    FilterQueueNode*    _next;
    E                   _data;
  };

  FilterQueueNode* _first;
  FilterQueueNode* load_first() {
    return Atomic::load_acquire(&_first);
  }

  static bool match_all(E d) { return true; }

 public:
  FilterQueue() : _first(NULL) { }

  // MT-safe
  bool is_empty() {
    return load_first() == NULL;
  }

  // MT-safe
  void add(E data);

  // MT-Unsafe, external serialization needed.
  template <typename MATCH_FUNC>
  bool contains(MATCH_FUNC& match_func);

  // MT-Unsafe, external serialization needed.
  E pop() {
    return pop(match_all);
  }

  // MT-Unsafe, external serialization needed.
  template <typename MATCH_FUNC>
  E pop(MATCH_FUNC& match_func);
};

#endif
