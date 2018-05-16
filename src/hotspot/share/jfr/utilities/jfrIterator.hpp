/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_UTILITIES_JFRLISTITERATOR_HPP
#define SHARE_VM_JFR_UTILITIES_JFRLISTITERATOR_HPP

#include "memory/allocation.hpp"

enum jfr_iter_direction {
  forward = 1,
  backward
};

template <typename Node>
class StopOnNullCondition : public AllStatic {
 public:
  static bool has_next(const Node* node) {
    return node != NULL;
  }
};

template <typename List, template <typename> class ContinuationPredicate>
class Navigator {
 public:
  typedef typename List::Node Node;
  typedef jfr_iter_direction Direction;
  Navigator(List& list, Direction direction) :
    _list(list), _node(direction == forward ? list.head() : list.tail()), _direction(direction) {}
  bool has_next() const {
    return ContinuationPredicate<Node>::has_next(_node);
  }

  bool direction_forward() const {
    return _direction == forward;
  }

  Node* next() const {
    assert(_node != NULL, "invariant");
    Node* temp = _node;
    _node = direction_forward() ? (Node*)_node->next() : (Node*)_node->prev();
    return temp;
  }

  void set_direction(Direction direction) {
    _direction = direction;
  }

  void reset(Direction direction) {
    set_direction(direction);
    _node = direction_forward() ? _list.head() : _list.tail();
  }

 private:
  List& _list;
  mutable Node* _node;
  Direction _direction;
};

template <typename List>
class NavigatorStopOnNull : public Navigator<List, StopOnNullCondition> {
 public:
  NavigatorStopOnNull(List& list, jfr_iter_direction direction = forward) : Navigator<List, StopOnNullCondition>(list, direction) {}
};

template<typename List, template <typename> class Navigator, typename AP = StackObj>
class IteratorHost : public AP {
 private:
  Navigator<List> _navigator;

 public:
  typedef typename List::Node Node;
  typedef jfr_iter_direction Direction;
  IteratorHost(List& list, Direction direction = forward) : AP(), _navigator(list, direction) {}
  void reset(Direction direction = forward) { _navigator.reset(direction); }
  bool has_next() const { return _navigator.has_next(); }
  Node* next() const { return _navigator.next(); }
  void set_direction(Direction direction) { _navigator.set_direction(direction); }
};

template<typename List, typename AP = StackObj>
class StopOnNullIterator : public IteratorHost<List, NavigatorStopOnNull, AP> {
 public:
  StopOnNullIterator(List& list, jfr_iter_direction direction = forward) : IteratorHost<List, NavigatorStopOnNull, AP>(list, direction) {}
};

#endif // SHARE_VM_JFR_UTILITIES_JFRLISTITERATOR_HPP
