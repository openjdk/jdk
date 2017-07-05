/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// A MutableSpace is a subtype of ImmutableSpace that supports the
// concept of allocation. This includes the concepts that a space may
// be only partially full, and the querry methods that go with such
// an assumption.
//
// Invariant: (ImmutableSpace +) bottom() <= top() <= end()
// top() is inclusive and end() is exclusive.

class MutableSpace: public ImmutableSpace {
  friend class VMStructs;
 protected:
  HeapWord* _top;

 public:
  virtual ~MutableSpace()                  {}
  MutableSpace()                           { _top = NULL;    }
  // Accessors
  HeapWord* top() const                    { return _top;    }
  virtual void set_top(HeapWord* value)    { _top = value;   }

  HeapWord** top_addr()                    { return &_top; }
  HeapWord** end_addr()                    { return &_end; }

  virtual void set_bottom(HeapWord* value) { _bottom = value; }
  virtual void set_end(HeapWord* value)    { _end = value; }

  // Returns a subregion containing all objects in this space.
  MemRegion used_region() { return MemRegion(bottom(), top()); }

  // Initialization
  virtual void initialize(MemRegion mr, bool clear_space);
  virtual void clear();
  virtual void update() { }
  virtual void accumulate_statistics() { }

  // Overwrites the unused portion of this space. Note that some collectors
  // may use this "scratch" space during collections.
  virtual void mangle_unused_area() {
    mangle_region(MemRegion(_top, _end));
  }
  virtual void ensure_parsability() { }

  void mangle_region(MemRegion mr) {
    debug_only(Copy::fill_to_words(mr.start(), mr.word_size(), badHeapWord));
  }

  // Boolean querries.
  bool is_empty() const              { return used_in_words() == 0; }
  bool not_empty() const             { return used_in_words() > 0; }
  bool contains(const void* p) const { return _bottom <= p && p < _end; }

  // Size computations.  Sizes are in bytes.
  size_t used_in_bytes() const                { return used_in_words() * HeapWordSize; }
  size_t free_in_bytes() const                { return free_in_words() * HeapWordSize; }

  // Size computations.  Sizes are in heapwords.
  virtual size_t used_in_words() const                    { return pointer_delta(top(), bottom()); }
  virtual size_t free_in_words() const                    { return pointer_delta(end(),    top()); }
  virtual size_t tlab_capacity(Thread* thr) const         { return capacity_in_bytes();            }
  virtual size_t unsafe_max_tlab_alloc(Thread* thr) const { return free_in_bytes();                }

  // Allocation (return NULL if full)
  virtual HeapWord* allocate(size_t word_size);
  virtual HeapWord* cas_allocate(size_t word_size);
  // Optional deallocation. Used in NUMA-allocator.
  bool cas_deallocate(HeapWord *obj, size_t size);

  // Iteration.
  void oop_iterate(OopClosure* cl);
  void object_iterate(ObjectClosure* cl);

  // Debugging
  virtual void print() const;
  virtual void print_on(outputStream* st) const;
  virtual void print_short() const;
  virtual void print_short_on(outputStream* st) const;
  virtual void verify(bool allow_dirty);
};
