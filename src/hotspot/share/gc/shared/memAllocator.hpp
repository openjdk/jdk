/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_MEMALLOCATOR_HPP
#define SHARE_GC_SHARED_MEMALLOCATOR_HPP

#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// These fascilities are used for allocating, and initializing newly allocated objects.

class MemAllocator: StackObj {
protected:
  class Allocation;

  Thread* const        _thread;
  Klass* const         _klass;
  const size_t         _word_size;

  // Allocate from the current thread's TLAB, without taking a new TLAB (no safepoint).
 HeapWord* mem_allocate_inside_tlab_fast() const;

private:
  // Allocate in a TLAB. Could allocate a new TLAB, and therefore potentially safepoint.
  HeapWord* mem_allocate_inside_tlab_slow(Allocation& allocation) const;

  // Allocate outside a TLAB. Could safepoint.
  HeapWord* mem_allocate_outside_tlab(Allocation& allocation) const;

protected:
  MemAllocator(Klass* klass, size_t word_size, Thread* thread)
    : _thread(thread),
      _klass(klass),
      _word_size(word_size)
  {
    assert(_thread == Thread::current(), "must be");
  }

  // Initialization provided by subclasses.
  virtual oop initialize(HeapWord* mem) const = 0;

  // This function clears the memory of the object.
  void mem_clear(HeapWord* mem) const;

  // This finish constructing an oop by installing the mark word and the Klass* pointer
  // last. At the point when the Klass pointer is initialized, this is a constructed object
  // that must be parseable as an oop by concurrent collectors.
  oop finish(HeapWord* mem) const;

  // Raw memory allocation. This will try to do a TLAB allocation, and otherwise fall
  // back to calling CollectedHeap::mem_allocate().
  HeapWord* mem_allocate(Allocation& allocation) const;

public:
  // Allocate and fully construct the object, and perform various instrumentation. Could safepoint.
  oop allocate() const;
};

class ObjAllocator: public MemAllocator {
public:
  ObjAllocator(Klass* klass, size_t word_size, Thread* thread = Thread::current())
    : MemAllocator(klass, word_size, thread) {}

  virtual oop initialize(HeapWord* mem) const;
};

class ObjArrayAllocator: public MemAllocator {
protected:
  const int  _length;
  const bool _do_zero;

  void mem_zap_start_padding(HeapWord* mem) const PRODUCT_RETURN;
  void mem_zap_end_padding(HeapWord* mem) const PRODUCT_RETURN;

public:
  ObjArrayAllocator(Klass* klass, size_t word_size, int length, bool do_zero,
                    Thread* thread = Thread::current())
    : MemAllocator(klass, word_size, thread),
      _length(length),
      _do_zero(do_zero) {}

  virtual oop initialize(HeapWord* mem) const;
};

class ClassAllocator: public MemAllocator {
public:
  ClassAllocator(Klass* klass, size_t word_size, Thread* thread = Thread::current())
    : MemAllocator(klass, word_size, thread) {}

  virtual oop initialize(HeapWord* mem) const;
};

// Manages a scope where a failed heap allocation results in
// suppression of JVMTI "resource exhausted" events and
// throwing a shared, backtrace-less OOME instance.
// Used for OOMEs that will not be propagated to user code.
class InternalOOMEMark: public StackObj {
 private:
  bool _outer;
  JavaThread* _thread;

 public:
  explicit InternalOOMEMark(JavaThread* thread) {
    assert(thread != nullptr, "nullptr is not supported");
    _outer = thread->is_in_internal_oome_mark();
    thread->set_is_in_internal_oome_mark(true);
    _thread = thread;
  }

  ~InternalOOMEMark() {
    // Check that only InternalOOMEMark sets
    // JavaThread::_is_in_internal_oome_mark
    assert(_thread->is_in_internal_oome_mark(), "must be");
    _thread->set_is_in_internal_oome_mark(_outer);
  }

  JavaThread* thread() const  { return _thread; }
};

#endif // SHARE_GC_SHARED_MEMALLOCATOR_HPP
