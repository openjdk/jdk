/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_MEMORY_METACHUNK_HPP
#define SHARE_VM_MEMORY_METACHUNK_HPP

//  Metachunk - Quantum of allocation from a Virtualspace
//    Metachunks are reused (when freed are put on a global freelist) and
//    have no permanent association to a SpaceManager.

//            +--------------+ <- end
//            |              |          --+       ---+
//            |              |            | free     |
//            |              |            |          |
//            |              |            |          | capacity
//            |              |            |          |
//            |              | <- top   --+          |
//            |              |           ---+        |
//            |              |              | used   |
//            |              |              |        |
//            |              |              |        |
//            +--------------+ <- bottom ---+     ---+

class Metachunk VALUE_OBJ_CLASS_SPEC {
  // link to support lists of chunks
  Metachunk* _next;
  Metachunk* _prev;

  MetaWord* _bottom;
  MetaWord* _end;
  MetaWord* _top;
  size_t _word_size;
  // Used in a guarantee() so included in the Product builds
  // even through it is only for debugging.
  bool _is_free;

  // Metachunks are allocated out of a MetadataVirtualSpace and
  // and use some of its space to describe itself (plus alignment
  // considerations).  Metadata is allocated in the rest of the chunk.
  // This size is the overhead of maintaining the Metachunk within
  // the space.
  static size_t _overhead;

  void set_bottom(MetaWord* v) { _bottom = v; }
  void set_end(MetaWord* v) { _end = v; }
  void set_top(MetaWord* v) { _top = v; }
  void set_word_size(size_t v) { _word_size = v; }
 public:
#ifdef ASSERT
  Metachunk() : _bottom(NULL), _end(NULL), _top(NULL), _is_free(false) {}
#else
  Metachunk() : _bottom(NULL), _end(NULL), _top(NULL) {}
#endif

  // Used to add a Metachunk to a list of Metachunks
  void set_next(Metachunk* v) { _next = v; assert(v != this, "Boom");}
  void set_prev(Metachunk* v) { _prev = v; assert(v != this, "Boom");}

  MetaWord* allocate(size_t word_size);
  static Metachunk* initialize(MetaWord* ptr, size_t word_size);

  // Accessors
  Metachunk* next() const { return _next; }
  Metachunk* prev() const { return _prev; }
  MetaWord* bottom() const { return _bottom; }
  MetaWord* end() const { return _end; }
  MetaWord* top() const { return _top; }
  size_t word_size() const { return _word_size; }
  size_t size() const volatile { return _word_size; }
  void set_size(size_t v) { _word_size = v; }
  bool is_free() { return _is_free; }
  void set_is_free(bool v) { _is_free = v; }
  static size_t overhead() { return _overhead; }
  void clear_next()              { set_next(NULL); }
  void link_prev(Metachunk* ptr) { set_prev(ptr); }
  uintptr_t* end()              { return ((uintptr_t*) this) + size(); }
  bool cantCoalesce() const     { return false; }
  void link_next(Metachunk* ptr) { set_next(ptr); }
  void link_after(Metachunk* ptr){
    link_next(ptr);
    if (ptr != NULL) ptr->link_prev(this);
  }

  // Reset top to bottom so chunk can be reused.
  void reset_empty() { _top = (_bottom + _overhead); }
  bool is_empty() { return _top == (_bottom + _overhead); }

  // used (has been allocated)
  // free (available for future allocations)
  // capacity (total size of chunk)
  size_t used_word_size();
  size_t free_word_size();
  size_t capacity_word_size();

  // Debug support
#ifdef ASSERT
  void* prev_addr() const { return (void*)&_prev; }
  void* next_addr() const { return (void*)&_next; }
  void* size_addr() const { return (void*)&_word_size; }
#endif
  bool verify_chunk_in_free_list(Metachunk* tc) const { return true; }
  bool verify_par_locked() { return true; }

  void assert_is_mangled() const {/* Don't check "\*/}

  NOT_PRODUCT(void mangle();)

  void print_on(outputStream* st) const;
  void verify();
};
#endif  // SHARE_VM_MEMORY_METACHUNK_HPP
