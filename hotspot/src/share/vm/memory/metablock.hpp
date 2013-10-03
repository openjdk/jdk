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
#ifndef SHARE_VM_MEMORY_METABLOCK_HPP
#define SHARE_VM_MEMORY_METABLOCK_HPP

// Metablock are the unit of allocation from a Chunk.  It is initialized
// with the size of the requested allocation.  That size is overwritten
// once the allocation returns.
//
// A Metablock may be reused by its SpaceManager but are never moved between
// SpaceManagers.  There is no explicit link to the Metachunk
// from which it was allocated.  Metablock may be deallocated and
// put on a freelist but the space is never freed, rather
// the Metachunk it is a part of will be deallocated when it's
// associated class loader is collected.

class Metablock VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:
  // Used to align the allocation (see below).
  union block_t {
    void* _data[3];
    struct header_t {
      size_t _word_size;
      Metablock* _next;
      Metablock* _prev;
    } _header;
  } _block;
  static size_t _min_block_byte_size;

  typedef union block_t Block;
  typedef struct header_t Header;
  const Block* block() const { return &_block; }
  const Block::header_t* header() const { return &(block()->_header); }
 public:

  static Metablock* initialize(MetaWord* p, size_t word_size);

  // This places the body of the block at a 2 word boundary
  // because every block starts on a 2 word boundary.  Work out
  // how to make the body on a 2 word boundary if the block
  // starts on a arbitrary boundary.  JJJ

  size_t word_size() const  { return header()->_word_size; }
  void set_word_size(size_t v) { _block._header._word_size = v; }
  size_t size() const volatile { return _block._header._word_size; }
  void set_size(size_t v) { _block._header._word_size = v; }
  Metablock* next() const { return header()->_next; }
  void set_next(Metablock* v) { _block._header._next = v; }
  Metablock* prev() const { return header()->_prev; }
  void set_prev(Metablock* v) { _block._header._prev = v; }

  static size_t min_block_byte_size() { return _min_block_byte_size; }

  bool is_free()                 { return header()->_word_size != 0; }
  void clear_next()              { set_next(NULL); }
  void link_prev(Metablock* ptr) { set_prev(ptr); }
  uintptr_t* end()              { return ((uintptr_t*) this) + size(); }
  bool cantCoalesce() const     { return false; }
  void link_next(Metablock* ptr) { set_next(ptr); }
  void link_after(Metablock* ptr){
    link_next(ptr);
    if (ptr != NULL) ptr->link_prev(this);
  }

  // Should not be needed in a free list of Metablocks
  void markNotFree()            { ShouldNotReachHere(); }

  // Debug support
#ifdef ASSERT
  void* prev_addr() const { return (void*)&_block._header._prev; }
  void* next_addr() const { return (void*)&_block._header._next; }
  void* size_addr() const { return (void*)&_block._header._word_size; }
#endif
  bool verify_chunk_in_free_list(Metablock* tc) const { return true; }
  bool verify_par_locked() { return true; }

  void assert_is_mangled() const {/* Don't check "\*/}
};
#endif // SHARE_VM_MEMORY_METABLOCK_HPP
