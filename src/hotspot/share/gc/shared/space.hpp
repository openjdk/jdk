/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
class Space;
class ContiguousSpace;
class Generation;
class ContiguousSpace;
class CardTableRS;
class DirtyCardToOopClosure;

// A Space describes a heap area. Class Space is an abstract
// base class.
//
// Space supports allocation, size computation and GC support is provided.
//
// Invariant: bottom() and end() are on page_size boundaries and
// bottom() <= top() <= end()
// top() is inclusive and end() is exclusive.

class Space: public CHeapObj<mtGC> {
  friend class VMStructs;
 protected:
  HeapWord* _bottom;
  HeapWord* _end;

  // Used in support of save_marks()
  HeapWord* _saved_mark_word;

  Space():
    _bottom(nullptr), _end(nullptr) { }

 public:
  // Accessors
  HeapWord* bottom() const         { return _bottom; }
  HeapWord* end() const            { return _end;    }
  virtual void set_bottom(HeapWord* value) { _bottom = value; }
  virtual void set_end(HeapWord* value)    { _end = value; }

  HeapWord* saved_mark_word() const  { return _saved_mark_word; }

  void set_saved_mark_word(HeapWord* p) { _saved_mark_word = p; }

  // Returns a subregion of the space containing only the allocated objects in
  // the space.
  virtual MemRegion used_region() const = 0;

  // Returns a region that is guaranteed to contain (at least) all objects
  // allocated at the time of the last call to "save_marks".  If the space
  // initializes its DirtyCardToOopClosure's specifying the "contig" option
  // (that is, if the space is contiguous), then this region must contain only
  // such objects: the memregion will be from the bottom of the region to the
  // saved mark.  Otherwise, the "obj_allocated_since_save_marks" method of
  // the space must distinguish between objects in the region allocated before
  // and after the call to save marks.
  MemRegion used_region_at_save_marks() const {
    return MemRegion(bottom(), saved_mark_word());
  }

  // For detecting GC bugs.  Should only be called at GC boundaries, since
  // some unused space may be used as scratch space during GC's.
  // We also call this when expanding a space to satisfy an allocation
  // request. See bug #4668531
  virtual void mangle_unused_area() = 0;
  virtual void mangle_unused_area_complete() = 0;

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
  bool is_in(oop obj) const {
    return is_in((void*)obj);
  }

  // Returns true iff the given reserved memory of the space contains the
  // given address.
  bool is_in_reserved(const void* p) const { return _bottom <= p && p < _end; }

  // Test whether p is double-aligned
  static bool is_aligned(void* p) {
    return ::is_aligned(p, sizeof(double));
  }

  // Size computations.  Sizes are in bytes.
  size_t capacity()     const { return byte_size(bottom(), end()); }
  virtual size_t used() const = 0;
  virtual size_t free() const = 0;

  // Iterate over all objects in the space, calling "cl.do_object" on
  // each.  Objects allocated by applications of the closure are not
  // included in the iteration.
  virtual void object_iterate(ObjectClosure* blk) = 0;

  // If "p" is in the space, returns the address of the start of the
  // "block" that contains "p".  We say "block" instead of "object" since
  // some heaps may not pack objects densely; a chunk may either be an
  // object or a non-object.  If "p" is not in the space, return null.
  virtual HeapWord* block_start_const(const void* p) const = 0;

  // The non-const version may have benevolent side effects on the data
  // structure supporting these calls, possibly speeding up future calls.
  // The default implementation, however, is simply to call the const
  // version.
  HeapWord* block_start(const void* p);

  // Requires "addr" to be the start of a chunk, and returns its size.
  // "addr + size" is required to be the start of a new chunk, or the end
  // of the active area of the heap.
  virtual size_t block_size(const HeapWord* addr) const = 0;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object.
  virtual bool block_is_obj(const HeapWord* addr) const = 0;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object and the object is alive.
  bool obj_is_alive(const HeapWord* addr) const;

  // Allocation (return null if full).  Assumes the caller has established
  // mutually exclusive access to the space.
  virtual HeapWord* allocate(size_t word_size) = 0;

  // Allocation (return null if full).  Enforces mutual exclusion internally.
  virtual HeapWord* par_allocate(size_t word_size) = 0;

  void print() const;
  virtual void print_on(outputStream* st) const;
  void print_short() const;
  void print_short_on(outputStream* st) const;
};

class GenSpaceMangler;

// A space in which the free area is contiguous.  It therefore supports
// faster allocation, and compaction.
class ContiguousSpace: public Space {
  friend class VMStructs;

private:
  ContiguousSpace* _next_compaction_space;

protected:
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

  // Initialization.
  // "initialize" should be called once on a space, before it is used for
  // any purpose.  The "mr" arguments gives the bounds of the space, and
  // the "clear_space" argument should be true unless the memory in "mr" is
  // known to be zeroed.
  void initialize(MemRegion mr, bool clear_space, bool mangle_space);

  // The "clear" method must be called on a region that may have
  // had allocation performed in it, but is now to be considered empty.
  void clear(bool mangle_space);

  // Returns the next space (in the current generation) to be compacted in
  // the global compaction order.  Also is used to select the next
  // space into which to compact.

  ContiguousSpace* next_compaction_space() const {
    return _next_compaction_space;
  }

  void set_next_compaction_space(ContiguousSpace* csp) {
    _next_compaction_space = csp;
  }

  // The maximum percentage of objects that can be dead in the compacted
  // live part of a compacted space ("deadwood" support.)
  virtual size_t allowed_dead_ratio() const { return 0; };

  // Accessors
  HeapWord* top() const            { return _top;    }
  void set_top(HeapWord* value)    { _top = value; }

  void set_saved_mark()            { _saved_mark_word = top();    }

  bool saved_mark_at_top() const { return saved_mark_word() == top(); }

  // In debug mode mangle (write it with a particular bit
  // pattern) the unused part of a space.

  // Used to save the address in a space for later use during mangling.
  void set_top_for_allocations(HeapWord* v) PRODUCT_RETURN;
  // Used to save the space's current top for later use during mangling.
  void set_top_for_allocations() PRODUCT_RETURN;

  // Mangle regions in the space from the current top up to the
  // previously mangled part of the space.
  void mangle_unused_area() override PRODUCT_RETURN;
  // Mangle [top, end)
  void mangle_unused_area_complete() override PRODUCT_RETURN;

  // Do some sparse checking on the area that should have been mangled.
  void check_mangled_unused_area(HeapWord* limit) PRODUCT_RETURN;
  // Check the complete area that should have been mangled.
  // This code may be null depending on the macro DEBUG_MANGLING.
  void check_mangled_unused_area_complete() PRODUCT_RETURN;

  // Size computations: sizes in bytes.
  size_t used() const override   { return byte_size(bottom(), top()); }
  size_t free() const override   { return byte_size(top(),    end()); }

  // In a contiguous space we have a more obvious bound on what parts
  // contain objects.
  MemRegion used_region() const override { return MemRegion(bottom(), top()); }

  // Allocation (return null if full)
  HeapWord* allocate(size_t word_size) override;
  HeapWord* par_allocate(size_t word_size) override;

  // Iteration
  void object_iterate(ObjectClosure* blk) override;

  // Apply "blk->do_oop" to the addresses of all reference fields in objects
  // starting with the _saved_mark_word, which was noted during a generation's
  // save_marks and is required to denote the head of an object.
  // Fields in objects allocated by applications of the closure
  // *are* included in the iteration.
  // Updates _saved_mark_word to point to just after the last object
  // iterated over.
  template <typename OopClosureType>
  void oop_since_save_marks_iterate(OopClosureType* blk);

  // Same as object_iterate, but starting from "mark", which is required
  // to denote the start of an object.  Objects allocated by
  // applications of the closure *are* included in the iteration.
  virtual void object_iterate_from(HeapWord* mark, ObjectClosure* blk);

  // Very inefficient implementation.
  HeapWord* block_start_const(const void* p) const override;
  size_t block_size(const HeapWord* p) const override;
  // If a block is in the allocated area, it is an object.
  bool block_is_obj(const HeapWord* p) const override { return p < top(); }

  // Addresses for inlined allocation
  HeapWord** top_addr() { return &_top; }

  void print_on(outputStream* st) const override;

  // Debugging
  void verify() const;
};

#if INCLUDE_SERIALGC

// Class TenuredSpace is used by TenuredGeneration; it supports an efficient
// "block_start" operation via a SerialBlockOffsetTable.

class TenuredSpace: public ContiguousSpace {
  friend class VMStructs;
 protected:
  SerialBlockOffsetTable _offsets;

  // Mark sweep support
  size_t allowed_dead_ratio() const override;
 public:
  // Constructor
  TenuredSpace(SerialBlockOffsetSharedArray* sharedOffsetArray,
               MemRegion mr);

  HeapWord* block_start_const(const void* addr) const override;

  // Add offset table update.
  inline HeapWord* allocate(size_t word_size) override;
  inline HeapWord* par_allocate(size_t word_size) override;

  inline void update_for_block(HeapWord* start, HeapWord* end);

  void print_on(outputStream* st) const override;
};
#endif //INCLUDE_SERIALGC

#endif // SHARE_GC_SHARED_SPACE_HPP
