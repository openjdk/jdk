/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_SPACE_HPP
#define SHARE_GC_SHARED_SPACE_HPP

#include "gc/shared/blockOffsetTable.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "memory/memRegion.hpp"
#include "oops/markWord.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/align.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_SERIALGC
#include "gc/serial/serialBlockOffsetTable.hpp"
#endif

// A space is an abstraction for the "storage units" backing
// up the generation abstraction. It includes specific
// implementations for keeping track of free and used space,
// for iterating over objects and free blocks, etc.

// Forward decls.
class ContiguousSpace;
class Generation;
class ContiguousSpace;
class CardTableRS;
class DirtyCardToOopClosure;
class GenSpaceMangler;

// A space in which the free area is contiguous.  It therefore supports
// faster allocation, and compaction.
//
// Invariant: bottom() and end() are on page_size boundaries and
// bottom() <= top() <= end()
// top() is inclusive and end() is exclusive.
class ContiguousSpace: public CHeapObj<mtGC> {
  friend class VMStructs;

private:
  HeapWord* _bottom;
  HeapWord* _end;
  HeapWord* _top;
  // A helper for mangling the unused area of the space in debug builds.
  GenSpaceMangler* _mangler;

  GenSpaceMangler* mangler() { return _mangler; }

  // Allocation helpers (return null if full).
  inline HeapWord* allocate_impl(size_t word_size);
  inline HeapWord* par_allocate_impl(size_t word_size);

public:
  ContiguousSpace();
  ~ContiguousSpace();

  // Accessors
  HeapWord* bottom() const         { return _bottom; }
  HeapWord* end() const            { return _end;    }
  void set_bottom(HeapWord* value) { _bottom = value; }
  void set_end(HeapWord* value)    { _end = value; }

  // Testers
  bool is_empty() const              { return used() == 0; }

  // Returns true iff the given the space contains the
  // given address as part of an allocated object. For
  // certain kinds of spaces, this might be a potentially
  // expensive operation. To prevent performance problems
  // on account of its inadvertent use in product jvm's,
  // we restrict its use to assertion checks only.
  bool is_in(const void* p) const {
    return used_region().contains(p);
  }

  // Returns true iff the given reserved memory of the space contains the
  // given address.
  bool is_in_reserved(const void* p) const { return _bottom <= p && p < _end; }

  // Size computations.  Sizes are in bytes.
  size_t capacity() const { return byte_size(bottom(), end()); }
  size_t used()     const { return byte_size(bottom(), top()); }
  size_t free()     const { return byte_size(top(),    end()); }

  void print() const;
  void print_on(outputStream* st) const;

  // Initialization.
  // "initialize" should be called once on a space, before it is used for
  // any purpose.  The "mr" arguments gives the bounds of the space, and
  // the "clear_space" argument should be true unless the memory in "mr" is
  // known to be zeroed.
  void initialize(MemRegion mr, bool clear_space, bool mangle_space);

  // The "clear" method must be called on a region that may have
  // had allocation performed in it, but is now to be considered empty.
  void clear(bool mangle_space);

  // Accessors
  HeapWord* top() const            { return _top;    }
  void set_top(HeapWord* value)    { _top = value; }

  // Used to save the space's current top for later use during mangling.
  void set_top_for_allocations() PRODUCT_RETURN;

  // For detecting GC bugs.  Should only be called at GC boundaries, since
  // some unused space may be used as scratch space during GC's.
  // We also call this when expanding a space to satisfy an allocation
  // request. See bug #4668531
  // Mangle regions in the space from the current top up to the
  // previously mangled part of the space.
  void mangle_unused_area() PRODUCT_RETURN;
  // Mangle [top, end)
  void mangle_unused_area_complete() PRODUCT_RETURN;

  // Do some sparse checking on the area that should have been mangled.
  void check_mangled_unused_area(HeapWord* limit) PRODUCT_RETURN;
  // Check the complete area that should have been mangled.
  // This code may be null depending on the macro DEBUG_MANGLING.
  void check_mangled_unused_area_complete() PRODUCT_RETURN;

  MemRegion used_region() const { return MemRegion(bottom(), top()); }

  // Allocation (return null if full).  Assumes the caller has established
  // mutually exclusive access to the space.
  virtual HeapWord* allocate(size_t word_size);
  // Allocation (return null if full).  Enforces mutual exclusion internally.
  virtual HeapWord* par_allocate(size_t word_size);

  // Iteration
  void object_iterate(ObjectClosure* blk);

  // Addresses for inlined allocation
  HeapWord** top_addr() { return &_top; }

  // Debugging
  void verify() const;
};

#endif // SHARE_GC_SHARED_SPACE_HPP
