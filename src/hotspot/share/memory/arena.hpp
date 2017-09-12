/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_ARENA_HPP
#define SHARE_VM_ARENA_HPP

#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"

#include <new>

// The byte alignment to be used by Arena::Amalloc.  See bugid 4169348.
// Note: this value must be a power of 2

#define ARENA_AMALLOC_ALIGNMENT (2*BytesPerWord)

#define ARENA_ALIGN_M1 (((size_t)(ARENA_AMALLOC_ALIGNMENT)) - 1)
#define ARENA_ALIGN_MASK (~((size_t)ARENA_ALIGN_M1))
#define ARENA_ALIGN(x) ((((size_t)(x)) + ARENA_ALIGN_M1) & ARENA_ALIGN_MASK)

//------------------------------Chunk------------------------------------------
// Linked list of raw memory chunks
class Chunk: CHeapObj<mtChunk> {

 private:
  Chunk*       _next;     // Next Chunk in list
  const size_t _len;      // Size of this Chunk
 public:
  void* operator new(size_t size, AllocFailType alloc_failmode, size_t length) throw();
  void  operator delete(void* p);
  Chunk(size_t length);

  enum {
    // default sizes; make them slightly smaller than 2**k to guard against
    // buddy-system style malloc implementations
#ifdef _LP64
    slack      = 40,            // [RGV] Not sure if this is right, but make it
                                //       a multiple of 8.
#else
    slack      = 20,            // suspected sizeof(Chunk) + internal malloc headers
#endif

    tiny_size  =  256  - slack, // Size of first chunk (tiny)
    init_size  =  1*K  - slack, // Size of first chunk (normal aka small)
    medium_size= 10*K  - slack, // Size of medium-sized chunk
    size       = 32*K  - slack, // Default size of an Arena chunk (following the first)
    non_pool_size = init_size + 32 // An initial size which is not one of above
  };

  void chop();                  // Chop this chunk
  void next_chop();             // Chop next chunk
  static size_t aligned_overhead_size(void) { return ARENA_ALIGN(sizeof(Chunk)); }
  static size_t aligned_overhead_size(size_t byte_size) { return ARENA_ALIGN(byte_size); }

  size_t length() const         { return _len;  }
  Chunk* next() const           { return _next;  }
  void set_next(Chunk* n)       { _next = n;  }
  // Boundaries of data area (possibly unused)
  char* bottom() const          { return ((char*) this) + aligned_overhead_size();  }
  char* top()    const          { return bottom() + _len; }
  bool contains(char* p) const  { return bottom() <= p && p <= top(); }

  // Start the chunk_pool cleaner task
  static void start_chunk_pool_cleaner_task();

  static void clean_chunk_pool();
};

//------------------------------Arena------------------------------------------
// Fast allocation of memory
class Arena : public CHeapObj<mtNone> {
protected:
  friend class ResourceMark;
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

  NOT_PRODUCT(static julong _bytes_allocated;) // total #bytes allocated since start
  friend class AllocStats;
  debug_only(void* malloc(size_t size);)
  debug_only(void* internal_malloc_4(size_t x);)
  NOT_PRODUCT(void inc_bytes_allocated(size_t x);)

  void signal_out_of_memory(size_t request, const char* whence) const;

  bool check_for_overflow(size_t request, const char* whence,
      AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) const {
    if (UINTPTR_MAX - request < (uintptr_t)_hwm) {
      if (alloc_failmode == AllocFailStrategy::RETURN_NULL) {
        return false;
      }
      signal_out_of_memory(request, whence);
    }
    return true;
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

  // Fast allocate in the arena.  Common case is: pointer test + increment.
  void* Amalloc(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    assert(is_power_of_2(ARENA_AMALLOC_ALIGNMENT) , "should be a power of 2");
    x = ARENA_ALIGN(x);
    debug_only(if (UseMallocOnly) return malloc(x);)
    if (!check_for_overflow(x, "Arena::Amalloc", alloc_failmode))
      return NULL;
    NOT_PRODUCT(inc_bytes_allocated(x);)
    if (_hwm + x > _max) {
      return grow(x, alloc_failmode);
    } else {
      char *old = _hwm;
      _hwm += x;
      return old;
    }
  }
  // Further assume size is padded out to words
  void *Amalloc_4(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    assert( (x&(sizeof(char*)-1)) == 0, "misaligned size" );
    debug_only(if (UseMallocOnly) return malloc(x);)
    if (!check_for_overflow(x, "Arena::Amalloc_4", alloc_failmode))
      return NULL;
    NOT_PRODUCT(inc_bytes_allocated(x);)
    if (_hwm + x > _max) {
      return grow(x, alloc_failmode);
    } else {
      char *old = _hwm;
      _hwm += x;
      return old;
    }
  }

  // Allocate with 'double' alignment. It is 8 bytes on sparc.
  // In other cases Amalloc_D() should be the same as Amalloc_4().
  void* Amalloc_D(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    assert( (x&(sizeof(char*)-1)) == 0, "misaligned size" );
    debug_only(if (UseMallocOnly) return malloc(x);)
#if defined(SPARC) && !defined(_LP64)
#define DALIGN_M1 7
    size_t delta = (((size_t)_hwm + DALIGN_M1) & ~DALIGN_M1) - (size_t)_hwm;
    x += delta;
#endif
    if (!check_for_overflow(x, "Arena::Amalloc_D", alloc_failmode))
      return NULL;
    NOT_PRODUCT(inc_bytes_allocated(x);)
    if (_hwm + x > _max) {
      return grow(x, alloc_failmode); // grow() returns a result aligned >= 8 bytes.
    } else {
      char *old = _hwm;
      _hwm += x;
#if defined(SPARC) && !defined(_LP64)
      old += delta; // align to 8-bytes
#endif
      return old;
    }
  }

  // Fast delete in area.  Common case is: NOP (except for storage reclaimed)
  void Afree(void *ptr, size_t size) {
#ifdef ASSERT
    if (ZapResourceArea) memset(ptr, badResourceValue, size); // zap freed memory
    if (UseMallocOnly) return;
#endif
    if (((char*)ptr) + size == _hwm) _hwm = (char*)ptr;
  }

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

#endif // SHARE_VM_ARENA_HPP
