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
 * accompanied this code).    EXPECT_EQ(dev->_summary.by_type(mtTest)->reserved(), 0);
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
// We achieve this by storing them in a bog-standard closed addressing hashtable and never removing any elements.
class NativeCallStackStorage : public CHeapObj<mtNMT> {
private:
  struct Link : public CHeapObj<mtNMT> {
    Link* next;
    NativeCallStack stack;
    Link(Link* next, NativeCallStack v)
      : next(next),
        stack(v) {
    }
  };
  NativeCallStack* put(const NativeCallStack& value) {
    int bucket = value.calculate_hash() % nr_buckets;
    Link* link = buckets.at(bucket);
    while (link != nullptr) {
      if (value.equals(link->stack)) {
        return &link->stack;
      }
      link = link->next;
    }
    Link* new_link = new Link(buckets.at(bucket), value);
    buckets.at_put(bucket, new_link);
    return &new_link->stack;
  }

  static const constexpr int nr_buckets = 4096;
  GrowableArrayCHeap<Link*, mtNMT> buckets;
  bool is_detailed_mode;
public:
  struct StackIndex {
    friend NativeCallStackStorage;
  private:
    NativeCallStack* _stack;
    StackIndex(NativeCallStack* stack) : _stack(stack) {}
  public:
    static bool equals(const StackIndex& a, const StackIndex& b) {
      return a._stack == b._stack;
    }
    StackIndex() : _stack(nullptr) {}
    const NativeCallStack& stack() const {
      return *_stack;
    }
  };

  StackIndex push(const NativeCallStack& stack) {
    // Not in detailed mode, so not tracking stacks.
    if (!is_detailed_mode) {
      return StackIndex(nullptr);
    }
    return put(stack);
  }

  const inline NativeCallStack& get(StackIndex si) {
    return *si._stack;
  }

  NativeCallStackStorage(bool is_detailed_mode)
  :  buckets(), is_detailed_mode(is_detailed_mode) {
    if (is_detailed_mode) {
      buckets.at_grow(nr_buckets, nullptr);
    }
  }
};

#endif // SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP
