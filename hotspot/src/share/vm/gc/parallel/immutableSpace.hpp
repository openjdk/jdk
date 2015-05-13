/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_PARALLEL_IMMUTABLESPACE_HPP
#define SHARE_VM_GC_PARALLEL_IMMUTABLESPACE_HPP

#include "memory/iterator.hpp"

// An ImmutableSpace is a viewport into a contiguous range
// (or subrange) of previously allocated objects.

// Invariant: bottom() and end() are on page_size boundaries and
// bottom() <= end()

class ImmutableSpace: public CHeapObj<mtGC> {
  friend class VMStructs;
 protected:
  HeapWord* _bottom;
  HeapWord* _end;

 public:
  ImmutableSpace()                   { _bottom = NULL; _end = NULL;  }
  HeapWord* bottom() const           { return _bottom;               }
  HeapWord* end() const              { return _end;                  }

  MemRegion region() const { return MemRegion(bottom(), end()); }

  // Initialization
  void initialize(MemRegion mr);

  bool contains(const void* p) const { return _bottom <= p && p < _end; }

  // Size computations.  Sizes are in bytes.
  size_t capacity_in_bytes() const            { return capacity_in_words() * HeapWordSize; }

  // Size computations.  Sizes are in heapwords.
  size_t capacity_in_words() const                { return pointer_delta(end(), bottom()); }
  virtual size_t capacity_in_words(Thread*) const { return capacity_in_words(); }

  // Iteration.
  virtual void oop_iterate(ExtendedOopClosure* cl);
  virtual void object_iterate(ObjectClosure* cl);

  // Debugging
  virtual void print() const            PRODUCT_RETURN;
  virtual void print_short() const      PRODUCT_RETURN;
  virtual void verify();
};

#endif // SHARE_VM_GC_PARALLEL_IMMUTABLESPACE_HPP
