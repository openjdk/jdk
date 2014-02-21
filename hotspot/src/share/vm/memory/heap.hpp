/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_HEAP_HPP
#define SHARE_VM_MEMORY_HEAP_HPP

#include "memory/allocation.hpp"
#include "runtime/virtualspace.hpp"

// Blocks

class HeapBlock VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;

 public:
  struct Header {
    size_t  _length;                             // the length in segments
    bool    _used;                               // Used bit
  };

 protected:
  union {
    Header _header;
    int64_t _padding[ (sizeof(Header) + sizeof(int64_t)-1) / sizeof(int64_t) ];
                        // pad to 0 mod 8
  };

 public:
  // Initialization
  void initialize(size_t length)                 { _header._length = length; set_used(); }

  // Accessors
  void* allocated_space() const                  { return (void*)(this + 1); }
  size_t length() const                          { return _header._length; }

  // Used/free
  void set_used()                                { _header._used = true; }
  void set_free()                                { _header._used = false; }
  bool free()                                    { return !_header._used; }
};

class FreeBlock: public HeapBlock {
  friend class VMStructs;
 protected:
  FreeBlock* _link;

 public:
  // Initialization
  void initialize(size_t length)             { HeapBlock::initialize(length); _link= NULL; }

  // Merging
  void set_length(size_t l)                  { _header._length = l; }

  // Accessors
  FreeBlock* link() const                    { return _link; }
  void set_link(FreeBlock* link)             { _link = link; }
};

class CodeHeap : public CHeapObj<mtCode> {
  friend class VMStructs;
 private:
  VirtualSpace _memory;                          // the memory holding the blocks
  VirtualSpace _segmap;                          // the memory holding the segment map

  size_t       _number_of_committed_segments;
  size_t       _number_of_reserved_segments;
  size_t       _segment_size;
  int          _log2_segment_size;

  size_t       _next_segment;

  FreeBlock*   _freelist;
  size_t       _freelist_segments;               // No. of segments in freelist

  // Helper functions
  size_t   size_to_segments(size_t size) const { return (size + _segment_size - 1) >> _log2_segment_size; }
  size_t   segments_to_size(size_t number_of_segments) const { return number_of_segments << _log2_segment_size; }

  size_t   segment_for(void* p) const            { return ((char*)p - _memory.low()) >> _log2_segment_size; }
  HeapBlock* block_at(size_t i) const            { return (HeapBlock*)(_memory.low() + (i << _log2_segment_size)); }

  void  mark_segmap_as_free(size_t beg, size_t end);
  void  mark_segmap_as_used(size_t beg, size_t end);

  // Freelist management helpers
  FreeBlock* following_block(FreeBlock *b);
  void insert_after(FreeBlock* a, FreeBlock* b);
  void merge_right (FreeBlock* a);

  // Toplevel freelist management
  void add_to_freelist(HeapBlock *b);
  FreeBlock* search_freelist(size_t length, bool is_critical);

  // Iteration helpers
  void*      next_free(HeapBlock* b) const;
  HeapBlock* first_block() const;
  HeapBlock* next_block(HeapBlock* b) const;
  HeapBlock* block_start(void* p) const;

  // to perform additional actions on creation of executable code
  void on_code_mapping(char* base, size_t size);

 public:
  CodeHeap();

  // Heap extents
  bool  reserve(size_t reserved_size, size_t committed_size, size_t segment_size);
  void  release();                               // releases all allocated memory
  bool  expand_by(size_t size);                  // expands committed memory by size
  void  shrink_by(size_t size);                  // shrinks committed memory by size
  void  clear();                                 // clears all heap contents

  // Memory allocation
  void* allocate  (size_t size, bool is_critical);  // allocates a block of size or returns NULL
  void  deallocate(void* p);                     // deallocates a block

  // Attributes
  char* low_boundary() const                     { return _memory.low_boundary (); }
  char* high() const                             { return _memory.high(); }
  char* high_boundary() const                    { return _memory.high_boundary(); }

  bool  contains(const void* p) const            { return low_boundary() <= p && p < high(); }
  void* find_start(void* p) const;              // returns the block containing p or NULL
  size_t alignment_unit() const;                // alignment of any block
  size_t alignment_offset() const;              // offset of first byte of any block, within the enclosing alignment unit
  static size_t header_size();                  // returns the header size for each heap block

  // Iteration

  // returns the first block or NULL
  void* first() const       { return next_free(first_block()); }
  // returns the next block given a block p or NULL
  void* next(void* p) const { return next_free(next_block(block_start(p))); }

  // Statistics
  size_t capacity() const;
  size_t max_capacity() const;
  size_t allocated_capacity() const;
  size_t unallocated_capacity() const            { return max_capacity() - allocated_capacity(); }

private:
  size_t heap_unallocated_capacity() const;

public:
  // Debugging
  void verify();
  void print()  PRODUCT_RETURN;
};

#endif // SHARE_VM_MEMORY_HEAP_HPP
