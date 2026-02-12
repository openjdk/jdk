/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

// The byte alignment to be used by Arena::Amalloc.
#define ARENA_AMALLOC_ALIGNMENT BytesPerLong
#define ARENA_ALIGN(x) (align_up((x), ARENA_AMALLOC_ALIGNMENT))

class ChunkPoolLocker : public StackObj {
  bool _locked;
 public:
  enum class LockStrategy { Lock, Try };

  ChunkPoolLocker(LockStrategy ls = LockStrategy::Lock);
  ~ChunkPoolLocker();
};

// Linked list of raw memory chunks
class Chunk {

 private:
  Chunk*       _next;     // Next Chunk in list
  const size_t _len;      // Size of this Chunk
  // Used for Compilation Memory Statistic
  uint64_t _stamp;

public:
  NONCOPYABLE(Chunk);

  void operator delete(void*) = delete;
  void* operator new(size_t) = delete;

  Chunk(size_t length);

  enum {
    // default sizes; make them slightly smaller than 2**k to guard against
    // buddy-system style malloc implementations
    // Note: please keep these constants 64-bit aligned.
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
    max_default_size = size     // Largest default size
  };

  static void chop(Chunk* chunk);                  // Chop this chunk
  static void next_chop(Chunk* chunk);             // Chop next chunk
  static size_t aligned_overhead_size(void) { return ARENA_ALIGN(sizeof(Chunk)); }
  static size_t aligned_overhead_size(size_t byte_size) { return ARENA_ALIGN(byte_size); }

  size_t length() const         { return _len;  }
  Chunk* next() const           { return _next;  }
  void set_next(Chunk* n)       { _next = n;  }
  // Boundaries of data area (possibly unused)
  char* bottom() const          { return ((char*) this) + aligned_overhead_size();  }
  char* top()    const          { return bottom() + _len; }
  bool contains(char* p) const  { return bottom() <= p && p <= top(); }

  void set_stamp(uint64_t v) { _stamp = v; }
  uint64_t stamp() const     { return _stamp; }
};

// Arena types (for Compilation Memory Statistic)
#define DO_ARENA_TAG(FN) \
  FN(ra,          Resource areas) \
  FN(node,        C2 Node arena) \
  FN(comp,        C2 Compile arena) \
  FN(idealloop,   C2 Ideal Loop arena) \
  FN(type,        C2 Type arena) \
  FN(states,      C2 Matcher States Arena) \
  FN(reglive,     C2 Register Allocation Live Arenas) \
  FN(regsplit,    C2 Register Allocation Split Arena) \
  FN(regmask,     C2 Short-Lived Register Mask Arena) \
  FN(superword,   C2 SuperWord Arenas) \
  FN(cienv,       CI Env Arena) \
  FN(ha,          Handle area) \
  FN(other,       Other arenas) \

// Fast allocation of memory
class Arena : public CHeapObjBase {
public:
  enum class Tag: uint8_t {
#define ARENA_TAG_ENUM(name, desc) tag_##name,
    DO_ARENA_TAG(ARENA_TAG_ENUM)
#undef ARENA_TAG_ENUM
    tag_count
  };

  constexpr static int tag_count() {
    return static_cast<int>(Tag::tag_count);
  }

  static const char* tag_name[static_cast<int>(Arena::Tag::tag_count)];
  static const char* tag_desc[static_cast<int>(Arena::Tag::tag_count)];

private:
  const MemTag _mem_tag;        // Native Memory Tracking tag
  const Tag _tag;
  size_t _size_in_bytes;        // Size of arena (used for native memory tracking)

protected:
  friend class HandleMark;
  friend class NoHandleMark;

  Chunk* _first;                // First chunk
  Chunk* _chunk;                // current chunk
  char* _hwm;                   // High water mark
  char* _max;                   // and max in current chunk

  // Get a new Chunk of at least size x
  void* grow(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);

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
  static void initialize_chunk_pool();

  // Start the chunk_pool cleaner task
  static void start_chunk_pool_cleaner_task();
  Arena(MemTag mem_tag, Tag tag = Tag::tag_other, size_t init_size = Chunk::init_size);
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

  // Determine if pointer belongs to this Arena or not.
  bool contains( const void *ptr ) const;

  // Total of all chunks in use (not thread-safe)
  size_t used() const;

  // Total # of bytes used
  size_t size_in_bytes() const         {  return _size_in_bytes; };
  void set_size_in_bytes(size_t size);

  MemTag get_mem_tag() const { return _mem_tag; }
  Tag get_tag() const { return _tag; }

  char* strdup(const char* s) {
    const size_t sz = strlen(s) + 1;
    char* ptr = (char*)Amalloc(sz);
    memcpy(ptr, s, sz);
    return ptr;
  }

private:
  // Reset this Arena to empty, access will trigger grow if necessary
  void reset(void) {
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
