/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class VirtualSpaceNode;

// Super class of Metablock and Metachunk to allow them to
// be put on the FreeList and in the BinaryTreeDictionary.
template <class T>
class Metabase VALUE_OBJ_CLASS_SPEC {
  size_t _word_size;
  T*     _next;
  T*     _prev;

 protected:
  Metabase(size_t word_size) : _word_size(word_size), _next(NULL), _prev(NULL) {}

 public:
  T* next() const         { return _next; }
  T* prev() const         { return _prev; }
  void set_next(T* v)     { _next = v; assert(v != this, "Boom");}
  void set_prev(T* v)     { _prev = v; assert(v != this, "Boom");}
  void clear_next()       { set_next(NULL); }
  void clear_prev()       { set_prev(NULL); }

  size_t size() const volatile { return _word_size; }
  void set_size(size_t v) { _word_size = v; }

  void link_next(T* ptr)  { set_next(ptr); }
  void link_prev(T* ptr)  { set_prev(ptr); }
  void link_after(T* ptr) {
    link_next(ptr);
    if (ptr != NULL) ptr->link_prev((T*)this);
  }

  uintptr_t* end() const        { return ((uintptr_t*) this) + size(); }

  bool cantCoalesce() const     { return false; }

  // Debug support
#ifdef ASSERT
  void* prev_addr() const { return (void*)&_prev; }
  void* next_addr() const { return (void*)&_next; }
  void* size_addr() const { return (void*)&_word_size; }
#endif
  bool verify_chunk_in_free_list(T* tc) const { return true; }
  bool verify_par_locked() { return true; }

  void assert_is_mangled() const {/* Don't check "\*/}

  bool is_free()                 { return true; }
};

//  Metachunk - Quantum of allocation from a Virtualspace
//    Metachunks are reused (when freed are put on a global freelist) and
//    have no permanent association to a SpaceManager.

//            +--------------+ <- end    --+       --+
//            |              |             |         |
//            |              |             | free    |
//            |              |             |         |
//            |              |             |         | size | capacity
//            |              |             |         |
//            |              | <- top   -- +         |
//            |              |             |         |
//            |              |             | used    |
//            |              |             |         |
//            |              |             |         |
//            +--------------+ <- bottom --+       --+

class Metachunk : public Metabase<Metachunk> {
  friend class TestMetachunk;
  // The VirtualSpaceNode containing this chunk.
  VirtualSpaceNode* _container;

  // Current allocation top.
  MetaWord* _top;

  DEBUG_ONLY(bool _is_tagged_free;)

  MetaWord* initial_top() const { return (MetaWord*)this + overhead(); }
  MetaWord* top() const         { return _top; }

 public:
  // Metachunks are allocated out of a MetadataVirtualSpace and
  // and use some of its space to describe itself (plus alignment
  // considerations).  Metadata is allocated in the rest of the chunk.
  // This size is the overhead of maintaining the Metachunk within
  // the space.

  // Alignment of each allocation in the chunks.
  static size_t object_alignment();

  // Size of the Metachunk header, including alignment.
  static size_t overhead();

  Metachunk(size_t word_size , VirtualSpaceNode* container);

  MetaWord* allocate(size_t word_size);

  VirtualSpaceNode* container() const { return _container; }

  MetaWord* bottom() const { return (MetaWord*) this; }

  // Reset top to bottom so chunk can be reused.
  void reset_empty() { _top = initial_top(); clear_next(); clear_prev(); }
  bool is_empty() { return _top == initial_top(); }

  // used (has been allocated)
  // free (available for future allocations)
  size_t word_size() const { return size(); }
  size_t used_word_size() const;
  size_t free_word_size() const;

#ifdef ASSERT
  void mangle();
  bool is_tagged_free() { return _is_tagged_free; }
  void set_is_tagged_free(bool v) { _is_tagged_free = v; }
#endif

  void print_on(outputStream* st) const;
  void verify();
};

// Metablock is the unit of allocation from a Chunk.
//
// A Metablock may be reused by its SpaceManager but are never moved between
// SpaceManagers.  There is no explicit link to the Metachunk
// from which it was allocated.  Metablock may be deallocated and
// put on a freelist but the space is never freed, rather
// the Metachunk it is a part of will be deallocated when it's
// associated class loader is collected.

class Metablock : public Metabase<Metablock> {
  friend class VMStructs;
 public:
  Metablock(size_t word_size) : Metabase<Metablock>(word_size) {}
};

#endif  // SHARE_VM_MEMORY_METACHUNK_HPP
