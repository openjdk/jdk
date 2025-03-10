/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_PRESERVEDMARKS_HPP
#define SHARE_GC_SHARED_PRESERVEDMARKS_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "oops/oop.hpp"
#include "utilities/stack.hpp"

class WorkerTask;
class PreservedMarksSet;
class WorkerThreads;

class PreservedMark {
 private:
  oop _o;
  markWord _m;

 public:
  PreservedMark(oop obj, markWord m) : _o(obj), _m(m) { }

  oop get_oop() { return _o; }
  inline void set_mark() const;
  void set_oop(oop obj) { _o = obj; }
};

class PreservedMarks {
private:
  typedef Stack<PreservedMark, mtGC> PreservedMarkStack;

  PreservedMarkStack _stack;

  inline bool should_preserve_mark(oop obj, markWord m) const;

public:
  size_t size() const { return _stack.size(); }
  inline void push_if_necessary(oop obj, markWord m);
  inline void push_always(oop obj, markWord m);
  // Iterate over the stack, restore all preserved marks, and
  // reclaim the memory taken up by the stack segments.
  void restore();

  // Adjust the preserved mark according to its
  // forwarding location stored in the mark.
  static void adjust_preserved_mark(PreservedMark* elem);

  // Iterate over the stack, adjust all preserved marks according
  // to their forwarding location stored in the mark.
  void adjust_during_full_gc();

  void restore_and_increment(volatile size_t* const _total_size_addr);

  // Assert the stack is empty and has no cached segments.
  void assert_empty() PRODUCT_RETURN;

  inline PreservedMarks();
  ~PreservedMarks() { assert_empty(); }
};

class PreservedMarksSet : public CHeapObj<mtGC> {
private:
  // true -> _stacks will be allocated in the C heap
  // false -> _stacks will be allocated in the resource arena
  const bool _in_c_heap;

  // Number of stacks we have allocated (typically, one stack per GC worker).
  // This should be >= 1 if the stacks have been initialized,
  // or == 0 if they have not.
  uint _num;

  // Stack array (typically, one stack per GC worker) of length _num.
  // This should be != null if the stacks have been initialized,
  // or == null if they have not.
  Padded<PreservedMarks>* _stacks;

public:
  uint num() const { return _num; }

  // Return the i'th stack.
  PreservedMarks* get(uint i = 0) const {
    assert(_num > 0 && _stacks != nullptr, "stacks should have been initialized");
    assert(i < _num, "pre-condition");
    return (_stacks + i);
  }

  // Allocate stack array.
  void init(uint num);

  // Iterate over all stacks, restore all preserved marks, and reclaim
  // the memory taken up by the stack segments using the given WorkerThreads. If the WorkerThreads
  // is null, perform the work serially in the current thread.
  void restore(WorkerThreads* workers);

  // Reclaim stack array.
  void reclaim();

  // Assert all the stacks are empty and have no cached segments.
  void assert_empty() PRODUCT_RETURN;

  PreservedMarksSet(bool in_c_heap)
      : _in_c_heap(in_c_heap), _num(0), _stacks(nullptr) { }

  ~PreservedMarksSet() {
    assert(_stacks == nullptr && _num == 0, "stacks should have been reclaimed");
  }
};

#endif // SHARE_GC_SHARED_PRESERVEDMARKS_HPP
