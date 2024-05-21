/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP
#define SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP

#include "memory/allocation.hpp"
#include "memory/arena.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/nativeCallStack.hpp"

// Virtual memory regions that are tracked by NMT also have their NativeCallStack (NCS) tracked.
// NCS:s are:
// - Fairly large
// - Regularly compared for equality
// - Read a lot when a detailed report is printed
// Therefore we'd like:
// - To not store duplicates
// - Have fast comparisons
// - Have constant time access
// We achieve this by using a closed hashtable for finding previously existing NCS:s and referring to them by an index that's smaller than a pointer.
class NativeCallStackStorage : public CHeapObj<mtNMT> {
public:
  struct StackIndex {
    friend NativeCallStackStorage;

  private:
    int32_t _stack_index;
    StackIndex(int32_t stack_index)
      : _stack_index(stack_index) {
    }

  public:
    static bool equals(const StackIndex& a, const StackIndex& b) {
      return a._stack_index == b._stack_index;
    }
    StackIndex()
      : _stack_index(-1) {
    }
  };

private:
  struct Link : public ArenaObj {
    Link* next;
    StackIndex stack;
    Link(Link* next, StackIndex v)
      : next(next),
        stack(v) {
    }
  };
  StackIndex put(const NativeCallStack& value) {
    int bucket = value.calculate_hash() % _nr_buckets;
    Link* link = _buckets[bucket];
    while (link != nullptr) {
      if (value.equals(get(link->stack))) {
        return link->stack;
      }
      link = link->next;
    }
    int idx = _stacks.append(value);
    Link* new_link = new (&_arena) Link(_buckets[bucket], StackIndex(idx));
    _buckets[bucket] = new_link;
    return new_link->stack;
  }

  // For storage of the Links
  Arena _arena;
  // Pick a prime number of buckets.
  // 4099 gives a 50% probability of collisions at 76 stacks (as per birthday problem).
  static const constexpr int default_nr_buckets = 4099;
  int _nr_buckets;
  Link** _buckets;
  GrowableArrayCHeap<NativeCallStack, mtNMT> _stacks;
  bool _is_detailed_mode;
public:

  StackIndex push(const NativeCallStack& stack) {
    // Not in detailed mode, so not tracking stacks.
    if (!_is_detailed_mode) {
      return StackIndex();
    }
    return put(stack);
  }

  const inline NativeCallStack& get(StackIndex si) {
    return _stacks.at(si._stack_index);
  }

  NativeCallStackStorage(bool is_detailed_mode, int nr_buckets = default_nr_buckets)
  : _arena(mtNMT), _nr_buckets(nr_buckets), _buckets(nullptr), _stacks(), _is_detailed_mode(is_detailed_mode) {
    if (_is_detailed_mode) {
      _buckets = NEW_ARENA_ARRAY(&_arena, Link*, _nr_buckets);
      for (int i = 0; i < _nr_buckets; i++) {
        _buckets[i] = nullptr;
      }
    }
  }
};

#endif // SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP
