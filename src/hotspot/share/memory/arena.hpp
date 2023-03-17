/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

#include <cstddef>
#include <new>

// The byte alignment to be used by Arena::Amalloc.
#define ARENA_AMALLOC_ALIGNMENT ((size_t) BytesPerLong)
#define ARENA_ALIGN(x) (align_up((size_t) (x), ARENA_AMALLOC_ALIGNMENT))

enum class ChunkPoolSize : uintptr_t {
  NONE = 0,

  TINY = 1,
  SMALL = 2,
  MEDIUM = 3,
  LARGE = 4,
};

constexpr uintptr_t chunk_pool_size_bits = uintptr_t{8} - 1;
constexpr uintptr_t chunk_pool_size_mask = ~chunk_pool_size_bits;

class ChunkPool;

//------------------------------Chunk------------------------------------------
// Linked list of raw memory chunks
class Chunk final {
 private:
  // Ensure natural malloc alignment has enough bits for us to use in _next.
  STATIC_ASSERT(alignof(std::max_align_t) >= 8);

  friend class ChunkPool;

  uintptr_t _next;    // Next Chunk in list. The lower 3 bits encode ChunkPoolSize.
  const size_t _len;  // Unaligned size of this Chunk, not including Chunk itself.

  uintptr_t raw_pool_size() const {
    return _next & chunk_pool_size_bits;
  }

  uintptr_t raw_next() const {
    return _next & chunk_pool_size_mask;
  }

  size_t raw_length() const { return _len; }

  static Chunk* allocate(size_t length, AllocFailType alloc_failmode);

  void deallocate();

  static constexpr size_t slack =
#ifdef _LP64
    40  // [RGV] Not sure if this is right, but make it a multiple of 8.
#else
    24  // suspected sizeof(Chunk) + internal malloc headers
#endif
    ;

  Chunk(size_t length, ChunkPoolSize pool_size);

  NONCOPYABLE(Chunk);

 public:
  static Chunk* acquire(size_t length, AllocFailType alloc_failmode);

  void release();

  // default sizes; make them slightly smaller than 2**k to guard against
  // buddy-system style malloc implementations
  // Note: please keep these constants 64-bit aligned.
  static constexpr size_t tiny_size = 256 - slack;        // Size of first chunk (tiny)
  static constexpr size_t small_size = 1*K - slack;       // Size of first chunk (normal aka small)
  static constexpr size_t init_size = small_size;
  static constexpr size_t medium_size = 10*K - slack;     // Size of medium-sized chunk
  static constexpr size_t large_size = 32*K - slack;    // Large size of an Arena chunk (following the first)
  static constexpr size_t size = large_size;
  static constexpr size_t non_pool_size = init_size + 32; // An initial size which is not one of above

  ChunkPoolSize pool_size() const {
    return static_cast<ChunkPoolSize>(raw_pool_size());
  }
  static ChunkPoolSize pool_size(size_t length) {
    switch (length) {
      case tiny_size:
        return ChunkPoolSize::TINY;
      case small_size:
        return ChunkPoolSize::SMALL;
      case medium_size:
        return ChunkPoolSize::MEDIUM;
      case large_size:
        return ChunkPoolSize::LARGE;
      default:
        return ChunkPoolSize::NONE;
    }
  }

  // Release this chunk and all subsequent chunks.
  void chop();

  // Release all subsequent chunks.
  void next_chop();

  static size_t aligned_overhead_size(size_t byte_size) { return ARENA_ALIGN(byte_size); }
  static size_t aligned_overhead_size() { return aligned_overhead_size(sizeof(Chunk)); }

  size_t length() const { return align_down(raw_length(), ARENA_AMALLOC_ALIGNMENT); }

  Chunk* next() const { return reinterpret_cast<Chunk*>(raw_next()); }

  void set_next(Chunk* next) {
    assert(next == nullptr || is_aligned(next, alignof(std::max_align_t)), "chunk misaligned");
    _next = reinterpret_cast<uintptr_t>(next) | raw_pool_size();
  }

  char* bottom() const {
    return reinterpret_cast<char*>(reinterpret_cast<uintptr_t>(this) + aligned_overhead_size());
  }

  char* top() const { return bottom() + length(); }

  bool contains(const void* p) const {
    return bottom() <= static_cast<const char*>(p) && static_cast<const char*>(p) <= top();
  }

  // Start the chunk_pool cleaner task. Must only be called once.
  static void start_chunk_pool_cleaner_task();

  // Chunks must be acquired and released using acquire() and release().
  static void* operator new(size_t size) = delete;
  static void* operator new[](size_t size) = delete;
  static void operator delete(void* p) = delete;
  static void operator delete[](void* p) = delete;
  static void operator delete(void* p, size_t size) = delete;
  static void operator delete[](void* p, size_t size) = delete;
};

//------------------------------Arena------------------------------------------
// Fast allocation of memory
class Arena : public CHeapObjBase {
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

  void* internal_amalloc(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM)  {
    assert(is_aligned(x, BytesPerWord), "misaligned size");
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

  // Fast allocate in the arena.  Common case aligns to the size of jlong which is 64 bits
  // on both 32 and 64 bit platforms. Required for atomic jlong operations on 32 bits.
  void* Amalloc(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    x = ARENA_ALIGN(x);  // note for 32 bits this should align _hwm as well.
    // Amalloc guarantees 64-bit alignment and we need to ensure that in case the preceding
    // allocation was AmallocWords. Only needed on 32-bit - on 64-bit Amalloc and AmallocWords are
    // identical.
    assert(is_aligned(_max, ARENA_AMALLOC_ALIGNMENT), "chunk end unaligned?");
    NOT_LP64(_hwm = ARENA_ALIGN(_hwm));
    return internal_amalloc(x, alloc_failmode);
  }

  // Allocate in the arena, assuming the size has been aligned to size of pointer, which
  // is 4 bytes on 32 bits, hence the name.
  void* AmallocWords(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    assert(is_aligned(x, BytesPerWord), "misaligned size");
    return internal_amalloc(x, alloc_failmode);
  }

  // Fast delete in area.  Common case is: NOP (except for storage reclaimed)
  bool Afree(void *ptr, size_t size) {
    if (ptr == nullptr) {
      return true; // as with free(3), freeing null is a noop.
    }
#ifdef ASSERT
    if (ZapResourceArea) memset(ptr, badResourceValue, size); // zap freed memory
#endif
    if (((char*)ptr) + size == _hwm) {
      _hwm = (char*)ptr;
      return true;
    } else {
      // Unable to fast free, so we just drop it.
      return false;
    }
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

private:
  // Reset this Arena to empty, access will trigger grow if necessary
  void   reset(void) {
    _first = _chunk = nullptr;
    _hwm = _max = nullptr;
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
