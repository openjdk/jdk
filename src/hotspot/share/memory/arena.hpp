/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_ARENA_HPP
#define SHARE_MEMORY_ARENA_HPP

#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

#include <new>

//------------------------------Chunk------------------------------------------
// Linked list of raw memory chunks
class Chunk: CHeapObj<mtChunk> {

  // Chunk layout:
  //
  // |-------------|-------------------------------------------------|
  // |           |p|                                                 |
  // |   Chunk   |a|                 payload                         |
  // |  (header) |d|          |                                      |
  // |-------------|----------|--------------------------------------|
  // this         bottom      |                                      top
  //                          _hwm (in arena)
  //
  // Start of Chunk (this), bottom and top have to be aligned to max arena alignment.
  // _hwm does not have to be aligned to anything - it gets aligned on demand
  // at allocation time.
  //
  // Size of Chunk may be unaligned (on 32-bit), therefore we may need to add padding
  // in front of the payload area. Otherwise, we just rely on malloc() returning memory
  // aligned to at least max arena alignment - that takes care of both Chunk start alignment
  // and allocation alignment if +UseMallocOnly.
  //
  // ---
  //
  // Allocation alignment:
  //
  // Allocation from an arena is possible in alignments [1..Chunk::max_arena_alignment].
  //  The arena will automatically align on each allocation if needed, adding padding if
  //  necessary. In debug builds, those paddings will be zapped with a pattern ('GGGG..').
  //

  static const char gap_pattern = 'G';
  static const uint64_t chunk_canary = 0x4152454e41434e4bLL; // "ARENACNK"

  const uint64_t _canary; // A Chunk is preceded by a canary
  Chunk*       _next;     // Next Chunk in list
  const size_t _len;      // Size of the *payload area* of this chunk, guaranteed to be aligned
 public:

  // Maximum possible alignment which can be requested by an arena allocation.
  //  (Note: currently, the implementation relies on this not being larger than
  //   malloc alignment, see UseMallocOnly and Chunk allocation).
  static const size_t max_arena_alignment = BytesPerLong; // 64 bit

  // Given a size, align it to max. alignment
  static size_t max_align(size_t s) { return align_up(s, max_arena_alignment); }

  // Return aligned header size aka payload start offset
  static size_t header_size() { return max_align(sizeof(Chunk)); }

  // Given a (possibly misaligned) payload size, return the total chunk size
  // including header
  static size_t calc_outer_size(size_t payload_size) {
    return header_size() + max_align(payload_size);
  }

  void* operator new(size_t size, AllocFailType alloc_failmode, size_t length) throw();
  void  operator delete(void* p);

  Chunk(size_t length) : _canary(chunk_canary), _next(NULL), _len(max_align(length)) {}

  enum {
    // default sizes; make them slightly smaller than 2**k to guard against
    // buddy-system style malloc implementations
    //
    // Note: standard chunk sizes need to be aligned to max arena alignment.
#ifdef _LP64
    slack      = 40,            // [RGV] Not sure if this is right, but make it
                                //       a multiple of 8.
#else
    slack      = 24,            // suspected sizeof(Chunk) + internal malloc headers
#endif

    tiny_size  =  256  - slack, // Size of first chunk (tiny)
    init_size  =  1*K  - slack, // Size of first chunk (normal aka small)
    medium_size= 10*K  - slack, // Size of medium-sized chunk
    size       = 32*K  - slack, // Default size of an Arena chunk (following the first)
    non_pool_size = init_size + 32 // An initial size which is not one of above
  };

  void chop();                  // Chop this chunk
  void next_chop();             // Chop next chunk

  // Returns size, in bytes, of the *payload* area of this chunk
  size_t length() const         { assert(is_aligned(_len, max_arena_alignment), "misaligned chunk length"); return _len;  }
  Chunk* next() const           { return _next;  }
  void set_next(Chunk* n)       { _next = n;  }

  // Returns size, in bytes, of the total chunk size including header
  size_t outer_size() const     { return calc_outer_size(length()); }
  // Boundaries of data area (possibly unused)
  char* bottom() const          { return ((char*) this) + header_size(); }
  char* top() const             { return bottom() + length(); }
  bool contains(char* p) const  { return bottom() <= p && p <= top(); }

  // Start the chunk_pool cleaner task
  static void start_chunk_pool_cleaner_task();
};

//------------------------------Arena------------------------------------------
// Fast allocation of memory
class Arena : public CHeapObj<mtNone> {
protected:
  friend class HandleMark;
  friend class NoHandleMark;
  friend class VMStructs;

  MEMFLAGS    _flags;           // Memory tracking flags

  Chunk *_first;                // First chunk
  Chunk *_chunk;                // current chunk
  char *_hwm, *_max;            // High water mark and max in current chunk
  // Get a new Chunk of at least size x
  void* grow(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
  size_t _size_in_bytes;        // Size of arena (used for native memory tracking)

  debug_only(void* malloc(size_t size);)

#ifdef ASSERT
  static void zap_alignment_gap(char* p, size_t s) {
    if (ZapResourceArea && s > 0) {
      assert(s < Chunk::max_arena_alignment, "weirdly large gap?");
      ::memset(p, (int)'G', s);
    }
  }
#endif

  void* internal_amalloc(size_t x, size_t alignment,
                         AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    assert(alignment > 0 && alignment <= Chunk::max_arena_alignment, "invalid alignment");
    assert(is_aligned(_max, Chunk::max_arena_alignment), "chunk end misaligned?");
    DEBUG_ONLY(char* old_hwm = _hwm;)
    _hwm = align_up(_hwm, alignment);
    DEBUG_ONLY(zap_alignment_gap(old_hwm, _hwm - old_hwm);)
    if (pointer_delta(_max, _hwm, 1) >= x) {
      char *old = _hwm;
      _hwm += x;
      return old;
    } else {
      return grow(x, alloc_failmode);
    }
  }

 public:
  Arena(MEMFLAGS memflag);
  Arena(MEMFLAGS memflag, size_t init_size);
  ~Arena();
  void  destruct_contents();
  char* hwm() const             { return _hwm; }

  // new operators
  void* operator new (size_t size) throw();
  void* operator new (size_t size, const std::nothrow_t& nothrow_constant) throw();

  // dynamic memory type tagging
  void* operator new(size_t size, MEMFLAGS flags) throw();
  void* operator new(size_t size, const std::nothrow_t& nothrow_constant, MEMFLAGS flags) throw();
  void  operator delete(void* p);

  // Allocate n bytes with a manually specified alignment (<= max arena alignment).
  void* Amalloc_aligned(size_t n, size_t alignment, AllocFailType failmode = AllocFailStrategy::EXIT_OOM) {
    debug_only(if (UseMallocOnly) return malloc(n);)
    return internal_amalloc(n, alignment, failmode);
  }

  // Allocate n bytes with 64-bit alignment
  void* Amalloc64(size_t n, AllocFailType failmode = AllocFailStrategy::EXIT_OOM) {
    return Amalloc_aligned(n, sizeof(uint64_t), failmode);
  }

  // Allocate n bytes with 32-bit alignment
  void* Amalloc32(size_t n, AllocFailType failmode = AllocFailStrategy::EXIT_OOM) {
    return Amalloc_aligned(n, sizeof(uint32_t), failmode);
  }

  // Allocate n bytes with default alignment (which is 64 bit on both 32/64-bit platforms)
  void* Amalloc(size_t n, AllocFailType failmode = AllocFailStrategy::EXIT_OOM) {
    return Amalloc64(n, failmode);
  }

  // Allocate n bytes with word alignment (32/64 bits on 32/64 bit platform)
  void* AmallocWords(size_t n, AllocFailType failmode = AllocFailStrategy::EXIT_OOM) {
    return Amalloc_aligned(n, sizeof(void*), failmode);
  }

  // Fast delete in area.  Common case is: NOP (except for storage reclaimed)
  bool Afree(void *ptr, size_t size) {
    if (ptr == NULL) {
      return true; // as with free(3), freeing NULL is a noop.
    }
#ifdef ASSERT
    if (ZapResourceArea) memset(ptr, badResourceValue, size); // zap freed memory
    if (UseMallocOnly) return true;
#endif
    if (((char*)ptr) + size == _hwm) {
      _hwm = (char*)ptr;
      return true;
    } else {
      // Unable to fast free, so we just drop it.
      return false;
    }
  }

  // Reallocate; the returned pointer is guaranteed to be aligned to the original alignment.
  // Note that this is a potentially wasteful operation since the old allocation may just leak.
  void *Arealloc( void *old_ptr, size_t old_size, size_t new_size,
      AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);

  // Move contents of this arena into an empty arena
  Arena *move_contents(Arena *empty_arena);

  // Determine if pointer belongs to this Arena or not.
  bool contains( const void *ptr ) const;

  // Total of all chunks in use (not thread-safe)
  size_t used() const;

  // Total # of bytes used
  size_t size_in_bytes() const         {  return _size_in_bytes; };
  void set_size_in_bytes(size_t size);

  static void free_malloced_objects(Chunk* chunk, char* hwm, char* max, char* hwm2)  PRODUCT_RETURN;
  static void free_all(char** start, char** end)                                     PRODUCT_RETURN;

private:
  // Reset this Arena to empty, access will trigger grow if necessary
  void   reset(void) {
    _first = _chunk = NULL;
    _hwm = _max = NULL;
    set_size_in_bytes(0);
  }
};

// One of the following macros must be used when allocating
// an array or object from an arena
#define NEW_ARENA_ARRAY(arena, type, size) \
  (type*) (arena)->Amalloc((size) * sizeof(type))

#define REALLOC_ARENA_ARRAY(arena, type, old, old_size, new_size)    \
  (type*) (arena)->Arealloc((char*)(old), (old_size) * sizeof(type), \
                            (new_size) * sizeof(type) )

#define FREE_ARENA_ARRAY(arena, type, old, size) \
  (arena)->Afree((char*)(old), (size) * sizeof(type))

#define NEW_ARENA_OBJ(arena, type) \
  NEW_ARENA_ARRAY(arena, type, 1)

#endif // SHARE_MEMORY_ARENA_HPP
