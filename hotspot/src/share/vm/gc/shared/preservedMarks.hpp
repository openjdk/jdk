/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_PRESERVEDMARKS_HPP
#define SHARE_VM_GC_SHARED_PRESERVEDMARKS_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "oops/oop.hpp"
#include "utilities/stack.hpp"

class GCTaskManager;
class WorkGang;

class PreservedMarks VALUE_OBJ_CLASS_SPEC {
private:
  class OopAndMarkOop {
  private:
    oop _o;
    markOop _m;

  public:
    OopAndMarkOop(oop obj, markOop m) : _o(obj), _m(m) { }

    void set_mark() const {
      _o->set_mark(_m);
    }
  };
  typedef Stack<OopAndMarkOop, mtGC> OopAndMarkOopStack;

  OopAndMarkOopStack _stack;

  inline bool should_preserve_mark(oop obj, markOop m) const;
  inline void push(oop obj, markOop m);

public:
  size_t size() const { return _stack.size(); }
  inline void push_if_necessary(oop obj, markOop m);
  // Iterate over the stack, restore all preserved marks, and
  // reclaim the memory taken up by the stack segments.
  void restore();

  inline static void init_forwarded_mark(oop obj);

  // Assert the stack is empty and has no cached segments.
  void assert_empty() PRODUCT_RETURN;

  inline PreservedMarks();
  ~PreservedMarks() { assert_empty(); }
};

class RemoveForwardedPointerClosure: public ObjectClosure {
public:
  virtual void do_object(oop obj);
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
  // This should be != NULL if the stacks have been initialized,
  // or == NULL if they have not.
  Padded<PreservedMarks>* _stacks;

  // Internal version of restore() that uses a WorkGang for parallelism.
  void restore_internal(WorkGang* workers, volatile size_t* total_size_addr);

  // Internal version of restore() that uses a GCTaskManager for parallelism.
  void restore_internal(GCTaskManager* gc_task_manager,
                        volatile size_t* total_size_addr);

public:
  uint num() const { return _num; }

  // Return the i'th stack.
  PreservedMarks* get(uint i = 0) const {
    assert(_num > 0 && _stacks != NULL, "stacks should have been initialized");
    assert(i < _num, "pre-condition");
    return (_stacks + i);
  }

  // Allocate stack array.
  void init(uint num);

  // Itrerate over all stacks, restore all presered marks, and reclaim
  // the memory taken up by the stack segments. If the executor is
  // NULL, restoration will be done serially. If the executor is not
  // NULL, restoration could be done in parallel (when it makes
  // sense). Supported executors: WorkGang (Serial, CMS, G1),
  // GCTaskManager (PS).
  template <class E>
  inline void restore(E* executor);

  // Reclaim stack array.
  void reclaim();

  // Assert all the stacks are empty and have no cached segments.
  void assert_empty() PRODUCT_RETURN;

  PreservedMarksSet(bool in_c_heap)
      : _in_c_heap(in_c_heap), _num(0), _stacks(NULL) { }

  ~PreservedMarksSet() {
    assert(_stacks == NULL && _num == 0, "stacks should have been reclaimed");
  }
};

#endif // SHARE_VM_GC_SHARED_PRESERVEDMARKS_HPP
